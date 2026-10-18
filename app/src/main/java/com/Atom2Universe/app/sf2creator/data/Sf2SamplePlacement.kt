package com.Atom2Universe.app.sf2creator.data

/**
 * Where new sounds go on the keyboard when audio files are added to an instrument.
 *
 * - One sound in an empty instrument plays on the whole keyboard.
 * - Several pitched sounds in an empty instrument share the keyboard: each one plays up to
 *   halfway to the next.
 * - Otherwise a pitched sound plays on its own note, and a sound without a clear pitch (drum,
 *   effect) takes the next free key from C2 up, like a drum kit.
 */
object Sf2SamplePlacement {

    const val FIRST_FREE_KEY = 36
    private const val DEFAULT_ROOT = 60

    class Placement(val rootNote: Int, val low: Int, val high: Int)

    /**
     * @param pitches detected MIDI note of each new sound, null when it has no clear pitch
     * @param occupied keys the instrument already plays
     */
    fun place(pitches: List<Int?>, occupied: Set<Int>): List<Placement> {
        if (pitches.isEmpty()) return emptyList()
        if (occupied.isEmpty() && pitches.size == 1) {
            return listOf(Placement(pitches[0] ?: DEFAULT_ROOT, 0, 127))
        }
        if (occupied.isEmpty() && pitches.all { it != null }) {
            val roots = pitches.filterNotNull().distinct().sorted()
            val ranges = roots.mapIndexed { n, root ->
                val low = if (n == 0) 0 else (roots[n - 1] + root) / 2 + 1
                val high = if (n == roots.lastIndex) 127 else (root + roots[n + 1]) / 2
                root to (low to high)
            }.toMap()
            return pitches.map { root -> ranges.getValue(root!!).let { (low, high) -> Placement(root, low, high) } }
        }
        val taken = (occupied + pitches.filterNotNull()).toMutableSet()
        var key = FIRST_FREE_KEY
        return pitches.map { root ->
            if (root != null) {
                Placement(root, root, root)
            } else {
                while (key in taken && key < 127) key++
                taken += key
                Placement(key, key, key)
            }
        }
    }
}
