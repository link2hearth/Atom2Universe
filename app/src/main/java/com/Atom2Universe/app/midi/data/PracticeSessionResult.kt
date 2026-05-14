package com.Atom2Universe.app.midi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing practice session results for history tracking.
 * Each record represents one completed practice session.
 */
@Entity(tableName = "practice_sessions")
data class PracticeSessionResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Timestamp when the session was completed */
    val timestampMs: Long = System.currentTimeMillis(),

    /** Path to the MIDI file that was practiced */
    val trackFilePath: String,

    /** Display title of the track */
    val trackTitle: String,

    /** MIDI channel number that was practiced */
    val channelNumber: Int,

    /** Instrument name for the practiced channel */
    val instrumentName: String,

    /** Final grade (S, A, B, C, D, F) */
    val grade: String,

    /** Final score */
    val score: Long,

    /** Accuracy percentage (0-100) */
    val accuracy: Float,

    /** Number of perfect notes (timing within ~30ms) */
    val perfectNotes: Int,

    /** Number of good notes (timing within tolerance) */
    val goodNotes: Int,

    /** Number of missed notes (expected but not played) */
    val missedNotes: Int,

    /** Number of wrong notes (played but not expected) */
    val wrongNotes: Int,

    /** Best consecutive streak in the session */
    val bestStreak: Int,

    /** Total expected notes in the track */
    val totalExpectedNotes: Int,

    /** Highest combo multiplier reached (1-4) */
    val maxComboReached: Int = 1,

    /** Playback speed used (0.1 - 1.0) */
    val playbackSpeed: Float = 1.0f,

    /** Duration of the session in milliseconds */
    val sessionDurationMs: Long = 0L
)
