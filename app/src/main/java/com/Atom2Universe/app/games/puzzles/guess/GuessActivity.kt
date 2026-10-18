package com.Atom2Universe.app.games.puzzles.guess

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleActivity
import com.Atom2Universe.app.games.kit.PuzzleView
import com.google.android.material.button.MaterialButton
import kotlin.math.min
import kotlin.random.Random

class GuessActivity : PuzzleActivity<GuessState>() {
    override val gameKey = "guess"
    override val titleRes = R.string.guess_title
    override val rulesRes = R.string.guess_rules
    override val presets = listOf(
        Preset(R.string.guess_basic),
        Preset(R.string.guess_standard),
        Preset(R.string.guess_super),
    )
    override val defaultPreset = 1

    private var swatchRow: LinearLayout? = null
    private var submitButton: MaterialButton? = null

    override fun createBoard() = GuessView(this)
    override fun generate(preset: Int, random: Random): GuessState = when (preset) {
        0 -> GuessState.generate(4, 6, 10, false, random)
        1 -> GuessState.generate(4, 6, 10, true, random)
        else -> GuessState.generate(5, 8, 12, true, random)
    }
    override fun encode(state: GuessState) = state.encode()
    override fun decode(text: String) = GuessState.decode(text)
    override fun isSolved(state: GuessState) = state.isSolved
    override val hasHints = true
    override fun hint(state: GuessState) = state.hint()
    override fun isLost(state: GuessState) = state.isLost
    override fun statusText(state: GuessState) = getString(R.string.guess_status, state.guesses.size, state.maxGuesses)

    override fun createControls(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        swatchRow = row
        addView(row, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val submit = actionButton(R.string.guess_submit, R.drawable.ic_check, primary = true) {
            current?.submit()?.let { play(it) }
        }
        submitButton = submit
        addView(submit, LinearLayout.LayoutParams((84 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    override fun onStateShown(state: GuessState) {
        val row = swatchRow ?: return
        if (row.childCount != state.colours) {
            row.removeAllViews()
            val size = (min(40f, 300f / state.colours) * dp).toInt()
            for (c in 0 until state.colours) {
                row.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(palette.piece(c))
                        setStroke((2 * dp).toInt(), palette.outline)
                    }
                    setOnClickListener {
                        val s = current ?: return@setOnClickListener
                        if (!s.isSolved && !s.isLost) play(s.put(c))
                    }
                }, LinearLayout.LayoutParams(size, size).apply { marginStart = (3 * dp).toInt(); marginEnd = (3 * dp).toInt() })
            }
        }
        submitButton?.let {
            it.isEnabled = state.canSubmit && !state.isSolved && !state.isLost
            it.alpha = if (it.isEnabled) 1f else 0.4f
        }
    }
}

/**
 * Les essais se lisent de haut en bas, la réponse de chaque essai à droite. L'essai en cours est
 * encadré ; toucher une de ses cases la désigne, la toucher encore la vide. Le secret apparaît en
 * haut une fois la partie finie.
 */
class GuessView(context: Context) : PuzzleView<GuessState>(context) {
    private var rowH = 0f
    private var top = 0f
    private var left = 0f
    private var slot = 0f

    override fun drawBoard(canvas: Canvas, state: GuessState, area: RectF) {
        val rows = state.maxGuesses + 1
        rowH = min(area.height() / rows, area.width() / (state.pegs + 2.2f))
        slot = rowH
        val width = slot * (state.pegs + 2.2f)
        left = area.centerX() - width / 2f
        top = area.centerY() - rows * rowH / 2f
        val over = state.isSolved || state.isLost
        // Le secret, caché tant que la partie n'est pas finie.
        for (i in 0 until state.pegs) {
            val cx = left + (i + 0.5f) * slot; val cy = top + rowH / 2f
            if (over) peg(canvas, cx, cy, palette.piece(state.secret[i]))
            else { fill.color = palette.raised; canvas.drawCircle(cx, cy, slot * 0.34f, fill) }
        }
        for (g in 0 until state.maxGuesses) {
            val y = top + (g + 1) * rowH
            val guess = state.guesses.getOrNull(g)
            val current = g == state.guesses.size && !over
            if (current) {
                fill.color = palette.cellHighlight
                canvas.drawRoundRect(left - slot * 0.1f, y + 2f, left + slot * state.pegs + slot * 0.1f, y + rowH - 2f,
                    rowH * 0.3f, rowH * 0.3f, fill)
            }
            for (i in 0 until state.pegs) {
                val cx = left + (i + 0.5f) * slot; val cy = y + rowH / 2f
                val colour = guess?.get(i) ?: if (current) state.row[i] else -1
                if (colour >= 0) peg(canvas, cx, cy, palette.piece(colour))
                else { fill.color = palette.blend(palette.surface, palette.text, 0.12f); canvas.drawCircle(cx, cy, slot * 0.14f, fill) }
                if (current && i == state.cursor) {
                    stroke.color = palette.accent; stroke.strokeWidth = slot * 0.06f
                    canvas.drawCircle(cx, cy, slot * 0.42f, stroke)
                }
            }
            if (guess != null) {
                val (black, white) = GuessState.score(state.secret, guess)
                val fx = left + slot * (state.pegs + 0.3f)
                val small = slot * 0.4f
                for (k in 0 until state.pegs) {
                    val px = fx + (k % 3) * small + small / 2f
                    val py = y + rowH / 2f + (k / 3 - (state.pegs - 1) / 3 / 2f) * small
                    when {
                        k < black -> { fill.color = palette.text; canvas.drawCircle(px, py, small * 0.32f, fill) }
                        k < black + white -> { stroke.color = palette.text; stroke.strokeWidth = small * 0.12f; canvas.drawCircle(px, py, small * 0.28f, stroke) }
                        else -> { fill.color = palette.outline; canvas.drawCircle(px, py, small * 0.12f, fill) }
                    }
                }
            }
        }
    }

    private fun peg(canvas: Canvas, cx: Float, cy: Float, colour: Int) {
        fill.color = colour
        canvas.drawCircle(cx, cy, slot * 0.34f, fill)
        fill.color = palette.withAlpha(0xFFFFFFFF.toInt(), 0.35f)
        canvas.drawCircle(cx - slot * 0.1f, cy - slot * 0.1f, slot * 0.1f, fill)
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val g = ((y - top) / rowH).toInt() - 1
        if (g != s.guesses.size) return
        val i = ((x - left) / slot).toInt()
        if (i !in 0 until s.pegs) return
        move(s.select(i))
    }
}

class GuessArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFE57373.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val secret = intArrayOf(1, 3, 0, 4)
        val guesses = listOf(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 3, 5), intArrayOf(1, 3, 4, 0))
        val state = GuessState(4, 6, 6, secret, guesses, intArrayOf(1, 3, -1, -1), 2)
        drawWith(GuessView(context), state, canvas, boardRect(w, h, 1.25f, 0.12f), palette)
    }
}
