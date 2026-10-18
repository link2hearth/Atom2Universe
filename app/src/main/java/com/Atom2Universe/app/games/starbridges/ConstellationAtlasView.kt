package com.Atom2Universe.app.games.starbridges

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.Atom2Universe.app.R
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * L'atlas céleste : toutes les constellations du joueur, chacune à sa place dans un même
 * ciel. On le fait glisser d'un doigt, on le zoome à deux, on touche une figure pour lire
 * son nom et quand elle a été tracée.
 *
 * Le monde est en **unités de grille** : une case du plateau vaut une unité, et c'est
 * [unitPx] qui dit combien de pixels elle couvre à l'écran.
 */
class ConstellationAtlasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private var entries: List<AtlasEntry> = emptyList()
    private var names: List<String> = emptyList()
    private var selected: AtlasEntry? = null
    private var sky: Bitmap? = null

    private var unitPx = 30f * dp
    private var camX = 0f
    private var camY = 0f
    private var fitted = false

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 12f * dp; color = 0xFFD8D2F0.toInt()
        typeface = Typeface.create("serif", Typeface.ITALIC)
    }
    private val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 20f * dp; color = NightSky.LINE
        typeface = Typeface.create("serif", Typeface.ITALIC)
    }
    private val pDetail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 12f * dp; color = 0xFF9A9CC0.toInt()
    }
    private val emptyText = context.getString(R.string.starbridges_atlas_empty)

    private val point = FloatArray(2)
    private var skyPos: List<FloatArray> = emptyList()

    // Tampons du dessin lointain : tous les traits en un appel, les étoiles en un appel par couleur.
    private var lineBuf = FloatArray(4096)
    private val dotBufs = Array(8) { FloatArray(1024) }
    private val dotCounts = IntArray(8)
    private val pDots = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    // Positions écran des étoiles d'une figure : 8 × 8 cases au plus.
    private val sx = FloatArray(64)
    private val sy = FloatArray(64)

    fun setEntries(list: List<AtlasEntry>) {
        entries = list
        names = list.map { ConstellationNames.name(context, it.noun, it.adjective) }
        // Les étoiles ne bougent jamais dans le ciel : leur place se calcule une fois.
        skyPos = list.map { e -> FloatArray(e.starCount * 2).also { out ->
            for (i in 0 until e.starCount) { starSky(e, i, point); out[i * 2] = point[0]; out[i * 2 + 1] = point[1] }
        } }
        fitted = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sky?.recycle()
        sky = if (w > 0 && h > 0) NightSky.bake(w, h, 3141, dp) else null
        fitted = false
    }

    /** Cadre l'ensemble du ciel à l'ouverture, sans jamais trop grossir une figure seule. */
    private fun fit() {
        fitted = true
        if (entries.isEmpty() || width == 0) return
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (e in entries) {
            minX = min(minX, e.skyX - e.radius); maxX = max(maxX, e.skyX + e.radius)
            minY = min(minY, e.skyY - e.radius); maxY = max(maxY, e.skyY + e.radius)
        }
        camX = (minX + maxX) / 2f; camY = (minY + maxY) / 2f
        unitPx = min(width / (maxX - minX + 2f), height / (maxY - minY + 2f))
            .coerceIn(minUnit(), maxUnit())
    }

    private fun minUnit() = 4f * dp
    private fun maxUnit() = min(width, height) / 7f

    private fun toScreenX(x: Float) = width / 2f + (x - camX) * unitPx
    private fun toScreenY(y: Float) = height / 2f + (y - camY) * unitPx

    /** Position dans le ciel d'une étoile de la figure (grille → inclinaison → place). */
    private fun starSky(e: AtlasEntry, i: Int, out: FloatArray) {
        val half = (e.size - 1) / 2f
        val lx = e.stars[i * 3] - half
        val ly = e.stars[i * 3 + 1] - half
        val a = Math.toRadians(e.rotation.toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        out[0] = e.skyX + lx * c - ly * s
        out[1] = e.skyY + lx * s + ly * c
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        sky?.let { canvas.drawBitmap(it, 0f, 0f, null) } ?: canvas.drawColor(0xFF060A1C.toInt())
        if (entries.isEmpty()) {
            pName.textSize = 15f * dp
            canvas.drawText(emptyText, width / 2f, height / 2f, pName)
            pName.textSize = 12f * dp
            return
        }
        if (!fitted) fit()
        drawFieldStars(canvas)

        val far = unitPx < 10f * dp
        val starScale = (unitPx / (24f * dp)).coerceIn(0.35f, 1.6f)
        var lineCount = 0
        dotCounts.fill(0)
        for ((index, e) in entries.withIndex()) {
            val isSelected = e === selected
            // Figure hors de l'écran : rien à dessiner.
            val r = e.radius * unitPx
            val ex = toScreenX(e.skyX); val ey = toScreenY(e.skyY)
            if (ex + r < 0 || ex - r > width || ey + r < 0 || ey - r > height) continue
            val pos = skyPos[index]
            for (i in 0 until e.starCount) {
                sx[i] = toScreenX(pos[i * 2]); sy[i] = toScreenY(pos[i * 2 + 1])
            }

            if (far && !isSelected) {
                // De loin, une figure n'est plus que des traits et des points : on les
                // entasse dans des tampons, dessinés d'un seul coup après la boucle. Mille
                // constellations à l'écran, c'est alors une dizaine d'appels, pas trente mille.
                val need = lineCount + e.links.size * 2
                if (need > lineBuf.size) lineBuf = lineBuf.copyOf(need * 2)
                for (k in 0 until e.links.size / 2) {
                    val a = e.links[k * 2]; val b = e.links[k * 2 + 1]
                    lineBuf[lineCount++] = sx[a]; lineBuf[lineCount++] = sy[a]
                    lineBuf[lineCount++] = sx[b]; lineBuf[lineCount++] = sy[b]
                }
                for (i in 0 until e.starCount) {
                    val c = (e.stars[i * 3 + 2] - 1).coerceIn(0, 7)
                    var buf = dotBufs[c]
                    if (dotCounts[c] + 2 > buf.size) { buf = buf.copyOf(buf.size * 2); dotBufs[c] = buf }
                    buf[dotCounts[c]++] = sx[i]; buf[dotCounts[c]++] = sy[i]
                }
                continue
            }

            pLine.strokeWidth = (if (isSelected) 2f else 1.2f) * dp
            pLine.color = (NightSky.LINE and 0xFFFFFF) or ((if (isSelected) 255 else 170) shl 24)
            for (k in 0 until e.links.size / 2) {
                val a = e.links[k * 2]; val b = e.links[k * 2 + 1]
                canvas.drawLine(sx[a], sy[a], sx[b], sy[b], pLine)
            }
            for (i in 0 until e.starCount) {
                val links = e.stars[i * 3 + 2]
                val core = (1.4f + links * 0.35f) * dp * starScale
                pGlow.shader = NightSky.glowShader(links)
                canvas.save(); canvas.translate(sx[i], sy[i]); canvas.scale(core * 3.5f, core * 3.5f)
                canvas.drawCircle(0f, 0f, 1f, pGlow)
                canvas.restore()
                pFill.color = NightSky.starColor(links)
                canvas.drawCircle(sx[i], sy[i], core, pFill)
            }
            // Le nom, sous la figure, dès qu'on est assez près pour le lire.
            val nameAlpha = ((unitPx - 8f * dp) / (10f * dp)).coerceIn(0f, 1f)
            if (nameAlpha > 0f || isSelected) {
                pName.alpha = if (isSelected) 255 else (nameAlpha * 200).toInt()
                canvas.drawText(names[index], ex, ey + r + 14f * dp, pName)
            }
        }
        if (lineCount > 0) {
            pLine.strokeWidth = 1f * dp
            pLine.color = (NightSky.LINE and 0xFFFFFF) or (140 shl 24)
            canvas.drawLines(lineBuf, 0, lineCount, pLine)
        }
        for (c in 0 until 8) {
            if (dotCounts[c] == 0) continue
            pDots.color = NightSky.starColor(c + 1)
            pDots.strokeWidth = (1.6f + c * 0.3f) * dp * starScale
            canvas.drawPoints(dotBufs[c], 0, dotCounts[c], pDots)
        }
        drawSelection(canvas)
    }

    /**
     * Les étoiles de fond, fixées au ciel et non à l'écran : elles défilent avec lui. Tirées
     * d'un hachage par case de quatre unités, donc toujours les mêmes au même endroit.
     */
    private fun drawFieldStars(canvas: Canvas) {
        val cell = when { unitPx < 6f * dp -> 16f; unitPx < 10f * dp -> 8f; else -> 4f }
        val x0 = floor((camX - width / 2f / unitPx) / cell).toInt()
        val x1 = floor((camX + width / 2f / unitPx) / cell).toInt()
        val y0 = floor((camY - height / 2f / unitPx) / cell).toInt()
        val y1 = floor((camY + height / 2f / unitPx) / cell).toInt()
        for (gy in y0..y1) for (gx in x0..x1) {
            var h = gx * 73856093 xor gy * 19349663
            repeat(3) {
                h = h * 1103515245 + 12345
                val fx = ((h ushr 8) and 0xFFFF) / 65535f
                h = h * 1103515245 + 12345
                val fy = ((h ushr 8) and 0xFFFF) / 65535f
                h = h * 1103515245 + 12345
                val b = ((h ushr 8) and 0xFF)
                pFill.color = (0x00DDE2FF) or ((40 + b / 3) shl 24)
                canvas.drawCircle(toScreenX((gx + fx) * cell), toScreenY((gy + fy) * cell), (0.6f + b / 400f) * dp, pFill)
            }
        }
    }

    private fun drawSelection(canvas: Canvas) {
        val e = selected ?: return
        val index = entries.indexOf(e)
        if (index < 0) return
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.solvedAt))
        val time = context.getString(R.string.starbridges_time, (e.seconds / 60).toInt(), (e.seconds % 60).toInt())
        val size = context.getString(R.string.starbridges_size, e.size)
        val detail = context.getString(R.string.starbridges_atlas_detail, size, time, date)
        val y = height - 56f * dp
        pFill.color = 0xB0060A1C.toInt()
        canvas.drawRect(0f, y - 30f * dp, width.toFloat(), height.toFloat(), pFill)
        canvas.drawText(names[index], width / 2f, y, pTitle)
        canvas.drawText(detail, width / 2f, y + 22f * dp, pDetail)
    }

    // ── Gestes ───────────────────────────────────────────────────────────────────

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Le point sous les doigts reste sous les doigts.
            val fx = camX + (detector.focusX - width / 2f) / unitPx
            val fy = camY + (detector.focusY - height / 2f) / unitPx
            unitPx = (unitPx * detector.scaleFactor).coerceIn(minUnit(), maxUnit())
            camX = fx - (detector.focusX - width / 2f) / unitPx
            camY = fy - (detector.focusY - height / 2f) / unitPx
            invalidate()
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            camX += dx / unitPx; camY += dy / unitPx
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val wx = camX + (e.x - width / 2f) / unitPx
            val wy = camY + (e.y - height / 2f) / unitPx
            val hit = entries.minByOrNull { hypot(it.skyX - wx, it.skyY - wy) }
                ?.takeIf { hypot(it.skyX - wx, it.skyY - wy) <= it.radius }
            selected = if (hit === selected) null else hit
            invalidate()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        if (!scaler.isInProgress) gestures.onTouchEvent(event)
        return true
    }
}
