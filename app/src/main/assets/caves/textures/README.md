# Cave World — paysages et matériaux, version 2

Les atlas complémentaires sont documentés dans [REFRESH.md](REFRESH.md), avec
leurs prompts de création : herbes, rochers et matériaux supplémentaires.

Assets originaux créés avec l’outil ImageGen intégré, le 9 septembre 2026.
Les PNG sont des atlas 4 × 4. `CozyTextureAtlas` découpe chaque case
à partir des dimensions réelles puis prépare des textures de 64 × 64 pixels.
Les variantes de bois, feuilles, pierres et tissus partagent ces matériaux,
avec une palette appliquée au chargement ; les inclusions de minerai sont dessinées
par le code Android. Les atlas ne sont pas chargés pendant la génération du terrain.

- `materials.png` : herbe, côté herbeux, terre, pierre ; gravier, sable, argile,
  écorce ; coupe de bois, planches, feuilles, bouleau ; briques, tuiles,
  calcaire et ardoise.
- `flora.png` : marguerites, lavande, cosmos, bleuets ; boutons d’or,
  coquelicots, fougère, herbes ; trèfle, herbes dorées, champignons rouges,
  champignons miel ; roseaux, succulente, églantines et buisson à baies.

## Prompts utilisés

### Matériaux

Create one production game texture atlas image, square 1024x1024, exactly 4 columns by 4 rows of equally sized square material tiles. Absolutely no gaps, padding, grid lines, borders, labels or text. Each tile is a flat orthographic seamless repeating texture, no perspective or object renders. Art direction: beautiful cozy voxel game, subtly hand-painted pixel art at 64-pixel detail per tile, calm sage, warm earth, honey oak, muted rose and mineral blue, restrained highlights, low contrast, no harsh black outlines. Uniform diffuse lighting. Tiles in strict row-major order: row 1: meadow grass top (sage green fine tiny blades), grass block side (top 20% sage grass scalloped into brown earth), warm brown soil with tiny grains, pale blue-grey natural stone with subtle broad veins; row 2: rounded river gravel, cream sand with very subtle ripples, peach terracotta clay, vertical honey oak bark; row 3: oak log end grain with centered growth rings, honey oak planks with staggered joints, dense sage leaf clusters (opaque surface), creamy birch bark with sparse dark small horizontal marks; row 4: dusty red bricks with thin warm mortar, overlapping desaturated teal roof shingles, cream limestone masonry with irregular blocks, blue-grey slate paving. Fill each tile entirely. Texture art only, NOT a contact sheet of 3D cubes, no frame or scene.

### Plantes

Create ONE production sprite atlas for a cozy voxel exploration game: square image, exactly 4 columns x 4 rows of equal square cells, no grid, no labels or text. True transparent background everywhere outside the plants, no ground patches, no shadows, no white backdrop. Flat front-facing hand-painted pixel art with crisp silhouettes, restrained warm pastel accents and sage green stems. Each plant isolated centered within its cell, maximum width 75% cell, roots/bottom precisely at 94% cell height, no part crosses into neighboring cells. Row 1 left to right: small cluster of white daisies with gold centers; three lavender flower spikes; blush pink cosmos flowers; blue cornflowers. Row 2: yellow buttercups; red poppies; broad leafy fern; airy meadow grass tuft. Row 3: clover with three white blossoms; tall golden grass tuft; three red-capped mushrooms; three honey-brown mushrooms. Row 4: cattails with green reeds; tiny succulent with dusty peach tips; delicate pale pink wildflower cluster; low green berry bush with red berries. Single coherent professional game asset sheet, charming carefully shaped leaves and petals, pixel detail readable at 64 pixels per cell, no pots, no scenery, no border.

## Compatibilité

`terrainVersion = 2` est attribué aux nouveaux mondes. Une sauvegarde sans ce
champ reste en version 1 : ses blocs de base doivent rester identiques puisque
le stockage des chunks ne conserve que les modifications du joueur.
La version 2 utilise des collines adoucies, des vallées, des arbres déterministes
qui traversent les chunks, des massifs de plantes et trois familles de lieux :
maison, pavillon avec puits, jardin parmi des vestiges. Les sites ont un sol nivelé
avec une transition progressive vers le terrain. Les textures bénéficient à
toutes les sauvegardes sans changer leurs identifiants de blocs.
