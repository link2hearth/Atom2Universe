package com.Atom2Universe.app.games.starbridges

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le plateau de Constellations.
 *
 * **Les gestes.** On pose le doigt sur une étoile et on glisse vers une autre : le trait
 * suit la direction du doigt, arrondie à l'une des huit directions, et s'accroche à la
 * voisine visible dans cette direction. Relâcher pose le trait ; glisser le long d'un trait
 * existant l'efface, tout comme toucher un trait sans partir d'une étoile.
 *
 * **La lecture.** Pas de couleurs de validation : autour de chaque étoile, autant de petites
 * lueurs que de liens demandés, qui s'allument une à une. Quand elles sont toutes allumées,
 * elles se mettent à tourner lentement autour de l'étoile. Une étoile trop reliée clignote.
 */
class StarBridgesBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onBoardChanged()
        fun onSolved()
        /** L'embrasement de la figure est fini : c'est le moment d'afficher le résultat. */
        fun onIgnitionDone()
    }

    var game: StarBridgesGame? = null
    var listener: Listener? = null

    private val dp = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // ── Pinceaux ─────────────────────────────────────────────────────────────────
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLineGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pPreview = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pDigit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFF121633.toInt()
    }

    // ── Géométrie ────────────────────────────────────────────────────────────────
    private var cellPx = 0f
    private var startX = 0f
    private var startY = 0f
    private var coreR = 0f
    private var ringR = 0f
    private var sky: Bitmap? = null

    private fun recompute() {
        val g = game ?: return
        val pad = 10f * dp
        val board = min(width - pad * 2, height - pad * 2)
        cellPx = board / g.size
        startX = (width - board) / 2f
        startY = (height - board) / 2f
        coreR = cellPx * 0.17f
        ringR = coreR * 1.75f
        pDigit.textSize = coreR * 1.2f
        pLineGlow.strokeWidth = cellPx * 0.11f
        pLine.strokeWidth = cellPx * 0.035f
        pPreview.strokeWidth = cellPx * 0.04f
        pPreview.pathEffect = DashPathEffect(floatArrayOf(cellPx * 0.1f, cellPx * 0.08f), 0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sky?.recycle()
        sky = if (w > 0 && h > 0) NightSky.bake(w, h, 1977, dp) else null
        recompute()
    }

    private fun cx(n: SbNode) = startX + (n.x + 0.5f) * cellPx
    private fun cy(n: SbNode) = startY + (n.y + 0.5f) * cellPx

    private companion object {
        /** Un tour complet des lueurs d'une étoile complète, en millisecondes. */
        const val SPIN_PERIOD = 8000L
        const val SPIN_FRAME_MS = 33L
    }

    // ── Geste en cours ───────────────────────────────────────────────────────────
    private var dragFrom: SbNode? = null
    private var dragTarget: SbNode? = null
    private var fingerX = 0f
    private var fingerY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    /** Un trait refusé (il en croiserait un autre) rougit un instant. */
    private var rejectA: SbNode? = null
    private var rejectB: SbNode? = null
    private var rejectUntil = 0L

    // ── Embrasement de la victoire ───────────────────────────────────────────────
    private var ignition: List<String> = emptyList()
    private var ignitionStart = 0L
    private val ignitionPerLine = 90L
    private var ignitionReported = true

    /** Lance l'embrasement de la figure (à la victoire). */
    fun ignite() {
        val g = game ?: return
        ignition = g.ignitionOrder()
        ignitionStart = SystemClock.uptimeMillis()
        ignitionReported = false
        postInvalidateOnAnimation()
    }

    /** Montre la figure résolue sans animation (partie reprise déjà gagnée). */
    fun showSolved() {
        ignition = emptyList()
        ignitionReported = true
        invalidate()
    }

    // ── Dessin ───────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        sky?.let { canvas.drawBitmap(it, 0f, 0f, null) } ?: canvas.drawColor(0xFF060A1C.toInt())
        val g = game ?: return
        if (cellPx <= 0f) recompute()
        val now = SystemClock.uptimeMillis()
        var animating = false

        // Repères de la carte : un point discret à chaque case vide.
        pFill.color = 0x22FFFFFF
        val occupied = HashSet<Int>()
        for (n in g.nodes) occupied.add(n.y * g.size + n.x)
        for (y in 0 until g.size) for (x in 0 until g.size) {
            if (y * g.size + x in occupied) continue
            canvas.drawCircle(startX + (x + 0.5f) * cellPx, startY + (y + 0.5f) * cellPx, 1.2f * dp, pFill)
        }

        // Les traits.
        val lit = ignitionLitCount(now)
        for ((key, count) in g.bridges) {
            if (count <= 0) continue
            val (a, b) = g.endsOf(key)
            val na = g.nodes.firstOrNull { it.id == a } ?: continue
            val nb = g.nodes.firstOrNull { it.id == b } ?: continue
            val order = ignition.indexOf(key)
            // Le feu est une vague : un trait flambe au passage, puis redevient or.
            val burning = g.solved && order >= 0 && lit - order in 0..2
            drawLine(canvas, na, nb, if (burning) Color.WHITE else NightSky.LINE, if (burning) 255 else 220)
        }

        // Le trait en cours de geste.
        val from = dragFrom
        if (from != null && moved) {
            val target = dragTarget
            if (target == null) {
                pPreview.color = 0x66FFFFFF
                canvas.drawLine(cx(from), cy(from), fingerX, fingerY, pPreview)
            } else {
                val key = g.keyOf(from.id, target.id)
                pPreview.color = when {
                    g.hasBridge(key) -> 0xCCFF9A8A.toInt()
                    g.canAddBridge(key) -> 0xE6FFE2A0.toInt()
                    else -> NightSky.LINE_REJECT
                }
                canvas.drawLine(cx(from), cy(from), cx(target), cy(target), pPreview)
            }
        }
        if (now < rejectUntil) {
            val ra = rejectA; val rb = rejectB
            if (ra != null && rb != null) drawLine(canvas, ra, rb, NightSky.LINE_REJECT, ((rejectUntil - now) * 255 / 450).toInt())
            animating = true
        }

        // Les étoiles.
        val flicker = (0.55f + 0.45f * sin(now / 90.0)).toFloat()
        val spin = (now % SPIN_PERIOD) * 2.0 * PI / SPIN_PERIOD
        var spinning = false
        for (n in g.nodes) {
            val placed = g.bridgeCountFor(n.id)
            val over = placed > n.required
            if (over) animating = true
            if (placed == n.required) spinning = true
            val burst = g.solved && ignitionTouched(n, now)
            drawStar(canvas, n, placed, over, flicker, burst || n === dragFrom, spin)
        }

        if (g.solved && ignition.isNotEmpty()) {
            val total = ignition.size * ignitionPerLine + 500L
            if (now - ignitionStart < total) animating = true
            else if (!ignitionReported) {
                ignitionReported = true
                listener?.onIgnitionDone()
            }
        }
        if (animating) postInvalidateOnAnimation()
        // Une rotation lente n'a pas besoin de chaque image de l'écran : sur une tablette à
        // 120 Hz, redessiner le ciel entier en continu pour des points qui avancent d'un
        // demi-degré chaufferait pour rien. Trente images par seconde suffisent.
        else if (spinning) postInvalidateDelayed(SPIN_FRAME_MS)
    }

    private fun drawLine(canvas: Canvas, a: SbNode, b: SbNode, color: Int, alpha: Int) {
        val x1 = cx(a); val y1 = cy(a); val x2 = cx(b); val y2 = cy(b)
        val len = hypot(x2 - x1, y2 - y1)
        if (len < 1f) return
        val trim = ringR + cellPx * 0.05f
        val ux = (x2 - x1) / len * trim; val uy = (y2 - y1) / len * trim
        pLineGlow.color = (color and 0xFFFFFF) or ((alpha * 0.22f).toInt() shl 24)
        canvas.drawLine(x1 + ux, y1 + uy, x2 - ux, y2 - uy, pLineGlow)
        pLine.color = (color and 0xFFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        canvas.drawLine(x1 + ux, y1 + uy, x2 - ux, y2 - uy, pLine)
    }

    private fun drawStar(canvas: Canvas, n: SbNode, placed: Int, over: Boolean, flicker: Float, highlight: Boolean, spin: Double) {
        val x = cx(n); val y = cy(n)
        val color = NightSky.starColor(n.required)
        val glowR = coreR * (2.2f + n.required * 0.12f) * if (highlight) 1.35f else 1f
        pGlow.shader = NightSky.glowShader(n.required)
        pGlow.alpha = if (over) (255 * flicker).toInt() else 255
        canvas.save(); canvas.translate(x, y); canvas.scale(glowR, glowR)
        canvas.drawCircle(0f, 0f, 1f, pGlow)
        canvas.restore()

        pFill.color = if (over) blend(color, NightSky.LINE_REJECT, 0.5f * flicker) else color
        canvas.drawCircle(x, y, coreR, pFill)
        canvas.drawText(n.required.toString(), x, y - (pDigit.descent() + pDigit.ascent()) / 2f, pDigit)

        // Les lueurs : une par lien demandé, allumées au fil des liens posés. Toutes
        // allumées, elles tournent : l'étoile est complète.
        val count = n.required
        val complete = placed == count
        val dotR = coreR * 0.2f
        val offset = if (complete) spin else 0.0
        for (i in 0 until count) {
            val a = -PI / 2 + i * 2 * PI / count + offset
            val dx = x + (cos(a) * ringR).toFloat(); val dy = y + (sin(a) * ringR).toFloat()
            pFill.color = when {
                over -> (NightSky.LINE_REJECT and 0xFFFFFF) or ((255 * flicker).toInt() shl 24)
                i < placed -> NightSky.LINE
                else -> 0x40FFFFFF
            }
            canvas.drawCircle(dx, dy, if (i < placed || over) dotR * 1.25f else dotR, pFill)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt()
    )

    private fun ignitionLitCount(now: Long): Int =
        if (ignition.isEmpty()) Int.MAX_VALUE else ((now - ignitionStart) / ignitionPerLine).toInt()

    /** Une étoile flambe au moment où le feu atteint un de ses traits. */
    private fun ignitionTouched(n: SbNode, now: Long): Boolean {
        if (ignition.isEmpty()) return false
        val g = game ?: return false
        val elapsed = now - ignitionStart
        ignition.forEachIndexed { i, k ->
            val (a, b) = g.endsOf(k)
            if (a == n.id || b == n.id) {
                val t = elapsed - i * ignitionPerLine
                if (t in 0..220) return true
            }
        }
        return false
    }

    // ── Toucher ──────────────────────────────────────────────────────────────────

    private fun starAt(x: Float, y: Float): SbNode? {
        val g = game ?: return null
        val reach = cellPx * 0.45f
        return g.nodes.minByOrNull { hypot(cx(it) - x, cy(it) - y) }
            ?.takeIf { hypot(cx(it) - x, cy(it) - y) <= reach }
    }

    /** La voisine visée depuis [from] quand le doigt est en ([x], [y]). */
    private fun aim(from: SbNode, x: Float, y: Float): SbNode? {
        val g = game ?: return null
        val dx = x - cx(from); val dy = y - cy(from)
        if (hypot(dx, dy) < cellPx * 0.35f) return null
        val octant = (atan2(dy, dx) / (PI / 4)).roundToInt()
        val a = octant * PI / 4
        return g.neighborInDirection(from.id, cos(a).roundToInt(), sin(a).roundToInt())
    }

    /** Le trait posé le plus proche du point, s'il passe assez près. */
    private fun lineAt(x: Float, y: Float): String? {
        val g = game ?: return null
        var best: String? = null
        var bestD = cellPx * 0.22f
        for ((key, count) in g.bridges) {
            if (count <= 0) continue
            val (a, b) = g.endsOf(key)
            val na = g.nodes.firstOrNull { it.id == a } ?: continue
            val nb = g.nodes.firstOrNull { it.id == b } ?: continue
            val d = distToSegment(x, y, cx(na), cy(na), cx(nb), cy(nb))
            if (d < bestD) { bestD = d; best = key }
        }
        return best
    }

    private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val vx = x2 - x1; val vy = y2 - y1
        val len2 = vx * vx + vy * vy
        val t = if (len2 <= 0f) 0f else (((px - x1) * vx + (py - y1) * vy) / len2).coerceIn(0f, 1f)
        return hypot(px - (x1 + t * vx), py - (y1 + t * vy))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return true
        if (g.solved) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                fingerX = event.x; fingerY = event.y
                moved = false
                dragFrom = starAt(event.x, event.y)
                dragTarget = null
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                fingerX = event.x; fingerY = event.y
                if (!moved && hypot(event.x - downX, event.y - downY) > touchSlop) moved = true
                dragFrom?.let { dragTarget = aim(it, event.x, event.y) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val from = dragFrom
                val target = dragTarget
                if (from != null && target != null && moved) {
                    if (!g.toggleBridge(from.id, target.id)) {
                        rejectA = from; rejectB = target
                        rejectUntil = SystemClock.uptimeMillis() + 450L
                    }
                    report(g)
                } else if (from == null && !moved) {
                    lineAt(event.x, event.y)?.let { key ->
                        val (a, b) = g.endsOf(key)
                        g.toggleBridge(a, b)
                        report(g)
                    }
                }
                dragFrom = null; dragTarget = null; moved = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragFrom = null; dragTarget = null; moved = false
                invalidate()
            }
        }
        return true
    }

    private fun report(g: StarBridgesGame) {
        if (g.solved) {
            listener?.onSolved()
            ignite()
        } else listener?.onBoardChanged()
    }

    fun refresh() { recompute(); invalidate() }
}
