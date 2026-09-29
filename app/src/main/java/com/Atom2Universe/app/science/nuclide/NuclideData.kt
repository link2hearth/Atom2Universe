package com.Atom2Universe.app.science.nuclide

data class Nuclide(
    val Z: Int,
    val N: Int,
    val A: Int,
    val symbol: String,
    val stable: Boolean,
    /** Mantisse telle que dans les données (« 1.248e9 », « 12.32 »), sans unité. */
    val halfLifeValue: String?,
    /** Code d'unité des données : ns, μs, ms, s, min, h, d, a. */
    val halfLifeUnit: String?,
    /** Modes dans l'ordre des données, le principal en premier : α, β-, 2β-, β+, EC, SF. */
    val decayModes: List<String>,
    /** Spin/parité normalisé (« 1/2+ »), null si inconnu. */
    val spin: String?,
    val bindingEnergyPerNucleon: Double
) {
    val notation get() = "${superscript(A)}$symbol"

    /** Couleur de la carte : celle du mode principal, comme sur les cartes de nucléides usuelles. */
    val decayType: DecayType get() {
        if (stable) return DecayType.STABLE
        val main = decayModes.firstOrNull() ?: return DecayType.OTHER
        return when {
            main.startsWith("α") -> DecayType.ALPHA
            // 2β- est une double désintégration bêta moins : même famille que β-.
            main == "β-" || main == "2β-" -> DecayType.BETA_MINUS
            main == "β+" || main == "EC" -> DecayType.BETA_PLUS
            main.contains("SF") -> DecayType.FISSION
            else -> DecayType.OTHER
        }
    }

    companion object {
        private const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

        fun superscript(n: Int): String = n.toString().map { c ->
            when (c) {
                in '0'..'9' -> SUPERSCRIPT_DIGITS[c - '0']
                '-' -> '⁻'
                else -> c
            }
        }.joinToString("")
    }
}

enum class DecayType {
    STABLE, ALPHA, BETA_MINUS, BETA_PLUS, FISSION, OTHER
}
