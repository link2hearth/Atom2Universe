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

/**
 * Grosse caisse : un sinus dont la hauteur chute vite (« pouf »), poussé dans un écrêtage doux qui lui ajoute
 * des harmoniques (le corps reste audible sur un petit haut-parleur qui ne rend rien sous 150 Hz), plus un
 * clic très discret de bruit adouci. Réglages tirés d'une comparaison chiffrée avec une vraie TR-909.
 *
 * Deux pièges évités : un clic de bruit blanc franc se paie en « claquement » (saut de ~40 % du pic en un
 * échantillon) ; et reprendre la phase en cours quand la frappe précédente sonne encore fait sauter l'onde
 * (la phase repart donc toujours de zéro, et l'écart avec la queue précédente s'éteint en quelques ms).
 */
open class Kick(sampleRate: Int, private val p: KickParams = KickParams()) : Instrument(sampleRate) {

    private val noise = Noise(11)
    private val ampCoef = Fast.decayCoef(p.ampT60, sampleRate)
    private val pitchCoef = Fast.decayCoef(p.pitchT60, sampleRate)
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

    override fun noteOn(note: Int, velocity: Float) {
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
        val lo = p.endHz
        val span = p.startHz - p.endHz
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

/** Tom : la même mécanique que la grosse caisse, plus aigu et plus court (mesuré sur le tom moyen d'une TR-808 : ~150 Hz). */
class Tom(sampleRate: Int) : Kick(sampleRate, KickParams(startHz = 200f, endHz = 130f, ampT60 = 0.3f, pitchT60 = 0.05f, drive = 1.4f, click = 0.02f))

/** Caisse claire : bruit passé au passe-haut (le « timbre ») plus un ton de fût qui descend. */
class Snare(sampleRate: Int) : Instrument(sampleRate) {

    private val noise = Noise(23)
    private val filter = Svf(sampleRate).apply { set(1400f, 0.8f) }
    private val noiseCoef = Fast.decayCoef(0.2f, sampleRate)
    private val toneCoef = Fast.decayCoef(0.1f, sampleRate)
    private val pitchCoef = Fast.decayCoef(0.05f, sampleRate)

    private var noiseEnv = 0f
    private var toneEnv = 0f
    private var pitchEnv = 0f
    private var phase = 0f
    private var vel = 0f

    override val activeVoices get() = if (noiseEnv > SILENT || toneEnv > SILENT) 1 else 0

    override fun noteOn(note: Int, velocity: Float) {
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
            phase += (180f + 60f * pitchEnv) / sampleRate
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

/** Clap : trois courtes rafales de bruit, puis une queue plus longue, le tout passé au passe-bande. */
class Clap(sampleRate: Int) : Instrument(sampleRate) {

    private val noise = Noise(37)
    private val filter = Svf(sampleRate).apply { set(1300f, 1.5f) }
    private val burstCoef = Fast.decayCoef(0.014f, sampleRate)
    private val tailCoef = Fast.decayCoef(0.16f, sampleRate)
    private val b1 = (0.009f * sampleRate).toInt()
    private val b2 = (0.019f * sampleRate).toInt()
    private val b3 = (0.029f * sampleRate).toInt()

    private var t = Int.MAX_VALUE
    private var env = 0f
    private var coef = 0f
    private var vel = 0f

    override val activeVoices get() = if (env > SILENT || t <= b3) 1 else 0

    override fun noteOn(note: Int, velocity: Float) {
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
    private val normalCoef = Fast.decayCoef(if (open) 0.45f else 0.09f, sampleRate)
    private val chokeCoef = Fast.decayCoef(0.01f, sampleRate)
    private val phases = FloatArray(6)

    private var env = 0f
    private var coef = normalCoef
    private var vel = 0f

    override val activeVoices get() = if (env > SILENT) 1 else 0

    override fun noteOn(note: Int, velocity: Float) {
        env = 1f
        coef = normalCoef
        vel = velocity
    }

    override fun choke() {
        coef = chokeCoef
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
                var p = phases[j] + FREQS[j] / sampleRate
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

/**
 * Percussion métallique « cloche » : deux carrés désaccordés (540 et 800 Hz, la recette de la TR-808) passés au
 * passe-bande autour de 830 Hz. Mesuré sur une vraie cloche de TR-808 : pics à ~560 et ~840 Hz, éteinte à −30 dB en 0,18 s.
 */
class Perc(sampleRate: Int) : Instrument(sampleRate) {

    private val filter = Svf(sampleRate).apply { set(830f, 1.1f) }
    private val coef = Fast.decayCoef(0.35f, sampleRate)

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

    override val activeVoices get() = if (env > SILENT || playing != null) 1 else 0

    override fun noteOn(note: Int, velocity: Float) {
        val s = sample
        if (s != null) {
            // On rejoue l'enregistrement, plus aigu ou plus grave selon la note (60 = tel quel)
            playing = s
            samplePos = 0.0
            sampleStep = sampleSourceRate.toDouble() / sampleRate * Math.pow(2.0, (note - 60) / 12.0)
            vel = velocity
            return
        }
        playing = null
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
            phaseA += 540f / sampleRate
            if (phaseA >= 1f) phaseA -= 1f
            phaseB += 800f / sampleRate
            if (phaseB >= 1f) phaseB -= 1f
            val sq = (if (phaseA < 0.5f) 1f else -1f) + (if (phaseB < 0.5f) 1f else -1f)
            filter.tick(sq * 0.5f)
            out[i] = filter.bp * env * vel * 1.2f
            env *= coef
        }
        return true
    }
}
