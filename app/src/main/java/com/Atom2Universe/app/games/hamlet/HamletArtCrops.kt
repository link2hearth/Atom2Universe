package com.Atom2Universe.app.games.hamlet

import kotlin.math.cos
import kotlin.math.sin

/** Les champs : une terre labourée et, selon l'avancement, des pousses, des plants, des fruits mûrs ou du flétri. */
object HamletArtCrops {

    private const val SOIL = 0x7A5230
    private const val SOIL_DARK = 0x5A3A20
    private const val WITHER = 0x8A7A52

    /** [stage] : -1 terre nue, 0 pousse, 1 croissance, 2 mûr, 3 flétri. */
    fun field(p: Iso, def: Def, crop: Crop?, stage: Int) {
        val w = def.w.toFloat(); val d = def.d.toFloat()
        val magic = def.magic
        p.shadow(0f, 0f, w, d, 0.15f)
        val border = if (magic) 0x6A5AA8 else 0x8A6A44
        // cadre de bois et terre
        p.box(0.04f, 0.04f, 0f, w - 0.04f, d - 0.04f, 0.1f, border)
        p.top(0.12f, 0.12f, w - 0.12f, d - 0.12f, 0.1f, if (magic) 0x5A4A6A else SOIL)
        // sillons
        val rows = 4
        for (r in 0 until rows) {
            val y = 0.26f + (d - 0.52f) * r / (rows - 1)
            p.line(0.2f, y, 0.11f, w - 0.2f, y, 0.11f, if (magic) 0x3A2E4A else SOIL_DARK, 2.4f)
        }
        if (magic) {
            for (i in 0..5) p.dot(0.3f + (i * 0.31f) % (w - 0.5f), 0.3f + (i * 0.47f) % (d - 0.5f), 0.14f, 1.6f, 0xD8B8FF)
        }
        if (crop == null || stage < 0) return
        // les plants, rangée par rangée de l'arrière vers l'avant
        val cols = if (w > 2f) 5 else 3
        for (r in 0 until rows) {
            val y = 0.26f + (d - 0.52f) * r / (rows - 1)
            for (c in 0 until cols) {
                val x = 0.3f + (w - 0.6f) * (c + 0.5f * (r % 2)) / (cols - 0.5f).coerceAtLeast(1f)
                plant(p, crop, x, y, stage, r * 7 + c)
            }
        }
        if (crop.magic && stage in 0..2) {
            for (i in 0..3) p.dot(0.4f + i * 0.45f % (w - 0.4f), 0.4f + (i * 0.7f) % (d - 0.4f), 0.5f + (i % 3) * 0.2f, 1.8f, 0xFFF4B0)
        }
    }

    private fun plant(p: Iso, c: Crop, x: Float, y: Float, stage: Int, seed: Int) {
        if (stage == 3) { wither(p, x, y, c.kind); return }
        val g = when (stage) { 0 -> 0.34f; 1 -> 0.7f; else -> 1f }
        val leaf = c.leaf
        val fruit = c.fruit
        val z0 = 0.1f
        when (c.kind) {
            "grain" -> {
                val h = 0.12f + 0.42f * g
                for (k in -2..2) {
                    val dx = k * 0.035f; val sway = (k % 2) * 0.012f
                    p.line(x + dx, y, z0, x + dx + sway, y, z0 + h, if (stage == 2) Iso.mix(leaf, fruit, 0.6f) else leaf, 1.6f)
                    if (stage == 2) p.oval(x + dx + sway, y, z0 + h + 0.04f, 1.8f, 4.4f, fruit)
                }
            }
            "stalk" -> {
                val h = 0.15f + 0.7f * g
                p.line(x, y, z0, x, y, z0 + h, Iso.shade(leaf, -0.1f), 3f)
                p.line(x, y, z0 + h * 0.4f, x - 0.14f * g, y, z0 + h * 0.62f, leaf, 2.4f)
                p.line(x, y, z0 + h * 0.55f, x + 0.15f * g, y, z0 + h * 0.8f, leaf, 2.4f)
                p.line(x, y, z0 + h * 0.75f, x - 0.1f * g, y, z0 + h * 0.98f, leaf, 2.4f)
                if (stage == 2) { p.oval(x + 0.05f, y + 0.04f, z0 + h * 0.58f, 3.2f, 7f, fruit); p.oval(x - 0.02f, y, z0 + h * 1.0f, 2f, 5f, Iso.shade(fruit, 0.2f)) }
            }
            "root" -> {
                for (k in 0..3) {
                    val a = k * 1.5708f + seed * 0.3f
                    p.line(x, y, z0 + 0.02f, x + cos(a) * 0.12f * g, y + sin(a) * 0.08f * g, z0 + 0.08f + 0.2f * g, leaf, 2.4f)
                }
                p.dot(x, y, z0 + 0.02f, 2.6f + 3f * g, leaf)
                if (stage == 2) p.oval(x, y + 0.02f, z0 + 0.03f, 4.6f, 3.2f, fruit)
            }
            "head" -> {
                val r = 0.07f + 0.1f * g
                p.ball(x, y, z0 + r * 0.9f, r, leaf, r * 0.85f)
                if (g > 0.5f) p.ball(x + 0.01f, y, z0 + r * 1.05f, r * 0.62f, Iso.shade(if (stage == 2) fruit else leaf, 0.2f), r * 0.55f)
            }
            "bush" -> {
                val r = 0.06f + 0.11f * g
                p.ball(x, y, z0 + r, r, leaf, r * 0.8f)
                if (g > 0.6f) p.ball(x - r * 0.4f, y, z0 + r * 1.3f, r * 0.7f, Iso.shade(leaf, 0.1f), r * 0.6f)
                if (stage == 2) for (k in 0..3) p.dot(x + (k - 1.5f) * r * 0.5f, y + r * 0.5f, z0 + r * (0.6f + (k % 2) * 0.5f), 2.6f, fruit)
            }
            "vine" -> {
                val r = 0.08f + 0.1f * g
                p.oval(x - 0.05f, y, z0 + 0.04f, 7f * g + 3f, 3f * g + 1.5f, leaf)
                p.oval(x + 0.07f, y + 0.03f, z0 + 0.04f, 6f * g + 2f, 3f * g + 1.2f, Iso.shade(leaf, 0.1f))
                if (g > 0.6f) p.ball(x + 0.03f, y + 0.07f, z0 + r * 0.8f, if (stage == 2) r else r * 0.5f, if (stage == 2) fruit else leaf, r * 0.75f)
            }
            "tall" -> {
                val h = 0.2f + 0.7f * g
                p.line(x, y, z0, x, y, z0 + h + 0.1f, 0x8A6A44, 1.8f)
                for (k in 0..3) {
                    val z = z0 + 0.1f + h * 0.22f * k
                    p.oval(x - 0.04f, y, z, 3.4f, 2.4f, leaf); p.oval(x + 0.05f, y + 0.02f, z + 0.06f, 3.4f, 2.4f, Iso.shade(leaf, 0.1f))
                    if (stage == 2 && k > 0) p.dot(x + 0.03f, y + 0.04f, z - 0.03f, 2.4f, fruit)
                }
            }
            else -> { // flower
                val h = 0.15f + 0.55f * g
                p.line(x, y, z0, x, y, z0 + h, leaf, 2f)
                p.oval(x - 0.05f, y, z0 + h * 0.4f, 4.4f, 2f, leaf)
                if (g > 0.6f) {
                    if (c.id == "sunflower") { p.oval(x, y + 0.03f, z0 + h, 7f, 7f, if (stage == 2) fruit else leaf); p.oval(x, y + 0.04f, z0 + h, 3.6f, 3.6f, 0x5A3A1E) }
                    else { p.oval(x, y, z0 + h + 0.04f, 2.6f, 8f, if (stage == 2) fruit else leaf); p.dot(x - 0.05f, y, z0 + h - 0.03f, 2f, if (stage == 2) fruit else leaf) }
                }
            }
        }
    }

    private fun wither(p: Iso, x: Float, y: Float, kind: String) {
        val z0 = 0.1f
        for (k in -1..1) p.line(x + k * 0.04f, y, z0, x + k * 0.07f, y + 0.02f, z0 + 0.22f - kotlin.math.abs(k) * 0.05f, WITHER, 1.8f)
        p.dot(x + 0.04f, y, z0 + 0.04f, 2f, 0x6A5A3A)
        if (kind == "head" || kind == "vine") p.oval(x, y, z0 + 0.05f, 4f, 2.4f, 0x7A6A42)
    }
}
