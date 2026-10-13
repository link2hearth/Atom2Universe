package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Mesure le vrai moteur : aucune victoire par puissance, aucun revenu ajouté artificiellement.
 * Le bot choisit, récolte, rejoue et entraîne ; les coûts de temps incluent les gestes de menu.
 * Banc demandé pour équilibrer Hamlet, exclu des tests ordinaires. */
class HamletAdventureBenchTest {
    data class Result(val seed: Int, val policy: String, val hours: Double, val stages: Int,
        val battles: Int, val losses: Int, val gathers: Int, val averageLevel: Double, val owned: Int,
        val firstBossMinutes: Double, val allHeroesMinutes: Double, val food: Long)

    private fun simulate(seed: Int, policy: String, budgetHours: Int): Result {
        val start = 1_800_000_000_000L; var time = start
        val g = HamletGame({ time }, Random(seed)).apply { startNew(false) }; val a = g.adventure
        var battles = 0; var losses = 0; var gathers = 0; var firstBoss = -1.0; var allHeroes = -1.0
        var farmNext = false
        fun clock(seconds: Double) { time += (seconds * 1000).toLong(); g.tick() }
        fun build(id: String) {
            val def = Hamlet.byId.getValue(id)
            if (g.objs.values.any { it.t == id } || g.level() < def.lvl || !g.canAfford(def)) return
            val origin = g.realmOrigin()
            for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) if (g.canPlace(id, x, y, false)) {
                if (g.buy(id, x, y, false) != null) clock(5.0)
                return
            }
        }
        while (time - start < budgetHours * 3_600_000L && a.frontier < 240) {
            if (policy == "four_visits" && (time - start) % 21_600_000L >= 8 * 60_000) {
                time = start + ((time - start) / 21_600_000L + 1) * 21_600_000L; a.advanceProduction()
                if (time - start >= budgetHours * 3_600_000L) break
            }
            if (policy != "basic") {
                listOf("adventureForge", "adventureAcademy", "adventureYard", "adventureStore").forEach(::build)
                // Toutes les améliorations restent payées avec le butin et la production réelle.
                repeat(20) {
                    val team = a.teams[a.selectedTeam].filter { it >= 0 }
                    val lowest = team.minByOrNull { a.heroes[it].level }!!
                    if (a.levelUp(lowest)) clock(2.0)
                }
                for (hero in a.teams[0].filter { it >= 0 }) {
                    if (a.promote(hero)) clock(2.0)
                    if (a.studySkill(hero, battles % 4)) clock(2.0)
                    if (a.forge(hero, battles % 4)) clock(2.0)
                }
                if (a.frontier >= 6 && a.heroes[2].owned) a.assign(2, 1)
                if (a.slotCount() >= 5 && a.heroes[4].owned) a.assign(4, 4)
                if (a.slotCount() >= 6 && a.heroes[5].owned) a.assign(5, 5)
                if (a.heroes[6].owned) a.assign(6, 0)
                if (a.heroes[8].owned) a.assign(8, 3)
                if (a.pets[0] > 0) a.selectSupport(true, 0)
                if (a.relics[0] > 0) a.selectSupport(false, 0)
            }
            val stage = if (farmNext) (0 until a.frontier).lastOrNull { a.node(it) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE) } ?: 1 else a.frontier
            if (a.node(stage) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) {
                assertTrue(a.gather(stage)); gathers++; clock(16.0); farmNext = false
            } else {
                val b = a.beginBattle(BattleMode.CAMPAIGN, stage)!!; b.advance(100f)
                assertTrue(a.settle(b)); battles++; clock(b.time.toDouble() / 2 + 5.0)
                if (b.outcome == BattleOutcome.DEFEAT) { losses++; farmNext = a.frontier > 1 }
            }
            if (firstBoss < 0 && a.frontier >= 10) firstBoss = (time - start) / 60_000.0
            if (allHeroes < 0 && a.heroes.all { it.owned }) allHeroes = (time - start) / 60_000.0
            assertTrue(a.bag.all { it >= 0 }); assertTrue(a.heroes.all { it.shards >= 0 })
        }
        return Result(seed, policy, (time - start) / 3_600_000.0, a.frontier, battles, losses, gathers,
            a.teams[0].filter { it >= 0 }.map { a.heroes[it].level }.average(), a.heroes.count { it.owned },
            firstBoss, allHeroes, a[AdventureResource.FOOD])
    }
    @Test fun campaignEconomy() {
        val results = ArrayList<Result>()
        for (seed in 0..5) {
            results.add(simulate(seed, "active", 6)); results.add(simulate(seed, "four_visits", 24 * 14))
            results.add(simulate(seed, "basic", 1))
        }
        val dir = File("build/reports/hamlet-adventure").also { it.mkdirs() }
        File(dir, "economy.csv").writeText("seed,policy,hours,stages,battles,losses,gathers,heroLevel,owned,firstBossMinutes,allHeroesMinutes,food\n" + results.joinToString("\n") { r ->
            listOf(r.seed, r.policy, r.hours, r.stages, r.battles, r.losses, r.gathers, r.averageLevel, r.owned, r.firstBossMinutes, r.allHeroesMinutes, r.food).joinToString(",")
        })
        for ((policy, rows) in results.groupBy { it.policy }) println("$policy: stages ${rows.minOf { it.stages }}..${rows.maxOf { it.stages }}, heroes ${rows.minOf { it.owned }}..${rows.maxOf { it.owned }}, boss ${rows.minOf { it.firstBossMinutes }}..${rows.maxOf { it.firstBossMinutes }} min")
        assertTrue(results.filter { it.policy == "active" }.all { it.stages >= 90 && it.owned == 12 })
        assertTrue(results.filter { it.policy == "four_visits" }.all { it.stages > 120 })
        assertTrue(results.filter { it.policy == "basic" }.all { it.stages < 40 })
    }
}
