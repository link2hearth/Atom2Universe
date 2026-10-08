package com.Atom2Universe.app.crypto.clicker

data class BigBangEffects(
    val godFingerCostDiscountLevel: Int,
    val starCoreCostDiscountLevel: Int
) {
    companion object {
        val NONE = BigBangEffects(0, 0)
    }
}

object BigBangEngine {
    fun computeEffects(repo: BigBangRepository): BigBangEffects = BigBangEffects(
        godFingerCostDiscountLevel = repo.getLevel(BigBangBonus.GOD_FINGER_DISCOUNT),
        starCoreCostDiscountLevel  = repo.getLevel(BigBangBonus.STAR_CORE_DISCOUNT)
    )
}
