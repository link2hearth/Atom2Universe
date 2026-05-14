package com.Atom2Universe.app.midi.sf2

/**
 * Lightweight metadata-only representation of an SF2 file.
 *
 * This class contains all the structural information from a SoundFont
 * without loading the actual sample data, enabling significant memory savings
 * for large SF2 files.
 *
 * Memory footprint estimation:
 * - For a 500MB SF2 with 1000 samples: ~100KB metadata vs 1GB full load
 * - Enables selective loading of only required samples
 */
data class Sf2Metadata(
    // Basic info
    val name: String,
    val filePath: String,

    // Sample data location (not loaded, just offsets)
    val smplByteOffset: Long,    // Byte offset of SMPL chunk in file
    val smplByteSize: Long,      // Size of SMPL chunk in bytes

    // PDTA chunks (parsed but lightweight)
    val sampleHeaders: List<Sf2SampleHeader>,
    val presetHeaders: List<Sf2PresetHeader>,
    val presetBags: List<Sf2PresetBag>,
    val presetGenerators: List<Sf2GeneratorEntry>,
    val instrumentHeaders: List<Sf2InstrumentHeader>,
    val instrumentBags: List<Sf2InstrumentBag>,
    val instrumentGenerators: List<Sf2GeneratorEntry>
) {
    /**
     * Number of samples (in shorts, not bytes)
     */
    val sampleCount: Long
        get() = smplByteSize / 2

    /**
     * Number of actual presets (excluding terminal)
     */
    val presetCount: Int
        get() = (presetHeaders.size - 1).coerceAtLeast(0)

    /**
     * Number of actual instruments (excluding terminal)
     */
    val instrumentCount: Int
        get() = (instrumentHeaders.size - 1).coerceAtLeast(0)

    /**
     * Estimated memory used by this metadata object (in bytes)
     */
    val estimatedMemoryUsage: Long
        get() {
            var size = 0L
            // String sizes (rough estimate)
            size += name.length * 2L + filePath.length * 2L
            // Sample headers: ~80 bytes each (with string)
            size += sampleHeaders.size * 80L
            // Preset headers: ~50 bytes each
            size += presetHeaders.size * 50L
            // Other structures: ~12 bytes each
            size += presetBags.size * 12L
            size += presetGenerators.size * 12L
            size += instrumentHeaders.size * 32L
            size += instrumentBags.size * 12L
            size += instrumentGenerators.size * 12L
            return size
        }

    /**
     * Get all available preset keys as "bank:program" format
     */
    fun getAvailablePresets(): List<String> {
        return presetHeaders
            .take(presetCount)
            .map { it.getKey() }
    }

    /**
     * Get preset info for display purposes
     */
    fun getPresetInfo(): List<PresetMetadata> {
        return presetHeaders
            .take(presetCount)
            .map { PresetMetadata(it.name, it.bank, it.preset) }
    }

    /**
     * Check if a specific preset exists
     */
    fun hasPreset(bank: Int, program: Int): Boolean {
        return presetHeaders.any { it.bank == bank && it.preset == program }
    }

    /**
     * Find preset index by bank and program
     * Returns -1 if not found
     */
    fun findPresetIndex(bank: Int, program: Int): Int {
        return presetHeaders.indexOfFirst { it.bank == bank && it.preset == program }
    }

    override fun toString(): String {
        return "Sf2Metadata(name='$name', presets=$presetCount, instruments=$instrumentCount, " +
               "samples=${sampleHeaders.size}, sampleData=${smplByteSize / 1024 / 1024}MB)"
    }
}

/**
 * Lightweight preset info for display
 */
data class PresetMetadata(
    val name: String,
    val bank: Int,
    val program: Int
) {
    fun getKey(): String = "$bank:$program"

    fun toDisplayString(): String {
        return String.format("%03d:%03d %s", bank, program, name)
    }
}
