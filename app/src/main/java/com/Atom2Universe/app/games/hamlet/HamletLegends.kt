package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject

/** Une nuit de veillée : ce qu'il faut offrir, puis une attente réelle que rien ne raccourcit. */
class VigilPhase(val coins: Long, val crystal: Long, val needs: List<HamletEconomy.Need>, val hours: Int)

/** Une légende : le héros qui arrive à la fin, le chapitre qui ouvre la veillée, les trois nuits. */
class LegendDef(val hero: Int, val chapter: Int, val phases: List<VigilPhase>, val requires: List<Int> = emptyList())

/** Le résultat d'une nuit terminée : à la troisième, le héros se révèle. */
class VigilStep(val legend: Int, val night: Int, val recruit: HeroRecruitment?)

/**
 * Les légendes ne sortent jamais d'un tirage : elles se méritent par des veillées, sur un cercle de pierres
 * construit au village. Chaque légende s'annonce par une rumeur, un chapitre avant de s'ouvrir. Trois nuits ;
 * chacune demande des pièces, des cristaux et des récoltes de la réserve, puis une attente de plusieurs
 * heures. On peut tout préparer à l'avance, mais on ne peut pas aller plus vite que le temps.
 */
class HamletLegends(private val g: HamletGame) {
    /** Nuits terminées, par légende (0 à 3). */
    val nights = IntArray(defs.size)
    /** Fin de la veillée en cours, en millisecondes d'horloge ; 0 quand aucune n'est lancée. */
    val until = LongArray(defs.size)

    fun reset() { nights.fill(0); until.fill(0) }
    fun copyFrom(other: HamletLegends) { other.nights.copyInto(nights); other.until.copyInto(until) }

    fun hero(legend: Int) = defs[legend].hero
    fun owned(legend: Int) = g.adventure.heroes[defs[legend].hero].owned
    /** La rumeur circule un chapitre avant l'ouverture, et seulement tant que la légende n'est pas révélée. */
    fun whispered(legend: Int) = legend in defs.indices && !owned(legend) && (g.sandbox || g.journey.chapter >= defs[legend].chapter - 1)
    fun circleBuilt() = g.sandbox || g.objs.values.any { it.t == CIRCLE && g.buildLeft(it) == 0L }
    fun opened(legend: Int) = whispered(legend) && (g.sandbox || g.journey.chapter >= defs[legend].chapter) &&
        defs[legend].requires.all { g.adventure.heroes[it].owned }
    fun running(legend: Int) = until[legend] > 0
    fun timeLeft(legend: Int) = if (until[legend] <= 0) 0L else (until[legend] - g.now()).coerceAtLeast(0)
    fun phase(legend: Int): VigilPhase? = defs[legend].phases.getOrNull(nights[legend])

    fun canPay(legend: Int): Boolean {
        val p = phase(legend) ?: return false
        if (g.sandbox) return true
        return g.coins >= p.coins && g.adventure[AdventureResource.CRYSTAL] >= p.crystal && p.needs.all { g.stock(it.crop) >= it.units }
    }
    fun canBegin(legend: Int) = legend in defs.indices && opened(legend) && circleBuilt() && !running(legend) && phase(legend) != null && canPay(legend)

    /** Offre les présents et allume la veillée. */
    fun begin(legend: Int): Boolean {
        if (!canBegin(legend)) return false
        val p = phase(legend)!!
        g.adventure.advanceProduction()
        if (!g.sandbox) {
            g.coins -= p.coins
            g.adventure.bag[AdventureResource.CRYSTAL.ordinal] -= p.crystal
            for (n in p.needs) g.pantry[n.crop] = g.stock(n.crop) - n.units
        }
        until[legend] = g.now() + p.hours * 3_600_000L
        g.stat("vigils"); g.listener?.onChanged()
        return true
    }
    fun canFinish(legend: Int) = legend in defs.indices && running(legend) && timeLeft(legend) == 0L

    /** Termine la nuit. À la troisième, le héros arrive. */
    fun finish(legend: Int): VigilStep? {
        if (!canFinish(legend)) return null
        until[legend] = 0
        nights[legend]++
        val night = nights[legend]
        var recruit: HeroRecruitment? = null
        if (night >= defs[legend].phases.size) {
            val h = g.adventure.heroes[defs[legend].hero]
            val fresh = !h.owned
            g.adventure.welcome(h)
            if (!fresh) h.shards += HamletHeroes.duplicateShards(h.def.rarity)
            recruit = HeroRecruitment(h.id, fresh, if (fresh) 0 else HamletHeroes.duplicateShards(h.def.rarity), guaranteed = false, scripted = false, legend = true)
            g.stat("legends")
            g.chronicle.record(HamletChronicle.Kind.LEGEND, subject = h.id.toString())
        }
        g.listener?.onChanged()
        return VigilStep(legend, night, recruit)
    }

    /** La première légende que la rumeur annonce et que le joueur n'a pas encore ouverte (pour la pastille). */
    fun attention(): Boolean = defs.indices.any { canFinish(it) || (canBegin(it) && !running(it)) }

    fun encode(): JSONObject = JSONObject().put("nights", JSONArray(nights.toList())).put("until", JSONArray(until.toList()))
    fun decode(j: JSONObject?) {
        reset()
        if (j == null) return
        j.optJSONArray("nights")?.let { a -> for (i in nights.indices) nights[i] = a.optInt(i).coerceIn(0, defs[i].phases.size) }
        j.optJSONArray("until")?.let { a -> for (i in until.indices) until[i] = if (nights[i] < defs[i].phases.size) a.optLong(i).coerceAtLeast(0) else 0 }
    }

    companion object {
        /** Le cercle de pierres du catalogue sert de site : aucun dessin de plus. */
        const val CIRCLE = "stonecircle"

        private fun need(crop: String, units: Int) = HamletEconomy.Need(crop, units)

        /** Solène, Ronce et Vulcain se méritent dans cet ordre ; Névé attend les trois. */
        val defs = listOf(
            LegendDef(8, 6, listOf(
                VigilPhase(12_000, 10, listOf(need("lavender", 24), need("pumpkins", 16)), 12),
                VigilPhase(24_000, 20, listOf(need("broccoli", 24), need("watermelon", 16)), 24),
                VigilPhase(48_000, 40, listOf(need("cotton", 30), need("eggplant", 20)), 24),
            )),
            LegendDef(30, 8, listOf(
                VigilPhase(40_000, 20, listOf(need("hops", 24), need("tea", 16)), 12),
                VigilPhase(80_000, 40, listOf(need("sunflower", 24), need("grapes", 16)), 24),
                VigilPhase(160_000, 80, listOf(need("saffron", 30), need("tea", 24)), 24),
            )),
            LegendDef(35, 10, listOf(
                VigilPhase(150_000, 60, listOf(need("grapes", 30), need("saffron", 20)), 12),
                VigilPhase(300_000, 120, listOf(need("saffron", 36), need("sunflower", 30)), 24),
                VigilPhase(600_000, 240, listOf(need("tea", 40), need("hops", 40), need("grapes", 30)), 24),
            )),
            LegendDef(HamletHeroes.MYTHIC, 12, listOf(
                VigilPhase(1_500_000, 150, listOf(need("saffron", 40), need("grapes", 40)), 24),
                VigilPhase(3_000_000, 300, listOf(need("tea", 50), need("sunflower", 50)), 48),
                VigilPhase(6_000_000, 550, listOf(need("saffron", 60), need("hops", 50), need("grapes", 50)), 72),
            ), requires = listOf(8, 30, 35)),
        )
    }
}
