package com.Atom2Universe.app.games.starswar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Atelier de dessin de Space Fight. Aucun bitmap : les silhouettes et les dégradés
 * sont préparés une fois, puis articulés dans leur repère local par Canvas.
 * Une instance appartient exclusivement au fil de jeu. Les effets n'interviennent
 * jamais dans les collisions et leur réserve est bornée, même lors d'une grosse salve.
 */
internal class SpaceFightArt {
    companion object {
        val INK = Color.rgb(20, 28, 49)
        val PANEL = Color.rgb(29, 43, 65)
        val EDGE = Color.rgb(69, 89, 112)
        val IVORY = Color.rgb(246, 243, 231)
        val MINT = Color.rgb(167, 232, 209)
        val LILAC = Color.rgb(199, 183, 242)
        val CORAL = Color.rgb(255, 176, 158)
        val GOLD = Color.rgb(245, 217, 160)
        val MUTED = Color.rgb(179, 198, 217)
        fun alpha(color: Int, alpha: Int) = (color and 0x00ffffff) or (alpha.coerceIn(0, 255) shl 24)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val rect = RectF()
    private val path = Path()
    private val ceramic = LinearGradient(-22f, -30f, 22f, 28f, IVORY, Color.rgb(140, 168, 184), Shader.TileMode.CLAMP)
    private val mint = LinearGradient(-24f, -22f, 22f, 25f, Color.rgb(219, 252, 234), Color.rgb(101, 161, 169), Shader.TileMode.CLAMP)
    private val lavender = LinearGradient(-24f, -25f, 24f, 27f, Color.rgb(234, 222, 252), Color.rgb(144, 129, 178), Shader.TileMode.CLAMP)
    private val apricot = LinearGradient(-24f, -25f, 24f, 27f, Color.rgb(255, 235, 201), Color.rgb(185, 130, 147), Shader.TileMode.CLAMP)
    private val visor = LinearGradient(-14f, -14f, 14f, 10f, Color.rgb(71, 106, 120), Color.rgb(29, 48, 71), Shader.TileMode.CLAMP)
    private val core = RadialGradient(-2f, -3f, 11f, IVORY, CORAL, Shader.TileMode.CLAMP)
    private val ion = LinearGradient(0f, 0f, 0f, 28f, MINT, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    private val sectorGradients = arrayOf(
        LinearGradient(0f, 0f, 480f, 720f, INK, Color.rgb(28, 49, 65), Shader.TileMode.CLAMP),
        LinearGradient(0f, 0f, 480f, 720f, Color.rgb(25, 27, 50), Color.rgb(46, 48, 74), Shader.TileMode.CLAMP),
        LinearGradient(0f, 0f, 480f, 720f, Color.rgb(22, 31, 49), Color.rgb(32, 60, 71), Shader.TileMode.CLAMP)
    )
    private val planetShader = LinearGradient(-100f, -100f, 90f, 90f, Color.rgb(60, 81, 98), Color.rgb(29, 43, 63), Shader.TileMode.CLAMP)
    private val glows = intArrayOf(MINT, LILAC, CORAL, GOLD).map { color ->
        RadialGradient(0f, 0f, 1f, intArrayOf(alpha(color, 100), alpha(color, 25), alpha(color, 0)), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
    }.toTypedArray()
    private val hull = Path().apply {
        moveTo(0f, -29f); cubicTo(-13f, -29f, -20f, -12f, -20f, 4f)
        lineTo(-15f, 20f); quadTo(0f, 29f, 15f, 20f); lineTo(20f, 4f)
        cubicTo(20f, -12f, 13f, -29f, 0f, -29f); close()
    }
    private val wing = Path().apply {
        moveTo(11f, -10f); cubicTo(23f, -9f, 32f, 4f, 30f, 19f)
        quadTo(28f, 24f, 22f, 19f); lineTo(8f, 12f); close()
    }
    private val swift = Path().apply {
        moveTo(0f, 23f); quadTo(-4f, 7f, -24f, -15f); quadTo(-18f, -19f, -9f, -12f)
        quadTo(0f, -24f, 9f, -12f); quadTo(18f, -19f, 24f, -15f); quadTo(4f, 7f, 0f, 23f); close()
    }
    private val tank = Path().apply {
        moveTo(-18f, -17f); quadTo(0f, -25f, 18f, -17f); lineTo(24f, -2f)
        lineTo(19f, 18f); quadTo(0f, 26f, -19f, 18f); lineTo(-24f, -2f); close()
    }
    private val prism = Path().apply {
        moveTo(0f, -26f); quadTo(13f, -9f, 15f, 2f); quadTo(9f, 18f, 0f, 27f)
        quadTo(-9f, 18f, -15f, 2f); quadTo(-13f, -9f, 0f, -26f); close()
    }
    private val spark = Path().apply {
        moveTo(0f, -1f); quadTo(.18f, -.18f, 1f, 0f); quadTo(.18f, .18f, 0f, 1f)
        quadTo(-.18f, .18f, -1f, 0f); quadTo(-.18f, -.18f, 0f, -1f); close()
    }

    var time = 0f
        private set
    private val random = Random(0x5FACED) // Ne modifie pas le hasard des vagues/améliorations.
    private val particleX = FloatArray(240)
    private val particleY = FloatArray(240)
    private val velocityX = FloatArray(240)
    private val velocityY = FloatArray(240)
    private val life = FloatArray(240)
    private val duration = FloatArray(240)
    private val sizes = FloatArray(240)
    private val colors = IntArray(240)
    private val kinds = IntArray(240)
    private var cursor = 0
    private var trailClock = 0f
    private var novaAge = 2f
    private var novaX = 0f
    private var novaY = 0f
    var hullImpact = 0f
        private set
    var shieldImpact = 0f
        private set
    var repairPulse = 0f
        private set
    var shotKick = 0f
        private set

    fun update(dt: Float) {
        time = (time + dt) % 3600f
        novaAge += dt
        hullImpact = (hullImpact - dt).coerceAtLeast(0f)
        shieldImpact = (shieldImpact - dt).coerceAtLeast(0f)
        repairPulse = (repairPulse - dt).coerceAtLeast(0f)
        shotKick = (shotKick - dt * 8f).coerceAtLeast(0f)
        for (i in life.indices) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            particleX[i] += velocityX[i] * dt
            particleY[i] += velocityY[i] * dt
            velocityX[i] *= 1f - dt * 1.4f
            velocityY[i] *= 1f - dt * 1.4f
        }
    }

    fun clear() { life.fill(0f); novaAge = 2f; hullImpact = 0f; shieldImpact = 0f; repairPulse = 0f; shotKick = 0f; trailClock = 0f }

    private fun particle(x: Float, y: Float, vx: Float, vy: Float, size: Float, color: Int, seconds: Float, kind: Int) {
        val i = cursor
        cursor = (cursor + 1) % life.size
        particleX[i] = x; particleY[i] = y; velocityX[i] = vx; velocityY[i] = vy
        sizes[i] = size; colors[i] = color; life[i] = seconds; duration[i] = seconds; kinds[i] = kind
    }

    fun burst(x: Float, y: Float, color: Int, boss: Boolean = false) {
        repeat(if (boss) 48 else 14) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = (30f + random.nextFloat() * 85f) * if (boss) 1.5f else 1f
            particle(x, y, cos(angle) * speed, sin(angle) * speed, 1.2f + random.nextFloat() * 3.2f,
                if (it % 3 == 0) IVORY else color, .35f + random.nextFloat() * .65f, it % 3)
        }
        particle(x, y, 0f, 0f, if (boss) 85f else 29f, color, if (boss) .85f else .4f, 3)
    }

    fun hit(x: Float, y: Float) {
        repeat(3) { particle(x, y, (random.nextFloat() - .5f) * 80f, 20f + random.nextFloat() * 35f, 1.5f, GOLD, .22f, 0) }
    }

    fun playerHit(x: Float, y: Float, shield: Boolean) {
        if (shield) shieldImpact = .55f else hullImpact = .6f
        burst(x, y, if (shield) MINT else CORAL)
    }

    fun repair(x: Float, y: Float) {
        repairPulse = .9f
        repeat(12) { val a = it * PI.toFloat() / 6f
            particle(x + cos(a) * 22f, y + sin(a) * 22f, cos(a) * 12f, sin(a) * 12f - 20f, 2.3f, MINT, .7f, 1)
        }
    }

    fun fire() { shotKick = 1f }

    fun nova(x: Float, y: Float) { novaX = x; novaY = y; novaAge = 0f }

    fun trail(dt: Float, x: Float, y: Float) {
        trailClock += dt
        if (trailClock < .045f) return
        trailClock = 0f
        particle(x + (random.nextFloat() - .5f) * 8f, y + 17f, 0f, 25f, 1.4f, MINT, .4f, 0)
    }

    private fun fill(color: Int, shader: Shader? = null) {
        paint.style = Paint.Style.FILL; paint.alpha = 255; paint.color = color; paint.shader = shader
    }
    fun ellipse(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, shader: Shader? = null) {
        fill(color, shader); c.drawOval(x - rx, y - ry, x + rx, y + ry, paint)
    }
    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 1.5f) {
        fill(color); paint.strokeWidth = width; c.drawLine(x1, y1, x2, y2, paint)
    }
    fun arc(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, start: Float, sweep: Float, color: Int, width: Float = 1.5f) {
        fill(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = width
        rect.set(x - rx, y - ry, x + rx, y + ry); c.drawArc(rect, start, sweep, false, paint)
    }
    fun panel(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int = PANEL, radius: Float = 18f, border: Int = EDGE) {
        fill(color); c.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        if (border != Color.TRANSPARENT) {
            paint.color = border; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
            c.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    }
    private fun silhouette(c: Canvas, p: Path, shader: Shader) {
        fill(IVORY, shader); c.drawPath(p, paint)
        fill(alpha(INK, 190)); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; c.drawPath(p, paint)
    }
    private fun pearl(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, shader: Shader = ceramic) {
        ellipse(c, x, y + 2f, rx, ry, alpha(INK, 150))
        ellipse(c, x, y, rx, ry, IVORY, shader)
        arc(c, x, y, rx * .84f, ry * .83f, 211f, 66f, alpha(IVORY, 180), 1.4f)
    }
    fun sparkle(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        c.save(); c.translate(x, y); c.scale(size, size); fill(color); c.drawPath(spark, paint); c.restore()
    }
    private fun glow(c: Canvas, x: Float, y: Float, r: Float, kind: Int, opacity: Int = 255) {
        if (r <= 0f) return
        c.save(); c.translate(x, y); c.scale(r, r); fill(Color.WHITE, glows[kind]); paint.alpha = opacity.coerceIn(0, 255)
        c.drawCircle(0f, 0f, 1f, paint); c.restore(); paint.shader = null
    }
    private fun eye(c: Canvas, x: Float, y: Float, r: Float, charge: Float = 0f) {
        if (charge > .02f) glow(c, x, y, r * (3f + charge * 2f), 2, (80f + charge * 130f).toInt())
        ellipse(c, x, y, r * 1.6f, r * 1.6f, INK)
        c.save(); c.translate(x, y); c.scale(r / 5f, r / 5f)
        ellipse(c, 0f, 0f, 5f, 5f, IVORY, core); ellipse(c, -1.5f, -1.5f, 1.2f, 1.2f, IVORY); c.restore()
    }
    private fun engine(c: Canvas, x: Float, y: Float, amount: Float) {
        c.save(); c.translate(x, y); c.scale(1f, .6f + amount * .4f + sin(time * 21f) * .08f)
        path.rewind(); path.moveTo(-4f, 0f); path.quadTo(-3f, 17f, 0f, 27f); path.quadTo(3f, 17f, 4f, 0f); path.close()
        fill(IVORY, ion); c.drawPath(path, paint); ellipse(c, 0f, 2f, 2f, 5f, IVORY); c.restore()
    }

    fun background(c: Canvas, sector: Int, meteor: Boolean = false) {
        fill(INK, sectorGradients[sector % sectorGradients.size]); c.drawRect(0f, 0f, 480f, 720f, paint)
        glow(c, 430f, 275f, 250f, sector % 3, 24)
        glow(c, 12f, 410f, 190f, 1, 17)
        c.save(); c.translate(438f, 235f + sin(time * .07f) * 6f)
        c.rotate(-27f); arc(c, 0f, 0f, 150f, 52f, 0f, 360f, alpha(MUTED, 16), 1f)
        ellipse(c, 0f, 0f, 83f, 83f, IVORY, planetShader)
        arc(c, 0f, 0f, 81f, 81f, 200f, 75f, alpha(MINT, 28), 1f)
        arc(c, 0f, 0f, 150f, 52f, 0f, 180f, alpha(MUTED, 23), 1f); c.restore()
        if (meteor) {
            for (i in 0..10) {
                val y = (i * 83f + time * 90f) % 800f - 40f
                line(c, (i * 137f) % 480f, y - 22f, (i * 137f) % 480f + 3f, y, alpha(MINT, 25), 1f)
            }
        }
        for (i in 0..8) {
            val y = 80f + i * 72f
            line(c, 9f, y, 13f, y, alpha(MUTED, 45), 1f)
            line(c, 467f, y, 471f, y, alpha(MUTED, 45), 1f)
        }
    }

    fun player(c: Canvas, x: Float, y: Float, size: Float, bank: Float = 0f, firing: Boolean = false) {
        c.save(); c.translate(x, y); c.rotate(bank); c.scale(size / 60f, size / 60f)
        engine(c, -11f, 19f, if (firing) 1f else .4f); engine(c, 11f, 19f, if (firing) 1f else .4f)
        c.save(); c.rotate(sin(time * 2f) * 2f); silhouette(c, wing, mint); c.restore()
        c.save(); c.scale(-1f, 1f); c.rotate(sin(time * 2f) * 2f); silhouette(c, wing, mint); c.restore()
        c.translate(0f, if (firing) shotKick * 1.8f else 0f)
        silhouette(c, hull, ceramic)
        arc(c, 0f, -3f, 17f, 22f, 204f, 58f, alpha(IVORY, 220), 2f)
        ellipse(c, 0f, -5f, 13.5f, 11f, IVORY, visor)
        arc(c, 0f, -5f, 13.5f, 11f, 186f, 154f, alpha(MINT, 170), 1.2f)
        val blink = time % 5.7f > 5.53f
        for (side in -1..1 step 2) {
            val ex = side * 5f
            if (blink) line(c, ex - 1.5f, -5f, ex + 1.5f, -5f, MINT, 1.5f)
            else panel(c, ex - 1.3f, -8f, ex + 1.3f, -3f, MINT, 1.3f, Color.TRANSPARENT)
            ellipse(c, side * 9f, 0f, 2f, 1.1f, alpha(CORAL, 100))
        }
        arc(c, 0f, -1f, 3.5f, 3f, 20f, 140f, MINT, 1f)
        panel(c, -6f, 12f, 6f, 16f, Color.rgb(102, 160, 162), 2f, Color.TRANSPARENT)
        ellipse(c, 0f, 14f, 1.3f, 1.3f, IVORY)
        line(c, 0f, -29f, 0f, -35f, MINT, 1.4f)
        ellipse(c, 0f, -35f, 2.4f, 2.4f, GOLD)
        if (hullImpact > 0f && firing) arc(c, 0f, 0f, 23f, 26f, 20f, 140f, alpha(CORAL, (hullImpact * 300).toInt()), 2f)
        c.restore()
    }

    fun satellite(c: Canvas, x: Float, y: Float, size: Float, alert: Boolean = false) {
        c.save(); c.translate(x, y); c.scale(size / 44f, size / 44f); c.rotate(sin(time * 2f) * 5f)
        line(c, -17f, 0f, 17f, 0f, MUTED, 3f)
        for (side in -1..1 step 2) {
            c.save(); c.scale(side.toFloat(), 1f)
            panel(c, 12f, -8f, 22f, 9f, MINT, 3f)
            line(c, 15f, -3f, 19f, -3f, alpha(IVORY, 160), 1f)
            line(c, 15f, 3f, 19f, 3f, alpha(IVORY, 160), 1f); c.restore()
        }
        pearl(c, 0f, 0f, 10f, 12f)
        ellipse(c, 0f, -1f, 5f, 5f, INK)
        ellipse(c, 0f, -1f, 2.8f, if (alert) 3.6f else 2.8f, if (alert) GOLD else MINT)
        c.restore()
    }

    fun enemy(c: Canvas, type: Int, x: Float, y: Float, size: Float, phase: Float, charge: Float, hit: Float) {
        c.save(); c.translate(x, y); c.rotate(sin(phase * 1.3f) * 5f); c.scale(size / 54f, size / 54f)
        when (type) {
            0 -> {
                c.save(); c.rotate(time * 17f + phase * 10f)
                repeat(3) { c.rotate(120f); pearl(c, 0f, -15f, 7f, 10f, lavender) }; c.restore()
                pearl(c, 0f, 0f, 13f, 12f); eye(c, 0f, 1f, 4f)
            }
            1 -> {
                silhouette(c, swift, lavender)
                line(c, -15f, -10f, -3f, -1f, IVORY); line(c, 15f, -10f, 3f, -1f, IVORY)
                eye(c, 0f, 3f, 3.8f)
            }
            2 -> {
                line(c, -21f, 0f, 21f, 0f, MUTED, 4f)
                for (side in -1..1 step 2) {
                    val ex = side * 18f
                    pearl(c, ex, charge * 3f, 6f, 14f, lavender)
                    panel(c, ex - 3f, 12f + charge * 3f, ex + 3f, 18f + charge * 3f, CORAL, 2f, Color.TRANSPARENT)
                }
                pearl(c, 0f, -3f, 13f, 16f); eye(c, 0f, 0f, 4f, charge)
            }
            3 -> {
                silhouette(c, tank, lavender); pearl(c, 0f, -2f, 17f, 18f)
                line(c, -22f, -4f, -16f, -4f, LILAC, 3f); line(c, 22f, -4f, 16f, -4f, LILAC, 3f)
                eye(c, 0f, 1f, 5f, charge); panel(c, -7f, 14f, 7f, 17f, EDGE, 2f, Color.TRANSPARENT)
            }
            4 -> {
                repeat(4) {
                    c.save(); c.rotate(it * 90f + sin(time * 4f + phase) * 4f)
                    path.rewind(); path.moveTo(-6f, -8f); path.lineTo(0f, -24f); path.lineTo(6f, -8f); path.close()
                    silhouette(c, path, apricot); c.restore()
                }
                pearl(c, 0f, 0f, 12f, 12f, apricot); eye(c, 0f, 1f, 4.5f, .3f + sin(time * 6f) * .15f)
            }
            5 -> {
                silhouette(c, prism, lavender); pearl(c, 0f, -1f, 8f, 17f)
                eye(c, 0f, 0f, 4f, charge); line(c, 0f, 13f, 0f, 25f, GOLD, 2f)
            }
            else -> {
                pearl(c, 0f, 0f, 24f, 15f, lavender); pearl(c, 0f, -4f, 14f, 13f)
                panel(c, -11f, 10f, 11f, 17f, INK, 3f, Color.TRANSPARENT)
                for (i in -1..1) ellipse(c, i * 7f, 13f, 2f, 2f, if (sin(time * 3f + i) > 0f) CORAL else GOLD)
                eye(c, 0f, -2f, 4f, charge)
            }
        }
        if (hit > 0f) {
            arc(c, 0f, 0f, 21f, 22f, -30f, 190f, alpha(IVORY, (hit / .16f * 210f).toInt()), 2.5f)
            sparkle(c, 12f, -9f, 4f, alpha(GOLD, (hit / .16f * 220f).toInt()))
        }
        c.restore()
    }

    fun boss(c: Canvas, type: Int, x: Float, y: Float, size: Float, charge: Float, hit: Float) {
        val ringBoss = type == 8
        c.save(); c.translate(x, y); c.scale(size / 110f, size / 110f)
        glow(c, 0f, 0f, 65f, if (ringBoss) 1 else 3, 80)
        if (ringBoss) {
            c.save(); c.rotate(-22f + sin(time * .7f) * 5f)
            arc(c, 0f, 0f, 54f, 21f, 0f, 360f, EDGE, 7f)
            arc(c, 0f, -1f, 54f, 21f, 0f, 360f, LILAC, 2f); c.restore()
        }
        repeat(if (ringBoss) 4 else 6) {
            c.save(); c.rotate(it * (if (ringBoss) 90f else 60f) + if (ringBoss) 45f else sin(time * .45f) * 14f)
            val spread = 42f + sin(time * 1.6f + it) * 2f + charge * 4f
            line(c, 0f, -22f, 0f, -spread, MUTED, 4f)
            pearl(c, 0f, -spread, if (ringBoss) 10f else 8f, 13f, if (ringBoss) lavender else apricot)
            eye(c, 0f, -spread, 3f, charge); c.restore()
        }
        pearl(c, 0f, 0f, 32f, 31f)
        ellipse(c, 0f, 0f, 24f, 24f, EDGE)
        pearl(c, 0f, 0f, 21f, 22f, if (ringBoss) lavender else apricot)
        arc(c, 0f, 0f, 18f, 18f, time * 22f, 275f, IVORY)
        eye(c, 0f, 0f, 9f, charge)
        if (ringBoss) {
            c.save(); c.rotate(-22f + sin(time * .7f) * 5f)
            arc(c, 0f, 0f, 54f, 21f, 0f, 180f, LILAC, 6f)
            arc(c, 0f, -2f, 54f, 21f, 6f, 168f, IVORY, 1.3f)
            pearl(c, cos(time * .6f) * 54f, sin(time * .6f) * 21f, 4f, 4f, apricot); c.restore()
        } else {
            arc(c, 0f, 0f, 35f, 35f, -time * 20f, 130f, GOLD, 2f)
            arc(c, 0f, 0f, 35f, 35f, 180f - time * 20f, 130f, GOLD, 2f)
        }
        if (hit > 0f) arc(c, 0f, 0f, 32f, 32f, 0f, 360f, alpha(IVORY, (hit / .16f * 200f).toInt()), 3f)
        c.restore()
    }

    fun photon(c: Canvas, x: Float, y: Float, dx: Float = 0f, dy: Float = -1f, power: Int = 1, pierce: Boolean = false, drone: Boolean = false) {
        c.save(); c.translate(x, y); c.rotate(Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat() + 90f)
        val width = if (drone) 2f else 2.7f + power.coerceAtMost(8) * .13f
        val length = if (drone) 6f else if (pierce) 16f else 10f
        line(c, 0f, -length, 0f, length * 1.7f, alpha(if (pierce) LILAC else MINT, 28), width * 3f)
        line(c, 0f, -length, 0f, length, if (pierce) LILAC else MINT, width)
        line(c, 0f, -length, 0f, 1f, IVORY, 1.2f)
        if (pierce) { line(c, -3.5f, 2f, 3.5f, 2f, alpha(IVORY, 160), 1f) }
        c.restore()
    }

    fun hostileBullet(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, missile: Boolean) {
        c.save(); c.translate(x, y); c.rotate(Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat() - 90f)
        line(c, 0f, -12f, 0f, 0f, alpha(CORAL, 45), 5f)
        if (missile) {
            path.rewind(); path.moveTo(0f, 8f); path.lineTo(-4f, 0f); path.lineTo(0f, -7f); path.lineTo(4f, 0f); path.close()
            fill(CORAL); c.drawPath(path, paint); line(c, 0f, -2f, 0f, 4f, IVORY, 1.4f)
        } else {
            ellipse(c, 0f, 0f, 3.2f, 5.8f, CORAL); ellipse(c, -.6f, 1f, 1.3f, 2.4f, IVORY)
        }
        c.restore()
    }

    fun rocket(c: Canvas, x: Float, y: Float, angle: Float) {
        c.save(); c.translate(x, y); c.rotate(angle)
        engine(c, 0f, 5f, 1f); pearl(c, 0f, 0f, 4.5f, 9f, apricot)
        line(c, -4f, 3f, -7f, 6f, GOLD, 2f); line(c, 4f, 3f, 7f, 6f, GOLD, 2f)
        ellipse(c, 0f, -4f, 2f, 2f, IVORY); c.restore()
    }

    fun meteor(c: Canvas, x: Float, y: Float, radius: Float, dx: Float) {
        c.save(); c.translate(x, y); c.rotate(-dx * 35f)
        line(c, 0f, -radius * 2.5f, 0f, -radius * .2f, alpha(MUTED, 18), radius * .85f)
        c.rotate(time * 17f + radius * 3f); c.scale(radius / 22f, radius / 22f)
        silhouette(c, tank, ceramic)
        ellipse(c, -6f, -5f, 6f, 4.5f, alpha(EDGE, 110)); arc(c, -6f, -5f, 6f, 4.5f, 0f, 150f, alpha(IVORY, 160))
        ellipse(c, 9f, 7f, 4f, 3f, alpha(EDGE, 110)); ellipse(c, 7f, -12f, 2f, 2f, alpha(EDGE, 90))
        c.restore()
    }

    fun shield(c: Canvas, x: Float, y: Float, charges: Int) {
        if (charges <= 0 && shieldImpact <= 0f) return
        val r = 27f + shieldImpact * 10f
        ellipse(c, x, y, r, r, alpha(MINT, 10))
        arc(c, x, y, r, r, 0f, 360f, alpha(MINT, 65), 1f)
        repeat(charges) { arc(c, x, y, r, r, -110f + it * 120f + sin(time) * 5f, 62f, alpha(IVORY, 190), 1.4f) }
        if (shieldImpact > 0f) arc(c, x, y, r, r, 0f, 360f, alpha(MINT, (shieldImpact * 350f).toInt()), 2.5f)
    }

    fun magnetic(c: Canvas, x: Float, y: Float, radius: Float, remaining: Float) {
        val strength = (remaining / .4f).coerceIn(0f, 1f)
        repeat(3) { i ->
            c.save(); c.translate(x, y); c.rotate(time * 24f + i * 60f)
            arc(c, 0f, 0f, radius, radius * .48f, 0f, 360f, alpha(LILAC, (strength * 65f).toInt()), 1.1f)
            repeat(2) { j ->
                val a = time * 1.7f + i + j * PI.toFloat()
                ellipse(c, cos(a) * radius, sin(a) * radius * .48f, 2f, 2f, alpha(IVORY, (strength * 195f).toInt()))
            }; c.restore()
        }
        arc(c, x, y, radius, radius, -140f, 45f, alpha(MINT, (strength * 120f).toInt()), 1.5f)
        arc(c, x, y, radius, radius, 40f, 45f, alpha(MINT, (strength * 120f).toInt()), 1.5f)
    }

    fun effects(c: Canvas) {
        for (i in life.indices) {
            if (life[i] <= 0f) continue
            val ratio = (life[i] / duration[i]).coerceIn(0f, 1f)
            val color = alpha(colors[i], (ratio * 230f).toInt())
            val x = particleX[i]; val y = particleY[i]
            when (kinds[i]) {
                1 -> sparkle(c, x, y, sizes[i] * (.5f + ratio), color)
                2 -> { c.save(); c.rotate((1f - ratio) * 150f, x, y); panel(c, x - sizes[i], y - sizes[i] * .5f, x + sizes[i], y + sizes[i] * .5f, color, 1f, Color.TRANSPARENT); c.restore() }
                3 -> { val r = sizes[i] * (1f - ratio); arc(c, x, y, r, r, 0f, 360f, color, ratio * 2f + .5f) }
                else -> ellipse(c, x, y, sizes[i] * ratio, sizes[i] * ratio, color)
            }
        }
        if (novaAge < .8f) {
            val p = (novaAge / .55f).coerceAtMost(1f)
            val r = 200f * (1f - (1f - p) * (1f - p))
            val a = ((1f - novaAge / .8f) * 210f).toInt()
            arc(c, novaX, novaY, r, r, 0f, 360f, alpha(GOLD, a), 2.5f)
            arc(c, novaX, novaY, r * .92f, r * .92f, 0f, 360f, alpha(LILAC, a / 2), 1f)
            repeat(24) { val angle = it * PI.toFloat() / 12f
                sparkle(c, novaX + cos(angle) * r, novaY + sin(angle) * r, 2.7f, alpha(IVORY, a))
            }
        }
    }

    fun repairHalo(c: Canvas, x: Float, y: Float) {
        if (repairPulse <= 0f) return
        val r = 20f + (1f - repairPulse / .9f) * 25f
        arc(c, x, y, r, r, 0f, 360f, alpha(MINT, (repairPulse * 150f).toInt()))
    }

    fun upgrade(c: Canvas, id: Int, x: Float, y: Float, size: Float) {
        c.save(); c.translate(x, y); c.scale(size / 54f, size / 54f)
        when (id) {
            13 -> satellite(c, 0f, 0f, 45f)
            3 -> { pearl(c, 0f, 0f, 10f, 14f); shield(c, 0f, 0f, 3) }
            4, 5, 14 -> {
                pearl(c, 0f, 0f, 20f, 21f, if (id == 14) lavender else mint)
                line(c, -7f, 0f, 7f, 0f, IVORY, 4f); line(c, 0f, -7f, 0f, 7f, IVORY, 4f)
                if (id == 5) arc(c, 0f, 0f, 25f, 25f, -70f, 280f, MINT)
            }
            8, 9, 10 -> { rocket(c, 0f, -2f, 20f); if (id != 8) sparkle(c, 16f, -14f, 5f, GOLD) }
            11, 12 -> { magnetic(c, 0f, 0f, 24f, 1f); pearl(c, 0f, 0f, 6f, 6f, lavender) }
            15 -> { arc(c, 0f, 0f, 22f, 22f, 0f, 360f, GOLD); arc(c, 0f, 0f, 16f, 16f, time * 25f, 240f, LILAC); sparkle(c, 0f, 0f, 12f, IVORY) }
            else -> {
                c.rotate(15f)
                photon(c, -7f, 0f, power = if (id == 2) 8 else 1, pierce = id == 7)
                if (id == 1) photon(c, 7f, 0f)
                if (id == 0) { line(c, 6f, -8f, 12f, -13f, GOLD, 2f); line(c, 7f, 3f, 13f, -2f, GOLD, 2f) }
            }
        }
        c.restore()
    }
}
