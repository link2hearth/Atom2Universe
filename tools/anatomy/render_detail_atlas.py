"""Render packaged detail meshes for visual inspection; never replaces hub artwork."""
import argparse
import json
import numpy as np
from PIL import Image, ImageDraw
from validate_atlas import ROOT, ASSETS, read_glb

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('atlas', choices=('female', 'ear'))
parser.add_argument('--layers', help='Comma-separated layers; defaults to catalogue visibility')
parser.add_argument('--region', help='Only the selected catalogue region')
parser.add_argument('--context', action='store_true', help='Include items initially hidden')
parser.add_argument('--supplements', action='store_true', help='Only original educational reconstructions')
parser.add_argument('--inferior', action='store_true', help='Use an inferior view for the third panel')
args = parser.parse_args()
catalog = json.loads((ASSETS / args.atlas / 'catalog.json').read_text(encoding='utf8'))
layers = set(args.layers.split(',')) if args.layers else set(catalog['defaultLayers'])
by_id = {s['id']: s for s in catalog['structures'] if s['layer'] in layers
         and (not args.region or s['region'] == args.region)
         and (not args.supplements or s.get('reconstructed'))
         and (args.context or s['id'] not in catalog['hiddenByDefault'])}
gltf, accessor = read_glb(ASSETS / args.atlas / 'atlas.glb')
triangles, colors, alphas = [], [], []
for node in gltf['nodes']:
    if node['name'] not in by_id:
        continue
    s = by_id[node['name']]
    primitive = gltf['meshes'][node['mesh']]['primitives'][0]
    p = accessor(primitive['attributes']['POSITION'])
    t = p[accessor(primitive['indices']).reshape(-1, 3)]
    triangles.append(t)
    colors.append(np.tile(np.array(s['color']) * 255, (len(t), 1)))
    alphas.append(np.full(len(t), round(s.get('opacity', 1.) * 255)))
t = np.concatenate(triangles)
colors = np.concatenate(colors)
alphas = np.concatenate(alphas)
points = t.reshape(-1, 3)
center = (points.min(0) + points.max(0)) / 2
span = np.ptp(points, axis=0)
scale = min(910 / span[1], 570 / max(span[0], span[2]))
result = Image.new('RGB', (1800, 1000), (8, 20, 29))
for view, yaw in enumerate((0., np.pi / 2, np.pi)):
    c, s = np.cos(yaw), np.sin(yaw)
    rotation = np.array([[c, 0, -s], [0, 1, 0], [s, 0, c]])
    if view == 2 and args.inferior:
        rotation = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]])
    tt = (t - center) @ rotation.T
    n = np.cross(tt[:, 1] - tt[:, 0], tt[:, 2] - tt[:, 0])
    n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-15)
    n *= np.where(n[:, 2:] < 0, -1, 1)
    light = np.array([-.4, .6, 1.]); light /= np.linalg.norm(light)
    rgb = np.clip((.3 + .7 * np.maximum(0, n @ light))[:, None] * colors, 0, 255).astype(np.uint8)
    x = tt[:, :, 0] * scale + 300
    y = -tt[:, :, 1] * scale + 515
    frame = Image.new('RGB', (600, 1000), (8, 20, 29))
    draw = ImageDraw.Draw(frame, 'RGBA')
    for i in np.argsort(tt[:, :, 2].mean(axis=1)):
        draw.polygon(list(zip(x[i].tolist(), y[i].tolist())), fill=tuple(rgb[i]) + (int(alphas[i]),))
    viewpoint = 'inferior' if view == 2 and args.inferior else f'{round(np.degrees(yaw))} degrees'
    draw.text((16, 16), f'{args.atlas} / {viewpoint} / {len(by_id)} meshes', fill=(225, 235, 240, 255))
    result.paste(frame, (view * 600, 0))
output = ROOT / 'app/build/anatomy-source' / args.atlas
output.mkdir(exist_ok=True)
path = output / ('preview-' + ('supplements-' if args.supplements else '') +
    (args.region or args.layers or 'default').replace(',', '-') + ('-inferior' if args.inferior else '') + '.png')
result.save(path)
print(path)
