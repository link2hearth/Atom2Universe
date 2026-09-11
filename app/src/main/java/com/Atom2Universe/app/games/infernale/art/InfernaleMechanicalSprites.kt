package com.Atom2Universe.app.games.infernale.art

import kotlin.math.*

internal fun InfernalePixels.structure(name: String) {
    when (name) {
        "beam" -> { panel(4, 24, 60, 39, WOOD); line(10, 31, 53, 31, GOLD); bolt(11, 31); bolt(53, 31) }
        "plate" -> { panel(9, 12, 55, 51, METAL); for (x in listOf(15, 48)) for (y in listOf(18, 44)) bolt(x, y); line(19, 21, 42, 21, LIGHT) }
        "block" -> { poly(INK, 9,20, 40,9, 55,20, 55,47, 24,58, 9,46); poly(METAL, 11,22, 24,31, 24,54, 11,45); poly(SHADE, 26,31, 53,22, 53,45, 26,54); poly(LIGHT, 12,20, 40,12, 51,20, 25,29) }
        "frame" -> { panel(5, 9, 15, 56, WOOD); panel(49, 9, 59, 56, WOOD); panel(5, 9, 59, 19, WOOD); panel(5, 47, 59, 56, WOOD); bolt(10, 14); bolt(54, 14); bar(14, 46, 48, 20, GOLD, 5) }
        "ground_anchor" -> { panel(7, 43, 57, 51, SHADE); for (x in 9..51 step 8) line(x, 53, x - 4, 59, SHADE); bar(32, 42, 32, 24, METAL, 10); ring(32, 18, 10, METAL, 5); bolt(12, 47); bolt(52, 47) }
        "spacer" -> { panel(14, 21, 50, 43, LILAC); for (x in listOf(14, 49)) { disk(x, 32, 12, INK); disk(x, 32, 10, METAL) }; disk(49, 32, 6, INK); disk(49, 32, 3, 0); line(19, 25, 42, 25, LIGHT) }
        "bolt" -> { panel(27, 24, 37, 55, METAL); for (y in 32..51 step 5) line(25, y + 3, 38, y, SHADE); poly(INK, 19,13, 32,6, 45,13, 45,26, 32,33, 19,26); poly(METAL, 22,15, 32,9, 42,15, 42,24, 32,30, 22,24); line(25, 24, 39, 15, INK, 3) }
        "weld" -> { panel(7, 31, 56, 43, METAL); panel(25, 8, 37, 42, METAL); for (i in 0..5) { disk(22 + i * 3, 34 + i % 2, 4, INK); disk(22 + i * 3, 33 + i % 2, 2, GOLD) }; rect(9, 44, 55, 48, SHADE) }
        "breakable_joint" -> { panel(5, 24, 25, 40, METAL); panel(39, 24, 59, 40, METAL); bar(24, 32, 40, 32, CORAL, 5); line(32, 25, 29, 32, INK); line(29, 32, 34, 38, INK); bolt(13, 32); bolt(51, 32) }
        "rivet" -> { panel(27, 27, 37, 49, SHADE); disk(32, 22, 16, INK); disk(32, 21, 14, METAL); rect(24, 13, 33, 17, LIGHT); panel(23, 46, 41, 52, METAL) }
        "ballast" -> { poly(INK, 20,17, 44,17, 57,54, 7,54); poly(LILAC, 22,20, 42,20, 53,51, 11,51); ring(32, 14, 8, METAL, 4); rect(18, 41, 46, 47, SHADE); line(22, 26, 18, 37, LIGHT, 3) }
        else -> error("Unknown structure sprite: $name")
    }
}

internal fun InfernalePixels.rotary(name: String) {
    when (name) {
        "solid_shaft" -> { panel(6, 25, 55, 40, METAL); disk(54, 32, 7, INK); disk(54, 32, 5, SHADE); line(10, 29, 49, 29, LIGHT); rect(18, 25, 31, 28, INK) }
        "hollow_shaft" -> { panel(6, 21, 51, 43, METAL); disk(51, 32, 11, INK); disk(51, 32, 9, METAL); disk(51, 32, 6, INK); disk(51, 32, 3, 0); line(10, 25, 43, 25, LIGHT) }
        "axle" -> { panel(4, 28, 60, 36, METAL); panel(8, 20, 18, 45, SHADE); panel(46, 20, 56, 45, SHADE); foot(13, 45); foot(51, 45); bolt(13, 25); bolt(51, 25) }
        "bearing" -> { ring(32, 31, 23, METAL, 12); for (i in 0..7) { val v = a / 2 + i * (PI / 4).toFloat(); disk(px(32, 17, v), py(31, 17, v), 4, INK); disk(px(32, 17, v), py(31, 17, v), 2, LIGHT) } }
        "bushing" -> { panel(11, 15, 53, 48, WOOD); ring(32, 31, 15, GOLD, 8); bolt(16, 20); bolt(48, 43); foot(15, 48); foot(49, 48) }
        "wheel" -> { ring(32, 32, 24, WOOD, 17); spokes(32, 32, 20, 6, a, WOOD); ring(32, 32, 7, METAL, 3); bolt(32, 32) }
        "flywheel" -> { ring(32, 32, 25, LILAC, 16); spokes(32, 32, 18, 3, a, METAL); disk(px(32, 20), py(32, 20), 3, GOLD) }
        "drum" -> { panel(14, 15, 50, 49, WOOD); for (y in 21..44 step 5) line(16, y, 48, y, GOLD); panel(8, 10, 17, 54, METAL); panel(47, 10, 56, 54, METAL); line(18, 18 + (phase * 26).toInt(), 46, 18 + (phase * 26).toInt(), SHADE, 3); bolt(12, 32); bolt(51, 32) }
        "crank" -> { ring(28, 32, 15, METAL, 9); val x = px(28, 22); val y = py(32, 22); bar(28, 32, x, y, CORAL, 9); bolt(28, 32); disk(x, y, 5, INK); disk(x, y, 3, WOOD) }
        "crankshaft" -> { val offset = (sin(a) * 10).toInt(); bar(5, 32, 17, 32); bar(17, 32, 17, 20 + offset, CORAL); bar(17, 20 + offset, 30, 20 + offset); bar(30, 20 + offset, 30, 44 - offset, CORAL); bar(30, 44 - offset, 45, 44 - offset); bar(45, 44 - offset, 45, 32, CORAL); bar(45, 32, 59, 32); bolt(17, 20 + offset); bolt(45, 44 - offset) }
        "cam" -> { val x = px(32, 12); val y = py(32, 12); disk(32, 32, 17, INK); disk(x, y, 11, INK); disk(32, 32, 15, MINT); disk(x, y, 9, MINT); bolt(32, 32); disk(x, y, 2, LIGHT) }
        "eccentric" -> { disk(32, 32, 23, INK); disk(32, 32, 21, GOLD); ring(32, 32, 16, WOOD, 12); disk(px(32, 10), py(32, 10), 6, INK); bolt(px(32, 10), py(32, 10)) }
        "winch" -> { panel(5, 47, 59, 55, METAL); panel(8, 20, 15, 48, METAL); panel(45, 20, 52, 48, METAL); panel(14, 22, 45, 43, WOOD); for (x in 18..40 step 4) line(x, 24, x, 41, SHADE); bar(50, 30, 57, 17, CORAL, 5); bolt(57, 17); line(30, 42, 30, 59, INK); gear(14, 33, 8, 8, GOLD, a) }
        else -> error("Unknown rotary sprite: $name")
    }
}

internal fun InfernalePixels.transmission(name: String) {
    when (name) {
        "spur_gear" -> { gear(32, 32, 22); ring(32, 32, 8, LILAC, 4) }
        "internal_ring_gear" -> { ring(32, 32, 26, LILAC, 20); for (i in 0..11) { val v = a + i * (PI / 6).toFloat(); val x = px(32, 18, v); val y = py(32, 18, v); panel(x - 3, y - 3, x + 4, y + 4, METAL) } }
        "helical_gear" -> { gear(35, 34, 20, 12, SHADE); gear(28, 27, 20, 12, LILAC); for (i in 0..5) { val v = a + i * (PI / 3).toFloat(); line(px(28, 17, v), py(27, 17, v), px(35, 17, v), py(34, 17, v), LIGHT, 2) }; bolt(28, 27) }
        "rack" -> { panel(4, 32, 60, 44, METAL); for (x in 7..55 step 8) panel(x, 24, x + 5, 34, METAL); bolt(10, 38); bolt(54, 38) }
        "worm" -> { bar(4, 32, 60, 32, METAL, 9); panel(14, 23, 50, 41, GOLD); for (i in 0..5) { val x = 15 + i * 6; bar(x, 39, x + 5, 24, WOOD, 4) }; rect(15, 22 + (phase * 3).toInt(), 49, 24 + (phase * 3).toInt(), LIGHT) }
        "worm_wheel" -> { gear(34, 38, 19, 14, GOLD); panel(6, 8, 55, 17, METAL); for (x in 12..48 step 7) line(x, 8, x - 4, 18, INK, 2) }
        "planet_carrier" -> { for (i in 0..2) { val v = a + i * (2 * PI / 3).toFloat(); val x = px(32, 19, v); val y = py(32, 19, v); bar(32, 32, x, y, LILAC, 9); ring(x, y, 7, METAL, 3); bolt(x, y) }; bolt(32, 32) }
        "pulley" -> { ring(32, 32, 24, METAL, 19); ring(32, 32, 17, SHADE, 12); spokes(32, 32, 14, 4, a, METAL) }
        "belt" -> { ring(16, 32, 12, CORAL, 8); ring(48, 32, 12, CORAL, 8); bar(16, 21, 48, 21, CORAL, 5); bar(16, 43, 48, 43, CORAL, 5); rect(20 + (phase * 20).toInt(), 19, 26 + (phase * 20).toInt(), 22, LIGHT) }
        "sprocket" -> { gear(32, 32, 23, 10, MINT); for (i in 0..4) { val v = a + i * (2 * PI / 5).toFloat(); disk(px(32, 13, v), py(32, 13, v), 4, INK); disk(px(32, 13, v), py(32, 13, v), 2, 0) } }
        "chain" -> { for (i in 0..5) { val x = 8 + i * 9; panel(x - 5, 22, x + 6, 30, METAL); panel(x - 5, 36, x + 6, 44, METAL); disk(x + (phase * 2).toInt(), 25, 1, INK); disk(x, 39, 1, INK) }; bar(4, 28, 4, 38, SHADE, 4); bar(58, 28, 58, 38, SHADE, 4) }
        "gearbox" -> { panel(11, 14, 53, 50, MINT); bar(3, 33, 12, 33); bar(53, 33, 61, 33); gear(24, 33, 10, 8, GOLD); gear(42, 27, 8, 7, METAL, -a); bolt(16, 19); bolt(48, 45) }
        "differential" -> { bar(3, 32, 61, 32, METAL, 9); poly(INK, 17,19, 46,19, 54,32, 46,45, 17,45, 10,32); poly(LILAC, 19,22, 44,22, 50,32, 44,42, 19,42, 14,32); bar(32, 5, 32, 21, GOLD, 8); gear(24, 32, 8, 6, METAL); gear(40, 32, 8, 6, METAL, -a) }
        "clutch" -> { bar(4, 32, 59, 32, METAL, 6); val gap = if (active) 0 else 5; panel(16, 14, 25, 50, CORAL); panel(29 + gap, 14, 38 + gap, 50, MINT); for (y in 20..44 step 8) rect(24, y, 30, y + 4, GOLD); bar(47, 19, 55, 10, WOOD, 4) }
        "torque_limiter" -> { bar(4, 32, 60, 32); panel(13, 20, 21, 44, METAL); panel(42, 20, 50, 44, METAL); spring(21, 32, 20, CORAL, 4, 7); panel(25, 13, 38, 20, GOLD) }
        "brake" -> { ring(32, 32, 20, METAL, 10); spokes(32, 32, 13, 4); panel(8, 12, 23, 24, CORAL); panel(8, 40, 23, 52, CORAL); bar(10, 22, 10, 42, LILAC, 6); bar(9, 32, 3, 32, METAL, 4) }
        "ratchet" -> { gear(32, 35, 20, 9, WOOD); bar(50, 9, 39, 21, CORAL, 7); bolt(50, 9); poly(INK, 35,18, 44,21, 38,29); poly(GOLD, 37,21, 41,22, 38,26) }
        "freewheel" -> { ring(32, 32, 24, METAL, 17); ring(32, 32, 11, MINT, 6); for (i in 0..3) { val v = a + i * (PI / 2).toFloat(); bar(px(32, 12, v), py(32, 12, v), px(32, 19, v + 0.35f), py(32, 19, v + 0.35f), GOLD, 5) } }
        "universal_joint" -> { bar(5, 22, 24, 30, METAL, 10); bar(40, 34, 59, 44, METAL, 10); bar(24, 20, 24, 42, LILAC); bar(40, 22, 40, 44, CORAL); bar(24, 22, 40, 42, METAL, 6); bar(24, 42, 40, 22, METAL, 6); bolt(32, 32) }
        else -> error("Unknown transmission sprite: $name")
    }
}

internal fun InfernalePixels.linear(name: String) {
    val end = 39 + (t * 18).toInt()
    when (name) {
        "guide" -> { panel(5, 18, 59, 25, METAL); panel(5, 39, 59, 46, METAL); for (x in listOf(9, 55)) { bar(x, 22, x, 43, LILAC, 5); bolt(x, 21); bolt(x, 42) } }
        "carriage" -> { panel(12, 19, 52, 43, MINT); for (x in listOf(19, 45)) { ring(x, 45, 7, METAL, 3); bolt(x, 45) }; bolt(20, 26); bolt(44, 26); rect(25, 23, 39, 37, LIGHT) }
        "lead_screw" -> { bar(4, 32, 60, 32, METAL, 8); for (x in 8..56 step 5) line(x, 28, x - 3, 36, INK); val x = 18 + (t * 22).toInt(); panel(x - 7, 19, x + 8, 45, GOLD); bolt(x, 24) }
        "spring" -> { spring(9, 32, 45 - (t * 20).toInt(), MINT, 7, 10); bolt(7, 32); bolt(56 - (t * 20).toInt(), 32) }
        "damper" -> { panel(9, 22, 36, 42, LILAC); bar(34, 32, end, 32, METAL, 6); ring(6, 32, 5, METAL, 2); ring(end + 2, 32, 4, METAL, 2); panel(14, 18, 22, 46, SHADE); rect(25, 26, 31, 38, LIGHT) }
        "gas_spring" -> { panel(7, 24, 33, 40, MINT); bar(32, 32, end, 32, METAL, 5); bolt(6, 32); bolt(end, 32); port(16, 21, GOLD); line(19, 28, 26, 28, LIGHT); rect(27, 25, 31, 39, SHADE) }
        "rope" -> { for (pass in 0..1) for (i in 0..42) { val x = 10 + i; val y = 20 + (sin(i * PI / 42) * 24).toInt(); disk(x, y, if (pass == 0) 3 else 1, if (pass == 0) INK else WOOD) }; ring(9, 17, 6, WOOD, 2); ring(54, 17, 6, WOOD, 2) }
        "steel_cable" -> { bar(9, 47, 52, 17, SHADE, 6); for (i in 0..9) { val x = 12 + i * 4; val y = 45 - i * 3; line(x, y - 2, x + 3, y + 1, LIGHT) }; ring(8, 48, 6, METAL, 3); ring(55, 15, 6, METAL, 3) }
        "lever" -> { poly(INK, 22,53, 32,31, 42,53); poly(LILAC, 26,50, 32,36, 38,50); val dy = (t * 14).toInt() - 7; bar(5, 30 - dy, 59, 30 + dy, WOOD, 8); bolt(32, 30); disk(6, 30 - dy, 4, CORAL) }
        "toggle_linkage" -> { val y = 14 + (t * 17).toInt(); bar(7, 43, 32, y, METAL, 8); bar(32, y, 57, 43, CORAL, 8); bolt(7, 43); bolt(32, y); bolt(57, 43); foot(7, 48); foot(57, 48) }
        "scissor_lift" -> { val y = 10 + (t * 18).toInt(); panel(5, y, 59, y + 6, WOOD); panel(5, 52, 59, 58, METAL); bar(12, y + 7, 52, 51, CORAL, 6); bar(52, y + 7, 12, 51, MINT, 6); bolt(32, (y + 58) / 2); bolt(12, 51); bolt(52, 51) }
        "conveyor" -> { panel(4, 24, 60, 42, MINT); for (x in 12..52 step 10) { disk(x, 33, 6, INK); spokes(x, 33, 3, 3) }; for (i in 0..5) { val x = 6 + ((i * 9 + phase * 9).toInt() % 51); rect(x, 22, x + 4, 25, LIGHT) }; bar(13, 43, 10, 55, METAL, 5); bar(50, 43, 54, 55, METAL, 5); panel(24, 10, 38, 23, WOOD) }
        else -> error("Unknown linear sprite: $name")
    }
}

