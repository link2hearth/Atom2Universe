package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Test
import java.io.File
import kotlin.math.*

/** Requested course balancing only; excluded by default. No aiming helper exists in production. */
class GardensScoringCalibrationTest {
    private data class Shot(val club: GolfClub, val aim: Float, val power: Float, val spin: Float = 0f)
    private data class Result(val shot: Shot, val game: ClassicGame, val error: Float)

    private fun play(h: ClassicHole, start: GolfPoint, strokes: Int, shot: Shot): ClassicGame =
        ClassicGame(h).apply {
            restore(start, strokes)
            club=shot.club; aimAngle=shot.aim; setSpin(0f,shot.spin)
            check(hit(shot.power))
            var steps=0
            while (state in listOf(GolfState.FLYING,GolfState.ROLLING) && steps++ < 2400) update(1f/60f)
        }

    private fun find(h: ClassicHole, start: GolfPoint, strokes: Int, target: GolfPoint,
        clubs: List<GolfClub>, ace: Boolean = false): Result {
        val distance=hypot(target.x-start.x,target.z-start.z)
        val heading=atan2(target.x-start.x,target.z-start.z)
        var best: Result? = null
        fun sample(shot: Shot): Result {
            val g=play(h,start,strokes,shot)
            val error=if(g.lastPenalty>0 || g.state !in listOf(GolfState.READY,GolfState.HOLED)) 10000f
                else hypot(g.ball.x-target.x,g.ball.z-target.z)
            return Result(shot,g,error).also { if(best==null || it.error<best!!.error)best=it }
        }
        for (club in clubs.sortedBy { abs(it.carry-distance) }) for (spin in if(ace) listOf(0f,-.6f,.6f) else listOf(0f)) {
            for (factor in listOf(.90f,.75f,1.0f)) {
                var shot=Shot(club,heading,(distance/club.carry*factor).coerceIn(.08f,1f),spin)
                repeat(18) {
                    val r=sample(shot)
                    if(r.game.state==GolfState.HOLED || (!ace && r.error<.25f)) return r
                    val da=.0015f; val dp=if(shot.power>.99f) -.004f else .004f
                    val a=sample(shot.copy(aim=shot.aim+da))
                    val p=sample(shot.copy(power=shot.power+dp))
                    val ax=(a.game.ball.x-r.game.ball.x)/da; val az=(a.game.ball.z-r.game.ball.z)/da
                    val px=(p.game.ball.x-r.game.ball.x)/dp; val pz=(p.game.ball.z-r.game.ball.z)/dp
                    val ex=target.x-r.game.ball.x; val ez=target.z-r.game.ball.z
                    val det=ax*pz-az*px
                    if(abs(det)>.0001f && r.error<9999f) {
                        val aimChange=((ex*pz-ez*px)/det).coerceIn(-.08f,.08f)
                        val powerChange=((ax*ez-az*ex)/det).coerceIn(-.12f,.12f)
                        val candidates=listOf(1f,.5f,.2f).map { scale ->
                            sample(shot.copy(aim=shot.aim+aimChange*scale,
                                power=(shot.power+powerChange*scale).coerceIn(.02f,1f)))
                        }
                        shot=candidates.minBy { it.error }.shot
                    } else shot=shot.copy(aim=shot.aim+.01f,power=(shot.power*.98f).coerceAtLeast(.02f))
                }
            }
        }
        return checkNotNull(best)
    }

    @Test fun scoringRoutes() {
        val directory=File("build/reports/gardens-scoring").apply{mkdirs()}
        val report=File(directory,"routes.csv").apply{writeText("hole,route,stroke,club,aim,power,spin,state,distance,lie\n")}
        fun record(h: ClassicHole, route: String, r: Result) {
            val s=r.shot; val g=r.game
            report.appendText("${h.number},$route,${g.strokes},${s.club},${s.aim},${s.power},${s.spin},${g.state},${g.distanceToCup},${g.lie}\n")
        }
        val bag=GolfClub.entries.filter { it !in listOf(GolfClub.PUTTER,GolfClub.DRIVER) }
        for(h in ClassicCourse.holes) {
            var g=ClassicGame(h)
            // Reach the green in regulation (one under regulation on par 5s and the short 13th).
            if(h.par>3 && h.number!=13) {
                val z=if(h.par==5) 225f else h.landingZ
                val target=GolfPoint(h.fairwayCenter(z),0f,z)
                val r=find(h,g.ball,0,target,listOf(GolfClub.DRIVER,GolfClub.WOOD3,GolfClub.WOOD5))
                record(h,"score",r);g=r.game
            }
            val target=GolfPoint(h.cup.x,h.cup.y,h.cup.z-2f)
            val clubs=if(h.number==13)listOf(GolfClub.DRIVER,GolfClub.WOOD3) else bag
            val r=find(h,g.ball,g.strokes,target,clubs)
            record(h,"score",r);g=r.game
            if(g.state!=GolfState.HOLED && g.lie==GolfLie.GREEN) {
                val putt=find(h,g.ball,g.strokes,h.cup,listOf(GolfClub.PUTTER),ace=true)
                record(h,"score",putt)
            }
            if(h.par==3) {
                val ace=find(h,h.tee,0,h.cup,bag.filter { it.ordinal>=GolfClub.IRON5.ordinal },ace=true)
                record(h,"ace",ace)
            }
        }
    }
}
