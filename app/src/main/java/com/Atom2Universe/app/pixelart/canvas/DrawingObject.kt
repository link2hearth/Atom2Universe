package com.Atom2Universe.app.pixelart.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Objet dessin libre sur la toile infinie.
 *
 * Utilise des chemins (Path) pour un rendu fluide et correct à tous les niveaux de zoom.
 * Stocke les points du tracé pour reconstruire le dessin.
 */
class DrawingObject(
    override val id: String = java.util.UUID.randomUUID().toString()
) : CanvasObject(CanvasObjectType.DRAWING) {

    // ========== STOCKAGE DES POINTS ==========

    /**
     * Liste des segments de tracé.
     * Chaque segment contient : (startX, startY, endX, endY, color, strokeWidth)
     */
    private val strokes = mutableListOf<StrokeSegment>()

    /**
     * Segment de tracé
     */
    data class StrokeSegment(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val color: Int,
        val strokeWidth: Float
    )

    // ========== BOUNDS ==========

    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var maxX = Float.MIN_VALUE
    private var maxY = Float.MIN_VALUE

    // ========== PROPRIÉTÉS DE DESSIN ==========

    /**
     * Taille du pinceau (épaisseur du trait)
     */
    var brushSize: Float = 8f

    /**
     * Couleur du pinceau
     */
    var brushColor: Int = Color.BLACK

    // ========== ÉTAT DU DESSIN EN COURS ==========

    private var lastX: Float? = null
    private var lastY: Float? = null
    private var isDrawingFinalized = false  // True après endStroke()

    // ========== PAINT PRÉ-ALLOUÉ ==========

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    // ========== MÉTHODES DE DESSIN ==========

    /**
     * Commence un nouveau tracé à la position donnée (coordonnées monde)
     */
    fun startStroke(worldX: Float, worldY: Float, color: Int, size: Float) {
        lastX = worldX
        lastY = worldY
        brushColor = color
        brushSize = size

        // Ajouter un point initial (cercle)
        addSegment(worldX, worldY, worldX, worldY, color, size)
    }

    /**
     * Continue le tracé vers une nouvelle position
     */
    fun continueStroke(worldX: Float, worldY: Float, color: Int, size: Float) {
        val prevX = lastX
        val prevY = lastY

        if (prevX != null && prevY != null) {
            addSegment(prevX, prevY, worldX, worldY, color, size)
        } else {
            addSegment(worldX, worldY, worldX, worldY, color, size)
        }

        lastX = worldX
        lastY = worldY
    }

    /**
     * Termine le tracé en cours
     */
    fun endStroke() {
        lastX = null
        lastY = null
        recalculateBoundsFromStrokes()
        isDrawingFinalized = true
    }

    /**
     * Ajoute un segment au tracé
     */
    private fun addSegment(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, size: Float) {
        strokes.add(StrokeSegment(x1, y1, x2, y2, color, size))

        // Mise à jour des bounds
        val halfSize = size / 2f
        updateBounds(x1 - halfSize, y1 - halfSize)
        updateBounds(x1 + halfSize, y1 + halfSize)
        updateBounds(x2 - halfSize, y2 - halfSize)
        updateBounds(x2 + halfSize, y2 + halfSize)

        markDirty()
    }

    private fun updateBounds(px: Float, py: Float) {
        if (px < minX) minX = px
        if (px > maxX) maxX = px
        if (py < minY) minY = py
        if (py > maxY) maxY = py
    }

    /**
     * Recalcule les bounds à partir des segments
     */
    private fun recalculateBoundsFromStrokes() {
        if (strokes.isEmpty()) {
            minX = x
            minY = y
            maxX = x
            maxY = y
            width = 0f
            height = 0f
            return
        }

        minX = Float.MAX_VALUE
        minY = Float.MAX_VALUE
        maxX = Float.MIN_VALUE
        maxY = Float.MIN_VALUE

        for (stroke in strokes) {
            val halfSize = stroke.strokeWidth / 2f
            updateBounds(stroke.x1 - halfSize, stroke.y1 - halfSize)
            updateBounds(stroke.x1 + halfSize, stroke.y1 + halfSize)
            updateBounds(stroke.x2 - halfSize, stroke.y2 - halfSize)
            updateBounds(stroke.x2 + halfSize, stroke.y2 + halfSize)
        }

        // Position = centre (cohérent avec les autres CanvasObject)
        x = (minX + maxX) / 2f
        y = (minY + maxY) / 2f
        width = maxX - minX
        height = maxY - minY

        markDirty()
    }

    // ========== IMPLÉMENTATION CANVASOBJECT ==========

    override fun calculateBounds(outBounds: RectF) {
        if (strokes.isEmpty()) {
            outBounds.set(x, y, x, y)
            return
        }

        if (isDrawingFinalized) {
            // Dessin finalisé : utiliser x, y comme centre avec width/height et scale
            val halfW = (width * scaleX) / 2f
            val halfH = (height * scaleY) / 2f
            outBounds.set(x - halfW, y - halfH, x + halfW, y + halfH)
        } else {
            // Dessin en cours : utiliser les bounds réels des strokes
            outBounds.set(minX, minY, maxX, maxY)
        }
    }

    override fun draw(canvas: Canvas, paint: Paint, selected: Boolean) {
        if (!visible || opacity <= 0f) return
        if (strokes.isEmpty()) return

        strokePaint.alpha = (opacity * 255).toInt()

        if (isDrawingFinalized) {
            // Dessin finalisé : utiliser les transformations (position, scale, rotation)
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f

            canvas.save()

            canvas.translate(x, y)
            if (rotation != 0f) {
                canvas.rotate(rotation)
            }
            canvas.scale(scaleX, scaleY)

            // Dessiner les segments en coordonnées relatives au centre
            for (stroke in strokes) {
                strokePaint.color = stroke.color
                strokePaint.strokeWidth = stroke.strokeWidth

                val relX1 = stroke.x1 - centerX
                val relY1 = stroke.y1 - centerY
                val relX2 = stroke.x2 - centerX
                val relY2 = stroke.y2 - centerY

                if (relX1 == relX2 && relY1 == relY2) {
                    strokePaint.style = Paint.Style.FILL
                    canvas.drawCircle(relX1, relY1, stroke.strokeWidth / 2f, strokePaint)
                    strokePaint.style = Paint.Style.STROKE
                } else {
                    canvas.drawLine(relX1, relY1, relX2, relY2, strokePaint)
                }
            }

            canvas.restore()
        } else {
            // Dessin en cours : dessiner directement en coordonnées absolues
            for (stroke in strokes) {
                strokePaint.color = stroke.color
                strokePaint.strokeWidth = stroke.strokeWidth

                if (stroke.x1 == stroke.x2 && stroke.y1 == stroke.y2) {
                    strokePaint.style = Paint.Style.FILL
                    canvas.drawCircle(stroke.x1, stroke.y1, stroke.strokeWidth / 2f, strokePaint)
                    strokePaint.style = Paint.Style.STROKE
                } else {
                    canvas.drawLine(stroke.x1, stroke.y1, stroke.x2, stroke.y2, strokePaint)
                }
            }
        }
    }

    override fun contains(worldX: Float, worldY: Float): Boolean {
        if (strokes.isEmpty()) return false

        // Transformer le point monde en coordonnées locales (inverser les transformations)
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        // Translation inverse
        var localX = worldX - x
        var localY = worldY - y

        // Rotation inverse
        if (rotation != 0f) {
            val rad = Math.toRadians(-rotation.toDouble())
            val cos = kotlin.math.cos(rad).toFloat()
            val sin = kotlin.math.sin(rad).toFloat()
            val rotX = localX * cos - localY * sin
            val rotY = localX * sin + localY * cos
            localX = rotX
            localY = rotY
        }

        // Scale inverse
        localX /= scaleX
        localY /= scaleY

        // Reconvertir en coordonnées des strokes (ajouter le centre original)
        val strokeX = localX + centerX
        val strokeY = localY + centerY

        // Vérifier si le point est proche d'un segment
        val tolerance = TOUCH_SLOP

        for (stroke in strokes) {
            val dist = distanceToSegment(strokeX, strokeY, stroke.x1, stroke.y1, stroke.x2, stroke.y2)
            if (dist <= stroke.strokeWidth / 2f + tolerance) {
                return true
            }
        }

        return false
    }

    /**
     * Calcule la distance d'un point à un segment de ligne
     */
    private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0f && dy == 0f) {
            // Le segment est un point
            return kotlin.math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        }

        // Projection du point sur la ligne
        val t = kotlin.math.max(0f, kotlin.math.min(1f,
            ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))

        val projX = x1 + t * dx
        val projY = y1 + t * dy

        return kotlin.math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
    }

    override fun recycle() {
        strokes.clear()
    }

    override fun copy(): CanvasObject {
        val copy = DrawingObject()
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
        copy.brushSize = brushSize
        copy.brushColor = brushColor
        copy.minX = minX
        copy.minY = minY
        copy.maxX = maxX
        copy.maxY = maxY

        // Copier les segments
        copy.strokes.addAll(strokes)

        // Une copie est toujours finalisée
        copy.finalizeBounds()

        return copy
    }

    // ========== UTILITAIRES ==========

    /**
     * Nombre de segments
     */
    fun getStrokeCount(): Int = strokes.size

    /**
     * Vérifie si le dessin est vide
     */
    fun isEmpty(): Boolean = strokes.isEmpty()

    /**
     * Retourne tous les segments (pour sérialisation)
     */
    fun getStrokes(): List<StrokeSegment> = strokes.toList()

    /**
     * Ajoute un segment directement (pour désérialisation)
     */
    fun addStrokeSegment(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, strokeWidth: Float) {
        addSegment(x1, y1, x2, y2, color, strokeWidth)
    }

    /**
     * Finalise les bounds après import (pour désérialisation)
     */
    fun finalizeBounds() {
        recalculateBoundsFromStrokes()
        isDrawingFinalized = true
    }

    /**
     * Efface le dessin
     */
    fun clear() {
        strokes.clear()
        minX = Float.MAX_VALUE
        minY = Float.MAX_VALUE
        maxX = Float.MIN_VALUE
        maxY = Float.MIN_VALUE
        width = 0f
        height = 0f
        markDirty()
    }
}
