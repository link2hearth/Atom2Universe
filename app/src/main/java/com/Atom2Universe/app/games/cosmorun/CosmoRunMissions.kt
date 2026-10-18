package com.Atom2Universe.app.games.cosmorun

import kotlin.math.min

/**
 * Trois missions en permanence. Les trois faites, le **rang** monte et trois nouvelles missions,
 * plus exigeantes, arrivent. Le rang débloque des combinaisons ; il ne touche jamais le score,
 * pour que les records restent comparables d'un rang à l'autre.
 */
class CosmoRunMissions {
    /** [perRun] : la progression est celle de la course en cours (elle repart de zéro à chaque départ). */
    enum class Kind(val perRun: Boolean) { ATOMS(false), DISTANCE(true), CLEARS(false), CHAIN(true), ROOF(false), SMASHES(false) }

    class Mission(val kind: Kind, val target: Int) {
        var progress = 0f
        val done get() = progress >= target
        val fraction get() = (progress / target).coerceIn(0f, 1f)
    }

    var rank = 0; private set
    var missions: List<Mission> = make(0); private set
    /** Vrai pour la mission terminée pendant cet appel de [add] / [report] (lu par l'appelant). */
    var justFinished = false; private set
    var justRankedUp = false; private set

    fun restore(rank: Int, progress: FloatArray) {
        this.rank = rank.coerceIn(0, MAX_RANK)
        missions = make(this.rank)
        for (i in missions.indices) {
            val p = progress.getOrNull(i) ?: 0f
            missions[i].progress = if (p.isFinite()) p.coerceIn(0f, missions[i].target - .001f) else 0f
        }
    }

    fun progressArray() = FloatArray(missions.size) { missions[it].progress }

    /** Une course commence : les missions « en une seule course » repartent de zéro. */
    fun startRun() {
        for (m in missions) if (m.kind.perRun) m.progress = 0f
    }

    /** Ajoute [amount] aux missions cumulatives de ce genre. Rend les missions terminées. */
    fun add(kind: Kind, amount: Float): Int = update(kind) { it.progress + amount }

    /** Pour les missions en une course : garde le meilleur niveau atteint. */
    fun report(kind: Kind, value: Float): Int = update(kind) { maxOf(it.progress, value) }

    private inline fun update(kind: Kind, next: (Mission) -> Float): Int {
        justFinished = false; justRankedUp = false
        var finished = 0
        for (m in missions) {
            if (m.kind != kind || m.done) continue
            m.progress = next(m)
            if (m.done) { m.progress = m.target.toFloat(); finished++ }
        }
        if (finished > 0) {
            justFinished = true
            if (missions.all { it.done }) {
                rank = min(rank + 1, MAX_RANK); missions = make(rank); justRankedUp = true
            }
        }
        return finished
    }

    companion object {
        const val MAX_RANK = 99
        private val ORDER = arrayOf(Kind.ATOMS, Kind.DISTANCE, Kind.CLEARS, Kind.CHAIN, Kind.ROOF, Kind.SMASHES)

        fun target(kind: Kind, rank: Int) = when (kind) {
            Kind.ATOMS -> 80 + 30 * rank
            Kind.DISTANCE -> min(5000, 700 + 200 * rank)
            Kind.CLEARS -> 25 + 8 * rank
            Kind.CHAIN -> min(200, 20 + 8 * rank)
            Kind.ROOF -> 15 + 5 * rank
            Kind.SMASHES -> 6 + 2 * rank
        }

        /** Trois genres consécutifs dans l'ordre : ils sont toujours distincts, et tournent avec le rang. */
        fun make(rank: Int): List<Mission> = List(3) {
            val kind = ORDER[(rank * 2 + it) % ORDER.size]
            Mission(kind, target(kind, rank))
        }

        /** Combinaisons de vol débloquées à ce rang. */
        fun suitsUnlocked(rank: Int) = when { rank >= 4 -> 3; rank >= 1 -> 2; else -> 1 }
        fun suitRank(suit: Int) = if (suit >= 2) 4 else if (suit == 1) 1 else 0
    }
}
