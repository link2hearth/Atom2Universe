package com.Atom2Universe.app.pixelart.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Types de formes supportées
 */
enum class ShapeType {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    CIRCLE,
    OVAL,
    LINE,
    DASHED_LINE,
    ARROW,
    TRIANGLE,
    DIAMOND,
    HEXAGON,
    STAR,
    POLYGON
}

/**
 * Objet forme vectorielle sur la toile infinie.
 *
 * Optimisations :
 * - Path pré-alloué et mis en cache
 * - Paint pré-alloués pour fill et stroke
 * - Recalcul du path seulement si dirty
 */
class ShapeObject(
    override val id: String = java.util.UUID.randomUUID().toString()
) : CanvasObject(CanvasObjectType.SHAPE) {

    // ========== TYPE DE FORME ==========

    var shapeType: ShapeType = ShapeType.RECTANGLE
        set(value) {
            if (field != value) {
                field = value
                pathDirty = true
                markDirty()
            }
        }

    // ========== STYLE ==========

    /**
     * Couleur de remplissage (null = pas de remplissage)
     */
    var fillColor: Int? = Color.WHITE
        set(value) {
            field = value
            updateFillPaint()
        }

    /**
     * Couleur du contour
     */
    var strokeColor: Int = Color.BLACK
        set(value) {
            field = value
            updateStrokePaint()
        }

    /**
     * Épaisseur du contour (0 = pas de contour)
     */
    var strokeWidth: Float = 2f
        set(value) {
            field = value.coerceAtLeast(0f)
            strokePaint.strokeWidth = field
        }

    /**
     * Rayon des coins arrondis (pour rectangles)
     */
    var cornerRadius: Float = 0f
        set(value) {
            if (field != value) {
                field = value.coerceAtLeast(0f)
                pathDirty = true
            }
        }

    /**
     * Nombre de côtés (pour polygones)
     */
    var sides: Int = 5
        set(value) {
            if (field != value) {
                field = value.coerceIn(3, 20)
                pathDirty = true
            }
        }

    /**
     * Nombre de branches (pour étoiles)
     */
    var starPoints: Int = 5
        set(value) {
            if (field != value) {
                field = value.coerceIn(3, 20)
                pathDirty = true
            }
        }

    /**
     * Ratio intérieur pour étoiles (0.0 - 1.0)
     */
    var starInnerRatio: Float = 0.5f
        set(value) {
            if (field != value) {
                field = value.coerceIn(0.1f, 0.9f)
                pathDirty = true
            }
        }

    // ========== LIGNE ET FLÈCHE ==========

    /**
     * Point de fin pour les lignes (relatif au centre)
     */
    var endX: Float = 100f
        set(value) {
            if (field != value) {
                field = value
                pathDirty = true
                markDirty()
            }
        }

    var endY: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                pathDirty = true
                markDirty()
            }
        }

    /**
     * Taille de la pointe de flèche
     */
    var arrowHeadSize: Float = 20f

    // ========== OPTIMISATION : Pré-allocation ==========

    /**
     * Path de la forme (pré-alloué)
     */
    private val shapePath = Path()
    private var pathDirty = true

    // Cache des dimensions pour détecter les changements
    private var cachedWidth = 0f
    private var cachedHeight = 0f

    /**
     * Paint pour le remplissage
     */
    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * Paint pour le contour
     */
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Rectangle temporaire pour calculs
     */
    private val tempRect = RectF()

    init {
        // Dimensions par défaut
        width = 100f
        height = 100f
        updateFillPaint()
        updateStrokePaint()
    }

    private fun updateFillPaint() {
        fillColor?.let {
            fillPaint.color = it
            fillPaint.alpha = (opacity * 255 * (Color.alpha(it) / 255f)).toInt()
        }
    }

    private fun updateStrokePaint() {
        strokePaint.color = strokeColor
        strokePaint.alpha = (opacity * 255 * (Color.alpha(strokeColor) / 255f)).toInt()
        strokePaint.strokeWidth = strokeWidth
    }

    // ========== CONSTRUCTION DU PATH ==========

    /**
     * Reconstruit le path de la forme si nécessaire
     */
    private fun rebuildPathIfNeeded() {
        // Vérifier si les dimensions ont changé
        if (width != cachedWidth || height != cachedHeight) {
            pathDirty = true
            cachedWidth = width
            cachedHeight = height
        }

        if (!pathDirty) return

        shapePath.reset()

        val halfW = width / 2f
        val halfH = height / 2f

        when (shapeType) {
            ShapeType.RECTANGLE -> buildRectanglePath(halfW, halfH)
            ShapeType.ROUNDED_RECTANGLE -> buildRoundedRectanglePath(halfW, halfH)
            ShapeType.CIRCLE -> buildCirclePath(halfW, halfH)
            ShapeType.OVAL -> buildOvalPath(halfW, halfH)
            ShapeType.LINE -> buildLinePath()
            ShapeType.DASHED_LINE -> buildLinePath()  // Visually handled in draw()
            ShapeType.ARROW -> buildArrowPath()
            ShapeType.TRIANGLE -> buildTrianglePath(halfW, halfH)
            ShapeType.DIAMOND -> buildDiamondPath(halfW, halfH)
            ShapeType.HEXAGON -> buildPolygonPath(halfW, halfH, 6)
            ShapeType.POLYGON -> buildPolygonPath(halfW, halfH, sides)
            ShapeType.STAR -> buildStarPath(halfW, halfH)
        }

        pathDirty = false
    }

    private fun buildRectanglePath(halfW: Float, halfH: Float) {
        if (cornerRadius > 0f) {
            tempRect.set(-halfW, -halfH, halfW, halfH)
            shapePath.addRoundRect(tempRect, cornerRadius, cornerRadius, Path.Direction.CW)
        } else {
            shapePath.addRect(-halfW, -halfH, halfW, halfH, Path.Direction.CW)
        }
    }

    private fun buildRoundedRectanglePath(halfW: Float, halfH: Float) {
        // Rounded rectangle with automatic corner radius based on size
        val radius = minOf(halfW, halfH) * 0.3f
        tempRect.set(-halfW, -halfH, halfW, halfH)
        shapePath.addRoundRect(tempRect, radius, radius, Path.Direction.CW)
    }

    private fun buildDiamondPath(halfW: Float, halfH: Float) {
        shapePath.moveTo(0f, -halfH)       // Top
        shapePath.lineTo(halfW, 0f)        // Right
        shapePath.lineTo(0f, halfH)        // Bottom
        shapePath.lineTo(-halfW, 0f)       // Left
        shapePath.close()
    }

    private fun buildCirclePath(halfW: Float, halfH: Float) {
        val radius = minOf(halfW, halfH)
        shapePath.addCircle(0f, 0f, radius, Path.Direction.CW)
    }

    private fun buildOvalPath(halfW: Float, halfH: Float) {
        tempRect.set(-halfW, -halfH, halfW, halfH)
        shapePath.addOval(tempRect, Path.Direction.CW)
    }

    private fun buildLinePath() {
        shapePath.moveTo(0f, 0f)
        shapePath.lineTo(endX, endY)
    }

    private fun buildArrowPath() {
        // Ligne principale
        shapePath.moveTo(0f, 0f)
        shapePath.lineTo(endX, endY)

        // Calculer l'angle de la ligne
        val angle = kotlin.math.atan2(endY.toDouble(), endX.toDouble())
        val arrowAngle = Math.PI / 6  // 30 degrés

        // Pointe de flèche
        val ax1 = endX - arrowHeadSize * kotlin.math.cos(angle - arrowAngle).toFloat()
        val ay1 = endY - arrowHeadSize * kotlin.math.sin(angle - arrowAngle).toFloat()
        val ax2 = endX - arrowHeadSize * kotlin.math.cos(angle + arrowAngle).toFloat()
        val ay2 = endY - arrowHeadSize * kotlin.math.sin(angle + arrowAngle).toFloat()

        shapePath.moveTo(endX, endY)
        shapePath.lineTo(ax1, ay1)
        shapePath.moveTo(endX, endY)
        shapePath.lineTo(ax2, ay2)
    }

    private fun buildTrianglePath(halfW: Float, halfH: Float) {
        shapePath.moveTo(0f, -halfH)           // Sommet
        shapePath.lineTo(halfW, halfH)         // Bas droite
        shapePath.lineTo(-halfW, halfH)        // Bas gauche
        shapePath.close()
    }

    private fun buildPolygonPath(halfW: Float, halfH: Float, numSides: Int) {
        val radius = minOf(halfW, halfH)
        val angleStep = (2 * Math.PI / numSides).toFloat()
        val startAngle = -Math.PI.toFloat() / 2  // Commencer en haut

        for (i in 0 until numSides) {
            val angle = startAngle + i * angleStep
            val px = radius * kotlin.math.cos(angle)
            val py = radius * kotlin.math.sin(angle)

            if (i == 0) {
                shapePath.moveTo(px, py)
            } else {
                shapePath.lineTo(px, py)
            }
        }
        shapePath.close()
    }

    private fun buildStarPath(halfW: Float, halfH: Float) {
        val outerRadius = minOf(halfW, halfH)
        val innerRadius = outerRadius * starInnerRatio
        val points = starPoints * 2
        val angleStep = (Math.PI / starPoints).toFloat()
        val startAngle = -Math.PI.toFloat() / 2

        for (i in 0 until points) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = startAngle + i * angleStep
            val px = radius * kotlin.math.cos(angle)
            val py = radius * kotlin.math.sin(angle)

            if (i == 0) {
                shapePath.moveTo(px, py)
            } else {
                shapePath.lineTo(px, py)
            }
        }
        shapePath.close()
    }

    // ========== DESSIN ==========

    override fun draw(canvas: Canvas, paint: Paint, selected: Boolean) {
        if (!visible || opacity <= 0f) return

        rebuildPathIfNeeded()
        updateFillPaint()
        updateStrokePaint()

        canvas.save()

        // Appliquer les transformations
        canvas.translate(x, y)
        if (rotation != 0f) {
            canvas.rotate(rotation)
        }
        if (scaleX != 1f || scaleY != 1f) {
            canvas.scale(scaleX, scaleY)
        }

        // Dessiner le remplissage
        if (fillColor != null && shapeType != ShapeType.LINE && shapeType != ShapeType.ARROW && shapeType != ShapeType.DASHED_LINE) {
            canvas.drawPath(shapePath, fillPaint)
        }

        // Dessiner le contour
        if (strokeWidth > 0f) {
            if (shapeType == ShapeType.DASHED_LINE) {
                // Ligne pointillée
                val dashPaint = Paint(strokePaint).apply {
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
                }
                canvas.drawPath(shapePath, dashPaint)
            } else {
                canvas.drawPath(shapePath, strokePaint)
            }
        }

        canvas.restore()

        // Handles de sélection
        if (selected) {
            drawSelectionHandles(canvas, paint)
        }
    }

    private fun drawSelectionHandles(canvas: Canvas, paint: Paint) {
        val bounds = getBounds()

        // Contour de sélection
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#2196F3")
        paint.strokeWidth = 2f
        canvas.drawRect(bounds, paint)

        // Handles
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val handleSize = HANDLE_SIZE / 2f

        val positions = listOf(
            bounds.left to bounds.top,
            bounds.centerX() to bounds.top,
            bounds.right to bounds.top,
            bounds.left to bounds.centerY(),
            bounds.right to bounds.centerY(),
            bounds.left to bounds.bottom,
            bounds.centerX() to bounds.bottom,
            bounds.right to bounds.bottom
        )

        for ((hx, hy) in positions) {
            canvas.drawRect(
                hx - handleSize, hy - handleSize,
                hx + handleSize, hy + handleSize,
                paint
            )
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#2196F3")
            canvas.drawRect(
                hx - handleSize, hy - handleSize,
                hx + handleSize, hy + handleSize,
                paint
            )
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
        }
    }

    // ========== BOUNDS SPÉCIFIQUES ==========

    override fun calculateBounds(outBounds: RectF) {
        when (shapeType) {
            ShapeType.LINE, ShapeType.DASHED_LINE, ShapeType.ARROW -> {
                // Pour les lignes, calculer les bounds entre origine et fin
                val minX = minOf(0f, endX) * scaleX + x
                val maxX = maxOf(0f, endX) * scaleX + x
                val minY = minOf(0f, endY) * scaleY + y
                val maxY = maxOf(0f, endY) * scaleY + y
                outBounds.set(minX, minY, maxX, maxY)
                // Ajouter un peu de marge pour la visibilité
                outBounds.inset(-strokeWidth, -strokeWidth)
            }
            else -> {
                super.calculateBounds(outBounds)
            }
        }
    }

    override fun contains(worldX: Float, worldY: Float): Boolean {
        when (shapeType) {
            ShapeType.LINE, ShapeType.DASHED_LINE, ShapeType.ARROW -> {
                // Pour les lignes, vérifier la distance au segment
                val dist = pointToSegmentDistance(
                    worldX, worldY,
                    x, y,
                    x + endX * scaleX, y + endY * scaleY
                )
                return dist < TOUCH_SLOP
            }
            else -> return super.contains(worldX, worldY)
        }
    }

    /**
     * Distance d'un point à un segment
     */
    private fun pointToSegmentDistance(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSq = dx * dx + dy * dy

        if (lengthSq == 0f) {
            // Segment de longueur 0
            return kotlin.math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        }

        var t = ((px - x1) * dx + (py - y1) * dy) / lengthSq
        t = t.coerceIn(0f, 1f)

        val nearestX = x1 + t * dx
        val nearestY = y1 + t * dy

        return kotlin.math.sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
    }

    // ========== COPIE ==========

    override fun copy(): CanvasObject {
        val copy = ShapeObject()
        copy.x = x
        copy.y = y
        copy.width = width
        copy.height = height
        copy.rotation = rotation
        copy.scaleX = scaleX
        copy.scaleY = scaleY
        copy.opacity = opacity
        copy.visible = visible
        copy.locked = locked
        copy.zIndex = zIndex
        copy.name = name
        copy.shapeType = shapeType
        copy.fillColor = fillColor
        copy.strokeColor = strokeColor
        copy.strokeWidth = strokeWidth
        copy.cornerRadius = cornerRadius
        copy.sides = sides
        copy.starPoints = starPoints
        copy.starInnerRatio = starInnerRatio
        copy.endX = endX
        copy.endY = endY
        copy.arrowHeadSize = arrowHeadSize
        return copy
    }

    companion object {
        /**
         * Crée un rectangle
         */
        fun rectangle(x: Float, y: Float, w: Float, h: Float): ShapeObject {
            return ShapeObject().apply {
                this.x = x
                this.y = y
                this.width = w
                this.height = h
                this.shapeType = ShapeType.RECTANGLE
            }
        }

        /**
         * Crée un cercle
         */
        fun circle(x: Float, y: Float, radius: Float): ShapeObject {
            return ShapeObject().apply {
                this.x = x
                this.y = y
                this.width = radius * 2
                this.height = radius * 2
                this.shapeType = ShapeType.CIRCLE
            }
        }

        /**
         * Crée une ligne
         */
        fun line(x1: Float, y1: Float, x2: Float, y2: Float): ShapeObject {
            return ShapeObject().apply {
                this.x = x1
                this.y = y1
                this.endX = x2 - x1
                this.endY = y2 - y1
                this.shapeType = ShapeType.LINE
                this.fillColor = null
            }
        }
    }
}
