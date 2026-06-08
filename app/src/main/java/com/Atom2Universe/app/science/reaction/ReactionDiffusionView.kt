package com.Atom2Universe.app.science.reaction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Simulation Gray-Scott de réaction-diffusion (motifs de Turing).
 *
 * Deux « réactifs » A et B diffusent sur une grille ; B se nourrit de A
 * (A + 2B → 3B). Selon les taux d'alimentation (feed) et de destruction (kill),
 * le système s'auto-organise en coraux, taches, labyrinthes, spirales, etc.
 *
 * La simulation tourne sur un thread dédié et publie une [Bitmap] colorée que
 * la View met à l'échelle de l'écran.
 */
class ReactionDiffusionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Paramètres physiques (lus à chaud par le thread de simulation) ─────
    @Volatile var feed = 0.054f
    @Volatile var kill = 0.062f
    @Volatile var iterationsPerFrame = 10
    @Volatile var brushRadius = 4

    private val diffA = 1.0f
    private val diffB = 0.5f
    private val dt = 1.0f

    // ── Grille ──────────────────────────────────────────────────────────────
    private var gw = 0
    private var gh = 0
    private var a = FloatArray(0)
    private var b = FloatArray(0)
    private var a2 = FloatArray(0)
    private var b2 = FloatArray(0)

    // Index voisins pré-calculés (tore) pour éviter les modulos en boucle chaude
    private var xl = IntArray(0)
    private var xr = IntArray(0)
    private var rowUp = IntArray(0)
    private var rowDn = IntArray(0)

    // ── Rendu ────────────────────────────────────────────────────────────────
    private var pixels = IntArray(0)
    private var bitmap: Bitmap? = null
    private val bmpLock = Any()
    private val dstRect = Rect()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    @Volatile private var lut: IntArray = buildPalette(0)

    // ── Ensemencements en attente (depuis le thread UI vers la simulation) ──
    private val seedQueue = ArrayDeque<IntArray>()
    private val seedLock = Any()

    // ── Thread de simulation ─────────────────────────────────────────────────
    @Volatile private var running = false
    private var simThread: Thread? = null

    val isRunning: Boolean get() = running

    // ── Construction de la grille ────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val wasRunning = running
        stop()
        buildGrid(w, h)
        if (wasRunning) start()
    }

    private fun buildGrid(viewW: Int, viewH: Int) {
        val target = 200
        if (viewW >= viewH) {
            gw = target
            gh = (target * viewH.toFloat() / viewW).roundToInt().coerceIn(40, target)
        } else {
            gh = target
            gw = (target * viewW.toFloat() / viewH).roundToInt().coerceIn(40, target)
        }

        val n = gw * gh
        a = FloatArray(n); b = FloatArray(n)
        a2 = FloatArray(n); b2 = FloatArray(n)
        pixels = IntArray(n)

        xl = IntArray(gw) { (it - 1 + gw) % gw }
        xr = IntArray(gw) { (it + 1) % gw }
        rowUp = IntArray(gh) { ((it - 1 + gh) % gh) * gw }
        rowDn = IntArray(gh) { ((it + 1) % gh) * gw }

        synchronized(bmpLock) {
            bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        }
        clearAndSeed()
    }

    // ── Cycle de vie de la simulation ────────────────────────────────────────
    fun start() {
        if (running || gw == 0) return
        running = true
        simThread = Thread {
            val frameTargetMs = 16L
            while (running) {
                val t0 = System.currentTimeMillis()
                drainSeeds()
                repeat(iterationsPerFrame) { stepOnce() }
                render()
                postInvalidate()
                val elapsed = System.currentTimeMillis() - t0
                val sleep = frameTargetMs - elapsed
                if (sleep > 0) {
                    try { Thread.sleep(sleep) } catch (_: InterruptedException) { break }
                }
            }
        }.apply { name = "ReactionDiffusionSim"; start() }
    }

    fun stop() {
        running = false
        simThread?.let {
            it.interrupt()
            try { it.join(200) } catch (_: InterruptedException) {}
        }
        simThread = null
    }

    // ── Un pas d'intégration Gray-Scott ──────────────────────────────────────
    private fun stepOnce() {
        val a = a; val b = b
        val na = a2; val nb = b2
        val f = feed; val k = kill
        for (y in 0 until gh) {
            val up = rowUp[y]
            val dn = rowDn[y]
            val yc = y * gw
            for (x in 0 until gw) {
                val l = xl[x]
                val r = xr[x]
                val i = yc + x
                val av = a[i]
                val bv = b[i]

                val lapA = (a[yc + l] + a[yc + r] + a[up + x] + a[dn + x]) * 0.2f +
                    (a[up + l] + a[up + r] + a[dn + l] + a[dn + r]) * 0.05f - av
                val lapB = (b[yc + l] + b[yc + r] + b[up + x] + b[dn + x]) * 0.2f +
                    (b[up + l] + b[up + r] + b[dn + l] + b[dn + r]) * 0.05f - bv

                val abb = av * bv * bv
                var newA = av + (diffA * lapA - abb + f * (1f - av)) * dt
                var newB = bv + (diffB * lapB + abb - (k + f) * bv) * dt
                if (newA < 0f) newA = 0f else if (newA > 1f) newA = 1f
                if (newB < 0f) newB = 0f else if (newB > 1f) newB = 1f
                na[i] = newA
                nb[i] = newB
            }
        }
        // Échange des buffers
        this.a = na; this.a2 = a
        this.b = nb; this.b2 = b
    }

    private fun render() {
        val b = b
        val lut = lut
        val px = pixels
        for (i in px.indices) {
            var idx = (b[i] * 637f).toInt()
            if (idx < 0) idx = 0 else if (idx > 255) idx = 255
            px[i] = lut[idx]
        }
        synchronized(bmpLock) {
            bitmap?.setPixels(px, 0, gw, 0, 0, gw, gh)
        }
    }

    override fun onDraw(canvas: Canvas) {
        synchronized(bmpLock) {
            val bmp = bitmap ?: return
            dstRect.set(0, 0, width, height)
            canvas.drawBitmap(bmp, null, dstRect, bmpPaint)
        }
    }

    // ── Ensemencement / réinitialisation ─────────────────────────────────────
    /** Champ vierge (A=1, B=0) avec une tache centrale de B qui va croître. */
    fun clearAndSeed() {
        for (i in a.indices) { a[i] = 1f; b[i] = 0f }
        val cx = gw / 2
        val cy = gh / 2
        seedBlob(cx, cy, (gw.coerceAtMost(gh) / 12).coerceAtLeast(6))
        flushImmediate()
    }

    /** Disperse une dizaine de taches aléatoires. */
    fun randomize() {
        for (i in a.indices) { a[i] = 1f; b[i] = 0f }
        val blobs = 12
        repeat(blobs) {
            seedBlob(
                Random.nextInt(gw),
                Random.nextInt(gh),
                Random.nextInt(4, 9)
            )
        }
        flushImmediate()
    }

    private fun seedBlob(cx: Int, cy: Int, radius: Int) {
        for (dy in -radius..radius) {
            val y = ((cy + dy) % gh + gh) % gh
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue
                val x = ((cx + dx) % gw + gw) % gw
                b[y * gw + x] = 1f
            }
        }
    }

    private fun flushImmediate() {
        render()
        postInvalidate()
    }

    // ── Pinceau tactile ──────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (gw == 0) return true
                val gx = (event.x / width * gw).toInt().coerceIn(0, gw - 1)
                val gy = (event.y / height * gh).toInt().coerceIn(0, gh - 1)
                synchronized(seedLock) { seedQueue.addLast(intArrayOf(gx, gy)) }
                if (!running) {
                    drainSeeds()
                    flushImmediate()
                }
            }
        }
        return true
    }

    private fun drainSeeds() {
        val r = brushRadius
        synchronized(seedLock) {
            while (seedQueue.isNotEmpty()) {
                val (gx, gy) = seedQueue.removeFirst()
                seedBlob(gx, gy, r)
            }
        }
    }

    // ── Palettes ─────────────────────────────────────────────────────────────
    fun setPalette(index: Int) { lut = buildPalette(index) }

    private fun buildPalette(index: Int): IntArray {
        val stops: Array<Pair<Float, Int>> = when (index) {
            1 -> arrayOf(   // Océan
                0f to 0xFF02071A.toInt(), 0.40f to 0xFF053B7A.toInt(),
                0.70f to 0xFF1CA7C4.toInt(), 1f to 0xFFE8FFFF.toInt()
            )
            2 -> arrayOf(   // Acide
                0f to 0xFF001005.toInt(), 0.50f to 0xFF1A8F1A.toInt(),
                0.80f to 0xFF9BE000.toInt(), 1f to 0xFFF2FF7A.toInt()
            )
            3 -> arrayOf(   // Plasma
                0f to 0xFF0D0887.toInt(), 0.50f to 0xFFCC4778.toInt(),
                0.80f to 0xFFF89540.toInt(), 1f to 0xFFF0F921.toInt()
            )
            4 -> arrayOf(   // Corail
                0f to 0xFF0A0014.toInt(), 0.40f to 0xFF7A1F5A.toInt(),
                0.70f to 0xFFE25563.toInt(), 1f to 0xFFFFE0A3.toInt()
            )
            5 -> arrayOf(   // Mono
                0f to 0xFF000000.toInt(), 1f to 0xFFFFFFFF.toInt()
            )
            else -> arrayOf( // Inferno (0)
                0f to 0xFF000004.toInt(), 0.25f to 0xFF420A68.toInt(),
                0.50f to 0xFF932667.toInt(), 0.75f to 0xFFDD513A.toInt(),
                0.90f to 0xFFFCA50A.toInt(), 1f to 0xFFFCFFA4.toInt()
            )
        }
        val out = IntArray(256)
        for (i in 0..255) {
            val t = i / 255f
            var s = 0
            while (s < stops.size - 1 && t > stops[s + 1].first) s++
            val (p0, c0) = stops[s]
            val (p1, c1) = stops[(s + 1).coerceAtMost(stops.size - 1)]
            val span = (p1 - p0)
            val lt = if (span <= 0f) 0f else ((t - p0) / span).coerceIn(0f, 1f)
            out[i] = lerpColor(c0, c1, lt)
        }
        return out
    }

    private fun lerpColor(c0: Int, c1: Int, t: Float): Int {
        val r = (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * t).roundToInt()
        val g = (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * t).roundToInt()
        val bl = (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * t).roundToInt()
        return Color.rgb(r, g, bl)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
