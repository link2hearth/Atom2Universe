"""Print the ImageGen prompts for Lanky Paws (Grandes papattes), one image per codex exec call.

Usage:
    python tools/papattes/prompts.py --list      # every image name, in generation order
    python tools/papattes/prompts.py legs        # the full prompt for one image (ready for codex exec)

The sheets (heads, bodies, legs, props) are cut by tools/papattes/pack_art.py, which reads the piece ids
below in reading order (left to right, top to bottom). The sources stay out of the repo, in
Documents NON commit du projet/Papattes.
"""
import sys

STYLE = ("Style: soft GOUACHE children's picture book illustration: crisp, fairly thick dark brown outlines that stay "
         "readable at small size on a phone, flat warm colours with gentle soft shading, small touches of brush texture "
         "inside the shapes, round cute characters with big shiny eyes. Absolutely no 3D render, no glossy plastic look, "
         "no airbrush, no photoreal, no pixel art, no text. Every creature and every piece faces to the RIGHT "
         "(right side profile or right three-quarter view).")

SHEET_RULES = ("Layout: a genuinely transparent background (real alpha, no checkerboard, no white or coloured backdrop, "
               "no glow, no haze, no cast shadow, nothing at all around the pieces). Exactly {count} separate pieces "
               "arranged in {columns} columns and {rows} rows, each one alone in the centre of its own invisible cell, "
               "with wide empty margins between pieces so that nothing touches or overlaps anything else. Every piece is "
               "drawn at the same visual size. No frames, no grid lines, no text, no letters, no numbers, no labels, no "
               "watermark, no UI. Production sprite sheet only, not a mockup.")

BACKDROP_RULES = ("Layout: a full-bleed opaque painting with NO transparency, filling the whole frame edge to edge. It must "
                  "be seamlessly tileable horizontally: the left edge continues exactly into the right edge (same horizon "
                  "height, same sky colour, no element cut at one edge only). Far distance only, soft and slightly hazy so "
                  "it stays behind the gameplay; no characters, no animals, no foreground ground strip, no path, no text. "
                  "Keep the bottom third simple and calm (the game paints its own ground over it).")


TILE_RULES = ("Layout: a full-bleed opaque square illustration with NO transparency, centred framing with the character "
              "large in the middle and a little breathing room around it, no frame, no text, no letters, no logo.")

HEAD_IDS = ["cat", "frog", "owl", "axolotl", "hamster", "capybara", "snail", "dragon", "panda"]
BODY_IDS = ["pompom", "bee", "shell", "wool", "buoy", "pumpkin", "dandelion", "jelly", "walnut"]
LEG_IDS = ["stilts", "flamingo", "dachshund", "frog", "gecko", "ostrich"]
# Later sheets: the game appends their ids after the first ones.
HEAD2_IDS = ["fox", "rabbit", "penguin", "koala", "hedgehog", "raccoon", "sloth", "otter", "redpanda"]
HEAD3_IDS = ["mouse", "pig", "duckling", "bat", "lion", "monkey", "unicorn", "shiba", "sheep"]
BODY2_IDS = ["cloud", "beachball", "boulder", "cauldron", "rubberball", "marshmallow", "strawberry", "cactus", "pineapple"]
LEG2_IDS = ["spring", "kangaroo", "skates", "rollers"]
PROP_IDS = ["finish_flag", "checkpoint_flag", "stepping_stone", "rock", "bush", "mushroom",
            "reeds", "lily_pad", "snow_pile", "egg", "seed", "flask"]

HEADS = {
    "cat": "a ginger tabby cat head",
    "frog": "a green frog head with big round eyes on top",
    "owl": "a brown and cream owl head with tufted ears",
    "axolotl": "a pink axolotl head with feathery frilly gills on each side",
    "hamster": "a golden hamster head with chubby cheeks",
    "capybara": "a calm brown capybara head with a big square snout",
    "snail": "a pale snail head with two eye stalks (no shell at all)",
    "dragon": "a little green baby dragon head with tiny horns",
    "panda": "a black and white panda head",
    "fox": "a red fox head",
    "rabbit": "a white bunny head with long upright ears",
    "penguin": "a black and white penguin head with an orange beak",
    "koala": "a grey koala head with big fluffy ears",
    "hedgehog": "a brown hedgehog head with spines",
    "raccoon": "a grey raccoon head with a black eye mask",
    "sloth": "a smiling brown sloth head",
    "otter": "a brown otter head with whiskers",
    "redpanda": "a red panda head with white cheek marks",
    "mouse": "a grey mouse head with big round ears",
    "pig": "a pink pig head with a snout",
    "duckling": "a fluffy yellow duckling head with an orange beak",
    "bat": "a purple bat head with big pointed ears",
    "lion": "a lion cub head with a soft golden mane",
    "monkey": "a brown monkey head with a peach face",
    "unicorn": "a white unicorn head with a pastel spiral horn and pastel mane",
    "shiba": "an orange and cream shiba inu dog head",
    "sheep": "a cream sheep head with a woolly tuft and dark face",
}

BODIES = {
    "pompom": "a fluffy mint green fur pompom ball",
    "bee": "a round bumblebee body with yellow and black stripes and two small translucent wings on its back",
    "shell": "a domed turtle shell with hexagon plates, green-brown",
    "wool": "a puffy cloud of cream sheep wool",
    "buoy": "a yellow rubber duck swim ring body, round and plump (no duck head)",
    "pumpkin": "a round orange pumpkin with ribs and a short stem on top",
    "dandelion": "a fluffy white dandelion seed ball",
    "jelly": "a wobbly translucent raspberry jelly blob",
    "walnut": "a round walnut shell, light brown and wrinkled",
    "cloud": "a fluffy white cumulus cloud ball",
    "beachball": "a striped inflatable beach ball",
    "boulder": "a round grey stone boulder with moss spots",
    "cauldron": "a round black iron cauldron pot",
    "rubberball": "a bouncy red and white striped rubber ball",
    "marshmallow": "a plump pink marshmallow",
    "strawberry": "a big red strawberry with seeds and green leaves on top",
    "cactus": "a round green barrel cactus with small spines and a flower on top",
    "pineapple": "a round pineapple body with diamond pattern and leaves on top",
}

LEGS = {
    "stilts": ("a wooden stilt pole with a leather strap", "a wooden stilt pole ending in a small wooden hoof block"),
    "flamingo": ("a very thin pink flamingo thigh", "a very thin pink flamingo shin ending in a pink webbed foot"),
    "dachshund": ("a short chunky brown dachshund thigh", "a short chunky brown dachshund shin ending in a small dog paw"),
    "frog": ("a green frog thigh", "a green frog shin ending in a webbed frog foot"),
    "gecko": ("a lime green gecko thigh", "a lime green gecko shin ending in a foot with big round sticky toe pads"),
    "ostrich": ("a powerful pinkish grey ostrich thigh", "a pinkish grey scaly ostrich shin ending in a two-toed ostrich foot"),
    "spring": ("a short sturdy blue painted wooden peg leg",
               "a tall coiled shiny silver metal spring ending in a round red rubber foot pad"),
    "kangaroo": ("a muscular sandy brown kangaroo thigh",
                 "a long sandy brown kangaroo shin ending in a very long narrow kangaroo foot"),
    "skates": ("a red and white striped woolly sock leg",
               "a red and white striped woolly sock shin ending in a white lace-up ice skate boot with a shiny steel "
               "blade underneath"),
    "rollers": ("a purple leg in a knee-high yellow sock",
                "a purple leg in a yellow sock ending in a retro roller skate boot with four colourful wheels underneath"),
}

PROPS = {
    "finish_flag": "a festive finish pennant flag with bunting on a tall pole",
    "checkpoint_flag": "a small blue pennant flag on a thin pole",
    "stepping_stone": "a flat grey stepping stone seen from the side",
    "rock": "a rounded mossy grey rock",
    "bush": "a round leafy green bush",
    "mushroom": "a big red mushroom with white spots",
    "reeds": "a clump of tall green reeds with brown cattails",
    "lily_pad": "a green lily pad with a pink water lily flower, seen from the side",
    "snow_pile": "a soft pile of white snow with blue shadows",
    "egg": "a speckled pastel egg standing upright",
    "seed": "a shiny golden seed, like a coin icon",
    "flask": "a round glass lab flask with bubbling green liquid and a cork",
}


def listing(ids, table):
    return "; ".join(f"{n}) {table[i]}" for n, i in enumerate(ids, 1))


def sheet(count, columns, rows):
    return SHEET_RULES.format(count=count, columns=columns, rows=rows)


def heads_sheet(ids):
    return ("A sprite sheet of 9 cute creature HEADS, each one isolated: only the head, with NO neck, NO body, NO "
            "shoulders, NO limbs (the head will later be placed on top of a separate body). Each head is seen in right "
            "side profile or right three-quarter view, looking to the right, with big shiny eyes. In exact reading order, "
            f"left to right then top to bottom: {listing(ids, HEADS)}. " + sheet(9, 3, 3))


def bodies_sheet(ids):
    return ("A sprite sheet of 9 round creature BODIES seen from the side, each one isolated: only the round torso, "
            "with NO head, NO face, NO eyes, NO arms and NO legs (legs will be attached underneath and a head on top "
            "later). Each body is a plump ball shape of about the same size. In exact reading order, left to right "
            f"then top to bottom: {listing(ids, BODIES)}. " + sheet(9, 3, 3))


def legs_sheet(ids):
    n = len(ids)
    return (f"A sprite sheet of separate LEG PARTS for a creature that walks on long legs, {n} columns by 2 rows, "
            f"{2 * n} pieces. "
            "Every piece stands perfectly VERTICAL and straight, narrow and tall (about four times taller than wide), "
            "alone in the middle of its cell with wide empty gaps on every side: the pieces never touch each other and "
            f"the top row never touches the bottom row. TOP ROW: {n} THIGHS, each one a plain vertical segment with "
            f"rounded ends at both top and bottom, no foot, no hip, no body attached. BOTTOM ROW: {n} SHINS, each one a "
            "vertical segment with a rounded top end and ending at the bottom in its FOOT, and every foot points to the "
            "RIGHT (toes forward to the right, heel at the back on the left). The thigh and the shin of the same column "
            "belong to the same kind of leg. Columns from left to right: "
            + "; ".join(f"column {c}: thigh = {LEGS[i][0]}, shin = {LEGS[i][1]}" for c, i in enumerate(ids, 1))
            + ". " + sheet(2 * n, n, 2))


IMAGES = {
    "heads": ("sheet", "3:2", heads_sheet(HEAD_IDS)),
    "bodies": ("sheet", "3:2", bodies_sheet(BODY_IDS)),
    "legs": ("sheet", "3:2", legs_sheet(LEG_IDS)),
    "heads2": ("sheet", "3:2", heads_sheet(HEAD2_IDS)),
    "heads3": ("sheet", "3:2", heads_sheet(HEAD3_IDS)),
    "bodies2": ("sheet", "3:2", bodies_sheet(BODY2_IDS)),
    "legs2": ("sheet", "3:2", legs_sheet(LEG2_IDS)),
    "props": ("sheet", "3:2",
              "A sprite sheet of 12 game scenery objects and icons, each one isolated and standing upright on its own "
              "invisible ground line (no ground drawn), seen from the side. These are plain inanimate objects: absolutely "
              "NO faces, NO eyes, NO mouths, NO smiles on any of them. In exact reading order, left to right then "
              f"top to bottom: {listing(PROP_IDS, PROPS)}. " + sheet(12, 4, 3)),
    "bg_meadow": ("backdrop", "3:2",
                  "A distant side-scrolling game background: a flowery meadow with rolling green hills, scattered "
                  "distant trees, a soft morning sky with pale clouds. " + BACKDROP_RULES),
    "bg_marsh": ("backdrop", "3:2",
                 "A distant side-scrolling game background: a misty marsh with far-away reeds and still pools, soft "
                 "fog banks and a warm golden sky. " + BACKDROP_RULES),
    "bg_ice": ("backdrop", "3:2",
               "A distant side-scrolling game background: an ice floe landscape with distant snowy mountains, a pale "
               "blue sky with a faint gentle aurora. " + BACKDROP_RULES),
    "hub": ("tile", "1:1",
            "A game tile illustration: a cute ginger cat head on a round fluffy mint green pompom body (no other body), "
            "walking proudly to the right on two very tall wooden stilt legs, in a sunny flowery meadow with soft hills. "
            + TILE_RULES),
}

FRAME = {
    ("sheet", "3:2"): "ONE landscape image with a 3:2 aspect ratio (1536 by 1024 pixels) with a transparent background",
    ("backdrop", "3:2"): "ONE landscape image with a 3:2 aspect ratio (1536 by 1024 pixels), fully opaque, no transparency",
    ("tile", "1:1"): "ONE square image (1024 by 1024 pixels), fully opaque, no transparency",
}


def prompt(name):
    kind, aspect, body = IMAGES[name]
    return (f"Use your image generation tool once (no code, no other files) to create {FRAME[(kind, aspect)]}, then copy "
            f"the generated PNG into the current directory as {name}.png. Image content: {body} {STYLE}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    if sys.argv[1] == "--list":
        print("\n".join(IMAGES))
    elif sys.argv[1] in IMAGES:
        print(prompt(sys.argv[1]))
    else:
        sys.exit(f"Unknown image {sys.argv[1]}; see --list")
