package com.Atom2Universe.app.sf2creator.writer

import android.util.Log
import com.Atom2Universe.app.sf2creator.audio.Crossfader
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.util.Sf2Constants
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import com.Atom2Universe.app.sf2creator.util.WavUtils
import java.io.File
import java.io.RandomAccessFile

/**
 * Writes complete SF2 SoundFont files.
 *
 * SF2 File Structure:
 * RIFF 'sfbk'
 * ├── LIST 'INFO'  - Metadata
 * ├── LIST 'sdta'  - Sample data
 * │   └── smpl     - 16-bit PCM samples
 * └── LIST 'pdta'  - Preset/instrument/sample definitions
 *     ├── phdr     - Preset headers
 *     ├── pbag     - Preset bags
 *     ├── pmod     - Preset modulators
 *     ├── pgen     - Preset generators
 *     ├── inst     - Instrument headers
 *     ├── ibag     - Instrument bags
 *     ├── imod     - Instrument modulators
 *     ├── igen     - Instrument generators
 *     └── shdr     - Sample headers
 */
class Sf2Writer {

    companion object {
        private const val TAG = "Sf2Writer"

        /**
         * Convert milliseconds to SF2 timecents.
         * Delegates to Sf2UnitConverter.
         */
        fun msToTimecents(ms: Int): Int = Sf2UnitConverter.msToTimecents(ms)

        /**
         * Convert sustain percentage (0-100%) to SF2 centibels of attenuation.
         * Delegates to Sf2UnitConverter.
         */
        fun sustainPercentToCentibels(percent: Int): Int = Sf2UnitConverter.sustainPercentToCentibels(percent)

        /**
         * Convert frequency in Hz to SF2 absolute cents for filter cutoff.
         * Delegates to Sf2UnitConverter.
         */
        fun hzToFilterCents(hz: Float): Int = Sf2UnitConverter.hzToFilterCents(hz)
    }

    /**
     * Result of loop point optimization: adjusted start/end that sit on
     * matching zero-crossings for a click-free loop.
     */
    private data class OptimizedLoop(val start: Int, val end: Int)

    /**
     * Snap loop points to nearby zero-crossings going in the same direction.
     *
     * A zero-crossing is where the waveform passes through zero.
     * For a seamless loop, both loopStart and loopEnd must be at
     * zero-crossings going in the same direction (both positive-going
     * or both negative-going). This way the waveform is continuous
     * when the engine wraps from loopEnd back to loopStart.
     *
     * @param samples The raw audio samples
     * @param loopStart Original loop start (inclusive)
     * @param loopEnd Original loop end (inclusive)
     * @return Optimized loop points snapped to zero crossings
     */
    private fun snapLoopToZeroCrossings(
        samples: ShortArray,
        loopStart: Int,
        loopEnd: Int
    ): OptimizedLoop {
        val window = Sf2Constants.ZERO_CROSSING_SEARCH_WINDOW.coerceAtMost((loopEnd - loopStart) / 4)

        // Find all zero crossings near loopStart and loopEnd
        val startCrossings = findZeroCrossings(samples, loopStart, window)
        val endCrossings = findZeroCrossings(samples, loopEnd, window)

        if (startCrossings.isEmpty() || endCrossings.isEmpty()) {
            return OptimizedLoop(loopStart, loopEnd)
        }

        // Find the best pair: same direction, closest to original positions
        var bestStart = loopStart
        var bestEnd = loopEnd
        var bestScore = Long.MAX_VALUE

        for ((sIdx, sDir) in startCrossings) {
            for ((eIdx, eDir) in endCrossings) {
                // Must cross in the same direction
                if (sDir != eDir) continue
                // Loop must remain valid
                if (eIdx <= sIdx + 100) continue

                // Score: prefer small displacement from original positions
                val displacement = Math.abs(sIdx - loopStart).toLong() +
                        Math.abs(eIdx - loopEnd).toLong()

                // Bonus: also match amplitude at both points
                val ampDiff = Math.abs(samples[sIdx].toInt() - samples[eIdx].toInt()).toLong()
                val score = displacement * 2 + ampDiff

                if (score < bestScore) {
                    bestScore = score
                    bestStart = sIdx
                    bestEnd = eIdx
                }
            }
        }

        return OptimizedLoop(bestStart, bestEnd)
    }

    /**
     * Find zero-crossings near a target position.
     * Returns list of (index, direction) where direction = true for positive-going.
     */
    private fun findZeroCrossings(
        samples: ShortArray,
        target: Int,
        window: Int
    ): List<Pair<Int, Boolean>> {
        val crossings = mutableListOf<Pair<Int, Boolean>>()
        val from = (target - window).coerceAtLeast(0)
        val to = (target + window).coerceAtMost(samples.size - 2)

        for (i in from..to) {
            val curr = samples[i].toInt()
            val next = samples[i + 1].toInt()

            // Zero crossing: sign change (or exact zero)
            if ((curr <= 0 && next > 0)) {
                // Positive-going zero crossing
                crossings.add(Pair(i + 1, true))
            } else if ((curr >= 0 && next < 0)) {
                // Negative-going zero crossing
                crossings.add(Pair(i + 1, false))
            }
        }

        return crossings
    }

    /**
     * Create an SF2 file with a single sample mapped across all keys.
     *
     * @param outputFile The output SF2 file
     * @param instrumentName Name of the instrument (max 20 chars)
     * @param sample Sample data including audio file and root note
     * @param presetNumber MIDI preset number (0-127)
     * @param bankNumber MIDI bank number (0-127)
     * @return true if successful, false otherwise
     */
    fun writeSf2(
        outputFile: File,
        instrumentName: String,
        sample: SampleData,
        presetNumber: Int = 0,
        bankNumber: Int = 0
    ): Boolean {
        return writeSf2(outputFile, instrumentName, listOf(sample), presetNumber, bankNumber)
    }

    /**
     * Create an SF2 file with multiple samples.
     *
     * @param outputFile The output SF2 file
     * @param instrumentName Name of the instrument (max 20 chars)
     * @param samples List of samples with key ranges and root notes
     * @param presetNumber MIDI preset number (0-127)
     * @param bankNumber MIDI bank number (0-127)
     * @return true if successful, false otherwise
     */
    fun writeSf2(
        outputFile: File,
        instrumentName: String,
        samples: List<SampleData>,
        presetNumber: Int = 0,
        bankNumber: Int = 0
    ): Boolean {
        if (samples.isEmpty()) {
            Log.e(TAG, "No samples provided")
            return false
        }

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0) // Clear file
                val writer = Sf2ChunkWriter(raf)

                // Load all sample audio data, optimize loop points, and apply crossfade
                val sampleDataList = mutableListOf<Triple<SampleData, ShortArray, OptimizedLoop?>>()
                for (sample in samples) {
                    val audioData = loadSampleAudio(sample.audioFile)
                    if (audioData.isEmpty()) {
                        Log.e(TAG, "Failed to load sample: ${sample.name}")
                        return false
                    }
                    val optimizedLoop = if (sample.hasLoop && sample.loopEnd > sample.loopStart) {
                        snapLoopToZeroCrossings(audioData, sample.loopStart, sample.loopEnd)
                    } else {
                        null
                    }
                    // Apply crossfade at loop boundary for seamless looping
                    val processedAudio = if (sample.hasLoop && optimizedLoop != null) {
                        val crossfader = Crossfader(sample.sampleRate)
                        crossfader.applyCrossfadeLoop(
                            audioData,
                            optimizedLoop.start,
                            optimizedLoop.end,
                            crossfadeMs = Sf2Constants.LOOP_CROSSFADE_MS,
                            curveType = Crossfader.CurveType.EQUAL_POWER
                        )
                    } else {
                        audioData
                    }
                    sampleDataList.add(Triple(sample, processedAudio, optimizedLoop))
                }

                // Write RIFF header (placeholder size)
                writer.writeChunkId("RIFF")
                val riffSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("sfbk")

                // Write LIST 'INFO'
                writer.getPosition()
                writer.writeChunkId("LIST")
                val infoSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("INFO")
                val infoContentStart = writer.getPosition()
                writer.writeInfoChunk(instrumentName.take(19), "Created with A2U SF2 Creator")
                val infoSize = writer.getPosition() - infoContentStart + 4 // +4 for 'INFO'
                // Update INFO list size
                val currentPos = writer.getPosition()
                writer.seek(infoSizePos)
                writer.writeUInt32(infoSize)
                writer.seek(currentPos)

                // Write LIST 'sdta'
                val sdtaListStart = writer.getPosition()
                writer.writeChunkId("LIST")
                val sdtaSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("sdta")

                // Write smpl chunk
                writer.writeChunkId("smpl")
                val smplSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                val smplDataStart = writer.getPosition()

                // Write all sample data
                val sampleOffsets = mutableListOf<Pair<Long, Long>>() // start, end positions
                for ((_, audioData, _) in sampleDataList) {
                    val startOffset = (writer.getPosition() - smplDataStart) / 2 // In samples
                    writer.writeSamples(audioData)
                    // Add 46 zero samples padding
                    writer.writeSamples(ShortArray(Sf2Constants.SAMPLE_PADDING))
                    val endOffset = (writer.getPosition() - smplDataStart) / 2 - Sf2Constants.SAMPLE_PADDING
                    sampleOffsets.add(Pair(startOffset, endOffset))
                }

                val smplSize = writer.getPosition() - smplDataStart
                // Update smpl size
                writer.seek(smplSizePos)
                writer.writeUInt32(smplSize)
                writer.seek(smplDataStart + smplSize)

                // RIFF LIST size = type_id (4) + content
                // sdtaSize = "sdta" (4) + smpl_chunk = position - (sdtaListStart + 8)
                val sdtaSize = writer.getPosition() - sdtaListStart - 8
                writer.seek(sdtaSizePos)
                writer.writeUInt32(sdtaSize)
                writer.seek(sdtaListStart + 8 + sdtaSize)

                // Write LIST 'pdta'
                val pdtaListStart = writer.getPosition()
                writer.writeChunkId("LIST")
                val pdtaSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("pdta")

                // phdr - Preset headers
                writer.writeChunkId("phdr")
                writer.writeUInt32((38 * 2).toLong()) // 1 preset + 1 terminal
                writer.writePresetHeader(instrumentName.take(19), presetNumber, bankNumber, 0)
                writer.writePresetHeader("EOP", 0, 0, 1) // Terminal

                // pbag - Preset bags
                writer.writeChunkId("pbag")
                writer.writeUInt32((4 * 2).toLong()) // 1 bag + 1 terminal
                writer.writeBag(0, 0) // Preset 0 starts at generator 0
                writer.writeBag(1, 0) // Terminal

                // pmod - Preset modulators (empty)
                writer.writeChunkId("pmod")
                writer.writeUInt32(10) // 1 terminal modulator
                writer.writeModulator(0, 0, 0, 0, 0) // Terminal

                // pgen - Preset generators
                writer.writeChunkId("pgen")
                writer.writeUInt32((4 * 2).toLong()) // 1 generator + 1 terminal
                // Point to instrument 0
                writer.writeGenerator(Sf2ChunkWriter.GEN_INSTRUMENT, 0)
                // Terminal
                writer.writeGenerator(0, 0)

                // inst - Instrument headers
                writer.writeChunkId("inst")
                writer.writeUInt32((22 * 2).toLong()) // 1 instrument + 1 terminal
                writer.writeInstrumentHeader(instrumentName.take(19), 0)
                writer.writeInstrumentHeader("EOI", samples.size) // Terminal

                // ibag - Instrument bags
                val generatorsPerZone = 43 // 37 original + 6 new (keynum-to-env + fixed key/vel)
                writer.writeChunkId("ibag")
                val numIBags = samples.size + 1 // One per sample zone + terminal
                writer.writeUInt32((4 * numIBags).toLong())
                var genIndex = 0
                for (i in samples.indices) {
                    writer.writeBag(genIndex, 0) // SampleData doesn't have modulators
                    genIndex += generatorsPerZone
                }
                writer.writeBag(genIndex, 0) // Terminal

                // imod - Instrument modulators (SampleData doesn't have modulators, just terminal)
                writer.writeChunkId("imod")
                writer.writeUInt32(10) // 1 terminal modulator
                writer.writeModulator(0, 0, 0, 0, 0) // Terminal

                // igen - Instrument generators (SF2 spec: keyRange first, sampleID last)
                val numIGens = samples.size * generatorsPerZone + 1
                writer.writeChunkId("igen")
                writer.writeUInt32((4 * numIGens).toLong())

                for (i in samples.indices) {
                    val sample = samples[i]

                    // 1. Key range (43) - MUST be first per SF2 spec
                    writer.writeGeneratorRange(
                        Sf2Constants.GEN_KEY_RANGE,
                        sample.keyRangeStart,
                        sample.keyRangeEnd
                    )
                    // 2. Velocity range (44)
                    writer.writeGeneratorRange(
                        Sf2Constants.GEN_VEL_RANGE,
                        sample.velRangeStart,
                        sample.velRangeEnd
                    )
                    // 3. Mod LFO to pitch (5)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_PITCH, sample.modLfoToPitch)
                    // 4. Vib LFO to pitch (6)
                    writer.writeGenerator(Sf2Constants.GEN_VIB_LFO_TO_PITCH, sample.vibLfoToPitch)
                    // 5. Mod Env to pitch (7)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_ENV_TO_PITCH, sample.modEnvToPitch)
                    // 6. Filter cutoff (8) - SF2 native units (absolute cents)
                    writer.writeGenerator(Sf2Constants.GEN_INITIAL_FILTER_FC, sample.filterFc)
                    // 7. Filter resonance (9)
                    writer.writeGenerator(Sf2Constants.GEN_INITIAL_FILTER_Q, sample.filterQ)
                    // 8. Mod LFO to filter (10)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_FILTER_FC, sample.modLfoToFilterFc)
                    // 9. Mod Env to filter (11)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_ENV_TO_FILTER_FC, sample.modEnvToFilterFc)
                    // 10. Mod LFO to volume (13)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_VOLUME, sample.modLfoToVolume)
                    // 11. Chorus send (15)
                    writer.writeGenerator(Sf2Constants.GEN_CHORUS_EFFECTS_SEND, sample.chorusSend)
                    // 12. Reverb send (16)
                    writer.writeGenerator(Sf2Constants.GEN_REVERB_EFFECTS_SEND, sample.reverbSend)
                    // 13. Pan (17)
                    writer.writeGenerator(Sf2Constants.GEN_PAN, sample.pan)
                    // 14. Mod LFO delay (21) - SF2 native units (timecents)
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_MOD_LFO, sample.modLfoDelay)
                    // 15. Mod LFO freq (22)
                    writer.writeGenerator(Sf2Constants.GEN_FREQ_MOD_LFO, sample.modLfoFreq)
                    // 16. Vib LFO delay (23) - SF2 native units (timecents)
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_VIB_LFO, sample.vibLfoDelay)
                    // 17. Vib LFO freq (24)
                    writer.writeGenerator(Sf2Constants.GEN_FREQ_VIB_LFO, sample.vibLfoFreq)
                    // 18. Mod Env delay (25) - SF2 native units (timecents)
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_MOD_ENV, sample.modEnvDelay)
                    // 19. Mod Env attack (26)
                    writer.writeGenerator(Sf2Constants.GEN_ATTACK_MOD_ENV, sample.modEnvAttack)
                    // 20. Mod Env hold (27)
                    writer.writeGenerator(Sf2Constants.GEN_HOLD_MOD_ENV, sample.modEnvHold)
                    // 21. Mod Env decay (28)
                    writer.writeGenerator(Sf2Constants.GEN_DECAY_MOD_ENV, sample.modEnvDecay)
                    // 22. Mod Env sustain (29) - SF2 native units (centibels)
                    writer.writeGenerator(Sf2Constants.GEN_SUSTAIN_MOD_ENV, sample.modEnvSustain)
                    // 23. Mod Env release (30)
                    writer.writeGenerator(Sf2Constants.GEN_RELEASE_MOD_ENV, sample.modEnvRelease)
                    // 24. Vol Env delay (33) - SF2 native units (timecents)
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_VOL_ENV, sample.volEnvDelay)
                    // 25. Vol Env attack (34)
                    writer.writeGenerator(Sf2Constants.GEN_ATTACK_VOL_ENV, sample.volEnvAttack)
                    // 26. Vol Env hold (35)
                    writer.writeGenerator(Sf2Constants.GEN_HOLD_VOL_ENV, sample.volEnvHold)
                    // 27. Vol Env decay (36)
                    writer.writeGenerator(Sf2Constants.GEN_DECAY_VOL_ENV, sample.volEnvDecay)
                    // 28. Vol Env sustain (37) - SF2 native units (centibels)
                    writer.writeGenerator(Sf2Constants.GEN_SUSTAIN_VOL_ENV, sample.volEnvSustain)
                    // 29. Vol Env release (38)
                    writer.writeGenerator(Sf2Constants.GEN_RELEASE_VOL_ENV, sample.volEnvRelease)
                    // 30. Initial attenuation (48)
                    writer.writeGenerator(Sf2Constants.GEN_INITIAL_ATTENUATION, sample.attenuation)
                    // 31. Coarse tune (51)
                    writer.writeGenerator(Sf2Constants.GEN_COARSE_TUNE, sample.coarseTune)
                    // 32. Fine tune (52)
                    writer.writeGenerator(Sf2Constants.GEN_FINE_TUNE, sample.fineTuneCents)
                    // 33. Sample modes (54) - use sampleModes if set, otherwise fallback to hasLoop
                    val sampleMode = if (sample.sampleModes != 0) sample.sampleModes
                                     else if (sample.hasLoop) Sf2Constants.LOOP_MODE_CONTINUOUS
                                     else Sf2Constants.LOOP_MODE_NONE
                    writer.writeGenerator(Sf2Constants.GEN_SAMPLE_MODES, sampleMode)
                    // 34. Scale tuning (56)
                    writer.writeGenerator(Sf2Constants.GEN_SCALE_TUNING, sample.scaleTuning)
                    // 35. Exclusive class (57)
                    writer.writeGenerator(Sf2Constants.GEN_EXCLUSIVE_CLASS, sample.exclusiveClass)
                    // 36. Key to mod env hold (31)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_HOLD, sample.keyToModEnvHold)
                    // 37. Key to mod env decay (32)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_DECAY, sample.keyToModEnvDecay)
                    // 38. Key to vol env hold (39)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_HOLD, sample.keyToVolEnvHold)
                    // 39. Key to vol env decay (40)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_DECAY, sample.keyToVolEnvDecay)
                    // 40. Fixed key (46) - -1 = not fixed
                    writer.writeGenerator(Sf2Constants.GEN_FIXED_KEY, sample.fixedKey)
                    // 41. Fixed velocity (47) - -1 = not fixed
                    writer.writeGenerator(Sf2Constants.GEN_FIXED_VELOCITY, sample.fixedVelocity)
                    // 42. Overriding root key (58)
                    writer.writeGenerator(Sf2Constants.GEN_OVERRIDING_ROOT_KEY, sample.rootNote)
                    // 43. Sample ID (53) - MUST be last per SF2 spec
                    writer.writeGenerator(Sf2Constants.GEN_SAMPLE_ID, i)
                }
                // Terminal
                writer.writeGenerator(0, 0)

                // shdr - Sample headers
                writer.writeChunkId("shdr")
                writer.writeUInt32((46 * (samples.size + 1)).toLong()) // samples + terminal

                for (i in samples.indices) {
                    val sample = samples[i]
                    val (startOffset, endOffset) = sampleOffsets[i]
                    val optimizedLoop = sampleDataList[i].third

                    // Loop points: use optimized zero-crossing positions when available
                    // SF2 loopEnd is exclusive (first sample after the loop)
                    val loopStart = if (sample.hasLoop && optimizedLoop != null) {
                        startOffset + optimizedLoop.start
                    } else if (sample.hasLoop) {
                        startOffset + sample.loopStart
                    } else {
                        startOffset
                    }
                    val loopEnd = if (sample.hasLoop && optimizedLoop != null) {
                        (startOffset + optimizedLoop.end + 1).coerceAtMost(endOffset)
                    } else if (sample.hasLoop) {
                        (startOffset + sample.loopEnd + 1).coerceAtMost(endOffset)
                    } else {
                        endOffset
                    }

                    writer.writeSampleHeader(
                        name = sample.name.take(19),
                        start = startOffset,
                        end = endOffset,
                        loopStart = loopStart,
                        loopEnd = loopEnd,
                        sampleRate = sample.sampleRate,
                        originalPitch = sample.rootNote,
                        pitchCorrection = sample.pitchCorrection,
                        sampleLink = 0,
                        sampleType = Sf2ChunkWriter.SAMPLE_TYPE_MONO
                    )
                }
                // Terminal sample header
                writer.writeSampleHeader(
                    name = "EOS",
                    start = 0,
                    end = 0,
                    loopStart = 0,
                    loopEnd = 0,
                    sampleRate = 0,
                    originalPitch = 0,
                    pitchCorrection = 0,
                    sampleLink = 0,
                    sampleType = 0
                )

                // Save end position before seeking back
                val fileEndPos = writer.getPosition()

                // Update pdta size
                val pdtaSize = fileEndPos - pdtaListStart - 8
                writer.seek(pdtaSizePos)
                writer.writeUInt32(pdtaSize)

                // Update RIFF size (total file size - 8 bytes for "RIFF" + size field)
                writer.seek(riffSizePos)
                writer.writeUInt32(fileEndPos - 8)

                Log.d(TAG, "SF2 file written successfully: ${outputFile.absolutePath}")
                Log.d(TAG, "Total size: $fileEndPos bytes")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing SF2 file", e)
            outputFile.delete()
            return false
        }
    }

    /**
     * Data class for instrument-level global parameters.
     * These are written to a "global zone" (first zone without sampleID) in SF2.
     * Values of 0 mean "no modification" (default) and won't be written.
     */
    data class InstrumentGlobalParams(
        val attenuation: Int = 0,           // GEN 48
        val coarseTune: Int = 0,            // GEN 51
        val fineTune: Int = 0,              // GEN 52
        val volEnvDelay: Int = 0,           // GEN 33
        val volEnvAttack: Int = 0,          // GEN 34
        val volEnvHold: Int = 0,            // GEN 35
        val volEnvDecay: Int = 0,           // GEN 36
        val volEnvSustain: Int = 0,         // GEN 37
        val volEnvRelease: Int = 0,         // GEN 38
        val modEnvDelay: Int = 0,           // GEN 25
        val modEnvAttack: Int = 0,          // GEN 26
        val modEnvHold: Int = 0,            // GEN 27
        val modEnvDecay: Int = 0,           // GEN 28
        val modEnvSustain: Int = 0,         // GEN 29
        val modEnvRelease: Int = 0,         // GEN 30
        val modEnvToPitch: Int = 0,         // GEN 7
        val modEnvToFilterFc: Int = 0,      // GEN 11
        val vibLfoDelay: Int = 0,           // GEN 23
        val vibLfoFreq: Int = 0,            // GEN 24
        val vibLfoToPitch: Int = 0,         // GEN 6
        val modLfoDelay: Int = 0,           // GEN 21
        val modLfoFreq: Int = 0,            // GEN 22
        val modLfoToPitch: Int = 0,         // GEN 5
        val modLfoToFilterFc: Int = 0,      // GEN 10
        val modLfoToVolume: Int = 0,        // GEN 13
        val filterFc: Int = 0,              // GEN 8 (in cents, 0 = no modification)
        val filterQ: Int = 0,               // GEN 9
        val chorusSend: Int = 0,            // GEN 15
        val reverbSend: Int = 0,            // GEN 16
        val pan: Int = 0,                   // GEN 17
        // Key-based envelope scaling (can be in global zone)
        val keyToModEnvHold: Int = 0,       // GEN 31
        val keyToModEnvDecay: Int = 0,      // GEN 32
        val keyToVolEnvHold: Int = 0,       // GEN 39
        val keyToVolEnvDecay: Int = 0,      // GEN 40
        // Additional generators that can appear in global zones
        val scaleTuning: Int = 0,           // GEN 56 (0 = use sample default, 100 = normal)
        val exclusiveClass: Int = 0         // GEN 57 (0 = none)
    ) {
        fun hasAnyNonDefault(): Boolean {
            return attenuation != 0 || coarseTune != 0 || fineTune != 0 ||
                    volEnvDelay != 0 || volEnvAttack != 0 || volEnvHold != 0 ||
                    volEnvDecay != 0 || volEnvSustain != 0 || volEnvRelease != 0 ||
                    modEnvDelay != 0 || modEnvAttack != 0 || modEnvHold != 0 ||
                    modEnvDecay != 0 || modEnvSustain != 0 || modEnvRelease != 0 ||
                    modEnvToPitch != 0 || modEnvToFilterFc != 0 ||
                    vibLfoDelay != 0 || vibLfoFreq != 0 || vibLfoToPitch != 0 ||
                    modLfoDelay != 0 || modLfoFreq != 0 || modLfoToPitch != 0 ||
                    modLfoToFilterFc != 0 || modLfoToVolume != 0 ||
                    filterFc != 0 || filterQ != 0 ||
                    chorusSend != 0 || reverbSend != 0 || pan != 0 ||
                    keyToModEnvHold != 0 || keyToModEnvDecay != 0 ||
                    keyToVolEnvHold != 0 || keyToVolEnvDecay != 0 ||
                    scaleTuning != 0 || exclusiveClass != 0
        }

        fun buildGenerators(): List<Pair<Int, Int>> {
            val gens = mutableListOf<Pair<Int, Int>>()
            // Order matters less for global zone, but let's be consistent
            if (modLfoToPitch != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_PITCH, modLfoToPitch))
            if (vibLfoToPitch != 0) gens.add(Pair(Sf2Constants.GEN_VIB_LFO_TO_PITCH, vibLfoToPitch))
            if (modEnvToPitch != 0) gens.add(Pair(Sf2Constants.GEN_MOD_ENV_TO_PITCH, modEnvToPitch))
            if (filterFc != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_FILTER_FC, filterFc))
            if (filterQ != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_FILTER_Q, filterQ))
            if (modLfoToFilterFc != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_FILTER_FC, modLfoToFilterFc))
            if (modEnvToFilterFc != 0) gens.add(Pair(Sf2Constants.GEN_MOD_ENV_TO_FILTER_FC, modEnvToFilterFc))
            if (modLfoToVolume != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_VOLUME, modLfoToVolume))
            if (chorusSend != 0) gens.add(Pair(Sf2Constants.GEN_CHORUS_EFFECTS_SEND, chorusSend))
            if (reverbSend != 0) gens.add(Pair(Sf2Constants.GEN_REVERB_EFFECTS_SEND, reverbSend))
            if (pan != 0) gens.add(Pair(Sf2Constants.GEN_PAN, pan))
            if (modLfoDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_MOD_LFO, modLfoDelay))
            if (modLfoFreq != 0) gens.add(Pair(Sf2Constants.GEN_FREQ_MOD_LFO, modLfoFreq))
            if (vibLfoDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_VIB_LFO, vibLfoDelay))
            if (vibLfoFreq != 0) gens.add(Pair(Sf2Constants.GEN_FREQ_VIB_LFO, vibLfoFreq))
            if (modEnvDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_MOD_ENV, modEnvDelay))
            if (modEnvAttack != 0) gens.add(Pair(Sf2Constants.GEN_ATTACK_MOD_ENV, modEnvAttack))
            if (modEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_HOLD_MOD_ENV, modEnvHold))
            if (modEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_DECAY_MOD_ENV, modEnvDecay))
            if (modEnvSustain != 0) gens.add(Pair(Sf2Constants.GEN_SUSTAIN_MOD_ENV, modEnvSustain))
            if (modEnvRelease != 0) gens.add(Pair(Sf2Constants.GEN_RELEASE_MOD_ENV, modEnvRelease))
            if (volEnvDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_VOL_ENV, volEnvDelay))
            if (volEnvAttack != 0) gens.add(Pair(Sf2Constants.GEN_ATTACK_VOL_ENV, volEnvAttack))
            if (volEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_HOLD_VOL_ENV, volEnvHold))
            if (volEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_DECAY_VOL_ENV, volEnvDecay))
            if (volEnvSustain != 0) gens.add(Pair(Sf2Constants.GEN_SUSTAIN_VOL_ENV, volEnvSustain))
            if (volEnvRelease != 0) gens.add(Pair(Sf2Constants.GEN_RELEASE_VOL_ENV, volEnvRelease))
            if (keyToModEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_HOLD, keyToModEnvHold))
            if (keyToModEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_DECAY, keyToModEnvDecay))
            if (keyToVolEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_HOLD, keyToVolEnvHold))
            if (keyToVolEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_DECAY, keyToVolEnvDecay))
            if (attenuation != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_ATTENUATION, attenuation))
            if (coarseTune != 0) gens.add(Pair(Sf2Constants.GEN_COARSE_TUNE, coarseTune))
            if (fineTune != 0) gens.add(Pair(Sf2Constants.GEN_FINE_TUNE, fineTune))
            if (scaleTuning != 0) gens.add(Pair(Sf2Constants.GEN_SCALE_TUNING, scaleTuning))
            if (exclusiveClass != 0) gens.add(Pair(Sf2Constants.GEN_EXCLUSIVE_CLASS, exclusiveClass))
            return gens
        }
    }

    /**
     * Data class for preset zone parameters (PGEN).
     * These generators are ADDITIVE to the instrument's generators.
     * Stored in SF2 native units (timecents for times, etc.)
     */
    data class PresetZoneParams(
        val keyRangeLow: Int = 0,
        val keyRangeHigh: Int = 127,
        val velRangeLow: Int = 0,
        val velRangeHigh: Int = 127,
        val attenuation: Int = 0,           // GEN 48
        val coarseTune: Int = 0,            // GEN 51
        val fineTune: Int = 0,              // GEN 52
        val filterFc: Int = 0,              // GEN 8
        val filterQ: Int = 0,               // GEN 9
        val chorusSend: Int = 0,            // GEN 15
        val reverbSend: Int = 0,            // GEN 16
        val pan: Int = 0,                   // GEN 17
        val volEnvDelay: Int = 0,           // GEN 33
        val volEnvAttack: Int = 0,          // GEN 34
        val volEnvHold: Int = 0,            // GEN 35
        val volEnvDecay: Int = 0,           // GEN 36
        val volEnvSustain: Int = 0,         // GEN 37
        val volEnvRelease: Int = 0,         // GEN 38
        val modEnvDelay: Int = 0,           // GEN 25
        val modEnvAttack: Int = 0,          // GEN 26
        val modEnvHold: Int = 0,            // GEN 27
        val modEnvDecay: Int = 0,           // GEN 28
        val modEnvSustain: Int = 0,         // GEN 29
        val modEnvRelease: Int = 0,         // GEN 30
        val modEnvToPitch: Int = 0,         // GEN 7
        val modEnvToFilterFc: Int = 0,      // GEN 11
        val vibLfoDelay: Int = 0,           // GEN 23
        val vibLfoFreq: Int = 0,            // GEN 24
        val vibLfoToPitch: Int = 0,         // GEN 6
        val modLfoDelay: Int = 0,           // GEN 21
        val modLfoFreq: Int = 0,            // GEN 22
        val modLfoToPitch: Int = 0,         // GEN 5
        val modLfoToFilterFc: Int = 0,      // GEN 10
        val modLfoToVolume: Int = 0,        // GEN 13
        // Key-based envelope scaling (can be in preset zones)
        val keyToModEnvHold: Int = 0,       // GEN 31
        val keyToModEnvDecay: Int = 0,      // GEN 32
        val keyToVolEnvHold: Int = 0,       // GEN 39
        val keyToVolEnvDecay: Int = 0,      // GEN 40
        // Additional generators that can appear in preset zones
        val scaleTuning: Int = 0,           // GEN 56 (0 = use sample default, 100 = normal)
        val exclusiveClass: Int = 0         // GEN 57 (0 = none)
    ) {
        /**
         * Build list of (generatorId, value) pairs for non-default values.
         * SF2 spec: keyRange first (if not full), velRange second (if not full), instrument last.
         */
        fun buildGenerators(): List<Pair<Int, Int>> {
            val gens = mutableListOf<Pair<Int, Int>>()

            // Key range - only if not full range
            if (keyRangeLow != 0 || keyRangeHigh != 127) {
                gens.add(Pair(Sf2Constants.GEN_KEY_RANGE, (keyRangeHigh shl 8) or keyRangeLow))
            }
            // Velocity range - only if not full range
            if (velRangeLow != 0 || velRangeHigh != 127) {
                gens.add(Pair(Sf2Constants.GEN_VEL_RANGE, (velRangeHigh shl 8) or velRangeLow))
            }

            // Other generators - only if non-zero
            if (modLfoToPitch != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_PITCH, modLfoToPitch))
            if (vibLfoToPitch != 0) gens.add(Pair(Sf2Constants.GEN_VIB_LFO_TO_PITCH, vibLfoToPitch))
            if (modEnvToPitch != 0) gens.add(Pair(Sf2Constants.GEN_MOD_ENV_TO_PITCH, modEnvToPitch))
            if (filterFc != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_FILTER_FC, filterFc))
            if (filterQ != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_FILTER_Q, filterQ))
            if (modLfoToFilterFc != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_FILTER_FC, modLfoToFilterFc))
            if (modEnvToFilterFc != 0) gens.add(Pair(Sf2Constants.GEN_MOD_ENV_TO_FILTER_FC, modEnvToFilterFc))
            if (modLfoToVolume != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_VOLUME, modLfoToVolume))
            if (chorusSend != 0) gens.add(Pair(Sf2Constants.GEN_CHORUS_EFFECTS_SEND, chorusSend))
            if (reverbSend != 0) gens.add(Pair(Sf2Constants.GEN_REVERB_EFFECTS_SEND, reverbSend))
            if (pan != 0) gens.add(Pair(Sf2Constants.GEN_PAN, pan))
            if (modLfoDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_MOD_LFO, modLfoDelay))
            if (modLfoFreq != 0) gens.add(Pair(Sf2Constants.GEN_FREQ_MOD_LFO, modLfoFreq))
            if (vibLfoDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_VIB_LFO, vibLfoDelay))
            if (vibLfoFreq != 0) gens.add(Pair(Sf2Constants.GEN_FREQ_VIB_LFO, vibLfoFreq))
            if (modEnvDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_MOD_ENV, modEnvDelay))
            if (modEnvAttack != 0) gens.add(Pair(Sf2Constants.GEN_ATTACK_MOD_ENV, modEnvAttack))
            if (modEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_HOLD_MOD_ENV, modEnvHold))
            if (modEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_DECAY_MOD_ENV, modEnvDecay))
            if (modEnvSustain != 0) gens.add(Pair(Sf2Constants.GEN_SUSTAIN_MOD_ENV, modEnvSustain))
            if (modEnvRelease != 0) gens.add(Pair(Sf2Constants.GEN_RELEASE_MOD_ENV, modEnvRelease))
            if (volEnvDelay != 0) gens.add(Pair(Sf2Constants.GEN_DELAY_VOL_ENV, volEnvDelay))
            if (volEnvAttack != 0) gens.add(Pair(Sf2Constants.GEN_ATTACK_VOL_ENV, volEnvAttack))
            if (volEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_HOLD_VOL_ENV, volEnvHold))
            if (volEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_DECAY_VOL_ENV, volEnvDecay))
            if (volEnvSustain != 0) gens.add(Pair(Sf2Constants.GEN_SUSTAIN_VOL_ENV, volEnvSustain))
            if (volEnvRelease != 0) gens.add(Pair(Sf2Constants.GEN_RELEASE_VOL_ENV, volEnvRelease))
            if (keyToModEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_HOLD, keyToModEnvHold))
            if (keyToModEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_DECAY, keyToModEnvDecay))
            if (keyToVolEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_HOLD, keyToVolEnvHold))
            if (keyToVolEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_DECAY, keyToVolEnvDecay))
            if (attenuation != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_ATTENUATION, attenuation))
            if (coarseTune != 0) gens.add(Pair(Sf2Constants.GEN_COARSE_TUNE, coarseTune))
            if (fineTune != 0) gens.add(Pair(Sf2Constants.GEN_FINE_TUNE, fineTune))
            if (scaleTuning != 0) gens.add(Pair(Sf2Constants.GEN_SCALE_TUNING, scaleTuning))
            if (exclusiveClass != 0) gens.add(Pair(Sf2Constants.GEN_EXCLUSIVE_CLASS, exclusiveClass))

            return gens
        }

        /**
         * Check if any non-range parameters are non-default.
         */
        fun hasNonDefaultParams(): Boolean {
            return attenuation != 0 || coarseTune != 0 || fineTune != 0 ||
                    filterFc != 0 || filterQ != 0 ||
                    chorusSend != 0 || reverbSend != 0 || pan != 0 ||
                    volEnvDelay != 0 || volEnvAttack != 0 || volEnvHold != 0 ||
                    volEnvDecay != 0 || volEnvSustain != 0 || volEnvRelease != 0 ||
                    modEnvDelay != 0 || modEnvAttack != 0 || modEnvHold != 0 ||
                    modEnvDecay != 0 || modEnvSustain != 0 || modEnvRelease != 0 ||
                    modEnvToPitch != 0 || modEnvToFilterFc != 0 ||
                    vibLfoDelay != 0 || vibLfoFreq != 0 || vibLfoToPitch != 0 ||
                    modLfoDelay != 0 || modLfoFreq != 0 || modLfoToPitch != 0 ||
                    modLfoToFilterFc != 0 || modLfoToVolume != 0 ||
                    keyToModEnvHold != 0 || keyToModEnvDecay != 0 ||
                    keyToVolEnvHold != 0 || keyToVolEnvDecay != 0 ||
                    scaleTuning != 0 || exclusiveClass != 0
        }
    }

    /**
     * Data class representing an instrument within a preset zone.
     * Each instrument in a preset corresponds to a preset zone with PGEN parameters.
     */
    data class InstrumentExportData(
        val name: String,
        val samples: List<InMemorySample>,
        val globalParams: InstrumentGlobalParams = InstrumentGlobalParams(),
        val globalModulators: List<InMemoryModulator> = emptyList(), // IMOD global zone
        val presetZoneParams: PresetZoneParams = PresetZoneParams(),
        val presetZoneModulators: List<InMemoryModulator> = emptyList()
    )

    /**
     * Data class representing a preset with its instruments for multi-preset export.
     * Each preset maps to a MIDI program number and can contain multiple instruments.
     */
    data class PresetExportData(
        val name: String,
        val programNumber: Int,
        val bankNumber: Int = 0,
        val instruments: List<InstrumentExportData>,
        val modulators: List<InMemoryModulator> = emptyList(), // Preset-level modulators (pmod)
        val presetGlobalParams: PresetZoneParams? = null // Global preset zone (PGEN without GEN_INSTRUMENT)
    ) {
        // For backward compatibility - get all samples from all instruments
        val samples: List<InMemorySample>
            get() = instruments.flatMap { it.samples }

        // Helper to create from flat samples list (legacy support)
        companion object {
            fun fromSamples(
                name: String,
                programNumber: Int,
                bankNumber: Int = 0,
                samples: List<InMemorySample>
            ): PresetExportData {
                return PresetExportData(
                    name = name,
                    programNumber = programNumber,
                    bankNumber = bankNumber,
                    instruments = listOf(InstrumentExportData(name, samples))
                )
            }
        }
    }

    /**
     * Data class for in-memory modulator.
     * Represents a modulation routing from a source to a destination.
     */
    data class InMemoryModulator(
        val srcOper: Int,      // Source operator (controller + flags)
        val destOper: Int,     // Destination generator number
        val amount: Int,       // Modulation amount (signed)
        val amtSrcOper: Int,   // Secondary source operator
        val transOper: Int     // Transform operator
    )

    /**
     * Data class for in-memory sample (no file required).
     * Contains all SF2 synthesis parameters in NATIVE SF2 UNITS.
     *
     * IMPORTANT: All time/envelope parameters are stored in SF2 native units:
     * - Time values: timecents (1200 * log2(seconds)), -12000 = instant
     * - Sustain levels: centibels of attenuation (0 = full volume, 1000 = silent)
     * - Filter cutoff: absolute cents (1200 * log2(freq/8.176))
     *
     * This ensures lossless import/export - values are written directly to SF2.
     *
     * Supports lazy loading: if audioLoader is provided, samples will be loaded on-demand
     * to reduce memory usage when exporting large SF2 files.
     */
    data class InMemorySample(
        val name: String,
        val samples: ShortArray = ShortArray(0),
        val sampleRate: Int = 44100,
        val rootNote: Int = 60,
        val keyRangeStart: Int = 0,
        val keyRangeEnd: Int = 127,
        val loopStart: Int = 0,
        val loopEnd: Int = 0,
        val hasLoop: Boolean = false,
        val sampleModes: Int = 0, // SF2 sampleModes: 0=no loop, 1=loop continuously, 3=loop then release
        val attenuation: Int = 0, // In centibels (0 = full volume, 480 = -48 dB)
        val fineTuneCents: Int = 0, // Fine tuning in cents (-99 to +99)
        // ==================== Volume Envelope - SF2 Native Units ====================
        val volEnvDelay: Int = -12000,    // timecents (-12000 = instant)
        val volEnvAttack: Int = -12000,   // timecents
        val volEnvHold: Int = -12000,     // timecents
        val volEnvDecay: Int = -12000,    // timecents
        val volEnvSustain: Int = 0,       // centibels (0 = full, 1000 = silent)
        val volEnvRelease: Int = -12000,  // timecents
        // ==================== Filter - SF2 Native Units ====================
        val filterFc: Int = 13500,        // absolute cents (13500 = ~20kHz)
        val filterQ: Int = 0,             // centibels (0-960)
        val chorusSend: Int = 0, // Chorus effects send (0-1000, units of 0.1%)
        val reverbSend: Int = 0, // Reverb effects send (0-1000, units of 0.1%)
        val pan: Int = 0, // Stereo pan (-500 = left, 0 = center, +500 = right)
        // ==================== Advanced SF2 Parameters ====================
        // Velocity range
        val velRangeStart: Int = 0,
        val velRangeEnd: Int = 127,
        // Tuning
        val coarseTune: Int = 0, // In semitones (-120 to +120)
        val scaleTuning: Int = 100, // Cents per semitone (0-1200, 100 = normal)
        // ==================== Modulation Envelope - SF2 Native Units ====================
        val modEnvDelay: Int = -12000,    // timecents
        val modEnvAttack: Int = -12000,   // timecents
        val modEnvHold: Int = -12000,     // timecents
        val modEnvDecay: Int = -12000,    // timecents
        val modEnvSustain: Int = 0,       // centibels
        val modEnvRelease: Int = -12000,  // timecents
        val modEnvToPitch: Int = 0, // In cents (-12000 to +12000)
        val modEnvToFilterFc: Int = 0, // In cents (-12000 to +12000)
        // ==================== Vibrato LFO - SF2 Native Units ====================
        val vibLfoDelay: Int = -12000,    // timecents
        val vibLfoFreq: Int = 0,          // cents (0 = ~8.176 Hz)
        val vibLfoToPitch: Int = 0, // In cents (-12000 to +12000)
        // ==================== Modulation LFO - SF2 Native Units ====================
        val modLfoDelay: Int = -12000,    // timecents
        val modLfoFreq: Int = 0,          // cents (0 = ~8.176 Hz)
        val modLfoToPitch: Int = 0, // In cents (-12000 to +12000)
        val modLfoToFilterFc: Int = 0, // In cents (-12000 to +12000)
        val modLfoToVolume: Int = 0, // In centibels (-960 to +960)
        // Exclusive class
        val exclusiveClass: Int = 0, // 0 = none
        // Key-to-envelope scaling
        val keyToVolEnvHold: Int = 0, // Timecents per key
        val keyToVolEnvDecay: Int = 0, // Timecents per key
        val keyToModEnvHold: Int = 0, // Timecents per key
        val keyToModEnvDecay: Int = 0, // Timecents per key
        // Fixed key/velocity (-1 = not fixed)
        val fixedKey: Int = -1,
        val fixedVelocity: Int = -1,
        // Sample header fields
        val pitchCorrection: Int = 0, // Pitch correction in cents (-99 to +99) from sample header
        // Modulators
        val modulators: List<InMemoryModulator> = emptyList(),
        // Lazy loading support - if set, samples are loaded on-demand
        val audioLoader: (() -> ShortArray)? = null,
        // Sample count for lazy-loaded samples (for size estimation)
        val sampleCount: Int = 0
    ) {
        /**
         * Get the audio samples, loading them on-demand if using lazy loading.
         */
        fun loadAudioSamples(): ShortArray {
            return if (audioLoader != null && samples.isEmpty()) {
                audioLoader.invoke()
            } else {
                samples
            }
        }

        /**
         * Get sample count without loading data.
         */
        fun getAudioSampleCount(): Int {
            return if (sampleCount > 0) sampleCount else samples.size
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as InMemorySample
            return name == other.name && samples.contentEquals(other.samples)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + samples.contentHashCode()
            return result
        }
    }

    /**
     * Create an SF2 file with a single in-memory sample.
     * No WAV file required - samples are provided directly as ShortArray.
     *
     * @param outputFile The output SF2 file
     * @param instrumentName Name of the instrument (max 20 chars)
     * @param sample In-memory sample data
     * @param presetNumber MIDI preset number (0-127)
     * @param bankNumber MIDI bank number (0-127)
     * @return true if successful, false otherwise
     */
    fun writeSf2FromMemory(
        outputFile: File,
        instrumentName: String,
        sample: InMemorySample,
        presetNumber: Int = 0,
        bankNumber: Int = 0
    ): Boolean {
        if (sample.samples.isEmpty()) {
            Log.e(TAG, "No sample data provided")
            return false
        }

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0) // Clear file
                val writer = Sf2ChunkWriter(raf)

                // Write RIFF header (placeholder size)
                writer.writeChunkId("RIFF")
                val riffSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("sfbk")

                // Write LIST 'INFO'
                writer.writeChunkId("LIST")
                val infoSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("INFO")
                val infoContentStart = writer.getPosition()
                writer.writeInfoChunk(instrumentName.take(19), "Preview Sample")
                val infoSize = writer.getPosition() - infoContentStart + 4 // +4 for 'INFO'
                val afterInfo = writer.getPosition()
                writer.seek(infoSizePos)
                writer.writeUInt32(infoSize)
                writer.seek(afterInfo)

                // Write LIST 'sdta'
                writer.writeChunkId("LIST")
                val sdtaSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("sdta")

                // Write smpl chunk
                writer.writeChunkId("smpl")
                val smplSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                val smplDataStart = writer.getPosition()

                // Optimize loop points to zero crossings for click-free looping
                val optimizedLoop = if (sample.hasLoop && sample.loopEnd > sample.loopStart) {
                    snapLoopToZeroCrossings(sample.samples, sample.loopStart, sample.loopEnd)
                } else {
                    null
                }

                // Apply crossfade at loop boundary for seamless looping
                val processedSamples = if (sample.hasLoop && optimizedLoop != null) {
                    val crossfader = Crossfader(sample.sampleRate)
                    crossfader.applyCrossfadeLoop(
                        sample.samples,
                        optimizedLoop.start,
                        optimizedLoop.end,
                        crossfadeMs = Sf2Constants.LOOP_CROSSFADE_MS,
                        curveType = Crossfader.CurveType.EQUAL_POWER
                    )
                } else {
                    sample.samples
                }

                // Write sample data
                val startOffset = 0L
                writer.writeSamples(processedSamples)
                // Add 46 zero samples padding (SF2 requirement)
                writer.writeSamples(ShortArray(Sf2Constants.SAMPLE_PADDING))
                val endOffset = sample.samples.size.toLong()

                val smplSize = writer.getPosition() - smplDataStart
                writer.seek(smplSizePos)
                writer.writeUInt32(smplSize)
                writer.seek(smplDataStart + smplSize)

                // Size = everything after size field: "sdta" (4) + smpl chunk
                val sdtaSize = writer.getPosition() - sdtaSizePos - 4
                writer.seek(sdtaSizePos)
                writer.writeUInt32(sdtaSize)
                writer.seek(sdtaSizePos + 4 + sdtaSize)

                // Write LIST 'pdta'
                val pdtaListStart = writer.getPosition()
                writer.writeChunkId("LIST")
                val pdtaSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("pdta")

                // phdr - Preset headers
                writer.writeChunkId("phdr")
                writer.writeUInt32((38 * 2).toLong()) // 1 preset + 1 terminal
                writer.writePresetHeader(instrumentName.take(19), presetNumber, bankNumber, 0)
                writer.writePresetHeader("EOP", 0, 0, 1) // Terminal

                // pbag - Preset bags
                writer.writeChunkId("pbag")
                writer.writeUInt32((4 * 2).toLong())
                writer.writeBag(0, 0)
                writer.writeBag(1, 0) // Terminal

                // pmod - Preset modulators (empty)
                writer.writeChunkId("pmod")
                writer.writeUInt32(10)
                writer.writeModulator(0, 0, 0, 0, 0) // Terminal

                // pgen - Preset generators
                writer.writeChunkId("pgen")
                writer.writeUInt32((4 * 2).toLong())
                writer.writeGenerator(Sf2ChunkWriter.GEN_INSTRUMENT, 0)
                writer.writeGenerator(0, 0) // Terminal

                // inst - Instrument headers
                writer.writeChunkId("inst")
                writer.writeUInt32((22 * 2).toLong())
                writer.writeInstrumentHeader(instrumentName.take(19), 0)
                writer.writeInstrumentHeader("EOI", 1) // Terminal

                // ibag - Instrument bags
                val generatorsPerZone = 43 // 37 original + 6 new (keynum-to-env + fixed key/vel)
                val numMods = sample.modulators.size
                writer.writeChunkId("ibag")
                writer.writeUInt32((4 * 2).toLong())
                writer.writeBag(0, 0)
                writer.writeBag(generatorsPerZone, numMods) // Terminal

                // imod - Instrument modulators
                writer.writeChunkId("imod")
                writer.writeUInt32((10 * (numMods + 1)).toLong()) // +1 for terminal
                for (mod in sample.modulators) {
                    writer.writeModulator(mod.srcOper, mod.destOper, mod.amount, mod.amtSrcOper, mod.transOper)
                }
                writer.writeModulator(0, 0, 0, 0, 0) // Terminal

                // igen - Instrument generators (SF2 spec: keyRange first, sampleID last)
                writer.writeChunkId("igen")
                writer.writeUInt32((4 * (generatorsPerZone + 1)).toLong())

                // 1. Key range (43) - MUST be first per SF2 spec
                writer.writeGeneratorRange(
                    Sf2Constants.GEN_KEY_RANGE,
                    sample.keyRangeStart,
                    sample.keyRangeEnd
                )
                // 2. Velocity range (44)
                writer.writeGeneratorRange(
                    Sf2Constants.GEN_VEL_RANGE,
                    sample.velRangeStart,
                    sample.velRangeEnd
                )
                // 3. Mod LFO to pitch (5)
                writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_PITCH, sample.modLfoToPitch)
                // 4. Vib LFO to pitch (6)
                writer.writeGenerator(Sf2Constants.GEN_VIB_LFO_TO_PITCH, sample.vibLfoToPitch)
                // 5. Mod Env to pitch (7)
                writer.writeGenerator(Sf2Constants.GEN_MOD_ENV_TO_PITCH, sample.modEnvToPitch)
                // 6. Filter cutoff (8) - SF2 native cents
                writer.writeGenerator(Sf2Constants.GEN_INITIAL_FILTER_FC, sample.filterFc)
                // 7. Filter resonance (9) - SF2 native centibels
                writer.writeGenerator(Sf2Constants.GEN_INITIAL_FILTER_Q, sample.filterQ)
                // 8. Mod LFO to filter (10)
                writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_FILTER_FC, sample.modLfoToFilterFc)
                // 9. Mod Env to filter (11)
                writer.writeGenerator(Sf2Constants.GEN_MOD_ENV_TO_FILTER_FC, sample.modEnvToFilterFc)
                // 10. Mod LFO to volume (13)
                writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_VOLUME, sample.modLfoToVolume)
                // 11. Chorus send (15)
                writer.writeGenerator(Sf2Constants.GEN_CHORUS_EFFECTS_SEND, sample.chorusSend)
                // 12. Reverb send (16)
                writer.writeGenerator(Sf2Constants.GEN_REVERB_EFFECTS_SEND, sample.reverbSend)
                // 13. Pan (17)
                writer.writeGenerator(Sf2Constants.GEN_PAN, sample.pan)
                // 14. Mod LFO delay (21) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_DELAY_MOD_LFO, sample.modLfoDelay)
                // 15. Mod LFO freq (22) - SF2 native cents
                writer.writeGenerator(Sf2Constants.GEN_FREQ_MOD_LFO, sample.modLfoFreq)
                // 16. Vib LFO delay (23) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_DELAY_VIB_LFO, sample.vibLfoDelay)
                // 17. Vib LFO freq (24) - SF2 native cents
                writer.writeGenerator(Sf2Constants.GEN_FREQ_VIB_LFO, sample.vibLfoFreq)
                // 18. Mod Env delay (25) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_DELAY_MOD_ENV, sample.modEnvDelay)
                // 19. Mod Env attack (26) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_ATTACK_MOD_ENV, sample.modEnvAttack)
                // 20. Mod Env hold (27) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_HOLD_MOD_ENV, sample.modEnvHold)
                // 21. Mod Env decay (28) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_DECAY_MOD_ENV, sample.modEnvDecay)
                // 22. Mod Env sustain (29) - SF2 native centibels
                writer.writeGenerator(Sf2Constants.GEN_SUSTAIN_MOD_ENV, sample.modEnvSustain)
                // 23. Mod Env release (30) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_RELEASE_MOD_ENV, sample.modEnvRelease)
                // 24. Vol Env delay (33) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_DELAY_VOL_ENV, sample.volEnvDelay)
                // 25. Vol Env attack (34) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_ATTACK_VOL_ENV, sample.volEnvAttack)
                // 26. Vol Env hold (35) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_HOLD_VOL_ENV, sample.volEnvHold)
                // 27. Vol Env decay (36) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_DECAY_VOL_ENV, sample.volEnvDecay)
                // 28. Vol Env sustain (37) - SF2 native centibels
                writer.writeGenerator(Sf2Constants.GEN_SUSTAIN_VOL_ENV, sample.volEnvSustain)
                // 29. Vol Env release (38) - SF2 native timecents
                writer.writeGenerator(Sf2Constants.GEN_RELEASE_VOL_ENV, sample.volEnvRelease)
                // 30. Initial attenuation (48)
                writer.writeGenerator(Sf2Constants.GEN_INITIAL_ATTENUATION, sample.attenuation)
                // 31. Coarse tune (51)
                writer.writeGenerator(Sf2Constants.GEN_COARSE_TUNE, sample.coarseTune)
                // 32. Fine tune (52)
                writer.writeGenerator(Sf2Constants.GEN_FINE_TUNE, sample.fineTuneCents)
                // 33. Sample modes (54) - use sampleModes if set, otherwise fallback to hasLoop
                val sampleMode = if (sample.sampleModes != 0) sample.sampleModes
                                 else if (sample.hasLoop) Sf2Constants.LOOP_MODE_CONTINUOUS
                                 else Sf2Constants.LOOP_MODE_NONE
                writer.writeGenerator(Sf2Constants.GEN_SAMPLE_MODES, sampleMode)
                // 34. Scale tuning (56)
                writer.writeGenerator(Sf2Constants.GEN_SCALE_TUNING, sample.scaleTuning)
                // 35. Exclusive class (57)
                writer.writeGenerator(Sf2Constants.GEN_EXCLUSIVE_CLASS, sample.exclusiveClass)
                // 36. Key to mod env hold (31)
                writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_HOLD, sample.keyToModEnvHold)
                // 37. Key to mod env decay (32)
                writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_DECAY, sample.keyToModEnvDecay)
                // 38. Key to vol env hold (39)
                writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_HOLD, sample.keyToVolEnvHold)
                // 39. Key to vol env decay (40)
                writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_DECAY, sample.keyToVolEnvDecay)
                // 40. Fixed key (46) - -1 = not fixed
                writer.writeGenerator(Sf2Constants.GEN_FIXED_KEY, sample.fixedKey)
                // 41. Fixed velocity (47) - -1 = not fixed
                writer.writeGenerator(Sf2Constants.GEN_FIXED_VELOCITY, sample.fixedVelocity)
                // 42. Overriding root key (58)
                writer.writeGenerator(Sf2Constants.GEN_OVERRIDING_ROOT_KEY, sample.rootNote)
                // 43. Sample ID (53) - MUST be last per SF2 spec
                writer.writeGenerator(Sf2Constants.GEN_SAMPLE_ID, 0)
                // Terminal
                writer.writeGenerator(0, 0)

                // shdr - Sample headers
                writer.writeChunkId("shdr")
                writer.writeUInt32((46 * 2).toLong()) // 1 sample + terminal

                // Loop points: use optimized zero-crossing positions
                // SF2 loopEnd is exclusive (first sample after the loop)
                val loopStart = if (optimizedLoop != null) {
                    optimizedLoop.start.toLong()
                } else {
                    0L
                }
                val loopEnd = if (optimizedLoop != null) {
                    (optimizedLoop.end + 1).toLong().coerceAtMost(endOffset)
                } else {
                    endOffset
                }

                writer.writeSampleHeader(
                    name = sample.name.take(19),
                    start = startOffset,
                    end = endOffset,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                    sampleRate = sample.sampleRate,
                    originalPitch = sample.rootNote,
                    pitchCorrection = sample.pitchCorrection,
                    sampleLink = 0,
                    sampleType = Sf2ChunkWriter.SAMPLE_TYPE_MONO
                )
                // Terminal sample header
                writer.writeSampleHeader(
                    name = "EOS",
                    start = 0, end = 0, loopStart = 0, loopEnd = 0,
                    sampleRate = 0, originalPitch = 0, pitchCorrection = 0,
                    sampleLink = 0, sampleType = 0
                )

                // Save end position before seeking back
                val fileEndPos = writer.getPosition()

                // Update pdta size: "pdta" (4) + all subchunks
                val pdtaSize = fileEndPos - pdtaListStart - 8
                writer.seek(pdtaSizePos)
                writer.writeUInt32(pdtaSize)

                // Update RIFF size (total file size - 8 bytes for "RIFF" + size field)
                writer.seek(riffSizePos)
                writer.writeUInt32(fileEndPos - 8)

                Log.d(TAG, "SF2 file (from memory) written: ${outputFile.absolutePath}")
                Log.d(TAG, "Sample: ${sample.samples.size} samples, loop=${sample.hasLoop} ($loopStart-$loopEnd)")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing SF2 from memory", e)
            outputFile.delete()
            return false
        }
    }

    /**
     * Create an SF2 file with multiple in-memory samples.
     * No WAV files required - samples are provided directly as ShortArrays.
     *
     * @param outputFile The output SF2 file
     * @param instrumentName Name of the instrument (max 20 chars)
     * @param samples List of in-memory samples with their parameters
     * @param presetNumber MIDI preset number (0-127)
     * @param bankNumber MIDI bank number (0-127)
     * @return true if successful, false otherwise
     */
    fun writeSf2FromMemorySamples(
        outputFile: File,
        instrumentName: String,
        samples: List<InMemorySample>,
        presetNumber: Int = 0,
        bankNumber: Int = 0,
        instrumentGlobalModulators: List<InMemoryModulator> = emptyList(),
        instrumentGlobalParams: InstrumentGlobalParams? = null,
        presetModulators: List<InMemoryModulator> = emptyList(),
        presetGlobalParams: PresetZoneParams? = null
    ): Boolean {
        if (samples.isEmpty()) {
            Log.e(TAG, "No samples provided")
            return false
        }

        // If only one sample, delegate to the single-sample method
        if (samples.size == 1) {
            return writeSf2FromMemory(outputFile, instrumentName, samples.first(), presetNumber, bankNumber)
        }

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0) // Clear file
                val writer = Sf2ChunkWriter(raf)

                // Pre-calculate optimized loop points (lightweight metadata only, no audio processing)
                val optimizedLoops = samples.map { sample ->
                    if (sample.hasLoop && sample.loopEnd > sample.loopStart) {
                        snapLoopToZeroCrossings(sample.samples, sample.loopStart, sample.loopEnd)
                    } else {
                        null
                    }
                }

                // Write RIFF header (placeholder size)
                writer.writeChunkId("RIFF")
                val riffSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("sfbk")

                // Write LIST 'INFO'
                writer.writeChunkId("LIST")
                val infoSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("INFO")
                val infoContentStart = writer.getPosition()
                writer.writeInfoChunk(instrumentName.take(19), "Created with A2U SF2 Creator")
                val infoSize = writer.getPosition() - infoContentStart + 4 // +4 for 'INFO'
                val afterInfo = writer.getPosition()
                writer.seek(infoSizePos)
                writer.writeUInt32(infoSize)
                writer.seek(afterInfo)

                // Write LIST 'sdta'
                writer.writeChunkId("LIST")
                val sdtaSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("sdta")

                // Write smpl chunk - process and write samples one at a time to save memory
                writer.writeChunkId("smpl")
                val smplSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                val smplDataStart = writer.getPosition()

                // Write all sample data and track offsets
                val sampleOffsets = mutableListOf<Pair<Long, Long>>() // start, end positions
                val paddingBuffer = ShortArray(Sf2Constants.SAMPLE_PADDING)

                for ((index, sample) in samples.withIndex()) {
                    val optimizedLoop = optimizedLoops[index]
                    val startOffset = (writer.getPosition() - smplDataStart) / 2 // In samples

                    // Process audio on-demand: copy and apply crossfade in-place
                    if (sample.hasLoop && optimizedLoop != null) {
                        val processedAudio = sample.samples.copyOf()
                        val crossfader = Crossfader(sample.sampleRate)
                        crossfader.applyCrossfadeLoopInPlace(
                            processedAudio,
                            optimizedLoop.start,
                            optimizedLoop.end,
                            crossfadeMs = Sf2Constants.LOOP_CROSSFADE_MS,
                            curveType = Crossfader.CurveType.EQUAL_POWER
                        )
                        writer.writeSamples(processedAudio)
                        // processedAudio goes out of scope here and can be GC'd
                    } else {
                        // No loop processing needed - write original samples directly
                        writer.writeSamples(sample.samples)
                    }

                    // Add 46 zero samples padding
                    writer.writeSamples(paddingBuffer)
                    val endOffset = (writer.getPosition() - smplDataStart) / 2 - Sf2Constants.SAMPLE_PADDING
                    sampleOffsets.add(Pair(startOffset, endOffset))
                }

                val smplSize = writer.getPosition() - smplDataStart
                writer.seek(smplSizePos)
                writer.writeUInt32(smplSize)
                writer.seek(smplDataStart + smplSize)

                val sdtaSize = writer.getPosition() - sdtaSizePos - 4
                writer.seek(sdtaSizePos)
                writer.writeUInt32(sdtaSize)
                writer.seek(sdtaSizePos + 4 + sdtaSize)

                // Write LIST 'pdta'
                val pdtaListStart = writer.getPosition()
                writer.writeChunkId("LIST")
                val pdtaSizePos = writer.getPosition()
                writer.writeUInt32(0) // Placeholder
                writer.writeChunkId("pdta")

                // Determine if we have a global preset zone
                val presetGlobalGens = presetGlobalParams?.buildGenerators() ?: emptyList()
                val hasPresetGlobalZone = presetGlobalGens.isNotEmpty() || presetModulators.isNotEmpty()
                val numPresetZones = (if (hasPresetGlobalZone) 1 else 0) + 1 // global zone (optional) + instrument zone

                // phdr - Preset headers
                writer.writeChunkId("phdr")
                writer.writeUInt32((38 * 2).toLong()) // 1 preset + 1 terminal
                writer.writePresetHeader(instrumentName.take(19), presetNumber, bankNumber, 0)
                writer.writePresetHeader("EOP", 0, 0, numPresetZones) // Terminal

                // pbag - Preset bags
                writer.writeChunkId("pbag")
                writer.writeUInt32((4 * (numPresetZones + 1)).toLong())
                var pgenIndex = 0
                var pmodIndex = 0
                if (hasPresetGlobalZone) {
                    writer.writeBag(pgenIndex, pmodIndex)
                    pgenIndex += presetGlobalGens.size
                    pmodIndex += presetModulators.size
                }
                writer.writeBag(pgenIndex, pmodIndex) // Instrument zone
                pgenIndex += 1 // GEN_INSTRUMENT
                writer.writeBag(pgenIndex, pmodIndex) // Terminal

                // pmod - Preset modulators
                val pmodCount = presetModulators.size + 1 // +1 for terminal
                writer.writeChunkId("pmod")
                writer.writeUInt32((10 * pmodCount).toLong())
                for (mod in presetModulators) {
                    writer.writeModulator(mod.srcOper, mod.destOper, mod.amount, mod.amtSrcOper, mod.transOper)
                }
                writer.writeModulator(0, 0, 0, 0, 0) // Terminal

                // pgen - Preset generators
                val pgenCount = presetGlobalGens.size + 1 + 1 // global gens + GEN_INSTRUMENT + terminal
                writer.writeChunkId("pgen")
                writer.writeUInt32((4 * pgenCount).toLong())
                // Global zone generators (if any)
                for ((genId, value) in presetGlobalGens) {
                    writer.writeGenerator(genId, value)
                }
                // Instrument zone - just GEN_INSTRUMENT
                writer.writeGenerator(Sf2ChunkWriter.GEN_INSTRUMENT, 0)
                writer.writeGenerator(0, 0) // Terminal

                // Determine if we have a global instrument zone
                val instGlobalGens = instrumentGlobalParams?.buildGenerators() ?: emptyList()
                val hasInstGlobalZone = instGlobalGens.isNotEmpty() || instrumentGlobalModulators.isNotEmpty()
                val numInstZones = (if (hasInstGlobalZone) 1 else 0) + samples.size

                // inst - Instrument headers
                writer.writeChunkId("inst")
                writer.writeUInt32((22 * 2).toLong())
                writer.writeInstrumentHeader(instrumentName.take(19), 0)
                writer.writeInstrumentHeader("EOI", numInstZones) // Terminal

                // ibag - Instrument bags
                val generatorsPerZone = 43 // 37 original + 6 new (keynum-to-env + fixed key/vel)
                writer.writeChunkId("ibag")
                val numIBags = numInstZones + 1
                writer.writeUInt32((4 * numIBags).toLong())
                var igenIndex = 0
                var imodIndex = 0
                // Global zone first (if exists)
                if (hasInstGlobalZone) {
                    writer.writeBag(igenIndex, imodIndex)
                    igenIndex += instGlobalGens.size
                    imodIndex += instrumentGlobalModulators.size
                }
                // Sample zones
                for (i in samples.indices) {
                    writer.writeBag(igenIndex, imodIndex)
                    igenIndex += generatorsPerZone
                    imodIndex += samples[i].modulators.size
                }
                writer.writeBag(igenIndex, imodIndex) // Terminal

                // imod - Instrument modulators
                val totalMods = instrumentGlobalModulators.size + samples.sumOf { it.modulators.size } + 1
                writer.writeChunkId("imod")
                writer.writeUInt32((10 * totalMods).toLong())
                // Global zone modulators first
                for (mod in instrumentGlobalModulators) {
                    writer.writeModulator(mod.srcOper, mod.destOper, mod.amount, mod.amtSrcOper, mod.transOper)
                }
                // Sample zone modulators
                for (sample in samples) {
                    for (mod in sample.modulators) {
                        writer.writeModulator(mod.srcOper, mod.destOper, mod.amount, mod.amtSrcOper, mod.transOper)
                    }
                }
                writer.writeModulator(0, 0, 0, 0, 0) // Terminal

                // igen - Instrument generators
                val numIGens = instGlobalGens.size + samples.size * generatorsPerZone + 1
                writer.writeChunkId("igen")
                writer.writeUInt32((4 * numIGens).toLong())

                // Global zone generators first (no GEN_SAMPLE_ID)
                for ((genId, value) in instGlobalGens) {
                    writer.writeGenerator(genId, value)
                }

                // Sample zone generators
                for (i in samples.indices) {
                    val sample = samples[i]

                    // 1. Key range (43) - MUST be first per SF2 spec
                    writer.writeGeneratorRange(
                        Sf2Constants.GEN_KEY_RANGE,
                        sample.keyRangeStart,
                        sample.keyRangeEnd
                    )
                    // 2. Velocity range (44)
                    writer.writeGeneratorRange(
                        Sf2Constants.GEN_VEL_RANGE,
                        sample.velRangeStart,
                        sample.velRangeEnd
                    )
                    // 3. Mod LFO to pitch (5)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_PITCH, sample.modLfoToPitch)
                    // 4. Vib LFO to pitch (6)
                    writer.writeGenerator(Sf2Constants.GEN_VIB_LFO_TO_PITCH, sample.vibLfoToPitch)
                    // 5. Mod Env to pitch (7)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_ENV_TO_PITCH, sample.modEnvToPitch)
                    // 6. Filter cutoff (8) - SF2 native cents
                    writer.writeGenerator(Sf2Constants.GEN_INITIAL_FILTER_FC, sample.filterFc)
                    // 7. Filter resonance (9) - SF2 native centibels
                    writer.writeGenerator(Sf2Constants.GEN_INITIAL_FILTER_Q, sample.filterQ)
                    // 8. Mod LFO to filter (10)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_FILTER_FC, sample.modLfoToFilterFc)
                    // 9. Mod Env to filter (11)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_ENV_TO_FILTER_FC, sample.modEnvToFilterFc)
                    // 10. Mod LFO to volume (13)
                    writer.writeGenerator(Sf2Constants.GEN_MOD_LFO_TO_VOLUME, sample.modLfoToVolume)
                    // 11. Chorus send (15)
                    writer.writeGenerator(Sf2Constants.GEN_CHORUS_EFFECTS_SEND, sample.chorusSend)
                    // 12. Reverb send (16)
                    writer.writeGenerator(Sf2Constants.GEN_REVERB_EFFECTS_SEND, sample.reverbSend)
                    // 13. Pan (17)
                    writer.writeGenerator(Sf2Constants.GEN_PAN, sample.pan)
                    // 14. Mod LFO delay (21) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_MOD_LFO, sample.modLfoDelay)
                    // 15. Mod LFO freq (22) - SF2 native cents
                    writer.writeGenerator(Sf2Constants.GEN_FREQ_MOD_LFO, sample.modLfoFreq)
                    // 16. Vib LFO delay (23) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_VIB_LFO, sample.vibLfoDelay)
                    // 17. Vib LFO freq (24) - SF2 native cents
                    writer.writeGenerator(Sf2Constants.GEN_FREQ_VIB_LFO, sample.vibLfoFreq)
                    // 18. Mod Env delay (25) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_MOD_ENV, sample.modEnvDelay)
                    // 19. Mod Env attack (26) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_ATTACK_MOD_ENV, sample.modEnvAttack)
                    // 20. Mod Env hold (27) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_HOLD_MOD_ENV, sample.modEnvHold)
                    // 21. Mod Env decay (28) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_DECAY_MOD_ENV, sample.modEnvDecay)
                    // 22. Mod Env sustain (29) - SF2 native centibels
                    writer.writeGenerator(Sf2Constants.GEN_SUSTAIN_MOD_ENV, sample.modEnvSustain)
                    // 23. Mod Env release (30) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_RELEASE_MOD_ENV, sample.modEnvRelease)
                    // 24. Vol Env delay (33) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_DELAY_VOL_ENV, sample.volEnvDelay)
                    // 25. Vol Env attack (34) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_ATTACK_VOL_ENV, sample.volEnvAttack)
                    // 26. Vol Env hold (35) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_HOLD_VOL_ENV, sample.volEnvHold)
                    // 27. Vol Env decay (36) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_DECAY_VOL_ENV, sample.volEnvDecay)
                    // 28. Vol Env sustain (37) - SF2 native centibels
                    writer.writeGenerator(Sf2Constants.GEN_SUSTAIN_VOL_ENV, sample.volEnvSustain)
                    // 29. Vol Env release (38) - SF2 native timecents
                    writer.writeGenerator(Sf2Constants.GEN_RELEASE_VOL_ENV, sample.volEnvRelease)
                    // 30. Initial attenuation (48)
                    writer.writeGenerator(Sf2Constants.GEN_INITIAL_ATTENUATION, sample.attenuation)
                    // 31. Coarse tune (51)
                    writer.writeGenerator(Sf2Constants.GEN_COARSE_TUNE, sample.coarseTune)
                    // 32. Fine tune (52)
                    writer.writeGenerator(Sf2Constants.GEN_FINE_TUNE, sample.fineTuneCents)
                    // 33. Sample modes (54) - use sampleModes if set, otherwise fallback to hasLoop
                    val sampleMode = if (sample.sampleModes != 0) sample.sampleModes
                                     else if (sample.hasLoop) Sf2Constants.LOOP_MODE_CONTINUOUS
                                     else Sf2Constants.LOOP_MODE_NONE
                    writer.writeGenerator(Sf2Constants.GEN_SAMPLE_MODES, sampleMode)
                    // 34. Scale tuning (56)
                    writer.writeGenerator(Sf2Constants.GEN_SCALE_TUNING, sample.scaleTuning)
                    // 35. Exclusive class (57)
                    writer.writeGenerator(Sf2Constants.GEN_EXCLUSIVE_CLASS, sample.exclusiveClass)
                    // 36. Key to mod env hold (31)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_HOLD, sample.keyToModEnvHold)
                    // 37. Key to mod env decay (32)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_DECAY, sample.keyToModEnvDecay)
                    // 38. Key to vol env hold (39)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_HOLD, sample.keyToVolEnvHold)
                    // 39. Key to vol env decay (40)
                    writer.writeGenerator(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_DECAY, sample.keyToVolEnvDecay)
                    // 40. Fixed key (46) - -1 = not fixed
                    writer.writeGenerator(Sf2Constants.GEN_FIXED_KEY, sample.fixedKey)
                    // 41. Fixed velocity (47) - -1 = not fixed
                    writer.writeGenerator(Sf2Constants.GEN_FIXED_VELOCITY, sample.fixedVelocity)
                    // 42. Overriding root key (58)
                    writer.writeGenerator(Sf2Constants.GEN_OVERRIDING_ROOT_KEY, sample.rootNote)
                    // 43. Sample ID (53) - MUST be last per SF2 spec
                    writer.writeGenerator(Sf2Constants.GEN_SAMPLE_ID, i)
                }
                // Terminal
                writer.writeGenerator(0, 0)

                // shdr - Sample headers
                writer.writeChunkId("shdr")
                writer.writeUInt32((46 * (samples.size + 1)).toLong())

                for (i in samples.indices) {
                    val sample = samples[i]
                    val (startOffset, endOffset) = sampleOffsets[i]
                    val optimizedLoop = optimizedLoops[i]

                    val loopStart = if (sample.hasLoop && optimizedLoop != null) {
                        startOffset + optimizedLoop.start
                    } else if (sample.hasLoop) {
                        startOffset + sample.loopStart
                    } else {
                        startOffset
                    }
                    val loopEnd = if (sample.hasLoop && optimizedLoop != null) {
                        (startOffset + optimizedLoop.end + 1).coerceAtMost(endOffset)
                    } else if (sample.hasLoop) {
                        (startOffset + sample.loopEnd + 1).coerceAtMost(endOffset)
                    } else {
                        endOffset
                    }

                    writer.writeSampleHeader(
                        name = sample.name.take(19),
                        start = startOffset,
                        end = endOffset,
                        loopStart = loopStart,
                        loopEnd = loopEnd,
                        sampleRate = sample.sampleRate,
                        originalPitch = sample.rootNote,
                        pitchCorrection = sample.pitchCorrection,
                        sampleLink = 0,
                        sampleType = Sf2ChunkWriter.SAMPLE_TYPE_MONO
                    )
                }
                // Terminal sample header
                writer.writeSampleHeader(
                    name = "EOS",
                    start = 0, end = 0, loopStart = 0, loopEnd = 0,
                    sampleRate = 0, originalPitch = 0, pitchCorrection = 0,
                    sampleLink = 0, sampleType = 0
                )

                // Save end position
                val fileEndPos = writer.getPosition()

                // Update pdta size
                val pdtaSize = fileEndPos - pdtaListStart - 8
                writer.seek(pdtaSizePos)
                writer.writeUInt32(pdtaSize)

                // Update RIFF size
                writer.seek(riffSizePos)
                writer.writeUInt32(fileEndPos - 8)

                Log.d(TAG, "SF2 file (multi-sample) written: ${outputFile.absolutePath}")
                Log.d(TAG, "Total samples: ${samples.size}, size: $fileEndPos bytes")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing multi-sample SF2", e)
            outputFile.delete()
            return false
        }
    }

    /**
     * Loop of a sample recorded in the app, as written in every SF2 this app creates: loop
     * points moved to matching zero crossings and an equal-power crossfade into the loop end.
     * Returns the frames to write and the loop points (end exclusive) for the sample header.
     */
    fun prepareRecordedLoop(audio: ShortArray, sampleRate: Int, loopStart: Int, loopEnd: Int): Sf2Document.LoadedPcm {
        if (loopEnd <= loopStart || loopEnd >= audio.size) return Sf2Document.LoadedPcm(audio)
        val loop = snapLoopToZeroCrossings(audio, loopStart, loopEnd)
        val processed = audio.copyOf()
        Crossfader(sampleRate).applyCrossfadeLoopInPlace(
            processed,
            loop.start,
            loop.end,
            crossfadeMs = Sf2Constants.LOOP_CROSSFADE_MS,
            curveType = Crossfader.CurveType.EQUAL_POWER
        )
        return Sf2Document.LoadedPcm(processed, loop.start.toLong(), minOf(loop.end + 1L, audio.size.toLong()))
    }

    /**
     * Load PCM audio data from a WAV file.
     * Delegates to WavUtils for the actual file I/O.
     */
    private fun loadSampleAudio(wavFile: File): ShortArray {
        val samples = WavUtils.loadWavFile(wavFile)
        if (samples == null) {
            Log.e(TAG, "Failed to load WAV file: ${wavFile.absolutePath}")
            return ShortArray(0)
        }
        return samples
    }

    /**
     * Validate that an SF2 file was written correctly by checking its header.
     */
    fun validateSf2(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Read RIFF header
                val riff = ByteArray(4)
                raf.read(riff)
                if (String(riff) != "RIFF") return false

                // Skip size
                raf.skipBytes(4)

                // Read format
                val format = ByteArray(4)
                raf.read(format)
                return String(format) == "sfbk"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating SF2 file", e)
            return false
        }
    }
}
