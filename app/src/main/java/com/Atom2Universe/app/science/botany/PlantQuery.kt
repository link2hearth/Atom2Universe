package com.Atom2Universe.app.science.botany

import java.text.CollationKey
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

enum class PlantSort { NAME, LATIN, FAMILY, HEIGHT, FLOWERING }

/** OR within a facet, AND between facets. Unobserved features stay unset, never treated as absent. */
data class PlantQuery(
    val text: String = "", val sections: Set<String> = emptySet(), val families: Set<String> = emptySet(),
    val leaves: Set<LeafShape> = emptySet(), val margins: Set<LeafMargin> = emptySet(),
    val arrangements: Set<LeafArrangement> = emptySet(), val colors: Set<FlowerColor> = emptySet(),
    val flowers: Set<FlowerShape> = emptySet(), val fruits: Set<FruitShape> = emptySet(),
    val habitats: Set<Habitat> = emptySet(), val months: Set<Int> = emptySet(), val evergreen: Boolean? = null,
    val collections: Set<String> = emptySet()
) {
    private val words by lazy { normalize(text).split(' ').filter { it.isNotBlank() } }

    /** [normalized]: [searchableText] already went through [normalize], as the screen's prepared index does. */
    fun matches(p: PlantSpecies, searchableText: String, normalized: Boolean = false): Boolean {
        fun <T> permits(selected: Set<T>, actual: T) = selected.isEmpty() || actual in selected
        fun <T> overlaps(selected: Set<T>, actual: Set<T>) = selected.isEmpty() || actual.any { it in selected }
        return overlaps(collections,p.collectionIds) && overlaps(sections,p.sectionIds) && permits(families, p.familyId) &&
            overlaps(leaves, p.leafShapes) && overlaps(margins, p.leafMargins) && overlaps(arrangements, p.leafArrangements) &&
            overlaps(colors, p.flowerColors) && permits(flowers, p.look.flower) && permits(fruits, p.look.fruit) &&
            (habitats.isEmpty() || p.habitats.any { it in habitats }) &&
            (months.isEmpty() || p.flowering.any { it in months }) && (evergreen == null || p.variablePersistence || evergreen == p.evergreen) &&
            (words.isEmpty() || (if (normalized) searchableText else normalize(searchableText)).let { s -> words.all { it in s } })
    }

    companion object {
        private val accents = Regex("\\p{M}+")
        private val separators = Regex("[^\\p{L}\\p{N}]+")

        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(accents, "").lowercase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae")
            .replace(separators, " ").trim()

        fun sorted(plants: List<PlantSpecies>, sort: PlantSort, locale: Locale, name: (PlantSpecies) -> String,
                   latin: (PlantSpecies) -> String, family: (PlantSpecies) -> String): List<PlantSpecies> {
            val collator = Collator.getInstance(locale)
            // Collation keys are computed once per plant instead of at each comparison.
            class Row(val plant: PlantSpecies, val name: CollationKey, val other: CollationKey?)
            val rows = plants.map { p ->
                Row(p, collator.getCollationKey(name(p)), when (sort) {
                    PlantSort.LATIN -> collator.getCollationKey(latin(p))
                    PlantSort.FAMILY -> collator.getCollationKey(family(p))
                    else -> null
                })
            }
            return rows.sortedWith(Comparator { a, b ->
                val primary = when (sort) {
                    PlantSort.NAME -> a.name.compareTo(b.name)
                    PlantSort.LATIN, PlantSort.FAMILY -> a.other!!.compareTo(b.other!!)
                    PlantSort.HEIGHT -> a.plant.heightMaxCm.compareTo(b.plant.heightMaxCm)
                    PlantSort.FLOWERING -> (a.plant.flowering.minOrNull() ?: 13).compareTo(b.plant.flowering.minOrNull() ?: 13)
                }
                if (primary != 0) primary else a.name.compareTo(b.name)
            }).map { it.plant }
        }
    }
}
