package com.Atom2Universe.app.games.wavesurf

import kotlin.math.sqrt
import kotlin.random.Random

/** Unités de jeu fixes : la taille de l'écran ne change ni les pentes ni le rythme. */
internal class WaveSurfTerrain(private val random: Random = Random.Default) {
    private data class Wave(
        val start: Float,
        val descentLength: Float,
        val ascentLength: Float,
        val startHeight: Float,
        val valleyHeight: Float,
        val endHeight: Float
    ) {
        val valleyX get() = start + descentLength
        val end get() = valleyX + ascentLength
    }

    data class Sample(val height: Float, val gradient: Float, val curvature: Float)

    private val waves = mutableListOf<Wave>()
    private var lastProfile = 1

    fun reset() {
        waves.clear()
        lastProfile = 1
        waves.add(Wave(-1500f, 750f, 750f, 0f, 270f, 0f))
        // Première descente lisible pour prendre de l'élan dès le départ.
        waves.add(Wave(0f, 850f, 700f, 0f, 300f, -40f))
        ensure(4800f)
    }

    fun ensure(x: Float) {
        while (waves.last().end < x) {
            val previous = waves.last()
            val startHeight = previous.endHeight
            val endHeight: Float
            val valleyHeight: Float
            val descentLength: Float
            val ascentLength: Float
            // Change réellement d'échelle, sans répéter le même gabarit deux fois.
            // Les sommets varient de +140 à -350, les creux restent dans l'écran.
            // Une petite bosse offre ~100 unités de relief, une grande jusqu'à 740.
            val profile = (lastProfile + 1 + random.nextInt(3)) % 4
            lastProfile = profile
            when (profile) {
                0 -> {
                    endHeight = random.nextFloat(80f, 140f)
                    valleyHeight = random.nextFloat(230f, 280f)
                    descentLength = random.nextFloat(600f, 850f)
                    ascentLength = random.nextFloat(600f, 850f)
                }
                1 -> {
                    endHeight = random.nextFloat(-60f, 30f)
                    valleyHeight = random.nextFloat(280f, 340f)
                    descentLength = random.nextFloat(900f, 1350f)
                    ascentLength = random.nextFloat(850f, 1200f)
                }
                2 -> {
                    endHeight = random.nextFloat(-350f, -260f)
                    valleyHeight = random.nextFloat(330f, 390f)
                    descentLength = random.nextFloat(1500f, 2100f)
                    ascentLength = random.nextFloat(1250f, 1800f)
                }
                else -> {
                    endHeight = random.nextFloat(-180f, -80f)
                    valleyHeight = random.nextFloat(260f, 360f)
                    descentLength = random.nextFloat(1800f, 2400f)
                    ascentLength = random.nextFloat(1200f, 1800f)
                }
            }
            // La dérivée maximale du raccord quintique vaut 1,875 : borner la
            // pente à 1,15 évite les murs lorsque deux sommets ont des hauteurs différentes.
            waves.add(Wave(
                previous.end,
                maxOf(descentLength, (valleyHeight - startHeight) * 1.875f / 1.15f),
                maxOf(ascentLength, (valleyHeight - endHeight) * 1.875f / 1.15f),
                startHeight, valleyHeight, endHeight
            ))
        }
    }

    fun prune(x: Float) {
        while (waves.size > 3 && waves[1].end < x) waves.removeAt(0)
    }

    private fun waveAt(x: Float): Wave = waves.firstOrNull { x < it.end } ?: waves.last()

    /** Le rendu n'a besoin ni de dérivées ni d'allouer un Sample par point. */
    fun height(x: Float): Float {
        val wave = waveAt(x)
        return if (x < wave.valleyX) {
            val t = ((x - wave.start) / wave.descentLength).coerceIn(0f, 1f)
            wave.startHeight + (wave.valleyHeight - wave.startHeight) * smooth(t)
        } else {
            val t = ((x - wave.valleyX) / wave.ascentLength).coerceIn(0f, 1f)
            wave.valleyHeight + (wave.endHeight - wave.valleyHeight) * smooth(t)
        }
    }

    fun sample(x: Float): Sample {
        val wave = waveAt(x)
        val descending = x < wave.valleyX
        val start = if (descending) wave.start else wave.valleyX
        val length = if (descending) wave.descentLength else wave.ascentLength
        val startY = if (descending) wave.startHeight else wave.valleyHeight
        val endY = if (descending) wave.valleyHeight else wave.endHeight
        val t = ((x - start) / length).coerceIn(0f, 1f)
        // Interpolation quintique : hauteur, pente ET courbure continues aux raccords.
        val first = 30f * t * t * (1f - t) * (1f - t)
        val second = 60f * t * (1f - t) * (1f - 2f * t)
        val gradient = (endY - startY) * first / length
        val secondDerivative = (endY - startY) * second / (length * length)
        val metric = 1f + gradient * gradient
        return Sample(
            startY + (endY - startY) * smooth(t),
            gradient,
            secondDerivative / (metric * sqrt(metric))
        )
    }

    private fun smooth(t: Float) = t * t * t * (10f + t * (-15f + 6f * t))

    private fun Random.nextFloat(min: Float, max: Float) = min + nextFloat() * (max - min)
}
