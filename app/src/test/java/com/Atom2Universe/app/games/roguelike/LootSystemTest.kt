package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

/** Règles du butin décidées dans DONJON.md, vérifiées sur beaucoup de tirages. */
class LootSystemTest {

    /** Les sauvegardes d'avant le renommage gardent leur rang : l'ancien « Rare » est l'Épique. */
    @Test
    fun lesAnciennesRaretesGardentLeurRang() {
        assertEquals(Rarity.COMMON, Rarity.fromLegacy("NORMAL"))
        assertEquals(Rarity.RARE, Rarity.fromLegacy("MAGIC"))
        assertEquals(Rarity.EPIC, Rarity.fromLegacy("RARE"))
        for (r in Rarity.entries) assertEquals(r, Rarity.fromLegacy(Rarity.legacyName(r)))
    }

    @Test
    fun chaqueArmeEtMainGaucheAUneClasse() {
        for (b in ItemBase.entries.filter { it.slot == EquipSlot.WEAPON || it.slot == EquipSlot.OFFHAND })
            assertTrue("$b n'a aucune classe", !LootSystem.affinity(b).isNullOrEmpty())
        assertEquals(Archetype.entries.toList(), LootSystem.affinity(ItemBase.SWORD))
        assertEquals(listOf(Archetype.VAGABOND), LootSystem.affinity(ItemBase.SPEAR))
    }

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

    /**
     * Les noms suivent le tableau périodique : un tier tous les deux crans de puissance (5
     * étages), cinq tiers par élément (25 étages), 118 éléments, puis un nouveau cycle.
     */
    @Test
    fun lesNomsSuiventLeTableauPeriodique() {
        assertEquals(Triple(0, 1, 0), grade(1))            // Hydrogène I
        assertEquals(Triple(0, 1, 0), grade(2))
        assertEquals(Triple(0, 2, 0), grade(3))            // Hydrogène II
        assertEquals(Triple(0, 5, 0), grade(10))           // Hydrogène V
        assertEquals(Triple(1, 1, 0), grade(11))           // Hélium I
        assertEquals(Triple(117, 5, 0), grade(1180))       // Oganesson V
        assertEquals(Triple(0, 1, 1), grade(1181))         // Hydrogène stellaire I
        // Le Fer (26) vers l'étage 650, comme décidé
        val iron = (1..100_000).first { Grade.element(LootSystem.powerCenter(it).roundToInt()) == 25 }
        assertTrue("le Fer arrive à l'étage $iron", iron in 620..660)
        // L'étage 10 000 est dans le 4e cycle (cosmique)
        assertEquals(3, Grade.cycle(LootSystem.powerCenter(10_000).roundToInt()))
    }

    private fun grade(power: Int) = Triple(Grade.element(power), Grade.tier(power), Grade.cycle(power))

    @Test
    fun raritiesRespectAffixCounts() {
        val rng = Random(3)
        repeat(5000) {
            val e = LootSystem.generate(rng.nextInt(1, 101), 0, rng)
            // Une pièce de set porte un affixe de plus qu'une rare (IsotopeSets.SET_AFFIXES)
            if (e.isotopeZ != null) assertEquals(IsotopeSets.SET_AFFIXES, e.affixes.size)
            else assertTrue(e.affixes.size in e.rarity.minAffixes..e.rarity.maxAffixes)
            assertEquals("pas deux fois le même affixe", e.affixes.size, e.affixes.map { it.type }.toSet().size)
        }
    }

    /** Ce qu'on trouve selon l'étage : matières, tiers et raretés (rapport dans build/loot-report.txt). */
    @Test
    fun dropReport() {
        val rng = Random(4)
        val out = StringBuilder("Matière × tier trouvés par étage (1000 objets chacun)\n")
        for (floor in listOf(1, 5, 10, 20, 40, 60, 80, 100, 300, 1000, 3000, 10_000)) {
            val items = List(1000) { LootSystem.generate(floor, 0, rng) }
            val grades = items.groupingBy { "élément ${Grade.element(it.power) + 1} tier ${Grade.tier(it.power)}" }.eachCount().entries.sortedByDescending { it.value }.take(6)
            val rar = Rarity.entries.joinToString { r -> "$r ${items.count { it.rarity == r } / 10}%" }
            val rating = items.map { LootSystem.rating(it) }.average().toInt()
            out.appendLine("Étage $floor : ${grades.joinToString { "${it.key} (${it.value / 10}%)" }}  |  $rar  |  note moyenne $rating")
        }
        File("build/loot-report.txt").writeText(out.toString())
        println(out)
    }
}
