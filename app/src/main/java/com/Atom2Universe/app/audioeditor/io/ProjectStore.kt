package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.Thumbnail
import com.Atom2Universe.app.audioeditor.core.withoutUnusedSources
import com.Atom2Universe.app.pixelart.io.PngWriter
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Le fichier de crêtes d'une source : `sources/<id>.peaks`, à côté de `sources/<id>.wav`. */
fun Source.peaksFile(projectDir: File) = File(projectDir, file.removeSuffix(".wav") + ".peaks")

/**
 * Les projets audio vivent chacun dans un dossier :
 *
 *     <racine>/<id>/manifest.json          le projet (pistes, clips, repères…) et son état d'écran
 *                   sources/<idSource>.wav     l'audio : 16 bits (importé, enregistré) ou flottant 32 bits (rendus d'effets)
 *                   sources/<idSource>.peaks   les crêtes pour dessiner la forme d'onde
 *                   thumb.png                  la vignette de la galerie
 *
 * Le nom d'un fichier de source est toujours `<id de la source>.<extension>` : c'est ce qui permet au
 * ramasse-miettes ([collectGarbage]) de savoir quoi garder sans relire les manifestes.
 *
 * Toute écriture passe par un fichier temporaire renommé ensuite : une coupure en plein milieu laisse
 * l'ancienne version intacte. Rien ici ne dépend d'Android, tout se teste en JVM.
 */
class ProjectStore(val root: File, private val now: () -> Long = System::currentTimeMillis) {

    init {
        root.mkdirs()
    }

    fun dir(id: String): File {
        require(SAFE_ID.matches(id)) { "identifiant de projet invalide" }
        return File(root, id)
    }

    fun sourcesDir(id: String) = File(dir(id), "sources")
    fun thumbFile(id: String) = File(dir(id), "thumb.png")
    private fun manifestFile(id: String) = File(dir(id), "manifest.json")

    // ---- Catalogue -----------------------------------------------------------------------

    fun list(): List<ProjectSummary> {
        val out = ArrayList<ProjectSummary>()
        for (d in root.listFiles().orEmpty()) {
            // Les dossiers « .incoming_… » sont des fichiers en cours de déballage, pas des projets.
            if (!d.isDirectory || d.name.startsWith(".")) continue
            val mf = File(d, "manifest.json")
            if (!mf.isFile) continue
            try {
                val j = JSONObject(mf.readText())
                out.add(
                    ProjectSummary(
                        id = d.name,
                        name = j.optString("name", ""),
                        sampleRate = j.optInt("rate", 44100),
                        trackCount = j.optInt("trackCount", 0),
                        frames = j.optLong("length", 0),
                        created = j.optLong("created"),
                        modified = j.optLong("modified"),
                    ),
                )
            } catch (e: Exception) {
                // Un manifeste abîmé n'empêche pas d'afficher les autres projets.
            }
        }
        return out.sortedByDescending { it.modified }
    }

    fun exists(id: String) = manifestFile(id).isFile

    // ---- Création / chargement -----------------------------------------------------------

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /** Crée un projet vide et l'écrit tout de suite (il apparaît dans la galerie même s'il n'est jamais modifié). */
    fun create(name: String, sampleRate: Int = 44100): LoadedProject {
        val id = newId()
        val t = now()
        val project = Project(sampleRate = sampleRate)
        val meta = ProjectMeta(name, t, t)
        sourcesDir(id).mkdirs()
        atomicWrite(manifestFile(id)) { it.write(ManifestCodec.encode(project, meta).toString().toByteArray(Charsets.UTF_8)) }
        return LoadedProject(id, project, meta)
    }

    /**
     * Charge un projet, ou `null` s'il manque ou est illisible. Avec [collectGarbage], efface aussi les
     * sources que le projet enregistré n'utilise pas : après un plantage, l'historique est perdu et
     * celles d'un rendu jamais enregistré ne serviront plus.
     */
    fun open(id: String, collectGarbage: Boolean = false): LoadedProject? {
        val mf = manifestFile(id)
        if (!mf.isFile) return null
        return try {
            val (project, meta) = ManifestCodec.decode(JSONObject(mf.readText()))
            if (collectGarbage) collectGarbage(id, project.usedSourceIds())
            LoadedProject(id, project, meta)
        } catch (e: Exception) {
            null
        }
    }

    // ---- Enregistrement ------------------------------------------------------------------

    /**
     * Écrit le manifeste. [touch] à faux garde la date de modification : à utiliser quand seul l'état
     * de l'écran a changé (la date sert au tri de la galerie, pas à signaler qu'on a regardé).
     * Rend les métadonnées telles qu'écrites.
     */
    fun save(id: String, project: Project, meta: ProjectMeta, touch: Boolean = true): ProjectMeta {
        val m = if (touch) meta.copy(modified = now()) else meta
        atomicWrite(manifestFile(id)) { it.write(ManifestCodec.encode(project, m).toString().toByteArray(Charsets.UTF_8)) }
        return m
    }

    /**
     * Fermeture d'un projet : oublie les sources inutilisées, enregistre, efface leurs fichiers et
     * refait la vignette. À n'appeler que quand plus rien ne peut annuler vers ces sources (la session
     * d'édition est finie). Rend le projet nettoyé.
     */
    fun finish(id: String, project: Project, meta: ProjectMeta, touch: Boolean = true): Project {
        val clean = project.withoutUnusedSources()
        save(id, clean, meta, touch)
        collectGarbage(id, clean.sources.keys)
        updateThumb(id, clean)
        return clean
    }

    /** Efface de `sources/` tout fichier dont l'identifiant n'est pas dans [keep] (et les `.tmp` d'écritures coupées). Rend les octets libérés. */
    fun collectGarbage(id: String, keep: Set<String>): Long {
        var freed = 0L
        for (f in sourcesDir(id).listFiles().orEmpty()) {
            if (!f.isFile) continue
            val keepIt = f.name.substringBefore('.') in keep && (f.name.endsWith(".wav") || f.name.endsWith(".peaks"))
            if (keepIt) continue
            freed += f.length()
            f.delete()
        }
        return freed
    }

    /** Refait `thumb.png` d'après le mixage. Un échec (source illisible…) laisse l'ancienne vignette. */
    fun updateThumb(id: String, project: Project) {
        runCatching {
            FileSampleProvider(dir(id)).use { provider ->
                val t = Thumbnail.render(project, provider)
                atomicWrite(thumbFile(id)) { PngWriter.write(it, t.pixels, t.width, t.height) }
            }
        }
    }

    // ---- Gestion -------------------------------------------------------------------------

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    fun rename(id: String, name: String) {
        val mf = manifestFile(id)
        if (!mf.isFile) return
        val j = JSONObject(mf.readText())
        j.put("name", name)
        j.put("modified", now())
        atomicWrite(mf) { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
    }

    /** Copie complète (audio compris) sous un nouvel identifiant ; rend celui-ci, ou `null` si [id] n'existe pas. */
    fun duplicate(id: String, newName: String): String? {
        if (!exists(id)) return null
        val newId = newId()
        val target = dir(newId)
        dir(id).copyRecursively(target, overwrite = false)
        target.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.forEach { it.delete() }
        val mf = manifestFile(newId)
        val j = JSONObject(mf.readText())
        j.put("name", newName)
        j.put("created", now())
        j.put("modified", now())
        atomicWrite(mf) { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
        return newId
    }

    fun nameOf(id: String): String? {
        val mf = manifestFile(id)
        if (!mf.isFile) return null
        return try { JSONObject(mf.readText()).optString("name", "") } catch (e: Exception) { null }
    }

    // ---- Fichier de projet (partage) -----------------------------------------------------

    fun exportZip(id: String, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
            z.setLevel(Deflater.BEST_SPEED)
            val base = dir(id)
            for (f in base.walkTopDown()) {
                if (!f.isFile) continue
                val rel = f.relativeTo(base).path.replace(File.separatorChar, '/')
                if (!allowedEntry(rel)) continue
                z.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    /** Ouvre un fichier de projet partagé ; rend l'identifiant du nouveau projet, ou `null` s'il est illisible (rien n'est alors laissé sur le disque). */
    fun importZip(input: InputStream, fallbackName: String): String? {
        val id = newId()
        val incoming = File(root, ".incoming_$id")
        if (!extractProject(input, incoming, fallbackName)) return null
        return if (incoming.renameTo(dir(id))) id else { incoming.deleteRecursively(); null }
    }

    private fun allowedEntry(name: String) =
        name == "manifest.json" || name == "thumb.png" || SAFE_ENTRY.matches(name)

    /**
     * Déballe [input] dans [target], le vérifie et efface [target] s'il est illisible. Seuls les noms
     * attendus sont extraits (pas de `..`, pas de sous-dossier) ; le manifeste doit se décoder et chaque
     * source utilisée doit exister, être un WAV valide et au moins aussi longue que le manifeste le dit.
     */
    private fun extractProject(input: InputStream, target: File, fallbackName: String): Boolean {
        try {
            target.deleteRecursively()
            target.mkdirs()
            ZipInputStream(BufferedInputStream(input)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory || !allowedEntry(e.name)) continue
                    val f = File(target, e.name)
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { z.copyTo(it) }
                }
            }
            val mf = File(target, "manifest.json")
            if (!mf.isFile) { target.deleteRecursively(); return false }
            val j = JSONObject(mf.readText())
            val (p, _) = ManifestCodec.decode(j)
            for (sid in p.usedSourceIds()) {
                val s = p.sources[sid] ?: run { target.deleteRecursively(); return false }
                val wav = File(target, s.file)
                if (!wav.isFile) { target.deleteRecursively(); return false }
                val info = WavFile.readInfo(wav)
                if (info.channels != s.channels || info.frames < s.frames) { target.deleteRecursively(); return false }
            }
            if (j.optString("name").isBlank()) j.put("name", fallbackName)
            j.put("modified", now())
            File(target, "sources").mkdirs()
            atomicWrite(mf) { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
            return true
        } catch (e: Exception) {
            target.deleteRecursively()
            return false
        }
    }

    private fun atomicWrite(target: File, block: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        BufferedOutputStream(FileOutputStream(tmp)).use { block(it) }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("Impossible d'écrire ${target.name}")
        }
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9_-]+")
        val SAFE_ENTRY = Regex("sources/[A-Za-z0-9_-]+\\.(wav|peaks)")
    }
}
