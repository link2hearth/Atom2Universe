package com.Atom2Universe.app.science.bestiary

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
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
    // Listeners of every plate queued or being decoded, guarded by itself.
    private val pending = HashMap<String, MutableList<Listener>>()
    // Plates not started yet, newest last: after a fling only the rows still on screen are decoded.
    private val queued = ArrayDeque<String>()
    private val unavailable = HashSet<String>()

    init {
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { cache.evictAll() }
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) cache.evictAll() else cache.trimToSize(cache.maxSize() / 2)
            }
        })
    }

    /** [thumbnail] plates are decoded at half resolution and cached under their own key. */
    fun peek(id: String, thumbnail: Boolean = false): Bitmap? = cache.get(key(id, thumbnail))

    /** A subscriber can leave without cancelling other views waiting for this plate. */
    fun request(id: String, thumbnail: Boolean = false, callback: (Bitmap?) -> Unit): Closeable {
        val key = key(id, thumbnail)
        val listener = Listener(callback)
        val ticket = Closeable {
            listener.cancelled.set(true)
            synchronized(pending) {
                pending[key]?.let { listeners ->
                    listeners.remove(listener)
                    // Nobody waits for this plate any more: it is not decoded.
                    if (listeners.isEmpty() && queued.remove(key)) pending.remove(key)
                }
            }
        }
        val cached = cache.get(key)
        if (cached != null) {
            main.post { if (!listener.cancelled.get()) callback(cached) }
            return ticket
        }
        synchronized(pending) {
            if (key in unavailable) {
                main.post { if (!listener.cancelled.get()) callback(null) }
                return ticket
            }
            pending[key]?.let {
                it.add(listener)
                if (queued.remove(key)) queued.addLast(key)
                return ticket
            }
            pending[key] = mutableListOf(listener)
            queued.addLast(key)
        }
        workers.execute(::decodeNewest)
        return ticket
    }

    private fun decodeNewest() {
        val key = synchronized(pending) { queued.removeLastOrNull() } ?: return
        val bitmap = if (key.endsWith(THUMBNAIL)) loadBlocking(key.removeSuffix(THUMBNAIL), true) else loadBlocking(key)
        val listeners = synchronized(pending) {
            if (bitmap == null) unavailable.add(key)
            pending.remove(key).orEmpty()
        }
        main.post { listeners.forEach { if (!it.cancelled.get()) it.callback(bitmap) } }
    }

    /** For the hub's existing background artwork worker, never call from onDraw. */
    fun loadBlocking(id: String, thumbnail: Boolean = false): Bitmap? {
        val key = key(id, thumbnail)
        cache.get(key)?.let { return it }
        if (!id.matches(ID)) return null
        val edge = if (thumbnail) THUMBNAIL_EDGE else PLATE_EDGE
        return try {
            val path = "science/bestiary/$id.webp"
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openAsset(path).use { BitmapFactory.decodeStream(it, null, dimensions) }
            if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.ARGB_8888
                while (maxOf(dimensions.outWidth, dimensions.outHeight) / inSampleSize > edge) inSampleSize *= 2
            }
            openAsset(path).use { BitmapFactory.decodeStream(it, null, options) }?.also { cache.put(key, it) }
        } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    }

    companion object {
        private const val PLATE_EDGE = 640
        private const val THUMBNAIL_EDGE = PLATE_EDGE / 2
        private const val THUMBNAIL = "#thumbnail"
        private val ID = Regex("[a-z0-9_]+")
        private fun key(id: String, thumbnail: Boolean) = if (thumbnail) id + THUMBNAIL else id

        @Volatile private var instance: AnimalImageStore? = null
        fun get(context: Context): AnimalImageStore = instance ?: synchronized(this) {
            instance ?: AnimalImageStore(context.applicationContext).also { instance = it }
        }
        /** Preserve the complete composition, even when the thumbnail has a different ratio. */
        fun drawFit(canvas: Canvas, image: Bitmap, width: Float, height: Float, paint: Paint, destination: RectF = RectF()) {
            val factor = min(width / image.width, height / image.height)
            val w = image.width * factor; val h = image.height * factor
            destination.set((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2)
            canvas.drawBitmap(image, null, destination, paint)
        }
    }
}
