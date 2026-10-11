"""Publish reviewed artwork independently of encyclopedia article production."""
import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("reviews", type=Path)
    parser.add_argument("plates", type=Path)
    parser.add_argument("--documents", type=Path, default=ROOT.parent / "Documents NON commit du projet")
    parser.add_argument("--authorized-retry", help="Explicit user request authorizing these new attempts")
    args = parser.parse_args()
    records = read(args.manifest)
    review_rows = read(args.reviews)
    reviews = {r["id"]: r for r in review_rows}
    if len(reviews) != len(review_rows) or len({r["id"] for r in records}) != len(records):
        raise ValueError("Duplicate artwork/review ID")
    if set(reviews) != {r["id"] for r in records}:
        raise ValueError("Every generated image needs its own completed review")
    identity_path = ROOT / "tools/bestiary/illustrated_species.json"
    identity = read(identity_path)
    by_id = {r["id"]: r for r in identity["species"]}
    inventory = {r["scientific"]: r for r in read(ROOT / "app/build/bestiary_expansion/inventory.json")}
    accepted = deferred = 0
    prepared = []
    for row in records:
        if not re.fullmatch(r"[a-z_]+", row["id"]):
            raise ValueError("Unsafe artwork ID")
        decision = reviews[row["id"]]
        if decision["decision"] not in {"accepted", "deferred"} or not decision.get("reason", "").strip():
            raise ValueError("Review must record an explicit decision and reason")
        row.update(status=decision["decision"], review_note=decision["reason"])
        if row.get("attempts") != 1 and not (args.authorized_retry and row.get("attempts", 0) >= 2):
            raise ValueError("New illustration waves allow exactly one generation per species")
        if args.authorized_retry:
            row["authorization"] = args.authorized_retry
        if row["scientific"] not in inventory or inventory[row["scientific"]]["group"] != row["group"]:
            raise ValueError("Scientific identity/group is absent from the animal inventory")
        if row["status"] == "deferred":
            continue
        plate = args.plates / f"{row['id']}.webp"
        with Image.open(plate) as image:
            if image.format != "WEBP" or max(image.size) != 640 or min(image.size) < 320:
                raise ValueError(f"Unexpected optimized illustration: {plate}")
        if not 1000 <= plate.stat().st_size <= 153600:
            raise ValueError(f"Illustration exceeds offline asset budget: {plate}")
        item = dict(id=row["id"], scientific_name=row["scientific"],
                    name_en=row["name_en"].replace("\\'", "'"), name_fr=row["name_fr"].replace("\\'", "'"),
                    group=row["group"], image=plate.name)
        if item["id"] in by_id and by_id[item["id"]] != item:
            raise ValueError("Refusing to replace a different existing species identity")
        if any(r["scientific_name"] == item["scientific_name"] and r["id"] != item["id"] for r in by_id.values()):
            raise ValueError("A scientific species already has another illustration ID")
        target = ROOT / "app/src/main/assets/bestiary" / plate.name
        if target.exists() and hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(plate.read_bytes()).digest():
            if not args.authorized_retry:
                raise ValueError("Refusing to overwrite a different existing illustration")
            backup = args.plates.parent / "previous_assets" / plate.name
            if backup.exists() and backup.read_bytes() != target.read_bytes():
                raise ValueError("Previous illustration backup already has different contents")
            row["previous_asset"] = str(backup.resolve())
        prepared.append((row, item, plate, target))
    for row, item, plate, target in prepared:
        if row.get("previous_asset"):
            backup = Path(row["previous_asset"])
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, backup)
        shutil.copy2(plate, target)
        by_id[item["id"]] = item
        row.update(bytes=target.stat().st_size, asset=str(target))
        accepted += 1
    for row in records:
        if row["status"] == "deferred":
            destination = args.documents / "Bestiaire/a revoir"
            destination.mkdir(parents=True, exist_ok=True)
            suffix = f"-retry{row['attempts']}" if args.authorized_retry else ""
            target = destination / f"{row['id']}{suffix}.png"
            if target.exists() and target.read_bytes() != Path(row["path"]).read_bytes():
                raise ValueError("Refusing to overwrite a different deferred original")
            shutil.copy2(row["path"], target)
            write(destination / f"{row['id']}{suffix}.json", row)
            deferred += 1
    identity["species"] = list(by_id.values())
    write(identity_path, identity)
    write(args.manifest, records)
    phase = "after authorized retry" if args.authorized_retry else "without regeneration"
    print(f"{accepted} accepted plates published; {deferred} originals deferred {phase}")


if __name__ == "__main__":
    main()
