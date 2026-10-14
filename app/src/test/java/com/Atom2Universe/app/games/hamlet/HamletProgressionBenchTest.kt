package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.ceil
import kotlin.random.Random

/** Banc de progression intégré demandé par le joueur. Aucun gain ni déblocage injecté.
 * Les gestes coûtent du temps actif et les batailles utilisent le moteur réel à vitesse x2.
 * La politique est volontairement imparfaite : ces mesures ne prouvent pas le plaisir de jeu.
 */
class HamletProgressionBenchTest {
    private data class Profile(val name: String, val days: Int, val visits: Int, val minutes: Int)
    private data class Snapshot(val profile: String, val seed: Int, val day: Double, val active: Double,
        val route: Int, val level: Int, val heroes: Int, val heroLevel: Double, val pieces: Int,
        val battles: Int, val defeats: Int, val lossesInRow: Int, val food: Long, val books: Long,
        val ore: Long, val invitations: Long, val population: Int, val chapter: Int, val forge: Int,
        val firstBoss: Double, val firstForge: Double, val fullTeam: Double,
        val pulls: Int, val legendary: Int, val mythic: Int, val shopFragments: Int)

    @Test fun integratedProgression() {
        val profiles = listOf(Profile("active_3h", 1, 1, 180), Profile("regular_30d", 30, 4, 5),
            Profile("relaxed_60d", 60, 1, 12), Profile("engaged_30d", 30, 1, 60))
        val output = File("build/reports/hamlet-progression").also { it.mkdirs() }
        val finals = ArrayList<Snapshot>()
        val allRows = ArrayList<Snapshot>()
        for (profile in profiles) for (seed in 0..2) {
            val rows = play(profile, seed)
            finals.add(rows.last()); allRows.addAll(rows)
            File(output, "${profile.name}_$seed.csv").writeText(csv(rows))
            println("${profile.name}/$seed: route=${rows.last().route}, heroes=${rows.last().heroes}, " +
                "gear=${rows.last().pieces}, levels=${rows.last().heroLevel}, defeats=${rows.last().defeats}/${rows.last().battles}")
        }
        File(output, "summary.csv").writeText(csv(finals)); File(output, "timeline.csv").writeText(csv(allRows))
        File(output, "method.txt").writeText("Trois graines par rythme. Horloge du village et minutes actives séparées.\n" +
            "Ressources issues seulement des actions publiques, récoltes, production et butin réel.\n" +
            "Collecte et gestion à chaque boucle, entraînement équilibré, recrutement, forge, puis tentative de frontière.\n" +
            "Après défaite : une récolte déjà explorée, puis amélioration et nouvelle tentative. Combat x2 + 4 s de menus.\n" +
            "Boutique : fragments personnels des héros de l'équipe ; une réserve de 20 runes et 12 cristaux protège la forge.\n" +
            "Les achats respectent niveau, pièces, terrain, population et chantiers. Reprise sérialisée à chaque visite.\n" +
            "Aucun paiement, aucune gemme dépensée. Ce banc décrit une stratégie, pas le ressenti d'un joueur humain.\n")
        assertTrue(finals.all { it.food >= 0 && it.books >= 0 && it.ore >= 0 && it.invitations >= 0 })
        assertTrue("le premier boss doit être accessible pendant une vraie session", finals.filter { it.profile == "active_3h" }.all { it.route >= 10 })
        assertTrue("les visites régulières doivent ouvrir la formation complète", finals.filter { it.profile == "regular_30d" }.all { it.route >= 24 })
        assertTrue("la progression doit encore offrir des aventures après un mois", finals.filter { it.profile == "engaged_30d" }.all { it.route > 24 })
    }

    private fun play(profile: Profile, seed: Int): List<Snapshot> {
        val start = 1_800_000_000_000L; var now = start; var activeSeconds = 0L
        val g = HamletGame({ now }, Random(seed)).apply { startNew(false) }; val a = g.adventure
        val rows = ArrayList<Snapshot>()
        var sessionEnd = start
        var battles = 0; var defeats = 0; var lossRun = 0; var longestLossRun = 0; var farm = false
        var firstBoss = -1.0; var firstForge = -1.0; var fullTeam = -1.0
        var shopFragments = 0
        val routeFailures = HashMap<Int, Int>()
        fun spend(seconds: Int) { now += seconds * 1000L; activeSeconds += seconds; g.tick() }
        fun buy(id: String): Boolean {
            val d = Hamlet.byId.getValue(id)
            if (!g.journey.unlocked(d) || g.coins < d.cost + 50 || d.gems > 0) return false
            val o = g.realmOrigin()
            for (y in o until o + g.realmSize()) for (x in o until o + g.realmSize()) if (g.canPlace(id, x, y, false)) {
                if (g.buy(id, x, y, false) == null) return false
                spend(5); return true
            }
            return false
        }
        fun developVillage() {
            g.tick(); val batch = g.canCollectAll()
            val collected = if (batch) g.collectAll() else g.objs.values.toList().count { g.collectReady(it) }
            spend(if (batch) 2 else 2 + collected)
            for (o in g.objs.values.toList()) {
                if (g.status(o) == Status.ORDER_NEW && g.acceptOrder(o)) spend(1)
                if (g.status(o) == Status.FEED && g.coins > 50 && g.feed(o)) spend(1)
                if (o.def.cat == Cat.FIELD && o.crop == null && g.buildLeft(o) == 0L && g.coins > 40) {
                    val unlocked = Hamlet.crops.filter { it.lvl <= g.level() && it.magic == o.def.magic && it.cost <= g.coins - 40 }
                    val short = unlocked.filter { it.time * 1000L < sessionEnd - now }
                    val crop = if (short.isNotEmpty()) short.maxByOrNull { (it.earn - it.cost).toDouble() / it.time }
                        else unlocked.filter { it.time * 1000L <= Hamlet.DAY_MS / profile.visits }.maxByOrNull { it.earn - it.cost }
                    if (crop != null && g.plant(o, crop.id)) { if (short.isEmpty()) g.tend(o); spend(1) }
                }
            }
            if (g.giftReady() && g.openGift()) spend(2)
            while (g.currentQuest()?.let { g.questDone(it) } == true) { g.claimQuest(); spend(2) }
            for (i in 0..2) { if (g.claimDaily(i)) spend(2); if (g.stewardship.claim(i)) spend(2) }
            if (g.stewardship.claimChest()) spend(2)
            if (g.journey.collectExpedition()) spend(2)
            while (g.journey.canClaim()) { g.journey.claim(); spend(2) }
            val chapterDef = HamletJourney.chapters.getOrNull(g.journey.chapter)
            val studyGoal = chapterDef?.goals?.firstOrNull { it.key == "research" }?.target ?: 13
            if (g.journey.research.sum() < studyGoal) g.journey.research.indices.sortedBy { g.journey.research[it] }
                .firstOrNull { g.journey.canResearch(it) && g.coins > g.journey.researchCoins(it) + 200 }?.let { g.journey.study(it); spend(3) }
            if (g.journey.expedition < 0) {
                val needed = chapterDef?.goals?.firstOrNull { it.key.startsWith("region_") && g.journey.progress(it) < it.target }?.key?.removePrefix("region_")?.toInt()
                val region = needed?.takeIf { g.journey.canExplore(it) } ?: HamletJourney.regions.indices.lastOrNull { g.journey.canExplore(it) }
                if (region != null) { g.journey.startExpedition(region); spend(2) }
            }
            val target = g.objs.values.firstOrNull { g.buildLeft(it) > 0 }
            if (target != null && g.applyWork(target)) spend(2)
            for (id in listOf("adventureInn", "adventureForge", "adventureAcademy", "adventureStore", "adventureYard", "adventureGate")) {
                if (g.objs.values.none { it.t == id } && buy(id)) return
            }
            val chapter = HamletJourney.chapters.getOrNull(g.journey.chapter)
            chapter?.goals?.firstOrNull { it.key.startsWith("built_") && g.journey.progress(it) < it.target }?.let {
                if (buy(it.key.removePrefix("built_"))) return
            }
            val questBuilding = g.currentQuest()?.takeIf { it.key.startsWith("built_") && !g.questDone(it) }?.key?.removePrefix("built_")
            if (questBuilding != null && buy(questBuilding)) return
            if (g.objs.values.count { it.t == "field" } < 4 && buy("field")) return
            if (g.objs.values.count { it.t == "cottage" } < 5 && buy("cottage")) return
            for (id in listOf("henyard", "bakery", "treeOrange", "treeApple", "beehive", "duckpond", "sheepmeadow", "goathill", "dairyfarm", "florist", "farmmarket", "jamkitchen")) {
                if (g.objs.values.none { it.t == id } && buy(id)) return
            }
            val facility = g.objs.values.firstOrNull { it.t in HamletAdventure.FACILITIES && g.canUpgrade(it) && g.coins > g.upgradeCost(it) + 200 }
            if (facility != null && g.upgrade(facility)) { spend(3); return }
            val next = g.nextHall()
            if (next != null && g.level() >= next.lvl && g.coins > next.cost + 300 && g.upgradeHall()) { spend(3); return }
            val populationGoal = chapterDef?.goals?.firstOrNull { it.key == "population" }?.target ?: 0
            if (g.population() < populationGoal) {
                val home = Hamlet.defs.filter { it.cat == Cat.HOME && it.gems == 0 && g.journey.unlocked(it) }.maxByOrNull { it.pop.toDouble() / it.cost.coerceAtLeast(1) }
                if (home != null && buy(home.id)) return
            }
            if (chapterDef?.goals?.any { it.key == "upgrades" && g.journey.progress(it) < it.target } == true) {
                g.objs.values.filter { g.canUpgrade(it) && g.coins > g.upgradeCost(it) + 200 }.minByOrNull { g.upgradeCost(it) }?.let { g.upgrade(it); spend(3); return }
            }
            if (g.coins > 3000 && g.canExpand() && g.expandLand()) spend(3)
        }
        fun developHeroes() {
            if (a[AdventureResource.TICKETS] > 0) { a.summon(minOf(10L, a[AdventureResource.TICKETS]).toInt()); spend(3) }
            // Traiter les héros obtenus comme de vrais choix de composition, pas les laisser au niveau 1.
            a.autoFormation()
            val team = a.teams[0].take(a.slotCount()).filter { it >= 0 }
            repeat(6) {
                val lowest = team.minByOrNull { a.effectiveLevel(it) } ?: return@repeat
                if (a.levelUp(lowest)) spend(2)
            }
            for (hero in team) {
                if (a.promote(hero)) spend(2)
                val skill = a.heroes[hero].skills.indices.minBy { a.heroes[hero].skills[it] }
                if (a.studySkill(hero, skill)) spend(2)
                val slot = a.heroes[hero].gear.indices.minBy { a.heroes[hero].gear[it] }
                val wanted = g.armory.recommendedSet(hero) ?: GearSet.HUNT
                val empty = (0..3).firstOrNull { g.armory.equipped(hero, it) == null }
                if (empty != null) {
                    val available = g.armory.inventory.firstOrNull { it.slot == empty && it.set == wanted && g.armory.owner(it.id) == null }
                    val piece = available ?: g.armory.craft(empty, wanted, EquipmentRarity.RARE)
                    if (piece != null && g.armory.equip(hero, piece.id)) spend(3)
                } else {
                    g.armory.equipped(hero, slot)?.let {
                        if (g.armory.upgradeQuality(it.id)) spend(3)
                        else if (g.armory.enhance(it.id)) spend(2)
                    }
                    if (a.forge(hero, slot)) spend(2)
                }
            }
            // A fragment purchase competes with equipment materials; keep a forge reserve.
            for (hero in team.sortedBy { a.heroes[it].stars }) {
                if (a.heroes[hero].stars >= HamletHeroes.STAR_CAP) continue
                val price = g.shardShop.cost(hero) ?: continue
                if (a[AdventureResource.RUNES] < price.runes + 20 || a[AdventureResource.CRYSTAL] < price.crystal + 12) continue
                if (g.shardShop.buy(hero, expectedCost = price)) { shopFragments++; spend(2); break }
            }
            for (i in a.pets.indices) if (a.pets[i] > 0) a.selectSupport(true, i)
            for (i in a.relics.indices) if (a.relics[i] > 0) a.selectSupport(false, i)
            for (post in g.stewardship.posts.indices) if (g.stewardship.posts[post] < 0) {
                a.heroes.filter { it.owned && it.id !in g.stewardship.posts }.maxByOrNull { g.stewardship.heroBonus(post, it.id) }?.let { g.stewardship.assign(post, it.id); spend(2) }
            }
            if (a.workers.sum() < a.workerCapacity() && a.assignWorker(0, 1)) spend(2)
            if (a.facility("adventureAcademy") > 0 && a.research(a.tech.indices.minBy { a.tech[it] })) spend(2)
            if (a.facility("adventureYard") > 0) for (faction in 0..2) {
                if (a.troops[faction] < a.marchCapacity() && a.trainTroops(faction, minOf(10, a.marchCapacity() - a.troops[faction]))) spend(2)
                if (a.improveTroops(faction)) spend(2)
            }
        }
        fun snapshot(): Snapshot = Snapshot(profile.name, seed, (now - start).toDouble() / Hamlet.DAY_MS,
            activeSeconds / 60.0, a.frontier, g.level(), a.heroes.count { it.owned },
            a.teams[0].take(a.slotCount()).filter { it >= 0 }.map { a.effectiveLevel(it) }.average(),
            g.armory.inventory.size, battles, defeats, longestLossRun, a[AdventureResource.FOOD], a[AdventureResource.BOOKS],
            a[AdventureResource.ORE], a[AdventureResource.TICKETS], g.population(), g.journey.chapter, a.facilityRank("adventureForge"), firstBoss, firstForge, fullTeam,
            a.recruitmentPulls, a.heroes.count { it.owned && it.def.rarity == HeroRarity.LEGENDARY }, a.heroes.count { it.owned && it.def.rarity == HeroRarity.MYTHIC }, shopFragments)
        for (visit in 0 until profile.days * profile.visits) {
            now = maxOf(now, start + visit * (Hamlet.DAY_MS / profile.visits))
            assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
            val visitEnd = now + profile.minutes * 60_000L
            sessionEnd = visitEnd
            while (now < visitEnd) {
                developVillage(); developHeroes()
                if (now >= visitEnd) break
                val stage = if (farm) (0 until a.frontier).lastOrNull { a.node(it) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE) } ?: a.frontier else a.frontier
                if (a.node(stage) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) {
                    assertTrue(a.gather(stage)); spend(16); farm = false
                } else {
                    val baseTactic = when (BattleField.fromRegion(a.region(stage))) {
                        BattleField.MIST, BattleField.MARSH -> BattleTactic.DEFENSIVE
                        BattleField.QUARRY, BattleField.FROST -> BattleTactic.CONTROL
                        BattleField.EMBER -> BattleTactic.ASSAULT
                        else -> BattleTactic.BALANCED
                    }
                    val failures = routeFailures[stage] ?: 0
                    val chosen = if (failures == 0) baseTactic else BattleTactic.entries[(baseTactic.ordinal + failures) % BattleTactic.entries.size]
                    if (a.tactics[0] != chosen) { a.selectTactic(chosen); spend(2) }
                    val battle = a.beginBattle(BattleMode.CAMPAIGN, stage)!!; battle.advance(100f)
                    assertTrue(a.settle(battle)); battles++; spend(ceil(battle.time / 2).toInt() + 4)
                    if (battle.outcome == BattleOutcome.DEFEAT) {
                        defeats++; lossRun++; routeFailures[stage] = failures + 1; longestLossRun = maxOf(longestLossRun, lossRun); farm = true
                    } else lossRun = 0
                }
                if (firstBoss < 0 && a.frontier >= 10) firstBoss = activeSeconds / 60.0
                if (firstForge < 0 && a.facility("adventureForge") > 0) firstForge = activeSeconds / 60.0
                if (fullTeam < 0 && a.slotCount() == 6) fullTeam = activeSeconds / 60.0
                assertTrue(a.bag.all { it >= 0 }); assertTrue(g.coins >= 0)
            }
            if (visit % profile.visits == 0 || visit == profile.days * profile.visits - 1) rows.add(snapshot())
        }
        return rows
    }
    private fun csv(rows: List<Snapshot>) = "profile,seed,elapsed_days,active_minutes,route,village_level,heroes,hero_level,gear_pieces,battles,defeats,longest_loss_run,food,books,ore,invitations,population,chapter,forge_rank,first_boss_active_minutes,first_forge_active_minutes,full_team_active_minutes,pulls,legendary,mythic,shop_fragments\n" + rows.joinToString("\n") {
        listOf(it.profile, it.seed, it.day, it.active, it.route, it.level, it.heroes, it.heroLevel, it.pieces, it.battles, it.defeats, it.lossesInRow,
            it.food, it.books, it.ore, it.invitations, it.population, it.chapter, it.forge, it.firstBoss, it.firstForge, it.fullTeam, it.pulls, it.legendary, it.mythic, it.shopFragments).joinToString(",")
    }
}
