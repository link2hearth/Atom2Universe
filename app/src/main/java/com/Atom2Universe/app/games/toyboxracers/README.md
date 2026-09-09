# Toybox Racers : conduite libre et maison à trois niveaux

Le gameplay reste une conduite arcade libre : accélérateur, frein/marche arrière,
virages, glisse et Ruban Turbo. Les véhicules et le catalogue de modèles 3D sont
conservés, ainsi que les couleurs pastel et les circuits des huit pièces.

## Un seul monde actif à la fois

`ActiveWorldKind` distingue les deux systèmes de piste, jamais actifs en même
temps (rendu et collisions) :
- **LEGACY** : les circuits procéduraux historiques (8 pièces × 10 circuits,
  maison à seed) — jouables en course complète avec adversaires, mais non
  éditables.
- **CUSTOM** : un `ToyboxWorld` bâti dans l'éditeur de blocs — exploration
  libre uniquement (`ArcadeCar.sandboxMode`), sans tour ni adversaires.

Le menu pause unique (`menu/ToyboxWorldMenu.kt`) fusionne l'ancien menu de
course et l'ancien menu d'édition : bascule Éditer/Tester (mondes CUSTOM
uniquement), Charger (mondes intégrés, circuits classiques, créations du
joueur) et Sauvegarder/Sauvegarder une copie. Charger une création ou un
monde intégré en fait toujours une copie en mémoire — le fichier source
n'est jamais écrasé tant qu'on n'a pas explicitement choisi Sauvegarder.

## Maison

La maison à trois niveaux est jouable des deux côtés : comme circuit
classique ("Maison (circuit)" dans Charger, sans édition possible) et comme
monde d'éditeur complet ("Maison complète 3 étages", éditable). Elle ouvre
une scène continue, sans changement de carte :
- garage à Y = 0 ;
- séjour avec cuisine à Y = 26 ;
- mezzanine à Y = 52, avec un pont sur l'atrium ;
- rampes extérieures à l'est pour monter et à l'ouest pour redescendre.

Un ruban de 12 unités de large relie les trois niveaux en une boucle fermée.
Les planchers supérieurs entourent une ouverture centrale ; sortir du pont
permet réellement de tomber à l'étage inférieur. Le mobilier laisse la ligne
principale dégagée. La minimap souligne les portions du niveau du joueur.

`HouseGeometry` définit le parcours, les planchers, les murs et le mobilier.
Les planchers et murs utilisent les mêmes `RoomBox` pour le rendu et les
collisions. La maison est ouverte en coupe pour rester lisible en conduite.
L'identifiant historique `HOUSE_GROUND_FLOOR` reste inchangé pour les menus
et sauvegardes ; il ne signifie plus qu'il n'existe qu'un rez-de-chaussée.

La seed de `HousePlan` continue de régler les huit scènes indépendantes.
Elle ne génère pas cette nouvelle maison, dont le tracé est fixe.

## Coordonnées et contacts

X et Z sont horizontaux, Y est l'altitude absolue du monde. Le niveau bas
reste à zéro ; les étages ne modifient jamais `groundHeightAt()` globalement.
`PrototypeTrack.decksAt()` interroge toutes les couches de piste sous une
empreinte, avec les mêmes triangles que le maillage visible et des segments
finis. La recherche élimine d'abord les segments éloignés.

Chaque roue choisit un appui accessible en hauteur, indépendamment de la
progression du tour. Une dalle au-dessus de la voiture ne peut pas être
un appui. En vol, la suspension ne peut pas annuler le saut : les collisions
résolvent explicitement les franchissements du dessus, du dessous et des côtés.
La préférence de progression est bornée pour pouvoir retrouver un autre
étage après une exploration ou une chute.

Au sol, le châssis suit les appuis proches sans ressort vertical qui accumule
un rebond. Tangage et roulis restent amortis. Les véritables bords déclenchent
une chute avec un élan vertical borné ; la gravité arcade vaut 26 unités/s².
Une petite correction de trajectoire en vol fait tourner la vitesse sans
augmenter sa norme. Le moteur, la glisse et la charge de turbo exigent le sol.
Le vide du grand huit est ramené à environ 20 unités pour accompagner cette
gravité, au lieu de demander un long vol pour rejoindre la réception.

## Vérification

`PrototypeTrackTest` couvre les contacts sous les ponts, les plafonds, les
réceptions en pente, les niveaux de la maison, le dégagement du parcours et
la correction aérienne. Les anciens tests propres à la maison sur un seul
plan ont été remplacés.

La politique du dépôt autorise ici seulement `compileDebugKotlin` : aucun
APK, aucune installation et aucune exécution de tests Gradle. Les tests
ajoutés restent donc à exécuter localement. Le ressenti des bosses, la
lisibilité des étages et les parcours complets restent à valider sur appareil.

## Édition tactile des pistes

- Un appui long sélectionne une bande de piste, y compris au-dessus du sol et
  dans les zones des joysticks si le doigt reste immobile. Un appui simple choisit
  ensuite un point de cette bande. Glisser un point déforme la courbe sur son plan
  horizontal ; glisser une extrémité étire ou raccourcit la bande.
- Les points disponibles suivent la longueur réelle en 3D (environ un tous les
  huit unités, de 2 à 64 intervalles automatiques). Les points déjà déformés restent
  disponibles pour conserver la forme lors du redimensionnement. Une spline cubique
  relie les points ; le maillage se raffine selon la longueur et la courbure.
- La poignée jaune est active. Le bouton de point parcourt tous les points ;
  « Y + / Y − » règle uniquement son altitude. Les extrémités déjà raccordées
  suivent la modification. Les déplacements, rotations et dimensions restent en
  mémoire pendant l’édition : « Valider » enregistre une seule annulation pour
  l’ensemble des réglages. « Annuler » avant validation restaure l’état initial.
- Toucher un point du morceau puis « Point + » le divise à cet endroit
  (près du bout si une extrémité est sélectionnée), en conservant la courbe
  échantillonnée. « Prolonger » ajoute une bande de 20 unités suivant la tangente
  finale, à la même altitude et avec le même dévers.
- Les dimensions inférieures à deux unités, les largeurs aux deux extrémités
  et les orientations des bords du circuit importé sont conservées en JSON.
  Le rendu et les contacts utilisent les mêmes triangles, indépendamment du
  nombre de morceaux. Modifier une couleur ou une altitude ne déclenche plus
  un déplacement par accrochage automatique.
- Les déformations sont sauvegardées dans `bends` sans changer la version du
  monde : les anciens fichiers sans points intermédiaires restent lisibles.
  Affichage, sélection tactile et collisions utilisent le même maillage.

Vérification manuelle sur appareil : copier un circuit sinueux, passer plusieurs
fois Éditer → Tester, sauvegarder puis recharger ; vérifier les virages et les
petits segments. Sur une nouvelle création, prolonger une piste, déplacer le
raccord commun, modifier son altitude, ajouter un point, puis annuler. Vérifier
également un glissement interrompu et la conservation des annulations après
ouverture de la liste des créations. La conversion reste une création en mode
exploration, sans les adversaires ni les objectifs de la course procédurale.
Vérifier aussi une courbe en S, une bosse intermédiaire avec Y + / Y −,
l’étirement et le raccourcissement, puis sauvegarder/recharger et rouler dessus.
L’historique des réglages est sauvegardé à la validation, hors du fil UI, et non
à chaque appui sur une direction ou un angle. Une sauvegarde explicite valide
aussi les réglages ; les opérations distinctes d’ajout/suppression conservent
leur propre entrée. À vérifier : appuis répétés sur Y + / Y − en grille 1 cm,
validation, une seule annulation, puis réouverture de la création.
Les altitudes modifiées utilisent une interpolation cubique monotone : les
sommets sont arrondis sans dépasser la hauteur des points, et les extrémités
modifiées se raccordent avec une pente horizontale. Le maillage contrôle aussi
les quarts des intervalles pour détecter les profils en S, avec une tolérance
de 0,0025 unité. Rendu et collisions partagent ces subdivisions.
## Collisions des créations

### Styles et barrières de piste

Le [catalogue pastel](models/CATALOGUE_PASTEL.md) regroupe les idées par catégorie
et les 32 miniatures des deux premiers lots. Le menu **+** propose huit familles dédiées :
jouets, outils, ustensiles, rétro gaming, livres, figurines, fruits et décors de
piste. Les modèles utilisent les primitives low-poly et la palette commune.

Le bouton Aimant des outils conserve son état entre les ouvertures. Activé, il
rapproche les extrémités à moins de 3 unités en 3D et harmonise les tangentes,
la pente, la largeur et le dévers des deux bandes. Les bandes peuvent se rejoindre
début/fin ou dans le sens inverse. Une jonction qui exigerait un demi-tour est
ignorée. Le glissement des points raccordés est solidaire tant que l’aimant reste
actif ; désactivé, il permet de séparer une extrémité. Les changements font partie
de l’édition en cours et sont enregistrés avec « Valider ».
À vérifier : raccord en pente et en virage, largeurs différentes, deux extrémités
inversées, désactivation pour détacher, annulation et sauvegarde/rechargement.

Le panneau de piste propose Classique, Rallye terre, Prairie, Sable du désert,
Glace cristalline, Passerelle en bois et Arcade néon. Les motifs sont des éléments
de maillage plaqués sur la surface, sans images à charger. Chaque style propose
une largeur (respectivement 9, 7, 6, 12, 10, 5 et 8 unités), facultative pour
préserver les raccords existants. La couleur reste personnalisable.

Les barrières sont indépendantes : aucune, gauche, droite ou les deux. Elles
mesurent 1,3 unité de haut et 0,22 unité d’épaisseur et suivent chaque subdivision.
Le contact latéral tient compte de la hauteur et du franchissement entre deux
positions ; une voiture au-dessus des barrières peut toujours les survoler.
Les matériaux sont visuels : ils ne modifient pas les paramètres de conduite.
`style` et `barriers` sont conservés par la sauvegarde, la copie, la division et
le prolongement. Les anciens fichiers chargent Classique sans barrières.

À vérifier sur appareil : sept styles sur une courbe avec dévers, choix de largeur,
barrières à gauche/droite, choc rapide et glissement le long des barrières,
passage sous une piste surélevée, validation/annulation et rechargement.

La copie d’un circuit reprend les murs et meubles solides de `RaceLayouts.solids`.
Les parties solides des modèles 3D deviennent des volumes de collision distincts,
ce qui laisse libres les passages sous les tables et entre leurs pieds. Le test
latéral respecte leur rotation horizontale ; les formes arrondies restent
approximées par les boîtes de leurs parties, comme pour les circuits classiques.

`EditorVolumeIndex` regroupe les obstacles par cellules de 18 unités et met leurs
limites et triangles en cache. Les grands volumes disposent d’une liste séparée
pour éviter une grille gigantesque. Les triangles des pistes sont aussi préparés
au changement du circuit. La voiture ne reconstruit que les catégories modifiées,
sur le fil de simulation ; les déplacements intermédiaires en édition attendent
le retour au test. Rendu et simulation consomment le même instantané du monde.

Les anciens fichiers conservent leurs attributs `solid` : un bloc volontairement
non solide ne doit pas devenir un obstacle automatiquement. Pour une ancienne
copie créée avec les collisions de mobilier désactivées, activer « Solide » sur
les blocs concernés ou refaire une copie du circuit original. Les parties
solides des modèles 3D sont prises en compte aussi dans les anciennes créations.

À vérifier sur appareil : heurter un mur et un meuble après Éditer → Tester puis
sauvegarde/rechargement, rouler sur un plateau et sous une table, tourner un meuble,
et comparer la fluidité d’une maison chargée avec de nombreux objets. Les flancs
des rampes et des volumes inclinés restent exclus du test latéral existant afin
de ne pas empêcher la montée ; leur surface supérieure reste porteuse.

Les modèles placés disposent maintenant du commutateur « Solide / Décor ».
Il désactive toutes leurs collisions, ou réactive les parties prévues comme
solides (toutes les parties si le modèle ne définit aucun volume solide).
Le choix et les remplacements de couleurs sont enregistrés par instance,
conservés à la duplication et intégrés à la transaction validée avec « Valider ».
La palette présente les couleurs du modèle par surface décroissante : chaque
pastille ouvre `SimpleColorPickerDialog`, le sélecteur intégré à l’application.
La réinitialisation restaure les couleurs originales de cette instance.

L’appui long compare la distance des modèles, volumes et pistes à la caméra.
Toutes les parties visibles d’un modèle sont sélectionnables, même sans collision.
Le panneau affiche le nom de la pièce, masque les commandes de piste pour les
modèles et propose une échelle uniforme à la place des dimensions de bloc.

À vérifier sur appareil : reprendre un fruit posé par appui long, changer deux
couleurs avec le sélecteur intégré, valider et recharger ; désactiver puis réactiver
ses collisions, vérifier la duplication et l’annulation de ces modifications.
