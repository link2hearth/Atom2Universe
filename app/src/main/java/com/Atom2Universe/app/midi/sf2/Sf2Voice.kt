package com.Atom2Universe.app.midi.sf2

import kotlin.math.max
import kotlin.math.min

/**
 * Velocity curve types for different playing feel.
 */
enum class VelocityCurve {
    LINEAR,     // Direct mapping (flat, less expressive)
    CONCAVE,    // SF2 standard (default modulator velocity -> attenuation, 960 cB concave)
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

        // Threshold below which a releasing voice is considered inaudible (-90 dB, the noise
        // floor FluidSynth uses): the release runs almost to its end, as in the standard
        const val VOICE_CULL_THRESHOLD = 0.00003f

        // Low quality mode: use linear interpolation instead of cubic
        // Automatically enabled when voice count exceeds threshold
        @Volatile var lowQualityMode: Boolean = false

        // Voice count threshold for automatic low quality mode (linear interpolation)
        // Raised from 40 to 52: moderate improvement, safe for most devices
        const val LOW_QUALITY_THRESHOLD = 52

        // Block size for block-based DSP processing
        // Modulation (envelope, LFOs, pitch) computed once per block (~750/s at 48kHz)
        // IIR filters and sample interpolation remain per-sample within each block
        const val BLOCK_SIZE = 64

        // Release cap mode: forces faster release when voice count is very high
        // This prevents buildup of releasing voices (Rush E scenario)
        @Volatile var releaseCapped: Boolean = false
        @Volatile var cappedReleaseSamples: Int = 4800  // ~100ms at 48000Hz

        // Enveloppes quand la zone n'en définit aucune : valeurs par défaut de la norme
        // (durées de -12000 timecents, sustain plein), pour le volume comme pour la modulation.
        private val DEFAULT_ENVELOPE = VolumeEnvelope()

        // Modulateur par défaut SF2 n° 2 : vélocité -> coupure du filtre, -2400 cents à
        // vélocité nulle, linéaire décroissant jusqu'à 0 vers la vélocité maximale
        private const val VELOCITY_TO_FILTER_CENTS = -2400f

        // Modulateurs par défaut SF2 n° 8 et 9 : CC91 / CC93 -> envois réverbération / chorus,
        // 20 % au maximum, ajoutés aux générateurs de la zone
        const val CC_EFFECTS_SEND_AMOUNT = 0.2f

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

    // Envelope (volume: dB-linear decay and release)
    val envelope = EnvelopeGenerator(sampleRate, isVolume = true)

    // Low-pass resonant filter (SF2 timbre filter)
    private val filter = BiquadFilter(sampleRate)

    // LFOs for modulation
    private val vibratoLfo = Lfo(sampleRate)
    private val modulationLfo = Lfo(sampleRate)

    // Modulation envelope (for pitch and filter modulation, linear)
    private val modEnvelope = EnvelopeGenerator(sampleRate, isVolume = false)

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
    // Only used when a slot still audible has to be reused directly (no spare slot left):
    // the new note gets a quick attack ramp to soften the cut
    private var declickSamplesRemaining: Int = 0
    private val declickDurationSamples: Int = (0.004f * sampleRate).toInt()  // 4ms declick ramp

    // Voix en fondu rapide (volée ou coupée par une exclusive class) : elle libère sa place
    // dans la polyphonie mais continue de jouer son fondu de 15 ms dans son propre slot.
    var isQuickFading: Boolean = false
        private set

    // Timing à l'échantillon près : la note démarre / est relâchée à cette position
    // dans le prochain buffer rendu (0 = tout de suite).
    private var startDelayFrames: Int = 0
    private var pendingReleaseFrame: Int = -1

    // sampleModes 3 : la touche a été relâchée, la boucle est quittée
    private var loopReleased: Boolean = false
    // La lecture a déjà fait au moins un tour de boucle (l'interpolation peut alors lire
    // avant loopStart en repartant de la fin de boucle)
    private var hasWrapped: Boolean = false

    // Derniers gains appliqués (fin du bloc précédent), pour rampes sans escalier.
    // < 0 = pas encore de valeur (nouvelle note).
    private var lastChannelGain: Float = -1f
    private var lastLevelLeft: Float = -1f
    private var lastLevelRight: Float = -1f

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
        region: Sf2Region,
        startDelayFrames: Int = 0
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

        isQuickFading = false
        this.startDelayFrames = startDelayFrames.coerceAtLeast(0)
        pendingReleaseFrame = -1
        lastChannelGain = -1f
        lastLevelLeft = -1f
        lastLevelRight = -1f

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

        // Le lissage du pitch bend repartira de la valeur courante du canal au premier rendu.
        // (Avant : départ à 0, donc chaque note glissait depuis la hauteur « sans bend » pendant
        // ~20 ms, y compris sur un canal transposé par RPN coarse/fine tuning.)
        smoothedPitchBend = Float.NaN

        // Configure and trigger envelope (with key tracking based on MIDI note)
        envelope.configure(region.volumeEnvelope ?: DEFAULT_ENVELOPE, note)
        envelope.trigger()

        // Configure low-pass filter (SF2 timbre filter), present on every voice like in the
        // standard, with the default velocity modulator: -2400 cents × (1 - velocity / 128)
        filter.reset()
        val velocityFilterCents = VELOCITY_TO_FILTER_CENTS * (1f - effectiveVelocity / 128f)
        filter.configure(
            (region.filterFc ?: BiquadFilter.DEFAULT_FC_CENTS) + velocityFilterCents,
            region.filterQ ?: 0
        )

        // Configure Vibrato LFO (always running: the modulation wheel drives its depth)
        vibratoLfo.reset()
        vibratoLfo.configure(region.vibLfo?.delay, region.vibLfo?.frequency)
        vibratoLfo.trigger()
        vibLfoToPitchCents = region.vibLfo?.getPitchDepthCents() ?: 0

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
            modEnvelope.configure(region.modEnvelope ?: DEFAULT_ENVELOPE, note)
            modEnvelope.trigger()
            modEnvToPitchCents = region.modEnvToPitch ?: 0
            modEnvToFilterCents = region.modEnvToFilterFc ?: 0
        } else {
            modEnvToPitchCents = 0
            modEnvToFilterCents = 0
        }

        loopReleased = false
        hasWrapped = false
    }

    /**
     * Releases this voice (note off).
     * When release cap is active (high polyphony), forces a faster release.
     *
     * @param frameOffset position, dans le prochain buffer rendu, où le relâchement doit
     *   avoir lieu (0 = immédiatement)
     */
    fun release(frameOffset: Int = 0) {
        if (frameOffset > 0) {
            pendingReleaseFrame = if (pendingReleaseFrame < 0) frameOffset else min(pendingReleaseFrame, frameOffset)
            return
        }
        pendingReleaseFrame = -1
        loopReleased = true
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
        isQuickFading = true
        pendingReleaseFrame = -1
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
     * @param channelVolume Volume multiplier for this voice's channel (0.0-1.0).
     *   The voice ramps linearly from the previous buffer's value to this one.
     * @param pitchBendSemitones Pitch bend value in semitones (-2 to +2 typically)
     * @param channelPan Channel pan value (-1.0 left to +1.0 right, 0.0 center)
     * @param channelModulation Modulation wheel value (0.0-1.0), adds vibrato
     * @param reverbSendLevel Channel CC91 value (0.0-1.0); the voice sends its zone reverb
     *   amount plus 20 % × this value (SF2 default modulator)
     * @param chorusSendLevel Channel CC93 value (0.0-1.0), same rule as the reverb
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

        // Note programmée plus loin dans ce buffer (ou dans un suivant) : rien à rendre avant
        var startFrame = 0
        if (startDelayFrames > 0) {
            if (startDelayFrames >= numSamples) {
                startDelayFrames -= numSamples
                // Un relâchement arrivé avant le début de la note s'applique dès son début
                if (pendingReleaseFrame >= 0) pendingReleaseFrame = 0
                return
            }
            startFrame = startDelayFrames
            startDelayFrames = 0
        }

        // Relâchement programmé dans ce buffer (jamais avant le début de la note)
        var releaseFrame = -1
        if (pendingReleaseFrame >= 0) {
            releaseFrame = pendingReleaseFrame.coerceAtLeast(startFrame)
            pendingReleaseFrame = -1
            if (releaseFrame >= numSamples) {
                pendingReleaseFrame = releaseFrame - numSamples
                releaseFrame = -1
            }
        }

        // Calculate effective pan by combining region pan with channel pan (MIDI CC10)
        // Channel pan is additive: region pan positions the voice, channel pan shifts the whole channel
        val effectivePanLeft: Float
        val effectivePanRight: Float
        if (channelPan != 0f) {
            val combinedPan = (reg.pan + channelPan).coerceIn(-1f, 1f)
            val angle = (combinedPan + 1f) * 0.25f * Math.PI.toFloat()
            effectivePanLeft = kotlin.math.cos(angle)
            effectivePanRight = kotlin.math.sin(angle)
        } else {
            effectivePanLeft = panLeft
            effectivePanRight = panRight
        }

        // Effect sends: zone generator + 20 % × CC (SF2 default modulators), loop-invariant
        val reverbSend = ((reg.reverbSend ?: 0f) + CC_EFFECTS_SEND_AMOUNT * reverbSendLevel).coerceIn(0f, 1f)
        val chorusSend = ((reg.chorusSend ?: 0f) + CC_EFFECTS_SEND_AMOUNT * chorusSendLevel).coerceIn(0f, 1f)
        val hasReverbSend = reverbSendLeft != null && reverbSendRight != null && reverbSend > 0.001f
        val hasChorusSend = chorusSendLeft != null && chorusSendRight != null && chorusSend > 0.001f

        // Pre-compute loop parameters (constant for entire render call)
        val hasValidLoop = reg.hasLoop && (reg.loopEnd > reg.loopStart)
        val loopLength = if (hasValidLoop) (reg.loopEnd - reg.loopStart).toDouble() else 0.0
        val loopRelativeEnd = if (hasValidLoop) (reg.loopEnd - reg.sampleStart).toDouble() else 0.0
        val loopRelativeStart = if (hasValidLoop) (reg.loopStart - reg.sampleStart).toDouble() else 0.0
        val sampleLength = (reg.sampleEnd - reg.sampleStart).toDouble()

        // Pre-compute modulation flags (avoid per-block checks when nothing is active)
        val hasVibLfo = vibLfoToPitchCents != 0 || channelModulation > 0f
        val hasModLfo = modLfoToPitchCents != 0 || modLfoToVolumeCentibels != 0 || modLfoToFilterCents != 0
        val hasModEnv = modEnvToPitchCents != 0 || modEnvToFilterCents != 0

        // Gain de canal (volume, expression, gain global, gain selon le nombre de voix) :
        // rampe linéaire depuis la valeur du buffer précédent, au lieu d'une marche à chaque buffer
        val targetChannelGain = channelVolume
        val startChannelGain = if (lastChannelGain < 0f) targetChannelGain else lastChannelGain
        lastChannelGain = targetChannelGain
        val renderSpan = (numSamples - startFrame).toFloat()
        val staticGain = baseGain * velocityGain

        // === BLOCK-BASED RENDERING ===
        // Process numSamples in blocks of BLOCK_SIZE (64 samples).
        // Block header: compute envelope, LFOs, modulation, pitch at block rate (~750/s at 48kHz)
        // Inner loop: only sample interpolation, IIR filters, gain ramp, output
        var offset = startFrame
        while (offset < numSamples) {
            if (offset == releaseFrame) {
                release()
                releaseFrame = -1
            }
            var blockEnd = min(offset + BLOCK_SIZE, numSamples)
            if (releaseFrame > offset) blockEnd = min(blockEnd, releaseFrame)
            val blockLen = blockEnd - offset

            // --- Block header: compute modulation values once per block ---

            // Envelope: capture start level, advance by blockLen, capture end level
            val envStart = envelope.getLevel()
            var envEnd = envelope.processBlock(blockLen)

            // Dernier bloc de la voix (enveloppe terminée, ou relâchement devenu inaudible) :
            // on le rend quand même avec une rampe jusqu'à zéro au lieu de couper net,
            // sinon la fin de chaque release laisse une petite marche (clic).
            val isLastBlock = envelope.isFinished() ||
                (envelope.stage == EnvelopeGenerator.Stage.RELEASE &&
                    max(envStart, envEnd) * velocityGain < VOICE_CULL_THRESHOLD)
            if (isLastBlock) envEnd = 0f

            // sampleModes 3: the loop only plays while the key is held, then the sample's tail
            val looping = hasValidLoop && !(reg.loopUntilRelease && loopReleased)

            // LFOs: O(1) per block via processBlock (no per-sample loop)
            val vibLfoValue = if (hasVibLfo) vibratoLfo.processBlock(blockLen) else 0f
            val modLfoValue = if (hasModLfo) modulationLfo.processBlock(blockLen) else 0f

            // Modulation envelope (once per block)
            val modEnvValue = if (hasModEnv) modEnvelope.processBlock(blockLen) else 0f

            // Pitch bend smoothing (approximate N exponential steps as single larger step)
            // coeff * blockLen ≈ 1 - (1-coeff)^blockLen for small coeff (0.002 * 64 = 0.128 ≈ exact 0.120)
            if (smoothedPitchBend.isNaN()) {
                smoothedPitchBend = pitchBendSemitones
            } else {
                val pbCoeff = (pitchBendSmoothingCoeff * blockLen).coerceAtMost(1f)
                smoothedPitchBend += pbCoeff * (pitchBendSemitones - smoothedPitchBend)
            }

            // Pitch modulation (combined: vibrato LFO + mod LFO + mod wheel + mod envelope + pitch bend)
            // channelModulation is pre-scaled to semitones (rawCC1 × depthRange), so multiply by 100 for cents.
            // SF2 default modulator: CC1 drives the vibrato LFO's pitch depth.
            val modWheelVibratoCents = vibLfoValue * channelModulation * 100f
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
            if ((modLfoToFilterCents != 0 || modEnvToFilterCents != 0) && filter.isActive()) {
                val filterModFactor = PitchLookupTable.centsToFactor(totalFilterModCents)
                filter.modulateCutoff(filterModFactor)
            }

            // Niveaux gauche/droite en fin de bloc (tout sauf l'échantillon), puis rampe
            // linéaire par échantillon depuis la fin du bloc précédent : enveloppe, volume de
            // canal, trémolo et panoramique évoluent sans escalier.
            val channelGain = startChannelGain +
                (targetChannelGain - startChannelGain) * ((blockEnd - startFrame) / renderSpan)
            val blockGain = staticGain * channelGain * volumeModFactor
            val levelLeftEnd = blockGain * envEnd * effectivePanLeft
            val levelRightEnd = blockGain * envEnd * effectivePanRight
            var levelLeft = if (lastLevelLeft < 0f) blockGain * envStart * effectivePanLeft else lastLevelLeft
            var levelRight = if (lastLevelRight < 0f) blockGain * envStart * effectivePanRight else lastLevelRight
            val incLeft = (levelLeftEnd - levelLeft) / blockLen
            val incRight = (levelRightEnd - levelRight) / blockLen
            lastLevelLeft = levelLeftEnd
            lastLevelRight = levelRightEnd

            // --- Inner loop: per-sample processing (tight loop) ---
            for (i in offset until blockEnd) {
                // Sample interpolation (cubic or linear depending on quality mode)
                var sample = getSampleInterpolated(sf2File, reg, looping)

                // SF2 low-pass timbre filter (per-sample, IIR state-dependent)
                sample = filter.process(sample)

                // Declick ramp when an audible slot had to be reused directly
                if (declickSamplesRemaining > 0) {
                    val rampProgress = 1f - (declickSamplesRemaining.toFloat() / declickDurationSamples)
                    sample *= rampProgress * rampProgress * (3f - 2f * rampProgress)
                    declickSamplesRemaining--
                }

                // NaN/Inf protection
                if (sample.isNaN() || sample.isInfinite()) sample = 0f

                levelLeft += incLeft
                levelRight += incRight
                val outL = sample * levelLeft
                val outR = sample * levelRight

                // Mix into output
                outputLeft[i] += outL
                outputRight[i] += outR

                // Direct reverb send
                if (hasReverbSend) {
                    reverbSendLeft[i] += outL * reverbSend
                    reverbSendRight[i] += outR * reverbSend
                }

                // Direct chorus send
                if (hasChorusSend) {
                    chorusSendLeft[i] += outL * chorusSend
                    chorusSendRight[i] += outR * chorusSend
                }

                // Advance sample position (constant rate within block)
                samplePosition += effectiveRate

                // Loop/end handling (pre-computed loop parameters, O(1) modulo wrap)
                if (looping) {
                    if (samplePosition >= loopRelativeEnd) {
                        samplePosition = loopRelativeStart + ((samplePosition - loopRelativeStart) % loopLength)
                        // Safety: floating-point modulo may land exactly at end
                        if (samplePosition >= loopRelativeEnd) samplePosition = loopRelativeStart
                        hasWrapped = true
                    }
                } else if (samplePosition >= sampleLength) {
                    isActive = false
                    return
                }
            }

            if (isLastBlock) {
                isActive = false
                return
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
     * While looping, the points read around the loop boundaries wrap inside the loop
     * (like FluidSynth's guard points): after loopEnd comes loopStart, and once the loop
     * has been played through, before loopStart comes the end of the loop.
     */
    private fun getSampleInterpolated(sf2File: Sf2File, region: Sf2Region, looping: Boolean): Float {
        val absolutePos = region.sampleStart + samplePosition.toLong()
        val frac = (samplePosition - samplePosition.toLong()).toFloat()

        if (looping && (absolutePos + 2 >= region.loopEnd || (hasWrapped && absolutePos - 1 < region.loopStart))) {
            return getLoopBoundarySample(sf2File, region, absolutePos, frac)
        }

        // Normal path: safely within loop interior or no loop
        if (lowQualityMode) {
            val s1 = sf2File.getSample(absolutePos)
            val s2 = sf2File.getSample(absolutePos + 1)
            return s1 + (s2 - s1) * frac
        }

        // Cubic Hermite interpolation (Catmull-Rom spline) via pre-computed lookup table
        // Table replaces inline coefficient calculation (~16 float ops) with 4 multiply-adds
        val s0 = sf2File.getSample(absolutePos - 1)
        val s1 = sf2File.getSample(absolutePos)
        val s2 = sf2File.getSample(absolutePos + 1)
        val s3 = sf2File.getSample(absolutePos + 2)

        return PitchLookupTable.cubicInterpolate(s0, s1, s2, s3, frac)
    }

    /**
     * Loop-boundary-aware sample interpolation: indices past loopEnd wrap to loopStart,
     * and (once looped) indices before loopStart wrap to the end of the loop.
     */
    private fun getLoopBoundarySample(sf2File: Sf2File, region: Sf2Region, index: Long, frac: Float): Float {
        val loopLen = region.loopEnd - region.loopStart
        if (loopLen <= 0) return sf2File.getSample(index)

        fun wrap(idx: Long): Long = when {
            idx >= region.loopEnd -> region.loopStart + (idx - region.loopEnd) % loopLen
            hasWrapped && idx < region.loopStart -> region.loopEnd - 1 - (region.loopStart - 1 - idx) % loopLen
            else -> idx
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
        isQuickFading = false
        startDelayFrames = 0
        pendingReleaseFrame = -1
        lastChannelGain = -1f
        lastLevelLeft = -1f
        lastLevelRight = -1f
        loopReleased = false
        hasWrapped = false
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
