package com.Atom2Universe.app.midi.practice.themes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Thèmes additionnels avec effets avancés
 */

// =============================================================================
// GALAXY THEME - Étoiles, nébuleuses et cosmos
// =============================================================================

class GalaxyTheme : BasePracticeTheme() {

    override val id = "galaxy"
    override val displayName = "Galaxie"
    override val description = "Voyage cosmique parmi les étoiles et nébuleuses"


    companion object {
        // Couleurs d'étoiles réalistes basées sur leur température
        private val STAR_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),  // C  - Blanc pur (étoile type A)
            0xFFCAE8FF.toInt(),  // C# - Blanc-bleu (type B)
            0xFF9BB0FF.toInt(),  // D  - Bleu clair (type B)
            0xFF6B8BFF.toInt(),  // D# - Bleu (type O)
            0xFFFFFFE0.toInt(),  // E  - Blanc-jaune (type F)
            0xFFFFF8DC.toInt(),  // F  - Jaune pâle (type G, comme le Soleil)
            0xFFFFE4B5.toInt(),  // F# - Jaune-orange (type K)
            0xFFFFD39B.toInt(),  // G  - Orange (type K)
            0xFFFFB86C.toInt(),  // G# - Orange foncé
            0xFFFF8C69.toInt(),  // A  - Rouge-orange (type M)
            0xFFFF6B6B.toInt(),  // A# - Rouge (géante rouge)
            0xFFFF4D4D.toInt()   // B  - Rouge profond (supergéante)
        )

        // PERFORMANCE: Reduced counts
        private const val MAX_STARS = 35               // Réduit de 100
        private const val MAX_SHOOTING_STARS = 1       // Réduit de 2
        private const val SHOOTING_STAR_PROBABILITY = 0.0002f  // Réduit

        // Lookup table pour sin (256 entrées)
        private const val SIN_TABLE_SIZE = 256
        private val SIN_TABLE = FloatArray(SIN_TABLE_SIZE) { i ->
            sin(i * 2.0 * PI / SIN_TABLE_SIZE).toFloat()
        }

        // Couleurs pré-calculées pour les étoiles (évite Color.parseColor)
        private val STAR_BASE_COLORS = intArrayOf(
            Color.WHITE,
            0xFFFFE4C4.toInt(),  // Bisque
            0xFFADD8E6.toInt(),  // Bleu clair
            0xFFFFD700.toInt()   // Or
        )

        private data class DustTemplate(
            val xFactor: Float,
            val yFactor: Float,
            val radiusFactor: Float,
            val driftX: Float,
            val driftY: Float,
            val driftSpeed: Float,
            val phaseOffset: Float,
            val colors: IntArray,
            val stops: FloatArray
        )

        // Nuages de poussière colorés façon Hubble (pré-calculés, peu nombreux)
        private val DUST_TEMPLATES = arrayOf(
            DustTemplate(
                xFactor = 0.22f,
                yFactor = 0.34f,
                radiusFactor = 0.72f,
                driftX = 18f,
                driftY = 12f,
                driftSpeed = 0.65f,
                phaseOffset = 0.2f,
                colors = intArrayOf(
                    Color.argb(120, 255, 245, 235),
                    Color.argb(95, 255, 170, 110),
                    Color.argb(55, 210, 70, 40),
                    Color.TRANSPARENT
                ),
                stops = floatArrayOf(0f, 0.28f, 0.68f, 1f)
            ),
            DustTemplate(
                xFactor = 0.52f,
                yFactor = 0.42f,
                radiusFactor = 0.78f,
                driftX = 14f,
                driftY = 16f,
                driftSpeed = 0.55f,
                phaseOffset = 1.1f,
                colors = intArrayOf(
                    Color.argb(110, 255, 240, 225),
                    Color.argb(85, 255, 150, 90),
                    Color.argb(48, 255, 90, 60),
                    Color.TRANSPARENT
                ),
                stops = floatArrayOf(0f, 0.24f, 0.7f, 1f)
            ),
            DustTemplate(
                xFactor = 0.78f,
                yFactor = 0.30f,
                radiusFactor = 0.64f,
                driftX = 12f,
                driftY = 10f,
                driftSpeed = 0.72f,
                phaseOffset = 2.2f,
                colors = intArrayOf(
                    Color.argb(105, 255, 252, 245),
                    Color.argb(78, 255, 190, 120),
                    Color.argb(42, 190, 80, 50),
                    Color.TRANSPARENT
                ),
                stops = floatArrayOf(0f, 0.26f, 0.66f, 1f)
            ),
            DustTemplate(
                xFactor = 0.46f,
                yFactor = 0.70f,
                radiusFactor = 0.58f,
                driftX = 10f,
                driftY = 14f,
                driftSpeed = 0.48f,
                phaseOffset = 3.0f,
                colors = intArrayOf(
                    Color.argb(70, 230, 240, 255),
                    Color.argb(60, 150, 120, 255),
                    Color.argb(28, 120, 60, 200),
                    Color.TRANSPARENT
                ),
                stops = floatArrayOf(0f, 0.3f, 0.72f, 1f)
            )
        )
    }

    // Lookup table helper - évite sin() à chaque frame
    private fun fastSin(radians: Float): Float {
        val normalized = ((radians / (2 * PI.toFloat())) % 1f + 1f) % 1f
        return SIN_TABLE[(normalized * SIN_TABLE_SIZE).toInt().coerceIn(0, SIN_TABLE_SIZE - 1)]
    }

    override fun getNoteColor(pitchClass: Int) = STAR_COLORS[pitchClass % 12]

    private var animationTimeMs = 0L
    private val stars = mutableListOf<Star>()
    private val shootingStars = mutableListOf<ShootingStar>()
    private var lastWidth = 0
    private var lastHeight = 0

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    private val dustOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(55, 6, 0, 18)
    }

    // Cache pour les nuages de poussière (positions statiques + dérive légère)
    private data class DustCloudCache(
        val baseX: Float,
        val baseY: Float,
        val radius: Float,
        val driftX: Float,
        val driftY: Float,
        val driftSpeed: Float,
        val driftPhase: Float,
        val colors: IntArray,
        val stops: FloatArray
    )
    private var dustCloudCaches: Array<DustCloudCache>? = null

    override fun getBackgroundColors() = Pair(
        Color.parseColor("#05000D"),  // Noir cosmique plus profond
        Color.parseColor("#190028")   // Violet sombre
    )

    override fun getHitZoneColor() = Color.parseColor("#FFFFFF")  // Blanc stellaire
    override fun getGridLineColor() = Color.parseColor("#20FFFFFF")

    override fun hasGlowEffect() = true
    override fun getGlowIntensity() = 0.6f
    override fun getGlowRadiusDp() = 10f

    override fun hasAnimatedBackground() = true

    override fun updateBackgroundAnimation(deltaMs: Long) {
        animationTimeMs += deltaMs

        // PERFORMANCE: Scintillement des étoiles (mise à jour simplifiée)
        val twinkleIncrement = deltaMs * 0.003f
        for (star in stars) {
            star.twinklePhase = (star.twinklePhase + twinkleIncrement * star.twinkleSpeed) % (2 * PI.toFloat())
        }

        // Étoiles filantes
        val iterator = shootingStars.iterator()
        while (iterator.hasNext()) {
            val ss = iterator.next()
            ss.progress += deltaMs / ss.duration
            if (ss.progress >= 1f) {
                iterator.remove()
            }
        }

        // PERFORMANCE: Ajouter occasionnellement une étoile filante (probabilité réduite)
        if (shootingStars.size < MAX_SHOOTING_STARS && (animationTimeMs % 5000L) < deltaMs) {
            shootingStars.add(createShootingStar())
        }
    }

    override fun drawAnimatedBackground(canvas: Canvas, width: Int, height: Int) {
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            initializeStars(width, height)
            initializeDustCloudCaches(width, height)
        }

        // Fond avec nuages de poussière colorés
        drawDustClouds(canvas, width, height)

        // Étoiles
        drawStars(canvas)

        // Étoiles filantes
        drawShootingStars(canvas)
    }

    private fun initializeStars(width: Int, height: Int) {
        stars.clear()
        // PERFORMANCE: Use deterministic positions based on index
        repeat(MAX_STARS) { i ->
            // Pseudo-random based on index for deterministic placement
            val xFactor = ((i * 7919 + 1) % 1000) / 1000f
            val yFactor = ((i * 6271 + 1) % 1000) / 1000f
            val radiusFactor = ((i * 3571 + 1) % 1000) / 1000f
            val brightFactor = ((i * 4973 + 1) % 1000) / 1000f
            val speedFactor = ((i * 2851 + 1) % 1000) / 1000f
            val phaseFactor = ((i * 1723 + 1) % 1000) / 1000f
            val colorIdx = i % STAR_BASE_COLORS.size
            val isGiantStar = i % 9 == 0

            val baseRadius = radiusFactor * 1.3f + 0.4f
            val radius = if (isGiantStar) baseRadius * 1.8f else baseRadius
            val baseBrightness = brightFactor * 0.45f + 0.45f
            val brightness = if (isGiantStar) (baseBrightness + 0.35f).coerceAtMost(1f) else baseBrightness
            val twinkleSpeed = if (isGiantStar) speedFactor * 1.2f + 0.4f else speedFactor * 2f + 0.5f

            stars.add(Star(
                x = xFactor * width,
                y = yFactor * height,
                radius = radius,
                brightness = brightness,
                twinkleSpeed = twinkleSpeed,
                twinklePhase = phaseFactor * PI.toFloat() * 2,
                color = STAR_BASE_COLORS[colorIdx]
            ))
        }
    }

    private fun initializeDustCloudCaches(width: Int, height: Int) {
        // PERFORMANCE: Pre-compute dust cloud positions once
        val baseRadius = max(width, height) * 0.45f
        dustCloudCaches = DUST_TEMPLATES.mapIndexed { index, template ->
            DustCloudCache(
                baseX = template.xFactor * width,
                baseY = template.yFactor * height,
                radius = baseRadius * template.radiusFactor,
                driftX = template.driftX,
                driftY = template.driftY,
                driftSpeed = template.driftSpeed,
                driftPhase = template.phaseOffset + index * 0.77f,
                colors = template.colors,
                stops = template.stops
            )
        }.toTypedArray()
    }

    private fun drawDustClouds(canvas: Canvas, width: Int, height: Int) {
        // Animation lente et légère pour éviter la charge CPU
        val phase = animationTimeMs * 0.00012f
        val caches = dustCloudCaches ?: return

        for (cache in caches) {
            val driftPhase = phase * cache.driftSpeed + cache.driftPhase
            val offsetX = fastSin(driftPhase) * cache.driftX
            val offsetY = fastSin(driftPhase * 0.82f + 1.37f) * cache.driftY

            val centerX = cache.baseX + offsetX
            val centerY = cache.baseY + offsetY

            nebulaPaint.shader = android.graphics.RadialGradient(
                centerX,
                centerY,
                cache.radius,
                cache.colors,
                cache.stops,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(centerX, centerY, cache.radius, nebulaPaint)
        }

        // Léger voile sombre pour préserver la lisibilité des notes
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dustOverlayPaint)
    }

    private fun drawStars(canvas: Canvas) {
        for (star in stars) {
            // PERFORMANCE: Utiliser fastSin au lieu de sin
            val twinkle = 0.5f + 0.5f * fastSin(star.twinklePhase)
            val alpha = (star.brightness * twinkle * 255).toInt().coerceIn(50, 255)

            starPaint.color = Color.argb(alpha, Color.red(star.color), Color.green(star.color), Color.blue(star.color))
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)

            // PERFORMANCE: Halo uniquement pour les étoiles très brillantes (seuil augmenté)
            if (star.brightness > 0.85f && star.radius > 1.2f) {
                starPaint.color = Color.argb((alpha * 0.2f).toInt(), Color.red(star.color), Color.green(star.color), Color.blue(star.color))
                canvas.drawCircle(star.x, star.y, star.radius * 2f, starPaint)
            }
        }
    }

    private fun drawShootingStars(canvas: Canvas) {
        for (ss in shootingStars) {
            val currentX = ss.startX + (ss.endX - ss.startX) * ss.progress
            val currentY = ss.startY + (ss.endY - ss.startY) * ss.progress

            // PERFORMANCE: Traînée simplifiée (sans shader pour économiser)
            val trailLength = 60f  // Réduit de 80
            val trailStartX = currentX - (ss.endX - ss.startX) / 100 * trailLength
            val trailStartY = currentY - (ss.endY - ss.startY) / 100 * trailLength

            // PERFORMANCE: Dessiner ligne simple au lieu de gradient
            trailPaint.shader = null
            trailPaint.color = Color.argb(180, 255, 255, 255)
            canvas.drawLine(trailStartX, trailStartY, currentX, currentY, trailPaint)

            // Tête brillante
            starPaint.color = Color.WHITE
            canvas.drawCircle(currentX, currentY, 2f, starPaint)
        }
    }

    private fun createShootingStar(): ShootingStar {
        // PERFORMANCE: Deterministic shooting star based on time
        val timeFactor = (animationTimeMs % 10000) / 10000f
        val startX = timeFactor * lastWidth
        val angle = 0.5f  // Angle fixe pour éviter calculs
        val length = lastWidth * 0.4f  // Réduit

        return ShootingStar(
            startX = startX,
            startY = 0f,
            endX = startX + cos(angle) * length,
            endY = sin(angle) * length,
            duration = 800f,  // Durée fixe
            progress = 0f
        )
    }

    override fun hasParticles() = true
    // PERFORMANCE: Reduced particles (was 15)
    override fun getParticlesPerHit() = 8
    override fun getParticleLifetimeMs() = 800L  // Réduit de 1200

    override fun release() {
        super.release()
        stars.clear()
        shootingStars.clear()
        dustCloudCaches = null
    }

    override fun getWhiteKeyColor() = Color.parseColor("#1A1A2E")
    override fun getBlackKeyColor() = Color.parseColor("#0D0015")
    override fun getPressedWhiteKeyColor() = Color.parseColor("#2A2A4E")
    override fun getPressedBlackKeyColor() = Color.parseColor("#1A0030")

    override fun getSheetMusicBackgroundColor() = Color.parseColor("#C9C9C9")
    override fun getStaffLineColor() = Color.parseColor("#30E040FB")
    override fun getCurrentTimeIndicatorColor() = Color.parseColor("#E040FB")

    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val brightness: Float,
        val twinkleSpeed: Float,
        var twinklePhase: Float,
        val color: Int
    )

    private data class ShootingStar(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val duration: Float,
        var progress: Float
    )
}

// =============================================================================
// CYBERPUNK THEME - Image statique avec effet scan line minimal
// =============================================================================

/**
 * Thème Cyberpunk optimisé utilisant une image statique au lieu d'animations CPU.
 * L'image cyberpunk_city.png est chargée une seule fois et pré-scalée.
 * Seule une simple ligne de scan anime le fond pour un effet minimal.
 */
class CyberpunkTheme : BasePracticeTheme() {

    override val id = "cyberpunk"
    override val displayName = "Cyberpunk"
    override val description = "Ville néon cyberpunk"


    companion object {
        private val CYBER_COLORS = intArrayOf(
            0xFFFF0080.toInt(),  // C  - Rose néon
            0xFFFF00BF.toInt(),  // C#
            0xFFFF00FF.toInt(),  // D  - Magenta
            0xFFBF00FF.toInt(),  // D#
            0xFF8000FF.toInt(),  // E  - Violet
            0xFF4000FF.toInt(),  // F
            0xFF0040FF.toInt(),  // F# - Bleu électrique
            0xFF0080FF.toInt(),  // G
            0xFF00BFFF.toInt(),  // G# - Cyan
            0xFF00FFFF.toInt(),  // A  - Cyan vif
            0xFF00FFB0.toInt(),  // A#
            0xFF00FF80.toInt()   // B  - Vert néon
        )

        private val BG_TOP = Color.rgb(5, 0, 15)
        private val BG_BOTTOM = Color.rgb(15, 5, 30)
    }

    // Animation state
    private var animationTimeMs = 0L
    private var scanLineY = 0f
    private var lastHeight = 0

    // Image de la ville pré-scalée (chargée une seule fois)
    private var cityBitmap: android.graphics.Bitmap? = null
    private var scaledBitmap: android.graphics.Bitmap? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var bitmapLoadAttempted = false

    // Paints pré-alloués
    private val imagePaint = Paint().apply {
        isFilterBitmap = false  // Désactiver le filtrage pour performance
        isDither = false
    }

    private val scanLinePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(25, 0, 255, 255)
    }

    private val overlayPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    // Rectangles pré-alloués pour éviter les allocations
    private val srcRect = android.graphics.Rect()
    private val dstRect = android.graphics.Rect()

    override fun getBackgroundColors() = Pair(BG_TOP, BG_BOTTOM)

    override fun getHitZoneColor() = Color.parseColor("#00FFFF")
    override fun getGridLineColor() = Color.parseColor("#25FF00FF")
    override fun getNoteColor(pitchClass: Int) = CYBER_COLORS[pitchClass % 12]

    override fun hasGlowEffect() = true
    override fun getGlowIntensity() = 0.5f
    override fun getGlowRadiusDp() = 8f

    override fun hasCustomNoteShape() = false
    override fun hasAnimatedBackground() = true

    override fun updateBackgroundAnimation(deltaMs: Long) {
        animationTimeMs += deltaMs
        // Simple scan line qui descend lentement
        scanLineY = (scanLineY + deltaMs * 0.08f) % (lastHeight + 50f)
    }

    override fun drawAnimatedBackground(canvas: Canvas, width: Int, height: Int) {
        lastHeight = height

        // Charger l'image si pas encore fait
        if (!bitmapLoadAttempted) {
            loadCityImage()
        }

        // Pré-scaler le bitmap si dimensions changent
        if (width != cachedWidth || height != cachedHeight) {
            prepareScaledBitmap(width, height)
            cachedWidth = width
            cachedHeight = height
        }

        // Dessiner le bitmap pré-scalé (très rapide - pas de scaling par frame)
        val scaled = scaledBitmap
        if (scaled != null && !scaled.isRecycled) {
            canvas.drawBitmap(scaled, 0f, 0f, imagePaint)
        }

        // Simple ligne de scan cyan
        val widthFloat = width.toFloat()
        canvas.drawRect(0f, scanLineY - 3f, widthFloat, scanLineY + 3f, scanLinePaint)
    }

    /**
     * Charge l'image de la ville depuis les resources drawable.
     * Appelé une seule fois au premier rendu.
     */
    private fun loadCityImage() {
        bitmapLoadAttempted = true
        try {
            val context = PracticeThemeManager.appContext ?: return
            val options = android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565  // Moins de mémoire
            }
            cityBitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                com.Atom2Universe.app.R.drawable.cyberpunk_city,
                options
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Pré-scale le bitmap à la taille du canvas pour éviter le scaling à chaque frame.
     * Utilise un crop center pour remplir tout l'écran.
     */
    private fun prepareScaledBitmap(targetWidth: Int, targetHeight: Int) {
        // Libérer l'ancien bitmap scalé
        scaledBitmap?.recycle()
        scaledBitmap = null

        val source = cityBitmap ?: return
        if (source.isRecycled) return

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
            scaledBitmap = android.graphics.Bitmap.createBitmap(
                targetWidth, targetHeight, android.graphics.Bitmap.Config.RGB_565
            )
            val tempCanvas = Canvas(scaledBitmap!!)

            srcRect.set(srcLeft, srcTop, srcRight, srcBottom)
            dstRect.set(0, 0, targetWidth, targetHeight)
            tempCanvas.drawBitmap(source, srcRect, dstRect, null)

            // Ajouter un overlay sombre pour la lisibilité des notes
            overlayPaint.color = Color.argb(120, 0, 0, 20)
            tempCanvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), overlayPaint)

        } catch (e: Exception) {
            e.printStackTrace()
            scaledBitmap = null
        }
    }

    override fun hasParticles() = true
    override fun getParticlesPerHit() = 4
    override fun getParticleLifetimeMs() = 600L

    override fun getWhiteKeyColor() = Color.parseColor("#12101A")
    override fun getBlackKeyColor() = Color.parseColor("#08060C")
    override fun getPressedWhiteKeyColor() = Color.parseColor("#2A1A3E")
    override fun getPressedBlackKeyColor() = Color.parseColor("#15001A")

    override fun getSheetMusicBackgroundColor() = Color.parseColor("#C9C9C9")
    override fun getStaffLineColor() = Color.parseColor("#40FF00FF")
    override fun getCurrentTimeIndicatorColor() = Color.parseColor("#00FFFF")

    override fun release() {
        super.release()
        cityBitmap?.recycle()
        cityBitmap = null
        scaledBitmap?.recycle()
        scaledBitmap = null
        cachedWidth = 0
        cachedHeight = 0
    }
}

// =============================================================================
// MINIMAL THEME - Noir et blanc élégant
// =============================================================================

class MinimalTheme : BasePracticeTheme() {

    override val id = "minimal"
    override val displayName = "Minimal"
    override val description = "Élégance épurée en noir et blanc"


    override fun getBackgroundColors() = Pair(
        Color.parseColor("#0A0A0A"),  // Noir pur
        Color.parseColor("#121212")   // Noir légèrement plus clair
    )

    override fun getHitZoneColor() = Color.WHITE
    override fun getGridLineColor() = Color.parseColor("#15FFFFFF")

    override fun getNoteColor(pitchClass: Int): Int {
        // Dégradé de gris basé sur la note
        val brightness = 0.5f + (pitchClass / 12f) * 0.5f
        val gray = (brightness * 255).toInt()
        return Color.rgb(gray, gray, gray)
    }

    // Pas de glow ni de particules pour rester minimal
    override fun hasGlowEffect() = false
    override fun hasParticles() = false
    override fun hasAnimatedBackground() = false

    // Piano classique noir et blanc
    override fun getWhiteKeyColor() = Color.parseColor("#FAFAFA")
    override fun getBlackKeyColor() = Color.parseColor("#1A1A1A")
    override fun getPressedWhiteKeyColor() = Color.parseColor("#E0E0E0")
    override fun getPressedBlackKeyColor() = Color.parseColor("#333333")

    override fun getSheetMusicBackgroundColor() = Color.parseColor("#C9C9C9")
    override fun getStaffLineColor() = Color.parseColor("#40FFFFFF")
    override fun getCurrentTimeIndicatorColor() = Color.WHITE
}
