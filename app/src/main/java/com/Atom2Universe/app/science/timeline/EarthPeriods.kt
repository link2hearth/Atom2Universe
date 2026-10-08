package com.Atom2Universe.app.science.timeline

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import java.text.NumberFormat

enum class GeologicalRank(@param:StringRes val label: Int) {
    EON(R.string.ct_geo_eon), ERA(R.string.ct_geo_era), PERIOD(R.string.ct_geo_period)
}

/** Ma before present, independent of the rounded cosmic origin.
 * ICS International Chronostratigraphic Chart, 2026/06:
 * https://stratigraphy.org/ICSchart/ChronostratChart2026-06.pdf
 * Uncertainties are copied only where the chart supplies them; null does not mean exact.
 * Hadean is clipped to Earth's estimated formation (4540 ± 50 Ma), not its ICS base (4567 Ma). */
data class GeologicalRange(
    val olderMa: Double,
    val youngerMa: Double,
    val olderUncertaintyMa: Double?,
    val youngerUncertaintyMa: Double?,
    val rank: GeologicalRank
) {
    private fun number(context: Context) = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        maximumFractionDigits = 3
    }
    fun dates(context: Context): String {
        val format = number(context)
        return if (youngerMa == 0.0) context.getString(R.string.ct_geo_dates_present, format.format(olderMa))
        else context.getString(R.string.ct_geo_dates, format.format(olderMa), format.format(youngerMa))
    }
    fun precision(context: Context): String {
        val format = number(context)
        fun boundary(ma: Double, uncertainty: Double?): String = when {
            ma == 0.0 -> context.getString(R.string.ct_geo_present)
            uncertainty != null -> context.getString(R.string.ct_geo_uncertainty, format.format(ma), format.format(uncertainty))
            else -> context.getString(R.string.ct_tick_myr, format.format(ma))
        }
        return context.getString(R.string.ct_geo_boundaries,
            boundary(olderMa, olderUncertaintyMa), boundary(youngerMa, youngerUncertaintyMa))
    }
}

fun CosmicPeriod.dateLabel(context: Context): String = human?.label(context) ?: geology?.dates(context)
    ?: LifeTimeline.get(datedAncestorId)?.dateLabel(context) ?: context.getString(dates)

object EarthPeriods {
    private fun division(
        id: String, parent: String, older: Double, olderError: Double?, younger: Double, youngerError: Double?,
        rank: GeologicalRank, art: CosmicArt.Kind, color: Int,
        @StringRes title: Int, @StringRes body: Int, source: CosmicSource
    ) = CosmicPeriod(id, CosmicTimeline.PRESENT - older * 1e6, CosmicTimeline.PRESENT - younger * 1e6,
        title, R.string.ct_geo_dates, body, art,
        if (id == "hadean") listOf("earth", "moon") else emptyList(), parent,
        GeologicalRange(older, younger, olderError, youngerError, rank), color,
        listOf(CosmicSource.GEOLOGICAL_SCALE, source) +
            if (id == "hadean") listOf(CosmicSource.EARLY_WATER, CosmicSource.EARTH, CosmicSource.MOON) else emptyList())

    val all = listOf(
        division("hadean", "earth", 4540.000, 50.0, 4031.000, 3.0,
            GeologicalRank.EON, CosmicArt.Kind.EARTH, 0xFFDBA077.toInt(),
            R.string.ct_geo_hadean, R.string.ct_geo_hadean_body, CosmicSource.PRECAMBRIAN),
        division("archean", "earth", 4031.000, 3.0, 2500.000, null,
            GeologicalRank.EON, CosmicArt.Kind.MICROBIAL, 0xFFD39CA7.toInt(),
            R.string.ct_geo_archean, R.string.ct_geo_archean_body, CosmicSource.PRECAMBRIAN),
        division("proterozoic", "earth", 2500.000, null, 538.800, 0.6,
            GeologicalRank.EON, CosmicArt.Kind.MICROBIAL, 0xFFE1B583.toInt(),
            R.string.ct_geo_proterozoic, R.string.ct_geo_proterozoic_body, CosmicSource.PROTEROZOIC),
        division("phanerozoic", "earth", 538.800, 0.6, 0.000, null,
            GeologicalRank.EON, CosmicArt.Kind.STRATA, 0xFFA8D3BE.toInt(),
            R.string.ct_geo_phanerozoic, R.string.ct_geo_phanerozoic_body, CosmicSource.FOSSILS),
        division("paleozoic", "phanerozoic", 538.800, 0.6, 251.902, 0.024,
            GeologicalRank.ERA, CosmicArt.Kind.MARINE, 0xFF8CCAC2.toInt(),
            R.string.ct_geo_paleozoic, R.string.ct_geo_paleozoic_body, CosmicSource.PALEOZOIC),
        division("mesozoic", "phanerozoic", 251.902, 0.024, 66.000, null,
            GeologicalRank.ERA, CosmicArt.Kind.CONIFERS, 0xFF8DBFDA.toInt(),
            R.string.ct_geo_mesozoic, R.string.ct_geo_mesozoic_body, CosmicSource.MESOZOIC),
        division("cenozoic", "phanerozoic", 66.000, null, 0.000, null,
            GeologicalRank.ERA, CosmicArt.Kind.GRASSLAND, 0xFFE2CE88.toInt(),
            R.string.ct_geo_cenozoic, R.string.ct_geo_cenozoic_body, CosmicSource.CENOZOIC),
        division("cambrian", "paleozoic", 538.800, 0.6, 486.850, 1.5,
            GeologicalRank.PERIOD, CosmicArt.Kind.MARINE, 0xFF9CCCB1.toInt(),
            R.string.ct_geo_cambrian, R.string.ct_geo_cambrian_body, CosmicSource.CAMBRIAN),
        division("ordovician", "paleozoic", 486.850, 1.5, 443.100, 0.9,
            GeologicalRank.PERIOD, CosmicArt.Kind.MARINE, 0xFF78C7B2.toInt(),
            R.string.ct_geo_ordovician, R.string.ct_geo_ordovician_body, CosmicSource.ORDOVICIAN),
        division("silurian", "paleozoic", 443.100, 0.9, 419.620, 1.36,
            GeologicalRank.PERIOD, CosmicArt.Kind.MARINE, 0xFFA6D3C4.toInt(),
            R.string.ct_geo_silurian, R.string.ct_geo_silurian_body, CosmicSource.SILURIAN),
        division("devonian", "paleozoic", 419.620, 1.36, 358.860, 0.19,
            GeologicalRank.PERIOD, CosmicArt.Kind.FERN, 0xFFC5BD8C.toInt(),
            R.string.ct_geo_devonian, R.string.ct_geo_devonian_body, CosmicSource.DEVONIAN),
        division("carboniferous", "paleozoic", 358.860, 0.19, 298.900, 0.15,
            GeologicalRank.PERIOD, CosmicArt.Kind.FERN, 0xFF8FBBA8.toInt(),
            R.string.ct_geo_carboniferous, R.string.ct_geo_carboniferous_body, CosmicSource.CARBONIFEROUS),
        division("permian", "paleozoic", 298.900, 0.15, 251.902, 0.024,
            GeologicalRank.PERIOD, CosmicArt.Kind.STRATA, 0xFFE1A590.toInt(),
            R.string.ct_geo_permian, R.string.ct_geo_permian_body, CosmicSource.PERMIAN),
        division("triassic", "mesozoic", 251.902, 0.024, 201.400, 0.2,
            GeologicalRank.PERIOD, CosmicArt.Kind.CONIFERS, 0xFFC8AADF.toInt(),
            R.string.ct_geo_triassic, R.string.ct_geo_triassic_body, CosmicSource.TRIASSIC),
        division("jurassic", "mesozoic", 201.400, 0.2, 143.100, 0.6,
            GeologicalRank.PERIOD, CosmicArt.Kind.CONIFERS, 0xFF83C7D6.toInt(),
            R.string.ct_geo_jurassic, R.string.ct_geo_jurassic_body, CosmicSource.JURASSIC),
        division("cretaceous", "mesozoic", 143.100, 0.6, 66.000, null,
            GeologicalRank.PERIOD, CosmicArt.Kind.FLOWER, 0xFFA9CC86.toInt(),
            R.string.ct_geo_cretaceous, R.string.ct_geo_cretaceous_body, CosmicSource.CRETACEOUS),
        division("paleogene", "cenozoic", 66.000, null, 23.040, null,
            GeologicalRank.PERIOD, CosmicArt.Kind.FLOWER, 0xFFE5BD91.toInt(),
            R.string.ct_geo_paleogene, R.string.ct_geo_paleogene_body, CosmicSource.PALEOGENE),
        division("neogene", "cenozoic", 23.040, null, 2.580, null,
            GeologicalRank.PERIOD, CosmicArt.Kind.GRASSLAND, 0xFFE1CF8A.toInt(),
            R.string.ct_geo_neogene, R.string.ct_geo_neogene_body, CosmicSource.NEOGENE),
        division("quaternary", "cenozoic", 2.580, null, 0.000, null,
            GeologicalRank.PERIOD, CosmicArt.Kind.ICE, 0xFFC3DBDE.toInt(),
            R.string.ct_geo_quaternary, R.string.ct_geo_quaternary_body, CosmicSource.QUATERNARY)
    )
}
