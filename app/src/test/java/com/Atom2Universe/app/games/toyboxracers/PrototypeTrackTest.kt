package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.CircuitCrossings
import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.CourseSurface
import com.Atom2Universe.app.games.toyboxracers.track.HouseGeometry
import com.Atom2Universe.app.games.toyboxracers.track.HousePlan
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
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
    fun figureEightCrossingMatchesTheJumpConstants() {
        val crossings = CircuitCrossings.crossingsFor(CircuitKind.FIGURE_EIGHT)
        assertEquals(1, crossings.size)
        assertEquals(PrototypeTrack.JUMP_START_FRACTION, crossings.single().gapStartFraction, 0f)
        assertEquals(PrototypeTrack.JUMP_END_FRACTION, crossings.single().gapEndFraction, 0f)

        val track = PrototypeTrack()
        assertEquals(track.jumpStartDistance, track.crossingAt(track.jumpStartDistance + 0.05f)!!.startDistance, 0.001f)
        assertEquals(track.jumpEndDistance, track.crossingAt(track.jumpStartDistance + 0.05f)!!.endDistance, 0.001f)
        assertEquals(null, track.crossingAt(track.jumpStartDistance - 5f))
    }

    @Test
    fun circuitsWithoutACrossingNeverReportAJumpGap() {
        for (kind in CircuitKind.entries) {
            if (kind == CircuitKind.FIGURE_EIGHT) continue
            val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, kind))
            assertTrue("$kind ne doit avoir aucun croisement pour cette itération",
                CircuitCrossings.crossingsFor(kind).isEmpty())
            assertFalse("$kind ne doit jamais signaler de vide", track.isJumpGap(track.length * 0.5f))
        }
    }

    @Test
    fun housePlanRoomToCircuitAssignmentIsStableForAFixedSeed() {
        val plan = HousePlan.generate(42L)
        assertEquals(
            listOf(
                CircuitKind.DOUBLE_BUMPS, CircuitKind.HIGH_GARDEN, CircuitKind.FIGURE_EIGHT,
                CircuitKind.SLALOM, CircuitKind.ROLLING_HILLS, CircuitKind.RIBBON_RALLY,
                CircuitKind.JUMP_PARADE, CircuitKind.SWITCHBACKS
            ),
            plan.rooms.map { it.circuit }
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
     *
     * **Le cap ne se vérifie que tant que rien n'a été percuté.** Depuis le rappel
     * d'alignement, une voiture déviée par un mur remet son nez dans l'axe de sa
     * nouvelle trajectoire : elle finit le long du mur au lieu de le longer en crabe.
     * Ce n'est pas l'accélérateur qui « braque », c'est le choc — on arrête donc de
     * surveiller le cap au premier impact, repéré à la vitesse qui chute d'un coup.
     */
    @Test
    fun acceleratorMovesCarButDoesNotSteerIt() {
        val track = PrototypeTrack()
        val car = ArcadeCar(track)
        val initialPosition = car.worldPosition
        val initialYaw = car.yawRadians

        var sawOffRoad = false
        var fastestOffRoad = 0f
        var impacted = false
        var previousSpeed = 0f
        var checkedSteps = 0
        repeat(60 * 10) {
            car.update(
                1f / 60f,
                ArcadeCar.Input(steering = 0f, accelerating = true, braking = false)
            )
            if (previousSpeed - car.speed > 0.5f) impacted = true
            if (!impacted) {
                checkedSteps++
                assertEquals(
                    "Plein gaz sans volant et sans choc ne doit jamais faire tourner la voiture",
                    initialYaw,
                    car.yawRadians,
                    0.0001f
                )
            }
            previousSpeed = car.speed
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
        assertTrue(
            "Le cap doit avoir été vérifié sur une vraie ligne droite : $checkedSteps pas",
            checkedSteps > 60
        )
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

    @Test
    fun ascendingLandingOnARampDoesNotBecomeAHeadOnCollision() {
        val track = PrototypeTrack(scene = SceneChoice(RoomKind.GARAGE, CircuitKind.WORKSHOP_EXPEDITION))
        val road = track.allSamples().first { track.hasDeck(it) && it.tangent.y > 0.3f }
        val car = ArcadeCar(track)
        val horizontal = kotlin.math.hypot(road.tangent.x, road.tangent.z)
        val y = road.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE + .02f
        car.setPrivateField("worldX", road.position.x)
        car.setPrivateField("worldZ", road.position.z)
        car.setPrivateField("airborneY", y)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(road.position.x, y, road.position.z))
        car.setPrivateField("distance", road.distance)
        car.setPrivateField("previousDistance", road.distance)
        car.setPrivateField("yawRadians", track.headingRadians(road))
        car.setPrivateField("velocityX", road.tangent.x / horizontal * 16f)
        car.setPrivateField("velocityZ", road.tangent.z / horizontal * 16f)
        car.setPrivateField("verticalVelocity", 1f)
        car.setPrivateField("airborne", true)
        car.setPrivateField("groundedOnRoad", false)

        car.update(1f / 60f, ArcadeCar.Input(0f, accelerating = false, braking = false))

        assertFalse("La pente doit recevoir la voiture qui monte encore", car.airborne)
        assertTrue("Une réception douce ne doit ni bloquer ni inverser la vitesse", car.speed > 15.5f)
        assertTrue(car.groundedOnRoad)
    }

    @Test
    fun climbingProjectionUsesHorizontalPositionRatherThanOldCarHeight() {
        val track = PrototypeTrack(scene = SceneChoice(RoomKind.GARAGE, CircuitKind.WORKSHOP_EXPEDITION))
        val road = track.allSamples().first { track.hasDeck(it) && it.tangent.y > .35f }
        val projected = track.project(road.position.x,
            road.position.y + PrototypeTrack.CAR_CLEARANCE - 1.5f, road.position.z, road.distance)
        assertEquals("L'ancienne hauteur ne doit pas reculer les roues sur la pente",
            road.distance, projected.sample.distance, .2f)
    }

    @Test
    fun organicCoursesUseRealFloorAndFurnitureInsteadOfHiddenDecks() {
        for (kind in listOf(CircuitKind.FURNITURE_TRAIL, CircuitKind.WORKSHOP_EXPEDITION)) {
            val track = PrototypeTrack(scene = SceneChoice(RoomKind.GARAGE, kind))
            val samples = track.allSamples()
            assertTrue(track.length > 700f)
            assertTrue(samples.count { track.surface(it) == CourseSurface.FLOOR } > samples.size / 2)
            val tabletop = samples.first { track.surface(it) == CourseSurface.FURNITURE && abs(it.position.x) < 10f }
            assertFalse(track.hasDeck(tabletop))
            assertEquals(12f, track.furnitureHeightAt(tabletop.position.x, tabletop.position.z, 12.01f), .001f)
            assertEquals("Le passage sous le meuble doit rester libre", 0f,
                track.furnitureHeightAt(0f, tabletop.position.z, 1f), .001f)
        }
    }

    @Test
    fun houseHasThreePhysicalLevelsAndAnOpenAtrium() {
        val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, CircuitKind.HOUSE_GROUND_FLOOR))
        assertEquals(0f, track.furnitureHeightAt(80f, 0f, 1f), .001f)
        assertEquals(26f, track.furnitureHeightAt(80f, 0f, 27f), .001f)
        assertEquals(52f, track.furnitureHeightAt(80f, 0f, 53f), .001f)
        assertEquals("Le vide de l'atrium ne doit pas être un plancher invisible",
            0f, track.furnitureHeightAt(0f, 15f, 60f), .001f)
        assertTrue(track.allSamples().all { track.hasDeck(it) })
        assertTrue(track.allSamples().any { it.position.y > 51f })
        assertTrue(track.allSamples().any { it.tangent.y > .1f })
        assertTrue(track.allSamples().any { it.tangent.y < -.1f })
    }

    @Test
    fun houseRouteDoesNotCrossWallsOrFurniture() {
        val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, CircuitKind.HOUSE_GROUND_FLOOR))
        for (sample in track.allSamples()) {
            val p = sample.position
            val bottom = p.y + PrototypeTrack.ROAD_SURFACE_LIFT
            val blocked = track.furnitureSolids.any {
                it.top > bottom + .1f && it.bottom < bottom + .92f &&
                    p.x > it.left - .6f && p.x < it.right + .6f &&
                    p.z > it.back - .6f && p.z < it.front + .6f
            }
            assertFalse("La trajectoire doit rester praticable à $p", blocked)
            assertTrue("La surface visible doit exister pour la collision à $p",
                track.decksAt(p.x, p.z).any { abs(it.sample.position.y - p.y) < .02f })
        }
    }

    @Test
    fun continuousHouseDescentsKeepWheelContactAtFullSpeed() {
        val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, CircuitKind.HOUSE_GROUND_FLOOR))
        val descents = track.allSamples().filter { it.tangent.y < -.1f }
        assertTrue(descents.isNotEmpty())
        for (sample in descents) {
            val car = ArcadeCar(track)
            val p = sample.position
            val y = p.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
            val horizontal = kotlin.math.hypot(sample.tangent.x, sample.tangent.z)
            car.setPrivateField("worldX", p.x)
            car.setPrivateField("worldZ", p.z)
            car.setPrivateField("airborneY", y)
            car.setPrivateField("worldPosition", PrototypeTrack.Vec3(p.x, y, p.z))
            car.setPrivateField("yawRadians", track.headingRadians(sample))
            car.setPrivateField("velocityX", sample.tangent.x / horizontal * 20f)
            car.setPrivateField("velocityZ", sample.tangent.z / horizontal * 20f)
            car.setPrivateField("distance", sample.distance)
            repeat(10) {
                car.update(1f / 60f, ArcadeCar.Input(0f, true, false))
                assertFalse("Une descente continue doit garder les roues au sol à $p", car.airborne)
            }
        }
    }

    @Test
    fun carBelowAnActualBridgeNeverSnapsToItsDeck() {
        val track = PrototypeTrack()
        val bridge = track.allSamples().first { it.fraction >= .4f }
        val car = ArcadeCar(track)
        car.setPrivateField("worldX", bridge.position.x)
        car.setPrivateField("worldZ", bridge.position.z)
        car.setPrivateField("airborneY", PrototypeTrack.CAR_CLEARANCE)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(bridge.position.x,
            PrototypeTrack.CAR_CLEARANCE, bridge.position.z))
        car.setPrivateField("distance", bridge.distance)
        repeat(120) {
            car.update(1f / 60f, ArcadeCar.Input(0f, false, false))
            assertTrue("Un pont au-dessus des roues n'est pas un appui", car.worldPosition.y < 1f)
        }
    }

    @Test
    fun fallingBetweenHouseLevelsLandsOnTheFloorBelow() {
        val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, CircuitKind.HOUSE_GROUND_FLOOR))
        val car = ArcadeCar(track)
        car.setPrivateField("worldX", 80f)
        car.setPrivateField("worldZ", 0f)
        car.setPrivateField("airborneY", 40f)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(80f, 40f, 0f))
        car.setPrivateField("airborne", true)
        repeat(120) {
            car.update(1f / 60f, ArcadeCar.Input(0f, false, false))
            assertTrue("Le plafond de l'étage ne doit pas attirer la voiture", car.worldPosition.y <= 40f)
        }
        assertFalse(car.airborne)
        assertEquals(26f + PrototypeTrack.CAR_CLEARANCE, car.worldPosition.y, .02f)
    }

    @Test
    fun airborneSteeringChangesDirectionWithoutCreatingSpeed() {
        val car = ArcadeCar(PrototypeTrack())
        car.setPrivateField("worldX", 0f)
        car.setPrivateField("worldZ", 20f)
        car.setPrivateField("airborneY", 25f)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(0f, 25f, 20f))
        car.setPrivateField("airborne", true)
        car.setPrivateField("velocityZ", 10f)
        car.setPrivateField("yawRadians", 0f)
        repeat(10) { car.update(1f / 60f, ArcadeCar.Input(1f, false, false)) }
        assertTrue(car.yawRadians > 0f)
        assertEquals(10f, car.speed, .001f)
        assertTrue("La chute doit être franche", car.worldPosition.y < 24.7f)
    }
    @Test
    fun drivingUpTheWorkshopRampNeverLosesSupportOrGetsStuckAtTheTop() {
        val track = PrototypeTrack(scene = SceneChoice(RoomKind.GARAGE, CircuitKind.WORKSHOP_EXPEDITION))
        val car = ArcadeCar(track)
        val samples = track.allSamples()
        val rampStart = samples.first { it.fraction >= 0.395f }
        val horizontal = kotlin.math.hypot(rampStart.tangent.x, rampStart.tangent.z)
        car.setPrivateField("worldX", rampStart.position.x)
        car.setPrivateField("worldZ", rampStart.position.z)
        val startY = rampStart.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
        car.setPrivateField("airborneY", startY)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(rampStart.position.x, startY, rampStart.position.z))
        car.setPrivateField("distance", rampStart.distance)
        car.setPrivateField("previousDistance", rampStart.distance)
        car.setPrivateField("yawRadians", track.headingRadians(rampStart))
        car.setPrivateField("velocityX", rampStart.tangent.x / horizontal * 14f)
        car.setPrivateField("velocityZ", rampStart.tangent.z / horizontal * 14f)
        car.setPrivateField("groundedOnRoad", true)

        var previousY = startY
        var sawUnexplainedDrop = false
        var sawAirborneDuringClimb = false
        var maxYReached = startY
        repeat(400) {
            car.update(1f / 60f, ArcadeCar.Input(0f, accelerating = true, braking = false))
            val y = car.worldPosition.y
            // Une vraie pente ne fait jamais chuter la voiture de plus d'un cran de
            // suspension en une image : au-delà, ce n'est plus une pente, c'est une chute.
            if (y < previousY - 0.3f) sawUnexplainedDrop = true
            if (car.airborne && y < 11.5f) sawAirborneDuringClimb = true
            maxYReached = maxOf(maxYReached, y)
            previousY = y
        }

        assertFalse("La voiture ne doit jamais chuter brutalement en montant une pente continue", sawUnexplainedDrop)
        assertFalse("La montée ne doit jamais être traitée comme un saut", sawAirborneDuringClimb)
        assertTrue("La voiture doit effectivement atteindre le plateau (~12)", maxYReached > 10f)
    }

    @Test
    fun drivingUnderTheHighBranchNeverGetsHoistedOntoItByAStaleDistance() {
        val track = PrototypeTrack()
        val car = ArcadeCar(track)
        val samples = track.allSamples()
        // La voiture est physiquement au sol, sous l'endroit où la branche haute
        // passe au-dessus (même x,z que le croisement), mais sa progression
        // mémorisée pointe par erreur vers cette branche haute : c'est exactement
        // le scénario qui a déjà causé une téléportation par le passé. supportAt()
        // (appui de chaque roue) doit ignorer cette continuité de progression et
        // ne comparer que les surfaces physiquement accessibles sous les roues.
        val lowPoint = samples.first()
        val highSample = samples.first { it.fraction >= 0.5f }
        car.setPrivateField("worldX", lowPoint.position.x)
        car.setPrivateField("worldZ", lowPoint.position.z)
        val floorY = lowPoint.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
        car.setPrivateField("airborneY", floorY)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(lowPoint.position.x, floorY, lowPoint.position.z))
        car.setPrivateField("distance", highSample.distance)
        car.setPrivateField("previousDistance", highSample.distance)
        car.setPrivateField("yawRadians", track.headingRadians(lowPoint))
        car.setPrivateField("groundedOnRoad", true)

        var maxY = floorY
        repeat(30) {
            car.update(1f / 60f, ArcadeCar.Input(0f, accelerating = false, braking = false))
            maxY = maxOf(maxY, car.worldPosition.y)
        }

        assertTrue(
            "La voiture ne doit jamais être hissée sur la branche haute alors qu'elle est en dessous : max observé $maxY",
            maxY < 3f
        )
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
