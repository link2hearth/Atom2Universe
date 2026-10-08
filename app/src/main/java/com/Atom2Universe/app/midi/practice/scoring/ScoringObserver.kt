package com.Atom2Universe.app.midi.practice.scoring

import android.util.Log

/**
 * Listener interface for scoring updates.
 * UI components implement this to receive real-time updates.
 */
interface ScoringListener {
    /**
     * Called when metrics are updated (after each note event)
     */
    fun onMetricsUpdated(metrics: ScoringMetrics, event: NoteEvent?)

    /**
     * Called when a streak milestone is reached (10, 25, 50, 100)
     */
    fun onStreakMilestone(streak: Int)

    /**
     * Called when combo multiplier changes
     */
    fun onComboChanged(multiplier: Int)

    /**
     * Called when session is complete
     */
    fun onSessionComplete(finalMetrics: ScoringMetrics)
}

/**
 * Passive observer that tracks all scoring metrics during practice.
 *
 * IMPORTANT: This class ONLY observes and NEVER modifies the practice flow.
 * It receives events from PianoPracticeFragment and updates metrics accordingly.
 *
 * Usage:
 * 1. Create instance with total expected notes
 * 2. Call onNoteHit/onNoteMissed/onNoteHeld as events occur
 * 3. Subscribe listeners for UI updates
 * 4. Call getMetrics() anytime for current state
 */
class ScoringObserver(
    totalExpectedNotes: Int = 0
) {
    companion object {
        private const val TAG = "ScoringObserver"
    }

    // Current metrics state (immutable updates for thread safety)
    private var _metrics = ScoringMetrics(totalExpectedNotes = totalExpectedNotes)
    val metrics: ScoringMetrics get() = _metrics

    // Listeners for UI updates
    private val listeners = mutableListOf<ScoringListener>()

    // Track notes currently being held (for hold judgment)
    private val heldNotes = mutableMapOf<Int, Long>() // note -> startTimeMs

    /**
     * Set the total number of expected notes (call when MIDI file is loaded)
     */
    fun setTotalExpectedNotes(count: Int) {
        _metrics = _metrics.copy(totalExpectedNotes = count)
        notifyListeners(null)
    }

    /**
     * Register a listener for scoring updates
     */
    fun addListener(listener: ScoringListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Unregister a listener
     */
    fun removeListener(listener: ScoringListener) {
        listeners.remove(listener)
    }

    /**
     * Called when user hits a note with good timing
     *
     * @param note MIDI note number
     * @param timingOffsetMs How early/late (negative = early, positive = late)
     * @param isPerfect True if timing was within "perfect" window (tighter than "good")
     * @param velocity Note velocity (for potential velocity accuracy tracking)
     */
    fun onNoteHit(note: Int, timingOffsetMs: Long, isPerfect: Boolean, velocity: Int) {
        val event = NoteEvent.NoteHit(note, timingOffsetMs, isPerfect, velocity)

        // Track that this note is being held
        heldNotes[note] = System.currentTimeMillis()

        // Calculate new streak
        val newStreak = _metrics.currentStreak + 1
        val newBestStreak = maxOf(_metrics.bestStreak, newStreak)

        // Calculate combo multiplier based on streak
        val newCombo = when {
            newStreak >= ScoringPoints.COMBO_4X_THRESHOLD -> 4
            newStreak >= ScoringPoints.COMBO_3X_THRESHOLD -> 3
            newStreak >= ScoringPoints.COMBO_2X_THRESHOLD -> 2
            else -> 1
        }
        val comboChanged = newCombo != _metrics.comboMultiplier

        // Calculate points (base points * combo multiplier)
        val basePoints = if (isPerfect) ScoringPoints.PERFECT_NOTE else ScoringPoints.GOOD_NOTE
        val pointsEarned = basePoints * _metrics.comboMultiplier

        // Check for streak bonuses
        val streakBonus = when (newStreak) {
            10 -> ScoringPoints.STREAK_BONUS_10
            25 -> ScoringPoints.STREAK_BONUS_25
            50 -> ScoringPoints.STREAK_BONUS_50
            100 -> ScoringPoints.STREAK_BONUS_100
            else -> 0
        }

        // Update metrics
        _metrics = _metrics.copy(
            goodNotes = _metrics.goodNotes + 1,
            perfectNotes = if (isPerfect) _metrics.perfectNotes + 1 else _metrics.perfectNotes,
            currentStreak = newStreak,
            bestStreak = newBestStreak,
            comboMultiplier = newCombo,
            maxComboReached = maxOf(_metrics.maxComboReached, newCombo),
            score = _metrics.score + pointsEarned + streakBonus,
            totalTimingOffsetMs = _metrics.totalTimingOffsetMs + kotlin.math.abs(timingOffsetMs),
            notesWithTiming = _metrics.notesWithTiming + 1
        )

        Log.d(TAG, "onNoteHit: note=$note timing=${timingOffsetMs}ms perfect=$isPerfect " +
                "streak=$newStreak combo=${newCombo}x score=${_metrics.score}")

        notifyListeners(event)

        // Notify streak milestones
        if (newStreak in listOf(10, 25, 50, 100)) {
            listeners.forEach { it.onStreakMilestone(newStreak) }
        }

        // Notify combo changes
        if (comboChanged) {
            listeners.forEach { it.onComboChanged(newCombo) }
        }
    }

    /**
     * Called when user releases a note (to check if held long enough)
     *
     * @param note MIDI note number
     * @param heldPercent How much of the expected duration was held (0.0 - 1.0+)
     * @param isSuccess True if held long enough
     */
    fun onNoteHeld(note: Int, heldPercent: Float, isSuccess: Boolean) {
        val event = NoteEvent.NoteHeld(note, heldPercent, isSuccess)

        // Remove from held notes tracking
        heldNotes.remove(note)

        // If released early, we might want to penalize slightly
        // But we don't break the streak for early release - only for misses
        if (!isSuccess) {
            // Could add early release tracking here if desired
            Log.d(TAG, "onNoteHeld: note=$note released early (${(heldPercent * 100).toInt()}%)")
        } else {
            // Note was held correctly - the hit was already counted as "good"
            // This confirms it as "perfect" if timing was also good
            Log.d(TAG, "onNoteHeld: note=$note held successfully (${(heldPercent * 100).toInt()}%)")
        }

        notifyListeners(event)
    }

    /**
     * Called when an expected note passes without being played
     *
     * @param note MIDI note number
     * @param expectedTimeMs When the note should have been played
     */
    fun onNoteMissed(note: Int, expectedTimeMs: Long) {
        val event = NoteEvent.NoteMissed(note, expectedTimeMs)

        // Reset streak and combo on miss
        val streakWasActive = _metrics.currentStreak > 0

        _metrics = _metrics.copy(
            missedNotes = _metrics.missedNotes + 1,
            currentStreak = 0,
            comboMultiplier = 1
        )

        Log.d(TAG, "onNoteMissed: note=$note - streak reset, misses=${_metrics.missedNotes}")

        notifyListeners(event)

        // Notify combo reset if we had an active streak
        if (streakWasActive) {
            listeners.forEach { it.onComboChanged(1) }
        }
    }

    /**
     * Called when user plays a note that wasn't expected (wrong note or wrong time)
     *
     * @param note MIDI note number
     * @param playedTimeMs When the note was played
     */
    fun onWrongNote(note: Int, playedTimeMs: Long) {
        val event = NoteEvent.WrongNote(note, playedTimeMs)

        // Wrong notes break the streak but are tracked separately from misses
        val streakWasActive = _metrics.currentStreak > 0

        _metrics = _metrics.copy(
            wrongNotes = _metrics.wrongNotes + 1,
            currentStreak = 0,
            comboMultiplier = 1
        )

        Log.d(TAG, "onWrongNote: note=$note - streak reset, wrong=${_metrics.wrongNotes}")

        notifyListeners(event)

        if (streakWasActive) {
            listeners.forEach { it.onComboChanged(1) }
        }
    }

    /**
     * Called when practice session ends
     */
    fun onSessionComplete() {
        _metrics = _metrics.copy(isComplete = true)

        Log.i(TAG, "onSessionComplete: final score=${_metrics.score} " +
                "accuracy=${_metrics.accuracy}% grade=${_metrics.grade} " +
                "bestStreak=${_metrics.bestStreak}")

        listeners.forEach { it.onSessionComplete(_metrics) }
    }

    /**
     * Reset all metrics for a new session
     */
    fun reset(totalExpectedNotes: Int = 0) {
        heldNotes.clear()
        _metrics = ScoringMetrics(totalExpectedNotes = totalExpectedNotes)
        notifyListeners(null)
    }

    private fun notifyListeners(event: NoteEvent?) {
        listeners.forEach { it.onMetricsUpdated(_metrics, event) }
    }
}
