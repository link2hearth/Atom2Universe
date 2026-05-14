package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a sample within an instrument.
 * Contains audio file reference and all synthesis parameters.
 *
 * SF2 Hierarchy: Project → Program → PresetZone → Instrument → Sample (this)
 *
 * Parameters here are SAMPLE-SPECIFIC and are ADDITIVE with
 * instrument-global and preset-zone parameters.
 *
 * IMPORTANT: All time/envelope parameters are stored in NATIVE SF2 UNITS:
 * - Time values: timecents (1200 * log2(seconds)), -12000 = instant
 * - Sustain levels: centibels of attenuation (0 = full volume, 1000 = silent)
 * - Filter cutoff: absolute cents (1200 * log2(freq/8.176))
 *
 * This ensures lossless import/export - no conversions needed during SF2 I/O.
 * Conversions to human-readable units (ms, %, Hz) happen only in the UI layer.
 */
@Entity(
    tableName = "sf2_samples",
    foreignKeys = [
        ForeignKey(
            entity = Sf2InstrumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("instrumentId")]
)
data class Sf2SampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val instrumentId: Long,
    val name: String,
    val audioFilePath: String,
    val sampleRate: Int = 44100,

    // ==================== Key/Velocity Range ====================
    val rootNote: Int,
    val keyRangeStart: Int = 0,
    val keyRangeEnd: Int = 127,
    val velRangeStart: Int = 0,       // GEN 44 lo - Velocity range start
    val velRangeEnd: Int = 127,       // GEN 44 hi - Velocity range end

    // ==================== Loop ====================
    val loopStart: Int = 0,
    val loopEnd: Int = 0,
    val hasLoop: Boolean = false,
    // SF2 sampleModes (GEN 54): 0=no loop, 1=loop continuously, 3=loop then release
    val sampleModes: Int = 0,

    // ==================== Volume/Attenuation ====================
    // Volume attenuation in centibels (0 = full volume, 480 = -48 dB)
    val attenuation: Int = 0,         // GEN 48

    // ==================== Tuning ====================
    val coarseTune: Int = 0,          // GEN 51 - Transposition in semitones (-120 to +120)
    val fineTuneCents: Int = 0,       // GEN 52 - Fine tuning in cents (-99 to +99)
    val scaleTuning: Int = 100,       // GEN 56 - Scale tuning (0-1200, 100 = normal)

    // ==================== Volume Envelope (DAHDSR) - SF2 Native Units ====================
    // All times in timecents: -12000 = instant (~1ms), 0 = 1 second
    val volEnvDelay: Int = -12000,    // GEN 33 - Delay before envelope starts (timecents)
    val volEnvAttack: Int = -12000,   // GEN 34 - Attack time (timecents)
    val volEnvHold: Int = -12000,     // GEN 35 - Hold time after attack (timecents)
    val volEnvDecay: Int = -12000,    // GEN 36 - Decay time (timecents)
    val volEnvSustain: Int = 0,       // GEN 37 - Sustain attenuation (centibels, 0=full, 1000=silent)
    val volEnvRelease: Int = -12000,  // GEN 38 - Release time (timecents)

    // ==================== Modulation Envelope (DAHDSR) - SF2 Native Units ====================
    val modEnvDelay: Int = -12000,    // GEN 25 - Delay before mod envelope starts (timecents)
    val modEnvAttack: Int = -12000,   // GEN 26 - Attack time (timecents)
    val modEnvHold: Int = -12000,     // GEN 27 - Hold time (timecents)
    val modEnvDecay: Int = -12000,    // GEN 28 - Decay time (timecents)
    val modEnvSustain: Int = 0,       // GEN 29 - Sustain attenuation (centibels, 0=full, 1000=silent)
    val modEnvRelease: Int = -12000,  // GEN 30 - Release time (timecents)

    // ==================== Mod Envelope Destinations ====================
    val modEnvToPitch: Int = 0,       // GEN 7  - Mod env → pitch (cents, -12000 to +12000)
    val modEnvToFilterFc: Int = 0,    // GEN 11 - Mod env → filter cutoff (cents)

    // ==================== Vibrato LFO - SF2 Native Units ====================
    val vibLfoDelay: Int = -12000,    // GEN 23 - Delay before vibrato starts (timecents)
    val vibLfoFreq: Int = 0,          // GEN 24 - Frequency in cents (0 = ~8.176 Hz)
    val vibLfoToPitch: Int = 0,       // GEN 6  - Vibrato depth (cents, -12000 to +12000)

    // ==================== Modulation LFO - SF2 Native Units ====================
    val modLfoDelay: Int = -12000,    // GEN 21 - Delay before mod LFO starts (timecents)
    val modLfoFreq: Int = 0,          // GEN 22 - Frequency in cents (0 = ~8.176 Hz)
    val modLfoToPitch: Int = 0,       // GEN 5  - Mod LFO → pitch (cents)
    val modLfoToFilterFc: Int = 0,    // GEN 10 - Mod LFO → filter cutoff (cents)
    val modLfoToVolume: Int = 0,      // GEN 13 - Mod LFO → volume (centibels, tremolo)

    // ==================== Low-pass Filter - SF2 Native Units ====================
    val filterFc: Int = 13500,        // GEN 8  - Cutoff in absolute cents (13500 = ~20kHz)
    val filterQ: Int = 0,             // GEN 9  - Resonance/Q in centibels (0-960)

    // ==================== Effects ====================
    val chorusSend: Int = 0,          // GEN 15 - Chorus send (0-1000, units of 0.1%)
    val reverbSend: Int = 0,          // GEN 16 - Reverb send (0-1000, units of 0.1%)

    // ==================== Pan ====================
    val pan: Int = 0,                 // GEN 17 - Stereo pan (-500 = left, 0 = center, +500 = right)

    // ==================== Exclusive Class (for drums) ====================
    val exclusiveClass: Int = 0,      // GEN 57 - Exclusive class (0 = none, 1-127 = mute group)

    // ==================== Key-based Envelope Scaling ====================
    // These parameters modify envelope times based on MIDI note number
    val keyToVolEnvHold: Int = 0,     // GEN 39 - Key to vol env hold (timecents per key)
    val keyToVolEnvDecay: Int = 0,    // GEN 40 - Key to vol env decay (timecents per key)
    val keyToModEnvHold: Int = 0,     // GEN 31 - Key to mod env hold (timecents per key)
    val keyToModEnvDecay: Int = 0,    // GEN 32 - Key to mod env decay (timecents per key)

    // ==================== Fixed Key/Velocity ====================
    // -1 means not fixed (use played note/velocity), 0-127 = fixed value
    val fixedKey: Int = -1,           // GEN 46 - Fixed MIDI key (-1 = off, 0-127 = fixed note)
    val fixedVelocity: Int = -1,      // GEN 47 - Fixed velocity (-1 = off, 0-127 = fixed vel)

    // ==================== Sample Header Fields ====================
    // These are stored in the shdr (sample header) chunk, not as generators
    val pitchCorrection: Int = 0,     // Pitch correction in cents (-99 to +99) from sample header

    // ==================== Source Reference (Patch-based Import) ====================
    // For imported samples, these reference the original SF2 file directly
    // instead of extracting to WAV files. This enables efficient patch-based export.
    /**
     * Path to the source SF2 file. Null for native samples (created from WAV).
     * When set, audio data is read directly from this file at export time.
     */
    val sourceFilePath: String? = null,

    /**
     * Byte offset within the source SF2's smpl chunk where this sample's audio data starts.
     * Only valid when sourceFilePath is set.
     */
    val sourceSmplOffset: Long = 0,

    /**
     * Size in bytes of this sample's audio data in the source SF2's smpl chunk.
     * Only valid when sourceFilePath is set.
     */
    val sourceSampleSize: Long = 0,

    /**
     * Whether this sample has been extracted to a local WAV file for editing.
     * - false: audio is read from sourceFilePath at sourceSmplOffset (import mode)
     * - true: audio is read from audioFilePath (extracted for editing)
     * For native samples (sourceFilePath = null), this is always effectively true.
     */
    val isExtracted: Boolean = false,

    // ==================== Hybrid Passthrough Tracking ====================
    // These fields support lossless round-trip import/export
    /**
     * SHA-256 hash of the sample's state at import time.
     * Used to detect if the sample has been modified by comparing current state.
     * Null for samples created from scratch (not imported).
     */
    val originalStateHash: String? = null,

    /**
     * Whether this sample has been modified by the user since import.
     * When true, the sample must be re-encoded at export time.
     * When false, the original audio chunk can be copied directly.
     */
    val isModifiedByUser: Boolean = false,

    /**
     * Bitmap of modification types (see ModificationFlags).
     * Tracks what was modified: audio, params, name, loop, etc.
     * 0 = no modifications
     */
    val modificationFlags: Int = 0
)
