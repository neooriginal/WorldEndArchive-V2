/* WorldEndArchive offline map: Natural Earth base map drawn on a canvas (no tiles, no internet), waypoints from the
   archive's `places` table, your GPS position and a compass that points to the selected place. */
(() => {
    const D2R = Math.PI / 180, TILE = 256;
    const CAT = {
        hospital: ["🏥", "#ff5c6c"], shelter: ["🛡️", "#ffcf70"], "rescue station": ["🚑", "#ff8f3d"], hut: ["🛖", "#7ee081"],
        airport: ["✈️", "#7aa7ff"], port: ["⚓", "#5ad1ff"], "power plant": ["⚡", "#e3d26f"], dam: ["🌊", "#3fb5ff"],
        "water works": ["🚰", "#3fd5ff"], embassy: ["🏛️", "#c79bff"],
    };
    const T = {
        en: { title: "Map", nearest: "Nearest to you", locate: "Show my position", compass: "Compass", noPlaces: "The map layer (hospitals, shelters, rescue stations…) arrives with the next archive update. The world map, cities and your position already work offline.", search: "Find a place…", far: "away", heading: "Heading", toTarget: "to", allowCompass: "Tap to enable the compass", noCompass: "No compass sensor - showing the direction relative to north.", gps: "Waiting for GPS…", nogps: "Position not available (allow location, go outside).", all: "All", close: "Close", navigate: "Point me there", coords: "Coordinates", cities: "cities", places: "places" },
        de: { title: "Karte", nearest: "In deiner Nähe", locate: "Meine Position", compass: "Kompass", noPlaces: "Die Kartenebene (Krankenhäuser, Schutzräume, Rettungsstationen…) kommt mit dem nächsten Archiv-Update. Weltkarte, Städte und deine Position funktionieren schon offline.", search: "Ort suchen…", far: "entfernt", heading: "Richtung", toTarget: "zu", allowCompass: "Tippen, um den Kompass zu aktivieren", noCompass: "Kein Kompass-Sensor – Richtung relativ zu Norden.", gps: "Warte auf GPS…", nogps: "Keine Position (Standort erlauben, nach draußen gehen).", all: "Alle", close: "Schließen", navigate: "Dorthin zeigen", coords: "Koordinaten", cities: "Städte", places: "Orte" },
    };
    const CAT_DE = { hospital: "Krankenhaus", shelter: "Schutzraum", "rescue station": "Rettungsstation", hut: "Hütte", airport: "Flughafen", port: "Hafen",
        "power plant": "Kraftwerk", dam: "Staudamm", "water works": "Wasserwerk", embassy: "Botschaft" };

    let base = null, basePaths = null;
    const st = { lon: 10, lat: 30, z: 1.6, me: null, heading: null, target: null, filter: null, places: [], hasPlaces: null, watch: null };

    // --- projection (Web Mercator, world = 256 px at zoom 0)
    const wx = (lon) => (lon + 180) / 360 * TILE;
    const wy = (lat) => { const s = Math.sin(Math.max(-85, Math.min(85, lat)) * D2R); return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * TILE; };
    const unx = (x) => x / TILE * 360 - 180;
    const uny = (y) => { const n = Math.PI - 2 * Math.PI * y / TILE; return Math.atan(Math.sinh(n)) / D2R; };

    function dist(a, b) {           // km
        const dLat = (b.lat - a.lat) * D2R, dLon = (b.lon - a.lon) * D2R;
        const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * D2R) * Math.cos(b.lat * D2R) * Math.sin(dLon / 2) ** 2;
        return 12742 * Math.asin(Math.sqrt(h));
    }
    function bearing(a, b) {
        const y = Math.sin((b.lon - a.lon) * D2R) * Math.cos(b.lat * D2R);
        const x = Math.cos(a.lat * D2R) * Math.sin(b.lat * D2R) - Math.sin(a.lat * D2R) * Math.cos(b.lat * D2R) * Math.cos((b.lon - a.lon) * D2R);
        return (Math.atan2(y, x) / D2R + 360) % 360;
    }
    const km = (d) => d < 1 ? `${Math.round(d * 1000)} m` : d < 20 ? `${d.toFixed(1)} km` : `${Math.round(d).toLocaleString()} km`;
    const dir = (b) => ["N", "NE", "E", "SE", "S", "SW", "W", "NW"][Math.round(b / 45) % 8];
    const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

    async function loadBase() {
        if (base) return;
        base = await (await fetch("data/basemap.json")).json();
        const s = base.scale, mk = (r) => {
            const p = new Path2D();
            for (let i = 0; i < r.length; i += 2) { const x = wx(r[i] / s), y = wy(r[i + 1] / s); i ? p.lineTo(x, y) : p.moveTo(x, y); }
            p.closePath();
            return p;
        };
        const land = new Path2D(), lakes = new Path2D();
        for (const c of base.countries) for (const r of c.r) land.addPath(mk(r));
        for (const r of base.lakes) lakes.addPath(mk(r));
        basePaths = { land, lakes };
    }

    // ------------------------------------------------------------------------------------------------ view
    async function render(el, ctx) {
        const L = T[ctx.lang] || T.en, cname = (c) => (ctx.lang === "de" ? CAT_DE[c] : c) || c;
        el.innerHTML = `<section class="map-wrap">
            <div class="map-top"><input type="search" id="map-q" placeholder="${L.search}" autocomplete="off">
              <div class="chips" id="map-cats"></div></div>
            <div class="map-stage glass" id="map-stage">
              <canvas id="map"></canvas>
              <div class="map-ctrl"><button id="m-in" aria-label="Zoom in">+</button><button id="m-out" aria-label="Zoom out">−</button>
                <button id="m-me" title="${L.locate}" aria-label="${L.locate}">⌖</button><button id="m-cmp" title="${L.compass}" aria-label="${L.compass}">🧭</button>
                <button id="m-full" aria-label="Fullscreen">⛶</button></div>
              <div class="map-status mono" id="map-status"></div>
              <div class="compass hidden" id="compass"><div class="dial" id="dial"><span class="n">N</span><span class="e">E</span><span class="s">S</span><span class="w">W</span></div>
                <div class="arrow" id="arrow"></div><div class="readout" id="readout"></div></div>
            </div>
            <div id="map-info"></div><div id="map-list"></div></section>`;
        await loadBase();
        const cv = el.querySelector("#map"), stage = el.querySelector("#map-stage"), g = cv.getContext("2d");
        let W = 0, H = 0, dpr = 1;
        const size = () => { dpr = window.devicePixelRatio || 1; W = stage.clientWidth; H = stage.clientHeight; cv.width = W * dpr; cv.height = H * dpr; cv.style.width = W + "px"; cv.style.height = H + "px"; st.z = Math.max(st.z, Math.log2(W / TILE)); draw(); };
        const scale = () => 2 ** st.z;
        const toScreen = (lon, lat) => { const s = scale(); return [(wx(lon) - wx(st.lon)) * s + W / 2, (wy(lat) - wy(st.lat)) * s + H / 2]; };
        const toGeo = (x, y) => { const s = scale(); return [unx(wx(st.lon) + (x - W / 2) / s), uny(wy(st.lat) + (y - H / 2) / s)]; };

        function draw() {
            if (!W) return;
            const s = scale();
            g.setTransform(dpr, 0, 0, dpr, 0, 0);
            g.fillStyle = "#04121b"; g.fillRect(0, 0, W, H);
            g.save();
            g.translate(W / 2 - wx(st.lon) * s, H / 2 - wy(st.lat) * s);
            g.scale(s, s);
            g.fillStyle = "#0f1a22"; g.fill(basePaths.land);
            g.lineWidth = 0.9 / s; g.strokeStyle = "rgba(0,242,255,0.22)"; g.stroke(basePaths.land);
            g.fillStyle = "#04121b"; g.fill(basePaths.lakes);
            g.restore();
            // cities: more at higher zoom
            const maxRank = st.z < 2.5 ? 1 : st.z < 3.5 ? 3 : st.z < 4.5 ? 5 : st.z < 6 ? 7 : 10, sc = base.scale;
            g.font = "11px Inter, system-ui, sans-serif"; g.textBaseline = "middle";
            const taken = [];
            for (const c of base.cities) {
                if (c[3] > maxRank) break;
                const [x, y] = toScreen(c[1] / sc, c[2] / sc);
                if (x < -40 || y < -20 || x > W + 40 || y > H + 20) continue;
                if (taken.some(([a, b]) => Math.abs(a - x) < 60 && Math.abs(b - y) < 14)) continue;
                taken.push([x, y]);
                g.fillStyle = c[6] ? "#ffffff" : "rgba(255,255,255,0.6)";
                g.beginPath(); g.arc(x, y, c[6] ? 2.6 : 1.8, 0, 7); g.fill();
                g.fillStyle = "rgba(220,230,240,0.75)"; g.fillText(c[0], x + 5, y);
            }
            // waypoints
            const r = st.z < 4 ? 5 : 7;
            for (const p of st.places) {
                if (st.filter && p.category !== st.filter) continue;
                const [x, y] = toScreen(p.lon, p.lat);
                if (x < -10 || y < -10 || x > W + 10 || y > H + 10) continue;
                p._x = x; p._y = y;
                const col = (CAT[p.category] || ["", "#00f2ff"])[1];
                g.fillStyle = col; g.strokeStyle = "#050505"; g.lineWidth = 1.5;
                g.beginPath(); g.arc(x, y, st.target && st.target.id === p.id ? r + 3 : r, 0, 7); g.fill(); g.stroke();
                if (st.z >= 8 && !taken.some(([a, b]) => Math.abs(a - x) < 110 && Math.abs(b - y) < 14)) {    // labels without overlap
                    taken.push([x, y]);
                    g.fillStyle = "rgba(255,255,255,0.85)"; g.fillText(p.name.replace(/\s*\(.*\)$/, "").slice(0, 32), x + r + 4, y);
                }
            }
            // me
            if (st.me) {
                const [x, y] = toScreen(st.me.lon, st.me.lat);
                g.fillStyle = "rgba(0,242,255,0.18)"; g.beginPath(); g.arc(x, y, 16, 0, 7); g.fill();
                g.fillStyle = "#00f2ff"; g.strokeStyle = "#fff"; g.lineWidth = 2; g.beginPath(); g.arc(x, y, 6, 0, 7); g.fill(); g.stroke();
                if (st.target) {
                    const [tx, ty] = toScreen(st.target.lon, st.target.lat);
                    g.setLineDash([6, 6]); g.strokeStyle = "rgba(0,242,255,0.6)"; g.lineWidth = 2;
                    g.beginPath(); g.moveTo(x, y); g.lineTo(tx, ty); g.stroke(); g.setLineDash([]);
                }
            }
        }

        // ---- data
        let qTimer;
        async function loadPlaces() {
            if (st.hasPlaces === null) {
                try { st.hasPlaces = !!(await ctx.q("SELECT name FROM sqlite_master WHERE name = 'places'")).length; } catch { st.hasPlaces = false; }
                if (st.hasPlaces) {
                    const cats = await ctx.q("SELECT category, COUNT(*) n FROM places WHERE lang = ? GROUP BY category ORDER BY n DESC", [ctx.lang]);
                    el.querySelector("#map-cats").innerHTML = `<button class="on" data-c="">${L.all}</button>` + cats.map((c) => `<button data-c="${esc(c.category)}">${(CAT[c.category] || ["📍"])[0]} ${esc(cname(c.category))}</button>`).join("");
                    el.querySelectorAll("#map-cats button").forEach((b) => (b.onclick = () => { st.filter = b.dataset.c || null; el.querySelectorAll("#map-cats button").forEach((x) => x.classList.toggle("on", x === b)); draw(); list(); }));
                } else {
                    el.querySelector("#map-info").innerHTML = `<p class="muted map-note">${L.noPlaces}</p>`;
                }
            }
            if (!st.hasPlaces) return;
            const [lon0, lat1] = toGeo(0, 0), [lon1, lat0] = toGeo(W, H);
            const minImp = st.z < 3 ? 5 : st.z < 4.5 ? 4 : 3;
            st.places = await ctx.q(`SELECT id, name, category, importance, lat, lon FROM places WHERE lang = ? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
                                     AND importance >= ? ORDER BY importance DESC LIMIT 600`, [ctx.lang, lat0, lat1, Math.max(-180, lon0), Math.min(180, lon1), minImp]);
            draw();
        }
        const refresh = () => { clearTimeout(qTimer); qTimer = setTimeout(() => { loadPlaces().then(list); status(); }, 250); };

        async function list() {
            const box = el.querySelector("#map-list");
            if (!st.hasPlaces || !st.me) { box.innerHTML = ""; return; }
            let rows = [];
            for (const d of [1, 3, 8]) {
                rows = await ctx.q(`SELECT id, name, category, city, importance, lat, lon FROM places WHERE lang = ? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
                                    ${st.filter ? "AND category = ?" : ""} LIMIT 400`,
                    [ctx.lang, st.me.lat - d, st.me.lat + d, st.me.lon - d * 1.5, st.me.lon + d * 1.5, ...(st.filter ? [st.filter] : [])]);
                if (rows.length >= 8) break;
            }
            rows.forEach((p) => { p.d = dist(st.me, p); });
            rows.sort((a, b) => a.d - b.d);
            box.innerHTML = rows.length ? `<h2 class="section-title">${L.nearest}</h2><ul class="link-list">${rows.slice(0, 12).map((p) =>
                `<li><a href="#" data-p="${p.id}"><span class="title">${(CAT[p.category] || ["📍"])[0]} ${esc(p.name)}</span><span class="meta">${km(p.d)} ${dir(bearing(st.me, p))} · ${esc(cname(p.category))}${p.city ? " · " + esc(p.city) : ""}</span></a></li>`).join("")}</ul>` : "";
            box.querySelectorAll("[data-p]").forEach((a) => (a.onclick = (e) => { e.preventDefault(); select(+a.dataset.p, true); }));
        }

        async function select(id, center) {
            const p = (await ctx.q("SELECT * FROM places WHERE id = ?", [id]))[0];
            if (!p) return;
            st.target = p;
            if (center) { st.lon = p.lon; st.lat = p.lat; st.z = Math.max(st.z, 9); refresh(); }
            let feats = [];
            try { feats = JSON.parse(p.features || "[]"); } catch { }
            const away = st.me ? `<p class="mono">${km(dist(st.me, p))} ${L.far} · ${Math.round(bearing(st.me, p))}° ${dir(bearing(st.me, p))}</p>` : "";
            el.querySelector("#map-info").innerHTML = `<article class="glass place-card"><div class="meta">${(CAT[p.category] || ["📍"])[0]} ${esc(cname(p.category))}${p.city ? " · " + esc(p.city) : ""} · ${esc(p.country || "")}</div>
                <h2 class="entry-title">${esc(p.name)}</h2><p class="lead">${esc(p.summary || "")}</p>
                ${feats.length ? `<ul>${feats.map((f) => `<li>${esc(f)}</li>`).join("")}</ul>` : ""}${away}
                <p class="mono">${L.coords}: ${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}</p>
                <div class="toolbar"><button id="m-go">🧭 ${L.navigate}</button><button id="m-x">${L.close}</button></div></article>`;
            el.querySelector("#m-go").onclick = () => startCompass();
            el.querySelector("#m-x").onclick = () => { st.target = null; el.querySelector("#map-info").innerHTML = ""; draw(); updateCompass(); };
            draw(); updateCompass();
        }

        function status() {
            const s = el.querySelector("#map-status");
            s.textContent = `${st.lat.toFixed(2)}, ${st.lon.toFixed(2)} · z${st.z.toFixed(1)}${st.hasPlaces ? ` · ${st.places.length} ${L.places}` : ""}`;
        }

        // ---- position + compass
        function locate() {
            if (!navigator.geolocation) return;
            el.querySelector("#map-status").textContent = L.gps;
            if (st.watch != null) navigator.geolocation.clearWatch(st.watch);
            let first = true;
            st.watch = navigator.geolocation.watchPosition((pos) => {
                st.me = { lat: pos.coords.latitude, lon: pos.coords.longitude, acc: pos.coords.accuracy };
                if (first) { st.lon = st.me.lon; st.lat = st.me.lat; st.z = Math.max(st.z, 8); first = false; refresh(); }
                draw(); updateCompass();
            }, () => { el.querySelector("#map-status").textContent = L.nogps; }, { enableHighAccuracy: true, maximumAge: 10000, timeout: 60000 });
        }
        function onOrient(e) {
            let h = null;
            if (typeof e.webkitCompassHeading === "number") h = e.webkitCompassHeading;
            else if (e.absolute && typeof e.alpha === "number") h = (360 - e.alpha) % 360;
            if (h != null) { st.heading = h; updateCompass(); }
        }
        async function startCompass() {
            el.querySelector("#compass").classList.remove("hidden");
            if (!st.me) locate();
            try {
                if (typeof DeviceOrientationEvent !== "undefined" && DeviceOrientationEvent.requestPermission) await DeviceOrientationEvent.requestPermission();
            } catch { }
            window.removeEventListener("deviceorientationabsolute", onOrient); window.removeEventListener("deviceorientation", onOrient);
            window.addEventListener("ondeviceorientationabsolute" in window ? "deviceorientationabsolute" : "deviceorientation", onOrient);
            updateCompass();
        }
        function updateCompass() {
            const c = el.querySelector("#compass");
            if (!c || c.classList.contains("hidden")) return;
            const h = st.heading;
            el.querySelector("#dial").style.transform = `rotate(${-(h || 0)}deg)`;
            const arrow = el.querySelector("#arrow"), out = el.querySelector("#readout");
            if (st.target && st.me) {
                const b = bearing(st.me, st.target);
                arrow.style.display = "block";
                arrow.style.transform = `translate(-50%, -100%) rotate(${b - (h || 0)}deg)`;
                out.innerHTML = `<b>${km(dist(st.me, st.target))}</b><br>${L.toTarget} ${esc(st.target.name)}<br>${Math.round(b)}° ${dir(b)}${h == null ? "<br><small>" + L.noCompass + "</small>" : ""}`;
            } else {
                arrow.style.display = "none";
                out.innerHTML = h != null ? `${L.heading} ${Math.round(h)}° ${dir(h)}` : (st.me ? L.allowCompass : L.gps);
            }
        }

        // ---- gestures
        const ptrs = new Map();
        let last = null, pinch = null, moved = false;
        cv.addEventListener("pointerdown", (e) => { cv.setPointerCapture(e.pointerId); ptrs.set(e.pointerId, [e.offsetX, e.offsetY]); last = [e.offsetX, e.offsetY]; moved = false; });
        cv.addEventListener("pointermove", (e) => {
            if (!ptrs.has(e.pointerId)) return;
            ptrs.set(e.pointerId, [e.offsetX, e.offsetY]);
            if (ptrs.size === 2) {
                const [a, b] = [...ptrs.values()], d = Math.hypot(a[0] - b[0], a[1] - b[1]);
                if (pinch) zoomAt((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, Math.log2(d / pinch));
                pinch = d; moved = true;
                return;
            }
            const dx = e.offsetX - last[0], dy = e.offsetY - last[1];
            if (Math.abs(dx) + Math.abs(dy) > 2) moved = true;
            const s = scale();
            st.lon = unx(wx(st.lon) - dx / s); st.lat = uny(Math.max(1, Math.min(TILE - 1, wy(st.lat) - dy / s)));
            if (st.lon > 180) st.lon -= 360; if (st.lon < -180) st.lon += 360;
            last = [e.offsetX, e.offsetY];
            draw();
        });
        const up = (e) => {
            ptrs.delete(e.pointerId); pinch = null;
            if (ptrs.size === 1) last = [...ptrs.values()][0];
            if (!moved && e.type === "pointerup") tap(e.offsetX, e.offsetY);
            refresh();
        };
        cv.addEventListener("pointerup", up); cv.addEventListener("pointercancel", up);
        cv.addEventListener("wheel", (e) => { e.preventDefault(); zoomAt(e.offsetX, e.offsetY, -e.deltaY / 400); refresh(); }, { passive: false });
        function zoomAt(x, y, dz) {
            const [lon, lat] = toGeo(x, y);
            st.z = Math.max(Math.log2(W / TILE), Math.min(16, st.z + dz));
            const s = scale();
            st.lon = unx(wx(lon) - (x - W / 2) / s); st.lat = uny(wy(lat) - (y - H / 2) / s);
            draw();
        }
        function tap(x, y) {
            let best = null, bd = 18;
            for (const p of st.places) { if (p._x == null || (st.filter && p.category !== st.filter)) continue; const d = Math.hypot(p._x - x, p._y - y); if (d < bd) { bd = d; best = p; } }
            if (best) select(best.id, false);
        }
        el.querySelector("#m-in").onclick = () => { zoomAt(W / 2, H / 2, 1); refresh(); };
        el.querySelector("#m-out").onclick = () => { zoomAt(W / 2, H / 2, -1); refresh(); };
        el.querySelector("#m-me").onclick = locate;
        el.querySelector("#m-cmp").onclick = () => { const c = el.querySelector("#compass"); if (c.classList.contains("hidden")) startCompass(); else c.classList.add("hidden"); };
        el.querySelector("#m-full").onclick = () => { stage.classList.toggle("full"); size(); };
        el.querySelector("#compass").onclick = startCompass;
        let sq;
        el.querySelector("#map-q").oninput = (e) => {
            clearTimeout(sq);
            sq = setTimeout(async () => {
                const v = e.target.value.trim();
                if (!v) return;
                if (st.hasPlaces) {
                    const r = await ctx.q("SELECT id FROM places WHERE lang = ? AND name LIKE ? ORDER BY importance DESC LIMIT 1", [ctx.lang, `%${v}%`]);
                    if (r.length) return select(r[0].id, true);
                }
                const c = base.cities.find((c) => c[0].toLowerCase().startsWith(v.toLowerCase()));
                if (c) { st.lon = c[1] / base.scale; st.lat = c[2] / base.scale; st.z = Math.max(st.z, 7); refresh(); draw(); }
            }, 350);
        };
        new ResizeObserver(size).observe(stage);
        size();
        refresh();
    }

    window.WEAMap = { render, stop: () => { if (st.watch != null) navigator.geolocation.clearWatch(st.watch); st.watch = null; } };
})();
