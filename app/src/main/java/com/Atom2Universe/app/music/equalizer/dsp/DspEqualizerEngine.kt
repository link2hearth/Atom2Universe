package com.Atom2Universe.app.music.equalizer.dsp

import android.util.Log
import com.Atom2Universe.app.music.equalizer.data.EqPreset

/**
 * DSP-based equalizer engine that wraps the DspEqualizerProcessor.
 * Provides a similar interface to EqualizerEngine but uses software DSP
 * instead of Android's AudioEffect API.
 *
 * This is used to bypass Samsung SoundAlive and other system-level audio
 * processing that intercepts the standard Android Equalizer.
 */
class DspEqualizerEngine {

    companion object {
        private const val TAG = "DspEqualizerEngine"

        /** Number of EQ bands */
        const val BAND_COUNT = 10

        /** Target frequencies in Hz */
        val TARGET_FREQUENCIES = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** Min/max band level in millibels */
        const val MIN_BAND_LEVEL = -1200
        const val MAX_BAND_LEVEL = 1200
    }

    private var processor: DspEqualizerProcessor? = null
    private var isInitialized = false

    // Cached band levels (millibels)
    private val bandLevels = IntArray(BAND_COUNT)

    // Cached bass boost and virtualizer
    private var bassBoostStrength = 0
    private var virtualizerStrength = 0

    // Enabled state
    private var enabled = true

    /**
     * Initialize the DSP engine with a processor.
     * The processor should already be attached to ExoPlayer.
     *
     * @param processor The DspEqualizerProcessor instance
     * @return true if initialization was successful
     */
    fun initialize(processor: DspEqualizerProcessor): Boolean {
        this.processor = processor
        isInitialized = true

        // Apply cached state to processor
        processor.setEnabled(enabled)
        processor.setAllBandLevels(bandLevels.toList())
        processor.setBassBoost(bassBoostStrength)
        processor.setVirtualizer(virtualizerStrength)

        Log.d(TAG, "DspEqualizerEngine initialized")
        return true
    }

    /**
     * Apply an EQ preset to the DSP processor.
     *
     * @param preset The preset to apply
     * @param enabled Whether the EQ should be enabled
     */
    fun applyPreset(preset: EqPreset, enabled: Boolean = true) {
        this.enabled = enabled

        val levels = preset.getBandLevels()
        for (i in 0 until BAND_COUNT) {
            bandLevels[i] = levels.getOrElse(i) { 0 }.coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL)
        }

        bassBoostStrength = preset.bassBoostStrength.coerceIn(0, 1000)
        virtualizerStrength = preset.virtualizerStrength.coerceIn(0, 1000)

        processor?.let { p ->
            p.setEnabled(enabled)
            p.setAllBandLevels(bandLevels.toList())
            p.setBassBoost(bassBoostStrength)
            p.setVirtualizer(virtualizerStrength)
        }

        Log.d(TAG, "Applied preset '${preset.name}' (enabled=$enabled)")
    }

    /**
     * Set a single band level.
     *
     * @param bandIndex Band index (0-9)
     * @param level Level in millibels (-1200 to +1200)
     */
    fun setBandLevel(bandIndex: Int, level: Int) {
        if (bandIndex !in 0 until BAND_COUNT) return

        val clampedLevel = level.coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL)
        bandLevels[bandIndex] = clampedLevel
        processor?.setBandLevel(bandIndex, clampedLevel)
    }

    /**
     * Get current band level.
     *
     * @param bandIndex Band index (0-9)
     * @return Level in millibels
     */
    fun getBandLevel(bandIndex: Int): Int {
        if (bandIndex !in 0 until BAND_COUNT) return 0
        return processor?.getBandLevel(bandIndex) ?: bandLevels[bandIndex]
    }

    /**
     * Set bass boost strength.
     *
     * @param strength 0-1000
     */
    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        processor?.setBassBoost(bassBoostStrength)
    }

    /**
     * Get current bass boost strength.
     */
    fun getBassBoost(): Int = processor?.getBassBoost() ?: bassBoostStrength

    /**
     * Set virtualizer strength.
     *
     * @param strength 0-1000
     */
    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        processor?.setVirtualizer(virtualizerStrength)
    }

    /**
     * Get current virtualizer strength.
     */
    fun getVirtualizer(): Int = processor?.getVirtualizer() ?: virtualizerStrength

    /**
     * Enable or disable the equalizer.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        processor?.setEnabled(enabled)
        Log.d(TAG, "Effects enabled: $enabled")
    }

    /**
     * Check if the equalizer is enabled.
     */
    fun isEnabled(): Boolean = processor?.isEnabled() ?: enabled

    /**
     * Check if the engine is initialized.
     */
    fun isReady(): Boolean = isInitialized && processor != null

    /**
     * Get the band level range.
     */
    fun getLevelRange(): Pair<Int, Int> = Pair(MIN_BAND_LEVEL, MAX_BAND_LEVEL)

    /**
     * Get the number of bands.
     */
    fun getBandCount(): Int = BAND_COUNT

    /**
     * Bass boost is always supported in DSP mode.
     */
    fun isBassBoostSupported(): Boolean = true

    /**
     * Virtualizer is always supported in DSP mode.
     */
    fun isVirtualizerSupported(): Boolean = true

    /**
     * Release the engine.
     */
    fun release() {
        // Don't release the processor - it's managed by ExoPlayer
        isInitialized = false
        Log.d(TAG, "DspEqualizerEngine released")
    }

    /**
     * Reset all filters (call on seek or track change).
     */
    fun resetFilters() {
        processor?.resetFilters()
    }

    /**
     * Get the attached processor.
     */
    fun getProcessor(): DspEqualizerProcessor? = processor
}
