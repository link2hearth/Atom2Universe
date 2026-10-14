package com.Atom2Universe.app.games.hamlet

import kotlin.math.max
import kotlin.random.Random

enum class AdventureResource { FOOD, TIMBER, STONE, ORE, BOOKS, CRYSTAL, RUNES, TICKETS }
/** [TREASURE] : un combat ordinaire qui offre un appel la première fois. */
enum class AdventureNode { BATTLE, GATHER, SHRINE, TREASURE, BOSS }
data class HeroRecruitment(val hero: Int, val newHero: Boolean, val shards: Long, val guaranteed: Boolean,
    val scripted: Boolean = false, val legend: Boolean = false)

/**
 * La campagne, les équipes et les héros. On commence sans aucun héros : le premier appel vient d'une quête du
 * village, les deux suivants de la campagne, puis les appels deviennent libres. Un héros n'existe pour le joueur
 * qu'une fois obtenu. Les légendes ne sortent pas des tirages (voir [HamletLegends]).
 * Les combats restent sans énergie ni quota ; ce qui limite la progression, ce sont les coûts qui montent et les durées.
 */
class HamletAdventure(private val g: HamletGame) {
    val bag = LongArray(AdventureResource.entries.size)
    val heroes = HamletHeroes.defs.map { HeroState(it.id) }
    val teams = Array(3) { IntArray(MAX_SLOTS) { -1 } }
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
    val tactics = Array(3) { BattleTactic.BALANCED }
    var productionAt = 0L
    val productionRemainder = DoubleArray(5)
    /** Appels déjà tirés (les trois premiers sont écrits d'avance). */
    var recruitmentPulls = 0
    var pityEpic = 0
    /** Tirages libres faits sans rare (le huitième en garantit un) ; -1 une fois le premier rare obtenu. */
    var pityRare = 0
    var recruitmentWork = 0L
    var workTicketDay = -1L
    var workTicketsToday = 0
    var innTicketProgress = 0L
    var shardFocusHero = -1
    /** Victoires depuis le dernier fragment de campagne. */
    var shardDrip = 0
    var auto = true
    var activeBattle: HamletBattle? = null
        private set
    fun reset() {
        bag.fill(0); bag[0] = 180; bag[1] = 80; bag[2] = 80; bag[3] = 35; bag[4] = 30
        heroes.forEach { h -> h.owned = g.sandbox; h.level = 1; h.stars = 0; h.shards = 0; h.skills.fill(1); h.traits.fill(0) }
        teams.forEach { it.fill(-1) }
        selectedTeam = 0; frontier = 0; trial = 0; bounty = 0; raid = 0; arena = 0
        stars.clear(); tech.fill(0); intArrayOf(1, 1, 1, 0, 0).copyInto(workers)
        troops.fill(10); troopTier.fill(1); relics.fill(0); pets.fill(0); teamPets.fill(-1); teamRelics.fill(-1)
        tactics.fill(BattleTactic.BALANCED)
        if (g.sandbox) { relics.fill(1); pets.fill(1); intArrayOf(7, 1, 3, 0, 2).copyInto(teams[0]) }
        productionAt = g.now(); auto = true; activeBattle = null
        productionRemainder.fill(0.0)
        recruitmentPulls = 0; pityEpic = 0; pityRare = 0
        recruitmentWork = 0; innTicketProgress = 0; shardFocusHero = -1; shardDrip = 0
        workTicketDay = -1; workTicketsToday = 0
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

    // ------------------------------------------------------------------ héros connus

    fun ownedCount() = heroes.count { it.owned }
    /** Les modules Héros et Aventure s'ouvrent au premier appel, pas avant. */
    fun opened() = g.sandbox || ownedCount() > 0 || this[AdventureResource.TICKETS] > 0 || recruitmentPulls > 0
    /** Un nouveau héros prend la première place libre de l'équipe choisie. */
    fun welcome(h: HeroState) {
        if (h.owned) return
        h.owned = true
        g.stat("hero_recruits")
        g.chronicle.record(HamletChronicle.Kind.HERO, subject = h.id.toString())
        val team = teams[selectedTeam]
        if (team.take(slotCount()).none { it == h.id }) {
            val free = (0 until slotCount()).firstOrNull { team[it] < 0 }
            if (free != null) team[free] = h.id
        }
    }

    // ------------------------------------------------------------------ équipes

    /** Trois places au début, une quatrième après le premier boss, une cinquième après le quatrième. */
    fun slotCount() = if (g.sandbox || frontier >= FIFTH_SLOT) 5 else if (frontier >= FOURTH_SLOT) 4 else 3
    fun assign(hero: Int, slot: Int): Boolean {
        if (hero !in heroes.indices || slot !in 0 until slotCount() || (!heroes[hero].owned && !g.sandbox)) return false
        if (g.sandbox) heroes[hero].owned = true
        val team = teams[selectedTeam]; val old = team.indexOf(hero)
        if (old >= 0) team[old] = team[slot]
        team[slot] = hero; changed(); return true
    }
    fun remove(slot: Int): Boolean {
        if (slot !in 0 until MAX_SLOTS || teams[selectedTeam].count { it >= 0 } <= 1) return false
        teams[selectedTeam][slot] = -1; changed(); return true
    }
    fun selectTeam(index: Int): Boolean {
        if (index !in teams.indices) return false
        selectedTeam = index
        if (teams[index].all { it < 0 }) teams[0].copyInto(teams[index])
        changed(); return true
    }
    /** Établit une équipe utilisable, puis préfère les alliés de mêmes factions. */
    fun autoFormation(): Boolean {
        val candidates = heroes.filter { it.owned }
        if (candidates.isEmpty()) return false
        val chosen = ArrayList<HeroState>()
        fun strength(h: HeroState) = numbers(h.id).let { it.hp / 10 + it.attack * 4 + it.armor * 2 }
        fun choose(roles: Set<HeroRole>): Boolean {
            val h = candidates.filter { it !in chosen && it.def.role in roles }.maxByOrNull { strength(it) } ?: return false
            chosen.add(h); return true
        }
        if (!choose(setOf(HeroRole.GUARDIAN))) choose(HeroRole.entries.toSet())
        if (!choose(setOf(HeroRole.GUARDIAN, HeroRole.DUELIST))) choose(HeroRole.entries.toSet())
        choose(setOf(HeroRole.HEALER))
        while (chosen.size < slotCount()) {
            val h = candidates.filter { it !in chosen }.maxByOrNull {
                strength(it) * (1 + chosen.count { ally -> ally.def.faction == it.def.faction } * 0.1)
            } ?: break
            chosen.add(h)
        }
        teams[selectedTeam].fill(-1)
        chosen.forEachIndexed { slot, h -> teams[selectedTeam][slot] = h.id }
        changed(); return true
    }
    fun activeBonds() = HamletHeroes.bonds.mapIndexedNotNull { i, members -> if (members.all { it in teams[selectedTeam].take(slotCount()) }) i else null }
    fun factionBonus(faction: HeroFaction): Double {
        val count = teams[selectedTeam].take(slotCount()).filter { it >= 0 }.count { heroes[it].def.faction == faction }
        return when { count >= 5 -> 0.22; count >= 4 -> 0.14; count >= 3 -> 0.08; count >= 2 -> 0.04; else -> 0.0 }
    }
    fun facility(id: String) = g.objs.values.filter { it.t == id && g.buildLeft(it) == 0L }.sumOf { it.tier }
    fun facilityRank(id: String) = g.objs.values.filter { it.t == id && g.buildLeft(it) == 0L }.maxOfOrNull { it.tier } ?: 0
    /** Une recrue peut rejoindre une équipe déjà entraînée sans repartir de zéro. */
    fun trainingLevel(): Int {
        if (facilityRank("adventureAcademy") == 0) return 1
        val trained = heroes.filter { it.owned }.sortedByDescending { it.level }.take(slotCount())
        return if (trained.size < slotCount()) 1 else trained.minOf { it.level }
    }
    fun levelCap(hero: Int) = HamletHeroes.levelCap(heroes[hero].stars)
    fun effectiveLevel(hero: Int) = max(heroes[hero].level, trainingLevel()).coerceAtMost(levelCap(hero))
    fun selectTactic(tactic: BattleTactic): Boolean { tactics[selectedTeam] = tactic; changed(); return true }
    fun numbers(hero: Int): HeroNumbers {
        val h = heroes[hero]; val base = HamletHeroes.numbers(h, facility("adventureAcademy"), effectiveLevel(hero))
        val equipment = g.armory.bonuses(hero)
        val bond = activeBonds(); val faction = factionBonus(h.def.faction)
        val army = minOf(troops[h.def.faction.ordinal], marchCapacity()) * (1.0 + (troopTier[h.def.faction.ordinal] - 1) * 0.2) * 0.6
        val pet = teamPets[selectedTeam].takeIf { it in pets.indices }?.let { pets[it] } ?: 0
        return base.copy(hp = base.hp * (1 + faction + army * 0.002 + tech[1] * 0.018 + pet * 0.01 + equipment.hpPercent),
            attack = base.attack * (1 + faction + army * 0.0015 + tech[0] * 0.018 + equipment.attackPercent + if (3 in bond) 0.08 else 0.0),
            armor = base.armor + equipment.armor,
            crit = (base.crit + equipment.crit).coerceAtMost(0.5),
            reduction = (base.reduction + if (1 in bond) 0.08 else 0.0).coerceAtMost(0.4),
            skillBoost = base.skillBoost * (1 + equipment.skillBoost) * (if (0 in bond) 1.08 else 1.0),
            haste = (base.haste + equipment.haste + if (2 in bond) 0.08 else 0.0).coerceAtMost(0.35))
    }
    fun power() = teams[selectedTeam].take(slotCount()).filter { it >= 0 }.sumOf { numbers(it).let { n -> n.hp / 10 + n.attack * 4 + n.armor * 2 } }.toLong()

    // ------------------------------------------------------------------ croissance

    fun levelCost(hero: Int): Long = effectiveLevel(hero).toLong().let { 18 + it * 7 + it * it / 40 }
    fun atLevelCap(hero: Int) = effectiveLevel(hero) >= levelCap(hero)
    fun levelUp(hero: Int): Boolean {
        if (hero !in heroes.indices || !heroes[hero].owned || atLevelCap(hero)) return false
        advanceProduction()
        if (!pay(AdventureResource.FOOD, levelCost(hero))) return false
        heroes[hero].level = effectiveLevel(hero) + 1; g.stat("hero_upgrades"); changed(); return true
    }
    fun starCost(hero: Int) = STAR_COSTS.getOrElse(heroes[hero].stars) { Long.MAX_VALUE }
    fun starLimit() = if (g.sandbox) HamletHeroes.STAR_CAP else HamletHeroes.starLimit(g.hall)
    fun canPromote(hero: Int) = heroes.getOrNull(hero)?.let { it.owned && it.stars < starLimit() && (g.sandbox || it.shards >= starCost(hero)) } == true
    fun starGainPercent(hero: Int) = if (heroes[hero].stars >= HamletHeroes.STAR_CAP) 0.0 else HamletHeroes.STAR_BONUS * 100 / (1 + heroes[hero].stars * HamletHeroes.STAR_BONUS)
    fun promote(hero: Int): Boolean {
        if (hero !in heroes.indices || !heroes[hero].owned || heroes[hero].stars >= starLimit()) return false
        val h = heroes[hero]; val cost = starCost(hero)
        if (!g.sandbox && h.shards < cost) return false
        advanceProduction()
        if (!g.sandbox) h.shards -= cost
        h.stars++; g.stat("hero_upgrades"); changed(); return true
    }
    /** Les compétences s'ouvrent après le premier boss. */
    fun skillsOpen() = g.sandbox || frontier >= FOURTH_SLOT
    fun skillCost(hero: Int, skill: Int) = (8L + heroes[hero].skills[skill] * 12L) * SINK
    /** Un rang de compétence de plus à chaque étoile, à partir du rang 3. */
    fun skillCap(hero: Int) = 3 + 2 * heroes[hero].stars
    fun studySkill(hero: Int, skill: Int): Boolean {
        if (hero !in heroes.indices || skill !in 0..3 || !heroes[hero].owned || !skillsOpen() || heroes[hero].skills[skill] >= skillCap(hero)) return false
        advanceProduction()
        if (!pay(AdventureResource.BOOKS, skillCost(hero, skill))) return false
        heroes[hero].skills[skill]++; g.stat("hero_upgrades"); changed(); return true
    }
    fun selectShardFocus(hero: Int): Boolean {
        if (hero != -1 && heroes.getOrNull(hero)?.owned != true) return false
        shardFocusHero = hero; changed(); return true
    }
    fun shardTarget(stage: Int): Int {
        if (heroes.getOrNull(shardFocusHero)?.owned == true) return shardFocusHero
        val active = teams[selectedTeam].take(slotCount()).distinct().filter { heroes.getOrNull(it)?.owned == true }
        val candidates = active.ifEmpty { heroes.filter { it.owned }.map { it.id } }
        val improving = candidates.filter { heroes[it].stars < HamletHeroes.STAR_CAP }.ifEmpty { candidates }
        return if (improving.isEmpty()) -1 else improving[Math.floorMod(stage, improving.size)]
    }

    // ------------------------------------------------------------------ l'appel

    /** Pourcentages ; le tirage exact utilise les poids entiers sur 10 000. */
    fun recruitmentOdds() = RECRUITMENT_WEIGHTS.mapValues { it.value / 100.0 }
    /** Combien d'appels libres avant la prochaine garantie épique. */
    fun epicIn() = PITY_EPIC - pityEpic
    fun scriptedLeft() = (HamletHeroes.OPENING.size - recruitmentPulls).coerceAtLeast(0)
    /**
     * Des invitations gagnées, jamais achetées. Les trois premiers appels donnent des héros écrits d'avance ;
     * ensuite chaque tirage donne un commun, un rare ou un épique (les légendes viennent des veillées).
     * Un doublon donne des fragments de son propre héros. Les garanties comptent tous les appels,
     * en lot ou non, et survivent à la sauvegarde.
     */
    fun summon(count: Int = 1): List<HeroRecruitment> {
        if (count !in 1..10 || !pay(AdventureResource.TICKETS, count.toLong())) return emptyList()
        val results = ArrayList<HeroRecruitment>(count)
        repeat(count) {
            val scripted = recruitmentPulls < HamletHeroes.OPENING.size
            val h: HeroState
            var guaranteed = false
            if (scripted) h = heroes[HamletHeroes.OPENING[recruitmentPulls]]
            else {
                val epicDue = pityEpic >= PITY_EPIC - 1
                val rareDue = pityRare >= RARE_PITY - 1
                var roll = g.rng.nextInt(RECRUITMENT_WEIGHTS.values.sum())
                val rolled = RECRUITMENT_WEIGHTS.entries.first { roll -= it.value; roll < 0 }.key
                var rarity = rolled
                if (epicDue && rarity.ordinal < HeroRarity.EPIC.ordinal) { rarity = HeroRarity.EPIC; guaranteed = true }
                else if (rareDue && rarity == HeroRarity.COMMON) { rarity = HeroRarity.RARE; guaranteed = true }
                val pool = heroes.filter { it.def.rarity == rarity }
                h = pool[g.rng.nextInt(pool.size)]
                pityEpic = if (rarity.ordinal >= HeroRarity.EPIC.ordinal) 0 else pityEpic + 1
                pityRare = if (rarity.ordinal >= HeroRarity.RARE.ordinal || pityRare < 0) -1 else pityRare + 1
            }
            val fresh = !h.owned
            if (fresh) welcome(h) else h.shards += HamletHeroes.duplicateShards(h.def.rarity)
            recruitmentPulls = (recruitmentPulls.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            results.add(HeroRecruitment(h.id, fresh, if (fresh) 0 else HamletHeroes.duplicateShards(h.def.rarity), guaranteed, scripted))
        }
        changed(); return results
    }
    /** Les traits s'éveillent à partir de cinq étoiles, un rang par étoile de plus. */
    fun traitCap(hero: Int) = (heroes[hero].stars - 4).coerceAtLeast(0)
    fun promoteTrait(hero: Int, trait: Int): Boolean {
        if (hero !in heroes.indices || trait !in 0..3 || !heroes[hero].owned || heroes[hero].traits[trait] >= traitCap(hero)) return false
        val h = heroes[hero]; val cost = 12L * (h.traits[trait] + 1)
        if (!g.sandbox && h.shards < cost) return false
        if (!g.sandbox) h.shards -= cost
        h.traits[trait]++; changed(); return true
    }
    fun researchCost(branch: Int) = (25L + tech[branch] * 15L) * SINK
    /** Les recherches montent avec le rang de l'académie. */
    fun techCap() = if (g.sandbox) 20 else 4 + 4 * facilityRank("adventureAcademy")
    fun research(branch: Int): Boolean {
        if (branch !in tech.indices || tech[branch] >= techCap() || (!g.sandbox && facility("adventureAcademy") == 0)) return false
        advanceProduction()
        if (!pay(AdventureResource.BOOKS, researchCost(branch))) return false
        tech[branch]++; g.stat("hero_upgrades"); changed(); return true
    }
    /** Un petit atelier de conversion permet de débloquer une progression sans attendre un tirage. */
    fun workshopSupplies(books: Boolean): Boolean {
        if (!g.sandbox && facility(if (books) "adventureAcademy" else "adventureStore") == 0) return false
        advanceProduction()
        val first = if (books) AdventureResource.FOOD else AdventureResource.TIMBER
        val second = if (books) AdventureResource.TIMBER else AdventureResource.STONE
        val cost = if (books) 16L else 12L
        if (!g.sandbox && (this[first] < cost || this[second] < cost)) return false
        pay(first, cost); pay(second, cost)
        add(if (books) AdventureResource.BOOKS else AdventureResource.ORE, 8)
        g.stat("supply_crafted"); changed(); return true
    }

    // ------------------------------------------------------------------ le camp

    fun productionPerMinute(): DoubleArray {
        val boost = 1 + tech[2] * 0.04 + facility("adventureStore") * 0.1
        val academy = facility("adventureAcademy"); val forge = facility("adventureForge")
        val amounts = doubleArrayOf(workers[0] * 3.0 + 1, workers[1] * 2.0, workers[2] * 2.0,
            workers[4].toDouble() + forge, workers[3].toDouble() + academy)
        return DoubleArray(amounts.size) { amounts[it] * boost * (1 + g.stewardship.productionBonus(AdventureResource.entries[it]) / 100.0) }
    }
    fun workerCapacity() = (g.population() / 2).coerceIn(3, 24)
    fun assignWorker(job: Int, delta: Int): Boolean {
        if (job !in workers.indices || delta !in listOf(-1, 1) || workers[job] + delta < 0 || (delta > 0 && workers.sum() >= workerCapacity())) return false
        advanceProduction(); workers[job] += delta; changed(); return true
    }
    /** La réserve du camp se remplit pendant au plus ce temps ; l'entrepôt l'allonge. */
    fun campCapMinutes() = ((CAMP_HOURS + CAMP_HOURS_PER_RANK * facilityRank("adventureStore")).coerceAtMost(CAMP_HOURS_MAX)) * 60L
    /** Millisecondes avant la prochaine invitation de l'auberge ; null tant qu'aucune auberge n'est terminée. */
    fun innNextIn(): Long? {
        val rank = facilityRank("adventureInn").coerceIn(0, 3)
        if (rank <= 0) return null
        return ((INN_INVITATION_UNITS - innTicketProgress) / (3 + rank)).coerceAtLeast(0)
    }
    /** Minutes avant que la réserve du camp soit pleine (0 si elle l'est). */
    fun campFullIn(): Long {
        val elapsed = ((g.now() - productionAt) / 60_000).coerceAtLeast(0)
        return (campCapMinutes() - elapsed).coerceAtLeast(0)
    }
    fun advanceProduction() {
        val t = g.now()
        if (productionAt <= 0) { productionAt = t; return }
        if (t < productionAt) return
        val minutes = (t - productionAt) / 60_000
        if (minutes <= 0) return
        val rates = productionPerMinute()
        val paid = minOf(minutes, campCapMinutes())
        for (i in rates.indices) {
            val produced = productionRemainder[i] + rates[i] * paid
            // Fractional yields belong to the player, even when returning every minute.
            val whole = kotlin.math.floor(produced + 1e-9).toLong()
            bag[i] += whole
            productionRemainder[i] = (produced - whole).coerceIn(0.0, 0.999999999999)
        }
        val productionEnd = productionAt + minutes * 60_000
        val inns = g.objs.values.filter { it.t == "adventureInn" }.map { inn ->
            (if (inn.b0 == 0L) productionAt else inn.b0 + inn.def.build * 1000L) to inn.tier.coerceIn(1, 3)
        }
        val boundaries = (listOf(productionAt, productionEnd) + inns.map { it.first }.filter { it > productionAt && it < productionEnd }).distinct().sorted()
        for ((from, until) in boundaries.zipWithNext()) {
            val rank = inns.filter { it.first <= from }.maxOfOrNull { it.second } ?: 0
            if (rank > 0) innTicketProgress += (until - from) * (3 + rank)
        }
        add(AdventureResource.TICKETS, innTicketProgress / INN_INVITATION_UNITS)
        innTicketProgress %= INN_INVITATION_UNITS
        productionAt += minutes * 60_000; changed()
    }
    fun villageAction(key: String, count: Long) {
        val n = count.coerceIn(0, 100)
        if (n <= 0) return
        when (key) {
            "harvested" -> add(AdventureResource.FOOD, n * 6)
            "products" -> { add(AdventureResource.FOOD, n * 4); add(AdventureResource.ORE, n) }
            "delivered" -> add(AdventureResource.BOOKS, n * 2)
            "workshops" -> { add(AdventureResource.TIMBER, 8); add(AdventureResource.STONE, 8); add(AdventureResource.RUNES, 2) }
        }
        val invitationWork = when (key) { "harvested" -> n; "products" -> n * 2; "delivered" -> n * 3; "workshops" -> n * 4; else -> 0 }
        recruitmentWork += invitationWork
        // Le travail du village offre quelques appels par jour, pas davantage : le reste du travail s'accumule en vain.
        val day = g.now() / Hamlet.DAY_MS
        if (day != workTicketDay) { workTicketDay = day; workTicketsToday = 0 }
        val earned = (recruitmentWork / WORK_INVITATION_POINTS).coerceAtMost((WORK_TICKETS_PER_DAY - workTicketsToday).coerceAtLeast(0).toLong())
        workTicketsToday += earned.toInt()
        add(AdventureResource.TICKETS, earned)
        recruitmentWork %= WORK_INVITATION_POINTS
    }
    fun marchCapacity() = (20 + frontier / 2 + facility("adventureYard") * 10).coerceAtMost(120)
    fun maxTroopTier() = if (g.sandbox) 10 else (1 + frontier / 20 + facilityRank("adventureYard")).coerceAtMost(10)
    fun trainTroops(faction: Int, amount: Int = 10): Boolean {
        if (faction !in troops.indices || amount !in 1..100 || (!g.sandbox && facility("adventureYard") == 0)) return false
        val trained = minOf(amount, marchCapacity() - troops[faction])
        if (trained <= 0 || !g.sandbox && (this[AdventureResource.FOOD] < trained * 2L || this[AdventureResource.ORE] < trained.toLong())) return false
        pay(AdventureResource.FOOD, trained * 2L); pay(AdventureResource.ORE, trained.toLong())
        troops[faction] += trained; changed(); return true
    }
    fun upgradeSupport(pet: Boolean, index: Int): Boolean {
        val values = if (pet) pets else relics
        if (index !in values.indices || values[index] == 0) return false
        val resource = if (pet) AdventureResource.FOOD else AdventureResource.CRYSTAL
        val cost = (if (pet) 50L * SINK else 12L) * (values[index] + 1)
        if (!pay(resource, cost)) return false
        values[index]++; changed(); return true
    }
    fun improveTroops(faction: Int): Boolean {
        if (faction !in troopTier.indices || (!g.sandbox && facility("adventureYard") == 0)) return false
        val tier = troopTier[faction]; val cost = 20L * tier * SINK
        if (tier >= maxTroopTier()) return false
        if (!g.sandbox && (this[AdventureResource.TIMBER] < cost || this[AdventureResource.STONE] < cost || this[AdventureResource.BOOKS] < 15L * tier * SINK)) return false
        pay(AdventureResource.TIMBER, cost); pay(AdventureResource.STONE, cost); pay(AdventureResource.BOOKS, 15L * tier * SINK)
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

    // ------------------------------------------------------------------ la campagne

    /** Appels offerts la première fois que l'on vient à bout de cette étape. */
    fun ticketReward(stage: Int): Long = when {
        stage == 3 || stage == 7 -> 1
        stage % 10 == 9 -> if ((stage + 1) % 40 == 0) 2 else 1
        else -> 0
    }
    fun node(stage: Int): AdventureNode {
        if (stage % 10 == 9) return AdventureNode.BOSS
        if (ticketReward(stage) > 0) return AdventureNode.TREASURE
        return when (stage % 8) { 1, 4 -> AdventureNode.GATHER; 6 -> AdventureNode.SHRINE; else -> AdventureNode.BATTLE }
    }
    fun region(stage: Int) = (stage / 20) % 6
    fun gather(stage: Int): Boolean {
        if (stage < 0 || stage > frontier || node(stage) !in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) return false
        // La cueillette est la voie sûre des matériaux : un tarif régulier, sans hasard (les combats donnent les fragments et les trouvailles).
        grantLoot(stage, first = stage == frontier, combat = false, share = if (stage == frontier) 2.0 else 1.0)
        record(stage, 3); g.stat("adventure_gathered"); changed(); return true
    }
    fun sweepCost(stage: Int) = stage.coerceAtLeast(0).let { 50L + (it / 10) * 8L + it / 2 }
    fun canSweep(stage: Int) = activeBattle == null && stage >= 0 && stage < frontier && stars[stage] == 3 && node(stage) in listOf(AdventureNode.BATTLE, AdventureNode.TREASURE, AdventureNode.BOSS)
    /** A mastered route offers a brief visit alternative; it never advances the frontier. */
    fun sweep(stage: Int): Boolean {
        if (!canSweep(stage) || !pay(AdventureResource.FOOD, sweepCost(stage))) return false
        grantLoot(stage, false, true, share = 1.0); g.stat("adventure_gathered"); changed(); return true
    }
    fun fielded() = teams[selectedTeam].take(slotCount()).count { it >= 0 && heroes[it].owned }
    fun canBattle(mode: BattleMode, stage: Int): Boolean {
        if (activeBattle != null || stage < 0 || fielded() == 0) return false
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
                val gear = g.armory.bonuses(hero)
                units.add(BattleUnit(hero, hero, false, d.faction, d.role, slot, n.hp, n.attack, n.armor,
                    d.interval * (1 - n.haste).toFloat(), d.skill * (1 - n.haste).toFloat(), d.ultimate * (1 - n.haste).toFloat(), n.crit, n.skillBoost, n.reduction, ability = d.ability,
                    healingBonus = gear.healingBonus, lifesteal = gear.lifesteal, armorPierce = gear.armorPierce, openingShield = gear.openingShield))
            }
        }
        val effective = effectiveStage(mode, stage)
        units.addAll(encounter(mode, stage))
        val relic = teamRelics[selectedTeam]; val pet = teamPets[selectedTeam]
        return HamletBattle(mode, stage, region(effective), units, Random(seed ?: g.rng.nextInt()), relic,
            relics.getOrElse(relic) { 0 }, pet, pets.getOrElse(pet) { 0 }, auto, tactics[selectedTeam]).also { activeBattle = it }
    }
    fun effectiveStage(mode: BattleMode, stage: Int) = when (mode) { BattleMode.CAMPAIGN -> stage; BattleMode.TRIAL -> stage * 2 + 8; BattleMode.BOUNTY -> stage * 2 + 3; BattleMode.RAID -> stage * 3 + 16; BattleMode.ARENA -> stage * 2 + 10 }
    /** La force des ennemis d'une étape : régulière au début, puis de plus en plus raide jusqu'à l'étape 80. */
    fun enemyScale(effective: Int): Double {
        val base = 0.8 + effective * 0.12 + effective.toDouble() * effective * 0.00035
        val t = ((effective - 12) / 68.0).coerceIn(0.0, 1.0)
        return base * (1.0 + 1.7 * t * t * (3 - 2 * t))
    }
    /** Combien d'ennemis : un seul au tout début, puis de plus en plus, jamais plus que les alliés n'en peuvent porter. */
    fun foeCount(effective: Int, boss: Boolean, allies: Int): Int {
        if (boss) return if (allies <= 1) 1 else if (allies == 2) 2 else 3
        val base = when { effective < 2 -> 1; effective < 6 -> 2; effective < 25 -> 3; effective < 60 -> 4; else -> 5 }
        return base.coerceAtMost(allies + 2)
    }
    /** Aperçu et moteur partagent la même rencontre, sans consommer le hasard de la sauvegarde. */
    fun encounter(mode: BattleMode, stage: Int): List<BattleUnit> {
        val effective = effectiveStage(mode, stage)
        val region = region(effective)
        val boss = mode == BattleMode.RAID || mode == BattleMode.CAMPAIGN && node(stage) == AdventureNode.BOSS || mode == BattleMode.TRIAL && stage % 5 == 4
        val scale = enemyScale(effective)
        val count = foeCount(effective, boss, fielded().coerceAtLeast(1))
        return (0 until count).map { i ->
            if (mode == BattleMode.ARENA) {
                val line = intArrayOf(1, 6, 0, 3, 5, 7)
                val def = HamletHeroes.defs[(line[i] + (stage / 4) * 3) % heroes.size]
                BattleUnit(100 + i, def.id, true, def.faction, def.role, i, def.hp * scale * 0.8,
                    def.attack * scale * 0.8, def.armor + scale * 2, def.interval, def.skill, def.ultimate, heroVisual = true, ability = def.ability)
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
                val first = stage == frontier
                grantLoot(stage, first, true, recent = stage >= frontier - FARM_WINDOW)
                record(stage, if (battle.survivors() >= battle.fielded()) 3 else 2)
                if (first) add(AdventureResource.TICKETS, ticketReward(stage))
            }
            BattleMode.TRIAL -> { val first = stage == trial; if (first) trial++; grantLoot(stage * 2 + 8, first, true); add(AdventureResource.RUNES, extra(first, 8L + stage)) }
            BattleMode.BOUNTY -> {
                val first = stage == bounty
                if (first) bounty++
                grantLoot(stage * 2 + 3, first, true)
                if (first && stage % 5 == 4) add(AdventureResource.TICKETS, 1)
                if (stage >= bounty - 1 - FARM_WINDOW) heroes.getOrNull(shardTarget(stage))?.let { it.shards += 3 }
            }
            BattleMode.RAID -> { val first = stage == raid; if (first) raid++; grantLoot(stage * 3 + 16, first, true); add(AdventureResource.CRYSTAL, extra(first, 12L + stage * 2L)) }
            BattleMode.ARENA -> { val first = stage == arena; if (first) arena++; grantLoot(stage * 2 + 10, first, true); add(AdventureResource.BOOKS, extra(first, 20L + stage * 3L)) }
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
    /** La prime propre à un mode : entière la première fois, au quart ensuite. */
    private fun extra(first: Boolean, base: Long) = if (first) base else max(1L, Math.round(base * REPEAT_SHARE))
    /** Ce que la dernière victoire a rapporté de remarquable : la première fois, ou une trouvaille (pour l'écran de résultat). */
    var lastFirst = false; private set
    var lastFind = false; private set
    /**
     * Une première victoire paie largement. La routine ensuite ne rapporte presque rien, mais réserve parfois une belle
     * trouvaille. Le tirage ne dépend que de l'historique des victoires : rejouer le même combat n'y change rien.
     * [recent] : seules les étapes proches de la frontière donnent des fragments, pour ne pas écumer les premières.
     * [share] remplace la part habituelle (la visite rapide paie un tarif fixe).
     */
    private fun grantLoot(stage: Int, first: Boolean, combat: Boolean, recent: Boolean = true, share: Double? = null) {
        val scale = 1 + stage / 10
        val boss = node(stage) == AdventureNode.BOSS
        val roll = Random((g.stats["battles_won"] ?: 0L) * 7919L + (g.stats["adventure_gathered"] ?: 0L) * 104_729L + stage * 131L).nextDouble()
        val find = share == null && !first && roll < (if (boss) FIND_BOSS else FIND_CHANCE)
        lastFirst = first && share == null; lastFind = find
        val part = share ?: when { first -> FIRST_SHARE; find -> FIND_SHARE; else -> REPEAT_SHARE }
        fun share(base: Long) = max(1L, Math.round(base * part))
        add(AdventureResource.FOOD, share(25L + scale * 8))
        add(AdventureResource.TIMBER, share(15L + scale * 4))
        add(AdventureResource.STONE, share(12L + scale * 4))
        add(AdventureResource.ORE, share(6L + scale * 2))
        add(AdventureResource.BOOKS, share(3L + scale))
        add(AdventureResource.RUNES, share(2L + scale))
        if (boss) add(AdventureResource.CRYSTAL, share(4L + scale))
        if (combat && recent) {
            // Un fragment tous les douze combats gagnés, trois d'un coup contre un boss, un de plus pour une trouvaille.
            val target = heroes.getOrNull(shardTarget(stage))
            if (target != null) {
                shardDrip += (if (boss) 3 else 1) + (if (find) SHARD_WINS else 0)
                while (shardDrip >= SHARD_WINS) { shardDrip -= SHARD_WINS; target.shards += 1 }
            }
        }
        g.journey.supplies += 1 + scale
        g.workSeconds = (g.workSeconds + (if (boss) 180 else 45) + tech[3] * 5).coerceAtMost(7200)
        g.reward(null, share(35L + scale * 15).toInt(), share(8L + scale * 3).toInt())
    }
    fun copyFrom(other: HamletAdventure) {
        other.bag.copyInto(bag); other.tech.copyInto(tech); other.workers.copyInto(workers)
        other.troops.copyInto(troops); other.troopTier.copyInto(troopTier)
        other.relics.copyInto(relics); other.pets.copyInto(pets); other.teamPets.copyInto(teamPets); other.teamRelics.copyInto(teamRelics)
        other.teams.forEachIndexed { i, team -> team.copyInto(teams[i]) }
        other.tactics.copyInto(tactics)
        selectedTeam = other.selectedTeam; frontier = other.frontier; trial = other.trial; bounty = other.bounty; raid = other.raid; arena = other.arena
        stars.clear(); stars.putAll(other.stars); productionAt = other.productionAt; auto = other.auto; activeBattle = null
        other.productionRemainder.copyInto(productionRemainder)
        recruitmentPulls = other.recruitmentPulls; pityEpic = other.pityEpic; pityRare = other.pityRare
        recruitmentWork = other.recruitmentWork; innTicketProgress = other.innTicketProgress
        shardFocusHero = other.shardFocusHero; shardDrip = other.shardDrip
        workTicketDay = other.workTicketDay; workTicketsToday = other.workTicketsToday
        other.heroes.forEachIndexed { i, source ->
            val h = heroes[i]; h.owned = source.owned; h.level = source.level; h.stars = source.stars; h.shards = source.shards
            source.skills.copyInto(h.skills); source.traits.copyInto(h.traits)
        }
    }
    companion object {
        const val MAX_SLOTS = 5
        /** Étape de campagne qui ouvre la quatrième, puis la cinquième place. */
        const val FOURTH_SLOT = 10
        const val FIFTH_SLOT = 40
        const val PITY_EPIC = 80
        /** Le huitième appel libre sans rare en garantit un. */
        const val RARE_PITY = 8
        const val WORK_INVITATION_POINTS = 400L
        const val WORK_TICKETS_PER_DAY = 2
        const val INN_INVITATION_UNITS = 345_600_000L
        const val SHARD_WINS = 12
        /** Parts du butin de base : première victoire, routine, trouvaille rare (une victoire sur 12, une sur 6 contre un boss). */
        const val FIRST_SHARE = 4.0
        const val REPEAT_SHARE = 0.25
        const val FIND_SHARE = 6.0
        const val FIND_CHANCE = 0.08
        const val FIND_BOSS = 0.16
        /** Livres, bois, pierre : les améliorations de compétences, de recherche et de milice coûtent ce multiple. */
        const val SINK = 4L
        /** Les fragments ne tombent que des étapes de ce nombre de rangs sous la frontière, au plus. */
        const val FARM_WINDOW = 8
        const val CAMP_HOURS = 12
        const val CAMP_HOURS_PER_RANK = 4
        const val CAMP_HOURS_MAX = 24
        val STAR_COSTS = longArrayOf(4, 7, 12, 20, 35, 60, 100, 160, 250, 400)
        val RECRUITMENT_WEIGHTS = linkedMapOf(HeroRarity.COMMON to 7800, HeroRarity.RARE to 2000, HeroRarity.EPIC to 200)
        val FACILITIES = listOf("adventureInn", "adventureAcademy", "adventureForge", "adventureStore", "adventureYard", "adventureGate")
    }
}
