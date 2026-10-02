package com.Atom2Universe.app.audioeditor.engine

import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.dsp.RenderResult
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.io.SourceImporter
import com.Atom2Universe.app.audioeditor.io.WavPcmStream
import java.io.File

/**
 * Fait d'un enregistrement un clip du projet : le WAV de la capture passe par le même chemin d'import
 * que n'importe quel fichier (rééchantillonnage vers la fréquence du projet, crêtes, source 16 bits
 * rangée dans `sources/`), puis un clip est posé à la position voulue.
 */
object RecordingPlacement {

    /**
     * @param trackId piste qui reçoit le clip ; `null` en crée une nouvelle nommée [trackName]
     * @param at trame où l'enregistrement a commencé (position du curseur ou de la lecture au départ)
     * @param latencyFrames retard de la chaîne micro (le micro entend avec un peu de retard sur ce qu'on joue) :
     *   le clip est avancé d'autant. 0 par défaut : pas de compensation automatique.
     * @return le nouveau projet et la source créée ; le fichier [wav] est effacé une fois importé
     */
    fun place(
        project: Project,
        projectDir: File,
        wav: File,
        trackId: Int?,
        trackName: String,
        clipName: String,
        at: Long,
        ctx: RunContext,
        latencyFrames: Long = 0,
        newId: () -> String = SourceImporter::newSourceId,
    ): RenderResult {
        val src = WavPcmStream(wav).use { SourceImporter.import(it, projectDir, project.sampleRate, ctx, newId) }
        wav.delete()
        var p = project.addSource(src)
        val target = trackId ?: p.addTrack(trackName).let { (np, tid) -> p = np; tid }
        val lat = latencyFrames.coerceAtLeast(0)
        val start = maxOf(0L, at - lat)
        // Si le retard dépasse la position de départ, on rogne le début du clip plutôt que de le placer avant zéro.
        val srcStart = (lat - at).coerceIn(0, src.frames - 1)
        p = p.addClip(target, src.id, start, srcStart, src.frames - srcStart, clipName).first
        return RenderResult(p, listOf(src))
    }
}
