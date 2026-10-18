"""Régénère app/src/main/assets/nuclides/nuclides.json depuis l'IAEA LiveChart of Nuclides.

Garde les mêmes nucléides (Z, N) que le fichier actuel ; ceux absents de la base IAEA sont retirés.
Usage, depuis la racine du dépôt :
    curl -o gs.csv "https://nds.iaea.org/relnsd/v1/data?fields=ground_states&nuclides=all"
    python3 tools/nuclides/generate_nuclides.py gs.csv
"""
import csv, json, collections, os, sys
SRC = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "nuclides", "nuclides.json")
old = json.load(open(SRC, encoding="utf-8"))
rows = list(csv.DictReader(open(sys.argv[1] if len(sys.argv) > 1 else "gs.csv", encoding="utf-8")))
g = {(int(r["z"]), int(r["n"])): r for r in rows}
UNITS = {"Y": "a", "d": "d", "h": "h", "m": "min", "s": "s", "ms": "ms", "us": "μs", "ns": "ns", "ps": "ps", "as": "as"}
OPS = {"": None, "GT": ">", "LT": "<", "GE": "≥", "AP": "~"}
MODES = {"A": "α", "B-": "β-", "2B-": "2β-", "B+": "β+", "2B+": "2β+", "EC": "EC", "2EC": "2EC", "EC+B+": "β+/EC",
         "SF": "SF", "IT": "IT", "P": "p", "2P": "2p", "N": "n", "2N": "2n", "B-N": "β-n", "B-2N": "β-2n", "B-3N": "β-3n",
         "B-4N": "β-4n", "B-P": "β-p", "B-A": "β-α", "B-SF": "β-SF", "ECP": "ECp", "EC2P": "EC2p", "ECA": "ECα", "ECSF": "ECSF",
         "B+P": "β+p", "B+2P": "β+2p", "B+A": "β+α", "SF+EC+B+": "SF/β+/EC", "14C": "¹⁴C", "24NE": "²⁴Ne", "Mg": "Mg", "{+22}Ne": "²²Ne", "{+24}Ne": "²⁴Ne", "{+25}Ne": "²⁵Ne", "{+34}Si": "³⁴Si"}
out, dropped = [], []
for n in old["nuclides"]:
    r = g.get((n["Z"], n["N"]))
    if r is None:
        dropped.append(f'{n["symbol"]}-{n["A"]}'); continue
    stable = r["half_life"] == "STABLE"
    hl = unit = op = None
    if not stable and r["half_life"]:
        if r["unit_hl"] in UNITS:
            hl, unit = r["half_life"], UNITS[r["unit_hl"]]
        else:  # largeur en énergie (keV, MeV, eV) : durée en secondes, 3 chiffres significatifs
            hl, unit = "%.3g" % float(r["half_life_sec"]), "s"
        op = OPS[r["operator_hl"]]
    modes = []
    for i in (1, 2, 3):
        m = r[f"decay_{i}"]
        if m:
            if m not in MODES: raise SystemExit(f"mode inconnu {m} pour {n['symbol']}-{n['A']}")
            modes.append({"mode": MODES[m], "percent": r[f"decay_{i}_%"] or None})
    be = round(float(r["binding"]) / 1000, 4) if r["binding"] else 0.0
    out.append({
        "Z": n["Z"], "N": n["N"], "A": n["Z"] + n["N"], "symbol": n["symbol"], "stable": stable,
        "halfLife": hl, "halfLifeUnit": unit, "halfLifeOperator": op,
        "decayModes": modes, "spin": r["jp"] or None, "bindingEnergyPerNucleon": be,
    })
doc = {"source": "IAEA LiveChart of Nuclides, ground states (nds.iaea.org/relnsd/v1), extracted "
       + rows[0]["Extraction_date"], "nuclides": out}
with open(SRC, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=1); f.write("\n")
print(len(out), "kept; dropped:", dropped)
print(collections.Counter(m["mode"] for x in out for m in x["decayModes"]))
print(collections.Counter(x["halfLifeUnit"] for x in out), collections.Counter(x["halfLifeOperator"] for x in out))
