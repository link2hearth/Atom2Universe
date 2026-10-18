package com.Atom2Universe.app.games.roguelike

import android.graphics.Bitmap

/**
 * Les icônes des reliques, une par relique, dessinées à la main en 16 × 16 (elles remplacent
 * la planche 64x64.png, retirée le 03/10/2026).
 *
 * Chaque icône est une grille de lettres : une lettre = une couleur de [palette], le point est
 * transparent. Le contour sombre n'est pas dessiné : il s'ajoute tout seul autour de la
 * silhouette (voir [pixels]), c'est pourquoi les grilles laissent toujours un pixel libre au bord.
 *
 * [pixels] est du Kotlin pur : RelicArtTest vérifie les grilles et en tire une planche PNG
 * (app/build/relic-preview/) pour juger les dessins sans compiler d'APK.
 */
internal object RelicArt {
    const val SIZE = 16
    private const val OUTLINE = 0xFF172034.toInt()

    val palette: Map<Char, Int> = mapOf(
        'k' to OUTLINE,
        // Neutres : métal, or, bois, os, pierre
        'w' to 0xFFF6F8F2.toInt(), 'l' to 0xFFDCE4EA.toInt(), 'm' to 0xFF9EAFC7.toInt(), 'M' to 0xFF5D6B82.toInt(),
        'g' to 0xFFE3C46F.toInt(), 'G' to 0xFFA4803A.toInt(), 'b' to 0xFF9A7350.toInt(), 'B' to 0xFF5E4330.toInt(),
        'h' to 0xFFE9DDC2.toInt(), 's' to 0xFF9A958A.toInt(), 'S' to 0xFF625E57.toInt(),
        // Feu
        'y' to 0xFFFFE27A.toInt(), 'o' to 0xFFF39A33.toInt(), 'r' to 0xFFD24A22.toInt(), 'R' to 0xFF8A2416.toInt(),
        // Glace
        'c' to 0xFFCDF2FB.toInt(), 'i' to 0xFF77C4E4.toInt(), 'I' to 0xFF3478B0.toInt(),
        // Foudre (le jaune clair est celui du feu)
        'Y' to 0xFFE0A82A.toInt(),
        // Poison et ténèbres
        'p' to 0xFF9EDC57.toInt(), 'P' to 0xFF4F9B34.toInt(), 'q' to 0xFF2D5F2A.toInt(),
        'v' to 0xFFA97BDB.toInt(), 'V' to 0xFF5F4192.toInt(),
        // Sang
        'd' to 0xFFC8343A.toInt(), 'D' to 0xFF7A1A26.toInt(), 'n' to 0xFFF39AA0.toInt(),
    )

    /** Les pixels ARGB de l'icône, ligne par ligne, contour compris. */
    fun pixels(relic: Relic): IntArray {
        val rows = sprite(relic)
        val fill = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val ch = rows[y][x]
            if (ch != '.') fill[y * SIZE + x] = palette.getValue(ch)
        }
        // Le contour : tout pixel vide qui touche la silhouette par un côté.
        val out = fill.copyOf()
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (fill[y * SIZE + x] != 0) continue
            val touches = (x > 0 && fill[y * SIZE + x - 1] != 0) || (x < SIZE - 1 && fill[y * SIZE + x + 1] != 0) ||
                (y > 0 && fill[(y - 1) * SIZE + x] != 0) || (y < SIZE - 1 && fill[(y + 1) * SIZE + x] != 0)
            if (touches) out[y * SIZE + x] = OUTLINE
        }
        return out
    }

    private val bitmaps = HashMap<Relic, Bitmap>()

    fun icon(relic: Relic): Bitmap = bitmaps.getOrPut(relic) {
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels(relic), 0, SIZE, 0, 0, SIZE, SIZE)
        }
    }

    /** La grille d'une relique. Un `when` sans `else` : une nouvelle relique ne compile pas sans son icône. */
    fun sprite(relic: Relic): List<String> = when (relic) {
        // ── Guerrier ──
        Relic.FER_ROUGE -> listOf(
            "................",
            ".........ooooo..",
            "......o..oyyyo..",
            ".........oy.yo..",
            ".........oyyyo..",
            "........rooooo..",
            ".......rr.......",
            "......rR........",
            ".....RM......y..",
            "....MM..........",
            "...GG...........",
            "..bbB...........",
            ".bbB............",
            ".BB.............",
            "................",
            "................",
        )
        Relic.STONESKIN -> listOf(
            "................",
            "..c..........c..",
            "..cc........ci..",
            "..ccccc..iiiiI..",
            "..ccciiiiiiiiI..",
            "...ciiiwiiiiI...",
            "...ciiwwwiiiI...",
            "...ciiiwiiiiI...",
            "...ciiiiiiiiI...",
            "....ciiiiiiI....",
            "....cIIIIIII....",
            "....ciiiiiiI....",
            ".....ciiiiI.....",
            "......c..c......",
            "................",
            "................",
        )
        Relic.MARTEAU_FOUDRE -> listOf(
            "................",
            "................",
            ".llllllllM......",
            ".lmmmmmmmM...wy.",
            ".lmmmmmmmM..wy..",
            ".lmmmmmmmM.wy...",
            ".MMMMMMMMM.wyyy.",
            "....GG.......wy.",
            "....bB......wy..",
            "....bB.....wy...",
            "....bB.....y....",
            "....bB..........",
            "....bB..........",
            "....GG..........",
            "................",
            "................",
        )
        Relic.VENOM -> listOf(
            "................",
            "................",
            ".....PPPPPP.....",
            "...PPppppppPP...",
            "..PppkypppppPq..",
            ".PpppppppppPPq..",
            ".qqqqqqPPPPPPq..",
            "..wDDDDDPPPPPq..",
            "..wDDDDDdPPPPq..",
            "...pppppppPPq...",
            "..p.qqqqqqPPq...",
            "..P.......PPPq..",
            "...........PPPq.",
            "............PPq.",
            "................",
            "................",
        )
        Relic.WAR_CRY -> listOf(
            "................",
            ".h............h.",
            ".h............h.",
            ".hh....ll....hh.",
            "..hh.llmmmm.hh..",
            "..hhlmmmmmmMhh..",
            "...hlmmmmmmMh...",
            "....lmmmmmmM....",
            "....GGGGGGGG....",
            "....mkkmmkkM....",
            "....mkkmmkkM....",
            "....mmmmmmmM....",
            "....MMm..mMM....",
            ".....M....M.....",
            "................",
            "................",
        )
        // ── Voleur ──
        Relic.COCKTAIL -> listOf(
            "................",
            "......y.........",
            ".....yoy.y......",
            ".....oroyo......",
            "......rRr.......",
            "......hhh.......",
            ".......Ph.......",
            ".......Ph.......",
            "......PPPh......",
            ".....pPPPPP.....",
            ".....pooooP.....",
            ".....poyooP.....",
            ".....pooooP.....",
            ".....pooooP.....",
            "......PPPP......",
            "................",
        )
        Relic.CRYSTALLIZE -> listOf(
            "................",
            ".......ci.......",
            "......ccii......",
            "......wcii..ci..",
            "......cciI.ccii.",
            "..ci..cciI.wciI.",
            ".cciI.cciI.cciI.",
            ".wciI.cciI.cciI.",
            ".cciI.cciI.cciI.",
            ".cciI.cciI.cciI.",
            ".cciI.cciI.cciI.",
            ".cciI.cciI.cciI.",
            ".ssssssssssssss.",
            "..SSSSSSSSSSSS..",
            "................",
            "................",
        )
        Relic.HASTE -> listOf(
            "................",
            "................",
            "...ddd...ddd....",
            "..dnndd.ddddd...",
            ".dnndddddyyddd..",
            ".dndddddyydddD..",
            ".dddddyyyydddD..",
            "..dddddyyddDD...",
            "...dddyydDDD....",
            "....ddydDDD.....",
            ".....ddDDD......",
            "......dDD.......",
            ".......D........",
            "................",
            "................",
            "................",
        )
        Relic.POISONED_BLADES -> listOf(
            "................",
            ".......l........",
            "......lmM.......",
            "......lmM.......",
            "......lmP.......",
            "......lpP.......",
            "......ppP.......",
            "......pPq.......",
            "......pPq.......",
            "...GGGGGGGGG....",
            "......bbB..p....",
            "......bbB...p...",
            "......bbB..ppP..",
            "......GGG...P...",
            "................",
            "................",
        )
        Relic.HUNTERS_MARK -> listOf(
            "................",
            "............l...",
            ".....ddddd.l.B..",
            "...ddwwwwwddB.l.",
            "..ddwwdddwwBdl..",
            "..dwddddddBwd...",
            ".dwwddwwwBdwwd..",
            ".dwddwwdBwddwd..",
            ".dwddwdddwddwd..",
            ".dwddwwdwwddwd..",
            ".dwwddwwwddwwd..",
            "..dwdddddddwd...",
            "..ddwwdddwwdd...",
            "...ddwwwwwdd....",
            ".....ddddd......",
            "................",
        )
        // ── Vagabond ──
        Relic.LANTERNE -> listOf(
            "................",
            ".......GG.......",
            "......G..G......",
            "......GGGG......",
            "....GGGGGGGG....",
            ".....MooooM.....",
            ".....MooyoM.....",
            ".....MoyyoM.....",
            "..y..MywyyM..y..",
            ".....MywwyM.....",
            ".....MoyyoM.....",
            ".....MooooM.....",
            "....GGGGGGGG....",
            ".....GGGGGG.....",
            "................",
            "................",
        )
        Relic.SLOW -> listOf(
            "................",
            "................",
            "................",
            "....cccc........",
            "...cciiic.......",
            "..cciIIIic..h.h.",
            "..ciIcccIi..h.h.",
            "..ciIcIcIi..hhh.",
            "..ciIccIiI..hkh.",
            "..cciIIiiI.hhhh.",
            "...ciiiiI.hhhh..",
            ".hhhhhhhhhhhhh..",
            "..ssssssssss....",
            "................",
            "................",
            "................",
        )
        Relic.CHAIN_LIGHTNING -> listOf(
            "................",
            "................",
            "...Y........Y...",
            "...y........y...",
            ".YywyY.Y.YYywyY.",
            "...y..Y.Y...y...",
            "...Y........Y...",
            "...........Y....",
            "..........Y.....",
            "..........Y.....",
            ".......Y.Y......",
            ".......y.Y......",
            ".....YywyY......",
            ".......y........",
            ".......Y........",
            "................",
        )
        Relic.CHAMPIGNON -> listOf(
            "................",
            ".............p..",
            "......vvvv......",
            "....vvvvppvv....",
            "...vppvvppvvV...",
            "..vvppvvvvvpVV..",
            "..vvvvvvvvpVVV..",
            "..VVhhhhhhhhVV..",
            "......hhhs......",
            ".p....hhhs......",
            "......hhhs......",
            ".....hhhhss.....",
            ".....hhhhss.....",
            "....qqqqqqqq....",
            "................",
            "................",
        )
        Relic.WHIRLWIND -> listOf(
            "................",
            "............ll..",
            "...........llm..",
            "..........lmmM..",
            "..........mMM...",
            ".........GG.....",
            "........bB...c..",
            ".......bB.....c.",
            "......bB....w.c.",
            ".....bB.....w.c.",
            "....bB.....w.c..",
            "...bB.....w.c...",
            "..bB............",
            ".GG.............",
            "................",
            "................",
        )
        // ── Barbare ──
        Relic.BLAZING_AXE -> listOf(
            "................",
            "..y....GG....y..",
            "..or...bB...ro..",
            "...olm.bB.mlo...",
            "..olmmMbBMmmlo..",
            ".olmmmMbBMmmmlo.",
            ".ylmmmMbBMmmmly.",
            ".olmmmMbBMmmmlo.",
            "..olmmMbBMmmlo..",
            "...olm.bB.mlo...",
            ".......bB.......",
            ".......GG.......",
            ".......bB.......",
            ".......bB.......",
            ".......GG.......",
            "................",
        )
        Relic.NORTHERN_BREATH -> listOf(
            "................",
            "................",
            ".ccc....ccc.....",
            "....cccc...ccc..",
            "............w...",
            "...w.......www..",
            "..www.......w...",
            "...w............",
            "..iii....iii....",
            ".....iiii...iii.",
            "................",
            "........w.......",
            ".......www......",
            ".ccc....w.ccc...",
            "....ccc....cc...",
            "................",
        )
        Relic.THUNDER_CLUB -> listOf(
            "................",
            "......bbbb......",
            ".....bbmbbB.....",
            "...y.bbbbmB.....",
            "..y.mbbbbbBm....",
            ".yyy.bmbbbB..y..",
            "..y..bbbbmB.y...",
            ".y....bbbB...y..",
            "......bbbB......",
            ".......bB.......",
            ".......bB.......",
            ".......bB.......",
            ".......bB.......",
            ".......GG.......",
            "................",
            "................",
        )
        Relic.VENOMOUS_WOUND -> listOf(
            "................",
            "................",
            "........D.......",
            "......dD....D...",
            ".....dD...dD....",
            "....dD...dD.....",
            "...dD...dD....D.",
            "..dD...dD...dD..",
            "..D...dD...dD...",
            ".....dD...dD....",
            "..p.dD...dD.....",
            "..P.D...dD......",
            ".......dD.......",
            "....p..D........",
            "....P...........",
            "................",
        )
        Relic.SEISMIC_STRIKE -> listOf(
            "................",
            "..........ss....",
            ".....ss...sS....",
            ".....sS.........",
            "..s.........sS..",
            "............SS..",
            "................",
            "......s..s......",
            ".....sS..Ss.....",
            "...ssSB..BSss...",
            ".ssSSBB..BBSSss.",
            ".SSBBBB..BBBBSS.",
            ".BBBBBB..BBBBBB.",
            ".BBBBBB..BBBBBB.",
            "................",
            "................",
        )
        // ── Mage ──
        Relic.FIREBALL -> listOf(
            "................",
            "................",
            ".........ror....",
            "........ooyoo...",
            ".......roywyor..",
            ".......oywwwyo..",
            "......oroywyor..",
            ".....oroooyoo...",
            "....roorrror....",
            "...rorrR........",
            "...RrR..r.......",
            "..rR..o.........",
            ".R..............",
            "................",
            "................",
            "................",
        )
        Relic.FREEZING_RAIN -> listOf(
            "................",
            "................",
            "....llll........",
            "...llllll.lll...",
            "..llllllllllll..",
            ".lllllllllllmmm.",
            ".mmmmmmmmmmmmmM.",
            "..MMMMMMMMMMMM..",
            "................",
            "...c...c...c....",
            "..i...i...i.....",
            "................",
            ".....c...c...c..",
            "....i...i...i...",
            "................",
            "................",
        )
        Relic.LIGHTNING -> listOf(
            "................",
            ".......wyyyy....",
            "......wyyyY.....",
            ".....wyyyY......",
            "....wyyyY.......",
            "...wyyyyyyyyY...",
            "...YYYYwyyyY....",
            ".......wyyY.....",
            "......wyyY......",
            ".....wyyY.......",
            ".....wyY........",
            "....wyY.........",
            "....wY..........",
            "...wY...........",
            "...Y............",
            "................",
        )
        Relic.ACID_FLASK -> listOf(
            "................",
            "......bbbb..p...",
            "......BBBB......",
            ".......lm...p...",
            ".......lm.......",
            "....lppppppm....",
            "...lpppppppPm...",
            "..lppwpppppPPm..",
            "..lpppppppPPPm..",
            "..lppppwppPPPm..",
            "..mpppppppPPPm..",
            "...mPppppPPPm...",
            "....mmPPPPmm....",
            "................",
            "................",
            "................",
        )
        Relic.HOLY_LIGHT -> listOf(
            "................",
            ".......g........",
            "..g....g....g...",
            "...g.......g....",
            "......yyy.......",
            ".....ywwwy......",
            "....ywwwwwy.....",
            ".gg.ywwwwwy.gg..",
            "....ywwwwwy.....",
            ".....ywwwy......",
            "......yyy.......",
            "...g.......g....",
            "..g....g....g...",
            ".......g........",
            "................",
            "................",
        )
        // ── Nécromancien ──
        Relic.METEOR -> listOf(
            "................",
            "........y.......",
            ".......yo.......",
            "...o...oyo..o...",
            "...oo.oyyo.oo...",
            "..oyoooywyooyo..",
            "..royoywwyoyor..",
            "..rooyywwyyoor..",
            "...rooywwyoor...",
            "....rrooooRR....",
            "...hbbbbbbbbh...",
            "...hBBBBBBBBh...",
            ".hbbbbbhhbbbbbh.",
            ".hBBBBBhhBBBBBh.",
            "................",
            "................",
        )
        Relic.ICE_SHARD -> listOf(
            "................",
            "................",
            ".............c..",
            "............ci..",
            "....w.....cwiI..",
            ".........cwiI...",
            "........cwiiI...",
            ".......cwiiI....",
            "......cciiI.....",
            ".....cciiI......",
            "....cciI........",
            "...cciI.....c...",
            "...cI...........",
            "..cI............",
            "..I.............",
            "................",
        )
        Relic.MAGIC_MISSILE -> listOf(
            "................",
            "................",
            "............y...",
            ".........vvywy..",
            ".......vv...y...",
            ".....VV.........",
            "................",
            "....VV...y......",
            "......vvywy.....",
            ".........y......",
            "................",
            "............y...",
            ".........vvywy..",
            ".......vv...y...",
            ".....VV.........",
            "................",
        )
        Relic.PESTE -> listOf(
            "................",
            "....MMMMMM......",
            "....MMMMMM......",
            "..MMMMMMMMMM....",
            "....hhhhhh......",
            "...hhhhhhhh.....",
            "...hhkkkhhhh....",
            "...hhkpkhhhhhh..",
            "...hhkkkhhhhhhh.",
            "....hhhhhhhhhh..",
            ".....hhhhh.hh...",
            "......hhh..h....",
            ".p..........p...",
            "..p.....p.......",
            "................",
            "................",
        )
        Relic.PONCTION -> listOf(
            "................",
            "......d..d......",
            ".......d........",
            "...gddddddddg...",
            "...gggggggggG...",
            "...lgggdggggG...",
            "....gggggggG....",
            ".....gggggG.....",
            ".......gG.......",
            ".......gG.......",
            "......ggGG......",
            ".....gggggG.....",
            "....ggggggGG....",
            "................",
            "................",
            "................",
        )
        // ── Hors grille ──
        Relic.HOURGLASS -> listOf(
            "................",
            "..bbbbbbbbbbb...",
            "..BBBBBBBBBBB...",
            "..b.lcccccl.b...",
            "..b.lgggggl.b...",
            "..b..lgggl..b...",
            "..b...lgl...b...",
            "..b....g....b...",
            "..b...lgl...b...",
            "..b..lcgcl..b...",
            "..b.lccgccl.b...",
            "..b.lgggggl.b...",
            "..bbbbbbbbbbb...",
            "..BBBBBBBBBBB...",
            "................",
            "................",
        )
    }
}
