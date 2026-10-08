package com.Atom2Universe.app.games.toyboxracers.render

import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import kotlin.math.abs
import kotlin.math.sqrt

/** Shortens the camera boom before a wall or table; never pushes it through the obstacle. */
internal object ChaseCamera {
    fun unobstructed(anchor: Vec3, wanted: Vec3, obstacles: List<RoomBox>): Vec3 {
        val delta=wanted-anchor
        val length=sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z)
        if(length<.001f) return wanted
        var fraction=1f
        for(box in obstacles) {
            if(box.left > maxOf(anchor.x,wanted.x) || box.right < minOf(anchor.x,wanted.x) ||
                box.back > maxOf(anchor.z,wanted.z) || box.front < minOf(anchor.z,wanted.z) ||
                box.bottom > maxOf(anchor.y,wanted.y) || box.top < minOf(anchor.y,wanted.y)) continue
            // Ignore volumes containing the anchor (e.g. the car straddling a slope).
            if(anchor.x in box.left..box.right && anchor.y in box.bottom..box.top && anchor.z in box.back..box.front) continue
            val hit=entry(anchor,delta,box) ?: continue
            fraction=minOf(fraction,(hit-.25f/length).coerceAtLeast(0f))
        }
        return anchor+delta*fraction
    }

    private fun entry(origin: Vec3, delta: Vec3, box: RoomBox): Float? {
        var near=0f
        var far=1f
        for(axis in 0..2) {
            val p=when(axis) { 0->origin.x; 1->origin.y; else->origin.z }
            val d=when(axis) { 0->delta.x; 1->delta.y; else->delta.z }
            val low=when(axis) { 0->box.left; 1->box.bottom; else->box.back }
            val high=when(axis) { 0->box.right; 1->box.top; else->box.front }
            if(abs(d)<.00001f) {
                if(p !in low..high) return null
                continue
            }
            val a=(low-p)/d
            val b=(high-p)/d
            near=maxOf(near,minOf(a,b))
            far=minOf(far,maxOf(a,b))
            if(near>far) return null
        }
        return near
    }
}
