package com.Atom2Universe.app.comics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ComicsDao {

    // ── Comics ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM comic_entries ORDER BY lastOpenedAt DESC")
    suspend fun getAllComics(): List<ComicEntry>

    @Query("SELECT * FROM comic_entries WHERE rootId = :rootId")
    suspend fun getComicsByRoot(rootId: String): List<ComicEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComic(comic: ComicEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComics(comics: List<ComicEntry>)

    @Query("SELECT * FROM comic_entries WHERE sourcePath = :sourcePath LIMIT 1")
    suspend fun getComicBySource(sourcePath: String): ComicEntry?

    @Query("SELECT currentPage FROM comic_entries WHERE id = :id")
    suspend fun getCurrentPage(id: String): Int?

    @Query("UPDATE comic_entries SET totalPages = :total WHERE id = :id")
    suspend fun updateTotalPages(id: String, total: Int)

    @Query("UPDATE comic_entries SET currentPage = :page, lastOpenedAt = :time WHERE id = :id")
    suspend fun updateProgress(id: String, page: Int, time: Long)

    @Query("DELETE FROM comic_entries WHERE id = :id")
    suspend fun deleteComic(id: String)

    @Query("DELETE FROM comic_entries WHERE rootId = :rootId")
    suspend fun deleteComicsByRoot(rootId: String)

    @Query("DELETE FROM comic_entries")
    suspend fun deleteAllComics()

    // ── Roots ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM comic_roots ORDER BY lastOpenedAt DESC")
    suspend fun getAllRoots(): List<ComicsRootLibrary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoot(root: ComicsRootLibrary)

    @Update
    suspend fun updateRoot(root: ComicsRootLibrary)

    @Query("DELETE FROM comic_roots WHERE id = :id")
    suspend fun deleteRoot(id: String)

    @Query("DELETE FROM comic_roots")
    suspend fun deleteAllRoots()
}
