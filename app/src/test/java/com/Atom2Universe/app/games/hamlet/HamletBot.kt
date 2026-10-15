package com.Atom2Universe.app.games.hamlet

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Horloge simulée : le jeu lit [t] à chaque appel, le robot la fait avancer. */
const val DAY = 86_400_000L

class SimClock(var t: Long) { fun now() = t }

/**
 * Un rythme de jeu : les séances de la journée (minute du jour de début, durée en minutes).
 * Les mesures décrivent un joueur simulé, pas tous les joueurs.
 */
class Rhythm(val name: String, val sessions: List<Pair<Int, Int>>) {
    val minutesPerDay get() = sessions.sumOf { it.second }
    companion object {
        /** Plusieurs heures par jour : matin, midi, soir. */
        val PASSIONATE = Rhythm("passionne", listOf(7 * 60 + 30 to 70, 12 * 60 + 30 to 50, 19 * 60 to 100))
        /** Environ une demi-heure en quatre visites. */
        val REGULAR = Rhythm("regulier", listOf(8 * 60 to 8, 12 * 60 + 30 to 8, 18 * 60 to 8, 22 * 60 to 8))
        /** Cinq minutes, deux à trois fois par jour. */
        val CASUAL = Rhythm("occasionnel", listOf(8 * 60 to 5, 13 * 60 to 5, 20 * 60 to 5))
        val MINIMAL = Rhythm("minimal", listOf(19 * 60 to 5))
    }
}

/**
 * Le joueur simulé. Il joue avec les vraies règles publiques de [HamletGame] : jamais d'argent injecté, jamais
 * de ressource inventée. Chaque geste coûte du temps actif, les combats durent leur vraie durée à vitesse ×2.
 */
class HamletBot(val rhythm: Rhythm, val seed: Int, startMs: Long = (1_800_000_000_000L / DAY) * DAY + 6 * 3_600_000L) {
    val clock = SimClock(startMs)
    val rng = Random(seed)
    val g = HamletGame(clock::now, Random(seed)).apply { startNew(false) }
    val a get() = g.adventure
    private val startMs = startMs
    var activeSeconds = 0.0
    var battles = 0; var defeats = 0
    /** Jour (depuis le début) où chaque événement s'est produit. */
    val milestones = LinkedHashMap<String, Double>()
    val timeline = StringBuilder("day,active_min,level,coins,hall,chapter,frontier,heroes,commons,rares,epics,legends,avg_level,avg_stars,shards,tickets,food,crystal,quest,upgrades,pantry_total,pulls\n")
    private var farmLeft = 0
    private var frontierFails = 0
    private var sessionEnd = 0L
    private var nextSessionStart = 0L

    val day get() = (clock.t - startMs) / DAY.toDouble()
    private fun mark(name: String) { if (name !in milestones) milestones[name] = day }

    private fun spend(seconds: Double) {
        clock.t += (seconds * 1000).toLong(); activeSeconds += seconds
        g.tick()
    }
    private fun action(count: Int = 1) = spend(1.4 * count)

    // ------------------------------------------------------------------ boucle principale

    fun run(days: Int, snapshotEvery: Double = 1.0) {
        val base = (startMs / DAY) * DAY
        val sessions = rhythm.sessions.sortedBy { it.first }
        var nextSnap = startMs
        for (d in 0 until days + 1) for ((i, s) in sessions.withIndex()) {
            val begin = base + d * DAY + s.first * 60_000L
            if (begin >= startMs + days * DAY) break
            nextSessionStart = if (i + 1 < sessions.size) base + d * DAY + sessions[i + 1].first * 60_000L else base + (d + 1) * DAY + sessions[0].first * 60_000L
            if (begin > clock.t) { clock.t = begin; g.tick() }
            sessionEnd = min(clock.t + s.second * 60_000L, startMs + days * DAY)
            // une reprise passe par la même sérialisation que l'application
            check(HamletSave.fromJson(g, HamletSave.toJson(g))) { "sauvegarde illisible" }
            session()
            while (clock.t >= nextSnap) { snapshot(); nextSnap += (snapshotEvery * DAY).toLong() }
            milestonesCheck()
        }
        snapshot()
    }

    private fun session() {
        villageChores()
        var guard = 0
        while (clock.t < sessionEnd && guard++ < 100_000) {
            managePower()
            // sans héros, le village occupe : on y revient toutes les minutes tant que la séance dure
            if (a.fielded() == 0) { spend(60.0); villageChores(); continue }
            val until = min(sessionEnd, clock.t + 300_000L)
            while (clock.t < until) fight()
            if (clock.t < sessionEnd) villageChores()
        }
    }

    // ------------------------------------------------------------------ village

    private fun villageChores() {
        g.tick()
        spend(5.0)
        collect()
        g.openGift().also { if (it) action() }
        // un pourboire environ toutes les 70 s de présence
        repeat((6 + rng.nextInt(4))) { if (rng.nextDouble() < .35) g.tip() }
        for (i in 0..2) { if (g.claimDaily(i)) action(); if (g.stewardship.claim(i)) action() }
        if (g.stewardship.claimChest()) action()
        while (g.currentQuest()?.let { g.questDone(it) } == true) { g.claimQuest(); action() }
        if (g.journey.collectExpedition()) action()
        while (g.journey.canClaim()) { g.journey.claim(); action() }
        plantFields()
        feedAndOrders()
        if (a[AdventureResource.FOOD] < 1000 && HamletIndustry.prepareRations(g)) action()
        if (g.journey.expedition >= 0 && g.workSeconds < 3600 && HamletIndustry.supplyBuilders(g)) action()
        if (HamletIndustry.welcomeCaravan(g)) action()
        developJourney()
        spendWork()
        shop()
        legends()
        useTickets()
        mark("level_${g.level() / 5 * 5}")
    }

    private fun collect() {
        if (g.canCollectAll()) { val n = g.collectAll(); if (n > 0) action(1 + n / 8) }
        else for (o in g.objs.values.toList()) if (g.collectReady(o)) action()
    }

    private fun feedAndOrders() {
        for (o in g.objs.values.toList()) {
            if (g.status(o) == Status.ORDER_NEW && g.acceptOrder(o)) action()
            if (g.status(o) == Status.FEED && g.coins > 60 && g.feed(o)) action()
        }
    }

    /** Les récoltes dont le bot a besoin : améliorations visées, veillées proches, quête en cours. */
    private fun wantedCrops(): Map<String, Int> {
        val want = HashMap<String, Int>()
        fun ask(crop: String, units: Int) { want[crop] = max(want[crop] ?: 0, units) }
        for (i in HamletLegends.defs.indices) if (g.legends.whispered(i) && !g.legends.running(i)) g.legends.phase(i)?.needs?.forEach { ask(it.crop, it.units) }
        upgradeTarget()?.let { o -> g.upgradeNeeds(o).forEach { ask(it.crop, it.units) } }
        for (id in HamletAdventure.FACILITIES) {
            val o = g.objs.values.filter { it.t == id }.maxByOrNull { it.tier } ?: continue
            if (g.canUpgrade(o)) g.upgradeNeeds(o).forEach { ask(it.crop, it.units) }
        }
        for (o in g.objs.values) {
            if (o.def.cat == Cat.SHOP) HamletIndustry.recipe(o.t, o.tier).cost.crops.forEach { (crop, n) -> ask(crop, n * 3) }
            if (HamletLife.produce[o.t]?.feed?.let { it > 0 } == true) HamletIndustry.feed(o).crops.forEach { (crop, n) -> ask(crop, n * 5) }
        }
        val wanted = want.filter { g.stock(it.key) < it.value }.toMutableMap()
        // une quête de récolte compte les récoltes faites, pas la réserve
        val q = g.currentQuest()
        if (q != null && q.key.startsWith("harvest_") && !g.questDone(q)) wanted[q.key.removePrefix("harvest_")] = 1000
        return wanted
    }

    /** Les récoltes qu'un joueur garde de côté pour la prochaine nuit d'une légende ouverte. */
    private fun reservedCrops(): Map<String, Int> {
        val reserve = HashMap<String, Int>()
        for (i in HamletLegends.defs.indices) if (g.legends.opened(i) && !g.legends.owned(i) && !g.legends.running(i))
            g.legends.phase(i)?.needs?.forEach { reserve[it.crop] = max(reserve[it.crop] ?: 0, it.units) }
        return reserve
    }
    /** Une amélioration qui n'entame pas la réserve d'une veillée. */
    private fun spare(o: Obj): Boolean { val reserve = reservedCrops(); return g.upgradeNeeds(o).all { g.stock(it.crop) - it.units >= (reserve[it.crop] ?: 0) } }

    private fun upgradeTarget(): Obj? = g.objs.values.filter { g.canUpgrade(it) }
        .minByOrNull { g.upgradeCost(it).toDouble() / (it.def.tax + it.def.pay + it.def.cost / 30 + 1) }

    private fun plantFields() {
        val wanted = wantedCrops()
        val untilNext = (nextSessionStart - clock.t).coerceAtLeast(60_000L)
        val inSession = (sessionEnd - clock.t).coerceAtLeast(0L)
        val fields = g.objs.values.filter { it.def.cat == Cat.FIELD }
        // un tiers des champs cultive des ingrédients ; quand une légende s'ouvre, un joueur motivé y consacre la majorité de ses champs
        val legendOpen = HamletLegends.defs.indices.any { g.legends.opened(it) && !g.legends.owned(it) }
        var neededRoom = max(1, (fields.size * (if (legendOpen) 0.6 else 0.34)).toInt()) - fields.count { it.crop != null && it.crop in wanted }
        for (o in g.objs.values.toList()) {
            if (o.def.cat != Cat.FIELD || o.crop != null || g.buildLeft(o) > 0) continue
            val options = Hamlet.crops.filter { it.lvl <= g.level() && it.magic == o.def.magic && it.cost <= g.coins - 30 }
            if (options.isEmpty()) continue
            val fitsReturn = options.filter { it.time * 1000L <= untilNext }
            val short = options.filter { it.time * 1000L <= inSession }
            // une culture dont on a besoin se plante même si elle ne sera mûre qu'après quelques visites
            val needed = if (neededRoom <= 0) null else options.filter { it.id in wanted && it.time <= 72 * 3600 }.maxByOrNull { wanted.getValue(it.id) - g.stock(it.id) }
            val crop = needed
                ?: if (short.isNotEmpty() && inSession > 120_000L) short.maxByOrNull { (it.earn - it.cost).toDouble() / it.time }
                else fitsReturn.maxByOrNull { it.earn - it.cost } ?: options.minByOrNull { it.time }
            if (crop != null && g.plant(o, crop.id)) { action(); if (crop.time * 1000L > inSession) g.tend(o); if (crop.id in wanted) neededRoom-- }
        }
    }

    private fun developJourney() {
        val j = g.journey
        val chapter = HamletJourney.chapters.getOrNull(j.chapter)
        val studyGoal = chapter?.goals?.firstOrNull { it.key == "research" }?.target ?: 13
        if (j.research.sum() < studyGoal) j.research.indices.sortedBy { j.research[it] }
            .firstOrNull { j.canResearch(it) && g.coins > j.researchCoins(it) + 200 }?.let { j.study(it); action(2) }
        if (j.expedition < 0) {
            val needed = chapter?.goals?.firstOrNull { it.key.startsWith("region_") && j.progress(it) < it.target }?.key?.removePrefix("region_")?.toInt()
            val region = needed?.takeIf { j.canExplore(it) } ?: HamletJourney.regions.indices.lastOrNull { j.canExplore(it) }
            if (region != null) { j.startExpedition(region); action(2) }
        }
    }

    private fun spendWork() {
        if (g.workSeconds <= 0) return
        if (g.journey.expedition >= 0 && g.journey.expeditionLeft() > 0 && g.workOnExpedition()) { action(); return }
        g.objs.values.filter { g.buildLeft(it) > 0 }.firstOrNull()?.let { if (g.applyWork(it)) action() }
    }

    private fun count(id: String) = g.objs.values.count { it.t == id }

    private fun buy(id: String, reserve: Int = 40): Boolean {
        val d = Hamlet.byId[id] ?: return false
        if (!g.journey.unlocked(d) || g.coins < d.cost + reserve || d.gems > 0) return false
        val origin = g.realmOrigin()
        for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) if (g.canPlace(id, x, y, false)) {
            if (g.buy(id, x, y, false) == null) return false
            action(2); return true
        }
        return false
    }

    /** Les achats, dans l'ordre où un joueur sensé les ferait. Rend vrai quand un achat a eu lieu. */
    private fun shop() {
        repeat(4) { shopOnce() }
    }
    private fun shopOnce(): Boolean {
        val chapter = HamletJourney.chapters.getOrNull(g.journey.chapter)
        chapter?.goals?.forEach { goal ->
            if (goal.key.startsWith("built_") && g.journey.progress(goal) < goal.target && buy(goal.key.removePrefix("built_"))) return true
        }
        val q = g.currentQuest()
        if (q != null && q.key.startsWith("built_") && !g.questDone(q) && buy(q.key.removePrefix("built_"))) return true
        if (count("field") < 4 && buy("field")) return true
        if (count("cottage") < 3 && buy("cottage")) return true
        for (id in listOf("bakery", "lumbermill", "quarry")) if (count(id) == 0 && buy(id, 100)) return true
        for (id in HamletAdventure.FACILITIES) if (count(id) == 0 && buy(id, 100)) return true
        // les bâtiments de l'aventure montent en rang dès que possible : l'équipement et le camp en dépendent
        for (id in HamletAdventure.FACILITIES) {
            val o = g.objs.values.filter { it.t == id }.maxByOrNull { it.tier } ?: continue
            if (g.canAffordUpgrade(o) && spare(o) && g.coins > g.upgradeCost(o) + 100 && g.upgrade(o)) { action(); return true }
        }
        if (count("field") < min(30, 6 + g.level() / 2) && buy("field")) return true
        if (count("cottage") < 5 && buy("cottage")) return true
        for (id in listOf("bakery", "henyard", "treeOrange", "treeApple", "well", "florist", "beehive", "duckpond", "farmmarket", "sheepmeadow", "jamkitchen", "goathill", "dairyfarm"))
            if (count(id) == 0 && buy(id)) return true
        if (q != null && !g.questDone(q)) when (q.key) {
            "roads", "water", "bridges" -> {
                val id = when (q.key) { "roads" -> "road"; "water" -> "river"; else -> "bridge" }
                val origin = g.realmOrigin()
                for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) if (g.objAt(x, y) == null && g.paint(id, x, y)) { action(); return true }
            }
            "cat_decor" -> if (buy("bench")) return true
            "cat_nature" -> if (buy("flowersRed")) return true
            "expanded" -> if (g.coins > g.expandCost() + 100 && g.expandLand()) { action(); return true }
            "castle", "hall" -> {}
        }
        val next = g.nextHall()
        if (next != null && g.level() >= next.lvl && g.journey.canUpgradeHall() && g.coins > next.cost + 150) { if (g.upgradeHall()) { action(2); return true } }
        // les bâtiments de la liste du joueur, par ordre de prix
        if (count("stonecircle") == 0 && g.journey.chapter >= 4 && buy("stonecircle", 200)) return true
        val up = g.objs.values.filter { g.canAffordUpgrade(it) && spare(it) && g.coins > g.upgradeCost(it) + 150 }
            .minByOrNull { g.upgradeCost(it).toDouble() / (it.def.tax + it.def.pay + it.def.cost / 30 + 1) }
        if (up != null && g.upgrade(up)) { action(); return true }
        val candidates = Hamlet.defs.filter { it.cat in setOf(Cat.HOME, Cat.SHOP, Cat.RANCH) && g.journey.unlocked(it) && it.gems == 0 && !it.shore && count(it.id) == 0 }.sortedBy { it.cost }
        for (d in candidates) if (g.coins > d.cost + 400 && buy(d.id, 400)) return true
        if (g.canExpand() && g.coins > g.expandCost() * 3 / 2) { if (g.expandLand()) { action(); return true } }
        return false
    }

    private fun legends() {
        for (i in HamletLegends.defs.indices) {
            if (g.legends.canFinish(i)) { g.legends.finish(i)?.recruit?.let { mark("legend_${it.hero}") }; action(3) }
            if (g.legends.canBegin(i) && g.legends.begin(i)) action(2)
        }
    }

    // ------------------------------------------------------------------ héros

    private fun useTickets() {
        while (a[AdventureResource.TICKETS] > 0) {
            val n = min(10L, a[AdventureResource.TICKETS]).toInt()
            val results = a.summon(n)
            if (results.isEmpty()) break
            action(1 + results.count { it.newHero })
            if (a.fielded() > 0) mark("first_hero")
        }
    }

    private fun strength(id: Int) = a.numbers(id).let { it.hp / 10 + it.attack * 4 + it.armor * 2 }

    /** Équipe, niveaux, étoiles, compétences, équipement : tout ce qu'un joueur applique entre deux combats. */
    private fun managePower() {
        useTickets()
        val slots = a.slotCount()
        // les places vides se remplissent, puis l'équipe se recompose parmi les meilleurs
        val owned = a.heroes.filter { it.owned }
        if (owned.isNotEmpty()) {
            val current = a.teams[a.selectedTeam].take(slots).filter { it >= 0 }
            val bestSet = owned.sortedByDescending { strength(it.id) }.take(slots).map { it.id }.toSet()
            if (current.size < min(slots, owned.size) || a.teams[a.selectedTeam].take(slots).toSet().filter { it >= 0 }.toSet() != bestSet && rng.nextInt(20) == 0) {
                a.autoFormation(); action(2)
            }
        }
        val team = a.teams[a.selectedTeam].take(slots).filter { it >= 0 }
        if (team.isEmpty()) return
        // les fragments vont au héros d'équipe qui a le moins d'étoiles
        a.selectShardFocus(team.minByOrNull { a.heroes[it].stars * 100 + a.heroes[it].level }!!)
        var guard = 0
        while (guard++ < 400) {
            var did = false
            for (id in team.sortedBy { a.heroes[it].level }) {
                if (a.canPromote(id) && a.promote(id)) { action(); did = true }
            }
            val candidates = team.filter { !a.atLevelCap(it) }.sortedBy { a.heroes[it].level }
            for (id in candidates) { if (a.levelUp(id)) { action(); did = true; break } }
            if (!did) break
        }
        for (id in team) {
            if (a.skillsOpen()) for (s in 0..3) if (a.studySkill(id, s)) action()
        }
        gear(team)
        for (b in 0..3) if (a.research(b)) action()
        for (f in 0..2) { if (a.trainTroops(f)) action(); if (a.improveTroops(f)) action() }
        for (i in 0..2) { if (a.upgradeSupport(true, i)) action(); if (a.upgradeSupport(false, i)) action() }
        if (a.pets[0] > 0) a.selectSupport(true, 0)
        if (a.relics[0] > 0) a.selectSupport(false, 0)
        // les ouvriers vont d'abord à la nourriture, aux livres et au minerai, un peu au bois et à la pierre
        val share = doubleArrayOf(4.0, 1.0, 1.0, 3.0, 3.0)
        while (a.workers.sum() < a.workerCapacity()) {
            val job = share.indices.minBy { a.workers[it] / share[it] }
            if (!a.assignWorker(job, 1)) break
        }
        a.workshopSupplies(true); a.workshopSupplies(false)
    }

    private fun gear(team: List<Int>) {
        val armory = g.armory
        if (armory.forgeRank() == 0) return
        for (id in team) for (slot in 0..3) {
            if (armory.equipped(id, slot) == null) {
                val set = armory.recommendedSet(id) ?: continue
                for (rarity in EquipmentRarity.entries.reversed()) {
                    if (!armory.unlocked(rarity)) continue
                    val piece = armory.craft(slot, set, rarity) ?: continue
                    armory.equip(id, piece.id); action(2); break
                }
            }
        }
        var guard = 0
        while (guard++ < 60) {
            val pieces = team.flatMap { id -> (0..3).mapNotNull { armory.equipped(id, it) } }
            // d'abord la qualité (une pièce à son maximum de niveau passe au rang supérieur), puis les niveaux
            val better = pieces.filter { it.level >= HamletArmory.MAX_LEVEL }.firstOrNull { armory.canUpgradeQuality(it.id) }
            if (better != null && armory.upgradeQuality(better.id)) { action(); continue }
            val piece = pieces.sortedBy { it.level }.firstOrNull { armory.canEnhance(it.id) } ?: break
            if (!armory.enhance(piece.id)) break
            action()
        }
    }

    // ------------------------------------------------------------------ combats

    private fun fight() {
        if (a.teams[a.selectedTeam].take(a.slotCount()).none { it >= 0 }) { spend(10.0); return }
        var stage = a.frontier
        if (a.node(stage) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) { a.gather(stage); spend(16.0); return }
        if (farmLeft > 0) {
            farmLeft--
            // une étape récente et maîtrisée, d'où tombent encore les fragments
            stage = (max(0, a.frontier - 3) until a.frontier).reversed().firstOrNull { a.node(it) !in listOf(AdventureNode.GATHER, AdventureNode.SHRINE) } ?: a.frontier
        }
        val before = a.frontier
        val b = a.beginBattle(BattleMode.CAMPAIGN, stage, rng.nextInt()) ?: run { spend(5.0); return }
        b.advance(120f)
        a.settle(b)
        battles++
        spend(b.time / 2.0 + 4.0)
        if (b.outcome == BattleOutcome.DEFEAT) {
            defeats++
            if (stage >= before) { frontierFails++; if (frontierFails >= 2) farmLeft = 8 }
        } else if (a.frontier > before) frontierFails = 0
        // à chaque série de combats, un coup d'œil à la progression des héros
        if (battles % 3 == 0) managePower()
    }

    // ------------------------------------------------------------------ mesures

    private fun milestonesCheck() {
        if (a.ownedCount() >= 1) mark("heroes_1")
        for (n in listOf(2, 3, 5, 8, 12, 16, 20, 24, 28, 32, 36)) if (a.ownedCount() >= n) mark("heroes_$n")
        for (n in listOf(10, 20, 40, 60, 80, 100, 150, 200, 240)) if (a.frontier >= n) mark("frontier_$n")
        for (n in listOf(2, 4, 6, 8, 10, 12)) if (g.journey.chapter >= n) mark("chapter_$n")
        for (n in listOf(2, 3, 4, 5, 6, 7, 8)) if (g.hall >= n) mark("hall_$n")
        for (n in listOf(5, 10, 15, 20, 25, 30, 35, 40)) if (g.level() >= n) mark("level_$n")
        if (a.slotCount() >= 4) mark("slot_4")
        if (a.slotCount() >= 5) mark("slot_5")
        val team = a.teams[a.selectedTeam].take(a.slotCount()).filter { it >= 0 }
        if (team.isNotEmpty()) {
            val stars = team.map { a.heroes[it].stars }.average()
            for (n in listOf(1, 2, 3, 5, 7, 10)) if (team.size >= 3 && stars >= n) mark("team_stars_$n")
            val lvl = team.map { a.heroes[it].level }.average()
            for (n in listOf(10, 20, 30, 50, 70, 90)) if (lvl >= n) mark("team_level_$n")
        }
        if (HamletLegends.defs.all { g.legends.owned(HamletLegends.defs.indexOf(it)) }) mark("all_legends")
        if (a.heroes.all { it.owned }) mark("all_heroes")
    }

    private fun snapshot() {
        val team = a.teams[a.selectedTeam].take(a.slotCount()).filter { it >= 0 }
        val owned = a.heroes.filter { it.owned }
        timeline.append(listOf(
            "%.2f".format(java.util.Locale.ROOT, day), (activeSeconds / 60).toInt(), g.level(), g.coins, g.hall, g.journey.chapter, a.frontier, owned.size,
            owned.count { it.def.rarity == HeroRarity.COMMON }, owned.count { it.def.rarity == HeroRarity.RARE }, owned.count { it.def.rarity == HeroRarity.EPIC },
            owned.count { it.def.rarity.ordinal >= HeroRarity.LEGENDARY.ordinal },
            if (team.isEmpty()) 0 else team.map { a.heroes[it].level }.average().toInt(),
            if (team.isEmpty()) "0" else "%.1f".format(java.util.Locale.ROOT, team.map { a.heroes[it].stars }.average()),
            owned.sumOf { it.shards }, a[AdventureResource.TICKETS], a[AdventureResource.FOOD], a[AdventureResource.CRYSTAL], g.quest, g.stats["upgrades"] ?: 0,
            g.pantry.values.sum(), a.recruitmentPulls,
        ).joinToString(",")).append('\n')
    }

    /** Le détail de l'équipe : à quoi tient sa force. */
    fun teamReport(): String {
        val team = a.teams[a.selectedTeam].take(a.slotCount()).filter { it >= 0 }
        return team.joinToString("\n") { id ->
            val h = a.heroes[id]; val n = a.numbers(id)
            "  hero $id ${h.def.role} ${h.def.rarity} niv=${h.level}/${a.levelCap(id)} etoiles=${h.stars} ppt=${(n.hp / 10 + n.attack * 4 + n.armor * 2).toInt()} hp=${n.hp.toInt()} atk=${n.attack.toInt()} arm=${n.armor.toInt()} " +
                "pieces=${(0..3).map { g.armory.equipped(id, it)?.let { p -> "${p.rarity.name[0]}${p.level}" } ?: "-" }} comp=${h.skills.toList()}"
        } + "\n  troupes=${a.troops.toList()}/${a.troopTier.toList()} tech=${a.tech.toList()} ouvriers=${a.workers.toList()} frontiere=${a.frontier} bonus_faction=${HeroFaction.entries.map { (a.factionBonus(it) * 100).toInt() }}"
    }

    fun chapterReport(): String {
        val c = HamletJourney.chapters.getOrNull(g.journey.chapter) ?: return "chapitres terminés"
        return "chapitre ${g.journey.chapter + 1}: niveau ${g.level()}/${c.level} halle ${g.hall}/${c.hall} " + c.goals.joinToString(" ") { "${it.key}=${g.journey.progress(it)}/${it.target}" } +
            " | exped=${g.journey.explored.toList()} recherche=${g.journey.research.toList()} habitants=${g.population()}"
    }

    fun legendReport(): String = HamletLegends.defs.indices.joinToString(" | ") { i ->
        val p = g.legends.phase(i)
        "L$i whisp=${g.legends.whispered(i)} open=${g.legends.opened(i)} cercle=${g.legends.circleBuilt()} nuits=${g.legends.nights[i]} pay=${g.legends.canPay(i)} " +
            (p?.let { "or=${g.coins >= it.coins} crist=${a[AdventureResource.CRYSTAL]}/${it.crystal} stock=" + it.needs.joinToString(",") { n -> "${n.crop}:${g.stock(n.crop)}/${n.units}" } } ?: "")
    } + " pierres=${count("stonecircle")} reserve=${g.pantryCap()} champs=${g.objs.values.filter { it.def.cat == Cat.FIELD }.groupingBy { it.crop ?: "-" }.eachCount()} voulu=${wantedCrops()}"

    fun summary(): String = milestones.entries.joinToString("  ") { "${it.key}=${"%.2f".format(java.util.Locale.ROOT, it.value)}" }
}
