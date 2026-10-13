package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Banc demandé : joue les vraies règles avec une horloge accélérée, sans Android ni argent injecté.
 * Les politiques sont explicites ; ces mesures décrivent des joueurs simulés, pas tous les joueurs.
 * ./gradlew testDebugUnitTest -PbancsMesure --tests "*HamletEconomyBenchTest*"
 */
class HamletEconomyBenchTest {
    private val out = File("build/reports/hamlet-economy").also { it.mkdirs() }
    private data class Profile(val name: String, val days: Int, val interval: Long, val session: Long, val workshop: Boolean = false)
    private data class Result(val profile: String, val seed: Int, val level: Int, val coins: Long, val pop: Int,
        val quests: Int, val harvests: Long, val products: Long, val activeMinutes: Long, val first2: Long, val first5: Long, val firstHall: Long, val finalHall: Int, val fullHallMinutes: Long,
        val chapters: Int, val studies: Int, val expeditions: Int, val workUsed: Long)

    @Test fun progression() {
        val profiles = listOf(
            Profile("active_2h", 1, Hamlet.DAY_MS, 2 * 3_600_000),
            Profile("four_visits_14d", 14, 6 * 3_600_000, 8 * 60_000),
            Profile("one_visit_14d", 14, Hamlet.DAY_MS, 10 * 60_000),
            Profile("long_village_56d", 56, 6 * 3_600_000, 8 * 60_000),
            Profile("journey_96d", 96, 6 * 3_600_000, 8 * 60_000),
            Profile("journey_workshop_96d", 96, 6 * 3_600_000, 8 * 60_000, workshop = true),
        )
        val results = profiles.flatMap { p -> (0 until 8).map { play(p, it) } }
        File(out, "summary.csv").writeText("profile,seed,level,coins,population,quests,harvests,products,active_minutes,level2_minutes,level5_minutes,hall2_minutes,hall,full_hall_minutes,chapters,research_ranks,expeditions,free_work_seconds\n" + results.joinToString("\n") {
            listOf(it.profile, it.seed, it.level, it.coins, it.pop, it.quests, it.harvests, it.products, it.activeMinutes, it.first2, it.first5, it.firstHall, it.finalHall, it.fullHallMinutes, it.chapters, it.studies, it.expeditions, it.workUsed).joinToString(",")
        })
        for (r in results) {
            assertTrue("${r.profile}/${r.seed}: aucune récolte", r.harvests > 0)
            assertTrue("${r.profile}/${r.seed}: niveau 2 inaccessible", r.level >= 2)
            if (r.profile == "long_village_56d") assertTrue("halle historique accessible sans argent injecté", r.finalHall >= 5)
            if (r.profile.startsWith("journey_")) assertEquals("les douze chapitres accessibles sans paiement", 12, r.chapters)
        }
        val report = results.groupBy { it.profile }.entries.joinToString("\n") { (p, runs) ->
            "$p: level ${runs.minOf { it.level }}..${runs.maxOf { it.level }}, coins ${runs.minOf { it.coins }}..${runs.maxOf { it.coins }}, " +
                "population ${runs.minOf { it.pop }}..${runs.maxOf { it.pop }}, quests ${runs.minOf { it.quests }}..${runs.maxOf { it.quests }}, hall ${runs.minOf { it.finalHall }}..${runs.maxOf { it.finalHall }}, chapters ${runs.minOf { it.chapters }}..${runs.maxOf { it.chapters }}"
        }
        File(out, "results.txt").writeText("8 graines par profil. Aucun argent ajouté, aucune gemme dépensée.\n" +
            "24 h désigne le temps du village ; les minutes actives sont mesurées séparément.\n" +
            "Le joueur choisit une culture adaptée à son prochain retour, nourrit, collecte, réclame les quêtes et construit avec une réserve de 40 pièces.\n" +
            "Tous les profils utilisent le travail gratuit gagné par le farm. Le profil workshop ajoute une tournée de 12 gestes (budget 30 secondes) par visite.\n" +
            "Chaque trajet est choisi pour le chapitre courant. La recherche et la formation utilisent uniquement les pièces gagnées et les provisions rapportées.\n" +
            "Une partie est ouverte : niveaux 2/5 et halle 2 sont des jalons, pas une fin de partie.\n\n" + report)
        println(report)
    }

    private fun play(p: Profile, seed: Int): Result {
        val start = 1_800_000_000_000L
        var t = start
        val r = Random(seed)
        val g = HamletGame({ t }, r).apply { startNew(false) }
        val snapshots = StringBuilder("elapsed_minutes,active_minutes,level,coins,population,quest,fields,pens,hall,harvests,products,chapter,supplies,research,expeditions,free_work_seconds\n")
        val milestones = LongArray(HamletJourney.chapters.size) { -1 }
        var active = 0L; var l2 = -1L; var l5 = -1L; var hall = -1L; var fullHall = -1L; var nextSnapshot = start
        val end = if (p.name == "active_2h") start + p.session else start + p.days * Hamlet.DAY_MS
        while (t < end) {
            val phase = (t - start) % p.interval
            if (phase >= p.session) {
                t += p.interval - phase
                // Une reprise passe par la même sérialisation que l'application.
                assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
                continue
            }
            g.tick(); g.collectAll()
            for (o in g.objs.values.toList()) {
                if (g.status(o) == Status.ORDER_NEW) g.acceptOrder(o)
                if (g.status(o) == Status.FEED && g.coins > 40) g.feed(o)
                if (o.def.cat == Cat.FIELD && o.crop == null) {
                    val remaining = p.session - phase
                    val untilReturn = p.interval - phase
                    val unlocked = Hamlet.crops.filter { it.lvl <= g.level() && it.magic == o.def.magic && it.cost <= g.coins - 30 }
                    val short = unlocked.filter { it.time * 1000L <= remaining }
                    val crop = if (short.isNotEmpty()) short.maxByOrNull { (it.earn - it.cost).toDouble() / it.time }
                    else unlocked.filter { it.time * 1000L <= untilReturn }.maxByOrNull { it.earn - it.cost }
                    if (crop != null) {
                        g.plant(o, crop.id)
                        if (short.isEmpty()) g.tend(o)
                    }
                }
            }
            if (g.giftReady()) g.openGift()
            // La vue propose un pourboire environ toutes les 70 s de présence.
            if (r.nextDouble() < 30.0 / 70) g.tip()
            while (g.currentQuest()?.let { g.questDone(it) } == true) g.claimQuest()
            for (i in 0..2) g.claimDaily(i)
            developJourney(g)
            // Une seule tournée d'atelier de 12 gestes par visite (budget de 30 secondes).
            if (p.workshop && phase == 0L) { g.beginWork(); repeat(12) { g.answerWork(g.workTool) } }
            if (g.journey.expedition >= 0 && g.journey.expeditionLeft() > 0) g.workOnExpedition()
            else g.objs.values.firstOrNull { g.buildLeft(it) > 0 }?.let { g.applyWork(it) }
            build(g)
            for (i in 0 until g.journey.chapter) if (milestones[i] < 0) milestones[i] = (t - start) / 60_000
            assertTrue("solde négatif", g.coins >= 0)
            assertTrue(g.gems >= 0)
            if (g.level() >= 2 && l2 < 0) l2 = (t - start) / 60_000
            if (g.level() >= 5 && l5 < 0) l5 = (t - start) / 60_000
            if (g.hall >= 2 && hall < 0) hall = (t - start) / 60_000
            if (g.hall == Hamlet.halls.size && fullHall < 0) fullHall = (t - start) / 60_000
            if (t >= nextSnapshot) {
                snapshots.append(listOf((t - start) / 60_000, active / 60_000, g.level(), g.coins, g.population(), g.quest,
                    g.objs.values.count { it.def.cat == Cat.FIELD }, g.objs.values.count { it.def.cat == Cat.RANCH }, g.hall, g.stats["harvested"] ?: 0, g.stats["products"] ?: 0,
                    g.journey.chapter, g.journey.supplies, g.journey.research.sum(), g.journey.explored.sum(), g.stats["work_used"] ?: 0).joinToString(",")).append('\n')
                nextSnapshot = t + 3_600_000
            }
            active += 30_000; t += 30_000
        }
        // Occupation et placement : aucune construction du bot n'a contourné les règles.
        for (o in g.objs.values) for (y in o.y until o.y + o.d) for (x in o.x until o.x + o.w) assertEquals(o.id, g.occ[g.idx(x, y)])
        File(out, "${p.name}_$seed.csv").writeText(snapshots.toString())
        File(out, "${p.name}_${seed}_chapters.csv").writeText("chapter,elapsed_minutes\n" + milestones.mapIndexed { i, n -> "${i + 1},$n" }.joinToString("\n"))
        return Result(p.name, seed, g.level(), g.coins, g.population(), g.quest, g.stats["harvested"] ?: 0, g.stats["products"] ?: 0, active / 60_000, l2, l5, hall, g.hall, fullHall,
            g.journey.chapter, g.journey.research.sum(), g.journey.explored.sum(), g.stats["work_used"] ?: 0)
    }

    private fun developJourney(g: HamletGame) {
        val j = g.journey
        j.collectExpedition()
        while (j.canClaim()) j.claim()
        // Recherches en priorité pour le prochain objectif, puis force requise pour la région.
        val goal = HamletJourney.chapters.getOrNull(j.chapter)?.goals?.firstOrNull { it.key == "research" }?.target ?: 13
        if (j.research.sum() < goal) j.research.indices.sortedBy { j.research[it] }.firstOrNull { j.canResearch(it) && g.coins > j.researchCoins(it) + 100 }?.let { j.study(it) }
        val available = HamletJourney.regions.indices.lastOrNull { j.chapter >= HamletJourney.regions[it].chapter } ?: 0
        if (j.power() < HamletJourney.regions[available].power) {
            j.companions.indices.filter { j.companions[it] > 0 }.sortedByDescending { j.companions[it] }.take(3).forEachIndexed { s, hero -> j.assign(hero, s) }
            j.companions.indices.sortedBy { j.companions[it] }.firstOrNull { j.canTrain(it) && g.coins > j.trainCoins(it) + 100 }?.let { j.train(it) }
        }
        if (j.expedition < 0) {
            val needed = HamletJourney.chapters.getOrNull(j.chapter)?.goals?.firstOrNull { it.key.startsWith("region_") && j.progress(it) < it.target }
                ?.key?.removePrefix("region_")?.toInt()
            val region = needed?.takeIf { j.canExplore(it) } ?: HamletJourney.regions.indices.lastOrNull { j.canExplore(it) }
            if (region != null) j.startExpedition(region)
        }
    }

    private fun build(g: HamletGame) {
        fun count(id: String) = g.objs.values.count { it.t == id }
        fun buy(id: String): Boolean {
            val d = Hamlet.byId.getValue(id)
            if (!g.journey.unlocked(d) || g.coins < d.cost + 40 || d.gems > 0) return false
            val origin = g.realmOrigin()
            for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) if (g.canPlace(id, x, y, false)) return g.buy(id, x, y, false) != null
            return false
        }
        val q = g.currentQuest()
        val chapter = HamletJourney.chapters.getOrNull(g.journey.chapter)
        if (chapter != null) for (goal in chapter.goals) {
            if (goal.key.startsWith("built_") && g.journey.progress(goal) < goal.target && buy(goal.key.removePrefix("built_"))) return
        }
        if (q != null && q.key.startsWith("built_")) {
            val id = q.key.removePrefix("built_")
            if (!g.questDone(q) && id in Hamlet.byId && buy(id)) return
        }
        if (count("field") < 8 && buy("field")) return
        if (count("cottage") < 5 && buy("cottage")) return
        // Après les premières sources de revenu, épargner pour la halle plutôt que tout réinvestir.
        if (count("bakery") > 0 && count("henyard") > 0 && g.hall == 1 && g.level() >= 5) {
            if (g.coins > g.nextHall()!!.cost + 100) g.upgradeHall()
            return
        }
        if (g.hall == 4 && g.level() >= 20) {
            if (g.coins > g.nextHall()!!.cost + 100) g.upgradeHall()
            return
        }
        for (id in listOf("bakery", "henyard", "treeOrange", "treeApple", "well", "florist", "beehive", "duckpond", "farmmarket", "sheepmeadow", "jamkitchen", "goathill", "dairyfarm")) if (count(id) == 0 && buy(id)) return
        if (q?.key == "roads" || q?.key == "water" || q?.key == "bridges") {
            val id = when (q.key) { "roads" -> "road"; "water" -> "river"; else -> "bridge" }
            val origin = g.realmOrigin()
            for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) if (g.objAt(x, y) == null && g.paint(id, x, y)) return
        }
        if (q?.key == "cat_decor" || q?.key == "cat_nature") {
            if (buy(if (q.key == "cat_decor") "bench" else "flowersRed")) return
        }
        val next = g.nextHall()
        if (next != null && g.level() >= next.lvl && g.journey.canUpgradeHall()) {
            if (g.coins > next.cost + 100) { g.upgradeHall(); return }
            if (chapter != null && g.hall < chapter.hall) return
        }
        if (q?.key == "expanded" && g.coins > g.expandCost() + 100 && g.expandLand()) return
        val candidates = Hamlet.defs.filter { it.cat in setOf(Cat.HOME, Cat.SHOP, Cat.RANCH) && g.journey.unlocked(it) && it.gems == 0 && !it.shore && count(it.id) == 0 }.sortedBy { it.cost }
        for (d in candidates) if (buy(d.id)) return
        val upgrade = g.objs.values.firstOrNull { g.canUpgrade(it) && g.coins > g.upgradeCost(it) + 100 }
        if (upgrade != null) g.upgrade(upgrade)
        else if (g.canExpand() && g.coins > g.expandCost() + 100) g.expandLand()
    }

    @Test fun catalogueReturnOnInvestment() {
        val csv = StringBuilder("id,category,unlock,cost,cycle_seconds,net_coins,cycles_to_repay,hours_to_repay\n")
        for (d in Hamlet.defs) {
            val p = HamletLife.produce[d.id]
            val period = p?.seconds ?: when (d.cat) { Cat.HOME -> d.period; Cat.SHOP -> d.job; else -> 0 }
            val profit = p?.let { it.coins - it.feed } ?: when (d.cat) { Cat.HOME -> d.tax; Cat.SHOP -> d.pay; else -> 0 }
            if (period == 0 || d.gems > 0) continue
            assertTrue("production non rentable ${d.id}", profit > 0)
            val cycles = d.cost.toDouble() / profit
            csv.append(listOf(d.id, d.cat, d.lvl, d.cost, period, profit, cycles, cycles * period / 3600).joinToString(",")).append('\n')
        }
        File(out, "buildings_roi.csv").writeText(csv.toString())
        File(out, "crops.csv").writeText("id,unlock,cost,seconds,net_coins,xp,coins_per_hour\n" + Hamlet.crops.joinToString("\n") {
            assertTrue(it.earn > it.cost)
            listOf(it.id, it.lvl, it.cost, it.time, it.earn - it.cost, it.xp, (it.earn - it.cost) * 3600.0 / it.time).joinToString(",")
        })
    }

    @Test fun effortPourAccelererUnLongChantier() {
        var time = 1_800_000_000_000L
        // Scénario préparé d'une partie avancée ; ce tableau mesure des gestes, pas l'économie initiale.
        val g = HamletGame({ time }, Random(4)).apply { startNew(false); coins = 1_000_000; xp = 10_000_000 }
        val start = g.realmOrigin()
        var obj: Obj? = null
        outer@ for (y in start until start + g.realmSize()) for (x in start until start + g.realmSize())
            if (g.canPlace("jousting", x, y, false)) { obj = g.buy("jousting", x, y, false); break@outer }
        val chantier = obj!!
        assertEquals(2 * Hamlet.HOUR * 1000L, g.buildLeft(chantier))
        val coins = g.coins; val gems = g.gems
        g.beginWork()
        var gestures = 0
        while (g.buildLeft(chantier) > 0) {
            repeat(12) { assertTrue(g.answerWork(g.workTool)); gestures++ }
            assertTrue(g.applyWork(chantier))
        }
        assertEquals(288, gestures)
        assertEquals(coins, g.coins); assertEquals(gems, g.gems)
        File(out, "free_work.csv").writeText("scenario,original_wait_seconds,gestures,coins_spent,gems_spent\nlate_building,7200,$gestures,0,0\n")
    }
}
