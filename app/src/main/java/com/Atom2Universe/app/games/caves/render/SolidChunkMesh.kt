package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.world.PackedMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Maillage d'un chunk solide sur la carte graphique, au format tassé de [PackedMesh] : un tampon de
 * sommets et un tampon d'indices. Le shader du monde ramène positions et coordonnées de texture à
 * leur échelle par `u_posScale` / `u_uvScale`.
 */
internal class SolidChunkMesh {
    private var vbo = 0
    private var ibo = 0
    private var indexCount = 0
    private var wideIndices = false
    private var ready = false

    @Volatile private var pending: PackedMesh? = null

    fun upload(mesh: PackedMesh) { pending = mesh }

    /** Fil GL uniquement. */
    fun flushPending() {
        val mesh = pending ?: return
        pending = null
        indexCount = mesh.indexCount
        wideIndices = mesh.wideIndices
        ready = true
        if (mesh.indexCount == 0) return
        if (vbo == 0) {
            val ids = IntArray(2)
            GLES30.glGenBuffers(2, ids, 0)
            vbo = ids[0]; ibo = ids[1]
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.vertices.size, staging(mesh.vertices), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size, staging(mesh.indices), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw(aPos: Int, aUv: Int, aSky: Int, aTint: Int, aBlock: Int) {
        if (!ready || indexCount == 0) return
        val s = PackedMesh.STRIDE
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_SHORT, false, s, 0)
        GLES30.glEnableVertexAttribArray(aUv)
        GLES30.glVertexAttribPointer(aUv, 3, GLES30.GL_UNSIGNED_SHORT, false, s, 8)
        if (aSky >= 0) {
            GLES30.glEnableVertexAttribArray(aSky)
            GLES30.glVertexAttribPointer(aSky, 1, GLES30.GL_UNSIGNED_BYTE, true, s, 14)
        }
        if (aBlock >= 0) {
            GLES30.glEnableVertexAttribArray(aBlock)
            GLES30.glVertexAttribPointer(aBlock, 1, GLES30.GL_UNSIGNED_BYTE, true, s, 15)
        }
        if (aTint >= 0) {
            GLES30.glEnableVertexAttribArray(aTint)
            GLES30.glVertexAttribPointer(aTint, 4, GLES30.GL_HALF_FLOAT, false, s, 16)
        }
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount,
            if (wideIndices) GLES30.GL_UNSIGNED_INT else GLES30.GL_UNSIGNED_SHORT, 0)
        // Ce qui se dessine ensuite sans maillage de chunk n'a ni teinte ni lumière cuite.
        if (aTint >= 0) { GLES30.glDisableVertexAttribArray(aTint); GLES30.glVertexAttrib4f(aTint, 0f, 0f, 0f, 0f) }
        if (aBlock >= 0) { GLES30.glDisableVertexAttribArray(aBlock); GLES30.glVertexAttrib1f(aBlock, 0f) }
        if (aSky >= 0) GLES30.glDisableVertexAttribArray(aSky)
        GLES30.glDisableVertexAttribArray(aUv)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun destroy() {
        if (vbo != 0) {
            GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
            vbo = 0; ibo = 0
        }
        ready = false
    }

    private companion object {
        // Un seul tampon de transfert, agrandi au besoin : flushPending tourne toujours sur le fil GL.
        private var shared: ByteBuffer? = null
        fun staging(bytes: ByteArray): ByteBuffer {
            val current = shared
            val buffer = if (current != null && current.capacity() >= bytes.size) current.also { it.clear() }
                else ByteBuffer.allocateDirect(maxOf(bytes.size, 256 * 1024)).order(ByteOrder.nativeOrder())
                    .also { shared = it }
            buffer.put(bytes).flip()
            return buffer
        }
    }
}
