package com.Atom2Universe.app.games.hamlet

import kotlin.math.max
import kotlin.random.Random

enum class AdventureResource { FOOD, TIMBER, STONE, ORE, BOOKS, CRYSTAL, RUNES, TICKETS }
enum class AdventureNode { BATTLE, GATHER, SHRINE, RESCUE, BOSS }

/** La campagne et les activités remplacent les limitations de serveur par des ressources gagnées.
 * Aucune énergie, limite quotidienne, blessure payante, achat, ou plafond de niveau de héros. */
class HamletAdventure(private val g: HamletGame) {
    val bag = LongArray(AdventureResource.entries.size)
    val heroes = HamletHeroes.defs.map { HeroState(it.id) }
    val teams = Array(3) { IntArray(6) { -1 } }
    var selectedTeam = 0
    var frontier = 0
    var trial = 0
    var bounty = 0
    var raid = 0
    var arena = 0
    val stars = HashMap<Int, Int>()
    val tech = IntArray(4)
    val workers = intArrayOf(1, 1, 1, 0, 0)
    val troops = intArrayOf(10, 10, 10)
    val troopTier = intArrayOf(1, 1, 1)
    val relics = IntArray(3)
    val pets = IntArray(3)
    val teamPets = IntArray(3) { -1 }
    val teamRelics = IntArray(3) { -1 }
    var productionAt = 0L
    var auto = true
    var activeBattle: HamletBattle? = null
        private set
    fun reset() {
        bag.fill(0); bag[0] = 180; bag[1] = 80; bag[2] = 80; bag[3] = 35; bag[4] = 30; bag[7] = 3
        heroes.forEach { h -> h.owned = g.sandbox || h.id in STARTERS; h.level = 1; h.stars = 0; h.shards = 0; h.skills.fill(1); h.gear.fill(0); h.gearTier.fill(0); h.traits.fill(0) }
        teams.forEach { it.fill(-1) }; intArrayOf(1, 7, 0, 3, -1, -1).copyInto(teams[0])
        selectedTeam = 0; frontier = 0; trial = 0; bounty = 0; raid = 0; arena = 0
        stars.clear(); tech.fill(0); intArrayOf(1, 1, 1, 0, 0).copyInto(workers)
        troops.fill(10); troopTier.fill(1); relics.fill(0); pets.fill(0); teamPets.fill(-1); teamRelics.fill(-1)
        if (g.sandbox) { relics.fill(1); pets.fill(1) }
        productionAt = g.now(); auto = true; activeBattle = null
    }
    init { reset() }
    operator fun get(resource: AdventureResource) = bag[resource.ordinal]
    fun add(resource: AdventureResource, amount: Long) { if (amount > 0) bag[resource.ordinal] += amount }
    private fun pay(resource: AdventureResource, amount: Long): Boolean {
        if (g.sandbox) return true
        if (amount < 0 || this[resource] < amount) return false
        bag[resource.ordinal] -= amount; return true
    }
    private fun changed() { g.listener?.onChanged() }
    fun slotCount() = if (g.sandbox || frontier >= 24) 6 else if (frontier >= 10) 5 else 4
    fun availableHeroes() = heroes.filter { it.def.rescue <= frontier || it.owned || g.sandbox }
    fun assign(hero: Int, slot: Int): Boolean {
        if (hero !in heroes.indices || slot !in 0 until slotCount() || (!heroes[hero].owned && !g.sandbox)) return false
        if (g.sandbox) heroes[hero].owned = true
        val team = teams[selectedTeam]; val old = team.indexOf(hero)
        if (old >= 0) team[old] = team[slot]
        team[slot] = hero; changed(); return true
    }
    fun remove(slot: Int): Boolean {
        if (slot !in 0..5 || teams[selectedTeam].count { it >= 0 } <= 1) return false
        teams[selectedTeam][slot] = -1; changed(); return true
    }
    fun selectTeam(index: Int): Boolean {
        if (index !in teams.indices) return false
        selectedTeam = index
        if (teams[index].all { it < 0 }) teams[0].copyInto(teams[index])
        changed(); return true
    }
    fun activeBonds() = HamletHeroes.bonds.mapIndexedNotNull { i, members -> if (members.all { it in teams[selectedTeam] }) i else null }
    fun factionBonus(faction: HeroFaction): Double {
        val count = teams[selectedTeam].take(slotCount()).filter { it >= 0 }.count { heroes[it].def.faction == faction }
        return when { count >= 4 -> 0.18; count >= 3 -> 0.1; else -> 0.0 }
    }
    fun facility(id: String) = g.objs.values.filter { it.t == id && g.buildLeft(it) == 0L }.sumOf { it.tier }
    fun numbers(hero: Int): HeroNumbers {
        val h = heroes[hero]; val base = HamletHeroes.numbers(h, facility("adventureAcademy"))
        val bond = activeBonds(); val faction = factionBonus(h.def.faction)
        val army = minOf(troops[h.def.faction.ordinal], marchCapacity()) * (1.0 + (troopTier[h.def.faction.ordinal] - 1) * 0.2)
        val pet = teamPets[selectedTeam].takeIf { it in pets.indices }?.let { pets[it] } ?: 0
        return base.copy(hp = base.hp * (1 + faction + army * 0.002 + tech[1] * 0.018 + pet * 0.01),
            attack = base.attack * (1 + faction + army * 0.0015 + tech[0] * 0.018 + if (3 in bond) 0.08 else 0.0),
            reduction = (base.reduction + if (1 in bond) 0.08 else 0.0).coerceAtMost(0.4),
            skillBoost = base.skillBoost * (if (0 in bond) 1.08 else 1.0),
            haste = (base.haste + if (2 in bond) 0.08 else 0.0).coerceAtMost(0.35))
    }
    fun power() = teams[selectedTeam].take(slotCount()).filter { it >= 0 }.sumOf { numbers(it).let { n -> n.hp / 10 + n.attack * 4 + n.armor * 2 } }.toLong()
    fun levelCost(hero: Int): Long = heroes[hero].level.toLong().let { 18 + it * 7 + it * it / 40 }
    fun levelUp(hero: Int): Boolean {
        if (hero !in heroes.indices || !heroes[hero].owned || !pay(AdventureResource.FOOD, levelCost(hero))) return false
        heroes[hero].level++; changed(); return true
    }
    fun starCost(hero: Int) = 5L + heroes[hero].stars * 5L
    fun promote(hero: Int): Boolean {
        if (hero !in heroes.indices || !heroes[hero].owned) return false
        val h = heroes[hero]; val cost = starCost(hero)
        if (!g.sandbox && h.shards < cost) return false
        if (!g.sandbox) h.shards -= cost
        h.stars++; changed(); return true
    }
    fun skillCost(hero: Int, skill: Int) = 8L + heroes[hero].skills[skill] * 12L
    fun studySkill(hero: Int, skill: Int): Boolean {
        if (hero !in heroes.indices || skill !in 0..3 || !heroes[hero].owned || !pay(AdventureResource.BOOKS, skillCost(hero, skill))) return false
        heroes[hero].skills[skill]++; changed(); return true
    }
    fun focusRecruit(hero: Int): Boolean {
        if (hero !in heroes.indices || heroes[hero] !in availableHeroes() || !pay(AdventureResource.TICKETS, 3)) return false
        val h = heroes[hero]
        if (!h.owned) h.owned = true else h.shards += 8
        g.stat("hero_recruits"); changed(); return true
    }
    fun promoteTrait(hero: Int, trait: Int): Boolean {
        if (hero !in heroes.indices || trait !in 0..3 || !heroes[hero].owned || heroes[hero].stars < 5) return false
        val h = heroes[hero]; val cost = 12L * (h.traits[trait] + 1)
        if (!g.sandbox && h.shards < cost) return false
        if (!g.sandbox) h.shards -= cost
        h.traits[trait]++; changed(); return true
    }
    fun forgeCost(hero: Int, slot: Int) = 10L + heroes[hero].gear[slot] * 6L + heroes[hero].gearTier[slot] * 15L
    fun forge(hero: Int, slot: Int): Boolean {
        if (hero !in heroes.indices || slot !in 0..3 || !heroes[hero].owned || (!g.sandbox && facility("adventureForge") == 0)) return false
        val cost = forgeCost(hero, slot)
        if (!g.sandbox && (this[AdventureResource.ORE] < cost || this[AdventureResource.RUNES] < heroes[hero].gear[slot])) return false
        pay(AdventureResource.ORE, cost); pay(AdventureResource.RUNES, heroes[hero].gear[slot].toLong())
        heroes[hero].gear[slot]++; g.stat("gear_forged"); changed(); return true
    }
    fun refine(hero: Int, slot: Int): Boolean {
        if (hero !in heroes.indices || slot !in 0..3 || !heroes[hero].owned || (!g.sandbox && facility("adventureForge") == 0)) return false
        val h = heroes[hero]; val tier = h.gearTier[slot]
        if (tier >= 3 || h.gear[slot] < (tier + 1) * 5 || !pay(AdventureResource.CRYSTAL, 10L * (tier + 1))) return false
        h.gearTier[slot]++; changed(); return true
    }
    fun researchCost(branch: Int) = 25L + tech[branch] * 15L
    fun research(branch: Int): Boolean {
        if (branch !in tech.indices || (!g.sandbox && facility("adventureAcademy") == 0) || !pay(AdventureResource.BOOKS, researchCost(branch))) return false
        tech[branch]++; changed(); return true
    }
    fun workerCapacity() = (g.population() / 2).coerceIn(3, 24)
    fun assignWorker(job: Int, delta: Int): Boolean {
        if (job !in workers.indices || delta !in listOf(-1, 1) || workers[job] + delta < 0 || (delta > 0 && workers.sum() >= workerCapacity())) return false
        advanceProduction(); workers[job] += delta; changed(); return true
    }
    fun advanceProduction() {
        val t = g.now()
        if (productionAt <= 0 || t < productionAt) { productionAt = t; return }
        val minutes = (t - productionAt) / 60_000
        if (minutes <= 0) return
        val boost = 1 + tech[2] * 0.04 + facility("adventureStore") * 0.1
        val academy = facility("adventureAcademy"); val forge = facility("adventureForge")
        val amounts = longArrayOf(workers[0] * 3L + 1, workers[1] * 2L, workers[2] * 2L, workers[4].toLong() + forge, workers[3].toLong() + academy)
        for (i in amounts.indices) bag[i] += (amounts[i] * minutes * boost).toLong()
        productionAt += minutes * 60_000; changed()
    }
    fun villageAction(key: String, count: Long) {
        val n = count.coerceIn(0, 100)
        when (key) {
            "harvested" -> add(AdventureResource.FOOD, n * 6)
            "products" -> { add(AdventureResource.FOOD, n * 4); add(AdventureResource.ORE, n) }
            "delivered" -> add(AdventureResource.BOOKS, n * 2)
            "workshops" -> { add(AdventureResource.TIMBER, 8); add(AdventureResource.STONE, 8); add(AdventureResource.RUNES, 2) }
        }
    }
    fun marchCapacity() = 20 + frontier / 2 + facility("adventureYard") * 10
    fun trainTroops(faction: Int, amount: Int = 10): Boolean {
        if (faction !in troops.indices || amount !in 1..100 || (!g.sandbox && facility("adventureYard") == 0)) return false
        if (!g.sandbox && (this[AdventureResource.FOOD] < amount * 2L || this[AdventureResource.ORE] < amount.toLong())) return false
        pay(AdventureResource.FOOD, amount * 2L); pay(AdventureResource.ORE, amount.toLong())
        troops[faction] += amount; changed(); return true
    }
    fun upgradeSupport(pet: Boolean, index: Int): Boolean {
        val values = if (pet) pets else relics
        if (index !in values.indices || values[index] == 0) return false
        val resource = if (pet) AdventureResource.FOOD else AdventureResource.CRYSTAL
        val cost = (if (pet) 50L else 12L) * (values[index] + 1)
        if (!pay(resource, cost)) return false
        values[index]++; changed(); return true
    }
    fun improveTroops(faction: Int): Boolean {
        if (faction !in troopTier.indices || (!g.sandbox && facility("adventureYard") == 0)) return false
        val tier = troopTier[faction]; val cost = 20L * tier
        if (!g.sandbox && (this[AdventureResource.TIMBER] < cost || this[AdventureResource.STONE] < cost || this[AdventureResource.BOOKS] < 15L * tier)) return false
        pay(AdventureResource.TIMBER, cost); pay(AdventureResource.STONE, cost); pay(AdventureResource.BOOKS, 15L * tier)
        troopTier[faction]++; changed(); return true
    }
    fun supplyBuilders(): Boolean {
        if (g.workSeconds > 7140 || (!g.sandbox && (this[AdventureResource.TIMBER] < 10 || this[AdventureResource.STONE] < 10))) return false
        pay(AdventureResource.TIMBER, 10); pay(AdventureResource.STONE, 10)
        g.workSeconds += 60; changed(); return true
    }
    fun selectSupport(pet: Boolean, index: Int): Boolean {
        val values = if (pet) pets else relics
        if (index !in values.indices || values[index] <= 0) return false
        (if (pet) teamPets else teamRelics)[selectedTeam] = index; changed(); return true
    }
    fun node(stage: Int): AdventureNode {
        if (stage % 10 == 9) return AdventureNode.BOSS
        if (HamletHeroes.defs.any { it.rescue > 0 && it.rescue - 1 == stage % 120 }) return AdventureNode.RESCUE
        return when (stage % 8) { 1, 4 -> AdventureNode.GATHER; 6 -> AdventureNode.SHRINE; else -> AdventureNode.BATTLE }
    }
    fun region(stage: Int) = (stage / 20) % 6
    fun gather(stage: Int): Boolean {
        if (stage < 0 || stage > frontier || node(stage) !in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) return false
        grantLoot(stage, first = stage == frontier, combat = false)
        record(stage, 3); changed(); return true
    }
    fun canBattle(mode: BattleMode, stage: Int): Boolean {
        if (activeBattle != null || stage < 0 || teams[selectedTeam].take(slotCount()).none { it >= 0 && heroes[it].owned }) return false
        return when (mode) {
            BattleMode.CAMPAIGN -> stage <= frontier && node(stage) !in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)
            BattleMode.TRIAL -> frontier >= 10 && stage <= trial
            BattleMode.BOUNTY -> frontier >= 5 && stage <= bounty
            BattleMode.RAID -> frontier >= 20 && stage <= raid
            BattleMode.ARENA -> frontier >= 12 && stage <= arena
        }
    }
    fun beginBattle(mode: BattleMode, stage: Int, seed: Int? = null): HamletBattle? {
        if (!canBattle(mode, stage)) return null
        val units = ArrayList<BattleUnit>()
        teams[selectedTeam].take(slotCount()).forEachIndexed { slot, hero ->
            if (hero >= 0 && heroes[hero].owned) {
                val h = heroes[hero]; val n = numbers(hero); val d = h.def
                units.add(BattleUnit(hero, hero, false, d.faction, d.role, slot, n.hp, n.attack, n.armor,
                    d.interval * (1 - n.haste).toFloat(), d.skill * (1 - n.haste).toFloat(), d.ultimate * (1 - n.haste).toFloat(), n.crit, n.skillBoost, n.reduction))
            }
        }
        val effective = effectiveStage(mode, stage)
        units.addAll(encounter(mode, stage))
        val relic = teamRelics[selectedTeam]; val pet = teamPets[selectedTeam]
        return HamletBattle(mode, stage, region(effective), units, Random(seed ?: g.rng.nextInt()), relic,
            relics.getOrElse(relic) { 0 }, pet, pets.getOrElse(pet) { 0 }, auto).also { activeBattle = it }
    }
    fun effectiveStage(mode: BattleMode, stage: Int) = when (mode) { BattleMode.CAMPAIGN -> stage; BattleMode.TRIAL -> stage * 2 + 8; BattleMode.BOUNTY -> stage * 2 + 3; BattleMode.RAID -> stage * 3 + 16; BattleMode.ARENA -> stage * 2 + 10 }
    /** Aperçu et moteur partagent la même rencontre, sans consommer le hasard de la sauvegarde. */
    fun encounter(mode: BattleMode, stage: Int): List<BattleUnit> {
        val effective = effectiveStage(mode, stage)
        val region = region(effective)
        val boss = mode == BattleMode.RAID || mode == BattleMode.CAMPAIGN && node(stage) == AdventureNode.BOSS || mode == BattleMode.TRIAL && stage % 5 == 4
        val scale = 0.8 + effective * 0.12 + effective.toDouble() * effective * 0.00035
        val count = if (boss) 3 else (3 + effective / 24).coerceAtMost(6)
        return (0 until count).map { i ->
            if (mode == BattleMode.ARENA) {
                val line = intArrayOf(1, 6, 0, 3, 5, 7)
                val def = HamletHeroes.defs[(line[i] + (stage / 4) * 3) % heroes.size]
                BattleUnit(100 + i, def.id, true, def.faction, def.role, i, def.hp * scale * 0.8,
                    def.attack * scale * 0.8, def.armor + scale * 2, def.interval, def.skill, def.ultimate, heroVisual = true)
            } else {
            val sprite = if (boss && i == 0) if (region < 3) 6 else 7 else (effective + i * 2) % 6
            HamletBattle.enemy(sprite, 100 + i, i, scale, boss && i == 0)
            }
        }
    }
    fun abandonBattle() { activeBattle = null }
    fun settle(battle: HamletBattle): Boolean {
        if (activeBattle !== battle || battle.settled || battle.outcome == BattleOutcome.RUNNING) return false
        battle.settled = true; activeBattle = null
        if (battle.outcome == BattleOutcome.DEFEAT) { g.stat("battles_lost"); changed(); return true }
        val stage = battle.stage
        when (battle.mode) {
            BattleMode.CAMPAIGN -> {
                grantLoot(stage, stage == frontier, true)
                record(stage, if (battle.survivors() >= 3) 3 else 2)
                val rescued = HamletHeroes.defs.firstOrNull { it.rescue > 0 && it.rescue - 1 == stage % 120 }
                if (rescued != null) { heroes[rescued.id].owned = true; heroes[rescued.id].shards += 5 }
            }
            BattleMode.TRIAL -> { if (stage == trial) trial++; grantLoot(stage * 2 + 8, false, true); add(AdventureResource.RUNES, 8L + stage) }
            BattleMode.BOUNTY -> { if (stage == bounty) bounty++; grantLoot(stage * 2 + 3, false, true); add(AdventureResource.TICKETS, 1); heroes[(stage * 7) % heroes.size].shards += 4 }
            BattleMode.RAID -> { if (stage == raid) raid++; grantLoot(stage * 3 + 16, false, true); add(AdventureResource.CRYSTAL, 12L + stage * 2L) }
            BattleMode.ARENA -> { if (stage == arena) arena++; grantLoot(stage * 2 + 10, false, true); add(AdventureResource.BOOKS, 20L + stage * 3L) }
        }
        g.stat("battles_won"); changed(); return true
    }
    private fun record(stage: Int, score: Int) {
        stars[stage] = max(stars[stage] ?: 0, score)
        if (stage == frontier) frontier++
        if (frontier >= 10 && relics[0] == 0) relics[0] = 1
        if (frontier >= 30 && relics[1] == 0) relics[1] = 1
        if (frontier >= 60 && relics[2] == 0) relics[2] = 1
        if (frontier >= 20 && pets[0] == 0) pets[0] = 1
        if (frontier >= 50 && pets[1] == 0) pets[1] = 1
        if (frontier >= 80 && pets[2] == 0) pets[2] = 1
    }
    private fun grantLoot(stage: Int, first: Boolean, combat: Boolean) {
        val scale = 1 + stage / 10
        val bonus = if (first) 2 else 1
        val boss = node(stage) == AdventureNode.BOSS
        add(AdventureResource.FOOD, (25L + scale * 8) * bonus)
        add(AdventureResource.TIMBER, (15L + scale * 4) * bonus)
        add(AdventureResource.STONE, (12L + scale * 4) * bonus)
        add(AdventureResource.ORE, (6L + scale * 2) * bonus)
        add(AdventureResource.BOOKS, (3L + scale) * bonus)
        add(AdventureResource.RUNES, (2L + scale) * bonus)
        if (boss) { add(AdventureResource.CRYSTAL, (4L + scale) * bonus); add(AdventureResource.TICKETS, 2) }
        if (combat) heroes[(stage * 5 + 1) % heroes.size].shards += if (boss) 6 else 2
        g.journey.supplies += 1 + scale
        g.workSeconds = (g.workSeconds + (if (boss) 180 else 45) + tech[3] * 5).coerceAtMost(7200)
        g.reward(null, (35 + scale * 15) * bonus, (8 + scale * 3) * bonus)
    }
    fun copyFrom(other: HamletAdventure) {
        other.bag.copyInto(bag); other.tech.copyInto(tech); other.workers.copyInto(workers)
        other.troops.copyInto(troops); other.troopTier.copyInto(troopTier)
        other.relics.copyInto(relics); other.pets.copyInto(pets); other.teamPets.copyInto(teamPets); other.teamRelics.copyInto(teamRelics)
        other.teams.forEachIndexed { i, team -> team.copyInto(teams[i]) }
        selectedTeam = other.selectedTeam; frontier = other.frontier; trial = other.trial; bounty = other.bounty; raid = other.raid; arena = other.arena
        stars.clear(); stars.putAll(other.stars); productionAt = other.productionAt; auto = other.auto; activeBattle = null
        other.heroes.forEachIndexed { i, source ->
            val h = heroes[i]; h.owned = source.owned; h.level = source.level; h.stars = source.stars; h.shards = source.shards
            source.skills.copyInto(h.skills); source.gear.copyInto(h.gear); source.gearTier.copyInto(h.gearTier); source.traits.copyInto(h.traits)
        }
    }
    companion object {
        val STARTERS = setOf(0, 1, 3, 7)
        val FACILITIES = listOf("adventureInn", "adventureAcademy", "adventureForge", "adventureStore", "adventureYard", "adventureGate")
    }
}
