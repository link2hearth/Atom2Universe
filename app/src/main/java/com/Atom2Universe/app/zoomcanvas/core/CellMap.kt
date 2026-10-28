package com.Atom2Universe.app.zoomcanvas.core

/**
 * Une table case → couleur sans emballage : les clés (`Long`, voir [PixelLayer.cellKey]) et les valeurs
 * (`Int`) vivent dans deux tableaux, rangées par adressage ouvert. Un geste de pixels touche des dizaines
 * de milliers de cases : une `HashMap` y créerait trois objets par case (la clé, la valeur, le maillon).
 *
 * Sert aussi d'ensemble de cases (on ignore alors la valeur). Un seul fil à la fois.
 */
class CellMap(capacity: Int = 64) {

    private var keys: LongArray
    private var values: IntArray
    private var used: BooleanArray
    /** 64 − log2(taille des tableaux) : le hachage garde les bits du haut. */
    private var shift: Int

    /** Nombre de cases rangées. */
    var size = 0
        private set

    init {
        var c = 16
        while (c < capacity * 2) c = c shl 1
        keys = LongArray(c)
        values = IntArray(c)
        used = BooleanArray(c)
        shift = 64 - c.countTrailingZeroBits()
    }

    /** La place de [key] : la sienne si elle y est, sinon la première libre sur son chemin. */
    private fun slot(key: Long): Int {
        val mask = keys.size - 1
        var i = ((key * HASH) ushr shift).toInt()
        while (used[i] && keys[i] != key) i = (i + 1) and mask
        return i
    }

    fun containsKey(key: Long): Boolean = used[slot(key)]

    /** La valeur rangée pour [key], ou [missing] si la case n'y est pas. */
    fun get(key: Long, missing: Int): Int {
        val i = slot(key)
        return if (used[i]) values[i] else missing
    }

    /** Range [value] pour [key] si la case n'y est pas déjà (sinon garde l'ancienne). Vrai si elle vient d'arriver. */
    fun putIfAbsent(key: Long, value: Int): Boolean {
        val i = slot(key)
        if (used[i]) return false
        used[i] = true
        keys[i] = key
        values[i] = value
        size++
        // À moitié pleine : on double (les chemins restent courts).
        if (size * 2 > keys.size) grow()
        return true
    }

    /** Vide la table en gardant ses tableaux (la prochaine série de cases ne réalloue rien). */
    fun clear() {
        if (size == 0) return
        java.util.Arrays.fill(used, false)
        size = 0
    }

    fun interface Visitor {
        fun visit(key: Long, value: Int)
    }

    /** Parcourt les paires (case, valeur), dans un ordre quelconque. */
    fun forEach(v: Visitor) {
        for (i in keys.indices) if (used[i]) v.visit(keys[i], values[i])
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        val oldUsed = used
        val c = oldKeys.size * 2
        keys = LongArray(c)
        values = IntArray(c)
        used = BooleanArray(c)
        shift--
        for (i in oldKeys.indices) {
            if (!oldUsed[i]) continue
            val j = slot(oldKeys[i])
            used[j] = true
            keys[j] = oldKeys[i]
            values[j] = oldValues[i]
        }
    }

    private companion object {
        /** Le nombre d'or sur 64 bits : mélange bien les cases voisines (des clés qui ne diffèrent que d'un). */
        const val HASH = -7046029254386353131L
    }
}
