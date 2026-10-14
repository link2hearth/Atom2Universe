package com.Atom2Universe.app.games.hamlet

import kotlin.math.max

/** Progression indépendante des anciennes quêtes : les objectifs restent cumulatifs et permanents.
 * Une expédition rapporte des provisions, jamais une construction automatique dans le village. */
class HamletJourney(private val g: HamletGame) {
    data class Goal(val key: String, val target: Int)
    data class Chapter(val level: Int, val hall: Int, val goals: List<Goal>, val coins: Int, val supplies: Int)
    data class Region(val chapter: Int, val seconds: Int, val power: Int, val coins: Int, val supplies: Int)

    var chapter = 0
    var supplies = 0
    val research = IntArray(3)
    val companions = IntArray(6)
    val party = IntArray(3) { -1 }
    val explored = IntArray(6)
    var expedition = -1
    var departure = 0L
    var arrival = 0L
    var invitationDay = -1L
    var invitationsToday = 0

    fun reset() {
        chapter = 0; supplies = 0; research.fill(0); companions.fill(0); party.fill(-1)
        explored.fill(0); expedition = -1; departure = 0; arrival = 0
        invitationDay = -1; invitationsToday = 0
    }

    fun copyFrom(other: HamletJourney) {
        chapter = other.chapter; supplies = other.supplies
        other.research.copyInto(research); other.companions.copyInto(companions)
        other.party.copyInto(party); other.explored.copyInto(explored)
        expedition = other.expedition; departure = other.departure; arrival = other.arrival
        invitationDay = other.invitationDay; invitationsToday = other.invitationsToday
    }

    fun progress(goal: Goal): Long = when {
        goal.key == "population" -> g.population().toLong()
        goal.key.startsWith("region_") -> explored[goal.key.removePrefix("region_").toInt()].toLong()
        goal.key == "research" -> research.sum().toLong()
        else -> g.stats[goal.key] ?: 0
    }

    fun canClaim(): Boolean = chapters.getOrNull(chapter)?.let { c ->
        g.level() >= c.level && g.hall >= c.hall && c.goals.all { progress(it) >= it.target }
    } ?: false

    fun claim(): Boolean {
        if (!canClaim()) return false
        val c = chapters[chapter]
        chapter++
        supplies += c.supplies
        // Les compagnons arrivent au village au fil des chapitres, sans tirage aléatoire.
        if (chapter % 2 == 1) {
            val hero = chapter / 2
            companions[hero] = 1
            g.adventure.heroes[hero].owned = true
            val slot = party.indexOf(-1)
            if (slot >= 0) party[slot] = hero
        }
        g.reward(null, c.coins, c.coins / 8)
        g.gems += 2
        g.stat("chapters")
        notifyChange()
        return true
    }

    fun buildingChapter(id: String) = buildings[id] ?: 0
    fun unlocked(d: Def) = g.sandbox || (g.level() >= d.lvl && chapter >= buildingChapter(d.id))
    fun hallChapter(target: Int) = when (target) { 6 -> 8; 7 -> 10; 8 -> 12; else -> 0 }
    fun canUpgradeHall() = g.sandbox || chapter >= hallChapter(g.hall + 1)

    fun researchChapter(branch: Int) = 2 + research[branch] * 2
    fun researchCoins(branch: Int) = 400 * (research[branch] + 1) * (research[branch] + 1)
    fun researchSupplies(branch: Int) = 12 * (research[branch] + 1) * (research[branch] + 1)
    fun canResearch(branch: Int) = branch in research.indices && research[branch] < 5 &&
        (g.sandbox || (chapter >= researchChapter(branch) && g.coins >= researchCoins(branch) && supplies >= researchSupplies(branch)))
    fun study(branch: Int): Boolean {
        if (!canResearch(branch)) return false
        val cost = researchCoins(branch)
        if (!g.sandbox) { g.coins -= cost; supplies -= researchSupplies(branch) }
        research[branch]++
        g.addXp(cost / 10); g.stat("studies"); notifyChange()
        return true
    }

    fun trainCoins(hero: Int) = 300 * companions[hero] * companions[hero]
    fun trainSupplies(hero: Int) = 8 * companions[hero] * companions[hero]
    fun canTrain(hero: Int) = hero in companions.indices && companions[hero] in 1..4 &&
        (g.sandbox || (chapter >= companions[hero] * 2 + 1 && g.coins >= trainCoins(hero) && supplies >= trainSupplies(hero)))
    fun train(hero: Int): Boolean {
        if (!canTrain(hero)) return false
        val cost = trainCoins(hero)
        if (!g.sandbox) { g.coins -= cost; supplies -= trainSupplies(hero) }
        companions[hero]++; g.addXp(cost / 10); notifyChange()
        return true
    }

    /** Échanger une place est libre ; l'équipe d'une expédition est figée au départ par ses dates. */
    fun assign(hero: Int, slot: Int): Boolean {
        if (hero !in companions.indices || slot !in party.indices || (companions[hero] == 0 && !g.sandbox)) return false
        if (g.sandbox && companions[hero] == 0) companions[hero] = 1
        val old = party.indexOf(hero)
        if (old >= 0) party[old] = -1
        party[slot] = hero
        notifyChange(); return true
    }

    private fun adventurers() = g.adventure.teams[g.adventure.selectedTeam].take(g.adventure.slotCount())
        .distinct().mapNotNull { g.adventure.heroes.getOrNull(it)?.takeIf { h -> h.owned } }
    fun scoutingPower() = adventurers().sumOf { (it.level - 1) / 5 + it.stars + it.def.rarity.ordinal }.coerceAtMost(20)
    fun scoutingSpeed() = adventurers().filter { it.def.role in setOf(HeroRole.RANGER, HeroRole.ROGUE, HeroRole.SUMMONER) }
        .sumOf { 3 + it.stars }.coerceAtMost(15)
    fun power() = party.filter { it >= 0 }.sumOf { companions[it] * 3 } + research[2] * 4 + chapter + scoutingPower()
    fun bonus(cat: Cat): Int {
        val branch = if (cat == Cat.FIELD || cat == Cat.RANCH || cat == Cat.NATURE) 0 else 1
        return research[branch] * 3 + party.filter { it >= 0 && it % 3 == branch }.sumOf { companions[it] * 3 }
    }
    fun expeditionSeconds(region: Int): Int {
        val scouts = party.filter { it >= 0 && it % 3 == 2 }.sumOf { companions[it] * 3 }
        val reduction = (scouts + research[2] * 3 + scoutingSpeed() + g.adventure.facilityRank("adventureGate") * 3).coerceAtMost(30)
        return regions[region].seconds * (100 - reduction) / 100
    }
    fun canExplore(region: Int) = region in regions.indices && expedition == -1 &&
        (g.sandbox || (chapter >= regions[region].chapter && power() >= regions[region].power))
    fun startExpedition(region: Int): Boolean {
        if (!canExplore(region)) return false
        expedition = region; departure = g.now(); arrival = departure + expeditionSeconds(region) * 1000L
        notifyChange(); return true
    }
    fun expeditionLeft() = if (expedition < 0) 0L else max(0L, arrival - g.now())
    fun collectExpedition(): Boolean {
        val region = expedition
        if (region !in regions.indices || expeditionLeft() > 0) return false
        val r = regions[region]
        val tickets = expeditionTickets(region)
        // Consommer la mission avant toute notification/récompense : jamais deux gains au même retour.
        expedition = -1; departure = 0; arrival = 0
        explored[region]++
        supplies += r.supplies
        g.adventure.add(AdventureResource.FOOD, expeditionFood(region))
        g.adventure.add(AdventureResource.ORE, expeditionOre(region))
        g.adventure.add(AdventureResource.BOOKS, expeditionBooks(region))
        if (tickets > 0) {
            val day = g.now() / Hamlet.DAY_MS
            if (day > invitationDay) { invitationDay = day; invitationsToday = 0 }
            invitationsToday += tickets.toInt()
            g.adventure.add(AdventureResource.TICKETS, tickets)
        }
        g.stat("expeditions"); g.reward(null, r.coins, r.coins / 5)
        notifyChange(); return true
    }
    fun expeditionFood(region: Int) = regions[region].supplies * 5L
    fun expeditionOre(region: Int) = regions[region].supplies * 2L
    fun expeditionBooks(region: Int) = regions[region].supplies.toLong()
    /** Invitations are discoveries, not an unlimited short-route farming currency. */
    fun expeditionTickets(region: Int): Long {
        if (region !in regions.indices) return 0
        val used = if (g.now() / Hamlet.DAY_MS > invitationDay) 0 else invitationsToday
        if (used >= 2) return 0
        return if (explored[region] == 0 || (explored[region] + 1) % 5 == 0) 1 else 0
    }
    private fun notifyChange() { g.listener?.onChanged() }

    companion object {
        private fun goals(vararg pairs: Pair<String, Int>) = pairs.map { Goal(it.first, it.second) }
        val chapters = listOf(
            Chapter(3, 1, goals("harvested" to 8, "built_bench" to 1), 150, 12),
            Chapter(4, 1, goals("population" to 6, "delivered" to 6, "built_waypost" to 1, "region_0" to 2), 400, 24),
            Chapter(6, 2, goals("products" to 15, "built_seedhouse" to 1, "region_1" to 1), 900, 40),
            Chapter(9, 2, goals("delivered" to 80, "upgrades" to 4, "region_1" to 6, "research" to 2), 1800, 60),
            Chapter(12, 3, goals("products" to 100, "built_gardenremedies" to 1, "region_2" to 4), 4000, 90),
            Chapter(15, 3, goals("population" to 24, "built_weavery" to 1, "region_2" to 10, "research" to 4), 8000, 120),
            Chapter(18, 4, goals("delivered" to 500, "built_cartographers" to 1, "region_3" to 6), 15000, 160),
            Chapter(21, 4, goals("products" to 700, "built_prismforge" to 1, "region_3" to 16, "research" to 7), 25000, 200),
            Chapter(25, 5, goals("population" to 55, "built_conservatory" to 1, "region_4" to 8), 45000, 240),
            Chapter(29, 6, goals("upgrades" to 30, "built_starwatch" to 1, "region_4" to 20, "research" to 10), 80000, 300),
            Chapter(33, 6, goals("delivered" to 1800, "built_skygarden" to 1, "region_5" to 5), 140000, 360),
            Chapter(38, 7, goals("population" to 90, "built_grandacademy" to 1, "region_5" to 12, "research" to 13), 250000, 500),
        )
        val regions = listOf(
            Region(0, 15 * 60, 0, 40, 4), Region(2, 3600, 4, 150, 12),
            Region(4, 3 * 3600, 10, 500, 30), Region(6, 6 * 3600, 18, 1400, 65),
            Region(8, 12 * 3600, 28, 3500, 140), Region(10, 24 * 3600, 40, 9000, 300),
        )
        val buildings = linkedMapOf("waypost" to 1, "seedhouse" to 2, "carpenters" to 3,
            "gardenremedies" to 4, "weavery" to 5, "cartographers" to 6, "prismforge" to 7,
            "conservatory" to 8, "starwatch" to 9, "skygarden" to 10, "grandacademy" to 11,
            "festivalhall" to 12)
    }
}
