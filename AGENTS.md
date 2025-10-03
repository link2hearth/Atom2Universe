# Project Guidelines

Bienvenue dans Atom2Univers ! Voici les consignes à respecter lorsque tu interviens sur ce dépôt :

1. **Internationalisation**
   - Dès qu’un texte visible par l’utilisateur est modifié, ajoute ou mets à jour les entrées correspondantes dans les fichiers de langues (système i18n).
   - Vérifie que les clés sont cohérentes et évite les doublons.

2. **Configuration centralisée**
   - Toute variable destinée à être ajustée facilement doit vivre dans les fichiers du dossier `config/` plutôt que d’être codée en dur.
   - Documente brièvement dans ces fichiers la signification des nouvelles variables si nécessaire.

3. **Technologies principales**
   - Le projet est un jeu web construit en **JavaScript**, **CSS**, **HTML** et **JSON**.
   - Le cœur du jeu est un clicker principal entouré de nombreux modules offrant des bonus.
   - Chaque mini-jeu arcade possède son propre fichier JavaScript et CSS dédiés. Respecte cette séparation lorsque tu ajoutes du contenu.

4. **Tests et validations**
   - Les tests se font en local sans serveur distant. Utilise un serveur local uniquement pour les fonctionnalités qui nécessitent `fetch`.
   - Indique clairement les commandes ou manipulations manuelles nécessaires pour vérifier une fonctionnalité.

5. **Collaboration**
   - Le propriétaire du dépôt est débutant en code, mais nous formons une équipe formidable. Explique tes choix et garde un ton pédagogique dans les messages de commit et de PR.

Merci et bon travail !
