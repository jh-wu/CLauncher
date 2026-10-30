#!/usr/bin/env python3
"""Fetches freely licensed landscape photos from Wikimedia Commons' Picture of the Day feed.

  candidates DIR DAYS   Save small previews of recent landscape pictures, plus candidates.json.
  bundle LIST OUT_DIR   Download TV-sized copies of the pictures named in LIST (a JSON list of
                        Commons file titles from candidates.json) and write credits.json.

Runs in CI, where Wikimedia is reachable.
"""
import datetime
import json
import os
import re
import sys
import urllib.request

FEED = "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/{:%Y/%m/%d}"
UA = "CLauncher/1.0 (https://github.com/jh-wu/CLauncher)"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def sized(thumb, width):
    return re.sub(r"/\d+px-", f"/{width}px-", thumb)


def entries(days):
    today = datetime.date.today()
    for back in range(days):
        day = today - datetime.timedelta(days=back)
        try:
            image = json.loads(get(FEED.format(day))).get("image")
        except Exception as e:  # noqa: BLE001 - a missing day shouldn't stop the run
            print(f"{day}: {e}", file=sys.stderr)
            continue
        if not image:
            continue
        original = image["image"]
        if original["width"] < original["height"] * 1.4 or original["width"] < 1920:
            continue
        yield {
            "date": str(day),
            "file": image["title"],
            "description": (image.get("description") or {}).get("text", "").strip(),
            "artist": (image.get("artist") or {}).get("text", "").strip(),
            "license": (image.get("license") or {}).get("type", "").strip(),
            "thumbnail": image["thumbnail"]["source"],
            "source": original["source"],
        }


def candidates(out_dir, days):
    os.makedirs(os.path.join(out_dir, "previews"), exist_ok=True)
    found = []
    for i, entry in enumerate(entries(days)):
        name = f"{i:03d}.jpg"
        with open(os.path.join(out_dir, "previews", name), "wb") as f:
            f.write(get(sized(entry["thumbnail"], 500)))
        entry["preview"] = name
        found.append(entry)
        print(name, entry["file"])
    with open(os.path.join(out_dir, "candidates.json"), "w") as f:
        json.dump(found, f, indent=1, ensure_ascii=False)


def bundle(list_path, out_dir, days=400):
    wanted = json.load(open(list_path))
    os.makedirs(out_dir, exist_ok=True)
    by_file = {e["file"]: e for e in entries(days) if e["file"] in wanted}
    credits = []
    for i, title in enumerate(wanted):
        entry = by_file[title]
        name = f"photo_{i + 1:02d}.jpg"
        with open(os.path.join(out_dir, name), "wb") as f:
            f.write(get(sized(entry["thumbnail"], 1920)))
        credit = " · ".join(x for x in (entry["artist"], entry["license"], "Wikimedia Commons") if x)
        credits.append({"asset": name, "title": entry["description"].split("\n")[0][:140], "credit": credit})
        print(name, title)
    with open(os.path.join(out_dir, "credits.json"), "w") as f:
        json.dump(credits, f, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    if sys.argv[1] == "candidates":
        candidates(sys.argv[2], int(sys.argv[3]))
    elif sys.argv[1] == "bundle":
        bundle(sys.argv[2], sys.argv[3])
