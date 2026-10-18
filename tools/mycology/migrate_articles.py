"""Create initial bilingual mycology articles from the existing Android resources.

The generated articles retain their original claims and are marked pending until
their species has been checked against the cited sources. Existing article files
are never replaced: a reviewed article is authoritative.
"""

from __future__ import annotations

import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/science/mycology"
CATALOG = ROOT / "app/src/main/java/com/Atom2Universe/app/science/mycology"
IDS = []
for filename in ("FungusCatalog.kt", "FungusSupplementCatalog.kt"):
    IDS.extend(re.findall(r'id = "([a-z0-9_]+)"', (CATALOG / filename).read_text(encoding="utf-8")))
assert len(IDS) == len(set(IDS))

SOURCES = json.loads((ASSETS / "sources.json").read_text(encoding="utf-8"))["sources"]


def resource_text(element: ET.Element) -> str:
    value = "".join(element.itertext()).strip()
    return value.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")


def resources(language: str) -> tuple[dict[str, str], dict[str, list[str]]]:
    folder = "values-fr" if language == "fr" else "values"
    tree = ET.parse(ROOT / f"app/src/main/res/{folder}/strings_mycology.xml")
    strings = {e.attrib["name"]: resource_text(e) for e in tree.findall("string")}
    arrays = {
        e.attrib["name"]: [resource_text(item) for item in e.findall("item")]
        for e in tree.findall("string-array")
    }
    return strings, arrays


def main() -> None:
    for language in ("en", "fr"):
        strings, arrays = resources(language)
        folder = ASSETS / "encyclopedia" / language
        folder.mkdir(parents=True, exist_ok=True)
        for species_id in IDS:
            path = folder / f"{species_id}.json"
            if path.exists():
                continue
            prefix = f"myco_{species_id}_"
            traits = arrays[prefix + "traits"]
            note = strings[prefix + "note"]
            matching = [s for s in SOURCES if species_id in s.get("species", [])]
            article = {
                "id": species_id,
                "reviewStatus": "pending",
                "sections": [],
                "traits": traits,
                "safetyNote": note,
                "sources": [{"title": s["citation"], "url": s["url"]} for s in matching],
                "related": [],
            }
            path.write_text(json.dumps(article, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared articles for {len(IDS)} species in English and French")


if __name__ == "__main__":
    main()
