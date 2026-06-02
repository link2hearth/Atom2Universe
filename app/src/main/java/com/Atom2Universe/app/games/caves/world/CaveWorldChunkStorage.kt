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

    // Cache mémoire blocs : clé "cx_cy_cz" → (localIndex → blockType)
    private val cache     = ConcurrentHashMap<String, ConcurrentHashMap<Int, Short>>()
    // Cache mémoire meta : clé "cx_cy_cz" → (localIndex → metaByte)
    private val metaCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, Byte>>()

    private fun cacheKey(cx: Int, cy: Int, cz: Int) = "${cx}_${cy}_${cz}"
    private fun diffFile(cx: Int, cy: Int, cz: Int) = File(diffsDir, "${cx}_${cy}_${cz}.diff")
    private fun metaFile(cx: Int, cy: Int, cz: Int) = File(diffsDir, "${cx}_${cy}_${cz}.meta")

    fun applyDiff(chunk: Chunk) {
        val diff = loadDiff(chunk.cx, chunk.cy, chunk.cz) ?: return
        diff.forEach { (idx, type) -> chunk.blocks[idx] = type }
    }

    fun applyMetaDiff(chunk: Chunk) {
        val diff = loadMetaDiff(chunk.cx, chunk.cy, chunk.cz) ?: return
        diff.forEach { (idx, value) -> chunk.meta[idx] = value }
    }

    fun recordChange(cx: Int, cy: Int, cz: Int, localIndex: Int, type: Short) {
        val k = cacheKey(cx, cy, cz)
        cache.getOrPut(k) { ConcurrentHashMap() }[localIndex] = type
        val snapshot = HashMap(cache[k]!!)
        executor.execute { writeDiff(cx, cy, cz, snapshot) }
    }

    fun recordMetaChange(cx: Int, cy: Int, cz: Int, localIndex: Int, value: Byte) {
        val k = cacheKey(cx, cy, cz)
        metaCache.getOrPut(k) { ConcurrentHashMap() }[localIndex] = value
        val snapshot = HashMap(metaCache[k]!!)
        executor.execute { writeMetaDiff(cx, cy, cz, snapshot) }
    }

    private fun loadMetaDiff(cx: Int, cy: Int, cz: Int): Map<Int, Byte>? {
        val k = cacheKey(cx, cy, cz)
        metaCache[k]?.let { return it }
        val f = metaFile(cx, cy, cz)
        if (!f.exists()) return null
        return runCatching {
            val map = ConcurrentHashMap<Int, Byte>()
            GZIPInputStream(f.inputStream().buffered()).use { gz ->
                DataInputStream(gz).use { din ->
                    val count = din.readUnsignedShort()
                    repeat(count) {
                        val idx   = din.readUnsignedShort()
                        val value = din.readByte()
                        map[idx] = value
                    }
                }
            }
            metaCache[k] = map
            map
        }.getOrNull()
    }

    private fun writeMetaDiff(cx: Int, cy: Int, cz: Int, diff: Map<Int, Byte>) {
        runCatching {
            metaFile(cx, cy, cz).outputStream().buffered().use { os ->
                GZIPOutputStream(os).use { gz ->
                    DataOutputStream(gz).use { dout ->
                        dout.writeShort(diff.size)
                        diff.forEach { (idx, value) ->
                            dout.writeShort(idx)
                            dout.writeByte(value.toInt())
                        }
                    }
                }
            }
        }
    }

    private fun loadDiff(cx: Int, cy: Int, cz: Int): Map<Int, Short>? {
        val k = cacheKey(cx, cy, cz)
        cache[k]?.let { return it }

        val f = diffFile(cx, cy, cz)
        if (!f.exists()) return null

        return runCatching {
            val map = ConcurrentHashMap<Int, Short>()
            GZIPInputStream(f.inputStream().buffered()).use { gz ->
                DataInputStream(gz).use { din ->
                    val count = din.readUnsignedShort()
                    repeat(count) {
                        val idx  = din.readUnsignedShort()
                        val type = din.readShort()
                        map[idx] = type
                    }
                }
            }
            cache[k] = map
            map
        }.getOrNull()
    }

    private fun writeDiff(cx: Int, cy: Int, cz: Int, diff: Map<Int, Short>) {
        runCatching {
            diffFile(cx, cy, cz).outputStream().buffered().use { os ->
                GZIPOutputStream(os).use { gz ->
                    DataOutputStream(gz).use { dout ->
                        dout.writeShort(diff.size)
                        diff.forEach { (idx, type) ->
                            dout.writeShort(idx)
                            dout.writeShort(type.toInt())
                        }
                    }
                }
            }
        }
    }

    fun shutdown() { executor.shutdown() }
}
