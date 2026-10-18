package com.Atom2Universe.app.science.parentes

import kotlin.math.hypot
import kotlin.math.max
import java.util.TreeSet

/** Screen-space labels stay readable while the geometry can grow beyond overview scale. */
internal object TreeZoom {
    const val MIN=.04f
    data class Point(val x:Float,val y:Float)
    data class Camera(val zoom:Float,val x:Float,val y:Float) {
        /** Preserve the world point under [focusX], [focusY], optionally moving it to a new screen point. */
        fun focus(next:Float,focusX:Float,focusY:Float,targetX:Float=focusX,targetY:Float=focusY)=
            Camera(next,targetX-(focusX-x)/zoom*next,targetY-(focusY-y)/zoom*next)
    }

    fun readable(distance:Float,labelWidth:Float,labelHeight:Float):Float =
        if(distance.isFinite() && distance>0f) max(.65f,hypot(labelWidth,labelHeight)*1.12f/distance) else .65f

    fun limit(points:List<Point>,labelWidth:Float,labelHeight:Float):Float =
        max(4f,readable(nearestSpacing(points),labelWidth,labelHeight))

    /** Closest pair by a sweep line: preparation stays O(n log n), even for a large atlas. */
    fun nearestSpacing(points:List<Point>):Float {
        val sorted=points.distinct().sortedWith(compareBy<Point>{it.x}.thenBy{it.y})
        if(sorted.size<2)return Float.POSITIVE_INFINITY
        val active=TreeSet(compareBy<Point>{it.y}.thenBy{it.x})
        var best=hypot(sorted[1].x-sorted[0].x,sorted[1].y-sorted[0].y)
        var start=0
        for(p in sorted) {
            while(start<sorted.size && p.x-sorted[start].x>best)active.remove(sorted[start++])
            val nearby=active.subSet(Point(Float.NEGATIVE_INFINITY,p.y-best),true,
                Point(Float.POSITIVE_INFINITY,p.y+best),true)
            for(q in nearby)best=minOf(best,hypot(p.x-q.x,p.y-q.y))
            active.add(p)
        }
        return best
    }
}
