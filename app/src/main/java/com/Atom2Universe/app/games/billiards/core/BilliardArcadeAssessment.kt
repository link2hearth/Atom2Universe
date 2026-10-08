package com.Atom2Universe.app.games.billiards.core

import kotlin.math.sqrt

/** Scores completed, refereed trials without changing the intended shot or match. */
object BilliardArcadeAssessment {
    /** Game outcomes dominate all positioning heuristics. Higher is better. */
    data class Score(
        val outcome: Int,
        val legal: Boolean,
        val netPoints: Int,
        val continues: Boolean,
        val placement: Double,
        val correction: Double
    ) : Comparable<Score> {
        override fun compareTo(other: Score): Int {
            outcome.compareTo(other.outcome).let { if(it!=0) return it }
            legal.compareTo(other.legal).let { if(it!=0) return it }
            netPoints.compareTo(other.netPoints).let { if(it!=0) return it }
            continues.compareTo(other.continues).let { if(it!=0) return it }
            // Tiny geometric gains should not cause gratuitous changes of angle or spin.
            (placement-correction*.02).compareTo(other.placement-other.correction*.02)
                .let { if(it!=0) return it }
            return other.correction.compareTo(correction)
        }
    }

    fun firstContact(trial: BilliardSession,events: List<BilliardEvent> = trial.world.events): Int? =
        firstContact(events,trial.before?.cueId ?: trial.cueId)

    fun score(before: SessionSnapshot,trial: BilliardSession,originalMode: PlayMode,correction: Double,
              events: List<BilliardEvent> = trial.world.events): Score {
        val player=before.match.player
        val practice=originalMode==PlayMode.PRACTICE
        val result=if(practice) practiceResult(before,trial,events) else null
        val legal=result?.legal ?: (trial.match.foul==Foul.NONE)
        val outcome=if(practice) 0 else when(trial.match.winner) {
            player -> 1
            1-player -> -1
            else -> 0
        }
        val net=result?.points ?: ((trial.match.score(player)-before.match.score(player))-
            (trial.match.score(1-player)-before.match.score(1-player)))
        val continues=result?.continues ?: (legal && trial.match.player==player &&
            trial.match.decision==ShotDecision.NONE && trial.match.winner<0)
        val placement=if(legal && outcome==0 && trial.match.decision==ShotDecision.NONE &&
            !trial.match.ballInHand) contactQuality(trial)*(if(practice || trial.match.player==player) 1 else -1)
            else 0.0
        return Score(outcome,legal,net,continues,placement,
            if(correction.isFinite()) correction.coerceIn(0.0,1.0) else 1.0)
    }

    private data class PracticeResult(val legal: Boolean,val points: Int,val continues: Boolean)

    /** Practice has no competitive referee or group assignment. Judge its actual
     * discipline objectives, while keeping free ball selection and optional calls.
     * In particular, a legal in-off is an objective in English billiards/pyramid.
     */
    private fun practiceResult(before: SessionSnapshot,trial: BilliardSession,events: List<BilliardEvent>): PracticeResult {
        val cue=before.cueId
        val hits=events.filter { it.kind==EventKind.BALL && (it.ball==cue || it.other==cue) }
        fun objectId(event: BilliardEvent)=if(event.ball==cue) event.other else event.ball
        val first=hits.firstOrNull()
        val firstId=first?.let(::objectId)
        val pots=events.filter { it.kind==EventKind.POCKET }
        val scratch=pots.any { it.ball==cue }
        var legal=events.none { it.kind==EventKind.OFF_TABLE } && (first!=null || before.pushOut)
        var points=0
        val discipline=trial.discipline
        val pins=discipline==Discipline.FIVE_PINS || discipline==Discipline.NINE_PINS
        when {
            pins -> {
                val pinEvents=events.filter { it.kind==EventKind.PIN }.distinctBy { it.other }
                val own=pinEvents.any { it.ball==cue }
                // Either white/yellow may be chosen freely in practice.
                if(cue in 0..1 && firstId!=1-cue) legal=false
                if(own) legal=false
                val outerDown=pinEvents.any { it.other!=0 } || before.pins.any { it.id!=0 && it.down }
                val nine=discipline==Discipline.NINE_PINS
                points=pinEvents.sumOf {
                    when {
                        it.other==0 && nine -> if(outerDown) 10 else 30
                        it.other==0 -> if(outerDown) 4 else 10
                        nine && it.other in 1..4 -> 8
                        else -> 2
                    }
                }
                val red=events.firstOrNull { it.kind==EventKind.BALL && (it.ball==2 || it.other==2) }
                if(red!=null) points+=if(nine) 6 else if(red.ball==cue || red.other==cue) 4 else 3
                if(nine && first!=null && events.any { it.kind==EventKind.CUSHION && it.ball==cue && it.time<first.time }) points*=2
                if(own) points=-points
            }
            discipline.family==TableFamily.CAROM -> {
                val objects=hits.map(::objectId).distinct()
                val second=hits.firstOrNull { objects.size>=2 && objectId(it)==objects[1] }
                val bands=if(second==null) 0 else events.count {
                    it.kind==EventKind.CUSHION && it.ball==cue && it.time<second.time
                }
                val required=when(discipline) {
                    Discipline.ONE_CUSHION -> 1
                    Discipline.THREE_CUSHION -> 3
                    Discipline.ARTISTIC -> before.drill%4
                    else -> 0
                }
                if(objects.size>=2 && bands>=required && legal) {
                    if(validPracticeRegions(before,trial,events)) points=1 else legal=false
                }
            }
            discipline==Discipline.ENGLISH -> {
                if(hits.map(::objectId).distinct().size>=2) points+=2
                points+=pots.sumOf {
                    if(it.ball==cue) { if(firstId==2) 3 else 2 } else if(it.ball==2) 3 else 2
                }
            }
            discipline.family==TableFamily.PYRAMID -> {
                val dynamicHand=discipline==Discipline.PYRAMID_DYNAMIC && before.match.ballInHand &&
                    !before.match.breaking
                if(dynamicHand && scratch) legal=false
                points=pots.size
                if(first!=null && pots.isEmpty() && !legalPyramidLeave(events,first.time)) legal=false
            }
            else -> {
                if(scratch) legal=false
                val objectPots=pots.filter { it.ball!=cue }
                val called=before.nominated>=0 && before.pocket in trial.table.pockets.indices
                fun madeCall()=objectPots.any { it.ball==before.nominated && it.other==before.pocket }
                points=when(discipline) {
                    Discipline.SNOOKER,Discipline.SIX_RED -> objectPots.sumOf {
                        if(it.ball in 1..15) 1 else (it.ball-14).coerceIn(2,7)
                    }
                    Discipline.ONE_POCKET -> objectPots.count { it.other==trial.pocketFor(before.match.player) }-
                        objectPots.count { it.other==trial.pocketFor(1-before.match.player) }
                    Discipline.BANK -> objectPots.count { pot ->
                        val banks=events.filter { it.kind==EventKind.CUSHION && it.ball==pot.ball &&
                            it.time<pot.time && it.other<6 }.map { it.other }
                        firstId==pot.ball && banks.isNotEmpty() &&
                            (!called || pot.ball==before.nominated && pot.other==before.pocket) &&
                            (before.bankRails.isEmpty() || banks==before.bankRails) &&
                            events.none { event -> event.time<pot.time &&
                                (event.kind==EventKind.BALL && first!=null && event.time>first.time &&
                                    (event.ball==pot.ball || event.other==pot.ball) ||
                                    event.kind==EventKind.CUSHION && event.ball==cue && first!=null && event.time<first.time) }
                    }
                    Discipline.STRAIGHT -> if(!called || madeCall()) objectPots.size else 0
                    Discipline.EIGHT,Discipline.TEN -> if(called) objectPots.count {
                        it.ball==before.nominated && it.other==before.pocket
                    } else objectPots.size
                    else -> objectPots.size
                }
            }
        }
        if(before.safety || before.pushOut) points=0
        // Positive pots cannot outweigh a scratch or a ball leaving the table.
        if(!legal && points>0) points=0
        return PracticeResult(legal,points,legal && points>0 && !pins)
    }

    private fun firstContact(events: List<BilliardEvent>,cue: Int): Int? {
        val event=events.firstOrNull { it.kind==EventKind.BALL && (it.ball==cue || it.other==cue) }
            ?: return null
        return if(event.ball==cue) event.other else event.ball
    }

    private fun validPracticeRegions(before: SessionSnapshot,trial: BilliardSession,events: List<BilliardEvent>): Boolean {
        val objects=trial.world.balls.filter { it.id!=before.cueId }
        val start=before.balls.filter { it.id!=before.cueId }
        if(objects.size!=2 || start.size!=2) return true
        for(anchor in listOf(false,true)) {
            val zones=trial.table.regions.filter { (it.id>=100)==anchor }
            val old=zones.firstOrNull { zone -> start.all { zone.contains(it.p) } } ?: continue
            val next=zones.firstOrNull { zone -> objects.all { zone.contains(it.p) } }
            val left=events.any {
                it.kind==EventKind.REGION_EXIT && it.ball!=before.cueId && it.other==old.id
            }
            val previous=if(anchor) before.match.anchorZone else before.match.cadreZone
            val count=if(anchor) before.match.anchorCount else before.match.cadreCount
            val total=if(!left && old.id==next?.id) (if(previous==old.id) count else 0)+1 else 0
            val limit=if(trial.discipline==Discipline.CADRE_47_1) 1 else 2
            if(total>=limit) return false
        }
        return true
    }

    private fun legalPyramidLeave(events: List<BilliardEvent>,firstTime: Double): Boolean {
        val rails=mutableMapOf<Int,Set<Int>>()
        val crossed=mutableSetOf<Int>()
        for(event in events) {
            if(event.time<firstTime) continue
            when(event.kind) {
                EventKind.CUSHION -> {
                    if(event.ball in crossed || rails[event.ball].orEmpty().any { it!=event.other }) return true
                    rails[event.ball]=rails[event.ball].orEmpty()+event.other
                }
                EventKind.CROSS_LINE -> {
                    if(rails[event.ball].orEmpty().isNotEmpty()) return true
                    crossed+=event.ball
                }
                EventKind.BALL -> {
                    val union=rails[event.ball].orEmpty()+rails[event.other].orEmpty()
                    rails[event.ball]=union; rails[event.other]=union
                    if(event.ball in crossed || event.other in crossed) { crossed+=event.ball; crossed+=event.other }
                }
                else -> Unit
            }
        }
        return false
    }

    /** Small tie-break only: a clear next contact is useful, but is not a pot prediction. */
    private fun contactQuality(trial: BilliardSession): Double {
        val cue=trial.cue ?: return 0.0
        return trial.legalTargets().maxOfOrNull { target ->
            val segment=(target.p-cue.p).planar()
            val squared=segment.dot(segment)
            if(squared<=1e-12) 0.0 else {
                val blocked=trial.world.balls.any { other ->
                    if(other.id==cue.id || other.id==target.id || other.motion==Motion.POCKETED) false else {
                        val along=(other.p-cue.p).planar().dot(segment)/squared
                        along in 0.0..1.0 && (other.p-(cue.p+segment*along)).planar().length()<other.radius+cue.radius
                    }
                }
                if(blocked) 0.0 else 1/(1+sqrt(squared)/trial.table.length)
            }
        } ?: 0.0
    }
}
