package com.Atom2Universe.app.games.caves.ai

/** File GL coopérative : un seul jeu de tableaux A*, un budget commun pour tous les soldats. */
internal class RouteQueue(grid: NavGrid) {
    class Request internal constructor(
        val start: Int, val goal: Int, val maxCost: Float,
        val clearance: BodyClearance?, val completed: (IntList) -> Unit,
    ) {
        var cancelled = false
    }

    private val finder = PathFinder(grid)
    private val pending = ArrayDeque<Request>()
    private var active: Request? = null
    private val output = IntList(256)
    val waitingCount: Int get() = pending.size + if (active != null) 1 else 0

    fun request(start: Int, goal: Int, maxCost: Float, clearance: BodyClearance?,
                completed: (IntList) -> Unit): Request =
        Request(start, goal, maxCost, clearance, completed).also { pending.addLast(it) }

    fun clear() {
        active = null
        pending.clear()
        output.clear()
    }

    fun update() {
        val deadline = System.nanoTime() + 1_500_000L
        // Au plus 1024 extractions, réparties par lots de 64, et environ 1,5 ms par image.
        repeat(16) {
            if (System.nanoTime() >= deadline) return
            if (active?.cancelled == true) active = null
            if (active == null) {
                while (pending.isNotEmpty() && pending.first().cancelled) pending.removeFirst()
                val next = pending.removeFirstOrNull() ?: return
                active = next
                output.clear()
                finder.begin(next.start, next.goal, next.clearance, next.maxCost)
            }
            val status = finder.advance(output, 64, deadline)
            if (status != PathFinder.Result.PENDING) {
                val finished = active!!
                active = null
                if (!finished.cancelled) finished.completed(output)
            }
        }
    }
}
