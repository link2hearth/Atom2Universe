package com.Atom2Universe.app.games.escapelabyrinth

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class EscapeLabyrinthView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── State ────────────────────────────────────────────────────────────────

    var level: Level? = null
        set(value) { field = value; invalidate() }

    var playState: PlayState? = null
        set(value) { field = value; invalidate() }

    var guardVision: Set<String> = emptySet()
        set(value) { field = value; invalidate() }

    var onSwipe: ((dr: Int, dc: Int) -> Unit)? = null
    var onWait: (() -> Unit)? = null

    // ── Colors ───────────────────────────────────────────────────────────────

    private val colorBg       = Color.parseColor("#0A0A18")
    private val colorWall     = Color.parseColor("#0D0D20")
    private val colorFloor    = Color.parseColor("#1C1C3A")
    private val colorStart    = Color.parseColor("#2244AA")
    private val colorExit     = Color.parseColor("#00CC66")
    private val colorOrb      = Color.parseColor("#FFD700")
    private val colorPlayer   = Color.parseColor("#00FFFF")
    private val colorGuard    = Color.parseColor("#FF3333")
    private val colorVision   = Color.parseColor("#FF0000")
    private val colorWallEdge = Color.parseColor("#141428")

    // ── Paints ───────────────────────────────────────────────────────────────

    private val pBg    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBg }
    private val pWall  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorWall }
    private val pFloor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFloor }
    private val pVision = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorVision; alpha = 55; style = Paint.Style.FILL
    }
    private val pExit  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStart = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorStart; style = Paint.Style.FILL }
    private val pOrb   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pOrbGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pPlayer = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pPlayerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pGuard  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorGuard; style = Paint.Style.FILL }
    private val pGuardGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pGuardDir = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFAAAA.toInt(); style = Paint.Style.FILL }
    private val pGrid  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWallEdge; style = Paint.Style.STROKE; strokeWidth = 0.5f
    }

    // ── Touch tracking ───────────────────────────────────────────────────────

    private var touchX = 0f; private var touchY = 0f
    private var touching = false
    private val swipeThreshold = 18f * resources.displayMetrics.density

    // ── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val lvl = level ?: run { canvas.drawColor(colorBg); return }
        val play = playState

        canvas.drawColor(colorBg)

        val cellSz = min(width.toFloat() / lvl.tileW, height.toFloat() / lvl.tileH)
        val ox = (width - cellSz * lvl.tileW) / 2f
        val oy = (height - cellSz * lvl.tileH) / 2f

        drawTiles(canvas, lvl, play, cellSz, ox, oy)
        drawVisionOverlay(canvas, lvl, guardVision, cellSz, ox, oy)
        drawBonusOrbs(canvas, lvl, play, cellSz, ox, oy)
        drawGuards(canvas, lvl, play, cellSz, ox, oy)
        if (play != null) drawPlayer(canvas, lvl, play, cellSz, ox, oy)
    }

    private fun drawTiles(
        canvas: Canvas, lvl: Level, play: PlayState?,
        cs: Float, ox: Float, oy: Float
    ) {
        val collectedKeys = play?.let {
            lvl.bonusByCell.entries
                .filter { (_, orb) -> orb.id in play.collectedIds }
                .map { it.key }.toSet()
        } ?: emptySet()

        for (tr in 0 until lvl.tileH) {
            for (tc in 0 until lvl.tileW) {
                val x = ox + tc * cs; val y = oy + tr * cs
                val tile = lvl.grid[tr][tc]
                val paint = when (tile) {
                    TileType.WALL  -> pWall
                    TileType.FLOOR -> pFloor
                    TileType.START -> pStart
                    TileType.EXIT  -> {
                        pExit.color = colorExit; pExit
                    }
                    TileType.BONUS -> {
                        // If orb at this cell was collected, show as dim floor
                        val cr = tr / 2; val cc = tc / 2
                        if (collectedKeys.contains(cellKey(cr, cc))) {
                            pFloor.apply { alpha = 180 }
                        } else pFloor
                    }
                }
                // Reset alpha
                if (paint !== pFloor || tile != TileType.BONUS) pFloor.alpha = 255

                val gap = if (tile == TileType.WALL) 0.5f else 1f
                val rect = RectF(x + gap, y + gap, x + cs - gap, y + cs - gap)
                canvas.drawRoundRect(rect, 2f, 2f, paint)

                // Exit portal decoration
                if (tile == TileType.EXIT) {
                    val cx = x + cs / 2; val cy = y + cs / 2; val r = cs * 0.28f
                    pExit.color = 0xFF00FF88.toInt(); pExit.alpha = 180
                    canvas.drawCircle(cx, cy, r, pExit)
                }
                // Start marker
                if (tile == TileType.START) {
                    val cx = x + cs / 2; val cy = y + cs / 2; val r = cs * 0.22f
                    pStart.alpha = 130
                    canvas.drawCircle(cx, cy, r, pStart)
                    pStart.alpha = 255
                }
            }
        }
        pFloor.alpha = 255
    }

    private fun drawVisionOverlay(
        canvas: Canvas, lvl: Level, vision: Set<String>,
        cs: Float, ox: Float, oy: Float
    ) {
        for (key in vision) {
            val (cr, cc) = key.asCell()
            val tr = cr * 2; val tc = cc * 2
            val x = ox + tc * cs; val y = oy + tr * cs
            canvas.drawRect(x, y, x + cs, y + cs, pVision)
        }
    }

    private fun drawBonusOrbs(
        canvas: Canvas, lvl: Level, play: PlayState?,
        cs: Float, ox: Float, oy: Float
    ) {
        val collectedIds = play?.collectedIds ?: emptySet()
        for (orb in lvl.bonuses) {
            if (orb.id in collectedIds) continue
            val tc = orb.col * 2; val tr = orb.row * 2
            val cx = ox + tc * cs + cs / 2; val cy = oy + tr * cs + cs / 2
            val r = cs * 0.26f

            // Glow
            pOrbGlow.shader = RadialGradient(cx, cy, r * 2.5f,
                intArrayOf(0xAAFFD700.toInt(), 0x00FFD700.toInt()),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 2.5f, pOrbGlow)

            // Core
            pOrb.color = colorOrb; pOrb.alpha = 230
            canvas.drawCircle(cx, cy, r, pOrb)
            // Highlight
            pOrb.color = 0xFFFFFFCC.toInt(); pOrb.alpha = 180
            canvas.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.35f, pOrb)
        }
    }

    private fun drawGuards(
        canvas: Canvas, lvl: Level, play: PlayState?,
        cs: Float, ox: Float, oy: Float
    ) {
        val phase = play?.guardPhase ?: 0
        val states = lvl.guardStates.getOrNull(phase) ?: return

        for (g in states) {
            val tc = g.col * 2; val tr = g.row * 2
            val cx = ox + tc * cs + cs / 2; val cy = oy + tr * cs + cs / 2
            val r = cs * 0.32f

            // Glow
            pGuardGlow.shader = RadialGradient(cx, cy, r * 2.8f,
                intArrayOf(0x88FF3333.toInt(), 0x00FF0000.toInt()),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 2.8f, pGuardGlow)

            // Body
            canvas.drawCircle(cx, cy, r, pGuard)

            // Direction triangle
            val (dirDc, dirDr) = g.dir.dc.toFloat() to g.dir.dr.toFloat()
            val tipX = cx + dirDc * r * 0.9f; val tipY = cy + dirDr * r * 0.9f
            val perp = if (dirDr != 0f) 1f to 0f else 0f to 1f
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(cx + perp.first * r * 0.45f - dirDc * r * 0.3f,
                       cy + perp.second * r * 0.45f - dirDr * r * 0.3f)
                lineTo(cx - perp.first * r * 0.45f - dirDc * r * 0.3f,
                       cy - perp.second * r * 0.45f - dirDr * r * 0.3f)
                close()
            }
            canvas.drawPath(path, pGuardDir)
        }
    }

    private fun drawPlayer(
        canvas: Canvas, lvl: Level, play: PlayState,
        cs: Float, ox: Float, oy: Float
    ) {
        if (play.caught) { drawCaughtFlash(canvas, play, cs, ox, oy); return }
        val tc = play.col * 2; val tr = play.row * 2
        val cx = ox + tc * cs + cs / 2; val cy = oy + tr * cs + cs / 2
        val r = cs * 0.30f

        // Glow aura
        pPlayerGlow.shader = RadialGradient(cx, cy, r * 3.0f,
            intArrayOf(0x6600FFFF.toInt(), 0x0000FFFF.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 3.0f, pPlayerGlow)

        // Body
        pPlayer.color = colorPlayer; pPlayer.alpha = 240
        canvas.drawCircle(cx, cy, r, pPlayer)

        // Inner ring
        pPlayer.color = 0xFF88FFFF.toInt(); pPlayer.alpha = 200
        canvas.drawCircle(cx, cy, r * 0.55f, pPlayer)

        // Core dot
        pPlayer.color = Color.WHITE; pPlayer.alpha = 255
        canvas.drawCircle(cx, cy, r * 0.18f, pPlayer)
    }

    private fun drawCaughtFlash(canvas: Canvas, play: PlayState, cs: Float, ox: Float, oy: Float) {
        val tc = play.col * 2; val tr = play.row * 2
        val cx = ox + tc * cs + cs / 2; val cy = oy + tr * cs + cs / 2
        val r = cs * 0.35f
        pPlayer.color = 0xFFFF4444.toInt(); pPlayer.alpha = 200
        canvas.drawCircle(cx, cy, r, pPlayer)
        pPlayer.color = Color.WHITE; pPlayer.alpha = 255
        canvas.drawCircle(cx, cy, r * 0.3f, pPlayer)
    }

    // ── Touch input ──────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x; touchY = event.y; touching = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!touching) return true
                touching = false
                val dx = event.x - touchX; val dy = event.y - touchY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < swipeThreshold) {
                    onWait?.invoke()
                } else {
                    if (abs(dx) > abs(dy)) {
                        if (dx > 0) onSwipe?.invoke(0, 1) else onSwipe?.invoke(0, -1)
                    } else {
                        if (dy > 0) onSwipe?.invoke(1, 0) else onSwipe?.invoke(-1, 0)
                    }
                }
                return true
            }
        }
        return false
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Converts a tap position to a cell (row, col), or null if outside the grid. */
    fun cellAtPoint(px: Float, py: Float, lvl: Level): Pair<Int, Int>? {
        val cs = min(width.toFloat() / lvl.tileW, height.toFloat() / lvl.tileH)
        val ox = (width - cs * lvl.tileW) / 2f; val oy = (height - cs * lvl.tileH) / 2f
        val tc = ((px - ox) / cs).toInt(); val tr = ((py - oy) / cs).toInt()
        if (tr < 0 || tc < 0 || tr >= lvl.tileH || tc >= lvl.tileW) return null
        // Only even tile rows/cols correspond to cells
        if (tr % 2 != 0 || tc % 2 != 0) return null
        return (tr / 2) to (tc / 2)
    }
}
