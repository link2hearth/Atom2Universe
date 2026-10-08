package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber

data class ProductionBreakdown(
    val apcShopBase: LayeredNumber = LayeredNumber.one(),
    val apcElemFlat: Long = 0L,
    val apcElemMult: Double = 0.0,
    val apcFusionMult: Double = 0.0,
    val apcConvFromAps: LayeredNumber = LayeredNumber.zero(),
    val apcFactoryMult: Double = 0.0,
    val apcFrenzyMult: Double = 1.0,

    val apsShopBase: LayeredNumber = LayeredNumber.zero(),
    val apsElemFlat: Long = 0L,
    val apsElemMult: Double = 0.0,
    val apsFusionMult: Double = 0.0,
    val apsConvFromApc: LayeredNumber = LayeredNumber.zero(),
    val apsFactoryMult: Double = 0.0,
    val apsFrenzyMult: Double = 1.0,
)
