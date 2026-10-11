package com.Atom2Universe.app.science.botany

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
    val habitats: Set<Habitat> = emptySet(), val months: Set<Int> = emptySet(), val evergreen: Boolean? = null
) {
    fun matches(p: PlantSpecies, searchableText: String): Boolean {
        fun <T> permits(selected: Set<T>, actual: T) = selected.isEmpty() || actual in selected
        fun <T> overlaps(selected: Set<T>, actual: Set<T>) = selected.isEmpty() || actual.any { it in selected }
        return (sections.isEmpty() || p.sectionIds.any { it in sections }) && permits(families, p.familyId) &&
            overlaps(leaves, p.leafShapes) && overlaps(margins, p.leafMargins) && overlaps(arrangements, p.leafArrangements) &&
            overlaps(colors, p.flowerColors) && permits(flowers, p.look.flower) && permits(fruits, p.look.fruit) &&
            (habitats.isEmpty() || p.habitats.any { it in habitats }) &&
            (months.isEmpty() || p.flowering.any { it in months }) && (evergreen == null || p.variablePersistence || evergreen == p.evergreen) &&
            normalize(text).split(' ').filter { it.isNotBlank() }.all { it in normalize(searchableText) }
    }

    companion object {
        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        fun sorted(plants: List<PlantSpecies>, sort: PlantSort, locale: Locale, name: (PlantSpecies) -> String,
                   latin: (PlantSpecies) -> String, family: (PlantSpecies) -> String): List<PlantSpecies> {
            val collator = Collator.getInstance(locale)
            return plants.sortedWith(Comparator { a, b ->
                val primary = when (sort) {
                    PlantSort.NAME -> collator.compare(name(a), name(b))
                    PlantSort.LATIN -> collator.compare(latin(a), latin(b))
                    PlantSort.FAMILY -> collator.compare(family(a), family(b))
                    PlantSort.HEIGHT -> a.heightMaxCm.compareTo(b.heightMaxCm)
                    PlantSort.FLOWERING -> (a.flowering.minOrNull() ?: 13).compareTo(b.flowering.minOrNull() ?: 13)
                }
                if (primary != 0) primary else collator.compare(name(a), name(b))
            })
        }
    }
}
