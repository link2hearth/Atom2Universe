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
 * - Cubic interpolation: Catmull-Rom basis functions h0..h3 for 256 fractional positions
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

    // Cubic Hermite (Catmull-Rom) interpolation coefficient table
    // 256 entries × 4 coefficients (h0, h1, h2, h3) in a flat array for cache locality
    // For fractional position t in [0,1), interpolation is:
    //   result = s0*h0(t) + s1*h1(t) + s2*h2(t) + s3*h3(t)
    // Replaces ~16 float ops (inline coefficient calculation + Horner evaluation)
    // with 4 multiply-adds + 1 table lookup per sample
    private const val INTERP_TABLE_SIZE = 256
    private val interpTable = FloatArray(INTERP_TABLE_SIZE * 4)

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

        // Initialize cubic interpolation coefficient table
        // Catmull-Rom basis functions for fractional position t in [0, 1):
        //   h0(t) = -0.5t³ +   t² - 0.5t      (weight for sample at position -1)
        //   h1(t) =  1.5t³ - 2.5t² + 1         (weight for sample at position  0)
        //   h2(t) = -1.5t³ +   2t² + 0.5t      (weight for sample at position +1)
        //   h3(t) =  0.5t³ - 0.5t²             (weight for sample at position +2)
        for (i in 0 until INTERP_TABLE_SIZE) {
            val t = i.toFloat() / INTERP_TABLE_SIZE
            val t2 = t * t
            val t3 = t2 * t
            val base = i * 4
            interpTable[base]     = -0.5f * t3 + t2 - 0.5f * t       // h0
            interpTable[base + 1] =  1.5f * t3 - 2.5f * t2 + 1f      // h1
            interpTable[base + 2] = -1.5f * t3 + 2f * t2 + 0.5f * t  // h2
            interpTable[base + 3] =  0.5f * t3 - 0.5f * t2            // h3
        }

        // Initialize velocity tables
        for (vel in 0 until 128) {
            val normalized = vel / 127f
            velocityTableLinear[vel] = normalized
            velocityTableConcave[vel] = normalized.pow(1.7f)
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

    /**
     * Fast cubic Hermite (Catmull-Rom) interpolation using pre-computed coefficient table.
     * Replaces inline coefficient calculation (~16 float ops) with
     * 4 multiply-adds + 1 table lookup.
     *
     * @param s0 Sample at position -1
     * @param s1 Sample at position 0 (current integer position)
     * @param s2 Sample at position +1
     * @param s3 Sample at position +2
     * @param frac Fractional position between s1 and s2 [0.0, 1.0)
     * @return Interpolated sample value
     */
    fun cubicInterpolate(s0: Float, s1: Float, s2: Float, s3: Float, frac: Float): Float {
        // Use bitwise AND for fast clamping (INTERP_TABLE_SIZE is power of 2)
        val idx = (frac * INTERP_TABLE_SIZE).toInt() and (INTERP_TABLE_SIZE - 1)
        val base = idx * 4
        return s0 * interpTable[base] + s1 * interpTable[base + 1] +
               s2 * interpTable[base + 2] + s3 * interpTable[base + 3]
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
