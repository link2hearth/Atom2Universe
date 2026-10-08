package com.Atom2Universe.app.books

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BookShelfDao {

    @Query("SELECT * FROM book_shelf_roots ORDER BY addedAt DESC")
    suspend fun getAllRoots(): List<BookShelfRoot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoot(root: BookShelfRoot)

    @Update
    suspend fun updateRoot(root: BookShelfRoot)

    @Query("DELETE FROM book_shelf_roots WHERE id = :id")
    suspend fun deleteRoot(id: String)

    @Query("SELECT * FROM book_shelf_entries WHERE rootId = :rootId")
    suspend fun getEntriesByRoot(rootId: String): List<BookShelfEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<BookShelfEntry>)

    @Query("DELETE FROM book_shelf_entries WHERE rootId = :rootId")
    suspend fun deleteEntriesByRoot(rootId: String)

    @Query("UPDATE book_shelf_entries SET lastReadItem = :item, totalItems = :total, lastOpenedAt = :time WHERE sourcePath = :path")
    suspend fun updateProgress(path: String, item: Int, total: Int, time: Long)
}
