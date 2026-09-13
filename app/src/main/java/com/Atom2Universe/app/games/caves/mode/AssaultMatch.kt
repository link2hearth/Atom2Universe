package com.Atom2Universe.app.games.caves.mode

import kotlin.math.ceil

/**
 * Le déroulé d'une partie Assaut : manches, chrono et score. Rien d'Android ni de rendu ici, pour
 * pouvoir le tester seul (voir AssaultMatchTest).
 *
 * Une manche = éliminer tous les ennemis avant la fin du chrono, sans mourir. Entre deux manches,
 * une courte pause laisse le temps de lire le bilan. Le score s'additionne d'une manche à l'autre.
 */
internal class AssaultMatch(
    val targetsPerRound: Int = 8,
    val roundSeconds: Float = 60f,
    val pauseSeconds: Float = 4f,
) {
    enum class Phase { PLAYING, BETWEEN_ROUNDS }
    enum class RoundEnd { CLEARED, TIME_UP, DIED }
    enum class Event { NONE, ROUND_STARTED, ROUND_ENDED }

    /** Photo de la partie, pour l'affichage. Les secondes sont arrondies au-dessus (1 tant qu'il en reste). */
    data class Status(
        val round: Int,
        val phase: Phase,
        val secondsLeft: Int,
        val targetsDown: Int,
        val targetsTotal: Int,
        val headshots: Int,
        val score: Int,
        val lastEnd: RoundEnd?,
        val lastTimeBonus: Int,
        val pauseSecondsLeft: Int,
    )

    var round = 0; private set
    // Au départ, une pause déjà écoulée : la première mise à jour lance la manche 1.
    var phase = Phase.BETWEEN_ROUNDS; private set
    var targetsDown = 0; private set
    var headshots = 0; private set
    var score = 0; private set

    private var timeLeft = 0f
    private var pauseLeft = 0f
    private var lastEnd: RoundEnd? = null
    private var lastTimeBonus = 0

    /** Fait avancer le chrono de [dt] secondes et dit si une manche vient de commencer ou de finir. */
    fun update(dt: Float): Event {
        when (phase) {
            Phase.PLAYING -> {
                timeLeft -= dt
                if (timeLeft <= 0f) {
                    timeLeft = 0f
                    endRound(RoundEnd.TIME_UP)
                    return Event.ROUND_ENDED
                }
            }
            Phase.BETWEEN_ROUNDS -> {
                pauseLeft -= dt
                if (pauseLeft <= 0f) {
                    startRound()
                    return Event.ROUND_STARTED
                }
            }
        }
        return Event.NONE
    }

    /**
     * Un ennemi vient de tomber, abattu d'un tir à la tête si [headshot].
     * Renvoie [Event.ROUND_ENDED] si c'était le dernier de la manche.
     */
    fun onTargetDown(headshot: Boolean): Event {
        if (phase != Phase.PLAYING) return Event.NONE
        targetsDown++
        score += KILL_POINTS
        if (headshot) {
            headshots++
            score += HEADSHOT_BONUS
        }
        if (targetsDown < targetsPerRound) return Event.NONE
        endRound(RoundEnd.CLEARED)
        return Event.ROUND_ENDED
    }

    /** Le joueur vient de mourir : la manche est perdue (les points déjà gagnés restent). */
    fun onPlayerDied(): Event {
        if (phase != Phase.PLAYING) return Event.NONE
        endRound(RoundEnd.DIED)
        return Event.ROUND_ENDED
    }

    fun status() = Status(
        round, phase, ceil(timeLeft).toInt(), targetsDown, targetsPerRound, headshots, score,
        lastEnd, lastTimeBonus, ceil(pauseLeft.coerceAtLeast(0f)).toInt(),
    )

    private fun startRound() {
        round++
        phase = Phase.PLAYING
        timeLeft = roundSeconds
        targetsDown = 0
        lastEnd = null
        lastTimeBonus = 0
    }

    private fun endRound(end: RoundEnd) {
        phase = Phase.BETWEEN_ROUNDS
        pauseLeft = pauseSeconds
        lastEnd = end
        // Finir vite rapporte : chaque seconde restante vaut des points.
        lastTimeBonus = if (end == RoundEnd.CLEARED) ceil(timeLeft).toInt() * TIME_BONUS_PER_SECOND else 0
        score += lastTimeBonus
    }

    companion object {
        const val KILL_POINTS = 100
        const val HEADSHOT_BONUS = 50
        const val TIME_BONUS_PER_SECOND = 10
    }
}
