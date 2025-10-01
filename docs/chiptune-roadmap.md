# Plan d'amélioration du lecteur MIDI chiptune

## Constats après lecture du code existant
- Le parser MIDI se limite aux évènements `noteOn`, `noteOff` et tempo, les changements de programme ne sont pas exploités.
- Toutes les notes sont jouées avec un simple oscillateur carré identique, ce qui réduit la richesse timbrale.
- Aucun traitement spécifique n'est appliqué au canal percussion (canal 10) ni de gestion avancée des enveloppes.
- La planification des notes repose sur `setTimeout`, ce qui peut induire du jitter lors de longues séquences ou sur des machines lentes.
- Il n'existe pas de paramétrage pour ajuster le mixage global (limiteur, filtre, panoramique).

## Liste des tâches proposées
1. **Introduire des timbres chiptune variés** en tirant parti des évènements de changement de programme pour sélectionner des formes d'onde, largeurs d'impulsion et enveloppes adaptées (terminé).
2. Ajouter un rendu dédié aux percussions du canal 10 via un générateur de bruit et des enveloppes rapides.
3. Mettre en place un planificateur audio à fenêtre glissante (look-ahead) pour réduire la dépendance à `setTimeout` et fiabiliser le timing.
4. Implémenter un LFO optionnel (vibrato/tremolo) configurable par programme pour apporter du mouvement aux notes tenues.
5. Ajouter un contrôle de mixage global (limiteur doux ou compresseur simple) pour éviter la saturation lorsque plusieurs notes se superposent.

Chaque point pourra être détaillé au fur et à mesure des itérations afin d'ajuster le périmètre selon les retours d'écoute.
