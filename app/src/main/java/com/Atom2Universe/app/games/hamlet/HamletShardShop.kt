package com.Atom2Universe.app.games.hamlet

import org.json.JSONObject

data class ShardShopRequirement(val chapter: Int, val frontier: Int, val inn: Int,
    val forge: Int, val academy: Int)

data class ShardShopCost(val runes: Long, val crystal: Long) {
    fun resources() = linkedMapOf(AdventureResource.RUNES to runes, AdventureResource.CRYSTAL to crystal)
}

data class ShardOffer(val hero: Int, val rarity: HeroRarity, val remaining: Int,
    val cost: ShardShopCost?, val unlocked: Boolean)

/** The guild sells personal fragments of heroes already welcomed, never a new hero or universal shard.
 * Stocks are shared by rarity, rather than multiplied by the roster size. Calendar quotas and price
 * progression only move forward; every mutation completes before a notification can re-enter the shop. */
class HamletShardShop(private val g: HamletGame) {
    private var stockDay = -1L
    private var stockWeek = -1L
    private var frontierPriceFloor = 0
    private val dayBought = IntArray(HeroRarity.entries.size)
    private val weekBought = IntArray(HeroRarity.entries.size)

    @Synchronized fun reset() {
        stockDay = -1; stockWeek = -1; frontierPriceFloor = 0
        dayBought.fill(0); weekBought.fill(0)
    }
    private fun ensureState() {
        val day = (g.now() / Hamlet.DAY_MS).coerceIn(0, MAX_CALENDAR_DAY)
        val week = (day + 3) / 7 // Unix epoch was a Thursday; this makes renewal Monday at 00:00 UTC.
        if (day > stockDay) { stockDay = day; dayBought.fill(0) }
        if (week > stockWeek) { stockWeek = week; weekBought.fill(0) }
        frontierPriceFloor = maxOf(frontierPriceFloor, g.adventure.frontier.coerceIn(0, MAX_FRONTIER))
    }
    fun requirements(rarity: HeroRarity) = when (rarity) {
        HeroRarity.COMMON -> ShardShopRequirement(2, 0, 1, 0, 0)
        HeroRarity.RARE -> ShardShopRequirement(3, 40, 1, 1, 0)
        HeroRarity.EPIC -> ShardShopRequirement(5, 120, 1, 2, 0)
        HeroRarity.LEGENDARY -> ShardShopRequirement(8, 500, 1, 3, 0)
        HeroRarity.MYTHIC -> ShardShopRequirement(12, 2000, 1, 3, 3)
    }
    fun unlocked(rarity: HeroRarity): Boolean {
        if (g.sandbox) return true
        val r = requirements(rarity)
        return g.journey.chapter >= r.chapter && g.adventure.frontier >= r.frontier &&
            g.adventure.facilityRank("adventureInn") >= r.inn &&
            g.adventure.facilityRank("adventureForge") >= r.forge &&
            g.adventure.facilityRank("adventureAcademy") >= r.academy
    }
    fun unlocked() = unlocked(HeroRarity.COMMON)
    fun rank() = HeroRarity.entries.lastOrNull { unlocked(it) }?.let { it.ordinal + 1 } ?: 0
    fun dailyStock(rarity: HeroRarity) = when (rarity) {
        HeroRarity.COMMON -> 6; HeroRarity.RARE -> 4; HeroRarity.EPIC -> 2
        HeroRarity.LEGENDARY -> 1; HeroRarity.MYTHIC -> 0
    }
    fun weeklyStock(rarity: HeroRarity) = if (rarity == HeroRarity.MYTHIC) 1 else 0
    /** Shows stock even before a tier opens, so the UI can explain its next progression milestone. */
    @Synchronized fun remaining(rarity: HeroRarity): Int {
        ensureState()
        if (g.sandbox) return MAX_QUANTITY
        return if (rarity == HeroRarity.MYTHIC) (weeklyStock(rarity) - weekBought[rarity.ordinal]).coerceAtLeast(0)
            else (dailyStock(rarity) - dayBought[rarity.ordinal]).coerceAtLeast(0)
    }
    fun remaining(hero: Int) = g.adventure.heroes.getOrNull(hero)?.takeIf { it.owned }?.let { remaining(it.def.rarity) } ?: 0
    @Synchronized fun nextRestock(rarity: HeroRarity): Long {
        ensureState()
        return if (rarity == HeroRarity.MYTHIC) ((stockWeek + 1) * 7 - 3) * Hamlet.DAY_MS
            else (stockDay + 1) * Hamlet.DAY_MS
    }
    /** Runes stay proportional to one ordinary encounter's loot as the campaign advances.
     * The first tiers are roughly two/three encounters per fragment; the elites need four/six/eight. */
    @Synchronized fun cost(hero: Int, quantity: Int = 1): ShardShopCost? {
        if (quantity !in 1..MAX_QUANTITY) return null
        val h = g.adventure.heroes.getOrNull(hero)?.takeIf { it.owned } ?: return null
        ensureState()
        val band = frontierPriceFloor / 10
        val crystalBand = frontierPriceFloor / 100
        val unit = when (h.def.rarity) {
            HeroRarity.COMMON -> ShardShopCost(6L + band * 2L, 0)
            HeroRarity.RARE -> ShardShopCost(10L + band * 3L, 0)
            HeroRarity.EPIC -> ShardShopCost(18L + band * 4L, 1L + crystalBand)
            HeroRarity.LEGENDARY -> ShardShopCost(30L + band * 6L, 3L + crystalBand * 2L)
            HeroRarity.MYTHIC -> ShardShopCost(48L + band * 8L, 6L + crystalBand * 3L)
        }
        return ShardShopCost(unit.runes * quantity, unit.crystal * quantity)
    }
    fun offers(): List<ShardOffer> = g.adventure.heroes.filter { it.owned }.map { h ->
        ShardOffer(h.id, h.def.rarity, remaining(h.id), cost(h.id), unlocked(h.def.rarity))
    }
    @Synchronized fun canBuy(hero: Int, quantity: Int = 1): Boolean {
        val h = g.adventure.heroes.getOrNull(hero)?.takeIf { it.owned } ?: return false
        if (quantity !in 1..MAX_QUANTITY || !unlocked(h.def.rarity) || quantity > remaining(h.id) || h.shards > Long.MAX_VALUE - quantity) return false
        val price = cost(hero, quantity) ?: return false
        return g.sandbox || price.resources().all { (resource, value) -> g.adventure[resource] >= value }
    }
    /** An optional quote rejects a changed displayed price. Failed purchases never charge or grant. */
    @Synchronized fun buy(hero: Int, quantity: Int = 1, expectedCost: ShardShopCost? = null): Boolean {
        if (!canBuy(hero, quantity)) return false
        val h = g.adventure.heroes[hero]
        val price = cost(hero, quantity) ?: return false
        if (expectedCost != null && price != expectedCost) return false
        if (!g.sandbox) {
            for ((resource, amount) in price.resources()) g.adventure.bag[resource.ordinal] -= amount
            if (h.def.rarity == HeroRarity.MYTHIC) weekBought[h.def.rarity.ordinal] += quantity
            else dayBought[h.def.rarity.ordinal] += quantity
        }
        h.shards += quantity
        g.stat("hero_fragments_bought", quantity.toLong())
        g.listener?.onChanged()
        return true
    }
    @Synchronized fun encode(): JSONObject {
        ensureState()
        val daily = JSONObject(); val weekly = JSONObject()
        for (rarity in HeroRarity.entries) {
            daily.put(rarity.name, dayBought[rarity.ordinal])
            weekly.put(rarity.name, weekBought[rarity.ordinal])
        }
        return JSONObject().put("day", stockDay).put("week", stockWeek)
            .put("frontierPriceFloor", frontierPriceFloor).put("daily", daily).put("weekly", weekly)
    }
    @Synchronized fun decode(j: JSONObject?) {
        reset()
        if (j != null) {
            stockDay = j.optLong("day", -1).coerceIn(-1, MAX_CALENDAR_DAY)
            stockWeek = j.optLong("week", -1).coerceIn(-1, (MAX_CALENDAR_DAY + 3) / 7)
            frontierPriceFloor = j.optInt("frontierPriceFloor").coerceIn(0, MAX_FRONTIER)
            for (rarity in HeroRarity.entries) {
                dayBought[rarity.ordinal] = j.optJSONObject("daily")?.optInt(rarity.name)?.coerceIn(0, dailyStock(rarity)) ?: 0
                weekBought[rarity.ordinal] = j.optJSONObject("weekly")?.optInt(rarity.name)?.coerceIn(0, weeklyStock(rarity)) ?: 0
            }
        }
        ensureState()
    }
    @Synchronized fun copyFrom(other: HamletShardShop) {
        if (other === this) return
        stockDay = other.stockDay; stockWeek = other.stockWeek; frontierPriceFloor = other.frontierPriceFloor
        other.dayBought.copyInto(dayBought); other.weekBought.copyInto(weekBought)
    }
    companion object {
        const val MAX_QUANTITY = 100
        private const val MAX_FRONTIER = 1_000_000
        private const val MAX_CALENDAR_DAY = 100_000_000L
    }
}
