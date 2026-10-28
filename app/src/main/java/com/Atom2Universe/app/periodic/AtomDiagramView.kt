package com.Atom2Universe.app.periodic

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.*
import androidx.core.graphics.toColorInt
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.science.SciencePalette

class AtomDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val palette = SciencePalette(context)

    var shells: List<Int> = emptyList()
        set(value) { field = value; recomputeGeometry(); invalidate() }
    var atomicNumber: Int = 0
        set(value) { field = value; nucleusDirty = true; staticLayer = null; invalidate() }
    var neutronCount: Int = 0
        set(value) { field = value; nucleusDirty = true; staticLayer = null; invalidate() }

    // Le noyau est immobile : mélange et spirale calculés une fois, pas à chaque image.
    private var nucleusDirty = true
    private var nucleusIsProton = BooleanArray(0)
    private var nucleusUnitX = FloatArray(0)
    private var nucleusUnitY = FloatArray(0)

    private var animating = false
    private var startTime = 0L

    // Lueur, anneaux et noyau ne bougent pas : peints une fois par taille dans une image.
    private var staticLayer: Bitmap? = null
    private var staticFailed = false

    // Les électrons avancent à 30 images par seconde, et seulement quand la vue se voit.
    private var ticking = false
    private val visibleRect = Rect()
    private val tick = Runnable {
        ticking = false
        if (animating && isOnScreen()) {
            invalidate()
            scheduleFrame()
        }
    }
    // Le défilement qui ramène l'atome à l'écran relance les électrons.
    private val onScroll = ViewTreeObserver.OnScrollChangedListener { scheduleFrame() }

    // Géométrie précalculée
    private var cx = 0f
    private var cy = 0f
    private var nucleusR = 0f
    private var shellRadii = floatArrayOf()

    // ── Paints noyau ──────────────────────────────────────────────────────
    private val nucleusBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nucleusRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        color = ColorUtils.setAlphaComponent(palette.mark("#FF9646".toColorInt()), 140)
    }
    private val protonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.mark("#FF7733".toColorInt())
        style = Paint.Style.FILL
    }
    private val neutronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (palette.isLight) palette.secondary else "#DDE0EE".toColorInt()
        style = Paint.Style.FILL
    }
    private val particleHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // ── Paints couches / électrons ────────────────────────────────────────
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(palette.secondary, if (palette.isLight) 120 else 70)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(6f, 5f), 0f)
    }
    private val electronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.mark("#78D8FF".toColorInt())
        style = Paint.Style.FILL
    }
    private val electronGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(electronPaint.color, 45)
        style = Paint.Style.FILL
    }
    private val bgGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val squareSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        super.onMeasure(squareSpec, squareSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeGeometry()
    }

    private fun recomputeGeometry() {
        staticLayer = null; staticFailed = false
        if (width == 0 || height == 0 || shells.isEmpty()) return
        cx = width / 2f
        cy = height / 2f
        val maxR = minOf(cx, cy) - dp(14)
        nucleusR = (maxR * 0.18f).coerceAtLeast(dp(22).toFloat())
        val numShells = shells.size
        val shellStep = (maxR - nucleusR) / numShells
        shellRadii = FloatArray(numShells) { i -> nucleusR + shellStep * (i + 1) }

        nucleusBgPaint.shader = RadialGradient(
            cx, cy, nucleusR,
            if (palette.isLight) intArrayOf(palette.surface, palette.raised)
            else intArrayOf("#2C1A0C".toColorInt(), "#0C0808".toColorInt()),
            floatArrayOf(0.1f, 1f),
            Shader.TileMode.CLAMP
        )
        bgGlowPaint.shader = RadialGradient(
            cx, cy, maxR * 1.1f,
            intArrayOf(Color.argb(28, 100, 160, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    fun startAnimation() {
        if (animating) return
        animating = true
        startTime = SystemClock.elapsedRealtime()
        invalidate()
        scheduleFrame()
    }

    fun stopAnimation() {
        animating = false
        removeCallbacks(tick); ticking = false
        // Les électrons reviennent à leur place de repos.
        invalidate()
    }

    private fun isOnScreen() = windowVisibility == VISIBLE && isShown && getLocalVisibleRect(visibleRect)

    private fun scheduleFrame() {
        if (!animating || ticking || !isAttachedToWindow || !isOnScreen()) return
        ticking = true
        postOnAnimationDelayed(tick, FRAME_MS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(onScroll)
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnScrollChangedListener(onScroll)
        removeCallbacks(tick); ticking = false
        animating = false
        staticLayer = null
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        scheduleFrame()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        scheduleFrame()
    }

    override fun onDraw(canvas: Canvas) {
        if (shells.isEmpty() || shellRadii.isEmpty()) return

        val elapsed = if (animating) (SystemClock.elapsedRealtime() - startTime) / 1000f else 0f
        val numShells = shells.size

        val layer = staticLayer ?: bakeStaticLayer()
        if (layer != null) canvas.drawBitmap(layer, 0f, 0f, null) else paintStatic(canvas)

        // Électrons orbitaux
        val eR = dp(2.5f)
        val eGlowR = dp(5.5f)
        for (i in 0 until numShells) {
            val count = shells[i]
            if (count == 0) continue
            val shellR = shellRadii[i]
            val depthFactor = (numShells - i).toFloat() / numShells
            val speed = 0.5f + depthFactor * 1.8f
            val direction = if (i % 2 == 0) 1f else -1f
            val baseAngle = elapsed * speed * direction
            val angleStep = (2.0 * PI / count).toFloat()
            for (j in 0 until count) {
                val angle = (baseAngle + angleStep * j).toDouble()
                val ex = cx + shellR * cos(angle).toFloat()
                val ey = cy + shellR * sin(angle).toFloat()
                canvas.drawCircle(ex, ey, eGlowR, electronGlowPaint)
                canvas.drawCircle(ex, ey, eR, electronPaint)
            }
        }
    }

    /** L'image fixe à cette taille ; sans mémoire pour elle, la partie fixe se peint à chaque image. */
    private fun bakeStaticLayer(): Bitmap? {
        if (width <= 0 || height <= 0 || staticFailed) return null
        val bmp = try { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            catch (e: OutOfMemoryError) { staticFailed = true; return null }
        paintStatic(Canvas(bmp))
        staticLayer = bmp
        return bmp
    }

    /** Lueur, anneaux, noyau et son liseré. */
    private fun paintStatic(c: Canvas) {
        // Lueur de fond
        c.drawCircle(cx, cy, minOf(cx, cy), bgGlowPaint)
        // Anneaux des couches électroniques
        for (r in shellRadii) c.drawCircle(cx, cy, r, shellPaint)
        // Noyau — fond adapté à la palette
        c.drawCircle(cx, cy, nucleusR, nucleusBgPaint)
        // Protons + neutrons en spirale de Fibonacci
        drawNucleus(c)
        // Rim du noyau (par-dessus les particules)
        c.drawCircle(cx, cy, nucleusR, nucleusRimPaint)
    }

    private fun rebuildNucleus() {
        nucleusDirty = false
        val total = (atomicNumber + neutronCount).coerceAtLeast(0)

        // Mélange uniforme déterministe : même élément → même motif
        val isProton = BooleanArray(total) { it < atomicNumber }
        val rng = java.util.Random(atomicNumber.toLong())
        for (i in total - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = isProton[i]; isProton[i] = isProton[j]; isProton[j] = tmp
        }

        // Spirale de Fibonacci dans le disque unité, mise à l'échelle au dessin.
        val goldenAngle = 2.3999632f
        val unitX = FloatArray(total)
        val unitY = FloatArray(total)
        for (i in 0 until total) {
            val r = sqrt((i + 0.5f) / total)
            val angle = (i * goldenAngle).toDouble()
            unitX[i] = r * cos(angle).toFloat()
            unitY[i] = r * sin(angle).toFloat()
        }
        nucleusIsProton = isProton
        nucleusUnitX = unitX
        nucleusUnitY = unitY
    }

    private fun drawNucleus(canvas: Canvas) {
        if (nucleusDirty) rebuildNucleus()
        val total = nucleusIsProton.size
        if (total == 0) return

        val dotR = (nucleusR * sqrt(0.60f / total))
            .coerceIn(dp(1.5f), nucleusR * 0.40f)
        val placementR = nucleusR * 0.82f
        val isProton = nucleusIsProton

        for (i in 0 until total) {
            val px = cx + nucleusUnitX[i] * placementR
            val py = cy + nucleusUnitY[i] * placementR

            canvas.drawCircle(px, py, dotR, if (isProton[i]) protonPaint else neutronPaint)

            if (dotR > dp(2f)) {
                canvas.drawCircle(
                    px - dotR * 0.27f,
                    py - dotR * 0.30f,
                    dotR * 0.30f,
                    particleHighlightPaint
                )
            }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    private companion object {
        /** Un peu moins qu'un trentième de seconde : l'image suivante de l'écran tombe à 1/30 s. */
        const val FRAME_MS = 29L
    }
}
