package com.Atom2Universe.app.games.roguelike

import kotlin.math.*

/** Palette and plants follow DungeonOutdoorBackdrop: ochre meadow, teal jungle, pastel flowers. */
internal class ForestTileArt {
    companion object {
        const val SIZE = 48
        private fun canopy(x: Double, y: Double) =
            ((sin(x * .24) + cos(y * .27) + sin((x + y) * .14)) / 3.0 * .9 + .5).coerceIn(0.0, 1.0)
        fun isJungle(x: Int, y: Int) = canopy(x + .5, y + .5) > .55
    }
    private var pixels = IntArray(SIZE * SIZE)
    private val ink = 0xFF315247.toInt()
    private val deep = 0xFF456A59.toInt()
    private val leaf = 0xFF789F78.toInt()
    private val light = 0xFFA3B57A.toInt()
    private val cream = 0xFFDDD6AE.toInt()
    private val bark = 0xFF79634F.toInt()
    private val wood = 0xFF998367.toInt()
    private val woodLight = 0xFFC0AC83.toInt()
    private val pink = 0xFFE2A1B6.toInt()
    private val lilac = 0xFFC6AAC3.toInt()
    private val peach = 0xFFE3B386.toInt()
    private fun hash(x: Int, y: Int, salt: Int = 0): Int {
        var n = x * 374761393 + y * 668265263 + salt * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and Int.MAX_VALUE
    }
    private fun dot(x: Int, y: Int, color: Int) { if (x in 0..47 && y in 0..47) pixels[y * SIZE + x] = color }
    private fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y.coerceAtLeast(0) until (y + h).coerceAtMost(SIZE))
            for (xx in x.coerceAtLeast(0) until (x + w).coerceAtMost(SIZE)) dot(xx, yy, color)
    }
    private fun oval(x: Int, y: Int, rx: Int, ry: Int, color: Int) {
        for (yy in -ry..ry) for (xx in -rx..rx)
            if (xx * xx * ry * ry + yy * yy * rx * rx <= rx * rx * ry * ry) dot(x + xx, y + yy, color)
    }
    private fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        var x=x0; var y=y0; val dx=abs(x1-x0); val dy=-abs(y1-y0)
        val sx=if(x0<x1) 1 else -1; val sy=if(y0<y1) 1 else -1; var e=dx+dy
        while(true) { dot(x,y,color); if(x==x1 && y==y1)break; val e2=e*2
            if(e2>=dy){e+=dy;x+=sx}; if(e2<=dx){e+=dx;y+=sy} }
    }
    private fun mix(a: Int, b: Int, t: Double): Int {
        fun channel(shift: Int) = (((a ushr shift and 255) * (1-t) + (b ushr shift and 255) * t).roundToInt()) shl shift
        return 0xFF000000.toInt() or channel(16) or channel(8) or channel(0)
    }
    fun render(tx: Int, ty: Int, wall: Boolean, stairs: Boolean, neighbours: Int): IntArray {
        pixels = IntArray(SIZE * SIZE)
        val seed=hash(tx,ty); val jungle=isJungle(tx,ty)
        ground(tx,ty,wall,neighbours)
        if(stairs) descent(jungle) else if(wall) {
            oval(25,39,20,6,deep)
            if(jungle) when(seed%6) {
                0 -> tropicalTree(seed)
                1 -> banana()
                2 -> ferns(seed)
                3 -> fallenLog(true)
                4 -> boulder(true)
                else -> floweringShrub(true,seed)
            } else when(seed%6) {
                0 -> tree(seed)
                1 -> floweringShrub(false,seed)
                2 -> fence(neighbours)
                3 -> fallenLog(false)
                4 -> boulder(false)
                else -> birches()
            }
        }
        return pixels
    }
    private fun ground(tx: Int,ty: Int,wall: Boolean,mask: Int) {
        for(y in 0..47)for(x in 0..47) {
            val wx=tx*SIZE+x; val wy=ty*SIZE+y
            val t=canopy(wx/48.0,wy/48.0)
            val border=(mask and 1!=0 && y<4+hash(wx,ty)%4) || (mask and 2!=0 && x>41+hash(tx,wy)%4) ||
                (mask and 4!=0 && y>41+hash(wx,ty)%4) || (mask and 8!=0 && x<4+hash(tx,wy)%4)
            val meadow=if(wall||border) 0xFF7F9867.toInt() else 0xFFB4A67C.toInt()
            val color=mix(meadow,if(wall||border) deep else 0xFF68846A.toInt(),t)
            val noise=hash(wx,wy,4)%101
            dot(x,y,when(noise){0->mix(color,cream,.22);1->mix(color,ink,.17);else->color})
        }
        val seed=hash(tx,ty)
        repeat(if(wall) 9 else 5){i->
            val x=3+hash(tx,ty,i+11)%42;val y=5+hash(tx,ty,i+23)%39
            if(wall || isJungle(tx,ty)) tuft(x,y) else {rect(x,y,2,1,woodLight);dot(x+1,y+1,wood)}
        }
        // Small edge flowers never occupy the open centre of a path.
        if(!wall && mask!=0 && seed%3==0) flower(if(mask and 8!=0) 5 else 42,32,if(isJungle(tx,ty))pink else lilac,2)
    }
    private fun tuft(x: Int,y: Int) {line(x,y,x-2,y-3,leaf);line(x+1,y,x+1,y-5,light);line(x+2,y,x+4,y-2,leaf)}
    private fun flower(x: Int,y: Int,color: Int,size: Int) {
        line(x,y,x,y-5,deep);rect(x-size,y-6,size*2+1,3,color);rect(x-1,y-7,3,5,color);dot(x,y-5,cream)
    }
    private fun crown(x: Int,y: Int,rx: Int,ry: Int,color: Int) {
        oval(x,y+2,rx,ry,ink);oval(x-1,y,rx-1,ry-1,color)
        oval(x-3,y-ry/3,(rx-3).coerceAtLeast(2),(ry/2).coerceAtLeast(2),leaf)
        // Small leaf clusters break up the smooth masses without noisy single-pixel dithering.
        for(i in 0..5) {
            val xx=x-rx/2+(i%3)*rx/3
            val yy=y-ry/2+(i/3)*ry/2
            oval(xx,yy,3,2,if(i%3==0)color else leaf)
            rect(xx-1,yy-1,2,1,if(i/3==0)light else color)
        }
        rect(x-rx/2,y-ry+2,rx/2,1,light)
    }
    private fun tree(seed: Int) {
        rect(20,22,8,18,bark);rect(21,24,2,15,woodLight)
        line(23,33,14,41,wood);line(25,34,33,40,bark)
        rect(24,32,2,3,ink);dot(24,31,woodLight)
        crown(15,21,12,10,0xFF638A6B.toInt());crown(32,20,12,10,0xFF638A6B.toInt())
        crown(24,12,16,11,0xFF83A17A.toInt())
        for(i in 0..4){val x=9+i*7;dot(x,17+seed%(i+3),light)}
        tuft(34,40);flower(9,40,lilac,1)
    }
    private fun birches() {
        for(i in 0..1){val x=13+i*19
            rect(x,14,5,25,wood);rect(x,14,3,24,cream)
            for(y in 18..36 step 6){rect(x,y,2,1,bark);dot(x+2,y+2,bark)}
            crown(x+2,11+i*3,11,9,0xFF8DA987.toInt())
        }
        tuft(25,40)
    }
    private fun floweringShrub(jungle: Boolean,seed: Int) {
        crown(14,28,11,9,if(jungle)0xFF487C84.toInt() else 0xFF78975F.toInt())
        crown(32,28,12,10,if(jungle)0xFF3F8468.toInt() else 0xFF78975F.toInt())
        crown(23,21,13,11,if(jungle)0xFF487C84.toInt() else 0xFF89A178.toInt())
        for(i in 0..4)flower(10+i*7,26+(i%2)*8,if(i%2==0)if(jungle)pink else lilac else peach,if(jungle)2 else 1)
        tuft(8,39);dot(25,36,if(seed%2==0)cream else lilac)
    }
    private fun fence(mask: Int) {
        val vertical=mask and 1!=0 && mask and 4!=0 && mask and 10==0
        if(vertical){
            for(y in intArrayOf(7,35)){rect(15,y,18,5,wood);rect(16,y,15,1,woodLight)}
            rect(18,3,4,41,bark);rect(19,3,1,40,woodLight);rect(29,3,3,41,wood)
        }else{
            rect(1,21,46,4,wood);rect(1,30,46,4,bark);rect(1,21,46,1,woodLight)
            for(x in intArrayOf(7,36)){rect(x,13,5,28,wood);rect(x,13,2,26,woodLight);dot(x+2,22,bark)}
            line(10,32,37,23,woodLight)
        }
        tuft(14,41);flower(31,39,peach,1)
    }
    private fun fallenLog(jungle: Boolean) {
        rect(9,24,31,13,bark);rect(10,24,29,3,wood)
        oval(10,30,6,7,bark);oval(10,29,4,5,woodLight);oval(10,29,2,3,wood)
        line(18,28,37,27,woodLight);line(16,34,34,34,ink)
        rect(21,22,12,4,leaf);rect(24,21,6,2,light)
        if(jungle){leafBlade(31,26,39,9,0xFF487C84.toInt());flower(23,24,pink,2)}
        else {rect(31,19,2,5,cream);rect(28,18,8,3,peach);dot(30,18,cream)}
    }
    private fun boulder(jungle: Boolean) {
        oval(25,30,18,10,ink);oval(23,26,16,11,0xFF81988D.toInt())
        oval(20,23,12,8,0xFFAAB9A3.toInt());line(15,18,24,18,cream)
        line(29,21,26,26,0xFF647C75.toInt());line(26,26,29,31,0xFF647C75.toInt())
        rect(11,31,10,4,leaf);rect(13,30,5,2,light)
        if(jungle)fern(35,38,10) else flower(37,38,lilac,1)
    }
    private fun leafBlade(x: Int,y: Int,tx: Int,ty: Int,color: Int) {
        val dx=tx-x;val dy=ty-y;val length=max(abs(dx),abs(dy)).coerceAtLeast(1)
        for(i in 0..length){val xx=x+dx*i/length;val yy=y+dy*i/length
            val radius=(sin(PI*i/length)*4).roundToInt()
            oval(xx,yy,radius.coerceAtLeast(1),2,color)
        }
        line(x,y,tx,ty,light)
        // Cut leaf margins, as in the tropical combat backdrop.
        for(i in 5 until length step 6){val xx=x+dx*i/length;val yy=y+dy*i/length;dot(xx+3,yy,deep)}
    }
    private fun banana() {
        rect(23,19,3,20,wood)
        leafBlade(24,29,9,8,0xFF3F8468.toInt());leafBlade(24,28,38,6,0xFF487C84.toInt())
        leafBlade(24,30,42,22,0xFF719D82.toInt());leafBlade(24,30,5,24,0xFF719D82.toInt())
        leafBlade(24,29,23,3,0xFF3F8468.toInt());flower(35,39,pink,3)
    }
    private fun fern(x: Int,y: Int,size: Int) {
        for(side in intArrayOf(-1,1)) {
            line(x,y,x+side*size,y-size,leaf)
            for(i in 2 until size step 2){line(x+side*i,y-i,x+side*(i+4),y-i,0xFF9ABF75.toInt());line(x+side*i,y-i,x+side*i,y-i-4,leaf)}
        }
        line(x,y,x,y-size-4,light)
    }
    private fun ferns(seed: Int) {
        oval(24,32,19,9,ink)
        fern(16,34,11);fern(32,37,12);fern(25,24,10)
        flower(9,39,if(seed%2==0)pink else lilac,2)
        rect(36,31,2,6,cream);rect(33,29,8,3,peach);dot(35,29,cream)
    }
    private fun tropicalTree(seed: Int) {
        rect(20,3,10,35,bark);rect(21,5,3,30,wood)
        line(23,32,10,40,wood);line(27,31,37,40,bark)
        leafBlade(23,14,4,7,0xFF315F50.toInt());leafBlade(25,13,44,8,0xFF487C84.toInt())
        leafBlade(24,8,13,1,0xFF3F8468.toInt());leafBlade(25,9,36,1,0xFF3F8468.toInt())
        for(i in 0..2){line(12+i*9,8,13+i*9,22+i*3,leaf);dot(13+i*9,19+i*3,light)}
        fern(11,38,8);flower(37,37,if(seed%2==0)pink else peach,2)
    }
    private fun descent(jungle: Boolean) {
        rect(5,9,38,33,ink)
        for(i in 0..5){val x=9+i*2;val y=13+i*5;rect(x,y,30-i*4,3,0xFF647C75.toInt());rect(x,y,30-i*4,1,cream)}
        rect(3,8,4,35,wood);rect(41,8,4,35,bark);rect(3,7,42,3,woodLight)
        if(jungle){fern(7,20,7);leafBlade(37,12,44,2,leaf)}else{tuft(5,12);flower(41,13,lilac,1)}
        line(19,39,24,43,peach);line(24,43,29,39,peach)
    }
}
