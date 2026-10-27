"""Print the ImageGen prompts for every Pocket Gobbler sprite sheet, in the catalogue's cell order.

Usage:
    python tools/glutton/prompts.py                 # all sheets, ready to paste into Codex ImageGen
    python tools/glutton/prompts.py room_a          # one sheet
    python tools/glutton/prompts.py --markdown OUT  # write everything to a Markdown file
    python tools/glutton/prompts.py --check         # only verify that every catalogue id has a description

The cell order comes from GluttonCatalog.kt (one `p("id", ...)` line per cell, 6 columns, row-major),
so the sheet generated from a prompt lines up with the game without any extra mapping.
Afterwards: python tools/glutton/pack_art.py GENERATED.png room_a
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "app/src/main/java/com/Atom2Universe/app/games/glutton/GluttonCatalog.kt"
COLUMNS = 6

STYLE = ("Style: premium hand-painted mobile game props, soft dimensional shading, warm playful colours "
         "(mint, coral, gold, lavender accents), crisp readable silhouettes at tiny sizes, gentle top-down "
         "three-quarter view as if seen from slightly above, consistent soft upper-left lighting, tiny soft "
         "contact shadow directly under each object only. Not pixel art, not clipart, not photoreal.")

RULES = ("Layout: exactly {count} isolated objects in a perfectly regular {cols} columns by {rows} rows grid on a "
         "genuinely transparent background (real alpha, no checkerboard). Square cells of identical size; each "
         "object centred in its own cell, filling about 70 percent of the cell, never touching cell borders, "
         "no overlap between cells. No frames, no text, no letters, no numbers, no labels, no watermark, no UI. "
         "Production sprite sheet only, not a mockup.")

# One short visual description per catalogue id. Same id in two scenes shares one description.
DESCRIPTIONS = {
    "crumb": "golden bread crumb", "cereal_loop": "honey-coloured ring of breakfast cereal", "pea": "bright green pea",
    "raisin": "wrinkled dark raisin", "button": "pastel blue shirt button with four holes", "coin": "shiny gold coin",
    "candy": "wrapped pink candy with twisted ends", "marble": "glass marble with a turquoise swirl",
    "lego_brick": "red interlocking toy brick with studs", "dice": "white die with black pips",
    "bottle_cap": "red metal bottle cap", "cherry": "pair of red cherries on a stem", "cookie": "chocolate chip cookie",
    "strawberry": "ripe strawberry with leaves", "macaron": "pastel pink macaron", "key": "small brass house key",
    "toy_soldier": "little blue toy soldier figurine", "spoon": "silver teaspoon", "pencil": "yellow pencil with pink eraser",
    "toy_car": "red toy racing car", "rubber_duck": "yellow rubber duck", "mug": "white mug with a mint stripe",
    "donut": "pink glazed donut with sprinkles", "sock": "striped lavender sock",
    "apple": "red apple with a leaf", "phone": "smartphone with a pastel case", "cupcake": "cupcake with swirled coral frosting",
    "remote": "grey TV remote with colourful buttons", "tennis_ball": "fuzzy yellow tennis ball", "book": "closed hardcover book, blue with gold title band",
    "teddy": "cuddly brown teddy bear sitting", "slipper": "fluffy pink slipper", "flower_pot": "terracotta pot with a leafy green plant",
    "teapot": "round turquoise teapot", "football": "classic black and white football", "backpack": "mint school backpack",
    "pillow": "plump lavender cushion", "toy_robot": "chunky retro toy robot, silver and coral", "laptop": "open laptop with a glowing screen",
    "toaster": "chrome toaster with two slices of toast", "kettle": "coral electric kettle", "fishbowl": "round fishbowl with a goldfish",
    "mouse": "tiny grey mouse with pink ears", "cat": "orange tabby cat, standing, surprised", "robot_vacuum": "round grey robot vacuum cleaner with a green light",
    "chair": "wooden dining chair with a blue cushion", "stool": "round wooden stool", "lamp": "floor lamp with a warm yellow shade",
    "tv": "flat-screen television on a stand", "nightstand": "small wooden nightstand with a drawer", "side_table": "low round coffee table",
    "dog_bed": "plush oval dog bed", "bookshelf": "wooden bookshelf full of colourful books", "armchair": "cosy pink armchair",
    "dining_table": "oval wooden dining table with a vase", "sofa": "pink three-seat sofa with cushions", "fridge": "tall mint retro fridge with magnets",
    "golden_spoon": "ornate golden spoon, glowing", "golden_egg": "glowing golden egg", "golden_trophy": "glowing golden trophy cup",
    "daisy": "white daisy with a yellow centre", "mushroom": "red mushroom with white spots", "pine_cone": "brown pine cone",
    "watering_can": "green metal watering can", "bucket": "blue plastic bucket", "garden_gnome": "garden gnome with a red hat",
    "hen": "plump white hen", "rabbit": "brown rabbit, alert", "pigeon": "grey pigeon with a green neck",
    "hedgehog": "small round hedgehog", "pumpkin": "orange pumpkin", "lantern": "garden lantern with a warm flame",
    "mailbox": "red mailbox on a post", "skateboard": "colourful skateboard", "tricycle": "red child tricycle",
    "bird_bath": "stone bird bath with water", "wheelbarrow": "coral wheelbarrow full of soil", "scooter": "mint kick scooter",
    "dog": "friendly brown dog, running", "hose_reel": "green garden hose coiled on a reel", "flower_bed": "round flower bed bursting with colourful flowers",
    "barbecue": "round kettle barbecue with smoke", "lawn_mower": "green push lawn mower", "bench": "wooden garden bench",
    "bike": "turquoise bicycle with a basket", "parasol": "coral and cream striped parasol", "picnic_table": "wooden picnic table with a checkered cloth",
    "swing": "wooden swing set with two seats", "doghouse": "little red doghouse", "sandbox": "square sandbox with a bucket and spade",
    "statue": "pale stone statue on a pedestal", "goat": "white goat with small horns", "scarecrow": "straw scarecrow with a hat",
    "trampoline": "round garden trampoline with a safety net", "fountain": "stone fountain with splashing water", "hot_tub": "round wooden hot tub, steaming",
    "kayak": "yellow kayak with a paddle", "slide": "playground slide, coral and mint", "playhouse": "children's wooden playhouse",
    "shed": "wooden garden shed", "tractor": "green farm tractor", "car": "small coral hatchback car", "pony": "brown pony with a blond mane",
    "apple_tree": "round apple tree full of red apples", "pine": "tall pine tree", "gazebo": "white garden gazebo", "van": "mint camper van",
    "caravan": "cream and lavender caravan", "hay_bale": "round hay bale", "beehive": "white wooden beehive with bees",
    "well": "stone wishing well with a little roof", "boat": "small wooden rowing boat", "pool": "round blue swimming pool with a floating ring",
    "treehouse": "treehouse in a big leafy tree", "oak": "huge old oak tree", "windmill": "wooden windmill with four sails",
    "greenhouse": "glass greenhouse glowing green inside", "golden_gnome": "glowing golden garden gnome", "golden_beehive": "glowing golden beehive",
    "golden_fountain": "glowing golden fountain", "phone_booth": "red telephone booth", "hot_dog_cart": "hot dog cart with a striped awning",
    "pigeon_flock": "small flock of pigeons", "bus_stop": "bus stop shelter with a bench", "taxi": "yellow taxi cab", "police_car": "white and blue police car with lights",
    "kiosk": "newspaper kiosk with a striped awning", "delivery_van": "white delivery van", "food_truck": "colourful food truck",
    "billboard": "large billboard on two legs with an abstract poster", "truck": "red cargo truck", "fire_truck": "red fire truck with a ladder",
    "cottage": "small cottage with a thatched roof", "bus": "yellow city bus", "house": "two-storey family house with a red roof",
    "cafe": "corner café with a terrace", "hot_air_balloon": "striped hot-air balloon with a basket", "bakery": "bakery shop with a pretzel sign",
    "tram": "red tram", "carousel": "fairground carousel with horses", "supermarket": "wide supermarket with a big sign",
    "mobile_crane": "yellow mobile crane", "water_tower": "water tower on steel legs", "townhouse": "narrow colourful townhouse",
    "apartment": "apartment block with many windows", "lighthouse": "red and white striped lighthouse", "school": "brick school with a clock and a flag",
    "cinema": "cinema with a lit marquee", "fire_station": "fire station with red doors", "church": "stone church with a steeple",
    "parking_garage": "multi-storey parking garage", "train": "red passenger train", "ferris_wheel": "big ferris wheel with colourful cabins",
    "museum": "museum with columns and a dome", "hospital": "hospital with a red cross", "factory": "factory with chimneys",
    "mall": "shopping mall with a glass roof", "stadium": "oval stadium with floodlights", "skyscraper": "tall glass skyscraper",
    "cathedral": "gothic cathedral with twin towers", "town_hall": "grand town hall with a clock tower", "golden_statue": "glowing golden statue",
    "golden_bell": "glowing golden bell", "golden_crown": "glowing golden crown",
}


def sheets():
    """Returns {sheet_name: [ids in cell order]} parsed from the Kotlin catalogue."""
    text = CATALOG.read_text(encoding="utf-8")
    result = {}
    current = None
    for line in text.splitlines():
        header = re.match(r"\s*sheet\(Sheet\.(\w+)\)", line)
        if header:
            current = header.group(1).lower()
            result[current] = []
            continue
        entry = re.match(r'\s*p\("([a-z_]+)"', line)
        if entry and current:
            result[current].append(entry.group(1))
    return result


def prompt(name, ids):
    rows = -(-len(ids) // COLUMNS)
    lines = [f"Use case: stylized-concept. Asset type: production sprite sheet for an Android game (Pocket Gobbler, sheet {name}).",
             RULES.format(count=len(ids), cols=COLUMNS, rows=rows), STYLE,
             "Subjects in exact row-major order:"]
    for row in range(rows):
        chunk = ids[row * COLUMNS:(row + 1) * COLUMNS]
        lines.append(f"row {row + 1}: " + "; ".join(DESCRIPTIONS[i] for i in chunk) + ".")
    lines.append("All objects fully visible and separated, roughly equal visual weight; the larger real-world "
                 "objects are drawn at the same cell size as the small ones (scale is applied in the game).")
    return "\n".join(lines)


def main():
    args = sys.argv[1:]
    catalogue = sheets()
    missing = sorted({i for ids in catalogue.values() for i in ids} - set(DESCRIPTIONS))
    if missing:
        raise SystemExit("Missing descriptions: " + ", ".join(missing))
    if "--check" in args:
        print(f"{sum(len(v) for v in catalogue.values())} ids across {len(catalogue)} sheets, all described.")
        return
    wanted = [a for a in args if not a.startswith("--")]
    if "--markdown" in args:
        out = Path(args[args.index("--markdown") + 1])
        blocks = ["# Glouton de poche : prompts des planches\n",
                  "Généré par `python tools/glutton/prompts.py --markdown`. Un prompt par planche, à coller dans ImageGen (Codex). ",
                  "Puis `python tools/glutton/pack_art.py IMAGE.png <planche>` range la planche convertie en WebP dans `assets/glutton/`.\n"]
        for name, ids in catalogue.items():
            blocks.append(f"\n## {name} ({len(ids)} cases, {COLUMNS} × {-(-len(ids) // COLUMNS)})\n\n```\n{prompt(name, ids)}\n```\n")
        out.write_text("".join(blocks), encoding="utf-8")
        print(f"Wrote {out}")
        return
    for name, ids in catalogue.items():
        if wanted and name not in wanted:
            continue
        print(f"===== {name} ({len(ids)} cells) =====\n{prompt(name, ids)}\n")


if __name__ == "__main__":
    main()
