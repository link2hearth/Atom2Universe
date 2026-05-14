package com.Atom2Universe.app.midi.sf2

import java.io.Closeable
import java.util.Locale

/**
 * Represents a fully parsed SF2 (SoundFont 2) file.
 * Contains sample data, presets, and all metadata needed for synthesis.
 *
 * Supports multiple sample storage modes:
 * - In-memory ShortArray (default, fast)
 * - Memory-mapped file (for very large SF2 files)
 * - Hybrid (loaded samples in RAM, fallback to mmap)
 */
class Sf2File(
    val name: String,                           // SoundFont name from INFO chunk
    val sampleData: ShortArray,                 // Raw sample data (kept for compatibility)
    private val presetMap: Map<String, Sf2Preset>, // "bank:program" -> Sf2Preset
    val sampleHeaders: List<Sf2SampleHeader>,   // Sample metadata
    private val sampleProvider: SampleDataProvider? = null  // Optional provider for mmap support
) : Closeable {

    /**
     * Secondary constructor using SampleDataProvider for memory-mapped access
     */
    constructor(
        name: String,
        sampleProvider: SampleDataProvider,
        presetMap: Map<String, Sf2Preset>,
        sampleHeaders: List<Sf2SampleHeader>
    ) : this(
        name = name,
        sampleData = ShortArray(0),  // Empty array, using provider instead
        presetMap = presetMap,
        sampleHeaders = sampleHeaders,
        sampleProvider = sampleProvider
    )

    // Use provider if available, otherwise fall back to array
    private val effectiveProvider: SampleDataProvider by lazy {
        sampleProvider ?: ArraySampleProvider(sampleData)
    }

    /**
     * Whether this SF2 file uses memory-mapped sample access
     */
    val isMemoryMapped: Boolean
        get() = sampleProvider?.isMemoryMapped == true
    /**
     * Gets a preset by bank and program number.
     * Returns null if not found.
     */
    fun getPreset(bank: Int, program: Int): Sf2Preset? {
        val key = "$bank:$program"
        return presetMap[key]
    }

    /**
     * Gets all regions that match the given bank, program, key and velocity.
     * This is the main lookup method for synthesis.
     */
    fun getRegions(
        bank: Int = 0,
        program: Int = 0,
        key: Int = 60,
        velocity: Int = 100
    ): List<Sf2Region> {
        val normalizedBank = bank.coerceIn(0, 16383)
        val normalizedProgram = program.coerceIn(0, 127)
        val normalizedKey = key.coerceIn(0, 127)
        val normalizedVel = velocity.coerceIn(0, 127)

        val preset = getPreset(normalizedBank, normalizedProgram) ?: return emptyList()
        return preset.getMatchingRegions(normalizedKey, normalizedVel)
    }

    /**
     * Gets all available presets/programs, optionally filtered by bank.
     */
    fun getPrograms(banks: List<Int>? = null): List<ProgramInfo> {
        val normalizedBanks = banks?.map { it.coerceIn(0, 16383) }?.toSet()

        return presetMap.values
            .filter { normalizedBanks == null || it.bank in normalizedBanks }
            .map { ProgramInfo(it.bank, it.program, it.name) }
            .sortedWith(compareBy({ it.bank }, { it.program }))
    }

    /**
     * Gets all available presets/programs in a specific bank.
     */
    @Suppress("unused")
    fun getProgramsInBank(bank: Int): List<ProgramInfo> {
        return getPrograms(listOf(bank))
    }

    /**
     * Gets all unique bank numbers in this SoundFont.
     */
    @Suppress("unused")
    fun getBanks(): List<Int> {
        return presetMap.values.map { it.bank }.distinct().sorted()
    }

    /**
     * Reads sample data for a region, converted to float (-1.0 to 1.0).
     * @param region The region to read samples from
     * @param outputBuffer Pre-allocated float array to fill
     * @param offset Offset into the region's sample data to start reading
     * @param length Number of samples to read
     * @return Number of samples actually read
     *
     * Thread-safety: This method captures a snapshot of the provider reference at entry
     * to ensure consistent sample count throughout the read operation.
     */
    @Suppress("unused")
    fun readSamples(
        region: Sf2Region,
        outputBuffer: FloatArray,
        offset: Long = 0,
        length: Int = outputBuffer.size
    ): Int {
        // Capture provider snapshot for thread-safe access
        // This ensures sampleCount is consistent throughout the read operation
        val provider = effectiveProvider
        val totalSamples = provider.sampleCount

        val startIndex = (region.sampleStart + offset)
        val maxSamples = minOf(
            length.toLong(),
            outputBuffer.size.toLong(),
            totalSamples - startIndex,
            region.sampleEnd - region.sampleStart - offset
        ).toInt()

        if (maxSamples <= 0) return 0

        for (i in 0 until maxSamples) {
            val sampleIndex = startIndex + i
            if (sampleIndex >= 0 && sampleIndex < totalSamples) {
                // Use provider's getSample for consistent access (works for both mmap and array)
                outputBuffer[i] = provider.getSample(sampleIndex)
            } else {
                outputBuffer[i] = 0f
            }
        }

        return maxSamples
    }

    /**
     * Gets a single sample value at the given position, normalized to float.
     * Uses the sample provider for memory-mapped or hybrid access.
     */
    fun getSample(index: Long): Float {
        return effectiveProvider.getSample(index)
    }

    /**
     * Gets a sample with linear interpolation for non-integer positions.
     */
    @Suppress("unused")
    fun getSampleInterpolated(position: Double): Float {
        val index = position.toLong()
        val frac = (position - index).toFloat()

        val s0 = getSample(index)
        val s1 = getSample(index + 1)

        return s0 + (s1 - s0) * frac
    }

    /**
     * Number of presets in this SoundFont
     */
    val presetCount: Int
        get() = presetMap.size

    /**
     * Total number of samples in the sample data
     */
    val sampleCount: Int
        get() = if (sampleProvider != null) {
            sampleProvider.sampleCount.toInt()
        } else {
            sampleData.size
        }

    /**
     * Number of sample headers
     */
    @Suppress("unused")
    val sampleHeaderCount: Int
        get() = sampleHeaders.size

    /**
     * Memory used by sample data in bytes
     */
    val memoryUsageBytes: Long
        get() = effectiveProvider.memoryUsageBytes

    /**
     * Close any resources (memory-mapped files, etc.)
     */
    override fun close() {
        sampleProvider?.close()
    }

    override fun toString(): String {
        val memMB = memoryUsageBytes / 1024 / 1024
        val mmapStr = if (isMemoryMapped) " [mmap]" else ""
        return "Sf2File(name='$name', presets=${presetCount}, samples=${sampleCount}, mem=${memMB}MB$mmapStr)"
    }
}

/**
 * Information about a program/preset
 */
data class ProgramInfo(
    val bank: Int,
    val program: Int,
    val name: String
) {
    /**
     * Returns a display string like "000:001 Piano"
     */
    @Suppress("unused")
    fun toDisplayString(): String {
        return String.format(Locale.getDefault(), "%03d:%03d %s", bank, program, name)
    }

    /**
     * Returns GM (General MIDI) program name if this is bank 0
     */
    @Suppress("unused")
    fun getGMName(): String? {
        return if (bank == 0 && program in 0..127) {
            GM_PROGRAM_NAMES.getOrNull(program)
        } else null
    }

    companion object {
        // General MIDI program names
        val GM_PROGRAM_NAMES = listOf(
            // Piano (0-7)
            "Acoustic Grand Piano", "Bright Acoustic Piano", "Electric Grand Piano",
            "Honky-tonk Piano", "Electric Piano 1", "Electric Piano 2", "Harpsichord", "Clavinet",
            // Chromatic Percussion (8-15)
            "Celesta", "Glockenspiel", "Music Box", "Vibraphone", "Marimba", "Xylophone",
            "Tubular Bells", "Dulcimer",
            // Organ (16-23)
            "Drawbar Organ", "Percussive Organ", "Rock Organ", "Church Organ", "Reed Organ",
            "Accordion", "Harmonica", "Tango Accordion",
            // Guitar (24-31)
            "Acoustic Guitar (nylon)", "Acoustic Guitar (steel)", "Electric Guitar (jazz)",
            "Electric Guitar (clean)", "Electric Guitar (muted)", "Overdriven Guitar",
            "Distortion Guitar", "Guitar Harmonics",
            // Bass (32-39)
            "Acoustic Bass", "Electric Bass (finger)", "Electric Bass (pick)", "Fretless Bass",
            "Slap Bass 1", "Slap Bass 2", "Synth Bass 1", "Synth Bass 2",
            // Strings (40-47)
            "Violin", "Viola", "Cello", "Contrabass", "Tremolo Strings", "Pizzicato Strings",
            "Orchestral Harp", "Timpani",
            // Ensemble (48-55)
            "String Ensemble 1", "String Ensemble 2", "Synth Strings 1", "Synth Strings 2",
            "Choir Aahs", "Voice Oohs", "Synth Choir", "Orchestra Hit",
            // Brass (56-63)
            "Trumpet", "Trombone", "Tuba", "Muted Trumpet", "French Horn", "Brass Section",
            "Synth Brass 1", "Synth Brass 2",
            // Reed (64-71)
            "Soprano Sax", "Alto Sax", "Tenor Sax", "Baritone Sax", "Oboe", "English Horn",
            "Bassoon", "Clarinet",
            // Pipe (72-79)
            "Piccolo", "Flute", "Recorder", "Pan Flute", "Blown Bottle", "Shakuhachi",
            "Whistle", "Ocarina",
            // Synth Lead (80-87)
            "Lead 1 (square)", "Lead 2 (sawtooth)", "Lead 3 (calliope)", "Lead 4 (chiff)",
            "Lead 5 (charang)", "Lead 6 (voice)", "Lead 7 (fifths)", "Lead 8 (bass + lead)",
            // Synth Pad (88-95)
            "Pad 1 (new age)", "Pad 2 (warm)", "Pad 3 (polysynth)", "Pad 4 (choir)",
            "Pad 5 (bowed)", "Pad 6 (metallic)", "Pad 7 (halo)", "Pad 8 (sweep)",
            // Synth Effects (96-103)
            "FX 1 (rain)", "FX 2 (soundtrack)", "FX 3 (crystal)", "FX 4 (atmosphere)",
            "FX 5 (brightness)", "FX 6 (goblins)", "FX 7 (echoes)", "FX 8 (sci-fi)",
            // Ethnic (104-111)
            "Sitar", "Banjo", "Shamisen", "Koto", "Kalimba", "Bagpipe", "Fiddle", "Shanai",
            // Percussive (112-119)
            "Tinkle Bell", "Agogo", "Steel Drums", "Woodblock", "Taiko Drum", "Melodic Tom",
            "Synth Drum", "Reverse Cymbal",
            // Sound Effects (120-127)
            "Guitar Fret Noise", "Breath Noise", "Seashore", "Bird Tweet", "Telephone Ring",
            "Helicopter", "Applause", "Gunshot"
        )
    }
}
