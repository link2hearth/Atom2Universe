package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R

/** Pantry ingredients, never a recurring animal production. Whole-animal value equals resale. */
enum class FarmMeat(val label: Int, val kind: LivestockKind, val portions: Int, val sale: Int) {
    POULTRY(R.string.farm_meat_poultry, LivestockKind.CHICKENS, 2, 24),
    MUTTON(R.string.farm_meat_mutton, LivestockKind.SHEEP, 4, 600),
    PORK(R.string.farm_meat_pork, LivestockKind.PIGS, 5, 1_600),
    BEEF(R.string.farm_meat_beef, LivestockKind.CATTLE, 8, 3_000);

    companion object {
        fun forKind(kind: LivestockKind) = entries.first { it.kind == kind }
    }
}
