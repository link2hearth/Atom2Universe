package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SnowPeaksCourseTest {
    private val holes = SnowPeaksCourse.holes
    private fun point(h: ClassicHole, x: Float, z: Float) = GolfPoint(x,h.heightAt(x,z),z)

    private fun shot(h: ClassicHole, from: GolfPoint, target: GolfPoint, green: Boolean): ClassicGame? {
        var best: ClassicGame? = null
        var gap = Float.POSITIVE_INFINITY
        for(club in GolfClub.entries.filter { it != GolfClub.PUTTER }) for(percent in 35..100 step 2) {
            val g = ClassicGame(h).apply {
                restore(from,0); windX=0f; windZ=0f; this.club=club
                aimAngle=atan2(target.x-ball.x,target.z-ball.z); hit(percent/100f)
            }
            var steps=0
            while(g.state in listOf(GolfState.FLYING,GolfState.ROLLING) && steps++<3000)g.update(1f/60f)
            val d=hypot(g.ball.x-target.x,g.ball.z-target.z)
            val accepts=if(green)g.lie==GolfLie.GREEN || g.state==GolfState.HOLED else g.lie==GolfLie.FAIRWAY && d<20f
            if(g.lastPenalty==0 && g.state in listOf(GolfState.READY,GolfState.HOLED) && accepts && d<gap) {
                best=g; gap=d
                if(d<3f)return g
            }
        }
        return best
    }

    @Test fun everyHoleCanReachItsGreenThroughActualTerraceShots() {
        val failed=mutableListOf<String>()
        for(h in holes) {
            var ball=h.tee
            val targets=if(h.par==3)listOf(h.cup) else h.attackLandings.map { point(h,it.x,it.z) }+h.cup
            for((i,target) in targets.withIndex()) {
                val played=shot(h,ball,target,i==targets.lastIndex)
                if(played==null) { failed+="${h.number}, shot ${i+1}: ${ball.x}/${ball.z} -> ${target.x}/${target.z}"; break }
                ball=played.ball
            }
        }
        assertTrue("Unplayable mountain stages: $failed",failed.isEmpty())
    }

    @Test fun variedMountainProfilesKeepCalmTeeTerracesAndGreens() {
        assertEquals((1..18).toList(),holes.map { it.number })
        assertEquals(72,holes.sumOf { it.par })
        assertEquals(4,holes.count { it.par==3 })
        assertEquals(4,holes.count { it.par==5 })
        assertTrue(holes.filter { it.par==3 }.any { it.cup.y-it.tee.y>8f })
        assertTrue(holes.filter { it.par==3 }.any { it.tee.y-it.cup.y>25f })
        for(h in holes) {
            assertEquals(GolfLandscapeStyle.SNOW_MOUNTAINS,h.landscapeStyle)
            assertEquals(GolfLie.TEE,h.lieAt(0f,0f))
            for(x in -3..3)for(z in -3..3)assertEquals(h.tee.y-ClassicHole.BALL_RADIUS,h.heightAt(x.toFloat(),z.toFloat()),.001f)
            for(ix in -12..12 step 2)for(iz in -12..12 step 2) {
                val x=h.cup.x+ix;val z=h.cup.z+iz
                if(h.lieAt(x,z)!=GolfLie.GREEN)continue
                val gx=(h.heightAt(x+.15f,z)-h.heightAt(x-.15f,z))/.3f
                val gz=(h.heightAt(x,z+.15f)-h.heightAt(x,z-.15f))/.3f
                assertTrue(GolfBallPhysics.canRest(gx,gz,GolfLie.GREEN))
            }
            for(a in h.attackLandings.filter { h.par>3 }) {
                val gx=(h.heightAt(a.x+.15f,a.z)-h.heightAt(a.x-.15f,a.z))/.3f
                val gz=(h.heightAt(a.x,a.z+.15f)-h.heightAt(a.x,a.z-.15f))/.3f
                assertTrue("Terrace slope ${h.number}: $gx/$gz",hypot(gx,gz)<.025f)
            }
            val palette=ClassicPalette(h)
            assertTrue(palette.rough.r>.8f && palette.rough.b>.9f)
            assertTrue(palette.green.g>palette.green.r)
        }
        for(number in listOf(2,13,18)) {
            val h=holes[number-1]
            assertTrue(h.tee.y-h.heightAt(h.finishX,400f)>45f)
            assertEquals(h.elevation,h.heightAt(h.finishX,h.length-45f),.5f)
        }
    }
}
