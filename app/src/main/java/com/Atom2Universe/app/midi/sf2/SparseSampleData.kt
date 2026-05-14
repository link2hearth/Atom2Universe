package com.Atom2Universe.app.midi.sf2

/**
 * Sparse sample data container for efficient memory usage with large SF2 files.
 *
 * Instead of loading the entire SMPL chunk (~1GB for large soundfonts), this class
 * only stores the sample ranges that are actually needed for playback.
 *
 * Features:
 * - Binary search for efficient sample lookup
 * - Merges overlapping/adjacent ranges to minimize fragmentation
 * - Thread-safe read access
 * - Supports incremental loading (add more ranges as needed)
 */
class SparseSampleData(
    initialRanges: List<LoadedRange> = emptyList()
) {
    companion object {
        /**
         * Creates an empty SparseSampleData
         */
        fun empty(): SparseSampleData = SparseSampleData()
    }

    /**
     * Represents a contiguous range of loaded sample data
     */
    data class LoadedRange(
        val startSample: Long,       // Start position in the original SMPL chunk
        val endSample: Long,         // End position (exclusive)
        val data: ShortArray         // The actual sample data
    ) {
        val length: Int get() = data.size

        /**
         * Check if this range contains the given sample position
         */
        fun contains(samplePosition: Long): Boolean {
            return samplePosition >= startSample && samplePosition < endSample
        }

        /**
         * Get sample at the given global position
         * @return sample value, or null if position is outside this range
         */
        fun getSample(samplePosition: Long): Short? {
            if (!contains(samplePosition)) return null
            val localIndex = (samplePosition - startSample).toInt()
            return if (localIndex >= 0 && localIndex < data.size) {
                data[localIndex]
            } else null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LoadedRange) return false
            return startSample == other.startSample &&
                   endSample == other.endSample &&
                   data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = startSample.hashCode()
            result = 31 * result + endSample.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    // Sorted list of loaded ranges (by startSample)
    private val ranges: MutableList<LoadedRange> = initialRanges
        .sortedBy { it.startSample }
        .toMutableList()

    // Total samples loaded
    private var totalSamplesLoaded: Long = initialRanges.sumOf { it.length.toLong() }

    /**
     * Number of separate ranges loaded
     */
    val rangeCount: Int
        get() = ranges.size

    /**
     * Total number of samples currently loaded
     */
    val loadedSampleCount: Long
        get() = totalSamplesLoaded

    /**
     * Estimated memory usage in bytes
     */
    val memoryUsageBytes: Long
        get() = totalSamplesLoaded * 2  // 2 bytes per sample (Short)

    /**
     * Get a sample at the given global position.
     * Uses binary search for efficient lookup.
     *
     * @param position Sample position in the original SMPL chunk
     * @return Sample value as Float (-1.0 to 1.0), or 0 if not loaded
     */
    fun getSample(position: Long): Float {
        if (ranges.isEmpty()) return 0f

        // Binary search to find the range
        val rangeIndex = findRangeIndex(position)
        if (rangeIndex < 0) return 0f

        val range = ranges[rangeIndex]
        val sample = range.getSample(position) ?: return 0f
        return sample / 32768f
    }

    /**
     * Get a raw sample value (Short) at the given position
     */
    fun getSampleRaw(position: Long): Short {
        if (ranges.isEmpty()) return 0

        val rangeIndex = findRangeIndex(position)
        if (rangeIndex < 0) return 0

        return ranges[rangeIndex].getSample(position) ?: 0
    }

    /**
     * Check if a sample position is loaded
     */
    fun isLoaded(position: Long): Boolean {
        if (ranges.isEmpty()) return false
        val rangeIndex = findRangeIndex(position)
        return rangeIndex >= 0
    }

    /**
     * Check if an entire range is loaded
     */
    fun isRangeLoaded(start: Long, end: Long): Boolean {
        if (ranges.isEmpty()) return false

        var currentPos = start
        while (currentPos < end) {
            val rangeIndex = findRangeIndex(currentPos)
            if (rangeIndex < 0) return false

            val range = ranges[rangeIndex]
            // Move to the end of this range (or end of requested range)
            currentPos = minOf(range.endSample, end)
        }
        return true
    }

    /**
     * Get samples with linear interpolation for non-integer positions.
     * Used for pitch-shifted playback.
     */
    fun getSampleInterpolated(position: Double): Float {
        val index = position.toLong()
        val frac = (position - index).toFloat()

        val s0 = getSample(index)
        val s1 = getSample(index + 1)

        return s0 + (s1 - s0) * frac
    }

    /**
     * Read multiple samples into a buffer
     *
     * @param startPosition Starting sample position
     * @param outputBuffer Buffer to fill
     * @param length Number of samples to read
     * @return Number of samples actually read (may be less if not all are loaded)
     */
    fun readSamples(startPosition: Long, outputBuffer: FloatArray, length: Int = outputBuffer.size): Int {
        var read = 0
        var pos = startPosition

        while (read < length && read < outputBuffer.size) {
            outputBuffer[read] = getSample(pos)
            read++
            pos++
        }

        return read
    }

    /**
     * Add a new range of samples.
     * Will merge with existing ranges if they overlap or are adjacent.
     */
    @Synchronized
    fun addRange(range: LoadedRange) {
        if (range.data.isEmpty()) return

        // Find insertion position
        val insertPos = ranges.binarySearch { it.startSample.compareTo(range.startSample) }
            .let { if (it < 0) -(it + 1) else it }

        // Check for overlaps/adjacency with previous range
        if (insertPos > 0) {
            val prev = ranges[insertPos - 1]
            if (prev.endSample >= range.startSample) {
                // Overlapping or adjacent - merge
                val merged = mergeRanges(prev, range)
                ranges.removeAt(insertPos - 1)
                ranges.add(insertPos - 1, merged)
                totalSamplesLoaded = ranges.sumOf { it.length.toLong() }
                return
            }
        }

        // Check for overlaps/adjacency with next range
        if (insertPos < ranges.size) {
            val next = ranges[insertPos]
            if (range.endSample >= next.startSample) {
                // Overlapping or adjacent - merge
                val merged = mergeRanges(range, next)
                ranges.removeAt(insertPos)
                ranges.add(insertPos, merged)
                totalSamplesLoaded = ranges.sumOf { it.length.toLong() }
                return
            }
        }

        // No overlap - just insert
        ranges.add(insertPos, range)
        totalSamplesLoaded += range.length
    }

    /**
     * Convert to a full ShortArray (for compatibility with existing Sf2File API).
     * WARNING: This defeats the purpose of sparse loading for large files!
     * Only use this for small soundfonts or when you know memory is sufficient.
     */
    fun toShortArray(): ShortArray {
        if (ranges.isEmpty()) return ShortArray(0)

        // Find the max extent
        val maxEnd = ranges.maxOf { it.endSample }

        val fullArray = ShortArray(maxEnd.toInt())

        for (range in ranges) {
            System.arraycopy(
                range.data, 0,
                fullArray, range.startSample.toInt(),
                range.length
            )
        }

        return fullArray
    }

    /**
     * Get statistics about loaded data
     */
    fun getStats(): SparseSampleStats {
        val totalExtent = if (ranges.isEmpty()) 0L else {
            ranges.last().endSample - ranges.first().startSample
        }
        val coverage = if (totalExtent > 0) {
            totalSamplesLoaded.toFloat() / totalExtent
        } else 0f

        return SparseSampleStats(
            rangeCount = ranges.size,
            totalSamplesLoaded = totalSamplesLoaded,
            memoryUsageMB = memoryUsageBytes / 1024f / 1024f,
            coverage = coverage
        )
    }

    /**
     * Clear all loaded data
     */
    @Synchronized
    fun clear() {
        ranges.clear()
        totalSamplesLoaded = 0
    }

    // Binary search to find the range containing the given position
    private fun findRangeIndex(position: Long): Int {
        if (ranges.isEmpty()) return -1

        var low = 0
        var high = ranges.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val range = ranges[mid]

            when {
                position < range.startSample -> high = mid - 1
                position >= range.endSample -> low = mid + 1
                else -> return mid  // Found it
            }
        }

        return -1  // Not found
    }

    // Merge two overlapping/adjacent ranges
    private fun mergeRanges(a: LoadedRange, b: LoadedRange): LoadedRange {
        val newStart = minOf(a.startSample, b.startSample)
        val newEnd = maxOf(a.endSample, b.endSample)
        val newLength = (newEnd - newStart).toInt()
        val newData = ShortArray(newLength)

        // Copy data from both ranges
        val aOffset = (a.startSample - newStart).toInt()
        System.arraycopy(a.data, 0, newData, aOffset, a.data.size)

        val bOffset = (b.startSample - newStart).toInt()
        System.arraycopy(b.data, 0, newData, bOffset, b.data.size)

        return LoadedRange(newStart, newEnd, newData)
    }

    override fun toString(): String {
        return "SparseSampleData(ranges=$rangeCount, samples=$totalSamplesLoaded, " +
               "memory=${memoryUsageBytes / 1024 / 1024}MB)"
    }
}

/**
 * Statistics about sparse sample data
 */
data class SparseSampleStats(
    val rangeCount: Int,
    val totalSamplesLoaded: Long,
    val memoryUsageMB: Float,
    val coverage: Float  // Ratio of loaded samples to total extent
)
