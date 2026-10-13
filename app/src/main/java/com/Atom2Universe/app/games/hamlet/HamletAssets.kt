package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.InputStream

/** Six décors WebP. Bâtiments et personnages sont dessinés en code dans leurs propres caches. */
class HamletAssets(private val open: (String) -> InputStream) {
    constructor(context: Context) : this({ context.assets.open("hamlet/adventure/$it.webp") })
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val missing = HashSet<String>()
    fun get(kind: String, index: Int): Bitmap? {
        if (kind != "region" || index !in 0..5) return null
        val key = "${kind}_$index"
        cache.get(key)?.let { return it }
        if (key in missing) return null
        val bitmap = try { open(key).use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        if (bitmap == null) missing.add(key) else cache.put(key, bitmap)
        return bitmap
    }
    fun clear() { cache.evictAll(); missing.clear() }
}
