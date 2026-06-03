package com.Atom2Universe.app.midi.sf2

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Real-time ADSR envelope generator for SF2 synthesis.
 * Generates amplitude envelope values sample-by-sample.
 *
 * Envelope stages:
 * - DELAY: Wait before attack starts (usually 0)
 * - ATTACK: Ramp from 0 to 1
 * - HOLD: Stay at 1
 * - DECAY: Ramp from 1 to sustain level
 * - SUSTAIN: Hold at sustain level (indefinitely until release)
 * - RELEASE: Ramp from current level to 0
 * - FINISHED: Envelope complete, voice can be freed
 */
class EnvelopeGenerator(
    private val sampleRate: Int = 44100
) {
    companion object {
        // Minimum release time in seconds (ensures short notes still ring out)
        const val MIN_RELEASE_TIME = 0.05f  // 50ms minimum release
        // Quick fade time for voice stealing (prevents clicks)
        const val QUICK_FADE_TIME = 0.015f  // 15ms quick fade
        // Maximum release time in seconds (prevents resonant sounds from accumulating)
        // Raised from 2.0 to 3.5 - balanced between natural decay and voice buildup prevention
        // The voice pool's release cap and emergency fade handle buildup at high voice counts
        const val MAX_RELEASE_TIME = 3.5f

        // BUG FIX 1.14: Lookup table pour les coefficients exponentiels d'enveloppe.
        // Remplace exp(-5f / samples) par une table pre-calculee pour economiser ~2-3% CPU.
        // Table couvre des durees de 1 a EXP_TABLE_SIZE samples (suffisant pour la plupart des enveloppes).
        // Pour des valeurs plus grandes, on retombe sur le calcul direct (rare).
        private const val EXP_TABLE_SIZE = 8192  // Couvre ~170ms a 48kHz
        private val expCoefficientTable = FloatArray(EXP_TABLE_SIZE + 1) { samples ->
            if (samples <= 0) 1f else exp(-5f / samples)
        }

        /**
         * Lookup rapide pour le coefficient exponentiel.
         * Utilise la table pour les petites valeurs, calcul direct sinon.
         */
        fun lookupExpCoefficient(samples: Int): Float {
            return if (samples in 1..EXP_TABLE_SIZE) {
                expCoefficientTable[samples]
            } else if (samples <= 0) {
                1f
            } else {
                // Valeurs > EXP_TABLE_SIZE: calcul direct (rare, longues releases)
                exp(-5f / samples)
            }
        }
    }

    enum class Stage {
        IDLE,
        DELAY,
        ATTACK,
        HOLD,
        DECAY,
        SUSTAIN,
        RELEASE,
        FINISHED
    }

    // Envelope parameters (in samples)
    private var delaySamples: Int = 0
    private var attackSamples: Int = 0
    private var holdSamples: Int = 0
    private var decaySamples: Int = 0
    private var releaseSamples: Int = 0
    private var sustainLevel: Float = 1f

    // State
    var stage: Stage = Stage.IDLE
        private set

    private var sampleCounter: Int = 0
    private var currentLevel: Float = 0f

    // Coefficients for exponential curves
    private var attackCoeff: Float = 0f
    private var decayCoeff: Float = 0f
    private var releaseCoeff: Float = 0f

    /**
     * Configures the envelope with the given parameters.
     * All times are in seconds.
     * @param envelope The envelope parameters
     * @param midiNote Optional MIDI note for key tracking (60 = middle C)
     */
    fun configure(envelope: VolumeEnvelope?, midiNote: Int = 60) {
        if (envelope == null) {
            // SF2 default modulation envelope: quick attack/decay to 0, not sustained
            // This prevents filter modulation from staying at max indefinitely
            delaySamples = 0
            attackSamples = (0.001f * sampleRate).toInt()
            holdSamples = 0
            decaySamples = (0.1f * sampleRate).toInt()  // 100ms decay to 0
            sustainLevel = 0f  // Decay to 0, not stay at max!
            releaseSamples = (MIN_RELEASE_TIME * sampleRate).toInt()
        } else {
            // Apply key tracking to hold and decay times
            // Formula: newTime = baseTime * 2^((midiNote - 60) * timecentsPerKey / 1200)
            // Positive timecentsPerKey = higher notes have shorter times (realistic for acoustic instruments)
            val keyDelta = midiNote - 60 // Semitones from middle C

            val holdTime = applyKeyTracking(envelope.hold, keyDelta, envelope.keynumToHold)
            val decayTime = applyKeyTracking(envelope.decay, keyDelta, envelope.keynumToDecay)

            // Apply minimum and MAXIMUM release time
            // Maximum prevents resonant sounds (bells, pads) from accumulating too many voices
            val releaseTime = envelope.release.coerceIn(MIN_RELEASE_TIME, MAX_RELEASE_TIME)

            delaySamples = (envelope.delay * sampleRate).toInt()
            attackSamples = max(1, (envelope.attack * sampleRate).toInt())
            holdSamples = (holdTime * sampleRate).toInt()
            decaySamples = max(1, (decayTime * sampleRate).toInt())
            sustainLevel = envelope.sustain.coerceIn(0f, 1f)
            releaseSamples = max(1, (releaseTime * sampleRate).toInt())
        }

        // Calculate exponential coefficients
        // Using exponential curves: y = 1 - e^(-t/tau) for attack, y = e^(-t/tau) for decay/release
        attackCoeff = calculateCoefficient(attackSamples)
        decayCoeff = calculateCoefficient(decaySamples)
        releaseCoeff = calculateCoefficient(releaseSamples)
    }

    /**
     * Applies key tracking to a time value.
     * @param baseTime Base time in seconds
     * @param keyDelta Semitones from middle C (positive = higher note)
     * @param timecentsPerKey Key tracking amount in timecents per semitone
     * @return Adjusted time in seconds
     */
    private fun applyKeyTracking(baseTime: Float, keyDelta: Int, timecentsPerKey: Int): Float {
        if (timecentsPerKey == 0 || keyDelta == 0) return baseTime

        // Calculate timecents adjustment
        val timecentsAdjust = keyDelta * timecentsPerKey

        // Convert to multiplier: 2^(timecents/1200)
        val multiplier = 2.0.pow(timecentsAdjust / 1200.0).toFloat()

        // Apply to base time, with sensible limits
        val adjustedTime = baseTime * multiplier

        // Clamp to reasonable range (0.001s to 100s)
        return adjustedTime.coerceIn(0.001f, 100f)
    }

    /**
     * Calculates the exponential coefficient for a given number of samples.
     * The coefficient produces ~99.3% of the target after 'samples' iterations.
     * BUG FIX 1.14: Utilise une lookup table pour eviter exp() couteux.
     */
    private fun calculateCoefficient(samples: Int): Float {
        return lookupExpCoefficient(samples)
    }

    /**
     * Triggers the envelope (note on).
     */
    fun trigger() {
        stage = if (delaySamples > 0) Stage.DELAY else Stage.ATTACK
        sampleCounter = 0
        currentLevel = 0f
    }

    /**
     * Releases the envelope (note off).
     */
    fun release() {
        if (stage == Stage.FINISHED || stage == Stage.RELEASE) return

        stage = Stage.RELEASE
        sampleCounter = 0
    }

    /**
     * Releases the envelope with a capped release time.
     * Used when polyphony is very high to prevent voice buildup.
     * @param maxReleaseSamples Maximum release time in samples
     */
    fun releaseWithCap(maxReleaseSamples: Int) {
        if (stage == Stage.FINISHED || stage == Stage.RELEASE) return

        // Cap the release time if it exceeds the maximum
        if (releaseSamples > maxReleaseSamples) {
            releaseSamples = maxReleaseSamples
            releaseCoeff = calculateCoefficient(releaseSamples)
        }

        stage = Stage.RELEASE
        sampleCounter = 0
    }

    /**
     * Quick fade for voice stealing - fast but click-free fadeout.
     * Uses a much shorter release time than normal release.
     */
    fun quickFade() {
        if (stage == Stage.FINISHED) return

        // Override release time to quick fade
        releaseSamples = max(1, (QUICK_FADE_TIME * sampleRate).toInt())
        releaseCoeff = calculateCoefficient(releaseSamples)

        stage = Stage.RELEASE
        sampleCounter = 0
    }

    /**
     * Forces emergency release on an envelope already in release stage.
     * Used when voice count is critical and we need to quickly clear resonating sounds.
     * Only shortens the release if it's longer than the target.
     * @param targetSamples Maximum release time in samples
     */
    fun forceEmergencyRelease(targetSamples: Int) {
        if (stage != Stage.RELEASE) return

        // Calculate how many samples are left in the current release
        val samplesRemaining = releaseSamples - sampleCounter

        // Only shorten if there's more time remaining than our target
        if (samplesRemaining > targetSamples) {
            // Reset to start a new, shorter release from current level
            releaseSamples = targetSamples
            releaseCoeff = calculateCoefficient(releaseSamples)
            sampleCounter = 0
        }
    }

    /**
     * Gets the current envelope level (0.0 to 1.0).
     */
    fun getLevel(): Float = currentLevel

    /**
     * Processes one sample and returns the envelope level.
     */
    fun process(): Float {
        when (stage) {
            Stage.IDLE -> {
                currentLevel = 0f
            }

            Stage.DELAY -> {
                currentLevel = 0f
                sampleCounter++
                if (sampleCounter >= delaySamples) {
                    stage = Stage.ATTACK
                    sampleCounter = 0
                }
            }

            Stage.ATTACK -> {
                // Exponential attack curve
                currentLevel = 1f - (1f - currentLevel) * attackCoeff
                sampleCounter++
                if (sampleCounter >= attackSamples || currentLevel >= 0.999f) {
                    currentLevel = 1f
                    stage = if (holdSamples > 0) Stage.HOLD else Stage.DECAY
                    sampleCounter = 0
                }
            }

            Stage.HOLD -> {
                currentLevel = 1f
                sampleCounter++
                if (sampleCounter >= holdSamples) {
                    stage = Stage.DECAY
                    sampleCounter = 0
                }
            }

            Stage.DECAY -> {
                // Exponential decay to sustain level
                val target = sustainLevel
                currentLevel = target + (currentLevel - target) * decayCoeff
                // Anti-denormal: prevent tiny residual from exponential approach
                val diff = currentLevel - target
                if (diff > 0f && diff < 1e-20f) currentLevel = target
                sampleCounter++
                if (sampleCounter >= decaySamples || currentLevel <= sustainLevel + 0.001f) {
                    currentLevel = sustainLevel
                    stage = Stage.SUSTAIN
                }
            }

            Stage.SUSTAIN -> {
                currentLevel = sustainLevel
                // Stay here until release() is called
            }

            Stage.RELEASE -> {
                // Exponential release to 0
                currentLevel = currentLevel * releaseCoeff
                // Anti-denormal: zero out tiny values to prevent denormalized floats
                // that cause CPU spikes. The exponential decay can produce very small
                // numbers that stay above 0 but below normal float range.
                if (currentLevel > 0f && currentLevel < 1e-20f) currentLevel = 0f
                sampleCounter++
                if (sampleCounter >= releaseSamples || currentLevel < 0.0001f) {
                    currentLevel = 0f
                    stage = Stage.FINISHED
                }
            }

            Stage.FINISHED -> {
                currentLevel = 0f
            }
        }

        return currentLevel
    }

    /**
     * Processes multiple samples and fills the output array with envelope levels.
     */
    fun process(output: FloatArray, offset: Int = 0, length: Int = output.size - offset) {
        val end = min(offset + length, output.size)
        for (i in offset until end) {
            output[i] = process()
        }
    }

    /**
     * Advances the envelope by blockSize samples and returns the final level.
     * Used for block-based rendering: compute level at block boundaries,
     * then linearly interpolate within the block for smooth amplitude.
     *
     * Optimized for the common SUSTAIN stage (zero work: just returns level).
     * For transitional stages (attack, decay, release), falls back to per-sample.
     */
    fun processBlock(blockSize: Int): Float {
        when (stage) {
            Stage.IDLE, Stage.FINISHED -> return currentLevel
            Stage.SUSTAIN -> return currentLevel  // Most common: notes held, no work
            else -> {
                // Transitional stages: process per-sample (handles stage transitions mid-block)
                repeat(blockSize) { process() }
                return currentLevel
            }
        }
    }

    /**
     * Returns true if the envelope has finished (level is 0 and stage is FINISHED).
     */
    fun isFinished(): Boolean = stage == Stage.FINISHED

    /**
     * Returns true if the envelope is currently active (not idle or finished).
     */
    fun isActive(): Boolean = stage != Stage.IDLE && stage != Stage.FINISHED

    /**
     * Resets the envelope to idle state.
     */
    fun reset() {
        stage = Stage.IDLE
        sampleCounter = 0
        currentLevel = 0f
    }

    /**
     * Creates a copy of this envelope generator with the same configuration.
     */
    fun copy(): EnvelopeGenerator {
        val copy = EnvelopeGenerator(sampleRate)
        copy.delaySamples = this.delaySamples
        copy.attackSamples = this.attackSamples
        copy.holdSamples = this.holdSamples
        copy.decaySamples = this.decaySamples
        copy.releaseSamples = this.releaseSamples
        copy.sustainLevel = this.sustainLevel
        copy.attackCoeff = this.attackCoeff
        copy.decayCoeff = this.decayCoeff
        copy.releaseCoeff = this.releaseCoeff
        return copy
    }
}
