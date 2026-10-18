package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException

/**
 * Reprend le projet de l'ancien éditeur (une liste de fichiers, `audio_editor/audio_editor_project.json`)
 * pour en faire un vrai projet : une piste par fichier, chacun posé à 0.
 *
 * On migre au lieu de supprimer sans prévenir : l'utilisateur retrouve son travail dans la galerie.
 * Seules les pistes elles-mêmes sont reprises, l'historique d'annulation de l'ancien éditeur (des
 * copies de fichiers entiers) n'a plus de sens et part avec le reste.
 */
object LegacyMigration {

    const val PROJECT_FILE = "audio_editor_project.json"
    const val MIGRATED_SUFFIX = ".migrated"

    class LegacyTrack(val name: String, val file: File)

    /** Le dossier de l'ancien éditeur dans `filesDir`. */
    fun legacyDir(filesDir: File) = File(filesDir, "audio_editor")

    /** Les pistes de l'ancien projet dont le fichier existe encore. */
    fun parse(json: String): List<LegacyTrack> {
        val arr = JSONObject(json).optJSONArray("tracks") ?: return emptyList()
        val out = ArrayList<LegacyTrack>()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val path = t.optString("filePath", "")
            if (path.isEmpty()) continue
            val f = File(path)
            if (!f.isFile) continue
            out += LegacyTrack(t.optString("name", "").ifBlank { f.nameWithoutExtension }, f)
        }
        return out
    }

    /** Les pistes à migrer, ou une liste vide s'il n'y a pas d'ancien projet (ou s'il est vide / illisible). */
    fun pending(filesDir: File): List<LegacyTrack> {
        val pf = File(legacyDir(filesDir), PROJECT_FILE)
        if (!pf.isFile || pf.length() > MAX_JSON) return emptyList()
        return runCatching { parse(pf.readText()) }.getOrDefault(emptyList())
    }

    /**
     * Crée le projet [name] dans [store] avec les pistes de l'ancien projet, puis range l'ancien :
     * le JSON devient `….migrated`, l'historique et les fichiers de piste repris sont effacés.
     *
     * [importer] lit un fichier et écrit sa source dans le dossier du projet (voir [AudioImporter.importFile]) ;
     * une piste qui ne s'importe pas est sautée. Rend l'identifiant du projet créé, ou `null` s'il n'y
     * avait rien à migrer ou que rien n'a pu l'être (l'ancien projet est alors laissé intact).
     */
    fun migrate(
        filesDir: File,
        store: ProjectStore,
        name: String,
        sampleRate: Int,
        ctx: RunContext,
        importer: (file: File, projectDir: File, ctx: RunContext) -> ImportedAudio,
    ): String? {
        val tracks = pending(filesDir)
        if (tracks.isEmpty()) return null

        val created = store.create(name, sampleRate)
        val dir = store.dir(created.id)
        var project = created.project
        val imported = ArrayList<File>()
        try {
            for ((i, t) in tracks.withIndex()) {
                ctx.checkCancelled()
                val sub = RunContext(
                    ctx.sampleRate,
                    progress = { f -> ctx.report((i + f) / tracks.size) },
                    isCancelled = { runCatching { ctx.checkCancelled() }.isFailure },
                )
                val r: ImportedAudio = try {
                    importer(t.file, dir, sub)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    continue
                }
                val src: Source = r.source
                val (p1, tid) = project.addSource(src).addTrack(t.name)
                project = p1.addClip(tid, src.id, 0, 0, src.frames, t.name).first
                imported += t.file
            }
        } catch (e: Throwable) {
            store.delete(created.id)
            throw e
        }

        if (imported.isEmpty()) {
            store.delete(created.id)
            return null
        }
        store.save(created.id, project, created.meta)
        store.updateThumb(created.id, project)

        // Tout est écrit : on range l'ancien.
        val legacy = legacyDir(filesDir)
        File(legacy, PROJECT_FILE).renameTo(File(legacy, PROJECT_FILE + MIGRATED_SUFFIX))
        File(legacy, "history").deleteRecursively()
        val tracksDir = File(legacy, "tracks").canonicalFile
        for (f in imported) {
            // Seuls les fichiers que l'ancien éditeur avait copiés chez lui sont effacés, jamais un chemin venu d'ailleurs.
            if (f.canonicalFile.parentFile == tracksDir) f.delete()
        }
        return created.id
    }

    private const val MAX_JSON = 10L * 1024 * 1024
}
