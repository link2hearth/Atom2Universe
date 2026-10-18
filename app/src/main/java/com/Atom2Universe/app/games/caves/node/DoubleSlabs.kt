package com.Atom2Universe.app.games.caves.node

/**
 * Deux dalles dans la même case, quelles qu'elles soient (même deux pareilles), couchées l'une
 * sur l'autre ou debout l'une contre l'autre.
 *
 * Il n'y a pas un bloc par paire : le **numéro du bloc** dit l'axe et la dalle du côté « moins »
 * (le bas, ou le côté −X / −Z), le **meta** de la case dit celle du côté « plus ». Chaque dalle du
 * registre reçoit un rang (0, 1, 2…) dans l'ordre de son numéro ; la double dalle d'axe `a` dont
 * le côté moins a le rang `r` est le bloc [BASE] + a × [MAX_SLABS] + r. Une nouvelle dalle ajoutée
 * au registre fonctionne donc toute seule avec toutes les autres, sans rien écrire ici.
 *
 * Limites : [MAX_SLABS] dalles (les numéros 5100..5699 sont réservés), le meta tient 256 rangs.
 * Le rang suit l'ordre des numéros : une dalle ajoutée avec un numéro plus petit que les autres
 * décalerait les rangs des mondes déjà construits (pas de rétrocompatibilité, on l'accepte).
 */
internal object DoubleSlabs {
    private const val BASE = 5100
    private const val MAX_SLABS = 200

    /** Axe d'une double dalle, comme `côté / 2` d'une dalle (voir PartialBlockModel.slabSide). */
    const val AXIS_Y = 0
    const val AXIS_X = 1
    const val AXIS_Z = 2

    /** Numéros des dalles, par rang. Rempli une fois par [definitions], au chargement du registre. */
    @Volatile private var slabs = ShortArray(0)

    private fun rank(slab: Short): Int = slabs.binarySearch(slab)
    private fun offset(id: Short): Int = id.toInt() - BASE

    fun isDouble(id: Short): Boolean {
        val o = offset(id)
        return o >= 0 && o < 3 * MAX_SLABS && o % MAX_SLABS < slabs.size
    }

    /** Axe de la double dalle [id] (voir [AXIS_Y]…), à n'appeler que si [isDouble]. */
    fun axis(id: Short): Int = offset(id) / MAX_SLABS

    /** Bloc et meta de deux dalles réunies sur [axis] (côté moins, côté plus), ou null si l'une n'est pas une dalle. */
    fun combine(minus: Short, plus: Short, axis: Int = AXIS_Y): Pair<Short, Byte>? {
        val lo = rank(minus); val hi = rank(plus)
        if (lo < 0 || hi < 0 || axis !in 0..2) return null
        return (BASE + axis * MAX_SLABS + lo).toShort() to hi.toByte()
    }

    /** Les deux dalles (côté moins, côté plus) d'une double dalle posée avec ce [meta], sinon null. */
    fun materials(id: Short, meta: Byte): Pair<Short, Short>? {
        if (!isDouble(id)) return null
        val all = slabs
        val plus = all.getOrNull(meta.toInt() and 0xFF) ?: return null
        return all[offset(id) % MAX_SLABS] to plus
    }

    /** Pour chaque axe, une double dalle par dalle du côté moins : c'est elle qui donne la dureté et le reste. */
    fun definitions(defs: Map<Short, BlockDef>): List<BlockDef> {
        val found = defs.values.filter { it.slab }.map { it.id }.sorted().toShortArray()
        require(found.size <= MAX_SLABS) { "Too many slabs for DoubleSlabs: ${found.size}" }
        slabs = found
        return (0..2).flatMap { axis ->
            found.mapIndexed { rank, id ->
                defs.getValue(id).copy(id = (BASE + axis * MAX_SLABS + rank).toShort(),
                    name = "double_slab_${axis}_$id", slab = false,
                    drop = "", creativeTab = "technical", tags = emptySet())
            }
        }
    }
}
