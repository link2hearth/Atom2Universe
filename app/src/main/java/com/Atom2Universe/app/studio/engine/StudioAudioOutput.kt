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
 *
 * Quand la sortie meurt en route (casque branché ou débranché, enceinte Bluetooth), le fil audio en rouvre une
 * lui-même : le son revient sans quitter l'écran.
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

    /** Protège [track] : l'écran l'arrête ou règle son tampon pendant que le fil audio peut être en train de le remplacer. */
    private val trackLock = Any()
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    /**
     * Le numéro de la boucle audio en cours. Si l'arrêt n'a pas pu attendre l'ancienne (écriture bloquée lors d'un changement
     * de sortie), elle s'en va d'elle-même au lieu de tourner à côté de la nouvelle ou de l'arrêter en échouant.
     */
    @Volatile private var generation = 0
    @Volatile private var bufferBlocks = 2

    fun start(context: Context): Boolean {
        if (running) return true
        val t = open()
        if (t == null) {
            stats.failed = true
            return false
        }

        stats.failed = false
        stats.sampleRate = engine.sampleRate
        stats.burstFrames = burstFrames(context)
        stats.loadAverage = 0f
        stats.loadMax = 0f
        synchronized(trackLock) {
            track = t
            applyBuffer(t)
        }

        running = true
        val gen = ++generation
        thread = Thread({ loop(t, gen) }, "StudioAudio").also { it.start() }
        return true
    }

    /** Fabrique une sortie prête à recevoir du son ; `null` si le système n'en donne pas. */
    private fun open(): AudioTrack? {
        val rate = engine.sampleRate
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBytes <= 0) return null
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
            return null
        }
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            return null
        }
        stats.lowLatency = t.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
        return t
    }

    /** Taille du tampon en nombre de blocs (2 = le plus court). Effet immédiat. */
    fun setBufferBlocks(blocks: Int) {
        bufferBlocks = blocks.coerceIn(2, 8)
        synchronized(trackLock) { track?.let(::applyBuffer) }
    }

    private fun applyBuffer(t: AudioTrack) {
        val frames = (bufferBlocks * blockFrames).coerceAtLeast(stats.burstFrames)
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
        synchronized(trackLock) {
            track?.let(::close)
            track = null
        }
    }

    private fun close(t: AudioTrack) {
        try {
            t.pause()
            t.flush()
        } catch (_: IllegalStateException) {
        }
        t.release()
    }

    /** La sortie [dead] ne prend plus de son : on la remplace. `null` si on arrête, ou si le système n'en donne pas d'autre. */
    private fun reopen(dead: AudioTrack, gen: Int): AudioTrack? = synchronized(trackLock) {
        if (!running || gen != generation || track !== dead) return null
        close(dead)
        val next = open()
        track = next
        if (next != null) applyBuffer(next)
        next
    }

    private fun loop(first: AudioTrack, gen: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var t = first
        val left = FloatArray(blockFrames)
        val right = FloatArray(blockFrames)
        val inter = FloatArray(blockFrames * 2)
        val budgetNs = blockFrames * 1_000_000_000.0 / engine.sampleRate
        var started = false
        var blocks = 0
        var refused = 0

        while (running && gen == generation) {
            val t0 = System.nanoTime()
            engine.render(left, right, blockFrames)
            val spent = System.nanoTime() - t0

            for (i in 0 until blockFrames) {
                inter[2 * i] = left[i]
                inter[2 * i + 1] = right[i]
            }
            val written = t.write(inter, 0, blockFrames * 2, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                // À l'arrêt, l'écran a pu fermer la sortie pendant l'écriture : ce n'est pas une panne
                if (!running || gen != generation) break
                Log.w(TAG, "Écriture refusée : $written")
                // La sortie a changé sous nos pieds (casque, Bluetooth) : on en rouvre une. Si la nouvelle refuse aussi
                // plusieurs fois de suite, c'est une vraie panne et on s'arrête là.
                val next = if (++refused <= MAX_REOPENS) reopen(t, gen) else null
                if (next == null) {
                    // Une ancienne boucle qui échoue ne doit pas arrêter la nouvelle
                    if (gen == generation) {
                        if (running) stats.failed = true
                        running = false
                    }
                    break
                }
                t = next
                started = false
                blocks = 0
                continue
            }
            refused = 0

            // On ne démarre la lecture qu'une fois deux blocs en file (le tampon le plus court en tient exactement deux :
            // en attendre davantage bloquerait l'écriture d'une sortie qui ne joue pas encore).
            if (!started && ++blocks >= 2) {
                try {
                    t.play()
                } catch (_: IllegalStateException) {
                    // Fermée par l'écran entre-temps : l'écriture suivante le dira
                }
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

        /** Nombre de sorties qu'on rouvre à la suite sans qu'aucune n'accepte un bloc, avant d'abandonner. */
        private const val MAX_REOPENS = 3

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
