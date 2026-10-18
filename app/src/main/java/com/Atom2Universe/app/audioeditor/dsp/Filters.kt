package com.Atom2Universe.app.audioeditor.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un filtre du second ordre (recettes « Audio EQ Cookbook » de Robert Bristow-Johnson), forme
 * transposée directe II, en double précision, un état par canal. Les coefficients se calculent une
 * fois ; [process] ne fait aucune allocation.
 */
class Biquad(
    val type: Type,
    sampleRate: Int,
    freq: Double,
    q: Double = 0.7071,
    gainDb: Double = 0.0,
    channels: Int = 2,
) {
    enum class Type { LOW_PASS, HIGH_PASS, BAND_PASS, NOTCH, PEAKING, LOW_SHELF, HIGH_SHELF }

    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    init {
        val f = freq.coerceIn(10.0, sampleRate * 0.49)
        val w0 = 2.0 * PI * f / sampleRate
        val cw = cos(w0)
        val sw = sin(w0)
        val qq = q.coerceAtLeast(0.05)
        val alpha = sw / (2.0 * qq)
        val a = 10.0.pow(gainDb / 40.0)
        var nb0: Double; var nb1: Double; var nb2: Double
        var na0: Double; var na1: Double; var na2: Double
        when (type) {
            Type.LOW_PASS -> {
                nb0 = (1 - cw) / 2; nb1 = 1 - cw; nb2 = (1 - cw) / 2
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
            Type.HIGH_PASS -> {
                nb0 = (1 + cw) / 2; nb1 = -(1 + cw); nb2 = (1 + cw) / 2
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
            Type.BAND_PASS -> {
                nb0 = alpha; nb1 = 0.0; nb2 = -alpha
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
            Type.NOTCH -> {
                nb0 = 1.0; nb1 = -2 * cw; nb2 = 1.0
                na0 = 1 + alpha; na1 = -2 * cw; na2 = 1 - alpha
            }
            Type.PEAKING -> {
                nb0 = 1 + alpha * a; nb1 = -2 * cw; nb2 = 1 - alpha * a
                na0 = 1 + alpha / a; na1 = -2 * cw; na2 = 1 - alpha / a
            }
            Type.LOW_SHELF, Type.HIGH_SHELF -> {
                // Pente de l'étagère S = 1 : alpha = sin(w0)/√2.
                val sa = 2.0 * sqrt(a) * (sw / sqrt(2.0))
                if (type == Type.LOW_SHELF) {
                    nb0 = a * ((a + 1) - (a - 1) * cw + sa)
                    nb1 = 2 * a * ((a - 1) - (a + 1) * cw)
                    nb2 = a * ((a + 1) - (a - 1) * cw - sa)
                    na0 = (a + 1) + (a - 1) * cw + sa
                    na1 = -2 * ((a - 1) + (a + 1) * cw)
                    na2 = (a + 1) + (a - 1) * cw - sa
                } else {
                    nb0 = a * ((a + 1) + (a - 1) * cw + sa)
                    nb1 = -2 * a * ((a - 1) + (a + 1) * cw)
                    nb2 = a * ((a + 1) + (a - 1) * cw - sa)
                    na0 = (a + 1) - (a - 1) * cw + sa
                    na1 = 2 * ((a - 1) - (a + 1) * cw)
                    na2 = (a + 1) - (a - 1) * cw - sa
                }
            }
        }
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0
        a1 = na1 / na0; a2 = na2 / na0
    }

    fun reset() { z1.fill(0.0); z2.fill(0.0) }

    /** Filtre [n] trames du canal [ch] sur place. */
    fun process(ch: Int, x: FloatArray, n: Int) {
        var s1 = z1[ch]
        var s2 = z2[ch]
        for (i in 0 until n) {
            val v = x[i].toDouble()
            val y = b0 * v + s1
            s1 = b1 * v - a1 * y + s2
            s2 = b2 * v - a2 * y
            x[i] = y.toFloat()
        }
        z1[ch] = s1
        z2[ch] = s2
    }
}

/** Plusieurs filtres en série sur les mêmes canaux (pentes plus raides, égaliseurs). */
internal class BiquadChain(private val stages: List<Biquad>) {
    fun process(buf: Array<FloatArray>, n: Int) {
        for (s in stages) for (c in buf.indices) s.process(c, buf[c], n)
    }
}

/**
 * Passe-bas, passe-haut, passe-bande ou coupe-bande.
 *
 * Les pentes de 12 à 48 dB/octave (passe-bas / passe-haut) sont des filtres de Butterworth : des
 * sections du second ordre en série, chacune avec le facteur de qualité qui rend la réponse plate
 * jusqu'à la coupure. [q] ne sert qu'au passe-bande et au coupe-bande.
 */
class FilterEffect(
    private val kind: Kind,
    private val freq: Float,
    private val q: Float = 0.7071f,
    private val slopeDb: Int = 12,
) : BlockEffect() {

    enum class Kind { LOW_PASS, HIGH_PASS, BAND_PASS, NOTCH }

    private var chain: BiquadChain? = null

    override fun start(channels: Int, sampleRate: Int) {
        val stages = ArrayList<Biquad>()
        when (kind) {
            Kind.LOW_PASS, Kind.HIGH_PASS -> {
                val type = if (kind == Kind.LOW_PASS) Biquad.Type.LOW_PASS else Biquad.Type.HIGH_PASS
                for (qq in butterworthQs(slopeDb / 6)) stages.add(Biquad(type, sampleRate, freq.toDouble(), qq, 0.0, channels))
            }
            Kind.BAND_PASS -> stages.add(Biquad(Biquad.Type.BAND_PASS, sampleRate, freq.toDouble(), q.toDouble(), 0.0, channels))
            Kind.NOTCH -> stages.add(Biquad(Biquad.Type.NOTCH, sampleRate, freq.toDouble(), q.toDouble(), 0.0, channels))
        }
        chain = BiquadChain(stages)
    }

    override fun process(buf: Array<FloatArray>, n: Int) { chain!!.process(buf, n) }

    companion object {
        /** Facteurs de qualité des sections d'un Butterworth d'ordre [order] (pair, 2 à 8). */
        fun butterworthQs(order: Int): List<Double> {
            val o = (order.coerceIn(2, 8) / 2) * 2
            val sections = o / 2
            return (1..sections).map { k -> 1.0 / (2.0 * cos((2.0 * k - 1.0) * PI / (2.0 * o))) }
        }
    }
}

/** Les dix bandes d'un égaliseur graphique, en Hz. */
val GRAPHIC_EQ_BANDS = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

/** Égaliseur graphique à dix bandes d'une octave (31 Hz … 16 kHz) : un filtre en cloche par bande réglée. */
class GraphicEq(private val gainsDb: FloatArray) : BlockEffect() {

    init { require(gainsDb.size == GRAPHIC_EQ_BANDS.size) { "Dix gains attendus" } }

    private var chain: BiquadChain? = null

    override fun start(channels: Int, sampleRate: Int) {
        val stages = ArrayList<Biquad>()
        for (i in GRAPHIC_EQ_BANDS.indices) {
            if (kotlin.math.abs(gainsDb[i]) < 0.01f) continue
            if (GRAPHIC_EQ_BANDS[i] >= sampleRate * 0.45f) continue
            // Q = √2 : la largeur d'une octave.
            stages.add(Biquad(Biquad.Type.PEAKING, sampleRate, GRAPHIC_EQ_BANDS[i].toDouble(), 1.4142, gainsDb[i].toDouble(), channels))
        }
        chain = BiquadChain(stages)
    }

    override fun process(buf: Array<FloatArray>, n: Int) { chain!!.process(buf, n) }

    companion object {
        /** Quelques réglages de départ, dans l'ordre des bandes. */
        val PRESETS: Map<String, FloatArray> = linkedMapOf(
            "flat" to FloatArray(10),
            "bass_boost" to floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "treble_boost" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 6f),
            "voice" to floatArrayOf(-6f, -4f, -2f, 0f, 2f, 4f, 4f, 2f, 0f, -2f),
            "loudness" to floatArrayOf(6f, 4f, 1f, 0f, -1f, -1f, 0f, 2f, 4f, 5f),
            "telephone" to floatArrayOf(-30f, -24f, -12f, -4f, 3f, 5f, 4f, -4f, -20f, -30f),
        )
    }
}

/** Graves et aigus : deux étagères (autour de 250 Hz et de 4 kHz), comme le réglage « Bass and Treble ». */
class BassTreble(private val bassDb: Float, private val trebleDb: Float) : BlockEffect() {
    private var chain: BiquadChain? = null

    override fun start(channels: Int, sampleRate: Int) {
        val stages = ArrayList<Biquad>()
        if (kotlin.math.abs(bassDb) >= 0.01f) stages.add(Biquad(Biquad.Type.LOW_SHELF, sampleRate, 250.0, 0.7071, bassDb.toDouble(), channels))
        if (kotlin.math.abs(trebleDb) >= 0.01f) stages.add(Biquad(Biquad.Type.HIGH_SHELF, sampleRate, 4000.0, 0.7071, trebleDb.toDouble(), channels))
        chain = BiquadChain(stages)
    }

    override fun process(buf: Array<FloatArray>, n: Int) { chain!!.process(buf, n) }
}
