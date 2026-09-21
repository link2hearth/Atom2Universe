package com.Atom2Universe.app.games.roguelike.demo

import com.Atom2Universe.app.games.roguelike.MonsterType

internal object DungeonWildCreatureSprites {
    fun draw(p: CreaturePixels) = when (p.type) {
        MonsterType.SCORPION -> scorpion(p)
        MonsterType.CARNIVOROUS_PLANT -> plant(p)
        MonsterType.SNAKE -> snake(p)
        else -> error("Not a wild creature: ${p.type}")
    }

    private fun scorpion(p: CreaturePixels) = with(p) {
        // Quatre paires de pattes et une queue segmentée recourbée au-dessus du dos.
        for (side in listOf(-1, 1)) for (leg in 0..3) {
            val root = 20 + side * 5
            val knee = 20 + side * (11 + leg % 2)
            val y = 19 + leg * 3
            val stride = if ((pose + leg) % 8 < 4) 1 else -1
            limb(root, y, knee, y - 3 + stride, body, 2)
            limb(knee, y - 3 + stride, knee + side * 4, y + 1, dark, 2)
        }
        val tail = listOf(25 to 22, 29 to 18, 31 to 13, 30 to 8, 26 to 5, 21 to 6)
        for ((i, point) in tail.withIndex()) {
            val sway = if (i > 2) step / 2 else 0
            shell(point.first + sway, point.second, if (boss) 4 else 3, 3)
            rect(point.first - 1 + sway, point.second - 1, 2, 1, light)
        }
        poly(ink, 18 + step / 2, 5, 22 + step / 2, 6, 20 + step / 2, 13)
        poly(eye, 19 + step / 2, 7, 21 + step / 2, 7, 20 + step / 2, 11)
        shell(20, 24 + bob, 8, 8)
        for (y in 19..27 step 3) {
            rect(15, y + bob, 10, 1, dark)
            rect(17, y + 1 + bob, 5, 1, light)
        }
        shell(20, 29 + bob, 6, 4)
        rect(16, 29 + bob, 2, 1, eye)
        rect(22, 29 + bob, 2, 1, eye)
        // Pinces ouvertes/fermées, séparées des pattes.
        for (side in listOf(-1, 1)) {
            val x = 20 + side * 11
            limb(20 + side * 5, 28, x, 26, body, 3)
            shell(x, 29, if (boss) 6 else 4, 4)
            val gap = if (pose in 2..5) 2 else 1
            line(x - 1, 30, x - gap - 1, 34, light, 2)
            line(x + 1, 30, x + gap + 1, 34, body, 2)
            line(x, 31, x, 35, ink)
        }
        if (boss) for (side in listOf(-1, 1)) for (y in 19..25 step 3)
            poly(ivory, 20 + side * 6, y, 20 + side * 10, y - 2, 20 + side * 7, y + 2)
    }

    private fun plant(p: CreaturePixels) = with(p) {
        // Racines porteuses : la plante marche, elle n'est pas plantée dans le sol.
        for (side in listOf(-1, 1)) {
            val x = 20 + side * 5
            val stride = side * step
            limb(20, 24, x + stride, 31, dark, 4)
            for (toe in 0..2) line(x + stride, 31, x + stride + (toe - 1) * 3, 34, accent, 2)
        }
        limb(18, 25, 19 + step / 2, 15, body, 5)
        // Deux feuilles-bras dentées, indépendantes de la bouche.
        for (side in listOf(-1, 1)) {
            val root = 20 + side * 3
            val tip = 20 + side * 16
            poly(ink, root, 22, tip, 15 - side * step / 2, tip - side * 3, 25, root, 25)
            poly(body, root, 23, tip - side, 17 - side * step / 2, tip - side * 4, 24, root, 24)
            line(root, 23, tip - side * 2, 19 - side * step / 2, light)
        }
        val cy = 12 + bob
        shell(20, cy, if (boss) 15 else 12, 10)
        oval(18, cy - 4, 8, 3, light)
        val jaw = if (pose in 2..5) 5 else 3
        oval(20, cy + 2, if (boss) 13 else 10, jaw, ink)
        oval(20, cy + 3, if (boss) 10 else 7, jaw - 1, accent)
        for (x in 12..27 step 3) {
            poly(ivory, x, cy - 1, x + 2, cy - 1, x + 1, cy + 3)
            if (x in 15..24) poly(ivory, x, cy + jaw, x + 2, cy + jaw, x + 1, cy + jaw - 3)
        }
        for (x in listOf(12, 17, 24, 28)) dot(x, cy - 5, accent)
        if (boss) {
            // Gueule hypertrophiée, lobes latéraux et nervures : une mutation végétale.
            for (side in listOf(-1, 1)) {
                val x = 20 + side * 13
                poly(accent, x, cy - 5, x + side * 5, cy - 2, x + side * 2, cy + 4, x, cy + 6)
                line(x, cy - 3, x + side * 2, cy + 2, dark)
            }
            line(15, cy - 7, 18, cy - 3, dark)
            line(25, cy - 7, 22, cy - 3, dark)
            for (y in 20..26 step 3) poly(ivory, 19, y, 24, y - 2, 21, y + 2)
            line(20, cy + 4, 23 + step, cy + 10, accent, 2)
            line(23 + step, cy + 10, 21 + step, cy + 12, accent)
            line(23 + step, cy + 10, 26 + step, cy + 11, accent)
        }
    }

    private fun snake(p: CreaturePixels) = with(p) {
        // Anneaux au sol, cou redressé et tête mobile.
        limb(28, 30, 37, 32 - step / 2, body, 3)
        if (boss) {
            // Un troisième anneau, plus large, porte les deux anneaux existants.
            shell(20, 31, 18, 4)
            oval(20, 30, 13, 2, dark)
            for (x in 5..35 step 4) rect(x, 33, 2, 1, light)
        }
        shell(21, 29, 13, 5)
        oval(21, 28, 8, 2, dark)
        shell(23, 25, 10, 5)
        oval(23, 24, 6, 2, dark)
        for (x in 11..31 step 4) rect(x, 30, 2, 1, light)
        val head = 14 + step / 2
        limb(19, 25, head + 4, 15, body, 6)
        limb(head + 4, 15, head, 10 + bob, body, 6)
        line(head + 5, 16, 20, 25, light, 2)
        if (boss) {
            // Cobra royal : coiffe large et ocelles, en plus de l'agrandissement.
            shell(head + 3, 15 + bob, 10, 11, dark)
            oval(head + 3, 14 + bob, 8, 9, body)
            for (side in listOf(-1, 1)) {
                shell(head + 3 + side * 5, 15 + bob, 3, 4, accent)
                oval(head + 3 + side * 5, 15 + bob, 1, 2, ink)
            }
            line(head + 2, 15, head + 4, 25, light, 3)
        }
        shell(head, 10 + bob, 8, 5)
        oval(head - 2, 9 + bob, 5, 2, light)
        rect(head - 6, 10 + bob, 3, 2, ink)
        rect(head - 6, 10 + bob, 2, 1, eye)
        line(head - 7, 13 + bob, head + 2, 13 + bob, ink)
        rect(head - 5, 14 + bob, 1, if (boss) 3 else 2, ivory)
        if (pose in 2..5) {
            line(head - 7, 13 + bob, head - 11, 14 + bob, accent)
            line(head - 11, 14 + bob, head - 13, 12 + bob, accent)
            line(head - 11, 14 + bob, head - 13, 15 + bob, accent)
        }
        if (style == DungeonDemoSprites.MonsterStyle.SNAKE_COPPER)
            for (x in 14..30 step 4) rect(x, 26, 2, 3, dark)
        else for (x in 13..29 step 4) dot(x, 27, eye)
    }
}
