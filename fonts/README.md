# Polices locales

Ce dossier accueillera les fichiers de polices nécessaires pour une exécution hors ligne du jeu.

## Polices attendues

Le jeu repose sur les familles et variantes suivantes :

- `Audiowide` — régulier (400)
- `Orbitron` — régulier (400), semi-bold (600) et bold (700)
- `Inter` — régulier (400), medium (500) et semi-bold (600)
- `Seven Segment` — régulier (400)

Chaque fichier est attendu au format `.woff2` avec les noms correspondants à ceux utilisés dans `styles/fonts.css`, par exemple
`Orbitron-600.woff2` ou `SevenSegment-400.woff2`.

## Télécharger automatiquement

Pour (re)télécharger les polices depuis Google Fonts et générer les fichiers `.woff2`, exécute :

```bash
node scripts/resources/download-fonts.mjs
```

Les fichiers générés seront placés ici et versionnables pour permettre un usage hors connexion.

## Ajouter les fichiers manuellement

Si tu préfères récupérer les polices à la main (ou si le script n’a pas accès à Internet), télécharge les variantes listées ci-dessus
depuis Google Fonts puis copie-les ici en respectant les noms de fichiers attendus. Une fois les fichiers présents, le jeu utilisera
automatiquement ces ressources locales sans connexion réseau.
