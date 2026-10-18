package com.Atom2Universe.app.games.puzzles.signpost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.math.atan2
import kotlin.random.Random

class SignpostActivity : PuzzleActivity<SignpostState>() {
    override val gameKey = "signpost"
    override val titleRes = R.string.signpost_title
    override val rulesRes = R.string.signpost_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 4, 4),
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 6, 6),
        Preset(R.string.kit_size, 7, 7),
    )
    override val defaultPreset = 1

    override fun createBoard() = SignpostView(this)
    override fun generate(preset: Int, random: Random) = SignpostState.generate(preset + 4, preset + 4, random)
    override fun encode(state: SignpostState) = state.encode()
    override fun decode(text: String) = SignpostState.decode(text)
    override fun isSolved(state: SignpostState) = state.isSolved
    override val hasHints = true
    override fun hint(state: SignpostState) = state.hint()
}

/**
 * Glisser d'une case vers une case dans la direction de sa flèche les relie ; toucher une case
 * défait ses liens. Une chaîne qui touche un numéro donné se numérote toute seule ; les chaînes
 * encore libres prennent chacune leur couleur.
 */
class SignpostView(context: Context) : PuzzleView<SignpostState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private val arrow = Path()
    private var dragFrom = -1
    private var dragX = 0f
    private var dragY = 0f

    override fun drawBoard(canvas: Canvas, state: SignpostState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val chain = IntArray(state.n)
        val num = state.numbering(chain)
        for (i in 0 until state.n) {
            grid.rect(i % state.w, i / state.w, r, cell * 0.05f)
            val linked = state.next[i] >= 0 || state.prev(i) >= 0
            fill.color = when {
                num[i] > 0 -> palette.blend(palette.cell, palette.accent, 0.12f)
                linked -> palette.blend(palette.cell, palette.piece(chain[i] * 3), 0.3f)
                else -> palette.cell
            }
            canvas.drawRoundRect(r, cell * 0.14f, cell * 0.14f, fill)
            if (i == dragFrom) {
                stroke.color = palette.accent; stroke.strokeWidth = cell * 0.06f
                canvas.drawRoundRect(r, cell * 0.14f, cell * 0.14f, stroke)
            }
            // La flèche, dans le coin bas-droit ; l'étoile pour la dernière case.
            val d = state.dirs[i]
            val ax = r.right - cell * 0.24f; val ay = r.bottom - cell * 0.24f
            if (d < 0) {
                canvas.centeredText(context.getString(R.string.signpost_star), ax, ay, cell * 0.3f, palette.accent)
            } else {
                canvas.save()
                canvas.rotate(Math.toDegrees(atan2(SignpostState.DY[d].toDouble(), SignpostState.DX[d].toDouble())).toFloat(), ax, ay)
                arrow.reset()
                arrow.moveTo(ax + cell * 0.14f, ay)
                arrow.lineTo(ax - cell * 0.08f, ay - cell * 0.1f)
                arrow.lineTo(ax - cell * 0.08f, ay + cell * 0.1f)
                arrow.close()
                fill.color = palette.secondary
                canvas.drawPath(arrow, fill)
                canvas.restore()
            }
            val text = when {
                state.givens[i] > 0 -> state.givens[i].toString()
                num[i] > 0 -> num[i].toString()
                else -> null
            }
            if (text != null) canvas.centeredText(text, r.centerX() - cell * 0.08f, r.centerY() - cell * 0.06f, cell * 0.38f,
                if (state.givens[i] > 0) palette.text else palette.ink, bold = state.givens[i] > 0)
        }
        // Les liens.
        stroke.strokeWidth = cell * 0.05f
        for (i in 0 until state.n) {
            val j = state.next[i]
            if (j < 0) continue
            stroke.color = palette.withAlpha(if (num[i] > 0) palette.accent else palette.piece(chain[i] * 3), 0.85f)
            canvas.drawLine(grid.cx(i % state.w), grid.cy(i / state.w), grid.cx(j % state.w), grid.cy(j / state.w), stroke)
            fill.color = stroke.color
            canvas.drawCircle(grid.cx(j % state.w), grid.cy(j / state.w), cell * 0.07f, fill)
        }
        if (dragFrom >= 0) {
            stroke.color = palette.withAlpha(palette.accent, 0.6f)
            canvas.drawLine(grid.cx(dragFrom % state.w), grid.cy(dragFrom / state.w), dragX, dragY, stroke)
        }
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        if (s.next[i] >= 0 || s.prev(i) >= 0) { tick(); move(s.unlink(i)) }
    }

    override fun wantsDrag(x: Float, y: Float) = grid.cellAt(x, y) >= 0
    override fun onDragStart(x: Float, y: Float) { dragFrom = grid.cellAt(x, y); dragX = x; dragY = y }
    override fun onDragMove(x: Float, y: Float) { dragX = x; dragY = y; invalidate() }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state
        val a = dragFrom
        dragFrom = -1
        val b = grid.cellAt(x, y)
        val next = if (s != null && a >= 0 && b >= 0) s.link(a, b) ?: s.link(b, a) else null
        if (next != null) { tick(); move(next) } else invalidate()
    }

    override fun onDragCancel() { dragFrom = -1; invalidate() }
}

class SignpostArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFF9575CD.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = SignpostState.generate(4, 4, Random(21))
        drawWith(SignpostView(context), s, canvas, boardRect(w, h, 1.05f), palette)
    }
}
