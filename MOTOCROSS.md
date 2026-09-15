# Motocross : conduite et pistes modulaires

## Jouer

- À gauche : déplacer le pilote vers l'arrière ou l'avant.
- À droite : frein et gaz, indépendants de l'équilibre. On peut tenir plusieurs boutons.
- Après une chute : toucher la piste ou utiliser ↻ pour reprendre au checkpoint.
- Appui long sur ↻ : recommencer la même piste depuis le départ.
- Bouton à deux flèches : nouvelle piste aléatoire.
- À l'arrivée : toucher la piste pour un nouveau parcours, ou ↻ pour rejouer celui-ci.
- Ponts : prendre le tremplin pour rejoindre la piste haute ; un saut trop court
  retombe sur la piste basse. Certains ponts comportent plusieurs plateformes
  séparées par des sauts. Les voies se rejoignent avant le checkpoint.
- Loopings : prendre de l'élan et garder les gaz. La caméra cadre la boucle entière.
  Une vitesse insuffisante peut faire décrocher la moto au sommet.

Le numéro de piste, le dernier checkpoint, le temps et les fautes sont sauvegardés.
Le chrono commence au premier appui de conduite et s'arrête pendant une chute ou
quand le jeu perd le focus. Une reprise conserve le temps et compte une faute ;
recommencer depuis le départ remet le chrono et les fautes à zéro.

## Organisation du code

Dans `app/src/main/java/com/Atom2Universe/app/games/motocross/` :

- `MotocrossTrack.kt` : 24 séries de six à huit bosses et une portion de repos.
  Leur géométrie est calculée une seule fois, puis assemblée en 36 portions.
  La difficulté disponible augmente avec la distance. Des zones de repos sont
  insérées entre les obstacles ; les nouvelles structures ont leur propre élan.
  Les quatre dernières portions sont exclues du tirage pour limiter les répétitions.
  Les profils alternent petites/moyennes bosses, doubles, progressions et rythmes
  irréguliers. Les grosses bosses isolées ont été remplacées par ces enchaînements.
- `MotocrossStructures.kt` : trois variantes de ponts (un, deux ou trois tabliers)
  et des loopings de 6,5 à 9 mètres de rayon. Une portion de pont et une portion
  de looping apparaissent dans chaque groupe de huit portions, dès le premier groupe.
- `MotocrossBike.kt` : châssis rigide, deux suspensions à ressort amorti,
  traction arrière, freinage et déplacement du pilote. Coordonnées en mètres,
  Y vers le haut ; pas de temps fixe de 1/240 seconde.
- `MotocrossView.kt` : boucle synchronisée à l'écran, dessin Canvas de la moto
  et du pilote, caméra, décor, checkpoints et sauvegarde.
- `MotocrossActivity.kt` : commandes multitouch, affichage des statistiques,
  pause et attribution des neutrinos.

La fourche et le bras oscillant suivent la compression réelle des suspensions.
Les rayons suivent la vitesse des roues. Les genoux et les coudes sont calculés
pour relier le corps mobile aux mains et aux pieds qui restent sur leurs appuis.
Le casque dessiné et sa collision utilisent la même position.

## Ajouter une portion

Ajouter un appel à `rhythm` dans `MODULES` : type, difficulté (0 à 2), puis
hauteurs successives des bosses. `stride` règle l'espacement de base ; les bosses
plus hautes reçoivent automatiquement une montée et une réception plus longues.
`launchEvery` permet de conserver une lèvre montante sur certaines bosses moyennes
pour les saltos. La géométrie reste fixe et précalculée, indépendamment du tirage.

Pour une géométrie personnalisée avec des couples X/Y :
Les X doivent être strictement croissants. Commencer à (0, 0), terminer à Y = 0
et conserver au moins quatre mètres plats à chaque extrémité. La moto réapparaît
à deux mètres de la fin d'une portion : cette zone doit donc rester plate.

Un saut contient sa prise d'élan et sa réception. Ne pas les tirer séparément
au hasard. L'interpolation conserve les hauteurs et a une tangente horizontale
aux nœuds, sauf aux lèvres des tremplins qui restent montantes pour lancer le saut.
Le dessin et les collisions emploient exactement les mêmes segments.
Le sol reste continu, sans trou obligatoire : on peut aborder les obstacles
prudemment. Le seed reproduit l'ordre pour une même version de la bibliothèque.

## Surfaces suspendues et loopings

La piste basse garde son profil continu. Les ponts et les boucles ajoutent des
segments orientés indépendants : il peut donc y avoir plusieurs surfaces au même X.
Un index par tranches de huit mètres limite la recherche des contacts proches.
Le moteur, la gravité, la force de rotation, les dimensions de la moto et le
cadrage de base conservent les réglages validés avant cet ajout.

Une roue prend appui sur le côté roulant d'un tablier quand sa suspension se
trouve de ce côté. Elle ne s'accroche pas à un pont situé au-dessus de la moto.
Les extrémités ne prolongent pas la surface dans le vide. Une moto qui passe sous
un tablier ignore ses collisions jusqu'à l'avoir entièrement dépassé, y compris
sa rampe descendante de sortie. Les roues, le cadre et le casque restent soumis
aux collisions de la piste basse. Une réception par-dessus conserve l'appui et
les collisions du tablier. Les piliers et les croisillons sont des
éléments du décor en arrière-plan ; la bande de roulement est la surface solide.

Les normales d'un looping pointent vers son centre : les suspensions fonctionnent
sur les murs et au plafond. Le croisement au pied est traité comme deux voies
à des profondeurs différentes : l'entrée active la boucle à sa tangente basse,
puis un tour ramène sur la voie de sortie. Ce changement de voie ne modifie
ni position, ni vitesse, ni orientation. Aucun aimant ni animation forcée ne
maintient la moto au plafond. Cette gestion de profondeur permet de passer
derrière/devant les branches du pied sans collision entre voies superposées.

Les checkpoints des structures restent sur le plat, après la réunion des voies.
Le compteur de progression conserve le maximum atteint : revenir vers la gauche
pendant un looping ne peut pas recréditer de récompense.

## Récompenses

Les montants viennent de `NeutrinoRewards.perDistance`. Seule la distance maximale
atteinte sur la piste courante fait progresser les gains. Le nombre de tranches
déjà créditées est sauvegardé : les reprises et les replays de cette piste ne
recréditent pas les mêmes passages. Une nouvelle piste ouvre une nouvelle course.
Les anciennes sauvegardes de record ne sont pas effacées.

## Vérification sur appareil

Le cadrage montre au minimum 32 mètres en largeur et 16 en hauteur, puis recule
avec la vitesse et la hauteur du saut. Les profils utilisent une échelle fixe
de 3,2 en longueur et 4,8 en hauteur par rapport aux formes initiales.
Le moteur développe une force maximale de 36, avec une courbe quadratique qui
conserve du couple à mi-régime et s'annule à 28 m/s. L'adhérence vaut 2,2 fois
la charge sur la roue ; le freinage maximal vaut 36 par roue. La force réellement
transmise reste limitée par l'adhérence, et le moteur ne pousse jamais en vol.
En vol, le couple de contrôle est de 22 et la vitesse angulaire est limitée à
10 rad/s (environ 1,6 tour/s). Relâcher l'équilibre amortit la rotation ;
appuyer dans le sens opposé permet de la contrer. Les commandes au sol gardent
leur force initiale.

La vérification automatisée autorisée dans ce dépôt est `compileDebugKotlin`.
La compilation ne valide pas le ressenti de conduite. Lors de l'essai manuel :

1. Tenir les gaz sur le plat, freiner, puis combiner gaz et équilibre.
2. Vérifier les réceptions sur une roue, le mouvement des suspensions et les figures.
3. Tenir frein et gaz ensemble en saut : la gravité doit continuer à agir.
4. Chuter après un drapeau ; reprendre et retrouver le même obstacle.
5. Essayer ↻ court, ↻ long et nouvelle piste : vérifier les remises à zéro attendues.
6. Passer en paysage, quitter/revenir et vérifier l'absence de commande bloquée.
7. Après une tranche récompensée, revenir en arrière ou rejouer : pas de gain doublé.
8. Atteindre un pont par son tremplin, puis essayer un saut trop court et le passage
   inférieur jusqu'au bout de la rampe descendante sans collision ; vérifier aussi
   une réception sur le second tablier et une chute quand on roule dessus.
9. Entrer dans un looping avec de l'élan et les gaz, vérifier le plafond et la sortie.
   Réessayer en freinant dans la montée pour vérifier le décrochage naturel.
10. Vérifier que le cadrage conserve la boucle entière en portrait et en paysage,
    et qu'une chute dans une structure reprend au checkpoint précédent.
