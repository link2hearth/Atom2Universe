package com.Atom2Universe.app.games.hamlet

import com.Atom2Universe.app.games.hamlet.AdventureResource.*

/** A single receipt shared by the simulation and every cost display. Payments are atomic. */
data class VillageCost(val coins: Long = 0, val gems: Int = 0,
    val resources: Map<AdventureResource, Long> = emptyMap(),
    val crops: Map<String, Int> = emptyMap(), val supplies: Int = 0) {
    fun canPay(g: HamletGame): Boolean = g.sandbox || g.coins >= coins && g.gems >= gems &&
        g.journey.supplies >= supplies && resources.all { (r, n) -> g.adventure[r] >= n } &&
        crops.all { (id, n) -> g.stock(id) >= n }

    fun pay(g: HamletGame): Boolean {
        if (!canPay(g)) return false
        if (!g.sandbox) {
            g.coins -= coins; g.gems -= gems; g.journey.supplies -= supplies
            resources.forEach { (r, n) -> g.adventure.bag[r.ordinal] -= n }
            crops.forEach { (id, n) -> g.pantry[id] = g.stock(id) - n }
        }
        return true
    }
}

/** Village inputs, manufactured goods and repeatable outlets. No daily purchase or farming limit. */
object HamletIndustry {
    data class Recipe(val cost: VillageCost, val output: Map<AdventureResource, Long>)

    fun building(d: Def): VillageCost {
        val structural = d.cat in setOf(Cat.HOME, Cat.SHOP, Cat.RANCH, Cat.FIELD)
        val size = d.w * d.d
        val amount = (size * (2L + d.lvl.toLong() * d.lvl / 2)).coerceAtLeast(4)
        val materials = if (!structural) linkedMapOf() else linkedMapOf(TIMBER to amount, STONE to (amount * 3 / 4).coerceAtLeast(2))
        // The smith itself must remain constructible without tools; the adventure forge opens at level 3.
        if (structural && d.lvl >= 8 && d.id != "blacksmith") materials[TOOLS] = (size * (d.lvl - 5L) / 6).coerceAtLeast(1)
        return VillageCost(coins = if (d.gems == 0) d.cost.toLong() else 0, gems = d.gems, resources = materials)
    }

    fun upgrade(o: Obj): VillageCost {
        val tier = o.tier + 1L
        val materials = building(o.def).resources.mapValues { (_, n) -> n * tier * tier / 2 }.toMutableMap()
        if (tier >= 3) { materials[ORE] = (o.def.lvl + 2) * tier * tier; materials[TOOLS] = (o.def.lvl + 2) * tier }
        return VillageCost(coins = HamletEconomy.upgradeCoins(o.def.cost, o.tier).toLong(), resources = materials,
            crops = HamletEconomy.upgradeNeeds(o.def, o.tier + 1).associate { it.crop to it.units })
    }

    fun hall(g: HamletGame): VillageCost {
        val rank = g.hall.toLong()
        return VillageCost(coins = g.nextHall()?.cost?.toLong() ?: 0,
            resources = linkedMapOf(TIMBER to 160 * rank * rank * rank, STONE to 200 * rank * rank * rank,
                ORE to if (rank >= 3) 40 * rank * rank else 0, TOOLS to if (rank >= 3) 4 * rank * rank else 0))
    }

    fun land(g: HamletGame): VillageCost {
        val rank = g.expand + 1L
        return VillageCost(coins = g.expandCost().toLong(), resources = mapOf(TIMBER to 40 * rank * rank, STONE to 60 * rank * rank))
    }

    /** Rank is captured when accepting the order: upgrading a busy shop cannot create free goods. */
    fun recipe(id: String, rank: Int): Recipe {
        val n = rank.coerceIn(1, HamletEconomy.MAX_TIER).toLong()
        fun recipe(inputs: Map<AdventureResource, Long> = emptyMap(), crops: Map<String, Int> = emptyMap(),
            outputs: Map<AdventureResource, Long> = emptyMap()) = Recipe(
                VillageCost(resources = inputs.mapValues { it.value * n }, crops = crops.mapValues { it.value * n.toInt() }),
                outputs.mapValues { it.value * n })
        val batches = ((Hamlet.byId[id]?.job ?: 900) / 900).coerceAtLeast(1).toLong()
        return when (id) {
            "bakery" -> recipe(mapOf(TIMBER to 2), mapOf("wheat" to 2), mapOf(BREAD to 4))
            "lumbermill" -> recipe(outputs = mapOf(TIMBER to 45))
            "quarry", "stonemason" -> recipe(outputs = mapOf(STONE to 45 * batches))
            "mine" -> recipe(mapOf(TIMBER to 60, FOOD to 80), outputs = mapOf(ORE to 1200, STONE to 600))
            "blacksmith", "adventureForge", "armory" -> recipe(mapOf(TIMBER to 8 * batches, ORE to 4 * batches), outputs = mapOf(TOOLS to 2 * batches))
            "library", "academy", "adventureAcademy", "grandacademy", "cartographers" ->
                recipe(mapOf(TIMBER to 10 * batches, FOOD to 4 * batches), outputs = mapOf(BOOKS to 16 * batches))
            "adventureInn" -> recipe(mapOf(BREAD to 2), outputs = mapOf(FOOD to 35))
            "adventureStore" -> recipe(mapOf(TIMBER to 12, STONE to 12), outputs = mapOf(ORE to 12))
            "adventureYard", "barracks" -> recipe(mapOf(BREAD to 2, TIMBER to 6), outputs = mapOf(FOOD to 40))
            "adventureGate", "waypost" -> recipe(mapOf(BREAD to 2, TIMBER to 8), outputs = mapOf(BOOKS to 12))
            // Other trades consume provisions; they no longer magically manufacture books.
            else -> recipe(mapOf(FOOD to 2))
        }
    }

    fun feed(o: Obj) = VillageCost(crops = mapOf("corn" to (1 + (HamletLife.produce[o.t]?.feed ?: 0) / 20)))
    fun productFood(o: Obj) = if (o.def.cat == Cat.RANCH) 12L * o.tier else 4L * o.tier

    val rations = VillageCost(resources = mapOf(BREAD to 2))
    val builders = VillageCost(resources = mapOf(BREAD to 8, TIMBER to 120, STONE to 120, TOOLS to 2))
    val caravan = VillageCost(resources = mapOf(BREAD to 80, TIMBER to 600, STONE to 600, BOOKS to 150, TOOLS to 20), supplies = 40)

    fun prepareRations(g: HamletGame): Boolean {
        if (!rations.pay(g)) return false
        g.adventure.add(FOOD, 40); g.stat("supply_crafted"); g.listener?.onChanged(); return true
    }
    fun supplyBuilders(g: HamletGame): Boolean {
        // Storage capacity is not a quota: spend the work and provision another crew immediately.
        if (g.workSeconds > 3600 || !builders.pay(g)) return false
        g.workSeconds += 3600; g.stat("supply_crafted"); g.listener?.onChanged(); return true
    }
    fun welcomeCaravan(g: HamletGame): Boolean {
        if (!g.sandbox && g.adventure.facility("adventureInn") == 0 || !caravan.pay(g)) return false
        g.adventure.add(TICKETS, 1); g.stat("supply_crafted"); g.listener?.onChanged(); return true
    }
}
