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

    private fun file(context: Context) = File(context.filesDir, "studio_sample.bin")

    /** Garde le son (16 bits) ; `null` l'efface. */
    fun save(context: Context, data: FloatArray?) {
        val f = file(context)
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

    fun load(context: Context): Pair<FloatArray, Int>? {
        val f = file(context)
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
