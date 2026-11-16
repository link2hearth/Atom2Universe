package com.Atom2Universe.app.games.mercerie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le plateau à l'écran : il montre les temps du moteur l'un après l'autre (échange, disparitions,
 * chute) et ne rend la main au joueur qu'une fois tout retombé.
 */
@SuppressLint("ViewConstructor")
class MercerieBoardView(context: Context, val board: MercerieBoard, private val sound: MercerieSound?) : View(context) {

    interface Listener {
        /** Score, coups ou objectifs ont bougé. */
        fun onChanged()
        /** Objectif atteint : les coups restants vont partir en bobines. */
        fun onBonus()
        fun onFinished(won: Boolean)
    }

    var listener: Listener? = null

    /** Montrer un coup possible (deux pièces qui battent) quand le joueur ne joue pas. */
    var hints = true
        set(value) { field = value; armHint(); invalidate() }

    private class Sprite(val piece: Piece) {
        var x = 0f
        var y = 0f
        var scale = 1f
        var alpha = 1f
        var squash = 0f
        var kind = piece.kind
        var color = piece.color
        var pinned = piece.pinned
        var fromX = 0f
        var fromY = 0f
    }

    private class Swap(val a: Int, val b: Int, val valid: Boolean, val start: Long)
    private class Effect(val type: FxType, val cell: Int, val arg: Int, val color: Int, val start: Long)
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val size: Float, val color: Int, var spin: Float, val born: Long, val life: Int)
    private class Floater(val x: Float, val y: Float, val text: String, val start: Long)

    private val sprites = LinkedHashMap<Int, Sprite>()
    private val shownKnot = IntArray(board.size)
    private val shownPatch = IntArray(board.size)
    private val effects = ArrayList<Effect>()
    private val particles = ArrayList<Particle>()
    private val floaters = ArrayList<Floater>()
    private val fired = HashSet<Any>()
    private val rnd = Random(System.nanoTime())
    private val cheers: Array<String> = resources.getStringArray(R.array.mercerie_cheers)

    private var cell = 0f
    private var originX = 0f
    private var originY = 0f
    private var art: MercerieSprites? = null
    private var backdrop: Bitmap? = null

    private var step: Step? = null
    private var stepStart = 0L
    private var swap: Swap? = null
    private var ended = false
    private var resolving = false
    private var selected = -1
    private var downCell = -1
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private var hint: IntArray? = null
    private var cheer: String? = null
    private var cheerStart = 0L
    private var shakeUntil = 0L
    private var lastFrame = 0L
    private var lastPop = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MercerieUi.bold; textAlign = Paint.Align.CENTER }
    private val dst = RectF()
    private val beatRect = RectF()
    private val path = Path()

    private val showHint = Runnable {
        if (!busy) { hint = board.findMove(); invalidate() }
    }

    private val busy: Boolean get() = swap != null || step != null || resolving || ended

    init { sync() }

    // ───────────────────────────── Mise en place ─────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = prepare(w, h)

    /** Le mode jour / nuit a changé en pleine partie : pièces, cases et tissu sont refaits. */
    fun restyle() {
        prepare(width, height)
        invalidate()
    }

    private fun prepare(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val margin = MercerieUi.dp(context, 10f)
        // Une rangée de marge en haut : les pièces neuves arrivent de là.
        // Le tissu du plateau dépasse des cases : on lui garde sa place dans la largeur.
        cell = min((w - 2 * margin) / (board.cols + .36f), (h - 2 * margin) / (board.rows + .6f))
        originX = (w - cell * board.cols) / 2
        originY = (h - cell * board.rows) / 2 + cell * .2f
        art = MercerieSprites(context.assets, cell.toInt().coerceAtLeast(8))
        backdrop?.recycle()
        backdrop = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            val pad = cell * .16f
            val unit = MercerieUi.unit(context)
            val left = originX - pad
            val top = originY - pad
            val right = originX + cell * board.cols + pad
            val bottom = originY + cell * board.rows + pad
            Felt.panel(c, left, top, right, bottom, cell * .34f, Felt.KRAFT, unit, threadColor = Felt.CREAM)
            linen(context)?.let { cloth ->
                // Le lin du plateau, un peu foncé pour que les cases claires s'en détachent.
                val weave = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                weave.shader = BitmapShader(cloth, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
                    val k = cell * 4f / cloth.width
                    setLocalMatrix(Matrix().apply { setScale(k, k) })
                }
                if (!Felt.dark) weave.colorFilter = LightingColorFilter(0xFFE4D0BC.toInt(), 0)
                c.drawRoundRect(left, top, right, bottom, cell * .34f, cell * .34f, weave)
                val inset = unit * .16f
                path.rewind()
                path.addRoundRect(left + inset, top + inset, right - inset, bottom - inset, cell * .28f, cell * .28f, Path.Direction.CW)
                Felt.stitches(c, path, Felt.CREAM, unit)
            }
            for (r in 0 until board.rows) for (col in 0 until board.cols) if (board.cell[board.idx(r, col)]) {
                c.drawBitmap(art!!.tile[(r + col) % 2], originX + col * cell, originY + r * cell, paint)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        armHint()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(showHint)
        super.onDetachedFromWindow()
    }

    private fun armHint() {
        removeCallbacks(showHint)
        hint = null
        if (!ended && hints && isAttachedToWindow) postDelayed(showHint, 6000L)
    }

    /** Remet chaque image sur la case de sa pièce : l'état de repos entre deux temps. */
    private fun sync() {
        val alive = HashSet<Int>()
        for (i in 0 until board.size) {
            val p = board.piece[i] ?: continue
            alive += p.id
            val s = sprites.getOrPut(p.id) { Sprite(p) }
            s.x = board.colOf(i).toFloat()
            s.y = board.rowOf(i).toFloat()
            s.scale = 1f; s.alpha = 1f; s.squash = 0f
            s.kind = p.kind; s.color = p.color; s.pinned = p.pinned
        }
        sprites.keys.retainAll(alive)
        board.knot.copyInto(shownKnot)
        board.patch.copyInto(shownPatch)
    }

    /** Fait apparaître un mot en grand au milieu du plateau. */
    fun showCheer(word: String) {
        cheer = word
        cheerStart = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun cx(col: Float) = originX + (col + .5f) * cell
    private fun cy(row: Float) = originY + (row + .5f) * cell

    private fun cellAt(x: Float, y: Float): Int {
        if (cell <= 0f) return -1
        val c = ((x - originX) / cell).toInt()
        val r = ((y - originY) / cell).toInt()
        if (x < originX || y < originY || c >= board.cols || r >= board.rows) return -1
        val i = board.idx(r, c)
        return if (board.cell[i]) i else -1
    }

    // ───────────────────────────── Doigt ─────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (busy) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                armHint()
                sound?.wake()
                downCell = cellAt(event.x, event.y)
                downX = event.x; downY = event.y
                dragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (downCell < 0 || dragged) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (maxOf(abs(dx), abs(dy)) < cell * .32f) return true
                dragged = true
                val r = board.rowOf(downCell) + (if (abs(dy) > abs(dx)) (if (dy > 0) 1 else -1) else 0)
                val c = board.colOf(downCell) + (if (abs(dx) >= abs(dy)) (if (dx > 0) 1 else -1) else 0)
                selected = -1
                if (r in 0 until board.rows && c in 0 until board.cols) trySwap(downCell, board.idx(r, c))
            }
            MotionEvent.ACTION_UP -> {
                if (dragged || downCell < 0) return true
                // Deux touches de suite sur des cases voisines valent un glissement.
                val previous = selected
                if (previous >= 0 && previous != downCell && neighbours(previous, downCell)) {
                    selected = -1
                    trySwap(previous, downCell)
                } else {
                    selected = if (previous == downCell || board.piece[downCell] == null) -1 else downCell
                    if (selected >= 0) sound?.tick()
                }
                invalidate()
            }
        }
        return true
    }

    private fun neighbours(a: Int, b: Int) =
        abs(board.rowOf(a) - board.rowOf(b)) + abs(board.colOf(a) - board.colOf(b)) == 1

    private fun trySwap(a: Int, b: Int) {
        if (b < 0 || !board.cell[b]) return
        val pa = board.piece[a]
        val pb = board.piece[b]
        if (pa == null || pb == null || pa.pinned || pb.pinned) return
        val valid = board.canSwap(a, b)
        swap = Swap(a, b, valid, SystemClock.uptimeMillis())
        if (valid) sound?.swap() else sound?.bad()
        hint = null
        removeCallbacks(showHint)
        invalidate()
    }

    // ───────────────────────────── Déroulé ─────────────────────────────

    private fun next() {
        resolving = true
        val s = board.advance()
        if (s == null) { settled(); return }
        begin(s)
    }

    private fun begin(s: Step) {
        step = s
        stepStart = SystemClock.uptimeMillis()
        fired.clear()
        when (s) {
            is ClearStep -> {
                for (b in s.born) sprites[b.piece.id] = Sprite(b.piece).also {
                    it.x = board.colOf(b.cell).toFloat(); it.y = board.rowOf(b.cell).toFloat(); it.scale = 0f; it.alpha = 0f
                }
                if (s.cascade >= 3 && !board.bonus) {
                    cheer = cheers[(s.cascade - 3).coerceIn(0, cheers.size - 1)]
                    cheerStart = stepStart
                }
            }
            is FallStep -> for (f in s.falls) if (f.spawned) sprites[f.piece.id] = Sprite(f.piece).also {
                val at = f.path[0].cell
                it.x = board.colOf(at).toFloat(); it.y = board.rowOf(at) - 1f; it.alpha = 0f
            }
            is ShuffleStep -> for ((p, _) in s.moved) sprites[p.id]?.let { it.fromX = it.x; it.fromY = it.y }
        }
        invalidate()
    }

    private fun settled() {
        resolving = false
        step = null
        sync()
        listener?.onChanged()
        if (board.finished && !ended) {
            if (board.goalMet && !board.bonus) {
                listener?.onBonus()
                resolving = true
                val s = board.startBonus()
                if (s != null) begin(s) else next()
                return
            }
            ended = true
            removeCallbacks(showHint)
            listener?.onFinished(board.goalMet)
            return
        }
        armHint()
        invalidate()
    }

    private fun update(now: Long) {
        swap?.let { s ->
            val u = ((now - s.start) / SWAP_MS).coerceIn(0f, 1f)
            val e = ease(if (s.valid) u else if (u < .5f) u * 2 else (1 - u) * 2) * (if (s.valid) 1f else .45f)
            val pa = board.piece[s.a]
            val pb = board.piece[s.b]
            val ax = board.colOf(s.a).toFloat(); val ay = board.rowOf(s.a).toFloat()
            val bx = board.colOf(s.b).toFloat(); val by = board.rowOf(s.b).toFloat()
            pa?.let { sprites[it.id] }?.let { it.x = ax + (bx - ax) * e; it.y = ay + (by - ay) * e }
            pb?.let { sprites[it.id] }?.let { it.x = bx + (ax - bx) * e; it.y = by + (ay - by) * e }
            if (u >= 1f) {
                swap = null
                if (s.valid && board.swap(s.a, s.b)) {
                    sync()
                    listener?.onChanged()
                    next()
                } else {
                    sync()
                    armHint()
                }
            }
        }
        when (val s = step) {
            is ClearStep -> clearStep(s, now - stepStart, now)
            is FallStep -> fallStep(s, now - stepStart)
            is ShuffleStep -> {
                val u = ((now - stepStart) / 450f).coerceIn(0f, 1f)
                val e = ease(u)
                for ((p, at) in s.moved) sprites[p.id]?.let {
                    it.x = it.fromX + (board.colOf(at) - it.fromX) * e
                    it.y = it.fromY + (board.rowOf(at) - it.fromY) * e
                    it.scale = 1f - sin(u * PI.toFloat()) * .25f
                }
                if (u >= 1f) endStep()
            }
            null -> {}
        }
        val dt = if (lastFrame == 0L) 0f else (now - lastFrame).coerceAtMost(50L) / 1000f
        lastFrame = now
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (now - p.born > p.life) { it.remove(); continue }
            p.vy += cell * 14f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        effects.removeAll { now - it.start > 420 }
        floaters.removeAll { now - it.start > 800 }
        if (cheer != null && now - cheerStart > 1000) cheer = null
    }

    private fun endStep() {
        step = null
        sync()
        listener?.onChanged()
        next()
    }

    private fun clearStep(s: ClearStep, t: Long, now: Long) {
        for (g in s.gone) {
            val t0 = g.wave * WAVE_MS
            if (t < t0) continue
            val sp = sprites[g.piece.id] ?: continue
            if (fired.add(g)) {
                if (g.collected) sound?.basket() else {
                    burst(g.cell, if (sp.color >= 0) Felt.COLORS[sp.color] else Felt.CREAM, 5)
                    if (now - lastPop > 45) { lastPop = now; sound?.pop(s.cascade) }
                }
            }
            val u = ((t - t0) / POP_MS).coerceIn(0f, 1f)
            if (g.collected) {
                sp.y = board.rowOf(g.cell) + u * 1.1f
                sp.alpha = 1f - u
            } else {
                sp.scale = if (u < .25f) 1f + u else 1.25f * (1f - (u - .25f) / .75f)
                sp.alpha = 1f - u * u
                if (g.toward >= 0) {
                    val e = u * u
                    sp.x = board.colOf(g.cell) + (board.colOf(g.toward) - board.colOf(g.cell)) * e
                    sp.y = board.rowOf(g.cell) + (board.rowOf(g.toward) - board.rowOf(g.cell)) * e
                }
            }
            if (u >= 1f) sprites.remove(g.piece.id)
        }
        var bornDone = true
        for (b in s.born) {
            val sp = sprites[b.piece.id] ?: continue
            val t0 = b.wave * WAVE_MS + 70
            if (t < t0) { bornDone = false; continue }
            if (fired.add(b)) sound?.special()
            val u = ((t - t0) / 280f).coerceIn(0f, 1f)
            sp.alpha = 1f
            sp.scale = overshoot(u)
            if (u < 1f) bornDone = false
        }
        for (f in s.fx) if (t >= f.wave * WAVE_MS && fired.add(f)) applyFx(f, now)
        for (p in s.pops) if (t >= p.wave * WAVE_MS && fired.add(p)) {
            floaters += Floater(cx(board.colOf(p.cell).toFloat()), cy(board.rowOf(p.cell).toFloat()), p.points.toString(), now)
            listener?.onChanged()
        }
        if (bornDone && t >= s.waves * WAVE_MS + POP_MS + 60) endStep()
    }

    private fun spriteAt(cell: Int): Sprite? {
        val c = board.colOf(cell).toFloat()
        val r = board.rowOf(cell).toFloat()
        return sprites.values.firstOrNull { abs(it.x - c) < .01f && abs(it.y - r) < .01f }
    }

    private fun applyFx(f: Fx, now: Long) {
        when (f.type) {
            FxType.KNOT -> { shownKnot[f.cell] = f.arg; burst(f.cell, 0xFFD8C4AE.toInt(), 6) }
            FxType.PATCH -> { shownPatch[f.cell] = f.arg; burst(f.cell, 0xFFF8BECB.toInt(), 4) }
            FxType.UNPIN -> { spriteAt(f.cell)?.pinned = false; burst(f.cell, 0xFF8D97AD.toInt(), 4); sound?.tick() }
            FxType.TRANSFORM -> {
                spriteAt(f.cell)?.let { it.kind = it.piece.kind; it.color = it.piece.color }
                burst(f.cell, Felt.CREAM, 6)
                sound?.special()
            }
            FxType.LINE_H, FxType.LINE_V -> { effects += Effect(f.type, f.cell, 0, f.color, now); sound?.line() }
            FxType.BLAST -> {
                effects += Effect(f.type, f.cell, f.arg, f.color, now)
                shakeUntil = now + 160
                sound?.blast()
            }
            FxType.ZAP -> { effects += Effect(f.type, f.cell, f.arg, f.color, now); sound?.zap() }
            FxType.BASKET -> {}
        }
    }

    private fun fallStep(s: FallStep, t: Long) {
        val tt = t / TICK_MS
        for (f in s.falls) {
            val sp = sprites[f.piece.id] ?: continue
            val hops = f.path
            val last = hops[hops.size - 1]
            if (tt >= last.tick) {
                sp.x = board.colOf(last.cell).toFloat()
                sp.y = board.rowOf(last.cell).toFloat()
                sp.alpha = 1f
                val ls = (t - last.tick * TICK_MS) / 170f
                sp.squash = if (ls in 0f..1f) sin(ls * PI.toFloat()) * .16f else 0f
                if (ls >= 0f && fired.add(f)) sound?.land()
                continue
            }
            if (tt <= hops[0].tick) continue
            var i = 0
            while (i < hops.size - 2 && hops[i + 1].tick <= tt) i++
            val a = hops[i]
            val b = hops[i + 1]
            // Une pièce qui attend son tour reste posée, puis fait son pas en un seul temps.
            val u = (tt - (b.tick - 1)).coerceIn(0f, 1f)
            val fromSky = f.spawned && i == 0
            val ay = board.rowOf(a.cell) - (if (fromSky) 1f else 0f)
            sp.x = board.colOf(a.cell) + (board.colOf(b.cell) - board.colOf(a.cell)) * u
            sp.y = ay + (board.rowOf(b.cell) - ay) * u
            sp.alpha = if (fromSky) u else 1f
        }
        if (t >= s.ticks * TICK_MS + 170) endStep()
    }

    private fun burst(cellIndex: Int, color: Int, count: Int) {
        if (particles.size > 260) return
        val x = cx(board.colOf(cellIndex).toFloat())
        val y = cy(board.rowOf(cellIndex).toFloat())
        val now = SystemClock.uptimeMillis()
        repeat(count) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val v = cell * (2.2f + rnd.nextFloat() * 3.5f)
            particles += Particle(x, y, cos(a) * v, sin(a) * v - cell * 2.5f, cell * (.05f + rnd.nextFloat() * .06f),
                if (rnd.nextInt(4) == 0) Felt.CREAM else color, rnd.nextFloat() * 360f, now, 420 + rnd.nextInt(320))
        }
    }

    private fun ease(u: Float) = u * u * (3 - 2 * u)

    private fun overshoot(u: Float): Float {
        val s = 1.9f
        val v = u - 1
        return v * v * ((s + 1) * v + s) + 1
    }

    // ───────────────────────────── Dessin ─────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val a = art ?: return
        val now = SystemClock.uptimeMillis()
        update(now)
        canvas.save()
        if (now < shakeUntil) canvas.translate((rnd.nextFloat() - .5f) * cell * .08f, (rnd.nextFloat() - .5f) * cell * .08f)
        backdrop?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        paint.alpha = 255
        for (i in 0 until board.size) if (shownPatch[i] > 0)
            canvas.drawBitmap(a.patch[shownPatch[i] - 1], originX + board.colOf(i) * cell, originY + board.rowOf(i) * cell, paint)
        for (i in 0 until board.size) if (shownKnot[i] > 0)
            canvas.drawBitmap(a.knot[(shownKnot[i] - 1).coerceAtMost(2)], originX + board.colOf(i) * cell, originY + board.rowOf(i) * cell, paint)
        if (selected >= 0) {
            val pulse = 1f + sin(now / 130f) * .04f
            val half = cell * .47f * pulse
            val x = cx(board.colOf(selected).toFloat())
            val y = cy(board.rowOf(selected).toFloat())
            path.rewind()
            path.addRoundRect(x - half, y - half, x + half, y + half, cell * .2f, cell * .2f, Path.Direction.CW)
            Felt.stitches(canvas, path, Felt.PINK, cell, .07f)
        }
        val h = hint
        var arrows = false
        for (pass in 0..1) for (s in sprites.values) {
            val moving = s.scale != 1f || s.alpha != 1f
            if ((pass == 1) != moving) continue
            if (s.alpha <= 0f || s.scale <= 0f) continue
            var scale = s.scale
            if (s.piece.armed) scale *= 1f + sin(now / 45f) * .07f
            if (h != null && !busy) {
                val i = board.idx(s.y.toInt().coerceIn(0, board.rows - 1), s.x.toInt().coerceIn(0, board.cols - 1))
                if (i == h[0] || i == h[1]) scale *= 1f + sin(now / 110f) * .07f
            }
            val w = cell * scale * (1f + s.squash)
            val hgt = cell * scale * (1f - s.squash)
            val x = cx(s.x)
            val y = cy(s.y) + cell * scale * s.squash / 2
            dst.set(x - w / 2, y - hgt / 2, x + w / 2, y + hgt / 2)
            paint.alpha = (s.alpha * 255).toInt().coerceIn(0, 255)
            val arrow = a.arrowOf(s.kind)
            if (arrow != null) {
                // Bobine : les ciseaux battent doucement au-dessus de la pièce.
                arrows = true
                canvas.drawBitmap(a.plain(s.color), null, dst, paint)
                val beat = 1f + sin(now / 170f) * .1f
                val bx = dst.centerX()
                val by = dst.centerY()
                val bw = dst.width() * beat / 2
                val bh = dst.height() * beat / 2
                beatRect.set(bx - bw, by - bh, bx + bw, by + bh)
                canvas.drawBitmap(arrow, null, beatRect, paint)
            } else if (s.kind == Kind.RAINBOW) {
                // Le bouton arc-en-ciel tourne doucement sur lui-même : un tour en cinq secondes.
                arrows = true
                canvas.save()
                canvas.rotate((now % RAINBOW_TURN_MS) * 360f / RAINBOW_TURN_MS, dst.centerX(), dst.centerY())
                canvas.drawBitmap(a.rainbow, null, dst, paint)
                canvas.restore()
            } else if (s.kind == Kind.BOMB) {
                // Le pompon gonfle et dégonfle à peine.
                arrows = true
                val puff = 1f + sin(now / 260f) * .05f
                val bw = dst.width() * puff / 2
                val bh = dst.height() * puff / 2
                beatRect.set(dst.centerX() - bw, dst.centerY() - bh, dst.centerX() + bw, dst.centerY() + bh)
                canvas.drawBitmap(a.of(s.kind, s.color), null, beatRect, paint)
            } else canvas.drawBitmap(a.of(s.kind, s.color), null, dst, paint)
            if (s.pinned) canvas.drawBitmap(a.pin, null, dst, paint)
        }
        paint.alpha = 255
        drawEffects(canvas, now)
        for (p in particles) {
            val u = (now - p.born).toFloat() / p.life
            paint.color = p.color
            paint.alpha = ((1f - u) * 255).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.rotate(p.spin + u * 300f, p.x, p.y)
            canvas.drawRoundRect(p.x - p.size, p.y - p.size, p.x + p.size, p.y + p.size, p.size * .5f, p.size * .5f, paint)
            canvas.restore()
        }
        paint.alpha = 255
        text.textSize = cell * .34f
        for (f in floaters) {
            val u = (now - f.start) / 800f
            val y = f.y - cell * .7f * u
            val alpha = ((1f - u * u) * 255).toInt().coerceIn(0, 255)
            outlined(canvas, f.text, f.x, y, Felt.PINK, alpha)
        }
        cheer?.let { word ->
            val u = ((now - cheerStart) / 1000f).coerceIn(0f, 1f)
            val scale = if (u < .25f) overshoot(u / .25f) else 1f
            text.textSize = min(cell * 1.15f, width / (word.length * .62f)) * scale
            val alpha = if (u > .7f) ((1f - u) / .3f * 255).toInt() else 255
            outlined(canvas, word, width / 2f, originY + cell * board.rows * .46f, Felt.COLORS[(word.length) % 6], alpha.coerceIn(0, 255))
        }
        canvas.restore()
        // Pleine cadence pendant que ça bouge ; le coup conseillé, la pièce choisie et les bonus (ciseaux,
        // arc-en-ciel, pompon), qui bougent doucement en attendant le joueur, se contentent de 30 images par seconde.
        if (busy && !ended || effects.isNotEmpty() || particles.isNotEmpty() || floaters.isNotEmpty() || cheer != null)
            postInvalidateOnAnimation()
        else if (selected >= 0 || hint != null || arrows)
            postInvalidateDelayed(MercerieUi.IDLE_FRAME_MS)
    }

    private fun outlined(canvas: Canvas, s: String, x: Float, y: Float, color: Int, alpha: Int) {
        text.style = Paint.Style.STROKE
        text.strokeJoin = Paint.Join.ROUND
        text.strokeWidth = text.textSize * .22f
        text.color = Felt.CREAM
        text.alpha = alpha
        canvas.drawText(s, x, y, text)
        text.style = Paint.Style.FILL
        text.color = color
        text.alpha = alpha
        canvas.drawText(s, x, y, text)
    }

    private fun drawEffects(canvas: Canvas, now: Long) {
        for (e in effects) {
            val u = ((now - e.start) / 380f).coerceIn(0f, 1f)
            val x = cx(board.colOf(e.cell).toFloat())
            val y = cy(board.rowOf(e.cell).toFloat())
            when (e.type) {
                FxType.LINE_H, FxType.LINE_V -> {
                    val horizontal = e.type == FxType.LINE_H
                    val reach = ease(min(1f, u * 2.2f))
                    val x0 = if (horizontal) x - (x - originX) * reach else x
                    val x1 = if (horizontal) x + (originX + cell * board.cols - x) * reach else x
                    val y0 = if (horizontal) y else y - (y - originY) * reach
                    val y1 = if (horizontal) y else y + (originY + cell * board.rows - y) * reach
                    line.pathEffect = null
                    line.color = Felt.alpha(Felt.CREAM, ((1f - u) * 235).toInt())
                    line.strokeWidth = cell * .5f * (1f - u * .7f)
                    canvas.drawLine(x0, y0, x1, y1, line)
                    path.rewind(); path.moveTo(x0, y0); path.lineTo(x1, y1)
                    Felt.stitches(canvas, path, Felt.alpha(Felt.PINK, ((1f - u) * 255).toInt()), cell, .06f)
                }
                FxType.BLAST -> {
                    val e2 = 1f - (1f - u) * (1f - u)
                    line.pathEffect = null
                    line.color = Felt.alpha(Felt.CREAM, ((1f - u) * 230).toInt())
                    line.strokeWidth = cell * .3f * (1f - u)
                    canvas.drawCircle(x, y, cell * (.4f + (e.arg + .7f) * e2), line)
                }
                FxType.ZAP -> {
                    val tx = cx(board.colOf(e.arg).toFloat())
                    val ty = cy(board.rowOf(e.arg).toFloat())
                    val reach = min(1f, u * 2.5f)
                    path.rewind()
                    path.moveTo(x, y)
                    path.lineTo(x + (tx - x) * reach, y + (ty - y) * reach)
                    val color = if (e.color in 0..5) Felt.COLORS[e.color] else Felt.PINK
                    Felt.stitches(canvas, path, Felt.alpha(color, ((1f - u * u) * 255).toInt()), cell, .08f)
                }
                else -> {}
            }
        }
    }

    companion object {
        /** Le tissu du plateau (`plateau.webp`), chargé une fois par mode. */
        private val cloth = HashMap<Boolean, Bitmap>()

        private fun linen(context: Context): Bitmap? =
            cloth[Felt.dark] ?: Felt.image(context.assets, "plateau")?.also { cloth[Felt.dark] = it }

        private const val RAINBOW_TURN_MS = 5000L
        private const val SWAP_MS = 150f
        private const val WAVE_MS = 70L
        private const val POP_MS = 230f
        private const val TICK_MS = 58f
    }
}
