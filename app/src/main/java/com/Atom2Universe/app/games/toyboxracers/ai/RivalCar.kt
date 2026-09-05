package com.Atom2Universe.app.games.toyboxracers.ai

import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Adversaire lisible : ligne de course, anticipation et erreurs, sans boost caché. */
internal class RivalCar(
    private val track: PrototypeTrack,
    val index: Int
) {
    var distance = 0f
        private set
    var raceProgress = 0f
        private set
    var speed = 0f
        private set
    var finishSeconds = Float.POSITIVE_INFINITY
        private set
    var worldPosition = Vec3(0f, 0f, 0f)
        private set
    var yawRadians = 0f
        private set

    private var elapsed = 0f
    private var personalityPace = 1f
    private var lineOffset = 0f
    private var mistakePhase = 0f

    fun reset(difficulty: RaceDifficulty) {
        distance = track.wrapDistance(track.length * START_FRACTION - 2.3f - index * 2.15f)
        raceProgress = -2.3f - index * 2.15f
        speed = 0f
        elapsed = 0f
        finishSeconds = Float.POSITIVE_INFINITY
        personalityPace = 0.94f + index * 0.024f
        lineOffset = (index - 2) * 0.42f
        mistakePhase = index * 1.37f + difficulty.ordinal * 0.53f
        updateTransform()
    }

    fun update(dt: Float, difficulty: RaceDifficulty) {
        elapsed += dt
        val config = config(difficulty)
        val here = track.sampleAt(distance)
        val ahead = track.sampleAt(distance + config.lookAhead)
        val headingHere = track.headingRadians(here)
        val headingAhead = track.headingRadians(ahead)
        val curvature = abs(angleDifference(headingAhead, headingHere))
        val cornerFactor = (curvature / 0.75f).coerceIn(0f, 1f)
        val periodicMistake = ((sin(elapsed * config.mistakeFrequency + mistakePhase) - 0.72f) / 0.28f)
            .coerceIn(0f, 1f)
        val targetSpeed = config.maxSpeed * personalityPace *
            (1f - cornerFactor * config.cornerCaution) *
            (1f - periodicMistake * config.mistakeStrength)
        val acceleration = if (speed < targetSpeed) config.acceleration else -config.braking
        speed = (speed + acceleration * dt).coerceIn(0f, config.maxSpeed)
        val travelled = speed * dt
        val finishDistance = RaceSession.TOTAL_LAPS * track.length
        if (raceProgress < finishDistance && raceProgress + travelled >= finishDistance) {
            finishSeconds = elapsed - dt + dt * (finishDistance - raceProgress) / travelled
        }
        raceProgress += travelled
        distance = track.wrapDistance(distance + travelled)
        updateTransform()
    }

    private fun updateTransform() {
        val sample = track.sampleAt(distance)
        val weave = sin(elapsed * 0.55f + mistakePhase) * 0.32f
        val offset = lineOffset + weave
        var y = sample.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
        val crossing = track.crossingAt(distance)
        if (crossing != null) {
            val start = track.sampleAt(crossing.startDistance)
            val end = track.sampleAt(crossing.endDistance)
            val t = ((distance - crossing.startDistance) /
                (crossing.endDistance - crossing.startDistance)).coerceIn(0f, 1f)
            y = start.position.y + (end.position.y - start.position.y) * t +
                sin(t * PI.toFloat()) * 2.2f + PrototypeTrack.CAR_CLEARANCE
        }
        worldPosition = Vec3(
            sample.position.x + sample.right.x * offset,
            y,
            sample.position.z + sample.right.z * offset
        )
        yawRadians = atan2(sample.tangent.x, sample.tangent.z)
    }

    private fun config(difficulty: RaceDifficulty): Config = when (difficulty) {
        RaceDifficulty.RELAX -> Config(16.2f, 7.2f, 7.5f, 12f, 0.34f, 0.22f, 0.72f)
        RaceDifficulty.ARCADE -> Config(18.3f, 7.8f, 8.3f, 15f, 0.27f, 0.15f, 0.61f)
        RaceDifficulty.CHAMPION -> Config(19.6f, 8.2f, 8.8f, 18f, 0.21f, 0.09f, 0.54f)
    }

    private fun angleDifference(target: Float, current: Float): Float {
        var value = target - current
        while (value > PI) value -= (2.0 * PI).toFloat()
        while (value < -PI) value += (2.0 * PI).toFloat()
        return value
    }

    private data class Config(
        val maxSpeed: Float,
        val acceleration: Float,
        val braking: Float,
        val lookAhead: Float,
        val cornerCaution: Float,
        val mistakeStrength: Float,
        val mistakeFrequency: Float
    )

    companion object {
        private const val START_FRACTION = RaceSession.START_FRACTION
    }
}
