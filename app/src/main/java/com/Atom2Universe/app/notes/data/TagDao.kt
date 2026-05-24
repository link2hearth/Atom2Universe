package com.Atom2Universe.app.notes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("""
        SELECT tags.*, COUNT(note_tags.noteId) as usageCount
        FROM tags
        LEFT JOIN note_tags ON note_tags.tagId = tags.id
        GROUP BY tags.id
        ORDER BY tags.sortOrder ASC, tags.name ASC
    """)
    fun getAllTagsWithCount(): Flow<List<TagWithCount>>

    @Query("""
        SELECT tags.*, COUNT(note_tags.noteId) as usageCount
        FROM tags
        LEFT JOIN note_tags ON note_tags.tagId = tags.id
        WHERE tags.id = :tagId
        GROUP BY tags.id
    """)
    suspend fun getTagWithCountById(tagId: Long): TagWithCount?

    @Query("SELECT * FROM tags WHERE id = :tagId")
    suspend fun getTagById(tagId: Long): Tag?

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllTagsList(): List<Tag>

    @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag): Long

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Query("UPDATE tags SET sortOrder = :sortOrder WHERE id = :tagId")
    suspend fun updateTagSortOrder(tagId: Long, sortOrder: Int)

    @Query("UPDATE tags SET categoryId = :categoryId WHERE id = :tagId")
    suspend fun updateTagCategory(tagId: Long, categoryId: Long?)

    // Backup
    @Query("SELECT * FROM tags")
    suspend fun getAllTagsForBackup(): List<Tag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<Tag>): List<Long>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()
}
