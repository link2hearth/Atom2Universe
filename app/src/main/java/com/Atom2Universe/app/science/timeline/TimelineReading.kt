package com.Atom2Universe.app.science.timeline

import com.Atom2Universe.app.R

/** Main chapters use a short introduction and optional, complementary reading. */
object TimelineReading {
    fun sources(id: String): List<CosmicSource> = when (id) {
        "universe" -> listOf(CosmicSource.HISTORY)
        "solar" -> listOf(CosmicSource.SOLAR)
        "earth" -> listOf(CosmicSource.EARTH_FACTS, CosmicSource.PRECAMBRIAN, CosmicSource.PROTEROZOIC)
        "phanerozoic" -> listOf(CosmicSource.FOSSILS)
        "mesozoic" -> listOf(CosmicSource.MESOZOIC, CosmicSource.CRETACEOUS, CosmicSource.JURASSIC)
        "jurassic" -> listOf(CosmicSource.JURASSIC, CosmicSource.GEOLOGICAL_SCALE)
        else -> emptyList()
    }
    fun intro(chapter: CosmicPeriod): Int = when (chapter.id) {
        "earth" -> R.string.ct_story_earth_intro
        "phanerozoic" -> R.string.ct_story_phanerozoic_intro
        "mesozoic" -> R.string.ct_story_mesozoic_intro
        "jurassic" -> R.string.ct_story_jurassic_intro
        "universe" -> R.string.ct_universe_intro
        "solar" -> R.string.ct_story_solar_intro
        else -> chapter.description
    }
    fun sections(id: String): List<Pair<Int, Int>> = when (id) {
        "earth" -> listOf(
            R.string.ct_story_earth_0_title to R.string.ct_story_earth_0_body,
            R.string.ct_story_earth_1_title to R.string.ct_story_earth_1_body
        )
        "phanerozoic" -> listOf(
            R.string.ct_story_phanerozoic_0_title to R.string.ct_story_phanerozoic_0_body,
            R.string.ct_story_phanerozoic_1_title to R.string.ct_story_phanerozoic_1_body
        )
        "mesozoic" -> listOf(
            R.string.ct_story_mesozoic_0_title to R.string.ct_story_mesozoic_0_body,
            R.string.ct_story_mesozoic_1_title to R.string.ct_story_mesozoic_1_body
        )
        "jurassic" -> listOf(
            R.string.ct_story_jurassic_0_title to R.string.ct_story_jurassic_0_body,
            R.string.ct_story_jurassic_1_title to R.string.ct_story_jurassic_1_body,
            R.string.ct_story_jurassic_2_title to R.string.ct_story_jurassic_2_body
        )
        "universe" -> listOf(
            R.string.ct_story_universe_0_title to R.string.ct_story_universe_0_body,
            R.string.ct_story_universe_1_title to R.string.ct_story_universe_1_body
        )
        "solar" -> listOf(
            R.string.ct_story_solar_0_title to R.string.ct_story_solar_0_body,
            R.string.ct_story_solar_1_title to R.string.ct_story_solar_1_body
        )
        else -> emptyList()
    }
}
