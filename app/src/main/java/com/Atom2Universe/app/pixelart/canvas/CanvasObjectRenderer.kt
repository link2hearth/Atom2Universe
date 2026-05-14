package com.Atom2Universe.app.pixelart.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Gestionnaire de rendu optimisé pour les objets de la toile infinie.
 *
 * Optimisations :
 * - Viewport culling (ne dessine que ce qui est visible)
 * - Tri par zIndex une seule fois
 * - Paint pré-alloués et réutilisés
 * - Spatial indexing simple pour grandes scènes
 */
class CanvasObjectRenderer {

    // ========== CONFIGURATION ==========

    /**
     * Couleur de fond du canvas
     */
    var backgroundColor: Int = Color.WHITE

    /**
     * Afficher la grille
     */
    var showGrid: Boolean = false

    /**
     * Taille de la grille (en unités monde)
     */
    var gridSize: Float = 50f

    /**
     * Couleur de la grille
     */
    var gridColor: Int = Color.argb(50, 0, 0, 0)

    // ========== PAINTS PRÉ-ALLOUÉS ==========

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val objectPaint = Paint().apply {
        isAntiAlias = true
    }

    private val selectionPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#2196F3")
    }

    // ========== VIEWPORT ==========

    /**
     * Rectangle du viewport pré-alloué
     */
    private val viewportRect = RectF()

    /**
     * Rectangle temporaire pour calculs
     */
    private val tempRect = RectF()

    // ========== STATISTIQUES ==========

    /**
     * Nombre d'objets dessinés lors du dernier rendu
     */
    var lastDrawnCount: Int = 0
        private set

    /**
     * Nombre d'objets total
     */
    var totalObjectCount: Int = 0
        private set

    /**
     * Temps du dernier rendu en ms
     */
    var lastRenderTimeMs: Long = 0
        private set

    // ========== RENDU PRINCIPAL ==========

    /**
     * Dessine tous les objets visibles sur le canvas.
     *
     * @param canvas Le canvas Android
     * @param objects Liste des objets à dessiner
     * @param viewportX Position X du viewport dans le monde
     * @param viewportY Position Y du viewport dans le monde
     * @param viewportZoom Niveau de zoom
     * @param viewportWidth Largeur du viewport en pixels écran
     * @param viewportHeight Hauteur du viewport en pixels écran
     * @param selectedObject Objet actuellement sélectionné (ou null)
     */
    fun render(
        canvas: Canvas,
        objects: List<CanvasObject>,
        viewportX: Float,
        viewportY: Float,
        viewportZoom: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        selectedObject: CanvasObject? = null
    ) {
        val startTime = System.nanoTime()

        totalObjectCount = objects.size
        lastDrawnCount = 0

        // Calculer les bounds du viewport dans le monde
        val worldWidth = viewportWidth / viewportZoom
        val worldHeight = viewportHeight / viewportZoom
        viewportRect.set(
            viewportX,
            viewportY,
            viewportX + worldWidth,
            viewportY + worldHeight
        )

        // Dessiner le fond
        backgroundPaint.color = backgroundColor
        canvas.drawColor(backgroundColor)

        // Appliquer la transformation du viewport
        canvas.save()
        canvas.scale(viewportZoom, viewportZoom)
        canvas.translate(-viewportX, -viewportY)

        // Dessiner la grille si activée
        if (showGrid && viewportZoom >= 0.1f) {
            drawGrid(canvas, viewportRect, viewportZoom)
        }

        // Filtrer et trier les objets visibles
        val visibleObjects = objects
            .filter { it.visible && it.intersects(viewportRect) }
            .sortedWith(CanvasObjectZIndexComparator)

        // Dessiner les objets
        for (obj in visibleObjects) {
            val isSelected = obj === selectedObject

            when (obj) {
                is ImageObject -> {
                    // Utiliser les mipmaps pour les images
                    obj.drawWithMipmap(canvas, objectPaint, viewportZoom, isSelected)
                }
                else -> {
                    obj.draw(canvas, objectPaint, isSelected)
                }
            }

            lastDrawnCount++
        }

        canvas.restore()

        // Temps de rendu
        lastRenderTimeMs = (System.nanoTime() - startTime) / 1_000_000
    }

    /**
     * Dessine la grille
     */
    private fun drawGrid(canvas: Canvas, viewport: RectF, zoom: Float) {
        gridPaint.color = gridColor
        gridPaint.strokeWidth = 1f / zoom  // Épaisseur constante à l'écran

        // Calculer les lignes de grille visibles
        val startX = (viewport.left / gridSize).toInt() * gridSize
        val startY = (viewport.top / gridSize).toInt() * gridSize

        // Lignes verticales
        var x = startX
        while (x <= viewport.right) {
            canvas.drawLine(x, viewport.top, x, viewport.bottom, gridPaint)
            x += gridSize
        }

        // Lignes horizontales
        var y = startY
        while (y <= viewport.bottom) {
            canvas.drawLine(viewport.left, y, viewport.right, y, gridPaint)
            y += gridSize
        }
    }

    /**
     * Trouve l'objet sous un point (coordonnées monde)
     */
    fun hitTest(
        objects: List<CanvasObject>,
        worldX: Float,
        worldY: Float
    ): CanvasObject? {
        // Parcourir en ordre inverse de zIndex (objets devant en premier)
        return objects
            .filter { it.visible && !it.locked }
            .sortedByDescending { it.zIndex }
            .firstOrNull { it.contains(worldX, worldY) }
    }

    /**
     * Trouve tous les objets dans un rectangle (coordonnées monde)
     */
    fun findObjectsInRect(
        objects: List<CanvasObject>,
        rect: RectF
    ): List<CanvasObject> {
        return objects
            .filter { it.visible && it.intersects(rect) }
            .sortedBy { it.zIndex }
    }

    /**
     * Calcule les bounds englobant tous les objets
     */
    fun calculateTotalBounds(objects: List<CanvasObject>, outBounds: RectF) {
        if (objects.isEmpty()) {
            outBounds.set(0f, 0f, 100f, 100f)
            return
        }

        outBounds.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        for (obj in objects) {
            val objBounds = obj.getBounds()
            if (objBounds.left < outBounds.left) outBounds.left = objBounds.left
            if (objBounds.top < outBounds.top) outBounds.top = objBounds.top
            if (objBounds.right > outBounds.right) outBounds.right = objBounds.right
            if (objBounds.bottom > outBounds.bottom) outBounds.bottom = objBounds.bottom
        }

        // Ajouter une marge
        val margin = 50f
        outBounds.inset(-margin, -margin)
    }

    /**
     * Calcule le zoom optimal pour voir tous les objets
     */
    fun calculateFitZoom(
        objects: List<CanvasObject>,
        viewportWidth: Int,
        viewportHeight: Int
    ): Triple<Float, Float, Float> {
        calculateTotalBounds(objects, tempRect)

        val boundsWidth = tempRect.width()
        val boundsHeight = tempRect.height()

        if (boundsWidth <= 0 || boundsHeight <= 0) {
            return Triple(1f, 0f, 0f)
        }

        // Calculer le zoom pour que tout tienne
        val zoomX = viewportWidth / boundsWidth
        val zoomY = viewportHeight / boundsHeight
        val zoom = minOf(zoomX, zoomY) * 0.9f  // 90% pour avoir une marge

        // Position pour centrer
        val centerX = tempRect.centerX() - (viewportWidth / zoom) / 2f
        val centerY = tempRect.centerY() - (viewportHeight / zoom) / 2f

        return Triple(zoom, centerX, centerY)
    }

    // ========== INDEXATION SPATIALE SIMPLE ==========

    /**
     * Index spatial simple pour optimiser les recherches sur grandes scènes.
     * Divise l'espace en cellules de taille fixe.
     */
    class SpatialIndex(private val cellSize: Float = 500f) {

        private val cells = mutableMapOf<Long, MutableList<CanvasObject>>()

        /**
         * Encode une cellule en clé
         */
        private fun cellKey(cellX: Int, cellY: Int): Long {
            return cellX.toLong() + cellY.toLong() * 1_000_000
        }

        /**
         * Reconstruit l'index
         */
        fun rebuild(objects: List<CanvasObject>) {
            cells.clear()

            for (obj in objects) {
                if (!obj.visible) continue

                val bounds = obj.getBounds()

                // Trouver toutes les cellules que l'objet chevauche
                val minCellX = (bounds.left / cellSize).toInt()
                val maxCellX = (bounds.right / cellSize).toInt()
                val minCellY = (bounds.top / cellSize).toInt()
                val maxCellY = (bounds.bottom / cellSize).toInt()

                for (cy in minCellY..maxCellY) {
                    for (cx in minCellX..maxCellX) {
                        val key = cellKey(cx, cy)
                        cells.getOrPut(key) { mutableListOf() }.add(obj)
                    }
                }
            }
        }

        /**
         * Trouve les objets potentiellement visibles dans un viewport
         */
        fun queryViewport(viewport: RectF): Set<CanvasObject> {
            val result = mutableSetOf<CanvasObject>()

            val minCellX = (viewport.left / cellSize).toInt()
            val maxCellX = (viewport.right / cellSize).toInt()
            val minCellY = (viewport.top / cellSize).toInt()
            val maxCellY = (viewport.bottom / cellSize).toInt()

            for (cy in minCellY..maxCellY) {
                for (cx in minCellX..maxCellX) {
                    val key = cellKey(cx, cy)
                    cells[key]?.let { result.addAll(it) }
                }
            }

            return result
        }

        /**
         * Vide l'index
         */
        fun clear() {
            cells.clear()
        }
    }

    /**
     * Index spatial (optionnel, pour scènes avec beaucoup d'objets)
     */
    private var spatialIndex: SpatialIndex? = null

    /**
     * Active l'indexation spatiale pour les grandes scènes
     */
    fun enableSpatialIndexing(enable: Boolean = true) {
        spatialIndex = if (enable) SpatialIndex() else null
    }

    /**
     * Met à jour l'index spatial
     */
    fun updateSpatialIndex(objects: List<CanvasObject>) {
        spatialIndex?.rebuild(objects)
    }

    // ========== RENDU AVEC INDEX SPATIAL ==========

    /**
     * Rendu optimisé avec index spatial (pour grandes scènes)
     */
    fun renderWithSpatialIndex(
        canvas: Canvas,
        objects: List<CanvasObject>,
        viewportX: Float,
        viewportY: Float,
        viewportZoom: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        selectedObject: CanvasObject? = null
    ) {
        val index = spatialIndex
        if (index == null) {
            // Fallback au rendu normal
            render(canvas, objects, viewportX, viewportY, viewportZoom, viewportWidth, viewportHeight, selectedObject)
            return
        }

        val startTime = System.nanoTime()

        // Calculer le viewport dans le monde
        val worldWidth = viewportWidth / viewportZoom
        val worldHeight = viewportHeight / viewportZoom
        viewportRect.set(
            viewportX,
            viewportY,
            viewportX + worldWidth,
            viewportY + worldHeight
        )

        // Utiliser l'index pour trouver les objets candidats
        val candidates = index.queryViewport(viewportRect)

        // Dessiner le fond
        canvas.drawColor(backgroundColor)

        // Transformation viewport
        canvas.save()
        canvas.scale(viewportZoom, viewportZoom)
        canvas.translate(-viewportX, -viewportY)

        // Grille
        if (showGrid && viewportZoom >= 0.1f) {
            drawGrid(canvas, viewportRect, viewportZoom)
        }

        // Filtrer et dessiner
        val visibleObjects = candidates
            .filter { it.visible && it.intersects(viewportRect) }
            .sortedWith(CanvasObjectZIndexComparator)

        lastDrawnCount = 0
        for (obj in visibleObjects) {
            val isSelected = obj === selectedObject

            when (obj) {
                is ImageObject -> obj.drawWithMipmap(canvas, objectPaint, viewportZoom, isSelected)
                else -> obj.draw(canvas, objectPaint, isSelected)
            }

            lastDrawnCount++
        }

        canvas.restore()

        totalObjectCount = objects.size
        lastRenderTimeMs = (System.nanoTime() - startTime) / 1_000_000
    }
}
