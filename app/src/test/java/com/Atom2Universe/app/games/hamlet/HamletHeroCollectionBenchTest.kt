package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Banc des appels : combien d'invitations pour rencontrer les 32 héros qui sortent d'un tirage (12 communs, 12 rares,
 * 8 épiques), ce que deviennent les doublons, et ce que valent les garanties. Les légendaires n'en font pas partie.
 * Exclu des tests ordinaires : ./gradlew testDebugUnitTest -PbancsMesure --tests "*HamletHeroCollectionBenchTest*"
 */
class HamletHeroCollectionBenchTest {
    private val start = 1_800_000_000_000L

    private fun fresh(seed: Int): HamletAdventure {
        val g = HamletGame({ start }, Random(seed)).apply { startNew(false) }
        return g.adventure
    }

    private fun drawsUntil(seed: Int, goal: (HamletAdventure) -> Boolean): Int {
        val a = fresh(seed)
        var draws = 0
        while (!goal(a) && draws < 5_000) { a.add(AdventureResource.TICKETS, 1); a.summon(); draws++ }
        return draws
    }

    @Test fun drawsToMeetEveryone() {
        val seeds = 60
        val all = (0 until seeds).map { seed -> drawsUntil(seed) { a -> a.heroes.count { it.owned && it.def.rarity.ordinal <= HeroRarity.EPIC.ordinal } >= 32 } }.sorted()
        val epics = (0 until seeds).map { seed -> drawsUntil(seed) { a -> a.heroes.filter { it.def.rarity == HeroRarity.EPIC }.all { it.owned } } }.sorted()
        println("32 héros : médiane ${all[seeds / 2]} appels, 10 % rapides ${all[seeds / 10]}, 10 % lents ${all[seeds * 9 / 10]}, pire ${all.last()}")
        println("8 épiques : médiane ${epics[seeds / 2]} appels, pire ${epics.last()}")
        for (perDay in listOf(4, 6, 8)) println("à $perDay appels par jour : médiane ${all[seeds / 2] / perDay} jours pour les 32")
    }

    @Test fun rarityShareOverTenThousandDraws() {
        val a = fresh(7)
        val counts = IntArray(HeroRarity.entries.size)
        repeat(10_000) { a.add(AdventureResource.TICKETS, 1); for (r in a.summon()) counts[a.heroes[r.hero].def.rarity.ordinal]++ }
        println("communs ${counts[0]}, rares ${counts[1]}, épiques ${counts[2]}, légendaires ${counts[3]}, mythiques ${counts[4]}")
        assertEquals(0, counts[HeroRarity.LEGENDARY.ordinal]); assertEquals(0, counts[HeroRarity.MYTHIC.ordinal])
        assertTrue(counts[HeroRarity.EPIC.ordinal] in 300..700)
    }

    @Test fun theEpicGuaranteeNeverWaitsMoreThanItsLimit() {
        for (seed in 0 until 40) {
            val a = fresh(seed)
            a.add(AdventureResource.TICKETS, 3); a.summon(3)
            var since = 0
            repeat(2_000) {
                a.add(AdventureResource.TICKETS, 1)
                val r = a.summon().single()
                if (a.heroes[r.hero].def.rarity == HeroRarity.EPIC) since = 0 else since++
                assertTrue("seed $seed : $since appels sans épique", since < HamletAdventure.PITY_EPIC)
            }
        }
    }
}
