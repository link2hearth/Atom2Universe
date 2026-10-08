package com.Atom2Universe.app.games.golf.classic.core

/** Mountain relief belongs to long slopes; tee platforms, receptions and putting surfaces stay calm. */
object SnowPeaksCourse {
    private fun e(vararg p: Pair<Int, Int>) = p.map { GolfElevationNode(it.first.toFloat(), it.second.toFloat()) }
    private fun n(x: Float, z: Float, width: Float) = GolfRouteNode(x, z, width)

    private fun hole(number: Int, par: Int, length: Int, finish: Int, profile: List<GolfElevationNode>,
        first: Int = 175, second: Int = 335, radius: Float = 18f): ClassicHole {
        val side = if(number % 2 == 0) -1f else 1f
        val end = finish.toFloat(); val len = length.toFloat()
        val route = if(par == 3) listOf(n(0f,0f,12f), n(end*.4f,len-65f,0f),
            n(end,len-30f,44f), n(end,len,38f)) else buildList {
            add(n(0f,0f,12f)); add(n(side*7f,55f,40f))
            add(n(side*16f,first.toFloat(),66f))
            if(par == 5) {
                add(n(side*7f,(first+second)*.5f,44f))
                add(n(-side*12f,second.toFloat(),68f))
            }
            add(n(end*.65f,len-60f,46f)); add(n(end,len,38f))
        }
        val sand = GolfHazard(end + side*36f,len-6f,8f,12f,GolfLie.BUNKER,.3f,.1f)
        return ClassicHole(number,par,len,finishX=end,width=320f,greenRadius=radius,
            elevation=profile.last().height,route=route,elevationProfile=profile,
            hazards=listOf(sand),landingZ=first.toFloat(),fairwayStart=if(par==3)len-50f else 45f,
            greenShape=GolfGreenShape(aspect=1.03f,rotation=number*.21f,shape=.08f,
                slopeX=side*.006f,slopeZ=.009f),
            fairwayRelief=GolfFairwayRelief(side*.004f,.05f),
            landscapeStyle=GolfLandscapeStyle.SNOW_MOUNTAINS,
            attackTreeClearance=6f,
            attackLandings=if(par==3) listOf(GolfAttackLanding(end,len,GolfClub.WOOD3)) else buildList {
                add(GolfAttackLanding(side*16f,first.toFloat(),GolfClub.DRIVER))
                if(par==5)add(GolfAttackLanding(-side*12f,second.toFloat(),GolfClub.WOOD3,side*16f,first.toFloat()))
            })
    }

    val holes = listOf(
        // Long descent, then level run-up: the first mountain view is forgiving.
        hole(1,4,370,12,e(0 to 58,180 to 38,300 to 20,370 to 20),first=180),
        // Four hundred downhill metres followed by a plateau supporting the green.
        hole(2,5,550,-18,e(0 to 78,190 to 53,350 to 30,400 to 24,550 to 24),first=190,second=350),
        hole(3,3,125,8,e(0 to 20,70 to 26,105 to 30,125 to 30),radius=19f),
        // A modest ascent to the pass, with a long descent on its far side.
        hole(4,4,355,-22,e(0 to 24,150 to 33,210 to 33,300 to 16,355 to 16),first=150),
        hole(5,4,315,18,e(0 to 18,145 to 26,235 to 29,315 to 29),first=145),
        hole(6,5,480,10,e(0 to 12,150 to 20,300 to 29,400 to 36,480 to 36),first=150,second=300),
        hole(7,3,165,-10,e(0 to 54,95 to 32,140 to 25,165 to 25),radius=19f),
        // High saddle, descent to a second terrace, then an easy final shelf.
        hole(8,4,385,25,e(0 to 34,160 to 43,230 to 43,330 to 27,385 to 27),first=160),
        hole(9,4,350,-15,e(0 to 62,170 to 39,285 to 29,350 to 29),first=170),
        hole(10,4,335,20,e(0 to 30,145 to 20,220 to 20,295 to 28,335 to 28),first=145),
        hole(11,4,365,-25,e(0 to 15,150 to 23,250 to 30,305 to 30,365 to 30),first=150),
        hole(12,3,145,12,e(0 to 20,85 to 28,120 to 33,145 to 33),radius=19f),
        // Almost four hundred downhill metres followed by a large level terrace.
        hole(13,5,530,-12,e(0 to 85,180 to 60,340 to 38,400 to 30,530 to 30),first=180,second=340),
        hole(14,4,340,22,e(0 to 45,145 to 53,225 to 53,290 to 39,340 to 39),first=145),
        hole(15,4,365,16,e(0 to 45,165 to 33,210 to 33,295 to 42,365 to 42),first=165),
        hole(16,3,190,-14,e(0 to 72,110 to 47,160 to 35,190 to 35),radius=20f),
        hole(17,4,375,-18,e(0 to 28,155 to 36,235 to 36,315 to 17,375 to 17),first=155),
        hole(18,5,565,8,e(0 to 88,190 to 61,350 to 37,410 to 29,565 to 29),first=190,second=350,radius=19f)
    )
}
