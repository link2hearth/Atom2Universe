package com.Atom2Universe.app.sf2creator.data

import android.util.Log
import com.Atom2Universe.app.sf2creator.reader.Sf2LazyReader

/**
 * Raw bytes clipboard for faithful copy-paste between SF2 projects.
 *
 * In the "Polyphone-like" architecture:
 * - We copy references to the original SF2 data (not extracted copies)
 * - Paste creates patches in the target project
 * - Audio data is extracted on paste (not stored in clipboard)
 *
 * This allows:
 * - Memory-efficient copy of large samples
 * - Faithful reproduction of original data
 * - Copy-paste between different SF2 source files
 */
object Sf2RawClipboard {

    private const val TAG = "Sf2RawClipboard"

    // Current clipboard content
    private var currentContent: ClipboardContent? = null

    /**
     * Clipboard content types.
     */
    sealed class ClipboardContent {
        /**
         * A copied preset with all its instruments and samples.
         */
        data class Preset(
            val sourceFilePath: String,
            val presetIndex: Int,
            val presetName: String,
            val programNumber: Int,
            val bankNumber: Int,
            val instrumentIndices: List<Int>,
            val sampleIndices: List<Int>,
            val generators: Map<Int, Int>,
            val sourceProjectId: Long? = null
        ) : ClipboardContent()

        /**
         * A copied instrument with all its samples.
         */
        data class Instrument(
            val sourceFilePath: String,
            val instrumentIndex: Int,
            val instrumentName: String,
            val sampleIndices: List<Int>,
            val generators: Map<Int, Int>,
            val sourceProjectId: Long? = null
        ) : ClipboardContent()

        /**
         * A copied sample.
         */
        data class Sample(
            val sourceFilePath: String,
            val sampleIndex: Int,
            val sampleName: String,
            val rootNote: Int,
            val sampleRate: Int,
            val loopStart: Long,
            val loopEnd: Long,
            val generators: Map<Int, Int>,
            val sourceProjectId: Long? = null
        ) : ClipboardContent()

        /**
         * Multiple copied samples.
         */
        data class MultipleSamples(
            val samples: List<Sample>
        ) : ClipboardContent()
    }

    /**
     * Result of a paste operation.
     */
    sealed class PasteResult {
        data class Success(
            val pastedPresets: Int = 0,
            val pastedInstruments: Int = 0,
            val pastedSamples: Int = 0
        ) : PasteResult()

        data class Error(val message: String) : PasteResult()
    }

    // ==================== Copy Operations ====================

    /**
     * Copy a preset to the clipboard.
     */
    fun copyPreset(
        reader: Sf2LazyReader,
        presetIndex: Int,
        sourceProjectId: Long? = null
    ): Boolean {
        val parseResult = reader.getParseResult() ?: return false
        val preset = parseResult.presets.getOrNull(presetIndex) ?: return false

        // Collect all instruments and samples used by this preset
        val instrumentIndices = preset.zones.mapNotNull { it.instrument?.index }.distinct()
        val sampleIndices = preset.zones
            .mapNotNull { it.instrument }
            .flatMap { it.zones.map { zone -> zone.sampleIndex } }
            .distinct()

        currentContent = ClipboardContent.Preset(
            sourceFilePath = reader.getFilePath(),
            presetIndex = presetIndex,
            presetName = preset.name,
            programNumber = preset.programNumber,
            bankNumber = preset.bankNumber,
            instrumentIndices = instrumentIndices,
            sampleIndices = sampleIndices,
            generators = preset.globalGenerators,
            sourceProjectId = sourceProjectId
        )

        Log.d(TAG, "Copied preset '${preset.name}' with ${instrumentIndices.size} instruments, ${sampleIndices.size} samples")
        return true
    }

    /**
     * Copy an instrument to the clipboard.
     */
    fun copyInstrument(
        reader: Sf2LazyReader,
        instrumentIndex: Int,
        sourceProjectId: Long? = null
    ): Boolean {
        val parseResult = reader.getParseResult() ?: return false
        val instrument = parseResult.instruments.getOrNull(instrumentIndex) ?: return false

        val sampleIndices = instrument.zones.map { it.sampleIndex }.distinct()

        currentContent = ClipboardContent.Instrument(
            sourceFilePath = reader.getFilePath(),
            instrumentIndex = instrumentIndex,
            instrumentName = instrument.name,
            sampleIndices = sampleIndices,
            generators = instrument.globalGenerators,
            sourceProjectId = sourceProjectId
        )

        Log.d(TAG, "Copied instrument '${instrument.name}' with ${sampleIndices.size} samples")
        return true
    }

    /**
     * Copy a sample to the clipboard.
     */
    fun copySample(
        reader: Sf2LazyReader,
        sampleIndex: Int,
        zoneGenerators: Map<Int, Int> = emptyMap(),
        sourceProjectId: Long? = null
    ): Boolean {
        val parseResult = reader.getParseResult() ?: return false
        val sample = parseResult.samples.getOrNull(sampleIndex) ?: return false

        currentContent = ClipboardContent.Sample(
            sourceFilePath = reader.getFilePath(),
            sampleIndex = sampleIndex,
            sampleName = sample.name,
            rootNote = sample.originalPitch,
            sampleRate = sample.sampleRate,
            loopStart = sample.loopStart,
            loopEnd = sample.loopEnd,
            generators = zoneGenerators,
            sourceProjectId = sourceProjectId
        )

        Log.d(TAG, "Copied sample '${sample.name}'")
        return true
    }

    /**
     * Copy multiple samples to the clipboard.
     */
    fun copySamples(
        reader: Sf2LazyReader,
        sampleIndices: List<Int>,
        zoneGenerators: Map<Int, Map<Int, Int>> = emptyMap(),
        sourceProjectId: Long? = null
    ): Boolean {
        val parseResult = reader.getParseResult() ?: return false

        val samples = sampleIndices.mapNotNull { index ->
            val sample = parseResult.samples.getOrNull(index) ?: return@mapNotNull null
            ClipboardContent.Sample(
                sourceFilePath = reader.getFilePath(),
                sampleIndex = index,
                sampleName = sample.name,
                rootNote = sample.originalPitch,
                sampleRate = sample.sampleRate,
                loopStart = sample.loopStart,
                loopEnd = sample.loopEnd,
                generators = zoneGenerators[index] ?: emptyMap(),
                sourceProjectId = sourceProjectId
            )
        }

        if (samples.isEmpty()) return false

        currentContent = ClipboardContent.MultipleSamples(samples)

        Log.d(TAG, "Copied ${samples.size} samples")
        return true
    }

    // ==================== Paste Operations ====================

    /**
     * Paste clipboard content into a project.
     * Creates patches in the target project for the pasted content.
     *
     * @param targetProjectId Target project ID
     * @param targetSourceFilePath Target project's SF2 source file path
     * @param patchService Service for creating patches
     * @param audioSaveDir Directory where extracted audio files will be saved
     */
    suspend fun paste(
        targetProjectId: Long,
        targetSourceFilePath: String,
        patchService: Sf2PatchService,
        audioSaveDir: java.io.File
    ): PasteResult {
        val content = currentContent ?: return PasteResult.Error("Clipboard is empty")

        return when (content) {
            is ClipboardContent.Preset -> pastePreset(content, targetProjectId, patchService, audioSaveDir)
            is ClipboardContent.Instrument -> pasteInstrument(content, targetProjectId, patchService, audioSaveDir)
            is ClipboardContent.Sample -> pasteSample(content, targetProjectId, patchService, audioSaveDir)
            is ClipboardContent.MultipleSamples -> pasteMultipleSamples(content, targetProjectId, patchService, audioSaveDir)
        }
    }

    private suspend fun pastePreset(
        preset: ClipboardContent.Preset,
        targetProjectId: Long,
        patchService: Sf2PatchService,
        audioSaveDir: java.io.File
    ): PasteResult {
        // For presets, we need to paste all the contained instruments and samples first
        // Then create the preset structure

        val sourceReader = Sf2LazyReader(preset.sourceFilePath)
        if (!sourceReader.isValid()) {
            return PasteResult.Error("Source file not found: ${preset.sourceFilePath}")
        }

        val parseResult = sourceReader.getParseResult()
            ?: return PasteResult.Error("Failed to parse source file")

        var pastedSamples = 0

        // Paste all samples
        for (sampleIndex in preset.sampleIndices) {
            val sample = parseResult.samples.getOrNull(sampleIndex) ?: continue

            // Extract audio data
            val audioData = sourceReader.extractSampleAudio(sampleIndex)
            if (audioData == null) {
                Log.w(TAG, "Failed to extract audio for sample $sampleIndex")
                continue
            }

            // Save audio to file
            val audioFile = java.io.File(audioSaveDir, "${sample.name}_${System.currentTimeMillis()}.wav")
            if (saveAudioToWav(audioFile, audioData, sample.sampleRate)) {
                // Find the zone generators for this sample
                val zoneGens = findSampleZoneGenerators(parseResult, sampleIndex)

                // Create ADD patch for the sample
                patchService.createAddSamplePatch(
                    projectId = targetProjectId,
                    sampleName = sample.name,
                    audioFilePath = audioFile.absolutePath,
                    sampleRate = sample.sampleRate,
                    rootNote = sample.originalPitch,
                    keyRangeStart = 0, // Default, should be extracted from zone
                    keyRangeEnd = 127,
                    loopStart = sample.loopStart.toInt(),
                    loopEnd = sample.loopEnd.toInt(),
                    hasLoop = sample.hasLoop(),
                    generators = zoneGens
                )
                pastedSamples++
            }
        }

        return PasteResult.Success(
            pastedPresets = 1,
            pastedInstruments = preset.instrumentIndices.size,
            pastedSamples = pastedSamples
        )
    }

    private suspend fun pasteInstrument(
        instrument: ClipboardContent.Instrument,
        targetProjectId: Long,
        patchService: Sf2PatchService,
        audioSaveDir: java.io.File
    ): PasteResult {
        val sourceReader = Sf2LazyReader(instrument.sourceFilePath)
        if (!sourceReader.isValid()) {
            return PasteResult.Error("Source file not found: ${instrument.sourceFilePath}")
        }

        val parseResult = sourceReader.getParseResult()
            ?: return PasteResult.Error("Failed to parse source file")

        var pastedSamples = 0

        for (sampleIndex in instrument.sampleIndices) {
            val sample = parseResult.samples.getOrNull(sampleIndex) ?: continue

            val audioData = sourceReader.extractSampleAudio(sampleIndex) ?: continue

            val audioFile = java.io.File(audioSaveDir, "${sample.name}_${System.currentTimeMillis()}.wav")
            if (saveAudioToWav(audioFile, audioData, sample.sampleRate)) {
                val zoneGens = findSampleZoneGenerators(parseResult, sampleIndex)

                patchService.createAddSamplePatch(
                    projectId = targetProjectId,
                    sampleName = sample.name,
                    audioFilePath = audioFile.absolutePath,
                    sampleRate = sample.sampleRate,
                    rootNote = sample.originalPitch,
                    keyRangeStart = 0,
                    keyRangeEnd = 127,
                    loopStart = sample.loopStart.toInt(),
                    loopEnd = sample.loopEnd.toInt(),
                    hasLoop = sample.hasLoop(),
                    generators = zoneGens
                )
                pastedSamples++
            }
        }

        return PasteResult.Success(
            pastedInstruments = 1,
            pastedSamples = pastedSamples
        )
    }

    private suspend fun pasteSample(
        sample: ClipboardContent.Sample,
        targetProjectId: Long,
        patchService: Sf2PatchService,
        audioSaveDir: java.io.File
    ): PasteResult {
        val sourceReader = Sf2LazyReader(sample.sourceFilePath)
        if (!sourceReader.isValid()) {
            return PasteResult.Error("Source file not found: ${sample.sourceFilePath}")
        }

        val audioData = sourceReader.extractSampleAudio(sample.sampleIndex)
            ?: return PasteResult.Error("Failed to extract audio data")

        val audioFile = java.io.File(audioSaveDir, "${sample.sampleName}_${System.currentTimeMillis()}.wav")
        if (!saveAudioToWav(audioFile, audioData, sample.sampleRate)) {
            return PasteResult.Error("Failed to save audio file")
        }

        patchService.createAddSamplePatch(
            projectId = targetProjectId,
            sampleName = sample.sampleName,
            audioFilePath = audioFile.absolutePath,
            sampleRate = sample.sampleRate,
            rootNote = sample.rootNote,
            keyRangeStart = 0,
            keyRangeEnd = 127,
            loopStart = sample.loopStart.toInt(),
            loopEnd = sample.loopEnd.toInt(),
            hasLoop = sample.loopEnd > sample.loopStart,
            generators = sample.generators
        )

        return PasteResult.Success(pastedSamples = 1)
    }

    private suspend fun pasteMultipleSamples(
        content: ClipboardContent.MultipleSamples,
        targetProjectId: Long,
        patchService: Sf2PatchService,
        audioSaveDir: java.io.File
    ): PasteResult {
        var pastedSamples = 0

        for (sample in content.samples) {
            val result = pasteSample(sample, targetProjectId, patchService, audioSaveDir)
            if (result is PasteResult.Success) {
                pastedSamples += result.pastedSamples
            }
        }

        return PasteResult.Success(pastedSamples = pastedSamples)
    }

    // ==================== Query Operations ====================

    /**
     * Check if there's content in the clipboard.
     */
    fun hasContent(): Boolean = currentContent != null

    /**
     * Get the current clipboard content type.
     */
    fun getContentType(): String? = when (currentContent) {
        is ClipboardContent.Preset -> "Preset"
        is ClipboardContent.Instrument -> "Instrument"
        is ClipboardContent.Sample -> "Sample"
        is ClipboardContent.MultipleSamples -> "MultipleSamples"
        null -> null
    }

    /**
     * Get a description of the clipboard content.
     */
    fun getContentDescription(): String? = when (val content = currentContent) {
        is ClipboardContent.Preset -> "Preset: ${content.presetName} (${content.sampleIndices.size} samples)"
        is ClipboardContent.Instrument -> "Instrument: ${content.instrumentName} (${content.sampleIndices.size} samples)"
        is ClipboardContent.Sample -> "Sample: ${content.sampleName}"
        is ClipboardContent.MultipleSamples -> "${content.samples.size} samples"
        null -> null
    }

    /**
     * Clear the clipboard.
     */
    fun clear() {
        currentContent = null
        Log.d(TAG, "Clipboard cleared")
    }

    // ==================== Helper Methods ====================

    private fun findSampleZoneGenerators(
        parseResult: com.Atom2Universe.app.sf2creator.reader.Sf2ParseResult,
        sampleIndex: Int
    ): Map<Int, Int> {
        // Find the zone that references this sample and return its generators
        for (instrument in parseResult.instruments) {
            for (zone in instrument.zones) {
                if (zone.sampleIndex == sampleIndex) {
                    return zone.generators
                }
            }
        }
        return emptyMap()
    }

    private fun saveAudioToWav(
        file: java.io.File,
        samples: ShortArray,
        sampleRate: Int
    ): Boolean {
        return try {
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file).use { fos ->
                // Write WAV header
                val dataSize = samples.size * 2
                val fileSize = dataSize + 36

                // RIFF header
                fos.write("RIFF".toByteArray())
                fos.write(intToLittleEndian(fileSize))
                fos.write("WAVE".toByteArray())

                // fmt chunk
                fos.write("fmt ".toByteArray())
                fos.write(intToLittleEndian(16)) // Chunk size
                fos.write(shortToLittleEndian(1)) // Audio format (PCM)
                fos.write(shortToLittleEndian(1)) // Num channels (mono)
                fos.write(intToLittleEndian(sampleRate)) // Sample rate
                fos.write(intToLittleEndian(sampleRate * 2)) // Byte rate
                fos.write(shortToLittleEndian(2)) // Block align
                fos.write(shortToLittleEndian(16)) // Bits per sample

                // data chunk
                fos.write("data".toByteArray())
                fos.write(intToLittleEndian(dataSize))

                // Sample data
                for (sample in samples) {
                    fos.write(shortToLittleEndian(sample.toInt()))
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save WAV file", e)
            false
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }
}
