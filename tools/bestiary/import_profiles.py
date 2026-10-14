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
    # Long encyclopedia prose lives in localized assets so R$string stays within
    # Android's class-file size limit as the catalog grows.
    assets = ROOT / "app/src/main/assets/bestiary/encyclopedia"
    for locale in ("en", "fr"):
        directory = assets / locale
        directory.mkdir(parents=True, exist_ok=True)
        for record in records:
            payload = {
                "id": record["id"],
                "name": record["name"][locale],
                "scientific": record["scientific"],
                "group": record["group"],
                "sections": {section: record["sections"][section][locale] for section in SECTIONS},
                "sources": [{"url": source["url"], "title": source["title"][locale]}
                            for source in record["sources"]],
            }
            (directory / f"{record['id']}.json").write_text(
                json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # Retire the old generated string resources. Keeping the prose in XML made
    # the single generated R$string class exceed the JVM class-file limit.
    for locale_dir in ("values", "values-fr"):
        for group in ("a_c", "d_l", "m_r", "s_z"):
            old_xml = ROOT / f"app/src/main/res/{locale_dir}/strings_bestiary_encyclopedia_{group}.xml"
            if old_xml.exists():
                old_xml.unlink()

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
            string = lambda value: json.dumps(value, ensure_ascii=False).replace("$", "\\$")
            lines += ["        AnimalSpecies(",
                      f"            id = {string(record['id'])}, scientific = {string(record['scientific'])},",
                      f"            name = R.string.be_encyclopedia_placeholder, group = {string(record['group'])},",
                      "            description = null, habitat = null, diet = null, behavior = null, identification = null,",
                      "            sourceUrl = null, sourceTitle = null, latin = R.string.be_encyclopedia_placeholder,",
                      "            reproduction = null, ecology = null, conservation = null, references = emptyList(),",
                      f"            encyclopediaId = {string(record['id'])}", "        ),"]
        lines.append("    )")

    name_chunks = [records[i:i + 80] for i in range(0, len(records), 80)]
    for locale, prefix in (("en", "namesEn"), ("fr", "namesFr")):
        chunk_names = []
        for i, chunk in enumerate(name_chunks):
            fn = f"{prefix}{i}"
            chunk_names.append(f"{fn}()")
            lines += ["", f"    private fun {fn}(): Map<String, String> = mapOf("]
            for record in chunk:
                string = lambda value: json.dumps(value, ensure_ascii=False).replace("$", "\\$")
                lines.append(f"        {string(record['id'])} to {string(record['name'][locale])},")
            lines.append("    )")
        lines += ["", f"    private val {prefix}: Map<String, String> = " + " + ".join(chunk_names)]
    lines += ["", "    fun name(id: String, language: String): String? =",
              "        if (language == \"fr\") namesFr[id] else namesEn[id]"]
    lines += ["}", ""]
    destination = ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/BestiaryEncyclopediaCatalog.kt"
    destination.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    reviewed = profiles()
    generate(reviewed)
    print(f"Published {len(reviewed)} reviewed encyclopedia profiles in EN/FR")
