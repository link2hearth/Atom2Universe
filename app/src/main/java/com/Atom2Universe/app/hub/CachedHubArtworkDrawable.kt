package com.Atom2Universe.app.hub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.LruCache
import android.util.Log
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Décors fixes : cache commun borné, réutilisé après recréation du hub, sans activité retenue.
 *
 * Le décor se cuit **hors du fil d'interface** : certains (Accrétion décode des textures et
 * projette des sphères pixel par pixel) coûtent assez pour faire saccader le défilement s'ils se
 * dessinaient au moment où la tuile apparaît. Tant que l'image n'est pas prête, la tuile montre
 * son fond ; elle se redessine toute seule dès que l'image arrive.
 */
abstract class CachedHubArtworkDrawable : Drawable() {
    private data class Key(val type: Class<*>, val width: Int, val height: Int)
    companion object {
        /**
         * Cache partagé borné. À 384 px, cent carrés ARGB occupent au plus 56,25 Mio,
         * contre 100 Mio à 512 px. Les vues gardent aussi leur image tant qu'elles l'affichent.
         */
        private val images = object : LruCache<Key, Bitmap>(
            minOf(96L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4).toInt()
        ) {
            override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
        }

        private val sequence = AtomicLong()
        /** Les demandes visibles passent devant le préchargement, même déjà en file. */
        private val worker = ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
            PriorityBlockingQueue<Runnable>()) { task ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "hub-artwork")
        }
        private val main = Handler(Looper.getMainLooper())

        /**
         * Décors en cuisson (clé → tuiles qui attendent l'image), pour ne jamais lancer deux fois
         * le même et prévenir chaque tuile à l'arrivée. Protégé par son propre verrou.
         */
        private val pending = HashMap<Key, BakeTask>()
        /** Décors dont la cuisson a échoué : on n'insiste pas à chaque image. */
        private val failed = HashSet<Key>()

        private fun keyFor(type: Class<*>, width: Int, height: Int): Key {
            val size = HubArtworkSize.forBounds(width, height)
            return Key(type, size.first, size.second)
        }

        /**
         * Lance la cuisson de tous les décors d'un hub dès que la taille d'une tuile est connue,
         * hors du fil d'interface, sans attendre que chaque tuile entre à l'écran en défilant.
         * La taille doit être celle du dessin affiché : une autre clé cuirait pour rien.
         */
        fun prefetch(context: Context, type: Class<out Drawable>, width: Int, height: Int) {
            if (width <= 0 || height <= 0 || !CachedHubArtworkDrawable::class.java.isAssignableFrom(type)) return
            val key = keyFor(type, width, height)
            if (images.get(key) != null) return
            val appContext = context.applicationContext
            bakeAsync(key, null) { type.getConstructor(Context::class.java).newInstance(appContext) as CachedHubArtworkDrawable }
        }

        private fun bakeAsync(key: Key, waiter: CachedHubArtworkDrawable?, source: () -> CachedHubArtworkDrawable) {
            synchronized(pending) {
                if (key in failed) return
                val waiting = pending[key]
                if (waiting != null) {
                    if (waiter != null) {
                        waiting.waiters.add(waiter)
                        if (waiting.priority != 0 && worker.remove(waiting)) {
                            waiting.priority = 0
                            worker.execute(waiting)
                        }
                    }
                    return
                }
                val task = BakeTask(key, source, if (waiter == null) 1 else 0)
                if (waiter != null) task.waiters.add(waiter)
                pending[key] = task
                worker.execute(task)
            }
        }

        private class BakeTask(
            private val key: Key,
            private val source: () -> CachedHubArtworkDrawable,
            var priority: Int
        ) : Runnable, Comparable<BakeTask> {
            private val order = sequence.getAndIncrement()
            val waiters = LinkedHashSet<CachedHubArtworkDrawable>()

            override fun compareTo(other: BakeTask): Int =
                compareValues(priority, other.priority).takeIf { it != 0 } ?: order.compareTo(other.order)

            override fun run() {
                var result: Bitmap? = null
                try {
                    result = images.get(key) ?: run {
                        val bitmap = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
                        source().render(Canvas(bitmap), key.width.toFloat(), key.height.toFloat())
                        images.put(key, bitmap)
                        bitmap
                    }
                } catch (error: Throwable) {
                    // Un décor raté laisse la tuile sur son fond plutôt que de faire tomber l'appli.
                    Log.w("HubArtwork", "Cannot render ${key.type.simpleName} (${key.width}x${key.height})", error)
                }
                val waiting = synchronized(pending) {
                    if (result == null) failed.add(key)
                    pending.remove(key)
                    waiters.toList()
                }
                val bitmap = result
                if (waiting.isNotEmpty()) main.post {
                    waiting.forEach {
                        if (!it.bounds.isEmpty && keyFor(it.javaClass, it.bounds.width(), it.bounds.height()) == key) {
                            it.displayedKey = key
                            it.displayedBitmap = bitmap
                            it.invalidateSelf()
                        }
                    }
                }
            }
        }
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var displayedKey: Key? = null
    private var displayedBitmap: Bitmap? = null

    final override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val key = keyFor(javaClass, bounds.width(), bounds.height())
        if (displayedKey != key) {
            displayedKey = key
            displayedBitmap = null
        }
        val bitmap = displayedBitmap ?: images.get(key)
        if (bitmap == null) {
            bakeAsync(key, this) { this }
            return
        }
        displayedBitmap = bitmap
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    protected abstract fun render(canvas: Canvas, w: Float, h: Float)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
