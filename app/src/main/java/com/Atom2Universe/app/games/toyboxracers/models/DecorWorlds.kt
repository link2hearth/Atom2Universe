package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.lathe
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.mesh
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.quad
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.rod
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.tube
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.v
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Grands objets originaux vus à hauteur de jouet ; les passages gardent leurs volumes séparés. */
internal object DecorWorlds {
    private const val STEEL = 0x566575
    private const val IRON = 0x2D3548
    private const val SILVER = 0xBDCFD8
    private const val WHITE = 0xEAF3ED
    private const val AMBER = 0xFFC663
    private const val CYAN = 0x65EAD5
    private const val PINK = 0xF66FA9
    private const val WOOD = 0x956141
    private const val BARK = 0x604334
    private const val ROCK = 0x51475E
    private const val RED = 0xB9474B

    private fun model(id: String, room: DecorRoom, build: DecorBuilder.() -> Unit) =
        DecorModel(id, room, DecorBuilder().apply(build).parts.toList())

    /** Fermé, six faces colorées : silhouette cristalline sans sphères ni textures. */
    private fun DecorBuilder.crystal(x: Float, y: Float, z: Float, radius: Float, height: Float,
                                     colors: IntArray) {
        for (side in 0 until 6) mesh(colors[side % colors.size]) {
            val a = side * PI.toFloat() / 3f
            val b = (side + 1) * PI.toFloat() / 3f
            val p = v(x + cos(a) * radius, y, z + sin(a) * radius)
            val q = v(x + cos(b) * radius, y, z + sin(b) * radius)
            val r = p + v(0f, height * .66f, 0f)
            val s = q + v(0f, height * .66f, 0f)
            quad(p, r, s, q)
            addAll(listOf(r, v(x + radius * .15f, y + height, z), s,
                p, q, v(x, y, z)))
        }
    }

    private fun DecorBuilder.rivets(x: Float, z: Float, bottom: Float, rows: Int,
                                    spacing: Float = 3f) {
        repeat(rows) { i -> box(x, bottom + i * spacing, z, .48f, .48f, .22f, SILVER, false) }
    }

    val mine: List<DecorModel> by lazy {
        listOf(
            model("mine.ore_cart", DecorRoom.MINE) {
                // Large wheels and a visibly open metal hopper, full of faceted ore.
                for (x in floatArrayOf(-8.2f, 8.2f)) for (z in floatArrayOf(-6f, 6f)) {
                    wheel(x, 3f, z, 3f, 1.8f, IRON)
                    wheel(x * 1.08f, 3f, z, 1.6f, .45f, SILVER)
                }
                box(0f, 4f, 0f, 16f, 2f, 18f, IRON)
                box(0f, 6f, 0f, 16f, 2f, 19f, STEEL)
                for (x in floatArrayOf(-7.6f, 7.6f)) {
                    box(x, 11f, 0f, 1.4f, 9f, 20f, 0x548A96)
                    box(x, 15.7f, 0f, 2f, .8f, 21f, SILVER)
                }
                for (z in floatArrayOf(-9.3f, 9.3f)) {
                    box(0f, 11f, z, 14.8f, 9f, 1.4f, 0x426878)
                    box(0f, 15.7f, z, 17f, .8f, 2f, SILVER)
                    for (x in floatArrayOf(-6f, 0f, 6f)) rivets(x, z * 1.08f, 8f, 3)
                }
                box(0f, 9f, 0f, 13.5f, 4f, 16f, ROCK)
                repeat(7) { i ->
                    val x = (i % 3 - 1) * 4f
                    val z = (i / 3 - 1) * 5f
                    crystal(x, 10f, z, 2.4f, 5.2f + i % 3 * 1.4f,
                        intArrayOf(0xBD79CE, 0x7D72BC, 0xE0A2E2))
                }
                box(0f, 4.8f, 11.5f, 2f, 1f, 5f, STEEL)
                cylinder(0f, 5.4f, 13.4f, .7f, 1f, AMBER)
            },
            model("mine.crystals", DecorRoom.MINE) {
                cylinder(0f, .65f, 0f, 8.5f, 1.3f, ROCK)
                crystal(0f, 1f, 0f, 3.4f, 25f, intArrayOf(0x7C66D7, 0xA58AEA, 0xC7C2FF))
                crystal(-5f, .5f, 1f, 2.5f, 15f, intArrayOf(0x328D9C, 0x62D8D7, 0xB4F4ED))
                crystal(4.9f, .4f, 3f, 2.4f, 18f, intArrayOf(0xAF539E, 0xEE83CE, 0xFFBDDD))
                crystal(3f, .4f, -5f, 2f, 12f, intArrayOf(0x328D9C, 0x62D8D7, 0xB4F4ED))
                crystal(-4f, .4f, -4f, 1.8f, 9f, intArrayOf(0x7C66D7, 0xA58AEA, 0xC7C2FF))
                for (i in 0..5) {
                    val a = i * PI.toFloat() / 3f
                    crystal(cos(a) * 7f, .1f, sin(a) * 7f, .8f, 2.8f, intArrayOf(CYAN, 0x418A9E))
                }
            },
            model("mine.timber_gate", DecorRoom.MINE) {
                // Clear opening: x [-13,13], y [0,22], along Z. No crossbar at ground level.
                for (x in floatArrayOf(-15f, 15f)) {
                    box(x, 11f, 0f, 4f, 22f, 7f, WOOD)
                    box(x * (15.5f / 15f), 1f, 0f, 5f, 2f, 8f, BARK)
                    for (y in floatArrayOf(4f, 17f, 20.3f)) {
                        box(x, y, 0f, 4.25f, .85f, 7.3f, STEEL)
                        for (dx in floatArrayOf(-1.2f, 1.2f)) rivets(x + dx, 3.8f, y, 1)
                    }
                    for (dx in floatArrayOf(-.9f, .8f))
                        box(x + dx, 11f, 3.53f, .16f, 16f, .06f, BARK, false)
                }
                box(0f, 24f, 0f, 37f, 4f, 8f, WOOD)
                box(0f, 25.4f, 4.04f, 34f, .3f, .08f, 0xB58554, false)
                for (x in floatArrayOf(-13f, 13f)) {
                    box(x, 24f, 0f, 1.2f, 4.15f, 8.2f, STEEL)
                    rivets(x, 4.22f, 23f, 2, 2f)
                }
                // Small suspended light sits entirely above the stated clear opening.
                box(0f, 22.8f, 0f, 5f, 1.4f, 4f, IRON)
                box(0f, 22.16f, 0f, 3.8f, .15f, 2.8f, AMBER, false)
            },
            model("mine.cable_drum", DecorRoom.MINE) {
                box(0f, 1f, 0f, 23f, 2f, 16f, IRON)
                for (x in floatArrayOf(-9f, 9f)) {
                    box(x, 6f, 0f, 2.5f, 10f, 10f, STEEL)
                    wheel(x, 9f, 0f, 7.5f, 1.3f, 0xB4753E)
                    wheel(x * 1.04f, 9f, 0f, 2.1f, 1.6f, SILVER)
                }
                wheel(0f, 9f, 0f, 5.6f, 16f, IRON)
                for (i in 0..10) wheel(-7.5f + i * 1.5f, 9f, 0f, 5.82f, .48f, 0x757D8B)
                box(-6f, 3.5f, 6f, 7f, 4f, 4f, AMBER)
                box(-6f, 5.55f, 6f, 5f, .12f, 2.8f, IRON, false)
                for (x in floatArrayOf(-7.3f, -5f)) cylinder(x, 5.8f, 6f, .5f, .35f, RED, false)
                rod(v(7f, 9f, 6f), v(8f, 2.5f, 7f), .3f, IRON)
            },
            model("mine.lantern", DecorRoom.MINE) {
                cylinder(0f, 1f, 0f, 5.3f, 2f, IRON)
                cylinder(0f, 2.3f, 0f, 4.6f, .6f, AMBER)
                cylinder(0f, 9.2f, 0f, 3.7f, 13f, 0xFFE1A0)
                for (i in 0 until 6) {
                    val a = i * PI.toFloat() / 3f
                    rod(v(cos(a) * 4.2f, 2.6f, sin(a) * 4.2f),
                        v(cos(a) * 4.2f, 15.8f, sin(a) * 4.2f), .35f, IRON)
                }
                cylinder(0f, 16f, 0f, 5f, 1.2f, IRON)
                cone(0f, 16.6f, 0f, 4.5f, 2.5f, STEEL)
                tube(List(13) { i ->
                    val a = i * PI.toFloat() / 12f
                    v(cos(a) * 4.4f, 19f + sin(a) * 4.4f, 0f)
                }, List(13) { .35f }, SILVER, sides = 6)
            },
            model("mine.rock_wall", DecorRoom.MINE) {
                // Staggered strata break the silhouette and offer a recognizable cave boundary.
                for (row in 0..3) for (column in 0..3) {
                    val x = -12f + column * 8f + if (row % 2 == 0) -1f else 1f
                    val h = 6.4f + (column + row) % 3 * .6f
                    crystal(x, row * 5.8f, 0f, 5.1f, h,
                        intArrayOf(0x4C4257, 0x61566B, 0x756579))
                }
                for (i in 0..3) crystal(-10f + i * 6f, 1f + i * 2f, 5f,
                    1.7f, 6f + i, intArrayOf(0x347D8D, 0x68C7C8, 0xA6EBDC))
            }
        )
    }

    val laboratory: List<DecorModel> by lazy {
        listOf(
            model("laboratory.microscope", DecorRoom.LABORATORY) {
                box(0f, 1.4f, 0f, 20f, 2.8f, 24f, WHITE)
                box(0f, 3f, 0f, 15f, .6f, 18f, STEEL)
                // Curved structural arm, separate from stage and optical tube.
                tube(listOf(v(0f, 3f, -8f), v(0f, 10f, -10f), v(0f, 22f, -10f),
                    v(0f, 30f, -5f), v(0f, 31f, 3f)),
                    listOf(3.6f, 3.4f, 3f, 2.8f, 2.5f), 0x81B9BC, sides = 8)
                box(0f, 12f, 2f, 17f, 1.4f, 15f, IRON)
                box(0f, 12.85f, 3f, 10f, .25f, 4f, CYAN, false)
                for (x in floatArrayOf(-6f, 6f)) box(x, 13.1f, 2f, .6f, .25f, 8f, SILVER, false)
                cylinder(0f, 7.5f, 3f, 3f, 6f, SILVER)
                cylinder(0f, 10.6f, 3f, 2.4f, .4f, AMBER)
                cylinder(0f, 27f, 4f, 3.6f, 12f, WHITE)
                cylinder(0f, 34f, 4f, 2.5f, 3f, IRON)
                cylinder(0f, 35.6f, 4f, 1.7f, .25f, 0x517CAA)
                cylinder(0f, 20f, 4f, 4.2f, 1.6f, STEEL)
                for (x in floatArrayOf(-2.8f, 2.8f)) cylinder(x, 17.5f, 4f, 1.15f, 4f, SILVER)
                for (x in floatArrayOf(-4f, 4f)) {
                    wheel(x, 23f, -8f, 3f, 2f, IRON)
                    wheel(x * 1.2f, 23f, -8f, 1.9f, .6f, SILVER)
                }
            },
            model("laboratory.flask", DecorRoom.LABORATORY) {
                lathe(0f, 0f, listOf(0f to 0f, 7f to 0f, 8.5f to 2f, 8.5f to 4f,
                    3f to 19f, 3f to 26f, 3.8f to 26f, 3.8f to 27f,
                    2.6f to 27f, 2.6f to 25.8f, 0f to 25.8f), 0x8CCFC7, segments = 16)
                // Opaque stylized liquid readable without transparency ordering.
                lathe(0f, 0f, listOf(0f to .3f, 8.6f to 1.8f, 8.6f to 4f,
                    5.6f to 12f, 0f to 12f), 0x48ADAF, solid = false, segments = 16)
                for (i in 0..4) box(4.2f - i * .32f, 7f + i * 2.4f,
                    5.8f - i * .84f, 2f, .24f, .25f, WHITE, false)
                box(-3.1f, 6f, 7.7f, 2f, 6f, .2f, 0xC8F4E9, false)
                cylinder(0f, 28f, 0f, 3f, 2f, 0x677A9A)
            },
            model("laboratory.test_tubes", DecorRoom.LABORATORY) {
                box(0f, .8f, 0f, 27f, 1.6f, 11f, WHITE)
                for (x in floatArrayOf(-12f, 12f)) box(x, 7f, 0f, 1.5f, 14f, 10f, 0x78A5BC)
                for (z in floatArrayOf(-4.2f, 4.2f)) {
                    box(0f, 12.5f, z, 25f, 1f, 1.3f, SILVER)
                    box(0f, 5f, z, 25f, .8f, 1.3f, SILVER)
                }
                repeat(4) { i ->
                    val x = -8.4f + i * 5.6f
                    cylinder(x, 10f, 0f, 2.1f, 16f, 0xB9E4E5)
                    val liquidHeight = 7f + i
                    cylinder(x, 2f + liquidHeight / 2f, 0f, 2.16f, liquidHeight,
                        intArrayOf(CYAN, PINK, AMBER, 0x9A88EE)[i])
                    cylinder(x, 18.4f, 0f, 2.5f, .8f, WHITE)
                    cylinder(x, 18.85f, 0f, 1.8f, .15f, 0x52747C, false)
                    box(x - .8f, 13f, 2.06f, .4f, 6f, .2f, WHITE, false)
                }
            },
            model("laboratory.coil", DecorRoom.LABORATORY) {
                box(0f, 1.5f, 0f, 19f, 3f, 19f, IRON)
                box(0f, 3.5f, 0f, 16f, 1f, 16f, WHITE)
                cylinder(0f, 13f, 0f, 3.8f, 18f, 0x755148)
                for (i in 0..11) cylinder(0f, 5f + i * 1.45f, 0f, 4.1f, .72f, 0xD69A66)
                cylinder(0f, 23f, 0f, 5f, 2f, IRON)
                lathe(0f, 0f, listOf(0f to 23f, 5f to 23f, 8.5f to 24f,
                    9.5f to 26f, 8.5f to 28f, 5f to 29f, 0f to 29f), SILVER, segments = 16)
                cylinder(0f, 30.3f, 0f, .4f, 3f, CYAN)
                for (x in floatArrayOf(-6f, 6f)) {
                    box(x, 2f, 9.55f, 2.3f, 1f, .1f, CYAN, false)
                    cylinder(x, 4.4f, 6f, .55f, .8f, AMBER)
                }
            },
            model("laboratory.workbench", DecorRoom.LABORATORY) {
                // Clear space between the legs: 28 x 18; traverse along Z.
                for (x in floatArrayOf(-16f, 16f)) for (z in floatArrayOf(-8f, 8f)) {
                    box(x, 9f, z, 4f, 18f, 3f, 0x638C9B)
                    box(x * (16.3f / 16f), .5f, z, 4.6f, 1f, 3.5f, IRON)
                }
                box(0f, 19f, 0f, 38f, 2f, 22f, IRON)
                box(0f, 20.2f, 0f, 38f, .4f, 22f, WHITE)
                box(0f, 23f, -10.5f, 38f, 5.2f, 1f, 0x92B8C4)
                for (x in floatArrayOf(-12f, -6f, 0f, 6f, 12f)) {
                    box(x, 23.2f, -9.9f, 2f, 2f, .1f, WHITE, false)
                    box(x, 23.2f, -9.8f, .3f, .8f, .1f, IRON, false)
                }
                cylinder(-11f, 24f, 1f, 3.5f, 7f, 0x91C9C8)
                cylinder(-11f, 27.7f, 1f, 3.8f, .5f, SILVER)
                cylinder(-11f, 23f, 1f, 3.6f, 3f, CYAN, false)
                box(8f, 23f, 1f, 10f, 5f, 8f, 0x7594AF)
                box(8f, 24f, 5.1f, 7f, 2.4f, .1f, IRON, false)
                for (i in 0..3) box(5.6f + i * 1.6f, 24f, 5.18f, .5f, 1.3f, .05f, CYAN, false)
                for (x in floatArrayOf(5f, 11f)) cylinder(x, 25.9f, 2f, .8f, .5f, AMBER)
            },
            model("laboratory.robot_arm", DecorRoom.LABORATORY) {
                cylinder(0f, 1.2f, 0f, 8f, 2.4f, IRON)
                cylinder(0f, 4f, 0f, 6f, 3.2f, WHITE)
                rod(v(0f, 5f, 0f), v(0f, 18f, -4f), 2.7f, 0x83A6C5)
                wheel(0f, 18f, -4f, 3.8f, 7f, WHITE)
                wheel(3.6f, 18f, -4f, 2.1f, .4f, CYAN)
                rod(v(0f, 18f, -4f), v(0f, 29f, 6f), 2.5f, WHITE)
                wheel(0f, 29f, 6f, 3.1f, 5.5f, IRON)
                wheel(2.85f, 29f, 6f, 1.7f, .4f, CYAN)
                rod(v(0f, 29f, 6f), v(0f, 23f, 12f), 1.8f, 0x83A6C5)
                box(0f, 22f, 12f, 7f, 2f, 3f, STEEL)
                for (x in floatArrayOf(-2.6f, 2.6f)) {
                    box(x, 19.8f, 12f, 1.1f, 3.5f, 2.5f, IRON)
                    box(x * .77f, 18.2f, 12f, 2.1f, .7f, 2.5f, IRON)
                }
                box(0f, 4f, 6.1f, 5f, 1f, .1f, CYAN, false)
            }
        )
    }

    val restaurant: List<DecorModel> by lazy {
        listOf(
            model("restaurant.booth", DecorRoom.RESTAURANT) {
                box(0f, 2f, 0f, 33f, 4f, 16f, IRON)
                box(0f, 6f, 0f, 34f, 4f, 17f, RED)
                box(0f, 15f, -6.5f, 34f, 20f, 4f, RED)
                box(0f, 25.2f, -6.5f, 34.5f, .5f, 4.5f, WHITE)
                for (i in 0..7) {
                    val x = -14f + i * 4f
                    box(x, 8.5f, 1f, 3.8f, 1f, 13f, 0xD36367)
                    box(x, 15.8f, -4.2f, 3.75f, 17f, .8f, 0xD36367)
                    box(x, 16f, -3.73f, .4f, .4f, .1f, AMBER, false)
                }
                for (x in floatArrayOf(-16.5f, 16.5f)) {
                    box(x, 10f, 1f, 1.2f, 7f, 15f, RED)
                    box(x, 13.7f, 1f, 1.5f, .5f, 15f, WHITE)
                }
            },
            model("restaurant.table", DecorRoom.RESTAURANT) {
                // Four separate legs, never a pedestal: clear 30-wide route under 18-high top.
                for (x in floatArrayOf(-17f, 17f)) for (z in floatArrayOf(-9f, 9f)) {
                    box(x, 9f, z, 4f, 18f, 3f, SILVER)
                    box(x * (17.1f / 17f), 1f, z, 4.2f, 2f, 3.2f, IRON)
                }
                box(0f, 19f, 0f, 40f, 2f, 25f, RED)
                box(0f, 20.15f, 0f, 40f, .3f, 25f, WHITE)
                for (x in floatArrayOf(-19.5f, 19.5f)) box(x, 19f, 0f, 1f, 1.3f, 25f, SILVER)
                for (z in floatArrayOf(-12f, 12f)) box(0f, 19f, z, 39f, 1.3f, 1f, SILVER)
                for (x in floatArrayOf(-10f, 10f)) {
                    cylinder(x, 20.55f, 1f, 5.6f, .5f, 0xDFE3DB)
                    cylinder(x, 20.85f, 1f, 4.4f, .1f, WHITE, false)
                    box(x + 6.8f, 20.6f, 1f, .5f, .3f, 7f, SILVER, false)
                }
                box(0f, 22f, -6f, 4f, 3.5f, 3f, SILVER)
                box(0f, 23.8f, -6f, 3.5f, .2f, 2.5f, WHITE, false)
                for (x in floatArrayOf(-3f, 3f)) cylinder(x, 22f, -7f, .9f, 3.4f, if (x < 0) RED else AMBER)
            },
            model("restaurant.burger", DecorRoom.RESTAURANT) {
                lathe(0f, 0f, listOf(0f to 0f, 9f to 0f, 13f to .6f,
                    14f to 1.4f, 14f to 2f, 10f to 1.4f, 0f to 1.4f), WHITE, segments = 20)
                cylinder(0f, 3f, 0f, 8.5f, 3f, 0xD58E43)
                cylinder(0f, 5.2f, 0f, 9f, 1.4f, 0x593C32)
                box(0f, 6.1f, 0f, 16f, .45f, 16f, 0xF0BA47)
                cylinder(0f, 6.8f, 0f, 8.5f, 1f, 0xDA6150)
                for (i in 0..7) {
                    val a = i * PI.toFloat() / 4f
                    val direction = v(cos(a), 0f, sin(a))
                    val side = v(-sin(a), 0f, cos(a)) * 1.8f
                    val root = direction * 4.2f + v(0f, 7.5f, 0f)
                    val tip = direction * 9.3f + v(0f, 7.5f, 0f)
                    val middle = direction * 6.8f + v(0f, 7.9f, 0f)
                    mesh(if (i % 2 == 0) 0x78A94C else 0x98BC55, false) {
                        addAll(listOf(root, middle + side, tip, root, tip, middle - side,
                            root, tip, middle + side, root, middle - side, tip))
                    }
                }
                lathe(0f, 0f, listOf(0f to 7.8f, 9f to 7.8f, 9.3f to 9f,
                    8f to 11.8f, 5.5f to 13.7f, 0f to 14.3f), 0xE4AA5F, segments = 16)
                repeat(12) { i ->
                    val a = i * 2.39996f
                    val r = 2.2f + i % 3 * 1.8f
                    box(cos(a) * r, 14.25f - r * r * .028f, sin(a) * r, .45f, .18f, .9f, WHITE, false)
                }
            },
            model("restaurant.coffee", DecorRoom.RESTAURANT) {
                cylinder(0f, .4f, 0f, 10.5f, .8f, WHITE)
                cylinder(0f, .85f, 0f, 8.5f, .1f, 0xCCD8D8, false)
                lathe(0f, 0f, listOf(0f to 1f, 4.8f to 1f, 5.5f to 2f,
                    6.5f to 13.5f, 6.5f to 14f, 5.8f to 14f,
                    5.8f to 12f, 0f to 12f), WHITE, segments = 16)
                cylinder(0f, 12.8f, 0f, 5.83f, .1f, 0x6A4437, false)
                cylinder(0f, 12.88f, 0f, 3.8f, .04f, 0xC39061, false)
                tube(List(13) { i ->
                    val a = -PI.toFloat() / 2 + i * PI.toFloat() / 12
                    v(5.8f + cos(a) * 4.7f, 8f + sin(a) * 4.2f, 0f)
                }, List(13) { 1.05f }, WHITE, sides = 8)
                box(0f, 7f, 6.02f, 5.4f, 4f, .2f, RED, false)
                box(0f, 7f, 6.18f, 3.3f, .65f, .08f, WHITE, false)
            },
            model("restaurant.cutlery", DecorRoom.RESTAURANT) {
                // A full-size folded napkin and oversized fork/knife at toy-car scale.
                box(0f, .35f, 0f, 21f, .7f, 29f, RED)
                box(0f, .75f, 0f, 18f, .1f, 26f, 0xCE6363, false)
                for (x in floatArrayOf(-8.5f, 8.5f)) box(x, .84f, 0f, .25f, .08f, 25f, WHITE, false)
                box(-4f, 1.3f, 3f, 1.8f, .8f, 17f, SILVER)
                box(-4f, 1.4f, -6.7f, 5.5f, .65f, 3f, SILVER)
                for (i in 0..3) box(-6.2f + i * 1.45f, 1.4f, -10f, .75f, .65f, 5f, SILVER)
                box(5f, 1.6f, 6f, 2.4f, 1.2f, 11f, IRON)
                box(4.5f, 1.6f, -5f, 3.4f, .7f, 13f, SILVER)
                for (z in floatArrayOf(2.5f, 8.5f)) box(5f, 2.23f, z, .55f, .08f, .55f, SILVER, false)
                for (i in 0..5) box(2.75f, 1.55f, -9.8f + i * 1.5f, .2f, .22f, .65f, STEEL, false)
            },
            model("restaurant.counter", DecorRoom.RESTAURANT) {
                box(0f, 8f, 0f, 39f, 16f, 14f, RED)
                for (i in 0..9) box(-17.5f + i * 3.9f, 8f, 7.2f, 3.55f, 12f, .4f, 0xD76966)
                box(0f, 1f, 7.5f, 40f, 1.3f, .5f, SILVER)
                box(0f, 16.5f, 0f, 43f, 1f, 18f, IRON)
                box(0f, 17.15f, 0f, 43f, .3f, 18f, WHITE)
                box(-11f, 21f, -2f, 12f, 7f, 8f, SILVER)
                box(-11f, 22f, 2.1f, 9f, 3f, .2f, IRON, false)
                for (x in floatArrayOf(-13.5f, -8.5f)) {
                    cylinder(x, 23f, 2.4f, .6f, .7f, RED, false)
                    box(x, 19f, 3f, .45f, 2f, 1.4f, SILVER)
                    cylinder(x, 18.3f, 3.2f, 1f, 1.5f, WHITE)
                }
                box(11f, 19f, 0f, 9f, 3.5f, 8f, 0x77A6A1)
                box(11f, 23f, -1f, 8f, 5f, 1f, IRON)
                box(11f, 23f, -.42f, 6.6f, 3.7f, .1f, CYAN, false)
                box(11f, 21f, 2f, 6f, .15f, 3f, WHITE, false)
                for (i in 0..3) box(8.9f + i * 1.4f, 21.12f, 2f, .65f, .08f, 2f, STEEL, false)
            }
        )
    }

    val neon: List<DecorModel> by lazy {
        listOf(
            model("neon.tower", DecorRoom.NEON_CITY) {
                box(0f, 2f, 0f, 28f, 4f, 28f, IRON)
                box(0f, 35f, 0f, 25f, 66f, 25f, 0x394564)
                box(0f, 69f, 0f, 21f, 2f, 21f, 0x586986)
                box(0f, 72f, 0f, 16f, 4f, 16f, 0x394564)
                rod(v(0f, 74f, 0f), v(0f, 81f, 0f), .35f, CYAN, false)
                for (i in 0..6) for (column in 0..2) {
                    val x = -8f + column * 8f
                    val y = 10f + i * 8f
                    val c = if ((i + column) % 4 == 0) 0x556183 else if ((i + column) % 2 == 0) CYAN else AMBER
                    for (sign in floatArrayOf(-1f, 1f)) {
                        box(x, y, sign * 12.55f, 4f, 4.8f, .1f, c, false)
                        box(sign * 12.55f, y, x, .1f, 4.8f, 4f, c, false)
                    }
                }
                for (x in floatArrayOf(-12.7f, 12.7f)) for (z in floatArrayOf(-12.7f, 12.7f))
                    box(x, 35f, z, .4f, 65f, .4f, PINK, false)
                box(0f, 5f, 12.7f, 8f, 8f, .25f, 0x7D9AAA)
                box(0f, 11f, 14f, 14f, 1f, 4f, IRON)
                box(0f, 11.6f, 15.5f, 13f, .3f, .4f, CYAN, false)
            },
            model("neon.billboard", DecorRoom.NEON_CITY) {
                for (x in floatArrayOf(-10f, 10f)) {
                    box(x, 1f, 0f, 8f, 2f, 8f, IRON)
                    box(x, 21f, 0f, 2f, 40f, 2f, STEEL)
                }
                box(0f, 30f, 0f, 34f, 22f, 3f, IRON)
                box(0f, 30f, 1.6f, 31f, 19f, .2f, 0x3C4169, false)
                for (x in floatArrayOf(-16.3f, 16.3f)) box(x, 30f, 1.9f, .5f, 21f, .4f, PINK, false)
                for (y in floatArrayOf(19.6f, 40.4f)) box(0f, y, 1.9f, 33f, .5f, .4f, CYAN, false)
                // Original abstract skyline and orbit, with no lettering, brands or screen textures.
                repeat(8) { i ->
                    val h = 2f + (i * 7 % 9)
                    box(-12f + i * 3.4f, 21f + h / 2, 1.85f, 2.4f, h, .15f,
                        if (i % 2 == 0) 0x897CDC else 0x648CCA, false)
                }
                tube(List(25) { i ->
                    val a = i * 2f * PI.toFloat() / 24
                    v(cos(a) * 6f - 2f, sin(a) * 6f + 31f, 2.15f)
                }, List(25) { .36f }, AMBER, solid = false, sides = 6)
                rod(v(-12f, 26f, 2.6f), v(11f, 36f, 2.6f), .28f, CYAN, false)
                for (i in 0..5) box(9f + i % 2 * 3f, 24f + i * 2.3f, 2.1f, .55f, .55f, .15f, WHITE, false)
            },
            model("neon.taxi", DecorRoom.NEON_CITY) {
                for (x in floatArrayOf(-9f, 9f)) for (z in floatArrayOf(-13f, 13f)) {
                    wheel(x, 4.8f, z, 4.8f, 2.5f, IRON)
                    wheel(x * 1.1f, 4.8f, z, 2.5f, .8f, SILVER)
                }
                box(0f, 5.8f, 0f, 18f, 4.5f, 37f, 0xD79834)
                box(0f, 9.5f, 0f, 20f, 4f, 38f, 0xF7BF42)
                box(0f, 14.2f, -1f, 16.5f, 6.3f, 19f, 0xF7BF42)
                box(0f, 17.7f, -1f, 17f, .7f, 20f, 0xFFD668)
                for (x in floatArrayOf(-8.34f, 8.34f)) for (z in floatArrayOf(-5.5f, 3.5f))
                    box(x, 14.5f, z, .18f, 4.3f, 7.7f, 0x638C9C, false)
                box(0f, 14.5f, 8.65f, 14.6f, 4.3f, .15f, 0x759DAA, false)
                box(0f, 14.5f, -10.65f, 14.6f, 4.3f, .15f, 0x759DAA, false)
                for (x in floatArrayOf(-10.12f, 10.12f)) {
                    box(x, 8.5f, 0f, .18f, 1.4f, 35f, IRON, false)
                    repeat(10) { i -> box(x * 1.008f, 8.5f, -15.7f + i * 3.5f, .08f, 1.25f, 1.7f, WHITE, false) }
                    for (z in floatArrayOf(-5f, 5f)) box(x, 11.2f, z, .25f, .4f, 2f, SILVER, false)
                }
                for (z in floatArrayOf(-19.3f, 19.3f)) box(0f, 5.5f, z, 19f, 1.3f, 1f, SILVER)
                for (x in floatArrayOf(-6.8f, 6.8f)) {
                    box(x, 9f, 19.15f, 4.3f, 2.1f, .35f, 0xFFF0B2)
                    box(x, 9f, -19.15f, 4.3f, 2.1f, .35f, RED)
                }
                box(0f, 8.7f, 19.25f, 6f, 2f, .3f, IRON)
                box(0f, 19f, -1f, 6f, 2f, 3f, WHITE)
                box(0f, 19f, .6f, 4.7f, 1.1f, .08f, AMBER, false)
            },
            model("neon.streetlamp", DecorRoom.NEON_CITY) {
                cylinder(0f, .8f, 0f, 3.6f, 1.6f, IRON)
                cylinder(0f, 3f, 0f, 1.8f, 3f, STEEL)
                cylinder(0f, 19f, 0f, .7f, 30f, STEEL)
                tube(listOf(v(0f, 34f, 0f), v(0f, 38f, 1f), v(0f, 40f, 5f), v(0f, 40f, 11f)),
                    listOf(.7f, .7f, .65f, .6f), STEEL, sides = 8)
                box(0f, 39.8f, 11f, 4f, 1.6f, 8f, IRON)
                box(0f, 38.95f, 11f, 3.4f, .15f, 6.8f, 0xFFE3A4, false)
                box(0f, 24f, .9f, 5.5f, 11f, .6f, 0x724E91)
                box(0f, 24f, 1.25f, 4f, 9f, .12f, PINK, false)
                box(0f, 24f, 1.36f, .4f, 6f, .08f, WHITE, false)
            },
            model("neon.hydrant", DecorRoom.NEON_CITY) {
                cylinder(0f, .8f, 0f, 4.7f, 1.6f, IRON)
                cylinder(0f, 6.6f, 0f, 3.3f, 10f, RED)
                for (y in floatArrayOf(2.5f, 10.5f)) cylinder(0f, y, 0f, 3.8f, .8f, 0xE06858)
                cone(0f, 11f, 0f, 3.7f, 3.2f, RED)
                cylinder(0f, 14.2f, 0f, .8f, .8f, SILVER)
                wheel(0f, 7.4f, 0f, 1.8f, 12f, RED)
                for (x in floatArrayOf(-6.2f, 6.2f)) {
                    wheel(x, 7.4f, 0f, 2.3f, 1f, 0xE06858)
                    wheel(x * 1.09f, 7.4f, 0f, .75f, .5f, SILVER)
                }
                for (i in 0 until 6) {
                    val a = i * PI.toFloat() / 3
                    cylinder(cos(a) * 3.8f, 1.65f, sin(a) * 3.8f, .4f, .3f, SILVER, false)
                }
                tube(List(10) { i -> v(-5.8f + i * 1.28f, 6.8f - sin(i * PI.toFloat() / 9) * 3f, 2f) },
                    List(10) { .12f }, SILVER, solid = false, sides = 5)
            },
            model("neon.kiosk", DecorRoom.NEON_CITY) {
                box(0f, 1f, 0f, 26f, 2f, 19f, IRON)
                box(0f, 8f, 0f, 24f, 14f, 17f, 0x2F6972)
                box(0f, 19f, -7.5f, 24f, 8f, 2f, 0x3E8190)
                for (x in floatArrayOf(-11f, 11f)) box(x, 20f, 0f, 2f, 12f, 17f, 0x3E8190)
                box(0f, 15.3f, 2f, 26f, .6f, 18f, SILVER)
                box(0f, 26.7f, 0f, 29f, 2f, 23f, IRON)
                box(0f, 27.9f, 0f, 30f, .4f, 24f, 0x7A6EAD)
                box(0f, 24.3f, 10f, 29f, 3f, 4f, 0xA75487)
                for (i in 0..8) box(-12.8f + i * 3.2f, 24.3f, 12.1f, 1.6f, 3f, .15f, PINK, false)
                for (i in 0..5) {
                    val x = -8f + i % 3 * 8f
                    val y = 5f + i / 3 * 6f
                    box(x, y, 8.65f, 5.5f, 4.5f, .2f, intArrayOf(AMBER, CYAN, 0xA78DEA)[i % 3], false)
                    box(x, y + .8f, 8.8f, 4f, .5f, .08f, WHITE, false)
                    box(x, y - .6f, 8.8f, 3f, .3f, .08f, IRON, false)
                }
                box(-4f, 18f, -5.9f, 8f, 4f, .1f, AMBER, false)
                cylinder(7f, 17f, 4f, 1.7f, 3f, WHITE)
                cylinder(7f, 18.6f, 4f, 1.9f, .3f, RED)
            }
        )
    }

    val all: List<DecorModel> by lazy { mine + laboratory + restaurant + neon }

    val names = mapOf(
        "mine.ore_cart" to R.string.toybox_decor_mine_ore_cart,
        "mine.crystals" to R.string.toybox_decor_mine_crystals,
        "mine.timber_gate" to R.string.toybox_decor_mine_timber_gate,
        "mine.cable_drum" to R.string.toybox_decor_mine_cable_drum,
        "mine.lantern" to R.string.toybox_decor_mine_lantern,
        "mine.rock_wall" to R.string.toybox_decor_mine_rock_wall,
        "laboratory.microscope" to R.string.toybox_decor_laboratory_microscope,
        "laboratory.flask" to R.string.toybox_decor_laboratory_flask,
        "laboratory.test_tubes" to R.string.toybox_decor_laboratory_test_tubes,
        "laboratory.coil" to R.string.toybox_decor_laboratory_coil,
        "laboratory.workbench" to R.string.toybox_decor_laboratory_workbench,
        "laboratory.robot_arm" to R.string.toybox_decor_laboratory_robot_arm,
        "restaurant.booth" to R.string.toybox_decor_restaurant_booth,
        "restaurant.table" to R.string.toybox_decor_restaurant_table,
        "restaurant.burger" to R.string.toybox_decor_restaurant_burger,
        "restaurant.coffee" to R.string.toybox_decor_restaurant_coffee,
        "restaurant.cutlery" to R.string.toybox_decor_restaurant_cutlery,
        "restaurant.counter" to R.string.toybox_decor_restaurant_counter,
        "neon.tower" to R.string.toybox_decor_neon_tower,
        "neon.billboard" to R.string.toybox_decor_neon_billboard,
        "neon.taxi" to R.string.toybox_decor_neon_taxi,
        "neon.streetlamp" to R.string.toybox_decor_neon_streetlamp,
        "neon.hydrant" to R.string.toybox_decor_neon_hydrant,
        "neon.kiosk" to R.string.toybox_decor_neon_kiosk
    )
}
