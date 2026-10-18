"""Render the real imported meshes for the hub tile and geometry inspection (Pillow + numpy)."""
import json
import argparse
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "app/build/anatomy-source"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--layer', default='skeleton', help='Layer name, comma-separated layers, or all')
parser.add_argument('--region', choices=('all', 'skull', 'neck', 'spine', 'thorax', 'abdomen', 'pelvis'), default='all')
parser.add_argument('--head', action='store_true', help='Inspect skull, teeth and head muscles at close range')
parser.add_argument('--knee', choices=('left', 'right'), help='Inspect one knee at close range')
parser.add_argument('--plateau', action='store_true', help='With --knee, hide femur/patella and include a superior view')
args = parser.parse_args()
catalog = json.loads((ROOT / 'app/src/main/assets/science/biology/catalog.json').read_text(encoding='utf-8'))['structures']
head_ids = {r['id'] for r in catalog if r['region'] in ('skull', 'teeth')}
region_ids = {r['id'] for r in catalog if r['region'] == args.region}
layers = ({'skeleton', 'cartilage'} if args.layer == 'skeleton-cartilage' else
          {'skeleton', 'organs'} if args.layer == 'skeleton-organs' else set(args.layer.split(',')))
meshes = [m for m in json.loads((DATA / "preview_geometry.json").read_text())
          if (args.layer == 'all' or m.get('layer', 'skeleton') in layers)
          and (not args.head or m['id'] in head_ids)
          and (args.region == 'all' or m['id'] in region_ids)]
if args.knee:
    knee_ids = {r['id'] for r in catalog if r['sourceName'].startswith(args.knee + ' ')
                and (r['kind'].startswith('cartilage_meniscus_')
                     or r['sourceName'] in [args.knee + ' ' + bone for bone in
                                            (('tibia',) if args.plateau else ('femur', 'tibia', 'patella'))])}
    meshes = [m for m in meshes if m['id'] in knee_ids]
    for m in meshes:
        t = np.asarray(m['v'])[np.asarray(m['f'])]
        m['f'] = np.asarray(m['f'])[(t[:, :, 1].min(1) > .30) & (t[:, :, 1].max(1) < .44)].tolist()
triangles = np.concatenate([np.asarray(m["v"], dtype=np.float32)[np.asarray(m["f"])] for m in meshes])
base_colors = np.concatenate([np.tile(np.array(m['color']) * 255,
                                     (len(m['f']), 1)) for m in meshes])
alphas = np.concatenate([np.full(len(m['f']), round(m.get('opacity', 1) * 255)) for m in meshes])


def render(size, yaw, center, scale, transparent=False, pitch=0.):
    c, s = np.cos(yaw), np.sin(yaw)
    rotation = np.array([[c, 0, -s], [0, 1, 0], [s, 0, c]])
    cp, sp = np.cos(pitch), np.sin(pitch)
    rotation = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ rotation
    t = (triangles - np.asarray(center)) @ rotation.T
    normals = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0])
    normals /= np.maximum(np.linalg.norm(normals, axis=1, keepdims=True), 1e-15)
    # Two-sided surface shading, like the glTF material.
    normals *= np.where(normals[:, 2:] < 0, -1, 1)
    light = np.array([-.4, .6, 1.0]); light /= np.linalg.norm(light)
    brightness = .30 + .70 * np.maximum(0, normals @ light)
    rgb = np.clip(brightness[:, None] * base_colors, 0, 255).astype(np.uint8)
    x = t[:, :, 0] * scale + size[0] / 2
    y = -t[:, :, 1] * scale + size[1] / 2
    visible = (x.max(axis=1) >= 0) & (x.min(axis=1) < size[0]) & (y.max(axis=1) >= 0) & (y.min(axis=1) < size[1])
    ordered = np.argsort(t[:, :, 2].mean(axis=1))
    image = Image.new("RGBA" if transparent else "RGB", size, (0, 0, 0, 0) if transparent else (8, 20, 29))
    draw = ImageDraw.Draw(image, 'RGBA')
    for i in ordered:
        if visible[i]:
            draw.polygon(list(zip(x[i].tolist(), y[i].tolist())), fill=tuple(rgb[i]) + (int(alphas[i]),))
    return image


middle_y = float((triangles[:, :, 1].min() + triangles[:, :, 1].max()) / 2)
close_view = args.head or args.knee or args.region != 'all'
middle_z = float((triangles[:, :, 2].min() + triangles[:, :, 2].max()) / 2) if close_view else .07
view_scale = 920 / float(triangles[:, :, 1].max() - triangles[:, :, 1].min())
if close_view:
    # The profile can be wider than the frontal view; fit all three views.
    span = np.ptp(triangles.reshape(-1, 3), axis=0)
    view_scale = min(view_scale, 600 / max(float(span[0]), float(span[2])))
middle_x = (.075 if args.knee == 'left' else -.075) if args.knee else 0
center = (middle_x, middle_y, middle_z)
front = render((640, 1000), 0, center, view_scale)
side = render((640, 1000), np.pi / 2, center, view_scale)
back = render((640, 1000), 0 if args.plateau else np.pi, center, view_scale,
              pitch=np.pi / 2 if args.plateau else 0.)
overview = Image.new("RGB", (1920, 1000), (8, 20, 29))
for i, image in enumerate((front, side, back)):
    overview.paste(image, (i * 640, 0))
prefix = (args.knee + '_knee_' + ('plateau_' if args.plateau else '') if args.knee else
          'head_' if args.head else args.region + '_' if args.region != 'all' else '')
overview.save(DATA / (prefix + args.layer + '_views.png'))
if args.layer == 'skeleton' and not close_view:
    tile = render((1024, 1024), -.20, (0, 1.27, .08), 1130, True)
    tile.resize((512, 512), Image.Resampling.LANCZOS).save(ROOT / "app/src/main/assets/science/biology/skeleton_tile.png")
print('Rendered imported geometry:', args.layer)
