@file:Suppress("DEPRECATION")

package com.Atom2Universe.app.music.equalizer

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.Atom2Universe.app.music.equalizer.data.EqPreset

/**
 * Wrapper around Android's audio effects APIs (Equalizer, BassBoost, Virtualizer).
 * Provides a clean interface for applying EQ presets to an audio session.
 */
class EqualizerEngine {

    companion object {
        private const val TAG = "EqualizerEngine"

        /** Number of bands we support (maps to EqPreset) */
        const val BAND_COUNT = 10

        /** Our target frequencies in Hz */
        val TARGET_FREQUENCIES = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var audioSessionId: Int = 0
    private var isInitialized: Boolean = false

    /** Mapping from our 10-band indices to device's equalizer band indices */
    private var bandMapping: IntArray = IntArray(BAND_COUNT) { -1 }

    /** Device's actual number of bands */
    private var deviceBandCount: Int = 0

    /** Device's band level range */
    private var minBandLevel: Short = -1500
    private var maxBandLevel: Short = 1500

    /**
     * Initialize the audio effects for the given audio session.
     * @param sessionId The audio session ID from ExoPlayer
     * @return true if initialization was successful
     */
    fun initialize(sessionId: Int): Boolean {
        if (sessionId == 0) {
            Log.w(TAG, "Cannot initialize with session ID 0")
            return false
        }

        // Release any existing effects
        release()

        audioSessionId = sessionId

        try {
            // Initialize Equalizer
            equalizer = Equalizer(0, sessionId).apply {
                enabled = false // Start disabled until we apply a preset

                // Get device capabilities
                deviceBandCount = numberOfBands.toInt()
                val levelRange = bandLevelRange
                minBandLevel = levelRange[0]
                maxBandLevel = levelRange[1]

                // Build mapping from our 10 bands to device bands
                buildBandMapping()
            }

            // Initialize BassBoost
            bassBoost = try {
                BassBoost(0, sessionId).apply {
                    enabled = false
                    if (!strengthSupported) {
                        Log.w(TAG, "BassBoost strength not supported on this device")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "BassBoost not available: ${e.message}")
                null
            }

            // Initialize Virtualizer
            virtualizer = try {
                Virtualizer(0, sessionId).apply {
                    enabled = false
                    if (!strengthSupported) {
                        Log.w(TAG, "Virtualizer strength not supported on this device")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Virtualizer not available: ${e.message}")
                null
            }

            isInitialized = true
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects: ${e.message}", e)
            release()
            return false
        }
    }

    /**
     * Build mapping from our 10-band layout to device's actual bands.
     * Finds the closest device band for each of our target frequencies.
     */
    private fun buildBandMapping() {
        val eq = equalizer ?: return

        for (i in 0 until BAND_COUNT) {
            val targetFreq = TARGET_FREQUENCIES[i]
            var closestBand = 0
            var closestDistance = Int.MAX_VALUE

            for (band in 0 until deviceBandCount) {
                val centerFreq = eq.getCenterFreq(band.toShort()) / 1000 // Convert mHz to Hz
                val distance = kotlin.math.abs(centerFreq - targetFreq)

                if (distance < closestDistance) {
                    closestDistance = distance
                    closestBand = band
                }
            }

            bandMapping[i] = closestBand
        }
    }

    /**
     * Apply an EQ preset to the audio effects.
     * @param preset The preset to apply
     * @param enabled Whether to enable the effects
     */
    fun applyPreset(preset: EqPreset, enabled: Boolean = true) {
        if (!isInitialized) {
            Log.w(TAG, "Cannot apply preset: engine not initialized")
            return
        }

        try {
            // Apply equalizer bands
            equalizer?.let { eq ->
                // Enable EQ first - some devices require this before setting bands
                eq.enabled = enabled

                val levels = preset.getBandLevels()
                for (i in 0 until BAND_COUNT) {
                    val deviceBand = bandMapping[i]
                    if (deviceBand >= 0 && deviceBand < deviceBandCount) {
                        // Clamp to device's supported range
                        val level = levels[i].toShort().coerceIn(minBandLevel, maxBandLevel)
                        eq.setBandLevel(deviceBand.toShort(), level)
                    }
                }
            }

            // Apply bass boost
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    val strength = preset.bassBoostStrength.toShort().coerceIn(0, 1000)
                    bb.setStrength(strength)
                    bb.enabled = enabled && strength > 0
                }
            }

            // Apply virtualizer
            virtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    val strength = preset.virtualizerStrength.toShort().coerceIn(0, 1000)
                    virt.setStrength(strength)
                    virt.enabled = enabled && strength > 0
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply preset: ${e.message}")
        }
    }

    /**
     * Set a single band level.
     * @param bandIndex Our band index (0-9)
     * @param level Level in millibels (-1200 to +1200)
     */
    fun setBandLevel(bandIndex: Int, level: Int) {
        if (bandIndex !in 0 until BAND_COUNT) return

        equalizer?.let { eq ->
            val deviceBand = bandMapping[bandIndex]
            if (deviceBand >= 0 && deviceBand < deviceBandCount) {
                val clampedLevel = level.toShort().coerceIn(minBandLevel, maxBandLevel)
                eq.setBandLevel(deviceBand.toShort(), clampedLevel)
            }
        }
    }

    /**
     * Get current band level.
     * @param bandIndex Our band index (0-9)
     * @return Level in millibels
     */
    @Suppress("unused")
    fun getBandLevel(bandIndex: Int): Int {
        if (bandIndex !in 0 until BAND_COUNT) return 0

        return equalizer?.let { eq ->
            val deviceBand = bandMapping[bandIndex]
            if (deviceBand >= 0 && deviceBand < deviceBandCount) {
                eq.getBandLevel(deviceBand.toShort()).toInt()
            } else 0
        } ?: 0
    }

    /**
     * Set bass boost strength.
     * @param strength 0-1000
     */
    fun setBassBoost(strength: Int) {
        bassBoost?.let { bb ->
            if (bb.strengthSupported) {
                val clampedStrength = strength.toShort().coerceIn(0, 1000)
                bb.setStrength(clampedStrength)
                bb.enabled = clampedStrength > 0
            }
        }
    }

    /**
     * Get current bass boost strength.
     */
    @Suppress("unused")
    fun getBassBoost(): Int {
        return bassBoost?.roundedStrength?.toInt() ?: 0
    }

    /**
     * Set virtualizer strength.
     * @param strength 0-1000
     */
    fun setVirtualizer(strength: Int) {
        virtualizer?.let { virt ->
            if (virt.strengthSupported) {
                val clampedStrength = strength.toShort().coerceIn(0, 1000)
                virt.setStrength(clampedStrength)
                virt.enabled = clampedStrength > 0
            }
        }
    }

    /**
     * Get current virtualizer strength.
     */
    @Suppress("unused")
    fun getVirtualizer(): Int {
        return virtualizer?.roundedStrength?.toInt() ?: 0
    }

    /**
     * Enable or disable all effects.
     */
    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.let { if (it.roundedStrength > 0) it.enabled = enabled }
        virtualizer?.let { if (it.roundedStrength > 0) it.enabled = enabled }
    }

    /**
     * Check if effects are currently enabled.
     */
    fun isEnabled(): Boolean = equalizer?.enabled ?: false

    /**
     * Check if the engine is initialized.
     */
    @Suppress("unused")
    fun isReady(): Boolean = isInitialized

    /**
     * Get device's band level range.
     */
    @Suppress("unused")
    fun getLevelRange(): Pair<Int, Int> = Pair(minBandLevel.toInt(), maxBandLevel.toInt())

    /**
     * Get the number of bands on the device.
     */
    @Suppress("unused")
    fun getDeviceBandCount(): Int = deviceBandCount

    /**
     * Check if bass boost is supported.
     */
    fun isBassBoostSupported(): Boolean = bassBoost?.strengthSupported ?: false

    /**
     * Check if virtualizer is supported.
     */
    fun isVirtualizerSupported(): Boolean = virtualizer?.strengthSupported ?: false

    /**
     * Release all audio effects.
     */
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing effects: ${e.message}")
        }

        equalizer = null
        bassBoost = null
        virtualizer = null
        isInitialized = false
        audioSessionId = 0

    }
}
