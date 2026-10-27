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
    # Countryside
    "windmill": "white stone windmill with four wooden sails on a little grassy mound",
    "farmhouse": "stone farmhouse with a red tiled roof and a small walled yard",
    "grain_silos": "cluster of three tall silver grain silos with a conveyor",
    "pond": "round countryside pond with reeds and lily pads on a grassy patch",
    "airliner": "white passenger airliner with blue tail seen from above, wings spread",
    "castle_ruin": "ruined grey stone castle keep with broken walls on a green hillock",
    "farm": "farmyard with a red barn, a farmhouse and a silo around a muddy courtyard",
    "abbey": "old stone abbey with a bell tower and a cloister garden",
    "chateau": "elegant french chateau with slate turrets and a formal garden",
    "cargo_ship": "long cargo ship loaded with colourful containers",
    "campsite": "campsite meadow with colourful tents and a campfire",
    "grove": "dense round grove of leafy green trees seen from above",
    "hamlet": "tiny hamlet of five stone houses around a crossroads",
    "orchard": "square orchard of neat rows of round fruit trees",
    "vineyard": "hillside vineyard with neat green rows of vines",
    "marina": "small marina with white sailing boats moored along wooden jetties",
    "solar_farm": "rectangular solar farm with rows of blue solar panels on grass",
    "dam": "curved concrete dam holding back a blue reservoir",
    "freight_train": "long freight train with a red locomotive and many wagons on a curved track",
    "quarry": "stepped grey stone quarry pit with a little yellow digger",
    "village": "country village with a church spire, red roofs and a green square",
    "viaduct": "tall stone railway viaduct with many arches over a green valley",
    "motorway_interchange": "cloverleaf motorway interchange with looping grey roads",
    "theme_park": "theme park with a big wheel, a roller coaster and colourful tents",
    "lake": "large blue lake with sandy shores and a little island",
    "hill": "rounded green hill with a winding path and a few trees",
    "golf_course": "golf course with green fairways, sand bunkers and a small pond",
    "racetrack": "oval motor racetrack with grandstands and a green infield",
    "oil_refinery": "oil refinery with silver tanks, pipes and tall flare stacks",
    "forest": "big dark green forest of many treetops seen from above",
    "small_town": "small town with red roofs, a river bridge and a church",
    "airport": "airport with two long runways, a terminal and parked planes",
    # Mountains and weather
    "rock_spire": "tall jagged rock spire of orange sandstone",
    "waterfall": "high waterfall tumbling down a mossy cliff into a pool",
    "tornado": "dark grey twisting tornado funnel with swirling dust at its base",
    "meteor_crater": "round meteor crater with a raised rocky rim in a desert",
    "mountain_village": "alpine mountain village of wooden chalets around a church",
    "geyser_basin": "geyser basin with steaming turquoise and orange hot springs",
    "cumulus_cloud": "puffy white cumulus cloud with soft grey shading",
    "ski_resort": "snowy ski resort with chalets, a chairlift and ski slopes",
    "alpine_town": "alpine town by a lake with a castle and snowy roofs",
    "mountain_lake": "turquoise mountain lake surrounded by pine trees and rocks",
    "pine_forest": "dark green pine forest covering a hillside",
    "mesa": "flat topped red rock mesa with steep striped cliffs",
    "rocky_peak": "grey rocky mountain peak with a little snow on top",
    "dam_reservoir": "big hydroelectric dam with a long turquoise reservoir between mountains",
    "glacier": "blue white glacier tongue flowing between grey rocks",
    "thunderstorm": "towering anvil thunderstorm cloud with a yellow lightning bolt",
    "snowy_peak": "majestic snow covered mountain peak with blue shadows",
    "pyramid_peak": "sharp pyramid shaped mountain peak with snowy ridges",
    "volcano": "cone volcano with a glowing orange crater and a smoke plume",
    "salt_lake": "white salt lake with pale blue water and crusted shores",
    "canyon": "deep winding red canyon with a river at the bottom",
    "plateau": "high green and brown plateau with steep cliffs on every side",
    "metropolis": "huge metropolis of skyscrapers spreading over a plain with a river",
    "mountain_range": "long snowy mountain range with several peaks in a row",
    # The Earth seen from orbit
    "atoll": "ring shaped coral atoll with a turquoise lagoon in a deep blue sea",
    "coral_reef": "colourful coral reef in shallow turquoise water",
    "volcanic_island": "green volcanic island with a smoking peak and black beaches",
    "iceberg": "huge flat topped white iceberg with blue cracks in dark water",
    "megacity": "sprawling megacity seen from high above, grey grid of streets and towers",
    "archipelago": "archipelago of many small green islands in a blue sea",
    "sand_dunes": "golden desert sea of curved sand dunes",
    "iceland": "the island of Iceland seen from above, green coasts and white glaciers",
    "grand_canyon": "the Grand Canyon seen from above, red and orange gorges",
    "great_lake": "large freshwater lake seen from above with green shores",
    "hurricane": "hurricane seen from space, white spiral clouds with a clear eye",
    "rainforest": "vast green rainforest seen from above with a winding brown river",
    "great_britain": "the island of Great Britain seen from above, green with brown hills",
    "alps": "the Alps seen from above, a curved band of white snowy peaks",
    "cyclone": "huge swirling cyclone of white clouds seen from space",
    "borneo": "the island of Borneo seen from above, dark green jungle",
    "new_zealand": "the two islands of New Zealand seen from above, green with snowy spines",
    "madagascar": "the island of Madagascar seen from above, green and red earth",
    "great_barrier_reef": "the Great Barrier Reef seen from above, a long turquoise ribbon of reefs",
    "himalaya": "the Himalaya seen from above, a long arc of high white peaks",
    "greenland": "the island of Greenland seen from above, white ice cap with rocky edges",
    "japan": "the islands of Japan seen from above, a green curved chain",
    "australia": "the continent of Australia seen from above, red centre and green coasts",
    "antarctica": "the continent of Antarctica seen from above, a white ice disc",
    # The solar system
    "deimos": "tiny lumpy grey moon Deimos, smooth and dusty",
    "comet": "small icy comet nucleus with a long glowing blue and white tail",
    "phobos": "small potato shaped grey moon Phobos with a big crater",
    "small_asteroid": "small grey cratered asteroid",
    "sungrazer_comet": "bright comet with a long golden tail",
    "ice_asteroid": "icy blue white asteroid with frosty craters",
    "rocky_asteroid": "brown rocky asteroid covered in craters",
    "binary_asteroid": "pair of grey asteroids touching like a peanut",
    "metal_asteroid": "shiny metallic grey asteroid with dents",
    "vesta": "the dwarf world Vesta, grey and battered with a big crater",
    "ceres": "the dwarf planet Ceres, round grey with bright white spots",
    "charon": "Pluto's moon Charon, grey with a reddish pole",
    "eris": "the dwarf planet Eris, pale white and icy",
    "pluto": "the dwarf planet Pluto, beige with a pale heart shaped plain",
    "triton": "Neptune's moon Triton, pinkish ice with dark streaks",
    "europa": "Jupiter's moon Europa, cream ice crossed by brown cracks",
    "moon": "the Moon, grey with dark seas and bright craters",
    "io": "Jupiter's moon Io, yellow and orange with volcano spots",
    "callisto": "Jupiter's moon Callisto, dark brown speckled with craters",
    "mercury": "the planet Mercury, grey and heavily cratered",
    "titan": "Saturn's moon Titan, a hazy orange ball",
    "ganymede": "Jupiter's moon Ganymede, grey and brown patchwork",
    "mars": "the planet Mars, rusty red with a white polar cap",
    "venus": "the planet Venus, pale yellow swirling clouds",
    "earth": "the planet Earth, blue oceans, green and brown continents, white clouds",
    "neptune": "the planet Neptune, deep blue with a dark spot",
    "uranus": "the planet Uranus, pale cyan with a thin vertical ring",
    "jupiter": "the planet Jupiter, cream and orange bands with the great red spot",
    "saturn": "the planet Saturn, golden with wide tilted rings",
    "sun": "the Sun, a blazing orange and yellow ball with flares and spots",
    # Stars and nebulae
    "white_dwarf": "tiny bright white star with a pale blue glow",
    "brown_dwarf": "dim brown dwarf, a dark reddish brown banded ball",
    "red_dwarf": "small deep red star with a soft red corona",
    "orange_dwarf": "orange star with a warm glowing corona",
    "yellow_star": "yellow star like the Sun with a golden corona",
    "pulsar": "spinning pulsar, a small bright star with two long blue light beams",
    "white_star": "brilliant white star with sharp rays",
    "blue_star": "hot blue star with a bright cyan corona",
    "binary_star": "pair of stars, one yellow and one blue, orbiting close together",
    "black_hole": "black hole with a bright orange swirling accretion disc",
    "wolf_rayet_star": "blazing blue star inside a shell of pink and purple gas",
    "red_giant": "huge swollen red giant star with a mottled surface",
    "orange_giant": "large orange giant star with a fiery surface",
    "blue_giant": "big blazing blue giant star with long rays",
    "cepheid": "pulsing yellow white star with rings of light around it",
    "yellow_supergiant": "enormous golden supergiant star",
    "red_supergiant": "gigantic deep red supergiant star with boiling spots",
    "hypergiant": "colossal orange red hypergiant star shedding a cloud of gas",
    "supernova": "exploding star, a bright white core with orange and blue blast",
    "protoplanetary_disc": "young star inside a flat orange and brown dusty disc with dark rings",
    "planetary_system": "small star with several planets on thin circular orbit lines",
    "protostar_jets": "newborn star hidden in dust with two narrow glowing jets",
    "comet_cloud": "round cloud of countless tiny icy comets around a faint star",
    "bok_globule": "small dark brown dust cloud with a glowing red rim",
    "planetary_nebula": "ring shaped planetary nebula, turquoise inside and red outside",
    "reflection_nebula": "soft blue reflection nebula around a few bright stars",
    "supernova_remnant": "supernova remnant, a tangled shell of red and green filaments",
    "emission_nebula": "glowing pink and red emission nebula with dark lanes",
    "open_cluster": "open star cluster of bright blue stars wrapped in faint blue haze",
    # The Milky Way
    "dark_nebula": "dark brown dust cloud silhouetted with a glowing edge",
    "star_cloud": "dense cloud of thousands of tiny yellow and white stars",
    "pillars_nebula": "three tall brown pillars of gas and dust with glowing tips",
    "horsehead_nebula": "dark nebula shaped like a horse head in front of glowing red gas",
    "orion_nebula": "Orion nebula, glowing pink and turquoise gas around bright young stars",
    "globular_cluster": "round globular cluster, a dense ball of golden stars",
    "giant_molecular_cloud": "giant brown and purple cloud of gas and dust",
    "sagittarius_a": "supermassive black hole with a glowing golden ring around a black centre",
    "omega_centauri": "huge globular cluster, a bright golden ball of millions of stars",
    "carina_nebula": "Carina nebula, orange and blue clouds with dark dust canyons",
    "tarantula_nebula": "Tarantula nebula, pink glowing gas with long spidery filaments",
    "superbubble": "huge round bubble of thin pink gas blown by many stars",
    "star_forming_complex": "large region of pink nebulae dotted with blue newborn stars",
    "stellar_stream": "long thin curved stream of tiny stars",
    "dwarf_spheroidal": "faint round dwarf galaxy, a soft ball of sparse stars",
    "relativistic_jet": "long narrow blue jet of light shooting from a bright point",
    "dust_lane": "long dark brown band of dust laced with pink nebulae",
    "galactic_bulge": "golden glowing core of a galaxy, a bright oval of old stars",
    "spiral_arm": "curved spiral galaxy arm with blue stars and pink nebulae",
    "galactic_bar": "elongated golden bar of stars at the heart of a galaxy",
    "fermi_bubbles": "two huge pale violet bubbles above and below a glowing point",
    "small_magellanic_cloud": "Small Magellanic Cloud, an irregular fuzzy patch of stars",
    "large_magellanic_cloud": "Large Magellanic Cloud, an irregular galaxy with a bar and pink nebulae",
    "milky_way": "the Milky Way galaxy seen face on, a bright spiral with a golden core and blue arms",
    # Galaxies and the large scale universe
    "dwarf_galaxy": "small fuzzy dwarf galaxy, a pale blue patch of stars",
    "irregular_galaxy": "irregular galaxy, a lumpy cloud of blue stars and pink nebulae",
    "triangulum_galaxy": "Triangulum galaxy, a loose blue spiral",
    "spiral_galaxy": "spiral galaxy seen face on with two blue arms and a golden core",
    "edge_on_galaxy": "spiral galaxy seen edge on, a thin bright disc with a dark dust lane",
    "lenticular_galaxy": "lenticular galaxy, a smooth golden lens shaped disc",
    "sombrero_galaxy": "Sombrero galaxy, a bright bulge with a dark dust ring like a hat brim",
    "barred_spiral_galaxy": "barred spiral galaxy with a straight golden bar and two curling arms",
    "ring_galaxy": "ring galaxy, a blue ring of stars around a yellow centre",
    "elliptical_galaxy": "elliptical galaxy, a smooth soft golden oval",
    "andromeda": "Andromeda galaxy, a large tilted spiral with a bright core",
    "merging_galaxies": "two spiral galaxies colliding with long curved tails of stars",
    "quasar": "quasar, a blazing point of light with two long purple jets",
    "radio_galaxy": "radio galaxy, a small galaxy with two huge orange and blue lobes",
    "giant_elliptical": "giant elliptical galaxy, a huge glowing golden oval with a faint halo",
    "local_group": "small group of three spiral galaxies and a few dwarf galaxies",
    "galaxy_group": "group of a dozen galaxies of different shapes",
    "galaxy_cluster": "galaxy cluster, a crowd of hundreds of golden galaxies in a purple haze",
    "lensing_cluster": "galaxy cluster with thin bright blue arcs of distorted galaxies around it",
    "supercluster": "supercluster, a long cloud of many golden galaxy clusters",
    "cosmic_void": "cosmic void, an empty dark bubble ringed by thin strands of galaxies",
    "laniakea": "Laniakea supercluster, streams of tiny galaxies flowing like veins of a leaf",
    "cosmic_filament": "cosmic filament, a long glowing thread of countless tiny galaxies",
    "great_wall": "great wall of galaxies, a vast curved sheet of glowing violet strands",
}

# The countryside, the mountains and the Earth are seen from very high above: little dioramas.
LANDSCAPE = ("Each subject is a small self-contained diorama seen from high above at a three-quarter angle, "
             "with only its own patch of ground, cut out in a soft rounded shape; nothing between the subjects.")
# Space subjects float alone: no black sky, no background stars.
SPACE = ("Each subject floats alone on the transparent background. Draw every glow, gas cloud and halo with solid "
         "coloured pencil strokes and a clear outer edge, never a faint transparent haze; no black sky, no background "
         "stars, no frame, no cast shadow.")
EXTRA = {"country": LANDSCAPE, "mountains": LANDSCAPE, "earth": LANDSCAPE,
         "planets": SPACE, "stars": SPACE, "galaxy": SPACE, "cosmos": SPACE}


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
             RULES, STYLE]
    theme = name.rsplit("_", 1)[0]
    if theme in EXTRA:
        lines.append(EXTRA[theme])
    lines.append("Subjects, in reading order (top row left to right, then bottom row):")
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
