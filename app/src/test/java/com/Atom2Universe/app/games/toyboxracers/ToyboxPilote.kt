package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pilote automatique des circuits classiques : il vise un point de la ligne de course
 * un peu devant lui, roule pied au plancher et ne freine qu'avant un virage que ses
 * pneus ne tiendraient pas. Il ne saute ni ne dérape : c'est un conducteur moyen et
 * propre, qui mesure ce que la piste impose, pas ce qu'un champion en tirerait.
 *
 * Il roule sur le vrai [ArcadeCar] : un vol, une chute ou un choc qu'il subit est
 * celui que subirait le joueur sur la même trajectoire.
 */
internal object ToyboxPilote {
    enum class State { ROAD, AIR, FALLEN }
    data class Point(val x: Float, val z: Float, val state: State)

    class Ride(
        val points: List<Point>,
        val lapSeconds: List<Float>,
        val flights: Int,
        val longestFlight: Float,
        val falls: Int,
        val fallenSeconds: Float,
        val hardestLanding: Float,
        val stuck: Boolean
    ) {
        fun summary() = "tours ${lapSeconds.joinToString(" / ") { "%.1f s".format(it) }}  vols $flights " +
            "(plus long ${"%.2f".format(longestFlight)} s)  chutes $falls (${"%.1f".format(fallenSeconds)} s)  " +
            "réception ${"%.1f".format(hardestLanding)}" + if (stuck) "  BLOQUÉ" else ""
    }

    fun drive(track: PrototypeTrack, laps: Int = 2, dt: Float = 1f / 60f): Ride {
        require(laps in 1..RaceSession.TOTAL_LAPS)
        val car = ArcadeCar(track)
        val race = RaceSession(track).apply {
            reset(car.distance)
            updateCountdown(4f, car.distance)
        }
        val points = ArrayList<Point>()
        val lapSeconds = ArrayList<Float>()
        var anchorSerial = car.rescueAnchorSerial
        var flights = 0
        var flightTime = 0f
        var longestFlight = 0f
        var falls = 0
        var fallen = false
        var fallenSeconds = 0f
        var hardestLanding = 0f
        var landingSerial = car.landingSerial
        var slowSeconds = 0f
        var stuck = false
        var time = 0f
        val limit = track.length / 4f * laps + 30f
        while (time < limit && lapSeconds.size < laps) {
            val position = car.worldPosition
            val lookAhead = 4f + car.speed * .45f
            val target = track.sampleAt(car.distance + lookAhead)
            val desired = atan2(target.position.x - position.x, target.position.z - position.z)
            val steering = (angle(desired - car.yawRadians) * 2.6f).coerceIn(-1f, 1f)
            // Vitesse tenable dans le virage le plus serré des prochains mètres.
            var tightest = Float.MAX_VALUE
            var d = 2f
            while (d < 10f + car.speed * 1.1f) {
                val a = track.sampleAt(car.distance + d - 2f)
                val b = track.sampleAt(car.distance + d + 2f)
                val turn = abs(angle(track.headingRadians(b) - track.headingRadians(a)))
                if (turn > 1e-4f) tightest = minOf(tightest, 4f / turn)
                d += 2f
            }
            val cornerSpeed = sqrt(19f * tightest)
            val braking = car.speed > cornerSpeed + 1f && !car.airborne
            val wasRescuing = car.rescuing
            car.update(dt, ArcadeCar.Input(steering, accelerating = !braking, braking = braking))
            if (!wasRescuing && car.rescuing) race.restoreRescuePoint(car.rescueAnchorDistance)
            if (wasRescuing && !car.rescuing) race.restoreRescuePoint(car.distance)
            race.updateRace(dt, car.distance, (car.groundedOnRoad || car.airborne) && !car.offRoad,
                emptyList(), recovering = wasRescuing || car.rescuing)
            if (anchorSerial != car.rescueAnchorSerial && !car.rescuing) {
                race.rememberRescuePoint()
                anchorSerial = car.rescueAnchorSerial
            }
            time += dt

            val here = track.project(car.worldPosition.x, car.worldPosition.y, car.worldPosition.z, car.distance)
            val roadY = here.sample.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
            val isFallen = !car.airborne && track.hasDeck(here.sample) && car.worldPosition.y < roadY - 1.2f
            if (isFallen && !fallen) falls++
            fallen = isFallen
            if (fallen) fallenSeconds += dt
            if (car.airborne) {
                flightTime += dt
            } else if (flightTime > 0f) {
                if (flightTime > .12f) flights++
                longestFlight = maxOf(longestFlight, flightTime)
                flightTime = 0f
            }
            if (car.landingSerial != landingSerial) {
                landingSerial = car.landingSerial
                hardestLanding = maxOf(hardestLanding, car.landingImpact)
            }
            slowSeconds = if (car.speed < 2f) slowSeconds + dt else 0f
            if (slowSeconds > 3f) { stuck = true; break }
            val completed = if (race.phase == RacePhase.FINISHED) RaceSession.TOTAL_LAPS else race.playerLap - 1
            while (lapSeconds.size > completed) lapSeconds.removeAt(lapSeconds.lastIndex)
            if (lapSeconds.size < completed) lapSeconds += race.lastLapSeconds
            points += Point(car.worldPosition.x, car.worldPosition.z, when {
                car.airborne -> State.AIR
                fallen -> State.FALLEN
                else -> State.ROAD
            })
        }
        return Ride(points, lapSeconds, flights, longestFlight, falls, fallenSeconds, hardestLanding, stuck)
    }

    private fun angle(value: Float): Float {
        var v = value
        while (v > PI) v -= (2 * PI).toFloat()
        while (v < -PI) v += (2 * PI).toFloat()
        return v
    }
}
