package com.Atom2Universe.app.crypto.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "crypto_background_images",
    primaryKeys = ["folderUriString", "uriString"],
    indices = [Index(value = ["folderUriString"])]
)
data class MainClickerImageEntity(
    val folderUriString: String,
    val uriString: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "crypto_background_shuffle_entries",
    primaryKeys = ["folderUriString", "position"],
    indices = [
        Index(value = ["folderUriString"]),
        Index(value = ["folderUriString", "uriString"])
    ]
)
data class MainClickerShuffleEntryEntity(
    val folderUriString: String,
    val position: Int,
    val uriString: String
)

@Entity(
    tableName = "crypto_background_history_entries",
    primaryKeys = ["folderUriString", "position"],
    indices = [
        Index(value = ["folderUriString"]),
        Index(value = ["folderUriString", "uriString"])
    ]
)
data class MainClickerHistoryEntryEntity(
    val folderUriString: String,
    val position: Int,
    val uriString: String
)

@Entity(tableName = "crypto_background_state")
data class MainClickerStateEntity(
    @PrimaryKey
    val id: Int = STATE_ID,
    val folderUriString: String?,
    val historyPosition: Int,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATE_ID = 0
    }
}

@Entity(tableName = "crypto_background_favorites")
data class MainClickerFavoriteEntity(
    @PrimaryKey
    val uriString: String,
    val addedAt: Long = System.currentTimeMillis()
)
