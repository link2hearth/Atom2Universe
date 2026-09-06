# Toybox Racers : conduite libre et maison à trois niveaux

Le gameplay reste une conduite arcade libre : accélérateur, frein/marche arrière,
virages, glisse et Ruban Turbo. Les véhicules et le catalogue de modèles 3D sont
conservés, ainsi que les couleurs pastel et les circuits des huit pièces.

## Maison

Le bouton Maison ouvre une scène continue, sans changement de carte :
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
