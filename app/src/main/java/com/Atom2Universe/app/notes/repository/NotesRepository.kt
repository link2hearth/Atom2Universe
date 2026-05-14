package com.Atom2Universe.app.notes.repository

import com.Atom2Universe.app.notes.data.*
import kotlinx.coroutines.flow.Flow

class NotesRepository(
    private val noteDao: NoteDao,
    private val noteGroupDao: NoteGroupDao,
    private val tagDao: TagDao,
    private val tagCategoryDao: TagCategoryDao
) {

    // ─── Notes ───────────────────────────────────────────────────────────────

    fun getAllNotesWithTags(): Flow<List<NoteWithTags>> = noteDao.getAllNotesWithTags()

    fun getUngroupedNotesWithTags(): Flow<List<NoteWithTags>> = noteDao.getUngroupedNotesWithTags()

    fun getNotesByGroupWithTags(groupId: Long): Flow<List<NoteWithTags>> =
        noteDao.getNotesByGroupWithTags(groupId)

    fun getFavoriteNotesWithTags(): Flow<List<NoteWithTags>> = noteDao.getFavoriteNotesWithTags()

    fun getRecentNotesWithTags(limit: Int = 10): Flow<List<NoteWithTags>> =
        noteDao.getRecentNotesWithTags(limit)

    suspend fun getNoteWithTagsById(noteId: Long): NoteWithTags? =
        noteDao.getNoteWithTagsById(noteId)

    suspend fun getNoteById(noteId: Long): Note? = noteDao.getNoteById(noteId)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    suspend fun deleteNoteById(noteId: Long) = noteDao.deleteNoteById(noteId)

    suspend fun toggleNotePinned(noteId: Long, isPinned: Boolean) =
        noteDao.updateNotePinned(noteId, isPinned)

    suspend fun toggleNoteFavorite(noteId: Long, isFavorite: Boolean) =
        noteDao.updateNoteFavorite(noteId, isFavorite)

    suspend fun updateNoteGroup(noteId: Long, groupId: Long?) =
        noteDao.updateNoteGroup(noteId, groupId)

    suspend fun updateNoteColor(noteId: Long, colorHex: String?, textColorMode: String) =
        noteDao.updateNoteColor(noteId, colorHex, textColorMode)

    // ─── Note Tags ────────────────────────────────────────────────────────────

    suspend fun setTagsForNote(noteId: Long, tagIds: List<Long>) {
        noteDao.deleteAllTagsForNote(noteId)
        tagIds.forEach { tagId -> noteDao.insertNoteTag(NoteTag(noteId, tagId)) }
    }

    suspend fun addTagToNote(noteId: Long, tagId: Long) =
        noteDao.insertNoteTag(NoteTag(noteId, tagId))

    suspend fun removeTagFromNote(noteId: Long, tagId: Long) =
        noteDao.deleteNoteTag(NoteTag(noteId, tagId))

    suspend fun getTagIdsForNote(noteId: Long): List<Long> = noteDao.getTagIdsForNote(noteId)

    fun getNotesByTag(tagId: Long): Flow<List<NoteWithTags>> = noteDao.getNotesByTag(tagId)

    // ─── Search ───────────────────────────────────────────────────────────────

    suspend fun searchNotes(query: String): List<NoteWithTags> {
        val ftsQuery = query.trim().split("\\s+".toRegex()).joinToString(" OR ") { "$it*" }
        return noteDao.searchNotes(ftsQuery)
    }

    // ─── Groups ───────────────────────────────────────────────────────────────

    fun getAllGroups(): Flow<List<NoteGroup>> = noteGroupDao.getAllGroups()

    fun getAllGroupsWithCount(): Flow<List<GroupWithCount>> = noteGroupDao.getAllGroupsWithCount()

    suspend fun getGroupById(groupId: Long): NoteGroup? = noteGroupDao.getGroupById(groupId)

    suspend fun insertGroup(group: NoteGroup): Long = noteGroupDao.insertGroup(group)

    suspend fun updateGroup(group: NoteGroup) = noteGroupDao.updateGroup(group)

    suspend fun deleteGroup(group: NoteGroup) = noteGroupDao.deleteGroup(group)

    suspend fun updateGroupPosition(groupId: Long, position: Int) =
        noteGroupDao.updateGroupPosition(groupId, position)

    suspend fun updateGroupColor(groupId: Long, colorHex: String?, textColorMode: String) =
        noteGroupDao.updateGroupColor(groupId, colorHex, textColorMode)

    suspend fun getGroupCount(): Int = noteGroupDao.getGroupCount()

    // ─── Tags ─────────────────────────────────────────────────────────────────

    fun getAllTagsWithCount(): Flow<List<TagWithCount>> = tagDao.getAllTagsWithCount()

    suspend fun getTagById(tagId: Long): Tag? = tagDao.getTagById(tagId)

    suspend fun getAllTagsList(): List<Tag> = tagDao.getAllTagsList()

    suspend fun insertTag(tag: Tag): Long = tagDao.insertTag(tag)

    suspend fun updateTag(tag: Tag) = tagDao.updateTag(tag)

    suspend fun deleteTag(tag: Tag) = tagDao.deleteTag(tag)

    suspend fun updateTagSortOrder(tagId: Long, sortOrder: Int) =
        tagDao.updateTagSortOrder(tagId, sortOrder)

    suspend fun updateTagCategory(tagId: Long, categoryId: Long?) =
        tagDao.updateTagCategory(tagId, categoryId)

    // ─── Categories ───────────────────────────────────────────────────────────

    fun getAllCategories(): Flow<List<TagCategory>> = tagCategoryDao.getAllCategories()

    suspend fun getCategoryById(categoryId: Long): TagCategory? =
        tagCategoryDao.getCategoryById(categoryId)

    suspend fun insertCategory(category: TagCategory): Long =
        tagCategoryDao.insertCategory(category)

    suspend fun updateCategory(category: TagCategory) = tagCategoryDao.updateCategory(category)

    suspend fun deleteCategory(category: TagCategory) = tagCategoryDao.deleteCategory(category)

    suspend fun updateCategorySortOrder(categoryId: Long, sortOrder: Int) =
        tagCategoryDao.updateCategorySortOrder(categoryId, sortOrder)

    // ─── Backup ───────────────────────────────────────────────────────────────

    suspend fun getAllNotesForBackup(): List<Note> = noteDao.getAllNotesForBackup()
    suspend fun getAllNoteTagsForBackup(): List<NoteTag> = noteDao.getAllNoteTagsForBackup()
    suspend fun getAllGroupsForBackup(): List<NoteGroup> = noteGroupDao.getAllGroupsForBackup()
    suspend fun getAllTagsForBackup(): List<Tag> = tagDao.getAllTagsForBackup()
    suspend fun getAllCategoriesForBackup(): List<TagCategory> =
        tagCategoryDao.getAllCategoriesForBackup()

    suspend fun replaceAllData(
        categories: List<TagCategory>,
        tags: List<Tag>,
        groups: List<NoteGroup>,
        notes: List<Note>,
        noteTags: List<NoteTag>
    ) {
        tagCategoryDao.deleteAllCategories()
        tagDao.deleteAllTags()
        noteGroupDao.deleteAllGroups()
        noteDao.deleteAllNotes()
        noteDao.deleteAllNoteTags()

        tagCategoryDao.insertCategories(categories)
        tagDao.insertTags(tags)
        noteGroupDao.insertGroups(groups)
        noteDao.insertNotes(notes)
        noteDao.insertNoteTags(noteTags)
    }

    suspend fun mergeData(
        categories: List<TagCategory>,
        tags: List<Tag>,
        groups: List<NoteGroup>,
        notes: List<Note>,
        noteTags: List<NoteTag>
    ) {
        categories.forEach { tagCategoryDao.insertCategory(it.copy(id = 0)) }
        tags.forEach { tagDao.insertTag(it.copy(id = 0)) }
        groups.forEach { noteGroupDao.insertGroup(it.copy(id = 0)) }
        notes.forEach { noteDao.insertNote(it.copy(id = 0)) }
    }
}
