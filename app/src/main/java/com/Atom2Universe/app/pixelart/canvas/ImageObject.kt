package com.Atom2Universe.app.pixelart.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri

/**
 * Objet image sur la toile infinie.
 *
 * Optimisations mémoire :
 * - Stocke une référence URI au fichier original (pas de duplication)
 * - Génère des versions réduites (mipmaps) pour le zoom arrière
 * - Recycle les bitmaps inutilisés
 * - Chargement paresseux des mipmaps
 */
class ImageObject(
    override val id: String = java.util.UUID.randomUUID().toString()
) : CanvasObject(CanvasObjectType.IMAGE) {

    // ========== SOURCE DE L'IMAGE ==========

    /**
     * URI du fichier original (pour rechargement si besoin)
     */
    var sourceUri: Uri? = null

    /**
     * Chemin relatif dans le projet sauvegardé
     */
    var relativePath: String? = null

    /**
     * Bitmap principal (résolution originale ou réduite)
     */
    private var bitmap: Bitmap? = null

    /**
     * Dimensions originales de l'image (avant tout scaling)
     */
    var originalWidth: Int = 0
        private set

    var originalHeight: Int = 0
        private set

    // ========== MIPMAPS POUR ZOOM ==========

    /**
     * Versions réduites pour affichage à faible zoom
     * Clé = niveau (1 = 1/2, 2 = 1/4, 3 = 1/8, etc.)
     */
    private val mipmaps = mutableMapOf<Int, Bitmap>()

    /**
     * Niveau de mipmap actuellement utilisé (0 = original)
     */
    private var currentMipmapLevel = 0

    // ========== RENDU ==========

    /**
     * Rectangle source pré-alloué pour le dessin
     */
    private val srcRect = android.graphics.Rect()

    /**
     * Rectangle destination pré-alloué pour le dessin
     */
    private val dstRect = RectF()

    /**
     * Paint spécifique pour cette image (filtre bilinéaire optionnel)
     */
    private val imagePaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false  // Nearest neighbor pour pixel art
        isDither = false
    }

    // ========== PROPRIÉTÉS D'AFFICHAGE ==========

    /**
     * Mode de filtrage (nearest = pixel art, bilinear = photos)
     */
    var filterBitmap: Boolean = false
        set(value) {
            field = value
            imagePaint.isFilterBitmap = value
        }

    /**
     * Teinte appliquée à l'image (Color.WHITE = pas de teinte)
     */
    var tintColor: Int = Color.WHITE

    /**
     * Mode de fusion (pour effets spéciaux, futur)
     */
    var blendMode: Int = 0

    // ========== INITIALISATION ==========

    /**
     * Définit le bitmap de l'image.
     * Calcule automatiquement les dimensions.
     */
    fun setBitmap(bmp: Bitmap) {
        // Recycler l'ancien bitmap si différent
        if (bitmap != null && bitmap != bmp) {
            recycleMipmaps()
        }

        bitmap = bmp
        originalWidth = bmp.width
        originalHeight = bmp.height

        // Mettre à jour les dimensions de l'objet si pas encore définies
        if (width == 0f) width = originalWidth.toFloat()
        if (height == 0f) height = originalHeight.toFloat()

        markDirty()
    }

    /**
     * Obtient le bitmap (peut être null si non chargé)
     */
    fun getBitmap(): Bitmap? = bitmap

    /**
     * Vérifie si l'image est chargée
     */
    fun isLoaded(): Boolean = bitmap != null && !bitmap!!.isRecycled

    // ========== MIPMAPS ==========

    /**
     * Génère un mipmap pour un niveau donné
     * Niveau 1 = 1/2, niveau 2 = 1/4, etc.
     */
    private fun generateMipmap(level: Int): Bitmap? {
        val source = bitmap ?: return null
        if (source.isRecycled) return null

        val scale = 1f / (1 shl level)  // 2^level
        val newWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (source.height * scale).toInt().coerceAtLeast(1)

        return try {
            Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Obtient le mipmap approprié pour un niveau de zoom donné
     */
    fun getMipmapForZoom(viewportZoom: Float): Bitmap? {
        val bmp = bitmap ?: return null
        if (bmp.isRecycled) return null

        // Calculer le niveau de mipmap nécessaire
        val effectiveScale = viewportZoom * scaleX
        val level = when {
            effectiveScale >= 0.5f -> 0   // Original
            effectiveScale >= 0.25f -> 1  // 1/2
            effectiveScale >= 0.125f -> 2 // 1/4
            effectiveScale >= 0.0625f -> 3 // 1/8
            else -> 4                      // 1/16
        }

        if (level == 0) {
            currentMipmapLevel = 0
            return bmp
        }

        // Chercher ou créer le mipmap
        var mipmap = mipmaps[level]
        if (mipmap == null || mipmap.isRecycled) {
            mipmap = generateMipmap(level)
            if (mipmap != null) {
                mipmaps[level] = mipmap
            }
        }

        currentMipmapLevel = if (mipmap != null) level else 0
        return mipmap ?: bmp
    }

    /**
     * Libère les mipmaps pour économiser la mémoire
     */
    fun recycleMipmaps() {
        for ((_, mipmap) in mipmaps) {
            if (!mipmap.isRecycled) {
                mipmap.recycle()
            }
        }
        mipmaps.clear()
        currentMipmapLevel = 0
    }

    // ========== DESSIN ==========

    override fun draw(canvas: Canvas, paint: Paint, selected: Boolean) {
        val bmp = bitmap ?: return
        if (bmp.isRecycled) return
        if (!visible || opacity <= 0f) return

        // Configurer le paint
        imagePaint.alpha = (opacity * 255).toInt()

        // Calculer la destination
        val halfW = (width * scaleX) / 2f
        val halfH = (height * scaleY) / 2f
        dstRect.set(x - halfW, y - halfH, x + halfW, y + halfH)

        // Appliquer la rotation si nécessaire
        if (rotation != 0f) {
            canvas.save()
            canvas.rotate(rotation, x, y)
        }

        // Dessiner l'image
        srcRect.set(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, srcRect, dstRect, imagePaint)

        // Restaurer si rotation
        if (rotation != 0f) {
            canvas.restore()
        }

        // Dessiner les handles de sélection
        if (selected) {
            drawSelectionHandles(canvas, paint)
        }
    }

    /**
     * Dessine avec le mipmap approprié pour le zoom
     */
    fun drawWithMipmap(canvas: Canvas, paint: Paint, viewportZoom: Float, selected: Boolean) {
        val bmp = getMipmapForZoom(viewportZoom) ?: return
        if (bmp.isRecycled) return
        if (!visible || opacity <= 0f) return

        imagePaint.alpha = (opacity * 255).toInt()

        val halfW = (width * scaleX) / 2f
        val halfH = (height * scaleY) / 2f
        dstRect.set(x - halfW, y - halfH, x + halfW, y + halfH)

        if (rotation != 0f) {
            canvas.save()
            canvas.rotate(rotation, x, y)
        }

        srcRect.set(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, srcRect, dstRect, imagePaint)

        if (rotation != 0f) {
            canvas.restore()
        }

        if (selected) {
            drawSelectionHandles(canvas, paint)
        }
    }

    /**
     * Dessine les handles de sélection autour de l'image
     */
    private fun drawSelectionHandles(canvas: Canvas, paint: Paint) {
        val bounds = getBounds()

        // Contour de sélection
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#2196F3")
        paint.strokeWidth = 2f
        canvas.drawRect(bounds, paint)

        // Handles aux coins et au milieu des côtés
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
            // Fond blanc
            canvas.drawRect(
                hx - handleSize, hy - handleSize,
                hx + handleSize, hy + handleSize,
                paint
            )
            // Contour bleu
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

        // Handle de rotation (cercle au-dessus)
        val rotHandleY = bounds.top - 30f
        paint.color = Color.parseColor("#4CAF50")
        canvas.drawCircle(bounds.centerX(), rotHandleY, handleSize, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        canvas.drawCircle(bounds.centerX(), rotHandleY, handleSize, paint)

        // Ligne vers le handle de rotation
        paint.strokeWidth = 1f
        canvas.drawLine(bounds.centerX(), bounds.top, bounds.centerX(), rotHandleY + handleSize, paint)
    }

    // ========== UTILITAIRES ==========

    override fun recycle() {
        recycleMipmaps()
        bitmap?.let {
            if (!it.isRecycled) {
                // Note: on ne recycle pas le bitmap principal car il peut être partagé
                // Le GC s'en chargera
            }
        }
        bitmap = null
    }

    override fun copy(): CanvasObject {
        val copy = ImageObject()
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
        copy.sourceUri = sourceUri
        copy.relativePath = relativePath
        copy.originalWidth = originalWidth
        copy.originalHeight = originalHeight
        copy.filterBitmap = filterBitmap
        copy.tintColor = tintColor

        // Partager le bitmap (pas de copie pour économiser la mémoire)
        bitmap?.let { copy.setBitmap(it) }

        return copy
    }

    /**
     * Calcule la mémoire utilisée par cette image (en bytes)
     */
    fun getMemoryUsage(): Long {
        var total = 0L
        bitmap?.let { total += it.allocationByteCount.toLong() }
        for ((_, mipmap) in mipmaps) {
            if (!mipmap.isRecycled) {
                total += mipmap.allocationByteCount.toLong()
            }
        }
        return total
    }

    /**
     * Vérifie si un point est sur un handle de sélection
     * Retourne le type de handle ou null
     */
    override fun hitTestHandle(worldX: Float, worldY: Float): HandlePosition? {
        val bounds = getBounds()
        val touchSize = HANDLE_TOUCH_SIZE / 2f

        // Handle de rotation
        val rotHandleY = bounds.top - 30f
        if (kotlin.math.abs(worldX - bounds.centerX()) < touchSize &&
            kotlin.math.abs(worldY - rotHandleY) < touchSize) {
            return HandlePosition.ROTATION
        }

        // Autres handles
        val positions = mapOf(
            HandlePosition.TOP_LEFT to (bounds.left to bounds.top),
            HandlePosition.TOP_CENTER to (bounds.centerX() to bounds.top),
            HandlePosition.TOP_RIGHT to (bounds.right to bounds.top),
            HandlePosition.MIDDLE_LEFT to (bounds.left to bounds.centerY()),
            HandlePosition.MIDDLE_RIGHT to (bounds.right to bounds.centerY()),
            HandlePosition.BOTTOM_LEFT to (bounds.left to bounds.bottom),
            HandlePosition.BOTTOM_CENTER to (bounds.centerX() to bounds.bottom),
            HandlePosition.BOTTOM_RIGHT to (bounds.right to bounds.bottom)
        )

        for ((handle, pos) in positions) {
            if (kotlin.math.abs(worldX - pos.first) < touchSize &&
                kotlin.math.abs(worldY - pos.second) < touchSize) {
                return handle
            }
        }

        return null
    }

    companion object {
        /**
         * Taille maximale d'image à charger en mémoire
         * Au-delà, on downscale automatiquement
         */
        const val MAX_TEXTURE_SIZE = 4096

        /**
         * Crée un ImageObject depuis un bitmap avec vérification de taille
         */
        fun fromBitmap(bitmap: Bitmap, downscaleIfNeeded: Boolean = true): ImageObject {
            val img = ImageObject()

            if (downscaleIfNeeded &&
                (bitmap.width > MAX_TEXTURE_SIZE || bitmap.height > MAX_TEXTURE_SIZE)) {
                // Downscale pour tenir dans les limites
                val scale = minOf(
                    MAX_TEXTURE_SIZE.toFloat() / bitmap.width,
                    MAX_TEXTURE_SIZE.toFloat() / bitmap.height
                )
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                img.setBitmap(scaled)
                // Garder les dimensions originales pour référence
                img.width = bitmap.width.toFloat()
                img.height = bitmap.height.toFloat()
            } else {
                img.setBitmap(bitmap)
            }

            return img
        }
    }
}
