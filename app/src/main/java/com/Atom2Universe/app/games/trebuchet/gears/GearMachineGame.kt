package com.Atom2Universe.app.games.trebuchet.gears

import com.Atom2Universe.app.games.physics.GearJoint
import com.Atom2Universe.app.games.physics.Joint
import com.Atom2Universe.app.games.physics.OneWayRotaryJoint
import com.Atom2Universe.app.games.physics.FlywheelSpec
import com.Atom2Universe.app.games.physics.MachineMaterials
import com.Atom2Universe.app.games.physics.MachinePartFactory
import com.Atom2Universe.app.games.physics.GearSpec
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.RevoluteJoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Atelier physique autonome de la première machine à engrenages. */
class GearMachineGame(initial: GearMachineConfig = GearMachineConfig()) {
    data class GearState(
        val wheel: GearWheelConfig,
        val body: PhysBody,
        val support: PhysBody,
        val axle: RevoluteJoint
    )

    data class Mesh(val firstId: Int, val secondId: Int, val joint: GearJoint)
    data class Transmission(
        val config: GearLinkConfig,
        val joint: Joint,
        /** Prise progressive de l'embrayage coaxial, de 0 à 1. */
        var clutchGrip: Float = 0f
    )

    data class GearResizeResult(val teeth: Int, val conflicts: Set<Int> = emptySet()) {
        val success: Boolean get() = conflicts.isEmpty()
    }

    private data class MeshConstraint(
        val groupA: Int,
        val groupB: Int,
        val wheelA: Int,
        val wheelB: Int,
        val ux: Float,
        val uy: Float
    )

    data class ProjectileState(
        val body: PhysBody,
        val startX: Float,
        val startY: Float,
        val launchEnergy: Float,
        var age: Float = 0f,
        var peakY: Float = startY
    )

    val world = PhysWorld().apply {
        gravity = -9.80665f
        linearDamping = 0.015f
        // Les pertes principales viennent désormais des paliers ; ceci ne représente
        // plus qu'une faible traînée de l'air sur les roues.
        angularDamping = 0.002f
        sleepEnabled = false
        iterations = 18
    }

    var config = initial.deepCopy()
        private set
    val gears = ArrayList<GearState>()
    val meshes = ArrayList<Mesh>()
    val transmissions = ArrayList<Transmission>()
    var projectile: ProjectileState? = null
        private set
    var lastLaunchSpeed = 0f
        private set
    var lastLaunchEnergy = 0f
        private set
    var lastLaunchEfficiency = 0f
        private set

    private data class SavedMotion(val angle: Float, val omega: Float, val energy: Float)

    init { rebuild(preserveMotion = false) }

    fun loadConfig(value: GearMachineConfig) {
        config = value.deepCopy().also { it.clamp() }
        rebuild(preserveMotion = false)
    }

    fun reset() = loadConfig(GearMachineConfig())

    fun rebuild(
        preserveMotion: Boolean = true,
        useConfiguredAngleFor: Set<Int> = emptySet()
    ) {
        val savedMotion = if (preserveMotion) gears.associate { state ->
            state.wheel.id to SavedMotion(
                state.body.angle,
                state.body.omega,
                0.5f * state.body.inertia * state.body.omega * state.body.omega
            )
        } else emptyMap()
        val savedProjectile = if (preserveMotion) projectile else null
        world.clear()
        gears.clear()
        meshes.clear()
        transmissions.clear()
        projectile = null
        config.clamp()
        alignShaftCenters()
        for (wheel in config.wheels) {
            val spec = GearSpec(
                pitchRadius = wheel.pitchRadius,
                teeth = wheel.teeth,
                faceWidth = 0.10f,
                boreRadius = minOf(0.18f, wheel.pitchRadius * 0.25f),
                material = MachineMaterials.STEEL
            )
            val body = if (wheel.kind == GearWheelKind.FLYWHEEL) {
                MachinePartFactory.flywheel(
                    FlywheelSpec(
                        outerRadius = wheel.outerRadius,
                        innerRadius = wheel.pitchRadius * 0.55f,
                        width = 0.35f,
                        material = wheel.material.physics
                    )
                )
            } else {
                MachinePartFactory.gear(spec.copy(material = wheel.material.physics))
            }.apply {
                x = wheel.x
                y = wheel.y
                angle = if (wheel.id in useConfiguredAngleFor) {
                    wheel.angle
                } else {
                    savedMotion[wheel.id]?.angle ?: wheel.angle
                }
                category = 1
                collidesWith = 0 // les dents sont logiques ; aucune collision parasite.
                collisionLayer = wheel.layer
            }
            val support = PhysBody.circle(maxOf(0.08f, spec.boreRadius), 0f).apply {
                x = wheel.x
                y = wheel.y
                lockPosition = true
                lockRotation = true
                collidesWith = 0
                collisionLayer = wheel.layer
                refreshMass()
            }
            world.add(support)
            world.add(body)
            savedMotion[wheel.id]?.let { motion ->
                if (wheel.id !in useConfiguredAngleFor) wheel.angle = motion.angle
                if (motion.energy > 0f && body.inertia > 1e-12f) {
                    val speed = sqrt(2f * motion.energy / body.inertia)
                    body.omega = if (motion.omega < 0f) -speed else speed
                }
                body.wake()
            }
            val axle = RevoluteJoint.pin(support, body, wheel.x, wheel.y).apply {
                // Frottement sec du palier : il dissipe jusqu'à l'arrêt mais ne peut
                // jamais accélérer la roue ni inverser son sens.
                motorEnabled = true
                motorSpeed = 0f
                maxMotorTorque = wheel.material.axleFriction * body.mass * 9.80665f *
                    maxOf(0.04f, spec.boreRadius)
            }
            world.addJoint(axle)
            gears += GearState(wheel, body, support, axle)
        }
        connectTransmissions()
        connectMeshes()
        savedProjectile?.let { shot ->
            if (shot.body.x.isFinite() && shot.body.y.isFinite()) {
                world.add(shot.body)
                projectile = shot
            }
        }
    }

    private fun connectTransmissions() {
        for (link in config.links) {
            val a = gears.firstOrNull { it.wheel.id == link.firstId } ?: continue
            val b = gears.firstOrNull { it.wheel.id == link.secondId } ?: continue
            val joint: Joint = when (link.kind) {
                GearLinkKind.BELT_OPEN -> GearJoint.belt(
                    a.body, a.wheel.pitchRadius, b.body, b.wheel.pitchRadius, crossed = false
                ).apply { maxTorqueOnB = 18_000f * b.wheel.pitchRadius }
                GearLinkKind.BELT_CROSSED -> GearJoint.belt(
                    a.body, a.wheel.pitchRadius, b.body, b.wheel.pitchRadius, crossed = true
                ).apply { maxTorqueOnB = 14_000f * b.wheel.pitchRadius }
                GearLinkKind.CHAIN_FREEWHEEL -> OneWayRotaryJoint(
                    a.body, b.body,
                    ratio = a.wheel.pitchRadius / b.wheel.pitchRadius,
                    inputDirection = link.inputDirection
                ).apply { maxTorqueOnB = 30_000f * b.wheel.pitchRadius }
                GearLinkKind.SHAFT_CLUTCH -> GearJoint(a.body, b.body, -1f).apply {
                    // Un même arbre traverse volontairement plusieurs couches.
                    requiresLayerOverlap = false
                    maxTorqueOnB = 0f
                }
            }
            world.addJoint(joint)
            transmissions += Transmission(link, joint)
        }
    }

    private fun connectMeshes() {
        for (i in 0 until gears.size) for (j in i + 1 until gears.size) {
            val a = gears[i]
            val b = gears[j]
            if (config.links.any {
                    (it.firstId == a.wheel.id && it.secondId == b.wheel.id) ||
                        (it.firstId == b.wheel.id && it.secondId == a.wheel.id)
                }) continue
            if (a.wheel.layer != b.wheel.layer) continue
            val expected = a.wheel.pitchRadius + b.wheel.pitchRadius
            val actual = hypot(b.wheel.x - a.wheel.x, b.wheel.y - a.wheel.y)
            if (abs(actual - expected) > GearMachineRules.MODULE * 0.38f) continue
            val joint = GearJoint.external(a.body, a.wheel.teeth, b.body, b.wheel.teeth)
            // Limite de denture simplifiée (Lewis) : face × module × résistance,
            // volontairement prudente pour que les matériaux aient un vrai effet.
            val weaker = minOf(a.wheel.material.physics.yieldStrength, b.wheel.material.physics.yieldStrength)
            // Le facteur de jeu garde l'acier rigide sous un entraînement manuel,
            // tout en laissant le bois ou un rapport extrême atteindre le patinage.
            val toothForce = weaker * GearMachineRules.MODULE * 0.10f * 0.060f
            joint.maxTorqueOnB = toothForce * b.wheel.pitchRadius
            world.addJoint(joint)
            meshes += Mesh(a.wheel.id, b.wheel.id, joint)
        }
    }

    fun step(dt: Float) {
        val h = dt.coerceIn(0f, 1f / 30f)
        updateProgressiveClutches(h)
        world.stepFrame(h)
        projectile?.let {
            it.age += h
            it.peakY = maxOf(it.peakY, it.body.y)
            if (it.age > 30f || it.body.y < -20f || !it.body.x.isFinite() || !it.body.y.isFinite()) {
                world.remove(it.body)
                projectile = null
            }
        }
    }

    /**
     * Embrayage centrifuge simplifié : à grand écart de vitesse il patine et protège
     * la roue lourde ; en se rapprochant de la synchronisation il serre davantage.
     */
    private fun updateProgressiveClutches(dt: Float) {
        for (transmission in transmissions) {
            if (transmission.config.kind != GearLinkKind.SHAFT_CLUTCH) continue
            val joint = transmission.joint as? GearJoint ?: continue
            val a = gears.firstOrNull { it.wheel.id == transmission.config.firstId } ?: continue
            val b = gears.firstOrNull { it.wheel.id == transmission.config.secondId } ?: continue
            val slipSpeed = abs(a.body.omega - b.body.omega)
            val targetGrip = 0.16f + 0.84f / (1f + slipSpeed / 7f)
            val response = (dt * if (targetGrip > transmission.clutchGrip) 2.4f else 7f).coerceIn(0f, 1f)
            transmission.clutchGrip += (targetGrip - transmission.clutchGrip) * response
            val smallerRadius = minOf(a.wheel.pitchRadius, b.wheel.pitchRadius).coerceAtLeast(0.25f)
            // La capacité pleine doit dépasser le frottement du palier d'une grande
            // roue d'acier ; c'est la prise faible à grand glissement qui adoucit le
            // choc, pas un embrayage trop faible incapable de la mettre en mouvement.
            joint.maxTorqueOnB = 90_000f * smallerRadius * transmission.clutchGrip.coerceIn(0.05f, 1f)
        }
    }

    fun addGear(teeth: Int, x: Float, y: Float, layer: Int): Int? {
        if (config.wheels.size >= GearMachineRules.MAX_GEARS) return null
        val wheel = GearWheelConfig(
            config.nextId++, x, y,
            teeth.coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH),
            layer.coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        )
        snap(wheel, ignoreId = -1, GearMachineRules.MAGNET_ENGAGE_MODULES)
        config.wheels += wheel
        rebuild()
        return wheel.id
    }

    fun addFlywheel(x: Float, y: Float, layer: Int): Int? {
        val id = addGear(GearMachineRules.FLYWHEEL_TEETH, x, y, layer) ?: return null
        config.wheels.firstOrNull { it.id == id }?.kind = GearWheelKind.FLYWHEEL
        rebuild()
        return id
    }

    fun moveGear(id: Int, x: Float, y: Float, snap: Boolean) {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return
        moveCoaxialGroup(id, x, y)
        val target = if (snap) {
            snap(wheel, id, GearMachineRules.MAGNET_ENGAGE_MODULES)
        } else null
        moveCoaxialFollowers(id)
        rebuild(useConfiguredAngleFor = if (target != null) setOf(id) else emptySet())
    }

    /**
     * Déplacement assisté. [preferredTargetId] donne une petite hystérésis à une prise
     * déjà engagée ; dès que le doigt s'en écarte franchement, la roue redevient libre.
     */
    fun moveGearMagnetic(id: Int, x: Float, y: Float, preferredTargetId: Int?): Int? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        moveCoaxialGroup(id, x, y)
        val held = preferredTargetId?.let {
            snap(wheel, id, GearMachineRules.MAGNET_RELEASE_MODULES, it)
        }
        val target = held ?: snap(wheel, id, GearMachineRules.MAGNET_ENGAGE_MODULES)
        moveCoaxialFollowers(id)
        rebuild(useConfiguredAngleFor = if (target != null) setOf(id) else emptySet())
        return target
    }

    /**
     * Ensemble mécanique solidaire d'une roue : engrènements et transmissions.
     * Les arbres coaxiaux y sont déjà inclus via leurs transmissions.
     */
    fun assemblyIds(startId: Int): Set<Int> {
        if (config.wheels.none { it.id == startId }) return emptySet()
        val ids = linkedSetOf(startId)
        var changed = true
        while (changed) {
            changed = false
            for (mesh in meshes) {
                if (mesh.firstId in ids && ids.add(mesh.secondId)) changed = true
                if (mesh.secondId in ids && ids.add(mesh.firstId)) changed = true
            }
            for (link in config.links) {
                if (link.firstId in ids && ids.add(link.secondId)) changed = true
                if (link.secondId in ids && ids.add(link.firstId)) changed = true
            }
        }
        return ids
    }

    /** Déplace une machine assemblée en gardant toutes ses distances internes. */
    fun moveAssembly(id: Int, x: Float, y: Float) {
        val root = config.wheels.firstOrNull { it.id == id } ?: return
        val dx = x.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD) - root.x
        val dy = y.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD) - root.y
        for (wheel in config.wheels) if (wheel.id in assemblyIds(id)) {
            wheel.x = (wheel.x + dx).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            wheel.y = (wheel.y + dy).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        }
        rebuild()
    }

    private fun coaxialIds(startId: Int): Set<Int> {
        val ids = linkedSetOf(startId)
        var changed = true
        while (changed) {
            changed = false
            for (link in config.links) {
                if (link.kind != GearLinkKind.SHAFT_CLUTCH) continue
                if (link.firstId in ids && ids.add(link.secondId)) changed = true
                if (link.secondId in ids && ids.add(link.firstId)) changed = true
            }
        }
        return ids
    }

    private fun moveCoaxialGroup(id: Int, x: Float, y: Float) {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return
        val nx = x.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        val ny = y.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        val dx = nx - wheel.x
        val dy = ny - wheel.y
        for (member in config.wheels) if (member.id in coaxialIds(id)) {
            member.x = (member.x + dx).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            member.y = (member.y + dy).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        }
    }

    /** Après un cran magnétique, recolle le reste de l'arbre sur la roue manipulée. */
    private fun moveCoaxialFollowers(id: Int) {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return
        for (member in config.wheels) if (member.id in coaxialIds(id) && member.id != id) {
            member.x = wheel.x
            member.y = wheel.y
        }
    }

    private fun alignShaftCenters() {
        val visited = HashSet<Int>()
        for (root in config.wheels) {
            if (!visited.add(root.id)) continue
            val group = coaxialIds(root.id)
            visited += group
            if (group.size < 2) continue
            for (member in config.wheels) if (member.id in group) {
                member.x = root.x
                member.y = root.y
            }
        }
    }

    /** Fige la phase visible atteinte par la simulation avant une opération d'édition. */
    fun captureWheelAngles() {
        for (state in gears) state.wheel.angle = state.body.angle
    }

    private fun snap(
        wheel: GearWheelConfig,
        ignoreId: Int,
        distanceModules: Float,
        onlyTargetId: Int? = null
    ): Int? {
        var best: GearWheelConfig? = null
        var bestError = GearMachineRules.MODULE * distanceModules
        for (other in config.wheels) {
            if (other.id == ignoreId || other.layer != wheel.layer ||
                (onlyTargetId != null && other.id != onlyTargetId)) continue
            val distance = hypot(wheel.x - other.x, wheel.y - other.y)
            val expected = wheel.pitchRadius + other.pitchRadius
            val error = abs(distance - expected)
            if (error < bestError && distance > 1e-4f) {
                best = other
                bestError = error
            }
        }
        best?.let { other ->
            val angle = atan2(wheel.y - other.y, wheel.x - other.x)
            val distance = wheel.pitchRadius + other.pitchRadius
            wheel.x = other.x + cos(angle) * distance
            wheel.y = other.y + sin(angle) * distance
            alignTeeth(wheel, other, angle)
        }
        return best?.id
    }

    /** Place une dent face à un creux, quelle que soit l'orientation de la prise. */
    private fun alignTeeth(wheel: GearWheelConfig, other: GearWheelConfig, centerAngle: Float) {
        val tau = (Math.PI * 2.0).toFloat()
        fun unitPhase(value: Float): Float {
            val remainder = value % 1f
            return if (remainder < 0f) remainder + 1f else remainder
        }
        val otherPhase = unitPhase((centerAngle - other.angle) * other.teeth / tau)
        val complementaryPhase = unitPhase(otherPhase + 0.5f)
        wheel.angle = centerAngle + Math.PI.toFloat() - complementaryPhase * tau / wheel.teeth
    }

    fun deleteGear(id: Int): Boolean {
        val removed = config.wheels.removeAll { it.id == id }
        config.links.removeAll { it.firstId == id || it.secondId == id }
        if (config.launcherWheelId == id) config.launcherWheelId = null
        if (removed) rebuild()
        return removed
    }

    /**
     * Redimensionne une roue comme une opération transactionnelle sur tout le train.
     * La roue éditée reste l'ancre ; chaque engrènement existant repousse sa branche,
     * y compris à travers les groupes coaxiaux. Au moindre conflit, rien ne bouge.
     */
    fun resizeGearAndReflow(id: Int, teeth: Int): GearResizeResult? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        val clamped = teeth.coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH)
        if (wheel.teeth == clamped) return GearResizeResult(clamped)

        val wheelById = config.wheels.associateBy { it.id }
        val groupByWheel = HashMap<Int, Int>()
        for (candidate in config.wheels) {
            if (candidate.id in groupByWheel) continue
            val group = coaxialIds(candidate.id)
            val root = group.minOrNull() ?: candidate.id
            for (memberId in group) groupByWheel[memberId] = root
        }
        val rootGroup = groupByWheel[id] ?: id
        val constraints = ArrayList<MeshConstraint>()
        val meshPairs = HashSet<String>()
        for (mesh in meshes) {
            val a = wheelById[mesh.firstId] ?: continue
            val b = wheelById[mesh.secondId] ?: continue
            val groupA = groupByWheel[a.id] ?: a.id
            val groupB = groupByWheel[b.id] ?: b.id
            if (groupA == groupB) continue
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = hypot(dx, dy)
            if (distance <= 1e-4f) continue
            constraints += MeshConstraint(groupA, groupB, a.id, b.id, dx / distance, dy / distance)
            meshPairs += pairKey(a.id, b.id)
        }

        fun pitchRadius(candidate: GearWheelConfig): Float =
            (if (candidate.id == id) clamped else candidate.teeth) * GearMachineRules.MODULE / 2f
        fun outerRadius(candidate: GearWheelConfig): Float = pitchRadius(candidate) + GearMachineRules.MODULE

        val proposed = HashMap<Int, FloatArray>()
        proposed[rootGroup] = floatArrayOf(wheel.x, wheel.y)
        val parentEdge = HashMap<Int, Pair<Int, MeshConstraint>>()
        val traversalOrder = ArrayList<Int>()
        val queue = java.util.ArrayDeque<Int>()
        queue.add(rootGroup)
        val conflicts = linkedSetOf<Int>()
        while (queue.isNotEmpty()) {
            val group = queue.removeFirst()
            val origin = proposed[group] ?: continue
            for (edge in constraints) {
                val forward = edge.groupA == group
                if (!forward && edge.groupB != group) continue
                val next = if (forward) edge.groupB else edge.groupA
                val a = wheelById[edge.wheelA] ?: continue
                val b = wheelById[edge.wheelB] ?: continue
                val distance = pitchRadius(a) + pitchRadius(b)
                val direction = if (forward) 1f else -1f
                val candidate = floatArrayOf(
                    origin[0] + edge.ux * direction * distance,
                    origin[1] + edge.uy * direction * distance
                )
                val known = proposed[next]
                if (known == null) {
                    proposed[next] = candidate
                    parentEdge[next] = group to edge
                    traversalOrder += next
                    queue.add(next)
                } else if (hypot(known[0] - candidate[0], known[1] - candidate[1]) >
                    GearMachineRules.MODULE * 0.18f) {
                    conflicts += id
                    conflicts += edge.wheelA
                    conflicts += edge.wheelB
                }
            }
        }

        fun position(candidate: GearWheelConfig): FloatArray =
            proposed[groupByWheel[candidate.id] ?: candidate.id]
                ?: floatArrayOf(candidate.x, candidate.y)

        // Seuls les groupes que ce redimensionnement déplace (ou dont le rayon
        // change) peuvent rendre l'opération impossible. Une collision déjà
        // présente dans une autre zone de l'atelier ne doit pas empêcher
        // l'édition d'une roue isolée.
        val affectedGroups = proposed.keys
        for (candidate in config.wheels) {
            if ((groupByWheel[candidate.id] ?: candidate.id) !in affectedGroups) continue
            val p = position(candidate)
            if (p[0] !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD ||
                p[1] !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD) {
                conflicts += id
                conflicts += candidate.id
            }
        }
        for (i in 0 until config.wheels.size) for (j in i + 1 until config.wheels.size) {
            val a = config.wheels[i]
            val b = config.wheels[j]
            if (a.layer != b.layer || pairKey(a.id, b.id) in meshPairs) continue
            val groupA = groupByWheel[a.id] ?: a.id
            val groupB = groupByWheel[b.id] ?: b.id
            if (groupA !in affectedGroups && groupB !in affectedGroups) continue
            val pa = position(a)
            val pb = position(b)
            val distance = hypot(pb[0] - pa[0], pb[1] - pa[1])
            val clearance = outerRadius(a) + outerRadius(b) - GearMachineRules.MODULE * 0.12f
            if (distance < clearance) {
                conflicts += id
                conflicts += a.id
                conflicts += b.id
            }
        }
        if (conflicts.isNotEmpty()) return GearResizeResult(wheel.teeth, conflicts)

        wheel.teeth = clamped
        for (candidate in config.wheels) {
            val p = position(candidate)
            candidate.x = p[0]
            candidate.y = p[1]
        }
        // Les branches déplacées reprennent aussi une phase dent/creux cohérente.
        for (childGroup in traversalOrder) {
            val parent = parentEdge[childGroup] ?: continue
            val parentGroup = parent.first
            val edge = parent.second
            val childWheelId = if (edge.groupA == childGroup) edge.wheelA else edge.wheelB
            val parentWheelId = if (edge.groupA == parentGroup) edge.wheelA else edge.wheelB
            val child = wheelById[childWheelId] ?: continue
            val anchor = wheelById[parentWheelId] ?: continue
            val centerAngle = atan2(child.y - anchor.y, child.x - anchor.x)
            alignTeeth(child, anchor, centerAngle)
        }
        rebuild()
        return GearResizeResult(clamped)
    }

    private fun pairKey(firstId: Int, secondId: Int): String =
        "${minOf(firstId, secondId)}:${maxOf(firstId, secondId)}"

    /**
     * Copie une roue près de l'originale, mais hors de la portée d'engrènement.
     * Les liaisons et le lanceur ne sont pas dupliqués : la nouvelle pièce reste
     * immédiatement réglable avant que l'utilisateur choisisse de la raccorder.
     */
    fun duplicateGear(id: Int): Int? {
        if (config.wheels.size >= GearMachineRules.MAX_GEARS) return null
        val source = config.wheels.firstOrNull { it.id == id } ?: return null
        val position = findFreeDuplicatePosition(source) ?: return null
        val copy = source.copy(
            id = config.nextId++,
            x = position[0],
            y = position[1]
        )
        config.wheels += copy
        rebuild()
        return copy.id
    }

    /** Cherche une place de préparation, sans contact ni aimantation automatique. */
    private fun findFreeDuplicatePosition(source: GearWheelConfig): FloatArray? {
        // La copie est posée avec assez de marge pour aller directement jusqu'à
        // sa taille maximale depuis sa bulle, sans venir heurter l'originale.
        val maxCopyOuterRadius =
            GearMachineRules.MAX_TEETH * GearMachineRules.MODULE / 2f + GearMachineRules.MODULE
        val baseDistance = source.outerRadius + maxCopyOuterRadius + GearMachineRules.MODULE * 2f
        val angles = 16
        for (ring in 1..4) {
            val distance = baseDistance * ring
            for (index in 0 until angles) {
                val angle = (Math.PI * 2.0 * index / angles).toFloat()
                val x = source.x + cos(angle) * distance
                val y = source.y + sin(angle) * distance
                if (x !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD ||
                    y !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD) continue
                val clear = config.wheels.filter { it.layer == source.layer }.all { other ->
                    hypot(x - other.x, y - other.y) >=
                        maxCopyOuterRadius + other.outerRadius + GearMachineRules.MODULE * 0.35f
                }
                if (clear) return floatArrayOf(x, y)
            }
        }
        return null
    }

    fun changeLayer(id: Int, delta: Int): Int? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        wheel.layer = (wheel.layer + delta).coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        config.links.removeAll { link ->
            if (link.kind != GearLinkKind.SHAFT_CLUTCH || (link.firstId != id && link.secondId != id)) {
                false
            } else {
                val otherId = if (link.firstId == id) link.secondId else link.firstId
                config.wheels.firstOrNull { it.id == otherId }?.layer == wheel.layer
            }
        }
        rebuild()
        return wheel.layer
    }

    fun cycleMaterial(id: Int): GearWheelMaterial? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        wheel.material = wheel.material.next()
        rebuild()
        return wheel.material
    }

    fun changeMaterial(id: Int, delta: Int): GearWheelMaterial? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        val materials = GearWheelMaterial.entries
        val index = Math.floorMod(wheel.material.ordinal + delta, materials.size)
        wheel.material = materials[index]
        rebuild()
        return wheel.material
    }

    fun attachLauncher(id: Int): Boolean {
        if (config.wheels.none { it.id == id }) return false
        config.launcherWheelId = id
        return true
    }

    fun adjustLauncherAngle(delta: Float): Float {
        config.launcherAngleDeg = (config.launcherAngleDeg + delta).coerceIn(0f, 85f)
        return config.launcherAngleDeg
    }

    fun addLink(firstId: Int, secondId: Int, kind: GearLinkKind, inputDirection: Int = 1): Boolean {
        if (firstId == secondId || config.links.size >= GearMachineRules.MAX_LINKS) return false
        val first = config.wheels.firstOrNull { it.id == firstId } ?: return false
        val second = config.wheels.firstOrNull { it.id == secondId } ?: return false
        if (kind == GearLinkKind.SHAFT_CLUTCH) {
            if (first.layer == second.layer) return false
            second.x = first.x
            second.y = first.y
            second.angle = first.angle
        } else if (first.layer != second.layer) return false
        config.links.removeAll {
            (it.firstId == firstId && it.secondId == secondId) ||
                (it.firstId == secondId && it.secondId == firstId)
        }
        config.links += GearLinkConfig(firstId, secondId, kind, if (inputDirection < 0) -1 else 1)
        rebuild()
        return true
    }

    fun removeLink(firstId: Int, secondId: Int): Boolean {
        val removed = config.links.removeAll {
            (it.firstId == firstId && it.secondId == secondId) ||
                (it.firstId == secondId && it.secondId == firstId)
        }
        if (removed) rebuild()
        return removed
    }

    fun removeLinksFor(id: Int): Int {
        val before = config.links.size
        config.links.removeAll { it.firstId == id || it.secondId == id }
        val removed = before - config.links.size
        if (removed > 0) rebuild()
        return removed
    }

    fun slippingCount(): Int = meshes.count { it.joint.slipping } + transmissions.count {
        when (val joint = it.joint) {
            is GearJoint -> joint.slipping
            is OneWayRotaryJoint -> joint.slipping
            else -> false
        }
    }

    fun rotationalEnergy(ids: Set<Int>? = null): Float {
        var energy = 0.0
        for (gear in gears) {
            if (ids == null || gear.wheel.id in ids) {
                energy += 0.5 * gear.body.inertia * gear.body.omega * gear.body.omega
            }
        }
        return energy.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }

    private fun connectedTo(startId: Int): Set<Int> {
        val found = linkedSetOf(startId)
        var changed = true
        while (changed) {
            changed = false
            for (mesh in meshes) {
                if (mesh.firstId in found && found.add(mesh.secondId)) changed = true
                if (mesh.secondId in found && found.add(mesh.firstId)) changed = true
            }
            for (transmission in transmissions) {
                val usable = transmission.config.kind != GearLinkKind.CHAIN_FREEWHEEL ||
                    (transmission.joint as OneWayRotaryJoint).engaged
                if (!usable) continue
                val first = transmission.config.firstId
                val second = transmission.config.secondId
                if (first in found && found.add(second)) changed = true
                if (second in found && found.add(first)) changed = true
            }
        }
        return found
    }

    /**
     * Embraye le lanceur sur son arbre et convertit une partie de l'énergie du train
     * connecté en énergie de translation. Le solde est laissé dans les roues ; les
     * pertes du rendement disparaissent en chaleur, jamais en vitesse supplémentaire.
     */
    fun launchProjectile(): Boolean {
        val launcherId = config.launcherWheelId ?: return false
        val launcher = gears.firstOrNull { it.wheel.id == launcherId } ?: return false
        val connected = connectedTo(launcherId)
        val available = rotationalEnergy(connected)
        if (available < 0.5f) return false

        projectile?.let { world.remove(it.body) }
        val mass = config.projectileMass
        val efficiency = GearMachineRules.LAUNCH_EFFICIENCY
        val maximumProjectileEnergy = 0.5f * mass * MAX_PROJECTILE_SPEED * MAX_PROJECTILE_SPEED
        val projectileEnergy = minOf(available * efficiency, maximumProjectileEnergy)
        val energyTaken = projectileEnergy / efficiency
        val remainingScale = sqrt(((available - energyTaken) / available).coerceIn(0f, 1f))
        for (gear in gears) if (gear.wheel.id in connected) {
            gear.body.omega *= remainingScale
            gear.body.wake()
        }
        for (mesh in meshes) mesh.joint.reset()
        for (transmission in transmissions) transmission.joint.reset()

        val angle = Math.toRadians(config.launcherAngleDeg.toDouble()).toFloat()
        val nx = cos(angle)
        val ny = sin(angle)
        val speed = sqrt(2f * projectileEnergy / mass)
        val radius = (0.075f * kotlin.math.cbrt(mass.toDouble())).toFloat().coerceIn(0.06f, 0.55f)
        val muzzleDistance = launcher.wheel.outerRadius + radius + 0.8f
        val shot = PhysBody.circle(radius, mass).apply {
            x = launcher.body.x + nx * muzzleDistance
            y = launcher.body.y + ny * muzzleDistance
            vx = nx * speed
            vy = ny * speed
            restitution = 0.18f
            friction = 0.55f
            dragFactor = 0.5f * 1.225f * 0.47f * Math.PI.toFloat() * radius * radius
            collidesWith = 0
            collisionLayer = launcher.wheel.layer
        }
        world.add(shot)
        projectile = ProjectileState(shot, shot.x, shot.y, projectileEnergy)
        lastLaunchSpeed = speed
        lastLaunchEnergy = projectileEnergy
        lastLaunchEfficiency = projectileEnergy / energyTaken
        return true
    }

    fun spinGear(id: Int, angularSpeed: Float) {
        val state = gears.firstOrNull { it.wheel.id == id } ?: return
        state.body.omega = angularSpeed.coerceIn(-GearMachineRules.MAX_MANUAL_SPEED, GearMachineRules.MAX_MANUAL_SPEED)
        state.body.wake()
    }

    /** Ajoute un coup de doigt sans écraser l'élan déjà accumulé. */
    fun flickGear(id: Int, gestureAngularSpeed: Float) {
        val state = gears.firstOrNull { it.wheel.id == id } ?: return
        val added = gestureAngularSpeed.coerceIn(
            -GearMachineRules.MAX_MANUAL_SPEED,
            GearMachineRules.MAX_MANUAL_SPEED
        ) * GearMachineRules.FLICK_TRANSFER
        state.body.omega = (state.body.omega + added).coerceIn(
            -GearMachineRules.MAX_MANUAL_SPEED,
            GearMachineRules.MAX_MANUAL_SPEED
        )
        state.body.wake()
    }

    fun stopAll() {
        for (gear in gears) {
            gear.body.vx = 0f
            gear.body.vy = 0f
            gear.body.omega = 0f
            gear.body.wake()
        }
        for (mesh in meshes) mesh.joint.reset()
        for (transmission in transmissions) transmission.joint.reset()
    }

    fun gearAt(x: Float, y: Float, layer: Int? = null): GearState? = gears.asReversed().firstOrNull {
        (layer == null || it.wheel.layer == layer) &&
            hypot(x - it.body.x, y - it.body.y) <= it.wheel.outerRadius
    }

    fun bounds(): FloatArray {
        if (gears.isEmpty()) return floatArrayOf(-5f, 5f, 0f, 8f)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = 0f; var maxY = -Float.MAX_VALUE
        for (gear in gears) {
            val r = gear.wheel.outerRadius
            minX = minOf(minX, gear.wheel.x - r)
            maxX = maxOf(maxX, gear.wheel.x + r)
            minY = minOf(minY, gear.wheel.y - r)
            maxY = maxOf(maxY, gear.wheel.y + r)
        }
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    companion object {
        private const val MAX_PROJECTILE_SPEED = 1_500f
    }
}
