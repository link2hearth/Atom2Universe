package com.Atom2Universe.app.sf2creator.util

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Utility class for converting between various SF2 units and standard units.
 *
 * SF2 uses specialized units:
 * - Timecents for time values (envelope times)
 * - Centibels for attenuation and sustain levels
 * - Absolute cents for filter frequency
 * - 0.1% units for effects send
 */
object Sf2UnitConverter {

    // ==================== Time Conversions ====================

    /**
     * Convert milliseconds to SF2 timecents.
     *
     * SF2 time = 2^(timecents/1200) seconds
     * So timecents = 1200 * log2(seconds) = 1200 * log2(ms/1000)
     *
     * @param ms Time in milliseconds
     * @return Time in SF2 timecents
     */
    fun msToTimecents(ms: Int): Int {
        // SF2 convention: -12000 timecents is the "instant" value (no delay/attack/release)
        // This is the standard default value in SF2 files
        if (ms <= 0) return Sf2Constants.TIMECENTS_DEFAULT_INSTANT
        // 1 ms also maps to ~-12000, so we use -12000 for very small values
        if (ms == 1) return Sf2Constants.TIMECENTS_DEFAULT_INSTANT
        val seconds = ms / 1000.0
        return (1200.0 * (kotlin.math.ln(seconds) / kotlin.math.ln(2.0)))
            .roundToInt()
            .coerceIn(Sf2Constants.TIMECENTS_MIN, Sf2Constants.TIMECENTS_MAX)
    }

    /**
     * Convert SF2 timecents to milliseconds.
     *
     * @param timecents Time in SF2 timecents
     * @return Time in milliseconds (0 for instant/-12000 or below)
     */
    fun timecentsToMs(timecents: Int): Int {
        // SF2 convention: -12000 timecents is "instant" (approximately 1ms)
        // Values at or below this threshold are treated as instant (0ms)
        if (timecents <= Sf2Constants.TIMECENTS_DEFAULT_INSTANT) return 0
        val seconds = 2.0.pow(timecents / 1200.0)
        return (seconds * 1000).toInt().coerceAtLeast(0)
    }

    // ==================== Volume/Attenuation Conversions ====================

    /**
     * Convert sustain percentage (0-100%) to SF2 centibels of attenuation.
     *
     * In SF2, sustain is expressed as attenuation from full volume:
     * - 100% sustain = 0 cB (no attenuation)
     * - 0% sustain = 1000 cB (-100 dB, effectively silent)
     *
     * @param percent Sustain level as percentage (0-100)
     * @return Sustain attenuation in centibels (0-1000)
     */
    fun sustainPercentToCentibels(percent: Int): Int {
        return ((100 - percent.coerceIn(0, 100)) * 10)
    }

    /**
     * Convert SF2 sustain centibels to percentage.
     *
     * @param centibels Sustain attenuation in centibels (0-1000)
     * @return Sustain level as percentage (0-100)
     */
    fun centibelsToSustainPercent(centibels: Int): Int {
        return (100 - (centibels.coerceIn(0, 1000) / 10))
    }

    /**
     * Convert attenuation in centibels to dB.
     *
     * @param centibels Attenuation in centibels
     * @return Attenuation in dB (negative value)
     */
    fun centibelsToDb(centibels: Int): Float {
        return -centibels / 10f
    }

    /**
     * Convert dB to centibels.
     * Note: SF2 only supports attenuation (negative dB or 0).
     *
     * @param db Attenuation in dB (should be <= 0)
     * @return Attenuation in centibels
     */
    fun dbToCentibels(db: Float): Int {
        return (-db * 10).toInt().coerceIn(0, Sf2Constants.ATTENUATION_CB_MAX)
    }

    // ==================== Filter Frequency Conversions ====================

    /**
     * Convert frequency in Hz to SF2 absolute cents for filter cutoff.
     *
     * SF2 filter FC: cents = 1200 * log2(freq / 8.176)
     * Range: 1500 (approximately 20 Hz) to 13500 (approximately 20 kHz)
     *
     * @param hz Frequency in Hz
     * @return Frequency in SF2 absolute cents (1500-13500)
     */
    fun hzToFilterCents(hz: Float): Int {
        if (hz <= 0f) return Sf2Constants.FILTER_CUTOFF_CENTS_MIN
        return (1200.0 * (kotlin.math.ln(hz.toDouble() / Sf2Constants.FILTER_REFERENCE_FREQ) / kotlin.math.ln(2.0)))
            .roundToInt()
            .coerceIn(Sf2Constants.FILTER_CUTOFF_CENTS_MIN, Sf2Constants.FILTER_CUTOFF_CENTS_MAX)
    }

    /**
     * Convert SF2 absolute cents to frequency in Hz.
     *
     * @param cents Frequency in SF2 absolute cents
     * @return Frequency in Hz
     */
    fun filterCentsToHz(cents: Int): Float {
        return (Sf2Constants.FILTER_REFERENCE_FREQ * 2.0.pow(cents / 1200.0)).toFloat()
    }

    /**
     * Convert filter resonance in centibels to dB.
     *
     * @param centibels Resonance in centibels (0-960)
     * @return Resonance in dB (0-96)
     */
    fun filterResonanceCbToDb(centibels: Int): Float {
        return centibels / 10f
    }

    /**
     * Convert filter resonance in dB to centibels.
     *
     * @param db Resonance in dB
     * @return Resonance in centibels
     */
    fun filterResonanceDbToCb(db: Float): Int {
        return (db * 10).toInt().coerceIn(
            Sf2Constants.FILTER_RESONANCE_CB_MIN,
            Sf2Constants.FILTER_RESONANCE_CB_MAX
        )
    }

    // ==================== Seekbar/UI Conversions ====================

    /**
     * Convert a seekbar position (0-1000) to filter cutoff Hz using logarithmic scale.
     * 0 = 200 Hz, 1000 = 20000 Hz
     *
     * @param progress Seekbar position (0-1000)
     * @return Cutoff frequency in Hz
     */
    fun seekbarToCutoffHz(progress: Int): Float {
        // Logarithmic scale: 200 Hz at 0, 20000 Hz at 1000
        return (Sf2Constants.FILTER_CUTOFF_HZ_MIN *
            100.0.pow(progress / 1000.0)).toFloat()
    }

    /**
     * Convert filter cutoff Hz to seekbar position (0-1000).
     *
     * @param hz Cutoff frequency in Hz
     * @return Seekbar position (0-1000)
     */
    fun cutoffHzToSeekbar(hz: Float): Int {
        // Inverse of seekbarToCutoffHz: progress = 1000 * log100(hz / 200)
        if (hz <= Sf2Constants.FILTER_CUTOFF_HZ_MIN) return 0
        return (1000.0 * kotlin.math.log10(hz.toDouble() / Sf2Constants.FILTER_CUTOFF_HZ_MIN) /
            kotlin.math.log10(100.0))
            .toInt()
            .coerceIn(0, 1000)
    }

    /**
     * Convert attenuation seekbar position to centibels.
     * Seekbar: 0 = -48dB, 480 = 0dB (full volume)
     *
     * @param progress Seekbar position (0-480)
     * @return Attenuation in centibels (0-480)
     */
    fun attenuationSeekbarToCb(progress: Int): Int {
        return Sf2Constants.ATTENUATION_CB_MAX - progress
    }

    /**
     * Convert attenuation centibels to seekbar position.
     *
     * @param centibels Attenuation in centibels
     * @return Seekbar position (0-480)
     */
    fun attenuationCbToSeekbar(centibels: Int): Int {
        return Sf2Constants.ATTENUATION_CB_MAX - centibels
    }

    /**
     * Convert fine tune seekbar position to cents.
     * Seekbar: 0 = -99 cents, 99 = 0 cents, 198 = +99 cents
     *
     * @param progress Seekbar position (0-198)
     * @return Fine tune in cents (-99 to +99)
     */
    fun fineTuneSeekbarToCents(progress: Int): Int {
        return progress - 99
    }

    /**
     * Convert fine tune cents to seekbar position.
     *
     * @param cents Fine tune in cents (-99 to +99)
     * @return Seekbar position (0-198)
     */
    fun fineTuneCentsToSeekbar(cents: Int): Int {
        return cents + 99
    }

    /**
     * Convert pan seekbar position to SF2 pan value.
     * Seekbar: 0-1000, offset by 500 for center
     *
     * @param progress Seekbar position (0-1000)
     * @return Pan value (-500 to +500)
     */
    fun panSeekbarToValue(progress: Int): Int {
        return progress - 500
    }

    /**
     * Convert pan value to seekbar position.
     *
     * @param pan Pan value (-500 to +500)
     * @return Seekbar position (0-1000)
     */
    fun panValueToSeekbar(pan: Int): Int {
        return pan + 500
    }

    // ==================== Effects Send Conversions ====================

    /**
     * Convert effects send value (0-1000) to percentage.
     *
     * @param sendValue Effects send value (units of 0.1%)
     * @return Percentage (0.0 to 100.0)
     */
    fun effectsSendToPercent(sendValue: Int): Float {
        return sendValue / 10f
    }

    /**
     * Convert percentage to effects send value.
     *
     * @param percent Percentage (0-100)
     * @return Effects send value (0-1000)
     */
    fun percentToEffectsSend(percent: Float): Int {
        return (percent * 10).toInt().coerceIn(
            Sf2Constants.EFFECTS_SEND_MIN,
            Sf2Constants.EFFECTS_SEND_MAX
        )
    }

    // ==================== LFO Frequency Conversions ====================

    /**
     * Convert LFO frequency in Hz to SF2 cents.
     * SF2 LFO freq: cents = 1200 * log2(freq / 8.176)
     *
     * @param hz Frequency in Hz
     * @return Frequency in cents
     */
    fun lfoFreqHzToCents(hz: Float): Int {
        if (hz <= 0f) return Sf2Constants.LFO_FREQ_CENTS_MIN
        return (1200.0 * (kotlin.math.ln(hz.toDouble() / Sf2Constants.LFO_REFERENCE_FREQ) / kotlin.math.ln(2.0)))
            .toInt()
            .coerceIn(Sf2Constants.LFO_FREQ_CENTS_MIN, Sf2Constants.LFO_FREQ_CENTS_MAX)
    }

    /**
     * Convert SF2 cents to LFO frequency in Hz.
     * freq = 8.176 * 2^(cents/1200)
     *
     * @param cents Frequency in cents
     * @return Frequency in Hz
     */
    fun lfoFreqCentsToHz(cents: Int): Float {
        return (Sf2Constants.LFO_REFERENCE_FREQ * 2.0.pow(cents / 1200.0)).toFloat()
    }

    /**
     * Convert LFO frequency seekbar position (0-1000) to cents.
     * Maps logarithmically: 0 = 0.1 Hz, 500 = ~8 Hz, 1000 = 100 Hz
     *
     * @param progress Seekbar position (0-1000)
     * @return LFO frequency in cents
     */
    fun lfoFreqSeekbarToCents(progress: Int): Int {
        // Map 0-1000 to 0.1 Hz - 100 Hz logarithmically
        val minHz = 0.1f
        val maxHz = 100f
        val hz = minHz * (maxHz / minHz).toDouble().pow(progress / 1000.0).toFloat()
        return lfoFreqHzToCents(hz)
    }

    /**
     * Convert LFO frequency in cents to seekbar position (0-1000).
     *
     * @param cents LFO frequency in cents
     * @return Seekbar position (0-1000)
     */
    fun lfoFreqCentsToSeekbar(cents: Int): Int {
        val hz = lfoFreqCentsToHz(cents)
        val minHz = 0.1f
        val maxHz = 100f
        if (hz <= minHz) return 0
        if (hz >= maxHz) return 1000
        return (1000.0 * kotlin.math.log10(hz.toDouble() / minHz) / kotlin.math.log10(maxHz.toDouble() / minHz))
            .toInt()
            .coerceIn(0, 1000)
    }

    // ==================== Modulation Amount Conversions ====================

    /**
     * Convert modulation amount seekbar (0-2400) to cents (-12000 to +12000).
     * Center (1200) = 0, represents ±10 octaves range.
     *
     * @param progress Seekbar position (0-2400, center = 1200)
     * @return Modulation amount in cents
     */
    fun modulationSeekbarToCents(progress: Int): Int {
        return ((progress - 1200) * 10).coerceIn(
            Sf2Constants.MODULATION_CENTS_MIN,
            Sf2Constants.MODULATION_CENTS_MAX
        )
    }

    /**
     * Convert modulation amount in cents to seekbar position.
     *
     * @param cents Modulation amount in cents (-12000 to +12000)
     * @return Seekbar position (0-2400)
     */
    fun modulationCentsToSeekbar(cents: Int): Int {
        return ((cents / 10) + 1200).coerceIn(0, 2400)
    }

    /**
     * Convert modulation cents to semitones for display.
     *
     * @param cents Modulation amount in cents
     * @return Modulation amount in semitones (with decimal)
     */
    fun modulationCentsToSemitones(cents: Int): Float {
        return cents / 100f
    }

    // ==================== Coarse Tune Conversions ====================

    /**
     * Convert coarse tune seekbar (0-240) to semitones (-120 to +120).
     *
     * @param progress Seekbar position (0-240, center = 120)
     * @return Coarse tune in semitones
     */
    fun coarseTuneSeekbarToSemitones(progress: Int): Int {
        return (progress - 120).coerceIn(
            Sf2Constants.COARSE_TUNE_MIN,
            Sf2Constants.COARSE_TUNE_MAX
        )
    }

    /**
     * Convert coarse tune semitones to seekbar position.
     *
     * @param semitones Coarse tune in semitones (-120 to +120)
     * @return Seekbar position (0-240)
     */
    fun coarseTuneSemitonesToSeekbar(semitones: Int): Int {
        return (semitones + 120).coerceIn(0, 240)
    }

    // ==================== MIDI Note Conversions ====================

    /**
     * Convert MIDI note number to note name (e.g., 60 -> "C4").
     *
     * @param midiNote MIDI note number (0-127)
     * @return Note name string
     */
    fun midiNoteToName(midiNote: Int): String {
        val octave = (midiNote / 12) - 1
        val noteIndex = midiNote % 12
        return "${Sf2Constants.NOTE_NAMES[noteIndex]}$octave"
    }

    /**
     * Convert note name to MIDI note number (e.g., "C4" -> 60).
     *
     * @param name Note name string
     * @return MIDI note number, or null if invalid
     */
    fun nameToMidiNote(name: String): Int? {
        val regex = Regex("^([A-G]#?)(-?\\d)$")
        val match = regex.matchEntire(name.uppercase()) ?: return null
        val noteName = match.groupValues[1]
        val octave = match.groupValues[2].toIntOrNull() ?: return null
        val noteIndex = Sf2Constants.NOTE_NAMES.indexOf(noteName)
        if (noteIndex < 0) return null
        return (octave + 1) * 12 + noteIndex
    }

    /**
     * Convert frequency to MIDI note number.
     * MIDI note = 69 + 12 * log2(freq / 440)
     *
     * @param frequency Frequency in Hz
     * @return MIDI note number (0-127)
     */
    fun frequencyToMidiNote(frequency: Float): Int {
        if (frequency <= 0) return Sf2Constants.DEFAULT_ROOT_NOTE
        val midiNote = 69 + 12 * log2(frequency / 440.0)
        return midiNote.toInt().coerceIn(Sf2Constants.MIDI_NOTE_MIN, Sf2Constants.MIDI_NOTE_MAX)
    }

    /**
     * Convert MIDI note number to frequency.
     * freq = 440 * 2^((note - 69) / 12)
     *
     * @param midiNote MIDI note number
     * @return Frequency in Hz
     */
    fun midiNoteToFrequency(midiNote: Int): Float {
        return (440.0 * 2.0.pow((midiNote - 69) / 12.0)).toFloat()
    }

    /**
     * Calculate the pitch deviation in cents from the nearest MIDI note.
     *
     * @param frequency Actual frequency in Hz
     * @return Cents deviation from nearest MIDI note (-50 to +50)
     */
    fun frequencyToCentsDeviation(frequency: Float): Int {
        if (frequency <= 0f) return 0
        val midiNote = frequencyToMidiNote(frequency)
        val perfectFreq = midiNoteToFrequency(midiNote)
        return if (perfectFreq > 0f) {
            (1200 * log2(frequency / perfectFreq)).toInt().coerceIn(-50, 50)
        } else {
            0
        }
    }
}
