"""Merges the map layer (a NeoRefinery JSONL export of the "WorldEndArchive map layer" project) into archive.db as the
`places` table, so knowledge and map ship as one file.
    python tools/merge_places.py archive.db places.jsonl"""
import json, sqlite3, sys

db_path, jsonl = sys.argv[1], sys.argv[2]
db = sqlite3.connect(db_path)
db.executescript("""
DROP TABLE IF EXISTS places;
CREATE TABLE places(id INTEGER PRIMARY KEY, grp TEXT, lang TEXT, name TEXT, category TEXT, summary TEXT, features TEXT,
                    city TEXT, importance INTEGER, lat REAL, lon REAL, country TEXT, osm TEXT, wikidata TEXT, source_url TEXT);
""")
n = 0
for line in open(jsonl, encoding="utf-8"):
    r = json.loads(line)
    if r.get("lat") is None or r.get("lon") is None:
        continue
    src = r.get("source") or {}
    db.execute("INSERT INTO places(grp, lang, name, category, summary, features, city, importance, lat, lon, country, osm, wikidata, source_url) "
               "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               (r.get("group"), r.get("lang") or "en", r.get("name"), r.get("category"), r.get("summary"), json.dumps(r.get("features") or [], ensure_ascii=False),
                r.get("city"), int(r.get("importance") or 3), float(r["lat"]), float(r["lon"]), r.get("country"), r.get("osm"), r.get("wikidata"),
                src.get("url")))
    n += 1
db.executescript("""
CREATE INDEX places_geo ON places(lang, lat, lon);
CREATE INDEX places_imp ON places(lang, importance DESC);
CREATE INDEX places_name ON places(lang, name);
""")
db.execute("INSERT OR REPLACE INTO info(k, v) VALUES('places', ?)", (str(n),))
db.execute("INSERT OR REPLACE INTO info(k, v) VALUES('places_license', ?)",
           ("The places table is built from OpenStreetMap data, (c) OpenStreetMap contributors, ODbL 1.0 "
            "(https://opendatacommons.org/licenses/odbl/1-0/). Each place lists its OpenStreetMap link in source_url.",))
db.commit()
db.execute("VACUUM")
print(f"{n:,} places merged into {db_path}")
