package com.Atom2Universe.app.games.roguelike

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SpriteLoader {
    private val bitmapCache = HashMap<String, Bitmap?>()
    private val dirCache    = HashMap<String, List<String>>()

    fun listDir(assets: AssetManager, dir: String): List<String> =
        dirCache.getOrPut(dir) {
            (assets.list(dir) ?: emptyArray())
                .filter { it.endsWith(".png") }
                .sorted()
                .map { "$dir/$it" }
        }

    fun load(assets: AssetManager, path: String): Bitmap? =
        bitmapCache.getOrPut(path) {
            runCatching { assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }

    fun clear() {
        bitmapCache.values.forEach { it?.recycle() }
        bitmapCache.clear()
        dirCache.clear()
    }
}
