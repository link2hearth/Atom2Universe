package com.Atom2Universe.app.midi.practice.themes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File

/**
 * Thème personnalisé permettant à l'utilisateur de choisir sa propre image de fond
 * tout en conservant les couleurs/effets d'un thème de base.
 *
 * L'utilisateur peut :
 * - Choisir n'importe quel thème comme base (couleurs des notes, effets, etc.)
 * - Fournir sa propre image de fond (photo de bébé, animal, paysage...)
 * - Ajuster l'opacité de l'image
 */
class CustomBackgroundTheme(
    private val context: Context,
    private var baseTheme: PracticeTheme = ClassicTheme(),
    private var backgroundImagePath: String? = null,
    private var imageOpacity: Float = 0.3f // 0.0 - 1.0
) : BasePracticeTheme() {

    override val id = "custom_background"
    override val displayName = "Personnalisé"
    override val description = "Votre image en fond"
    // Bitmap de l'image de fond (chargé paresseusement)
    private var backgroundBitmap: Bitmap? = null
    private var scaledBitmap: Bitmap? = null  // Version pré-scalée pour performance
    private var bitmapLoadAttempted = false

    // Dimensions cachées pour détecter les changements
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedOverlayColor = 0

    // Paint pour dessiner l'image (sans anti-alias pour performance)
    private val imagePaint = Paint().apply {
        isFilterBitmap = false  // Désactiver le filtrage pour performance
        isDither = false
    }

    private val overlayPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var srcRect = Rect()
    private var dstRect = Rect()

    companion object {
        // Nom du fichier de fond personnalisé stocké dans l'app
        const val CUSTOM_BG_FILENAME = "custom_practice_background.jpg"

        /**
         * Sauvegarde une image comme fond personnalisé
         */
        fun saveCustomBackground(context: Context, uri: Uri): Boolean {
            return try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return false

                // Charger et redimensionner l'image pour économiser la mémoire
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                // Calculer le ratio de sous-échantillonnage
                val targetWidth = 1920
                val targetHeight = 1080
                var sampleSize = 1
                if (options.outWidth > targetWidth || options.outHeight > targetHeight) {
                    val widthRatio = options.outWidth.toFloat() / targetWidth
                    val heightRatio = options.outHeight.toFloat() / targetHeight
                    sampleSize = maxOf(widthRatio, heightRatio).toInt().coerceAtLeast(1)
                }

                // Charger avec sous-échantillonnage
                val inputStream2 = context.contentResolver.openInputStream(uri)
                    ?: return false
                val loadOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val bitmap = BitmapFactory.decodeStream(inputStream2, null, loadOptions)
                inputStream2.close()

                if (bitmap == null) return false

                // Sauvegarder
                val file = File(context.filesDir, CUSTOM_BG_FILENAME)
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * Vérifie si un fond personnalisé existe
         */
        fun hasCustomBackground(context: Context): Boolean {
            return File(context.filesDir, CUSTOM_BG_FILENAME).exists()
        }

        /**
         * Supprime le fond personnalisé
         */
        fun deleteCustomBackground(context: Context): Boolean {
            return try {
                File(context.filesDir, CUSTOM_BG_FILENAME).delete()
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Retourne le chemin du fond personnalisé
         */
        fun getCustomBackgroundPath(context: Context): String? {
            val file = File(context.filesDir, CUSTOM_BG_FILENAME)
            return if (file.exists()) file.absolutePath else null
        }
    }

    /**
     * Définit le thème de base (pour les couleurs des notes)
     */
    fun setBaseTheme(theme: PracticeTheme) {
        baseTheme = theme
    }

    /**
     * Retourne le thème de base actuel
     */
    fun getBaseTheme(): PracticeTheme = baseTheme

    /**
     * Définit l'opacité de l'image de fond
     */
    fun setImageOpacity(opacity: Float) {
        imageOpacity = opacity.coerceIn(0.1f, 0.8f)
        // Invalider le cache pour recalculer overlay et alpha
        cachedWidth = 0
        cachedHeight = 0
    }

    /**
     * Charge l'image de fond depuis le stockage
     */
    private fun loadBackgroundImage() {
        if (bitmapLoadAttempted) return
        bitmapLoadAttempted = true

        try {
            val path = backgroundImagePath
                ?: File(context.filesDir, CUSTOM_BG_FILENAME).absolutePath

            val file = File(path)
            if (!file.exists()) return

            // Charger avec options pour éviter les OOM
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565  // Moins de mémoire
            }
            backgroundBitmap = BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Force le rechargement de l'image
     */
    fun reloadImage() {
        backgroundBitmap?.recycle()
        backgroundBitmap = null
        scaledBitmap?.recycle()
        scaledBitmap = null
        bitmapLoadAttempted = false
        cachedWidth = 0
        cachedHeight = 0
        loadBackgroundImage()
    }

    // ========== Délégation au thème de base ==========

    override fun getBackgroundColors() = baseTheme.getBackgroundColors()
    override fun getHitZoneColor() = baseTheme.getHitZoneColor()
    override fun getGridLineColor() = baseTheme.getGridLineColor()
    override fun getNoteColor(pitchClass: Int) = baseTheme.getNoteColor(pitchClass)

    override fun hasCustomNoteShape() = baseTheme.hasCustomNoteShape()
    override fun drawNote(canvas: Canvas, rect: RectF, pitchClass: Int, velocity: Int, paint: Paint, cornerRadius: Float) {
        baseTheme.drawNote(canvas, rect, pitchClass, velocity, paint, cornerRadius)
    }

    override fun hasGlowEffect() = baseTheme.hasGlowEffect()
    override fun getGlowIntensity() = baseTheme.getGlowIntensity()
    override fun getGlowRadiusDp() = baseTheme.getGlowRadiusDp()

    // Le fond animé est remplacé par notre image
    override fun hasAnimatedBackground() = true

    override fun updateBackgroundAnimation(deltaMs: Long) {
        // Mettre à jour l'animation du thème de base (pour les effets superposés)
        if (baseTheme.hasAnimatedBackground()) {
            baseTheme.updateBackgroundAnimation(deltaMs)
        }
    }

    override fun drawAnimatedBackground(canvas: Canvas, width: Int, height: Int) {
        // Charger l'image si nécessaire
        if (!bitmapLoadAttempted) {
            loadBackgroundImage()
        }

        val bitmap = backgroundBitmap

        if (bitmap != null && !bitmap.isRecycled) {
            // Pré-scaler le bitmap si les dimensions ont changé
            if (width != cachedWidth || height != cachedHeight) {
                prepareScaledBitmap(bitmap, width, height)
                cachedWidth = width
                cachedHeight = height
                // Mettre à jour la couleur de l'overlay
                val (bgTop, _) = baseTheme.getBackgroundColors()
                val overlayAlpha = ((1f - imageOpacity) * 180).toInt().coerceIn(50, 200)
                cachedOverlayColor = Color.argb(overlayAlpha, Color.red(bgTop), Color.green(bgTop), Color.blue(bgTop))
                overlayPaint.color = cachedOverlayColor
                imagePaint.alpha = (imageOpacity * 255).toInt()
            }

            // Dessiner le bitmap pré-scalé (très rapide - pas de scaling à chaque frame)
            val scaled = scaledBitmap
            if (scaled != null && !scaled.isRecycled) {
                canvas.drawBitmap(scaled, 0f, 0f, imagePaint)
            }

            // Overlay sombre pour la lisibilité (couleur pré-calculée)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        } else {
            // Pas d'image : dessiner le fond du thème de base directement
            // Utiliser le gradient seulement si dimensions changent
            if (width != cachedWidth || height != cachedHeight) {
                val (top, bottom) = baseTheme.getBackgroundColors()
                tempPaint.shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    top, bottom,
                    android.graphics.Shader.TileMode.CLAMP
                )
                cachedWidth = width
                cachedHeight = height
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tempPaint)
        }

        // Dessiner les effets du thème de base par-dessus
        if (baseTheme.hasAnimatedBackground() && bitmap != null) {
            baseTheme.drawForegroundElements(canvas, width, height)
        }
    }

    /**
     * Pré-scale le bitmap à la taille du canvas pour éviter le scaling à chaque frame
     */
    private fun prepareScaledBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int) {
        // Libérer l'ancien bitmap scalé
        scaledBitmap?.recycle()
        scaledBitmap = null

        try {
            val sourceWidth = source.width.toFloat()
            val sourceHeight = source.height.toFloat()
            val targetRatio = targetWidth.toFloat() / targetHeight
            val sourceRatio = sourceWidth / sourceHeight

            val srcLeft: Int
            val srcTop: Int
            val srcRight: Int
            val srcBottom: Int

            if (sourceRatio > targetRatio) {
                // Image plus large : on coupe les côtés
                val visibleWidth = sourceHeight * targetRatio
                srcLeft = ((sourceWidth - visibleWidth) / 2).toInt()
                srcTop = 0
                srcRight = (srcLeft + visibleWidth).toInt()
                srcBottom = sourceHeight.toInt()
            } else {
                // Image plus haute : on coupe le haut/bas
                val visibleHeight = sourceWidth / targetRatio
                srcLeft = 0
                srcTop = ((sourceHeight - visibleHeight) / 2).toInt()
                srcRight = sourceWidth.toInt()
                srcBottom = (srcTop + visibleHeight).toInt()
            }

            // Créer un bitmap de la taille exacte du canvas
            scaledBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            val tempCanvas = Canvas(scaledBitmap!!)
            srcRect.set(srcLeft, srcTop, srcRight, srcBottom)
            dstRect.set(0, 0, targetWidth, targetHeight)
            tempCanvas.drawBitmap(source, srcRect, dstRect, null)
        } catch (e: Exception) {
            e.printStackTrace()
            scaledBitmap = null
        }
    }

    override fun drawForegroundElements(canvas: Canvas, width: Int, height: Int) {
        baseTheme.drawForegroundElements(canvas, width, height)
    }

    override fun hasParticles() = baseTheme.hasParticles()
    override fun getParticlesPerHit() = baseTheme.getParticlesPerHit()
    override fun getParticleLifetimeMs() = baseTheme.getParticleLifetimeMs()
    override fun getParticleColor(noteColor: Int) = baseTheme.getParticleColor(noteColor)

    override fun getWhiteKeyColor() = baseTheme.getWhiteKeyColor()
    override fun getBlackKeyColor() = baseTheme.getBlackKeyColor()
    override fun getPressedWhiteKeyColor() = baseTheme.getPressedWhiteKeyColor()
    override fun getPressedBlackKeyColor() = baseTheme.getPressedBlackKeyColor()

    override fun hasCustomPianoStyle() = baseTheme.hasCustomPianoStyle()
    override fun drawWhiteKey(canvas: Canvas, rect: RectF, isPressed: Boolean, isActive: Boolean, paint: Paint) {
        baseTheme.drawWhiteKey(canvas, rect, isPressed, isActive, paint)
    }
    override fun drawBlackKey(canvas: Canvas, rect: RectF, isPressed: Boolean, isActive: Boolean, paint: Paint) {
        baseTheme.drawBlackKey(canvas, rect, isPressed, isActive, paint)
    }

    override fun getSheetMusicBackgroundColor() = baseTheme.getSheetMusicBackgroundColor()
    override fun getStaffLineColor() = baseTheme.getStaffLineColor()
    override fun getCurrentTimeIndicatorColor() = baseTheme.getCurrentTimeIndicatorColor()
    override fun getSheetMusicSymbolColor() = baseTheme.getSheetMusicSymbolColor()

    override fun onActivate() {
        baseTheme.onActivate()
        loadBackgroundImage()
    }

    override fun onDeactivate() {
        baseTheme.onDeactivate()
    }

    override fun release() {
        super.release()
        backgroundBitmap?.recycle()
        backgroundBitmap = null
        scaledBitmap?.recycle()
        scaledBitmap = null
        cachedWidth = 0
        cachedHeight = 0
        baseTheme.release()
    }
}
