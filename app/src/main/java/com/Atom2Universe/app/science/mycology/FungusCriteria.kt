package com.Atom2Universe.app.science.mycology

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import kotlin.math.max
import kotlin.math.min

/** Une ligne de critères de l'écran Espèces : plusieurs choix d'une même ligne s'additionnent (« ou »), les lignes se croisent (« et »). */
enum class CriterionGroup(@param:StringRes val title: Int) {
    CAP_WIDTH(R.string.myco_cg_cap_width),
    CAP_COLOR(R.string.myco_cg_cap_color),
    STIPE_COLOR(R.string.myco_cg_stipe_color),
    UNDER(R.string.myco_cg_under),
    CAP(R.string.myco_cg_cap),
    STIPE(R.string.myco_cg_stipe),
    MORE(R.string.myco_cg_more),
    SPORE(R.string.myco_cg_spore)
}

/** Une teinte, pour dire « chapeau brun » ou « pied blanc » : tirée de la couleur dessinée, jamais saisie à part. */
enum class Tone(@param:StringRes val label: Int) {
    WHITE(R.string.myco_tone_white),
    GREY(R.string.myco_tone_grey),
    BLACK(R.string.myco_tone_black),
    BEIGE(R.string.myco_tone_beige),
    BROWN(R.string.myco_tone_brown),
    YELLOW(R.string.myco_tone_yellow),
    ORANGE(R.string.myco_tone_orange),
    RED(R.string.myco_tone_red),
    GREEN(R.string.myco_tone_green),
    PINK(R.string.myco_tone_pink);

    companion object {
        /**
         * La teinte qu'un œil lui donnerait. Les couleurs vives se nomment d'après leur teinte (rouge, orange, jaune) ;
         * dès qu'elles sont plus sombres ou plus ternes, ce sont des bruns et des beiges (un cèpe n'est pas « orange »).
         */
        fun of(argb: Int): Tone {
            val (h, s, v) = hsv(argb)
            if (s < 0.2f) return if (v >= 0.82f) WHITE else if (v >= 0.3f) GREY else BLACK
            if (v < 0.2f) return BLACK
            if (h >= 170f && h < 340f) return PINK
            if (h >= 58f && h < 170f) return GREEN
            val vivid = s >= 0.6f
            if (h < 15f || h >= 340f) { if (vivid) return if (v >= 0.4f) RED else BROWN }
            else if (h < 38f) { if (vivid && ((s >= 0.75f && v >= 0.75f) || v >= 0.85f)) return ORANGE }
            else if (s >= 0.55f && v >= 0.75f) return YELLOW
            // rose, saumon : une teinte rouge ou orangée ternie mais claire (sinon, ce sont des bruns et des beiges)
            if (h >= 340f || (h < 22f && s < 0.6f && v >= 0.6f)) return PINK
            return if (v < 0.7f) BROWN else BEIGE
        }
    }
}

/** Teinte (0–360°), saturation et valeur (0–1) d'une couleur. */
private fun hsv(argb: Int): Triple<Float, Float, Float> {
    val r = (argb shr 16 and 255) / 255f
    val g = (argb shr 8 and 255) / 255f
    val b = (argb and 255) / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val s = if (hi == 0f) 0f else (hi - lo) / hi
    val h = when {
        hi == lo -> 0f
        hi == r -> 60f * (((g - b) / (hi - lo)) % 6f)
        hi == g -> 60f * ((b - r) / (hi - lo) + 2f)
        else -> 60f * ((r - g) / (hi - lo) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Triple(h, s, hi)
}

/** Un bouton de critère. [test] lit le dessin : un critère ne peut donc pas contredire ce qu'on voit. */
class Criterion(
    val id: String,
    val group: CriterionGroup,
    @param:StringRes val label: Int,
    val labelArgs: List<Int> = emptyList(),
    val test: (FungusLook) -> Boolean
)

/**
 * Les critères de tri de l'écran Espèces, pour retrouver un champignon vu en promenade sans tout parcourir :
 * largeur, couleurs, dessous, anneau, volve, touffe, sporée… Chaque critère est calculé d'après le dessin de l'espèce ;
 * un bouton qu'aucune espèce du catalogue ne remplit n'est pas proposé.
 */
object FungusCriteria {
    const val SMALL_CM = 6f
    const val LARGE_CM = 12f

    /** Un chapeau dont le centre est creusé d'au moins autant (en cm) compte comme « creux ». */
    const val DIPPED_CM = 0.6f

    private fun capTones(look: FungusLook) = setOf(look.capCenter, look.capMid, look.capEdge).map { Tone.of(it) }.toSet()
    private fun stipeTones(look: FungusLook) = setOf(look.stipeTop, look.stipeBottom).map { Tone.of(it) }.toSet()

    /** Famille de la sporée : blanche ou crème, rose, brune ou ocre, noire ou pourpre. */
    private fun sporeTone(look: FungusLook): Tone {
        val (h, s, v) = hsv(look.spore)
        return when {
            v >= 0.75f -> if (s >= 0.15f && (h < 30f || h >= 330f)) Tone.PINK else Tone.WHITE
            v >= 0.3f -> Tone.BROWN
            s >= 0.45f -> Tone.BROWN
            else -> Tone.BLACK
        }
    }

    private fun hasDeco(look: FungusLook) = look.capDecos.any {
        when (it) {
            is CapDeco.Warts -> it.count >= 3
            is CapDeco.Scales -> it.rows >= 2
            is CapDeco.Flakes -> it.count >= 3
            is CapDeco.Shaggy -> true
            else -> false
        }
    }

    val all: List<Criterion> = buildList {
        add(Criterion("w_small", CriterionGroup.CAP_WIDTH, R.string.myco_c_small, listOf(SMALL_CM.toInt())) { it.capDiam < SMALL_CM })
        add(Criterion("w_medium", CriterionGroup.CAP_WIDTH, R.string.myco_c_medium, listOf(SMALL_CM.toInt(), LARGE_CM.toInt())) { it.capDiam >= SMALL_CM && it.capDiam <= LARGE_CM })
        add(Criterion("w_large", CriterionGroup.CAP_WIDTH, R.string.myco_c_large, listOf(LARGE_CM.toInt())) { it.capDiam > LARGE_CM })
        for (tone in Tone.entries) add(Criterion("cap_${tone.name}", CriterionGroup.CAP_COLOR, tone.label) { tone in capTones(it) })
        for (tone in Tone.entries) add(Criterion("stipe_${tone.name}", CriterionGroup.STIPE_COLOR, tone.label) { tone in stipeTones(it) })
        add(Criterion("u_gills", CriterionGroup.UNDER, R.string.myco_c_gills) { it.hymenium == Hymenium.GILLS })
        add(Criterion("u_pores", CriterionGroup.UNDER, R.string.myco_c_pores) { it.hymenium == Hymenium.PORES })
        add(Criterion("u_ridges", CriterionGroup.UNDER, R.string.myco_c_ridges) { it.hymenium == Hymenium.RIDGES })
        add(Criterion("u_teeth", CriterionGroup.UNDER, R.string.myco_c_teeth) { it.hymenium == Hymenium.TEETH })
        add(Criterion("u_none", CriterionGroup.UNDER, R.string.myco_c_none_under) { it.hymenium == Hymenium.NONE })
        add(Criterion("c_funnel", CriterionGroup.CAP, R.string.myco_c_funnel) { it.capShape == CapShape.FUNNEL || it.capShape == CapShape.DEPRESSED || it.dip >= DIPPED_CM })
        add(Criterion("c_wrinkled", CriterionGroup.CAP, R.string.myco_c_wrinkled) { it.capShape == CapShape.MOREL || it.capShape == CapShape.BRAIN })
        add(Criterion("c_ball", CriterionGroup.CAP, R.string.myco_c_ball) { it.capShape == CapShape.BALL })
        add(Criterion("c_bracket", CriterionGroup.CAP, R.string.myco_c_bracket) { it.capShape == CapShape.BRACKET })
        add(Criterion("c_coral", CriterionGroup.CAP, R.string.myco_c_coral) { it.capShape == CapShape.CORAL })
        add(Criterion("c_scaly", CriterionGroup.CAP, R.string.myco_c_scaly) { hasDeco(it) })
        add(Criterion("s_ring", CriterionGroup.STIPE, R.string.myco_c_ring) { it.ring != RingKind.NONE })
        add(Criterion("s_no_ring", CriterionGroup.STIPE, R.string.myco_c_no_ring) { it.ring == RingKind.NONE })
        add(Criterion("s_volva", CriterionGroup.STIPE, R.string.myco_c_volva) { it.volva != VolvaKind.NONE })
        add(Criterion("s_no_volva", CriterionGroup.STIPE, R.string.myco_c_no_volva) { it.volva == VolvaKind.NONE })
        add(Criterion("m_cluster", CriterionGroup.MORE, R.string.myco_c_cluster) { it.cluster > 1 })
        add(Criterion("m_wood", CriterionGroup.MORE, R.string.myco_c_wood) { it.onWood || it.capShape == CapShape.BRACKET })
        add(Criterion("m_latex", CriterionGroup.MORE, R.string.myco_c_latex) { it.latex != 0 })
        add(Criterion("m_stains", CriterionGroup.MORE, R.string.myco_c_stains) { look -> look.stains.any { it.strength >= 0.5f } })
        add(Criterion("sp_white", CriterionGroup.SPORE, R.string.myco_c_spore_white) { sporeTone(it) == Tone.WHITE })
        add(Criterion("sp_pink", CriterionGroup.SPORE, R.string.myco_c_spore_pink) { sporeTone(it) == Tone.PINK })
        add(Criterion("sp_brown", CriterionGroup.SPORE, R.string.myco_c_spore_brown) { sporeTone(it) == Tone.BROWN })
        add(Criterion("sp_black", CriterionGroup.SPORE, R.string.myco_c_spore_black) { sporeTone(it) == Tone.BLACK })
    }

    private val byId = all.associateBy { it.id }

    /** Les critères proposés, par ligne : ceux qu'au moins une espèce remplit (un bouton qui ne trouverait rien n'a pas lieu d'être). */
    val offered: Map<CriterionGroup, List<Criterion>> by lazy {
        val looks = FungusCatalog.species.map { it.look }
        all.filter { c -> looks.any { c.test(it) } }.groupBy { it.group }
    }

    /** Vrai si le dessin remplit tous les critères choisis, une ligne après l'autre (au moins un choix par ligne). */
    fun matches(look: FungusLook, selected: Set<String>): Boolean =
        selected.mapNotNull { byId[it] }.groupBy { it.group }.values.all { choices -> choices.any { it.test(look) } }

    /** Ne garde que les identifiants encore proposés (un critère retiré ne doit pas rester coché en silence). */
    fun known(ids: Set<String>): Set<String> = ids.filterTo(HashSet()) { id -> offered.values.any { list -> list.any { it.id == id } } }
}
