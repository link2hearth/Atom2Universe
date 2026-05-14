package com.Atom2Universe.app.games.theline

data class PathState(
    val colorValue: Int,
    val endpoints: List<TLCoord>,
    val sequence: MutableList<TLCoord> = mutableListOf(),
    var complete: Boolean = false
)

class TheLineGame {

    var mode: TheLineMode = TheLineMode.SINGLE
    var difficulty: TheLineDifficulty = TheLineDifficulty.EASY
    var levelCounters = mutableMapOf(
        TheLineMode.SINGLE to mutableMapOf(
            TheLineDifficulty.EASY to 1, TheLineDifficulty.MEDIUM to 1, TheLineDifficulty.HARD to 1
        ),
        TheLineMode.MULTI to mutableMapOf(
            TheLineDifficulty.EASY to 1, TheLineDifficulty.MEDIUM to 1, TheLineDifficulty.HARD to 1
        )
    )

    var puzzle: TheLinePuzzle? = null
    var paths: MutableMap<Int, PathState> = mutableMapOf()
    var cellsRemaining: Int = 0

    val currentLevel: Int get() = levelCounters[mode]?.get(difficulty) ?: 1

    fun loadPuzzle(p: TheLinePuzzle) {
        puzzle = p
        paths.clear()
        val totalCells = p.width * p.height
        cellsRemaining = totalCells - p.blockedIndices.size

        if (p.mode == TheLineMode.MULTI) {
            p.segments.forEachIndexed { i, seg ->
                paths[i] = PathState(
                    colorValue = seg.colorValue,
                    endpoints = listOf(seg.start, seg.end)
                )
            }
        } else {
            val ep = p.endpoints ?: return
            paths[0] = PathState(
                colorValue = THE_LINE_SINGLE_COLOR,
                endpoints = listOf(ep.first, ep.second)
            )
        }
    }

    fun isComplete(): Boolean {
        if (cellsRemaining > 0) return false
        return paths.values.all { it.complete }
    }

    fun onLevelCompleted() {
        levelCounters[mode]?.let { it[difficulty] = (it[difficulty] ?: 1) + 1 }
    }

    fun getOccupiedColors(): Map<TLCoord, Int> {
        val result = mutableMapOf<TLCoord, Int>()
        for ((_, path) in paths) {
            for (coord in path.sequence) result[coord] = path.colorValue
        }
        return result
    }

    fun isBlocked(x: Int, y: Int): Boolean {
        val p = puzzle ?: return true
        return (y * p.width + x) in p.blockedIndices
    }

    fun getEndpointColor(coord: TLCoord): Int? {
        val p = puzzle ?: return null
        if (p.mode == TheLineMode.MULTI) {
            p.segments.forEachIndexed { i, seg ->
                if (seg.start == coord || seg.end == coord) return seg.colorValue
            }
        } else {
            val ep = p.endpoints ?: return null
            if (coord == ep.first || coord == ep.second) return THE_LINE_SINGLE_COLOR
        }
        return null
    }

    fun isStartEndpoint(coord: TLCoord): Boolean {
        val p = puzzle ?: return false
        return if (p.mode == TheLineMode.MULTI)
            p.segments.any { it.start == coord }
        else
            p.endpoints?.first == coord
    }

    fun getPathIndexForEndpoint(coord: TLCoord): Int? {
        val p = puzzle ?: return null
        if (p.mode == TheLineMode.MULTI) {
            p.segments.forEachIndexed { i, seg ->
                if (seg.start == coord || seg.end == coord) return i
            }
        } else {
            val ep = p.endpoints ?: return null
            if (coord == ep.first || coord == ep.second) return 0
        }
        return null
    }

    fun getPathForCoord(coord: TLCoord): Int? {
        for ((id, path) in paths) {
            if (coord in path.sequence) return id
        }
        return null
    }

    fun clearPath(pathId: Int) {
        val path = paths[pathId] ?: return
        val freed = path.sequence.size
        cellsRemaining += freed
        path.sequence.clear()
        path.complete = false
    }

    fun addToPath(pathId: Int, coord: TLCoord): Boolean {
        val path = paths[pathId] ?: return false
        if (coord in path.sequence) return false
        path.sequence.add(coord)
        cellsRemaining = maxOf(0, cellsRemaining - 1)
        return true
    }

    fun removeLastFromPath(pathId: Int): TLCoord? {
        val path = paths[pathId] ?: return null
        if (path.sequence.isEmpty()) return null
        val removed = path.sequence.removeAt(path.sequence.lastIndex)
        cellsRemaining++
        return removed
    }

    fun setPathComplete(pathId: Int, complete: Boolean) {
        paths[pathId]?.complete = complete
    }
}
