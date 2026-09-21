package com.Atom2Universe.app.games.roguelike

/** Blue-grey alloys, pastel cyan instruments and amber accents from DungeonSpaceshipBackdrop. */
internal class SpaceshipTileArt {
    companion object { const val SIZE = 48 }
    private var pixels = IntArray(SIZE * SIZE)
    private val ink = 0xFF1B3043.toInt()
    private val mortar = 0xFF293E52.toInt()
    private val dark = 0xFF405168.toInt()
    private val stone = 0xFF657C86.toInt()
    private val light = 0xFF98ACA9.toInt()
    private val cyan = 0xFF82C9CE.toInt()
    private val warm = 0xFFC7AA78.toInt()
    private fun hash(x: Int, y: Int, salt: Int = 0): Int {
        var n = x * 374761393 + y * 668265263 + salt * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and Int.MAX_VALUE
    }
    private fun dot(x: Int, y: Int, color: Int) {
        if (x in 0..47 && y in 0..47) pixels[y * SIZE + x] = color
    }
    private fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y.coerceAtLeast(0) until (y + h).coerceAtMost(SIZE))
            for (xx in x.coerceAtLeast(0) until (x + w).coerceAtMost(SIZE)) dot(xx, yy, color)
    }
    private fun line(x: Int, y: Int, dx: Int, dy: Int, length: Int, color: Int) {
        for (i in 0 until length) dot(x + dx * i, y + dy * i, color)
    }
    fun render(tx: Int, ty: Int, wall: Boolean, stairs: Boolean, neighbours: Int): IntArray {
        pixels = IntArray(SIZE * SIZE) { if (wall) mortar else ink }
        val seed = hash(tx, ty)
        if (wall) masonry(tx, ty, seed, neighbours) else floor(tx, ty, seed, neighbours)
        if (stairs) stairs()
        return pixels
    }
    private fun panel(x:Int,y:Int,w:Int,h:Int) {
        rect(x,y,w,h,ink);rect(x+1,y+1,w-2,h-3,dark)
        rect(x+2,y+1,w-4,2,stone);rect(x+2,y+h-3,w-4,1,mortar)
        for(xx in intArrayOf(x+3,x+w-4)) {dot(xx,y+4,light);dot(xx,y+h-5,stone)}
    }
    private fun masonry(tx:Int,ty:Int,seed:Int,mask:Int) {
        rect(0,0,48,48,dark)
        for(x in 0..47 step 16) {rect(x,0,1,48,ink);rect(x+2,2,12,1,stone)}
        if(mask and 1==0){rect(0,0,48,3,light);rect(0,3,48,2,stone)}
        if(mask and 8==0){rect(0,0,3,48,light);rect(3,3,2,45,stone)}
        if(mask and 2==0){rect(44,0,4,48,ink);rect(42,1,2,46,stone)}
        if(mask and 4==0){rect(0,37,48,11,ink);rect(1,38,46,6,stone);rect(1,38,46,1,light);rect(5,45,36,1,cyan)}
        when(seed%8) {
            0->console()
            1->porthole(false)
            2->porthole(true)
            3->ducts()
            4->lockers()
            5->batteries()
            6->vent()
            else->sealedPanel()
        }
    }
    private fun console() {
        panel(5,7,38,28)
        rect(9,10,30,14,ink);rect(10,11,28,12,0xFF1E465A.toInt())
        rect(12,12,15,1,cyan)
        for(i in 0..5)rect(12+i*4,20-i%3,2,2+i%3,0xFF4E9DB2.toInt())
        rect(6,26,36,4,stone);rect(7,26,34,1,light)
        for(i in 0..6)rect(9+i*4,28,2,1,if(i%3==0)warm else cyan)
        rect(10,32,28,1,mortar)
    }
    private fun porthole(planet:Boolean) {
        rect(6,9,36,23,stone);rect(9,6,30,29,stone)
        rect(9,9,30,23,0xFF111F36.toInt());rect(11,8,26,25,0xFF111F36.toInt())
        rect(10,6,28,1,light);rect(6,10,1,20,light)
        for(i in 0..12){val x=11+i*7%26;val y=11+i*11%19;dot(x,y,if(i%3==0)light else stone)}
        if(planet) {
            for(y in -8..8)for(x in -8..8)if(x*x+y*y<=64)dot(23+x,20+y,if(x>3)0xFF395B82.toInt() else 0xFF4C789A.toInt())
            rect(17,15,8,2,0xFF75AEAF.toInt());rect(18,17,5,4,0xFF75AEAF.toInt())
            rect(22,22,6,2,0xFF75AEAF.toInt());rect(18,13,7,1,0xFFBCD9CC.toInt())
        }
        rect(12,31,24,1,metalReflection())
        dot(8,12,light);dot(39,29,light)
    }
    private fun metalReflection() = 0xFF89B8BD.toInt()
    private fun ducts() {
        panel(5,5,38,31)
        for(x in intArrayOf(11,23,34)) {
            rect(x,7,5,27,ink);rect(x,7,3,27,stone);rect(x,8,1,25,light)
            for(y in intArrayOf(13,27)){rect(x-1,y,7,3,dark);rect(x,y,5,1,cyan)}
        }
        rect(7,18,14,5,stone);rect(7,18,13,1,light);dot(19,20,warm)
    }
    private fun lockers() {
        for(x in intArrayOf(5,25)) {
            panel(x,6,18,30);rect(x+3,9,12,21,0xFF71899B.toInt())
            rect(x+3,9,11,1,light);rect(x+12,18,2,7,ink)
            rect(x+5,13,6,2,warm);rect(x+7,13,2,2,dark)
            for(y in 25..29 step 2)rect(x+5,y,6,1,dark)
        }
    }
    private fun batteries() {
        panel(7,6,34,30)
        for(x in intArrayOf(12,24)) {
            rect(x,10,10,22,ink);rect(x+2,9,6,24,stone)
            rect(x+2,13,6,14,0xFF527D87.toInt());rect(x+3,14,2,11,cyan)
            rect(x+1,10,8,3,light);rect(x+1,28,8,3,light)
        }
        rect(17,34,15,1,warm)
    }
    private fun vent() {
        panel(6,7,36,28)
        rect(10,10,28,19,ink)
        for(y in 12..28 step 4){rect(11,y,26,2,stone);rect(12,y,23,1,light)}
        rect(11,31,8,1,cyan);rect(29,31,8,1,cyan)
    }
    private fun sealedPanel() {
        panel(7,5,34,31)
        rect(12,9,24,22,stone);rect(13,10,10,19,dark);rect(25,10,10,19,dark)
        rect(23,9,2,22,ink);rect(19,17,2,7,light);rect(28,17,2,7,light)
        for(i in 0..3){rect(13+i*6,31,3,2,warm);rect(16+i*6,31,3,2,ink)}
        rect(5,15,3,9,ink);dot(6,17,cyan);dot(6,20,warm)
    }
    private fun floor(tx:Int,ty:Int,seed:Int,mask:Int) {
        rect(0,0,48,48,mortar)
        for(row in 0..1)for(col in -1..1){
            val x=col*32+row%2*16;val y=row*24
            rect(x+1,y+1,30,22,if(hash(tx+col,ty+row)%3==0)0xFF435B6C.toInt() else 0xFF3C5163.toInt())
            rect(x+3,y+2,26,1,0xFF6B8091.toInt());rect(x+3,y+20,7,1,ink)
            dot(x+4,y+5,light);dot(x+27,y+18,stone)
        }
        when(seed%10) {
            0->{rect(12,12,24,23,ink);for(y in 14..32 step 3)rect(14,y,20,1,stone)}
            1->{rect(13,12,23,24,dark);rect(14,12,21,1,stone);rect(14,35,21,1,ink);rect(22,22,6,2,ink);dot(16,15,light);dot(32,32,light)}
            2->{for(i in 0..3){rect(13+i*6,22,3,2,warm);rect(15+i*6,24,3,2,warm)}}
            3->{line(12,17,1,1,9,stone);line(16,17,1,1,6,dark)}
            else->Unit
        }
        if(mask and 1!=0){rect(0,0,48,4,ink);rect(7,1,30,1,cyan)}
        if(mask and 8!=0){rect(0,0,4,48,ink);rect(1,8,1,29,cyan)}
        if(mask and 2!=0){rect(44,0,4,48,ink);rect(46,8,1,29,cyan)}
        if(mask and 4!=0){rect(0,44,48,4,ink);rect(7,46,30,1,cyan)}
    }
    private fun stairs() {
        panel(3,3,42,42);rect(8,7,32,33,ink)
        for(i in 0..5){val x=10+i*2;val y=11+i*5;rect(x,y,28-i*4,3,stone);rect(x,y,28-i*4,1,light)}
        rect(5,9,1,28,cyan);rect(42,9,1,28,cyan)
        line(19,38,1,1,5,warm);line(24,42,1,-1,5,warm)
    }
}