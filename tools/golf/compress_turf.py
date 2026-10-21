"""Compress ImageGen grayscale turf sources: python tools/golf/compress_turf.py SOURCE_DIR.

Source PNGs stay outside the APK. Only the four 512px WebP files ship.
"""
import sys
from pathlib import Path
from PIL import Image, ImageStat

SOURCES = {
    "green": "exec-ba5668ad-f958-4e15-bb8f-a96279b1b8be.png",
    "fairway": "exec-97133e32-1437-4355-81c1-c99c0e3d8ee0.png",
    "rough": "exec-dd789db6-f374-49a0-9dfa-25acaeeba2f3.png",
    "fringe": "exec-56b31dfc-b3e6-48fe-bac2-841476d569b5.png",
}
out = Path(__file__).resolve().parents[2] / "app/src/main/assets/golf/textures"
out.mkdir(parents=True, exist_ok=True)
for name, filename in SOURCES.items():
    with Image.open(Path(sys.argv[1]) / filename) as source:
        gray = source.convert("L").resize((512, 512), Image.Resampling.LANCZOS)
    # Equal neutral brightness keeps the seasonal palette independent of the material.
    mean = ImageStat.Stat(gray).mean[0]
    gray = gray.point(lambda value: max(0, min(255, round(200 + (value - mean) * .85))))
    # Blend the outer 12 pixels towards matching opposite edges for repeat sampling.
    pixels = gray.load()
    for y in range(512):
        for i in range(12):
            a, b = pixels[i, y], pixels[511-i, y]
            weight = .5 * (1 - i / 12)
            pixels[i, y] = round(a * (1-weight) + b * weight)
            pixels[511-i, y] = round(b * (1-weight) + a * weight)
    for x in range(512):
        for i in range(12):
            a, b = pixels[x, i], pixels[x, 511-i]
            weight = .5 * (1 - i / 12)
            pixels[x, i] = round(a * (1-weight) + b * weight)
            pixels[x, 511-i] = round(b * (1-weight) + a * weight)
    target = out / f"{name}.webp"
    gray.save(target, "WEBP", quality=78, method=6)
    print(f"{name}: {target.stat().st_size:,} bytes")
