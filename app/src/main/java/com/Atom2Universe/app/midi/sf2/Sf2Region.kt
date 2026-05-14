package com.Atom2Universe.app.midi.sf2

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Represents a finalized region that maps a range of keys/velocities to a sample.
 * This is the result of merging preset and instrument zone generators.
 */
data class Sf2Region(
    val keyRange: IntRange,             // MIDI key range (0-127)
    val velRange: IntRange,             // Velocity range (0-127)
    val sampleId: Int,                  // Index into sample headers
    val sampleRate: Int,                // Sample rate in Hz
    val sampleStart: Long,              // Start offset in sample data
    val sampleEnd: Long,                // End offset in sample data
    val loopStart: Long,                // Loop start offset
    val loopEnd: Long,                  // Loop end offset
    val hasLoop: Boolean,               // Whether looping is enabled
    val rootKey: Int,                   // MIDI note of the sample's original pitch
    val coarseTune: Int,                // Coarse tuning in semitones
    val fineTune: Int,                  // Fine tuning in cents
    val scaleTuning: Int,               // Scale tuning (cents per semitone, usually 100)
    val pitchCorrection: Int,           // Sample pitch correction in cents
    val attenuation: Int,               // Initial attenuation in centibels
    val pan: Float,                     // Pan position (-1.0 = left, 0.0 = center, 1.0 = right)
    val exclusiveClass: Int,            // Exclusive class for drum instruments
    val reverbSend: Float?,             // Reverb send amount (0.0 - 1.0)
    val chorusSend: Float?,             // Chorus send amount (0.0 - 1.0)
    val volumeEnvelope: VolumeEnvelope?, // ADSR envelope parameters
    val sampleName: String,             // Name of the sample
    val filterFc: Int?,                 // Initial filter cutoff in absolute cents (generator 8)
    val filterQ: Int?,                  // Initial filter Q/resonance in centibels (generator 9)
    // Vibrato LFO parameters
    val vibLfo: LfoParameters?,         // Vibrato LFO (pitch modulation only)
    // Modulation LFO parameters
    val modLfo: LfoParameters?,         // Mod LFO (pitch, filter, volume modulation)
    // Modulation Envelope parameters
    val modEnvelope: VolumeEnvelope?,   // Mod envelope (DAHDSR like volume envelope)
    val modEnvToPitch: Int?,            // Mod envelope to pitch in cents
    val modEnvToFilterFc: Int?,         // Mod envelope to filter cutoff in cents
    // Force key/velocity generators (SF2 generators 46, 47)
    val forcedKeyNum: Int?,             // Forces MIDI note for pitch calculation (for sound effects)
    val forcedVelocity: Int?            // Forces velocity value (for fixed-velocity samples)
) {
    /**
     * Checks if this region matches the given key and velocity
     */
    fun matches(key: Int, velocity: Int): Boolean {
        return key in keyRange && velocity in velRange
    }

    /**
     * Calculates the playback rate for a given MIDI note.
     * Formula: playbackRate = 2^(totalCents/1200) * (sampleRate / outputSampleRate)
     *
     * If forcedKeyNum is set, uses that instead of midiNote for pitch calculation.
     */
    fun calculatePlaybackRate(midiNote: Int, outputSampleRate: Int): Double {
        // Safety check: use default sample rate if invalid
        val effectiveSampleRate = if (sampleRate <= 0) 44100 else sampleRate

        // Use forced key number if specified (for sound effects, percussion)
        val effectiveNote = forcedKeyNum ?: midiNote

        // Calculate cents from key difference, scaled by scaleTuning
        val centsFromKey = (effectiveNote - rootKey) * scaleTuning
        // Add tuning adjustments
        val coarseCents = coarseTune * 100
        val totalCents = centsFromKey + coarseCents + fineTune + pitchCorrection
        // Calculate base rate from sample rate ratio
        val baseRate = effectiveSampleRate.toDouble() / outputSampleRate
        // Apply pitch shift
        val rate = 2.0.pow(totalCents / 1200.0) * baseRate

        // Clamp to reasonable range to prevent extreme pitch shifts causing issues
        return rate.coerceIn(0.05, 20.0)
    }

    /**
     * Calculates the gain from attenuation.
     * Attenuation is in centibels (1/10th of a decibel).
     * gain = 10^(-attenuation/200)
     *
     * BUG FIX: Limite l'atténuation à 120 cB max pour éviter les presets muets
     */
    fun calculateGain(): Float {
        if (attenuation <= 0) return 1.0f
        // Limite à 120 cB (12 dB) pour préserver le caractère des SF2
        // Maintenant que le limiter est corrigé, on peut permettre plus d'atténuation
        val limitedAttenuation = attenuation.coerceAtMost(120)
        return 10.0.pow(-limitedAttenuation / 200.0).toFloat()
    }

    /**
     * Returns the sample length in samples
     */
    val sampleLength: Long
        get() = max(1, sampleEnd - sampleStart)

    /**
     * Returns the loop length in samples
     */
    val loopLength: Long
        get() = if (hasLoop) max(1, loopEnd - loopStart) else 0

    companion object {
        /**
         * Checks if a zone has an extreme rootKey offset that would produce
         * unusable playback rates. Returns true if the zone should be skipped.
         *
         * Note: Many SF2 files intentionally stretch samples across wide ranges.
         * We only filter truly extreme cases that would sound broken.
         */
        private fun hasExtremeRootKeyOffset(keyRange: IntRange, rootKey: Int, sampleName: String): Boolean {
            val keyRangeCenter = (keyRange.first + keyRange.last) / 2
            val semitoneOffset = kotlin.math.abs(keyRangeCenter - rootKey)

            // Only filter extremely stretched samples (>3 octaves with extreme rates)
            if (semitoneOffset > 36) { // More than 3 octaves
                val estimatedRate = 2.0.pow((keyRangeCenter - rootKey) / 12.0)
                if (estimatedRate < 0.05 || estimatedRate > 20.0) {
                    return true
                }
            }
            return false
        }

        /**
         * Creates a region from finalized zone data
         */
        fun fromZoneData(
            zone: Sf2ZoneData,
            sampleData: ShortArray,
            sampleHeaders: List<Sf2SampleHeader>
        ): Sf2Region? {
            val sampleId = zone.sampleId ?: return null
            val sampleHeader = zone.sampleHeader ?: sampleHeaders.getOrNull(sampleId) ?: return null

            val dataLength = sampleData.size.toLong()

            // Calculate sample boundaries with offsets
            val start = clampIndex(
                sampleHeader.start + zone.startOffset,
                0,
                dataLength - 1
            )
            val endRaw = sampleHeader.end + zone.endOffset
            val end = clampIndex(endRaw, start + 1, dataLength)

            // Calculate loop boundaries with offsets
            val loopStartRaw = sampleHeader.startLoop + zone.startLoopOffset
            val loopEndRaw = sampleHeader.endLoop + zone.endLoopOffset
            val loopStart = clampIndex(loopStartRaw, start, end)
            val loopEnd = clampIndex(loopEndRaw, loopStart + 1, end)

            // Determine root key (override or sample default)
            val rootKey = zone.rootKey ?: sampleHeader.originalPitch

            // Skip zones with extreme pitch offsets (corrupt or unusual SF2 data)
            if (hasExtremeRootKeyOffset(zone.keyRange, rootKey, sampleHeader.name)) {
                return null
            }

            // Convert envelope data
            val envelope = convertEnvelope(zone.volumeEnvelope)

            // Check if loop is valid and enabled
            val hasLoop = SampleModes.hasLoop(zone.sampleModes) && loopEnd > loopStart + 7

            return Sf2Region(
                keyRange = zone.keyRange,
                velRange = zone.velRange,
                sampleId = sampleId,
                sampleRate = sampleHeader.sampleRate.takeIf { it > 0 } ?: 44100,
                sampleStart = start,
                sampleEnd = end,
                loopStart = loopStart,
                loopEnd = loopEnd,
                hasLoop = hasLoop,
                rootKey = rootKey,
                coarseTune = zone.coarseTune,
                fineTune = zone.fineTune,
                scaleTuning = zone.scaleTuning.takeIf { it in 0..100 } ?: 100,
                pitchCorrection = sampleHeader.pitchCorrection,
                attenuation = zone.attenuation,
                pan = zone.pan ?: 0f,
                exclusiveClass = zone.exclusiveClass,
                reverbSend = zone.reverbSend,
                chorusSend = zone.chorusSend,
                volumeEnvelope = envelope,
                sampleName = sampleHeader.name,
                filterFc = zone.filterFc,
                filterQ = zone.filterQ,
                vibLfo = createVibLfoParams(zone),
                modLfo = createModLfoParams(zone),
                modEnvelope = convertEnvelope(zone.modEnvelope),
                modEnvToPitch = zone.modEnvToPitch,
                modEnvToFilterFc = zone.modEnvToFilterFc,
                forcedKeyNum = zone.forcedKeyNum,
                forcedVelocity = zone.forcedVelocity
            )
        }

        private fun clampIndex(value: Long, minVal: Long, maxVal: Long): Long {
            return max(minVal, min(maxVal, value))
        }

        /**
         * Creates Vibrato LFO parameters from zone data.
         * Returns null if no vibrato effect is defined.
         */
        private fun createVibLfoParams(zone: Sf2ZoneData): LfoParameters? {
            // Only create if there's a pitch modulation depth
            if (zone.vibLfoToPitch == null || zone.vibLfoToPitch == 0) {
                return null
            }
            return LfoParameters(
                delay = zone.vibLfoDelay,
                frequency = zone.vibLfoFreq,
                toPitch = zone.vibLfoToPitch,
                toFilterFc = null,
                toVolume = null
            )
        }

        /**
         * Creates Modulation LFO parameters from zone data.
         * Returns null if no modulation effect is defined.
         */
        private fun createModLfoParams(zone: Sf2ZoneData): LfoParameters? {
            // Only create if there's any modulation depth
            val hasPitch = zone.modLfoToPitch != null && zone.modLfoToPitch != 0
            val hasFilter = zone.modLfoToFilterFc != null && zone.modLfoToFilterFc != 0
            val hasVolume = zone.modLfoToVolume != null && zone.modLfoToVolume != 0

            if (!hasPitch && !hasFilter && !hasVolume) {
                return null
            }
            return LfoParameters(
                delay = zone.modLfoDelay,
                frequency = zone.modLfoFreq,
                toPitch = zone.modLfoToPitch,
                toFilterFc = zone.modLfoToFilterFc,
                toVolume = zone.modLfoToVolume
            )
        }

        /**
         * Converts SF2 envelope data (in timecents) to seconds
         */
        private fun convertEnvelope(data: Sf2EnvelopeData): VolumeEnvelope? {
            var hasData = false

            val attack = data.attack?.let {
                hasData = true
                max(0.001f, timecentsToSeconds(it))
            }

            val decay = data.decay?.let {
                hasData = true
                max(0.01f, timecentsToSeconds(it))
            }

            val release = data.release?.let {
                hasData = true
                max(0.02f, timecentsToSeconds(it))
            }

            // Sustain is in centibels attenuation, convert to linear level
            val sustain = data.sustain?.let {
                hasData = true
                val level = 10.0.pow(-it / 200.0).toFloat()
                // Plafonnement sustain : min 1% (au lieu de 10%) pour respecter les SF2 avec sustain faible
                val clampedLevel = max(0.01f, min(1f, level))
                clampedLevel
            }

            val delay = data.delay?.let {
                hasData = true
                max(0f, timecentsToSeconds(it))
            }

            val hold = data.hold?.let {
                hasData = true
                max(0f, timecentsToSeconds(it))
            }

            // Key tracking values (keep in timecents, applied at runtime based on MIDI note)
            val keynumToHold = data.keynumToHold ?: 0
            val keynumToDecay = data.keynumToDecay ?: 0
            if (keynumToHold != 0 || keynumToDecay != 0) {
                hasData = true
            }

            return if (hasData) {
                VolumeEnvelope(
                    delay = delay ?: 0f,
                    attack = attack ?: 0.001f,
                    hold = hold ?: 0f,
                    decay = decay ?: 0.01f,
                    sustain = sustain ?: 1f,
                    release = release ?: 0.02f,
                    keynumToHold = keynumToHold,
                    keynumToDecay = keynumToDecay
                )
            } else null
        }

        /**
         * Converts timecents to seconds: seconds = 2^(timecents/1200)
         */
        private fun timecentsToSeconds(timecents: Int): Float {
            return 2.0.pow(timecents / 1200.0).toFloat()
        }

        /**
         * Creates a region from finalized zone data using SparseSampleData.
         * This version is used for streaming/selective loading where only
         * specific samples are loaded into memory.
         */
        fun fromZoneDataSparse(
            zone: Sf2ZoneData,
            sampleData: SparseSampleData,
            sampleHeaders: List<Sf2SampleHeader>
        ): Sf2Region? {
            val sampleId = zone.sampleId ?: return null
            val sampleHeader = zone.sampleHeader ?: sampleHeaders.getOrNull(sampleId) ?: return null

            // For sparse data, we don't have the total data length upfront
            // We use the sample header's end position as the upper bound
            val headerEnd = sampleHeader.end

            // Calculate sample boundaries with offsets
            val start = max(0L, sampleHeader.start + zone.startOffset)
            val endRaw = sampleHeader.end + zone.endOffset
            val end = max(start + 1, endRaw)

            // Calculate loop boundaries with offsets
            val loopStartRaw = sampleHeader.startLoop + zone.startLoopOffset
            val loopEndRaw = sampleHeader.endLoop + zone.endLoopOffset
            val loopStart = max(start, loopStartRaw)
            val loopEnd = max(loopStart + 1, minOf(loopEndRaw, end))

            // Determine root key (override or sample default)
            val rootKey = zone.rootKey ?: sampleHeader.originalPitch

            // Skip zones with extreme pitch offsets (corrupt or unusual SF2 data)
            if (hasExtremeRootKeyOffset(zone.keyRange, rootKey, sampleHeader.name)) {
                return null
            }

            // Convert envelope data
            val envelope = convertEnvelope(zone.volumeEnvelope)

            // Check if loop is valid and enabled
            val hasLoop = SampleModes.hasLoop(zone.sampleModes) && loopEnd > loopStart + 7

            return Sf2Region(
                keyRange = zone.keyRange,
                velRange = zone.velRange,
                sampleId = sampleId,
                sampleRate = sampleHeader.sampleRate.takeIf { it > 0 } ?: 44100,
                sampleStart = start,
                sampleEnd = end,
                loopStart = loopStart,
                loopEnd = loopEnd,
                hasLoop = hasLoop,
                rootKey = rootKey,
                coarseTune = zone.coarseTune,
                fineTune = zone.fineTune,
                scaleTuning = zone.scaleTuning.takeIf { it in 0..100 } ?: 100,
                pitchCorrection = sampleHeader.pitchCorrection,
                attenuation = zone.attenuation,
                pan = zone.pan ?: 0f,
                exclusiveClass = zone.exclusiveClass,
                reverbSend = zone.reverbSend,
                chorusSend = zone.chorusSend,
                volumeEnvelope = envelope,
                sampleName = sampleHeader.name,
                filterFc = zone.filterFc,
                filterQ = zone.filterQ,
                vibLfo = createVibLfoParams(zone),
                modLfo = createModLfoParams(zone),
                modEnvelope = convertEnvelope(zone.modEnvelope),
                modEnvToPitch = zone.modEnvToPitch,
                modEnvToFilterFc = zone.modEnvToFilterFc,
                forcedKeyNum = zone.forcedKeyNum,
                forcedVelocity = zone.forcedVelocity
            )
        }

        /**
         * Creates a region for memory-mapped mode.
         * Similar to fromZoneDataSparse but doesn't need sample data array
         * since samples will be read directly from the mmap'd file.
         */
        fun fromZoneDataMmap(
            zone: Sf2ZoneData,
            sampleHeaders: List<Sf2SampleHeader>
        ): Sf2Region? {
            val sampleId = zone.sampleId ?: return null
            val sampleHeader = zone.sampleHeader ?: sampleHeaders.getOrNull(sampleId) ?: return null

            // Calculate sample boundaries with offsets (using original file positions)
            val start = max(0L, sampleHeader.start + zone.startOffset)
            val endRaw = sampleHeader.end + zone.endOffset
            val end = max(start + 1, endRaw)

            // Calculate loop boundaries with offsets
            val loopStartRaw = sampleHeader.startLoop + zone.startLoopOffset
            val loopEndRaw = sampleHeader.endLoop + zone.endLoopOffset
            val loopStart = max(start, loopStartRaw)
            val loopEnd = max(loopStart + 1, minOf(loopEndRaw, end))

            // Determine root key (override or sample default)
            val rootKey = zone.rootKey ?: sampleHeader.originalPitch

            // Skip zones with extreme pitch offsets (corrupt or unusual SF2 data)
            if (hasExtremeRootKeyOffset(zone.keyRange, rootKey, sampleHeader.name)) {
                return null
            }

            // Convert envelope data
            val envelope = convertEnvelope(zone.volumeEnvelope)

            // Check if loop is valid and enabled
            val hasLoop = SampleModes.hasLoop(zone.sampleModes) && loopEnd > loopStart + 7

            return Sf2Region(
                keyRange = zone.keyRange,
                velRange = zone.velRange,
                sampleId = sampleId,
                sampleRate = sampleHeader.sampleRate.takeIf { it > 0 } ?: 44100,
                sampleStart = start,
                sampleEnd = end,
                loopStart = loopStart,
                loopEnd = loopEnd,
                hasLoop = hasLoop,
                rootKey = rootKey,
                coarseTune = zone.coarseTune,
                fineTune = zone.fineTune,
                scaleTuning = zone.scaleTuning.takeIf { it in 0..100 } ?: 100,
                pitchCorrection = sampleHeader.pitchCorrection,
                attenuation = zone.attenuation,
                pan = zone.pan ?: 0f,
                exclusiveClass = zone.exclusiveClass,
                reverbSend = zone.reverbSend,
                chorusSend = zone.chorusSend,
                volumeEnvelope = envelope,
                sampleName = sampleHeader.name,
                filterFc = zone.filterFc,
                filterQ = zone.filterQ,
                vibLfo = createVibLfoParams(zone),
                modLfo = createModLfoParams(zone),
                modEnvelope = convertEnvelope(zone.modEnvelope),
                modEnvToPitch = zone.modEnvToPitch,
                modEnvToFilterFc = zone.modEnvToFilterFc,
                forcedKeyNum = zone.forcedKeyNum,
                forcedVelocity = zone.forcedVelocity
            )
        }
    }
}

/**
 * Volume envelope parameters in seconds/linear units (converted from SF2 timecents)
 */
data class VolumeEnvelope(
    val delay: Float = 0f,      // Delay time in seconds before attack starts
    val attack: Float = 0.001f, // Attack time in seconds
    val hold: Float = 0f,       // Hold time at peak before decay
    val decay: Float = 0.01f,   // Decay time in seconds
    val sustain: Float = 1f,    // Sustain level (0.0 - 1.0)
    val release: Float = 0.02f, // Release time in seconds
    // Key tracking: timecents per key from middle C (key 60)
    // Positive values = higher notes have shorter times
    val keynumToHold: Int = 0,  // timecents per key for hold adjustment
    val keynumToDecay: Int = 0  // timecents per key for decay adjustment
)
