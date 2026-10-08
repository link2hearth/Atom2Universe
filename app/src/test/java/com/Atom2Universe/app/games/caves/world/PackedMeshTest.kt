package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PackedMeshTest {
    /** Un sommet large : position, uv, face/couche, ciel, teinte ×3, masque, torches. */
    private fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) =
        floatArrayOf(x, y, z, u, v, 2 * 4096f + 17f, .5f, .25f, -.5f, 1.5f, 1f, .8f)

    @Test
    fun aFaceKeepsFourVerticesAndSixIndices() {
        val a = vertex(0f, 1f, 0f, 0f, 0f); val b = vertex(16f, 1f, 0f, 1f, 0f)
        val c = vertex(16f, 1f, 16f, 1f, 1f); val d = vertex(-.0625f, 1f, 16f, 0f, 1f)
        val mesh = PackedMesh.pack(a + b + c + a + c + d, 12)
        assertEquals(4, mesh.vertexCount)
        assertEquals(6, mesh.indexCount)
        assertFalse(mesh.wideIndices)
        val idx = ByteBuffer.wrap(mesh.indices).order(ByteOrder.nativeOrder())
        assertEquals(listOf(0, 1, 2, 0, 2, 3), List(6) { idx.getShort(it * 2).toInt() })

        val vb = ByteBuffer.wrap(mesh.vertices).order(ByteOrder.nativeOrder())
        fun at(v: Int, offset: Int) = v * PackedMesh.STRIDE + offset
        assertEquals(16f, vb.getShort(at(1, 0)) / PackedMesh.POS_SCALE, 1e-3f)
        assertEquals(-.0625f, vb.getShort(at(3, 0)) / PackedMesh.POS_SCALE, 1e-3f)
        assertEquals(1f, (vb.getShort(at(2, 10)).toInt() and 0xFFFF) / PackedMesh.UV_SCALE, 1e-3f)
        assertEquals(2 * 4096 + 17, vb.getShort(at(0, 12)).toInt() and 0xFFFF)
        assertEquals(128, vb.get(at(0, 14)).toInt() and 0xFF)
        assertEquals(204, vb.get(at(0, 15)).toInt() and 0xFF)
        assertEquals(PackedMesh.toHalf(-.5f), vb.getShort(at(0, 18)))
    }

    @Test
    fun cubeSealsStayInTheFacePlaneWithoutIncreasingMeshSize() {
        // Les trois orientations, une face fusionnée et des coordonnées négatives.
        for (normal in 0..2) {
            val axes = (0..2).filter { it != normal }
            fun corner(u: Float, v: Float): FloatArray {
                val p = floatArrayOf(-1f, -1f, -1f)
                p[axes[0]] = u; p[axes[1]] = v
                return vertex(p[0], p[1], p[2], u, v)
            }
            val a = corner(0f, 0f); val b = corner(16f, 0f)
            val c = corner(16f, 8f); val d = corner(0f, 8f)
            val wide = a + b + c + a + c + d
            val plain = PackedMesh.pack(wide, 12)
            val sealed = PackedMesh.pack(wide, 12, sealFromVertex = 0)
            assertEquals(plain.byteSize, sealed.byteSize)
            assertEquals(4, sealed.vertexCount)
            assertEquals(6, sealed.indexCount)
            val vb = ByteBuffer.wrap(sealed.vertices).order(ByteOrder.nativeOrder())
            for (v in 0..3) {
                val edges = vb.getShort(v * PackedMesh.STRIDE + 6).toInt()
                assertEquals("la normale reste fixe", 0, (edges shr (normal * 2)) and 3)
                assertEquals(if (v == 0 || v == 3) 1 else 2, (edges shr (axes[0] * 2)) and 3)
                assertEquals(if (v < 2) 1 else 2, (edges shr (axes[1] * 2)) and 3)
            }
            // Positions, UV, éclairage et indices ne changent pas : seul le champ réservé varie.
            for (i in plain.vertices.indices) if (i % PackedMesh.STRIDE !in 6..7)
                assertEquals(plain.vertices[i], sealed.vertices[i])
            assertEquals(plain.indices.toList(), sealed.indices.toList())
        }
    }

    @Test
    fun spritesAndPartialFacesBeforeTheCubeRangeKeepNoSeal() {
        val a = vertex(0f, 1f, 0f, 0f, 0f); val b = vertex(1f, 1f, 0f, 1f, 0f)
        val c = vertex(1f, 1f, 1f, 1f, 1f); val d = vertex(0f, 1f, 1f, 0f, 1f)
        val quad = a + b + c + a + c + d
        val plain = PackedMesh.pack(quad, 12)
        val mesh = PackedMesh.pack(quad + quad, 12, sealFromVertex = 6)
        assertEquals(plain.vertices.toList(), mesh.vertices.take(plain.vertices.size))
        val vb = ByteBuffer.wrap(mesh.vertices).order(ByteOrder.nativeOrder())
        assertEquals(17, vb.getShort(4 * PackedMesh.STRIDE + 6).toInt()) // moins X, moins Z
    }

    @Test
    fun halfFloatsMatchIeee() {
        assertEquals(0x3C00.toShort(), PackedMesh.toHalf(1f))
        assertEquals(0xB800.toShort(), PackedMesh.toHalf(-.5f))
        assertEquals(0x3E00.toShort(), PackedMesh.toHalf(1.5f))
        assertEquals(0x4000.toShort(), PackedMesh.toHalf(2f))
        assertEquals(0.toShort(), PackedMesh.toHalf(0f))
    }
}
