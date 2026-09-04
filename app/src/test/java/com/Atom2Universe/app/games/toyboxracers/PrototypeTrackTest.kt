package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class PrototypeTrackTest {

    @Test
    fun trackContainsClimbHighSectionAndRealGap() {
        val track = PrototypeTrack()
        val samples = track.allSamples()

        assertTrue("La piste doit conserver sa très grande échelle", track.length > 650f)
        assertTrue(samples.any { it.position.y >= PrototypeTrack.HIGH_LEVEL })
        assertTrue(track.jumpEndDistance > track.jumpStartDistance)
        assertTrue(track.isJumpGap((track.jumpStartDistance + track.jumpEndDistance) * 0.5f))
        assertFalse(track.isJumpGap(track.jumpStartDistance - 1f))

        val takeoffHeading = track.headingRadians(track.sampleAt(track.jumpStartDistance + 0.2f))
        val landingHeading = track.headingRadians(track.sampleAt(track.jumpEndDistance - 0.2f))
        assertTrue(
            "Le saut doit rester sur une ligne droite",
            abs(angleDifference(landingHeading, takeoffHeading)) < 0.08f
        )

        val lowerCrossing = samples.first().position
        val upperCrossing = samples.first { it.fraction >= 0.5f }.position
        assertEquals(lowerCrossing.x, upperCrossing.x, 1f)
        assertEquals(lowerCrossing.z, upperCrossing.z, 1f)
        assertTrue(
            "La seconde branche doit franchir le centre en hauteur",
            upperCrossing.y - lowerCrossing.y > 10f
        )
    }

    @Test
    fun carDoesNotMoveOrTurnWithoutPlayerInput() {
        val track = PrototypeTrack()
        val car = ArcadeCar(track)
        val initialPosition = car.worldPosition
        val initialYaw = car.yawRadians

        repeat(60 * 5) {
            car.update(
                1f / 60f,
                ArcadeCar.Input(steering = 0f, accelerating = false, braking = false)
            )
        }

        assertEquals(0f, car.speed, 0.0001f)
        assertEquals(initialYaw, car.yawRadians, 0.0001f)
        assertEquals(initialPosition.x, car.worldPosition.x, 0.0001f)
        assertEquals(initialPosition.z, car.worldPosition.z, 0.0001f)
    }

    /**
     * Le hors-piste ne doit pas brider la voiture.
     *
     * **On mesure la vitesse pendant qu'elle est hors piste, pas au bout des dix
     * secondes.** Sans volant, la voiture roule tout droit : à vingt mètres par seconde
     * elle traverse la chambre en cinq secondes et finit plaquée contre un mur, où sa
     * vitesse tombe évidemment à rien. Lire le compteur à l'arrivée revenait donc à
     * tester le mur, pas le hors-piste — et le test échouait sur une voiture qui avait
     * pourtant atteint sa vitesse maximale dès la troisième seconde.
     */
    @Test
    fun acceleratorMovesCarButDoesNotSteerIt() {
        val track = PrototypeTrack()
        val car = ArcadeCar(track)
        val initialPosition = car.worldPosition
        val initialYaw = car.yawRadians

        var sawOffRoad = false
        var fastestOffRoad = 0f
        repeat(60 * 10) {
            car.update(
                1f / 60f,
                ArcadeCar.Input(steering = 0f, accelerating = true, braking = false)
            )
            if (car.offRoad) {
                sawOffRoad = true
                fastestOffRoad = kotlin.math.max(fastestOffRoad, car.speed)
            }
        }

        assertTrue("La voiture doit pouvoir rouler librement hors piste", sawOffRoad)
        assertTrue(
            "Le hors-piste ne doit pas imposer une vitesse très basse : $fastestOffRoad m/s",
            fastestOffRoad > 18f
        )
        assertEquals(initialYaw, car.yawRadians, 0.0001f)
        assertTrue(
            car.worldPosition.x != initialPosition.x || car.worldPosition.z != initialPosition.z
        )
    }

    @Test
    fun jumpStartsByLeavingTheRampWhileReverseLandingStaysOnTheFloor() {
        val track = PrototypeTrack()
        val forwardCar = ArcadeCar(track)
        val takeoff = track.sampleAt(track.jumpStartDistance - 1f)
        val takeoffHorizontalLength = kotlin.math.hypot(takeoff.tangent.x, takeoff.tangent.z)
        forwardCar.setPrivateField("worldX", takeoff.position.x)
        forwardCar.setPrivateField("worldZ", takeoff.position.z)
        forwardCar.setPrivateField(
            "airborneY",
            takeoff.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
        )
        forwardCar.setPrivateField(
            "worldPosition",
            PrototypeTrack.Vec3(
                takeoff.position.x,
                takeoff.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE,
                takeoff.position.z
            )
        )
        forwardCar.setPrivateField("distance", takeoff.distance)
        forwardCar.setPrivateField("previousDistance", takeoff.distance)
        forwardCar.setPrivateField("velocityX", takeoff.tangent.x / takeoffHorizontalLength * 16f)
        forwardCar.setPrivateField("velocityZ", takeoff.tangent.z / takeoffHorizontalLength * 16f)
        repeat(30) {
            forwardCar.update(1f / 60f, ArcadeCar.Input(0f, accelerating = false, braking = false))
        }
        assertTrue("Quitter physiquement le bord du tremplin doit produire le saut", forwardCar.airborne)

        val reverseCar = ArcadeCar(track)
        val landing = track.sampleAt(track.jumpEndDistance + 0.8f)
        val landingHorizontalLength = kotlin.math.hypot(landing.tangent.x, landing.tangent.z)
        val landingY = landing.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
        reverseCar.setPrivateField("worldX", landing.position.x)
        reverseCar.setPrivateField("worldZ", landing.position.z)
        reverseCar.setPrivateField("airborneY", landingY)
        reverseCar.setPrivateField("worldPosition", PrototypeTrack.Vec3(landing.position.x, landingY, landing.position.z))
        reverseCar.setPrivateField("distance", landing.distance)
        reverseCar.setPrivateField("previousDistance", landing.distance)
        reverseCar.setPrivateField("velocityX", -landing.tangent.x / landingHorizontalLength * 12f)
        reverseCar.setPrivateField("velocityZ", -landing.tangent.z / landingHorizontalLength * 12f)
        repeat(30) {
            reverseCar.update(1f / 60f, ArcadeCar.Input(0f, accelerating = false, braking = false))
            assertFalse("Prendre la réception à contresens ne doit créer aucun saut", reverseCar.airborne)
        }
        assertTrue(reverseCar.worldPosition.y < 1f)
    }

    @Test
    fun elevatedRoadBlocksTheCarFromBelow() {
        val track = PrototypeTrack()
        val car = ArcadeCar(track)
        val bridge = track.allSamples().first { it.fraction >= 0.40f }
        val slabBottom = bridge.position.y + PrototypeTrack.ROAD_SURFACE_LIFT - PrototypeTrack.ROAD_THICKNESS
        val startY = slabBottom - 0.68f - 0.04f
        car.setPrivateField("worldX", bridge.position.x)
        car.setPrivateField("worldZ", bridge.position.z)
        car.setPrivateField("airborneY", startY)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(bridge.position.x, startY, bridge.position.z))
        car.setPrivateField("distance", bridge.distance)
        car.setPrivateField("previousDistance", bridge.distance)
        car.setPrivateField("airborne", true)
        car.setPrivateField("groundedOnRoad", false)
        car.setPrivateField("verticalVelocity", 5f)

        car.update(1f / 60f, ArcadeCar.Input(0f, accelerating = false, braking = false))

        assertTrue("Le toit de la voiture ne doit pas traverser le dessous de la dalle", car.worldPosition.y + 0.68f <= slabBottom + 0.01f)
    }

    @Test
    fun roomIsFlatToysAvoidTheTrackAndWallsKeepTheCarInside() {
        val track = PrototypeTrack()
        assertEquals(0f, track.groundHeightAt(-100f, -60f), 0f)
        assertEquals(0f, track.groundHeightAt(45f, 30f), 0f)

        for (toy in track.toyObstacles) {
            val nearestRoad = track.allSamples().minOf { sample ->
                kotlin.math.hypot(toy.x - sample.position.x, toy.z - sample.position.z)
            }
            assertTrue(
                "Chaque jouet doit conserver une large allée autour de la piste",
                nearestRoad > toy.radius + 10f
            )
        }

        val car = ArcadeCar(track)
        repeat(60 * 20) {
            car.update(1f / 60f, ArcadeCar.Input(steering = 0f, accelerating = true, braking = false))
        }
        assertTrue(abs(car.worldPosition.x) < PrototypeTrack.ROOM_HALF_WIDTH)
        assertTrue(abs(car.worldPosition.z) < PrototypeTrack.ROOM_HALF_DEPTH)
    }

    @Test
    fun carUnderTheHighBranchStaysNearTheFloor() {
        val track = PrototypeTrack()
        val car = ArcadeCar(track)
        val floorY = PrototypeTrack.CAR_CLEARANCE
        val upperBranchDistance = track.length * 0.5f
        car.setPrivateField("worldX", 0f)
        car.setPrivateField("worldZ", 0f)
        car.setPrivateField("airborneY", floorY)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(0f, floorY, 0f))
        car.setPrivateField("distance", upperBranchDistance)
        car.setPrivateField("previousDistance", upperBranchDistance)
        car.setPrivateField("groundedOnRoad", true)

        car.update(1f / 60f, ArcadeCar.Input(0f, accelerating = false, braking = false))

        assertTrue("Passer sous le pont ne doit jamais téléporter la voiture dessus", car.worldPosition.y < 1f)
    }

    private fun angleDifference(target: Float, current: Float): Float {
        var value = target - current
        while (value > PI) value -= (2.0 * PI).toFloat()
        while (value < -PI) value += (2.0 * PI).toFloat()
        return value
    }

    private fun ArcadeCar.setPrivateField(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }
}
