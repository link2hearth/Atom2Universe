package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf
import com.Atom2Universe.app.periodic.PeriodicCollectionStore

data class ElementBonuses(
    val flatApc: Long,   // H(+1 gacha), C(+10 fusion), O(+100 fusion), SUPERNOVA pair(+1000 fusion)
    val flatAps: Long,   // He(+1 fusion), Ne(+10 fusion), Fe(+100 fusion), SUPERNOVA impair(+1000 fusion)
    val multApc: Double, // % accumulé via copies gacha
    val multAps: Double
)

object ElementBonusEngine {
    // Règle : flat = copies obtenues via fusion uniquement (getFusionCount)
    //         %    = copies obtenues via gacha uniquement (getTotalEverCount - getFusionCount)
    //
    // Chaîne de fusion et bonus flat par copie fusion :
    //   H  (#1)           → +1 APC  (exception : H vient du gacha uniquement, getTotalEverCount)
    //   He (#2)           → +1 APS  (fusion)
    //   C  (#6)           → +10 APC (fusion)
    //   Ne (#10)          → +10 APS (fusion)
    //   O  (#8)           → +100 APC (fusion)
    //   Fe (#26)          → +100 APS (fusion)
    //   SUPERNOVA Z15–28 pair  → +1000 APC (neutron capture)
    //   SUPERNOVA Z15–28 impair→ +1000 APS (neutron capture)
    //
    // Bonus % par rareté (gacha uniquement) :
    //   FUSION      → ×mult APS  +0.01%/copie
    //   SUPERNOVA   → ×mult APC  +0.1%/copie
    //   NEUTRONIQUE → ×mult APS  +0.1%/copie
    //   SPALLATION  → ×mult APC  +0.01%/copie
    //   SYNTHETIQUE → ×mult APC+APS +0.1%/copie chacun

    private const val BONUS_LOW  = 0.0001  // 0.01%
    private const val BONUS_HIGH = 0.001   // 0.1%

    fun compute(store: PeriodicCollectionStore): ElementBonuses {
        var flatApc = 0L
        var flatAps = 0L
        var multApc = 0.0
        var multAps = 0.0

        for (atomicNum in 1..118) {
            val totalCopies = store.getTotalEverCount(atomicNum).toLong()
            if (totalCopies <= 0L) continue

            val fusionCopies = store.getFusionCount(atomicNum).toLong()
            val gachaCopies  = (totalCopies - fusionCopies).coerceAtLeast(0L)

            when (atomicNum) {
                1  -> flatApc += totalCopies                  // H et He : bonus flat via gacha
                2  -> flatAps += totalCopies
                6  -> { flatApc += fusionCopies * 10L;  multAps += gachaCopies * BONUS_LOW  }
                8  -> { flatApc += fusionCopies * 100L; multApc += gachaCopies * BONUS_LOW  }
                10 -> { flatAps += fusionCopies * 10L;  multAps += gachaCopies * BONUS_LOW  }
                26 -> { flatAps += fusionCopies * 100L; multApc += gachaCopies * BONUS_HIGH }
                else -> {
                    val rarity = rarityOf(atomicNum)
                    // Éléments SUPERNOVA Z15–28 (hors 8 et 26 déjà traités) :
                    // flat +1000 depuis fusion, % depuis gacha
                    if (rarity == GachaRarity.SUPERNOVA && atomicNum in 15..28) {
                        if (atomicNum % 2 == 0) flatApc += fusionCopies * 1000L
                        else                    flatAps += fusionCopies * 1000L
                        multApc += gachaCopies * BONUS_HIGH
                    } else {
                        val copies = totalCopies  // éléments non-chaîne : tous gacha
                        when (rarity) {
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
            }
        }

        return ElementBonuses(flatApc, flatAps, multApc, multAps)
    }
}
