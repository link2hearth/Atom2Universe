"""Validate bilingual mycology articles and reviewed herbarium assets."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/science/mycology"
CATALOG = ROOT / "app/src/main/java/com/Atom2Universe/app/science/mycology"
IDS = set()
for filename in ("FungusCatalog.kt", "FungusSupplementCatalog.kt"):
    IDS.update(re.findall(r'id = "([a-z0-9_]+)"', (CATALOG / filename).read_text(encoding="utf-8")))


def main() -> None:
    reviewed = set()
    for species_id in sorted(IDS):
        articles = {}
        paths = {
            language: ASSETS / "encyclopedia" / language / f"{species_id}.json"
            for language in ("en", "fr")
        }
        assert paths["en"].exists() == paths["fr"].exists(), species_id
        if not paths["en"].exists():
            continue
        for language in ("en", "fr"):
            path = paths[language]
            article = json.loads(path.read_text(encoding="utf-8"))
            assert article["id"] == species_id, path
            assert article["reviewStatus"] in ("pending", "verified"), path
            assert article["sections"] or article.get("traits"), path
            assert article.get("safetyNote"), path
            assert article["sources"], path
            assert all(source["title"] and source["url"].startswith("https://") for source in article["sources"]), path
            assert all(related in IDS for related in article.get("related", [])), path
            articles[language] = article
        assert articles["en"]["reviewStatus"] == articles["fr"]["reviewStatus"], species_id
        if articles["en"]["reviewStatus"] == "verified":
            reviewed.add(species_id)
            image = (ASSETS / "herbarium" / f"{species_id}.webp").read_bytes()
            assert len(image) <= 50_000, species_id
            assert image[:4] == b"RIFF" and image[8:12] == b"WEBP", species_id
    print(f"{len(IDS)} bilingual articles: {len(reviewed)} verified with WebP <= 50,000 bytes; {len(IDS) - len(reviewed)} pending")


if __name__ == "__main__":
    main()
