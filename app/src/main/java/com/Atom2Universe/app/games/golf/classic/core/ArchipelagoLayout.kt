package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*

/** Deterministic coastal sites shared by the scenery and the palm collision bodies. */
internal object ArchipelagoLayout {
    fun coast(hole: ClassicHole, island: GolfHazard, angle: Float, inset: Float): GolfPoint {
        val dx = cos(angle); val dz = sin(angle)
        var lo = 0f; var hi = max(island.rx, island.rz) * 2f
        repeat(24) {
            val r = (lo + hi) * .5f
            if (island.signedDistance(island.x + dx * r, island.z + dz * r) < -inset) lo = r else hi = r
        }
        val x = island.x + dx * lo; val z = island.z + dz * lo
        return GolfPoint(x, hole.heightAt(x, z), z)
    }

    fun palms(hole: ClassicHole): List<GolfTree> = buildList {
        for ((index, island) in hole.islands.withIndex()) {
            for (candidate in 0 until 12) {
                val angle = candidate * PI.toFloat() / 6f + index * .43f + hole.number * .31f
                val p = coast(hole, island, angle, 4.5f)
                val radius = 2.5f + (candidate + index) % 3 * .25f
                if (hole.lieAt(p.x, p.z) !in listOf(GolfLie.ROUGH, GolfLie.SEMI_ROUGH) ||
                    hole.greenSignedDistance(p.x, p.z) < radius + 4f ||
                    hypot(p.x, p.z) < 11f ||
                    hypot(p.x - island.x, p.z - island.z) < radius + 12f ||
                    any { hypot(p.x - it.x, p.z - it.z) < 9f }) continue
                // Keep the incoming/outgoing flight corridors free as well as the landing cores.
                val targets = hole.attackLandings + GolfAttackLanding(hole.cup.x, hole.cup.z,
                    GolfClub.WOOD3, island.x, island.z)
                if (targets.any { a ->
                    val dx = a.x - a.fromX; val dz = a.z - a.fromZ
                    val t = (((p.x - a.fromX) * dx + (p.z - a.fromZ) * dz) /
                        (dx * dx + dz * dz).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    hypot(p.x - a.fromX - dx * t, p.z - a.fromZ - dz * t) < radius + 7f
                }) continue
                add(GolfTree(p.x, p.z, radius, 3))
                if (count { hypot(it.x - island.x, it.z - island.z) < max(island.rx, island.rz) * 1.5f } >= 3) break
            }
        }
    }

    data class Mooring(val shore: GolfPoint, val boat: GolfPoint, val yaw: Float)

    fun moorings(hole: ClassicHole): List<Mooring> = hole.islands.mapIndexed { index, island ->
        // Lateral moorings leave both the departure and the incoming approach open.
        (0 until 32).map { i ->
            val angle = i * PI.toFloat() / 16f
            val shore = coast(hole, island, angle, 1f)
            val x = shore.x + cos(angle) * 9f; val z = shore.z + sin(angle) * 9f
            Mooring(shore, GolfPoint(x, hole.islandWaterLevel, z), atan2(cos(angle), sin(angle)))
        }.filter { m ->
            (0 until 16).all { i ->
                val a = i * PI.toFloat() / 8f
                hole.islandSignedDistance(m.boat.x + cos(a) * 4f, m.boat.z + sin(a) * 4f) > 1f
            } && abs(m.boat.x) < hole.width * .5f - 7f && m.boat.z in -12f..hole.length + 45f
        }.maxBy { m ->
            abs(m.boat.x - island.x) - abs(m.boat.z - island.z) * .6f +
                (if ((index + hole.number) % 2 == 0) 1f else -1f) * (m.boat.x - island.x) * .2f
        }
    }
}
