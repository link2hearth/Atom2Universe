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
import kotlin.math.floor
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
    /** Le tir a toujours le même rayon : son dégradé est calculé une fois, centré sur l'origine. */
    private val projectileShade = RadialGradient(-.15f, -.2f, RicochetPuzzleRules.PROJECTILE_RADIUS * 1.7f,
        intArrayOf(0xFF81999F.toInt(), 0xFF263E48.toInt(), 0xFF13252C.toInt()), null, Shader.TileMode.CLAMP)
    // Le mur du mode difficile n'a pas besoin de plus de 60 images par seconde.
    private val wallTick = Runnable { invalidate() }
    // Prévenir l'activité hors du dessin, une seule fois par image.
    private var changePosted = false
    private val postedChange = Runnable { changePosted = false; onChanged?.invoke() }

    // Démonstration du geste quand on attend : un doigt touche à gauche puis à droite (le palet suit),
    // puis tire vers le bas et lâche. Vite pour les premiers tirs, ensuite seulement après une longue attente.
    private val prefs = context.getSharedPreferences(RicochetPuzzleStore.PREFS, Context.MODE_PRIVATE)
    private var taught = prefs.getInt(TAUGHT_KEY, 0)
    private var idleSince = 0L
    private var hintOn = false
    private var hintX = 0f
    private var hintY = 0f
    private var hintPressed = false
    private var hintAlpha = 0f
    private var hintPuck = false
    private var hintPuckX = 0f
    private var hintDragX = 0f
    private var hintDragY = 0f
    private var hintPower = 0f
    private var hintFlight = -1f
    private val hand = HandPainter()
    private val powerBar = PowerBarPainter()

    init { isFocusable = true; contentDescription = context.getString(R.string.puzzle_controls) }

    fun resume() { active = true; last = 0; idleSince = System.nanoTime(); postInvalidateOnAnimation() }
    fun pause() { active = false; last = 0; cancelAim() }
    fun refresh() {
        cancelAim(); rings.clear(); accumulator = 0f; last = 0; idleSince = System.nanoTime()
        launchX = 5f; launchY = RicochetPuzzleRules.BASE + 1.5f
        onChanged?.invoke(); invalidate()
    }

    /** Couché : la bannière est posée sur le jeu et les couleurs passent en colonne à droite de la planche. */
    private var landscape = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Ne jamais déformer la géométrie d'une seed en pivotant l'écran : seule l'échelle change.
        landscape = w > h
        val below = if (landscape) 8 * dp else 44 * dp
        scale = min((w - 12 * dp) / 10.4f, (h - below) / (RicochetPuzzleRules.HEIGHT + .4f)).coerceAtLeast(.1f)
        left = (w - 10 * scale) / 2
        top = 4 * dp + .2f * scale + maxOf(0f, (h - below - (RicochetPuzzleRules.HEIGHT + .4f) * scale) / 2)
        board?.recycle()
        board = Bitmap.createBitmap(ceil(10.4f * scale).toInt(), ceil((RicochetPuzzleRules.HEIGHT + .4f) * scale).toInt(), Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it); c.scale(scale, scale); c.translate(.2f, .2f); art.board(c, RicochetPuzzleRules.HEIGHT)
        }
        strip.clear()
        cancelAim()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        var dt = 0f
        val now = System.nanoTime()
        if (active) {
            dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, .05f)
            last = now
            val wasMoving = game.moving
            accumulator += dt
            while (accumulator >= 1f / 120) { game.step(1f / 120); accumulator -= 1f / 120 }
            val changed = game.events.isNotEmpty()
            game.events.forEach { rings.add(Ring(it)); sound?.onMerge(it.color, 1) }
            game.events.clear()
            if (wasMoving && !game.moving) idleSince = now
            if (wasMoving && !game.moving && game.complete) sound?.onDiscovery()
            if ((changed || wasMoving != game.moving) && !changePosted) { changePosted = true; post(postedChange) }
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
        val hintClock = hintTime(now)
        updateHint(hintClock)
        if (game.canLaunch) {
            val hintBase = RicochetPuzzleRules.BASE + 1.5f
            if (pointer >= 0) drawGuide(c, launchX, launchY, aimX, aimY, power)
            else if (hintOn && hintPower > 0f) drawGuide(c, HINT_RIGHT, hintBase, -hintDragX, -hintDragY, hintPower)
            val flying = hintOn && hintFlight >= 0f
            val px = if (hintOn && hintPuck) hintPuckX else launchX
            val py = if (hintOn && hintPuck) hintBase else launchY
            if (flying) {
                // Le palet de la démonstration part le long du guide et s'efface.
                val length = hypot(hintDragX, hintDragY)
                val fx = HINT_RIGHT - hintDragX / length * 3.2f * hintFlight
                val fy = hintBase - hintDragY / length * 3.2f * hintFlight
                c.saveLayerAlpha(fx - .6f, fy - .6f, fx + .6f, fy + .6f, ((1 - hintFlight) * 255).toInt())
                projectile(c, fx, fy); c.restore()
            } else {
                projectile(c, px, py)
                p.color = if (game.isLaunchClear(px, py)) EnamelPainter.INK else 0xFFC0503C.toInt()
                p.style = Paint.Style.STROKE; p.strokeWidth = .025f
                c.drawCircle(px, py, RicochetPuzzleRules.PROJECTILE_RADIUS + .13f, p)
                p.style = Paint.Style.FILL
            }
            val bar = if (pointer >= 0) power else if (hintOn) hintPower else 0f
            if (pointer >= 0 || bar > 0f) {
                powerBar.draw(c, RicochetPuzzleRules.BASE, bar)
            }
        }
        c.restore()
        if (hintOn && game.canLaunch) {
            c.save(); c.translate(left, top); c.scale(scale, scale)
            hand.draw(c, hintX, hintY, hintPressed, (hintAlpha * 235).toInt(), RicochetPuzzleRules.PROJECTILE_RADIUS + .25f)
            c.restore()
        }
        drawColorStrip(c)
        if (active && (game.moving || rings.isNotEmpty())) postInvalidateOnAnimation()
        else if (active && game.hard && !game.complete) { removeCallbacks(wallTick); postDelayed(wallTick, WALL_FRAME_MS) }
        else {
            last = 0
            // Sans mur à animer : on ne redessine que pendant la démonstration, sinon on attend la prochaine.
            if (hintClock != null) {
                val wait = when {
                    hintClock < 0f -> (-hintClock * 1000).toLong() + 1
                    hintClock % HINT_CYCLE < HINT_END -> WALL_FRAME_MS
                    else -> ((HINT_CYCLE - hintClock % HINT_CYCLE) * 1000).toLong() + 1
                }
                removeCallbacks(wallTick); postDelayed(wallTick, wait)
            }
        }
    }

    /** Secondes écoulées depuis que la démonstration peut commencer (négatif : encore à attendre). */
    private fun hintTime(now: Long): Float? {
        if (!active || !game.canLaunch || pointer >= 0 || idleSince == 0L) return null
        return (now - idleSince) / 1_000_000_000f - if (taught < LEARNED_SHOTS) FIRST_DELAY else REMINDER_DELAY
    }

    private fun updateHint(t: Float?) {
        hintOn = t != null && t >= 0f && t % HINT_CYCLE < HINT_END
        if (!hintOn || t == null) return
        val u = t % HINT_CYCLE
        fun phase(a: Float, b: Float) = ((u - a) / (b - a)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        val base = RicochetPuzzleRules.BASE + 1.5f
        hintAlpha = phase(0f, .35f) * (1 - phase(3.9f, 4.4f))
        hintX = if (u < 1.25f) 5f + (HINT_LEFT - 5f) * phase(.35f, .95f) else HINT_LEFT + (HINT_RIGHT - HINT_LEFT) * phase(1.25f, 1.95f)
        hintY = base
        hintPressed = u in .95f..1.25f || u in 1.95f..2.25f || u in 2.55f..3.75f
        hintPuck = u >= .95f
        hintPuckX = if (u < 1.95f) HINT_LEFT else HINT_RIGHT
        hintPower = 0f; hintFlight = -1f; hintDragX = 0f; hintDragY = 0f
        if (u >= 2.55f) {
            // Même longueur de tirage que le vrai geste : la jauge et le guide montrent un vrai tir.
            val k = .85f * phase(2.65f, 3.55f)
            val pull = pullLength(top + base * scale) / scale
            hintDragX = PULL_X * pull * k.coerceAtLeast(.05f); hintDragY = PULL_Y * pull * k.coerceAtLeast(.05f)
            hintX = HINT_RIGHT + hintDragX; hintY = base + hintDragY
            if (u < 3.75f) hintPower = k else hintFlight = ((u - 3.75f) / .65f).coerceIn(0f, 1f)
        }
    }

    /**
     * Les six couleurs, pleines tant qu'il en reste sur la planche. La bande ne change qu'avec les
     * couleurs restantes : elle est gardée en image au lieu de redessiner six palets à chaque image.
     */
    private fun drawColorStrip(c: Canvas) {
        var present = 0
        val pucks = game.pucks
        for (i in pucks.indices) { val color = pucks[i].color; if (color in 0..5) present = present or (1 shl color) }
        stripColors = present
        if (landscape) {
            val x = (left + 10 * scale + width) / 2
            val unit = (height - 72 * dp - 12 * dp) / 6
            val m = 1.25f * min(16 * dp, unit * .3f) + 4 * dp
            strip.draw(c, present.toLong(), floor(x - m).toInt(), 0, ceil(x + m).toInt(), height, paintStrip)
        } else {
            val y = height - 19 * dp
            val unit = (width - 24 * dp) / 6
            val m = 1.25f * min(11 * dp, unit * .2f) + 4 * dp
            strip.draw(c, present.toLong(), 0, floor(y - m).toInt().coerceAtLeast(0), width, ceil(y + m).toInt().coerceAtMost(height), paintStrip)
        }
    }

    private val strip = StripCache()
    private var stripColors = 0
    private val paintStrip: (Canvas) -> Unit = { c ->
        if (landscape) {
            // Couché : les six couleurs en colonne, à droite de la planche, sous les icônes de la bannière.
            val x = (left + 10 * scale + width) / 2
            val topY = 72 * dp
            val unit = (height - topY - 12 * dp) / 6
            for (color in 0..5) art.puck(c, color, x, topY + unit * (color + .5f),
                min(16 * dp, unit * .3f), alpha = if (stripColors and (1 shl color) != 0) 255 else 45)
        } else {
            val unit = (width - 24 * dp) / 6
            for (color in 0..5) art.puck(c, color, 12 * dp + unit * (color + .5f), height - 19 * dp,
                min(11 * dp, unit * .2f), alpha = if (stripColors and (1 shl color) != 0) 255 else 45)
        }
    }

    private fun projectile(c: Canvas, x: Float, y: Float) {
        val r = RicochetPuzzleRules.PROJECTILE_RADIUS
        p.style = Paint.Style.FILL; p.color = 0x50203035
        c.drawCircle(x + .03f, y + .08f, r, p)
        p.color = 0xFFB9CFD4.toInt(); c.drawCircle(x, y, r, p)
        p.shader = projectileShade
        c.save(); c.translate(x, y)
        c.drawCircle(0f, 0f, r * .88f, p); p.shader = null
        c.restore()
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
    private fun drawGuide(c: Canvas, fromX: Float, fromY: Float, towardX: Float, towardY: Float, strength: Float) {
        val speed = game.shotSpeed(fromY, towardX, towardY, strength) ?: return
        val length = hypot(towardX, towardY)
        val dx = towardX / length; val dy = towardY / length
        val distance = speed * speed / (2 * RicochetPuzzleRules.DRAG)
        val r = RicochetPuzzleRules.PROJECTILE_RADIUS
        var x = fromX; var y = fromY
        for (i in 1..ceil(distance / .05f).toInt()) {
            val nx = fromX + dx * min(i * .05f, distance)
            val ny = fromY + dy * min(i * .05f, distance)
            if (nx < r || nx > 10 - r || ny < r) break
            x = nx; y = ny
            if (game.touchesWall(x, y, r) || touchesPuck(x, y, r)) break
        }
        c.drawLine(fromX, fromY, x, y, guide)
    }

    /** Appelé à chaque pas du guide, des centaines de fois par image : distances au carré, sans hypot ni itérateur. */
    private fun touchesPuck(x: Float, y: Float, r: Float): Boolean {
        val pucks = game.pucks
        for (i in pucks.indices) {
            val q = pucks[i]
            val dx = q.x - x; val dy = q.y - y; val reach = q.radius + r
            if (dx * dx + dy * dy < reach * reach) return true
        }
        return false
    }

    private fun cancelAim() { pointer = -1; power = 0f; parent?.requestDisallowInterceptTouchEvent(false) }

    /** Distance de tirage (pixels) qui donne la pleine puissance depuis un appui à la hauteur [fromY]. */
    private fun pullLength(fromY: Float) = min(min(110 * dp, scale * 2.8f), height - fromY - 8 * dp).coerceAtLeast(18 * dp)

    private fun aim(e: MotionEvent): Boolean {
        val i = e.findPointerIndex(pointer)
        if (i < 0) { cancelAim(); return false }
        aimX = startX - e.getX(i); aimY = startY - e.getY(i)
        power = (hypot(aimX, aimY) / pullLength(startY)).coerceIn(0f, 1f)
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!active) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!game.canLaunch || e.x < left || e.x > left + 10 * scale ||
                    e.y < top + RicochetPuzzleRules.BASE * scale || e.y > top + RicochetPuzzleRules.HEIGHT * scale) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = e.getPointerId(0); startX = e.x; startY = e.y; idleSince = System.nanoTime()
                val r = RicochetPuzzleRules.PROJECTILE_RADIUS
                launchX = ((e.x - left) / scale).coerceIn(r, 10 - r)
                launchY = ((e.y - top) / scale).coerceIn(RicochetPuzzleRules.BASE + r, RicochetPuzzleRules.HEIGHT - r)
                power = 0f; aimX = 0f; aimY = -1f
            }
            MotionEvent.ACTION_MOVE -> if (pointer >= 0) aim(e)
            MotionEvent.ACTION_UP -> if (pointer >= 0) {
                if (aim(e) && game.launch(launchX, launchY, aimX, aimY, power)) {
                    sound?.onDrop(); onChanged?.invoke(); performClick()
                    if (taught < LEARNED_SHOTS) { taught++; prefs.edit().putInt(TAUGHT_KEY, taught).apply() }
                }
                cancelAim(); idleSince = System.nanoTime()
            }
            MotionEvent.ACTION_CANCEL -> cancelAim()
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == pointer) cancelAim()
        }
        invalidate(); return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private companion object {
        /** La prochaine image de l'écran tombe alors vers 17 ms (~60 i/s). */
        const val WALL_FRAME_MS = 12L
        const val TAUGHT_KEY = "hint_shots"
        /** Tirs réussis après lesquels la main n'arrive plus qu'après une longue attente. */
        const val LEARNED_SHOTS = 5
        const val FIRST_DELAY = 1.5f
        const val REMINDER_DELAY = 15f
        const val HINT_CYCLE = 6f
        const val HINT_END = 4.4f
        const val HINT_LEFT = 2.2f
        const val HINT_RIGHT = 7.8f
        // Tirage vers le bas et un peu à droite : le tir part vers le haut et la gauche.
        const val PULL_X = .29f
        const val PULL_Y = .957f
    }
}
