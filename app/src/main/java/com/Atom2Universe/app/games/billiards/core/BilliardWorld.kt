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

    // Earliest contact of the current step. Plain fields: the search runs for every pair
    // and rail at every step, so it must not allocate a candidate it then rejects.
    private var hitFound = false
    private var hitT = 0.0
    private var hitKind = EventKind.BALL
    private var hitA = 0
    private var hitB = 0
    private var hitNormal = V3.ZERO
    private var hitEndpoint: V3? = null
    private fun accepts(t: Double, dt: Double) = t.isFinite() && t >= -1e-10 && t <= dt && (!hitFound || t < hitT)
    private fun take(t: Double, kind: EventKind, a: Int, b: Int, normal: V3 = V3.ZERO, endpoint: V3? = null) {
        hitFound = true; hitT = max(0.0, t); hitKind = kind; hitA = a; hitB = b; hitNormal = normal; hitEndpoint = endpoint
    }
    private fun offer(t: Double, dt: Double, kind: EventKind, a: Int, b: Int) { if(accepts(t, dt)) take(t, kind, a, b) }

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
            hitFound=false; hitEndpoint=null; hitNormal=V3.ZERO
            // Components are read once; every expression keeps the V3 operations in the
            // same order (planar vectors with z=0.0), so the roots are bit-for-bit the same.
            for(i in balls.indices) {
                val a=balls[i]; if(a.motion == Motion.POCKETED) continue
                val ap=a.p; val av=a.v
                val px=ap.x; val py=ap.y; val pz=ap.z; val vx=av.x; val vy=av.y; val vz=av.z
                if(a.motion == Motion.AIRBORNE) {
                    val root=(vz+sqrt(vz*vz+2*PooltoolPhysics.G*max(0.0,pz-a.radius)))/PooltoolPhysics.G
                    offer(root,dt,EventKind.LAND,i,0)
                }
                for(j in i+1 until balls.size) {
                    val b=balls[j]; if(b.motion == Motion.POCKETED) continue
                    if((a.motion==Motion.STILL || a.motion==Motion.SPINNING) &&
                        (b.motion==Motion.STILL || b.motion==Motion.SPINNING)) continue
                    val bp=b.p; val bv=b.v
                    offer(impactTime(bp.x-px,bp.y-py,bp.z-pz,bv.x-vx,bv.y-vy,bv.z-vz,a.radius+b.radius),dt,EventKind.BALL,i,j)
                }
                // Boundary contacts were already resolved. A stationary ball
                // still participates in ball collisions above, but has no swept
                // pocket/rail/pin path to search during a headless trial.
                if(!trackVisualRotation && (a.motion==Motion.STILL || a.motion==Motion.SPINNING)) continue
                // A slow ball can stop on the lip during this interval. Capture
                // is positional as well as swept, even when its speed is zero.
                if(pz <= a.radius*1.3) for(k in table.pockets.indices) {
                    val p=table.pockets[k]; val c=p.center
                    val rx=px-c.x; val ry=py-c.y
                    // A sum of squares is never -0.0: the z=0.0 term of length() changes nothing.
                    if(sqrt(rx*rx+ry*ry) <= p.radius) offer(0.0,dt,EventKind.POCKET,i,p.id)
                    else offer(impactTime(rx,ry,0.0,vx,vy,0.0,p.radius),dt,EventKind.POCKET,i,p.id)
                }
                val ballSpeed=sqrt(vx*vx+vy*vy+vz*vz)
                if(ballSpeed < 1e-10) continue
                // Airborne balls can clear the cushion nose.
                if(pz < a.radius*2.2) {
                    // No contact within this step can come from a rail farther than the
                    // radius plus the distance travelled: its box is skipped untouched.
                    val reach=a.radius+ballSpeed*dt+1e-3
                    val rails=table.rails
                    for(j in rails.indices) {
                        val rail=rails[j]; val ra=rail.a; val rb=rail.b
                        if(px < min(ra.x,rb.x)-reach || px > max(ra.x,rb.x)+reach ||
                            py < min(ra.y,rb.y)-reach || py > max(ra.y,rb.y)+reach) continue
                        val n=rail.normal; val speedN=vx*n.x+vy*n.y+vz*n.z
                        val signed=(px-ra.x)*n.x+(py-ra.y)*n.y+(pz-ra.z)*n.z
                        if(speedN > 1e-8 && signed <= a.radius*.25) {
                            val t=(-a.radius-signed)/speedN
                            val s=max(0.0,t); val tg=rail.tangent
                            val along=(px+vx*s-ra.x)*tg.x+(py+vy*s-ra.y)*tg.y+(pz+vz*s-ra.z)*tg.z
                            if(along >= 0 && along <= rail.length && accepts(s,dt)) take(s,EventKind.CUSHION,i,j,n)
                        }
                        for(endpoint in 0..1) {
                            val end=if(endpoint==0) ra else rb
                            val rx=px-end.x; val ry=py-end.y
                            val t=impactTime(rx,ry,0.0,vx,vy,0.0,a.radius)
                            if(t <= dt && accepts(t,dt)) take(t,EventKind.CUSHION,i,j,-(V3(rx,ry)+av.planar()*max(0.0,t)).unit(),end)
                        }
                    }
                }
                if(pz < a.radius+.025) for(j in pins.indices) {
                    val p=pins[j]; if(p.down) continue
                    offer(impactTime(px-p.p.x,py-p.p.y,0.0,vx,vy,0.0,a.radius+.005),dt,EventKind.PIN,i,j)
                }
            }
            val found=hitFound
            val elapsed=if(found) hitT else dt
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
                val regions=table.regions
                for(r in regions.indices) { val region=regions[r]; if(region.contains(old) && !region.contains(it.p))
                    events+=BilliardEvent(EventKind.REGION_EXIT,it.id,region.id,0.0,time+elapsed) }
            }
            time+=elapsed; left-=elapsed
            if(found) {
                val kind=hitKind; val hb=hitB
                val a=balls[hitA]
                var strength=0.0
                when(kind) {
                    EventKind.BALL -> {
                        val b=balls[hb]; val n=(b.p-a.p).unit(); val overlap=a.radius+b.radius-(b.p-a.p).length()
                        // Position-only correction does not inject kinetic energy.
                        if(abs(overlap) < .003) { a.p-=n*(overlap/2+1e-9); b.p+=n*(overlap/2+1e-9) }
                        strength=PooltoolPhysics.collide(a,b,table.cloth.restitution)
                        // Slate contact follows the sphere collision, preserving the
                        // exact Pooltool collision model and separating its impulses.
                        if(a.p.z<=a.radius+1e-7 && a.v.z<0) PooltoolPhysics.land(a)
                        if(b.p.z<=b.radius+1e-7 && b.v.z<0) PooltoolPhysics.land(b)
                    }
                    EventKind.CUSHION -> {
                        val endpoint=hitEndpoint
                        val normal=if(endpoint==null) hitNormal else (endpoint-a.p).planar().unit().let {
                            if(it.length()>0.0) it else hitNormal
                        }
                        val separation=if(endpoint==null) -(a.p-table.rails[hb].a).dot(normal)
                            else (a.p-endpoint).planar().length()
                        // The root uses local velocity but motion includes friction/spin.
                        // Correct any overlap, not just a fixed epsilon, without adding speed.
                        a.p-=normal*(max(0.0,a.radius-separation)+1e-8)
                        strength=PooltoolPhysics.cushion(a,normal,table.cloth)
                    }
                    EventKind.POCKET -> { strength=a.v.length(); a.motion=Motion.POCKETED; a.stop() }
                    EventKind.PIN -> { strength=a.v.length(); pins[hb].down=true; a.v*=.995; a.classify() }
                    EventKind.LAND -> { strength=abs(a.v.z); PooltoolPhysics.land(a) }
                    else -> Unit
                }
                if(strength > 1e-8 || kind == EventKind.POCKET || kind == EventKind.LAND)
                    events+=BilliardEvent(kind,a.id,if(kind == EventKind.BALL) balls[hb].id else hb,strength,time,a.p)
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
