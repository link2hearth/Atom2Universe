"""Cut the generated Pocket Gobbler images into objects and pack them as the WebP pages the game loads.

Usage: python -I tools/glutton/pack_art.py THEME --source DIR [--cell 192] [--threshold 128] [--preview]
    THEME is room, suburb or city. DIR holds the generated images <theme>_NN.png (six objects each,
    3 columns by 2 rows, transparent background), as listed by tools/glutton/prompts.py --list.

ImageGen never respects a grid exactly, so each image is segmented instead: pixels whose alpha passes
the threshold are grouped into connected blobs (after a small dilation that joins the parts of one
object), every blob is attributed to one of the six zones by its centre, and the blobs of a zone are
cut out together with their own alpha mask, so a neighbour spilling into the zone is not carried along.
Objects are then re-centred in square cells, 24 per page, and saved as
app/src/main/assets/glutton/<theme>_<page>.webp (target: under 400 KB each). Sources stay out of the repo.
"""
from argparse import ArgumentParser
from collections import deque
from pathlib import Path
import sys

from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prompts import images, themes  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "app/src/main/assets/glutton"
COLUMNS, ROWS = 6, 4
PAGE = COLUMNS * ROWS
ZONE_COLUMNS, ZONE_ROWS = 3, 2
SHRINK = 4           # the mask is labelled at a quarter of the resolution
DILATE = 5           # odd window, in shrunk pixels: joins the pieces of one object
WHOLE = .7           # a blob with at least this share of its pixels in one zone belongs to it entirely
SPLIT = .1           # when a blob is split along zone boundaries, zones holding less than this share get nothing
SPECK = .0015        # blobs smaller than this fraction of the image are noise
BUDGET = 400_000


def label(mask, width, height):
    """4-connected components of a 0/1 list; returns (labels list, count)."""
    labels = [0] * (width * height)
    count = 0
    for start in range(width * height):
        if not mask[start] or labels[start]:
            continue
        count += 1
        labels[start] = count
        queue = deque([start])
        while queue:
            i = queue.popleft()
            x, y = i % width, i // width
            for j in ((i - 1) if x else -1, (i + 1) if x < width - 1 else -1, (i - width) if y else -1, (i + width) if y < height - 1 else -1):
                if j >= 0 and mask[j] and not labels[j]:
                    labels[j] = count
                    queue.append(j)
    return labels, count


def segment(image, threshold):
    """Returns one (bbox, mask) per zone of the image, or None for an empty zone; bbox is in full resolution.

    A blob normally goes whole to the zone holding most of its pixels, so an object may spill a little over
    its zone. A blob spread over several zones (two objects touching, a tall one leaning into its neighbour)
    is instead split along the zone boundaries.
    """
    alpha = image.getchannel("A").point(lambda a: 255 if a >= threshold else 0)
    small = alpha.resize((image.width // SHRINK, image.height // SHRINK), Image.Resampling.BOX).filter(ImageFilter.MaxFilter(DILATE))
    w, h = small.size
    mask = [1 if v else 0 for v in small.getdata()]
    labels, count = label(mask, w, h)
    zone_count = ZONE_COLUMNS * ZONE_ROWS

    def zone_of(i):
        x, y = i % w, i // w
        return min(y * ZONE_ROWS // h, ZONE_ROWS - 1) * ZONE_COLUMNS + min(x * ZONE_COLUMNS // w, ZONE_COLUMNS - 1)

    shares = [[0] * zone_count for _ in range(count + 1)]
    for i, l in enumerate(labels):
        if l:
            shares[l][zone_of(i)] += 1
    home = [0] * (count + 1); whole = [True] * (count + 1)
    for l in range(1, count + 1):
        total = sum(shares[l])
        if total < SPECK * w * h:
            home[l] = -1
            continue
        home[l] = max(range(zone_count), key=lambda z: shares[l][z])
        whole[l] = shares[l][home[l]] >= WHOLE * total
    def centre(z):
        return (z % ZONE_COLUMNS + .5, z // ZONE_COLUMNS + .5)

    owner = [-1] * (w * h)
    for i, l in enumerate(labels):
        if l and home[l] >= 0:
            z = zone_of(i)
            if whole[l]:
                owner[i] = home[l]
            elif shares[l][z] >= SPLIT * sum(shares[l]):
                owner[i] = z
            else:
                # A sliver in a zone the blob barely touches joins the nearest zone it really occupies.
                zx, zy = centre(z)
                owner[i] = min((q for q in range(zone_count) if shares[l][q] >= SPLIT * sum(shares[l])),
                               key=lambda q: (centre(q)[0] - zx) ** 2 + (centre(q)[1] - zy) ** 2)
    result = []
    for zone in range(zone_count):
        xs = [i % w for i, o in enumerate(owner) if o == zone]
        if not xs:
            result.append(None)
            continue
        ys = [i // w for i, o in enumerate(owner) if o == zone]
        zone_mask = Image.new("L", (w, h), 0)
        zone_mask.putdata([255 if o == zone else 0 for o in owner])
        full = zone_mask.resize(image.size, Image.Resampling.NEAREST)
        result.append(((min(xs) * SHRINK, min(ys) * SHRINK, (max(xs) + 1) * SHRINK, (max(ys) + 1) * SHRINK), full))
    return result


def cut(image, zone, threshold):
    bbox, mask = zone
    piece = image.crop(bbox)
    alpha = piece.getchannel("A").point(lambda a: 0 if a < threshold else a)
    alpha = Image.composite(alpha, Image.new("L", piece.size, 0), mask.crop(bbox))
    piece.putalpha(alpha)
    bounds = alpha.getbbox()
    return piece.crop(bounds) if bounds else None


def main():
    parser = ArgumentParser()
    parser.add_argument("theme")
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--cell", type=int, default=192, help="output cell size in pixels")
    parser.add_argument("--threshold", type=int, default=128, help="alpha below this is background (ImageGen adds a faint haze)")
    parser.add_argument("--preview", action="store_true", help="also write <theme>_<page>.png next to the sources")
    args = parser.parse_args()
    ids = themes().get(args.theme) or sys.exit(f"Unknown theme {args.theme}; see GluttonCatalog.kt")
    inner = int(args.cell * .86)
    objects = {}
    # Each image holds its own subjects (see prompts.ADDED); every object goes to its slot in the catalogue.
    for name, subjects in images().items():
        if name.rsplit("_", 1)[0] != args.theme:
            continue
        path = args.source / f"{name}.png"
        if not path.exists():
            print(f"warning: {path.name} missing", file=sys.stderr)
            continue
        image = Image.open(path).convert("RGBA")
        zones = segment(image, args.threshold)
        for zone, subject in zip(zones, subjects):
            piece = cut(image, zone, args.threshold) if zone else None
            if piece is None:
                print(f"warning: {path.name} has no {subject}", file=sys.stderr)
            else:
                objects[ids.index(subject)] = piece
    DESTINATION.mkdir(parents=True, exist_ok=True)
    for page in range(-(-len(ids) // PAGE)):
        atlas = Image.new("RGBA", (args.cell * COLUMNS, args.cell * ROWS))
        for cell in range(PAGE):
            piece = objects.get(page * PAGE + cell)
            if piece is None:
                continue
            piece = piece.copy()
            piece.thumbnail((inner, inner), Image.Resampling.LANCZOS)
            col, row = cell % COLUMNS, cell // COLUMNS
            atlas.alpha_composite(piece, (col * args.cell + (args.cell - piece.width) // 2, row * args.cell + (args.cell - piece.height) // 2))
        if args.preview:
            atlas.save(args.source / f"{args.theme}_{page}.png")
        atlas.putalpha(atlas.getchannel("A").point(lambda a: round(a / 17) * 17))  # quantised alpha compresses far better
        out = DESTINATION / f"{args.theme}_{page}.webp"
        for quality in (88, 82, 75, 65, 55, 45):
            atlas.save(out, "WEBP", quality=quality, method=6)
            if out.stat().st_size < BUDGET:
                break
        print(f"{out.name}: {out.stat().st_size:,} bytes, quality={quality}, {sum(1 for c in range(PAGE) if page * PAGE + c in objects)} objects")


if __name__ == "__main__":
    main()
