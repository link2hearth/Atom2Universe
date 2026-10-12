"""Reserve new animal artwork after checking all published and attempted identities."""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "app/build/bestiary_expansion"
STYLE = (
    "Use case: scientific-educational. Landscape 4:3 natural-history encyclopedia plate. "
    "ONE complete anatomically accurate animal, exquisite fine graphite/ink with restrained "
    "realistic watercolor, natural textures and lifelike proportions. Pure uniform white "
    "#FFFFFF background and broad clean white margins around all limbs and the tail. "
    "No text, labels, scenery, branches, plants, water, frame, watermark or cartoon. "
    "Recognizable at 640px. "
)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def dorsal_constraints():
    """Apply the user's preferred view from actual tree ancestry, not name guesses."""
    sys.path.insert(0, str(ROOT / "tools/parentes"))
    from build_runtime import decode
    _, nodes, _, _ = decode((ROOT / "app/src/main/assets/parentes/runtime.bin").read_bytes())
    by_id = {node[0]: node for node in nodes}
    result = {}
    for node in nodes:
        if node[4] != "1":
            continue
        parent, lineage = node[0], set()
        while parent:
            ancestor = by_id[parent]
            lineage.add(ancestor[2])
            parent = ancestor[1]
        if "Araneae" in lineage:
            result[node[2]] = (
                " Strict direct top-down dorsal view. Exactly eight walking legs, four per side, "
                "each articulated to the cephalothorax and separately traceable; two shorter "
                "front pedipalps distinct from walking legs. Spread limbs naturally without overlap."
            )
        elif "Amphibia" in lineage:
            result[node[2]] = (
                " Strict direct top-down dorsal view, head upward. Spread existing limbs "
                "naturally so the feet and digits can be inspected without overlap. "
                "Respect this species' actual limb and digit anatomy; never add limbs "
                "to a naturally limbless amphibian."
            )
        elif "Insecta" in lineage:
            result[node[2]] = (
                " Strict direct top-down dorsal view. Exactly six walking legs articulated to "
                "the thorax, separated and naturally spread so their joints can be traced."
            )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("prompts", type=Path)
    parser.add_argument("wave", type=Path)
    args = parser.parse_args()
    if (args.wave / "queue.json").exists():
        raise ValueError("This wave is already reserved; never start it again")
    records = read(args.prompts)
    inventory = {r["scientific"]: r for r in read(BUILD / "inventory.json")}
    views = dorsal_constraints()
    used_scientific, used_ids = set(), set()
    initial = re.findall(r'AnimalSpecies\("([a-z_]+)", "([^"]+)"',
                         (ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/AnimalCatalog.kt").read_text("utf-8"))
    for animal_id, scientific in initial:
        used_ids.add(animal_id)
        used_scientific.add(scientific)
    for profile in (ROOT / "tools/bestiary/data/profiles").glob("*.json"):
        row = read(profile)
        used_ids.add(row["id"])
        used_scientific.add(row["scientific"])
    for row in read(ROOT / "tools/bestiary/illustrated_species.json")["species"]:
        used_ids.add(row["id"])
        used_scientific.add(row["scientific_name"])
    for path in [*BUILD.glob("*manifest*.json"), *BUILD.glob("illustration_wave_*/queue.json"),
                 *BUILD.glob("continuation_*/queue.json"), *BUILD.glob("continuation_*/root_*_manifest.json")]:
        rows = read(path)
        if isinstance(rows, list):
            for row in rows:
                if isinstance(row, dict):
                    if row.get("id"):
                        used_ids.add(row["id"])
                    if row.get("scientific"):
                        used_scientific.add(row["scientific"])
    for path in BUILD.glob("lot20_*/plan.json"):
        for row in read(path)["items"]:
            used_ids.add(row["id"])
            used_scientific.add(row["scientific"])
    if len({r["id"] for r in records}) != len(records) or len({r["scientific"] for r in records}) != len(records):
        raise ValueError("Duplicate identity inside proposed wave")
    for row in records:
        if not re.fullmatch(r"[a-z_]+", row["id"]):
            raise ValueError("Unsafe animal ID")
        if row["id"] in used_ids or row["scientific"] in used_scientific:
            raise ValueError(f"Already published, generated, or reserved: {row['scientific']}")
        if (ROOT / "app/src/main/assets/bestiary" / f"{row['id']}.webp").exists():
            raise ValueError("An asset with this ID already exists")
        if row["scientific"] not in inventory or inventory[row["scientific"]]["group"] != row["group"]:
            raise ValueError("Species identity/group does not match the tree inventory")
        if not all(row.get(k, "").strip() for k in ("name_en", "name_fr", "prompt_detail", "source_url", "checked_brief")):
            raise ValueError("Names, verified visual traits and source are required")
        for key in ("name_en", "name_fr"):
            row[key] = row[key].replace("\\'", "'")
        row.update(prompt=STYLE + row["scientific"] + ". " + row["prompt_detail"] + views.get(row["scientific"], ""),
                   attempts=1, status="generation_started")
    args.wave.mkdir(parents=True, exist_ok=True)
    (args.wave / "queue.json").write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(records)} new species reserved; no published or attempted animal repeated")


if __name__ == "__main__":
    main()
