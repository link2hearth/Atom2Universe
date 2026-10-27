"""Print the ImageGen prompts for Pocket Gobbler, six objects per image, in the catalogue's slot order.

Usage:
    python tools/glutton/prompts.py --list           # every image name (room_00, room_01, ... suburb_00, ...)
    python tools/glutton/prompts.py room_03          # the prompt for one image (slots 18..23 of the room theme)
    python tools/glutton/prompts.py --check          # verify that every catalogue id has a description

Each theme of GluttonCatalog.kt lists its objects in slot order; slots are cut into images of six
(3 columns by 2 rows). tools/glutton/pack_art.py then segments every image and packs 24 objects per
WebP page (assets/glutton/<theme>_<page>.webp), so the game never depends on where ImageGen put things.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "app/src/main/java/com/Atom2Universe/app/games/glutton/GluttonCatalog.kt"
PER_IMAGE = 6

STYLE = ("Style: charming hand-drawn COLOURED PENCIL illustration, like a children's picture book sketched on paper: "
         "visible pencil strokes and light hatching inside the shapes, slightly wobbly soft graphite outlines, matte paper "
         "texture inside the drawing only, gentle warm colours, a small cast shadow drawn with pencil hatching under each "
         "object. Seen from slightly above, three-quarter view. Absolutely no 3D render, no glossy highlights, no airbrush, "
         "no plastic look, no photoreal, no vector clipart, no pixel art.")

RULES = ("Layout: a 3:2 landscape image on a genuinely transparent background (real alpha, no checkerboard, no glow, "
         "no coloured haze, no vignette, nothing at all around the objects). Exactly 6 objects arranged in 3 columns and "
         "2 rows, each one alone in the centre of its own invisible square, filling about 55 percent of that square, with "
         "wide empty margins between objects so that nothing touches or overlaps anything else. Every object is drawn at "
         "the same visual size whatever its real size. No frames, no grid lines, no text, no letters, no numbers, no labels, "
         "no watermark, no UI. Production sprite sheet only, not a mockup.")

# One short visual description per catalogue id (filled from the catalogue table, see Documents NON commit du projet/Glouton).
DESCRIPTIONS = {
    "sesame_seed": "cream teardrop sesame seed with a matte skin",
    "peppercorn": "wrinkled black peppercorn with a dusty matte surface",
    "sugar_sprinkle": "pink sugar sprinkle rod with a glossy coating",
    "lentil": "flat orange lentil with a smooth glossy rim",
    "rice_grain": "white oblong rice grain with a pearly matte surface",
    "popcorn_kernel": "ochre popcorn kernel with a hard glossy shell",
    "coffee_bean": "dark roasted coffee bean with an oily brown sheen",
    "thumbtack": "red plastic thumbtack with a steel pin",
    "staple": "bent silver steel staple with square shoulders",
    "glass_bead": "turquoise glass bead with a round threading hole",
    "raisin": "wrinkled dark purple raisin with a sugary sheen",
    "sunflower_seed": "striped grey and white sunflower seed with a pointed tip",
    "jelly_bean": "glossy coral jelly bean with a soft sugar shell",
    "almond": "brown almond with a rough matte skin",
    "marble": "clear glass marble with a blue and yellow swirl",
    "coin": "shiny gold coin with a ridged edge",
    "cherry": "glossy red cherry with a green stem",
    "bottle_cap": "crimped blue metal bottle cap with a white star",
    "lego_brick": "bright yellow plastic building brick with eight studs",
    "paperclip": "bent silver steel paperclip with rounded loops",
    "walnut": "wrinkled light brown walnut in its hard shell",
    "strawberry": "red strawberry with yellow seeds and green leaves",
    "golf_ball": "white dimpled golf ball with a blue logo",
    "eraser": "pink rubber eraser with soft worn corners",
    "matchbox": "cardboard matchbox with a red and blue label",
    "door_key": "brass door key with a round bow and worn teeth",
    "egg": "speckled cream egg with a smooth matte shell",
    "tennis_ball": "fuzzy neon yellow tennis ball with white seams",
    "lipstick": "gold lipstick tube with the coral stick exposed",
    "cookie": "golden chocolate chip cookie with cracked edges",
    "apple": "red apple with a brown stem and one leaf",
    "soap_bar": "lavender soap bar with rounded edges and bubbles",
    "mug": "mint ceramic mug with a white handle",
    "toy_car": "red tin toy car with a white racing stripe",
    "alarm_clock": "round turquoise alarm clock with two brass bells",
    "smartphone": "black glass smartphone with a lavender case",
    "potted_cactus": "spiky green cactus in a terracotta pot",
    "tv_remote": "grey plastic TV remote with coloured rubber buttons",
    "banana": "ripe yellow banana with brown freckles",
    "toy_robot": "boxy silver tin toy robot with red dial lights",
    "hairbrush": "oval wooden hairbrush with cream bristles",
    "soccer_ball": "classic black and white leather football with hexagons",
    "hardcover_book": "hardcover book with a red cloth cover and gold title",
    "toy_train": "green wooden toy train engine with red wheels",
    "kettle": "cream enamel kettle with a curved black handle",
    "slipper": "fluffy coral slipper with a soft beige sole",
    "toaster": "retro mint chrome toaster with two slots",
    "teddy_bear": "worn honey brown plush teddy bear with a red bow",
    "drone": "white plastic quadcopter drone with four grey propellers",
    "laptop": "slim silver aluminium laptop with its lid open",
    "robot_vacuum": "round black robot vacuum with a glossy top",
    "rc_car": "blue remote control buggy with chunky rubber tyres",
    "wastepaper_basket": "woven wicker wastepaper basket with crumpled paper",
    "globe": "blue and ochre desk globe on a brass stand",
    "wooden_stool": "three legged pale oak stool with a round seat",
    "microwave": "white microwave with a dark glass door",
    "desk_lamp": "yellow metal desk lamp with a bendy arm",
    "nightstand": "walnut nightstand with a single drawer and brass knob",
    "fire_extinguisher": "red steel fire extinguisher with a black hose",
    "computer_monitor": "black flat computer monitor on a slim stand",
    "houseplant": "leafy green houseplant in a cream ceramic pot",
    "bed_pillow": "plump white cotton pillow with a soft dent",
    "suitcase": "hard turquoise suitcase with four spinner wheels",
    "floor_fan": "white pedestal fan with a round wire cage",
    "beanbag": "squashy ochre corduroy beanbag with a dented top",
    "dining_chair": "red painted wooden dining chair with a straw seat",
    "armchair": "plush mustard velvet armchair with wooden legs",
    "flat_screen_tv": "black flat screen television on two feet",
    "chest_of_drawers": "mint painted chest of drawers with brass knobs",
    "upright_vacuum": "purple upright vacuum cleaner with a grey hose",
    "exercise_bike": "black and red stationary exercise bike with a screen",
    "desk": "pale birch writing desk with two drawers",
    "upright_piano": "glossy black upright piano with ivory keys",
    "floor_lamp": "brass floor lamp with a cream linen shade",
    "bathtub": "white enamel clawfoot bathtub with brass taps",
    "fridge": "retro coral fridge with a chrome handle",
    "bookshelf": "oak bookshelf packed with colourful books",
    "dining_table": "walnut dining table with turned legs and a glossy top",
    "christmas_tree": "green christmas tree with red baubles and gold tinsel",
    "double_bed": "double bed with a lavender duvet and wooden headboard",
    "grandfather_clock": "mahogany grandfather clock with a brass face and pendulum",
    "sofa": "deep teal velvet sofa with three cushions",
    "wardrobe": "cream painted wardrobe with two mirrored doors",
    "kitchen_island": "navy kitchen island with a white marble top",
    "beach_ball": "inflatable beach ball with red yellow and blue panels",
    "watering_can": "green galvanised watering can with a brass rose",
    "terracotta_planter": "terracotta pot overflowing with pink geraniums",
    "hose_reel": "coiled green garden hose on a grey plastic reel",
    "garden_gnome": "painted ceramic garden gnome with a red hat",
    "robot_mower": "grey robot lawn mower with orange trim",
    "cooler_box": "blue and white plastic cooler box with a handle",
    "car_tyre": "black rubber car tyre with a worn tread",
    "tricycle": "red metal tricycle with white rubber tyres",
    "traffic_cone": "orange plastic traffic cone with a white reflective band",
    "fire_hydrant": "cast iron fire hydrant painted yellow with silver caps",
    "bird_bath": "grey stone bird bath with a shallow bowl",
    "skateboard": "maple skateboard with a purple grip and orange wheels",
    "plastic_chair": "white stackable plastic garden chair with a slatted back",
    "kick_scooter": "folding aluminium kick scooter with pink handles",
    "pool_ring": "inflatable swim ring with turquoise and white stripes",
    "rain_barrel": "green plastic rain barrel with a brass tap",
    "doghouse": "wooden doghouse with a red roof and arched door",
    "wheelie_bin": "dark green wheelie bin with a flip lid",
    "kettle_barbecue": "black kettle barbecue on three steel legs",
    "mailbox": "red metal mailbox on a white wooden post",
    "hay_bale": "round golden hay bale wrapped in twine",
    "wheelbarrow": "rusty red wheelbarrow with a wooden handle",
    "push_mower": "green petrol lawn mower with a black grass bag",
    "sunflower": "sunflower with yellow petals and a dark brown heart",
    "kiddie_pool": "inflatable blue paddling pool with printed shells",
    "bicycle": "turquoise bicycle with a wicker basket",
    "garden_bench": "weathered teak garden bench with a slatted back",
    "dumpster": "rusty blue steel dumpster with two lids",
    "picnic_table": "pine picnic table with attached benches",
    "vending_machine": "red vending machine with a glowing glass front",
    "garden_statue": "cream stone statue of a vase on a fluted pedestal",
    "playhouse": "mint wooden playhouse with a pink door",
    "motorcycle": "black and chrome motorcycle with a red tank",
    "hot_tub": "round cedar hot tub with steaming blue water",
    "camping_tent": "orange dome tent with grey poles",
    "golf_cart": "white golf cart with a cream canopy",
    "parasol": "striped red and white parasol on a wooden pole",
    "garden_fountain": "three tier sandstone fountain with trickling water",
    "garden_shed": "grey wooden garden shed with a felt roof",
    "basketball_hoop": "portable basketball hoop with an orange rim and black base",
    "newspaper_kiosk": "dark green newspaper kiosk with a striped awning",
    "gazebo": "white wooden gazebo with a cream canvas roof",
    "trampoline": "round trampoline with a blue safety net",
    "hatchback": "yellow hatchback car with black roof rails",
    "bus_shelter": "glass bus shelter with a red steel frame",
    "apple_tree": "round apple tree with red fruit and grey bark",
    "street_lamp": "black cast iron street lamp with a frosted glass lantern",
    "bouncy_castle": "inflatable bouncy castle in red yellow and blue",
    "delivery_van": "white delivery van with a blue stripe",
    "tractor": "green tractor with yellow wheels and a black cab",
    "pickup_truck": "dusty red pickup truck with a chrome grille",
    "caravan": "cream caravan with a mint stripe and round windows",
    "treehouse": "wooden treehouse perched in a leafy tree with a rope ladder",
    "double_garage": "brick double garage with two white roller doors",
    "greenhouse": "glass greenhouse with a white frame and green plants",
    "motorhome": "white motorhome with brown swooshes and a roof hatch",
    "minibus": "lavender minibus with tinted windows and a roof rack",
    "backyard_pool": "turquoise backyard swimming pool with a stone edge",
    "birch_tree": "birch tree with white bark and light green leaves",
    "box_truck": "orange box truck with a white cargo body",
    "cottage": "stone cottage with a thatched roof and blue door",
    "bungalow": "cream bungalow with a terracotta tiled roof",
    "utility_pole": "weathered wooden utility pole with sagging black cables",
    "pine_tree": "dark green pine tree with a reddish trunk",
    "school_bus": "yellow school bus with black stripes",
    "house": "two storey family house with cream walls and a red roof",
    "shipping_container": "rusty blue corrugated steel shipping container",
    "fuel_station": "fuel station with a red canopy and two pumps",
    "grain_silo": "silver corrugated grain silo with a conical roof",
    "oak_tree": "broad old oak tree with a knotted brown trunk",
    "red_barn": "red wooden barn with white trim and a hayloft door",
    "police_car": "white and blue police car with a flashing light bar",
    "market_stall": "wooden market stall with a striped green awning and crates",
    "ambulance": "white ambulance with red stripes and blue lights",
    "food_truck": "coral food truck with a hatch and a chalkboard menu",
    "bandstand": "octagonal cast iron bandstand with a green copper roof",
    "plaza_fountain": "granite plaza fountain with bronze spouts and clear water",
    "obelisk": "pale granite obelisk on a stepped stone base",
    "bakery": "bakery with a cream facade and an ochre awning",
    "fire_truck": "red fire truck with a silver ladder",
    "cafe": "corner café with a dark green facade and brass lettering",
    "corner_shop": "corner shop with a blue facade and fruit crates outside",
    "city_bus": "blue and white city bus with panoramic windows",
    "billboard": "roadside billboard with a bright yellow advert on steel legs",
    "townhouse": "red brick townhouse with white window frames and steps",
    "mobile_crane": "yellow mobile crane truck with its boom folded",
    "chapel": "white stone chapel with a slate steeple",
    "tanker_truck": "silver tanker truck with a blue cab",
    "metro_car": "silver metro car with red doors",
    "tram": "sleek green and cream tram with rounded windows",
    "water_tower": "rusty steel water tower on four steel legs",
    "freight_locomotive": "heavy orange diesel locomotive with black trim",
    "clock_tower": "sandstone clock tower with a blue clock face",
    "fire_station": "red brick fire station with three bay doors",
    "hot_air_balloon": "striped red and cream hot air balloon with a wicker basket",
    "lighthouse": "white lighthouse with red bands and a glass lantern",
    "apartment_block": "beige apartment block with rows of balconies",
    "theatre": "ornate theatre with a cream facade and a red marquee",
    "cinema": "art deco cinema with a neon sign and glass doors",
    "school": "red brick school with arched windows and a flagpole",
    "ferris_wheel": "white ferris wheel with coloured gondolas",
    "church": "grey stone church with a pointed bell tower",
    "town_hall": "stately town hall with columns and a green dome",
    "parking_garage": "concrete multi storey parking garage with open ramps",
    "museum": "classical museum with white columns and sweeping steps",
    "gasometer": "rusty steel gasometer with riveted panels",
    "tower_crane": "yellow tower crane with a lattice jib and counterweight",
    "hotel": "grand cream hotel with a navy awning and gold sign",
    "factory": "brick factory with a smoking chimney and sawtooth roof",
    "hospital": "white hospital building with a red cross sign",
    "wind_turbine": "white wind turbine with three sleek blades",
    "cathedral": "gothic cathedral with twin spires and stained glass",
    "radio_telescope": "white radio telescope dish tilted toward the sky",
    "ferry": "white and blue passenger ferry with a yellow funnel",
    "office_tower": "grey concrete office tower with rows of windows",
    "aircraft_hangar": "curved steel aircraft hangar with sliding doors",
    "arena": "round indoor arena with a silver dome roof",
    "opera_house": "ornate opera house with gilded ornaments and a green roof",
    "cooling_tower": "grey concrete cooling tower with a steam plume",
    "glass_tower": "sleek turquoise glass tower with a tapered top",
    "university_hall": "sandstone university hall with a central clock",
    "high_speed_train": "white high speed train with a red nose",
    "power_plant": "sprawling power plant with grey halls and three chimneys",
    "parliament": "grand parliament building with a copper dome and columns",
    "airship": "silver airship with a red gondola and blue fins",
    "skyscraper": "steel and glass skyscraper with a pointed spire",
    "shopping_mall": "sprawling shopping mall with a glass atrium and coral walls",
    "stadium": "oval football stadium with white roof arches",
    "cruise_ship": "white cruise ship with blue funnels and many decks",
    "tv_tower": "concrete tv tower with a red observation sphere",
    "airport_terminal": "glass airport terminal with a wavy white roof",
}


def themes():
    """Returns {theme_name: [ids in slot order]} parsed from the Kotlin catalogue (the code-drawn theme is skipped)."""
    text = CATALOG.read_text(encoding="utf-8")
    result, current = {}, None
    for line in text.splitlines():
        header = re.match(r"\s*theme\((Theme\.(\w+)|null)\)", line)
        if header:
            current = header.group(2).lower() if header.group(2) else None
            if current:
                result[current] = []
            continue
        entry = re.match(r'\s*p\("([a-z_0-9]+)"', line)
        if entry and current:
            result[current].append(entry.group(1))
    return result


def images():
    """Returns {image_name: [ids]} with six ids per image."""
    result = {}
    for theme, ids in themes().items():
        for batch in range(-(-len(ids) // PER_IMAGE)):
            result[f"{theme}_{batch:02d}"] = ids[batch * PER_IMAGE:(batch + 1) * PER_IMAGE]
    return result


def prompt(name, ids):
    lines = [f"Use case: stylized-concept. Asset type: production sprite sheet for an Android game (Pocket Gobbler, image {name}).",
             RULES, STYLE, "Subjects, in reading order (top row left to right, then bottom row):"]
    for index, i in enumerate(ids):
        lines.append(f"{index + 1}. {DESCRIPTIONS[i]}.")
    return "\n".join(lines)


def main():
    args = sys.argv[1:]
    catalogue = images()
    missing = sorted({i for ids in catalogue.values() for i in ids} - set(DESCRIPTIONS))
    if missing:
        raise SystemExit("Missing descriptions: " + ", ".join(missing))
    if "--check" in args:
        print(f"{sum(len(v) for v in catalogue.values())} ids across {len(catalogue)} images, all described.")
        return
    if "--list" in args:
        print("\n".join(catalogue))
        return
    wanted = [a for a in args if not a.startswith("--")]
    for name, ids in catalogue.items():
        if wanted and name not in wanted:
            continue
        if not wanted:
            print(f"===== {name} =====")
        print(prompt(name, ids))


if __name__ == "__main__":
    main()
