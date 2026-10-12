"""Generate minimal bilingual bestiary identities from the accepted art manifest.

This deliberately imports names and taxonomy only. Detailed profiles remain owned by
import_profiles.py and take precedence in AnimalCatalog.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "tools/bestiary/illustrated_species.json"
ASSETS = ROOT / "app/src/main/assets/bestiary"
GROUPS = {"mammals", "birds", "reptiles", "amphibians", "fish", "invertebrates"}
ID_RE = re.compile(r"[a-z][a-z0-9_]*\Z")


def android_text(value):
    return value.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")


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

    # Refuse identity collisions with either detailed source catalogue.
    source = (ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/AnimalCatalog.kt").read_text(encoding="utf-8")
    initial = re.findall(r'AnimalSpecies\("([a-z][a-z0-9_]*)", "([^"]+)"', source)
    detailed = {animal_id: scientific for animal_id, scientific in initial}
    # Draft profile files may be written by another editorial task. They must
    # not hide an illustrated entry until the detailed native entry is published.
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
    minimal = []
    for row in records:
        prior = detailed.get(row["id"])
        if prior is not None:
            if prior != row["scientific_name"]:
                raise ValueError(f"Manifest id conflicts with detailed profile identity: {row['id']}")
            continue  # Keep the detailed catalog entry and its reviewed content.
        other_id = detailed_scientific.get(row["scientific_name"])
        if other_id is not None and other_id != row["id"]:
            raise ValueError(f"Manifest scientific name conflicts with detailed profile id {other_id}: {row['id']}")
        minimal.append(row)
    return minimal


def write_resources(records):
    for locale, directory, key in (("en", "values", "name_en"), ("fr", "values-fr", "name_fr")):
        root = ET.Element("resources")
        for row in records:
            prefix = "animal_illustrated_" + row["id"]
            # Accept names pasted with Python/Java-style escaped apostrophes in the JSON.
            ET.SubElement(root, "string", name=f"{prefix}_name", formatted="false").text = android_text(row[key].replace("\\'", "'"))
            ET.SubElement(root, "string", name=f"{prefix}_latin", formatted="false").text = android_text(row["scientific_name"])
        ET.indent(root, space="    ")
        ET.ElementTree(root).write(ROOT / f"app/src/main/res/{directory}/strings_bestiary_illustrated.xml",
                                   encoding="utf-8", xml_declaration=True)


def write_catalog(records):
    lines = ["package com.Atom2Universe.app.science.bestiary", "", "import com.Atom2Universe.app.R", "",
             "/** Generated from the accepted, identity-only illustration manifest. */",
             "internal object BestiaryIllustratedCatalog {"]
    chunks = [records[index:index + 40] for index in range(0, len(records), 40)]
    lines.append("    val species: List<AnimalSpecies> = " +
                 (" + ".join(f"batch{index}()" for index in range(len(chunks))) if chunks else "emptyList()"))
    for index, chunk in enumerate(chunks):
        lines += ["", f"    private fun batch{index}(): List<AnimalSpecies> = listOf("]
        for row in chunk:
            prefix = "R.string.animal_illustrated_" + row["id"]
            lines += [
                "        AnimalSpecies(",
                f'            id = {json.dumps(row["id"])}, scientific = {json.dumps(row["scientific_name"])},',
                f"            name = {prefix}_name, group = {json.dumps(row['group'])},",
                "            description = null, identification = null, habitat = null, diet = null, behavior = null,",
                "            sourceUrl = null, sourceTitle = null, latin = " + prefix + "_latin,",
                "            references = emptyList()", "        ),"
            ]
        lines.append("    )")
    lines += ["}", ""]
    (ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/BestiaryIllustratedCatalog.kt").write_text(
        "\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    reviewed = load_records()
    write_resources(reviewed)
    write_catalog(reviewed)
    print(f"Published {len(reviewed)} accepted illustration-only identities in EN/FR")
