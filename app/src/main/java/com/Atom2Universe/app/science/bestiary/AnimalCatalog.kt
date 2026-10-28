package com.Atom2Universe.app.science.bestiary

import com.Atom2Universe.app.science.parentes.LifeTree
import java.text.Collator
import java.util.Locale

/** Identity of a published profile; its text lives in the localized encyclopedia assets. */
data class AnimalSpecies(val id: String, val scientific: String, val group: String)

/** A row of the bestiary list: localized name and normalized search text. */
internal class BestiaryEntry(val animal: AnimalSpecies, val name: String, val searchKey: String)

/** Stable species identities; content deliberately avoids dated population and status estimates. */
object AnimalCatalog {
    val species: List<AnimalSpecies> = BestiaryEncyclopediaCatalog.species
    val byId = species.associateBy { it.id }

    fun name(animal: AnimalSpecies, language: String): String =
        BestiaryEncyclopediaCatalog.name(animal.id, language) ?: animal.scientific

    @Volatile private var sorted: Pair<Locale, List<BestiaryEntry>>? = null

    /** Sorted once per locale; call off the UI thread. */
    internal fun entries(locale: Locale): List<BestiaryEntry> {
        sorted?.takeIf { it.first == locale }?.let { return it.second }
        val collator = Collator.getInstance(locale)
        val result = species.map { animal ->
            val name = name(animal, locale.language)
            BestiaryEntry(animal, name, LifeTree.normalize(name + " " + animal.scientific)) to collator.getCollationKey(name)
        }.sortedBy { it.second }.map { it.first }
        sorted = locale to result
        return result
    }
}
