package com.Atom2Universe.app.games.roguelike.demo

import com.Atom2Universe.app.games.roguelike.MonsterType

/** Anatomies distinctes, corps ancrés : seules les mâchoires et les queues s'animent au repos. */
internal object DungeonBeastSprites {
    fun draw(p: CreaturePixels) = when (p.type) {
        MonsterType.FELINE -> feline(p)
        MonsterType.WOLF -> wolf(p)
        MonsterType.BEAR -> bear(p)
        else -> error("Not a beast: ${p.type}")
    }

    private fun feline(p: CreaturePixels) = with(p) {
        // Silhouette basse : dos long, épaules tendues et pattes fléchies.
        limb(31, 21, 37, 18, body, 2)
        limb(37, 18, 36, 12 + step / 2, body, 2)
        limb(15, 24, 12, 32, dark, 3)
        limb(30, 23, 33, 32, dark, 3)
        poly(ink, 10, 19, 17, 15, 29, 16, 35, 20, 34, 27, 25, 29, 15, 27, 9, 25)
        poly(body, 12, 20, 18, 17, 29, 18, 33, 21, 32, 26, 24, 27, 14, 25)
        line(18, 17, 28, 18, light)
        oval(29, 23, 4, 4, accent)
        limb(13, 23, 10, 30, body, if (boss) 5 else 4)
        limb(29, 25, 27, 31, body, 4)
        plate(6, 31, 8, 3, dark)
        plate(25, 31, 7, 3, dark)
        for (x in listOf(7, 9, 11, 26, 28)) dot(x, 33, ivory)
        // Petite tête anguleuse en trois-quarts, yeux étroits sous les arcades.
        poly(ink, 5, 11, 8, 9, 15, 10, 19, 15, 18, 23, 13, 27, 5, 24, 2, 20, 3, 14)
        poly(body, 5, 14, 9, 11, 14, 12, 17, 16, 16, 22, 12, 24, 6, 22, 4, 19)
        poly(dark, 4, 14, 4, 8, 8, 11, 8, 14)
        poly(dark, 13, 12, 17, 9, 18, 15)
        line(5, 15, 9, 16, ink, 2)
        line(12, 16, 16, 15, ink, 2)
        rect(6, 17, 3, 1, eye)
        rect(13, 17, 2, 1, eye)
        poly(light, 9, 18, 12, 18, 14, 22, 7, 22)
        rect(8, 20, 4, 2, ink)
        val jaw = if (pose in 3..5) 1 else 0
        plate(5, 23, 12, 4 + jaw, ink)
        line(6, 26 + jaw, 14, 26 + jaw, accent)
        if (boss) {
            // Smilodon : avant-train massif, canines hors de la gueule, sans crinière.
            poly(accent, 17, 15, 20, 12, 23, 16, 21, 23, 18, 25)
            poly(ivory, 6, 23, 9, 23, 8, 31, 6, 33)
            poly(ivory, 13, 23, 16, 23, 15, 30, 13, 32)
            line(18, 17, 19, 23, light)
            line(11, 11, 12, 14, accent)
        } else {
            rect(6, 23, 2, 2, ivory)
            rect(14, 23, 1, 2, ivory)
        }
        if (style == DungeonDemoSprites.MonsterStyle.FELINE_STRIPED) {
            for (i in 0..4) poly(dark, 18 + i * 3, 17, 20 + i * 3, 18, 18 + i * 3, 24)
            line(5, 13, 8, 14, dark)
            line(13, 13, 15, 12, dark)
        }
    }

    private fun wolf(p: CreaturePixels) = with(p) {
        poly(ink, 29, 17, 36, 20, 39, 28 + step / 2, 34, 27, 29, 23)
        poly(body, 31, 19, 35, 22, 37, 26 + step / 2, 32, 24)
        limb(17, 23, 15, 32, dark, 3)
        limb(30, 24, 32, 32, dark, 3)
        poly(ink, 11, 15, 19, 12, 29, 16, 34, 21, 31, 27, 21, 27, 12, 24)
        poly(body, 13, 17, 20, 14, 29, 18, 32, 21, 29, 25, 19, 25, 13, 22)
        line(21, 15, 28, 18, light)
        limb(14, 23, 12, 32, body, 4)
        limb(28, 25, 27, 32, body, 4)
        plate(9, 32, 7, 3, dark)
        plate(25, 32, 7, 3, dark)
        // Garrot hérissé et tête fixe : aucune pièce du visage ne flotte entre deux poses.
        poly(ink, 9, 12, 17, 8, 21, 14, 19, 16, 23, 20, 19, 20, 19, 25, 13, 22, 9, 24, 7, 17)
        poly(accent, 11, 13, 16, 11, 19, 15, 17, 17, 20, 19, 17, 19, 16, 23, 11, 20)
        poly(ink, 4, 14, 4, 3, 10, 9, 14, 8, 18, 3, 19, 16, 16, 23, 8, 25, 2, 21)
        poly(body, 6, 13, 6, 7, 10, 11, 14, 10, 17, 7, 17, 16, 14, 21, 8, 22, 4, 20)
        line(5, 14, 9, 15, dark, 2)
        line(12, 15, 16, 14, dark, 2)
        // Les deux yeux sont posés après le masque et les arcades.
        rect(6, 16, 3, 1, eye)
        rect(13, 16, 3, 1, eye)
        poly(light, 9, 17, 12, 17, 13, 21, 8, 22, 5, 20)
        rect(6, 20, 5, 2, ink)
        val jaw = if (pose in 3..5) 1 else 0
        rect(7, 23, 8, 2 + jaw, ink)
        dot(8, 23, ivory)
        dot(13, 23, ivory)
        line(9, 25 + jaw, 13, 25 + jaw, accent)
        if (boss) {
            // Loup ancien : cou plus épais, poil hérissé et cicatrice loin des yeux.
            for (i in 0..2) poly(light, 18 + i * 3, 13 + i, 19 + i * 3, 8 + i, 22 + i * 3, 16 + i)
            line(17, 19, 16, 22, light)
            rect(8, 23, 1, 3, ivory)
            rect(13, 23, 1, 3, ivory)
        }
    }

    private fun bear(p: CreaturePixels) = with(p) {
        // Dos bombé au garrot, arrière-train bas et membres lourds : pas de tête ronde d'ourson.
        shell(34, 23, 2, 2, dark)
        limb(18, 24, 17, 31, dark, 5)
        limb(30, 25, 32, 31, dark, 5)
        poly(ink, 9, 18, 12, 10, 19, 7, 26, 11, 33, 16, 37, 23, 34, 29, 20, 30, 11, 26)
        poly(body, 11, 18, 14, 12, 19, 9, 25, 13, 32, 18, 35, 23, 32, 27, 20, 28, 13, 24)
        poly(accent, 17, 11, 21, 10, 27, 16, 25, 23, 19, 21)
        line(17, 11, 21, 10, light)
        limb(13, 23, 10, 30, body, if (boss) 8 else 6)
        limb(29, 26, 27, 31, body, 6)
        plate(7, 30, 11, 5, dark)
        plate(25, 31, 10, 4, dark)
        for (x in listOf(8, 11, 14, 26, 29, 32)) line(x, 33, x - 1, 34, ivory)
        // Oreilles minuscules et tête basse presque rectangulaire.
        shell(7, 13, 2, 2, dark)
        shell(17, 12, 2, 2, dark)
        poly(ink, 5, 14, 11, 12, 18, 14, 21, 19, 18, 25, 12, 29, 4, 27, 1, 22)
        poly(body, 6, 16, 12, 14, 17, 16, 18, 21, 15, 25, 10, 26, 5, 24, 3, 21)
        line(5, 17, 9, 18, dark, 2)
        line(12, 18, 17, 17, dark, 2)
        rect(6, 19, 2, 1, eye)
        rect(14, 19, 2, 1, eye)
        poly(accent, 8, 21, 13, 21, 15, 25, 6, 25)
        rect(7, 22, 5, 2, ink)
        val jaw = if (pose in 3..5) 1 else 0
        rect(6, 26, 10, 2 + jaw, ink)
        rect(7, 26, 2, 2, ivory)
        rect(13, 26, 1, 2, ivory)
        if (boss) {
            // Ours des cavernes : bosse massive, longue fourrure et épaules grisonnantes.
            poly(dark, 18, 8, 21, 4, 23, 8, 26, 6, 28, 12, 32, 13, 28, 16)
            for (i in 0..3) line(20 + i * 2, 10 + i, 19 + i * 2, 15 + i, light)
            line(17, 21, 16, 24, accent)
            line(10, 31, 8, 34, ivory)
            line(14, 31, 12, 34, ivory)
        }
    }
}
