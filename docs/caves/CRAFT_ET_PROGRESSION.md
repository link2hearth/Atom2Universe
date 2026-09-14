# Fabrication, placement et prochaines étapes

## Direction retenue

Fabrication depuis le personnage, sans grille et sans coffre. Les matériaux et
les armes conservent leurs inventaires et raccourcis distincts. Les recettes
utilisent les quantités réellement possédées, même réparties entre plusieurs
essences de bois. Le four doit être **dans l’inventaire** pour cuire/fondre ;
il n’est pas consommé. Un four posé est un bloc récupérable, pas une machine
autonome. L’établi est explicitement décoratif et ne bloque aucune recette.

Inspirations : [fabrication Minecraft](https://www.minecraft.net/en-us/article/how-craft),
[fonction du four](https://www.minecraft.net/de-de/article/block-week-furnace),
[lingots de fer](https://www.minecraft.net/en-us/article/taking-inventory--iron-ignot).
Les quantités et l’ergonomie ci-dessous sont celles de Cave World.

## Résultat de la passe

187 définitions de blocs/objets relues et normalisées, 92 recettes canoniques.
Les anciennes recettes incohérentes et leurs doublons `_harvest` sont remplacés.
Les blocs de minerai déjà stockés peuvent être extraits en ressources brutes :
pas de suppression d’inventaire ni de changement des anciens identifiants.

| Chaîne | Recettes principales |
| --- | --- |
| Bois | 1 bûche → 4 planches de son essence ; 2 planches quelconques → 4 bâtons |
| Fibres | Herbes/fougères → fibre ; 3 fibres → 1 ficelle ; 8 fibres → 1 bloc textile blanc |
| Équipements | 8 cobbles → four ; 4 planches quelconques → établi décoratif ; 3 lingots de fer → seau |
| Combustible | 1 bûche + 2 planches + four conservé → 1 charbon de bois |
| Métaux | 4 ressources brutes + 1 charbon/charbon de bois + four conservé → 4 lingots |
| Verre | 4 sables + 1 combustible + four conservé → 4 verres |
| Pierre | 4 cobbles + 1 combustible + four conservé → 4 pierres lisses ; 4 pierres → 4 briques grises |
| Argile | 1 bloc cassé → 4 boules ; 4 boules → 1 bloc ; 4 boules + combustible + four → 4 briques cuites ; 4 briques cuites → 1 bloc de briques rouges |
| Terre cuite | 4 blocs d’argile + combustible + four → 4 terres cuites ; 4 terres cuites → 4 briques de terre cuite |
| Construction | Grès compacté, briques de grès, briques de basalte, pavage, variantes moussues, toit teinté au cuivre |
| Éclairage | 1 bâton + 1 charbon/charbon de bois → 4 torches |
| Combat | Fronde avec fibres/ficelle ; arc avec bâtons/ficelle ; arbalète avec planches/fer/ficelle |
| Munitions | Flèches avec bâton/caillou/fibre ; carreaux avec bâton/fer ; balles avec cuivre/fer/poudre minérale |
| Couleurs | 33 teintures de textile blanc avec végétaux ou pigments minéraux existants |

Libertés assumées : les fibres remplacent temporairement les plumes pour les
flèches ; les nuances textiles partagent parfois un pigment. Le charbon et le
minerai rouge produisent une poudre propulsive **fictive propre au jeu**. Ce
n’est pas une recette de poudre réelle. Les cuissons se font instantanément par
lots de quatre, sans simulation de machine ni attente en arrière-plan.

## Placement et récolte

- Les JSON déclarent `placement_rule`, `replaceable`, `tags`, `drop` et les quantités.
- Fleurs/herbes sur sol ; cactus sur sable ou cactus, avec côtés libres ; roseaux
  près de l’eau ; petits objets et torches sur un support solide.
- Impossible d’écraser un bloc occupé. Les petites herbes remplaçables peuvent
  céder la place à une construction. Orientation remise à zéro lors d’une nouvelle pose.
- Les décorations privées de support lors des éditions ou des chutes de blocs
  se cassent suivant leur récolte, avec propagation bornée pour éviter un pic de travail.
- Les munitions, lingots et autres ressources ne sont pas posables. Le seau garde
  une action spécifique et vise directement les sources, sans prendre l’eau courante.
- Bois, planches et constructions restent récupérables. Sols couverts → terre,
  pierre ordinaire → cobble, minerais → ressources, argile → boules.
- La cobble moussue fabriquée reste désormais récupérable telle quelle.
- Les glaces et le verre restent fragiles. Pas encore d’outils spécialisés pour les récupérer.

## Ce qu’il reste à ajouter, dans l’ordre utile

1. **Approvisionnement naturel — réalisé en génération v3** : argile, boue, humus,
   mousse, basalte et terre cuite ont leur place dans les nouveaux mondes.
   Voir [la génération naturelle](GENERATION_NATURELLE.md). La glace apparaît dans
   les eaux froides ; sa récupération avec un outil et son regel restent à ajouter.
2. **Animaux passifs** : moutons, vaches, puis poules/cochons. Modèles, déplacements,
   apparition par biome, évitement des obstacles, sons, reproduction et persistance.
   Les moutons apporteraient une vraie filière laine/tonte ; les vaches viande/cuir/lait ;
   les poules plumes/œufs. Moutons, vaches, poulets et cochons intégrés avec jeunes
   et variations de robes (croissance et reproduction à venir) : voir
   [les animaux passifs](ANIMAUX_PASSIFS.md) pour le périmètre et les suites.
3. **Agriculture et nourriture** : semences, terre cultivée, eau, croissance du blé,
   récoltes selon maturité, pain et utilisation des aliments. Le blé actuel reste
   décoratif ; aucune fausse recette de nourriture sans effet n’a été ajoutée.
4. **Dalles et escaliers** : état/orientation, géométrie partielle, sélection et
   physique correspondantes, raccords et placement haut/bas. Ce ne sont pas simplement
   deux JSON de textures. Commencer par pierre, cobble, planches, grès et briques.
5. **Construction** : portes, barrières, portillons, échelles, vitres fines ; terre
   cuite teintée/émaillée et variantes des formes partielles. La terre cuite de base
   et ses briques existent déjà dans cette passe.
6. **Outils et entretien** : pioche, hache, pelle, cisailles ; temps de casse adaptés,
   récoltes spéciales, jeunes pousses et décomposition des feuilles. Affiner ensuite
   les récoltes du verre, de la glace et des feuillages.
7. **Métallurgie et combat avancés** : usages de l’or/argent, composants et recettes
   des armes à feu, équilibrage coûts/raretés/revente. Les armes à feu restent issues
   des systèmes de butin existants ; cette passe fabrique fronde, arc et arbalète.

Pas de coffre ni de grille 3×3 prévu. Un four interactif posé avec cuisson dans
le temps serait une évolution facultative, pas nécessaire au système actuel.

## Vérification

`tools/caves/check-data.ps1` contrôle références, groupes disjoints, équipements
non consommés, règles de placement et noms EN/FR. `CraftRulesCheck.java` exerce
le code Kotlin compilé : mélanges, lots, combustible alternatif, conservation
du four et grands stocks (11 cas). `PlacementRulesCheck.java` vérifie 9 cas de
support et de voisinage avec le prédicat du jeu. L’audit des transformations
récolte/fabrication converge sans cycle productif. Les 194 textures conservent
leurs transparences dans les deux palettes. Compilation autorisée : `compileDebugKotlin` seulement.
Les contrôles sur appareil restent nécessaires pour le placement et l’ergonomie.
