package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * La fusion des faces (étape B) ne doit rien changer à l'image : redécoupé en carrés d'un bloc,
 * le maillage fusionné porte exactement les mêmes carrés — même texture, même ciel, mêmes
 * torches, même teinte, même sens de texture — que le maillage bloc par bloc.
 */
class GreedyMeshTest {
    @Test
    fun mergedMeshCoversTheSameSquaresAsTheBlockByBlockMesh() {
        // La Tour du crépuscule : murs, vitres, marches, dalles et torches, sans terrain naturel.
        val source = MapSource(OfficeTowerMap.create())
        val world = World(source = source)
        val bounds = source.chunkBounds()
        val chunks = ArrayList<Chunk>()
        for (cy in bounds.minCy..bounds.maxCy) for (cz in bounds.minCz..bounds.maxCz) for (cx in bounds.minCx..bounds.maxCx)
            chunks += world.pregenerateChunk(cx, cy, cz)
        repeat(8) { for (c in chunks) { LightEngine.computeSky(c, world); LightEngine.computeBlock(c, world) } }

        var before = 0; var after = 0; var lit = 0
        for (chunk in chunks) {
            val single = MeshBuilder.build(chunk, world, mergeFaces = false)
            val merged = MeshBuilder.build(chunk, world)
            before += single.vertexCount; after += merged.vertexCount
            for (v in 0 until single.vertexCount) if (single.vertices[v * PackedMesh.STRIDE + 15].toInt() != 0) lit++
            assertEquals("chunk ${chunk.cx},${chunk.cy},${chunk.cz}", squares(single), squares(merged))
        }
        // Tour pleine de torches : leurs dégradés empêchent souvent la fusion (−29 % mesurés).
        println("sommets bloc par bloc : $before, fusionnés : $after")
        assertTrue("des faces éclairées par les torches", lit > 0)
        assertTrue("la fusion réduit les sommets ($before → $after)", after < before * 0.8)
    }

    /** Chaque face rectangulaire alignée sur la grille, redécoupée en carrés d'un bloc. */
    private fun squares(mesh: PackedMesh): List<String> {
        val vb = ByteBuffer.wrap(mesh.vertices).order(ByteOrder.nativeOrder())
        val ib = ByteBuffer.wrap(mesh.indices).order(ByteOrder.nativeOrder())
        fun index(i: Int) = if (mesh.wideIndices) ib.getInt(i * 4) else ib.getShort(i * 2).toInt() and 0xFFFF
        fun pos(v: Int, k: Int) = vb.getShort(v * PackedMesh.STRIDE + k * 2) / PackedMesh.POS_SCALE
        fun uv(v: Int, k: Int) = (vb.getShort(v * PackedMesh.STRIDE + 8 + k * 2).toInt() and 0xFFFF) / PackedMesh.UV_SCALE
        fun attributes(v: Int): String {
            val o = v * PackedMesh.STRIDE
            return "p=${vb.getShort(o + 12).toInt() and 0xFFFF} s=${vb.get(o + 14).toInt() and 0xFF} " +
                "b=${vb.get(o + 15).toInt() and 0xFF} t=${(0..3).map { vb.getShort(o + 16 + it * 2) }}"
        }
        val out = ArrayList<String>()
        for (q in 0 until mesh.indexCount / 6) {
            val v0 = index(q * 6); val v1 = index(q * 6 + 1); val v2 = index(q * 6 + 2); val v3 = index(q * 6 + 5)
            val corners = intArrayOf(v0, v1, v2, v3)
            val flatAxis = (0..2).firstOrNull { k -> corners.all { pos(it, k) == pos(v0, k) } }
            val integral = corners.all { v -> (0..2).all { k -> pos(v, k) == floor(pos(v, k)) } }
            val uniform = corners.all { attributes(it) == attributes(v0) }
            if (flatAxis == null || !integral || !uniform) {
                // Tout le reste (torches, marches, fleurs, dégradés) doit sortir à l'identique.
                out += "raw " + corners.joinToString { v -> "(${(0..2).map { pos(v, it) }} ${uv(v, 0)},${uv(v, 1)} ${attributes(v)})" }
                continue
            }
            val (a, b) = (0..2).filter { it != flatAxis }
            val lo = IntArray(3) { k -> corners.minOf { pos(it, k) }.toInt() }
            val hi = IntArray(3) { k -> corners.maxOf { pos(it, k) }.toInt() }
            // Coordonnées de texture affines sur la face : origine v0, axes v0→v1 et v0→v3.
            val e1 = FloatArray(3) { pos(v1, it) - pos(v0, it) }
            val e2 = FloatArray(3) { pos(v3, it) - pos(v0, it) }
            fun uvAt(p: FloatArray): Pair<Float, Float> {
                val d = FloatArray(3) { p[it] - pos(v0, it) }
                val s = d.indices.sumOf { (d[it] * e1[it]).toDouble() } / e1.sumOf { (it * it).toDouble() }
                val t = d.indices.sumOf { (d[it] * e2[it]).toDouble() } / e2.sumOf { (it * it).toDouble() }
                val u = uv(v0, 0) + s * (uv(v1, 0) - uv(v0, 0)) + t * (uv(v3, 0) - uv(v0, 0))
                val w = uv(v0, 1) + s * (uv(v1, 1) - uv(v0, 1)) + t * (uv(v3, 1) - uv(v0, 1))
                return frac(u.toFloat()) to frac(w.toFloat())
            }
            for (i in lo[a] until hi[a]) for (j in lo[b] until hi[b]) {
                // Un point décentré du carré : sa coordonnée de texture trahit une rotation ou un miroir.
                val p = FloatArray(3); p[flatAxis] = pos(v0, flatAxis); p[a] = i + .25f; p[b] = j + .125f
                val (u, w) = uvAt(p)
                out += "sq axis=$flatAxis at=${p[flatAxis]} $i,$j uv=${(u * 64).roundToInt()},${(w * 64).roundToInt()} " +
                    "winding=${winding(corners, ::pos, flatAxis)} ${attributes(v0)}"
            }
        }
        return out.sorted()
    }

    private fun frac(x: Float): Float { val f = x - floor(x); return if (abs(f - 1f) < 1e-4f) 0f else f }

    /** Sens de rotation des coins (face avant ou arrière), qui ne doit pas changer. */
    private fun winding(c: IntArray, pos: (Int, Int) -> Float, axis: Int): Int {
        val (a, b) = (0..2).filter { it != axis }
        val ux = pos(c[1], a) - pos(c[0], a); val uy = pos(c[1], b) - pos(c[0], b)
        val vx = pos(c[2], a) - pos(c[0], a); val vy = pos(c[2], b) - pos(c[0], b)
        return if (ux * vy - uy * vx > 0) 1 else -1
    }
}
