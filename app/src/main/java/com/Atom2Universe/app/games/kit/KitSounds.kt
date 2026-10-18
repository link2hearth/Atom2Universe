package com.Atom2Universe.app.games.kit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Le carillon de victoire des puzzles du kit : un arpège montant (do, mi, sol, do) puis l'accord
 * tenu, avec un timbre de cloche. Synthétisé une fois (aucun fichier, aucun crédit), joué par un
 * AudioTrack. Il se tait si le téléphone est en silencieux ou si de la musique joue déjà (le
 * lecteur de l'appli, par exemple) : on ne coupe pas la musique de quelqu'un pour le féliciter.
 */
object KitSounds {
    private const val RATE = 22050
    private val victoryPcm: ShortArray by lazy { renderVictory() }

    fun victory(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val quiet = try {
            audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && !audio.isMusicActive &&
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        } catch (_: SecurityException) { false }
        if (!quiet) return
        Thread {
            try {
                val pcm = victoryPcm
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setVolume(0.55f)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 100L)
                track.release()
            } catch (_: Exception) {
                // Pas de son plutôt qu'un plantage : la fête visuelle suffit.
            }
        }.start()
    }

    private fun renderVictory(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        val step = 0.11
        val total = step * notes.size + 1.1
        val out = DoubleArray((RATE * total).toInt())
        fun bell(freq: Double, start: Double, length: Double, gain: Double) {
            val from = (start * RATE).toInt()
            val n = (length * RATE).toInt()
            for (i in 0 until n) {
                val idx = from + i
                if (idx >= out.size) break
                val t = i.toDouble() / RATE
                val env = (if (t < 0.006) t / 0.006 else 1.0) * exp(-t * 4.5)
                // Cloche : fondamentale, octave et une partielle un peu désaccordée.
                val v = sin(2 * PI * freq * t) + 0.35 * sin(2 * PI * freq * 2 * t) + 0.12 * sin(2 * PI * freq * 2.76 * t)
                out[idx] += v * env * gain
            }
        }
        notes.forEachIndexed { k, f -> bell(f, k * step, 0.9, 0.5) }
        val chord = step * notes.size
        for (f in doubleArrayOf(523.25, 659.25, 783.99, 1046.5)) bell(f, chord, 1.1, 0.3)
        var peak = 0.0
        for (v in out) peak = maxOf(peak, kotlin.math.abs(v))
        val edge = 300
        for (i in 0 until edge) out[out.size - 1 - i] *= i.toDouble() / edge
        return ShortArray(out.size) { (out[it] / peak * 0.85 * Short.MAX_VALUE).toInt().toShort() }
    }
}
