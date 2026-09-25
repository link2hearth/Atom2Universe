package com.Atom2Universe.app.games.caves

import org.json.JSONArray
import org.json.JSONObject

/** Persistent stack locations. The existing totals remain the crafting/simulation authority. */
internal class CaveStackInventory(json: String = "[]") {
    data class Stack(val key: Long,val id: Short,val count: Int,val slot: Int = -1)
    private val entries=ArrayList<Stack>()
    private var nextKey=1L
    private var lastTotals: Map<Short,Int>?=null
    private var lastBar: Array<Short?>?=null
    init {
        runCatching { JSONArray(json) }.getOrNull()?.let { rows ->
            val keys=hashSetOf<Long>();val slots=hashSetOf<Int>()
            for(i in 0 until rows.length()) {
                val row=rows.optJSONObject(i) ?: continue
                val key=row.optLong("key");val id=row.optInt("id");val n=row.optInt("count")
                if(key<=0 || key==Long.MAX_VALUE || id !in 1..32767 || n<=0 || !keys.add(key)) continue
                val slot=row.optInt("slot",-1).takeIf { it in 0 until CaveActivity.ACTIVE_SIZE && slots.add(it) } ?: -1
                entries+=Stack(key,id.toShort(),n,slot);nextKey=maxOf(nextKey,key+1)
            }
        }
    }
    @Synchronized fun snapshot(): List<Stack> = entries.toList()
    @Synchronized fun get(key: Long): Stack? = entries.firstOrNull { it.key==key }
    @Synchronized fun at(slot: Int): Stack? = entries.firstOrNull { it.slot==slot }
    @Synchronized fun writeBar(bar: Array<Short?>) {
        bar.fill(null);for(e in entries) if(e.slot in bar.indices) bar[e.slot]=e.id
    }
    @Synchronized fun reconcile(totals: Map<Short,Int>,bar: Array<Short?>?=null,preferredSlot: Int=-1): Boolean {
        if(lastTotals==totals && (if(bar==null) lastBar==null else lastBar?.contentEquals(bar)==true)) return false
        val previousBar=entries.filter { it.slot>=0 }.associateBy { it.slot }
        val allocated=entries.groupBy { it.id }.mapValues { (_,rows)->rows.sumOf { it.count.toLong() } }
        for(id in allocated.keys+totals.keys) {
            val target=(totals[id] ?: 0).coerceAtLeast(0).toLong()
            val delta=target-(allocated[id] ?: 0L)
            if(delta>0) receive(id,delta.toInt())
            else if(delta<0) {
                var remaining=-delta
                val ordered=entries.filter { it.id==id }.sortedBy {
                    when { preferredSlot>=0 && it.slot==preferredSlot -> 0;it.slot<0 -> 1;else -> 2 }
                }
                for(e in ordered) {
                    val n=minOf(remaining,e.count.toLong()).toInt();take(e.key,n);remaining-=n
                    if(remaining==0L) break
                }
            }
        }
        if(bar!=null) {
            for((slot,old) in previousBar) if(slot in bar.indices && at(slot)==null && bar[slot]==old.id) bar[slot]=null
            // External gameplay actions may replace a held item (bucket, equipment, starter kit).
            for(slot in bar.indices) {
                val expected=bar[slot];val current=at(slot)
                if(current?.id==expected) continue
                if(current!=null) moveToBag(current.key)
                if(expected!=null) entries.firstOrNull { it.id==expected && it.slot<0 }?.let { moveToBar(it.key,slot) }
            }
            writeBar(bar)
        } else {
            for(i in entries.indices) if(entries[i].slot>=0) entries[i]=entries[i].copy(slot=-1)
        }
        lastTotals=totals.toMap();lastBar=bar?.copyOf()
        return true
    }
    @Synchronized fun take(key: Long,requested: Int): Int {
        val index=entries.indexOfFirst { it.key==key };if(index<0 || requested<=0) return 0
        val old=entries[index];val amount=minOf(requested,old.count)
        if(amount==old.count) entries.removeAt(index) else entries[index]=old.copy(count=old.count-amount)
        return amount
    }
    @Synchronized fun receive(id: Short,count: Int,targetKey: Long?=null,slot: Int?=null,bagOnly: Boolean=false): Long {
        require(count>0)
        val merge=(targetKey?.let(::get)?.takeIf { it.id==id }
            ?: slot?.let(::at)?.takeIf { it.id==id }
            ?: if(targetKey==null && slot==null) entries.firstOrNull { it.id==id && it.slot<0 }
                ?: if(!bagOnly) entries.firstOrNull { it.id==id } else null else null)
        if(merge!=null && merge.count.toLong()+count<=Int.MAX_VALUE) {
            val index=entries.indexOfFirst { it.key==merge.key }
            entries[index]=merge.copy(count=merge.count+count);return merge.key
        }
        val key=nextKey++;entries+=Stack(key,id,count)
        if(slot!=null) moveToBar(key,slot)
        return key
    }
    @Synchronized fun split(key: Long,count: Int): Boolean {
        val index=entries.indexOfFirst { it.key==key };if(index<0) return false
        val old=entries[index];if(count !in 1 until old.count) return false
        entries[index]=old.copy(count=old.count-count)
        entries.add(index+1,Stack(nextKey++,old.id,count))
        return true
    }
    @Synchronized fun moveToBag(key: Long,targetKey: Long?=null): Boolean {
        val index=entries.indexOfFirst { it.key==key };if(index<0) return false
        val old=entries[index]
        val target=targetKey?.let(::get)?.takeIf { it.key!=key && it.slot<0 && it.id==old.id }
        if(target!=null && target.count.toLong()+old.count<=Int.MAX_VALUE) {
            take(key,old.count);receive(old.id,old.count,target.key)
        } else entries[index]=old.copy(slot=-1)
        return true
    }
    @Synchronized fun moveToBar(key: Long,slot: Int): Boolean {
        if(slot !in 0 until CaveActivity.ACTIVE_SIZE) return false
        val index=entries.indexOfFirst { it.key==key };if(index<0) return false
        val old=entries[index];if(old.slot==slot) return true
        val target=at(slot)
        if(target!=null && target.id==old.id && target.count.toLong()+old.count<=Int.MAX_VALUE) {
            take(key,old.count)
            val targetIndex=entries.indexOfFirst { it.key==target.key }
            entries[targetIndex]=target.copy(count=target.count+old.count)
        } else {
            if(target!=null) entries[entries.indexOfFirst { it.key==target.key }]=target.copy(slot=old.slot)
            entries[index]=old.copy(slot=slot)
        }
        return true
    }
    @Synchronized fun json(): String = JSONArray().apply {
        entries.forEach { put(JSONObject().put("key",it.key).put("id",it.id.toInt()).put("count",it.count).put("slot",it.slot)) }
    }.toString()
}
