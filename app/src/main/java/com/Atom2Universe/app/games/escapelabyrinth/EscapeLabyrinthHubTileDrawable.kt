package com.Atom2Universe.app.games.escapelabyrinth

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

/** Aperçu fixe du vrai rendu Canvas, comme la tuile PipeTap. */
class EscapeLabyrinthHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val appContext = context.applicationContext

    override fun render(canvas: Canvas, w: Float, h: Float) {
        // Composition dédiée : aucun générateur ni solveur lancé depuis le hub.
        val rows = arrayOf(
            ".......",
            ".#.#.#.",
            "...#...",
            ".###.#.",
            ".......",
            ".#.###.",
            "...#..."
        )
        val grid = Array(7) { r -> Array(7) { c ->
            if (rows[r][c] == '#') TileType.WALL else TileType.FLOOR
        } }
        val start = 1 to 1
        val exit = 0 to 3
        val bonuses = listOf(BonusOrb(0, 0, 1), BonusOrb(1, 2, 2), BonusOrb(2, 3, 0))
        grid[start.first * 2][start.second * 2] = TileType.START
        grid[exit.first * 2][exit.second * 2] = TileType.EXIT
        bonuses.forEach { grid[it.row * 2][it.col * 2] = TileType.BONUS }
        // Seuls les états affichés sont nécessaires à cette scène immobile.
        val preview = Level(
            seed = 42L,
            difficulty = Difficulty.EASY,
            cellW = 4,
            cellH = 4,
            grid = grid,
            adj = Array(4) { Array(4) { emptySet<String>() } },
            start = start,
            exit = exit,
            bonuses = bonuses,
            guards = emptyList(),
            guardCycle = 1,
            guardStates = listOf(listOf(GuardPhaseState(1, 2, Dir.E, 1, 3))),
            bonusByCell = bonuses.associateBy { it.key() },
            solveTurns = 0
        )
        // Vue hors écran, jamais attachée. Un seul état : aucune animation démarrée.
        val side = 504
        val view = EscapeLabyrinthView(appContext).apply {
            level = preview
            playState = EscapeLabyrinthGame.initialPlay(preview)
            guardVision = setOf(cellKey(1, 3))
            layout(0, 0, side, side)
        }
        val cell = maxOf(w / 6.8f, h / 6.8f)
        val scale = cell / (side / 8.4f)
        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        canvas.translate((w - side * scale) / 2f, (h - side * scale) / 2f)
        canvas.scale(scale, scale)
        view.draw(canvas)
        canvas.restore()

        // Même voile inférieur que PipeTap pour garder le titre lisible.
        val shade = Paint().apply {
            shader = LinearGradient(0f, h * .55f, 0f, h,
                Color.TRANSPARENT, 0xEE0C1619.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, h * .55f, w, h, shade)
    }
}
