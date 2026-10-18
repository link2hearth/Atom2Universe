package com.Atom2Universe.app.science.timeline

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import java.text.NumberFormat

/** Astronomical years internally (0 = 1 BCE). The UI never displays a civil year zero.
 * 2026 is a fixed editorial endpoint, not a live feed or a claim of exhaustive coverage. */
data class HumanDate(val year: Int, val approximate: Boolean = false, val yearsAgo: Int? = null) {
    val position get() = CosmicTimeline.PRESENT - (HumanHistory.END_YEAR.toDouble() - year)
    fun label(context: Context): String {
        val number = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
            isGroupingUsed = false; maximumFractionDigits = 3
        }
        val value = when {
            yearsAgo != null -> context.getString(R.string.ct_time_before,
                context.getString(R.string.ct_tick_kyr, number.format(yearsAgo / 1000.0)))
            year <= 0 -> context.getString(R.string.ct_history_bce, number.format(1 - year))
            else -> context.getString(R.string.ct_history_ce, number.format(year))
        }
        return if (approximate) context.getString(R.string.ct_time_approximate, value) else value
    }
}

data class HumanRange(val first: HumanDate, val last: HumanDate, val rank: Int = 0) {
    init { require(first.year <= last.year) }
    fun label(context: Context) = if (first == last) first.label(context)
        else context.getString(R.string.ct_range, first.label(context), last.label(context))
}

enum class HumanRegion(@param:StringRes val label: Int, val color: Int) {
    AFRICA(R.string.ct_history_africa, 0xFFE2B789.toInt()),
    ASIA(R.string.ct_history_asia, 0xFF98CEC1.toInt()),
    EUROPE(R.string.ct_history_europe, 0xFF9DBFDE.toInt()),
    AMERICAS(R.string.ct_history_americas, 0xFFB7C98C.toInt()),
    OCEANIA(R.string.ct_history_oceania, 0xFF97CDD8.toInt()),
    WORLD(R.string.ct_history_world, 0xFFC3AEDB.toInt())
}

data class HumanLandmark(
    val id: String, val range: HumanRange, val regions: Set<HumanRegion>,
    @param:StringRes val title: Int, @param:StringRes val body: Int,
    @param:StringRes val precision: Int, val source: HumanSource,
    val uncertainRange: Boolean = false,
    @param:StringRes val exactDate: Int? = null,
    val science: ScienceField? = null,
    val additionalSources: List<HumanSource> = emptyList()
) {
    /** Keep the axis in years while documenting finer dates in cards and accessibility labels. */
    fun dateLabel(context: Context) = exactDate?.let { context.getString(it) } ?: range.label(context)

    fun indexedText(context: Context) = listOfNotNull(
        context.getString(title), context.getString(body), context.getString(precision),
        dateLabel(context), science?.let { context.getString(it.label) },
        regions.joinToString(" ") { context.getString(it.label) }
    ).joinToString(" ")

    fun hasSource(reference: HumanSource) = source == reference || reference in additionalSources

    fun overlaps(period: CosmicPeriod): Boolean {
        val bounds = period.human ?: return false
        return if (range.first.year == range.last.year)
            range.first.year >= bounds.first.year && range.first.year < bounds.last.year
        else range.last.year > bounds.first.year && range.first.year < bounds.last.year
    }
    /** Rendering proxy only: its duration can cross a reading-window boundary. */
    fun band(parent: CosmicPeriod) = CosmicPeriod("history:$id", range.first.position, range.last.position,
        title, R.string.ct_range, body, parent.art, emptyList(), color = regions.first().color, human = range,
        uncertainRange = uncertainRange)
}

object HumanHistory {
    const val END_YEAR = 2026
    fun bce(year: Int, approximate: Boolean = false) = HumanDate(1 - year, approximate)
    fun ce(year: Int, approximate: Boolean = false) = HumanDate(year, approximate)
    fun ago(years: Int) = HumanDate(END_YEAR - years, true, years)
    private fun chapter(id: String, parent: String, start: HumanDate, end: HumanDate, rank: Int,
        @StringRes title: Int, @StringRes body: Int, color: Int) =
        CosmicPeriod(id, start.position, end.position, title, R.string.ct_range, body,
            CosmicArt.Kind.STRATA, emptyList(), parent, color = color,
            human = HumanRange(start, end, rank), thematic = id == "human")

    val periods = listOf(
        chapter("human", "earth", ago(300_000), ce(END_YEAR), 0,
            R.string.ct_history_human_title, R.string.ct_history_human_body, 0xFFD2B389.toInt()),
        chapter("human_foragers", "human", ago(300_000), bce(10_000), 1,
            R.string.ct_history_foragers_title, R.string.ct_history_foragers_body, 0xFFC5AF92.toInt()),
        chapter("human_villages", "human", bce(10_000), bce(3000), 1,
            R.string.ct_history_villages_title, R.string.ct_history_villages_body, 0xFFB8C68E.toInt()),
        chapter("human_recent", "human", bce(3000), ce(END_YEAR), 1,
            R.string.ct_history_recent_title, R.string.ct_history_recent_body, 0xFF99C8C2.toInt()),
        chapter("human_cities", "human_recent", bce(3000), bce(1000), 2,
            R.string.ct_history_cities_title, R.string.ct_history_cities_body, 0xFFD5BB89.toInt()),
        chapter("human_exchanges", "human_recent", bce(1000), ce(1500), 2,
            R.string.ct_history_exchanges_title, R.string.ct_history_exchanges_body, 0xFF9ACABD.toInt()),
        chapter("human_modern", "human_recent", ce(1500), ce(END_YEAR), 2,
            R.string.ct_history_modern_title, R.string.ct_history_modern_body, 0xFFA8BFDA.toInt()),
        chapter("human_ancient", "human_exchanges", bce(1000), ce(500), 3,
            R.string.ct_history_ancient_title, R.string.ct_history_ancient_body, 0xFFAACB9F.toInt()),
        chapter("human_regional", "human_exchanges", ce(500), ce(1500), 3,
            R.string.ct_history_regional_title, R.string.ct_history_regional_body, 0xFF92C6C5.toInt()),
        chapter("human_oceans", "human_modern", ce(1500), ce(1800), 3,
            R.string.ct_history_oceans_title, R.string.ct_history_oceans_body, 0xFF91BFCB.toInt()),
        chapter("human_industry", "human_modern", ce(1800), ce(1914), 3,
            R.string.ct_history_industry_title, R.string.ct_history_industry_body, 0xFFC1B39E.toInt()),
        chapter("human_wars", "human_modern", ce(1914), ce(1945), 3,
            R.string.ct_history_wars_title, R.string.ct_history_wars_body, 0xFFC49C98.toInt()),
        chapter("human_postwar", "human_modern", ce(1945), ce(1991), 3,
            R.string.ct_history_postwar_title, R.string.ct_history_postwar_body, 0xFFAFBBCC.toInt()),
        chapter("human_connected", "human_modern", ce(1991), ce(END_YEAR), 3,
            R.string.ct_history_connected_title, R.string.ct_history_connected_body, 0xFFABBCD8.toInt())
    )

    val entries get() = HumanLandmarks.all
    fun inPeriod(period: CosmicPeriod, region: HumanRegion? = null) = entries.filter {
        it.overlaps(period) && (region == null || region in it.regions)
    }
    fun chapterFor(entry: HumanLandmark): CosmicPeriod = periods.filter {
        val range = requireNotNull(it.human)
        entry.overlaps(it) && range.first.year <= entry.range.first.year && range.last.year >= entry.range.last.year
    }.minBy { it.end - it.start }
}
