package com.Atom2Universe.app.crypto.clicker

enum class BigBangBonus(val id: String, val tokenCost: Int, val effectPercent: Int) {
    GOD_FINGER("god_finger", 50, 20),
    STAR_CORE("star_core", 50, 20),
    PROTON_ACCELERATOR("proton_accel", 50, 20),
    FUSION_REACTOR("fusion_reactor", 50, 20),
    HADRON_COLLIDER("hadron_collider", 50, 20),
    PROTON_INJECTOR("proton_injector", 50, 20),
    PLASMA_CATALYST("plasma_catalyst", 50, 20),
    SYNCHROTRON("synchrotron", 50, 20),
    // Rabais exponentiel borné sur le prix de Doigt Créateur / Cœur d'Étoile.
    // effectPercent n'est pas utilisé pour ce bonus (la pente est calculée dans ClickerShopEngine).
    SPACETIME_COMPRESSION("spacetime_compression", 50, 0);

    fun multiplier(level: Int): Double = 1.0 + level * (effectPercent / 100.0)
}
