"""Refresh the external bestiary progress ledger without claiming pending work complete."""
import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/parentes"))
from build_runtime import decode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--documents", type=Path, default=ROOT.parent / "Documents NON commit du projet")
    args = parser.parse_args()
    docs = args.documents
    destination = docs / "Bestiaire"
    destination.mkdir(parents=True, exist_ok=True)
    published_source = (ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/BestiaryEncyclopediaCatalog.kt").read_text(encoding="utf-8")
    published_ids = set(re.findall(r'id = "([a-z_]+)"', published_source))
    reviewed = [record for p in (ROOT / "tools/bestiary/data/profiles").glob("*.json")
                for record in [json.loads(p.read_text(encoding="utf-8"))] if record["id"] in published_ids]
    extended = {r["scientific"]: r for r in reviewed}
    original = dict(re.findall(r'AnimalSpecies\("([a-z_]+)", "([^"]+)"',
                              (ROOT / "app/src/main/java/com/Atom2Universe/app/science/bestiary/AnimalCatalog.kt").read_text(encoding="utf-8")))
    original_scientific = {scientific: animal_id for animal_id, scientific in original.items()}
    inventory_path = ROOT / "app/build/bestiary_expansion/inventory.json"
    if not inventory_path.exists():
        inventory_path = destination / "INVENTAIRE_EXTENSION.json"
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    _, nodes, _, _ = decode((ROOT / "app/src/main/assets/parentes/runtime.bin").read_bytes())
    by_id = {n[0]: n for n in nodes}
    pending = {}
    for path in (ROOT / "app/build/bestiary_expansion").glob("*.json"):
        value = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(value, dict) and "scientific" in value and "sections" in value:
            pending[value["scientific"]] = value["id"]
    for animal in inventory:
        ancestors = []
        node = animal["node"]
        while node:
            ancestors.append(by_id[node][2])
            node = by_id[node][1]
        # Sauropsida includes birds: check Aves first. Non-tetrapod vertebrates
        # include jawless fishes, which must not become invertebrates.
        animal["group"] = next((group for clade, group in (
            ("Mammalia", "mammals"), ("Aves", "birds"), ("Amphibia", "amphibians"),
            ("Sauropsida", "reptiles"), ("Vertebrata", "fish")) if clade in ancestors), "invertebrates")
        scientific = animal["scientific"]
        if scientific in extended:
            animal.update(status="integrated_encyclopedia", id=extended[scientific]["id"])
        elif scientific in pending:
            animal.update(status="research_and_review", id=pending[scientific])
        elif scientific in original_scientific:
            animal.update(status="integrated_brief", id=original_scientific[scientific])
        else:
            animal.update(status="queued")
    (destination / "INVENTAIRE_EXTENSION.json").write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (destination / "ARTICLES_ENCYCLOPEDIQUES.json").write_text(json.dumps(reviewed, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    priority = ROOT / "app/build/bestiary_expansion/priority.json"
    if priority.exists():
        (destination / "FILE_PRIORITAIRE.json").write_text(priority.read_text(encoding="utf-8"), encoding="utf-8")
    depth_file = destination / "APPROFONDISSEMENT_EDITORIAL.json"
    earlier_depth_ids = ({r["id"] for r in json.loads(depth_file.read_text(encoding="utf-8"))}
                         if depth_file.exists() else
                         {r["id"] for r in reviewed
                          if sum(len(s["en"].split()) for s in r["sections"].values()) < 500})
    depth_queue = [dict(id=r["id"], scientific=r["scientific"],
                        status="further_explanatory_enrichment",
                        reason="Earlier concise article: review biological explanations where sources permit.")
                   for r in reviewed
                   if r["id"] in earlier_depth_ids or r.get("needs_depth_review")]
    (destination / "APPROFONDISSEMENT_EDITORIAL.json").write_text(
        json.dumps(depth_queue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    attempts = []
    manifests = sorted((ROOT / "app/build/bestiary_expansion").glob("*manifest*.json"),
                       key=lambda p: [int(part) if part.isdigit() else part
                                      for part in re.split(r"(\d+)", p.name)])
    for manifest in manifests:
        value = json.loads(manifest.read_text(encoding="utf-8"))
        if isinstance(value, list):
            attempts.extend(r for r in value if isinstance(r, dict) and "prompt" in r and "path" in r)
    # A batch manifest and its individual record can describe the same PNG.
    # Count that generation once, retaining the latest review metadata.
    attempts = list({(r["id"], r["path"]): r for r in attempts}.values())
    final_art = {}
    for attempt in attempts:
        if (attempt["id"] in {r["id"] for r in reviewed}
                and attempt.get("status", "accepted") == "accepted"):
            final_art[attempt["id"]] = attempt
    for animal_id, record in final_art.items():
        file = ROOT / f"app/src/main/assets/bestiary/{animal_id}.webp"
        record.update(asset=str(file), sha256=hashlib.sha256(file.read_bytes()).hexdigest(), bytes=file.stat().st_size)
    (destination / "ILLUSTRATIONS_EXTENSION.json").write_text(json.dumps(
        {"tool": "built-in ImageGen", "attempts": attempts, "final": list(final_art.values())}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = Counter(a["status"] for a in inventory)
    catalog_ids = set(original) | {r["id"] for r in reviewed}
    total_bytes = sum((ROOT / f"app/src/main/assets/bestiary/{animal_id}.webp").stat().st_size for animal_id in catalog_ids)
    lines = ["## Extension encyclopédique — en cours", "",
             f"Inventaire : {len(inventory)} espèces animales. Catalogue : {len(catalog_ids)} fiches illustrées, dont {len(reviewed)} articles encyclopédiques enrichis. "
             f"{counts['integrated_brief']} notices initiales restent à enrichir ; {counts['research_and_review']} recherches sont en cours de contrôle et {counts['queued']} espèces restent en file.", "",
             "Objectif : couvrir autant que possible l’arbre et enrichir les notices, en reportant les espèces mal documentées ou les illustrations non validées avec un motif précis. Une espèce non examinée reste en file, sans report fictif.", "",
             "Huit rubriques bilingues : portrait, identification, répartition/habitat, alimentation, comportement, reproduction/développement, rôle écologique, conservation/relations humaines. Sources biologiques universitaires, institutionnelles et spécialistes croisées ; les évaluations portent une date et une portée géographique. Les observations régionales ne deviennent pas des règles universelles.", "",
             "Exigence éditoriale précisée : articles explicatifs de niveau encyclopédique, avec mécanismes des adaptations, organisation sociale, étapes du développement et relations écologiques, sans quota artificiel de mots ni remplissage. Parcours des nouvelles fiches : Wikipédia en anglais, comparaison avec la page française et vérification auprès d’une ou deux sources institutionnelles ou spécialisées. Les premiers articles concis restent à approfondir lorsque les sources le permettent. Les budgets des sources s’appliquent aux deux langues cumulées.", "",
             f"{len(depth_queue)} articles antérieurs concis sont conservés dans APPROFONDISSEMENT_EDITORIAL.json pour une seconde passe sur la profondeur des explications ; la longueur seule ne décide pas de leur qualité.", "",
             "Agents gpt-6-luna avec contexte réduit pour le tri et les articles. Contrôle de cohérence par l’agent principal et critique indépendante des illustrations. Les données JSON revues régénèrent le catalogue Kotlin et les ressources EN/FR sans nouvelle dépense IA. ImageGen intégré conserve le style naturaliste validé, fond blanc. PNG originaux conservés, WebP hors ligne optimisés.", "",
             f"Contrôle supplémentaire des nouvelles rédactions : chaque phrase est attribuée à une source et les budgets cumulent anglais, français et notes factuelles. {sum(r.get('source_audit_version') == 1 for r in reviewed)} articles disposent actuellement de cet audit reproductible ; les métadonnées des articles antérieurs restent à normaliser. Ce contrôle comptable complète la relecture scientifique, sans la remplacer.", "",
             f"Poids des illustrations embarquées : {total_bytes:,} octets pour {len(catalog_ids)} animaux.", "",
             "| Article enrichi | Identité dans l’arbre | Mots EN / FR | Sources | Image WebP |",
             "|---|---|---:|---|---:|"]
    for record in sorted(reviewed, key=lambda r: r["name"]["fr"]):
        words = [sum(len(v[lang].split()) for v in record["sections"].values()) for lang in ("en", "fr")]
        links = "; ".join(f"[{s['title']['fr']}]({s['url']})" for s in record["sources"])
        size = (ROOT / f"app/src/main/assets/bestiary/{record['id']}.webp").stat().st_size
        lines.append(f"| {record['name']['fr']} | {record['scientific']} | {words[0]} / {words[1]} | {links} | {size:,} octets |")
    verification = ROOT / "app/build/bestiary_expansion/verification_state.json"
    if verification.exists():
        state = json.loads(verification.read_text(encoding="utf-8"))
        (destination / "VERIFICATIONS_EXTENSION.json").write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        lines += ["", f"Dernier lot vérifié automatiquement : {state['encyclopedia_articles']} articles enrichis, {state['illustrated_profiles']} fiches illustrées. "
                  f"Compilation Kotlin et {state['tests_passed']} tests ciblés réussis. Journal : {state['log']}. Le contenu ajouté ou modifié ensuite attend la vérification du lot suivant."]
        if (state.get("content_pending_reverification")
                or state["encyclopedia_articles"] != len(reviewed)
                or state["illustrated_profiles"] != len(catalog_ids)):
            lines += ["", "Le contenu actuel comporte des ajouts ou des révisions postérieurs à ce contrôle ; même un remplacement de fiche à effectif constant reste en attente de vérification."]
    lines += ["", "La validation de compilation et les résultats des tests ciblés sont conservés séparément ; cette table décrit les articles intégrés et ne présume pas du résultat d’un contrôle encore en cours.", "", ""]
    progress = docs / "BESTIAIRE_SUIVI.md"
    text = progress.read_text(encoding="utf-8")
    section = "\n".join(lines)
    if "## Extension encyclopédique — en cours" in text:
        text = re.sub(r"## Extension encyclopédique — en cours\n.*?(?=## Résultat du premier lot)", lambda _: section, text, flags=re.S)
    else:
        text = text.replace("## Résultat\n", section + "## Résultat du premier lot\n", 1)
    progress.write_text(text, encoding="utf-8")
    print(f"Tracking: {len(catalog_ids)} illustrated profiles; {len(reviewed)} encyclopedia articles; {len(inventory)} animals in inventory")


if __name__ == "__main__":
    main()
