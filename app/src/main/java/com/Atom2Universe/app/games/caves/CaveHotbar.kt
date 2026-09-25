package com.Atom2Universe.app.games.caves

/** The inventory count map owns every stack; these slots record which stacks live in the bar. */
internal object CaveHotbar {
    fun restore(primary: List<Short?>, oldBuild: List<Short?>, oldGarden: List<Short?>,
                inventory: Map<Short,Int>?): List<Short?> {
        fun owned(id: Short)=inventory==null || (inventory[id] ?: 0)>0
        val seen=hashSetOf<Short>()
        val slots=MutableList<Short?>(CaveActivity.ACTIVE_SIZE) { i ->
            primary.getOrNull(i)?.takeIf { owned(it) && seen.add(it) }
        }
        // Keep existing positions. Legacy stacks which do not fit simply stay in the bag.
        for(id in oldBuild+oldGarden) {
            if(id==null || !owned(id) || id in seen) continue
            val free=slots.indexOf(null)
            if(free<0) break
            slots[free]=id;seen.add(id)
        }
        return slots
    }

    /** Bag -> slot replaces its contents; slot -> slot swaps their stacks. Counts never change. */
    fun place(slots: Array<Short?>, inventory: Map<Short,Int>, id: Short, target: Int): Boolean {
        if(target !in slots.indices || (inventory[id] ?: 0)<=0) return false
        val source=slots.indexOf(id)
        if(source==target) return true
        if(source>=0) slots[source]=slots[target]
        slots[target]=id
        return true
    }
}
