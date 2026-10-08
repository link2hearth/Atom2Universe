package com.Atom2Universe.app.games.balance

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Les sons du mobile : un carillon. Chaque objet a sa note, d'autant plus grave qu'il est lourd,
 * et le mobile achevé joue un accord.
 *
 * Tout est synthétisé (pas de fichier livré) : un WAV par son, écrit une fois dans le cache puis
 * chargé par SoundPool, sur un fil à part.
 */
class MobileSfx(private val context: Context) {

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()

    private companion object {
        const val VERSION = 1
        const val RATE = 44_100

        /** Gamme pentatonique majeure, de grave à aigu, en demi-tons au-dessus de do 4. */
        val GAMME = intArrayOf(-12, -10, -8, -5, -3, 0, 2, 4, 7, 9, 12, 14)

        /** Notes de la cascade de victoire, une par étage du mobile. */
        const val NOTES_CASCADE = 6

        /** Fréquence d'un degré de la gamme. */
        fun frequence(degre: Int): Float =
            (261.63 * 2.0.pow(GAMME[degre.coerceIn(0, GAMME.size - 1)] / 12.0)).toFloat()
    }

    @Synchronized fun start() {
        if (pool != null) return
        val sounds = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        pool = sounds
        sounds.setOnLoadCompleteListener { source, id, status ->
            synchronized(this) { if (source === pool && status == 0) loaded.add(id) }
        }
        Thread({
            try {
                val noms = (1..9).map { "masse$it" } + (0 until NOTES_CASCADE).map { "cascade$it" } +
                    listOf("prise", "juste", "victoire")
                val fichiers = noms.map { it to cached(it) }
                synchronized(this) {
                    if (pool === sounds) for ((nom, f) in fichiers) ids[nom] = sounds.load(f.path, 1)
                }
            } catch (e: Exception) {
                Log.w("MobileSfx", "Cannot prepare sounds", e)
            }
        }, "MobileAudio").start()
    }

    @Synchronized fun release() {
        pool?.release()
        pool = null
        ids.clear()
        loaded.clear()
    }

    /** Le son d'un objet qu'on accroche : grave s'il est lourd. */
    fun accroche(masse: Int) = jouer("masse${masse.coerceIn(1, 9)}", 0.8f)

    fun prise() = jouer("prise", 0.5f)

    /** Une tige vient de trouver son équilibre. */
    fun juste() = jouer("juste", 0.7f)

    fun victoire() = jouer("victoire", 0.9f)

    /** Un nœud de la cascade s'allume : [etage] 0 pour le plus bas, la note monte avec. */
    fun cascade(etage: Int) = jouer("cascade${etage.coerceIn(0, NOTES_CASCADE - 1)}", 0.6f)

    @Synchronized private fun jouer(nom: String, volume: Float) {
        val p = pool ?: return
        val id = ids[nom] ?: return
        if (id !in loaded) return
        p.play(id, volume, volume, 1, 0, 1f)
    }

    // ── Synthèse ──────────────────────────────────────────────────────────────

    private fun cached(nom: String): File {
        val file = File(context.cacheDir, "mobile_v${VERSION}_$nom.wav")
        if (file.isFile && file.length() > 44) return file
        val temp = File.createTempFile("mobile_", ".wav", context.cacheDir)
        try {
            FileOutputStream(temp).use { it.write(wav(rendre(nom))) }
            check(temp.renameTo(file)) { "Cannot cache $nom" }
        } finally {
            temp.delete()
        }
        return file
    }

    private fun rendre(nom: String): FloatArray = when {
        nom.startsWith("masse") -> {
            // Masse 1 : la note la plus aiguë ; masse 9 : la plus grave.
            val masse = nom.removePrefix("masse").toInt()
            cloche(frequence(11 - masse), 1.6f)
        }
        nom.startsWith("cascade") -> cloche(frequence(5 + nom.removePrefix("cascade").toInt()), 1.1f, 0.45f)
        nom == "prise" -> pincement()
        nom == "juste" -> {
            val a = cloche(frequence(9), 0.9f, 0.5f)
            val b = cloche(frequence(11), 0.9f, 0.5f)
            melanger(listOf(a to 0f, b to 0.07f))
        }
        else -> {
            val notes = intArrayOf(5, 7, 8, 10)
            melanger(notes.mapIndexed { i, d -> cloche(frequence(d), 2.2f, 0.7f) to i * 0.11f })
        }
    }

    /** Une cloche : quelques partiels inharmoniques qui s'éteignent chacun à son rythme. */
    private fun cloche(f: Float, duree: Float, gain: Float = 0.6f): FloatArray {
        val n = (duree * RATE).toInt()
        val out = FloatArray(n)
        val rapports = floatArrayOf(1f, 2.76f, 5.40f, 8.93f)
        val amplitudes = floatArrayOf(1f, 0.45f, 0.22f, 0.1f)
        val tenues = floatArrayOf(duree * 0.45f, duree * 0.25f, duree * 0.12f, duree * 0.07f)
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val attaque = min(1f, t / 0.004f)
            var s = 0f
            for (k in rapports.indices) {
                s += amplitudes[k] * exp(-t / tenues[k]) * sin(2.0 * PI * f * rapports[k] * t).toFloat()
            }
            out[i] = s * attaque * gain * 0.55f
        }
        return out
    }

    /** Un petit bruit de fil qu'on décroche : bref, sourd. */
    private fun pincement(): FloatArray {
        val n = (0.12f * RATE).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val env = exp(-t / 0.025f) * min(1f, t / 0.002f)
            out[i] = 0.35f * env * (sin(2.0 * PI * 520 * t) + 0.5 * sin(2.0 * PI * 1310 * t)).toFloat()
        }
        return out
    }

    private fun melanger(pistes: List<Pair<FloatArray, Float>>): FloatArray {
        val n = pistes.maxOf { it.first.size + (it.second * RATE).toInt() }
        val out = FloatArray(n)
        for ((p, debut) in pistes) {
            val d = (debut * RATE).toInt()
            for (i in p.indices) out[d + i] += p[i]
        }
        return out
    }

    private fun wav(samples: FloatArray): ByteArray {
        val data = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            data[2 * i] = (v and 0xFF).toByte()
            data[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        val out = ByteArrayOutputStream()
        fun int(v: Int) { for (k in 0 until 4) out.write((v shr (8 * k)) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
        out.write("data".toByteArray()); int(data.size); out.write(data)
        return out.toByteArray()
    }
}
