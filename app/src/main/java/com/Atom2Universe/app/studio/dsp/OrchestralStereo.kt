package com.Atom2Universe.app.studio.dsp

import java.nio.ByteBuffer
import kotlin.math.pow

/** Milieu et différence mesurés sur les deux canaux secs de chaque zone stéréo du SF2. */
internal class OrchestralStereo(private val zones: ByteArray, private val layers: Array<Layer?>) {
    class Layer(val mid: Float, val pan: Float, val difference: OrchestralBank.Spectrum)

    fun at(key: Int, velocity: Int): Layer? {
        if (key !in 0..127 || velocity !in 1..127) return null
        val mask = zones[key * 16 + velocity / 8].toInt()
        if (mask and (1 shl (velocity % 8)) == 0) return null
        return layers[key * 2 + if (velocity <= 80) 0 else 1]
    }

    companion object {
        private val banks = arrayOfNulls<OrchestralStereo>(128)
        private val checked = BooleanArray(128)
        private val times = floatArrayOf(.018f, .06f, .15f, .34f, .68f, 1.18f, 1.8f)
        private val amplitudes = FloatArray(111) { if (it == 110) 0f else 10f.pow(-it / 20f) }

        @Synchronized fun load(program: Int): OrchestralStereo? {
            if (checked[program]) return banks[program]
            val stream = OrchestralStereo::class.java.getResourceAsStream("/studio/orchestral/$program-stereo.bin")
            banks[program] = stream?.let {
                ByteBuffer.wrap(it.use { stream -> stream.readBytes() }).let { input ->
                    val format = input.int
                    require(format == 0x53545331 || format == 0x53545332)
                    // STS2 : instants libres (ceux du modèle mono ORC7, départ à zéro) au lieu des sept instants fixes.
                    val instants = if (format == 0x53545332) FloatArray(input.u8()) { input.float } else times
                    val zones = ByteArray(2048).also { bytes -> input.get(bytes) }
                    val layers = Array<Layer?>(256) {
                        if (input.get().toInt() == 0) return@Array null
                        val mid = input.float
                        val pan = input.float
                        val velocity = input.u8()
                        val tones = input.u8()
                        val ratios = FloatArray(tones + 6)
                        val levels = Array(ratios.size) { i ->
                            ratios[i] = input.float
                            FloatArray(instants.size) { amplitudes[input.u8()] }
                        }
                        Layer(mid, pan, OrchestralBank.Spectrum(tones, 1f, ratios, levels, instants,
                            null, 127, velocity, -1, -1))
                    }
                    OrchestralStereo(zones, layers)
                }
            }
            checked[program] = true
            return banks[program]
        }
    }
}
