package com.Atom2Universe.app.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class EditTab { FRAME, LIGHT, LOOK, SELECT, CUTOUT, DRAW }
internal enum class EditTool { PAN, CROP, RECT, LASSO, WAND, SUBJECT, ADD, SUBTRACT, BACKGROUND, BRUSH, ERASER }

internal class PhotoEditorModel(app: Application) : AndroidViewModel(app) {
    private data class Step(val edit: PhotoEdit?, val selection: PhotoMask?)
    private val steps = mutableListOf<Step>()
    private var cursor = 0
    private var source: Bitmap? = null
    private var sourcePixels = 0L
    private var originalUri: Uri? = null
    private val library = GalleryLibrary.get(app)
    var exportOptions = PhotoExportOptions()
    val updates = MutableLiveData(0)
    var preview: Bitmap? = null
        private set
    val selection get() = steps.getOrNull(cursor-1)?.selection
    val canUndo get() = cursor > 0
    val canRedo get() = cursor < steps.size
    val dirty get() = (0 until cursor).any { steps[it].edit != null } || draft != null
    var tab = EditTab.LIGHT
    var tool = EditTool.PAN
    var channel = 0
    val adjustments = IntArray(3)
    var look: PhotoLook? = null
    var brushSize = 4
    var tolerance = 36
    var ink = Color.WHITE
    val draft: PhotoEdit.Tone? get() {
        val matrix = when {
            tab == EditTab.LIGHT && adjustments.any { it != 0 } -> toneMatrix(adjustments[0],adjustments[1],adjustments[2])
            tab == EditTab.LOOK -> look?.matrix()
            else -> null
        }
        return matrix?.let { PhotoEdit.Tone(it,selection) }
    }
    var busy = false
        private set
    var error = false
    var prepared: Bitmap? = null
        private set
    var reduced = false
        private set
    var writableParent: Uri? = null
        private set
    var saved: Photo? = null
        private set

    private fun notifyUi() { updates.value = (updates.value ?: 0)+1 }
    private fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        notifyUi()
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true }
            catch (_: OutOfMemoryError) { error = true }
            finally { busy = false; notifyUi() }
        }
    }
    fun open(uri: Uri, parent: Uri?) {
        if (originalUri != null) return
        originalUri = uri
        work {
            val app = getApplication<Application>()
            source = requireNotNull(GalleryImages.screen(app, uri, 1600))
            preview = source
            withContext(Dispatchers.IO) {
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                runCatching { app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,size) } }
                sourcePixels = size.outWidth.toLong()*size.outHeight
                writableParent = parent?.takeIf { runCatching { library.canCreateIn(it) }.getOrDefault(false) }
            }
        }
    }

    private fun append(edit: PhotoEdit?, mask: PhotoMask?) {
        steps.subList(cursor,steps.size).clear()
        steps.add(Step(edit, mask))
        cursor++
    }

    fun selectMask(make: (Bitmap) -> PhotoMask?) = work {
        val current = requireNotNull(preview)
        val mask = withContext(Dispatchers.Default) { make(current) }
        append(null, mask)
    }

    fun edit(op: PhotoEdit, retainMask: Boolean = true) = work {
        val current = requireNotNull(preview)
        val output = withContext(Dispatchers.Default) { PhotoRender.apply(current,op) }
        append(op, if (retainMask) selection else null)
        preview = output
    }

    fun removeBackground(trace: Trace) = work {
        val current = requireNotNull(preview)
        val op = withContext(Dispatchers.Default) { PhotoEdit.Cut(PhotoSelections.wand(current,trace.points.first(),tolerance),false) }
        val output = withContext(Dispatchers.Default) { PhotoRender.apply(current,op) }
        append(op,null)
        preview = output
    }

    fun clearDraft() { adjustments.fill(0); look = null }

    /** Intermediate export/replay images belong only to this worker, except the starting bitmap. */
    private fun replay(start: Bitmap, edits: List<PhotoEdit>): Bitmap {
        var image = start
        for (edit in edits) {
            val next = PhotoRender.apply(image,edit)
            if (image !== start && image !== next) image.recycle()
            image = next
        }
        return image
    }
    fun history(direction: Int) {
        if (busy) return
        if (draft != null) { clearDraft(); notifyUi(); return }
        val next = cursor + direction
        if (next !in 0..steps.size) return
        work {
            if (steps[minOf(next,cursor)].edit != null) {
                val edits = steps.take(next).mapNotNull { it.edit }
                preview = withContext(Dispatchers.Default) { replay(requireNotNull(source), edits) }
            }
            cursor = next
        }
    }
    fun prepare() = work {
        val edits = steps.take(cursor).mapNotNull { it.edit } + listOfNotNull(draft)
        val (output, scaled) = withContext(Dispatchers.IO) {
            val full = requireNotNull(GalleryImages.exportSource(getApplication(), requireNotNull(originalUri)))
            val resized = sourcePixels > full.width.toLong()*full.height
            val bitmap = replay(full,edits)
            if (bitmap !== full) full.recycle()
            bitmap to resized
        }
        prepared = output
        reduced = scaled
    }
    fun cancelExport() { if (!busy) { prepared?.recycle(); prepared = null } }
    fun write(parent: Uri, title: String) = work {
        val bitmap = requireNotNull(prepared)
        val options = exportOptions
        saved = withContext(Dispatchers.IO) { library.saveEdit(parent,title,bitmap,options) }
        bitmap.recycle()
        prepared = null
    }
}
