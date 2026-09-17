package com.Atom2Universe.app.games.roguelike

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SpriteLoader {
    private val bitmapCache = HashMap<String, Bitmap?>()
    private val dirCache    = HashMap<String, List<String>>()

    /** Planche d'objets 64x64.png (16 colonnes de cases 64 px), chargée une seule fois. */
    private var sheet: Bitmap? = null
    private val cellCache = HashMap<Int, Bitmap>()

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

    /** Sprites provisoires : toute la partie graphique sera refaite. */
    fun monsterPath(type: MonsterType): String = when (type) {
        MonsterType.RAT      -> "Assets/sprites/Dungeon/Monsters/misc/fire_bat.png"
        MonsterType.GOBLIN   -> "Assets/sprites/Dungeon/Monsters/misc/quasit.png"
        MonsterType.SKELETON -> "Assets/sprites/Dungeon/Monsters/skeleton/skeleton_humanoid.png"
        MonsterType.ORC      -> "Assets/sprites/Dungeon/Monsters/deepdwarf/deepdwarf_berzerker.png"
        MonsterType.DEMON    -> "Assets/sprites/Dungeon/Monsters/pandemon/examples/monsters_pandemon_examples_a.png"
    }

    fun clear() {
        bitmapCache.values.forEach { it?.recycle() }
        bitmapCache.clear()
        dirCache.clear()
        cellCache.values.forEach { it.recycle() }
        cellCache.clear()
        sheet?.recycle(); sheet = null
    }
}
