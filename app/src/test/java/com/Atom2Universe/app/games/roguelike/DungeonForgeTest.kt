package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DungeonForgeTest {

    @Test fun priceIsFiveFloorsOfIncomeAndGrowsWithDepth() {
        for (floor in listOf(50, 100, 200, 300)) {
            val exact = DungeonForge.incomePerFloor(floor) * DungeonForge.FLOORS_OF_INCOME
            assertEquals("étage $floor", exact, DungeonForge.price(floor).toDouble(), exact * 0.06)
        }
        // Mesure des bots : ~630-880 or par étage vers l'étage 50, ~2 600 vers l'étage 100
        assertTrue(DungeonForge.price(50) in 3_000..4_500)
        assertTrue(DungeonForge.price(100) in 11_000..14_000)
        assertTrue((50..1000).zipWithNext().all { (a, b) -> DungeonForge.price(a) <= DungeonForge.price(b) })
    }

    @Test fun noForgeBeforeFloorFiftyAndAboutFivePercentAfter() {
        val rng = Random(3)
        assertTrue((1 until DungeonForge.MIN_FLOOR).none { f -> (0 until 200).any { DungeonForge.rollPresence(f, rng) } })
        val share = (0 until 20_000).count { DungeonForge.rollPresence(80, rng) } / 20_000f
        assertEquals(DungeonForge.CHANCE, share, 0.01f)
    }

    @Test fun reforgeKeepsTypeRarityAndWeightAtTheCurrentFloor() {
        val rng = Random(11)
        repeat(300) {
            val old = LootSystem.generateNormal(5, 0, rng)
            val forged = LootSystem.reforge(old, 120, 1, rng)
            assertEquals(old.base, forged.base)
            assertEquals(old.rarity, forged.rarity)
            assertEquals(old.weight, forged.weight)
            assertNull(forged.isotopeZ)
            assertTrue(forged.power >= LootSystem.powerCenter(120) - 3)
        }
    }

    @Test fun aSetPieceStaysInItsSet() {
        val rng = Random(5)
        for (set in IsotopeSets.PERMANENT) for (base in IsotopeSets.BASES) {
            val old = LootSystem.createSetPiece(set, base, 0, rng, 10)
            val forged = LootSystem.reforge(old, 200, 1, rng)
            assertEquals(set.archetype, forged.isotopeSet?.archetype)
            assertEquals(old.base, forged.base)
            assertEquals(old.weight, forged.weight)
            assertTrue(forged.power > old.power)
        }
    }

    /** Une forge posée à côté du feu de camp, et le héros qui marche dessus. */
    private fun heroOnForge(): RoguelikeGame {
        val g = RoguelikeGame(startFloor = 60, rng = Random(7))
        g.level.packs.clear()
        val start = g.playerPos
        val step = listOf(Pos(1, 0), Pos(-1, 0), Pos(0, 1), Pos(0, -1))
            .first { g.level.canStep(start, it.x, it.y) && g.level.tiles[start.y + it.y][start.x + it.x] == TileType.FLOOR }
        g.level.forge = Pos(start.x + step.x, start.y + step.y)
        g.tryMove(step.x, step.y)
        return g
    }

    @Test fun walkingOnTheForgeOpensItAndLeavingClosesIt() {
        val g = heroOnForge()
        assertTrue(g.forgeOpen)
        assertFalse(g.isExploring)
        g.closeForge()
        assertTrue(g.isExploring)
    }

    @Test fun reforgingReplacesTheItemInPlaceAndCostsTheFloorPrice() {
        val g = heroOnForge()
        val hero = g.hero
        val old = LootSystem.generateNormal(5, hero.nextLootId++, Random(1))
        hero.bag += LootSystem.generateNormal(5, hero.nextLootId++, Random(2))
        hero.bag.add(0, old)
        hero.gold = g.forgePrice * 2
        val forged = requireNotNull(g.reforge(old))
        assertEquals(g.forgePrice, hero.gold)
        assertSame(forged, hero.bag[0])
        assertFalse(hero.bag.any { it === old })
        assertEquals(2, hero.bag.size)
        // On peut enchaîner, tant qu'on paie
        val again = requireNotNull(g.reforge(forged))
        assertEquals(0, hero.gold)
        assertNull(g.reforge(again))
        assertSame(again, hero.bag[0])
    }

    @Test fun reforgingAWornItemKeepsItWorn() {
        val g = heroOnForge()
        val hero = g.hero
        val worn = LootSystem.generateNormal(5, hero.nextLootId++, Random(4))
        hero.equip(worn)
        hero.gold = g.forgePrice
        val forged = requireNotNull(g.reforge(worn))
        assertSame(forged, hero.equipped[worn.slot])
        assertFalse(hero.bag.any { it === worn || it === forged })
    }

    @Test fun noReforgeWithoutAnOpenForge() {
        val g = RoguelikeGame(startFloor = 60, rng = Random(7))
        val item = LootSystem.generateNormal(5, 0, Random(1))
        g.hero.bag += item
        g.hero.gold = 1_000_000
        assertNull(g.reforge(item))
        assertEquals(1_000_000, g.hero.gold)
    }

    /** La sauvegarde (JSON) ne tourne pas dans les tests sur ordinateur : on vérifie la régénération. */
    @Test fun theForgeSurvivesRegeneration() {
        repeat(20) { seed ->
            val g = RoguelikeGame(startFloor = 60, rng = Random(seed))
            assertTrue(g.placeForgeNearHero())
            // Régénérer l'étage garde la forge : la chance n'est tirée qu'à l'arrivée
            g.regenerateCurrentFloor()
            val forge = requireNotNull(g.level.forge)
            assertTrue(g.level.tiles[forge.y][forge.x] == TileType.FLOOR && forge != g.level.start)
            assertTrue(g.level.items.none { it.pos == forge })
        }
    }
}
