package com.Atom2Universe.app.games.roguelike.demo

import com.Atom2Universe.app.games.roguelike.MonsterType

internal object DungeonHumanoidCreatureSprites {
    fun draw(p: CreaturePixels) = with(p) {
        when (type) {
            MonsterType.GOBLIN -> goblin(this)
            MonsterType.TROLL -> troll(this)
            MonsterType.DEMON -> demon(this)
            else -> error("Not a humanoid: $type")
        }
    }

    private fun goblin(p: CreaturePixels) = with(p) {
        for (side in 0..1) {
            val x = 13 + side * 10
            val stride = if (side == 0) step else -step
            limb(x, 25, x + stride, 31, trousers, 5)
            plate(x + stride - 2, 31, 7, 4, dark)
        }
        plate(11, 16 + bob, 18, 12, shirt)
        rect(12, 25 + bob, 16, 2, trousers)
        rect(19, 25 + bob, 3, 2, metal)
        line(15, 18 + bob, 13, 22 + bob, light)
        limb(10, 18, 6, 25 + step, body, 4)
        limb(28, 18, 31, 24 - step, body, 4)
        shell(7, 27 + step, 3, 3)
        shell(32, 25 - step, 3, 3)
        // Dague courte ; le chef porte une lame dentelée plus large.
        line(33, 24 - step, 34, 13 - step, ink, if (boss) 4 else 3)
        line(34, 22 - step, 35, 14 - step, metal)
        rect(31, 24 - step, 6, 2, wood)
        if (boss) for (y in 15..21 step 3) rect(32, y - step, 2, 2, metal)
        // Grandes oreilles pointues, nez crochu et sourcils épais.
        poly(ink, 12, 8 + bob, 3, 5 + bob, 6, 13 + bob, 13, 15 + bob)
        poly(body, 11, 9 + bob, 5, 7 + bob, 8, 12 + bob, 12, 13 + bob)
        poly(ink, 26, 8 + bob, 36, 5 + bob, 32, 13 + bob, 25, 15 + bob)
        poly(body, 27, 9 + bob, 34, 7 + bob, 31, 12 + bob, 27, 13 + bob)
        shell(20, 11 + bob, 10, 8)
        oval(18, 8 + bob, 6, 3, light)
        rect(12, 10 + bob, 6, 2, dark)
        rect(23, 10 + bob, 5, 2, dark)
        rect(14, 12 + bob, 3, 2, eye)
        rect(23, 12 + bob, 3, 2, eye)
        poly(dark, 19, 10 + bob, 22, 10 + bob, 24, 16 + bob, 18, 16 + bob)
        line(19, 11 + bob, 19, 14 + bob, light)
        rect(15, 17 + bob, 10, 2, ink)
        rect(16, 17 + bob, 2, 2, ivory)
        rect(23, 17 + bob, 1, 2, ivory)
        if (boss) {
            // Chef : couronne de fer, épaulettes et oreilles percées.
            rect(12, 4 + bob, 17, 3, metal)
            for (x in 13..27 step 7) rect(x, 1 + bob, 3, 4, metal)
            rect(19, 4 + bob, 3, 2, eye)
            plate(8, 17, 8, 4, metal)
            plate(25, 17, 7, 4, metal)
            rect(6, 12 + bob, 2, 3, eye)
        }
    }

    private fun troll(p: CreaturePixels) = with(p) {
        // Corps trapu, longs bras bas et jambes courtes.
        for (side in 0..1) {
            val x = 10 + side * 14
            val stride = if (side == 0) step / 2 else -step / 2
            limb(x, 26, x + stride, 31, body, 7)
            plate(x + stride - 2, 31, 10, 4, dark)
            for (claw in 0..2) dot(x + stride + claw * 2, 34, ivory)
        }
        shell(20, 21 + bob, 13, 11)
        oval(19, 20 + bob, 9, 7, light)
        plate(8, 27, 25, 4, trousers)
        rect(8, 16 + bob, 5, 11, shirt)
        rect(27, 16 + bob, 5, 11, shirt)
        for (side in 0..1) {
            val x = if (side == 0) 4 else 31
            limb(x, 17 + bob, x - 1, 28 + if (side == 0) step / 2 else -step / 2, body, 6)
            oval(x + 2, 18 + bob, 3, 4, light)
        }
        // Gourdin avec nœuds ; le chef dispose d'une tête de pierre cerclée.
        line(35, 20 - step / 2, 36, 32 - step / 2, wood, 3)
        plate(32, 12 - step / 2, 7, 12, if (boss) metal else wood)
        for (y in 14..21 step 3) rect(33, y - step / 2, 2, 1, boss.let { if (it) ink else dark })
        shell(20, 11 + bob, 11, 9)
        rect(11, 7 + bob, 18, 3, dark)
        rect(13, 10 + bob, 4, 2, eye)
        rect(24, 10 + bob, 4, 2, eye)
        shell(20, 13 + bob, 4, 3, accent)
        rect(14, 17 + bob, 13, 3, ink)
        poly(ivory, 13, 18 + bob, 14, 12 + bob, 17, 18 + bob)
        poly(ivory, 24, 18 + bob, 27, 12 + bob, 28, 18 + bob)
        rect(18, 19 + bob, 4, 1, accent)
        if (boss) {
            // Ancien : peau rocheuse, défense cassée et épaules cuirassées.
            oval(16, 6, 3, 2, dark)
            line(24, 4, 26, 7, accent)
            poly(ivory, 12, 18, 11, 11, 14, 13, 17, 18)
            plate(5, 16, 7, 4, metal)
            plate(29, 16, 7, 4, metal)
            line(22, 6 + bob, 24, 9 + bob, ivory)
        } else {
            for (x in 14..24 step 5) rect(x, 4 + bob, 3, 2, dark)
        }
    }

    private fun demon(p: CreaturePixels) = with(p) {
        val wing = step / 2
        // Ailes de membrane ouvertes derrière le torse, articulées à la même racine.
        for (side in listOf(-1, 1)) {
            val root = 20 + side * 6
            val edge = 20 + side * 18
            poly(ink, root, 17, edge, 5 + wing, edge, 22 + wing, 20 + side * 12, 19,
                20 + side * 10, 24, root, 21)
            poly(accent, root, 17, edge - side, 8 + wing, edge - side, 19 + wing,
                20 + side * 11, 18, root, 21)
            line(root, 17, edge - side, 8 + wing, body)
            line(root, 17, edge - side, 19 + wing, dark)
        }
        limb(27, 26, 35, 29 + step / 2, body)
        poly(eye, 34, 29 + step / 2, 37, 25 + step / 2, 39, 30 + step / 2)
        for (side in 0..1) {
            val x = 13 + side * 11
            val stride = if (side == 0) step else -step
            limb(x, 25, x + stride, 31, body, 5)
            plate(x + stride - 2, 31, 8, 4, dark)
            line(x + stride + 1, 32, x + stride + 1, 34, ink)
        }
        shell(20, 22 + bob, 9, 9)
        oval(18, 19 + bob, 5, 4, light)
        for (y in 22..26 step 2) rect(17, y + bob, 7, 1, dark)
        limb(10, 18, 7, 26 + step, body, 4)
        limb(28, 18, 30, 26 - step, body, 4)
        for (x in listOf(6, 29)) for (claw in 0..2) rect(x + claw * 2, 29 + if (x == 6) step else -step, 1, 3, ivory)
        shell(20, 11 + bob, 9, 8)
        poly(ivory, 12, 8 + bob, 8, 1 + bob, 11, 2 + bob, 16, 6 + bob)
        poly(ivory, 24, 6 + bob, 29, 1 + bob, 31, 2 + bob, 28, 9 + bob)
        rect(13, 10 + bob, 6, 2, ink)
        rect(22, 10 + bob, 6, 2, ink)
        rect(14, 11 + bob, 4, 1, eye)
        rect(23, 11 + bob, 4, 1, eye)
        poly(dark, 20, 11 + bob, 23, 15 + bob, 18, 15 + bob)
        rect(15, 16 + bob, 11, 2, ink)
        for (x in 16..24 step 4) rect(x, 16 + bob, 1, 2, ivory)
        if (boss) {
            // Archidémon : deuxième paire de cornes, épaules épineuses et sceau incandescent.
            poly(ivory, 16, 5 + bob, 15, 0, 19, 4 + bob)
            poly(ivory, 22, 4 + bob, 25, 0, 25, 6 + bob)
            for (x in listOf(10, 28)) poly(ivory, x - 2, 18, x, 13, x + 3, 18)
            poly(eye, 20, 20 + bob, 23, 23 + bob, 20, 26 + bob, 17, 23 + bob)
        }
    }
}
