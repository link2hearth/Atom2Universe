package com.Atom2Universe.app.crypto.gacha

import com.Atom2Universe.app.periodic.PeriodicElement
import com.Atom2Universe.app.periodic.getPeriodicElements

enum class GachaRarity(
    val label: String,
    val colorHex: Long,
    val weight: Int
) {
    COMMUN("Commun",    0xFFCCCCCC, 400),
    ESSENTIEL("Essentiel", 0xFF00CC66, 250),
    STELLAIRE("Stellaire", 0xFF00BBFF, 180),
    MYTHIQUE("Mythique",   0xFFFFCC00, 100),
    SINGULIER("Singulier", 0xFFFF55FF,  50),
    IRREEL("Irréel",       0xFFFF3333,  20);

    val color: Int get() = colorHex.toInt()
}

private val RARITY_MAP: Map<Int, GachaRarity> = buildMap {
    listOf(1, 2).forEach { put(it, GachaRarity.COMMUN) }
    listOf(3, 4, 5).forEach { put(it, GachaRarity.ESSENTIEL) }
    (6..14).forEach { put(it, GachaRarity.STELLAIRE) }
    (15..28).forEach { put(it, GachaRarity.MYTHIQUE) }
    (29..42).forEach { put(it, GachaRarity.SINGULIER) }
    put(43, GachaRarity.IRREEL)   // Technétium
    (44..60).forEach { put(it, GachaRarity.SINGULIER) }
    put(61, GachaRarity.IRREEL)   // Prométhium
    (62..92).forEach { put(it, GachaRarity.SINGULIER) }
    (93..118).forEach { put(it, GachaRarity.IRREEL) }
}

fun rarityOf(atomicNumber: Int): GachaRarity =
    RARITY_MAP[atomicNumber] ?: GachaRarity.SINGULIER

fun rollGacha(): Pair<PeriodicElement, GachaRarity> {
    val totalWeight = GachaRarity.entries.sumOf { it.weight }
    var roll = (1..totalWeight).random()
    val targetRarity = GachaRarity.entries.first { rarity ->
        roll -= rarity.weight
        roll <= 0
    }

    val pool = getPeriodicElements().filter { rarityOf(it.atomicNumber) == targetRarity }
    val element = pool.random()
    return element to targetRarity
}
