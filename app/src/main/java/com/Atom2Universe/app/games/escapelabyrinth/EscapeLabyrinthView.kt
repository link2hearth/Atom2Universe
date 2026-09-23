package com.Atom2Universe.app.games.escapelabyrinth

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/** Procedural stone sanctuary. All artwork is drawn locally, without bitmap assets. */
class EscapeLabyrinthView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var level: Level? = null
        set(value) {
            if (field !== value) {
                terrain = null
                previous = null
                animator.cancel()
                progress = 1f
            }
            field = value
            invalidate()
        }
    var playState: PlayState? = null
        set(value) {
            val old = field
            field = value
            if (old != null && value != null && value.turn == old.turn + 1) {
                animator.cancel()
                previous = old
                animator.start()
            } else {
                previous = null
                progress = 1f
            }
            invalidate()
        }
    var guardVision: Set<String> = emptySet()
        set(value) { field = value; invalidate() }
    var onSwipe: ((Int, Int) -> Unit)? = null
    var onWait: (() -> Unit)? = null

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var terrain: Bitmap? = null
    private var previous: PlayState? = null
    private var progress = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 180
        addUpdateListener { progress = it.animatedValue as Float; invalidate() }
    }
    private val bg = Color.rgb(12, 22, 25)
    private val stone = Color.rgb(48, 66, 67)
    private val edge = Color.rgb(76, 98, 94)
    private val teal = Color.rgb(112, 230, 200)
    private val gold = Color.rgb(240, 193, 111)
    private val red = Color.rgb(245, 124, 94)
    private var touchX = 0f
    private var touchY = 0f
    private var touching = false
    private val swipeThreshold = 18f * resources.displayMetrics.density

    private fun paint(color: Int, stroke: Float = 0f): Paint = ink.apply {
        this.color = color
        shader = null
        style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private fun line(c: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, sw: Float) =
        c.drawLine(x, y, x2, y2, paint(color, sw))
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) =
        c.drawOval(l, t, r, b, paint(color))
    private fun polygon(c: Canvas, color: Int, vararg points: Float) {
        path.reset()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close()
        c.drawPath(path, paint(color))
    }
    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint(color).shader = RadialGradient(x, y, r,
            intArrayOf((color and 0x00FFFFFF) or 0x55000000, color and 0x00FFFFFF),
            null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, ink)
        ink.shader = null
    }
    private fun cellSize(lvl: Level) = min(width / (lvl.tileW + 1.4f), height / (lvl.tileH + 1.4f))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { terrain = null }
    override fun onDetachedFromWindow() {
        animator.cancel()
        terrain = null
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        val lvl = level ?: return
        if (width <= 0 || height <= 0) return
        val cs = cellSize(lvl)
        val ox = (width - cs * lvl.tileW) / 2f
        val oy = (height - cs * lvl.tileH) / 2f
        val backdrop = terrain ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            drawTerrain(Canvas(it), lvl, cs, ox, oy)
            terrain = it
        }
        canvas.drawBitmap(backdrop, 0f, 0f, null)
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(cs, cs)
        // Exact watched cells stay visible, even while the sentinels animate between turns.
        for (key in guardVision) {
            val (r, c) = key.asCell()
            val x = c * 2f; val y = r * 2f
            canvas.drawRoundRect(x + .06f, y + .06f, x + .94f, y + .94f, .12f, .12f, paint(0x40F57C5E))
            line(canvas, x + .35f, y + .78f, x + .65f, y + .78f, red, .045f)
        }
        val play = playState
        portal(canvas, lvl.exit.second * 2f + .5f, lvl.exit.first * 2f + .5f,
            play != null && play.collectedIds.size == lvl.bonuses.size)
        for (orb in lvl.bonuses) {
            if (play?.collectedIds?.contains(orb.id) == true) continue
            val x = orb.col * 2f + .5f; val y = orb.row * 2f + .5f
            glow(canvas, x, y, .8f, gold)
            oval(canvas, x - .25f, y + .22f, x + .25f, y + .34f, 0x99000000.toInt())
            polygon(canvas, gold, x, y - .34f, x + .23f, y, x, y + .28f, x - .23f, y)
            polygon(canvas, 0xFFFFE9AF.toInt(), x, y - .34f, x, y + .28f, x - .23f, y)
            line(canvas, x + .32f, y - .25f, x + .32f, y - .09f, gold, .04f)
            line(canvas, x + .24f, y - .17f, x + .40f, y - .17f, gold, .04f)
        }
        val states = lvl.guardStates[play?.guardPhase ?: 0]
        val oldStates = previous?.let { lvl.guardStates[it.guardPhase] }
        states.forEachIndexed { i, g ->
            val old = oldStates?.getOrNull(i)
            val x = ((old?.col ?: g.col) + (g.col - (old?.col ?: g.col)) * progress) * 2f + .5f
            val y = ((old?.row ?: g.row) + (g.row - (old?.row ?: g.row)) * progress) * 2f + .5f
            sentinel(canvas, x, y, g)
        }
        if (play != null) {
            val old = previous ?: play
            val x = (old.col + (play.col - old.col) * progress) * 2f + .5f
            val y = (old.row + (play.row - old.row) * progress) * 2f + .5f
            explorer(canvas, x, y, play.caught)
        }
        canvas.restore()
    }

    private fun drawTerrain(c: Canvas, lvl: Level, cs: Float, ox: Float, oy: Float) {
        c.save()
        c.translate(ox, oy)
        c.scale(cs, cs)
        c.drawRoundRect(-.28f, -.28f, lvl.tileW + .28f, lvl.tileH + .28f, .25f, .25f, paint(0xFF15292D.toInt()))
        c.drawRoundRect(-.28f, -.28f, lvl.tileW + .28f, lvl.tileH + .28f, .25f, .25f, paint(edge, .045f))
        for (r in 0 until lvl.tileH) for (col in 0 until lvl.tileW) {
            val x = col.toFloat(); val y = r.toFloat()
            val hash = ((r * 73856093L xor col * 19349663L xor lvl.seed) and 0x7FFFFFFF).toInt()
            if (lvl.grid[r][col] == TileType.WALL) {
                c.drawRect(x, y, x + 1f, y + 1f, paint(0xFF1B2C30.toInt()))
                c.drawRoundRect(x + .025f, y + .025f, x + .975f, y + .84f, .08f, .08f, paint(stone))
                line(c, x + .12f, y + .09f, x + .88f, y + .09f, edge, .04f)
                line(c, x + .09f, y + .13f, x + .09f, y + .69f, 0xFF3A5351.toInt(), .035f)
                if (hash % 4 == 0) {
                    line(c, x + .61f, y + .12f, x + .49f, y + .35f, 0xFF233A3B.toInt(), .04f)
                    line(c, x + .49f, y + .35f, x + .64f, y + .5f, 0xFF233A3B.toInt(), .04f)
                }
                if (hash % 7 == 0) oval(c, x + .1f, y + .60f, x + .44f, y + .77f, 0xFF3E6252.toInt())
            } else {
                c.drawRect(x, y, x + 1f, y + 1f, paint(if (hash % 3 == 0) 0xFF203638.toInt() else 0xFF1D3033.toInt()))
                line(c, x + .10f, y + .96f, x + .9f, y + .96f, 0xFF15272B.toInt(), .04f)
                if (hash % 5 == 0) {
                    line(c, x + .16f, y + .24f, x + .26f, y + .30f, 0xFF39504B.toInt(), .035f)
                    oval(c, x + .7f, y + .65f, x + .8f, y + .70f, 0xFF526258.toInt())
                }
                if (lvl.grid[r][col] == TileType.START) {
                    c.drawCircle(x + .5f, y + .5f, .37f, paint(0xFF4A8F83.toInt(), .035f))
                    for (i in 0..3) {
                        c.save(); c.rotate(i * 90f, x + .5f, y + .5f)
                        line(c, x + .5f, y + .06f, x + .5f, y + .22f, teal, .04f); c.restore()
                    }
                }
            }
        }
        c.restore()
    }

    private fun portal(c: Canvas, x: Float, y: Float, unlocked: Boolean) {
        val light = if (unlocked) teal else gold
        glow(c, x, y, if (unlocked) 1f else .65f, light)
        c.drawRoundRect(x - .39f, y - .46f, x + .39f, y + .39f, .3f, .3f, paint(0xFF0D1E23.toInt()))
        c.drawRoundRect(x - .39f, y - .46f, x + .39f, y + .39f, .3f, .3f, paint(edge, .10f))
        c.drawArc(x - .27f, y - .35f, x + .27f, y + .29f, 180f, 180f, false, paint(light, .07f))
        if (unlocked) {
            oval(c, x - .18f, y - .23f, x + .18f, y + .31f, 0xCC70E6C8.toInt())
            line(c, x, y - .13f, x, y + .23f, Color.WHITE, .04f)
        } else {
            for (i in -1..1) line(c, x + i * .15f, y - .13f, x + i * .15f, y + .26f, gold, .055f)
        }
        line(c, x - .42f, y + .41f, x + .42f, y + .41f, light, .075f)
    }

    private fun sentinel(c: Canvas, x: Float, y: Float, g: GuardPhaseState) {
        oval(c, x - .38f, y + .17f, x + .38f, y + .43f, 0x99000000.toInt())
        c.save(); c.translate(x, y)
        c.rotate(when (g.dir) { Dir.N -> 0f; Dir.E -> 90f; Dir.S -> 180f; Dir.W -> 270f })
        // A small chevron shows the next step without revealing a second danger overlay.
        line(c, -.12f, -.65f, 0f, -.78f, gold, .045f)
        line(c, 0f, -.78f, .12f, -.65f, gold, .045f)
        c.drawRoundRect(-.39f, -.1f, -.23f, .3f, .05f, .05f, paint(0xFF715C48.toInt()))
        c.drawRoundRect(.23f, -.1f, .39f, .3f, .05f, .05f, paint(0xFF715C48.toInt()))
        polygon(c, 0xFFB28A5D.toInt(), -.27f, -.23f, 0f, -.39f, .27f, -.23f, .29f, .26f, 0f, .38f, -.29f, .26f)
        polygon(c, 0xFFE0B980.toInt(), -.27f, -.23f, 0f, -.39f, 0f, .3f, -.29f, .26f)
        c.drawRoundRect(-.24f, -.19f, .24f, -.01f, .06f, .06f, paint(0xFF352B2B.toInt()))
        line(c, -.14f, -.1f, .14f, -.1f, red, .08f)
        c.drawCircle(0f, .15f, .07f, paint(0xFF69513E.toInt()))
        c.restore()
    }

    private fun explorer(c: Canvas, x: Float, y: Float, caught: Boolean) {
        val cloth = if (caught) red else teal
        glow(c, x, y, .8f, cloth)
        oval(c, x - .34f, y + .22f, x + .34f, y + .44f, 0xAA000000.toInt())
        line(c, x - .13f, y + .25f, x - .17f, y + .38f, 0xFF102426.toInt(), .13f)
        line(c, x + .13f, y + .25f, x + .17f, y + .38f, 0xFF102426.toInt(), .13f)
        polygon(c, if (caught) 0xFF9F5448.toInt() else 0xFF348C80.toInt(), x, y - .20f, x + .32f, y + .27f, x, y + .34f, x - .32f, y + .27f)
        polygon(c, cloth, x, y - .45f, x + .28f, y - .24f, x + .25f, y + .10f, x - .25f, y + .10f, x - .28f, y - .24f)
        oval(c, x - .18f, y - .26f, x + .18f, y + .02f, 0xFF132D32.toInt())
        line(c, x - .1f, y - .10f, x - .05f, y - .10f, 0xFFFFE6B8.toInt(), .045f)
        line(c, x + .05f, y - .10f, x + .1f, y - .10f, 0xFFFFE6B8.toInt(), .045f)
        line(c, x - .16f, y + .07f, x + .17f, y + .26f, gold, .055f)
        glow(c, x + .34f, y + .19f, .32f, gold)
        c.drawRoundRect(x + .26f, y + .08f, x + .42f, y + .30f, .04f, .04f, paint(gold))
        if (caught) c.drawCircle(x, y, .55f, paint(red, .055f))
    }

    override fun performClick(): Boolean { super.performClick(); onWait?.invoke(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x; touchY = event.y; touching = true
                return true
            }
            MotionEvent.ACTION_CANCEL -> { touching = false; return true }
            MotionEvent.ACTION_UP -> {
                if (!touching) return true
                touching = false
                if (animator.isRunning) return true
                val dx = event.x - touchX; val dy = event.y - touchY
                if (hypot(dx, dy) < swipeThreshold) performClick()
                else if (abs(dx) > abs(dy)) onSwipe?.invoke(0, if (dx > 0) 1 else -1)
                else onSwipe?.invoke(if (dy > 0) 1 else -1, 0)
                return true
            }
        }
        return true
    }

    fun cellAtPoint(px: Float, py: Float, lvl: Level): Pair<Int, Int>? {
        val cs = cellSize(lvl)
        if (cs <= 0f) return null
        val x = px - (width - cs * lvl.tileW) / 2f
        val y = py - (height - cs * lvl.tileH) / 2f
        if (x < 0 || y < 0 || x >= cs * lvl.tileW || y >= cs * lvl.tileH) return null
        val c = (x / cs).toInt(); val r = (y / cs).toInt()
        return if (r % 2 == 0 && c % 2 == 0) r / 2 to c / 2 else null
    }
}
