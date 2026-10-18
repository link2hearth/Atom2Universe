package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.caves.world.MineralProgression as P

/**
 * Le catalogue créatif range les paliers d'un même objet sous une seule case : un plastron de
 * cuivre T1 à T6 forme une famille, déployée à la demande.
 */
internal object CreativeTiers {
    /** Famille commune à tous les paliers d'un objet (même forme, même métal), ou null s'il n'en a pas. */
    fun family(id: Short): String? {
        MineralItems.variant(id)?.let { return "mineral:${it.form}:${P.metal(it.stage)}" }
        ForgedEquipment.template(id)?.let { return "armor:${it.slot}:${P.metal(it.stage)}" }
        if (MineralItems.reinforcementTier(id) != null) return "reinforcement"
        for (tier in 1..P.TIERS) {
            if (id == MineralItems.steel(tier)) return "steel"
            if (id == MineralItems.moltenSteel(tier)) return "molten_steel"
        }
        return null
    }

    /** Palier de l'objet dans sa famille (1 pour tout ce qui n'en a pas). */
    fun tier(id: Short): Int {
        MineralItems.variant(id)?.let { return it.tier }
        ForgedEquipment.template(id)?.let { return P.tier(it.stage) }
        MineralItems.reinforcementTier(id)?.let { return it }
        return (1..P.TIERS).firstOrNull { id == MineralItems.steel(it) || id == MineralItems.moltenSteel(it) } ?: 1
    }

    /**
     * Regroupe [ordered] (déjà trié) : chaque famille n'occupe qu'une place, celle de son premier
     * membre rencontré, tenue par son plus petit palier. La famille [open] montre tous ses paliers
     * à la suite. Rend l'ordre obtenu et, pour chaque tête de famille, le nombre de paliers.
     */
    fun <T> group(ordered: List<T>, idOf: (T) -> Short?, open: String?): Pair<List<T>, Map<T, Int>> {
        val families = ordered.associateWith { item -> idOf(item)?.let(::family) }
        val members = ordered.groupBy { families[it] }
        val heads = HashMap<T, Int>()
        val placed = HashSet<String>()
        val result = ArrayList<T>(ordered.size)
        for (item in ordered) {
            val family = families[item]
            val group = family?.let(members::get)?.takeIf { it.size > 1 }
            if (family == null || group == null) { result += item; continue }
            if (!placed.add(family)) continue
            val tiers = group.sortedBy { idOf(it)?.let(::tier) ?: 1 }
            heads[tiers.first()] = tiers.size
            if (family == open) result += tiers else result += tiers.first()
        }
        return result to heads
    }
}
