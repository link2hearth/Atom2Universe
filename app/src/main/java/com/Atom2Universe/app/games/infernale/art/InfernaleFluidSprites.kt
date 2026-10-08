package com.Atom2Universe.app.games.infernale.art

/** Les raccords, chambres et commandes rendent les fonctions lisibles sans étiquette. */
internal fun InfernalePixels.pneumatic(name: String) {
    val rod = 39 + (t * 16).toInt()
    when (name) {
        "chamber" -> { disk(32, 32, 23, INK); disk(32, 32, 21, MINT); port(8, 32); port(56, 32); panel(23, 20, 41, 44, LIGHT); for (y in 25..39 step 7) disk(32, y, 2, MINT); bolt(19, 17); bolt(45, 47) }
        "receiver" -> { disk(17, 34, 13, INK); disk(47, 34, 13, INK); panel(17, 21, 48, 48, MINT); disk(17, 34, 10, MINT); disk(47, 34, 10, MINT); gauge(33, 16, 10, MINT); foot(18, 49); foot(47, 49); line(18, 26, 46, 26, LIGHT) }
        "pipe" -> { bar(7, 43, 32, 43, MINT, 11); bar(32, 43, 32, 17, MINT, 11); bar(32, 17, 57, 17, MINT, 11); port(7, 43); port(57, 17); panel(26, 27, 38, 34, SHADE) }
        "hose" -> { for (pass in 0..1) for (i in 0..42) { val x = 10 + i; val y = 17 + (kotlin.math.sin(i * Math.PI / 42) * 29).toInt(); disk(x, y, if (pass == 0) 4 else 2, if (pass == 0) INK else MINT) }; port(8, 16, GOLD); port(55, 16, GOLD); line(21, 39, 26, 43, LIGHT) }
        "manifold" -> { panel(18, 10, 44, 54, MINT); for (y in 18..46 step 14) { bar(43, y, 57, y, METAL, 6); port(57, y) }; port(15, 32, GOLD); bolt(25, 17); bolt(25, 47) }
        "shutoff_valve" -> { bar(5, 36, 59, 36, MINT, 12); panel(23, 27, 41, 45, METAL); bar(32, 17, 32, 29, METAL, 5); val dy = if (active) 9 else 0; bar(18, 15 - dy, 46, 15 + dy, CORAL, 6); bolt(32, 15); port(6, 36); port(58, 36) }
        "check_valve" -> { bar(5, 32, 59, 32, MINT, 9); panel(14, 21, 50, 43, MINT); poly(INK, 22,25, 33,32, 22,39); rect(34, 24, 37, 40, SHADE); spring(38, 32, 8, GOLD, 2, 3); port(7, 32); port(57, 32) }
        "regulator" -> { panel(21, 27, 43, 52, MINT); port(12, 37); port(52, 37); bar(12, 37, 22, 37); bar(42, 37, 52, 37); panel(24, 7, 40, 18, CORAL); bar(32, 18, 32, 29, METAL, 5); gauge(32, 31, 12, GOLD) }
        "relief_valve" -> { bar(5, 45, 58, 45, MINT, 9); panel(23, 15, 41, 47, MINT); spring(26, 29, 12, GOLD, 3, 7); panel(20, 9, 44, 16, METAL); bar(41, 20, 53, 20, METAL, 5); if (active) { line(54, 16, 59, 12, MINT); line(55, 20, 61, 20, MINT) } }
        "compressor" -> { panel(8, 38, 55, 53, MINT); foot(15, 53); foot(49, 53); panel(11, 15, 30, 36, METAL); for (y in 20..31 step 5) rect(8, y, 32, y + 2, SHADE); gear(43, 30, 12, 8, CORAL); bar(24, 36, 36, 39, WOOD, 4); port(54, 44) }
        "vacuum_pump" -> { panel(11, 37, 53, 53, MINT); disk(25, 27, 17, INK); disk(25, 27, 15, METAL); spokes(25, 27, 10, 3, -a, MINT); bar(42, 39, 42, 13, METAL, 8); port(42, 11); poly(CORAL, 44,20, 49,27, 46,27, 46,34, 42,34, 42,27, 39,27); foot(17, 53); foot(47, 53) }
        "single_cylinder" -> { panel(9, 19, 39, 45, MINT); bar(35, 32, rod, 32, METAL, 6); spring(15, 32, 15, GOLD, 4, 6); rect(31, 23, 35, 41, SHADE); port(14, 49); bolt(rod, 32) }
        "double_cylinder" -> { panel(8, 20, 39, 44, MINT); port(13, 49); port(33, 49); bar(35, 32, rod, 32, METAL, 6); rect(27, 23, 31, 41, SHADE); line(13, 27, 21, 27, LIGHT); line(13, 37, 21, 37, LIGHT); bolt(rod, 32) }
        "air_motor" -> { disk(31, 32, 20, INK); disk(31, 32, 18, MINT); spokes(31, 32, 13, 5, a, METAL); port(10, 18); port(10, 46); bar(49, 32, 60, 32, METAL, 7); foot(31, 53) }
        "nozzle" -> { panel(6, 24, 25, 40, MINT); poly(INK, 25,24, 49,29, 49,35, 25,40); poly(METAL, 27,27, 47,30, 47,34, 27,37); rect(47, 29, 50, 35, INK); for (i in 0..2) line(53, 30 + i * 2, 59, 26 + i * 6, if (active) MINT else SHADE); port(8, 32) }
        "launcher" -> { panel(6, 21, 59, 34, MINT); panel(43, 18, 60, 37, METAL); disk(16, 44, 11, INK); disk(16, 44, 9, MINT); bar(29, 34, 29, 52, WOOD, 7); gauge(17, 44, 8, GOLD); rect(56, 24, 60, 31, INK) }
        "quick_exhaust" -> { panel(19, 21, 45, 47, MINT); port(10, 34); port(54, 34); bar(10, 34, 20, 34); bar(44, 34, 54, 34); panel(26, 9, 38, 22, SHADE); for (y in 11..18 step 3) rect(27, y, 37, y + 1, LIGHT); poly(CORAL, 26,38, 32,27, 38,38); bolt(23, 25) }
        "venturi" -> { poly(INK, 5,19, 21,19, 31,28, 42,19, 59,19, 59,45, 42,45, 31,36, 21,45, 5,45); poly(MINT, 8,22, 20,22, 31,31, 43,22, 56,22, 56,42, 43,42, 31,33, 20,42, 8,42); bar(31, 35, 31, 55, METAL, 5); port(31, 54); line(10, 32, 23, 32, LIGHT) }
        else -> error("Unknown pneumatic sprite: $name")
    }
}

internal fun InfernalePixels.hydraulic(name: String) {
    val rod = 39 + (t * 17).toInt()
    when (name) {
        "reservoir" -> { panel(8, 18, 56, 52, METAL); panel(14, 24, 50, 46, LIGHT); rect(16, 43 - (level * 16).toInt(), 48, 44, METAL); panel(17, 10, 28, 18, SHADE); bar(43, 20, 43, 8, METAL, 5); foot(15, 52); foot(49, 52) }
        "accumulator" -> { disk(32, 18, 15, INK); disk(32, 18, 13, LILAC); panel(17, 18, 47, 49, METAL); disk(32, 47, 15, INK); disk(32, 47, 13, METAL); panel(24, 17, 40, 47, LIGHT); val y = 23 + (level * 15).toInt(); rect(26, y, 38, 45, METAL); rect(25, y, 39, y + 3, INK); port(32, 57); rect(28, 19, 36, 23, LILAC) }
        "rigid_pipe" -> { bar(6, 18, 47, 18, METAL, 9); bar(47, 18, 47, 49, METAL, 9); port(7, 18, GOLD); panel(42, 44, 53, 52, GOLD); panel(23, 12, 31, 25, SHADE); bolt(27, 18) }
        "hose" -> { for (pass in 0..1) for (i in 0..43) { val x = 10 + i; val y = 32 + (kotlin.math.sin(i * Math.PI * 2 / 43) * 14).toInt(); disk(x, y, if (pass == 0) 4 else 2, if (pass == 0) INK else METAL) }; port(8, 32, LILAC); port(56, 32, LILAC); for (x in 14..20 step 3) line(x, 38, x, 43, SHADE) }
        "manifold" -> { panel(8, 22, 56, 44, METAL); for (x in 16..48 step 16) { bar(x, 13, x, 23, METAL, 5); port(x, 11, GOLD); bar(x, 43, x, 52, METAL, 5); port(x, 53, GOLD) }; bolt(14, 32); bolt(50, 32) }
        "directional_valve" -> { panel(10, 23, 54, 43, METAL); rect(31, 25, 33, 41, INK); line(16, 37, 26, 28, CORAL, 3); line(38, 28, 48, 37, CORAL, 3); bar(53, 29, 57, 12, WOOD, 5); bolt(57, 12); for (x in listOf(20, 43)) { port(x, 17); port(x, 49) } }
        "flow_valve" -> { bar(5, 35, 59, 35, METAL, 10); panel(21, 25, 43, 46, METAL); bar(32, 12, 32, 33, GOLD, 4); panel(20, 7, 44, 15, CORAL); poly(INK, 28,26, 36,26, 32,38); port(8, 35, GOLD); port(56, 35, GOLD) }
        "check_valve" -> { bar(5, 32, 59, 32, METAL, 10); panel(16, 19, 48, 45, METAL); disk(if (active) 31 else 26, 32, 6, INK); disk(if (active) 31 else 26, 32, 4, GOLD); spring(34, 32, 10, LILAC, 2, 5); line(20, 23, 20, 41, SHADE, 3) }
        "relief_valve" -> { panel(17, 17, 43, 48, METAL); port(11, 38, GOLD); port(49, 28, GOLD); panel(24, 7, 36, 18, LILAC); spring(22, 30, 15, CORAL, 4, 7); bar(29, 47, 29, 55, METAL, 6); port(29, 55); line(37, 38, 42, 32, INK, 2) }
        "filter" -> { panel(20, 16, 44, 52, METAL); panel(15, 10, 49, 20, SHADE); port(9, 15, GOLD); port(55, 15, GOLD); panel(25, 24, 39, 45, LIGHT); for (y in 27..40 step 5) { line(26, y, 37, y + 4, MINT); line(26, y + 4, 37, y, SHADE) }; port(32, 56) }
        "pump" -> { panel(12, 14, 52, 49, METAL); port(6, 31, GOLD); port(58, 31, GOLD); gear(26, 27, 10, 8, LILAC); gear(39, 38, 9, 8, GOLD, -a); foot(18, 49); foot(46, 49); bolt(46, 19) }
        "hand_pump" -> { panel(18, 24, 35, 53, METAL); panel(10, 52, 47, 58, SHADE); val y = 10 + (t * 10).toInt(); bar(26, 29, 26, 19, METAL, 5); bar(26, 19, 54, y, WOOD, 7); bolt(26, 19); port(39, 43, GOLD); bar(34, 43, 41, 43, METAL, 5) }
        "cylinder" -> { panel(8, 17, 38, 47, METAL); bar(35, 32, rod, 32, METAL, 9); for (y in listOf(21, 43)) line(11, y, 35, y, INK, 2); port(14, 51, GOLD); port(32, 51, GOLD); bolt(rod + 1, 32); panel(6, 15, 13, 49, LILAC) }
        "ram" -> { panel(18, 31, 46, 52, METAL); val y = 26 - (t * 16).toInt(); panel(24, y, 40, 33, SHADE); panel(18, y - 5, 46, y + 2, GOLD); panel(11, 51, 53, 58, LILAC); port(50, 43, GOLD) }
        "motor" -> { panel(13, 15, 46, 49, METAL); disk(30, 32, 13, INK); disk(30, 32, 11, LILAC); spokes(30, 32, 8, 6); bar(43, 32, 59, 32, METAL, 8); port(9, 21, GOLD); port(9, 43, GOLD); foot(30, 50) }
        "nozzle" -> { panel(6, 22, 28, 42, METAL); poly(INK, 28,22, 45,28, 45,36, 28,42); poly(GOLD, 29,25, 43,30, 43,34, 29,39); port(9, 32, GOLD); for (i in 0..2) { val x = 49 + i * 5; disk(x, 32 + (i - 1) * 3, 2, if (active) METAL else SHADE) } }
        "waterjet_head" -> { panel(24, 7, 40, 28, METAL); port(18, 20, GOLD); poly(INK, 21,28, 43,28, 36,44, 28,44); poly(LILAC, 24,30, 40,30, 34,42, 30,42); line(32, 44, 32, 56, METAL, if (active) 3 else 1); panel(9, 56, 55, 61, WOOD); if (active) { line(29, 52, 24, 48, LIGHT); line(35, 52, 40, 48, LIGHT) } }
        "abrasive_feeder" -> { poly(INK, 8,11, 55,11, 55,28, 38,46, 27,46, 8,28); poly(WOOD, 11,14, 52,14, 52,26, 36,43, 29,43, 11,26); for (y in 19..29 step 5) for (x in 17..47 step 7) disk(x, y, 1, GOLD); panel(27, 43, 38, 57, METAL); bar(13, 29, 13, 56, SHADE, 4); bar(50, 29, 50, 56, SHADE, 4) }
        "press_frame" -> { panel(6, 8, 15, 58, LILAC); panel(49, 8, 58, 58, LILAC); panel(6, 8, 58, 18, METAL); panel(6, 49, 58, 58, METAL); panel(25, 18, 39, 31, METAL); val y = 34 + (t * 10).toInt(); bar(32, 29, 32, y, METAL, 6); panel(20, y, 44, y + 5, GOLD); bolt(10, 13); bolt(54, 13) }
        else -> error("Unknown hydraulic sprite: $name")
    }
}

