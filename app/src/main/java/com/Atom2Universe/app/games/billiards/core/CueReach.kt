package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*

/**
 * The cue is a solid stick: it cannot lie level, since the rail and any ball behind the
 * cue ball stand in its way. Play, robot and drawing all raise a shot by the same rule.
 */
object CueReach {
    /** Drawn cue: tip and butt radii, and its length. */
    const val TIP=.0057
    const val BUTT=.015
    const val LENGTH=1.35
    /** A cue that grazes the wood still touches it. */
    private const val CLEARANCE=.003
    private const val PIN_HEIGHT=.025
    private const val PIN_RADIUS=.005

    /** Tip offsets as struck: Pooltool keeps them within 0.8 radius. */
    private fun offsets(shot: Shot): Pair<Double,Double> {
        val norm=hypot(shot.side,shot.top); val scale=if(norm>.8) .8/norm else 1.0
        return shot.side*scale to shot.top*scale
    }

    /** From the tip towards the butt. */
    fun direction(shot: Shot): V3 {
        val t=Math.toRadians(shot.elevation)
        return V3(-cos(shot.angle)*cos(t),-sin(shot.angle)*cos(t),sin(t))
    }

    /** Where the tip meets the ball (Pooltool's contact point), drawn back by [gap]. */
    fun tip(ball: Ball,shot: Shot,gap: Double=0.0): V3 {
        val (side,top)=offsets(shot)
        val t=Math.toRadians(shot.elevation)
        val dir=direction(shot)
        val up=V3(cos(shot.angle)*sin(t),sin(shot.angle)*sin(t),cos(t))
        val across=V3(-sin(shot.angle),cos(shot.angle))
        return ball.p+(across*side+up*top+dir*sqrt(1-side*side-top*top))*ball.radius+dir*gap
    }

    /** Lowest whole degree at which the cue clears the rail and every ball or pin behind the cue ball. */
    fun minimum(world: BilliardWorld,cue: Ball,shot: Shot): Int {
        val table=world.table
        val back=V3(-cos(shot.angle),-sin(shot.angle))
        val across=V3(-sin(shot.angle),cos(shot.angle))
        val (side,top)=offsets(shot)
        val r=cue.radius
        val origin=cue.p+across*(side*r)
        // Cushion rubber then rail cap, as BilliardTableMesh draws them: how far back along
        // the cue each one starts, and its height above the slate.
        val rail=listOf(0.0 to table.noseHeight,.009 to table.noseHeight+.006,.035 to table.railTop).map { (offset,height) ->
            fun exit(position: Double,heading: Double,size: Double)=when {
                heading< -1e-9 -> (position+offset)/-heading
                heading>1e-9 -> (size-position+offset)/heading
                else -> Double.POSITIVE_INFINITY
            }
            min(exit(origin.x,back.x,table.length),exit(origin.y,back.y,table.width)) to height
        }
        val balls=world.balls.filter { it.id!=cue.id && it.motion!=Motion.POCKETED }
        val pins=world.pins.filter { !it.down }
        val reach=sqrt(1-side*side-top*top)*r
        fun radius(along: Double)=TIP+(BUTT-TIP)*((along-reach)/LENGTH).coerceIn(0.0,1.0)
        for(degrees in 0 until 80) {
            val t=Math.toRadians(degrees.toDouble()); val c=cos(t); val s=sin(t)
            val dir=V3(back.x*c,back.y*c,s)
            // The cue's axis runs through this point; the tip sits [reach] further along it.
            val axis=origin+V3(-back.x*s,-back.y*s,c)*(top*r)
            // Lowest point of the cue above a spot [distance] behind the ball, along the table.
            fun underside(distance: Double): Double {
                val along=(distance+s*top*r)/c
                return axis.z+along*s-radius(along)/c
            }
            if(rail.any { (distance,height) -> underside(distance)<height+CLEARANCE }) continue
            if(balls.any { b ->
                val q=b.p-axis
                val along=max(q.dot(dir),reach)
                (q-dir*along).length()<b.radius+radius(along)+CLEARANCE
            }) continue
            if(pins.any { p ->
                val q=(p.p-origin).planar()
                val distance=q.dot(back)
                distance>0 && abs(q.dot(across))<PIN_RADIUS+BUTT+CLEARANCE && underside(distance)<PIN_HEIGHT+CLEARANCE
            }) continue
            return degrees
        }
        return 80
    }

    /** The requested elevation, raised to the lowest one the cue can actually take. */
    fun lift(world: BilliardWorld,cue: Ball,shot: Shot): Shot {
        val floor=minimum(world,cue,shot).toDouble()
        return if(shot.elevation>=floor) shot else shot.copy(elevation=floor)
    }
}
