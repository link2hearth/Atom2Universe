package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*
import kotlin.random.Random

data class Trace(val paths: Map<Int,List<V3>>, val events: List<BilliardEvent>, val startTime: Double = 0.0)

/** How far the computer's stroke strays from the shot it chose: one standard deviation
 * of the direction (radians) and of the strength (fraction of the speed). */
class BilliardSkill(val angle: Double, val speed: Double) {
    /** The stroke actually played: a normal error, bounded so a stroke never goes wild. */
    fun stroke(shot: Shot, random: Random = Random.Default): Shot {
        fun normal()=(sqrt(-2*ln(1-random.nextDouble()))*cos(2*PI*random.nextDouble())).coerceIn(-2.5,2.5)
        return shot.copy(angle=shot.angle+normal()*angle,speed=(shot.speed*(1+normal()*speed)).coerceAtLeast(.05))
    }
    companion object {
        fun of(difficulty: Int)=when(difficulty) {
            0 -> BilliardSkill(Math.toRadians(.45),.08)
            1 -> BilliardSkill(Math.toRadians(.22),.05)
            else -> BilliardSkill(Math.toRadians(.08),.03)
        }
    }
}

object BilliardPlanner {
    /** Fixed draws: the same position always gets the same choice, and tests can repeat it. */
    private const val STROKE_SEED=7
    /** Trial values, against 25 per point scored. Bounded: in the average over strays,
     * one lucky stroke that wins the rack must not outweigh several clean pots. */
    private const val WIN=300.0
    /** Keeping the table: handing it over gives the opponent the next pot. */
    private const val TABLE=20.0
    /** A foul loses the table and hands the opponent the cue ball: about half a pot more
     * than a clean miss, on top of the penalty points the referee already counts. */
    private const val FOUL=25.0
    /** In the average over strays, winning counts as one more pot: luck is never a plan,
     * and a sure win (every stray wins) still beats a pot. Points scored still count. */
    private const val STRAY_WIN=40.0
    /** A bounded aiming guide, not a prediction of the entire result of the shot.
     * Keeps the play solver (including spin and cushion response), but follows only
     * the cue and its first object ball. All budgets also stop the worker's work.
     */
    fun preview(world: BilliardWorld, cueId: Int, shot: Shot,
                cancelled: () -> Boolean = { false }): Trace {
        if(cancelled()) return Trace(emptyMap(),emptyList(),world.time)
        val w=world.copyDeep()
        val cue=w.balls.firstOrNull { it.id==cueId && it.motion!=Motion.POCKETED }
            ?: return Trace(emptyMap(),emptyList(),world.time)
        PooltoolPhysics.strike(cue,CueReach.lift(w,cue,shot))
        val balls=w.balls.associateBy { it.id }
        val paths=linkedMapOf(cueId to mutableListOf(cue.p))
        val cursors=mutableMapOf(cueId to cue.p)
        val remaining=mutableMapOf(cueId to 3.0)
        val events=mutableListOf<BilliardEvent>()
        var contacted=false
        val rebounded=mutableSetOf<Int>()
        val afterCushion=1.2
        fun visible(id: Int)=(remaining[id] ?: 0.0)>1e-8

        // Count every solver segment, even when a point is too close to draw.
        // Interpolate the final point so the visible guide never exceeds its budget.
        fun append(id: Int, point: V3, force: Boolean = false): Boolean {
            val previous=cursors[id] ?: return false
            val budget=remaining[id] ?: return false
            if(budget<=0.0) return false
            val delta=point-previous; val distance=delta.length()
            val used=min(distance,budget)
            val end=if(distance>budget) previous+delta*(budget/distance) else point
            remaining[id]=(budget-used).coerceAtLeast(0.0); cursors[id]=end
            val path=paths.getValue(id)
            if((end-path.last()).length()>1e-8 &&
                (force || remaining.getValue(id)<=1e-8 || (end-path.last()).length()>=.006)) path+=end
            return distance<=budget+1e-8
        }

        // A slow rolling/spinning ball must not keep an aiming worker alive for
        // the duration of a full shot. Cancellation is checked every 2 ms of play.
        while(w.time-world.time<6.0 && !cancelled()) {
            // Expired branches remain in the copied physics so the other path
            // cannot pass through them. Only visible branches keep the worker alive.
            val active=paths.keys.filter { id ->
                remaining.getValue(id)>1e-8 && balls.getValue(id).let {
                    it.motion!=Motion.POCKETED && (it.v.length()>1e-8 || it.motion==Motion.AIRBORNE)
                }
            }
            if(active.isEmpty()) break
            val cueBefore=cue.p
            val start=w.events.size
            w.advance(.002)
            for(index in start until w.events.size) {
                val event=w.events[index]
                when(event.kind) {
                    EventKind.BALL -> {
                        if(!visible(event.ball) && !visible(event.other)) continue
                        if(contacted || event.ball!=cueId && event.other!=cueId) {
                            // Only the branches involved in this next contact end.
                            // The other visible ball may still have a useful exit to show.
                            val a=balls.getValue(event.ball); val b=balls.getValue(event.other)
                            val impact=event.position ?: a.p
                            if(visible(a.id)) { append(a.id,impact,true); remaining[a.id]=0.0 }
                            if(visible(b.id)) {
                                append(b.id,impact+(b.p-a.p).unit()*(a.radius+b.radius),true)
                                remaining[b.id]=0.0
                            }
                            continue
                        }
                        val otherId=if(event.ball==cueId) event.other else event.ball
                        val other=balls.getValue(otherId)
                        val impact=event.position ?: cueBefore
                        val cueImpact=if(event.ball==cueId) impact else
                            impact+(cueBefore-impact).unit()*(cue.radius+other.radius)
                        if(!append(cueId,cueImpact,true)) continue
                        remaining[cueId]=min(remaining.getValue(cueId),.9)
                        // Before this contact the object ball was stationary.
                        val objectStart=world.balls.first { it.id==otherId }.p
                        paths[otherId]=mutableListOf(objectStart)
                        cursors[otherId]=objectStart; remaining[otherId]=.9
                        contacted=true; events+=event
                    }
                    EventKind.CUSHION -> {
                        if(!visible(event.ball)) continue
                        if(event.position?.let { append(event.ball,it,true) }==false) continue
                        if(!rebounded.add(event.ball)) { remaining[event.ball]=0.0; continue }
                        // Reserve a readable exit even if the approach used nearly
                        // all 3 m (or the object's 90 cm). At most one extension per ball.
                        remaining[event.ball]=max(remaining.getValue(event.ball),afterCushion)
                        events+=event
                    }
                    EventKind.POCKET,EventKind.OFF_TABLE -> {
                        if(!visible(event.ball)) continue
                        event.position?.let { append(event.ball,it,true) }
                        remaining[event.ball]=0.0; events+=event
                    }
                    EventKind.PIN -> {
                        if(!visible(event.ball)) continue
                        event.position?.let { append(event.ball,it,true) }
                        remaining[event.ball]=0.0; events+=event
                    }
                    else -> Unit
                }
            }
            for(id in paths.keys) {
                val ball=balls.getValue(id)
                append(id,ball.p,ball.motion==Motion.STILL || ball.motion==Motion.SPINNING)
            }
        }
        for((id,path) in paths) {
            val end=cursors.getValue(id)
            if((end-path.last()).length()>1e-8) path+=end
        }
        return Trace(paths,events,world.time)
    }

    /** Same equations and collision scheduler as play; caller runs on a worker. */
    fun trace(world: BilliardWorld, cueId: Int, shot: Shot, seconds: Double = 12.0,
              firstContactOnly: Boolean = false, cancelled: () -> Boolean = { false }): Trace {
        val w=world.copyDeep(); val cue=w.balls.firstOrNull { it.id==cueId && it.motion!=Motion.POCKETED } ?: return Trace(emptyMap(),emptyList())
        PooltoolPhysics.strike(cue,CueReach.lift(w,cue,shot))
        val paths=w.balls.associate { it.id to mutableListOf(it.p) }
        var elapsed=0.0
        while(w.moving && elapsed<seconds && !cancelled()) {
            w.advance(.04); elapsed+=.04
            w.balls.filter { it.motion!=Motion.POCKETED }.forEach { b ->
                val path=paths.getValue(b.id)
                if((path.last()-b.p).length()>.003) path+=b.p
            }
            if(firstContactOnly && w.events.any { it.kind==EventKind.BALL || it.kind==EventKind.CUSHION }) break
        }
        return Trace(paths,w.events.toList(),world.time)
    }

    data class PlannedShot(val shot: Shot, val ball: Int, val pocket: Int,
                           val position: V3? = null, val rails: List<Int> = emptyList())
    private data class Candidate(val angle: Double,val ball: Int,val pocket: Int,
                                 val position: V3?,val rails: List<Int> = emptyList(),
                                 val ghost: V3? = null,val objectDistance: Double? = null,
                                 val side: Double = 0.0,val top: Double = 0.0,val power: Double? = null)
    /** One trial: its refereed score, the pool object ball's angular miss of the opening,
     * and at carom how close the cue ball passed to its second ball (0 when it touched). */
    private class Outcome(val score: Double,val miss: Double?,val near: Double)

    /** Evaluate the referee as well as the trajectory: an early eight or an
     * opponent pocket must never be rewarded by a generic pot-count heuristic. */
    fun choose(session: BilliardSession, difficulty: Int, cueMass: Double = .567, endMass: Double = .012,
               skill: BilliardSkill = BilliardSkill.of(difficulty),
               onCandidate: (PlannedShot) -> Unit = {},
               cancelled: () -> Boolean): PlannedShot? {
        val started=System.nanoTime()
        if(cancelled()) return null
        val cue=session.cue ?: return null
        val targets=session.legalTargets(); if(targets.isEmpty()) return null
        val table=session.table; val snapshot=session.snapshot(); val player=session.match.player
        // Carom scores on contacts, not pockets: the cue ball must reach both object balls,
        // after the cushions this discipline requires. Pins games aim at the opponent's ball.
        val pins=session.discipline==Discipline.FIVE_PINS || session.discipline==Discipline.NINE_PINS
        val carom=table.pockets.isEmpty() && !pins
        val requiredRails=when(session.discipline) { Discipline.ONE_CUSHION -> 1; Discipline.THREE_CUSHION -> 3
            Discipline.ARTISTIC -> session.drill%4; else -> 0 }
        // Every level thinks the same, counted in full simulations, never in milliseconds:
        // only its stroke error differs. The clock only guards a very slow device.
        val checked=5; val strokes=10
        val budget=(if(table.pockets.isEmpty()) 340 else 150)+checked*strokes
        var allowed=budget-checked*strokes
        var trials=0
        fun late()=System.nanoTime()-started>=4_000_000_000L
        val candidates=mutableListOf<Candidate>()
        fun offer(ghost: V3,ball: Int,pocket: Int,rails: List<Int> = emptyList(),objectDistance: Double? = null) {
            val positions=mutableListOf<V3?>(null)
            if(session.match.ballInHand) {
                val proposed=ghost-(ghost-cue.p).planar().unit()*.3
                if(table.canPlace(proposed,session.world.balls,cue.id) && session.inHandRegion(proposed)) positions+=proposed
                val head=table.headSpot.copy(x=table.headLine-.12)
                if(table.canPlace(head,session.world.balls,cue.id) && session.inHandRegion(head)) positions+=head
            }
            for(position in positions) {
                val from=position ?: cue.p
                candidates+=Candidate(atan2(ghost.y-from.y,ghost.x-from.x),ball,pocket,position,rails,ghost,objectDistance)
            }
        }
        if(session.match.breaking && table.pockets.isNotEmpty() && table.family!=TableFamily.SNOOKER) {
            val apex=targets.minBy { it.p.x }
            offer(apex.p,apex.id,0)
        } else for(b in targets) {
            if(table.pockets.isEmpty()) {
                // Sweep the whole ball, from a full hit to the thinnest edge. From 1 m that is
                // only ±3.5 degrees: fixed angle offsets around the centre miss the ball.
                val rel=(b.p-cue.p).planar(); val distance=rel.length()
                val reach=min(1.0,(b.radius+cue.radius)/max(distance,1e-6))
                val aim=atan2(rel.y,rel.x)
                val speeds=when { pins -> listOf(1.5,2.5); requiredRails>=3 -> listOf(2.4,3.4)
                    requiredRails>0 -> listOf(1.8,2.8); else -> listOf(1.3,2.2) }
                val spins=if(pins) listOf(0.0 to 0.0,0.0 to .45,0.0 to -.45)
                    else listOf(0.0 to 0.0,-.5 to 0.0,.5 to 0.0,0.0 to .45,0.0 to -.45)
                for(fraction in (-5..5).map { it*.18 }) for((side,top) in spins) for(speed in speeds)
                    candidates+=Candidate(aim+asin(fraction*reach),b.id,-1,null,side=side,top=top,power=speed)
            } else for(p in table.pockets) {
                if(session.discipline==Discipline.ONE_POCKET && p.id!=session.pocketFor(player)) continue
                val opening=table.opening(p)
                if(session.discipline==Discipline.BANK) {
                    for((index,rail) in table.rails.take(6).withIndex()) {
                        val mirror=opening-rail.normal*((opening-rail.a).dot(rail.normal)*2)
                        val direction=(mirror-b.p).planar().unit()
                        val denom=direction.dot(rail.normal)
                        if(denom<=1e-6) continue
                        val distance=(-(b.radius)-(b.p-rail.a).dot(rail.normal))/denom
                        val impact=b.p+direction*distance
                        val along=(impact-rail.a).dot(rail.tangent)
                        if(distance>0 && along in b.radius..(rail.length-b.radius)) offer(b.p-direction*(b.radius+cue.radius),b.id,p.id,listOf(index),distance+(p.center-impact).planar().length())
                    }
                } else offer(b.p-(opening-b.p).planar().unit()*(b.radius+cue.radius),b.id,p.id)
            }
        }
        // Search clear, easy cuts first: a time-limited search must not spend its
        // whole budget on the first ball/pocket in rack order.
        fun blocked(from: V3,to: V3,targetId: Int): Boolean {
            val segment=(to-from).planar(); val lengthSquared=segment.dot(segment)
            if(lengthSquared<1e-12) return false
            return session.world.balls.any { b ->
                if(b.id==cue.id || b.id==targetId || b.motion==Motion.POCKETED) false
                else {
                    val t=(b.p-from).planar().dot(segment)/lengthSquared
                    t in 0.0..1.0 && (b.p-(from+segment*t)).planar().length()<b.radius+cue.radius
                }
            }
        }
        fun priority(candidate: Candidate): Double {
            val ball=targets.first { it.id==candidate.ball }
            val from=candidate.position ?: cue.p
            val direction=V3(cos(candidate.angle),sin(candidate.angle))
            val distance=(ball.p-from).planar().dot(direction)
            val approach=candidate.ghost ?: (from+direction*max(0.0,distance-cue.radius-ball.radius))
            var score=if(blocked(from,approach,ball.id)) -10.0 else 0.0
            score-=(ball.p-from).planar().length()*.1
            if(candidate.rails.isEmpty()) table.pockets.firstOrNull { it.id==candidate.pocket }?.let { pocket ->
                val opening=table.opening(pocket)
                score+=direction.dot((opening-ball.p).planar().unit())
                if(blocked(ball.p,opening,ball.id)) score-=10.0
            }
            return score
        }
        val limit=if(table.pockets.isEmpty()) candidates.size else 56
        // Cache geometric scores once, rather than recomputing blockers on every
        // comparison in the sort. Geometry only orders trials; the referee scores them.
        val sampled=candidates.map { it to priority(it) }.sortedByDescending { it.second }.take(limit).map { it.first }
        val targetById=targets.associateBy { it.id }
        val breaking=session.match.breaking && table.family in setOf(TableFamily.POOL,TableFamily.BLACKBALL,TableFamily.PYRAMID,TableFamily.HEYBALL)
        fun initialPower(candidate: Candidate): Double {
            candidate.power?.let { return it }
            if(breaking) return 5.6
            val pocket=table.pockets.firstOrNull { it.id==candidate.pocket } ?: return 1.7
            val ball=targetById.getValue(candidate.ball)
            val ghost=candidate.ghost ?: return 1.7
            val from=candidate.position ?: cue.p
            val incoming=(ghost-from).planar().unit()
            val outgoing=(ball.p-ghost).planar().unit()
            val transfer=(1+table.cloth.restitution)*.5*incoming.dot(outgoing).coerceAtLeast(.15)
            // Aim through the opening, but roll on to the drop behind it.
            val objectDistance=candidate.objectDistance ?: (pocket.center-ball.p).planar().length()
            val sliding=table.cloth.sliding*PooltoolPhysics.G
            val rolling=table.cloth.rolling*PooltoolPhysics.G
            // A centre hit slides, then rolls at 5/7 of its launch speed. Use
            // those same friction equations to seed the search, allowing a small
            // arrival speed at the pocket. Full simulations correct this estimate.
            val stopDistancePerSpeedSquared=12/(49*sliding)+25/(98*rolling)
            var impactSpeed=sqrt((objectDistance+.10)/stopDistancePerSpeedSquared)/transfer
            if(candidate.rails.isNotEmpty()) impactSpeed/=table.cloth.cushionRestitution
            val cueDistance=(ghost-from).planar().length()
            val slidingLaunchSquared=impactSpeed*impactSpeed+2*sliding*cueDistance
            val launchSpeed=if(cueDistance<=12*slidingLaunchSquared/(49*sliding)) sqrt(slidingLaunchSquared)
                else sqrt((impactSpeed*impactSpeed+2*rolling*cueDistance)/(25.0/49+24*rolling/(49*sliding)))
            return (launchSpeed*(1+cue.mass/cueMass)*.5).coerceIn(.35,4.0)
        }
        var lastPreview=0L
        val trial=BilliardSession(session.discipline,session.mode,session.clothSpeed,session.table.size,trackVisualRotation=false)
        class Tried(val candidate: Candidate,val planned: PlannedShot,val score: Double,val near: Double)
        val tried=mutableListOf<Tried>()
        val evaluated=mutableListOf<Pair<Candidate,Double>>()
        // Collision throw sends the object ball up to ~2 degrees off the ghost-ball
        // line: 30 mm at 1 m, while a pyramid corner only accepts about 5. Each
        // direct pot measures where its object ball really went, then corrects.
        val aimCorrection=HashMap<Candidate,Double>()
        /** Object-ball departure angle of the ghost-ball model, without throw. */
        fun departure(from: V3,angle: Double,target: V3,contact: Double): Double? {
            val d=V3(cos(angle),sin(angle))
            val t=impactTime((from-target).planar(),d,contact)
            if(!t.isFinite()) return null
            val n=(target-(from+d*t)).planar()
            return atan2(n.y,n.x)
        }
        /** Cue rotation that turns the object ball back by [miss] radians. */
        fun turnFor(candidate: Candidate,angle: Double,miss: Double): Double? {
            val ball=targetById.getValue(candidate.ball)
            val from=candidate.position ?: cue.p
            val contact=ball.radius+cue.radius; val step=1e-4
            val a=departure(from,angle-step,ball.p,contact) ?: return null
            val b=departure(from,angle+step,ball.p,contact) ?: return null
            var slope=b-a
            if(slope>PI) slope-=2*PI else if(slope< -PI) slope+=2*PI
            slope/=2*step
            return if(abs(slope)<1e-3) null else (-miss/slope).coerceIn(-.01,.01)
        }
        /** One full refereed trial. Only [kept] trials are candidates; the others measure
         * a stroke's error. */
        fun attempt(candidate: Candidate,angle: Double,power: Double,kept: Boolean = true): Outcome? {
                if(cancelled() || tried.isNotEmpty() && (trials>=allowed || late())) return null
                trials++
                val shot=Shot(angle,power,candidate.side,candidate.top,cueMass=cueMass,endMass=endMass)
                // Reuse the table and referee, resetting all match state per trial.
                trial.restore(snapshot)
                if(candidate.position!=null && !trial.moveBall(cue.id,candidate.position)) return null
                trial.nominated=candidate.ball; trial.calledPocket=candidate.pocket; trial.bankRails=candidate.rails
                if(!trial.shoot(shot)) return null
                val planned=PlannedShot(shot,candidate.ball,candidate.pocket,candidate.position,candidate.rails)
                // Show actual candidates without flooding the main thread during fast searches.
                val now=System.nanoTime()
                if(lastPreview==0L || now-lastPreview>=250_000_000L) {
                    lastPreview=now
                    onCandidate(planned)
                }
                var elapsed=0.0
                val target=targetById.getValue(candidate.ball)
                val pocket=table.pockets.firstOrNull { it.id==candidate.pocket }
                val aim=if(pocket!=null && candidate.rails.isEmpty() && !breaking) (table.opening(pocket)-target.p).planar() else null
                var miss: Double?=null
                var tracking=aim!=null; var contacted=false
                fun measure(p: V3) {
                    val rel=(p-target.p).planar()
                    miss=atan2(aim!!.x*rel.y-aim.y*rel.x,aim.dot(rel)); tracking=false
                }
                // Carom: after its first object ball and the required cushions, how close
                // the cue ball passes to the other one. A miss short of cushions counts
                // half a metre per cushion missing.
                val cueBall=trial.cue
                var firstObject=-1; var touchedBoth=false; var rails=0
                var nearest=Double.POSITIVE_INFINITY; var previous=cueBall?.p
                // Always finish the first usable trial. Afterwards the clock guard
                // can interrupt a trial, but only a fully refereed choice is returned.
                while(trial.world.moving && elapsed<60 && !cancelled() && (!late() || tried.isEmpty())) {
                    val frame=trial.update(.02); elapsed+=.02
                    if(carom && cueBall!=null && !touchedBoth) {
                        for(e in frame) if(e.kind==EventKind.CUSHION && e.ball==cue.id) rails++
                        else if(e.kind==EventKind.BALL && (e.ball==cue.id || e.other==cue.id)) {
                            val other=if(e.ball==cue.id) e.other else e.ball
                            if(firstObject<0) firstObject=other else if(other!=firstObject) touchedBoth=true
                        }
                        val second=trial.world.balls.firstOrNull { it.id!=cue.id && it.id!=firstObject && firstObject>=0 }
                        val from=previous
                        if(second!=null && from!=null) {
                            val path=(cueBall.p-from).planar(); val length=path.dot(path)
                            val t=if(length<1e-12) 0.0 else ((second.p-from).planar().dot(path)/length).coerceIn(0.0,1.0)
                            val gap=(second.p-(from+path*t)).planar().length()-cueBall.radius-second.radius
                            nearest=min(nearest,max(0.0,gap)+.5*max(0,requiredRails-rails))
                        }
                        previous=cueBall.p
                    }
                    // Follow the object ball from the cue's first contact to the
                    // opening, a jaw or the drop. Any other contact voids the measure.
                    for(e in frame) {
                        if(!tracking) break
                        val touches=e.ball==candidate.ball || e.kind==EventKind.BALL && e.other==candidate.ball
                        if(e.kind==EventKind.BALL && !contacted) {
                            if(setOf(e.ball,e.other)==setOf(cue.id,candidate.ball)) contacted=true else tracking=false
                        } else if(touches && contacted) when(e.kind) {
                            EventKind.BALL -> tracking=false
                            EventKind.POCKET -> if(e.other==candidate.pocket) tracking=false else e.position?.let(::measure)
                            EventKind.CUSHION,EventKind.OFF_TABLE -> e.position?.let(::measure)
                            else -> Unit
                        }
                    }
                    if(tracking && contacted) trial.world.balls.firstOrNull { it.id==candidate.ball }?.let { b ->
                        if((b.p-target.p).planar().dot(aim!!)>=aim.dot(aim)) measure(b.p)
                    }
                }
                if(cancelled() || trial.world.moving) return null
                var score=(trial.match.score(player)-session.match.score(player))*25.0-
                    (trial.match.score(1-player)-session.match.score(1-player))*35.0
                if(trial.match.foul!=Foul.NONE) score-=FOUL
                if(trial.match.winner==player) score+=if(kept) WIN else STRAY_WIN
                if(trial.match.winner==1-player) score-=WIN
                if(trial.match.player==player) score+=TABLE
                if(trial.match.decision!=ShotDecision.NONE) score-=4
                // Position matters after a legal shot: prefer a clear next contact
                // for a continuing turn, or fewer easy contacts for the opponent.
                if(trial.match.foul==Foul.NONE && trial.match.winner<0 && trial.match.decision==ShotDecision.NONE) {
                    val nextCue=trial.cue
                    if(nextCue!=null) {
                        val nextBalls=trial.world.balls
                        val nextQuality=trial.legalTargets().maxOfOrNull { b ->
                            val segment=(b.p-nextCue.p).planar(); val distanceSquared=segment.dot(segment)
                            val obstructed=nextBalls.any { other ->
                                if(other.id==nextCue.id || other.id==b.id || other.motion==Motion.POCKETED || distanceSquared<1e-12) false
                                else {
                                    val t=(other.p-nextCue.p).planar().dot(segment)/distanceSquared
                                    t in 0.0..1.0 && (other.p-(nextCue.p+segment*t)).planar().length()<other.radius+nextCue.radius
                                }
                            }
                            if(obstructed) 0.0 else 1/(1+sqrt(distanceSquared))
                        } ?: 0.0
                        score+=nextQuality*if(trial.match.player==player) 4.0 else -4.0
                    }
                }
                // A carom series goes on while the three balls stay together.
                if(carom && trial.match.player==player && trial.match.winner<0) {
                    val spread=trial.world.balls.maxOf { a -> trial.world.balls.maxOf { b -> (a.p-b.p).planar().length() } }
                    score+=6*(1-spread/1.5).coerceAtLeast(0.0)
                }
                // Prefer a controlled leave when scores are tied, while retaining
                // a legal contact as the minimum useful fallback.
                score-=power*.08
                if(touchedBoth) nearest=min(nearest,.5*max(0,requiredRails-rails))
                if(kept) tried+=Tried(candidate,planned,score,nearest)
                return Outcome(score,miss,nearest)
        }
        fun evaluate(candidate: Candidate,power: Double,offset: Double = 0.0): Double? {
            // A measured aim replaces the blind angle offsets below.
            if(offset!=0.0 && candidate in aimCorrection) return null
            var angle=candidate.angle+(aimCorrection[candidate] ?: 0.0)+offset
            var top: Double?=null
            // One trial, then up to two corrected ones while the object ball misses.
            for(round in 0..2) {
                val outcome=attempt(candidate,angle,power) ?: return top
                val score=outcome.score; val miss=outcome.miss
                top=max(top ?: score,score)
                // A miss of 1 mm per metre is enough; a wild one is not throw.
                if(miss==null || abs(miss)<.001 || abs(miss)>.1 || round==2) return top
                angle+=turnFor(candidate,angle,miss) ?: return top
                aimCorrection[candidate]=angle-candidate.angle
            }
            return top
        }
        fun done()=trials>=allowed || late()
        // Breadth first: compare different routes before spending trials tuning one.
        for(candidate in sampled) {
            val score=evaluate(candidate,initialPower(candidate))
            if(cancelled()) return null
            if(score!=null) evaluated+=candidate to score
            if(done()) break
        }
        if(carom) {
            // A point is rare in the sweep. Start from the closest passes and turn one knob
            // at a time (aim, strength, side, follow), keeping each change that brings the
            // cue ball closer to its second ball, with finer steps when none does.
            val seeds=tried.filter { it.near.isFinite() }.sortedWith(compareBy<Tried> { it.near }.thenByDescending { it.score }).take(8)
            for(seed in seeds) {
                var candidate=seed.candidate; var angle=seed.planned.shot.angle; var power=seed.planned.shot.speed
                var near=seed.near; var step=1.0
                val ball=targetById.getValue(candidate.ball)
                val turn=.12*asin(min(1.0,(ball.radius+cue.radius)/max((ball.p-cue.p).planar().length(),1e-6)))
                while(near>0.0 && step>.12 && !done()) {
                    val moves=listOf(Triple(candidate,angle+turn*step,power),Triple(candidate,angle-turn*step,power),
                        Triple(candidate,angle,(power*(1+.15*step)).coerceAtMost(5.0)),Triple(candidate,angle,(power*(1-.15*step)).coerceAtLeast(.4)),
                        Triple(candidate.copy(side=(candidate.side+.2*step).coerceAtMost(.6)),angle,power),
                        Triple(candidate.copy(side=(candidate.side-.2*step).coerceAtLeast(-.6)),angle,power),
                        Triple(candidate.copy(top=(candidate.top+.2*step).coerceAtMost(.6)),angle,power),
                        Triple(candidate.copy(top=(candidate.top-.2*step).coerceAtLeast(-.6)),angle,power))
                    var moved=false
                    for((c,a,p) in moves) {
                        val outcome=attempt(c,a,p) ?: break
                        if(outcome.near<near) { candidate=c; angle=a; power=p; near=outcome.near; moved=true; break }
                    }
                    if(cancelled()) return null
                    if(!moved) step/=2
                }
                if(done()) break
            }
        } else {
            // Refine the most promising routes, adjusting power. Blind angle offsets
            // remain for routes without a measured throw. Every variant uses the full solver.
            val finalists=evaluated.sortedByDescending { it.second }.take(6)
            val variants=if(breaking) listOf(3.8/5.6 to 0.0)
                else if(table.pockets.isEmpty()) listOf(.8 to 0.0,1.3 to 0.0)
                else listOf(.8 to 0.0,1.2 to 0.0,1.0 to -.006,1.0 to .006)
            refine@ for((factor,offset) in variants) {
                for((candidate,_) in finalists) {
                    if(done()) break@refine
                    evaluate(candidate,(initialPower(candidate)*factor).coerceIn(.05,8.0),offset)
                    if(cancelled()) return null
                }
            }
        }
        // The robot will not strike exactly: replay each route's best stroke as its skill
        // strays, with the same draws for every route. A pot that only drops at the exact
        // angle, or a slam that drops something by luck, loses to a pot that forgives
        // the error, and a weaker robot falls back on easier pots.
        val shortlist=tried.groupBy { it.candidate }.values.map { routes -> routes.maxBy { it.score } }
            .sortedByDescending { it.score }.take(checked)
        allowed=budget
        var choice=shortlist.firstOrNull()?.planned; var best=Double.NEGATIVE_INFINITY
        for(route in shortlist) {
            val draws=Random(STROKE_SEED)
            var total=0.0; var measured=0
            repeat(strokes) {
                val stroke=skill.stroke(route.planned.shot,draws)
                val outcome=attempt(route.candidate,stroke.angle,stroke.speed,kept=false) ?: return@repeat
                total+=outcome.score; measured++
            }
            if(cancelled()) return null
            val expected=total/strokes
            if(measured==strokes && expected>best) { best=expected; choice=route.planned }
        }
        return choice
    }
}
