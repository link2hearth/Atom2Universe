package com.Atom2Universe.app.crypto.gacha

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import com.Atom2Universe.app.periodic.PeriodicElement
import com.Atom2Universe.app.periodic.getPeriodicElements

enum class GachaRarity(
    val label: String,
    val colorHex: Long,
    val weight: Int,
    @StringRes val nameRes: Int
) {
    PRIMORDIAL("Primordial",   0xFFCCCCCC,  75, R.string.gacha_rarity_primordial),
    FUSION("Fusion",           0xFF00BBFF,  10, R.string.gacha_rarity_fusion),
    SUPERNOVA("Supernova",     0xFFFFCC00,   7, R.string.gacha_rarity_supernova),
    NEUTRONIQUE("Neutronique", 0xFFFF55FF,   5, R.string.gacha_rarity_neutronique),
    SPALLATION("Spallation",   0xFF00CC66,   1, R.string.gacha_rarity_spallation),
    SYNTHETIQUE("Synthétique", 0xFFFF3333,   2, R.string.gacha_rarity_synthetique);

    val color: Int get() = colorHex.toInt()
}

private val RARITY_MAP: Map<Int, GachaRarity> = buildMap {
    listOf(1, 2).forEach { put(it, GachaRarity.PRIMORDIAL) }
    listOf(3, 4, 5).forEach { put(it, GachaRarity.SPALLATION) }
    (6..14).forEach { put(it, GachaRarity.FUSION) }
    (15..28).forEach { put(it, GachaRarity.SUPERNOVA) }
    (29..42).forEach { put(it, GachaRarity.NEUTRONIQUE) }
    put(43, GachaRarity.SYNTHETIQUE)   // Technétium
    (44..60).forEach { put(it, GachaRarity.NEUTRONIQUE) }
    put(61, GachaRarity.SYNTHETIQUE)   // Prométhium
    (62..92).forEach { put(it, GachaRarity.NEUTRONIQUE) }
    (93..118).forEach { put(it, GachaRarity.SYNTHETIQUE) }
}

fun rarityOf(atomicNumber: Int): GachaRarity =
    RARITY_MAP[atomicNumber] ?: GachaRarity.NEUTRONIQUE

fun atomicNumbersOf(rarity: GachaRarity): List<Int> =
    RARITY_MAP.entries.filter { it.value == rarity }.map { it.key }.sorted()

/**
 * Retourne le nombre de raretés consécutives complètes (au moins 1 copie de chaque élément).
 * S'arrête à la première rareté incomplète — la rareté n+1 ne compte pas sans la n.
 * Rareté 1 (COMMUN) complète → 1, rareté 2 (ESSENTIEL) aussi → 2, etc.
 */
fun completedRarityCount(store: PeriodicCollectionStore): Int {
    var count = 0
    for (rarity in GachaRarity.entries) {
        if (atomicNumbersOf(rarity).all { store.hasElement(it) }) count++
        else break
    }
    return count
}

/**
 * Retourne la probabilité de spawn de frénésie par seconde en fonction du nombre de raretés complètes.
 * 0 → frénésies verrouillées ; 1 → 1% ; 2 → 2% ; … ; 5 → 5% ; 6 (toutes) → 10%.
 */
fun frenzyChanceForCompletedRarities(count: Int): Double = when (count) {
    0    -> 0.0
    1    -> 0.01
    2    -> 0.02
    3    -> 0.03
    4    -> 0.04
    5    -> 0.05
    else -> 0.10
}

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
