package com.Atom2Universe.app.games.enamel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.games.bigger.BiggerSoundEngine
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

class RicochetPuzzleView(context: Context, val game: RicochetPuzzleGame = RicochetPuzzleGame()) : View(context) {
    var onChanged: (() -> Unit)? = null
    var sound: BiggerSoundEngine? = null
    private val dp = resources.displayMetrics.density
    private val colors = SciencePalette(context)
    private val art = EnamelPainter()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EnamelPainter.INK; alpha = 130; strokeWidth = .035f
        pathEffect = DashPathEffect(floatArrayOf(.12f, .12f), 0f)
    }
    private var board: Bitmap? = null
    private var scale = 1f
    private var left = 0f
    private var top = 0f
    private var active = false
    private var last = 0L
    private var accumulator = 0f
    private var pointer = -1
    private var startX = 0f
    private var startY = 0f
    private var launchX = 5f
    private var launchY = RicochetPuzzleRules.BASE + 1.5f
    private var aimX = 0f
    private var aimY = -1f
    private var power = 0f
    private data class Ring(val event: PuzzlePairEvent, var life: Float = .65f)
    private val rings = ArrayList<Ring>()

    init { isFocusable = true; contentDescription = context.getString(R.string.puzzle_controls) }

    fun resume() { active = true; last = 0; postInvalidateOnAnimation() }
    fun pause() { active = false; last = 0; cancelAim() }
    fun refresh() {
        cancelAim(); rings.clear(); accumulator = 0f; last = 0
        launchX = 5f; launchY = RicochetPuzzleRules.BASE + 1.5f
        onChanged?.invoke(); invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Ne jamais déformer la géométrie d'une seed en pivotant l'écran.
        scale = min((w - 12 * dp) / 10.4f, (h - 44 * dp) / (RicochetPuzzleRules.HEIGHT + .4f)).coerceAtLeast(.1f)
        left = (w - 10 * scale) / 2
        top = 4 * dp + .2f * scale + maxOf(0f, (h - 44 * dp - (RicochetPuzzleRules.HEIGHT + .4f) * scale) / 2)
        board?.recycle()
        board = Bitmap.createBitmap(ceil(10.4f * scale).toInt(), ceil((RicochetPuzzleRules.HEIGHT + .4f) * scale).toInt(), Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it); c.scale(scale, scale); c.translate(.2f, .2f); art.board(c, RicochetPuzzleRules.HEIGHT)
        }
        cancelAim()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        var dt = 0f
        if (active) {
            val now = System.nanoTime()
            dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, .05f)
            last = now
            val wasMoving = game.moving
            accumulator += dt
            while (accumulator >= 1f / 120) { game.step(1f / 120); accumulator -= 1f / 120 }
            val changed = game.events.isNotEmpty()
            game.events.forEach { rings.add(Ring(it)); sound?.onMerge(it.color, 1) }
            game.events.clear()
            if (wasMoving && !game.moving && game.complete) sound?.onDiscovery()
            if (changed || wasMoving != game.moving) onChanged?.invoke()
        }
        rings.forEach { it.life -= dt }; rings.removeAll { it.life <= 0f }
        c.drawColor(colors.background)
        board?.let { c.drawBitmap(it, left - .2f * scale, top - .2f * scale, null) }
        c.save(); c.translate(left, top); c.scale(scale, scale)
        c.clipRect(0f, 0f, 10f, RicochetPuzzleRules.HEIGHT)
        if (game.hard) drawWall(c)
        game.pucks.forEach {
            if (it.projectile) projectile(c, it.x, it.y) else art.puck(c, it.color, it.x, it.y, it.radius)
        }
        for (ring in rings) {
            p.style = Paint.Style.STROKE; p.strokeWidth = .045f
            p.color = EnamelPainter.COLORS[ring.event.color]; p.alpha = (ring.life / .65f * 230).toInt()
            c.drawCircle(ring.event.x, ring.event.y, .55f + (1 - ring.life / .65f) * .8f, p)
        }
        p.style = Paint.Style.FILL; p.alpha = 255
        if (game.canLaunch) {
            if (pointer >= 0 && game.shotSpeed(launchY, aimX, aimY, power) != null) drawGuide(c)
            projectile(c, launchX, launchY)
            p.color = if (game.isLaunchClear(launchX, launchY)) EnamelPainter.INK else 0xFFC0503C.toInt()
            p.style = Paint.Style.STROKE; p.strokeWidth = .025f
            c.drawCircle(launchX, launchY, RicochetPuzzleRules.PROJECTILE_RADIUS + .13f, p)
            p.style = Paint.Style.FILL
            if (pointer >= 0) {
                p.color = EnamelPainter.GOLD; p.strokeWidth = .065f
                c.drawLine(0f, RicochetPuzzleRules.BASE, 10f * power, RicochetPuzzleRules.BASE, p)
            }
        }
        c.restore()
        val unit = (width - 24 * dp) / 6
        for (color in 0..5) art.puck(c, color, 12 * dp + unit * (color + .5f), height - 19 * dp,
            min(11 * dp, unit * .2f), alpha = if (game.pucks.any { it.color == color }) 255 else 45)
        if (active && (game.moving || (game.hard && !game.complete) || rings.isNotEmpty())) postInvalidateOnAnimation()
        else last = 0
    }

    private fun projectile(c: Canvas, x: Float, y: Float) {
        val r = RicochetPuzzleRules.PROJECTILE_RADIUS
        p.style = Paint.Style.FILL; p.color = 0x50203035
        c.drawCircle(x + .03f, y + .08f, r, p)
        p.color = 0xFFB9CFD4.toInt(); c.drawCircle(x, y, r, p)
        p.shader = RadialGradient(x - .15f, y - .2f, r * 1.7f,
            intArrayOf(0xFF81999F.toInt(), 0xFF263E48.toInt(), 0xFF13252C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * .88f, p); p.shader = null
        p.color = 0xFFB9CFD4.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = .024f
        c.drawCircle(x, y, r * .64f, p); p.style = Paint.Style.FILL
    }

    private fun drawWall(c: Canvas) {
        val x = game.wallX; val y = RicochetPuzzleRules.WALL_Y
        val hw = RicochetPuzzleRules.WALL_HALF_WIDTH; val hh = RicochetPuzzleRules.WALL_HALF_HEIGHT
        p.color = 0x50173E40; c.drawRoundRect(x - hw, y - hh + .08f, x + hw, y + hh + .08f, .08f, .08f, p)
        p.color = EnamelPainter.INK; c.drawRoundRect(x - hw, y - hh, x + hw, y + hh, .08f, .08f, p)
        p.color = EnamelPainter.GOLD; p.strokeWidth = .035f
        c.drawLine(x - hw + .1f, y - hh + .06f, x + hw - .1f, y - hh + .06f, p)
        c.drawLine(x - hw + .1f, y + hh - .06f, x + hw - .1f, y + hh - .06f, p)
        for (end in floatArrayOf(-1f, 1f)) c.drawCircle(x + end * (hw - .12f), y, .045f, p)
        val dir = if (game.wallVelocity > 0) 1f else -1f
        c.drawLine(x - dir * .1f, y - .07f, x + dir * .1f, y, p)
        c.drawLine(x - dir * .1f, y + .07f, x + dir * .1f, y, p)
    }

    /** Premier impact seulement : le mur peut encore se déplacer avant l'arrivée du tir. */
    private fun drawGuide(c: Canvas) {
        val speed = game.shotSpeed(launchY, aimX, aimY, power) ?: return
        val length = hypot(aimX, aimY)
        val dx = aimX / length; val dy = aimY / length
        val distance = speed * speed / (2 * RicochetPuzzleRules.DRAG)
        val r = RicochetPuzzleRules.PROJECTILE_RADIUS
        var x = launchX; var y = launchY
        for (i in 1..ceil(distance / .05f).toInt()) {
            val nx = launchX + dx * min(i * .05f, distance)
            val ny = launchY + dy * min(i * .05f, distance)
            if (nx < r || nx > 10 - r || ny < r) break
            x = nx; y = ny
            if (game.touchesWall(x, y, r) || game.pucks.any { hypot(it.x - x, it.y - y) < it.radius + r }) break
        }
        c.drawLine(launchX, launchY, x, y, guide)
    }

    private fun cancelAim() { pointer = -1; power = 0f; parent?.requestDisallowInterceptTouchEvent(false) }

    private fun aim(e: MotionEvent): Boolean {
        val i = e.findPointerIndex(pointer)
        if (i < 0) { cancelAim(); return false }
        aimX = startX - e.getX(i); aimY = startY - e.getY(i)
        val pull = min(min(110 * dp, scale * 2.8f), height - startY - 8 * dp).coerceAtLeast(18 * dp)
        power = (hypot(aimX, aimY) / pull).coerceIn(0f, 1f)
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!active) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!game.canLaunch || e.x < left || e.x > left + 10 * scale ||
                    e.y < top + RicochetPuzzleRules.BASE * scale || e.y > top + RicochetPuzzleRules.HEIGHT * scale) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = e.getPointerId(0); startX = e.x; startY = e.y
                val r = RicochetPuzzleRules.PROJECTILE_RADIUS
                launchX = ((e.x - left) / scale).coerceIn(r, 10 - r)
                launchY = ((e.y - top) / scale).coerceIn(RicochetPuzzleRules.BASE + r, RicochetPuzzleRules.HEIGHT - r)
                power = 0f; aimX = 0f; aimY = -1f
            }
            MotionEvent.ACTION_MOVE -> if (pointer >= 0) aim(e)
            MotionEvent.ACTION_UP -> if (pointer >= 0) {
                if (aim(e) && game.launch(launchX, launchY, aimX, aimY, power)) {
                    sound?.onDrop(); onChanged?.invoke(); performClick()
                }
                cancelAim()
            }
            MotionEvent.ACTION_CANCEL -> cancelAim()
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == pointer) cancelAim()
        }
        invalidate(); return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
