package com.Atom2Universe.app.games.toyboxracers.ai

import com.Atom2Universe.app.games.toyboxracers.driving.RaceVehicle
import com.Atom2Universe.app.games.toyboxracers.driving.RacerTuning
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.*

/** Anticipation du freinage, dépassements et turbos gagnés dans les virages.
 * Aucun rattrapage artificiel en fonction de la position du joueur.
 */
internal class RivalCar(private val track: PrototypeTrack, val index: Int) : RaceVehicle {
    var distance = 0f
        private set
    var raceProgress = 0f
        private set
    var speed = 0f
        private set
    var finishSeconds = Float.POSITIVE_INFINITY
        private set
    override var worldPosition = Vec3(0f, 0f, 0f)
        private set
    override var yawRadians = 0f
        private set
    var drifting = false
        private set
    var turboLevel = 0
        private set
    var turboBoostSeconds = 0f
        private set

    private var forwardSpeed = 0f
    private var lateralSpeed = 0f
    private var forwardX = 0f
    private var forwardZ = 1f
    override val contactVelocityX: Float get() = forwardX * forwardSpeed + forwardZ * lateralSpeed
    override val contactVelocityZ: Float get() = forwardZ * forwardSpeed - forwardX * lateralSpeed
    private var elapsed = 0f
    private var laneOffset = 0f
    private var routeResidualX = 0f
    private var routeResidualZ = 0f
    private var earnedCharge = 0f
    private var straightSeconds = 0f
    private var boostDuration = 0f
    private var boostPeak = 1f
    private var passSeconds = 0f
    private var passLane = 0f
    private var trafficTargetSpeed = Float.POSITIVE_INFINITY
    private var trafficLane = 0f

    fun reset(difficulty: RaceDifficulty) {
        distance = track.wrapDistance(track.length * RaceSession.START_FRACTION - 2.3f - index * 2.15f)
        raceProgress = -2.3f - index * 2.15f
        speed = 0f; forwardSpeed = 0f; lateralSpeed = 0f
        elapsed = 0f; laneOffset = (index - 2) * if (difficulty == RaceDifficulty.RELAX) .48f else .40f
        finishSeconds = Float.POSITIVE_INFINITY
        earnedCharge = 0f; straightSeconds = 0f; turboBoostSeconds = 0f
        boostDuration = 0f; boostPeak = 1f; turboLevel = 0; drifting = false
        passSeconds = 0f
        routeResidualX = 0f; routeResidualZ = 0f
        updateTransform()
    }

    fun update(dt: Float, difficulty: RaceDifficulty, player: RaceVehicle? = null, rivals: List<RivalCar> = emptyList()) {
        if (!dt.isFinite() || dt <= 0f) return
        elapsed += dt
        val settling = exp(-6f * dt)
        routeResidualX *= settling; routeResidualZ *= settling
        val config = config(difficulty)
        val here = track.sampleAt(distance)
        val turn = angleDifference(track.headingRadians(track.sampleAt(distance + 1.5f)),
            track.headingRadians(track.sampleAt(distance - 1.5f))) / 3f
        // Préparer le rétrécissement avant d'y entrer, sans couper le bas-côté
        // lorsque le dépassement engagé sur une portion large se termine.
        val upcomingWidth = track.sampleAt(distance + 2f + speed * .45f).roadWidth
        val maxLane = (minOf(here.roadWidth, upcomingWidth) * .5f - .70f).coerceAtLeast(0f)
        val baseLane = ((index - 2) * .28f + turn.coerceIn(-.05f, .05f) * 10f).coerceIn(-maxLane, maxLane)
        chooseTrafficLane(player, rivals, baseLane, maxLane, config.braking, dt)

        // Chaque virage impose sa propre vitesse ; on freine seulement à la distance nécessaire.
        var target = RacerTuning.MAX_SPEED * config.pace * (1f - index * .004f)
        var cornerLimit = Float.POSITIVE_INFINITY
        var look = 0f
        while (look <= 6f + speed * 1.15f) {
            val a = track.sampleAt(distance + look - 1.5f)
            val b = track.sampleAt(distance + look + 1.5f)
            val curvature = abs(angleDifference(track.headingRadians(b), track.headingRadians(a))) / 3f
            if (curvature > .0001f) {
                val cornerSpeedSquared = config.cornerGrip / curvature
                val allowed = sqrt(cornerSpeedSquared + 2f * config.braking * (look - 1.5f).coerceAtLeast(0f))
                cornerLimit = minOf(cornerLimit, allowed)
            }
            look += 3f
        }
        val mistake = ((sin(elapsed * .47f + index * 1.37f) - .86f) / .14f).coerceIn(0f, 1f)
        target *= 1f - mistake * config.mistake
        updateTurbo(dt, turn, config)
        val multiplier = if (turboBoostSeconds > 0f)
            RacerTuning.boostMultiplier(boostPeak, 1f - turboBoostSeconds / boostDuration) else 1f
        // Le turbo aide en sortie et en courbe large ; les épingles gardent leur limite d'adhérence.
        target = minOf(target * multiplier,
            cornerLimit,
            if (abs(turn) > .001f) sqrt(config.cornerGrip / abs(turn)) else Float.POSITIVE_INFINITY,
            trafficTargetSpeed)
        val engineDrive = ((RacerTuning.MAX_SPEED - forwardSpeed) / 3f).coerceIn(0f, 1f)
        val turboDrive = ((RacerTuning.MAX_SPEED * multiplier - forwardSpeed) / 4f).coerceIn(0f, 1f)
        val motor = RacerTuning.ENGINE_ACCELERATION * config.acceleration * engineDrive +
            if (turboBoostSeconds > 0f) RacerTuning.TURBO_ACCELERATION * (multiplier - 1f) /
                (boostPeak - 1f).coerceAtLeast(.01f) * turboDrive else 0f
        forwardSpeed = approach(forwardSpeed, target,
            (if (forwardSpeed < target) motor else config.braking) * dt)
        if (turboBoostSeconds > 0f) turboBoostSeconds = (turboBoostSeconds - dt).coerceAtLeast(0f)

        val wantedLateralSpeed = ((trafficLane - laneOffset) * 2.8f).coerceIn(-3f, 3f)
        lateralSpeed = approach(lateralSpeed, wantedLateralSpeed, 9f * dt)
        laneOffset += lateralSpeed * dt
        if (abs(laneOffset) > maxLane) {
            laneOffset = laneOffset.coerceIn(-maxLane, maxLane)
            if (lateralSpeed * laneOffset > 0f || maxLane == 0f) lateralSpeed = 0f
        }
        val travelled = forwardSpeed * dt
        advanceProgress(travelled, dt)
        distance = track.wrapDistance(distance + travelled)
        speed = hypot(forwardSpeed, lateralSpeed)
        updateTransform()
    }

    private fun updateTurbo(dt: Float, turn: Float, config: Config) {
        // Un saut suspend la charge, comme pour le joueur.
        if (track.crossingAt(distance) != null) return
        val entering = abs(turn) > .015f && forwardSpeed > 9f
        val holding = abs(turn) > .009f && forwardSpeed > 7f
        if (entering || (drifting && holding)) {
            drifting = true; straightSeconds = 0f
            earnedCharge = (earnedCharge + dt * config.chargeSkill).coerceAtMost(RacerTuning.FULL_CHARGE_SECONDS)
            turboLevel = if (earnedCharge >= RacerTuning.FULL_CHARGE_SECONDS * .55f) 3
                else if (earnedCharge >= RacerTuning.FULL_CHARGE_SECONDS * .22f) 2 else 1
        } else if (drifting) {
            straightSeconds += dt
            if (straightSeconds >= .18f) {
                val duration = RacerTuning.boostDuration(earnedCharge)
                if (duration >= RacerTuning.MIN_BOOST_DURATION && config.chargeSkill > 0f) {
                    val earnedPeak = RacerTuning.boostPeak(earnedCharge)
                    boostPeak = maxOf(if (turboBoostSeconds > 0f) boostPeak else 1f, earnedPeak)
                    turboBoostSeconds = maxOf(turboBoostSeconds, duration)
                    boostDuration = turboBoostSeconds
                    forwardSpeed = minOf(forwardSpeed + (earnedPeak - 1f) * RacerTuning.TURBO_RELEASE_IMPULSE,
                        RacerTuning.MAX_SPEED * boostPeak)
                }
                drifting = false; turboLevel = 0; earnedCharge = 0f
            }
        }
    }

    private fun chooseTrafficLane(player: RaceVehicle?, rivals: List<RivalCar>, base: Float, maxLane: Float, braking: Float, dt: Float) {
        trafficTargetSpeed = Float.POSITIVE_INFINITY
        passSeconds = (passSeconds - dt).coerceAtLeast(0f)
        trafficLane = if (passSeconds > 0f) passLane.coerceIn(-maxLane, maxLane) else base
        var nearest: RaceVehicle? = null
        var nearestAhead = Float.POSITIVE_INFINITY
        val lookAhead = 3f + speed * .65f + speed * speed / (2f * braking)
        for (i in 0..rivals.size) {
            val other = if (i == 0) player else rivals[i - 1]
            if (other == null || other === this || !other.vehicleContactsEnabled || abs(other.worldPosition.y - worldPosition.y) > .6f) continue
            val dx = other.worldPosition.x - worldPosition.x; val dz = other.worldPosition.z - worldPosition.z
            val ahead = dx * forwardX + dz * forwardZ
            val sideways = dx * forwardZ - dz * forwardX
            if (ahead > 0f && ahead < lookAhead && abs(sideways) < 1.2f && ahead < nearestAhead) {
                nearest = other; nearestAhead = ahead
            }
        }
        val obstacle = nearest ?: return
        val obstacleLane = laneOffset + (obstacle.worldPosition.x - worldPosition.x) * forwardZ -
            (obstacle.worldPosition.z - worldPosition.z) * forwardX
        var bestLane = trafficLane
        var bestScore = Float.NEGATIVE_INFINITY
        for (side in -1..1 step 2) {
            val candidate = (obstacleLane + side * 1.4f).coerceIn(-maxLane, maxLane)
            if (abs(candidate - obstacleLane) < 1.18f) continue
            var score = -abs(candidate - laneOffset) * .3f
            var blocked = false
            for (i in 0..rivals.size) {
                val other = if (i == 0) player else rivals[i - 1]
                if (other == null || other === this || !other.vehicleContactsEnabled || abs(other.worldPosition.y - worldPosition.y) > .6f) continue
                val dx = other.worldPosition.x - worldPosition.x; val dz = other.worldPosition.z - worldPosition.z
                val ahead = dx * forwardX + dz * forwardZ
                val lane = laneOffset + dx * forwardZ - dz * forwardX
                if (ahead > -2f && ahead < 6f + speed * .3f && abs(lane - candidate) < 1.15f) blocked = true
            }
            if (!blocked && score > bestScore) { bestScore = score; bestLane = candidate }
        }
        if (bestScore.isFinite()) {
            passLane = bestLane; passSeconds = 1.1f; trafficLane = bestLane
        }
        // Le changement de file prend du temps. Freiner d'après la distance de
        // fermeture évite de percuter systématiquement une voiture arrêtée
        // avant d'avoir rejoint la voie choisie pour la dépasser.
        val obstacleSpeed = (obstacle.contactVelocityX * forwardX + obstacle.contactVelocityZ * forwardZ).coerceAtLeast(0f)
        val remainingSide = abs(trafficLane - laneOffset)
        val closingSpeed = (forwardSpeed - obstacleSpeed).coerceAtLeast(0f)
        val laneChangeSeconds = remainingSide / 3f + .25f
        if (!bestScore.isFinite() || nearestAhead < 1.8f + closingSpeed * laneChangeSeconds) {
            val brakingRoom = (nearestAhead - 1.8f).coerceAtLeast(0f)
            trafficTargetSpeed = minOf(
                sqrt(obstacleSpeed * obstacleSpeed + 2f * braking * brakingRoom),
                obstacleSpeed + brakingRoom * 2f
            )
        }
    }

    override fun applyVehicleContact(dx: Float, dz: Float, dvx: Float, dvz: Float, impact: Float) {
        val vx = contactVelocityX + dvx; val vz = contactVelocityZ + dvz
        val targetX = worldPosition.x + dx; val targetZ = worldPosition.z + dz
        val projection = track.project(targetX, worldPosition.y, targetZ, distance)
        val correction = angleDistance(projection.sample.distance - distance)
        distance = projection.sample.distance
        laneOffset = projection.lateralOffset
        advanceProgress(correction, 0f)
        val heading = track.headingRadians(projection.sample)
        forwardX = sin(heading); forwardZ = cos(heading)
        forwardSpeed = vx * forwardX + vz * forwardZ
        lateralSpeed = vx * forwardZ - vz * forwardX
        speed = hypot(forwardSpeed, lateralSpeed)
        // La projection prépare le prochain pas ; elle ne doit pas effacer la séparation
        // ni changer le cap au milieu de la résolution du même contact.
        routeResidualX = targetX - projection.sample.position.x - forwardZ * laneOffset
        routeResidualZ = targetZ - projection.sample.position.z + forwardX * laneOffset
        worldPosition = Vec3(targetX, worldPosition.y, targetZ)
    }

    private fun advanceProgress(travelled: Float, dt: Float) {
        val finish = RaceSession.TOTAL_LAPS * track.length
        if (finishSeconds.isInfinite() && raceProgress < finish && raceProgress + travelled >= finish) {
            finishSeconds = elapsed - dt + if (travelled > 0f) dt * (finish - raceProgress) / travelled else 0f
        }
        raceProgress += travelled
    }

    private fun updateTransform() {
        val sample = track.sampleAt(distance)
        val heading = track.headingRadians(sample)
        forwardX = sin(heading); forwardZ = cos(heading)
        var y = sample.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
        val crossing = track.crossingAt(distance)
        if (crossing != null) {
            val start = track.sampleAt(crossing.startDistance); val end = track.sampleAt(crossing.endDistance)
            val t = ((distance - crossing.startDistance) / (crossing.endDistance - crossing.startDistance)).coerceIn(0f, 1f)
            y = start.position.y + (end.position.y - start.position.y) * t +
                sin(t * PI.toFloat()) * 2.2f + PrototypeTrack.CAR_CLEARANCE
        }
        worldPosition = Vec3(sample.position.x + forwardZ * laneOffset + routeResidualX, y,
            sample.position.z - forwardX * laneOffset + routeResidualZ)
        yawRadians = heading + (if (drifting) -.17f * sign(lateralSpeed) else 0f) +
            atan2(lateralSpeed, abs(forwardSpeed).coerceAtLeast(1f)) * .22f
    }

    private fun config(difficulty: RaceDifficulty) = when (difficulty) {
        RaceDifficulty.RELAX -> Config(.80f, .78f, 12f, 22f, .16f, .35f)
        RaceDifficulty.ARCADE -> Config(.93f, .94f, 15f, 29f, .07f, 1f)
        RaceDifficulty.CHAMPION -> Config(1f, 1f, 17f, 34f, .025f, 1.65f)
    }

    private data class Config(val pace: Float, val acceleration: Float, val braking: Float,
        val cornerGrip: Float, val mistake: Float, val chargeSkill: Float)

    private fun angleDistance(delta: Float): Float = when {
        delta > track.length * .5f -> delta - track.length
        delta < -track.length * .5f -> delta + track.length
        else -> delta
    }
    private fun angleDifference(target: Float, current: Float): Float {
        var angle = target - current
        while (angle > PI) angle -= 2f * PI.toFloat()
        while (angle < -PI) angle += 2f * PI.toFloat()
        return angle
    }
    private fun approach(value: Float, target: Float, step: Float) =
        if (value < target) minOf(value + step, target) else maxOf(value - step, target)
}
