package com.Atom2Universe.app.games.hamlet

import java.util.ArrayDeque

/** A reversible construction journal. Never restores a save or another activity's economy. */
class HamletBuildHistory(private val game: HamletGame) {
    internal data class Rewards(val xp: Int, val firstLevel: Int, val lastLevel: Int,
        val stats: Map<String, Long>, val quest: Int, val chapter: Int)
    private sealed interface Change
    private data class Bought(val obj: Obj, val coins: Long, val gems: Int, val revision: Int, val reward: Rewards) : Change
    private data class Sold(val obj: Obj, val proceeds: Long) : Change
    private data class Moved(val obj: Obj, val oldX: Int, val oldY: Int, val oldFlip: Boolean,
        val newX: Int, val newY: Int, val newFlip: Boolean) : Change
    private data class Ground(val index: Int, val before: Int, val after: Int, val cost: Long, val reward: Rewards?) : Change
    private data class Geometry(val obj: Obj, var x: Int, var y: Int, var flip: Boolean) {
        val w get() = if (flip) obj.def.d else obj.def.w
        val d get() = if (flip) obj.def.w else obj.def.d
    }
    private val stack = ArrayDeque<List<Change>>()
    private var gesture: MutableList<Change>? = null
    private var gestureDepth = 0
    private val recentIds = ArrayList<String>(9)
    val recent: List<String> get() = recentIds.toList()
    val size: Int get() = stack.size

    fun canUndo() = stack.isNotEmpty() || gesture?.isNotEmpty() == true
    fun beginGesture() { if (gesture == null) gesture = ArrayList(); gestureDepth++ }
    fun endGesture() {
        val changes = gesture ?: return
        gestureDepth--
        if (gestureDepth > 0) return
        gesture = null
        if (changes.isEmpty()) return
        stack.addLast(changes.toList())
        while (stack.size > 100 || stack.size > 1 && stack.sumOf { it.size } > 20_000) stack.removeFirst()
        game.listener?.onChanged()
    }
    /** Call on load, realm switch and new game. The nine recent tools are saved separately. */
    fun clear() { stack.clear(); gesture = null; gestureDepth = 0 }
    fun restoreRecent(ids: List<String>) {
        recentIds.clear()
        recentIds.addAll(ids.filter { Hamlet.byId[it]?.cat?.let { cat -> cat != Cat.HALL } == true }.distinct().take(9))
    }
    fun noteRecent(id: String) {
        if (Hamlet.byId[id]?.cat?.let { it == Cat.HALL } != false) return
        recentIds.remove(id); recentIds.add(0, id)
        while (recentIds.size > 9) recentIds.removeAt(recentIds.lastIndex)
        game.listener?.onChanged()
    }
    private fun record(change: Change) {
        val own = gesture == null
        if (own) beginGesture()
        gesture!!.add(change)
        if (own) endGesture()
    }
    internal fun bought(o: Obj, coins: Long, gems: Int, reward: Rewards) = record(Bought(o, coins, gems, o.economicRevision, reward))
    internal fun sold(o: Obj, proceeds: Long) = record(Sold(o, proceeds))
    internal fun moved(o: Obj, x: Int, y: Int, flip: Boolean) = record(Moved(o, x, y, flip, o.x, o.y, o.flip))
    internal fun ground(index: Int, before: Int, after: Int, cost: Long = 0, reward: Rewards? = null) =
        record(Ground(index, before, after, cost, reward))

    private fun fail(code: String): Boolean { game.listener?.onMessage(code); return false }

    /** Validate the entire inverse before touching anything, including mixed bulldozer strokes. */
    fun undo(): Boolean {
        while (gesture != null) endGesture()
        val changes = stack.peekLast() ?: return fail("undo_empty")
        val objects = game.objs.mapValuesTo(LinkedHashMap()) { (_, o) -> Geometry(o, o.x, o.y, o.flip) }
        val ground = game.paths.copyOf()
        val stats = HashMap<String, Long>()
        val grantedLevels = HashSet<Int>()
        var coins = 0L; var gems = 0L; var xp = 0L
        fun rewards(receipt: Rewards): Boolean {
            // A claimed milestone may have depended on this construction. Keep it intact.
            if (game.quest != receipt.quest || game.journey.chapter != receipt.chapter) return false
            xp += receipt.xp
            for (level in receipt.firstLevel..receipt.lastLevel) grantedLevels.add(level)
            receipt.stats.forEach { (key, amount) -> stats[key] = (stats[key] ?: 0) + amount }
            return true
        }
        for (change in changes.asReversed()) when (change) {
            is Bought -> {
                val current = objects[change.obj.id]
                if (current?.obj !== change.obj) return fail("undo_blocked")
                if (change.obj.economicRevision != change.revision || !rewards(change.reward)) return fail("undo_progress")
                objects.remove(change.obj.id); coins += change.coins; gems += change.gems
            }
            is Sold -> {
                if (objects.containsKey(change.obj.id)) return fail("undo_blocked")
                objects[change.obj.id] = Geometry(change.obj, change.obj.x, change.obj.y, change.obj.flip)
                coins -= change.proceeds
            }
            is Moved -> {
                val current = objects[change.obj.id] ?: return fail("undo_blocked")
                if (current.obj !== change.obj || current.x != change.newX || current.y != change.newY || current.flip != change.newFlip) return fail("undo_blocked")
                current.x = change.oldX; current.y = change.oldY; current.flip = change.oldFlip
            }
            is Ground -> {
                if (ground[change.index] != change.after) return fail("undo_blocked")
                ground[change.index] = change.before; coins += change.cost
                if (change.reward?.let { !rewards(it) } == true) return fail("undo_progress")
            }
        }
        if (xp > game.xp || stats.any { (key, n) -> (game.stats[key] ?: 0) < n }) return fail("undo_progress")
        val resultingLevel = Hamlet.levelForXp(game.xp - xp)
        for (level in resultingLevel + 1..game.level()) {
            // Never revoke a level reward earned by a harvest or battle after the placement.
            if (level !in grantedLevels) return fail("undo_progress")
            gems -= Hamlet.gemsForLevel(level)
        }
        if (game.coins + coins < 0 || game.gems.toLong() + gems !in 0..Int.MAX_VALUE.toLong()) return fail("undo_funds")
        val occupancy = IntArray(game.occ.size)
        for ((id, entry) in objects) {
            if (!game.inRealm(entry.x, entry.y, entry.w, entry.d)) return fail("undo_blocked")
            for (y in entry.y until entry.y + entry.d) for (x in entry.x until entry.x + entry.w) {
                val index = game.idx(x, y)
                if (occupancy[index] != 0) return fail("undo_blocked")
                if (entry.obj.def.onWater && ground[index] != Hamlet.PATH_WATER || !entry.obj.def.onWater && ground[index] != 0) return fail("undo_blocked")
                occupancy[index] = id
            }
        }
        game.coins += coins; game.gems = (game.gems + gems).toInt(); game.xp -= xp
        stats.forEach { (key, n) -> game.stats[key] = game.stats.getValue(key) - n }
        game.objs.clear()
        objects.forEach { (id, entry) ->
            entry.obj.x = entry.x; entry.obj.y = entry.y; entry.obj.flip = entry.flip
            game.objs[id] = entry.obj
        }
        ground.copyInto(game.paths); occupancy.copyInto(game.occ)
        game.currentQuest()?.let { if (!game.questDone(it)) game.questNoted = -1 }
        stack.removeLast(); game.bump(); game.listener?.onChanged(); game.listener?.onMessage("undone")
        return true
    }
}
