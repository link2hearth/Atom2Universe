package com.Atom2Universe.app.periodic

/** Educational badges, not a GHS classification or a claim that unmarked elements are safe.
 * Toxicity is form-, dose- and exposure-dependent (RSC element profiles).
 * Radioactivity criterion: no stable isotope; not merely having a radioactive isotope,
 * since that would flag every element. Bi-209 decays extremely slowly.
 * Sources: https://periodic-table.rsc.org/element/61/promethium
 * https://periodic-table.rsc.org/element/83/bismuth
 * https://periodic-table.rsc.org/element/33/arsenic (and corresponding element profiles).
 */
internal object ElementCardHazards {
    private val toxicElements = setOf(4, 9, 17, 33, 34, 35, 48, 51, 52, 80, 81, 82, 92)

    fun isToxic(atomicNumber: Int): Boolean = atomicNumber in toxicElements
    fun hasNoStableIsotope(atomicNumber: Int): Boolean =
        atomicNumber == 43 || atomicNumber == 61 || atomicNumber in 83..118
}
