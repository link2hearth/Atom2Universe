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
    # The app lists encyclopedic profiles only: no initial notice nor illustration-only entry remains.
    illustrated_ids = set()
    reviewed = [record for p in (ROOT / "tools/bestiary/data/profiles").glob("*.json")
                for record in [json.loads(p.read_text(encoding="utf-8"))] if record["id"] in published_ids]
    extended = {r["scientific"]: r for r in reviewed}
    original = {}
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
    illustrated_manifest = json.loads(
        (ROOT / "tools/bestiary/illustrated_species.json").read_text(encoding="utf-8"))
    illustrated_by_scientific = {r["scientific_name"]: r for r in illustrated_manifest["species"]}
    unresolved_lineages = 0
    for animal in inventory:
        ancestors = []
        node = animal["node"]
        while node:
            taxon = by_id.get(node)
            if taxon is None:
                # The external inventory can outlive a taxonomy-tree refresh.
                # Preserve its last reviewed group when an OTT node was retired.
                unresolved_lineages += 1
                break
            ancestors.append(taxon[2])
            node = taxon[1]
        # Sauropsida includes birds: check Aves first. Non-tetrapod vertebrates
        # include jawless fishes, which must not become invertebrates.
        derived_group = next((group for clade, group in (
            ("Mammalia", "mammals"), ("Aves", "birds"), ("Amphibia", "amphibians"),
            ("Sauropsida", "reptiles"), ("Vertebrata", "fish")) if clade in ancestors), None)
        if derived_group is not None:
            animal["group"] = derived_group
        elif not animal.get("group"):
            animal["group"] = "invertebrates"
        scientific = animal["scientific"]
        if scientific in extended:
            animal.update(status="integrated_encyclopedia", id=extended[scientific]["id"])
        elif scientific in original_scientific:
            animal.update(status="integrated_brief", id=original_scientific[scientific])
        elif scientific in illustrated_by_scientific and illustrated_by_scientific[scientific]["id"] in illustrated_ids:
            illustrated = illustrated_by_scientific[scientific]
            animal.update(status="illustrations_only", id=illustrated["id"])
        elif scientific in pending:
            animal.update(status="research_and_review", id=pending[scientific])
        else:
            animal.update(status="queued")
        animal["article_in_preparation"] = scientific in pending and scientific not in extended
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
            for row in value:
                if not isinstance(row, dict) or not row.get("id"):
                    continue
                # Keep a stable ledger shape across the original manifests and
                # the newer image_manifest_N batches.
                attempts.append({
                    "id": row["id"], "scientific": row.get("scientific", ""),
                    "group": row.get("group", ""), "prompt": row.get("prompt", ""),
                    "path": row.get("path", ""), "attempts": row.get("attempts", 1),
                    "status": row.get("status", "accepted"), "bytes": row.get("bytes"),
                    **{k: v for k, v in row.items() if k not in {
                        "id", "scientific", "group", "prompt", "path", "attempts", "status", "bytes"}},
                })
    # A batch manifest and its individual record can describe the same PNG.
    # Count that generation once, retaining the latest review metadata.
    attempts_by_key = {}
    for record in attempts:
        key = (record["id"], record["path"])
        attempts_by_key[key] = record
    attempts = list(attempts_by_key.values())
    final_art = {}
    for attempt in attempts:
        if (attempt["id"] in published_ids | illustrated_ids
                and attempt.get("status", "accepted") == "accepted"):
            final_art[attempt["id"]] = attempt
    for animal_id, record in final_art.items():
        file = ROOT / f"app/src/main/assets/science/bestiary/{animal_id}.webp"
        record.update(asset=str(file), sha256=hashlib.sha256(file.read_bytes()).hexdigest(), bytes=file.stat().st_size)
    (destination / "ILLUSTRATIONS_EXTENSION.json").write_text(json.dumps(
        {"tool": "built-in ImageGen", "attempts": attempts, "final": list(final_art.values())}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = Counter(a["status"] for a in inventory)
    # Native catalogue is a union by identity: brief entries, enriched
    # encyclopedia entries, and identity-only illustrated entries.  An animal
    # present in more than one source counts once by ID and scientific name.
    catalog_by_id = {}
    for animal_id, scientific in [
            *original.items(),
            *((r["id"], r["scientific"]) for r in reviewed),
            *((r["id"], r["scientific_name"]) for r in illustrated_manifest["species"] if r["id"] in illustrated_ids)]:
        prior = catalog_by_id.setdefault(animal_id, scientific)
        if prior != scientific:
            raise ValueError(f"Conflicting scientific identity for catalogue ID {animal_id}: {prior} / {scientific}")
    catalog_ids = set(catalog_by_id)
    catalog_scientific = set(catalog_by_id.values())
    if len(catalog_by_id) != len(catalog_ids) or len(catalog_scientific) != len(catalog_by_id):
        raise ValueError("Duplicate animal identity in native bestiary catalogue sources")
    write_illustration_wave_tracking(
        destination, ROOT, original_scientific, extended, pending,
        published_ids, illustrated_ids, catalog_ids)
    total_bytes = sum((ROOT / f"app/src/main/assets/science/bestiary/{animal_id}.webp").stat().st_size for animal_id in catalog_ids)
    lines = ["## Extension encyclopédique — en cours", "",
             f"Inventaire : {len(inventory)} espèces animales. Catalogue natif : {len(catalog_ids)} identités uniques ({len(original)} notices initiales, {len(reviewed)} articles encyclopédiques enrichis et {len(illustrated_ids)} fiches avec illustration seule, dédoublonnés). "
             f"{counts['integrated_brief']} notices initiales restent à enrichir ; {counts['research_and_review']} recherches sont en cours de contrôle et {counts['queued']} espèces restent en file.", "",
             "Priorité actuelle : produire rapidement des illustrations naturalistes pour presque toutes les espèces faciles à représenter, puis reprendre les articles plus tard. Les textes restent au stade réel (brouillon, recherche ou article intégré) ; aucune description biologique n’est inventée pour les fiches illustrées seules.", "",
             "Huit rubriques bilingues : portrait, identification, répartition/habitat, alimentation, comportement, reproduction/développement, rôle écologique, conservation/relations humaines. Sources biologiques universitaires, institutionnelles et spécialistes croisées ; les évaluations portent une date et une portée géographique. Les observations régionales ne deviennent pas des règles universelles.", "",
             "Exigence éditoriale précisée : articles explicatifs de niveau encyclopédique, avec mécanismes des adaptations, organisation sociale, étapes du développement et relations écologiques, sans quota artificiel de mots ni remplissage. Parcours des nouvelles fiches : Wikipédia en anglais, comparaison avec la page française et vérification auprès d’une ou deux sources institutionnelles ou spécialisées. Les premiers articles concis restent à approfondir lorsque les sources le permettent. Les budgets des sources s’appliquent aux deux langues cumulées.", "",
             f"{len(depth_queue)} articles antérieurs concis sont conservés dans APPROFONDISSEMENT_EDITORIAL.json pour une seconde passe sur la profondeur des explications ; la longueur seule ne décide pas de leur qualité.", "",
             "Agents gpt-6-luna avec contexte réduit pour le tri et les articles. Contrôle de cohérence par l’agent principal et critique indépendante des illustrations. Les données JSON revues régénèrent le catalogue Kotlin et les ressources EN/FR sans nouvelle dépense IA. ImageGen intégré conserve le style naturaliste validé, fond blanc. PNG originaux conservés, WebP hors ligne optimisés.", "",
             f"Contrôle supplémentaire des nouvelles rédactions : chaque phrase est attribuée à une source et les budgets cumulent anglais, français et notes factuelles. {sum(r.get('source_audit_version') == 1 for r in reviewed)} articles disposent actuellement de cet audit reproductible ; les métadonnées des articles antérieurs restent à normaliser. Ce contrôle comptable complète la relecture scientifique, sans la remplacer.", "",
             f"Poids des illustrations embarquées : {total_bytes:,} octets pour {len(catalog_ids)} identités du catalogue natif. Parmi elles, {len(illustrated_ids)} fiches sont actuellement illustrées sans article enrichi.", "",
             "| Article enrichi | Identité dans l’arbre | Mots EN / FR | Sources | Image WebP |",
             "|---|---|---:|---|---:|"]
    for record in sorted(reviewed, key=lambda r: r["name"]["fr"]):
        words = [sum(len(v[lang].split()) for v in record["sections"].values()) for lang in ("en", "fr")]
        links = "; ".join(f"[{s['title']['fr']}]({s['url']})" for s in record["sources"])
        size = (ROOT / f"app/src/main/assets/science/bestiary/{record['id']}.webp").stat().st_size
        lines.append(f"| {record['name']['fr']} | {record['scientific']} | {words[0]} / {words[1]} | {links} | {size:,} octets |")
    verification = ROOT / "app/build/bestiary_expansion/verification_state.json"
    if verification.exists():
        state = json.loads(verification.read_text(encoding="utf-8"))
        (destination / "VERIFICATIONS_EXTENSION.json").write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        lines += ["", f"Précédent contrôle automatique (historique) : {state['encyclopedia_articles']} articles enrichis, {state['illustrated_profiles']} fiches illustrées. "
                  f"Compilation Kotlin et {state['tests_passed']} tests ciblés réussis. Journal : {state['log']}. Le contenu ajouté ou modifié ensuite attend la vérification du lot suivant."]
        if (state.get("content_pending_reverification")
                or state["encyclopedia_articles"] != len(reviewed)
                or state["illustrated_profiles"] != len(catalog_ids)):
            lines += ["", "Le contenu actuel comporte des ajouts ou des révisions postérieurs à ce contrôle ; même un remplacement de fiche à effectif constant reste en attente de vérification."]
    lines += ["", "Les compteurs de textes et d’illustrations sont indépendants : une image peut être intégrée alors que le texte reste en rédaction. La génération d’illustrations passe avant l’approfondissement éditorial. La validation de compilation et les résultats des tests ciblés sont conservés séparément ; ce suivi ne présume pas du résultat d’un contrôle encore en cours.", "", ""]
    progress = docs / "BESTIAIRE_SUIVI.md"
    text = progress.read_text(encoding="utf-8")
    section = "\n".join(lines)
    if "## Extension encyclopédique — en cours" in text:
        text = re.sub(r"## Extension encyclopédique — en cours\n.*?(?=## Résultat du premier lot)", lambda _: section, text, flags=re.S)
    else:
        text = text.replace("## Résultat\n", section + "## Résultat du premier lot\n", 1)
    progress.write_text(text, encoding="utf-8")
    print(f"Tracking: {len(catalog_ids)} illustrated profiles; {len(reviewed)} encyclopedia articles; {len(inventory)} animals in inventory; {unresolved_lineages} inventory entries used their previously reviewed group because a taxonomy node was retired")


def write_illustration_wave_tracking(destination, root, initial_by_scientific, enriched,
                                     pending, enriched_ids, illustrated_ids, native_ids):
    """Write the external ledger for fast illustration waves, independent of article status."""
    build = root / "app/build/bestiary_expansion"
    wave_dirs = sorted(build.glob("illustration_wave_[0-9][0-9][0-9]"))
    attempts_by_id = {}
    manifests = sorted(build.glob("image_manifest*.json"),
                       key=lambda p: [int(part) if part.isdigit() else part
                                      for part in re.split(r"(\d+)", p.name)])
    for manifest in manifests:
        try:
            value = json.loads(manifest.read_text(encoding="utf-8-sig"))
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(value, list):
            for record in value:
                if isinstance(record, dict) and record.get("id"):
                    # Manifests are ordered numerically; later entries correct
                    # earlier attempt metadata for the same identity.
                    attempts_by_id[record["id"]] = record

    def read_json(path, default):
        try:
            return json.loads(path.read_text(encoding="utf-8-sig"))
        except (OSError, json.JSONDecodeError):
            return default

    lines = [
        "# Suivi des vagues d’illustrations prioritaires", "",
        "Ce suivi distingue la production du PNG, son contrôle visuel, la publication de l’asset dans le catalogue natif et l’état du texte. Une illustration peut être publiée pendant que l’article reste en préparation. Les descriptions biologiques ne sont jamais inventées et les brouillons existants sont conservés.", "",
        "Règle de génération : une seule tentative par espèce, sauf nouvelle demande explicite de l’utilisateur. Un PNG produit est consigné même si l’enregistrement `art_<id>.json` manque ; les manifests `image_manifest*.json` servent alors de source de tentative. Un statut absent reste « en cours » ou « à contrôler », sans validation présumée.", "",
    ]
    wave_evidence = []
    for wave in wave_dirs:
        queue = read_json(wave / "queue.json", [])
        if isinstance(queue, dict):
            queue = queue.get("items", [])
        if not isinstance(queue, list) or not queue:
            continue
        review_path = wave / "reviews.json"
        if not review_path.exists():
            review_path = wave / "art_review.json"
        reviews_raw = read_json(review_path, [])
        if isinstance(reviews_raw, dict):
            reviews_raw = reviews_raw.get("items", [])
        reviews = {r.get("id"): r for r in reviews_raw if isinstance(r, dict) and r.get("id")}
        wave_evidence.append({
            "wave": wave.name,
            "queue": queue,
            "reviews": reviews_raw,
            "generated_records": [read_json(wave / f"art_{row['id']}.json", {})
                                  for row in queue if isinstance(row, dict) and row.get("id")],
        })
        generated_count = checked_count = published_count = enriched_count = in_progress_count = 0
        lines.extend([f"## {wave.name.replace('_', ' ').title()}", ""])
        lines.append("| Espèce (nom scientifique) | Génération | Contrôle visuel | Asset publié | Texte |")
        lines.append("|---|---|---|---|---|")
        for row in queue:
            if not isinstance(row, dict) or not row.get("id"):
                continue
            animal_id = row["id"]
            artifact = read_json(wave / f"art_{animal_id}.json", {})
            if not isinstance(artifact, dict):
                artifact = {}
            attempt = attempts_by_id.get(animal_id, {})
            path = artifact.get("path") or attempt.get("path")
            status = artifact.get("status") or attempt.get("status") or row.get("status", "")
            attempt_count = artifact.get("attempts", attempt.get("attempts", row.get("attempts", 1)))
            if path:
                generation = f"PNG produit (tentative {attempt_count})"
                generated_count += 1
            elif status in {"generation_failed", "failed"}:
                generation = "Échec consigné (aucune nouvelle tentative)"
            elif status in {"generation_started", "pending_review", "in_progress"}:
                generation = "En cours"
                in_progress_count += 1
            else:
                generation = "À générer"

            decision = reviews.get(animal_id, {}).get("decision")
            if decision:
                checked_count += 1
                control = {"accepted": "Acceptée", "deferred": "Différée", "rejected": "Refusée"}.get(decision, str(decision))
            else:
                control = "À contrôler" if path else "En attente du PNG"

            asset_file = root / f"app/src/main/assets/science/bestiary/{animal_id}.webp"
            is_native = animal_id in native_ids
            if (is_native and asset_file.is_file() and path and attempt.get("path")
                    and path != attempt["path"]):
                asset_status = "Autre tentative publiée (exception demandée)"
            elif is_native and asset_file.is_file():
                asset_status = "Publié"
                published_count += 1
            elif is_native:
                asset_status = "Catalogue, asset manquant"
            else:
                asset_status = "Pas encore publié"

            scientific = row.get("scientific") or artifact.get("scientific") or attempt.get("scientific") or animal_id
            if scientific in enriched:
                text_status = "Article enrichi intégré"
                enriched_count += 1
            elif scientific in pending:
                text_status = "Texte en préparation"
            elif scientific in initial_by_scientific:
                text_status = "Notice initiale à enrichir"
            else:
                text_status = "Aucun article intégré"
            lines.append(f"| {scientific} | {generation} | {control} | {asset_status} | {text_status} |")

        row_count = sum(1 for row in queue if isinstance(row, dict) and row.get("id"))
        lines.extend(["", f"État de la vague : {generated_count}/{row_count} PNG produits ; {checked_count}/{row_count} illustrations contrôlées ; {published_count}/{row_count} assets publiés dans le catalogue natif ; {enriched_count} articles enrichis intégrés ; {in_progress_count} générations en cours.", ""])

    if not wave_dirs:
        lines.extend(["Aucune vague `illustration_wave_NNN` n’est encore enregistrée.", ""])
    exceptions = [r for r in attempts_by_id.values()
                  if r.get("attempts", 1) > 1 and r.get("status") == "accepted"
                  and r["id"] in native_ids]
    if exceptions:
        lines.extend(["## Nouvelles tentatives explicitement demandées", ""])
        for record in exceptions:
            lines.append(f"- {record.get('scientific', record['id'])} : tentative {record['attempts']} contrôlée et publiée. Autorisation : {record.get('authorization', 'demande explicite consignée dans le manifeste')}.")
        lines.append("")
    lines.extend([
        "## État du catalogue natif", "",
        f"Le catalogue natif contient {len(native_ids)} identités uniques, toutes avec un article enrichi. Les contrôles d’identité du suivi principal rejettent les doublons d’ID ou de nom scientifique.", "",
        f"Textes : {len(enriched)} articles enrichis intégrés. Illustrations seules : {len(illustrated_ids - enriched_ids)} identités dans le catalogue illustré sans article enrichi.", "",
        "Les compteurs de textes et d’illustrations sont indépendants. Les espèces en file ne sont pas déclarées différées sans décision explicite, et une tentative unique n’est jamais relancée automatiquement.", "", "",
    ])
    (destination / "ILLUSTRATIONS_PRIORITAIRES.md").write_text("\n".join(lines), encoding="utf-8")
    (destination / "VAGUES_ILLUSTRATIONS.json").write_text(
        json.dumps(wave_evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
