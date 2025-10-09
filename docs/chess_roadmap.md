# Intégration du mini-jeu d'échecs

Ce plan décrit les étapes pour ajouter un mini-jeu d'échecs jouable dans Atom → Univers. Le joueur humain y contrôlera les blancs et affrontera une IA pilotant les noirs.

## Étape 1 — Cadrage et architecture
- **Définir l'intégration** : ajouter une entrée "Échecs" dans le hub des mini-jeux (`index.html`, section arcade) avec une carte dédiée et le routage i18n.
- **Créer les fichiers dédiés** :
  - `scripts/arcade/echecs.js` pour la logique du plateau, l'orchestration du tour par tour et l'interface.
  - `styles/arcade/echecs.css` pour la mise en forme.
  - Actifs supplémentaires (sprites des pièces, sons) dans `Assets/Arcade/Chess/`.
- **Configurer les ressources** : renseigner les clés de traduction (FR/EN) et, si nécessaire, ajouter un bloc de configuration dans `config/config.js` pour les récompenses du mini-jeu.

## Étape 2 — Moteur d'échecs côté client
- **Modèle de données** : représenter l'échiquier en matrice 8×8 et structurer les pièces via des objets ou constantes symboliques.
- **Validation des coups** : implémenter les règles principales (déplacements, captures, promotion, roque, prise en passant) et la détection d'échec.
- **Détection de fin de partie** : vérifier mat, pat, répétitions, règle des 50 coups et matériels insuffisants.
- **Interface utilisateur** : générer la grille HTML, gérer la sélection des pièces, les indications de coups valides et les messages d'état i18n.

## Étape 3 — Boucle de jeu et intégration UI/UX
- **Interaction joueur** : permettre le glisser-déposer ou le clic-clic pour les mouvements, avec validations et messages d'erreur.
- **État de partie** : sauvegarder l'avancement dans la structure centrale (similaire aux autres mini-jeux) et relier les récompenses en cas de victoire ou de match nul.
- **Accessibilité** : ajouter une option d'affichage des coordonnées et des coups joués (liste de notation algébrique basique).
- **Tests manuels** : vérifier toutes les règles côté joueur sur différents scénarios (roques, promotions, pat…).

## Étape 4 — IA des noirs (version 1)
- **Évaluation statique** : définir une fonction de score pondérant le matériel, le développement, la structure de pions et la sécurité du roi.
- **Recherche minimax** : implémenter un minimax avec profondeur configurable (cible initiale : 2 à 3 demi-coups) et élagage alpha-bêta.
- **Optimisations simples** :
  - Tri des coups (captures en premier).
  - Détection de coups illégaux (laisser le roi en échec) avant l'exploration.
  - Table de transposition légère basée sur FEN tronqué.
- **Paramétrage** : exposer les profondeurs/temps de calcul dans `config/config.js` pour un ajustement rapide.

## Étape 5 — IA des noirs (améliorations)
- **Extensions d'itération** : ajouter l'itération approfondie (iterative deepening) avec limite de temps en millisecondes.
- **Heuristiques** : intégrer l'heuristique MVV-LVA pour les captures et le killer heuristic pour accélérer la recherche.
- **Gestion des finales** : appliquer des bonus/malus spécifiques (pions passés, roi centralisé) lorsque peu de pièces restent.
- **Tests ciblés** : construire un petit corpus de positions (FEN) pour vérifier la cohérence des choix de l'IA.

## Étape 6 — Finitions et QA
- **Interface** : ajouter des animations légères, possibilité de réinitialiser la partie et d'analyser le dernier coup de l'IA.
- **Equilibrage** : ajuster les récompenses en fonction de la difficulté et valider que les parties se terminent dans un temps raisonnable.
- **Sauvegarde** : décider si la progression du mini-jeu doit être persistée entre les sessions (localStorage) et implémenter si nécessaire.
- **Documentation** : mettre à jour `ReadMe.md` (section mini-jeux) une fois le mini-jeu jouable et décrire la commande de lancement.

Ce plan peut être itéré en plusieurs PR : commencer par les étapes 1–3 pour poser le plateau, puis créer des itérations supplémentaires pour les étapes 4 et 5 afin d'enrichir l'IA.
