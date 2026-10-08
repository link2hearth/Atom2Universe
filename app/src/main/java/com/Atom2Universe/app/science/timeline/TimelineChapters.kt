package com.Atom2Universe.app.science.timeline

import android.content.Context
import com.Atom2Universe.app.R
import java.text.NumberFormat

/** Explicit navigation: changing the visible chapter never depends on a zoom threshold. */
object TimelineChapters {
    val universe = CosmicPeriod("universe", 0.0, CosmicTimeline.PRESENT,
        R.string.ct_chapter_universe, R.string.ct_big_bang_date, R.string.ct_universe_intro,
        CosmicArt.Kind.GALAXY, emptyList())
    val minutes = CosmicPeriod("minutes", 0.0, 1500.0 / CosmicTimeline.YEAR_SECONDS,
        R.string.ct_chapter_minutes, R.string.ct_minutes_dates, R.string.ct_minutes_intro,
        CosmicArt.Kind.ATOM, listOf("big_bang", "inflation", "particles", "nuclei"), "primordial")
    val all = listOf(universe) + CosmicPeriods.all + minutes + LifeTimeline.periods + HumanHistory.periods
    fun get(id: String?) = all.find { it.id == id }
    fun children(chapter: CosmicPeriod): List<CosmicPeriod> = when (chapter.id) {
        "universe" -> listOfNotNull(get("origins"), get("galaxies"))
        else -> all.filter { it.parentId == chapter.id && it.datedAncestorId == null && !it.thematic }
    }
    fun related(chapter: CosmicPeriod): List<CosmicPeriod> = when (chapter.id) {
        "universe", "galaxies" -> listOfNotNull(get("solar"), get("earth"))
        "solar" -> listOfNotNull(get("earth"))
        "earth", "quaternary" -> listOfNotNull(get("human"))
        else -> emptyList()
    }

    /** Adjacent periods remain reachable across era boundaries; parallel histories are not siblings. */
    fun neighbours(chapter: CosmicPeriod): Pair<CosmicPeriod?, CosmicPeriod?> {
        val rank = chapter.geology?.rank
        val sequence = when {
            chapter.datedAncestorId != null -> LifeTimeline.periods.sortedBy { it.start }
            chapter.human != null -> HumanHistory.periods.filter { it.human?.rank == chapter.human.rank }.sortedBy { it.start }
            rank != null -> all.filter { it.geology?.rank == rank }.sortedBy { it.start }
            else -> children(get(chapter.parentId) ?: universe).sortedBy { it.start }
        }
        val index = sequence.indexOfFirst { it.id == chapter.id }
        return if (index < 0) null to null else sequence.getOrNull(index - 1) to sequence.getOrNull(index + 1)
    }
    fun contains(parent: CosmicPeriod, child: CosmicPeriod) =
        child.start >= parent.start && child.end <= parent.end && child.end - child.start < parent.end - parent.start

    fun canonical(id: String): List<String> {
        val chapter = get(id) ?: return listOf(universe.id)
        return if (chapter == universe) listOf(universe.id)
        else canonical(chapter.parentId ?: universe.id) + chapter.id
    }

    /** Preserve the actual route, including Universe → Solar System → Earth. */
    fun navigate(path: List<String>, id: String): List<String> {
        val target = get(id) ?: return path
        val existing = path.indexOf(id)
        if (existing >= 0) return path.take(existing + 1)
        val index = path.indexOfLast { get(it)?.let { ancestor ->
            ancestor.datedAncestorId == null && contains(ancestor, target)
        } == true }
        if (index < 0) return canonical(id)
        val ancestor = requireNotNull(get(path[index]))
        val tail = canonical(id).filter { contains(ancestor, requireNotNull(get(it))) }
        return path.take(index + 1) + tail
    }

    fun restore(path: List<String>?): List<String>? {
        if (path.isNullOrEmpty() || path.first() != universe.id || path.distinct().size != path.size) return null
        val chapters = path.map { get(it) ?: return null }
        if (chapters.zipWithNext().any { (parent, child) -> !contains(parent, child) }) return null
        return path
    }

    fun chapterFor(event: CosmicEvent): CosmicPeriod = all
        .filter { event.id in it.eventIds }
        .minByOrNull { it.end - it.start } ?: universe
}

/** Geological dates retain their independent Ma values; do not subtract rounded cosmic ages. */
class TimelineDates(private val context: Context) {
    private val number = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        maximumFractionDigits = 3; isGroupingUsed = false
    }
    private fun amount(years: Double): String {
        val (value, unit) = when {
            years >= 1e9 -> years / 1e9 to R.string.ct_tick_gyr
            years >= 1e6 -> years / 1e6 to R.string.ct_tick_myr
            years >= 1e3 -> years / 1e3 to R.string.ct_tick_kyr
            years >= 1.0 -> years to R.string.ct_unit_year
            years * CosmicTimeline.YEAR_SECONDS >= 60 -> years * CosmicTimeline.YEAR_SECONDS / 60 to R.string.ct_unit_minute
            else -> years * CosmicTimeline.YEAR_SECONDS to R.string.ct_unit_second
        }
        return context.getString(unit, number.format(value))
    }
    fun edge(chapter: CosmicPeriod, start: Boolean): String {
        chapter.human?.let { return (if (start) it.first else it.last).label(context) }
        val age = if (start) chapter.start else chapter.end
        if (age == 0.0) return context.getString(R.string.ct_time_origin)
        if (age == CosmicTimeline.PRESENT) return context.getString(R.string.ct_geo_present)
        val geological = chapter.geology
        val lifeDate = LifeTimeline.get(chapter.datedAncestorId)
        val lookback = geological != null || lifeDate != null || chapter.id in listOf("solar", "earth")
        val years = if (geological != null) (if (start) geological.olderMa else geological.youngerMa) * 1e6
            else if (lifeDate != null) (if (start) lifeDate.olderMa else lifeDate.youngerMa) * 1e6
            else if (lookback) CosmicTimeline.PRESENT - age else age
        val value = context.getString(if (lookback) R.string.ct_time_before else R.string.ct_time_after, amount(years))
        return if (geological == null && chapter != TimelineChapters.minutes) context.getString(R.string.ct_time_approximate, value) else value
    }
}
