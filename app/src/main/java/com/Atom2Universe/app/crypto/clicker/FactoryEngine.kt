package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
import kotlin.math.log10
import kotlin.math.pow

internal object FactoryEngine {

    private const val PRICE_SCALE = 1.35

    fun cost(type: FactoryType, currentCount: Int): LayeredNumber {
        val logPrice = log10(type.basePrice) + currentCount * log10(PRICE_SCALE)
        // Petits prix : calcul direct, identique à avant ; au-delà, mantisse/exposant
        // (un Double passerait à l'infini, donc à 0, vers 1e308).
        if (logPrice < 15.0) return LayeredNumber(type.basePrice * PRICE_SCALE.pow(currentCount))
        val exponent = kotlin.math.floor(logPrice)
        return LayeredNumber.fromLayer0(10.0.pow(logPrice - exponent), exponent)
    }

    fun computeApcBonus(counts: Map<FactoryType, Int>): Double {
        val accelCount    = counts[FactoryType.PROTON_ACCELERATOR] ?: 0
        val injectorCount = counts[FactoryType.PROTON_INJECTOR]    ?: 0
        val colliderCount = counts[FactoryType.HADRON_COLLIDER]    ?: 0
        val synchCount    = counts[FactoryType.SYNCHROTRON]        ?: 0
        val accelBonus    = accelCount    * FactoryType.PROTON_ACCELERATOR.bonusApcPerUnit *
                            (1.0 + injectorCount * FactoryType.PROTON_INJECTOR.boostPerUnit)
        val colliderBonus = colliderCount * FactoryType.HADRON_COLLIDER.bonusApcPerUnit *
                            (1.0 + synchCount    * FactoryType.SYNCHROTRON.boostPerUnit)
        return accelBonus + colliderBonus
    }

    fun computeApsBonus(counts: Map<FactoryType, Int>): Double {
        val reactorCount  = counts[FactoryType.FUSION_REACTOR]  ?: 0
        val catalystCount = counts[FactoryType.PLASMA_CATALYST] ?: 0
        val colliderCount = counts[FactoryType.HADRON_COLLIDER] ?: 0
        val synchCount    = counts[FactoryType.SYNCHROTRON]     ?: 0
        val reactorBonus  = reactorCount  * FactoryType.FUSION_REACTOR.bonusApsPerUnit *
                            (1.0 + catalystCount * FactoryType.PLASMA_CATALYST.boostPerUnit)
        val colliderBonus = colliderCount * FactoryType.HADRON_COLLIDER.bonusApsPerUnit *
                            (1.0 + synchCount    * FactoryType.SYNCHROTRON.boostPerUnit)
        return reactorBonus + colliderBonus
    }
}
