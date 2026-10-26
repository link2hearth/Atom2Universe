"""Slice the ImageGen source atlases and package only scaled WebP game assets.

Usage: python tools/mochi/prepare_art.py --source-directory <generated_images folder>
Original illustrations remain outside the application; no network or image generation here.
"""
from argparse import ArgumentParser
from pathlib import Path
from PIL import Image, ImageOps

parser = ArgumentParser()
parser.add_argument('--source-directory', required=True, type=Path)
args = parser.parse_args()
destination = Path(__file__).resolve().parents[2] / 'app/src/main/assets/mochi'
destination.mkdir(parents=True, exist_ok=True)

def source(name):
    return Image.open(args.source_directory / ('exec-' + name + '.png')).convert('RGBA')

def sprite(im, name, size=256):
    # Tighten the atlas cell, preserving the generated alpha and a small clear border.
    box = im.getchannel('A').point(lambda a: 255 if a > 16 else 0).getbbox()
    if box is None:
        raise ValueError('Empty sprite: ' + name)
    im = ImageOps.contain(im.crop(box), (size - 16, size - 16), Image.Resampling.LANCZOS)
    out = Image.new('RGBA', (size, size))
    out.alpha_composite(im, ((size - im.width) // 2, (size - im.height) // 2))
    out.save(destination / (name + '.webp'), quality=90, method=6)

for name, uid in [
    ('garden_dawn', '461aad3f-98e1-4b01-afa6-6b34150206e6'),
    ('garden_sunset', '189fd5ed-f9d7-4c1c-b401-7f29ddb6e522'),
    ('garden_moon', '6f941d16-5634-45d9-bd04-924b44890b16'),
]:
    source(uid).convert('RGB').resize((768, 1152), Image.Resampling.LANCZOS).save(
        destination / (name + '.webp'), quality=86, method=6)

sprite(source('1507fc55-7807-4bbc-8851-7359932213de'), 'mochi_hero', 512)
flowers = source('0838bb32-0509-49ed-8cdf-ae1dad7c08d2')
for i, name in enumerate(['flower_gold', 'flower_spring', 'flower_drift', 'flower_fragile', 'flower_rest', 'leaves']):
    x, y = i % 3 * 512, i // 3 * 512
    sprite(flowers.crop((x, y, x + 512, y + 512)), name, 384 if name == 'leaves' else 256)

props = source('131277b0-f4bb-4c4d-91fb-bfaf2ea623d3')
for i, name in enumerate(['bee', 'thorn', 'peach', 'starfruit']):
    x, y = i % 2 * 627, i // 2 * 627
    sprite(props.crop((x, y, x + 627, y + 627)), name)

expressions = source('4b710e86-99af-473f-9464-08993ffb7d96')
# The generated atlas gutter is slightly right of its mathematical midpoint.
for i, name in enumerate(['mochi_happy', 'mochi_blink', 'mochi_fly', 'mochi_hurt']):
    x0, x1 = (0, 680) if i % 2 == 0 else (680, 1254)
    y = i // 2 * 627
    sprite(expressions.crop((x0, y, x1, y + 627)), name)

total = 0
for file in sorted(destination.glob('*.webp')):
    with Image.open(file) as im:
        if im.mode == 'RGBA':
            assert im.getchannel('A').getextrema()[0] == 0, file.name
        print(f'{file.name}: {im.size}, {file.stat().st_size:,} bytes')
    total += file.stat().st_size
print(f'TOTAL: {total:,} bytes')
