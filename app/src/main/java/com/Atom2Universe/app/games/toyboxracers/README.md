# Toybox Racers : conduite libre et maison à trois niveaux

Le gameplay reste une conduite arcade libre : accélérateur, frein/marche arrière,
virages, glisse et Ruban Turbo. Les véhicules et le catalogue de modèles 3D sont
conservés, ainsi que les couleurs pastel et les circuits des huit pièces.

## Conduite façon kart

### Saut et dérapage tenu

Un bouton **SAUT** (tactile, `R1`/`L1`/`X` à la manette) fait sauter la voiture
sur place : environ un demi-tour de roue de haut, un peu moins d'une demi-seconde
en l'air, sans moteur ni charge pendant le vol. Il ne raccourcit donc aucun tour
à lui seul — c'est une porte d'entrée, pas un raccourci.

Tenir ce bouton et braquer engage un **dérapage tenu** dès que les roues
retouchent le sol. Tant qu'il est tenu :
- le côté est figé au moment de l'engagement ; pousser le stick à l'opposé
  élargit la courbe (braquage effectif de 0,28 à 1,0) mais ne change jamais
  de côté ;
- la voiture vise un travers choisi — 0,38 × vitesse en latéral — au lieu de
  simplement patiner. La glisse est stable, tenue et reproductible ;
- la charge du Ruban Turbo monte bien plus vite qu'en automatique (1,5 à 2,2
  secondes utiles par seconde réelle), suspendue mais jamais perdue en vol ;
- relâcher le bouton relance. Freiner, caler ou repasser sous 3,5 unités/s
  annule la charge sans relance.

Le Ruban Turbo **automatique** d'origine reste en place, mais seulement quand le
bouton n'est pas tenu : la conduite tactile sans bouton saut garde exactement le
comportement d'avant. Les deux entrées alimentent la même charge et la même
relance, jamais en même temps.

### Le saut et les contacts : deux pièges réglés

Sauter est la seule façon de décoller **roues au sol**, ce qui a mis au jour deux
défauts que rien n'exerçait avant :

- **Le châssis part enfoncé.** Au repos, il suit la *moyenne* des appuis de roues :
  sur une bosse, son plancher passe donc sous la surface qui le porte en son
  milieu — c'est voulu, la caisse enjambe le sommet. Mais en vol, toutes les
  questions « étais-tu au-dessus de cette dalle ? » répondaient alors non, et le
  premier contact était pris pour un choc de flanc. Le saut commence donc par
  poser la caisse sur le point le plus haut qu'elle enjambe (ses quatre roues et
  son milieu) : c'est le seul état d'où le vol est cohérent. Au sol, ce décalage
  était déjà toléré (`resolveGroundRoadCollision` accorde `MAX_SUPPORT_RISE`) ;
  seule la branche aérienne ne l'était pas.
- **Le circuit procédural existait encore en vol dans un monde de l'éditeur.**
  `resolveAirborneRoadCollision` et `resolveGroundRoadCollision` interrogeaient la
  piste LEGACY même en mode bac à sable, où elle est invisible. La voiture
  atterrissait sur une dalle qui n'est pas là — donc *au travers* de la piste
  construite dès qu'elle est plus haute. Les deux sont maintenant réservées à
  LEGACY, comme les murs de pièce et le mobilier l'étaient déjà : « un seul monde
  actif à la fois » vaut aussi en l'air.

### En rampe : deux règles que le saut a révélées

- **Le saut s'ajoute à la montée en cours.** En rampe, la suspension donne déjà à
  la caisse la vitesse verticale de la pente. Écraser cette valeur par
  l'impulsion revenait à *freiner* la montée au moment du saut : au-delà d'environ
  23°, la rampe monte plus vite que l'impulsion et passait donc par-dessus la
  voiture. La part héritée est bornée (9 unités/s), car la suspension recale la
  caisse d'un bloc sur une marche — sans plafond, une arête suffirait à envoyer
  la voiture en orbite.
- **Le plafond d'appui admissible suit le pas horizontal.** Une dalle au-dessus
  de la caisse ne peut pas la porter, sinon la voiture se ferait hisser sur un
  pont qu'elle survole. Mais le sol a pu *monter* sous elle pendant qu'elle
  avançait : la marge fixe de 2 cm refusait la surface d'une rampe et la voiture
  la traversait en vol. La marge vaut maintenant le pas horizontal réellement
  parcouru × 2 (soit ~63° de pente), plafonnée à 0,80 — sous la hauteur de la
  caisse (0,92), ce qui garantit qu'une dalle sous laquelle on peut passer ne
  devient jamais un appui.

### Accroche au sol

`Support` porte maintenant une matière en plus d'une hauteur. `grip` multiplie
l'adhérence des pneus, `drag` freine en unités/s² — deux effets distincts : la
glace fait glisser sans ralentir, le sable ralentit sans faire glisser.

- Styles de piste (`TrackStyle`) : Classique 1,00 · Bois 0,94 · Terre 0,82 ·
  Prairie 0,76 · Sable 0,70 · Glace 0,46 · Néon 1,06 · Boost 1,00. Les traînées
  valent respectivement 0 · 0,2 · 0,9 · 1,6 · 2,4 · 0 · 0 · 0. **Les matériaux
  ne sont donc plus seulement visuels.**
- L'accroche **n'est pas la vitesse**. Elle multiplie l'adhérence latérale et le
  couple moteur : le néon fait donc sortir des virages un peu plus vite, mais la
  vitesse de pointe reste plafonnée par `MAX_SPEED` pour toutes les matières.
  Seule une relance (Ruban Turbo ou bande Boost) lève ce plafond.
- Circuits classiques : le ruban de route vaut 1,00, un plateau de meuble 0,88,
  le plancher 0,74 — sauf sur les circuits de mobilier, où le plancher *est* la
  piste et garde 1,00, sans quoi le parcours prévu deviendrait une patinoire.
- `wheelGripScale()` multiplie le nombre de roues portantes par cette matière.
  Les deux facteurs datent de la fin du pas précédent, comme `groundedWheelCount`
  l'était déjà : un pas de retard sur un changement de sol est invisible,
  recalculer tous les appuis deux fois par image ne l'est pas.

### La bande Boost

`TrackStyle.BOOST` est une huitième matière, avec son propre habillage : des
chevrons dorés en marches vers l'avant sur un ruban violet profond. Elle se pose,
se raccorde, se courbe et se sauvegarde comme n'importe quelle autre bande — d'où
le choix d'un style de piste plutôt qu'un objet à part : tout l'outillage de
l'éditeur (largeur, dévers, points, aimant, JSON) marche déjà dessus.

Rouler dessus relance, sans gaz ni bouton. Une seule roue portante suffit :
effleurer le bord doit relancer, sinon la bande punirait la trajectoire propre
qui la longe. La relance emprunte la mécanique du Ruban Turbo — même courbe
d'attaque, même plafond de vitesse levé, même front `turboReleaseSerial` (donc
secousse de caméra, vibration, étincelles et traînée chaude sans une ligne de
plus).

La bande ne fait que **garantir un plancher** : `max` sur la puissance (×1,75)
et sur la durée (1,5 s). Un gros Ruban Turbo déjà lancé reste donc intact, et
enchaîner deux bandes ne cumule rien. Tant qu'une roue la touche, la durée est
maintenue à plein : la poussée dure autant que la bande, puis s'éteint sur la
courbe habituelle. Une bande juste avant un tremplin envoie donc réellement loin.

Un Ruban Turbo parfait (×1,80, jusqu'à 5 s) reste plus fort qu'une bande : la
bande gagne en immédiateté, la glisse gagne en durée. Rouler sur une bande
interrompt un dérapage tenu et en libère la charge — les deux relances ne
s'additionnent pas, la meilleure l'emporte.

### Manette

`input/RacerGamepad` traduit les événements, sans aucun état de jeu. Direction au
stick gauche ou à la croix, gaz à la gâchette droite ou `A`, frein/recul à la
gâchette gauche ou `B`, saut/glisse sur `R1`/`L1`/`X`, pause sur `Start`, départ
sur `Y`. Chaque commande a deux entrées et la plus engagée l'emporte, pour les
manettes sans gâchettes analogiques.

Les gâchettes sont **analogiques** jusqu'à la physique : `ArcadeCar.Input` porte
`throttle` et `brakeAmount` en plus des booléens, qui valent 1 quand seuls les
booléens sont renseignés — le tactile garde donc son tout-ou-rien exact.

Seuls les périphériques de source `GAMEPAD`/`JOYSTICK` sont détournés, et jamais
pendant l'édition : un clavier garde ses flèches et les panneaux de l'éditeur
restent atteignables au clavier.

### Animation du véhicule

Le maillage de la voiture du joueur est coupé en deux : `carBody()` sans roues et
`carWheel()`, dessinée quatre fois avec sa propre matrice. Les rivaux gardent le
maillage d'un seul tenant, sans animation. Les roues avant braquent (26° max,
commande du joueur plus contre-braquage du travers : elles pointent vers
l'extérieur en glisse) et les quatre tournent à la vitesse réelle. Une croix
d'enjoliveur rend cette rotation visible — un cylindre nu tourne sans qu'on le
voie.

La carrosserie seule reçoit ensuite l'assiette moteur (elle s'assoit aux gaz,
plonge au frein, se creuse au turbo), la gîte vers l'extérieur du virage, la
détente du saut et l'écrasement de la réception, dosé par la violence du choc.
`hopSerial` et `landingSerial` portent ces fronts jusqu'au rendu pour qu'il
n'ait pas à deviner l'instant. Tout cela est visuel et n'entre jamais en
physique. La caméra recule aussi avec la vitesse, ce qui fait sentir le turbo
même compteur au plafond.

### Ruban Turbo, l'effet

`render/TurboEffects` dessine maintenant **deux** bandes, une par roue arrière,
au lieu d'un tapis central. Elle s'affinent en vieillissant au lieu de
disparaître d'un bloc. Le ruban enregistre pendant la charge — sa couleur dit le
niveau — et pendant la relance, où il passe en traînée chaude.

Un seul tas de particules borné sert la poussière, les étincelles et les flammes :
elles ne diffèrent que par couleur, pesanteur et durée. Les étincelles ne sortent
qu'à partir du deuxième niveau — c'est le seul signal qui dise que relâcher le
bouton vaut désormais quelque chose — et partent des deux roues arrière vers
l'extérieur du virage.

### Vérification

`ArcadeCarDrivingTest` couvre le saut (hauteur bornée, retombée seule, fronts
signalés), l'engagement de la glisse et son côté figé, la relance unique au
relâchement, le fait qu'un dérapage ne crée **jamais** de vitesse, la gâchette
partielle, et l'accroche par matière (glace contre bitume). Deux cas gardent les
pièges de contact ci-dessus : un saut depuis un châssis volontairement enfoncé
doit décoller au-dessus de la surface enjambée, et un saut dans un monde de
l'éditeur posé haut ne doit jamais redescendre sous sa piste. Trois cas couvrent
la bande Boost : elle relance au simple contact, elle ne relance qu'une fois tant
qu'on reste dessus, et sa poussée s'éteint bien après l'avoir quittée. Deux cas
gardent la rampe, sur une pente de 31° : marteler le bouton saut en montée ne doit
jamais faire passer le plancher de la caisse sous la piste, et un saut lancé en
rampe doit monter *plus* que la rampe dès la première image. Ces tests roulent sur
une dalle plate construite pour eux : sur un circuit réel, une voiture qui tourne
en rond taperait le décor avant la fin de la mesure.

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
Glace cristalline, Passerelle en bois, Arcade néon et Ruban Boost. Les motifs sont
des éléments de maillage plaqués sur la surface, sans images à charger. Chaque
style propose une largeur (respectivement 9, 7, 6, 12, 10, 5, 8 et 8 unités),
facultative pour préserver les raccords existants. La couleur reste
personnalisable.

Les barrières sont indépendantes : aucune, gauche, droite ou les deux. Elles
mesurent 1,3 unité de haut et 0,22 unité d’épaisseur et suivent chaque subdivision.
Le contact latéral tient compte de la hauteur et du franchissement entre deux
positions ; une voiture au-dessus des barrières peut toujours les survoler.
Les matériaux règlent désormais l'accroche et la traînée : voir « Accroche au
sol » plus haut. Ils restent sans effet sur les collisions.
`style` et `barriers` sont conservés par la sauvegarde, la copie, la division et
le prolongement. Les anciens fichiers chargent Classique sans barrières.

À vérifier sur appareil : huit styles sur une courbe avec dévers, choix de largeur,
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
