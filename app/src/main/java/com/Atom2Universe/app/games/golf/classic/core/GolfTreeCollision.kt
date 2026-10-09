package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*

/** Swept entry contacts only: prevents tunnelling and repeated impulses while leaving foliage. */
internal object GolfTreeCollision {
    data class Contact(val point: GolfPoint, val velocity: GolfPoint, val time: Float)

    fun collide(tree: GolfTree, ground: Float, from: GolfPoint, to: GolfPoint, velocity: GolfPoint): Contact? {
        val r = tree.radius
        if (max(from.x, to.x) < tree.x - r * 1.5f || min(from.x, to.x) > tree.x + r * 1.5f ||
            max(from.z, to.z) < tree.z - r * 1.5f || min(from.z, to.z) > tree.z + r * 1.5f) return null
        var contact: Contact? = null
        fun shape(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, trunk: Boolean = false) {
            val sx = (from.x - cx) / rx; val sz = (from.z - cz) / rz
            val sy = if (trunk) 0f else (from.y - cy) / ry
            val dx = (to.x - from.x) / rx; val dz = (to.z - from.z) / rz
            val dy = if (trunk) 0f else (to.y - from.y) / ry
            val a = dx * dx + dy * dy + dz * dz
            val b = sx * dx + sy * dy + sz * dz
            val c = sx * sx + sy * sy + sz * sz - 1f
            if (c <= 0f || a < 1e-10f || b >= 0f) return
            val discriminant = b * b - a * c
            if (discriminant < 0f) return
            val t = (-b - sqrt(discriminant)) / a
            if (t !in 0f..1f || t >= (contact?.time ?: 2f)) return
            val px = from.x + (to.x - from.x) * t
            val py = from.y + (to.y - from.y) * t
            val pz = from.z + (to.z - from.z) * t
            if (trunk && (py < ground - .12f || py > ground + r * if (tree.kind == 3) 2.35f else 1.5f)) return
            var nx = (px - cx) / (rx * rx)
            var ny = if (trunk) 0f else (py - cy) / (ry * ry)
            var nz = (pz - cz) / (rz * rz)
            val norm = sqrt(nx * nx + ny * ny + nz * nz)
            if (norm < 1e-8f) return
            nx /= norm; ny /= norm; nz /= norm
            val dot = velocity.x * nx + velocity.y * ny + velocity.z * nz
            if (dot >= 0f) return
            // Reflection followed by uniform damping: kinetic energy can only decrease.
            val damping = if (trunk) .36f else .18f
            val v = GolfPoint((velocity.x - 2f * dot * nx) * damping,
                (velocity.y - 2f * dot * ny) * damping, (velocity.z - 2f * dot * nz) * damping)
            contact = Contact(GolfPoint(px + nx * .008f, py + ny * .008f, pz + nz * .008f), v, t)
        }
        val radius = ClassicHole.BALL_RADIUS
        shape(tree.x, ground, tree.z, .29f + radius, 1f, .29f + radius, true)
        when (tree.kind) {
            0 -> {
                shape(tree.x, ground + r * 1.65f, tree.z, r + radius, r * 1.1f + radius, r + radius)
                shape(tree.x - r * .6f, ground + r * 1.3f, tree.z + .4f, r * .7f + radius, r * .7f + radius, r * .7f + radius)
            }
            1 -> {
                // Inscribed ellipsoids approximate the two visible conifer cones.
                shape(tree.x, ground + r * 1.35f, tree.z, r * .73f + radius, r * .65f + radius, r * .73f + radius)
                shape(tree.x, ground + r * 1.97f, tree.z, r * .57f + radius, r * .57f + radius, r * .57f + radius)
            }
            3 -> shape(tree.x, ground + r * 2.35f, tree.z, r + radius, r * .4f + radius, r + radius)
            else -> {
                shape(tree.x, ground + r * 1.7f, tree.z, r * .68f + radius, r * .68f * 1.8f + radius, r * .68f + radius)
                shape(tree.x + .5f, ground + r, tree.z - .3f, r * .58f + radius, r * .58f + radius, r * .58f + radius)
            }
        }
        return contact
    }
}
