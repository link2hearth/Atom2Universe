package com.Atom2Universe.app.crypto.data

import android.net.Uri
import androidx.room.withTransaction

class MainClickerRepository private constructor(
    private val database: MainClickerDatabase
) {

    data class PersistedState(
        val folderUriString: String,
        val imageUris: List<Uri>,
        val shuffledOrderUris: List<Uri>,
        val historyUris: List<Uri>,
        val historyPosition: Int,
        val isDirty: Boolean
    )

    private val dao = database.cryptoBackgroundDao()

    suspend fun loadState(folderUriString: String): PersistedState? = database.withTransaction {
        val state = dao.getState() ?: return@withTransaction null
        if (state.folderUriString != folderUriString) return@withTransaction null

        val images = dao.getImages(folderUriString)
        if (images.isEmpty()) return@withTransaction null

        val imageUris = images.map { Uri.parse(it.uriString) }
        val validSet = imageUris.map { it.toString() }.toSet()

        val rawShuffleUris = dao.getShuffleEntries(folderUriString).map { Uri.parse(it.uriString) }
        val rawHistoryUris = dao.getHistoryEntries(folderUriString).map { Uri.parse(it.uriString) }
        val shuffledOrderUris = normalizeOrder(rawShuffleUris, validSet, imageUris)
        val historyUris = normalizeHistory(rawHistoryUris, validSet)
        val historyPosition = normalizeHistoryPosition(state.historyPosition, historyUris.size)
        val isDirty =
            rawShuffleUris.map { it.toString() } != shuffledOrderUris.map { it.toString() } ||
            rawHistoryUris.map { it.toString() } != historyUris.map { it.toString() } ||
            historyPosition != state.historyPosition

        PersistedState(
            folderUriString = folderUriString,
            imageUris = imageUris,
            shuffledOrderUris = shuffledOrderUris,
            historyUris = historyUris,
            historyPosition = historyPosition,
            isDirty = isDirty
        )
    }

    suspend fun replaceFolderData(
        folderUriString: String,
        imageUris: List<Uri>,
        shuffledOrderUris: List<Uri>,
        historyUris: List<Uri>,
        historyPosition: Int
    ) {
        val normalizedImages = normalizeImages(imageUris)
        if (normalizedImages.isEmpty()) {
            val now = System.currentTimeMillis()
            database.withTransaction {
                clearFolderData(folderUriString)
                dao.upsertState(
                    MainClickerStateEntity(
                        folderUriString = null,
                        historyPosition = -1,
                        updatedAt = now
                    )
                )
            }
            return
        }

        val validSet = normalizedImages.map { it.toString() }.toSet()
        val normalizedShuffle = normalizeOrder(shuffledOrderUris, validSet, normalizedImages)
        val normalizedHistory = normalizeHistory(historyUris, validSet)
        val normalizedHistoryPosition = normalizeHistoryPosition(historyPosition, normalizedHistory.size)
        val now = System.currentTimeMillis()

        database.withTransaction {
            clearFolderData(folderUriString)
            dao.insertImages(
                normalizedImages.map {
                    MainClickerImageEntity(
                        folderUriString = folderUriString,
                        uriString = it.toString(),
                        addedAt = now
                    )
                }
            )
            dao.insertShuffleEntries(
                normalizedShuffle.mapIndexed { index, uri ->
                    MainClickerShuffleEntryEntity(
                        folderUriString = folderUriString,
                        position = index,
                        uriString = uri.toString()
                    )
                }
            )
            dao.insertHistoryEntries(
                normalizedHistory.mapIndexed { index, uri ->
                    MainClickerHistoryEntryEntity(
                        folderUriString = folderUriString,
                        position = index,
                        uriString = uri.toString()
                    )
                }
            )
            dao.upsertState(
                MainClickerStateEntity(
                    folderUriString = folderUriString,
                    historyPosition = normalizedHistoryPosition,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun persistPlayback(
        folderUriString: String,
        historyUris: List<Uri>,
        historyPosition: Int
    ) {
        database.withTransaction {
            val images = dao.getImages(folderUriString)
            if (images.isEmpty()) {
                dao.clearState()
                return@withTransaction
            }

            val validSet = images.map { it.uriString }.toSet()
            val normalizedHistory = normalizeHistory(historyUris, validSet)
            val normalizedHistoryPosition = normalizeHistoryPosition(historyPosition, normalizedHistory.size)
            val now = System.currentTimeMillis()

            dao.clearHistoryEntries(folderUriString)
            dao.insertHistoryEntries(
                normalizedHistory.mapIndexed { index, uri ->
                    MainClickerHistoryEntryEntity(
                        folderUriString = folderUriString,
                        position = index,
                        uriString = uri.toString()
                    )
                }
            )
            dao.upsertState(
                MainClickerStateEntity(
                    folderUriString = folderUriString,
                    historyPosition = normalizedHistoryPosition,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun clearState() {
        database.withTransaction {
            dao.upsertState(
                MainClickerStateEntity(
                    folderUriString = null,
                    historyPosition = -1,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun addFavorite(uri: Uri) {
        dao.insertFavorite(MainClickerFavoriteEntity(uri.toString()))
    }

    suspend fun removeFavorite(uri: Uri) {
        dao.deleteFavorite(uri.toString())
    }

    suspend fun isFavorite(uri: Uri): Boolean =
        dao.isFavorite(uri.toString()) > 0

    suspend fun getAllFavoriteUris(): List<Uri> =
        dao.getAllFavorites().map { Uri.parse(it.uriString) }

    private suspend fun clearFolderData(folderUriString: String) {
        dao.clearHistoryEntries(folderUriString)
        dao.clearShuffleEntries(folderUriString)
        dao.clearImages(folderUriString)
    }

    private fun normalizeImages(imageUris: List<Uri>): List<Uri> {
        val distinct = linkedMapOf<String, Uri>()
        imageUris.forEach { uri ->
            distinct[uri.toString()] = uri
        }
        return distinct.values.sortedBy { it.toString() }
    }

    private fun normalizeOrder(orderUris: List<Uri>, validSet: Set<String>, fallback: List<Uri>): List<Uri> {
        val normalized = linkedMapOf<String, Uri>()
        orderUris.forEach { uri ->
            val key = uri.toString()
            if (key in validSet && key !in normalized) {
                normalized[key] = uri
            }
        }

        if (normalized.size == validSet.size) {
            return normalized.values.toList()
        }

        fallback.forEach { uri ->
            val key = uri.toString()
            if (key in validSet && key !in normalized) {
                normalized[key] = uri
            }
        }
        return normalized.values.toList()
    }

    private fun normalizeHistory(historyUris: List<Uri>, validSet: Set<String>): List<Uri> {
        val normalized = linkedSetOf<String>()
        val output = mutableListOf<Uri>()
        historyUris.forEach { uri ->
            val key = uri.toString()
            if (key in validSet && normalized.add(key)) {
                output.add(uri)
            }
        }
        return output
    }

    private fun normalizeHistoryPosition(historyPosition: Int, historySize: Int): Int {
        if (historySize == 0) return -1
        return historyPosition.coerceIn(0, historySize - 1)
    }

    companion object {
        @Volatile
        private var INSTANCE: MainClickerRepository? = null

        fun getInstance(database: MainClickerDatabase): MainClickerRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = MainClickerRepository(database)
                INSTANCE = instance
                instance
            }
        }
    }
}
