package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Barre de progression avec système de marqueurs
 *
 * Structure en 2 lignes parallèles:
 * - Ligne 1: Barre de progression avec curseur
 * - Ligne 2: Marqueurs placés (points colorés cliquables) + bouton "+" qui suit le curseur
 */
class MarkerSeekBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_MARKERS = 10

        // Couleurs des marqueurs (cycle)
        private val MARKER_COLORS = intArrayOf(
            0xFFE53935.toInt(),  // Rouge
            0xFF2196F3.toInt(),  // Bleu
            0xFF4CAF50.toInt(),  // Vert
            0xFFFF9800.toInt(),  // Orange
            0xFF9C27B0.toInt(),  // Violet
            0xFF00BCD4.toInt(),  // Cyan
            0xFFFFEB3B.toInt(),  // Jaune
            0xFFE91E63.toInt(),  // Rose
            0xFF795548.toInt(),  // Marron
            0xFF607D8B.toInt()   // Gris-bleu
        )

        // Dimensions en dp
        private const val TRACK_HEIGHT_DP = 4f
        private const val THUMB_RADIUS_DP = 8f
        private const val MARKER_BUTTON_SIZE_DP = 20f
        private const val MARKER_DOT_RADIUS_DP = 6f
        private const val LINE_SPACING_DP = 20f  // Augmenté pour écarter les deux lignes
        private const val HORIZONTAL_PADDING_DP = 16f
        private const val VERTICAL_PADDING_DP = 8f  // Padding vertical pour agrandir la boîte
    }

    // Données
    private var progress: Float = 0f  // 0.0 - 1.0
    private var maxDurationMs: Long = 0L
    private val markers = mutableListOf<Marker>()

    // Dimensions calculées
    private var trackHeight = 0f
    private var thumbRadius = 0f
    private var markerButtonSize = 0f
    private var markerDotRadius = 0f
    private var lineSpacing = 0f
    private var horizontalPadding = 0f
    private var verticalPadding = 0f
    private var trackStartX = 0f
    private var trackEndX = 0f
    private var trackWidth = 0f

    // Y positions des 2 lignes
    private var line1Y = 0f  // Seekbar
    private var line2Y = 0f  // Marqueurs placés + bouton "+"

    // Callbacks
    var onProgressChanged: ((progress: Float, fromUser: Boolean) -> Unit)? = null
    var onMarkerClicked: ((positionMs: Long) -> Unit)? = null
    var onMarkersChanged: ((markers: List<Long>) -> Unit)? = null

    // État du drag
    private var isDragging = false
    private var isHoveringMarkerButton = false

    // Paints
    private val trackBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.FILL
    }

    private val trackProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5CC")
        style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 2f, Color.parseColor("#40000000"))
    }

    private val thumbGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000E5CC")
        style = Paint.Style.FILL
    }

    private val markerButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.FILL
    }

    private val markerButtonIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    private val markerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerDotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)  // Pour les ombres
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val density = resources.displayMetrics.density
        trackHeight = TRACK_HEIGHT_DP * density
        thumbRadius = THUMB_RADIUS_DP * density
        markerButtonSize = MARKER_BUTTON_SIZE_DP * density
        markerDotRadius = MARKER_DOT_RADIUS_DP * density
        lineSpacing = LINE_SPACING_DP * density
        horizontalPadding = HORIZONTAL_PADDING_DP * density
        verticalPadding = VERTICAL_PADDING_DP * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        trackStartX = horizontalPadding + thumbRadius
        trackEndX = w - horizontalPadding - thumbRadius
        trackWidth = trackEndX - trackStartX

        // Positionner les lignes: seekbar en haut, marqueurs en bas
        line1Y = verticalPadding + thumbRadius  // Seekbar en haut avec padding
        line2Y = h - verticalPadding - markerButtonSize / 2  // Marqueurs/bouton en bas avec padding

        // Créer le gradient pour la track
        trackProgressPaint.shader = LinearGradient(
            trackStartX, 0f, trackEndX, 0f,
            intArrayOf(
                Color.parseColor("#00E5CC"),
                Color.parseColor("#4ECDC4")
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Hauteur totale: padding haut + seekbar + espacement + marqueurs + padding bas
        val desiredHeight = (verticalPadding * 2 + thumbRadius * 2 + lineSpacing + markerButtonSize).toInt()

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val thumbX = trackStartX + trackWidth * progress

        // === Ligne 1: Seekbar ===

        // Track background
        val trackRect = RectF(trackStartX, line1Y - trackHeight / 2, trackEndX, line1Y + trackHeight / 2)
        canvas.drawRoundRect(trackRect, trackHeight / 2, trackHeight / 2, trackBackgroundPaint)

        // Track progress
        val progressRect = RectF(trackStartX, line1Y - trackHeight / 2, thumbX, line1Y + trackHeight / 2)
        canvas.drawRoundRect(progressRect, trackHeight / 2, trackHeight / 2, trackProgressPaint)

        // Thumb glow
        if (isDragging) {
            canvas.drawCircle(thumbX, line1Y, thumbRadius * 1.8f, thumbGlowPaint)
        }

        // Thumb
        canvas.drawCircle(thumbX, line1Y, thumbRadius, thumbPaint)

        // === Ligne 2: Marqueurs placés + bouton "+" ===

        // Ligne de fond pour les marqueurs
        val markerLineRect = RectF(trackStartX, line2Y - 2, trackEndX, line2Y + 2)
        canvas.drawRoundRect(markerLineRect, 2f, 2f, markerLinePaint)

        // Dessiner chaque marqueur
        for (marker in markers) {
            val markerX = trackStartX + trackWidth * marker.progress

            // Glow
            markerDotGlowPaint.color = adjustAlpha(marker.color, 0.3f)
            canvas.drawCircle(markerX, line2Y, markerDotRadius * 1.5f, markerDotGlowPaint)

            // Point
            markerDotPaint.color = marker.color
            canvas.drawCircle(markerX, line2Y, markerDotRadius, markerDotPaint)
        }

        // Bouton "+" qui suit le thumb (dessiné par-dessus les marqueurs)
        val buttonAlpha = if (isHoveringMarkerButton) 255 else 180
        markerButtonPaint.alpha = buttonAlpha

        // Fond du bouton
        canvas.drawCircle(thumbX, line2Y, markerButtonSize / 2, markerButtonPaint)

        // Icône "+" dans le bouton
        val iconSize = markerButtonSize * 0.3f
        markerButtonIconPaint.strokeWidth = 2f * resources.displayMetrics.density
        canvas.drawLine(thumbX - iconSize, line2Y, thumbX + iconSize, line2Y, markerButtonIconPaint)
        canvas.drawLine(thumbX, line2Y - iconSize, thumbX, line2Y + iconSize, markerButtonIconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val thumbX = trackStartX + trackWidth * progress

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // IMPORTANT: Vérifier les marqueurs existants EN PREMIER
                // pour éviter d'ajouter un marqueur quand on clique sur un existant
                val clickedMarker = findMarkerAt(x, y)
                if (clickedMarker != null) {
                    val positionMs = (clickedMarker.progress * maxDurationMs).toLong()
                    onMarkerClicked?.invoke(positionMs)
                    return true
                }

                // Ensuite vérifier si on touche le bouton "+" (maintenant sur ligne 2)
                if (isInMarkerButton(x, y, thumbX)) {
                    isHoveringMarkerButton = true
                    invalidate()
                    return true
                }

                // Vérifier si on touche la seekbar (ligne 1)
                if (isInSeekBarArea(x, y)) {
                    isDragging = true
                    updateProgressFromTouch(x)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateProgressFromTouch(x)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHoveringMarkerButton && isInMarkerButton(x, y, thumbX)) {
                    // Vérifier qu'on n'est pas sur un marqueur existant avant d'ajouter
                    val markerAtPosition = findMarkerAt(x, y)
                    if (markerAtPosition == null) {
                        addMarker()
                    }
                }

                isDragging = false
                isHoveringMarkerButton = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun isInSeekBarArea(x: Float, y: Float): Boolean {
        val hitAreaHeight = thumbRadius * 3
        return x >= trackStartX - thumbRadius &&
               x <= trackEndX + thumbRadius &&
               y >= line1Y - hitAreaHeight &&
               y <= line1Y + hitAreaHeight
    }

    private fun isInMarkerButton(x: Float, y: Float, thumbX: Float): Boolean {
        val distance = Math.sqrt(((x - thumbX) * (x - thumbX) + (y - line2Y) * (y - line2Y)).toDouble())
        return distance <= markerButtonSize
    }

    private fun findMarkerAt(x: Float, y: Float): Marker? {
        // Les marqueurs sont maintenant sur line2Y
        if (abs(y - line2Y) > markerDotRadius * 2.5f) return null

        for (marker in markers) {
            val markerX = trackStartX + trackWidth * marker.progress
            if (abs(x - markerX) <= markerDotRadius * 2.5f) {
                return marker
            }
        }
        return null
    }

    private fun updateProgressFromTouch(x: Float) {
        val newProgress = ((x - trackStartX) / trackWidth).coerceIn(0f, 1f)
        if (newProgress != progress) {
            progress = newProgress
            onProgressChanged?.invoke(progress, true)
            invalidate()
        }
    }

    private fun addMarker() {
        if (markers.size >= MAX_MARKERS) {
            // Supprimer le plus ancien
            markers.removeAt(0)
        }

        val colorIndex = markers.size % MARKER_COLORS.size
        val marker = Marker(
            progress = progress,
            color = MARKER_COLORS[colorIndex],
            positionMs = (progress * maxDurationMs).toLong()
        )
        markers.add(marker)

        onMarkersChanged?.invoke(markers.map { it.positionMs })
        invalidate()
    }

    /**
     * Supprime un marqueur par appui long (à implémenter si besoin)
     */
    fun removeMarker(index: Int) {
        if (index in markers.indices) {
            markers.removeAt(index)
            onMarkersChanged?.invoke(markers.map { it.positionMs })
            invalidate()
        }
    }

    /**
     * Efface tous les marqueurs
     */
    fun clearMarkers() {
        markers.clear()
        onMarkersChanged?.invoke(emptyList())
        invalidate()
    }

    /**
     * Définit la progression (0.0 - 1.0)
     */
    fun setProgress(value: Float, fromUser: Boolean = false) {
        val newProgress = value.coerceIn(0f, 1f)
        if (newProgress != progress) {
            progress = newProgress
            if (!fromUser) {
                invalidate()
            }
        }
    }

    /**
     * Retourne la progression actuelle (0.0 - 1.0)
     */
    fun getProgress(): Float = progress

    /**
     * Définit la durée maximale en ms
     */
    fun setMaxDuration(durationMs: Long) {
        maxDurationMs = durationMs
    }

    /**
     * Retourne la position actuelle en ms
     */
    fun getCurrentPositionMs(): Long = (progress * maxDurationMs).toLong()

    /**
     * Charge les marqueurs depuis une liste de positions en ms
     */
    fun setMarkers(positionsMs: List<Long>) {
        markers.clear()
        positionsMs.forEachIndexed { index, posMs ->
            if (maxDurationMs > 0) {
                val prog = (posMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)
                markers.add(Marker(
                    progress = prog,
                    color = MARKER_COLORS[index % MARKER_COLORS.size],
                    positionMs = posMs
                ))
            }
        }
        invalidate()
    }

    /**
     * Retourne la liste des positions des marqueurs en ms
     */
    fun getMarkerPositions(): List<Long> = markers.map { it.positionMs }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    /**
     * Data class pour un marqueur
     */
    private data class Marker(
        val progress: Float,
        val color: Int,
        val positionMs: Long
    )
}
