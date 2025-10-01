# Plan d'amélioration du lecteur MIDI chiptune

## Constats après lecture du code existant
- Le parser MIDI se limite aux évènements `noteOn`, `noteOff` et tempo, les changements de programme ne sont pas exploités.
- Toutes les notes sont jouées avec un simple oscillateur carré identique, ce qui réduit la richesse timbrale.
- Aucun traitement spécifique n'est appliqué au canal percussion (canal 10) ni de gestion avancée des enveloppes.
- La planification des notes repose sur `setTimeout`, ce qui peut induire du jitter lors de longues séquences ou sur des machines lentes.
- Il n'existe pas de paramétrage pour ajuster le mixage global (limiteur, filtre, panoramique).

## Liste des tâches proposées
1. **Introduire des timbres chiptune variés** en tirant parti des évènements de changement de programme pour sélectionner des formes d'onde, largeurs d'impulsion et enveloppes adaptées (**terminé**).
2. **Ajouter un rendu dédié aux percussions du canal 10** via un générateur de bruit et des enveloppes rapides (**terminé** : bruit blanc mis en cache, presets de timbres et nettoyage des voix dédiées).
3. **Mettre en place un planificateur audio à fenêtre glissante (look-ahead)** pour réduire la dépendance à `setTimeout` et fiabiliser le timing (**terminé** : ordonnancement par tranches de 250 ms avec rattrapage automatique en cas de retard).
4. Implémenter un LFO optionnel (vibrato/tremolo) configurable par programme pour apporter du mouvement aux notes tenues.
5. Ajouter un contrôle de mixage global (limiteur doux ou compresseur simple) pour éviter la saturation lorsque plusieurs notes se superposent.

Chaque point pourra être détaillé au fur et à mesure des itérations afin d'ajuster le périmètre selon les retours d'écoute.

## Vérification des étapes réalisées

- ✅ **Étape 1** – Les changements de programme sont convertis en timbres spécifiques avec enveloppes, vibratos et formes d'onde adaptés via `getInstrumentSettings`, assurant la variété attendue pour les instruments mélodiques.
- ✅ **Étape 2** – Le canal 10 bénéficie d'un moteur de percussions dédié (`getPercussionSettings`) qui combine bruit filtré et composantes tonales pour les principaux coups de batterie.
- ✅ **Étape 3** – Le séquençage audio repose sur un planificateur à fenêtre glissante (`processScheduler`) qui précharge les notes 250 ms à l'avance pour fiabiliser le timing.
- ✅ **Étape 4** – Un LFO configurable par programme module désormais la hauteur (vibrato) et/ou l'amplitude (tremolo) des voix, permettant d'ajouter du mouvement aux notes tenues tout en conservant des réglages spécifiques à chaque famille d'instrument.
- ✅ **Étape 5** – Un bus master dédié applique un compresseur doux configuré en mode limiteur afin de lisser les crêtes lorsque plusieurs voix se superposent, évitant ainsi la saturation sans écraser la dynamique globale.

