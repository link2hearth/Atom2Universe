package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FarmItems
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import org.json.JSONArray
import org.json.JSONObject

/** Household travel and settlement trade. Mutations are made on the game thread. */
internal class FrontierLife(json: String) {
    data class Place(val x: Int,val y: Int,val z: Int)
    /** [stage]: the deepest stage the player must have reached before this offer is on the counter. */
    data class Offer(val cost: Short,val costCount: Int,val result: Short,val count: Int,val stage: Int=0)
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
    /** Deepest stage ever reached: it decides which seeds the farmer sells. */
    var deepest=0
        private set
    init {
        val root=runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        fun place(key: String): Place? = root.optJSONArray(key)?.let { a ->
            if(a.length()!=3) null else Place(a.optInt(0),a.optInt(1),a.optInt(2))
        }
        home=place("home");expedition=place("expedition")
        elapsedMs=root.optLong("elapsed",0L).coerceAtLeast(0L)
        deepest=root.optInt("deepest",0).coerceIn(0,MineralProgression.LAST_STAGE)
        lastTravel=if(root.has("elapsed")) root.optLong("lastTravel",Long.MIN_VALUE/2) else Long.MIN_VALUE/2
        residents=root.optString("residents","[]")
        equipment=root.optString("equipment","{}")
        magazines=root.optString("magazines","{}")
        fisheries=root.optString("fisheries","{}")
        stacks=root.optString("stacks","[]")
    }
    fun bindHome(place: Place) { home=place }
    fun reached(stage: Int) { if(stage>deepest) deepest=stage.coerceAtMost(MineralProgression.LAST_STAGE) }
    fun advance(ms: Long) { elapsedMs+=ms.coerceIn(0L,1000L) }
    fun arrived(from: Place,returningHome: Boolean,now: Long) {
        val h=home
        if(returningHome && (h==null || kotlin.math.abs(from.x-h.x)>8 || kotlin.math.abs(from.z-h.z)>8 || kotlin.math.abs(from.y-h.y)>5)) expedition=from
        lastTravel=now
    }
    /** One seed per lot, cheapest species first; a species shows up once its unlock stage has been reached. */
    private val seedOffers by lazy {
        CoinEconomy.order.indices.map { position ->
            Offer(F.COIN,CoinEconomy.price(position),FarmItems.seed(CoinEconomy.cropAt(position)),CoinEconomy.SEED_LOT,CoinEconomy.unlockStage(position))
        }
    }
    fun offers(role: Int): List<Offer> = when(role) {
        0 -> seedOffers
        else -> emptyList()
    }
    fun trade(role: Int,index: Int,inventory: MutableMap<Short,Int>): Boolean {
        val offer=offers(role).getOrNull(index) ?: return false
        if(offer.stage>deepest || (inventory[offer.cost] ?: 0)<offer.costCount ||
            (inventory[offer.result] ?: 0)>Int.MAX_VALUE-offer.count) return false
        val left=inventory.getValue(offer.cost)-offer.costCount
        if(left==0) inventory.remove(offer.cost) else inventory[offer.cost]=left
        inventory[offer.result]=(inventory[offer.result] ?: 0)+offer.count
        return true
    }
    fun snapshot(): String = JSONObject().apply {
        fun putPlace(key: String,p: Place?) { if(p!=null) put(key,JSONArray().put(p.x).put(p.y).put(p.z)) }
        putPlace("home",home);putPlace("expedition",expedition);put("lastTravel",lastTravel)
        put("elapsed",elapsedMs);put("deepest",deepest)
        put("residents",residents)
        put("equipment",equipment);put("magazines",magazines);put("fisheries",fisheries);put("stacks",stacks)
    }.toString()
}
