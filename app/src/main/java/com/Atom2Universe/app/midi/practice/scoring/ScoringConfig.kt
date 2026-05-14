package com.Atom2Universe.app.midi.practice.scoring

import android.content.Context

/**
 * User preferences for which scoring metrics to display.
 * All metrics are always tracked - this only controls visibility.
 *
 * The user can customize what they see to avoid discouragement.
 * Example: Show only "Good Notes" and "Streak", hide accuracy and misses.
 */
data class ScoringConfig(
    // Master toggle - show any scoring at all
    val scoringEnabled: Boolean = true,

    // Individual metric visibility
    val showScore: Boolean = true,
    val showAccuracy: Boolean = true,
    val showCurrentStreak: Boolean = true,
    val showBestStreak: Boolean = false,
    val showGoodNotes: Boolean = true,
    val showPerfectNotes: Boolean = false,
    val showMissedNotes: Boolean = false,
    val showCombo: Boolean = true,
    val showGrade: Boolean = false,

    // Display style
    val compactMode: Boolean = false,       // Smaller display
    val showAnimations: Boolean = true,     // Animate score changes
    val showStreakPopups: Boolean = true,   // "10 streak!" popup

    // Position on screen
    val displayPosition: DisplayPosition = DisplayPosition.TOP_RIGHT
) {
    enum class DisplayPosition {
        TOP_LEFT,
        TOP_RIGHT,
        TOP_CENTER,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    companion object {
        private const val PREFS_NAME = "scoring_config"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SHOW_SCORE = "show_score"
        private const val KEY_SHOW_ACCURACY = "show_accuracy"
        private const val KEY_SHOW_CURRENT_STREAK = "show_current_streak"
        private const val KEY_SHOW_BEST_STREAK = "show_best_streak"
        private const val KEY_SHOW_GOOD_NOTES = "show_good_notes"
        private const val KEY_SHOW_PERFECT_NOTES = "show_perfect_notes"
        private const val KEY_SHOW_MISSED_NOTES = "show_missed_notes"
        private const val KEY_SHOW_COMBO = "show_combo"
        private const val KEY_SHOW_GRADE = "show_grade"
        private const val KEY_COMPACT_MODE = "compact_mode"
        private const val KEY_SHOW_ANIMATIONS = "show_animations"
        private const val KEY_SHOW_STREAK_POPUPS = "show_streak_popups"
        private const val KEY_DISPLAY_POSITION = "display_position"

        /**
         * Load config from SharedPreferences
         */
        fun load(context: Context): ScoringConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ScoringConfig(
                scoringEnabled = prefs.getBoolean(KEY_ENABLED, true),
                showScore = prefs.getBoolean(KEY_SHOW_SCORE, true),
                showAccuracy = prefs.getBoolean(KEY_SHOW_ACCURACY, true),
                showCurrentStreak = prefs.getBoolean(KEY_SHOW_CURRENT_STREAK, true),
                showBestStreak = prefs.getBoolean(KEY_SHOW_BEST_STREAK, false),
                showGoodNotes = prefs.getBoolean(KEY_SHOW_GOOD_NOTES, true),
                showPerfectNotes = prefs.getBoolean(KEY_SHOW_PERFECT_NOTES, false),
                showMissedNotes = prefs.getBoolean(KEY_SHOW_MISSED_NOTES, false),
                showCombo = prefs.getBoolean(KEY_SHOW_COMBO, true),
                showGrade = prefs.getBoolean(KEY_SHOW_GRADE, false),
                compactMode = prefs.getBoolean(KEY_COMPACT_MODE, false),
                showAnimations = prefs.getBoolean(KEY_SHOW_ANIMATIONS, true),
                showStreakPopups = prefs.getBoolean(KEY_SHOW_STREAK_POPUPS, true),
                displayPosition = DisplayPosition.entries.getOrNull(
                    prefs.getInt(KEY_DISPLAY_POSITION, DisplayPosition.TOP_RIGHT.ordinal)
                ) ?: DisplayPosition.TOP_RIGHT
            )
        }

        /**
         * Save config to SharedPreferences
         */
        fun save(context: Context, config: ScoringConfig) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, config.scoringEnabled)
                .putBoolean(KEY_SHOW_SCORE, config.showScore)
                .putBoolean(KEY_SHOW_ACCURACY, config.showAccuracy)
                .putBoolean(KEY_SHOW_CURRENT_STREAK, config.showCurrentStreak)
                .putBoolean(KEY_SHOW_BEST_STREAK, config.showBestStreak)
                .putBoolean(KEY_SHOW_GOOD_NOTES, config.showGoodNotes)
                .putBoolean(KEY_SHOW_PERFECT_NOTES, config.showPerfectNotes)
                .putBoolean(KEY_SHOW_MISSED_NOTES, config.showMissedNotes)
                .putBoolean(KEY_SHOW_COMBO, config.showCombo)
                .putBoolean(KEY_SHOW_GRADE, config.showGrade)
                .putBoolean(KEY_COMPACT_MODE, config.compactMode)
                .putBoolean(KEY_SHOW_ANIMATIONS, config.showAnimations)
                .putBoolean(KEY_SHOW_STREAK_POPUPS, config.showStreakPopups)
                .putInt(KEY_DISPLAY_POSITION, config.displayPosition.ordinal)
                .apply()
        }

        /**
         * Preset: Encouragement mode - only positive metrics
         */
        fun encouragementPreset() = ScoringConfig(
            scoringEnabled = true,
            showScore = false,
            showAccuracy = false,
            showCurrentStreak = true,
            showBestStreak = false,
            showGoodNotes = true,
            showPerfectNotes = false,
            showMissedNotes = false,
            showCombo = false,
            showGrade = false,
            showStreakPopups = true
        )

        /**
         * Preset: Full stats - everything visible
         */
        fun fullStatsPreset() = ScoringConfig(
            scoringEnabled = true,
            showScore = true,
            showAccuracy = true,
            showCurrentStreak = true,
            showBestStreak = true,
            showGoodNotes = true,
            showPerfectNotes = true,
            showMissedNotes = true,
            showCombo = true,
            showGrade = true,
            showStreakPopups = true
        )

        /**
         * Preset: Minimal - just score and streak
         */
        fun minimalPreset() = ScoringConfig(
            scoringEnabled = true,
            showScore = true,
            showAccuracy = false,
            showCurrentStreak = true,
            showBestStreak = false,
            showGoodNotes = false,
            showPerfectNotes = false,
            showMissedNotes = false,
            showCombo = true,
            showGrade = false,
            compactMode = true,
            showStreakPopups = false
        )

        /**
         * Preset: Disabled - no scoring display
         */
        fun disabledPreset() = ScoringConfig(
            scoringEnabled = false
        )
    }

    /**
     * Check if any individual metric is visible
     */
    fun hasVisibleMetrics(): Boolean {
        return scoringEnabled && (
            showScore || showAccuracy || showCurrentStreak || showBestStreak ||
            showGoodNotes || showPerfectNotes || showMissedNotes || showCombo || showGrade
        )
    }
}
