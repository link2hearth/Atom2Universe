package com.Atom2Universe.app.midi.sf2

/**
 * EQ graphique 10 bandes pour le pipeline audio SF2.
 *
 * S'insère post-limiter dans Sf2Synthesizer.render().
 * Chaque bande dispose de deux filtres biquad indépendants (L/R).
 *
 * Bandes : 32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz
 * Q     : 1.41 (standard pour EQ graphique ≈ 1 octave)
 * Gain  : ±12 dB par bande, stocké en millibels (±1200)
 */
class MidiEqualizerEngine(private val sampleRate: Int) {

    companion object {
        val BAND_FREQUENCIES = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val BAND_COUNT = 10
        const val BAND_Q = 1.41f
    }

    var enabled = false

    private val bandGainsDb = FloatArray(BAND_COUNT) { 0f }
    private val filtersLeft  = Array(BAND_COUNT) { MidiEqBiquadFilter() }
    private val filtersRight = Array(BAND_COUNT) { MidiEqBiquadFilter() }

    /**
     * Définit le gain d'une bande.
     *
     * @param bandIndex Index de la bande (0–9)
     * @param millibels Gain en millibels (−1200 à +1200, 0 = plat)
     */
    fun setBandLevel(bandIndex: Int, millibels: Int) {
        if (bandIndex !in 0 until BAND_COUNT) return
        val gainDb = millibels / 100f
        bandGainsDb[bandIndex] = gainDb
        reconfigureBand(bandIndex)
    }

    /**
     * Retourne le gain d'une bande en millibels.
     */
    fun getBandLevel(bandIndex: Int): Int {
        if (bandIndex !in 0 until BAND_COUNT) return 0
        return (bandGainsDb[bandIndex] * 100f).toInt()
    }

    /**
     * Réinitialise les filtres (clear de l'état interne uniquement, pas les gains).
     */
    fun reset() {
        filtersLeft.forEach { it.reset() }
        filtersRight.forEach { it.reset() }
    }

    /**
     * Traite un buffer interleaved short[] stéréo (L, R, L, R, ...).
     * Utilisé par FluidSynthEngine (pipeline short[]).
     * Si l'EQ est désactivé ou si toutes les bandes sont à 0 dB, bypass total.
     */
    fun processInterleaved(buffer: ShortArray, numFrames: Int) {
        if (!enabled) return
        if (bandGainsDb.all { it == 0f }) return
        val count = minOf(numFrames, buffer.size / 2)
        for (band in 0 until BAND_COUNT) {
            if (bandGainsDb[band] == 0f) continue
            val fL = filtersLeft[band]
            val fR = filtersRight[band]
            for (i in 0 until count) {
                val l = fL.process(buffer[i * 2].toFloat() / 32768f)
                val r = fR.process(buffer[i * 2 + 1].toFloat() / 32768f)
                buffer[i * 2]     = (l.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                buffer[i * 2 + 1] = (r.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
        }
    }

    /**
     * Traite les buffers stéréo en appliquant l'EQ bande par bande.
     * Si l'EQ est désactivé ou si toutes les bandes sont à 0 dB, bypass total.
     */
    fun process(left: FloatArray, right: FloatArray, numSamples: Int) {
        if (!enabled) return
        val count = minOf(numSamples, left.size, right.size)
        for (band in 0 until BAND_COUNT) {
            if (bandGainsDb[band] == 0f) continue  // bande plate → skip
            val fL = filtersLeft[band]
            val fR = filtersRight[band]
            for (i in 0 until count) {
                left[i]  = fL.process(left[i])
                right[i] = fR.process(right[i])
            }
        }
    }

    // -------------------------------------------------------------------------

    private fun reconfigureBand(bandIndex: Int) {
        val freqHz = BAND_FREQUENCIES[bandIndex].toFloat()
        val gainDb = bandGainsDb[bandIndex]
        val type = when (bandIndex) {
            0                -> MidiEqBiquadFilter.Type.LOW_SHELF
            BAND_COUNT - 1   -> MidiEqBiquadFilter.Type.HIGH_SHELF
            else             -> MidiEqBiquadFilter.Type.PEAK
        }
        filtersLeft[bandIndex].configure(sampleRate, freqHz, gainDb, BAND_Q, type)
        filtersRight[bandIndex].configure(sampleRate, freqHz, gainDb, BAND_Q, type)
    }
}
