package com.Atom2Universe.app

import android.content.Context
import android.content.Intent
import android.util.Log
import com.Atom2Universe.app.midi.service.MidiPlaybackService
import com.Atom2Universe.app.midi.service.MidiPlaybackState
import com.Atom2Universe.app.music.MusicPlaybackHolder
import com.Atom2Universe.app.radio.RadioPlaybackHolder
import androidx.appcompat.app.AlertDialog

/**
 * Gestionnaire centralisé pour éviter les conflits entre sources audio (Radio et MIDI).
 *
 * Utilisation:
 * - Avant de lancer une lecture, appeler requestPlayback(source, context, onGranted)
 * - Si une autre source joue, affiche un dialogue de choix
 * - Si aucun conflit, exécute directement onGranted
 *
 * Thread-safety: Toutes les méthodes sont synchronisées pour éviter les race conditions
 * lors des accès concurrents depuis différents threads (UI, services, callbacks).
 */
object AudioPlaybackManager {

    enum class AudioSource {
        RADIO,
        MIDI,
        MUSIC
    }

    // Lock object for synchronization - using dedicated object is cleaner than 'this'
    private val lock = Any()

    // Guarded by lock
    private var currentSource: AudioSource? = null
    private var stopCurrentCallback: (() -> Unit)? = null

    /**
     * Enregistre qu'une source a démarré la lecture.
     * Doit être appelé quand la lecture démarre effectivement.
     */
    fun registerPlayback(source: AudioSource, stopCallback: () -> Unit) {
        synchronized(lock) {
            currentSource = source
            stopCurrentCallback = stopCallback
        }
    }

    /**
     * Enregistre qu'une source a arrêté la lecture.
     */
    fun unregisterPlayback(source: AudioSource) {
        synchronized(lock) {
            if (currentSource == source) {
                currentSource = null
                stopCurrentCallback = null
            }
        }
    }

    /**
     * Vérifie si une source spécifique est en lecture.
     */
    fun isPlaying(source: AudioSource): Boolean {
        synchronized(lock) {
            return currentSource == source
        }
    }

    /**
     * Vérifie si une autre source est en lecture.
     */
    @Suppress("unused")
    fun hasConflict(requestingSource: AudioSource): Boolean {
        synchronized(lock) {
            return currentSource != null && currentSource != requestingSource
        }
    }

    /**
     * Demande la permission de lancer une lecture.
     * Si une autre source joue, affiche un dialogue de choix.
     *
     * @param requestingSource La source qui veut jouer
     * @param context Le contexte pour afficher le dialogue
     * @param onGranted Callback exécuté si la lecture est autorisée
     */
    fun requestPlayback(
        requestingSource: AudioSource,
        context: Context,
        onGranted: () -> Unit
    ) {
        // Capture state atomically under lock
        val (conflictSource, stopCallback) = synchronized(lock) {
            Pair(currentSource, stopCurrentCallback)
        }

        // Pas de conflit, on autorise directement
        if (conflictSource == null || conflictSource == requestingSource) {
            onGranted()
            return
        }

        // Conflit détecté, afficher dialogue de choix
        val currentSourceName = when (conflictSource) {
            AudioSource.RADIO -> context.getString(R.string.audio_source_radio)
            AudioSource.MIDI -> context.getString(R.string.audio_source_midi)
            AudioSource.MUSIC -> context.getString(R.string.audio_source_music)
        }
        val requestingSourceName = when (requestingSource) {
            AudioSource.RADIO -> context.getString(R.string.audio_source_radio)
            AudioSource.MIDI -> context.getString(R.string.audio_source_midi)
            AudioSource.MUSIC -> context.getString(R.string.audio_source_music)
        }

        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(context)
            .setTitle(R.string.audio_conflict_title)
            .setMessage(context.getString(R.string.audio_conflict_message, currentSourceName))
            .setPositiveButton(requestingSourceName) { _, _ ->
                // Arrête la source actuelle et lance la nouvelle
                // Note: stopCallback captured earlier, invoke outside lock to avoid deadlock
                stopCallback?.invoke()
                unregisterPlayback(conflictSource)
                onGranted()
            }
            .setNegativeButton(currentSourceName) { _, _ ->
                // Garde la source actuelle, ne fait rien
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Force l'arrêt de toutes les sources (minuteur de sommeil).
     *
     * Seule la dernière source inscrite a un rappel ; une radio relancée par le widget, le hub ou la
     * notification, ou une musique restaurée par le widget, n'est jamais inscrite. On arrête donc aussi
     * directement chaque lecteur qui joue encore.
     */
    fun stopAll(context: Context) {
        // Capture callback under lock, then invoke outside to avoid deadlock
        val callback = synchronized(lock) {
            val cb = stopCurrentCallback
            currentSource = null
            stopCurrentCallback = null
            cb
        }
        callback?.invoke()

        val app = context.applicationContext
        if (MusicPlaybackHolder.isPlaying()) MusicPlaybackHolder.stop(app)
        if (RadioPlaybackHolder.isPlaybackWanted() || RadioPlaybackHolder.isCurrentlyRecording()) RadioPlaybackHolder.stop(app)
        if (MidiPlaybackState.isPlaying()) {
            try {
                app.startService(Intent(app, MidiPlaybackService::class.java).setAction(MidiPlaybackService.ACTION_STOP))
            } catch (e: IllegalStateException) {
                // Service déjà arrêté et appli en arrière-plan : rien ne joue plus de ce côté.
                Log.w("AudioPlaybackManager", "Cannot stop MIDI service", e)
            }
        }
    }
}
