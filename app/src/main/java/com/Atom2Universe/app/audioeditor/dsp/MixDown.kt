package com.Atom2Universe.app.audioeditor.dsp

import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.SampleProvider
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.io.OfflineMix
import java.io.File
import java.util.UUID

/**
 * Mixe plusieurs pistes en une seule : leur mélange stéréo (avec volume, panoramique, enveloppe et fondus de chaque piste,
 * mais sans sourdine ni solo) s'écrit dans une **nouvelle source** flottante 32 bits, un seul clip la pose sur une piste
 * qui prend la place de la première des pistes mixées. Les anciennes pistes disparaissent du projet — mais pas leurs
 * fichiers : annuler les ramène.
 */
object MixDown {

    fun apply(
        project: Project,
        projectDir: File,
        provider: SampleProvider,
        trackIds: Collection<Int>,
        name: String,
        ctx: RunContext,
        newId: () -> String = { "s" + UUID.randomUUID().toString().replace("-", "").take(12) },
    ): RenderResult {
        val chosen = project.tracks.filter { it.id in trackIds && !it.locked && it.clips.isNotEmpty() }
        val end = chosen.maxOfOrNull { it.end } ?: 0L
        if (chosen.isEmpty() || end <= 0) return RenderResult(project, emptyList())

        val ids = chosen.map { it.id }.toSet()
        val sub = project.copy(tracks = chosen.map { it.copy(mute = false, solo = false) }, master = 1f)
        val id = newId()
        val file = File(projectDir, "sources/$id.wav")
        file.parentFile?.mkdirs()
        val mixed = OfflineMix.render(sub, provider, 0, end, file, float = true, mono = false, ctx = ctx)
        if (mixed.frames <= 0) { file.delete(); return RenderResult(project, emptyList()) }

        val source = Source(id, "sources/$id.wav", 2, mixed.frames, project.sampleRate, float = true)
        val at = project.tracks.indexOfFirst { it.id in ids }
        var p = project.addSource(source).let { it.copy(tracks = it.tracks.filter { t -> t.id !in ids }) }
        val (withTrack, trackId) = p.addTrack(name, at = at)
        p = withTrack.addClip(trackId, id, 0, 0, mixed.frames, name).first
        return RenderResult(p, listOf(source))
    }
}
