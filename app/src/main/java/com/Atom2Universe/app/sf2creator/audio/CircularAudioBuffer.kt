package com.Atom2Universe.app.sf2creator.audio

import android.util.Log

/**
 * Circular audio buffer for continuous recording.
 *
 * Allows recording to start before the user presses "Record" so that
 * microphone startup transients are avoided. When the user presses Record,
 * we can go back in time (pre-roll) to capture audio from before the button press.
 *
 * The buffer continuously overwrites old data as new audio comes in,
 * maintaining a sliding window of the most recent audio.
 */
class CircularAudioBuffer(
    capacitySeconds: Float = 10f,
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "CircularAudioBuffer"
    }

    private val capacity: Int = (capacitySeconds * sampleRate).toInt()
    private val buffer: ShortArray = ShortArray(capacity)

    @Volatile
    private var writePosition: Int = 0

    @Volatile
    private var totalSamplesWritten: Long = 0

    private val lock = Any()

    /**
     * Write samples to the buffer.
     * This overwrites old data when the buffer is full (circular behavior).
     *
     * @param samples Source array
     * @param offset Start offset in source array
     * @param count Number of samples to write
     */
    fun write(samples: ShortArray, offset: Int = 0, count: Int = samples.size) {
        synchronized(lock) {
            var remaining = count
            var srcOffset = offset

            while (remaining > 0) {
                val spaceToEnd = capacity - writePosition
                val toWrite = minOf(remaining, spaceToEnd)

                System.arraycopy(samples, srcOffset, buffer, writePosition, toWrite)

                writePosition = (writePosition + toWrite) % capacity
                srcOffset += toWrite
                remaining -= toWrite
            }

            totalSamplesWritten += count
        }
    }

    /**
     * Get the current write position in the buffer.
     * This represents the most recent sample position.
     */
    fun getCurrentPosition(): Int {
        synchronized(lock) {
            return writePosition
        }
    }

    /**
     * Get total samples written since buffer creation.
     * Useful for calculating time elapsed.
     */
    fun getTotalSamplesWritten(): Long {
        synchronized(lock) {
            return totalSamplesWritten
        }
    }

    /**
     * Extract samples from the buffer between two positions.
     * Handles wrap-around automatically.
     *
     * @param startPosition Start position in buffer (can be negative for pre-roll)
     * @param endPosition End position in buffer
     * @param preRollSamples Additional samples to include before startPosition
     * @return Extracted samples, or null if not enough data
     */
    fun extract(
        startPosition: Int,
        endPosition: Int,
        preRollSamples: Int = 0
    ): ShortArray? {
        synchronized(lock) {
            // Adjust start position for pre-roll
            var actualStart = startPosition - preRollSamples
            val actualEnd = endPosition

            // Calculate how many samples we need
            var length = actualEnd - actualStart
            if (length < 0) {
                // Wrap-around case
                length += capacity
            }

            // Check if we have enough data
            if (length > capacity || length <= 0) {
                Log.w(TAG, "Invalid extraction range: start=$actualStart, end=$actualEnd, length=$length")
                return null
            }

            // Normalize start position to be within buffer bounds
            actualStart = ((actualStart % capacity) + capacity) % capacity

            val result = ShortArray(length)
            var destOffset = 0
            var srcPos = actualStart
            var remaining = length

            while (remaining > 0) {
                val toEnd = capacity - srcPos
                val toCopy = minOf(remaining, toEnd)

                System.arraycopy(buffer, srcPos, result, destOffset, toCopy)

                srcPos = (srcPos + toCopy) % capacity
                destOffset += toCopy
                remaining -= toCopy
            }

            return result
        }
    }

    /**
     * Extract samples from a marked start position to the current position.
     *
     * @param markedStartPosition The position marked when recording started
     * @param preRollSamples Additional samples to include before the marked position
     * @return Extracted samples
     */
    fun extractFromMarkedStart(
        markedStartPosition: Int,
        preRollSamples: Int = 0
    ): ShortArray? {
        synchronized(lock) {
            return extract(markedStartPosition, writePosition, preRollSamples)
        }
    }

    /**
     * Clear the buffer and reset positions.
     */
    fun clear() {
        synchronized(lock) {
            buffer.fill(0)
            writePosition = 0
            totalSamplesWritten = 0
        }
    }

    /**
     * Get the buffer capacity in samples.
     */
    fun getCapacity(): Int = capacity

    /**
     * Get the buffer capacity in seconds.
     */
    fun getCapacitySeconds(): Float = capacity.toFloat() / sampleRate

    /**
     * Check if the buffer has enough data for a given duration.
     *
     * @param durationMs Required duration in milliseconds
     * @return true if buffer has enough data
     */
    fun hasEnoughData(durationMs: Int): Boolean {
        synchronized(lock) {
            val requiredSamples = durationMs * sampleRate / 1000
            return totalSamplesWritten >= requiredSamples
        }
    }

    /**
     * Calculate the position that was N milliseconds ago from current position.
     *
     * @param msAgo Milliseconds in the past
     * @return Buffer position, or -1 if not enough data
     */
    fun getPositionMsAgo(msAgo: Int): Int {
        synchronized(lock) {
            val samplesAgo = msAgo * sampleRate / 1000
            if (totalSamplesWritten < samplesAgo) {
                return -1
            }
            return ((writePosition - samplesAgo) % capacity + capacity) % capacity
        }
    }
}
