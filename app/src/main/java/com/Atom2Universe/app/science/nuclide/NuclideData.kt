package com.Atom2Universe.app.science.nuclide

data class Nuclide(
    val Z: Int,
    val N: Int,
    val A: Int,
    val symbol: String,
    val stable: Boolean,
    val halfLife: String?,
    val decayModes: List<String>,
    val spin: String,
    val bindingEnergyPerNucleon: Double
) {
    val notation get() = "${superscript(A)}$symbol"

    private fun superscript(n: Int): String {
        val map = mapOf('0' to '⁰','1' to '¹','2' to '²','3' to '³','4' to '⁴',
                        '5' to '⁵','6' to '⁶','7' to '⁷','8' to '⁸','9' to '⁹')
        return n.toString().map { map[it] ?: it }.joinToString("")
    }

    val decayType: DecayType get() = when {
        stable -> DecayType.STABLE
        decayModes.any { it.startsWith("α") } -> DecayType.ALPHA
        decayModes.any { it == "β-" } -> DecayType.BETA_MINUS
        decayModes.any { it == "β+" || it == "EC" } -> DecayType.BETA_PLUS
        decayModes.any { it.contains("SF") } -> DecayType.FISSION
        else -> DecayType.OTHER
    }
}

enum class DecayType {
    STABLE, ALPHA, BETA_MINUS, BETA_PLUS, FISSION, OTHER
}
