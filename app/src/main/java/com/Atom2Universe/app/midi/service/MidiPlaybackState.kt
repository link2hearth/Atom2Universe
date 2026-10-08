package com.Atom2Universe.app.midi.service

import android.content.Context
import com.Atom2Universe.app.AudioPlaybackManager
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.widget.MusicWidgetController

/**
 * Singleton pour exposer l'état du playback MIDI au widget et autres composants.
 * Permet au widget de récupérer les infos de la piste en cours sans accéder
 * directement au service.
 */
object MidiPlaybackState {

    private var currentTrack: MidiTrack? = null
    private var isPlaying: Boolean = false
    private var hasNext: Boolean = false
    private var hasPrevious: Boolean = false

    /**
     * Met à jour l'état depuis le service.
     * Appelé par MidiPlaybackService quand l'état change réellement
     * (play, pause, stop, changement de piste).
     * Notifie le widget.
     */
    fun updateState(
        context: Context,
        track: MidiTrack?,
        playing: Boolean,
        canNext: Boolean,
        canPrevious: Boolean
    ) {
        val stateChanged = currentTrack?.id != track?.id ||
            isPlaying != playing ||
            hasNext != canNext ||
            hasPrevious != canPrevious

        currentTrack = track
        isPlaying = playing
        hasNext = canNext
        hasPrevious = canPrevious

        // Notifier le widget seulement si l'état a réellement changé
        if (stateChanged) {
            MusicWidgetController.notifyStateChanged(context)
        }
    }

    /**
     * Met à jour l'état interne sans notifier le widget.
     * Utilisé pour les mises à jour de position fréquentes.
     */
    fun updateStateQuiet(
        track: MidiTrack?,
        playing: Boolean,
        canNext: Boolean,
        canPrevious: Boolean
    ) {
        currentTrack = track
        isPlaying = playing
        hasNext = canNext
        hasPrevious = canPrevious
    }

    /**
     * Signale que le playback MIDI a démarré.
     * Enregistre auprès de AudioPlaybackManager.
     */
    fun onPlaybackStarted(context: Context, stopCallback: () -> Unit) {
        AudioPlaybackManager.registerPlayback(
            AudioPlaybackManager.AudioSource.MIDI,
            stopCallback
        )
        MusicWidgetController.notifyStateChanged(context)
    }

    /**
     * Signale que le playback MIDI s'est arrêté.
     */
    fun onPlaybackStopped(context: Context) {
        isPlaying = false
        AudioPlaybackManager.unregisterPlayback(AudioPlaybackManager.AudioSource.MIDI)
        MusicWidgetController.notifyStateChanged(context)
    }

    /**
     * Récupère la piste en cours de lecture.
     */
    fun getCurrentTrack(): MidiTrack? = currentTrack

    /**
     * Vérifie si le playback MIDI est en cours.
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * Vérifie s'il y a une piste suivante.
     */
    fun hasNext(): Boolean = hasNext

    /**
     * Vérifie s'il y a une piste précédente.
     */
    fun hasPrevious(): Boolean = hasPrevious

    /**
     * Réinitialise l'état (appelé quand le service est détruit).
     */
    fun reset() {
        currentTrack = null
        isPlaying = false
        hasNext = false
        hasPrevious = false
    }
}
