package com.Atom2Universe.app.games.jigsaw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.CardBacks
import java.util.concurrent.ConcurrentHashMap

/** Dedicated puzzle illustrations by default; card collections remain optional. */
object JigsawImages {
    const val DEFAULT_COLLECTION = 0
    private val extensions = listOf(".jpg", ".jpeg", ".png", ".webp")
    /** Listing assets is slow on old phones and they never change while the app runs. */
    private val listed = ConcurrentHashMap<String, List<String>>()

    class Collection(val folder: String, val labelRes: Int) {
        fun paths(context: Context): List<String> = listed.getOrPut(folder) {
            runCatching { context.assets.list(folder) }.getOrNull().orEmpty()
                .filter { name -> extensions.any { name.endsWith(it, ignoreCase = true) } }
                .sorted()
                .map { "$folder/$it" }
        }
    }

    val collections = listOf(Collection("Assets/Puzzle", R.string.jigsaw_collection_puzzle)) +
        CardBacks.families.map { Collection(it.folder, it.labelRes) }

    fun paths(context: Context, collection: Int): List<String> =
        if (collection < 0) collections.flatMap { it.paths(context) }
        else collections.getOrNull(collection)?.paths(context).orEmpty()

    fun contains(context: Context, path: String): Boolean = collections.any {
        it.folder == path.substringBeforeLast('/') && path in it.paths(context)
    }

    /** Only document URIs selected by the user, or known bundled images, are readable. */
    fun decode(context: Context, path: String): Bitmap? = runCatching {
        val photo = path.startsWith("content://")
        if (!photo && !contains(context, path)) return@runCatching null
        fun open() = if (photo) requireNotNull(context.contentResolver.openInputStream(Uri.parse(path)))
            else context.assets.open(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            // Bound camera-photo memory before allocating any pixels.
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1800) inSampleSize *= 2
        }
        val decoded = requireNotNull(open().use { BitmapFactory.decodeStream(it, null, options) })
        val orientation = runCatching {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        // Uploads the texture to the GPU now, on this worker thread, instead of during the first frame.
        upright.prepareToDraw()
        upright
    }.getOrNull()
}
