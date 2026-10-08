package com.Atom2Universe.app.notes.export

import android.content.Context
import android.net.Uri
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.*
import com.Atom2Universe.app.notes.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class NotesBackupManager(
    private val context: Context,
    private val repository: NotesRepository
) {

    enum class ImportMode { MERGE, REPLACE }

    /** Le résultat, déjà rédigé pour l'écran. */
    sealed class BackupResult {
        abstract val message: String
        data class Success(override val message: String) : BackupResult()
        data class Error(override val message: String, val cause: Throwable? = null) : BackupResult()
    }

    suspend fun exportFullBackup(outputUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val notes = repository.getAllNotesForBackup()
            val noteTags = repository.getAllNoteTagsForBackup()
            val groups = repository.getAllGroupsForBackup()
            val tags = repository.getAllTagsForBackup()
            val categories = repository.getAllCategoriesForBackup()

            context.contentResolver.openOutputStream(outputUri)?.use { os ->
                ZipOutputStream(os.buffered()).use { zip ->
                    // manifest.json
                    val manifest = JSONObject().apply {
                        put("app", "A2U Notes")
                        put("version", 1)
                        put("exportDate", System.currentTimeMillis())
                        put("noteCount", notes.size)
                    }
                    writeZipEntry(zip, "manifest.json", manifest.toString(2))

                    // tag_categories.json
                    val categoriesJson = JSONArray(categories.map { it.toJson() })
                    writeZipEntry(zip, "tag_categories.json", categoriesJson.toString(2))

                    // tags.json
                    val tagsJson = JSONArray(tags.map { it.toJson() })
                    writeZipEntry(zip, "tags.json", tagsJson.toString(2))

                    // groups.json
                    val groupsJson = JSONArray(groups.map { it.toJson() })
                    writeZipEntry(zip, "groups.json", groupsJson.toString(2))

                    // notes/XXX_title.md
                    val noteTagMap = noteTags.groupBy { it.noteId }.mapValues { it.value.map { nt -> nt.tagId } }
                    val tagMap = tags.associateBy { it.id }
                    val groupMap = groups.associateBy { it.id }

                    notes.forEach { note ->
                        val noteTags2 = (noteTagMap[note.id] ?: emptyList()).mapNotNull { tagMap[it] }
                        val group = note.groupId?.let { groupMap[it] }
                        val content = NoteExportManager.generateExportContent(note, noteTags2, group)
                        val safeTitle = note.title.take(40).replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        writeZipEntry(zip, "notes/${note.id}_$safeTitle.md", content)
                    }
                }
            }
            BackupResult.Success(context.resources.getQuantityString(R.plurals.notes_backup_exported, notes.size, notes.size))
        } catch (e: Exception) {
            BackupResult.Error(context.getString(R.string.notes_backup_failed, e.message ?: ""), e)
        }
    }

    suspend fun importFullBackup(inputUri: Uri, mode: ImportMode): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val categoriesFromFile = mutableListOf<TagCategory>()
                val tagsFromFile = mutableListOf<Tag>()
                val groupsFromFile = mutableListOf<NoteGroup>()
                val notesFromFile = mutableListOf<NoteExportManager.ImportedNoteData>()

                context.contentResolver.openInputStream(inputUri)?.use { ins ->
                    ZipInputStream(ins.buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val text = zip.bufferedReader().readText()
                            when {
                                entry.name == "tag_categories.json" ->
                                    categoriesFromFile.addAll(parseCategoriesJson(text))
                                entry.name == "tags.json" ->
                                    tagsFromFile.addAll(parseTagsJson(text))
                                entry.name == "groups.json" ->
                                    groupsFromFile.addAll(parseGroupsJson(text))
                                entry.name.startsWith("notes/") && entry.name.endsWith(".md") ->
                                    notesFromFile.add(NoteExportManager.parseImportContent(text))
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }

                repository.importBackup(
                    categoriesFromFile, tagsFromFile, groupsFromFile, notesFromFile,
                    replace = mode == ImportMode.REPLACE,
                )

                BackupResult.Success(context.resources.getQuantityString(R.plurals.notes_backup_imported, notesFromFile.size, notesFromFile.size))
            } catch (e: Exception) {
                BackupResult.Error(context.getString(R.string.notes_backup_failed, e.message ?: ""), e)
            }
        }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun parseCategoriesJson(json: String): List<TagCategory> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            TagCategory(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                sortOrder = obj.optInt("sortOrder", i),
                dateCreated = obj.optLong("dateCreated", System.currentTimeMillis())
            )
        }
    }

    private fun parseTagsJson(json: String): List<Tag> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Tag(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                colorHex = obj.optString("colorHex").ifBlank { null },
                sortOrder = obj.optInt("sortOrder", i),
                textColorMode = obj.optString("textColorMode", "auto"),
                categoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.getLong("categoryId") else null,
                dateCreated = obj.optLong("dateCreated", System.currentTimeMillis())
            )
        }
    }

    private fun parseGroupsJson(json: String): List<NoteGroup> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            NoteGroup(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                description = obj.optString("description", ""),
                colorHex = obj.optString("colorHex").ifBlank { null },
                textColorMode = obj.optString("textColorMode", "auto"),
                iconName = obj.optString("iconName").ifBlank { null },
                position = obj.optInt("position", i),
                dateCreated = obj.optLong("dateCreated", System.currentTimeMillis()),
                dateModified = obj.optLong("dateModified", System.currentTimeMillis())
            )
        }
    }

    private fun TagCategory.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("sortOrder", sortOrder); put("dateCreated", dateCreated)
    }

    private fun Tag.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("colorHex", colorHex ?: ""); put("sortOrder", sortOrder)
        put("textColorMode", textColorMode); put("categoryId", categoryId); put("dateCreated", dateCreated)
    }

    private fun NoteGroup.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("description", description); put("colorHex", colorHex ?: "")
        put("textColorMode", textColorMode); put("iconName", iconName ?: ""); put("position", position)
        put("dateCreated", dateCreated); put("dateModified", dateModified)
    }
}
