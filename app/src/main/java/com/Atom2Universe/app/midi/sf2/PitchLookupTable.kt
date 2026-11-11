package com.Atom2Universe.app.midi.sf2

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pre-computed lookup tables for fast pitch calculations.
 *
 * Replaces expensive pow() calls with simple array lookups.
 * This is critical for performance during playback of complex pieces
 * with many simultaneous voices.
 *
 * The tables cover:
 * - Pitch modulation: 2^(cents/1200) for cents in [-2400, +2400]
 * - Volume modulation: 10^(centibels/200) for centibels in [-960, +960]
 */
object PitchLookupTable {

    // Pitch table: 2^(cents/1200)
    // Range: -2400 to +2400 cents (±2 octaves)
    // Resolution: 1 cent
    private const val PITCH_OFFSET = 2400
    private const val PITCH_TABLE_SIZE = 4801 // -2400 to +2400 inclusive
    private val pitchTable = FloatArray(PITCH_TABLE_SIZE)

    // Volume table: 10^(centibels/200) = dB to linear
    // Range: -960 to +960 centibels (±48 dB)
    // Resolution: 1 centibel
    private const val VOLUME_OFFSET = 960
    private const val VOLUME_TABLE_SIZE = 1921 // -960 to +960 inclusive
    private val volumeTable = FloatArray(VOLUME_TABLE_SIZE)

    // Velocity gain table: pre-computed for each velocity curve
    // Linear interpolation not needed since MIDI velocity is integer (0-127)
    private val velocityTableLinear = FloatArray(128)
    private val velocityTableConcave = FloatArray(128)
    private val velocityTableSoft = FloatArray(128)
    private val velocityTableHard = FloatArray(128)

    init {
        // Initialize pitch table: 2^(cents/1200)
        for (i in 0 until PITCH_TABLE_SIZE) {
            val cents = i - PITCH_OFFSET
            pitchTable[i] = 2.0.pow(cents / 1200.0).toFloat()
        }

        // Initialize volume table: 10^(centibels/200)
        for (i in 0 until VOLUME_TABLE_SIZE) {
            val centibels = i - VOLUME_OFFSET
            volumeTable[i] = 10.0.pow(centibels / 200.0).toFloat()
        }

        // Initialize velocity tables
        for (vel in 0 until 128) {
            val normalized = vel / 127f
            velocityTableLinear[vel] = normalized
            // Courbe de la norme SF2 : modulateur par défaut vélocité -> atténuation, 960 cB,
            // concave, soit une amplitude (v / 127)²
            velocityTableConcave[vel] = normalized * normalized
            velocityTableSoft[vel] = normalized.pow(2.2f)
            velocityTableHard[vel] = normalized.pow(1.2f)
        }
    }

    /**
     * Fast lookup for pitch factor: 2^(cents/1200).
     * @param cents Pitch deviation in cents (-2400 to +2400 for table, but handles extremes)
     * @return Pitch factor (0.25 to 4.0 for ±2 octaves, computed directly for extremes)
     */
    fun centsToFactor(cents: Float): Float {
        // BUG FIX: Handle extreme values beyond table range (GeneralUser GM uses ±8000+ cents)
        // Don't clamp - compute directly for extremes to avoid opening filter when it should close
        val centsInt = cents.roundToInt()
        return if (centsInt < -PITCH_OFFSET || centsInt > PITCH_OFFSET) {
            // Outside table range: compute directly (rare, so performance hit is acceptable)
            2f.pow(cents / 1200f).coerceIn(0.001f, 1000f)
        } else {
            // Within table range: fast lookup
            val index = centsInt + PITCH_OFFSET
            pitchTable[index]
        }
    }

    /**
     * Fast lookup for volume factor: 10^(centibels/200).
     * @param centibels Volume deviation in centibels (-960 to +960)
     * @return Volume factor
     */
    fun centibelsToFactor(centibels: Float): Float {
        val index = (centibels.roundToInt() + VOLUME_OFFSET).coerceIn(0, VOLUME_TABLE_SIZE - 1)
        return volumeTable[index]
    }

    /**
     * Fast velocity gain lookup.
     * @param velocity MIDI velocity (0-127)
     * @param curve Velocity curve type
     * @return Gain value (0.0 to 1.0)
     */
    fun velocityToGain(velocity: Int, curve: VelocityCurve): Float {
        val clampedVel = velocity.coerceIn(0, 127)
        return when (curve) {
            VelocityCurve.LINEAR -> velocityTableLinear[clampedVel]
            VelocityCurve.CONCAVE -> velocityTableConcave[clampedVel]
            VelocityCurve.SOFT -> velocityTableSoft[clampedVel]
            VelocityCurve.HARD -> velocityTableHard[clampedVel]
        }
    }

    /** Catmull-Rom at the actual playback phase, including phases that round up to 1f. */
    fun cubicInterpolate(s0: Float, s1: Float, s2: Float, s3: Float, frac: Float): Float {
        val delta = s2 - s1
        val startSlope = 0.5f * (s2 - s0)
        val endSlope = 0.5f * (s3 - s1)
        val quadratic = 3f * delta - 2f * startSlope - endSlope
        val cubic = startSlope + endSlope - 2f * delta
        return s1 + frac * (startSlope + frac * (quadratic + frac * cubic))
    }

    /**
     * Combined pitch calculation for semitones + cents.
     * More accurate than using roundToInt on the combined value.
     * @param semitones Pitch bend in semitones
     * @return Pitch factor
     */
    fun semitonesToFactor(semitones: Float): Float {
        // Convert semitones to cents and use lookup
        return centsToFactor(semitones * 100f)
    }
}
