package com.Atom2Universe.app.sf2creator.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes an audio file of any format the device reads (WAV, MP3, OGG, FLAC, M4A...) to
 * 16-bit mono frames, the format of an SF2 sample. Stereo files are mixed down.
 */
object AudioFileDecoder {

    /** Longest sound kept: SF2 samples are notes and short sounds, not whole songs. */
    const val MAX_SECONDS = 120

    private const val TIMEOUT_US = 10_000L

    class Decoded(val samples: ShortArray, val sampleRate: Int, val truncated: Boolean)

    fun decode(context: Context, uri: Uri): Decoded? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val out = MonoBuffer(sampleRate * MAX_SECONDS)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var idleAfterInput = 0
                while (!outputDone && !out.full && idleAfterInput < 300) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index)!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    // A decoder that never signals the end of the stream must not hang the import
                    if (inputDone && index == MediaCodec.INFO_TRY_AGAIN_LATER) idleAfterInput++ else idleAfterInput = 0
                    if (index >= 0) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            out.add(buffer, channels, encoding)
                        }
                        codec.releaseOutputBuffer(index, false)
                    } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val output = codec.outputFormat
                        if (output.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (output.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                }
                if (out.size == 0) return null
                return Decoded(out.toArray(), sampleRate, out.full)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } catch (e: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }

    /** Mono 16-bit frames, up to a maximum. */
    private class MonoBuffer(private val capacity: Int) {
        private var data = ShortArray(minOf(capacity, 1 shl 16))
        var size = 0
            private set
        val full get() = size >= capacity

        fun add(buffer: java.nio.ByteBuffer, channels: Int, encoding: Int) {
            val ch = channels.coerceAtLeast(1)
            when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    val floats = buffer.asFloatBuffer()
                    while (floats.remaining() >= ch && !full) {
                        var sum = 0f
                        repeat(ch) { sum += floats.get() }
                        put((sum / ch).coerceIn(-1f, 1f) * Short.MAX_VALUE)
                    }
                }
                AudioFormat.ENCODING_PCM_8BIT -> {
                    while (buffer.remaining() >= ch && !full) {
                        var sum = 0f
                        repeat(ch) { sum += ((buffer.get().toInt() and 0xFF) - 128) * 256f }
                        put(sum / ch)
                    }
                }
                else -> {
                    val shorts = buffer.asShortBuffer()
                    while (shorts.remaining() >= ch && !full) {
                        var sum = 0f
                        repeat(ch) { sum += shorts.get() }
                        put(sum / ch)
                    }
                }
            }
        }

        private fun put(value: Float) {
            if (size == data.size) data = data.copyOf(minOf(capacity, data.size * 2))
            data[size++] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        fun toArray(): ShortArray = data.copyOf(size)
    }
}
