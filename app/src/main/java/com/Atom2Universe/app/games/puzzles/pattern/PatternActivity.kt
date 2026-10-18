package com.Atom2Universe.app.games.puzzles.pattern

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.GridGeometry
import com.Atom2Universe.app.games.kit.KitArtwork
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.games.kit.PuzzleView
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class PatternActivity : PictureModeActivity<PatternState>() {
    override val gameKey = "pattern"
    override val titleRes = R.string.pattern_title
    override val rulesRes = R.string.pattern_rules
    override val presets = listOf(
        Preset(R.string.kit_size, 5, 5),
        Preset(R.string.kit_size, 10, 10),
        Preset(R.string.kit_size, 15, 15),
        Preset(R.string.kit_size, 20, 20),
        Preset(R.string.pattern_pictures),
    )
    override val defaultPreset = 1
    override val picturesPreset = 4

    private val toolChips = ArrayList<TextView>()
    private lateinit var helpChip: TextView

    override fun createBoard() = PatternView(this).also { it.help = levels.getBoolean(KEY_HELP, false) }
    override fun pictureOf(state: PatternState) = state.picture
    override fun fromPicture(index: Int) = PatternState.fromPicture(index)
    override fun generateRandom(preset: Int, random: Random): PatternState {
        val size = listOf(5, 10, 15, 20)[preset]
        return PatternState.generate(size, size, random)
    }
    override fun encode(state: PatternState) = state.encode()
    override fun decode(text: String) = PatternState.decode(text)
    override fun isSolved(state: PatternState) = state.isSolved
    override val hasHints = true
    override fun hint(state: PatternState) = state.hint()

    private fun chip(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        setOnClickListener { onClick() }
    }

    private fun spaced() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginStart = (4 * dp).toInt(); marginEnd = (4 * dp).toInt()
    }

    /**
     * L'outil de l'appui simple (noircir ou poser une croix, l'appui long fait l'autre), l'aide
     * qui barre les blocs bien placés, et en mode « Images » la pastille qui ouvre la galerie.
     */
    override fun createControls(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        for ((mode, text) in listOf(1 to R.string.pattern_tool_fill, 2 to R.string.pattern_tool_cross)) {
            val c = chip(getString(text)) {
                (board as PatternView).tool = mode
                toolChips.forEachIndexed { i, t -> styleChip(t, i + 1 == mode) }
            }
            toolChips.add(c)
            addView(c, spaced())
        }
        helpChip = chip(getString(R.string.pattern_help)) {
            val view = board as PatternView
            view.help = !view.help
            levels.edit { putBoolean(KEY_HELP, view.help) }
            styleChip(helpChip, view.help)
            view.invalidate()
        }
        addView(helpChip, spaced())
        addView(makeGalleryChip(), spaced())
        post {
            toolChips.forEachIndexed { i, c -> styleChip(c, i == 0) }
            styleChip(helpChip, (board as PatternView).help)
        }
    }

    private companion object {
        const val KEY_HELP = "help"
    }
}

/**
 * La miniature d'une image dans la galerie, toujours carrée. [DONE] : l'image en couleurs ;
 * [NEXT] : sa grille vide, cerclée de la couleur du thème (elle attend d'être jouée) ;
 * [LOCKED] : un cadenas dessiné, rien ne trahit l'image.
 */
class PictureThumb(
    context: Context, private val picture: PatternPictures.Picture, private val mode: Int, private val palette: KitPalette,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val r = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        val size = width.toFloat()
        if (mode == LOCKED) { drawLock(canvas, size); return }
        // L'image garde ses proportions, centrée dans le carré.
        val margin = size * 0.06f
        val cell = min((size - 2 * margin) / picture.w, (size - 2 * margin) / picture.h)
        val left = (size - cell * picture.w) / 2f
        val top = (size - cell * picture.h) / 2f
        fill.color = palette.cell
        canvas.drawRect(left, top, left + cell * picture.w, top + cell * picture.h, fill)
        if (mode == DONE) {
            for (i in 0 until picture.w * picture.h) {
                if (!picture.coloured(i)) continue
                val x = left + (i % picture.w) * cell
                val y = top + (i / picture.w) * cell
                fill.color = picture.colourAt(i)
                // Un soupçon de chevauchement pour éviter les fines lignes entre deux cases.
                canvas.drawRect(x, y, x + cell + 0.5f, y + cell + 0.5f, fill)
            }
        } else {
            stroke.color = palette.gridLine
            stroke.strokeWidth = (cell * 0.06f).coerceAtLeast(1f)
            for (k in 1 until picture.w) canvas.drawLine(left + k * cell, top, left + k * cell, top + cell * picture.h, stroke)
            for (k in 1 until picture.h) canvas.drawLine(left, top + k * cell, left + cell * picture.w, top + k * cell, stroke)
            stroke.color = palette.accent
            stroke.strokeWidth = size * 0.025f
            canvas.drawRect(left, top, left + cell * picture.w, top + cell * picture.h, stroke)
        }
    }

    /** Un cadenas : un anneau en arc au-dessus d'un corps arrondi, avec son trou de serrure. */
    private fun drawLock(canvas: Canvas, size: Float) {
        val cx = size / 2f
        val bodyW = size * 0.42f
        val bodyH = size * 0.32f
        val bodyTop = size * 0.46f
        stroke.color = palette.tertiary
        stroke.strokeWidth = size * 0.06f
        val ring = bodyW * 0.32f
        r.set(cx - ring, bodyTop - ring * 1.6f, cx + ring, bodyTop + ring * 0.4f)
        canvas.drawArc(r, 180f, 180f, false, stroke)
        canvas.drawLine(cx - ring, bodyTop - ring * 0.6f, cx - ring, bodyTop, stroke)
        canvas.drawLine(cx + ring, bodyTop - ring * 0.6f, cx + ring, bodyTop, stroke)
        fill.color = palette.tertiary
        r.set(cx - bodyW / 2f, bodyTop, cx + bodyW / 2f, bodyTop + bodyH)
        canvas.drawRoundRect(r, size * 0.04f, size * 0.04f, fill)
        fill.color = palette.raised
        canvas.drawCircle(cx, bodyTop + bodyH * 0.42f, size * 0.035f, fill)
        canvas.drawRect(cx - size * 0.012f, bodyTop + bodyH * 0.42f, cx + size * 0.012f, bodyTop + bodyH * 0.75f, fill)
    }

    companion object {
        const val DONE = 0
        const val NEXT = 1
        const val LOCKED = 2
    }
}

/**
 * Appui : l'outil choisi ; appui long : l'autre. Un glissé applique le même geste à toute la
 * ligne ou la colonne parcourue. Les indices d'une ligne juste s'estompent ; avec l'aide, chaque
 * bloc bien placé est barré. Une image réussie se révèle en couleurs.
 */
class PatternView(context: Context) : PuzzleView<PatternState>(context) {
    private val grid = GridGeometry()
    private val r = RectF()
    var tool = 1
    /** L'aide : barrer chaque bloc dont les cases sont exactement les bonnes. */
    var help = false
    private var dragStart = -1
    private var dragEnd = -1
    private var dragValue = 0

    private fun marginsFor(state: PatternState): Pair<Float, Float> {
        val left = (state.rows.maxOf { it.size }.coerceAtLeast(1)) * 0.55f + 0.2f
        val top = (state.cols.maxOf { it.size }.coerceAtLeast(1)) * 0.55f + 0.2f
        return left to top
    }

    private fun preview(state: PatternState): Map<Int, Int> {
        if (dragStart < 0 || dragEnd < 0) return emptyMap()
        val (sx, sy) = dragStart % state.w to dragStart / state.w
        val (ex, ey) = dragEnd % state.w to dragEnd / state.w
        val cells = if (abs(ex - sx) >= abs(ey - sy)) (minOf(sx, ex)..maxOf(sx, ex)).map { sy * state.w + it }
        else (minOf(sy, ey)..maxOf(sy, ey)).map { it * state.w + sx }
        return cells.associateWith { dragValue }
    }

    override fun drawBoard(canvas: Canvas, state: PatternState, area: RectF) {
        val (ml, mt) = marginsFor(state)
        grid.fit(area, state.w, state.h, ml, mt, 0.1f, 0.1f)
        val cell = grid.cell
        val shown = preview(state)
        // Révélée, l'image se peint entière : aussi les cases colorées qu'on ne noircissait pas.
        val picture = if (state.picture >= 0 && state.isSolved) PatternPictures.all[state.picture] else null
        for (i in state.cells.indices) {
            grid.rect(i % state.w, i / state.w, r)
            val v = shown[i] ?: state.cells[i]
            fill.color = when {
                picture != null && picture.coloured(i) -> picture.colourAt(i)
                v == 1 -> palette.text
                else -> palette.cell
            }
            canvas.drawRect(r, fill)
            if (v == 2 && picture?.coloured(i) != true) {
                stroke.color = palette.tertiary
                stroke.strokeWidth = cell * 0.06f
                val m = cell * 0.3f
                canvas.drawLine(r.left + m, r.top + m, r.right - m, r.bottom - m, stroke)
                canvas.drawLine(r.right - m, r.top + m, r.left + m, r.bottom - m, stroke)
            }
        }
        for (k in 0..state.w) {
            stroke.color = if (k % 5 == 0) palette.gridBold else palette.gridLine
            stroke.strokeWidth = cell * if (k % 5 == 0) 0.05f else 0.02f
            canvas.drawLine(grid.x(k), grid.top, grid.x(k), grid.bottom, stroke)
        }
        for (k in 0..state.h) {
            stroke.color = if (k % 5 == 0) palette.gridBold else palette.gridLine
            stroke.strokeWidth = cell * if (k % 5 == 0) 0.05f else 0.02f
            canvas.drawLine(grid.left, grid.y(k), grid.right, grid.y(k), stroke)
        }
        val size = cell * 0.42f
        for (row in 0 until state.h) {
            val clue = state.rows[row]
            val done = state.lineDone(true, row)
            if (clue.isEmpty()) clueNumber(canvas, 0, grid.left - cell * 0.45f, grid.cy(row), size, done, false)
            for ((j, v) in clue.withIndex()) {
                clueNumber(canvas, v, grid.left - (clue.size - j) * cell * 0.55f + cell * 0.1f, grid.cy(row), size,
                    done, help && state.blockDone(true, row, j))
            }
        }
        for (col in 0 until state.w) {
            val clue = state.cols[col]
            val done = state.lineDone(false, col)
            if (clue.isEmpty()) clueNumber(canvas, 0, grid.cx(col), grid.top - cell * 0.35f, size, done, false)
            for ((j, v) in clue.withIndex()) {
                clueNumber(canvas, v, grid.cx(col), grid.top - (clue.size - j) * cell * 0.55f + cell * 0.2f, size,
                    done, help && state.blockDone(false, col, j))
            }
        }
        label.textAlign = Paint.Align.CENTER
    }

    /** Un indice : estompé quand sa ligne est complète, barré par l'aide quand son bloc est bien placé. */
    private fun clueNumber(canvas: Canvas, v: Int, x: Float, y: Float, size: Float, lineDone: Boolean, struck: Boolean) {
        val faded = lineDone || struck
        canvas.centeredText(context.getString(R.string.pattern_clue, v), x, y, size, if (faded) palette.tertiary else palette.text, bold = !faded)
        if (struck) {
            stroke.color = palette.tertiary
            stroke.strokeWidth = size * 0.1f
            canvas.drawLine(x - size * 0.4f, y + size * 0.35f, x + size * 0.4f, y - size * 0.35f, stroke)
        }
    }

    private fun valueFor(current: Int, primary: Boolean): Int {
        val mode = if (primary) tool else 3 - tool
        return if (current == mode) 0 else mode
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        tick(); move(s.with(mapOf(i to valueFor(s.cells[i], true))))
    }

    override fun onLongPress(x: Float, y: Float) {
        val s = state ?: return
        val i = grid.cellAt(x, y)
        if (i < 0) return
        move(s.with(mapOf(i to valueFor(s.cells[i], false))))
    }

    override fun wantsDrag(x: Float, y: Float) = grid.cellAt(x, y) >= 0

    override fun onDragStart(x: Float, y: Float) {
        val s = state ?: return
        dragStart = grid.cellAt(x, y)
        dragEnd = dragStart
        dragValue = valueFor(s.cells[dragStart], true)
    }

    override fun onDragMove(x: Float, y: Float) {
        val c = grid.colAt(x.coerceIn(grid.left, grid.right - 1f))
        val rr = grid.rowAt(y.coerceIn(grid.top, grid.bottom - 1f))
        val s = state ?: return
        dragEnd = rr * s.w + c
        invalidate()
    }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state
        val changes = if (s != null) preview(s) else emptyMap()
        dragStart = -1; dragEnd = -1
        if (s != null && changes.isNotEmpty()) { tick(); move(s.with(changes)) } else invalidate()
    }

    override fun onDragCancel() {
        dragStart = -1; dragEnd = -1
        invalidate()
    }
}

class PatternArtwork(context: Context) : KitArtwork(context) {
    override val accent = 0xFFA1887F.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        val s = PatternState.generate(8, 8, Random(13))
        // On révèle la moitié haute de la solution.
        val solution = PatternState.lineSolution(8, 8, s.rows, s.cols) ?: IntArray(64)
        val cells = IntArray(64) { if (it < 32) (if (solution[it] == 1) 1 else 2) else 0 }
        drawWith(PatternView(context), PatternState(8, 8, s.rows, s.cols, cells), canvas, boardRect(w, h, 1.1f), palette)
    }
}
