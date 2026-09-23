package com.Atom2Universe.app.games.roguelike

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SpriteLoader {
    /** Planche d'objets 64x64.png (16 colonnes de cases 64 px), chargée une seule fois. */
    private var sheet: Bitmap? = null
    private val cellCache = HashMap<Int, Bitmap>()

    fun sheet(assets: AssetManager): Bitmap? {
        if (sheet == null) sheet = runCatching { assets.open("64x64.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        return sheet
    }

    /** Une case de la planche, découpée et gardée en cache (pour les listes de l'inventaire). */
    fun sheetCell(assets: AssetManager, row: Int, col: Int): Bitmap? {
        val key = row * 16 + col
        cellCache[key]?.let { return it }
        val s = sheet(assets) ?: return null
        if ((row + 1) * 64 > s.height || (col + 1) * 64 > s.width) return null
        return Bitmap.createBitmap(s, col * 64, row * 64, 64, 64).also { cellCache[key] = it }
    }

    fun clear() {
        cellCache.values.forEach { it.recycle() }
        cellCache.clear()
        sheet?.recycle(); sheet = null
    }
}
