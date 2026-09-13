package com.Atom2Universe.app.games.caves.ai

/**
 * « Ce bloc est-il plein ? », en coordonnées entières.
 *
 * Une interface plutôt qu'un lambda `(Int, Int, Int) -> Boolean` : ce dernier emballe chaque
 * entier dans un objet à chaque appel, et la ligne de vue en fait des centaines par image.
 */
internal fun interface SolidGrid {
    fun isSolid(x: Int, y: Int, z: Int): Boolean
}

/** Liste d'entiers qui grandit toute seule, sans emballer les valeurs (réutilisable d'une recherche à l'autre). */
internal class IntList(capacity: Int = 16) {
    private var data = IntArray(capacity.coerceAtLeast(1))

    var size = 0
        private set

    operator fun get(index: Int): Int {
        if (index !in 0 until size) throw IndexOutOfBoundsException("$index hors de 0..${size - 1}")
        return data[index]
    }

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun clear() { size = 0 }

    fun isEmpty() = size == 0

    fun last(): Int = get(size - 1)

    fun reverse() {
        var i = 0; var j = size - 1
        while (i < j) {
            val t = data[i]; data[i] = data[j]; data[j] = t
            i++; j--
        }
    }

    fun copyFrom(other: IntList) {
        clear()
        for (i in 0 until other.size) add(other.data[i])
    }
}
