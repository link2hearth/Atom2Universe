package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

/** Décor de tuile composé avec les mêmes arts de sol et d'acteurs que la carte du jeu. */
class DungeonHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val mapArt = DungeonMapArt()
    private val actors = DungeonCombatArt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val hero = Hero()

    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFF101820.toInt())
        val tile = h / 4f
        val columns = (w / tile).toInt() + 2
        val rows = 5
        for (row in 0 until rows) for (col in 0 until columns) {
            val x = (col - .5f) * tile
            val y = row * tile
            rect.set(x, y, x + tile, y + tile)
            val wall = row == 0 || col == 0 || col == columns - 1
            mapArt.tile(canvas, rect, DungeonTheme.DUNGEON,
                if (wall) TileType.WALL else TileType.FLOOR, col, row, 15)
        }

        // Torches use the same wall tile art as an in-game dungeon level.
        val torchY = tile * .08f
        for (x in listOf(w * .22f, w * .78f)) {
            rect.set(x - tile * .45f, torchY, x + tile * .45f, torchY + tile * .9f)
            mapArt.tile(canvas, rect, DungeonTheme.DUNGEON, TileType.WALL,
                (x / tile).toInt(), 0, 15)
        }

        // Stairs are a real dungeon floor tile, and the actors use the in-game sprite renderer.
        rect.set(w * .40f, h * .55f, w * .60f, h * .77f)
        mapArt.tile(canvas, rect, DungeonTheme.DUNGEON, TileType.STAIRS_DOWN, 4, 3, 15)

        val heroRect = RectF(w * .45f, h * .40f, w * .55f, h * .62f)
        actors.drawShadow(canvas, heroRect, hero = true)
        actors.drawHero(canvas, heroRect, hero)

        drawMonster(canvas, RectF(w * .24f, h * .43f, w * .35f, h * .65f), MonsterType.SKELETON, 0)
        drawMonster(canvas, RectF(w * .65f, h * .41f, w * .77f, h * .65f), MonsterType.GOBLIN, 1)

        // Assombrit la bande de titre comme les autres tuiles illustrées du hub.
        paint.shader = android.graphics.LinearGradient(0f, h * .55f, 0f, h,
            0x00101820, 0xD9101820.toInt(), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .55f, w, h, paint)
        paint.shader = null
        // Redraw actors above the title shade to retain sprite contrast.
        actors.drawShadow(canvas, heroRect, hero = true)
        actors.drawHero(canvas, heroRect, hero)
        drawMonster(canvas, RectF(w * .24f, h * .43f, w * .35f, h * .65f), MonsterType.SKELETON, 0)
        drawMonster(canvas, RectF(w * .65f, h * .41f, w * .77f, h * .65f), MonsterType.GOBLIN, 1)
    }

    private fun drawMonster(canvas: Canvas, bounds: RectF, type: MonsterType, index: Int) {
        actors.prepareMapPack(MonsterPack(listOf(type), Pos(index + 2, 1)))
        actors.drawShadow(canvas, bounds)
        actors.drawMonster(canvas, bounds, type, actorIndex = index)
    }
}
