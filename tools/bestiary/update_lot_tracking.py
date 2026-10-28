"""Refresh external Markdown ledgers for the sequential batches of twenty species."""
import argparse
import json
import re
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--documents", type=Path, default=root.parent / "Documents NON commit du projet")
    args = parser.parse_args()
    build = root / "app/build/bestiary_expansion"
    catalog = root / "app/src/main/java/com/Atom2Universe/app/science/bestiary/BestiaryEncyclopediaCatalog.kt"
    published_ids = set(re.findall(r'id = "([a-z_]+)"', catalog.read_text(encoding="utf-8"))) if catalog.exists() else set()
    destination = args.documents / "Bestiaire"
    destination.mkdir(parents=True, exist_ok=True)
    for lot in sorted(build.glob("lot20_*")):
        plan_file = lot / "plan.json"
        if not plan_file.exists():
            continue
        plan = json.loads(plan_file.read_text(encoding="utf-8-sig"))
        review_file = lot / "art_review.json"
        review = {r["id"]: r for r in json.loads(review_file.read_text(encoding="utf-8-sig"))} if review_file.exists() else {}
        lines = [f"# Bestiaire — {lot.name}", "", "Vingt espèces par lot. Les compteurs de texte et d’image sont indépendants : une illustration peut être intégrée pendant que le texte reste en rédaction. Priorité actuelle aux illustrations rapides des espèces faciles ; articles repris ensuite. Les brouillons sont préservés et aucune description biologique n’est inventée.", "", "| Espèce | Texte EN/FR | Illustration | Image intégrée | Article intégré |", "|---|---|---|---|---|"]
        drafts = reviewed = generated = published = image_integrated = 0
        for row in plan["items"]:
            animal_id = row["id"]
            draft_file = build / f"{animal_id}.json"
            draft = "En rédaction / en file"
            if draft_file.exists():
                value = json.loads(draft_file.read_text(encoding="utf-8-sig"))
                draft = "Relue" if value.get("reviewed") else "Brouillon à relire"
                drafts += 1
                reviewed += bool(value.get("reviewed"))
            image_file = lot / f"art_{animal_id}.json"
            art = "À générer"
            if image_file.exists():
                record = json.loads(image_file.read_text(encoding="utf-8-sig"))
                if record.get("path"):
                    generated += 1
                    art = {"accepted": "Acceptée", "deferred": "À revoir plus tard"}.get(review.get(animal_id, {}).get("decision"), "Générée, à contrôler")
                elif record.get("status") == "generation_failed":
                    art = "Échec, sans nouvelle tentative"
                else:
                    art = "Génération en cours"
            integrated = animal_id in published_ids
            # The app shows an illustration only with its published profile.
            image_is_integrated = integrated
            published += integrated
            image_integrated += image_is_integrated
            lines.append(f"| {row['scientific']} | {draft} | {art} | {'Oui' if image_is_integrated else 'Non'} | {'Oui' if integrated else 'Non'} |")
        lines[2:2] = [f"État : {drafts}/{len(plan['items'])} textes présents, dont {reviewed} relus ; {generated}/{len(plan['items'])} images générées ; {image_integrated}/{len(plan['items'])} images intégrées au catalogue ; {published}/{len(plan['items'])} articles intégrés.", "Les brouillons et images en préparation ne comptent pas comme intégrés. Une fiche peut avoir son image intégrée sans article encyclopédique.", ""]
        deferred_items = plan.get("deferred_items", [])
        if deferred_items:
            lines.extend(["", "## Illustrations différées", "", "Ces espèces ont été remplacées dans le lot ; leurs images originales sont conservées pour une correction ultérieure.", ""])
            for row in deferred_items:
                reason = review.get(row["id"], {}).get("reason", "À revoir plus tard, sans régénération.")
                lines.append(f"- {row['scientific']} : {reason}")
        (destination / (lot.name.upper() + ".md")).write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"{lot.name}: {drafts} drafts, {generated} images, {published} integrated")


if __name__ == "__main__":
    main()
