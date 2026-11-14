/* WorldEndArchive reader: a static page that opens the archive SQLite file in the browser (sql.js) and searches
   it with the built-in full-text index. No server, no internet needed once loaded. */
(() => {
    const CFG = Object.assign({ db: "data/archive.db", defaultLang: "en", resultsPerPage: 40 }, window.ARCHIVE_CONFIG);
    const $ = (id) => document.getElementById(id);
    const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
    const store = { get(k, d) { try { return localStorage.getItem(k) ?? d; } catch { return d; } },
                    set(k, v) { try { localStorage.setItem(k, v); } catch { } } };
    let db, lang = store.get("wea-lang", CFG.defaultLang), cats = [];

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
        try {
            open(SQL, await cachedDb(CFG.db));
        } catch (e) {
            progress("No archive found at " + CFG.db + ".");
            $("boot-pick").classList.remove("hidden");
        }
    }

    function open(SQL, bytes) {
        try {
            db = new SQL.Database(bytes);
            q("SELECT 1 FROM entries LIMIT 1");
        } catch (e) {
            progress("That file is not a WorldEndArchive database.");
            $("boot-pick").classList.remove("hidden");
            return;
        }
        const info = Object.fromEntries(q("SELECT k, v FROM info").map((r) => [r.k, r.v]));
        const n = q("SELECT COUNT(*) n FROM entries")[0].n;
        $("db-info").textContent = `archive built ${info.built || "?"} · ${n.toLocaleString()} entries · ${(bytes.length / 1e6).toFixed(0)} MB`;
        // relevance for FTS4: weighted hit count per column (title, summary, keywords, body) from matchinfo('pcx')
        db.create_function("rank", (blob) => {
            const m = new Uint32Array(blob.buffer, blob.byteOffset, blob.byteLength / 4), p = m[0], c = m[1], w = [10, 4, 6, 1];
            let s = 0;
            for (let i = 0; i < p; i++) for (let j = 0; j < c; j++) {
                const hits = m[2 + 3 * (i * c + j)], all = m[3 + 3 * (i * c + j)];
                if (hits) s += (w[j] || 1) * (1 + Math.log(hits)) / Math.log(2 + all);
            }
            return s;
        });
        $("boot").classList.add("hidden");
        $("app").classList.remove("hidden");
        setupUi();
        route();
    }

    // ---------- data ----------
    function q(sql, params = []) {
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

    function search(text, offset) {
        const m = ftsQuery(text);
        if (!m) return [];
        return q(`SELECT ${LIST_COLS} FROM (SELECT docid, rank(matchinfo(entries_fts, 'pcx')) r FROM entries_fts
                  WHERE entries_fts MATCH ?) f JOIN entries e ON e.id = f.docid WHERE e.lang = ?
                  ORDER BY f.r * (1 + e.importance * 0.15) DESC LIMIT ? OFFSET ?`,
                 [m, lang, CFG.resultsPerPage, offset]);
    }

    function byCategory(cat, offset) {
        return q(`SELECT ${LIST_COLS} FROM entries e WHERE e.lang = ? AND e.category = ?
                  ORDER BY e.importance DESC, e.title LIMIT ? OFFSET ?`, [lang, cat, CFG.resultsPerPage, offset]);
    }

    function essentials(offset) {
        return q(`SELECT ${LIST_COLS} FROM entries e WHERE e.lang = ? ORDER BY e.importance DESC,
                  CASE WHEN e.kind = 'howto' THEN 0 ELSE 1 END, e.title LIMIT ? OFFSET ?`, [lang, CFG.resultsPerPage, offset]);
    }

    // ---------- UI ----------
    function setupUi() {
        cats = q("SELECT category, COUNT(*) n FROM entries WHERE lang = ? GROUP BY category ORDER BY n DESC", [lang]);
        $("cats").innerHTML = cats.map((c) => `<button data-cat="${esc(c.category)}">${esc(c.category)}</button>`).join("");
        $("cats").onclick = (ev) => {
            const c = ev.target.dataset.cat;
            if (c) location.hash = "#/c/" + encodeURIComponent(c);
        };
        document.querySelectorAll(".lang button").forEach((b) => {
            b.classList.toggle("on", b.dataset.lang === lang);
            b.onclick = () => {
                lang = b.dataset.lang;
                store.set("wea-lang", lang);
                document.documentElement.lang = lang;
                setupUi();
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

    function showList(heading, fetchPage) {
        let offset = 0;
        const rows = fetchPage(0);
        $("view").innerHTML = `<section><h2>${esc(heading)}</h2><ul class="link-list" id="list">${listHtml(rows)}</ul>
            ${rows.length === CFG.resultsPerPage ? '<button class="button more" id="more">Show more</button>' : ""}</section>`;
        const more = $("more");
        if (more) more.onclick = () => {
            offset += CFG.resultsPerPage;
            const next = fetchPage(offset);
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

    function showEntry(id) {
        const e = q("SELECT * FROM entries WHERE id = ?", [id])[0];
        if (!e) { $("view").innerHTML = '<p class="muted">Entry not found.</p>'; return; }
        const other = q("SELECT id, lang FROM entries WHERE slug = ? AND id != ?", [e.slug, e.id]);
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

    function route() {
        const h = decodeURIComponent(location.hash.slice(1) || "/");
        const [, kind, arg = ""] = h.match(/^\/(\w)?\/?(.*)$/) || [];
        document.querySelectorAll("#cats button").forEach((b) => b.classList.toggle("on", kind === "c" && b.dataset.cat === arg));
        const de = lang === "de";
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
