package com.Atom2Universe.app.sf2creator.data

import com.Atom2Universe.app.sf2creator.util.Sf2Constants
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import java.io.File
import kotlin.math.log2

/**
 * Represents a recorded audio sample for use in SF2 creation.
 * Contains all synthesis parameters matching Sf2SampleEntity.
 *
 * @property id Unique identifier for the sample
 * @property name Display name of the sample
 * @property audioFile The recorded WAV file
 * @property sampleRate Sample rate in Hz (e.g., 44100)
 * @property channels Number of audio channels (1 for mono)
 * @property bitDepth Bits per sample (16 for 16-bit PCM)
 * @property rootNote The MIDI note number that represents the original pitch (0-127)
 * @property detectedFrequency The detected fundamental frequency in Hz
 * @property keyRangeStart Start of the key range this sample covers (0-127)
 * @property keyRangeEnd End of the key range this sample covers (0-127)
 * @property velRangeStart Start of the velocity range (0-127)
 * @property velRangeEnd End of the velocity range (0-127)
 * @property loopStart Sample index where loop begins (0 if no loop)
 * @property loopEnd Sample index where loop ends (0 if no loop)
 * @property hasLoop Whether this sample has loop points defined
 */
data class SampleData(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val audioFile: File,
    val sampleRate: Int = Sf2Constants.DEFAULT_SAMPLE_RATE,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val rootNote: Int = Sf2Constants.DEFAULT_ROOT_NOTE,
    val detectedFrequency: Float = 261.63f, // Default C4 frequency

    // ==================== Key/Velocity Range ====================
    val keyRangeStart: Int = Sf2Constants.MIDI_NOTE_MIN,
    val keyRangeEnd: Int = Sf2Constants.MIDI_NOTE_MAX,
    val velRangeStart: Int = Sf2Constants.VELOCITY_MIN,
    val velRangeEnd: Int = Sf2Constants.VELOCITY_MAX,

    // ==================== Loop ====================
    val loopStart: Int = 0,
    val loopEnd: Int = 0,
    val hasLoop: Boolean = false,
    val sampleModes: Int = 0, // SF2 sampleModes: 0=no loop, 1=loop continuously, 3=loop then release

    // ==================== Volume/Attenuation ====================
    val attenuation: Int = Sf2Constants.DEFAULT_ATTENUATION_CB,

    // ==================== Tuning ====================
    val coarseTune: Int = Sf2Constants.DEFAULT_COARSE_TUNE,
    val fineTuneCents: Int = Sf2Constants.DEFAULT_FINE_TUNE_CENTS,
    val scaleTuning: Int = Sf2Constants.DEFAULT_SCALE_TUNING,

    // ==================== Volume Envelope (DAHDSR) - SF2 Native Units ====================
    // All times in timecents: -12000 = instant, 0 = 1 second
    // Sustain in centibels: 0 = full volume, 1000 = silent
    val volEnvDelay: Int = Sf2Constants.DEFAULT_VOL_ENV_DELAY_TC,
    val volEnvAttack: Int = Sf2Constants.DEFAULT_VOL_ENV_ATTACK_TC,
    val volEnvHold: Int = Sf2Constants.DEFAULT_VOL_ENV_HOLD_TC,
    val volEnvDecay: Int = Sf2Constants.DEFAULT_VOL_ENV_DECAY_TC,
    val volEnvSustain: Int = Sf2Constants.DEFAULT_VOL_ENV_SUSTAIN_CB,
    val volEnvRelease: Int = Sf2Constants.DEFAULT_VOL_ENV_RELEASE_TC,

    // ==================== Modulation Envelope (DAHDSR) - SF2 Native Units ====================
    val modEnvDelay: Int = Sf2Constants.DEFAULT_MOD_ENV_DELAY_TC,
    val modEnvAttack: Int = Sf2Constants.DEFAULT_MOD_ENV_ATTACK_TC,
    val modEnvHold: Int = Sf2Constants.DEFAULT_MOD_ENV_HOLD_TC,
    val modEnvDecay: Int = Sf2Constants.DEFAULT_MOD_ENV_DECAY_TC,
    val modEnvSustain: Int = Sf2Constants.DEFAULT_MOD_ENV_SUSTAIN_CB,
    val modEnvRelease: Int = Sf2Constants.DEFAULT_MOD_ENV_RELEASE_TC,
    val modEnvToPitch: Int = Sf2Constants.DEFAULT_MOD_ENV_TO_PITCH,
    val modEnvToFilterFc: Int = Sf2Constants.DEFAULT_MOD_ENV_TO_FILTER_FC,

    // ==================== Vibrato LFO - SF2 Native Units ====================
    val vibLfoDelay: Int = Sf2Constants.DEFAULT_VIB_LFO_DELAY_TC,
    val vibLfoFreq: Int = Sf2Constants.DEFAULT_VIB_LFO_FREQ_CENTS,
    val vibLfoToPitch: Int = Sf2Constants.DEFAULT_LFO_TO_PITCH,

    // ==================== Modulation LFO - SF2 Native Units ====================
    val modLfoDelay: Int = Sf2Constants.DEFAULT_MOD_LFO_DELAY_TC,
    val modLfoFreq: Int = Sf2Constants.DEFAULT_MOD_LFO_FREQ_CENTS,
    val modLfoToPitch: Int = Sf2Constants.DEFAULT_LFO_TO_PITCH,
    val modLfoToFilterFc: Int = Sf2Constants.DEFAULT_LFO_TO_FILTER_FC,
    val modLfoToVolume: Int = Sf2Constants.DEFAULT_LFO_TO_VOLUME,

    // ==================== Filter - SF2 Native Units ====================
    // filterFc in absolute cents (13500 = ~20kHz)
    val filterFc: Int = Sf2Constants.DEFAULT_FILTER_CUTOFF_CENTS,
    val filterQ: Int = Sf2Constants.DEFAULT_FILTER_RESONANCE_CB,

    // ==================== Effects ====================
    val chorusSend: Int = Sf2Constants.DEFAULT_CHORUS_SEND,
    val reverbSend: Int = Sf2Constants.DEFAULT_REVERB_SEND,

    // ==================== Pan ====================
    val pan: Int = Sf2Constants.DEFAULT_PAN,

    // ==================== Exclusive Class ====================
    val exclusiveClass: Int = Sf2Constants.EXCLUSIVE_CLASS_NONE,

    // ==================== Key-to-Envelope Scaling ====================
    val keyToVolEnvHold: Int = 0,    // GEN 39 - Timecents per key
    val keyToVolEnvDecay: Int = 0,   // GEN 40 - Timecents per key
    val keyToModEnvHold: Int = 0,    // GEN 31 - Timecents per key
    val keyToModEnvDecay: Int = 0,   // GEN 32 - Timecents per key

    // ==================== Fixed Key/Velocity ====================
    val fixedKey: Int = -1,          // GEN 46 - -1 = not fixed, 0-127 = fixed note
    val fixedVelocity: Int = -1,     // GEN 47 - -1 = not fixed, 0-127 = fixed velocity

    // ==================== Sample Header Fields ====================
    val pitchCorrection: Int = 0     // Pitch correction in cents (-99 to +99) from sample header
) {
    /**
     * Duration of the sample in seconds based on file size.
     */
    val durationSeconds: Float
        get() {
            if (!audioFile.exists()) return 0f
            // WAV file: subtract 44 bytes header, calculate from PCM data
            val dataSize = (audioFile.length() - 44).coerceAtLeast(0)
            val bytesPerSample = bitDepth / 8
            val totalSamples = dataSize / (channels * bytesPerSample)
            return totalSamples.toFloat() / sampleRate
        }

    /**
     * Total number of samples (per channel) in the audio data.
     */
    val sampleCount: Int
        get() {
            if (!audioFile.exists()) return 0
            val dataSize = (audioFile.length() - 44).coerceAtLeast(0)
            val bytesPerSample = bitDepth / 8
            return (dataSize / (channels * bytesPerSample)).toInt()
        }

    /**
     * Returns the note name (e.g., "C4", "F#5") for the root note.
     */
    fun getRootNoteName(): String {
        return midiNoteToName(rootNote)
    }

    companion object {
        /**
         * Convert MIDI note number to note name (e.g., 60 -> "C4")
         */
        fun midiNoteToName(midiNote: Int): String = Sf2UnitConverter.midiNoteToName(midiNote)

        /**
         * Convert note name to MIDI note number (e.g., "C4" -> 60)
         */
        fun nameToMidiNote(name: String): Int? = Sf2UnitConverter.nameToMidiNote(name)

        /**
         * Convert frequency to MIDI note number.
         */
        fun frequencyToMidiNote(frequency: Float): Int = Sf2UnitConverter.frequencyToMidiNote(frequency)

        /**
         * Convert MIDI note number to frequency.
         */
        fun midiNoteToFrequency(midiNote: Int): Float = Sf2UnitConverter.midiNoteToFrequency(midiNote)
    }
}

/**
 * Represents a complete SF2 project with metadata and samples.
 */
data class Sf2Project(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val samples: List<SampleData> = emptyList(),
    val presetNumber: Int = 0,
    val bankNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

/**
 * Result of pitch detection.
 */
data class PitchResult(
    val frequency: Float,
    val midiNote: Int,
    val noteName: String,
    val confidence: Float, // 0.0 to 1.0
    val cents: Int // Deviation from perfect pitch in cents (-50 to +50)
) {
    companion object {
        fun fromFrequency(frequency: Float, confidence: Float = 1.0f): PitchResult {
            if (frequency <= 0f) return UNKNOWN

            val midiNote = SampleData.frequencyToMidiNote(frequency)
            val perfectFreq = SampleData.midiNoteToFrequency(midiNote)

            val cents = if (perfectFreq > 0f) {
                (1200 * log2(frequency / perfectFreq)).toInt().coerceIn(-50, 50)
            } else {
                0
            }

            return PitchResult(
                frequency = frequency,
                midiNote = midiNote,
                noteName = SampleData.midiNoteToName(midiNote),
                confidence = confidence,
                cents = cents
            )
        }

        val UNKNOWN = PitchResult(0f, 60, "C4", 0f, 0)
    }
}
