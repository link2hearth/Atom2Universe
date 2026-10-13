"""Assemble les frames PNG du banc natif Hamlet en aperçus GIF.

Usage : python tools/hamlet/export_previews.py
Les dessins proviennent du moteur Kotlin ; ce script ne redessine aucun asset.
"""
import argparse
from pathlib import Path
from PIL import Image


def export_frames(folder: Path, prefix: str, duration: int):
    paths = sorted(folder.glob(f"{prefix}_*.png"), key=lambda p: int(p.stem.rsplit("_", 1)[1]))
    if not paths:
        raise FileNotFoundError(f"Frames absentes : {folder / prefix}")
    frames = []
    for path in paths:
        with Image.open(path) as source:
            frames.append(source.convert("RGB"))
    target = folder / f"{prefix}.gif"
    frames[0].save(target, save_all=True, append_images=frames[1:], duration=duration,
                   loop=0, optimize=True, disposal=2)
    print(f"{target}: {len(frames)} frames, {target.stat().st_size:,} octets")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--folder", type=Path, default=Path("app/build/apercu/hamlet"))
    args = parser.parse_args()
    export_frames(args.folder, "personnages_animation", 110)
    export_frames(args.folder, "batiments_animation", 125)
