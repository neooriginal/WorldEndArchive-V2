"""Builds reader/data/basemap.json (also used by the Android app) from Natural Earth (public domain):
country outlines, lakes and ~7,000 cities, with coordinates as integers (degrees × 100) to keep it small.
    python tools/build_basemap.py <dir with ne_50m_admin_0_countries / ne_50m_lakes / ne_10m_populated_places_simple .geojson>"""
import json, os, sys

src = sys.argv[1]
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def rings(geom):
    polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
    for poly in polys:
        for ring in poly[:1]:                      # outer ring only; holes are rare at this scale
            flat, last = [], None
            for lon, lat in ring:
                p = (round(lon * 100), round(lat * 100))
                if p != last:
                    flat += p
                    last = p
            if len(flat) >= 8:
                yield flat


countries = []
for f in json.load(open(os.path.join(src, "ne_50m_admin_0_countries.geojson")))["features"]:
    p = f["properties"]
    countries.append({"n": p.get("NAME_EN") or p.get("NAME"), "de": p.get("NAME_DE") or p.get("NAME"), "iso": p.get("ISO_A2_EH") or p.get("ISO_A2"),
                      "l": [round(p.get("LABEL_X", 0) * 100), round(p.get("LABEL_Y", 0) * 100)], "r": list(rings(f["geometry"]))})
lakes = [r for f in json.load(open(os.path.join(src, "ne_50m_lakes.geojson")))["features"] for r in rings(f["geometry"])]
cities = []
for f in json.load(open(os.path.join(src, "ne_10m_populated_places_simple.geojson")))["features"]:
    p = f["properties"]
    lon, lat = f["geometry"]["coordinates"][:2]
    cities.append([p["name"], round(lon * 100), round(lat * 100), int(p.get("scalerank", 10)), int(p.get("pop_max") or 0), p.get("iso_a2") or "",
                   1 if p.get("adm0cap") else 0])
cities.sort(key=lambda c: (c[3], -c[4]))
out = {"v": 1, "scale": 100, "source": "Natural Earth (public domain)", "countries": countries, "lakes": lakes, "cities": cities}
path = os.path.join(root, "reader", "data", "basemap.json")
json.dump(out, open(path, "w"), separators=(",", ":"), ensure_ascii=False)
print(path, os.path.getsize(path) // 1024, "KB:", len(countries), "countries,", len(lakes), "lakes,", len(cities), "cities")
