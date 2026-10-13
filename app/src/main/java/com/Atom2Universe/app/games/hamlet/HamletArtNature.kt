package com.Atom2Universe.app.games.hamlet

import kotlin.math.cos
import kotlin.math.sin

/** Arbres, buissons, fleurs, rochers : tout ce qui pousse ou traîne. */
object HamletArtNature {

    private const val TRUNK = 0x7A5632

    /** Pseudo-hasard stable : même graine, même résultat, pour que les sprites ne changent pas d'un lancement à l'autre. */
    private fun hash(i: Int): Float {
        var h = i * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    fun draw(p: Iso, def: Def, w: Float, d: Float) {
        val l = def.look
        val cx = w / 2; val cy = d / 2
        val a = l.a; val b = l.b
        val big = w > 1
        val s = if (big) 1.55f else 1f
        when (l.kind) {
            "t_round", "t_oval", "t_pine", "t_birch", "t_dead", "t_willow", "t_bonsai", "t_palm" -> p.shadow(cx - 0.4f * s, cy - 0.4f * s, cx + 0.4f * s, cy + 0.4f * s, 0.3f)
            else -> p.shadow(cx - 0.35f, cy - 0.35f, cx + 0.35f, cy + 0.35f, 0.2f)
        }
        when (l.kind) {
            "t_round" -> {
                p.cyl(cx, cy, 0f, 0.5f * s, 0.075f * s, TRUNK)
                if (l.n == 2) p.oval(cx, cy, 0.95f * s, 40f, 36f, Iso.withAlpha(b, 0.25f))
                p.ball(cx - 0.17f * s, cy - 0.08f * s, 0.8f * s, 0.34f * s, Iso.shade(a, -0.1f), 0.3f * s)
                p.ball(cx + 0.2f * s, cy - 0.05f * s, 0.78f * s, 0.32f * s, Iso.shade(a, -0.05f), 0.3f * s)
                p.ball(cx, cy + 0.04f * s, 0.98f * s, 0.4f * s, a, 0.36f * s)
                if (b != 0) for (i in 0..5) p.dot(cx + (hash(i) - 0.5f) * 0.6f * s, cy + 0.1f + (hash(i + 9) - 0.5f) * 0.3f * s, 0.78f * s + hash(i + 3) * 0.35f * s, 3.2f, b)
            }
            "t_oval" -> {
                p.cyl(cx, cy, 0f, 0.4f, 0.06f, TRUNK)
                val h = if (l.n == 1) 1.0f else 0.85f
                p.ball(cx, cy, 0.5f + h * 0.55f, 0.27f, a, h * 0.58f)
                p.ball(cx - 0.05f, cy - 0.03f, 0.55f + h * 0.7f, 0.2f, Iso.shade(a, 0.1f), h * 0.35f)
            }
            "t_pine" -> {
                p.cyl(cx, cy, 0f, 0.4f * s, 0.07f * s, TRUNK)
                for (k in 0..3) p.cone(cx, cy, (0.22f + k * 0.34f) * s, (0.62f - k * 0.08f) * s, (0.46f - k * 0.1f) * s, if (k % 2 == 0) a else Iso.shade(a, 0.08f))
            }
            "t_birch" -> {
                p.cyl(cx, cy, 0f, 0.8f, 0.06f, 0xEFEBE0)
                for (z in listOf(0.2f, 0.38f, 0.55f)) p.line(cx - 0.05f, cy + 0.05f, z, cx + 0.05f, cy + 0.05f, z, 0x2A2A2E, 1.4f)
                p.ball(cx - 0.12f, cy, 1.0f, 0.24f, Iso.shade(a, -0.05f), 0.25f); p.ball(cx + 0.14f, cy, 1.05f, 0.22f, a, 0.24f); p.ball(cx, cy, 1.25f, 0.2f, Iso.shade(a, 0.08f), 0.22f)
            }
            "t_dead" -> {
                p.cyl(cx, cy, 0f, 0.9f, 0.07f, 0x4A3A32)
                p.line(cx, cy, 0.6f, cx - 0.35f, cy, 1.05f, 0x4A3A32, 3f); p.line(cx, cy, 0.75f, cx + 0.36f, cy, 1.15f, 0x4A3A32, 3f)
                p.line(cx - 0.35f, cy, 1.05f, cx - 0.5f, cy, 1.25f, 0x4A3A32, 2f); p.line(cx + 0.36f, cy, 1.15f, cx + 0.3f, cy, 1.45f, 0x4A3A32, 2f)
                p.line(cx, cy, 0.9f, cx + 0.05f, cy, 1.3f, 0x4A3A32, 2.4f)
            }
            "t_willow" -> {
                p.cyl(cx, cy, 0f, 0.6f, 0.09f, 0x6A5638)
                p.ball(cx, cy, 1.0f, 0.5f, a, 0.32f)
                for (i in 0..11) { val ang = i * 0.5236f; val rx = cx + cos(ang) * 0.42f; val ry = cy + sin(ang) * 0.42f; p.line(rx, ry, 0.98f, rx, ry, 0.35f + hash(i) * 0.15f, Iso.shade(a, if (i % 2 == 0) -0.1f else 0.1f), 2.4f) }
            }
            "t_bonsai" -> {
                p.box(cx - 0.17f, cy - 0.12f, 0f, cx + 0.17f, cy + 0.12f, 0.12f, 0x7A4A3A)
                p.line(cx, cy, 0.12f, cx + 0.06f, cy, 0.3f, TRUNK, 4f); p.line(cx + 0.06f, cy, 0.3f, cx - 0.04f, cy, 0.4f, TRUNK, 3f)
                p.ball(cx - 0.1f, cy, 0.48f, 0.15f, a, 0.1f); p.ball(cx + 0.12f, cy, 0.4f, 0.13f, Iso.shade(a, 0.1f), 0.09f)
            }
            "t_palm" -> {
                p.line(cx, cy, 0f, cx + 0.12f, cy, 0.55f, 0x9A7A4A, 6f); p.line(cx + 0.12f, cy, 0.55f, cx + 0.05f, cy, 1.15f, 0x9A7A4A, 5f)
                for (i in 0..6) {
                    val ang = i * 0.8976f
                    val ex = cx + 0.05f + cos(ang) * 0.5f; val ey = cy + sin(ang) * 0.5f
                    p.line(cx + 0.05f, cy, 1.15f, ex, ey, 0.92f, 0x4F9D37, 4f)
                    p.line(cx + 0.05f, cy, 1.15f, (cx + 0.05f + ex) / 2 + 0.02f, (cy + ey) / 2, 1.08f, 0x6AB84A, 3f)
                }
                p.ball(cx + 0.05f, cy, 1.12f, 0.06f, 0x8A5A32, 0.06f)
            }
            "n_mushroom" -> {
                val k = if (l.n == 1) 1.7f else 1f
                for ((i, off) in (if (l.n == 1) listOf(0f to 0f) else listOf(-0.2f to -0.05f, 0.12f to 0.1f, 0.22f to -0.14f)).withIndex()) {
                    val mx = cx + off.first; val my = cy + off.second
                    val sc = k * (1f - i * 0.2f)
                    p.cyl(mx, my, 0f, 0.2f * sc, 0.055f * sc, 0xF0E8D0)
                    p.ball(mx, my, 0.22f * sc, 0.17f * sc, a, 0.1f * sc)
                    p.dot(mx - 0.05f * sc, my + 0.06f * sc, 0.28f * sc, 2f * sc, 0xFFFFFF); p.dot(mx + 0.06f * sc, my + 0.02f * sc, 0.3f * sc, 1.6f * sc, 0xFFFFFF)
                }
            }
            "n_fairy" -> {
                p.disc(cx, cy, 0f, 0.75f, 0x8FD070)
                for (i in 0..9) {
                    val ang = i * 0.6283f; val mx = cx + cos(ang) * 0.6f; val my = cy + sin(ang) * 0.6f
                    p.cyl(mx, my, 0f, 0.1f, 0.025f, 0xF0E8D0); p.ball(mx, my, 0.11f, 0.065f, 0xE8402A, 0.045f); p.dot(mx, my + 0.02f, 0.14f, 1f, 0xFFFFFF)
                }
                p.dot(cx, cy, 0.3f, 2f, 0xFFF4B0); p.dot(cx + 0.2f, cy + 0.1f, 0.5f, 1.6f, 0xFFF4B0); p.dot(cx - 0.15f, cy - 0.1f, 0.4f, 1.6f, 0xB8FFD8)
            }
            "n_cactus" -> {
                p.cyl(cx, cy, 0f, 0.75f, 0.12f, 0x4F9D5A)
                p.line(cx - 0.12f, cy, 0.3f, cx - 0.3f, cy, 0.3f, 0x4F9D5A, 5f); p.line(cx - 0.3f, cy, 0.3f, cx - 0.3f, cy, 0.58f, 0x4F9D5A, 5f)
                p.line(cx + 0.12f, cy, 0.45f, cx + 0.28f, cy, 0.45f, 0x4F9D5A, 5f); p.line(cx + 0.28f, cy, 0.45f, cx + 0.28f, cy, 0.68f, 0x4F9D5A, 5f)
                p.dot(cx, cy + 0.1f, 0.78f, 3f, 0xF88AB8)
            }
            "n_bamboo" -> {
                for ((i, off) in listOf(-0.18f to 0f, 0.05f to 0.12f, 0.2f to -0.08f, -0.05f to -0.15f).withIndex()) {
                    val hh = 1.1f + i * 0.15f
                    p.cyl(cx + off.first, cy + off.second, 0f, hh, 0.04f, 0x8AB84A)
                    for (z in 1..3) p.line(cx + off.first - 0.04f, cy + off.second, hh * z / 4f, cx + off.first + 0.04f, cy + off.second, hh * z / 4f, 0x4F7A2A, 1.4f)
                    p.line(cx + off.first, cy + off.second, hh, cx + off.first + 0.2f, cy + off.second, hh - 0.12f, 0x5AAD3C, 2.4f)
                }
            }
            "n_reeds" -> for (i in 0..8) {
                val rx = cx + (hash(i) - 0.5f) * 0.7f; val ry = cy + (hash(i + 20) - 0.5f) * 0.6f; val hh = 0.5f + hash(i + 40) * 0.4f
                p.line(rx, ry, 0f, rx + 0.03f, ry, hh, 0x6AA040, 1.8f)
                if (i % 3 == 0) p.cyl(rx + 0.03f, ry, hh - 0.14f, hh, 0.025f, 0x7A4A2A)
            }
            "n_topiary" -> {
                p.box(cx - 0.12f, cy - 0.12f, 0f, cx + 0.12f, cy + 0.12f, 0.16f, 0x9A6A52)
                when (l.n) {
                    1 -> p.cone(cx, cy, 0.14f, 0.85f, 0.3f, a)
                    2 -> p.box(cx - 0.25f, cy - 0.25f, 0.14f, cx + 0.25f, cy + 0.25f, 0.72f, a)
                    3 -> { p.ball(cx, cy, 0.36f, 0.3f, a, 0.2f); p.ball(cx, cy, 0.62f, 0.23f, Iso.shade(a, 0.05f), 0.18f); p.ball(cx, cy, 0.84f, 0.15f, Iso.shade(a, 0.1f), 0.14f); p.ball(cx, cy, 1.0f, 0.08f, Iso.shade(a, 0.15f), 0.1f) }
                    else -> { p.ball(cx, cy, 0.55f, 0.32f, a, 0.32f) }
                }
            }
            "n_bush" -> {
                p.ball(cx - 0.12f, cy, 0.22f, 0.25f, Iso.shade(a, -0.1f), 0.2f); p.ball(cx + 0.14f, cy + 0.02f, 0.22f, 0.24f, Iso.shade(a, -0.05f), 0.2f)
                p.ball(cx, cy + 0.06f, 0.32f, 0.26f, a, 0.22f)
                if (b != 0) for (i in 0..6) p.dot(cx + (hash(i) - 0.5f) * 0.5f, cy + 0.1f + (hash(i + 5) - 0.5f) * 0.2f, 0.25f + hash(i + 2) * 0.2f, 3f, b)
            }
            "n_hedge" -> {
                p.box(0.08f, 0.2f, 0f, 0.92f, 0.8f, 0.55f, a)
                for (i in 0..6) p.dot(0.15f + i * 0.12f, 0.8f, 0.15f + hash(i) * 0.35f, 3f, Iso.shade(a, 0.18f))
            }
            "n_fern" -> for (i in 0..6) {
                val ang = i * 0.8976f
                p.line(cx, cy, 0f, cx + cos(ang) * 0.32f, cy + sin(ang) * 0.32f, 0.38f - (i % 2) * 0.1f, if (i % 2 == 0) 0x4F9D37 else 0x6AB84A, 3.2f)
            }
            "n_flowers" -> flowers(p, a, l.n)
            "n_sunflower" -> for (i in 0..3) {
                val fx = 0.25f + (i % 2) * 0.5f + (hash(i) - 0.5f) * 0.1f; val fy = 0.25f + (i / 2) * 0.5f
                p.line(fx, fy, 0f, fx, fy, 0.75f, 0x4F8D37, 2.4f)
                p.oval(fx, fy + 0.04f, 0.78f, 9f, 9f, 0xF2C21A); p.oval(fx, fy + 0.05f, 0.78f, 4.5f, 4.5f, 0x5A3A1E)
                p.oval(fx - 0.1f, fy, 0.3f, 6f, 2.4f, 0x4F9D37)
            }
            "n_rock" -> {
                val k = if (big) 1.8f else 1f
                p.ball(cx, cy, 0.14f * k, 0.3f * k, a, 0.2f * k)
                p.ball(cx + 0.18f * k, cy + 0.08f * k, 0.1f * k, 0.18f * k, Iso.shade(a, 0.08f), 0.13f * k)
                p.ball(cx - 0.18f * k, cy + 0.1f * k, 0.08f * k, 0.14f * k, Iso.shade(a, -0.08f), 0.1f * k)
                p.dot(cx - 0.04f * k, cy + 0.05f * k, 0.3f * k, 2f, Iso.shade(a, 0.35f))
            }
            "n_stump" -> {
                p.cyl(cx, cy, 0f, 0.22f, 0.17f, 0x8A5A32); p.disc(cx, cy, 0.22f, 0.15f, 0xD8B070); p.disc(cx, cy, 0.225f, 0.09f, 0xC09050); p.disc(cx, cy, 0.23f, 0.03f, 0xD8B070)
            }
            "n_log" -> {
                p.line(cx - 0.35f, cy - 0.05f, 0.1f, cx + 0.3f, cy + 0.08f, 0.1f, 0x7A5632, 9f)
                p.dot(cx + 0.3f, cy + 0.08f, 0.1f, 4.4f, 0xD8B070); p.dot(cx + 0.3f, cy + 0.08f, 0.1f, 2f, 0xC09050)
                p.ball(cx - 0.1f, cy - 0.05f, 0.17f, 0.1f, 0x5AAD3C, 0.05f)
            }
            "n_pumpkins" -> {
                p.ball(cx, cy, 0.04f, 0.45f, 0x6AA040, 0.04f)
                for ((i, off) in listOf(-0.22f to -0.06f, 0.16f to 0.1f, 0.0f to 0.2f, 0.25f to -0.14f).withIndex()) {
                    p.ball(cx + off.first, cy + off.second, 0.12f, 0.15f - i * 0.01f, 0xF08A1C, 0.11f)
                    p.line(cx + off.first, cy + off.second, 0.22f, cx + off.first, cy + off.second, 0.28f, 0x4F7A2A, 2f)
                }
            }
            else -> p.ball(cx, cy, 0.3f, 0.3f, a, 0.3f)
        }
    }

    private fun flowers(p: Iso, color: Int, kind: Int) {
        val n = 9
        // du fond vers l'avant pour que les fleurs se recouvrent bien
        val items = (0 until n).map { i -> Triple(0.14f + hash(i) * 0.72f, 0.14f + hash(i + 31) * 0.72f, i) }.sortedBy { it.first + it.second }
        for ((fx, fy, i) in items) {
            val hh = 0.2f + hash(i + 70) * 0.18f
            val col = if (kind == 2) intArrayOf(0xE0304A, 0xF4D42A, 0xFFFFFF, 0xA84AD8, 0xF88AB8, 0x4A7AE8)[i % 6] else color
            if (kind == 1) { // lavande : épis
                p.line(fx, fy, 0f, fx, fy, hh + 0.2f, 0x6A8F4A, 1.4f)
                p.oval(fx, fy, hh + 0.28f, 2.6f, 7f, col)
                continue
            }
            p.line(fx, fy, 0f, fx, fy, hh, 0x4F8D37, 1.4f)
            p.oval(fx - 0.04f, fy, hh * 0.5f, 4f, 1.8f, 0x4F9D37)
            p.dot(fx, fy, hh + 0.04f, 4.4f, col)
            p.dot(fx, fy, hh + 0.04f, 1.8f, if (col == 0xFFFFFF || col == 0xF4D42A) 0xF08A1C else 0xFFE680)
        }
    }
}
