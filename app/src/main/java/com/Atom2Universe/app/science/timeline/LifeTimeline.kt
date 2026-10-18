package com.Atom2Universe.app.science.timeline

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.parentes.LifeTree
import java.text.NumberFormat

/** Published uncertainty ranges, not branch lengths, first fossil occurrences or trait ages. */
data class LifeDate(
    val id: String, val nodeId: String, val scientific: String,
    val olderMa: Double, val youngerMa: Double,
    @param:StringRes val title: Int, val art: CosmicArt.Kind,
    val source: CosmicSource, @param:StringRes val note: Int,
    val comparison: Pair<String, String>? = null
) {
    val periodId get() = "life:$id"
    val start get() = CosmicTimeline.PRESENT - olderMa * 1e6
    val end get() = CosmicTimeline.PRESENT - youngerMa * 1e6
    fun dateLabel(context: Context): String {
        val number = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
            maximumFractionDigits = 1; isGroupingUsed = false
        }
        return context.getString(R.string.ct_life_dates, number.format(olderMa), number.format(youngerMa))
    }
}

object LifeTimeline {
    // dos Reis et al. (2015), Table 1, Composite columns (not fossil calibration bounds).
    // Other records retain the ranges described by their own source. No date is inferred from OTT topology.
    val all = listOf(
        LifeDate("luca", "ott93302", "cellular organisms", 4520.0, 3940.0,
            R.string.ct_life_luca, CosmicArt.Kind.MICROBIAL, CosmicSource.LIFE_LUCA, R.string.ct_life_luca_note),
        LifeDate("eukaryotes", "ott304358", "Eukaryota", 1800.0, 1670.0,
            R.string.pt_group_eukaryotes, CosmicArt.Kind.MICROBIAL, CosmicSource.LIFE_EUKARYOTES, R.string.ct_life_eukaryotes_note),
        LifeDate("animals", "ott691846", "Metazoa", 833.5, 649.8,
            R.string.pt_group_animals, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("bilaterians", "ott117569", "Bilateria", 688.3, 595.7,
            R.string.pt_group_bilaterians, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("cnidarians", "ott641033", "Cnidaria", 641.8, 531.5,
            R.string.pt_group_cnidarians, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("chordates", "ott125642", "Chordata", 635.7, 555.4,
            R.string.ct_life_chordates, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("vertebrates", "ott801601", "Vertebrata", 533.8, 459.3,
            R.string.ct_life_vertebrates, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("jawed_vertebrates", "ott278114", "Gnathostomata", 468.1, 432.1,
            R.string.ct_life_jawed, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("tetrapods", "ott229562", "Tetrapoda", 354.0, 338.2,
            R.string.pt_group_tetrapods, CosmicArt.Kind.FERN, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("amniotes", "ott229560", "Amniota", 331.5, 318.0,
            R.string.pt_group_amniotes, CosmicArt.Kind.FERN, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("mammals", "ott244265", "Mammalia", 204.7, 164.8,
            R.string.pt_group_mammals, CosmicArt.Kind.CONIFERS, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("arthropods", "ott632179", "Arthropoda", 560.7, 530.8,
            R.string.pt_group_arthropods, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("molluscs", "ott802117", "Mollusca", 550.3, 538.3,
            R.string.pt_group_molluscs, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("gastropods", "ott409995", "Gastropoda", 512.6, 470.0,
            R.string.ct_life_gastropods, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("ecdysozoans", "ott611099", "Ecdysozoa", 628.9, 566.5,
            R.string.ct_life_ecdysozoans, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("protostomes", "ott189832", "Protostomia", 653.1, 578.1,
            R.string.ct_life_protostomes, CosmicArt.Kind.MARINE, CosmicSource.LIFE_ANIMALS, R.string.ct_life_animals_note),
        LifeDate("land_plants", "ott56610", "Embryophyta", 515.1, 470.0,
            R.string.pt_group_land, CosmicArt.Kind.FERN, CosmicSource.LIFE_LAND_PLANTS, R.string.ct_life_plants_note),
        LifeDate("human_chimp", "mrcaott83926ott84217", "", 8.0, 6.0,
            R.string.ct_life_human_chimp, CosmicArt.Kind.GRASSLAND, CosmicSource.LIFE_HUMAN_CHIMP, R.string.ct_life_human_chimp_note,
            comparison = "ott770315" to "ott417950")
    )
    val sources get() = all.map { it.source }.distinct()
    fun get(id: String?) = all.find { it.id == id }

    val periods: List<CosmicPeriod> by lazy {
        all.map { date ->
            val parent = CosmicPeriods.all.filter { (it.geology != null || it.id == "earth") &&
                it.start <= date.start && it.end >= date.end }.minBy { it.end - it.start }
            CosmicPeriod(date.periodId, date.start, date.end, date.title, R.string.ct_life_dates,
                R.string.ct_life_description, date.art, emptyList(), parent.id,
                sources = listOf(date.source), datedAncestorId = date.id)
        }
    }

    /** Recheck the source-node identity before linking; unnamed Homo/Pan junctions use the actual LCA. */
    fun matches(tree: LifeTree, date: LifeDate): Boolean {
        val node = tree.nodes[date.nodeId] ?: return false
        val pair = date.comparison
        return if (pair != null) {
            pair.first in tree.nodes && pair.second in tree.nodes &&
                tree.lca(pair.first, pair.second) == node.id
        } else node.scientific == date.scientific && !node.species
    }
    fun forNode(tree: LifeTree, id: String): LifeDate? =
        all.find { it.nodeId == id && matches(tree, it) }

    fun nearestAncestor(tree: LifeTree, id: String): LifeDate? =
        tree.ancestors(id).drop(1).firstNotNullOfOrNull { forNode(tree, it) }

    fun inPeriod(period: CosmicPeriod): List<LifeDate> =
        if (period.datedAncestorId != null) emptyList()
        else all.filter { it.end > period.start && it.start < period.end }.sortedByDescending { it.olderMa }
}
