package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.IOException

/** Editable, bundled PNG overrides. Missing or invalid files use the original pixel recipes. */
internal object TexturePack {
    const val ROOT = "caves/textures/pack"
    private const val MAX_SIZE = 2048
    private const val HEX = "0123456789ABCDEF"

    /** Reversible names: ':' separates folders; other punctuation is encoded without collisions. */
    fun relativePath(key: String): String = key.split(':').joinToString("/") { component ->
        buildString {
            for (byte in component.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 255
                if (c in 65..90 || c in 97..122 || c in 48..57 || c == 95 || c == 45) append(c.toChar())
                else { append('%'); append(HEX[c ushr 4]); append(HEX[c and 15]) }
            }
        }
    } + ".png"

    fun load(assets: AssetManager, key: String, size: Int, vivid: Boolean, fallback: () -> Bitmap): Bitmap {
        require(size > 0)
        val path = "$ROOT/${relativePath(key)}"
        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth !in 1..MAX_SIZE || bounds.outHeight != bounds.outWidth) {
                Log.w("CaveTexturePack", "Invalid square PNG dimensions: $path; using code fallback")
                null
            } else {
                assets.open(path).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
                }.also { if (it == null) Log.w("CaveTexturePack", "Cannot decode $path; using code fallback") }
            }
        } catch (_: IOException) {
            // Packs may intentionally override only a few textures.
            null
        }
        if (bitmap == null) return fallback()
        val colored = if (vivid && usesVividPalette(key)) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (i in pixels.indices) if (pixels[i] ushr 24 != 0) pixels[i] = CavePalette.vivid(pixels[i])
            Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { bitmap.recycle() }
        } else bitmap
        if (colored.width == size) return colored
        // No smoothing: native 32px/48px silhouettes remain sharp in the shared 96px atlas.
        return Bitmap.createScaledBitmap(colored, size, size, false).also { colored.recycle() }
    }

    private fun usesVividPalette(key: String) = key.startsWith("cozy:") ||
        key.startsWith("base_art:") || key in MeadowTextures.itemTextureNames
}
