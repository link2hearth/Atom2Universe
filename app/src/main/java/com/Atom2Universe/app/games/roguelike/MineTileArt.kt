package com.Atom2Universe.app.games.roguelike

/** Colours follow DungeonMineBackdrop: slate rock, teal ore, weathered timber and amber lamps. */
internal class MineTileArt {
    companion object { const val SIZE=48 }
    fun render(tx:Int,ty:Int,wall:Boolean,stairs:Boolean,neighbours:Int):IntArray {
        val p=SceneryPixels(fill=if(wall)0xFF303942 else 0xFF4A504C)
        val seed=(tx*73856093 xor ty*19349663) and Int.MAX_VALUE
        with(p) {
            if(wall) {
                for(row in 0..3) for(col in -1..2) {
                    val x=col*23+row%2*11; val y=row*15-5+(col+seed%3).mod(3)
                    val color=if((row+col+seed)%3==0)0xFF536165 else 0xFF3B484E
                    polygon(color,x,y+7,x+6,y,x+16,y+1,x+22,y+6,x+18,y+13,x+7,y+16,x+2,y+12)
                    polygon(0xFF687873,x+6,y+1,x+15,y+2,x+20,y+6,x+10,y+5,x+2,y+8)
                    line(x+10,y+6,x+14,y+9,0xFF303942)
                    line(x+14,y+9,x+12,y+13,0xFF303942)
                }
                if(neighbours and 4==0) {rect(0,38,48,10,0xFF26363C);rect(1,38,46,3,0xFF667773);rect(4,42,40,2,0xFF3B484E)}
                if(neighbours and 8==0)rect(0,2,2,34,0xFF7A8981)
                when(seed%6) {
                    0,1 -> {crystal(p,20,35,15);crystal(p,29,37,11);crystal(p,13,37,9)}
                    2 -> {timber(p,5);timber(p,37);rect(3,7,42,6,0xFF715D4B);rect(3,7,42,1,0xFFC1A075);line(10,24,21,13,0xFFAD8559);lamp(p,25,17)}
                    3 -> {line(21,3,18,12,0xFF172F3A);line(18,12,24,21,0xFF172F3A);line(24,21,19,31,0xFF172F3A);oval(13,33,24,5,0xFF315C68);rect(18,34,12,1,0xFF8BB9BD)}
                    4 -> {for(i in 0..4) {rect(8+i*6,17+i%2*3,3,2,0xFFC9B27E);rect(9+i*6,19+i%2*3,2,2,0xFF937A59)}}
                    else -> {for(i in 0..3) {val x=7+i*10;rect(x,24-i%2*5,5,13+i%2*5,0xFF60716E);rect(x,24-i%2*5,2,9,0xFF92A09A)}}
                }
            } else {
                repeat(26) { i ->
                    val x=(seed%43+i*17)%46;val y=(seed%37+i*11)%47
                    rect(x,y,2+i%3,1,if(i%3==0)0xFF697068 else 0xFF3D4846)
                }
                when(seed%5) {
                    0 -> {oval(10,19,26,10,0xFF394F53);oval(13,20,21,6,0xFF45656B);rect(17,21,12,1,0xFF7F9C99)}
                    1 -> {line(9,11,22,21,0xFF323F3F);line(22,21,20,28,0xFF323F3F);line(22,21,32,17,0xFF323F3F)}
                    2 -> {rect(5,6,7,3,0xFF717971);rect(6,6,4,1,0xFF9CA591);rect(33,37,5,2,0xFF798076)}
                    3 -> {rect(6,34,14,3,0xFF655C4A);line(7,34,18,35,0xFF9B8766)}
                }
                if(neighbours and 1!=0)rect(1,0,46,3,0xFF35423F)
                if(neighbours and 8!=0)rect(0,3,2,43,0xFF35423F)
            }
            if(stairs) lift(p)
        }
        return p.pixels
    }
    private fun timber(p:SceneryPixels,x:Int) = with(p) {
        rect(x,9,6,30,0xFF715D4B);rect(x+1,10,1,27,0xFFAD8559)
        for(y in listOf(17,31)) {rect(x-1,y,8,3,0xFF4C626B);rect(x+1,y+1,1,1,0xFFB1B8A2)}
    }
    private fun lamp(p:SceneryPixels,x:Int,y:Int) = with(p) {
        rect(x+3,y-4,1,5,0xFFA6AC97);rect(x,y,7,10,0xFF263940);rect(x+1,y+2,5,6,0xFFCAAB71)
        rect(x+2,y+3,2,4,0xFFF1D99B);rect(x-1,y-1,9,2,0xFF95A396)
    }
    private fun crystal(p:SceneryPixels,x:Int,y:Int,h:Int) = with(p) {
        for(i in 0 until h) {val w=if(i<4)i+1 else 5;rect(x-w/2,y-h+i,w,1,0xFF57939E)}
        line(x,y-h+2,x,y-2,0xFFB5DECD);line(x+2,y-h+6,x+2,y-1,0xFF6DAEB4)
    }
    private fun lift(p:SceneryPixels) = with(p) {
        rect(6,8,36,36,0xFF1B2D35);rect(9,12,30,25,0xFF32444B)
        for(y in 26..38 step 4) {rect(10,y,28,3,0xFF766E58);rect(11,y,26,1,0xFFB9A07A)}
        timber(p,5);timber(p,37);rect(4,7,40,5,0xFFB09770);rect(4,7,40,1,0xFFE1C49A)
        for(x in listOf(14,33))rect(x,2,1,27,0xFF9AAEAA)
        oval(19,3,11,9,0xFF263940);oval(21,4,7,6,0xFF8FA09A);rect(23,5,2,4,0xFF3B5058)
        lamp(p,23,14)
        // Down chevron keeps the actual exit recognisable among decorative machinery.
        line(19,32,24,36,0xFFF2DDAE);line(24,36,29,32,0xFFF2DDAE)
    }
}
