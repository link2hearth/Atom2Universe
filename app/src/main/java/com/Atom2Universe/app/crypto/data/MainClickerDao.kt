package com.Atom2Universe.app.crypto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MainClickerDao {

    @Query(
        """
        SELECT * FROM crypto_background_images
        WHERE folderUriString = :folderUriString
        ORDER BY uriString ASC
        """
    )
    suspend fun getImages(folderUriString: String): List<MainClickerImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<MainClickerImageEntity>)

    @Query("DELETE FROM crypto_background_images WHERE folderUriString = :folderUriString")
    suspend fun clearImages(folderUriString: String)

    @Query("DELETE FROM crypto_background_images WHERE folderUriString = :folderUriString AND uriString = :uriString")
    suspend fun deleteImage(folderUriString: String, uriString: String)

    @Query(
        """
        SELECT * FROM crypto_background_shuffle_entries
        WHERE folderUriString = :folderUriString
        ORDER BY position ASC
        """
    )
    suspend fun getShuffleEntries(folderUriString: String): List<MainClickerShuffleEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShuffleEntries(entries: List<MainClickerShuffleEntryEntity>)

    @Query("DELETE FROM crypto_background_shuffle_entries WHERE folderUriString = :folderUriString")
    suspend fun clearShuffleEntries(folderUriString: String)

    @Query("DELETE FROM crypto_background_shuffle_entries WHERE folderUriString = :folderUriString AND uriString = :uriString")
    suspend fun deleteShuffleEntriesByUri(folderUriString: String, uriString: String)

    @Query(
        """
        SELECT * FROM crypto_background_history_entries
        WHERE folderUriString = :folderUriString
        ORDER BY position ASC
        """
    )
    suspend fun getHistoryEntries(folderUriString: String): List<MainClickerHistoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntries(entries: List<MainClickerHistoryEntryEntity>)

    @Query("DELETE FROM crypto_background_history_entries WHERE folderUriString = :folderUriString")
    suspend fun clearHistoryEntries(folderUriString: String)

    @Query("DELETE FROM crypto_background_history_entries WHERE folderUriString = :folderUriString AND uriString = :uriString")
    suspend fun deleteHistoryEntriesByUri(folderUriString: String, uriString: String)

    @Query("SELECT * FROM crypto_background_state WHERE id = :id")
    suspend fun getState(id: Int = MainClickerStateEntity.STATE_ID): MainClickerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: MainClickerStateEntity)

    @Query("DELETE FROM crypto_background_state WHERE id = :id")
    suspend fun clearState(id: Int = MainClickerStateEntity.STATE_ID)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: MainClickerFavoriteEntity)

    @Query("DELETE FROM crypto_background_favorites WHERE uriString = :uriString")
    suspend fun deleteFavorite(uriString: String)

    @Query("SELECT COUNT(*) FROM crypto_background_favorites WHERE uriString = :uriString")
    suspend fun isFavorite(uriString: String): Int

    @Query("SELECT * FROM crypto_background_favorites ORDER BY addedAt ASC")
    suspend fun getAllFavorites(): List<MainClickerFavoriteEntity>
}
