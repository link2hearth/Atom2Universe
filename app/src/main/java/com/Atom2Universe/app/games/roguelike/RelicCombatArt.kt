package com.Atom2Universe.app.games.roguelike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.*

/** Vocabulaire commun des familles, silhouettes propres à chacune des 27 reliques.
 * Dessin en pixels logiques : aucune image ni particule allouée à chaque frame.
 */
internal class RelicCombatArt {
    private val paint = Paint()
    private var ink = 0
    private var light = 0
    private var opacity = 1f

    // L'éclair du geste Tracer : des segments réutilisés d'une image à l'autre
    private val boltPaint = Paint().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE }
    private val iconPaint = Paint().apply { isFilterBitmap = false }
    private val iconRect = RectF()
    private var segments = FloatArray(0)
    private val fork = FloatArray(8)
    private val tip = FloatArray(2)

    /**
     * L'éclair du geste Tracer : il suit les points tracés par le doigt ([path], [count] points
     * en pixels d'écran, de la tête près du héros jusqu'à la cible). Pendant le trajet il court
     * le long du tracé (40 % du temps) puis crépite ; à l'impact, flash et étincelles, et il
     * s'efface. Le marteau et la massue ([icon]) voyagent en tête de l'éclair en tournant.
     * [timing] règle la force : Parfait plus épais avec un flash de toute la scène, Raté fin.
     */
    fun drawTracedBolt(canvas: Canvas, scene: RectF, path: FloatArray, count: Int, relic: Relic,
        progress: Float, impact: Boolean, timing: Timing, pixel: Float, icon: Bitmap?) {
        if (count < 2 || pixel <= 0f) return
        palette(relic.element)
        val t = progress.coerceIn(0f, 1f)
        val power = when (timing) { Timing.PERFECT -> 1.4f; Timing.GOOD -> 1f; Timing.MISS -> .6f }
        val fade = if (impact) 1f - t else 1f
        val clock = SystemClock.uptimeMillis()
        canvas.save()
        canvas.clipRect(scene)
        val ex = path[count * 2 - 2]
        val ey = path[count * 2 - 1]
        // Le flash de toute la scène, au moment où l'éclair touche
        if (impact && timing != Timing.MISS && t < .3f) {
            paint.color = Color.WHITE
            paint.alpha = ((1f - t / .3f) * (if (timing == Timing.PERFECT) 110 else 45)).toInt()
            canvas.drawRect(scene, paint)
        }
        val floats = boltSegments(path, count, if (impact) 1f else (t / .4f).coerceAtMost(1f), clock, pixel)
        // Halo, puis cœur clair qui crépite, puis les fourches
        boltPaint.color = ink; boltPaint.alpha = (150 * fade).toInt()
        boltPaint.strokeWidth = 3.4f * pixel * power
        canvas.drawLines(segments, 0, floats, boltPaint)
        boltPaint.color = if ((clock / 45) % 3 == 0L) light else Color.WHITE
        boltPaint.alpha = (255 * fade).toInt()
        boltPaint.strokeWidth = 1.3f * pixel * power
        canvas.drawLines(segments, 0, floats, boltPaint)
        boltForks(canvas, floats, clock, pixel * power, fade)
        // L'arme lancée : en tête de l'éclair pendant le trajet, sur la cible à l'impact
        if (icon != null) {
            val size = 16f * pixel
            val x = if (impact) ex else tip[0]
            val y = if (impact) ey - size * .4f else tip[1]
            canvas.save()
            if (!impact) canvas.rotate((clock % 500) / 500f * 360f, x, y)
            iconRect.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
            iconPaint.alpha = (255 * fade).toInt()
            canvas.drawBitmap(icon, null, iconRect, iconPaint)
            canvas.restore()
        } else if (!impact) {
            dotAt(canvas, tip[0], tip[1], 2.6f * pixel * power, light)
        }
        // Les étincelles de l'impact, dans le repère pixel des autres sorts
        if (impact) {
            canvas.translate(scene.left, scene.top)
            canvas.scale(pixel, pixel)
            opacity = fade
            val x = (ex - scene.left) / pixel
            val y = (ey - scene.top) / pixel
            burst(canvas, x, y, t, if (timing == Timing.PERFECT) 22 else 12)
            if (icon != null) { bolt(canvas, x, y, x - 22, y + 12, t); bolt(canvas, x, y, x + 22, y + 12, t) }
            opacity = 1f
        }
        canvas.restore()
    }

    /**
     * Remplit [segments] avec l'éclair jusqu'à la fraction [reach] du tracé, chaque point
     * intérieur secoué un peu à chaque instant ; [tip] reçoit sa pointe. Rend le nombre de floats.
     */
    private fun boltSegments(path: FloatArray, count: Int, reach: Float, clock: Long, pixel: Float): Int {
        if (segments.size < (count - 1) * 4) segments = FloatArray((count - 1) * 4)
        val end = reach * (count - 1)
        val whole = floor(end).toInt().coerceAtMost(count - 1)
        val frame = (clock / 55).toInt()
        fun x(i: Int) = path[i * 2] + if (i == 0 || i == count - 1) 0f else sin(i * 12.9898f + frame * 7.13f) * 1.4f * pixel
        fun y(i: Int) = path[i * 2 + 1] + if (i == 0 || i == count - 1) 0f else cos(i * 4.1414f + frame * 3.71f) * 1.4f * pixel
        var n = 0
        for (i in 1..whole) {
            segments[n++] = x(i - 1); segments[n++] = y(i - 1); segments[n++] = x(i); segments[n++] = y(i)
        }
        tip[0] = x(whole); tip[1] = y(whole)
        if (whole < count - 1) {
            val k = end - whole
            val nx = x(whole) + (x(whole + 1) - x(whole)) * k
            val ny = y(whole) + (y(whole + 1) - y(whole)) * k
            segments[n++] = x(whole); segments[n++] = y(whole); segments[n++] = nx; segments[n++] = ny
            tip[0] = nx; tip[1] = ny
        }
        return n
    }

    /** De petites fourches tous les dix points, d'un côté puis de l'autre, qui changent sans cesse. */
    private fun boltForks(canvas: Canvas, floats: Int, clock: Long, pixel: Float, fade: Float) {
        boltPaint.color = light; boltPaint.alpha = (200 * fade).toInt()
        boltPaint.strokeWidth = pixel
        val frame = (clock / 90).toInt()
        var s = 20
        while (s + 4 <= floats) {
            val x = segments[s]; val y = segments[s + 1]
            val dx = segments[s + 2] - x; val dy = segments[s + 3] - y
            val len = hypot(dx, dy).coerceAtLeast(.01f)
            val side = if ((s / 40 + frame) % 2 == 0) 1f else -1f
            val px = -dy / len * side; val py = dx / len * side
            val ux = dx / len; val uy = dy / len
            fork[0] = x; fork[1] = y
            fork[2] = x + px * 6f * pixel + ux * 4f * pixel; fork[3] = y + py * 6f * pixel + uy * 4f * pixel
            fork[4] = fork[2]; fork[5] = fork[3]
            fork[6] = fork[2] + px * 3f * pixel + ux * 6f * pixel; fork[7] = fork[3] + py * 3f * pixel + uy * 6f * pixel
            canvas.drawLines(fork, boltPaint)
            s += 40
        }
    }

    private fun dotAt(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.color = color
        canvas.drawRect(x - r, y - r, x + r, y + r, paint)
    }

    private fun palette(element: Element) {
        val colors = when (element) {
            Element.FIRE -> 0xFFEF542B to 0xFFFFD078
            Element.ICE -> 0xFF57BFE7 to 0xFFE0FAFF
            Element.LIGHTNING -> 0xFFEAC43F to 0xFFFFFFCE
            Element.POISON -> 0xFF73AA37 to 0xFFD4EF7B
            Element.HOLY -> 0xFFEBCB78 to 0xFFFFF9DC
            else -> 0xFFBB7584 to 0xFFE5D4CD
        }
        ink = colors.first.toInt(); light = colors.second.toInt()
    }

    private fun dot(c: Canvas, x: Float, y: Float, r: Float, bright: Boolean = false) {
        paint.color = if (bright) light else ink
        paint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
        c.drawRect(floor(x-r), floor(y-r), ceil(x+r), ceil(y+r), paint)
    }

    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, bright: Boolean = false) {
        val steps = max(abs(xx-x), abs(yy-y)).toInt().coerceAtLeast(1)
        for (i in 0..steps step 2) {
            val t = i.toFloat()/steps
            dot(c, x+(xx-x)*t, y+(yy-y)*t, 1f, bright)
        }
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, phase: Float, flat: Boolean = false) {
        repeat(24) { i ->
            val a = i * PI.toFloat()/12 + phase
            dot(c, x+cos(a)*r, y+sin(a)*r*(if (flat) .35f else 1f), 1f, i%3 == 0)
        }
    }

    private fun burst(c: Canvas, x: Float, y: Float, t: Float, count: Int = 12) {
        repeat(count) { i ->
            val a = i*2*PI.toFloat()/count
            dot(c, x+cos(a)*(3+23*t), y+sin(a)*(3+23*t)+8*t*t, 2*(1-t)+.5f, i%3==0)
        }
    }

    private fun bolt(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, t: Float) {
        var px=x; var py=y
        for (i in 1..8) {
            val f=i/8f
            val nx=x+(xx-x)*f + if(i==8) 0f else sin(i*13f+floor(t*12))*5
            val ny=y+(yy-y)*f
            line(c,px,py,nx,ny,true); px=nx; py=ny
        }
    }

    fun draw(canvas: Canvas, scene: RectF, hero: RectF, enemies: List<RectF>, targets: List<Int>,
        relic: Relic, progress: Float, impact: Boolean, meteorFall: Boolean = false, pixel: Float = scene.width() / 240f) {
        val unit=pixel
        if(unit<=0f) return
        palette(relic.element)
        val t=progress.coerceIn(0f,1f)
        opacity=if(impact) (1-t).coerceAtLeast(0f) else 1f
        canvas.save(); canvas.clipRect(scene); canvas.translate(scene.left,scene.top); canvas.scale(unit,unit)
        val hx=(hero.centerX()-scene.left)/unit
        val hy=(hero.centerY()-scene.top)/unit
        fun x(r: RectF)=(r.centerX()-scene.left)/unit
        fun y(r: RectF)=(r.centerY()-scene.top)/unit
        val c=canvas
        // Invocation différée : aucun lien ni projectile vers les ennemis.
        if(relic==Relic.METEOR && !meteorFall) {
            ring(c,hx,hy+16,12f,t*2,true)
            repeat(14) { i ->
                val f=(t+i/14f)%1f
                dot(c,hx-8+sin(i*7f)*8,hy-8-f*38,1.5f,i%3==0)
            }
        } else if(relic==Relic.MAGIC_MISSILE && !impact) {
            repeat(Relic.MISSILE_COUNT) { i ->
                val a=i*2*PI.toFloat()/Relic.MISSILE_COUNT+t*4
                dot(c,hx+cos(a)*14,hy+sin(a)*14,2f,true)
            }
        } else if(relic.target==RelicTarget.SELF) {
            when(relic) {
                Relic.STONESKIN -> repeat(8) { i ->
                    val a=i*PI.toFloat()/4+t*.4f
                    dot(c,hx+cos(a)*18,hy+sin(a)*20,3f,true)
                }
                Relic.POISONED_BLADES -> {
                    line(c,hx-12,hy-19,hx-5,hy+4,true)
                    repeat(7) { i -> dot(c,hx-12+i,hy-15+i*3+t*9,1.5f) }
                }
                Relic.HASTE -> repeat(4) { i ->
                    val xx=hx-24+i*10+t*8
                    line(c,xx+5,hy-7,xx,hy); line(c,xx,hy,xx+5,hy+7)
                }
                Relic.HOURGLASS -> {
                    ink=0xFFC9A227.toInt(); light=0xFFFFEDAD.toInt()
                    line(c,hx-8,hy-12,hx+8,hy-12); line(c,hx-8,hy+12,hx+8,hy+12)
                    line(c,hx-8,hy-12,hx+8,hy+12); line(c,hx+8,hy-12,hx-8,hy+12)
                    dot(c,hx,hy-7+t*14,2f,true); ring(c,hx,hy,23f,-t)
                }
                else -> Unit // Toutes les reliques SELF sont décrites ci-dessus.
            }
        } else if(relic==Relic.WAR_CRY) {
            repeat(3) { ring(c,hx,hy,8+((t+it/3f)%1f)*75,0f) }
        } else {
            targets.forEachIndexed { order, index ->
                val r=enemies.getOrNull(index) ?: return@forEachIndexed
                val ex=x(r); val ey=y(r)
                val px=hx+(ex-hx)*t; val py=hy+(ey-hy)*t
                when(relic) {
                    Relic.METEOR -> {
                        val fall=(t/.35f).coerceIn(0f,1f)
                        val yy=ey-100*(1-fall)
                        if(t<.35f) { line(c,ex+12,yy-26,ex,yy); dot(c,ex,yy,8f); dot(c,ex-2,yy-2,4f,true) }
                        else { burst(c,ex,ey,(t-.35f)/.65f,20); ring(c,ex,ey+15,(t-.35f)*65,0f,true) }
                    }
                    Relic.FIREBALL -> if(impact) burst(c,ex,ey,t,18) else {
                        repeat(7) { i -> dot(c,px+(hx-ex)*i*.012f,py+(hy-ey)*i*.012f,4-i*.45f) }
                        dot(c,px,py,3f,true)
                    }
                    Relic.COCKTAIL, Relic.ACID_FLASK -> if(impact) {
                        burst(c,ex,ey,t); ring(c,ex,ey+14,8+17*t,0f,true)
                    } else {
                        dot(c,px,py-sin(t*PI.toFloat())*32,3f)
                        dot(c,px,py-sin(t*PI.toFloat())*32-4,1f,true)
                    }
                    Relic.FER_ROUGE, Relic.BLAZING_AXE -> {
                        line(c,ex-13,ey+12-24*t,ex+13,ey+12-24*t,true)
                        if(impact) burst(c,ex,ey,t,8)
                    }
                    Relic.LANTERNE -> repeat(12) { i ->
                        val a=i*.6f+t*5
                        dot(c,ex+cos(a)*(15-7*t),ey+12-i*2-t*8,2f,i%4==0)
                    }
                    Relic.ICE_SHARD -> if(impact) burst(c,ex,ey,t,9) else {
                        line(c,px-8,py+5,px+4,py-5,true); line(c,px-8,py+5,px-5,py-5)
                    }
                    Relic.CRYSTALLIZE -> repeat(6) { i ->
                        val a=i*PI.toFloat()/3
                        val radius=if(impact) 6+22*t else 23*(1-t)+4
                        val xx=ex+cos(a)*radius; val yy=ey+sin(a)*radius
                        line(c,xx,yy-7,xx-3,yy); line(c,xx-3,yy,xx,yy+7,true)
                        line(c,xx,yy+7,xx+3,yy); line(c,xx+3,yy,xx,yy-7,true)
                    }
                    Relic.FREEZING_RAIN -> repeat(12) { i ->
                        val yy=ey-40+((t+i*.17f)%1f)*62
                        line(c,ex-22+i*4,yy,ex-24+i*4,yy+7,true)
                    }
                    Relic.SLOW, Relic.NORTHERN_BREATH -> {
                        repeat(3) { ring(c,ex,ey+10-it*7,19f-it*3,-t*.5f,true) }
                        repeat(5) { dot(c,ex-12+it*6,ey+15,2f,true) }
                    }
                    Relic.LIGHTNING -> bolt(c,ex-8,ey-65,ex,ey,t)
                    Relic.MARTEAU_FOUDRE, Relic.THUNDER_CLUB -> {
                        val yy=ey-30*(1-t)
                        line(c,ex+6,yy-16,ex+6,yy+4); line(c,ex-5,yy-15,ex+15,yy-15,true)
                        if(impact) { bolt(c,ex,ey,ex-22,ey+12,t); bolt(c,ex,ey,ex+22,ey+12,t) }
                    }
                    Relic.CHAIN_LIGHTNING -> {
                        val flight=((t-order*.10f)/.5f).coerceIn(0f,1f)
                        val xx=hx+(ex-hx)*flight; val yy=hy+(ey-hy)*flight
                        if (impact) burst(c,ex,ey,t,5) else { dot(c,xx,yy,2f,true); bolt(c,xx-5,yy-3,xx+3,yy+4,t) }
                    }
                    Relic.MAGIC_MISSILE -> {
                        val flight=((t-order*.08f)/.48f).coerceIn(0f,1f)
                        if(flight>=1f) burst(c,ex,ey,((t-.48f-order*.08f)/.36f).coerceIn(0f,1f),6) else {
                            val xx=hx+(ex-hx)*flight
                            val yy=hy+(ey-hy)*flight+sin(flight*PI.toFloat())*(order-1)*22
                            dot(c,xx,yy,3f,true); dot(c,xx+5,yy,2f)
                        }
                    }
                    Relic.VENOM, Relic.VENOMOUS_WOUND -> if(impact) burst(c,ex,ey,t,7) else {
                        dot(c,px,py,3f); dot(c,px+4,py-2,1f,true)
                    }
                    Relic.CHAMPIGNON -> {
                        line(c,ex,ey+12,ex,ey+2,true)
                        repeat(7) { i -> dot(c,ex-9+i*3,ey-2+abs(i-3),2f) }
                        repeat(8) { i -> dot(c,ex+sin(i*5f+t)*18,ey-((t+i*.12f)%1f)*26,1f,true) }
                    }
                    Relic.PESTE -> repeat(20) { i ->
                        val a=i*2.4f+t; val radius=8+(i%5)*4
                        dot(c,ex+cos(a)*radius,ey+sin(a)*radius*.6f-t*8,2.5f,i%7==0)
                    }
                    Relic.HUNTERS_MARK -> {
                        ring(c,ex,ey,18-5*t,0f)
                        line(c,ex-22,ey,ex-8,ey,true); line(c,ex+8,ey,ex+22,ey,true)
                        line(c,ex,ey-22,ex,ey-8,true); line(c,ex,ey+8,ex,ey+22,true)
                    }
                    Relic.WHIRLWIND -> {
                        val yy=ey-12+t*24
                        line(c,ex-25,yy,ex+20,yy+5,true)
                        line(c,ex+20,yy+5,ex+14,yy,true)
                    }
                    Relic.SEISMIC_STRIKE -> {
                        ring(c,ex,ey+15,5+t*30,0f,true)
                        repeat(5) { j -> line(c,ex,ey+12,ex-20+j*10,ey+18+t*10,j%2==0) }
                    }
                    Relic.PONCTION -> {
                        ink=0xFFAB365C.toInt(); light=0xFFFFA0B4.toInt()
                        repeat(9) { i ->
                            val f=(t+i/9f)%1f
                            dot(c,ex+(hx-ex)*f,ey+(hy-ey)*f+sin(f*6+i)*4,1.5f,i%3==0)
                        }
                    }
                    Relic.HOLY_LIGHT -> {
                        repeat(5) { i -> line(c,ex-4+i*2,ey-65,ex-4+i*2,ey+14,i%2==0) }
                        ring(c,ex,ey,6+t*20,t)
                    }
                    Relic.STONESKIN, Relic.POISONED_BLADES, Relic.HASTE,
                    Relic.HOURGLASS, Relic.WAR_CRY -> Unit
                }
            }
        }
        canvas.restore()
    }
}
