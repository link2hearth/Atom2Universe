package com.Atom2Universe.app.studio.dsp

import kotlin.math.roundToInt

/** Un cycle SCC. GXSCC moyenne les paliers traversés pendant une trame de sortie. */
internal class GxWave(gain: Int, val octave: Int, points: ByteArray) {
    // Le même facteur global pour toutes les ondes préserve les niveaux relatifs dans le mix de Studio.
    private val levels = DoubleArray(32) { points[it] * gain / 65536.0 }
    private val integral = DoubleArray(33).also {
        for (i in levels.indices) it[i + 1] = it[i] + levels[i]
    }

    fun sample(phase: Double, width: Double): Float {
        val first = phase.toInt()
        val end = phase + width
        val last = end.toInt()
        if (last == first) return levels[first].toFloat()
        // La primitive périodique évite de parcourir chaque palier, même dans l'aigu.
        val lastCell = last and 31
        val endArea = (last shr 5) * integral[32] + integral[lastCell] + (end - last) * levels[lastCell]
        val startArea = integral[first] + (phase - first) * levels[first]
        return ((endArea - startArea) / width).toFloat()
    }
}

/** Durées en trames de sortie (également à 48 kHz). Un maintien sans extinction a une durée nulle. */
internal class GxEnvelope(
    val attack: Int, val decay: Int, val sustain: Int, val fade: Int, val release: Int,
)

/** Les deux banques mélodiques de GXSCC. Chaque voix conserve le timbre choisi au départ de sa note. */
class GxSynth(sampleRate: Int, voiceCount: Int = 8, midiChannel: Int = 0) : Instrument(sampleRate) {
    private val voices = Array(voiceCount.coerceAtLeast(1)) { Voice(it + 1) }
    @Volatile var midiChannel: Int = midiChannel
    private val channelTuning = DoubleArray(16) { channel ->
        // Profil « Max 14cent » : la même piste garde son propre décalage pour toutes ses notes.
        val cents = 3 * (channel % 4) + channel / 4 - 14
        Math.pow(2.0, cents / 1200.0)
    }
    private val glideFrames = (sampleRate * 0.035).roundToInt().coerceAtLeast(1)
    private val halfRate = sampleRate == 22050
    private val drumPitchRatio = Math.pow(2.0, (if (halfRate) -1.2 else -0.6) / 1200.0)
    private val phaseSteps = DoubleArray(205) { note ->
        // GXSCC emploie do4 = 261,63 Hz, avec une correction d'octave propre à la table.
        32.0 * 261.63 * Math.pow(2.0, (note - 60) / 12.0) / sampleRate
    }
    private var age = 0L
    @Volatile private var selection = 0

    fun select(famicom: Boolean, program: Int) {
        selection = program.coerceIn(0, 127) + if (famicom) 128 else 0
    }

    override val activeVoices: Int get() = voices.count { it.active }

    fun holds(note: Int): Boolean = voices.any { it.active && it.held && it.note == note }

    override fun noteOn(note: Int, velocity: Float) {
        if (velocity <= 0f) { noteOff(note); return }
        val pitch = note.coerceIn(0, 127)
        val voice = voices.firstOrNull { it.held && it.note == pitch }
            ?: voices.firstOrNull { !it.active }
            ?: voiceToSteal()
        voice.start(pitch, velocity.coerceAtMost(1f))
    }

    private fun voiceToSteal(): Voice {
        var quietest: Voice? = null
        var oldest = voices.first()
        for (v in voices) {
            if (!v.held && (quietest == null || v.level < quietest.level)) quietest = v
            if (v.age < oldest.age) oldest = v
        }
        return quietest ?: oldest
    }

    override fun noteOff(note: Int) {
        for (v in voices) if (v.held && v.note == note) v.release()
    }

    override fun allOff() {
        for (v in voices) if (v.held) v.release()
    }

    fun clear() {
        for (v in voices) v.stop()
    }

    override fun glide(from: Int, to: Int, velocity: Float) {
        val voice = voices.firstOrNull { it.held && it.note == from }
        if (voice == null) noteOn(to, velocity) else voice.glide(to.coerceIn(0, 127))
    }

    public override fun renderMono(out: FloatArray, count: Int): Boolean {
        if (voices.none { it.active }) return false
        out.fill(0f, 0, count)
        for (voice in voices) if (voice.active) voice.render(out, count)
        return true
    }

    private inner class Voice(seed: Int) {
        var active = false
        var held = false
        var note = 0
        var age = 0L
        var level = 0
        private var shape = GxBanks.wave(false, 0)
        private var env = GxBanks.envelope(0)
        private var phase = 0.0
        private var width = 0.0
        private var targetWidth = 0.0
        private var widthDelta = 0.0
        private var glideLeft = 0
        private var velocity = 0f
        private var stage = Stage.HOLD
        private var increment = 0
        private var sustain = 0
        private var tuning = 1.0
        private val noise = Noise(0x1105 * seed)
        private var noiseLevel = 0f
        private var noiseProgram = false
        private var drumFrames = 0

        // En 236E le reste d'une division signée par 65535 est décalé de -32768.
        // Cette composante continue est aussi présente dans les WAV de référence.
        private fun nextNoise(): Float = noise.next() - 0.5f

        private fun phaseWidth(pitch: Int): Double {
            // Seashore et Helicopter cadencent le bruit à 1,6 fois le numéro de note MIDI.
            val key = if (noiseProgram) (pitch * 1.6).toInt() else pitch
            return Math.scalb(phaseSteps[key], shape.octave) * tuning
        }

        fun start(pitch: Int, strength: Float) {
            val selected = selection
            val program = selected and 127
            shape = GxBanks.wave(selected >= 128, program)
            env = GxBanks.envelope(program)
            noiseProgram = program == 122 || program == 126
            if (noiseProgram) noiseLevel = nextNoise()
            drumFrames = if (program == 118) (if (halfRate) 800 else 1600) else 0
            tuning = channelTuning[midiChannel.coerceIn(0, 15)]
            note = pitch
            age = this@GxSynth.age++
            held = true
            active = true
            velocity = strength
            width = phaseWidth(note)
            phase = 0.0
            glideLeft = 0
            sustain = env.sustain shl 14
            level = 0
            if (env.attack > 0) {
                stage = Stage.ATTACK
                increment = FULL / env.attack
            } else startDecay()
        }

        private fun startDecay() {
            level = FULL
            if (env.decay > 0 && sustain < FULL) {
                stage = Stage.DECAY
                increment = (FULL - sustain) / env.decay
            } else startHold()
        }

        private fun startHold() {
            level = sustain
            stage = Stage.HOLD
            increment = if (env.fade > 0) sustain / env.fade else 0
        }

        fun glide(pitch: Int) {
            note = pitch
            targetWidth = phaseWidth(pitch)
            widthDelta = (targetWidth - width) / glideFrames
            glideLeft = glideFrames
        }

        fun release() {
            held = false
            if (level <= 0 || env.release == 0) { stop(); return }
            stage = Stage.RELEASE
            increment = (level / env.release).coerceAtLeast(1)
        }

        fun stop() {
            held = false
            active = false
            level = 0
        }

        fun render(out: FloatArray, count: Int) {
            for (i in 0 until count) {
                when (stage) {
                    Stage.ATTACK -> {
                        level += increment
                        if (level >= FULL) startDecay()
                    }
                    Stage.DECAY -> {
                        level -= increment
                        if (level <= sustain) startHold()
                    }
                    Stage.HOLD, Stage.RELEASE -> {
                        level -= increment
                        if (level <= 0) { stop(); return }
                    }
                }
                if (glideLeft > 0) {
                    width += widthDelta
                    if (--glideLeft == 0) width = targetWidth
                }
                val signal = if (noiseProgram) noiseLevel else shape.sample(phase, width)
                out[i] += signal * (level ushr 14) * (1f / 65536f) * velocity
                phase += width
                if (phase >= 32.0) {
                    phase %= 32.0
                    if (noiseProgram) noiseLevel = nextNoise()
                }
                // Synth Drum chute de 0,6 centième par trame (1,2 à demi-fréquence).
                if (drumFrames > 0) {
                    width *= drumPitchRatio
                    if (--drumFrames == 0) { stop(); return }
                }
            }
        }
    }

    private enum class Stage { ATTACK, DECAY, HOLD, RELEASE }

    private companion object { const val FULL = 65535 shl 14 }
}
