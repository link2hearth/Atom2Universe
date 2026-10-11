"""Audit bilingual sentence allocations before publishing a researched animal profile.

This checks accounting and completeness, not whether a source proves a claim.
Scientific review must still check the actual publications.
"""
import argparse
import json
import re
from pathlib import Path


def sentences(text):
    # A taxon initial such as M. martes does not end a sentence.
    return re.split(r"(?<![A-Z]\.)(?<=[.!?])\s+", text.strip())


def references(claims):
    result = []
    for claim in claims:
        if isinstance(claim, dict):
            for language in ("en", "fr"):
                result.extend(f"{claim['section']}.{language}.{n}"
                              for n in claim[f"{language}_sentence_ids"])
            continue
        direct = re.fullmatch(r"([a-z]+)\.(en|fr)\.(\d+)", claim)
        if direct:
            result.append(claim)
            continue
        matches = list(re.finditer(r"([a-z]+)\.(\d+)(?:-(\d+))?", claim))
        if not matches:
            raise ValueError(f"Unrecognized sentence reference: {claim}")
        for match in matches:
            for n in range(int(match[2]), int(match[3] or match[2]) + 1):
                result.extend(f"{match[1]}.{language}.{n}" for language in ("en", "fr"))
    return result


def audit(profile):
    expected = {f"{section}.{language}.{n}": sentence
                for section, values in profile["sections"].items()
                for language in ("en", "fr")
                for n, sentence in enumerate(sentences(values[language]), 1)}
    seen = set()
    errors = []
    totals = []
    for source in profile["sources"]:
        refs = references(source.get("prose_claims", []))
        words = 0
        for ref in refs:
            if ref not in expected:
                errors.append(f"{source['url']}: absent sentence {ref}")
                continue
            if ref in seen:
                errors.append(f"Duplicate allocation: {ref}")
            seen.add(ref)
            words += len(expected[ref].split())
        notes = sum(len(note.split()) for note in source["facts"])
        limit = source.get("budget", {}).get("limit", source.get("limit", 200))
        total = words + notes
        if total > limit:
            errors.append(f"{source['url']}: {words} prose + {notes} notes = {total} > {limit}")
        totals.append((source, refs, dict(limit=limit, derived_prose_en_fr=words,
                                        notes_words=notes, total=total,
                                        method="Complete referenced bilingual sentences and factual notes.")))
    missing = set(expected) - seen
    if missing:
        errors.append("Unallocated sentences: " + ", ".join(sorted(missing)))
    return errors, totals, sum(len(sentence.split()) for sentence in expected.values())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profiles", type=Path, nargs="+")
    parser.add_argument("--normalize", action="store_true",
                        help="Save normalized sentence references and calculated budgets only when the audit passes.")
    args = parser.parse_args()
    failed = False
    for path in args.profiles:
        profile = json.loads(path.read_text(encoding="utf-8"))
        errors, totals, prose = audit(profile)
        if errors:
            failed = True
            print(f"{profile['id']}: source accounting needs correction")
            for error in errors:
                print("  " + error)
            continue
        if args.normalize:
            for source, refs, budget in totals:
                source["prose_claims"] = refs
                source["budget"] = budget
                for field in ("limit", "derived_prose_en_fr", "notes_words", "total"):
                    source.pop(field, None)
            path.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{profile['id']}: {prose} bilingual prose words; {len(totals)} sources; complete allocation within limits")
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
