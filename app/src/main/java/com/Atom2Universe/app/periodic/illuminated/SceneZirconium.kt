package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Zirconium — la bague au zircon. La zircone, un oxyde de zirconium taillé comme un diamant,
 * brille et jette encore plus de feux que lui. Dans son écrin de velours, la bague attend sous
 * une lumière qui tourne : ses facettes s'allument tour à tour, et des éclats aux couleurs de
 * l'arc-en-ciel s'en échappent.
 */
internal class SceneZirconium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_zircon_ring
    override val noteRes = R.string.card_note_zircon_ring
    override val explainRes = R.string.card_explain_zircon_ring

    /** Les facettes vues de profil : la table, la couronne, le pavillon ; et l'angle de chacune. */
    private val facets = Array(16) { Path() }
    private val facetAngle = FloatArray(16)
    private val gem = Path()

    init {
        val n = 8
        val gx = FloatArray(n + 1); val tx = FloatArray(n + 1)
        for (k in 0..n) {
            // Des points de rondiste et de table répartis comme sur un cercle vu de face.
            val a = PI.toFloat() * k / n
            gx[k] = GX - GIRDLE * cos(a)
            tx[k] = GX - TABLE * cos(a)
        }
        for (k in 0 until n) {
            facets[2 * k].set(poly(tx[k], GY - CROWN, tx[k + 1], GY - CROWN, gx[k + 1], GY, gx[k], GY))
            facetAngle[2 * k] = (k + .5f) / n * PI.toFloat()
            facets[2 * k + 1].set(poly(gx[k], GY, gx[k + 1], GY, GX, GY + PAVILION))
            facetAngle[2 * k + 1] = (k + .5f) / n * PI.toFloat() + 2.1f
        }
        gem.addPath(poly(GX - TABLE, GY - CROWN, GX + TABLE, GY - CROWN, GX + GIRDLE, GY, GX, GY + PAVILION, GX - GIRDLE, GY))
    }

    override fun engrave(b: Burin) {
        // Le fond de velours sombre, éclairé d'en haut.
        val back = rect(40f, 50f, 320f, 340f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 340f,
            intArrayOf(0xFF1A1430.toInt(), 0xFF0A0814.toInt()), null, Shader.TileMode.CLAMP)
        b.c.drawPath(back, b.p)
        b.glow(GX, 140f, 140f, 0xFF4A3A7A.toInt(), 120)
        // L'écrin : couvercle ouvert derrière, satin blanc ; boîte de velours rouge, coussin fendu.
        val lid = poly(96f, 118f, 264f, 118f, 270f, 206f, 90f, 206f)
        b.body(lid, 0xFF6A1424.toInt(), .45f, 0f, outline = 1f, washAlpha = 255)
        val satin = poly(104f, 126f, 256f, 126f, 261f, 200f, 99f, 200f)
        b.pen(0xFF000000.toInt()).shader = LinearGradient(100f, 0f, 260f, 0f,
            intArrayOf(0xFFE8E4F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFD0CCD8.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(satin, b.p)
        for (k in 0 until 5) b.stroke(Path().apply { moveTo(108f + k * 34f, 128f); quadTo(118f + k * 34f, 160f, 106f + k * 34f, 198f) }, .6f, withAlpha(0xFF9A96A8.toInt(), 150))
        b.stroke(satin, .8f)
        val box = poly(84f, 206f, 276f, 206f, 290f, 292f, 70f, 292f)
        b.body(box, 0xFF8A1A2E.toInt(), .4f, 0f, Fade(0f, 292f, 0f, 206f, 220, 30), outline = 1f, washAlpha = 255)
        val cushion = Path().apply { addRoundRect(96f, 208f, 264f, 250f, 18f, 18f, Path.Direction.CW) }
        b.body(cushion, 0xFF9A2A3A.toInt(), .35f, 0f, Fade(0f, 250f, 0f, 210f, 200, 0), outline = .9f, washAlpha = 255)
        b.glow(GX, 214f, 60f, 0xFFFFC8D0.toInt(), 60)
        // L'anneau d'or blanc, enfoncé dans la fente du coussin.
        b.c.save(); b.c.clipRect(40f, 50f, 320f, 226f)
        b.c.drawOval(GX - 28f, GY + 8f, GX + 28f, GY + 74f, b.pen(0xFF8A9096.toInt(), 8f))
        b.c.drawOval(GX - 28f, GY + 8f, GX + 28f, GY + 74f, b.pen(0xFFE8ECF0.toInt(), 4.4f))
        b.c.drawArc(GX - 27f, GY + 9f, GX + 27f, GY + 73f, 190f, 60f, false, b.pen(0xFFFFFFFF.toInt(), 1.6f))
        b.c.restore()
        b.line(GX - 34f, 226f, GX + 34f, 226f, 2.4f, 0xFF4A0E18.toInt())
        // Le chaton et ses griffes.
        b.body(poly(GX - 12f, GY + 4f, GX + 12f, GY + 4f, GX + 6f, GY + 14f, GX - 6f, GY + 14f), 0xFFD8DCE0.toInt(), .2f, 0f, outline = .8f, washAlpha = 255)
        b.stroke(gem, 1f)
        for (x in floatArrayOf(GX - GIRDLE + 3f, GX - 8f, GX + 8f, GX + GIRDLE - 3f)) {
            b.line(x, GY + 10f, x * .9f + GX * .1f, GY - 4f, 2.4f, 0xFFB8BEC4.toInt())
        }
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val light = t * .8f
        // Chaque facette s'éclaire quand la lumière qui tourne passe dans son axe ; certaines
        // renvoient une couleur au lieu du blanc : ce sont les « feux ».
        for (k in facets.indices) {
            val a = facetAngle[k]
            val face = ((cos(a * 2f - light) + 1f) / 2f).pow(6)
            val base = 0xFF8A94B8.toInt()
            val color = if (face > .55f && k % 3 == 0) spectral(a + light * .3f) else blend(base, 0xFFFFFFFF.toInt(), face)
            fill.color = color
            c.drawPath(facets[k], fill)
        }
        ink.color = withAlpha(0xFF3A4060.toInt(), 200); ink.strokeWidth = .6f
        for (k in facets.indices) c.drawPath(facets[k], ink)
        ink.color = Ink.SEPIA; ink.strokeWidth = 1f
        c.drawPath(gem, ink)
        // Les éclats : des étoiles colorées qui naissent autour de la pierre et s'éteignent.
        for (k in 0 until SPARKS) {
            val period = 1.6f + hash(k, 1)
            val slot = ((t + hash(k, 2) * 4f) / period).toInt()
            val life = ((t + hash(k, 2) * 4f) % period) / period
            val a = hash(slot, k) * 2f * PI.toFloat()
            val r = 16f + 70f * hash(slot, k + 40)
            val x = GX + r * cos(a) * 1.3f
            val y = GY + r * sin(a) * .8f
            val glow = sin(life * PI.toFloat())
            if (glow < .1f) continue
            ink.color = withAlpha(spectral(hash(slot, k + 80) * 6f), (230 * glow).toInt()); ink.strokeWidth = 1f
            val l = 2f + 5f * glow
            c.drawLine(x - l, y, x + l, y, ink); c.drawLine(x, y - l, x, y + l, ink)
            ink.strokeWidth = .6f
            c.drawLine(x - l * .5f, y - l * .5f, x + l * .5f, y + l * .5f, ink)
            c.drawLine(x - l * .5f, y + l * .5f, x + l * .5f, y - l * .5f, ink)
        }
        // Le grand éclat blanc sur la table, quand la lumière y passe.
        val flash = ((cos(light) + 1f) / 2f).pow(20)
        if (flash > .05f) {
            ink.color = withAlpha(0xFFFFFFFF.toInt(), (255 * flash).toInt()); ink.strokeWidth = 1.4f
            val l = 18f * flash
            val fx = GX - 6f; val fy = GY - CROWN
            c.drawLine(fx - l, fy, fx + l, fy, ink); c.drawLine(fx, fy - l, fx, fy + l, ink)
            fill.color = withAlpha(0xFFFFFFFF.toInt(), (120 * flash).toInt()); c.drawCircle(fx, fy, 6f * flash + 2f, fill)
        }
    }

    /** Une couleur de l'arc-en-ciel, de 0 (rouge) à 6 (violet), en boucle. */
    private fun spectral(x: Float): Int {
        val h = ((x % 6f) + 6f) % 6f
        val i = h.toInt(); val f = h - i
        val a = SPECTRUM[i % 6]; val b = SPECTRUM[(i + 1) % 6]
        return blend(a, b, f)
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        val r = ((a shr 16) and 255) + ((((b shr 16) and 255) - ((a shr 16) and 255)) * f).toInt()
        val g = ((a shr 8) and 255) + ((((b shr 8) and 255) - ((a shr 8) and 255)) * f).toInt()
        val bl = (a and 255) + (((b and 255) - (a and 255)) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        /** La pierre : centre du rondiste, demi-largeurs, hauteurs de la couronne et du pavillon. */
        const val GX = 180f
        const val GY = 166f
        const val GIRDLE = 26f
        const val TABLE = 14f
        const val CROWN = 10f
        const val PAVILION = 22f
        const val SPARKS = 18
        val SPECTRUM = intArrayOf(
            0xFFFF4A4A.toInt(), 0xFFFFB03A.toInt(), 0xFFFFF04A.toInt(), 0xFF5AFF7A.toInt(), 0xFF4AA8FF.toInt(), 0xFFB05AFF.toInt()
        )
    }
}
