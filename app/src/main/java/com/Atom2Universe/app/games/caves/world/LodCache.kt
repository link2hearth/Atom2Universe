package com.Atom2Universe.app.games.caves.world

import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Cache persistant des heightmaps LOD (hauteur + type de bloc de surface par cellule).
 * Format binaire : 256×Short hauteur + 256×Short bloc.
 * Short.MIN_VALUE = cellule vide.
 */
class LodCache(private val file: File) {

    data class Entry(val heights: ShortArray, val blocks: ShortArray)

    private val cache = ConcurrentHashMap<Long, Entry>()
    private val dirty = AtomicBoolean(false)

    init { load() }

    fun get(cx: Int, cz: Int): Entry? = cache[key(cx, cz)]

    fun cachedColumns(): List<Pair<Int, Int>> = cache.keys.map { k ->
        val cx = (k and 0xFFFFF).toInt().let { if (it >= 0x80000) it - 0x100000 else it }
        val cz = ((k shr 20) and 0xFFFFF).toInt().let { if (it >= 0x80000) it - 0x100000 else it }
        Pair(cx, cz)
    }

    fun put(cx: Int, cz: Int, heights: ShortArray, blocks: ShortArray) {
        cache[key(cx, cz)] = Entry(heights, blocks)
        dirty.set(true)
    }

    fun invalidate(cx: Int, cz: Int) {
        if (cache.remove(key(cx, cz)) != null) dirty.set(true)
    }

    fun saveIfDirty(scope: CoroutineScope) {
        if (dirty.compareAndSet(true, false))
            scope.launch(Dispatchers.IO) { save() }
    }

    fun shutdown() {
        if (dirty.getAndSet(false)) save()
    }

    private fun key(cx: Int, cz: Int): Long =
        (cx.toLong() and 0xFFFFF) or ((cz.toLong() and 0xFFFFF) shl 20)

    private fun load() {
        if (!file.exists()) return
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { dis ->
                if (dis.readInt() != MAGIC)   return
                if (dis.readInt() != VERSION) return
                val count = dis.readInt()
                repeat(count) {
                    val cx = dis.readInt(); val cz = dis.readInt()
                    val n = CHUNK_SIZE * CHUNK_SIZE
                    val heights = ShortArray(n) { dis.readShort() }
                    val blocks  = ShortArray(n) { dis.readShort() }
                    cache[key(cx, cz)] = Entry(heights, blocks)
                }
            }
        } catch (_: Exception) { cache.clear() }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dos ->
                dos.writeInt(MAGIC); dos.writeInt(VERSION)
                val entries = cache.entries.toList()
                dos.writeInt(entries.size)
                for ((k, v) in entries) {
                    val cx = (k and 0xFFFFF).toInt().let { if (it >= 0x80000) it - 0x100000 else it }
                    val cz = ((k shr 20) and 0xFFFFF).toInt().let { if (it >= 0x80000) it - 0x100000 else it }
                    dos.writeInt(cx); dos.writeInt(cz)
                    v.heights.forEach { dos.writeShort(it.toInt()) }
                    v.blocks.forEach  { dos.writeShort(it.toInt()) }
                }
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val MAGIC   = 0x4C4F4448   // "LODH"
        private const val VERSION = 2            // bumped : blocks sont maintenant Short
    }
}
