package com.Atom2Universe.app.audioeditor.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * L'empreinte d'un bruit de fond : sa puissance moyenne dans chaque case de fréquence, mesurée sur un
 * échantillon qui ne contient que du bruit (un souffle, un ronflement, un silence de pièce).
 */
class NoiseProfile(internal val power: DoubleArray) {

    companion object {
        const val FFT_SIZE = 2048
        const val HOP = 512

        /** Fenêtre de Hann « en racine » : analyse × synthèse redonne un Hann, qui somme à 2 avec 75 % de recouvrement. */
        internal val SQRT_HANN = DoubleArray(FFT_SIZE) { sqrt(0.5 - 0.5 * cos(2.0 * PI * it / FFT_SIZE)) }

        /** Mesure le bruit d'un échantillon mono ; `null` s'il est plus court qu'une fenêtre (46 ms). */
        fun fromSamples(mono: FloatArray): NoiseProfile? {
            if (mono.size < FFT_SIZE) return null
            val fft = Fft(FFT_SIZE)
            val re = DoubleArray(FFT_SIZE)
            val im = DoubleArray(FFT_SIZE)
            val acc = DoubleArray(FFT_SIZE / 2 + 1)
            var frames = 0
            var start = 0
            while (start + FFT_SIZE <= mono.size) {
                for (i in 0 until FFT_SIZE) { re[i] = mono[start + i] * SQRT_HANN[i]; im[i] = 0.0 }
                fft.transform(re, im)
                for (b in 0..FFT_SIZE / 2) acc[b] += re[b] * re[b] + im[b] * im[b]
                frames++
                start += HOP
            }
            for (b in acc.indices) acc[b] /= frames
            return NoiseProfile(acc)
        }

        /** Idem depuis un lecteur (la plage de piste sélectionnée), mélangé en mono, au plus [maxFrames] trames. */
        fun fromReader(reader: FrameReader, maxFrames: Int = 44100 * 30): NoiseProfile? {
            val cap = minOf(reader.frames, maxFrames.toLong()).toInt()
            if (cap < FFT_SIZE) return null
            val mono = FloatArray(cap)
            val buf = Array(reader.channels) { FloatArray(BlockEffect.BLOCK) }
            var pos = 0
            while (pos < cap) {
                val n = reader.read(buf, minOf(BlockEffect.BLOCK, cap - pos))
                if (n <= 0) break
                for (i in 0 until n) {
                    var s = 0f
                    for (c in buf.indices) s += buf[c][i]
                    mono[pos + i] = s / buf.size
                }
                pos += n
            }
            return fromSamples(mono.copyOf(pos))
        }
    }
}

/**
 * Réduction de bruit par soustraction spectrale.
 *
 * Le signal est découpé en fenêtres de 2 048 échantillons (recouvrement de 75 %) ; dans chaque case
 * de fréquence, la puissance du bruit de [profile], multipliée par `10^(sensitivity/10)`, est
 * retranchée. Ce qui reste sous ce seuil est atténué d'au plus [reductionDb]. Pour éviter le
 * « bruit musical » (des tintements isolés), le gain remonte tout de suite mais ne redescend
 * que progressivement, et il est lissé sur [smoothingBands] cases de part et d'autre (sans jamais abaisser le gain d'une case franchement au-dessus du bruit).
 */
class NoiseReduction(
    private val profile: NoiseProfile,
    private val reductionDb: Float = 12f,
    private val sensitivity: Float = 6f,
    private val smoothingBands: Int = 3,
) : BlockEffect() {

    private class ChannelState {
        val ring = FloatArray(N)
        val acc = FloatArray(N)
        val gain = DoubleArray(N / 2 + 1) { 1.0 }
        var written = 0L
        var w = 0
    }

    private var states: Array<ChannelState> = emptyArray()
    private val fft = Fft(N)
    private val re = DoubleArray(N)
    private val im = DoubleArray(N)
    private val raw = DoubleArray(N / 2 + 1)
    private val alpha = Math.pow(10.0, sensitivity / 10.0)
    private val floor = Math.pow(10.0, -reductionDb / 20.0)

    // Chaque échantillon de sortie dépend des N derniers échantillons d'entrée.
    override fun latencyFrames(sampleRate: Int): Int = N

    override fun start(channels: Int, sampleRate: Int) {
        states = Array(channels) { ChannelState() }
    }

    override fun process(buf: Array<FloatArray>, n: Int) {
        for (c in buf.indices) {
            val st = states[c]
            val x = buf[c]
            for (i in 0 until n) {
                st.ring[st.w] = x[i]
                st.w = (st.w + 1) % N
                st.written++
                if (st.written % HOP == 0L) frame(st)
                val q = st.written - 1 - N // position de sortie devenue définitive
                if (q >= 0) {
                    val idx = (q % N).toInt()
                    x[i] = st.acc[idx] * 0.5f
                    st.acc[idx] = 0f
                } else x[i] = 0f
            }
        }
    }

    private fun frame(st: ChannelState) {
        val win = NoiseProfile.SQRT_HANN
        for (j in 0 until N) {
            re[j] = st.ring[(st.w + j) % N] * win[j]
            im[j] = 0.0
        }
        fft.transform(re, im)

        for (b in 0..N / 2) {
            val p = re[b] * re[b] + im[b] * im[b]
            val noise = profile.power[b] * alpha
            raw[b] = if (p <= 1e-18) floor else max(floor, sqrt(max(0.0, 1.0 - noise / p)))
        }
        // Lissage en fréquence, puis en temps (montée immédiate, descente lente).
        val s = smoothingBands
        for (b in 0..N / 2) {
            var sum = 0.0
            var cnt = 0
            for (k in max(0, b - s)..minOf(N / 2, b + s)) { sum += raw[k]; cnt++ }
            // Le lissage ne doit jamais creuser un pic utile : on garde au moins le gain propre de la case.
            val g = max(raw[b], sum / cnt)
            val prev = st.gain[b]
            st.gain[b] = if (g >= prev) g else max(g, prev * RELEASE)
        }
        for (b in 0..N / 2) {
            val g = st.gain[b]
            re[b] *= g; im[b] *= g
            if (b in 1 until N / 2) { re[N - b] *= g; im[N - b] *= g }
        }
        fft.transform(re, im, inverse = true)
        val start = st.written - N
        for (j in 0 until N) {
            val p = start + j
            if (p >= 0) st.acc[(p % N).toInt()] += (re[j] * win[j]).toFloat()
        }
    }

    companion object {
        private const val N = NoiseProfile.FFT_SIZE
        private const val HOP = NoiseProfile.HOP
        private const val RELEASE = 0.85
    }
}
