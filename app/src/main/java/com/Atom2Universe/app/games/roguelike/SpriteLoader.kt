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
    }
}
