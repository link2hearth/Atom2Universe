package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class ChunkMesh {
    private var vboId = 0
    private var vertexCount = 0
    private var ready = false

    @Volatile private var pending: FloatArray? = null

    fun upload(vertices: FloatArray) {
        pending = vertices
    }

    // Must be called from the GL thread.
    fun flushPending() {
        val verts = pending ?: return
        pending = null

        if (verts.isEmpty()) {
            vertexCount = 0
            ready = true
            return
        }

        if (vboId == 0) {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            vboId = ids[0]
        }

        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .also { it.put(verts); it.position(0) }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        vertexCount = verts.size / 6  // x,y,z,r,g,b
        ready = true
    }

    fun draw(aPos: Int, aColor: Int) {
        if (!ready || vertexCount == 0) return
        val stride = 6 * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos,   3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(aColor)
        GLES30.glVertexAttribPointer(aColor, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aColor)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun destroy() {
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        ready = false
    }
}
