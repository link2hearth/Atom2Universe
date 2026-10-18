package com.Atom2Universe.app.games.toyboxracers.driving

import com.Atom2Universe.app.games.toyboxracers.ai.RivalCar
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal interface RaceVehicle {
    val worldPosition: Vec3
    val yawRadians: Float
    val contactVelocityX: Float
    val contactVelocityZ: Float
    val vehicleContactsEnabled: Boolean get() = true

    /** Déplacement de séparation et impulsion sont deux opérations distinctes. */
    fun applyVehicleContact(dx: Float, dz: Float, dvx: Float, dvz: Float, impact: Float)
}

/** Contacts entre carrosseries orientées, masses égales, restitution faible.
 * La séparation ne crée aucune vitesse ; l'impulsion ne s'applique qu'en rapprochement.
 */
internal class VehicleContacts {
    private val bodies = arrayOfNulls<RaceVehicle>(6)
    private val axesX = FloatArray(4)
    private val axesZ = FloatArray(4)

    fun resolve(player: ArcadeCar, rivals: List<RivalCar>) {
        bodies[0] = player
        for (i in rivals.indices) bodies[i + 1] = rivals[i]
        // Quatre passages stabilisent un peloton serré sans ajouter de rebond.
        repeat(4) {
            for (i in 0..rivals.size) for (j in i + 1..rivals.size) {
                resolvePair(bodies[i]!!, bodies[j]!!)
            }
        }
    }

    fun resolvePair(a: RaceVehicle, b: RaceVehicle) {
        if (!a.vehicleContactsEnabled || !b.vehicleContactsEnabled) return
        val pa = a.worldPosition; val pb = b.worldPosition
        if (abs(pa.y - pb.y) >= BODY_HEIGHT) return
        val dx = pb.x - pa.x; val dz = pb.z - pa.z
        if (dx * dx + dz * dz > 4.0f) return
        val afx = sin(a.yawRadians); val afz = cos(a.yawRadians)
        val bfx = sin(b.yawRadians); val bfz = cos(b.yawRadians)
        axesX[0] = afz; axesZ[0] = -afx
        axesX[1] = afx; axesZ[1] = afz
        axesX[2] = bfz; axesZ[2] = -bfx
        axesX[3] = bfx; axesZ[3] = bfz
        var penetration = Float.POSITIVE_INFINITY
        var nx = 0f; var nz = 0f
        for (i in 0..3) {
            val x = axesX[i]; val z = axesZ[i]
            val ra = HALF_WIDTH * abs(x * afz - z * afx) + HALF_LENGTH * abs(x * afx + z * afz)
            val rb = HALF_WIDTH * abs(x * bfz - z * bfx) + HALF_LENGTH * abs(x * bfx + z * bfz)
            val center = dx * x + dz * z
            val overlap = ra + rb - abs(center)
            if (overlap <= 0f) return
            if (overlap < penetration) {
                penetration = overlap
                val sign = if (center < 0f) -1f else 1f
                nx = x * sign; nz = z * sign
            }
        }
        val correction = (penetration + .002f) * .5f
        val rvx = b.contactVelocityX - a.contactVelocityX
        val rvz = b.contactVelocityZ - a.contactVelocityZ
        val relativeNormal = rvx * nx + rvz * nz
        val impulse = if (relativeNormal < 0f) -(1f + RESTITUTION) * relativeNormal * .5f else 0f
        val tx = -nz; val tz = nx
        val tangentImpulse = (-(rvx * tx + rvz * tz) * .5f).coerceIn(-impulse * .22f, impulse * .22f)
        val ix = nx * impulse + tx * tangentImpulse
        val iz = nz * impulse + tz * tangentImpulse
        val impact = (-relativeNormal).coerceAtLeast(0f)
        a.applyVehicleContact(-nx * correction, -nz * correction, -ix, -iz, impact)
        b.applyVehicleContact(nx * correction, nz * correction, ix, iz, impact)
    }

    companion object {
        private const val HALF_WIDTH = .56f
        private const val HALF_LENGTH = .78f
        private const val BODY_HEIGHT = .52f
        private const val RESTITUTION = .08f
    }
}
