package com.Atom2Universe.app.games.motocross

import kotlin.math.*

/** Surfaces minces : leur vide inférieur reste réellement praticable. */
internal data class MotocrossRoad(val points: List<MotocrossTrack.Point>, val loop: Boolean = false) {
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val topIndex = points.indices.maxBy { points[it].y }
    val maxY = points[topIndex].y
    val loopCenterX = points[topIndex].x
}

/** Portions complètes : sol de secours, surfaces suspendues et raccords plats. */
internal object MotocrossStructures {
    data class Recipe(
        val ground: List<MotocrossTrack.Point>,
        val decks: List<List<MotocrossTrack.Point>> = emptyList(),
        val difficulty: Int = 0,
        val loop: Boolean = false,
        val levels: Int = 2,
        val transfer: Boolean = false,
        val guidedDecks: Set<Int> = emptySet()
    )

    private fun profile(vararg xy: Float, launchX: Float? = null): List<MotocrossTrack.Point> {
        require(xy.size >= 4 && xy.size % 2 == 0)
        val knots = xy.toList().chunked(2).map { MotocrossTrack.Point(it[0], it[1]) }
        require(knots.zipWithNext().all { (a, b) -> b.x > a.x })
        return buildList {
            add(knots.first())
            for ((a, b) in knots.zipWithNext()) {
                val count = ceil((b.x - a.x) / .16f).toInt()
                for (i in 1..count) {
                    val t = i.toFloat() / count
                    val endSlope = if (b.x == launchX) 1.15f else 0f
                    val blend = t * t * (3f - 2f * t) + (t * t * t - t * t) * endSlope
                    add(MotocrossTrack.Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * blend))
                }
            }
        }
    }

    // Une montée lancée rejoint le pont. Un saut trop court retombe sur la piste
    // basse ; les deux voies se rejoignent avant le checkpoint final.
    val bridges: List<Recipe> = listOf(
        Recipe(
            ground = profile(0f,0f, 16f,0f, 28f,5.8f, 34f,0f,
                45f,0f, 53f,1.6f, 62f,0f, 71f,1.9f, 80f,0f, 90f,1.4f,
                101f,0f, 158f,0f, launchX = 28f),
            decks = listOf(profile(37f,5.3f, 47f,5.3f, 57f,7.2f, 66f,5.5f,
                77f,8f, 87f,5.5f, 98f,7.1f, 110f,5.3f, 119f,5.3f, 145f,0f))
        ),
        Recipe(
            ground = profile(0f,0f, 18f,0f, 31f,6.5f, 38f,0f,
                48f,0f, 57f,1.8f, 66f,0f, 76f,2.1f, 86f,0f, 97f,1.6f,
                109f,0f, 178f,0f, launchX = 31f),
            difficulty = 1, decks = listOf(
                profile(41f,5.8f, 52f,5.8f, 63f,7.8f, 73f,5.8f,
                    84f,7.5f, 94f,5.8f, 105f,8.2f, launchX = 105f),
                profile(116f,6f, 128f,6f, 139f,7.6f, 149f,5.5f, 165f,0f)
            )
        ),
        Recipe(
            ground = profile(0f,0f, 19f,0f, 33f,7.2f, 40f,0f,
                50f,0f, 60f,2f, 70f,0f, 80f,2.4f, 91f,0f, 102f,1.7f,
                114f,0f, 202f,0f, launchX = 33f),
            difficulty = 2, decks = listOf(
                profile(43f,6.2f, 55f,6.2f, 67f,8.5f, 77f,6.2f, 90f,9f, launchX = 90f),
                profile(101f,6.6f, 114f,6.6f, 127f,9.2f, launchX = 127f),
                profile(139f,6.5f, 150f,6.5f, 161f,8f, 173f,5.5f, 189f,0f)
            )
        )
    ) + listOf(
        // Deux voies longues : le niveau supérieur reste accessible par une rampe continue.
        Recipe(
            ground = profile(0f,0f, 14f,0f, 32f,0f, 45f,1.5f, 58f,0f,
                72f,2f, 88f,0f, 105f,1.8f, 121f,0f, 180f,0f),
            decks = listOf(profile(14f,0f, 38f,6f, 53f,6f, 65f,8f,
                79f,6f, 95f,6f, 110f,8f, 125f,6f, 144f,6f, 168f,0f))
        ),
        // Trois niveaux simultanés, avec un tremplin du premier au deuxième étage.
        Recipe(
            ground = profile(0f,0f, 16f,0f, 31f,6f, 40f,0f, 57f,1.5f,
                72f,0f, 89f,2f, 108f,0f, 132f,1.5f, 149f,0f, 246f,0f, launchX = 31f),
            decks = listOf(
                profile(40f,5.5f, 53f,5.5f, 70f,10.5f, 80f,5.5f,
                    105f,5.5f, 120f,7f, 138f,5.5f, 174f,5.5f, 200f,0f, launchX = 70f),
                profile(80f,11f, 99f,11f, 113f,13f, 131f,11f,
                    150f,13f, 169f,11f, 193f,11f, 230f,0f)
            ), difficulty = 1, levels = 3
        ),
        // Quatre niveaux : chaque saut manqué dispose d'une voie de récupération.
        Recipe(
            ground = profile(0f,0f, 16f,0f, 32f,6.5f, 42f,0f,
                61f,2f, 80f,0f, 101f,1.8f, 123f,0f, 146f,2f, 170f,0f, 322f,0f, launchX = 32f),
            decks = listOf(
                profile(42f,5.5f, 57f,5.5f, 75f,11f, 87f,5.5f,
                    118f,5.5f, 136f,7f, 153f,5.5f, 209f,5.5f, 237f,0f, launchX = 75f),
                profile(85f,11f, 102f,11f, 122f,16.5f, 134f,11f,
                    162f,11f, 180f,12.5f, 201f,11f, 235f,11f, 271f,0f, launchX = 122f),
                profile(133f,16.5f, 152f,16.5f, 170f,18.5f, 188f,16.5f,
                    210f,18f, 229f,16.5f, 252f,16.5f, 305f,0f)
            ), difficulty = 2, levels = 4
        ),
        // Ponts en relais : retombées sur un étage inférieur, puis nouvelle ascension.
        Recipe(
            ground = profile(0f,0f, 16f,0f, 33f,7f, 43f,0f,
                66f,2f, 89f,0f, 110f,2f, 136f,0f, 250f,0f, launchX = 33f),
            decks = listOf(
                profile(44f,6f, 60f,6f, 76f,9f, launchX = 76f),
                profile(90f,5.5f, 109f,5.5f, 130f,11f, launchX = 130f),
                profile(141f,10f, 161f,10f, 178f,12f, 194f,9f, 231f,0f),
                profile(83f,0f, 106f,4.5f, 137f,4.5f, 164f,0f)
            ), difficulty = 1
        )
    )

    /** Boucle ouverte : saut d'entrée, plafond, retour à gauche puis sortie à -45°.
     * La longue descente du sol réceptionne la moto sous l'entrée de la boucle. */
    private fun openLoop(radius: Float, base: Float, tier: Int): Recipe {
        val entry = 62f
        val angleEnd = 7f * PI.toFloat() / 4f
        val count = ceil(angleEnd * radius / .12f).toInt()
        val road = (0..count).map { i ->
            val angle = angleEnd * i / count
            MotocrossTrack.Point(entry + radius * sin(angle), base + radius * (1f - cos(angle)))
        }
        return Recipe(
            ground = profile(0f,0f, 22f,0f, 36f,1.5f, 52f,base - .8f,
                82f,-3f, 100f,-3f, 125f,0f, 142f,0f, launchX = 52f),
            decks = listOf(road), difficulty = tier, loop = true
        )
    }

    val loops = listOf(openLoop(7f, 6f, 0), openLoop(8.5f, 7f, 1),
        openLoop(10f, 8f, 2))

    /** Transfert en deux sauts : le crochet dépasse la verticale et renvoie à gauche.
     * La plateforme est une cuvette ouverte ; son bord droit relance vers la sortie.
     * Elle se termine avant le crochet pour libérer son état de passage dessous
     * avant que la moto revienne s'y poser depuis la droite. */
    private fun aerialTransfer(radius: Float, tier: Int): Recipe {
        val center = 48f
        val endAngle = 2f * PI.toFloat() / 3f
        val count = ceil(radius * endAngle / .12f).toInt()
        val hook = buildList {
            addAll(profile(16f,0f, center,0f))
            for (i in 1..count) {
                val angle = endAngle * i / count
                add(MotocrossTrack.Point(center + radius * sin(angle), radius * (1f - cos(angle))))
            }
        }
        val bowlBottom = radius * .95f
        val bowlLeft = center - 22f
        val bowlRight = center + 2f
        // Parabole : la tangente augmente continûment, y compris à la lèvre de sortie.
        val bowl = (0..200).map { i ->
            val t = i / 200f
            MotocrossTrack.Point(bowlLeft + (bowlRight - bowlLeft) * t,
                bowlBottom + 4.5f * (2f * t - 1f).pow(2))
        }
        val landingX = center + radius + 5f
        return Recipe(
            ground = profile(0f,0f, 16f,0f, 35f,-2f, 88f,-2f, 116f,0f, 174f,0f),
            decks = listOf(hook, bowl,
                profile(landingX,bowlBottom + 2f, landingX + 20f,bowlBottom + 1f,
                    118f,bowlBottom + 1f, 158f,0f)),
            difficulty = tier, transfer = true, guidedDecks = setOf(0)
        )
    }

    val transfers = listOf(aerialTransfer(9f, 0), aerialTransfer(10f, 1), aerialTransfer(11f, 2))
}
