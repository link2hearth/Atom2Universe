package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class ChunkMesh(private val floatsPerVertex: Int = 6) {
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

        val byteSize = verts.size * 4
        val bb = sharedUploadBuffer(byteSize)
        val floatBuf = bb.asFloatBuffer()
        floatBuf.put(verts)
        floatBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, byteSize, floatBuf, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        vertexCount = verts.size / floatsPerVertex
        ready = true
    }

    companion object {
        // Un seul ByteBuffer partagé — flushPending est toujours appelé depuis le thread GL.
        private var sharedBuf: ByteBuffer? = null
        private fun sharedUploadBuffer(byteSize: Int): ByteBuffer {
            val cur = sharedBuf
            return if (cur != null && cur.capacity() >= byteSize) {
                cur.clear(); cur
            } else {
                ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder()).also { sharedBuf = it }
            }
        }
    }

    fun draw(aPos: Int, aUv: Int, aSky: Int = -1) {
        if (!ready || vertexCount == 0) return
        val stride = floatsPerVertex * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(aUv)
        GLES30.glVertexAttribPointer(aUv,  3, GLES30.GL_FLOAT, false, stride, 12)
        if (aSky >= 0) {
            GLES30.glEnableVertexAttribArray(aSky)
            GLES30.glVertexAttribPointer(aSky, 1, GLES30.GL_FLOAT, false, stride, 24)
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aUv)
        if (aSky >= 0) GLES30.glDisableVertexAttribArray(aSky)
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
