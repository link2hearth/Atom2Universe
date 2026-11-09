package com.Atom2Universe.app.studio.data

import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * La bibliothèque des morceaux du Studio : un fichier JSON par morceau dans [dir] (le nom, les dates, et le morceau de départ), plus un
 * journal `<id>.journal` des actions faites depuis (voir [StudioHistory]). Le morceau tel qu'on l'a laissé est le morceau de départ
 * avec le journal rejoué par-dessus ; le journal garde aussi l'historique d'annulation. Rien d'Android ici : testable sur le PC.
 * Un fichier abîmé est simplement ignoré.
 *
 * Le fichier du morceau porte un numéro de version (`rev`) que reprend la première ligne du journal : si les deux ne vont pas
 * ensemble (coupure pendant une réécriture), le journal est ignoré et le morceau de départ, déjà à jour, suffit.
 */
class StudioLibrary(private val dir: File) {

    /** Un morceau ; [project] est le morceau tel qu'on l'a laissé, [history] ce qu'on peut annuler et rétablir. */
    class Entry(
        val id: String,
        val name: String,
        val created: Long,
        val modified: Long,
        val project: StudioProject,
        val history: StudioHistory.Replayed? = null,
    )

    init {
        dir.mkdirs()
    }

    private fun file(id: String) = File(dir, "$id.json")

    private fun journalFile(id: String) = File(dir, "$id.journal")

    /** Tous les morceaux, le plus récemment modifié d'abord. */
    fun list(): List<Entry> =
        (dir.listFiles { f -> f.isFile && f.extension == "json" } ?: emptyArray())
            .mapNotNull { read(it) }
            .sortedByDescending { it.modified }

    fun load(id: String): Entry? = file(id).takeIf { it.isFile }?.let { read(it) }

    /** Crée un morceau et rend son entrée. */
    fun create(name: String, project: StudioProject, now: Long = System.currentTimeMillis()): Entry {
        val id = "p" + UUID.randomUUID().toString().replace("-", "").take(12)
        write(id, name, now, now, project, 0)
        return Entry(id, name, now, now, project)
    }

    /**
     * Remplace le morceau d'un morceau par [project] et efface son historique (le journal d'un morceau ouvert sert normalement à ça
     * et non cette fonction) ; rend faux si le morceau n'existe plus.
     */
    fun save(id: String, project: StudioProject, now: Long = System.currentTimeMillis()): Boolean {
        val old = readRaw(id) ?: return false
        write(id, old.getString("name"), old.getLong("created"), now, project, old.optInt("rev", 0) + 1)
        journalFile(id).delete()
        return true
    }

    fun rename(id: String, name: String) {
        val o = readRaw(id) ?: return
        o.put("name", name)
        writeText(id, o.toString())
    }

    /** Copie un morceau sous le nom [name] et rend la copie (sans l'historique). */
    fun duplicate(id: String, name: String, now: Long = System.currentTimeMillis()): Entry? {
        val old = load(id) ?: return null
        return create(name, old.project, now)
    }

    fun delete(id: String) {
        file(id).delete()
        journalFile(id).delete()
    }

    /** Le journal d'un morceau ouvert : on y ajoute les actions, il se réécrit quand il devient long. */
    fun journal(id: String): StudioPersist = Journal(id)

    private fun readRaw(id: String): JSONObject? = try {
        file(id).takeIf { it.isFile }?.let { JSONObject(it.readText()) }
    } catch (_: Exception) {
        null
    }

    /** Les lignes du journal si elles vont avec la version [rev] du morceau, sinon rien. */
    private fun journalLines(id: String, rev: Int): List<String> {
        val f = journalFile(id)
        if (!f.isFile) return emptyList()
        val lines = try {
            f.readLines()
        } catch (_: Exception) {
            return emptyList()
        }
        val header = lines.firstOrNull()?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyList()
        if (header.optString("t") != "h" || header.optInt("rev", -1) != rev) return emptyList()
        return lines.drop(1).filter { it.isNotBlank() }
    }

    private fun read(f: File): Entry? = try {
        val o = JSONObject(f.readText())
        val id = f.nameWithoutExtension
        val base = StudioProjectCodec.decode(o.getString("project"))
        if (base == null) {
            null
        } else {
            val replayed = StudioHistory.replay(base, journalLines(id, o.optInt("rev", 0)))
            val journalTime = journalFile(id).takeIf { it.isFile }?.lastModified() ?: 0L
            Entry(id, o.getString("name"), o.getLong("created"), maxOf(o.getLong("modified"), journalTime), replayed.project, replayed)
        }
    } catch (_: Exception) {
        null
    }

    /** Écrit dans un fichier temporaire puis le renomme : une coupure en route ne laisse jamais un fichier à moitié écrit. */
    private fun write(id: String, name: String, created: Long, modified: Long, project: StudioProject, rev: Int) {
        writeText(
            id,
            JSONObject()
                .put("name", name)
                .put("created", created)
                .put("modified", modified)
                .put("rev", rev)
                .put("project", StudioProjectCodec.encode(project))
                .toString(),
        )
    }

    private fun writeText(id: String, text: String) {
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file(id))) {
            file(id).writeText(text)
            tmp.delete()
        }
    }

    private inner class Journal(private val id: String) : StudioPersist {

        private var rev = readRaw(id)?.optInt("rev", 0) ?: 0

        init {
            repair()
        }

        private fun header(r: Int) = JSONObject().put("t", "h").put("rev", r).toString()

        /** Un journal coupé en pleine écriture, ou qui n'est pas de la bonne version : on garde seulement ce qui est lisible. */
        private fun repair() {
            val f = journalFile(id)
            if (!f.isFile) return
            val base = readRaw(id)?.getString("project")?.let { StudioProjectCodec.decode(it) } ?: return
            val lines = journalLines(id, rev)
            val good = StudioHistory.replay(base, lines).valid
            if (lines.size == good && f.readLines().firstOrNull()?.let { runCatching { JSONObject(it).optInt("rev", -1) }.getOrNull() } == rev) return
            rewrite(lines.take(good))
        }

        private fun rewrite(lines: List<String>) {
            val tmp = File(dir, "$id.journal.tmp")
            tmp.writeText((listOf(header(rev)) + lines).joinToString("\n", postfix = "\n"))
            if (!tmp.renameTo(journalFile(id))) {
                journalFile(id).writeText(tmp.readText())
                tmp.delete()
            }
        }

        override fun append(lines: List<String>) {
            if (lines.isEmpty()) return
            val f = journalFile(id)
            if (!f.isFile || f.length() == 0L) {
                rewrite(lines)
                return
            }
            f.appendText(lines.joinToString("\n", postfix = "\n"))
        }

        override fun compact(base: StudioProject, lines: List<String>) {
            val old = readRaw(id) ?: return
            val next = rev + 1
            // D'abord le nouveau journal (sous un nom provisoire), puis le morceau de départ, puis le journal prend sa place :
            // une coupure entre deux étapes laisse soit l'ancien couple, soit le nouveau morceau avec un vieux journal qu'on ignore.
            val tmp = File(dir, "$id.journal.tmp")
            tmp.writeText((listOf(header(next)) + lines).joinToString("\n", postfix = "\n"))
            write(id, old.getString("name"), old.getLong("created"), System.currentTimeMillis(), base, next)
            rev = next
            if (!tmp.renameTo(journalFile(id))) {
                journalFile(id).writeText(tmp.readText())
                tmp.delete()
            }
        }
    }
}
