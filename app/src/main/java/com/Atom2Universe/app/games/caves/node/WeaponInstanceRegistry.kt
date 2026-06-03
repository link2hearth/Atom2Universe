package com.Atom2Universe.app.games.caves.node

internal object WeaponInstanceRegistry {

    private const val ID_START = 10000
    private const val ID_END   = 32767

    private val instances = mutableMapOf<Short, ItemInstance>()

    fun isWeapon(id: Short): Boolean = id.toInt() >= ID_START

    fun get(id: Short): ItemInstance? = instances[id]

    fun allocate(instance: ItemInstance): Short {
        for (i in ID_START..ID_END) {
            val s = i.toShort()
            if (s !in instances) {
                instances[s] = instance
                return s
            }
        }
        error("WeaponInstanceRegistry: plus d'ID disponibles (${instances.size} armes actives)")
    }

    fun free(id: Short) {
        instances.remove(id)
    }

    /** Réinjecte un ID et son instance depuis un save (sans chercher le prochain libre). */
    fun restore(id: Short, instance: ItemInstance) {
        instances[id] = instance
    }

    fun clear() {
        instances.clear()
    }

    fun snapshot(): Map<Short, ItemInstance> = instances.toMap()
}
