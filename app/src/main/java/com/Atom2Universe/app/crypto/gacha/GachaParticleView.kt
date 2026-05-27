package com.Atom2Universe.app.crypto.gacha

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GachaParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f
    private var rawProgress = 0f
    private var speedMultiplier = 1f
    private var effectCompleted = false
    private var falloutActive = false
    private var falloutStartMs = 0L
    private var rarity: GachaRarity = GachaRarity.PRIMORDIAL
    private var categoryColor: Int = 0xFFFFFFFF.toInt()
    private var animator: ValueAnimator? = null

    private val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint2 = Paint(Paint.ANTI_ALIAS_FLAG)  // pour les effets glow sans écraser le state
    private val path   = Path()
    private val path2  = Path()

    private val baseParticles = mutableListOf<Particle>()
    private val particles     = mutableListOf<Particle>()
    private val falloutParticles = mutableListOf<FalloutParticle>()

    // Seeds figées pour les éclairs (évite les scintillements de path entre frames)
    private val lightningSeeds = Array(12) { LightningPath() }

    // Étoiles de fond fixes
    private val stars: List<FloatArray> = (0 until 100).map { i ->
        val rng = java.util.Random(i * 6364136L + 1442695040L)
        floatArrayOf(rng.nextFloat(), rng.nextFloat(), 0.5f + rng.nextFloat() * 1.5f)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    data class Particle(
        val angle: Float, val speed: Float,
        val size: Float,  val color: Int,
        val spin: Float,  val delay: Float,
        val wobble: Float = 0f   // amplitude de dérive latérale
    )

    data class LightningPath(
        val offsets: FloatArray = FloatArray(8),
        var color: Int = Color.WHITE,
        var originX: Float = 0f,
        var originY: Float = 0f,
        var destX: Float = 0f,
        var destY: Float = 0f
    )

    data class FalloutParticle(
        val xNorm: Float,
        val laneShift: Float,
        val fallSpeed: Float,
        val size: Float,
        val sway: Float,
        val delay: Float,
        val twinkle: Float,
        val tintMix: Float
    )

    companion object {
        private const val BASE_MS = 1500L

        // Palette arc-en-ciel complète pour les particules de base
        private val RAINBOW = listOf(
            0xFFFF3333.toInt(), 0xFFFF6600.toInt(), 0xFFFF9900.toInt(), 0xFFFFCC00.toInt(),
            0xFFFFFF00.toInt(), 0xFFCCFF00.toInt(), 0xFF66FF00.toInt(), 0xFF00FF66.toInt(),
            0xFF00FFCC.toInt(), 0xFF00CCFF.toInt(), 0xFF0088FF.toInt(), 0xFF4400FF.toInt(),
            0xFF8800FF.toInt(), 0xFFCC00FF.toInt(), 0xFFFF00CC.toInt(), 0xFFFF0066.toInt(),
            0xFFFFFFFF.toInt(), 0xFFEEEEFF.toInt(), 0xFFFFEEEE.toInt(), 0xFFEEFFEE.toInt(),
            0xFFFFEEFF.toInt(), 0xFFEEFFFF.toInt(), 0xFFFFFFCC.toInt(), 0xFFCCFFFF.toInt()
        )
    }

    // ── Durées totales ────────────────────────────────────────────────────────

    private fun totalDuration(r: GachaRarity): Long = when (r) {
        GachaRarity.PRIMORDIAL  -> 2200L
        GachaRarity.FUSION      -> BASE_MS + 2800L
        GachaRarity.SUPERNOVA   -> BASE_MS + 3200L
        GachaRarity.NEUTRONIQUE -> BASE_MS + 3800L
        GachaRarity.SPALLATION  -> BASE_MS + 4500L
        GachaRarity.SYNTHETIQUE -> BASE_MS + 5500L
    }

    private fun basePhaseEnd(r: GachaRarity): Float =
        if (r == GachaRarity.PRIMORDIAL) 1f else BASE_MS.toFloat() / totalDuration(r)

    // ── API publique ──────────────────────────────────────────────────────────

    fun playEffect(rarity: GachaRarity, categoryColor: Int = 0xFFFFFFFF.toInt(), onEnd: () -> Unit) {
        this.rarity = rarity
        this.categoryColor = categoryColor
        visibility = VISIBLE
        animator?.cancel()
        progress = 0f
        rawProgress = 0f
        speedMultiplier = 1f
        effectCompleted = false
        falloutActive = false
        falloutStartMs = 0L
        post {
            generateBaseParticles()
            generateParticles()
            generateLightningSeeds()
            generateFalloutParticles()
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = totalDuration(rarity)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val nextRaw = it.animatedValue as Float
                val delta = (nextRaw - rawProgress).coerceAtLeast(0f)
                rawProgress = nextRaw
                progress = (progress + delta * speedMultiplier).coerceAtMost(1f)
                invalidate()

                if (progress >= 1f && !effectCompleted) {
                    effectCompleted = true
                    falloutActive = true
                    falloutStartMs = SystemClock.uptimeMillis()
                    cancel()
                    onEnd()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!effectCompleted) {
                        effectCompleted = true
                        onEnd()
                    }
                }
            })
            start()
        }
    }

    fun setHoldSpeedBoost(enabled: Boolean) {
        speedMultiplier = if (enabled) 2f else 1f
    }

    fun stop() {
        animator?.cancel()
        progress = 0f
        rawProgress = 0f
        effectCompleted = false
        speedMultiplier = 1f
        falloutActive = false
        falloutStartMs = 0L
        visibility = GONE
        invalidate()
    }

    // ── Génération ────────────────────────────────────────────────────────────

    private fun generateBaseParticles() {
        baseParticles.clear()
        repeat(350) {
            baseParticles.add(Particle(
                angle  = Random.nextFloat() * 360f,
                speed  = Random.nextFloat() * 0.55f + 0.45f,
                size   = Random.nextFloat() * 14f + 3f,
                color  = RAINBOW[Random.nextInt(RAINBOW.size)],
                spin   = 0f,
                delay  = Random.nextFloat() * 0.07f,
                wobble = Random.nextFloat() * 60f - 30f
            ))
        }
    }

    private fun generateFalloutParticles() {
        falloutParticles.clear()
        repeat(220) {
            falloutParticles.add(
                FalloutParticle(
                    xNorm = Random.nextFloat(),
                    laneShift = Random.nextFloat() * 0.22f - 0.11f,
                    fallSpeed = 0.42f + Random.nextFloat() * 1.18f,
                    size = 1.8f + Random.nextFloat() * 5.8f,
                    sway = 18f + Random.nextFloat() * 70f,
                    delay = Random.nextFloat(),
                    twinkle = Random.nextFloat() * 6.28f,
                    tintMix = 0.45f + Random.nextFloat() * 0.55f
                )
            )
        }
    }

    private fun generateParticles() {
        particles.clear()
        val count = when (rarity) {
            GachaRarity.PRIMORDIAL  -> 0
            GachaRarity.FUSION      -> 80
            GachaRarity.SUPERNOVA   -> 100
            GachaRarity.NEUTRONIQUE -> 130
            GachaRarity.SPALLATION  -> 160
            GachaRarity.SYNTHETIQUE -> 200
        }
        val pool = rarityColors(rarity)
        repeat(count) {
            particles.add(Particle(
                angle  = Random.nextFloat() * 360f,
                speed  = Random.nextFloat() * 0.55f + 0.3f,
                size   = Random.nextFloat() * 15f + 4f,
                color  = pool[Random.nextInt(pool.size)],
                spin   = Random.nextFloat() * 900f - 450f,
                delay  = Random.nextFloat() * 0.3f,
                wobble = Random.nextFloat() * 80f - 40f
            ))
        }
    }

    // Génère des seeds d'éclairs (offsets fixes par frame pour éviter le scintillement)
    private fun generateLightningSeeds() {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        lightningSeeds.forEachIndexed { i, ls ->
            for (k in ls.offsets.indices) ls.offsets[k] = Random.nextFloat() * 2f - 1f
            // Origines aléatoires sur les bords ou depuis des coins
            val edge = Random.nextInt(4)
            ls.originX = when (edge) {
                0 -> Random.nextFloat() * w
                1 -> w
                2 -> Random.nextFloat() * w
                else -> 0f
            }
            ls.originY = when (edge) {
                0 -> 0f
                1 -> Random.nextFloat() * h
                2 -> h
                else -> Random.nextFloat() * h
            }
            // Destination : zone centrale ±150dp
            ls.destX = cx + (Random.nextFloat() - 0.5f) * 300f
            ls.destY = cy + (Random.nextFloat() - 0.5f) * 300f
            ls.color = when (i % 5) {
                0    -> 0xFFAA44FF.toInt()
                1    -> 0xFFFF00FF.toInt()
                2    -> 0xFF4488FF.toInt()
                3    -> 0xFFFF4400.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
        }
    }

    private fun rarityColors(r: GachaRarity): List<Int> = when (r) {
        GachaRarity.PRIMORDIAL  -> RAINBOW
        GachaRarity.FUSION      -> listOf(
            0xFF00CCFF.toInt(), 0xFF00AAFF.toInt(), 0xFF44DDFF.toInt(),
            0xFFAAEEFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF88CCFF.toInt(),
            0xFF0066FF.toInt(), 0xFFCCEEFF.toInt(), 0xFF4400FF.toInt()
        )
        GachaRarity.SUPERNOVA   -> listOf(
            0xFFFFCC00.toInt(), 0xFFFF8800.toInt(), 0xFFFFEE44.toInt(),
            0xFFFF6600.toInt(), 0xFFFFDD88.toInt(), 0xFFFFFFAA.toInt(),
            0xFFFF4400.toInt(), 0xFFFFBB00.toInt(), 0xFFFFFFFF.toInt()
        )
        GachaRarity.NEUTRONIQUE -> listOf(
            0xFFFF55FF.toInt(), 0xFFFF00CC.toInt(), 0xFF9955FF.toInt(),
            0xFF00FFFF.toInt(), 0xFFFFFF00.toInt(), 0xFFFF4444.toInt(),
            0xFF44FFFF.toInt(), 0xFFFF44FF.toInt(), 0xFF8844FF.toInt(),
            0xFFFFFFFF.toInt(), 0xFF00FF88.toInt(), 0xFF8800FF.toInt()
        )
        GachaRarity.SPALLATION  -> listOf(
            0xFF00FF88.toInt(), 0xFF00FF44.toInt(), 0xFF00CC66.toInt(),
            0xFFAAFFCC.toInt(), 0xFF44FFAA.toInt(), 0xFFCCFF88.toInt(),
            0xFF88FFCC.toInt(), 0xFFFFFFCC.toInt()
        )
        GachaRarity.SYNTHETIQUE -> listOf(
            0xFFFF3333.toInt(), 0xFFFF6600.toInt(), 0xFFFFFFFF.toInt(),
            0xFFCC00FF.toInt(), 0xFFFF0088.toInt(), 0xFF8800FF.toInt(),
            0xFFFF00FF.toInt(), 0xFFFF8800.toInt(), 0xFF00FFFF.toInt(),
            0xFFFFCC00.toInt(), 0xFFFF4400.toInt(), 0xFF4400FF.toInt()
        )
    }

    // ── Dispatch onDraw ───────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        drawBackgroundStars(canvas, w, h)

        if (progress <= 0f) return
        val cx = width / 2f; val cy = height / 2f
        val bpe = basePhaseEnd(rarity)
        val progressBase = (progress / bpe).coerceAtMost(1f)

        drawCinematicVignette(canvas, w, h, progress)
        drawBaseExplosion(canvas, cx, cy, progressBase, progress)
        drawCategoryAura(canvas, cx, cy, progress, totalDuration(rarity))

        if (rarity != GachaRarity.PRIMORDIAL && progress > bpe) {
            val ps = (progress - bpe) / (1f - bpe)
            when (rarity) {
                GachaRarity.FUSION      -> drawFusion(canvas, cx, cy, w, h, ps)
                GachaRarity.SUPERNOVA   -> drawSupernova(canvas, cx, cy, w, h, ps)
                GachaRarity.NEUTRONIQUE -> drawNeutronique(canvas, cx, cy, w, h, ps)
                GachaRarity.SPALLATION  -> drawSpallation(canvas, cx, cy, w, h, ps)
                GachaRarity.SYNTHETIQUE -> drawSynthetique(canvas, cx, cy, w, h, ps)
                else -> {}
            }
        }

        drawGravityFallout(canvas, w, h, progress, if (falloutActive) (SystemClock.uptimeMillis() - falloutStartMs) / 1000f else 0f)
        if (falloutActive) postInvalidateOnAnimation()
    }

    // ── COUCHE DE BASE ────────────────────────────────────────────────────────

    private fun drawBaseExplosion(canvas: Canvas, cx: Float, cy: Float, p: Float, gp: Float) {
        // Flash initial
        if (p < 0.14f) {
            val flashT = p / 0.14f
            val fa = if (flashT < 0.45f) flashT / 0.45f else 1f - (flashT - 0.45f) / 0.55f
            paint.color = Color.WHITE
            paint.alpha = (fa * 240).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        val bloomAlpha = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
        drawCenterBloom(canvas, cx, cy, 130f + p * 340f, Color.WHITE, (bloomAlpha * 0.35f).coerceIn(0f, 1f))

        val ga = when {
            gp < 0.70f -> 1f
            gp < 0.92f -> 1f - (gp - 0.70f) / 0.22f
            else       -> 0f
        }
        if (ga <= 0f) return

        paint.style = Paint.Style.FILL
        for (bp in baseParticles) {
            val t = ((p - bp.delay).coerceAtLeast(0f)).coerceAtMost(1f)
            if (t <= 0f) continue
            val et = 1f - (1f - t) * (1f - t) * (1f - t)
            val dist = et * 950f * bp.speed
            val wobbleX = sin((t * 5f + bp.wobble).toDouble()).toFloat() * bp.wobble * (1f - t)
            val x = cx + cos(Math.toRadians(bp.angle.toDouble())).toFloat() * dist + wobbleX
            val y = cy + sin(Math.toRadians(bp.angle.toDouble())).toFloat() * dist
            val rarityBlend = easeInOut((p - 0.2f) / 0.75f)
            val categoryBlend = easeInOut((p - 0.45f) / 0.5f)
            val rarityTint = blendColor(bp.color, rarity.color, rarityBlend * 0.82f)
            val finalColor = blendColor(rarityTint, categoryColor, categoryBlend * 0.72f)
            paint.color = finalColor
            paint.alpha = (ga * 245).toInt()
            canvas.drawCircle(x, y, bp.size * (1f - t * 0.42f), paint)
        }
    }

    // ── SPALLATION ────────────────────────────────────────────────────────────

    private fun drawSpallation(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Float) {
        drawCenterBloom(canvas, cx, cy, 160f + p * 240f, 0xFF33FF99.toInt(), (0.24f * (1f - p * 0.5f)).coerceIn(0f, 0.3f))

        // 3 anneaux verts successifs
        for (ring in 0..2) {
            val rStart = ring * 0.15f
            if (p < rStart) continue
            val rt = ((p - rStart) / 0.7f).coerceAtMost(1f)
            paint.color = listOf(0xFF00FF88.toInt(), 0xFF00CC66.toInt(), 0xFF44FFAA.toInt())[ring]
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (1f - rt) * 18f + 2f
            paint.alpha = ((1f - rt) * 220).toInt()
            canvas.drawCircle(cx, cy, rt * (380f + ring * 80f), paint)
        }
        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f

        // Spirale de points verts
        val spiralCount = 3
        for (s in 0 until spiralCount) {
            val sOffset = s * (360f / spiralCount)
            for (i in 0 until 30) {
                val t2 = (i / 30f)
                val spiralAngle = sOffset + p * 360f * 2f + t2 * 360f
                val spiralR = t2 * 320f * p
                val sx = cx + cos(Math.toRadians(spiralAngle.toDouble())).toFloat() * spiralR
                val sy = cy + sin(Math.toRadians(spiralAngle.toDouble())).toFloat() * spiralR
                val alpha = (1f - t2) * 200f * (if (p < 0.5f) p / 0.5f else 1f)
                paint.color = 0xFF00FF88.toInt()
                paint.alpha = alpha.toInt()
                canvas.drawCircle(sx, sy, 5f * (1f - t2), paint)
            }
        }

        // Particules radiales
        for (part in particles) {
            val t = ((p - part.delay).coerceAtLeast(0f)).coerceAtMost(1f)
            if (t <= 0f) continue
            val dist = t * 420f * part.speed
            val wob = sin((t * 6f + part.wobble * 0.05f).toDouble()).toFloat() * part.wobble * 0.3f
            val x = cx + cos(Math.toRadians(part.angle.toDouble())).toFloat() * dist + wob
            val y = cy + sin(Math.toRadians(part.angle.toDouble())).toFloat() * dist
            val a = if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f
            paint.color = part.color; paint.alpha = (a * 245).toInt()
            canvas.drawCircle(x, y, part.size * (1f - t * 0.5f), paint)
        }

        // Étoiles 4 branches scintillantes
        if (p > 0.4f) {
            val phase = (p - 0.4f) / 0.6f
            for (i in 0 until 14) {
                val angle = i * (360f / 14f) + phase * 60f
                val r = 160f + sin((phase * 4f + i * 0.9f).toDouble()).toFloat() * 70f
                val sx = cx + cos(Math.toRadians(angle.toDouble())).toFloat() * r
                val sy = cy + sin(Math.toRadians(angle.toDouble())).toFloat() * r
                val blink = (sin((p * 22f + i * 1.5f).toDouble()).toFloat() * 0.5f + 0.5f)
                drawStar4(canvas, sx, sy, 16f * blink, angle, 0xFF00FF88.toInt(), (blink * 240).toInt())
            }
        }

        // Halo de lucioles orbitales
        if (p > 0.2f) {
            val fireflyPhase = (p - 0.2f) / 0.8f
            for (i in 0 until 10) {
                val angle = i * 36f + fireflyPhase * 420f
                val r = 110f + i * 14f + sin((p * 9f + i).toDouble()).toFloat() * 18f
                val x = cx + cos(Math.toRadians(angle.toDouble())).toFloat() * r
                val y = cy + sin(Math.toRadians(angle.toDouble())).toFloat() * r
                val a = (0.4f + 0.6f * abs(sin((p * 16f + i).toDouble()).toFloat())) * (1f - fireflyPhase * 0.25f)
                drawCenterBloom(canvas, x, y, 24f, 0xFF99FFCC.toInt(), a * 0.25f)
            }
        }
    }

    // ── FUSION ────────────────────────────────────────────────────────────────

    private fun drawFusion(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Float) {
        drawCenterBloom(canvas, cx, cy, 220f + p * 280f, 0xFF33CCFF.toInt(), (0.22f * (1f - p * 0.4f)).coerceIn(0f, 0.3f))

        // Flash cyan initial
        if (p < 0.1f) {
            val fa = (p / 0.1f).let { if (it < 0.5f) it / 0.5f else 1f - (it - 0.5f) / 0.5f }
            paint.color = 0xFF00CCFF.toInt(); paint.alpha = (fa * 160).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, p / 0.1f * 400f, paint)
        }

        // Étoiles filantes avec traînée large
        paint.style = Paint.Style.STROKE
        for (part in particles) {
            val t = ((p - part.delay - 0.04f).coerceAtLeast(0f)).coerceAtMost(1f)
            if (t <= 0f) continue
            val et = 1f - (1f - t) * (1f - t)
            val dist = et * 720f * part.speed
            val x = cx + cos(Math.toRadians(part.angle.toDouble())).toFloat() * dist
            val y = cy + sin(Math.toRadians(part.angle.toDouble())).toFloat() * dist
            val trailLen = 120f * t * part.speed
            val tx = x - cos(Math.toRadians(part.angle.toDouble())).toFloat() * trailLen
            val ty = y - sin(Math.toRadians(part.angle.toDouble())).toFloat() * trailLen
            val a = if (t < 0.55f) 1f else 1f - (t - 0.55f) / 0.45f
            // Glow épais semi-transparent
            paint.color = part.color; paint.alpha = (a * 60).toInt()
            paint.strokeWidth = part.size * 2.5f
            canvas.drawLine(tx, ty, x, y, paint)
            // Trait fin brillant
            paint.alpha = (a * 230).toInt(); paint.strokeWidth = part.size * 0.6f
            canvas.drawLine(tx, ty, x, y, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, part.size * (1f - t * 0.75f), paint)
            paint.style = Paint.Style.STROKE
        }
        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f

        // Nébuleuse : cercles translucides concentriques
        if (p > 0.25f) {
            val phase = (p - 0.25f) / 0.75f
            for (ring in 0..4) {
                val rt = (phase - ring * 0.07f).coerceAtLeast(0f).coerceAtMost(1f)
                if (rt <= 0f) continue
                val rAlpha = (1f - rt) * 0.18f
                paint.color = listOf(
                    0xFF0044FF.toInt(), 0xFF00CCFF.toInt(), 0xFF8800FF.toInt(),
                    0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt()
                )[ring]
                paint.alpha = (rAlpha * 255).toInt()
                canvas.drawCircle(cx, cy, 100f + rt * 500f, paint)
            }
        }

        // Étoiles 4 branches aléatoires réparties sur l'écran
        if (p in 0.15f..0.95f) {
            for (i in 0 until 18) {
                val blink = sin((p * 18f + i * 1.9f).toDouble()).toFloat() * 0.5f + 0.5f
                val sx = cx + (i * 67f % w - w * 0.35f)
                val sy = cy + sin((i * 0.9f + p * 5f).toDouble()).toFloat() * h * 0.38f
                val starColor = listOf(0xFF00CCFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF4488FF.toInt())[i % 3]
                drawStar4(canvas, sx, sy, 14f * blink, p * 120f + i * 20f, starColor, (blink * 220).toInt())
            }
        }

        // Lueur finale (0.75→1.0)
        if (p > 0.75f) {
            val phase = (p - 0.75f) / 0.25f
            val ga = if (phase < 0.5f) phase / 0.5f else 1f - (phase - 0.5f) / 0.5f
            for (ring in 0..1) {
                paint.color = if (ring == 0) 0xFF00CCFF.toInt() else 0xFFFFFFFF.toInt()
                paint.alpha = (ga * (120 - ring * 50)).toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 24f - ring * 10f
                canvas.drawCircle(cx, cy, 260f + phase * 140f + ring * 40f, paint)
            }
            paint.style = Paint.Style.FILL; paint.strokeWidth = 1f
        }

        // micro-étoiles de fond (parallaxe légère)
        for (i in 0 until 22) {
            val sx = ((i * 97f + p * 160f) % (w + 180f)) - 90f
            val sy = ((i * 53f + p * 70f) % (h + 180f)) - 90f
            val blink = 0.3f + 0.7f * abs(sin((p * 14f + i * 0.8f).toDouble()).toFloat())
            paint.color = 0xFFCCEEFF.toInt()
            paint.alpha = (blink * 120).toInt()
            canvas.drawCircle(sx, sy, 1.2f + blink * 2.1f, paint)
        }
    }

    // ── SUPERNOVA ─────────────────────────────────────────────────────────────

    private fun drawSupernova(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Float) {
        drawCenterBloom(canvas, cx, cy, 190f + p * 260f, 0xFFFFAA33.toInt(), (0.28f * (1f - p * 0.35f)).coerceIn(0f, 0.35f))

        // Phase 1 (0→35%) : tremblement + fond rouge/or progressif
        if (p < 0.35f) {
            val intensity = (p / 0.35f)
            val shake = intensity * 12f
            canvas.translate((Random.nextFloat() - 0.5f) * shake, (Random.nextFloat() - 0.5f) * shake)
            paint.color = 0xFFCC4400.toInt()
            paint.alpha = (intensity * 80).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        // 4 anneaux dorés successifs
        val ringColors = listOf(0xFFFFCC00.toInt(), 0xFFFF8800.toInt(), 0xFFFFEE44.toInt(), 0xFFFFFFAA.toInt())
        for (ring in 0..3) {
            val rStart = 0.20f + ring * 0.10f
            if (p < rStart) continue
            val rt = ((p - rStart) / 0.45f).coerceAtMost(1f)
            paint.color = ringColors[ring]
            paint.alpha = ((1f - rt) * 240).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (1f - rt) * 22f + 2f
            canvas.drawCircle(cx, cy, rt * (280f + ring * 100f), paint)
        }
        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f

        // Feu d'artifice radial multiple (dès 0.45)
        if (p > 0.45f) {
            val phase = (p - 0.45f) / 0.55f
            drawRadialLines(canvas, cx, cy, 32, 50f, 340f + phase * 130f, 0xFFFFCC00.toInt(), ((1f - phase) * 220).toInt())
            drawRadialLines(canvas, cx, cy, 16, 30f, 200f + phase * 80f, 0xFFFF8800.toInt(), ((1f - phase) * 160).toInt())
        }

        // Particules dorées qui explosent
        paint.style = Paint.Style.FILL
        for (part in particles) {
            val t = ((p - part.delay * 0.4f - 0.22f).coerceAtLeast(0f)).coerceAtMost(1f)
            if (t <= 0f) continue
            val dist = t * 560f * part.speed
            val wob = sin((t * 7f + part.wobble * 0.04f).toDouble()).toFloat() * part.wobble * (1f - t) * 0.4f
            val x = cx + cos(Math.toRadians(part.angle.toDouble())).toFloat() * dist + wob
            val y = cy + sin(Math.toRadians(part.angle.toDouble())).toFloat() * dist
            val a = if (t < 0.45f) 1f else 1f - (t - 0.45f) / 0.55f
            paint.color = part.color; paint.alpha = (a * 255).toInt()
            // Mélange de cercles et petits losanges
            if (part.spin > 0f) {
                canvas.save(); canvas.rotate(part.spin * t * 0.5f, x, y)
                val s = part.size * (1f - t * 0.5f)
                path.reset()
                path.moveTo(x, y - s); path.lineTo(x + s * 0.6f, y)
                path.lineTo(x, y + s); path.lineTo(x - s * 0.6f, y); path.close()
                canvas.drawPath(path, paint); canvas.restore()
            } else {
                canvas.drawCircle(x, y, part.size * (1f - t * 0.55f), paint)
            }
        }

        // Étoiles 4 branches dorées orbitales
        if (p > 0.5f) {
            val phase = (p - 0.5f) / 0.5f
            for (i in 0 until 12) {
                val angle = i * 30f + phase * 90f
                val r = 200f + sin((phase * 5f + i).toDouble()).toFloat() * 80f
                val sx = cx + cos(Math.toRadians(angle.toDouble())).toFloat() * r
                val sy = cy + sin(Math.toRadians(angle.toDouble())).toFloat() * r
                val blink = abs(sin((p * 25f + i * 1.4f).toDouble()).toFloat())
                drawStar4(canvas, sx, sy, 18f * blink, angle + 45f, 0xFFFFCC00.toInt(), (blink * 255).toInt())
            }
        }

        // pluie d'étincelles ambre
        if (p > 0.3f) {
            val emberPhase = (p - 0.3f) / 0.7f
            for (i in 0 until 20) {
                val ex = (i * 61f + emberPhase * 210f) % (w + 120f) - 60f
                val ey = ((i * 41f + emberPhase * 640f) % (h + 140f)) - 70f
                val life = 1f - ((emberPhase * 1.7f + i * 0.05f) % 1f)
                paint.color = if (i % 3 == 0) 0xFFFFFFAA.toInt() else 0xFFFFAA33.toInt()
                paint.alpha = (life * 150).toInt()
                canvas.drawCircle(ex, ey, 1.5f + life * 3.2f, paint)
            }
        }
    }

    // ── NEUTRONIQUE ───────────────────────────────────────────────────────────

    private fun drawNeutronique(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Float) {
        drawCenterBloom(canvas, cx, cy, 210f + p * 320f, 0xFFAA33FF.toInt(), (0.26f * (1f - p * 0.25f)).coerceIn(0f, 0.35f))

        // Phase 1 (0→25%) : 1 éclair aléatoire + halo violet
        if (p in 0f..0.25f) {
            val intensity = p / 0.25f
            drawBigLightning(canvas, lightningSeeds[0], intensity, 0xFF9944FF.toInt())
            paint.color = 0xFF8800FF.toInt()
            paint.alpha = (intensity * 0.4f * 255).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, intensity * 250f, paint)
        }

        // Phase 2 (25→50%) : 2 éclairs de bords différents
        if (p in 0.25f..0.50f) {
            val intensity = (p - 0.25f) / 0.25f
            drawBigLightning(canvas, lightningSeeds[1], intensity, 0xFFCC22FF.toInt())
            drawBigLightning(canvas, lightningSeeds[2], intensity, 0xFF8844FF.toInt())
            // Vibration
            canvas.translate((Random.nextFloat() - 0.5f) * intensity * 8f, (Random.nextFloat() - 0.5f) * intensity * 8f)
        }

        // Phase 3 (50→72%) : 4 éclairs massifs depuis 4 côtés + flash plein écran
        if (p in 0.50f..0.72f) {
            val intensity = (p - 0.50f) / 0.22f
            canvas.translate((Random.nextFloat() - 0.5f) * 14f, (Random.nextFloat() - 0.5f) * 14f)
            drawBigLightning(canvas, lightningSeeds[3], intensity * 1.5f, 0xFFFF00FF.toInt())
            drawBigLightning(canvas, lightningSeeds[4], intensity * 1.3f, 0xFFCC00FF.toInt())
            drawBigLightning(canvas, lightningSeeds[5], intensity * 1.4f, 0xFF8800FF.toInt())
            drawBigLightning(canvas, lightningSeeds[6], intensity * 1.2f, 0xFFFF44FF.toInt())
            // Anneaux violets qui pulsent
            for (ring in 0..2) {
                val rt = ((intensity - ring * 0.2f).coerceAtLeast(0f)).coerceAtMost(1f)
                if (rt <= 0f) continue
                paint.color = 0xFFAA00FF.toInt()
                paint.alpha = ((1f - rt) * 180).toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (1f - rt) * 20f
                canvas.drawCircle(cx, cy, rt * 450f, paint)
            }
            val fa = if (intensity < 0.5f) intensity / 0.5f else 1f - (intensity - 0.5f) / 0.5f
            paint.color = 0xFFFFFFFF.toInt(); paint.alpha = (fa * 230).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f

        // Phase 4 (72→100%) : cristaux multicolores + feu d'artifice arc-en-ciel
        if (p > 0.72f) {
            val phase = (p - 0.72f) / 0.28f
            // Lignes radiales arc-en-ciel
            drawRadialLines(canvas, cx, cy, 36, 40f, 360f + phase * 160f, 0xFFFF55FF.toInt(), ((1f - phase) * 200).toInt())
            drawRadialLines(canvas, cx, cy, 18, 20f, 200f + phase * 80f, 0xFF00FFFF.toInt(), ((1f - phase) * 150).toInt())

            for (part in particles) {
                val t = ((p - 0.72f - part.delay * 0.25f).coerceAtLeast(0f) / 0.28f).coerceAtMost(1f)
                if (t <= 0f) continue
                val dist = t * 530f * part.speed
                val wob = sin((t * 8f + part.wobble * 0.05f).toDouble()).toFloat() * part.wobble * (1f - t) * 0.5f
                val x = cx + cos(Math.toRadians(part.angle.toDouble())).toFloat() * dist + wob
                val y = cy + sin(Math.toRadians(part.angle.toDouble())).toFloat() * dist
                val a = if (t < 0.55f) 1f else 1f - (t - 0.55f) / 0.45f
                paint.color = part.color; paint.alpha = (a * 255).toInt()
                canvas.save(); canvas.rotate(part.spin * t * 2.5f, x, y)
                val s = part.size * (1f - t * 0.4f)
                path.reset()
                path.moveTo(x, y - s * 1.6f); path.lineTo(x + s * 0.75f, y)
                path.lineTo(x, y + s * 1.6f); path.lineTo(x - s * 0.75f, y); path.close()
                canvas.drawPath(path, paint); canvas.restore()
            }
            // Étoiles multicolores
            for (i in 0 until 16) {
                val blink = abs(sin((p * 20f + i * 1.8f).toDouble()).toFloat())
                val sx = cx + cos(Math.toRadians((i * 22.5).toDouble())).toFloat() * (220f + blink * 80f)
                val sy = cy + sin(Math.toRadians((i * 22.5).toDouble())).toFloat() * (220f + blink * 80f)
                val sc = listOf(0xFFFF55FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFF00.toInt(), 0xFFFF4444.toInt())[i % 4]
                drawStar4(canvas, sx, sy, 20f * blink, p * 180f + i * 22.5f, sc, (blink * 255).toInt())
            }
        }

        if (p > 0.45f) {
            val chroma = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
            drawEdgeAura(canvas, w, h, 0xFFFF44FF.toInt(), chroma * 0.16f)
        }
    }

    // ── SYNTHÉTIQUE ───────────────────────────────────────────────────────────

    private fun drawSynthetique(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Float) {
        drawCenterBloom(canvas, cx, cy, 260f + p * 420f, 0xFFFF3355.toInt(), (0.24f * (1f - p * 0.2f)).coerceIn(0f, 0.34f))

        // Phase 1 (0→18%) : fond rouge progressif + premier flash
        val bgAlpha = (p / 0.18f).coerceAtMost(1f)
        paint.color = 0xFFAA0000.toInt(); paint.alpha = (bgAlpha * 130).toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)

        // Phase 2 (18→48%) : 6 éclairs depuis tous les bords avec vibration croissante
        if (p in 0.18f..0.48f) {
            val phase = (p - 0.18f) / 0.30f
            val shake = phase * 18f
            canvas.translate((Random.nextFloat() - 0.5f) * shake, (Random.nextFloat() - 0.5f) * shake)
            val count = (phase * 6f).toInt().coerceAtMost(6)
            for (i in 0..count) {
                val lPhase = ((phase * 6f) - i).coerceIn(0f, 1f)
                drawBigLightning(canvas, lightningSeeds[i], lPhase * 1.5f, lightningSeeds[i].color)
            }
            // Flashes pulsés
            val flashA = (sin(phase * Math.PI * 7).toFloat().coerceAtLeast(0f)) * 0.55f
            paint.color = 0xFFFF2200.toInt(); paint.alpha = (flashA * 160).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        // Phase 3 (48→62%) : flash blanc total aveuglant
        if (p in 0.48f..0.62f) {
            val phase = (p - 0.48f) / 0.14f
            val fa = if (phase < 0.45f) phase / 0.45f else 1f - (phase - 0.45f) / 0.55f
            paint.color = Color.WHITE; paint.alpha = (fa * 255).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        // Phase 4 (62→82%) : supernova — 5 ondes concentriques colorées
        if (p in 0.62f..0.82f) {
            val phase = (p - 0.62f) / 0.20f
            val waveColors = listOf(
                0xFFFF3333.toInt(), 0xFFFF6600.toInt(), 0xFFFFCC00.toInt(),
                0xFFCC00FF.toInt(), 0xFF00FFFF.toInt()
            )
            for ((i, wc) in waveColors.withIndex()) {
                val wt = (phase - i * 0.08f).coerceAtLeast(0f).coerceAtMost(1f)
                if (wt <= 0f) continue
                paint.color = wc; paint.alpha = ((1f - wt) * 240).toInt()
                paint.style = Paint.Style.STROKE; paint.strokeWidth = (1f - wt) * 32f + 4f
                canvas.drawCircle(cx, cy, wt * 1000f, paint)
            }
        }

        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f

        // Phase 5 (82→100%) : particules cosmiques + radiales multicolores
        if (p > 0.82f) {
            val phase = (p - 0.82f) / 0.18f
            drawRadialLines(canvas, cx, cy, 48, 30f, 400f + phase * 180f, 0xFFFF3333.toInt(), ((1f - phase) * 200).toInt())
            drawRadialLines(canvas, cx, cy, 24, 20f, 250f + phase * 100f, 0xFFCC00FF.toInt(), ((1f - phase) * 160).toInt())
            drawRadialLines(canvas, cx, cy, 12, 10f, 150f + phase * 60f, 0xFFFFCC00.toInt(), ((1f - phase) * 120).toInt())

            for (part in particles) {
                val t = ((p - 0.82f - part.delay * 0.12f).coerceAtLeast(0f) / 0.18f).coerceAtMost(1f)
                if (t <= 0f) continue
                val dist = t * 820f * part.speed
                val wob = sin((t * 8f + part.wobble * 0.04f).toDouble()).toFloat() * part.wobble * (1f - t) * 0.6f
                val x = cx + cos(Math.toRadians(part.angle.toDouble())).toFloat() * dist + wob
                val y = cy + sin(Math.toRadians(part.angle.toDouble())).toFloat() * dist
                val a = if (t < 0.45f) t / 0.45f else 1f - (t - 0.45f) / 0.55f
                paint.color = part.color; paint.alpha = (a * 255).toInt()
                canvas.drawCircle(x, y, part.size * (1f - t * 0.45f), paint)
            }

            // Étoiles cosmiques tournantes
            for (i in 0 until 20) {
                val blink = abs(sin((p * 22f + i * 1.6f).toDouble()).toFloat())
                val angle = i * 18f + phase * 120f
                val r = 180f + blink * 130f + i * 8f
                val sx = cx + cos(Math.toRadians(angle.toDouble())).toFloat() * r
                val sy = cy + sin(Math.toRadians(angle.toDouble())).toFloat() * r
                val sc = RAINBOW[i % RAINBOW.size]
                drawStar4(canvas, sx, sy, 22f * blink, angle, sc, (blink * 255).toInt())
            }
        }

        if (p > 0.6f) {
            val aura = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
            drawEdgeAura(canvas, w, h, 0xFFFF2200.toInt(), aura * 0.2f)
        }
    }

    // ── Fonctions utilitaires canvas ──────────────────────────────────────────

    // Éclair massif depuis un point jusqu'à une destination, path pré-calculé
    private fun drawBigLightning(canvas: Canvas, ls: LightningPath, intensity: Float, color: Int) {
        if (intensity <= 0f) return
        val segments = 10
        path.reset()
        path.moveTo(ls.originX, ls.originY)
        for (i in 1..segments) {
            val t2 = i.toFloat() / segments
            val x = ls.originX + (ls.destX - ls.originX) * t2
            val y = ls.originY + (ls.destY - ls.originY) * t2
            val perpX = -(ls.destY - ls.originY) / sqrt(
                (ls.destX - ls.originX) * (ls.destX - ls.originX) +
                (ls.destY - ls.originY) * (ls.destY - ls.originY)
            )
            val perpY = (ls.destX - ls.originX) / sqrt(
                (ls.destX - ls.originX) * (ls.destX - ls.originX) +
                (ls.destY - ls.originY) * (ls.destY - ls.originY)
            )
            val spread = if (i < segments) ls.offsets[i % ls.offsets.size] * 120f * intensity else 0f
            path.lineTo(x + perpX * spread, y + perpY * spread)
        }
        // Glow très large
        paint.color = color; paint.alpha = (intensity * 70).toInt()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = intensity * 40f
        canvas.drawPath(path, paint)
        // Glow intermédiaire
        paint.alpha = (intensity * 120).toInt(); paint.strokeWidth = intensity * 16f
        canvas.drawPath(path, paint)
        // Cœur brillant
        paint.color = Color.WHITE; paint.alpha = (intensity * 230).toInt()
        paint.strokeWidth = intensity * 5f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f

        // Point d'impact
        paint.color = color; paint.alpha = (intensity * 200).toInt()
        canvas.drawCircle(ls.destX, ls.destY, intensity * 25f, paint)
        paint.color = Color.WHITE; paint.alpha = (intensity * 255).toInt()
        canvas.drawCircle(ls.destX, ls.destY, intensity * 10f, paint)
    }

    private fun drawStar4(canvas: Canvas, cx: Float, cy: Float, size: Float, rotation: Float, color: Int, alpha: Int) {
        if (size <= 0f || alpha <= 0) return
        paint.color = color; paint.alpha = alpha
        paint.style = Paint.Style.STROKE; paint.strokeWidth = (size * 0.3f).coerceAtLeast(1.5f)
        canvas.save(); canvas.rotate(rotation, cx, cy)
        canvas.drawLine(cx - size, cy, cx + size, cy, paint)
        canvas.drawLine(cx, cy - size, cx, cy + size, paint)
        val d = size * 0.55f
        paint.strokeWidth = (size * 0.18f).coerceAtLeast(1f)
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
        canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f
    }

    private fun drawRadialLines(canvas: Canvas, cx: Float, cy: Float, count: Int, innerR: Float, outerR: Float, color: Int, alpha: Int) {
        if (alpha <= 0) return
        paint.color = color; paint.alpha = alpha
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        val step = 360f / count
        for (i in 0 until count) {
            val angle = Math.toRadians((i * step).toDouble())
            val c = cos(angle).toFloat(); val s = sin(angle).toFloat()
            canvas.drawLine(cx + c * innerR, cy + s * innerR, cx + c * outerR, cy + s * outerR, paint)
        }
        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f
    }

    private fun drawCategoryAura(canvas: Canvas, cx: Float, cy: Float, progress: Float, totalDurationMs: Long) {
        if (categoryColor == 0xFFFFFFFF.toInt()) return

        val rarityColor = rarity.color
        val globalPulse = abs(sin(progress * Math.PI * 3f).toFloat())
        val ringSpeed = (totalDurationMs / 1000f).coerceAtLeast(1f)
        val ringMix = easeInOut((progress - 0.2f) / 0.7f)
        val mixedAuraColor = blendColor(categoryColor, rarityColor, ringMix * 0.65f)
        val coreRadius = 48f + progress * 28f + globalPulse * 8f
        drawCenterBloom(
            canvas = canvas,
            cx = cx,
            cy = cy,
            radius = 150f + progress * 240f,
            color = mixedAuraColor,
            intensity = (0.08f + globalPulse * 0.08f) * (1f - progress * 0.35f)
        )

        // Anneaux simples existants (renforcés)
        paint.style = Paint.Style.STROKE
        paint.color = mixedAuraColor

        val popIntervalMs = 500L
        val numCircles = ((totalDurationMs + popIntervalMs - 1) / popIntervalMs).toInt()

        for (idx in 0 until numCircles) {
            val popTimeMs = idx * popIntervalMs
            val popTimeNorm = popTimeMs.toFloat() / totalDurationMs

            if (progress < popTimeNorm) continue

            val timeSincePop = progress - popTimeNorm
            val timeRemainingNorm = 1f - popTimeNorm

            // Le cercle continue à s'étendre pendant toute la durée restante
            val radius = 50f + timeSincePop * timeRemainingNorm * 1200f

            // Opacité décroît progressivement
            val alpha = ((1f - timeSincePop) * 255).toInt().coerceIn(0, 255)

            // Trait épais
            paint.strokeWidth = 16f + globalPulse * 8f
            paint.alpha = alpha
            canvas.drawCircle(cx, cy, radius, paint)
        }

        // Cercles de groupe plus "vivants" : segments tournants + pulse
        for (ring in 0..2) {
            val ringProgress = ((progress * (1.2f + ring * 0.28f)) % 1f)
            val ringRadius = coreRadius + 90f + ring * 70f + ringProgress * 220f
            val ringAlpha = ((1f - ringProgress) * 120f + globalPulse * 70f).toInt().coerceIn(0, 220)
            paint.color = blendColor(mixedAuraColor, categoryColor, ring * 0.2f)
            paint.alpha = ringAlpha
            paint.strokeWidth = 6f + ring * 2f
            canvas.drawCircle(cx, cy, ringRadius, paint)

            val arcSweep = 36f + ring * 10f
            val arcStep = 60f - ring * 8f
            val rotation = progress * (140f + ring * 65f) * ringSpeed
            var angle = rotation
            while (angle < rotation + 360f) {
                val sr = android.graphics.RectF(
                    cx - ringRadius,
                    cy - ringRadius,
                    cx + ringRadius,
                    cy + ringRadius
                )
                canvas.drawArc(sr, angle, arcSweep, false, paint)
                angle += arcStep
            }
        }

        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawGravityFallout(canvas: Canvas, w: Float, h: Float, mainProgress: Float, postEndSeconds: Float) {
        val preFall = ((mainProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val postFall = if (falloutActive) (postEndSeconds * 0.14f).coerceIn(0f, 1f) else 0f
        val alphaGlobal = ((preFall * 0.7f) + (postFall * 0.9f)).coerceIn(0f, 1f)
        if (alphaGlobal <= 0f) return

        val finalRarityCategory = blendColor(rarity.color, categoryColor, 0.62f)
        paint.style = Paint.Style.FILL

        for (fp in falloutParticles) {
            val life = preFall + postEndSeconds * fp.fallSpeed * 0.18f - fp.delay * 0.65f
            if (life <= 0f) continue

            val cycle = life % 1.35f
            val dropT = (cycle / 1.35f).coerceIn(0f, 1f)
            val gravityT = dropT * dropT
            val xBase = (fp.xNorm + fp.laneShift * sin((postEndSeconds * 0.85f + fp.twinkle).toDouble()).toFloat()) * w
            val x = xBase + sin((dropT * 8.6f + fp.twinkle).toDouble()).toFloat() * fp.sway
            val y = -h * 0.2f + gravityT * (h * 1.45f)
            if (y < -40f || y > h + 60f) continue

            val twinkle = (0.35f + 0.65f * abs(sin((postEndSeconds * 5.5f + fp.twinkle).toDouble()).toFloat()))
            val alpha = (alphaGlobal * (1f - dropT * 0.45f) * twinkle * 220f).toInt().coerceIn(0, 255)
            val color = blendColor(finalRarityCategory, Color.WHITE, fp.tintMix * twinkle * 0.45f)
            paint.color = color
            paint.alpha = alpha
            canvas.drawCircle(x, y, fp.size * (0.75f + (1f - dropT) * 0.45f), paint)
        }
    }

    private fun drawCenterBloom(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, intensity: Float) {
        if (radius <= 0f || intensity <= 0f) return
        val alpha = (255f * intensity.coerceIn(0f, 1f)).toInt()
        paint2.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(withAlpha(color, alpha), withAlpha(color, (alpha * 0.42f).toInt()), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint2.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, paint2)
        paint2.shader = null
    }

    private fun drawEdgeAura(canvas: Canvas, w: Float, h: Float, color: Int, intensity: Float) {
        if (intensity <= 0f) return
        val edge = maxOf(w, h) * 0.75f
        val cx = w / 2f
        val cy = h / 2f
        paint2.shader = RadialGradient(
            cx, cy, edge,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(color, (255f * intensity).toInt())),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        paint2.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint2)
        paint2.shader = null
    }

    private fun drawCinematicVignette(canvas: Canvas, w: Float, h: Float, globalProgress: Float) {
        val intensity = (0.08f + abs(sin((globalProgress * Math.PI).toFloat())) * 0.08f).coerceIn(0.06f, 0.18f)
        drawEdgeAura(canvas, w, h, Color.BLACK, intensity)
    }

    private fun drawBackgroundStars(canvas: Canvas, w: Float, h: Float) {
        for (s in stars) {
            starPaint.alpha = (120 + s[2].toInt() * 40).coerceIn(60, 220)
            starPaint.color = Color.WHITE
            canvas.drawCircle(s[0] * w, s[1] * h, s[2], starPaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
    }

    private fun blendColor(from: Int, to: Int, t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * c).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * c).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * c).toInt()
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * c).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun easeInOut(v: Float): Float {
        val x = v.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
