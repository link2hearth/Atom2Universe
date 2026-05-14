package com.Atom2Universe.app.notes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteGroupDao {

    @Query("SELECT * FROM note_groups ORDER BY position ASC, name ASC")
    fun getAllGroups(): Flow<List<NoteGroup>>

    @Query("""
        SELECT note_groups.*, COUNT(notes.id) as noteCount
        FROM note_groups
        LEFT JOIN notes ON notes.groupId = note_groups.id
        GROUP BY note_groups.id
        ORDER BY note_groups.position ASC, note_groups.name ASC
    """)
    fun getAllGroupsWithCount(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM note_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): NoteGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: NoteGroup): Long

    @Update
    suspend fun updateGroup(group: NoteGroup)

    @Delete
    suspend fun deleteGroup(group: NoteGroup)

    @Query("DELETE FROM note_groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    @Query("UPDATE note_groups SET position = :position WHERE id = :groupId")
    suspend fun updateGroupPosition(groupId: Long, position: Int)

    @Query("UPDATE note_groups SET colorHex = :colorHex, textColorMode = :textColorMode WHERE id = :groupId")
    suspend fun updateGroupColor(groupId: Long, colorHex: String?, textColorMode: String)

    @Query("SELECT COUNT(*) FROM note_groups")
    suspend fun getGroupCount(): Int

    // Backup
    @Query("SELECT * FROM note_groups")
    suspend fun getAllGroupsForBackup(): List<NoteGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<NoteGroup>): List<Long>

    @Query("DELETE FROM note_groups")
    suspend fun deleteAllGroups()
}
