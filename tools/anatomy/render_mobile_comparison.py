"""Render original/staged mobile geometry side by side, with identical camera and materials.

Outputs are inspection images in app/build/anatomy-source/mobile, never app artwork.
Run after optimize_mobile.mjs. Requires numpy and Pillow.
"""
import json
import numpy as np
from PIL import Image, ImageDraw
from validate_atlas import read_glb
from validate_mobile import WORK


def scene(directory, select):
    catalog = json.loads((directory / 'catalog.json').read_text(encoding='utf8'))
    records = {s['id']: s for s in catalog['structures'] if select(s)}
    gltf, accessor = read_glb(directory / 'atlas.glb')
    triangles, colors = [], []
    for node in gltf['nodes']:
        if node['name'] not in records:
            continue
        primitive, = gltf['meshes'][node['mesh']]['primitives']
        v, f = accessor(primitive['attributes']['POSITION']), accessor(primitive['indices']).reshape(-1, 3)
        color = gltf['materials'][primitive['material']]['pbrMetallicRoughness']['baseColorFactor']
        triangles.append(v[f]); colors.append(np.tile(np.array(color[:3]) * 255, (len(f), 1)))
    return np.concatenate(triangles), np.concatenate(colors)


def render(triangles, colors, rotation, centre, scale, label):
    t = (triangles - centre) @ rotation.T
    normals = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0])
    normals /= np.maximum(np.linalg.norm(normals, axis=1, keepdims=True), 1e-15)
    normals *= np.where(normals[:, 2:] < 0, -1, 1)
    light = np.array([-.4, .6, 1.]); light /= np.linalg.norm(light)
    rgb = np.clip((.3 + .7 * np.maximum(0, normals @ light))[:, None] * colors, 0, 255).astype(np.uint8)
    x, y = t[:, :, 0] * scale + 350, -t[:, :, 1] * scale + 485
    image = Image.new('RGB', (700, 960), (8, 20, 29)); draw = ImageDraw.Draw(image)
    for i in np.argsort(t[:, :, 2].mean(1)):
        draw.polygon(list(zip(x[i].tolist(), y[i].tolist())), fill=tuple(rgb[i]))
    draw.text((18, 16), f'{label} / {len(t):,} triangles', fill=(230, 238, 245))
    return image


def compare(atlas, name, select, yaw):
    original = scene(WORK / atlas / 'original', select)
    optimized = scene(WORK / atlas / 'optimized', select)
    c, s = np.cos(yaw), np.sin(yaw)
    rotation = np.array([[c, 0, -s], [0, 1, 0], [s, 0, c]])
    points = original[0].reshape(-1, 3)
    centre = (points.min(0) + points.max(0)) / 2
    span = np.ptp((points - centre) @ rotation.T, axis=0)
    scale = min(620 / span[0], 850 / span[1])
    image = Image.new('RGB', (1400, 960))
    for i, (data, label) in enumerate(((original, 'Original'), (optimized, 'Mobile'))):
        image.paste(render(*data, rotation, centre, scale, label), (i * 700, 0))
    output = WORK / atlas / (name + '-comparison.png'); image.save(output); print(output, flush=True)


if __name__ == '__main__':
    compare('male', 'body', lambda r: r['layer'] in {'skeleton', 'muscles'}, -.18)
    compare('male', 'head', lambda r: r['region'] in {'skull', 'teeth'} and r['layer'] in {'skeleton', 'muscles', 'senses'}, -.45)
    compare('female', 'organs', lambda r: r['region'] in {'thorax', 'abdomen', 'pelvis'} and r['layer'] == 'organs', -.25)
    compare('ear', 'fine', lambda r: r['id'] != 'openear_zeta_temporal_context', .3)
