package com.Atom2Universe.app.studio.export

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs

/** Le son enregistré au micro pour la piste de percussion : nettoyage (pur), enregistrement, et stockage. */
object SampleTools {

    const val RATE = 48_000

    /** Durée de l'enregistrement, en secondes. */
    const val SECONDS = 2

    /**
     * Nettoie un enregistrement : coupe le silence du début et de la fin, normalise, adoucit les bords (pas de clic).
     * Rend `null` quand rien n'a été entendu.
     */
    fun clean(pcm: ShortArray, count: Int = pcm.size): FloatArray? {
        var peak = 0f
        for (i in 0 until count) peak = maxOf(peak, abs(pcm[i] / 32768f))
        if (peak < 0.02f) return null
        val loud = peak * 0.1f
        val quiet = peak * 0.02f
        var start = 0
        while (start < count && abs(pcm[start] / 32768f) < loud) start++
        var end = count - 1
        while (end > start && abs(pcm[end] / 32768f) < quiet) end--
        start = maxOf(0, start - 240) // un peu d'attaque avant
        end = minOf(count - 1, end + 480)
        val n = end - start + 1
        if (n < 64) return null
        val out = FloatArray(n) { pcm[start + it] / 32768f * (0.9f / peak) }
        val fadeIn = minOf(48, n / 4)
        for (i in 0 until fadeIn) out[i] *= i / fadeIn.toFloat()
        val fadeOut = minOf(960, n / 2)
        for (i in 0 until fadeOut) out[n - 1 - i] *= i / fadeOut.toFloat()
        return out
    }

    /**
     * La note que chante un enregistrement (note MIDI avec décimales), pour que le synthé le rejoue juste : méthode YIN sur le
     * passage le plus fort. Un son sans hauteur nette (souffle, claquement) rend 60 (le do du milieu : joué tel quel sur cette touche).
     */
    fun detectRoot(data: FloatArray, rate: Int): Float {
        val window = minOf(4096, data.size)
        val minLag = rate / 1000 // 1 000 Hz au plus aigu
        val maxLag = minOf(rate / 60, window / 2) // 60 Hz au plus grave
        if (maxLag <= minLag + 2) return 60f
        // Le passage le plus fort
        var best = 0
        var bestEnergy = -1.0
        var start = 0
        while (start + window <= data.size) {
            var e = 0.0
            for (i in start until start + window) e += data[i].toDouble() * data[i]
            if (e > bestEnergy) { bestEnergy = e; best = start }
            start += 1024
        }
        val n = window - maxLag
        val d = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in best until best + n) {
                val diff = data[i] - data[i + lag]
                sum += diff * diff
            }
            d[lag] = sum
        }
        // Différence normalisée par sa moyenne cumulée : le premier creux franc sous le seuil donne la période
        val norm = DoubleArray(maxLag + 1) { 1.0 }
        var running = 0.0
        for (lag in 1..maxLag) {
            running += d[lag]
            norm[lag] = if (running > 0.0) d[lag] * lag / running else 1.0
        }
        var tau = -1
        var lag = minLag
        while (lag < maxLag) {
            if (norm[lag] < 0.15) {
                while (lag + 1 < maxLag && norm[lag + 1] < norm[lag]) lag++
                tau = lag
                break
            }
            lag++
        }
        if (tau < 0) {
            var m = minLag
            for (k in minLag..maxLag) if (norm[k] < norm[m]) m = k
            if (norm[m] > 0.4) return 60f
            tau = m
        }
        // Affinage entre deux échantillons (parabole sur les trois points)
        var period = tau.toDouble()
        if (tau in 2 until maxLag) {
            val a = norm[tau - 1]
            val b = norm[tau]
            val c = norm[tau + 1]
            val den = a - 2 * b + c
            if (den > 1e-12) period += 0.5 * (a - c) / den
        }
        val freq = rate / period
        val midi = 69.0 + 12.0 * kotlin.math.ln(freq / 440.0) / kotlin.math.ln(2.0)
        return midi.toFloat().coerceIn(24f, 96f)
    }

    /** Enregistre [SECONDS] secondes au micro (bloquant : à lancer hors du fil de l'interface). `null` si le micro ne marche pas. */
    @SuppressLint("MissingPermission")
    fun record(): ShortArray? {
        val total = RATE * SECONDS
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
        } catch (_: Exception) {
            return null
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        val data = ShortArray(total)
        var got = 0
        try {
            rec.startRecording()
            while (got < total) {
                val r = rec.read(data, got, minOf(2048, total - got))
                if (r <= 0) break
                got += r
            }
            rec.stop()
        } finally {
            rec.release()
        }
        return if (got > 0) data.copyOf(got) else null
    }

    /** Le fichier du son de la percussion ; celui du synthé est [SYNTH_FILE]. */
    const val PERC_FILE = "studio_sample.bin"
    const val SYNTH_FILE = "studio_synth_sample.bin"

    private fun file(context: Context, name: String) = File(context.filesDir, name)

    /** Garde le son (16 bits) ; `null` l'efface. */
    fun save(context: Context, data: FloatArray?, name: String = PERC_FILE) {
        val f = file(context, name)
        if (data == null) {
            f.delete()
            return
        }
        DataOutputStream(f.outputStream().buffered()).use { out ->
            out.writeInt(RATE)
            out.writeInt(data.size)
            for (v in data) out.writeShort((v.coerceIn(-1f, 1f) * 32767f).toInt())
        }
    }

    fun load(context: Context, name: String = PERC_FILE): Pair<FloatArray, Int>? {
        val f = file(context, name)
        if (!f.exists()) return null
        return try {
            DataInputStream(f.inputStream().buffered()).use { input ->
                val rate = input.readInt()
                val n = input.readInt()
                if (n <= 0 || n > RATE * 10 || rate !in 8_000..192_000) return null
                FloatArray(n) { input.readShort() / 32767f } to rate
            }
        } catch (_: Exception) {
            null
        }
    }
}
