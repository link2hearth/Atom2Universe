package com.Atom2Universe.app.midi.sf2

/**
 * SF2 chunk data classes representing the parsed binary structures.
 * These follow the SoundFont 2.01 specification.
 */

/**
 * Preset header (phdr) - 38 bytes per record
 * Defines a preset (instrument program) with bank/program number
 */
data class Sf2PresetHeader(
    val name: String,           // 20 chars, null-terminated
    val preset: Int,            // Program number (0-127)
    val bank: Int,              // Bank number (0-128 for melodic, 128 for percussion)
    val bagIndex: Int,          // Index into pbag
    val library: Int = 0,       // Reserved (unused)
    val genre: Int = 0,         // Reserved (unused)
    val morphology: Int = 0     // Reserved (unused)
) {
    /**
     * Returns a unique key for this preset: "bank:program"
     */
    fun getKey(): String = "$bank:$preset"
}

/**
 * Preset bag (pbag) - 4 bytes per record
 * Defines zones within a preset
 */
data class Sf2PresetBag(
    val generatorIndex: Int,    // Index into pgen
    val modulatorIndex: Int     // Index into pmod
)

/**
 * Generator (pgen/igen) - 4 bytes per record
 * Defines a generator operator with amount
 */
data class Sf2GeneratorEntry(
    val operator: Int,          // Generator operator ID
    val amount: Int             // Signed 16-bit amount (or range encoded as lo/hi bytes)
) {
    /**
     * Decodes the amount as a key/velocity range
     * Low byte is lo value, high byte is hi value
     */
    fun decodeRange(): IntRange {
        val lo = amount and 0xFF
        val hi = (amount shr 8) and 0xFF
        return lo..hi
    }
}

/**
 * Instrument header (inst) - 22 bytes per record
 * Defines an instrument
 */
data class Sf2InstrumentHeader(
    val name: String,           // 20 chars, null-terminated
    val bagIndex: Int           // Index into ibag
)

/**
 * Instrument bag (ibag) - 4 bytes per record
 * Defines zones within an instrument
 */
data class Sf2InstrumentBag(
    val generatorIndex: Int,    // Index into igen
    val modulatorIndex: Int     // Index into imod
)

/**
 * Sample header (shdr) - 46 bytes per record
 * Defines a sample in the sample data pool
 */
data class Sf2SampleHeader(
    val name: String,           // 20 chars, null-terminated
    val start: Long,            // Start offset in sample data (in samples)
    val end: Long,              // End offset in sample data (in samples)
    val startLoop: Long,        // Loop start offset (in samples)
    val endLoop: Long,          // Loop end offset (in samples)
    val sampleRate: Int,        // Sample rate in Hz
    val originalPitch: Int,     // MIDI note of original pitch (60 = middle C)
    val pitchCorrection: Int,   // Pitch correction in cents
    val sampleLink: Int,        // Index of linked sample (for stereo)
    val sampleType: Int         // Sample type flags
) {
    companion object {
        // Sample type flags
        const val TYPE_MONO = 1
        const val TYPE_RIGHT = 2
        const val TYPE_LEFT = 4
        const val TYPE_LINKED = 8
        const val TYPE_ROM_MONO = 0x8001
        const val TYPE_ROM_RIGHT = 0x8002
        const val TYPE_ROM_LEFT = 0x8004
        const val TYPE_ROM_LINKED = 0x8008
    }

    fun isMono(): Boolean = (sampleType and TYPE_MONO) != 0
    fun isRight(): Boolean = (sampleType and TYPE_RIGHT) != 0
    fun isLeft(): Boolean = (sampleType and TYPE_LEFT) != 0
    fun isLinked(): Boolean = (sampleType and TYPE_LINKED) != 0
    fun isRom(): Boolean = (sampleType and 0x8000) != 0
}

/**
 * Modulator (pmod/imod) - 10 bytes per record
 * Defines modulation routing (not fully implemented in this basic port)
 */
data class Sf2ModulatorEntry(
    val srcOper: Int,           // Source modulator operator
    val destOper: Int,          // Destination generator
    val amount: Int,            // Modulation amount
    val amtSrcOper: Int,        // Amount source operator
    val transOper: Int          // Transform operator
)

/**
 * Zone defaults with all generator values
 * Used to accumulate generator values across preset and instrument zones
 */
data class Sf2ZoneData(
    val keyRange: IntRange = 0..127,
    val velRange: IntRange = 0..127,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val startLoopOffset: Int = 0,
    val endLoopOffset: Int = 0,
    val coarseTune: Int = 0,
    val fineTune: Int = 0,
    val scaleTuning: Int = 100,
    val attenuation: Int = 0,
    val pan: Float? = null,
    val sampleModes: Int = 0,
    val rootKey: Int? = null,
    val exclusiveClass: Int = 0,
    val reverbSend: Float? = null,
    val chorusSend: Float? = null,
    // Force key/velocity generators (SF2 generators 46, 47)
    val forcedKeyNum: Int? = null,      // Forces MIDI note number (for sound effects)
    val forcedVelocity: Int? = null,    // Forces velocity value (for fixed-velocity samples)
    val volumeEnvelope: Sf2EnvelopeData = Sf2EnvelopeData(),
    val sampleId: Int? = null,
    val instrumentIndex: Int? = null,
    val sampleHeader: Sf2SampleHeader? = null,
    // Filter parameters (SF2 generators 8 and 9)
    val filterFc: Int? = null,      // Initial filter cutoff in absolute cents
    val filterQ: Int? = null,       // Initial filter Q/resonance in centibels
    // Vibrato LFO parameters (SF2 generators 6, 23, 24)
    val vibLfoDelay: Int? = null,   // Vibrato LFO delay in timecents
    val vibLfoFreq: Int? = null,    // Vibrato LFO frequency in absolute cents
    val vibLfoToPitch: Int? = null, // Vibrato LFO to pitch in cents
    // Modulation LFO parameters (SF2 generators 5, 10, 13, 21, 22)
    val modLfoDelay: Int? = null,   // Mod LFO delay in timecents
    val modLfoFreq: Int? = null,    // Mod LFO frequency in absolute cents
    val modLfoToPitch: Int? = null, // Mod LFO to pitch in cents
    val modLfoToFilterFc: Int? = null, // Mod LFO to filter cutoff in cents
    val modLfoToVolume: Int? = null, // Mod LFO to volume in centibels (tremolo)
    // Modulation Envelope parameters (SF2 generators 25-30, 7, 11)
    val modEnvelope: Sf2EnvelopeData = Sf2EnvelopeData(),
    val modEnvToPitch: Int? = null,    // Mod envelope to pitch in cents
    val modEnvToFilterFc: Int? = null  // Mod envelope to filter cutoff in cents
) {
    companion object {
        fun createDefaults() = Sf2ZoneData()
    }
}

/**
 * Envelope data in timecents (SF2 native format)
 * timecents are converted to seconds using: seconds = 2^(timecents/1200)
 */
data class Sf2EnvelopeData(
    val delay: Int? = null,     // timecents
    val attack: Int? = null,    // timecents
    val hold: Int? = null,      // timecents
    val decay: Int? = null,     // timecents
    val sustain: Int? = null,   // centibels attenuation (0 = max sustain)
    val release: Int? = null,   // timecents
    // Key tracking: adjust hold/decay based on MIDI note (timecents per key from middle C)
    val keynumToHold: Int? = null,  // timecents per key (positive = higher notes = shorter hold)
    val keynumToDecay: Int? = null  // timecents per key (positive = higher notes = shorter decay)
)
