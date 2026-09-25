package com.Atom2Universe.app.games.caves.world

import org.json.JSONArray
import org.json.JSONObject

/** Household travel and settlement trade. Mutations are made on the game thread. */
internal class FrontierLife(json: String) {
    data class Place(val x: Int,val y: Int,val z: Int)
    data class Offer(val cost: Short,val costCount: Int,val result: Short,val count: Int)
    var home: Place?=null
        private set
    var expedition: Place?=null
        private set
    var lastTravel=Long.MIN_VALUE/2
    var residents="[]"
    var equipment="{}"
    var magazines="{}"
    var fisheries="{}"
    var stacks="[]"
    var elapsedMs=0L
        private set
    private val purchases=mutableMapOf<String,Pair<Long,Int>>()
    init {
        val root=runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        fun place(key: String): Place? = root.optJSONArray(key)?.let { a ->
            if(a.length()!=3) null else Place(a.optInt(0),a.optInt(1),a.optInt(2))
        }
        home=place("home");expedition=place("expedition")
        elapsedMs=root.optLong("elapsed",0L).coerceAtLeast(0L)
        lastTravel=if(root.has("elapsed")) root.optLong("lastTravel",Long.MIN_VALUE/2) else Long.MIN_VALUE/2
        residents=root.optString("residents","[]")
        equipment=root.optString("equipment","{}")
        magazines=root.optString("magazines","{}")
        fisheries=root.optString("fisheries","{}")
        stacks=root.optString("stacks","[]")
        val rows=root.optJSONObject("purchases") ?: JSONObject()
        rows.keys().forEach { key -> rows.optJSONArray(key)?.let { a -> purchases[key]=a.optLong(0) to a.optInt(1).coerceIn(0,8) } }
    }
    fun bindHome(place: Place) { home=place }
    fun advance(ms: Long) { elapsedMs+=ms.coerceIn(0L,1000L) }
    fun arrived(from: Place,returningHome: Boolean,now: Long) {
        val h=home
        if(returningHome && (h==null || kotlin.math.abs(from.x-h.x)>8 || kotlin.math.abs(from.z-h.z)>8 || kotlin.math.abs(from.y-h.y)>5)) expedition=from
        lastTravel=now
    }
    fun offers(role: Int): List<Offer> = when(role) {
        0 -> listOf(Offer(9700,8,9848,2),Offer(9704,6,9848,2),Offer(9852,4,9848,3),
            Offer(9848,2,9607,3),Offer(9848,3,9618,3),Offer(9848,2,9811,2),Offer(9894,3,9848,2),Offer(9896,2,9848,4))
        1 -> listOf(Offer(9850,4,9848,3),Offer(3114,4,9848,3),Offer(9855,3,9848,4),
            Offer(9848,3,9820,2),Offer(9848,4,9846,1),Offer(9848,6,9859,1),Offer(9899,2,9848,3))
        else -> listOf(Offer(9854,4,9848,3),Offer(9860,1,9848,3),Offer(9853,3,9848,3),
            Offer(9848,5,9860,1),Offer(9848,3,9617,4),Offer(9848,6,9861,1))
    }
    fun remaining(shop: String,index: Int,now: Long): Int {
        val p=purchases["$shop:$index"] ?: return 8
        return if(p.first==now/600_000L) 8-p.second else 8
    }
    fun trade(shop: String,role: Int,index: Int,inventory: MutableMap<Short,Int>,now: Long): Boolean {
        val offer=offers(role).getOrNull(index) ?: return false
        if(remaining(shop,index,now)<=0 || (inventory[offer.cost] ?: 0)<offer.costCount ||
            (inventory[offer.result] ?: 0)>Int.MAX_VALUE-offer.count) return false
        val left=inventory.getValue(offer.cost)-offer.costCount
        if(left==0) inventory.remove(offer.cost) else inventory[offer.cost]=left
        inventory[offer.result]=(inventory[offer.result] ?: 0)+offer.count
        val key="$shop:$index";val used=8-remaining(shop,index,now)
        purchases[key]=now/600_000L to used+1
        return true
    }
    fun snapshot(): String = JSONObject().apply {
        fun putPlace(key: String,p: Place?) { if(p!=null) put(key,JSONArray().put(p.x).put(p.y).put(p.z)) }
        putPlace("home",home);putPlace("expedition",expedition);put("lastTravel",lastTravel)
        put("elapsed",elapsedMs)
        put("residents",residents)
        put("equipment",equipment);put("magazines",magazines);put("fisheries",fisheries);put("stacks",stacks)
        put("purchases",JSONObject().also { rows -> purchases.forEach { (key,p)->rows.put(key,JSONArray().put(p.first).put(p.second)) } })
    }.toString()
}
