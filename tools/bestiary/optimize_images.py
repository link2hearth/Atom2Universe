"""Create small offline WebP plates from an ImageGen output manifest (no creative edits)."""
import argparse
import json
import re
from pathlib import Path

from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/bestiary"))
    args = parser.parse_args()
    records = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    args.output.mkdir(parents=True, exist_ok=True)
    total = 0
    for record in records:
        animal_id = record["id"]
        if not re.fullmatch(r"[a-z_]+", animal_id):
            raise ValueError(f"Unsafe species ID: {animal_id}")
        with Image.open(record["path"]) as original:
            plate = original.convert("RGB")
            plate.thumbnail((640, 640), Image.Resampling.LANCZOS)
            destination = args.output / f"{animal_id}.webp"
            plate.save(destination, "WEBP", quality=78, method=6)
        size = destination.stat().st_size
        record.update(width=plate.width, height=plate.height, bytes=size)
        total += size
    args.manifest.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{len(records)} plates, {total:,} bytes")


if __name__ == "__main__":
    main()
