package com.Atom2Universe.app.crypto.clicker

data class BigBangEffects(
    val godFingerMult: Double,
    val starCoreMult: Double,
    val protonAccelMult: Double,
    val fusionReactorMult: Double,
    val hadronColliderMult: Double,
    val protonInjectorMult: Double,
    val plasmaCatalystMult: Double,
    val synchrotronMult: Double
) {
    companion object {
        val NONE = BigBangEffects(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
    }
}

object BigBangEngine {
    fun computeEffects(repo: BigBangRepository): BigBangEffects = BigBangEffects(
        godFingerMult      = BigBangBonus.GOD_FINGER.multiplier(repo.getLevel(BigBangBonus.GOD_FINGER)),
        starCoreMult       = BigBangBonus.STAR_CORE.multiplier(repo.getLevel(BigBangBonus.STAR_CORE)),
        protonAccelMult    = BigBangBonus.PROTON_ACCELERATOR.multiplier(repo.getLevel(BigBangBonus.PROTON_ACCELERATOR)),
        fusionReactorMult  = BigBangBonus.FUSION_REACTOR.multiplier(repo.getLevel(BigBangBonus.FUSION_REACTOR)),
        hadronColliderMult = BigBangBonus.HADRON_COLLIDER.multiplier(repo.getLevel(BigBangBonus.HADRON_COLLIDER)),
        protonInjectorMult = BigBangBonus.PROTON_INJECTOR.multiplier(repo.getLevel(BigBangBonus.PROTON_INJECTOR)),
        plasmaCatalystMult = BigBangBonus.PLASMA_CATALYST.multiplier(repo.getLevel(BigBangBonus.PLASMA_CATALYST)),
        synchrotronMult    = BigBangBonus.SYNCHROTRON.multiplier(repo.getLevel(BigBangBonus.SYNCHROTRON))
    )
}
