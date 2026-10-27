"""Ground textures for Pocket Gobbler: four ImageGen images of four textures each, cut and made seamless.

Usage:
    python tools/glutton/textures.py --list               # image names (ground_0 .. ground_3)
    python tools/glutton/textures.py ground_1             # the prompt of one image
    python -I tools/glutton/textures.py --pack DIR        # cut DIR/ground_N.png into assets/glutton/ground_<name>.webp

Each image is a 2 x 2 grid of square textures, in the order of NAMES. ImageGen rarely tiles perfectly, so
--pack crops each quadrant away from its borders, then blends it with a half-offset copy of itself across
a soft cross-shaped seam so the result repeats without visible joins. GluttonView names its grounds
after these files (GluttonWorld.Ground).
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "app/src/main/assets/glutton"
SIZE = 512

NAMES = [
    ["wood", "tiles", "carpet", "bath"],
    ["grass", "asphalt", "pavement", "cobbles"],
    ["wheat", "meadow", "forest", "water"],
    ["rock", "sand", "ocean", "space"],
]
DESCRIPTIONS = {
    "wood": "honey coloured wooden parquet floor of long planks",
    "tiles": "kitchen floor of large cream and terracotta square tiles",
    "carpet": "soft dusty blue carpet with a faint woven texture",
    "bath": "bathroom floor of small white and pale aqua square tiles",
    "grass": "fresh green lawn grass, short and even",
    "asphalt": "plain dark grey asphalt road surface with fine grain, no markings",
    "pavement": "light grey square pavement slabs",
    "cobbles": "warm beige cobblestone square paving",
    "wheat": "golden wheat field seen from above, parallel rows",
    "meadow": "green meadow with tiny wild flowers seen from above",
    "forest": "dense forest canopy seen from far above, many round dark green treetops",
    "water": "calm blue lake water with soft ripples",
    "rock": "grey alpine rock and scree with patches of snow, seen from far above",
    "sand": "golden desert sand with gentle wind ripples",
    "ocean": "deep blue ocean seen from orbit with faint swells",
    "space": "deep night blue sky of space with tiny scattered white and yellow pencil dot stars",
}
STYLE = ("Style: hand-drawn COLOURED PENCIL texture on matte paper, visible soft pencil strokes and light hatching, "
         "gentle colours, like the background of a children's picture book. Strictly top-down orthographic view, flat even "
         "lighting, no perspective, no horizon, no objects, no shadows, no text, no frame.")


def prompt(index):
    names = NAMES[index]
    lines = [f"Use case: game background textures (Pocket Gobbler, image ground_{index}).",
             "Layout: ONE square image divided into four equal square quadrants (2 columns by 2 rows), each quadrant "
             "filled completely edge to edge with a different seamless tileable surface texture; the quadrants touch "
             "directly with no border, gap or line between them.", STYLE,
             "Quadrants: top left, top right, bottom left, bottom right:"]
    for n, name in enumerate(names):
        lines.append(f"{n + 1}. {DESCRIPTIONS[name]}.")
    return "\n".join(lines)


def seamless(tile):
    """Blends the tile with itself shifted by half its size across a soft cross, so its edges wrap."""
    from PIL import Image, ImageDraw, ImageFilter
    w, h = tile.size
    shifted = Image.new(tile.mode, (w, h))
    shifted.paste(tile.crop((w // 2, h // 2, w, h)), (0, 0))
    shifted.paste(tile.crop((0, h // 2, w // 2, h)), (w - w // 2, 0))
    shifted.paste(tile.crop((w // 2, 0, w, h // 2)), (0, h - h // 2))
    shifted.paste(tile.crop((0, 0, w // 2, h // 2)), (w - w // 2, h - h // 2))
    # The shifted copy has its seams on the centre cross: show the original there, the copy near the edges.
    mask = Image.new("L", (w, h), 255)
    draw = ImageDraw.Draw(mask)
    band = w // 5
    draw.rectangle((w // 2 - band, 0, w // 2 + band, h), fill=0)
    draw.rectangle((0, h // 2 - band, w, h // 2 + band), fill=0)
    mask = mask.filter(ImageFilter.GaussianBlur(band * .45))
    # Near the outer edges the copy (whose own content is continuous there) must win completely.
    return Image.composite(tile, shifted, mask.point(lambda v: 255 - v))


# Regular patterns (planks, tiles, slabs) would show ghost joints if blended: they are mirrored instead.
REGULAR = {"wood", "tiles", "bath", "pavement", "cobbles"}


def mirrored(tile):
    """Halves the tile and mirrors it into a 2 x 2 arrangement, which always wraps without a seam."""
    from PIL import Image, ImageOps
    w, h = tile.size
    quarter = tile.resize((w // 2, h // 2), Image.Resampling.LANCZOS)
    out = Image.new(tile.mode, (w, h))
    out.paste(quarter, (0, 0)); out.paste(ImageOps.mirror(quarter), (w // 2, 0))
    out.paste(ImageOps.flip(quarter), (0, h // 2)); out.paste(ImageOps.flip(ImageOps.mirror(quarter)), (w // 2, h // 2))
    return out


def pack(source):
    from PIL import Image
    DESTINATION.mkdir(parents=True, exist_ok=True)
    for index, names in enumerate(NAMES):
        path = source / f"ground_{index}.png"
        if not path.exists():
            print(f"warning: {path.name} missing", file=sys.stderr)
            continue
        image = Image.open(path).convert("RGB")
        half_w, half_h = image.width // 2, image.height // 2
        for n, name in enumerate(names):
            x, y = (n % 2) * half_w, (n // 2) * half_h
            inset_x, inset_y = half_w // 12, half_h // 12
            tile = image.crop((x + inset_x, y + inset_y, x + half_w - inset_x, y + half_h - inset_y)).resize((SIZE, SIZE), Image.Resampling.LANCZOS)
            tile = mirrored(tile) if name in REGULAR else seamless(tile)
            out = DESTINATION / f"ground_{name}.webp"
            tile.save(out, "WEBP", quality=80, method=6)
            tile.save(source / f"ground_{name}.png")
            print(f"{out.name}: {out.stat().st_size:,} bytes")


def main():
    args = sys.argv[1:]
    if "--list" in args:
        print("\n".join(f"ground_{i}" for i in range(len(NAMES))))
    elif "--pack" in args:
        pack(Path(args[args.index("--pack") + 1]))
    else:
        for a in args:
            print(prompt(int(a.rsplit("_", 1)[1])))


if __name__ == "__main__":
    main()
