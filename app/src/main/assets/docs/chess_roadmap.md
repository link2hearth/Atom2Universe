# Intégration du mini-jeu d'échecs

Ce plan décrit les étapes pour ajouter un mini-jeu d'échecs jouable dans Atom → Univers. Le joueur humain y contrôlera les blancs et affrontera une IA pilotant les noirs.

## Étape 1 — Cadrage et architecture
- **Définir l'intégration** : ajouter une entrée "Échecs" dans le hub des mini-jeux (`index.html`, section arcade) avec une carte dédiée et le routage i18n.
- **Créer les fichiers dédiés** :
  - `scripts/arcade/echecs.js` pour la logique du plateau, l'orchestration du tour par tour et l'interface.
  - `styles/arcade/echecs.css` pour la mise en forme.
- **Configurer les ressources** : renseigner les clés de traduction (FR/EN).

## Étape 2 — Moteur d'échecs côté client
- **Modèle de données** : représenter l'échiquier en matrice 8×8 et structurer les pièces via des objets ou constantes symboliques.
- **Validation des coups** : implémenter les règles principales (déplacements, captures, promotion, roque, prise en passant) et la détection d'échec.
- **Détection de fin de partie** : vérifier mat, pat, répétitions, règle des 50 coups et matériels insuffisants.
- **Interface utilisateur** : générer la grille HTML, gérer la sélection des pièces, les indications de coups valides et les messages d'état i18n.

## Étape 3 — Boucle de jeu et intégration UI/UX
- **Interaction joueur** : permettre le glisser-déposer et le clic-clic pour les mouvements (adapté au tactile et a la souris), avec validations et messages d'erreur.
- **État de partie** : sauvegarder l'avancement dans la structure centrale (similaire aux autres mini-jeux).
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

## Revue des étapes 1 à 5
- ✅ Étape 1 — Cadrage, architecture et intégration I18N : la section arcade “Échecs” est disponible dans `index.html` avec sa carte dédiée et toutes les clés de traduction nécessaires.
- ✅ Étape 2 — Moteur d'échecs : la validation des coups (pions, pièces majeures, roques, promotion, prise en passant) est en place ainsi que la détection d'échecs et de fins de partie standards.
- ✅ Étape 3 — Boucle de jeu et UX : la grille HTML supporte le clic et le glisser-déposer, l'historique SAN et les aides visuelles sont opérationnels.
- ✅ Étape 4 — IA des noirs (v1) : minimax + alpha-bêta, tri des captures et table de transposition légère sont implémentés.
- ✅ Étape 5 — IA des noirs (améliorations) : itération approfondie, heuristiques MVV-LVA/killer, évaluation de finales et corpus FEN ont été livrés.

## Étape 6 — Finitions et QA
- ✅ **Interface** : animations de déplacement/capture, bouton de réinitialisation, panneau d'analyse du dernier coup de l'IA et sélecteur de difficulté ont été ajoutés.
- ✅ **Équilibrage** : trois modes (Entraînement/Standard/Expert) ajustent profondeur, temps de réflexion et bonus hors-ligne ; un plafond de coups limite les parties interminables.
- ✅ **Récompense** : la victoire des blancs déclenche désormais le bonus hors ligne associé à la difficulté via `registerChessVictoryReward`.
- ✅ **Sauvegarde** : la progression (plateau, historique, préférences, difficulté, analyse) est persistée dans `localStorage` et dans l'état global du jeu.
- 📌 **Documentation** : ce fichier et le `ReadMe.md` sont mis à jour pour refléter les nouvelles commandes.

Ce plan peut être itéré en plusieurs PR : commencer par les étapes 1–3 pour poser le plateau, puis créer des itérations supplémentaires pour les étapes 4 et 5 afin d'enrichir l'IA.
