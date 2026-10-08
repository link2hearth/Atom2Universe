package com.Atom2Universe.app.audioeditor.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rééchantillonneur en flux, par sinc fenêtré (Kaiser, β = 8,6 : environ −90 dB hors bande).
 *
 * Tous les fichiers importés passent par lui pour arriver à la fréquence du projet. Une
 * interpolation linéaire aurait suffi à « faire marcher » la lecture mais laisse du repliement
 * audible sur les aigus ; ici le noyau est calculé une fois pour 1024 positions fractionnaires
 * puis interpolé, et chaque ligne est normalisée pour que le continu passe à l'identique.
 *
 * Utilisation : [process] autant de fois que nécessaire avec des blocs de n'importe quelle taille,
 * puis [finish] une fois pour récupérer la fin. Les blocs de sortie ont besoin de
 * [maxOutputFor] trames de place.
 */
class Resampler(val inRate: Int, val outRate: Int, val channels: Int, quality: Int = 16) {

    private val step = inRate.toDouble() / outRate
    private val cutoff = if (outRate < inRate) outRate.toDouble() / inRate * 0.95 else 1.0
    private val halfW = ceil(quality / cutoff).toInt()
    private val taps = halfW * 2

    // table[p * taps + j] = h(k − p/PHASES) avec k = j − halfW + 1.
    private val table = FloatArray((PHASES + 2) * taps)

    private var buf = Array(channels) { FloatArray(halfW) } // halfW zéros de « passé »
    private var size = halfW
    private var base = -halfW.toLong()  // indice d'entrée de buf[0]
    private var outPos = 0.0            // indice d'entrée (fractionnaire) du prochain échantillon de sortie
    private var totalIn = 0L

    init {
        require(inRate > 0 && outRate > 0 && channels > 0)
        val beta = 8.6
        val i0Beta = bessel0(beta)
        for (p in 0..PHASES + 1) {
            val frac = p.toDouble() / PHASES
            var sum = 0.0
            val row = DoubleArray(taps)
            for (j in 0 until taps) {
                val x = (j - halfW + 1) - frac
                val u = x / halfW
                val w = if (u <= -1.0 || u >= 1.0) 0.0 else bessel0(beta * sqrt(1.0 - u * u)) / i0Beta
                val arg = PI * cutoff * x
                val s = if (kotlin.math.abs(arg) < 1e-12) 1.0 else sin(arg) / arg
                row[j] = cutoff * s * w
                sum += row[j]
            }
            for (j in 0 until taps) table[p * taps + j] = (row[j] / sum).toFloat()
        }
    }

    /** Place à prévoir en sortie pour [n] trames d'entrée (et pour [finish], avec n = 0). */
    fun maxOutputFor(n: Int): Int = ceil((n + halfW) / step).toInt() + 2

    /** Consomme [n] trames de [input] ; écrit dans [output] et rend le nombre de trames produites. */
    fun process(input: Array<FloatArray>, n: Int, output: Array<FloatArray>): Int {
        append(input, n)
        totalIn += n
        // Tant qu'il reste assez d'entrée pour la moitié droite du noyau.
        return produce(output) { floor(it - base).toLong() + halfW < size }
    }

    /** Vide le reste : ajoute du silence en entrée jusqu'à la dernière trame de sortie qui tombe dans le signal. */
    fun finish(output: Array<FloatArray>): Int {
        val pad = Array(channels) { FloatArray(halfW) }
        append(pad, halfW)
        return produce(output) { it < totalIn }
    }

    private fun append(input: Array<FloatArray>, n: Int) {
        if (n <= 0) return
        if (buf[0].size < size + n) buf = Array(channels) { c -> buf[c].copyOf(maxOf(size + n, buf[c].size * 2)) }
        for (c in 0 until channels) System.arraycopy(input[c], 0, buf[c], size, n)
        size += n
    }

    private inline fun produce(output: Array<FloatArray>, canProduce: (Double) -> Boolean): Int {
        var m = 0
        while (canProduce(outPos)) {
            val t = outPos - base
            val i0 = floor(t).toInt()
            val fp = (t - i0) * PHASES
            val p0 = fp.toInt()
            val w = (fp - p0).toFloat()
            val r0 = p0 * taps
            val r1 = r0 + taps
            val first = i0 - halfW + 1
            for (c in 0 until channels) {
                val x = buf[c]
                var acc = 0f
                for (j in 0 until taps) {
                    val h = table[r0 + j] + w * (table[r1 + j] - table[r0 + j])
                    acc += x[first + j] * h
                }
                output[c][m] = acc
            }
            m++
            outPos += step
        }
        // On ne garde que ce dont les prochaines sorties auront besoin.
        val drop = (floor(outPos - base).toInt() - halfW + 1).coerceIn(0, size)
        if (drop > 0) {
            for (c in 0 until channels) System.arraycopy(buf[c], drop, buf[c], 0, size - drop)
            size -= drop
            base += drop
        }
        return m
    }

    companion object {
        private const val PHASES = 1024

        private fun bessel0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val q = x * x / 4.0
            var k = 1
            while (term > 1e-12 * sum) {
                term *= q / (k.toDouble() * k)
                sum += term
                k++
            }
            return sum
        }

        /** Rééchantillonne d'un coup des tableaux entiers (petits signaux, tests). */
        fun resampleAll(input: Array<FloatArray>, inRate: Int, outRate: Int): Array<FloatArray> {
            val ch = input.size
            if (inRate == outRate) return Array(ch) { input[it].copyOf() }
            val r = Resampler(inRate, outRate, ch)
            val n = input[0].size
            val cap = r.maxOutputFor(n) * 2
            val out = Array(ch) { FloatArray(cap) }
            val m1 = r.process(input, n, out)
            val tail = Array(ch) { FloatArray(r.maxOutputFor(0) + 4) }
            val m2 = r.finish(tail)
            return Array(ch) { c ->
                val a = FloatArray(m1 + m2)
                System.arraycopy(out[c], 0, a, 0, min(m1, a.size))
                System.arraycopy(tail[c], 0, a, m1, m2)
                a
            }
        }
    }
}
