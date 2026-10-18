package com.Atom2Universe.app.science

import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.core.net.toUri
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.botany.BotanyCatalog

/**
 * Shared botanical explanations for the atlas and life tree.
 * Family diagnosis comes directly from the atlas resources, never a copied article.
 * Order comparisons describe common traits, not an identification key or a new topology.
 * Sources: P. F. Stevens, Angiosperm Phylogeny Website; Kew POWO.
 * Checked 2026-10-11. The offline OpenTree archive remains authoritative for ancestry.
 */
object PlantGroupKnowledge {
    private val orderFamilies = mapOf(
        "Alismatales" to listOf("Alismataceae"),
        "Ericales" to listOf("Theaceae", "Ericaceae", "Primulaceae", "Actinidiaceae", "Lecythidaceae"),
        "Myrtales" to listOf("Lythraceae", "Combretaceae"),
        "Laurales" to listOf("Lauraceae"),
        "Asparagales" to listOf("Amaryllidaceae", "Asphodelaceae", "Asparagaceae", "Orchidaceae"),
        "Cucurbitales" to listOf("Cucurbitaceae"),
        "Gentianales" to listOf("Rubiaceae", "Apocynaceae"),
        "Sapindales" to listOf("Rutaceae", "Sapindaceae"),
        "Solanales" to listOf("Solanaceae"),
        "Boraginales" to listOf("Boraginaceae"),
        "Piperales" to listOf("Aristolochiaceae"),
        "Pinales" to listOf("Pinaceae", "Araucariaceae"),
        "Cupressales" to listOf("Cupressaceae"),
        "Zygophyllales" to listOf("Zygophyllaceae"),
        "Lamiales" to listOf("Oleaceae", "Pedaliaceae", "Plantaginaceae", "Lamiaceae"),
        "Fagales" to listOf("Fagaceae", "Betulaceae", "Juglandaceae"),
        "Malvales" to listOf("Malvaceae"),
        "Rosales" to listOf("Rosaceae", "Moraceae", "Urticaceae", "Ulmaceae", "Rhamnaceae"),
        "Malpighiales" to listOf("Salicaceae", "Violaceae"),
        "Dipsacales" to listOf("Viburnaceae"),
        "Cornales" to listOf("Cornaceae"),
        "Celastrales" to listOf("Celastraceae"),
        "Aquifoliales" to listOf("Aquifoliaceae"),
        "Buxales" to listOf("Buxaceae"),
        "Fabales" to listOf("Fabaceae"),
        "Asterales" to listOf("Asteraceae", "Campanulaceae"),
        "Ranunculales" to listOf("Ranunculaceae", "Papaveraceae"),
        "Apiales" to listOf("Apiaceae", "Araliaceae"),
        "Poales" to listOf("Poaceae", "Bromeliaceae"),
        "Brassicales" to listOf("Brassicaceae"),
        "Caryophyllales" to listOf("Caryophyllaceae", "Plumbaginaceae", "Droseraceae", "Cactaceae"),
        "Geraniales" to listOf("Geraniaceae"),
        "Zingiberales" to listOf("Musaceae", "Zingiberaceae"),
        "Arecales" to listOf("Arecaceae"),
        "Saxifragales" to listOf("Grossulariaceae", "Crassulaceae"),
    )
    private val familyOrders = orderFamilies.flatMap { (order, families) -> families.map { it to order } }.toMap()
    private val clades = mapOf(
        "Chlorophyta" to R.string.plant_group_Chlorophyta,
        "Streptophyta" to R.string.plant_group_Streptophyta,
        "Bryophyta" to R.string.plant_group_Bryophyta,
        "Marchantiophyta" to R.string.plant_group_Marchantiophyta,
        "Chloroplastida" to R.string.plant_group_green,
        "Viridiplantae" to R.string.plant_group_green,
        "Embryophyta" to R.string.plant_group_land,
        "Tracheophyta" to R.string.plant_group_vascular,
        "Spermatophyta" to R.string.plant_group_seeds,
        "Angiospermae" to R.string.plant_group_flowers,
        "Magnoliophyta" to R.string.plant_group_flowers,
        // The bundled OpenTree archive uses Magnoliopsida for all flowering plants.
        // This alias follows that archive, not the historical class of dicotyledons.
        "Magnoliopsida" to R.string.plant_group_flowers,
        "Mesangiospermae" to R.string.plant_group_Mesangiospermae,
        "Magnoliidae" to R.string.plant_group_Magnoliidae,
        "Monocots" to R.string.plant_group_monocots,
        "Monocotyledoneae" to R.string.plant_group_monocots,
        "Liliopsida" to R.string.plant_group_monocots,
        "Eudicotyledons" to R.string.plant_group_eudicots,
        "Eudicotyledoneae" to R.string.plant_group_eudicots,
        "Gymnospermae" to R.string.plant_group_gymnosperms,
        "Acrogymnospermae" to R.string.plant_group_gymnosperms,
        "Monilophyta" to R.string.plant_group_ferns,
        "Polypodiopsida" to R.string.plant_group_ferns
    )
    private val subgroups = mapOf(
        "Euphyllophyta" to (R.string.plant_group_Euphyllophyta to "sporing.html"),
        "Core_Lamiales" to (R.string.plant_group_Core_Lamiales to "lamialesweb.htm"),
        "Asphodeloideae" to (R.string.plant_group_Asphodeloideae to "asparagalesweb.htm"),
        "Amygdaleae" to (R.string.plant_group_Amygdaleae to "rosalesweb.htm"),
        "Apiineae" to (R.string.plant_group_Apiineae to "apialesweb.htm"),
        "Cicereae" to (R.string.plant_group_Cicereae to "fabalesweb.htm"),
        "Oleeae" to (R.string.plant_group_Oleeae to "lamialesweb.htm"),
        "Phoeniceae" to (R.string.plant_group_Phoeniceae to "arecalesweb.htm"),
        "Plantagineae" to (R.string.plant_group_Plantagineae to "lamialesweb.htm"),
        "Saliceae" to (R.string.plant_group_Saliceae to "malpighialesweb.htm"),
        "Ranunculoideae" to (R.string.plant_group_Ranunculoideae to "ranunculalesweb.htm"),
        "Agrimoniinae" to (R.string.plant_group_Agrimoniinae to "rosalesweb.htm"),
        "Sanguisorbeae" to (R.string.plant_group_Sanguisorbeae to "rosalesweb.htm"),
        "Cercidoideae" to (R.string.plant_group_Cercidoideae to "fabalesweb.htm"),
        "Fragariinae" to (R.string.plant_group_Fragariinae to "rosalesweb.htm"),
        "Kerrieae" to (R.string.plant_group_Kerrieae to "rosalesweb.htm"),
        "Cinchonoideae" to (R.string.plant_group_Cinchonoideae to "gentianalesweb.htm"),
        "Rubieae" to (R.string.plant_group_Rubieae to "gentianalesweb.htm"),
        "Lycieae" to (R.string.plant_group_Lycieae to "solanalesweb.htm"),
        "Cactoideae" to (R.string.plant_group_Cactoideae to "caryophyllalesweb.htm"),
        "Opuntioideae" to (R.string.plant_group_Opuntioideae to "caryophyllalesweb.htm"),
        "Attaleinae" to (R.string.plant_group_Attaleinae to "arecalesweb.htm"),
        "Saniculoideae" to (R.string.plant_group_Saniculoideae to "apialesweb.htm"),
        "Equisetidae" to (R.string.plant_group_Equisetidae to "sporing.html"),
        "Polypodiidae" to (R.string.plant_group_Polypodiidae to "sporing.html"),
        "Ginkgoidae" to (R.string.plant_group_Ginkgoidae to "ginkgoales.html"),
        "Allioideae" to (R.string.plant_group_Allioideae to "asparagalesweb.htm"),
        "Allieae" to (R.string.plant_group_Allioideae to "asparagalesweb.htm"),
        "Amaryllidoideae" to (R.string.plant_group_Amaryllidoideae to "asparagalesweb.htm"),
        "Oryzoideae" to (R.string.plant_group_Oryzoideae to "poalesweb.htm"),
        "Oryzeae" to (R.string.plant_group_Oryzoideae to "poalesweb.htm"),
        "Puyoideae" to (R.string.plant_group_Puyoideae to "poalesweb.htm")
    )
    private val comparisons = mapOf(
        "Ericales" to R.string.plant_group_Ericales,
        "Myrtales" to R.string.plant_group_Myrtales,
        "Laurales" to R.string.plant_group_Laurales,
        "Asparagales" to R.string.plant_group_Asparagales,
        "Cucurbitales" to R.string.plant_group_Cucurbitales,
        "Gentianales" to R.string.plant_group_Gentianales,
        "Sapindales" to R.string.plant_group_Sapindales,
        "Solanales" to R.string.plant_group_Solanales,
        "Pinales" to R.string.plant_group_Pinales,
        "Cupressales" to R.string.plant_group_Cupressales,
        "Zygophyllales" to R.string.plant_group_Zygophyllales,
        "Lamiales" to R.string.plant_group_Lamiales,
        "Fagales" to R.string.plant_group_Fagales,
        "Malvales" to R.string.plant_group_Malvales,
        "Rosales" to R.string.plant_group_Rosales,
        "Malpighiales" to R.string.plant_group_Malpighiales,
        "Dipsacales" to R.string.plant_group_Dipsacales,
        "Cornales" to R.string.plant_group_Cornales,
        "Celastrales" to R.string.plant_group_Celastrales,
        "Aquifoliales" to R.string.plant_group_Aquifoliales,
        "Buxales" to R.string.plant_group_Buxales,
        "Fabales" to R.string.plant_group_Fabales,
        "Asterales" to R.string.plant_group_Asterales,
        "Ranunculales" to R.string.plant_group_Ranunculales,
        "Apiales" to R.string.plant_group_Apiales,
        "Poales" to R.string.plant_group_Poales,
        "Brassicales" to R.string.plant_group_Brassicales,
        "Caryophyllales" to R.string.plant_group_Caryophyllales,
        "Geraniales" to R.string.plant_group_Geraniales,
        "Zingiberales" to R.string.plant_group_Zingiberales,
        "Arecales" to R.string.plant_group_Arecales,
        "Saxifragales" to R.string.plant_group_Saxifragales,
    )
    fun hasArticle(scientific: String): Boolean =
        scientific in clades || scientific in comparisons || scientific in subgroups || BotanyCatalog.families.any { it.id == scientific }

    fun descriptionResource(scientific: String): Int? =
        BotanyCatalog.families.firstOrNull { it.id == scientific }?.description
            ?: clades[scientific] ?: subgroups[scientific]?.first ?: comparisons[scientific]

    fun description(context: Context, scientific: String): String? =
        descriptionResource(scientific)?.let { context.getString(it) }

    /** Explicit, individually checked URLs: APweb filenames are not uniform. */
    private val orderPages = mapOf(
        "Alismatales" to "alismatalesweb.htm",
        "Ericales" to "ericalesweb.htm", "Myrtales" to "myrtalesweb2.htm",
        "Laurales" to "lauralesweb.htm", "Asparagales" to "asparagalesweb.htm",
        "Cucurbitales" to "cucurbitalesweb.htm", "Gentianales" to "gentianalesweb.htm",
        "Sapindales" to "sapindalesweb.htm", "Solanales" to "solanalesweb.htm",
        "Pinales" to "conifers.html", "Cupressales" to "conifers.html",
        "Zygophyllales" to "zygophyllalesweb.htm", "Lamiales" to "lamialesweb.htm",
        "Fagales" to "fagalesweb.htm", "Malvales" to "malvalesweb.htm",
        "Rosales" to "rosalesweb.htm", "Malpighiales" to "malpighialesweb.htm",
        "Dipsacales" to "dipsacalesweb.htm", "Cornales" to "cornalesweb.htm",
        "Celastrales" to "celastralesweb.html", "Aquifoliales" to "aquifolialesweb2.htm",
        "Buxales" to "buxalesweb.htm", "Fabales" to "fabalesweb.htm",
        "Asterales" to "asteralesweb.htm", "Ranunculales" to "ranunculalesweb.htm",
        "Apiales" to "apialesweb.htm", "Poales" to "poalesweb.htm",
        "Brassicales" to "brassicalesweb.htm", "Caryophyllales" to "caryophyllalesweb.htm",
        "Geraniales" to "geranialesweb.htm", "Zingiberales" to "zingiberalesweb.htm",
        "Arecales" to "arecalesweb.htm", "Saxifragales" to "saxifragalesweb.htm"
    )

    fun sources(scientific: String): List<Pair<Int, String>> {
        val order = familyOrders[scientific] ?: scientific.takeIf { it in comparisons }
        // A source page is metadata, never a replacement parent in the offline tree.
        val subgroupSource = subgroups[scientific]?.second
        val page = subgroupSource ?: order?.let { orderPages.getValue(it) }
            ?: if(scientific in setOf("Chloroplastida", "Viridiplantae", "Chlorophyta", "Streptophyta", "Embryophyta", "Tracheophyta", "Bryophyta", "Marchantiophyta", "Monilophyta", "Polypodiopsida")) "sporing.html"
            else if(scientific in setOf("Gymnospermae", "Acrogymnospermae")) "conifers.html"
            else "amborellalesweb2.htm"
        val apweb = "https://www.mobot.org/MOBOT/research/APweb/orders/$page"
        return listOf(R.string.plant_group_source_apweb to (apweb +
            if (scientific in familyOrders) "#$scientific" else ""),
            R.string.plant_group_source_kew to "https://powo.science.kew.org/results?q=${android.net.Uri.encode(scientific)}")
    }

    /** Shared presentation avoids source links/qualifications drifting between modules. */
    fun append(context: Context, body: LinearLayout, palette: SciencePalette, scientific: String,
               includeDescription: Boolean = true) {
        if (!hasArticle(scientific)) return
        fun text(value: String, bold: Boolean = false) {
            body.addView(TextView(context).apply {
                this.text = value; textSize = 15f; setTextColor(palette.text)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
        if (includeDescription) description(context, scientific)?.let { text(it) }
        text(context.getString(R.string.plant_group_differences), true)
        text(context.getString(R.string.plant_group_scope))
        val order = familyOrders[scientific]
        // An order's description already is its comparison paragraph.
        if (order != null) comparisons[order]?.let { text(context.getString(it)) }
        sources(scientific).forEach { (title, url) ->
            body.addView(Button(context).apply {
                this.text = context.getString(title); isAllCaps = false; setTextColor(palette.text)
                background = palette.control(false)
                setOnClickListener {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.bot_no_browser, Toast.LENGTH_SHORT).show() }
                }
            })
        }
    }
}
