package com.Atom2Universe.app.studio.dsp

import com.Atom2Universe.app.studio.engine.SynthBank
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/*
 * Le petit synthé soustractif : deux oscillateurs légèrement désaccordés (scie, carré, triangle ou sinus, avec
 * anti-repliement polyBLEP pour les formes à angles vifs) plus un sous-oscillateur sinus, un filtre passe-bas
 * résonant piloté par une enveloppe et par un LFO lent, une enveloppe de volume ADSR, et un étage de distorsion
 * sur le mélange des voix. Huit voix ; quand elles sont toutes prises, la nouvelle reprend la plus silencieuse en
 * phase de relâchement (sinon la plus ancienne) en repartant de son niveau actuel : jamais de saut audible.
 */

/**
 * Un son du synthé. Durées en secondes ; [envOctaves] = ouverture du filtre en octaves au début de la note ;
 * [lfoFilter] = amplitude du balayage du filtre par le LFO, en octaves ; [drive] de 0 (net) à 1 (très saturé) ;
 * [volume] ajuste le niveau pour que tous les sons soient aussi forts les uns que les autres.
 */
data class SynthPatch(
    val id: String,
    val wave: Int = WAVE_SAW,
    val detuneCents: Float = 8f,
    val subLevel: Float = 0f,
    val cutoffHz: Float = 1500f,
    val resonance: Float = 1f,
    val envOctaves: Float = 1f,
    val filterDecay: Float = 0.3f,
    val attack: Float = 0.005f,
    val decay: Float = 0.3f,
    val sustain: Float = 0.6f,
    val release: Float = 0.1f,
    val keyTrack: Float = 0.5f,
    val lfoRate: Float = 0f,
    val lfoFilter: Float = 0f,
    val drive: Float = 0f,
    val volume: Float = 1f,
    /** Écart en demi-tons du second oscillateur au premier (0 = même hauteur ; 12 = octave ; 19 = octave et quinte). */
    val interval: Int = 0,
) {
    /** Vrai si ce son joue l'enregistrement du micro ([WAVE_SAMPLE]). */
    val isSample: Boolean get() = wave == WAVE_SAMPLE

    /** Programme GM d'un instrument acoustique modélisé, même après l'avoir renommé dans « Mes sons ». */
    val gmProgram: Int? get() = when (wave) {
        WAVE_VIBRAPHONE -> 11
        WAVE_PIANO -> 0
        else -> null
    }

    companion object {
        const val WAVE_SAW = 0
        const val WAVE_SQUARE = 1
        const val WAVE_TRIANGLE = 2
        const val WAVE_SINE = 3

        /** L'oscillateur est le son enregistré au micro, rejoué plus aigu ou plus grave selon la note. */
        const val WAVE_SAMPLE = 4
        const val WAVE_VIBRAPHONE = 5
        const val WAVE_PIANO = 6
        internal const val WAVE_ORCHESTRAL = 7

        /** Les sons gardés par l'utilisateur ont un identifiant « mine:<nom> ». */
        const val MINE_PREFIX = "mine:"
    }
}

/** Un son enregistré au micro pour le synthé : les échantillons, leur fréquence, et la note MIDI (avec décimales) qu'ils jouent tels quels. */
class SynthSample(val data: FloatArray, val sourceRate: Int, val rootNote: Float)

object SynthPresets {
    val BASS = SynthPatch(
        id = "bass", wave = SynthPatch.WAVE_SAW, detuneCents = 7f, subLevel = 0.7f, cutoffHz = 380f, resonance = 2.2f,
        envOctaves = 2.6f, filterDecay = 0.25f, attack = 0.003f, decay = 0.25f, sustain = 0.55f, release = 0.09f,
        keyTrack = 0.3f,
    )
    val SUB = SynthPatch(
        id = "sub", volume = 0.93f, wave = SynthPatch.WAVE_SINE, detuneCents = 3f, subLevel = 0.5f, cutoffHz = 300f, resonance = 0.7f,
        envOctaves = 0.3f, filterDecay = 0.3f, attack = 0.01f, decay = 0.3f, sustain = 0.8f, release = 0.15f,
        keyTrack = 0f, drive = 0.1f,
    )
    val ACID = SynthPatch(
        id = "acid", volume = 1.06f, wave = SynthPatch.WAVE_SAW, detuneCents = 0f, subLevel = 0f, cutoffHz = 500f, resonance = 6f,
        envOctaves = 3.2f, filterDecay = 0.18f, attack = 0.002f, decay = 0.2f, sustain = 0.35f, release = 0.06f,
        keyTrack = 0.5f, drive = 0.35f,
    )
    val DIST_BASS = SynthPatch(
        id = "dist_bass", volume = 1.22f, wave = SynthPatch.WAVE_SAW, detuneCents = 10f, subLevel = 0.6f, cutoffHz = 600f,
        resonance = 1.5f, envOctaves = 1.5f, filterDecay = 0.3f, attack = 0.003f, decay = 0.2f, sustain = 0.6f,
        release = 0.08f, keyTrack = 0.3f, drive = 0.85f,
    )
    val WOBBLE = SynthPatch(
        id = "wobble", volume = 0.92f, wave = SynthPatch.WAVE_SAW, detuneCents = 9f, subLevel = 0.6f, cutoffHz = 400f, resonance = 3.5f,
        envOctaves = 0.5f, filterDecay = 0.5f, attack = 0.005f, decay = 0.2f, sustain = 0.8f, release = 0.1f,
        keyTrack = 0.2f, lfoRate = 3f, lfoFilter = 2f, drive = 0.3f,
    )
    val PAD = SynthPatch(
        id = "pad", volume = 1.26f, wave = SynthPatch.WAVE_SAW, detuneCents = 16f, subLevel = 0.2f, cutoffHz = 1500f, resonance = 0.9f,
        envOctaves = 0.8f, filterDecay = 1.4f, attack = 0.45f, decay = 0.9f, sustain = 0.75f, release = 1.1f,
        keyTrack = 0.6f, lfoRate = 0.2f, lfoFilter = 0.3f,
    )
    val STRINGS = SynthPatch(
        id = "strings", volume = 1.13f, wave = SynthPatch.WAVE_SAW, detuneCents = 14f, subLevel = 0f, cutoffHz = 2200f, resonance = 0.8f,
        envOctaves = 0.5f, filterDecay = 0.8f, attack = 0.35f, decay = 0.5f, sustain = 0.85f, release = 0.6f,
        keyTrack = 0.5f,
    )
    val DRONE = SynthPatch(
        id = "drone", volume = 0.93f, wave = SynthPatch.WAVE_SAW, detuneCents = 22f, subLevel = 0.5f, cutoffHz = 700f, resonance = 1.5f,
        envOctaves = 0f, filterDecay = 1f, attack = 1.5f, decay = 1f, sustain = 1f, release = 2.5f, keyTrack = 0.3f,
        lfoRate = 0.08f, lfoFilter = 1.2f, drive = 0.1f,
    )
    val LEAD = SynthPatch(
        id = "lead", volume = 1.12f, wave = SynthPatch.WAVE_SAW, detuneCents = 11f, subLevel = 0.15f, cutoffHz = 1800f, resonance = 3.2f,
        envOctaves = 2.0f, filterDecay = 0.35f, attack = 0.006f, decay = 0.3f, sustain = 0.65f, release = 0.2f,
        keyTrack = 0.8f,
    )
    val ARP = SynthPatch(
        id = "arp", wave = SynthPatch.WAVE_SQUARE, detuneCents = 6f, subLevel = 0f, cutoffHz = 1600f, resonance = 2f,
        envOctaves = 2f, filterDecay = 0.15f, attack = 0.002f, decay = 0.18f, sustain = 0.1f, release = 0.1f,
        keyTrack = 0.8f,
    )
    val PLUCK = SynthPatch(
        id = "pluck", volume = 1.57f, wave = SynthPatch.WAVE_SAW, detuneCents = 8f, subLevel = 0f, cutoffHz = 1200f, resonance = 1.5f,
        envOctaves = 2.4f, filterDecay = 0.22f, attack = 0.002f, decay = 0.3f, sustain = 0f, release = 0.15f,
        keyTrack = 0.8f,
    )
    val BELL = SynthPatch(
        // Deux sinus : la note et sa douzième (octave + quinte, 3e harmonique). Un écart « inharmonique » de 1000 centièmes
        // faisait sonner la cloche trois quarts de ton trop bas (mesuré : −75 centièmes) ; ici elle est juste.
        id = "bell", volume = 1.24f, wave = SynthPatch.WAVE_SINE, detuneCents = 5f, subLevel = 0f, cutoffHz = 5000f,
        resonance = 0.7f, envOctaves = 0f, filterDecay = 1f, attack = 0.002f, decay = 1.4f, sustain = 0f, release = 1.2f,
        keyTrack = 0f, interval = 19,
    )

    /** Le son enregistré au micro : rejoué tel quel (filtre grand ouvert), avec une petite attaque et un relâchement doux. */
    val MY_VOICE = SynthPatch(
        id = "my_voice", wave = SynthPatch.WAVE_SAMPLE, detuneCents = 0f, subLevel = 0f, cutoffHz = 9000f, resonance = 0.7f,
        envOctaves = 0f, filterDecay = 1f, attack = 0.004f, decay = 0.5f, sustain = 1f, release = 0.25f, keyTrack = 0f,
        volume = 1.2f,
    )

    val ORGAN = SynthPatch(
        id = "organ", wave = SynthPatch.WAVE_SINE, interval = 12, detuneCents = 0f, subLevel = 0.12f,
        cutoffHz = 4500f, resonance = 0.75f, envOctaves = 0f, attack = 0.012f, decay = 0.12f,
        sustain = 0.88f, release = 0.085f, drive = 0.09f, keyTrack = 0.2f, volume = 1.12f,
    )
    val MALLET = SynthPatch(
        id = "mallet", wave = SynthPatch.WAVE_TRIANGLE, interval = 12, detuneCents = 2f,
        cutoffHz = 1050f, resonance = 0.8f, envOctaves = 1.7f, filterDecay = 0.065f,
        attack = 0.003f, decay = 0.5f, sustain = 0f, release = 0.19f, keyTrack = 0.7f, volume = 1.88f,
    )
    val VIBRAPHONE = SynthPatch(
        id = "vibraphone", wave = SynthPatch.WAVE_VIBRAPHONE, interval = 24, detuneCents = 0f,
        cutoffHz = 10_000f, resonance = 0.707f, envOctaves = 0f, keyTrack = 0f,
        attack = 0.004f, decay = VibraphoneBar.DEFAULT_DECAY, sustain = 0f, release = 1.9f, volume = 2.47f,
    )
    val BRASS = SynthPatch(
        id = "brass", wave = SynthPatch.WAVE_SAW, detuneCents = 5f,
        cutoffHz = 1350f, resonance = 1.25f, envOctaves = 0.6f, filterDecay = 0.28f,
        attack = 0.09f, decay = 0.25f, sustain = 0.8f, release = 0.16f, keyTrack = 0.6f, volume = 1.1f,
    )

    private val PICK_BASS = SynthPatch(
        id = "pick_bass", volume = 1.30f, wave = SynthPatch.WAVE_TRIANGLE, detuneCents = 0f, subLevel = 0.35f,
        cutoffHz = 430f, resonance = 1.4f, envOctaves = 3.5f, filterDecay = 0.095f,
        attack = 0.003f, decay = 0.38f, sustain = 0.12f, release = 0.07f, keyTrack = 0.4f,
    )
    private val OCTAVE_BASS = SynthPatch(
        id = "octave_bass", volume = 1.07f, wave = SynthPatch.WAVE_SQUARE, interval = 12, detuneCents = 0f, subLevel = 0.45f,
        cutoffHz = 650f, resonance = 1.1f, envOctaves = 1.6f, filterDecay = 0.14f,
        attack = 0.006f, decay = 0.18f, sustain = 0.6f, release = 0.06f, drive = 0.08f,
    )
    private val TINES = SynthPatch(
        id = "tines", volume = 1.23f, wave = SynthPatch.WAVE_SINE, interval = 12, detuneCents = 3f,
        cutoffHz = 820f, resonance = 0.85f, envOctaves = 2.7f, filterDecay = 0.12f,
        attack = 0.004f, decay = 1.6f, sustain = 0.16f, release = 0.22f, keyTrack = 0.7f,
    )
    private val HARPSICHORD = SynthPatch(
        id = "harpsichord", volume = 1.58f, wave = SynthPatch.WAVE_SAW, interval = 12, detuneCents = 2f,
        cutoffHz = 2300f, resonance = 0.75f, envOctaves = 1.7f, filterDecay = 0.09f,
        attack = 0.003f, decay = 0.48f, sustain = 0.08f, release = 0.055f, keyTrack = 0.8f,
    )
    private val FLUTE = SynthPatch(
        id = "flute", volume = 1.26f, wave = SynthPatch.WAVE_SINE, interval = 12, detuneCents = 0f,
        cutoffHz = 720f, resonance = 0.65f, envOctaves = 0.4f, filterDecay = 0.13f,
        attack = 0.075f, decay = 0.3f, sustain = 0.91f, release = 0.13f, keyTrack = 0.8f,
    )
    private val CLARINET = SynthPatch(
        id = "clarinet", volume = 0.60f, wave = SynthPatch.WAVE_SQUARE, detuneCents = 0f,
        cutoffHz = 1320f, resonance = 1.3f, envOctaves = 0.15f, filterDecay = 0.18f,
        attack = 0.042f, decay = 0.25f, sustain = 0.85f, release = 0.105f, keyTrack = 0.7f,
    )
    private val GLASS_PAD = SynthPatch(
        id = "glass_pad", volume = 1.12f, wave = SynthPatch.WAVE_SINE, interval = 19, detuneCents = 3f,
        cutoffHz = 1500f, resonance = 0.85f, envOctaves = 0f, filterDecay = 0.5f,
        attack = 0.8f, decay = 0.7f, sustain = 0.85f, release = 1.8f, keyTrack = 0.6f,
        lfoRate = 0.17f, lfoFilter = 0.8f,
    )
    private val FIFTH_PAD = SynthPatch(
        id = "fifth_pad", volume = 1.33f, wave = SynthPatch.WAVE_TRIANGLE, interval = 7, detuneCents = 4f, subLevel = 0.15f,
        cutoffHz = 1100f, resonance = 1.3f, envOctaves = 0.35f, filterDecay = 0.9f,
        attack = 0.32f, decay = 0.7f, sustain = 0.8f, release = 1.4f,
        lfoRate = 0.28f, lfoFilter = 1.1f,
    )
    private val KALIMBA = SynthPatch(
        id = "kalimba", volume = 1.46f, wave = SynthPatch.WAVE_SINE, interval = 24, detuneCents = 0f,
        cutoffHz = 630f, resonance = 0.8f, envOctaves = 3.3f, filterDecay = 0.08f,
        attack = 0.002f, decay = 0.72f, sustain = 0f, release = 0.32f, keyTrack = 0.65f,
    )
    private val ARCADE = SynthPatch(
        id = "arcade", volume = 0.77f, wave = SynthPatch.WAVE_SQUARE, detuneCents = 0f,
        cutoffHz = 4500f, resonance = 0.72f, envOctaves = 0f, filterDecay = 0.1f,
        attack = 0.002f, decay = 0.065f, sustain = 0.67f, release = 0.035f, keyTrack = 0.4f,
    )

    val BASSES = listOf(BASS, SUB, ACID, DIST_BASS, WOBBLE, PICK_BASS, OCTAVE_BASS)
    val ACOUSTIC_PIANO = SynthPatch(
        id = "acoustic_piano", wave = SynthPatch.WAVE_PIANO, detuneCents = 0f, cutoffHz = 10_000f,
        resonance = 0.707f, envOctaves = 0f, keyTrack = 0f, attack = 0.003f, decay = 4f,
        sustain = 0f, release = 0.38f, volume = 2.65f,
    )
    val KEYS = listOf(ORGAN, TINES, HARPSICHORD)
    val LEADS = listOf(LEAD, BRASS, FLUTE, CLARINET, ARCADE)
    val PADS = listOf(PAD, STRINGS, DRONE, GLASS_PAD, FIFTH_PAD)
    val PLUCKS = listOf(ARP, PLUCK, BELL, MALLET, KALIMBA)
    val ALL = BASSES + KEYS + LEADS + PADS + PLUCKS

    /** Programme General MIDI proposé au premier passage en SCC/Famicom sur cette piste. */
    fun chipProgram(patch: SynthPatch): Int = patch.gmProgram ?: when (patch.id) {
        BASS.id, ACID.id, SUB.id -> 38 // Synth Bass 1
        DIST_BASS.id, WOBBLE.id, OCTAVE_BASS.id -> 39
        PICK_BASS.id -> 34
        ORGAN.id -> 16
        TINES.id -> 4
        HARPSICHORD.id -> 6
        STRINGS.id -> 48
        PAD.id -> 89
        DRONE.id -> 88
        GLASS_PAD.id -> 92
        FIFTH_PAD.id -> 86
        BRASS.id -> 61
        FLUTE.id -> 73
        CLARINET.id -> 71
        ARP.id, ARCADE.id -> 80
        LEAD.id -> 81
        PLUCK.id -> 24
        BELL.id -> 10
        MALLET.id -> 12
        KALIMBA.id -> 108
        else -> 0
    }

    fun byId(id: String): SynthPatch = when (id) {
        MY_VOICE.id -> MY_VOICE
        ACOUSTIC_PIANO.id -> ACOUSTIC_PIANO
        VIBRAPHONE.id -> VIBRAPHONE
        else -> ALL.firstOrNull { it.id == id } ?: BASS
    }

    internal val orchestral = SynthPatch("orchestral", wave = SynthPatch.WAVE_ORCHESTRAL, detuneCents = 0f,
        cutoffHz = 16000f, resonance = 0.707f, envOctaves = 0f, keyTrack = 0f, attack = 0.002f, sustain = 1f, volume = 2f)
}

class PolySynth(sampleRate: Int, voiceCount: Int = 8) : Instrument(sampleRate), AutoCloseable {

    @Volatile var patch: SynthPatch = SynthPresets.BASS

    /** Le son enregistré au micro que joue un patch [SynthPatch.WAVE_SAMPLE] (`null` : ce patch se tait). */
    @Volatile var sample: SynthSample? = null

    private val voices = Array(voiceCount) { Voice() }
    private var age = 0L

    // Les moteurs sont créés sur le fil des réglages ; les notes déjà lancées finissent sur leur moteur.
    @Volatile private var chip: GxSynth? = null
    @Volatile private var fm: Ym2413Synth? = null
    @Volatile private var selectedBank = SynthBank.STUDIO
    @Volatile private var orchestralProgram = 0
    @Volatile private lateinit var orchestralProfile: OrchestralBank.Program
    private fun currentPatch() = if (selectedBank == SynthBank.ORCHESTRAL) when (orchestralProgram) {
        0 -> SynthPresets.ACOUSTIC_PIANO
        11 -> SynthPresets.VIBRAPHONE
        else -> SynthPresets.orchestral
    } else patch
    private val mixScratch = FloatArray(Instrument.MAX_CHUNK)
    private var closed = false

    /** Une voix de Studio pilotée par le séquenceur, les contrôleurs et le mixeur du moteur SF2. */
    internal class OrchestralNote(rate: Int, blockSize: Int) {
        val samples = FloatArray(blockSize)
        private val synth = PolySynth(rate, 1)
        private val voice = synth.voices[0].apply { prepareOrchestral() }
        private var lastPitch = 1f

        fun start(program: Int, profile: OrchestralBank.Program?, note: Int, velocity: Float) {
            synth.selectedBank = SynthBank.ORCHESTRAL
            synth.orchestralProgram = program
            voice.active = false
            voice.start(note, velocity, synth.currentPatch(), 0L, profile)
            lastPitch = 1f
        }

        fun release() = voice.release()

        fun render(count: Int, pitch: Float): Boolean {
            if (pitch != lastPitch) {
                voice.tuneOrchestral(pitch)
                lastPitch = pitch
            }
            return synth.renderStudio(samples, count)
        }
    }

    @Synchronized
    fun selectBank(bank: SynthBank, program: Int, midiChannel: Int, smoothBass: Boolean = false,
                   brightness: Float = 0.5f, attack: Float = 0.5f) {
        if (closed) return
        when (bank) {
            SynthBank.FM -> {
                val source = fm ?: Ym2413Synth(sampleRate).also { fm = it }
                source.select(program, brightness, attack)
            }
            SynthBank.SCC, SynthBank.FAMICOM -> {
                val source = chip ?: GxSynth(sampleRate).also { chip = it }
                source.midiChannel = midiChannel
                source.smoothBass = smoothBass
                source.select(bank == SynthBank.FAMICOM, program)
            }
            SynthBank.STUDIO -> Unit
            SynthBank.ORCHESTRAL -> {
                val next = program.coerceIn(0, 127)
                if (next != orchestralProgram && next != 0 && next != 11) {
                    orchestralProfile = OrchestralBank.load(next)
                    for (v in voices) v.prepareOrchestral()
                }
                orchestralProgram = next
            }
        }
        selectedBank = bank
    }

    fun setFmPad(brightness: Float, attack: Float) {
        fm?.select(0, brightness, attack)
    }

    @Synchronized
    override fun close() {
        closed = true
        fm?.close()
        fm = null
    }

    override fun glide(from: Int, to: Int, velocity: Float) {
        val ym = fm
        if (ym != null && ym.holds(from)) {
            ym.glide(from, to, velocity)
            return
        }
        val gx = chip
        if (gx != null && gx.holds(from)) {
            gx.glide(from, to, velocity)
            return
        }
        // La voix qui tient [from] change de hauteur en glissant, sans repartir de zéro (pad thérémine)
        for (v in voices) if (v.active && v.held && v.note == from) {
            v.retune(to)
            return
        }
        noteOn(to, velocity)
    }

    override val activeVoices: Int
        get() {
            var n = 0
            for (v in voices) if (v.active) n++
            return n + (chip?.activeVoices ?: 0) + (fm?.activeVoices ?: 0)
        }

    override fun noteOn(note: Int, velocity: Float) {
        val bank = selectedBank
        if (!bank.isGx) chip?.noteOff(note)
        if (bank != SynthBank.FM) fm?.noteOff(note)
        if (bank != SynthBank.STUDIO && bank != SynthBank.ORCHESTRAL) {
            for (v in voices) if (v.active && v.held && v.note == note) v.release()
            if (bank == SynthBank.FM) fm?.noteOn(note, velocity) else chip?.noteOn(note, velocity)
            return
        }
        // Une même note rejouée reprend sa voix (sinon deux voix identiques s'additionnent).
        var chosen: Voice? = null
        for (v in voices) if (v.active && v.note == note && v.held) chosen = v
        if (chosen == null) for (v in voices) if (!v.active) { chosen = v; break }
        if (chosen == null) {
            var best = Float.MAX_VALUE
            for (v in voices) {
                if (v.stage == RELEASE && v.level < best) { best = v.level; chosen = v }
            }
        }
        if (chosen == null) {
            var oldest = Long.MAX_VALUE
            for (v in voices) if (v.age < oldest) { oldest = v.age; chosen = v }
        }
        chosen!!.start(note, velocity, currentPatch(), age++)
    }

    override fun noteOff(note: Int) {
        chip?.noteOff(note)
        fm?.noteOff(note)
        for (v in voices) if (v.active && v.held && v.note == note) v.release()
    }

    override fun allOff() {
        chip?.allOff()
        fm?.allOff()
        for (v in voices) if (v.active && v.held) v.release()
    }

    override fun renderMono(out: FloatArray, count: Int): Boolean {
        var mixed = renderStudio(out, count)
        chip?.let { mixed = mixSource(out, count, mixed, it::renderMono) }
        fm?.let { mixed = mixSource(out, count, mixed, it::renderMono) }
        return mixed
    }

    private inline fun mixSource(out: FloatArray, count: Int, mixed: Boolean, render: (FloatArray, Int) -> Boolean): Boolean {
        if (!mixed) return render(out, count)
        if (render(mixScratch, count)) for (i in 0 until count) out[i] += mixScratch[i]
        return true
    }

    private fun renderStudio(out: FloatArray, count: Int): Boolean {
        var any = false
        for (v in voices) if (v.active) { any = true; break }
        if (!any) return false
        java.util.Arrays.fill(out, 0, count, 0f)
        for (v in voices) if (v.active) v.render(out, count)

        // Distorsion et niveau du son, sur le mélange des voix.
        val p = currentPatch()
        val d = p.drive
        if (d > 0.001f) {
            // Le mélange des voix est faible (~0,2 de crête) : on le monte d'abord (DRIVE_PRE) pour que l'écrêtage morde,
            // puis on ramène le niveau pour qu'il ne grossisse pas quand la distorsion augmente.
            val g = 1f + d * 9f
            val pre = DRIVE_PRE * g
            val comp = p.volume / (DRIVE_PRE * (1f + (g - 1f) / DRIVE_COMP))
            for (i in 0 until count) out[i] = Fast.tanh(out[i] * pre) * comp
        } else if (p.volume != 1f) {
            for (i in 0 until count) out[i] *= p.volume
        }
        return true
    }

    private inner class Voice {
        var active = false
        var held = false
        var note = 0
        var age = 0L
        var stage = RELEASE
        var level = 0f

        private var p1 = 0f
        private var p2 = 0f
        private var ps = 0f
        private var inc1 = 0f
        private var inc2 = 0f
        private var incSub = 0f
        private var vel = 0f
        private var fEnv = 0f
        private var ctl = 0
        private var baseCutoff = 0f
        private var lfoPhase = 0f
        private var lfoInc = 0f
        private var p: SynthPatch = SynthPresets.BASS
        private var attackInc = 0f
        private var decayCoef = 0f
        private var releaseCoef = 0f
        private var filterCoef = 0f
        private val filter = Svf(sampleRate)
        private val bar = VibraphoneBar(sampleRate)
        private val piano = PianoStrings(sampleRate)
        private lateinit var orchestra: OrchestralVoice
        fun prepareOrchestral() {
            if (!::orchestra.isInitialized) orchestra = OrchestralVoice(sampleRate)
        }

        fun tuneOrchestral(multiplier: Float) {
            when (p.wave) {
                SynthPatch.WAVE_PIANO -> piano.tune(inc1 * multiplier, inc2 * multiplier)
                SynthPatch.WAVE_VIBRAPHONE -> bar.tune(inc1 * multiplier, inc2 * multiplier)
                SynthPatch.WAVE_ORCHESTRAL -> orchestra.tune(inc1 * multiplier)
            }
        }

        // Son enregistré : la position de lecture (en échantillons de la source), son pas, et le pas visé pendant un glissé
        private var smp: SynthSample? = null
        private var smpPos = 0.0
        private var smpInc = 0.0
        private var smpIncTarget = 0.0

        // Glissé de hauteur (pad thérémine) : les pas des oscillateurs rejoignent leur cible en quelques dizaines de ms
        private var gliding = false
        private var inc1Target = 0f
        private var inc2Target = 0f
        private var incSubTarget = 0f
        private var cutoffTarget = 0f

        private fun freqOf(n: Int): Float = 440f * 2f.pow((n - 69) / 12f)

        fun start(n: Int, velocity: Float, patch: SynthPatch, a: Long, profile: OrchestralBank.Program? = null) {
            val wasActive = active
            val wasBar = wasActive && p.wave == SynthPatch.WAVE_VIBRAPHONE
            val wasPiano = wasActive && p.wave == SynthPatch.WAVE_PIANO
            val wasOrchestral = wasActive && p.wave == SynthPatch.WAVE_ORCHESTRAL
            p = patch
            note = n
            age = a
            vel = velocity
            held = true
            active = true
            stage = ATTACK
            gliding = false
            if (!wasActive) level = 0f
            val freq = freqOf(n)
            val half = 2f.pow(patch.detuneCents / 2400f)
            inc1 = freq / half / sampleRate
            inc2 = freq * half * 2f.pow(patch.interval / 12f) / sampleRate
            incSub = freq / 2f / sampleRate
            attackInc = 1f / (maxOf(patch.attack, 0.002f) * sampleRate)
            val decayTime = if (patch.wave == SynthPatch.WAVE_VIBRAPHONE) {
                val ringTime = bar.strike(n.coerceIn(0, 127), velocity, wasBar)
                bar.tune(inc1, inc2)
                ringTime * patch.decay / VibraphoneBar.DEFAULT_DECAY
            } else if (patch.wave == SynthPatch.WAVE_PIANO) {
                piano.strike(n, velocity, wasPiano)
                piano.tune(inc1, inc2)
                // Les partiels ont leur propre décroissance ; l'enveloppe générale borne la queue.
                patch.decay * 12f
            } else patch.decay
            decayCoef = Fast.decayCoef(decayTime, sampleRate)
            releaseCoef = Fast.decayCoef(maxOf(patch.release, 0.005f), sampleRate)
            if (patch.wave == SynthPatch.WAVE_ORCHESTRAL) {
                releaseCoef = Fast.decayCoef(orchestra.strike(profile ?: orchestralProfile, n, velocity, wasOrchestral), sampleRate)
                orchestra.tune(inc1)
            }
            filterCoef = Fast.decayCoef(patch.filterDecay, sampleRate)
            baseCutoff = patch.cutoffHz * (freq / 261.63f).pow(patch.keyTrack)
            lfoInc = patch.lfoRate / sampleRate
            lfoPhase = 0f
            fEnv = 1f
            ctl = 0
            smp = if (patch.isSample) sample else null
            smp?.let { s ->
                smpPos = 0.0
                smpInc = s.sourceRate.toDouble() / sampleRate * Math.pow(2.0, (n - s.rootNote) / 12.0)
                smpIncTarget = smpInc
            }
            if (!wasActive) filter.reset()
        }

        /** Change de note sans nouvelle attaque : la hauteur glisse vers [n]. */
        fun retune(n: Int) {
            note = n
            val freq = freqOf(n)
            val half = 2f.pow(p.detuneCents / 2400f)
            inc1Target = freq / half / sampleRate
            inc2Target = freq * half * 2f.pow(p.interval / 12f) / sampleRate
            incSubTarget = freq / 2f / sampleRate
            cutoffTarget = p.cutoffHz * (freq / 261.63f).pow(p.keyTrack)
            smp?.let { s -> smpIncTarget = s.sourceRate.toDouble() / sampleRate * Math.pow(2.0, (n - s.rootNote) / 12.0) }
            gliding = true
        }

        fun release() {
            held = false
            stage = RELEASE
        }

        fun render(out: FloatArray, count: Int) {
            val sub = p.subLevel
            val sus = p.sustain
            val wave = p.wave
            val s = smp
            for (i in 0 until count) {
                if (ctl <= 0) {
                    ctl = CONTROL_PERIOD
                    if (wave == SynthPatch.WAVE_PIANO && piano.finished) { active = false; return }
                    if (wave == SynthPatch.WAVE_ORCHESTRAL && orchestra.finished) { active = false; return }
                    if (gliding) {
                        // Portamento : chaque pas d'oscillateur fait un bout du chemin (≈ 40 ms pour arriver)
                        inc1 += (inc1Target - inc1) * GLIDE
                        inc2 += (inc2Target - inc2) * GLIDE
                        incSub += (incSubTarget - incSub) * GLIDE
                        baseCutoff += (cutoffTarget - baseCutoff) * GLIDE
                        smpInc += (smpIncTarget - smpInc) * GLIDE
                        if (abs(inc1 - inc1Target) < inc1Target * 1e-4f) {
                            inc1 = inc1Target; inc2 = inc2Target; incSub = incSubTarget; baseCutoff = cutoffTarget; smpInc = smpIncTarget
                            gliding = false
                        }
                        if (wave == SynthPatch.WAVE_VIBRAPHONE) bar.tune(inc1, inc2)
                        if (wave == SynthPatch.WAVE_PIANO) piano.tune(inc1, inc2)
                        if (wave == SynthPatch.WAVE_ORCHESTRAL) orchestra.tune(inc1)
                    }
                    var octaves = p.envOctaves * fEnv
                    if (p.lfoFilter != 0f) {
                        octaves += p.lfoFilter * sin(lfoPhase * TWO_PI)
                        lfoPhase += lfoInc * CONTROL_PERIOD
                        if (lfoPhase >= 1f) lfoPhase -= 1f
                    }
                    // Le pad thérémine assombrit le son quand le doigt descend (jusqu'à trois octaves de filtre en moins)
                    val dim = expressionNow
                    if (dim < 0.999f) octaves -= (1f - dim) * 3f
                    filter.set(baseCutoff * 2.0.pow(octaves.toDouble()).toFloat(), p.resonance)
                }
                ctl--

                when (stage) {
                    ATTACK -> {
                        level += attackInc
                        if (level >= 1f) { level = 1f; stage = DECAY }
                    }
                    DECAY -> {
                        level = sus + (level - sus) * decayCoef
                        if (sus < 0.001f && level < SILENT) { active = false; return }
                    }
                    else -> {
                        level *= releaseCoef
                        if (level < SILENT) { active = false; return }
                    }
                }

                ps += incSub
                if (ps >= 1f) ps -= 1f
                val osc = if (wave == SynthPatch.WAVE_SAMPLE) {
                    // Le son enregistré, lu avec interpolation ; à sa fin, la voix s'éteint (sans enregistrement : silence)
                    if (s == null) { active = false; return }
                    val k = smpPos.toInt()
                    if (k >= s.data.size - 1) { active = false; return }
                    val f = (smpPos - k).toFloat()
                    smpPos += smpInc
                    (s.data[k] * (1f - f) + s.data[k + 1] * f) * 0.8f + sin(ps * TWO_PI) * sub * 0.5f
                } else if (wave == SynthPatch.WAVE_ORCHESTRAL) {
                    orchestra.next()
                } else if (wave == SynthPatch.WAVE_PIANO) {
                    piano.next() + sin(ps * TWO_PI) * sub * 0.5f
                } else if (wave == SynthPatch.WAVE_VIBRAPHONE) {
                    bar.next() + sin(ps * TWO_PI) * sub * 0.5f
                } else {
                    p1 += inc1
                    if (p1 >= 1f) p1 -= 1f
                    p2 += inc2
                    if (p2 >= 1f) p2 -= 1f
                    (shape(p1, inc1, wave) + shape(p2, inc2, wave)) * 0.4f + sin(ps * TWO_PI) * sub * 0.5f
                }
                filter.tick(osc)
                out[i] += filter.lp * level * vel * 0.5f
                fEnv *= filterCoef
            }
        }
    }

    private companion object {
        const val ATTACK = 0
        const val DECAY = 1
        const val RELEASE = 2
        const val CONTROL_PERIOD = 16
        const val SILENT = 1e-4f

        /** Part du chemin faite à chaque période de contrôle pendant un glissé (≈ 40 ms pour arriver à 48 kHz). */
        const val GLIDE = 0.06f
        const val TWO_PI = (2.0 * PI).toFloat()

        /** Gain d'entrée de l'étage de distorsion, et pente de la compensation de niveau (réglés par mesure). */
        const val DRIVE_PRE = 4f
        const val DRIVE_COMP = 12f

        fun shape(p: Float, dt: Float, wave: Int): Float = when (wave) {
            SynthPatch.WAVE_SQUARE -> {
                var q = (if (p < 0.5f) 1f else -1f) + polyBlep(p, dt)
                var p2 = p + 0.5f
                if (p2 >= 1f) p2 -= 1f
                q -= polyBlep(p2, dt)
                q
            }
            SynthPatch.WAVE_TRIANGLE -> 4f * abs(p - 0.5f) - 1f
            SynthPatch.WAVE_SINE -> sin(p * TWO_PI)
            else -> 2f * p - 1f - polyBlep(p, dt)
        }

        /** Correction de la marche d'une forme d'onde à angle vif (anti-repliement). */
        fun polyBlep(t: Float, dt: Float): Float {
            if (t < dt) {
                val x = t / dt
                return x + x - x * x - 1f
            }
            if (t > 1f - dt) {
                val x = (t - 1f) / dt
                return x * x + x + x + 1f
            }
            return 0f
        }
    }
}
