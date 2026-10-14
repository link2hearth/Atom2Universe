package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Isolated collection/economy bench requested by the player, not a village-playability proof.
 * Twelve seeds per rhythm over ninety days. Mature crop actions are explicit input assumptions;
 * invitations, production, journal claims, expeditions, draws and personal fragments use real APIs.
 * No elite hero, invitation balance or fragment balance is injected into a run. */
class HamletHeroCollectionBenchTest {
    private data class Profile(val name: String, val visits: Int, val minutes: Int,
        val harvestsPerVisit: Int, val sweepsPerVisit: Int, val innRank: Int)
    private data class Row(val profile: String, val seed: Int, val day: Int, val pulls: Int,
        val common: Int, val rare: Int, val epic: Int, val legendary: Int, val mythic: Int,
        val personalFragments: Long, val starterStars: Double, val hero0Stars: Int,
        val hero0Fragments: Long, val hero0StarGain: Double, val invitations: Long,
        val journalTickets: Long, val innTickets: Long, val expeditionTickets: Long, val villageTickets: Long)

    @Test fun personalFragmentsAndSlowCollection() {
        val profiles = listOf(Profile("casual_1x5m", 1, 5, 6, 1, 1),
            Profile("regular_4x5m", 4, 5, 6, 2, 2), Profile("active_1x60m", 1, 60, 120, 12, 3))
        val rows = ArrayList<Row>()
        for (profile in profiles) for (seed in 0 until 12) rows.addAll(play(profile, seed))
        val output = File("build/reports/hamlet-hero-collection").also { it.mkdirs() }
        File(output, "collection.csv").writeText(csv(rows))
        File(output, "method.txt").writeText(
            "Banc de collection isolé : 12 graines pour chacun des trois rythmes, jours 0,1,7,14,30,60,90.\n" +
            "Hypothèse explicite : 6/6/120 récoltes mûres par visite, sans simulation du calendrier des plantes.\n" +
            "Journal : objectifs réels, au maximum six invitations/jour ; aucun crédit artificiel d'invitations.\n" +
            "Auberge achetée avec les récompenses réelles à partir du jour 3, rang 2 au jour 7 et rang 3 au jour 14 si abordables.\n" +
            "Expéditions : région 0 répétée, durée réelle, une invitation à la découverte puis tous les cinq voyages, maximum deux/jour.\n" +
            "Introduction et première récolte jouées avec le moteur réel. Ensuite, 1/2/12 raids rapides par visite sur route 0 maîtrisée.\n" +
            "Les fragments de combat ciblent Lina ; un doublon donne strictement un fragment de son personnage précis.\n" +
            "Toutes les invitations sont tirées en lots ; promotions dès que leurs coûts personnels sont remplis.\n" +
            "Sauvegarde/restauration à chaque journée. Ni ciblage garanti, ni fragments de boss découvert, ni premiers secours supplémentaires.\n" +
            "Ce banc isole le rythme de collection et l'utilité d'un héros commun conservé. Il ne mesure pas le ressenti ni la progression en campagne.\n")
        val initial = rows.filter { it.day == 0 }
        assertTrue(initial.all { it.common == 4 && it.rare == 0 && it.epic == 0 && it.legendary == 0 && it.mythic == 0 })
        val month = rows.filter { it.day == 30 }
        assertTrue("la collection doit encore rester incomplète après un mois", month.all { it.common + it.rare + it.epic + it.legendary + it.mythic < 36 })
        val veteran = rows.filter { it.day == 90 }
        assertTrue("les fragments de héros d'origine restent utiles à long terme", veteran.all { it.hero0Stars > 0 && it.hero0StarGain >= 5.0 })
        for (profile in profiles) for (day in listOf(0, 1, 7, 14, 30, 60, 90)) {
            val group = rows.filter { it.profile == profile.name && it.day == day }
            println("${profile.name}/day$day: pulls=${group.map { it.pulls }.average()}, " +
                "common=${group.map { it.common }.average()}, rare=${group.map { it.rare }.average()}, " +
                "epic=${group.map { it.epic }.average()}, legendary=${group.map { it.legendary }.average()}, " +
                "mythic=${group.map { it.mythic }.average()}, starterStars=${group.map { it.starterStars }.average()}, " +
                "LinaStars=${group.map { it.hero0Stars }.average()}")
        }
    }

    private fun play(profile: Profile, seed: Int): List<Row> {
        val start = 1_800_000_000_000L; var now = start
        val g = HamletGame({ now }, Random(seed)).apply { startNew(false) }; val a = g.adventure
        var journalTickets = 0L; var innTickets = 0L; var expeditionTickets = 0L; var villageTickets = 0L
        val rows = ArrayList<Row>()
        fun snapshot(day: Int) = Row(profile.name, seed, day, a.recruitmentPulls,
            a.heroes.count { it.owned && it.def.rarity == HeroRarity.COMMON },
            a.heroes.count { it.owned && it.def.rarity == HeroRarity.RARE },
            a.heroes.count { it.owned && it.def.rarity == HeroRarity.EPIC },
            a.heroes.count { it.owned && it.def.rarity == HeroRarity.LEGENDARY },
            a.heroes.count { it.owned && it.def.rarity == HeroRarity.MYTHIC },
            a.heroes.sumOf { it.shards }, HamletAdventure.STARTERS.map { a.heroes[it].stars }.average(),
            a.heroes[0].stars, a.heroes[0].shards, a.heroes[0].stars * HamletHeroes.STAR_BONUS * 100,
            a[AdventureResource.TICKETS], journalTickets, innTickets, expeditionTickets, villageTickets)
        fun drawAll() {
            while (a[AdventureResource.TICKETS] > 0) assertFalse(a.summon(minOf(10L, a[AdventureResource.TICKETS]).toInt()).isEmpty())
            for (h in a.heroes) while (a.canPromote(h.id)) assertTrue(a.promote(h.id))
        }
        fun advanceVillage() {
            val before = a[AdventureResource.TICKETS]; a.advanceProduction()
            innTickets += a[AdventureResource.TICKETS] - before
        }
        fun claimJournal() {
            for (index in 0..2) {
                val before = a[AdventureResource.TICKETS]
                g.stewardship.claim(index); journalTickets += a[AdventureResource.TICKETS] - before
            }
            val before = a[AdventureResource.TICKETS]
            g.stewardship.claimChest(); journalTickets += a[AdventureResource.TICKETS] - before
        }
        fun expedition() {
            val before = a[AdventureResource.TICKETS]
            g.journey.collectExpedition(); expeditionTickets += a[AdventureResource.TICKETS] - before
            if (g.journey.canExplore(0)) assertTrue(g.journey.startExpedition(0))
        }
        fun developInn(day: Int) {
            if (day < 3) return
            var inn = g.objs.values.firstOrNull { it.t == "adventureInn" }
            if (inn == null) {
                val origin = g.realmOrigin()
                loop@ for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) {
                    if (!g.canPlace("adventureInn", x, y, false)) continue
                    inn = g.buy("adventureInn", x, y, false)
                    if (inn != null) break@loop
                }
            }
            val target = minOf(profile.innRank, if (day >= 14) 3 else if (day >= 7) 2 else 1)
            val building = inn ?: return
            while (building.tier < target && g.canUpgrade(building)) assertTrue(g.upgrade(building))
        }
        rows.add(snapshot(0))
        val intro = a.beginBattle(BattleMode.CAMPAIGN, 0, seed)!!; intro.advance(100f)
        assertEquals(BattleOutcome.VICTORY, intro.outcome); assertTrue(a.settle(intro))
        assertTrue(a.gather(1)); assertTrue(a.selectShardFocus(0))
        for (day in 1..90) {
            for (visit in 0 until profile.visits) {
                now = start + (day - 1) * Hamlet.DAY_MS + visit * (Hamlet.DAY_MS / profile.visits)
                advanceVillage(); developInn(day); expedition()
                val beforeHarvest = a[AdventureResource.TICKETS]
                repeat(profile.harvestsPerVisit) { g.stat("harvested") }
                villageTickets += a[AdventureResource.TICKETS] - beforeHarvest
                repeat(profile.sweepsPerVisit) { assertTrue(a.sweep(0)) }
                repeat(maxOf(0, 3 - profile.sweepsPerVisit)) { assertTrue(a.gather(1)) }
                claimJournal(); drawAll(); claimJournal(); drawAll()
                repeat(profile.minutes) { now += 60_000; advanceVillage(); expedition() }
                drawAll()
                assertTrue(a.bag.all { it >= 0 })
            }
            assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
            if (day in listOf(1, 7, 14, 30, 60, 90)) rows.add(snapshot(day))
        }
        return rows
    }
    private fun csv(rows: List<Row>): String = "profile,seed,day,pulls,common,rare,epic,legendary,mythic,personal_fragments,starter_stars_mean,hero0_stars,hero0_fragments,hero0_star_gain_percent,invitations,journal_tickets,inn_tickets,expedition_tickets,village_tickets\n" + rows.joinToString("\n") { r ->
        listOf(r.profile, r.seed, r.day, r.pulls, r.common, r.rare, r.epic, r.legendary, r.mythic,
            r.personalFragments, r.starterStars, r.hero0Stars, r.hero0Fragments, r.hero0StarGain,
            r.invitations, r.journalTickets, r.innTickets, r.expeditionTickets, r.villageTickets).joinToString(",")
    }
}
