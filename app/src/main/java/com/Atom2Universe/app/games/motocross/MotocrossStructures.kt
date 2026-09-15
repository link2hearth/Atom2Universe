package com.Atom2Universe.app.games.motocross

import kotlin.math.*

/** Surfaces minces : leur vide inférieur reste réellement praticable. */
internal data class MotocrossRoad(
    val points: List<MotocrossTrack.Point>,
    val loopId: Int = -1
) {
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
}

internal data class MotocrossLoop(val id: Int, val x: Float, val radius: Float) {
    val y: Float get() = radius
}

/** Portions complètes : sol de secours, surfaces suspendues et raccords plats. */
internal object MotocrossStructures {
    data class Recipe(
        val ground: List<MotocrossTrack.Point>,
        val decks: List<List<MotocrossTrack.Point>> = emptyList(),
        val loop: MotocrossLoop? = null
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
            decks = listOf(
                profile(41f,5.8f, 52f,5.8f, 63f,7.8f, 73f,5.8f,
                    84f,7.5f, 94f,5.8f, 105f,8.2f, launchX = 105f),
                profile(116f,6f, 128f,6f, 139f,7.6f, 149f,5.5f, 165f,0f)
            )
        ),
        Recipe(
            ground = profile(0f,0f, 19f,0f, 33f,7.2f, 40f,0f,
                50f,0f, 60f,2f, 70f,0f, 80f,2.4f, 91f,0f, 102f,1.7f,
                114f,0f, 202f,0f, launchX = 33f),
            decks = listOf(
                profile(43f,6.2f, 55f,6.2f, 67f,8.5f, 77f,6.2f, 90f,9f, launchX = 90f),
                profile(101f,6.6f, 114f,6.6f, 127f,9.2f, launchX = 127f),
                profile(139f,6.5f, 150f,6.5f, 161f,8f, 173f,5.5f, 189f,0f)
            )
        )
    )

    fun looping(id: Int, radius: Float): Recipe = Recipe(
        ground = profile(0f,0f, 70f,0f, 80f,2f, 90f,0f, 102f,2.8f, 114f,0f, 132f,0f),
        loop = MotocrossLoop(id, 42f, radius)
    )

    fun loopRoad(loop: MotocrossLoop): MotocrossRoad {
        // Sens de parcours : droite au pied, montée à droite, plafond vers la
        // gauche, descente à gauche. Les normales pointent vers l'intérieur.
        val count = ceil(2f * PI.toFloat() * loop.radius / .16f).toInt()
        val points = (0..count).map { i ->
            val angle = i.toFloat() / count * 2f * PI.toFloat()
            if (i == count) MotocrossTrack.Point(loop.x, 0f)
            else MotocrossTrack.Point(loop.x + sin(angle) * loop.radius,
                loop.y - cos(angle) * loop.radius)
        }
        return MotocrossRoad(points, loop.id)
    }
}
