package com.Atom2Universe.app.games.golf.classic.core

/**
 * Autumn highlands: the pin sits beyond the narrow attack, opposite the broad outer route.
 * Par 4s offer driver + approach versus three conservative shots; par 5s offer three versus four.
 * Par 3s offer a guarded green in one or a broad short bailout followed by a chip.
 * These are routing choices, not stroke guarantees: wind, contact and recovery still matter.
 * Full flat carries: driver 235 m, wood 3 213 m, wood 5 195 m, iron 8 125 m, PW 97 m.
 * Landing bulbs allow some rollout; slopes and wind still require reading the live preview.
 * Primary corridors give the caddie a dry, conservative route. Branches are genuine fairways,
 * shared by turf rendering, lies, vegetation clearance and terrain grading.
 */
object WildDetoursCourse {
    private fun n(x: Int, z: Int, w: Int) = GolfRouteNode(x.toFloat(), z.toFloat(), w.toFloat())
    private fun r(vararg nodes: GolfRouteNode) = nodes.toList()
    private fun e(vararg nodes: Pair<Int, Int>) = nodes.map { GolfElevationNode(it.first.toFloat(), it.second.toFloat()) }
    private fun hill(x: Int, z: Int, h: Int, rx: Int, rz: Int) =
        GolfMound(x.toFloat(), z.toFloat(), h.toFloat(), rx.toFloat(), rz.toFloat())
    private fun sand(x: Int, z: Int, rx: Int = 9, rz: Int = 14, angle: Float = 0f) =
        GolfHazard(x.toFloat(), z.toFloat(), rx.toFloat(), rz.toFloat(), GolfLie.BUNKER, angle, .12f)
    private fun lake(x: Int, z: Int, rx: Int, rz: Int, angle: Float = 0f) =
        GolfWatercourse.meander(x.toFloat(),z.toFloat(),rx.toFloat(),rz.toFloat(),angle)
    private fun w(x:Int,z:Int,r:Int)=GolfWaterNode(x.toFloat(),z.toFloat(),r.toFloat())
    private fun grove(x: Int, z: Int) = listOf(
        GolfTree(x.toFloat(), z.toFloat(), 4.8f, 1),
        GolfTree(x - 8f, z - 12f, 4.1f, 0), GolfTree(x + 8f, z + 12f, 4.3f, 2),
        GolfTree(x - 5f, z + 19f, 3.9f, 1), GolfTree(x + 5f, z - 20f, 4.2f, 0))

    private fun hole(number: Int, par: Int, length: Int, finish: Int, height: Int,
        route: List<GolfRouteNode>, profile: List<GolfElevationNode>,
        hazards: List<GolfHazard>, mounds: List<GolfMound> = emptyList(),
        branches: List<List<GolfRouteNode>> = emptyList(), trees: List<GolfTree> = emptyList(),
        form: GolfGreenForm = GolfGreenForm.PLANE, landing: Float = 200f,
        radius: Float = 14.5f, aspect: Float = 1f, rotation: Float = 0f) = ClassicHole(
        number, par, length.toFloat(), finishX=finish.toFloat(), elevation=height.toFloat(),
        width=400f, greenRadius=radius, route=route, elevationProfile=profile,
        hazards=hazards, mounds=mounds, alternateRoutes=branches, plantedTrees=trees,
        landingZ=landing, fairwayStart=if (par == 3) 65f else 48f,
        greenShape=GolfGreenShape(aspect=aspect, rotation=rotation, shape=.10f,
            slopeX=if (number % 2 == 0) -.014f else .012f, slopeZ=.013f,
            relief=GolfGreenRelief(form, direction=rotation + .8f,
                height=if (form == GolfGreenForm.CROWN) .10f else .28f, run=15f)),
        fairwayRelief=GolfFairwayRelief(if (number % 2 == 0) -.012f else .012f, .15f))

    val holes: List<ClassicHole> = listOf(
        // Wooded fork: the pin extends the small inner clearing, away from the broad outer shelf.
        hole(1,4,365,110,7,
            r(n(0,0,12),n(-35,75,24),n(-110,175,48),n(-115,255,42),n(-40,315,40),n(110,365,24)),
            e(0 to 9,100 to 5,210 to 3,365 to 7), listOf(sand(137,326),sand(-150,216)),
            listOf(hill(0,190,15,45,64)),
            listOf(r(n(-23,70,26),n(45,140,0),n(56,199,0),n(56,225,24),n(65,265,22),n(85,310,22),n(110,365,24))),
            grove(0,190), GolfGreenForm.RIDGE, landing=175f),
        // Two lake carries versus four shots around the outside bank.
        hole(2,5,505,-100,5,
            r(n(0,0,12),n(45,80,32),n(110,175,50),n(130,270,44),n(150,300,48),n(45,435,42),n(-100,505,24)),
            e(0 to 14,110 to 6,210 to 2,355 to 2,505 to 5),
            listOf(GolfWatercourse.lake(w(-8,150,26),w(-42,205,20),w(-47,290,20),w(-43,352,18),
                w(-10,385,14),w(26,345,16),w(27,280,21),w(26,225,18)),sand(-47,450)),
            branches=listOf(r(n(0,65,20),n(-10,170,0),n(-10,203,0),n(-10,225,28),n(-10,235,17),n(-10,335,17),n(-10,358,0),n(-10,407,0),n(-10,430,24),n(-100,505,24))),
            form=GolfGreenForm.TIER, landing=175f),
        // Peninsula: direct water carry or a broad short reception leaving a separate chip.
        hole(3,3,155,12,3,
            r(n(0,0,12),n(6,80,0),n(45,96,42),n(12,155,21)),
            e(0 to 12,65 to 4,115 to 1,155 to 3),
            listOf(lake(-9,104,36,27),sand(9,183,12,7)),
            form=GolfGreenForm.SWALE, radius=14f, aspect=1.15f, rotation=.65f),
        // Screenshot's saddle: the green follows the narrow passage, opposite the long elbow.
        hole(4,4,405,110,12,
            r(n(0,0,12),n(-40,80,32),n(-110,175,48),n(-120,265,42),n(-40,335,40),n(110,405,24)),
            e(0 to 4,120 to 8,220 to 6,300 to 5,405 to 12),listOf(sand(136,369),sand(-150,210)),
            listOf(hill(0,205,21,43,55)),
            listOf(r(n(0,60,21),n(46,160,0),n(54,194,0),n(54,220,24),n(62,245,22),n(82,310,22),n(110,405,24))),
            grove(0,205), GolfGreenForm.FALSE_FRONT, landing=175f),
        // The cape carries past water; the far outside shelf cannot reach the green with rollout.
        hole(5,4,330,-140,3,
            r(n(0,0,12),n(45,80,32),n(110,165,50),n(105,240,44),n(10,285,40),n(-140,330,24)),
            e(0 to 16,100 to 5,200 to 1,330 to 3),
            listOf(lake(-21,171,43,36,-.3f),sand(-168,326,7,12)),
            branches=listOf(r(n(0,65,22),n(-10,113,22),n(-25,150,0),n(-25,205,0),n(-25,228,24),n(-25,247,24),n(-140,330,24))),
            form=GolfGreenForm.CROWN, landing=165f, radius=13.5f),
        // Two demanding river receptions save a stroke over the broad eastern terraces.
        hole(6,5,545,-100,8,
            r(n(0,0,12),n(45,80,32),n(110,175,50),n(130,270,44),n(125,350,48),n(35,455,42),n(-100,545,24)),
            e(0 to 8,150 to 3,280 to 2,410 to 4,545 to 8),
            listOf(GolfWatercourse.lake(w(-30,248,21),w(-30,278,26),w(12,318,18),w(10,355,16),
                w(-14,390,24)),sand(-50,490)),
            branches=listOf(r(n(0,65,22),n(-38,178,28),n(-45,215,24),n(-46,253,0),n(-46,285,0),
                n(-65,331,18),n(-65,355,18),n(-65,392,0),n(-65,425,24),n(-100,545,24))),
            form=GolfGreenForm.RIDGE, landing=175f),
        // Redan: guarded long shot or short apron plus a diagonal approach.
        hole(7,3,185,-18,8,
            r(n(0,0,12),n(0,110,0),n(38,119,42),n(-18,185,22)),
            e(0 to 7,90 to 0,150 to 5,185 to 8),listOf(sand(-28,152,13,9,-.4f)),
            listOf(hill(49,120,10,30,35)), form=GolfGreenForm.RIDGE, aspect=1.2f,rotation=-.65f),
        // Quarry carry: the narrow tongue continues to its own green.
        hole(8,4,395,110,5,
            r(n(0,0,12),n(-40,80,32),n(-110,175,48),n(-120,260,42),n(-40,325,40),n(110,395,24)),
            e(0 to 5,130 to 8,250 to 3,395 to 5),
            listOf(sand(0,211,30,59,.1f),sand(138,389,8,12)),
            branches=listOf(r(n(-26,80,26),n(48,157,0),n(55,194,0),n(55,220,24),n(62,242,22),n(82,300,22),n(110,395,24))),
            form=GolfGreenForm.TIER, landing=175f),
        // Reverse S: pin beyond the inner pine corridor, far from the outside drive.
        hole(9,4,420,-110,11,
            r(n(0,0,12),n(40,80,32),n(110,175,48),n(120,275,42),n(40,345,40),n(-110,420,24)),
            e(0 to 12,130 to 6,240 to 2,330 to 5,420 to 11),listOf(sand(-139,409),sand(13,310)),
            listOf(hill(-8,215,14,36,55)),
            listOf(r(n(0,65,20),n(-48,165,0),n(-51,192,0),n(-51,220,24),n(-51,243,25),n(-45,325,21),n(-110,420,24))),
            grove(-8,215), GolfGreenForm.FALSE_FRONT, landing=175f, aspect=.9f,rotation=.6f),
        // Downhill horseshoe: the water-side attack has the short approach.
        hole(10,4,350,-110,3,
            r(n(0,0,12),n(40,75,32),n(110,165,50),n(115,235,44),n(30,295,40),n(-110,350,24)),
            e(0 to 22,110 to 11,210 to 2,350 to 3),listOf(lake(0,210,29,48),sand(-84,339)),
            branches=listOf(r(n(0,62,20),n(-46,150,0),n(-51,195,0),n(-51,222,24),n(-51,245,22),n(-23,292,20),n(-110,350,24))),
            form=GolfGreenForm.PUNCHBOWL,landing=165f),
        // Woodland island: committing to the small clearing opens the green in two.
        hole(11,4,430,110,9,
            r(n(0,0,12),n(-40,80,32),n(-110,175,50),n(-125,275,44),n(-45,350,42),n(110,430,24)),
            e(0 to 9,140 to 3,280 to 5,350 to 2,430 to 9),listOf(sand(124,400),sand(139,426)),
            listOf(hill(-5,240,18,51,89)),
            listOf(r(n(0,65,23),n(52,165,0),n(57,198,0),n(57,225,24),n(62,245,22),n(85,330,22),n(97,375,22),n(110,430,24))),
            grove(-5,205)+grove(-5,290),GolfGreenForm.TIER, landing=175f),
        // Front lake and rear sand reward direct distance control; bailout stays well short.
        hole(12,3,170,8,4,
            r(n(0,0,12),n(20,75,0),n(45,104,42),n(8,170,22)),
            e(0 to 14,80 to 2,125 to 1,170 to 4),listOf(lake(-8,114,37,27),sand(5,201,14,7)),
            form=GolfGreenForm.TIER,radius=13.5f,aspect=.88f,rotation=.2f),
        // Three prongs: two precision options, both shorter than the broad outer route.
        hole(13,4,350,110,6,
            r(n(0,0,12),n(-40,75,32),n(-110,165,48),n(-115,235,42),n(-30,295,40),n(110,350,24)),
            e(0 to 11,120 to 4,210 to 2,350 to 6),listOf(sand(-23,190,11,29),sand(23,190,11,29),sand(110,300,12,14),sand(138,345)),
            branches=listOf(r(n(0,65,22),n(56,150,0),n(56,195,0),n(56,222,28),n(24,264,22),n(110,350,24)),
                r(n(0,65,22),n(0,130,17),n(0,210,20),n(0,232,0),n(0,280,0),n(110,350,24))),
            form=GolfGreenForm.CROWN,landing=165f),
        // High balconies: uphill green aligned with the small attack shelf.
        hole(14,4,430,110,15,
            r(n(0,0,12),n(-40,80,32),n(-110,175,48),n(-120,275,42),n(-40,350,40),n(110,430,24)),
            e(0 to 5,110 to 8,240 to 9,330 to 8,430 to 15),listOf(sand(135,415),sand(-150,220)),
            listOf(hill(4,215,23,42,68)),
            listOf(r(n(0,65,20),n(58,171,0),n(65,193,0),n(65,220,24),n(70,240,22),n(88,320,22),n(110,430,24))),
            grove(4,215),GolfGreenForm.RIDGE,landing=175f,aspect=.85f),
        // Double cape: two precise carries leave an iron, outside terraces need one more shot.
        hole(15,5,560,-100,6,
            r(n(0,0,12),n(45,80,32),n(110,175,50),n(130,270,44),n(125,355,48),n(35,465,42),n(-100,560,24)),
            e(0 to 14,120 to 7,250 to 2,380 to 3,560 to 6),
            listOf(GolfWatercourse.lake(w(-13,250,33),w(8,294,24),w(11,330,18),w(-17,359,23),
                w(-26,400,25)),sand(-76,521)),
            branches=listOf(r(n(0,65,22),n(-44,150,0),n(-71,193,0),n(-71,219,28),n(-74,238,20),n(-74,297,22),
                n(-74,365,21),n(-73,421,23),n(-20,490,25),n(-100,560,24))),
            form=GolfGreenForm.SWALE, landing=175f),
        // Long iron dilemma: direct green between hazards or dry short apron and chip.
        hole(16,3,205,-8,5,
            r(n(0,0,12),n(-15,100,0),n(-48,143,42),n(-8,205,23)),
            e(0 to 17,90 to 6,150 to 1,205 to 5),listOf(lake(33,164,22,36),sand(-34,217,8,13)),
            form=GolfGreenForm.FALSE_FRONT,radius=15f,aspect=1.08f,rotation=-.4f),
        // Narrow water-side landing earns the direct approach; opposite shelf remains broad.
        hole(17,4,390,110,8,
            r(n(0,0,12),n(-40,80,32),n(-110,175,48),n(-120,255,42),n(-40,320,40),n(110,390,24)),
            e(0 to 6,130 to 10,220 to 4,300 to 3,390 to 8),listOf(lake(68,215,24,57),sand(85,351)),
            listOf(hill(0,200,12,31,45)),
            listOf(r(n(0,63,21),n(27,150,0),n(29,196,0),n(29,222,28),n(29,235,22),n(40,300,21),n(110,390,24))),
            grove(0,200),GolfGreenForm.TIER,landing=175f),
        // Two exact tarn carries open the final green; the wide crescent takes an extra shot.
        hole(18,5,535,-80,7,
            r(n(0,0,12),n(45,80,32),n(110,175,50),n(135,270,44),n(140,350,48),n(45,450,42),n(-80,535,24)),
            e(0 to 20,120 to 10,240 to 2,380 to 2,535 to 7),
            listOf(GolfWatercourse.lake(w(-7,151,25),w(-42,187,16),w(-43,222,18),w(-45,310,20),w(-38,373,16),
                w(-7,402,11),w(30,363,17),w(31,291,20),w(29,234,17)),sand(-108,526)),
            branches=listOf(r(n(0,65,22),n(-7,171,0),n(-7,196,0),n(-7,220,28),n(-7,236,18),n(-7,375,18),n(-7,391,0),n(-7,414,0),n(-7,430,24),n(0,460,24),n(-80,535,24))),
            form=GolfGreenForm.RIDGE,landing=175f,radius=15f)
    ).map { h ->
        val driver=GolfClub.DRIVER
        val attacks=when(h.number) {
            1 -> listOf(GolfAttackLanding(56f,225f,driver))
            2 -> listOf(GolfAttackLanding(-10f,225f,driver),GolfAttackLanding(-10f,430f,GolfClub.WOOD3,-10f,225f))
            4 -> listOf(GolfAttackLanding(54f,220f,driver))
            5 -> listOf(GolfAttackLanding(-25f,228f,driver))
            6 -> listOf(GolfAttackLanding(-45f,215f,driver),GolfAttackLanding(-65f,425f,GolfClub.WOOD3,-45f,215f))
            8 -> listOf(GolfAttackLanding(55f,220f,driver))
            9 -> listOf(GolfAttackLanding(-51f,220f,driver))
            10 -> listOf(GolfAttackLanding(-51f,222f,driver))
            11 -> listOf(GolfAttackLanding(57f,225f,driver))
            13 -> listOf(GolfAttackLanding(56f,222f,driver),GolfAttackLanding(0f,210f,GolfClub.WOOD3))
            14 -> listOf(GolfAttackLanding(65f,220f,driver))
            15 -> listOf(GolfAttackLanding(-71f,219f,driver),GolfAttackLanding(-73f,421f,GolfClub.WOOD3,-71f,219f))
            17 -> listOf(GolfAttackLanding(29f,222f,driver))
            18 -> listOf(GolfAttackLanding(-7f,220f,driver),GolfAttackLanding(-7f,430f,GolfClub.WOOD3,-7f,220f))
            else -> emptyList()
        }
        h.copy(landscapeStyle=GolfLandscapeStyle.AUTUMN_HIGHLANDS,attackLandings=attacks)
    }
}
