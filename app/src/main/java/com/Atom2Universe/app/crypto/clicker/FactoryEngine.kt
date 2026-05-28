package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
import kotlin.math.pow

internal object FactoryEngine {

    private const val PRICE_SCALE = 1.35

    fun cost(type: FactoryType, currentCount: Int): LayeredNumber {
        val price = type.basePrice * PRICE_SCALE.pow(currentCount)
        return LayeredNumber(price)
    }

    fun computeApcBonus(counts: Map<FactoryType, Int>, bb: BigBangEffects = BigBangEffects.NONE): Double {
        val accelCount    = counts[FactoryType.PROTON_ACCELERATOR] ?: 0
        val injectorCount = counts[FactoryType.PROTON_INJECTOR]    ?: 0
        val colliderCount = counts[FactoryType.HADRON_COLLIDER]    ?: 0
        val synchCount    = counts[FactoryType.SYNCHROTRON]        ?: 0
        val accelBonus    = accelCount    * FactoryType.PROTON_ACCELERATOR.bonusApcPerUnit * bb.protonAccelMult *
                            (1.0 + injectorCount * FactoryType.PROTON_INJECTOR.boostPerUnit * bb.protonInjectorMult)
        val colliderBonus = colliderCount * FactoryType.HADRON_COLLIDER.bonusApcPerUnit * bb.hadronColliderMult *
                            (1.0 + synchCount    * FactoryType.SYNCHROTRON.boostPerUnit * bb.synchrotronMult)
        return accelBonus + colliderBonus
    }

    fun computeApsBonus(counts: Map<FactoryType, Int>, bb: BigBangEffects = BigBangEffects.NONE): Double {
        val reactorCount  = counts[FactoryType.FUSION_REACTOR]  ?: 0
        val catalystCount = counts[FactoryType.PLASMA_CATALYST] ?: 0
        val colliderCount = counts[FactoryType.HADRON_COLLIDER] ?: 0
        val synchCount    = counts[FactoryType.SYNCHROTRON]     ?: 0
        val reactorBonus  = reactorCount  * FactoryType.FUSION_REACTOR.bonusApsPerUnit * bb.fusionReactorMult *
                            (1.0 + catalystCount * FactoryType.PLASMA_CATALYST.boostPerUnit * bb.plasmaCatalystMult)
        val colliderBonus = colliderCount * FactoryType.HADRON_COLLIDER.bonusApsPerUnit * bb.hadronColliderMult *
                            (1.0 + synchCount    * FactoryType.SYNCHROTRON.boostPerUnit * bb.synchrotronMult)
        return reactorBonus + colliderBonus
    }
}
