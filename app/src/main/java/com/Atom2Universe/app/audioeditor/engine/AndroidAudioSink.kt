package com.Atom2Universe.app.audioeditor.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.io.IOException

/**
 * La sortie réelle : un `AudioTrack` en flottant stéréo, en flux, écriture bloquante.
 *
 * Le tampon fait quatre blocs du moteur (~185 ms à 44,1 kHz) ou deux fois le minimum du système si
 * c'est plus : assez pour ne pas craquer quand le fil de mixage est retardé, assez court pour qu'un
 * saut ou une édition s'entende vite. La position vient de `playbackHeadPosition`, un compteur 32 bits
 * non signé qui ne dépend d'aucun minuteur.
 */
class AndroidAudioSink(override val sampleRate: Int) : AudioSink {

    private val track: AudioTrack
    @Volatile private var aborted = false
    @Volatile private var writtenFrames = 0L

    init {
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBytes <= 0) throw IOException("Sortie audio indisponible à $sampleRate Hz")
        val bytes = maxOf(minBytes * 2, PlaybackEngine.BLOCK * BYTES_PER_FRAME * 4)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IOException("AudioTrack non initialisé")
        }
    }

    override fun write(interleaved: FloatArray, offsetFrames: Int, frames: Int): Int {
        if (aborted) return 0
        val r = track.write(interleaved, offsetFrames * 2, frames * 2, AudioTrack.WRITE_BLOCKING)
        if (r < 0) return r
        val f = r / 2
        writtenFrames += f
        return f
    }

    override fun start() {
        if (!aborted) track.play()
    }

    override fun playedFrames(): Long = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    override fun drain(timeoutMs: Long) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!aborted && playedFrames() < writtenFrames && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
    }

    override fun abort() {
        aborted = true
        // Pause + flush vident le tampon : une écriture bloquée en attente de place repart aussitôt.
        runCatching { track.pause(); track.flush() }
    }

    override fun release() {
        runCatching { track.stop() }
        track.release()
    }

    companion object {
        private const val BYTES_PER_FRAME = 8   // 2 voies × flottant 32 bits

        /** À passer au moteur : fil audio à la priorité « audio urgent » d'Android. */
        fun raiseThreadPriority() {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
        }
    }
}
