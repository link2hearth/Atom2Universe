package com.Atom2Universe.app.notes.export

import com.Atom2Universe.app.notes.data.Note
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.Tag

/**
 * Une note en fichier `.md` : un en-tête (tags, groupe, favori, couleur, dates), puis `# Titre`,
 * puis le texte tel quel. Le même format sert à la sauvegarde complète et au partage d'une note.
 */
object NoteExportManager {

    fun generateExportContent(note: Note, tags: List<Tag>, group: NoteGroup?): String {
        val sb = StringBuilder()
        sb.appendLine("---")
        if (tags.isNotEmpty()) {
            sb.appendLine("tags: [${tags.joinToString(", ") { "\"${it.name}\"" }}]")
        }
        group?.let { sb.appendLine("group: \"${it.name}\"") }
        if (note.isFavorite) sb.appendLine("favorite: true")
        if (note.isPinned) sb.appendLine("pinned: true")
        note.colorHex?.let { sb.appendLine("color: \"$it\"") }
        sb.appendLine("created: ${note.dateCreated}")
        sb.appendLine("modified: ${note.dateModified}")
        sb.appendLine("---")
        sb.appendLine()
        if (note.title.isNotBlank()) {
            sb.appendLine("# ${note.title}")
            sb.appendLine()
        }
        sb.append(note.content)
        return sb.toString()
    }

    /** Le texte d'une note seule, à partager : titre puis contenu, sans en-tête. */
    fun shareText(note: Note): String =
        if (note.title.isBlank()) note.content else "# ${note.title}\n\n${note.content}"

    fun parseImportContent(content: String): ImportedNoteData {
        val lines = content.lines()
        var inFrontMatter = false
        var frontMatterDone = false
        val frontMatterLines = mutableListOf<String>()
        val contentLines = mutableListOf<String>()

        for (line in lines) {
            when {
                !inFrontMatter && !frontMatterDone && contentLines.isEmpty() && line.trim() == "---" -> inFrontMatter = true
                inFrontMatter && line.trim() == "---" -> {
                    inFrontMatter = false
                    frontMatterDone = true
                }
                inFrontMatter -> frontMatterLines.add(line)
                else -> contentLines.add(line)
            }
        }

        val frontMatter = parseFrontMatter(frontMatterLines)
        val body = contentLines.dropWhile { it.isBlank() }

        val h1Match = Regex("^# (.+)$").find(body.firstOrNull() ?: "")
        val title: String
        val noteContent: String
        if (h1Match != null) {
            title = h1Match.groupValues[1]
            noteContent = body.drop(1).dropWhile { it.isBlank() }.joinToString("\n")
        } else {
            title = ""
            noteContent = body.joinToString("\n")
        }

        return ImportedNoteData(
            title = title,
            content = noteContent,
            tagNames = (frontMatter["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            groupName = frontMatter["group"] as? String,
            isFavorite = frontMatter["favorite"] == true,
            isPinned = frontMatter["pinned"] == true,
            colorHex = frontMatter["color"] as? String,
            created = (frontMatter["created"] as? String)?.toLongOrNull(),
            modified = (frontMatter["modified"] as? String)?.toLongOrNull(),
        )
    }

    private fun parseFrontMatter(lines: List<String>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val key = line.substring(0, colon).trim()
            val rawValue = line.substring(colon + 1).trim()
            when {
                rawValue.startsWith("[") -> {
                    val items = rawValue.trim('[', ']').split(",")
                        .map { it.trim().trim('"') }
                        .filter { it.isNotEmpty() }
                    result[key] = items
                }
                rawValue == "true" -> result[key] = true
                rawValue == "false" -> result[key] = false
                rawValue.startsWith("\"") -> result[key] = rawValue.trim('"')
                else -> result[key] = rawValue
            }
        }
        return result
    }

    data class ImportedNoteData(
        val title: String,
        val content: String,
        val tagNames: List<String>,
        val groupName: String?,
        val isFavorite: Boolean,
        val isPinned: Boolean,
        val colorHex: String?,
        val created: Long? = null,
        val modified: Long? = null,
    )
}
