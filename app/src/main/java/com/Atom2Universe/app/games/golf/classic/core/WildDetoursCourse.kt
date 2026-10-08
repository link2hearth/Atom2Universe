package com.Atom2Universe.app.games.golf.classic.core

/**
 * Original advanced routing: fantasy-course variety with real club ranges and regulation cups.
 * References (principles only, no copied layouts or assets):
 * https://www.nintendo.com/us/store/products/mario-golf-super-rush-switch/
 * https://www.nintendo.com/us/store/products/golf-with-your-friends-switch/
 * https://www.ea.com/games/ea-sports-pga-tour/pga-tour/news/ea-sports-pga-tour-courses-overview
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
        GolfHazard(x.toFloat(), z.toFloat(), rx.toFloat(), rz.toFloat(), GolfLie.WATER, angle, .06f)
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
        width=360f, greenRadius=radius, route=route, elevationProfile=profile,
        hazards=hazards, mounds=mounds, alternateRoutes=branches, plantedTrees=trees,
        landingZ=landing, fairwayStart=if (par == 3) length - 36f else 48f,
        greenShape=GolfGreenShape(aspect=aspect, rotation=rotation, shape=.10f,
            slopeX=if (number % 2 == 0) -.014f else .012f, slopeZ=.013f,
            relief=GolfGreenRelief(form, direction=rotation + .8f,
                height=if (form == GolfGreenForm.CROWN) .10f else .28f, run=15f)),
        fairwayRelief=GolfFairwayRelief(if (number % 2 == 0) -.012f else .012f, .15f))

    val holes: List<ClassicHole> = listOf(
        // 1. Two fairways around a wooded central hill; left is wide, right opens the green.
        hole(1,4,365,0,7,
            r(n(0,0,12),n(-23,70,26),n(-59,145,34),n(-62,205,44),n(-36,280,28),n(0,365,22)),
            e(0 to 9,100 to 5,210 to 3,365 to 7), listOf(sand(27,326),sand(-94,216)),
            listOf(hill(0,190,15,45,64)),
            listOf(r(n(-23,70,26),n(45,140,21),n(56,220,25),n(25,300,20),n(0,365,22))),
            grove(0,190), GolfGreenForm.RIDGE),
        // 2. Wide three-shot eastern crescent; a skinny western causeway between two lakes.
        hole(2,5,505,0,5,
            r(n(0,0,12),n(37,85,26),n(90,180,46),n(110,270,36),n(92,355,46),n(43,430,26),n(0,505,22)),
            e(0 to 14,110 to 6,210 to 2,355 to 2,505 to 5),
            listOf(lake(-47,290,25,66),lake(27,290,27,66),sand(53,450)),
            branches=listOf(r(n(0,65,20),n(-10,170,23),n(-10,235,17),n(-10,335,17),n(0,410,24),n(0,505,22))),
            form=GolfGreenForm.TIER, landing=180f),
        // 3. Island-like peninsula, open escape apron on the right; short is wet, long is sand.
        hole(3,3,155,12,3,
            r(n(0,0,12),n(6,80,0),n(32,124,25),n(12,155,21)),
            e(0 to 12,65 to 4,115 to 1,155 to 3),
            listOf(lake(-15,105,26,25),sand(9,183,12,7)),
            form=GolfGreenForm.SWALE, radius=14f, aspect=1.15f, rotation=.65f),
        // 4. A ridge hides the direct line; the right saddle is narrow but avoids the long elbow.
        hole(4,4,405,30,12,
            r(n(0,0,12),n(-30,85,25),n(-70,180,43),n(-57,250,30),n(0,325,28),n(30,405,23)),
            e(0 to 4,120 to 8,220 to 6,300 to 5,405 to 12),listOf(sand(56,369),sand(-103,210)),
            listOf(hill(0,205,21,43,55)),
            listOf(r(n(0,60,21),n(46,160,23),n(54,237,21),n(30,310,24),n(30,405,23))),
            grove(0,205), GolfGreenForm.FALSE_FRONT, landing=180f),
        // 5. Drivable cape: carry the diagonal lake or lay up on a wide right-hand terrace.
        hole(5,4,285,-25,3,
            r(n(0,0,12),n(35,80,28),n(71,165,46),n(34,230,26),n(-25,285,22)),
            e(0 to 16,100 to 5,200 to 1,285 to 3),
            listOf(lake(-21,185,43,45,-.3f),sand(-53,281,7,12)),
            branches=listOf(r(n(0,65,22),n(-10,113,22),n(-25,150,0),n(-25,242,0),n(-25,285,22))),
            form=GolfGreenForm.CROWN, landing=165f, radius=13.5f),
        // 6. Braided river: a broad eastern shelf and two precise stepping-stone landings west.
        hole(6,5,545,-15,8,
            r(n(0,0,12),n(35,85,28),n(76,200,46),n(92,285,32),n(62,375,44),n(20,450,26),n(-15,545,23)),
            e(0 to 8,150 to 3,280 to 2,410 to 4,545 to 8),
            listOf(lake(-30,268,36,43),lake(-14,390,36,28),sand(35,490)),
            branches=listOf(r(n(0,65,22),n(-38,178,28),n(-45,215,24),n(-46,253,0),n(-46,285,0),
                n(-48,331,26),n(-47,355,26),n(-35,392,0),n(-22,424,24),n(-15,545,23))),
            form=GolfGreenForm.RIDGE),
        // 7. Long Redan; the high right apron feeds left, while a central front bunker punishes short.
        hole(7,3,185,-18,8,
            r(n(0,0,12),n(0,110,0),n(12,152,27),n(-18,185,22)),
            e(0 to 7,90 to 0,150 to 5,185 to 8),listOf(sand(-28,152,13,9,-.4f)),
            listOf(hill(49,120,10,30,35)), form=GolfGreenForm.RIDGE, aspect=1.2f,rotation=-.65f),
        // 8. Sand sea instead of water: two mown ribbons flank a massive central waste bunker.
        hole(8,4,395,10,5,
            r(n(0,0,12),n(-26,80,26),n(-61,190,43),n(-51,260,28),n(-17,335,25),n(10,395,22)),
            e(0 to 5,130 to 8,250 to 3,395 to 5),
            listOf(sand(0,211,30,59,.1f),sand(38,389,8,12)),
            branches=listOf(r(n(-26,80,26),n(48,157,22),n(55,242,24),n(35,300,21),n(10,395,22))),
            form=GolfGreenForm.TIER, landing=190f),
        // 9. Reverse S: wide outer shelf, short inner line over a grove to an angled green.
        hole(9,4,420,-45,11,
            r(n(0,0,12),n(27,85,26),n(65,205,43),n(22,285,28),n(-26,350,26),n(-45,420,22)),
            e(0 to 12,130 to 6,240 to 2,330 to 5,420 to 11),listOf(sand(-74,409),sand(13,310)),
            listOf(hill(-8,215,14,36,55)),
            listOf(r(n(0,65,20),n(-48,165,22),n(-51,243,25),n(-45,325,21),n(-45,420,22))),
            grove(-8,215), GolfGreenForm.FALSE_FRONT, aspect=.9f,rotation=.6f),
        // 10. Downhill horseshoe around a pond; attack left for a shorter but water-lined approach.
        hole(10,4,350,0,3,
            r(n(0,0,12),n(35,83,26),n(71,181,45),n(58,245,29),n(0,350,22)),
            e(0 to 22,110 to 11,210 to 2,350 to 3),listOf(lake(0,210,29,48),sand(26,339)),
            branches=listOf(r(n(0,62,20),n(-46,150,20),n(-51,231,24),n(-23,292,20),n(0,350,22))),
            form=GolfGreenForm.PUNCHBOWL,landing=180f),
        // 11. Central woodland island with diverging first and second-shot choices.
        hole(11,4,430,15,9,
            r(n(0,0,12),n(-33,85,27),n(-79,200,45),n(-80,290,31),n(-59,335,44),n(-15,380,28),n(15,430,23)),
            e(0 to 9,140 to 3,280 to 5,350 to 2,430 to 9),listOf(sand(29,400),sand(44,426)),
            listOf(hill(-5,240,18,51,89)),
            listOf(r(n(0,65,23),n(52,165,23),n(57,245,26),n(55,330,22),n(31,375,25),n(15,430,23))),
            grove(-5,205)+grove(-5,290),GolfGreenForm.TIER),
        // 12. Hourglass target: rear sand and front water demand carry control, right apron is dry.
        hole(12,3,170,8,4,
            r(n(0,0,12),n(10,105,0),n(34,139,27),n(8,170,22)),
            e(0 to 14,80 to 2,125 to 1,170 to 4),listOf(lake(-14,114,28,24),sand(5,201,14,7)),
            form=GolfGreenForm.TIER,radius=13.5f,aspect=.88f,rotation=.2f),
        // 13. Three prongs: comfortable left, short right, thin central ribbon between bunkers.
        hole(13,4,310,0,6,
            r(n(0,0,12),n(-30,83,26),n(-63,166,43),n(-40,235,27),n(0,310,22)),
            e(0 to 11,120 to 4,210 to 2,310 to 6),listOf(sand(-23,190,11,29),sand(23,190,11,29),sand(28,305)),
            branches=listOf(r(n(0,65,22),n(56,150,23),n(51,205,25),n(24,264,22),n(0,310,22)),
                r(n(0,65,22),n(0,130,17),n(0,220,17),n(0,270,22),n(0,310,22))),
            form=GolfGreenForm.CROWN,landing=166f),
        // 14. Two balconies around a high wooded ridge, then a rising narrow green.
        hole(14,4,430,35,15,
            r(n(0,0,12),n(-29,88,25),n(-62,198,42),n(-52,280,29),n(0,353,25),n(35,430,22)),
            e(0 to 5,110 to 8,240 to 9,330 to 8,430 to 15),listOf(sand(60,415),sand(-92,220)),
            listOf(hill(4,215,23,42,68)),
            listOf(r(n(0,65,20),n(58,171,23),n(65,240,24),n(50,320,21),n(35,430,22))),
            grove(4,215),GolfGreenForm.RIDGE,landing=195f,aspect=.85f),
        // 15. Long double cape: cutting either lake saves distance, never both without precise shots.
        hole(15,5,560,-20,6,
            r(n(0,0,12),n(38,87,26),n(89,200,46),n(102,280,30),n(74,385,45),n(19,468,26),n(-20,560,23)),
            e(0 to 14,120 to 7,250 to 2,380 to 3,560 to 6),
            listOf(lake(-13,250,49,48),lake(-26,400,33,40),sand(4,521)),
            branches=listOf(r(n(0,65,22),n(-44,150,24),n(-74,238,20),n(-74,297,22),
                n(-74,365,21),n(-73,421,23),n(-20,490,25),n(-20,560,23))),
            form=GolfGreenForm.SWALE),
        // 16. Exposed long iron; left sand, right lake, a front-left bailout still permits a chip.
        hole(16,3,205,-8,5,
            r(n(0,0,12),n(-3,135,0),n(-28,172,28),n(-8,205,23)),
            e(0 to 17,90 to 6,150 to 1,205 to 5),listOf(lake(33,164,22,36),sand(-34,217,8,13)),
            form=GolfGreenForm.FALSE_FRONT,radius=15f,aspect=1.08f,rotation=-.4f),
        // 17. Late dilemma: left is broad but leaves a diagonal carry, right is narrow beside water.
        hole(17,4,390,40,8,
            r(n(0,0,12),n(-24,80,25),n(-55,190,43),n(-35,260,29),n(12,322,24),n(40,390,22)),
            e(0 to 6,130 to 10,220 to 4,300 to 3,390 to 8),listOf(lake(68,215,24,57),sand(15,351)),
            listOf(hill(0,200,12,31,45)),
            listOf(r(n(0,63,21),n(27,150,20),n(29,235,22),n(40,300,21),n(40,390,22))),
            grove(0,200),GolfGreenForm.TIER,landing=190f),
        // 18. Grand finale: wide crescent or the 18 m causeway between two long lakes.
        hole(18,5,535,0,7,
            r(n(0,0,12),n(38,85,27),n(95,190,46),n(122,285,32),n(102,380,45),n(40,458,26),n(0,535,23)),
            e(0 to 20,120 to 10,240 to 2,380 to 2,535 to 7),
            listOf(lake(-45,310,26,81),lake(31,310,26,81),sand(-28,526)),
            branches=listOf(r(n(0,65,22),n(-7,171,24),n(-7,236,18),n(-7,380,18),n(0,440,24),n(0,535,23))),
            form=GolfGreenForm.RIDGE,landing=190f,radius=15f)
    )
}
