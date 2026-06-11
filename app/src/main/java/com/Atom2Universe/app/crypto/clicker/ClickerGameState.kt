package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber

data class ClickerGameState(
    val atoms: LayeredNumber = LayeredNumber.zero(),
    val lifetime: LayeredNumber = LayeredNumber.zero(),
    val allTimeTotalAtoms: LayeredNumber = LayeredNumber.zero(),
    val perClick: LayeredNumber = LayeredNumber.one(),
    val perSecond: LayeredNumber = LayeredNumber.zero(),
    val godFingerLevel: Int = 0,
    val starCoreLevel: Int = 0,
    val gachaTickets: Int = 10,
    val neutrinos: Int = 0,
    val elementTokens: Int = 0,
    val apcToApsLevel: Int = 0,
    val apsToApcLevel: Int = 0,
    val factoryCounts: Map<FactoryType, Int> = emptyMap(),
    val isFusionAvailable: Boolean = false,
    val breakdown: ProductionBreakdown = ProductionBreakdown(),
    val quarks: Int = 0,
    val critChanceLevel: Int = 0,
    val critDamageLevel: Int = 0,
    val critUnlocked: Boolean = false,
)
