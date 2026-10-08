package com.Atom2Universe.app.games.caves.ai

import kotlin.math.abs
import kotlin.math.min

/**
 * Plus court chemin entre deux cases d'une [NavGrid], par l'algorithme A*.
 *
 * A* explore d'abord les cases qui ont l'air les plus prometteuses : coût déjà payé pour y venir
 * + estimation de ce qui reste (la distance à vol d'oiseau sur la grille). Tant que l'estimation
 * ne surestime jamais, le chemin trouvé est le plus court.
 *
 * Tous les tableaux sont alloués une fois : une recherche ne crée aucun objet. On ne les remet pas
 * à zéro non plus : chaque recherche a un numéro, et une case notée d'un autre numéro compte comme
 * jamais vue.
 */
internal class PathFinder(private val grid: NavGrid) {

    private val count = grid.nodeCount
    private val gScore = FloatArray(count)
    private val cameFrom = IntArray(count)
    private val seenBy = IntArray(count)
    private val closedBy = IntArray(count)
    private val clearanceBy = IntArray(count)
    private val clearanceFree = BooleanArray(count)
    private var search = 0

    // Tas binaire (le plus petit coût estimé en haut). Une case peut y figurer plusieurs fois :
    // les doublons périmés sont ignorés à la sortie, plus simple que de réordonner le tas.
    private var heapNode = IntArray(64)
    private var heapCost = FloatArray(64)
    private var heapSize = 0
    enum class Result { PENDING, FOUND, MISSING }
    private var goal = -1
    private var routeClearance: BodyClearance? = null
    private var routeMaxCost = Float.POSITIVE_INFINITY
    private var result = Result.MISSING
    /** Budget partagé des petites recherches synchrones ; illimité hors du mode Assaut. */
    var remainingExpansions = Int.MAX_VALUE

    /**
     * Cherche le plus court chemin de [start] à [goal]. S'il existe, l'écrit dans [out] (départ et
     * arrivée compris, dans l'ordre) et renvoie vrai ; sinon [out] reste vide.
     */
    fun findPath(start: Int, goal: Int, out: IntList, clearance: BodyClearance? = null,
                 maxCost: Float = Float.POSITIVE_INFINITY): Boolean {
        out.clear()
        begin(start, goal, clearance, maxCost)
        val before = expanded
        val status = advance(out, remainingExpansions)
        if (remainingExpansions != Int.MAX_VALUE) remainingExpansions -= expanded - before
        return status == Result.FOUND
    }

    private var expanded = 0

    /** Prépare une recherche reprenable. Une instance appartient à une seule file de travail. */
    fun begin(start: Int, goal: Int, clearance: BodyClearance? = null,
              maxCost: Float = Float.POSITIVE_INFINITY) {
        result = Result.MISSING
        heapSize = 0
        this.goal = goal
        routeClearance = clearance
        routeMaxCost = maxCost
        expanded = 0
        if (start !in 0 until count || goal !in 0 until count || estimate(start, goal) > maxCost) return
        search++
        result = Result.PENDING
        gScore[start] = 0f
        cameFrom[start] = -1
        seenBy[start] = search
        push(start, estimate(start, goal))
    }

    /** Rend la main à la limite de travail ; garde le tas et les coûts pour l'image suivante. */
    fun advance(out: IntList, maxExpansions: Int, deadlineNs: Long = Long.MAX_VALUE): Result {
        if (result != Result.PENDING) return result
        val clearance = routeClearance
        var operations = 0
        while (heapSize > 0) {
            if (operations >= maxExpansions ||
                (operations and 31 == 0 && System.nanoTime() >= deadlineNs)) return Result.PENDING
            operations++
            expanded++
            val current = pop()
            if (closedBy[current] == search) continue
            if (current == goal) {
                var n = goal
                while (n != -1) { out.add(n); n = cameFrom[n] }
                out.reverse()
                result = Result.FOUND
                return result
            }
            closedBy[current] = search

            val paid = gScore[current]
            for (e in grid.edgeStart[current] until grid.edgeStart[current + 1]) {
                val next = grid.edgeTarget[e]
                if (closedBy[next] == search) continue
                val cost = paid + grid.edgeCost[e]
                if (cost + estimate(next, goal) > routeMaxCost) continue
                if (seenBy[next] != search || cost < gScore[next]) {
                    // Une seule observation par case pour ce trajet. Les corps peuvent bouger
                    // entre deux lots ; PathFollower revérifie toujours la collision réelle.
                    if (clearance != null) {
                        if (clearanceBy[next] != search) {
                            clearanceBy[next] = search
                            clearanceFree[next] = clearance.isFree(grid.nodeX[next] + .5,
                                grid.nodeY[next].toDouble(), grid.nodeZ[next] + .5)
                        }
                        if (!clearanceFree[next]) continue
                    }
                    seenBy[next] = search
                    gScore[next] = cost
                    cameFrom[next] = current
                    push(next, cost + estimate(next, goal))
                }
            }
        }
        result = Result.MISSING
        return result
    }

    /** Distance « octile » : lignes droites et diagonales, sans obstacle. Ne surestime jamais. */
    private fun estimate(from: Int, to: Int): Float {
        val dx = abs(grid.nodeX[from] - grid.nodeX[to])
        val dz = abs(grid.nodeZ[from] - grid.nodeZ[to])
        val dy = grid.nodeY[to] - grid.nodeY[from]
        // Chaque bloc monté coûte au moins 0,5 de plus qu'un déplacement horizontal ;
        // chaque bloc descendu 0,2. Borne admissible, même pour les chutes de trois blocs.
        return (dx + dz) + (DIAGONAL - 2f) * min(dx, dz) +
            if (dy >= 0) dy * .5f else -dy * .2f
    }

    private fun push(node: Int, cost: Float) {
        if (heapSize == heapNode.size) {
            heapNode = heapNode.copyOf(heapSize * 2)
            heapCost = heapCost.copyOf(heapSize * 2)
        }
        var i = heapSize++
        while (i > 0) {
            val parent = (i - 1) / 2
            if (heapCost[parent] <= cost) break
            heapNode[i] = heapNode[parent]; heapCost[i] = heapCost[parent]
            i = parent
        }
        heapNode[i] = node; heapCost[i] = cost
    }

    private fun pop(): Int {
        val top = heapNode[0]
        val lastNode = heapNode[--heapSize]
        val lastCost = heapCost[heapSize]
        var i = 0
        while (true) {
            var child = 2 * i + 1
            if (child >= heapSize) break
            if (child + 1 < heapSize && heapCost[child + 1] < heapCost[child]) child++
            if (heapCost[child] >= lastCost) break
            heapNode[i] = heapNode[child]; heapCost[i] = heapCost[child]
            i = child
        }
        heapNode[i] = lastNode; heapCost[i] = lastCost
        return top
    }

    private companion object {
        const val DIAGONAL = 1.4142135f
    }
}
