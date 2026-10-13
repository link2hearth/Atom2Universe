package com.Atom2Universe.app.games.hamlet

import kotlin.math.max
import kotlin.math.min

/**
 * Maisons et ateliers. Chaque fonction dessine dans une empreinte de [w] × [d] tuiles, le coin
 * arrière du terrain en (0, 0). Les couleurs viennent de la fiche de l'objet.
 */
object HamletArtHomes {

    private const val STONE = 0xB4AEA2
    private const val DOOR = 0x7A4A28

    fun home(p: Iso, look: Look, w: Float, d: Float) {
        val a = look.a; val b = look.b; val c = look.c
        when (look.kind) {
            "h_cot" -> gableHouse(p, w, d, a, b, c, floors = 1, chimney = true, lines = 3)
            "h_hut" -> hut(p, w, d, a, b, c)
            "h_farm" -> { gableHouse(p, w, d, a, b, c, floors = 1, wallH = 0.95f, chimney = true, lines = 3); porch(p, w, d, c) }
            "h_cabin" -> cabin(p, w, d, a, b)
            "h_shack" -> shack(p, w, d, a, b, c)
            "h_yurt" -> yurt(p, w, d, a, b, c)
            "h_villa" -> villa(p, w, d, a, b, c)
            "h_timber" -> timber(p, w, d, a, b, c)
            "h_mush" -> mushroom(p, w, d, a, b, c)
            "h_town" -> townhouse(p, w, d, a, b, c)
            "h_burrow" -> burrow(p, w, d, a, b, c)
            "h_tree" -> treehouse(p, w, d, a, b, c)
            "h_manor" -> manor(p, w, d, a, b, c, false)
            "h_mansion" -> manor(p, w, d, a, b, c, true)
            "h_guild" -> guild(p, w, d, a, b, c)
            "h_watch" -> watch(p, w, d, a, b, c)
            "h_tower" -> roundTower(p, w, d, a, b, c)
            "h_long" -> gableHouse(p, w, d, a, b, c, floors = 1, wallH = 0.7f, roofH = 0.85f, ov = 0.2f, chimney = false, lines = 4, smoke = true)
            "h_chalet" -> chalet(p, w, d, a, b, c)
            "h_chateau" -> fortress(p, w, d, a, b, c, domes = false, towers = 4)
            "h_palace" -> fortress(p, w, d, a, b, c, domes = true, towers = 4)
            "h_citadel" -> fortress(p, w, d, a, b, c, domes = false, towers = 8)
            else -> gableHouse(p, w, d, a, b, c)
        }
    }

    fun shop(p: Iso, def: Def, w: Float, d: Float, staticParts: Boolean = true) {
        val l = def.look
        val a = l.a; val b = l.b; val c = l.c
        when (l.kind) {
            "s_shop" -> { gableHouse(p, w, d, a, b, DOOR, floors = 1, wallH = 0.85f, chimney = true, lines = 3, door = false); awning(p, w, d, c, def.glyph) }
            "s_lumber" -> lumber(p, w, d, a, b, c, def.glyph)
            "s_hall" -> hall(p, w, d, a, b, c, def.glyph)
            "s_smith" -> smith(p, w, d, a, b, c, def.glyph)
            "s_glass" -> glass(p, w, d, a, b, c)
            "s_stable" -> stable(p, w, d, a, b, c)
            "s_barn" -> barn(p, w, d, a, b, c)
            "s_tower" -> tower(p, w, d, a, b, c, l.n, def.glyph)
            "s_mill" -> mill(p, w, d, a, b, c, l.n, staticParts)
            "s_chapel" -> chapel(p, w, d, a, b, c)
            "s_vine" -> vineyard(p, w, d, a, b, c)
            "s_pagoda" -> pagoda(p, w, d, a, b, c)
            "s_theater" -> theater(p, w, d, a, b, c, l.n)
            "s_circus" -> circus(p, w, d, a, b, c)
            "s_arena" -> arena(p, w, d, a, b, c)
            "s_mine" -> mine(p, w, d, a, b, c)
            "s_dock" -> dock(p, w, d, a, b, c)
            "s_lair" -> lair(p, w, d, a, b, c, l.n)
            else -> gableHouse(p, w, d, a, b, c)
        }
    }

    // ------------------------------------------------------------------ éléments communs

    private fun base(p: Iso, w: Float, d: Float, m: Float = 0.1f, color: Int = STONE, z: Float = 0.07f) {
        p.shadow(0f, 0f, w, d)
        p.box(m - 0.03f, m - 0.03f, 0f, w - m + 0.03f, d - m + 0.03f, z, color)
    }

    /** Fenêtres régulières sur la façade avant (+y) et le flanc (+x). */
    private fun windows(p: Iso, x0: Float, y0: Float, x1: Float, y1: Float, z0: Float, floors: Int, wallH: Float, skipFront: Float = -1f) {
        val nx = max(1, ((x1 - x0) / 0.62f).toInt())
        val ny = max(1, ((y1 - y0) / 0.62f).toInt())
        for (f in 0 until floors) {
            val zc = z0 + f * wallH + wallH * 0.55f
            for (i in 0 until nx) {
                val u = x0 + (x1 - x0) * (i + 0.5f) / nx
                if (f == 0 && skipFront >= 0 && kotlin.math.abs(u - skipFront) < 0.25f) continue
                p.windowY(y1, u, zc, 0.22f, 0.28f)
            }
            for (j in 0 until ny) {
                val u = y0 + (y1 - y0) * (j + 0.5f) / ny
                p.windowX(x1, u, zc, 0.22f, 0.28f)
            }
        }
    }

    fun gableHouse(
        p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int,
        floors: Int = 1, wallH: Float = 0.8f, roofH: Float = 0.6f, ov: Float = 0.12f, chimney: Boolean = true,
        lines: Int = 3, door: Boolean = true, smoke: Boolean = false, doorColor: Int = DOOR,
    ) {
        val m = 0.14f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d)
        val z0 = 0.07f
        val top = z0 + floors * wallH
        p.box(x0, y0, z0, x1, y1, top, wall)
        val alongX = (x1 - x0) >= (y1 - y0)
        val doorU = if (alongX) x0 + (x1 - x0) * 0.5f else (x0 + x1) / 2
        if (door) p.doorY(y1, doorU, 0.3f, 0.56f, doorColor)
        windows(p, x0, y0, x1, y1, z0, floors, wallH, if (door) doorU else -1f)
        // bandeau de couleur au pied du toit
        p.faceY(y1 + 0.004f, x0, x1, top - 0.07f, top, trim)
        p.faceX(x1 + 0.004f, y0, y1, top - 0.07f, top, p.right(trim))
        p.gable(x0, y0, x1, y1, top, roofH, roof, p.right(wall), alongX, ov, lines)
        if (chimney) {
            val cx = x0 + (x1 - x0) * 0.28f
            val cy = y0 + (y1 - y0) * 0.3f
            p.chimney(cx, cy, top + roofH * 0.2f, top + roofH + 0.28f)
            if (smoke) p.smoke(cx, cy, top + roofH + 0.28f)
        } else if (smoke) {
            p.smoke((x0 + x1) / 2, (y0 + y1) / 2, top + roofH)
        }
    }

    private fun porch(p: Iso, w: Float, d: Float, trim: Int) {
        val y = d - 0.12f
        val x = w * 0.5f
        p.box(x - 0.34f, y, 0.0f, x + 0.34f, y + 0.3f, 0.05f, 0xA89A80)
        p.box(x - 0.32f, y + 0.26f, 0.05f, x - 0.27f, y + 0.31f, 0.72f, trim)
        p.box(x + 0.27f, y + 0.26f, 0.05f, x + 0.32f, y + 0.31f, 0.72f, trim)
        p.box(x - 0.4f, y - 0.02f, 0.72f, x + 0.4f, y + 0.36f, 0.79f, p.shade(trim, 0.1f))
    }

    private fun Iso.shade(c: Int, k: Float) = Iso.shade(c, k)

    private fun awning(p: Iso, w: Float, d: Float, color: Int, glyph: String) {
        val y1 = d - 0.14f
        val x0 = 0.3f; val x1 = w - 0.3f
        val strips = max(4, ((x1 - x0) / 0.19f).toInt())
        val xs = (x1 - x0) / strips
        for (i in 0 until strips) {
            val xa = x0 + i * xs; val xb = xa + xs
            val col = if (i % 2 == 0) color else 0xF8F4EA
            p.face(col, xa, y1, 0.66f, xb, y1, 0.66f, xb, y1 + 0.26f, 0.5f, xa, y1 + 0.26f, 0.5f)
        }
        // porte
        p.doorY(y1, w * 0.5f, 0.3f, 0.46f, DOOR)
        // enseigne sur potence, côté droit de la façade
        val sx = w - 0.28f
        p.line(sx, y1 + 0.2f, 0.95f, sx, y1 + 0.2f, 0.1f, 0x5A4A32, 2.4f)
        val cx = p.px(sx, y1 + 0.2f)
        val cy = p.py(sx, y1 + 0.2f, 0.82f)
        p.flat(0xF2E4B8, cx - 13f, cy - 12f, cx + 13f, cy - 12f, cx + 13f, cy + 12f, cx - 13f, cy + 12f)
        HamletGlyphs.draw(p.c, glyph, cx, cy, 20f, color)
    }

    // ------------------------------------------------------------------ maisons

    private fun hut(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        base(p, w, d, 0.22f)
        val x0 = 0.3f; val y0 = 0.3f; val x1 = w - 0.3f; val y1 = d - 0.3f
        p.box(x0, y0, 0.07f, x1, y1, 0.62f, wall)
        p.doorY(y1, (x0 + x1) / 2, 0.26f, 0.42f, DOOR)
        p.windowX(x1, (y0 + y1) / 2, 0.38f, 0.2f, 0.2f)
        p.pyramid((x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2 + 0.2f, (y1 - y0) / 2 + 0.2f, 0.56f, 0.8f, roof, 4)
    }

    private fun cabin(p: Iso, w: Float, d: Float, wall: Int, roof: Int) {
        val m = 0.16f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d, color = 0x8A8478)
        p.box(x0, y0, 0.07f, x1, y1, 0.9f, wall)
        val dark = Iso.shade(wall, -0.4f)
        var z = 0.2f
        while (z < 0.9f) {
            p.line(x0, y1, z, x1, y1, z, dark, 1.4f)
            p.line(x1, y0, z, x1, y1, z, dark, 1.4f)
            z += 0.14f
        }
        p.doorY(y1, (x0 + x1) / 2, 0.3f, 0.55f, 0x4A2E18)
        p.windowY(y1, x0 + 0.3f, 0.55f, 0.2f, 0.22f)
        p.windowX(x1, (y0 + y1) / 2, 0.55f, 0.2f, 0.22f)
        p.gable(x0, y0, x1, y1, 0.9f, 0.55f, roof, Iso.shade(wall, -0.3f), true, 0.14f, 3)
        p.chimney(x0 + 0.4f, y0 + 0.3f, 1.1f, 1.55f, 0x8A8478)
        p.smoke(x0 + 0.4f, y0 + 0.3f, 1.55f)
    }

    private fun shack(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        p.shadow(0f, 0f, w, d)
        val x0 = 0.25f; val y0 = 0.25f; val x1 = w - 0.25f; val y1 = d - 0.25f
        for ((px, py) in listOf(x0 + 0.05f to y0 + 0.05f, x1 - 0.1f to y0 + 0.05f, x0 + 0.05f to y1 - 0.1f, x1 - 0.1f to y1 - 0.1f))
            p.box(px, py, 0f, px + 0.07f, py + 0.07f, 0.34f, 0x6A4A2E)
        p.box(x0, y0, 0.3f, x1, y1, 0.34f, 0x8A6A44)
        p.box(x0 + 0.05f, y0 + 0.05f, 0.34f, x1 - 0.05f, y1 - 0.05f, 1.0f, wall)
        p.doorY(y1 - 0.05f, (x0 + x1) / 2, 0.26f, 0.5f, 0x4A2E18)
        p.windowX(x1 - 0.05f, (y0 + y1) / 2, 0.7f, 0.2f, 0.2f)
        p.gable(x0 + 0.05f, y0 + 0.05f, x1 - 0.05f, y1 - 0.05f, 1.0f, 0.4f, roof, Iso.shade(wall, -0.3f), true, 0.12f, 2)
        // échelle
        p.line(x0 + 0.5f, y1 + 0.02f, 0.33f, x0 + 0.5f, y1 + 0.28f, 0f, 0x6A4A2E, 2f)
        p.line(x0 + 0.62f, y1 + 0.02f, 0.33f, x0 + 0.62f, y1 + 0.28f, 0f, 0x6A4A2E, 2f)
        // filet de pêche
        p.line(x1 - 0.1f, y0 + 0.2f, 0.9f, x1 + 0.15f, y0 + 0.5f, 0.3f, trim, 1.4f)
    }

    private fun yurt(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        val r = min(w, d) * 0.4f
        p.cyl(cx, cy, 0f, 0.6f, r, wall)
        p.cone(cx, cy, 0.58f, 0.62f, r + 0.1f, roof)
        p.oval(cx, cy, 1.2f, 4f, 3f, 0xE8D8B0)
        // porte et frise
        p.faceY(cy + r * 0.72f, cx - 0.14f, cx + 0.14f, 0.04f, 0.46f, 0x5A3A22)
        p.line(cx - r, cy, 0.5f, cx, cy + r, 0.5f, trim, 2.2f)
    }

    private fun villa(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        val m = 0.14f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d)
        p.box(x0, y0, 0.07f, x1, y1, 1.62f, wall)
        p.faceY(y1 + 0.004f, x0, x1, 0.82f, 0.9f, trim)
        p.faceX(x1 + 0.004f, y0, y1, 0.82f, 0.9f, p.right(trim))
        p.doorY(y1, (x0 + x1) / 2, 0.3f, 0.58f, DOOR)
        windows(p, x0, y0, x1, y1, 0.07f, 2, 0.78f, (x0 + x1) / 2)
        // balcon
        p.box((x0 + x1) / 2 - 0.3f, y1, 0.82f, (x0 + x1) / 2 + 0.3f, y1 + 0.2f, 0.88f, trim)
        p.pyramid((x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2 + 0.12f, (y1 - y0) / 2 + 0.12f, 1.6f, 0.75f, roof, 4)
        p.chimney(x0 + 0.4f, y0 + 0.4f, 1.9f, 2.5f)
    }

    private fun timber(p: Iso, w: Float, d: Float, wall: Int, roof: Int, beam: Int) {
        val m = 0.16f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d)
        p.box(x0, y0, 0.07f, x1, y1, 0.8f, wall)
        p.box(x0 - 0.05f, y0 - 0.05f, 0.8f, x1 + 0.05f, y1 + 0.05f, 1.55f, wall)
        // colombages
        var u = x0 + 0.1f
        while (u < x1) { p.line(u, y1 + 0.05f, 0.82f, u, y1 + 0.05f, 1.55f, beam, 1.8f); p.line(u, y1, 0.09f, u, y1, 0.8f, beam, 1.8f); u += 0.34f }
        var v = y0 + 0.1f
        while (v < y1) { p.line(x1 + 0.05f, v, 0.82f, x1 + 0.05f, v, 1.55f, beam, 1.8f); v += 0.34f }
        p.line(x0, y1, 0.8f, x1, y1, 0.8f, beam, 2.4f)
        p.line(x0 - 0.05f, y1 + 0.05f, 1.55f, x1 + 0.05f, y1 + 0.05f, 1.55f, beam, 2.4f)
        p.doorY(y1, (x0 + x1) / 2, 0.3f, 0.56f, DOOR)
        p.windowY(y1 + 0.05f, x0 + 0.3f, 1.2f, 0.2f, 0.26f)
        p.windowY(y1 + 0.05f, x1 - 0.3f, 1.2f, 0.2f, 0.26f)
        p.gable(x0 - 0.05f, y0 - 0.05f, x1 + 0.05f, y1 + 0.05f, 1.55f, 0.7f, roof, Iso.shade(wall, -0.3f), false, 0.14f, 3)
        p.chimney(x1 - 0.3f, y0 + 0.3f, 1.8f, 2.4f)
    }

    private fun mushroom(p: Iso, w: Float, d: Float, stem: Int, cap: Int, spots: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        p.cyl(cx, cy, 0f, 0.75f, 0.42f, stem)
        p.faceY(cy + 0.3f, cx - 0.13f, cx + 0.13f, 0.03f, 0.42f, 0x7A4A28)
        p.windowX(cx + 0.3f, cy, 0.5f, 0.18f, 0.2f)
        p.ball(cx, cy, 0.95f, 0.98f, cap, 0.55f)
        p.dot(cx - 0.35f, cy + 0.45f, 1.15f, 6f, spots)
        p.dot(cx + 0.3f, cy + 0.4f, 1.0f, 5f, spots)
        p.dot(cx, cy + 0.15f, 1.38f, 6f, spots)
        p.dot(cx + 0.55f, cy, 1.15f, 4f, spots)
        p.dot(cx - 0.55f, cy + 0.1f, 1.1f, 4f, spots)
    }

    private fun townhouse(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        val m = 0.2f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d)
        p.box(x0, y0, 0.07f, x1, y1, 2.2f, wall)
        for (f in 0 until 3) p.faceY(y1 + 0.004f, x0, x1, 0.07f + f * 0.72f, 0.1f + f * 0.72f, trim)
        p.doorY(y1, (x0 + x1) / 2, 0.3f, 0.56f, DOOR)
        for (f in 0 until 3) {
            val zc = 0.07f + f * 0.72f + 0.46f
            if (f > 0) { p.windowY(y1, x0 + 0.3f, zc, 0.2f, 0.3f); p.windowY(y1, x1 - 0.3f, zc, 0.2f, 0.3f) }
            else { p.windowY(y1, x0 + 0.25f, zc, 0.18f, 0.28f); p.windowY(y1, x1 - 0.25f, zc, 0.18f, 0.28f) }
            p.windowX(x1, (y0 + y1) / 2, zc, 0.22f, 0.3f)
        }
        p.gable(x0, y0, x1, y1, 2.2f, 0.9f, roof, p.shadeWall(wall), false, 0.12f, 4)
        p.chimney(x1 - 0.2f, y0 + 0.2f, 2.5f, 3.3f)
    }

    private fun Iso.shadeWall(wall: Int) = Iso.shade(wall, -0.3f)

    private fun burrow(p: Iso, w: Float, d: Float, grass: Int, door: Int, flower: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        p.ball(cx, cy, 0.38f, 1.0f, grass, 0.78f)
        p.ball(cx - 0.55f, cy - 0.35f, 0.3f, 0.45f, Iso.shade(grass, 0.05f), 0.32f)
        // porte ronde
        p.oval(cx + 0.1f, cy + 0.82f, 0.38f, 19f, 24f, 0x4A2E18)
        p.oval(cx + 0.1f, cy + 0.82f, 0.38f, 15f, 20f, door)
        p.dot(cx + 0.18f, cy + 0.84f, 0.36f, 2.2f, 0xF2D060)
        p.oval(cx - 0.55f, cy + 0.62f, 0.62f, 8f, 8f, 0xBFE3F2)
        p.chimney(cx + 0.55f, cy - 0.35f, 0.9f, 1.4f, 0x8A8478)
        p.smoke(cx + 0.55f, cy - 0.35f, 1.4f)
        for ((fx, fy) in listOf(0.2f to 0.9f, 0.5f to 1.1f, 1.3f to 1.0f, 1.55f to 0.8f)) p.dot(fx * w / 1.8f, fy * d / 1.8f + 0.1f, 0.1f, 3.4f, flower)
    }

    private fun treehouse(p: Iso, w: Float, d: Float, wood: Int, leaf: Int, trim: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        p.cyl(cx, cy, 0f, 1.3f, 0.3f, 0x7A5632)
        p.ball(cx - 0.25f, cy - 0.25f, 1.95f, 0.8f, Iso.shade(leaf, -0.1f), 0.55f)
        p.ball(cx + 0.3f, cy - 0.1f, 1.9f, 0.7f, leaf, 0.5f)
        p.box(cx - 0.8f, cy - 0.6f, 1.0f, cx + 0.8f, cy + 0.6f, 1.08f, 0x8A6A44)
        p.box(cx - 0.5f, cy - 0.35f, 1.08f, cx + 0.5f, cy + 0.35f, 1.7f, wood)
        p.doorY(cy + 0.35f, cx, 0.24f, 0.42f, 0x4A2E18)
        p.windowX(cx + 0.5f, cy, 1.45f, 0.2f, 0.2f)
        p.pyramid(cx, cy, 0.62f, 0.45f, 1.66f, 0.55f, trim, 3)
        p.ball(cx + 0.35f, cy + 0.55f, 2.0f, 0.6f, leaf, 0.45f)
        p.line(cx - 0.4f, cy + 0.6f, 1.0f, cx - 0.4f, cy + 0.85f, 0f, 0x6A4A2E, 2f)
        p.line(cx - 0.55f, cy + 0.6f, 1.0f, cx - 0.55f, cy + 0.85f, 0f, 0x6A4A2E, 2f)
    }

    private fun manor(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int, grand: Boolean) {
        val m = 0.18f
        val x0 = m; val y0 = m + 0.1f; val x1 = w - m; val y1 = d - 0.55f
        base(p, w, d)
        val fl = if (grand) 3 else 2
        val wh = 0.78f
        val top = 0.07f + fl * wh
        p.box(x0, y0, 0.07f, x1, y1, top, wall)
        windows(p, x0, y0, x1, y1, 0.07f, fl, wh, -1f)
        p.faceY(y1 + 0.004f, x0, x1, top - 0.07f, top, trim)
        p.gable(x0, y0, x1, y1, top, 0.75f, roof, Iso.shade(wall, -0.3f), true, 0.12f, 4)
        // avant-corps central
        val cx = w / 2
        val px0 = cx - 0.55f; val px1 = cx + 0.55f; val py1 = d - 0.14f
        p.box(px0, y1 - 0.1f, 0.07f, px1, py1, top - 0.1f, wall)
        p.doorY(py1, cx, 0.34f, 0.62f, DOOR)
        p.windowY(py1, cx - 0.33f, 0.5f, 0.16f, 0.26f)
        p.windowY(py1, cx + 0.33f, 0.5f, 0.16f, 0.26f)
        for (f in 1 until fl) p.windowY(py1, cx, 0.07f + f * wh + 0.4f, 0.22f, 0.3f)
        p.gable(px0, y1 - 0.1f, px1, py1, top - 0.1f, 0.62f, roof, Iso.shade(wall, -0.3f), false, 0.1f, 3)
        if (grand) {
            for (i in 0..3) p.box(px0 + 0.06f + i * 0.33f, py1 + 0.01f, 0.07f, px0 + 0.14f + i * 0.33f, py1 + 0.1f, 0.85f, 0xF4F0E4)
            p.box(cx - 0.62f, py1 + 0.0f, 0.85f, cx + 0.62f, py1 + 0.14f, 0.92f, trim)
        }
        p.chimney(x0 + 0.35f, y0 + 0.3f, top + 0.2f, top + 0.95f)
        p.chimney(x1 - 0.4f, y0 + 0.3f, top + 0.2f, top + 0.95f)
    }

    private fun guild(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        base(p, w, d)
        val x0 = 0.18f; val x1 = w - 0.18f; val y0 = 0.2f; val y1 = d - 0.2f
        p.box(x0, y0, 0.07f, x1, y1, 1.55f, wall)
        windows(p, x0, y0, x1, y1, 0.07f, 2, 0.74f, w / 2)
        p.doorY(y1, w / 2, 0.4f, 0.7f, DOOR)
        p.faceY(y1 + 0.004f, w / 2 - 0.3f, w / 2 + 0.3f, 0.78f, 0.84f, trim)
        // deux pignons à degrés qui se font face
        p.gable(x0, y0, w / 2, y1, 1.55f, 0.85f, roof, Iso.shade(wall, -0.3f), false, 0.1f, 3)
        p.gable(w / 2, y0, x1, y1, 1.55f, 0.85f, roof, Iso.shade(wall, -0.3f), false, 0.1f, 3)
        // bannières
        for (u in listOf(x0 + 0.25f, x1 - 0.25f)) {
            p.faceY(y1 + 0.01f, u - 0.08f, u + 0.08f, 0.8f, 1.35f, trim)
        }
        p.chimney(w / 2, y0 + 0.4f, 2.1f, 2.7f)
    }

    private fun watch(p: Iso, w: Float, d: Float, stone: Int, roof: Int, trim: Int) {
        base(p, w, d)
        val cx = w / 2; val cy = d / 2
        p.box(cx - 0.4f, cy - 0.4f, 0.07f, cx + 0.4f, cy + 0.4f, 2.1f, stone)
        p.doorY(cy + 0.4f, cx, 0.26f, 0.5f, 0x4A2E18)
        p.windowY(cy + 0.4f, cx, 1.0f, 0.14f, 0.3f, 0x2A3A4A, trim)
        p.windowY(cy + 0.4f, cx, 1.6f, 0.14f, 0.3f, 0x2A3A4A, trim)
        p.box(cx - 0.5f, cy - 0.5f, 2.1f, cx + 0.5f, cy + 0.5f, 2.22f, Iso.shade(stone, 0.08f))
        for (i in 0..2) for (j in 0..2) if ((i + j) % 2 == 0) p.box(cx - 0.5f + i * 0.36f, cy - 0.5f + j * 0.36f, 2.22f, cx - 0.5f + i * 0.36f + 0.28f, cy - 0.5f + j * 0.36f + 0.28f, 2.42f, stone)
        p.line(cx, cy, 2.4f, cx, cy, 3.0f, 0x6A4A2E, 2f)
        p.face(roof, cx, cy, 3.0f, cx + 0.4f, cy, 2.85f, cx, cy, 2.7f)
        // petite annexe
        p.box(w - 0.55f, d - 0.7f, 0.07f, w - 0.1f, d - 0.15f, 0.6f, Iso.shade(stone, 0.1f))
        p.pyramid(w - 0.325f, d - 0.425f, 0.32f, 0.32f, 0.56f, 0.4f, roof, 2)
    }

    private fun roundTower(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2 - 0.1f; val cy = d / 2 - 0.1f
        p.cyl(cx, cy, 0f, 1.9f, 0.55f, wall)
        p.faceY(cy + 0.5f, cx - 0.13f, cx + 0.13f, 0.03f, 0.5f, 0x4A2E18)
        for (z in listOf(0.9f, 1.4f)) p.oval(cx + 0.28f, cy + 0.4f, z, 4f, 7f, 0x2A3A4A)
        p.cyl(cx, cy, 1.9f, 2.0f, 0.64f, trim)
        p.cone(cx, cy, 2.0f, 1.0f, 0.72f, roof)
        p.line(cx, cy, 3.0f, cx, cy, 3.4f, 0x5A4A32, 1.6f)
        p.face(0xC8392E, cx, cy, 3.4f, cx + 0.3f, cy, 3.28f, cx, cy, 3.16f)
        // maison collée à la tour
        p.box(w - 0.9f, d - 0.9f, 0.07f, w - 0.15f, d - 0.15f, 0.7f, Iso.shade(wall, 0.1f))
        p.doorY(d - 0.15f, w - 0.52f, 0.24f, 0.44f, DOOR)
        p.gable(w - 0.9f, d - 0.9f, w - 0.15f, d - 0.15f, 0.7f, 0.4f, roof, Iso.shade(wall, -0.3f), true, 0.08f, 2)
    }

    private fun chalet(p: Iso, w: Float, d: Float, wood: Int, roof: Int, trim: Int) {
        val m = 0.16f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d, color = 0x8A8478)
        p.box(x0, y0, 0.07f, x1, y1, 0.75f, 0xF0E6CC)
        p.box(x0 - 0.02f, y0, 0.75f, x1 + 0.02f, y1 + 0.05f, 1.5f, wood)
        var u = x0 + 0.1f
        while (u < x1) { p.line(u, y1 + 0.05f, 0.78f, u, y1 + 0.05f, 1.5f, Iso.shade(wood, -0.35f), 1.4f); u += 0.16f }
        p.doorY(y1, w * 0.5f, 0.3f, 0.56f, DOOR)
        p.windowY(y1, x0 + 0.35f, 0.42f, 0.22f, 0.26f)
        p.windowY(y1, x1 - 0.35f, 0.42f, 0.22f, 0.26f)
        // balcon en bois
        p.box(x0 + 0.15f, y1 + 0.02f, 0.72f, x1 - 0.15f, y1 + 0.28f, 0.78f, trim)
        for (i in 0..6) p.box(x0 + 0.17f + i * ((x1 - x0 - 0.36f) / 6f), y1 + 0.24f, 0.78f, x0 + 0.2f + i * ((x1 - x0 - 0.36f) / 6f), y1 + 0.27f, 1.05f, trim)
        p.windowY(y1 + 0.05f, w * 0.5f, 1.1f, 0.24f, 0.3f)
        p.gable(x0, y0, x1, y1, 1.5f, 0.55f, roof, wood, true, 0.3f, 3)
        p.face(0xFFFFFF, x0 - 0.3f, y1 + 0.3f, 1.47f, x1 + 0.3f, y1 + 0.3f, 1.47f, x1 + 0.3f, y1 + 0.22f, 1.53f, x0 - 0.3f, y1 + 0.22f, 1.53f)
        p.chimney(x1 - 0.5f, y0 + 0.3f, 1.7f, 2.3f, 0x8A8478)
        p.smoke(x1 - 0.5f, y0 + 0.3f, 2.3f)
    }

    /** Château, palais et citadelle : un corps, des tours d'angle, un donjon central. */
    fun fortress(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int, domes: Boolean, towers: Int) {
        base(p, w, d, 0.05f, 0xA8A298, 0.08f)
        val x0 = 0.45f; val y0 = 0.45f; val x1 = w - 0.45f; val y1 = d - 0.45f
        val bodyH = if (towers > 4) 1.3f else 1.1f
        p.box(x0, y0, 0.08f, x1, y1, bodyH, wall)
        val cx = w / 2
        p.doorY(y1, cx, 0.4f, 0.7f, if (domes) trim else DOOR)
        windows(p, x0, y0, x1, y1, 0.08f, 1, bodyH - 0.1f, cx)
        // créneaux
        var u = x0
        while (u < x1 - 0.1f) { p.box(u, y1 - 0.08f, bodyH, u + 0.16f, y1, bodyH + 0.14f, wall); u += 0.32f }
        // donjon
        val kh = bodyH + 0.9f
        p.box(cx - 0.55f, d / 2 - 0.55f, bodyH, cx + 0.55f, d / 2 + 0.55f, kh, wall)
        p.windowY(d / 2 + 0.55f, cx, bodyH + 0.45f, 0.2f, 0.3f)
        if (domes) p.ball(cx, d / 2, kh + 0.05f, 0.62f, trim, 0.7f) else p.pyramid(cx, d / 2, 0.66f, 0.66f, kh - 0.04f, 0.8f, roof, 4)
        p.line(cx, d / 2, kh + 0.6f, cx, d / 2, kh + 1.3f, 0x5A4A32, 1.6f)
        p.face(trim, cx, d / 2, kh + 1.3f, cx + 0.3f, d / 2, kh + 1.18f, cx, d / 2, kh + 1.06f)
        val pts = ArrayList<Pair<Float, Float>>()
        pts.add(x0 to y0); pts.add(x1 to y0); pts.add(x0 to y1); pts.add(x1 to y1)
        if (towers > 4) { pts.add(cx to y0); pts.add(cx to y1); pts.add(x0 to d / 2); pts.add(x1 to d / 2) }
        pts.sortBy { it.first + it.second }
        for ((tx, ty) in pts) {
            val th = if (towers > 4) 1.9f else 1.7f
            p.cyl(tx, ty, 0.08f, th, 0.3f, wall)
            p.oval(tx + 0.1f, ty + 0.25f, th * 0.55f, 3f, 6f, 0x2A3A4A)
            if (domes) p.ball(tx, ty, th + 0.12f, 0.36f, trim, 0.42f) else p.cone(tx, ty, th, 0.65f, 0.38f, roof)
        }
    }

    // ------------------------------------------------------------------ ateliers

    private fun lumber(p: Iso, w: Float, d: Float, wall: Int, roof: Int, accent: Int, glyph: String) {
        gableHouse(p, w - 0.5f, d, wall, roof, DOOR, floors = 1, wallH = 0.8f, chimney = true, lines = 3, door = false)
        p.doorY(d - 0.14f, (w - 0.5f) / 2, 0.4f, 0.5f, DOOR)
        // piles de rondins
        for (k in 0..2) for (j in 0..(2 - k)) p.ball(w - 0.35f - j * 0.2f + k * 0.1f, d - 0.5f, 0.12f + k * 0.17f, 0.12f, 0xC8975A, 0.12f)
        // scie ronde
        val sx = p.px(w - 0.2f, 0.45f); val sy = p.py(w - 0.2f, 0.45f, 0.5f)
        HamletGlyphs.draw(p.c, glyph, sx, sy - 6f, 18f, accent)
    }

    private fun hall(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int, glyph: String) {
        val m = 0.14f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - 0.4f
        base(p, w, d)
        val top = 1.0f
        p.box(x0, y0, 0.07f, x1, y1, top, wall)
        p.gable(x0, y0, x1, y1, top, 0.6f, roof, Iso.shade(wall, -0.3f), true, 0.1f, 3)
        windows(p, x0, y0, x1, y1, 0.07f, 1, 0.8f, -1f)
        // porche à colonnes
        val n = max(3, (w * 1.6f).toInt())
        p.box(x0 + 0.05f, y1, 0.07f, x1 - 0.05f, d - 0.12f, 0.12f, 0xD8D2C4)
        p.box(x0 + 0.05f, y1, 0.98f, x1 - 0.05f, d - 0.12f, 1.06f, trim)
        for (i in 0 until n) {
            val u = x0 + 0.15f + (x1 - x0 - 0.3f) * i / (n - 1f)
            p.cyl(u, d - 0.2f, 0.12f, 0.98f, 0.06f, 0xF0ECE0)
        }
        p.doorY(y1, (x0 + x1) / 2, 0.4f, 0.62f, DOOR)
        val cx = p.px((x0 + x1) / 2, y1 + 0.05f); val cy = p.py((x0 + x1) / 2, y1 + 0.05f, 1.45f)
        HamletGlyphs.draw(p.c, glyph, cx, cy, 22f, trim)
    }

    private fun smith(p: Iso, w: Float, d: Float, wall: Int, roof: Int, glow: Int, glyph: String) {
        val m = 0.14f
        gableHouse(p, w, d, wall, roof, 0x3A3A42, floors = 1, wallH = 0.8f, chimney = false, lines = 2, door = false)
        val x1 = w - m; val y1 = d - m
        p.faceY(y1 + 0.004f, w * 0.18f, w * 0.52f, 0.07f, 0.62f, 0x2A2A2E)
        p.faceY(y1 + 0.006f, w * 0.22f, w * 0.48f, 0.1f, 0.5f, glow)
        p.doorY(y1, w * 0.76f, 0.26f, 0.5f, DOOR)
        p.chimney(x1 - 0.4f, m + 0.3f, 0.9f, 1.8f, 0x6A665C)
        p.smoke(x1 - 0.4f, m + 0.3f, 1.8f)
        // enclume
        p.box(w * 0.3f, d - 0.02f, 0f, w * 0.3f + 0.22f, d + 0.16f, 0.14f, 0x4A4A52)
        val cx = p.px(w * 0.75f, y1 + 0.03f); val cy = p.py(w * 0.75f, y1 + 0.03f, 0.95f)
        HamletGlyphs.draw(p.c, glyph, cx, cy, 20f, glow)
    }

    private fun glass(p: Iso, w: Float, d: Float, tint: Int, frame: Int, bloom: Int) {
        val m = 0.14f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d, color = 0xB8B0A0)
        // plantes à l'intérieur
        for (i in 0..5) {
            val fx = x0 + 0.2f + (x1 - x0 - 0.4f) * ((i * 37) % 10) / 10f
            val fy = y0 + 0.2f + (y1 - y0 - 0.4f) * ((i * 53) % 10) / 10f
            p.ball(fx, fy, 0.35f, 0.17f, 0x4F9D37, 0.3f)
            p.dot(fx, fy, 0.62f, 3.5f, bloom)
        }
        val g = Iso.withAlpha(tint, 0.55f)
        p.faceY(y1, x0, x1, 0.07f, 0.7f, g)
        p.faceX(x1, y0, y1, 0.07f, 0.7f, Iso.withAlpha(Iso.shade(tint, -0.2f), 0.55f))
        p.gable(x0, y0, x1, y1, 0.7f, 0.55f, Iso.withAlpha(Iso.shade(tint, 0.1f), 0.55f), Iso.withAlpha(tint, 0.5f), true, 0.04f, 0)
        val n = max(3, ((x1 - x0) / 0.4f).toInt())
        for (i in 0..n) { val u = x0 + (x1 - x0) * i / n; p.line(u, y1, 0.07f, u, y1, 0.7f, frame, 1.6f) }
        p.line(x0, y1, 0.7f, x1, y1, 0.7f, frame, 2.2f)
        p.line(x1, y0, 0.7f, x1, y1, 0.7f, frame, 2.2f)
        p.line(x0, (y0 + y1) / 2, 1.25f, x1, (y0 + y1) / 2, 1.25f, frame, 2f)
        p.line(x0, y1, 0.7f, x0, (y0 + y1) / 2, 1.25f, frame, 1.6f)
        p.line(x1, y1, 0.7f, x1, (y0 + y1) / 2, 1.25f, frame, 1.6f)
    }

    private fun stable(p: Iso, w: Float, d: Float, wall: Int, roof: Int, hay: Int) {
        gableHouse(p, w, d, wall, roof, DOOR, floors = 1, wallH = 0.8f, chimney = false, lines = 3, door = false)
        val y1 = d - 0.14f
        for (u in listOf(w * 0.2f, w * 0.5f, w * 0.8f)) {
            p.faceY(y1 + 0.004f, u - 0.22f, u + 0.22f, 0.07f, 0.65f, 0x2A1E12)
            p.faceY(y1 + 0.006f, u - 0.22f, u + 0.22f, 0.07f, 0.34f, DOOR)
        }
        p.ball(w - 0.3f, d + 0.0f, 0.12f, 0.2f, hay, 0.16f)
        p.ball(w - 0.55f, d + 0.0f, 0.1f, 0.16f, hay, 0.13f)
    }

    private fun barn(p: Iso, w: Float, d: Float, wall: Int, roof: Int, trim: Int) {
        val m = 0.14f
        val x0 = m; val y0 = m; val x1 = w - m; val y1 = d - m
        base(p, w, d)
        p.box(x0, y0, 0.07f, x1, y1, 1.0f, wall)
        p.gable(x0, y0, x1, y1, 1.0f, 0.85f, roof, p.shadeWall(wall), false, 0.1f, 3)
        val cx = (x0 + x1) / 2
        p.faceY(y1 + 0.004f, cx - 0.4f, cx + 0.4f, 0.07f, 0.8f, trim)
        p.line(cx - 0.4f, y1 + 0.01f, 0.07f, cx + 0.4f, y1 + 0.01f, 0.8f, wall, 2f)
        p.line(cx + 0.4f, y1 + 0.01f, 0.07f, cx - 0.4f, y1 + 0.01f, 0.8f, wall, 2f)
        p.line(cx, y1 + 0.01f, 0.07f, cx, y1 + 0.01f, 0.8f, wall, 2f)
        p.windowY(y1, cx, 1.25f, 0.2f, 0.2f, 0x2A1E12, trim)
        // silo
        p.cyl(x1 - 0.1f, y0 + 0.15f, 0f, 1.7f, 0.22f, 0xB8B8B0)
        p.cone(x1 - 0.1f, y0 + 0.15f, 1.7f, 0.3f, 0.24f, 0x7A8AA0)
    }

    private fun tower(p: Iso, w: Float, d: Float, wall: Int, roof: Int, acc: Int, kind: Int, glyph: String) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        when (kind) {
            1 -> { // château d'eau
                for ((fx, fy) in listOf(0.2f to 0.2f, 0.8f to 0.2f, 0.2f to 0.8f, 0.8f to 0.8f)) p.box(fx * w - 0.04f, fy * d - 0.04f, 0f, fx * w + 0.04f, fy * d + 0.04f, 1.2f, 0x6A4A2E)
                p.cyl(cx, cy, 1.2f, 1.75f, 0.36f, wall)
                p.cone(cx, cy, 1.75f, 0.5f, 0.4f, roof)
                p.line(cx - 0.3f, cy + 0.3f, 1.2f, cx + 0.3f, cy + 0.3f, 1.75f, 0x6A4A2E, 1.4f)
                p.oval(cx, cy + 0.3f, 1.45f, 5f, 5f, acc)
            }
            2 -> { // phare
                p.cyl(cx, cy, 0f, 0.8f, 0.46f, 0xF4F0E4)
                p.cyl(cx, cy, 0.8f, 1.5f, 0.4f, roof)
                p.cyl(cx, cy, 1.5f, 2.2f, 0.34f, 0xF4F0E4)
                p.cyl(cx, cy, 2.2f, 2.5f, 0.38f, 0x4A4A52)
                p.ball(cx, cy, 2.75f, 0.26f, acc, 0.3f)
                p.cone(cx, cy, 2.95f, 0.45f, 0.34f, roof)
                p.faceY(cy + 0.44f, cx - 0.1f, cx + 0.1f, 0.03f, 0.4f, 0x5A3A22)
            }
            3 -> { // tour de l'horloge
                p.box(cx - 0.5f, cy - 0.5f, 0f, cx + 0.5f, cy + 0.5f, 2.3f, wall)
                p.doorY(cy + 0.5f, cx, 0.3f, 0.5f, 0x4A2E18)
                val fx = p.px(cx, cy + 0.5f) ; val fy = p.py(cx, cy + 0.5f, 1.6f)
                p.flat(0xF8F4E4, fx - 17f, fy - 12f, fx + 17f, fy - 12f, fx + 17f, fy + 12f, fx - 17f, fy + 12f)
                HamletGlyphs.draw(p.c, "clock", fx, fy, 24f, 0x2A2A32)
                p.pyramid(cx, cy, 0.62f, 0.62f, 2.28f, 1.0f, roof, 5)
                p.ball(cx, cy, 3.35f, 0.1f, acc, 0.1f)
            }
            4 -> { // tour du mage
                p.cyl(cx, cy, 0f, 1.6f, 0.45f, wall)
                p.cyl(cx, cy, 1.6f, 1.72f, 0.56f, Iso.shade(wall, -0.2f))
                p.cone(cx, cy, 1.72f, 1.5f, 0.6f, roof)
                p.faceY(cy + 0.42f, cx - 0.12f, cx + 0.12f, 0.03f, 0.46f, 0x3A2A5A)
                p.oval(cx + 0.2f, cy + 0.36f, 1.0f, 4f, 8f, acc)
                p.oval(cx - 0.2f, cy + 0.38f, 1.2f, 4f, 7f, acc)
                p.dot(cx + 0.55f, cy + 0.2f, 1.9f, 3f, acc); p.dot(cx - 0.3f, cy - 0.1f, 2.5f, 2.5f, acc); p.dot(cx + 0.2f, cy + 0.4f, 2.9f, 2f, 0xFFFFFF)
            }
            5 -> { // observatoire
                p.cyl(cx, cy, 0f, 1.3f, 0.52f, wall)
                p.faceY(cy + 0.48f, cx - 0.12f, cx + 0.12f, 0.03f, 0.42f, 0x3A3A5A)
                p.ball(cx, cy, 1.3f, 0.55f, roof, 0.6f)
                p.face(0x2A2A4A, cx + 0.05f, cy + 0.4f, 1.55f, cx + 0.2f, cy + 0.45f, 1.55f, cx + 0.2f, cy + 0.45f, 1.95f, cx + 0.05f, cy + 0.4f, 1.95f)
                p.line(cx + 0.1f, cy + 0.2f, 1.7f, cx + 0.5f, cy + 0.6f, 2.1f, 0xC8D0D8, 5f)
                p.dot(cx - 0.3f, cy + 0.1f, 1.7f, 2.5f, acc)
            }
            7 -> { // tour des cartes, posée sur un véritable socle
                p.cyl(cx, cy, 0f, 0.14f, 0.7f, 0xA6A397)
                p.cyl(cx, cy, 0.14f, 2.3f, 0.53f, wall)
                p.faceY(cy + 0.5f, cx - 0.12f, cx + 0.12f, 0.14f, 0.64f, 0x65472E)
                p.cyl(cx, cy, 2.18f, 2.3f, 0.6f, acc)
                p.cone(cx, cy, 2.3f, 0.9f, 0.66f, roof)
                val fx = p.px(cx, cy + 0.52f); val fy = p.py(cx, cy + 0.52f, 1.5f)
                HamletGlyphs.draw(p.c, "map", fx, fy, 21f, acc)
                p.windowX(cx + 0.48f, cy - 0.05f, 1.05f, 0.18f, 0.35f, 0x2D4A59, acc)
            }
            else -> { // port aérien : socle et ballon
                p.cyl(cx, cy, 0f, 0.2f, 0.62f, 0x9A9488)
                p.ball(cx, cy, 1.9f, 0.62f, roof, 0.78f)
                for (a in listOf(-0.3f, 0f, 0.3f)) p.line(cx + a, cy, 1.9f - 0.65f, cx + a, cy, 1.12f, Iso.shade(acc, -0.1f), 4f)
                p.oval(cx, cy, 1.9f, 5f, 40f, Iso.withAlpha(acc, 0.9f))
                p.box(cx - 0.17f, cy - 0.17f, 0.9f, cx + 0.17f, cy + 0.17f, 1.12f, 0x8A6A44)
                p.line(cx - 0.17f, cy, 1.12f, cx - 0.4f, cy, 1.5f, 0x5A4A32, 1.2f)
                p.line(cx + 0.17f, cy, 1.12f, cx + 0.4f, cy, 1.5f, 0x5A4A32, 1.2f)
            }
        }
    }

    private fun mill(p: Iso, w: Float, d: Float, wall: Int, roof: Int, acc: Int, kind: Int, staticParts: Boolean) {
        if (kind == 1) { // moulin à eau
            gableHouse(p, w - 0.3f, d, wall, roof, DOOR, floors = 1, wallH = 0.9f, chimney = true, lines = 3)
            val rx = w - 0.1f
            p.cyl(rx, d * 0.5f, 0.1f, 0.2f, 0.5f, 0x6A4A2E)
            p.disc(rx, d * 0.5f, 0.0f, 0.55f, Iso.withAlpha(acc, 0.7f))
            p.line(rx, d * 0.5f - 0.5f, 0.4f, rx, d * 0.5f + 0.5f, 0.4f, 0x6A4A2E, 2.4f)
            return
        }
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        p.cone(cx, cy, 0f, 1.7f, 0.62f, wall)
        p.cyl(cx, cy, 1.45f, 1.55f, 0.4f, 0x6A4A2E)
        p.cone(cx, cy, 1.55f, 0.5f, 0.44f, roof)
        p.faceY(cy + 0.5f, cx - 0.13f, cx + 0.13f, 0.03f, 0.46f, 0x5A3A22)
        p.oval(cx + 0.18f, cy + 0.34f, 0.85f, 4f, 6f, 0x2A3A4A)
        if (staticParts) millWings(p, w, d, 0.35f)
    }

    fun millWings(p: Iso, w: Float, d: Float, ang: Float) {
        val hx = w / 2 + 0.12f; val hy = d / 2 + 0.5f; val hz = 1.35f
        for (k in 0..3) {
            val a = ang + k * 1.5708f
            val dx = kotlin.math.cos(a); val dz = kotlin.math.sin(a)
            val len = 1.1f
            val ex = hx + dx * len * 0.55f; val ez = hz + dz * len
            p.line(hx, hy, hz, ex, hy + 0.04f, ez, 0x6A4A2E, 3.2f)
            val ox = -dz * 0.18f; val oz = dx * 0.18f
            p.face(0xF0E8D0, hx + dx * len * 0.25f * 0.55f, hy + 0.02f, hz + dz * len * 0.25f, ex, hy + 0.04f, ez,
                ex + ox * 0.55f, hy + 0.04f, ez + oz, hx + dx * len * 0.25f * 0.55f + ox * 0.55f, hy + 0.02f, hz + dz * len * 0.25f + oz)
        }
        p.ball(hx, hy, hz, 0.08f, 0x4A3A2A, 0.08f)
    }

    private fun chapel(p: Iso, w: Float, d: Float, wall: Int, roof: Int, gold: Int) {
        val m = 0.16f
        base(p, w, d)
        val x0 = m; val y0 = m + 0.3f; val x1 = w - m; val y1 = d - m
        p.box(x0, y0, 0.07f, x1, y1, 0.85f, wall)
        p.gable(x0, y0, x1, y1, 0.85f, 0.55f, roof, Iso.shade(wall, -0.3f), true, 0.1f, 3)
        p.doorY(y1, w * 0.5f, 0.34f, 0.5f, DOOR)
        windows(p, x0, y0, x1, y1, 0.07f, 1, 0.8f, w * 0.5f)
        // clocher
        val tx = x1 - 0.25f; val ty = y0 - 0.05f
        p.box(tx - 0.24f, ty - 0.24f, 0.07f, tx + 0.24f, ty + 0.24f, 1.95f, wall)
        p.windowY(ty + 0.24f, tx, 1.5f, 0.14f, 0.3f, 0x2A2A3A, gold)
        p.pyramid(tx, ty, 0.3f, 0.3f, 1.93f, 0.95f, roof, 3)
        p.line(tx, ty, 2.85f, tx, ty, 3.2f, gold, 2.2f)
        p.line(tx - 0.08f, ty, 3.07f, tx + 0.08f, ty, 3.07f, gold, 2.2f)
        // rosace
        p.dot(w * 0.5f, y1 + 0.01f, 1.0f, 6f, gold)
        p.dot(w * 0.5f, y1 + 0.01f, 1.0f, 3.5f, 0x4A7AD8)
    }

    private fun vineyard(p: Iso, w: Float, d: Float, wall: Int, roof: Int, grape: Int) {
        p.shadow(0f, 0f, w, d)
        // rangs de vigne
        for (r in 0..3) {
            val y = 0.3f + r * 0.28f
            p.line(0.2f, y, 0.1f, w - 0.5f, y, 0.1f, 0x6A5A3A, 2f)
            var u = 0.3f
            while (u < w - 0.5f) { p.ball(u, y, 0.22f, 0.12f, 0x4F9D37, 0.2f); p.dot(u + 0.04f, y + 0.08f, 0.14f, 3f, grape); u += 0.3f }
        }
        // chai
        val x0 = w - 1.0f; val y0 = d - 0.95f; val x1 = w - 0.1f; val y1 = d - 0.1f
        p.box(x0, y0, 0.0f, x1, y1, 0.8f, wall)
        p.gable(x0, y0, x1, y1, 0.8f, 0.5f, roof, Iso.shade(wall, -0.3f), true, 0.1f, 2)
        p.doorY(y1, (x0 + x1) / 2, 0.3f, 0.5f, DOOR)
        // tonneau
        p.cyl(x0 - 0.2f, y1 - 0.2f, 0f, 0.3f, 0.17f, 0x9A6A3A)
    }

    private fun pagoda(p: Iso, w: Float, d: Float, wall: Int, roof: Int, acc: Int) {
        base(p, w, d, 0.16f)
        val cx = w / 2; val cy = d / 2
        var z = 0.07f
        var hx = 0.55f
        for (tier in 0..2) {
            p.box(cx - hx * 0.75f, cy - hx * 0.75f, z, cx + hx * 0.75f, cy + hx * 0.75f, z + 0.5f, wall)
            p.windowY(cy + hx * 0.75f, cx, z + 0.28f, 0.16f, 0.2f, 0x2A3A4A, acc)
            p.pyramid(cx, cy, hx + 0.22f, hx + 0.22f, z + 0.46f, 0.34f, roof, 1)
            z += 0.72f
            hx -= 0.1f
        }
        p.line(cx, cy, z - 0.2f, cx, cy, z + 0.3f, acc, 2f)
        p.doorY(cy + 0.41f, cx, 0.26f, 0.4f, acc)
    }

    private fun theater(p: Iso, w: Float, d: Float, wall: Int, roof: Int, gold: Int, kind: Int) {
        base(p, w, d, 0.1f)
        val x0 = 0.2f; val y0 = 0.2f; val x1 = w - 0.2f; val y1 = d - 0.55f
        p.box(x0, y0, 0.07f, x1, y1, 1.2f, wall)
        windows(p, x0, y0, x1, y1, 0.07f, 1, 1.0f, -1f)
        if (kind == 1) {
            p.cyl(w / 2, (y0 + y1) / 2, 1.2f, 1.45f, 0.9f, wall)
            p.ball(w / 2, (y0 + y1) / 2, 1.45f, 0.9f, roof, 0.75f)
            p.ball(w / 2, (y0 + y1) / 2, 2.1f, 0.12f, gold, 0.12f)
        } else {
            p.gable(x0, y0, x1, y1, 1.2f, 0.7f, roof, Iso.shade(wall, -0.3f), true, 0.12f, 3)
        }
        // façade à colonnes
        p.box(x0 + 0.05f, y1, 0.07f, x1 - 0.05f, d - 0.1f, 0.14f, 0xD8D2C4)
        val n = 6
        for (i in 0 until n) p.cyl(x0 + 0.2f + (x1 - x0 - 0.4f) * i / (n - 1f), d - 0.2f, 0.14f, 1.15f, 0.07f, 0xF4F0E4)
        p.box(x0 + 0.05f, y1, 1.15f, x1 - 0.05f, d - 0.1f, 1.25f, gold)
        p.doorY(y1, w / 2, 0.5f, 0.7f, 0x6A2A3A)
        // rideaux et masque
        val fx = p.px(w / 2, y1 + 0.05f); val fy = p.py(w / 2, y1 + 0.05f, 1.55f)
        HamletGlyphs.draw(p.c, "mask", fx, fy, 24f, gold)
    }

    private fun circus(p: Iso, w: Float, d: Float, red: Int, white: Int, gold: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        p.cyl(cx, cy, 0f, 0.8f, 1.15f, red)
        // bandes blanches sur le chapiteau
        p.cone(cx, cy, 0.8f, 1.5f, 1.25f, red)
        val sx = p.px(cx, cy)
        val yb = p.py(cx, cy, 0.8f); val ya = p.py(cx, cy, 2.3f)
        val rx = 1.25f * Iso.HW * 1.4142f
        for (k in 0..2) {
            val a0 = -rx + (k * 2 + 0.5f) * rx / 3.2f
            val a1 = a0 + rx / 3.2f
            p.flat(white, sx, ya, sx + a0, yb + 4f, sx + a1, yb + 4f)
        }
        p.line(cx, cy, 2.3f, cx, cy, 2.75f, 0x5A4A32, 1.8f)
        p.face(gold, cx, cy, 2.75f, cx + 0.4f, cy, 2.6f, cx, cy, 2.45f)
        p.faceY(cy + 1.13f, cx - 0.22f, cx + 0.22f, 0.02f, 0.62f, 0x2A1E22)
        p.faceY(cy + 1.14f, cx - 0.22f, cx - 0.02f, 0.02f, 0.62f, gold)
    }

    private fun arena(p: Iso, w: Float, d: Float, sand: Int, red: Int, blue: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        p.cyl(cx, cy, 0f, 0.5f, 1.4f, 0xC8C0AC)
        p.disc(cx, cy, 0.5f, 1.22f, sand)
        p.line(cx - 0.7f, cy - 0.1f, 0.52f, cx + 0.7f, cy - 0.1f, 0.52f, 0x8A6A44, 3f)
        // gradins avant : tribunes rayées
        for (k in 0..4) {
            val u = cx - 0.9f + k * 0.45f
            p.box(u, cy + 0.9f, 0.5f, u + 0.4f, cy + 1.15f, 0.7f, if (k % 2 == 0) red else blue)
        }
        for ((fx, fy) in listOf(cx - 1.15f to cy + 0.2f, cx + 1.15f to cy - 0.3f)) {
            p.line(fx, fy, 0.5f, fx, fy, 1.5f, 0x6A4A2E, 2f)
            p.face(red, fx, fy, 1.5f, fx + 0.3f, fy, 1.4f, fx, fy, 1.28f)
        }
    }

    private fun mine(p: Iso, w: Float, d: Float, rock: Int, wood: Int, gold: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2 - 0.1f
        p.ball(cx, cy, 0.5f, 1.35f, rock, 0.95f)
        p.ball(cx - 0.8f, cy - 0.3f, 0.35f, 0.55f, Iso.shade(rock, 0.06f), 0.42f)
        val ex = cx; val ey = cy + 1.15f
        p.oval(ex, ey, 0.5f, 26f, 30f, 0x1A1410)
        p.line(ex - 0.42f, ey, 0.05f, ex - 0.42f, ey, 0.85f, wood, 4f)
        p.line(ex + 0.42f, ey, 0.05f, ex + 0.42f, ey, 0.85f, wood, 4f)
        p.line(ex - 0.5f, ey, 0.85f, ex + 0.5f, ey, 0.85f, wood, 4.5f)
        // rails et wagonnet
        p.line(ex - 0.15f, ey, 0f, ex - 0.15f, ey + 0.8f, 0f, 0x5A5A62, 1.4f)
        p.line(ex + 0.15f, ey, 0f, ex + 0.15f, ey + 0.8f, 0f, 0x5A5A62, 1.4f)
        p.box(ex - 0.2f, ey + 0.55f, 0.04f, ex + 0.2f, ey + 0.85f, 0.32f, 0x6A5A4A)
        p.ball(ex, ey + 0.7f, 0.36f, 0.2f, gold, 0.12f)
        for ((gx, gy) in listOf(cx - 1.0f to cy + 0.9f, cx - 0.85f to cy + 1.05f)) p.ball(gx, gy, 0.08f, 0.1f, gold, 0.08f)
    }

    private fun dock(p: Iso, w: Float, d: Float, wood: Int, hull: Int, sail: Int) {
        p.shadow(0f, 0f, w, d)
        p.box(0.1f, 0.1f, 0f, w - 0.1f, d - 0.1f, 0.14f, wood)
        for (i in 0..5) p.line(0.1f, 0.1f + i * (d - 0.2f) / 5f, 0.15f, w - 0.1f, 0.1f + i * (d - 0.2f) / 5f, 0.15f, Iso.shade(wood, -0.4f), 1f)
        // coque en chantier
        p.box(0.5f, 0.7f, 0.14f, w - 0.5f, d - 0.7f, 0.62f, hull)
        p.box(0.4f, 0.8f, 0.5f, w - 0.4f, d - 0.8f, 0.7f, Iso.shade(hull, 0.1f))
        for (i in 0..4) p.line(0.8f + i * 0.35f, d - 0.7f, 0.14f, 0.8f + i * 0.35f, d - 0.7f, 0.7f, Iso.shade(wood, 0.2f), 1.4f)
        p.line(w / 2, d / 2, 0.7f, w / 2, d / 2, 2.2f, 0x6A4A2E, 3f)
        p.face(sail, w / 2, d / 2 - 0.4f, 2.1f, w / 2, d / 2 + 0.4f, 2.1f, w / 2, d / 2 + 0.4f, 1.0f, w / 2, d / 2 - 0.4f, 1.0f)
        // grue
        p.line(w - 0.25f, 0.25f, 0.14f, w - 0.25f, 0.25f, 1.6f, 0x6A4A2E, 3f)
        p.line(w - 0.25f, 0.25f, 1.6f, w - 0.9f, 0.25f, 1.45f, 0x6A4A2E, 2.4f)
    }

    private fun lair(p: Iso, w: Float, d: Float, rock: Int, acc: Int, glow: Int, kind: Int) {
        p.shadow(0f, 0f, w, d)
        val cx = w / 2; val cy = d / 2
        when (kind) {
            0 -> { // antre du dragon
                p.ball(cx, cy - 0.2f, 0.6f, 1.35f, rock, 1.1f)
                p.ball(cx + 0.8f, cy - 0.5f, 0.5f, 0.6f, Iso.shade(rock, 0.1f), 0.8f)
                p.oval(cx, cy + 0.85f, 0.45f, 26f, 28f, 0x1A1008)
                p.oval(cx, cy + 0.9f, 0.4f, 18f, 20f, Iso.withAlpha(glow, 0.8f))
                p.ball(cx - 0.9f, cy + 1.0f, 0.12f, 0.16f, 0xF0E8D0, 0.2f)
                p.ball(cx - 0.65f, cy + 1.1f, 0.1f, 0.14f, 0xE8D8C0, 0.18f)
                p.ball(cx + 0.9f, cy + 0.9f, 0.14f, 0.16f, acc, 0.2f)
                // pointes
                for (k in 0..3) p.face(Iso.shade(rock, -0.15f), cx - 0.9f + k * 0.55f, cy - 0.5f, 1.2f + (k % 2) * 0.2f, cx - 0.7f + k * 0.55f, cy - 0.5f, 1.2f, cx - 0.8f + k * 0.55f, cy - 0.5f, 1.65f)
            }
            1 -> { // nid de griffon
                p.cyl(cx, cy, 0f, 1.0f, 0.5f, rock)
                p.cyl(cx - 0.2f, cy - 0.1f, 1.0f, 1.8f, 0.34f, Iso.shade(rock, 0.1f))
                p.ball(cx - 0.2f, cy - 0.1f, 1.95f, 0.6f, 0x8A6A3A, 0.22f)
                p.ball(cx - 0.2f, cy - 0.05f, 2.05f, 0.34f, 0xE8D8B0, 0.12f)
                for (k in 0..2) p.line(cx - 0.4f + k * 0.2f, cy, 2.0f, cx - 0.5f + k * 0.28f, cy + 0.1f, 2.5f, acc, 2.6f)
            }
            else -> { // clairière de la licorne
                p.disc(cx, cy, 0.01f, 0.85f, 0x8FD070)
                for (k in 0..6) p.ball(cx + kotlin.math.cos(k * 0.9f) * 0.5f, cy + kotlin.math.sin(k * 0.9f) * 0.4f, 0.1f, 0.1f, if (k % 2 == 0) glow else acc, 0.1f)
                p.box(cx - 0.55f, cy - 0.05f, 0f, cx - 0.43f, cy + 0.07f, 1.1f, 0xF4F0FF)
                p.box(cx + 0.43f, cy - 0.05f, 0f, cx + 0.55f, cy + 0.07f, 1.1f, 0xF4F0FF)
                val cols = intArrayOf(0xE8302A, 0xF0A02A, 0xF0E02A, 0x4AD05A, 0x3A9AD8, 0x9A4AD8)
                for ((i, c) in cols.withIndex()) {
                    val r = 0.62f - i * 0.045f
                    var a = 0f
                    while (a < 3.1f) {
                        val x0 = cx + kotlin.math.cos(a) * r; val z0 = 1.0f + kotlin.math.sin(a) * r * 1.1f
                        val a2 = a + 0.2f
                        p.line(x0, cy, z0, cx + kotlin.math.cos(a2) * r, cy, 1.0f + kotlin.math.sin(a2) * r * 1.1f, c, 3f)
                        a = a2
                    }
                }
                p.ball(cx, cy + 0.3f, 0.35f, 0.18f, 0xFFFFFF, 0.22f)
                p.line(cx, cy + 0.3f, 0.5f, cx + 0.03f, cy + 0.3f, 0.75f, acc, 2.4f)
            }
        }
    }

    /** Chantier : des planches, des piquets, un tas de sable et un petit toit provisoire. */
    fun construction(p: Iso, w: Float, d: Float) {
        p.shadow(0f, 0f, w, d, 0.2f)
        p.box(0.15f, 0.15f, 0f, w - 0.15f, d - 0.15f, 0.06f, 0xC8B088)
        val posts = listOf(0.25f to 0.25f, w - 0.3f to 0.25f, 0.25f to d - 0.3f, w - 0.3f to d - 0.3f)
        for ((x, y) in posts) p.box(x, y, 0f, x + 0.07f, y + 0.07f, 0.8f, 0x8A6A44)
        p.line(0.28f, d - 0.26f, 0.7f, w - 0.26f, d - 0.26f, 0.7f, 0x6A4A2E, 2.2f)
        p.line(w - 0.26f, 0.28f, 0.7f, w - 0.26f, d - 0.26f, 0.7f, 0x6A4A2E, 2.2f)
        p.line(0.28f, d - 0.26f, 0.3f, w - 0.26f, d - 0.26f, 0.3f, 0x6A4A2E, 2.2f)
        p.ball(w * 0.5f, d * 0.5f, 0.12f, min(w, d) * 0.28f, 0xD8C890, 0.2f)
        p.box(w * 0.3f, d * 0.35f, 0.06f, w * 0.3f + 0.4f, d * 0.35f + 0.18f, 0.2f, 0xB8905A)
        p.box(w * 0.55f, d * 0.55f, 0.06f, w * 0.55f + 0.18f, d * 0.55f + 0.4f, 0.26f, 0xA8804A)
    }
}
