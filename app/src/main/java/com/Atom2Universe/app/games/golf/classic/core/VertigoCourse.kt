package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*
import kotlin.random.Random

/**
 * Expert course: broad outside loops versus detached, 18–22 m wide carry receptions.
 * Carries stay around 180–210 m, so precision and club choice, rather than extra power, matter.
 * Every reception is checked with two real clubs; the final attack approaches are also playable.
 * Coordinates are mirrored per hole, not the equipment or the physics used by other courses.
 */
object VertigoCourse {
    private fun n(x: Int, z: Int, width: Int) = GolfRouteNode(x.toFloat(), z.toFloat(), width.toFloat())
    private fun obstacle(x: Int, z: Int, rx: Int, rz: Int, lie: GolfLie) =
        GolfHazard(x.toFloat(), z.toFloat(), rx.toFloat(), rz.toFloat(), lie, shape = .06f,
            shoreline = if (lie == GolfLie.WATER) GolfShoreline.at(x.toFloat(), z.toFloat()) else null)

    /** A small elongated reception, with fully unmown gaps before and after it. */
    private fun pocket(x: Int, z: Int, width: Int) = listOf(
        n(x, z - 42, 0), n(x, z - 17, width), n(x, z + 17, width), n(x, z + 42, 0))

    private fun longHole(number: Int, par: Int, length: Int, side: Int,
        firstX: Int, firstZ: Int, finish: Int, height: Int, outer: Int,
        lie: GolfLie, form: GolfGreenForm, secondX: Int = firstX,
        secondZ: Int = 0, reception: Int = 20): ClassicHole {
        val x = side * firstX
        val endX = side * finish
        val second = side * secondX
        val safe = if (par == 4) listOf(
            n(0, 0, 12), n(-side * 60, 75, 32), n(-side * 150, 155, 48),
            n(-side * outer, 220, 44), n(-side * (outer - 15), length - 100, 48),
            n(-side * 75, length - 45, 42), n(endX, length, 22)
        ) else listOf(
            n(0, 0, 12), n(-side * 60, 75, 32), n(-side * 150, 155, 48),
            n(-side * outer, 245, 44), n(-side * (outer + 10), 335, 48),
            n(-side * (outer - 15), length - 135, 44),
            n(-side * 75, length - 50, 42), n(endX, length, 22)
        )
        val islands = pocket(x, firstZ, reception) + if (par == 5)
            pocket(second, secondZ, reception - 2) else emptyList()
        // No continuous ribbon to the green: leave a real rough carry after the last island.
        val branch = islands + n(endX, length - 30, 0) + n(endX, length, 22)
        val hazards = buildList {
            if (lie == GolfLie.WATER) {
                fun w(px:Int,pz:Int,r:Int)=GolfWaterNode(px.toFloat(),pz.toFloat(),r.toFloat())
                val spine=mutableListOf(w(x,firstZ-66,20),w(x+side*40,firstZ-38,17),
                    w(x+side*36,firstZ,13),w(x+side*43,firstZ+36,16))
                if(par==4) spine+=w(x,firstZ+67,21) else {
                    spine+=w((x+second)/2,(firstZ+secondZ)/2,27)
                    spine+=w(second+side*39,secondZ-38,16)
                    spine+=w(second+side*36,secondZ,12)
                    spine+=w(second+side*38,secondZ+37,15)
                    spine+=w(second,secondZ+65,19)
                }
                add(GolfWatercourse.lake(*spine.toTypedArray()))
            } else {
                add(obstacle(x, firstZ - 66, 38, 22, lie))
                add(obstacle(x + side * 33, firstZ, 12, 33, lie))
                if (par == 4) add(obstacle(x, firstZ + 67, 38, 22, lie))
                else {
                    add(obstacle((x + second) / 2, (firstZ + secondZ) / 2, 42, 40, lie))
                    add(obstacle(second + side * 32, secondZ, 11, 31, lie))
                    add(obstacle(second, secondZ + 65, 32, 20, lie))
                }
            }
            add(obstacle(endX + side * 27, length - 8, 8, 13, GolfLie.BUNKER))
        }
        val attacks = buildList {
            for (club in listOf(GolfClub.WOOD3, GolfClub.DRIVER))
                add(GolfAttackLanding(x.toFloat(), firstZ.toFloat(), club))
            if (par == 5) for (club in listOf(GolfClub.WOOD3, GolfClub.DRIVER))
                add(GolfAttackLanding(second.toFloat(), secondZ.toFloat(), club,
                    x.toFloat(), firstZ.toFloat()))
        }
        return ClassicHole(number, par, length.toFloat(), finishX = endX.toFloat(),
            width = 620f, greenRadius = 12.5f, elevation = height.toFloat(),
            route = safe, alternateRoutes = listOf(branch), hazards = hazards,
            landingZ = 155f, fairwayStart = 45f,
            elevationProfile = listOf(GolfElevationNode(0f, 9f),
                GolfElevationNode(firstZ.toFloat(), 5f),
                GolfElevationNode((length - 60).toFloat(), 4f),
                GolfElevationNode(length.toFloat(), height.toFloat())),
            mounds = listOf(GolfMound(-side * 30f, firstZ + 60f, 10f, 45f, 55f)),
            greenShape = shape(number, form), fairwayRelief = GolfFairwayRelief(side * .004f, .08f),
            landscapeStyle = GolfLandscapeStyle.AUTUMN_HIGHLANDS, attackLandings = attacks,
            attackTreeClearance = 5f)
    }

    private fun shape(number: Int, form: GolfGreenForm) = GolfGreenShape(
        aspect = if (number % 2 == 0) .94f else 1.06f, rotation = number * .37f,
        shape = .08f, slopeX = if (number % 2 == 0) -.012f else .011f, slopeZ = .012f,
        relief = GolfGreenRelief(form, direction = number * .37f + .6f,
            height = if (form == GolfGreenForm.CROWN) .10f else .25f, run = 13f))

    private fun shortHole(number: Int, length: Int, side: Int, finish: Int,
        height: Int, form: GolfGreenForm, lie: GolfLie, clubs: List<GolfClub>): ClassicHole {
        val x = side * finish
        return ClassicHole(number, 3, length.toFloat(), finishX = x.toFloat(),
            width = 480f, greenRadius = 11.8f, elevation = height.toFloat(),
            route = listOf(n(0, 0, 12), n(-side * 55, 55, 0),
                n(-side * 110, length - 80, 48), n(-side * 100, length - 40, 40), n(x, length, 20)),
            alternateRoutes = listOf(listOf(n(x, length - 55, 0), n(x, length - 25, 0), n(x, length, 20))),
            fairwayStart = 50f,
            elevationProfile = listOf(GolfElevationNode(0f, 10f),
                GolfElevationNode((length - 65).toFloat(), 4f), GolfElevationNode(length.toFloat(), height.toFloat())),
            hazards = (if(lie==GolfLie.WATER) listOf(GolfWatercourse.lake(
                GolfWaterNode(x.toFloat(),length-65f,20f),GolfWaterNode(x+side*30f,length-40f,15f),
                GolfWaterNode(x+side*32f,length-8f,10f))) else
                listOf(obstacle(x,length-65,37,23,lie),obstacle(x+side*28,length-6,10,20,lie))) +
                obstacle(x,length+26,14,7,GolfLie.BUNKER),
            greenShape = shape(number, form), fairwayRelief = GolfFairwayRelief(side * .003f, .06f),
            landscapeStyle = GolfLandscapeStyle.AUTUMN_HIGHLANDS,
            attackLandings = clubs.map { GolfAttackLanding(x.toFloat(), length.toFloat(), it) },
            attackTreeClearance = 5f)
    }

    /** Broad outside fairways wind around wooded ridges; the detached attack pockets stay narrow.
     * Overlapping shoulders avoid a single cone; clearings retain views of the lake.
     */
    private fun woodedRidge(hole: ClassicHole): ClassicHole {
        val route = hole.route.mapIndexed { i, node ->
            if (i in 1 until hole.route.lastIndex && node.width >= 28f) node.copy(width = node.width * 1.45f)
            else node
        }
        val attacks = hole.attackLandings.distinctBy { it.z }
        val middleZ = if (hole.par == 3) hole.length * .62f else attacks.map { it.z }.average().toFloat() + 20f
        val attackX = if (hole.par == 3) hole.finishX else attacks.map { it.x }.average().toFloat()
        val middleX = (hole.fairwayCenter(middleZ) + attackX) * .5f
        val rx = if (hole.par == 3) 30f else 68f
        val rz = if (hole.par == 5) 108f else if (hole.par == 4) 76f else 40f
        val side = if (middleX >= 0f) 1f else -1f
        val ridge = if (hole.number == 2) listOf(
            GolfMound(85f, 335f, 22f, 75f, 103f),
            GolfMound(90f, 260f, 10f, 60f, 74f),
            GolfMound(42f, 410f, 9f, 45f, 64f)) else listOf(
            GolfMound(middleX, middleZ, if (hole.par == 3) 9f else 18f + hole.number % 4, rx, rz),
            GolfMound(middleX + side * rx * .20f, middleZ - rz * .66f, 7f, rx * .8f, rz * .72f),
            GolfMound(middleX - side * rx * .34f, middleZ + rz * .64f, 8f, rx * .7f, rz * .62f))
        val terrain = hole.copy(route = route, mounds = ridge)
        val random = Random(6200 + hole.number)
        val main = ridge.first()
        val groves = listOf(-.42f to -.86f, .34f to -.55f, -.28f to -.12f,
            .60f to .10f, .05f to .53f, -.62f to .84f).map { (x, z) ->
                main.x + x * main.rx to main.z + z * main.rz
            }
        val trees = buildList<GolfTree> {
            for ((cx, cz) in groves) repeat(20) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val reach = sqrt(random.nextFloat())
                val x = cx + cos(angle) * reach * min(27f, main.rx * .4f)
                val z = cz + sin(angle) * reach * min(31f, main.rz * .4f)
                val radius = 4.1f + random.nextFloat() * 2.1f
                if (terrain.lieAt(x, z) != GolfLie.ROUGH ||
                    terrain.fairwaySignedDistance(x, z) < radius + 10f ||
                    terrain.hazards.any { it.signedDistance(x, z) < radius + 6f } ||
                    any { hypot(x - it.x, z - it.z) < (radius + it.radius) * .85f }) return@repeat
                // Keep both the tee carry and the lake-bank attack corridor open.
                if (terrain.attackLandings.any { a ->
                    val dx = a.x - a.fromX; val dz = a.z - a.fromZ
                    val t = (((x - a.fromX) * dx + (z - a.fromZ) * dz) /
                        (dx * dx + dz * dz).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    hypot(x - a.fromX - dx * t, z - a.fromZ - dz * t) < radius + 12f
                }) return@repeat
                add(GolfTree(x, z, radius, if (random.nextInt(5) < 3) 1 else 2))
            }
        }
        return terrain.copy(plantedTrees = hole.plantedTrees + trees)
    }

    val holes = listOf(
        // Detached clearing beyond a tarn; the outside loop goes around the entire basin.
        longHole(1, 4, 370, 1, 55, 190, 105, 7, 230, GolfLie.WATER, GolfGreenForm.RIDGE),
        // Two isolated lake banks, with no mown causeway between them.
        longHole(2, 5, 565, -1, 50, 195, 115, 8, 240, GolfLie.WATER, GolfGreenForm.TIER, 65, 390),
        shortHole(3, 150, 1, 45, 5, GolfGreenForm.FALSE_FRONT, GolfLie.WATER,
            listOf(GolfClub.IRON5, GolfClub.HYBRID4)),
        // Quarry pockets require stopping the ball, not merely flying over the sand.
        longHole(4, 4, 390, -1, 65, 195, 110, 10, 245, GolfLie.BUNKER, GolfGreenForm.FALSE_FRONT),
        longHole(5, 4, 350, 1, 60, 180, 125, 5, 235, GolfLie.WATER, GolfGreenForm.CROWN, reception = 18),
        // A second carry changes the lateral angle of the approach.
        longHole(6, 5, 590, 1, 60, 195, 115, 9, 250, GolfLie.WATER, GolfGreenForm.RIDGE, 90, 395),
        shortHole(7, 180, -1, 50, 8, GolfGreenForm.RIDGE, GolfLie.BUNKER,
            listOf(GolfClub.WOOD5, GolfClub.WOOD3)),
        longHole(8, 4, 405, 1, 45, 200, 90, 8, 250, GolfLie.BUNKER, GolfGreenForm.TIER),
        longHole(9, 4, 385, -1, 70, 190, 120, 9, 240, GolfLie.WATER, GolfGreenForm.SWALE, reception = 18),
        longHole(10, 4, 360, -1, 50, 185, 130, 6, 235, GolfLie.WATER, GolfGreenForm.PUNCHBOWL),
        longHole(11, 4, 410, 1, 75, 200, 115, 11, 255, GolfLie.BUNKER, GolfGreenForm.TIER),
        shortHole(12, 165, 1, 55, 6, GolfGreenForm.TIER, GolfLie.WATER,
            listOf(GolfClub.HYBRID4, GolfClub.WOOD5)),
        longHole(13, 4, 375, 1, 35, 195, 115, 7, 245, GolfLie.BUNKER, GolfGreenForm.CROWN, reception = 18),
        longHole(14, 4, 400, -1, 65, 200, 100, 12, 255, GolfLie.WATER, GolfGreenForm.RIDGE),
        longHole(15, 5, 605, -1, 70, 200, 120, 8, 255, GolfLie.WATER, GolfGreenForm.SWALE, 45, 410),
        shortHole(16, 195, -1, 40, 7, GolfGreenForm.FALSE_FRONT, GolfLie.WATER,
            listOf(GolfClub.WOOD3, GolfClub.DRIVER)),
        longHole(17, 4, 395, 1, 60, 195, 125, 10, 250, GolfLie.WATER, GolfGreenForm.FALSE_FRONT, reception = 18),
        longHole(18, 5, 580, 1, 45, 190, 100, 10, 260, GolfLie.WATER, GolfGreenForm.RIDGE, 70, 390)
    ).map { woodedRidge(it).copy(decorTheme = GolfDecorTheme.HALLOWEEN) }
}
