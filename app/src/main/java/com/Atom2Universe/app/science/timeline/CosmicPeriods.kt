package com.Atom2Universe.app.science.timeline

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

/** Cosmic reading chapters and formal geological divisions. Parallel histories deliberately overlap.
 * Event membership is explicit: belonging to a period is not inferred from an approximate date. */
data class CosmicPeriod(
    val id: String,
    val start: Double,
    val end: Double,
    @param:StringRes val title: Int,
    @param:StringRes val dates: Int,
    @param:StringRes val description: Int,
    val art: CosmicArt.Kind,
    val eventIds: List<String>,
    val parentId: String? = null,
    val geology: GeologicalRange? = null,
    val color: Int = art.color,
    val sources: List<CosmicSource> = emptyList(),
    val datedAncestorId: String? = null,
    val human: HumanRange? = null,
    val thematic: Boolean = false,
    val uncertainRange: Boolean = datedAncestorId != null
) {
    val window get() = CosmicWindow.bounded(start, end - start)
    val events get() = eventIds.mapNotNull(CosmicTimeline::event)
    fun intersects(window: CosmicWindow) = end > window.start && start < window.end
}

object CosmicPeriods {
    val all = listOf(
        CosmicPeriod("origins", 0.0, 1e9, R.string.ct_period_origins, R.string.ct_period_origins_dates,
            R.string.ct_period_origins_body, CosmicArt.Kind.ORIGIN,
            listOf("big_bang", "inflation", "particles", "nuclei", "first_light", "dark_ages",
                "first_stars", "first_galaxies", "elements", "milky_way", "reionization")),
        CosmicPeriod("galaxies", 1e9, CosmicTimeline.PRESENT, R.string.ct_period_galaxies, R.string.ct_period_galaxies_dates,
            R.string.ct_period_galaxies_body, CosmicArt.Kind.GALAXY,
            listOf("cosmic_web", "elements", "star_peak", "acceleration", "present")),
        CosmicPeriod("solar", 9.2e9, CosmicTimeline.PRESENT, R.string.ct_period_solar, R.string.ct_period_solar_dates,
            R.string.ct_period_solar_body, CosmicArt.Kind.SUN, listOf("sun", "earth", "moon")),
        CosmicPeriod("earth", 9.26e9, CosmicTimeline.PRESENT, R.string.ct_period_earth, R.string.ct_period_earth_dates,
            R.string.ct_period_earth_body, CosmicArt.Kind.STRATA, listOf("earth", "moon")),
        CosmicPeriod("primordial", 0.0, 380_000.0, R.string.ct_period_primordial, R.string.ct_period_primordial_dates,
            R.string.ct_period_primordial_body, CosmicArt.Kind.PARTICLES,
            listOf("big_bang", "inflation", "particles", "nuclei", "first_light"), "origins"),
        CosmicPeriod("dark", 380_000.0, 200e6, R.string.ct_dark_ages_title, R.string.ct_period_dark_dates,
            R.string.ct_period_dark_body, CosmicArt.Kind.DARK, listOf("dark_ages", "first_stars"), "origins"),
        CosmicPeriod("dawn", 200e6, 1e9, R.string.ct_chapter_dawn, R.string.ct_period_dawn_dates,
            R.string.ct_period_dawn_body, CosmicArt.Kind.PROTOGALAXY,
            listOf("first_stars", "first_galaxies", "elements", "milky_way", "reionization"), "origins")
    ) + EarthPeriods.all
    val roots get() = all.filter { it.parentId == null }
    fun period(id: String?) = all.find { it.id == id }
    fun children(period: CosmicPeriod) = all.filter { it.parentId == period.id }
    fun earthContext(window: CosmicWindow): CosmicPeriod? {
        val earth = period("earth") ?: return null
        val overlap = minOf(window.end, earth.end) - maxOf(window.start, earth.start)
        if (window.span > earth.window.span * 1.05 || overlap < window.span * .9) return null
        return all.filter { (it.geology != null || it.id == "earth") &&
            it.start <= window.start + .01 && it.end >= window.end - .01 }
            .minByOrNull { it.end - it.start } ?: earth
    }
    fun visible(window: CosmicWindow): List<CosmicPeriod> {
        if (earthContext(window) != null) {
            fun expand(period: CosmicPeriod): List<CosmicPeriod> {
                if (!period.intersects(window)) return emptyList()
                val subdivisions = children(period)
                return if (subdivisions.isNotEmpty() && window.span <= period.window.span * 1.05)
                    subdivisions.flatMap(::expand) else listOf(period)
            }
            return expand(requireNotNull(period("earth")))
        }
        val expandOrigins = window.span <= 1.5e9 && window.start < 1e9
        return roots.flatMap { if (expandOrigins && it.id == "origins") children(it) else listOf(it) }
            .filter { it.intersects(window) }
    }
}
