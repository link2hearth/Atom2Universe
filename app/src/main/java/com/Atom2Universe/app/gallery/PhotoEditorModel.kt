package com.Atom2Universe.app.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal enum class EditTab { FRAME, LIGHT, LOOK, SELECT, CUTOUT, DRAW }
internal enum class EditTool { PAN, CROP, RECT, OVAL, LASSO, WAND, SUBJECT, ADD, SUBTRACT, BACKGROUND, BRUSH, ERASER, CLONE, HEAL, PICKER }

/** [value] is the long side over the short side; the crop frame follows the picture's orientation. */
internal enum class CropRatio(val label: Int, private val value: Float) {
    FREE(R.string.photo_ratio_free, 0f), ORIGINAL(R.string.photo_ratio_original, 0f),
    SQUARE(R.string.photo_ratio_square, 1f), R4_3(R.string.photo_ratio_4_3, 4f/3f),
    R3_2(R.string.photo_ratio_3_2, 1.5f), R16_9(R.string.photo_ratio_16_9, 16f/9f);

    /** Width over height in pixels for an image of the given [aspect], or null when unconstrained. */
    fun of(aspect: Float): Float? = when (this) {
        FREE -> null
        ORIGINAL -> aspect
        else -> if (aspect >= 1f) value else 1f / value
    }
}

internal class PhotoEditorModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        /** Recent previews kept for instant undo (about 8 MB each). */
        const val SNAPSHOTS = 5
        const val PROXY_EDGE = 800
    }
    private data class Step(val edit: PhotoEdit?, val selection: PhotoMask?)
    private val steps = mutableListOf<Step>()
    private var cursor = 0
    private val snapshots = HashMap<Int, Bitmap>()
    private var proxy: Bitmap? = null
    private var proxyOf: Bitmap? = null
    private var source: Bitmap? = null
    private var sourcePixels = 0L
    private var originalUri: Uri? = null
    private val library = GalleryLibrary.get(app)
    var exportOptions = PhotoExportOptions()
    /** Structural changes: the tool rows are rebuilt. */
    val updates = MutableLiveData(0)
    /** Light changes (a slider or a handle moved): only the picture and the top buttons follow. */
    val frames = MutableLiveData(0)
    var preview: Bitmap? = null
        private set
    val original get() = source
    val selection get() = steps.getOrNull(cursor-1)?.selection
    val canUndo get() = cursor > 0
    val canRedo get() = cursor < steps.size
    val dirty get() = (0 until cursor).any { steps[it].edit != null } || draft != null
    var tab = EditTab.LIGHT
    var tool = EditTool.PAN
    var channel = AdjustChannel.LIGHT
    val adjustments = IntArray(AdjustChannel.entries.size)
    var look: PhotoLook? = null
    var lookStrength = 100
    var angle = 0
    val crop = RectF(0f, 0f, 1f, 1f)
    var ratio = CropRatio.FREE
        private set
    var brushSize = 4
    var opacity = 100
    var softness = 0
    var tolerance = 36
    var ink = Color.WHITE
    /** Clone stamp: the point to copy from, then the fixed shift once the first stroke starts. */
    var cloneSource: PointF? = null
    var cloneOffset: PointF? = null
    /** While held, the canvas shows the untouched photo. */
    var compare = false
    /** Live rendering of an [PhotoEdit.Adjust] draft; other drafts are drawn directly by the canvas. */
    var draftImage: Bitmap? = null
        private set
    private var rendering = false
    private var stale = false

    /** The setting being tuned in the current tab, shown live and applied on demand or on leaving the tab. */
    val draft: PhotoEdit? get() = when (tab) {
        EditTab.FRAME -> if (angle != 0 || crop.left > 0f || crop.top > 0f || crop.right < 1f || crop.bottom < 1f)
            PhotoEdit.Frame(angle.toFloat(), RectF(crop)) else null
        EditTab.LIGHT -> if (adjustments.any { it != 0 }) PhotoEdit.Adjust(adjustments.toList(), selection) else null
        EditTab.LOOK -> look?.let { PhotoEdit.Tone(it.matrix().faded(lookStrength / 100f), selection) }
        else -> null
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
    fun touch() { frames.value = (frames.value ?: 0)+1 }
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
        snapshots.keys.removeAll { it > cursor }
        steps.add(Step(edit, mask))
        cursor++
    }

    /** Remembers the preview reached at [cursor], so undoing a recent step replays little or nothing. */
    private fun remember(image: Bitmap) {
        snapshots[cursor] = image
        while (snapshots.size > SNAPSHOTS) snapshots.remove(snapshots.keys.maxBy { abs(it - cursor) })
    }

    fun selectMask(make: (Bitmap) -> PhotoMask?) = work {
        val current = requireNotNull(preview)
        val mask = withContext(Dispatchers.Default) { make(current) }
        append(null, mask)
    }

    /** A change of geometry makes the selection and the clone source meaningless. */
    private suspend fun commit(op: PhotoEdit, retainMask: Boolean) {
        val current = requireNotNull(preview)
        val output = withContext(Dispatchers.Default) { PhotoRender.apply(current,op) }
        val geometry = op is PhotoEdit.Frame || op == PhotoEdit.Rotate || op == PhotoEdit.Mirror
        append(op, if (retainMask && !geometry) selection else null)
        if (geometry) { cloneSource = null; cloneOffset = null }
        preview = output
        remember(output)
    }

    fun edit(op: PhotoEdit, retainMask: Boolean = true) = work { commit(op, retainMask) }

    /** Applies the pending draft, then [next] if given. */
    fun applyDraft(next: PhotoEdit? = null, retainMask: Boolean = true) {
        val pending = draft
        clearDraft()
        if (pending == null && next == null) return
        work {
            if (pending != null) commit(pending, true)
            if (next != null) commit(next, retainMask)
        }
    }

    fun auto() {
        val pending = draft
        clearDraft()
        work {
            if (pending != null) commit(pending, true)
            val current = requireNotNull(preview)
            val mask = selection
            withContext(Dispatchers.Default) { PhotoRender.autoLevels(current, mask) }?.let { commit(it, true) }
        }
    }

    fun removeBackground(trace: Trace) = work {
        val current = requireNotNull(preview)
        val op = withContext(Dispatchers.Default) { PhotoEdit.Cut(PhotoSelections.wand(current,trace.points.first(),tolerance),false) }
        commit(op, false)
    }

    /** Fits the crop frame to [next]: the largest centred frame of that shape. */
    fun shape(next: CropRatio) {
        ratio = next
        val image = preview ?: return
        val aspect = image.width.toFloat() / image.height
        val n = (next.of(aspect) ?: return) / aspect
        val w = if (n >= 1f) 1f else n
        val h = if (n >= 1f) 1f / n else 1f
        crop.set((1f-w)/2, (1f-h)/2, (1f+w)/2, (1f+h)/2)
    }

    fun clearDraft() {
        adjustments.fill(0); look = null; lookStrength = 100
        angle = 0; crop.set(0f, 0f, 1f, 1f); ratio = CropRatio.FREE
        draftImage = null
    }

    /** Small copy of the preview: adjustments follow the finger on it, the sharp pass comes after. */
    private fun proxyFor(image: Bitmap): Bitmap {
        proxy?.takeIf { proxyOf === image }?.let { return it }
        val scale = PROXY_EDGE.toFloat() / maxOf(image.width, image.height)
        val small = if (scale >= 0.8f) image else Bitmap.createScaledBitmap(image,
            (image.width*scale).toInt().coerceAtLeast(1), (image.height*scale).toInt().coerceAtLeast(1), true)
        proxy = small
        proxyOf = image
        return small
    }

    /** Recomputes the live adjustment; while one is running, only the latest request is kept. */
    fun refreshDraft() = render(false)
    private fun render(sharp: Boolean) {
        val edit = draft as? PhotoEdit.Adjust
        val image = preview
        if (edit == null || image == null) { draftImage = null; touch(); return }
        if (rendering) { stale = true; return }
        rendering = true
        viewModelScope.launch {
            var quick = false
            val output = try {
                withContext(Dispatchers.Default) {
                    val base = if (sharp) image else proxyFor(image)
                    quick = base !== image
                    PhotoRender.apply(base, edit)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            rendering = false
            if (output != null && preview === image && draft is PhotoEdit.Adjust) draftImage = output
            touch()
            if (stale) { stale = false; render(false) }
            else if (quick && output != null) render(true)
        }
    }

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
            val crossed = steps[minOf(next,cursor)].edit != null
            if (crossed) {
                // Start from the closest remembered image at or before the target.
                val from = snapshots.keys.filter { it <= next }.maxOrNull() ?: 0
                val start = snapshots[from] ?: requireNotNull(source)
                val edits = steps.subList(from,next).mapNotNull { it.edit }
                val image = withContext(Dispatchers.Default) { replay(start, edits) }
                cursor = next
                preview = image
                remember(image)
                cloneSource = null; cloneOffset = null
            } else cursor = next
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
