/*
 * Warning, this file ("python-worker.js"), along with "bridge.js" and "runner.py" were AI generated.
 * Do not expect craftsmanship or honour ahead, remember that the machine lies.
 *
 * Python worker.
 *
 * Loads Pyodide and runner.py, then runs programs sent by bridge.js. Running
 * in a worker means a long or blocking program never freezes the page.
 *
 * input() and time.sleep() must block this thread until the page answers.
 * Two channels are supported, chosen by bridge.js:
 *
 *  - "shared": the page is cross-origin isolated, so SharedArrayBuffer and
 *    Atomics.wait are available. Stop uses Pyodide's interrupt buffer.
 *  - "service-worker": a synchronous XMLHttpRequest to a URL that
 *    primm-sw.js holds open until the page posts the answer.
 *
 * Messages to the page:  ready, loadFailed, stdout, stderr, inputRequest,
 * paused, traceStart, traceDone, analysis, finished. See bridge.js.
 *
 * Step mode uses the same blocking channel as input(): the worker posts
 * "paused" and waits for the page to answer "next" or "continue".
 */
"use strict";

importScripts("../pyodide/pyodide.js");

// Slots in the shared control array.
const STATE = 0;
const LENGTH = 1;
const SLEEP = 2;
// Values of control[STATE].
const WAITING = 0;
const ANSWERED = 1;
const STOPPED = 2;

let channel = null;
let primmRun = null;
let primmAnalyse = null;
let nextRequestId = 1;
let currentRun = null;
const decoder = new TextDecoder();

self.onmessage = (event) => {
  const message = event.data;
  if (message.type === "init") {
    init(message);
  } else if (message.type === "run") {
    currentRun = message.run ?? null;
    run(message.code, Boolean(message.trace), Boolean(message.step), message.files ?? null);
    currentRun = null;
  } else if (message.type === "analyse") {
    analyse(message.requestId, message.code);
  }
};

async function init(message) {
  try {
    channel = message.channel;
    const pyodide = await loadPyodide({ indexURL: message.pyodideUrl });
    if (channel.kind === "shared") {
      pyodide.setInterruptBuffer(channel.interrupt);
    }
    const response = await fetch(message.runnerUrl);
    if (!response.ok) {
      throw new Error(`Could not load runner.py (HTTP ${response.status})`);
    }
    pyodide.runPython(await response.text());
    primmRun = pyodide.globals.get("primm_run");
    primmAnalyse = pyodide.globals.get("primm_analyse");
    postMessage({ type: "ready", channel: channel.kind });
  } catch (error) {
    postMessage({ type: "loadFailed", message: describe(error) });
  }
}

function analyse(requestId, code) {
  let analysis;
  try {
    analysis = JSON.parse(primmAnalyse(code));
  } catch (error) {
    analysis = { ok: false };
  }
  postMessage({ type: "analysis", requestId, analysis });
}

function run(code, trace, step, files) {
  if (channel.kind === "shared") {
    channel.interrupt[0] = 0;
  }
  let result;
  try {
    const options = files === null ? {} : { files_json: files };
    const json = trace
      ? primmRun.callKwargs(code, writeOut, writeErr, readInput, sleep, traceStart, traceDone, stepWait, step, options)
      : primmRun.callKwargs(code, writeOut, writeErr, readInput, sleep, options);
    result = JSON.parse(json);
  } catch (error) {
    const stopped = /KeyboardInterrupt/.test(describe(error));
    result = stopped
      ? { outcome: "stopped" }
      : {
          outcome: "error",
          type: "InternalError",
          line: null,
          message: describe(error),
          traceback: "",
        };
  }
  postRun({ type: "finished", result });
}

function postRun(message) {
  postMessage({ ...message, run: currentRun });
}

function writeOut(text) {
  postRun({ type: "stdout", text });
}

function writeErr(text) {
  postRun({ type: "stderr", text });
}

function traceStart(index, line, frame, scope) {
  postRun({ type: "traceStart", index, line, frame, scope });
}

function traceDone(index, changes, output) {
  postRun({ type: "traceDone", index, changes: JSON.parse(changes), output });
}

/** Blocks until the student answers input(). Returns the line, or null to stop. */
function readInput(prompt, line) {
  return waitForPage({ type: "inputRequest", prompt, line: line ?? null });
}

/** Blocks before a line while stepping. Returns "next", "continue", or null to stop. */
function stepWait() {
  return waitForPage({ type: "paused" });
}

/** Posts a request and blocks until the page answers it (or asks to stop). */
function waitForPage(request) {
  if (channel.kind === "shared") {
    const control = channel.control;
    Atomics.store(control, STATE, WAITING);
    postRun(request);
    while (Atomics.wait(control, STATE, WAITING, 1000) === "timed-out") {
      // Keep waiting; the timeout only stops a lost wake-up hanging forever.
    }
    if (Atomics.load(control, STATE) === STOPPED) {
      return null;
    }
    const length = Atomics.load(control, LENGTH);
    // slice() copies into an unshared buffer, which TextDecoder requires.
    return decoder.decode(channel.data.slice(0, length));
  }

  const id = nextRequestId++;
  postRun({ ...request, requestId: id });
  for (;;) {
    const reply = syncRequest("read", { session: channel.session, id });
    if (reply.kind === "input") return reply.value;
    if (reply.kind === "stop") return null;
    // "timeout": the service worker gave up waiting; ask again.
  }
}

/** Blocks for the given time. Returns true if Stop was pressed. */
function sleep(seconds) {
  const end = Date.now() + seconds * 1000;
  if (channel.kind === "shared") {
    for (;;) {
      if (channel.interrupt[0] !== 0) return true;
      const left = end - Date.now();
      if (left <= 0) return false;
      Atomics.wait(channel.control, SLEEP, 0, Math.min(left, 50));
    }
  }
  // Service-worker mode: Stop terminates the worker, so a plain wait is fine.
  let left = end - Date.now();
  while (left > 0) {
    const chunk = Math.min(left, 10000);
    if (chunk >= 20) {
      syncRequest("sleep", { ms: chunk });
    } else {
      while (Date.now() < end) {
        // Busy-wait for very short sleeps only.
      }
    }
    left = end - Date.now();
  }
  return false;
}

function syncRequest(action, body) {
  const xhr = new XMLHttpRequest();
  xhr.open("POST", channel.url + action, false);
  xhr.setRequestHeader("Content-Type", "application/json");
  xhr.send(JSON.stringify(body));
  if (xhr.status !== 200) {
    throw new Error(
      `The input channel stopped working (HTTP ${xhr.status}). Reload the page.`,
    );
  }
  return JSON.parse(xhr.responseText);
}

function describe(error) {
  return String((error && error.message) || error);
}
