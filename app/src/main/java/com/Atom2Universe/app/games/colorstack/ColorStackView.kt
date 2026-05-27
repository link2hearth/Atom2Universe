package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
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
    }

    var game: ColorStackGame? = null
        set(value) { field = value; requestLayout() }
    var listener: OnMoveListener? = null

    private var selectedColumn: Int? = null
    private var validTargets: List<Int> = emptyList()

    private val density = context.resources.displayMetrics.density

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

                val isTopToken = ti == col.size - 1

                // Couleur du jeton
                tokenPaint.color = Color.parseColor(col[ti].colorHex)
                canvas.drawRoundRect(tokenRect, tokenCorner, tokenCorner, tokenPaint)

                // Reflet léger sur chaque jeton
                val shineRect = RectF(tokenRect.left, tokenRect.top, tokenRect.right,
                    tokenRect.top + (tokenRect.bottom - tokenRect.top) * 0.4f)
                canvas.drawRoundRect(shineRect, tokenCorner, tokenCorner, shinePaint)

                // Contour blanc sur le jeton du haut si colonne sélectionnée
                if (isSelected && isTopToken) {
                    strokePaint.color = 0xFFFFFFFF.toInt()
                    strokePaint.strokeWidth = 2f * density
                    canvas.drawRoundRect(tokenRect, tokenCorner, tokenCorner, strokePaint)
                }
            }

        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val g = game ?: return true
        val cols = g.columnCount
        val padH = 10f * density
        val colGap = 5f * density
        val colW = (width - 2f * padH - (cols - 1) * colGap) / cols

        val tapped = (0 until cols).firstOrNull { ci ->
            val left = padH + ci * (colW + colGap)
            event.x >= left && event.x <= left + colW
        }

        if (tapped != null) handleColumnTap(tapped) else clearSelection()
        return true
    }

    private fun handleColumnTap(col: Int) {
        val g = game ?: return
        val sel = selectedColumn

        when {
            sel == null -> {
                if (g.board[col].isEmpty()) return
                selectedColumn = col
                validTargets = g.getValidTargets(col)
                listener?.onColumnSelected(col)
                invalidate()
            }
            sel == col -> clearSelection()
            col in validTargets -> {
                listener?.onMove(sel, col)
                clearSelection()
            }
            else -> {
                // Retaper une autre colonne non-cible → re-sélectionner si non-vide
                clearSelection()
                if (g.board[col].isNotEmpty()) {
                    selectedColumn = col
                    validTargets = g.getValidTargets(col)
                    listener?.onColumnSelected(col)
                    invalidate()
                }
            }
        }
    }

    fun clearSelection() {
        selectedColumn = null
        validTargets = emptyList()
        listener?.onColumnSelected(null)
        invalidate()
    }

    fun refresh() {
        clearSelection()
        invalidate()
    }
}
