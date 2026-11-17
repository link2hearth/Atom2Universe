package com.Atom2Universe.app.games.sokoban

/**
 * L'espace des positions d'un plateau figé (murs + quais), partagé par la fabrique de niveaux
 * du PC et par le générateur aléatoire de la tablette : seul le budget change.
 *
 * Les cases de sol sont renumérotées de 0 à 63 : un ensemble de caisses tient dans un `Long`
 * (un bit par case). Une position = (caisses, plus petite case de la zone où le robot peut
 * marcher) : deux positions où le robot peut se rejoindre sans pousser sont la même.
 *
 * [explore] part de la position résolue et **tire** les caisses à reculons, en largeur, jusqu'à
 * épuisement : chaque position atteinte est résoluble, et sa distance est exactement le nombre
 * minimal de poussées qui la résout. Les positions les plus lointaines sont les départs les plus
 * longs que ce plateau puisse offrir.
 */
internal class SokobanSpace(
    val width: Int,
    val height: Int,
    isFloor: BooleanArray,
    goalCells: IntArray
) {
    val floorCount: Int
    /** Case de sol → case de la grille. */
    val cellOf: IntArray
    /** Case de la grille → case de sol, ou -1. */
    val floorOf: IntArray
    /** `nb[f * 4 + dir]` : case de sol voisine dans la direction `dir` (ordre de [SokobanDir]), ou -1. */
    val nb: IntArray
    private val nbMask: LongArray
    val allFloor: Long
    val goalMask: Long
    val boxCount: Int

    init {
        val n = width * height
        floorOf = IntArray(n) { -1 }
        var count = 0
        for (i in 0 until n) if (isFloor[i]) floorOf[i] = count++
        require(count in 1..64) { "64 cases de sol au plus ($count)" }
        floorCount = count
        cellOf = IntArray(count)
        for (i in 0 until n) if (floorOf[i] >= 0) cellOf[floorOf[i]] = i
        nb = IntArray(count * 4) { -1 }
        nbMask = LongArray(count)
        for (f in 0 until count) {
            val x = cellOf[f] % width
            val y = cellOf[f] / width
            for (dir in SokobanDir.entries) {
                val nx = x + dir.dx
                val ny = y + dir.dy
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                val g = floorOf[ny * width + nx]
                if (g < 0) continue
                nb[f * 4 + dir.ordinal] = g
                nbMask[f] = nbMask[f] or (1L shl g)
            }
        }
        allFloor = if (count == 64) -1L else (1L shl count) - 1
        var goals = 0L
        for (c in goalCells) goals = goals or (1L shl floorOf[c])
        goalMask = goals
        boxCount = goalCells.size
    }

    fun maskOf(gridCells: Iterable<Int>): Long {
        var m = 0L
        for (c in gridCells) m = m or (1L shl floorOf[c])
        return m
    }

    /** La zone où le robot peut marcher depuis [start] sans pousser les caisses [boxes]. */
    fun region(start: Int, boxes: Long): Long {
        val free = allFloor and boxes.inv()
        var reg = 1L shl start
        var frontier = reg
        while (frontier != 0L) {
            var next = 0L
            var f = frontier
            while (f != 0L) {
                next = next or nbMask[f.countTrailingZeroBits()]
                f = f and (f - 1)
            }
            next = next and free and reg.inv()
            reg = reg or next
            frontier = next
        }
        return reg
    }

    fun rep(start: Int, boxes: Long): Int = region(start, boxes).countTrailingZeroBits()

    /**
     * Parcours inverse exhaustif. Renvoie null si plus de [cap] positions existent (plateau trop
     * ouvert pour ce budget) ou si aucune caisse ne peut bouger.
     */
    fun explore(cap: Int, cancel: () -> Unit = {}): SokobanExploration? {
        val store = StateStore(minOf(cap + 1, 4096))
        // Amorce : la position résolue, une fois par zone où le robot peut finir.
        var rest = allFloor and goalMask.inv()
        while (rest != 0L) {
            val reg = region(rest.countTrailingZeroBits(), goalMask)
            store.add(goalMask, reg.countTrailingZeroBits(), 0)
            rest = rest and reg.inv()
        }
        if (store.count == 0) return null
        val layers = ArrayList<Int>()
        layers.add(0)
        var currentDist = 0
        var i = 0
        while (i < store.count) {
            if ((i and 1023) == 0) cancel()
            val d = store.dists[i].toInt()
            if (d != currentDist) { layers.add(i); currentDist = d }
            val mask = store.masks[i]
            val reg = region(store.reps[i].toInt(), mask)
            var bs = mask
            while (bs != 0L) {
                val b = bs.countTrailingZeroBits()
                bs = bs and (bs - 1)
                for (dir in 0 until 4) {
                    val stand = nb[b * 4 + dir]
                    if (stand < 0 || (reg ushr stand) and 1L == 0L) continue
                    val dest = nb[stand * 4 + dir]
                    if (dest < 0 || (mask ushr dest) and 1L != 0L) continue
                    val nm = (mask xor (1L shl b)) or (1L shl stand)
                    if (store.add(nm, rep(dest, nm), d + 1) && store.count > cap) return null
                }
            }
            i++
        }
        layers.add(store.count)
        if (layers.size < 3) return null
        return SokobanExploration(this, store, layers.toIntArray())
    }
}

/** Une solution lue dans l'exploration : poussées (case de sol de la caisse, direction). */
internal class SokobanLine(
    val boxes: IntArray,
    val dirs: IntArray,
    val changes: Int,
    val lines: Int,
    /** Part des poussées possibles le long de la solution qui mènent à une impasse. */
    val trapRatio: Double,
    /** Nombre de caisses que la solution déplace au moins une fois. */
    val movedBoxes: Int
) {
    val pushes get() = boxes.size
}

internal class SokobanExploration(
    val space: SokobanSpace,
    private val store: StateStore,
    /** `layers[d]` = première position à distance d ; la dernière case vaut le total. */
    private val layers: IntArray
) {
    val maxDist get() = layers.size - 2
    val stateCount get() = store.count

    fun layer(d: Int): IntRange = layers[d] until layers[d + 1]
    fun mask(i: Int) = store.masks[i]
    fun rep(i: Int) = store.reps[i].toInt()
    fun dist(i: Int) = store.dists[i].toInt()
    fun indexOf(mask: Long, rep: Int) = store.find(mask, rep)

    private class TooBig : RuntimeException(null, null, false, false)

    private var memo = LongIntMap(64)
    private var memoLimit = 0

    /**
     * Parmi les solutions optimales en poussées depuis la position [start], celle qui change le
     * moins de caisse, puis le moins de direction. Null si le graphe des solutions optimales
     * dépasse [memoLimit] entrées.
     */
    fun solve(start: Int, memoLimit: Int = 400_000): SokobanLine? {
        this.memoLimit = memoLimit
        memo = LongIntMap(1024)
        try {
            cost(start, -1, 0)
        } catch (_: TooBig) {
            return null
        }
        val nb = space.nb
        val boxes = IntArray(dist(start))
        val dirs = IntArray(dist(start))
        var i = start
        var lastBox = -1
        var lastDir = 0
        var changes = 0
        var lines = 0
        var dead = 0
        var total = 0
        // Qui est qui : pour compter les caisses jamais déplacées.
        val ids = IntArray(64) { -1 }
        var startMask = mask(start)
        var id = 0
        while (startMask != 0L) {
            ids[startMask.countTrailingZeroBits()] = id++
            startMask = startMask and (startMask - 1)
        }
        val moved = BooleanArray(id)
        for (step in 0 until dist(start)) {
            val mask = mask(i)
            val d = dist(i)
            val reg = space.region(rep(i), mask)
            var bestCost = Int.MAX_VALUE
            var bestJ = -1; var bestB = -1; var bestDir = -1
            var bs = mask
            while (bs != 0L) {
                val b = bs.countTrailingZeroBits()
                bs = bs and (bs - 1)
                for (dir in 0 until 4) {
                    val stand = nb[b * 4 + (dir xor 1)]
                    if (stand < 0 || (reg ushr stand) and 1L == 0L) continue
                    val to = nb[b * 4 + dir]
                    if (to < 0 || (mask ushr to) and 1L != 0L) continue
                    val nm = (mask xor (1L shl b)) or (1L shl to)
                    val j = indexOf(nm, space.rep(b, nm))
                    total++
                    if (j < 0) { dead++; continue }
                    if (dist(j) != d - 1) continue
                    val c = stepCost(lastBox, lastDir, b, dir) + memo.get(key(j, to, dir), 0)
                    if (c < bestCost) { bestCost = c; bestJ = j; bestB = b; bestDir = dir }
                }
            }
            if (lastBox >= 0 && bestB != lastBox) changes++
            if (bestB != lastBox || bestDir != lastDir) lines++
            boxes[step] = bestB
            dirs[step] = bestDir
            val who = ids[bestB]
            moved[who] = true
            ids[bestB] = -1
            val to = nb[bestB * 4 + bestDir]
            ids[to] = who
            lastBox = to
            lastDir = bestDir
            i = bestJ
        }
        return SokobanLine(boxes, dirs, changes, lines,
            if (total == 0) 0.0 else dead.toDouble() / total, moved.count { it })
    }

    private fun key(i: Int, lastBox: Int, lastDir: Int): Long =
        (i.toLong() shl 9) or ((lastBox + 1).toLong() shl 2) or lastDir.toLong()

    private fun stepCost(lastBox: Int, lastDir: Int, box: Int, dir: Int): Int =
        (if (lastBox >= 0 && box != lastBox) CHANGE else 0) +
            (if (box != lastBox || dir != lastDir) 1 else 0)

    private fun cost(i: Int, lastBox: Int, lastDir: Int): Int {
        val d = dist(i)
        if (d == 0) return 0
        val k = key(i, lastBox, lastDir)
        val known = memo.get(k, -1)
        if (known >= 0) return known
        if (memo.size >= memoLimit) throw TooBig()
        val nb = space.nb
        val mask = mask(i)
        val reg = space.region(rep(i), mask)
        var best = Int.MAX_VALUE
        var bs = mask
        while (bs != 0L) {
            val b = bs.countTrailingZeroBits()
            bs = bs and (bs - 1)
            for (dir in 0 until 4) {
                val stand = nb[b * 4 + (dir xor 1)]
                if (stand < 0 || (reg ushr stand) and 1L == 0L) continue
                val to = nb[b * 4 + dir]
                if (to < 0 || (mask ushr to) and 1L != 0L) continue
                val nm = (mask xor (1L shl b)) or (1L shl to)
                val j = indexOf(nm, space.rep(b, nm))
                if (j < 0 || dist(j) != d - 1) continue
                val c = stepCost(lastBox, lastDir, b, dir) + cost(j, to, dir)
                if (c < best) best = c
            }
        }
        memo.put(k, best)
        return best
    }

    private companion object {
        const val CHANGE = 1024
    }
}

/** Positions vues : tableaux denses dans l'ordre d'arrivée (la file du parcours) + table de hachage. */
internal class StateStore(capacity: Int) {
    var masks = LongArray(capacity)
    var reps = ByteArray(capacity)
    var dists = ShortArray(capacity)
    var count = 0
    private var table = IntArray(Integer.highestOneBit(maxOf(16, capacity * 2 - 1)) * 2)

    private fun slotOf(mask: Long, rep: Int): Int {
        var h = mask * -7046029254386353131L + rep
        h = h xor (h ushr 32)
        h *= -4658895280553007687L
        h = h xor (h ushr 29)
        return h.toInt() and (table.size - 1)
    }

    fun find(mask: Long, rep: Int): Int {
        val m = table.size - 1
        var slot = slotOf(mask, rep)
        while (true) {
            val e = table[slot]
            if (e == 0) return -1
            val i = e - 1
            if (masks[i] == mask && reps[i].toInt() == rep) return i
            slot = (slot + 1) and m
        }
    }

    /** Ajoute la position si elle est nouvelle ; false si elle était déjà vue. */
    fun add(mask: Long, rep: Int, dist: Int): Boolean {
        if ((count + 1) * 2 > table.size) rehash(table.size * 2)
        val m = table.size - 1
        var slot = slotOf(mask, rep)
        while (true) {
            val e = table[slot]
            if (e == 0) break
            val i = e - 1
            if (masks[i] == mask && reps[i].toInt() == rep) return false
            slot = (slot + 1) and m
        }
        if (count == masks.size) {
            val size = masks.size * 2
            masks = masks.copyOf(size)
            reps = reps.copyOf(size)
            dists = dists.copyOf(size)
        }
        masks[count] = mask
        reps[count] = rep.toByte()
        dists[count] = dist.toShort()
        count++
        table[slot] = count
        return true
    }

    private fun rehash(size: Int) {
        table = IntArray(size)
        val m = size - 1
        for (i in 0 until count) {
            var slot = slotOf(masks[i], reps[i].toInt())
            while (table[slot] != 0) slot = (slot + 1) and m
            table[slot] = i + 1
        }
    }
}

/** Table Long → Int sans boîtes, pour la mémoire du calcul de solution. */
internal class LongIntMap(capacity: Int) {
    private var keys = LongArray(Integer.highestOneBit(maxOf(16, capacity * 2 - 1)) * 2)
    private var values = IntArray(keys.size)
    private var used = BooleanArray(keys.size)
    var size = 0
        private set

    private fun slotOf(k: Long): Int {
        var h = k * -7046029254386353131L
        h = h xor (h ushr 31)
        return h.toInt() and (keys.size - 1)
    }

    fun get(k: Long, default: Int): Int {
        val m = keys.size - 1
        var s = slotOf(k)
        while (used[s]) {
            if (keys[s] == k) return values[s]
            s = (s + 1) and m
        }
        return default
    }

    fun put(k: Long, v: Int) {
        if ((size + 1) * 2 > keys.size) grow()
        val m = keys.size - 1
        var s = slotOf(k)
        while (used[s]) {
            if (keys[s] == k) { values[s] = v; return }
            s = (s + 1) and m
        }
        used[s] = true; keys[s] = k; values[s] = v; size++
    }

    private fun grow() {
        val oldK = keys; val oldV = values; val oldU = used
        keys = LongArray(oldK.size * 2); values = IntArray(keys.size); used = BooleanArray(keys.size)
        size = 0
        for (i in oldK.indices) if (oldU[i]) put(oldK[i], oldV[i])
    }
}
