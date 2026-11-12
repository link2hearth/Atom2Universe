package com.Atom2Universe.app.gallery

import android.content.Context
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import java.io.File
import com.Atom2Universe.app.reading.CoverThumbs
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Bounded by both viewport and pixel count, including panoramas and full camera originals. */
internal object GalleryImages {
    private val thumbs = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 12).coerceAtMost(32L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun thumbnail(context: Context, photo: Photo, size: Int): Bitmap? {
        val edge = when { size <= 160 -> 160; size <= 320 -> 320; size <= 640 -> 640; else -> 1024 }
        val key = "${photo.uri}|${photo.modified}|$edge"
        thumbs.get(key)?.let { return it }
        return withContext(CoverThumbs.dispatcher) {
            thumbs.get(key) ?: run {
                // Providers may supply square, pre-cropped thumbnails. Decode the oriented frame.
                decode(context, photo.uri, edge, (edge * edge * 3).coerceAtMost(1_500_000), tile = true)
                    ?.also { thumbs.put(key, it) }
            }
        }
    }

    suspend fun screen(context: Context, uri: Uri, edge: Int): Bitmap? =
        withContext(CoverThumbs.dispatcher) { decode(context, uri, edge.coerceIn(1200, 4096), 4_500_000) }

    /** Export replays edits on a fresh decode. Allow space for source, output and compositing layers. */
    fun exportSource(context: Context, uri: Uri): Bitmap? {
        val budget = (Runtime.getRuntime().maxMemory() / 32).coerceIn(1_000_000L, 24_000_000L).toInt()
        return decode(context, uri, 8192, budget)
    }

    private fun ratio(w: Int, h: Int, edge: Int, pixels: Int, tile: Boolean): Double {
        val viewportScale = if (tile) minOf(edge.toDouble() / w, 2048.0 / maxOf(w, h))
            else edge.toDouble() / maxOf(w, h)
        return minOf(1.0, viewportScale, sqrt(pixels.toDouble() / w / h))
    }

    private fun decode(context: Context, uri: Uri, edge: Int, pixels: Int, tile: Boolean = false): Bitmap? = try {
        val resolver = context.contentResolver
        val modern = if (Build.VERSION.SDK_INT >= 28) try {
            val source = if (uri.scheme == "file") ImageDecoder.createSource(File(requireNotNull(uri.path)))
                else ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = ratio(info.size.width, info.size.height, edge, pixels, tile)
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) { null } else null
        modern ?: decodeStream(resolver, uri, edge, pixels, tile)
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    private fun decodeStream(resolver: ContentResolver, uri: Uri, edge: Int, pixels: Int, tile: Boolean): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val scale = ratio(bounds.outWidth, bounds.outHeight, edge, pixels, tile)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (inSampleSize * scale < 1.0) inSampleSize *= 2
        }
        val bitmap = requireNotNull(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) })
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        }.getOrNull()
        val transform = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
    }
}
