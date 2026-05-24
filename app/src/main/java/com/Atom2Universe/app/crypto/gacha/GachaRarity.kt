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
    COMMUN("Commun",    0xFFCCCCCC, 400, R.string.gacha_rarity_commun),
    ESSENTIEL("Essentiel", 0xFF00CC66, 250, R.string.gacha_rarity_essentiel),
    STELLAIRE("Stellaire", 0xFF00BBFF, 180, R.string.gacha_rarity_stellaire),
    MYTHIQUE("Mythique",   0xFFFFCC00, 100, R.string.gacha_rarity_mythique),
    SINGULIER("Singulier", 0xFFFF55FF,  50, R.string.gacha_rarity_singulier),
    IRREEL("Irréel",       0xFFFF3333,  20, R.string.gacha_rarity_irreel);

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
