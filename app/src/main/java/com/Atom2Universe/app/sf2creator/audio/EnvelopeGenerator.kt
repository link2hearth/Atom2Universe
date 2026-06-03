package com.Atom2Universe.app.sf2creator.audio

import com.Atom2Universe.app.BuildConfig
import kotlin.math.exp

/**
 * ADSR Envelope Generator for shaping sample dynamics.
 *
 * Supports:
 * - Standard ADSR (Attack, Decay, Sustain, Release)
 * - Exponential and linear curves
 * - Presets for common instrument types
 */
class EnvelopeGenerator(private val sampleRate: Int = 44100) {

    data class ADSRSettings(
        val attackMs: Float = 10f,       // Time to reach peak (0-5000ms)
        val decayMs: Float = 100f,       // Time to reach sustain level (0-5000ms)
        val sustainLevel: Float = 0.7f,  // Sustain level (0-1)
        val releaseMs: Float = 200f,     // Time to reach zero after note off (0-5000ms)
        val attackCurve: CurveType = CurveType.EXPONENTIAL,
        val decayCurve: CurveType = CurveType.EXPONENTIAL,
        val releaseCurve: CurveType = CurveType.EXPONENTIAL,
        val requiresLoop: Boolean = true // Whether this preset needs loop for sustained sound
    )

    enum class CurveType {
        LINEAR,      // Straight line
        EXPONENTIAL, // Natural decay/attack
        LOGARITHMIC  // Fast start, slow finish
    }

    /**
     * Preset envelopes for common instrument types.
     */
    object Presets {
        // No envelope - sample plays as-is with full volume throughout
        val NONE = ADSRSettings(
            attackMs = 0f,
            decayMs = 0f,
            sustainLevel = 1.0f,
            releaseMs = 0f,
            requiresLoop = false
        )

        val PIANO = ADSRSettings(
            attackMs = 5f,
            decayMs = 2000f,      // Long natural decay
            sustainLevel = 0.0f,  // Piano doesn't sustain - plays sample naturally
            releaseMs = 300f,
            requiresLoop = false  // One-shot, no loop needed
        )

        val ORGAN = ADSRSettings(
            attackMs = 20f,
            decayMs = 50f,
            sustainLevel = 1.0f,  // Full sustain - needs loop
            releaseMs = 100f,
            requiresLoop = true
        )

        val PAD = ADSRSettings(
            attackMs = 500f,      // Slow attack
            decayMs = 2000f,      // Long decay
            sustainLevel = 0.0f,  // One-shot with slow envelope
            releaseMs = 1000f,    // Long release
            requiresLoop = false  // One-shot, no loop needed
        )

        val PLUCK = ADSRSettings(
            attackMs = 1f,        // Instant attack
            decayMs = 500f,       // Medium decay
            sustainLevel = 0.0f,  // No sustain - plays and fades
            releaseMs = 100f,
            requiresLoop = false  // One-shot, no loop needed
        )

        val STRINGS = ADSRSettings(
            attackMs = 200f,
            decayMs = 100f,
            sustainLevel = 0.9f,  // Needs loop for sustained bowing
            releaseMs = 400f,
            requiresLoop = true
        )

        val BRASS = ADSRSettings(
            attackMs = 50f,
            decayMs = 100f,
            sustainLevel = 0.8f,
            releaseMs = 150f,
            requiresLoop = true
        )

        val PERCUSSION = ADSRSettings(
            attackMs = 0f,        // Instant
            decayMs = 200f,
            sustainLevel = 0.0f,  // No sustain
            releaseMs = 50f,
            requiresLoop = false  // One-shot
        )

        val VOICE = ADSRSettings(
            attackMs = 30f,
            decayMs = 50f,
            sustainLevel = 0.85f,
            releaseMs = 200f
        )

        val SYNTH_LEAD = ADSRSettings(
            attackMs = 5f,
            decayMs = 100f,
            sustainLevel = 0.7f,
            releaseMs = 150f
        )

        val AMBIENT = ADSRSettings(
            attackMs = 1000f,     // Very slow attack
            decayMs = 500f,
            sustainLevel = 0.6f,
            releaseMs = 2000f     // Very long release
        )
    }

    private enum class Phase {
        ATTACK, DECAY, SUSTAIN, RELEASE, OFF
    }

    /**
     * Generate an ADSR envelope curve.
     *
     * @param totalSamples Total number of samples for the envelope
     * @param noteOnDuration Duration of note-on in samples (after this, release begins)
     * @param settings ADSR parameters
     * @return FloatArray of envelope values (0-1)
     */
    fun generateEnvelope(
        totalSamples: Int,
        noteOnDuration: Int,
        settings: ADSRSettings
    ): FloatArray {
        val envelope = FloatArray(totalSamples)

        val attackSamples = (settings.attackMs * sampleRate / 1000).toInt()
        val decaySamples = (settings.decayMs * sampleRate / 1000).toInt()
        val releaseSamples = (settings.releaseMs * sampleRate / 1000).toInt()

        var level: Float
        var phase = Phase.ATTACK
        var phasePosition = 0

        for (i in 0 until totalSamples) {
            // Check for release trigger
            if (i >= noteOnDuration && phase != Phase.RELEASE && phase != Phase.OFF) {
                phase = Phase.RELEASE
                phasePosition = 0
            }

            when (phase) {
                Phase.ATTACK -> {
                    if (attackSamples > 0) {
                        val progress = phasePosition.toFloat() / attackSamples
                        level = applyCurve(progress, settings.attackCurve, true)
                    } else {
                        level = 1f
                    }

                    if (phasePosition >= attackSamples || level >= 1f) {
                        level = 1f
                        phase = Phase.DECAY
                        phasePosition = 0
                    } else {
                        phasePosition++
                    }
                }

                Phase.DECAY -> {
                    if (decaySamples > 0 && settings.sustainLevel < 1f) {
                        val progress = phasePosition.toFloat() / decaySamples
                        val decayAmount = 1f - settings.sustainLevel
                        level = 1f - decayAmount * applyCurve(progress, settings.decayCurve, false)
                    } else {
                        level = settings.sustainLevel
                    }

                    if (phasePosition >= decaySamples || level <= settings.sustainLevel) {
                        level = settings.sustainLevel
                        phase = Phase.SUSTAIN
                        phasePosition = 0
                    } else {
                        phasePosition++
                    }
                }

                Phase.SUSTAIN -> {
                    level = settings.sustainLevel
                    // Stay in sustain until note off (handled at loop start)
                }

                Phase.RELEASE -> {
                    val startLevel = envelope.getOrNull(i - 1) ?: settings.sustainLevel

                    if (releaseSamples > 0) {
                        val progress = phasePosition.toFloat() / releaseSamples
                        level = startLevel * (1f - applyCurve(progress, settings.releaseCurve, false))
                    } else {
                        level = 0f
                    }

                    if (phasePosition >= releaseSamples || level <= 0.001f) {
                        level = 0f
                        phase = Phase.OFF
                    } else {
                        phasePosition++
                    }
                }

                Phase.OFF -> {
                    level = 0f
                }
            }

            envelope[i] = level.coerceIn(0f, 1f)
        }

        return envelope
    }

    /**
     * Apply envelope to audio samples.
     *
     * @param samples Audio samples
     * @param settings ADSR settings
     * @param noteOnDurationMs Duration of note-on in milliseconds
     * @return Samples with envelope applied
     */
    fun applyEnvelope(
        samples: ShortArray,
        settings: ADSRSettings,
        noteOnDurationMs: Int? = null
    ): ShortArray {
        if (samples.isEmpty()) return samples

        // If no note-on duration specified, use sample length minus release time
        val releaseMs = settings.releaseMs.toInt()
        val sampleDurationMs = samples.size * 1000 / sampleRate
        val noteOnMs = noteOnDurationMs ?: (sampleDurationMs - releaseMs).coerceAtLeast(100)
        val noteOnSamples = (noteOnMs * sampleRate / 1000)

        val envelope = generateEnvelope(samples.size, noteOnSamples, settings)

        if (BuildConfig.DEBUG) {
            android.util.Log.d("EnvelopeGenerator", "applyEnvelope: sampleDuration=${sampleDurationMs}ms, noteOnMs=${noteOnMs}ms, noteOnSamples=$noteOnSamples")
            val maxEnv = envelope.maxOrNull() ?: 0f
            val minEnv = envelope.minOrNull() ?: 0f
            val midEnv = if (envelope.isNotEmpty()) envelope[envelope.size / 2] else 0f
            val endEnv = if (envelope.isNotEmpty()) envelope.last() else 0f
            android.util.Log.d("EnvelopeGenerator", "Envelope: max=$maxEnv, min=$minEnv, mid=$midEnv, end=$endEnv")
            val timePoints = listOf(10, 100, 500, 1000, 2000, 3000)
            for (ms in timePoints) {
                val sampleIndex = (ms * sampleRate / 1000).coerceIn(0, envelope.size - 1)
                if (sampleIndex < envelope.size) {
                    android.util.Log.d("EnvelopeGenerator", "Envelope at ${ms}ms: ${envelope[sampleIndex]}")
                }
            }
        }

        return ShortArray(samples.size) { i ->
            (samples[i] * envelope[i]).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    /**
     * Apply curve transformation to a linear progress value.
     */
    private fun applyCurve(progress: Float, curveType: CurveType, isRising: Boolean): Float {
        val p = progress.coerceIn(0f, 1f)

        return when (curveType) {
            CurveType.LINEAR -> p

            CurveType.EXPONENTIAL -> {
                if (isRising) {
                    // Exponential rise: starts slow, accelerates
                    1f - exp(-5f * p)
                } else {
                    // Exponential decay: starts fast, slows down
                    1f - exp(-5f * p)
                }
            }

            CurveType.LOGARITHMIC -> {
                if (isRising) {
                    // Logarithmic rise: starts fast, slows down
                    if (p <= 0f) 0f else kotlin.math.log10(p * 9f + 1f)
                } else {
                    // Logarithmic decay: starts slow, accelerates
                    if (p >= 1f) 1f else 1f - kotlin.math.log10((1f - p) * 9f + 1f)
                }
            }
        }
    }

    /**
     * Suggest an envelope preset based on sample characteristics.
     */
    fun suggestPreset(samples: ShortArray): ADSRSettings {
        if (samples.isEmpty()) return Presets.PIANO

        // Analyze attack characteristics
        val attackInfo = analyzeAttack(samples)

        // Analyze sustain characteristics
        val hasSustain = analyzeHasSustain(samples)

        // Choose preset based on analysis
        return when {
            attackInfo.isPercussive && !hasSustain -> Presets.PERCUSSION
            attackInfo.isPercussive && hasSustain -> Presets.PLUCK
            !attackInfo.isPercussive && hasSustain -> Presets.ORGAN
            attackInfo.attackTimeMs > 200 -> Presets.PAD
            attackInfo.attackTimeMs > 50 -> Presets.STRINGS
            else -> Presets.PIANO
        }
    }

    private data class AttackInfo(
        val attackTimeMs: Int,
        val isPercussive: Boolean
    )

    private fun analyzeAttack(samples: ShortArray): AttackInfo {
        if (samples.isEmpty()) return AttackInfo(10, false)

        // Find peak in first 500ms
        val searchSamples = (500 * sampleRate / 1000).coerceAtMost(samples.size)
        var peakValue = 0
        var peakIndex = 0

        for (i in 0 until searchSamples) {
            val absValue = kotlin.math.abs(samples[i].toInt())
            if (absValue > peakValue) {
                peakValue = absValue
                peakIndex = i
            }
        }

        val attackTimeMs = peakIndex * 1000 / sampleRate

        // Check if attack is percussive (reaches 90% of peak in < 10ms)
        val threshold = (peakValue * 0.9).toInt()
        var thresholdReachedIndex = 0
        for (i in 0 until searchSamples) {
            if (kotlin.math.abs(samples[i].toInt()) >= threshold) {
                thresholdReachedIndex = i
                break
            }
        }
        val attackTo90Ms = thresholdReachedIndex * 1000 / sampleRate
        val isPercussive = attackTo90Ms < 10

        return AttackInfo(attackTimeMs, isPercussive)
    }

    private fun analyzeHasSustain(samples: ShortArray): Boolean {
        if (samples.size < sampleRate) return false // Less than 1 second

        // Analyze the middle third of the sample
        val startIdx = samples.size / 3
        val endIdx = samples.size * 2 / 3

        // Calculate average energy in this region
        var energy = 0f
        for (i in startIdx until endIdx) {
            val normalized = samples[i].toFloat() / Short.MAX_VALUE
            energy += normalized * normalized
        }
        energy /= (endIdx - startIdx)

        // Calculate peak energy in attack region
        val attackEnd = (sampleRate / 10).coerceAtMost(samples.size) // First 100ms
        var peakEnergy = 0f
        for (i in 0 until attackEnd) {
            val normalized = samples[i].toFloat() / Short.MAX_VALUE
            val e = normalized * normalized
            if (e > peakEnergy) peakEnergy = e
        }

        // Has sustain if middle energy is > 20% of peak
        return energy > peakEnergy * 0.2f
    }

    /**
     * Get all available presets as a map.
     */
    @Suppress("unused")
    fun getAllPresets(): Map<String, ADSRSettings> = mapOf(
        "None" to Presets.NONE,
        "Piano" to Presets.PIANO,
        "Organ" to Presets.ORGAN,
        "Pad" to Presets.PAD,
        "Pluck" to Presets.PLUCK,
        "Strings" to Presets.STRINGS,
        "Brass" to Presets.BRASS,
        "Percussion" to Presets.PERCUSSION,
        "Voice" to Presets.VOICE,
        "Synth Lead" to Presets.SYNTH_LEAD,
        "Ambient" to Presets.AMBIENT
    )

}
