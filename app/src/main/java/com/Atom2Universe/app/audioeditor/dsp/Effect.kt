package com.Atom2Universe.app.audioeditor.dsp

import kotlin.math.log10
import kotlin.math.pow

/*
 * Le socle des effets. Un effet lit un [FrameReader] et écrit dans un [FrameWriter], toujours **par
 * blocs** : la mémoire reste bornée que la plage fasse dix secondes ou une heure. Ni l'un ni l'autre
 * ne connaît Android : les effets se testent en JVM sur de simples tableaux.
 */

/** Source de trames planaires (un tableau par canal). */
interface FrameReader {
    val channels: Int

    /** Nombre total de trames, pour la progression et les effets qui en ont besoin (fondus). */
    val frames: Long

    /** Remplit au plus [n] trames dans `dst[c][0 until n]` ; rend le nombre lu, 0 à la fin. */
    fun read(dst: Array<FloatArray>, n: Int): Int
}

interface FrameWriter {
    fun write(src: Array<FloatArray>, n: Int)
}

/** Progression et annulation d'un rendu. L'annulation lève une `CancellationException` depuis l'effet. */
class RunContext(
    val sampleRate: Int,
    private val progress: (Float) -> Unit = {},
    private val isCancelled: () -> Boolean = { false },
) {
    fun report(fraction: Float) = progress(fraction.coerceIn(0f, 1f))

    fun checkCancelled() {
        if (isCancelled()) throw java.util.concurrent.CancellationException("Effet annulé")
    }
}

abstract class Effect {

    /** Nombre de canaux produits pour [inputChannels] en entrée (stéréo → mono change la réponse). */
    open fun outputChannels(inputChannels: Int): Int = inputChannels

    /**
     * Vrai quand la durée de sortie diffère de celle de l'entrée (vitesse, tempo, étirement) : le
     * reste de la piste se décale alors. Faux pour les effets qui gardent la durée, queue d'écho ou
     * de réverbération comprise (la queue se superpose à la suite au lieu de la repousser).
     */
    open val changesLength: Boolean get() = false

    /**
     * Joue l'effet. [openInput] ouvre l'entrée depuis le début : les effets en plusieurs passes
     * (normaliser mesure d'abord, applique ensuite) l'appellent plusieurs fois.
     */
    abstract fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext)
}

/**
 * Un effet qui garde la durée de son entrée et travaille **sur place** sur des blocs.
 *
 * - [tailFrames] : trames de silence ajoutées après l'entrée pour laisser mourir la queue.
 * - [latencyFrames] : retard interne (anticipation d'un limiteur, fenêtre d'une FFT) ; le
 *   début du flux produit est écarté et autant de silence est lu à la fin, si bien que la sortie
 *   reste alignée sur l'entrée.
 */
abstract class BlockEffect : Effect() {

    protected open fun tailFrames(sampleRate: Int): Int = 0
    protected open fun latencyFrames(sampleRate: Int): Int = 0

    /** Passes de mesure avant le traitement (normaliser, retirer le continu…). */
    protected open fun analyze(openInput: () -> FrameReader, ctx: RunContext) {}

    /** Remet l'état à zéro avant le premier bloc. */
    protected abstract fun start(channels: Int, sampleRate: Int)

    /**
     * Traite [n] trames sur place. [buf] a au moins autant de tableaux que le plus grand de l'entrée
     * et de la sortie ; quand la sortie a plus de canaux, les canaux en plus démarrent copie du premier.
     */
    protected abstract fun process(buf: Array<FloatArray>, n: Int)

    override fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext) {
        analyze(openInput, ctx)
        val input = openInput()
        val inCh = input.channels
        val outCh = outputChannels(inCh)
        val rate = ctx.sampleRate
        start(inCh, rate)

        val buf = Array(maxOf(inCh, outCh)) { FloatArray(BLOCK) }
        val latency = latencyFrames(rate)
        var skip = latency
        var extra = (tailFrames(rate) + latency).toLong()
        val total = (input.frames + extra).coerceAtLeast(1)
        var done = 0L

        fun emit(n: Int) {
            for (c in inCh until buf.size) System.arraycopy(buf[0], 0, buf[c], 0, n)
            process(buf, n)
            var from = 0
            if (skip > 0) {
                from = minOf(skip, n)
                skip -= from
            }
            val m = n - from
            if (m > 0) {
                if (from > 0) for (c in 0 until outCh) System.arraycopy(buf[c], from, buf[c], 0, m)
                out.write(buf, m)
            }
            done += n
            ctx.report(done.toFloat() / total)
        }

        while (true) {
            ctx.checkCancelled()
            val n = input.read(buf, BLOCK)
            if (n <= 0) break
            emit(n)
        }
        while (extra > 0) {
            ctx.checkCancelled()
            val n = minOf(BLOCK.toLong(), extra).toInt()
            for (c in buf.indices) java.util.Arrays.fill(buf[c], 0, n, 0f)
            // Le silence entre dans l'effet comme une entrée : seules les copies de canaux sont à refaire.
            emit(n)
            extra -= n
        }
    }

    companion object {
        const val BLOCK = 4096
    }
}

// ---- Entrée / sortie en mémoire (aperçus, tests) -------------------------------------------

/** Lit des tableaux déjà en mémoire, par morceaux d'au plus [chunk] trames (pour simuler des lectures courtes). */
class MemoryReader(private val data: Array<FloatArray>, private val chunk: Int = Int.MAX_VALUE) : FrameReader {
    private var pos = 0
    override val channels get() = data.size
    override val frames get() = data[0].size.toLong()

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val m = minOf(n, chunk, data[0].size - pos)
        if (m <= 0) return 0
        for (c in data.indices) System.arraycopy(data[c], pos, dst[c], 0, m)
        pos += m
        return m
    }
}

/** Rassemble ce qu'un effet écrit. */
class MemoryWriter(private val channels: Int) : FrameWriter {
    private var data = Array(channels) { FloatArray(0) }
    var frames = 0
        private set

    override fun write(src: Array<FloatArray>, n: Int) {
        if (data[0].size < frames + n) data = Array(channels) { c -> data[c].copyOf(maxOf(frames + n, data[c].size * 2)) }
        for (c in 0 until channels) System.arraycopy(src[c], 0, data[c], frames, n)
        frames += n
    }

    fun toArrays(): Array<FloatArray> = Array(channels) { data[it].copyOf(frames) }
}

/** Passe un tableau dans un effet et rend le résultat (aperçus de quelques secondes, tests). */
fun Effect.processInMemory(input: Array<FloatArray>, sampleRate: Int, chunk: Int = Int.MAX_VALUE): Array<FloatArray> {
    val writer = MemoryWriter(outputChannels(input.size))
    run({ MemoryReader(input, chunk) }, writer, RunContext(sampleRate))
    return writer.toArrays()
}

// ---- Petites aides -------------------------------------------------------------------------

fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

fun linearToDb(lin: Float): Float = 20f * log10(lin.coerceAtLeast(1e-9f))
