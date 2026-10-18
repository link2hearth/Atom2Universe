package com.Atom2Universe.app.audioeditor.dsp

import com.Atom2Universe.app.audioeditor.core.FadeShape
import kotlin.math.abs
import kotlin.math.sqrt

/** Applique un gain fixe, en dB. */
class Amplify(private val gainDb: Float) : BlockEffect() {
    private val g = dbToLinear(gainDb)
    override fun start(channels: Int, sampleRate: Int) {}
    override fun process(buf: Array<FloatArray>, n: Int) {
        for (c in buf.indices) { val x = buf[c]; for (i in 0 until n) x[i] *= g }
    }
}

/** Inverse la polarité (le signal passe en miroir autour de zéro). */
class Invert : BlockEffect() {
    override fun start(channels: Int, sampleRate: Int) {}
    override fun process(buf: Array<FloatArray>, n: Int) {
        for (c in buf.indices) { val x = buf[c]; for (i in 0 until n) x[i] = -x[i] }
    }
}

/**
 * Normalise : amène la crête (ou le niveau RMS) à [targetDb]. Deux passes, une pour mesurer, une pour appliquer.
 *
 * - [removeDc] retire d'abord le décalage continu de chaque canal.
 * - [independent] normalise chaque canal pour lui-même ; sinon un même gain pour tous garde l'équilibre stéréo.
 * - Mode [Mode.RMS] : vise un niveau moyen ; avec [limitPeak] le gain est borné pour que la crête ne dépasse pas 0 dB.
 */
class Normalize(
    private val targetDb: Float = -1f,
    private val removeDc: Boolean = true,
    private val independent: Boolean = false,
    private val mode: Mode = Mode.PEAK,
    private val limitPeak: Boolean = true,
) : BlockEffect() {

    enum class Mode { PEAK, RMS }

    private var offsets = FloatArray(0)
    private var gains = FloatArray(0)

    override fun analyze(openInput: () -> FrameReader, ctx: RunContext) {
        val r1 = openInput()
        val ch = r1.channels
        val buf = Array(ch) { FloatArray(BLOCK) }
        offsets = FloatArray(ch)
        if (removeDc) {
            val sum = DoubleArray(ch)
            var count = 0L
            while (true) {
                ctx.checkCancelled()
                val n = r1.read(buf, BLOCK)
                if (n <= 0) break
                for (c in 0 until ch) { var s = 0.0; val x = buf[c]; for (i in 0 until n) s += x[i]; sum[c] += s }
                count += n
            }
            if (count > 0) for (c in 0 until ch) offsets[c] = (sum[c] / count).toFloat()
        }
        val r2 = if (removeDc) openInput() else r1
        val peak = FloatArray(ch)
        val sq = DoubleArray(ch)
        var count = 0L
        while (true) {
            ctx.checkCancelled()
            val n = r2.read(buf, BLOCK)
            if (n <= 0) break
            for (c in 0 until ch) {
                val x = buf[c]; val o = offsets[c]
                var p = peak[c]; var s = 0.0
                for (i in 0 until n) { val v = x[i] - o; val a = abs(v); if (a > p) p = a; s += v.toDouble() * v }
                peak[c] = p; sq[c] += s
            }
            count += n
        }
        val target = dbToLinear(targetDb)
        // Niveau mesuré par canal selon le mode ; la crête sert aussi de garde-fou en RMS.
        val level = FloatArray(ch) { c -> if (mode == Mode.PEAK) peak[c] else if (count > 0) sqrt(sq[c] / count).toFloat() else 0f }
        gains = FloatArray(ch)
        fun gainFor(lv: Float, pk: Float): Float {
            if (lv <= 1e-9f) return 1f
            var g = target / lv
            if (mode == Mode.RMS && limitPeak && pk > 1e-9f) g = minOf(g, 1f / pk)
            return g
        }
        if (independent) {
            for (c in 0 until ch) gains[c] = gainFor(level[c], peak[c])
        } else {
            // Un seul gain : celui qui convient au canal le plus fort.
            val lv = level.max()
            val pk = peak.max()
            val g = gainFor(lv, pk)
            for (c in 0 until ch) gains[c] = g
        }
    }

    override fun start(channels: Int, sampleRate: Int) {}

    override fun process(buf: Array<FloatArray>, n: Int) {
        for (c in buf.indices) {
            val x = buf[c]
            val o = if (c < offsets.size) offsets[c] else 0f
            val g = if (c < gains.size) gains[c] else 1f
            for (i in 0 until n) x[i] = (x[i] - o) * g
        }
    }
}

/** Retire le décalage continu (la moyenne) de chaque canal. */
class RemoveDc : BlockEffect() {
    private var offsets = FloatArray(0)

    override fun analyze(openInput: () -> FrameReader, ctx: RunContext) {
        val r = openInput()
        val ch = r.channels
        val buf = Array(ch) { FloatArray(BLOCK) }
        val sum = DoubleArray(ch)
        var count = 0L
        while (true) {
            ctx.checkCancelled()
            val n = r.read(buf, BLOCK)
            if (n <= 0) break
            for (c in 0 until ch) { var s = 0.0; val x = buf[c]; for (i in 0 until n) s += x[i]; sum[c] += s }
            count += n
        }
        offsets = FloatArray(ch) { if (count > 0) (sum[it] / count).toFloat() else 0f }
    }

    override fun start(channels: Int, sampleRate: Int) {}

    override fun process(buf: Array<FloatArray>, n: Int) {
        for (c in buf.indices) { val x = buf[c]; val o = offsets.getOrElse(c) { 0f }; for (i in 0 until n) x[i] -= o }
    }
}

/**
 * Fondu sur toute la plage : d'entrée (0 → plein niveau) ou de sortie (plein niveau → 0), selon [shape].
 * Pour un fondu *sur un clip* qui ne réécrit rien, voir `setClipFades`.
 */
class FadeEffect(private val fadeIn: Boolean, private val shape: FadeShape = FadeShape.LINEAR) : BlockEffect() {
    private var total = 1L
    private var pos = 0L

    override fun analyze(openInput: () -> FrameReader, ctx: RunContext) {
        total = maxOf(1L, openInput().frames)
    }

    override fun start(channels: Int, sampleRate: Int) { pos = 0 }

    override fun process(buf: Array<FloatArray>, n: Int) {
        for (i in 0 until n) {
            val x = (pos + i).toFloat() / total
            val g = shape.curve(if (fadeIn) x else 1f - x)
            for (c in buf.indices) buf[c][i] *= g
        }
        pos += n
    }
}

/** Mélange les deux canaux en un seul (moyenne). Sur une entrée mono, ne fait rien. */
class StereoToMono : BlockEffect() {
    override fun outputChannels(inputChannels: Int) = 1
    override fun start(channels: Int, sampleRate: Int) {}
    override fun process(buf: Array<FloatArray>, n: Int) {
        if (buf.size < 2) return
        val l = buf[0]; val r = buf[1]
        for (i in 0 until n) l[i] = (l[i] + r[i]) * 0.5f
    }
}

/** Échange les canaux gauche et droit. */
class SwapChannels : BlockEffect() {
    override fun start(channels: Int, sampleRate: Int) {}
    override fun process(buf: Array<FloatArray>, n: Int) {
        if (buf.size < 2) return
        val l = buf[0]; val r = buf[1]
        for (i in 0 until n) { val t = l[i]; l[i] = r[i]; r[i] = t }
    }
}
