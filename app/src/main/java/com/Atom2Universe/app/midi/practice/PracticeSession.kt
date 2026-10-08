package com.Atom2Universe.app.midi.practice

/**
 * Mode de pratique des mains (filtrage visuel uniquement, n'affecte pas l'audio)
 */
enum class HandPracticeMode {
    BOTH_HANDS,       // Les deux mains
    LEFT_HAND_ONLY,   // Main gauche uniquement
    RIGHT_HAND_ONLY   // Main droite uniquement
}

/**
 * Arguments pour lancer le mode pratique piano
 */
data class PracticeArgs(
    val trackFilePath: String,
    val channelNumber: Int,
    val noteRangeMin: Int,
    val noteRangeMax: Int,
    val instrumentName: String,
    val trackTitle: String,
    val trackIndex: Int,
    val programNumber: Int = 0,  // MIDI program number (0-127)

    // Mode deux mains (optionnel)
    val isTwoHandsMode: Boolean = false,
    val leftHandChannel: Int = -1,
    val rightHandChannel: Int = -1,
    val leftHandName: String = "",
    val rightHandName: String = "",
    val leftHandNoteRangeMin: Int = 0,
    val leftHandNoteRangeMax: Int = 0,
    val rightHandNoteRangeMin: Int = 0,
    val rightHandNoteRangeMax: Int = 0
)

/**
 * Note programmée pour le mode pratique (falling notes)
 */
data class ScheduledNote(
    val note: Int,           // MIDI note 0-127
    val startTimeMs: Long,   // Timestamp absolu en ms
    val durationMs: Long,    // Durée de la note en ms
    val velocity: Int,       // Vélocité 0-127
    val channel: Int = 0,    // Canal MIDI (pour distinguer main gauche/droite)
    val isLeftHand: Boolean = false  // True si main gauche (notes basses)
) {
    val endTimeMs: Long get() = startTimeMs + durationMs
}

/**
 * État de la session de pratique
 */
class PracticeSession(
    val args: PracticeArgs
) {
    companion object {
        private const val DEFAULT_TIMING_TOLERANCE_MS = 60L
        private const val DEFAULT_HOLD_DURATION_THRESHOLD = 0.5f  // 50% minimum hold
        // BUG FIX 3.33: Intervalle de sauvegarde periodique des stats
        private const val SAVE_INTERVAL_MS = 30_000L  // Sauvegarder toutes les 30 secondes
    }

    // BUG FIX 3.33: Callback pour persister les stats periodiquement
    var onStatsChanged: ((notesPlayed: Int, totalNotes: Int) -> Unit)? = null
    private var lastSaveTimeMs = 0L

    // Options
    var accompanimentEnabled: Boolean = true
        private set
    var showNoteNames: Boolean = false
        private set

    // Mode de pratique des mains (filtrage visuel uniquement)
    var handPracticeMode: HandPracticeMode = HandPracticeMode.BOTH_HANDS
        private set

    var midiInputEnabled: Boolean = true
        private set

    // LED du clavier externe
    var ledEnabled: Boolean = true
        private set
    var ledAdvanceTimeMs: Long = 0L  // 0 = immédiat (hit zone), >0 = en avance
        private set

    // Mode attente (wait mode)
    var waitModeEnabled: Boolean = false
        private set
    var waitModeGraceMs: Long = 500L  // 500ms par défaut
        private set

    // Validation de la durée de maintien des notes
    var holdDurationValidationEnabled: Boolean = true
        private set
    var holdDurationThreshold: Float = DEFAULT_HOLD_DURATION_THRESHOLD  // 0.0 - 1.0
        private set

    // Tracking des notes en cours de maintien
    // Map<noteNumber, Pair<startTimeMs, expectedScheduledNote>>
    private val activeHeldNotes = mutableMapOf<Int, Pair<Long, ScheduledNote?>>()

    // Statistiques de session
    var totalNotesInTrack: Int = 0
        private set
    var notesPlayed: Int = 0
        private set

    var timingToleranceMs: Long = DEFAULT_TIMING_TOLERANCE_MS
        private set

    /**
     * Active/désactive l'accompagnement des autres canaux
     */
    fun setAccompanimentEnabled(enabled: Boolean) {
        accompanimentEnabled = enabled
    }

    /**
     * Active/désactive l'affichage des noms de notes
     */
    fun setShowNoteNames(show: Boolean) {
        showNoteNames = show
    }

    /**
     * Définit le mode de pratique des mains (filtrage visuel uniquement, n'affecte pas l'audio)
     */
    fun setHandPracticeMode(mode: HandPracticeMode) {
        handPracticeMode = mode
    }

    /**
     * Active/désactive l'écoute MIDI externe
     */
    @Suppress("unused")
    fun setMidiInputEnabled(enabled: Boolean) {
        midiInputEnabled = enabled
    }

    /**
     * Active/désactive les LEDs du clavier externe
     */
    @Suppress("unused")
    fun setLedEnabled(enabled: Boolean) {
        ledEnabled = enabled
    }

    /**
     * Définit l'avance des LEDs en ms (0 = immédiat quand note entre dans hit zone)
     */
    @Suppress("unused")
    fun setLedAdvanceTimeMs(ms: Long) {
        ledAdvanceTimeMs = ms.coerceIn(0L, 500L)
    }

    /**
     * Active/désactive le mode attente
     */
    fun setWaitModeEnabled(enabled: Boolean) {
        waitModeEnabled = enabled
    }

    /**
     * Définit le délai de grâce du mode attente (100ms - 1000ms)
     */
    @Suppress("unused")
    fun setWaitModeGraceMs(ms: Long) {
        waitModeGraceMs = ms.coerceIn(100L, 1000L)
    }

    /**
     * Active/désactive la validation de durée de maintien des notes
     */
    fun setHoldDurationValidationEnabled(enabled: Boolean) {
        holdDurationValidationEnabled = enabled
    }

    /**
     * Définit le seuil minimum de maintien (0.0 - 1.0, ex: 0.5 = 50%)
     */
    @Suppress("unused")
    fun setHoldDurationThreshold(threshold: Float) {
        holdDurationThreshold = threshold.coerceIn(0.1f, 1.0f)
    }

    /**
     * Définit la tolérance d'alignement temporel (en ms)
     */
    fun setTimingToleranceMs(toleranceMs: Long) {
        timingToleranceMs = toleranceMs.coerceIn(10L, 500L)
    }

    /**
     * Définit le nombre total de notes dans la piste
     */
    fun setTotalNotes(count: Int) {
        totalNotesInTrack = count
    }

    enum class TimingJudgement {
        GOOD,
        BAD
    }

    enum class HoldJudgement {
        SUCCESS,       // Maintenu assez longtemps (>= seuil)
        RELEASED_EARLY // Relâché trop tôt (< seuil)
    }

    data class UserNoteResult(
        val timing: TimingJudgement,
        val isExpected: Boolean,
        val expectedNote: ScheduledNote?
    )

    data class NoteReleaseResult(
        val holdJudgement: HoldJudgement,
        val holdDurationMs: Long,
        val expectedDurationMs: Long,
        val holdPercentage: Float,  // 0.0 - 1.0+
        val expectedNote: ScheduledNote?
    )

    /**
     * Callback utilisateur (NoteOn) pour validation de la note jouée
     * Enregistre également le début du maintien pour la validation de durée
     */
    @Suppress("UNUSED_PARAMETER")
    fun onUserNoteOn(
        note: Int,
        velocity: Int,
        timestampMs: Long,
        expectedNotesWindow: List<ScheduledNote>
    ): UserNoteResult {
        val candidates = expectedNotesWindow.filter { it.note == note }
        val expectedNote = candidates.minByOrNull { kotlin.math.abs(timestampMs - it.startTimeMs) }

        // Enregistrer le début du maintien pour la validation de durée
        if (holdDurationValidationEnabled) {
            activeHeldNotes[note] = Pair(timestampMs, expectedNote)
        }

        val result = if (expectedNote == null) {
            UserNoteResult(TimingJudgement.BAD, false, null)
        } else {
            val deltaMs = timestampMs - expectedNote.startTimeMs
            val timing = if (kotlin.math.abs(deltaMs) <= timingToleranceMs) {
                notesPlayed++  // Incrémenter le compteur pour les notes réussies
                TimingJudgement.GOOD
            } else {
                TimingJudgement.BAD
            }
            UserNoteResult(timing, true, expectedNote)
        }

        // BUG FIX 3.33: Sauvegarder périodiquement les stats
        checkPeriodicSave()

        return result
    }

    /**
     * BUG FIX 3.33: Vérifie si une sauvegarde périodique est nécessaire
     */
    private fun checkPeriodicSave() {
        val now = System.currentTimeMillis()
        if (now - lastSaveTimeMs >= SAVE_INTERVAL_MS) {
            lastSaveTimeMs = now
            onStatsChanged?.invoke(notesPlayed, totalNotesInTrack)
        }
    }

    /**
     * BUG FIX 3.33: Force la sauvegarde immédiate des stats
     * À appeler lors de pause/stop/quitter
     */
    @Suppress("unused")
    fun forceSaveStats() {
        lastSaveTimeMs = System.currentTimeMillis()
        onStatsChanged?.invoke(notesPlayed, totalNotesInTrack)
    }

    /**
     * Callback utilisateur (NoteOff) pour la note relâchée
     * Calcule si l'utilisateur a maintenu la note assez longtemps
     *
     * @param note Le numéro de note MIDI relâchée
     * @param releaseTimestampMs Le timestamp actuel (position de lecture)
     * @return NoteReleaseResult ou null si la validation de durée est désactivée
     */
    fun onUserNoteOff(note: Int, releaseTimestampMs: Long = System.currentTimeMillis()): NoteReleaseResult? {
        if (!holdDurationValidationEnabled) {
            activeHeldNotes.remove(note)
            return null
        }

        val heldInfo = activeHeldNotes.remove(note) ?: return null
        val (holdStartMs, expectedNote) = heldInfo

        // Calculer la durée de maintien
        val holdDurationMs = releaseTimestampMs - holdStartMs

        // Si pas de note attendue, on ne peut pas valider la durée
        if (expectedNote == null) {
            return NoteReleaseResult(
                holdJudgement = HoldJudgement.RELEASED_EARLY,
                holdDurationMs = holdDurationMs,
                expectedDurationMs = 0L,
                holdPercentage = 0f,
                expectedNote = null
            )
        }

        // Calculer le pourcentage de durée maintenue
        val expectedDurationMs = expectedNote.durationMs
        val holdPercentage = if (expectedDurationMs > 0) {
            holdDurationMs.toFloat() / expectedDurationMs.toFloat()
        } else {
            1f  // Notes instantanées sont toujours "réussies"
        }

        // Déterminer si le maintien est suffisant
        val holdJudgement = if (holdPercentage >= holdDurationThreshold) {
            HoldJudgement.SUCCESS
        } else {
            HoldJudgement.RELEASED_EARLY
        }

        return NoteReleaseResult(
            holdJudgement = holdJudgement,
            holdDurationMs = holdDurationMs,
            expectedDurationMs = expectedDurationMs,
            holdPercentage = holdPercentage,
            expectedNote = expectedNote
        )
    }

    /**
     * Efface toutes les notes actuellement suivies (appelé lors de stop/restart)
     */
    fun clearActiveHeldNotes() {
        activeHeldNotes.clear()
    }
}
