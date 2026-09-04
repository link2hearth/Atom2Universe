# Toybox Racers — plan et suivi du projet

> Jeu de course 3D arcade pour Atom2Universe — document vivant créé le 4 septembre 2026.

## État du projet

**Phase actuelle :** P1 jouable validée — première passe de la direction artistique P4 en cours.

| État | Signification |
|---|---|
| `[ ]` | À faire |
| `[~]` | En cours |
| `[x]` | Terminé et vérifié |
| `[!]` | Bloqué ou décision nécessaire |

### Prochaine étape recommandée

Tester sur téléphone la première chambre procédurale : tapis patchwork pastel, quatre jouets low-poly solides distribués hors de la piste et murs de pièce. Vérifier surtout que les murs sont visibles assez tôt et que la densité du décor ne gêne pas la conduite.

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
- Pas de marche arrière complexe : replacement automatique si la voiture reste bloquée.

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
- [ ] Compiler l'application complète avec `compileDebugKotlin` dans l'environnement utilisateur.
- [x] Essayer sur téléphone et valider la base de gameplay, la direction, la pente, le saut et la caméra.

**Critère de sortie :** conduire seul pendant cinq minutes reste fluide, compréhensible et amusant.

### P2 — Ruban Turbo et sensation de conduite

- [ ] Détecter un dérapage intentionnel plutôt qu'un simple tête-à-queue.
- [ ] Faire croître la charge selon angle, vitesse et durée.
- [ ] Dessiner le ruban pastel et ses trois niveaux visuels.
- [ ] Déclencher une impulsion lisible au redressement.
- [ ] Ajouter vibrations légères, poussière pastel et mouvements de caméra.
- [ ] Régler les collisions pour conserver le rythme de course.
- [ ] Tester plusieurs tailles de commandes tactiles.

**Critère de sortie :** le joueur cherche spontanément les courbes pour préparer ses relances.

### P3 — Première course complète

- [ ] Ajouter checkpoints, sens de circulation, tours et arrivée.
- [ ] Créer une ligne de course pour l'IA.
- [ ] Ajouter cinq adversaires avec erreurs et personnalités simples.
- [ ] Calculer le classement sans saut incohérent de position.
- [ ] Ajouter compte à rebours, faux départ impossible et écran de résultat.
- [ ] Créer trois difficultés équitables.
- [ ] Sauvegarder les meilleurs temps.

**Critère de sortie :** une course de trois tours peut être gagnée, perdue, recommencée et enregistrée correctement.

### P4 — Direction artistique jouable

- [ ] Créer une voiture jouet finale et plusieurs variantes originales.
- [ ] Définir la palette officielle et les matériaux.
- [~] Construire le décor **La Chambre Arc-en-ciel** : sol plat, tapis, murs et premiers jouets procéduraux ajoutés.
- [x] Ajouter des limites de pièce visibles et solides pour contenir l'exploration libre.
- [x] Créer une première collection de jouets low-poly originaux : cubes, ours, train et toupie.
- [ ] Ajouter éclairage, blob shadows et arrière-plan de pièce.
- [ ] Créer HUD, icône et écran de sélection originaux.
- [ ] Remplacer chaque élément temporaire du prototype.
- [ ] Tenir à jour l'inventaire de provenance des assets.

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

---

## Questions encore ouvertes

- Orientation paysage obligatoire ? Recommandation actuelle : **oui**.
- Vue uniquement derrière la voiture ou choix entre deux distances de caméra ?
- Personnalisation purement cosmétique ou petites différences de conduite entre véhicules ?
- Le premier circuit doit-il passer réellement sous le lit dès P4, ou garder ce décor pour une variante plus ambitieuse ?

Ces questions ne bloquent pas la création du prototype P1.
