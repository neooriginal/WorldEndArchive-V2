/* WorldEndArchive reader: a static page that opens the archive SQLite file in the browser (sql.js) and searches
   it with the built-in full-text index. No server, no internet needed once loaded. */
(() => {
    const CFG = Object.assign({ db: "data/archive.db", lazy: null, defaultLang: "en", resultsPerPage: 40 }, window.ARCHIVE_CONFIG);
    const $ = (id) => document.getElementById(id);
    const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
    const store = { get(k, d) { try { return localStorage.getItem(k) ?? d; } catch { return d; } },
                    set(k, v) { try { localStorage.setItem(k, v); } catch { } } };
    let db, lazy, lang = store.get("wea-lang", CFG.defaultLang), cats = [];

    // ---------- boot: load sql.js, then the archive file (cached for offline use) ----------
    function progress(msg, frac) {
        $("boot-msg").textContent = msg;
        if (frac != null) $("boot-bar").style.width = Math.round(frac * 100) + "%";
    }

    async function loadSql() {
        const opts = { locateFile: (f) => "vendor/" + f };
        if (location.protocol === "file:") {      // browsers refuse to fetch the .wasm from a folder: use the embedded copy
            await new Promise((ok, fail) => {
                const s = document.createElement("script");
                s.src = "vendor/sql-wasm-b64.js";
                s.onload = ok; s.onerror = fail;
                document.head.appendChild(s);
            });
        }
        if (window.SQL_WASM_B64) {        // works from file:// too, where the browser refuses to fetch the .wasm
            const bin = atob(window.SQL_WASM_B64), buf = new Uint8Array(bin.length);
            for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
            opts.wasmBinary = buf;
        }
        return initSqlJs(opts);
    }

    async function cachedDb(url) {
        // Re-downloads only when the file on the server changed (size / date), so replacing the export just works.
        let cache = null;
        try { cache = await caches.open("wea-archive"); } catch { }
        let head = null;
        try { head = await fetch(url, { method: "HEAD", cache: "no-store" }); } catch { }
        const sig = head && head.ok ? (head.headers.get("content-length") || "") + "|" + (head.headers.get("last-modified") || head.headers.get("etag") || "") : null;
        if (cache) {
            const hit = await cache.match(url);
            if (hit && (!sig || hit.headers.get("x-wea-sig") === sig)) {
                progress("Opening the archive from this device…", 1);
                return new Uint8Array(await hit.arrayBuffer());
            }
        }
        const res = await fetch(url, { cache: "no-store" });
        if (!res.ok) throw new Error("HTTP " + res.status);
        // with compression (e.g. GitHub Pages) content-length is the compressed size, not what we receive
        const total = res.headers.get("content-encoding") ? 0 : (+res.headers.get("content-length") || 0);
        const reader = res.body.getReader(), parts = [];
        let got = 0;
        for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            parts.push(value);
            got += value.length;
            const known = total && got <= total;
            progress(`Downloading the archive… ${(got / 1e6).toFixed(0)}${known ? " / " + (total / 1e6).toFixed(0) : ""} MB`,
                     known ? got / total : 1 - Math.exp(-got / 4e7));
        }
        const buf = new Uint8Array(got);
        let o = 0;
        for (const p of parts) { buf.set(p, o); o += p.length; }
        if (cache && sig) {
            try { await cache.put(url, new Response(buf, { headers: { "x-wea-sig": sig } })); } catch { }
        }
        return buf;
    }

    async function boot() {
        let SQL;
        try { SQL = await loadSql(); } catch (e) { progress("This browser cannot run the reader: " + e.message); return; }
        $("file").addEventListener("change", async (ev) => {
            const f = ev.target.files[0];
            if (!f) return;
            progress("Opening " + f.name + "…", 0.5);
            open(SQL, new Uint8Array(await f.arrayBuffer()));
        });
        if (CFG.lazy && location.protocol.startsWith("http")) {
            try { return await openLazy(); } catch (e) { console.warn("lazy load failed, falling back", e); }
        }
        try {
            open(SQL, await cachedDb(CFG.db));
        } catch (e) {
            progress("No archive found at " + CFG.db + ".");
            $("boot-pick").classList.remove("hidden");
        }
    }

    // Big archives on the web: read only the parts of the file a query touches, via HTTP range requests.
    async function openLazy() {
        progress("Connecting to the archive…", 0.3);
        const worker = await createDbWorker([{ from: "jsonconfig", configUrl: new URL(CFG.lazy, location.href).toString() }],
            new URL("vendor/httpvfs/sqlite.worker.js?v=2", location.href).toString(),
            new URL("vendor/httpvfs/sql-wasm.wasm", location.href).toString());
        lazy = worker.db;
        await q("SELECT 1 FROM light LIMIT 1");
        const info = Object.fromEntries((await q("SELECT k, v FROM info")).map((r) => [r.k, r.v]));
        $("db-info").textContent = `archive built ${info.built || "?"} · ${(+info.entries || 0).toLocaleString()} entries · read on demand`;
        db = true;
        await start();
    }

    function open(SQL, bytes) {
        try {
            db = new SQL.Database(bytes);
            db.exec("SELECT 1 FROM entries LIMIT 1");
        } catch (e) {
            progress("That file is not a WorldEndArchive database.");
            $("boot-pick").classList.remove("hidden");
            return;
        }
        const info = Object.fromEntries(db.exec("SELECT k, v FROM info")[0].values);
        const n = db.exec("SELECT COUNT(*) FROM entries")[0].values[0][0];
        $("db-info").textContent = `archive built ${info.built || "?"} · ${n.toLocaleString()} entries · ${(bytes.length / 1e6).toFixed(0)} MB`;
        start();
    }

    async function start() {
        hasLight = !!(await q("SELECT name FROM sqlite_master WHERE name = 'light'")).length;
        $("boot").classList.add("hidden");
        $("app").classList.remove("hidden");
        await setupUi();
        await route();
    }

    // ---------- data ----------
    async function q(sql, params = []) {
        if (lazy) return await lazy.query(sql, params);
        const st = db.prepare(sql);
        st.bind(params);
        const out = [];
        while (st.step()) out.push(st.getAsObject());
        st.free();
        return out;
    }

    function ftsQuery(text) {
        const terms = (text.match(/[\p{L}\p{N}]+/gu) || []).slice(0, 8);
        return terms.length ? terms.map((t) => t + "*").join(" ") : null;      // FTS4: prefix match, all terms
    }

    const LIST_COLS = "e.id, e.title, e.category, e.summary, e.kind, e.importance";
    let hasLight = false;      // newer exports carry a small "light" table (no article bodies) for fast lists
    const T = () => (hasLight ? "light" : "entries");

    // relevance for FTS4: weighted hit count per column (title, summary, keywords, body) from matchinfo('pcx')
    function rank(blob) {
        const m = new Uint32Array(blob.buffer.slice(blob.byteOffset, blob.byteOffset + blob.byteLength)), p = m[0], c = m[1], w = [10, 4, 6, 1];
        let s = 0;
        for (let i = 0; i < p; i++) for (let j = 0; j < c; j++) {
            const hits = m[2 + 3 * (i * c + j)], all = m[3 + 3 * (i * c + j)];
            if (hits) s += (w[j] || 1) * (1 + Math.log(hits)) / Math.log(2 + all);
        }
        return s;
    }

    let lastSearch = { key: null, ids: [] };
    async function search(text, offset) {
        const m = ftsQuery(text);
        if (!m) return [];
        const key = m + "|" + lang;
        if (lastSearch.key !== key) {
            // two steps: rank from the full-text index alone, then look up language/importance for the best
            // candidates only (a join would make SQLite visit every row - slow when the file is read on demand)
            const hits = await q("SELECT docid id, matchinfo(entries_fts, 'pcx') mi FROM entries_fts WHERE entries_fts MATCH ?", [m]);
            hits.forEach((h) => { h.r = rank(h.mi); });
            hits.sort((a, b) => b.r - a.r);
            const top = hits.slice(0, 1500), ids = top.map((h) => h.id).sort((a, b) => a - b);
            const meta = new Map();
            for (let i = 0; i < ids.length; i += 500) {
                const part = ids.slice(i, i + 500);
                for (const r of await q(`SELECT id, lang, importance FROM ${T()} WHERE id IN (${part.join(",")})`)) meta.set(r.id, r);
            }
            const mine = top.filter((h) => meta.get(h.id)?.lang === lang);
            mine.forEach((h) => { h.r *= 1 + meta.get(h.id).importance * 0.15; });
            mine.sort((a, b) => b.r - a.r);
            lastSearch = { key, ids: mine.map((h) => h.id) };
        }
        const page = lastSearch.ids.slice(offset, offset + CFG.resultsPerPage);
        if (!page.length) return [];
        const rows = await q(`SELECT ${LIST_COLS} FROM ${T()} e WHERE e.id IN (${page.map(() => "?").join(",")})`, page);
        const pos = new Map(page.map((id, i) => [id, i]));
        return rows.sort((a, b) => pos.get(a.id) - pos.get(b.id));
    }

    function byCategory(cat, offset) {
        return q(`SELECT ${LIST_COLS} FROM ${T()} e WHERE e.lang = ? AND e.category = ?
                  ORDER BY e.importance DESC, e.title LIMIT ? OFFSET ?`, [lang, cat, CFG.resultsPerPage, offset]);
    }

    function essentials(offset) {
        return q(`SELECT ${LIST_COLS} FROM ${T()} e WHERE e.lang = ? ORDER BY e.importance DESC,
                  e.kind, e.title LIMIT ? OFFSET ?`, [lang, CFG.resultsPerPage, offset]);
    }

    // ---------- UI ----------
    async function setupUi() {
        cats = await q(`SELECT category, COUNT(*) n FROM ${T()} WHERE lang = ? GROUP BY category ORDER BY n DESC`, [lang]);
        $("cats").innerHTML = `<button data-map="1" class="map-chip">🗺️ ${lang === "de" ? "Karte" : "Map"}</button>` +
            cats.map((c) => `<button data-cat="${esc(c.category)}">${esc(c.category)}</button>`).join("");
        $("cats").onclick = (ev) => {
            if (ev.target.dataset.map) { location.hash = "#/m/"; return; }
            const c = ev.target.dataset.cat;
            if (c) location.hash = "#/c/" + encodeURIComponent(c);
        };
        document.querySelectorAll(".lang button").forEach((b) => {
            b.classList.toggle("on", b.dataset.lang === lang);
            b.onclick = async () => {
                lang = b.dataset.lang;
                store.set("wea-lang", lang);
                document.documentElement.lang = lang;
                await setupUi();
                route();
            };
        });
        let t;
        $("search-input").oninput = (ev) => {
            clearTimeout(t);
            t = setTimeout(() => {
                const v = ev.target.value.trim();
                history.replaceState(null, "", v ? "#/s/" + encodeURIComponent(v) : "#/");
                route();
            }, 250);
        };
    }

    function listHtml(rows) {
        if (!rows.length) return '<li class="muted">Nothing found.</li>';
        return rows.map((r) => `<li><a href="#/e/${r.id}"><span class="title">${esc(r.title)}</span>
            <span class="meta">${esc(r.category)}${r.kind === "howto" ? " · guide" : ""}</span>
            <span class="sum">${esc(r.summary)}</span></a></li>`).join("");
    }

    async function showList(heading, fetchPage) {
        let offset = 0;
        $("view").innerHTML = '<p class="muted">…</p>';
        const rows = await fetchPage(0);
        $("view").innerHTML = `<section><h2>${esc(heading)}</h2><ul class="link-list" id="list">${listHtml(rows)}</ul>
            ${rows.length === CFG.resultsPerPage ? '<button class="button more" id="more">Show more</button>' : ""}</section>`;
        const more = $("more");
        if (more) more.onclick = async () => {
            offset += CFG.resultsPerPage;
            const next = await fetchPage(offset);
            $("list").insertAdjacentHTML("beforeend", listHtml(next));
            if (next.length < CFG.resultsPerPage) more.remove();
        };
    }

    function md(src) {
        let html = "", list = "";
        const close = () => { if (list) { html += `</${list}>`; list = ""; } };
        for (let line of esc(src).split("\n")) {
            line = line.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/(^|[^*])\*([^*\s][^*]*?)\*/g, "$1<em>$2</em>");
            let m;
            if ((m = line.match(/^(#{1,4})\s+(.*)/))) { close(); const l = Math.min(4, Math.max(2, m[1].length)); html += `<h${l}>${m[2]}</h${l}>`; }
            else if ((m = line.match(/^\s*[-*•]\s+(.*)/))) { if (list !== "ul") { close(); html += "<ul>"; list = "ul"; } html += `<li>${m[1]}</li>`; }
            else if ((m = line.match(/^\s*\d+[.)]\s+(.*)/))) { if (list !== "ol") { close(); html += "<ol>"; list = "ol"; } html += `<li>${m[1]}</li>`; }
            else if (line.trim()) { close(); html += `<p>${line}</p>`; }
        }
        close();
        return html;
    }

    async function showEntry(id) {
        const e = (await q("SELECT * FROM entries WHERE id = ?", [id]))[0];
        if (!e) { $("view").innerHTML = '<p class="muted">Entry not found.</p>'; return; }
        const other = await q(`SELECT id, lang FROM ${T()} WHERE slug = ? AND id != ?`, [e.slug, e.id]);
        let warnings = [];
        try { warnings = JSON.parse(e.warnings || "[]"); } catch { }
        const src = e.source_url ? `<a href="${esc(e.source_url)}" target="_blank" rel="noopener">${esc(e.source_title)}</a>` : esc(e.source_title);
        $("view").innerHTML = `<div class="toolbar"><button onclick="history.back()">← back</button>
            ${other.map((o) => `<a href="#/e/${o.id}">${esc(o.lang.toUpperCase())}</a>`).join("")}</div>
            <article class="glass">
              <div class="meta">${esc(e.category)} · ${esc(e.kind === "howto" ? "guide" : e.kind)} · importance ${e.importance}/5</div>
              <h1 class="entry-title">${esc(e.title)}</h1>
              <p class="lead">${esc(e.summary)}</p>
              ${warnings.length ? `<div class="warn"><strong>⚠ ${lang === "de" ? "Sicherheit" : "Safety"}</strong><ul>${warnings.map((w) => `<li>${esc(w)}</li>`).join("")}</ul></div>` : ""}
              ${md(e.body)}
              <div class="source mono">${lang === "de" ? "Quelle" : "Source"}: ${src} · ${esc(e.license)}</div>
            </article>`;
        scrollTo(0, 0);
    }

    async function route() {
        const h = decodeURIComponent(location.hash.slice(1) || "/");
        const [, kind, arg = ""] = h.match(/^\/(\w)?\/?(.*)$/) || [];
        document.querySelectorAll("#cats button").forEach((b) => b.classList.toggle("on", kind === "c" && b.dataset.cat === arg));
        const de = lang === "de";
        document.querySelector("#cats .map-chip")?.classList.toggle("on", kind === "m");
        if (kind !== "m" && window.WEAMap) window.WEAMap.stop();
        if (kind === "m") return window.WEAMap.render($("view"), { q, lang });
        if (kind === "e") return showEntry(+arg);
        if (kind === "s" && arg) {
            if ($("search-input").value.trim() !== arg) $("search-input").value = arg;
            return showList(de ? `Ergebnisse für „${arg}“` : `Results for “${arg}”`, (o) => search(arg, o));
        }
        if (kind === "c" && arg) return showList(arg, (o) => byCategory(arg, o));
        $("search-input").value = "";
        showList(de ? "Das Wichtigste" : "Essentials", essentials);
    }

    window.addEventListener("hashchange", () => db && route());
    document.documentElement.lang = lang;
    if ("serviceWorker" in navigator && location.protocol.startsWith("http")) navigator.serviceWorker.register("sw.js").catch(() => { });
    boot();
})();
