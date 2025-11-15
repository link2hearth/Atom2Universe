# Project Guidelines

Bienvenue dans Atom2Univers ! Voici les consignes à respecter lorsque tu interviens sur ce dépôt :

1. **Internationalisation**
   - Dès qu’un texte visible par l’utilisateur est modifié, ajoute ou mets à jour les entrées correspondantes dans les fichiers de langues (système i18n).
   - Utilise la méthode de fallback HTML (texte visible + data-i18n), pas la méthode JS avec defaultValue.
   - Vérifie que les clés sont cohérentes et évite les doublons.

2. **Configuration centralisée**
   - Toute variable destinée à être ajustée facilement doit vivre dans les fichiers du dossier `config/` plutôt que d’être codée en dur.
   - Documente brièvement dans ces fichiers la signification des nouvelles variables si nécessaire.

3. **Technologies principales**
   - Le projet est un jeu web construit en **JavaScript**, **CSS**, **HTML** et **JSON**.
   - Le cœur du jeu est un clicker principal entouré de nombreux modules offrant des bonus.
   - Chaque mini-jeu arcade possède son propre fichier JavaScript et CSS dédiés. Respecte cette séparation lorsque tu ajoutes du contenu.

4. **Collaboration**
   - Le propriétaire du dépôt est débutant en code, mais nous formons une équipe formidable. Explique tes choix et garde un ton pédagogique dans les messages de commit et de PR.

5. **Ressources binaires**
   - Ne propose pas de fichiers binaires (images, polices, etc.) dans tes contributions. Fournis uniquement des références ou des instructions pour que le propriétaire ajoute lui-même les ressources nécessaires.

6. **Sauvegardes arcade**
   - Après toute mise à jour du `gameState.arcadeProgress`, déclenche immédiatement `saveGame()` pour garantir la persistance des records.

Merci et bon travail !
