package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.world.PackedMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Maillage d'un chunk solide sur la carte graphique, au format tassé de [PackedMesh] : un tampon de
 * sommets et un tampon d'indices. Le shader du monde ramène positions et coordonnées de texture à
 * leur échelle par `u_posScale` / `u_uvScale`.
 *
 * Le format des sommets est enregistré une fois pour toutes dans un VAO (vertex array object) :
 * dessiner le chunk ne coûte plus que deux appels (lier le VAO, dessiner) au lieu d'une vingtaine.
 * À 9 000 chunks affichés, ces appels saturaient le fil de rendu (mesuré).
 */
internal class SolidChunkMesh {
    private var vbo = 0
    private var ibo = 0
    private var vao = 0
    private var indexCount = 0
    private var wideIndices = false
    private var ready = false

    @Volatile private var pending: PackedMesh? = null

    fun upload(mesh: PackedMesh) { pending = mesh }

    /** Fil GL uniquement, jamais pendant la boucle de [draw] (un VAO lié y garderait l'IBO). */
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
        // Les tampons gardent leur nom quand leur contenu change : le VAO se prépare une seule fois.
        if (vao == 0) layout?.let { prepareVao(it) }
    }

    private fun prepareVao(a: IntArray) {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        pointers(a[0], a[1], a[2], a[3], a[4])
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun pointers(aPos: Int, aUv: Int, aSky: Int, aTint: Int, aBlock: Int) {
        val s = PackedMesh.STRIDE
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
    }

    /** Laisse son VAO lié : appeler [endDraws] après le dernier chunk. */
    fun draw() {
        if (!ready || indexCount == 0 || vbo == 0) return
        if (vao == 0) prepareVao(layout ?: return)   // maillage envoyé avant le premier shader
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount,
            if (wideIndices) GLES30.GL_UNSIGNED_INT else GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun destroy() {
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
            vbo = 0; ibo = 0
        }
        ready = false
    }

    companion object {
        /** Emplacements a_pos, a_uv, a_skyLight, a_tint, a_blockLight du shader du monde. */
        @Volatile var layout: IntArray? = null

        /** Revient au VAO par défaut : les autres dessins (eau, LOD, entités) décrivent leurs sommets eux-mêmes. */
        fun endDraws() {
            GLES30.glBindVertexArray(0)
            // Ce qui se dessine ensuite sans maillage de chunk n'a ni teinte ni lumière cuite.
            layout?.let { a ->
                if (a[3] >= 0) GLES30.glVertexAttrib4f(a[3], 0f, 0f, 0f, 0f)
                if (a[4] >= 0) GLES30.glVertexAttrib1f(a[4], 0f)
            }
        }

        // Un seul tampon de transfert, agrandi au besoin : flushPending tourne toujours sur le fil GL.
        private var shared: ByteBuffer? = null
        private fun staging(bytes: ByteArray): ByteBuffer {
            val current = shared
            val buffer = if (current != null && current.capacity() >= bytes.size) current.also { it.clear() }
                else ByteBuffer.allocateDirect(maxOf(bytes.size, 256 * 1024)).order(ByteOrder.nativeOrder())
                    .also { shared = it }
            buffer.put(bytes).flip()
            return buffer
        }
    }
}
