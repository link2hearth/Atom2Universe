package com.Atom2Universe.app.reading

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Vignettes de couverture communes aux livres et aux BD : décodage sous-échantillonné,
 * cache disque écrit à la taille vignette et petit cache mémoire.
 */

/** Côté le plus court visé pour une vignette de couverture, en pixels. */
const val COVER_THUMB_PX = 300

/** Plus grande puissance de 2 qui garde les deux côtés au-dessus de [minSide]. */
fun thumbSampleSize(width: Int, height: Int, minSide: Int): Int {
    if (width <= 0 || height <= 0 || minSide <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= minSide && height / (sample * 2) >= minSide) sample *= 2
    return sample
}

/**
 * Décode une image en deux passes : dimensions seules, puis pixels sous-échantillonnés.
 * [open] doit rouvrir un flux neuf à chaque appel.
 */
fun decodeSampledStream(minSide: Int, open: () -> InputStream?): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val first = open()
    if (first == null) {
        null
    } else {
        first.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = thumbSampleSize(bounds.outWidth, bounds.outHeight, minSide)
            }
            open()?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

fun decodeSampledFile(path: String, minSide: Int): Bitmap? =
    decodeSampledStream(minSide) { FileInputStream(path) }

fun decodeSampledBytes(bytes: ByteArray, minSide: Int): Bitmap? =
    decodeSampledStream(minSide) { bytes.inputStream() }

/** Rend la première page d'un PDF directement à la taille vignette. */
fun renderPdfThumb(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
        PdfRenderer(fd).use { renderer ->
            if (renderer.pageCount == 0) {
                null
            } else {
                renderer.openPage(0).use { page ->
                    val scale = COVER_THUMB_PX.toFloat() / minOf(page.width, page.height).coerceAtLeast(1)
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }
} catch (_: Exception) {
    null
}

object CoverThumbs {

    /** Deux décodages à la fois au plus : défiler une grille ne sature plus les fils d'E/S. */
    val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(key: String): Bitmap? = memory.get(key)

    fun forget(key: String) {
        memory.remove(key)
    }

    /**
     * Mémoire, puis fichier [diskFile], puis [produce] (dont le résultat est réduit
     * à la taille vignette et écrit sur disque). À appeler hors du fil de l'écran.
     */
    fun load(key: String, diskFile: File?, produce: () -> Bitmap?): Bitmap? {
        memory.get(key)?.let { return it }
        if (diskFile != null && diskFile.exists()) {
            decodeSampledFile(diskFile.path, COVER_THUMB_PX)?.let {
                memory.put(key, it)
                return it
            }
        }
        val produced = produce() ?: return null
        val thumb = shrinkToThumb(produced)
        if (diskFile != null) {
            try {
                diskFile.parentFile?.mkdirs()
                FileOutputStream(diskFile).use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } catch (_: Exception) {}
        }
        memory.put(key, thumb)
        return thumb
    }

    /** Réduit une image dont le plus petit côté dépasse nettement la taille vignette. */
    private fun shrinkToThumb(bmp: Bitmap): Bitmap {
        val minSide = minOf(bmp.width, bmp.height)
        if (minSide <= COVER_THUMB_PX * 5 / 4) return bmp
        val ratio = COVER_THUMB_PX.toFloat() / minSide
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return try { Bitmap.createScaledBitmap(bmp, w, h, true) } catch (_: Exception) { bmp }
    }
}
