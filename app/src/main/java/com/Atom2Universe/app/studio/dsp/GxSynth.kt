package com.Atom2Universe.app.studio.dsp

import kotlin.math.roundToInt

/** Un cycle SCC. GXSCC moyenne les paliers traversés pendant une trame de sortie. */
internal class GxWave(gain: Int, val octave: Int, points: ByteArray, piano: Boolean = false) {
    // Le même facteur global pour toutes les ondes préserve les niveaux relatifs dans le mix de Studio.
    private val levels = DoubleArray(32) { points[it] * gain / 65536.0 }
    private val integral = DoubleArray(33).also {
        for (i in levels.indices) it[i + 1] = it[i] + levels[i]
    }
    private val bass = FloatArray(CYCLE)
    private val mellow = FloatArray(CYCLE)
    // Le piano garde les harmoniques de sa table ; dans l'aigu, on retire celles qui se replieraient.
    private val pianoBands = if (piano) Array(6) { if (it == 0) bass else FloatArray(CYCLE) } else emptyArray()
    private var fundamental = 0f
    private var body = octaveAndTwelfth

    init {
        // Un cycle de 32 points décrit 16 harmoniques. Les répliques au-delà (notamment
        // H31 et H33 sur le piano) viennent uniquement du maintien de chaque palier.
        bass.fill(levels.average().toFloat())
        mellow.fill(bass[0])
        for (band in 1 until pianoBands.size) pianoBands[band].fill(bass[0])
        for (h in 1..16) {
            var a = 0.0
            var b = 0.0
            for (i in levels.indices) {
                val center = (i * CELL + CELL / 2) * h
                a += levels[i] * sine[(center + CYCLE / 4) and MASK]
                b += levels[i] * sine[center and MASK]
            }
            val angle = Math.PI * h / 32.0
            val gain = 2.0 / 32.0 * kotlin.math.sin(angle) / angle
            a *= gain
            b *= gain
            if (h == 1) fundamental = kotlin.math.hypot(a, b).toFloat()
            // Le piano triangulaire possède déjà une douzième. Renforcer seulement
            // l'octave évite de lui donner deux pulsations distinctes par période.
            if (h == 3 && kotlin.math.hypot(a, b) >= fundamental * 0.05) body = octaveOnly
            val warmth = ((1.0 + kotlin.math.cos(Math.PI * (h - 1) / 16.0)) * 0.5).toFloat()
            for (i in bass.indices) {
                val partial = (a * sine[(i * h + CYCLE / 4) and MASK] + b * sine[(i * h) and MASK]).toFloat()
                bass[i] += partial
                mellow[i] += partial * warmth
                for (band in 1 until pianoBands.size) {
                    if (h > (16 shr band)) break
                    pianoBands[band][i] += partial
                }
            }
        }
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

    fun bassSample(phase: Double, width: Double, softness: Float, presence: Float): Float {
        val pos = (phase + width * 0.5) * CELL
        val cell = pos.toInt()
        val i = cell and MASK
        val j = (cell + 1) and MASK
        val fraction = (pos - cell).toFloat()
        val base = bass[i] + (bass[j] - bass[i]) * fraction
        val tone = if (softness == 0f) base else {
            val soft = mellow[i] + (mellow[j] - mellow[i]) * fraction
            base + (soft - base) * softness
        }
        return if (presence == 0f) tone else {
            tone + fundamental * presence * (body[i] + (body[j] - body[i]) * fraction)
        }
    }

    fun pianoSample(phase: Double, width: Double, band: Int, blend: Float): Float {
        val pos = (phase + width * 0.5) * CELL
        val cell = pos.toInt()
        val i = cell and MASK
        val j = (cell + 1) and MASK
        val fraction = (pos - cell).toFloat()
        val richer = pianoBands[band]
        val sample = richer[i] + (richer[j] - richer[i]) * fraction
        if (blend == 0f) return sample
        val leaner = pianoBands[band + 1]
        return sample + (leaner[i] + (leaner[j] - leaner[i]) * fraction - sample) * blend
    }

    private companion object {
        const val CYCLE = 512
        const val CELL = CYCLE / 32
        const val MASK = CYCLE - 1
        val sine = DoubleArray(CYCLE) { kotlin.math.sin(2.0 * Math.PI * it / CYCLE) }
        // Les deux pianos démarrent en phase au centre du premier palier. Un peu d'octave
        // et de douzième rend la hauteur perceptible sur un haut-parleur qui atténue le grave.
        val octaveOnly = FloatArray(CYCLE) { (0.22 * sine[(2 * (it - CELL / 2)) and MASK]).toFloat() }
        val octaveAndTwelfth = FloatArray(CYCLE) { octaveOnly[it] - (0.12 * sine[(3 * (it - CELL / 2)) and MASK]).toFloat() }
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
    @Volatile var smoothBass: Boolean = false
    private val smoothingStep = 1f / (sampleRate * 0.005f).coerceAtLeast(1f)
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
        private var smoothMix = 0f
        private var bassWeight = 0f
        private var piano = false
        private var pianoBand = 0
        private var pianoBlend = 0f
        private var presence = 0f
        private var upperBassWidth = 0.0
        private var bassWidthSpan = 0.0
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
            piano = program == 0
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
            upperBassWidth = phaseWidth(60)
            bassWidthSpan = upperBassWidth - phaseWidth(59)
            updateBass()
            smoothMix = if (smoothBass) 1f else 0f
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

        private fun updateBass() {
            // Le traitement du grave rejoint le timbre normal entre si3 et do4.
            bassWeight = if (noiseProgram || drumFrames > 0) 0f else
                ((upperBassWidth - width) / bassWidthSpan).coerceIn(0.0, 1.0).toFloat()
            presence = if (piano) ((1.0 - width / upperBassWidth) / 0.75).coerceIn(0.0, 1.0).toFloat() else 0f
            if (piano) {
                // width = 32 × fréquence / fréquence d'échantillonnage. Quand width atteint 1,
                // H16 atteint Nyquist : la transition vers la table à 8 harmoniques est déjà finie.
                pianoBand = Math.getExponent(2.0 * width).coerceIn(0, 4)
                pianoBlend = (Math.scalb(width, 1 - pianoBand) - 1.0).coerceIn(0.0, 1.0).toFloat()
            }
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
            val smoothTarget = if (smoothBass) 1f else 0f
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
                    updateBass()
                }
                if (smoothMix != smoothTarget) {
                    smoothMix = if (smoothTarget > smoothMix) (smoothMix + smoothingStep).coerceAtMost(1f)
                        else (smoothMix - smoothingStep).coerceAtLeast(0f)
                }
                val signal = when {
                    noiseProgram -> noiseLevel
                    bassWeight >= 1f -> shape.bassSample(phase, width, smoothMix, presence)
                    else -> {
                        val normal = if (piano) shape.pianoSample(phase, width, pianoBand, pianoBlend)
                            else shape.sample(phase, width)
                        if (bassWeight == 0f) normal else
                            normal + (shape.bassSample(phase, width, smoothMix, presence) - normal) * bassWeight
                    }
                }
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
