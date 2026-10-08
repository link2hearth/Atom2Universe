package com.Atom2Universe.app.games.kit

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
import kotlin.random.Random

/**
 * La grille commune des puzzles « carré latin » de Simon Tatham (Towers, Unequal, Keen…) :
 * chaque chiffre de 1 à n une seule fois par ligne et par colonne, plus les indices du jeu.
 *
 * [values] : 0 = vide. [notes] : annotations du joueur, un bit par chiffre. [fixed] : chiffres
 * donnés par l'énoncé (ou révélés par une astuce). [solution] : la grille complète, gardée pour
 * les astuces (vide si inconnue).
 */
class LatinBoard(
    val n: Int, val values: IntArray, val notes: IntArray, val fixed: BooleanArray,
    val solution: IntArray = IntArray(0),
) {

    val full: Boolean get() = values.all { it != 0 }

    fun set(cell: Int, value: Int): LatinBoard {
        if (fixed[cell]) return this
        val v = values.copyOf(); v[cell] = value
        val nt = notes.copyOf(); nt[cell] = 0
        // Un chiffre posé efface cette annotation sur sa ligne et sa colonne.
        if (value != 0) {
            val r = cell / n; val c = cell % n
            for (i in 0 until n) {
                nt[r * n + i] = nt[r * n + i] and (1 shl value).inv()
                nt[i * n + c] = nt[i * n + c] and (1 shl value).inv()
            }
        }
        return LatinBoard(n, v, nt, fixed, solution)
    }

    fun toggleNote(cell: Int, digit: Int): LatinBoard {
        if (fixed[cell] || values[cell] != 0) return this
        val nt = notes.copyOf(); nt[cell] = nt[cell] xor (1 shl digit)
        return LatinBoard(n, values, nt, fixed, solution)
    }

    fun clear(cell: Int): LatinBoard {
        if (fixed[cell]) return this
        val v = values.copyOf(); v[cell] = 0
        val nt = notes.copyOf(); nt[cell] = 0
        return LatinBoard(n, v, nt, fixed, solution)
    }

    /**
     * L'astuce : corrige d'abord une case fausse, sinon révèle une case vide au hasard. La case
     * révélée devient fixe, comme un chiffre de l'énoncé. null s'il n'y a rien à montrer.
     */
    fun hint(random: kotlin.random.Random = kotlin.random.Random): LatinBoard? {
        if (solution.size != n * n) return null
        val wrong = values.indices.filter { !fixed[it] && values[it] != 0 && values[it] != solution[it] }
        val empty = values.indices.filter { values[it] == 0 }
        val cell = (wrong.ifEmpty { empty }).randomOrNull(random) ?: return null
        val v = values.copyOf(); v[cell] = solution[cell]
        val nt = notes.copyOf(); nt[cell] = 0
        val fx = fixed.copyOf(); fx[cell] = true
        return LatinBoard(n, v, nt, fx, solution)
    }

    /** Les cases qui répètent un chiffre de leur ligne ou de leur colonne. */
    fun duplicates(): Set<Int> {
        val out = HashSet<Int>()
        for (a in values.indices) {
            if (values[a] == 0) continue
            val r = a / n; val c = a % n
            for (i in 0 until n) {
                val b1 = r * n + i; val b2 = i * n + c
                if (b1 != a && values[b1] == values[a]) out.add(a)
                if (b2 != a && values[b2] == values[a]) out.add(a)
            }
        }
        return out
    }

    fun encode(): String = "$n;" + values.joinToString(",") + ";" + notes.joinToString(",") + ";" +
        fixed.joinToString("") { if (it) "1" else "0" } + ";" + solution.joinToString(",")

    companion object {
        fun decode(text: String): LatinBoard {
            val p = text.split(';')
            val n = p[0].toInt()
            return LatinBoard(n, p[1].split(',').map { it.toInt() }.toIntArray(),
                p[2].split(',').map { it.toInt() }.toIntArray(), BooleanArray(n * n) { p[3][it] == '1' },
                p[4].split(',').filter { it.isNotEmpty() }.map { it.toInt() }.toIntArray())
        }

        fun empty(n: Int) = LatinBoard(n, IntArray(n * n), IntArray(n * n), BooleanArray(n * n))

        fun withGivens(n: Int, givens: IntArray, solution: IntArray) =
            LatinBoard(n, givens.copyOf(), IntArray(n * n), BooleanArray(n * n) { givens[it] != 0 }, solution.copyOf())

        /** Un carré latin au hasard : remplissage à rebours, chiffres essayés dans le désordre. */
        fun randomSquare(n: Int, random: Random): IntArray {
            val grid = IntArray(n * n)
            fun fill(cell: Int): Boolean {
                if (cell == n * n) return true
                val r = cell / n; val c = cell % n
                for (d in (1..n).shuffled(random)) {
                    var ok = true
                    for (i in 0 until c) if (grid[r * n + i] == d) { ok = false; break }
                    if (ok) for (i in 0 until r) if (grid[i * n + c] == d) { ok = false; break }
                    if (!ok) continue
                    grid[cell] = d
                    if (fill(cell + 1)) return true
                }
                grid[cell] = 0
                return false
            }
            fill(0)
            return grid
        }

        /**
         * Compte les solutions (jusqu'à [limit]) d'une grille [givens] sous les règles latines
         * et [ok], qui dit si la grille partielle reste possible après avoir posé la case donnée.
         * Toujours la case la plus contrainte d'abord.
         */
        fun countSolutions(n: Int, givens: IntArray, limit: Int = 2, budget: Int = 2_000_000,
                           ok: (IntArray, Int) -> Boolean): Int {
            val grid = givens.copyOf()
            val rowUsed = IntArray(n); val colUsed = IntArray(n)
            for (i in grid.indices) if (grid[i] != 0) {
                rowUsed[i / n] = rowUsed[i / n] or (1 shl grid[i]); colUsed[i % n] = colUsed[i % n] or (1 shl grid[i])
            }
            var count = 0
            var nodes = 0
            fun solve() {
                if (count >= limit || nodes++ > budget) return
                var best = -1; var bestMask = 0; var bestBits = 99
                for (i in grid.indices) {
                    if (grid[i] != 0) continue
                    val mask = ((1 shl (n + 1)) - 2) and (rowUsed[i / n] or colUsed[i % n]).inv()
                    val bits = Integer.bitCount(mask)
                    if (bits < bestBits) { best = i; bestMask = mask; bestBits = bits; if (bits <= 1) break }
                }
                if (best < 0) { count++; return }
                if (bestBits == 0) return
                var m = bestMask
                while (m != 0) {
                    val bit = m and -m; m = m xor bit
                    val d = Integer.numberOfTrailingZeros(bit)
                    grid[best] = d
                    rowUsed[best / n] = rowUsed[best / n] or bit; colUsed[best % n] = colUsed[best % n] or bit
                    if (ok(grid, best)) solve()
                    rowUsed[best / n] = rowUsed[best / n] xor bit; colUsed[best % n] = colUsed[best % n] xor bit
                    grid[best] = 0
                    if (count >= limit) return
                }
            }
            solve()
            return if (nodes > budget) limit else count
        }
    }
}

/** Un puzzle qui porte une [LatinBoard] et sait en changer. */
interface LatinHolder<S> {
    val board: LatinBoard
    fun withBoard(board: LatinBoard): S
}

/**
 * La vue commune : une case choisie, les chiffres et annotations, les doublons en rouge, les
 * chiffres égaux à celui de la case choisie mis en avant. Chaque jeu dessine ses indices
 * ([drawClues]) et réserve leur marge ([margin]).
 */
abstract class LatinView<S : LatinHolder<S>>(context: Context) : PuzzleView<S>(context) {
    protected val grid = GridGeometry()
    protected val r = RectF()
    var selected = -1
        set(value) { field = value; invalidate() }

    /** Marge autour de la grille, en fraction de case (gauche, haut, droite, bas). */
    protected open fun margin(state: S): FloatArray = floatArrayOf(0f, 0f, 0f, 0f)
    protected open fun drawCellBackground(canvas: Canvas, state: S, cell: Int) {}
    protected abstract fun drawClues(canvas: Canvas, state: S)
    /** Les cases à signaler en plus des doublons (indices non respectés). */
    protected open fun clueErrors(state: S): Set<Int> = emptySet()
    protected open val gridGap: Float = 0f

    override fun drawBoard(canvas: Canvas, state: S, area: RectF) {
        val b = state.board
        val m = margin(state)
        grid.fit(area, b.n, b.n, m[0], m[1], m[2], m[3])
        val cell = grid.cell
        val errors = b.duplicates() + clueErrors(state)
        val selValue = b.values.getOrElse(selected) { 0 }
        for (i in 0 until b.n * b.n) {
            grid.rect(i % b.n, i / b.n, r, gridGap * cell)
            fill.color = when {
                i == selected -> palette.cellHighlight
                selValue != 0 && b.values[i] == selValue -> palette.blend(palette.cell, palette.accent, 0.10f)
                b.fixed[i] -> palette.cellFixed
                else -> palette.cell
            }
            if (gridGap > 0f) canvas.drawRoundRect(r, cell * 0.1f, cell * 0.1f, fill) else canvas.drawRect(r, fill)
            drawCellBackground(canvas, state, i)
        }
        if (gridGap == 0f) {
            stroke.color = palette.gridLine
            stroke.strokeWidth = cell * 0.025f
            for (k in 1 until b.n) {
                canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
                canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
            }
            stroke.color = palette.gridBold
            stroke.strokeWidth = cell * 0.05f
            canvas.drawRect(grid.left, grid.top, grid.right, grid.bottom, stroke)
        }
        drawClues(canvas, state)
        for (i in 0 until b.n * b.n) {
            grid.rect(i % b.n, i / b.n, r)
            val v = b.values[i]
            if (v != 0) {
                val colour = when {
                    i in errors -> palette.error
                    b.fixed[i] -> palette.text
                    else -> palette.ink
                }
                canvas.centeredText(v.toString(), r.centerX(), r.centerY() + cell * 0.04f, cell * 0.52f, colour, bold = b.fixed[i])
            } else if (b.notes[i] != 0) {
                val cols = if (b.n <= 4) 2 else 3
                val sub = cell * 0.62f / cols
                var k = 0
                for (d in 1..b.n) {
                    if (b.notes[i] and (1 shl d) == 0) { continue }
                    val slot = d - 1
                    val cx = r.centerX() + ((slot % cols) - (cols - 1) / 2f) * sub
                    val cy = r.centerY() + ((slot / cols) - ((b.n - 1) / cols) / 2f) * sub
                    canvas.centeredText(d.toString(), cx, cy, sub * 0.8f, palette.secondary)
                    k++
                }
            }
        }
    }

    override fun onTap(x: Float, y: Float) {
        val i = grid.cellAt(x, y)
        selected = if (i == selected) -1 else i
    }

    fun edit(change: (LatinBoard) -> LatinBoard) {
        val s = state ?: return
        if (selected < 0) return
        val next = change(s.board)
        if (next !== s.board) { tick(); move(s.withBoard(next)) }
    }
}

/**
 * L'écran commun : le pavé de chiffres sous la grille, la gomme et le crayon d'annotations.
 */
abstract class LatinPuzzleActivity<S : LatinHolder<S>> : PuzzleActivity<S>() {
    private var notesMode = false
    private var padRow: LinearLayout? = null
    private var pencil: TextView? = null
    private val latinView get() = board as LatinView<S>

    /** Astuce : une case juste de plus (ou une erreur corrigée), comme un chiffre de l'énoncé. */
    override val hasHints = true
    override fun hint(state: S): S? = state.board.hint()?.let { state.withBoard(it) }

    override fun createControls(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        padRow = this
    }

    override fun onStateShown(state: S) {
        val row = padRow ?: return
        val n = state.board.n
        if (row.childCount != n + 2) {
            row.removeAllViews()
            for (d in 1..n) row.addView(key(d.toString()) { press(d) }, weight())
            row.addView(key(getString(R.string.kit_erase)) {
                latinView.edit { b -> b.clear(latinView.selected) }
            }, weight())
            pencil = key(getString(R.string.kit_pencil)) {
                notesMode = !notesMode
                stylePencil()
            }
            row.addView(pencil, weight())
            stylePencil()
        }
        if (latinView.selected >= n * n) latinView.selected = -1
    }

    private fun press(d: Int) {
        latinView.edit { b ->
            val cell = latinView.selected
            when {
                notesMode -> b.toggleNote(cell, d)
                b.values[cell] == d -> b.clear(cell)
                else -> b.set(cell, d)
            }
        }
    }

    private fun weight() = LinearLayout.LayoutParams(0, (46 * dp).toInt(), 1f).apply {
        marginStart = (2 * dp).toInt(); marginEnd = (2 * dp).toInt()
    }

    private fun key(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 18f
        setTextColor(palette.text)
        background = GradientDrawable().apply {
            cornerRadius = AppearanceStyle.corner(this@LatinPuzzleActivity, 12f)
            setColor(palette.raised)
            setStroke(dp.toInt().coerceAtLeast(1), palette.outline)
        }
        setOnClickListener { onClick() }
    }

    private fun stylePencil() {
        pencil?.let { styleChip(it, notesMode) }
    }
}
