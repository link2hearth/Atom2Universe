# Harmonisation du 10 septembre 2026

Deux atlas originaux créés avec ImageGen intégré, ajoutés à `materials.png` et
`flora.png` :

- `groundcover.png` : trois touffes d’herbe, rocher, rocher moussu, petits galets,
  galets moussus, cactus décoratif, quatre étapes de blé, herbe sèche, roseaux,
  fougère et trèfle sans fleurs. Les trois nouvelles touffes sont disponibles dans
  l’inventaire créatif. Les herbes existantes et les rochers utilisent également cet atlas.
- `utility.png` : four, établi, cactus (côté, dessus, intérieur), verre, eau calme,
  eau courante, lave, pierre moussue, neige, glace, enduit, argile, paille et pavés.
  Les matériaux supplémentaires restent disponibles dans l’atlas pour de futurs blocs.

La génération privilégie désormais les herbes. Les fleurs sauvages sont un tirage
rare dans leurs massifs ; les jardins construits conservent leurs plantations.
Les 147 fichiers de l’ancien dossier `Cave World/Tiles` ont été supprimés après
remplacement de toutes leurs références. Les images d’objets et de particules
encore utilisées sont conservées. Aucun identifiant de bloc existant n’a été supprimé.

## Prompt — groundcover.png

Create ONE cohesive production game sprite atlas, exactly 4 columns by 4 rows of equal square cells, transparent RGBA background, no grid lines, labels, frames, shadows or ground patches. Cozy pastel voxel game, hand-painted crisp pixel art readable at 64 pixels per cell, muted sage and mint greens, soft blue-grey stones, gentle cream highlights, no harsh black outlines. Flat front view, isolated grounded objects: each object centered, fits within 80% of its cell width and 88% cell height, bottom roots/base at 94% cell height. Strict row-major contents: ROW 1: short sparse sage grass tuft with five broad curved blades, medium airy mint meadow grass tuft with many fine bending blades, taller arching olive-sage grass tuft with asymmetric silhouette, low irregular faceted blue-grey rock with a few rounded corners and subtle pale mineral marks. ROW 2: same blue-grey rock partly covered with soft sage moss, small group of three blue-grey pebbles, same three pebbles partly mossy, small branching pale green cactus. ROW 3: wheat growth stage 1 tiny green sprouts, wheat stage 2 leafy green stems, wheat stage 3 taller stems with unripe seed heads, wheat stage 4 mature softly golden wheat. ROW 4: dry straw grass tuft, slim green reeds without flowers, low fern with delicate fronds, low clover leaves WITHOUT flowers. No flowers anywhere. Calm desaturated palette, polished charming silhouettes, fully transparent empty areas, no scenery, single exact uniform 4x4 atlas.

## Prompt — utility.png

Create ONE square game MATERIAL texture atlas, exactly 4 columns x 4 rows of equal square tiles, no gaps, no grid borders, no labels or text. Flat orthographic texture artwork, NOT 3D cubes or scenes. Cozy hand-painted pixel art at 64-pixel detail per tile, restrained pastel sage, honey wood, mineral blue, cream and muted coral, subtle low-contrast material detail. Every tile fills its whole square. Strict row-major order: ROW 1: front face of a small cream limestone furnace with centered dark arched opening and gentle ember glow; honey wood crafting table top with inset squared work surface and a few tool marks but no loose objects; sage green cactus skin with soft vertical ribs and tiny ivory spines; top cross section of cactus with radial sage ribs. ROW 2: pale mint cactus inner flesh with delicate small radial fibers; pale blue glass window with slim ivory frame only around its four edges and faint diagonal reflections in the center; calm muted turquoise water surface with a few delicate pale ripples; flowing muted turquoise water with graceful narrow waves. ROW 3: soft coral-orange molten lava with dark desaturated basalt patches and warm fine bright cracks; blue-grey stone lightly mottled with sage moss; soft cream-white snow with fine granular texture; pale mineral blue ice with a few delicate cracks. ROW 4: whitewashed cream plaster; warm peach clay; woven pale straw mat; soft rounded limestone cobblestones. The glass tile may have a pale blue opaque center, the engine will handle transparency. All other tiles opaque. Tiles should repeat seamlessly where applicable, no exaggerated dark contrast, polished consistent game assets.
