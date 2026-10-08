package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*

enum class TableFamily { CAROM, POOL, BLACKBALL, SNOOKER, PYRAMID, HEYBALL }
enum class Discipline(val family: TableFamily) {
    FREE(TableFamily.CAROM), ONE_CUSHION(TableFamily.CAROM), THREE_CUSHION(TableFamily.CAROM),
    CADRE_47_1(TableFamily.CAROM), CADRE_47_2(TableFamily.CAROM), CADRE_71_2(TableFamily.CAROM),
    FIVE_PINS(TableFamily.CAROM), NINE_PINS(TableFamily.CAROM), ARTISTIC(TableFamily.CAROM),
    EIGHT(TableFamily.POOL), NINE(TableFamily.POOL), TEN(TableFamily.POOL),
    STRAIGHT(TableFamily.POOL), ONE_POCKET(TableFamily.POOL), BANK(TableFamily.POOL),
    BLACKBALL(TableFamily.BLACKBALL), SNOOKER(TableFamily.SNOOKER), SIX_RED(TableFamily.SNOOKER),
    ENGLISH(TableFamily.SNOOKER), PYRAMID_FREE(TableFamily.PYRAMID),
    PYRAMID_DYNAMIC(TableFamily.PYRAMID), HEYBALL(TableFamily.HEYBALL)
}

data class Rail(val a: V3, val b: V3) {
    val tangent = (b-a).unit()
    val normal = V3(tangent.y, -tangent.x)
    val length = (b-a).length()
}
data class Pocket(val id: Int, val center: V3, val radius: Double)
data class Pin(val id: Int, val p: V3, var down: Boolean = false)
data class BilliardRegion(val id: Int, val x0: Double, val y0: Double, val x1: Double, val y1: Double,
                          val corner: Boolean = false) {
    fun contains(p: V3): Boolean {
        val x=(p.x-x0)/(x1-x0); val y=(p.y-y0)/(y1-y0)
        return x in 0.0..1.0 && y in 0.0..1.0 && (!corner || x+y<=1)
    }
}

class BilliardTable(val family: TableFamily, val clothSpeed: Int = 1, val discipline: Discipline? = null,
                   val size: BilliardTableSize = BilliardTableSize.defaultFor(family)) {
    init { require(size.family==family) }
    // Playing area, measured between cushion noses (not the outside cabinet).
    // WPA equipment specifications; WPBSA 2024, section 1; UMB, article 11.
    val length = size.length
    val width = size.width
    val radius = when(family) { TableFamily.CAROM -> .03075; TableFamily.SNOOKER -> .02625; TableFamily.BLACKBALL -> .0254; TableFamily.PYRAMID -> .034; else -> .028575 }
    val mass = when(family) { TableFamily.CAROM -> .210; TableFamily.SNOOKER -> .142; TableFamily.BLACKBALL -> .118; TableFamily.PYRAMID -> .280; else -> .170 }
    private val snookerScale = if(family==TableFamily.SNOOKER) length/3.569 else 1.0
    val headLine = when(family) { TableFamily.SNOOKER -> .737*snookerScale; TableFamily.BLACKBALL -> length/5; else -> length/4 }
    val dRadius = .292*snookerScale
    val blackSpotInset = .324*snookerScale
    // FFB art. 1.3.01 / 1.3.04. Ball and pocket sizes do not scale with the bed.
    val startSpotOffset = when(size) {
        BilliardTableSize.CAROM_230 -> .148
        BilliardTableSize.CAROM_252 -> .162
        BilliardTableSize.CAROM_210 -> .135
        else -> .1825
    }
    val cadreInset = if(discipline==Discipline.CADRE_71_2) length/4 else when(size) {
        BilliardTableSize.CAROM_230 -> .383
        BilliardTableSize.CAROM_252 -> .420
        BilliardTableSize.CAROM_210 -> .350
        else -> .473
    }
    val footSpot get() = V3(length*.75,width/2,radius)
    val headSpot get() = V3(headLine,width/2,radius)
    fun colorSpot(id: Int): V3 = when(id) {
        16 -> V3(headLine,width/2+dRadius,radius)
        17 -> V3(headLine,width/2-dRadius,radius)
        18 -> headSpot
        19 -> V3(length/2,width/2,radius)
        20 -> footSpot
        else -> V3(length-blackSpotInset,width/2,radius)
    }
    val cloth = Cloth(rolling = when(clothSpeed) { 0 -> .014; 2 -> .0065; else -> .010 },
        spin = 4*radius/9, cushionRestitution = if(family == TableFamily.CAROM) .87 else .85)
    /** Heights above the slate of the cushion nose and of the wooden rail cap: drawn, and cleared by the cue. */
    val noseHeight get() = radius*1.27
    val railTop get() = noseHeight+.012
    val rails: List<Rail>
    val pockets: List<Pocket>
    /** Ordered nose contours, shared by the collider and the cloth-covered rubber mesh. */
    val cushionPaths: List<List<V3>>
    private val surfaceBoundary: List<V3>
    // Corner mouth is the diagonal distance between the two noses. Side mouths
    // have their own width; multiplying both by the same factor distorts them.
    val cornerOpening = when(family) { TableFamily.PYRAMID -> .073; TableFamily.SNOOKER -> .086; TableFamily.HEYBALL -> .085; TableFamily.BLACKBALL -> .090; else -> .1143 }
    val sideOpening = when(family) { TableFamily.PYRAMID -> .082; TableFamily.SNOOKER -> .090; TableFamily.HEYBALL -> .095; TableFamily.BLACKBALL -> .100; else -> .127 }
    val mouth = cornerOpening/sqrt(2.0)
    val sideMouth = sideOpening/2
    val regions: List<BilliardRegion> = buildList {
        if(discipline==Discipline.FREE) {
            for(x in listOf(0.0,length)) for(y in listOf(0.0,width))
                add(BilliardRegion(size,x,y,x+if(x==0.0) length/4 else -length/4,y+if(y==0.0) width/4 else -width/4,true))
        } else if(discipline in listOf(Discipline.CADRE_47_1,Discipline.CADRE_47_2,Discipline.CADRE_71_2)) {
            val inset=cadreInset
            val xs=listOf(0.0,inset,length-inset,length)
            val ys=if(discipline==Discipline.CADRE_71_2) listOf(0.0,width/2,width) else listOf(0.0,inset,width-inset,width)
            for(i in 0 until xs.lastIndex) for(j in 0 until ys.lastIndex) add(BilliardRegion(size,xs[i],ys[j],xs[i+1],ys[j+1]))
            var id=100
            for(x in xs.drop(1).dropLast(1)) for(y in listOf(0.0,width-.178)) add(BilliardRegion(id++,x-.089,y,x+.089,y+.178))
            for(y in ys.drop(1).dropLast(1)) for(x in listOf(0.0,length-.178)) add(BilliardRegion(id++,x,y-.089,x+.178,y+.089))
        }
    }
    init {
        if (family == TableFamily.CAROM) {
            rails = listOf(Rail(V3(), V3(length)), Rail(V3(length), V3(length,width)),
                Rail(V3(length,width), V3(0.0,width)), Rail(V3(0.0,width), V3()))
            pockets = emptyList()
            cushionPaths = listOf(rails.map { it.a })
        } else {
            val m = mouth; val mid = length/2; val side = sideMouth
            // Each path has an entry jaw, a straight nose, then an exit jaw.
            // Keeping that winding makes every normal point into the rubber,
            // including the six corner faces that previously faced into the pocket.
            cushionPaths = listOf(
                listOf(V3(.015,-m*.8),V3(m,0.0),V3(mid-side,0.0),V3(mid-side*.75,-m)),
                listOf(V3(mid+side*.75,-m),V3(mid+side,0.0),V3(length-m,0.0),V3(length-.015,-m*.8)),
                listOf(V3(length+m*.8,.015),V3(length,m),V3(length,width-m),V3(length+m*.8,width-.015)),
                listOf(V3(length-.015,width+m*.8),V3(length-m,width),V3(mid+side,width),V3(mid+side*.75,width+m)),
                listOf(V3(mid-side*.75,width+m),V3(mid-side,width),V3(m,width),V3(.015,width+m*.8)),
                listOf(V3(-m*.8,width-.015),V3(0.0,width-m),V3(0.0,m),V3(-m*.8,.015))
            )
            fun entry(i: Int)=Rail(cushionPaths[i][0],cushionPaths[i][1])
            fun exit(i: Int)=Rail(cushionPaths[i][2],cushionPaths[i][3])
            // Preserve segment IDs used by bank calls and saved collision histories.
            rails = cushionPaths.map { Rail(it[1],it[2]) } + listOf(
                entry(0),exit(5),exit(1),entry(2),exit(2),entry(3),entry(5),exit(4),
                exit(0),entry(1),exit(3),entry(4))
            pockets = listOf(Pocket(0,V3(-m*.45,-m*.45),m*.80),
                Pocket(1,V3(mid,-m*.9),m*.80), Pocket(2,V3(length+m*.45,-m*.45),m*.80),
                Pocket(3,V3(length+m*.45,width+m*.45),m*.80),
                Pocket(4,V3(mid,width+m*.9),m*.80), Pocket(5,V3(-m*.45,width+m*.45),m*.80))
        }
        // The gaps close across the back of each throat, inside its capture circle.
        surfaceBoundary=cushionPaths.flatten()
    }

    /** Middle of the mouth, between the two cushion points. Aim here, not at the
     * centre: it lies behind the corner, and from an angle that line hits the
     * long cushion just before the point. Pocket i follows cushion path i-1. */
    fun opening(pocket: Pocket): V3 = (cushionPaths[(pocket.id+5)%6][2]+cushionPaths[pocket.id][1])*.5

    /** Includes pocket throats, but excludes the wood behind the cushions. */
    fun supportsCenter(p: V3): Boolean {
        if(p.x in 0.0..length && p.y in 0.0..width) return true
        var inside=false
        var a=surfaceBoundary.last()
        for(b in surfaceBoundary) {
            if((a.y>p.y)!=(b.y>p.y) && p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x) inside=!inside
            a=b
        }
        return inside
    }
    fun ball(id: Int, x: Double, y: Double): Ball {
        // Printed badges face different directions, including the break camera.
        val phase=PI/2+(id*.173)%.7-.35
        return Ball(id,V3(x,y,radius),radius,mass,q=doubleArrayOf(0.0,0.0,sin(phase/2),cos(phase/2)))
    }
    fun inside(p: V3) = p.x >= radius && p.x <= length-radius && p.y >= radius && p.y <= width-radius
    fun canPlace(p: V3, balls: List<Ball>, ignoreId: Int): Boolean = inside(p) &&
        balls.none { it.id != ignoreId && it.motion != Motion.POCKETED && (it.p-p).planar().length() < radius+it.radius+1e-6 }
    fun pins(d: Discipline): MutableList<Pin> = buildList {
        if (d == Discipline.FIVE_PINS || d == Discipline.NINE_PINS) {
            val c = V3(length/2,width/2); add(Pin(0,c))
            for (p in listOf(V3(.066),V3(-.066),V3(0.0,.066),V3(0.0,-.066))) add(Pin(size,c+p))
            // Goriziana has a longer cross, not a 3 x 3 square.
            if(d == Discipline.NINE_PINS) for(p in listOf(V3(.132),V3(-.132),V3(0.0,.132),V3(0.0,-.132))) add(Pin(size,c+p))
        }
    }.toMutableList()

    fun rack(d: Discipline): MutableList<Ball> {
        val result = mutableListOf(ball(0,headLine-radius*2,width*.5))
        if(family == TableFamily.CAROM) {
            val pins=d==Discipline.FIVE_PINS || d==Discipline.NINE_PINS
            result[0].p=V3(length*.25,width*.5+if(pins) .25 else startSpotOffset,radius)
            result += ball(1,if(pins) length-.10 else length*.25,width*.5)
            result += ball(2,length*.75,width*.5)
            return result
        }
        if(d == Discipline.ENGLISH) {
            result[0].p=V3(headLine-dRadius/2,width/2,radius)
            result += ball(1,headLine,width/2)
            result += ball(2,length-blackSpotInset,width/2)
            return result
        }
        if(d == Discipline.SNOOKER || d == Discipline.SIX_RED) {
            val rows = if(d == Discipline.SNOOKER) 5 else 3
            var id = 1
            result[0].p=V3(headLine-dRadius/2,width/2,radius)
            for(row in 0 until rows) for(col in 0..row) result += ball(id++, footSpot.x+radius*2+.0001+row*radius*sqrt(3.0)*1.0001,width/2+(col-row/2.0)*radius*2.0002)
            for(color in 16..21) { val p=colorSpot(color); result += ball(color,p.x,p.y) }
            return result
        }
        val count = when(d) { Discipline.NINE -> 9; Discipline.TEN -> 10; else -> 15 }
        val positions = when(d) { Discipline.NINE -> listOf(1,2,3,2,1); Discipline.TEN -> (1..4).toList(); else -> (1..5).toList() }
        val ids=(1..count).shuffled().toMutableList()
        fun at(index: Int,id: Int) { val old=ids.indexOf(id); val previous=ids[index]; ids[index]=id; ids[old]=previous }
        when(d) {
            Discipline.NINE,Discipline.TEN -> { at(0,1); at(4,count) }
            Discipline.EIGHT,Discipline.HEYBALL -> {
                at(4,8); at(10,(1..7).random()); at(14,(9..15).random())
            }
            Discipline.BLACKBALL -> {
                // WPA blackball pattern: seven of each colour, black in row three.
                val pattern=listOf(1,9,2,3,8,10,11,4,12,5,6,13,14,7,15)
                ids.clear(); ids.addAll(pattern)
            }
            else -> Unit
        }
        // WPA 2025 nine-ball: the NINE is on the foot spot. Blackball likewise
        // places the black on its spot. Other pool triangles put the apex there.
        val apex=footSpot.x-if(d==Discipline.NINE || d==Discipline.BLACKBALL) 2*radius*sqrt(3.0)*1.0001 else 0.0
        var index = 0
        positions.forEachIndexed { row, n -> repeat(n) { col ->
            result += ball(ids[index++],apex+row*radius*sqrt(3.0)*1.0001,width/2+(col-(n-1)/2.0)*radius*2.0002)
        } }
        return result
    }
}
