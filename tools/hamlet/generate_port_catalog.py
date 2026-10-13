"""Generate native Kotlin catalogue additions from the extracted, data-only HTML catalogue.

Usage: python tools/hamlet/generate_port_catalog.py app/build/hamlet-source-catalog.json
Rendering is original Kotlin; no JavaScript, source images or HTML ships in the application.
Existing native definitions and resource strings retain their balancing and translations.
"""
import json
import math
import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/Atom2Universe/app/games/hamlet"
RES = ROOT / "app/src/main/res"
source = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))["definitions"]
native = (JAVA / "HamletData.kt").read_text(encoding="utf-8")
existing = set(re.findall(r'(?:home|shop|decor|nature|path|Def)\("([^"$]+)"', native))
aliases = {"castle": "hall", "farm": "field", "magicfarm": "magicfield"}
missing = [d for d in source if d["id"] not in existing and d["id"] not in aliases]
colors = {"red": 0xBA5146, "green": 0x4A8565, "blue": 0x5685AA, "gold": 0xD9B966, "golden": 0xD9B966,
          "purple": 0x8C73A9, "orange": 0xD99152, "ruby": 0xB64F64, "white": 0xECEADE,
          "black": 0x3E4552, "slate": 0x697381, "thatched": 0xD4B578, "rose": 0xD69DAA,
          "yellow": 0xD5C36B, "pink": 0xDFA8C2, "teal": 0x6EAFA2}
coastal = set("lighthouseBlue lighthouseStone lighthouseGrand harbor fishmarket boathouse anchor netrack lobsterpots shipwheel dryboat seashells palmhut parasol sandcastle castlewall brickwall marblewall goldfence bamboofence flowerfence palisade ctowerBlue ctowerRed ctowerGreen ctowerGold ctowerSlate ctowerPurple gatehouse keep bastion drawbridge portcullis ballista wellGrand grandfountain".split())
coastal.update(d["id"] for d in missing if re.match(r"w[A-Z]", d["id"]))
animals = "cow pig sheepd goat horseB horseW horseK deer stag fox rabbit cat hound bear wolf hens geese peacock lamb donkey llama panda lion elephant".split()

def look(d):
    id = d["id"]; cat = d["cat"]
    color = next((value for name, value in {**colors, "yellow": 0xD6B95C, "teal": 0x63A397, "pink": 0xD69DAA}.items() if name in id.lower()), 0x779964)
    if id in coastal: return "coastal", 0xB8B6A7, color, 0xC4A16A, 0
    if id.startswith("an_"): return "animal", 0xBC9871, 0, 0, animals.index(id[3:])
    if cat == "house":
        if id.startswith("hv"):
            i = int(id[2:]); kind = ["h_cot", "h_town", "h_villa"][i % 3]
            material = [0xE4D6B6, 0xADAFA7, 0xC98E73, 0xD9C4A3, 0xDFC5C2, 0xEEEBDD][i % 6]
            roof = colors.get(d["name"].split()[0].lower(), color)
            return kind, material, roof, 0x70523B, 0
        return {"rubyCitadel": "h_citadel", "hobbithole": "h_burrow", "goldmanor": "h_palace"}[id], 0xE8D2B2, color, 0xE6C477, 0
    if cat == "shop":
        kind = "s_shop"; n = 0
        if id in "locksmith bellfoundry shieldmaker mint gemcutter stonemason weaponsmith".split(): kind = "s_smith"
        if id in "dairy honeyshop winecellar eggfarm berryfarm".split(): kind = "s_barn"
        if id in "scribe postoffice soupkitchen bootcamp prison".split(): kind = "s_hall"
        if id == "ridingschool": kind = "s_stable"
        if id == "cornmill": kind = "s_mill"
        if id in ("perfumer", "magicshop"): kind = "s_glass"
        if id == "stargazer": kind = "s_tower"; n = 5
        return kind, 0xE2CFAD, list(colors.values())[sum(map(ord, id)) % len(colors)], 0xD6AC64, n
    if cat == "path":
        palette = {"lake": 0x468FA8, "sea": 0x367F9B, "landbig": 0x77A75C, "lavaflow": 0xCC6742, "icepath": 0xAFDCE2, "sandpatch": 0xD8C690, "mosspath": 0x71915C, "rosepath": 0xBF8E95}
        return "p", palette[id], 0x826F5B, 0, 1 if id == "rosepath" else 0
    if id.startswith("banner") or id.startswith("pennant"):
        if id.startswith("pennant"): color = [0xB84F49, 0x528BB0, 0x54895B, 0xD5B367, 0x8D74A3, 0xEDEADD, 0x454451, 0xD38C4B][int(id[7:])]
        return "banner", color, 0, 0, 0
    if id.startswith("statue"):
        kind = next((n for name, n in [("Dragon", 3), ("Lion", 4), ("Angel", 5), ("Wizard", 6)] if name in id), 0)
        material = next(value for name, value in [("Golden", 0xD8B559), ("Bronze", 0xB48B64), ("Jade", 0x77A895), ("Obsidian", 0x515361), ("RoseMarble", 0xD6A8AB)] if name in id)
        return "d_statue", material, 0, 0, kind
    if id.startswith("crystal"): return "port_nature" if cat == "nature" else "d_crystal", color if id != "crystal" else 0x6EAFCB, 0, 0, 0
    if cat == "nature":
        if "leaf" in id:
            prefix = id.split("_")[0]
            return {"tr": "t_round", "to": "t_oval", "tb": "t_round", "bu": "n_bush"}[prefix], color, 0, 0, int(prefix == "tb")
        if re.match(r"f[lt]\d+", id): return "n_flowers", [0xC65C61, 0x75B8D1, 0xDB945A, 0xEDEADA, 0xD68DB0, 0x9279B0, 0xD6BC53, 0x718ABE][int(id[2:])], 0, 0, int(id[1] == "t")
        if id.startswith("mush"): return "n_mushroom", color, 0, 0, 0
        if id in ("snowpine", "snowoak"): return "t_pine" if id == "snowpine" else "t_round", 0xC9D8D2, 0xFFFFFF, 0, 0
        if id in ("palmPair", "coconut", "bananatree"): return "port_nature", 0x5E9465, 0xD6BD63, 0, 0
        if id in ("olive", "fig", "peach", "cherrytree"): return "t_round", 0x658B60, {"olive": 0x66723F, "fig": 0x93729D, "peach": 0xE9A786, "cherrytree": 0xB84E63}[id], 0, 0
        if id in ("mossrock", "snowrock", "sandstone", "obsidian"): return "n_rock", {"mossrock": 0x83926F, "snowrock": 0xC9D7DB, "sandstone": 0xC5A47F, "obsidian": 0x484654}[id], 0, 0, 0
        return "port_nature", 0x9C9885, 0x77A163, 0, 0
    if id.startswith("stall"): return "d_stall", list(colors.values())[int(id[5:])], 0, 0, int(id[5:]) % 3
    reuse = {"pillar": "d_torch", "arch": "d_arch", "feasttable": "d_picnic", "haywagon": "d_cart", "vegcart": "d_flowercart", "scarecrowKing": "d_scarecrow", "flagpole": "banner", "heartarch": "d_arch", "wishingwell": "d_well", "floatcrystal": "d_crystal", "beekeeper": "d_beehive"}
    if id in reuse: return reuse[id], color, 0xDBB562, 0, 0
    return "port_decor", color, 0xBB9060, 0xE7D6AA, 0

# Individually curated French names; procedural families below retain meaningful materials/colors.
fr = dict(pair.split("=", 1) for pair in """
rubyCitadel=Citadelle de rubis
prison=Prison
pillar=Colonne de feu
arch=Arche de roses
crystal=Cristal magique
hobbithole=Terrier de la colline
goldmanor=Palais doré
lake=Lac · pinceau 3×3
sea=Mer · pinceau 5×5
landbig=Île · pinceau 3×3
lavaflow=Coulée de lave
icepath=Glace
sandpatch=Sable de plage
mosspath=Mousse
rosepath=Pavage rose
greengrocer=Marchand de légumes
fishmonger=Poissonnier
dairy=Laiterie
honeyshop=Miellerie
winecellar=Cave à vin
locksmith=Serrurier
lanternmaker=Fabricant de lanternes
bellfoundry=Fonderie de cloches
shieldmaker=Fabricant de boucliers
giftshop=Boutique de cadeaux
gemcutter=Tailleur de gemmes
stonemason=Tailleur de pierre
scribe=Scribe
fletcher=Fabricant de flèches
mint=Monnaie royale
postoffice=Bureau de poste
pumpkinstand=Étal de citrouilles
juicebar=Bar à jus
eggfarm=Ferme aux œufs
soupkitchen=Cuisine solidaire
ridingschool=École d’équitation
perfumer=Parfumeur
stargazer=Astrologue
berryfarm=Ferme aux baies
cornmill=Moulin à maïs
weaponsmith=Armurier
magicshop=Boutique de magie
bootcamp=École des écuyers
snowpine=Pin enneigé
snowoak=Chêne enneigé
palmPair=Palmiers jumeaux
coconut=Cocotier
bananatree=Bananier
olive=Olivier
fig=Figuier
peach=Pêcher
cherrytree=Cerisier
mossrock=Rocher moussu
snowrock=Rocher enneigé
sandstone=Grès
obsidian=Obsidienne
crystalYellow=Cristal de topaze
crystalPink=Cristal rose
crystalWhite=Cristal de diamant
crystalBlack=Cristal d’onyx
crystalRed=Cristal de rubis
crystalGreen=Cristal d’émeraude
crystalPurple=Cristal d’améthyste
mushBlue=Champignons bleus
mushGold=Champignons dorés
mushPurple=Champignons violets
cliff=Falaise
hill=Colline verdoyante
volcano=Petit volcan
geyser=Geyser
hotspring=Source chaude
cave=Entrée de grotte
mesa=Mesa
feasttable=Table de banquet
cake=Gâteau royal
roastspit=Broche à rôtir
kegstand=Tonnelet sur support
wagon=Chariot bâché
haywagon=Charrette de foin
sleigh=Traîneau
wheel=Roue de chariot
spellbook=Grimoire sur pupitre
potionshelf=Étagère à potions
dragonegg=Nid d’œuf de dragon
floatcrystal=Cristal flottant
fairylights=Guirlande féerique
wishingwell=Puits à souhaits
igloo=Igloo
icesculpture=Sculpture de glace
candycane=Cannes en sucre
giftpile=Pile de cadeaux
ghost=Gentil fantôme
coffin=Cercueil mystérieux
spiderweb=Toile d’araignée
eastereggs=Œufs peints
heartarch=Arche en cœur
fireworks=Feux d’artifice
bell=Cloche du village
gallows=Panneau d’annonces
telescope=Télescope
globe=Grand globe
hourglass=Sablier
harp=Harpe dorée
drum=Tambours
chessset=Échiquier de jardin
maze=Labyrinthe de haies
swing=Balançoire dans l’arbre
seesaw=Bascule
sandbox=Bac à sable
kite=Cerf-volant
scarecrowKing=Épouvantail royal
windchime=Carillon à vent
birdhouse=Nichoir
weathervane=Girouette
flagpole=Mât royal
redcarpetroll=Tapis d’apparat
pillarMarble=Colonne de marbre
pillarBroken=Colonne brisée
urn=Urne de jardin
stocksGold=Présentoir à trophées
anvil=Enclume
grindstone=Meule à aiguiser
loom=Métier à tisser
spinningwheel=Rouet
butterchurn=Baratte
beekeeper=Rucher
vegcart=Charrette à légumes
milkcans=Bidons de lait
ladder=Échelle
toolrack=Râtelier à outils
""".strip().splitlines())
coastal_fr = """wRowboat=Barque
wCanoe=Canoë
wRaft=Radeau de rondins
wSailRed=Voilier rouge
wSailBlue=Voilier bleu
wSailWhite=Sloop blanc
wFishing=Bateau de pêche
wGondola=Gondole
wSwanBoat=Pédalo cygne
wLongship=Drakkar
wPirate=Navire pirate
wGalleon=Galion royal
wMerchant=Nef marchande
wSteamer=Bateau à aubes
wBarge=Péniche de fret
wWreck=Épave
wLighthouse=Phare en mer
wBeacon=Balise du port
wBuoy=Bouée
wLily=Nénuphars
wLotus=Fleurs de lotus
wCattail=Massettes
wStones=Pierres de gué
wRock=Rochers marins
wDucks=Famille de canards
wSwans=Cygnes
wKoi=Carpes koï
wJet=Jet d’eau
wSerpent=Serpent de mer
wWhale=Baleine
wMermaid=Rocher de sirène
wDock=Pieux de ponton
wPier=Ponton de pêche
wIceberg=Iceberg
wTreasure=Trésor englouti
wWaterfallMill=Roue à eau
wShark=Aileron de requin
wDolphin=Dauphins
wTurtle=Tortue marine
wHouseboat=Maison flottante
wIsleHut=Cabane sur pilotis
lighthouseBlue=Phare bleu
lighthouseStone=Vieux phare de pierre
lighthouseGrand=Grand phare
harbor=Capitainerie
fishmarket=Marché aux poissons
boathouse=Hangar à bateaux
anchor=Ancre
netrack=Filets de pêche
lobsterpots=Casiers à homards
shipwheel=Roue de gouvernail
dryboat=Bateau échoué
seashells=Coquillages
palmhut=Cabane de plage
parasol=Parasol
sandcastle=Château de sable
castlewall=Rempart
brickwall=Mur de briques
marblewall=Balustrade de marbre
goldfence=Clôture dorée
bamboofence=Clôture en bambou
flowerfence=Clôture fleurie
palisade=Palissade
ctowerBlue=Tour de rempart bleue
ctowerRed=Tour de rempart rouge
ctowerGreen=Tour de rempart verte
ctowerGold=Tour de rempart dorée
ctowerSlate=Tour de rempart ardoise
ctowerPurple=Tour de rempart violette
gatehouse=Châtelet
keep=Grand donjon
bastion=Bastion rond
drawbridge=Pont-levis
portcullis=Arche à herse
ballista=Baliste
wellGrand=Grand puits
grandfountain=Grande fontaine"""
fr.update(pair.split("=", 1) for pair in coastal_fr.splitlines())
for id, label in zip(animals, "Vache|Cochon|Mouton|Chèvre|Cheval brun|Cheval blanc|Étalon noir|Biche|Cerf|Renard|Lapin|Chat|Chien|Ours|Loup|Poule et poussins|Oies|Paon|Agneau|Âne|Lama|Panda|Lion|Éléphant".split("|")): fr["an_" + id] = label
color_fr = {"Red": "rouge", "Blue": "bleue", "Green": "verte", "Gold": "dorée", "Purple": "violette", "White": "blanche", "Black": "noire", "Orange": "orange"}
for suffix, label in color_fr.items(): fr["banner" + suffix] = "Bannière " + label
flower_fr = "Coquelicots pourpres|Myosotis célestes|Dahlias orangés|Fleurs de lune|Pivoines|Iris|Boutons-d’or|Lupins bleus".split("|")
for i, label in enumerate(flower_fr): fr[f"fl{i}"] = label; fr[f"ft{i}"] = label + " · variété haute"
for i, material in enumerate("terre cuite|bleue|blanche|verte|rose|dorée".split("|")): fr[f"pot{i}"] = "Jardinière " + ("en " if i == 0 else "") + material
for i, label in enumerate("tissus|pains|fleurs|poteries|fromages|jouets".split("|")): fr[f"stall{i}"] = "Étal de " + label
for i, label in enumerate("rouges|jaunes|bleues|vertes|roses".split("|")): fr[f"plant{i}"] = "Lanternes de papier " + label
for i, label in enumerate("rouge|bleu|vert|doré|violet|blanc|noir|orange".split("|")): fr[f"pennant{i}"] = "Fanion " + label
for color, label in {"Red": "pourpre", "Yellow": "doré", "White": "aux fleurs blanches", "Purple": "jacaranda", "Blue": "azur", "Teal": "jade"}.items():
    for prefix, name in {"tr": "Arbre", "to": "Peuplier", "tb": "Grand arbre", "bu": "Buisson"}.items(): fr[f"{prefix}_leaf{color}"] = f"{name} {label}"
for statue, name in {"statue": "chevalier", "statueDragon": "dragon", "statueLion": "lion", "statueAngel": "ange", "statueWizard": "mage"}.items():
    for material, label in {"Golden": "or", "Bronze": "bronze", "Jade": "jade", "Obsidian": "obsidienne", "RoseMarble": "marbre rose"}.items(): fr[statue + material] = f"Statue de {name} en {label}"
for d in missing:
    if d["id"].startswith("hv"):
        i = int(d["id"][2:]); shape = ["Chaumière", "Maison de ville", "Villa"][i % 3]
        material = ["enduite", "en pierre", "en briques", "à colombages", "aux murs roses", "chaulée"][i % 6]
        roof = {"Red": "rouge", "Green": "vert", "Orange": "orange", "Thatched": "de chaume", "Ruby": "rubis", "Blue": "bleu", "Purple": "violet", "Slate": "d’ardoise", "Golden": "doré"}[d["name"].split()[0]]
        fr[d["id"]] = f"{shape} {material} · toit {roof}"
products = dict(pair.split("=", 1) for pair in """Royal Justice=Justice royale
Guiding Light=Lumière de guidage
Cargo=Cargaison
Fresh Fish=Poisson frais
Boat Hire=Location de bateau
Vegetables=Légumes
Fresh Catch=Pêche du jour
Butter=Beurre
Honey Pots=Pots de miel
Vintage Wine=Vin de garde
Keys=Clés
Lanterns=Lanternes
Bells=Cloches
Shields=Boucliers
Presents=Cadeaux
Cut Gems=Gemmes taillées
Cut Stone=Pierres taillées
Scrolls=Parchemins
Arrows=Flèches
Coins=Pièces
Parcels=Colis
Pumpkins=Citrouilles
Fresh Juice=Jus frais
Eggs=Œufs
Hot Soup=Soupe chaude
Lessons=Leçons
Perfume=Parfum
Horoscopes=Horoscopes
Berry Jam=Confiture de baies
Cornmeal=Farine de maïs
Axes=Haches
Wands=Baguettes
Training=Entraînement""".splitlines())

def kotlin_string(value): return json.dumps(value, ensure_ascii=False)
def definition(d):
    id = d["id"]; category = {"house": "HOME", "shop": "SHOP", "decor": "DECOR", "nature": "NATURE", "path": "PATH"}[d["cat"]]
    kind, a, b, c, n = look(d)
    args = [kotlin_string(id), f"Cat.{category}", str(d["w"]), str(d["d"]), str(d.get("cost", 0)), str(d["lvl"]), str(d.get("xp", 1)), f'Look("{kind}", 0x{a:06X}, 0x{b:06X}, 0x{c:06X}, {n})']
    for sourcekey, nativekey in [("mojo", "gems"), ("pop", "pop")]:
        if d.get(sourcekey): args.append(f"{nativekey} = {d[sourcekey]}")
    if category == "HOME":
        period = int(min(7200, d.get("period", 300)))
        tax = max(1, round(d.get("tax", 10) * period / d.get("period", 300)))
        args += [f"tax = {tax}", f"period = {period}", f"taxXp = {max(1, round(d.get('txp', 1) * period / d.get('period', 300)))}"]
    if category == "SHOP":
        job = int(min(7200, d.get("job", 300))); scale = job / d.get("job", 300)
        args += [f"job = {job}", f"pay = {max(1, round(d.get('pay', 20) * scale))}", f"payXp = {max(1, round(d.get('pxp', 1) * scale))}", f"glyph = {kotlin_string(d.get('icon', 'crate'))}"]
    if d.get("build"): args.append(f"build = {min(7200, d['build'])}")
    for sourcekey, nativekey in [("magic", "magic"), ("drag", "drag"), ("link", "link"), ("water", "onWater"), ("shore", "shore")]:
        if d.get(sourcekey): args.append(f"{nativekey} = true")
    if category in ("DECOR", "NATURE") and not d.get("link"):
        args.append(f"charm = {max(1, round(math.log2(d.get('cost', 0) / 60 + d.get('mojo', 0) + 1)))}")
    return "Def(" + ", ".join(args) + ")"

lines = ["package com.Atom2Universe.app.games.hamlet", "", "/** Native data port. Original art is selected by family, palette and variant.", " * Long source timers are capped at two hours; yields scale to retain the same hourly rate.", " * Existing native definitions are kept, and no legacy soil code is reordered. */", "object HamletPortCatalog {", '    val aliases = mapOf("castle" to "hall", "farm" to "field", "magicfarm" to "magicfield")', "    val sourceIds: Set<String> = setOf("]
for start in range(0, len(source), 12): lines.append("        " + ", ".join(kotlin_string(d["id"]) for d in source[start:start + 12]) + ",")
lines += ["    )", "    fun appendTo(target: MutableList<Def>) {", "        val present = target.mapTo(HashSet()) { it.id }"]
for i in range((len(missing) + 39) // 40): lines.append(f"        for (definition in batch{i}()) if (present.add(definition.id)) target.add(definition)")
lines += ["    }", ""]
for i, start in enumerate(range(0, len(missing), 40)):
    lines += [f"    private fun batch{i}() = listOf("]
    lines += ["        " + definition(d) + "," for d in missing[start:start + 40]]
    lines += ["    )", ""]
lines += ["}", ""]
(JAVA / "HamletPortCatalog.kt").write_text("\n".join(lines), encoding="utf-8")
for lang in ("values", "values-fr"):
    resources = RES / lang
    used = set()
    for path in resources.glob("strings_hamlet*.xml"):
        if path.name != "strings_hamlet_port.xml": used.update(re.findall(r'<string name="([^"]+)"', path.read_text(encoding="utf-8")))
    entries = {}
    for d in missing:
        key = "hamlet_n_" + d["id"]
        if key not in used: entries[key] = d["name"] if lang == "values" else fr[d["id"]]
        if d["cat"] == "shop":
            key = "hamlet_n_p_" + d["id"]
            if key not in used: entries[key] = d["product"] if lang == "values" else products[d["product"]]
    def xml(value): return escape(value).replace("'", "\\'").replace('"', '\\"')
    (resources / "strings_hamlet_port.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(f'    <string name="{key}">{xml(value)}</string>' for key, value in entries.items()) + "\n</resources>\n", encoding="utf-8")
print(json.dumps({"source": len(source), "native_additions": len(missing), "by_category": {cat: sum(d["cat"] == cat for d in missing) for cat in ("house", "shop", "decor", "nature", "path")}, "custom_decor": [d["id"] for d in missing if look(d)[0] == "port_decor"]}, ensure_ascii=False))
