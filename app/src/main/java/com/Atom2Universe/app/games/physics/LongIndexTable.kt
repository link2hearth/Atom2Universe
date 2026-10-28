package com.Atom2Universe.app.games.physics

/**
 * Table de hachage d'une clé `Long` vers un indice, sans rien allouer à la lecture.
 *
 * Elle remplace les `LinkedHashMap<Long, …>` du monde, qui emballaient leur clé dans
 * un objet à chaque consultation — une fois par contact et par sous-pas. Elle ne range
 * **que des indices** : l'ordre, lui, est porté par la liste à laquelle ils renvoient,
 * et c'est cette liste qui garde l'ordre d'insertion dont le solveur a besoin.
 *
 * Pas de retrait : on vide et on reconstruit, ce qui ne se fait que lorsqu'un contact
 * disparaît, et coûte un parcours de la liste.
 */
internal class LongIndexTable {

    private var keys = LongArray(64)
    /** Indice rangé, ou -1 pour une case libre. */
    private var values = IntArray(64).also { it.fill(-1) }
    private var mask = 63

    var size = 0
        private set

    /** L'indice rangé sous [key], ou -1. */
    fun get(key: Long): Int {
        var i = slot(key)
        while (true) {
            val v = values[i]
            if (v < 0) return -1
            if (keys[i] == key) return v
            i = (i + 1) and mask
        }
    }

    /** Range [value] (positif ou nul) sous [key], en remplaçant l'ancien s'il existe. */
    fun put(key: Long, value: Int) {
        if ((size + 1) * 2 > keys.size) grow()
        var i = slot(key)
        while (values[i] >= 0) {
            if (keys[i] == key) { values[i] = value; return }
            i = (i + 1) and mask
        }
        keys[i] = key
        values[i] = value
        size++
    }

    fun clear() {
        if (size == 0) return
        values.fill(-1)
        size = 0
    }

    private fun slot(key: Long): Int {
        val h = key * -7046029254386353131L
        return (h xor (h ushr 32)).toInt() and mask
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = LongArray(oldKeys.size * 2)
        values = IntArray(oldKeys.size * 2).also { it.fill(-1) }
        mask = keys.size - 1
        size = 0
        for (i in oldKeys.indices) if (oldValues[i] >= 0) put(oldKeys[i], oldValues[i])
    }
}
