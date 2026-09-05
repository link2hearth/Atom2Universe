package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement

internal enum class RoomKind { BEDROOM, KITCHEN, LIVING_ROOM, DINING_ROOM, OFFICE, BATHROOM, LAUNDRY, GARAGE }
internal enum class CircuitKind {
    FIGURE_EIGHT, SLALOM, ROLLING_HILLS, DOUBLE_BUMPS, HIGH_GARDEN, SWITCHBACKS, JUMP_PARADE, RIBBON_RALLY,
    FURNITURE_TRAIL, WORKSHOP_EXPEDITION, HOUSE_GROUND_FLOOR;

    val usesSculptedLayout get() = ordinal in ROLLING_HILLS.ordinal..RIBBON_RALLY.ordinal
    val usesFurnitureLayout get() = this == FURNITURE_TRAIL || this == WORKSHOP_EXPEDITION || this == HOUSE_GROUND_FLOOR
    /** Seul ce circuit couvre plusieurs pièces à la fois : mur unique désactivé,
     * rendu par pièce, mobilier et jouets propres à la maison. */
    val usesHouseLayout get() = this == HOUSE_GROUND_FLOOR
    val usesPeripheralDecor get() = usesSculptedLayout || usesFurnitureLayout
}
internal data class SceneChoice(val room: RoomKind = RoomKind.BEDROOM, val circuit: CircuitKind = CircuitKind.FIGURE_EIGHT)

/** Implantations dessinées avec la piste : les meubles peuvent l'enjamber, jamais la boucher. */
internal object RaceLayouts {
    private fun bedroomBox(box: RoomBox, circuit: CircuitKind): RoomBox {
        // Le lit quitte la ligne droite sud et rejoint le mur ouest sur le slalom.
        return if (circuit == CircuitKind.SLALOM && box.x in -36f..-10f && box.z > 50f)
            box.copy(x = box.x - 78f, z = box.z - 62f) else box
    }

    fun boxes(scene: SceneChoice): List<RoomBox> = when {
        scene.circuit.usesHouseLayout -> HouseGeometry.wallBoxes() + HouseGeometry.furnitureBoxes()
        scene.room == RoomKind.BEDROOM -> RoomDecor.boxes.map { bedroomBox(it, scene.circuit) }
        else -> RoomDecor.boxes.filter(::opening) + RoomThemes.boxes(scene.room)
    }

    fun solids(scene: SceneChoice): List<RoomBox> = when {
        scene.circuit.usesHouseLayout -> HouseGeometry.wallBoxes() + HouseGeometry.furnitureBoxes()
        scene.room == RoomKind.BEDROOM -> RoomDecor.solids.map { bedroomBox(it, scene.circuit) }
        else -> RoomDecor.solids.filter(::opening) + RoomThemes.boxes(scene.room)
    }

    private fun opening(box: RoomBox) =
        (box.z < -70f && box.y >= 13f && box.x in -40f..42f) || (box.z > 73f && box.x < -60f)

    fun decorations(scene: SceneChoice): List<DecorPlacement> = buildList {
        if (scene.circuit.usesHouseLayout) {
            addAll(HouseGeometry.furnitureDecorations())
            return@buildList
        }
        addAll(RoomThemes.decorations(scene.room))
        if (scene.circuit.usesFurnitureLayout) addAll(OrganicCircuits.decorations(scene))
        fun place(id: String, x: Float, z: Float, scale: Float = 1f, y: Float = 0f, turn: Int = 0) {
            add(DecorPlacement(DecorCatalog[id], x, y, z, turn, scale))
        }
        if (scene.room == RoomKind.KITCHEN) {
            place("kitchen.fridge", -48f, -68.5f, 1.35f)
            place("kitchen.counter", -32f, -67f, 1.4f)
            place("kitchen.sink", -15f, -67f, 1.4f)
            place("kitchen.oven", 0f, -67f, 1.4f)
            place("kitchen.counter", 16f, -67f, 1.4f)
            place("kitchen.microwave", -32f, -67f, 1.1f, 13.44f)
            place("kitchen.toaster", 14f, -67f, 1.2f, 13.44f)
            place("kitchen.kettle", 20f, -67f, 1.1f, 13.44f)
            val islandX = if (scene.circuit.usesPeripheralDecor) -105f else if (scene.circuit == CircuitKind.SLALOM) 35f else -65f
            place("kitchen.island", islandX, 0f, 1.15f)
            place("kitchen.fruit_bowl", islandX, 0f, 1.15f, 11.68f)
            place("office.dresser", 28f, 70f, 1f, turn = 2)
            if (scene.circuit == CircuitKind.FIGURE_EIGHT) {
                place("living.dining_table", 55f, 0f, 1.7f)
                place("living.dining_chair", 55f, 17f, 1.5f, turn = 2)
                place("living.plant", 0f, 66f, 1.7f)
            }
        }
        if (scene.circuit == CircuitKind.SLALOM && scene.room in listOf(RoomKind.BEDROOM, RoomKind.KITCHEN)) {
            // Ligne droite sous le plateau ; les deux rangées de pieds encadrent le ruban.
            place("living.dining_table", -15f, -44f, 1.65f)
            // Centre de l'épingle est : demi-tour autour du pot, pas au travers.
            place("living.plant", 64f, -28f, 1.8f)
        }
    }

    fun toys(scene: SceneChoice): List<ToyObstacle> {
        if (scene.circuit.usesHouseLayout) return HouseGeometry.toys()
        if (scene.room != RoomKind.BEDROOM) return emptyList()
        if (scene.circuit.usesPeripheralDecor) return listOf(
            ToyObstacle(ToyKind.BLOCKS, -105f, 25f, 6.5f, 7f),
            ToyObstacle(ToyKind.TEDDY, 105f, if (scene.circuit.usesFurnitureLayout) -54f else 0f, 5.5f, 10f),
            ToyObstacle(ToyKind.TRAIN, 25f, -62f, 8f, 7f),
            ToyObstacle(ToyKind.SPINNING_TOP, 0f, 62f, 5f, 8f)
        )
        val slalom = scene.circuit == CircuitKind.SLALOM
        return listOf(
            ToyObstacle(ToyKind.BLOCKS, if (slalom) -45f else -65f, if (slalom) 32f else 0f, 6.5f, 7f),
            ToyObstacle(ToyKind.TEDDY, 65f, 0f, 5.5f, 10f),
            ToyObstacle(ToyKind.TRAIN, 25f, -62f, 8f, 7f),
            ToyObstacle(ToyKind.SPINNING_TOP, 0f, if (slalom) 68f else 62f, 5f, 8f)
        )
    }
}
