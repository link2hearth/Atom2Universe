# Toybox Racers — plan et suivi du projet

> Jeu de course 3D arcade pour Atom2Universe — document vivant créé le 4 septembre 2026.

## État du projet

**Phase actuelle :** P3 jouable et P4 enrichie : chambre meublée, ours assis retravaillé et catalogue de 49 décors pour les futures pièces et l'extérieur — validation et équilibrage sur téléphone requis.

Le catalogue, les identifiants et les conventions de placement sont décrits dans
[TOYBOX_DECORS.md](TOYBOX_DECORS.md).

| État | Signification |
|---|---|
| `[ ]` | À faire |
| `[~]` | En cours |
| `[x]` | Terminé et vérifié |
| `[!]` | Bloqué ou décision nécessaire |

### Prochaine étape recommandée

Le jeu démarre en **Libre**, sans adversaires ni compte à rebours. Tester la conduite et la mini-carte (triangle blanc : joueur et cap ; carré crème : départ). Choisir une difficulté puis toucher **Course** pour lancer les trois tours ; **Libre** permet de quitter le défi.

Sur téléphone, vérifier la pause avec **Ⅱ** ou Retour, puis passer dans une autre application en maintenant GAZ : au retour, le jeu doit attendre **Reprendre**, sans accélérateur bloqué ni temps écoulé pendant la pause. Vérifier aussi la pause pendant le compte à rebours et le turbo.

Tester ensuite une course complète en difficulté **Arcade** : départ verrouillé, cinq adversaires et points colorés sur la carte, classement, trois tours, arrivée, record et nouvelle course. Essayer Détente et Champion, puis vérifier qu'une sortie de piste ou un passage à contresens ne valide pas abusivement un tour. Contrôler la lisibilité du HUD et des commandes sur un petit écran paysage.

---

## Identité

### Titre de travail

**Toybox Racers**

Le nom est naturel en anglais, facile à retenir et décrit l'idée sans reprendre l'identité d'une licence existante. Il reste provisoire jusqu'à la vérification du nom avant publication.

### Promesse

De minuscules voitures jouets disputent des courses pleines de courbes dans les pièces d'une maison transformées par l'imagination : tapis devenus collines, livres devenus tunnels, crayons devenus barrières et blocs de construction devenus tribunes.

### Piliers

1. **Le plaisir dans les virages** — conduite souple, dérapage lisible et relance satisfaisante.
2. **Un monde miniature attachant** — objets domestiques surdimensionnés et décors racontant une petite histoire.
3. **Une 3D pastel originale** — formes simples, silhouettes rondes, couleurs mates et excellente lisibilité sur téléphone.
4. **Des courses courtes et rejouables** — parties de deux à quatre minutes, immédiatement accessibles.
5. **Une identité propre** — aucune voiture, piste, musique, interface ou mécanique copiée d'une licence commerciale.
6. **Un bac à sable miniature** — la piste propose un parcours, mais le joueur reste libre de quitter la route et d'explorer la pièce.

### Signature de gameplay

Le dérapage dessine derrière la voiture un **ruban lumineux pastel**. Maintenir proprement la courbe remplit progressivement le ruban ; redresser la voiture le transforme en petite impulsion. Cette mécanique, nom de travail **Ruban Turbo**, doit devenir la signature visuelle et ludique du jeu.

---

## Direction artistique

### Ton visuel

- Mignon, chaleureux et légèrement fantaisiste.
- Palette pastel : menthe, pêche, lavande, jaune crème, bleu ciel et rose framboise.
- Voitures compactes aux proportions de jouets : grandes roues, carrosserie courte, visage suggéré uniquement par les formes des phares.
- Matériaux mats proches du bois peint, du plastique doux et du caoutchouc.
- Géométrie low-poly propre, avec arêtes adoucies dans la silhouette mais facettes encore visibles.
- Éclairage simple de fin d'après-midi et ombres douces stylisées.
- Pas de photoréalisme, de marques réelles ou de ressemblance directe avec un film ou un jeu existant.

### Fabrication des ressources

**Règle du projet : aucune ressource graphique, sonore ou 3D téléchargée.**

Les ressources seront produites spécialement pour Toybox Racers :

- modèles 3D simples construits par code à partir de boîtes, prismes, cylindres et maillages originaux ;
- piste générée depuis une courbe paramétrique ;
- objets domestiques assemblés à partir de primitives réutilisables ;
- icônes, motifs et illustrations 2D générés ou dessinés pour le projet ;
- bruitages synthétisés ou enregistrés/créés spécifiquement ;
- musique originale seulement, dans une phase ultérieure.

Un fichier d'inventaire devra accompagner les assets et indiquer pour chacun : méthode de création, date, auteur/générateur et éventuelles retouches. Aucun fichier externe ne doit être ajouté sans décision explicite et vérification de sa licence.

### Premier décor : La Chambre Arc-en-ciel

Une chambre d'enfant générique et originale, vue à l'échelle d'une voiture miniature :

- départ sur un tapis moelleux ;
- grandes courbes autour de cubes colorés ;
- passage sous un pont fait de livres ;
- épingle autour d'un pot à crayons ;
- petite montée sur une règle inclinée ;
- tunnel sous le bord du lit ;
- pente permettant de rouler sur le lit puis de rejoindre le bureau ;
- tremplin permettant de quitter la hauteur et de retomber sur le circuit au sol ;
- arrivée au milieu d'une arche de blocs.

Le premier prototype utilisera seulement le sol, quelques cubes et des barrières. Le mobilier détaillé arrivera après validation de la conduite.

---

## Gameplay cible

### Conduite

- Accélérateur manuel : sans pression sur **GAZ**, la voiture reste immobile puis ralentit naturellement.
- Direction tactile gauche/droite ; commandes suffisamment grandes pour ne pas masquer la piste.
- Frein servant aussi à amorcer un dérapage.
- Adhérence arcade calculée, sans simulation mécanique réaliste des quatre roues.
- Hors-piste libre avec seulement une très légère différence de friction, sans plafond de vitesse ni retour forcé.
- Collisions souples : perte d'élan et léger rebond, jamais d'arrêt brutal prolongé.
- Caméra arrière élastique qui anticipe légèrement les courbes.
- Marche arrière simple sur le bouton FREIN lorsque la voiture est presque arrêtée ; vitesse limitée et aucune boîte de vitesses complexe.

### Exploration et format d'une course

- L'exploration libre est le mode par défaut du prototype : aucun tour n'est imposé.
- Le circuit sert de terrain de jeu pour les courbes, pentes et sauts.
- Le replacement au départ est une action manuelle de secours, jamais automatique.
- Les courses structurées restent prévues comme défis facultatifs ultérieurs.

- 6 voitures au total pour la première version complète.
- 3 tours courts.
- Durée cible : 2 à 4 minutes.
- Départ animé très bref.
- Classement en temps réel et résultat final.
- Trois niveaux d'IA obtenus par anticipation, trajectoire et tolérance aux erreurs — pas par triche de vitesse excessive.

### Progression envisagée

- Records locaux par circuit et difficulté.
- Médailles selon la position et le temps.
- Couleurs de carrosserie à débloquer.
- Récompenses en neutrinos, ajoutées uniquement lorsque la boucle de course est stable.
- Pas de boutique interne propre au jeu dans la première version.

### Hors périmètre initial

- Multijoueur réseau ou écran partagé.
- Monde ouvert.
- Éditeur de circuits destiné au joueur.
- Dégâts réalistes.
- Véhicules sous licence.
- Objets offensifs façon karting dans le MVP.
- Cinématiques complexes.

---

## Choix techniques

### Intégration dans Atom2Universe

- Kotlin natif, package prévu : `com.Atom2Universe.app.games.toyboxracers`.
- Activités basées sur le système de thème existant.
- Rendu avec `GLSurfaceView` et OpenGL ES 3.0.
- Réutilisation possible des **principes techniques génériques** déjà éprouvés dans Cave World (cycle de rendu, shaders, buffers, caméra), sans coupler les deux jeux.
- Nouveau moteur de course contenu dans son propre dossier afin de ne pas fragiliser Cave World.
- Interface Android superposée au rendu 3D lorsque cela simplifie l'accessibilité et la traduction.
- Chaînes ajoutées aux 14 langues seulement au moment de l'intégration visible dans le hub.

### Organisation prévue

```text
games/toyboxracers/
├─ ToyboxRacersActivity.kt
├─ ToyboxRacersGLView.kt
├─ ToyboxRacersRenderer.kt
├─ game/       # boucle, états de course, tours, classement
├─ driving/    # véhicule arcade, collisions, Ruban Turbo
├─ track/      # spline, largeur, checkpoints, génération du maillage
├─ ai/         # trajectoire, anticipation, dépassements simples
├─ render/     # shaders, caméra, meshes, ombres et effets
├─ models/     # générateurs de voitures et objets miniatures
├─ audio/      # moteur, dérapage, impacts, musique
└─ data/       # paramètres des voitures et circuits
```

### Architecture de simulation

- Simulation à pas fixe, cible `1/60 s`, indépendante du nombre d'images affichées.
- Position et orientation libres dans le monde : la piste mesure seulement la progression et le hors-piste, elle ne dirige jamais la voiture.
- Circuit décrit par une trajectoire 3D fermée : centre, largeur, pente et inclinaison.
- Altitude traitée comme une donnée de piste fondamentale : sol, rampes, surfaces de meubles et portions aériennes.
- Les sauts possèdent une zone sans chaussée ; la voiture y suit une trajectoire verticale balistique avant la réception.
- Maillage de piste généré une fois au chargement puis envoyé au GPU.
- Collisions du décor limitées à des volumes simples.
- IA suivant une ligne de course échantillonnée avec petites variations contrôlées.
- Interpolation visuelle entre deux pas de simulation afin de conserver un mouvement fluide.

### Objectifs de performance

- 60 FPS visés ; mode dégradé stable à 30 FPS si nécessaire.
- Compatible avec le minimum Android actuel du projet.
- Très peu de changements de shaders et de matériaux.
- Géométrie instanciée ou regroupée pour les objets répétés.
- Ombres stylisées peu coûteuses : blob shadows au MVP, vraie ombre directionnelle seulement si le budget GPU le permet.
- Aucun objet temporaire créé à chaque image dans la boucle critique.

---

## Feuille de route

### P0 — Cadrage

- [x] Choisir le titre de travail **Toybox Racers**.
- [x] Fixer la vision : voitures jouets, maison géante, pastel, vraie 3D.
- [x] Fixer l'orientation : virages, dérapage et relance plutôt que vitesse pure.
- [x] Interdire les ressources tierces téléchargées.
- [x] Créer ce document de suivi.
- [ ] Valider définitivement le titre et la signature **Ruban Turbo**.

**Critère de sortie :** identité et périmètre du prototype acceptés.

### P1 — Prototype de conduite (« piste grise »)

- [x] Créer le package, l'activité de prototype et une tuile de test dans le hub des jeux.
- [x] Initialiser `GLSurfaceView` et le rendu OpenGL ES 3.0.
- [x] Afficher une voiture procédurale très simple.
- [x] Générer une grande piste fermée en huit avec montée, croisement vertical, tremplin central, vide et réception.
- [x] Ajouter caméra arrière, direction, accélérateur manuel et frein.
- [x] Garantir que ni le mouvement ni la rotation ne peuvent être produits par la piste.
- [x] Déplacer le tremplin et la réception sur une longue ligne droite.
- [x] Lisser visuellement l'assiette de la voiture au décollage et à la réception pour supprimer la secousse perçue comme un lag.
- [x] Agrandir le circuit de 79,8 à environ 714 unités, élargir la route et réduire fortement la voiture relativement à la route.
- [x] Faire distinguer au moteur la branche haute et la branche basse grâce à l'altitude et à la continuité de progression.
- [x] Donner une épaisseur visible à la piste : dessous sombre, flancs et extrémités du tremplin.
- [x] Autoriser le passage sous la branche haute sans attraction ni téléportation sur sa surface.
- [x] Faire correspondre la collision au volume de la dalle, y compris dessous, flancs et extrémités.
- [x] Remplacer le lancement scripté du saut par une sortie physique du tremplin suivie par la gravité.
- [x] Retirer les volumes provisoires de lit/bureau tant que leurs collisions ne sont pas implémentées.
- [x] Corriger le sens des deux commandes de direction après essai sur téléphone.
- [x] Passer le prototype en exploration libre : aucun retour forcé et hors-piste presque aussi rapide que la route.
- [x] Corriger le maintien multitouch de GAZ pendant que le joueur dirige ou relâche un autre bouton.
- [x] Empêcher le tremplin de déclencher des petits sauts lorsque la voiture roule à contresens.
- [x] Faire chuter naturellement la voiture lorsqu'elle quitte une route surélevée.
- [x] Remplacer le compteur de tours visible par l'indication **Libre**.
- [x] Ajouter adhérence latérale, amorce de dérapage et ralentissement hors piste.
- [x] Ajouter limites de piste et replacement manuel.
- [x] Afficher vitesse, tour, temps et état du saut de manière provisoire.
- [x] Gérer pause/reprise, orientation paysage et bouton Retour.
- [x] Vérifier hors Android la simulation fixe, le bouclage des tours et le déclenchement du saut.
- [x] Ajouter des tests unitaires permanents pour la verticalité, le vide, le saut, la réception et les tours.
- [x] Compiler l'application complète avec `compileDebugKotlin` dans l'environnement utilisateur.
- [x] Essayer sur téléphone et valider la base de gameplay, la direction, la pente, le saut et la caméra.

**Critère de sortie :** conduire seul pendant cinq minutes reste fluide, compréhensible et amusant.

### P2 — Ruban Turbo et sensation de conduite

- [~] Détecter un dérapage intentionnel plutôt qu'un simple tête-à-queue : déclenchement automatique en virage selon direction, vitesse, mouvement avant, angle de glisse et confirmation temporelle ; ressenti téléphone à valider.
- [~] Faire croître la charge selon angle, vitesse et durée : réserve continue en secondes efficaces, courbe peu rentable au début puis plafonnée progressivement ; cadence à régler sur téléphone.
- [~] Dessiner le ruban pastel et ses trois niveaux visuels : menthe, lavande et jaune avec largeur/opacité croissantes ; lisibilité et performances à valider sur téléphone.
- [~] Déclencher une impulsion lisible au redressement : durée continue jusqu'à cinq secondes, attaque proportionnelle au ruban et retour courbe à la vitesse normale ; puissance à régler.
- [~] Ajouter vibrations légères, poussière pastel et mouvements de caméra : retours aux paliers et à la relance implémentés ; confort à valider.
- [~] Régler les collisions pour conserver le rythme de course : murs et jouets conservent au moins 58 % de l'élan ; comportement à tester en jeu.
- [~] Stabiliser la conduite pour les virages serrés : braquage lissé (les boutons ne donnent que -1/0/+1), rotation plafonnée par l'adhérence, rappel d'alignement contre le tête-à-queue et regain d'adhérence pied levé ou au frein ; réglages à valider sur téléphone.
- [~] Supprimer la vibration de l'image sur écran 120 Hz : position et cap de la voiture et des rivaux interpolés entre deux pas, amortissements caméra et assiette exprimés par seconde ; à confirmer sur tablette.
- [~] Tester plusieurs tailles de commandes tactiles : trois profils adaptatifs ajoutés pour écrans compacts, moyens et larges ; essais physiques à faire.

**Critère de sortie :** le joueur cherche spontanément les courbes pour préparer ses relances.

### P3 — Première course complète

- [~] Ajouter checkpoints, sens de circulation, tours et arrivée : quatre validations ordonnées par tour, détection du mauvais sens et arrivée au troisième tour implémentées ; essai réel requis.
- [~] Créer une ligne de course pour l'IA : trajectoire centrale anticipée avec décalages latéraux et saut procédural implémentée ; trajectoire à observer.
- [~] Ajouter cinq adversaires avec erreurs et personnalités simples : rythmes, lignes et ralentissements périodiques distincts implémentés ; équilibrage requis.
- [~] Calculer le classement sans saut incohérent de position : progression continue, rejet des grands sauts de projection, marge spatiale et confirmation temporelle implémentés.
- [~] Ajouter compte à rebours, faux départ impossible et écran de résultat : simulation joueur verrouillée avant le départ, panneau place/temps/rejouer implémenté.
- [~] Créer trois difficultés équitables : Détente, Arcade et Champion règlent anticipation, allure, prudence et fréquence des erreurs sans boost caché.
- [~] Sauvegarder les meilleurs temps : meilleur chrono séparé par difficulté dans les préférences locales.

**Critère de sortie :** une course de trois tours peut être gagnée, perdue, recommencée et enregistrée correctement.

### P4 — Direction artistique jouable

- [ ] Créer une voiture jouet finale et plusieurs variantes originales.
- [ ] Définir la palette officielle et les matériaux.
- [~] Construire le décor **La Chambre Arc-en-ciel** : sol, tapis, parquet, murs, jouets, ours assis, porte, deux fenêtres, rideaux, bureau, bibliothèque, étagères et lit ajoutés ; validation téléphone restante.
- [x] Ajouter des limites de pièce visibles et solides pour contenir l'exploration libre.
- [x] Créer une première collection de jouets low-poly originaux : cubes, ours, train et toupie.
- [ ] Ajouter éclairage, blob shadows et arrière-plan de pièce.
- [ ] Créer HUD, icône et écran de sélection originaux.
- [ ] Remplacer chaque élément temporaire du prototype.
- [~] Tenir à jour l'inventaire de provenance des assets : mobilier et catalogue documentés dans `TOYBOX_DECORS.md`.

**Critère de sortie :** aucune ressource temporaire ou externe ne subsiste et une capture d'écran est immédiatement identifiable comme Toybox Racers.

### P5 — Audio et finition

- [ ] Créer un moteur jouet expressif dont la hauteur suit le régime simulé.
- [ ] Créer sons de pneus, chocs, turbo, compte à rebours et interface.
- [ ] Ajouter une musique originale en boucles courtes.
- [ ] Mixer les sons pour rester agréables sur haut-parleur de téléphone.
- [ ] Ajouter options de volume, vibration et commandes.
- [ ] Ajouter tutoriel visuel très court.

**Critère de sortie :** le jeu est compréhensible sans texte long et chaque action importante possède un retour sonore/visuel.

### P6 — Intégration Atom2Universe

- [x] Ajouter une première tuile fonctionnelle au hub des jeux.
- [x] Déclarer l'activité dans le manifeste et créer la couleur et l'icône vectorielle de la tuile.
- [x] Traduire le titre et la description de la tuile dans les 14 langues.
- [ ] Remplacer les textes provisoires du jeu et traduire toute l'interface finale dans les 14 langues.
- [ ] Définir les récompenses via `NeutrinoRewards`, source unique de vérité.
- [ ] Ajouter Toybox Racers au résumé des récompenses.
- [ ] Brancher les statistiques communes pertinentes.
- [ ] Documenter le jeu dans `GAMES_NEUTRINOS.md`.
- [ ] Vérifier la compilation avec `./gradlew compileDebugKotlin`.

**Critère de sortie :** intégration complète sans régression de compilation ni incohérence de récompense.

### P7 — Contenu ultérieur

- [ ] Étudier un deuxième circuit dans la cuisine ou le salon.
- [ ] Étudier obstacles mobiles non offensifs.
- [ ] Étudier courses contre-la-montre et fantôme local.
- [ ] Étudier capacités propres aux carrosseries sans déséquilibrer la course.
- [ ] Étudier objets de course originaux seulement après validation du jeu de base.

---

## Risques et garde-fous

| Risque | Garde-fou |
|---|---|
| Conduite techniquement correcte mais peu amusante | Valider P1 et P2 avant tout gros décor. |
| Projet trop grand | Un circuit, une voiture de base et un mode course jusqu'à P4. |
| Ressemblance excessive avec une licence connue | Formes, noms, UI, circuits et mécanique-signature originaux. |
| Mauvaises performances | Profilage dès la piste grise, géométrie simple et simulation fixe. |
| IA artificielle ou injuste | Trajectoire visible, erreurs paramétrées, pas de téléportation ni boost caché excessif. |
| Assets dont l'origine devient floue | Inventaire obligatoire et aucun téléchargement externe. |
| Régression dans Cave World | Code autonome ; réutiliser des idées ou petites abstractions stables, pas son renderer entier. |
| Trop de textes à traduire pendant l'expérimentation | Prototype interne d'abord, i18n complète à P6. |

---

## Décisions à conserver

| Date | Décision | Raison |
|---|---|---|
| 2026-09-04 | Titre de travail : **Toybox Racers** | Anglais naturel, évocateur et mémorisable. |
| 2026-09-04 | Vraie 3D low-poly native | Correspond au rendu polygonal recherché et aux compétences OpenGL déjà présentes dans l'application. |
| 2026-09-04 | Kotlin + OpenGL ES 3.0 | Intégration directe dans Atom2Universe sans embarquer un moteur externe. |
| 2026-09-04 | Conduite arcade cinématique | Le plaisir et la maîtrise des courbes priment sur la simulation réaliste. |
| 2026-09-04 | Ruban Turbo comme signature | Rend le dérapage lisible et donne une identité propre au jeu. |
| 2026-09-04 | Aucun asset téléchargé | Cohérence artistique et provenance maîtrisée. |
| 2026-09-04 | Décor initial : chambre miniature | Objets simples à modéliser et échelle immédiatement compréhensible. |
| 2026-09-04 | Circuits réellement verticaux | Les meubles servent de surfaces de course ; pentes et sauts font partie du moteur dès P1. |
| 2026-09-04 | Conduite libre dans le monde | Le joueur contrôle seul le mouvement et le cap ; la piste ne fait que mesurer sa position. |
| 2026-09-04 | Accélérateur manuel | La voiture ne doit jamais partir seule et le joueur gère son allure avant les courbes. |
| 2026-09-04 | Tracé en huit à deux niveaux | Le saut traverse le centre sur la branche haute tandis que l'autre passage reste au sol. |
| 2026-09-04 | Aucun gros décor sans collision | Un meuble ne doit jamais sembler solide si la voiture peut le traverser. |
| 2026-09-05 | Exploration libre comme mode principal | Le circuit est un jouet dans la pièce, pas une frontière obligatoire. |
| 2026-09-05 | Replacement strictement manuel | Le jeu ne reprend jamais le contrôle de la position du joueur. |
| 2026-09-05 | Le braquage est plafonné par l'adhérence | Tourner une voiture plus vite que ce que ses pneus tiennent ne la fait pas tourner : ça la met en travers. La rotation du nez est donc bornée à `adhérence / vitesse`, ce qui donne un braquage vif en épingle et calme la voiture à pleine allure — au lieu de l'inverse. |
| 2026-09-05 | Lever le pied rend de l'adhérence | C'est le réflexe naturel du joueur quand la voiture part : il doit être récompensé, jamais puni. |
| 2026-09-05 | Rendu interpolé entre deux pas de simulation | L'écran de la tablette affiche 120 images par seconde, la simulation en calcule 60 : une image sur deux ne montrait aucun mouvement et l'autre un saut double. Le cap de la caméra n'étant pas amorti, ce battement à 60 Hz se voyait comme une vibration de l'image. La simulation garde son pas fixe déterministe ; seul l'affichage interpole. |
| 2026-09-05 | Deux projections, jamais mélangées | `project` répond « où en est le tour », `projectForCollision` répond « quelle dalle je touche ». Au croisement elles désignent volontairement des branches différentes : croiser leurs résultats dans un même calcul d'altitude téléporte la voiture. |

---

## Journal de développement

### 2026-09-04 — Création du projet

- Vision générale définie avec le propriétaire du projet.
- Nom de travail choisi : **Toybox Racers**.
- Première feuille de route créée.
- P1 démarrée : renderer OpenGL ES 3.0, piste 3D et conduite arcade créés.
- Tuile Toybox Racers ajoutée au Game Hub pour permettre les essais sur téléphone.
- Prototype doté d'une montée au niveau des meubles, d'un tremplin, d'un vide et d'une réception au sol.
- Première voiture jouet et volumes de décor entièrement procéduraux, sans asset externe.
- Test de simulation accéléré réussi : piste de 79,8 unités, saut déclenché et tours bouclés.
- Tests unitaires de non-régression ajoutés dans `PrototypeTrackTest`.
- Après le premier essai sur téléphone : suppression du guidage automatique implicite, ajout du bouton GAZ, saut replacé sur une ligne droite et échelle générale multipliée par environ 2,5.
- Nouvelle simulation accélérée réussie : immobilité sans commande, cap inchangé sans direction, piste de 202,9 unités, tours, décollage et réception validés.
- Deuxième retour téléphone : commandes inversées corrigées et faux bureau vert retiré.
- Circuit remplacé par un huit de 714,2 unités. Le premier passage central est au sol ; le second accueille en ligne droite le tremplin et le saut.
- Projection 3D sécurisée au croisement par l'altitude et la continuité de progression, afin de ne pas confondre les deux branches.
- Simulation du nouveau huit réussie : plusieurs tours, décollage et réception validés sans guidage automatique de la voiture.

### 2026-09-05 — Orientation bac à sable

- Plafond de vitesse hors piste supprimé ; friction supplémentaire réduite de 6 à 0,65.
- Aucun retour automatique : le bouton **DÉPART** est la seule action de replacement.
- HUD passé de « Tour » à « Libre ».
- Sortie d'une route en hauteur transformée en chute physique plutôt qu'en téléportation verticale.
- Gestion tactile réécrite avec un propriétaire par bouton : relâcher la direction ou le frein ne coupe plus GAZ.
- Test renforcé : après dix secondes en ligne droite hors piste, la voiture doit encore dépasser 18 unités/s.
- Simulation de contrôle réussie : vitesse 20 hors piste et vitesse 20 en montée avec GAZ maintenu.
- Compilation Gradle complète non obtenue dans le bac à sable : accès au SDK/cache refusé et autorisation externe non accordée. La simulation Kotlin pure et la couche de rendu vérifiée avec interfaces Android minimales compilent.

### 2026-09-05 — Première chambre procédurale

- Un premier déclenchement directionnel avait supprimé les petits sauts à contresens ; il a ensuite été entièrement remplacé par une sortie de dalle simulée.
- Un premier relief procédural a été essayé puis retiré après test : le sol de la chambre reste plat pour préserver la conduite.
- Grand tapis central créé sous forme de patchwork pastel, sans image ni texture externe.
- Quatre murs pastel avec plinthes ajoutés ; leurs limites physiques empêchent de quitter la pièce.
- Premiers jouets low-poly créés par code et dotés de collisions cohérentes : cubes empilés, ours en peluche, petit train et toupie. Ils ont ensuite été redistribués dans quatre zones libres de la chambre, loin du ruban de piste.
- Ombre de la voiture adaptée au sol hors piste.
- Contrôle Kotlin autonome réussi : sol plat, jouets séparés de la piste, saut inverse refusé et voiture contenue dans la pièce après 20 secondes d'accélération.
- Couche piste/conduite/rendu recompilée avec interfaces Android minimales : aucune erreur Kotlin détectée.

### 2026-09-05 — Épaisseur et passage sous le pont

- Le ruban de piste est devenu une dalle de 0,85 unité avec dessous sombre, flancs et bouchons visibles autour du vide du tremplin.
- L'état de contact avec la route est désormais perdu dès que la voiture se trouve trop bas sous une branche surélevée.
- Un atterrissage n'est accepté que si la voiture traverse réellement la surface depuis le dessus en descendant.
- Le décollage exige lui aussi un contact vertical réel avec le tremplin.
- Test de régression ajouté et simulation ciblée réussie : au centre du huit sous la branche haute, la voiture reste au niveau du plancher ou de la route basse.

### 2026-09-05 — Collision volumique et saut simulé

- Une projection de collision indépendante de la progression recherche maintenant la dalle physiquement la plus proche, y compris une branche située au-dessus de la voiture.
- Le volume de 0,85 unité bloque la voiture par le dessus, le dessous, les côtés et les extrémités ; une zone assez haute reste naturellement franchissable en dessous.
- Le déclencheur artificiel du tremplin a été supprimé. La voiture décolle uniquement lorsque ses roues quittent le bord réel de la dalle.
- La vitesse verticale initiale vient de la pente et de la vitesse courante, sans minimum forcé, bonus ni guidage horizontal ; la gravité contrôle ensuite toute la trajectoire.
- En l'air, direction, moteur, frein et adhérence ne modifient plus la vitesse : les commandes reprennent effet seulement au contact du sol ou de la piste.
- Prendre la réception à contresens maintient la voiture au sol et ne peut plus appeler le code du saut.
- Trois simulations ciblées réussies : décollage au bord du tremplin, trente pas à contresens sans saut et choc vertical arrêté exactement sous la dalle.

### 2026-09-05 — Première exécution réelle des tests

Le journal du 5 septembre note que la compilation Gradle complète n'avait pas pu
aboutir dans le bac à sable. Conséquence : `PrototypeTrackTest` avait été écrit mais
**jamais exécuté**. Lancé pour la première fois dans l'environnement utilisateur, il
sortait deux échecs — l'un révélait un vrai bug, l'autre venait du test lui-même.

- **Téléportation sur le pont, corrigée.** `update` réécrivait l'altitude après coup
  avec `roadY`, qui vient de `project` (la projection de **progression**), juste après
  que `resolveGroundRoadCollision` ait posé la voiture sur la dalle que lui avait
  trouvée `projectForCollision` (la projection **physique**). Au croisement du huit ces
  deux projections désignent volontairement des branches différentes — c'est ce qui
  permet de cogner le dessous du pont — donc une voiture arrêtée sous le tablier, dont
  la progression était restée sur la branche haute, se retrouvait projetée quatorze
  mètres plus haut. La réaffectation traînante était par ailleurs redondante dans
  l'autre cas de figure : elle a été retirée.
- **Test du hors-piste, recalibré.** Il lisait la vitesse au bout de dix secondes.
  Mesuré : la voiture atteint bien ses 20 unités/s à la troisième seconde, traverse la
  chambre à la cinquième et finit plaquée contre le mur du fond à 0,12 unité/s. Il
  testait donc le mur, pas le hors-piste. Il mesure désormais la vitesse **pendant**
  que la voiture est hors piste.
- Suite complète de l'application : 444 tests, tous verts.

**Reste à regarder, sans urgence.** Deux points repérés en passant, laissés en l'état
parce qu'ils relèvent de la conception du prototype :

- `resolveAirborneRoadCollision` adopte la distance de la dalle qu'il a trouvée
  (`distance = collision.sample.distance`) ; `resolveGroundRoadCollision` ne le fait
  pas. Sous le pont, la voiture est donc posée sur la dalle basse alors que sa
  progression pointe toujours la branche haute, et l'état « sur la route » oscille
  d'une image à l'autre. L'altitude, elle, reste stable — mais le comptage des tours
  pourrait s'y perdre quand P3 arrivera.
- `Sample.fraction` (indice ÷ nombre d'échantillons) et le `fraction` que renvoie
  `sampleAt` (distance ÷ longueur) ne veulent pas dire la même chose sur une piste à
  vitesse non uniforme — et le huit l'est, puisque la montée n'existe que dans sa
  première moitié. Rien ne casse aujourd'hui ; `isJumpGap` compare bien des distances.

### 2026-09-05 — Première passe du Ruban Turbo (P2)

- Dérapage intentionnel séparé d'un tête-à-queue : frein et direction maintenus,
  vitesse minimale, mouvement encore majoritairement vers l'avant, fenêtre d'angle
  de glisse et confirmation pendant plusieurs pas de simulation.
- Charge calculée à chaque pas fixe selon l'angle, la vitesse et le temps passé dans
  la dérive. Trois niveaux déclenchent une relance de force et de durée croissantes.
- Ruban procédural ajouté sans asset : menthe au premier niveau, lavande au deuxième,
  jaune crème au troisième. Le maillage dynamique, les traces et les particules ont
  une capacité fixe et réutilisent leurs buffers.
- Poussière pastel ajoutée pendant la dérive et en petite gerbe lors de la relance.
- Caméra décalée légèrement selon la glisse, puis recul bref lors de l'impulsion.
- Vibrations légères ajoutées lors du franchissement des niveaux et à la relance.
- Vitesse supérieure autorisée pendant le turbo, suivie d'un retour progressif à la
  vitesse normale plutôt que d'une coupure brutale.
- Collisions contre murs et jouets recalibrées pour conserver au moins 58 % de l'élan.
- Trois profils de tailles de commandes tactiles sélectionnés selon la largeur utile.
- Vérification `compileDebugKotlin` réussie avec le JDK embarqué d'Android Studio.
- Tous les points P2 restent en cours jusqu'au réglage et à la validation sur téléphone.

### 2026-09-05 — Ruban Turbo simplifié après retour téléphone

- La combinaison GAZ + direction + FREIN est supprimée : elle compliquait une action
  qui doit rester naturelle et obligeait à interrompre inutilement l'accélération.
- Le joueur peut désormais garder GAZ. Un virage suffisamment marqué réduit
  automatiquement et légèrement l'adhérence, fait apparaître le ruban, puis le
  redressement déclenche la relance.
- L'adhérence de dérive a été remontée pour produire une glisse plus légère et mieux
  contrôlée que l'ancien dérapage au frein.
- Le bouton FREIN ne commande plus le dérapage ; son second rôle contextuel de marche arrière est documenté ci-dessous.

### 2026-09-05 — Temporisation et continuité du Ruban Turbo

- Un petit coup de volant ne suffit plus : le virage et la glisse doivent rester
  valides pendant 0,38 seconde avant l'apparition du ruban.
- Une fois le ruban actif, une correction de trajectoire pouvant durer jusqu'à
  0,52 seconde ne coupe plus la dérive. Reprendre la direction pendant cette fenêtre
  continue le même ruban et conserve toute la charge.
- Freiner, décoller ou partir en tête-à-queue annule toujours la charge afin de ne pas
  récompenser une perte de contrôle.
- Le premier niveau est garanti après une dérive réellement confirmée. Les relances
  durent désormais de 0,70 à 1,20 seconde et leur accélération a été renforcée.
- Le déplacement de caméra à l'activation et à la relance est maintenant interpolé ;
  sa cible ne saute plus brutalement vers l'avant, ce qui pouvait ressembler à un lag.

### 2026-09-05 — Frein contextuel et marche arrière

- Tant que la voiture avance, le bouton FREIN conserve son rôle normal.
- Une fois presque immobile, maintenir FREIN pendant une très courte pause engage la
  marche arrière. Relâcher le bouton la désengage immédiatement.
- La marche arrière est limitée à 7 unités/s et possède une accélération plus douce
  que la marche avant.
- La direction est inversée pendant le recul afin que les commandes restent naturelles.

### 2026-09-05 — Correction du crash du retour haptique

- Le premier essai des vibrations a révélé une `SecurityException` : la permission
  normale `android.permission.VIBRATE` manquait dans le manifeste.
- La permission est maintenant déclarée et l'appel au vibreur est protégé : si un
  appareil ou un profil refuse malgré tout le service, seul l'effet haptique est
  ignoré et la partie continue.

### 2026-09-05 — Déclenchement du ruban fiabilisé

- Le compteur d'entrée dépend désormais d'un virage maintenu à vitesse suffisante.
  Il ne repart plus à zéro simplement parce que l'angle de glisse traverse trop vite
  une fenêtre étroite entre deux pas de simulation.
- L'angle de glisse reste utilisé pour calculer la qualité de la charge et annuler la
  dérive à l'approche d'une vraie perte de contrôle.
- La tolérance sur la composante avant et sur l'angle a été élargie afin que le ruban
  apparaisse avant un tête-à-queue plutôt que de refuser silencieusement l'activation.
- L'adhérence automatique en virage est renforcée pour conserver une glisse visible
  mais réduire la tendance de la mécanique à provoquer elle-même un tête-à-queue.

### 2026-09-05 — Première course complète (P3)

- Nouveau contrôleur de course à pas fixe : compte à rebours de 3,25 secondes,
  chronomètre, quatre checkpoints ordonnés, mauvais sens, trois tours et arrivée.
- Les commandes peuvent être maintenues pendant le compte à rebours mais la voiture
  reste physiquement immobile : aucun faux départ n'est possible.
- Les changements de branche incohérents au croisement sont filtrés dans la mesure de
  progression. Le classement utilise également une marge de 1,5 unité et une
  confirmation de 0,28 seconde pour éviter de clignoter lors d'un dépassement.
- Cinq adversaires colorés suivent une ligne de course anticipée. Ils possèdent des
  décalages latéraux, des rythmes et des erreurs périodiques distincts.
- Les adversaires franchissent le vide central avec une trajectoire verticale
  procédurale au lieu de flotter sur la portion absente du ruban de piste.
- Trois difficultés sans triche de boost : **Détente**, **Arcade** et **Champion**.
  Elles modifient vitesse cible, anticipation, prudence en courbe et erreurs.
- HUD de course ajouté : vitesse, position sur six, tour sur trois, chronomètre,
  compte à rebours central et alerte de mauvais sens.
- Écran de résultat ajouté avec place, temps, difficulté, meilleur chrono local et
  bouton pour rejouer. Les records et la difficulté choisie sont persistés.
- Les sept tâches P3 restent en cours jusqu'à une course complète validée sur téléphone.
- Vérification `compileDebugKotlin` réussie.

### 2026-09-05 — Récompense continue du Ruban Turbo

- Le ruban reste invisible pendant les 0,25 premières secondes du virage. Ce temps
  confirme l'intention mais ne donne plus automatiquement un niveau de turbo.
- Une fois visible, la réserve cumule des secondes efficaces. Chaque seconde compte
  davantage lorsque l'angle et la vitesse témoignent d'une glisse bien contrôlée,
  tandis qu'un petit virage reste légèrement positif.
- La correction latérale propre au ruban conserve désormais la norme de la vitesse :
  activer la mécanique ne prélève aucune énergie cachée au joueur.
- Les trois boosts fixes sont supprimés. La durée suit une courbe convexe au départ,
  puis à rendement décroissant, avec un plafond strict de cinq secondes.
- La puissance d'attaque dépend elle aussi de la réserve, entre environ x1,12 et
  x1,80 de vitesse maximale. La poussée soutenue conserve 38 % de cet excédent, puis
  rejoint doucement x1 pendant le dernier quart du boost. Même un petit boost décroît
  donc toujours depuis sa propre puissance initiale.
- Une nouvelle charge est impossible tant qu'un boost reste actif. Les cycles de
  rubans courts ne peuvent donc plus s'enchaîner par-dessus leur propre relance.
- Les couleurs menthe, lavande et or restent des repères visuels, mais la physique
  utilise maintenant une valeur continue plutôt que trois récompenses discrètes.

---

### 2026-09-05 — Choix du mode, pause et repérage

- L'exploration libre redevient le mode initial, conformément à la direction bac à sable :
  conduite et Ruban Turbo actifs, sans adversaires, arrivée ni enregistrement de record.
- Le bouton **Course** lance le défi avec la difficulté choisie ; **Libre** quitte le défi.
  La difficulté est verrouillée pendant la course pour éviter une remise à zéro accidentelle.
- Pause ajoutée avec reprise, recommencement, changement de mode et sortie. Le bouton
  Retour ouvre ce menu. Le passage en arrière-plan suspend aussi la partie et demande
  une reprise explicite au retour ; les doigts propriétaires des commandes sont libérés.
- La simulation et le chronomètre ignorent le temps passé en pause. Une recréation du
  contexte OpenGL recharge les meshes sans réinitialiser la course. Cela ne constitue
  pas une sauvegarde de partie après destruction de l'activité ou du processus Android.
- Mini-carte procédurale de la pièce : tracé, interruption du tremplin, départ, cap du
  joueur et cinq points aux couleurs des adversaires. Aucun asset externe ajouté.
  Le tracé est construit au changement de taille ; les positions suivent le HUD à 10 Hz.
- HUD resserré pour réserver une zone distincte aux boutons à droite.
- Le résultat final ne dépend plus du délai de stabilisation du classement affiché.
  Les instants de franchissement sont interpolés dans le pas fixe pour départager
  deux arrivées pendant le même pas ; le chronomètre final utilise également cet instant.
- Neuf nouvelles chaînes d'interface ajoutées dans les 14 langues ; les anciens textes
  provisoires français restent à reprendre lors de la traduction complète P6.
- Vérification finale `compileDebugKotlin` réussie (34 secondes), XML des 14 langues
  vérifiés et `git diff --check` sans erreur. Aucun APK construit ou installé.
  Validation visuelle et comportementale sur téléphone
  encore nécessaire, suivant la liste en tête de document.

---

### 2026-09-05 — Chambre meublée et catalogue pour les futures pistes

- Ours remodelé en position assise avec pattes avancées, coussinets, bras, ventre
  clair, oreilles bicolores, yeux brillants et nœud lavande.
- Chambre enrichie : porte fermée à panneaux, deux fenêtres avec ciel illustré et
  rideaux, bureau avec tiroirs et tabouret, lampe champignon, crayons, cahier, livres,
  bibliothèque, étagères, bacs, lit bas et parquet. Murs rehaussés à 32 unités.
- Volumes du mobilier définis dans `RoomDecor` et partagés entre rendu et collisions.
  Les pieds restent séparés ; les plateaux supportent une arrivée depuis le haut,
  les dessous bloquent une montée et une sortie de plateau déclenche une chute.
- Train déplacé pour dégager le bureau. Écart horizontal minimal calculé entre
  mobilier et bord du circuit : environ 2,33 unités, avant rayon de la voiture.
- Correction du sens des triangles latéraux des cylindres orientés X : les roues
  des voitures, du train et de la servante présentent désormais leurs faces extérieures.
- Bibliothèque de 25 modèles originaux : 8 cuisine, 9 salon/salle à manger et
  8 garage. Ils ne sont pas ajoutés automatiquement à la chambre existante.
- Placement réutilisable par position, quart de tour et échelle uniforme ; normales
  tournées avec le mesh et collisions transformées depuis la même définition.
- Modèles regroupés dans le mesh statique du décor ; catalogue chargé à la demande.
- `TOYBOX_DECORS.md` ajouté : inventaire, provenance, conventions et exemple de composition.
- Vérification finale `compileDebugKotlin` réussie (34 secondes). Maillages exportés
  depuis les classes compilées pour une planche d'aperçu, contrôlée visuellement hors
  Android : 10 432 triangles pour le décor de chambre, de 84 à 1 404 par modèle du
  catalogue. Aucun APK généré ou installé ; fluidité et collisions à essayer sur téléphone.

---

### 2026-09-05 — Bureau, salle de bains et extérieur

- 24 modèles ajoutés dans `DecorExpansion` : tour PC, écran/clavier/souris, vélo
  d'appartement, commode, micro-ondes, neuf accessoires/meubles de salle de bains
  et dix éléments d'extérieur, dont maison, porche et voiture familiale miniature.
- Catalogue porté à 49 identifiants uniques. `DecorCatalog.forRoom` permet de
  sélectionner tous les éléments d'une pièce, quelle que soit leur collection.
- Toit à deux pentes ajouté aux primitives de rendu pour maison, porche et petits
  accessoires. Collision encore rectangulaire, non destinée à rouler sur ces toits.
- Douche ouverte et baignoire construite avec fond et parois distinctes ; pas de
  boîte globale bouchant leur intérieur. Modèles et collisions restent statiques.
- Miroir encadré créé avec surface claire stylisée. Les vrais reflets OpenGL ES
  demandent une passe de rendu supplémentaire et restent un chantier ultérieur.
- Maison extérieure terminée sur ses quatre côtés ; le circuit extérieur et les
  intérieurs de cette maison ne sont pas encore implémentés.
- Compilation `compileDebugKotlin` et inspection d'une planche issue des maillages
  compilés réalisées ; aucun APK généré ou installé. Catalogue documenté dans
  `TOYBOX_DECORS.md`. Essais sur téléphone à effectuer lors du placement des décors.

---

## Questions encore ouvertes

- Orientation paysage obligatoire ? Recommandation actuelle : **oui**.
- Vue uniquement derrière la voiture ou choix entre deux distances de caméra ?
- Personnalisation purement cosmétique ou petites différences de conduite entre véhicules ?
- Le premier circuit doit-il passer réellement sous le lit dès P4, ou garder ce décor pour une variante plus ambitieuse ?

Ces questions ne bloquent pas la création du prototype P1.
