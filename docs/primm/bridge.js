/*
 * Warning, this file ("bridge.js"), along with "python-worker.js" and "runner.py" were AI generated.
 * Do not expect craftsmanship or honour ahead, remember that the machine lies.
 *
 * Bridge: the only JavaScript the Kotlin code talks to.
 *
 * Loaded as a classic script before the Compose app. It defines global
 * functions that Kotlin declares as `external`:
 *
 *   Python runtime
 *     primmRuntimeStart(onEvent)    onEvent receives one JSON string per event
 *     primmRuntimeRun(code, trace, step, filesJson, runId)   filesJson: the Files tab as
 *                                   [{name, text}] JSON; the finished event's
 *                                   result lists the files afterwards
 *     primmRuntimeSendInput(text)
 *     primmRuntimeStep(action)      "next" or "continue", while paused
 *     primmRuntimeStop()
 *     primmRuntimeAnalyse(code)     answered by an "analysis" event
 *
 *   Code editor (CodeMirror, drawn over the Compose canvas)
 *     primmEditorCreate(text, onChange)
 *     primmEditorSetBounds(x, y, width, height, density, visible)
 *     primmEditorSetText(text)
 *     primmEditorSetReadOnly(readOnly)
 *     primmEditorFocus()
 *     primmEditorSetFontSize(px)
 *     primmEditorMarkError(line, message)     line 0 clears the mark
 *     primmEditorSetInteractive(interactive)
 *     primmEditorSetRunMarks(current, ranCsv)  next line to run (0 = none), lines run
 *     primmEditorSetDiffMarks(addedCsv, changedCsv, removedCsv)  Modified tab gutter
 *
 *   Marks drawn over the code (pinned to it: they scroll and resize with it)
 *     primmMarksSetPage(key)        each page (program and editor tab) keeps its own marks
 *     primmMarksSetTool(tool)       "pen", "highlighter", "eraser", or "" to use the editor
 *     primmMarksSetVisible(visible) hides the marks without forgetting them
 *     primmMarksClear()             forgets the current page's marks
 *     primmMarksCount() -> number   strokes on the current page
 *
 *   Browser services
 *     primmStorageGet(key) -> string | null   remembered settings (localStorage)
 *     primmStorageSet(key, value)
 *     primmGetQuery() -> string               query string without "?"
 *     primmReplaceQuery(query)
 *     primmPageAddress() -> string            this page's address without query or hash
 *     primmCopyText(text, onResult)           onResult receives "ok" or "failed"
 *     primmFetchText(url, onResult)           onResult receives one JSON string; files up
 *                                             to 50 KB (1 MB inside primm/snippets/)
 *     primmDownloadText(fileName, text, mimeType)   offers text as a file to save
 *     primmPickTextFile(accept, onResult)     file chooser; onResult receives one
 *                                             JSON string: {ok, name, text} or
 *                                             {ok: false, reason}
 *     primmToday() / primmClockTime()         "20 October 2026" / "10:42"
 *     primmBrowserLanguages() -> string       the browser's own languages, as a JSON array
 *     primmUseLanguage(iso)                   shows the app in that language ("en", "cy"):
 *                                             navigator.languages answers with it, so
 *                                             Compose Resources picks it up
 *     primmPrintReport(html)                  print window for a report (Save as PDF)
 *     primmReportImage(html, width, action, fileName, onResult)
 *                                             report as one PNG; action "download"
 *                                             or "clipboard"; onResult receives
 *                                             {ok} or {ok: false, reason}
 *
 * Events sent to Kotlin (field "type"):
 *   loading                       Pyodide is (re)starting
 *   ready        channel          "shared" or "service-worker"
 *   loadFailed   message
 *   stdout       text
 *   stderr       text
 *   inputRequest prompt, line     line is the input() call's line, or null
 *   paused                        stepping: waiting before the next line
 *   traceStart   index, line, frame, scope    a line is about to run
 *   traceDone    index, changes, output       that line finished; changes is
 *                                             { variable: repr } for changed values
 *   analysis     requestId, analysis          see primm_analyse in runner.py
 *   finished     outcome          "completed" | "stopped" | "error"
 *                errorType, line, message, traceback   (outcome "error")
 *                restarted        true when Stop had to restart Python
 *                traceTruncated   true when the trace stopped at its row limit
 *
 * Every event a run causes also has "run": the runId given to primmRuntimeRun.
 */
(function () {
  "use strict";

  const base = new URL(".", document.currentScript.src);
  const urls = {
    worker: new URL("python-worker.js", base).href,
    runner: new URL("runner.py", base).href,
    pyodide: new URL("../pyodide/", base).href,
    serviceWorker: new URL("../primm-sw.js", base).href,
    sync: new URL("../__primm_sync__/", base).href,
  };

  // -------------------------------------------------------------- isolation

  /*
   * A host that can't send the cross-origin isolation headers (GitHub Pages,
   * for one) gets them from primm-sw.js instead, once it controls the page.
   * On a first visit that means one reload, started here before the app has
   * loaded much. It is tried once per load: if the page still isn't isolated
   * after reloading (a hard refresh skips service workers), the runtime uses
   * the service-worker channel, and the next load tries again.
   */
  const ISOLATION_TRIED = "primm.isolationTried";

  const isolation = (async function isolate() {
    let tried = false;
    try {
      tried = window.sessionStorage.getItem(ISOLATION_TRIED) === "1";
      window.sessionStorage.removeItem(ISOLATION_TRIED);
    } catch {
      return; // without a note of having tried, a reload could repeat forever
    }
    if (tried || self.crossOriginIsolated || !window.isSecureContext || !("serviceWorker" in navigator)) return;
    try {
      await navigator.serviceWorker.register(urls.serviceWorker);
      await navigator.serviceWorker.ready;
      await whenControlled();
      if (!navigator.serviceWorker.controller) return;
      window.sessionStorage.setItem(ISOLATION_TRIED, "1");
    } catch {
      return;
    }
    window.location.reload();
    await new Promise(() => {}); // nothing more happens on this page
  })();

  // Waits (up to 3 seconds) for a newly registered service worker to take control of the page.
  async function whenControlled() {
    if (navigator.serviceWorker.controller) return;
    await new Promise((resolve) => {
      const timer = setTimeout(resolve, 3000);
      navigator.serviceWorker.addEventListener(
        "controllerchange",
        () => {
          clearTimeout(timer);
          resolve();
        },
        { once: true },
      );
    });
  }

  // ---------------------------------------------------------------- runtime

  const INPUT_BYTES = 64 * 1024;
  const STOP_GRACE_MS = 1500;
  const FLUSH_MS = 16;
  // Slots and states shared with python-worker.js.
  const STATE = 0;
  const LENGTH = 1;
  const ANSWERED = 1;
  const STOPPED = 2;

  const runtime = {
    listener: null,
    started: false,
    worker: null,
    channel: null, // descriptor sent to the worker
    shared: null, // { control, data, interrupt } in shared mode
    session: Math.random().toString(36).slice(2),
    status: "idle", // idle | loading | ready | running | input | paused | failed
    inputRequestId: null,
    analysis: null, // { requestId, code } waiting for an answer
    nextAnalysisId: 1,
    stopTimer: null,
    queue: [],
    flushTimer: null,
    run: null, // the id of the run in progress; its events carry it as "run"
  };

  function emit(event) {
    const last = runtime.queue[runtime.queue.length - 1];
    if (
      last &&
      (event.type === "stdout" || event.type === "stderr") &&
      last.type === event.type &&
      last.run === event.run
    ) {
      last.text += event.text; // merge bursts of output
    } else {
      runtime.queue.push(event);
    }
    if (runtime.flushTimer === null) {
      runtime.flushTimer = setTimeout(flush, FLUSH_MS);
    }
  }

  function flush() {
    runtime.flushTimer = null;
    const events = runtime.queue;
    runtime.queue = [];
    for (const event of events) {
      try {
        if (runtime.listener) runtime.listener(JSON.stringify(event));
      } catch (error) {
        console.error("PRIMM: event listener failed", error);
      }
    }
  }

  async function chooseChannel() {
    await isolation;
    if (self.crossOriginIsolated && typeof SharedArrayBuffer === "function") {
      const control = new Int32Array(new SharedArrayBuffer(4 * 4));
      const data = new Uint8Array(new SharedArrayBuffer(INPUT_BYTES));
      const interrupt = new Uint8Array(new SharedArrayBuffer(1));
      runtime.shared = { control, data, interrupt };
      return { kind: "shared", control, data, interrupt };
    }
    if (!("serviceWorker" in navigator)) {
      throw new Error(
        "This browser cannot pause Python for input(). Try a current version of Chrome or Edge.",
      );
    }
    await navigator.serviceWorker.register(urls.serviceWorker);
    await navigator.serviceWorker.ready;
    // First visit: wait for clients.claim() to take control of this page.
    await whenControlled();
    if (!navigator.serviceWorker.controller) {
      throw new Error("The input helper did not start. Reload the page and try again.");
    }
    return { kind: "service-worker", url: urls.sync, session: runtime.session };
  }

  function spawnWorker() {
    runtime.status = "loading";
    runtime.inputRequestId = null;
    emit({ type: "loading" });
    const worker = new Worker(urls.worker);
    runtime.worker = worker;
    worker.onmessage = (event) => {
      if (worker === runtime.worker) onWorkerMessage(event.data);
    };
    worker.onerror = (event) => {
      if (worker !== runtime.worker) return;
      event.preventDefault();
      fail(`Python stopped unexpectedly: ${event.message || "unknown error"}`);
    };
    worker.postMessage({
      type: "init",
      channel: runtime.channel,
      pyodideUrl: urls.pyodide,
      runnerUrl: urls.runner,
    });
  }

  function fail(message) {
    clearStopTimer();
    runtime.status = "failed";
    emit({ type: "loadFailed", message });
  }

  function onWorkerMessage(message) {
    switch (message.type) {
      case "ready":
        runtime.status = "ready";
        emit({ type: "ready", channel: message.channel });
        sendAnalysis();
        break;
      case "loadFailed":
        fail(message.message);
        break;
      case "stdout":
      case "stderr":
        emit({ type: message.type, text: message.text, run: runOf(message) });
        break;
      case "inputRequest":
        runtime.status = "input";
        runtime.inputRequestId = message.requestId ?? null;
        emit({ type: "inputRequest", prompt: message.prompt, line: message.line ?? null, run: runOf(message) });
        break;
      case "paused":
        runtime.status = "paused";
        runtime.inputRequestId = message.requestId ?? null;
        emit({ type: "paused", run: runOf(message) });
        break;
      case "traceStart":
        emit({
          type: "traceStart",
          index: message.index,
          line: message.line,
          frame: message.frame,
          scope: message.scope,
          run: runOf(message),
        });
        break;
      case "traceDone":
        emit({ type: "traceDone", index: message.index, changes: message.changes, output: message.output, run: runOf(message) });
        break;
      case "analysis":
        if (runtime.analysis && runtime.analysis.requestId === message.requestId) {
          runtime.analysis = null;
          emit({ type: "analysis", requestId: message.requestId, analysis: message.analysis });
        }
        break;
      case "finished": {
        clearStopTimer();
        runtime.status = "ready";
        runtime.inputRequestId = null;
        const r = message.result;
        emit({
          type: "finished",
          outcome: r.outcome,
          errorType: r.type ?? null,
          line: r.line ?? null,
          message: r.message ?? null,
          traceback: r.traceback ?? null,
          restarted: false,
          traceTruncated: r.traceTruncated ?? false,
          files: r.files ?? null,
          run: runOf(message),
        });
        runtime.run = null;
        break;
      }
      default:
        console.warn("PRIMM: unknown worker message", message);
    }
  }

  /** The run a worker event belongs to (older workers don't say, so it's the current one). */
  function runOf(message) {
    return message.run ?? runtime.run ?? null;
  }

  function clearStopTimer() {
    if (runtime.stopTimer !== null) {
      clearTimeout(runtime.stopTimer);
      runtime.stopTimer = null;
    }
  }

  /** Ends a program that will not stop politely, then reloads Python. */
  function restartWorker() {
    clearStopTimer();
    if (runtime.worker) runtime.worker.terminate();
    runtime.worker = null;
    emit({
      type: "finished",
      outcome: "stopped",
      errorType: null,
      line: null,
      message: null,
      traceback: null,
      restarted: true,
      traceTruncated: false,
      files: null,
      run: runtime.run,
    });
    runtime.run = null;
    spawnWorker();
  }

  /** Sends the waiting analysis request once the worker is ready (again). */
  function sendAnalysis() {
    if (runtime.analysis && runtime.worker && runtime.status !== "loading" && runtime.status !== "failed") {
      runtime.worker.postMessage({
        type: "analyse",
        requestId: runtime.analysis.requestId,
        code: runtime.analysis.code,
      });
    }
  }

  /** Answers whatever the worker is waiting for: an input() line or a step action. */
  function answerWorker(text) {
    if (runtime.channel.kind === "shared") {
      const { control, data } = runtime.shared;
      let bytes = new TextEncoder().encode(text);
      if (bytes.length > data.length) bytes = bytes.slice(0, data.length);
      data.set(bytes);
      Atomics.store(control, LENGTH, bytes.length);
      Atomics.store(control, STATE, ANSWERED);
      Atomics.notify(control, STATE);
    } else {
      postToServiceWorker({
        type: "primm-input",
        session: runtime.session,
        id: runtime.inputRequestId,
        value: text,
      });
    }
  }

  function postToServiceWorker(message) {
    const target = navigator.serviceWorker.controller;
    if (target) target.postMessage(message);
  }

  window.primmRuntimeStart = function (onEvent) {
    runtime.listener = onEvent;
    if (runtime.started) {
      // A second start (e.g. the UI was rebuilt) only replaces the listener
      // and repeats the current state.
      if (runtime.status === "ready") emit({ type: "ready", channel: runtime.channel.kind });
      else if (runtime.status === "loading") emit({ type: "loading" });
      return;
    }
    runtime.started = true;
    runtime.status = "loading";
    emit({ type: "loading" });
    chooseChannel().then(
      (channel) => {
        runtime.channel = channel;
        spawnWorker();
      },
      (error) => fail(String((error && error.message) || error)),
    );
  };

  window.primmRuntimeRun = function (code, trace, step, filesJson, runId) {
    if (runtime.status !== "ready") return false;
    runtime.status = "running";
    runtime.run = Number.isInteger(runId) ? runId : null;
    const files = typeof filesJson === "string" && filesJson !== "" ? filesJson : null;
    runtime.worker.postMessage({ type: "run", code: String(code), trace: Boolean(trace), step: Boolean(step), files, run: runtime.run });
    return true;
  };

  window.primmRuntimeSendInput = function (text) {
    if (runtime.status !== "input") return false;
    runtime.status = "running";
    answerWorker(String(text));
    return true;
  };

  window.primmRuntimeStep = function (action) {
    if (runtime.status !== "paused") return false;
    runtime.status = "running";
    answerWorker(action === "continue" ? "continue" : "next");
    return true;
  };

  /** Asks for an analysis of code; only the latest request is answered. */
  window.primmRuntimeAnalyse = function (code) {
    runtime.analysis = { requestId: runtime.nextAnalysisId++, code: String(code) };
    if (runtime.status === "ready" || runtime.status === "running" ||
        runtime.status === "input" || runtime.status === "paused") {
      // A busy worker handles it as soon as the program finishes.
      sendAnalysis();
    }
    return runtime.analysis.requestId;
  };

  window.primmRuntimeStop = function () {
    const status = runtime.status;
    if (status !== "running" && status !== "input" && status !== "paused") return false;
    if (runtime.stopTimer !== null) return true; // already stopping
    if (runtime.channel.kind === "shared") {
      const { control, interrupt } = runtime.shared;
      if (status === "input" || status === "paused") {
        // Python is blocked waiting for the page: answer "stop". Setting the
        // interrupt as well would raise a second KeyboardInterrupt while the
        // runner tidies up, losing the last trace row.
        Atomics.store(control, STATE, STOPPED);
        Atomics.notify(control, STATE);
      } else {
        interrupt[0] = 2; // SIGINT: Python raises KeyboardInterrupt
      }
    } else if (status === "input" || status === "paused") {
      postToServiceWorker({
        type: "primm-input",
        session: runtime.session,
        id: runtime.inputRequestId,
        stop: true,
      });
    } else {
      // Without shared memory a running program cannot be interrupted.
      restartWorker();
      return true;
    }
    // If the program ignores the interrupt (for example `except: pass`
    // inside a loop), restart Python after a short grace period.
    runtime.stopTimer = setTimeout(restartWorker, STOP_GRACE_MS);
    return true;
  };

  // ----------------------------------------------------------------- editor

  const editor = {
    host: null,
    api: null,
    onChange: null,
    applying: false, // true while Kotlin is setting the text
    bounds: null, // last bounds from Kotlin, applied when the editor is created
  };

  function findComposeCanvas() {
    const search = (root) => {
      for (const element of root.querySelectorAll("*")) {
        // The marks canvas lives in the editor; it isn't Compose's.
        if (element instanceof HTMLCanvasElement && !element.closest(".primm-editor-host")) return element;
        if (element.shadowRoot) {
          const found = search(element.shadowRoot);
          if (found) return found;
        }
      }
      return null;
    };
    return search(document.body);
  }

  window.primmEditorCreate = function (text, onChange) {
    editor.onChange = onChange;
    if (editor.api) {
      window.primmEditorSetText(text);
      return;
    }
    const host = document.createElement("div");
    host.id = "primm-editor";
    host.className = "primm-editor-host";
    host.style.display = "none";
    // Keep keystrokes inside the editor away from Compose's own handlers.
    for (const type of ["keydown", "keyup", "keypress"]) {
      host.addEventListener(type, (event) => event.stopPropagation());
    }
    document.body.appendChild(host);
    editor.host = host;
    editor.api = window.PrimmCM.createEditor(host, String(text), (newText) => {
      if (!editor.applying && editor.onChange) editor.onChange(newText);
    });
    if (editor.bounds) window.primmEditorSetBounds(...editor.bounds);
    if (editor.diff) editor.api.setDiffMarks(...editor.diff);
    createMarksLayer(host);
  };

  /**
   * Places the editor over the Compose layout. x, y, width and height are in
   * Compose pixels (relative to the Compose canvas); density is Compose's
   * pixels per CSS pixel, used if the canvas cannot be found.
   */
  window.primmEditorSetBounds = function (x, y, width, height, density, visible) {
    editor.bounds = [x, y, width, height, density, visible];
    const host = editor.host;
    if (!host) return;
    if (!visible || width <= 0 || height <= 0) {
      host.style.display = "none";
      return;
    }
    const canvas = findComposeCanvas();
    let left = 0;
    let top = 0;
    let scale = 1 / (density || window.devicePixelRatio || 1);
    if (canvas && canvas.width > 0) {
      const rect = canvas.getBoundingClientRect();
      left = rect.left;
      top = rect.top;
      scale = rect.width / canvas.width;
    }
    host.style.left = `${left + x * scale}px`;
    host.style.top = `${top + y * scale}px`;
    host.style.width = `${width * scale}px`;
    host.style.height = `${height * scale}px`;
    host.style.display = "block";
  };

  window.primmEditorSetText = function (text) {
    if (!editor.api) return;
    editor.applying = true;
    try {
      editor.api.setText(String(text));
    } finally {
      editor.applying = false;
    }
  };

  window.primmEditorSetReadOnly = function (readOnly) {
    if (editor.api) editor.api.setReadOnly(Boolean(readOnly));
  };

  window.primmEditorFocus = function () {
    if (editor.api) editor.api.focus();
  };

  /** Font size in CSS pixels, for the A- / A+ buttons. */
  window.primmEditorSetFontSize = function (px) {
    document.documentElement.style.setProperty("--primm-editor-font-size", `${px}px`);
    requestAnimationFrame(drawMarks);
  };

  /** Marks the line (1-based) where an error happened; line 0 clears the mark. */
  window.primmEditorMarkError = function (line, message) {
    if (editor.api) editor.api.markError(Math.trunc(line), String(message));
  };

  /**
   * While a divider is being dragged across the editor, let the pointer pass
   * through it so Compose keeps receiving the drag.
   */
  window.primmEditorSetInteractive = function (interactive) {
    if (editor.host) editor.host.style.pointerEvents = interactive ? "" : "none";
  };

  /**
   * Step mode: highlights the next line to run (0 for none) and puts a dot by
   * each line that has run. ranCsv is a comma-separated list of line numbers.
   */
  window.primmEditorSetDiffMarks = function (addedCsv, changedCsv, removedCsv) {
    const parse = (csv) => String(csv || "").split(",").filter(Boolean).map(Number);
    editor.diff = [parse(addedCsv), parse(changedCsv), parse(removedCsv)];
    if (editor.api) editor.api.setDiffMarks(...editor.diff);
  };

  window.primmEditorSetRunMarks = function (current, ranCsv) {
    if (!editor.api) return;
    const ran = String(ranCsv)
      .split(",")
      .map((n) => Number.parseInt(n, 10))
      .filter((n) => Number.isFinite(n) && n > 0);
    editor.api.setRunMarks(Math.trunc(current), ran);
  };

  // ------------------------------------------------------------------ marks
  //
  // A transparent canvas over the editor. Points are stored in the code's own
  // coordinates (from the top left of the text, scrolled or not) divided by
  // the font size, so a mark stays on the same code when the editor scrolls
  // or the text gets bigger. The canvas only takes the pointer while a tool
  // is chosen; otherwise clicks go straight through to the code.

  const MARK_STYLES = {
    pen: { colour: "#c62828", width: 0.15, alpha: 1 },
    highlighter: { colour: "#ffd600", width: 1.2, alpha: 0.45 },
  };
  const ERASER_RADIUS = 0.5; // in font sizes, around the pointer
  const MIN_STEP = 0.03; // ignore pointer moves smaller than this (font sizes)

  const marks = {
    canvas: null,
    pages: new Map(), // page key -> [{ tool, points: [[x, y], ...] }]
    page: "",
    tool: "",
    visible: true,
    stroke: null, // the stroke being drawn
    erasing: false,
  };

  function marksStrokes() {
    if (!marks.pages.has(marks.page)) marks.pages.set(marks.page, []);
    return marks.pages.get(marks.page);
  }

  function marksScroller() {
    return editor.host ? editor.host.querySelector(".cm-scroller") : null;
  }

  function marksFontSize(scroller) {
    return Number.parseFloat(getComputedStyle(scroller).fontSize) || 15;
  }

  function createMarksLayer(host) {
    const canvas = document.createElement("canvas");
    canvas.id = "primm-marks";
    canvas.className = "primm-marks";
    host.appendChild(canvas);
    marks.canvas = canvas;

    // A pointer position as a point in the code (see above).
    const toPoint = (event) => {
      const scroller = marksScroller();
      const rect = scroller.getBoundingClientRect();
      const size = marksFontSize(scroller);
      return [
        (event.clientX - rect.left + scroller.scrollLeft) / size,
        (event.clientY - rect.top + scroller.scrollTop) / size,
      ];
    };

    canvas.addEventListener("pointerdown", (event) => {
      if (!marks.tool || !marksScroller()) return;
      if (event.pointerType === "mouse" && event.button !== 0) return;
      event.preventDefault();
      canvas.setPointerCapture(event.pointerId);
      const point = toPoint(event);
      if (marks.tool === "eraser") {
        marks.erasing = true;
        eraseAt(point);
      } else {
        marks.stroke = { tool: marks.tool, points: [point] };
        marksStrokes().push(marks.stroke);
      }
      drawMarks();
    });
    canvas.addEventListener("pointermove", (event) => {
      if (!marks.stroke && !marks.erasing) return;
      const point = toPoint(event);
      if (marks.erasing) {
        eraseAt(point);
      } else {
        const last = marks.stroke.points[marks.stroke.points.length - 1];
        if (Math.hypot(point[0] - last[0], point[1] - last[1]) < MIN_STEP) return;
        marks.stroke.points.push(point);
      }
      drawMarks();
    });
    const finish = () => {
      marks.stroke = null;
      marks.erasing = false;
    };
    canvas.addEventListener("pointerup", finish);
    canvas.addEventListener("pointercancel", finish);
    // While drawing, the wheel still scrolls the code underneath.
    canvas.addEventListener(
      "wheel",
      (event) => {
        const scroller = marksScroller();
        if (!scroller) return;
        event.preventDefault();
        const unit = event.deltaMode === 1 ? marksFontSize(scroller) * 1.5 : event.deltaMode === 2 ? scroller.clientHeight : 1;
        scroller.scrollBy(event.deltaX * unit, event.deltaY * unit);
      },
      { passive: false },
    );

    const scroller = marksScroller();
    if (scroller) scroller.addEventListener("scroll", drawMarks, { passive: true });
    if (window.ResizeObserver) new ResizeObserver(drawMarks).observe(host);
    applyMarksState();
  }

  /** Removes every stroke that passes within the eraser of point. */
  function eraseAt(point) {
    const strokes = marksStrokes();
    const near = (stroke) => {
      const reach = ERASER_RADIUS + MARK_STYLES[stroke.tool].width / 2;
      const pts = stroke.points;
      if (pts.length === 1) return Math.hypot(point[0] - pts[0][0], point[1] - pts[0][1]) <= reach;
      for (let i = 1; i < pts.length; i++) {
        if (distanceToSegment(point, pts[i - 1], pts[i]) <= reach) return true;
      }
      return false;
    };
    const kept = strokes.filter((stroke) => !near(stroke));
    if (kept.length !== strokes.length) marks.pages.set(marks.page, kept);
  }

  function distanceToSegment(p, a, b) {
    const dx = b[0] - a[0];
    const dy = b[1] - a[1];
    const lengthSquared = dx * dx + dy * dy;
    const t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lengthSquared));
    return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
  }

  /** Sizes the canvas to the editor and draws the current page's strokes. */
  function drawMarks() {
    const canvas = marks.canvas;
    const scroller = marksScroller();
    if (!canvas || !scroller || !marks.visible) return;
    const ratio = window.devicePixelRatio || 1;
    const width = Math.round(canvas.clientWidth * ratio);
    const height = Math.round(canvas.clientHeight * ratio);
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const context = canvas.getContext("2d");
    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, canvas.width, canvas.height);
    const size = marksFontSize(scroller);
    const canvasRect = canvas.getBoundingClientRect();
    const scrollerRect = scroller.getBoundingClientRect();
    // From code coordinates (in font sizes) to canvas pixels.
    context.setTransform(
      ratio * size,
      0,
      0,
      ratio * size,
      ratio * (scrollerRect.left - canvasRect.left - scroller.scrollLeft),
      ratio * (scrollerRect.top - canvasRect.top - scroller.scrollTop),
    );
    context.lineCap = "round";
    context.lineJoin = "round";
    for (const stroke of marksStrokes()) {
      const style = MARK_STYLES[stroke.tool];
      context.globalAlpha = style.alpha;
      context.strokeStyle = style.colour;
      context.fillStyle = style.colour;
      context.lineWidth = style.width;
      const [first, ...rest] = stroke.points;
      if (rest.length === 0) {
        context.beginPath();
        context.arc(first[0], first[1], style.width / 2, 0, Math.PI * 2);
        context.fill();
        continue;
      }
      context.beginPath();
      context.moveTo(first[0], first[1]);
      for (const [x, y] of rest) context.lineTo(x, y);
      context.stroke();
    }
    context.globalAlpha = 1;
  }

  function applyMarksState() {
    const canvas = marks.canvas;
    if (!canvas) return;
    canvas.style.display = marks.visible ? "block" : "none";
    const drawing = marks.visible && Boolean(marks.tool);
    canvas.style.pointerEvents = drawing ? "auto" : "none";
    canvas.style.cursor = !drawing ? "" : marks.tool === "eraser" ? "cell" : "crosshair";
    drawMarks();
  }

  window.primmMarksSetPage = function (key) {
    marks.page = String(key);
    marks.stroke = null;
    marks.erasing = false;
    drawMarks();
  };

  window.primmMarksSetTool = function (tool) {
    marks.tool = tool in MARK_STYLES || tool === "eraser" ? tool : "";
    marks.stroke = null;
    marks.erasing = false;
    applyMarksState();
  };

  window.primmMarksSetVisible = function (visible) {
    marks.visible = Boolean(visible);
    applyMarksState();
  };

  window.primmMarksClear = function () {
    marks.pages.set(marks.page, []);
    drawMarks();
  };

  window.primmMarksCount = function () {
    return marksStrokes().length;
  };

  // ------------------------------------------------------- browser services

  /** Reads a remembered setting, or returns null. Never throws. */
  window.primmStorageGet = function (key) {
    try {
      return window.localStorage.getItem(`primm.${key}`);
    } catch {
      return null;
    }
  };

  window.primmStorageSet = function (key, value) {
    try {
      window.localStorage.setItem(`primm.${key}`, String(value));
    } catch {
      // Storage can be unavailable (private windows, blocked site data).
    }
  };

  /** The page's query string without the leading "?". */
  window.primmGetQuery = function () {
    return window.location.search.replace(/^\?/, "");
  };

  window.primmPageAddress = function () {
    return window.location.origin + window.location.pathname;
  };

  /** Puts text on the clipboard; falls back to a hidden text box where the API is blocked. */
  window.primmCopyText = function (text, onResult) {
    const value = String(text);
    const fallback = () => {
      let ok = false;
      const box = document.createElement("textarea");
      box.value = value;
      box.setAttribute("readonly", "");
      box.style.position = "fixed";
      box.style.opacity = "0";
      document.body.appendChild(box);
      try {
        box.select();
        ok = document.execCommand("copy");
      } catch {
        ok = false;
      }
      box.remove();
      onResult(ok ? "ok" : "failed");
    };
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(value).then(() => onResult("ok"), fallback);
    } else {
      fallback();
    }
  };

  /** Replaces the query string in the address bar without reloading. */
  window.primmReplaceQuery = function (query) {
    const url = query ? `${window.location.pathname}?${query}` : window.location.pathname;
    try {
      window.history.replaceState(null, "", url + window.location.hash);
    } catch (error) {
      console.warn("PRIMM: could not update the address bar", error);
    }
  };

  const MAX_PROGRAM_BYTES = 50 * 1024;
  // The snippet library (primm/snippets/, beside this file) may be bigger than one program.
  const MAX_LIBRARY_BYTES = 1024 * 1024;
  const libraryFolder = new URL("snippets/", document.currentScript.src).href;

  /**
   * Fetches a text file. onResult receives one JSON string:
   *   { ok: true,  status, contentType, text }
   *   { ok: false, kind: "network" }                 blocked (often CORS) or offline
   *   { ok: false, kind: "http", status }
   *   { ok: false, kind: "tooLarge", size }
   */
  window.primmFetchText = function (url, onResult) {
    const reply = (result) => {
      try {
        onResult(JSON.stringify(result));
      } catch (error) {
        console.error("PRIMM: fetch listener failed", error);
      }
    };
    let request;
    let limit = MAX_PROGRAM_BYTES;
    try {
      if (new URL(String(url), document.baseURI).href.startsWith(libraryFolder)) limit = MAX_LIBRARY_BYTES;
      request = fetch(String(url), {
        mode: "cors",
        credentials: "omit",
        cache: "no-store",
        redirect: "follow",
      });
    } catch {
      reply({ ok: false, kind: "network" });
      return;
    }
    request.then(
      async (response) => {
        if (!response.ok) {
          reply({ ok: false, kind: "http", status: response.status });
          return;
        }
        const declared = Number(response.headers.get("Content-Length"));
        if (declared > limit) {
          reply({ ok: false, kind: "tooLarge", size: declared });
          return;
        }
        try {
          const buffer = await response.arrayBuffer();
          if (buffer.byteLength > limit) {
            reply({ ok: false, kind: "tooLarge", size: buffer.byteLength });
            return;
          }
          reply({
            ok: true,
            status: response.status,
            contentType: response.headers.get("Content-Type") || "",
            text: new TextDecoder("utf-8").decode(buffer),
          });
        } catch {
          reply({ ok: false, kind: "network" });
        }
      },
      () => reply({ ok: false, kind: "network" }),
    );
  };

  // ------------------------------------------------------------------ files

  const MAX_SESSION_BYTES = 2 * 1024 * 1024;

  window.primmDownloadText = function (fileName, text, mimeType) {
    downloadBlob(new Blob([String(text)], { type: mimeType || "text/plain" }), fileName);
  };

  function downloadBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = String(fileName);
    link.style.display = "none";
    document.body.appendChild(link);
    link.click();
    link.remove();
    // Give the browser time to start the download before letting go of the data.
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  window.primmPickTextFile = function (accept, onResult) {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = String(accept || "");
    input.style.display = "none";
    let answered = false;
    const finish = (result) => {
      if (answered) return;
      answered = true;
      input.remove();
      try {
        onResult(JSON.stringify(result));
      } catch (error) {
        console.error("PRIMM: file listener failed", error);
      }
    };
    // "cancel" is reported by current browsers; older ones say nothing.
    input.addEventListener("cancel", () => finish({ ok: false, reason: "cancelled" }));
    input.addEventListener("change", async () => {
      const file = input.files && input.files[0];
      if (!file) {
        finish({ ok: false, reason: "cancelled" });
        return;
      }
      if (file.size > MAX_SESSION_BYTES) {
        finish({ ok: false, reason: "tooLarge", size: file.size });
        return;
      }
      try {
        finish({ ok: true, name: file.name, text: await file.text() });
      } catch {
        finish({ ok: false, reason: "unreadable" });
      }
    });
    document.body.appendChild(input);
    input.click();
  };

  // ----------------------------------------------------------------- report

  /*
   * Language. Compose Resources chooses strings from navigator.languages, and
   * there is no other way to override that, so (as JetBrains suggest) the
   * getter answers with the chosen language once one is set.
   */
  const languagesProperty = Object.getOwnPropertyDescriptor(Navigator.prototype, "languages");
  const browserLanguages = () => Array.from(languagesProperty.get.call(window.navigator) || [window.navigator.language]);
  window.__customLocale = null;
  Object.defineProperty(Navigator.prototype, "languages", {
    ...languagesProperty,
    get: function () {
      return window.__customLocale ? [window.__customLocale] : languagesProperty.get.call(this);
    },
  });

  window.primmBrowserLanguages = function () {
    return JSON.stringify(browserLanguages());
  };

  window.primmUseLanguage = function (iso) {
    window.__customLocale = String(iso);
    document.documentElement.lang = String(iso);
  };

  window.primmToday = function () {
    return new Date().toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" });
  };

  window.primmClockTime = function () {
    return new Date().toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  };

  // An off-screen frame holding a report, resolved once it has loaded.
  function reportFrame(html, width, height) {
    return new Promise((resolve) => {
      const frame = document.createElement("iframe");
      frame.className = "primm-report-frame";
      frame.setAttribute("aria-hidden", "true");
      frame.style.cssText =
        `position:fixed;left:-20000px;top:0;width:${width}px;height:${height}px;border:0;`;
      frame.addEventListener("load", () => resolve(frame), { once: true });
      frame.srcdoc = String(html);
      document.body.appendChild(frame);
    });
  }

  window.primmPrintReport = function (html) {
    reportFrame(html, 800, 600).then((frame) => {
      const win = frame.contentWindow;
      let removed = false;
      const remove = () => {
        if (removed) return;
        removed = true;
        frame.remove();
      };
      win.addEventListener("afterprint", () => setTimeout(remove, 500));
      // Some browsers never say printing ended; tidy up eventually anyway.
      setTimeout(remove, 10 * 60 * 1000);
      // Print a moment after loading: some browsers print a blank page straight from the load event.
      setTimeout(() => {
        win.focus();
        win.print();
      }, 50);
    });
  };

  const MAX_IMAGE_HEIGHT = 32000; // browsers refuse taller canvases

  window.primmReportImage = function (html, width, action, fileName, onResult) {
    const reply = (result) => {
      try {
        onResult(JSON.stringify(result));
      } catch (error) {
        console.error("PRIMM: image listener failed", error);
      }
    };
    width = Math.max(320, Number(width) || 900);
    reportFrame(html, width, 200).then(async (frame) => {
      try {
        const doc = frame.contentDocument;
        const height = Math.ceil(Math.max(doc.documentElement.scrollHeight, doc.body.scrollHeight));
        const scale = Math.min(2, window.devicePixelRatio || 1, MAX_IMAGE_HEIGHT / height);
        if (scale < 0.75) {
          reply({ ok: false, reason: "tooTall", height });
          return;
        }
        // The report drawn as an SVG picture of its own page, then onto a canvas.
        const page = new XMLSerializer().serializeToString(doc.documentElement);
        const svg =
          `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">` +
          `<foreignObject x="0" y="0" width="${width}" height="${height}">${page}</foreignObject></svg>`;
        const image = new Image();
        image.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
        await image.decode();
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(width * scale);
        canvas.height = Math.round(height * scale);
        const context = canvas.getContext("2d");
        context.fillStyle = "#ffffff";
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.scale(scale, scale);
        context.drawImage(image, 0, 0);
        const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
        if (!blob) throw new Error("no image");
        if (action === "clipboard") {
          try {
            await navigator.clipboard.write([new ClipboardItem({ "image/png": blob })]);
          } catch (error) {
            console.warn("PRIMM: copying the picture failed", error);
            reply({ ok: false, reason: "clipboard" });
            return;
          }
        } else {
          downloadBlob(blob, fileName || "primm-report.png");
        }
        reply({ ok: true, width: canvas.width, height: canvas.height });
      } catch (error) {
        console.error("PRIMM: the picture couldn't be made", error);
        reply({ ok: false, reason: "failed" });
      } finally {
        frame.remove();
      }
    });
  };
})();
