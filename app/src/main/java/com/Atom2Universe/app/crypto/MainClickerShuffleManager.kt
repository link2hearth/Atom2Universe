package com.Atom2Universe.app.crypto

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.Atom2Universe.app.crypto.data.MainClickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

class MainClickerShuffleManager(
    private val repository: MainClickerRepository
) {

    enum class ReloadReason {
        LOADED,
        UNCHANGED,
        NO_FOLDER,
        NO_IMAGES,
        ERROR
    }

    data class ReloadResult(
        val reason: ReloadReason,
        val imageCount: Int
    )

    private val secureRandom = SecureRandom()
    private val supportedExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "avif", "heic", "heif"
    )

    private var lastFolderUriString: String? = null
    private var imageUris: List<Uri> = emptyList()
    private var shuffledOrder: MutableList<Int> = mutableListOf()
    private var history: MutableList<Int> = mutableListOf()
    private var historyPosition: Int = -1

    fun imageCount(): Int = imageUris.size

    fun currentUri(): Uri? {
        if (historyPosition !in history.indices) return null
        val index = history[historyPosition]
        return imageUris.getOrNull(index)
    }

    fun nextUri(): Uri? {
        if (imageUris.isEmpty()) return null

        if (historyPosition < history.lastIndex) {
            historyPosition += 1
            return currentUri()
        }

        // Si on a parcouru toutes les images, recommencer un nouveau cycle
        if (history.size >= imageUris.size) {
            startNewCycle()
        }

        val orderIndex = shuffledOrder.getOrNull(history.size) ?: return currentUri()
        history.add(orderIndex)
        historyPosition = history.lastIndex
        return currentUri()
    }

    private fun startNewCycle() {
        // Garder une trace de la dernière image affichée pour éviter de la répéter immédiatement
        val lastShown = history.lastOrNull()

        // Réinitialiser l'historique
        history = mutableListOf()
        historyPosition = -1

        // Créer un nouvel ordre mélangé
        shuffledOrder = imageUris.indices.toMutableList()
        fisherYatesShuffle(shuffledOrder)

        // Éviter que la première image du nouveau cycle soit la même que la dernière du cycle précédent
        if (lastShown != null && shuffledOrder.size > 1 && shuffledOrder.firstOrNull() == lastShown) {
            val swapIndex = 1 + secureRandom.nextInt(shuffledOrder.size - 1)
            val tmp = shuffledOrder[0]
            shuffledOrder[0] = shuffledOrder[swapIndex]
            shuffledOrder[swapIndex] = tmp
        }
    }

    fun previousUri(): Uri? {
        if (imageUris.isEmpty()) return null
        if (historyPosition <= 0) return currentUri()
        historyPosition -= 1
        return currentUri()
    }

    suspend fun reloadFromFolder(
        context: Context,
        folderUri: Uri?,
        force: Boolean
    ): ReloadResult = withContext(Dispatchers.IO) {
        if (folderUri == null) {
            clearState()
            repository.clearState()
            return@withContext ReloadResult(ReloadReason.NO_FOLDER, 0)
        }

        val uriString = folderUri.toString()

        if (!force) {
            repository.loadState(uriString)?.let { persisted ->
                applyPersistedState(uriString, persisted)
                val needsPersist =
                    persisted.isDirty ||
                    uriStrings(persisted.shuffledOrderUris) != uriStrings(shuffledOrderUris()) ||
                    uriStrings(persisted.historyUris) != uriStrings(historyUris()) ||
                    persisted.historyPosition != historyPosition
                if (needsPersist) {
                    repository.replaceFolderData(
                        folderUriString = uriString,
                        imageUris = imageUris,
                        shuffledOrderUris = shuffledOrderUris(),
                        historyUris = historyUris(),
                        historyPosition = historyPosition
                    )
                }
                return@withContext ReloadResult(ReloadReason.UNCHANGED, imageUris.size)
            }
        }

        if (!force && uriString == lastFolderUriString && imageUris.isNotEmpty()) {
            return@withContext ReloadResult(ReloadReason.UNCHANGED, imageUris.size)
        }

        val root = DocumentFile.fromTreeUri(context, folderUri)
        if (root == null || !root.canRead()) {
            clearState()
            lastFolderUriString = uriString
            repository.clearState()
            return@withContext ReloadResult(ReloadReason.ERROR, 0)
        }

        val images = mutableListOf<Uri>()
        collectImages(root, images)

        if (images.isEmpty()) {
            clearState()
            lastFolderUriString = uriString
            repository.replaceFolderData(
                folderUriString = uriString,
                imageUris = emptyList(),
                shuffledOrderUris = emptyList(),
                historyUris = emptyList(),
                historyPosition = -1
            )
            return@withContext ReloadResult(ReloadReason.NO_IMAGES, 0)
        }

        imageUris = images.distinctBy { it.toString() }.sortedBy { it.toString() }
        lastFolderUriString = uriString
        history = mutableListOf()
        historyPosition = -1
        shuffledOrder = mutableListOf()
        reshufflePreservingHistory()

        repository.replaceFolderData(
            folderUriString = uriString,
            imageUris = imageUris,
            shuffledOrderUris = shuffledOrderUris(),
            historyUris = historyUris(),
            historyPosition = historyPosition
        )

        ReloadResult(ReloadReason.LOADED, imageUris.size)
    }

    suspend fun persistPlaybackState() {
        val folderUriString = lastFolderUriString ?: return
        repository.persistPlayback(
            folderUriString = folderUriString,
            historyUris = historyUris(),
            historyPosition = historyPosition
        )
    }

    fun loadFromFavorites(uris: List<Uri>) {
        imageUris = uris.distinctBy { it.toString() }
        lastFolderUriString = null
        history = mutableListOf()
        historyPosition = -1
        shuffledOrder = mutableListOf()
        if (imageUris.isNotEmpty()) reshufflePreservingHistory()
    }

    suspend fun removeInvalidUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val folderUriString = lastFolderUriString ?: return@withContext false
        val removedIndex = imageUris.indexOfFirst { it.toString() == uri.toString() }
        if (removedIndex < 0) return@withContext false

        val currentUriString = currentUri()?.toString()

        imageUris = imageUris.filterIndexed { index, _ -> index != removedIndex }
        if (imageUris.isEmpty()) {
            clearState()
            repository.replaceFolderData(
                folderUriString = folderUriString,
                imageUris = emptyList(),
                shuffledOrderUris = emptyList(),
                historyUris = emptyList(),
                historyPosition = -1
            )
            return@withContext true
        }

        adjustAfterRemoval(removedIndex)
        restoreHistoryPosition(currentUriString)
        reshufflePreservingHistory()

        repository.replaceFolderData(
            folderUriString = folderUriString,
            imageUris = imageUris,
            shuffledOrderUris = shuffledOrderUris(),
            historyUris = historyUris(),
            historyPosition = historyPosition
        )

        true
    }

    private fun collectImages(directory: DocumentFile, output: MutableList<Uri>) {
        directory.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            if (name.startsWith(".")) return@forEach
            when {
                file.isDirectory -> collectImages(file, output)
                file.isFile && isSupportedImage(file) -> output.add(file.uri)
            }
        }
    }

    private fun isSupportedImage(file: DocumentFile): Boolean {
        val mime = file.type
        if (mime != null && mime.startsWith("image/")) return true

        val name = file.name ?: return false
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in supportedExtensions
    }

    private fun applyPersistedState(
        folderUriString: String,
        persisted: MainClickerRepository.PersistedState
    ) {
        lastFolderUriString = folderUriString
        imageUris = persisted.imageUris.distinctBy { it.toString() }.sortedBy { it.toString() }

        val indexMap = buildIndexMap()
        history = persisted.historyUris
            .mapNotNull { indexMap[it.toString()] }
            .distinct()
            .toMutableList()

        historyPosition = when {
            history.isEmpty() -> -1
            persisted.historyPosition in history.indices -> persisted.historyPosition
            else -> history.lastIndex
        }

        val persistedOrder = persisted.shuffledOrderUris
            .mapNotNull { indexMap[it.toString()] }
            .distinct()

        shuffledOrder = buildShuffleOrder(history, persistedOrder)
    }

    private fun buildIndexMap(): Map<String, Int> {
        return imageUris.mapIndexed { index, uri -> uri.toString() to index }.toMap()
    }

    private fun shuffledOrderUris(): List<Uri> {
        return shuffledOrder.mapNotNull { imageUris.getOrNull(it) }
    }

    private fun historyUris(): List<Uri> {
        return history.mapNotNull { imageUris.getOrNull(it) }
    }

    private fun uriStrings(uris: List<Uri>): List<String> = uris.map { it.toString() }

    private fun adjustAfterRemoval(removedIndex: Int) {
        shuffledOrder = adjustIndexList(shuffledOrder, removedIndex)
        history = adjustIndexList(history, removedIndex)
    }

    private fun adjustIndexList(source: MutableList<Int>, removedIndex: Int): MutableList<Int> {
        val adjusted = mutableListOf<Int>()
        source.forEach { value ->
            when {
                value == removedIndex -> Unit
                value > removedIndex -> adjusted.add(value - 1)
                else -> adjusted.add(value)
            }
        }
        return adjusted
    }

    private fun restoreHistoryPosition(previousCurrentUriString: String?) {
        if (history.isEmpty()) {
            historyPosition = -1
            return
        }

        if (previousCurrentUriString != null) {
            val currentIndex = buildIndexMap()[previousCurrentUriString]
            val newPosition = currentIndex?.let { history.indexOf(it) } ?: -1
            if (newPosition >= 0) {
                historyPosition = newPosition
                return
            }
        }

        historyPosition = history.lastIndex.coerceAtLeast(0)
    }

    private fun reshufflePreservingHistory() {
        shuffledOrder = buildShuffleOrder(history, shuffledOrder)
    }

    private fun buildShuffleOrder(historyIndices: List<Int>, preferredOrder: List<Int>): MutableList<Int> {
        val used = linkedSetOf<Int>()
        val output = mutableListOf<Int>()

        historyIndices.forEach { index ->
            if (index in imageUris.indices && used.add(index)) {
                output.add(index)
            }
        }

        preferredOrder.forEach { index ->
            if (index in imageUris.indices && used.add(index)) {
                output.add(index)
            }
        }

        val missing = imageUris.indices.filter { it !in used }.toMutableList()
        fisherYatesShuffle(missing)
        output.addAll(missing)
        return output
    }

    private fun fisherYatesShuffle(list: MutableList<Int>) {
        for (i in list.lastIndex downTo 1) {
            val swapIndex = secureRandom.nextInt(i + 1)
            if (swapIndex != i) {
                val tmp = list[i]
                list[i] = list[swapIndex]
                list[swapIndex] = tmp
            }
        }
    }

    private fun clearState() {
        imageUris = emptyList()
        shuffledOrder = mutableListOf()
        history = mutableListOf()
        historyPosition = -1
        lastFolderUriString = null
    }
}
