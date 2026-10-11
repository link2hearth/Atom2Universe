package com.Atom2Universe.app.science.bestiary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** Offline plates. Decodes are bounded, shared and performed outside the UI thread. */
class AnimalImageStore internal constructor(
    context: Context,
    private val openAsset: (String) -> InputStream = { context.applicationContext.assets.open(it) }
) {
    private val cache = object : LruCache<String, Bitmap>(
        minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 12).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        // Eviction must not recycle a bitmap still displayed by a View.
    }
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "bestiary-image").apply { isDaemon = true }
    }
    private class Listener(val callback: (Bitmap?) -> Unit) { val cancelled = AtomicBoolean(false) }
    private val pending = HashMap<String, MutableList<Listener>>()
    private val unavailable = HashSet<String>()

    fun peek(id: String): Bitmap? = cache.get(id)

    /** A subscriber can leave without cancelling other views waiting for this plate. */
    fun request(id: String, callback: (Bitmap?) -> Unit): Closeable {
        val listener = Listener(callback)
        val ticket = Closeable {
            listener.cancelled.set(true)
            synchronized(pending) { pending[id]?.remove(listener) }
        }
        val cached = peek(id)
        if (cached != null) {
            main.post { if (!listener.cancelled.get()) callback(cached) }
            return ticket
        }
        synchronized(pending) {
            if (id in unavailable) {
                main.post { if (!listener.cancelled.get()) callback(null) }
                return ticket
            }
            pending[id]?.let { it.add(listener); return ticket }
            pending[id] = mutableListOf(listener)
        }
        workers.execute {
            val bitmap = loadBlocking(id)
            val listeners = synchronized(pending) {
                if (bitmap == null) unavailable.add(id)
                pending.remove(id).orEmpty()
            }
            main.post { listeners.forEach { if (!it.cancelled.get()) it.callback(bitmap) } }
        }
        return ticket
    }

    /** For the hub's existing background artwork worker, never call from onDraw. */
    fun loadBlocking(id: String): Bitmap? {
        peek(id)?.let { return it }
        if (!id.matches(Regex("[a-z0-9_]+"))) return null
        return try {
            val path = "bestiary/$id.webp"
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openAsset(path).use { BitmapFactory.decodeStream(it, null, dimensions) }
            if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.ARGB_8888
                while (maxOf(dimensions.outWidth, dimensions.outHeight) / inSampleSize > 640) inSampleSize *= 2
            }
            openAsset(path).use { BitmapFactory.decodeStream(it, null, options) }?.also { cache.put(id, it) }
        } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    }

    companion object {
        @Volatile private var instance: AnimalImageStore? = null
        fun get(context: Context): AnimalImageStore = instance ?: synchronized(this) {
            instance ?: AnimalImageStore(context.applicationContext).also { instance = it }
        }
        /** Preserve the complete composition, even when the thumbnail has a different ratio. */
        fun drawFit(canvas: Canvas, image: Bitmap, width: Float, height: Float, paint: Paint) {
            val factor = min(width / image.width, height / image.height)
            val w = image.width * factor; val h = image.height * factor
            canvas.drawBitmap(image, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), paint)
        }
    }
}
