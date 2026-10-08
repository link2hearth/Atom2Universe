package com.Atom2Universe.app.notes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagCategoryDao {

    @Query("SELECT * FROM tag_categories ORDER BY sortOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<TagCategory>>

    @Query("SELECT * FROM tag_categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): TagCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: TagCategory): Long

    @Update
    suspend fun updateCategory(category: TagCategory)

    @Delete
    suspend fun deleteCategory(category: TagCategory)

    @Query("UPDATE tag_categories SET sortOrder = :sortOrder WHERE id = :categoryId")
    suspend fun updateCategorySortOrder(categoryId: Long, sortOrder: Int)

    // Backup
    @Query("SELECT * FROM tag_categories")
    suspend fun getAllCategoriesForBackup(): List<TagCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<TagCategory>): List<Long>

    @Query("DELETE FROM tag_categories")
    suspend fun deleteAllCategories()
}
