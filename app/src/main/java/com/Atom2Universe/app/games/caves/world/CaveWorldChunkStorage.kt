package com.Atom2Universe.app.games.caves.world

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class CaveWorldChunkStorage(worldDir: File) {

    private val diffsDir = File(worldDir, "chunks").also { it.mkdirs() }
    private val executor = Executors.newSingleThreadExecutor()

    // Cache mémoire : clé "cx_cy_cz" → (localIndex → blockType)
    // Chargé paresseusement depuis le disque à la première utilisation par chunk.
    private val cache = ConcurrentHashMap<String, ConcurrentHashMap<Int, Byte>>()

    private fun cacheKey(cx: Int, cy: Int, cz: Int) = "${cx}_${cy}_${cz}"
    private fun diffFile(cx: Int, cy: Int, cz: Int) = File(diffsDir, "${cx}_${cy}_${cz}.diff")

    // Applique les modifications du joueur sur un chunk fraîchement généré depuis la seed.
    fun applyDiff(chunk: Chunk) {
        val diff = loadDiff(chunk.cx, chunk.cy, chunk.cz) ?: return
        diff.forEach { (idx, type) -> chunk.blocks[idx] = type }
    }

    // Enregistre une modification et déclenche une sauvegarde async.
    fun recordChange(cx: Int, cy: Int, cz: Int, localIndex: Int, type: Byte) {
        val k = cacheKey(cx, cy, cz)
        cache.getOrPut(k) { ConcurrentHashMap() }[localIndex] = type
        // Snapshot immutable pour l'écriture async (le cache peut continuer à muter)
        val snapshot = HashMap(cache[k]!!)
        executor.execute { writeDiff(cx, cy, cz, snapshot) }
    }

    private fun loadDiff(cx: Int, cy: Int, cz: Int): Map<Int, Byte>? {
        val k = cacheKey(cx, cy, cz)
        cache[k]?.let { return it }   // déjà en mémoire

        val f = diffFile(cx, cy, cz)
        if (!f.exists()) return null

        return runCatching {
            val map = ConcurrentHashMap<Int, Byte>()
            GZIPInputStream(f.inputStream().buffered()).use { gz ->
                DataInputStream(gz).use { din ->
                    val count = din.readUnsignedShort()
                    repeat(count) {
                        val idx  = din.readUnsignedShort()
                        val type = din.readByte()
                        map[idx] = type
                    }
                }
            }
            cache[k] = map
            map
        }.getOrNull()
    }

    private fun writeDiff(cx: Int, cy: Int, cz: Int, diff: Map<Int, Byte>) {
        runCatching {
            diffFile(cx, cy, cz).outputStream().buffered().use { os ->
                GZIPOutputStream(os).use { gz ->
                    DataOutputStream(gz).use { dout ->
                        dout.writeShort(diff.size)
                        diff.forEach { (idx, type) ->
                            dout.writeShort(idx)
                            dout.writeByte(type.toInt())
                        }
                    }
                }
            }
        }
    }

    fun shutdown() { executor.shutdown() }
}
