# Plan de diagnostic pour l’aperçu MIDI

1. **Reproduire le problème**
   - Charger un fichier MIDI sur la page dédiée.
   - Observer que la lecture démarre immédiatement et qu’aucune barre d’aperçu ne tombe sur les claviers.

2. **Inspecter la planification des notes**
   - Examiner `scheduleNote` dans `scripts/modules/chiptune-player.js` pour comprendre comment l’audio et les événements visuels sont programmés.
   - Vérifier le calcul des temporisations `startDelay`/`endDelay` et la valeur configurée `MIDI_PLAYBACK_PREVIEW_LEAD_SECONDS`.

3. **Tracer le chemin de lecture**
   - Étudier la méthode `play()` qui initialise la planification afin de confirmer à quel moment `previewLead` est appliqué.
   - Contrôler que `startScheduler` et `scheduleNoteVisualization` reçoivent des temps cohérents.

4. **Valider l’écart temporel attendu**
   - Calculer manuellement le délai entre l’instant présent, l’animation d’aperçu et le démarrage audio.
   - Confirmer que l’événement `onNoteOn` est déclenché `previewLead` secondes avant l’audio.

5. **Corriger et vérifier**
   - Ajuster le calcul du temps de démarrage pour éviter un double ajout de `previewLead`.
   - Rejouer le morceau pour vérifier que les barres tombent bien ~2 s avant que le son ne soit émis, sur les deux claviers.
