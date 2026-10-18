package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.MineralItems as M
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Kind
import kotlin.math.abs
import kotlin.random.Random

/** Room furniture is kept in the four corners: the five-block crossing belongs to navigation. */
internal object UndergroundSiteDressing {
    data class Room(val x: Int, val z: Int, val index: Int, val height: Int,
                    val floorY: Int = 0, val stairwell: Boolean = false)
    data class Palette(val wall: Short, val floor: Short, val trim: Short,
                       val slab: Short, val stairs: Short, val light: Short)

    fun palette(kind: Kind): Palette = when (kind) {
        Kind.ABANDONED_MINE -> Palette(COBBLESTONE, PLANK, UndergroundSites.BEAM_OAK, 2500, 2400, 2620)
        Kind.FUNGAL_MINE -> Palette(MOSSY_COBBLESTONE, 2607, UndergroundSites.BEAM, 2501, 2401, 2626)
        Kind.EMBER_FOUNDRY -> Palette(UndergroundSites.BASALT_BRICKS, 2202, BRICK_RED, 2507, 2407, 2625)
        Kind.ZOMBIE_TEMPLE -> Palette(UndergroundSites.TOMB_WEATHERED, BRICK_GREY, UndergroundSites.TOMB_SMOOTH, 2506, 2406, 2620)
        Kind.GOBLIN_LABORATORY -> Palette(BRICK_GREY, BRICK_TERRACOTTA, PLANK_JUNGLE, 2503, 2403, 2622)
        Kind.CRYSTAL_SANCTUM -> Palette(2613, QUARTZ, 2321, 2510, 2410, 2631)
        Kind.DWARVEN_MINE -> Palette(BRICK_GREY, 2202, UndergroundSites.BEAM_OAK, 2511, 2411, 2631)
        Kind.OGRE_DEN -> Palette(COBBLESTONE, GRAVEL_DIRT, PLANK_DARK, 2502, 2402, 2620)
        Kind.MUMMY_TOMB -> Palette(BRICK_SANDY, SANDSTONE, BRICK_TERRACOTTA, 2508, 2408, 2620)
        Kind.TROLL_GROVE -> Palette(BRICK_MOSS, FOREST_FLOOR, 2608, 2503, 2403, 2621)
        Kind.WRAITH_ARCHIVE -> Palette(BRICK_OBSIDIAN, 2321, BRICK_COBALT, 2509, 2409, 2622)
        Kind.SLIME_CISTERN -> Palette(BRICK_MOSS, 2202, BRICK_GREY, 2506, 2406, 2621)
    }

    fun dress(kind: Kind, salt: Long, stage: Int,
              blocks: MutableMap<UndergroundSites.Point, UndergroundSites.Cell>, rooms: List<Room>) {
        val p = palette(kind)
        val rng = Random(salt xor 0x51A7C0DEL)
        val roleOffset = rng.nextInt(4)
        for (r in rooms) {
            if (r.stairwell) continue
            fun put(x: Int, y: Int, z: Int, id: Short, meta: Int = 0) {
                blocks[UndergroundSites.Point(x, r.floorY + y, z)] = UndergroundSites.Cell(id, meta.toByte())
            }
            val arena = r.index == 9
            val variant = Math.floorMod(r.index + roleOffset, 4)
            fun cell(x: Int, y: Int, z: Int, id: Short, meta: Int = 0) {
                // All furnishing stays inside the shell. Boss clearance is wider than ordinary aisles.
                if (abs(x) > 6 || abs(z) > 4 || y > r.height) return
                if (y > 0 && (abs(x) <= (if (arena) 3 else 2) || abs(z) <= 2)) return
                put(r.x + x, y, r.z + z, id, meta)
            }
            fun column(x: Int, z: Int, height: Int, id: Short) {
                for (y in 1..height) cell(x, y, z, id)
            }
            fun table(x: Int, z: Int, id: Short = p.slab) {
                cell(x, 1, z, id, 4)
                cell(x + 1, 1, z, id, 4)
            }
            fun shelf(x: Int, z: Int, high: Int, fir: Boolean = false) {
                for (y in 1..high) cell(x, y, z,
                    if (fir) UndergroundSites.BOOKSHELF_FIR else UndergroundSites.BOOKSHELF,
                    if (x < 0) 2 else 3)
            }
            fun lamp(x: Int, z: Int, base: Short = p.trim) {
                cell(x, 1, z, base); cell(x, 2, z, p.light)
            }
            fun planter(x: Int, z: Int, plant: Short) {
                cell(x, 0, z, if (kind == Kind.FUNGAL_MINE) 2607 else MOSS)
                cell(x, 1, z, plant)
            }

            // Masonry courses, patterned borders and stone/wood ribs make silhouettes readable.
            for (x in -6..6) for (z in -4..4) {
                if (abs(x) == 6 || abs(z) == 4)
                    cell(x, 0, z, if ((x + z + r.index) % 4 == 0) p.wall else p.trim)
                else if ((x + z + r.index) % 7 == 0) cell(x, 0, z, p.floor)
            }
            for (sx in listOf(-1, 1)) for (sz in listOf(-1, 1)) {
                val x = sx * 6; val z = sz * 4
                column(x, z, minOf(4, r.height - 1), p.trim)
                cell(x - sx, minOf(4, r.height - 1), z, p.stairs, if (sx < 0) 7 else 5)
                cell(x, r.height, z - sz, p.slab, 4)
            }
            if (!arena) {
                val weathered: Short = when (kind) {
                    Kind.ABANDONED_MINE, Kind.ZOMBIE_TEMPLE -> MOSSY_COBBLESTONE
                    Kind.FUNGAL_MINE, Kind.TROLL_GROVE, Kind.SLIME_CISTERN -> MOSS
                    Kind.EMBER_FOUNDRY -> UndergroundSites.BASALT_CRACKED
                    Kind.MUMMY_TOMB -> SANDSTONE
                    Kind.WRAITH_ARCHIVE -> BRICK_COBALT
                    Kind.CRYSTAL_SANCTUM -> QUARTZ
                    else -> p.trim
                }
                for (x in -7..7) for (z in -5..5) {
                    if (abs(x) != 7 && abs(z) != 5) continue
                    for (y in listOf(1, 3, r.height)) {
                        val point = UndergroundSites.Point(r.x + x, r.floorY + y, r.z + z)
                        // Never replace a door, its frame or any carved passage.
                        if (blocks[point]?.id != p.wall) continue
                        if (y == r.height || (x + z + r.index) % 4 == 0)
                            put(point.x, y, point.z, if (y == 3) weathered else p.trim)
                    }
                }
                val timbered = kind in setOf(Kind.ABANDONED_MINE, Kind.FUNGAL_MINE, Kind.DWARVEN_MINE)
                val vaulted = kind in setOf(Kind.ZOMBIE_TEMPLE, Kind.MUMMY_TOMB,
                    Kind.CRYSTAL_SANCTUM, Kind.WRAITH_ARCHIVE)
                // Overhead ribs cross the aisles only above the four-block doorway clearance.
                if (timbered) for (z in listOf(-3, 3)) for (x in -6..6)
                    put(r.x + x, r.height, r.z + z, p.trim, 1)
                if (vaulted) for (z in listOf(-3, 3)) {
                    for (x in -5..5) put(r.x + x, r.height, r.z + z,
                        if (abs(x) <= 2) p.slab else p.stairs,
                        if (abs(x) <= 2) 4 else if (x < 0) 7 else 5)
                }
                if (kind == Kind.SLIME_CISTERN) for (z in -4..4)
                    put(r.x - 5, r.height, r.z + z, 2506, 4)
                if (kind == Kind.TROLL_GROVE) for (x in -5..5)
                    put(r.x + x, r.height, r.z - 3, if (x % 3 == 0) 2608 else MOSS)
            }
            // A room has its own motif as well as a shared faction palette.
            if (variant == 3 || arena) when (kind) {
                Kind.ABANDONED_MINE -> {
                    for (sx in listOf(-1, 1)) {
                        for (z in -4..4) if (abs(z) >= 3) cell(sx * 4, 1, z, UndergroundSites.RAIL)
                        cell(sx * 5, 1, -3, if (variant == 0) GRAVEL else UndergroundSites.CRATE_OAK)
                        if (variant == 1) cell(sx * 5, 2, -3, UndergroundSites.CRATE)
                        cell(sx * 5, 3, 4, UndergroundSites.COBWEB)
                    }
                    table(-5, 4); cell(-5, 2, 4, F.LAMP)
                    cell(5, 1, 4, UndergroundSites.BARREL_OAK)
                    if (variant == 2) { cell(-5, 1, -4, GRAVEL); cell(-4, 1, -4, STONE) }
                }
                Kind.FUNGAL_MINE -> {
                    val cap: Short = (2640 + variant).toShort()
                    column(-5, -4, 3, 2609)
                    for (x in -6..-4) for (z in -4..-3) cell(x, 4, z, cap)
                    for (x in 3..5) for (z in listOf(-4, 4))
                        planter(x, z, (2620 + (x + variant) % 7).toShort())
                    cell(-4, 1, 3, UndergroundSites.BARREL_FIR)
                    cell(-5, 2, 4, UndergroundSites.COBWEB)
                    table(4, 3); cell(4, 2, 3, 2626)
                }
                Kind.EMBER_FOUNDRY -> {
                    for (x in listOf(-5, 5)) {
                        column(x, -4, 3, UndergroundSites.BASALT_CRACKED)
                        cell(x, 4, -4, 2625)
                        cell(x, 1, 3, if (variant % 2 == 0) FURNACE else E.FORGE, if (x < 0) 2 else 3)
                        cell(x, 1, 4, E.ANVIL)
                    }
                    cell(-4, 1, -3, E.FORGE, 2); cell(-4, 2, -3, F.CRUCIBLE)
                    cell(-3, 1, -3, p.wall); cell(-3, 2, -3, F.CAST_MOLD)
                }
                Kind.ZOMBIE_TEMPLE -> {
                    for (sx in listOf(-1, 1)) {
                        for (x in 3..5) cell(sx * x, 1, -4, UndergroundSites.TOMB_BRICKS)
                        cell(sx * 4, 2, -4, p.slab)
                        cell(sx * 5, 1, 3, UndergroundSites.SKULL_ALTAR)
                        cell(sx * 5, 2, 3, 2620)
                        cell(sx * 4, 3, 4, UndergroundSites.COBWEB)
                        if (variant % 2 == 0) cell(sx * 3, 1, 4, MOSSY_COBBLESTONE)
                    }
                }
                Kind.GOBLIN_LABORATORY -> {
                    for (z in listOf(-4, 4)) {
                        shelf(-6, z, 3)
                        table(-5, z); cell(-5, 2, z, if (z < 0) 2631 else 2622)
                        cell(5, 1, z, (2680 + variant % 3).toShort())
                        cell(4, 1, z, UndergroundSites.BARREL_OAK)
                    }
                    cell(3, 1, 3, F.COOKER); cell(-4, 1, 3, UndergroundSites.CRATE)
                }
                Kind.CRYSTAL_SANCTUM -> {
                    for (sx in listOf(-1, 1)) for (sz in listOf(-1, 1)) {
                        cell(sx * 4, 1, sz * 4, 2510)
                        val crystalHeight = 2 + (variant + sx + sz + 4) % 3
                        column(sx * 5, sz * 3, crystalHeight, 2614)
                        cell(sx * 5, crystalHeight + 1, sz * 3, 2631)
                        cell(sx * 3, 0, sz * 3, BRICK_COBALT)
                    }
                }
                Kind.DWARVEN_MINE -> {
                    for (sx in listOf(-1, 1)) {
                        cell(sx * 5, 1, -3, E.FORGE, if (sx < 0) 2 else 3)
                        cell(sx * 4, 1, -3, E.ANVIL)
                        cell(sx * 5, 1, 3, UndergroundSites.CRATE_OAK)
                        cell(sx * 5, 2, 3, UndergroundSites.CRATE)
                        cell(sx * 4, 1, 4, UndergroundSites.BARREL_OAK)
                        cell(sx * 5, 2, -4, F.LAMP)
                        cell(sx * 6, 2, 4, M.id(stage, M.Form.ORE))
                    }
                }
                Kind.OGRE_DEN -> {
                    for (sx in listOf(-1, 1)) {
                        for (x in 3..5) cell(sx * x, 1, 3, p.slab, 4)
                        cell(sx * 4, 2, 3, if (variant % 2 == 0) F.COOKER else UndergroundSites.BARREL_OAK)
                        cell(sx * 5, 1, 4, p.stairs, 2)
                        cell(sx * 4, 1, -4, p.stairs, 0)
                        cell(sx * 5, 1, -3, UndergroundSites.CRATE)
                        cell(sx * 6, 2, -4, COBBLESTONE)
                    }
                }
                Kind.MUMMY_TOMB -> {
                    for (sx in listOf(-1, 1)) {
                        for (x in 3..5) cell(sx * x, 1, -3, UndergroundSites.TOMB_SMOOTH)
                        for (x in 3..5) cell(sx * x, 2, -3, 2508)
                        cell(sx * 5, 1, 4, UndergroundSites.RITUAL_ALTAR)
                        cell(sx * 4, 1, 3, UndergroundSites.BARREL_OAK)
                        cell(sx * 5, 2, 4, 2620)
                        cell(sx * 6, 3, 4, if (variant % 2 == 0) BRICK_COBALT else BRICK_TERRACOTTA)
                    }
                }
                Kind.TROLL_GROVE -> {
                    for (sx in listOf(-1, 1)) {
                        column(sx * 5, -4, r.height - 1, 2608)
                        for (x in 3..6) cell(sx * x, r.height, -4, MOSS)
                        for (z in listOf(-3, 3, 4)) planter(sx * 4, z, if ((z + variant) % 2 == 0) 2630 else 2621)
                        cell(sx * 5, 1, 4, 2609); cell(sx * 5, 2, 4, 2641)
                        cell(sx * 6, 0, 3, MUD)
                    }
                }
                Kind.WRAITH_ARCHIVE -> {
                    for (sx in listOf(-1, 1)) for (z in listOf(-4, 3, 4)) {
                        shelf(sx * 6, z, if (variant == 0) 4 else 3, true)
                        cell(sx * 5, 4, z, 2501, 4)
                    }
                    table(-5, -3, 2501); cell(-5, 2, -3, UndergroundSites.RITUAL_ALTAR)
                    lamp(5, -3, BRICK_COBALT)
                    cell(-4, 1, 4, 2401, 2); cell(4, 1, 4, 2401, 2)
                }
                Kind.SLIME_CISTERN -> {
                    // Water sits in fully enclosed one-block basins, away from the walking cross.
                    for (sx in listOf(-1, 1)) {
                        for (x in 3..5) for (z in -4..-2) {
                            put(r.x + sx * x, -1, r.z + z, BRICK_GREY)
                            if (z == -2) continue // Existing dry floor closes the front rim.
                            cell(sx * x, 0, z, if (x == 4 && z == -3) WATER else BRICK_GREY)
                        }
                        cell(sx * 4, 1, 4, UndergroundSites.VAT_FULL)
                        cell(sx * 5, 1, 3, UndergroundSites.BARREL_FIR)
                        cell(sx * 6, 2, 3, 2621)
                    }
                }
            } else when (variant) {
                0 -> {
                    // An open reception/guard gallery: tall supports and seats, no workstation.
                    for (sx in listOf(-1, 1)) {
                        column(sx * 4, -4, minOf(4, r.height - 1), p.trim)
                        cell(sx * 4, minOf(5, r.height), -4, p.light)
                        cell(sx * 4, 1, 4, p.stairs, 2)
                        cell(sx * 5, 1, 4, p.stairs, 2)
                        cell(sx * 4, 0, 3, p.trim)
                    }
                    if (kind == Kind.ABANDONED_MINE || kind == Kind.DWARVEN_MINE)
                        for (x in -5..-3) cell(x, 1, -3, UndergroundSites.RAIL_CROSSWISE)
                }
                1 -> {
                    // Supply room, burial annex or seed store, according to its inhabitants.
                    for (sx in listOf(-1, 1)) for (z in listOf(-4, 4)) when (kind) {
                        Kind.ZOMBIE_TEMPLE, Kind.MUMMY_TOMB -> {
                            cell(sx * 5, 1, z, UndergroundSites.TOMB_SMOOTH)
                            cell(sx * 4, 1, z, p.slab)
                            cell(sx * 5, 2, z, p.slab)
                            cell(sx * 6, 2, z, p.stairs, if (sx < 0) 1 else 3)
                        }
                        Kind.CRYSTAL_SANCTUM -> {
                            cell(sx * 5, 1, z, QUARTZ)
                            cell(sx * 5, 2, z, 2631)
                            cell(sx * 4, 0, z, BRICK_COBALT)
                        }
                        Kind.WRAITH_ARCHIVE -> {
                            shelf(sx * 5, z, 3, true)
                            cell(sx * 4, 1, z, 2501, 4)
                        }
                        Kind.TROLL_GROVE, Kind.FUNGAL_MINE -> {
                            planter(sx * 4, z, if (kind == Kind.TROLL_GROVE) 2630 else 2626)
                            cell(sx * 5, 1, z, UndergroundSites.BARREL_FIR)
                        }
                        else -> {
                            cell(sx * 5, 1, z, UndergroundSites.CRATE_OAK)
                            cell(sx * 5, 2, z, UndergroundSites.CRATE)
                            cell(sx * 4, 1, z, UndergroundSites.BARREL_OAK)
                        }
                    }
                }
                else -> {
                    // A distinct secondary activity gives each faction more than a single room.
                    when (kind) {
                        Kind.ABANDONED_MINE -> {
                            for (x in -6..-3) for (z in -4..-3) cell(x, 1, z, GRAVEL)
                            cell(-5, 2, -4, STONE); cell(-4, 3, -4, UndergroundSites.COBWEB)
                            table(4, 4); cell(4, 2, 4, UndergroundSites.CRATE_OAK)
                        }
                        Kind.FUNGAL_MINE -> {
                            for (sx in listOf(-1, 1)) {
                                cell(sx * 4, 1, -4, UndergroundSites.COBWEB)
                                cell(sx * 5, 1, -3, UndergroundSites.COBWEB)
                                cell(sx * 5, 3, -4, UndergroundSites.COBWEB)
                                planter(sx * 4, 4, 2626)
                            }
                        }
                        Kind.EMBER_FOUNDRY, Kind.DWARVEN_MINE -> {
                            for (sx in listOf(-1, 1)) {
                                table(if (sx < 0) -5 else 4, -3)
                                cell(sx * 5, 2, -3, E.ANVIL)
                                cell(sx * 4, 1, 4, p.wall)
                                cell(sx * 4, 2, 4, M.id(stage, M.Form.ORE))
                            }
                        }
                        Kind.ZOMBIE_TEMPLE, Kind.MUMMY_TOMB -> {
                            for (sx in listOf(-1, 1)) {
                                column(sx * 5, -4, 3, p.trim)
                                cell(sx * 5, 4, -4, p.stairs, if (sx < 0) 1 else 3)
                                cell(sx * 4, 1, 4, UndergroundSites.RITUAL_ALTAR)
                            }
                        }
                        Kind.GOBLIN_LABORATORY -> {
                            for (z in listOf(-4, 4)) {
                                shelf(-5, z, 3)
                                table(3, z)
                                cell(4, 2, z, 2631)
                                cell(5, 1, z, UndergroundSites.VAT_HALF)
                            }
                        }
                        Kind.CRYSTAL_SANCTUM -> {
                            for (sx in listOf(-1, 1)) for (sz in listOf(-1, 1)) {
                                column(sx * 5, sz * 3, 3, 2614)
                                cell(sx * 5, 4, sz * 3, 2631)
                                cell(sx * 4, 1, sz * 4, 2510)
                            }
                        }
                        Kind.OGRE_DEN -> {
                            for (sx in listOf(-1, 1)) {
                                for (x in 3..5) cell(sx * x, 1, -4, 2502)
                                cell(sx * 5, 1, 3, F.COOKER)
                                cell(sx * 4, 1, 4, UndergroundSites.BARREL_OAK)
                            }
                        }
                        Kind.TROLL_GROVE -> {
                            for (sx in listOf(-1, 1)) {
                                column(sx * 5, -4, 2, 2608)
                                cell(sx * 5, 3, -4, MOSS)
                                planter(sx * 4, -3, 2630)
                                planter(sx * 4, 3, 2621)
                                cell(sx * 5, 1, 4, 2609)
                                cell(sx * 5, 2, 4, 2641)
                            }
                        }
                        Kind.WRAITH_ARCHIVE -> {
                            table(-5, -3, 2501); table(4, 4, 2501)
                            cell(-5, 2, -3, F.LAMP); cell(4, 2, 4, F.LAMP)
                            cell(-4, 1, 4, UndergroundSites.RITUAL_ALTAR)
                            shelf(6, -4, 4, true)
                        }
                        Kind.SLIME_CISTERN -> {
                            for (sx in listOf(-1, 1)) {
                                cell(sx * 5, 1, -4, UndergroundSites.VAT_FULL)
                                cell(sx * 4, 1, -3, UndergroundSites.VAT_HALF)
                                cell(sx * 5, 1, 4, UndergroundSites.VAT_LOW)
                            }
                        }
                    }
                }
            }
            lamp(-5, 3)
            if (variant % 2 == 0) lamp(5, -3)

            // One operational hydraulic corner per industrial site. The water column is boxed
            // on the outer wall and empties into a recessed catchment; no source touches an aisle.
            if (r.index == 5 && kind in setOf(Kind.DWARVEN_MINE, Kind.GOBLIN_LABORATORY,
                    Kind.EMBER_FOUNDRY, Kind.SLIME_CISTERN)) {
                fun machine(x: Int, y: Int, z: Int, id: Short, meta: Int = 0) = put(r.x + x, y, r.z + z, id, meta)
                if (kind == Kind.DWARVEN_MINE) {
                    // A five-block wheel faces the gallery. Its edge occupies a recessed wall
                    // niche; the stream falls onto the left paddle, not into a walking route.
                    for (x in 3..7) for (z in -4..-3) for (y in 1..6)
                        machine(x, y, z, if (x == 7 && z == -3) p.wall else AIR)
                    // The rotor reaches into the old right wall. A backed, roofed niche keeps
                    // this recess enclosed even when natural caves touch the building outside.
                    for (y in 0..7) machine(8, y, -4, p.wall)
                    for (x in 7..8) {
                        machine(x, 0, -4, p.wall)
                        machine(x, 7, -4, p.wall)
                        for (y in 0..7) {
                            machine(x, y, -5, p.wall)
                            machine(x, y, -3, p.wall)
                        }
                    }
                    for (x in 2..4) for (z in -5..-3) {
                        machine(x, -1, z, p.wall)
                        machine(x, 0, z, if (x == 3 && z == -4) AIR else p.wall)
                        for (y in 6..7) machine(x, y, z, p.wall)
                    }
                    machine(3, 6, -4, WATER)
                    machine(5, 3, -4, F.LARGE_WATERWHEEL, 2)
                    machine(5, 3, -5, F.SHAFT, 2)
                    machine(5, 2, -5, p.wall)
                    // Axial output -> two meshing gears -> downward gear -> right-angle box.
                    machine(5, 3, -3, F.COGWHEEL, 2)
                    machine(4, 3, -3, F.COGWHEEL, 2)
                    machine(4, 2, -3, F.COGWHEEL, 2)
                    // Keep the driven machine in front of the gears, outside the rotor disc.
                    // The central three-block aisle remains free next to this outer margin.
                    machine(4, 2, -2, F.GEARBOX, 1)
                    machine(4, 1, -2, F.CRUSHER)
                    // Forging and storage line, opposite the hydraulic machinery.
                    for (x in 3..6) for (z in 3..4) for (y in 1..3) machine(x, y, z, AIR)
                    machine(3, 1, 3, E.FORGE, 0)
                    machine(3, 2, 3, F.CRUCIBLE)
                    machine(4, 1, 3, BRICK_GREY)
                    machine(4, 2, 3, F.CAST_MOLD)
                    machine(5, 1, 3, E.ANVIL)
                    machine(6, 1, 3, FURNACE, 0)
                    machine(6, 1, 4, UndergroundSites.CRATE_OAK)
                    machine(5, 1, 4, UndergroundSites.BARREL_OAK)
                    machine(4, 1, 4, UndergroundSites.BEAM_OAK)
                    machine(4, 2, 4, F.LAMP)
                    continue
                }
                // Remove corner furniture and retain a sealed shell around the moving wheel.
                for (x in 3..6) for (z in -4..-3) for (y in 1..r.height) machine(x, y, z, AIR)
                for (x in 5..7) for (z in -5..-2) {
                    machine(x, -1, z, p.wall)
                    machine(x, 0, z, if (x == 6 && z in -4..-3) AIR else p.wall)
                }
                // Side outlet is at z=-3; the source remains surrounded until it falls.
                for (x in 5..7) for (z in -4..-2) for (y in 5..6) machine(x, y, z, p.wall)
                machine(6, 5, -3, WATER)
                machine(6, 3, -4, F.WATERWHEEL, 1)
                machine(5, 3, -4, F.SHAFT, 1)
                machine(4, 3, -4, F.COGWHEEL, 1)
                machine(4, 2, -4, F.COGWHEEL, 1)
                machine(4, 1, -4, p.wall)
                machine(3, 2, -4, F.GEARBOX, 2)
                machine(3, 1, -4, when (kind) {
                    Kind.GOBLIN_LABORATORY -> F.PRESS
                    Kind.EMBER_FOUNDRY -> F.BELLOWS
                    else -> F.MILL
                })
                if (kind == Kind.EMBER_FOUNDRY) {
                    machine(3, 1, -3, E.FORGE)
                    machine(3, 2, -3, F.CRUCIBLE)
                }
            }
        }
    }
}
