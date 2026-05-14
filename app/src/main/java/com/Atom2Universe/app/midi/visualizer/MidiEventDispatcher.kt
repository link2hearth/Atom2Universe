package com.Atom2Universe.app.midi.visualizer

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton pour dispatcher les événements MIDI du service vers les composants UI
 *
 * Pattern Observer thread-safe permettant au NowPlayingFragment
 * d'observer les événements MIDI en temps réel
 */
object MidiEventDispatcher {

    // Listeners enregistrés (thread-safe)
    private val midiEventListeners = CopyOnWriteArrayList<MidiEventListener>()
    private val analysisListeners = CopyOnWriteArrayList<MidiAnalysisListener>()
    private val channelControlListeners = CopyOnWriteArrayList<ChannelControlListener>()

    // Canaux mutés (pas de son mais visualisation OK)
    private val mutedChannels = mutableSetOf<Int>()

    // Handler pour dispatcher sur le main thread
    private val mainHandler = Handler(Looper.getMainLooper())

    // Note tracker partagé
    private var noteTracker: MidiNoteTracker? = null

    // Cache des derniers résultats d'analyse (pour les nouveaux listeners)
    private var cachedAnalysisResult: AnalysisResult? = null

    /**
     * Résultat d'analyse caché
     */
    data class AnalysisResult(
        val noteMin: Int,
        val noteMax: Int,
        val displayMin: Int,
        val displayMax: Int,
        val tracks: List<MidiNoteTracker.TrackInfo>  // Pistes du fichier (pas limité à 16)
    )

    /**
     * Interface pour recevoir les événements MIDI en temps réel
     */
    interface MidiEventListener {
        fun onNoteOn(channel: Int, note: Int, velocity: Int)
        fun onNoteOff(channel: Int, note: Int)
        fun onProgramChange(channel: Int, program: Int)
        fun onAllNotesOff()
    }

    /**
     * Interface pour recevoir les résultats d'analyse du fichier MIDI
     */
    interface MidiAnalysisListener {
        fun onAnalysisComplete(
            noteMin: Int,
            noteMax: Int,
            displayMin: Int,
            displayMax: Int,
            tracks: List<MidiNoteTracker.TrackInfo>
        )
        fun onAnalysisReset()
    }

    /**
     * Interface pour recevoir les changements de contrôle de canal (mute/volume)
     * Utilisé par les moteurs de synthèse pour réagir aux changements UI
     */
    interface ChannelControlListener {
        fun onChannelMuteChanged(channel: Int, isMuted: Boolean)
        fun onChannelVolumeChanged(channel: Int, volume: Float)
    }

    /**
     * Initialise le note tracker pour un nouveau fichier MIDI
     */
    fun initializeTracker(context: android.content.Context): MidiNoteTracker {
        noteTracker = MidiNoteTracker(context).apply {
            // Connecter les callbacks du tracker au dispatcher
            onNoteOn = { channel, note, velocity ->
                dispatchNoteOn(channel, note, velocity)
            }
            onNoteOff = { channel, note ->
                dispatchNoteOff(channel, note)
            }
            onProgramChange = { channel, program ->
                dispatchProgramChange(channel, program)
            }
            onAnalysisComplete = { noteMin, noteMax, tracks ->
                dispatchAnalysisComplete(noteMin, noteMax, tracks)
            }
        }
        return noteTracker!!
    }

    /**
     * Récupère le tracker actuel (peut être null)
     */
    fun getTracker(): MidiNoteTracker? = noteTracker

    /**
     * Enregistre un listener pour les événements MIDI
     */
    fun addMidiEventListener(listener: MidiEventListener) {
        if (!midiEventListeners.contains(listener)) {
            midiEventListeners.add(listener)
        }
    }

    /**
     * Retire un listener
     */
    fun removeMidiEventListener(listener: MidiEventListener) {
        midiEventListeners.remove(listener)
    }

    /**
     * Enregistre un listener pour les analyses
     * Envoie immédiatement le dernier résultat d'analyse s'il existe
     */
    fun addAnalysisListener(listener: MidiAnalysisListener) {
        if (!analysisListeners.contains(listener)) {
            analysisListeners.add(listener)

            // Envoyer le résultat caché au nouveau listener s'il existe
            cachedAnalysisResult?.let { result ->
                mainHandler.post {
                    try {
                        listener.onAnalysisComplete(
                            result.noteMin,
                            result.noteMax,
                            result.displayMin,
                            result.displayMax,
                            result.tracks
                        )
                    } catch (e: Exception) { }
                }
            }
        }
    }

    /**
     * Retire un listener d'analyse
     */
    fun removeAnalysisListener(listener: MidiAnalysisListener) {
        analysisListeners.remove(listener)
    }

    /**
     * Enregistre un listener pour les contrôles de canal (mute/volume)
     */
    fun addChannelControlListener(listener: ChannelControlListener) {
        if (!channelControlListeners.contains(listener)) {
            channelControlListeners.add(listener)
        }
    }

    /**
     * Retire un listener de contrôle de canal
     */
    fun removeChannelControlListener(listener: ChannelControlListener) {
        channelControlListeners.remove(listener)
    }

    /**
     * Indique si des listeners MIDI visuels sont actifs.
     * Utilisé par le thread audio pour éviter les allocations inutiles en arrière-plan.
     */
    fun hasMidiEventListeners(): Boolean = midiEventListeners.isNotEmpty()

    /**
     * Traite des bytes MIDI bruts et dispatche vers les listeners
     * Appelé depuis le service de playback
     */
    fun processMidiBytes(midiBytes: ByteArray) {
        noteTracker?.processMidiBytes(midiBytes)
    }

    /**
     * Éteint toutes les notes visuelles sans réinitialiser l'analyse
     * Utilisé lors de la pause pour effacer les notes du piano virtuel
     */
    fun dispatchAllNotesOff() {
        mainHandler.post {
            midiEventListeners.forEach { it.onAllNotesOff() }
        }
    }

    /**
     * Reset quand la lecture s'arrête (pause, stop)
     * Garde le cache d'analyse pour les listeners qui reviennent
     */
    fun reset() {
        noteTracker?.reset()

        // Notifier tous les listeners
        mainHandler.post {
            midiEventListeners.forEach { it.onAllNotesOff() }
            analysisListeners.forEach { it.onAnalysisReset() }
        }
    }

    /**
     * Prépare le dispatcher pour un nouveau fichier MIDI
     * Efface tout le cache et remet à zéro les états
     * Appelé AVANT de charger un nouveau fichier
     */
    fun prepareForNewFile() {
        // Reset le tracker
        noteTracker?.reset()

        // Effacer le cache d'analyse (nouveau fichier = nouvelle analyse)
        cachedAnalysisResult = null

        // Réinitialiser les canaux mutés (nouveau fichier = pas de mutes)
        mutedChannels.clear()

        // Notifier tous les listeners de reset complet
        mainHandler.post {
            midiEventListeners.forEach {
                try {
                    it.onAllNotesOff()
                } catch (e: Exception) { }
            }
            analysisListeners.forEach {
                try {
                    it.onAnalysisReset()
                } catch (e: Exception) { }
            }
        }
    }

    /**
     * Prépare le dispatcher pour un changement de SoundFont
     * PRÉSERVE le cache d'analyse et les états de mute (même fichier MIDI)
     * Éteint seulement les notes actives pour éviter des notes bloquées
     * Appelé AVANT de recharger un SoundFont
     */
    fun prepareForSoundFontChange() {
        // Reset le tracker (éteint les notes actives)
        noteTracker?.reset()

        // NE PAS effacer le cache d'analyse (même fichier MIDI)
        // cachedAnalysisResult reste intact

        // NE PAS réinitialiser les mutes (l'utilisateur les a configurés)
        // mutedChannels reste intact

        // Éteindre les notes visuellement mais ne pas reset l'analyse
        mainHandler.post {
            midiEventListeners.forEach {
                try {
                    it.onAllNotesOff()
                } catch (e: Exception) { }
            }
            // NE PAS appeler onAnalysisReset - les claviers gardent leur configuration
        }
    }

    /**
     * Ré-envoie le résultat d'analyse caché à tous les listeners
     * Utile après un changement de SoundFont pour rafraîchir les claviers
     */
    fun resendCachedAnalysis() {
        val result = cachedAnalysisResult
        if (result == null) {
            return
        }

        mainHandler.post {
            analysisListeners.forEach { listener ->
                try {
                    listener.onAnalysisComplete(
                        result.noteMin,
                        result.noteMax,
                        result.displayMin,
                        result.displayMax,
                        result.tracks
                    )
                } catch (e: Exception) { }
            }
        }
    }

    /**
     * Efface les notes actives du tracker sans réinitialiser l'analyse.
     * Utilisé avant de synchroniser avec une nouvelle position.
     */
    fun clearTrackerActiveNotes() {
        noteTracker?.clearActiveNotes()
    }

    /**
     * Ré-envoie les notes actuellement actives à tous les listeners
     * Utile après un refresh des claviers pour afficher les notes en cours de lecture
     */
    fun resendActiveNotes() {
        val tracker = noteTracker
        if (tracker == null) {
            return
        }

        var totalNotes = 0

        // Pour chaque canal, récupérer les notes actives et les envoyer
        for (channel in 0 until MidiNoteTracker.TOTAL_CHANNELS) {
            val activeNotes = tracker.getActiveNotes(channel)
            for (note in activeNotes) {
                val velocity = tracker.getNoteVelocity(channel, note)
                if (velocity > 0) {
                    dispatchNoteOn(channel, note, velocity)
                    totalNotes++
                }
            }
        }
    }

    /**
     * Libère les ressources
     */
    fun release() {
        midiEventListeners.clear()
        analysisListeners.clear()
        noteTracker = null
        cachedAnalysisResult = null
    }

    // === Dispatch Methods (thread-safe, sur main thread) ===

    private fun dispatchNoteOn(channel: Int, note: Int, velocity: Int) {
        if (midiEventListeners.isEmpty()) return

        mainHandler.post {
            midiEventListeners.forEach { listener ->
                try {
                    listener.onNoteOn(channel, note, velocity)
                } catch (e: Exception) { }
            }
        }
    }

    private fun dispatchNoteOff(channel: Int, note: Int) {
        if (midiEventListeners.isEmpty()) return

        mainHandler.post {
            midiEventListeners.forEach { listener ->
                try {
                    listener.onNoteOff(channel, note)
                } catch (e: Exception) { }
            }
        }
    }

    private fun dispatchProgramChange(channel: Int, program: Int) {
        if (midiEventListeners.isEmpty()) return

        mainHandler.post {
            midiEventListeners.forEach { listener ->
                try {
                    listener.onProgramChange(channel, program)
                } catch (e: Exception) { }
            }
        }
    }

    private fun dispatchAnalysisComplete(
        noteMin: Int,
        noteMax: Int,
        tracks: List<MidiNoteTracker.TrackInfo>
    ) {
        val tracker = noteTracker ?: return
        val displayMin = tracker.displayRangeMin
        val displayMax = tracker.displayRangeMax

        // Cacher le résultat pour les listeners qui s'enregistrent plus tard
        cachedAnalysisResult = AnalysisResult(
            noteMin = noteMin,
            noteMax = noteMax,
            displayMin = displayMin,
            displayMax = displayMax,
            tracks = tracks
        )

        if (analysisListeners.isEmpty()) {
            return
        }

        mainHandler.post {
            analysisListeners.forEach { listener ->
                try {
                    listener.onAnalysisComplete(noteMin, noteMax, displayMin, displayMax, tracks)
                } catch (e: Exception) { }
            }
        }
    }

    /**
     * Retourne le résultat d'analyse caché (si disponible)
     */
    fun getCachedAnalysis(): AnalysisResult? = cachedAnalysisResult

    // === Gestion des canaux mutés ===

    /**
     * Mute ou unmute un canal
     */
    fun setChannelMuted(channel: Int, muted: Boolean) {
        val wasChanged = if (muted) {
            mutedChannels.add(channel)
        } else {
            mutedChannels.remove(channel)
        }

        if (wasChanged) {
            // Notifier les listeners de contrôle (moteurs de synthèse)
            channelControlListeners.forEach { listener ->
                try {
                    listener.onChannelMuteChanged(channel, muted)
                } catch (e: Exception) { }
            }
        }
    }

    /**
     * Vérifie si un canal est muté
     */
    fun isChannelMuted(channel: Int): Boolean = mutedChannels.contains(channel)

    /**
     * Notifie les listeners d'un changement de volume sur un canal
     * Appelé par l'UI quand le slider de volume change
     */
    fun notifyChannelVolumeChanged(channel: Int, volume: Float) {
        // Notifier les listeners de contrôle (moteurs de synthèse)
        channelControlListeners.forEach { listener ->
            try {
                listener.onChannelVolumeChanged(channel, volume)
            } catch (e: Exception) { }
        }
    }

    /**
     * Récupère la liste des canaux mutés
     */
    fun getMutedChannels(): Set<Int> = mutedChannels.toSet()

    /**
     * Réinitialise tous les mutes
     */
    fun clearMutes() {
        mutedChannels.clear()
    }
}
