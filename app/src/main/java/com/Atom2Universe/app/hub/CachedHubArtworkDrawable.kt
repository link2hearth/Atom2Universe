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
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

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
         * De quoi garder toute la grille des jeux : une cinquantaine de tuiles carrées d'environ
         * 1 Mio chacune (512 px plafonné). Trop petit, le cache évince les dernières tuiles qui
         * se recuisent à chaque défilement : elles apparaissent puis disparaissent. Les pixels
         * d'un Bitmap vivent hors du tas Java : le plafond suit donc le quart du tas, pas moins.
         */
        private val images = object : LruCache<Key, Bitmap>(
            minOf(96L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4).toInt()
        ) {
            override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
        }

        /** Quelques ouvriers en basse priorité, servis dans l'ordre d'arrivée (tuiles visibles d'abord). */
        private val worker = Executors.newFixedThreadPool(3) { task ->
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
        private val pending = HashMap<Key, MutableSet<CachedHubArtworkDrawable>>()
        /** Décors dont la cuisson a échoué : on n'insiste pas à chaque image. */
        private val failed = HashSet<Key>()

        private fun keyFor(type: Class<*>, width: Int, height: Int): Key {
            val scale = min(1f, 512f / maxOf(width, height))
            return Key(type, (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1))
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
            bakeAsync(key, null) { type.getConstructor(Context::class.java).newInstance(context) as CachedHubArtworkDrawable }
        }

        private fun bakeAsync(key: Key, waiter: CachedHubArtworkDrawable?, source: () -> CachedHubArtworkDrawable) {
            synchronized(pending) {
                if (key in failed) return
                val waiting = pending[key]
                if (waiting != null) {
                    if (waiter != null) waiting.add(waiter)
                    return
                }
                pending[key] = LinkedHashSet<CachedHubArtworkDrawable>().also { if (waiter != null) it.add(waiter) }
            }
            worker.execute {
                var ok = false
                try {
                    if (images.get(key) == null) {
                        val bitmap = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
                        source().render(Canvas(bitmap), key.width.toFloat(), key.height.toFloat())
                        images.put(key, bitmap)
                    }
                    ok = true
                } catch (_: Throwable) {
                    // Un décor raté laisse la tuile sur son fond plutôt que de faire tomber l'appli.
                }
                val waiting = synchronized(pending) {
                    if (!ok) failed.add(key)
                    pending.remove(key).orEmpty()
                }
                if (waiting.isNotEmpty()) main.post { waiting.forEach { it.invalidateSelf() } }
            }
        }
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    final override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val key = keyFor(javaClass, bounds.width(), bounds.height())
        val bitmap = images.get(key)
        if (bitmap == null) {
            bakeAsync(key, this) { this }
            return
        }
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    protected abstract fun render(canvas: Canvas, w: Float, h: Float)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
