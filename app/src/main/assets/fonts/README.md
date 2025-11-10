# Polices locales

Ce dossier accueillera les fichiers de polices nécessaires pour une exécution hors ligne du jeu.

## Polices attendues

Le jeu repose sur les familles et variantes suivantes :

- `Audiowide` — régulier (400)
- `Orbitron` — régulier (400), semi-bold (600) et bold (700)
- `Cinzel` — régulier (400) pour une option à empattements
- `DigitTech7` — régulier (400) pour certains affichages numériques
- `VT323` — régulier (400) pour proposer une alternative rétro

Chaque variante peut être fournie au format `.woff2` **ou** `.ttf`. Le jeu essaiera d’abord de charger le fichier `.woff2`, puis
de basculer sur la version `.ttf` si elle est disponible. Respecte les noms utilisés dans `styles/fonts.css`, par exemple
`Orbitron-600.woff2` / `Orbitron-600.ttf`. Pour les fichiers téléchargés qui
gardent un suffixe plus verbeux (ex. `Orbitron-Regular.ttf`), conserve aussi cette variante : elle est référencée comme solution
de secours.

## Télécharger automatiquement

Pour (re)télécharger les polices depuis Google Fonts et générer les fichiers `.woff2`, exécute :

```bash
node scripts/resources/download-fonts.mjs
```

Les fichiers générés seront placés ici et versionnables pour permettre un usage hors connexion.

## Ajouter les fichiers manuellement

Si tu préfères récupérer les polices à la main (ou si le script n’a pas accès à Internet), télécharge les variantes listées ci-dessus
depuis Google Fonts puis copie-les ici. Renomme-les au besoin pour qu’elles correspondent aux patrons `Famille-poids.woff2` ou
`Famille-poids.ttf` (par exemple `Orbitron-Regular.ttf` → `Orbitron-400.ttf`). Les appellations d’origine les plus courantes (`*-Regular.ttf`,
`*-SemiBold.ttf`, etc.) sont également renseignées comme solution de repli. Une fois les fichiers présents, le jeu utilisera
automatiquement ces ressources locales sans connexion réseau.
