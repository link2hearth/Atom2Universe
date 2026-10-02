package com.Atom2Universe.app.audioeditor.ui

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Les calculs de la chronologie qui n'ont pas besoin d'Android : graduations de la règle, aimant,
 * zoom, découpage du temps en heures / minutes / secondes. Tout se teste en JVM.
 */
object TimelineMath {

    /** Espacements de graduations majeures possibles, en secondes, et combien de petits intervalles on trace entre deux. */
    private class Step(val seconds: Double, val minor: Int)

    private val STEPS = listOf(
        Step(0.001, 5), Step(0.002, 4), Step(0.005, 5),
        Step(0.01, 5), Step(0.02, 4), Step(0.05, 5),
        Step(0.1, 5), Step(0.2, 4), Step(0.5, 5),
        Step(1.0, 5), Step(2.0, 4), Step(5.0, 5),
        Step(10.0, 5), Step(15.0, 3), Step(30.0, 3),
        Step(60.0, 6), Step(120.0, 4), Step(300.0, 5),
        Step(600.0, 5), Step(900.0, 3), Step(1800.0, 3),
        Step(3600.0, 6), Step(7200.0, 4),
    )

    /** Écart entre deux graduations majeures en trames : le plus petit pas « rond » qui fait au moins [minPx] pixels. */
    fun majorTickFrames(framesPerPixel: Double, rate: Int, minPx: Double): Long {
        val s = stepFor(framesPerPixel, rate, minPx)
        return max(1L, Math.round(s.seconds * rate))
    }

    /** Nombre d'intervalles entre deux graduations majeures (donc minor − 1 petits traits). */
    fun minorDivisions(framesPerPixel: Double, rate: Int, minPx: Double): Int = stepFor(framesPerPixel, rate, minPx).minor

    private fun stepFor(framesPerPixel: Double, rate: Int, minPx: Double): Step {
        val wantSeconds = framesPerPixel * minPx / rate
        return STEPS.firstOrNull { it.seconds >= wantSeconds } ?: STEPS.last()
    }

    /**
     * La trame candidate la plus proche de [frame], si elle est à moins de [threshold] trames ; sinon [frame].
     * [candidates] n'a pas besoin d'être triée.
     */
    fun snap(frame: Long, candidates: LongArray, threshold: Double): Long {
        var best = frame
        var bestD = Double.MAX_VALUE
        for (c in candidates) {
            val d = abs((c - frame).toDouble())
            // À égale distance, le premier candidat garde la place : le résultat ne dépend pas de l'ordre de rangement.
            if (d <= threshold && d < bestD) { best = c; bestD = d }
        }
        return best
    }

    /**
     * Déplacement d'un clip : le début **ou** la fin du clip se colle au candidat le plus proche, selon
     * celui des deux qui est le plus près d'en trouver un. Rend le nouveau début ; [start] inchangé si rien n'est à portée.
     */
    fun snapSpan(start: Long, length: Long, candidates: LongArray, threshold: Double): Long {
        val a = snap(start, candidates, threshold)
        val b = snap(start + length, candidates, threshold) - length
        val da = abs(a - start)
        val db = abs(b - start)
        val aHit = a != start
        val bHit = b != start
        return when {
            aHit && bHit -> if (da <= db) a else b
            aHit -> a
            bHit -> b
            else -> start
        }
    }

    /** Le zoom qui fait tenir [lengthFrames] (au moins [minSeconds] secondes) dans [widthPx] pixels. */
    fun fitFramesPerPixel(lengthFrames: Long, widthPx: Int, rate: Int, minSeconds: Double = 10.0): Double {
        val frames = max(lengthFrames.toDouble(), minSeconds * rate)
        return frames / max(1, widthPx)
    }

    /** Bornes du zoom : au plus 4 pixels par trame ; au plus assez large pour voir [maxSeconds] secondes dans [widthPx]. */
    fun minFramesPerPixel() = 0.25

    fun maxFramesPerPixel(widthPx: Int, rate: Int, lengthFrames: Long, maxSeconds: Double = 4 * 3600.0): Double {
        val frames = max(lengthFrames * 2.0, 60.0 * rate)
        return min(frames, maxSeconds * rate) / max(1, widthPx)
    }

    /**
     * Défilement (trame affichée au bord gauche) qui garde la trame [focusFrame] sous le pixel [focusX]
     * après un changement de zoom.
     */
    fun scrollForZoom(focusFrame: Double, focusX: Double, newFramesPerPixel: Double): Long =
        Math.round(focusFrame - focusX * newFramesPerPixel)

    /** Heures, minutes, secondes et centièmes d'une position. */
    class Clock(val hours: Long, val minutes: Long, val seconds: Long, val centis: Long)

    fun clock(frames: Long, rate: Int): Clock {
        val f = max(0L, frames)
        val totalCentis = f * 100 / rate
        val centis = totalCentis % 100
        val totalSeconds = totalCentis / 100
        return Clock(totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60, centis)
    }

    /** Première graduation majeure visible : le plus petit multiple de [step] qui est ≥ [frame]. */
    fun firstTickAtOrAfter(frame: Long, step: Long): Long = if (frame <= 0) 0 else ceil(frame.toDouble() / step).toLong() * step

    fun floorTick(frame: Long, step: Long): Long = floor(frame.toDouble() / step).toLong() * step
}

/** Les couleurs de piste : celle choisie, ou une de la palette selon la place de la piste. */
object TrackColors {
    val PALETTE = intArrayOf(
        0xFF4FC3F7.toInt(), 0xFFFFB74D.toInt(), 0xFF81C784.toInt(), 0xFFBA68C8.toInt(),
        0xFFE57373.toInt(), 0xFF4DB6AC.toInt(), 0xFFFFD54F.toInt(), 0xFF9575CD.toInt(),
    )

    fun colorFor(color: Int, index: Int): Int = if (color != 0) color else PALETTE[index.mod(PALETTE.size)]
}
