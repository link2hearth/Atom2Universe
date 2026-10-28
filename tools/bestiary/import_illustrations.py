"""Validate the accepted art manifest against the tree and the published profiles.

The app only lists encyclopedic profiles (import_profiles.py): an illustration whose
profile is not published yet is reported, never turned into an identity-only entry.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "tools/bestiary/illustrated_species.json"
ASSETS = ROOT / "app/src/main/assets/science/bestiary"
GROUPS = {"mammals", "birds", "reptiles", "amphibians", "fish", "invertebrates"}
ID_RE = re.compile(r"[a-z][a-z0-9_]*\Z")


def load_records():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8-sig"))
    if manifest.get("schema_version") != 1 or manifest.get("review_status") != "accepted":
        raise ValueError("Expected schema_version 1 and review_status 'accepted'")
    records = manifest.get("species")
    if not isinstance(records, list):
        raise ValueError("species must be a JSON array")

    # Verify names against the exact terminal taxa and their ancestry used by the tree.
    sys.path.insert(0, str(ROOT / "tools/parentes"))
    from build_runtime import decode
    _, tree_nodes, _, _ = decode((ROOT / "app/src/main/assets/parentes/runtime.bin").read_bytes())
    by_id = {node[0]: node for node in tree_nodes}
    tree_species = {}
    for node in tree_nodes:
        if node[4] == "1":
            tree_species.setdefault(node[2], []).append(node)
    def tree_group(scientific):
        matches = tree_species.get(scientific, [])
        if len(matches) != 1:
            return None
        node = matches[0]
        parent = node[0]
        lineage = set()
        while parent:
            parent_node = by_id[parent]
            lineage.add(parent_node[2])
            parent = parent_node[1]
        return next((group for clade, group in (
            ("Mammalia", "mammals"), ("Aves", "birds"), ("Amphibia", "amphibians"),
            ("Sauropsida", "reptiles"), ("Vertebrata", "fish")) if clade in lineage), "invertebrates")

    ids, scientific_names = set(), set()
    for row in records:
        required = {"id", "scientific_name", "name_en", "name_fr", "group", "image"}
        if not isinstance(row, dict) or set(row) != required:
            raise ValueError(f"Each species must contain exactly {sorted(required)}: {row!r}")
        animal_id = row["id"]
        if not isinstance(animal_id, str) or not ID_RE.fullmatch(animal_id):
            raise ValueError(f"Invalid stable species id: {animal_id!r}")
        if animal_id in ids:
            raise ValueError(f"Duplicate species id: {animal_id}")
        ids.add(animal_id)
        scientific = row["scientific_name"]
        if not isinstance(scientific, str) or not re.fullmatch(r"[A-Z][a-z]+ [a-z][a-z-]+", scientific):
            raise ValueError(f"Expected a binomial scientific_name for {animal_id}")
        if scientific in scientific_names:
            raise ValueError(f"Duplicate scientific name: {scientific}")
        scientific_names.add(scientific)
        if row["group"] not in GROUPS:
            raise ValueError(f"Unknown animal group for {animal_id}: {row['group']!r}")
        if tree_group(scientific) != row["group"]:
            raise ValueError(f"Scientific identity/group does not match the living tree: {animal_id}")
        if any(not isinstance(row[key], str) or not row[key].strip() for key in ("name_en", "name_fr")):
            raise ValueError(f"Both English and French common names are required for {animal_id}")
        if row["image"] != f"{animal_id}.webp" or not (ASSETS / row["image"]).is_file():
            raise ValueError(f"Manifest image must be the existing {animal_id}.webp asset")

    # Refuse identity collisions with the published profiles.
    detailed = {}
    # Draft profile files may be written by another editorial task: only
    # published profiles count.
    published_source = (ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/BestiaryEncyclopediaCatalog.kt").read_text(encoding="utf-8")
    published_ids = set(re.findall(r'id = "([a-z][a-z0-9_]*)"', published_source))
    for profile in (ROOT / "tools/bestiary/data/profiles").glob("*.json"):
        value = json.loads(profile.read_text(encoding="utf-8-sig"))
        if value["id"] not in published_ids:
            continue
        prior = detailed.get(value["id"])
        if prior is not None and prior != value["scientific"]:
            raise ValueError(f"Detailed profile id has a different scientific name: {value['id']}")
        detailed[value["id"]] = value["scientific"]

    detailed_scientific = {scientific: animal_id for animal_id, scientific in detailed.items()}
    awaiting = []
    for row in records:
        prior = detailed.get(row["id"])
        if prior is not None:
            if prior != row["scientific_name"]:
                raise ValueError(f"Manifest id conflicts with detailed profile identity: {row['id']}")
            continue
        other_id = detailed_scientific.get(row["scientific_name"])
        if other_id is not None and other_id != row["id"]:
            raise ValueError(f"Manifest scientific name conflicts with detailed profile id {other_id}: {row['id']}")
        awaiting.append(row)
    return awaiting


if __name__ == "__main__":
    awaiting = load_records()
    for row in awaiting:
        print(f"Awaiting a published profile: {row['id']} ({row['scientific_name']})")
    print(f"Manifest valid; {len(awaiting)} illustrations are not shown until their profile is published")
