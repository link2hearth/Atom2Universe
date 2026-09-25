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
    fun halfFloatsMatchIeee() {
        assertEquals(0x3C00.toShort(), PackedMesh.toHalf(1f))
        assertEquals(0xB800.toShort(), PackedMesh.toHalf(-.5f))
        assertEquals(0x3E00.toShort(), PackedMesh.toHalf(1.5f))
        assertEquals(0x4000.toShort(), PackedMesh.toHalf(2f))
        assertEquals(0.toShort(), PackedMesh.toHalf(0f))
    }
}
