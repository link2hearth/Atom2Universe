package com.Atom2Universe.app.games.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.min

/**
 * Le plateau d'un jeu du kit. Il ne garde que l'état à afficher ; chaque coup du joueur produit
 * un **nouvel** état, remis à l'écran par [move] — c'est l'écran qui l'empile pour annuler.
 *
 * Le dessin passe par [drawBoard], qui ne lit que l'état, la palette et le rectangle donné : la
 * même fonction sert au jeu et à l'illustration du hub.
 *
 * Les gestes suivent la version Android de la collection de Simon Tatham : un appui pour l'action
 * principale, un appui long pour l'action secondaire (marquer, annoter), un glissé pour tracer ou
 * déplacer quand le jeu le demande ([wantsDrag]).
 */
abstract class PuzzleView<S : Any>(context: Context) : View(context) {

    var palette: KitPalette = KitPalette.artwork(0xFF4FC3F7.toInt())
        set(value) { field = value; invalidate() }

    var state: S? = null
        set(value) { field = value; onStateChanged(); invalidate() }

    /** Faux une fois la partie gagnée, ou pendant la génération. */
    var inputEnabled = true

    var onMove: ((S) -> Unit)? = null

    /** Rectangle utile, dans les marges de la vue. */
    protected val area = RectF()

    protected val density = resources.displayMetrics.density
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    protected val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** La fête de victoire (rebond, vague arc-en-ciel, confettis, halo, carillon). */
    private val celebration = Celebration(this)

    abstract fun drawBoard(canvas: Canvas, state: S, area: RectF)

    protected open fun onStateChanged() {}

    protected fun move(next: S) {
        if (!inputEnabled) return
        onMove?.invoke(next)
    }

    /** Petite secousse de la main quand un coup compte. */
    protected fun tick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** La grille est résolue, on fête ça : voir [Celebration]. */
    fun celebrate() = celebration.start(palette)

    override fun onDraw(canvas: Canvas) {
        val s = state ?: return
        area.set(paddingLeft.toFloat(), paddingTop.toFloat(),
            (width - paddingRight).toFloat(), (height - paddingBottom).toFloat())
        if (area.width() <= 0f || area.height() <= 0f) return
        val party = celebration.running
        if (party) {
            val k = celebration.scale
            canvas.save()
            canvas.scale(k, k, area.centerX(), area.centerY())
        }
        drawBoard(canvas, s, area)
        if (party) {
            canvas.restore()
            celebration.drawOver(canvas, area, palette)
        }
    }

    override fun onDetachedFromWindow() {
        celebration.cancel()
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    // ── Gestes ──────────────────────────────────────────────────────────────────────────────

    open fun onTap(x: Float, y: Float) {}
    open fun onLongPress(x: Float, y: Float) {}

    /** Vrai si un glissé qui commence en (x, y) doit être suivi au lieu d'être ignoré. */
    open fun wantsDrag(x: Float, y: Float): Boolean = false
    open fun onDragStart(x: Float, y: Float) {}
    open fun onDragMove(x: Float, y: Float) {}
    open fun onDragEnd(x: Float, y: Float) {}
    open fun onDragCancel() {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress(downX, downY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled || state == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                longPressed = false
                parent?.requestDisallowInterceptTouchEvent(true)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && !longPressed && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    removeCallbacks(longPressRunnable)
                    if (wantsDrag(downX, downY)) {
                        dragging = true
                        onDragStart(downX, downY)
                    }
                }
                if (dragging) onDragMove(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                when {
                    dragging -> onDragEnd(event.x, event.y)
                    !longPressed && hypot(event.x - downX, event.y - downY) <= touchSlop * 2 -> {
                        performClick()
                        onTap(downX, downY)
                    }
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (dragging) onDragCancel()
                dragging = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ── Aides de dessin ─────────────────────────────────────────────────────────────────────

    /** Écrit [text] centré sur (cx, cy), à la hauteur [size]. */
    protected fun Canvas.centeredText(text: String, cx: Float, cy: Float, size: Float, color: Int,
                                      bold: Boolean = false) {
        label.textSize = size
        label.color = color
        label.isFakeBoldText = bold
        drawText(text, cx, cy - (label.ascent() + label.descent()) / 2f, label)
    }

    protected fun roundedCell(canvas: Canvas, r: RectF, color: Int, radius: Float) {
        fill.color = color
        canvas.drawRoundRect(r, radius, radius, fill)
    }
}

/**
 * Où tombe une grille de [cols] × [rows] cases dans un rectangle : taille de case et origine.
 * [marginCells] réserve autour un bord en fraction de case (pour les indices extérieurs).
 */
class GridGeometry {
    var cols = 1; private set
    var rows = 1; private set
    var cell = 0f; private set
    var left = 0f; private set
    var top = 0f; private set

    fun fit(area: RectF, cols: Int, rows: Int,
            marginLeft: Float = 0f, marginTop: Float = 0f, marginRight: Float = 0f, marginBottom: Float = 0f) {
        this.cols = cols
        this.rows = rows
        val wCells = cols + marginLeft + marginRight
        val hCells = rows + marginTop + marginBottom
        cell = min(area.width() / wCells, area.height() / hCells)
        left = area.centerX() - wCells * cell / 2f + marginLeft * cell
        top = area.centerY() - hCells * cell / 2f + marginTop * cell
    }

    val right get() = left + cols * cell
    val bottom get() = top + rows * cell

    fun x(col: Int) = left + col * cell
    fun y(row: Int) = top + row * cell
    fun cx(col: Int) = left + (col + 0.5f) * cell
    fun cy(row: Int) = top + (row + 0.5f) * cell

    fun rect(col: Int, row: Int, out: RectF, inset: Float = 0f): RectF {
        out.set(x(col) + inset, y(row) + inset, x(col + 1) - inset, y(row + 1) - inset)
        return out
    }

    /** La colonne sous x, ou -1 hors de la grille. */
    fun colAt(x: Float): Int = if (x < left || x >= right) -1 else ((x - left) / cell).toInt().coerceIn(0, cols - 1)
    fun rowAt(y: Float): Int = if (y < top || y >= bottom) -1 else ((y - top) / cell).toInt().coerceIn(0, rows - 1)

    /** La case sous (x, y) en index ligne × colonnes + colonne, ou -1. */
    fun cellAt(x: Float, y: Float): Int {
        val c = colAt(x)
        val r = rowAt(y)
        return if (c < 0 || r < 0) -1 else r * cols + c
    }
}
