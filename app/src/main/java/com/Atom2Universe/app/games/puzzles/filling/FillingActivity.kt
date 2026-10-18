package com.Atom2Universe.app.games.puzzles.filling

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.random.Random

class FillingActivity : PuzzleActivity<FillingState>() {
    override val gameKey = "filling"
    override val titleRes = R.string.filling_title
    override val rulesRes = R.string.filling_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 7, 7),
        Preset(R.string.kit_size, 9, 9),
    )
    override val defaultPreset = 1

    override fun createBoard() = FillingView(this)
    override fun generate(preset: Int, random: Random): FillingState {
        val size = listOf(5, 7, 9)[preset]
        return FillingState.generate(size, size, listOf(5, 7, 8)[preset], random)
    }
    override fun encode(state: FillingState) = state.encode()
    override fun decode(text: String) = FillingState.decode(text)
    override fun isSolved(state: FillingState) = state.isSolved
    override val hasHints = true
    override fun hint(state: FillingState) = state.hint()

    override fun createControls(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        for (d in 1..9) addView(key(d.toString()) { (board as FillingView).enter(d) }, weight())
        addView(key(getString(R.string.kit_erase)) { (board as FillingView).enter(0) }, weight())
    }

    private fun weight() = LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f).apply {
        marginStart = (2 * dp).toInt(); marginEnd = (2 * dp).toInt()
    }

    private fun key(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 17f
        setTextColor(palette.text)
        background = GradientDrawable().apply {
            cornerRadius = AppearanceStyle.corner(this@FillingActivity, 12f)
            setColor(palette.raised)
            setStroke(dp.toInt().coerceAtLeast(1), palette.outline)
        }
        setOnClickListener { onClick() }
    }
}

/**
 * Toucher une case la choisit, le pavé y écrit. Glisser depuis une case chiffrée recopie son
 * chiffre sur les cases traversées. Un bloc complet prend la couleur de son chiffre.
 */
class FillingView(context: Context) : PuzzleView<FillingState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    private var selected = -1
    private val trail = LinkedHashSet<Int>()
    private var trailValue = 0

    fun enter(v: Int) {
        val s = state ?: return
        if (selected < 0) return
        s.set(selected, v)?.let { tick(); move(it) }
    }

    override fun drawBoard(canvas: Canvas, state: FillingState, area: RectF) {
        grid.fit(area, state.w, state.h)
        val cell = grid.cell
        val errors = state.errors()
        for (i in state.values.indices) {
            grid.rect(i % state.w, i / state.w, r)
            val v = if (i in trail) trailValue else state.values[i]
            fill.color = when {
                i == selected -> palette.cellHighlight
                v != 0 && state.complete(i) -> palette.blend(palette.cell, palette.piece(v), if (palette.isLight) 0.3f else 0.35f)
                state.givens[i] != 0 -> palette.cellFixed
                else -> palette.cell
            }
            canvas.drawRect(r, fill)
            if (v != 0) canvas.centeredText(v.toString(), r.centerX(), r.centerY(), cell * 0.5f,
                when { i in errors -> palette.error; state.givens[i] != 0 -> palette.text; else -> palette.ink }, bold = state.givens[i] != 0)
        }
        // Lignes fines dans un bloc, épaisses entre blocs de chiffres différents.
        for (i in state.values.indices) {
            val x = i % state.w; val y = i / state.w
            if (x < state.w - 1) {
                val same = state.values[i] != 0 && state.values[i] == state.values[i + 1]
                stroke.color = if (same) palette.gridLine else palette.gridBold
                stroke.strokeWidth = cell * if (same) 0.02f else 0.05f
                canvas.drawLine(grid.x(x + 1), grid.y(y), grid.x(x + 1), grid.y(y + 1), stroke)
            }
            if (y < state.h - 1) {
                val same = state.values[i] != 0 && state.values[i] == state.values[i + state.w]
                stroke.color = if (same) palette.gridLine else palette.gridBold
                stroke.strokeWidth = cell * if (same) 0.02f else 0.05f
                canvas.drawLine(grid.x(x), grid.y(y + 1), grid.x(x + 1), grid.y(y + 1), stroke)
            }
        }
        stroke.color = palette.gridBold; stroke.strokeWidth = cell * 0.06f
        canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, stroke)
    }

    override fun onTap(x: Float, y: Float) {
        val i = grid.cellAt(x, y)
        selected = if (i == selected) -1 else i
        invalidate()
    }

    override fun wantsDrag(x: Float, y: Float): Boolean {
        val s = state ?: return false
        val i = grid.cellAt(x, y)
        return i >= 0 && s.values[i] != 0
    }

    override fun onDragStart(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        trailValue = s.values[i]
        trail.clear()
    }

    override fun onDragMove(x: Float, y: Float) {
        val i = grid.cellAt(x, y)
        val s = state ?: return
        if (i >= 0 && s.givens[i] == 0) { trail.add(i); invalidate() }
    }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state
        val next = if (s != null && trail.isNotEmpty()) s.setAll(trail, trailValue) else null
        trail.clear()
        if (next != null) { tick(); move(next) } else invalidate()
    }

    override fun onDragCancel() { trail.clear(); invalidate() }
}

class FillingArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFFFCC80.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = FillingState.generate(6, 6, 6, Random(24))
        drawWith(FillingView(context), s, canvas, boardRect(w, h, 1.1f), palette)
    }
}
