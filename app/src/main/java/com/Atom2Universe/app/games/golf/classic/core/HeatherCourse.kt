package com.Atom2Universe.app.games.golf.classic.core

/**
 * Heather Heights: an original second course, par 72, 6,207 m (original Gardens layout: 5,920 m).
 * A small step up through approach angles and placement, not faster greens or stronger wind.
 * Inspiration: Pinehurst No. 2's strategic width and raised targets; Old Macdonald's Redan,
 * Cape and split-line principles. These are new routings, not reproductions of real holes.
 * https://www.pinehurst.com/news/the-pinehurst-no-2-restoration-a-hole-by-hole-tour/
 * https://bandondunesgolf.com/blog/tag/bandon-dunes/page/5/
 * Landing bulbs remain 45–52 m wide, with a safe side and open approaches. Four short holes
 * and two short par 4s break up the longer work; the harder later courses can tighten further.
 */
object HeatherCourse {
    private fun n(x: Int, z: Int, width: Int) = GolfRouteNode(x.toFloat(), z.toFloat(), width.toFloat())
    private fun r(vararg nodes: GolfRouteNode) = nodes.toList()
    private fun e(vararg nodes: Pair<Int, Int>) = nodes.map { GolfElevationNode(it.first.toFloat(), it.second.toFloat()) }
    private fun hill(x: Int, z: Int, height: Int, rx: Int, rz: Int) =
        GolfMound(x.toFloat(), z.toFloat(), height.toFloat(), rx.toFloat(), rz.toFloat())
    private fun sand(x: Int, z: Int, rx: Int = 7, rz: Int = 11, angle: Float = 0f) =
        GolfHazard(x.toFloat(), z.toFloat(), rx.toFloat(), rz.toFloat(), GolfLie.BUNKER, angle, .18f, x * .07f)
    private fun lake(x: Int, z: Int, rx: Int, rz: Int, angle: Float = 0f) =
        GolfHazard(x.toFloat(), z.toFloat(), rx.toFloat(), rz.toFloat(), GolfLie.WATER, angle, .12f, z * .05f)
    private fun green(sx: Float, sz: Float, form: GolfGreenForm = GolfGreenForm.PLANE,
        direction: Float = 0f, height: Float = 0f, run: Float = 14f,
        aspect: Float = 1f, rotation: Float = 0f) = GolfGreenShape(
        aspect=aspect, rotation=rotation, shape=.14f, slopeX=sx, slopeZ=sz,
        relief=GolfGreenRelief(form, direction, height, run=run))

    val holes = listOf(
        // 1. Open rising start: take the left half to open the offset right-hand green.
        ClassicHole(1,4,330f,finishX=18f,elevation=8f,greenRadius=17f,width=260f,
            route=r(n(0,0,12),n(-8,55,20),n(-22,125,34),n(-18,195,50),n(-4,246,29),n(13,293,25),n(18,330,22)),
            elevationProfile=e(0 to 10,65 to 9,146 to 2,210 to 3,330 to 8),
            mounds=listOf(hill(47,161,7,37,62),hill(-56,280,5,32,51)),
            hazards=listOf(sand(13,219,8,15,-.3f),sand(44,326,7,12,.4f)),
            greenShape=green(.006f,.016f),fairwayRelief=GolfFairwayRelief(.012f,.20f),landingZ=193f),
        // 2. Left elbow: challenge the corner sand or use the wide right-hand shoulder.
        ClassicHole(2,4,360f,finishX=-60f,elevation=10f,greenRadius=16f,width=270f,
            route=r(n(0,0,12),n(11,60,19),n(28,130,32),n(24,202,48),n(-5,250,27),n(-48,300,25),n(-60,360,21)),
            elevationProfile=e(0 to 3,92 to 8,186 to 11,264 to 5,360 to 10),
            mounds=listOf(hill(-35,177,7,33,51),hill(57,285,6,38,52)),
            hazards=listOf(sand(-9,215,9,17,.45f),sand(-85,350,7,12,-.3f)),
            greenShape=green(-.008f,.015f,GolfGreenForm.RIDGE,.5f,.20f,aspect=.96f,rotation=.45f),
            fairwayRelief=GolfFairwayRelief(-.014f,.24f),landingZ=199f),
        // 3. Gentle Redan idea: an angled green feeds a right-side landing to the left.
        ClassicHole(3,3,142f,finishX=24f,elevation=5f,greenRadius=17f,width=250f,
            route=r(n(0,0,12),n(4,18,0),n(12,88,0),n(32,118,24),n(24,142,22)),
            elevationProfile=e(0 to 12,32 to 10,83 to 1,113 to 4,142 to 5),
            mounds=listOf(hill(-48,83,7,37,40)),
            hazards=listOf(sand(-3,133,9,13,-.55f)),
            greenShape=green(.015f,.010f,aspect=1.08f,rotation=-.6f),
            fairwayRelief=GolfFairwayRelief(.01f,0f)),
        // 4. Three-shot S through the pines: two safe shelves, central second-shot bunker.
        ClassicHole(4,5,490f,finishX=-28f,elevation=9f,greenRadius=17.5f,width=280f,
            route=r(n(0,0,12),n(7,58,19),n(31,129,31),n(43,202,49),n(21,258,25),n(-13,316,32),n(-45,374,50),n(-37,433,26),n(-28,490,23)),
            elevationProfile=e(0 to 7,83 to 12,185 to 4,268 to 7,366 to 2,490 to 9),
            mounds=listOf(hill(-40,190,8,41,65),hill(41,356,8,38,66)),
            hazards=listOf(sand(8,221,8,15,.2f),sand(-13,345,7,12,-.4f),sand(-55,487,7,12,.6f)),
            greenShape=green(.004f,.016f,GolfGreenForm.SWALE,0f,.22f,aspect=1.1f),
            fairwayRelief=GolfFairwayRelief(.012f,-.22f),landingZ=200f),
        // 5. Short strategic par 4: a centre bunker divides the approach, not the safe tee shelf.
        ClassicHole(5,4,320f,finishX=8f,elevation=6f,greenRadius=15.5f,width=250f,
            route=r(n(0,0,12),n(-6,55,22),n(-22,119,34),n(-16,185,52),n(0,225,50),n(17,269,26),n(8,320,21)),
            elevationProfile=e(0 to 8,80 to 5,165 to 1,232 to 2,320 to 6),
            mounds=listOf(hill(-64,239,7,38,46)),
            hazards=listOf(sand(0,239,6,9,.25f),sand(34,315,7,11,-.4f)),
            greenShape=green(.012f,.014f,GolfGreenForm.CROWN,0f,.07f,16f,aspect=1.02f),
            fairwayRelief=GolfFairwayRelief(.01f,.24f),landingZ=185f),
        // 6. Cape principle with a land route right of the lake; no compulsory water carry.
        ClassicHole(6,4,390f,finishX=58f,elevation=8f,greenRadius=17f,width=290f,
            route=r(n(0,0,12),n(18,65,20),n(44,140,31),n(52,211,48),n(70,266,29),n(69,326,27),n(58,390,22)),
            elevationProfile=e(0 to 5,95 to 11,193 to 7,285 to 2,390 to 8),
            mounds=listOf(hill(111,171,8,32,58)),
            hazards=listOf(lake(-5,266,38,65,-.15f),sand(25,214,7,13,.3f),sand(84,385,7,12,.5f)),
            greenShape=green(.006f,.014f,GolfGreenForm.FALSE_FRONT,1.5707964f,.22f,12f),
            fairwayRelief=GolfFairwayRelief(-.012f,.16f),landingZ=207f),
        // 7. Breather: a short iron into an open bowl, with water only beyond the safe left apron.
        ClassicHole(7,3,118f,finishX=-12f,elevation=2f,greenRadius=16.5f,width=250f,
            route=r(n(0,0,12),n(-2,18,0),n(-6,69,0),n(-18,94,19),n(-12,118,20)),
            elevationProfile=e(0 to 14,29 to 12,70 to 0,118 to 2),
            mounds=listOf(hill(-63,51,6,34,36)),
            hazards=listOf(lake(44,91,18,31,.3f),sand(-38,116,6,8,.2f)),
            greenShape=green(.010f,.017f,GolfGreenForm.PUNCHBOWL,0f,.075f,16f),
            fairwayRelief=GolfFairwayRelief(.006f,0f)),
        // 8. Dune ridge: lay up at the saddle or carry a dry hollow to the second landing shelf.
        ClassicHole(8,5,510f,finishX=48f,elevation=12f,greenRadius=18f,width=290f,
            route=r(n(0,0,12),n(-12,60,20),n(-33,137,32),n(-38,205,49),n(-18,263,26),n(1,294,14),n(30,346,30),n(51,394,51),n(59,451,25),n(48,510,23)),
            elevationProfile=e(0 to 3,93 to 11,193 to 8,255 to 10,291 to 3,380 to 6,510 to 12),
            mounds=listOf(hill(27,175,9,37,53),hill(-53,356,9,41,66)),
            hazards=listOf(sand(-6,227,8,14,-.2f),sand(23,382,8,13,.5f),sand(76,506,8,12,-.5f)),
            greenShape=green(.005f,.016f,GolfGreenForm.RIDGE,-.3f,.24f),
            fairwayRelief=GolfFairwayRelief(-.012f,.28f),landingZ=202f),
        // 9. A rising terrace: favour the left to see across the green's modest rear tier.
        ClassicHole(9,4,365f,finishX=-36f,elevation=14f,greenRadius=16f,width=260f,
            route=r(n(0,0,12),n(9,57,20),n(18,129,31),n(8,202,47),n(-13,258,26),n(-32,310,24),n(-36,365,21)),
            elevationProfile=e(0 to 6,86 to 3,175 to 5,245 to 4,365 to 14),
            mounds=listOf(hill(62,163,8,37,50),hill(-72,282,6,31,43)),
            hazards=listOf(sand(38,216,8,14,-.35f),sand(-62,360,7,12,.35f)),
            greenShape=green(.012f,.008f,GolfGreenForm.TIER,1.5707964f,.25f,13f),
            fairwayRelief=GolfFairwayRelief(.012f,-.12f),landingZ=198f),
        // 10. Downhill restart: generous drive, then a softly crowned target and open front.
        ClassicHole(10,4,345f,finishX=30f,elevation=4f,greenRadius=16.5f,width=260f,
            route=r(n(0,0,12),n(-12,60,21),n(-23,129,34),n(-9,198,50),n(16,250,30),n(35,297,24),n(30,345,22)),
            elevationProfile=e(0 to 16,65 to 14,159 to 5,230 to 1,345 to 4),
            mounds=listOf(hill(-61,254,8,36,57)),
            hazards=listOf(sand(22,219,7,13,.5f),sand(56,340,7,12,-.3f)),
            greenShape=green(-.010f,.016f,GolfGreenForm.CROWN,0f,.07f,16f),
            fairwayRelief=GolfFairwayRelief(-.01f,.22f),landingZ=194f),
        // 11. Two alternating angles: the outside shelf opens a diagonal shoulder at the green.
        ClassicHole(11,4,375f,finishX=-52f,elevation=10f,greenRadius=16f,width=280f,
            route=r(n(0,0,12),n(-15,64,19),n(-37,134,31),n(-42,202,47),n(-10,248,27),n(-22,300,26),n(-52,375,21)),
            elevationProfile=e(0 to 2,97 to 8,188 to 11,258 to 4,375 to 10),
            mounds=listOf(hill(22,167,8,35,55),hill(-82,276,6,30,46)),
            hazards=listOf(sand(-12,214,8,16,.55f),sand(-78,367,7,12,-.2f)),
            greenShape=green(-.008f,.015f,GolfGreenForm.RIDGE,.5f,.20f,rotation=.4f),
            fairwayRelief=GolfFairwayRelief(.014f,.18f),landingZ=199f),
        // 12. Long diagonal terrace: a grass run-up right, bunker on the direct left-hand line.
        ClassicHole(12,3,168f,finishX=-24f,elevation=10f,greenRadius=17f,width=250f,
            route=r(n(0,0,12),n(-3,19,0),n(-10,105,0),n(-15,137,25),n(-24,168,22)),
            elevationProfile=e(0 to 3,40 to 1,88 to 0,126 to 6,168 to 10),
            mounds=listOf(hill(47,94,8,38,43)),
            hazards=listOf(sand(-53,157,8,13,-.5f)),
            greenShape=green(.010f,.012f,GolfGreenForm.TIER,.2f,.24f,aspect=1.12f,rotation=.5f),
            fairwayRelief=GolfFairwayRelief(-.008f,0f)),
        // 13. Lakeside par 5: cut the corner only if confident; the right shore is continuous land.
        ClassicHole(13,5,495f,finishX=12f,elevation=7f,greenRadius=17.5f,width=290f,
            route=r(n(0,0,12),n(13,60,19),n(39,137,32),n(50,205,48),n(63,262,27),n(64,322,29),n(41,376,50),n(19,437,25),n(12,495,23)),
            elevationProfile=e(0 to 10,85 to 6,179 to 3,263 to 6,339 to 1,417 to 3,495 to 7),
            mounds=listOf(hill(112,260,8,34,73),hill(-44,444,7,33,51)),
            hazards=listOf(lake(-20,300,43,66,-.15f),sand(18,220,8,14,.3f),sand(67,389,8,12,-.4f),sand(-15,490,7,12,.3f)),
            greenShape=green(.003f,.016f,GolfGreenForm.SWALE,.16f,.22f),
            fairwayRelief=GolfFairwayRelief(-.014f,-.18f),landingZ=201f),
        // 14. Short temptation: a broad lay-up left, a narrow direct approach over a sandy shoulder.
        ClassicHole(14,4,315f,finishX=42f,elevation=9f,greenRadius=15.5f,width=270f,
            route=r(n(0,0,12),n(-13,59,20),n(-25,123,31),n(-17,180,48),n(7,222,28),n(35,264,23),n(42,315,20)),
            elevationProfile=e(0 to 12,73 to 8,155 to 1,221 to 3,315 to 9),
            mounds=listOf(hill(59,179,7,33,46)),
            hazards=listOf(sand(15,196,10,17,-.45f),sand(68,311,7,12,.25f)),
            greenShape=green(.015f,.010f,GolfGreenForm.CROWN,0f,.07f,16f),
            fairwayRelief=GolfFairwayRelief(.012f,.18f),landingZ=177f),
        // 15. Long but fair: a shallow left bend and broad front entrance for a running long iron.
        ClassicHole(15,4,410f,finishX=-45f,elevation=6f,greenRadius=17f,width=280f,
            route=r(n(0,0,12),n(10,62,21),n(17,139,33),n(6,210,49),n(-17,271,29),n(-36,342,28),n(-45,410,24)),
            elevationProfile=e(0 to 11,74 to 9,169 to 3,241 to 5,321 to 1,410 to 6),
            mounds=listOf(hill(-43,170,8,36,48),hill(37,318,7,35,53)),
            hazards=listOf(sand(-27,226,8,15,.3f),sand(-72,405,7,12,-.3f)),
            greenShape=green(.006f,.017f),fairwayRelief=GolfFairwayRelief(-.014f,.22f),landingZ=207f),
        // 16. Long iron beside water, with a generous right-hand bailout all the way to the green.
        ClassicHole(16,3,184f,finishX=22f,elevation=4f,greenRadius=17.5f,width=270f,
            route=r(n(0,0,12),n(5,22,0),n(16,116,0),n(34,148,29),n(22,184,24)),
            elevationProfile=e(0 to 12,47 to 9,109 to 0,151 to 2,184 to 4),
            mounds=listOf(hill(77,101,8,35,45)),
            hazards=listOf(lake(-31,121,29,38,-.35f),sand(52,183,7,12,.5f)),
            greenShape=green(.014f,.012f,GolfGreenForm.TIER,-.4f,.20f,aspect=1.10f,rotation=.4f),
            fairwayRelief=GolfFairwayRelief(-.01f,0f)),
        // 17. Reverse diagonal shoulder: placement left rewards the approach, right sand tempts.
        ClassicHole(17,4,370f,finishX=61f,elevation=11f,greenRadius=16.5f,width=280f,
            route=r(n(0,0,12),n(-9,63,20),n(-21,132,32),n(-12,204,47),n(21,254,26),n(51,311,24),n(61,370,21)),
            elevationProfile=e(0 to 3,92 to 10,183 to 7,258 to 3,370 to 11),
            mounds=listOf(hill(36,176,8,35,50),hill(-43,288,7,34,50)),
            hazards=listOf(sand(17,222,8,16,-.4f),sand(88,363,7,12,.35f)),
            greenShape=green(-.015f,.010f,GolfGreenForm.RIDGE,.5f,.18f,rotation=.5f),
            fairwayRelief=GolfFairwayRelief(.012f,.20f),landingZ=200f),
        // 18. Sweeping finish: two comfortable landings, with a rear tier to reward the last approach.
        ClassicHole(18,5,520f,finishX=-32f,elevation=8f,greenRadius=18f,width=290f,
            route=r(n(0,0,12),n(-10,65,20),n(-35,139,32),n(-47,208,49),n(-57,267,26),n(-43,326,30),n(-14,385,52),n(-13,445,27),n(-32,520,24)),
            elevationProfile=e(0 to 15,78 to 12,178 to 4,257 to 7,342 to 1,422 to 3,520 to 8),
            mounds=listOf(hill(21,181,8,38,62),hill(-100,324,9,33,70)),
            hazards=listOf(lake(57,336,29,65,.2f),sand(-16,225,8,15,-.4f),sand(-43,397,8,13,.5f),sand(-61,515,8,12,-.3f)),
            greenShape=green(.008f,.014f,GolfGreenForm.TIER,1.5707964f,.25f),
            fairwayRelief=GolfFairwayRelief(.014f,-.20f),landingZ=204f)
    ).map { it.copy(decorTheme = GolfDecorTheme.FAIRY) }
}
