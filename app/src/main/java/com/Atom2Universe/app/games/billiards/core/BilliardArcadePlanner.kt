package com.Atom2Universe.app.games.billiards.core

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/** Local assistance: never moves a ball, changes a call, or invents a trajectory. */
object BilliardArcadePlanner {
    data class Request(val discipline: Discipline,val mode: PlayMode,val cloth: Int,
        val size: BilliardTableSize,val before: SessionSnapshot,val shot: Shot)
    data class Candidate(val shot: Shot,val radius: Double,val score: BilliardArcadeAssessment.Score)

    /** Constructed entirely on the worker. At the tap, selection is one array lookup. */
    class Plan(val original: Shot,candidates: List<Candidate>) {
        private val choices=List(101) { percent ->
            if(percent==0) original else candidates.filter { it.radius<=percent/100.0+1e-9 }
                .maxByOrNull { it.score }?.shot ?: original
        }
        fun select(quality: Double): Shot = choices[
            if(quality.isFinite()) (quality.coerceIn(0.0,1.0)*100).toInt() else 0]
    }

    internal data class Limits(val angle: Double,val speed: Double,val spin: Double=.035)
    internal fun limits(request: Request,target: Int?): Limits {
        val cue=request.before.balls.firstOrNull { it.id==request.before.cueId }
        val ball=request.before.balls.firstOrNull { it.id==target }
        val angle=if(cue!=null && ball!=null) min(.7*PI/180,
            atan2(ball.radius*.35,(ball.p-cue.p).planar().length().coerceAtLeast(ball.radius))) else .7*PI/180
        return Limits(angle,request.shot.speed*.08)
    }
    internal fun variant(original: Shot,limits: Limits,a: Double,p: Double,x: Double=0.0,y: Double=0.0): Shot {
        val side=original.side+x*limits.spin; val top=original.top+y*limits.spin
        val length=hypot(side,top); val scale=if(length>.8) .8/length else 1.0
        return original.copy(angle=original.angle+a*limits.angle,
            speed=(original.speed+p*limits.speed).coerceIn(.05,8.0),side=side*scale,top=top*scale)
    }
    internal fun radius(original: Shot,shot: Shot,limits: Limits): Double = maxOf(
        abs(shot.angle-original.angle)/limits.angle,
        abs(shot.speed-original.speed)/limits.speed.coerceAtLeast(1e-9),
        hypot(shot.side-original.side,shot.top-original.top)/limits.spin)

    /** A missed nominal shot may still have one clear ball within the small aiming corridor. */
    private fun nearbyTarget(request: Request): Int? {
        val cue=request.before.balls.firstOrNull { it.id==request.before.cueId } ?: return null
        val direction=V3(cos(request.shot.angle),sin(request.shot.angle))
        val options=request.before.balls.filter { it.id!=cue.id && it.motion!=Motion.POCKETED }.mapNotNull { ball ->
            val delta=(ball.p-cue.p).planar(); val along=delta.dot(direction)
            val across=abs(delta.cross(direction).z)
            if(along>0 && across<=cue.radius+ball.radius+along*tan(.7*PI/180))
                Triple(ball.id,across,along) else null
        }.sortedWith(compareBy({ it.second },{ it.third }))
        if(options.size>1 && abs(options[0].second-options[1].second)<cue.radius*.15) return null
        return options.firstOrNull()?.first
    }

    /** Finite progressive search, with cancellation between 20 ms of simulated play.
     * Incomplete trials never enter a plan. Rendering quaternions are omitted, physics is identical.
     */
    fun prepare(request: Request,published: (Plan)->Unit,cancelled: ()->Boolean) {
        if(cancelled()) return
        if(request.before.pushOut || request.before.safety) { published(Plan(request.shot,emptyList())); return }
        val started=System.nanoTime(); val deadline=started+2_400_000_000L
        val trial=BilliardSession(request.discipline,request.mode,request.cloth,request.size,trackVisualRotation=false)
        val candidates=mutableListOf<Candidate>()
        val events=mutableListOf<BilliardEvent>()
        fun simulate(shot: Shot,budget: Long): Boolean {
            if(cancelled() || System.nanoTime()>=deadline) return false
            trial.restore(request.before)
            events.clear()
            if(!trial.shoot(shot)) return false
            val trialDeadline=min(deadline,System.nanoTime()+budget)
            var elapsed=0.0
            while(trial.world.moving && elapsed<60 && !cancelled() && System.nanoTime()<trialDeadline) {
                events+=trial.update(.02); elapsed+=.02
            }
            return !cancelled() && !trial.world.moving && !trial.isShotActive
        }
        if(!simulate(request.shot,900_000_000L)) return
        val target=BilliardArcadeAssessment.firstContact(trial,events) ?: nearbyTarget(request)
        val bounds=limits(request,target)
        candidates+=Candidate(request.shot,0.0,BilliardArcadeAssessment.score(request.before,trial,request.mode,0.0,events))
        published(Plan(request.shot,candidates))
        if(target==null) return
        val tested=mutableSetOf(request.shot)
        var lastPublished=System.nanoTime()
        fun evaluate(shot: Shot) {
            val distance=radius(request.shot,shot,bounds)
            if(distance>1.0+1e-8 || hypot(shot.side,shot.top)>.8+1e-9 || shot.speed !in .05..8.0 ||
                !tested.add(shot) || !simulate(shot,300_000_000L)) return
            // Keep the intended first object, including when the original aim was illegal.
            if(BilliardArcadeAssessment.firstContact(trial,events)!=target) return
            val boundedDistance=distance.coerceIn(0.0,1.0)
            candidates+=Candidate(shot,boundedDistance,BilliardArcadeAssessment.score(request.before,trial,request.mode,boundedDistance,events))
            if(System.nanoTime()-lastPublished>=70_000_000L) {
                published(Plan(request.shot,candidates)); lastPublished=System.nanoTime()
            }
        }
        // Small corrections first, then widen. Good timing unlocks nested neighborhoods.
        for(level in listOf(.35,.7,1.0)) {
            for(sign in listOf(-1.0,1.0)) {
                evaluate(variant(request.shot,bounds,sign*level,0.0))
                evaluate(variant(request.shot,bounds,0.0,sign*level))
                evaluate(variant(request.shot,bounds,0.0,0.0,sign*level,0.0))
                evaluate(variant(request.shot,bounds,0.0,0.0,0.0,sign*level))
            }
            for(a in listOf(-level,level)) for(p in listOf(-level,level))
                evaluate(variant(request.shot,bounds,a,p))
            if(cancelled() || System.nanoTime()>=deadline) break
        }
        // Refine the best completed neighborhood, retaining all earlier choices.
        val best=candidates.maxByOrNull { it.score }?.shot ?: request.shot
        for(sign in listOf(-1.0,1.0)) {
            evaluate(best.copy(angle=best.angle+sign*bounds.angle*.15))
            evaluate(best.copy(speed=best.speed+sign*bounds.speed*.2))
            evaluate(best.copy(top=best.top+sign*bounds.spin*.2))
            evaluate(best.copy(side=best.side+sign*bounds.spin*.2))
        }
        if(!cancelled()) published(Plan(request.shot,candidates))
    }
}

/** One low-priority worker, one replaceable pending request, no idle polling or job backlog. */
class BilliardArcadePreparation(private val initializeThread: ()->Unit = {}) : AutoCloseable {
    private data class Work(val revision: Long,val request: BilliardArcadePlanner.Request,
        val published: (BilliardArcadePlanner.Plan)->Unit)
    private val revision=AtomicLong()
    private val lock=Any()
    private var pending: Work?=null
    private var running=false
    private var closed=false
    private val executor=Executors.newSingleThreadExecutor { task ->
        Thread({ initializeThread(); task.run() },"billiard-arcade").apply { priority=Thread.MIN_PRIORITY }
    }
    fun invalidate(): Long = synchronized(lock) { pending=null; revision.incrementAndGet() }
    fun submit(ticket: Long,request: BilliardArcadePlanner.Request,published: (BilliardArcadePlanner.Plan)->Unit) {
        synchronized(lock) {
            if(closed || ticket!=revision.get()) return
            pending=Work(ticket,request,published)
            if(running) return
            running=true
            executor.execute {
                while(true) {
                    val work=synchronized(lock) {
                        val next=pending; pending=null
                        if(next==null) running=false
                        next
                    } ?: return@execute
                    val cancelled={ work.revision!=revision.get() || Thread.currentThread().isInterrupted }
                    // A failed preparation simply leaves the player's original shot available.
                    runCatching { BilliardArcadePlanner.prepare(work.request,{ if(!cancelled()) work.published(it) },cancelled) }
                }
            }
        }
    }
    override fun close() {
        synchronized(lock) { closed=true; pending=null; revision.incrementAndGet(); executor.shutdownNow() }
    }
}
