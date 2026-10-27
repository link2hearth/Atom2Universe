"""Slice a generated Pocket Gobbler sprite sheet and pack it as the WebP the game loads.

Usage: python -I tools/glutton/pack_art.py SOURCE.png SHEET [--cell 256] [--threshold 40]
    SHEET is room_a, room_b, room_c, garden_a, garden_b, garden_c, city_a or city_b.
    The number of cells (and rows) comes from GluttonCatalog.kt, so the grid always matches the game.

The generated image is divided into equal cells (6 columns), each object is trimmed to its alpha
bounds and re-centred in a square cell with a clear border, then the atlas is saved under
app/src/main/assets/glutton/<sheet>.webp (target: under 400 KB). Original PNGs stay out of the repo.
"""
from argparse import ArgumentParser
from pathlib import Path
import re
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "app/src/main/java/com/Atom2Universe/app/games/glutton/GluttonCatalog.kt"
DESTINATION = ROOT / "app/src/main/assets/glutton"
COLUMNS = 6
BUDGET = 400_000


def cell_count(sheet):
    text = CATALOG.read_text(encoding="utf-8")
    current, count = None, None
    for line in text.splitlines():
        header = re.match(r"\s*sheet\(Sheet\.(\w+)\)", line)
        if header:
            current = header.group(1).lower()
            if current == sheet:
                count = 0
            continue
        if current == sheet and re.match(r'\s*p\("', line):
            count += 1
    if count is None:
        raise SystemExit(f"Unknown sheet {sheet}; see GluttonCatalog.kt")
    return count


def main():
    parser = ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("sheet")
    parser.add_argument("--cell", type=int, default=256, help="output cell size in pixels")
    parser.add_argument("--threshold", type=int, default=40, help="alpha below this counts as empty when trimming")
    args = parser.parse_args()
    count = cell_count(args.sheet)
    rows = -(-count // COLUMNS)
    source = Image.open(args.source).convert("RGBA")
    cell_w = source.width / COLUMNS
    cell_h = source.height / rows
    atlas = Image.new("RGBA", (args.cell * COLUMNS, args.cell * rows))
    inner = int(args.cell * .86)
    for index in range(count):
        col, row = index % COLUMNS, index // COLUMNS
        cell = source.crop((round(col * cell_w), round(row * cell_h), round((col + 1) * cell_w), round((row + 1) * cell_h)))
        bounds = cell.getchannel("A").point(lambda a: 255 if a > args.threshold else 0).getbbox()
        if bounds is None:
            print(f"warning: cell {index} is empty", file=sys.stderr)
            continue
        cell = cell.crop(bounds)
        cell.thumbnail((inner, inner), Image.Resampling.LANCZOS)
        atlas.alpha_composite(cell, (col * args.cell + (args.cell - cell.width) // 2, row * args.cell + (args.cell - cell.height) // 2))
    # Quantised alpha compresses far better and keeps soft edges.
    atlas.putalpha(atlas.getchannel("A").point(lambda a: round(a / 17) * 17))
    DESTINATION.mkdir(parents=True, exist_ok=True)
    out = DESTINATION / f"{args.sheet}.webp"
    for quality in (88, 82, 75, 65, 55, 45):
        atlas.save(out, "WEBP", quality=quality, method=6)
        if out.stat().st_size < BUDGET:
            print(f"{out}: {out.stat().st_size:,} bytes, {atlas.size}, quality={quality}, {count} cells")
            return
    raise SystemExit(f"{out} exceeds {BUDGET:,} bytes even at low quality; try --cell 192")


if __name__ == "__main__":
    main()
