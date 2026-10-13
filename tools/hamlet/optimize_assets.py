"""Découpe et compresse les décors ImageGen, sans retouche artistique.
Usage: python tools/hamlet/optimize_assets.py --source-dir PATH
Les acteurs et bâtiments sont désormais dessinés en Kotlin. Seules les régions sont
exportées par défaut. --kinds permet de conserver les anciens atlas comme références
hors de l'APK. Alpha préservé, WebP lossy, métadonnées retirées.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageOps

ATLAS = {
    "hero": ("exec-dfdb872b-0d3e-415b-88a0-841cd240581a.png", 4, 3, 224, 80),
    "enemy": ("exec-2247fb42-f2da-4778-beae-c636e7b8e14d.png", 4, 2, 192, 78),
    "region": ("exec-d8ac32a3-a5cc-44e1-b7d4-ac41e32dfb51.png", 3, 2, 512, 73),
    "building": ("exec-6bdf9c0e-c751-4a6d-960b-637b21d35151.png", 3, 2, 256, 82),
}

def optimize(source_dir: Path, destination: Path, report: Path, kinds=("region",)):
    destination.mkdir(parents=True, exist_ok=True)
    originals = 0
    output = []
    for kind, (name, cols, rows, edge, quality) in ATLAS.items():
        if kind not in kinds:
            continue
        source = source_dir / name
        originals += source.stat().st_size
        with Image.open(source) as atlas:
            for i in range(cols * rows):
                x, y = i % cols, i // cols
                cell = atlas.crop((round(x * atlas.width / cols), round(y * atlas.height / rows),
                                   round((x + 1) * atlas.width / cols), round((y + 1) * atlas.height / rows)))
                if kind == "region":
                    image = ImageOps.fit(cell.convert("RGB"), (edge, edge * 3 // 4), Image.Resampling.LANCZOS, centering=(0.5, 0.6))
                else:
                    if cell.mode != "RGBA":
                        raise ValueError(f"Alpha manquant pour {kind}")
                    bbox = cell.getchannel("A").point(lambda a: 255 if a > 16 else 0).getbbox()
                    if bbox is None:
                        raise ValueError(f"Cellule vide: {kind}/{i}")
                    cell = cell.crop(bbox)
                    cell.thumbnail((edge - 12, edge - 12), Image.Resampling.LANCZOS)
                    image = Image.new("RGBA", (edge, edge))
                    image.alpha_composite(cell, ((edge - cell.width) // 2, edge - cell.height - 6))
                target = destination / f"{kind}_{i}.webp"
                image.save(target, "WEBP", quality=quality, method=6, exact=True)
                output.append({"file": target.name, "width": image.width, "height": image.height,
                               "bytes": target.stat().st_size, "alpha": image.mode == "RGBA"})
    total = sum(x["bytes"] for x in output)
    result = {"source_bytes": originals, "asset_bytes": total, "reduction_percent": round(100 * (1 - total / originals), 2), "files": output}
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(f"{len(output)} assets: {originals:,} -> {total:,} bytes ({result['reduction_percent']}% reduction)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--destination", type=Path, default=Path("app/src/main/assets/hamlet/adventure"))
    parser.add_argument("--report", type=Path, default=Path("app/build/reports/hamlet-adventure/assets.json"))
    parser.add_argument("--kinds", nargs="+", choices=tuple(ATLAS), default=["region"])
    args = parser.parse_args()
    optimize(args.source_dir, args.destination, args.report, args.kinds)
