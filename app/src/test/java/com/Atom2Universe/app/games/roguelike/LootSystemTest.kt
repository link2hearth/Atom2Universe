package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Règles du butin décidées dans DONJON.md, vérifiées sur beaucoup de tirages. */
class LootSystemTest {

    @Test
    fun weaponsAlwaysGiveTheirTypeAttribute() {
        val rng = Random(1)
        repeat(5000) {
            val e = LootSystem.generate(rng.nextInt(1, 101), 0, rng)
            val expected = e.base.attribute ?: return@repeat
            assertTrue("${e.base} doit donner $expected", e.implicits.any { it.type == expected && it.value >= 1f })
            if (e.base.damageMult > 0f) assertTrue(e.damageMax > e.damageMin)
        }
    }

    @Test
    fun tierFiveOverlapsNextMaterialTierOne() {
        val rng = Random(2)
        for (m in Material.entries.dropLast(1)) {
            val five = LootSystem.create(ItemBase.ARMOR, m, 5, Rarity.NORMAL, 0, rng)
            val nextOne = LootSystem.create(ItemBase.ARMOR, Material.entries[m.ordinal + 1], 1, Rarity.NORMAL, 0, rng)
            assertEquals(five.power, nextOne.power)
            assertEquals(five.armor, nextOne.armor)
        }
    }

    @Test
    fun raritiesRespectAffixCounts() {
        val rng = Random(3)
        repeat(5000) {
            val e = LootSystem.generate(rng.nextInt(1, 101), 0, rng)
            assertTrue(e.affixes.size in e.rarity.minAffixes..e.rarity.maxAffixes)
            assertEquals("pas deux fois le même affixe", e.affixes.size, e.affixes.map { it.type }.toSet().size)
        }
    }

    /** Ce qu'on trouve selon l'étage : matières, tiers et raretés (rapport dans build/loot-report.txt). */
    @Test
    fun dropReport() {
        val rng = Random(4)
        val out = StringBuilder("Matière × tier trouvés par étage (1000 objets chacun)\n")
        for (floor in listOf(1, 5, 10, 20, 40, 60, 80, 100)) {
            val items = List(1000) { LootSystem.generate(floor, 0, rng) }
            val grades = items.groupingBy { "${it.material} ${it.tier}" }.eachCount().entries.sortedByDescending { it.value }.take(6)
            val rar = Rarity.entries.joinToString { r -> "$r ${items.count { it.rarity == r } / 10}%" }
            val rating = items.map { LootSystem.rating(it) }.average().toInt()
            out.appendLine("Étage $floor : ${grades.joinToString { "${it.key} (${it.value / 10}%)" }}  |  $rar  |  note moyenne $rating")
        }
        File("build/loot-report.txt").writeText(out.toString())
        println(out)
    }
}
