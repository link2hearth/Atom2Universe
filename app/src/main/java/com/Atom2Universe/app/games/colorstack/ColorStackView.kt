package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class ColorStackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnMoveListener {
        fun onMove(from: Int, to: Int)
        fun onColumnSelected(col: Int?)
        // Long clic : déplace d'un coup tout le groupe de jetons de la couleur du dessus.
        fun onMoveGroup(from: Int, to: Int) {}
    }

    var game: ColorStackGame? = null
        set(value) { field = value; requestLayout() }
    var listener: OnMoveListener? = null

    private var selectedColumn: Int? = null
    private var validTargets: List<Int> = emptyList()
    // Vrai quand la sélection courante a été déclenchée par un long clic (déplacement de groupe).
    private var bulkMode = false

    private val density = context.resources.displayMetrics.density

    // Détection du long clic pour le déplacement de groupe.
    private var longPressHandled = false
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            val col = columnAt(e.x) ?: return
            longPressHandled = true
            handleLongPress(col)
        }
    })

    private fun neededHeight(availableWidth: Int): Int {
        val g = game ?: return (200 * density).toInt()
        val cols = g.columnCount
        val padH = 10f * density
        val colGap = 5f * density
        val tokenPadH = 3f * density
        val tokenPadV = 1.5f * density
        val padV = 8f * density
        val colW = (availableWidth - 2f * padH - (cols - 1) * colGap) / cols
        val tokenSize = colW - 2f * tokenPadH
        val slotH = tokenSize + 2f * tokenPadV
        val colH = slotH * g.effectiveCapacity
        return (colH + 2f * padV).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val needed = if (w > 0) neededHeight(w) else hSize
        val h = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> minOf(needed, hSize)
            else -> needed
        }
        setMeasuredDimension(w, h)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x18FFFFFF
    }

    private val colRect = RectF()
    private val tokenRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        val cols = g.columnCount
        if (cols == 0 || width == 0 || height == 0) return

        val padH = 10f * density
        8f * density
        val colGap = 5f * density
        val tokenPadH = 3f * density
        val tokenPadV = 1.5f * density
        val colCorner = 8f * density
        val tokenCorner = 5f * density

        val colW = (width - 2f * padH - (cols - 1) * colGap) / cols
        // Jetons carrés : hauteur du slot = largeur du token + marges verticales
        val tokenSize = colW - 2f * tokenPadH
        val slotH = tokenSize + 2f * tokenPadV
        val colH = slotH * g.effectiveCapacity
        // Colonnes centrées verticalement
        val colTop = (height - colH) / 2f
        val colBot = colTop + colH

        for (ci in 0 until cols) {
            val colLeft = padH + ci * (colW + colGap)
            val colRight = colLeft + colW

            val isSelected = selectedColumn == ci
            val isTarget = ci in validTargets
            val col = g.board[ci]
            // En mode groupe, tout le groupe du dessus est surligné ; sinon seul le jeton du haut.
            val highlightCount = if (isSelected) {
                if (bulkMode) g.topGroupSize(ci) else 1
            } else 0
            val isSolved = col.size == g.capacity &&
                col.isNotEmpty() && col.all { it.colorId == col.first().colorId }

            // Fond de colonne
            colRect.set(colLeft, colTop, colRight, colBot)
            bgPaint.color = when {
                isSelected -> 0xFF1E3A5F.toInt()
                isTarget   -> 0xFF14532D.toInt()
                isSolved   -> 0xFF1A3A1A.toInt()
                else       -> 0xFF0F172A.toInt()
            }
            canvas.drawRoundRect(colRect, colCorner, colCorner, bgPaint)

            // Bordure de colonne
            strokePaint.strokeWidth = when {
                isSelected || isTarget -> 2.5f * density
                isSolved -> 1.5f * density
                else -> 1f * density
            }
            strokePaint.color = when {
                isSelected -> 0xFFFFFFFF.toInt()
                isTarget   -> 0xFF22C55E.toInt()
                isSolved   -> 0xFF4ADE80.toInt()
                else       -> 0xFF1F2937.toInt()
            }
            canvas.drawRoundRect(colRect, colCorner, colCorner, strokePaint)

            // Tokens — index 0 = fond de pile, index size-1 = jeton accessible (haut)
            // Slot 0 = haut visuel (Y faible), slot cap-1 = bas visuel (Y fort)
            // Jeton au fond (ti=0) → slot bas = effectiveCapacity-1
            // Jeton du haut (ti=size-1) → slot effectiveCapacity-size
            // Formule : slotIndex = effectiveCapacity - 1 - ti
            for (ti in col.indices) {
                val slotIndex = g.effectiveCapacity - 1 - ti
                val tTop = colTop + slotIndex * slotH + tokenPadV
                val tBot = colTop + (slotIndex + 1) * slotH - tokenPadV
                tokenRect.set(colLeft + tokenPadH, tTop, colRight - tokenPadH, tBot)

                // Couleur du jeton
                tokenPaint.color = Color.parseColor(col[ti].colorHex)
                canvas.drawRoundRect(tokenRect, tokenCorner, tokenCorner, tokenPaint)

                // Reflet léger sur chaque jeton
                val shineRect = RectF(tokenRect.left, tokenRect.top, tokenRect.right,
                    tokenRect.top + (tokenRect.bottom - tokenRect.top) * 0.4f)
                canvas.drawRoundRect(shineRect, tokenCorner, tokenCorner, shinePaint)

                // Contour blanc sur les jetons surlignés (haut, ou tout le groupe en mode groupe)
                if (highlightCount > 0 && ti >= col.size - highlightCount) {
                    strokePaint.color = 0xFFFFFFFF.toInt()
                    strokePaint.strokeWidth = 2f * density
                    canvas.drawRoundRect(tokenRect, tokenCorner, tokenCorner, strokePaint)
                }
            }

        }
    }

    private fun columnAt(x: Float): Int? {
        val g = game ?: return null
        val cols = g.columnCount
        val padH = 10f * density
        val colGap = 5f * density
        val colW = (width - 2f * padH - (cols - 1) * colGap) / cols
        return (0 until cols).firstOrNull { ci ->
            val left = padH + ci * (colW + colGap)
            x >= left && x <= left + colW
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) longPressHandled = false
        gestureDetector.onTouchEvent(event)
        if (event.action != MotionEvent.ACTION_UP) return true
        // Le long clic a déjà tout traité : on ne déclenche pas de tap en plus.
        if (longPressHandled) return true

        val tapped = columnAt(event.x)
        if (tapped != null) handleColumnTap(tapped) else clearSelection()
        return true
    }

    private fun handleColumnTap(col: Int) {
        val g = game ?: return
        val sel = selectedColumn

        when {
            sel == null -> selectColumn(col, bulk = false)
            sel == col -> clearSelection()
            col in validTargets -> {
                if (bulkMode) listener?.onMoveGroup(sel, col) else listener?.onMove(sel, col)
                clearSelection()
            }
            // Retaper une autre colonne non-cible → re-sélectionner (en mode simple) si non-vide
            else -> selectColumn(col, bulk = false)
        }
    }

    // Long clic : sélectionne la colonne en mode groupe. Si une seule cible valide existe,
    // le déplacement de groupe est effectué immédiatement ; sinon on attend le tap sur la cible.
    private fun handleLongPress(col: Int) {
        val g = game ?: return
        if (g.board[col].isEmpty()) { clearSelection(); return }
        val targets = g.getValidTargets(col)
        if (targets.size == 1) {
            clearSelection()
            listener?.onMoveGroup(col, targets[0])
            return
        }
        selectColumn(col, bulk = true)
    }

    private fun selectColumn(col: Int, bulk: Boolean) {
        val g = game ?: return
        clearSelection()
        if (g.board[col].isEmpty()) return
        selectedColumn = col
        validTargets = g.getValidTargets(col)
        bulkMode = bulk
        listener?.onColumnSelected(col)
        invalidate()
    }

    fun clearSelection() {
        selectedColumn = null
        validTargets = emptyList()
        bulkMode = false
        listener?.onColumnSelected(null)
        invalidate()
    }

    fun refresh() {
        clearSelection()
        invalidate()
    }
}
