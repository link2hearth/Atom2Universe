package com.Atom2Universe.app.games.toyboxracers.driving

import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos

/** Modèle de conduite volontairement arcade, déterministe et à pas fixe. */
internal class ArcadeCar(private val track: PrototypeTrack) {

    data class Input(val steering: Float, val accelerating: Boolean, val braking: Boolean)

    var distance = track.length * 0.015f
        private set
    var lateralOffset = 0f
        private set
    var speed = 0f
        private set
    var headingOffset = 0f
        private set
    var yawRadians = 0f
        private set
    var worldPosition = Vec3(0f, PrototypeTrack.CAR_CLEARANCE, 0f)
        private set
    var airborne = false
        private set
    var offRoad = false
        private set
    var lap = 1
        private set
    var elapsedSeconds = 0f
        private set

    private var verticalVelocity = 0f
    private var airborneY = 0f
    private var worldX = 0f
    private var worldZ = 0f
    private var velocityX = 0f
    private var velocityZ = 0f
    var groundedOnRoad = true
        private set
    private var previousDistance = distance

    init {
        placeAtStart()
    }

    fun reset() {
        distance = track.length * 0.015f
        lateralOffset = 0f
        speed = 0f
        headingOffset = 0f
        yawRadians = 0f
        airborne = false
        offRoad = false
        lap = 1
        elapsedSeconds = 0f
        verticalVelocity = 0f
        velocityX = 0f
        velocityZ = 0f
        groundedOnRoad = true
        placeAtStart()
    }

    fun update(dt: Float, input: Input) {
        elapsedSeconds += dt
        val beforeProjection = track.project(worldX, worldPosition.y, worldZ, distance)
        previousDistance = distance
        distance = beforeProjection.sample.distance
        lateralOffset = beforeProjection.lateralOffset
        offRoad = abs(lateralOffset) > beforeProjection.sample.roadWidth * 0.5f

        val steering = input.steering.coerceIn(-1f, 1f)
        speed = hypot(velocityX, velocityZ)
        if (!airborne && speed > 0.35f && abs(steering) > 0.01f) {
            val steeringStrength = 0.45f + 1.30f * (speed / MAX_SPEED).coerceIn(0f, 1f)
            yawRadians += steering * steeringStrength * dt
        }

        val forwardX = sin(yawRadians)
        val forwardZ = cos(yawRadians)
        if (!airborne && input.accelerating) {
            velocityX += forwardX * ENGINE_ACCELERATION * dt
            velocityZ += forwardZ * ENGINE_ACCELERATION * dt
        }

        val forwardSpeed = velocityX * forwardX + velocityZ * forwardZ
        val rightX = forwardZ
        val rightZ = -forwardX
        var lateralSpeed = velocityX * rightX + velocityZ * rightZ
        val drifting = input.braking && abs(steering) > 0.1f && speed > 5f
        val grip = when {
            airborne -> 0f
            drifting -> DRIFT_GRIP
            else -> NORMAL_GRIP
        }
        lateralSpeed = approach(lateralSpeed, 0f, grip * dt)
        velocityX = forwardX * forwardSpeed + rightX * lateralSpeed
        velocityZ = forwardZ * forwardSpeed + rightZ * lateralSpeed
        headingOffset = atan2(lateralSpeed, max(0.1f, abs(forwardSpeed)))

        val deceleration = if (airborne) {
            0f
        } else {
            when {
                input.braking -> BRAKE_DECELERATION
                !input.accelerating -> ROLLING_DECELERATION
                else -> 0f
            } + if (offRoad) OFF_ROAD_DECELERATION else 0f
        }
        applyDeceleration(deceleration * dt)
        // Le hors-piste appartient au terrain de jeu : aucune vitesse plafond
        // artificielle ne force le joueur à retourner sur le ruban de route.
        limitSpeed(MAX_SPEED)

        val previousWorldX = worldX
        val previousWorldZ = worldZ
        worldX += velocityX * dt
        worldZ += velocityZ * dt
        resolveRoomWalls()
        resolveToyObstacles()

        val projection = track.project(worldX, airborneY, worldZ, distance)
        distance = projection.sample.distance
        lateralOffset = projection.lateralOffset
        offRoad = abs(lateralOffset) > projection.sample.roadWidth * 0.5f
        if (distance < previousDistance && previousDistance > track.length * 0.85f && distance < track.length * 0.15f) lap++

        val road = projection.sample
        val onRoad = abs(lateralOffset) <= road.roadWidth * 0.55f
        val hasRoadSurface = onRoad && !track.isJumpGap(distance)
        val floorY = track.groundHeightAt(worldX, worldZ) + PrototypeTrack.CAR_CLEARANCE
        val roadY = road.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE

        // Aucun lancement scripté : les roues perdent simplement leur support
        // au bord réel de la dalle. La vitesse verticale provient uniquement de
        // la pente et du mouvement actuel, puis la gravité prend le relais.
        if (!airborne && groundedOnRoad) {
            val remainsSupported = hasRoadSurface && abs(worldPosition.y - roadY) < MAX_ROAD_STEP
            if (!remainsSupported) {
                groundedOnRoad = false
                if (worldPosition.y > floorY + 0.5f) {
                    airborne = true
                    airborneY = worldPosition.y
                    verticalVelocity = slopeVelocity(beforeProjection.sample)
                } else {
                    airborneY = floorY
                    verticalVelocity = 0f
                }
            }
        }

        var landedOnRoadThisStep = false
        if (airborne) {
            val previousAirborneY = airborneY
            airborneY += verticalVelocity * dt
            verticalVelocity -= 9.81f * dt
            landedOnRoadThisStep = resolveAirborneRoadCollision(
                previousWorldX,
                previousWorldZ,
                previousAirborneY
            )
            if (
                airborne &&
                verticalVelocity <= 0f &&
                airborneY <= track.groundHeightAt(worldX, worldZ) + PrototypeTrack.CAR_CLEARANCE
            ) {
                airborne = false
                airborneY = track.groundHeightAt(worldX, worldZ) + PrototypeTrack.CAR_CLEARANCE
                verticalVelocity = 0f
                groundedOnRoad = false
                velocityX *= 0.80f
                velocityZ *= 0.80f
            }
        }

        if (!airborne && !landedOnRoadThisStep) {
            if (groundedOnRoad && hasRoadSurface) {
                airborneY = roadY
            } else {
                // `resolveGroundRoadCollision` cherche la dalle **physiquement** la plus
                // proche et pose lui-même l'altitude sur celle qu'il a trouvée. Il ne
                // faut donc rien réécrire derrière lui : `roadY` vient de l'autre
                // projection, celle de la progression, et les deux ne désignent pas la
                // même dalle au croisement. Une réaffectation traînait ici et faisait
                // exactement ça — la voiture arrêtée sous le pont, dont la progression
                // était restée sur la branche haute, se retrouvait téléportée sur le
                // tablier quatorze mètres plus haut.
                groundedOnRoad = false
                airborneY = floorY
                resolveGroundRoadCollision(previousWorldX, previousWorldZ)
            }
        }
        speed = hypot(velocityX, velocityZ)
        worldPosition = Vec3(worldX, airborneY, worldZ)
    }

    private fun placeAtStart() {
        val start = track.sampleAt(distance)
        worldX = start.position.x
        worldZ = start.position.z
        yawRadians = track.headingRadians(start)
        airborneY = start.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE
        worldPosition = Vec3(worldX, airborneY, worldZ)
        previousDistance = distance
    }

    private fun slopeVelocity(sample: PrototypeTrack.Sample): Float {
        val horizontalLength = hypot(sample.tangent.x, sample.tangent.z).coerceAtLeast(0.0001f)
        val tangentX = sample.tangent.x / horizontalLength
        val tangentZ = sample.tangent.z / horizontalLength
        val alongTrack = velocityX * tangentX + velocityZ * tangentZ
        return alongTrack * sample.tangent.y / horizontalLength
    }

    /** Collision complète contre le dessus, le dessous et les flancs de la dalle. */
    private fun resolveAirborneRoadCollision(previousX: Float, previousZ: Float, previousY: Float): Boolean {
        val collision = track.projectForCollision(worldX, (previousY + airborneY) * 0.5f, worldZ)
            ?: return false
        val halfWidth = collision.sample.roadWidth * 0.5f +
            PrototypeTrack.CURB_WIDTH + CAR_COLLISION_RADIUS
        if (abs(collision.lateralOffset) > halfWidth) return false

        val slabTop = collision.sample.position.y + PrototypeTrack.ROAD_SURFACE_LIFT
        val slabBottom = slabTop - PrototypeTrack.ROAD_THICKNESS
        val previousBottom = previousY - PrototypeTrack.CAR_CLEARANCE
        val currentBottom = airborneY - PrototypeTrack.CAR_CLEARANCE
        val previousTop = previousY + CAR_TOP_FROM_ORIGIN
        val currentTop = airborneY + CAR_TOP_FROM_ORIGIN

        if (airborneY <= previousY && previousBottom >= slabTop && currentBottom <= slabTop) {
            airborne = false
            airborneY = slabTop + PrototypeTrack.CAR_CLEARANCE
            verticalVelocity = 0f
            groundedOnRoad = true
            distance = collision.sample.distance
            lateralOffset = collision.lateralOffset
            offRoad = false
            velocityX *= 0.94f
            velocityZ *= 0.94f
            return true
        }

        if (airborneY >= previousY && previousTop <= slabBottom && currentTop >= slabBottom) {
            val belowSlabY = slabBottom - CAR_TOP_FROM_ORIGIN
            val floorY = track.groundHeightAt(worldX, worldZ) + PrototypeTrack.CAR_CLEARANCE
            if (belowSlabY >= floorY) {
                airborneY = belowSlabY
                verticalVelocity = -abs(verticalVelocity) * ROAD_RESTITUTION
            } else {
                blockAtPreviousHorizontalPosition(previousX, previousZ)
                airborneY = previousY
                verticalVelocity = -abs(verticalVelocity) * ROAD_RESTITUTION
            }
            return false
        }

        val overlapsSlab = currentTop > slabBottom && currentBottom < slabTop
        if (overlapsSlab) {
            // L'intersection ne vient pas d'un franchissement vertical : la
            // voiture est entrée dans un flanc ou une extrémité de la dalle.
            blockAtPreviousHorizontalPosition(previousX, previousZ)
            airborneY = previousY
        }
        return false
    }

    private fun resolveGroundRoadCollision(previousX: Float, previousZ: Float) {
        val collision = track.projectForCollision(worldX, airborneY, worldZ) ?: return
        val halfWidth = collision.sample.roadWidth * 0.5f +
            PrototypeTrack.CURB_WIDTH + CAR_COLLISION_RADIUS
        if (abs(collision.lateralOffset) > halfWidth) return

        val groundHeight = track.groundHeightAt(worldX, worldZ)
        val slabTop = collision.sample.position.y + PrototypeTrack.ROAD_SURFACE_LIFT
        val slabBottom = slabTop - PrototypeTrack.ROAD_THICKNESS
        if (slabTop - groundHeight in -0.05f..MAX_GROUND_STEP) {
            airborneY = slabTop + PrototypeTrack.CAR_CLEARANCE
            groundedOnRoad = true
            return
        }

        val carBottom = airborneY - PrototypeTrack.CAR_CLEARANCE
        val carTop = airborneY + CAR_TOP_FROM_ORIGIN
        if (carTop > slabBottom && carBottom < slabTop) {
            blockAtPreviousHorizontalPosition(previousX, previousZ)
        }
    }

    private fun blockAtPreviousHorizontalPosition(previousX: Float, previousZ: Float) {
        worldX = previousX
        worldZ = previousZ
        velocityX *= -ROAD_RESTITUTION
        velocityZ *= -ROAD_RESTITUTION
    }

    private fun applyDeceleration(amount: Float) {
        val current = hypot(velocityX, velocityZ)
        if (current <= 0.0001f) return
        val next = (current - amount).coerceAtLeast(0f)
        val scale = next / current
        velocityX *= scale
        velocityZ *= scale
    }

    private fun limitSpeed(limit: Float) {
        val current = hypot(velocityX, velocityZ)
        if (current <= limit || current <= 0.0001f) return
        val scale = limit / current
        velocityX *= scale
        velocityZ *= scale
    }

    private fun resolveRoomWalls() {
        val xLimit = PrototypeTrack.ROOM_HALF_WIDTH - CAR_COLLISION_RADIUS
        val zLimit = PrototypeTrack.ROOM_HALF_DEPTH - CAR_COLLISION_RADIUS
        if (worldX < -xLimit) {
            worldX = -xLimit
            velocityX = abs(velocityX) * WALL_RESTITUTION
        } else if (worldX > xLimit) {
            worldX = xLimit
            velocityX = -abs(velocityX) * WALL_RESTITUTION
        }
        if (worldZ < -zLimit) {
            worldZ = -zLimit
            velocityZ = abs(velocityZ) * WALL_RESTITUTION
        } else if (worldZ > zLimit) {
            worldZ = zLimit
            velocityZ = -abs(velocityZ) * WALL_RESTITUTION
        }
    }

    private fun resolveToyObstacles() {
        for (obstacle in track.toyObstacles) {
            if (worldPosition.y > obstacle.height + PrototypeTrack.CAR_CLEARANCE) continue
            val dx = worldX - obstacle.x
            val dz = worldZ - obstacle.z
            val minimumDistance = obstacle.radius + CAR_COLLISION_RADIUS
            val distanceSquared = dx * dx + dz * dz
            if (distanceSquared >= minimumDistance * minimumDistance) continue
            val distance = kotlin.math.sqrt(distanceSquared).coerceAtLeast(0.001f)
            val normalX = dx / distance
            val normalZ = dz / distance
            worldX = obstacle.x + normalX * minimumDistance
            worldZ = obstacle.z + normalZ * minimumDistance
            val normalSpeed = velocityX * normalX + velocityZ * normalZ
            if (normalSpeed < 0f) {
                velocityX -= (1f + TOY_RESTITUTION) * normalSpeed * normalX
                velocityZ -= (1f + TOY_RESTITUTION) * normalSpeed * normalZ
            }
        }
    }

    private fun approach(current: Float, target: Float, amount: Float): Float = when {
        current < target -> (current + amount).coerceAtMost(target)
        current > target -> (current - amount).coerceAtLeast(target)
        else -> current
    }

    companion object {
        private const val MAX_SPEED = 20f
        private const val ENGINE_ACCELERATION = 8.5f
        private const val BRAKE_DECELERATION = 13f
        private const val ROLLING_DECELERATION = 2.4f
        private const val OFF_ROAD_DECELERATION = 0.65f
        private const val NORMAL_GRIP = 16f
        private const val DRIFT_GRIP = 3.8f
        private const val CAR_COLLISION_RADIUS = 0.58f
        private const val WALL_RESTITUTION = 0.28f
        private const val TOY_RESTITUTION = 0.22f
        private const val ROAD_RESTITUTION = 0.12f
        private const val CAR_TOP_FROM_ORIGIN = 0.68f
        private const val MAX_ROAD_STEP = 0.75f
        private const val MAX_GROUND_STEP = 0.16f
    }
}
