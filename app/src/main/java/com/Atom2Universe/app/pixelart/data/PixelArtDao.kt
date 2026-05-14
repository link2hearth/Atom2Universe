package com.Atom2Universe.app.pixelart.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les opérations sur les projets et frames pixel art
 */
@Dao
interface PixelArtDao {

    // ===== PROJETS =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: PixelArtProject): Long

    @Update
    suspend fun updateProject(project: PixelArtProject)

    @Query("UPDATE pixelart_projects SET dateModified = :timestamp WHERE id = :projectId")
    suspend fun updateProjectTimestamp(projectId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE pixelart_projects SET
            canvasWidth = :width,
            canvasHeight = :height,
            fps = :fps,
            primaryColor = :primaryColor,
            secondaryColor = :secondaryColor,
            currentFrameIndex = :currentFrameIndex,
            dateModified = :timestamp
        WHERE id = :projectId
    """)
    suspend fun updateProjectMetadata(
        projectId: Long,
        width: Int,
        height: Int,
        fps: Int,
        primaryColor: Int,
        secondaryColor: Int,
        currentFrameIndex: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteProject(project: PixelArtProject)

    @Query("DELETE FROM pixelart_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("SELECT * FROM pixelart_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): PixelArtProject?

    @Query("SELECT * FROM pixelart_projects WHERE id = :projectId")
    fun getProjectByIdFlow(projectId: Long): Flow<PixelArtProject?>

    @Query("SELECT * FROM pixelart_projects ORDER BY dateModified DESC")
    fun getAllProjectsFlow(): Flow<List<PixelArtProject>>

    @Query("SELECT * FROM pixelart_projects ORDER BY dateModified DESC")
    suspend fun getAllProjects(): List<PixelArtProject>

    @Query("SELECT * FROM pixelart_projects ORDER BY dateModified DESC LIMIT 1")
    suspend fun getLastModifiedProject(): PixelArtProject?

    @Query("SELECT COUNT(*) FROM pixelart_projects")
    suspend fun getProjectCount(): Int

    // ===== FRAMES =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrame(frame: PixelArtFrame): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrames(frames: List<PixelArtFrame>)

    @Update
    suspend fun updateFrame(frame: PixelArtFrame)

    @Query("""
        UPDATE pixelart_frames SET
            pixelDataBase64 = :pixelDataBase64,
            dateModified = :timestamp
        WHERE projectId = :projectId AND frameIndex = :frameIndex
    """)
    suspend fun updateFramePixelData(
        projectId: Long,
        frameIndex: Int,
        pixelDataBase64: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE pixelart_frames SET
            duration = :duration,
            dateModified = :timestamp
        WHERE projectId = :projectId AND frameIndex = :frameIndex
    """)
    suspend fun updateFrameDuration(
        projectId: Long,
        frameIndex: Int,
        duration: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteFrame(frame: PixelArtFrame)

    @Query("DELETE FROM pixelart_frames WHERE id = :frameId")
    suspend fun deleteFrameById(frameId: Long)

    @Query("DELETE FROM pixelart_frames WHERE projectId = :projectId")
    suspend fun deleteAllFramesByProjectId(projectId: Long)

    @Query("DELETE FROM pixelart_frames WHERE projectId = :projectId AND frameIndex = :frameIndex")
    suspend fun deleteFrameByIndex(projectId: Long, frameIndex: Int)

    @Query("SELECT * FROM pixelart_frames WHERE projectId = :projectId ORDER BY frameIndex ASC")
    suspend fun getFramesByProjectId(projectId: Long): List<PixelArtFrame>

    @Query("SELECT * FROM pixelart_frames WHERE projectId = :projectId ORDER BY frameIndex ASC")
    fun getFramesByProjectIdFlow(projectId: Long): Flow<List<PixelArtFrame>>

    @Query("SELECT * FROM pixelart_frames WHERE projectId = :projectId AND frameIndex = :frameIndex LIMIT 1")
    suspend fun getFrameByIndex(projectId: Long, frameIndex: Int): PixelArtFrame?

    @Query("SELECT COUNT(*) FROM pixelart_frames WHERE projectId = :projectId")
    suspend fun getFrameCount(projectId: Long): Int

    // ===== TRANSACTIONS COMBINÉES =====

    /**
     * Sauvegarde un projet complet avec toutes ses frames
     * Supprime les anciennes frames et insère les nouvelles
     */
    @Transaction
    suspend fun saveProjectWithFrames(project: PixelArtProject, frames: List<PixelArtFrame>): Long {
        val projectId = if (project.id == 0L) {
            insertProject(project)
        } else {
            updateProject(project)
            project.id
        }

        // Supprimer les anciennes frames et insérer les nouvelles
        deleteAllFramesByProjectId(projectId)
        val framesWithProjectId = frames.map { it.copy(projectId = projectId) }
        insertFrames(framesWithProjectId)

        return projectId
    }

    /**
     * Charge un projet avec toutes ses frames
     */
    @Transaction
    suspend fun getProjectWithFrames(projectId: Long): ProjectWithFrames? {
        val project = getProjectById(projectId) ?: return null
        val frames = getFramesByProjectId(projectId)
        return ProjectWithFrames(project, frames)
    }
}

/**
 * Classe de données combinant un projet avec ses frames
 */
data class ProjectWithFrames(
    val project: PixelArtProject,
    val frames: List<PixelArtFrame>
)
