package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Conteneur draggable pour la vue de partition
 *
 * Permet à l'utilisateur de déplacer verticalement la bande de partition
 * La zone de drag est en haut du conteneur (24dp)
 */
class DraggableSheetMusicContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DRAG_HANDLE_HEIGHT_DP = 24f
        private const val DRAG_HANDLE_LINE_WIDTH_DP = 40f
        private const val DRAG_HANDLE_LINE_HEIGHT_DP = 4f
        private const val DRAG_HANDLE_LINE_CORNER_RADIUS_DP = 2f
    }

    // Callbacks
    var onDragListener: ((deltaY: Float) -> Unit)? = null
    var onDragEndListener: (() -> Unit)? = null

    // État du drag
    private var isDragging = false
    private var lastTouchY = 0f
    private var dragHandleHeight = 0f
    private var dragHandleLineWidth = 0f
    private var dragHandleLineHeight = 0f
    private var dragHandleLineCornerRadius = 0f

    // Paint pour la poignée
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60FFFFFF")
        style = Paint.Style.FILL
    }

    private val handleBackgroundPaint = Paint().apply {
        color = Color.parseColor("#2A2A4E")
        style = Paint.Style.FILL
    }

    init {
        setWillNotDraw(false)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val density = resources.displayMetrics.density
        dragHandleHeight = DRAG_HANDLE_HEIGHT_DP * density
        dragHandleLineWidth = DRAG_HANDLE_LINE_WIDTH_DP * density
        dragHandleLineHeight = DRAG_HANDLE_LINE_HEIGHT_DP * density
        dragHandleLineCornerRadius = DRAG_HANDLE_LINE_CORNER_RADIUS_DP * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dessiner le fond de la poignée
        canvas.drawRect(0f, 0f, width.toFloat(), dragHandleHeight, handleBackgroundPaint)

        // Dessiner la ligne de poignée (indicateur de drag)
        val lineLeft = (width - dragHandleLineWidth) / 2
        val lineTop = (dragHandleHeight - dragHandleLineHeight) / 2
        canvas.drawRoundRect(
            lineLeft,
            lineTop,
            lineLeft + dragHandleLineWidth,
            lineTop + dragHandleLineHeight,
            dragHandleLineCornerRadius,
            dragHandleLineCornerRadius,
            handlePaint
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Ne jamais intercepter si multi-touch (pinch-to-zoom sur l'enfant)
        if (ev.pointerCount > 1) {
            isDragging = false
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Intercepter seulement si dans la zone de la poignée
                if (ev.y <= dragHandleHeight) {
                    isDragging = true
                    lastTouchY = ev.rawY
                    return true
                }
                // Sinon, laisser passer à l'enfant
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Deuxième doigt = arrêter le drag, laisser passer pour pinch
                isDragging = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                // Si on drag, intercepter
                return isDragging
            }
        }

        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Si multi-touch, ne pas gérer (laisser l'enfant gérer le pinch)
        if (event.pointerCount > 1) {
            isDragging = false
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y <= dragHandleHeight) {
                    isDragging = true
                    lastTouchY = event.rawY
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaY = event.rawY - lastTouchY
                    lastTouchY = event.rawY
                    onDragListener?.invoke(deltaY)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onDragEndListener?.invoke()
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Un autre doigt arrive, arrêter le drag
                isDragging = false
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Met à jour la hauteur du conteneur
     */
    fun setContainerHeight(heightPx: Float) {
        val params = layoutParams
        params.height = heightPx.toInt().coerceAtLeast((dragHandleHeight + 50).toInt())
        layoutParams = params
    }

    /**
     * Retourne la hauteur de la zone de drag
     */
    fun getDragHandleHeight(): Float = dragHandleHeight
}
