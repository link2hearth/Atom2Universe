package com.Atom2Universe.app.games.caves.world

import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream

/** Edits are committed with the inventory. Legacy chunk files remain read-only migration sources. */
class CaveWorldChunkStorage(private val worldDir: File) {
    private val reader=CaveCheckpoint.Reader(worldDir)
    private data class MutableEdit(val blocks: MutableMap<Int,Short>,val meta: MutableMap<Int,Byte>)
    private val cache=object : LinkedHashMap<String,MutableEdit>(256,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,MutableEdit>?) =
            size>2048 && eldest?.key !in dirty
    }
    private val dirty=hashMapOf<String,Long>()
    private var revision=0L
    private fun key(cx: Int,cy: Int,cz: Int) = "${cx}_${cy}_${cz}"
    /**
     * Le disque (SQLite, anciens fichiers) se lit hors du verrou : un fil de génération qui charge
     * un chunk ne bloque plus le fil GL qui casse un bloc. Deux lectures du même chunk au même
     * moment donnent la même chose ; la première rangée dans le cache gagne.
     */
    private fun load(key: String): MutableEdit {
        synchronized(this) { cache[key]?.let { return it } }
        val fromDisk=read(key)
        synchronized(this) { return cache.getOrPut(key) { fromDisk } }
    }
    private fun read(key: String): MutableEdit {
        val saved=reader.chunk(key)
        return if(saved!=null) MutableEdit(saved.blocks.toMutableMap(),saved.meta.toMutableMap()) else {
            val blocks=mutableMapOf<Int,Short>(); val meta=mutableMapOf<Int,Byte>()
            for(extension in listOf("diff","meta")) {
                val file=File(worldDir,"chunks/$key.$extension")
                if(file.exists()) GZIPInputStream(file.inputStream().buffered()).use { gz -> DataInputStream(gz).use { input ->
                    val count=input.readUnsignedShort();require(count<=4096)
                    repeat(count) {
                        val index=input.readUnsignedShort();require(index<4096)
                        if(extension=="diff") blocks[index]=input.readShort() else meta[index]=input.readByte()
                    }
                } }
            }
            MutableEdit(blocks,meta)
        }
    }
    fun applyDiff(chunk: Chunk) {
        val edit=load(key(chunk.cx,chunk.cy,chunk.cz))
        synchronized(this) { edit.blocks.forEach { (i,v)->chunk.blocks[i]=v } }
    }
    fun applyMetaDiff(chunk: Chunk) {
        val edit=load(key(chunk.cx,chunk.cy,chunk.cz))
        synchronized(this) { edit.meta.forEach { (i,v)->chunk.meta[i]=v } }
    }
    // Sous le verrou, l'entrée est reprise du cache (ou y revient si elle en est sortie entre-temps) :
    // une modification ne doit jamais tomber dans une copie que snapshot() ne verrait pas.
    fun recordChange(cx: Int,cy: Int,cz: Int,localIndex: Int,type: Short) {
        val k=key(cx,cy,cz);val loaded=load(k)
        synchronized(this) { cache.getOrPut(k) { loaded }.blocks[localIndex]=type;dirty[k]=++revision }
    }
    fun recordMetaChange(cx: Int,cy: Int,cz: Int,localIndex: Int,value: Byte) {
        val k=key(cx,cy,cz);val loaded=load(k)
        synchronized(this) { cache.getOrPut(k) { loaded }.meta[localIndex]=value;dirty[k]=++revision }
    }
    // Keep changes until a committed snapshot acknowledges this precise revision.
    @Synchronized internal fun snapshot(): Map<String,CaveCheckpoint.Edit> = dirty.mapValues { (k,v) ->
        val e=cache.getValue(k);CaveCheckpoint.Edit(e.blocks.toMap(),e.meta.toMap(),v)
    }
    @Synchronized internal fun acknowledge(changes: Map<String,CaveCheckpoint.Edit>) {
        for((key,edit) in changes) if(dirty[key]==edit.revision) dirty.remove(key)
        if(cache.size>2048) {
            val entries=cache.iterator()
            while(entries.hasNext() && cache.size>2048) if(entries.next().key !in dirty) entries.remove()
        }
    }
    fun flush() = Unit
    @Synchronized fun shutdown() { reader.close() }
}
