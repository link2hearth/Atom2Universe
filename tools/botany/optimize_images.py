"""Compress herbarium plates like the bestiary: 640px WebP, capped at 50 KB.

The manifest contains id/path pairs pointing to originals kept outside Android assets.
No creative edits: preserve the complete portrait composition.
"""
import argparse
import json
import re
from io import BytesIO
from pathlib import Path

from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/science/botany/herbarium"))
    args = parser.parse_args()
    records = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    args.output.mkdir(parents=True, exist_ok=True)
    total = 0
    for record in records:
        species_id = record["id"]
        if not re.fullmatch(r"[a-z0-9_]+", species_id):
            raise ValueError(f"Unsafe species ID: {species_id}")
        with Image.open(record["path"]) as original:
            plate = original.convert("RGB")
            plate.thumbnail((640, 640), Image.Resampling.LANCZOS)
            for quality in range(78, 49, -2):
                buffer = BytesIO()
                plate.save(buffer, "WEBP", quality=quality, method=6)
                payload = buffer.getvalue()
                if len(payload) <= 50_000:
                    break
            else:
                raise ValueError(f"Plate exceeds 50 KB at quality 50: {species_id}; review before reducing detail")
        (args.output / f"{species_id}.webp").write_bytes(payload)
        record.update(width=plate.width, height=plate.height, bytes=len(payload), quality=quality)
        total += len(payload)
    args.manifest.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{len(records)} plates, {total:,} bytes")


if __name__ == "__main__":
    main()
