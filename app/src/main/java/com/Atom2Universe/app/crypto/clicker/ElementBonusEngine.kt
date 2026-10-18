package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf
import com.Atom2Universe.app.periodic.PeriodicCollectionStore

data class ElementBonuses(
    val flatApc: Long,   // bonus plat APC (copies gacha)
    val flatAps: Long,   // bonus plat APS (copies gacha)
    val multApc: Double, // multiplicateur accumulé APC, base 1 (somme : 0,5 ⇒ facteur ×1,5)
    val multAps: Double  // multiplicateur accumulé APS, base 1 (somme : 0,5 ⇒ facteur ×1,5)
)

object ElementBonusEngine {
    // Bonus gacha par rareté, déclenché par les tirages (1 tirage = 1 copie).
    //
    // Palier 1 — flat, appliqué à CHAQUE copie gacha :
    //   rareté 1 PRIMORDIAL  → +1    | rareté 2 FUSION      → +10
    //   rareté 3 SUPERNOVA   → +10   | rareté 4 NEUTRONIQUE → +100
    //   rareté 5 SPALLATION  → +100  | rareté 6 SYNTHETIQUE → +1000
    //
    // Palier 2 — multiplicateur (base 1, additif), appliqué à CHAQUE copie gacha, dès la 1re :
    //   r1 +0,005 APC | r2 +0,01 APS | r3 +0,015 APC | r4 +0,02 APS | r5 +0,025 APC | r6 +0,03 APS
    //   La somme s'ajoute à 1 en aval (base × (1 + mult)) → croît énormément avec le temps.
    //
    // Flat et mult d'une rareté vont vers la même cible (APC ou APS), en alternance par rareté.
    // Seules les copies gacha comptent (totalEver - fusion) ; les copies de fusion ont leur
    // propre bonus via FusionStore.
    private data class RarityBonus(val flat: Long, val mult: Double, val toApc: Boolean)

    private fun rarityBonus(rarity: GachaRarity): RarityBonus = when (rarity) {
        GachaRarity.PRIMORDIAL  -> RarityBonus(1L,    0.005, toApc = true)
        GachaRarity.FUSION      -> RarityBonus(10L,   0.010, toApc = false)
        GachaRarity.SUPERNOVA   -> RarityBonus(10L,   0.015, toApc = true)
        GachaRarity.NEUTRONIQUE -> RarityBonus(100L,  0.020, toApc = false)
        GachaRarity.SPALLATION  -> RarityBonus(100L,  0.025, toApc = true)
        GachaRarity.SYNTHETIQUE -> RarityBonus(1000L, 0.030, toApc = false)
    }

    fun compute(store: PeriodicCollectionStore): ElementBonuses {
        // Copies gacha cumulées par rareté.
        val gachaByRarity = HashMap<GachaRarity, Long>()
        for (atomicNum in 1..118) {
            val total = store.getTotalEverCount(atomicNum).toLong()
            if (total <= 0L) continue
            val fusion = store.getFusionCount(atomicNum).toLong()
            val gacha = (total - fusion).coerceAtLeast(0L)
            if (gacha <= 0L) continue
            val rarity = rarityOf(atomicNum)
            gachaByRarity[rarity] = (gachaByRarity[rarity] ?: 0L) + gacha
        }

        var flatApc = 0L
        var flatAps = 0L
        var multApc = 0.0
        var multAps = 0.0
        for ((rarity, copies) in gachaByRarity) {
            val def = rarityBonus(rarity)
            val flat = copies * def.flat
            if (def.toApc) flatApc += flat else flatAps += flat
            val mult = copies * def.mult
            if (def.toApc) multApc += mult else multAps += mult
        }

        return ElementBonuses(flatApc, flatAps, multApc, multAps)
    }
}
