package com.Atom2Universe.app.games.puzzles.samegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.random.Random

class SameGameActivity : PuzzleActivity<SameGameState>() {
    override val gameKey = "samegame"
    override val titleRes = R.string.samegame_title
    override val rulesRes = R.string.samegame_rules
    private val configs = listOf(Triple(5, 5, 3), Triple(10, 8, 3), Triple(12, 10, 4), Triple(15, 12, 4))
    override val presets = configs.mapIndexed { i, (w, h, c) ->
        Preset(R.string.samegame_preset, w, h, c)
    }
    override val defaultPreset = 1
    override val timed = false

    override fun createBoard() = SameGameView(this)
    override fun generate(preset: Int, random: Random): SameGameState {
        val (w, h, c) = configs[preset]
        return SameGameState.generate(w, h, c, random)
    }
    override fun encode(state: SameGameState) = state.encode()
    override fun decode(text: String) = SameGameState.decode(text)
    override fun isSolved(state: SameGameState) = state.isSolved
    /** L'astuce est une démonstration : la grille vidée sous les yeux du joueur (ou le mieux possible). */
    override val hasHints = true
    override val demoStepMs = 700L
    override fun demo(state: SameGameState) = state.demo()
    override fun isLost(state: SameGameState) = !state.isSolved && !state.hasMoves
    override fun statusText(state: SameGameState) =
        getString(R.string.samegame_status, state.score, state.remaining)
}

/**
 * Un premier appui désigne le groupe (il s'illumine et annonce ce qu'il rapporte), un second le
 * retire. Toucher ailleurs change simplement de groupe.
 */
class SameGameView(context: Context) : PuzzleView<SameGameState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private var selection: Set<Int> = emptySet()

    override fun onStateChanged() {
        selection = emptySet()
    }

    override fun drawBoard(canvas: Canvas, state: SameGameState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        fill.color = palette.raised
        canvas.drawRoundRect(grid.left, grid.top, grid.right, grid.bottom, cell * 0.2f, cell * 0.2f, fill)
        for (i in state.cells.indices) {
            val c = state.cells[i]
            if (c < 0) continue
            grid.rect(i % state.w, i / state.w, r, cell * 0.06f)
            val colour = palette.piece(c * 2)
            val picked = i in selection
            fill.color = if (selection.isNotEmpty() && !picked) palette.blend(colour, palette.raised, 0.45f) else colour
            canvas.drawRoundRect(r, cell * 0.22f, cell * 0.22f, fill)
            if (picked) {
                stroke.color = palette.text
                stroke.strokeWidth = cell * 0.07f
                canvas.drawRoundRect(r, cell * 0.22f, cell * 0.22f, stroke)
            }
        }
        if (selection.size >= 2) {
            val first = selection.minOrNull()!!
            val gain = (selection.size - 2) * (selection.size - 2)
            grid.rect(first % state.w, first / state.w, r)
            canvas.centeredText(context.getString(R.string.samegame_gain, gain), r.centerX(),
                r.top - cell * 0.35f, cell * 0.5f, palette.text, bold = true)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0 || s.cells[i] < 0) { selection = emptySet(); invalidate(); return }
        if (i in selection) {
            s.remove(i)?.let { tick(); move(it) }
            return
        }
        val group = s.groupOf(i)
        selection = if (group.size >= 2) group.toSet() else emptySet()
        invalidate()
    }
}

class SameGameArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFFD54F.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val rnd = Random(11)
        val state = SameGameState(8, 8, 3, IntArray(64) { rnd.nextInt(3) }, 0)
        drawWith(SameGameView(context), state, canvas, boardRect(w, h, 1.1f), palette)
    }
}
