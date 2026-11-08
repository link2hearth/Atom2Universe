package com.Atom2Universe.app.studio.data

import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * La bibliothèque des morceaux du Studio : un fichier JSON par morceau dans [dir] (le nom, les dates, et le projet). Rien
 * d'Android ici : testable sur le PC. Un fichier abîmé est simplement ignoré.
 */
class StudioLibrary(private val dir: File) {

    class Entry(val id: String, val name: String, val created: Long, val modified: Long, val project: StudioProject)

    init {
        dir.mkdirs()
    }

    private fun file(id: String) = File(dir, "$id.json")

    /** Tous les morceaux, le plus récemment modifié d'abord. */
    fun list(): List<Entry> =
        (dir.listFiles { f -> f.isFile && f.extension == "json" } ?: emptyArray())
            .mapNotNull { read(it) }
            .sortedByDescending { it.modified }

    fun load(id: String): Entry? = file(id).takeIf { it.isFile }?.let { read(it) }

    /** Crée un morceau et rend son entrée. */
    fun create(name: String, project: StudioProject, now: Long = System.currentTimeMillis()): Entry {
        val id = "p" + UUID.randomUUID().toString().replace("-", "").take(12)
        write(id, name, now, now, project)
        return Entry(id, name, now, now, project)
    }

    /** Enregistre le projet d'un morceau (le nom et la date de création restent) ; rend faux si le morceau n'existe plus. */
    fun save(id: String, project: StudioProject, now: Long = System.currentTimeMillis()): Boolean {
        val old = load(id) ?: return false
        write(id, old.name, old.created, now, project)
        return true
    }

    fun rename(id: String, name: String) {
        val old = load(id) ?: return
        write(id, name, old.created, old.modified, old.project)
    }

    /** Copie un morceau sous le nom [name] et rend la copie. */
    fun duplicate(id: String, name: String, now: Long = System.currentTimeMillis()): Entry? {
        val old = load(id) ?: return null
        return create(name, old.project, now)
    }

    fun delete(id: String) {
        file(id).delete()
    }

    private fun read(f: File): Entry? = try {
        val o = JSONObject(f.readText())
        val project = StudioProjectCodec.decode(o.getString("project"))
        if (project == null) null
        else Entry(f.nameWithoutExtension, o.getString("name"), o.getLong("created"), o.getLong("modified"), project)
    } catch (_: Exception) {
        null
    }

    /** Écrit dans un fichier temporaire puis le renomme : une coupure en route ne laisse jamais un fichier à moitié écrit. */
    private fun write(id: String, name: String, created: Long, modified: Long, project: StudioProject) {
        val text = JSONObject()
            .put("name", name)
            .put("created", created)
            .put("modified", modified)
            .put("project", StudioProjectCodec.encode(project))
            .toString()
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file(id))) {
            file(id).writeText(text)
            tmp.delete()
        }
    }
}
