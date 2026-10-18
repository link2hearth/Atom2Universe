package com.Atom2Universe.app.games.reflex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tuile du hub : une image figée d'une partie du Collisionneur, avec les couleurs du jeu
 * ([ParticleKind.color]) — anneaux du collisionneur, particules et leur anneau
 * d'approche, une antimatière, et un éclat de touche réussie.
 */
class ReflexHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = min(w, h)
        fun a(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
        fun glow(x: Float, y: Float, radius: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(x, y, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, paint)
            paint.shader = null
        }

        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF080818.toInt(), 0xFF170A22.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val random = Random(1207)
        repeat(50) {
            paint.color = Color.argb(60 + random.nextInt(120), 200, 210, 255)
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h, unit * (.002f + random.nextFloat() * .003f), paint)
        }

        // Anneaux du collisionneur.
        val cx = w * .5f
        val cy = h * .32f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = unit * .006f
        for (k in 1..3) {
            paint.color = 0x305C6BC0
            canvas.drawCircle(cx, cy, unit * (.14f + .12f * k), paint)
        }
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = unit * .014f
        paint.color = 0x805C6BC0.toInt()
        val arc = RectF()
        for (k in 1..3) {
            val r = unit * (.14f + .12f * k)
            arc.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(arc, 40f * k, 38f, false, paint)
        }
        paint.style = Paint.Style.FILL

        fun particle(kind: ParticleKind, x: Float, y: Float, r: Float, ring: Float) {
            glow(x, y, r * 1.9f, a(kind.color, 0x90))
            paint.shader = RadialGradient(x - r * .3f, y - r * .3f, r * 1.35f,
                intArrayOf(Color.WHITE, kind.color, a(kind.color, 0xFF) and 0xFF3F3F3F.toInt()),
                floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r, paint)
            paint.shader = null
            if (ring > 1f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * .012f
                paint.color = a(kind.color, 0xD0)
                canvas.drawCircle(x, y, r * ring, paint)
                paint.style = Paint.Style.FILL
            }
        }

        particle(ParticleKind.PROTON, w * .24f, h * .24f, unit * .075f, 1.9f)
        particle(ParticleKind.ELECTRON, w * .78f, h * .40f, unit * .065f, 1.3f)
        particle(ParticleKind.NUCLEUS, w * .60f, h * .14f, unit * .06f, 2.4f)

        // Antimatière : cœur sombre et piquants.
        val ax = w * .44f
        val ay = h * .43f
        val ar = unit * .07f
        val anti = ParticleKind.ANTI.color
        glow(ax, ay, ar * 2f, a(anti, 0x90))
        paint.color = anti
        val path = Path()
        for (i in 0 until 10) {
            val ang = i * 2f * PI.toFloat() / 10
            val half = PI.toFloat() / 10 * .45f
            path.moveTo(ax + cos(ang - half) * ar * .92f, ay + sin(ang - half) * ar * .92f)
            path.lineTo(ax + cos(ang) * ar * 1.4f, ay + sin(ang) * ar * 1.4f)
            path.lineTo(ax + cos(ang + half) * ar * .92f, ay + sin(ang + half) * ar * .92f)
            path.close()
        }
        canvas.drawPath(path, paint)
        paint.color = 0xFF3A0014.toInt()
        canvas.drawCircle(ax, ay, ar, paint)

        // Éclat d'une touche parfaite.
        val bx = w * .86f
        val by = h * .16f
        glow(bx, by, unit * .09f, 0xC0FFFFFF.toInt())
        repeat(14) {
            val ang = random.nextFloat() * 2f * PI.toFloat()
            val d = unit * (.05f + random.nextFloat() * .08f)
            paint.color = a(ParticleKind.PROTON.color, 150 + random.nextInt(100))
            canvas.drawCircle(bx + cos(ang) * d, by + sin(ang) * d, unit * (.006f + random.nextFloat() * .008f), paint)
        }

        // Réserver la partie basse au seul titre natif, identique aux autres tuiles.
        paint.shader = LinearGradient(0f, h * .48f, 0f, h, Color.TRANSPARENT, 0xF0080818.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .48f, w, h, paint)
    }
}
