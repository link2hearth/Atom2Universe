# Renseigner les fiches du tableau périodique

Chaque élément affiché dans la fenêtre modale lit ses textes via le système de localisation.
Pour renseigner ou modifier le contenu, il suffit de compléter les fichiers JSON par langue situés dans `scripts/i18n/`.

## Où placer le texte ?

Pour l’hydrogène, par exemple, la structure se trouve dans `scripts/i18n/fr.json` à la clé :

```
scripts.periodic.elements.element-001-hydrogene.details
```

La même clé existe dans `scripts/i18n/en.json` (pour l’anglais) et dans `scripts/i18n/embedded-resources.js` (fallback embarqué pour le chargement hors-ligne).

Chaque bloc `details` accepte :

- `summary` : une phrase d’accroche affichée en tête.
- `paragraphs` : un tableau de paragraphes (chaque entrée devient un `<p>`).
- `sources` : un tableau d’URL listé en bas de la fiche.

Si tu ne fournis que `summary`, la fiche affichera cette phrase seule. Si tu ajoutes un long texte et préfères ne pas gérer les paragraphes, tu peux simplement mettre tout le contenu dans `summary`.

## Exemple rempli

```json
"element-001-hydrogene": {
  "symbol": "H",
  "name": "Hydrogène",
  "details": {
    "summary": "Premier élément du tableau périodique, l’hydrogène est un gaz diatomique extrêmement léger.",
    "paragraphs": [
      "Constitué d’un proton et d’un électron, il représente environ 75 % de la masse baryonique de l’Univers observable.",
      "Sur Terre, on le rencontre rarement à l’état libre ; il se combine dans l’eau, les hydrocarbures et d’innombrables biomolécules.",
      "Dans Atom → Univers, compléter la collection d’hydrogène débloque les premières fusions et sert de base aux molécules plus complexes."
    ],
    "sources": [
      "https://www.larousse.fr/encyclopedie/divers/hydrogene/110473",
      "https://www.cnrs.fr/fr/le-cnrs-explique/hydrogene-lenergie-du-futur"
    ]
  }
}
```

Tu peux dupliquer ce modèle pour les autres éléments en adaptant les identifiants (`element-002-helium`, etc.) et en renseignant les textes souhaités pour chaque langue.
