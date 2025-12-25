# Ajouter facilement une nouvelle page HTML

Ce guide explique comment ajouter une page HTML **sans coder** dans l’application, en vous contentant d’ajouter des fichiers et une entrée dans un registre.

## ✅ Ce qu’il faut préparer

Pour chaque nouvelle page, vous aurez en général :

- Un fichier HTML (obligatoire)
- Un fichier CSS (optionnel)
- Un fichier JS (optionnel)

## 1) Placer les fichiers au bon endroit

Copiez vos fichiers dans :

```
app/src/main/assets/
```

Exemples :

- `app/src/main/assets/ma-page.html`
- `app/src/main/assets/styles/ma-page.css`
- `app/src/main/assets/scripts/ma-page.js`

## 2) Utiliser le header « retour » prêt à l’emploi

Copiez/collez ce header **en haut** de votre page HTML :

```
<!-- Header standard (bouton retour vers index.html) -->
<header class="page-header" data-i18n="aria-label:pages.header.aria">
  <a
    class="page-header__back"
    href="index.html"
    data-i18n="pages.header.back;aria-label:pages.header.backAria;title:pages.header.backAria"
  >
    Retour
  </a>
  <h1 class="page-header__title" data-i18n="pages.header.title">Page</h1>
</header>
```

> Astuce : la version officielle est aussi disponible dans `app/src/main/assets/snippets/page-header.html`.

## 3) Déclarer la page dans le registre

Ouvrez le fichier :

```
app/src/main/assets/config/custom-pages.json
```

Ajoutez un nouvel objet **dans le tableau** :

```json
{
  "id": "ma-page",
  "path": "ma-page.html",
  "titleKey": "index.sections.options.customPages.pages.maPage.title",
  "descriptionKey": "index.sections.options.customPages.pages.maPage.description",
  "openMode": "standalone",
  "css": ["styles/ma-page.css"],
  "js": ["scripts/ma-page.js"]
}
```

### Explications rapides

- `id` : identifiant interne (sans espaces)
- `path` : chemin vers le HTML (relatif à `assets/`)
- `titleKey` : clé i18n du nom affiché
- `descriptionKey` : clé i18n de la description (optionnelle)
- `openMode` :
  - `"standalone"` → ouvre dans une page séparée
  - (ou laissez vide pour ouvrir dans l’app)
- `css` / `js` : listes optionnelles de fichiers

## 4) Ajouter les textes traduits (i18n)

Ajoutez les libellés dans :

- `app/src/main/assets/scripts/i18n/fr.json`
- `app/src/main/assets/scripts/i18n/en.json`

Exemple (à placer dans `index.sections.options.customPages.pages`) :

```json
"ma-page": {
  "title": "Ma page",
  "description": "Ouvrir ma nouvelle page personnalisée."
}
```

## 5) Exemple complet minimal

### 1) Fichier HTML

`app/src/main/assets/ma-page.html`

```html
<!DOCTYPE html>
<html lang="fr">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Ma page</title>
    <link rel="stylesheet" href="styles/ma-page.css" />
  </head>
  <body>
    <header class="page-header" data-i18n="aria-label:pages.header.aria">
      <a
        class="page-header__back"
        href="index.html"
        data-i18n="pages.header.back;aria-label:pages.header.backAria;title:pages.header.backAria"
      >
        Retour
      </a>
      <h1 class="page-header__title" data-i18n="pages.header.title">Page</h1>
    </header>

    <main class="page-content">
      <p data-i18n="pages.ma-page.intro">Bienvenue sur ma page !</p>
    </main>

    <script src="scripts/i18n/i18n.js"></script>
    <script src="scripts/boot.js"></script>
  </body>
</html>
```

### 2) Entrée JSON

`app/src/main/assets/config/custom-pages.json`

```json
{
  "id": "ma-page",
  "path": "ma-page.html",
  "titleKey": "index.sections.options.customPages.pages.ma-page.title",
  "descriptionKey": "index.sections.options.customPages.pages.ma-page.description",
  "openMode": "standalone",
  "css": ["styles/ma-page.css"],
  "js": []
}
```

### 3) Traductions

`app/src/main/assets/scripts/i18n/fr.json`

```json
"ma-page": {
  "title": "Ma page",
  "description": "Ouvrir ma nouvelle page personnalisée.",
  "intro": "Bienvenue sur ma page !"
}
```

`app/src/main/assets/scripts/i18n/en.json`

```json
"ma-page": {
  "title": "My page",
  "description": "Open my new custom page.",
  "intro": "Welcome to my page!"
}
```

---

✅ Une fois les fichiers déposés + l’entrée JSON ajoutée, la page apparaît automatiquement dans l’app (menu **Pages personnalisées** dans les options).
