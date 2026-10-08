package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.hypot

/** A lake with actual disconnected land contours, rather than narrow fairways around hazards. */
object ArchipelagoCourse {
    private fun island(x: Int, z: Int, rx: Int, rz: Int, rotation: Float = 0f) =
        GolfHazard(x.toFloat(), z.toFloat(), rx.toFloat(), rz.toFloat(), GolfLie.FAIRWAY,
            rotation, .08f, x * .031f + z * .017f)

    private fun hole(number: Int, par: Int, length: Int, finish: Int, radius: Int,
        first: List<GolfHazard> = emptyList(), second: List<GolfHazard> = emptyList()): ClassicHole {
        val greenIsland = island(finish, length, radius + 14, radius + 17, number * .2f)
        val teeIsland = island(0, 0, 17, 22)
        val attacks = buildList {
            if (par == 3) {
                val distance = hypot(finish.toFloat(), length.toFloat())
                val clubs = GolfClub.entries.filter { it != GolfClub.PUTTER && it.carry >= distance }.takeLast(2)
                clubs.forEach { add(GolfAttackLanding(finish.toFloat(), length.toFloat(), it)) }
            } else {
                for (target in first) for (club in listOf(GolfClub.WOOD3, GolfClub.DRIVER))
                    add(GolfAttackLanding(target.x, target.z, club))
                for (target in second) {
                    val from = first.minBy { hypot(target.x - it.x, target.z - it.z) }
                    for (club in listOf(GolfClub.WOOD3, GolfClub.DRIVER))
                        add(GolfAttackLanding(target.x, target.z, club, from.x, from.z))
                }
            }
        }
        return ClassicHole(number, par, length.toFloat(), finishX = finish.toFloat(), width = 360f,
            greenRadius = radius.toFloat(), elevation = 2.4f,
            route = listOf(GolfRouteNode(0f, 0f, 0f), GolfRouteNode(finish.toFloat(), length.toFloat(), 0f)),
            fairwayStart = 0f, landingZ = first.firstOrNull()?.z ?: length.toFloat(),
            islands = listOf(teeIsland) + first + second + greenIsland,
            greenShape = GolfGreenShape(aspect = 1f, rotation = number * .2f, shape = .06f,
                slopeX = if (number % 2 == 0) -.007f else .007f, slopeZ = .009f),
            attackLandings = attacks)
    }

    val holes = listOf(
        hole(1,4,325,8,16, listOf(island(-18,180,29,33), island(65,200,23,28))),
        hole(2,5,495,-12,15, listOf(island(0,175,31,34), island(-78,150,25,28), island(70,200,20,25)),
            listOf(island(-10,350,29,34), island(-75,325,22,28), island(70,375,21,27))),
        hole(3,3,125,12,14),
        hole(4,4,350,-18,15, listOf(island(12,190,27,33), island(-70,175,33,30), island(78,210,19,24))),
        hole(5,4,285,0,17, listOf(island(-45,155,32,30), island(45,175,21,25))),
        hole(6,5,530,20,16, listOf(island(-12,185,29,33), island(72,200,24,30)),
            listOf(island(15,365,30,35), island(-70,350,24,29), island(85,385,20,25))),
        hole(7,3,150,-15,12),
        hole(8,4,365,22,14, listOf(island(5,190,30,34), island(-80,165,24,28), island(75,205,21,25))),
        hole(9,4,340,-8,16, listOf(island(-25,175,26,32), island(58,195,31,29))),
        hole(10,4,310,18,17, listOf(island(0,170,33,32), island(-75,145,21,26), island(75,190,22,27))),
        hole(11,4,375,-22,14, listOf(island(-10,195,26,33), island(-82,180,30,28), island(72,205,20,26))),
        hole(12,3,170,8,15),
        hole(13,4,295,-12,13, listOf(island(-55,165,22,26), island(40,155,34,31))),
        hole(14,4,355,15,16, listOf(island(15,185,29,34), island(-72,170,23,27), island(85,210,19,24))),
        hole(15,5,545,-18,14, listOf(island(10,195,28,33), island(-75,180,25,28), island(75,205,20,26)),
            listOf(island(-5,385,29,34), island(-80,360,23,28), island(72,395,21,26))),
        hole(16,3,190,-10,13),
        hole(17,4,360,10,15, listOf(island(-18,190,25,32), island(-85,170,31,29), island(65,205,20,25))),
        hole(18,5,520,0,17, listOf(island(0,180,32,35), island(-78,160,24,28), island(72,205,20,25)),
            listOf(island(12,370,30,35), island(-72,350,25,29), island(82,390,19,25)))
    )
}
