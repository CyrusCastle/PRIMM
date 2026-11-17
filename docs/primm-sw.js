"use strict";

const READ_TIMEOUT_MS = 20000;
const pending = new Map();
const early = new Map();

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("message", (event) => {
  const message = event.data;
  if (!message || message.type !== "primm-input") return;
  const key = `${message.session}:${message.id}`;
  const reply = message.stop
    ? { kind: "stop" }
    : { kind: "input", value: String(message.value) };
  const waiter = pending.get(key);
  if (waiter) {
    pending.delete(key);
    clearTimeout(waiter.timer);
    waiter.resolve(reply);
  } else {
    early.set(key, reply);
  }
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (!url.pathname.includes("/__primm_sync__/")) {
    // A DevTools quirk: such requests can't be fetched again from here.
    if (request.cache === "only-if-cached" && request.mode !== "same-origin") return;
    event.respondWith(isolated(request));
  } else if (url.pathname.endsWith("/read")) {
    event.respondWith(handleRead(request));
  } else if (url.pathname.endsWith("/sleep")) {
    event.respondWith(handleSleep(request));
  } else {
    event.respondWith(new Response("Unknown action", { status: 404 }));
  }
});

// The response with the headers that make a page cross-origin isolated.
async function isolated(request) {
  const response = await fetch(request);
  // Opaque responses (such as a redirect for the browser to follow) can't be changed, and needn't be.
  if (response.status === 0) return response;
  const headers = new Headers(response.headers);
  headers.set("Cross-Origin-Opener-Policy", "same-origin");
  headers.set("Cross-Origin-Embedder-Policy", "require-corp");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

async function handleRead(request) {
  const { session, id } = await request.json();
  const key = `${session}:${id}`;
  let reply = early.get(key);
  if (reply) {
    early.delete(key);
  } else {
    reply = await new Promise((resolve) => {
      const timer = setTimeout(() => {
        pending.delete(key);
        resolve({ kind: "timeout" });
      }, READ_TIMEOUT_MS);
      pending.set(key, { resolve, timer });
    });
  }
  return json(reply);
}

async function handleSleep(request) {
  const { ms } = await request.json();
  await new Promise((resolve) => setTimeout(resolve, Math.max(0, Math.min(ms, 10000))));
  return json({ kind: "slept" });
}

function json(body) {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
