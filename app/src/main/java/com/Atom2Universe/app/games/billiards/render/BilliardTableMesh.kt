package com.Atom2Universe.app.games.billiards.render

import com.Atom2Universe.app.games.billiards.core.BilliardTable
import com.Atom2Universe.app.games.billiards.core.V3
import com.Atom2Universe.app.games.toyboxracers.render.MeshBuilder
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.*

/** Solid table parts, in metres. Cushion noses use the simulation's rail coordinates. */
internal class BilliardTableMesh(private val table: BilliardTable, private val height: Float) {
    private val l = table.length
    private val w = table.width
    private val noseHeight = table.noseHeight
    val railTop = table.railTop.toFloat()
    private val pocketSides = 96
    private val holes = table.pockets.map { pocket ->
        circle(pocket.center, pocket.radius * 1.15)
    }
    private val collarHoles = table.pockets.map { pocket ->
        circle(pocket.center, pocket.radius * 1.15 + .014)
    }

    fun wood(mesh: MeshBuilder, color: FloatArray) = with(mesh) {
        // Four hollow aprons leave space for the recessed pocket liners. A solid
        // box here would fill every pocket, even with a hole in the top surface.
        val apron = listOf(.220 to -.018, .220 to -.160, .195 to -.205, .180 to -.260)
        for (i in 0 until apron.lastIndex) {
            loopStrip(this, outline(apron[i].first), apron[i].second,
                outline(apron[i + 1].first), apron[i + 1].second, color)
        }
        box(0f, height - .29f, 0f, (l + .36).toFloat(), .06f, (w + .36).toFloat(), color)
        for (x in listOf(-l * .37, l * .37)) for (z in listOf(-w * .34, w * .34)) {
            cylinderY(x.toFloat(), .29f, z.toFloat(), .58f, .085f, 32, color)
            cylinderY(x.toFloat(), .035f, z.toFloat(), .07f, .095f, 32, rgb(0x24252B))
        }

        // The rail cap is a real annulus with six openings, not intersecting boxes
        // or holes discarded by the shader. Its outer edge has a small roundover.
        val cap = subtract(outline(.212), rectangle(-.060, -.060, l + .060, w + .060))
        for (panel in cutHoles(cap, collarHoles)) solid(this, panel, railTop.toDouble(), -.018, color)
        val edge = listOf(.212 to railTop.toDouble(), .215 to railTop - .0006,
            .218 to railTop - .003, .220 to railTop - .008, .220 to -.018)
        for (i in 0 until edge.lastIndex) {
            loopStrip(this, outline(edge[i].first), edge[i].second,
                outline(edge[i + 1].first), edge[i + 1].second, color)
        }
    }

    fun cloth(mesh: MeshBuilder, color: FloatArray) {
        // Cloth continues under the cushion noses to form the shelf at each mouth.
        val bed = rectangle(-.061, -.061, l + .061, w + .061)
        for (panel in cutHoles(listOf(bed), holes)) solid(mesh, panel, 0.0, -.025, color)
    }

    fun cushions(mesh: MeshBuilder, color: FloatArray) {
        val closed = table.pockets.isEmpty()
        val paths = table.cushionPaths
        // A closed rubber profile: recessed underside, rounded nose, cloth-covered
        // slope and flat back. Shared miter vertices seal the two jaw connections.
        val profile = listOf(.018 to .0005, .005 to noseHeight - .010,
            .001 to noseHeight - .004, 0.0 to noseHeight, .002 to noseHeight + .003,
            .009 to noseHeight + .006, .035 to railTop - .001,
            .065 to railTop.toDouble(), .065 to -.002)
        for (path in paths) {
            fun normal(a: V3, b: V3): V3 {
                val tangent = (b - a).unit()
                return V3(tangent.y, -tangent.x)
            }
            val offsets = path.indices.map { i ->
                if (!closed && i == 0) normal(path[0], path[1])
                else if (!closed && i == path.lastIndex) normal(path[i - 1], path[i])
                else {
                    val a = normal(path[(i + path.size - 1) % path.size], path[i])
                    val b = normal(path[i], path[(i + 1) % path.size])
                    (a + b) / (1.0 + a.dot(b)).coerceAtLeast(.1)
                }
            }
            fun section(i: Int) = profile.map { (offset, y) -> point(path[i] + offsets[i] * offset, y) }
            val sections = path.indices.map(::section)
            val count = if (closed) path.size else path.size - 1
            repeat(count) { i ->
                val a = sections[i]
                val b = sections[(i + 1) % sections.size]
                for (j in profile.indices) {
                    val k = (j + 1) % profile.size
                    mesh.quad(a[j], b[j], b[k], a[k], color)
                }
            }
            if (!closed) {
                cap(mesh, sections.first(), color)
                cap(mesh, sections.last().reversed(), color)
            }
        }
    }

    fun pockets(mesh: MeshBuilder) {
        val liningColors = intArrayOf(0x302720, 0x201C19, 0x100F0E, 0x090A0B, 0x050607, 0x030405).map(::rgb)
        val leather = rgb(0x49372A)
        for (pocket in table.pockets) {
            val r = pocket.radius * 1.15
            val outer = circle(pocket.center, r + .014)
            val rim = circle(pocket.center, r)
            // Flush at the cloth-facing edge, rising only behind the jaws to meet
            // the wooden cap. There is no raised cylinder obstructing the mouth.
            fun lipHeight(p: V3): Double {
                val outside = max(max(-p.x, p.x - l), max(-p.y, p.y - w))
                val t = ((outside - .004) / .053).coerceIn(0.0, 1.0)
                return .001 + (railTop - .001) * t * t * (3 - 2 * t)
            }
            val lip = rim.map { point(it, lipHeight(it)) }
            val rimOutside = outer.map { point(it, lipHeight(it)) }
            val rings = listOf(
                lip,
                circle(pocket.center, r - .006).map { point(it, -.012) },
                circle(pocket.center, r * .95).map { point(it, -.065) },
                circle(pocket.center, r * .83).map { point(it, -.150) },
                circle(pocket.center, r * .60).map { point(it, -.225) },
                circle(pocket.center, r * .35).map { point(it, -.245) }
            )
            repeat(pocketSides) { i ->
                val j = (i + 1) % pocketSides
                mesh.quad(rimOutside[i], lip[i], lip[j], rimOutside[j], leather)
                for (level in 0 until rings.lastIndex) {
                    // Vertex colours darken continuously with depth in the pocket.
                    val top = liningColors[level]
                    val bottom = liningColors[level + 1]
                    val a = rings[level][i]; val b = rings[level + 1][i]
                    val c = rings[level + 1][j]; val d = rings[level][j]
                    mesh.coloredTriangle(a, b, c, top, bottom, bottom)
                    mesh.coloredTriangle(a, c, d, top, bottom, top)
                }
                mesh.triangle(point(pocket.center, -.247), rings.last()[j], rings.last()[i], liningColors.last())
            }
        }
    }

    fun trim(mesh: MeshBuilder, color: FloatArray) {
        loopStrip(mesh, outline(.2205), -.041, outline(.2205), -.046, color)
        mesh.box(0f, height - .210f, (w / 2 + .195).toFloat(), .16f, .04f, .005f, color)
    }

    private fun point(p: V3, y: Double) = Vec3((p.x - l / 2).toFloat(), height + y.toFloat(), (p.y - w / 2).toFloat())

    private fun circle(center: V3, radius: Double) = List(pocketSides) { i ->
        val angle = i * 2 * PI / pocketSides
        center + V3(cos(angle), sin(angle)) * radius
    }

    private fun rectangle(x0: Double, y0: Double, x1: Double, y1: Double) =
        listOf(V3(x0, y0), V3(x1, y0), V3(x1, y1), V3(x0, y1))

    /** Rounded outside corners, with identical vertex counts for each moulding. */
    private fun outline(extension: Double): List<V3> {
        val r = extension - .160
        return buildList {
            val centers = listOf(V3(-.160, -.160), V3(l + .160, -.160), V3(l + .160, w + .160), V3(-.160, w + .160))
            for (corner in 0..3) for (step in 0..12) {
                val angle = PI + corner * PI / 2 + step * PI / 24
                add(centers[corner] + V3(cos(angle), sin(angle)) * r)
            }
        }
    }

    private fun loopStrip(mesh: MeshBuilder, a: List<V3>, ay: Double, b: List<V3>, by: Double, color: FloatArray) {
        for (i in a.indices) {
            val j = (i + 1) % a.size
            mesh.quad(point(a[i], ay), point(a[j], ay), point(b[j], by), point(b[i], by), color)
        }
    }

    private fun cap(mesh: MeshBuilder, ring: List<Vec3>, color: FloatArray) {
        val center = Vec3(ring.sumOf { it.x.toDouble() }.toFloat() / ring.size,
            ring.sumOf { it.y.toDouble() }.toFloat() / ring.size, ring.sumOf { it.z.toDouble() }.toFloat() / ring.size)
        for (i in ring.indices) mesh.triangle(center, ring[i], ring[(i + 1) % ring.size], color)
    }

    private fun solid(mesh: MeshBuilder, polygon: List<V3>, top: Double, bottom: Double, color: FloatArray) {
        for (i in 1 until polygon.lastIndex) {
            // The simulation uses XY; mapping it to OpenGL's XZ reverses winding.
            mesh.triangle(point(polygon[0], top), point(polygon[i + 1], top), point(polygon[i], top), color)
            mesh.triangle(point(polygon[0], bottom), point(polygon[i], bottom), point(polygon[i + 1], bottom), color)
        }
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            mesh.quad(point(a, top), point(b, top), point(b, bottom), point(a, bottom), color)
        }
    }

    private fun cutHoles(panels: List<List<V3>>, openings: List<List<V3>>): List<List<V3>> {
        var result = panels
        for (opening in openings) result = result.flatMap { panel ->
            if (panel.maxOf { it.x } < opening.minOf { it.x } || panel.minOf { it.x } > opening.maxOf { it.x } ||
                panel.maxOf { it.y } < opening.minOf { it.y } || panel.minOf { it.y } > opening.maxOf { it.y }) listOf(panel)
            else subtract(panel, opening)
        }
        return result
    }

    /** Difference of convex polygons, kept as disjoint convex pieces for triangle fans. */
    private fun subtract(polygon: List<V3>, hole: List<V3>): List<List<V3>> = buildList {
        var remaining = polygon
        for (i in hole.indices) {
            if (remaining.size < 3) break
            val a = hole[i]; val b = hole[(i + 1) % hole.size]
            val outside = clip(remaining, a, b, false)
            if (area(outside) > 1e-10) add(outside)
            remaining = clip(remaining, a, b, true)
        }
    }

    private fun clip(polygon: List<V3>, a: V3, b: V3, inside: Boolean): List<V3> = buildList {
        if (polygon.isEmpty()) return@buildList
        fun distance(p: V3) = ((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)) * if (inside) 1 else -1
        var previous = polygon.last()
        var dp = distance(previous)
        for (current in polygon) {
            val dc = distance(current)
            if ((dc >= 0) != (dp >= 0)) add(previous + (current - previous) * (dp / (dp - dc)))
            if (dc >= 0) add(current)
            previous = current
            dp = dc
        }
    }

    private fun area(polygon: List<V3>): Double = abs(polygon.indices.sumOf { i ->
        val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
        a.x * b.y - b.x * a.y
    }) / 2

    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f, 1f)
}
