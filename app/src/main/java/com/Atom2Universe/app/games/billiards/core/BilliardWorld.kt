package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*

enum class EventKind { BALL, CUSHION, POCKET, PIN, LAND, OFF_TABLE, CROSS_LINE, HEAD_CROSS, REGION_EXIT }
data class BilliardEvent(val kind: EventKind, val ball: Int, val other: Int, val strength: Double, val time: Double,
                         val position: V3? = null)

/** Continuous collision scheduling inside small analytic-motion intervals.
 * Pooltool motion models are exact between transitions; collision roots here use
 * local velocities, bounded by 2ms and a quarter radius, then correct geometry.
 * This is intentionally separate from the shared 2D rigid-body solver.
 */
class BilliardWorld(val table: BilliardTable, val balls: MutableList<Ball>, val pins: MutableList<Pin> = mutableListOf(),
                    private val trackVisualRotation: Boolean = true) {
    var time = 0.0
    val events = mutableListOf<BilliardEvent>()
    val moving get() = balls.any { it.motion != Motion.STILL && it.motion != Motion.POCKETED }
    fun copyDeep() = BilliardWorld(table,balls.map { it.copyDeep() }.toMutableList(), pins.map { it.copy() }.toMutableList(),trackVisualRotation).also { it.time=time }
    /** Same trajectories without the drawn rotation or resting balls (BilliardSimulationTest). */
    fun copyHeadless() = BilliardWorld(table,balls.map { it.copyDeep() }.toMutableList(), pins.map { it.copy() }.toMutableList(),false).also { it.time=time }

    private data class Hit(val t: Double, val kind: EventKind, val a: Int, val b: Int,
                           val normal: V3 = V3.ZERO, val endpoint: V3? = null)

    fun advance(seconds: Double) {
        resolveBoundaryContacts()
        var left = seconds.coerceAtLeast(0.0)
        var iterations = 0
        while(left > 1e-10 && moving && iterations++ < 30000) {
            // With no translation left, spin decay cannot produce contacts or
            // rule events. Headless trials can solve this tail analytically.
            if(!trackVisualRotation && balls.all { it.motion in stationaryMotions }) {
                // Same floor as the drawn path below: a ball that stops rolling with no
                // vertical spin has a zero transition, and a zero tail never stopped it.
                val tail=min(left,balls.maxOf { b ->
                    if(b.motion==Motion.SPINNING) max(1e-9,PooltoolPhysics.transitionTime(b,table.cloth)) else 0.0
                })
                balls.forEach { PooltoolPhysics.evolve(it,table.cloth,tail,false) }
                time+=tail
                break
            }
            val speed = balls.maxOfOrNull { it.v.length() } ?: 0.0
            var dt = min(left,min(.002,table.radius/(4*max(1.0,speed))))
            balls.forEach { dt=min(dt,max(1e-9,PooltoolPhysics.transitionTime(it,table.cloth))) }
            var hit: Hit? = null
            fun offer(h: Hit) { if(h.t.isFinite() && h.t >= -1e-10 && h.t <= dt && (hit == null || h.t < hit!!.t)) hit=h.copy(t=max(0.0,h.t)) }
            for(i in balls.indices) {
                val a=balls[i]; if(a.motion == Motion.POCKETED) continue
                if(a.motion == Motion.AIRBORNE) {
                    val root=(a.v.z+sqrt(a.v.z*a.v.z+2*PooltoolPhysics.G*max(0.0,a.p.z-a.radius)))/PooltoolPhysics.G
                    offer(Hit(root,EventKind.LAND,i,0))
                }
                for(j in i+1 until balls.size) {
                    val b=balls[j]; if(b.motion == Motion.POCKETED) continue
                    if((a.motion==Motion.STILL || a.motion==Motion.SPINNING) &&
                        (b.motion==Motion.STILL || b.motion==Motion.SPINNING)) continue
                    offer(Hit(impactTime(b.p-a.p,b.v-a.v,a.radius+b.radius),EventKind.BALL,i,j))
                }
                // Boundary contacts were already resolved. A stationary ball
                // still participates in ball collisions above, but has no swept
                // pocket/rail/pin path to search during a headless trial.
                if(!trackVisualRotation && (a.motion==Motion.STILL || a.motion==Motion.SPINNING)) continue
                // A slow ball can stop on the lip during this interval. Capture
                // is positional as well as swept, even when its speed is zero.
                if(a.p.z <= a.radius*1.3) for(p in table.pockets) {
                    val rel=(a.p-p.center).planar()
                    if(rel.length() <= p.radius) offer(Hit(0.0,EventKind.POCKET,i,p.id))
                    else offer(Hit(impactTime(rel,a.v.planar(),p.radius),EventKind.POCKET,i,p.id))
                }
                if(a.v.length() < 1e-10) continue
                // Airborne balls can clear the cushion nose.
                if(a.p.z < a.radius*2.2) for((j,rail) in table.rails.withIndex()) {
                    val n=rail.normal; val speedN=a.v.dot(n)
                    val signed=(a.p-rail.a).dot(n)
                    if(speedN > 1e-8 && signed <= a.radius*.25) {
                        val t=(-a.radius-signed)/speedN
                        val p=a.p+a.v*max(0.0,t); val along=(p-rail.a).dot(rail.tangent)
                        if(along >= 0 && along <= rail.length) offer(Hit(max(0.0,t),EventKind.CUSHION,i,j,n))
                    }
                    for(endpoint in 0..1) {
                        val end=if(endpoint==0) rail.a else rail.b
                        val rel=(a.p-end).planar()
                        val t=impactTime(rel,a.v.planar(),a.radius)
                        if(t <= dt) offer(Hit(t,EventKind.CUSHION,i,j,-(rel+a.v.planar()*max(0.0,t)).unit(),end))
                    }
                }
                if(a.p.z < a.radius+.025) for((j,p) in pins.withIndex()) if(!p.down) {
                    offer(Hit(impactTime((a.p-p.p).planar(),a.v.planar(),a.radius+.005),EventKind.PIN,i,j))
                }
            }
            val h=hit
            val elapsed=h?.t ?: dt
            balls.forEach {
                if(!trackVisualRotation && (it.motion==Motion.STILL || it.motion==Motion.POCKETED)) return@forEach
                val old=it.p
                val oldX=it.p.x
                PooltoolPhysics.evolve(it,table.cloth,elapsed,trackVisualRotation)
                if(!trackVisualRotation && it.p==old) return@forEach
                if(oldX>table.length/2 && it.p.x<=table.length/2 || oldX<table.length/2 && it.p.x>=table.length/2)
                    events+=BilliardEvent(EventKind.CROSS_LINE,it.id,if(it.p.x<oldX) -1 else 1,0.0,time+elapsed)
                if(oldX>table.headLine && it.p.x<=table.headLine || oldX<table.headLine && it.p.x>=table.headLine)
                    events+=BilliardEvent(EventKind.HEAD_CROSS,it.id,if(it.p.x<oldX) -1 else 1,0.0,time+elapsed)
                for(region in table.regions) if(region.contains(old) && !region.contains(it.p))
                    events+=BilliardEvent(EventKind.REGION_EXIT,it.id,region.id,0.0,time+elapsed)
            }
            time+=elapsed; left-=elapsed
            if(h != null) {
                val a=balls[h.a]
                var strength=0.0
                when(h.kind) {
                    EventKind.BALL -> {
                        val b=balls[h.b]; val n=(b.p-a.p).unit(); val overlap=a.radius+b.radius-(b.p-a.p).length()
                        // Position-only correction does not inject kinetic energy.
                        if(abs(overlap) < .003) { a.p-=n*(overlap/2+1e-9); b.p+=n*(overlap/2+1e-9) }
                        strength=PooltoolPhysics.collide(a,b,table.cloth.restitution)
                        // Slate contact follows the sphere collision, preserving the
                        // exact Pooltool collision model and separating its impulses.
                        if(a.p.z<=a.radius+1e-7 && a.v.z<0) PooltoolPhysics.land(a)
                        if(b.p.z<=b.radius+1e-7 && b.v.z<0) PooltoolPhysics.land(b)
                    }
                    EventKind.CUSHION -> {
                        val endpoint=h.endpoint
                        val normal=if(endpoint==null) h.normal else (endpoint-a.p).planar().unit().let {
                            if(it.length()>0.0) it else h.normal
                        }
                        val separation=if(endpoint==null) -(a.p-table.rails[h.b].a).dot(normal)
                            else (a.p-endpoint).planar().length()
                        // The root uses local velocity but motion includes friction/spin.
                        // Correct any overlap, not just a fixed epsilon, without adding speed.
                        a.p-=normal*(max(0.0,a.radius-separation)+1e-8)
                        strength=PooltoolPhysics.cushion(a,normal,table.cloth)
                    }
                    EventKind.POCKET -> { strength=a.v.length(); a.motion=Motion.POCKETED; a.stop() }
                    EventKind.PIN -> { strength=a.v.length(); pins[h.b].down=true; a.v*=.995; a.classify() }
                    EventKind.LAND -> { strength=abs(a.v.z); PooltoolPhysics.land(a) }
                    else -> Unit
                }
                if(strength > 1e-8 || h.kind == EventKind.POCKET || h.kind == EventKind.LAND)
                    events+=BilliardEvent(h.kind,a.id,if(h.kind == EventKind.BALL) balls[h.b].id else h.b,strength,time,a.p)
                if(elapsed < 1e-9) {
                    // Avoid zero-time loops at simultaneous rack contacts. Keep the
                    // nudge much smaller than the collision-position tolerance.
                    val tiny=min(left,1e-7); balls.forEach { PooltoolPhysics.evolve(it,table.cloth,tiny,trackVisualRotation) }; left-=tiny; time+=tiny
                }
            }
            resolveBoundaryContacts()
        }
    }

    private fun resolveBoundaryContacts() {
        for(b in balls) if(b.motion != Motion.POCKETED) {
            val pocket=if(b.p.z<=b.radius*1.3) table.pockets.firstOrNull { (b.p-it.center).planar().length()<=it.radius } else null
            val outside=b.p.x < -.25 || b.p.x > table.length+.25 || b.p.y < -.25 || b.p.y > table.width+.25 ||
                b.p.z<=b.radius+1e-7 && !table.supportsCenter(b.p)
            if(pocket!=null || outside) {
                // An opening wins over the out-of-table guard. A jump landing
                // on wood is a real out-of-table event, never a hidden rolling ball.
                events+=BilliardEvent(if(pocket!=null) EventKind.POCKET else EventKind.OFF_TABLE,
                    b.id,pocket?.id ?: -1,b.v.length(),time,b.p)
                b.motion=Motion.POCKETED; b.stop()
            }
        }
    }

    private companion object {
        val stationaryMotions=setOf(Motion.STILL,Motion.SPINNING,Motion.POCKETED)
    }
}
