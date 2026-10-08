package com.Atom2Universe.app.games.bigger

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Peint les deux astres d'Accrétion qui n'ont pas de carte au hub science : la poussière
 * et le trou noir (l'échelle cosmique n'en montre que l'horizon, sans disque). Les autres
 * viennent de [AccretionSprites]. Garde aussi les réglages communs : débord et teinte.
 *
 * Tiré d'une graine fixe : la tuile du hub et la partie montrent le même astre.
 */
object AccretionPainter {

    /**
     * Débord de chaque objet au-delà de son rayon de collision, en rayons : atmosphères,
     * couronnes des étoiles, disque du trou noir. L'image cuite doit le contenir.
     */
    val GLOW = floatArrayOf(1f, 1f, 1f, 1f, 1.06f, 1.14f, 1.1f, 1.06f, 1.55f, 1.6f, 1.7f, 1.35f)

    /** Teinte dominante de chaque objet : éclats de fusion, halos. */
    val TINT = intArrayOf(
        0xFFB8A590.toInt(), 0xFFA4A4AE.toInt(), 0xFF9A8670.toInt(), 0xFFDADAD4.toInt(),
        0xFFE0704A.toInt(), 0xFF4A9CF0.toInt(), 0xFF7AB4F0.toInt(), 0xFFE8A868.toInt(),
        0xFFFF6A3A.toInt(), 0xFFFFD54A.toInt(), 0xFF9CCBFF.toInt(), 0xFFFFA040.toInt()
    )

    /** Vrai pour ce qui brille par soi-même : la vue leur ajoute un halo qui pulse. */
    fun isLuminous(tier: Int) = tier >= 8

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Synchronisé : la tuile du hub peint sur le fil d'interface, la partie sur son propre fil. */
    @Synchronized
    fun paint(c: Canvas, tier: Int, cx: Float, cy: Float, r: Float) {
        p.reset(); p.isAntiAlias = true
        val rnd = Random(tier * 7919 + 13)
        if (tier == 0) dust(c, rnd, cx, cy, r) else blackHole(c, cx, cy, r)
        p.reset()
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun fill(color: Int) { p.shader = null; p.style = Paint.Style.FILL; p.color = color }

    /** Un point au hasard dans le disque de rayon [r], réparti uniformément. */
    private fun inDisc(rnd: Random, r: Float): Pair<Float, Float> {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val d = r * sqrt(rnd.nextFloat())
        return cos(a) * d to sin(a) * d
    }

    /** Contour irrégulier : [smooth] faux donne des facettes, vrai des bosses arrondies. */
    private fun blob(rnd: Random, cx: Float, cy: Float, r: Float, points: Int, jitter: Float, smooth: Boolean): Path {
        val xs = FloatArray(points); val ys = FloatArray(points)
        for (i in 0 until points) {
            val a = i * 2f * PI.toFloat() / points + rnd.nextFloat() * 0.3f / points
            val d = r * (1f - jitter + rnd.nextFloat() * jitter)
            xs[i] = cx + cos(a) * d; ys[i] = cy + sin(a) * d
        }
        val path = Path()
        if (!smooth) {
            path.moveTo(xs[0], ys[0])
            for (i in 1 until points) path.lineTo(xs[i], ys[i])
        } else {
            path.moveTo((xs[0] + xs[points - 1]) / 2f, (ys[0] + ys[points - 1]) / 2f)
            for (i in 0 until points) {
                val n = (i + 1) % points
                path.quadTo(xs[i], ys[i], (xs[i] + xs[n]) / 2f, (ys[i] + ys[n]) / 2f)
            }
        }
        path.close()
        return path
    }

    // ── Les objets ───────────────────────────────────────────────────────────

    private fun dust(c: Canvas, rnd: Random, cx: Float, cy: Float, r: Float) {
        p.shader = RadialGradient(cx, cy, r,
            intArrayOf(Color.argb(120, 150, 128, 104), Color.argb(70, 120, 104, 88), Color.argb(25, 110, 98, 88)),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        // Des volutes plus denses : un nuage, pas une assiette de grains.
        repeat(4) {
            val (dx, dy) = inDisc(rnd, r * 0.45f)
            fill(Color.argb(45, 190, 170, 140))
            c.drawPath(blob(rnd, cx + dx, cy + dy, r * (0.3f + rnd.nextFloat() * 0.2f), 9, 0.5f, smooth = true), p)
        }
        val colors = intArrayOf(0xFFC8B498.toInt(), 0xFF8C7C6C.toInt(), 0xFFA89478.toInt(), 0xFF6E6258.toInt())
        repeat(120) {
            val (dx, dy) = inDisc(rnd, r * 0.92f)
            fill(alpha(colors[rnd.nextInt(colors.size)], 90 + rnd.nextInt(110)))
            c.drawCircle(cx + dx, cy + dy, r * (0.015f + rnd.nextFloat() * 0.03f), p)
        }
        repeat(6) {
            val (dx, dy) = inDisc(rnd, r * 0.8f)
            fill(Color.argb(210, 255, 248, 230))
            c.drawCircle(cx + dx, cy + dy, r * 0.022f, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.03f; p.color = Color.argb(45, 220, 205, 180)
        c.drawCircle(cx, cy, r * 0.97f, p)
    }

    /** Disque d'accrétion, blanc brûlant au bord intérieur, rouge sombre au bord extérieur. */
    private fun accretionDisc(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.save(); c.scale(1f, 0.26f, cx, cy)
        val inner = 0.66f; val outer = 1.3f
        p.style = Paint.Style.STROKE; p.strokeWidth = r * (outer - inner)
        p.shader = RadialGradient(cx, cy, r * outer,
            intArrayOf(0xFFFFF4D0.toInt(), 0xFFFFD27A.toInt(), 0xFFFF9838.toInt(), 0xC0D0561A.toInt(), 0x00801E08),
            floatArrayOf(inner / outer, 0.62f, 0.74f, 0.88f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * (inner + outer) / 2f, p)
        p.shader = null; p.style = Paint.Style.FILL
        c.restore()
    }

    private fun blackHole(c: Canvas, cx: Float, cy: Float, r: Float) {
        // La lentille : l'espace se courbe autour de l'astre, un voile violet marque sa taille.
        p.shader = RadialGradient(cx, cy, r * GLOW[11],
            intArrayOf(Color.argb(0, 90, 60, 160), Color.argb(90, 110, 70, 190), Color.argb(0, 90, 60, 160)),
            floatArrayOf(0.55f, 0.74f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * GLOW[11], p); p.shader = null
        val tilt = -14f
        c.save(); c.rotate(tilt, cx, cy); accretionDisc(c, cx, cy, r); c.restore()
        // Le disque vu par-dessus l'horizon, replié par la gravité.
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.1f
        p.shader = RadialGradient(cx, cy, r * 0.9f,
            intArrayOf(0xFFFFE8B0.toInt(), 0xFFFF9A40.toInt(), 0x00FF6010),
            floatArrayOf(0.6f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.72f, p); p.shader = null
        fill(Color.BLACK); c.drawCircle(cx, cy, r * 0.6f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.035f; p.color = 0xFFFFF2D8.toInt()
        c.drawCircle(cx, cy, r * 0.62f, p)
        // La moitié avant du disque passe devant l'horizon.
        c.save(); c.rotate(tilt, cx, cy)
        c.clipRect(cx - r * 1.5f, cy, cx + r * 1.5f, cy + r)
        accretionDisc(c, cx, cy, r)
        c.restore()
    }
}
