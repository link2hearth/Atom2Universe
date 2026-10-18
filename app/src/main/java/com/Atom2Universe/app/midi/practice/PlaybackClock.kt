package com.Atom2Universe.app.midi.practice

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * Horloge partagée pour synchroniser le thread audio et les vues visuelles.
 *
 * Cette classe fournit une source de temps unique pour:
 * - PracticePlaybackController (thread audio)
 * - FallingNotesView (animation visuelle)
 * - SheetMusicView (partition défilante)
 *
 * L'horloge utilise System.nanoTime() comme base de temps monotone et
 * gère la conversion en position MIDI (ms) avec support du tempo variable.
 */
class PlaybackClock {

    // État de l'horloge
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // Temps de référence (en nanosecondes)
    private var startTimeNs: Long = 0L

    // Position de départ (en ms MIDI)
    private var startPositionMs: Long = 0L

    // Position courante mise à jour par le thread audio
    private val currentPositionMs = AtomicLong(0L)

    // Vitesse de lecture (multiplicateur de tempo)
    @Volatile
    private var speedMultiplier: Float = 1.0f

    /**
     * Démarre l'horloge à partir d'une position donnée
     */
    @Synchronized
    fun start(fromPositionMs: Long, speed: Float = 1.0f) {
        startTimeNs = System.nanoTime()
        startPositionMs = fromPositionMs
        speedMultiplier = speed
        currentPositionMs.set(fromPositionMs)
        isPaused.set(false)
        isRunning.set(true)
    }

    /**
     * Met en pause l'horloge
     */
    @Synchronized
    fun pause() {
        if (isRunning.get() && !isPaused.get()) {
            // Sauvegarder la position actuelle
            currentPositionMs.set(getCurrentPositionMs())
            isPaused.set(true)
        }
    }

    /**
     * Reprend l'horloge après une pause
     */
    @Synchronized
    fun resume() {
        if (isRunning.get() && isPaused.get()) {
            // Redémarrer depuis la position sauvegardée
            startTimeNs = System.nanoTime()
            startPositionMs = currentPositionMs.get()
            isPaused.set(false)
        }
    }

    /**
     * Arrête l'horloge
     */
    @Synchronized
    fun stop() {
        isRunning.set(false)
        isPaused.set(false)
        currentPositionMs.set(0L)
    }

    /**
     * Seek à une nouvelle position
     */
    @Synchronized
    fun seekTo(positionMs: Long) {
        startTimeNs = System.nanoTime()
        startPositionMs = positionMs
        currentPositionMs.set(positionMs)
    }

    /**
     * Change la vitesse de lecture
     * Recalcule les références pour éviter les sauts de position
     */
    @Synchronized
    fun setSpeed(speed: Float) {
        if (isRunning.get() && !isPaused.get()) {
            // Sauvegarder la position actuelle avant de changer la vitesse
            val currentPos = getCurrentPositionMs()
            startTimeNs = System.nanoTime()
            startPositionMs = currentPos
            currentPositionMs.set(currentPos)
        }
        speedMultiplier = speed
    }

    /**
     * Retourne la vitesse actuelle
     */
    fun getSpeed(): Float = speedMultiplier

    /**
     * Retourne la position courante en millisecondes MIDI.
     * C'est LA méthode centrale de synchronisation.
     *
     * Appelée par:
     * - Le thread audio pour savoir quand jouer les événements
     * - FallingNotesView pour positionner les notes
     * - SheetMusicView pour positionner le curseur
     */
    fun getCurrentPositionMs(): Long {
        if (!isRunning.get() || isPaused.get()) {
            return currentPositionMs.get()
        }

        val elapsedNs = System.nanoTime() - startTimeNs
        val elapsedRealMs = elapsedNs / 1_000_000L
        val elapsedAdjustedMs = (elapsedRealMs * speedMultiplier).roundToLong()

        return startPositionMs + elapsedAdjustedMs
    }

    /**
     * Met à jour la position courante (appelé par le thread audio)
     * Permet de forcer une synchronisation si nécessaire
     */
    @Suppress("unused")
    fun updatePosition(positionMs: Long) {
        currentPositionMs.set(positionMs)
    }

    /**
     * Vérifie si l'horloge est en cours d'exécution
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * Vérifie si l'horloge est en pause
     */
    fun isPaused(): Boolean = isPaused.get()
}
