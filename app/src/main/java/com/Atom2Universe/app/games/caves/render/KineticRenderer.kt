package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.node.FrontierItems
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Rotating mechanical parts and the machines they drive. One mesh per part type, built once; every placed
 * part is one or more instances (position, axis, angle, light), so each part type costs a single draw call.
 * The motion is applied in the vertex shader: the CPU only uploads six floats per instance.
 *
 * A machine is drawn whole: its body (always upright), the axle end on the face its gearbox drives,
 * and the part that shows its work: a millstone, a press head, two crusher rollers, a shuttle.
 */
internal class KineticRenderer {
    private var shader: ShaderProgram? = null
    private var vao = 0
    private var meshVbo = 0
    private var instanceVbo = 0
    private val meshFirst = IntArray(TYPES)
    private val meshCount = IntArray(TYPES)
    private var vpLocation = 0
    private var fogLocation = 0
    private var motionLocation = 0
    private val instances = FloatArray((FrontierWorkshops.MAX_KINETICS * 4 + MAX_WINDMILLS) * INSTANCE_FLOATS)
    /** Sails of each turning windmill, meshed once when it starts: (buffer, vertex count). */
    private val windmillMeshes = HashMap<FrontierWorkshops.Windmill, IntArray>()
    private val drawnWindmills = HashSet<FrontierWorkshops.Windmill>()
    private val nativeInstances: FloatBuffer = ByteBuffer.allocateDirect(instances.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val groupStart = IntArray(TYPES + 1)

    fun onSurfaceCreated() {
        // The previous context and its handles are gone: rebuild everything on resume.
        windmillMeshes.clear()
        shader = ShaderProgram(VERTEX, FRAGMENT).also {
            vpLocation = it.uniform("uVp")
            fogLocation = it.uniform("uCaveFog")
            motionLocation = it.uniform("uMotion")
        }
        val meshes = listOf(shaftMesh(), cogMesh(large = false), cogMesh(large = true), crankMesh(),
            wheelMesh(1.45f, 8), wheelMesh(2.45f, 12), socketMesh(),
            millMesh(), millstoneMesh(), pressMesh(), pressHeadMesh(), crusherMesh(), rollerMesh(), loomMesh(), shuttleMesh(),
            bellowsMesh(), bellowsBoardMesh(), bellowsLeatherMesh(), hopperMesh(), spoutMesh(),
            crucibleStandMesh(), crucibleMesh(), moltenMesh(), moldMetalMesh())
        check(meshes.size == TYPES)
        var first = 0
        for ((type, mesh) in meshes.withIndex()) {
            meshFirst[type] = first; meshCount[type] = mesh.size / MESH_FLOATS; first += meshCount[type]
        }
        val all = meshes.fold(FloatArray(0)) { acc, m -> acc + m }
        val ids = IntArray(2); GLES30.glGenBuffers(2, ids, 0)
        meshVbo = ids[0]; instanceVbo = ids[1]
        val v = IntArray(1); GLES30.glGenVertexArrays(1, v, 0); vao = v[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, meshVbo)
        val meshBuffer = ByteBuffer.allocateDirect(all.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        meshBuffer.put(all).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, all.size * 4, meshBuffer, GLES30.GL_STATIC_DRAW)
        for (location in 0..2) GLES30.glEnableVertexAttribArray(location)
        bindMesh(meshVbo)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instances.size * 4, null, GLES30.GL_DYNAMIC_DRAW)
        for (location in 3..5) {
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribDivisor(location, 1)
        }
        bindInstances(0)
        // Cave's other renderers use the default VAO: never leave this one bound.
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Points the per-vertex attributes at a mesh buffer: the shared part meshes or one windmill's sails. */
    private fun bindMesh(vbo: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val stride = MESH_FLOATS * 4
        for ((location, offset) in listOf(0 to 0, 1 to 3, 2 to 6))
            GLES30.glVertexAttribPointer(location, 3, GLES30.GL_FLOAT, false, stride, offset * 4)
    }

    /** Points the per-instance attributes at the group starting at [first] (GLES 3.0 has no base instance). */
    private fun bindInstances(first: Int) {
        val instanceStride = INSTANCE_FLOATS * 4
        GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, instanceStride, first * instanceStride)
        GLES30.glVertexAttribPointer(4, 3, GLES30.GL_FLOAT, false, instanceStride, first * instanceStride + 12)
        GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, instanceStride, first * instanceStride + 24)
    }

    private fun type(k: FrontierWorkshops.Kinetic) = when (k.block) {
        FrontierItems.COGWHEEL -> 1
        FrontierItems.LARGE_COGWHEEL -> 2
        FrontierItems.CRANK -> 3
        FrontierItems.WATERWHEEL -> 4
        FrontierItems.LARGE_WATERWHEEL -> 5
        else -> 0
    }

    /** The mesh types a machine is drawn with: body, working part (the axle ends are [SOCKET] for all). */
    private fun machineTypes(block: Short) = when (block) {
        FrontierItems.MILL -> MILL to MILLSTONE
        FrontierItems.PRESS -> PRESS to PRESS_HEAD
        FrontierItems.CRUSHER -> CRUSHER to ROLLER
        FrontierItems.LOOM -> LOOM to SHUTTLE
        FrontierItems.BELLOWS -> BELLOWS to BELLOWS_BOARD
        FrontierItems.HOPPER -> HOPPER to SPOUT
        FrontierItems.CRUCIBLE -> CRUCIBLE_STAND to CRUCIBLE_POT
        FrontierItems.CAST_MOLD -> MOLD_METAL to MOLD_METAL
        else -> null
    }

    private var count = 0
    private fun put(k: FrontierWorkshops.Kinetic, camera: Camera, axis: Float, angle: Float, dz: Float = 0f, level: Float = 1f) {
        if ((count + 1 + MAX_WINDMILLS) * INSTANCE_FLOATS > instances.size) return
        val o = count * INSTANCE_FLOATS
        instances[o] = (k.pos.x - camera.x).toFloat()
        instances[o + 1] = (k.pos.y - camera.y).toFloat()
        instances[o + 2] = (k.pos.z - camera.z).toFloat() + dz
        instances[o + 3] = axis
        instances[o + 4] = angle
        instances[o + 5] = k.light
        instances[o + 6] = level
        count++
    }

    /** Every instance of [type] that part [k] needs. Machine bodies stand upright (axis code 1: local Z is up). */
    private fun emit(k: FrontierWorkshops.Kinetic, type: Int, camera: Camera) {
        val machine = machineTypes(k.block)
        if (machine == null) {
            if (type(k) == type) put(k, camera, k.axis.toFloat() + if (k.flipped) 3f else 0f, k.angle + phase(k))
            return
        }
        if (k.block == FrontierItems.CRUCIBLE) {
            // Stand, pot and metal turn about the horizontal axis across the spout; the pot leans towards
            // its mould (at -axis when flipped, else +axis).
            val tiltAxis = if (k.axis == 2) 0 else 2
            val toward = if (k.flipped) -1f else 1f
            val lean = if (k.axis == 0) -toward * k.tilt else toward * k.tilt
            when (type) {
                CRUCIBLE_STAND -> put(k, camera, tiltAxis.toFloat(), 0f)
                CRUCIBLE_POT -> put(k, camera, tiltAxis.toFloat(), lean)
                MOLTEN -> if (k.level > .01f) put(k, camera, tiltAxis.toFloat(), lean, level = k.level)
            }
            return
        }
        if (k.block == FrontierItems.CAST_MOLD) {
            if (type == MOLD_METAL && k.level > .01f) put(k, camera, 2f, 0f, level = k.level)
            return
        }
        when (type) {
            SOCKET -> if (k.axis >= 0 && k.block != FrontierItems.HOPPER) put(k, camera, k.axis.toFloat() + if (k.flipped) 3f else 0f, k.angle)
            SPOUT -> if (k.block == FrontierItems.HOPPER) put(k, camera, k.axis.toFloat() + if (k.flipped) 3f else 0f, 0f)
            machine.first -> put(k, camera, 1f, 0f)
            // Two rollers side by side along X, turning towards each other.
            ROLLER -> if (machine.second == ROLLER) {
                put(k, camera, 0f, k.angle, dz = -ROLLER_GAP)
                put(k, camera, 0f, -k.angle, dz = ROLLER_GAP)
            }
            machine.second -> put(k, camera, 1f, k.angle)
            BELLOWS_LEATHER -> if (k.block == FrontierItems.BELLOWS) put(k, camera, 1f, k.angle)
        }
    }

    fun draw(parts: List<FrontierWorkshops.Kinetic>, windmills: List<FrontierWorkshops.Windmill>, camera: Camera,
             caveBlend: Float, fogEnd: Float) {
        val s = shader ?: return
        if (parts.isEmpty() && windmills.isEmpty()) { releaseWindmills(emptySet()); return }
        // Instances grouped by part type, one contiguous range per mesh.
        count = 0
        for (type in 0 until TYPES) {
            groupStart[type] = count
            for (k in parts) emit(k, type, camera)
        }
        var n = count
        groupStart[TYPES] = n
        val mills = windmills.take(MAX_WINDMILLS)
        for (m in mills) {
            val o = n * INSTANCE_FLOATS
            instances[o] = (m.pos.x - camera.x).toFloat()
            instances[o + 1] = (m.pos.y - camera.y).toFloat()
            instances[o + 2] = (m.pos.z - camera.z).toFloat()
            instances[o + 3] = m.axis.toFloat()
            instances[o + 4] = m.angle
            instances[o + 5] = m.light
            instances[o + 6] = 1f
            n++
        }
        if (n == 0) return
        nativeInstances.clear(); nativeInstances.put(instances, 0, n * INSTANCE_FLOATS).position(0)
        s.use()
        GLES30.glUniformMatrix4fv(vpLocation, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform3f(fogLocation, caveBlend, fogEnd * .55f, fogEnd)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * INSTANCE_FLOATS * 4, nativeInstances)
        bindMesh(meshVbo)
        // Instance attributes read from whatever buffer is bound when they are pointed: the instance one.
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        for (type in 0 until TYPES) {
            val count = groupStart[type + 1] - groupStart[type]
            if (count == 0) continue
            bindInstances(groupStart[type])
            GLES30.glUniform1i(motionLocation, MOTION[type])
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, meshFirst[type], meshCount[type], count)
        }
        GLES30.glUniform1i(motionLocation, 0)
        drawnWindmills.clear()
        for ((i, m) in mills.withIndex()) {
            val mesh = windmillMeshes.getOrPut(m) { uploadWindmill(m) }
            drawnWindmills += m
            if (mesh[1] == 0) continue
            bindMesh(mesh[0])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
            bindInstances(groupStart[TYPES] + i)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, mesh[1], 1)
        }
        releaseWindmills(drawnWindmills)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Neighbouring small cogwheels turn opposite ways: offset every other one by half a tooth so they mesh. */
    private fun phase(k: FrontierWorkshops.Kinetic): Float {
        if (k.block != FrontierItems.COGWHEEL) return 0f
        val parity = when (k.axis) { 0 -> k.pos.y + k.pos.z; 1 -> k.pos.x + k.pos.z; else -> k.pos.x + k.pos.y }
        return if (Math.floorMod(parity, 2) == 1) (PI / COG_TEETH).toFloat() else 0f
    }

    /** Frees the sail meshes of windmills stopped or out of sight. */
    private fun releaseWindmills(keep: Set<FrontierWorkshops.Windmill>) {
        val gone = windmillMeshes.keys.filter { it !in keep }
        for (m in gone) windmillMeshes.remove(m)?.let { GLES30.glDeleteBuffers(1, intArrayOf(it[0]), 0) }
    }

    /** Each sail is a wooden frame with a thin white cloth; sails on the arms from the hub carry a beam.
     * Built in the head's world offsets, then turned into the part frame (local Z = axis). */
    private fun uploadWindmill(m: FrontierWorkshops.Windmill): IntArray {
        val data = windmillMesh(m)
        val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        val buffer = ByteBuffer.allocateDirect(maxOf(4, data.size * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(data).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        return intArrayOf(ids[0], data.size / MESH_FLOATS)
    }

    internal fun windmillMesh(m: FrontierWorkshops.Windmill): FloatArray {
        val out = ArrayList<Float>()
        val axis = m.axis; val u = (axis + 1) % 3; val v = (axis + 2) % 3
        // World offset from the head's centre → part frame: the inverse of the shader's orient().
        fun local(p: FloatArray) = when (axis) {
            0 -> floatArrayOf(-p[2], p[1], p[0]); 1 -> floatArrayOf(p[0], -p[2], p[1]); else -> p
        }
        /** A box given by its centre and half sizes along the wind (a) and the two sail directions (u, v). */
        fun box(center: FloatArray, ha: Float, hu: Float, hv: Float, color: Int) {
            val half = FloatArray(3); half[axis] = ha; half[u] = hu; half[v] = hv
            val r = (color shr 16 and 255) / 255f; val g = (color shr 8 and 255) / 255f; val b = (color and 255) / 255f
            for (k in 0..2) for (sign in intArrayOf(1, -1)) {
                val a = (k + 1) % 3; val c = (k + 2) % 3
                val corners = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (du, dv) ->
                    val q = center.copyOf()
                    q[k] += sign * half[k]; q[a] += du * half[a]; q[c] += dv * half[c] * sign
                    local(q)
                }
                val n = FloatArray(3); n[k] = sign.toFloat(); val ln = local(n)
                for (i in intArrayOf(0, 1, 2, 0, 2, 3)) {
                    val q = corners[i]
                    out += q[0]; out += q[1]; out += q[2]; out += ln[0]; out += ln[1]; out += ln[2]; out += r; out += g; out += b
                }
            }
        }
        val px = 1f / 16f
        for (sail in m.sails) {
            val c = floatArrayOf(sail[0].toFloat(), sail[1].toFloat(), sail[2].toFloat())
            box(c, px, .5f - 2 * px, .5f - 2 * px, CLOTH)
            // Frame along the four edges of the cell, a little thicker than the cloth.
            for (side in intArrayOf(-1, 1)) {
                val e = c.copyOf(); e[v] += side * (.5f - px); box(e, 1.5f * px, .5f, px, FRAME)
                val f = c.copyOf(); f[u] += side * (.5f - px); box(f, 1.5f * px, px, .5f - 2 * px, FRAME)
            }
            // Arms: the beam runs through every sail lined up with the hub.
            if (sail[u] == 0) box(c, 2.5f * px, 2.5f * px, .5f, BEAM)
            if (sail[v] == 0) box(c, 2.5f * px, .5f, 2.5f * px, BEAM)
            // Hub: an axle out of the head's face to the arms, and a boss where the arms cross.
            if (sail[u] == 0 && sail[v] == 0) {
                // From the face of the head (±.5 along the axis) to the plane of the sails.
                val d = sail[axis].toFloat(); val face = if (d > 0) .5f else -.5f
                val a = c.copyOf(); a[axis] = (face + d) / 2f
                box(a, abs(d - face) / 2f, 3f * px, 3f * px, BEAM)
                box(c, 3.5f * px, 5f * px, 5f * px, FRAME)
            }
        }
        return out.toFloatArray()
    }

    fun destroy() {
        releaseWindmills(emptySet())
        shader?.destroy(); shader = null
        if (meshVbo != 0) GLES30.glDeleteBuffers(2, intArrayOf(meshVbo, instanceVbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        meshVbo = 0; instanceVbo = 0; vao = 0
    }

    // ── Meshes, all along local Z and centred on the origin ──────────────────

    private class MeshOut { val data = ArrayList<Float>() }

    /** A box turned by [angle] around local Z: half sizes [hx], [hy] around ([cx], [cy]), from z0 to z1. */
    private fun MeshOut.prism(cx: Float, cy: Float, hx: Float, hy: Float, z0: Float, z1: Float, angle: Float, color: Int) {
        val c = cos(angle); val s = sin(angle)
        fun corner(x: Float, y: Float) = floatArrayOf(cx + x * c - y * s, cy + x * s + y * c)
        val p = arrayOf(corner(-hx, -hy), corner(hx, -hy), corner(hx, hy), corner(-hx, hy))
        val r = (color shr 16 and 255) / 255f; val g = (color shr 8 and 255) / 255f; val b = (color and 255) / 255f
        fun vertex(xy: FloatArray, z: Float, nx: Float, ny: Float, nz: Float) {
            data += xy[0]; data += xy[1]; data += z; data += nx; data += ny; data += nz; data += r; data += g; data += b
        }
        fun quad(a: FloatArray, za: Float, bb: FloatArray, zb: Float, cc: FloatArray, zc: Float, d: FloatArray, zd: Float,
                 nx: Float, ny: Float, nz: Float) {
            for ((xy, z) in listOf(a to za, bb to zb, cc to zc, a to za, cc to zc, d to zd)) vertex(xy, z, nx, ny, nz)
        }
        // Four sides, counter-clockwise seen from outside, then both ends.
        for (i in 0..3) {
            val a = p[i]; val bb = p[(i + 1) % 4]
            val ex = bb[0] - a[0]; val ey = bb[1] - a[1]; val len = kotlin.math.sqrt(ex * ex + ey * ey)
            quad(a, z0, bb, z0, bb, z1, a, z1, ey / len, -ex / len, 0f)
        }
        quad(p[0], z1, p[1], z1, p[2], z1, p[3], z1, 0f, 0f, 1f)
        quad(p[3], z0, p[2], z0, p[1], z0, p[0], z0, 0f, 0f, -1f)
    }

    private fun MeshOut.rod(color: Int, keyColor: Int) {
        prism(0f, 0f, ROD, ROD, -.5f, .5f, 0f, color)
        // A darker strip along one side: without it a turning square rod hardly reads as turning.
        prism(ROD + .25f / 16f, 0f, .25f / 16f, .6f / 16f, -.5f, .5f, 0f, keyColor)
    }

    private fun shaftMesh() = MeshOut().apply { rod(WOOD, DARK) }.data.toFloatArray()

    private fun cogMesh(large: Boolean) = MeshOut().apply {
        rod(WOOD, DARK)
        val teeth = if (large) LARGE_TEETH else COG_TEETH
        val rim = if (large) .80f else .36f
        val thick = 2.5f / 16f
        // Two squares 45° apart make an octagonal wheel; a darker hub; teeth all around.
        for (a in 0..1) prism(0f, 0f, rim, rim, -thick, thick, a * (PI / 4).toFloat(), LIGHT)
        prism(0f, 0f, rim * tan(PI / 8).toFloat() * 1.2f, rim * tan(PI / 8).toFloat() * 1.2f, -thick - .01f, thick + .01f, 0f, DARK)
        val toothRadius = rim + (if (large) .1f else .08f)
        for (i in 0 until teeth) {
            val angle = (i * 2 * PI / teeth).toFloat()
            prism(cos(angle) * toothRadius, sin(angle) * toothRadius, if (large) .09f else .07f, if (large) .06f else .055f,
                -thick * .8f, thick * .8f, angle, DARK)
        }
    }.data.toFloatArray()

    /** Fixed to its axle at local -z: a hub, a lever, and a handle sticking out towards +z. */
    private fun crankMesh() = MeshOut().apply {
        prism(0f, 0f, 1.8f / 16f, 1.8f / 16f, -.5f, -.18f, 0f, WOOD)
        for (a in 0..1) prism(0f, 0f, .15f, .15f, -.5f, -.4f, a * (PI / 4).toFloat(), DARK)
        prism(.2f, 0f, .27f, 1.4f / 16f, -.26f, -.14f, 0f, LIGHT)
        prism(.4f, 0f, 1.1f / 16f, 1.1f / 16f, -.14f, .42f, 0f, WOOD)
        prism(.4f, 0f, 1.6f / 16f, 1.6f / 16f, .36f, .5f, 0f, DARK)
    }.data.toFloatArray()

    /** Paddle wheel: axle, two rims, spokes and paddles, [radius] from the axle to the paddle tips. */
    private fun wheelMesh(radius: Float, spokes: Int) = MeshOut().apply {
        val hub = 3.5f / 16f
        prism(0f, 0f, hub, hub, -.5f, .5f, 0f, DARK)
        val rim = radius * .72f
        val segments = spokes * 2
        for (side in intArrayOf(-1, 1)) {
            val z0 = side * .36f - .045f; val z1 = side * .36f + .045f
            for (i in 0 until segments) {
                val angle = (i * 2 * PI / segments).toFloat()
                prism(cos(angle) * rim, sin(angle) * rim, rim * tan(PI / segments).toFloat() * 1.08f, radius * .035f + .02f,
                    z0, z1, angle + (PI / 2).toFloat(), DARK)
            }
            for (i in 0 until spokes) {
                val angle = (i * 2 * PI / spokes).toFloat()
                prism(cos(angle) * rim / 2, sin(angle) * rim / 2, rim / 2, radius * .02f + .015f, z0, z1, angle, WOOD)
            }
        }
        // Paddles run from inside the rims out past them, across the whole width of the wheel.
        for (i in 0 until spokes) {
            val angle = ((i + .5) * 2 * PI / spokes).toFloat()
            val inner = radius * .55f
            val middle = (inner + radius) / 2
            prism(cos(angle) * middle, sin(angle) * middle, (radius - inner) / 2, radius * .02f + .012f, -.42f, .42f, angle, LIGHT)
        }
    }.data.toFloatArray()

    // ── Machines: bodies in an upright frame (local Z = up), 1/16 = one pixel of a block ─────

    /** The axle end on the face a gearbox drives, at local +z: a short stub and a flange, turning with the network. */
    private fun socketMesh() = MeshOut().apply {
        prism(0f, 0f, ROD, ROD, .42f, .5f, 0f, WOOD)
        prism(ROD + .25f / 16f, 0f, .25f / 16f, .6f / 16f, .42f, .5f, 0f, DARK)
        for (a in 0..1) prism(0f, 0f, 2.6f / 16f, 2.6f / 16f, .47f, .505f, a * (PI / 4).toFloat(), IRON)
    }.data.toFloatArray()

    /** Four slim corner posts from [z0] to the top; beams round the top when [top]. */
    private fun MeshOut.frame(z0: Float, top: Boolean = true) {
        val c = 7f / 16f; val t = 1f / 16f
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) prism(sx * c, sy * c, t, t, z0, .5f, 0f, POST)
        if (top) for (s in intArrayOf(-1, 1)) {
            prism(0f, s * c, c, t, .5f - 2 * t, .5f, 0f, POST)
            prism(s * c, 0f, t, c, .5f - 2 * t, .5f, 0f, POST)
        }
    }

    /** Millstone: a plank base, the fixed bed stone and a spout, a gantry holding the spindle;
     * the runner stone turns on the bed stone. */
    private fun millMesh() = MeshOut().apply {
        prism(0f, 0f, 7f / 16f, 7f / 16f, -.5f, -.12f, 0f, LIGHT)
        prism(0f, 0f, 7.2f / 16f, 7.2f / 16f, -.26f, -.22f, 0f, DARK)
        for (a in 0..1) prism(0f, 0f, 6f / 16f, 6f / 16f, -.12f, -.03f, a * (PI / 4).toFloat(), STONE_DARK)
        prism(0f, .44f, 1.2f / 16f, .06f, -.2f, -.12f, 0f, IRON)
        for (s in intArrayOf(-1, 1)) prism(s * 6.6f / 16f, 0f, 1f / 16f, 1.2f / 16f, -.12f, .5f, 0f, POST)
        prism(0f, 0f, 7.6f / 16f, 1.2f / 16f, .38f, .46f, 0f, POST)
    }.data.toFloatArray()

    private fun millstoneMesh() = MeshOut().apply {
        for (a in 0..1) prism(0f, 0f, 5.8f / 16f, 5.8f / 16f, -.03f, .2f, a * (PI / 4).toFloat(), STONE)
        // Grooves across the top: they show the stone turning.
        for (i in 0..3) prism(0f, 0f, 5.4f / 16f, .45f / 16f, .2f, .215f, (i * PI / 4).toFloat(), STONE_DARK)
        prism(0f, 0f, ROD * .8f, ROD * .8f, .2f, .38f, 0f, WOOD)
    }.data.toFloatArray()

    /** Press: a stone and iron anvil below, a wooden housing above, the head strikes between them. */
    private fun pressMesh() = MeshOut().apply {
        prism(0f, 0f, 7f / 16f, 7f / 16f, -.5f, -.25f, 0f, STONE_DARK)
        prism(0f, 0f, 4.6f / 16f, 4.6f / 16f, -.25f, -.2f, 0f, IRON_LIGHT)
        prism(0f, 0f, 7f / 16f, 7f / 16f, .22f, .5f, 0f, LIGHT)
        prism(0f, 0f, 7.2f / 16f, 7.2f / 16f, .3f, .34f, 0f, DARK)
        frame(-.25f)
    }.data.toFloatArray()

    private fun pressHeadMesh() = MeshOut().apply {
        prism(0f, 0f, 4f / 16f, 4f / 16f, .1f, .2f, 0f, IRON)
        prism(0f, 0f, 1.2f / 16f, 1.2f / 16f, .2f, .5f, 0f, IRON_LIGHT)
    }.data.toFloatArray()

    /** Crusher: a trough, iron cheeks at both ends of the rollers, a hopper rim on top. */
    private fun crusherMesh() = MeshOut().apply {
        prism(0f, 0f, 7f / 16f, 7f / 16f, -.5f, -.3f, 0f, LIGHT)
        for (s in intArrayOf(-1, 1)) {
            prism(s * .46f, 0f, .04f, 6.4f / 16f, -.3f, .34f, 0f, IRON)
            prism(0f, s * .41f, .42f, .04f, .22f, .38f, 0f, WOOD)
        }
        frame(-.3f, top = false)
    }.data.toFloatArray()

    /** One roller, along its own local Z (drawn with axis X): an iron drum with ridges. */
    private fun rollerMesh() = MeshOut().apply {
        for (a in 0..1) prism(0f, 0f, 2.8f / 16f, 2.8f / 16f, -.42f, .42f, a * (PI / 4).toFloat(), IRON)
        for (i in 0 until 8) {
            val angle = (i * 2 * PI / 8).toFloat()
            prism(cos(angle) * 3f / 16f, sin(angle) * 3f / 16f, .5f / 16f, .6f / 16f, -.42f, .42f, angle, IRON_LIGHT)
        }
    }.data.toFloatArray()

    /** Loom: warp beam at the back, threads across, a reed, the woven cloth rolled on the front beam. */
    private fun loomMesh() = MeshOut().apply {
        frame(-.5f, top = false)
        for (s in intArrayOf(-1, 1)) prism(0f, s * 7f / 16f, .42f, 1f / 16f, -.5f, -.42f, 0f, POST)
        prism(0f, .32f, .42f, .06f, .18f, .3f, 0f, LIGHT)
        prism(0f, -.32f, .4f, .08f, -.12f, .04f, 0f, WOOL)
        for (i in 0 until 7) prism(-.3f + i * .1f, .02f, .2f / 16f, .3f, .03f, .045f, 0f, CLOTH)
        prism(0f, -.18f, .36f, .12f, .02f, .04f, 0f, WOOL)
        prism(0f, -.04f, .42f, .02f, .03f, .22f, 0f, DARK)
    }.data.toFloatArray()

    /** Bellows: a fixed bottom board with its hinge; the top board rises and falls, the leather folds between. */
    private fun bellowsMesh() = MeshOut().apply {
        prism(0f, 0f, 7f / 16f, 5f / 16f, -.5f, -.42f, 0f, LIGHT)
        prism(0f, 5.4f / 16f, 7f / 16f, .6f / 16f, -.42f, -.3f, 0f, DARK)
        prism(0f, -6.2f / 16f, 1f / 16f, 1.4f / 16f, -.5f, -.42f, 0f, IRON)
    }.data.toFloatArray()

    private fun bellowsBoardMesh() = MeshOut().apply {
        prism(0f, 0f, 7f / 16f, 5f / 16f, .1f, .18f, 0f, LIGHT)
        prism(0f, -5.8f / 16f, 1f / 16f, 1.2f / 16f, .12f, .16f, 0f, DARK)
    }.data.toFloatArray()

    private fun bellowsLeatherMesh() = MeshOut().apply {
        prism(0f, 0f, 6.4f / 16f, 4.4f / 16f, -.42f, .1f, 0f, LEATHER)
        for (i in 0..2) prism(0f, 0f, 6.7f / 16f, 4.7f / 16f, -.3f + i * .13f, -.28f + i * .13f, 0f, LEATHER_DARK)
    }.data.toFloatArray()

    /** Hopper: an open iron bowl on top narrowing to a neck in the middle of the block. */
    private fun hopperMesh() = MeshOut().apply {
        prism(0f, 0f, 7f / 16f, 7f / 16f, .3f, .5f, 0f, IRON)
        prism(0f, 0f, 5.6f / 16f, 5.6f / 16f, .44f, .505f, 0f, 0x1F2326)
        prism(0f, 0f, 5f / 16f, 5f / 16f, .05f, .3f, 0f, IRON)
        prism(0f, 0f, 3f / 16f, 3f / 16f, -.1f, .05f, 0f, IRON_LIGHT)
        for (s in intArrayOf(-1, 1)) { prism(s * 7.2f / 16f, 0f, .4f / 16f, 7.2f / 16f, .36f, .42f, 0f, IRON_LIGHT)
            prism(0f, s * 7.2f / 16f, 7.2f / 16f, .4f / 16f, .36f, .42f, 0f, IRON_LIGHT) }
    }.data.toFloatArray()

    /** The hopper's spout, along local +z from the neck to the face it pours through. */
    private fun spoutMesh() = MeshOut().apply {
        prism(0f, 0f, 2f / 16f, 2f / 16f, 0f, .5f, 0f, IRON)
        prism(0f, 0f, 2.6f / 16f, 2.6f / 16f, .44f, .5f, 0f, IRON_LIGHT)
    }.data.toFloatArray()

    /** Crucible stand: two posts along the tilt axis (local Z) holding the pivot; local Y is up here. */
    private fun crucibleStandMesh() = MeshOut().apply {
        for (s in intArrayOf(-1, 1)) {
            prism(0f, -.12f, 1.2f / 16f, .38f, s * .44f - .04f, s * .44f + .04f, 0f, IRON)
            prism(0f, -.46f, 3f / 16f, .04f, s * .44f - .05f, s * .44f + .05f, 0f, IRON_LIGHT)
            prism(0f, .02f, 1f / 16f, 1f / 16f, s * .3f - .1f, s * .3f + .1f, 0f, IRON_LIGHT)
        }
    }.data.toFloatArray()

    /** The pot, fired clay, open at the top (local +Y), turning on the pivot at the origin. */
    private fun crucibleMesh() = MeshOut().apply {
        val w = 5f / 16f; val t = 1f / 16f
        prism(0f, -.3f, w, t, -w, w, 0f, CLAY_DARK)
        for (s in intArrayOf(-1, 1)) {
            prism(s * (w - t / 2), 0f, t / 2, .3f, -w, w, 0f, CLAY)
            prism(0f, 0f, w, .3f, s * (w - t / 2) - t / 2, s * (w - t / 2) + t / 2, 0f, CLAY)
        }
        for (s in intArrayOf(-1, 1)) prism(s * (w + t / 2), .28f, t / 2, .03f, -w, w, 0f, CLAY_DARK)
    }.data.toFloatArray()

    /** Molten metal filling the pot from its floor, drawn only when there is some; its height follows the level. */
    private fun moltenMesh() = MeshOut().apply {
        prism(0f, -.005f, 4f / 16f, .235f, -4f / 16f, 4f / 16f, 0f, GLOW)
        prism(0f, .235f, 3f / 16f, .01f, -3f / 16f, 3f / 16f, 0f, GLOW_LIGHT)
    }.data.toFloatArray()

    /** Molten metal in the hollow on top of a casting mould, spreading from the middle as it fills. */
    private fun moldMetalMesh() = MeshOut().apply {
        prism(0f, -.12f, 9f / 32f, .004f, -5f / 32f, 5f / 32f, 0f, GLOW)
        prism(0f, -.114f, 6f / 32f, .003f, -3f / 32f, 3f / 32f, 0f, GLOW_LIGHT)
    }.data.toFloatArray()

    private fun shuttleMesh() = MeshOut().apply {
        prism(0f, .1f, .09f, .03f, .05f, .1f, 0f, LIGHT)
        prism(0f, .1f, .04f, .02f, .1f, .11f, 0f, CLOTH)
    }.data.toFloatArray()

    companion object {
        private const val TYPES = 24
        private const val SOCKET = 6
        private const val MILL = 7
        private const val MILLSTONE = 8
        private const val PRESS = 9
        private const val PRESS_HEAD = 10
        private const val CRUSHER = 11
        private const val ROLLER = 12
        private const val LOOM = 13
        private const val SHUTTLE = 14
        private const val BELLOWS = 15
        private const val BELLOWS_BOARD = 16
        private const val BELLOWS_LEATHER = 17
        private const val HOPPER = 18
        private const val SPOUT = 19
        private const val CRUCIBLE_STAND = 20
        private const val CRUCIBLE_POT = 21
        private const val MOLTEN = 22
        private const val MOLD_METAL = 23
        private const val CLAY = 0xA85B40
        private const val CLAY_DARK = 0x7A4030
        private const val GLOW = 0xFF8A2A
        private const val GLOW_LIGHT = 0xFFD27A
        /** Per type: 0 = turns about its axis, 1 = strikes down and back up, 2 = runs to and fro along X,
         * 3 = folds down from its base (the bellows' leather), 4 = a liquid as high as its level (then turns),
         * 5 = a liquid as wide as its level. */
        private val MOTION = IntArray(TYPES).also { it[PRESS_HEAD] = 1; it[SHUTTLE] = 2; it[BELLOWS_BOARD] = 1; it[BELLOWS_LEATHER] = 3
            it[MOLTEN] = 4; it[MOLD_METAL] = 5 }
        private const val LEATHER = 0x7A5236
        private const val LEATHER_DARK = 0x5A3A24
        /** Crusher rollers: each is this far from the middle, across X. */
        private const val ROLLER_GAP = 3.5f / 16f
        private const val STONE = 0xA5A29B
        private const val STONE_DARK = 0x77746E
        private const val IRON = 0x5E656B
        private const val IRON_LIGHT = 0x8A9299
        private const val WOOL = 0xE2D6BC
        private const val POST = 0x8A6A44
        private const val MAX_WINDMILLS = 16
        private const val CLOTH = 0xF3F0E8
        private const val FRAME = 0x9A7446
        private const val BEAM = 0x6E4E2C
        private const val MESH_FLOATS = 9
        private const val INSTANCE_FLOATS = 7
        private const val COG_TEETH = 8
        private const val LARGE_TEETH = 16
        private const val ROD = 2.5f / 16f
        private const val WOOD = 0xC4A06A
        private const val LIGHT = 0xB8894F
        private const val DARK = 0x6E5436
        private const val VERTEX = """#version 300 es
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec3 aNormal;
            layout(location=2) in vec3 aColor;
            layout(location=3) in vec3 iOffset;
            layout(location=4) in vec3 iAxisAngleLight;
            layout(location=5) in float iLevel;
            uniform mat4 uVp;
            // 0: turns about local Z; 1: strikes down local Z and back once a turn; 2: runs to and fro along local X.
            uniform int uMotion;
            out vec3 vNormal;
            out vec3 vColor;
            out float vLight;
            out float vDistance;
            // Local Z becomes the part's axis: 0 = X, 1 = Y, 2 = Z (both are proper rotations).
            vec3 orient(vec3 p, float axis) {
                if (axis < 0.5) return vec3(p.z, p.y, -p.x);
                if (axis < 1.5) return vec3(p.x, p.z, -p.y);
                return p;
            }
            void main() {
                // Axis codes 3..5: the part is turned round (half a turn about Y), so it spins the other way.
                float axis = iAxisAngleLight.x;
                bool flipped = axis > 2.5;
                if (flipped) axis -= 3.0;
                float angle = flipped ? -iAxisAngleLight.y : iAxisAngleLight.y;
                vec3 p = aPosition;
                vec3 n = aNormal;
                // Liquids: the crucible's rises from the pot floor, the mould's spreads from the middle.
                if (uMotion == 4) p.y = -0.24 + (p.y + 0.24) * iLevel;
                else if (uMotion == 5) { float w = sqrt(iLevel); p.x *= w; p.z *= w; }
                if (uMotion == 1) p.z -= 0.295 * (0.5 - 0.5 * cos(angle));
                else if (uMotion == 2) p.x += 0.26 * sin(angle);
                else if (uMotion == 3) p.z = -0.42 + (p.z + 0.42) * (1.0 - 0.49 * (0.5 - 0.5 * cos(angle)));
                else {
                    float c = cos(angle);
                    float s = sin(angle);
                    p = vec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z);
                    n = vec3(aNormal.x * c - aNormal.y * s, aNormal.x * s + aNormal.y * c, aNormal.z);
                }
                if (flipped) { p = vec3(-p.x, p.y, -p.z); n = vec3(-n.x, n.y, -n.z); }
                vec3 world = orient(p, axis) + vec3(0.5) + iOffset;
                gl_Position = uVp * vec4(world, 1.0);
                vNormal = orient(n, axis);
                vColor = aColor;
                vLight = iAxisAngleLight.z;
                vDistance = length(world);
            }
        """
        private const val FRAGMENT = """#version 300 es
            precision mediump float;
            in vec3 vNormal;
            in vec3 vColor;
            in float vLight;
            in float vDistance;
            uniform vec3 uCaveFog;
            out vec4 fragColor;
            void main() {
                float diffuse = max(dot(normalize(vNormal), normalize(vec3(-0.45, 0.85, 0.35))), 0.0);
                vec3 rgb = vColor * (0.55 + diffuse * 0.45) * (0.12 + vLight * 0.88);
                float fog = smoothstep(uCaveFog.y, uCaveFog.z, vDistance) * uCaveFog.x;
                fragColor = vec4(mix(rgb, vec3(0.0), fog), 1.0);
            }
        """
    }
}
