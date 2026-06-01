package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry

internal object LodBuilder {

    fun buildColumn(cx: Int, cz: Int, world: World, cache: LodCache? = null): FloatArray {
        val H = CHUNK_SIZE
        val buf = Buf()

        // ── Heightmap ────────────────────────────────────────────────────────
        // Hauteur (absY du bloc le plus haut) et type pour chaque cellule (lx, lz).
        // Int.MIN_VALUE = colonne vide ou non générée.
        val heights: IntArray
        val topBlocks: ShortArray

        val cached = cache?.get(cx, cz)
        if (cached != null) {
            heights   = IntArray(H * H) { i ->
                cached.heights[i].let { h -> if (h == Short.MIN_VALUE) Int.MIN_VALUE else h.toInt() }
            }
            topBlocks = cached.blocks
        } else {
            heights   = IntArray(H * H) { Int.MIN_VALUE }
            topBlocks = ShortArray(H * H) { AIR }
            for (lz in 0 until H) for (lx in 0 until H) {
                val idx = lz * H + lx
                for (cy in 9 downTo -2) {
                    if (heights[idx] != Int.MIN_VALUE) break
                    val chunk = world.getChunk(cx, cy, cz) ?: continue
                    if (!chunk.generated) continue
                    for (ly in H - 1 downTo 0) {
                        val b = chunk.blockAt(lx, ly, lz)
                        if (b == AIR || isDecoration(b) || isWater(b)) continue
                        heights[idx]   = cy * H + ly
                        topBlocks[idx] = b
                        break
                    }
                }
            }
            if (cache != null) {
                val shortH = ShortArray(H * H) { i ->
                    heights[i].let { if (it == Int.MIN_VALUE) Short.MIN_VALUE else it.toShort() }
                }
                cache.put(cx, cz, shortH, topBlocks.copyOf())
            }
        }

        // ── Faces supérieures ────────────────────────────────────────────────
        for (lz in 0 until H) for (lx in 0 until H) {
            val h = heights[lz * H + lx]
            if (h == Int.MIN_VALUE) continue
            val p = topLayer(topBlocks[lz * H + lx]).toFloat()   // faceDir=0 → light 1.0
            val x = lx.toFloat(); val z = lz.toFloat(); val y1 = (h + 1).toFloat()
            buf.add6(x,    y1, z,    0f, 0f, p); buf.add6(x+1f, y1, z,    1f, 0f, p)
            buf.add6(x+1f, y1, z+1f, 1f, 1f, p); buf.add6(x,    y1, z,    0f, 0f, p)
            buf.add6(x+1f, y1, z+1f, 1f, 1f, p); buf.add6(x,    y1, z+1f, 0f, 1f, p)
        }

        // ── Faces latérales : comble l'écart de hauteur avec les voisins ─────
        // packed = layer + faceDir*128 (même encodage que MeshBuilder / VERT_WORLD).
        // faceDir 2/3 → côtés X (lumière 0.72), faceDir 4/5 → côtés Z (lumière 0.62).
        for (lz in 0 until H) for (lx in 0 until H) {
            val h = heights[lz * H + lx]
            if (h == Int.MIN_VALUE) continue
            val block = topBlocks[lz * H + lx]
            val y1 = (h + 1).toFloat()

            // +X  (faceDir 2 → 0.72)
            val hPX = if (lx < H - 1) heights[lz * H + lx + 1]
                      else adjHeight(cx + 1, cz, 0, lz, world, cache)
            if (hPX != Int.MIN_VALUE && hPX < h) {
                val yb = (hPX + 1).toFloat()
                val p = (sideLayer(block) + 2 * 4096).toFloat()
                val x = (lx + 1).toFloat(); val z = lz.toFloat()
                buf.add6(x, y1, z,    0f, 0f, p); buf.add6(x, y1, z+1f, 1f, 0f, p)
                buf.add6(x, yb, z+1f, 1f, 1f, p); buf.add6(x, y1, z,    0f, 0f, p)
                buf.add6(x, yb, z+1f, 1f, 1f, p); buf.add6(x, yb, z,    0f, 1f, p)
            }

            // -X  (faceDir 3 → 0.72)
            val hMX = if (lx > 0) heights[lz * H + lx - 1]
                      else adjHeight(cx - 1, cz, H - 1, lz, world, cache)
            if (hMX != Int.MIN_VALUE && hMX < h) {
                val yb = (hMX + 1).toFloat()
                val p = (sideLayer(block) + 3 * 4096).toFloat()
                val x = lx.toFloat(); val z = lz.toFloat()
                buf.add6(x, y1, z+1f, 0f, 0f, p); buf.add6(x, y1, z,    1f, 0f, p)
                buf.add6(x, yb, z,    1f, 1f, p); buf.add6(x, y1, z+1f, 0f, 0f, p)
                buf.add6(x, yb, z,    1f, 1f, p); buf.add6(x, yb, z+1f, 0f, 1f, p)
            }

            // +Z  (faceDir 4 → 0.62)
            val hPZ = if (lz < H - 1) heights[(lz + 1) * H + lx]
                      else adjHeight(cx, cz + 1, lx, 0, world, cache)
            if (hPZ != Int.MIN_VALUE && hPZ < h) {
                val yb = (hPZ + 1).toFloat()
                val p = (sideLayer(block) + 4 * 4096).toFloat()
                val x = lx.toFloat(); val z = (lz + 1).toFloat()
                buf.add6(x+1f, y1, z, 0f, 0f, p); buf.add6(x,    y1, z, 1f, 0f, p)
                buf.add6(x,    yb, z, 1f, 1f, p); buf.add6(x+1f, y1, z, 0f, 0f, p)
                buf.add6(x,    yb, z, 1f, 1f, p); buf.add6(x+1f, yb, z, 0f, 1f, p)
            }

            // -Z  (faceDir 5 → 0.62)
            val hMZ = if (lz > 0) heights[(lz - 1) * H + lx]
                      else adjHeight(cx, cz - 1, lx, H - 1, world, cache)
            if (hMZ != Int.MIN_VALUE && hMZ < h) {
                val yb = (hMZ + 1).toFloat()
                val p = (sideLayer(block) + 5 * 4096).toFloat()
                val x = lx.toFloat(); val z = lz.toFloat()
                buf.add6(x,    y1, z, 0f, 0f, p); buf.add6(x+1f, y1, z, 1f, 0f, p)
                buf.add6(x+1f, yb, z, 1f, 1f, p); buf.add6(x,    y1, z, 0f, 0f, p)
                buf.add6(x+1f, yb, z, 1f, 1f, p); buf.add6(x,    yb, z, 0f, 1f, p)
            }
        }

        return buf.toArray()
    }

    // Hauteur du voisin : chunk monde en priorité, sinon cache LOD.
    private fun adjHeight(cx: Int, cz: Int, lx: Int, lz: Int, world: World, cache: LodCache?): Int {
        val worldH = columnHeight(cx, cz, lx, lz, world)
        if (worldH != Int.MIN_VALUE) return worldH
        val entry = cache?.get(cx, cz) ?: return Int.MIN_VALUE
        val idx = lz * CHUNK_SIZE + lx
        val h = entry.heights[idx]
        return if (h == Short.MIN_VALUE) Int.MIN_VALUE else h.toInt()
    }

    // Hauteur du bloc le plus haut en (lx, lz) dans la colonne d'un chunk adjacent.
    private fun columnHeight(cx: Int, cz: Int, lx: Int, lz: Int, world: World): Int {
        for (cy in 9 downTo -2) {
            val chunk = world.getChunk(cx, cy, cz) ?: continue
            if (!chunk.generated) continue
            for (ly in CHUNK_SIZE - 1 downTo 0) {
                val b = chunk.blockAt(lx, ly, lz)
                if (b == AIR || isDecoration(b) || isWater(b)) continue
                return cy * CHUNK_SIZE + ly
            }
        }
        return Int.MIN_VALUE
    }

    private fun topLayer(b: Short): Int = BlockRegistry.getLayerForFace(b, 0, AIR)

    private fun sideLayer(b: Short): Int = BlockRegistry.getLayerForFace(b, 2, AIR)

    private class Buf(cap: Int = 8192) {
        private var data = FloatArray(cap); private var n = 0
        fun add(v: Float) { if (n == data.size) data = data.copyOf(n * 2); data[n++] = v }
        fun add6(x:Float,y:Float,z:Float,u:Float,v:Float,p:Float) { add(x);add(y);add(z);add(u);add(v);add(p) }
        fun toArray() = data.copyOf(n)
    }
}
