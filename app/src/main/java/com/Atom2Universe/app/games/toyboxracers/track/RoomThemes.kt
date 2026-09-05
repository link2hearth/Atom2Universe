package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement

internal enum class FloorKind { PARQUET, TILES, CONCRETE }
internal data class RoomTheme(val floor: FloorKind, val floorColor: Int, val accent: Int, val wall: Int)

/** Mobilier au pourtour : réserve centrale commune aux huit circuits et accès à
 * la porte sud-ouest conservé pour le futur raccordement au couloir. */
internal object RoomThemes {
    fun boxes(room: RoomKind): List<RoomBox> = buildList {
        if (room == RoomKind.OFFICE) {
            add(RoomBox(-12f, 10.6f, -66f, 26f, 1f, 12f, 0xDAB68B))
            for (x in floatArrayOf(-23f, -1f)) for (z in floatArrayOf(-70f, -62f))
                add(RoomBox(x, 5.05f, z, 1.2f, 10.1f, 1.2f, 0xABBBD5))
        }
        if (room == RoomKind.GARAGE) {
            // Porte sectionnelle fermée sur le mur est, distincte de la porte du couloir.
            add(RoomBox(117.7f, 13f, 0f, .4f, 26f, 50f, 0x485065))
            repeat(8) { row ->
                add(RoomBox(117.35f, 1.65f + row * 3.2f, 0f, .3f, 3f, 48f, 0xB8C9CD))
            }
            add(RoomBox(117f, 4f, 0f, .4f, .6f, 5f, 0x485065))
        }
    }

    fun theme(room: RoomKind): RoomTheme = when (room) {
        RoomKind.BEDROOM -> RoomTheme(FloorKind.PARQUET, 0xDAB68B, 0xF4C8D6, 0xFFF0D1)
        RoomKind.KITCHEN -> RoomTheme(FloorKind.TILES, 0xEBF2DB, 0xBDDECC, 0xEDF5DA)
        RoomKind.LIVING_ROOM -> RoomTheme(FloorKind.PARQUET, 0xCBA47C, 0xA5C8B2, 0xF5ECD8)
        RoomKind.DINING_ROOM -> RoomTheme(FloorKind.PARQUET, 0xBF956C, 0xD7AE94, 0xFFF0D1)
        RoomKind.OFFICE -> RoomTheme(FloorKind.PARQUET, 0xC8B19B, 0xABBBD5, 0xE8EAF2)
        RoomKind.BATHROOM -> RoomTheme(FloorKind.TILES, 0xE7F3F6, 0x94C9D6, 0xDAEDF2)
        RoomKind.LAUNDRY -> RoomTheme(FloorKind.TILES, 0xEEEAF4, 0xB8ADD1, 0xF1EDF6)
        RoomKind.GARAGE -> RoomTheme(FloorKind.CONCRETE, 0xA7ABB2, 0x7E8D9E, 0xCED2D5)
    }

    fun decorations(room: RoomKind): List<DecorPlacement> = buildList {
        fun place(id: String, x: Float, z: Float, scale: Float = 1f, y: Float = 0f, turn: Int = 0) {
            add(DecorPlacement(DecorCatalog[id], x, y, z, turn, scale))
        }
        when (room) {
            RoomKind.BEDROOM, RoomKind.KITCHEN -> Unit // Implantations historiques dans RaceLayouts.
            RoomKind.LIVING_ROOM -> {
                place("living.sofa", -18f, -67f, 1.25f)
                place("living.armchair", 14f, -67f, 1.25f)
                place("living.floor_lamp", -40f, -67f, 1.3f)
                place("living.plant", 34f, -67f, 1.5f)
                place("living.tv_cabinet", 0f, 68f, 1.4f, turn = 2)
                place("living.coffee_table", -30f, 67f)
                place("living.books", -30f, 67f, y = 4.795f)
                place("living.plant", 32f, 68f, 1.4f)
            }
            RoomKind.DINING_ROOM -> {
                place("living.dining_table", 0f, -65f, 1.1f)
                place("living.dining_chair", -18f, -65f, turn = 1)
                place("living.dining_chair", 18f, -65f, turn = 3)
                place("kitchen.fruit_bowl", 0f, -65f, y = 12.21f)
                place("office.dresser", -20f, 68f, 1.3f, turn = 2)
                place("office.dresser", 0f, 68f, 1.3f, turn = 2)
                place("living.plant", 30f, 67f, 1.6f)
                place("living.floor_lamp", -38f, -68f, 1.3f)
            }
            RoomKind.OFFICE -> {
                place("office.computer", -12f, -66f, y = 11.1f)
                place("office.pc_tower", 3f, -66f)
                place("living.dining_chair", -12f, -55f, turn = 2)
                place("living.books", -34f, -67f)
                place("office.dresser", 24f, -67f, 1.25f)
                place("living.books", 24f, -67f, y = 14.0625f)
                place("office.exercise_bike", -24f, 67f, 1.2f, turn = 2)
                place("office.dresser", 0f, 68f, 1.3f, turn = 2)
                place("living.plant", 28f, 67f, 1.5f)
            }
            RoomKind.BATHROOM -> {
                place("bathroom.bathtub", -23f, -66f, 1.1f, turn = 1)
                place("bathroom.vanity", 5f, -67f, 1.25f)
                place("bathroom.mirror", 5f, -73f, 1.2f, y = 15f)
                place("bathroom.shower", 30f, -67f)
                place("bathroom.toilet", -20f, 68f, 1.2f, turn = 2)
                place("bathroom.toilet_paper", -12f, 68f)
                place("bathroom.towel_rack", 10f, 68f, 1.3f, turn = 2)
                place("bathroom.laundry_basket", 28f, 68f, 1.3f)
            }
            RoomKind.LAUNDRY -> {
                place("bathroom.washer", -28f, -67f, 1.3f)
                place("bathroom.washer", -13f, -67f, 1.3f)
                place("kitchen.sink", 7f, -67f, 1.3f)
                place("office.dresser", 29f, -67f, 1.2f)
                place("bathroom.laundry_basket", -24f, 66f, 1.4f)
                place("bathroom.laundry_basket", -12f, 67f, 1.1f)
                place("bathroom.towel_rack", 5f, 67f, 1.4f)
                place("bathroom.towel_rack", 19f, 67f, 1.4f)
                place("garage.storage_rack", 38f, 68f, turn = 2)
            }
            RoomKind.GARAGE -> {
                place("garage.workbench", -22f, -67f, 1.2f)
                place("garage.toolbox", -26f, -65f, y = 12f)
                place("garage.tool_chest", 1f, -67f, 1.3f)
                place("garage.storage_rack", 26f, -67f, 1.2f)
                place("garage.tires", -30f, 67f, 1.4f)
                place("garage.crate", -14f, 68f, 1.4f)
                place("garage.crate", -14f, 68f, 1.1f, y = 7.14f)
                place("garage.storage_rack", 9f, 68f, 1.1f, turn = 2)
                place("garage.cone", 31f, 68f, 1.3f)
                place("garage.cone", 40f, 68f)
            }
        }
    }
}
