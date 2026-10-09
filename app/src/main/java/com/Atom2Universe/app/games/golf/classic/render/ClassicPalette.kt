package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfLandscapeStyle

internal val ClassicHole.highlands get() = landscapeStyle != GolfLandscapeStyle.PARKLAND
internal val ClassicHole.snowy get() = landscapeStyle == GolfLandscapeStyle.SNOW_MOUNTAINS

/** Baked colours and shader palettes describe the same surfaces. */
internal class ClassicPalette(hole: ClassicHole) {
    val rough=if(hole.snowy) C(.86f,.90f,.94f) else if(hole.highlands) C(.48f,.43f,.26f) else C(.30f,.46f,.21f)
    val fairway=if(hole.snowy) C(.37f,.51f,.48f) else if(hole.highlands) C(.40f,.51f,.32f) else C(.39f,.58f,.25f)
    val green=if(hole.snowy) C(.40f,.58f,.49f) else if(hole.highlands) C(.49f,.61f,.40f) else C(.46f,.65f,.32f)
    val fringe=if(hole.snowy) C(.52f,.65f,.61f) else if(hole.highlands) C(.35f,.47f,.30f) else C(.32f,.52f,.24f)
    val sand=if(hole.snowy) C(.68f,.70f,.75f) else if(hole.highlands) C(.69f,.66f,.56f) else C(.83f,.76f,.58f)
    val water=if(hole.islands.isNotEmpty()) C(.045f,.29f,.43f) else if(hole.highlands) C(.13f,.32f,.39f) else C(.15f,.40f,.43f)
}
