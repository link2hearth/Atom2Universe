package com.Atom2Universe.app.games.roguelike

import java.util.PriorityQueue

/** Select only the next 50 ranked items. Never sort or materialize the whole bag. */
internal class InventoryBagPager(
    private val bag: List<Equipment>,
    private val recent: Boolean,
    private val archetype: Archetype?,
    private val accepts: (Equipment) -> Boolean,
) {
    companion object { const val PAGE_SIZE = 50 }
    private data class Entry(val index: Int, val item: Equipment, val score: Int)
    private val scores = mutableMapOf<Int, Int>()
    private val order = compareByDescending<Entry> { if (recent) it.item.lootId else it.score.toLong() }
        .thenByDescending { it.item.lootId }.thenBy { it.index }
    private val loaded = mutableListOf<Equipment>()
    private var cursor: Entry? = null
    private var initialized = false
    var total = 0
        private set

    /** Includes matching items beyond the pages already displayed. No icons or scores loaded. */
    fun matchingItems(): Sequence<Equipment> = bag.asSequence().filter(accepts)

    fun firstPage(): List<Equipment> {
        if (!initialized) appendPage()
        return loaded.take(PAGE_SIZE)
    }

    fun through(count: Int): List<Equipment> {
        while (loaded.size < count && loaded.size < total) appendPage()
        return loaded.take(count)
    }

    private fun appendPage() {
        val first = !initialized
        initialized = true
        if (first) total = 0
        val best = PriorityQueue(PAGE_SIZE, order.reversed())
        bag.forEachIndexed { index, item ->
            if (!accepts(item)) return@forEachIndexed
            if (first) total++
            val entry = Entry(index, item, if (recent) 0 else scores.getOrPut(index) { LootSystem.rating(item, archetype) })
            if (cursor?.let { order.compare(entry, it) <= 0 } == true) return@forEachIndexed
            if (best.size < PAGE_SIZE) best.add(entry)
            else if (order.compare(entry, best.peek()) < 0) { best.poll(); best.add(entry) }
        }
        val page = best.toList().sortedWith(order)
        loaded.addAll(page.map { it.item })
        cursor = page.lastOrNull() ?: cursor
    }
}
