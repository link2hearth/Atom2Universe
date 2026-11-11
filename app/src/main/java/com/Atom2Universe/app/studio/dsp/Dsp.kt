package com.Atom2Universe.app.studio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/*
 * Les briques du son du Studio. Rien ici ne connaît Android : tout se teste en JVM sur de simples
 * tableaux. Règle de la boucle de rendu : aucune allocation, et le résultat ne dépend jamais de la
 * façon dont on découpe les blocs (les compteurs « tous les 16 échantillons » sont des compteurs
 * d'échantillons, pas des compteurs de blocs).
 */

/** Bruit blanc (xorshift32), reproductible : même graine, même bruit. */
class Noise(seed: Int = 0x1234567) {
    private var s = if (seed == 0) 1 else seed

    /** Un échantillon dans [-1, 1[. */
    fun next(): Float {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x * (1f / 2147483648f)
    }
}

object Fast {
    private const val KNEE = 0.85f

    /** tanh rationnel : exact aux deux bouts (±1 à ±3), assez doux entre les deux pour un son. */
    fun tanh(x: Float): Float {
        val c = if (x > 3f) 3f else if (x < -3f) -3f else x
        return c * (27f + c * c) / (27f + 9f * c * c)
    }

    /**
     * Limiteur doux de sortie : transparent jusqu'à 0,85, puis courbe qui s'approche de 1 sans
     * jamais l'atteindre brutalement (pas de plat dur, donc pas de grésillement). Ne dépasse jamais 1.
     */
    fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val y = KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE))
        return if (x < 0f) -y else y
    }

    /** Coefficient à multiplier à chaque échantillon pour que l'enveloppe perde 60 dB en [t60] secondes. */
    fun decayCoef(t60: Float, sampleRate: Int): Float =
        exp(-6.907755 / (t60.coerceAtLeast(0.0005f) * sampleRate)).toFloat()
}

/** Filtre à variables d'état (Zavalishin, TPT) : stable même cutoff très haut ou modulé vite. */
class Svf(private val sampleRate: Int) {
    private var g = 0f
    private var k = 1f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var ic1 = 0f
    private var ic2 = 0f

    var lp = 0f
        private set
    var bp = 0f
        private set
    var hp = 0f
        private set

    init {
        set(1000f, 0.7f)
    }

    fun set(cutoffHz: Float, q: Float) {
        val fc = cutoffHz.coerceIn(10f, sampleRate * 0.45f)
        g = tan(PI * fc / sampleRate).toFloat()
        k = 1f / q.coerceAtLeast(0.3f)
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun reset() {
        ic1 = 0f
        ic2 = 0f
        lp = 0f
        bp = 0f
        hp = 0f
    }

    /** Fait avancer d'un échantillon ; les sorties sont dans [lp], [bp] et [hp]. */
    fun tick(x: Float) {
        val v3 = x - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2f * v1 - ic1
        ic2 = 2f * v2 - ic2
        lp = v2
        bp = v1
        hp = x - k * v1 - v2
    }
}

/**
 * Les sous-classes produisent le signal central et, pour un son stéréo, sa différence gauche/droite. La base les place
 * dans le mixage stéréo. [renderMono] est appelé par morceaux d'au plus [MAX_CHUNK] trames.
 */
abstract class Instrument(protected val sampleRate: Int) {

    @Volatile var level = 1f
    @Volatile var pan = 0f

    /** La durée du coup qui va partir (batterie) : 1 = celle du son, 2 = deux fois plus long. Posée par le moteur avant chaque frappe. */
    @Volatile var hold = 1f

    /** Part envoyée à la réverbération et à l'écho (0 = rien, 1 = autant que le son sec). */
    @Volatile var reverbSend = 0f
    @Volatile var delaySend = 0f

    /** Muet : l'instrument continue de tourner (son état ne dépend pas du muet) mais n'est pas mixé. */
    @Volatile var muted = false

    // Effets de piste (0 = coupé, sans aucun coût) : dégradation lo-fi, distorsion, puis filtre.
    @Volatile var crush = 0f
    @Volatile var drive = 0f

    /** −1 = coupe les aigus (étouffé), +1 = coupe les graves (mince), 0 = pas de filtre. */
    @Volatile var filterSweep = 0f

    /** La « pompe » : de combien la piste se creuse à chaque coup de grosse caisse (0 = pas du tout, 1 = presque au silence). */
    @Volatile var pump = 0f

    /**
     * La distorsion jouée en direct (hauteur du doigt sur une touche des pads), de 0 (son net) à 1 : elle s'ajoute à [drive]
     * et, comme elle, garde le niveau du son. Lissée par échantillon : pas de saut audible.
     */
    @Volatile var liveDrive = 0f

    // Passe-bas du pad, après les effets du mixeur. Un pôle nul laisse passer exactement le signal d'entrée.
    private val liveFilterMaxPole = exp(-2.0 * PI * 200.0 / sampleRate).toFloat()
    private val liveFilterStep = 1f / (0.012f * sampleRate)
    @Volatile private var liveFilterTarget = 0f

    fun setLiveFilter(amount: Float) {
        val open = 1f - amount.coerceIn(0f, 1f)
        liveFilterTarget = liveFilterMaxPole * (1f - open * open * open)
    }

    /**
     * Le jeu en direct (pad thérémine) : 1 = normal, vers 0 = plus doux (et plus sourd pour le synthé). Lissé par échantillon,
     * il ne fait jamais de saut audible.
     */
    @Volatile var expression = 1f

    /** L'expression telle qu'elle sonne en ce moment (elle rejoint [expression] en quelques millisecondes). */
    protected var expressionNow = 1f
        private set

    private val scratch = FloatArray(MAX_CHUNK)
    private val leftEffects = ChannelEffects()
    private var rightEffects: ChannelEffects? = null
    private var rightScratch: FloatArray? = null
    protected open val stereoDifference: FloatArray? get() = null

    abstract fun noteOn(note: Int, velocity: Float)

    open fun noteOff(note: Int) {}

    /** Coupe court (le charleston fermé coupe le charleston ouvert). */
    open fun choke() {}

    /**
     * Le caractère d'un instrument de batterie : [kit] (une des variantes du son, 0 = celle d'origine), [tune] en demi-tons
     * (−12 à +12) et [length] en pour cent de la durée d'origine (50 à 200). Sans effet sur les autres instruments.
     */
    open fun setVoice(kit: Int, tune: Int, length: Int) {}

    /** Libère tout ce qui est tenu. */
    abstract fun allOff()

    /**
     * La note tenue [from] devient [to] sans nouvelle attaque quand l'instrument le sait (le synthé glisse d'une hauteur à
     * l'autre) ; sinon on relâche l'une et on joue l'autre.
     */
    open fun glide(from: Int, to: Int, velocity: Float) {
        noteOff(from)
        noteOn(to, velocity)
    }

    abstract val activeVoices: Int

    /** Écrit [count] trames dans out[0 until count] et rend vrai ; rend faux (sans rien écrire) si tout est silencieux. */
    protected abstract fun renderMono(out: FloatArray, count: Int): Boolean

    /**
     * Ajoute le rendu de [from] (inclus) à [to] (exclu) au mixage sec stéréo ([left], [right]) et aux deux
     * bus d'envoi mono ([reverbBus], [delayBus]). [duck] (de 0 à 1, aux mêmes indices) est l'enveloppe de la grosse caisse
     * qui fait « pomper » les pistes dont [pump] n'est pas nul.
     */
    fun render(
        left: FloatArray, right: FloatArray, reverbBus: FloatArray, delayBus: FloatArray,
        from: Int, to: Int, duck: FloatArray? = null,
    ) {
        val angle = (pan.coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)
        val gl = cos(angle) * level
        val gr = sin(angle) * level
        val rs = level * reverbSend
        val ds = level * delaySend
        val audible = !muted
        val depth = if (duck != null) pump.coerceIn(0f, 1f) * PUMP_DEPTH else 0f
        val exprTarget = expression.coerceIn(0f, 1f)
        var pos = from
        while (pos < to) {
            val c = min(to - pos, MAX_CHUNK)
            val hasSound = renderMono(scratch, c)
            val side = stereoDifference
            if (side != null && rightScratch == null) {
                rightScratch = FloatArray(MAX_CHUNK)
                rightEffects = ChannelEffects()
            }
            val other = rightScratch
            if (hasSound && other != null) {
                if (side == null) scratch.copyInto(other, 0, 0, c) else for (i in 0 until c) {
                    other[i] = scratch[i] - side[i]
                    scratch[i] += side[i]
                }
            }
            val produced = leftEffects.process(scratch, c, hasSound)
            if (other != null) rightEffects!!.process(other, c, hasSound)
            // L'expression se lisse même en silence : la note suivante part du bon niveau
            var e = expressionNow
            val smoothing = e != exprTarget
            if (produced && audible) {
                for (i in 0 until c) {
                    if (smoothing) e += (exprTarget - e) * EXPRESSION_GLIDE
                    val gain = if (depth > 0f) e * (1f - depth * duck!![pos + i]) else e
                    val l = scratch[i] * gain
                    val r = other?.get(i)?.times(gain) ?: l
                    left[pos + i] += l * gl
                    right[pos + i] += r * gr
                    val send = (l + r) * 0.5f
                    reverbBus[pos + i] += send * rs
                    delayBus[pos + i] += send * ds
                }
            } else if (smoothing) {
                for (i in 0 until c) e += (exprTarget - e) * EXPRESSION_GLIDE
            }
            if (smoothing && abs(e - exprTarget) < 1e-4f) e = exprTarget
            expressionNow = e
            pos += c
        }
    }

    private inner class ChannelEffects {
        private val fxFilter = Svf(sampleRate)
        private var fxFilterFor = 0f
        private var crushCounter = 0
        private var crushHeld = 0f
        private var driveEnvIn = 1e-9f
        private var driveEnvOut = 1e-9f
        private var liveDriveNow = 0f
        private var liveFilterPole = 0f
        private var liveFilterLast = 0f

        fun process(scratch: FloatArray, count: Int, hasSound: Boolean): Boolean {
            var produced = hasSound
            if (drive > FX_MIN || crush > FX_MIN || abs(filterSweep) > FX_MIN || liveDrive > FX_MIN || liveDriveNow > FX_MIN) {
                if (!produced) java.util.Arrays.fill(scratch, 0, count, 0f)
                applyEffects(scratch, count)
                produced = true
            }
            val target = liveFilterTarget
            if (target != 0f || liveFilterPole != 0f) {
                if (!produced) java.util.Arrays.fill(scratch, 0, count, 0f)
                applyLiveFilter(scratch, count, target)
                produced = true
            }
            return produced
        }

        private fun applyLiveFilter(scratch: FloatArray, count: Int, target: Float) {
            var pole = liveFilterPole
            var last = if (pole == 0f) scratch[0] else liveFilterLast
            for (i in 0 until count) {
                pole += (target - pole).coerceIn(-liveFilterStep, liveFilterStep)
                val input = scratch[i]
                last = input + pole * (last - input)
                scratch[i] = last
            }
            liveFilterLast = last
            liveFilterPole = pole
        }

        /** Dégradation, distorsion, filtre, sur les [count] premiers échantillons de [scratch]. */
        private fun applyEffects(scratch: FloatArray, count: Int) {
            val cr = crush
            if (cr > FX_MIN) {
                // De 16 à 5 bits et jusqu'à 1 échantillon sur 15 retenu : le grain des vieux échantillonneurs.
                val levels = Math.pow(2.0, (15f - cr * 11f).toDouble()).toFloat()
                val hold = 1 + (cr * cr * 14f).toInt()
                for (i in 0 until count) {
                    if (--crushCounter <= 0) {
                        crushCounter = hold
                        crushHeld = Math.round(scratch[i] * levels) / levels
                    }
                    scratch[i] = crushHeld
                }
            }
            val d = drive
            val live = liveDrive.coerceIn(0f, 1f)
            if (d > FX_MIN || live > FX_MIN || liveDriveNow > FX_MIN) {
                // Compensation qui suit le niveau : une distorsion rend un son doux bien plus fort qu'un son déjà fort,
                // donc un retrait fixe ne convient pas. On garde le niveau d'entrée (suivi sur ~10 ms, par échantillon).
                var ein = driveEnvIn
                var eout = driveEnvOut
                var ld = liveDriveNow
                for (i in 0 until count) {
                    ld += (live - ld) * LIVE_DRIVE_GLIDE
                    // Le doigt pousse bien plus fort que le réglage du mixeur : l'effet doit s'entendre franchement sous le doigt
                    val g = 1f + d * 7f + ld * LIVE_DRIVE_GAIN
                    val x = scratch[i]
                    val y = Fast.tanh(x * g)
                    ein += (x * x - ein) * DRIVE_ENV
                    eout += (y * y - eout) * DRIVE_ENV
                    val comp = Math.sqrt((ein / eout).toDouble()).toFloat().coerceIn(0.05f, 1.5f)
                    scratch[i] = y * comp
                }
                driveEnvIn = ein
                driveEnvOut = eout
                liveDriveNow = if (live <= FX_MIN && ld < FX_MIN) 0f else ld
            }
            val fs = filterSweep
            if (abs(fs) > FX_MIN) {
                if (fs != fxFilterFor) {
                    fxFilterFor = fs
                    if (fs < 0f) fxFilter.set((18000.0 * Math.pow(200.0 / 18000.0, -fs.toDouble())).toFloat(), 0.9f)
                    else fxFilter.set((20.0 * Math.pow(8000.0 / 20.0, fs.toDouble())).toFloat(), 0.9f)
                }
                if (fs < 0f) for (i in 0 until count) { fxFilter.tick(scratch[i]); scratch[i] = fxFilter.lp }
                else for (i in 0 until count) { fxFilter.tick(scratch[i]); scratch[i] = fxFilter.hp }
            } else if (fxFilterFor != 0f) {
                fxFilterFor = 0f
                fxFilter.reset()
            }
        }
    }

    companion object {
        const val MAX_CHUNK = 512
        private const val FX_MIN = 0.001f

        /** Vitesse du suivi de niveau de la compensation de distorsion (≈ 10 ms à 48 kHz). */
        private const val DRIVE_ENV = 0.002f

        /** Le creux le plus profond de la pompe (pompe à fond) : il reste un dixième du son. */
        const val PUMP_DEPTH = 0.9f

        /** Vitesse du lissage de la distorsion jouée en direct (≈ 10 ms à 48 kHz). */
        private const val LIVE_DRIVE_GLIDE = 0.002f

        /** Gain de distorsion ajouté quand le doigt est au bout de son geste. */
        private const val LIVE_DRIVE_GAIN = 24f

        /** Vitesse du lissage de l'expression (≈ 10 ms à 48 kHz). */
        private const val EXPRESSION_GLIDE = 0.002f
    }
}
