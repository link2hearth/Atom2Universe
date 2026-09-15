package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Quelques sprites seulement, décodés lors de la création du décor mis en cache. */
class DungeonHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val assets = context.applicationContext.assets

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint()
        val base = "Assets/sprites/Dungeon"
        val paths = listOf(
            "$base/floor/brick/mossy/floor_brick_mossy_a.png",
            "$base/wall/brick/mossy/wall_brick_mossy_a.png",
            "$base/Heros/paperdoll_example_01.png",
            "$base/Monsters/skeleton/skeleton_humanoid.png",
            "$base/Monsters/misc/quasit.png"
        )
        // Cache local au rendu : aucune dépendance au cache du jeu, qui peut être vidé en jouant.
        val sprites = paths.map { path -> assets.open(path).use { BitmapFactory.decodeStream(it) } }
        try {
            canvas.drawColor(0xFF17191C.toInt())
            val cell = h * .23f
            fun sprite(index: Int, x: Float, y: Float, size: Float) {
                sprites[index]?.let { canvas.drawBitmap(it, null, RectF(x, y, x + size, y + size), paint) }
            }
            for (row in 0..4) for (col in 0..(w / cell).toInt()) {
                sprite(if (row == 0) 1 else 0, col * cell, row * cell, cell)
            }
            // Éclairage chaud au centre, sol plus sombre derrière le titre.
            paint.shader = RadialGradient(w * .5f, h * .27f, w * .55f,
                0x40FFC575, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            val size = min(w * .23f, h * .40f)
            sprite(3, w * .23f - size / 2f, h * .22f, size)
            sprite(4, w * .78f - size / 2f, h * .23f, size)
            sprite(2, w * .5f - size / 2f, h * .18f, size)
            paint.shader = LinearGradient(0f, h * .48f, 0f, h,
                Color.TRANSPARENT, 0xF0101319.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h * .48f, w, h, paint)
        } finally {
            sprites.forEach { it?.recycle() }
        }
    }
}
