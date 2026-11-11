package com.Atom2Universe.app.studio.dsp

import androidx.annotation.Keep
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Keep
internal object Ym2413Native {
    init { System.loadLibrary("ym2413_studio") }

    // La première instance d'emu2413 construit des tables partagées par tous les circuits.
    @Synchronized external fun create(rate: Int): Long
    external fun destroy(id: Long)
    external fun write(id: Long, register: Int, data: Int)
    external fun render(id: Long, output: FloatArray, offset: Int, count: Int): Int
}

/** Un YM2413 par piste FM, avec ses neuf canaux et son unique instrument utilisateur partagé. */
class Ym2413Synth(sampleRate: Int) : Instrument(sampleRate), AutoCloseable {
    private val voices = Array(9) { Voice(it) }
    private var handle = Ym2413Native.create(sampleRate).also { check(it != 0L) }
    private var serial = 0L
    private val glideFrames = (sampleRate * 0.035).roundToInt().coerceAtLeast(1)
    private var untilGlideStep = 0
    private var cachedPatch = -1
    @Volatile private var selection = 3 // Piano

    init {
        // Timbre personnel : deux opérateurs à 2:1, ondes sinusoïdales et un peu de rétroaction.
        val initial = intArrayOf(0x22, 0x21, 0x16, 0x02, 0xd4, 0xd4, 0x25, 0x26)
        for (reg in initial.indices) write(reg, initial[reg])
    }

    /** Publication atomique depuis le pad. Le fil audio applique seulement les changements de registre. */
    fun select(program: Int, brightness: Float, attack: Float) {
        val attenuation = ((1f - brightness.coerceIn(0f, 1f)) * 54f).roundToInt()
        val attackRate = 15 - (attack.coerceIn(0f, 1f) * 11f).roundToInt()
        selection = (attackRate shl 10) or (attenuation shl 4) or program.coerceIn(0, 15)
    }

    private fun applyCustomPatch(selected: Int) {
        val patch = selected ushr 4
        if (cachedPatch == patch) return
        val attenuation = patch and 63
        val attackRate = patch ushr 6
        if (cachedPatch < 0 || (cachedPatch and 63) != attenuation) write(2, attenuation)
        if (cachedPatch < 0 || (cachedPatch ushr 6) != attackRate) {
            write(4, (attackRate shl 4) or 4)
            write(5, (attackRate shl 4) or 4)
        }
        cachedPatch = patch
    }

    private fun write(reg: Int, value: Int) = Ym2413Native.write(handle, reg, value)

    override val activeVoices: Int @Synchronized get() = voices.count { it.note >= 0 }
    @Synchronized fun holds(note: Int): Boolean = voices.any { it.held && it.note == note }

    @Synchronized override fun noteOn(note: Int, velocity: Float) {
        if (handle == 0L) return
        if (velocity <= 0f) { noteOff(note); return }
        val pitch = note.coerceIn(0, 127)
        val voice = voices.firstOrNull { it.held && it.note == pitch }
            ?: voices.firstOrNull { it.note < 0 }
            ?: oldestVoice()
        if (voice.held) voice.release()
        val selected = selection
        if ((selected and 15) == 0) applyCustomPatch(selected)
        val volume = (-20.0 * log10(velocity.coerceAtMost(1f).toDouble()) / 3.0).roundToInt().coerceIn(0, 15)
        write(0x30 + voice.channel, ((selected and 15) shl 4) or volume)
        voice.note = pitch
        voice.age = ++serial
        voice.held = true
        voice.glideLeft = 0
        voice.pitch = pitch.toDouble()
        voice.tune()
    }

    private fun oldestVoice(): Voice {
        var oldest = voices[0]
        for (v in voices) {
            if ((!v.held && oldest.held) || (v.held == oldest.held && v.age < oldest.age)) oldest = v
        }
        return oldest
    }

    @Synchronized override fun noteOff(note: Int) {
        for (v in voices) if (v.held && v.note == note) v.release()
    }

    @Synchronized override fun allOff() {
        for (v in voices) if (v.held) v.release()
    }

    @Synchronized override fun glide(from: Int, to: Int, velocity: Float) {
        val v = voices.firstOrNull { it.held && it.note == from }
        if (v == null) { noteOn(to, velocity); return }
        v.note = to.coerceIn(0, 127)
        v.glideLeft = glideFrames
        v.glideStep = (v.note - v.pitch) / glideFrames
    }

    @Synchronized public override fun renderMono(out: FloatArray, count: Int): Boolean {
        if (handle == 0L || voices.none { it.note >= 0 }) return false
        require(count in 0..out.size)
        val selected = selection
        if ((selected and 15) == 0) applyCustomPatch(selected)
        var offset = 0
        while (offset < count) {
            val gliding = voices.any { it.glideLeft > 0 }
            if (gliding && untilGlideStep == 0) {
                for (v in voices) if (v.glideLeft > 0) v.tune()
                untilGlideStep = GLIDE_PERIOD
            }
            val size = if (gliding) minOf(count - offset, untilGlideStep) else count - offset
            val audible = Ym2413Native.render(handle, out, offset, size)
            for (v in voices) {
                if (!v.held && (audible and (1 shl v.channel)) == 0) v.note = -1
                if (v.glideLeft > 0) {
                    val advanced = minOf(size, v.glideLeft)
                    v.pitch += v.glideStep * advanced
                    v.glideLeft -= advanced
                    if (v.glideLeft == 0) { v.pitch = v.note.toDouble(); v.tune() }
                }
            }
            untilGlideStep = if (gliding) untilGlideStep - size else 0
            offset += size
        }
        return true
    }

    @Synchronized override fun close() {
        if (handle == 0L) return
        Ym2413Native.destroy(handle)
        handle = 0
        for (v in voices) { v.note = -1; v.held = false; v.glideLeft = 0 }
    }

    private inner class Voice(val channel: Int) {
        var note = -1
        var held = false
        var age = 0L
        var pitch = 0.0
        var glideLeft = 0
        var glideStep = 0.0
        private var frequency = 0

        fun tune() {
            // Fréquence = F-number × 2^block × (horloge / 72) / 2^19.
            var number = 440.0 * 2.0.pow((pitch - 69.0) / 12.0) * (524288.0 * 72 / 3579545)
            var block = 0
            while (number >= 511.5 && block < 7) { number *= 0.5; block++ }
            val fnum = number.roundToInt().coerceIn(0, 511)
            write(0x10 + channel, fnum and 255)
            frequency = (block shl 1) or (fnum ushr 8)
            write(0x20 + channel, frequency or 0x10)
        }

        fun release() {
            write(0x20 + channel, frequency)
            held = false
            glideLeft = 0
        }
    }

    private companion object { const val GLIDE_PERIOD = 16 }
}
