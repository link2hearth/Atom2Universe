package com.Atom2Universe.app.science.timeline

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Cosmic age in years, with a local origin at the Big Bang (not a Unix/calendar date).
 * Doubles are kept until projection to pixels. Display dates are independently sourced:
 * subtracting two approximate ages must not manufacture a precise date for an event. */
data class CosmicEvent(
    val id: String,
    val age: Double,
    @StringRes val title: Int,
    @StringRes val date: Int,
    @StringRes val summary: Int,
    @StringRes val detail: Int,
    @StringRes val precision: Int,
    val art: CosmicArt.Kind,
    val sources: List<CosmicSource>,
    val priority: Int = 1,
    val endAge: Double? = null
)

enum class CosmicSource(@StringRes val label: Int, val url: String) {
    HISTORY(R.string.ct_source_history, "https://science.nasa.gov/universe/overview/"),
    PARTICLES(R.string.ct_source_particles, "https://home.web.cern.ch/science/physics/early-universe/"),
    STRUCTURE(R.string.ct_source_structure, "https://www.esa.int/Science_Exploration/Space_Science/Planck/History_of_cosmic_structure_formation"),
    DAWN(R.string.ct_source_dawn, "https://science.nasa.gov/mission/webb/early-universe/"),
    REIONIZATION(R.string.ct_source_reionization, "https://science.nasa.gov/missions/webb/nasas-webb-proves-galaxies-transformed-the-early-universe/"),
    STARS(R.string.ct_source_stars, "https://science.nasa.gov/mission/webb/star-lifecycle/"),
    EXPLOSIONS(R.string.ct_source_explosions, "https://science.nasa.gov/mission/hubble/science/science-behind-the-discoveries/hubble-stellar-explosions/"),
    MILKY_WAY(R.string.ct_source_milky_way, "https://www.esa.int/Science_Exploration/Space_Science/Gaia/Gaia_finds_parts_of_the_Milky_Way_much_older_than_expected"),
    WEB(R.string.ct_source_web, "https://science.nasa.gov/mission/hubble/science/science-highlights/mapping-the-cosmic-web/"),
    PEAK(R.string.ct_source_peak, "https://science.nasa.gov/missions/hubble/hubble-breaks-record-in-search-for-farthest-supernova/"),
    EXPANSION(R.string.ct_source_expansion, "https://science.nasa.gov/mission/hubble/science/science-behind-the-discoveries/hubble-cosmological-redshift/"),
    SOLAR(R.string.ct_source_solar, "https://science.nasa.gov/solar-system/solar-system-facts/"),
    EARTH(R.string.ct_source_earth, "https://www.usgs.gov/observatories/yvo/news/when-earths-history-did-yellowstones-volcanism-begin-lets-look-calendar-find"),
    EARTH_FACTS(R.string.ct_source_earth_facts, "https://science.nasa.gov/earth/facts/"),
    MOON(R.string.ct_source_moon, "https://science.nasa.gov/moon/formation/"),
    GEOLOGICAL_SCALE(R.string.ct_source_geological_scale, "https://stratigraphy.org/ICSchart/ChronostratChart2026-06.pdf"),
    EARLY_WATER(R.string.ct_source_early_water, "https://science.nasa.gov/earth/earth-observatory/ancient-crystals-suggest-earlier-ocean/"),
    PRECAMBRIAN(R.string.ct_source_precambrian, "https://www.nps.gov/articles/000/the-precambrian.htm"),
    PROTEROZOIC(R.string.ct_source_proterozoic, "https://www.nps.gov/articles/000/proterozoic-eon.htm"),
    FOSSILS(R.string.ct_source_fossils, "https://www.nps.gov/subjects/fossils/fossils-through-geologic-time.htm"),
    FOSSILIZATION(R.string.ct_read_source_fossilization, "https://home.nps.gov/subjects/fossils/how-fossils-form.htm"),
    ROCK_DATING(R.string.ct_read_source_dating, "https://www.usgs.gov/observatories/yvo/news/a-beginners-guide-dating-rocks"),
    PALEOZOIC(R.string.ct_source_paleozoic, "https://www.nps.gov/articles/000/paleozoic-era.htm"),
    MESOZOIC(R.string.ct_source_mesozoic, "https://www.nps.gov/articles/000/mesozoic-era.htm"),
    CENOZOIC(R.string.ct_source_cenozoic, "https://www.nps.gov/articles/000/cenozoic-era.htm"),
    CAMBRIAN(R.string.ct_source_cambrian, "https://www.nps.gov/articles/000/cambrian-period.htm"),
    ORDOVICIAN(R.string.ct_source_ordovician, "https://www.nps.gov/articles/000/ordovician-period.htm"),
    SILURIAN(R.string.ct_source_silurian, "https://www.nps.gov/articles/000/silurian-period.htm"),
    DEVONIAN(R.string.ct_source_devonian, "https://www.nps.gov/articles/000/devonian-period.htm"),
    CARBONIFEROUS(R.string.ct_source_carboniferous, "https://www.nps.gov/articles/000/pennsylvanian-period.htm"),
    PERMIAN(R.string.ct_source_permian, "https://www.nps.gov/articles/000/permian-period.htm"),
    TRIASSIC(R.string.ct_source_triassic, "https://www.nps.gov/articles/000/triassic-period.htm"),
    JURASSIC(R.string.ct_source_jurassic, "https://www.nps.gov/articles/000/jurassic-period.htm"),
    CRETACEOUS(R.string.ct_source_cretaceous, "https://www.nps.gov/articles/000/cretaceous-period.htm"),
    PALEOGENE(R.string.ct_source_paleogene, "https://www.nps.gov/articles/000/paleogene-period.htm"),
    NEOGENE(R.string.ct_source_neogene, "https://www.nps.gov/articles/000/neogene-period.htm"),
    QUATERNARY(R.string.ct_source_quaternary, "https://www.nps.gov/articles/000/quaternary-period.htm"),
    LIFE_LUCA(R.string.ct_source_life_luca, "https://www.nature.com/articles/s41559-024-02461-1"),
    LIFE_EUKARYOTES(R.string.ct_source_life_eukaryotes, "https://www.nature.com/articles/s41586-025-09808-z"),
    LIFE_ANIMALS(R.string.ct_source_life_animals, "https://doi.org/10.1016/j.cub.2015.09.066"),
    LIFE_LAND_PLANTS(R.string.ct_source_life_plants, "https://doi.org/10.1073/pnas.1719588115"),
    LIFE_HUMAN_CHIMP(R.string.ct_source_life_human_chimp, "https://humanorigins.si.edu/evidence/genetics")
}

object CosmicTimeline {
    const val PRESENT = 13_800_000_000.0
    const val YEAR_SECONDS = 31_557_600.0
    val events = listOf(
        CosmicEvent("big_bang", 0.0, R.string.ct_big_bang_title, R.string.ct_big_bang_date,
            R.string.ct_big_bang_summary, R.string.ct_big_bang_detail, R.string.ct_big_bang_precision,
            CosmicArt.Kind.ORIGIN, listOf(CosmicSource.HISTORY), 10),
        // Inflation has no measured timestamp: zero is a grouping anchor, not a dated point.
        CosmicEvent("inflation", 0.0, R.string.ct_inflation_title, R.string.ct_inflation_date,
            R.string.ct_inflation_summary, R.string.ct_inflation_detail, R.string.ct_inflation_precision,
            CosmicArt.Kind.EXPANSION, listOf(CosmicSource.HISTORY)),
        CosmicEvent("particles", 0.000003 / YEAR_SECONDS, R.string.ct_particles_title, R.string.ct_particles_date,
            R.string.ct_particles_summary, R.string.ct_particles_detail, R.string.ct_particles_precision,
            CosmicArt.Kind.PARTICLES, listOf(CosmicSource.PARTICLES)),
        CosmicEvent("nuclei", 180.0 / YEAR_SECONDS, R.string.ct_nuclei_title, R.string.ct_nuclei_date,
            R.string.ct_nuclei_summary, R.string.ct_nuclei_detail, R.string.ct_nuclei_precision,
            CosmicArt.Kind.ATOM, listOf(CosmicSource.PARTICLES), endAge = 1200.0 / YEAR_SECONDS),
        CosmicEvent("first_light", 380_000.0, R.string.ct_first_light_title, R.string.ct_first_light_date,
            R.string.ct_first_light_summary, R.string.ct_first_light_detail, R.string.ct_first_light_precision,
            CosmicArt.Kind.LIGHT, listOf(CosmicSource.STRUCTURE), 7),
        CosmicEvent("dark_ages", 380_000.0, R.string.ct_dark_ages_title, R.string.ct_dark_ages_date,
            R.string.ct_dark_ages_summary, R.string.ct_dark_ages_detail, R.string.ct_dark_ages_precision,
            CosmicArt.Kind.DARK, listOf(CosmicSource.STRUCTURE), endAge = 200_000_000.0),
        CosmicEvent("first_stars", 200_000_000.0, R.string.ct_first_stars_title, R.string.ct_first_stars_date,
            R.string.ct_first_stars_summary, R.string.ct_first_stars_detail, R.string.ct_first_stars_precision,
            CosmicArt.Kind.STARS, listOf(CosmicSource.DAWN), 8),
        CosmicEvent("first_galaxies", 300_000_000.0, R.string.ct_first_galaxies_title, R.string.ct_first_galaxies_date,
            R.string.ct_first_galaxies_summary, R.string.ct_first_galaxies_detail, R.string.ct_first_galaxies_precision,
            CosmicArt.Kind.PROTOGALAXY, listOf(CosmicSource.DAWN), 6),
        CosmicEvent("elements", 300_000_000.0, R.string.ct_elements_title, R.string.ct_elements_date,
            R.string.ct_elements_summary, R.string.ct_elements_detail, R.string.ct_elements_precision,
            CosmicArt.Kind.SUPERNOVA, listOf(CosmicSource.STARS, CosmicSource.EXPLOSIONS), endAge = PRESENT),
        CosmicEvent("milky_way", 800_000_000.0, R.string.ct_milky_way_title, R.string.ct_milky_way_date,
            R.string.ct_milky_way_summary, R.string.ct_milky_way_detail, R.string.ct_milky_way_precision,
            CosmicArt.Kind.PROTOGALAXY, listOf(CosmicSource.MILKY_WAY), 5),
        CosmicEvent("reionization", 1_000_000_000.0, R.string.ct_reionization_title, R.string.ct_reionization_date,
            R.string.ct_reionization_summary, R.string.ct_reionization_detail, R.string.ct_reionization_precision,
            CosmicArt.Kind.LIGHT, listOf(CosmicSource.REIONIZATION), 4),
        // A thematic anchor: the web develops over time, not at a single universal instant.
        CosmicEvent("cosmic_web", 1_000_000_000.0, R.string.ct_cosmic_web_title, R.string.ct_cosmic_web_date,
            R.string.ct_cosmic_web_summary, R.string.ct_cosmic_web_detail, R.string.ct_cosmic_web_precision,
            CosmicArt.Kind.WEB, listOf(CosmicSource.WEB), endAge = PRESENT),
        CosmicEvent("star_peak", 3_800_000_000.0, R.string.ct_star_peak_title, R.string.ct_star_peak_date,
            R.string.ct_star_peak_summary, R.string.ct_star_peak_detail, R.string.ct_star_peak_precision,
            CosmicArt.Kind.STARS, listOf(CosmicSource.PEAK), 8),
        CosmicEvent("acceleration", 8_800_000_000.0, R.string.ct_acceleration_title, R.string.ct_acceleration_date,
            R.string.ct_acceleration_summary, R.string.ct_acceleration_detail, R.string.ct_acceleration_precision,
            CosmicArt.Kind.EXPANSION, listOf(CosmicSource.EXPANSION), 6),
        CosmicEvent("sun", 9_200_000_000.0, R.string.ct_sun_title, R.string.ct_sun_date,
            R.string.ct_sun_summary, R.string.ct_sun_detail, R.string.ct_sun_precision,
            CosmicArt.Kind.SUN, listOf(CosmicSource.SOLAR), 9),
        CosmicEvent("earth", 9_260_000_000.0, R.string.ct_earth_title, R.string.ct_earth_date,
            R.string.ct_earth_summary, R.string.ct_earth_detail, R.string.ct_earth_precision,
            CosmicArt.Kind.EARTH, listOf(CosmicSource.EARTH), 8),
        CosmicEvent("moon", 9_300_000_000.0, R.string.ct_moon_title, R.string.ct_moon_date,
            R.string.ct_moon_summary, R.string.ct_moon_detail, R.string.ct_moon_precision,
            CosmicArt.Kind.MOON, listOf(CosmicSource.MOON), 5),
        CosmicEvent("present", PRESENT, R.string.ct_present_title, R.string.ct_present_date,
            R.string.ct_present_summary, R.string.ct_present_detail, R.string.ct_present_precision,
            CosmicArt.Kind.WEB, listOf(CosmicSource.HISTORY), 10)
    )
    fun event(id: String?) = events.find { it.id == id }
}

/** Camera bounds independent of Android; zoom is anchored under the fingers. */
data class CosmicWindow(val start: Double = 0.0, val span: Double = CosmicTimeline.PRESENT) {
    val end: Double get() = start + span
    fun fraction(age: Double): Double = (age - start) / span
    fun zoom(factor: Double, anchor: Double = 0.5): CosmicWindow {
        if (!factor.isFinite() || factor <= 0.0) return this
        val newSpan = (span / factor).coerceIn(MIN_SPAN, CosmicTimeline.PRESENT)
        val f = anchor.coerceIn(0.0, 1.0)
        return bounded(start + f * (span - newSpan), newSpan)
    }
    fun pan(fraction: Double) = bounded(start + fraction * span, span)
    fun ticks(target: Int): List<Double> {
        val raw = span / target.coerceAtLeast(2)
        val unit = 10.0.pow(floor(log10(raw)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * unit >= raw } * unit
        val first = kotlin.math.ceil(start / step) * step
        return (0..target + 1).map { first + it * step }.filter { it <= end + step * 1e-8 }
    }
    companion object {
        const val MIN_SPAN = 1.0 / CosmicTimeline.YEAR_SECONDS
        fun bounded(start: Double, span: Double): CosmicWindow {
            // Keep enough significant bits for panning and projection near the present.
            val minimum = maxOf(MIN_SPAN, Math.ulp(if (start.isFinite()) start.coerceAtLeast(0.0) else 0.0) * 64)
                .coerceAtMost(CosmicTimeline.PRESENT)
            val safeSpan = if (span.isFinite()) span.coerceIn(minimum, CosmicTimeline.PRESENT) else CosmicTimeline.PRESENT
            val safeStart = if (start.isFinite()) start.coerceIn(0.0, CosmicTimeline.PRESENT - safeSpan) else 0.0
            return CosmicWindow(safeStart, safeSpan)
        }
    }
}

enum class CosmicChapter(@StringRes val title: Int, val window: CosmicWindow) {
    UNIVERSE(R.string.ct_chapter_universe, CosmicWindow()),
    MINUTES(R.string.ct_chapter_minutes, CosmicWindow(0.0, 1500.0 / CosmicTimeline.YEAR_SECONDS)),
    ATOMS(R.string.ct_chapter_atoms, CosmicWindow(0.0, 1_000_000.0)),
    DAWN(R.string.ct_chapter_dawn, CosmicWindow(0.0, 1_300_000_000.0)),
    SOLAR(R.string.ct_chapter_solar, CosmicWindow(9_100_000_000.0, 300_000_000.0))
}
