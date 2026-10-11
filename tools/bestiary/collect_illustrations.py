"""Collect a completed generation wave and prepare its lightweight review plates."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("wave", type=Path)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    queue = read(args.wave / "queue.json")
    if len({row["id"] for row in queue}) != len(queue):
        raise ValueError("Duplicate queued artwork identity")
    records = []
    for planned in queue:
        record = read(args.wave / f"art_{planned['id']}.json")
        for key in ("id", "scientific", "group", "prompt", "attempts"):
            if record[key] != planned[key]:
                raise ValueError(f"Generation record disagrees with queue: {key}")
        if not Path(record["path"]).is_file():
            raise ValueError(f"Generated original is missing: {record['id']}")
        records.append(record)
    if args.manifest.exists():
        raise ValueError("Manifest already exists; do not overwrite review/publication state")
    args.manifest.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    plates = args.wave / "plates"
    subprocess.run([sys.executable, str(Path(__file__).with_name("optimize_images.py")),
                    str(args.manifest), "--output", str(plates)], check=True)
    columns = 4
    sheet = Image.new("RGB", (columns * 320, ((len(records) + columns - 1) // columns) * 270), "white")
    draw = ImageDraw.Draw(sheet)
    for index, record in enumerate(records):
        with Image.open(plates / f"{record['id']}.webp") as source:
            preview = source.convert("RGB")
        preview.thumbnail((320, 240))
        x, y = (index % columns) * 320, (index // columns) * 270
        sheet.paste(preview, (x + (320 - preview.width) // 2, y + (240 - preview.height) // 2))
        draw.text((x + 8, y + 245), record["id"], fill="black")
    sheet.save(args.wave / "contact.png")
    print(f"{len(records)} originals collected; contact sheet ready; publication still requires review")


if __name__ == "__main__":
    main()
