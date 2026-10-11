"""Record a single-attempt art review and preserve deferred plates for later repair."""
import argparse
import json
import shutil
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("review", type=Path)
    parser.add_argument("--deferred", type=Path, default=root.parent / "Documents NON commit du projet/Bestiaire/a revoir")
    args = parser.parse_args()
    records = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    decisions = json.loads(args.review.read_text(encoding="utf-8-sig"))
    by_id = {r["id"]: r for r in decisions}
    if len(by_id) != len(decisions) or set(by_id) != {r["id"] for r in records}:
        raise ValueError("The review must cover every manifest species exactly once")
    if any(r["decision"] not in ("accepted", "deferred") or not r["reason"].strip() for r in decisions):
        raise ValueError("Each species needs an explicit decision and reason")
    deferred = []
    for record in records:
        decision = by_id[record["id"]]
        record.update(status=decision["decision"], review_note=decision["reason"], review_date="2026-10-11")
        if decision["decision"] == "deferred":
            args.deferred.mkdir(parents=True, exist_ok=True)
            original = Path(record["path"])
            destination = args.deferred / (record["id"] + original.suffix)
            if destination.exists() and destination.read_bytes() != original.read_bytes():
                raise FileExistsError(f"Preserve existing deferred artwork: {destination}")
            shutil.copy2(original, destination)
            record["deferred_path"] = str(destination)
            (args.deferred / (record["id"] + ".json")).write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            deferred.append(record["id"])
    args.manifest.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(records) - len(deferred)} accepted; {len(deferred)} deferred; no regeneration")


if __name__ == "__main__":
    main()
