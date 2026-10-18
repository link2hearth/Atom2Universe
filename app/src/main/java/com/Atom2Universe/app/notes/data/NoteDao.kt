package com.Atom2Universe.app.notes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Toutes les listes ignorent les notes à la corbeille (`deletedAt` non nul), sauf celles de la corbeille. */
@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, dateModified DESC")
    fun getAllNotesWithTags(): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashedNotesWithTags(): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteWithTagsById(noteId: Long): NoteWithTags?

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)

    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :noteId")
    suspend fun updateNotePinned(noteId: Long, isPinned: Boolean)

    @Query("UPDATE notes SET isFavorite = :isFavorite WHERE id = :noteId")
    suspend fun updateNoteFavorite(noteId: Long, isFavorite: Boolean)

    @Query("UPDATE notes SET groupId = :groupId WHERE id = :noteId")
    suspend fun updateNoteGroup(noteId: Long, groupId: Long?)

    @Query("UPDATE notes SET colorHex = :colorHex WHERE id = :noteId")
    suspend fun updateNoteColor(noteId: Long, colorHex: String?)

    // Corbeille
    @Query("UPDATE notes SET deletedAt = :at WHERE id = :noteId")
    suspend fun setDeletedAt(noteId: Long, at: Long?)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrashBefore(before: Long)

    // NoteTag junction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTag(noteTag: NoteTag)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteAllTagsForNote(noteId: Long)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun getNoteByTitle(title: String): Note?

    /** Les notes qui citent `[[titre]]` (sans tenir compte de la casse). */
    @Transaction
    @Query("""
        SELECT * FROM notes
        WHERE deletedAt IS NULL AND id != :excludeNoteId AND instr(LOWER(content), LOWER(:needle)) > 0
        ORDER BY dateModified DESC
    """)
    suspend fun getNotesContaining(needle: String, excludeNoteId: Long): List<NoteWithTags>

    @Query("SELECT id, title FROM notes WHERE deletedAt IS NULL AND title != '' ORDER BY title COLLATE NOCASE")
    suspend fun getAllTitles(): List<NoteTitle>

    // Backup
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    suspend fun getAllNotesForBackup(): List<Note>

    // Sync : toutes les notes, la corbeille comprise (elle voyage aussi)
    @Query("SELECT * FROM notes")
    suspend fun getAllNotesForSync(): List<Note>

    @Query("SELECT * FROM notes WHERE uuid = :uuid LIMIT 1")
    suspend fun getNoteByUuid(uuid: String): Note?

    @Query("DELETE FROM notes WHERE uuid = :uuid")
    suspend fun deleteNoteByUuid(uuid: String)

    @Query("SELECT * FROM note_tags")
    suspend fun getAllNoteTagsForBackup(): List<NoteTag>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM note_tags")
    suspend fun deleteAllNoteTags()
}

data class NoteTitle(val id: Long, val title: String)
