package com.Atom2Universe.app.games.hamlet

/** Petites fermes et animaux en volumes dessinés, sans asset. */
object HamletArtAnimals {
    fun ranch(p: Iso, d: Def, animals: Boolean = true) {
        val w = d.w.toFloat(); val h = d.d.toFloat()
        p.top(0.08f, 0.08f, w - 0.08f, h - 0.08f, 0.02f, 0x82AD57)
        if (d.look.n == 4) {
            p.oval(w * 0.56f, h * 0.55f, 0.03f, 28f, 14f, 0x65B2CA)
            p.oval(w * 0.56f, h * 0.55f, 0.04f, 22f, 10f, 0x91CEDC)
        }
        if (d.look.n == 5) p.ball(w * 0.65f, h * 0.35f, 0.08f, 0.38f, 0x9D9C83, 0.14f)
        p.shadow(0.15f, 0.1f, 0.92f, 0.9f, 0.25f)
        p.box(0.15f, 0.12f, 0f, 1.05f, 0.92f, 0.68f, 0xE8D9AF)
        p.gable(0.09f, 0.06f, 1.11f, 0.98f, 0.68f, 0.5f, d.look.a, 0xE8D9AF, alongX = false, lines = 4)
        p.box(0.47f, 0.93f, 0f, 0.76f, 0.95f, 0.47f, 0x5C4131)
        p.box(w - 0.65f, h - 0.5f, 0.02f, w - 0.25f, h - 0.3f, 0.14f, 0x98704A)
        p.top(w - 0.62f, h - 0.47f, w - 0.28f, h - 0.33f, 0.15f, 0xE7C66D)
        for (x in 0..d.w) {
            p.box(x.toFloat() - 0.035f, h - 0.09f, 0f, x + 0.035f, h - 0.02f, 0.48f, 0xCDB48A)
        }
        for (y in 0..d.d) p.box(w - 0.09f, y - 0.035f, 0f, w - 0.02f, y + 0.035f, 0.48f, 0xCDB48A)
        for (z in listOf(0.2f, 0.38f)) {
            p.line(0f, h - 0.05f, z, w, h - 0.05f, z, 0xA88455, 2f)
            p.line(w - 0.05f, 0f, z, w - 0.05f, h, z, 0xA88455, 2f)
        }
        if (animals) for (i in 0..2) animal(p, d.look.n, w * 0.49f + (i - 1) * 0.28f, h * 0.62f + (i - 1) * 0.13f, 0f)
    }

    /** Dessin local d'un animal ; sa position et son balancement sont animés par le rendu. */
    fun animal(p: Iso, kind: Int, x: Float, y: Float, bob: Float) {
        val small = kind == 0 || kind == 1 || kind == 4
        val body = when (kind) { 1 -> 0xBE9D7C; 3 -> 0xF7F2E4; 4 -> 0xF0EED5; 5 -> 0xB5A08B; else -> 0xFFFAEA }
        val size = if (small) 0.16f else 0.24f
        p.shadow(x - size, y - size, x + size, y + size, 0.14f)
        for (dx in listOf(-size * 0.65f, size * 0.65f)) for (dy in listOf(-size * 0.65f, size * 0.65f)) {
            p.line(x + dx, y + dy, 0.03f, x + dx, y + dy, 0.17f + bob, 0x665344, if (small) 1.4f else 2f)
        }
        p.ball(x, y, 0.24f + bob, size, body, size * 0.8f)
        if (kind == 2) for (i in -1..1) p.ball(x + i * 0.13f, y + 0.05f, 0.3f + bob, 0.13f, 0xF7F0DB, 0.12f)
        if (kind == 3) { p.ball(x - 0.1f, y + 0.14f, 0.3f + bob, 0.09f, 0x514639, 0.06f); p.ball(x + 0.1f, y - 0.08f, 0.36f + bob, 0.09f, 0x514639, 0.05f) }
        val hx = x + size * 0.85f; val hy = y + size * 0.5f
        p.ball(hx, hy, 0.37f + bob, size * 0.65f, body, size * 0.65f)
        p.dot(hx + 0.065f, hy + 0.045f, 0.41f + bob, 1.2f, 0x302B24)
        if (kind == 0 || kind == 4) {
            p.cone(hx + 0.07f, hy + 0.08f, 0.34f + bob, 0.07f, 0.06f, 0xE9AA32)
            if (kind == 0) p.ball(hx, hy, 0.48f + bob, 0.045f, 0xD74C37, 0.05f)
        } else if (kind == 1) {
            for (dx in listOf(-0.05f, 0.05f)) p.ball(hx + dx, hy, 0.57f + bob, 0.03f, body, 0.13f)
        } else {
            for (dx in listOf(-0.06f, 0.06f)) p.ball(hx + dx, hy, 0.47f + bob, 0.045f, body, 0.025f)
            if (kind == 5) for (dx in listOf(-0.05f, 0.05f)) p.line(hx + dx, hy, 0.44f + bob, hx + dx, hy - 0.03f, 0.6f + bob, 0x6E5C48, 2f)
        }
    }
}
