package com.Atom2Universe.app.games.caves.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.World
import com.Atom2Universe.app.games.caves.world.isDecoration
import com.Atom2Universe.app.games.caves.world.isWater

internal class MinimapRenderer {

    companion object {
        const val RADIUS = 32
        private const val CAVE_UP   = 2
        private const val CAVE_DOWN = 8
        private const val SURF_UP   = 60
        private const val SURF_DOWN = 200
        private val COLOR_VOID = Color.argb(255, 10, 10, 20)
    }

    @Volatile var isCaveMode = false
        private set

    private val size = RADIUS * 2
    private val pixels = IntArray(size * size)

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val arrowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    // Flèche pointant vers le haut (nord), centrée sur (0,0), taille ~7px
    private val arrowPath = Path().apply {
        moveTo(0f, -7f)   // pointe
        lineTo(4f,  4f)   // bas-droit
        lineTo(0f,  2f)   // encoche
        lineTo(-4f, 4f)   // bas-gauche
        close()
    }

    fun render(world: World, playerX: Double, playerY: Double, playerZ: Double, yaw: Float): Bitmap {
        val px = playerX.toInt()
        val py = playerY.toInt()
        val pz = playerZ.toInt()

        isCaveMode = hasRoofAbove(world, px, py, pz)

        for (dz in -RADIUS until RADIUS)
            for (dx in -RADIUS until RADIUS) {
            pixels[(dz + RADIUS) * size + (dx + RADIUS)] =
                if (isCaveMode) columnCave(world, px + dx, pz + dz, py)
                else            columnSurface(world, px + dx, pz + dz, py)
        }

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)

        // Flèche orientée selon le regard (yaw=0 = sud, rotation = 180-yaw pour nord en haut)
        val canvas = Canvas(bmp)
        val cx = RADIUS.toFloat()
        val cz = RADIUS.toFloat()
        canvas.save()
        canvas.translate(cx, cz)
        canvas.rotate(180f - yaw)
        canvas.drawPath(arrowPath, arrowBorderPaint)
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()

        return bmp
    }

    private fun hasRoofAbove(world: World, px: Int, py: Int, pz: Int): Boolean {
        for (dy in 1..16) {
            val b = world.blockAt(px, py + dy, pz)
            if (b != AIR && !isDecoration(b)) return true
        }
        return false
    }

    private fun columnSurface(world: World, wx: Int, wz: Int, playerY: Int): Int {
        for (dy in SURF_UP downTo -SURF_DOWN) {
            val b = world.blockAt(wx, playerY + dy, wz)
            if (b != AIR && !isDecoration(b) && !isWater(b))
                return BlockRegistry.getColor(b) or 0xFF000000.toInt()
        }
        return COLOR_VOID
    }

    private fun columnCave(world: World, wx: Int, wz: Int, playerY: Int): Int {
        for (dy in CAVE_UP downTo -CAVE_DOWN) {
            val b = world.blockAt(wx, playerY + dy, wz)
            if (b != AIR && !isDecoration(b) && !isWater(b)) {
                val base = BlockRegistry.getColor(b)
                val factor = when {
                    dy > 0  -> 0.45f
                    dy == 0 -> 0.95f
                    else    -> (0.80f - (-dy - 1) * 0.07f).coerceAtLeast(0.25f)
                }
                return tint(base, factor)
            }
        }
        return COLOR_VOID
    }

    private fun tint(color: Int, factor: Float): Int {
        val r = (Color.red(color)   * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * factor).toInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }
}
