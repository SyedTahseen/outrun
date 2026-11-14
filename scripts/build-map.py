#!/usr/bin/env python3
"""
Builds app/src/main/assets/world.bin: real country polygons for the home-screen map.

Source: Natural Earth admin-0 countries (public domain), 1:50m scale.
  python3 scripts/build-map.py                 # downloads the GeoJSON
  python3 scripts/build-map.py --src file.geojson   # use a local copy (offline)
  python3 scripts/build-map.py --scale 110     # smaller / coarser (1:110m)

Binary format (big-endian), read by app.xvpn.ui.WorldGeo:
  int32  magic 0x574D5031 ("WMP1")
  uint16 countryCount
  per country:
    2 bytes ASCII ISO-3166 alpha-2 code ("--" when the country has none)
    uint16 ringCount
    per ring: int32 pointCount, then pointCount x (int16 lon*100, int16 lat*100)
Rings of one country go into one path drawn with the even-odd rule, so holes (e.g. Lesotho) just work.
Only the standard library is used, so it runs on any CI image.
"""
import argparse
import json
import os
import struct
import sys
import urllib.request

URL = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/"
       "geojson/ne_{scale}m_admin_0_countries.geojson")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "world.bin")
Q = 100          # 0.01 degree ~ 1 km, far below a pixel at the app's zoom
MIN_SPAN = 4     # drop islets smaller than 0.04 degrees in both directions


def iso2(props):
    # Natural Earth uses "-99" for several countries (France, Norway...) in ISO_A2; the *_EH field fixes most.
    for key in ("ISO_A2", "ISO_A2_EH", "WB_A2", "iso_a2", "iso_a2_eh"):
        v = props.get(key)
        if isinstance(v, str) and len(v) == 2 and v.isalpha():
            return v.upper()
    return "--"


def polygons(geom):
    if geom["type"] == "Polygon":
        return [geom["coordinates"]]
    if geom["type"] == "MultiPolygon":
        return geom["coordinates"]
    return []


def quantize(ring):
    pts, last = [], None
    for lon, lat, *_ in ring:
        p = (max(-18000, min(18000, round(lon * Q))), max(-9000, min(9000, round(lat * Q))))
        if p != last:
            pts.append(p)
            last = p
    if len(pts) > 1 and pts[0] == pts[-1]:
        pts.pop()
    return pts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", help="local GeoJSON instead of downloading")
    ap.add_argument("--scale", default="50", choices=["10", "50", "110"])
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--min-countries", type=int, default=100, help="sanity check")
    a = ap.parse_args()

    if a.src:
        data = json.load(open(a.src, encoding="utf-8"))
    else:
        url = URL.format(scale=a.scale)
        print("Downloading", url)
        with urllib.request.urlopen(url, timeout=60) as r:
            data = json.loads(r.read().decode("utf-8"))

    countries = []
    for f in data["features"]:
        props = f.get("properties", {})
        code = iso2(props)
        if code == "AQ" or props.get("ADM0_A3") == "ATA":
            continue  # Antarctica: huge, useless for a VPN map
        rings = []
        for poly in polygons(f["geometry"]):
            for ring in poly:
                pts = quantize(ring)
                if len(pts) < 3:
                    continue
                xs = [p[0] for p in pts]
                ys = [p[1] for p in pts]
                if max(xs) - min(xs) < MIN_SPAN and max(ys) - min(ys) < MIN_SPAN:
                    continue
                rings.append(pts)
        if rings:
            countries.append((code, rings))

    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "wb") as o:
        o.write(struct.pack(">IH", 0x574D5031, len(countries)))
        for code, rings in countries:
            o.write(code.encode("ascii"))
            o.write(struct.pack(">H", len(rings)))
            for pts in rings:
                o.write(struct.pack(">i", len(pts)))
                o.write(b"".join(struct.pack(">hh", x, y) for x, y in pts))

    n_pts = sum(len(r) for _, rs in countries for r in rs)
    print("%d countries, %d points -> %s (%d KB)" % (len(countries), n_pts, a.out, os.path.getsize(a.out) // 1024))
    if len(countries) < a.min_countries:
        sys.exit("ERROR: suspiciously few countries - is this the admin_0_countries file?")


if __name__ == "__main__":
    main()
