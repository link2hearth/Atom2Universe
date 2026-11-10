package com.Atom2Universe.app.crypto.clicker

/** Les achats en atomes, dans l'ordre de la boutique (départage les prix identiques). */
internal enum class AtomShopUpgrade(val preferenceId: String, val factory: FactoryType? = null) {
    GOD_FINGER("godFinger"),
    STAR_CORE("starCore"),
    PROTON_ACCELERATOR(FactoryType.PROTON_ACCELERATOR),
    PROTON_INJECTOR(FactoryType.PROTON_INJECTOR),
    FUSION_REACTOR(FactoryType.FUSION_REACTOR),
    PLASMA_CATALYST(FactoryType.PLASMA_CATALYST),
    HADRON_COLLIDER(FactoryType.HADRON_COLLIDER),
    SYNCHROTRON(FactoryType.SYNCHROTRON);

    constructor(type: FactoryType) : this(type.name, type)

    companion object {
        val factories = entries.filter { it.factory != null }
    }

    fun level(state: ClickerGameState): Int = when (this) {
        GOD_FINGER -> state.godFingerLevel
        STAR_CORE -> state.starCoreLevel
        else -> state.factoryCounts[factory] ?: 0
    }
}
