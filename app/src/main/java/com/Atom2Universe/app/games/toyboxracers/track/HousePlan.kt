package com.Atom2Universe.app.games.toyboxracers.track

import java.util.Random

/** Plan logique version 1. Les scènes restent locales pour le moment.
 * Deux rangées de pièces bordent un couloir central est-ouest : les coordonnées
 * et la rotation permettront de raccorder la porte locale sud-ouest au couloir.
 * Ne pas modifier l'ordre des listes sans versionner la génération sauvegardée.
 */
internal data class HouseRoom(
    val kind: RoomKind,
    val circuit: CircuitKind,
    val column: Int,
    val north: Boolean
) {
    val centerX get() = column * (PrototypeTrack.ROOM_HALF_WIDTH * 2f + 12f)
    val centerZ get() = (if (north) -1f else 1f) * (PrototypeTrack.ROOM_HALF_DEPTH + 12f)
    val quarterTurns get() = if (north) 0 else 2
    val scene get() = SceneChoice(kind, circuit)
}

internal data class HousePlan(val seed: Long, val rooms: List<HouseRoom>) {
    fun room(kind: RoomKind): HouseRoom = rooms.first { it.kind == kind }

    companion object {
        const val VERSION = 1
        fun generate(seed: Long): HousePlan {
            val random = Random(seed)
            // java.util.Random + Fisher-Yates : même résultat indépendamment du runtime Kotlin.
            fun <T> shuffled(values: List<T>): List<T> = values.toMutableList().apply {
                for (i in lastIndex downTo 1) {
                    val j = random.nextInt(i + 1)
                    val previous = this[i]; this[i] = this[j]; this[j] = previous
                }
            }
            val kinds = shuffled(listOf(RoomKind.BEDROOM, RoomKind.KITCHEN, RoomKind.LIVING_ROOM,
                RoomKind.DINING_ROOM, RoomKind.OFFICE, RoomKind.BATHROOM, RoomKind.LAUNDRY, RoomKind.GARAGE))
            val circuits = shuffled(listOf(CircuitKind.FIGURE_EIGHT, CircuitKind.SLALOM,
                CircuitKind.ROLLING_HILLS, CircuitKind.DOUBLE_BUMPS, CircuitKind.HIGH_GARDEN,
                CircuitKind.SWITCHBACKS, CircuitKind.JUMP_PARADE, CircuitKind.RIBBON_RALLY))
            return HousePlan(seed, kinds.mapIndexed { i, kind -> HouseRoom(kind, circuits[i], i / 2, i % 2 == 0) })
        }
    }
}
