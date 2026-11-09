package com.Atom2Universe.app.studio.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/*
 * La batterie électro, entièrement fabriquée par code (aucun fichier son). Chaque instrument est
 * monophonique : une nouvelle frappe reprend la précédente. Les enveloppes sont exponentielles et les
 * voix s'éteignent d'elles-mêmes sous −80 dB.
 */

private const val TWO_PI = (2.0 * PI).toFloat()
private const val SILENT = 1e-4f

/** Le rapport de fréquence d'un décalage de [tune] demi-tons. */
private fun semitoneRatio(tune: Int): Float = Math.pow(2.0, tune.coerceIn(-12, 12) / 12.0).toFloat()

/** Le coefficient de durée : 100 % = la durée d'origine. */
private fun lengthMul(length: Int): Float = length.coerceIn(50, 200) / 100f

/**
 * Grosse caisse : un sinus dont la hauteur chute vite (« pouf »), poussé dans un écrêtage doux qui lui ajoute
 * des harmoniques (le corps reste audible sur un petit haut-parleur qui ne rend rien sous 150 Hz), plus un
 * clic très discret de bruit adouci. Réglages tirés d'une comparaison chiffrée avec une vraie TR-909.
 *
 * Deux pièges évités : un clic de bruit blanc franc se paie en « claquement » (saut de ~40 % du pic en un
 * échantillon) ; et reprendre la phase en cours quand la frappe précédente sonne encore fait sauter l'onde
 * (la phase repart donc toujours de zéro, et l'écart avec la queue précédente s'éteint en quelques ms).
 */
open class Kick(sampleRate: Int, private val variants: List<KickParams> = KICK_VARIANTS) : Instrument(sampleRate) {

    private var p: KickParams = variants[0]
    private var ratio = 1f
    /** La hauteur de la frappe en cours (le réglage d'un pas), en plus de celle de la piste. */
    private var hit = 1f
    private val noise = Noise(11)
    private var ampT60 = p.ampT60
    private var ampCoef = Fast.decayCoef(p.ampT60, sampleRate)
    private var pitchCoef = Fast.decayCoef(p.pitchT60, sampleRate)
    private val clickCoef = Fast.decayCoef(0.008f, sampleRate)
    private val clickLp = (1.0 - exp(-2.0 * PI * 2500.0 / sampleRate)).toFloat()
    private val declickCoef = Fast.decayCoef(0.03f, sampleRate)

    private var phase = 0f
    private var amp = 0f
    private var pitchEnv = 0f
    private var clickEnv = 0f
    private var clickState = 0f
    private var vel = 0f
    private var lastOut = 0f
    private var offset = 0f

    override val activeVoices get() = if (amp > SILENT) 1 else 0

    override fun setVoice(kit: Int, tune: Int, length: Int) {
        val v = variants[kit.coerceIn(0, variants.size - 1)]
        val m = lengthMul(length)
        ratio = semitoneRatio(tune)
        ampT60 = v.ampT60 * m
        ampCoef = Fast.decayCoef(ampT60, sampleRate)
        pitchCoef = Fast.decayCoef(v.pitchT60, sampleRate)
        p = v
    }

    override fun noteOn(note: Int, velocity: Float) {
        hit = semitoneRatio(note - 60)
        ampCoef = Fast.decayCoef(ampT60 * hold, sampleRate)
        offset = lastOut
        phase = 0f
        amp = 1f
        pitchEnv = 1f
        clickEnv = 1f
        vel = velocity
    }

    override fun allOff() {
        amp = 0f
    }

    override fun renderMono(out: FloatArray, count: Int): Boolean {
        if (amp <= SILENT) return false
        val p = p
        val lo = p.endHz * ratio * hit
        val span = (p.startHz - p.endHz) * ratio * hit
        for (i in 0 until count) {
            if (amp <= SILENT) {
                out[i] = 0f
                lastOut = 0f
                offset = 0f
                continue
            }
            phase += (lo + span * pitchEnv) / sampleRate
            if (phase >= 1f) phase -= 1f
            clickState += (noise.next() - clickState) * clickLp
            val s = sin(phase * TWO_PI) * amp + clickState * clickEnv * p.click
            val y = Fast.tanh(s * p.drive) * (p.gain / Fast.tanh(p.drive)) * vel + offset
            out[i] = y
            lastOut = y
            offset *= declickCoef
            pitchEnv *= pitchCoef
            amp *= ampCoef
            clickEnv *= clickCoef
        }
        return true
    }
}

/** Réglages d'une grosse caisse (ou d'un tom) : hauteur de départ et d'arrivée, durées, écrêtage, clic. */
class KickParams(
    val startHz: Float = 180f,
    val endHz: Float = 50f,
    val ampT60: Float = 0.38f,
    val pitchT60: Float = 0.08f,
    val drive: Float = 2.0f,
    val click: Float = 0.05f,
    val gain: Float = 0.8f,
)

/** Les trois grosses caisses : celle d'origine, une longue et grave (le « boum »), une sèche et dure. */
val KICK_VARIANTS = listOf(
    KickParams(),
    KickParams(startHz = 130f, endHz = 40f, ampT60 = 0.85f, pitchT60 = 0.12f, drive = 1.3f, click = 0.02f, gain = 0.85f),
    KickParams(startHz = 240f, endHz = 58f, ampT60 = 0.22f, pitchT60 = 0.04f, drive = 3.5f, click = 0.12f, gain = 0.75f),
)

/** Les trois toms : celui d'origine (mesuré sur le tom moyen d'une TR-808 : ~150 Hz), un grave, un aigu. */
val TOM_VARIANTS = listOf(
    KickParams(startHz = 200f, endHz = 130f, ampT60 = 0.3f, pitchT60 = 0.05f, drive = 1.4f, click = 0.02f),
    KickParams(startHz = 150f, endHz = 90f, ampT60 = 0.4f, pitchT60 = 0.06f, drive = 1.4f, click = 0.02f),
    KickParams(startHz = 320f, endHz = 220f, ampT60 = 0.25f, pitchT60 = 0.04f, drive = 1.2f, click = 0.03f),
)

/** Tom : la même mécanique que la grosse caisse, plus aigu et plus court. */
class Tom(sampleRate: Int) : Kick(sampleRate, TOM_VARIANTS)

/** Caisse claire : bruit passé au passe-haut (le « timbre ») plus un ton de fût qui descend. */
class Snare(sampleRate: Int) : Instrument(sampleRate) {

    private val noise = Noise(23)
    private val filter = Svf(sampleRate).apply { set(1400f, 0.8f) }
    private var noiseT60 = 0.2f
    private var toneT60 = 0.1f
    private var noiseCoef = Fast.decayCoef(0.2f, sampleRate)
    private var toneCoef = Fast.decayCoef(0.1f, sampleRate)
    private val pitchCoef = Fast.decayCoef(0.05f, sampleRate)
    private var bodyHz = 180f
    private var hpHz = 1400f
    private var ratio = 1f
    private var hit = 1f

    private var noiseEnv = 0f
    private var toneEnv = 0f
    private var pitchEnv = 0f
    private var phase = 0f
    private var vel = 0f

    override val activeVoices get() = if (noiseEnv > SILENT || toneEnv > SILENT) 1 else 0

    override fun setVoice(kit: Int, tune: Int, length: Int) {
        val v = SNARE_VARIANTS[kit.coerceIn(0, SNARE_VARIANTS.size - 1)]
        val m = lengthMul(length)
        ratio = semitoneRatio(tune)
        bodyHz = v.bodyHz
        hpHz = v.highPassHz
        noiseT60 = v.noiseT60 * m
        toneT60 = v.toneT60 * m
        noiseCoef = Fast.decayCoef(noiseT60, sampleRate)
        toneCoef = Fast.decayCoef(toneT60, sampleRate)
        filter.set(hpHz * ratio, 0.8f)
    }

    override fun noteOn(note: Int, velocity: Float) {
        hit = semitoneRatio(note - 60)
        noiseCoef = Fast.decayCoef(noiseT60 * hold, sampleRate)
        toneCoef = Fast.decayCoef(toneT60 * hold, sampleRate)
        filter.set(hpHz * ratio * hit, 0.8f)
        noiseEnv = 1f
        toneEnv = 1f
        pitchEnv = 1f
        vel = velocity
    }

    override fun allOff() {
        noiseEnv = 0f
        toneEnv = 0f
    }

    override fun renderMono(out: FloatArray, count: Int): Boolean {
        if (noiseEnv <= SILENT && toneEnv <= SILENT) return false
        for (i in 0 until count) {
            // Dès que tout est éteint, l'état (bruit, filtre) cesse d'avancer : le résultat ne dépend pas du découpage en blocs.
            if (noiseEnv <= SILENT && toneEnv <= SILENT) {
                out[i] = 0f
                continue
            }
            phase += (bodyHz + 60f * pitchEnv) * ratio * hit / sampleRate
            if (phase >= 1f) phase -= 1f
            filter.tick(noise.next())
            val tone = sin(phase * TWO_PI) * toneEnv * 0.7f
            out[i] = (filter.hp * noiseEnv * 0.8f + tone) * vel * 0.8f
            noiseEnv *= noiseCoef
            toneEnv *= toneCoef
            pitchEnv *= pitchCoef
        }
        return true
    }
}

/** Une caisse claire : où le bruit commence (passe-haut), la durée du bruit et du fût, la hauteur du fût. */
class SnareParams(val highPassHz: Float, val noiseT60: Float, val toneT60: Float, val bodyHz: Float)

/** Les trois caisses claires : d'origine, courte et sèche, ample et longue. */
val SNARE_VARIANTS = listOf(
    SnareParams(1400f, 0.2f, 0.1f, 180f),
    SnareParams(2100f, 0.09f, 0.05f, 230f),
    SnareParams(900f, 0.4f, 0.14f, 150f),
)

/** Les trois claps (durée de la queue, hauteur du filtre) : d'origine, court et aigu, long et plus grave. */
private val CLAP_VARIANTS = listOf(0.16f to 1300f, 0.07f to 1700f, 0.32f to 1050f)

/** Clap : trois courtes rafales de bruit, puis une queue plus longue, le tout passé au passe-bande. */
class Clap(sampleRate: Int) : Instrument(sampleRate) {

    private val noise = Noise(37)
    private val filter = Svf(sampleRate).apply { set(1300f, 1.5f) }
    private val burstCoef = Fast.decayCoef(0.014f, sampleRate)
    private var tailT60 = 0.16f
    private var tailCoef = Fast.decayCoef(0.16f, sampleRate)
    private var bandHz = 1300f
    private var ratio = 1f
    private val b1 = (0.009f * sampleRate).toInt()
    private val b2 = (0.019f * sampleRate).toInt()
    private val b3 = (0.029f * sampleRate).toInt()

    private var t = Int.MAX_VALUE
    private var env = 0f
    private var coef = 0f
    private var vel = 0f

    override val activeVoices get() = if (env > SILENT || t <= b3) 1 else 0

    override fun setVoice(kit: Int, tune: Int, length: Int) {
        val v = CLAP_VARIANTS[kit.coerceIn(0, CLAP_VARIANTS.size - 1)]
        tailT60 = v.first * lengthMul(length)
        tailCoef = Fast.decayCoef(tailT60, sampleRate)
        bandHz = v.second
        ratio = semitoneRatio(tune)
        filter.set(bandHz * ratio, 1.5f)
    }

    override fun noteOn(note: Int, velocity: Float) {
        filter.set(bandHz * ratio * semitoneRatio(note - 60), 1.5f)
        tailCoef = Fast.decayCoef(tailT60 * hold, sampleRate)
        t = 0
        env = 0f
        coef = burstCoef
        vel = velocity
    }

    override fun allOff() {
        env = 0f
        t = Int.MAX_VALUE
    }

    override fun renderMono(out: FloatArray, count: Int): Boolean {
        if (env <= SILENT && t > b3) return false
        for (i in 0 until count) {
            if (env <= SILENT && t > b3) {
                out[i] = 0f
                continue
            }
            if (t == 0 || t == b1 || t == b2) {
                env = 1f
            } else if (t == b3) {
                env = 1f
                coef = tailCoef
            }
            if (t <= b3) t++
            filter.tick(noise.next())
            out[i] = filter.bp * env * 2.2f * vel * 0.8f
            env *= coef
        }
        return true
    }
}

/**
 * Charleston : six carrés désaccordés (la recette des boîtes à rythmes classiques) passés au passe-haut puis
 * adoucis au-dessus de 9 kHz, plus un peu de bruit. Le spectre vise celui d'un vrai charleston de TR-909 (le gros
 * de l'énergie entre 5 et 12 kHz) : bien plus haut, il devient inaudible sur un petit haut-parleur.
 * [open] allonge la queue ; [choke] la coupe en quelques millisecondes.
 */
class Hat(sampleRate: Int, private val open: Boolean) : Instrument(sampleRate) {

    private val noise = Noise(if (open) 53 else 47)
    private val highPass = Svf(sampleRate).apply { set(5500f, 0.7f) }
    private val lowPass = Svf(sampleRate).apply { set(9000f, 0.7f) }
    private var decayT60 = if (open) 0.45f else 0.09f
    private var normalCoef = Fast.decayCoef(if (open) 0.45f else 0.09f, sampleRate)
    private var ratio = 1f
    private var hit = 1f
    private var hpHz = 5500f
    private var lpHz = 9000f
    private val chokeCoef = Fast.decayCoef(0.01f, sampleRate)
    private val phases = FloatArray(6)

    private var env = 0f
    private var coef = normalCoef
    private var vel = 0f

    override val activeVoices get() = if (env > SILENT) 1 else 0

    override fun noteOn(note: Int, velocity: Float) {
        hit = semitoneRatio(note - 60)
        normalCoef = Fast.decayCoef(decayT60 * hold, sampleRate)
        highPass.set(hpHz * ratio * hit, 0.7f)
        lowPass.set(lpHz * ratio * hit, 0.7f)
        env = 1f
        coef = normalCoef
        vel = velocity
    }

    override fun choke() {
        coef = chokeCoef
    }

    override fun setVoice(kit: Int, tune: Int, length: Int) {
        val v = (if (open) OPEN_HAT_VARIANTS else HAT_VARIANTS)[kit.coerceIn(0, 2)]
        ratio = semitoneRatio(tune)
        decayT60 = v.decay * lengthMul(length)
        normalCoef = Fast.decayCoef(decayT60, sampleRate)
        hpHz = v.highPassHz
        lpHz = v.lowPassHz
        highPass.set(hpHz * ratio, 0.7f)
        lowPass.set(lpHz * ratio, 0.7f)
    }

    override fun allOff() {
        env = 0f
    }

    override fun renderMono(out: FloatArray, count: Int): Boolean {
        if (env <= SILENT) return false
        for (i in 0 until count) {
            if (env <= SILENT) {
                out[i] = 0f
                continue
            }
            var sum = 0f
            for (j in 0 until 6) {
                var p = phases[j] + FREQS[j] * ratio * hit / sampleRate
                if (p >= 1f) p -= 1f
                phases[j] = p
                sum += if (p < 0.5f) 1f else -1f
            }
            highPass.tick(sum * (1f / 6f) + noise.next() * 0.3f)
            lowPass.tick(highPass.hp)
            out[i] = lowPass.lp * env * vel * HAT_GAIN
            env *= coef
        }
        return true
    }

    private companion object {
        val FREQS = floatArrayOf(205.3f, 304.4f, 369.6f, 522.7f, 540f, 800f)
        const val HAT_GAIN = 1.6f
    }
}

/** Un charleston : les deux filtres qui dessinent son brillant et la durée de sa queue. */
class HatParams(val highPassHz: Float, val lowPassHz: Float, val decay: Float)

/** Les trois charlestons fermés : d'origine, serré et brillant, lourd et plus sourd. */
val HAT_VARIANTS = listOf(HatParams(5500f, 9000f, 0.09f), HatParams(7000f, 10500f, 0.045f), HatParams(4200f, 8000f, 0.15f))

/** Les trois charlestons ouverts : d'origine, court, long. */
val OPEN_HAT_VARIANTS = listOf(HatParams(5500f, 9000f, 0.45f), HatParams(5500f, 9000f, 0.25f), HatParams(5000f, 9000f, 0.9f))

/**
 * Percussion métallique « cloche » : deux carrés désaccordés (540 et 800 Hz, la recette de la TR-808) passés au
 * passe-bande autour de 830 Hz. Mesuré sur une vraie cloche de TR-808 : pics à ~560 et ~840 Hz, éteinte à −30 dB en 0,18 s.
 */
class Perc(sampleRate: Int) : Instrument(sampleRate) {

    private val filter = Svf(sampleRate).apply { set(830f, 1.1f) }
    private var t60 = 0.35f
    private var coef = Fast.decayCoef(0.35f, sampleRate)
    private var freqA = 540f
    private var freqB = 800f
    private var filterHz = 830f
    private var ratio = 1f
    private var hit = 1f

    private var phaseA = 0f
    private var phaseB = 0f
    private var env = 0f
    private var vel = 0f

    /** Le son enregistré au micro : s'il existe, il remplace la cloche. [sampleRate] est celui de l'enregistrement. */
    @Volatile private var sample: FloatArray? = null
    @Volatile private var sampleSourceRate = 48_000
    private var playing: FloatArray? = null
    private var samplePos = 0.0
    private var sampleStep = 1.0

    fun setSample(data: FloatArray?, sourceRate: Int) {
        sampleSourceRate = sourceRate
        sample = data
    }

    val hasSample: Boolean get() = sample != null

    override fun setVoice(kit: Int, tune: Int, length: Int) {
        val v = PERC_VARIANTS[kit.coerceIn(0, PERC_VARIANTS.size - 1)]
        ratio = semitoneRatio(tune)
        freqA = v.freqA
        freqB = v.freqB
        t60 = v.t60 * lengthMul(length)
        coef = Fast.decayCoef(t60, sampleRate)
        filterHz = v.filterHz
        filter.set(filterHz * ratio, 1.1f)
    }

    override val activeVoices get() = if (env > SILENT || playing != null) 1 else 0

    override fun noteOn(note: Int, velocity: Float) {
        val s = sample
        if (s != null) {
            // On rejoue l'enregistrement, plus aigu ou plus grave selon la note (60 = tel quel)
            playing = s
            samplePos = 0.0
            sampleStep = sampleSourceRate.toDouble() / sampleRate * Math.pow(2.0, (note - 60) / 12.0) * ratio
            vel = velocity
            return
        }
        playing = null
        hit = semitoneRatio(note - 60)
        coef = Fast.decayCoef(t60 * hold, sampleRate)
        filter.set(filterHz * ratio * hit, 1.1f)
        env = 1f
        vel = velocity
    }

    override fun allOff() {
        env = 0f
        playing = null
    }

    override fun renderMono(out: FloatArray, count: Int): Boolean {
        val data = playing
        if (data != null) {
            for (i in 0 until count) {
                val p = samplePos.toInt()
                if (p >= data.size - 1) {
                    playing = null
                    for (k in i until count) out[k] = 0f
                    return i > 0
                }
                val f = (samplePos - p).toFloat()
                out[i] = (data[p] * (1f - f) + data[p + 1] * f) * vel
                samplePos += sampleStep
            }
            return true
        }
        if (env <= SILENT) return false
        for (i in 0 until count) {
            if (env <= SILENT) {
                out[i] = 0f
                continue
            }
            phaseA += freqA * ratio * hit / sampleRate
            if (phaseA >= 1f) phaseA -= 1f
            phaseB += freqB * ratio * hit / sampleRate
            if (phaseB >= 1f) phaseB -= 1f
            val sq = (if (phaseA < 0.5f) 1f else -1f) + (if (phaseB < 0.5f) 1f else -1f)
            filter.tick(sq * 0.5f)
            out[i] = filter.bp * env * vel * 1.2f
            env *= coef
        }
        return true
    }
}

/** Une percussion métallique : ses deux fréquences, le centre de son filtre et sa durée. */
class PercParams(val freqA: Float, val freqB: Float, val filterHz: Float, val t60: Float)

/** Les trois percussions : la cloche d'origine, un bloc de bois (bref), une cloche aiguë. */
val PERC_VARIANTS = listOf(
    PercParams(540f, 800f, 830f, 0.35f),
    PercParams(880f, 1320f, 1200f, 0.07f),
    PercParams(1080f, 1600f, 1650f, 0.2f),
)
