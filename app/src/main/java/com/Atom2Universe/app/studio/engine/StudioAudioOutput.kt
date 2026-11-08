package com.Atom2Universe.app.studio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * La sortie du moteur du Studio : un `AudioTrack` en flottant stéréo, mode faible latence, alimenté par un
 * fil audio dédié qui demande [blockFrames] trames au moteur, les écrit (écriture bloquante : c'est la sortie
 * qui cadence) et mesure le temps de calcul de chaque bloc.
 *
 * Les mesures ([stats]) servent à régler le compromis latence / craquements : un tampon de deux blocs
 * (~10 ms) est idéal pour jouer au doigt mais craque si le calcul traîne ; [setBufferBlocks] permet d'essayer
 * trois ou quatre blocs en direct.
 */
class StudioAudioOutput(
    private val engine: StudioEngine,
    private val blockFrames: Int = 256,
) {

    class Stats {
        @Volatile var sampleRate = 0
        @Volatile var bufferFrames = 0
        @Volatile var burstFrames = 0
        @Volatile var underruns = 0
        @Volatile var lowLatency = false

        /** Part du temps disponible utilisée pour calculer un bloc (1,0 = au bord du craquement). Moyenne glissante. */
        @Volatile var loadAverage = 0f

        /** Pire bloc depuis la dernière remise à zéro ([resetMax]). */
        @Volatile var loadMax = 0f
        @Volatile var failed = false
    }

    val stats = Stats()

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start(context: Context): Boolean {
        if (running) return true
        val rate = engine.sampleRate
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBytes <= 0) {
            stats.failed = true
            return false
        }
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Grand à la construction (le système peut refuser trop petit), réduit ensuite à la demande.
                .setBufferSizeInBytes(maxOf(minBytes, blockFrames * 2 * 4 * 8))
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack indisponible", e)
            stats.failed = true
            return false
        }
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            stats.failed = true
            return false
        }

        stats.failed = false
        stats.sampleRate = rate
        stats.lowLatency = t.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
        stats.burstFrames = burstFrames(context)
        stats.loadAverage = 0f
        stats.loadMax = 0f
        track = t
        setBufferBlocks(2)

        running = true
        thread = Thread({ loop(t) }, "StudioAudio").also { it.start() }
        return true
    }

    /** Taille du tampon en nombre de blocs (2 = le plus court). Effet immédiat. */
    fun setBufferBlocks(blocks: Int) {
        val t = track ?: return
        val frames = (blocks.coerceIn(2, 8) * blockFrames).coerceAtLeast(stats.burstFrames)
        stats.bufferFrames = t.setBufferSizeInFrames(frames)
    }

    fun resetMax() {
        stats.loadMax = 0f
    }

    fun stop() {
        running = false
        val th = thread
        thread = null
        try {
            th?.join(1000)
        } catch (_: InterruptedException) {
        }
        track?.let {
            try {
                it.pause()
                it.flush()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
    }

    private fun loop(t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val left = FloatArray(blockFrames)
        val right = FloatArray(blockFrames)
        val inter = FloatArray(blockFrames * 2)
        val budgetNs = blockFrames * 1_000_000_000.0 / engine.sampleRate
        var started = false
        var blocks = 0

        while (running) {
            val t0 = System.nanoTime()
            engine.render(left, right, blockFrames)
            val spent = System.nanoTime() - t0

            for (i in 0 until blockFrames) {
                inter[2 * i] = left[i]
                inter[2 * i + 1] = right[i]
            }
            val written = t.write(inter, 0, blockFrames * 2, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.w(TAG, "Écriture refusée : $written")
                stats.failed = true
                running = false
                break
            }

            // On ne démarre la lecture qu'une fois deux blocs en file (le tampon le plus court en tient exactement deux :
            // en attendre davantage bloquerait l'écriture d'une sortie qui ne joue pas encore).
            if (!started && ++blocks >= 2) {
                t.play()
                started = true
            }

            val load = (spent / budgetNs).toFloat()
            stats.loadAverage += (load - stats.loadAverage) * 0.05f
            if (load > stats.loadMax) stats.loadMax = load
            if (started) stats.underruns = t.underrunCount
        }
    }

    companion object {
        private const val TAG = "StudioAudio"

        /** Fréquence native de la sortie (48 000 Hz presque partout) : la jouer telle quelle évite un ré-échantillonnage du système. */
        fun preferredSampleRate(context: Context): Int {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000
        }

        fun burstFrames(context: Context): Int {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        }
    }
}
