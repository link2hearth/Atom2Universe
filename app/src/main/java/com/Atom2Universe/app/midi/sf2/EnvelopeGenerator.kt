package com.Atom2Universe.app.midi.sf2

import kotlin.math.max
import kotlin.math.pow

/**
 * Real-time DAHDSR envelope generator for SF2 synthesis, following SoundFont 2.01 §8.1.2-8.1.3.
 *
 * Envelope stages:
 * - DELAY: Wait before attack starts
 * - ATTACK: Ramp from 0 to 1, linear in amplitude
 * - HOLD: Stay at 1
 * - DECAY: Fall towards the sustain level
 * - SUSTAIN: Hold at sustain level (indefinitely until release)
 * - RELEASE: Fall from the current level to silence
 * - FINISHED: Envelope complete, voice can be freed
 *
 * Volume envelope ([isVolume] = true): decay and release are linear in decibels. The decay
 * and release times are the times of a full 100 dB fall, so a fall from a lower level is
 * proportionally shorter. A volume envelope whose sustain is silent ends once it gets there.
 *
 * Modulation envelope ([isVolume] = false): every stage is linear in value, the release time
 * being the time of a full 1 → 0 fall.
 */
class EnvelopeGenerator(
    private val sampleRate: Int = 44100,
    private val isVolume: Boolean = true
) {
    companion object {
        // Minimum volume release (-7200 timecents = 15.6 ms), the same guard as FluidSynth:
        // shorter releases allowed by the standard (down to 1 ms) only produce clicks
        const val MIN_RELEASE_TIME = 0.015625f
        // Quick fade time for voice stealing and exclusive classes (prevents clicks)
        const val QUICK_FADE_TIME = 0.015f  // 15ms quick fade

        // 100 dB below full scale: the volume envelope's silence
        const val SILENCE_LEVEL = 0.00001f

        /** Per-sample factor of a fall of 100 dB in [samples] samples. */
        private fun dbFallCoefficient(samples: Int): Float =
            if (samples <= 1) 0f else 10.0.pow(-5.0 / samples).toFloat()
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
    private var attackSamples: Int = 1
    private var holdSamples: Int = 0
    private var decaySamples: Int = 1
    private var releaseSamples: Int = 1

    // Sustain level as an amplitude (volume) or a value (modulation)
    private var sustainLevel: Float = 1f

    // State
    var stage: Stage = Stage.IDLE
        private set

    private var sampleCounter: Int = 0
    private var currentLevel: Float = 0f

    // Per-sample steps: multiplicative for the volume envelope (dB-linear), additive otherwise
    private var attackStep: Float = 1f
    private var decayStep: Float = 0f
    private var releaseStep: Float = 0f

    /**
     * Configures the envelope with the given parameters.
     * All times are in seconds; the sustain is a normalized level (1 = full, 0 = none).
     * @param envelope The envelope parameters
     * @param midiNote MIDI note for key tracking of hold and decay (60 = unchanged)
     */
    fun configure(envelope: VolumeEnvelope, midiNote: Int = 60) {
        // Key tracking (keynumToHold / keynumToDecay): timecents per key, applied as
        // (60 - key) * value. Positive values shorten hold / decay for higher keys.
        val keyDelta = 60 - midiNote
        val holdTime = applyKeyTracking(envelope.hold, keyDelta, envelope.keynumToHold)
        val decayTime = applyKeyTracking(envelope.decay, keyDelta, envelope.keynumToDecay)
        val releaseTime = if (isVolume) max(envelope.release, MIN_RELEASE_TIME) else envelope.release

        delaySamples = (envelope.delay * sampleRate).toInt()
        attackSamples = max(1, (envelope.attack * sampleRate).toInt())
        holdSamples = (holdTime * sampleRate).toInt()
        decaySamples = max(1, (decayTime * sampleRate).toInt())
        releaseSamples = max(1, (releaseTime * sampleRate).toInt())

        val sustain = envelope.sustain.coerceIn(0f, 1f)
        sustainLevel = if (isVolume) {
            // Normalized level -> amplitude over a 100 dB range; 0 = silence
            if (sustain <= 0f) 0f else 10.0.pow(-5.0 * (1.0 - sustain)).toFloat()
        } else {
            sustain
        }

        attackStep = 1f / attackSamples
        if (isVolume) {
            decayStep = dbFallCoefficient(decaySamples)
            releaseStep = dbFallCoefficient(releaseSamples)
        } else {
            decayStep = 1f / decaySamples
            releaseStep = 1f / releaseSamples
        }
    }

    /**
     * Applies key tracking to a time value.
     * @param baseTime Base time in seconds
     * @param keyDelta 60 - MIDI key
     * @param timecentsPerKey Key tracking amount in timecents per key
     * @return Adjusted time in seconds
     */
    private fun applyKeyTracking(baseTime: Float, keyDelta: Int, timecentsPerKey: Int): Float {
        if (timecentsPerKey == 0 || keyDelta == 0) return baseTime
        val multiplier = 2.0.pow(keyDelta * timecentsPerKey / 1200.0).toFloat()
        return (baseTime * multiplier).coerceIn(0.001f, 100f)
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
        startRelease()
    }

    /**
     * Releases the envelope with a capped release time.
     * Used when polyphony is very high to prevent voice buildup.
     * @param maxReleaseSamples Maximum release time in samples
     */
    fun releaseWithCap(maxReleaseSamples: Int) {
        if (stage == Stage.FINISHED || stage == Stage.RELEASE) return
        if (releaseSamples > maxReleaseSamples) setReleaseSamples(maxReleaseSamples)
        startRelease()
    }

    /**
     * Quick fade for voice stealing - fast but click-free fadeout.
     * Uses a much shorter release time than normal release.
     */
    fun quickFade() {
        if (stage == Stage.FINISHED) return
        setReleaseSamples(max(1, (QUICK_FADE_TIME * sampleRate).toInt()))
        startRelease()
    }

    /**
     * Forces emergency release on an envelope already in release stage.
     * Used when voice count is critical and we need to quickly clear resonating sounds.
     * Only shortens the release if it's longer than the target.
     * @param targetSamples Maximum release time in samples
     */
    fun forceEmergencyRelease(targetSamples: Int) {
        if (stage != Stage.RELEASE) return
        if (releaseSamples > targetSamples) setReleaseSamples(targetSamples)
    }

    private fun setReleaseSamples(samples: Int) {
        releaseSamples = max(1, samples)
        releaseStep = if (isVolume) dbFallCoefficient(releaseSamples) else 1f / releaseSamples
    }

    private fun startRelease() {
        stage = Stage.RELEASE
        sampleCounter = 0
    }

    /**
     * Gets the current envelope level (0.0 to 1.0): an amplitude for the volume envelope,
     * a modulation value otherwise.
     */
    fun getLevel(): Float = currentLevel

    /**
     * Processes one sample and returns the envelope level.
     */
    fun process(): Float {
        when (stage) {
            Stage.IDLE, Stage.FINISHED -> {
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
                // Linear rise in amplitude (SF2 §8.1.3, attackVolEnv)
                currentLevel += attackStep
                sampleCounter++
                if (sampleCounter >= attackSamples || currentLevel >= 1f) {
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
                currentLevel = if (isVolume) currentLevel * decayStep else currentLevel - decayStep
                if (currentLevel <= sustainLevel) {
                    currentLevel = sustainLevel
                    stage = Stage.SUSTAIN
                }
                if (isVolume && currentLevel <= SILENCE_LEVEL) {
                    // Sustain at (or below) -100 dB: the note has died out
                    currentLevel = 0f
                    stage = Stage.FINISHED
                }
            }

            Stage.SUSTAIN -> {
                currentLevel = sustainLevel
                // Stay here until release() is called
            }

            Stage.RELEASE -> {
                currentLevel = if (isVolume) currentLevel * releaseStep else currentLevel - releaseStep
                sampleCounter++
                val silent = if (isVolume) currentLevel <= SILENCE_LEVEL else currentLevel <= 0f
                if (silent || sampleCounter >= releaseSamples) {
                    currentLevel = 0f
                    stage = Stage.FINISHED
                }
            }
        }

        return currentLevel
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
}
