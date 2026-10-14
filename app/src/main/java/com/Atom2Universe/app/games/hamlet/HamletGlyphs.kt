package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Les petites pictogrammes : le produit d'un atelier sur son enseigne et dans les bulles, la pièce,
 * la gemme, l'étoile d'expérience. Chacun se dessine dans un carré de côté [size] centré en (cx, cy).
 */
object HamletGlyphs {

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()

    private const val INK = 0x3A2E22

    fun draw(c: Canvas, name: String, cx: Float, cy: Float, size: Float, accent: Int = 0xC8392E) {
        c.save()
        c.translate(cx, cy)
        c.scale(size / 2f, size / 2f)
        lineP.strokeWidth = 0.12f
        glyph(c, name, accent)
        c.restore()
    }

    private fun fill(color: Int) { fillP.color = Iso.opaque(color) }
    private fun circ(c: Canvas, x: Float, y: Float, r: Float, color: Int) { fill(color); c.drawCircle(x, y, r, fillP) }
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) { fill(color); rect.set(l, t, r, b); c.drawOval(rect, fillP) }
    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) { fill(color); c.drawRect(l, t, r, b, fillP) }
    private fun rbox(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) { fill(color); rect.set(l, t, r, b); c.drawRoundRect(rect, rad, rad, fillP) }
    private fun poly(c: Canvas, color: Int, vararg v: Float) {
        path.reset()
        var i = 0
        while (i + 1 < v.size) { if (i == 0) path.moveTo(v[i], v[i + 1]) else path.lineTo(v[i], v[i + 1]); i += 2 }
        path.close()
        fill(color)
        c.drawPath(path, fillP)
    }
    private fun ln(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, w: Float = 0.12f) {
        lineP.color = Iso.opaque(color); lineP.strokeWidth = w
        c.drawLine(x0, y0, x1, y1, lineP)
    }

    private fun shade(color: Int, k: Float) = Iso.shade(color, k)

    private fun glyph(c: Canvas, name: String, acc: Int) {
        when (name) {
            "bread" -> { oval(c, -0.9f, -0.45f, 0.9f, 0.55f, 0xC8883A); oval(c, -0.8f, -0.4f, 0.8f, 0.3f, 0xE0A850); for (i in -1..1) ln(c, i * 0.4f - 0.1f, -0.25f, i * 0.4f + 0.1f, 0.05f, 0xC8883A, 0.1f) }
            "flower", "orchid" -> {
                ln(c, 0f, 0.1f, 0f, 0.95f, 0x4F9D37, 0.14f)
                for (i in 0 until 5) { val a = i * 2 * PI.toFloat() / 5; circ(c, cos(a) * 0.4f, -0.35f + sin(a) * 0.4f, 0.3f, acc) }
                circ(c, 0f, -0.35f, 0.22f, 0xF4D42A)
            }
            "cheese" -> { poly(c, 0xF2C230, -0.95f, 0.5f, 0.95f, 0.5f, 0.95f, -0.1f, -0.95f, -0.55f); box(c, -0.95f, 0.4f, 0.95f, 0.55f, 0xD8A41A); circ(c, -0.3f, 0.05f, 0.15f, 0xD8A41A); circ(c, 0.4f, 0.15f, 0.18f, 0xD8A41A) }
            "cloth" -> { rbox(c, -0.8f, -0.7f, 0.8f, 0.7f, 0.15f, acc); box(c, -0.8f, -0.2f, 0.8f, 0.05f, shade(acc, 0.35f)); ln(c, -0.8f, 0.4f, 0.8f, 0.4f, shade(acc, -0.3f), 0.1f) }
            "leaf" -> { poly(c, 0x5AAD3C, 0f, -0.95f, 0.7f, -0.2f, 0.45f, 0.7f, 0f, 0.95f, -0.45f, 0.7f, -0.7f, -0.2f); ln(c, 0f, -0.7f, 0f, 0.9f, 0x2F6A2A, 0.1f) }
            "candle" -> { rbox(c, -0.3f, -0.15f, 0.3f, 0.9f, 0.08f, 0xF4EBC8); poly(c, 0xFFB02A, 0f, -0.95f, 0.22f, -0.45f, 0f, -0.2f, -0.22f, -0.45f); ln(c, 0f, -0.2f, 0f, -0.1f, INK, 0.08f) }
            "book", "scroll" -> {
                if (name == "book") { rbox(c, -0.7f, -0.8f, 0.7f, 0.8f, 0.1f, acc); box(c, -0.7f, -0.8f, -0.5f, 0.8f, shade(acc, -0.3f)); box(c, -0.3f, -0.4f, 0.45f, -0.25f, 0xF4EBC8); box(c, -0.3f, -0.1f, 0.45f, 0.05f, 0xF4EBC8) }
                else { rbox(c, -0.6f, -0.8f, 0.6f, 0.8f, 0.1f, 0xF4EBC8); oval(c, -0.8f, -0.95f, 0.8f, -0.6f, 0xD8C890); oval(c, -0.8f, 0.6f, 0.8f, 0.95f, 0xD8C890); for (i in 0..2) ln(c, -0.35f, -0.3f + i * 0.3f, 0.35f, -0.3f + i * 0.3f, INK, 0.08f) }
            }
            "toy" -> { box(c, -0.9f, 0.05f, -0.05f, 0.9f, 0xE8402A); box(c, 0.05f, 0.05f, 0.9f, 0.9f, 0x3A9AD8); box(c, -0.45f, -0.8f, 0.4f, 0.0f, 0xF4C22A) }
            "pot" -> { poly(c, 0xC8783A, -0.45f, -0.75f, 0.45f, -0.75f, 0.85f, 0.1f, 0.5f, 0.9f, -0.5f, 0.9f, -0.85f, 0.1f); box(c, -0.55f, -0.9f, 0.55f, -0.7f, 0xA85A2A) }
            "log", "wood" -> { box(c, -0.9f, -0.5f, 0.55f, 0.5f, 0x9A6A3A); circ(c, 0.55f, 0f, 0.5f, 0xD8B070); circ(c, 0.55f, 0f, 0.3f, 0xC09050); circ(c, 0.55f, 0f, 0.1f, 0x9A6A3A) }
            "crate" -> { box(c, -0.8f, -0.8f, 0.8f, 0.8f, 0xB8905A); ln(c, -0.8f, -0.8f, 0.8f, 0.8f, 0x7A5A32); ln(c, 0.8f, -0.8f, -0.8f, 0.8f, 0x7A5A32); ln(c, -0.8f, -0.8f, 0.8f, -0.8f, 0x7A5A32); ln(c, -0.8f, 0.8f, 0.8f, 0.8f, 0x7A5A32) }
            "fruit", "apple" -> { circ(c, 0f, 0.15f, 0.75f, if (name == "apple") 0xD8302A else acc); ln(c, 0f, -0.55f, 0.1f, -0.9f, 0x6A4A2E); poly(c, 0x4F9D37, 0.1f, -0.7f, 0.5f, -0.9f, 0.4f, -0.5f) }
            "boot" -> { poly(c, 0x8A5A32, -0.4f, -0.9f, 0.3f, -0.9f, 0.3f, 0.1f, 0.9f, 0.4f, 0.9f, 0.85f, -0.4f, 0.85f); box(c, -0.4f, 0.65f, 0.9f, 0.85f, 0x4A3220) }
            "shoe" -> { lineP.color = Iso.opaque(0x9A9AA6); lineP.strokeWidth = 0.32f; rect.set(-0.65f, -0.7f, 0.65f, 0.75f); c.drawArc(rect, -30f, 240f, false, lineP); circ(c, -0.52f, 0.25f, 0.06f, INK); circ(c, 0.52f, 0.25f, 0.06f, INK) }
            "mug" -> { rbox(c, -0.7f, -0.55f, 0.5f, 0.85f, 0.12f, 0xD8A850); lineP.color = Iso.opaque(0xD8A850); lineP.strokeWidth = 0.18f; rect.set(0.2f, -0.2f, 0.9f, 0.55f); c.drawArc(rect, -90f, 180f, false, lineP); oval(c, -0.8f, -0.85f, 0.6f, -0.35f, 0xF8F4E4) }
            "brush" -> { ln(c, -0.8f, 0.8f, 0.4f, -0.4f, 0x8A5A32, 0.2f); poly(c, acc, 0.2f, -0.2f, 0.65f, -0.65f, 0.9f, -0.9f, 0.45f, -0.35f) }
            "hat" -> { oval(c, -0.95f, 0.15f, 0.95f, 0.75f, shade(acc, -0.1f)); rbox(c, -0.5f, -0.75f, 0.5f, 0.4f, 0.25f, acc); box(c, -0.5f, 0.05f, 0.5f, 0.25f, 0xF4D42A) }
            "meat" -> { circ(c, -0.2f, -0.2f, 0.65f, 0xC84A3A); circ(c, -0.35f, -0.35f, 0.25f, 0xE07A6A); ln(c, 0.25f, 0.25f, 0.75f, 0.75f, 0xF4EBC8, 0.22f); circ(c, 0.85f, 0.65f, 0.14f, 0xF4EBC8); circ(c, 0.65f, 0.85f, 0.14f, 0xF4EBC8) }
            "horse" -> { poly(c, 0x9A6A3A, -0.5f, 0.9f, -0.3f, -0.1f, -0.1f, -0.75f, 0.1f, -0.9f, 0.2f, -0.55f, 0.85f, 0.05f, 0.85f, 0.4f, 0.4f, 0.4f, 0.25f, 0.9f); circ(c, 0.1f, -0.3f, 0.07f, INK); poly(c, 0x5A3A22, -0.45f, -0.3f, -0.15f, -0.75f, -0.1f, 0.1f) }
            "paw" -> { oval(c, -0.5f, 0f, 0.5f, 0.85f, 0x6A4A32); circ(c, -0.7f, -0.2f, 0.2f, 0x6A4A32); circ(c, -0.25f, -0.6f, 0.2f, 0x6A4A32); circ(c, 0.25f, -0.6f, 0.2f, 0x6A4A32); circ(c, 0.7f, -0.2f, 0.2f, 0x6A4A32) }
            "fish" -> { oval(c, -0.8f, -0.45f, 0.55f, 0.45f, 0x6AA8D8); poly(c, 0x6AA8D8, 0.4f, 0f, 0.95f, -0.5f, 0.95f, 0.5f); circ(c, -0.5f, -0.1f, 0.09f, INK) }
            "milk" -> { rbox(c, -0.5f, -0.4f, 0.5f, 0.95f, 0.1f, 0xF8F8F8); poly(c, 0xF8F8F8, -0.3f, -0.4f, 0.3f, -0.4f, 0.2f, -0.85f, -0.2f, -0.85f); box(c, -0.5f, 0.1f, 0.5f, 0.55f, 0x4A8AD8); box(c, -0.22f, -1f, 0.22f, -0.85f, 0xD83A2A) }
            "drop" -> poly(c, 0x3A9AD8, 0f, -0.95f, 0.6f, 0.15f, 0.55f, 0.6f, 0.2f, 0.9f, -0.2f, 0.9f, -0.55f, 0.6f, -0.6f, 0.15f)
            "key" -> { circ(c, -0.4f, -0.4f, 0.4f, 0xE8B82A); circ(c, -0.4f, -0.4f, 0.16f, 0x6A5A32); ln(c, -0.1f, -0.1f, 0.8f, 0.8f, 0xE8B82A, 0.2f); ln(c, 0.5f, 0.5f, 0.2f, 0.8f, 0xE8B82A, 0.18f); ln(c, 0.75f, 0.75f, 0.5f, 0.98f, 0xE8B82A, 0.18f) }
            "lantern" -> { box(c, -0.4f, -0.5f, 0.4f, 0.65f, 0xF4D44A); box(c, -0.5f, -0.65f, 0.5f, -0.5f, 0x4A4A52); box(c, -0.5f, 0.65f, 0.5f, 0.8f, 0x4A4A52); ln(c, 0f, -0.5f, 0f, 0.65f, 0xC89A1A, 0.08f); ln(c, -0.3f, -0.9f, 0.3f, -0.9f, 0x4A4A52, 0.12f) }
            "flour", "sack" -> { poly(c, if (name == "sack") 0xC89A5A else 0xF4EBD0, -0.45f, -0.55f, 0.45f, -0.55f, 0.85f, 0.2f, 0.7f, 0.9f, -0.7f, 0.9f, -0.85f, 0.2f); ln(c, -0.4f, -0.55f, 0.4f, -0.55f, 0x6A4A32, 0.14f); poly(c, 0xF4EBD0, -0.45f, -0.55f, -0.2f, -0.9f, 0.2f, -0.9f, 0.45f, -0.55f) }
            "shield" -> { poly(c, acc, -0.7f, -0.8f, 0.7f, -0.8f, 0.7f, 0.1f, 0f, 0.95f, -0.7f, 0.1f); poly(c, 0xF4EBD0, 0f, -0.8f, 0.7f, -0.8f, 0.7f, 0.1f, 0f, 0.95f); ln(c, -0.7f, -0.8f, 0.7f, -0.8f, 0xE8B82A, 0.14f) }
            "bottle", "potion" -> { circ(c, 0f, 0.35f, 0.6f, acc); box(c, -0.2f, -0.6f, 0.2f, 0.0f, 0xCFE8F0); box(c, -0.28f, -0.8f, 0.28f, -0.6f, 0x8A5A32); circ(c, -0.2f, 0.2f, 0.14f, shade(acc, 0.5f)) }
            "bird" -> { oval(c, -0.7f, -0.2f, 0.6f, 0.7f, acc); circ(c, 0.45f, -0.35f, 0.34f, acc); poly(c, 0xF4A82A, 0.75f, -0.4f, 1.0f, -0.3f, 0.75f, -0.25f); circ(c, 0.52f, -0.42f, 0.06f, INK); poly(c, shade(acc, -0.3f), -0.7f, 0.1f, -1f, -0.3f, -0.4f, -0.05f) }
            "bell" -> { poly(c, 0xE8B82A, -0.2f, -0.8f, 0.2f, -0.8f, 0.55f, 0.1f, 0.85f, 0.55f, -0.85f, 0.55f, -0.55f, 0.1f); circ(c, 0f, 0.7f, 0.2f, 0xC89A1A); circ(c, 0f, -0.88f, 0.1f, 0xC89A1A) }
            "sword" -> { ln(c, -0.7f, 0.7f, 0.75f, -0.75f, 0xD0D6DC, 0.22f); ln(c, -0.7f, 0.7f, -0.45f, 0.45f, 0x6A4A2E, 0.28f); ln(c, -0.62f, 0.15f, -0.15f, 0.62f, 0xE8B82A, 0.2f) }
            "map" -> { poly(c, 0xF0E0A8, -0.9f, -0.6f, -0.3f, -0.8f, 0.3f, -0.6f, 0.9f, -0.8f, 0.9f, 0.6f, 0.3f, 0.8f, -0.3f, 0.6f, -0.9f, 0.8f); ln(c, -0.35f, -0.4f, 0.3f, 0.3f, 0xC8392E, 0.14f); ln(c, 0.3f, -0.4f, -0.35f, 0.3f, 0xC8392E, 0.14f); ln(c, 0.3f, -0.6f, 0.3f, 0.8f, 0xA89050, 0.05f) }
            "gear" -> { for (i in 0 until 8) { val a = i * PI.toFloat() / 4; circ(c, cos(a) * 0.72f, sin(a) * 0.72f, 0.2f, 0x8A8A96) }; circ(c, 0f, 0f, 0.65f, 0x9A9AA6); circ(c, 0f, 0f, 0.25f, 0x5A5A66) }
            "note" -> { oval(c, -0.7f, 0.35f, -0.05f, 0.85f, INK); ln(c, -0.12f, 0.6f, -0.12f, -0.8f, INK, 0.14f); poly(c, INK, -0.12f, -0.8f, 0.6f, -0.45f, 0.6f, -0.1f, -0.12f, -0.4f) }
            "grape" -> { for ((x, y) in listOf(-0.4f to -0.2f, 0.1f to -0.2f, 0.55f to -0.2f, -0.15f to 0.25f, 0.35f to 0.25f, 0.1f to 0.7f)) circ(c, x, y, 0.3f, 0x7A2A8A); ln(c, 0f, -0.45f, 0.05f, -0.9f, 0x5A7A2A, 0.14f); poly(c, 0x4F9D37, 0.05f, -0.8f, 0.55f, -0.95f, 0.4f, -0.55f) }
            "cup" -> { poly(c, 0xF8F4E4, -0.7f, -0.35f, 0.5f, -0.35f, 0.4f, 0.4f, -0.6f, 0.4f); lineP.color = Iso.opaque(0xF8F4E4); lineP.strokeWidth = 0.14f; rect.set(0.2f, -0.2f, 0.85f, 0.25f); c.drawArc(rect, -90f, 180f, false, lineP); oval(c, -0.95f, 0.35f, 0.75f, 0.65f, acc); box(c, -0.7f, -0.35f, 0.5f, -0.15f, acc) }
            "coins" -> { for (i in 0..2) { oval(c, -0.7f, 0.25f - i * 0.38f, 0.7f, 0.7f - i * 0.38f, 0xB8861A); oval(c, -0.7f, 0.1f - i * 0.38f, 0.7f, 0.55f - i * 0.38f, 0xF2C230) } }
            "gem" -> { poly(c, acc, -0.9f, -0.2f, -0.45f, -0.8f, 0.45f, -0.8f, 0.9f, -0.2f, 0f, 0.9f); poly(c, shade(acc, 0.4f), -0.45f, -0.8f, 0f, -0.2f, 0.45f, -0.8f); poly(c, shade(acc, -0.25f), 0f, -0.2f, 0.9f, -0.2f, 0f, 0.9f) }
            "crystal" -> { poly(c, 0x6FB8E8, -0.35f, -0.9f, 0.35f, -0.9f, 0.7f, -0.1f, 0f, 0.95f, -0.7f, -0.1f); poly(c, 0xA9DDF6, -0.35f, -0.9f, 0f, -0.1f, -0.7f, -0.1f); poly(c, 0x4A8FBF, 0f, -0.1f, 0.7f, -0.1f, 0f, 0.95f) }
            "ore" -> { poly(c, 0x7A7A86, -0.9f, 0.6f, -0.7f, -0.3f, -0.1f, -0.8f, 0.6f, -0.5f, 0.95f, 0.3f, 0.7f, 0.8f); circ(c, -0.2f, 0f, 0.16f, 0xF2C230); circ(c, 0.35f, 0.25f, 0.13f, 0xF2C230); circ(c, 0.1f, -0.4f, 0.1f, 0xF2C230) }
            "ticket" -> { poly(c, 0xF2C230, -0.95f, -0.5f, 0.95f, -0.5f, 0.95f, -0.15f, 0.8f, 0f, 0.95f, 0.15f, 0.95f, 0.5f, -0.95f, 0.5f, -0.95f, 0.15f, -0.8f, 0f, -0.95f, -0.15f); ln(c, -0.3f, -0.4f, -0.3f, 0.4f, 0xC89A1A, 0.06f); circ(c, 0.35f, 0f, 0.17f, 0xC8392E) }
            "star" -> star(c, 0f, 0f, 0.95f, 0.4f, 0xF2C230)
            "mask" -> { oval(c, -0.7f, -0.8f, 0.7f, 0.85f, 0xF4EBD0); oval(c, -0.5f, -0.35f, -0.12f, -0.05f, INK); oval(c, 0.12f, -0.35f, 0.5f, -0.05f, INK); lineP.color = Iso.opaque(INK); lineP.strokeWidth = 0.1f; rect.set(-0.3f, 0.05f, 0.3f, 0.55f); c.drawArc(rect, 20f, 140f, false, lineP) }
            "vase" -> { poly(c, 0x4A8AB8, -0.3f, -0.9f, 0.3f, -0.9f, 0.25f, -0.4f, 0.75f, 0.2f, 0.45f, 0.9f, -0.45f, 0.9f, -0.75f, 0.2f, -0.25f, -0.4f); ln(c, -0.5f, 0.1f, 0.5f, 0.1f, 0xF2C230, 0.12f) }
            "ship" -> { poly(c, 0x8A5A32, -0.95f, 0.15f, 0.95f, 0.15f, 0.6f, 0.8f, -0.6f, 0.8f); ln(c, 0f, 0.15f, 0f, -0.9f, 0x5A3A22, 0.1f); poly(c, 0xF4EBD0, 0.05f, -0.85f, 0.75f, 0.05f, 0.05f, 0.05f); poly(c, shade(0xF4EBD0, -0.1f), -0.05f, -0.6f, -0.6f, 0.05f, -0.05f, 0.05f) }
            "lance" -> { ln(c, -0.9f, 0.9f, 0.85f, -0.85f, 0x8A5A32, 0.16f); poly(c, 0xD0D6DC, 0.7f, -0.9f, 1f, -1f, 0.9f, -0.7f); poly(c, acc, 0.2f, -0.35f, 0.55f, -0.2f, 0.4f, 0.1f) }
            "egg" -> { oval(c, -0.6f, -0.85f, 0.6f, 0.85f, 0xE8E0C8); circ(c, -0.15f, -0.2f, 0.17f, acc); circ(c, 0.2f, 0.25f, 0.2f, acc); circ(c, -0.2f, 0.45f, 0.1f, acc) }
            "feather" -> { poly(c, acc, -0.8f, 0.9f, -0.4f, 0.1f, 0.2f, -0.6f, 0.9f, -0.9f, 0.5f, -0.1f, 0.1f, 0.4f); ln(c, -0.85f, 0.95f, 0.7f, -0.75f, shade(acc, -0.4f), 0.08f) }
            "clock" -> { circ(c, 0f, 0f, 0.95f, 0xF8F4E4); lineP.color = Iso.opaque(acc); lineP.strokeWidth = 0.14f; ln(c, 0f, 0f, 0f, -0.6f, acc, 0.16f); ln(c, 0f, 0f, 0.45f, 0.2f, acc, 0.16f); circ(c, 0f, 0f, 0.1f, acc) }
            "coin" -> { circ(c, 0f, 0f, 0.95f, 0xB8861A); circ(c, 0f, 0f, 0.8f, 0xF2C230); circ(c, 0f, 0f, 0.55f, 0xE0A81A); circ(c, -0.2f, -0.25f, 0.2f, 0xFFE680) }
            "xp" -> star(c, 0f, 0f, 0.95f, 0.42f, 0x5AB0F0)
            "check" -> { ln(c, -0.7f, 0.05f, -0.2f, 0.6f, 0xFFFFFF, 0.3f); ln(c, -0.2f, 0.6f, 0.75f, -0.6f, 0xFFFFFF, 0.3f) }
            "bang" -> { rbox(c, -0.17f, -0.8f, 0.17f, 0.2f, 0.08f, 0xFFFFFF); circ(c, 0f, 0.62f, 0.2f, 0xFFFFFF) }
            "gift" -> { box(c, -0.8f, -0.1f, 0.8f, 0.85f, acc); box(c, -0.9f, -0.5f, 0.9f, -0.1f, shade(acc, 0.2f)); box(c, -0.12f, -0.5f, 0.12f, 0.85f, 0xF2C230); circ(c, -0.3f, -0.7f, 0.25f, 0xF2C230); circ(c, 0.3f, -0.7f, 0.25f, 0xF2C230) }
            "hourglass" -> { poly(c, 0xF4EBD0, -0.6f, -0.85f, 0.6f, -0.85f, 0.05f, 0f, 0.6f, 0.85f, -0.6f, 0.85f, -0.05f, 0f); poly(c, 0xE8B82A, -0.3f, 0.85f, 0.3f, 0.85f, 0f, 0.4f); box(c, -0.7f, -0.95f, 0.7f, -0.8f, 0x6A4A32); box(c, -0.7f, 0.8f, 0.7f, 0.95f, 0x6A4A32) }
            "house" -> { box(c, -0.7f, -0.1f, 0.7f, 0.9f, 0xF0E0B8); poly(c, 0xC8503A, -0.95f, 0f, 0f, -0.9f, 0.95f, 0f); box(c, -0.2f, 0.3f, 0.2f, 0.9f, 0x8A5A32); box(c, 0.35f, 0.15f, 0.6f, 0.4f, 0x7FB4D8); box(c, 0.45f, -0.75f, 0.65f, -0.35f, 0x8A6A52) }
            "door" -> { box(c, -0.6f, -0.9f, 0.6f, 0.9f, 0x5A3A22); poly(c, 0xA8743E, -0.45f, -0.75f, 0.35f, -0.9f, 0.35f, 0.9f, -0.45f, 0.75f); circ(c, 0.15f, 0.05f, 0.09f, 0xF2C230) }
            "cross" -> { ln(c, -0.6f, -0.6f, 0.6f, 0.6f, acc, 0.26f); ln(c, 0.6f, -0.6f, -0.6f, 0.6f, acc, 0.26f) }
            "hammer" -> { ln(c, -0.7f, 0.8f, 0.25f, -0.15f, 0x9A6A3A, 0.24f); poly(c, 0x8A8A96, -0.05f, -0.75f, 0.35f, -0.95f, 0.95f, -0.35f, 0.75f, 0.05f); poly(c, 0xB8B8C4, 0.05f, -0.68f, 0.35f, -0.82f, 0.82f, -0.35f, 0.68f, -0.05f) }
            "anvil" -> { poly(c, 0x5A5A66, -0.95f, -0.45f, 0.75f, -0.45f, 0.95f, -0.2f, 0.4f, -0.1f, 0.3f, 0.35f, -0.3f, 0.35f, -0.4f, -0.1f, -0.75f, -0.15f); box(c, -0.55f, 0.35f, 0.55f, 0.55f, 0x4A4A52); box(c, -0.75f, 0.55f, 0.75f, 0.85f, 0x3A3A42); ln(c, -0.8f, -0.42f, 0.7f, -0.42f, 0x9A9AA6, 0.08f) }
            "castle" -> { box(c, -0.6f, -0.2f, 0.6f, 0.9f, 0xB8B0A0); for (x in floatArrayOf(-0.95f, 0.55f)) { box(c, x, -0.55f, x + 0.4f, 0.9f, 0xA8A090); for (k in 0..1) box(c, x + k * 0.25f, -0.75f, x + k * 0.25f + 0.15f, -0.55f, 0xA8A090) }; for (k in 0..2) box(c, -0.45f + k * 0.35f, -0.4f, -0.3f + k * 0.35f, -0.2f, 0xB8B0A0); poly(c, 0x5A3A22, -0.22f, 0.9f, -0.22f, 0.45f, 0f, 0.25f, 0.22f, 0.45f, 0.22f, 0.9f); box(c, 0.7f, -0.98f, 0.76f, -0.75f, 0x6A4A32); poly(c, acc, 0.76f, -0.98f, 1f, -0.9f, 0.76f, -0.82f) }
            "trophy" -> { lineP.color = Iso.opaque(0xE8B82A); lineP.strokeWidth = 0.14f; rect.set(-0.95f, -0.75f, -0.35f, 0.05f); c.drawArc(rect, 90f, 180f, false, lineP); rect.set(0.35f, -0.75f, 0.95f, 0.05f); c.drawArc(rect, -90f, 180f, false, lineP); poly(c, 0xF2C230, -0.6f, -0.85f, 0.6f, -0.85f, 0.45f, -0.1f, 0f, 0.25f, -0.45f, -0.1f); box(c, -0.1f, 0.2f, 0.1f, 0.6f, 0xC89A1A); box(c, -0.5f, 0.6f, 0.5f, 0.85f, 0x8A5A32); circ(c, -0.2f, -0.55f, 0.1f, 0xFFE680) }
            "chest" -> { box(c, -0.9f, -0.15f, 0.9f, 0.8f, 0x9A6A3A); poly(c, 0xB07A42, -0.9f, -0.15f, -0.75f, -0.7f, 0.75f, -0.7f, 0.9f, -0.15f); box(c, -0.9f, -0.2f, 0.9f, -0.05f, 0xE8B82A); box(c, -0.6f, -0.7f, -0.45f, 0.8f, 0xE8B82A); box(c, 0.45f, -0.7f, 0.6f, 0.8f, 0xE8B82A); box(c, -0.15f, -0.15f, 0.15f, 0.25f, 0xF2C230) }
            "sun" -> { for (i in 0 until 8) { val a = i * PI.toFloat() / 4; ln(c, cos(a) * 0.62f, sin(a) * 0.62f, cos(a) * 0.92f, sin(a) * 0.92f, 0xF2B22A, 0.14f) }; circ(c, 0f, 0f, 0.5f, 0xF2C230); circ(c, -0.15f, -0.15f, 0.18f, 0xFFE680) }
            "camera" -> { rbox(c, -0.95f, -0.45f, 0.95f, 0.75f, 0.15f, 0x4A4A52); box(c, -0.35f, -0.65f, 0.25f, -0.45f, 0x4A4A52); circ(c, 0f, 0.15f, 0.42f, 0xD0D6DC); circ(c, 0f, 0.15f, 0.28f, 0x3A6A9A); circ(c, 0.6f, -0.25f, 0.09f, 0xF2C230) }
            "signpost" -> { box(c, -0.08f, -0.8f, 0.08f, 0.95f, 0x8A5A32); poly(c, 0xD8B070, -0.75f, -0.7f, 0.6f, -0.7f, 0.85f, -0.45f, 0.6f, -0.2f, -0.75f, -0.2f); poly(c, 0xC09050, 0.75f, 0f, -0.6f, 0f, -0.85f, 0.25f, -0.6f, 0.5f, 0.75f, 0.5f) }
            "move" -> { ln(c, -0.8f, 0f, 0.8f, 0f, acc, 0.14f); ln(c, 0f, -0.8f, 0f, 0.8f, acc, 0.14f); poly(c, acc, -0.95f, 0f, -0.6f, -0.3f, -0.6f, 0.3f); poly(c, acc, 0.95f, 0f, 0.6f, -0.3f, 0.6f, 0.3f); poly(c, acc, 0f, -0.95f, -0.3f, -0.6f, 0.3f, -0.6f); poly(c, acc, 0f, 0.95f, -0.3f, 0.6f, 0.3f, 0.6f) }
            "undo" -> { lineP.color = Iso.opaque(acc); lineP.strokeWidth = 0.2f; rect.set(-0.55f, -0.55f, 0.75f, 0.75f); c.drawArc(rect, 200f, 250f, false, lineP); poly(c, acc, -0.95f, -0.2f, -0.35f, -0.05f, -0.6f, 0.45f) }
            "people" -> { circ(c, -0.4f, -0.4f, 0.28f, 0xE5B98E); oval(c, -0.8f, -0.05f, 0f, 0.85f, 0x547F9B); circ(c, 0.4f, -0.3f, 0.28f, 0xBF906B); oval(c, 0f, 0.05f, 0.8f, 0.9f, 0xB17855) }
            else -> circ(c, 0f, 0f, 0.7f, acc)
        }
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, inner: Float, color: Int) {
        path.reset()
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
            val rr = if (i % 2 == 0) r else r * inner / 0.4f * 0.4f
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill(color)
        c.drawPath(path, fillP)
    }
}
