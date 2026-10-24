"""Pack the ImageGen porcelain atlas into a transparent WebP below 100,000 bytes.

Built-in ImageGen prompt: 24 hand-painted glazed ceramic miniature jewels,
restrained gold inlay, celestial garden, transparent 6x4 atlas, no text or frames.
Row order: jade atom, jade lotus, sakura, sakura sprig, shell, opal drop;
lavender cloud, amethyst, honey flower, sun, peach blossom, lantern;
snowflake, wave, mint leaf, fern, nebula, ruby; pearl lotus, galaxy,
crescent, Saturn, butterfly, compass star.
Usage: python tools/mahjong/pack_art.py SOURCE.png OUTPUT.webp
"""
from pathlib import Path
import sys
from PIL import Image

# Exact prompts used with the built-in ImageGen tool.
PORCELAIN_PROMPT = "Use case: stylized-concept\nAsset type: production game sprite atlas for an Android mahjong porcelain collection, Atom2Universe.\nPrimary request: a beautiful premium atlas of exactly 24 isolated small enamel porcelain medallion illustrations, arranged in a perfectly regular 6 columns by 4 rows grid on transparent background. Square canvas. Each cell identical size; each object centered in its own cell with generous transparent padding; no overlap. No tile bodies, no frames, no text, no letters, no numbers.\nStyle: exquisite hand-painted glazed ceramic miniature jewels, soft dimensional highlights, delicate restrained gold inlay, crisp silhouettes readable at tiny sizes. Harmonious jade, lavender, pearl white, rose and celestial blue. Elegant science-meets-nature cosmic garden, not cartoon clipart.\nSubjects in exact row-major order:\nrow 1: jade atom with three orbital rings; imperial jade lotus with gold center; pale pink sakura blossom; golden pink sakura sprig; pearlescent turquoise seashell; luminous opal water droplet.\nrow 2: lavender cloud with tiny stars; amethyst crystal cluster; honey golden five-petal flower; amber sun disc with rays; peach blossom; coral red lantern.\nrow 3: icy blue snowflake; sapphire ocean wave; mint green leaf; emerald fern sprig; purple nebula swirl; ruby faceted heart jewel.\nrow 4: pearl white lotus; turquoise spiral galaxy; silver crescent moon; gold ringed planet Saturn; apricot butterfly; radiant golden celestial compass star.\nAll 24 objects fully visible and separated. Each has roughly equal visual size, fits within central 75 percent of its cell. Actual alpha transparency, no shadows outside cell. Production sprite sheet only, no mockup, no checkerboard."
TREASURE_PROMPT = "Use case: stylized-concept. Asset type: compact production Android game sprite atlas.\nCreate exactly four isolated premium celestial porcelain treasure illustrations in a precise 2 columns by 2 rows square grid, each centered in its equal cell with 15% clear padding on all sides. Actual transparent background.\nTop left: closed small rounded jade and ivory porcelain treasure chest, delicate gold brass inlay, atom medallion clasp, three-quarter frontal view.\nTop right: same chest open, lid attached at the back, filled with glowing pearlescent gems and a few tiny lavender and gold stars hovering just above it.\nBottom left: beautiful ivory and sapphire porcelain trophy with gold crescent handles, star shaped gem center, modest low pedestal.\nBottom right: precious blue and lavender spherical pearl encircled by three delicate golden atomic orbital rings, one small star.\nStyle: refined hand-painted dimensional ceramic collectibles, glossy soft highlights, exquisite but clean shapes, consistent soft upper-left lighting, calm cosmic garden palette of jade, lavender, pearl, gold. All four completely separated, fully contained in cells, no ground plane, no text, no numbers, no watermark, no UI. Not a mockup."
PORCELAIN_LAYOUT_EDIT = "Preserve all 24 designs and exact order. Re-layout in a regular 6x4 grid with each object occupying at most 60 percent of cell width and 65 percent of cell height. Wide transparent gutters; no cropped edges or neighboring fragments. Actual alpha transparency; no frames or text."

source = Image.open(sys.argv[1]).convert("RGBA")
# Texture atlas: full-bleed cells, cropped slightly to exclude generated seams.
TEXTURE_PROMPT = "Use case: stylized-concept.\nAsset type: production texture atlas for mahjong tile faces, NOT icons and NOT UI.\nCreate a landscape 3:2 image with EXACTLY 6 equal columns and 4 equal rows, 24 square full-bleed material swatches. Every swatch fills its cell edge to edge with no gutters, no borders, no rounded corners, no shadows. Texture only, front-on orthographic flat materials.\nThese will be small porcelain tile backgrounds under dark mahjong pips. All textures MUST be very pale, luminous, restrained, low contrast, with understated fine material detail. Mostly ivory/pastel surfaces, never dark saturated fills. Beautiful tactile artisanal ceramic, natural stone, glass and silk finishes. No objects, no central illustration, no glyphs, no text, no numbers, no stars, no big flowers.\nExact row-major materials:\n1 pale celadon jade porcelain with tiny crackle glaze;\n2 ivory jade stone with extremely thin warm gold mineral veins;\n3 milky blush porcelain with faint scattered tiny petal impressions;\n4 pale rose quartz with subtle gold wisps near corners;\n5 pearly white nacre with soft turquoise iridescence;\n6 milky opal with faint watery aqua ripples;\n7 pale lavender satin ceramic;\n8 ivory lilac mineral stone with faint amethyst marbling;\n9 warm cream ceramic with extremely faint honeycomb embossing;\n10 pale amber translucent alabaster;\n11 peach satin glaze;\n12 pale coral lacquer with delicate fine organic grain;\n13 white blue frosted ceramic with tiny crystalline cracks;\n14 pale sapphire blue marble;\n15 ivory mint leaf-vein pressed paper ceramic;\n16 very pale emerald malachite with soft fine curved bands;\n17 very pale mauve cloud glaze;\n18 pale ruby pink enamel with tiny fine gold dust;\n19 white pearl lotus-silk nacre;\n20 pale turquoise sea glass with tiny bubble grain;\n21 cool silver moonstone with subtle cloudy sheen;\n22 ivory porcelain with sparse microscopic gold mineral flecks;\n23 apricot woven silk texture;\n24 pale rose-gold sandstone with delicate flowing strata.\nNo perspective, no bevels, no rendered tile objects. Visibly different materials but subtle enough for maximum readability of dark foreground marks. Consistent bright diffuse light across all swatches."
if "--textures" in sys.argv:
    atlas = Image.new("RGB", (768, 512))
    for i in range(24):
        x, y = i % 6 * 256, i // 6 * 256
        cell = source.crop((x + 4, y + 4, x + 252, y + 252)).convert("RGB")
        atlas.paste(cell.resize((128, 128), Image.Resampling.LANCZOS), (i % 6 * 128, i // 6 * 128))
    out = Path(sys.argv[2])
    out.parent.mkdir(parents=True, exist_ok=True)
    for quality in (85, 75, 65, 55):
        atlas.save(out, "WEBP", quality=quality, method=4)
        if out.stat().st_size < 100_000:
            print(f"{out}: {out.stat().st_size} bytes, quality={quality}")
            sys.exit(0)
    raise RuntimeError("Texture atlas exceeds its 100 KB budget")
# The generated atlas has wider vertical gutters; normalize to square cells.
rows = (135, 365, 635, 900, 1150)
treasures = "--treasures" in sys.argv
cell_size = 320 if treasures else 120
cols = 2 if treasures else 6
boxes = [(50,90,625,555),(650,0,1254,600),(0,560,650,1230),(680,650,1254,1175)] if treasures else [
    (round(i % 6 * source.width / 6), rows[i // 6],
     round((i % 6 + 1) * source.width / 6), rows[i // 6 + 1]) for i in range(24)]
atlas = Image.new("RGBA", (cell_size * cols, cell_size * (2 if treasures else 4)))
for index, box in enumerate(boxes):
    col, row = index % cols, index // cols
    cell = source.crop(box)
    bounds = cell.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()
    if bounds:
        cell = cell.crop(bounds)
    cell.thumbnail((int(cell_size * .9), int(cell_size * .9)), Image.Resampling.LANCZOS)
    atlas.alpha_composite(cell, (col * cell_size + (cell_size - cell.width) // 2,
                                row * cell_size + (cell_size - cell.height) // 2))
# Quantize alpha for compression while retaining soft transparent edges.
atlas.putalpha(atlas.getchannel('A').point(lambda a: round(a / 17) * 17))
out = Path(sys.argv[2])
out.parent.mkdir(parents=True, exist_ok=True)
for quality in (85, 80, 65, 50, 35):
    atlas.save(out, "WEBP", quality=quality, method=4)
    if out.stat().st_size < 100_000:
        print(f"{out}: {out.stat().st_size} bytes, {atlas.size}, quality={quality}")
        break
else:
    raise RuntimeError("Atlas exceeds its 100 KB budget")
