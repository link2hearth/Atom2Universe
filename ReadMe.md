# 🧪 Atom → Univers

Atom → Univers est un idle/clicker cosmique actuellement jouable en français et en anglais. La boucle principale combine le clic manuel, la production passive, la chasse aux frénésies et une collection d’éléments débloquée via un gacha alimenté par plusieurs mini-jeux. Le développement est toujours en cours, mais l’ensemble des systèmes listés ci-dessous est implémenté dans le dépôt.

## Sommaire
1. [Boucle de jeu et ressources](#boucle-de-jeu-et-ressources)
2. [Boutique scientifique](#boutique-scientifique)
3. [Collections, gacha et tickets](#collections-gacha-et-tickets)
4. [Succès, trophées et objectifs](#succès-trophées-et-objectifs)
5. [Fusion moléculaire](#fusion-moléculaire)
6. [Mini-jeux d’arcade](#mini-jeux-darcade)
7. [Infos, DevKit et options](#infos-devkit-et-options)
8. [Sauvegardes, hors-ligne et grands nombres](#sauvegardes-hors-ligne-et-grands-nombres)
9. [Internationalisation](#internationalisation)
10. [Lancer le projet en local](#lancer-le-projet-en-local)

---

## Boucle de jeu et ressources
- **Page principale** : le bouton d’atome déclenche la production par clic (APC) tandis que les compteurs de l’en-tête suivent les gains manuels, passifs et les critiques.【F:index.html†L18-L84】
- **Frénésies** : des orbes temporaires peuvent apparaître aussi bien pour l’APC que pour l’APS. Elles durent 30 s, appliquent un multiplicateur ×2 et peuvent se cumuler selon les trophées débloqués.【F:config/config.js†L612-L643】
- **Système critique** : chaque session démarre avec 5 % de chances de critique ×2 et un plafond de multiplicateur à ×100, modifiés ensuite par les bonus d’éléments et d’événements.【F:config/config.js†L666-L686】
- **Progression** : les valeurs de base (1 atome par clic, 0 APS) sont ajustées par les bâtiments, les collections et les mini-jeux ; l’intervalle moyen de la “star” à tickets est initialement de 60 s.【F:config/config.js†L655-L705】【F:scripts/app.js†L1270-L1283】

## Boutique scientifique
La boutique regroupe quinze bâtiments (manuel, automatique ou hybrides). Chaque carte n’accorde désormais plus que des gains plats d’APC et/ou d’APS, sans multiplicateurs, synergies croisées ni réductions de coûts supplémentaires.【F:config/config.js†L45-L268】【F:scripts/app.js†L6056-L6078】
- **Électrons libres** : +1 APC par niveau.【F:config/config.js†L45-L58】
- **Laboratoire de Physique** : +1 APS par niveau.【F:config/config.js†L59-L72】
- **Réacteur nucléaire** : +10 APS par niveau.【F:config/config.js†L73-L87】
- **Chaîne tardive** : du Forgeron d’étoiles au Grand Ordonnateur quantique, chaque bâtiment ajoute simplement son APS plat annoncé par niveau.【F:config/config.js†L149-L268】

Chaque carte de boutique est décrite et générée dynamiquement à partir de `GAME_CONFIG`, ce qui permet d’ajuster facilement l’équilibrage sans modifier la logique d’interface.【F:scripts/app.js†L6089-L7576】

## Collections, gacha et tickets
- **Tickets** : la “star” apparaît automatiquement, peut être améliorée par la rareté Mythe quantique (−1 s par élément unique, minimum 5 s) et offre un mode de collecte automatique via les trophées dédiés.【F:scripts/app.js†L1270-L1318】【F:config/config.js†L990-L1014】【F:config/config.js†L810-L827】
- **Portail gacha** : chaque tirage coûte 1 ticket (ou peut être gratuit via le DevKit). Les probabilités de base et la rareté mise à l’honneur changent selon le jour (pity journalier).【F:scripts/arcade/gacha.js†L107-L217】【F:config/config.js†L1528-L1612】
- **Raretés & bonus** : six familles d’éléments apportent des bonus plats, multiplicatifs ou utilitaires (intervalle de tickets, critique, hors-ligne…). Les caps de progression sont précisés pour chaque groupe.【F:config/config.js†L910-L1034】
- **Tableau périodique** : la liste complète des éléments et leurs méta-données se trouve dans `scripts/resources/periodic-elements.js` et alimente la collection affichée dans l’onglet dédié.【F:scripts/resources/periodic-elements.js†L1-L210】

## Succès, trophées et objectifs
- **Trophées d’échelle atomique** : 21 jalons de 10^14 à 10^80 octroient chacun +2 au multiplicateur global et contextualisent la progression.【F:config/config.js†L409-L608】
- **Succès thématiques** : la ruée vers le million, les frénésies (100/1 000), la collecte automatique des étoiles et d’autres objectifs octroient des bonus permanents (multiplicateurs, nouveaux emplacements de frénésie, auto-collecte…).【F:config/config.js†L745-L827】
- **Panneau “Objectifs”** : la navigation inclut une page dédiée qui récapitule ces jalons et se déverrouille avec la progression pour guider les priorités.【F:index.html†L35-L69】【F:scripts/app.js†L1607-L1699】

## Fusion moléculaire
Un onglet “Fusion” présente des recettes consommant des éléments du gacha pour octroyer des bonus plats APC/APS. Chaque carte affiche chances de réussite, prérequis, état de la collection et historique de tentatives.【F:index.html†L700-L748】【F:config/config.js†L1396-L1514】【F:scripts/arcade/gacha.js†L1684-L1884】

## Mini-jeux d’arcade
L’onglet Arcade propose trois expériences qui alimentent les tickets et bonus :
- **Particules** (brick breaker) : niveaux successifs, HUD complet, tickets de gacha en récompense de niveau parfait, gravitons convertis en crédits Mach3 et en annonces toast.【F:index.html†L158-L266】【F:scripts/arcade/particules.js†L1980-L2056】【F:scripts/arcade/particules.js†L2532-L2555】
- **Mach3 – Métaux** : grille 9×16, cinq gemmes, timer de 6 s extensible, consommation d’un crédit Mach3 par partie et bonus APS proportionnel aux performances.【F:index.html†L424-L477】【F:scripts/arcade/metaux-match3.js†L4-L118】【F:scripts/app.js†L1608-L1705】
- **Photon** : runner basé sur un photon alternant entre deux états pour traverser des obstacles, trois modes (single/classic/hold), score en temps réel et rotation automatique des modes. Aucun bonus permanent n’est encore rattaché, comme indiqué dans le texte d’interface.【F:index.html†L880-L1036】【F:scripts/arcade/photon.js†L1-L210】【F:config/config.js†L686-L705】

## Infos, DevKit et options
- **Page Infos** : breakdown complet des gains APC/APS, statistiques de session et globales, liste des bonus actifs par source.【F:index.html†L714-L804】【F:scripts/app.js†L7522-L7569】
- **DevKit quantique** : accessible via F9, permet d’ajouter des ressources, tickets, crédits Mach3 ou de passer le magasin/gacha en mode gratuit pour les tests. Les actions mettent à jour l’UI et consignent les gains via des toasts dédiés.【F:index.html†L1163-L1269】【F:scripts/app.js†L3727-L3957】
- **Options** : thèmes visuels, langue, import/export de sauvegarde et paramètres audio sont gérés via l’onglet Options (chargement dynamiques par `app.js`).【F:index.html†L35-L69】【F:scripts/app.js†L5435-L5712】

## Sauvegardes, hors-ligne et grands nombres
- Les sauvegardes sont automatiques (localStorage) et incluent tickets, fusions, bonus, statistiques et paramètres.【F:scripts/app.js†L8820-L9137】
- Les gains hors-ligne prennent en compte jusqu’à 12 h d’absence et peuvent générer des tickets supplémentaires en fonction du temps écoulé.【F:config/config.js†L655-L705】【F:scripts/app.js†L9246-L9258】
- Le moteur de grands nombres bascule vers des layers exponentiels au-delà de 1e6, ce qui garantit une progression fluide jusqu’à 10^80 et plus.【F:config/config.js†L594-L643】

## Internationalisation
Le jeu charge dynamiquement les ressources depuis `scripts/i18n/<code>.json`. Ajouter une langue consiste à dupliquer un fichier existant, traduire les clés, puis enregistrer le code dans `AVAILABLE_LANGUAGES` pour affichage dans le sélecteur.【F:scripts/i18n/fr.json†L1-L340】【F:scripts/modules/i18n.js†L33-L142】

## Lancer le projet en local
Le dépôt contient uniquement des fichiers statiques. Pour tester le jeu en local :
1. Installez une version LTS de Node.js (ou utilisez Python si vous préférez).
2. Depuis la racine du projet, lancez un serveur statique, par exemple :
   ```bash
   npx serve .
   # ou
   python -m http.server 8080
   ```
3. Ouvrez `http://localhost:3000` (ou le port choisi) dans votre navigateur. Les requêtes `fetch` du jeu nécessitent un serveur HTTP et ne fonctionnent pas en ouvrant directement `index.html`.

---

Bon jeu et bon click !
