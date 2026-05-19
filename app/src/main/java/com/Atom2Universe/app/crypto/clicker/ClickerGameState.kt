package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber

data class ClickerGameState(
    val atoms: LayeredNumber = LayeredNumber.zero(),
    val lifetime: LayeredNumber = LayeredNumber.zero(),
    val perClick: LayeredNumber = LayeredNumber.one(),
    val perSecond: LayeredNumber = LayeredNumber.zero(),
    val godFingerLevel: Int = 0,
    val starCoreLevel: Int = 0,
    val gachaTickets: Int = 10,
    val neutrinos: Int = 0,
    val elementTokens: Int = 0,
    val apcToApsLevel: Int = 0,
    val apsToApcLevel: Int = 0
)
