package com.Atom2Universe.app.crypto.clicker

data class FrenzyUiState(
    val apcEffectExpiries: List<Long> = emptyList(),
    val apsEffectExpiries: List<Long> = emptyList(),
    val apcClickCount: Int = 0
) {
    fun isEmpty(nowMs: Long): Boolean =
        apcEffectExpiries.none { it > nowMs } &&
        apsEffectExpiries.none { it > nowMs }
}
