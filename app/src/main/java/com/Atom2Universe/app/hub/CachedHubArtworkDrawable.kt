package com.Atom2Universe.app.hub

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
         * De quoi garder toute la grille des jeux : une quarantaine de tuiles carrées d'environ
         * 0,9 Mio chacune. À 2 Mio, le cache ne tenait que deux tuiles et chaque défilement
         * redessinait les décors. Borné au huitième de la mémoire de l'appli sur un petit appareil.
         */
        private val images = object : LruCache<Key, Bitmap>(
            minOf(40 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 8).toInt())
        ) {
            override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
        }

        /** Un seul ouvrier, en basse priorité : les tuiles visibles sont servies dans l'ordre. */
        private val worker = Executors.newSingleThreadExecutor { task ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "hub-artwork")
        }
        private val main = Handler(Looper.getMainLooper())

        /** Décors en cours de cuisson, pour ne jamais lancer deux fois le même. */
        private val pending = HashSet<Key>()
        /** Décors dont la cuisson a échoué : on n'insiste pas à chaque image. */
        private val failed = HashSet<Key>()
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    final override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val scale = min(1f, 512f / maxOf(bounds.width(), bounds.height()))
        val w = (bounds.width() * scale).roundToInt().coerceAtLeast(1)
        val h = (bounds.height() * scale).roundToInt().coerceAtLeast(1)
        val key = Key(javaClass, w, h)
        val bitmap = images.get(key)
        if (bitmap == null) {
            bake(key, w, h)
            return
        }
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    private fun bake(key: Key, w: Int, h: Int) {
        synchronized(pending) {
            if (key in failed || !pending.add(key)) return
        }
        worker.execute {
            var ok = false
            try {
                if (images.get(key) == null) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    render(Canvas(bitmap), w.toFloat(), h.toFloat())
                    images.put(key, bitmap)
                }
                ok = true
            } catch (_: Throwable) {
                // Un décor raté laisse la tuile sur son fond plutôt que de faire tomber l'appli.
            } finally {
                synchronized(pending) {
                    pending.remove(key)
                    if (!ok) failed.add(key)
                }
            }
            main.post { invalidateSelf() }
        }
    }

    protected abstract fun render(canvas: Canvas, w: Float, h: Float)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
