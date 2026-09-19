package com.Atom2Universe.app.games.roguelike

import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Ce que vaut chaque main gauche, à puissance égale, sans affixe : la note de l'objet et ses lignes. */
class OffhandBudgetTest {
    @Test
    fun offhandRatings() {
        val out = StringBuilder("Note d'une main gauche Normale, par puissance\n")
        for (power in listOf(1, 10, 20, 50)) {
            for (base in ItemBase.entries.filter { it.slot == EquipSlot.OFFHAND }) {
                val e = LootSystem.create(base, power, Rarity.NORMAL, 0, Random(0))
                out.appendLine(String.format("P%-3d %-8s note %5d  armure %3d  CA %d  %s", power, base.name, LootSystem.rating(e), e.armor, e.acBonus,
                    e.implicits.joinToString(" ") { "${it.type.name}:${"%.2f".format(it.value)}" }))
            }
        }
        File("build/roguelike-offhand.txt").writeText(out.toString())
        println(out)
    }
}
