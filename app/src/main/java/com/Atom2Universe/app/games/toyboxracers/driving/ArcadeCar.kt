package com.Atom2Universe.app.games.toyboxracers.driving

import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import com.Atom2Universe.app.games.toyboxracers.track.CourseSurface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow

/** Modèle de conduite volontairement arcade, déterministe et à pas fixe. */
internal class ArcadeCar(
    private val track: PrototypeTrack,
    private val spec: VehicleSpec = VehicleSpec.ToyCar
) {

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
    var pitchRadians = 0f
        private set
    var rollRadians = 0f
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
    var drifting = false
        private set
    var turboCharge = 0f
        private set
    var turboLevel = 0
        private set
    var turboBoostSeconds = 0f
        private set
    var turboBoostTotalSeconds = 0f
        private set
    var turboReleaseSerial = 0
        private set
    var reversing = false
        private set

    private var verticalVelocity = 0f
    private var airborneY = 0f
    private var worldX = 0f
    private var worldZ = 0f
    private var velocityX = 0f
    private var velocityZ = 0f
    var groundedOnRoad = true
        private set
    private var groundedWheelCount = 4
    private var previousDistance = distance
    private var driftCandidateSeconds = 0f
    private var driftDurationSeconds = 0f
    private var driftGraceSeconds = 0f
    private var driftEffectiveSeconds = 0f
    private var turboBoostPeakMultiplier = 1f
    private var reverseHoldSeconds = 0f
    private var steeringSmoothed = 0f
    private var driftBlend = 0f

    init {
        placeAtStart()
    }

    fun reset() {
        distance = track.length * 0.015f
        lateralOffset = 0f
        speed = 0f
        headingOffset = 0f
        yawRadians = 0f
        pitchRadians = 0f
        rollRadians = 0f
        airborne = false
        offRoad = false
        lap = 1
        elapsedSeconds = 0f
        drifting = false
        turboCharge = 0f
        turboLevel = 0
        turboBoostSeconds = 0f
        turboBoostTotalSeconds = 0f
        driftCandidateSeconds = 0f
        driftDurationSeconds = 0f
        driftGraceSeconds = 0f
        driftEffectiveSeconds = 0f
        turboBoostPeakMultiplier = 1f
        reversing = false
        reverseHoldSeconds = 0f
        steeringSmoothed = 0f
        driftBlend = 0f
        verticalVelocity = 0f
        velocityX = 0f
        velocityZ = 0f
        groundedOnRoad = true
        groundedWheelCount = 4
        placeAtStart()
    }

    fun update(dt: Float, input: Input) {
        elapsedSeconds += dt
        val beforeProjection = track.project(worldX, worldPosition.y, worldZ, distance)
        previousDistance = distance
        distance = beforeProjection.sample.distance
        lateralOffset = beforeProjection.lateralOffset
        offRoad = abs(lateralOffset) > beforeProjection.sample.roadWidth * 0.5f

        // Les boutons ne donnent que -1, 0 ou +1 : sans lissage, la voiture
        // passe de tout droit à braquage complet en une image et le train
        // arrière décroche avant même que le joueur ait vu le virage.
        val steeringTarget = input.steering.coerceIn(-1f, 1f)
        val steeringRate = if (abs(steeringTarget) < abs(steeringSmoothed) ||
            steeringTarget * steeringSmoothed < 0f
        ) STEERING_RETURN_RATE else STEERING_RATE
        steeringSmoothed = approach(steeringSmoothed, steeringTarget, steeringRate * dt)
        val steering = steeringSmoothed
        speed = hypot(velocityX, velocityZ)
        val currentForwardX = sin(yawRadians)
        val currentForwardZ = cos(yawRadians)
        val currentForwardSpeed = velocityX * currentForwardX + velocityZ * currentForwardZ
        val currentLateralSpeed = velocityX * currentForwardZ - velocityZ * currentForwardX
        updateReverseState(dt, input)
        // Le passage en glisse s'installe en un tiers de seconde au lieu de
        // basculer d'une image à l'autre : l'arrière sort progressivement.
        driftBlend = approach(driftBlend, if (drifting) 1f else 0f, DRIFT_BLEND_RATE * dt)
        val wheelGrip = wheelGripScale()
        if (!airborne && speed > 0.35f && abs(steering) > 0.01f) {
            // Une trajectoire courbe demande une accélération latérale v × ω.
            // Faire pivoter le nez plus vite que ce que les pneus peuvent tenir
            // ne fait pas tourner la voiture : ça la met en travers. On plafonne
            // donc la rotation à ω = adhérence / vitesse, ce qui laisse un
            // braquage vif en épingle et le calme naturellement à pleine allure.
            val speedRatio = (speed / MAX_SPEED).coerceIn(0f, 1f)
            val desiredRate = STEER_RATE_SLOW + (STEER_RATE_FAST - STEER_RATE_SLOW) * speedRatio
            val corneringGrip = (CORNERING_GRIP +
                (DRIFT_CORNERING_GRIP - CORNERING_GRIP) * driftBlend) * spec.lateralGrip * wheelGrip
            val tractionRate = corneringGrip / max(speed, STEER_LIMIT_MIN_SPEED)
            val reverseSteering = if (currentForwardSpeed < -0.2f) -1f else 1f
            yawRadians += steering * reverseSteering * minOf(desiredRate, tractionRate) * dt
        }
        // Rappel d'alignement : le nez revient de lui-même dans l'axe de la
        // trajectoire. Sans lui, seule l'adhérence latérale corrige un travers,
        // et le tête-à-queue s'entretient tout seul une fois lancé. Au-delà de
        // l'angle de rattrapage, le rappel se durcit : c'est le filet qui évite
        // la toupie, borné pour ne jamais faire pivoter la voiture d'un coup.
        if (!airborne && speed > ALIGN_MIN_SPEED && currentForwardSpeed > 0.5f) {
            val slip = atan2(currentLateralSpeed, currentForwardSpeed)
            var alignRate = ALIGN_RATE + (ALIGN_RATE_DRIFT - ALIGN_RATE) * driftBlend
            if (abs(slip) > SPIN_CATCH_ANGLE) alignRate += SPIN_CATCH_RATE
            yawRadians += (slip * alignRate).coerceIn(-MAX_ALIGN_RATE, MAX_ALIGN_RATE) * dt
        }

        val forwardX = sin(yawRadians)
        val forwardZ = cos(yawRadians)
        if (!airborne && input.accelerating) {
            velocityX += forwardX * ENGINE_ACCELERATION * spec.longitudinalGrip * wheelGrip * dt
            velocityZ += forwardZ * ENGINE_ACCELERATION * spec.longitudinalGrip * wheelGrip * dt
        } else if (!airborne && reversing) {
            velocityX -= forwardX * REVERSE_ACCELERATION * spec.longitudinalGrip * wheelGrip * dt
            velocityZ -= forwardZ * REVERSE_ACCELERATION * spec.longitudinalGrip * wheelGrip * dt
        }

        var forwardSpeed = velocityX * forwardX + velocityZ * forwardZ
        val rightX = forwardZ
        val rightZ = -forwardX
        var lateralSpeed = velocityX * rightX + velocityZ * rightZ
        val slipAngle = abs(atan2(lateralSpeed, max(0.1f, abs(forwardSpeed))))
        val wasDrifting = drifting
        // Le Ruban Turbo est automatique : le joueur garde GAZ et conduit
        // normalement. Un virage assez marqué assouplit légèrement l'adhérence,
        // puis le redressement libère la charge sans combinaison de boutons.
        val driftBaseValid = !airborne && !input.braking && turboBoostSeconds <= 0f &&
            speed >= DRIFT_SPEED_MIN && forwardSpeed > speed * DRIFT_FORWARD_RATIO_MIN
        val driftRequested = driftBaseValid && abs(steering) >= DRIFT_STEERING_MIN
        val chargeableDrift = driftRequested && slipAngle in DRIFT_ANGLE_MIN..DRIFT_ANGLE_MAX
        if (!wasDrifting) {
            // La confirmation repose sur un vrai virage maintenu, pas sur une fenêtre
            // d'angle que la voiture peut traverser entre deux images. L'angle sert
            // ensuite à doser la charge et à refuser une perte de contrôle.
            driftCandidateSeconds = if (driftRequested) driftCandidateSeconds + dt else 0f
            drifting = driftCandidateSeconds >= DRIFT_CONFIRM_SECONDS
            if (drifting) {
                // Le seuil rend la détection visible, mais la réserve commence à
                // zéro : aucun petit boost fixe ne peut être spammé.
                driftEffectiveSeconds = 0f
                turboCharge = 0f
                turboLevel = 1
                driftGraceSeconds = 0f
            }
        } else {
            val canKeepDrifting = driftBaseValid && slipAngle <= DRIFT_CANCEL_ANGLE
            if (driftRequested && canKeepDrifting) {
                // Reprendre le virage pendant la fenêtre de grâce prolonge le même ruban.
                driftGraceSeconds = 0f
                drifting = true
            } else if (canKeepDrifting && driftGraceSeconds < DRIFT_EXIT_GRACE_SECONDS) {
                // Le joueur peut relâcher brièvement la direction pour éviter le décor
                // sans perdre immédiatement son ruban ni sa charge.
                driftGraceSeconds += dt
                drifting = true
            } else {
                drifting = false
            }
        }
        if (drifting) {
            driftDurationSeconds += dt
            if (driftRequested) {
                val angleQuality = ((slipAngle - DRIFT_ANGLE_MIN) /
                    (DRIFT_IDEAL_ANGLE - DRIFT_ANGLE_MIN)).coerceIn(0f, 1f)
                val speedQuality = ((speed - DRIFT_SPEED_MIN) /
                    (MAX_SPEED - DRIFT_SPEED_MIN)).coerceIn(0f, 1f)
                val controlQuality = if (chargeableDrift) {
                    0.55f + angleQuality * 0.30f + speedQuality * 0.15f
                } else {
                    // Un petit virage reste légèrement positif et ne s'auto-punit
                    // jamais, mais une belle glisse charge sensiblement plus vite.
                    0.42f + speedQuality * 0.10f
                }
                driftEffectiveSeconds += dt * controlQuality
                turboCharge = (driftEffectiveSeconds / FULL_CHARGE_SECONDS).coerceIn(0f, 1f)
                turboLevel = chargeLevel(turboCharge)
            }
        } else if (wasDrifting) {
            if (driftBaseValid && abs(steering) < DRIFT_STEERING_MIN) {
                releaseTurbo(forwardX, forwardZ)
            } else {
                // Freiner, décoller ou partir en tête-à-queue annule la charge :
                // seul un vrai redressement en fin de courbe mérite la relance.
                clearTurboCharge()
            }
            driftDurationSeconds = 0f
            driftGraceSeconds = 0f
            driftCandidateSeconds = 0f
        }
        // L'adhérence a une part fixe et une part proportionnelle au travers :
        // plus la voiture glisse, plus vite elle se recolle. Un taux fixe seul
        // mettait plusieurs secondes à effacer une grosse glissade, d'où la
        // sensation de patinage qui ne s'arrête jamais.
        val gripBase = when {
            airborne -> 0f
            driftRequested -> DRIFT_GRIP
            else -> NORMAL_GRIP
        }
        val gripGain = when {
            airborne -> 0f
            driftRequested -> DRIFT_GRIP_GAIN
            else -> NORMAL_GRIP_GAIN
        }
        // Lever le pied ou freiner rend de l'adhérence : c'est le geste naturel
        // pour rattraper un travers, il doit récompenser le joueur.
        val liftBonus = if (!airborne && (!input.accelerating || input.braking)) LIFT_GRIP_BONUS else 1f
        // Le plafond ne change rien en virage tenu — l'équilibre s'y établit
        // bien en dessous — mais il empêche un gros travers d'être effacé en
        // deux images, ce qui se verrait comme un claquement du châssis.
        val grip = ((gripBase + gripGain * abs(lateralSpeed)) *
            liftBonus * spec.lateralGrip * wheelGrip).coerceAtMost(MAX_GRIP)
        val speedBeforeGrip = hypot(forwardSpeed, lateralSpeed)
        lateralSpeed = approach(lateralSpeed, 0f, grip * dt)
        velocityX = forwardX * forwardSpeed + rightX * lateralSpeed
        velocityZ = forwardZ * forwardSpeed + rightZ * lateralSpeed
        if (driftRequested) {
            // Le ruban ne prélève aucune énergie cachée. Frein, collisions et
            // erreurs restent les seules causes de perte d'élan.
            val speedAfterGrip = hypot(velocityX, velocityZ)
            if (speedAfterGrip > 0.0001f) {
                val conservation = speedBeforeGrip / speedAfterGrip
                velocityX *= conservation
                velocityZ *= conservation
            }
        }
        headingOffset = atan2(lateralSpeed, max(0.1f, abs(forwardSpeed)))

        val deceleration = if (airborne) {
            0f
        } else {
            when {
                input.braking && !reversing -> BRAKE_DECELERATION
                !input.accelerating -> ROLLING_DECELERATION
                else -> 0f
            } + if (offRoad && !track.scene.circuit.usesFurnitureLayout) OFF_ROAD_DECELERATION else 0f
        }
        applyDeceleration(deceleration * dt)
        limitReverseSpeed(forwardX, forwardZ)
        // Le hors-piste appartient au terrain de jeu : aucune vitesse plafond
        // artificielle ne force le joueur à retourner sur le ruban de route.
        if (turboBoostSeconds > 0f) {
            val progress = if (turboBoostTotalSeconds > 0f) {
                1f - turboBoostSeconds / turboBoostTotalSeconds
            } else 1f
            val multiplier = boostMultiplier(progress.coerceIn(0f, 1f))
            forwardSpeed = velocityX * forwardX + velocityZ * forwardZ
            if (forwardSpeed > 0f) {
                val strength = ((multiplier - 1f) /
                    (turboBoostPeakMultiplier - 1f).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                velocityX += forwardX * TURBO_ACCELERATION * strength * dt
                velocityZ += forwardZ * TURBO_ACCELERATION * strength * dt
            }
            limitSpeed(MAX_SPEED * multiplier)
            turboBoostSeconds = (turboBoostSeconds - dt).coerceAtLeast(0f)
        } else {
            limitSpeed(MAX_SPEED)
        }

        val previousWorldX = worldX
        val previousWorldZ = worldZ
        worldX += velocityX * dt
        worldZ += velocityZ * dt
        // La maison n'a pas de rectangle englobant unique : ses murs (troués aux
        // portes) sont des furnitureSolids ordinaires, résolus par resolveFurnitureSides().
        if (!track.scene.circuit.usesHouseLayout) resolveRoomWalls()
        resolveToyObstacles()

        val projection = track.project(worldX, airborneY, worldZ, distance)
        distance = projection.sample.distance
        lateralOffset = projection.lateralOffset
        offRoad = abs(lateralOffset) > projection.sample.roadWidth * 0.5f
        if (distance < previousDistance && previousDistance > track.length * 0.85f && distance < track.length * 0.15f) lap++

        val wheelContacts = sampleWheelContacts()
        val groundedContacts = wheelContacts.filter { it.grounded }
        groundedWheelCount = groundedContacts.size
        groundedOnRoad = groundedContacts.any { it.road }
        val floorY = maxOf(track.groundHeightAt(worldX, worldZ),
            track.furnitureHeightAt(worldX, worldZ, worldPosition.y - PrototypeTrack.CAR_CLEARANCE + 0.02f)) +
            spec.rideHeight
        updateSuspensionPose(dt, wheelContacts, beforeProjection.sample)

        var landedOnRoadThisStep = groundedWheelCount > 0
        // Un meuble ne téléporte jamais la voiture sur son plateau. Il ne porte
        // que des roues déjà au-dessus ; quitter son bord déclenche une chute.
        if (!airborne && groundedWheelCount == 0 && worldPosition.y > floorY + 0.05f) {
            airborne = true
            airborneY = worldPosition.y
            verticalVelocity = slopeVelocity(beforeProjection.sample)
        }
        if (airborne) {
            val previousAirborneY = airborneY
            airborneY += verticalVelocity * dt - 0.5f * 9.81f * dt * dt
            verticalVelocity -= 9.81f * dt
            resolveFurnitureCeilings(previousAirborneY)
            landedOnRoadThisStep = resolveAirborneRoadCollision(
                previousWorldX,
                previousWorldZ,
                previousAirborneY
            )
            if (
                airborne &&
                verticalVelocity <= 0f &&
                airborneY <= floorY
            ) {
                val landingImpact = -verticalVelocity
                airborne = false
                airborneY = floorY
                verticalVelocity = 0f
                groundedOnRoad = false
                // Le parquet et les meubles reçoivent aussi les petits sauts
                // sans prélever arbitrairement 20 % de la vitesse à chaque contact.
                val retention = (1f - (landingImpact - 4f).coerceAtLeast(0f) * .012f).coerceAtLeast(.8f)
                velocityX *= retention
                velocityZ *= retention
            }
        }

        if (!airborne && !landedOnRoadThisStep) {
            if (!groundedOnRoad) {
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
                if (floorY <= track.groundHeightAt(worldX, worldZ) + PrototypeTrack.CAR_CLEARANCE + 0.02f) {
                    resolveGroundRoadCollision(previousWorldX, previousWorldZ)
                }
            }
        }
        // Résoudre les flancs avec la hauteur de cette image : l'ancienne
        // hauteur confondait l'arrivée sur un plateau avec un choc de face.
        resolveFurnitureSides()
        speed = hypot(velocityX, velocityZ)
        worldPosition = Vec3(worldX, airborneY, worldZ)
    }

    private fun placeAtStart() {
        val start = track.sampleAt(distance)
        worldX = start.position.x
        worldZ = start.position.z
        yawRadians = track.headingRadians(start)
        airborneY = start.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + spec.rideHeight
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

    private fun wheelGripScale(): Float {
        if (airborne) return 0f
        return (0.35f + groundedWheelCount * 0.1625f).coerceIn(0f, 1f)
    }

    private data class WheelContact(
        val localX: Float,
        val localZ: Float,
        val surfaceY: Float,
        val grounded: Boolean,
        val road: Boolean
    )

    private fun sampleWheelContacts(): List<WheelContact> {
        val forwardX = sin(yawRadians)
        val forwardZ = cos(yawRadians)
        val rightX = forwardZ
        val rightZ = -forwardX
        val halfTrack = spec.trackWidth * 0.5f
        val halfBase = spec.wheelBase * 0.5f
        return listOf(
            wheelContact(-halfTrack, halfBase, forwardX, forwardZ, rightX, rightZ),
            wheelContact(halfTrack, halfBase, forwardX, forwardZ, rightX, rightZ),
            wheelContact(-halfTrack, -halfBase, forwardX, forwardZ, rightX, rightZ),
            wheelContact(halfTrack, -halfBase, forwardX, forwardZ, rightX, rightZ)
        )
    }

    private fun wheelContact(
        localX: Float,
        localZ: Float,
        forwardX: Float,
        forwardZ: Float,
        rightX: Float,
        rightZ: Float
    ): WheelContact {
        val wheelX = worldX + rightX * localX + forwardX * localZ
        val wheelZ = worldZ + rightZ * localX + forwardZ * localZ
        val support = supportAt(wheelX, wheelZ)
        val suspensionExtension = airborneY - support.surfaceY - spec.rideHeight
        val grounded = suspensionExtension <= spec.suspensionTravel
        return WheelContact(localX, localZ, support.surfaceY, grounded, support.road)
    }

    private data class Support(val surfaceY: Float, val road: Boolean)

    /** Appui d'une roue : cherche la dalle physiquement la plus proche, jamais celle
     * que la progression du tour laisserait deviner. `project()` pondère fortement
     * la continuité de progression (utile pour le hors-piste/la surface globale),
     * au point qu'un écart de progression peut l'emporter sur dix unités de
     * différence de hauteur — exactement le mécanisme qui téléportait la voiture sur
     * une branche haute non pertinente en passant dessous. `projectForCollision()`
     * existe déjà pour ignorer cette continuité et ne comparer que la position réelle. */
    private fun supportAt(wheelX: Float, wheelZ: Float): Support {
        val collision = track.projectForCollision(wheelX, airborneY, wheelZ)
        val onDeck = collision != null &&
            abs(collision.lateralOffset) <= collision.sample.roadWidth * 0.5f + PrototypeTrack.CURB_WIDTH + spec.wheelRadius
        val roadSurfaceY = (collision?.sample?.position?.y ?: 0f) + PrototypeTrack.ROAD_SURFACE_LIFT
        val furnitureY = track.furnitureHeightAt(
            wheelX,
            wheelZ,
            airborneY - spec.rideHeight + spec.suspensionTravel + spec.wheelRadius
        )
        val realSurfaceY = maxOf(track.groundHeightAt(wheelX, wheelZ), furnitureY)
        return if (onDeck && roadSurfaceY >= realSurfaceY - 0.20f) {
            Support(roadSurfaceY, true)
        } else {
            Support(realSurfaceY, false)
        }
    }

    private fun updateSuspensionPose(
        dt: Float,
        contacts: List<WheelContact>,
        previousRoad: PrototypeTrack.Sample
    ) {
        val grounded = contacts.filter { it.grounded }
        if (grounded.isEmpty()) return

        val targetY = grounded.sumOf { (it.surfaceY + spec.rideHeight).toDouble() }.toFloat() / grounded.size
        val springError = targetY - airborneY
        verticalVelocity += (springError * spec.suspensionStiffness -
            verticalVelocity * spec.suspensionDamping) / spec.mass * dt
        val maxLift = (slopeVelocity(previousRoad).coerceAtLeast(0f) + 2.2f) * dt
        val nextY = (airborneY + verticalVelocity * dt).coerceAtMost(targetY + spec.suspensionTravel)
        airborneY = if (nextY < targetY - spec.suspensionTravel) {
            targetY - spec.suspensionTravel
        } else {
            minOf(nextY, airborneY + maxLift)
        }
        if (abs(airborneY - targetY) < 0.015f && abs(verticalVelocity) < 0.08f) {
            airborneY = targetY
            verticalVelocity = 0f
        }
        airborne = false

        val front = contacts.filter { it.localZ > 0f && it.grounded }
        val rear = contacts.filter { it.localZ < 0f && it.grounded }
        val left = contacts.filter { it.localX < 0f && it.grounded }
        val right = contacts.filter { it.localX > 0f && it.grounded }
        val targetPitch = if (front.isNotEmpty() && rear.isNotEmpty()) {
            val frontY = front.sumOf { it.surfaceY.toDouble() }.toFloat() / front.size
            val rearY = rear.sumOf { it.surfaceY.toDouble() }.toFloat() / rear.size
            kotlin.math.atan2(frontY - rearY, spec.wheelBase)
        } else 0f
        val targetRoll = if (left.isNotEmpty() && right.isNotEmpty()) {
            val leftY = left.sumOf { it.surfaceY.toDouble() }.toFloat() / left.size
            val rightY = right.sumOf { it.surfaceY.toDouble() }.toFloat() / right.size
            kotlin.math.atan2(leftY - rightY, spec.trackWidth)
        } else 0f
        pitchRadians += (targetPitch.coerceIn(-MAX_BODY_PITCH, MAX_BODY_PITCH) - pitchRadians) *
            SUSPENSION_POSE_BLEND
        rollRadians += (targetRoll.coerceIn(-0.42f, 0.42f) - rollRadians) * SUSPENSION_POSE_BLEND
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

        val tangent = collision.sample.tangent
        val horizontalSquared = (tangent.x * tangent.x + tangent.z * tangent.z).coerceAtLeast(0.0001f)
        val travelled = ((worldX - previousX) * tangent.x + (worldZ - previousZ) * tangent.z) / horizontalSquared
        val previousSurface = track.sampleAt(collision.sample.distance - travelled)
        val previousLateral = (previousX - previousSurface.position.x) * previousSurface.right.x +
            (previousZ - previousSurface.position.z) * previousSurface.right.z
        val wasAboveSameSurface = track.hasDeck(previousSurface) &&
            abs(previousLateral) <= previousSurface.roadWidth * 0.5f + CAR_COLLISION_RADIUS &&
            previousBottom >= previousSurface.position.y + PrototypeTrack.ROAD_SURFACE_LIFT - 0.025f

        // Réception relative au relief, même si la voiture monte encore : la
        // pente peut rattraper les roues. Un choc latéral reste un vrai choc.
        if (currentBottom <= slabTop &&
            ((wasAboveSameSurface && currentBottom - slabTop <= previousBottom -
                (previousSurface.position.y + PrototypeTrack.ROAD_SURFACE_LIFT)) ||
                (previousBottom >= slabTop && airborneY <= previousY))) {
            val impact = abs(verticalVelocity - slopeVelocity(collision.sample))
            airborne = false
            airborneY = slabTop + PrototypeTrack.CAR_CLEARANCE
            verticalVelocity = 0f
            groundedOnRoad = true
            distance = collision.sample.distance
            lateralOffset = collision.lateralOffset
            offRoad = false
            val retention = (1f - (impact - 4f).coerceAtLeast(0f) * 0.012f).coerceAtLeast(0.8f)
            velocityX *= retention
            velocityZ *= retention
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

    private fun releaseTurbo(forwardX: Float, forwardZ: Float) {
        val earnedSeconds = driftEffectiveSeconds
        val duration = boostDuration(earnedSeconds)
        if (duration < MIN_BOOST_DURATION) {
            clearTurboCharge()
            return
        }
        turboBoostPeakMultiplier = boostPeakMultiplier(earnedSeconds)
        val impulse = (turboBoostPeakMultiplier - 1f) * TURBO_RELEASE_IMPULSE
        velocityX += forwardX * impulse
        velocityZ += forwardZ * impulse
        turboBoostTotalSeconds = duration
        turboBoostSeconds = duration
        turboReleaseSerial++
        clearTurboCharge()
    }

    private fun clearTurboCharge() {
        turboCharge = 0f
        turboLevel = 0
        driftEffectiveSeconds = 0f
    }

    private fun updateReverseState(dt: Float, input: Input) {
        if (
            airborne || !input.braking || input.accelerating ||
            (!reversing && speed > REVERSE_ENGAGE_SPEED)
        ) {
            reversing = false
            reverseHoldSeconds = 0f
            return
        }
        if (!reversing) {
            reverseHoldSeconds += dt
            reversing = reverseHoldSeconds >= REVERSE_ENGAGE_DELAY
        }
    }

    private fun limitReverseSpeed(forwardX: Float, forwardZ: Float) {
        val forwardSpeed = velocityX * forwardX + velocityZ * forwardZ
        if (forwardSpeed >= -MAX_REVERSE_SPEED) return
        val excess = forwardSpeed + MAX_REVERSE_SPEED
        velocityX -= forwardX * excess
        velocityZ -= forwardZ * excess
    }

    private fun chargeLevel(charge: Float): Int = when {
        charge >= TURBO_LEVEL_THREE -> 3
        charge >= TURBO_LEVEL_TWO -> 2
        else -> 1
    }

    /** Convexe au départ, puis rendement décroissant près du plafond de cinq secondes. */
    private fun boostDuration(effectiveSeconds: Float): Float {
        if (effectiveSeconds <= 0f) return 0f
        val curved = MAX_BOOST_DURATION *
            (1f - exp(-((effectiveSeconds / BOOST_DURATION_SCALE).pow(BOOST_DURATION_POWER))))
        return (curved + effectiveSeconds * BOOST_DURATION_LINEAR_GAIN)
            .coerceAtMost(MAX_BOOST_DURATION)
    }

    private fun boostPeakMultiplier(effectiveSeconds: Float): Float =
        (MIN_BOOST_PEAK + BOOST_PEAK_RANGE *
            (1f - exp(-(effectiveSeconds / BOOST_PEAK_SCALE))))
            .coerceAtMost(MAX_BOOST_PEAK)

    /** Attaque marquée, poussée prolongée, puis retour doux à x1 sur le dernier quart. */
    private fun boostMultiplier(progress: Float): Float {
        val sustainMultiplier = 1f + (turboBoostPeakMultiplier - 1f) * BOOST_SUSTAIN_RATIO
        return if (progress < BOOST_TAIL_START) {
            val t = progress / BOOST_TAIL_START
            val eased = t * t * (3f - 2f * t)
            turboBoostPeakMultiplier +
                (sustainMultiplier - turboBoostPeakMultiplier) * eased
        } else {
            val t = (progress - BOOST_TAIL_START) / (1f - BOOST_TAIL_START)
            val eased = t * t * (3f - 2f * t)
            sustainMultiplier + (1f - sustainMultiplier) * eased
        }
    }

    private fun preserveCollisionRhythm(beforeSpeed: Float) {
        val current = hypot(velocityX, velocityZ)
        val minimum = beforeSpeed * COLLISION_SPEED_RETENTION
        if (current >= minimum || current <= 0.0001f) return
        val scale = minimum / current
        velocityX *= scale
        velocityZ *= scale
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
        val impactSpeed = hypot(velocityX, velocityZ)
        var collided = false
        val xLimit = PrototypeTrack.ROOM_HALF_WIDTH - CAR_COLLISION_RADIUS
        val zLimit = PrototypeTrack.ROOM_HALF_DEPTH - CAR_COLLISION_RADIUS
        if (worldX < -xLimit) {
            worldX = -xLimit
            velocityX = abs(velocityX) * WALL_RESTITUTION
            collided = true
        } else if (worldX > xLimit) {
            worldX = xLimit
            velocityX = -abs(velocityX) * WALL_RESTITUTION
            collided = true
        }
        if (worldZ < -zLimit) {
            worldZ = -zLimit
            velocityZ = abs(velocityZ) * WALL_RESTITUTION
            collided = true
        } else if (worldZ > zLimit) {
            worldZ = zLimit
            velocityZ = -abs(velocityZ) * WALL_RESTITUTION
            collided = true
        }
        if (collided) preserveCollisionRhythm(impactSpeed)
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
                val impactSpeed = hypot(velocityX, velocityZ)
                velocityX -= (1f + TOY_RESTITUTION) * normalSpeed * normalX
                velocityZ -= (1f + TOY_RESTITUTION) * normalSpeed * normalZ
                preserveCollisionRhythm(impactSpeed)
            }
        }
    }

    private fun resolveFurnitureSides() {
        val bottom = airborneY - PrototypeTrack.CAR_CLEARANCE
        val top = airborneY + CAR_TOP_FROM_ORIGIN
        for (box in track.furnitureSolids) {
            // La suspension à ressort met quelques images à rattraper une nouvelle
            // hauteur d'appui (rampe -> dessus d'un meuble) : juste après la
            // transition, le dessous mesuré reste encore un peu sous le plateau
            // réel. Une marge de 0,02 confondait ce retard normal avec un flanc,
            // ce qui éjectait la voiture pile à l'arrivée sur la table.
            if (bottom >= box.top - FURNITURE_TOP_SETTLING_MARGIN || top <= box.bottom) continue
            val left = box.left - CAR_COLLISION_RADIUS
            val right = box.right + CAR_COLLISION_RADIUS
            val back = box.back - CAR_COLLISION_RADIUS
            val front = box.front + CAR_COLLISION_RADIUS
            if (worldX <= left || worldX >= right || worldZ <= back || worldZ >= front) continue
            // Éjection par la face la plus proche, sans bloquer l'espace entre les pieds.
            val dx = minOf(worldX - left, right - worldX)
            val dz = minOf(worldZ - back, front - worldZ)
            var nx = 0f
            var nz = 0f
            if (dx < dz) {
                nx = if (worldX < box.x) -1f else 1f
                worldX = if (nx < 0f) left else right
            } else {
                nz = if (worldZ < box.z) -1f else 1f
                worldZ = if (nz < 0f) back else front
            }
            val normalSpeed = velocityX * nx + velocityZ * nz
            if (normalSpeed < 0f) {
                val impactSpeed = hypot(velocityX, velocityZ)
                velocityX -= (1f + TOY_RESTITUTION) * normalSpeed * nx
                velocityZ -= (1f + TOY_RESTITUTION) * normalSpeed * nz
                preserveCollisionRhythm(impactSpeed)
            }
        }
    }

    private fun resolveFurnitureCeilings(previousY: Float) {
        if (airborneY <= previousY) return
        var ceiling = Float.POSITIVE_INFINITY
        for (box in track.furnitureSolids) {
            if (worldX < box.left - CAR_COLLISION_RADIUS || worldX > box.right + CAR_COLLISION_RADIUS ||
                worldZ < box.back - CAR_COLLISION_RADIUS || worldZ > box.front + CAR_COLLISION_RADIUS) continue
            if (previousY + CAR_TOP_FROM_ORIGIN <= box.bottom && airborneY + CAR_TOP_FROM_ORIGIN >= box.bottom)
                ceiling = minOf(ceiling, box.bottom)
        }
        if (ceiling.isFinite()) {
            airborneY = ceiling - CAR_TOP_FROM_ORIGIN
            verticalVelocity = -abs(verticalVelocity) * TOY_RESTITUTION
        }
    }

    private fun approach(current: Float, target: Float, amount: Float): Float = when {
        current < target -> (current + amount).coerceAtMost(target)
        current > target -> (current - amount).coerceAtLeast(target)
        else -> current
    }

    companion object {
        private const val MAX_SPEED = 20f
        private const val TURBO_ACCELERATION = 11.0f
        private const val TURBO_RELEASE_IMPULSE = 4.0f
        private const val MIN_BOOST_DURATION = 0.06f
        private const val MAX_BOOST_DURATION = 5.0f
        private const val BOOST_DURATION_SCALE = 7.0f
        private const val BOOST_DURATION_POWER = 1.6f
        private const val BOOST_DURATION_LINEAR_GAIN = 0.08f
        private const val MIN_BOOST_PEAK = 1.12f
        private const val MAX_BOOST_PEAK = 1.80f
        private const val BOOST_PEAK_RANGE = 0.68f
        private const val BOOST_PEAK_SCALE = 4.0f
        private const val BOOST_SUSTAIN_RATIO = 0.38f
        private const val BOOST_TAIL_START = 0.75f
        private const val ENGINE_ACCELERATION = 8.5f
        private const val REVERSE_ACCELERATION = 6.0f
        private const val MAX_REVERSE_SPEED = 7.0f
        private const val REVERSE_ENGAGE_SPEED = 0.55f
        private const val REVERSE_ENGAGE_DELAY = 0.12f
        private const val BRAKE_DECELERATION = 13f
        private const val ROLLING_DECELERATION = 2.4f
        private const val OFF_ROAD_DECELERATION = 0.65f
        private const val STEERING_RATE = 5.5f
        private const val STEERING_RETURN_RATE = 9.0f
        private const val STEER_RATE_SLOW = 2.40f
        private const val STEER_RATE_FAST = 1.70f
        private const val STEER_LIMIT_MIN_SPEED = 4f
        private const val CORNERING_GRIP = 22f
        private const val DRIFT_CORNERING_GRIP = 34f
        private const val ALIGN_MIN_SPEED = 1.5f
        private const val ALIGN_RATE = 2.0f
        private const val ALIGN_RATE_DRIFT = 0.45f
        private const val SPIN_CATCH_ANGLE = 0.85f
        private const val SPIN_CATCH_RATE = 4.0f
        private const val MAX_ALIGN_RATE = 2.5f
        private const val NORMAL_GRIP = 16f
        private const val NORMAL_GRIP_GAIN = 6.5f
        private const val DRIFT_GRIP = 8.0f
        private const val DRIFT_GRIP_GAIN = 3.0f
        private const val LIFT_GRIP_BONUS = 1.45f
        private const val MAX_GRIP = 42f
        private const val DRIFT_BLEND_RATE = 3.0f
        private const val DRIFT_SPEED_MIN = 7f
        private const val DRIFT_STEERING_MIN = 0.28f
        private const val DRIFT_FORWARD_RATIO_MIN = 0.18f
        private const val DRIFT_ANGLE_MIN = 0.035f
        private const val DRIFT_IDEAL_ANGLE = 0.42f
        private const val DRIFT_ANGLE_MAX = 0.95f
        private const val DRIFT_CANCEL_ANGLE = 1.25f
        private const val DRIFT_CONFIRM_SECONDS = 0.25f
        private const val DRIFT_EXIT_GRACE_SECONDS = 0.52f
        private const val FULL_CHARGE_SECONDS = 10f
        private const val TURBO_LEVEL_TWO = 0.25f
        private const val TURBO_LEVEL_THREE = 0.60f
        private const val COLLISION_SPEED_RETENTION = 0.58f
        private const val CAR_COLLISION_RADIUS = 0.58f
        private const val WALL_RESTITUTION = 0.28f
        private const val TOY_RESTITUTION = 0.22f
        private const val ROAD_RESTITUTION = 0.12f
        private const val CAR_TOP_FROM_ORIGIN = 0.68f
        private const val MAX_ROAD_STEP = 0.75f
        private const val MAX_GROUND_STEP = 0.16f
        private const val MAX_BODY_PITCH = 0.76f
        private const val SUSPENSION_POSE_BLEND = 0.18f
        private const val FURNITURE_TOP_SETTLING_MARGIN = 0.3f
    }
}
