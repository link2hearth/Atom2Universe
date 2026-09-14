package com.Atom2Universe.app.games.caves.world

/** Éclairage réellement utilisé par un mesh : son chunk et ses six voisins seulement. */
internal class MeshLightingSnapshot(private val chunk: Chunk, private val world: World) {
    private val neighbors = arrayOf(
        chunk,
        world.getChunk(chunk.cx + 1, chunk.cy, chunk.cz),
        world.getChunk(chunk.cx - 1, chunk.cy, chunk.cz),
        world.getChunk(chunk.cx, chunk.cy + 1, chunk.cz),
        world.getChunk(chunk.cx, chunk.cy - 1, chunk.cz),
        world.getChunk(chunk.cx, chunk.cy, chunk.cz + 1),
        world.getChunk(chunk.cx, chunk.cy, chunk.cz - 1)
    )
    private val lights = Array(neighbors.size) { i ->
        neighbors[i]?.takeIf { it.generated }?.light
    }

    fun isCurrent(): Boolean {
        for (i in neighbors.indices) {
            val dx = if (i == 1) 1 else if (i == 2) -1 else 0
            val dy = if (i == 3) 1 else if (i == 4) -1 else 0
            val dz = if (i == 5) 1 else if (i == 6) -1 else 0
            val current = world.getChunk(chunk.cx + dx, chunk.cy + dy, chunk.cz + dz)
            if (current !== neighbors[i]) return false
            if (current?.takeIf { it.generated }?.light !== lights[i]) return false
        }
        return true
    }
}
