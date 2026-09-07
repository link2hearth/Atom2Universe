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

- Toucher une piste la sélectionne ; les deux poignées correspondent au début
  et à la fin. Glisser une poignée déplace cette extrémité sur son plan horizontal.
- La poignée jaune est active. « Extrémité » alterne entre début et fin ;
  « Y + / Y − » règle uniquement son altitude. Les extrémités déjà raccordées
  suivent la modification. Un glissement complet compte comme une seule annulation.
- Toucher une zone du morceau puis « Point + » le divise près de cette zone
  et sélectionne le point ajouté. « Prolonger » ajoute une section de 20 unités
  à la fin du morceau sélectionné, à la même altitude.
- Les dimensions inférieures à deux unités, les largeurs aux deux extrémités
  et les orientations des bords du circuit importé sont conservées en JSON.
  Le rendu et les contacts utilisent les mêmes triangles, indépendamment du
  nombre de morceaux. Modifier une couleur ou une altitude ne déclenche plus
  un déplacement par accrochage automatique.

Vérification manuelle sur appareil : copier un circuit sinueux, passer plusieurs
fois Éditer → Tester, sauvegarder puis recharger ; vérifier les virages et les
petits segments. Sur une nouvelle création, prolonger une piste, déplacer le
raccord commun, modifier son altitude, ajouter un point, puis annuler. Vérifier
également un glissement interrompu et la conservation des annulations après
ouverture de la liste des créations. La conversion reste une création en mode
exploration, sans les adversaires ni les objectifs de la course procédurale.
## Collisions des créations

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