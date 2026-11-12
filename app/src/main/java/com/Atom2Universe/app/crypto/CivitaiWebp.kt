package com.Atom2Universe.app.crypto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object CivitaiWebp {
    private fun scale(w: Int, h: Int) = minOf(1.0, 2400.0 / maxOf(w, h),
        sqrt(2_000_000.0 / (w.toDouble() * h)))

    fun write(bytes: ByteArray, destination: File) {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val ratio = scale(w, h)
                decoder.setTargetSize((w * ratio).roundToInt().coerceAtLeast(1), (h * ratio).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            }
        } else decodeLegacy(bytes)
        try {
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            destination.outputStream().buffered().use {
                if (!bitmap.compress(format, 80, it)) throw IOException("WebP encoding failed")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeLegacy(bytes: ByteArray): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val w = options.outWidth
        val h = options.outHeight
        if (w <= 0 || h <= 0) throw IOException("Invalid dimensions")
        val ratio = scale(w, h)
        options.inJustDecodeBounds = false
        options.inSampleSize = Integer.highestOneBit((1.0 / ratio).toInt().coerceAtLeast(1))
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("Invalid image")
        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) ?: 1
        val transform = Matrix().apply {
            setScale((w * ratio / original.width).toFloat(), (h * ratio / original.height).toFloat())
            if (orientation in listOf(2, 4, 5, 7)) postScale(-1f, 1f)
            postRotate(when (orientation) { 3, 4 -> 180f; 5, 8 -> 270f; 6, 7 -> 90f; else -> 0f })
        }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, transform, true).also {
            if (it !== original) original.recycle()
        }
    }
}
