package com.Atom2Universe.app.midi.sf2

import kotlin.math.max
import kotlin.math.min

/**
 * Velocity curve types for different playing feel.
 */
enum class VelocityCurve {
    LINEAR,     // Direct mapping (flat, less expressive)
    CONCAVE,    // More control at low velocities (default, most musical)
    SOFT,       // Even more gentle at low velocities
    HARD        // More aggressive response
}

/**
 * Represents a single active voice in the SF2 synthesizer.
 * A voice plays one sample region with pitch shifting, envelope, and panning.
 *
 * Voices are pooled and reused to avoid allocations during playback.
 */
class Sf2Voice(
    private val sampleRate: Int = 44100
) {
    companion object {
        // Default velocity curve for all voices
        @Volatile var velocityCurve: VelocityCurve = VelocityCurve.CONCAVE

        /**
         * Calculates velocity gain based on the selected curve.
         * Uses pre-computed lookup table for performance.
         *
         * @param velocity MIDI velocity (0-127)
         * @return Gain value (0.0 to 1.0)
         */
        fun calculateVelocityGain(velocity: Int): Float {
            return PitchLookupTable.velocityToGain(velocity, velocityCurve)
        }

        // Threshold below which a voice is considered inaudible
        // -60dB = 0.001 linear, but we use slightly higher for safety margin
        const val VOICE_CULL_THRESHOLD = 0.002f

        // Low quality mode: use linear interpolation instead of cubic
        // Automatically enabled when voice count exceeds threshold
        @Volatile var lowQualityMode: Boolean = false

        // Ultra low quality mode: skip filters and use simplest rendering
        // Used when CPU is severely overloaded
        @Volatile var ultraLowQualityMode: Boolean = false

        // Voice count threshold for automatic low quality mode (linear interpolation)
        // Raised from 40 to 52: moderate improvement, safe for most devices
        const val LOW_QUALITY_THRESHOLD = 52

        // Voice count threshold for ultra low quality mode (skip filters, simpler envelope)
        // Raised from 64 to 80: filters/LFOs stay active longer without overloading most devices
        const val ULTRA_LOW_QUALITY_THRESHOLD = 80

        // Block size for block-based DSP processing
        // Modulation (envelope, LFOs, pitch) computed once per block (~750/s at 48kHz)
        // IIR filters and sample interpolation remain per-sample within each block
        const val BLOCK_SIZE = 64

        // Release cap mode: forces faster release when voice count is very high
        // This prevents buildup of releasing voices (Rush E scenario)
        @Volatile var releaseCapped: Boolean = false
        @Volatile var cappedReleaseSamples: Int = 4800  // ~100ms at 48000Hz

        // Loop handling constants (shared across all voice instances)
        const val SHORT_LOOP_THRESHOLD = 200
        const val CROSSFADE_SAMPLES = 32

        // Soft saturation threshold (above this, we start compressing)
        private const val SATURATION_THRESHOLD = 0.75f
        // Maximum output after saturation - reduced from 1.2 to 1.0 to prevent downstream issues
        private const val SATURATION_MAX = 1.0f

        /**
         * Soft saturation function to prevent extreme values from individual voices.
         * Uses a fast polynomial approximation that's gentler than hard clipping.
         * Values below threshold pass through unchanged; above threshold are compressed.
         */
        @JvmStatic
        @Suppress("unused")
        fun softSaturate(x: Float): Float {
            val absX = if (x >= 0f) x else -x

            // Below threshold: pass through unchanged (most common case - fast path)
            if (absX <= SATURATION_THRESHOLD) return x

            // Above threshold: apply soft saturation curve
            // Maps [threshold, infinity) -> [threshold, max) asymptotically
            val sign = if (x >= 0f) 1f else -1f
            val excess = absX - SATURATION_THRESHOLD
            // Use a simple hyperbolic curve with gentler knee
            val headroom = SATURATION_MAX - SATURATION_THRESHOLD
            val compressed = SATURATION_THRESHOLD + headroom * excess / (excess + headroom)
            return sign * compressed
        }
    }
    // Voice state (volatile for thread-safe visibility between render and MIDI threads)
    @Volatile
    var isActive: Boolean = false
        private set

    var channel: Int = 0
        private set

    var midiNote: Int = 60
        private set

    // Allocation order for voice stealing (higher = newer, lower = older = better steal candidate)
    var allocationOrder: Long = 0

    var velocity: Int = 100
        private set

    // Region being played
    var region: Sf2Region? = null
        private set

    // Sample playback state
    private var samplePosition: Double = 0.0
    private var playbackRate: Double = 1.0

    // Envelope
    val envelope = EnvelopeGenerator(sampleRate)

    // Low-pass resonant filter (SF2 timbre filter)
    private val filter = BiquadFilter(sampleRate)

    // LFOs for modulation
    private val vibratoLfo = Lfo(sampleRate)
    private val modulationLfo = Lfo(sampleRate)

    // Modulation envelope (for pitch and filter modulation)
    private val modEnvelope = EnvelopeGenerator(sampleRate)

    // LFO modulation depths (in appropriate units)
    private var vibLfoToPitchCents: Int = 0
    private var modLfoToPitchCents: Int = 0
    private var modLfoToFilterCents: Int = 0
    private var modLfoToVolumeCentibels: Int = 0

    // Modulation envelope depths
    private var modEnvToPitchCents: Int = 0
    private var modEnvToFilterCents: Int = 0

    // Gain (from velocity, attenuation, etc.)
    private var baseGain: Float = 1f
    private var velocityGain: Float = 1f

    // Pan
    private var panLeft: Float = 0.707f   // sqrt(0.5) for center
    private var panRight: Float = 0.707f

    // Exclusive class (for drum instruments)
    var exclusiveClass: Int = 0
        private set

    // Smoothed pitch bend (to avoid clicks from abrupt changes)
    private var smoothedPitchBend: Float = 0f
    // Smoothing coefficient (higher = faster response, lower = smoother)
    // At 44100Hz, 0.001 gives ~10ms smoothing time
    private val pitchBendSmoothingCoeff: Float = 0.002f

    // Declick mechanism for voice stealing
    // When a voice is stolen while audible, we add a quick attack ramp to mask the click
    private var declickSamplesRemaining: Int = 0
    private val declickDurationSamples: Int = (0.004f * sampleRate).toInt()  // 4ms declick ramp

    /**
     * Returns an estimated loudness value for voice stealing decisions.
     * Combines envelope level with static gains (velocity + attenuation).
     */
    fun getEstimatedAmplitude(): Float {
        return (envelope.getLevel() * baseGain * velocityGain).coerceAtLeast(0f)
    }

    /**
     * Triggers this voice with the given parameters.
     *
     * If the region has forcedVelocity set, that value is used instead of the played velocity.
     * This is used for sound effects and samples that should always play at the same volume.
     */
    fun trigger(
        channel: Int,
        note: Int,
        velocity: Int,
        region: Sf2Region
    ) {
        // DECLICK: If voice was active with audible level, add a quick attack ramp
        // This masks the click from the old voice being cut off during voice stealing
        if (isActive && envelope.getLevel() > 0.05f) {
            declickSamplesRemaining = declickDurationSamples
        } else {
            declickSamplesRemaining = 0
        }

        // Use forced velocity if specified (for sound effects, fixed-velocity samples)
        val effectiveVelocity = region.forcedVelocity ?: velocity

        this.isActive = true
        this.channel = channel
        this.midiNote = note
        this.velocity = effectiveVelocity
        this.region = region

        // Reset sample position
        samplePosition = 0.0

        // Calculate playback rate for pitch
        val calculatedRate = region.calculatePlaybackRate(note, sampleRate)
        // BUG FIX 1.13: Valider que le playback rate est fini et dans une plage raisonnable.
        // Un rate NaN/Inf ou hors limites causerait du crackle audio.
        // Plage valide: 0.01x (2 octaves en dessous) a 16x (4 octaves au dessus)
        playbackRate = if (calculatedRate.isNaN() || calculatedRate.isInfinite()) {
            1.0 // Fallback au rate nominal si calcul invalide
        } else {
            calculatedRate.coerceIn(0.01, 16.0)
        }

        // Calculate gain from velocity using the selected curve
        // Concave curves provide more expressive control at low velocities
        velocityGain = calculateVelocityGain(effectiveVelocity)

        // Calculate base gain from region attenuation
        baseGain = region.calculateGain()

        // Calculate pan (constant power panning)
        val pan = region.pan.coerceIn(-1f, 1f)
        // Convert pan (-1 to 1) to left/right gains using constant power law
        val angle = (pan + 1f) * 0.25f * Math.PI.toFloat()  // 0 to PI/2
        panLeft = kotlin.math.cos(angle)
        panRight = kotlin.math.sin(angle)

        // Store exclusive class
        exclusiveClass = region.exclusiveClass

        // Reset smoothed pitch bend to current channel value (will be updated in render)
        smoothedPitchBend = 0f

        // Configure and trigger envelope (with key tracking based on MIDI note)
        envelope.configure(region.volumeEnvelope, note)
        envelope.trigger()

        // Configure low-pass filter (SF2 timbre filter)
        // BUG FIX: Désactiver le filtre pour le canal 10 (percussion/drums)
        // Les percussions ont besoin de hautes fréquences (cymbales, hi-hat)
        filter.reset()
        if (channel == 9) { // Canal 10 MIDI = index 9
            filter.configure(null, null) // Désactive le filtre pour les percussions
        } else {
            filter.configure(region.filterFc, region.filterQ)

            // SF2 Default Modulator: MIDI velocity → Initial Filter Cutoff
            // Vélocité haute = filtre plus ouvert (son plus brillant), comportement FluidSynth.
            // Sans ce modulateur, les presets avec filtres agressifs par vélocité (ex. piano)
            // sonnent quasi-inaudibles à vélocité modérée sur SF2Engine vs FluidSynth.
            // Approche: interpolation linéaire entre la valeur de zone et Nyquist*0.95,
            // pondérée par la vélocité. À vel=127 → filtre quasi-ouvert, à vel=0 → valeur zone.
            if (filter.isActive()) {
                val velFactor = effectiveVelocity / 127f
                if (velFactor > 0f) {
                    val currentFcHz = filter.getCutoffHz()
                    val nyquistFcHz = sampleRate / 2f * 0.95f
                    val newFcHz = (currentFcHz + (nyquistFcHz - currentFcHz) * velFactor)
                        .coerceIn(BiquadFilter.MIN_FC_HZ, sampleRate / 2f - 100f)
                    filter.setParametersImmediate(newFcHz, filter.getQ())
                }
            }
        }

        // Configure Vibrato LFO
        // Always configure with at least a default frequency (6 Hz) so modulation wheel (CC1) works
        // even when the SF2 region doesn't define a vibrato LFO
        vibratoLfo.reset()
        if (region.vibLfo != null && region.vibLfo.hasEffect()) {
            vibratoLfo.configure(region.vibLfo.delay, region.vibLfo.frequency)
            vibratoLfo.trigger()
            vibLfoToPitchCents = region.vibLfo.getPitchDepthCents()
        } else {
            // Default vibrato LFO at ~6 Hz for mod wheel use (no region-defined depth)
            vibratoLfo.configure(null, null)  // Uses default 8.176 Hz
            vibratoLfo.trigger()
            vibLfoToPitchCents = 0  // No region-defined vibrato, but mod wheel can still use this LFO
        }

        // Configure Modulation LFO
        modulationLfo.reset()
        if (region.modLfo != null && region.modLfo.hasEffect()) {
            modulationLfo.configure(region.modLfo.delay, region.modLfo.frequency)
            modulationLfo.trigger()
            modLfoToPitchCents = region.modLfo.getPitchDepthCents()
            modLfoToFilterCents = region.modLfo.getFilterDepthCents()
            modLfoToVolumeCentibels = region.modLfo.getVolumeDepthCentibels()
        } else {
            modLfoToPitchCents = 0
            modLfoToFilterCents = 0
            modLfoToVolumeCentibels = 0
        }

        // Configure Modulation Envelope (with key tracking based on MIDI note)
        modEnvelope.reset()
        val hasModEnvEffect = (region.modEnvToPitch != null && region.modEnvToPitch != 0) ||
                              (region.modEnvToFilterFc != null && region.modEnvToFilterFc != 0)
        if (hasModEnvEffect) {
            // SF2 mod envelope defaults still apply even if no explicit envelope generators are set.
            // Without this, modEnvToFilterFc/pitch is ignored, which can overly darken drums
            // that rely on a default envelope to open the filter.
            modEnvelope.configure(region.modEnvelope, note)
            modEnvelope.trigger()
            modEnvToPitchCents = region.modEnvToPitch ?: 0
            modEnvToFilterCents = region.modEnvToFilterFc ?: 0
        } else {
            modEnvToPitchCents = 0
            modEnvToFilterCents = 0
        }
    }

    /**
     * Releases this voice (note off).
     * When release cap is active (high polyphony), forces a faster release.
     */
    fun release() {
        if (releaseCapped) {
            // Force faster release when voice count is very high
            envelope.releaseWithCap(cappedReleaseSamples)
            modEnvelope.releaseWithCap(cappedReleaseSamples)
        } else {
            envelope.release()
            modEnvelope.release()
        }
    }

    /**
     * Force stops this voice immediately (may cause clicks - use softStop when possible).
     */
    fun stop() {
        isActive = false
        envelope.reset()
    }

    /**
     * Soft stops this voice with a quick fade to prevent clicks.
     * Used for voice stealing - the voice will finish naturally after the quick fade.
     */
    fun softStop() {
        if (!isActive) return
        envelope.quickFade()
        modEnvelope.quickFade()
        // Voice remains active until envelope finishes (handled in render)
    }

    /**
     * Forces an emergency fade on a voice already in release.
     * Used when voice count is critical and we need to quickly clear resonating sounds.
     * Only affects voices that haven't already been emergency-faded.
     * @param fadeSamples Target fade time in samples
     */
    fun forceEmergencyFade(fadeSamples: Int) {
        if (!isActive) return
        // Only force fade if we're in release and the envelope hasn't already been shortened
        if (envelope.stage == EnvelopeGenerator.Stage.RELEASE) {
            envelope.forceEmergencyRelease(fadeSamples)
            modEnvelope.forceEmergencyRelease(fadeSamples)
        }
    }

    /**
     * Returns true if this voice is in the process of fading out (soft stop or release).
     */
    @Suppress("unused")
    fun isFadingOut(): Boolean {
        return isActive && envelope.stage == EnvelopeGenerator.Stage.RELEASE
    }

    /**
     * Renders audio samples into the output buffers.
     * @param sf2File The SF2 file containing sample data
     * @param outputLeft Left channel output buffer
     * @param outputRight Right channel output buffer
     * @param numSamples Number of samples to render
     * @param channelVolume Volume multiplier for this voice's channel (0.0-1.0)
     * @param pitchBendSemitones Pitch bend value in semitones (-2 to +2 typically)
     * @param channelPan Channel pan value (-1.0 left to +1.0 right, 0.0 center)
     * @param channelModulation Modulation wheel value (0.0-1.0), adds vibrato
     */
    fun render(
        sf2File: Sf2File,
        outputLeft: FloatArray,
        outputRight: FloatArray,
        numSamples: Int,
        channelVolume: Float = 1f,
        pitchBendSemitones: Float = 0f,
        channelPan: Float = 0f,
        channelModulation: Float = 0f,
        reverbSendLeft: FloatArray? = null,
        reverbSendRight: FloatArray? = null,
        reverbSendLevel: Float = 0f,
        chorusSendLeft: FloatArray? = null,
        chorusSendRight: FloatArray? = null,
        chorusSendLevel: Float = 0f
    ) {
        if (!isActive) return

        val reg = region ?: return

        // Calculate effective pan by combining region pan with channel pan (MIDI CC10)
        // Channel pan is additive: region pan positions the voice, channel pan shifts the whole channel
        val effectivePanLeft: Float
        val effectivePanRight: Float
        if (channelPan != 0f) {
            val combinedPan = (region!!.pan + channelPan).coerceIn(-1f, 1f)
            val angle = (combinedPan + 1f) * 0.25f * Math.PI.toFloat()
            effectivePanLeft = kotlin.math.cos(angle)
            effectivePanRight = kotlin.math.sin(angle)
        } else {
            effectivePanLeft = panLeft
            effectivePanRight = panRight
        }

        // Pre-compute send state (loop-invariant)
        val hasReverbSend = reverbSendLeft != null && reverbSendRight != null && reverbSendLevel > 0.01f
        val hasChorusSend = chorusSendLeft != null && chorusSendRight != null && chorusSendLevel > 0.01f

        // Ultra low quality mode: use fast path with minimal processing
        if (ultraLowQualityMode) {
            renderFastPath(sf2File, reg, outputLeft, outputRight, numSamples, channelVolume, effectivePanLeft, effectivePanRight, reverbSendLeft, reverbSendRight, reverbSendLevel, chorusSendLeft, chorusSendRight, chorusSendLevel)
            return
        }

        // Pre-compute loop parameters (constant for entire render call)
        val hasValidLoop = reg.hasLoop && (reg.loopEnd > reg.loopStart)
        val noLoop = !reg.hasLoop
        val loopLength = if (hasValidLoop) (reg.loopEnd - reg.loopStart).toDouble() else 0.0
        val loopRelativeEnd = if (hasValidLoop) (reg.loopEnd - reg.sampleStart).toDouble() else 0.0
        val loopRelativeStart = if (hasValidLoop) (reg.loopStart - reg.sampleStart).toDouble() else 0.0
        val sampleLength = if (noLoop) (reg.sampleEnd - reg.sampleStart).toDouble() else 0.0

        // Pre-compute modulation flags (avoid per-block checks when nothing is active)
        val hasVibLfo = vibLfoToPitchCents != 0 || channelModulation > 0f
        val hasModLfo = modLfoToPitchCents != 0 || modLfoToVolumeCentibels != 0 || modLfoToFilterCents != 0
        val hasModEnv = modEnvToPitchCents != 0 || modEnvToFilterCents != 0

        // === BLOCK-BASED RENDERING ===
        // Process numSamples in blocks of BLOCK_SIZE (64 samples).
        // Block header: compute envelope, LFOs, modulation, pitch at block rate (~750/s at 48kHz)
        // Inner loop: only sample interpolation, IIR filters, gain ramp, output
        var offset = 0
        while (offset < numSamples) {
            val blockEnd = min(offset + BLOCK_SIZE, numSamples)
            val blockLen = blockEnd - offset

            // --- Block header: compute modulation values once per block ---

            // Envelope: capture start level, advance by blockLen, capture end level
            val envStart = envelope.getLevel()
            val envEnd = envelope.processBlock(blockLen)

            // Check if envelope finished during this block
            if (envelope.isFinished()) {
                isActive = false
                return
            }

            // Early cull at block rate (saves processing entire block)
            val blockMaxEnv = max(envStart, envEnd)
            if (blockMaxEnv * velocityGain < VOICE_CULL_THRESHOLD) {
                if (envelope.stage == EnvelopeGenerator.Stage.RELEASE) {
                    isActive = false
                    return
                }
            }

            // LFOs: O(1) per block via processBlock (no per-sample loop)
            val vibLfoValue = if (hasVibLfo) vibratoLfo.processBlock(blockLen) else 0f
            val modLfoValue = if (hasModLfo) modulationLfo.processBlock(blockLen) else 0f

            // Modulation envelope (once per block)
            val modEnvValue = if (hasModEnv) modEnvelope.processBlock(blockLen) else 0f

            // Pitch bend smoothing (approximate N exponential steps as single larger step)
            // coeff * blockLen ≈ 1 - (1-coeff)^blockLen for small coeff (0.002 * 64 = 0.128 ≈ exact 0.120)
            val pbCoeff = (pitchBendSmoothingCoeff * blockLen).coerceAtMost(1f)
            smoothedPitchBend += pbCoeff * (pitchBendSemitones - smoothedPitchBend)

            // Pitch modulation (combined: vibrato LFO + mod LFO + mod wheel + mod envelope + pitch bend)
            // channelModulation is pre-scaled to semitones (rawCC1 × depthRange), so multiply by 100 for cents.
            val modWheelVibratoCents = if (channelModulation > 0f) {
                val lfoSource = if (vibLfoValue != 0f) vibLfoValue else modLfoValue
                lfoSource * channelModulation * 100f
            } else 0f
            val totalPitchModCents = vibLfoValue * vibLfoToPitchCents +
                    modLfoValue * modLfoToPitchCents +
                    modWheelVibratoCents +
                    modEnvValue * modEnvToPitchCents +
                    smoothedPitchBend * 100f

            // Effective playback rate for this block (constant pitch within a 1.3ms block)
            val effectiveRate = if (totalPitchModCents != 0f) {
                playbackRate * PitchLookupTable.centsToFactor(totalPitchModCents).toDouble()
            } else {
                playbackRate
            }

            // Volume modulation (tremolo from mod LFO, once per block)
            val volumeModFactor = if (modLfoToVolumeCentibels != 0) {
                val cbMod = modLfoValue * modLfoToVolumeCentibels
                PitchLookupTable.centibelsToFactor(cbMod)
            } else 1f

            // Filter modulation (once per block instead of per-sample)
            val lfoFilterModCents = modLfoValue * modLfoToFilterCents
            val envFilterModCents = modEnvValue * modEnvToFilterCents
            val totalFilterModCents = lfoFilterModCents + envFilterModCents
            if (totalFilterModCents != 0f && filter.isActive()) {
                val filterModFactor = PitchLookupTable.centsToFactor(totalFilterModCents)
                filter.modulateCutoff(filterModFactor)
            }

            // Block gain: everything except envelope (which is linearly interpolated per-sample)
            // 1 multiply per sample instead of 5 (base * velocity * channel * volumeMod * env)
            val blockGain = baseGain * velocityGain * channelVolume * volumeModFactor

            // Envelope linear ramp: interpolate from envStart to envEnd across the block
            val envIncrement = if (blockLen > 1) (envEnd - envStart) / blockLen.toFloat() else 0f
            var envLevel = envStart

            // --- Inner loop: per-sample processing (tight loop) ---
            for (i in offset until blockEnd) {
                // Sample interpolation (cubic or linear depending on quality mode)
                var sample = getSampleInterpolated(sf2File, reg)

                // SF2 low-pass timbre filter (per-sample, IIR state-dependent)
                sample = filter.process(sample)

                // Apply gains with linearly interpolated envelope
                var finalGain = sample * blockGain * envLevel
                envLevel += envIncrement

                // Declick ramp for voice stealing
                if (declickSamplesRemaining > 0) {
                    val rampProgress = 1f - (declickSamplesRemaining.toFloat() / declickDurationSamples)
                    val smoothRamp = rampProgress * rampProgress * (3f - 2f * rampProgress)
                    finalGain *= smoothRamp
                    declickSamplesRemaining--
                }

                // NaN/Inf protection + lightweight clamp
                val safeSample = if (finalGain.isNaN() || finalGain.isInfinite()) {
                    0f
                } else {
                    finalGain.coerceIn(-2.0f, 2.0f)
                }

                // Pan and mix into output
                outputLeft[i] += safeSample * effectivePanLeft
                outputRight[i] += safeSample * effectivePanRight

                // Direct reverb send
                if (hasReverbSend) {
                    reverbSendLeft[i] += safeSample * effectivePanLeft * reverbSendLevel
                    reverbSendRight[i] += safeSample * effectivePanRight * reverbSendLevel
                }

                // Direct chorus send
                if (hasChorusSend) {
                    chorusSendLeft[i] += safeSample * effectivePanLeft * chorusSendLevel
                    chorusSendRight[i] += safeSample * effectivePanRight * chorusSendLevel
                }

                // Advance sample position (constant rate within block)
                samplePosition += effectiveRate

                // Loop/end handling (pre-computed loop parameters, O(1) modulo wrap)
                if (hasValidLoop) {
                    if (samplePosition >= loopRelativeEnd) {
                        samplePosition = loopRelativeStart + ((samplePosition - loopRelativeStart) % loopLength)
                        // Safety: floating-point modulo may land exactly at end
                        if (samplePosition >= loopRelativeEnd) samplePosition = loopRelativeStart
                    }
                } else if (noLoop) {
                    if (samplePosition >= sampleLength) {
                        isActive = false
                        return
                    }
                }
            }

            offset = blockEnd
        }
    }

    /**
     * Gets a sample with interpolation.
     * Uses cubic Hermite interpolation normally for high quality.
     * Falls back to linear interpolation when lowQualityMode is enabled
     * (automatically when voice count is very high to save CPU).
     *
     * For short loops, applies crossfade near the loop boundary to reduce clicking.
     * For all loops, uses loop-aware interpolation near loopEnd to prevent
     * reading non-loop tail data (which causes clicks at every loop wrap).
     */
    private fun getSampleInterpolated(sf2File: Sf2File, region: Sf2Region): Float {
        val absolutePos = region.sampleStart + samplePosition.toLong()
        val index = absolutePos.toInt()
        val frac = (samplePosition - samplePosition.toLong()).toFloat()

        // Loop-aware interpolation: when near the loop boundary, indices past
        // loopEnd must wrap to loopStart (like FluidSynth's guard points).
        // Without this, cubic/linear interpolation reads non-loop tail data,
        // creating a click at every loop wrap point.
        if (region.hasLoop) {
            val loopLength = region.loopEnd - region.loopStart

            // BUG FIX 1.9: Verifier loopLength > 0 pour eviter division par zero
            // Une boucle degeneree (loopEnd <= loopStart) est traitee comme pas de boucle
            if (loopLength <= 0) {
                // Fallback au chemin normal (pas de boucle)
            } else {
                // Short loops: use crossfade for extra smoothing
                if (loopLength in 1 until SHORT_LOOP_THRESHOLD) {
                    return getSampleWithLoopCrossfade(sf2File, region, loopLength)
                }

                // Near loop boundary: cubic reads index+2, so trigger 2 samples before loopEnd
                if (absolutePos >= region.loopEnd - 2) {
                    return getLoopBoundarySample(sf2File, region, index.toLong(), frac)
                }
            }
        }

        // Normal path: safely within loop interior or no loop
        if (lowQualityMode) {
            val s1 = sf2File.getSample(index.toLong())
            val s2 = sf2File.getSample((index + 1).toLong())
            return s1 + (s2 - s1) * frac
        }

        // Cubic Hermite interpolation (Catmull-Rom spline) via pre-computed lookup table
        // Table replaces inline coefficient calculation (~16 float ops) with 4 multiply-adds
        val s0 = sf2File.getSample((index - 1).toLong())
        val s1 = sf2File.getSample(index.toLong())
        val s2 = sf2File.getSample((index + 1).toLong())
        val s3 = sf2File.getSample((index + 2).toLong())

        return PitchLookupTable.cubicInterpolate(s0, s1, s2, s3, frac)
    }

    /**
     * Loop-boundary-aware sample interpolation.
     * Wraps any index >= loopEnd back to loopStart + offset, ensuring the
     * interpolation reads loop-continuous data instead of non-loop tail data.
     * This is the equivalent of FluidSynth's "guard point" technique.
     */
    private fun getLoopBoundarySample(sf2File: Sf2File, region: Sf2Region, index: Long, frac: Float): Float {
        val loopLen = region.loopEnd - region.loopStart

        // BUG FIX 1.9: Verifier loopLen > 0 avant division pour eviter division par zero
        // Cela peut arriver si loopEnd == loopStart (boucle invalide/degeneree)
        if (loopLen <= 0) {
            // Fallback: retourner l'echantillon direct sans interpolation de boucle
            return sf2File.getSample(index)
        }

        // Wrap indices past loopEnd back into the loop region
        fun wrap(idx: Long): Long {
            return if (idx >= region.loopEnd) {
                region.loopStart + (idx - region.loopEnd) % loopLen
            } else {
                idx
            }
        }

        if (lowQualityMode) {
            val s1 = sf2File.getSample(wrap(index))
            val s2 = sf2File.getSample(wrap(index + 1))
            return s1 + (s2 - s1) * frac
        }

        val s0 = sf2File.getSample(wrap(index - 1))
        val s1 = sf2File.getSample(wrap(index))
        val s2 = sf2File.getSample(wrap(index + 1))
        val s3 = sf2File.getSample(wrap(index + 2))

        return PitchLookupTable.cubicInterpolate(s0, s1, s2, s3, frac)
    }

    /**
     * Gets a sample with crossfade for short loops.
     * This blends the sample near the loop end with the wrapped-around position
     * to eliminate clicking at the loop boundary.
     */
    private fun getSampleWithLoopCrossfade(sf2File: Sf2File, region: Sf2Region, loopLength: Long): Float {
        val loopRelativeStart = (region.loopStart - region.sampleStart).toDouble()
        val loopRelativeEnd = (region.loopEnd - region.sampleStart).toDouble()

        // Normalize position to be within the loop if we've entered it
        var pos = samplePosition
        if (pos >= loopRelativeEnd) {
            pos = loopRelativeStart + ((pos - loopRelativeStart) % loopLength.toDouble())
            // Safety: floating-point modulo may land exactly at end
            if (pos >= loopRelativeEnd) pos = loopRelativeStart
        }

        // For short loops (< 2x crossfade size), skip crossfade entirely.
        // Crossfade would exceed loop bounds causing out-of-bounds reads.
        // Use loop-aware linear interpolation instead.
        if (loopLength < CROSSFADE_SAMPLES * 2) {
            val absolutePos = region.sampleStart + pos.toLong()
            val frac = (pos - pos.toLong()).toFloat()
            val sa = sf2File.getSample(absolutePos)
            // Wrap next sample within loop to avoid reading past loop end
            val nextPos = pos + 1.0
            val wrappedNextAbs = if (nextPos >= loopRelativeEnd) {
                region.sampleStart + loopRelativeStart.toLong()
            } else {
                absolutePos + 1
            }
            val sb = sf2File.getSample(wrappedNextAbs)
            return sa + (sb - sa) * frac
        }

        // Calculate distance from loop end
        val distFromEnd = loopRelativeEnd - pos

        // Crossfade length: never exceed half the loop to stay within bounds
        val crossfadeLen = minOf(CROSSFADE_SAMPLES, (loopLength / 2).toInt())

        if (distFromEnd < crossfadeLen && distFromEnd >= 0) {
            // Get sample at current position
            val absPos1 = region.sampleStart + pos.toLong()
            val s1 = sf2File.getSample(absPos1)

            // Get sample at wrapped-around position (start of loop + offset)
            val wrapOffset = (crossfadeLen - distFromEnd).toLong()
            // Belt-and-suspenders: ensure offset stays within loop bounds
            val boundedOffset = if (wrapOffset >= loopLength) wrapOffset % loopLength else wrapOffset
            val absPos2 = region.loopStart + boundedOffset
            val s2 = sf2File.getSample(absPos2)

            // Linear crossfade factor: 1.0 at loop end, 0.0 at start of crossfade zone
            val fade = (1.0 - distFromEnd / crossfadeLen).toFloat().coerceIn(0f, 1f)

            // Blend samples
            return s1 * (1f - fade) + s2 * fade
        }

        // Not in crossfade zone - regular sample with linear interpolation
        val absolutePos = region.sampleStart + pos.toLong()
        val index = absolutePos.toInt()
        val frac = (pos - pos.toLong()).toFloat()

        val sa = sf2File.getSample(index.toLong())
        val sb = sf2File.getSample((index + 1).toLong())
        return sa + (sb - sa) * frac
    }

    /**
     * Ultra-fast rendering path for CPU-constrained situations.
     * Skips: LFOs, modulation envelope, anti-aliasing, pitch bend smoothing, block-based processing.
     * Keeps: SF2 low-pass filter (critical for timbre — skipping it causes crackling on filtered instruments).
     * Uses: Simple envelope, linear interpolation, basic panning, clamp.
     */
    private fun renderFastPath(
        sf2File: Sf2File,
        reg: Sf2Region,
        outputLeft: FloatArray,
        outputRight: FloatArray,
        numSamples: Int,
        channelVolume: Float,
        effectivePanLeft: Float = panLeft,
        effectivePanRight: Float = panRight,
        reverbSendLeft: FloatArray? = null,
        reverbSendRight: FloatArray? = null,
        reverbSendLevel: Float = 0f,
        chorusSendLeft: FloatArray? = null,
        chorusSendRight: FloatArray? = null,
        chorusSendLevel: Float = 0f
    ) {
        val hasReverbSend = reverbSendLeft != null && reverbSendRight != null && reverbSendLevel > 0.01f
        val hasChorusSend = chorusSendLeft != null && chorusSendRight != null && chorusSendLevel > 0.01f
        val gain = baseGain * velocityGain * channelVolume

        // Pre-compute loop parameters (constant for entire render call)
        val fpHasValidLoop = reg.hasLoop && (reg.loopEnd > reg.loopStart)
        val fpLoopLength = if (fpHasValidLoop) (reg.loopEnd - reg.loopStart).toDouble() else 0.0
        val fpLoopRelativeEnd = if (fpHasValidLoop) (reg.loopEnd - reg.sampleStart).toDouble() else 0.0
        val fpLoopRelativeStart = if (fpHasValidLoop) (reg.loopStart - reg.sampleStart).toDouble() else 0.0
        val fpSampleLength = if (!reg.hasLoop) (reg.sampleEnd - reg.sampleStart).toDouble() else 0.0

        for (i in 0 until numSamples) {
            val envLevel = envelope.process()

            if (envelope.isFinished()) {
                isActive = false
                return
            }

            if (envLevel < 0.005f && envelope.stage == EnvelopeGenerator.Stage.RELEASE) {
                isActive = false
                return
            }

            // Linear interpolation
            val absolutePos = reg.sampleStart + samplePosition.toLong()
            val index = absolutePos.toInt()
            val frac = (samplePosition - samplePosition.toLong()).toFloat()

            val s1 = sf2File.getSample(index.toLong())
            val s2 = sf2File.getSample((index + 1).toLong())
            var sample = s1 + (s2 - s1) * frac

            // Apply SF2 low-pass timbre filter even in fast path.
            // Skipping the filter causes massive timbre changes when ultraLowQualityMode
            // toggles (e.g., 587 Hz cutoff pad goes from muffled to bright = crackling).
            // A biquad is only 5 multiply-adds per sample — negligible CPU cost.
            sample = filter.process(sample)

            var finalSample = sample * gain * envLevel

            // DECLICK: Apply attack ramp in fast path too
            if (declickSamplesRemaining > 0) {
                val rampProgress = 1f - (declickSamplesRemaining.toFloat() / declickDurationSamples)
                val smoothRamp = rampProgress * rampProgress * (3f - 2f * rampProgress)
                finalSample *= smoothRamp
                declickSamplesRemaining--
            }

            // NaN/Inf protection + lightweight clamp (critical for stability)
            if (finalSample.isNaN() || finalSample.isInfinite()) {
                finalSample = 0f
            } else {
                finalSample = finalSample.coerceIn(-2.0f, 2.0f)
            }

            outputLeft[i] += finalSample * effectivePanLeft
            outputRight[i] += finalSample * effectivePanRight

            // Direct reverb send (fast path)
            if (hasReverbSend) {
                reverbSendLeft[i] += finalSample * effectivePanLeft * reverbSendLevel
                reverbSendRight[i] += finalSample * effectivePanRight * reverbSendLevel
            }

            // Direct chorus send (fast path)
            if (hasChorusSend) {
                chorusSendLeft[i] += finalSample * effectivePanLeft * chorusSendLevel
                chorusSendRight[i] += finalSample * effectivePanRight * chorusSendLevel
            }

            samplePosition += playbackRate

            // Loop/end handling (pre-computed parameters, O(1) modulo wrap)
            if (fpHasValidLoop) {
                if (samplePosition >= fpLoopRelativeEnd) {
                    samplePosition = fpLoopRelativeStart + ((samplePosition - fpLoopRelativeStart) % fpLoopLength)
                    // Safety: floating-point modulo may land exactly at end
                    if (samplePosition >= fpLoopRelativeEnd) samplePosition = fpLoopRelativeStart
                }
            } else if (!reg.hasLoop) {
                if (samplePosition >= fpSampleLength) {
                    isActive = false
                    return
                }
            }
        }
    }

    /**
     * Checks if this voice matches the given channel and note (for note-off).
     */
    fun matches(channel: Int, note: Int): Boolean {
        return this.channel == channel && this.midiNote == note && isActive
    }

    /**
     * Checks if this voice should be killed by an exclusive class.
     */
    fun shouldBeKilledByExclusiveClass(exclusiveClass: Int, channel: Int): Boolean {
        if (exclusiveClass == 0) return false
        return this.exclusiveClass == exclusiveClass &&
                this.channel == channel &&
                this.isActive
    }

    /**
     * Returns true if this voice has finished and can be reused.
     */
    @Suppress("unused")
    fun isFinished(): Boolean = !isActive || envelope.isFinished()

    /**
     * Resets the voice for reuse.
     */
    fun reset() {
        isActive = false
        channel = 0
        midiNote = 60
        velocity = 100
        allocationOrder = 0
        region = null
        samplePosition = 0.0
        playbackRate = 1.0
        baseGain = 1f
        velocityGain = 1f
        panLeft = 0.707f
        panRight = 0.707f
        exclusiveClass = 0
        envelope.reset()
        filter.reset()
        vibratoLfo.reset()
        modulationLfo.reset()
        vibLfoToPitchCents = 0
        modLfoToPitchCents = 0
        modLfoToFilterCents = 0
        modLfoToVolumeCentibels = 0
        modEnvelope.reset()
        modEnvToPitchCents = 0
        modEnvToFilterCents = 0
        smoothedPitchBend = 0f
        declickSamplesRemaining = 0
    }

    /**
     * Returns true if this voice has an active filter.
     */
    @Suppress("unused")
    fun hasActiveFilter(): Boolean = filter.isActive()

    /**
     * Returns filter debug info.
     */
    @Suppress("unused")
    fun getFilterInfo(): String = filter.getDebugInfo()
}
