package com.Atom2Universe.app.games.caves.world

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinEconomyTest {
    private val species = CoinEconomy.order.size

    @Test fun everySeedCostsThreeToFiveMobsOfItsUnlockStage() {
        for (position in 0 until species) {
            val stage = CoinEconomy.unlockStage(position)
            val mobs = CoinEconomy.price(position) / CoinEconomy.lootMean(stage)
            assertTrue("position $position stage $stage: $mobs mobs", mobs in 3.0..5.0)
        }
    }

    @Test fun pricesFollowTheDesignedCurve() {
        assertEquals(listOf(10, 10, 10, 15, 22, 34), (0..5).map(CoinEconomy::price))
        assertEquals(0, CoinEconomy.unlockStage(2))
        assertTrue((1 until species).all { CoinEconomy.unlockStage(it) >= CoinEconomy.unlockStage(it - 1) })
    }

    @Test fun noIntegerOverflowUpToTheLastStage() {
        val rng = Random(1)
        for (stage in 0..MineralProgression.LAST_STAGE) for (boss in listOf(false, true)) {
            val coins = CoinEconomy.loot(stage, boss, rng)
            assertTrue("stage $stage boss $boss: $coins", coins in 1..Int.MAX_VALUE)
            assertTrue(CoinEconomy.lootMax(stage) * CoinEconomy.BOSS_MULTIPLIER < Int.MAX_VALUE)
            assertTrue(CoinEconomy.chestCoins(stage, rng) in 1..Int.MAX_VALUE)
        }
        for (position in 0 until species) assertTrue(CoinEconomy.price(position) in 1 until Int.MAX_VALUE)
        // A full bag of top-stage boss loot, thousands of kills over, still fits a Long before it is clamped.
        assertTrue(CoinEconomy.lootMax(MineralProgression.LAST_STAGE) * CoinEconomy.BOSS_MULTIPLIER * 1_000_000L < Long.MAX_VALUE)
    }

    @Test fun firstStageMobDropsTwoOrThree() {
        val rng = Random(7)
        val drops = (0 until 500).map { CoinEconomy.loot(0, false, rng) }.toSet()
        assertEquals(setOf(2, 3), drops)
    }

    @Test fun everySpeciesIsSoldOnce() {
        assertEquals((0 until species).toList(), CoinEconomy.order.sorted())
    }

    @Test fun dishesHealByTheDepthOfTheirBestVegetable() {
        fun stage(id: Short) = CoinEconomy.dishStage(id)
        fun crop(c: com.Atom2Universe.app.games.farm.FarmCrop) = CoinEconomy.cropStage(
            com.Atom2Universe.app.games.caves.node.FarmItems.crops.indexOf(c))
        val K = com.Atom2Universe.app.games.caves.node.KitchenItems
        val F = com.Atom2Universe.app.games.caves.node.FrontierItems
        assertEquals(0, stage(F.BREAD))
        assertEquals(0, stage(K.BURGER))
        assertEquals(crop(com.Atom2Universe.app.games.farm.FarmCrop.TOMATO), stage(K.CHICKEN_BURGER))
        assertEquals(crop(com.Atom2Universe.app.games.farm.FarmCrop.POTATO), stage(K.BURGER_FRIES))
        assertTrue(F.healing(K.BURGER_FRIES) > F.healing(K.BURGER))
        assertTrue(F.healing(K.BURGER) > F.healing(F.BREAD))
    }
}
