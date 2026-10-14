"""Validate reviewed bilingual source profiles and emit native Android resources/catalog.

Only profiles with a final offline illustration are published. Raw research belongs in
app/build/bestiary_expansion; copy reviewed JSON into tools/bestiary/data/profiles first.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/parentes"))
from build_runtime import decode
from audit_profile_sources import audit

SECTIONS = ("overview", "identification", "habitat", "diet", "behavior",
            "reproduction", "ecology", "conservation")
GROUPS = {"mammals", "birds", "reptiles", "amphibians", "fish", "invertebrates"}


def profiles():
    _, nodes, _, _ = decode((ROOT / "app/src/main/assets/parentes/runtime.bin").read_bytes())
    by_id = {n[0]: n for n in nodes}
    metazoa = next(n[0] for n in nodes if n[2] == "Metazoa")
    animal_groups = {}
    for node in nodes:
        if node[4] != "1":
            continue
        parent = node[0]
        lineage = []
        while parent:
            lineage.append(by_id[parent][2])
            if parent == metazoa:
                animal_groups[node[2]] = next((group for clade, group in (
                    ("Mammalia", "mammals"), ("Aves", "birds"), ("Amphibia", "amphibians"),
                    ("Sauropsida", "reptiles"), ("Vertebrata", "fish")) if clade in lineage), "invertebrates")
                break
            parent = by_id[parent][1]
    records = []
    directory = ROOT / "tools/bestiary/data/profiles"
    for file in sorted(directory.glob("*.json")):
        record = json.loads(file.read_text(encoding="utf-8-sig"))
        def check_encoding(value):
            if isinstance(value, dict):
                for item in value.values():
                    check_encoding(item)
            elif isinstance(value, list):
                for item in value:
                    check_encoding(item)
            elif isinstance(value, str):
                assert not any(marker in value for marker in ("\ufffd", "Ã©", "Ã‰", "â€™", "â€”", "â€", "ï¿½")), f"Corrupt text encoding: {file}"
        check_encoding(record)
        animal_id = record["id"]
        assert re.fullmatch(r"[a-z_]+", animal_id), file
        assert record["scientific"] in animal_groups, f"Not an animal species in tree: {file}"
        assert record["group"] in GROUPS, file
        assert record["group"] == animal_groups[record["scientific"]], f"Group disagrees with tree ancestry: {file}"
        assert set(record["sections"]) == set(SECTIONS), file
        assert record.get("reviewed") is True, f"Not reviewed: {file}"
        if record.get("source_audit_version") == 1:
            errors, _, _ = audit(record)
            assert not errors, f"Source sentence accounting failed: {file}: {errors}"
        assert (ROOT / f"app/src/main/assets/bestiary/{animal_id}.webp").is_file(), f"Missing illustration: {file}"
        sources = record["sources"]
        assert len(sources) >= 2 and len({s["url"] for s in sources}) == len(sources), file
        assert len({urlparse(s["url"]).hostname for s in sources}) >= 2, file
        covered = set()
        for source in sources:
            url = urlparse(source["url"])
            # Institutional publication records can use a root path with a document query.
            assert url.scheme == "https" and url.hostname and (len(url.path) > 1 or url.query), file
            assert source["consulted"] and source["facts"] and source["supports"], file
            covered.update(source["supports"])
            for locale in ("en", "fr"):
                assert source["title"][locale].strip(), file
        assert set(SECTIONS) <= covered, f"Sections without source support: {file}"
        for locale in ("en", "fr"):
            assert record["name"][locale].strip(), file
            for section in SECTIONS:
                text = record["sections"][section][locale]
                assert text.strip() and len(text.split()) >= 15, f"Empty/telegraphic {file}/{section}/{locale}"
        records.append(record)
    assert len({r["id"] for r in records}) == len(records)
    assert len({r["scientific"] for r in records}) == len(records)
    return records


def android_text(text):
    return text.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')


def generate(records):
    resource_groups = {
        "a_c": lambda record: record["id"][0] <= "c",
        "d_l": lambda record: "d" <= record["id"][0] <= "l",
        "m_r": lambda record: "m" <= record["id"][0] <= "r",
        "s_z": lambda record: "s" <= record["id"][0] <= "z",
    }
    for locale, resource_dir in (("en", "values"), ("fr", "values-fr")):
        for group, include in resource_groups.items():
            root = ET.Element("resources")
            for record in records:
                if not include(record):
                    continue
                prefix = "animal_encyclopedia_" + record["id"]
                values = {"name": record["name"][locale], "latin": record["scientific"]}
                values.update({s: record["sections"][s][locale] for s in SECTIONS})
                values.update({f"source_{i}": source["title"][locale]
                               for i, source in enumerate(record["sources"], 1)})
                for suffix, value in values.items():
                    ET.SubElement(root, "string", name=f"{prefix}_{suffix}", formatted="false").text = android_text(value)
            ET.indent(root, space="    ")
            ET.ElementTree(root).write(
                ROOT / f"app/src/main/res/{resource_dir}/strings_bestiary_encyclopedia_{group}.xml",
                encoding="utf-8", xml_declaration=True)
    # Small functions avoid the JVM method-size limit as the collection grows.
    lines = ["package com.Atom2Universe.app.science.bestiary", "",
             "import com.Atom2Universe.app.R", "",
             "/** Generated by tools/bestiary/import_profiles.py from reviewed source records. */",
             "internal object BestiaryEncyclopediaCatalog {"]
    chunks = [records[i:i + 40] for i in range(0, len(records), 40)]
    lines.append("    val species: List<AnimalSpecies> = " +
                 (" + ".join(f"batch{i}()" for i in range(len(chunks))) if chunks else "emptyList()"))
    for i, chunk in enumerate(chunks):
        lines += ["", f"    private fun batch{i}(): List<AnimalSpecies> = listOf("]
        for record in chunk:
            prefix = "R.string.animal_encyclopedia_" + record["id"] + "_"
            string = lambda value: json.dumps(value, ensure_ascii=False).replace("$", "\\$")
            references = ", ".join(f"AnimalReference({string(s['url'])}, {prefix}source_{j})"
                                   for j, s in enumerate(record["sources"], 1))
            lines += ["        AnimalSpecies(",
                      f"            id = {string(record['id'])}, scientific = {string(record['scientific'])},",
                      f"            name = {prefix}name, group = {string(record['group'])},",
                      f"            description = {prefix}overview, identification = {prefix}identification,",
                      f"            habitat = {prefix}habitat, diet = {prefix}diet, behavior = {prefix}behavior,",
                      f"            reproduction = {prefix}reproduction, ecology = {prefix}ecology, conservation = {prefix}conservation,",
                      f"            latin = {prefix}latin, sourceUrl = {string(record['sources'][0]['url'])}, sourceTitle = {prefix}source_1,",
                      f"            references = listOf({references})", "        ),"]
        lines.append("    )")
    lines += ["}", ""]
    destination = ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/BestiaryEncyclopediaCatalog.kt"
    destination.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    reviewed = profiles()
    generate(reviewed)
    print(f"Published {len(reviewed)} reviewed encyclopedia profiles in EN/FR")
