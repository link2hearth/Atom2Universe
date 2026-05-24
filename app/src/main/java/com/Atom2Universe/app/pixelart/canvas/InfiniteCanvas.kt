package com.Atom2Universe.app.pixelart.canvas

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Modes d'interaction avec le canvas
 */
enum class CanvasInteractionMode {
    PAN,        // Déplacement du viewport (2 doigts ou outil main)
    SELECT,     // Sélection d'objets
    DRAW,       // Dessin libre
    SHAPE,      // Création de formes
    TEXT,       // Ajout de texte
    IMAGE       // Placement d'image
}

/**
 * Listener pour les événements du canvas
 */
interface InfiniteCanvasListener {
    fun onObjectSelected(obj: CanvasObject?)
    fun onObjectModified(obj: CanvasObject)
    fun onViewportChanged(x: Float, y: Float, zoom: Float)
    fun onTap(worldX: Float, worldY: Float)
    fun onLongPress(worldX: Float, worldY: Float)

    // Callbacks pour création d'objets par drag
    fun onDragStart(worldX: Float, worldY: Float) {}
    fun onDragMove(startX: Float, startY: Float, currentX: Float, currentY: Float) {}
    fun onDragEnd(startX: Float, startY: Float, endX: Float, endY: Float) {}
    fun onDragCancel() {}  // Appelé quand le dessin est annulé (ex: passage en mode zoom)

    // Callback pour sauvegarde automatique - appelé après toute modification du canvas
    fun onCanvasModified() {}
}

/**
 * Vue canvas infinie avec support pour :
 * - Zoom et pan illimités
 * - Objets vectoriels (formes, texte, images, dessins)
 * - Sélection et manipulation d'objets
 * - Rendu optimisé (culling des objets hors écran)
 */
class InfiniteCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ========== OBJETS ==========

    private val objects = mutableListOf<CanvasObject>()
    private var selectedObject: CanvasObject? = null

    // ========== VIEWPORT ==========

    // Position du viewport dans les coordonnées monde
    private var viewportX = 0f
    private var viewportY = 0f

    // Niveau de zoom (1.0 = 100%)
    private var viewportZoom = 1f

    // Limites de zoom
    private val minZoom = 0.1f
    private val maxZoom = 10f

    // ========== INTERACTION ==========

    var interactionMode = CanvasInteractionMode.SELECT
        set(value) {
            field = value
            invalidate()
        }

    var listener: InfiniteCanvasListener? = null

    // Gesture detectors
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    // État du touch
    private var isDragging = false
    private var isScaling = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Pour le pan à 2 doigts
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var isTwoFingerDrag = false

    // Manipulation d'objet
    private var isDraggingObject = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var objectStartX = 0f
    private var objectStartY = 0f

    // Redimensionnement par handles
    private var isResizingObject = false
    private var activeHandle: HandlePosition? = null
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeStartWidth = 0f
    private var resizeStartHeight = 0f
    private var resizeStartObjX = 0f
    private var resizeStartObjY = 0f
    private var resizeAspectRatio = 1f  // Ratio largeur/hauteur au début du resize

    // Timestamp pour distinguer dessin vs multi-touch
    private var drawingStartTime = 0L
    private val MULTITOUCH_GRACE_PERIOD_MS = 500L  // 500ms pour décider si c'est du multi-touch

    // Création d'objet par drag (outils SHAPE, DRAW)
    private var isToolDragging = false
    private var toolDragStartX = 0f
    private var toolDragStartY = 0f

    // Suspension du dessin pendant le geste à 2 doigts
    private var wasToolDraggingSuspended = false
    private var suspendedToolLastX = 0f
    private var suspendedToolLastY = 0f

    // ID du doigt primaire utilisé pour le dessin
    private var primaryPointerId = -1

    // ========== RENDU ==========

    // Couleur de fond du canvas
    var canvasBackgroundColor = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    // Grille
    var showGrid = false
        set(value) {
            field = value
            invalidate()
        }

    var gridSize = 50f // Taille de la grille en unités monde
        set(value) {
            field = value
            invalidate()
        }

    var snapToGrid = false

    // Paints pré-alloués (évite GC dans onDraw)
    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.argb(50, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = false
    }

    private val selectionPaint = Paint().apply {
        color = Color.rgb(33, 150, 243) // Material Blue
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val handleStrokePaint = Paint().apply {
        color = Color.rgb(33, 150, 243)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val objectPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    // Rectangles temporaires (évite allocations)
    private val tempRect = RectF()
    private val visibleWorldBounds = RectF()
    private val clipRect = RectF()


    init {
        // Scale gesture detector pour le zoom
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val focusX = detector.focusX
                val focusY = detector.focusY

                // Calculer le point de focus dans les coordonnées monde
                val worldFocusX = screenToWorldX(focusX)
                val worldFocusY = screenToWorldY(focusY)

                // Appliquer le zoom
                val newZoom = (viewportZoom * scaleFactor).coerceIn(minZoom, maxZoom)

                if (newZoom != viewportZoom) {
                    // Ajuster le viewport pour que le point de focus reste fixe
                    val zoomRatio = newZoom / viewportZoom
                    viewportX = worldFocusX - (worldFocusX - viewportX) / zoomRatio
                    viewportY = worldFocusY - (worldFocusY - viewportY) / zoomRatio
                    viewportZoom = newZoom

                    listener?.onViewportChanged(viewportX, viewportY, viewportZoom)
                    invalidate()
                }

                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        })

        // Gesture detector pour tap et long press
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val worldX = screenToWorldX(e.x)
                val worldY = screenToWorldY(e.y)

                when (interactionMode) {
                    CanvasInteractionMode.SELECT -> {
                        // Chercher un objet sous le tap
                        val hitObject = hitTest(worldX, worldY)
                        selectObject(hitObject)
                    }
                    else -> {
                        listener?.onTap(worldX, worldY)
                    }
                }

                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val worldX = screenToWorldX(e.x)
                val worldY = screenToWorldY(e.y)
                listener?.onLongPress(worldX, worldY)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap pour reset zoom ou éditer objet
                if (selectedObject != null) {
                    // TODO: Ouvrir l'éditeur de l'objet
                } else {
                    // Reset zoom
                    viewportZoom = 1f
                    listener?.onViewportChanged(viewportX, viewportY, viewportZoom)
                    invalidate()
                }
                return true
            }
        })
    }

    // ========== CONVERSION COORDONNÉES ==========

    /**
     * Convertit une coordonnée X écran en coordonnée monde
     */
    fun screenToWorldX(screenX: Float): Float {
        return viewportX + (screenX - width / 2f) / viewportZoom
    }

    /**
     * Convertit une coordonnée Y écran en coordonnée monde
     */
    fun screenToWorldY(screenY: Float): Float {
        return viewportY + (screenY - height / 2f) / viewportZoom
    }

    /**
     * Convertit une coordonnée X monde en coordonnée écran
     */
    fun worldToScreenX(worldX: Float): Float {
        return (worldX - viewportX) * viewportZoom + width / 2f
    }

    /**
     * Convertit une coordonnée Y monde en coordonnée écran
     */
    fun worldToScreenY(worldY: Float): Float {
        return (worldY - viewportY) * viewportZoom + height / 2f
    }

    /**
     * Retourne les bounds visibles dans les coordonnées monde
     */
    fun getVisibleWorldBounds(): RectF {
        val halfWidth = (width / 2f) / viewportZoom
        val halfHeight = (height / 2f) / viewportZoom
        visibleWorldBounds.set(
            viewportX - halfWidth,
            viewportY - halfHeight,
            viewportX + halfWidth,
            viewportY + halfHeight
        )
        return visibleWorldBounds
    }

    // ========== GESTION DES OBJETS ==========

    /**
     * Ajoute un objet au canvas
     */
    fun addObject(obj: CanvasObject) {
        // Assigner un zIndex si pas défini
        if (obj.zIndex == 0 && objects.isNotEmpty()) {
            obj.zIndex = objects.maxOf { it.zIndex } + 1
        }
        objects.add(obj)
        objects.sortBy { it.zIndex }
        invalidate()
        // Notifier pour sauvegarde automatique
        listener?.onCanvasModified()
    }

    /**
     * Ajoute un objet au canvas sans déclencher de notification
     * Utilisé lors du chargement initial pour éviter les sauvegardes inutiles
     */
    fun addObjectSilently(obj: CanvasObject) {
        if (obj.zIndex == 0 && objects.isNotEmpty()) {
            obj.zIndex = objects.maxOf { it.zIndex } + 1
        }
        objects.add(obj)
        objects.sortBy { it.zIndex }
        invalidate()
    }

    /**
     * Supprime un objet du canvas
     */
    fun removeObject(id: String): Boolean {
        val obj = objects.find { it.id == id }
        if (obj != null) {
            if (selectedObject?.id == id) {
                selectedObject = null
                listener?.onObjectSelected(null)
            }
            obj.recycle()
            objects.remove(obj)
            invalidate()
            // Notifier pour sauvegarde automatique
            listener?.onCanvasModified()
            return true
        }
        return false
    }

    /**
     * Supprime l'objet sélectionné
     */
    fun deleteSelectedObject(): Boolean {
        val obj = selectedObject ?: return false
        return removeObject(obj.id)
    }

    /**
     * Trouve un objet par son ID
     */
    fun findObject(id: String): CanvasObject? {
        return objects.find { it.id == id }
    }

    /**
     * Retourne tous les objets
     */
    fun getObjects(): List<CanvasObject> = objects.toList()

    /**
     * Efface tous les objets
     */
    fun clearObjects() {
        objects.forEach { it.recycle() }
        objects.clear()
        selectedObject = null
        listener?.onObjectSelected(null)
        invalidate()
        // Notifier pour sauvegarde automatique
        listener?.onCanvasModified()
    }

    /**
     * Sélectionne un objet
     */
    fun selectObject(obj: CanvasObject?) {
        if (selectedObject != obj) {
            selectedObject = obj
            listener?.onObjectSelected(obj)
            invalidate()
        }
    }

    /**
     * Retourne l'objet sélectionné
     */
    fun getSelectedObject(): CanvasObject? = selectedObject

    // ========== GESTION DES LAYERS (Z-INDEX) ==========

    /**
     * Amène l'objet au premier plan (zIndex maximum)
     */
    fun bringToFront(obj: CanvasObject) {
        val maxZIndex = objects.maxOfOrNull { it.zIndex } ?: 0
        obj.zIndex = maxZIndex + 1
        objects.sortBy { it.zIndex }
        invalidate()
    }

    /**
     * Monte l'objet d'un niveau
     */
    fun bringForward(obj: CanvasObject) {
        // Trouver l'objet juste au-dessus
        val currentZ = obj.zIndex
        val above = objects
            .filter { it.zIndex > currentZ }
            .minByOrNull { it.zIndex }

        if (above != null) {
            // Échanger les zIndex
            val temp = above.zIndex
            above.zIndex = currentZ
            obj.zIndex = temp
        } else {
            // Déjà au sommet, juste incrémenter
            obj.zIndex++
        }
        objects.sortBy { it.zIndex }
        invalidate()
    }

    /**
     * Descend l'objet d'un niveau
     */
    fun sendBackward(obj: CanvasObject) {
        // Trouver l'objet juste en-dessous
        val currentZ = obj.zIndex
        val below = objects
            .filter { it.zIndex < currentZ }
            .maxByOrNull { it.zIndex }

        if (below != null) {
            // Échanger les zIndex
            val temp = below.zIndex
            below.zIndex = currentZ
            obj.zIndex = temp
        } else {
            // Déjà tout en bas, juste décrémenter
            obj.zIndex = (obj.zIndex - 1).coerceAtLeast(0)
        }
        objects.sortBy { it.zIndex }
        invalidate()
    }

    /**
     * Envoie l'objet à l'arrière-plan (zIndex minimum)
     */
    fun sendToBack(obj: CanvasObject) {
        val minZIndex = objects.minOfOrNull { it.zIndex } ?: 0
        obj.zIndex = (minZIndex - 1).coerceAtLeast(0)
        // Réajuster tous les autres objets si nécessaire
        if (obj.zIndex == 0) {
            objects.filter { it != obj }.forEach { it.zIndex++ }
        }
        objects.sortBy { it.zIndex }
        invalidate()
    }

    /**
     * Test de collision pour trouver un objet sous un point
     */
    fun hitTest(worldX: Float, worldY: Float): CanvasObject? {
        // Parcourir du plus haut zIndex au plus bas (liste maintenue triée)
        return objects
            .filter { it.visible && !it.locked }
            .asReversed()
            .firstOrNull { it.contains(worldX, worldY) }
    }

    // ========== VIEWPORT ==========

    /**
     * Définit la position du viewport
     */
    fun setViewport(x: Float, y: Float, zoom: Float = viewportZoom) {
        viewportX = x
        viewportY = y
        viewportZoom = zoom.coerceIn(minZoom, maxZoom)
        listener?.onViewportChanged(viewportX, viewportY, viewportZoom)
        invalidate()
    }

    /**
     * Centre le viewport sur un point
     */
    fun centerOn(worldX: Float, worldY: Float) {
        viewportX = worldX
        viewportY = worldY
        listener?.onViewportChanged(viewportX, viewportY, viewportZoom)
        invalidate()
    }

    /**
     * Centre le viewport sur tous les objets
     */
    fun fitToContent() {
        if (objects.isEmpty()) {
            setViewport(0f, 0f, 1f)
            return
        }

        // Calculer les bounds de tous les objets
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        objects.forEach { obj ->
            val bounds = obj.getBounds()
            minX = min(minX, bounds.left)
            minY = min(minY, bounds.top)
            maxX = max(maxX, bounds.right)
            maxY = max(maxY, bounds.bottom)
        }

        // Centrer et ajuster le zoom
        val contentWidth = maxX - minX
        val contentHeight = maxY - minY
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        // Calculer le zoom pour que tout rentre avec une marge
        val margin = 50f
        val zoomX = (width - margin * 2) / contentWidth
        val zoomY = (height - margin * 2) / contentHeight
        val newZoom = min(zoomX, zoomY).coerceIn(minZoom, maxZoom)

        setViewport(centerX, centerY, newZoom)
    }

    fun getViewportX() = viewportX
    fun getViewportY() = viewportY
    fun getViewportZoom() = viewportZoom

    // ========== TOUCH HANDLING ==========

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Vérifier si on dessine depuis plus de 500ms (priorité au dessin)
        val isDrawingLongEnough = isToolDragging &&
            (System.currentTimeMillis() - drawingStartTime) > MULTITOUCH_GRACE_PERIOD_MS

        // Ne pas traiter le scale detector si on dessine depuis longtemps
        // Cela empêche le ScaleGestureDetector de "manger" les événements
        if (!isDrawingLongEnough) {
            scaleDetector.onTouchEvent(event)
        }

        // Traiter le gesture detector pour tap/longpress (seulement 1 doigt)
        if (event.pointerCount == 1 && !isScaling) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                isTwoFingerDrag = false
                isToolDragging = false
                wasToolDraggingSuspended = false
                primaryPointerId = event.getPointerId(0)

                val worldX = screenToWorldX(event.x)
                val worldY = screenToWorldY(event.y)

                when (interactionMode) {
                    CanvasInteractionMode.SELECT -> {
                        // D'abord vérifier si on touche un handle de l'objet sélectionné
                        if (selectedObject != null) {
                            val handle = selectedObject!!.hitTestHandle(worldX, worldY)
                            if (handle != null) {
                                // Début du redimensionnement
                                isResizingObject = true
                                activeHandle = handle
                                resizeStartX = worldX
                                resizeStartY = worldY
                                resizeStartWidth = selectedObject!!.width * selectedObject!!.scaleX
                                resizeStartHeight = selectedObject!!.height * selectedObject!!.scaleY
                                resizeStartObjX = selectedObject!!.x
                                resizeStartObjY = selectedObject!!.y
                                // Calculer le ratio actuel pour les coins (proportionnel)
                                resizeAspectRatio = if (resizeStartHeight > 0f) {
                                    resizeStartWidth / resizeStartHeight
                                } else 1f
                            } else if (selectedObject!!.contains(worldX, worldY)) {
                                // Sinon vérifier si on touche l'objet lui-même
                                isDraggingObject = true
                                dragStartX = worldX
                                dragStartY = worldY
                                objectStartX = selectedObject!!.x
                                objectStartY = selectedObject!!.y
                            }
                        }
                    }
                    CanvasInteractionMode.SHAPE, CanvasInteractionMode.DRAW -> {
                        // Début du drag pour création d'objet
                        isToolDragging = true
                        toolDragStartX = worldX
                        toolDragStartY = worldY
                        drawingStartTime = System.currentTimeMillis()  // Enregistrer le timestamp
                        listener?.onDragStart(worldX, worldY)
                        invalidate()  // Afficher le premier point/forme immédiatement
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second doigt posé : initialiser le pan/zoom 2 doigts
                if (event.pointerCount == 2) {
                    val elapsed = System.currentTimeMillis() - drawingStartTime

                    // Si on dessine depuis plus de 500ms, ignorer le 2ème doigt
                    // Le dessin continue normalement, pas de zoom/pan
                    if (isToolDragging && elapsed > MULTITOUCH_GRACE_PERIOD_MS) {
                        // Ignorer le second doigt - continuer à dessiner
                        // Ne pas passer en mode two-finger drag
                    } else {
                        // Le 2ème doigt est arrivé rapidement : mode zoom/pan
                        // ANNULER le dessin en cours (pas le terminer)
                        if (isToolDragging) {
                            wasToolDraggingSuspended = true
                            // Annuler le dessin - ne pas créer d'objet
                            listener?.onDragCancel()
                            isToolDragging = false
                        }

                        isTwoFingerDrag = true
                        isDraggingObject = false  // Annuler le drag d'objet
                        isResizingObject = false
                        activeHandle = null
                        lastFocusX = (event.getX(0) + event.getX(1)) / 2f
                        lastFocusY = (event.getY(0) + event.getY(1)) / 2f
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Si on dessine et que le 2ème doigt a été ignoré, continuer avec le doigt primaire
                if (isToolDragging && event.pointerCount >= 2 && !isTwoFingerDrag) {
                    // Continuer le dessin avec le doigt primaire uniquement
                    val primaryIndex = event.findPointerIndex(primaryPointerId)
                    if (primaryIndex >= 0) {
                        val worldX = screenToWorldX(event.getX(primaryIndex))
                        val worldY = screenToWorldY(event.getY(primaryIndex))
                        listener?.onDragMove(toolDragStartX, toolDragStartY, worldX, worldY)
                        invalidate()
                    }
                } else if (event.pointerCount >= 2 && isTwoFingerDrag) {
                    // 2+ doigts en mode pan/zoom
                    val focusX = (event.getX(0) + event.getX(1)) / 2f
                    val focusY = (event.getY(0) + event.getY(1)) / 2f

                    val dx = focusX - lastFocusX
                    val dy = focusY - lastFocusY

                    viewportX -= dx / viewportZoom
                    viewportY -= dy / viewportZoom
                    listener?.onViewportChanged(viewportX, viewportY, viewportZoom)
                    invalidate()

                    lastFocusX = focusX
                    lastFocusY = focusY
                } else if (isDragging && !isScaling) {
                    // 1 doigt
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val worldX = screenToWorldX(event.x)
                    val worldY = screenToWorldY(event.y)

                    if (isResizingObject && selectedObject != null && !selectedObject!!.locked) {
                        // Redimensionnement via handle
                        applyResize(worldX, worldY)
                        listener?.onObjectModified(selectedObject!!)
                        invalidate()
                    } else if (isToolDragging) {
                        // Drag pour création d'objet (SHAPE, DRAW)
                        listener?.onDragMove(toolDragStartX, toolDragStartY, worldX, worldY)
                        invalidate()
                    } else if (isDraggingObject && selectedObject != null && !selectedObject!!.locked) {
                        // Déplacer l'objet sélectionné
                        val worldDx = dx / viewportZoom
                        val worldDy = dy / viewportZoom

                        var newX = selectedObject!!.x + worldDx
                        var newY = selectedObject!!.y + worldDy

                        // Snap to grid si activé
                        if (snapToGrid) {
                            newX = (newX / gridSize).toInt() * gridSize
                            newY = (newY / gridSize).toInt() * gridSize
                        }

                        selectedObject!!.x = newX
                        selectedObject!!.y = newY
                        listener?.onObjectModified(selectedObject!!)
                        invalidate()
                    } else if (interactionMode == CanvasInteractionMode.PAN) {
                        // Mode PAN : déplacer le viewport avec 1 doigt
                        viewportX -= dx / viewportZoom
                        viewportY -= dy / viewportZoom
                        listener?.onViewportChanged(viewportX, viewportY, viewportZoom)
                        invalidate()
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Un doigt levé : on passe de 2 à 1 doigt
                if (event.pointerCount == 2) {
                    // Déterminer quel doigt reste
                    val leavingIndex = event.actionIndex
                    val remainingIndex = if (leavingIndex == 0) 1 else 0
                    val remainingPointerId = event.getPointerId(remainingIndex)

                    lastTouchX = event.getX(remainingIndex)
                    lastTouchY = event.getY(remainingIndex)
                    isTwoFingerDrag = false

                    // Si le doigt primaire (celui qui dessinait) est levé,
                    // on ne reprend pas le dessin avec l'autre doigt
                    if (event.getPointerId(leavingIndex) == primaryPointerId) {
                        wasToolDraggingSuspended = false  // Annuler toute reprise
                        primaryPointerId = -1  // Plus de doigt primaire
                    } else {
                        // Le doigt secondaire est levé, le primaire reste
                        // On peut potentiellement reprendre en mode PAN si souhaité
                        primaryPointerId = remainingPointerId
                    }

                    // Important: ne pas reprendre automatiquement le dessin
                    // L'utilisateur doit relever puis reposer pour recommencer
                    isToolDragging = false
                    isDraggingObject = false
                    isResizingObject = false
                    activeHandle = null
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Track si on a modifié quelque chose pour auto-save
                val wasModifyingCanvas = isDraggingObject || isResizingObject

                // Terminer le trait seulement si on était vraiment en train de dessiner
                // (pas si le dessin a été suspendu par un geste 2 doigts)
                if (isToolDragging && event.actionMasked == MotionEvent.ACTION_UP) {
                    val worldX = screenToWorldX(event.x)
                    val worldY = screenToWorldY(event.y)
                    listener?.onDragEnd(toolDragStartX, toolDragStartY, worldX, worldY)
                    invalidate()  // Afficher l'état final
                }

                // Réinitialiser tout l'état
                isDragging = false
                isDraggingObject = false
                isTwoFingerDrag = false
                isToolDragging = false
                wasToolDraggingSuspended = false
                primaryPointerId = -1
                isResizingObject = false
                activeHandle = null

                // Notifier pour sauvegarde automatique si on a modifié un objet
                if (wasModifyingCanvas && event.actionMasked == MotionEvent.ACTION_UP) {
                    listener?.onCanvasModified()
                }
            }
        }

        return true
    }

    /**
     * Applique le redimensionnement selon le handle actif
     */
    private fun applyResize(worldX: Float, worldY: Float) {
        val obj = selectedObject ?: return
        val handle = activeHandle ?: return

        val deltaX = worldX - resizeStartX
        val deltaY = worldY - resizeStartY

        when (handle) {
            HandlePosition.ROTATION -> {
                // Rotation : calculer l'angle entre le centre et le point actuel
                val centerX = obj.x
                val centerY = obj.y
                val angle = kotlin.math.atan2(
                    (worldY - centerY).toDouble(),
                    (worldX - centerX).toDouble()
                )
                // Convertir en degrés et ajuster (0° = vers le haut)
                obj.rotation = (Math.toDegrees(angle) + 90).toFloat()
            }

            HandlePosition.TOP_LEFT -> {
                // Coin supérieur gauche : proportionnel
                // Utiliser le delta diagonal pour un redimensionnement uniforme
                val diagonalDelta = (-deltaX - deltaY) / 2f
                val newWidth = (resizeStartWidth + diagonalDelta).coerceAtLeast(20f)
                val newHeight = (newWidth / resizeAspectRatio).coerceAtLeast(20f)
                val actualWidth = newHeight * resizeAspectRatio
                obj.scaleX = actualWidth / obj.width
                obj.scaleY = newHeight / obj.height
                // Ajuster la position pour garder le coin bas-droite fixe
                val widthDiff = actualWidth - resizeStartWidth
                val heightDiff = newHeight - resizeStartHeight
                obj.x = resizeStartObjX + widthDiff / 2f
                obj.y = resizeStartObjY + heightDiff / 2f
            }

            HandlePosition.TOP_RIGHT -> {
                // Coin supérieur droit : proportionnel
                val diagonalDelta = (deltaX - deltaY) / 2f
                val newWidth = (resizeStartWidth + diagonalDelta).coerceAtLeast(20f)
                val newHeight = (newWidth / resizeAspectRatio).coerceAtLeast(20f)
                val actualWidth = newHeight * resizeAspectRatio
                obj.scaleX = actualWidth / obj.width
                obj.scaleY = newHeight / obj.height
                val widthDiff = actualWidth - resizeStartWidth
                val heightDiff = newHeight - resizeStartHeight
                obj.x = resizeStartObjX + widthDiff / 2f
                obj.y = resizeStartObjY + heightDiff / 2f
            }

            HandlePosition.BOTTOM_LEFT -> {
                // Coin inférieur gauche : proportionnel
                val diagonalDelta = (-deltaX + deltaY) / 2f
                val newWidth = (resizeStartWidth + diagonalDelta).coerceAtLeast(20f)
                val newHeight = (newWidth / resizeAspectRatio).coerceAtLeast(20f)
                val actualWidth = newHeight * resizeAspectRatio
                obj.scaleX = actualWidth / obj.width
                obj.scaleY = newHeight / obj.height
                val widthDiff = actualWidth - resizeStartWidth
                val heightDiff = newHeight - resizeStartHeight
                obj.x = resizeStartObjX + widthDiff / 2f
                obj.y = resizeStartObjY + heightDiff / 2f
            }

            HandlePosition.BOTTOM_RIGHT -> {
                // Coin inférieur droit : proportionnel
                val diagonalDelta = (deltaX + deltaY) / 2f
                val newWidth = (resizeStartWidth + diagonalDelta).coerceAtLeast(20f)
                val newHeight = (newWidth / resizeAspectRatio).coerceAtLeast(20f)
                val actualWidth = newHeight * resizeAspectRatio
                obj.scaleX = actualWidth / obj.width
                obj.scaleY = newHeight / obj.height
                val widthDiff = actualWidth - resizeStartWidth
                val heightDiff = newHeight - resizeStartHeight
                obj.x = resizeStartObjX + widthDiff / 2f
                obj.y = resizeStartObjY + heightDiff / 2f
            }

            HandlePosition.TOP_CENTER -> {
                // Bord supérieur : redimensionner en hauteur seulement
                val newHeight = (resizeStartHeight - deltaY).coerceAtLeast(20f)
                obj.scaleY = newHeight / obj.height
                obj.y = resizeStartObjY + deltaY / 2f
            }

            HandlePosition.BOTTOM_CENTER -> {
                // Bord inférieur : redimensionner en hauteur seulement
                val newHeight = (resizeStartHeight + deltaY).coerceAtLeast(20f)
                obj.scaleY = newHeight / obj.height
                obj.y = resizeStartObjY + deltaY / 2f
            }

            HandlePosition.MIDDLE_LEFT -> {
                // Bord gauche : redimensionner en largeur seulement
                val newWidth = (resizeStartWidth - deltaX).coerceAtLeast(20f)
                obj.scaleX = newWidth / obj.width
                obj.x = resizeStartObjX + deltaX / 2f
            }

            HandlePosition.MIDDLE_RIGHT -> {
                // Bord droit : redimensionner en largeur seulement
                val newWidth = (resizeStartWidth + deltaX).coerceAtLeast(20f)
                obj.scaleX = newWidth / obj.width
                obj.x = resizeStartObjX + deltaX / 2f
            }
        }
    }

    // ========== RENDU ==========

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Fond
        backgroundPaint.color = canvasBackgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Sauvegarder l'état du canvas
        canvas.save()

        // Appliquer la transformation du viewport
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(viewportZoom, viewportZoom)
        canvas.translate(-viewportX, -viewportY)

        // Dessiner la grille
        if (showGrid && viewportZoom > 0.2f) {
            drawGrid(canvas)
        }

        // Calculer les bounds visibles pour le culling
        val visible = getVisibleWorldBounds()

        // Dessiner les objets (liste maintenue triée par zIndex)
        for (obj in objects) {
            if (obj.visible && obj.intersects(visible)) {
                obj.draw(canvas, objectPaint, obj == selectedObject)

                // Dessiner la sélection
                if (obj == selectedObject) {
                    drawSelection(canvas, obj)
                }
            }
        }

        // Restaurer l'état du canvas
        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas) {
        val visible = getVisibleWorldBounds()

        // Ajuster l'épaisseur de la grille selon le zoom
        gridPaint.strokeWidth = 1f / viewportZoom

        // Calculer les lignes à dessiner
        val startX = (visible.left / gridSize).toInt() * gridSize
        val startY = (visible.top / gridSize).toInt() * gridSize

        // Lignes verticales
        var x = startX
        while (x <= visible.right) {
            canvas.drawLine(x, visible.top, x, visible.bottom, gridPaint)
            x += gridSize
        }

        // Lignes horizontales
        var y = startY
        while (y <= visible.bottom) {
            canvas.drawLine(visible.left, y, visible.right, y, gridPaint)
            y += gridSize
        }
    }

    private fun drawSelection(canvas: Canvas, obj: CanvasObject) {
        val bounds = obj.getBounds()

        // Ajuster l'épaisseur selon le zoom
        selectionPaint.strokeWidth = 2f / viewportZoom
        handleStrokePaint.strokeWidth = 2f / viewportZoom

        // Rectangle de sélection
        canvas.drawRect(bounds, selectionPaint)

        // Handles de redimensionnement
        val handleSize = CanvasObject.HANDLE_SIZE / viewportZoom

        // Positions des handles
        val positions = arrayOf(
            bounds.left to bounds.top,           // TOP_LEFT
            bounds.centerX() to bounds.top,      // TOP_CENTER
            bounds.right to bounds.top,          // TOP_RIGHT
            bounds.left to bounds.centerY(),    // MIDDLE_LEFT
            bounds.right to bounds.centerY(),   // MIDDLE_RIGHT
            bounds.left to bounds.bottom,        // BOTTOM_LEFT
            bounds.centerX() to bounds.bottom,   // BOTTOM_CENTER
            bounds.right to bounds.bottom        // BOTTOM_RIGHT
        )

        for ((x, y) in positions) {
            canvas.drawCircle(x, y, handleSize / 2f, handlePaint)
            canvas.drawCircle(x, y, handleSize / 2f, handleStrokePaint)
        }

        // Handle de rotation (au-dessus du centre haut)
        val rotationHandleY = bounds.top - handleSize * 2
        canvas.drawCircle(bounds.centerX(), rotationHandleY, handleSize / 2f, handlePaint)
        canvas.drawCircle(bounds.centerX(), rotationHandleY, handleSize / 2f, handleStrokePaint)

        // Ligne vers le handle de rotation
        canvas.drawLine(bounds.centerX(), bounds.top, bounds.centerX(), rotationHandleY, selectionPaint)
    }

    // ========== UTILITAIRES ==========

    /**
     * Exporte le canvas en bitmap
     */
    fun exportToBitmap(): Bitmap? {
        if (objects.isEmpty()) return null

        // Calculer les bounds totaux
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        objects.filter { it.visible }.forEach { obj ->
            val bounds = obj.getBounds()
            minX = min(minX, bounds.left)
            minY = min(minY, bounds.top)
            maxX = max(maxX, bounds.right)
            maxY = max(maxY, bounds.bottom)
        }

        val width = (maxX - minX).toInt().coerceAtLeast(1)
        val height = (maxY - minY).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val exportCanvas = Canvas(bitmap)

        // Fond
        exportCanvas.drawColor(canvasBackgroundColor)

        // Translation pour que le contenu commence à (0,0)
        exportCanvas.translate(-minX, -minY)

        // Dessiner les objets (liste maintenue triée par zIndex)
        for (obj in objects.filter { it.visible }) {
            obj.draw(exportCanvas, objectPaint, false)
        }

        return bitmap
    }
}
