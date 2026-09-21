package com.Atom2Universe.app.games.roguelike.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import com.Atom2Universe.app.R
import kotlin.math.*

/** Reconnaissance indépendante du lieu du toucher, avec une seule tentative par étape. */
internal class DungeonGesture(private val context: Context) {
    enum class Kind { RIGHT, RETURN, CIRCLE, TRIANGLE, TAP, DOUBLE_TAP }
    enum class Grade { MISS, GOOD, PERFECT }
    private enum class SwipeDirection(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }
    var kind = Kind.RIGHT
        private set
    var active = false
        private set
    var grade = Grade.MISS
        private set
    private var start = 0f
    private var duration = 2100f
    private var resolvedAt = -10000f
    private var step = 0
    private var firstGrade = Grade.PERFECT
    private val points = mutableListOf<Pair<Float, Float>>()
    private var downAt = 0f
    private var pointer = -1
    private var targetRadius = 20f
    private var defending = false
    private var swipeDirection = SwipeDirection.UP
    private var nextSwipeDirection = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val good = 0xFF91E6C3.toInt()
    private val bad = 0xFFF18B93.toInt()
    private val white = 0xFFF1F0DB.toInt()
    private val blue = 0xFF9ADDEF.toInt()
    private val messages = listOf(R.string.dungeon_gesture_right, R.string.dungeon_gesture_return,
        R.string.dungeon_gesture_circle, R.string.dungeon_gesture_triangle,
        R.string.dungeon_gesture_tap, R.string.dungeon_gesture_double).map { context.getString(it) }
    private val results = listOf(R.string.dungeon_gesture_miss, R.string.dungeon_gesture_good,
        R.string.dungeon_gesture_perfect).map { context.getString(it) }
    private val swipeInstruction = context.getString(R.string.dungeon_gesture_swipe)
    val result get() = results[grade.ordinal]
    fun shake(now: Float): Float {
        val age = now - resolvedAt
        return if (!active && grade == Grade.MISS && age in 0f..220f)
            sin(age / 17f) * (1 - age / 220f) * 2f else 0f
    }
    fun progress(now: Float) = ((now - start) / duration).coerceIn(0f, 1f)
    fun begin(value: Kind, now: Float, defense: Boolean = false) {
        defending = defense
        kind = value
        start = now
        duration = if (kind == Kind.CIRCLE || kind == Kind.TRIANGLE) 2600f else 2100f
        active = true
        grade = Grade.MISS
        step = 0
        firstGrade = Grade.PERFECT
        pointer = -1
        points.clear()
        targetRadius = listOf(16f, 21f, 26f).random()
        if (kind == Kind.RIGHT) swipeDirection = SwipeDirection.entries[nextSwipeDirection++ % SwipeDirection.entries.size]
        resolvedAt = -10000f
    }
    fun update(now: Float) {
        if (active && progress(now) >= 1f) finish(Grade.MISS, now)
    }
    private fun finish(value: Grade, now: Float) {
        grade = value
        active = false
        resolvedAt = now
        pointer = -1
        points.clear()
    }
    private fun timing(now: Float, target: Float): Grade {
        val error = abs(progress(now) - target)
        return when { error <= .035f -> Grade.PERFECT; error <= .095f -> Grade.GOOD; else -> Grade.MISS }
    }
    fun touch(event: MotionEvent, now: Float) {
        if (!active) return
        val density = context.resources.displayMetrics.density
        val x = event.x / density
        val y = event.y / density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(0)
                points.clear()
                points += x to y
                downAt = now
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                // Multitouch et interruptions ne peuvent pas valider plusieurs étapes à la fois.
                pointer = -1
                points.clear()
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (pointer == -1 || event.getPointerId(0) != pointer) return
                for (i in 0 until event.historySize) add(event.getHistoricalX(i) / density, event.getHistoricalY(i) / density)
                add(x, y)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val tap = kind == Kind.TAP || kind == Kind.DOUBLE_TAP
                    val valid = shapeMatches() && (!tap || now - downAt <= 260f)
                    val target = if (kind == Kind.DOUBLE_TAP) (if (step == 0) .65f else .81f) else .72f
                    val value = if (valid) timing(if (tap) downAt else now, target) else Grade.MISS
                    pointer = -1
                    points.clear()
                    if (kind == Kind.DOUBLE_TAP && step == 0 && value != Grade.MISS) {
                        firstGrade = value
                        step = 1
                    } else finish(if (value.ordinal < firstGrade.ordinal) value else firstGrade, now)
                }
            }
        }
    }
    private fun add(x: Float, y: Float) {
        val last = points.lastOrNull()
        if (last == null || hypot(x-last.first, y-last.second) >= 2f) points += x to y
    }
    private fun shapeMatches(): Boolean {
        if (points.isEmpty()) return false
        val first = points.first()
        val last = points.last()
        val dx = last.first - first.first
        val dy = last.second - first.second
        val width = points.maxOf { it.first } - points.minOf { it.first }
        val height = points.maxOf { it.second } - points.minOf { it.second }
        val length = points.zipWithNext().sumOf { (a,b) -> hypot(b.first-a.first,b.second-a.second).toDouble() }.toFloat()
        return when (kind) {
            Kind.TAP, Kind.DOUBLE_TAP -> width < 18 && height < 18
            Kind.RIGHT -> {
                val travel = dx * swipeDirection.dx + dy * swipeDirection.dy
                val sideways = abs(dx * swipeDirection.dy - dy * swipeDirection.dx)
                travel >= 42 && sideways < travel * .55f && length < travel * 1.6f
            }
            Kind.RETURN -> width >= 50 && height < width * .5f && abs(dx) < width * .35f &&
                points.maxOf { it.first } - first.first >= width * .8f && length >= width * 1.6f
            Kind.CIRCLE, Kind.TRIANGLE -> {
                if (width < 45 || height < 45 || points.size < 8 || hypot(dx,dy) > max(width,height) * .4f) false
                else {
                    // Cercle : rayon régulier et un tour complet. Triangle : trois côtés droits,
                    // comparés aux trois sommets dominants, indépendamment de sa rotation.
                    val cx = (points.maxOf { it.first } + points.minOf { it.first }) / 2
                    val cy = (points.maxOf { it.second } + points.minOf { it.second }) / 2
                    val radii = points.map { hypot(it.first-cx,it.second-cy) }
                    val mean = radii.average().toFloat()
                    var angle = 0f
                    for ((a,b) in points.zipWithNext()) {
                        var d = atan2(b.second-cy,b.first-cx)-atan2(a.second-cy,a.first-cx)
                        while (d > PI) d -= (2*PI).toFloat()
                        while (d < -PI) d += (2*PI).toFloat()
                        angle += d
                    }
                    if (abs(angle) < 5f || abs(angle) > 7.5f) false
                    else if (kind == Kind.CIRCLE) radii.all { it in mean*.65f..mean*1.35f } && length < mean*8f
                    else {
                        val a = points.maxBy { hypot(it.first-cx,it.second-cy) }
                        val b = points.maxBy { hypot(it.first-a.first,it.second-a.second) }
                        fun distance(p: Pair<Float,Float>, u: Pair<Float,Float>, v: Pair<Float,Float>): Float {
                            val vx=v.first-u.first; val vy=v.second-u.second
                            val t=((p.first-u.first)*vx+(p.second-u.second)*vy)/(vx*vx+vy*vy).coerceAtLeast(1f)
                            return hypot(p.first-u.first-t.coerceIn(0f,1f)*vx,p.second-u.second-t.coerceIn(0f,1f)*vy)
                        }
                        val c = points.maxBy { distance(it,a,b) }
                        val tolerance=max(width,height)*.13f
                        points.count { minOf(distance(it,a,b),distance(it,b,c),distance(it,c,a)) <= tolerance } >= points.size*.9f
                    }
                }
            }
        }
    }
    fun draw(canvas: Canvas, now: Float, width: Float, height: Float, heroX: Float, heroY: Float) {
        val feedback = (now - resolvedAt) / 650f
        if (!active && feedback !in 0f..1f) return
        // Comme dans CombatView : la parade entoure le héros, sans panneau ni fond.
        val cx = if (defending) heroX else width * .5f
        val cy = if (defending) heroY else height * .54f
        val p = progress(now)
        val target = if (kind == Kind.DOUBLE_TAP) (if (step == 0) .65f else .81f) else .72f
        val color = if (active) {
            if (abs(p - target) <= .035f) good else if (defending) bad else blue
        } else if (grade == Grade.MISS) bad else good

        // La frappe de la démo emploie maintenant la même idée que le combat réel :
        // un curseur traverse la fenêtre, et la flèche impose le sens du swipe.
        if (kind == Kind.RIGHT && active) {
            val horizontal = swipeDirection.dy != 0
            val bar = if (horizontal) android.graphics.RectF(width * .15f, height * .55f, width * .85f, height * .55f + 8f)
            else android.graphics.RectF(width * .5f - 4f, height * .31f, width * .5f + 4f, height * .79f)
            paint.style = Paint.Style.FILL
            paint.color = 0xFF263238.toInt(); canvas.drawRoundRect(bar, 4f, 4f, paint)
            fun along(at: Float) = if (horizontal) bar.left + bar.width() * at else bar.top + bar.height() * at
            val goodL = along(.625f); val goodR = along(.815f)
            paint.color = good
            if (horizontal) canvas.drawRoundRect(goodL, bar.top, goodR, bar.bottom, 4f, 4f, paint)
            else canvas.drawRoundRect(bar.left, goodL, bar.right, goodR, 4f, 4f, paint)
            val perfectL = along(.685f); val perfectR = along(.755f)
            paint.color = bad
            if (horizontal) canvas.drawRoundRect(perfectL, bar.top - 1f, perfectR, bar.bottom + 1f, 4f, 4f, paint)
            else canvas.drawRoundRect(bar.left - 1f, perfectL, bar.right + 1f, perfectR, 4f, 4f, paint)
            val cursor = along(p)
            paint.color = white
            if (horizontal) canvas.drawRect(cursor - 1.5f, bar.top - 4f, cursor + 1.5f, bar.bottom + 4f, paint)
            else canvas.drawRect(bar.left - 4f, cursor - 1.5f, bar.right + 4f, cursor + 1.5f, paint)
            val arrowX = if (horizontal) along(.72f) else bar.centerX()
            val arrowY = if (horizontal) bar.centerY() else along(.72f)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.strokeCap = Paint.Cap.ROUND; paint.color = good
            val dx = swipeDirection.dx.toFloat(); val dy = swipeDirection.dy.toFloat()
            canvas.drawLine(arrowX - dx * 12f, arrowY - dy * 12f, arrowX + dx * 12f, arrowY + dy * 12f, paint)
            canvas.drawLine(arrowX + dx * 12f, arrowY + dy * 12f, arrowX + dx * 5f - dy * 6f, arrowY + dy * 5f + dx * 6f, paint)
            canvas.drawLine(arrowX + dx * 12f, arrowY + dy * 12f, arrowX + dx * 5f + dy * 6f, arrowY + dy * 5f - dx * 6f, paint)
            paint.strokeCap = Paint.Cap.BUTT; paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 6f
            canvas.drawText(swipeInstruction, width / 2f, height - 12f, paint)
            return
        }

        fun outline(radius: Float, tint: Int, thickness: Float) {
            path.reset()
            when (kind) {
                Kind.TAP, Kind.DOUBLE_TAP, Kind.CIRCLE -> path.addCircle(cx, cy, radius, Path.Direction.CW)
                Kind.TRIANGLE -> {
                    path.moveTo(cx, cy - radius)
                    path.lineTo(cx + radius * .87f, cy + radius * .5f)
                    path.lineTo(cx - radius * .87f, cy + radius * .5f)
                    path.close()
                }
                Kind.RIGHT, Kind.RETURN -> {
                    val y = if (kind == Kind.RETURN) cy - radius * .3f else cy
                    path.moveTo(cx - radius, y)
                    path.lineTo(cx + radius, y)
                    path.moveTo(cx + radius * .55f, y - radius * .45f)
                    path.lineTo(cx + radius, y)
                    path.lineTo(cx + radius * .55f, y + radius * .45f)
                    if (kind == Kind.RETURN) {
                        val bottom = cy + radius * .4f
                        path.moveTo(cx + radius, bottom)
                        path.lineTo(cx - radius, bottom)
                        path.moveTo(cx - radius * .55f, bottom - radius * .45f)
                        path.lineTo(cx - radius, bottom)
                        path.lineTo(cx - radius * .55f, bottom + radius * .45f)
                    }
                }
            }
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            // Fin liseré sombre pour rester lisible sur les pierres, intérieur transparent.
            paint.color = 0x70111729
            paint.strokeWidth = thickness + 1.5f
            canvas.drawPath(path, paint)
            paint.color = tint
            paint.strokeWidth = thickness
            canvas.drawPath(path, paint)
        }

        val base = if (kind == Kind.TAP || kind == Kind.DOUBLE_TAP) targetRadius else 21f
        fun radius(t: Float) = (base + (.72f - t) * 32f).coerceAtLeast(5f)
        if (active) {
            // La silhouette fixe EST la cible de timing ; aucune jauge supplémentaire.
            if (kind == Kind.DOUBLE_TAP) {
                outline(radius(.65f), if (step > 0) good else 0x90FFFFFF.toInt(), 1f)
                outline(radius(.81f), 0x90FFFFFF.toInt(), 1f)
            } else outline(base, 0x90FFFFFF.toInt(), 1f)
            outline(radius(p), color, 1.8f)
        } else {
            val alpha = ((1 - feedback) * 220).toInt().coerceIn(0, 220)
            val fading = (color and 0x00FFFFFF) or (alpha shl 24)
            outline(base + feedback * 10, fading, 1.5f)
            paint.style = Paint.Style.FILL
            paint.color = fading
            for (i in 0..9) {
                val angle = i * PI / 5
                val r = base + feedback * 22
                val x = cx + cos(angle).toFloat() * r
                val y = cy + sin(angle).toFloat() * r
                canvas.drawRect(x, y, x + 1.2f, y + 1.2f, paint)
            }
        }
        // Une consigne courte, posée directement sur le décor. Aucun cartouche.
        val label = if (active) messages[kind.ordinal] else result
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (active) 6f else 7f
        val maxWidth = width - 20
        if (paint.measureText(label) > maxWidth) paint.textSize *= maxWidth / paint.measureText(label)
        val labelX = if (active) width / 2 else cx.coerceIn(35f, width - 35)
        val labelY = if (active) height - 12f else cy + base + 15f
        paint.color = 0xCC111729.toInt()
        canvas.drawText(label, labelX + .5f, labelY + .5f, paint)
        paint.color = if (active) white else color
        canvas.drawText(label, labelX, labelY, paint)
    }
}
