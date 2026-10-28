"""Cut the generated Lanky Paws (Grandes papattes) images into sprites, compress them and compute their anchors.

Usage: python -I tools/papattes/pack_art.py --source DIR [--threshold 128] [--preview FILE]
    DIR holds heads*.png, bodies*.png, legs*.png, props.png (transparent sheets), bg_<biome>.png
    and hub.png, as listed by tools/papattes/prompts.py --list.

ImageGen never respects a grid exactly, so each sheet is segmented instead: pixels whose alpha passes the threshold
are grouped into connected blobs (after a small dilation that joins the parts of one piece), every blob is
attributed to one zone of the sheet's grid by where most of its pixels are, and the blobs of a zone are cut out
together with their own alpha mask, so a neighbour spilling into the zone is not carried along.

Output, in app/src/main/assets/papattes/:
    head_<id>, body_<id>, thigh_<id>, shin_<id>, prop_<id>  (WebP with alpha, cropped to the outline)
    bg_<biome> (1280x853), hub (512x512)  (opaque WebP)
    sprites.json: {"<name>": {"w": W, "h": H, "anchors": {"<anchor>": [x, y], ...}}} in pixels of the final WebP.

Anchors are measured on the outline: a band is the top or bottom 6 percent of the piece's height, and its point is
the centroid of the opaque pixels inside it.
    head: neck (bottom band)            body: neck (top band), hip (bottom band)
    thigh: top, bottom                  shin: top, bottom (under the foot), ankle (shaft centre at the bottom band height)
    prop: base (bottom band)
Backdrops are made seamless by cross-fading their left and right edges.
"""
from argparse import ArgumentParser
from collections import deque
import json
import math
from pathlib import Path
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prompts import BODY2_IDS, BODY_IDS, HEAD2_IDS, HEAD3_IDS, HEAD_IDS, LEG2_IDS, LEG_IDS, PROP_IDS  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "app/src/main/assets/papattes"
SHRINK = 4           # the mask is labelled at a quarter of the resolution
DILATE = 3           # odd window, in shrunk pixels: joins the pieces of one object (5 merged close neighbours)
WHOLE = .7           # a blob with at least this share of its pixels in one zone belongs to it entirely
SPLIT = .1           # when a blob is split along zone boundaries, zones holding less than this share get nothing
SPECK = .0008        # blobs smaller than this fraction of the image are noise
STRAY = .06          # blobs smaller than this share of their zone's main blob are noise too
STRAY_FINE = .01     # same rule at full resolution inside a cut piece
BAND = .06           # anchor band: share of the piece's height
BUDGET = 2_500_000
BIOMES = ["meadow", "marsh", "ice"]

# sheet name -> (columns, rows, [(sprite name, kind, size rule)] in reading order)
SHEETS = {
    "heads": (3, 3, [(f"head_{i}", "head", ("max", 320)) for i in HEAD_IDS]),
    "bodies": (3, 3, [(f"body_{i}", "body", ("max", 320)) for i in BODY_IDS]),
    "legs": (6, 2, [(f"thigh_{i}", "thigh", ("height", 448)) for i in LEG_IDS]
             + [(f"shin_{i}", "shin", ("height", 448)) for i in LEG_IDS]),
    "heads2": (3, 3, [(f"head_{i}", "head", ("max", 320)) for i in HEAD2_IDS]),
    "heads3": (3, 3, [(f"head_{i}", "head", ("max", 320)) for i in HEAD3_IDS]),
    "bodies2": (3, 3, [(f"body_{i}", "body", ("max", 320)) for i in BODY2_IDS]),
    "legs2": (4, 2, [(f"thigh_{i}", "thigh", ("height", 448)) for i in LEG2_IDS]
              + [(f"shin_{i}", "shin", ("height", 448)) for i in LEG2_IDS]),
    "props": (4, 3, [(f"prop_{i}", "prop", ("max", 256)) for i in PROP_IDS]),
}


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


def segment(image, threshold, columns, rows):
    """Returns one (bbox, mask) per zone of the grid, or None for an empty zone; bbox is in full resolution."""
    alpha = image.getchannel("A").point(lambda a: 255 if a >= threshold else 0)
    small = alpha.resize((image.width // SHRINK, image.height // SHRINK), Image.Resampling.BOX).filter(ImageFilter.MaxFilter(DILATE))
    w, h = small.size
    mask = [1 if v else 0 for v in small.tobytes()]
    labels, count = label(mask, w, h)
    zone_count = columns * rows

    def zone_of(i):
        x, y = i % w, i // w
        return min(y * rows // h, rows - 1) * columns + min(x * columns // w, columns - 1)

    def centre(z):
        return (z % columns + .5, z // columns + .5)

    shares = [[0] * zone_count for _ in range(count + 1)]
    for i, l in enumerate(labels):
        if l:
            shares[l][zone_of(i)] += 1
    home = [0] * (count + 1)
    whole = [True] * (count + 1)
    for l in range(1, count + 1):
        total = sum(shares[l])
        if total < SPECK * w * h:
            home[l] = -1
            continue
        home[l] = max(range(zone_count), key=lambda z: shares[l][z])
        whole[l] = shares[l][home[l]] >= WHOLE * total
    # Stray brush specks: a blob far smaller than the main blob of its zone is dropped.
    biggest = [0] * zone_count
    for l in range(1, count + 1):
        if home[l] >= 0:
            biggest[home[l]] = max(biggest[home[l]], sum(shares[l]))
    for l in range(1, count + 1):
        if home[l] >= 0 and whole[l] and sum(shares[l]) < STRAY * biggest[home[l]]:
            home[l] = -1
    owner = [-1] * (w * h)
    for i, l in enumerate(labels):
        if l and home[l] >= 0:
            z = zone_of(i)
            if whole[l]:
                owner[i] = home[l]
            elif shares[l][z] >= SPLIT * sum(shares[l]):
                owner[i] = z
            else:
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
        result.append([[min(xs) * SHRINK, min(ys) * SHRINK, (max(xs) + 1) * SHRINK, (max(ys) + 1) * SHRINK], full])
    # Two pieces drawn a few pixels apart are joined by the coarse dilation, and a blob straddling zones is split
    # straight along the zone line. Inside such a blob, the full resolution parts that lie mostly in one zone are
    # given whole to that zone instead (a strawberry's leaves stay on the strawberry, not under the pot above it).
    W, H = image.size
    for l in range(1, count + 1):
        if home[l] < 0 or whole[l]:
            continue
        cells = [i for i, m in enumerate(labels) if m == l]
        box = (min(i % w for i in cells) * SHRINK, min(i // w for i in cells) * SHRINK,
               (max(i % w for i in cells) + 1) * SHRINK, (max(i // w for i in cells) + 1) * SHRINK)
        bw, bh = box[2] - box[0], box[3] - box[1]
        parts, n = label([1 if v else 0 for v in alpha.crop(box).tobytes()], bw, bh)
        part_shares = [[0] * zone_count for _ in range(n + 1)]
        for j, c in enumerate(parts):
            if c:
                x, y = box[0] + j % bw, box[1] + j // bw
                part_shares[c][min(y * rows // H, rows - 1) * columns + min(x * columns // W, columns - 1)] += 1
        largest = max((sum(ps) for ps in part_shares[1:]), default=0)
        for c in range(1, n + 1):
            total = sum(part_shares[c])
            z = max(range(zone_count), key=lambda q: part_shares[c][q])
            if total < STRAY_FINE * largest or part_shares[c][z] < WHOLE * total:
                continue
            js = [j for j, v in enumerate(parts) if v == c]
            stencil = Image.frombytes("L", (bw, bh), bytes(255 if v == c else 0 for v in parts))
            for q in range(zone_count):
                if result[q] is not None:
                    result[q][1].paste(255 if q == z else 0, box, stencil)
            px0, py0 = box[0] + min(j % bw for j in js), box[1] + min(j // bw for j in js)
            px1, py1 = box[0] + max(j % bw for j in js) + 1, box[1] + max(j // bw for j in js) + 1
            if result[z] is None:
                result[z] = [[px0, py0, px1, py1], Image.new("L", image.size, 0)]
                result[z][1].paste(255, box, stencil)
            else:
                b = result[z][0]
                result[z][0] = [min(b[0], px0), min(b[1], py0), max(b[2], px1), max(b[3], py1)]
    return [(tuple(r[0]), r[1]) if r else None for r in result]


def cut(image, zone, threshold):
    bbox, mask = zone
    piece = image.crop(bbox)
    alpha = piece.getchannel("A").point(lambda a: 0 if a < threshold else a)
    alpha = Image.composite(alpha, Image.new("L", piece.size, 0), mask.crop(bbox))
    # Specks a few pixels away from the piece survive the coarse mask: label the piece itself (joining only
    # touching strokes) and keep the parts that weigh at least STRAY_FINE of the main one.
    joined = alpha.point(lambda a: 255 if a >= threshold else 0).filter(ImageFilter.MaxFilter(3))
    labels, count = label([1 if v else 0 for v in joined.tobytes()], piece.width, piece.height)
    if count > 1:
        sizes = [0] * (count + 1)
        for l in labels:
            sizes[l] += 1
        largest = max(sizes[1:])
        keep = bytes(255 if l and sizes[l] >= STRAY_FINE * largest else 0 for l in labels)
        alpha = Image.composite(alpha, Image.new("L", piece.size, 0), Image.frombytes("L", piece.size, keep))
    piece.putalpha(alpha)
    bounds = alpha.getbbox()
    return piece.crop(bounds) if bounds else None


def resize(piece, rule):
    how, size = rule
    scale = size / (piece.height if how == "height" else max(piece.size))
    return piece.resize((max(1, round(piece.width * scale)), max(1, round(piece.height * scale))), Image.Resampling.LANCZOS)


def centroid(alpha, y0, y1, threshold=128):
    """Centroid of the opaque pixels in rows [y0, y1); None if the band is empty."""
    w = alpha.width
    data = alpha.crop((0, y0, w, y1)).tobytes()
    sx = sy = n = 0
    for i, a in enumerate(data):
        if a >= threshold:
            sx += i % w
            sy += i // w
            n += 1
    return (sx / n, y0 + sy / n) if n else None


def edge_under_centre(alpha, bottom, threshold=128):
    """Point on the top or bottom outline, straight above or below the piece's centre of mass, moved half a band
    inside. Heads and bodies use it rather than a band centroid: a bee's wing or a pumpkin's stem sticks out of
    the top, an axolotl's gills hang low on one side, and the joint must stay under the middle of the mass."""
    w, h = alpha.size
    data = alpha.tobytes()
    sx = n = 0
    for i, a in enumerate(data):
        if a >= threshold:
            sx += i % w
            n += 1
    cx = sx / n
    half = max(3, round(w * .04))
    x0, x1 = max(0, round(cx) - half), min(w, round(cx) + half + 1)
    rows = [y for y in range(h) if any(data[y * w + x] >= threshold for x in range(x0, x1))]
    inset = h * BAND / 2
    return (cx, rows[-1] - inset) if bottom else (cx, rows[0] + inset)


def anchors(piece, kind):
    alpha = piece.getchannel("A")
    h = piece.height
    band = max(2, round(h * BAND))
    top = centroid(alpha, 0, band)
    bottom = centroid(alpha, h - band, h)
    if kind == "head":
        found = {"neck": edge_under_centre(alpha, bottom=True)}
    elif kind == "body":
        found = {"neck": edge_under_centre(alpha, bottom=False), "hip": edge_under_centre(alpha, bottom=True)}
    elif kind == "thigh":
        found = {"top": top, "bottom": bottom}
    elif kind == "shin":
        # The shaft is followed from just under the top to the middle, and extended down to the sole's height.
        upper = centroid(alpha, round(h * .12), round(h * .22)) or top
        middle = centroid(alpha, round(h * .45), round(h * .55)) or upper
        slope = (middle[0] - upper[0]) / max(1e-6, middle[1] - upper[1])
        ankle_x = min(max(middle[0] + slope * (bottom[1] - middle[1]), 0), piece.width - 1)
        found = {"top": top, "bottom": bottom, "ankle": (ankle_x, bottom[1])}
    else:
        found = {"base": bottom}
    return {k: [round(v[0]), round(v[1])] for k, v in found.items()}


def save_webp(image, path, quality):
    if image.mode == "RGBA":
        image = image.copy()
        image.putalpha(image.getchannel("A").point(lambda a: round(a / 17) * 17))  # quantised alpha compresses better
    image.save(path, "WEBP", quality=quality, method=6)


def crossfade_x(image, fraction):
    """Seamless horizontally: the last columns fade into the first ones, the result is narrower."""
    w, h = image.size
    k = round(w * fraction)
    result = image.crop((0, 0, w - k, h))
    ramp = Image.linear_gradient("L").rotate(90, expand=True).resize((k, h))
    # ramp goes 0 at x=0 to 255 at x=k: start from the image's tail, end on its head.
    head = image.crop((0, 0, k, h))
    tail = image.crop((w - k, 0, w, h))
    result.paste(Image.composite(head, tail, ramp), (0, 0))
    return result


def pivot_paste(canvas, sprite, anchor, target, angle):
    """Pastes the sprite rotated by angle (degrees, counter-clockwise) around its anchor, anchor landing on target."""
    ax, ay = anchor
    r = math.ceil(math.hypot(max(ax, sprite.width - ax), max(ay, sprite.height - ay))) + 2
    holder = Image.new("RGBA", (2 * r, 2 * r))
    holder.alpha_composite(sprite, (r - round(ax), r - round(ay)))
    holder = holder.rotate(angle, resample=Image.Resampling.BICUBIC, center=(r, r))
    canvas.alpha_composite(holder, (round(target[0]) - r, round(target[1]) - r))


def rotated(point, anchor, target, angle):
    """Where a point of the sprite ends up after pivot_paste."""
    dx, dy = point[0] - anchor[0], point[1] - anchor[1]
    a = math.radians(angle)
    return (target[0] + dx * math.cos(a) + dy * math.sin(a), target[1] - dx * math.sin(a) + dy * math.cos(a))


def dot(draw, p, colour, r=5):
    draw.ellipse((p[0] - r, p[1] - r, p[0] + r, p[1] + r), fill=colour, outline=(0, 0, 0, 255))


def preview(sprites, info, path):
    head, body, thigh, shin = (f"head_cat", "body_pompom", "thigh_stilts", "shin_stilts")
    scale = {head: .75, body: .75, thigh: .7, shin: .7}
    small = {}
    for name in scale:
        s = sprites[name]
        f = scale[name]
        small[name] = (s.resize((round(s.width * f), round(s.height * f)), Image.Resampling.LANCZOS),
                       {k: (v[0] * f, v[1] * f) for k, v in info[name]["anchors"].items()})
    assembly = Image.new("RGBA", (900, 1150), (236, 242, 228, 255))
    draw = ImageDraw.Draw(assembly)
    bsprite, banchor = small[body]
    body_neck = (450, 260)
    body_origin = (body_neck[0] - banchor["neck"][0], body_neck[1] - banchor["neck"][1])
    hip = (body_origin[0] + banchor["hip"][0], body_origin[1] + banchor["hip"][1])
    marks = []
    for side, (thigh_angle, shin_angle) in enumerate([(-25, 10), (20, -15)]):  # back leg first, then front leg
        ts, ta = small[thigh]
        pivot_paste(assembly, ts, ta["top"], hip, thigh_angle)
        knee = rotated(ta["bottom"], ta["top"], hip, thigh_angle)
        ss, sa = small[shin]
        pivot_paste(assembly, ss, sa["top"], knee, shin_angle)
        marks += [knee, rotated(sa["ankle"], sa["top"], knee, shin_angle), rotated(sa["bottom"], sa["top"], knee, shin_angle)]
        if side == 0:
            assembly.alpha_composite(bsprite, (round(body_origin[0]), round(body_origin[1])))
    hsprite, hanchor = small[head]
    overlap = .08 * hsprite.height
    assembly.alpha_composite(hsprite, (round(body_neck[0] - hanchor["neck"][0]), round(body_neck[1] - hanchor["neck"][1] + overlap)))
    for p in [body_neck, hip] + marks:
        dot(draw, p, (230, 30, 30, 255))
    # Contact sheet of every cut sprite, with its anchors.
    cell = 200
    names = list(sprites)
    cols = 8
    rows = -(-len(names) // cols)
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (250, 248, 240, 255))
    sd = ImageDraw.Draw(sheet)
    for n, name in enumerate(names):
        s = sprites[name]
        f = min((cell - 20) / s.width, (cell - 20) / s.height)
        t = s.resize((max(1, round(s.width * f)), max(1, round(s.height * f))), Image.Resampling.LANCZOS)
        ox, oy = (n % cols) * cell + (cell - t.width) // 2, (n // cols) * cell + (cell - t.height) // 2
        sd.rectangle(((n % cols) * cell, (n // cols) * cell, (n % cols + 1) * cell - 1, (n // cols + 1) * cell - 1), outline=(200, 200, 200, 255))
        sheet.alpha_composite(t, (ox, oy))
        for k, v in info[name]["anchors"].items():
            dot(sd, (ox + v[0] * f, oy + v[1] * f), (40, 120, 230, 255) if k == "ankle" else (230, 30, 30, 255), 3)
    out = Image.new("RGB", (max(assembly.width, sheet.width), assembly.height + sheet.height), (255, 255, 255))
    out.paste(assembly.convert("RGB"), (0, 0))
    out.paste(sheet.convert("RGB"), (0, assembly.height))
    out.save(path)


def main():
    parser = ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--threshold", type=int, default=128, help="alpha below this is background (ImageGen adds a faint haze)")
    parser.add_argument("--preview", type=Path, help="write the assembly preview and contact sheet there")
    args = parser.parse_args()
    DESTINATION.mkdir(parents=True, exist_ok=True)
    sprites, info = {}, {}
    for sheet_name, (columns, rows, entries) in SHEETS.items():
        path = args.source / f"{sheet_name}.png"
        if not path.exists():
            print(f"warning: {path.name} missing", file=sys.stderr)
            continue
        image = Image.open(path).convert("RGBA")
        corners = [image.getpixel(p)[3] for p in ((0, 0), (image.width - 1, 0), (0, image.height - 1), (image.width - 1, image.height - 1))]
        if min(corners) > 0:
            print(f"warning: {path.name} has an opaque background", file=sys.stderr)
        for (name, kind, rule), zone in zip(entries, segment(image, args.threshold, columns, rows)):
            piece = cut(image, zone, args.threshold) if zone else None
            if piece is None:
                print(f"warning: {sheet_name} zone of {name} is empty", file=sys.stderr)
                continue
            piece = resize(piece, rule)
            sprites[name] = piece
            info[name] = {"w": piece.width, "h": piece.height, "anchors": anchors(piece, kind)}
            save_webp(piece, DESTINATION / f"{name}.webp", 85)
    for biome in BIOMES:
        path = args.source / f"bg_{biome}.png"
        if path.exists():
            image = crossfade_x(Image.open(path).convert("RGB"), .10)
            keep = round(image.width * 853 / 1280)          # crop rows to the 1280x853 ratio, mostly from the bottom
            excess = image.height - keep
            image = image.crop((0, excess // 3, image.width, excess // 3 + keep)).resize((1280, 853), Image.Resampling.LANCZOS)
            save_webp(image, DESTINATION / f"bg_{biome}.webp", 80)
        else:
            print(f"warning: {path.name} missing", file=sys.stderr)
    path = args.source / "hub.png"
    if path.exists():
        image = Image.open(path).convert("RGB")
        side = min(image.size)
        image = image.crop(((image.width - side) // 2, (image.height - side) // 2, (image.width + side) // 2, (image.height + side) // 2))
        save_webp(image.resize((512, 512), Image.Resampling.LANCZOS), DESTINATION / "hub.webp", 82)
    (DESTINATION / "sprites.json").write_text(json.dumps(info, indent=1) + "\n", encoding="utf-8")
    total = sum(f.stat().st_size for f in DESTINATION.iterdir() if f.is_file())
    print(f"{len(info)} sprites, {sum(1 for _ in DESTINATION.iterdir())} files, total {total:,} bytes"
          + ("" if total < BUDGET else f"  OVER BUDGET ({BUDGET:,})"))
    if args.preview and sprites:
        needed = {"head_cat", "body_pompom", "thigh_stilts", "shin_stilts"}
        if needed <= sprites.keys():
            preview(sprites, info, args.preview)
            print(f"preview: {args.preview}")
        else:
            print(f"preview skipped, missing {sorted(needed - sprites.keys())}", file=sys.stderr)


if __name__ == "__main__":
    main()
