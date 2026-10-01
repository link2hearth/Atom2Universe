package com.Atom2Universe.app.notes.repository

import androidx.room.withTransaction
import com.Atom2Universe.app.notes.data.*
import com.Atom2Universe.app.notes.export.NoteExportManager
import com.Atom2Universe.app.notes.editor.MarkdownSyntax
import kotlinx.coroutines.flow.Flow

class NotesRepository(private val db: NotesDatabase) {

    private val noteDao = db.noteDao()
    private val noteGroupDao = db.noteGroupDao()
    private val tagDao = db.tagDao()
    private val tagCategoryDao = db.tagCategoryDao()

    // ─── Notes ───────────────────────────────────────────────────────────────

    fun getAllNotesWithTags(): Flow<List<NoteWithTags>> = noteDao.getAllNotesWithTags()

    fun getTrashedNotesWithTags(): Flow<List<NoteWithTags>> = noteDao.getTrashedNotesWithTags()

    suspend fun getNoteWithTagsById(noteId: Long): NoteWithTags? = noteDao.getNoteWithTagsById(noteId)

    suspend fun getNoteById(noteId: Long): Note? = noteDao.getNoteById(noteId)

    suspend fun getNoteByTitle(title: String): Note? = noteDao.getNoteByTitle(title)

    suspend fun getAllTitles(): List<NoteTitle> = noteDao.getAllTitles()

    /** Les notes qui citent celle-ci par `[[titre]]`. */
    suspend fun getBacklinks(title: String, noteId: Long): List<NoteWithTags> =
        if (title.isBlank()) emptyList() else noteDao.getNotesContaining("[[${title.trim()}]]", noteId)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    suspend fun deleteNoteForever(noteId: Long) = noteDao.deleteNoteById(noteId)

    suspend fun setPinned(noteId: Long, pinned: Boolean) = noteDao.updateNotePinned(noteId, pinned)

    suspend fun setFavorite(noteId: Long, favorite: Boolean) = noteDao.updateNoteFavorite(noteId, favorite)

    suspend fun updateNoteGroup(noteId: Long, groupId: Long?) = noteDao.updateNoteGroup(noteId, groupId)

    suspend fun updateNoteColor(noteId: Long, colorHex: String?) = noteDao.updateNoteColor(noteId, colorHex)

    /** Une copie complète (texte, couleur, groupe, tags), jamais épinglée. */
    suspend fun duplicateNote(noteId: Long, title: String): Long? = db.withTransaction {
        val src = noteDao.getNoteWithTagsById(noteId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        val id = noteDao.insertNote(src.note.copy(id = 0, title = title, isPinned = false, dateCreated = now, dateModified = now))
        src.tags.forEach { noteDao.insertNoteTag(NoteTag(id, it.id)) }
        id
    }

    // ─── Corbeille ────────────────────────────────────────────────────────────

    suspend fun moveToTrash(noteId: Long) = noteDao.setDeletedAt(noteId, System.currentTimeMillis())

    suspend fun restoreFromTrash(noteId: Long) = noteDao.setDeletedAt(noteId, null)

    suspend fun emptyTrash() = noteDao.emptyTrash()

    suspend fun purgeTrash(maxAgeMs: Long) = noteDao.purgeTrashBefore(System.currentTimeMillis() - maxAgeMs)

    // ─── Note Tags ────────────────────────────────────────────────────────────

    suspend fun setTagsForNote(noteId: Long, tagIds: List<Long>) = db.withTransaction {
        noteDao.deleteAllTagsForNote(noteId)
        tagIds.forEach { tagId -> noteDao.insertNoteTag(NoteTag(noteId, tagId)) }
    }

    // ─── Groups ───────────────────────────────────────────────────────────────

    fun getAllGroupsWithCount(): Flow<List<GroupWithCount>> = noteGroupDao.getAllGroupsWithCount()

    suspend fun insertGroup(group: NoteGroup): Long = noteGroupDao.insertGroup(group)

    suspend fun updateGroup(group: NoteGroup) = noteGroupDao.updateGroup(group)

    suspend fun deleteGroup(group: NoteGroup) = noteGroupDao.deleteGroup(group)

    suspend fun getGroupCount(): Int = noteGroupDao.getGroupCount()

    // ─── Tags ─────────────────────────────────────────────────────────────────

    fun getAllTagsWithCount(): Flow<List<TagWithCount>> = tagDao.getAllTagsWithCount()

    suspend fun getTagByName(name: String): Tag? = tagDao.getTagByName(name)

    suspend fun getAllTagsList(): List<Tag> = tagDao.getAllTagsList()

    suspend fun insertTag(tag: Tag): Long = tagDao.insertTag(tag)

    suspend fun updateTag(tag: Tag) = tagDao.updateTag(tag)

    suspend fun deleteTag(tag: Tag) = tagDao.deleteTag(tag)

    suspend fun updateTagCategory(tagId: Long, categoryId: Long?) = tagDao.updateTagCategory(tagId, categoryId)

    // ─── Categories ───────────────────────────────────────────────────────────

    fun getAllCategories(): Flow<List<TagCategory>> = tagCategoryDao.getAllCategories()

    suspend fun insertCategory(category: TagCategory): Long = tagCategoryDao.insertCategory(category)

    suspend fun updateCategory(category: TagCategory) = tagCategoryDao.updateCategory(category)

    suspend fun deleteCategory(category: TagCategory) = tagCategoryDao.deleteCategory(category)

    // ─── Backup ───────────────────────────────────────────────────────────────

    suspend fun getAllNotesForBackup(): List<Note> = noteDao.getAllNotesForBackup()
    suspend fun getAllNoteTagsForBackup(): List<NoteTag> = noteDao.getAllNoteTagsForBackup()
    suspend fun getAllGroupsForBackup(): List<NoteGroup> = noteGroupDao.getAllGroupsForBackup()
    suspend fun getAllTagsForBackup(): List<Tag> = tagDao.getAllTagsForBackup()
    suspend fun getAllCategoriesForBackup(): List<TagCategory> = tagCategoryDao.getAllCategoriesForBackup()

    /**
     * Importe une sauvegarde. Les identifiants du fichier ne valent rien ici : catégories, tags et
     * groupes sont retrouvés **par leur nom** (réutilisés s'ils existent déjà, créés sinon), et
     * chaque note est rattachée à son groupe et à ses tags par ces noms. En mode [replace], tout
     * est d'abord effacé ; sinon les notes s'ajoutent à celles qui existent.
     */
    suspend fun importBackup(
        categories: List<TagCategory>,
        tags: List<Tag>,
        groups: List<NoteGroup>,
        notes: List<NoteExportManager.ImportedNoteData>,
        replace: Boolean,
    ) = db.withTransaction {
        if (replace) {
            noteDao.deleteAllNoteTags()
            noteDao.deleteAllNotes()
            tagDao.deleteAllTags()
            tagCategoryDao.deleteAllCategories()
            noteGroupDao.deleteAllGroups()
        }
        val categoryIds = tagCategoryDao.getAllCategoriesForBackup().associate { it.name.lowercase() to it.id }.toMutableMap()
        val oldCategoryName = categories.associate { it.id to it.name }
        for (c in categories) {
            categoryIds.getOrPut(c.name.lowercase()) { tagCategoryDao.insertCategory(c.copy(id = 0)) }
        }
        val tagIds = tagDao.getAllTagsForBackup().associate { it.name.lowercase() to it.id }.toMutableMap()
        for (t in tags) {
            val cat = t.categoryId?.let { oldCategoryName[it] }?.let { categoryIds[it.lowercase()] }
            tagIds.getOrPut(t.name.lowercase()) { tagDao.insertTag(t.copy(id = 0, categoryId = cat)) }
        }
        val groupIds = noteGroupDao.getAllGroupsForBackup().associate { it.name.lowercase() to it.id }.toMutableMap()
        var position = groupIds.size
        for (g in groups) {
            groupIds.getOrPut(g.name.lowercase()) { noteGroupDao.insertGroup(g.copy(id = 0, position = position++)) }
        }
        for (n in notes) {
            val groupId = n.groupName?.takeIf { it.isNotBlank() }?.let { name ->
                groupIds.getOrPut(name.lowercase()) { noteGroupDao.insertGroup(NoteGroup(name = name, position = position++)) }
            }
            val id = noteDao.insertNote(
                Note(
                    title = n.title,
                    content = n.content,
                    contentPlainText = MarkdownSyntax.toPlainText(n.content),
                    groupId = groupId,
                    isFavorite = n.isFavorite,
                    isPinned = n.isPinned,
                    colorHex = n.colorHex,
                    dateCreated = n.created ?: System.currentTimeMillis(),
                    dateModified = n.modified ?: System.currentTimeMillis(),
                )
            )
            for (tagName in n.tagNames) {
                val tagId = tagIds.getOrPut(tagName.lowercase()) { tagDao.insertTag(Tag(name = tagName)) }
                noteDao.insertNoteTag(NoteTag(id, tagId))
            }
        }
    }
}
