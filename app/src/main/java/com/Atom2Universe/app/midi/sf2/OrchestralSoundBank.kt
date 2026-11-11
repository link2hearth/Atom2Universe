package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.studio.dsp.Clap
import com.Atom2Universe.app.studio.dsp.Hat
import com.Atom2Universe.app.studio.dsp.Kick
import com.Atom2Universe.app.studio.dsp.OrchestralBank
import com.Atom2Universe.app.studio.dsp.OrchestralAttack
import com.Atom2Universe.app.studio.dsp.OrchestralStereo
import com.Atom2Universe.app.studio.dsp.OrchestralRanges
import com.Atom2Universe.app.studio.dsp.Perc
import com.Atom2Universe.app.studio.dsp.Snare
import com.Atom2Universe.app.studio.dsp.Svf
import com.Atom2Universe.app.studio.dsp.Tom
import java.util.concurrent.FutureTask
import kotlin.math.roundToInt

/** Un programme GM de Studio. Le lecteur charge les profils du morceau avant de lancer le fil audio. */
class OrchestralProgram internal constructor(val number: Int, private val channel: OrchestralBank.Channel) {
    internal val isRight get() = channel == OrchestralBank.Channel.RIGHT
    @Volatile private var loaded: OrchestralBank.Program? = null

    internal fun profile(): OrchestralBank.Program? {
        if (number == 0 || number == 11) return null
        return loaded ?: OrchestralBank.load(number, channel).also { loaded = it }
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

    private val programs = (0..127).associateWith { number ->
        if (number == 48) listOf(OrchestralProgram(number, OrchestralBank.Channel.LEFT),
            OrchestralProgram(number, OrchestralBank.Channel.RIGHT))
        else listOf(OrchestralProgram(number, OrchestralBank.Channel.MONO))
    }
    val file: Sf2File
    private val prepared = mutableSetOf<OrchestralBank.Spectrum>()

    fun clearAttacks() {
        for (spectrum in prepared) spectrum.attack = null
        prepared.clear()
    }

    init {
        val (drumPcm, kit) = drums
        // Le piano : de vrais échantillons, joués par le moteur SF2 comme n'importe quel SF2.
        val sampled = OrchestralSamples.file
        val pcm = drumPcm + sampled.sampleData
        val offset = drumPcm.size.toLong()
        val presets = programs.entries.associate { (number, layers) ->
            sampled.getPreset(0, number)?.let { preset ->
                return@associate "0:$number" to preset.copy(regions = preset.regions.map {
                    it.copy(sampleStart = it.sampleStart + offset, sampleEnd = it.sampleEnd + offset,
                        loopStart = it.loopStart + offset, loopEnd = it.loopEnd + offset)
                })
            }
            val regions = OrchestralRanges.zones(number).flatMap { zone ->
                layers.mapIndexed { index, program ->
                    val pan = if (layers.size == 1) 0f else if (index == 0) -1f else 1f
                    region(number, zone.keys, 48000, 0, 0, 60, program).copy(velRange = zone.velocities, pan = pan)
                }
            }
            "0:$number" to Sf2Preset(ProgramInfo.GM_PROGRAM_NAMES[number], 0, number, regions)
        } + ("128:0" to kit)
        file = Sf2File("Orchestral", pcm, presets, emptyList())
    }

    fun preparePrograms(used: Set<Int>) {
        for (number in used) if (number != 48) OrchestralStereo.load(number)
        for ((number, layers) in programs) for (layer in layers) {
            if (number in used) layer.profile() else layer.unload()
        }
    }

    /** Prépare en RAM les attaques récurrentes. Les notes rares, modulées ou longues restent synthétisées. */
    fun prepareAttacks(notes: Map<Int, Int>, rate: Int) {
        data class Target(val spectrum: OrchestralBank.Spectrum, val key: Int, val right: Boolean)
        val hits = HashMap<Target, Int>()
        fun count(spectrum: OrchestralBank.Spectrum?, key: Int, right: Boolean, times: Int) {
            if (spectrum == null || spectrum.tones == 0 || spectrum.movingRatios != null) return
            val target = Target(spectrum, key, right)
            hits[target] = (hits[target] ?: 0) + times
        }
        for ((id, times) in notes) {
            val program = id ushr 14
            val key = id ushr 7 and 127
            val vel = id and 127
            if (!OrchestralRanges.contains(program, key, vel)) continue
            for (layer in programs.getValue(program)) {
                val profile = layer.profile() ?: continue
                if (key in profile.firstKey..profile.lastKey) count(profile.spectrum(key, vel), key, layer.isRight, times)
            }
            if (program != 48) count(OrchestralStereo.load(program)?.at(key, vel)?.difference, key, true, times)
        }
        var floatsLeft = 6 * 1024 * 1024 // Au plus 24 Mio, points de reprise compris.
        val selected = HashMap<Target, Int>()
        for ((target, times) in hits.entries.sortedByDescending { it.value * it.key.spectrum.tones }) {
            val frames = if (times >= 8) rate else rate / 2
            val cost = frames + (frames / OrchestralAttack.STRIDE + 1) * target.spectrum.tones
            if (times < 2 || cost > floatsLeft) continue
            floatsLeft -= cost
            selected[target] = frames
        }
        val keep = selected.keys.mapTo(mutableSetOf()) { it.spectrum }
        for (spectrum in prepared) if (spectrum !in keep) spectrum.attack = null
        prepared.clear()
        prepared.addAll(keep)
        val missing = selected.entries.filter { (target, frames) ->
            val cache = target.spectrum.attack
            cache == null || cache.rate != rate || cache.samples.size != frames
        }.sortedByDescending { (target, frames) -> frames * target.spectrum.tones }
        fun build(index: Int) {
            val (target, frames) = missing[index]
            target.spectrum.attack = OrchestralAttack(rate, target.key, target.right, target.spectrum, frames)
        }
        if (missing.size < 4 || Runtime.getRuntime().availableProcessors() < 2) {
            for (i in missing.indices) build(i)
        } else {
            val other = FutureTask { for (i in 1 until missing.size step 2) build(i) }
            Thread(other, "Orchestral-Preparation").start()
            try {
                for (i in missing.indices step 2) build(i)
            } finally {
                other.get()
            }
        }
    }
}
