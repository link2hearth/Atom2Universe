package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.*
import kotlin.math.*

/** Coastal scenery baked per island. Only the player's launch bobs live; no per-frame mesh work. */
internal class ArchipelagoScenery(private val hole: ClassicHole) {
    val moorings = ArchipelagoLayout.moorings(hole)
    val launch = boat(false).build()
    val scenery: List<ClassicMesh> by lazy {
        hole.islands.mapIndexed { index, island -> MeshBuilder().apply {
            val m = moorings[index]
            jetty(this, m)
            for (i in 0 until 7) {
                val angle = i * .89f + hole.number * .63f + index * 1.7f
                val p = ArchipelagoLayout.coast(hole, island, angle, -1.5f - i % 3)
                if (hole.islandSignedDistance(p.x, p.z) < .4f ||
                    hypot(p.x - m.boat.x, p.z - m.boat.z) < 8f ||
                    hypot(p.x - m.shore.x, p.z - m.shore.z) < 5f) continue
                val size = .7f + (i + index) % 3 * .35f
                organic(p.x, hole.islandWaterLevel + size * .28f, p.z, size * 1.3f, size * .72f,
                    size, C(.48f,.51f,.46f), angle, 9, 5, .22f)
                organic(p.x + size, hole.islandWaterLevel + size * .09f, p.z + .5f,
                    size * .7f, size * .38f, size * .6f, C(.61f,.62f,.53f), angle + 2f, 7, 4)
            }
        }.build() } + MeshBuilder().apply {
            hole.trees.filter { it.kind == 3 }.forEach { palm(this, it) }
            for (side in listOf(-1f, 1f)) {
                val x = side * (hole.width * .5f + 35f)
                val z = hole.length * if (side < 0f) .38f else .77f
                append(boat(true), P(x, hole.islandWaterLevel, z), side * .65f, 1.6f)
            }
        }.build()
    }

    /** Derived from the addressed ball, so replay seeking and penalty returns choose the same dock. */
    fun mooringFor(ball: GolfPoint): ArchipelagoLayout.Mooring = moorings[hole.islands.indices.minBy {
        hole.islands[it].signedDistance(ball.x, ball.z)
    }]

    private fun jetty(b: MeshBuilder, m: ArchipelagoLayout.Mooring) {
        val dx = sin(m.yaw); val dz = cos(m.yaw)
        val sx = dz * .8f; val sz = -dx * .8f
        val deck = hole.islandWaterLevel + .65f
        for (i in 0 until 13) {
            val t = i * .38f
            val y = m.shore.y + .15f + (deck - m.shore.y - .15f) * min(1f, t / 2f)
            fun at(d: Float, side: Float) = P(m.shore.x + dx * d + sx * side, y,
                m.shore.z + dz * d + sz * side)
            b.quad(at(t, -1f), at(t, 1f), at(t + .34f, 1f), at(t + .34f, -1f),
                C(.61f,.43f,.25f).shade(if (i % 3 == 0) .9f else 1f))
        }
        for (t in listOf(.4f, 2.2f, 4.4f)) for (side in listOf(-1f, 1f)) {
            val x = m.shore.x + dx * t + sx * side
            val z = m.shore.z + dz * t + sz * side
            b.cone(x, hole.islandWaterLevel - .3f, z, .10f, 1.35f, C(.38f,.27f,.17f), 7, .085f)
        }
        // A cream mooring buoy and short rope mark the berth even while the launch is elsewhere.
        b.sphere(m.boat.x + dz * 2.1f, hole.islandWaterLevel + .16f, m.boat.z - dx * 2.1f,
            .25f, C(.94f,.81f,.47f), 10, 6)
    }

    private fun palm(b: MeshBuilder, tree: GolfTree) {
        val x = tree.x; val z = tree.z; val y = hole.heightAt(x, z)
        val r = tree.radius; val top = y + r * 2.35f
        // The slender trunk and elevated fronds match the palm's collision volumes.
        b.cone(x,y,z,.28f,top-y,C(.52f,.38f,.23f),9,.16f)
        for (i in 1..12) b.cone(x,y+(top-y)*i/13f,z,.27f-i*.007f,.07f,C(.39f,.29f,.18f),9,.25f-i*.007f)
        val crown = P(x, top, z)
        for (leaf in 0 until 9) {
            val angle = leaf * 2f * PI.toFloat() / 9f + x * .1f
            val dx = cos(angle); val dz = sin(angle)
            val length = r * (if (leaf % 3 == 0) 1f else .86f)
            fun point(t: Float, side: Float): P {
                val w = sin(t * PI.toFloat()) * .38f * side
                return P(x + dx * length * t - dz * w, top + r * (.29f * sin(t * PI.toFloat()) - .22f * t * t),
                    z + dz * length * t + dx * w)
            }
            for (part in 0 until 6) {
                val a = part / 6f; val c = (part + 1) / 6f
                b.quad(point(a,-1f),point(a,0f),point(c,0f),point(c,-1f),C(.19f,.43f,.22f))
                b.quad(point(a,0f),point(a,1f),point(c,1f),point(c,0f),C(.30f,.54f,.23f))
                // Narrow leaflets give the silhouette a feathery edge.
                if (part > 0) for (side in listOf(-1f, 1f)) {
                    val tip = point(min(1f, a + .2f), side * 1.65f)
                    b.tri(point(a,0f), tip, point(c,side), C(.23f,.47f,.21f))
                }
            }
            b.beam(crown,point(.48f,0f),.035f,C(.42f,.52f,.22f),4,.018f)
        }
        for (i in 0..2) b.sphere(x+cos(i*2.1f)*.22f,top-.25f,z+sin(i*2.1f)*.22f,.18f,C(.37f,.29f,.16f),7,5)
    }

    companion object {
        /** A recognisable wooden launch: pointed bow, open cockpit, benches, canopy and clubs. */
        fun boat(sailing: Boolean): MeshBuilder = MeshBuilder().apply {
            val hull = listOf(P(-.87f,.52f,-2f),P(.87f,.52f,-2f),P(1.03f,.57f,.9f),
                P(.58f,.66f,2.15f),P(0f,.71f,2.8f),P(-.58f,.66f,2.15f),P(-1.03f,.57f,.9f))
            for (i in hull.indices) {
                val a = hull[i]; val b = hull[(i+1)%hull.size]
                quad(P(a.x*.72f,-.16f,a.z*.86f),P(b.x*.72f,-.16f,b.z*.86f),b,a,C(.88f,.88f,.76f))
                beam(a,b,.075f,C(.32f,.22f,.14f),6)
            }
            for (i in 1 until hull.lastIndex) tri(hull[0].copy(y=.22f),hull[i].copy(y=.22f),
                hull[i+1].copy(y=.22f),C(.43f,.31f,.19f))
            for (z in listOf(-1.15f,.55f)) box(0f,.45f,z,1.65f,.13f,.42f,C(.68f,.47f,.25f))
            if (sailing) {
                cone(0f,.3f,.1f,.065f,6.5f,C(.66f,.61f,.45f),7,.04f)
                tri(P(0f,6.8f,.1f),P(0f,1.4f,.1f),P(0f,1.4f,-2f),C(.97f,.93f,.78f),false)
                tri(P(0f,6.0f,.2f),P(0f,1.4f,2.5f),P(0f,1.4f,.2f),C(.85f,.50f,.28f),false)
                beam(P(0f,1.35f,.1f),P(0f,1.35f,-2f),.045f,C(.48f,.37f,.23f),6)
            } else {
                for (x in listOf(-.76f,.76f)) for (z in listOf(-1.2f,1.05f))
                    beam(P(x,.55f,z),P(x,2.05f,z),.035f,C(.83f,.85f,.77f),6)
                for (strip in 0 until 6) {
                    val x = -.92f + strip * .307f
                    quad(P(x,2.10f,-1.45f),P(x+.307f,2.10f,-1.45f),P(x+.307f,2.10f,1.3f),P(x,2.10f,1.3f),
                        if (strip % 2 == 0) C(.21f,.57f,.61f) else C(.96f,.90f,.72f))
                }
                box(0f,.2f,-2.15f,.45f,.7f,.35f,C(.16f,.24f,.27f))
                cone(-.48f,.55f,-1.25f,.22f,.75f,C(.20f,.36f,.43f),9,.20f)
                for (i in 0..3) {
                    val x = -.6f + i * .075f
                    beam(P(x,1f,-1.25f),P(x,1.62f+i*.07f,-1.25f),.018f,C(.78f,.80f,.77f),5)
                    box(x+.035f,1.6f+i*.07f,-1.25f,.11f,.06f,.045f,C(.31f,.33f,.30f))
                }
                ring(.67f,.69f,-.3f,.20f,.075f,C(.94f,.45f,.20f),18)
            }
        }
    }
}
