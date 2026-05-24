package com.Atom2Universe.app.notes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, dateModified DESC")
    fun getAllNotesWithTags(): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE groupId IS NULL ORDER BY isPinned DESC, dateModified DESC")
    fun getUngroupedNotesWithTags(): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE groupId = :groupId ORDER BY isPinned DESC, dateModified DESC")
    fun getNotesByGroupWithTags(groupId: Long): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE isFavorite = 1 ORDER BY isPinned DESC, dateModified DESC")
    fun getFavoriteNotesWithTags(): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY dateModified DESC LIMIT :limit")
    fun getRecentNotesWithTags(limit: Int = 10): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteWithTagsById(noteId: Long): NoteWithTags?

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)

    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :noteId")
    suspend fun updateNotePinned(noteId: Long, isPinned: Boolean)

    @Query("UPDATE notes SET isFavorite = :isFavorite WHERE id = :noteId")
    suspend fun updateNoteFavorite(noteId: Long, isFavorite: Boolean)

    @Query("UPDATE notes SET groupId = :groupId WHERE id = :noteId")
    suspend fun updateNoteGroup(noteId: Long, groupId: Long?)

    @Query("UPDATE notes SET colorHex = :colorHex, textColorMode = :textColorMode WHERE id = :noteId")
    suspend fun updateNoteColor(noteId: Long, colorHex: String?, textColorMode: String)

    // NoteTag junction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTag(noteTag: NoteTag)

    @Delete
    suspend fun deleteNoteTag(noteTag: NoteTag)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteAllTagsForNote(noteId: Long)

    @Query("SELECT tagId FROM note_tags WHERE noteId = :noteId")
    suspend fun getTagIdsForNote(noteId: Long): List<Long>

    // FTS Search
    @Transaction
    @Query("""
        SELECT notes.* FROM notes
        INNER JOIN notes_fts ON notes.id = notes_fts.rowid
        WHERE notes_fts MATCH :query
        ORDER BY notes.dateModified DESC
    """)
    suspend fun searchNotes(query: String): List<NoteWithTags>

    @Query("SELECT * FROM notes WHERE LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun getNoteByTitle(title: String): Note?

    // Search by tag
    @Transaction
    @Query("""
        SELECT notes.* FROM notes
        INNER JOIN note_tags ON notes.id = note_tags.noteId
        WHERE note_tags.tagId = :tagId
        ORDER BY notes.dateModified DESC
    """)
    fun getNotesByTag(tagId: Long): Flow<List<NoteWithTags>>

    @Transaction
    @Query("""
        SELECT notes.* FROM notes
        INNER JOIN note_tags ON notes.id = note_tags.noteId
        WHERE note_tags.tagId = :tagId AND notes.id != :excludeNoteId
        ORDER BY notes.dateModified DESC
    """)
    suspend fun getNotesByTagExcluding(tagId: Long, excludeNoteId: Long): List<NoteWithTags>

    // Backup
    @Query("SELECT * FROM notes")
    suspend fun getAllNotesForBackup(): List<Note>

    @Query("SELECT * FROM note_tags")
    suspend fun getAllNoteTagsForBackup(): List<NoteTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTags(noteTags: List<NoteTag>)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM note_tags")
    suspend fun deleteAllNoteTags()
}
