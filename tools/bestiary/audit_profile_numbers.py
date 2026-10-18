"""Flag differing numeric facts in bilingual drafts before scientific review."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re


def numbers(text):
    # French grouped thousands and decimal commas represent the same values.
    text = re.sub(r"\d{1,3}(?:[ \u00a0\u202f]\d{3})+",
                  lambda m: re.sub(r"\s", "", m[0]), text)
    return Counter(n.replace(",", ".") for n in
                   re.findall(r"\d+(?:[.,]\d+)?", text))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profiles", type=Path, nargs="+")
    args = parser.parse_args()
    mismatch = False
    for path in args.profiles:
        profile = json.loads(path.read_text(encoding="utf-8-sig"))
        for section, value in profile["sections"].items():
            en, fr = numbers(value["en"]), numbers(value["fr"])
            if en != fr:
                mismatch = True
                print(f"{profile['id']}/{section}: EN {dict(en)}; FR {dict(fr)}")
    if not mismatch:
        print("Numeric facts match across EN/FR sections.")
    # This catches digits only; words, units and geographic qualifiers still
    # need human review. Roman numerals may also require a manual explanation.
    raise SystemExit(1 if mismatch else 0)


if __name__ == "__main__":
    main()
