package com.Atom2Universe.app.games.roguelike

/** Hand-composed silhouettes, transparent ground and a limited pastel palette. */
internal class SceneryTileArt {
    companion object { const val SIZE=48 }
    private val ink=0xFF344448L
    private val stone=0xFF88938AL
    private val light=0xFFC5C6ABL
    private val wood=0xFF997F64L
    private val woodLight=0xFFD0B38AL
    fun render(kind:SceneryKind,variant:Int,connections:Int=0):IntArray {
        val p=SceneryPixels(SIZE*kind.span)
        with(p) {
            when(kind) {
                SceneryKind.CARGO, SceneryKind.REACTOR, SceneryKind.BOOKCASES,
                SceneryKind.ORE_VEIN, SceneryKind.OAK, SceneryKind.HAYSTACK,
                SceneryKind.BANQUET_TABLE, SceneryKind.MEMORIAL -> LargeSceneryArt.draw(this, kind, variant)
                SceneryKind.WELL -> well(this,variant)
                SceneryKind.STALL -> stall(this,variant)
                SceneryKind.CART -> cart(this,false)
                SceneryKind.MINECART -> { rails(this,if(connections==0)10 else connections); cart(this,true) }
                SceneryKind.CAMPFIRE -> fire(this)
                SceneryKind.BONFIRE -> {
                    // Low stone hearth, crossed logs and an open flame: this is a campfire, not a portal.
                    oval(4,26,40,19,0xFF3E5049);oval(8,28,33,14,0xFF81705A)
                    val stones=listOf(7 to 32,12 to 27,23 to 26,34 to 29,41 to 35,33 to 42,21 to 43,10 to 39)
                    for((x,y) in stones) {oval(x-3,y-2,7,5,0xFF88968A);rect(x-2,y-2,4,1,0xFFD0CBA9)}
                    polygon(0xFF635347,12,33,15,30,36,38,33,41)
                    polygon(0xFFB3946F,13,38,33,30,35,33,16,41)
                    line(16,39,33,32,0xFFDFBA8B)
                    polygon(0xFFD68D69,15,35,17,27,20,30,22,20,25,27,28,16,30,28,33,25,35,35,30,40,20,40)
                    polygon(0xFFF0BA7D,19,35,22,27,24,31,28,23,29,32,32,35,28,39,22,39)
                    polygon(0xFFFFE0A4,23,36,26,29,29,36,27,39,24,39)
                    rect(23,14,1,2,0xFFDBAF80);rect(30,10,2,2,0xFF9AADA1)
                    rect(27,5,3,1,0xFF81998D)
                }                SceneryKind.RUIN -> ruin(this,variant)
                SceneryKind.CHAPEL -> chapel(this)
                SceneryKind.RAIL -> rails(this,connections)
                SceneryKind.MARSH -> {
                    oval(3,13,42,25,0xFF597F74);oval(6,15,36,19,0xFF779F91)
                    rect(11,18,14,1,0xFFB7C9AD);rect(24,29,12,1,0xFFABD0BC)
                    for(i in 0..3) {val x=8+i*9;val y=31-i%2*14;oval(x,y,5,3,0xFF91AC76);rect(x+2,y,1,1,0xFFCED3A0)}
                    for(x in listOf(4,39)) {line(x,33,x-1,22,0xFF586D4F);line(x,33,x+3,24,0xFFBDD09A)}
                    // Flat exposed stepping stones distinguish shallow pools from deep streams.
                    oval(17,23,8,4,0xFFB3B9A1);rect(18,23,5,1,0xFFD2D0AD)
                }
                SceneryKind.CLIFF -> {
                    rect(0,9,48,33,0xFF52616A)
                    for(y in 0..3) {val x=(y+variant)%3*3;rect(x,6+y*9,48-x,6,if(y%2==0)0xFF8B9994 else 0xFF73868A);rect(x+2,6+y*9,41-x,1,light)}
                    for(i in 0..4)line(i*11+3,13,i*11,37,0xFF435664)
                    rect(0,3,48,5,0xFF93A58C);rect(0,3,48,1,0xFFC0C7A1)
                    oval(5,39,10,5,0xFF6E7D7D);oval(33,40,11,5,stone)
                }
            }
        }
        return p.pixels
    }
    private fun well(p:SceneryPixels,v:Int)=with(p) {
        oval(5,35,38,9,0xFF3C5049)
        oval(8,24,32,18,ink);rect(9,25,30,10,stone);oval(9,28,30,12,stone)
        for(y in listOf(29,35)) {rect(10,y,28,1,0xFF637572);for(x in 12..36 step 9)rect(x+(y%2)*2,y-4,1,4,0xFF637572)}
        oval(7,19,34,14,light);oval(11,21,26,9,0xFF293F49);oval(14,24,20,5,0xFF507D86);rect(19,25,10,1,0xFFAACEC6)
        for(x in listOf(8,36)) {rect(x,9,3,21,wood);rect(x,9,1,19,woodLight)}
        rect(7,12,34,3,woodLight);rect(22,12,2,12,0xFFC3B38D);rect(18,12,9,2,0xFF697D78)
        for(y in 0..7)rect(24-y*3,3+y,2+y*6,1,if(y%2==0)0xFF728D8C else 0xFF587879)
        rect(1,11,46,2,0xFFAFC2AF)
        rect(30,31,7,7,wood);rect(30,33,7,1,0xFF536768);rect(32,29,3,2,light)
        if(v==1){rect(8,35,4,2,0xFF96AB7B);rect(35,28,3,3,0xFF96AB7B)}
    }
    private fun stall(p:SceneryPixels,v:Int)=with(p) {
        oval(3,37,43,8,0xFF405448)
        for(x in listOf(6,39)){rect(x,10,3,30,wood);rect(x,11,1,27,woodLight)}
        rect(4,29,40,11,wood);rect(4,29,40,2,woodLight)
        for(x in 7..41 step 7)rect(x,32,1,8,0xFF705C51)
        rect(3,24,42,6,woodLight);rect(4,29,40,2,0xFF6B6453)
        val accent=if(v==0)0xFFC4999A else if(v==1)0xFF91B6AF else 0xFFB7AC7E
        for(x in 2..44 step 7) {
            rect(x,8,7,11,if(x%2==0)accent else 0xFFDED2AD)
            rect(x,18,7,3,if(x%2==0)0xFFA38085 else 0xFFB8AB8D)
            rect(x+1,21,5,2,if(x%2==0)accent else 0xFFDED2AD)
        }
        rect(6,5,37,3,accent);rect(9,4,31,1,0xFFE5D7B8)
        for(i in 0..2) {rect(7+i*12,24,10,5,0xFF6C6555);rect(8+i*12,24,8,1,0xFFDAC29C)}
        for(i in 0..8){val x=9+i%3*12+i/3*2;val y=23-i/3;oval(x,y,3,3,when(i%3){0->0xFFD1A08C;1->0xFFA8BC83;else->0xFFE0C588})}
    }
    private fun cart(p:SceneryPixels,mine:Boolean)=with(p) {
        oval(4,36,40,8,0xFF32464A)

        for(x in listOf(9,32)){oval(x-2,31,11,12,ink);oval(x,33,7,8,if(mine)stone else wood);rect(x+2,35,2,4,light)}
        if(!mine){line(14,30,4,44,woodLight);line(34,30,43,44,woodLight)}
        rect(7,19,34,16,ink);rect(9,20,30,13,if(mine)0xFF78848A else wood)
        for(y in listOf(23,28))rect(10,y,28,1,if(mine)0xFF586D79 else 0xFF705C51)
        if(mine) {
            for(i in 0..4){val x=10+i*6;val y=14-i%2*4;rect(x,y,7,8,0xFF526C75);rect(x+1,y,4,2,0xFF94B7B1)}
            for(x in listOf(11,34))rect(x,26,2,2,light)
        } else {
            rect(11,13,12,8,0xFFB8AA81);rect(12,13,10,1,0xFFDFCE9F)
            oval(24,13,12,11,0xFFA5AE87);rect(28,12,4,2,0xFFD8CAA3)
            line(11,14,21,20,0xFF82745B)
        }
        rect(6,19,36,3,if(mine)0xFFB1B8AA else woodLight)
    }
    private fun fire(p:SceneryPixels)=with(p) {
        oval(6,28,36,14,0xFF424F48);oval(11,29,26,10,0xFF706554)
        for(i in 0..7){val x=intArrayOf(8,14,24,34,38,32,21,12)[i];val y=intArrayOf(30,26,25,28,33,38,39,37)[i];oval(x-3,y-2,7,5,stone);rect(x-2,y-2,4,1,light)}
        line(14,31,32,37,woodLight);line(15,37,31,30,wood)
        rect(17,27,15,7,0xFFB57B6B);rect(20,21,10,11,0xFFE4AA7D);rect(23,15,4,15,0xFFF0C98C)
        rect(22,27,7,5,0xFFFFE1A9);rect(18,24,3,7,0xFFD29378)
        rect(24,8,2,2,0xFFAFB8A9);rect(27,4,3,1,0xFF899E95)
        line(8,32,13,14,wood);line(39,33,34,14,wood);rect(12,13,23,2,woodLight)
        rect(24,14,1,7,ink);oval(18,20,13,8,ink);rect(19,20,11,1,stone)
    }
    private fun ruin(p:SceneryPixels,v:Int)=with(p) {
        // Three solid quadrants and an open south-east courtyard.
        rect(5,8,82,37,0xFF52615E);rect(7,10,77,31,0xFF899287)
        rect(6,9,11,76,0xFF65756D);rect(8,10,8,72,0xFFA5AC94)
        for(y in 11..39 step 8)for(x in 9..80 step 13) {
            rect(x+(y/8%2)*4,y,11,6,if((x+y)%3==0)0xFFACB29B else 0xFF818F86)
            rect(x+(y/8%2)*4,y,10,1,light)
        }
        // Missing roof: exposed rafters, jagged wall and ivy.
        for(i in 0..5) {rect(8+i*13,4+i%3*3,10,7,stone);rect(9+i*13,4+i%3*3,8,1,light)}
        rect(28,15,20,26,ink);rect(31,18,14,22,0xFF475C5C);rect(37,17,2,22,wood);rect(30,28,16,2,wood)
        rect(20,44,22,38,0xFF79877D);rect(21,44,19,2,light)
        for(y in 48..76 step 8){rect(20,y,22,1,0xFF4F6560);rect(29+(y%3),y-5,1,5,0xFF52645C)}
        for(i in 0..4) {val x=46+i*8;val y=44+(i%2)*7;rect(x,y,7,4,stone);rect(x,y,6,1,light)}
        line(8,43,31,66,wood);line(9,43,32,66,woodLight)
        for(i in 0..10){val x=13+i%3*3;val y=14+i*5;rect(x,y,4,3,0xFF6E917B);rect(x+1,y,2,1,0xFFAFC29B)}
        rect(55,61,19,2,0xFFA6AA8C);rect(60,70,24,2,0xFF7A9580)
        if(v!=2){rect(32,74,7,3,0xFFC9ADA0);rect(33,73,5,1,0xFFDBC2AC)}
    }
    private fun chapel(p:SceneryPixels)=with(p) {
        rect(7,25,81,22,ink);rect(10,25,75,20,0xFF899593)
        for(y in 28..44 step 7) {rect(11,y,73,1,0xFF62757B);for(x in 14..81 step 13)rect(x+(y%2)*4,y-5,1,5,0xFF62757B)}
        // Slate roof and small bell tower occupy the upper, blocked half.
        for(y in 0..20) {val left=47-y*2;rect(left,7+y,2+y*4,1,if(y%4==0)0xFFA4B3AD else 0xFF687D87)}
        rect(4,28,87,3,0xFFBCC7B7);rect(38,4,18,22,0xFF97A499);rect(42,8,10,14,ink)
        oval(43,11,8,8,0xFFCFB98B);rect(42,18,10,2,0xFFE3CE9E);rect(47,19,1,3,wood)
        rect(45,0,3,7,light);rect(41,2,11,2,light)
        // West buttress stays solid; east arch is the walkable crypt threshold.
        rect(9,44,32,43,0xFF687D87);rect(11,45,28,3,light)
        for(y in 53..81 step 8){rect(10,y,30,1,0xFF425B66);rect(23,y-6,1,6,0xFF425B66)}
        rect(53,44,6,39,stone);rect(83,44,6,39,stone)
        rect(60,46,22,4,light);rect(57,50,5,8,light);rect(80,50,5,8,light)
        rect(62,51,18,7,0xFF263B4C)
        for(y in 61..84 step 5){rect(60,y,22,3,0xFF738591);rect(60,y,22,1,0xFFB1BBB0)}
        for(x in listOf(54,85)){rect(x,63,2,9,0xFFE1CF9F);rect(x,61,2,2,0xFFF4D995)}
        line(66,77,71,81,0xFFD9D5B6);line(71,81,76,77,0xFFD9D5B6)
    }
    private fun rails(p:SceneryPixels,bits:Int)=with(p) {
                if(bits in listOf(3,6,12,9)) {
            // Dedicated elbow: two continuous L-shaped rails, without crossing stubs.
            for(y in 0..10 step 8){rect(12,y,24,3,wood);rect(13,y,22,1,woodLight)}
            for(x in 38..47 step 8){rect(x,12,3,24,wood);rect(x,13,1,22,woodLight)}
            for(i in 0..2){line(13+i*6,14,32,33-i*6,wood);line(14+i*6,14,32,32-i*6,woodLight)}
            rect(16,0,3,33,0xFF526A73);rect(16,0,1,32,0xFFACBEB4)
            rect(16,30,32,3,0xFF526A73);rect(17,30,31,1,0xFFACBEB4)
            rect(30,0,3,19,0xFF526A73);rect(30,0,1,17,0xFFACBEB4)
            rect(30,16,18,3,0xFF526A73);rect(31,16,17,1,0xFFACBEB4)
            repeat(listOf(3,6,12,9).indexOf(bits)) {
                val original=pixels.copyOf()
                for(y in 0 until size)for(x in 0 until size)pixels[y*size+x]=original[(size-1-x)*size+y]
            }
            return@with
        }
        // Straight stretches and switches meet at the same rail gauge.
        fun vertical(y:Int,h:Int) {
            for(yy in y until y+h step 8){rect(12,yy,24,3,wood);rect(13,yy,22,1,woodLight)}
            for(x in listOf(16,30)){rect(x,y,3,h,0xFF526A73);rect(x,y,1,h,0xFFACBEB4)}
        }
        fun horizontal(x:Int,w:Int) {
            for(xx in x until x+w step 8){rect(xx,12,3,24,wood);rect(xx,13,1,22,woodLight)}
            for(y in listOf(16,30)){rect(x,y,w,3,0xFF526A73);rect(x,y,w,1,0xFFACBEB4)}
        }
        if(bits and 1!=0)vertical(0,33)
        if(bits and 4!=0)vertical(16,32)
        if(bits and 8!=0)horizontal(0,33)
        if(bits and 2!=0)horizontal(16,32)
        if(Integer.bitCount(bits)==1) {
            if(bits and 5!=0){rect(12,23,25,4,woodLight);rect(13,27,3,4,ink);rect(33,27,3,4,ink)}
            else {rect(23,12,4,25,woodLight);rect(27,13,4,3,ink);rect(27,33,4,3,ink)}
        }
    }
}
