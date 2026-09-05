package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement

/**
 * Applique enfin les poses préparées par [HouseRoom] : un étage complet, quatre
 * pièces fixes reliées par un couloir central est-ouest. Mur affiché et mur
 * collisionnable proviennent toujours de la même liste de [RoomBox] — jamais
 * deux chemins séparés, pour ne pas reproduire le bug de téléportation déjà
 * rencontré au croisement du grand huit quand rendu et collision divergeaient.
 *
 * Les pièces sont fixes (pas tirées de la seed de [HousePlan]) car le parcours
 * signature a besoin de savoir précisément où se trouve chaque meuble.
 */
internal object HouseGeometry {
    val rooms: List<HouseRoom> = listOf(
        HouseRoom(RoomKind.BEDROOM, CircuitKind.HOUSE_GROUND_FLOOR, column = 0, north = true),
        HouseRoom(RoomKind.OFFICE, CircuitKind.HOUSE_GROUND_FLOOR, column = 0, north = false),
        HouseRoom(RoomKind.LIVING_ROOM, CircuitKind.HOUSE_GROUND_FLOOR, column = 1, north = true),
        HouseRoom(RoomKind.KITCHEN, CircuitKind.HOUSE_GROUND_FLOOR, column = 1, north = false)
    )

    private const val DOOR_HALF_WIDTH = 7.5f
    private const val DOOR_LOCAL_X = -73f
    private const val WALL_COLOR = 0xE8DFC8
    const val CORRIDOR_HALF_DEPTH = 12f

    /** Bornes est-ouest du couloir : du bord de la colonne la plus à l'ouest à
     * celui de la colonne la plus à l'est. */
    fun corridorBounds(): Pair<Float, Float> {
        val columnSpan = PrototypeTrack.ROOM_HALF_WIDTH * 2f + 12f
        val minColumn = rooms.minOf { it.column }
        val maxColumn = rooms.maxOf { it.column }
        return (minColumn * columnSpan - PrototypeTrack.ROOM_HALF_WIDTH) to
            (maxColumn * columnSpan + PrototypeTrack.ROOM_HALF_WIDTH)
    }

    private fun HouseRoom.toWorldX(localX: Float) = centerX + if (quarterTurns == 2) -localX else localX
    private fun HouseRoom.toWorldZ(localZ: Float) = centerZ + if (quarterTurns == 2) -localZ else localZ

    private fun HouseRoom.transform(box: RoomBox) = RoomBox(
        toWorldX(box.x), box.y, toWorldZ(box.z), box.width, box.height, box.depth, box.color
    )

    private fun HouseRoom.transform(placement: DecorPlacement) = placement.copy(
        x = toWorldX(placement.x), z = toWorldZ(placement.z),
        quarterTurns = (placement.quarterTurns + quarterTurns) % 4
    )

    private fun HouseRoom.transform(toy: ToyObstacle) = toy.copy(x = toWorldX(toy.x), z = toWorldZ(toy.z))

    /** La porte sud-ouest, déjà réservée par RoomDecor/RaceLayouts.opening() pour
     * ce raccordement, devient ici un vrai passage plutôt qu'un décor fermé. */
    private fun isDoorBox(box: RoomBox) = box.z > 73f && box.x < -60f
    private fun isWindowBox(box: RoomBox) = box.z < -70f && box.y >= 13f && box.x in -40f..42f

    private fun wallsFor(room: HouseRoom): List<RoomBox> {
        val halfWidth = PrototypeTrack.ROOM_HALF_WIDTH
        val halfDepth = PrototypeTrack.ROOM_HALF_DEPTH
        val height = PrototypeTrack.ROOM_WALL_HEIGHT
        val thickness = 1.5f
        val doorLeft = DOOR_LOCAL_X - DOOR_HALF_WIDTH
        val doorRight = DOOR_LOCAL_X + DOOR_HALF_WIDTH
        return buildList {
            add(RoomBox(0f, height * .5f, -halfDepth - thickness * .5f,
                halfWidth * 2f + thickness * 2f, height, thickness, WALL_COLOR))
            add(RoomBox(-halfWidth - thickness * .5f, height * .5f, 0f, thickness, height, halfDepth * 2f, WALL_COLOR))
            add(RoomBox(halfWidth + thickness * .5f, height * .5f, 0f, thickness, height, halfDepth * 2f, WALL_COLOR))
            // Mur sud coupé en deux de part et d'autre de la porte du couloir.
            val leftWidth = doorLeft - (-halfWidth)
            if (leftWidth > 0.5f) add(RoomBox(-halfWidth + leftWidth * .5f, height * .5f,
                halfDepth + thickness * .5f, leftWidth, height, thickness, WALL_COLOR))
            val rightWidth = halfWidth - doorRight
            if (rightWidth > 0.5f) add(RoomBox(halfWidth - rightWidth * .5f, height * .5f,
                halfDepth + thickness * .5f, rightWidth, height, thickness, WALL_COLOR))
        }.map { room.transform(it) }
    }

    private fun endCapWalls(): List<RoomBox> {
        val (minX, maxX) = corridorBounds()
        val height = PrototypeTrack.ROOM_WALL_HEIGHT
        val thickness = 1.5f
        return listOf(
            RoomBox(minX - thickness * .5f, height * .5f, 0f, thickness, height, CORRIDOR_HALF_DEPTH * 2f, WALL_COLOR),
            RoomBox(maxX + thickness * .5f, height * .5f, 0f, thickness, height, CORRIDOR_HALF_DEPTH * 2f, WALL_COLOR)
        )
    }

    private fun furnitureBoxesFor(room: HouseRoom): List<RoomBox> {
        val base = if (room.kind == RoomKind.BEDROOM) {
            RoomDecor.solids.filterNot(::isDoorBox)
        } else {
            RoomThemes.boxes(room.kind) + RoomDecor.boxes.filter(::isWindowBox)
        }
        return base.map { room.transform(it) }
    }

    fun wallBoxes(): List<RoomBox> = rooms.flatMap(::wallsFor) + endCapWalls()

    fun furnitureBoxes(): List<RoomBox> = rooms.flatMap(::furnitureBoxesFor)

    /** Commode existante réutilisée telle quelle ; étagère-escabeau et armoire
     * sont les deux seuls nouveaux meubles ajoutés pour ce parcours. */
    fun furnitureDecorations(): List<DecorPlacement> = rooms.flatMap { room ->
        val base = when (room.kind) {
            RoomKind.OFFICE -> RoomThemes.decorations(RoomKind.OFFICE) + listOf(
                DecorPlacement(DecorCatalog["office.shelf_ladder"], 34f, 0f, -67f),
                DecorPlacement(DecorCatalog["bedroom.wardrobe"], 75f, 0f, -67f)
            )
            RoomKind.BEDROOM -> emptyList()
            else -> RoomThemes.decorations(room.kind)
        }
        base.map { room.transform(it) }
    }

    /** Les jouets de la chambre historique, translatés sur la chambre de la maison. */
    fun toys(): List<ToyObstacle> = rooms.filter { it.kind == RoomKind.BEDROOM }.flatMap { room ->
        RaceLayouts.toys(SceneChoice(RoomKind.BEDROOM, CircuitKind.FIGURE_EIGHT)).map { room.transform(it) }
    }
}
