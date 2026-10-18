package com.Atom2Universe.app.games.cosmorun

import kotlin.math.abs

/**
 * Transforme un doigt qui glisse en gestes. Fonction pure de (x, y) : testable sans appareil.
 *
 * Règle des runners : **un geste par contact**. Le premier déplacement franc décide, puis plus rien ne part
 * tant que le doigt n'est pas levé, quelle que soit la suite du trajet : une courbe, une dérive, une
 * hésitation ou un relâchement lent ne peuvent jamais fabriquer un geste de plus.
 * Seule exception : un demi-tour franc (sens opposé, deux fois le seuil, mesuré depuis le point le plus
 * lointain atteint) relance un geste, pour passer vite de gauche à droite sans lever le doigt.
 */
class CosmoRunGesture(private val threshold: Float) {
    enum class Swipe { LEFT, RIGHT, UP, DOWN }

    private var anchorX = 0f
    private var anchorY = 0f
    private var last: Swipe? = null

    /** Vrai dès qu'un geste est parti depuis le dernier [down]. */
    var fired = false
        private set

    private var startX = 0f
    private var startY = 0f
    private var reach = 0f

    fun down(x: Float, y: Float, @Suppress("UNUSED_PARAMETER") timeMs: Long = 0L) {
        anchorX = x; anchorY = y; startX = x; startY = y; reach = 0f; last = null; fired = false
    }

    /**
     * Le doigt se lève. Un geste court mais net (au moins [SHORT] du seuil) part quand même : un coup de pouce
     * rapide ne doit pas être pris pour un tap. Rend le geste, ou null.
     */
    fun up(x: Float, y: Float): Swipe? {
        if (fired) return null
        val dx = x - startX; val dy = y - startY
        val major = maxOf(abs(dx), abs(dy)); val minor = minOf(abs(dx), abs(dy))
        if (major < threshold * SHORT || major < minor * AXIS_RATIO) return null
        return fire(if (abs(dx) >= abs(dy)) (if (dx > 0) Swipe.RIGHT else Swipe.LEFT) else (if (dy > 0) Swipe.DOWN else Swipe.UP), x, y)
    }

    /** Vrai si le doigt est resté quasi immobile depuis [down] : un vrai tap (saut). */
    val isTap get() = !fired && reach < threshold * SHORT

    fun move(x: Float, y: Float, @Suppress("UNUSED_PARAMETER") timeMs: Long = 0L): Swipe? {
        reach = maxOf(reach, abs(x - startX), abs(y - startY))
        val previous = last
        if (previous == null) {
            val dx = x - anchorX; val dy = y - anchorY
            val major = maxOf(abs(dx), abs(dy)); val minor = minOf(abs(dx), abs(dy))
            if (major < threshold) return null
            // Diagonale : on attend que l'un des axes l'emporte nettement, sans attendre indéfiniment.
            if (major < minor * AXIS_RATIO && major < threshold * PATIENCE) return null
            return fire(if (abs(dx) >= abs(dy)) (if (dx > 0) Swipe.RIGHT else Swipe.LEFT) else (if (dy > 0) Swipe.DOWN else Swipe.UP), x, y)
        }
        // Après un geste : l'ancre suit le doigt dans le sens du geste, pour mesurer un demi-tour depuis l'extrême.
        when (previous) {
            Swipe.RIGHT -> if (x > anchorX) anchorX = x
            Swipe.LEFT -> if (x < anchorX) anchorX = x
            Swipe.DOWN -> if (y > anchorY) anchorY = y
            Swipe.UP -> if (y < anchorY) anchorY = y
        }
        val back = when (previous) {
            Swipe.RIGHT -> anchorX - x
            Swipe.LEFT -> x - anchorX
            Swipe.DOWN -> anchorY - y
            Swipe.UP -> y - anchorY
        }
        val drift = when (previous) {
            Swipe.RIGHT, Swipe.LEFT -> abs(y - anchorY)
            else -> abs(x - anchorX)
        }
        // Le retour doit être net et surtout franc dans son axe : une dérive de côté n'en est pas un.
        if (back < threshold * REVERSAL || back < drift * AXIS_RATIO) return null
        return fire(opposite(previous), x, y)
    }

    private fun opposite(s: Swipe) = when (s) {
        Swipe.LEFT -> Swipe.RIGHT; Swipe.RIGHT -> Swipe.LEFT; Swipe.UP -> Swipe.DOWN; Swipe.DOWN -> Swipe.UP
    }

    private fun fire(swipe: Swipe, x: Float, y: Float): Swipe {
        anchorX = x; anchorY = y; last = swipe; fired = true
        return swipe
    }

    private companion object {
        /** Un axe doit dépasser l'autre de 40 % pour décider. */
        const val AXIS_RATIO = 1.4f
        /** Jusqu'à 2 fois le seuil, on attend un axe dominant ; au-delà, le plus grand gagne. */
        const val PATIENCE = 2f
        const val REVERSAL = 2f
        /** Part du seuil au-delà de laquelle un mouvement n'est plus un tap. */
        const val SHORT = .45f
    }
}
