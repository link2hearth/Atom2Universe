package com.Atom2Universe.app.audioeditor.dsp

import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.PeakBuilder
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.SampleProvider
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.replaceRange
import com.Atom2Universe.app.audioeditor.io.PeakFiles
import com.Atom2Universe.app.audioeditor.io.WavWriter
import java.io.File
import java.util.UUID

/**
 * Lit la plage `[from, to)` d'**une** piste : ses clips mélangés (gain de clip, fondus, retourné)
 * mais ni son volume, ni son panoramique, ni son enveloppe, ni sourdine / solo — ce sont des réglages
 * de mixage, pas du contenu, et un effet ne doit pas les graver dans l'audio.
 *
 * Mono si tous les clips de la plage sont mono, stéréo dès que l'un des deux est stéréo.
 */
class TrackRangeReader(project: Project, trackId: Int, private val from: Long, private val to: Long, provider: SampleProvider) : FrameReader {

    private val mixer = Mixer(provider)
    private val single: Project
    private var pos = from
    private var scratch = FloatArray(0)

    override val channels: Int
    override val frames: Long = maxOf(0L, to - from)

    init {
        val track = project.track(trackId)
        val clips = track?.clips.orEmpty().filter { it.end > from && it.start < to }
        channels = if (clips.any { (project.sources[it.sourceId]?.channels ?: 1) >= 2 }) 2 else 1
        single = project.copy(
            tracks = listOfNotNull(track?.copy(volume = 1f, pan = 0f, mute = false, solo = false, envelope = emptyList())),
            master = 1f,
        )
    }

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val m = minOf(n.toLong(), to - pos).toInt()
        if (m <= 0) return 0
        val left = dst[0]
        val right = if (channels >= 2 && dst.size >= 2) dst[1] else scratchOf(m)
        mixer.render(single, pos, m, left, right)
        pos += m
        return m
    }

    private fun scratchOf(n: Int): FloatArray {
        if (scratch.size < n) scratch = FloatArray(n)
        return scratch
    }
}

/** Le résultat d'un rendu : le nouveau projet et les sources créées (à effacer si on annule l'édition). */
class RenderResult(val project: Project, val newSources: List<Source>)

object RenderRange {

    /**
     * Applique [effect] à la plage `[from, to)` de chaque piste de [trackIds].
     *
     * Pour chaque piste : la plage est lue ([TrackRangeReader]), passée dans l'effet, écrite dans une
     * **nouvelle source** `sources/<id>.wav` en flottant 32 bits (une amplification qui dépasse 0 dB ne
     * perd rien, on peut normaliser ensuite), avec ses crêtes ; puis `replaceRange` remplace la plage par
     * un clip sur cette source. Rien d'ancien n'est modifié, donc annuler revient à l'ancien projet.
     *
     * Une annulation ou une erreur efface le fichier en cours d'écriture et relance l'exception : le
     * projet d'origine, lui, n'a pas bougé.
     */
    fun apply(
        project: Project,
        projectDir: File,
        provider: SampleProvider,
        trackIds: Collection<Int>,
        from: Long,
        to: Long,
        effect: Effect,
        ctx: RunContext,
        newId: () -> String = { "s" + UUID.randomUUID().toString().replace("-", "").take(12) },
    ): RenderResult {
        var p = project
        val created = ArrayList<Source>()
        val targets = trackIds.mapNotNull { id -> p.track(id)?.takeIf { !it.locked && it.clips.any { c -> c.end > from && c.start < to } } }
        for ((k, t) in targets.withIndex()) {
            val reader = TrackRangeReader(p, t.id, from, to, provider)
            val outCh = effect.outputChannels(reader.channels)
            val id = newId()
            val file = File(projectDir, "sources/$id.wav")
            file.parentFile?.mkdirs()
            val peaks = PeakBuilder(outCh)
            var frames = 0L
            try {
                WavWriter(file, outCh, p.sampleRate, float = true).use { w ->
                    val sink = object : FrameWriter {
                        override fun write(src: Array<FloatArray>, n: Int) { w.write(src, n); peaks.add(src, n) }
                    }
                    val sub = RunContext(
                        ctx.sampleRate,
                        progress = { f -> ctx.report((k + f) / targets.size) },
                        isCancelled = { runCatching { ctx.checkCancelled() }.isFailure },
                    )
                    effect.run({ TrackRangeReader(p, t.id, from, to, provider) }, sink, sub)
                    frames = w.frames
                }
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
            if (frames == 0L) { file.delete(); continue }
            PeakFiles.save(peaks.finish(), File(projectDir, "sources/$id.peaks"))
            val src = Source(id, "sources/$id.wav", outCh, frames, p.sampleRate, float = true)
            created.add(src)
            p = p.addSource(src).replaceRange(t.id, from, to, id, frames, ripple = effect.changesLength)
        }
        return RenderResult(p, created)
    }
}
