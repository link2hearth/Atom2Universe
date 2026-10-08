package com.Atom2Universe.app.midi.practice.themes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.toColorInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Thème "Ocean Deep" - Monde sous-marin
 * Version optimisée pour performances (60fps sans chauffe)
 *
 * Optimisations appliquées:
 * - Réduction du nombre de bulles (25 -> 12)
 * - Banc de poissons optimisé avec boids légers
 * - Rayons de lumière limités pour éviter les shaders coûteux
 * - Caching des valeurs sin/cos via lookup table
 * - Pré-allocation des shaders (réutilisés quand possible)
 * - Réduction de la fréquence de mise à jour des éléments
 */
class OceanDeepTheme : BasePracticeTheme() {

    override val id = "ocean_deep"
    override val displayName = "Océan Profond"
    override val description = "Plongez dans les profondeurs avec bulles et vie marine"
    companion object {
        // Couleurs de l'océan profond
        private val DEEP_COLORS = intArrayOf(
            0xFF0077B6.toInt(),  // C  - Bleu profond
            0xFF0096C7.toInt(),  // C#
            0xFF00B4D8.toInt(),  // D  - Bleu clair
            0xFF48CAE4.toInt(),  // D#
            0xFF90E0EF.toInt(),  // E  - Cyan léger
            0xFFADE8F4.toInt(),  // F
            0xFFCAF0F8.toInt(),  // F# - Très clair
            0xFF00F5D4.toInt(),  // G  - Turquoise lumineux
            0xFF00BBF9.toInt(),  // G# - Bleu électrique
            0xFF00A8E8.toInt(),  // A
            0xFF007EA7.toInt(),  // A#
            0xFF003459.toInt()   // B  - Bleu très profond
        )

        // Limites réduites pour performance
        private const val MAX_BACKGROUND_BUBBLES = 12  // Réduit de 25
        private const val MAX_FISH = 30                 // Quelques dizaines de petits poissons
        private const val FISH_GROUP_SIZE = 5
        private const val MAX_LIGHT_RAYS = 2            // 1 ou 2 rayons max

        // Lookup table pour sin (256 entrées pour 2*PI)
        private const val SIN_TABLE_SIZE = 256
        private val SIN_TABLE = FloatArray(SIN_TABLE_SIZE) { i ->
            sin(i * 2.0 * PI / SIN_TABLE_SIZE).toFloat()
        }

        // Couleurs de poissons pré-définies
        private val FISH_COLORS = intArrayOf(
            0xFFFF6B6B.toInt(),  // Rouge
            0xFF4ECDC4.toInt(),  // Turquoise
            0xFFFFE66D.toInt(),  // Jaune doré
            0xFF95E1D3.toInt()   // Vert d'eau
        )

        private val LIGHT_RAY_COLORS = intArrayOf(
            0xFFB7F5FF.toInt(),  // Bleu très clair
            0xFF9EE7FF.toInt(),  // Cyan doux
            0xFFBFFFD6.toInt(),  // Vert d'eau
            0xFFFFF2B3.toInt()   // Doré pâle
        )
    }

    // Lookup table helper - évite sin() à chaque frame
    private fun fastSin(radians: Float): Float {
        val normalized = ((radians / (2 * PI.toFloat())) % 1f + 1f) % 1f
        return SIN_TABLE[(normalized * SIN_TABLE_SIZE).toInt().coerceIn(0, SIN_TABLE_SIZE - 1)]
    }

    // État d'animation
    private var animationTimeMs = 0L
    private var wavePhase = 0f
    private var cachedWaveSin = 0f  // Cache de sin(wavePhase)

    // Bulles d'arrière-plan
    private val backgroundBubbles = mutableListOf<BackgroundBubble>()

    // Poissons
    private val fish = mutableListOf<Fish>()
    private val fishGroups = mutableListOf<FishGroup>()

    // Rayons de lumière
    private val lightRays = mutableListOf<LightRay>()
    private var lightRaySkew = 0f

    // Dauphin
    private var dolphin: Dolphin? = null

    // Paints optimisés (créés une seule fois)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bubbleHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 255, 255, 255)
    }

    private val bubbleOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val fishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val lightRayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dolphinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#A7D8F5".toColorInt()
    }

    private val backgroundGradientPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var lastWidth = 0
    private var lastHeight = 0

    // Shaders pré-alloués pour réutilisation
    private var backgroundShader: LinearGradient? = null
    private var lastBackgroundShaderOffset = -1f

    // Couleurs pré-allouées pour le background
    private val backgroundColors = intArrayOf(
        "#001219".toColorInt(),
        "#002233".toColorInt(),
        "#003845".toColorInt(),
        "#005F73".toColorInt()
    )
    private val backgroundPositions = floatArrayOf(0f, 0.3f, 0.6f, 1f)

    // ========== INTERFACE IMPLEMENTATION ==========

    override fun getBackgroundColors() = Pair(
        "#001219".toColorInt(),  // Bleu très profond
        "#005F73".toColorInt()   // Bleu-vert profond
    )

    override fun getHitZoneColor() = "#00F5D4".toColorInt()  // Turquoise lumineux

    override fun getGridLineColor() = "#1500F5D4".toColorInt()

    override fun getNoteColor(pitchClass: Int) = DEEP_COLORS[pitchClass % 12]
    override fun getSheetMusicNoteColor() = "#0077B6".toColorInt()

    // Notes en forme de bulles
    override fun hasCustomNoteShape() = true

    override fun drawNote(
        canvas: Canvas,
        rect: RectF,
        pitchClass: Int,
        velocity: Int,
        paint: Paint,
        cornerRadius: Float
    ) {
        val baseColor = getNoteColor(pitchClass)
        val alphaFactor = 0.7f + (velocity / 127f) * 0.3f

        // La bulle prend la forme du rectangle (ovale/ellipse)
        // Plus la note est longue, plus la bulle est étirée verticalement
        val centerX = rect.centerX()
        val radiusX = rect.width() / 2f * 0.95f
        val radiusY = rect.height() / 2f * 0.95f

        // Corps de la bulle avec dégradé
        val gradientColors = intArrayOf(
            adjustAlpha(lightenColor(baseColor, 0.4f), alphaFactor),
            adjustAlpha(baseColor, alphaFactor),
            adjustAlpha(darkenColor(baseColor), alphaFactor * 0.9f)
        )

        // Dégradé du haut vers le bas pour effet 3D
        bubblePaint.shader = LinearGradient(
            rect.left, rect.top,
            rect.left, rect.bottom,
            gradientColors,
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        // Dessiner l'ellipse (bulle étirée)
        tempRect.set(rect.left, rect.top, rect.right, rect.bottom)
        canvas.drawOval(tempRect, bubblePaint)

        // Contour subtil lumineux
        bubbleOutlinePaint.color = adjustAlpha(lightenColor(baseColor, 0.6f), 0.4f)
        bubbleOutlinePaint.strokeWidth = 2f
        canvas.drawOval(tempRect, bubbleOutlinePaint)

        // Reflet principal (ellipse blanche en haut)
        val highlightHeight = minOf(radiusY * 0.4f, 20f)
        val highlightWidth = radiusX * 0.6f
        bubbleHighlightPaint.alpha = 160
        tempRect.set(
            centerX - highlightWidth,
            rect.top + radiusY * 0.1f,
            centerX + highlightWidth,
            rect.top + radiusY * 0.1f + highlightHeight
        )
        canvas.drawOval(tempRect, bubbleHighlightPaint)

        // Petit reflet secondaire
        if (rect.height() > 30f) {
            val smallHighlightSize = minOf(radiusX * 0.2f, 8f)
            bubbleHighlightPaint.alpha = 120
            canvas.drawCircle(
                centerX - radiusX * 0.3f,
                rect.top + radiusY * 0.25f,
                smallHighlightSize,
                bubbleHighlightPaint
            )
        }

        bubbleHighlightPaint.alpha = 180  // Reset
    }

    override fun hasGlowEffect() = true
    override fun getGlowIntensity() = 0.5f
    override fun getGlowRadiusDp() = 12f

    override fun hasAnimatedBackground() = true

    override fun updateBackgroundAnimation(deltaMs: Long) {
        animationTimeMs += deltaMs
        wavePhase = (animationTimeMs * 0.001f) % (2 * PI.toFloat())
        cachedWaveSin = fastSin(wavePhase)  // Cache une fois par frame

        // Mettre à jour les bulles d'arrière-plan
        updateBackgroundBubbles(deltaMs)

        // Mettre à jour les poissons
        updateFish(deltaMs)

        // Mettre à jour le dauphin
        updateDolphin(deltaMs)

        // Les rayons de lumière n'ont pas besoin de mise à jour
        // (leur animation est basée sur wavePhase dans draw)
    }

    override fun drawAnimatedBackground(canvas: Canvas, width: Int, height: Int) {
        // Mettre à jour les dimensions (utilisées pour les limites de mouvement)
        lastWidth = width
        lastHeight = height

        // Initialiser les éléments seulement s'ils n'existent pas encore
        // (ne pas réinitialiser lors du redimensionnement pour éviter les sauts visuels)
        if (backgroundBubbles.isEmpty() && width > 0 && height > 0) {
            initializeElements(width, height)
        }

        // 1. Fond avec dégradé ondulant
        drawWavyBackground(canvas, width, height)

        // 2. Rayons de soleil sous-marins
        drawLightRays(canvas, height)

        // 3. Bulles d'arrière-plan
        drawBackgroundBubbles(canvas)

        // 4. Dauphin
        drawDolphin(canvas)

        // 5. Poissons (en arrière des notes)
        drawFish(canvas)
    }

    private fun initializeElements(width: Int, height: Int) {
        // Initialiser les bulles d'arrière-plan
        backgroundBubbles.clear()
        repeat(MAX_BACKGROUND_BUBBLES) {
            backgroundBubbles.add(createRandomBubble(width, height))
        }

        // Initialiser les poissons
        fish.clear()
        fishGroups.clear()
        val groupCount = (MAX_FISH / FISH_GROUP_SIZE).coerceAtLeast(1)
        repeat(groupCount) { groupIndex ->
            fishGroups.add(createRandomFishGroup(width, height, groupIndex))
        }

        // Initialiser le dauphin
        dolphin = createRandomDolphin(width, height)

        // Initialiser les rayons de lumière
        lightRays.clear()
        val rayCount = Random.nextInt(1, MAX_LIGHT_RAYS + 1)
        lightRaySkew = if (Random.nextBoolean()) {
            Random.nextFloat() * 0.2f + 0.15f
        } else {
            -(Random.nextFloat() * 0.2f + 0.15f)
        }
        repeat(rayCount) {
            lightRays.add(createRandomLightRay(width))
        }
    }

    private fun drawWavyBackground(canvas: Canvas, width: Int, height: Int) {
        // Utiliser la valeur sin cachée
        val waveOffset = cachedWaveSin * 20f

        // Réutiliser le shader si l'offset est similaire (évite recréation)
        val roundedOffset = (waveOffset * 2).toInt() / 2f  // Arrondi à 0.5
        if (backgroundShader == null || roundedOffset != lastBackgroundShaderOffset) {
            backgroundShader = LinearGradient(
                0f, roundedOffset,
                0f, height.toFloat() + roundedOffset,
                backgroundColors,
                backgroundPositions,
                Shader.TileMode.CLAMP
            )
            lastBackgroundShaderOffset = roundedOffset
        }

        backgroundGradientPaint.shader = backgroundShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundGradientPaint)
    }

    private fun drawLightRays(canvas: Canvas, height: Int) {
        // Rayons de soleil sous-marins - version simplifiée
        val rayHeight = height * 0.7f

        for (ray in lightRays) {
            // Utiliser fastSin au lieu de sin
            val flickerValue = fastSin(wavePhase * ray.flickerSpeed + ray.phase)
            val alpha = (ray.alpha * (0.5f + flickerValue * 0.5f)).coerceIn(0.02f, 0.08f)

            // Position X avec léger mouvement (utiliser fastSin)
            val x = ray.x + fastSin(wavePhase * 0.3f + ray.phase) * 15f
            val topWidth = ray.width * 0.1f
            val bottomWidth = ray.width * 0.6f

            // Simplifier: utiliser une couleur semi-transparente au lieu d'un shader
            // (économise la création de LinearGradient à chaque frame)
            val alphaInt = (alpha * 255).toInt().coerceAtMost(25)
            lightRayPaint.shader = null
            lightRayPaint.color = Color.argb(
                alphaInt,
                Color.red(ray.color),
                Color.green(ray.color),
                Color.blue(ray.color)
            )

            // Dessiner un trapèze simple
            tempPath.reset()
            val skewOffset = ray.skew * rayHeight
            tempPath.moveTo(x - topWidth / 2 + skewOffset, 0f)
            tempPath.lineTo(x + topWidth / 2 + skewOffset, 0f)
            tempPath.lineTo(x + bottomWidth / 2 - skewOffset, rayHeight)
            tempPath.lineTo(x - bottomWidth / 2 - skewOffset, rayHeight)
            tempPath.close()

            canvas.drawPath(tempPath, lightRayPaint)
        }
    }

    private fun updateBackgroundBubbles(deltaMs: Long) {
        // Pré-calculer la valeur de wobble pour toutes les bulles
        val wobbleBase = fastSin(animationTimeMs * 0.002f)

        val iterator = backgroundBubbles.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            bubble.y -= bubble.speed * deltaMs / 16f
            // Utiliser wobbleBase + phase pour variation (évite sin() par bulle)
            bubble.x += (wobbleBase + fastSin(bubble.phase)) * bubble.wobble * 0.5f

            // Si la bulle sort de l'écran, la recycler
            if (bubble.y + bubble.radius < 0) {
                iterator.remove()
            }
        }

        // Ajouter de nouvelles bulles si nécessaire (une à la fois max)
        if (backgroundBubbles.size < MAX_BACKGROUND_BUBBLES && lastHeight > 0) {
            val newBubble = createRandomBubble(lastWidth, lastHeight)
            newBubble.y = lastHeight.toFloat() + newBubble.radius
            backgroundBubbles.add(newBubble)
        }
    }

    private fun drawBackgroundBubbles(canvas: Canvas) {
        for (bubble in backgroundBubbles) {
            bubblePaint.shader = null
            bubblePaint.color = Color.argb(
                (bubble.alpha * 255).toInt(),
                100, 200, 255
            )
            canvas.drawCircle(bubble.x, bubble.y, bubble.radius, bubblePaint)

            // Petit reflet
            bubbleHighlightPaint.alpha = (bubble.alpha * 150).toInt()
            canvas.drawCircle(
                bubble.x - bubble.radius * 0.3f,
                bubble.y - bubble.radius * 0.3f,
                bubble.radius * 0.2f,
                bubbleHighlightPaint
            )
        }
        bubbleHighlightPaint.alpha = 180
    }

    private fun updateFish(deltaMs: Long) {
        if (fish.isEmpty() || lastWidth <= 0 || lastHeight <= 0) return

        val deltaFactor = deltaMs / 16f
        val minY = lastHeight * 0.15f
        val maxY = lastHeight * 0.75f
        val separationRadius = 24f
        val maxSpeed = 1.8f
        val minSpeed = 0.4f
        val dolphinAvoidRadius = 140f
        val dolphinPosition = dolphin?.let { Pair(it.x, it.y) }

        for (group in fishGroups) {
            updateFishGroup(group, deltaFactor, minY, maxY)
        }

        for (i in fish.indices) {
            val fishItem = fish[i]
            var vx = fishItem.vx
            var vy = fishItem.vy

            for (j in fish.indices) {
                if (i == j) continue
                val other = fish[j]
                val dx = other.x - fishItem.x
                val dy = other.y - fishItem.y
                val distanceSq = dx * dx + dy * dy
                if (distanceSq < separationRadius * separationRadius && distanceSq > 0.01f) {
                    val inverse = 1f / distanceSq
                    vx -= dx * inverse
                    vy -= dy * inverse
                }
            }

            if (dolphinPosition != null) {
                val dx = dolphinPosition.first - fishItem.x
                val dy = dolphinPosition.second - fishItem.y
                val distanceSq = dx * dx + dy * dy
                if (distanceSq < dolphinAvoidRadius * dolphinAvoidRadius && distanceSq > 0.01f) {
                    val inverse = 1f / distanceSq
                    vx -= dx * inverse * 180f
                    vy -= dy * inverse * 180f
                }
            }

            val speed = kotlin.math.sqrt(vx * vx + vy * vy)
            if (speed > maxSpeed) {
                val scale = maxSpeed / speed
                vx *= scale
                vy *= scale
            } else if (speed < minSpeed) {
                val scale = minSpeed / (speed.coerceAtLeast(0.01f))
                vx *= scale
                vy *= scale
            }

            fishItem.vx = vx
            fishItem.vy = vy
            fishItem.x += fishItem.vx * deltaFactor
            fishItem.y += fishItem.vy * deltaFactor

            if (fishItem.x > lastWidth + 40) fishItem.x = -40f
            if (fishItem.x < -40) fishItem.x = lastWidth + 40f

            if (fishItem.y < minY) fishItem.vy += 0.05f
            if (fishItem.y > maxY) fishItem.vy -= 0.05f
        }
    }

    private fun updateFishGroup(group: FishGroup, deltaFactor: Float, minY: Float, maxY: Float) {
        val driftX = fastSin(wavePhase * 0.8f + group.phase) * 0.15f
        val driftY = fastSin(wavePhase * 0.6f + group.phase) * 0.1f
        group.vx += driftX * 0.02f
        group.vy += driftY * 0.02f

        val groupSpeed = kotlin.math.sqrt(group.vx * group.vx + group.vy * group.vy).coerceAtLeast(0.01f)
        if (groupSpeed > 1.2f) {
            val scale = 1.2f / groupSpeed
            group.vx *= scale
            group.vy *= scale
        }

        group.x += group.vx * deltaFactor
        group.y += group.vy * deltaFactor

        if (group.x > lastWidth + 80) group.x = -80f
        if (group.x < -80) group.x = lastWidth + 80f
        if (group.y < minY) group.vy += 0.04f
        if (group.y > maxY) group.vy -= 0.04f

        for (member in group.members) {
            val targetX = group.x + member.offsetX
            val targetY = group.y + member.offsetY
            val toTargetX = targetX - member.fish.x
            val toTargetY = targetY - member.fish.y
            val followStrength = 0.008f
            member.fish.vx += (group.vx - member.fish.vx) * 0.06f
            member.fish.vy += (group.vy - member.fish.vy) * 0.06f
            member.fish.vx += toTargetX * followStrength
            member.fish.vy += toTargetY * followStrength
        }
    }

    private fun updateDolphin(deltaMs: Long) {
        val dolphinItem = dolphin ?: return
        val deltaFactor = deltaMs / 16f
        val minY = lastHeight * 0.2f
        val maxY = lastHeight * 0.7f

        val drift = fastSin(wavePhase * 0.7f + dolphinItem.phase) * 0.08f
        dolphinItem.vx += drift * 0.04f
        dolphinItem.vy += drift * 0.02f

        val wanderX = fastSin(wavePhase * 0.35f + dolphinItem.phase * 1.7f) * 0.12f
        val wanderY = fastSin(wavePhase * 0.42f + dolphinItem.phase * 1.3f) * 0.18f
        dolphinItem.vx += wanderX * 0.03f
        dolphinItem.vy += wanderY * 0.03f

        val speed = kotlin.math.sqrt(dolphinItem.vx * dolphinItem.vx + dolphinItem.vy * dolphinItem.vy).coerceAtLeast(0.01f)
        if (speed > 1.8f) {
            val scale = 1.8f / speed
            dolphinItem.vx *= scale
            dolphinItem.vy *= scale
        }

        dolphinItem.x += dolphinItem.vx * deltaFactor
        dolphinItem.y += dolphinItem.vy * deltaFactor

        if (dolphinItem.x > lastWidth + 120) dolphinItem.x = -120f
        if (dolphinItem.x < -120) dolphinItem.x = lastWidth + 120f

        if (dolphinItem.y < minY) dolphinItem.vy += 0.06f
        if (dolphinItem.y > maxY) dolphinItem.vy -= 0.06f
    }

    private fun drawFish(canvas: Canvas) {
        for (fishItem in fish) {
            drawStylizedFish(canvas, fishItem)
        }
    }

    private fun drawDolphin(canvas: Canvas) {
        val dolphinItem = dolphin ?: return
        val scale = dolphinItem.scale
        val x = dolphinItem.x
        val y = dolphinItem.y

        dolphinPaint.alpha = (dolphinItem.alpha * 255).toInt().coerceIn(0, 255)

        val bodyLength = 80f * scale
        val bodyHeight = 22f * scale
        val finHeight = 18f * scale
        val tailLength = 26f * scale
        val facingRight = dolphinItem.vx >= 0

        tempPath.reset()

        if (facingRight) {
            tempPath.moveTo(x - bodyLength / 2, y)
            tempPath.cubicTo(
                x - bodyLength / 2, y - bodyHeight / 2,
                x + bodyLength / 2, y - bodyHeight / 2,
                x + bodyLength / 2, y
            )
            tempPath.cubicTo(
                x + bodyLength / 2, y + bodyHeight / 2,
                x - bodyLength / 2, y + bodyHeight / 2,
                x - bodyLength / 2, y
            )
            tempPath.moveTo(x - bodyLength / 6, y - bodyHeight / 2)
            tempPath.lineTo(x - bodyLength / 12, y - bodyHeight / 2 - finHeight)
            tempPath.lineTo(x, y - bodyHeight / 2)
            tempPath.close()
            tempPath.moveTo(x - bodyLength / 2, y)
            tempPath.lineTo(x - bodyLength / 2 - tailLength, y - bodyHeight * 0.6f)
            tempPath.lineTo(x - bodyLength / 2 - tailLength, y + bodyHeight * 0.6f)
            tempPath.close()
        } else {
            tempPath.moveTo(x + bodyLength / 2, y)
            tempPath.cubicTo(
                x + bodyLength / 2, y - bodyHeight / 2,
                x - bodyLength / 2, y - bodyHeight / 2,
                x - bodyLength / 2, y
            )
            tempPath.cubicTo(
                x - bodyLength / 2, y + bodyHeight / 2,
                x + bodyLength / 2, y + bodyHeight / 2,
                x + bodyLength / 2, y
            )
            tempPath.moveTo(x + bodyLength / 6, y - bodyHeight / 2)
            tempPath.lineTo(x + bodyLength / 12, y - bodyHeight / 2 - finHeight)
            tempPath.lineTo(x, y - bodyHeight / 2)
            tempPath.close()
            tempPath.moveTo(x + bodyLength / 2, y)
            tempPath.lineTo(x + bodyLength / 2 + tailLength, y - bodyHeight * 0.6f)
            tempPath.lineTo(x + bodyLength / 2 + tailLength, y + bodyHeight * 0.6f)
            tempPath.close()
        }

        canvas.drawPath(tempPath, dolphinPaint)
    }

    /**
     * Dessine un poisson stylisé (silhouette géométrique simple)
     */
    private fun drawStylizedFish(canvas: Canvas, fishItem: Fish) {
        val scale = fishItem.scale
        val x = fishItem.x
        val y = fishItem.y
        val facingRight = fishItem.vx > 0

        fishPaint.color = adjustAlpha(fishItem.color, fishItem.alpha)

        tempPath.reset()

        // Corps du poisson (ellipse + queue triangulaire)
        val bodyLength = 34f * scale
        val bodyHeight = 14f * scale
        val tailLength = 16f * scale

        if (facingRight) {
            // Corps (ellipse simplifiée via bézier)
            tempPath.moveTo(x - bodyLength / 2, y)
            tempPath.cubicTo(
                x - bodyLength / 2, y - bodyHeight / 2,
                x + bodyLength / 2, y - bodyHeight / 2,
                x + bodyLength / 2, y
            )
            tempPath.cubicTo(
                x + bodyLength / 2, y + bodyHeight / 2,
                x - bodyLength / 2, y + bodyHeight / 2,
                x - bodyLength / 2, y
            )

            // Queue
            tempPath.moveTo(x - bodyLength / 2, y)
            tempPath.lineTo(x - bodyLength / 2 - tailLength, y - bodyHeight * 0.6f)
            tempPath.lineTo(x - bodyLength / 2 - tailLength, y + bodyHeight * 0.6f)
            tempPath.close()
        } else {
            // Corps (miroir)
            tempPath.moveTo(x + bodyLength / 2, y)
            tempPath.cubicTo(
                x + bodyLength / 2, y - bodyHeight / 2,
                x - bodyLength / 2, y - bodyHeight / 2,
                x - bodyLength / 2, y
            )
            tempPath.cubicTo(
                x - bodyLength / 2, y + bodyHeight / 2,
                x + bodyLength / 2, y + bodyHeight / 2,
                x + bodyLength / 2, y
            )

            // Queue
            tempPath.moveTo(x + bodyLength / 2, y)
            tempPath.lineTo(x + bodyLength / 2 + tailLength, y - bodyHeight * 0.6f)
            tempPath.lineTo(x + bodyLength / 2 + tailLength, y + bodyHeight * 0.6f)
            tempPath.close()
        }

        canvas.drawPath(tempPath, fishPaint)

        // Œil (petit cercle blanc)
        val eyeX = if (facingRight) x + bodyLength * 0.25f else x - bodyLength * 0.25f
        val eyeY = y - bodyHeight * 0.15f
        fishPaint.color = Color.argb((fishItem.alpha * 255).toInt(), 255, 255, 255)
        canvas.drawCircle(eyeX, eyeY, 2f * scale, fishPaint)
    }

    // Note: updateLightRays supprimé - animation gérée dans drawLightRays via wavePhase

    // ========== PIANO STYLE ==========

    override fun hasCustomPianoStyle() = true

    override fun getWhiteKeyColor() = "#E8F4F8".toColorInt()  // Blanc légèrement bleuté
    override fun getBlackKeyColor() = "#003459".toColorInt()  // Bleu profond
    override fun getPressedWhiteKeyColor() = "#B8D8E8".toColorInt()
    override fun getPressedBlackKeyColor() = "#002040".toColorInt()

    // ========== SHEET MUSIC ==========

    override fun getSheetMusicBackgroundColor() = "#001825".toColorInt()
    override fun getStaffLineColor() = "#3000F5D4".toColorInt()
    override fun getCurrentTimeIndicatorColor() = "#00F5D4".toColorInt()
    override fun getSheetMusicSymbolColor() = "#E0F0FF".toColorInt()  // Blanc-bleu pour fond sombre

    // ========== HELPERS ==========

    private fun createRandomBubble(width: Int, height: Int) = BackgroundBubble(
        x = Random.nextFloat() * width,
        y = Random.nextFloat() * height,
        radius = Random.nextFloat() * 8f + 2f,
        speed = Random.nextFloat() * 0.8f + 0.2f,
        alpha = Random.nextFloat() * 0.3f + 0.1f,
        phase = Random.nextFloat() * PI.toFloat() * 2,
        wobble = Random.nextFloat() * 0.5f + 0.1f
    )

    private fun createRandomFish(width: Int, height: Int): Fish {
        val baseY = Random.nextFloat() * height * 0.6f + height * 0.1f
        return Fish(
            x = Random.nextFloat() * width,
            y = baseY,
            vx = Random.nextFloat() * 1.6f - 0.8f,
            vy = Random.nextFloat() * 0.8f - 0.4f,
            scale = Random.nextFloat() * 0.2f + 0.3f,
            color = FISH_COLORS[Random.nextInt(FISH_COLORS.size)],  // Utiliser le tableau pré-alloué
            alpha = Random.nextFloat() * 0.35f + 0.25f,
            phase = Random.nextFloat() * PI.toFloat() * 2
        )
    }

    private fun createRandomLightRay(width: Int) = LightRay(
        x = Random.nextFloat() * width,
        width = Random.nextFloat() * 80f + 50f,
        alpha = Random.nextFloat() * 0.12f + 0.06f,
        phase = Random.nextFloat() * PI.toFloat() * 2,
        flickerSpeed = Random.nextFloat() * 0.5f + 0.5f,
        skew = lightRaySkew,
        color = LIGHT_RAY_COLORS[Random.nextInt(LIGHT_RAY_COLORS.size)]
    )

    private fun createRandomFishGroup(width: Int, height: Int, index: Int): FishGroup {
        val baseX = Random.nextFloat() * width
        val baseY = Random.nextFloat() * height * 0.6f + height * 0.1f
        val group = FishGroup(
            id = index,
            x = baseX,
            y = baseY,
            vx = Random.nextFloat() * 0.8f - 0.4f,
            vy = Random.nextFloat() * 0.4f - 0.2f,
            phase = Random.nextFloat() * PI.toFloat() * 2,
            members = mutableListOf()
        )

        repeat(FISH_GROUP_SIZE) {
            val fishItem = createRandomFish(width, height)
            fishItem.x = baseX
            fishItem.y = baseY
            val offset = createNonOverlappingOffset(group.members)
            group.members.add(FishGroupMember(fishItem, offset.first, offset.second))
            fish.add(fishItem)
        }

        return group
    }

    private fun createNonOverlappingOffset(members: List<FishGroupMember>): Pair<Float, Float> {
        val minDistance = 18f
        repeat(20) {
            val angle = Random.nextFloat() * PI.toFloat() * 2
            val radius = Random.nextFloat() * 28f + 8f
            val offsetX = cos(angle) * radius
            val offsetY = sin(angle) * radius
            val isFarEnough = members.none { existing ->
                val dx = existing.offsetX - offsetX
                val dy = existing.offsetY - offsetY
                dx * dx + dy * dy < minDistance * minDistance
            }
            if (isFarEnough) return Pair(offsetX, offsetY)
        }
        return Pair(Random.nextFloat() * 20f - 10f, Random.nextFloat() * 20f - 10f)
    }

    private fun createRandomDolphin(width: Int, height: Int): Dolphin {
        val x = Random.nextFloat() * width
        val y = Random.nextFloat() * height * 0.4f + height * 0.2f
        return Dolphin(
            x = x,
            y = y,
            vx = Random.nextFloat() * 1.4f - 0.7f,
            vy = Random.nextFloat() * 0.6f - 0.3f,
            alpha = 0.7f,
            scale = 0.9f,
            phase = Random.nextFloat() * PI.toFloat() * 2
        )
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r + (255 - r) * factor).toInt().coerceIn(0, 255),
            (g + (255 - g) * factor).toInt().coerceIn(0, 255),
            (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        )
    }

    private fun darkenColor(color: Int): Int {
        val factor = 0.15f
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r * (1 - factor)).toInt().coerceIn(0, 255),
            (g * (1 - factor)).toInt().coerceIn(0, 255),
            (b * (1 - factor)).toInt().coerceIn(0, 255)
        )
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (alpha * 255).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    override fun hasParticles() = true
    override fun getParticlesPerHit() = 6  // Réduit de 8 pour performance
    override fun getParticleLifetimeMs() = 800L  // Réduit de 1000ms

    override fun release() {
        super.release()
        backgroundBubbles.clear()
        fish.clear()
        fishGroups.clear()
        lightRays.clear()
        dolphin = null
        backgroundShader = null
        lastBackgroundShaderOffset = -1f
    }

    override fun getParticleColor(noteColor: Int): Int {
        // Particules style bulles - plus claires
        return lightenColor(noteColor, 0.4f)
    }

    // ========== DATA CLASSES ==========

    private data class BackgroundBubble(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speed: Float,
        val alpha: Float,
        val phase: Float,
        val wobble: Float
    )

    private data class Fish(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val scale: Float,
        val color: Int,
        val alpha: Float,
        val phase: Float
    )

    private data class FishGroupMember(
        val fish: Fish,
        val offsetX: Float,
        val offsetY: Float
    )

    private data class FishGroup(
        val id: Int,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val phase: Float,
        val members: MutableList<FishGroupMember>
    )

    private data class Dolphin(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val alpha: Float,
        val scale: Float,
        val phase: Float
    )

    private data class LightRay(
        val x: Float,
        val width: Float,
        val alpha: Float,
        val phase: Float,
        val flickerSpeed: Float,
        val skew: Float,
        val color: Int
    )
}
