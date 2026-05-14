package com.Atom2Universe.app.midi.practice.scoring

/**
 * All scoring metrics tracked during a practice session.
 * All values are always tracked in memory, regardless of display settings.
 */
data class ScoringMetrics(
    // Note counts
    val totalExpectedNotes: Int = 0,
    val perfectNotes: Int = 0,      // Good timing + held correctly
    val goodNotes: Int = 0,         // Good timing (includes perfect)
    val missedNotes: Int = 0,       // Notes that passed without being played
    val wrongNotes: Int = 0,        // Notes played at wrong time or wrong pitch

    // Streaks
    val currentStreak: Int = 0,     // Current consecutive good notes
    val bestStreak: Int = 0,        // Best streak this session

    // Combo system
    val comboMultiplier: Int = 1,   // Current multiplier (1x, 2x, 3x, 4x max)
    val maxComboReached: Int = 1,   // Highest multiplier reached

    // Score
    val score: Long = 0,            // Total points

    // Timing stats (for detailed analysis)
    val totalTimingOffsetMs: Long = 0,  // Sum of all timing offsets (for average)
    val notesWithTiming: Int = 0,       // Count for average calculation

    // Session info
    val sessionStartTimeMs: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false
) {
    /**
     * Calculated accuracy percentage (0-100)
     */
    val accuracy: Float
        get() = if (goodNotes + missedNotes + wrongNotes > 0) {
            (goodNotes.toFloat() / (goodNotes + missedNotes + wrongNotes)) * 100f
        } else 0f

    /**
     * Average timing offset in milliseconds (how early/late on average)
     */
    val averageTimingOffsetMs: Float
        get() = if (notesWithTiming > 0) {
            totalTimingOffsetMs.toFloat() / notesWithTiming
        } else 0f

    /**
     * Progress percentage through the track
     */
    val progressPercent: Float
        get() = if (totalExpectedNotes > 0) {
            ((goodNotes + missedNotes).toFloat() / totalExpectedNotes) * 100f
        } else 0f

    /**
     * Grade based on accuracy (S, A, B, C, D, F)
     */
    val grade: String
        get() = when {
            accuracy >= 95f -> "S"
            accuracy >= 90f -> "A"
            accuracy >= 80f -> "B"
            accuracy >= 70f -> "C"
            accuracy >= 60f -> "D"
            else -> "F"
        }
}

/**
 * Result of a single note event for the observer
 */
sealed class NoteEvent {
    data class NoteHit(
        val note: Int,
        val timingOffsetMs: Long,   // Negative = early, positive = late
        val isPerfectTiming: Boolean,
        val velocity: Int
    ) : NoteEvent()

    data class NoteHeld(
        val note: Int,
        val heldPercent: Float,     // 0.0 - 1.0+
        val isSuccess: Boolean
    ) : NoteEvent()

    data class NoteMissed(
        val note: Int,
        val expectedTimeMs: Long
    ) : NoteEvent()

    data class WrongNote(
        val note: Int,
        val playedTimeMs: Long
    ) : NoteEvent()
}

/**
 * Points awarded for different achievements
 */
object ScoringPoints {
    const val PERFECT_NOTE = 100        // Perfect timing + held correctly
    const val GOOD_NOTE = 75            // Good timing
    const val EARLY_RELEASE = 25        // Good timing but released early

    // Combo thresholds
    const val COMBO_2X_THRESHOLD = 10   // Notes for 2x multiplier
    const val COMBO_3X_THRESHOLD = 25   // Notes for 3x multiplier
    const val COMBO_4X_THRESHOLD = 50   // Notes for 4x multiplier (max)

    // Bonus points
    const val STREAK_BONUS_10 = 500     // Bonus at 10 streak
    const val STREAK_BONUS_25 = 1500    // Bonus at 25 streak
    const val STREAK_BONUS_50 = 5000    // Bonus at 50 streak
    const val STREAK_BONUS_100 = 15000  // Bonus at 100 streak
}
