package com.Atom2Universe.app.midi.service

import com.Atom2Universe.app.midi.data.MidiTrack
import kotlin.random.Random

/**
 * Gestionnaire de la queue de lecture MIDI
 * Gère: auto-play next, shuffle, repeat modes
 */
class PlaybackQueueManager {

    enum class RepeatMode {
        NONE,      // Pas de repeat
        ONE,       // Repeat le track actuel
        ALL        // Repeat toute la queue
    }

    data class QueueState(
        val queue: List<MidiTrack>,
        val currentIndex: Int,
        val repeatMode: RepeatMode,
        val shuffleEnabled: Boolean
    )

    private var queue: List<MidiTrack> = emptyList()
    private var originalQueue: List<MidiTrack> = emptyList() // Pour restaurer après shuffle
    private var currentIndex: Int = -1
    private var repeatMode: RepeatMode = RepeatMode.NONE
    private var shuffleEnabled: Boolean = false

    // Callbacks
    private var onQueueChangedCallback: ((QueueState) -> Unit)? = null
    private var onCurrentTrackChangedCallback: ((MidiTrack?) -> Unit)? = null

    /**
     * Définit la queue de lecture
     * @param tracks Liste des tracks à jouer
     * @param startIndex Index du track à démarrer (défaut: 0)
     */
    fun setQueue(tracks: List<MidiTrack>, startIndex: Int = 0) {
        originalQueue = tracks
        if (tracks.isEmpty()) {
            queue = emptyList()
            currentIndex = -1
            notifyQueueChanged()
            notifyCurrentTrackChanged()
            return
        }
        queue = if (shuffleEnabled) {
            shuffleQueue(tracks, startIndex)
        } else {
            tracks
        }

        currentIndex = if (shuffleEnabled) {
            0
        } else {
            startIndex.coerceIn(0, queue.size - 1)
        }

        notifyQueueChanged()
        notifyCurrentTrackChanged()
    }

    /**
     * Ajoute un track à la fin de la queue
     */
    fun addToQueue(track: MidiTrack) {
        val newQueue = queue.toMutableList()
        newQueue.add(track)
        queue = newQueue

        originalQueue = originalQueue.toMutableList().apply { add(track) }

        notifyQueueChanged()
    }

    /**
     * Insère un track après le track en cours
     */
    @Suppress("unused")
    fun playNext(track: MidiTrack) {
        val newQueue = queue.toMutableList()
        val insertIndex = (currentIndex + 1).coerceAtMost(newQueue.size)
        newQueue.add(insertIndex, track)
        queue = newQueue

        originalQueue = originalQueue.toMutableList().apply {
            val currentTrackId = getCurrentTrack()?.id
            val originalInsertIndex = if (currentTrackId != null) {
                val originalIndex = indexOfFirst { it.id == currentTrackId }
                if (originalIndex >= 0) (originalIndex + 1).coerceAtMost(size) else size
            } else {
                size
            }
            add(originalInsertIndex, track)
        }

        notifyQueueChanged()
    }

    /**
     * Retire un track de la queue
     */
    @Suppress("unused")
    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) {
            return
        }

        val newQueue = queue.toMutableList()
        val removedTrack = newQueue[index]
        newQueue.removeAt(index)
        queue = newQueue

        originalQueue = originalQueue.toMutableList().apply {
            val originalIndex = indexOfFirst { it.id == removedTrack.id }
            if (originalIndex != -1) {
                removeAt(originalIndex)
            }
        }

        // Ajuste currentIndex si nécessaire
        currentIndex = when {
            queue.isEmpty() -> -1
            currentIndex > index -> currentIndex - 1
            currentIndex == index -> currentIndex.coerceAtMost(queue.size - 1)
            else -> currentIndex
        }

        notifyQueueChanged()
        notifyCurrentTrackChanged()
    }

    /**
     * Vide la queue
     */
    @Suppress("unused")
    fun clearQueue() {
        queue = emptyList()
        originalQueue = emptyList()
        currentIndex = -1

        notifyQueueChanged()
        notifyCurrentTrackChanged()
    }

    /**
     * Passe au track suivant
     * @return Le track suivant, ou null si fin de queue
     */
    fun skipToNext(): MidiTrack? {
        if (queue.isEmpty()) {
            return null
        }
        when (repeatMode) {
            RepeatMode.ONE -> {
                // Repeat le même track
                return getCurrentTrack()
            }
            RepeatMode.ALL -> {
                // Passe au suivant, ou recommence au début
                currentIndex = (currentIndex + 1) % queue.size
            }
            RepeatMode.NONE -> {
                // Passe au suivant, ou retourne null si fin
                if (currentIndex >= queue.size - 1) {
                    return null
                }
                currentIndex++
            }
        }

        val nextTrack = getCurrentTrack()
        notifyCurrentTrackChanged()
        return nextTrack
    }

    /**
     * Retourne au track précédent
     * @return Le track précédent, ou null si début de queue
     */
    fun skipToPrevious(): MidiTrack? {
        if (queue.isEmpty()) {
            return null
        }
        when (repeatMode) {
            RepeatMode.ONE -> {
                // Repeat le même track
                return getCurrentTrack()
            }
            RepeatMode.ALL -> {
                // Passe au précédent, ou va à la fin
                currentIndex = if (currentIndex > 0) currentIndex - 1 else queue.size - 1
            }
            RepeatMode.NONE -> {
                // Passe au précédent, ou retourne null si début
                if (currentIndex <= 0) {
                    return null
                }
                currentIndex--
            }
        }

        val prevTrack = getCurrentTrack()
        notifyCurrentTrackChanged()
        return prevTrack
    }

    /**
     * Saute à un track spécifique par index
     */
    @Suppress("unused")
    fun skipToIndex(index: Int): MidiTrack? {
        if (index !in queue.indices) {
            return null
        }

        currentIndex = index
        val track = getCurrentTrack()
        notifyCurrentTrackChanged()
        return track
    }

    /**
     * Récupère le track actuellement en cours
     */
    fun getCurrentTrack(): MidiTrack? {
        return queue.getOrNull(currentIndex)
    }

    /**
     * Récupère l'index actuel
     */
    @Suppress("unused")
    fun getCurrentIndex(): Int {
        return currentIndex
    }

    /**
     * Récupère toute la queue
     */
    @Suppress("unused")
    fun getQueue(): List<MidiTrack> {
        return queue
    }

    /**
     * Récupère le nombre de tracks dans la queue
     */
    @Suppress("unused")
    fun getQueueSize(): Int {
        return queue.size
    }

    /**
     * Active/désactive le shuffle
     */
    fun toggleShuffle(): Boolean {
        shuffleEnabled = !shuffleEnabled

        if (shuffleEnabled) {
            // Shuffle la queue en gardant le track actuel en première position
            val currentTrack = getCurrentTrack()
            queue = shuffleQueue(originalQueue, currentIndex)
            currentIndex = when {
                queue.isEmpty() -> -1
                currentTrack == null -> -1
                else -> 0 // Le track actuel est maintenant en position 0
            }
        } else {
            // Restaure la queue originale
            val currentTrack = getCurrentTrack()
            queue = originalQueue
            // Trouve l'index du track actuel dans la queue originale
            currentIndex = when {
                queue.isEmpty() -> -1
                currentTrack == null -> -1
                else -> queue.indexOfFirst { it.id == currentTrack.id }.let { index ->
                    if (index >= 0) index else 0
                }
            }
        }

        notifyQueueChanged()
        return shuffleEnabled
    }

    /**
     * Change le mode repeat
     * @return Le nouveau mode repeat
     */
    fun cycleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }

        notifyQueueChanged()
        return repeatMode
    }

    /**
     * Définit le mode repeat
     */
    @Suppress("unused")
    fun setRepeatMode(mode: RepeatMode) {
        if (repeatMode != mode) {
            repeatMode = mode
            notifyQueueChanged()
        }
    }

    /**
     * Récupère le mode repeat actuel
     */
    fun getRepeatMode(): RepeatMode {
        return repeatMode
    }

    /**
     * Vérifie si shuffle est activé
     */
    fun isShuffleEnabled(): Boolean {
        return shuffleEnabled
    }

    /**
     * Vérifie s'il y a un track suivant disponible
     */
    fun hasNext(): Boolean {
        if (queue.isEmpty()) return false
        return when (repeatMode) {
            RepeatMode.ONE, RepeatMode.ALL -> true
            RepeatMode.NONE -> currentIndex < queue.size - 1
        }
    }

    /**
     * Vérifie s'il y a un track précédent disponible
     */
    fun hasPrevious(): Boolean {
        if (queue.isEmpty()) return false
        return when (repeatMode) {
            RepeatMode.ONE, RepeatMode.ALL -> true
            RepeatMode.NONE -> currentIndex > 0
        }
    }

    /**
     * Récupère l'état complet de la queue
     */
    fun getState(): QueueState {
        return QueueState(
            queue = queue,
            currentIndex = currentIndex,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled
        )
    }

    /**
     * Définit le callback de changement de queue
     */
    @Suppress("unused")
    fun setOnQueueChangedListener(listener: (QueueState) -> Unit) {
        onQueueChangedCallback = listener
    }

    /**
     * Définit le callback de changement de track actuel
     */
    fun setOnCurrentTrackChangedListener(listener: (MidiTrack?) -> Unit) {
        onCurrentTrackChangedCallback = listener
    }

    // === Private methods ===

    /**
     * Fisher-Yates shuffle algorithm - unbiased shuffle
     * Keeps the current track in first position
     */
    private fun shuffleQueue(tracks: List<MidiTrack>, currentIndex: Int): List<MidiTrack> {
        if (tracks.isEmpty()) return emptyList()

        val currentTrack = tracks.getOrNull(currentIndex)
        val otherTracks = tracks.toMutableList()

        // Retire le track actuel de la liste
        if (currentTrack != null) {
            otherTracks.removeAt(currentIndex)
        }

        // Fisher-Yates shuffle (unbiased algorithm)
        val random = Random(System.currentTimeMillis())
        for (i in otherTracks.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            // Swap elements at i and j
            val temp = otherTracks[i]
            otherTracks[i] = otherTracks[j]
            otherTracks[j] = temp
        }

        // Remet le track actuel en première position
        return if (currentTrack != null) {
            mutableListOf(currentTrack) + otherTracks
        } else {
            otherTracks
        }
    }

    private fun notifyQueueChanged() {
        onQueueChangedCallback?.invoke(getState())
    }

    private fun notifyCurrentTrackChanged() {
        onCurrentTrackChangedCallback?.invoke(getCurrentTrack())
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "PlaybackQueueManager"
    }
}
