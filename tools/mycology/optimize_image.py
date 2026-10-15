"""Compress one reviewed mycology plate to a WebP no larger than 50,000 bytes."""

from __future__ import annotations

import argparse
import re
from io import BytesIO
from pathlib import Path

from PIL import Image


OUTPUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/science/mycology/herbarium"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("species_id")
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9_]+", args.species_id):
        raise ValueError("Invalid species ID")
    with Image.open(args.source) as original:
        plate = original.convert("RGB")
        plate.thumbnail((800, 800), Image.Resampling.LANCZOS)
        for quality in range(80, 35, -2):
            buffer = BytesIO()
            plate.save(buffer, "WEBP", quality=quality, method=6)
            data = buffer.getvalue()
            if len(data) <= 50_000:
                break
        else:
            raise ValueError("Plate exceeds 50,000 bytes; review before further resizing")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / f"{args.species_id}.webp"
    path.write_bytes(data)
    print(f"{path}: {plate.width}x{plate.height}, {len(data)} bytes, quality {quality}")


if __name__ == "__main__":
    main()
