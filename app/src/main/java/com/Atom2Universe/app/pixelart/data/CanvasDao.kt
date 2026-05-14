package com.Atom2Universe.app.pixelart.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les opérations sur les projets Canvas (Toile Infinie)
 */
@Dao
interface CanvasDao {

    // ===== INSERTION =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: CanvasProject): Long

    // ===== MISE À JOUR =====

    @Update
    suspend fun updateProject(project: CanvasProject)

    @Query("UPDATE canvas_projects SET dateModified = :timestamp WHERE id = :projectId")
    suspend fun updateProjectTimestamp(projectId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE canvas_projects SET
            viewportX = :viewportX,
            viewportY = :viewportY,
            viewportZoom = :viewportZoom,
            objectsJson = :objectsJson,
            dateModified = :timestamp
        WHERE id = :projectId
    """)
    suspend fun updateProjectData(
        projectId: Long,
        viewportX: Float,
        viewportY: Float,
        viewportZoom: Float,
        objectsJson: String,
        timestamp: Long = System.currentTimeMillis()
    )

    // ===== SUPPRESSION =====

    @Delete
    suspend fun deleteProject(project: CanvasProject)

    @Query("DELETE FROM canvas_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    // ===== LECTURE =====

    @Query("SELECT * FROM canvas_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): CanvasProject?

    @Query("SELECT * FROM canvas_projects WHERE id = :projectId")
    fun getProjectByIdFlow(projectId: Long): Flow<CanvasProject?>

    @Query("SELECT * FROM canvas_projects ORDER BY dateModified DESC")
    fun getAllProjectsFlow(): Flow<List<CanvasProject>>

    @Query("SELECT * FROM canvas_projects ORDER BY dateModified DESC")
    suspend fun getAllProjects(): List<CanvasProject>

    @Query("SELECT * FROM canvas_projects ORDER BY dateModified DESC LIMIT 1")
    suspend fun getLastModifiedProject(): CanvasProject?

    @Query("SELECT COUNT(*) FROM canvas_projects")
    suspend fun getProjectCount(): Int
}
