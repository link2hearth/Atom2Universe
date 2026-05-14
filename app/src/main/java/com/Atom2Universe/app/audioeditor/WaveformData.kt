package com.Atom2Universe.app.audioeditor

/**
 * Represents waveform data extracted from an audio file.
 * Contains amplitude samples normalized to 0.0-1.0 range.
 */
data class WaveformData(
    val amplitudes: FloatArray,
    val sampleRate: Int,
    val durationMs: Long,
    val channels: Int
) {
    // Bug fix: Guard against division by zero when durationMs is 0
    val samplesPerMs: Float get() = if (durationMs > 0) amplitudes.size.toFloat() / durationMs else 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WaveformData
        if (!amplitudes.contentEquals(other.amplitudes)) return false
        if (sampleRate != other.sampleRate) return false
        if (durationMs != other.durationMs) return false
        if (channels != other.channels) return false
        return true
    }

    override fun hashCode(): Int {
        var result = amplitudes.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + channels
        return result
    }
}
