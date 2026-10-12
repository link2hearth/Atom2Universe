"""Refresh the external mammal progress ledger from inventory and native assets.

Generated images and queued prompts are recorded as pending until their native
asset is actually integrated. The script never edits project catalogs or assets.
"""
import argparse
import json
import re
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "app/build/bestiary_expansion"
DOCS_DEFAULT = ROOT.parent / "Documents NON commit du projet"
FAMILY_OVERRIDES = {
    "Balaenoptera musculus": "Balaenopteridae",
    "Megaptera novaeangliae": "Balaenopteridae",
    "Gorilla gorilla": "Hominidae",
    "Pan paniscus": "Hominidae",
    "Pan troglodytes": "Hominidae",
    "Homo sapiens": "Hominidae",
    "Paradoxurus hermaphroditus": "Viverridae",
}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def mammal_catalog_ids_by_scientific():
    """Read native constructor pairs instead of guessing IDs from Latin names."""
    catalog_dir = ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary"
    by_scientific = defaultdict(set)
    for path in catalog_dir.glob("*Catalog.kt"):
        source = path.read_text(encoding="utf-8")
        for block in re.split(r"AnimalSpecies\s*\(", source)[1:]:
            if 'group = "mammals"' not in block and not re.search(r',\s*"mammals"\s*,', block):
                continue
            match = re.search(r'\bid\s*=\s*"([a-z0-9_]+)"\s*,\s*scientific\s*=\s*"([^"]+)"', block)
            if not match:
                match = re.search(r'^\s*"([a-z0-9_]+)"\s*,\s*"([^"]+)"', block)
            if match:
                by_scientific[match.group(2)].add(match.group(1))
    return by_scientific


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--documents", type=Path, default=DOCS_DEFAULT)
    parser.add_argument("--stdout", action="store_true", help="Print Markdown without writing outside the project.")
    args = parser.parse_args()

    inventory_path = BUILD / "mammals_inventory.json"
    if not inventory_path.exists():
        inventory_path = args.documents / "Bestiaire/MAMMIFERES_INVENTAIRE.json"
    inventory = read_json(inventory_path)
    catalog_ids_by_scientific = mammal_catalog_ids_by_scientific()
    asset_dir = ROOT / "app/src/main/assets/bestiary"
    native_scientific = {
        scientific for scientific, animal_ids in catalog_ids_by_scientific.items()
        if any((asset_dir / f"{animal_id}.{ext}").is_file()
               for animal_id in animal_ids for ext in ("webp", "png", "jpg", "jpeg"))
    }

    review_by_id = {}
    review_notes = {}
    for review_path in BUILD.glob("**/reviews.json"):
        try:
            records = read_json(review_path)
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(records, list):
            for record in records:
                if isinstance(record, dict) and record.get("id"):
                    review_by_id[record["id"]] = record.get("decision", "")
                    review_notes[record["id"]] = record.get("reason", "")

    source_gap_ids = {}
    preserved = args.documents / "Bestiaire/MAMMIFERES_PREPARATION.json"
    if preserved.exists():
        for record in read_json(preserved):
            if record.get("source_gap"):
                source_gap_ids[record["id"]] = record.get("checked_brief", "")
    for prompt_path in BUILD.glob("mammal_batch_*_prompts.json"):
        for record in read_json(prompt_path):
            if record.get("source_gap") and record.get("id"):
                source_gap_ids[record["id"]] = record.get("checked_brief", "Gap source noté dans le prompt.")
    for gap_path in BUILD.glob("**/source_gaps.json"):
        for record in read_json(gap_path):
            if record.get("id"):
                source_gap_ids[record["id"]] = record.get("reason", "")
    batch_gap_path = BUILD / "mammal_batch_003_sources_gap.json"
    if batch_gap_path.exists():
        for record in read_json(batch_gap_path):
            if record.get("id"):
                source_gap_ids[record["id"]] = record.get("reason", "")

    # Normalize families from the authoritative inventory while applying only
    # the seven explicit family corrections requested for the unclassified rows.
    rows = []
    for row in inventory:
        animal_id = row.get("id") or row.get("existing_id") or ""
        scientific = row["scientific"]
        family = FAMILY_OVERRIDES.get(scientific, row.get("family") or "Non classée")
        source_aliases = {animal_id, re.sub(r"[^a-z0-9]+", "_", scientific.casefold()).strip("_")}
        is_illustrated = scientific in native_scientific
        is_source_gap = not is_illustrated and bool(source_aliases & source_gap_ids.keys())
        is_review = not is_illustrated and bool(
            any(review_by_id.get(alias) == "deferred" for alias in source_aliases)
            or any((args.documents / "Bestiaire/a revoir" / f"{alias}.json").exists()
                   for alias in source_aliases if alias))
        review_reason = next((review_notes[alias] for alias in source_aliases
                              if review_by_id.get(alias) == "deferred"), "")
        if is_review and not review_reason:
            for alias in source_aliases:
                archived = args.documents / "Bestiaire/a revoir" / f"{alias}.json"
                if alias and archived.exists():
                    review_reason = read_json(archived).get("review_note", "")
                    break
        gap_reason = next((source_gap_ids[alias] for alias in source_aliases
                           if alias in source_gap_ids), "")
        status = "illustrée" if is_illustrated else "en attente"
        rows.append({**row, "id": animal_id, "family": family, "tracking_status": status,
                     "source_gap": is_source_gap, "needs_review": is_review,
                     "source_gap_reason": gap_reason, "review_reason": review_reason})

    families = defaultdict(list)
    for row in rows:
        families[row["family"]].append(row)

    lines = [
        "# Suivi des illustrations de mammifères",
        "",
        f"Dernière mise à jour : {date.today().isoformat()}.",
        f"Familles complètes : {sum(all(row['tracking_status'] == 'illustrée' for row in group) for group in families.values())}/{len(families)}.",
        "",
        f"Inventaire source : `app/build/bestiary_expansion/mammals_inventory.json` ({len(rows)} espèces).",
        "Les images générées, acceptées en revue ou présentes dans une file ne sont pas comptées comme intégrées : seule une correspondance scientifique exacte dans un catalogue natif, avec son asset présent dans `app/src/main/assets/bestiary`, vaut « illustrée ». « En attente » est le nombre total de taxons non illustrés ; les colonnes source_gap et à revoir en détaillent des sous-ensembles. Les gaps et différés sont lus dans les prompts et fichiers `reviews.json`, pas déduits des tentatives d’image.",
        "",
        "| Famille | Total | Illustrées (natif) | En attente | Source gap | À revoir |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    total_counts = Counter()
    for family in sorted(families, key=str.casefold):
        family_rows = families[family]
        counts = Counter(row["tracking_status"] for row in family_rows)
        source_gap_count = sum(row["source_gap"] for row in family_rows)
        review_count = sum(row["needs_review"] for row in family_rows)
        total_counts.update(counts)
        total_counts["source_gap"] += source_gap_count
        total_counts["à revoir"] += review_count
        lines.append(f"| {family} | {len(family_rows)} | {counts['illustrée']} | {counts['en attente']} | {source_gap_count} | {review_count} |")
    lines.extend([
        f"| **Total** | **{len(rows)}** | **{total_counts['illustrée']}** | **{total_counts['en attente']}** | **{total_counts['source_gap']}** | **{total_counts['à revoir']}** |",
        "",
        "## Espèces actuellement en manque de source",
        "",
    ])
    gap_rows = [row for row in rows if row["source_gap"]]
    if gap_rows:
        lines.extend(f"- **{row['scientific']}** — {row['en']} ({row['family']}) : {row['source_gap_reason']}" for row in gap_rows)
    else:
        lines.append(f"Aucun des {len(rows)} taxons de l’inventaire n’est actuellement en source_gap.")
    inventory_ids = {row["id"] for row in rows}
    outside_gaps = [(animal_id, reason) for animal_id, reason in source_gap_ids.items()
                    if animal_id not in inventory_ids and not any(
                        re.sub(r"[^a-z0-9]+", "_", row["scientific"].casefold()).strip("_") == animal_id
                        for row in rows)]
    if outside_gaps:
        lines.extend(["", f"Ces espèces mises de côté sont hors des {len(rows)} lignes de l’inventaire actuel et ne sont donc pas incluses dans les compteurs par famille. Leur historique documentaire est conservé :", ""])
        lines.extend(f"- `{animal_id}` : {reason}" for animal_id, reason in outside_gaps)
    lines.extend(["", "## Espèces à revoir", ""])
    review_rows = [row for row in rows if row["needs_review"]]
    if review_rows:
        lines.extend(f"- **{row['scientific']}** — {row['en']} : {row['review_reason']}" for row in review_rows)
    else:
        lines.append("Aucune image native différée en revue actuellement.")
    lines.append("")
    markdown = "\n".join(lines)
    if args.stdout:
        print(markdown, end="")
    else:
        destination = args.documents / "Bestiaire" / "MAMMIFERES_SUIVI.md"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(markdown, encoding="utf-8")
        print(f"Mis à jour : {destination}")


if __name__ == "__main__":
    main()
