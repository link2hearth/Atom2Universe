package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf
import com.Atom2Universe.app.periodic.PeriodicCollectionStore

data class ElementBonuses(
    val flatApc: Int,    // copies de H (#1) → +1 APC flat par copie
    val flatAps: Int,    // copies de He (#2) → +1 APS flat par copie
    val multApc: Double, // ex: 0.05 = +5% APC (somme de tous les éléments collectés)
    val multAps: Double
)

object ElementBonusEngine {
    // Attribution des bonus par rareté :
    //   H (#1)          → flat +1 APC/copie
    //   He (#2)         → flat +1 APS/copie
    //   FUSION          → ×mult APS  +0.01%/copie
    //   SUPERNOVA       → ×mult APC  +0.1%/copie
    //   NEUTRONIQUE     → ×mult APS  +0.1%/copie
    //   SPALLATION      → ×mult APC  +0.01%/copie
    //   SYNTHETIQUE     → ×mult APC+APS +0.1%/copie chacun

    private const val BONUS_LOW  = 0.0001  // 0.01%
    private const val BONUS_HIGH = 0.001   // 0.1%

    fun compute(store: PeriodicCollectionStore): ElementBonuses {
        var flatApc = 0
        var flatAps = 0
        var multApc = 0.0
        var multAps = 0.0

        for (atomicNum in 1..118) {
            val copies = store.getMaxCopyCount(atomicNum)
            if (copies <= 0) continue

            when (atomicNum) {
                1 -> flatApc += copies
                2 -> flatAps += copies
                else -> when (rarityOf(atomicNum)) {
                    GachaRarity.FUSION      -> multAps += copies * BONUS_LOW
                    GachaRarity.SUPERNOVA   -> multApc += copies * BONUS_HIGH
                    GachaRarity.NEUTRONIQUE -> multAps += copies * BONUS_HIGH
                    GachaRarity.SPALLATION  -> multApc += copies * BONUS_LOW
                    GachaRarity.SYNTHETIQUE -> {
                        multApc += copies * BONUS_HIGH
                        multAps += copies * BONUS_HIGH
                    }
                    else -> Unit
                }
            }
        }

        return ElementBonuses(flatApc, flatAps, multApc, multAps)
    }
}
