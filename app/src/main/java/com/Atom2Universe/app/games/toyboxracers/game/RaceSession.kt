package com.Atom2Universe.app.games.toyboxracers.game

import com.Atom2Universe.app.games.toyboxracers.ai.RivalCar
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import kotlin.math.abs

internal enum class RaceDifficulty(val label: String) {
    RELAX("Détente"),
    ARCADE("Arcade"),
    CHAMPION("Champion")
}

internal enum class RacePhase { COUNTDOWN, RACING, FINISHED }
internal enum class PlayMode { EXPLORATION, RACE }

/** État déterministe d'une course, indépendant du rendu et de la fréquence d'image. */
internal class RaceSession(private val track: PrototypeTrack) {
    var phase = RacePhase.COUNTDOWN
        private set
    var countdownSeconds = COUNTDOWN_SECONDS
        private set
    var raceSeconds = 0f
        private set
    var playerLap = 1
        private set
    var playerPosition = 1
        private set
    var finishPosition = 0
        private set
    var finishSerial = 0
        private set
    var wrongWay = false
        private set
    var nextCheckpoint = 0
        private set
    var playerProgress = 0f
        private set

    private val checkpoints = floatArrayOf(0.20f, 0.43f, 0.66f, 0.86f)
    private var lastPlayerDistance = 0f
    private var completedLaps = 0
    private var reverseSeconds = 0f
    private var candidatePosition = 1
    private var candidatePositionSeconds = 0f

    fun reset(playerDistance: Float) {
        phase = RacePhase.COUNTDOWN
        countdownSeconds = COUNTDOWN_SECONDS
        raceSeconds = 0f
        playerLap = 1
        playerPosition = 1
        finishPosition = 0
        wrongWay = false
        nextCheckpoint = 0
        playerProgress = 0f
        lastPlayerDistance = playerDistance
        completedLaps = 0
        reverseSeconds = 0f
        candidatePosition = 1
        candidatePositionSeconds = 0f
    }

    fun updateCountdown(dt: Float, playerDistance: Float) {
        if (phase != RacePhase.COUNTDOWN) return
        lastPlayerDistance = playerDistance
        countdownSeconds -= dt
        if (countdownSeconds <= 0f) {
            countdownSeconds = 0f
            phase = RacePhase.RACING
        }
    }

    fun updateRace(dt: Float, playerDistance: Float, playerOnRoad: Boolean, rivals: List<RivalCar>) {
        if (phase != RacePhase.RACING) return
        raceSeconds += dt
        var delta = playerDistance - lastPlayerDistance
        if (delta < -track.length * 0.5f) delta += track.length
        if (delta > track.length * 0.5f) delta -= track.length

        // Une projection ne doit jamais propulser le classement d'un demi-tour au
        // croisement. Les déplacements ordinaires restent très loin de ce seuil.
        if (abs(delta) <= track.length * MAX_PROGRESS_STEP_FRACTION) {
            playerProgress = (playerProgress + delta).coerceAtLeast(-12f)
            reverseSeconds = if (delta < -0.01f) reverseSeconds + dt else (reverseSeconds - dt * 2f).coerceAtLeast(0f)
            wrongWay = reverseSeconds >= WRONG_WAY_DELAY
            if (delta > 0f && playerOnRoad) {
                advanceCheckpoints(lastPlayerDistance, playerDistance)
                if (phase == RacePhase.FINISHED) {
                    val toFinish = track.wrapDistance(track.length * START_FRACTION - lastPlayerDistance)
                    raceSeconds -= dt * (1f - (toFinish / delta).coerceIn(0f, 1f))
                }
            }
        }
        lastPlayerDistance = playerDistance
        updatePosition(dt, rivals)
        if (phase == RacePhase.FINISHED && finishPosition == 0) {
            // L'hystérésis rend le HUD lisible mais ne doit pas décider du résultat.
            finishPosition = 1 + rivals.count { it.finishSeconds <= raceSeconds }
            playerPosition = finishPosition
        }
    }

    private fun advanceCheckpoints(previous: Float, current: Float) {
        if (nextCheckpoint < checkpoints.size) {
            val target = checkpoints[nextCheckpoint] * track.length
            if (crossedForward(previous, current, target)) nextCheckpoint++
            return
        }

        val finishDistance = track.length * START_FRACTION
        if (!crossedForward(previous, current, finishDistance)) return
        completedLaps++
        nextCheckpoint = 0
        if (completedLaps >= TOTAL_LAPS) {
            phase = RacePhase.FINISHED
            finishSerial++
            playerLap = TOTAL_LAPS
        } else {
            playerLap = completedLaps + 1
        }
    }

    private fun updatePosition(dt: Float, rivals: List<RivalCar>) {
        val wanted = 1 + rivals.count { it.raceProgress > playerProgress + POSITION_HYSTERESIS_METERS }
        if (wanted == playerPosition) {
            candidatePosition = wanted
            candidatePositionSeconds = 0f
        } else if (wanted != candidatePosition) {
            candidatePosition = wanted
            candidatePositionSeconds = 0f
        } else {
            candidatePositionSeconds += dt
            if (candidatePositionSeconds >= POSITION_CONFIRM_SECONDS) {
                playerPosition = candidatePosition
                candidatePositionSeconds = 0f
            }
        }
    }

    private fun crossedForward(previous: Float, current: Float, target: Float): Boolean {
        return if (current >= previous) target > previous && target <= current
        else target > previous || target <= current
    }

    companion object {
        const val TOTAL_LAPS = 3
        const val START_FRACTION = 0.015f
        private const val COUNTDOWN_SECONDS = 3.25f
        private const val WRONG_WAY_DELAY = 0.75f
        private const val MAX_PROGRESS_STEP_FRACTION = 0.08f
        private const val POSITION_HYSTERESIS_METERS = 1.5f
        private const val POSITION_CONFIRM_SECONDS = 0.28f
    }
}
