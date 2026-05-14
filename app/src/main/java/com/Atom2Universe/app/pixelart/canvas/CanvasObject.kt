package com.Atom2Universe.app.pixelart.canvas

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import java.util.UUID

/**
 * Types d'objets supportés sur la toile infinie
 */
enum class CanvasObjectType {
    IMAGE,      // Image importée (PNG/JPG)
    SHAPE,      // Forme vectorielle
    TEXT,       // Texte éditable
    DRAWING     // Dessin libre (pixels sparse)
}

/**
 * Classe de base scellée pour tous les objets sur la toile infinie.
 *
 * Optimisations pour tablettes modestes :
 * - RectF et Matrix pré-alloués (évite GC pendant onDraw)
 * - Calculs de bounds mis en cache
 * - Flag dirty pour invalider le cache seulement si nécessaire
 */
sealed class CanvasObject(
    val type: CanvasObjectType
) {
    // Identifiant unique
    open val id: String = UUID.randomUUID().toString()

    // Position dans le monde (coordonnées absolues)
    var x: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                markDirty()
            }
        }

    var y: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                markDirty()
            }
        }

    // Dimensions de base (avant transformation)
    var width: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                markDirty()
            }
        }

    var height: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                markDirty()
            }
        }

    // Transformations
    var rotation: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                markDirty()
            }
        }

    var scaleX: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                markDirty()
            }
        }

    var scaleY: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                markDirty()
            }
        }

    // Affichage
    var opacity: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    var visible: Boolean = true
    var locked: Boolean = false

    // Ordre d'affichage (plus élevé = devant)
    var zIndex: Int = 0

    // Nom optionnel pour l'utilisateur
    var name: String = ""

    // ========== OPTIMISATION : Cache et pré-allocation ==========

    // Cache des bounds (évite recalcul à chaque frame)
    @Transient
    protected var cachedBounds: RectF = RectF()

    @Transient
    protected var boundsDirty: Boolean = true

    // Matrice de transformation pré-allouée
    @Transient
    protected val cachedMatrix: Matrix = Matrix()

    @Transient
    protected var matrixDirty: Boolean = true

    // Points temporaires pour calculs (évite allocations)
    @Transient
    protected val tempPoints = FloatArray(8)

    @Transient
    protected val tempBounds = RectF()

    /**
     * Marque l'objet comme modifié (invalide les caches)
     */
    protected fun markDirty() {
        boundsDirty = true
        matrixDirty = true
    }

    /**
     * Retourne les bounds de l'objet dans les coordonnées du monde.
     * Utilise un cache pour éviter les recalculs.
     */
    fun getBounds(): RectF {
        if (boundsDirty) {
            calculateBounds(cachedBounds)
            boundsDirty = false
        }
        return cachedBounds
    }

    /**
     * Calcule les bounds réels (avec rotation/scale)
     * À surcharger si l'objet a une forme non-rectangulaire
     */
    protected open fun calculateBounds(outBounds: RectF) {
        // Bounds de base avant transformation
        val halfW = (width * scaleX) / 2f
        val halfH = (height * scaleY) / 2f

        if (rotation == 0f) {
            // Pas de rotation : calcul simple
            outBounds.set(x - halfW, y - halfH, x + halfW, y + halfH)
        } else {
            // Avec rotation : calculer les 4 coins transformés
            val rad = Math.toRadians(rotation.toDouble())
            val cos = kotlin.math.cos(rad).toFloat()
            val sin = kotlin.math.sin(rad).toFloat()

            // Coins avant rotation (centrés sur origine)
            val corners = floatArrayOf(
                -halfW, -halfH,  // Top-left
                halfW, -halfH,   // Top-right
                halfW, halfH,    // Bottom-right
                -halfW, halfH    // Bottom-left
            )

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (i in 0 until 4) {
                val px = corners[i * 2]
                val py = corners[i * 2 + 1]
                // Rotation puis translation
                val rx = px * cos - py * sin + x
                val ry = px * sin + py * cos + y
                minX = minOf(minX, rx)
                minY = minOf(minY, ry)
                maxX = maxOf(maxX, rx)
                maxY = maxOf(maxY, ry)
            }

            outBounds.set(minX, minY, maxX, maxY)
        }
    }

    /**
     * Retourne la matrice de transformation (translation + rotation + scale)
     */
    fun computeTransformMatrix(): Matrix {
        if (matrixDirty) {
            cachedMatrix.reset()
            cachedMatrix.postScale(scaleX, scaleY)
            cachedMatrix.postRotate(rotation)
            cachedMatrix.postTranslate(x, y)
            matrixDirty = false
        }
        return cachedMatrix
    }

    /**
     * Vérifie si un point (coordonnées monde) est à l'intérieur de l'objet.
     * Tient compte de la rotation.
     */
    open fun contains(worldX: Float, worldY: Float): Boolean {
        if (rotation == 0f) {
            // Pas de rotation : test simple
            val halfW = (width * scaleX) / 2f
            val halfH = (height * scaleY) / 2f
            return worldX >= x - halfW && worldX <= x + halfW &&
                   worldY >= y - halfH && worldY <= y + halfH
        } else {
            // Avec rotation : transformer le point dans l'espace local
            val rad = Math.toRadians(-rotation.toDouble())
            val cos = kotlin.math.cos(rad).toFloat()
            val sin = kotlin.math.sin(rad).toFloat()

            // Translation inverse
            val dx = worldX - x
            val dy = worldY - y

            // Rotation inverse
            val localX = dx * cos - dy * sin
            val localY = dx * sin + dy * cos

            // Test dans l'espace local (non rotaté)
            val halfW = (width * scaleX) / 2f
            val halfH = (height * scaleY) / 2f
            return localX >= -halfW && localX <= halfW &&
                   localY >= -halfH && localY <= halfH
        }
    }

    /**
     * Vérifie si les bounds de l'objet intersectent un rectangle.
     * Utilisé pour le culling (ne pas dessiner ce qui n'est pas visible).
     */
    fun intersects(rect: RectF): Boolean {
        return RectF.intersects(getBounds(), rect)
    }

    /**
     * Vérifie si un point est sur un handle de redimensionnement.
     * Retourne le type de handle ou null si aucun handle n'est touché.
     */
    open fun hitTestHandle(worldX: Float, worldY: Float): HandlePosition? {
        val bounds = getBounds()
        val touchSize = HANDLE_TOUCH_SIZE / 2f

        // Handle de rotation (au-dessus du centre haut)
        val rotHandleY = bounds.top - HANDLE_SIZE * 2
        if (kotlin.math.abs(worldX - bounds.centerX()) < touchSize &&
            kotlin.math.abs(worldY - rotHandleY) < touchSize) {
            return HandlePosition.ROTATION
        }

        // Autres handles de redimensionnement
        val positions = listOf(
            HandlePosition.TOP_LEFT to Pair(bounds.left, bounds.top),
            HandlePosition.TOP_CENTER to Pair(bounds.centerX(), bounds.top),
            HandlePosition.TOP_RIGHT to Pair(bounds.right, bounds.top),
            HandlePosition.MIDDLE_LEFT to Pair(bounds.left, bounds.centerY()),
            HandlePosition.MIDDLE_RIGHT to Pair(bounds.right, bounds.centerY()),
            HandlePosition.BOTTOM_LEFT to Pair(bounds.left, bounds.bottom),
            HandlePosition.BOTTOM_CENTER to Pair(bounds.centerX(), bounds.bottom),
            HandlePosition.BOTTOM_RIGHT to Pair(bounds.right, bounds.bottom)
        )

        for ((handle, pos) in positions) {
            if (kotlin.math.abs(worldX - pos.first) < touchSize &&
                kotlin.math.abs(worldY - pos.second) < touchSize) {
                return handle
            }
        }

        return null
    }

    /**
     * Dessine l'objet sur le canvas.
     * Le canvas est déjà transformé pour le viewport.
     *
     * @param canvas Le canvas Android
     * @param paint Paint pré-configuré (réutiliser pour éviter GC)
     * @param selected True si l'objet est sélectionné (dessiner les handles)
     */
    abstract fun draw(canvas: Canvas, paint: Paint, selected: Boolean = false)

    /**
     * Libère les ressources (bitmaps, etc.)
     * Appelé quand l'objet est supprimé.
     */
    open fun recycle() {
        // Par défaut : rien à faire
        // Surcharger pour ImageObject
    }

    /**
     * Clone l'objet (pour duplication)
     */
    abstract fun copy(): CanvasObject

    /**
     * Déplace l'objet de dx, dy
     */
    fun translate(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    /**
     * Centre de l'objet (pour rotation)
     */
    fun getCenter(): PointF = PointF(x, y)

    /**
     * Distance du centre à un point
     */
    fun distanceToCenter(worldX: Float, worldY: Float): Float {
        val dx = worldX - x
        val dy = worldY - y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        // Seuil de distance pour sélection tactile (en pixels écran)
        const val TOUCH_SLOP = 24f

        // Taille des handles de sélection
        const val HANDLE_SIZE = 20f
        const val HANDLE_TOUCH_SIZE = 70f  // Zone de détection tactile plus grande
    }
}

/**
 * Enum pour les handles de redimensionnement
 */
enum class HandlePosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    ROTATION  // Handle de rotation (au-dessus du top center)
}

/**
 * Comparateur pour trier les objets par zIndex
 */
object CanvasObjectZIndexComparator : Comparator<CanvasObject> {
    override fun compare(a: CanvasObject, b: CanvasObject): Int {
        return a.zIndex.compareTo(b.zIndex)
    }
}
