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
    /** Artificial light uses the high nibble and travels through free cells, never solid walls. */
    fun computeBlock(chunk: Chunk, world: World): Int {
        val values=checkNotNull(scratchLocal.get()).also { it.fill(0) }
        val queue=checkNotNull(queueLocal.get()).also { it.clear() }
        fun emit(x: Int,y: Int,z: Int,level: Int) {
            if(level<=0 || x !in 0..15 || y !in 0..15 || z !in 0..15) return
            val i=x+y*16+z*256
            if(level>values[i]) { values[i]=level.toByte();queue.add(i) }
        }
        fun outside(x: Int,y: Int,z: Int): Int {
            val wx=chunk.worldX+x;val wy=chunk.worldY+y;val wz=chunk.worldZ+z
            val c=world.getChunk(Math.floorDiv(wx,16),Math.floorDiv(wy,16),Math.floorDiv(wz,16)) ?: return 0
            if(!c.generated) return 0
            return (c.light[Math.floorMod(wx,16)+Math.floorMod(wy,16)*16+Math.floorMod(wz,16)*256].toInt() ushr 4) and 15
        }
        for(z in 0..15) for(y in 0..15) for(x in 0..15) {
            val block=chunk.blockAt(x,y,z)
            val source=when(block) { TORCH->15;LAVA->12;else->com.Atom2Universe.app.games.caves.node.BlockRegistry.lightEmission(block) }
            emit(x,y,z,source)
            if(!passable(block)) continue
            if(x==0) emit(x,y,z,outside(-1,y,z)-1)
            if(x==15) emit(x,y,z,outside(16,y,z)-1)
            if(y==0) emit(x,y,z,outside(x,-1,z)-1)
            if(y==15) emit(x,y,z,outside(x,16,z)-1)
            if(z==0) emit(x,y,z,outside(x,y,-1)-1)
            if(z==15) emit(x,y,z,outside(x,y,16)-1)
        }
        fun spreadTo(x: Int,y: Int,z: Int,level: Int) {
            if(x in 0..15 && y in 0..15 && z in 0..15 && passable(chunk.blockAt(x,y,z))) emit(x,y,z,level)
        }
        while(queue.isNotEmpty()) {
            val i=queue.poll();val x=i%16;val y=(i/16)%16;val z=i/256;val level=values[i]-1
            if(level<=0) continue
            spreadTo(x+1,y,z,level);spreadTo(x-1,y,z,level);spreadTo(x,y+1,z,level)
            spreadTo(x,y-1,z,level);spreadTo(x,y,z+1,level);spreadTo(x,y,z-1,level)
        }
        val previous=chunk.light;var next: ByteArray?=null;var mask=0
        for(z in 0..15) for(y in 0..15) for(x in 0..15) {
            val i=x+y*16+z*256
            if(((previous[i].toInt() ushr 4) and 15)==values[i].toInt()) continue
            val result=next ?: previous.copyOf().also { next=it }
            result[i]=((previous[i].toInt() and 15) or (values[i].toInt() shl 4)).toByte()
            mask=mask or 64
            if(y==15) mask=mask or 1
            if(y==0) mask=mask or 2
            if(x==15) mask=mask or 4
            if(x==0) mask=mask or 8
            if(z==15) mask=mask or 16
            if(z==0) mask=mask or 32
        }
        next?.let { chunk.light=it }
        return mask
    }

    const val MAX_LIGHT = 15

    private const val N = CHUNK_SIZE
    private const val VOL = N * N * N
    private val scratchLocal = ThreadLocal.withInitial { ByteArray(VOL) }
    private val queueLocal = ThreadLocal.withInitial { IntQueue() }

    private fun idx(lx: Int, ly: Int, lz: Int) = lx + ly * N + lz * N * N

    private fun passable(block: Short): Boolean =
        block == AIR || isTransparent(block) || isDecoration(block) || isWater(block) || isLeaf(block)

    /**
     * Recalcule la skylight de [chunk]. Renvoie 0 si rien n'a changé (lumière stabilisée), sinon un
     * masque : bits 0..5 = faces dont le bord a changé (bit0=+Y, 1=−Y, 2=+X, 3=−X, 4=+Z, 5=−Z) pour
     * propager aux voisins ; bit 6 (0x40) = au moins un voxel a changé → le mesh est périmé.
     */
    fun computeSky(chunk: Chunk, world: World): Int {
        // Buffers réutilisés sur le worker de lumière, distinct du thread GL.
        val scratch = checkNotNull(scratchLocal.get())
        scratch.fill(0)
        val queue = checkNotNull(queueLocal.get())
        queue.clear()
        val neighbors = World.ChunkLookupCache()

        // ── Amorçage depuis les six voisins ───────────────────────────────────
        for (lz in 0 until N) for (lx in 0 until N) {
            // Face +Y (haut) : flux descendant → 15 conservé sans atténuation.
            seed(chunk, world, scratch, queue, lx, N - 1, lz, lx, N, lz, downward = true, neighbors = neighbors)
            // Face −Y (bas) : flux montant → toujours atténué.
            seed(chunk, world, scratch, queue, lx, 0, lz, lx, -1, lz, downward = false, neighbors = neighbors)
        }
        for (ly in 0 until N) for (lx in 0 until N) {
            seed(chunk, world, scratch, queue, lx, ly, N - 1, lx, ly, N, downward = false, neighbors = neighbors)   // +Z
            seed(chunk, world, scratch, queue, lx, ly, 0,     lx, ly, -1, downward = false, neighbors = neighbors)  // −Z
        }
        for (ly in 0 until N) for (lz in 0 until N) {
            seed(chunk, world, scratch, queue, N - 1, ly, lz, N, ly, lz, downward = false, neighbors = neighbors)   // +X
            seed(chunk, world, scratch, queue, 0,     ly, lz, -1, ly, lz, downward = false, neighbors = neighbors)  // −X
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
        var updated: ByteArray? = null
        for (lz in 0 until N) for (ly in 0 until N) for (lx in 0 until N) {
            val i = idx(lx, ly, lz)
            val nv = scratch[i].toInt()
            val old = light[i].toInt() and 0x0F
            if (nv == old) continue
            val output = updated ?: light.copyOf().also { updated = it }
            output[i] = ((light[i].toInt() and 0xF0) or nv).toByte()
            faceMask = faceMask or 0x40   // un voxel a changé → mesh périmé
            if (ly == N - 1) faceMask = faceMask or 0x01
            if (ly == 0)     faceMask = faceMask or 0x02
            if (lx == N - 1) faceMask = faceMask or 0x04
            if (lx == 0)     faceMask = faceMask or 0x08
            if (lz == N - 1) faceMask = faceMask or 0x10
            if (lz == 0)     faceMask = faceMask or 0x20
        }
        // Publication atomique : aucun lecteur ne voit un tableau partiellement recalculé.
        updated?.let { chunk.light = it }
        return faceMask
    }

    // Amorce le voxel de bord (inLx,inLy,inLz) depuis le voxel extérieur (outLx,outLy,outLz).
    private fun seed(
        chunk: Chunk, world: World, scratch: ByteArray, queue: IntQueue,
        inLx: Int, inLy: Int, inLz: Int, outLx: Int, outLy: Int, outLz: Int, downward: Boolean,
        neighbors: World.ChunkLookupCache
    ) {
        if (!passable(chunk.blockAt(inLx, inLy, inLz))) return
        val outside = world.skyLightAt(chunk, outLx, outLy, outLz, neighbors)
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
        fun clear() {
            head = 0
            tail = 0
        }
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
