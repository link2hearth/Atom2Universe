package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject

/** Les mêmes héros aident le village et combattent. Les tâches du jour restent un bonus facultatif. */
class HamletStewardship(private val g: HamletGame) {
    val posts = IntArray(3) { -1 }
    private var day = -1L
    private val baseline = LongArray(3)
    var claimed = 0
        private set
    var chestClaimed = false
        private set

    fun reset() {
        posts.fill(-1); day = -1; baseline.fill(0); claimed = 0; chestClaimed = false
    }
    private fun total(index: Int) = KEYS[index].sumOf { (g.stats[it] ?: 0L).coerceAtLeast(0) }
    // Appelé avant g.stat : le premier geste de la journée compte aussi après une longue absence.
    fun ensureDay() {
        val current = g.now() / Hamlet.DAY_MS
        if (current == day || current < day) return
        day = current; claimed = 0; chestClaimed = false
        for (i in baseline.indices) baseline[i] = total(i)
    }
    fun progress(index: Int): Long {
        ensureDay()
        return if (index in baseline.indices) (total(index) - baseline[index]).coerceAtLeast(0) else 0
    }
    fun canClaim(index: Int): Boolean {
        ensureDay()
        return index in TARGETS.indices && claimed and (1 shl index) == 0 && progress(index) >= TARGETS[index]
    }
    fun claim(index: Int): Boolean {
        if (!canClaim(index)) return false
        claimed = claimed or (1 shl index)
        g.adventure.add(AdventureResource.TICKETS, TICKETS[index])
        g.adventure.add(AdventureResource.BOOKS, 12)
        g.reward(null, 60 + index * 30, 15)
        g.listener?.onChanged(); return true
    }
    fun canClaimChest(): Boolean { ensureDay(); return claimed == 7 && !chestClaimed }
    fun claimChest(): Boolean {
        if (!canClaimChest()) return false
        chestClaimed = true
        g.adventure.add(AdventureResource.TICKETS, 1)
        g.adventure.add(AdventureResource.CRYSTAL, 8)
        g.workSeconds = (g.workSeconds + 180).coerceAtMost(7200)
        g.reward(null, 200, 40); g.listener?.onChanged(); return true
    }
    fun hasRewards() = TARGETS.indices.any { canClaim(it) } || canClaimChest()

    fun suited(post: Int, hero: Int): Boolean {
        val role = g.adventure.heroes.getOrNull(hero)?.def?.role ?: return false
        return when (post) {
            0 -> role in setOf(HeroRole.RANGER, HeroRole.HEALER, HeroRole.SUMMONER)
            1 -> role in setOf(HeroRole.GUARDIAN, HeroRole.ENGINEER, HeroRole.DUELIST)
            2 -> role in setOf(HeroRole.BARD, HeroRole.MAGE, HeroRole.ROGUE)
            else -> false
        }
    }
    fun heroBonus(post: Int, hero: Int): Int {
        val h = g.adventure.heroes.getOrNull(hero)?.takeIf { it.owned } ?: return 0
        if (post !in posts.indices) return 0
        return (5 + if (suited(post, hero)) 5 else 0) + (h.level / 10 + h.stars).coerceAtMost(15)
    }
    fun bonus(post: Int) = posts.getOrNull(post)?.let { heroBonus(post, it) } ?: 0
    fun assign(post: Int, hero: Int): Boolean {
        if (post !in posts.indices || (hero != -1 && g.adventure.heroes.getOrNull(hero)?.owned != true)) return false
        g.adventure.advanceProduction()
        if (hero >= 0) posts.indices.filter { posts[it] == hero }.forEach { posts[it] = -1 }
        posts[post] = hero
        g.listener?.onChanged(); return true
    }
    fun villageBonus(cat: Cat): Int = when (cat) {
        Cat.FIELD, Cat.RANCH, Cat.NATURE -> bonus(0)
        Cat.SHOP -> bonus(1)
        Cat.HOME -> bonus(2)
        else -> 0
    }
    fun productionBonus(resource: AdventureResource): Int = when (resource) {
        AdventureResource.FOOD -> bonus(0)
        AdventureResource.TIMBER, AdventureResource.STONE, AdventureResource.ORE -> bonus(1)
        AdventureResource.BOOKS -> bonus(2)
        else -> 0
    }
    fun encode(): JSONObject {
        ensureDay()
        return JSONObject().put("posts", JSONArray(posts.toList())).put("day", day)
            .put("baseline", JSONArray(baseline.toList())).put("claimed", claimed).put("chest", chestClaimed)
    }
    fun decode(j: JSONObject?) {
        reset()
        if (j == null) { ensureDay(); return }
        day = j.optLong("day", -1); claimed = j.optInt("claimed") and 7
        chestClaimed = j.optBoolean("chest") && claimed == 7
        j.optJSONArray("baseline")?.let { a -> for (i in baseline.indices) baseline[i] = a.optLong(i).coerceIn(0, total(i)) }
        val seen = HashSet<Int>()
        j.optJSONArray("posts")?.let { a -> for (i in posts.indices) {
            val hero = a.optInt(i, -1)
            posts[i] = hero.takeIf { g.adventure.heroes.getOrNull(it)?.owned == true && seen.add(it) } ?: -1
        } }
        ensureDay()
    }
    fun copyFrom(other: HamletStewardship) {
        other.posts.copyInto(posts); other.baseline.copyInto(baseline)
        day = other.day; claimed = other.claimed; chestClaimed = other.chestClaimed
    }
    companion object {
        val TARGETS = intArrayOf(6, 3, 2)
        val TICKETS = longArrayOf(1, 1, 1)
        private val KEYS = listOf(listOf("harvested", "products", "delivered", "taxes"),
            listOf("battles_won", "adventure_gathered", "expeditions"),
            listOf("hero_upgrades", "hero_recruits", "upgrades", "studies"))
    }
}
