package com.Atom2Universe.app.games.toyboxracers.driving

import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolume
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolumeKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxTrackSection
import com.Atom2Universe.app.games.toyboxracers.editor.TrackStyle
import com.Atom2Universe.app.games.toyboxracers.editor.VolumePoint
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import com.Atom2Universe.app.games.toyboxracers.track.CourseSurface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
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

    /** Commandes d'une image.
     *
     * `steering` accepte tout le continu entre -1 et 1 : les boutons tactiles
     * n'envoient que les extrêmes, le stick d'une manette envoie le reste.
     * `throttle` et `brakeAmount` font la même chose pour les gâchettes ; ils
     * valent 1 quand seuls les booléens sont renseignés, pour que le tactile
     * garde exactement son comportement d'avant.
     * `hopping` est le bouton saut/dérapage tenu, à la façon des jeux de kart.
     */
    data class Input(
        val steering: Float,
        val accelerating: Boolean,
        val braking: Boolean,
        val hopping: Boolean = false,
        val throttle: Float = if (accelerating) 1f else 0f,
        val brakeAmount: Float = if (braking) 1f else 0f
    )

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
    /** Vrai pendant le petit saut déclenché par le bouton, pas pendant une chute. */
    var hopping = false
        private set
    /** Front montant du saut : le rendu et le son n'ont pas à deviner l'instant. */
    var hopSerial = 0
        private set
    /** Front montant d'une réception, avec sa violence, pour l'écrasement visuel. */
    var landingSerial = 0
        private set
    var landingImpact = 0f
        private set
    /** Côté du dérapage tenu : -1, 0 ou +1. Zéro hors dérapage au bouton. */
    var driftDirection = 0
        private set
    /** Adhérence moyenne du sol sous les roues portantes (1 = bitume). */
    var surfaceGrip = 1f
        private set
    /** Braquage lissé du joueur : les roues avant s'en servent pour s'orienter. */
    var steeringVisual = 0f
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
    private var hopHeld = false
    private var hopCooldownSeconds = 0f
    private var manualDrift = false
    private var releaseRequested = false
    private var surfaceDrag = 0f
    private var onBoostPad = false
    private var editorWorld = ToyboxWorld(volumes = emptyList(), trackSections = emptyList())
    private var editorTrackColliders = emptyList<EditorTrackCollider>()
    private var editorHasBarriers = false
    private var editorTrackIndex = EditorTrackIndex(emptyList())
    private var editorVolumeIndex = EditorVolumeIndex(emptyList())
    /** Vrai quand un monde d'éditeur (pas un circuit classique) est conduit :
     * la piste procédurale (route, murs de pièce, meubles, jouets) est alors
     * totalement ignorée, seul `editorWorld` fait office de sol/obstacles. */
    var sandboxMode = false

    init {
        placeAtStart()
    }

    fun setEditorWorld(world: ToyboxWorld) {
        if (editorWorld === world) return
        if (editorWorld.trackSections !== world.trackSections) {
            editorHasBarriers = world.trackSections.any { it.barriers != 0 }
            editorTrackColliders = world.trackSections.flatMap { it.meshSections }.map(::EditorTrackCollider)
            editorTrackIndex = EditorTrackIndex(editorTrackColliders)
        }
        if (editorWorld.volumes !== world.volumes || editorWorld.decorations !== world.decorations) {
            val decorVolumes = world.decorations.flatMap { decor ->
                val placement = decor.placement() ?: return@flatMap emptyList()
                placement.model.parts.filter { it.solid }.map { part ->
                    ToyboxVolume(decor.id, ToyboxVolumeKind.FURNITURE,
                        placement.x + placement.rotatedX(part.x, part.z) * placement.scale,
                        placement.y + part.y * placement.scale,
                        placement.z + placement.rotatedZ(part.x, part.z) * placement.scale,
                        part.width * placement.scale, part.height * placement.scale, part.depth * placement.scale,
                        yawDegrees = placement.yawDegrees)
                }
            }
            editorVolumeIndex = EditorVolumeIndex(world.volumes + decorVolumes)
        }
        editorWorld = world
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
        steeringVisual = 0f
        driftBlend = 0f
        hopping = false
        hopHeld = false
        hopCooldownSeconds = 0f
        manualDrift = false
        releaseRequested = false
        driftDirection = 0
        landingImpact = 0f
        surfaceGrip = 1f
        surfaceDrag = 0f
        onBoostPad = false
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
        steeringVisual = steeringSmoothed
        val steering = steeringSmoothed
        // Saut puis dérapage tenu : la mécanique de kart. Les deux se règlent
        // avant l'orientation, car ils décident du braquage réellement appliqué.
        updateHop(dt, input)
        updateManualDrift(dt, input, steering)
        // Dans un dérapage tenu, le stick ne choisit plus le côté : il resserre
        // ou élargit la courbe. C'est ce braquage-là qui fait tourner la voiture.
        val steerCommand = if (manualDrift) driftSteering(steering) else steering
        if (airborne) {
            // Correction arcade modérée de la trajectoire, sans ajouter de vitesse.
            val turn = steering * .55f * dt
            val vx = velocityX
            velocityX = vx * cos(turn) + velocityZ * sin(turn)
            velocityZ = velocityZ * cos(turn) - vx * sin(turn)
            yawRadians += turn
        }
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
        if (!airborne && speed > 0.35f && abs(steerCommand) > 0.01f) {
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
            yawRadians += steerCommand * reverseSteering * minOf(desiredRate, tractionRate) * dt
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
        // Une gâchette à moitié enfoncée n'envoie qu'une moitié de couple : sur
        // une surface peu adhérente, c'est le seul moyen de repartir sans patiner.
        val throttle = input.throttle.coerceIn(0f, 1f)
        val brakeAmount = input.brakeAmount.coerceIn(0f, 1f)
        if (!airborne && input.accelerating) {
            velocityX += forwardX * ENGINE_ACCELERATION * throttle * spec.longitudinalGrip * wheelGrip * dt
            velocityZ += forwardZ * ENGINE_ACCELERATION * throttle * spec.longitudinalGrip * wheelGrip * dt
        } else if (!airborne && reversing) {
            velocityX -= forwardX * REVERSE_ACCELERATION * brakeAmount * spec.longitudinalGrip * wheelGrip * dt
            velocityZ -= forwardZ * REVERSE_ACCELERATION * brakeAmount * spec.longitudinalGrip * wheelGrip * dt
        }

        var forwardSpeed = velocityX * forwardX + velocityZ * forwardZ
        val rightX = forwardZ
        val rightZ = -forwardX
        var lateralSpeed = velocityX * rightX + velocityZ * rightZ
        val slipAngle = abs(atan2(lateralSpeed, max(0.1f, abs(forwardSpeed))))
        // Le relâchement du bouton a été constaté avant l'orientation, mais la
        // relance a besoin de l'axe de la voiture : elle est encaissée ici, et
        // avant l'automate automatique pour qu'il ne vide pas la charge d'abord.
        if (releaseRequested) {
            releaseRequested = false
            releaseTurbo(forwardX, forwardZ)
            drifting = false
            driftDurationSeconds = 0f
            driftGraceSeconds = 0f
            driftCandidateSeconds = 0f
        }
        val wasDrifting = drifting
        // Deux entrées mènent au même Ruban Turbo. Au bouton (façon kart), le
        // joueur choisit son côté et le tient : c'est la version pilotée. Sans
        // bouton, un virage assez marqué déclenche la même charge tout seul,
        // pour que la conduite tactile d'origine reste jouable telle quelle.
        val driftBaseValid = !manualDrift && !airborne && !input.braking && turboBoostSeconds <= 0f &&
            speed >= DRIFT_SPEED_MIN && forwardSpeed > speed * DRIFT_FORWARD_RATIO_MIN
        val driftRequested = driftBaseValid && abs(steering) >= DRIFT_STEERING_MIN
        val chargeableDrift = driftRequested && slipAngle in DRIFT_ANGLE_MIN..DRIFT_ANGLE_MAX
        if (manualDrift) {
            // Le bouton fait foi : ni la fenêtre d'angle ni le délai de
            // confirmation de l'automatique n'ont leur mot à dire.
            driftCandidateSeconds = 0f
            driftGraceSeconds = 0f
            driftDurationSeconds += dt
            // En l'air la charge est suspendue, jamais perdue : franchir une
            // bosse au milieu d'une courbe ne doit pas punir le joueur.
            if (!airborne) {
                val speedQuality = ((speed - DRIFT_SPEED_MIN) /
                    (MAX_SPEED - DRIFT_SPEED_MIN)).coerceIn(0f, 1f)
                driftEffectiveSeconds += dt *
                    (MANUAL_CHARGE_BASE + MANUAL_CHARGE_SPEED_GAIN * speedQuality)
                turboCharge = (driftEffectiveSeconds / FULL_CHARGE_SECONDS).coerceIn(0f, 1f)
                turboLevel = chargeLevel(turboCharge)
            }
        } else if (!wasDrifting) {
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
        if (!manualDrift && drifting) {
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
        } else if (!manualDrift && wasDrifting) {
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
        val slipping = driftRequested || manualDrift
        val gripBase = when {
            airborne -> 0f
            slipping -> DRIFT_GRIP
            else -> NORMAL_GRIP
        }
        val gripGain = when {
            airborne -> 0f
            slipping -> DRIFT_GRIP_GAIN
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
        // Hors dérapage tenu, les pneus ramènent le travers à zéro. Dans un
        // dérapage tenu, ils le ramènent vers un travers *choisi* : c'est ce qui
        // fait glisser la voiture en crabe au lieu de la laisser simplement
        // patiner, et ce qui rend la glisse stable et reproductible.
        val slipTarget = if (manualDrift && !airborne) {
            -driftDirection * DRIFT_SLIP_RATIO * speed * driftBlend
        } else 0f
        lateralSpeed = approach(lateralSpeed, slipTarget, grip * dt)
        velocityX = forwardX * forwardSpeed + rightX * lateralSpeed
        velocityZ = forwardZ * forwardSpeed + rightZ * lateralSpeed
        if (slipping) {
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
                input.braking && !reversing -> BRAKE_DECELERATION * brakeAmount
                !input.accelerating -> ROLLING_DECELERATION
                else -> 0f
            } + (if (offRoad && !track.scene.circuit.usesFurnitureLayout) OFF_ROAD_DECELERATION else 0f) +
                // La matière du sol freine indépendamment de l'accroche : le
                // sable ralentit sans faire glisser, la glace fait glisser
                // sans ralentir.
                surfaceDrag
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
        if (!sandboxMode) {
            if (!track.scene.circuit.usesHouseLayout) resolveRoomWalls()
            resolveToyObstacles()
        }

        val projection = track.project(worldX, airborneY, worldZ, distance)
        distance = projection.sample.distance
        lateralOffset = projection.lateralOffset
        offRoad = abs(lateralOffset) > projection.sample.roadWidth * 0.5f
        if (distance < previousDistance && previousDistance > track.length * 0.85f && distance < track.length * 0.15f) lap++

        val wheelContacts = sampleWheelContacts()
        val groundedContacts = wheelContacts.filter { it.grounded }
        groundedWheelCount = groundedContacts.size
        groundedOnRoad = groundedContacts.any { it.road }
        // Roues en l'air : on garde la dernière matière connue. Sinon l'adhérence
        // repartirait de 1 à chaque bosse et la réception collerait d'un coup.
        if (groundedContacts.isNotEmpty()) {
            surfaceGrip = groundedContacts.sumOf { it.grip.toDouble() }.toFloat() / groundedContacts.size
            surfaceDrag = groundedContacts.sumOf { it.drag.toDouble() }.toFloat() / groundedContacts.size
        }
        // Une seule roue sur la bande suffit : effleurer le bord doit relancer,
        // sinon la bande punit une trajectoire propre qui la longe.
        updateBoostPad(groundedContacts.any { it.boost })
        // Une dalle au-dessus de la caisse ne peut pas la porter — sinon la
        // voiture se ferait hisser sur un pont qu'elle survole. Mais le sol a pu
        // MONTER sous elle pendant qu'elle avançait : sur une rampe, la surface
        // devant est plus haute que celle qu'elle vient de quitter, et la
        // refuser faisait traverser la piste en vol. Le plafond admissible suit
        // donc le pas horizontal réellement parcouru, jamais une marge fixe.
        // Il reste sous la hauteur de la caisse : c'est ce qui garantit qu'une
        // dalle sous laquelle on peut passer ne devient jamais un appui.
        val horizontalStep = hypot(worldX - previousWorldX, worldZ - previousWorldZ)
        val climbAllowance = (horizontalStep * SUPPORT_CLIMB_SLOPE)
            .coerceIn(0.02f, MAX_SUPPORT_CLIMB)
        val maximumBodySurfaceY = worldPosition.y - PrototypeTrack.CAR_CLEARANCE + climbAllowance
        val floorY = (if (sandboxMode) {
            maxOf(
                editorTrackSurfaceHeightAt(worldX, worldZ, maximumBodySurfaceY),
                editorWorldSurfaceHeightAt(worldX, worldZ, maximumBodySurfaceY)
            ).coerceAtLeast(SANDBOX_FALLBACK_GROUND_Y)
        } else {
            maxOf(
                track.groundHeightAt(worldX, worldZ),
                track.furnitureHeightAt(worldX, worldZ, maximumBodySurfaceY)
            )
        }) + spec.rideHeight
        updateSuspensionPose(dt, wheelContacts)

        var landedOnRoadThisStep = groundedWheelCount > 0
        // Un meuble ne téléporte jamais la voiture sur son plateau. Il ne porte
        // que des roues déjà au-dessus ; quitter son bord déclenche une chute.
        if (!airborne && groundedWheelCount == 0 && worldPosition.y > floorY + 0.05f) {
            airborne = true
            airborneY = worldPosition.y
            // L'élan vient du dernier appui réel, jamais de la branche de course
            // projetée (qui peut être le pont au-dessus).
            verticalVelocity = verticalVelocity.coerceIn(-8f, 4.5f)
        }
        if (airborne) {
            val previousAirborneY = airborneY
            airborneY += verticalVelocity * dt - 0.5f * GRAVITY * dt * dt
            verticalVelocity -= GRAVITY * dt
            resolveFurnitureCeilings(previousAirborneY)
            // Un monde de l'éditeur n'a pas de piste procédurale : celle-ci
            // existe encore en mémoire, invisible, et la consulter en vol
            // faisait atterrir la voiture sur une dalle qui n'est pas là — donc
            // *au travers* de la piste construite quand elle est plus haute.
            if (!sandboxMode) {
                landedOnRoadThisStep = resolveAirborneRoadCollision(
                    previousWorldX,
                    previousWorldZ,
                    previousAirborneY
                )
            }
            if (
                airborne &&
                verticalVelocity <= 0f &&
                airborneY <= floorY
            ) {
                val impact = -verticalVelocity
                airborne = false
                airborneY = floorY
                verticalVelocity = 0f
                groundedOnRoad = false
                noteLanding(impact)
                // Le parquet et les meubles reçoivent aussi les petits sauts
                // sans prélever arbitrairement 20 % de la vitesse à chaque contact.
                val retention = (1f - (impact - 4f).coerceAtLeast(0f) * .012f).coerceAtLeast(.8f)
                velocityX *= retention
                velocityZ *= retention
            }
        }

        if (!airborne && !landedOnRoadThisStep) {
            if (!groundedOnRoad) {
                groundedOnRoad = false
                airborneY = floorY
            }
        }
        // Résoudre les flancs avec la hauteur de cette image : l'ancienne
        // hauteur confondait l'arrivée sur un plateau avec un choc de face.
        if (!airborne && !sandboxMode) resolveGroundRoadCollision(previousWorldX, previousWorldZ)
        if (!sandboxMode) resolveFurnitureSides()
        if (sandboxMode) {
            resolveEditorWorldSides(previousWorldX, previousWorldZ)
            resolveEditorTrackBarriers(previousWorldX, previousWorldZ)
        }
        if (track.scene.circuit.usesHouseLayout && airborneY < -12f) {
            // Une sortie de la maquette n'abandonne pas le joueur sur un sol invisible.
            velocityX = 0f
            velocityZ = 0f
            verticalVelocity = 0f
            airborne = false
            hopping = false
            groundedOnRoad = true
            groundedWheelCount = 4
            pitchRadians = 0f
            rollRadians = 0f
            // Un retour au départ n'est pas une sortie de virage : la glisse en
            // cours est abandonnée sans relance, charge comprise.
            manualDrift = false
            driftDirection = 0
            drifting = false
            releaseRequested = false
            clearTurboCharge()
            placeAtStart()
        }
        speed = hypot(velocityX, velocityZ)
        worldPosition = Vec3(worldX, airborneY, worldZ)
    }

    /** Petit saut : la porte d'entrée du dérapage, pas un saut de plateforme.
     *
     * Il décolle les roues juste assez longtemps pour que le joueur choisisse
     * son côté avant que l'adhérence n'ait redressé la voiture. En l'air, ni
     * moteur ni charge : il ne raccourcit donc aucun tour à lui seul.
     */
    private fun updateHop(dt: Float, input: Input) {
        hopCooldownSeconds = (hopCooldownSeconds - dt).coerceAtLeast(0f)
        val pressed = input.hopping && !hopHeld
        hopHeld = input.hopping
        if (!pressed || airborne || hopCooldownSeconds > 0f) return
        // Au repos, le châssis suit la MOYENNE des appuis de roues. Sur une
        // bosse, son plancher passe donc sous la surface qui le porte en son
        // milieu — c'est voulu, la caisse enjambe le sommet. Mais partir en vol
        // depuis là fait répondre « non » à toutes les questions du type
        // « étais-tu au-dessus de cette dalle ? », et le premier contact est
        // alors pris pour un choc de flanc au lieu d'une réception. Le saut
        // commence donc par poser la caisse sur le point le plus haut qu'elle
        // enjambe : c'est le seul état d'où le vol est cohérent.
        airborneY = maxOf(airborneY, highestSupportUnderBody() + PrototypeTrack.CAR_CLEARANCE)
        airborne = true
        hopping = true
        // Le saut s'AJOUTE à la montée en cours. En rampe, la suspension donne
        // déjà à la caisse la vitesse verticale de la pente (14 unités/s à 30 %
        // font +4 unités/s) : écraser cette valeur par l'impulsion revenait à
        // freiner la montée au moment du saut, et la rampe passait par-dessus
        // la voiture. Une pente descendante ne retire rien : on saute toujours
        // vers le haut par rapport à la surface qu'on quitte. La part héritée
        // est bornée, car la suspension recale la caisse d'un bloc sur une
        // marche : sans plafond, une arête suffirait à envoyer la voiture en
        // orbite. La borne couvre une vraie rampe raide à pleine allure.
        verticalVelocity = verticalVelocity.coerceIn(0f, HOP_CLIMB_CARRY_MAX) + HOP_IMPULSE
        hopCooldownSeconds = HOP_COOLDOWN_SECONDS
        hopSerial++
    }

    /** La surface la plus haute que la caisse enjambe : ses quatre roues et son milieu. */
    private fun highestSupportUnderBody(): Float {
        val forwardX = sin(yawRadians)
        val forwardZ = cos(yawRadians)
        val rightX = forwardZ
        val rightZ = -forwardX
        val halfTrack = spec.trackWidth * 0.5f
        val halfBase = spec.wheelBase * 0.5f
        var highest = supportAt(worldX, worldZ).surfaceY
        for (localX in floatArrayOf(-halfTrack, halfTrack)) {
            for (localZ in floatArrayOf(-halfBase, halfBase)) {
                val x = worldX + rightX * localX + forwardX * localZ
                val z = worldZ + rightZ * localX + forwardZ * localZ
                highest = maxOf(highest, supportAt(x, z).surfaceY)
            }
        }
        return highest
    }

    /** Dérapage tenu au bouton : engagé à la retombée, relâché à la relance. */
    private fun updateManualDrift(dt: Float, input: Input, steering: Float) {
        val held = input.hopping && !input.braking && turboBoostSeconds <= 0f
        if (!manualDrift) {
            // On engage seulement roues au sol : le saut sert justement à donner
            // au joueur le temps de tourner le stick avant de reposer la voiture.
            if (held && !airborne && speed >= DRIFT_SPEED_MIN &&
                abs(steering) >= HOP_DRIFT_STEERING_MIN
            ) {
                manualDrift = true
                drifting = true
                driftDirection = if (steering > 0f) 1 else -1
                driftEffectiveSeconds = 0f
                driftDurationSeconds = 0f
                turboCharge = 0f
                turboLevel = 1
            }
            return
        }
        // Trop lent ou à l'arrêt : la glisse n'a plus de sens, la charge non plus.
        val stillValid = held && speed >= DRIFT_HOLD_MIN_SPEED
        if (stillValid) return
        manualDrift = false
        driftDirection = 0
        // Relâcher le bouton relance ; caler ou freiner annule. La relance elle-même
        // attend l'axe de la voiture, calculé plus loin dans le pas.
        if (held) clearTurboCharge() else releaseRequested = true
    }

    /** Dans un dérapage tenu, le stick module la courbe sans changer de côté. */
    private fun driftSteering(steering: Float): Float {
        val side = driftDirection.toFloat()
        val inward = (steering * side).coerceIn(-1f, 1f)
        return side * (DRIFT_STEER_BASE + DRIFT_STEER_MODULATION * inward)
    }

    /** Bande de relance : elle pousse tant qu'on roule dessus, puis s'éteint. */
    private fun updateBoostPad(touching: Boolean) {
        if (!touching) {
            onBoostPad = false
            return
        }
        // Ni empilement ni raccourcissement : la bande ne fait que garantir un
        // plancher de puissance et de durée. Un gros Ruban Turbo déjà lancé
        // reste donc intact, et enchaîner deux bandes ne cumule rien.
        turboBoostPeakMultiplier = maxOf(turboBoostPeakMultiplier, PAD_BOOST_PEAK)
        turboBoostSeconds = maxOf(turboBoostSeconds, PAD_BOOST_DURATION)
        turboBoostTotalSeconds = maxOf(turboBoostTotalSeconds, turboBoostSeconds)
        if (!onBoostPad) {
            onBoostPad = true
            // Le même front que la relance d'un dérapage : secousse de caméra,
            // vibration, flammes et traînée sont déjà branchées dessus.
            turboReleaseSerial++
        }
    }

    private fun noteLanding(impact: Float) {
        hopping = false
        if (impact < LANDING_NOTICE_SPEED) return
        landingImpact = impact
        landingSerial++
    }

    private fun placeAtStart() {
        editorTrackIndex.firstOrNull()?.takeIf { sandboxMode }?.let { section ->
            worldX = section.startX
            worldZ = section.startZ
            yawRadians = section.yawRadians
            airborneY = section.startY + PrototypeTrack.ROAD_SURFACE_LIFT + spec.rideHeight
            worldPosition = Vec3(worldX, airborneY, worldZ)
            previousDistance = distance
            return
        }
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

    /** Accroche disponible : combien de roues touchent, et sur quelle matière.
     *
     * Les deux facteurs sont lus tels qu'ils ont été mesurés à la fin du pas
     * précédent — comme `groundedWheelCount` l'était déjà. Un pas de retard sur
     * un changement de sol est invisible ; recalculer les appuis deux fois par
     * image, non.
     */
    private fun wheelGripScale(): Float {
        if (airborne) return 0f
        val contact = (0.35f + groundedWheelCount * 0.1625f).coerceIn(0f, 1f)
        return contact * surfaceGrip
    }

    private data class WheelContact(
        val localX: Float,
        val localZ: Float,
        val surfaceY: Float,
        val grounded: Boolean,
        val road: Boolean,
        val grip: Float,
        val drag: Float,
        val boost: Boolean
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
        val grounded = !airborne && suspensionExtension in -MAX_SUPPORT_RISE..GROUND_FOLLOW_DISTANCE
        return WheelContact(
            localX, localZ, support.surfaceY, grounded, support.road,
            support.grip, support.drag, support.boost
        )
    }

    private data class Support(
        val surfaceY: Float,
        val road: Boolean,
        val grip: Float = 1f,
        val drag: Float = 0f,
        val boost: Boolean = false
    )

    /** L'appui est la plus haute surface accessible sous la roue, sans biais de tour. */
    private fun supportAt(wheelX: Float, wheelZ: Float): Support {
        val maximumY = airborneY - spec.rideHeight + if (airborne) .02f else MAX_SUPPORT_RISE
        if (sandboxMode) {
            // La bande la plus haute sous la roue donne aussi sa matière : un
            // ruban de glace posé sur du bitume glisse bien pour de vrai.
            var editorRoadY = Float.NEGATIVE_INFINITY
            var roadStyle = TrackStyle.CLASSIC
            for (section in editorTrackIndex.candidates(wheelX, wheelZ, spec.wheelRadius)) {
                val surfaceY = section.surfaceYAt(wheelX, wheelZ) ?: continue
                if (surfaceY > maximumY + EDITOR_WORLD_SURFACE_TOLERANCE) continue
                if (surfaceY > editorRoadY) {
                    editorRoadY = surfaceY
                    roadStyle = section.style
                }
            }
            val editorY = editorWorldSurfaceHeightAt(wheelX, wheelZ, maximumY)
            val surface = maxOf(editorRoadY, editorY).coerceAtLeast(SANDBOX_FALLBACK_GROUND_Y)
            val onRoad = editorRoadY >= surface - 0.20f
            return if (onRoad) Support(surface, true, roadStyle.grip, roadStyle.drag, roadStyle.boosts)
            else Support(surface, false, SANDBOX_GROUND_GRIP, 0f)
        }
        val collision = track.decksAt(wheelX, wheelZ, spec.wheelRadius)
            .filter { it.sample.position.y + PrototypeTrack.ROAD_SURFACE_LIFT <= maximumY }
            .maxByOrNull { it.sample.position.y }
        val onDeck = collision != null
        val roadSurfaceY = (collision?.sample?.position?.y ?: 0f) + PrototypeTrack.ROAD_SURFACE_LIFT
        val furnitureY = track.furnitureHeightAt(
            wheelX,
            wheelZ,
            maximumY
        )
        val realSurfaceY = maxOf(track.groundHeightAt(wheelX, wheelZ), furnitureY)
        if (onDeck && roadSurfaceY >= realSurfaceY - 0.20f) return Support(roadSurfaceY, true)
        // Dans les circuits de mobilier, le plancher *est* la piste : lui
        // retirer de l'accroche transformerait le parcours prévu en patinoire.
        val floorGrip = if (track.scene.circuit.usesFurnitureLayout) 1f
        else if (furnitureY >= realSurfaceY - 0.01f) FURNITURE_GRIP else FLOOR_GRIP
        return Support(realSurfaceY, false, floorGrip, 0f)
    }

    private fun updateSuspensionPose(
        dt: Float,
        contacts: List<WheelContact>
    ) {
        if (airborne) return
        val grounded = contacts.filter { it.grounded }
        if (grounded.isEmpty()) return

        val targetY = grounded.sumOf { (it.surfaceY + spec.rideHeight).toDouble() }.toFloat() / grounded.size
        // Le châssis suit l'appui ; seul le tangage est amorti visuellement.
        // Un ressort sur l'altitude injectait un rebond à chaque petite bosse.
        verticalVelocity = (targetY - airborneY) / dt.coerceAtLeast(.0001f)
        airborneY = targetY
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
        for (collision in track.decksAt(worldX, worldZ, CAR_COLLISION_RADIUS)
            .sortedByDescending { it.sample.position.y }) {
            val halfWidth = collision.sample.roadWidth * 0.5f +
                PrototypeTrack.CURB_WIDTH + CAR_COLLISION_RADIUS
            if (abs(collision.lateralOffset) > halfWidth) continue

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
                noteLanding(impact)
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
                return false
            }
        }
        return false
    }

    private fun resolveGroundRoadCollision(previousX: Float, previousZ: Float) {
        for (collision in track.decksAt(worldX, worldZ, CAR_COLLISION_RADIUS)) {
            val halfWidth = collision.sample.roadWidth * 0.5f +
                PrototypeTrack.CURB_WIDTH + CAR_COLLISION_RADIUS
            if (abs(collision.lateralOffset) > halfWidth) continue

            val slabTop = collision.sample.position.y + PrototypeTrack.ROAD_SURFACE_LIFT
            val slabBottom = slabTop - PrototypeTrack.ROAD_THICKNESS
            val carBottom = airborneY - PrototypeTrack.CAR_CLEARANCE
            val carTop = airborneY + CAR_TOP_FROM_ORIGIN
            // La hauteur a déjà été résolue par les roues. Vérifier également les
            // autres couches, même si une route basse porte actuellement la voiture.
            if (carTop > slabBottom && carBottom + MAX_SUPPORT_RISE < slabTop) {
                blockAtPreviousHorizontalPosition(previousX, previousZ)
                return
            }
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
            // Tolérance de raccord entre les appuis des roues et le plateau.
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
        if (sandboxMode) {
            var ceiling = Float.POSITIVE_INFINITY
            editorVolumeIndex.visit(worldX, worldZ, 0f) { collider ->
                val underside = collider.ceilingAt(worldX, worldZ) ?: return@visit
                if (previousY + CAR_TOP_FROM_ORIGIN <= underside && airborneY + CAR_TOP_FROM_ORIGIN >= underside)
                    ceiling = minOf(ceiling, underside)
            }
            if (ceiling.isFinite()) {
                airborneY = ceiling - CAR_TOP_FROM_ORIGIN
                verticalVelocity = -abs(verticalVelocity) * TOY_RESTITUTION
            }
            return
        }
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

    private fun editorWorldSurfaceHeightAt(x: Float, z: Float, maximumY: Float): Float {
        var height = Float.NEGATIVE_INFINITY
        editorVolumeIndex.visit(x, z, 0f) { collider ->
            val surfaceY = collider.heightAt(x, z) ?: return@visit
            if (surfaceY <= maximumY + EDITOR_WORLD_SURFACE_TOLERANCE) height = maxOf(height, surfaceY)
        }
        return height
    }

    private fun editorTrackSurfaceHeightAt(x: Float, z: Float, maximumY: Float): Float {
        var height = Float.NEGATIVE_INFINITY
        for (section in editorTrackIndex.candidates(x, z, spec.wheelRadius)) {
            val surfaceY = section.surfaceYAt(x, z) ?: continue
            if (surfaceY <= maximumY + EDITOR_WORLD_SURFACE_TOLERANCE) height = maxOf(height, surfaceY)
        }
        return height
    }

    private class EditorTrackIndex(private val sections: List<EditorTrackCollider>) {
        private val buckets = HashMap<Long, MutableList<EditorTrackCollider>>()

        init {
            sections.forEach { section ->
                val minCellX = cell(section.minX)
                val maxCellX = cell(section.maxX)
                val minCellZ = cell(section.minZ)
                val maxCellZ = cell(section.maxZ)
                for (cx in minCellX..maxCellX) {
                    for (cz in minCellZ..maxCellZ) {
                        buckets.getOrPut(key(cx, cz)) { ArrayList() }.add(section)
                    }
                }
            }
        }

        fun firstOrNull(): EditorTrackCollider? = sections.firstOrNull()

        fun candidates(x: Float, z: Float, margin: Float): List<EditorTrackCollider> {
            if (sections.size < 80) return sections
            val seen = HashSet<EditorTrackCollider>()
            val result = ArrayList<EditorTrackCollider>()
            val minCellX = cell(x - margin)
            val maxCellX = cell(x + margin)
            val minCellZ = cell(z - margin)
            val maxCellZ = cell(z + margin)
            for (cx in minCellX..maxCellX) {
                for (cz in minCellZ..maxCellZ) {
                    buckets[key(cx, cz)]?.forEach { section ->
                        if (seen.add(section) &&
                            x >= section.minX - margin && x <= section.maxX + margin &&
                            z >= section.minZ - margin && z <= section.maxZ + margin) {
                            result += section
                        }
                    }
                }
            }
            return result
        }

        private fun cell(value: Float): Int = floor(value / CELL_SIZE).toInt()

        private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) xor (z.toLong() and 0xFFFFFFFFL)

        companion object {
            private const val CELL_SIZE = 18f
        }
    }

    private class EditorTrackCollider(private val section: ToyboxTrackSection) {
        val barriers = section.barriers
        val style = section.style
        private val a = section.corner(-1f, -1f)
        private val b = section.corner(1f, -1f)
        private val c = section.corner(1f, 1f)
        private val d = section.corner(-1f, 1f)
        // Independent left/right banking creases at the centreline: match the render split.
        private val m1 = section.corner(-1f, 0f)
        private val m2 = section.corner(1f, 0f)
        val leftEdge = a to b
        val rightEdge = d to c
        private val surfaceLeftA = EditorSurfaceTriangle(a, b, m2)
        private val surfaceLeftB = EditorSurfaceTriangle(a, m2, m1)
        private val surfaceRightA = EditorSurfaceTriangle(m1, m2, c)
        private val surfaceRightB = EditorSurfaceTriangle(m1, c, d)
        val startY = section.y
        val endY = section.endY
        val yawRadians = section.yawRadians
        val startX = section.startX
        val startZ = section.startZ
        val minX = minOf(a.x, b.x, c.x, d.x)
        val maxX = maxOf(a.x, b.x, c.x, d.x)
        val minZ = minOf(a.z, b.z, c.z, d.z)
        val maxZ = maxOf(a.z, b.z, c.z, d.z)
        fun surfaceYAt(worldX: Float, worldZ: Float) =
            (surfaceLeftA.heightAt(worldX, worldZ) ?: surfaceLeftB.heightAt(worldX, worldZ)
                ?: surfaceRightA.heightAt(worldX, worldZ) ?: surfaceRightB.heightAt(worldX, worldZ))
                ?.plus(PrototypeTrack.ROAD_SURFACE_LIFT)
    }
    private fun resolveEditorTrackBarriers(previousX: Float, previousZ: Float) {
        if (!editorHasBarriers) return
        val margin = CAR_COLLISION_RADIUS + TrackStyle.BARRIER_THICKNESS
        val travelled = hypot(worldX-previousX, worldZ-previousZ)
        val candidates = editorTrackIndex.candidates(worldX, worldZ, travelled + margin)
        fun collide(a: VolumePoint, b: VolumePoint) {
            val dx = b.x-a.x
            val dz = b.z-a.z
            val length = hypot(dx,dz)
            if (length < .0001f) return
            val nx = -dz/length
            val nz = dx/length
            val before = (previousX-a.x)*nx + (previousZ-a.z)*nz
            val after = (worldX-a.x)*nx + (worldZ-a.z)*nz
            val crossing = before*after < 0f
            val time = if (crossing) (before/(before-after)).coerceIn(0f,1f) else 1f
            val px = previousX+(worldX-previousX)*time
            val pz = previousZ+(worldZ-previousZ)*time
            val along = ((px-a.x)*dx+(pz-a.z)*dz)/(length*length)
            if (along < -margin/length || along > 1f+margin/length) return
            val t = along.coerceIn(0f,1f)
            val base = a.y+(b.y-a.y)*t+PrototypeTrack.ROAD_SURFACE_LIFT
            if (airborneY+CAR_TOP_FROM_ORIGIN < base ||
                airborneY-PrototypeTrack.CAR_CLEARANCE > base+TrackStyle.BARRIER_HEIGHT) return
            val distance = hypot(worldX-(a.x+dx*t), worldZ-(a.z+dz*t))
            if (!crossing && distance >= margin) return
            // Retain the side occupied before this step; a fast car cannot tunnel through.
            val sign = if (before >= 0f) 1f else -1f
            val correction = margin-sign*after
            if (correction <= 0f) return
            worldX += nx*sign*correction
            worldZ += nz*sign*correction
            val speedIntoWall = velocityX*nx*sign + velocityZ*nz*sign
            if (speedIntoWall < 0f) {
                velocityX -= nx*sign*speedIntoWall*1.12f
                velocityZ -= nz*sign*speedIntoWall*1.12f
            }
        }
        for (section in candidates) {
            if (section.barriers and 1 != 0) collide(section.leftEdge.first, section.leftEdge.second)
            if (section.barriers and 2 != 0) collide(section.rightEdge.first, section.rightEdge.second)
        }
    }

    private fun resolveEditorWorldSides(previousX: Float, previousZ: Float) {
        val bottom = airborneY - PrototypeTrack.CAR_CLEARANCE
        val top = airborneY + CAR_TOP_FROM_ORIGIN
        val travelled = hypot(worldX - previousX, worldZ - previousZ)
        editorVolumeIndex.visit(worldX, worldZ, CAR_COLLISION_RADIUS + travelled) { collider ->
            val volume = collider.volume
            if (!volume.blocksSides()) return@visit
            if (bottom >= collider.top - FURNITURE_TOP_SETTLING_MARGIN || top <= collider.bottom) return@visit
            val c = collider.yawCos
            val s = collider.yawSin
            fun localX(x: Float, z: Float) = (x - volume.x) * c - (z - volume.z) * s
            fun localZ(x: Float, z: Float) = (x - volume.x) * s + (z - volume.z) * c
            var localX = localX(worldX, worldZ)
            var localZ = localZ(worldX, worldZ)
            val halfWidth = volume.width * 0.5f + CAR_COLLISION_RADIUS
            val halfDepth = volume.depth * 0.5f + CAR_COLLISION_RADIUS
            var sweptNormalX = 0f
            var sweptNormalZ = 0f
            var sweptHit = false
            if (abs(localX) >= halfWidth || abs(localZ) >= halfDepth) {
                val previousLocalX = localX(previousX, previousZ)
                val previousLocalZ = localZ(previousX, previousZ)
                val dx = localX - previousLocalX
                val dz = localZ - previousLocalZ
                var enter = 0f
                var exit = 1f
                fun clip(start: Float, delta: Float, min: Float, max: Float, axisX: Boolean): Boolean {
                    if (abs(delta) < 0.0001f) return start in min..max
                    val a = (min - start) / delta
                    val b = (max - start) / delta
                    val axisEnter = minOf(a, b)
                    if (axisEnter > enter) {
                        if (axisX) {
                            sweptNormalX = if (a < b) -1f else 1f
                            sweptNormalZ = 0f
                        } else {
                            sweptNormalX = 0f
                            sweptNormalZ = if (a < b) -1f else 1f
                        }
                    }
                    enter = maxOf(enter, axisEnter)
                    exit = minOf(exit, maxOf(a, b))
                    return enter <= exit
                }
                if (!clip(previousLocalX, dx, -halfWidth, halfWidth, axisX = true) ||
                    !clip(previousLocalZ, dz, -halfDepth, halfDepth, axisX = false) ||
                    enter !in 0f..1f) return@visit
                sweptHit = sweptNormalX != 0f || sweptNormalZ != 0f
                if (sweptHit) {
                    val targetX = if (sweptNormalX < 0f) -halfWidth else if (sweptNormalX > 0f) halfWidth else localX
                    val targetZ = if (sweptNormalZ < 0f) -halfDepth else if (sweptNormalZ > 0f) halfDepth else localZ
                    val correctionX = targetX - localX
                    val correctionZ = targetZ - localZ
                    worldX += correctionX * c + correctionZ * s
                    worldZ += -correctionX * s + correctionZ * c
                    localX = targetX
                    localZ = targetZ
                }
            }
            val dx = halfWidth - abs(localX)
            val dz = halfDepth - abs(localZ)
            val nx: Float
            val nz: Float
            val penetration: Float
            if (sweptHit) {
                nx = sweptNormalX * c + sweptNormalZ * s
                nz = -sweptNormalX * s + sweptNormalZ * c
                penetration = 0f
            } else if (dx < dz) {
                val sign = if (localX < 0f) -1f else 1f
                nx = sign * c
                nz = -sign * s
                penetration = dx
            } else {
                val sign = if (localZ < 0f) -1f else 1f
                nx = sign * s
                nz = sign * c
                penetration = dz
            }
            worldX += nx * penetration
            worldZ += nz * penetration
            val normalSpeed = velocityX * nx + velocityZ * nz
            if (normalSpeed < 0f) {
                val impactSpeed = hypot(velocityX, velocityZ)
                velocityX -= (1f + TOY_RESTITUTION) * normalSpeed * nx
                velocityZ -= (1f + TOY_RESTITUTION) * normalSpeed * nz
                preserveCollisionRhythm(impactSpeed)
            }
        }
    }

    private fun ToyboxVolume.blocksSides(): Boolean =
        kind != ToyboxVolumeKind.RAMP &&
            foldedDegrees(pitchDegrees) < 1.5f && foldedDegrees(rollDegrees) < 1.5f && height > 1.2f

    private fun foldedDegrees(degrees: Float): Float {
        val normalized = abs(degrees % 360f)
        return minOf(normalized, 360f - normalized)
    }

    private fun approach(current: Float, target: Float, amount: Float): Float = when {
        current < target -> (current + amount).coerceAtMost(target)
        current > target -> (current - amount).coerceAtLeast(target)
        else -> current
    }

    companion object {
        private const val GRAVITY = 26f
        private const val HOP_IMPULSE = 5.4f
        private const val HOP_CLIMB_CARRY_MAX = 9f
        private const val HOP_COOLDOWN_SECONDS = 0.22f
        private const val HOP_DRIFT_STEERING_MIN = 0.22f
        private const val DRIFT_HOLD_MIN_SPEED = 3.5f
        private const val DRIFT_STEER_BASE = 0.64f
        private const val DRIFT_STEER_MODULATION = 0.36f
        private const val DRIFT_SLIP_RATIO = 0.38f
        private const val MANUAL_CHARGE_BASE = 1.50f
        private const val MANUAL_CHARGE_SPEED_GAIN = 0.70f
        private const val LANDING_NOTICE_SPEED = 2.2f
        private const val PAD_BOOST_PEAK = 1.75f
        private const val PAD_BOOST_DURATION = 1.5f
        private const val FLOOR_GRIP = 0.74f
        private const val FURNITURE_GRIP = 0.88f
        private const val SANDBOX_GROUND_GRIP = 0.80f
        private const val MAX_SUPPORT_RISE = .55f
        /** Pente maximale qu'un appui peut avoir gagné sous la voiture en un pas (~63°). */
        private const val SUPPORT_CLIMB_SLOPE = 2f
        /** Plafonné sous la hauteur de la caisse (0,92) : un toit reste un toit. */
        private const val MAX_SUPPORT_CLIMB = .80f
        private const val GROUND_FOLLOW_DISTANCE = .65f
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
        private const val MAX_BODY_PITCH = 0.76f
        private const val SUSPENSION_POSE_BLEND = 0.18f
        private const val FURNITURE_TOP_SETTLING_MARGIN = 0.3f
        private const val EDITOR_WORLD_EDGE_TOLERANCE = 0.035f
        private const val EDITOR_WORLD_SURFACE_TOLERANCE = 0.08f
        /** Plancher de repli en mode bac à sable : évite une chute infinie
         * quand la roue n'est au-dessus d'aucun bloc du monde édité. */
        private const val SANDBOX_FALLBACK_GROUND_Y = 0f
    }
}
