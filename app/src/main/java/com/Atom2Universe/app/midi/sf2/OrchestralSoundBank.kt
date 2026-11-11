package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.studio.dsp.Clap
import com.Atom2Universe.app.studio.dsp.Hat
import com.Atom2Universe.app.studio.dsp.Kick
import com.Atom2Universe.app.studio.dsp.OrchestralBank
import com.Atom2Universe.app.studio.dsp.Perc
import com.Atom2Universe.app.studio.dsp.Snare
import com.Atom2Universe.app.studio.dsp.Svf
import com.Atom2Universe.app.studio.dsp.Tom
import kotlin.math.roundToInt

/** Un programme GM de Studio. Le lecteur charge les profils du morceau avant de lancer le fil audio. */
class OrchestralProgram internal constructor(val number: Int) {
    @Volatile private var loaded: OrchestralBank.Program? = null

    internal fun profile(): OrchestralBank.Program? {
        if (number == 0 || number == 11) return null
        return loaded ?: OrchestralBank.load(number).also { loaded = it }
    }

    internal fun unload() { loaded = null }
}

internal class OrchestralSoundBank {
    companion object {
        const val ID = "builtin:orchestral"
        private const val DRUM_RATE = 24000
        private const val SNARE_KEY = 40

        // Les percussions de Studio sont des frappes synthétisées. Les partager sous forme de PCM
        // permet au canal GM 10 de les jouer comme un kit SF2 ordinaire (hauteur et charlestons étouffés).
        private val drums by lazy {
            val kit = listOf(Kick(DRUM_RATE), Snare(DRUM_RATE), Clap(DRUM_RATE),
                Hat(DRUM_RATE, false), Hat(DRUM_RATE, true), Tom(DRUM_RATE), Perc(DRUM_RATE))
            // Quatre coups successifs de la caisse ample de Studio : le bruit du timbre
            // continue d'un coup à l'autre. Les quatre prises sont calculées au chargement.
            val rollSnare = Snare(DRUM_RATE).apply { setVoice(kit = 2, tune = 1, length = 110) }
            val instruments = kit + List(4) { rollSnare }
            val samples = ArrayList<ShortArray>()
            val starts = IntArray(instruments.size)
            var size = 0
            instruments.forEachIndexed { i, instrument ->
                val length = 2 * DRUM_RATE
                val mono = FloatArray(length)
                val other = FloatArray(length)
                instrument.pan = -1f
                instrument.noteOn(60, 1f)
                instrument.render(mono, other, other, other, 0, length)
                if (instrument === rollSnare) {
                    val lowPass = Svf(DRUM_RATE).apply { set(4400f, 0.707f) }
                    val attackFrames = DRUM_RATE * 4 / 1000
                    for (frame in mono.indices) {
                        lowPass.tick(mono[frame])
                        val attack = (frame.toFloat() / attackFrames).coerceAtMost(1f)
                        mono[frame] = lowPass.lp * attack * attack * (3f - 2f * attack)
                    }
                }
                val last = mono.indexOfLast { kotlin.math.abs(it) > 1f / 32768f }
                val pcm = ShortArray((last + 2).coerceIn(2, length)) { frame ->
                    (mono[frame].coerceIn(-1f, 1f) * 32767).roundToInt().toShort()
                }
                starts[i] = size + 2
                size += pcm.size + 4
                samples.add(pcm)
            }
            val pcm = ShortArray(size)
            samples.forEachIndexed { i, sample -> sample.copyInto(pcm, starts[i]) }
            fun drumRegion(sound: Int, key: Int, root: Int = key) =
                region(sound, key..key, DRUM_RATE, starts[sound].toLong(),
                    (starts[sound] + samples[sound].size).toLong(), root, null)
            val snareStrokes = (kit.size until instruments.size).map { drumRegion(it, SNARE_KEY) }
            val regions = (0..127).map { key ->
                if (key == SNARE_KEY) return@map snareStrokes.first().copy(strikeVariants = snareStrokes)
                val sound = when (key) {
                    35, 36 -> 0
                    37, 38 -> 1
                    39 -> 2
                    42, 44, 54, 69, 70 -> 3
                    46, 49, 51, 52, 53, 55, 57, 59 -> 4
                    41, 43, 45, 47, 48, 50, in 60..66 -> 5
                    else -> 6
                }
                val root = if (sound == 5) (if (key >= 60) 63 else 45) else key
                drumRegion(sound, key, root)
                    .copy(exclusiveClass = if (key == 42 || key == 44 || key == 46) 1 else 0)
            }
            pcm to Sf2Preset("Studio", 128, 0, regions)
        }

        private fun region(id: Int, keys: IntRange, rate: Int, start: Long, end: Long,
                           root: Int, program: OrchestralProgram?) = Sf2Region(
            keyRange = keys, velRange = 0..127, sampleId = id, sampleRate = rate, sampleStart = start,
            sampleEnd = end, loopStart = 0, loopEnd = 0, hasLoop = false, rootKey = root, coarseTune = 0,
            fineTune = 0, scaleTuning = if (program == null) 100 else 0, pitchCorrection = 0, attenuation = 0,
            pan = 0f, exclusiveClass = 0, reverbSend = null, chorusSend = null,
            volumeEnvelope = VolumeEnvelope(attack = 0f, release = 2f),
            sampleName = program?.let { ProgramInfo.GM_PROGRAM_NAMES[it.number] } ?: "Studio",
            filterFc = null, filterQ = null, vibLfo = null, modLfo = null, modEnvelope = null,
            modEnvToPitch = null, modEnvToFilterFc = null, forcedKeyNum = null, forcedVelocity = null,
            orchestralProgram = program
        )
    }

    private val programs = Array(128, ::OrchestralProgram)
    val file: Sf2File

    init {
        val (pcm, kit) = drums
        val presets = programs.associate { program ->
            val name = ProgramInfo.GM_PROGRAM_NAMES[program.number]
            val region = region(program.number, 0..127, 48000, 0, 0, 60, program)
            "0:${program.number}" to Sf2Preset(name, 0, program.number, listOf(region))
        } + ("128:0" to kit)
        file = Sf2File("Orchestral", pcm, presets, emptyList())
    }

    fun preparePrograms(used: Set<Int>) {
        for (program in programs) if (program.number !in used) program.unload()
        for (number in used) programs[number].profile()
    }
}
