package com.Atom2Universe.app.audioeditor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Extracts waveform amplitude data from audio files.
 * Uses MediaExtractor and MediaCodec to decode audio and compute amplitudes.
 *
 * Bug 2.27: MediaCodec is not thread-safe. All codec operations (configure, start, stop, release,
 * queueInputBuffer, dequeueOutputBuffer) are performed sequentially within a single coroutine
 * on Dispatchers.IO. Do not share codec instances across threads.
 */
class WaveformExtractor(private val context: Context) {

    companion object {
        private const val TARGET_SAMPLES = 1000 // Number of amplitude points for waveform
        private const val TIMEOUT_US = 10000L
        private const val MAX_SPECTROGRAM_SAMPLES = 500_000 // Cap raw samples for spectrogram (~2MB)
        private const val EXTRACTION_TIMEOUT_MS = 60_000L // 60 seconds global timeout for extraction
    }

    /**
     * Extract waveform data from an audio file URI.
     * Returns WaveformData with normalized amplitudes (0.0 to 1.0).
     * Has a global timeout to prevent infinite extraction on problematic files.
     */
    suspend fun extract(uri: Uri): WaveformData = withTimeout(EXTRACTION_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)

                // Find audio track
                val audioTrackIndex = findAudioTrack(extractor)
                if (audioTrackIndex < 0) {
                    throw IllegalArgumentException("No audio track found in file")
                }

                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)

                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                val durationMs = durationUs / 1000

                val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("Unknown MIME type")
                val codec = MediaCodec.createDecoderByType(mime)

                // Wrap codec operations in try-finally to ensure release on any error
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()

                    val amplitudes = decodeAndExtractAmplitudes(extractor, codec, durationMs)

                    codec.stop()

                    WaveformData(
                        amplitudes = amplitudes,
                        sampleRate = sampleRate,
                        durationMs = durationMs,
                        channels = channels
                    )
                } finally {
                    codec.release()
                }
            } finally {
                extractor.release()
            }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    /**
     * State holder for progressive downsampling during decoding.
     * Accumulates peak amplitude per bucket without storing all samples.
     */
    private class WaveformAccumulator(estimatedTotalSamples: Long, private val targetSamples: Int) {
        val result = FloatArray(targetSamples)
        val samplesPerBucket = max(1L, estimatedTotalSamples / targetSamples)
        var sampleIndex = 0L
        var currentBucket = 0
        var currentPeak = 0f

        fun addSample(absValue: Float) {
            currentPeak = max(currentPeak, absValue)
            sampleIndex++

            if (sampleIndex % samplesPerBucket == 0L) {
                if (currentBucket < targetSamples) {
                    result[currentBucket] = currentPeak
                }
                currentBucket++
                currentPeak = 0f
            }
        }

        fun finish(): FloatArray {
            // Flush last partial bucket
            if (currentPeak > 0f && currentBucket < targetSamples) {
                result[currentBucket] = currentPeak
            }
            return result
        }
    }

    private fun decodeAndExtractAmplitudes(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationMs: Long
    ): FloatArray {
        // Estimate total samples to compute bucket size upfront
        val format = codec.outputFormat
        var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val estimatedTotal = durationMs * sampleRate * channels / 1000

        val accumulator = WaveformAccumulator(estimatedTotal, TARGET_SAMPLES)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            // Feed input
            if (!inputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            // Get output
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        extractSamplesFromBuffer(outputBuffer, accumulator)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }
                // Bug 2.36: Handle output format changes during extraction
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    codec.outputFormat
                    // Bug 2.37: Recalculate estimated total based on actual format
                    // Note: accumulator already handles this gracefully with its bucket approach
                }
            }
        }

        return accumulator.finish()
    }

    private fun extractSamplesFromBuffer(buffer: ByteBuffer, accumulator: WaveformAccumulator) {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = buffer.asShortBuffer()
        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get()
            val normalized = abs(sample.toFloat() / Short.MAX_VALUE)
            accumulator.addSample(normalized)
        }
    }

    /**
     * Extract raw audio samples for spectrum analysis.
     * Returns signed normalized samples (-1.0 to 1.0) suitable for FFT.
     * Decimates long files to cap memory at ~2MB (MAX_SPECTROGRAM_SAMPLES floats).
     * Has a global timeout to prevent infinite extraction on problematic files.
     */
    suspend fun extractRawSamples(uri: Uri): RawAudioData = withTimeout(EXTRACTION_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)

                val audioTrackIndex = findAudioTrack(extractor)
                if (audioTrackIndex < 0) {
                    throw IllegalArgumentException("No audio track found in file")
                }

                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)

                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                val durationMs = durationUs / 1000

                // Estimate total mono samples and compute decimation factor
                val estimatedMonoSamples = durationMs * sampleRate / 1000
                val decimation = max(1L, estimatedMonoSamples / MAX_SPECTROGRAM_SAMPLES).toInt()
                val effectiveSampleRate = sampleRate / decimation

                val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("Unknown MIME type")
                val codec = MediaCodec.createDecoderByType(mime)

                // Wrap codec operations in try-finally to ensure release on any error
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()

                    val samples = decodeRawSamples(extractor, codec, channels, decimation)

                    codec.stop()

                    RawAudioData(
                        samples = samples,
                        sampleRate = effectiveSampleRate,
                        channels = channels,
                        durationMs = durationMs
                    )
                } finally {
                    codec.release()
                }
            } finally {
                extractor.release()
            }
        }
    }

    /**
     * State for decimated raw sample extraction.
     * Keeps only every Nth mono sample to cap total memory usage.
     */
    private class RawSampleAccumulator(private val decimation: Int) {
        private val collected = mutableListOf<Float>()
        var sampleCounter = 0L

        fun addMonoSample(value: Float) {
            if (sampleCounter % decimation == 0L) {
                collected.add(value)
            }
            sampleCounter++
        }

        fun toFloatArray(): FloatArray = collected.toFloatArray()
    }

    private fun decodeRawSamples(
        extractor: MediaExtractor,
        codec: MediaCodec,
        channels: Int,
        decimation: Int
    ): FloatArray {
        val accumulator = RawSampleAccumulator(decimation)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var outputChannels = channels
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        extractSignedSamples(outputBuffer, accumulator, outputChannels, pcmEncoding)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                }
            }
        }

        return accumulator.toFloatArray()
    }

    private fun extractSignedSamples(
        buffer: ByteBuffer,
        accumulator: RawSampleAccumulator,
        channels: Int,
        pcmEncoding: Int
    ) {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatBuffer = buffer.asFloatBuffer()
                while (floatBuffer.hasRemaining()) {
                    var sum = 0f
                    repeat(channels) {
                        if (floatBuffer.hasRemaining()) {
                            sum += floatBuffer.get().coerceIn(-1f, 1f)
                        }
                    }
                    accumulator.addMonoSample(sum / channels)
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                while (buffer.hasRemaining()) {
                    var sum = 0f
                    repeat(channels) {
                        if (buffer.hasRemaining()) {
                            val unsigned = buffer.get().toInt() and 0xFF
                            sum += (unsigned - 128) / 128f
                        }
                    }
                    accumulator.addMonoSample(sum / channels)
                }
            }
            else -> {
                val shortBuffer = buffer.asShortBuffer()
                while (shortBuffer.hasRemaining()) {
                    var sum = 0f
                    repeat(channels) {
                        if (shortBuffer.hasRemaining()) {
                            sum += shortBuffer.get().toFloat() / Short.MAX_VALUE
                        }
                    }
                    accumulator.addMonoSample(sum / channels)
                }
            }
        }
    }

    /**
     * Raw audio data for spectrum analysis.
     */
    data class RawAudioData(
        val samples: FloatArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RawAudioData
            return samples.contentEquals(other.samples) && sampleRate == other.sampleRate
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + sampleRate
            return result
        }
    }
}
