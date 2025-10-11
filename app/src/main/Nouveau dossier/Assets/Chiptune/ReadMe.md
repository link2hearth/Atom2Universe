# Bibliothèque chiptune locale

Ajoutez ici vos fichiers MIDI (extension `.mid` ou `.midi`) que vous souhaitez proposer dans la liste déroulante du lecteur 8-bit.

Pour afficher un morceau dans l'interface :

1. Déposez le fichier dans ce dossier.
2. Éditez `resources/chiptune/library.json` et ajoutez une entrée au tableau `tracks`, par exemple :
   ```json
   {
     "name": "Nom du morceau",
     "file": "Assets/Chiptune/mon-morceau.mid"
   }
   ```

L'interface recharge automatiquement la bibliothèque au chargement de la page.
