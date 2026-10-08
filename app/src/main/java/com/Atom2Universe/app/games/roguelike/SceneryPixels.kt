package com.Atom2Universe.app.games.roguelike

/** Small integer-only raster shared by code-generated scenery. */
internal class SceneryPixels(val size: Int = 48, fill: Long = 0) {
    val pixels = IntArray(size * size) { fill.toInt() }
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Long) {
        for (yy in y.coerceAtLeast(0) until (y+h).coerceAtMost(size))
            for (xx in x.coerceAtLeast(0) until (x+w).coerceAtMost(size)) pixels[yy*size+xx] = color.toInt()
    }
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Long) {
        var x=x0; var y=y0
        val dx=kotlin.math.abs(x1-x0); val dy=-kotlin.math.abs(y1-y0)
        val sx=if(x0<x1)1 else -1; val sy=if(y0<y1)1 else -1
        var error=dx+dy
        while(true) {
            rect(x,y,1,1,color)
            if(x==x1 && y==y1) break
            val e=error*2
            if(e>=dy) { error+=dy; x+=sx }
            if(e<=dx) { error+=dx; y+=sy }
        }
    }
    fun oval(x: Int,y: Int,w: Int,h: Int,color: Long) {
        for(yy in 0 until h) for(xx in 0 until w) {
            val a=(xx+.5-w/2.0)/(w/2.0); val b=(yy+.5-h/2.0)/(h/2.0)
            if(a*a+b*b<=1) rect(x+xx,y+yy,1,1,color)
        }
    }
    fun polygon(color: Long, vararg points: Int) {
        val count=points.size/2
        for(y in 0 until size) {
            val intersections=mutableListOf<Int>()
            for(i in 0 until count) {
                val j=(i+1)%count
                val ax=points[i*2];val ay=points[i*2+1]
                val bx=points[j*2];val by=points[j*2+1]
                if((ay<=y && by>y)||(by<=y && ay>y))
                    intersections += ax+(y-ay)*(bx-ax)/(by-ay)
            }
            intersections.sort()
            for(i in 0 until intersections.size-1 step 2)
                rect(intersections[i],y,intersections[i+1]-intersections[i]+1,1,color)
        }
    }
}
