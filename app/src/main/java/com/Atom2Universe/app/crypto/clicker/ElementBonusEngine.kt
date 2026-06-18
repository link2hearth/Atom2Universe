package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf
import com.Atom2Universe.app.periodic.PeriodicCollectionStore

data class ElementBonuses(
    val flatApc: Long,   // bonus plat APC (copies gacha)
    val flatAps: Long,   // bonus plat APS (copies gacha)
    val multApc: Double, // % accumulé APC (copies gacha, débloqué à 100 tirages)
    val multAps: Double  // % accumulé APS (copies gacha, débloqué à 100 tirages)
)

object ElementBonusEngine {
    // Bonus gacha par rareté, déclenché par les tirages (1 tirage = 1 copie).
    //
    // Palier 1 — flat, appliqué à CHAQUE copie gacha, toujours :
    //   rareté 1 PRIMORDIAL  → +1    | rareté 2 FUSION      → +10
    //   rareté 3 SUPERNOVA   → +10   | rareté 4 NEUTRONIQUE → +100
    //   rareté 5 SPALLATION  → +100  | rareté 6 SYNTHETIQUE → +1000
    //
    // Palier 2 — %, appliqué à CHAQUE copie gacha, débloqué une fois >= 100 tirages gacha :
    //   r1 +0,1% APC | r2 +0,2% APS | r3 +0,2% APC | r4 +0,3% APS | r5 +0,5% APC | r6 +0,5% APS
    //
    // Flat et % d'une rareté vont vers la même cible (APC ou APS), en alternance par rareté.
    // Seules les copies gacha comptent (totalEver - fusion) ; les copies de fusion ont leur
    // propre bonus via FusionStore.
    private const val PULL_THRESHOLD = 100

    private data class RarityBonus(val flat: Long, val pct: Double, val toApc: Boolean)

    private fun rarityBonus(rarity: GachaRarity): RarityBonus = when (rarity) {
        GachaRarity.PRIMORDIAL  -> RarityBonus(1L,    0.001, toApc = true)
        GachaRarity.FUSION      -> RarityBonus(10L,   0.002, toApc = false)
        GachaRarity.SUPERNOVA   -> RarityBonus(10L,   0.002, toApc = true)
        GachaRarity.NEUTRONIQUE -> RarityBonus(100L,  0.003, toApc = false)
        GachaRarity.SPALLATION  -> RarityBonus(100L,  0.005, toApc = true)
        GachaRarity.SYNTHETIQUE -> RarityBonus(1000L, 0.005, toApc = false)
    }

    fun compute(store: PeriodicCollectionStore): ElementBonuses {
        // Copies gacha cumulées par rareté + total des tirages gacha (pour le seuil).
        val gachaByRarity = HashMap<GachaRarity, Long>()
        var totalGachaPulls = 0L
        for (atomicNum in 1..118) {
            val total = store.getTotalEverCount(atomicNum).toLong()
            if (total <= 0L) continue
            val fusion = store.getFusionCount(atomicNum).toLong()
            val gacha = (total - fusion).coerceAtLeast(0L)
            if (gacha <= 0L) continue
            val rarity = rarityOf(atomicNum)
            gachaByRarity[rarity] = (gachaByRarity[rarity] ?: 0L) + gacha
            totalGachaPulls += gacha
        }

        val pctUnlocked = totalGachaPulls >= PULL_THRESHOLD

        var flatApc = 0L
        var flatAps = 0L
        var multApc = 0.0
        var multAps = 0.0
        for ((rarity, copies) in gachaByRarity) {
            val def = rarityBonus(rarity)
            val flat = copies * def.flat
            if (def.toApc) flatApc += flat else flatAps += flat
            if (pctUnlocked) {
                val pct = copies * def.pct
                if (def.toApc) multApc += pct else multAps += pct
            }
        }

        return ElementBonuses(flatApc, flatAps, multApc, multAps)
    }
}
