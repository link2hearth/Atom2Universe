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
    data class Transmission(val config: GearLinkConfig, val joint: Joint)

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

    init { rebuild() }

    fun loadConfig(value: GearMachineConfig) {
        config = value.deepCopy().also { it.clamp() }
        rebuild()
    }

    fun reset() = loadConfig(GearMachineConfig())

    fun rebuild() {
        world.clear()
        gears.clear()
        meshes.clear()
        transmissions.clear()
        projectile = null
        config.clamp()
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

    fun addGear(teeth: Int, x: Float, y: Float, layer: Int): Int? {
        if (config.wheels.size >= GearMachineRules.MAX_GEARS) return null
        val wheel = GearWheelConfig(
            config.nextId++, x, y,
            teeth.coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH),
            layer.coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        )
        snap(wheel, ignoreId = -1)
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
        wheel.x = x.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        wheel.y = y.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        if (snap) snap(wheel, id)
        rebuild()
    }

    private fun snap(wheel: GearWheelConfig, ignoreId: Int) {
        var best: GearWheelConfig? = null
        var bestError = GearMachineRules.MODULE * GearMachineRules.SNAP_DISTANCE_MODULES
        for (other in config.wheels) {
            if (other.id == ignoreId || other.layer != wheel.layer) continue
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
        }
    }

    fun deleteGear(id: Int): Boolean {
        val removed = config.wheels.removeAll { it.id == id }
        config.links.removeAll { it.firstId == id || it.secondId == id }
        if (config.launcherWheelId == id) config.launcherWheelId = null
        if (removed) rebuild()
        return removed
    }

    fun changeLayer(id: Int, delta: Int): Int? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        wheel.layer = (wheel.layer + delta).coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        rebuild()
        return wheel.layer
    }

    fun cycleMaterial(id: Int): GearWheelMaterial? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        wheel.material = wheel.material.next()
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
        if (first.layer != second.layer) return false
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

    fun gearAt(x: Float, y: Float): GearState? = gears.asReversed().firstOrNull {
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
