package com.Atom2Universe.app.games.caves.world

/**
 * Propagation de la lumière du ciel (skylight) par chunk, façon Minecraft.
 *
 * Niveaux 0..15. La lumière entre par le bord supérieur (ciel ouvert) et se propage en flood-fill :
 * vers le bas sans atténuation (puits de lumière qui descendent à pleine intensité), dans les autres
 * directions −1 par bloc traversé. Un bloc opaque bloque la lumière.
 *
 * Convergence multi-chunk : [computeSky] amorce la lumière depuis les six voisins (via
 * [World.skyLightAt], qui retombe sur la surface analytique quand le voisin n'est pas chargé), puis
 * renvoie un masque des faces dont la lumière de bord a changé. L'appelant re-met alors ces voisins
 * en file de reconstruction → la lumière se propage de proche en proche sur quelques frames jusqu'au
 * point fixe (itération de Gauss-Seidel, bornée car les niveaux sont dans 0..15).
 */
internal object LightEngine {

    const val MAX_LIGHT = 15

    private const val N = CHUNK_SIZE
    private const val VOL = N * N * N

    private fun idx(lx: Int, ly: Int, lz: Int) = lx + ly * N + lz * N * N

    private fun passable(block: Short): Boolean =
        block == AIR || isTransparent(block) || isDecoration(block) || isWater(block) || isLeaf(block)

    /**
     * Recalcule la skylight de [chunk]. Renvoie 0 si rien n'a changé (lumière stabilisée), sinon un
     * masque : bits 0..5 = faces dont le bord a changé (bit0=+Y, 1=−Y, 2=+X, 3=−X, 4=+Z, 5=−Z) pour
     * propager aux voisins ; bit 6 (0x40) = au moins un voxel a changé → le mesh est périmé.
     */
    fun computeSky(chunk: Chunk, world: World): Int {
        // Scratch alloué localement : négligeable face au FloatArray du mesh, et computeSky est
        // appelé exclusivement depuis le passage de lumière mono-thread (thread GL).
        val scratch = ByteArray(VOL)
        val queue   = IntQueue()

        // ── Amorçage depuis les six voisins ───────────────────────────────────
        for (lz in 0 until N) for (lx in 0 until N) {
            // Face +Y (haut) : flux descendant → 15 conservé sans atténuation.
            seed(chunk, world, scratch, queue, lx, N - 1, lz, lx, N, lz, downward = true)
            // Face −Y (bas) : flux montant → toujours atténué.
            seed(chunk, world, scratch, queue, lx, 0, lz, lx, -1, lz, downward = false)
        }
        for (ly in 0 until N) for (lx in 0 until N) {
            seed(chunk, world, scratch, queue, lx, ly, N - 1, lx, ly, N, downward = false)   // +Z
            seed(chunk, world, scratch, queue, lx, ly, 0,     lx, ly, -1, downward = false)  // −Z
        }
        for (ly in 0 until N) for (lz in 0 until N) {
            seed(chunk, world, scratch, queue, N - 1, ly, lz, N, ly, lz, downward = false)   // +X
            seed(chunk, world, scratch, queue, 0,     ly, lz, -1, ly, lz, downward = false)  // −X
        }

        // ── Flood-fill interne ────────────────────────────────────────────────
        while (queue.isNotEmpty()) {
            val i = queue.poll()
            val level = scratch[i].toInt()
            if (level <= 0) continue
            val lx = i % N
            val ly = (i / N) % N
            val lz = i / (N * N)
            // Bas (descente libre si pleine intensité), puis haut, ±X, ±Z.
            if (ly > 0)     spread(chunk, scratch, queue, lx, ly - 1, lz, if (level == MAX_LIGHT) MAX_LIGHT else level - 1)
            if (ly < N - 1) spread(chunk, scratch, queue, lx, ly + 1, lz, level - 1)
            if (lx > 0)     spread(chunk, scratch, queue, lx - 1, ly, lz, level - 1)
            if (lx < N - 1) spread(chunk, scratch, queue, lx + 1, ly, lz, level - 1)
            if (lz > 0)     spread(chunk, scratch, queue, lx, ly, lz - 1, level - 1)
            if (lz < N - 1) spread(chunk, scratch, queue, lx, ly, lz + 1, level - 1)
        }

        // ── Écriture + détection des changements de bord ──────────────────────
        var faceMask = 0
        val light = chunk.light
        for (lz in 0 until N) for (ly in 0 until N) for (lx in 0 until N) {
            val i = idx(lx, ly, lz)
            val nv = scratch[i].toInt()
            val old = light[i].toInt() and 0x0F
            if (nv == old) continue
            light[i] = ((light[i].toInt() and 0xF0) or nv).toByte()
            faceMask = faceMask or 0x40   // un voxel a changé → mesh périmé
            if (ly == N - 1) faceMask = faceMask or 0x01
            if (ly == 0)     faceMask = faceMask or 0x02
            if (lx == N - 1) faceMask = faceMask or 0x04
            if (lx == 0)     faceMask = faceMask or 0x08
            if (lz == N - 1) faceMask = faceMask or 0x10
            if (lz == 0)     faceMask = faceMask or 0x20
        }
        return faceMask
    }

    // Amorce le voxel de bord (inLx,inLy,inLz) depuis le voxel extérieur (outLx,outLy,outLz).
    private fun seed(
        chunk: Chunk, world: World, scratch: ByteArray, queue: IntQueue,
        inLx: Int, inLy: Int, inLz: Int, outLx: Int, outLy: Int, outLz: Int, downward: Boolean
    ) {
        if (!passable(chunk.blockAt(inLx, inLy, inLz))) return
        val outside = world.skyLightAt(chunk, outLx, outLy, outLz)
        val incoming = if (downward && outside == MAX_LIGHT) MAX_LIGHT else outside - 1
        if (incoming <= 0) return
        val i = idx(inLx, inLy, inLz)
        if (incoming > scratch[i].toInt()) { scratch[i] = incoming.toByte(); queue.add(i) }
    }

    private fun spread(chunk: Chunk, scratch: ByteArray, queue: IntQueue, lx: Int, ly: Int, lz: Int, level: Int) {
        if (level <= 0) return
        if (!passable(chunk.blockAt(lx, ly, lz))) return
        val i = idx(lx, ly, lz)
        if (level > scratch[i].toInt()) { scratch[i] = level.toByte(); queue.add(i) }
    }

    /** File FIFO d'entiers minimale, sans boxing. */
    private class IntQueue {
        private var data = IntArray(8192)
        private var head = 0
        private var tail = 0
        fun isNotEmpty() = head != tail
        fun add(v: Int) {
            if (tail == data.size) {
                if (head > 0) {
                    System.arraycopy(data, head, data, 0, tail - head); tail -= head; head = 0
                } else {
                    data = data.copyOf(data.size * 2)
                }
            }
            data[tail++] = v
        }
        fun poll(): Int = data[head++]
    }
}
