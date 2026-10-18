package com.Atom2Universe.app.games.roguelike

/** Transparent pixel art layered on the region's own floor. No filtered textures. */
internal class WaterwayTileArt {
    companion object { const val SIZE=48 }
    fun render(bridge: Boolean, horizontalFlow: Boolean, connections: Int, variant: Int): IntArray {
        val pixels=IntArray(SIZE*SIZE)
        fun box(x:Int,y:Int,w:Int,h:Int,c:Long) {
            for(yy in y.coerceAtLeast(0) until (y+h).coerceAtMost(SIZE))
                for(xx in x.coerceAtLeast(0) until (x+w).coerceAtMost(SIZE)) pixels[yy*SIZE+xx]=c.toInt()
        }
        val start=if(connections and (if(horizontalFlow)8 else 1)!=0)0 else 7
        val end=if(connections and (if(horizontalFlow)2 else 4)!=0)48 else 41
        // Work vertically, then rotate the whole tile for east/west flow.
        for(y in start until end) {
            val inset=if(y<start+4 && start>0 || y>=end-4 && end<48)4 else 0
            val edge=9+inset+if(y/6%3==1)1 else 0
            box(edge-3,y,48-edge*2+6,1,0xFF667E70)
            box(edge-1,y,48-edge*2+2,1,0xFFA8B79A)
            box(edge+1,y,48-edge*2-2,1,0xFF507C83)
            box(edge+4,y,48-edge*2-8,1,0xFF6C9EAA)
            box(edge+7,y,7,1,0xFF83B5B9)
        }
        repeat(6) { i ->
            val y=start+3+Math.floorMod(i*11+variant*3,(end-start-6).coerceAtLeast(1))
            val x=15+(i*7+variant*3)%15
            box(x,y,4+i%3,1,0xFFBBDDD1); box(x-2,y+1,2,1,0xFF91C4C3)
        }
        // Small stones and reeds sit on the bank, away from the bridge deck.
        for(y in listOf(5,36)) {
            box(3,y+5,5,3,0xFF506761); box(3,y+4,4,2,0xFFBAC1AA)
            box(40,y,1,9,0xFF78966F); box(43,y+2,1,7,0xFF9EB584)
            box(39,y-2,3,4,0xFFBEAE89); box(42,y,3,3,0xFFCFBC95)
        }
        if(bridge) {
            box(0,16,48,21,0xFF3E6264)
            box(0,14,48,19,0xFF665D53)
            for(x in 0 until 48 step 6) {
                box(x,15,5,16,0xFFB89B7C); box(x,15,5,2,0xFFE0C3A0)
                box(x+1,20+(x/6%3),3,1,0xFF967D69); box(x+2,28,1,1,0xFF655D58)
            }
            // Rails parallel to travel, with stout capped posts and dark footings.
            for(y in listOf(11,32)) {
                box(0,y+2,48,3,0xFF746A5F); box(0,y,48,2,0xFFD7BA95)
                for(x in listOf(2,41)) {
                    box(x,y-3,5,9,0xFF77695C); box(x,y-4,5,2,0xFFE4CBAB)
                    box(x+1,y-2,2,5,0xFFBBA181)
                }
            }
        }
        if(!horizontalFlow) return pixels
        return IntArray(SIZE*SIZE) { i -> val x=i%SIZE;val y=i/SIZE;pixels[x*SIZE+SIZE-1-y] }
    }
}
