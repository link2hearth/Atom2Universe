package com.Atom2Universe.app.science.nuclide

/** Un mode de désintégration et sa part en %, null si les données ne la donnent pas. */
data class DecayMode(val mode: String, val percent: Double?)

data class Nuclide(
    val Z: Int,
    val N: Int,
    val A: Int,
    val symbol: String,
    val stable: Boolean,
    /** Valeur telle que dans les données (« 4.468E9 », « 12.32 »), sans unité. */
    val halfLifeValue: String?,
    /** Code d'unité des données : ns, μs, ms, s, min, h, d, a. */
    val halfLifeUnit: String?,
    /** « > », « < », « ≥ » ou « ~ » quand la demi-vie n'est qu'une borne ou une estimation. */
    val halfLifeOperator: String?,
    /** Modes dans l'ordre des données (IAEA), le principal en premier : α, β-, β+/EC, SF, p… */
    val decayModes: List<DecayMode>,
    /** Spin/parité normalisé (« 1/2+ »), null si inconnu. */
    val spin: String?,
    val bindingEnergyPerNucleon: Double
) {
    val notation get() = "${superscript(A)}$symbol"

    /** Couleur de la carte : celle du mode principal, comme sur les cartes de nucléides usuelles. */
    val decayType: DecayType get() {
        if (stable) return DecayType.STABLE
        val main = decayModes.firstOrNull()?.mode ?: return DecayType.OTHER
        return when {
            main.startsWith("α") -> DecayType.ALPHA
            // 2β- (double bêta moins) et β-n, β-α… (émission retardée) restent de la famille β-.
            main.startsWith("β-") || main.startsWith("2β-") -> DecayType.BETA_MINUS
            main.startsWith("β+") || main.startsWith("2β+") || main.startsWith("EC") || main.startsWith("2EC") ->
                DecayType.BETA_PLUS
            main.startsWith("SF") -> DecayType.FISSION
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
