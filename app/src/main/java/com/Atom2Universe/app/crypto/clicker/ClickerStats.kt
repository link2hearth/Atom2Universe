package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber

data class ClickerStats(
    val totalClicks: Long = 0L,
    val clicksDuringFrenzy: Long = 0L,
    val apcFrenzyCount: Int = 0,
    val apsFrenzyCount: Int = 0,
    val maxCps: Double = 0.0,          // record clics/seconde (fenêtre 1s)
    val maxClicksPerApcFrenzy: Int = 0,
    val totalPlayTimeMs: Long = 0L,
    val lifetimeApcAtoms: LayeredNumber = LayeredNumber.zero(),
    val lifetimeApsAtoms: LayeredNumber = LayeredNumber.zero(),
    val spentFromApc: LayeredNumber = LayeredNumber.zero(),
    val spentFromAps: LayeredNumber = LayeredNumber.zero()
)
