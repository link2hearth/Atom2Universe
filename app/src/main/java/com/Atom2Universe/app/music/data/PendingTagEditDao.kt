package com.Atom2Universe.app.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO pour la gestion des éditions de tags en attente.
 */
@Dao
interface PendingTagEditDao {

    /**
     * Récupère toutes les éditions en attente.
     */
    @Query("SELECT * FROM pending_tag_edits ORDER BY createdAt ASC")
    suspend fun getAllPendingEdits(): List<PendingTagEdit>

    /**
     * Récupère une édition en attente par chemin de fichier.
     */
    @Query("SELECT * FROM pending_tag_edits WHERE filePath = :filePath")
    suspend fun getPendingEdit(filePath: String): PendingTagEdit?

    /**
     * Vérifie s'il y a des éditions en attente.
     */
    @Query("SELECT COUNT(*) > 0 FROM pending_tag_edits")
    suspend fun hasPendingEdits(): Boolean

    /**
     * Compte le nombre d'éditions en attente.
     */
    @Query("SELECT COUNT(*) FROM pending_tag_edits")
    suspend fun getPendingEditCount(): Int

    /**
     * Récupère les éditions en attente pour un fichier spécifique.
     * Retourne true si le fichier a une édition en attente.
     */
    @Query("SELECT COUNT(*) > 0 FROM pending_tag_edits WHERE filePath = :filePath")
    suspend fun hasPendingEditForFile(filePath: String): Boolean

    /**
     * Insère ou met à jour une édition en attente.
     * Si une édition existe déjà pour ce fichier, elle est remplacée.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingEdit(edit: PendingTagEdit)

    /**
     * Supprime une édition en attente après son application.
     */
    @Query("DELETE FROM pending_tag_edits WHERE filePath = :filePath")
    suspend fun deletePendingEdit(filePath: String)

    /**
     * Supprime toutes les éditions en attente.
     */
    @Query("DELETE FROM pending_tag_edits")
    suspend fun deleteAllPendingEdits()
}
