// Keeps the reader itself available offline. The archive file is cached separately by app.js
// (it checks for a newer export on every visit while online).
const CACHE = "wea-shell-v1";
const SHELL = ["./", "index.html", "style.css", "app.js", "config.js", "vendor/sql-wasm.js", "vendor/sql-wasm.wasm", "vendor/sql-wasm-b64.js",
    "manifest.webmanifest", "icon.svg"];

self.addEventListener("install", (e) => e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())));
self.addEventListener("activate", (e) => e.waitUntil(
    caches.keys().then((ks) => Promise.all(ks.filter((k) => k.startsWith("wea-shell") && k !== CACHE).map((k) => caches.delete(k))))
        .then(() => self.clients.claim())));

// network first (so updates arrive), cache as fallback (so it works offline). The .db is left to app.js.
self.addEventListener("fetch", (e) => {
    const url = new URL(e.request.url);
    if (e.request.method !== "GET" || url.origin !== location.origin || url.pathname.endsWith(".db")) return;
    e.respondWith(fetch(e.request).then((r) => {
        const copy = r.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return r;
    }).catch(() => caches.match(e.request, { ignoreSearch: true })));
});
