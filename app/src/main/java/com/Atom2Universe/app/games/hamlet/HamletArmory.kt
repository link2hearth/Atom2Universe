package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject

/** Enum names, piece identities and slots are save identifiers: never reorder or reuse them. */
enum class GearSet { HEARTHWARD, HUNT, GALE, GROVE, BLOOD, STORM }
enum class EquipmentRarity { RARE, EPIC, LEGENDARY, MYTHIC }

data class GearPiece(val id: Int, val slot: Int, val set: GearSet, val rarity: EquipmentRarity, val level: Int = 1,
    val craftRarity: EquipmentRarity = rarity)

data class GearCost(val ore: Long = 0, val runes: Long = 0, val crystal: Long = 0,
    val timber: Long = 0, val stone: Long = 0) {
    fun resources() = linkedMapOf(AdventureResource.ORE to ore, AdventureResource.RUNES to runes,
        AdventureResource.CRYSTAL to crystal, AdventureResource.TIMBER to timber, AdventureResource.STONE to stone)
    operator fun plus(other: GearCost) = GearCost(ore + other.ore, runes + other.runes,
        crystal + other.crystal, timber + other.timber, stone + other.stone)
    fun difference(other: GearCost) = GearCost((ore - other.ore).coerceAtLeast(0),
        (runes - other.runes).coerceAtLeast(0), (crystal - other.crystal).coerceAtLeast(0),
        (timber - other.timber).coerceAtLeast(0), (stone - other.stone).coerceAtLeast(0))
    fun half() = GearCost(ore / 2, runes / 2, crystal / 2, timber / 2, stone / 2)
}

/** Percentages use fractions (0.10 = +10%), except armor, which is a flat addition. */
data class GearBonuses(val hpPercent: Double = 0.0, val attackPercent: Double = 0.0,
    val armor: Double = 0.0, val crit: Double = 0.0, val haste: Double = 0.0,
    val skillBoost: Double = 0.0, val healingBonus: Double = 0.0,
    val lifesteal: Double = 0.0, val armorPierce: Double = 0.0, val openingShield: Double = 0.0) {
    operator fun plus(b: GearBonuses) = GearBonuses(hpPercent + b.hpPercent, attackPercent + b.attackPercent,
        armor + b.armor, crit + b.crit, haste + b.haste, skillBoost + b.skillBoost,
        healingBonus + b.healingBonus, lifesteal + b.lifesteal, armorPierce + b.armorPierce, openingShield + b.openingShield)
    fun scaled(scale: Double) = GearBonuses(hpPercent * scale, attackPercent * scale, armor * scale,
        crit * scale, haste * scale, skillBoost * scale, healingBonus * scale,
        lifesteal * scale, armorPierce * scale, openingShield * scale)
    fun bounded(): GearBonuses {
        fun clean(value: Double, cap: Double) = if (value.isFinite()) value.coerceIn(0.0, cap) else 0.0
        return GearBonuses(clean(hpPercent, 1.5), clean(attackPercent, 1.0), clean(armor, 200.0),
            clean(crit, 0.35), clean(haste, 0.3), clean(skillBoost, 1.0), clean(healingBonus, 2.0),
            clean(lifesteal, 0.5), clean(armorPierce, 0.7), clean(openingShield, 0.6))
    }
}

/** Crafted pieces complement legacy permanent upgrades. A piece has exactly one owner or none.
 * Crafting/enhancement are immediate, deterministic and paid exclusively with adventure resources. */
class HamletArmory(private val g: HamletGame) {
    private val pieces = LinkedHashMap<Int, GearPiece>()
    private val assignments = HashMap<Int, Int>()
    private var nextId = 1
    val inventory: List<GearPiece> get() = pieces.values.toList()
    fun reset() { pieces.clear(); assignments.clear(); nextId = 1 }
    private fun changed() { g.listener?.onChanged() }
    fun forgeRank() = if (g.sandbox) 3 else g.objs.values.filter {
        it.t == "adventureForge" && g.buildLeft(it) == 0L
    }.maxOfOrNull { it.tier } ?: 0
    fun requiredForge(rarity: EquipmentRarity) = when (rarity) {
        EquipmentRarity.RARE, EquipmentRarity.EPIC -> 1; EquipmentRarity.LEGENDARY -> 2; EquipmentRarity.MYTHIC -> 3
    }
    fun requiredFrontier(rarity: EquipmentRarity) = when (rarity) {
        EquipmentRarity.RARE -> 0; EquipmentRarity.EPIC -> 10; EquipmentRarity.LEGENDARY -> 20; EquipmentRarity.MYTHIC -> 45
    }
    fun unlocked(rarity: EquipmentRarity) = g.sandbox || forgeRank() >= requiredForge(rarity) && g.adventure.frontier >= requiredFrontier(rarity)
    fun craftCost(slot: Int, set: GearSet, rarity: EquipmentRarity): GearCost {
        val tier = rarity.ordinal
        val ore = longArrayOf(24, 55, 110, 210)[tier] + slot.coerceIn(0, 3) * 3L
        val runes = longArrayOf(0, 5, 12, 24)[tier]
        val crystal = longArrayOf(0, 0, 6, 18)[tier]
        val material = 6L + tier * 4L
        return GearCost(ore, runes, crystal,
            if (set in setOf(GearSet.HUNT, GearSet.GALE, GearSet.GROVE)) material else 0,
            if (set in setOf(GearSet.HEARTHWARD, GearSet.BLOOD, GearSet.STORM)) material else 0)
    }
    fun canPay(cost: GearCost) = g.sandbox || cost.resources().all { (resource, value) -> value >= 0 && g.adventure[resource] >= value }
    private fun pay(cost: GearCost): Boolean {
        if (!canPay(cost)) return false
        if (!g.sandbox) cost.resources().forEach { (resource, value) -> g.adventure.bag[resource.ordinal] -= value }
        return true
    }
    fun canCraft(slot: Int, set: GearSet, rarity: EquipmentRarity) = slot in 0..3 && nextId < Int.MAX_VALUE && unlocked(rarity) && canPay(craftCost(slot, set, rarity))
    fun craft(slot: Int, set: GearSet, rarity: EquipmentRarity): GearPiece? {
        if (slot !in 0..3 || nextId >= Int.MAX_VALUE || !unlocked(rarity)) return null
        g.adventure.advanceProduction()
        if (!pay(craftCost(slot, set, rarity))) return null
        return GearPiece(nextId++, slot, set, rarity).also {
            pieces[it.id] = it; g.stat("gear_forged"); g.stat("hero_upgrades"); changed()
        }
    }
    fun equipped(hero: Int, slot: Int): GearPiece? = if (hero in g.adventure.heroes.indices && slot in 0..3)
        assignments[hero * 4 + slot]?.let { pieces[it] } else null
    fun owner(pieceId: Int): Int? = assignments.entries.firstOrNull { it.value == pieceId }?.key?.div(4)
    fun equip(hero: Int, pieceId: Int): Boolean {
        if (g.adventure.activeBattle != null || g.adventure.heroes.getOrNull(hero)?.owned != true) return false
        val piece = pieces[pieceId] ?: return false
        // Replacing a piece leaves it in the inventory; transferring clears only its previous owner.
        assignments.entries.removeAll { it.value == pieceId }
        assignments[hero * 4 + piece.slot] = pieceId
        changed(); return true
    }
    fun unequip(hero: Int, slot: Int): Boolean {
        if (g.adventure.activeBattle != null || hero !in g.adventure.heroes.indices || slot !in 0..3) return false
        if (assignments.remove(hero * 4 + slot) == null) return false
        changed(); return true
    }
    fun enhanceCost(pieceId: Int): GearCost? = pieces[pieceId]?.let { enhancementCost(it.rarity, it.level) }
    private fun enhancementCost(rarity: EquipmentRarity, level: Int) = GearCost(
        ore = 10L + (rarity.ordinal + 1) * 6L * level,
        runes = (rarity.ordinal + 1L) * level,
        crystal = if (rarity.ordinal >= 2 && level % 3 == 0) rarity.ordinal.toLong() else 0)
    fun canEnhance(pieceId: Int): Boolean {
        val piece = pieces[pieceId] ?: return false
        return g.adventure.activeBattle == null && piece.level < MAX_LEVEL && unlocked(piece.rarity) && canPay(enhancementCost(piece.rarity, piece.level))
    }
    fun enhance(pieceId: Int): Boolean {
        val piece = pieces[pieceId] ?: return false
        if (g.adventure.activeBattle != null || piece.level >= MAX_LEVEL || !unlocked(piece.rarity)) return false
        g.adventure.advanceProduction()
        if (!pay(enhancementCost(piece.rarity, piece.level))) return false
        pieces[pieceId] = piece.copy(level = piece.level + 1)
        g.stat("gear_forged"); g.stat("hero_upgrades"); changed(); return true
    }
    fun nextQuality(pieceId: Int): EquipmentRarity? = pieces[pieceId]?.let { EquipmentRarity.entries.getOrNull(it.rarity.ordinal + 1) }
    private fun investment(piece: GearPiece, rarity: EquipmentRarity): GearCost {
        var spent = craftCost(piece.slot, piece.set, rarity)
        for (level in 1 until piece.level) spent += enhancementCost(rarity, level)
        return spent
    }
    private fun qualitySurcharge(rarity: EquipmentRarity) = GearCost(ore = rarity.ordinal * 12L,
        runes = rarity.ordinal * 3L, crystal = rarity.ordinal * 2L)
    /** Pay the difference for both the recipe and every retained enhancement level.
     * Crafting cheaply then upgrading can never bypass the high-quality recipe cost. */
    fun qualityCost(pieceId: Int): GearCost? {
        val piece = pieces[pieceId] ?: return null
        val next = nextQuality(pieceId) ?: return null
        return investment(piece, next).difference(investment(piece, piece.rarity)) + qualitySurcharge(next)
    }
    fun canUpgradeQuality(pieceId: Int): Boolean {
        val next = nextQuality(pieceId) ?: return false
        val cost = qualityCost(pieceId) ?: return false
        return g.adventure.activeBattle == null && unlocked(next) && canPay(cost)
    }
    fun upgradeQuality(pieceId: Int): Boolean {
        val piece = pieces[pieceId] ?: return false
        val next = nextQuality(pieceId) ?: return false
        if (g.adventure.activeBattle != null || !unlocked(next)) return false
        g.adventure.advanceProduction()
        val cost = qualityCost(pieceId) ?: return false
        if (!pay(cost)) return false
        pieces[pieceId] = piece.copy(rarity = next)
        g.stat("gear_forged"); g.stat("hero_upgrades"); changed(); return true
    }
    fun salvageReturn(pieceId: Int): GearCost? = pieces[pieceId]?.let { piece ->
        var spent = investment(piece, piece.rarity)
        for (rarity in piece.craftRarity.ordinal + 1..piece.rarity.ordinal) spent += qualitySurcharge(EquipmentRarity.entries[rarity])
        spent.half()
    }
    fun salvage(pieceId: Int): Boolean {
        if (g.adventure.activeBattle != null || owner(pieceId) != null) return false
        val refund = salvageReturn(pieceId) ?: return false
        pieces.remove(pieceId)
        if (!g.sandbox) refund.resources().forEach { (resource, value) -> g.adventure.add(resource, value) }
        changed(); return true
    }
    fun pieceBonus(piece: GearPiece): GearBonuses {
        val scale = doubleArrayOf(1.0, 1.35, 1.75, 2.2)[piece.rarity.ordinal] * (1 + (piece.level.coerceIn(1, MAX_LEVEL) - 1) * 0.06)
        val base = when (piece.slot) {
            0 -> GearBonuses(attackPercent = 0.025)
            1 -> GearBonuses(hpPercent = 0.035, armor = 2.0)
            2 -> GearBonuses(hpPercent = 0.02, armor = 1.0)
            else -> GearBonuses(attackPercent = 0.015)
        }
        val specialty = when (piece.set) {
            GearSet.HEARTHWARD -> GearBonuses(hpPercent = 0.015, armor = 2.0, openingShield = 0.015)
            GearSet.HUNT -> GearBonuses(crit = 0.015, attackPercent = 0.008)
            GearSet.GALE -> GearBonuses(haste = 0.012)
            GearSet.GROVE -> GearBonuses(healingBonus = 0.025, hpPercent = 0.008)
            GearSet.BLOOD -> GearBonuses(lifesteal = 0.012, attackPercent = 0.006)
            GearSet.STORM -> GearBonuses(armorPierce = 0.02, skillBoost = 0.015)
        }
        return (base + specialty).scaled(scale).bounded()
    }
    fun setCounts(hero: Int): Map<GearSet, Int> = (0..3).mapNotNull { equipped(hero, it)?.set }.groupingBy { it }.eachCount()
    fun setBonus(hero: Int): GearBonuses {
        var result = GearBonuses()
        for ((set, count) in setCounts(hero)) {
            if (count >= 2) result += when (set) {
                GearSet.HEARTHWARD -> GearBonuses(hpPercent = 0.08)
                GearSet.HUNT -> GearBonuses(crit = 0.04)
                GearSet.GALE -> GearBonuses(haste = 0.04)
                GearSet.GROVE -> GearBonuses(healingBonus = 0.12)
                GearSet.BLOOD -> GearBonuses(lifesteal = 0.05)
                GearSet.STORM -> GearBonuses(armorPierce = 0.08)
            }
            if (count == 4) result += when (set) {
                GearSet.HEARTHWARD -> GearBonuses(armor = 8.0, openingShield = 0.12)
                GearSet.HUNT -> GearBonuses(attackPercent = 0.1, crit = 0.02)
                GearSet.GALE -> GearBonuses(haste = 0.04, skillBoost = 0.08)
                GearSet.GROVE -> GearBonuses(healingBonus = 0.18, openingShield = 0.06)
                GearSet.BLOOD -> GearBonuses(lifesteal = 0.08, hpPercent = 0.06)
                GearSet.STORM -> GearBonuses(skillBoost = 0.15, armorPierce = 0.06)
            }
        }
        return result.bounded()
    }
    fun bonuses(hero: Int): GearBonuses = (0..3).mapNotNull { equipped(hero, it) }
        .fold(setBonus(hero)) { total, piece -> total + pieceBonus(piece) }.bounded()
    fun recommendedSet(hero: Int): GearSet? = g.adventure.heroes.getOrNull(hero)?.def?.role?.let { role ->
        when (role) {
            HeroRole.GUARDIAN -> GearSet.HEARTHWARD
            HeroRole.HEALER -> GearSet.GROVE
            HeroRole.BARD, HeroRole.SUMMONER -> GearSet.GALE
            HeroRole.RANGER, HeroRole.ROGUE -> GearSet.HUNT
            HeroRole.DUELIST -> GearSet.BLOOD
            HeroRole.MAGE, HeroRole.ENGINEER -> GearSet.STORM
        }
    }
    fun encode(): JSONObject {
        val rows = JSONArray()
        pieces.values.forEach { p -> rows.put(JSONObject().put("id", p.id).put("slot", p.slot)
            .put("set", p.set.name).put("rarity", p.rarity.name).put("level", p.level).put("craftRarity", p.craftRarity.name)) }
        val equipped = JSONArray()
        assignments.toSortedMap().forEach { (key, piece) -> equipped.put(JSONObject().put("hero", key / 4).put("slot", key % 4).put("piece", piece)) }
        return JSONObject().put("nextId", nextId).put("pieces", rows).put("equipped", equipped)
    }
    fun decode(j: JSONObject?) {
        reset()
        if (j == null) return
        j.optJSONArray("pieces")?.let { rows ->
            for (i in 0 until minOf(rows.length(), 10_000)) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optInt("id", -1); val slot = row.optInt("slot", -1)
                val set = GearSet.entries.firstOrNull { it.name == row.optString("set") } ?: continue
                val rarity = EquipmentRarity.entries.firstOrNull { it.name == row.optString("rarity") } ?: continue
                if (id <= 0 || id >= Int.MAX_VALUE || slot !in 0..3 || id in pieces) continue
                val origin = EquipmentRarity.entries.firstOrNull { it.name == row.optString("craftRarity") && it.ordinal <= rarity.ordinal } ?: rarity
                pieces[id] = GearPiece(id, slot, set, rarity, row.optInt("level", 1).coerceIn(1, MAX_LEVEL), origin)
            }
        }
        nextId = maxOf(j.optInt("nextId", 1).coerceIn(1, Int.MAX_VALUE), (pieces.keys.maxOrNull() ?: 0) + 1)
        val seen = HashSet<Int>()
        j.optJSONArray("equipped")?.let { rows ->
            for (i in 0 until minOf(rows.length(), g.adventure.heroes.size * 4)) {
                val row = rows.optJSONObject(i) ?: continue
                val hero = row.optInt("hero", -1); val slot = row.optInt("slot", -1); val id = row.optInt("piece", -1)
                val piece = pieces[id] ?: continue
                if (g.adventure.heroes.getOrNull(hero)?.owned != true || slot !in 0..3 || piece.slot != slot || hero * 4 + slot in assignments || !seen.add(id)) continue
                assignments[hero * 4 + slot] = id
            }
        }
    }
    fun copyFrom(other: HamletArmory) {
        pieces.clear(); pieces.putAll(other.pieces)
        assignments.clear(); assignments.putAll(other.assignments)
        nextId = other.nextId
    }
    companion object { const val MAX_LEVEL = 10 }
}
