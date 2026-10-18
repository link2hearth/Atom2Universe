package com.Atom2Universe.app.games.infernale.art

internal fun InfernalePixels.projectile(name: String) {
    when (name) {
        "sphere" -> { disk(32, 32, 19, INK); disk(32, 31, 17, CORAL); rect(22, 20, 29, 24, LIGHT); line(36, 44, 43, 38, WOOD, 3) }
        "slug" -> { poly(INK, 12,23, 40,23, 54,32, 40,41, 12,41); poly(METAL, 15,26, 39,26, 50,32, 39,38, 15,38); panel(11, 22, 19, 42, GOLD); line(23, 28, 38, 28, LIGHT) }
        "potato" -> { poly(INK, 9,27, 18,16, 36,13, 51,24, 55,38, 44,48, 25,51, 12,41); poly(WOOD, 12,28, 20,19, 36,16, 48,26, 52,37, 43,45, 25,48, 15,40); for ((x,y) in listOf(23 to 27, 39 to 23, 43 to 38, 25 to 42)) { disk(x,y,2,SHADE); rect(x-1,y-2,x+1,y,GOLD) }; line(17, 27, 22, 22, LIGHT, 3) }
        "sabot" -> { panel(18, 24, 48, 40, METAL); poly(INK, 7,15, 23,22, 23,42, 7,49); poly(LILAC, 10,19, 20,24, 20,40, 10,45); poly(INK, 44,24, 57,32, 44,40); poly(GOLD, 46,28, 53,32, 46,36); panel(23, 20, 31, 44, WOOD) }
        "barrel" -> { panel(5, 24, 58, 40, METAL); panel(49, 21, 60, 43, SHADE); rect(54, 25, 60, 39, INK); panel(12, 21, 19, 43, GOLD); line(22, 28, 45, 28, LIGHT) }
        "breech" -> { panel(10, 18, 43, 47, METAL); panel(42, 25, 59, 39, SHADE); panel(16, 23, 32, 43, LILAC); bar(25, 18, 33, 7, WOOD, 5); bolt(33, 7); bolt(16, 24); bar(10, 32, 4, 32, GOLD, 5) }
        "muzzle_brake" -> { panel(5, 25, 18, 39, METAL); panel(17, 18, 58, 46, SHADE); for (x in 23..47 step 10) { panel(x, 16, x + 7, 48, METAL); rect(x + 2, 24, x + 5, 40, INK) }; rect(55, 23, 59, 41, INK) }
        "catch_box" -> { panel(7, 25, 57, 55, WOOD); rect(12, 25, 52, 38, INK); for (x in 16..48 step 8) { line(x, 28, x, 37, MINT); line(12, 29 + (x - 16) / 4, 51, 29 + (x - 16) / 4, MINT) }; bar(12, 49, 51, 42, GOLD, 3); bolt(12, 49); bolt(51, 49) }
        else -> error("Unknown projectile sprite: $name")
    }
}

internal fun InfernalePixels.energy(name: String) {
    when (name) {
        "rotary_motor" -> { disk(31, 31, 21, INK); disk(31, 31, 19, GOLD); gear(31, 31, 12, 8, METAL); bar(51, 31, 60, 31, METAL, 7); foot(19, 53); foot(43, 53); panel(24, 5, 38, 12, CORAL) }
        "linear_motor" -> { panel(5, 20, 59, 45, GOLD); rect(9, 29, 55, 36, SHADE); for (x in 11..51 step 8) { rect(x, 24, x+4, 28, CORAL); rect(x, 37, x+4, 41, METAL) }; val x = 16 + (t * 29).toInt(); panel(x-6, 14, x+7, 49, LILAC); bolt(x, 20); bar(8, 44, 8, 54, INK, 2) }
        "electric_motor" -> { panel(8, 19, 46, 47, GOLD); panel(9, 47, 51, 54, METAL); for (x in 15..36 step 6) rect(x, 25, x+2, 41, SHADE); panel(17, 11, 32, 20, LILAC); bar(45, 32, 60, 32, METAL, 7); disk(46, 32, 9, INK); disk(46, 32, 7, METAL); spokes(46,32,4,3); lamp(24,15) }
        "generator" -> { panel(10, 22, 54, 49, GOLD); panel(20, 10, 44, 24, METAL); for (x in listOf(25,39)) { port(x, 9, CORAL); line(x, 15, x, 21, INK) }; ring(24, 36, 10, LILAC, 5); spokes(24,36,7,3); poly(INK, 43,27, 36,37, 42,37, 39,44, 49,33, 43,33); foot(16,49); foot(48,49) }
        "battery" -> { panel(12, 17, 52, 53, MINT); panel(18, 10, 27, 17, CORAL); panel(38, 10, 47, 17, METAL); panel(18, 31, 46, 45, LIGHT); rect(21,34,21+(level*22).toInt(),42,MINT); line(19,24,27,24,INK); line(23,20,23,28,INK); line(39,24,46,24,INK) }
        "capacitor" -> { disk(31, 21, 16, INK); panel(15, 21, 48, 49, LILAC); disk(31, 21, 14, METAL); line(22,16,40,26,SHADE); line(22,26,40,16,SHADE); bar(22,49,22,59,METAL,3); bar(41,49,41,59,METAL,3); rect(20,30,24,43,LIGHT); rect(38,28,44,46,SHADE) }
        "combustion_engine" -> { panel(10, 32, 49, 53, GOLD); for (x in listOf(17,33)) { panel(x-6,14,x+7,34,METAL); for (y in 18..29 step 5) rect(x-8,y,x+9,y+2,SHADE); bar(x,14,x,8,CORAL,4) }; gear(48,41,11,8,CORAL); bar(9,34,5,25,METAL,5); foot(18,53) }
        "steam_engine" -> { panel(7, 25, 39, 49, GOLD); panel(12, 6, 22, 26, SHADE); panel(10, 4, 24, 10, METAL); gauge(23,34,10,MINT); ring(48,41,13,CORAL,8); spokes(48,41,10,5); bar(34,42,px(48,7),py(41,7),METAL,4); panel(6,54,60,59,METAL) }
        "turbine" -> { ring(32,32,26,METAL,21); for (i in 0..7) { val v = a + i * (Math.PI / 4).toFloat(); bar(px(32,8,v),py(32,8,v),px(32,20,v+0.4f),py(32,20,v+0.4f),GOLD,6) }; bolt(32,32); port(7,12,METAL); port(57,52,METAL) }
        "human_crank" -> { panel(7,48,43,55,WOOD); bar(25,48,25,30,METAL,8); ring(25,29,16,GOLD,10); spokes(25,29,12,4); val x=px(25,18); val y=py(29,18); bar(25,29,x,y,METAL,5); panel(x-3,y-6,x+5,y+7,WOOD); bolt(25,29) }
        else -> error("Unknown energy sprite: $name")
    }
}

internal fun InfernalePixels.control(name: String) {
    when (name) {
        "trigger" -> { panel(9,40,55,54,METAL); panel(18,if(active) 33 else 23,46,42,CORAL); bolt(14,47); bolt(50,47); rect(23,if(active) 35 else 26,41,if(active) 37 else 29,LIGHT) }
        "switch" -> { panel(16,26,48,52,LILAC); bar(32,35,if(active) 44 else 20,12,METAL,6); disk(if(active) 44 else 20,12,6,INK); disk(if(active) 44 else 20,11,4,CORAL); bolt(22,45); bolt(42,45); port(32,56) }
        "timer" -> { panel(25,4,39,12,GOLD); bar(47,17,53,11,METAL,5); gauge(32,34,23,LILAC); line(32,34,25,28,INK); rect(26,46,39,49,SHADE) }
        "logic_gate" -> { bar(5,23,20,23,LILAC,3); bar(5,41,20,41,LILAC,3); bar(47,32,60,32,LILAC,3); disk(33,32,18,INK); disk(33,32,15,MINT); panel(17,14,34,50,MINT); rect(21,18,35,46,MINT); lamp(36,32); bolt(23,23); bolt(23,41) }
        "pid" -> { panel(7,12,57,52,LILAC); panel(13,18,51,34,LIGHT); line(17,29,23,24,MINT); line(23,24,28,29,MINT); line(28,29,34,22,MINT); line(34,22,47,22,MINT); for(x in listOf(19,32,45)) { disk(x,43,5,INK); disk(x,42,3,GOLD); line(x,42,x+1,39,LIGHT) }; port(32,56) }
        "governor" -> { bar(32,9,32,54,METAL,5); val spread=10+(t*12).toInt(); bar(32,15,32-spread,32,GOLD,4); bar(32,15,32+spread,32,GOLD,4); bar(32-spread,32,32,45,METAL,4); bar(32+spread,32,32,45,METAL,4); disk(32-spread,32,6,INK); disk(32-spread,31,4,CORAL); disk(32+spread,32,6,INK); disk(32+spread,31,4,CORAL); panel(26,42,38,49,LILAC); foot(32,55) }
        else -> error("Unknown control sprite: $name")
    }
}

internal fun InfernalePixels.sensor(name: String) {
    when (name) {
        "pressure" -> { port(32,54,GOLD); gauge(32,29,22,METAL); rect(26,40,39,43,SHADE) }
        "tachometer" -> { panel(18,42,46,55,LILAC); gauge(32,27,22,GOLD); for(i in 0..3) { val v=4.6f+i*0.3f; disk(px(32,17,v),py(27,17,v),2,CORAL) }; bar(32,55,32,60,METAL,4) }
        "encoder" -> { ring(30,31,23,LILAC,12); for(i in 0..15) { val v=a+i*(Math.PI/8).toFloat(); line(px(30,15,v),py(31,15,v),px(30,20,v),py(31,20,v),INK,2) }; panel(43,30,59,47,METAL); lamp(51,37); line(54,47,54,58,INK,3) }
        "force" -> { panel(14,14,50,25,LILAC); panel(14,39,50,50,LILAC); panel(14,23,24,41,METAL); panel(40,23,50,41,METAL); spring(23,32,18,CORAL,3,5); bolt(32,19); bolt(32,44); line(50,32,59,32,INK,3); port(58,32) }
        "position" -> { panel(6,24,58,42,METAL); for(x in 12..52 step 5) line(x,28,x,if(x%2==0) 34 else 37,INK); val x=14+(t*34).toInt(); panel(x-5,14,x+6,26,LILAC); poly(CORAL,x-3,20,x+4,20,x,26); bar(x,14,x,7,INK,2) }
        "zone" -> { for(i in 0..5) { val x=9+i*9; rect(x,9,x+4,11,LILAC); rect(x,53,x+4,55,LILAC); rect(9,x,11,x+4,LILAC); rect(53,x,55,x+4,LILAC) }; panel(19,25,32,40,METAL); disk(28,32,4,if(active) MINT else CORAL); for(i in 0..2) { val x=37+i*6; line(x,26-i*4,x+2,32,LILAC); line(x+2,32,x,38+i*4,LILAC) } }
        "limit_switch" -> { panel(11,33,43,53,LILAC); bar(25,34,49,if(active) 29 else 15,METAL,5); ring(49,if(active) 29 else 15,6,GOLD,2); bolt(18,43); bolt(36,43); port(9,44) }
        else -> error("Unknown sensor sprite: $name")
    }
}

internal fun InfernalePixels.safety(name: String) {
    when(name) {
        "rupture_disk" -> { ring(32,32,25,METAL,18); disk(32,32,17,CORAL); line(21,21,43,43,LIGHT,2); line(21,43,43,21,LIGHT,2); for(i in 0..3) { val v=i*(Math.PI/2).toFloat(); bolt(px(32,21,v),py(32,21,v)) }; if(active) { poly(0,24,23,36,27,32,34,41,42,28,37,30,30); line(34,30,43,24,INK,2) } }
        "fuse" -> { panel(14,24,50,40,LIGHT); spring(21,32,22,if(active) CORAL else GOLD,3,4); panel(7,21,20,43,METAL); panel(44,21,57,43,METAL); line(21,27,39,27,MINT); if(active) rect(30,29,35,36,LIGHT) }
        "emergency_stop" -> { panel(12,39,52,56,GOLD); for(x in 15..45 step 10) line(x,51,x+4,43,INK,3); panel(25,26,39,42,METAL); val y=if(active) 27 else 18; panel(7,y,57,y+14,CORAL); rect(12,y+4,52,y+7,LIGHT); bolt(17,52); bolt(47,52) }
        else -> error("Unknown safety sprite: $name")
    }
}
