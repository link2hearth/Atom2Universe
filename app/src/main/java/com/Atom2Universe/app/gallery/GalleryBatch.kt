package com.Atom2Universe.app.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class BatchAction { COPY, MOVE, HIDE, DELETE }
internal data class BatchResult(val failures: List<Photo>, val total: Int)

/** A rotation can replace the screen while a provider is still writing a large file. */
internal class GalleryBatch(app: Application) : AndroidViewModel(app) {
    private val library = GalleryLibrary.get(app)
    var selected = emptySet<String>()
    val result = MutableLiveData<BatchResult?>()
    var busy = false
        private set

    fun run(action: BatchAction, photos: List<Photo>, folder: Folder? = null) {
        if (busy || photos.isEmpty()) return
        busy = true
        viewModelScope.launch {
            val failures = withContext(Dispatchers.IO) {
                val names = try { folder?.let(library::destinationNames) } catch (_: Exception) { return@withContext photos }
                photos.filter { photo ->
                    try {
                        when (action) {
                            BatchAction.COPY, BatchAction.MOVE -> library.transfer(photo, requireNotNull(folder),
                                action == BatchAction.MOVE, requireNotNull(names))
                            BatchAction.HIDE -> library.hide(photo)
                            BatchAction.DELETE -> library.delete(photo)
                        }
                        false
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { true }
                }
            }
            busy = false
            selected = failures.map { it.id }.toSet()
            result.value = BatchResult(failures, photos.size)
        }
    }
}
