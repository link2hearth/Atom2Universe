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



## Succès, trophées et objectifs
- **Trophées d’échelle atomique** : 21 jalons de 10^14 à 10^80 octroient chacun +2 au multiplicateur global et contextualisent la progression.【F:config/config.js†L409-L608】
- **Succès thématiques** : la ruée vers le million, les frénésies (100/1 000), la collecte automatique des étoiles et d’autres objectifs octroient des bonus permanents (multiplicateurs, nouveaux emplacements de frénésie, auto-collecte…).【F:config/config.js†L745-L827】
- **Panneau “Objectifs”** : la navigation inclut une page dédiée qui récapitule ces jalons et se déverrouille avec la progression pour guider les priorités.【F:index.html†L35-L69】【F:scripts/app.js†L1607-L1699】

## Fusion moléculaire
Un onglet “Fusion” présente des recettes consommant des éléments du gacha pour octroyer des bonus APC/APS. Chaque carte affiche chances de réussite, prérequis, état de la collection et historique de tentatives.

## Mini-jeux d’arcade
L’onglet Arcade propose plusieurs jeux qui donnent pour la majorité d'entre eux des tickets gacha en cas de réussite.
