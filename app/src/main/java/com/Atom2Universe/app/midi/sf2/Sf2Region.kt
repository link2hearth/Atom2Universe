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
    val forcedVelocity: Int?,           // Forces velocity value (for fixed-velocity samples)
    // sampleModes 3 : la boucle ne tourne que tant que la touche est enfoncée, puis la lecture
    // continue jusqu'à la fin de l'échantillon
    val loopUntilRelease: Boolean = false
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
     * Attenuation is in centibels (1/10th of a decibel), 0 to 1440 (144 dB) per the standard.
     * gain = 10^(-attenuation/200)
     */
    fun calculateGain(): Float {
        if (attenuation <= 0) return 1.0f
        return 10.0.pow(-attenuation.coerceAtMost(1440) / 200.0).toFloat()
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
         * Checks if a zone has an extreme pitch offset that would produce
         * unusable playback rates. Returns true if the zone should be skipped.
         *
         * Note: Many SF2 files intentionally stretch samples across wide ranges.
         * We only filter truly extreme cases that would sound broken.
         * Le scale tuning est pris en compte : les bruitages GM (applaudissements, hélicoptère...)
         * ont souvent une root key très éloignée mais 10 cents par touche seulement ; ils
         * étaient écartés à tort et restaient muets.
         */
        private fun hasExtremePitchOffset(keyRange: IntRange, rootKey: Int, scaleTuning: Int): Boolean {
            val keyRangeCenter = (keyRange.first + keyRange.last) / 2
            val centsOffset = (keyRangeCenter - rootKey) * scaleTuning

            // Only filter extremely stretched samples (>3 octaves with extreme rates)
            if (kotlin.math.abs(centsOffset) > 3600) {
                val estimatedRate = 2.0.pow(centsOffset / 1200.0)
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

            return build(zone, sampleId, sampleHeader, start, end, loopStart, loopEnd)
        }

        /**
         * Creates a region for memory-mapped mode.
         * Samples are read directly from the mmap'd file, so the data length is not known here:
         * the sample header bounds are used instead.
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

            return build(zone, sampleId, sampleHeader, start, end, loopStart, loopEnd)
        }

        private fun build(
            zone: Sf2ZoneData,
            sampleId: Int,
            sampleHeader: Sf2SampleHeader,
            start: Long,
            end: Long,
            loopStart: Long,
            loopEnd: Long
        ): Sf2Region? {
            // Determine root key (override or sample default).
            // Norme : originalPitch 255 = échantillon non accordé, 128-254 invalide -> 60.
            val rootKey = zone.rootKey ?: sampleHeader.originalPitch.takeIf { it in 0..127 } ?: 60

            // Norme : 0 à 1200 cents par touche (100 = gamme tempérée)
            val scaleTuning = zone.scaleTuning.takeIf { it in 0..1200 } ?: 100

            // Skip zones with extreme pitch offsets (corrupt or unusual SF2 data)
            if (hasExtremePitchOffset(zone.keyRange, rootKey, scaleTuning)) {
                return null
            }

            // Check if loop is valid and enabled
            val hasLoop = SampleModes.hasLoop(zone.sampleModes) && loopEnd > loopStart + 7
            val loopUntilRelease = hasLoop && zone.sampleModes == SampleModes.LOOP_UNTIL_RELEASE

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
                scaleTuning = scaleTuning,
                pitchCorrection = sampleHeader.pitchCorrection,
                attenuation = zone.attenuation,
                pan = zone.pan ?: 0f,
                exclusiveClass = zone.exclusiveClass,
                reverbSend = zone.reverbSend,
                chorusSend = zone.chorusSend,
                volumeEnvelope = convertEnvelope(zone.volumeEnvelope),
                sampleName = sampleHeader.name,
                filterFc = zone.filterFc,
                filterQ = zone.filterQ,
                vibLfo = createVibLfoParams(zone),
                modLfo = createModLfoParams(zone),
                modEnvelope = convertEnvelope(zone.modEnvelope),
                modEnvToPitch = zone.modEnvToPitch,
                modEnvToFilterFc = zone.modEnvToFilterFc,
                forcedKeyNum = zone.forcedKeyNum,
                forcedVelocity = zone.forcedVelocity,
                loopUntilRelease = loopUntilRelease
            )
        }

        private fun clampIndex(value: Long, minVal: Long, maxVal: Long): Long {
            return max(minVal, min(maxVal, value))
        }

        /**
         * Creates Vibrato LFO parameters from zone data.
         * Returns null if the zone defines nothing for the vibrato LFO. Its frequency and delay
         * are kept even without depth: the modulation wheel uses this LFO.
         */
        private fun createVibLfoParams(zone: Sf2ZoneData): LfoParameters? {
            if (zone.vibLfoToPitch == null && zone.vibLfoFreq == null && zone.vibLfoDelay == null) {
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
         * Converts SF2 envelope data (timecents) to seconds, following the standard's ranges.
         * The sustain becomes a normalized level (1 = full, 0 = none), interpreted by
         * [EnvelopeGenerator]: for the volume envelope, sustainVolEnv is an attenuation in
         * centibels (1000 cB = 100 dB = silence); for the modulation envelope, sustainModEnv is
         * a decrease in 0.1 % units (1000 = zero). Both give 1 - value / 1000.
         * Returns null when no generator of this envelope is set (the standard's defaults apply).
         */
        private fun convertEnvelope(data: Sf2EnvelopeData): VolumeEnvelope? {
            val hasData = data.delay != null || data.attack != null || data.hold != null ||
                data.decay != null || data.sustain != null || data.release != null ||
                (data.keynumToHold ?: 0) != 0 || (data.keynumToDecay ?: 0) != 0
            if (!hasData) return null

            fun seconds(timecents: Int?, maxTimecents: Int): Float =
                timecentsToSeconds((timecents ?: DEFAULT_TIMECENTS).coerceIn(DEFAULT_TIMECENTS, maxTimecents))

            val sustain = data.sustain?.let { (1f - it / 1000f).coerceIn(0f, 1f) } ?: 1f

            return VolumeEnvelope(
                delay = data.delay?.let { seconds(it, 5000) } ?: 0f,
                attack = seconds(data.attack, 8000),
                hold = data.hold?.let { seconds(it, 5000) } ?: 0f,
                decay = seconds(data.decay, 8000),
                sustain = sustain,
                release = seconds(data.release, 8000),
                keynumToHold = (data.keynumToHold ?: 0).coerceIn(-1200, 1200),
                keynumToDecay = (data.keynumToDecay ?: 0).coerceIn(-1200, 1200)
            )
        }

        // Valeur par défaut de la norme pour les durées d'enveloppe (2^-10 s ≈ 1 ms)
        private const val DEFAULT_TIMECENTS = -12000

        /**
         * Converts timecents to seconds: seconds = 2^(timecents/1200)
         */
        private fun timecentsToSeconds(timecents: Int): Float {
            return 2.0.pow(timecents / 1200.0).toFloat()
        }
    }
}

/**
 * Envelope parameters (volume or modulation envelope) converted from SF2 timecents.
 * Defaults are the standard's: -12000 timecents (≈ 1 ms) for the times, full sustain.
 */
data class VolumeEnvelope(
    val delay: Float = 0f,           // Delay time in seconds before attack starts
    val attack: Float = DEFAULT_TIME, // Attack time in seconds
    val hold: Float = 0f,            // Hold time at peak before decay
    val decay: Float = DEFAULT_TIME,  // Time of a full decay (100 dB or 1 -> 0) in seconds
    val sustain: Float = 1f,         // Normalized sustain level (1 = full, 0 = none)
    val release: Float = DEFAULT_TIME, // Time of a full release in seconds
    // Key tracking: timecents per key, applied as (60 - key) * value
    // Positive values = higher notes have shorter times
    val keynumToHold: Int = 0,  // timecents per key for hold adjustment
    val keynumToDecay: Int = 0  // timecents per key for decay adjustment
) {
    companion object {
        /** 2^(-12000/1200) s, the standard's default envelope time. */
        const val DEFAULT_TIME = 0.0009765625f
    }
}
