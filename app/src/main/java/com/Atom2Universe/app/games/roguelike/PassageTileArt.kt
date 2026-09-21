package com.Atom2Universe.app.games.roguelike

/** Transparent 48px overlays: the centre of every threshold visibly remains passable. */
internal class PassageTileArt {
    companion object { const val SIZE=48 }
    private var pixels=IntArray(SIZE*SIZE)
    private val ink=0xFF34484B.toInt()
    private val stone=0xFF899A96.toInt()
    private val light=0xFFBEC4AD.toInt()
    private val wood=0xFF9B8060.toInt()
    private val pale=0xFFC0AC83.toInt()
    private val brass=0xFFD5B477.toInt()
    private fun dot(x:Int,y:Int,c:Int){if(x in 0..47&&y in 0..47)pixels[y*48+x]=c}
    private fun rect(x:Int,y:Int,w:Int,h:Int,c:Int){for(yy in y.coerceAtLeast(0) until(y+h).coerceAtMost(48))for(xx in x.coerceAtLeast(0) until(x+w).coerceAtMost(48))dot(xx,yy,c)}
    private fun path(mask:Int){
        // Deterministic ragged margins; N/E/S/W match the connections of the map.
        for(y in 0..47)for(x in 0..47){
            val wobble=(x*13+y*7)%3
            val centre=x in 13-wobble..34+wobble && y in 13-wobble..34+wobble
            val arm=(mask and 1!=0&&x in 13-wobble..34+wobble&&y<24)||
                (mask and 2!=0&&y in 13-wobble..34+wobble&&x>=24)||
                (mask and 4!=0&&x in 13-wobble..34+wobble&&y>=24)||
                (mask and 8!=0&&y in 13-wobble..34+wobble&&x<24)
            if(centre||arm)dot(x,y,when((x*17+y*23)%43){0->0xFFC6B892.toInt();1->0xFF9D9274.toInt();else->0xFFB4A67C.toInt()})
        }
        rect(21,17,3,1,pale);rect(28,32,2,1,wood)
    }
    fun render(kind:PassageKind,horizontal:Boolean,connections:Int):IntArray {
        pixels=IntArray(SIZE*SIZE)
        if(kind==PassageKind.PATH){path(connections);return pixels}
        if(kind==PassageKind.FENCE){
            // Full cross-bar: this tile really is solid.
            rect(0,17,48,5,wood);rect(0,17,48,1,pale)
            rect(0,29,48,4,wood);rect(0,29,48,1,pale)
            for(x in intArrayOf(5,22,39)){rect(x,10,4,30,wood);rect(x,10,1,29,pale);dot(x+2,20,ink)}
        }else{
            // Shallow stone sill underfoot, with the path clearly continuing through the middle.
            rect(6,34,36,5,0xFF647875.toInt());rect(7,34,34,1,light)
            for(x in 14..36 step 9)rect(x,35,1,3,stone)
            for(x in intArrayOf(3,38)){
                rect(x,11,7,28,ink);rect(x,12,5,25,stone);rect(x,12,1,24,light)
                rect(x-1,9,9,3,light);rect(x-1,37,9,4,stone)
                dot(x+2,22,0xFF6D8361.toInt())
            }
            when(kind){
                PassageKind.DOOR->{
                    // Two leaves folded along the jambs; no plank across the walkable centre.
                    rect(10,15,5,17,wood);rect(11,15,1,16,pale)
                    rect(33,15,5,17,wood);rect(34,15,1,16,pale)
                    for(y in intArrayOf(18,28)){rect(10,y,5,2,ink);rect(33,y,5,2,ink)}
                    dot(13,24,brass);dot(34,24,brass)
                }
                PassageKind.GATE->{
                    for(x in intArrayOf(11,34)){
                        rect(x,14,3,19,ink);rect(x+1,15,1,15,stone)
                        dot(x+1,12,light);rect(x-1,21,5,2,ink)
                    }
                    // Lanterns on the stone piers distinguish the cemetery entrance.
                    for(x in intArrayOf(3,39)){rect(x,16,5,8,ink);rect(x+1,18,3,4,brass);dot(x+2,18,light)}
                    rect(5,7,3,2,stone);rect(40,7,3,2,stone)
                }
                PassageKind.PORTCULLIS->{
                    rect(3,7,42,4,ink);rect(4,7,40,1,stone)
                    rect(9,11,30,2,stone)
                    // Teeth stop above the open passage: the grille is raised, not blocking it.
                    for(x in 12..36 step 6){rect(x,10,2,7,ink);dot(x,17,light)}
                    for(y in 16..31 step 4){dot(6,y,brass);dot(40,y,brass)}
                }
                else->Unit
            }
        }
        if(horizontal){
            val rotated=IntArray(SIZE*SIZE)
            for(y in 0..47)for(x in 0..47)rotated[x*48+47-y]=pixels[y*48+x]
            return rotated
        }
        return pixels
    }
}
