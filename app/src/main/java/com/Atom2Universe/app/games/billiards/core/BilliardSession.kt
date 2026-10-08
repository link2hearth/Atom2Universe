package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*

enum class PlayMode { PRACTICE, LOCAL, COMPUTER }
enum class Foul { NONE, SCRATCH, NO_CONTACT, WRONG_BALL, NO_RAIL, WRONG_POT, OWN_PIN, OFF_TABLE, THREE_FOULS, ILLEGAL_BREAK }
enum class HandRegion { ANYWHERE, HEAD, D, LOWER_HALF, OPPOSITE_HALF, START_SPOTS }
enum class ShotDecision { NONE, PUSH_OUT, TEN_OPTION, SNOOKER_FOUL, RERACK, ILLEGAL_NINE_BREAK, ENGLISH_RESET }
data class MatchState(
    var player: Int = 0, var score0: Int = 0, var score1: Int = 0,
    var group0: Int = 0, var winner: Int = -1, var ballInHand: Boolean = false,
    var breaking: Boolean = true, var snookerColor: Boolean = false,
    var lastRedColor: Boolean = false, var cadreZone: Int = -1, var cadreCount: Int = 0,
    var foul: Foul = Foul.NONE, var points: Int = 0, var shots: Int = 0,
    var freeShots: Int = 0, var fouls0: Int = 0, var fouls1: Int = 0,
    var pushOutAvailable: Boolean = false, var decision: ShotDecision = ShotDecision.NONE,
    var handRegion: HandRegion = HandRegion.ANYWHERE, var run: Int = 0,
    var best0: Int = 0, var best1: Int = 0, var anchorZone: Int = -1,
    var anchorCount: Int = 0, var respottedBlack: Boolean = false,
    var captured0: List<Int> = emptyList(), var captured1: List<Int> = emptyList(),
    var pendingSpots: List<Int> = emptyList(), var englishCannons: Int = 0,
    var englishHazards: Int = 0, var englishRedPots: Int = 0
) {
    fun add(player: Int, points: Int) { if(player == 0) score0+=points else score1+=points }
    fun score(player: Int) = if(player == 0) score0 else score1
    fun fouls(player: Int) = if(player==0) fouls0 else fouls1
    fun recordFoul(player: Int, foul: Boolean) {
        if(player==0) fouls0=if(foul) fouls0+1 else 0 else fouls1=if(foul) fouls1+1 else 0
    }
    fun captured(player: Int) = if(player==0) captured0 else captured1
    fun capture(player: Int,id: Int) { if(player==0) captured0=captured0+id else captured1=captured1+id }
    fun release(player: Int,id: Int) { if(player==0) captured0=captured0-id else captured1=captured1-id }
}
data class SessionSnapshot(val balls: List<Ball>, val pins: List<Pin>, val match: MatchState,
                           val cueId: Int, val drill: Int, val time: Double = 0.0,
                           val nominated: Int = -1, val pocket: Int = -1,
                           val pushOut: Boolean = false, val safety: Boolean = false,
                           val bankRails: List<Int> = emptyList())

/** Rules consume the actual ordered collision log, never geometric guesses. */
class BilliardSession(val discipline: Discipline, val mode: PlayMode, val clothSpeed: Int = 1,
                      tableSize: BilliardTableSize = BilliardTableSize.defaultFor(discipline.family),
                      private val trackVisualRotation: Boolean = true) {
    val table = BilliardTable(discipline.family,clothSpeed,discipline,tableSize)
    var world = BilliardWorld(table,table.rack(discipline),table.pins(discipline),trackVisualRotation)
        private set
    var match = MatchState()
        private set
    var cueId = 0
    var nominated = -1
    var calledPocket = -1
    var pushOut = false
    var safety = false
    var bankRails: List<Int> = emptyList()
    var drill = 0
        private set
    var before: SessionSnapshot? = null
        private set
    var lastShot: Shot? = null
        private set
    var replaying = false
        private set
    val cue get() = world.balls.firstOrNull { it.id == cueId && it.motion != Motion.POCKETED }
    val ready get() = !world.moving && !replaying && match.winner < 0 && cue != null && match.decision==ShotDecision.NONE
    val cuePlacementValid get() = mode==PlayMode.PRACTICE || !match.ballInHand ||
        cue?.let { (discipline==Discipline.BLACKBALL && !match.breaking || inHandRegion(it.p)) && table.canPlace(it.p,world.balls,it.id) }==true
    val canPushOut get() = mode!=PlayMode.PRACTICE && ready && match.pushOutAvailable
    val canSafety get() = mode!=PlayMode.PRACTICE && (discipline==Discipline.STRAIGHT || discipline==Discipline.EIGHT && !match.breaking)
    val needsCall get() = mode!=PlayMode.PRACTICE && !pushOut && !safety &&
        ((discipline in listOf(Discipline.EIGHT,Discipline.TEN,Discipline.BANK) && !match.breaking) || discipline==Discipline.STRAIGHT ||
            discipline in listOf(Discipline.SNOOKER,Discipline.SIX_RED) && match.snookerColor)
    private var shotActive = false
    private var replayAfter: SessionSnapshot? = null
    private var openingBalls = emptyList<Ball>()
    private val spots = world.balls.associate { it.id to it.p }

    init {
        if(discipline == Discipline.ARTISTIC) setupDrill(0)
        else if(mode!=PlayMode.PRACTICE) {
            match.ballInHand=true
            match.handRegion=when {
                discipline.family==TableFamily.SNOOKER -> HandRegion.D
                discipline==Discipline.FIVE_PINS || discipline==Discipline.NINE_PINS -> HandRegion.LOWER_HALF
                discipline.family==TableFamily.CAROM -> HandRegion.START_SPOTS
                else -> HandRegion.HEAD
            }
            if(discipline==Discipline.ENGLISH) world.balls.first { it.id==1 }.motion=Motion.POCKETED
        }
    }

    fun snapshot() = SessionSnapshot(world.balls.map { it.copyDeep() },world.pins.map { it.copy() },match.copy(),cueId,drill,world.time,nominated,calledPocket,pushOut,safety,bankRails.toList())
    fun startWithPlayer(player: Int) {
        require(player in 0..1 && match.shots==0 && before==null)
        match.player=player
        if(discipline.family==TableFamily.CAROM && discipline!=Discipline.ARTISTIC || discipline==Discipline.ENGLISH) cueId=player
        if(discipline==Discipline.ENGLISH && player==1) {
            world.balls.first { it.id==0 }.let { it.motion=Motion.POCKETED; it.stop() }
            respot(1,V3(table.headLine-table.dRadius/2,table.width/2))
        }
        ensureHandPlacement()
    }
    val isShotActive get() = shotActive && !replaying
    fun persistentSnapshot() = replayAfter ?: snapshot()
    fun recoverHistory(previous: SessionSnapshot?, shot: Shot?, events: List<BilliardEvent>, active: Boolean) {
        before=previous; lastShot=shot; openingBalls=previous?.balls ?: emptyList()
        world.events.addAll(events); shotActive=active
    }
    fun restore(s: SessionSnapshot) {
        world=BilliardWorld(table,s.balls.map { it.copyDeep() }.toMutableList(),s.pins.map { it.copy() }.toMutableList(),trackVisualRotation)
        world.time=s.time
        match=s.match.copy(); cueId=s.cueId; drill=s.drill; shotActive=false
        nominated=s.nominated; calledPocket=s.pocket; pushOut=s.pushOut; safety=s.safety; bankRails=s.bankRails.toList()
        recoverEmbeddedBalls()
    }
    /** Recover old saves/replays affected by reversed jaws, without inventing a pot or points. */
    private fun recoverEmbeddedBalls() {
        var repairedCue=false
        for(b in world.balls) {
            if(b.motion==Motion.POCKETED || b.motion==Motion.AIRBORNE || table.supportsCenter(b.p)) continue
            // A legitimate snapshot may catch a ball just as it enters a pocket.
            if(table.pockets.any { (b.p-it.center).planar().length()<=it.radius }) continue
            val inset=b.radius+.001
            respot(b.id,V3(b.p.x.coerceIn(inset,table.length-inset),b.p.y.coerceIn(inset,table.width-inset),b.radius))
            if(b.id==cueId) repairedCue=true
        }
        if(repairedCue && match.ballInHand) ensureHandPlacement()
    }
    fun shoot(requested: Shot): Boolean {
        if(!ready || replaying || !cuePlacementValid || pushOut && !canPushOut) return false
        if(listOf(requested.angle,requested.speed,requested.side,requested.top,requested.elevation,requested.cueMass,requested.endMass).any { !it.isFinite() } || requested.cueMass<=0 || requested.endMass<0) return false
        if(needsCall && (nominated<0 || discipline.family!=TableFamily.SNOOKER && calledPocket !in table.pockets.indices)) return false
        if(needsCall && discipline==Discipline.BANK && bankRails.isEmpty()) return false
        // The cue rises over the rail and the balls behind: the stored shot is the one played.
        val shot=CueReach.lift(world,cue!!,requested)
        before=snapshot(); lastShot=shot; openingBalls=before!!.balls
        world.events.clear(); match.foul=Foul.NONE; match.points=0
        match.ballInHand=false
        PooltoolPhysics.strike(cue!!,shot); shotActive=true
        return true
    }
    fun update(seconds: Double): List<BilliardEvent> {
        val offset=world.events.size
        world.advance(seconds)
        val new=world.events.drop(offset)
        if(shotActive && !world.moving) {
            shotActive=false
            if(replaying) { replaying=false; replayAfter?.let(::restore); replayAfter=null }
            else if(mode == PlayMode.PRACTICE) {
                match.shots++; match.points=world.events.count { it.kind==EventKind.POCKET && it.ball!=cueId }
                if(cue==null) respot(cueId)
                world.pins.forEach { it.down=false }
            } else finishShot()
        }
        return new
    }
    fun undo(): Boolean {
        if(world.moving || replaying) return false
        val s=before ?: return false; restore(s); before=null; return true
    }
    fun replay(): Boolean {
        if(world.moving || replaying) return false
        val s=before ?: return false; val shot=lastShot ?: return false
        replayAfter=snapshot(); restore(s); openingBalls=s.balls
        replaying=true; shotActive=true; PooltoolPhysics.strike(cue!!,shot)
        return true
    }
    fun moveBall(id: Int, p: V3): Boolean {
        if(!ready || (mode != PlayMode.PRACTICE && (!match.ballInHand || id!=cueId))) return false
        val b=world.balls.firstOrNull { it.id==id } ?: return false
        val placed=if(mode!=PlayMode.PRACTICE && match.handRegion==HandRegion.START_SPOTS)
            V3(table.length/4,table.width/2+if(p.y<table.width/2) -table.startSpotOffset else table.startSpotOffset,table.radius) else p.copy(z=table.radius)
        if(!table.canPlace(placed,world.balls,id)) return false
        if(mode != PlayMode.PRACTICE && !inHandRegion(placed)) return false
        if(world.pins.any { !it.down && (it.p-placed).planar().length()<table.radius+.005 }) return false
        b.p=placed; b.motion=Motion.STILL; b.stop(); return true
    }
    fun inHandRegion(p: V3): Boolean = when(match.handRegion) {
        HandRegion.ANYWHERE -> true
        HandRegion.HEAD -> p.x<table.headLine
        HandRegion.D -> p.x<=table.headLine && (p-table.headSpot).planar().length()<=table.dRadius
        HandRegion.LOWER_HALF -> p.x+table.radius<table.length/2
        HandRegion.OPPOSITE_HALF -> world.balls.firstOrNull { it.id==1-match.player }?.let {
            if(it.p.x>=table.length/2) p.x+table.radius<table.length/2 else p.x-table.radius>table.length/2
        } ?: true
        HandRegion.START_SPOTS -> abs(p.x-table.length/4)<table.radius && abs(abs(p.y-table.width/2)-table.startSpotOffset)<table.radius
    }
    fun decide(playHere: Boolean, originalBreaker: Boolean = false): Boolean {
        if(world.moving || replaying || match.decision==ShotDecision.NONE) return false
        if(!playHere) {
            if(match.decision==ShotDecision.ENGLISH_RESET) {
                world.balls.forEach { it.motion=Motion.POCKETED; it.stop() }
                respot(2,table.colorSpot(21)); respot(1-match.player,table.colorSpot(19))
                respot(match.player,V3(table.headLine-table.dRadius/2,table.width/2))
                match.ballInHand=true; match.handRegion=HandRegion.D
            } else if(match.decision==ShotDecision.RERACK) {
                if(discipline==Discipline.STRAIGHT || discipline==Discipline.BANK || originalBreaker) match.player=before?.match?.player ?: 1-match.player
                world=BilliardWorld(table,table.rack(discipline),table.pins(discipline),trackVisualRotation); cueId=0
                match.breaking=true; match.ballInHand=true; match.handRegion=HandRegion.HEAD
                match.group0=0; match.freeShots=0; match.pushOutAvailable=false; match.run=0
            } else match.player=1-match.player
            if(match.decision==ShotDecision.SNOOKER_FOUL) {
                match.snookerColor=before?.match?.snookerColor==true
                match.lastRedColor=before?.match?.lastRedColor==true
            }
        }
        match.decision=ShotDecision.NONE
        ensureHandPlacement()
        return true
    }
    fun chooseCue(id: Int): Boolean {
        if(!ready || (mode!=PlayMode.PRACTICE && (discipline!=Discipline.PYRAMID_FREE || match.breaking))) return false
        if(world.balls.none { it.id==id && it.motion!=Motion.POCKETED }) return false
        cueId=id; return true
    }
    fun legalTargets(): List<Ball> {
        val active=world.balls.filter { it.id!=cueId && it.motion!=Motion.POCKETED }
        if(mode==PlayMode.PRACTICE) return active
        return when(discipline) {
            Discipline.EIGHT, Discipline.BLACKBALL, Discipline.HEYBALL -> {
                val group=groupFor(match.player)
                if(discipline==Discipline.BLACKBALL && match.freeShots>0) active
                else if(match.breaking && discipline==Discipline.EIGHT) active
                else if(group==0) active.filter { it.id!=8 }
                else active.filter { group(it.id)==group }.ifEmpty { active.filter { it.id==8 } }
            }
            Discipline.NINE, Discipline.TEN -> active.minByOrNull { it.id }?.let { listOf(it) } ?: emptyList()
            Discipline.SNOOKER, Discipline.SIX_RED -> {
                val reds=active.filter { it.id in 1..15 }
                if(match.snookerColor) active.filter { it.id>=16 }
                else if(reds.isNotEmpty()) reds
                else active.filter { it.id>=16 }.minByOrNull { it.id }?.let { listOf(it) } ?: emptyList()
            }
            Discipline.FIVE_PINS, Discipline.NINE_PINS -> active.filter { it.id==1-match.player }
            Discipline.FREE,Discipline.ONE_CUSHION,Discipline.THREE_CUSHION,Discipline.CADRE_47_1,Discipline.CADRE_47_2,Discipline.CADRE_71_2 -> if(match.breaking) active.filter { it.id==2 } else active
            else -> active
        }
    }
    private fun group(id: Int) = when(id) { in 1..7 -> 1; in 9..15 -> 2; else -> 0 }
    fun groupFor(player: Int) = if(match.group0==0) 0 else if(player==0) match.group0 else 3-match.group0
    fun pocketFor(player: Int) = if(player==0) 2 else 3
    private fun potValue(id: Int) = if(id in 1..15) 1 else id-14

    private fun finishShot() {
        val e=world.events; val player=match.player
        val wasBreak=match.breaking
        val wasFree=discipline==Discipline.BLACKBALL && match.freeShots>0
        val wasPush=pushOut && match.pushOutAvailable
        match.pushOutAvailable=false
        var newBreak=false
        val hits=e.filter { it.kind==EventKind.BALL && (it.ball==cueId || it.other==cueId) }
        fun other(hit: BilliardEvent) = if(hit.ball==cueId) hit.other else hit.ball
        val first=hits.firstOrNull()?.let(::other)
        val potted=e.filter { it.kind==EventKind.POCKET }
        val scratch=potted.any { it.ball==cueId }
        val off=e.any { it.kind==EventKind.OFF_TABLE }
        val targetIds=legalTargetIdsAtStart()
        var foul=when { off -> Foul.OFF_TABLE; wasPush -> Foul.NONE; first==null -> Foul.NO_CONTACT; first !in targetIds -> Foul.WRONG_BALL; else -> Foul.NONE }
        val firstTime=hits.firstOrNull()?.time ?: Double.POSITIVE_INFINITY
        // A ball in the kitchen cannot be played directly after a pool in-hand.
        if(before?.match?.ballInHand==true && before?.match?.handRegion==HandRegion.HEAD &&
            discipline.family!=TableFamily.BLACKBALL && !wasPush && first!=null &&
            openingBalls.firstOrNull { it.id==first }?.p?.x?.let { it<table.headLine }==true &&
            e.none { it.kind==EventKind.HEAD_CROSS && it.ball==cueId && it.time<firstTime }) foul=Foul.WRONG_BALL
        var points=0; var keep=false
        val carom=discipline.family==TableFamily.CAROM
        val pins=discipline==Discipline.FIVE_PINS || discipline==Discipline.NINE_PINS
        if(carom && !pins) {
            val objects=hits.map(::other).distinct()
            val last=hits.firstOrNull { objects.size>=2 && other(it)==objects.last() }
            val rails=if(last==null) 0 else e.count { it.kind==EventKind.CUSHION && it.ball==cueId && it.time < last.time }
            val required=when(discipline) { Discipline.ONE_CUSHION -> 1; Discipline.THREE_CUSHION -> 3; Discipline.ARTISTIC -> drill%4; else -> 0 }
            val scored=objects.size>=2 && rails>=required && foul==Foul.NONE
            if(scored) {
                points=1
                if(!validCaromRegions()) { points=0; foul=Foul.WRONG_BALL }
            }
            keep=points>0
        } else if(pins) {
            if(first != 1-player) foul=if(first==null) Foul.NO_CONTACT else Foul.WRONG_BALL
            val pinEvents=e.filter { it.kind==EventKind.PIN }
            val own=pinEvents.any { it.ball==cueId }
            if(pinEvents.any { it.ball==cueId && it.time<firstTime }) foul=Foul.OWN_PIN
            val outerDown=world.pins.any { it.id!=0 && it.down }
            val nine=discipline==Discipline.NINE_PINS
            points=pinEvents.distinctBy { it.other }.sumOf {
                if(it.other==0) { if(nine) { if(outerDown) 10 else 30 } else if(outerDown) 4 else 10 }
                else if(nine && it.other in 1..4) 8 else 2
            }
            val redContact=e.firstOrNull { it.kind==EventKind.BALL && (it.ball==2 || it.other==2) }
            if(redContact!=null) points+=if(foul!=Foul.NONE) 2 else if(nine) 6 else if(redContact.ball==cueId || redContact.other==cueId) 4 else 3
            if(nine && e.any { it.kind==EventKind.CUSHION && it.ball==cueId && it.time<firstTime }) points*=2
            if(foul!=Foul.NONE) { match.add(1-player,points+2); points=0; match.ballInHand=true; match.handRegion=HandRegion.OPPOSITE_HALF }
            else if(own || wasBreak) { match.add(1-player,points); points=0; if(own) foul=Foul.OWN_PIN }
            world.pins.forEach { it.down=false }
        } else when(discipline) {
            Discipline.ENGLISH -> {
                val cannon=hits.map(::other).distinct().size==2
                if(before?.match?.ballInHand==true) {
                    val outsideContact=e.firstOrNull { it.ball==cueId && it.kind==EventKind.CUSHION && (it.position?.x ?: 0.0)>table.headLine ||
                        it.kind==EventKind.BALL && (it.ball==cueId || it.other==cueId) && openingBalls.firstOrNull { b -> b.id==other(it) }?.p?.x?.let { x -> x>table.headLine }==true }
                    if(first!=null && openingBalls.first { it.id==first }.p.x<=table.headLine && (outsideContact==null || outsideContact.time>firstTime)) foul=Foul.WRONG_BALL
                    if(cue?.p?.x?.let { it<=table.headLine }==true && outsideContact==null) foul=Foul.WRONG_BALL
                }
                if(foul==Foul.NONE) {
                    if(cannon) points+=2
                    potted.forEach { p -> points+=if(p.ball==cueId) { if(first==2) 3 else 2 } else if(p.ball==2) 3 else 2 }
                    match.englishCannons=if(cannon && potted.isEmpty()) match.englishCannons+1 else 0
                    match.englishHazards=if(!cannon && potted.isNotEmpty()) match.englishHazards+1 else 0
                    if(match.englishCannons>75 || match.englishHazards>15) { foul=Foul.WRONG_POT; points=0 }
                    keep=points>0
                }
                if(foul!=Foul.NONE) { match.add(1-player,2); match.decision=ShotDecision.ENGLISH_RESET }
                // The opponent's cue remains off the table until their next visit.
                val redOnly=foul==Foul.NONE && !cannon && potted.size==1 && potted[0].ball==2
                val oldRed=openingBalls.first { it.id==2 }
                val redOnSpot=listOf(table.colorSpot(21),table.footSpot).any { (oldRed.p-it).length()<.00001 }
                match.englishRedPots=if(redOnly && redOnSpot) match.englishRedPots+1 else 0
                if(world.balls.first { it.id==2 }.motion==Motion.POCKETED) {
                    val spots=if(match.englishRedPots>=2) listOf(table.colorSpot(19),table.footSpot,table.colorSpot(21))
                        else listOf(table.colorSpot(21),table.footSpot,table.colorSpot(19))
                    respot(2,spots.firstOrNull { table.canPlace(it,world.balls,2) } ?: spots.first())
                }
                if(match.englishHazards==15 && world.balls.first { it.id==1-player }.motion==Motion.POCKETED)
                    respot(1-player,if(table.canPlace(table.headSpot,world.balls,1-player)) table.headSpot else table.colorSpot(16))
                if(scratch && keep && foul==Foul.NONE) { respot(cueId,V3(table.headLine-table.dRadius/2,table.width/2)); match.ballInHand=true; match.handRegion=HandRegion.D }
            }
            Discipline.SNOOKER, Discipline.SIX_RED -> {
                if(scratch) foul=Foul.SCRATCH
                if(potted.any { it.ball!=cueId && it.ball !in targetIds }) foul=Foul.WRONG_POT
                val wasColor=match.snookerColor
                if(foul==Foul.NONE) {
                    points=potted.sumOf { potValue(it.ball) }
                    if(wasColor && potted.size>1) { foul=Foul.WRONG_POT; points=0 }
                }
                val redsRemain=world.balls.any { it.id in 1..15 && it.motion!=Motion.POCKETED }
                val redsAtStart=openingBalls.any { it.id in 1..15 && it.motion!=Motion.POCKETED }
                if(wasColor || foul!=Foul.NONE) (potted+e.filter { it.kind==EventKind.OFF_TABLE }).filter { it.ball>=16 }.distinctBy { it.ball }.sortedByDescending { it.ball }.forEach { if(redsRemain || match.lastRedColor || foul!=Foul.NONE) respot(it.ball) }
                if(foul!=Foul.NONE) {
                    val involved=(potted+e.filter { it.kind==EventKind.OFF_TABLE }).map { if(it.ball==cueId) 4 else potValue(it.ball) }
                    val penalty=maxOf(4,targetIds.maxOfOrNull(::potValue) ?: 4,first?.let(::potValue) ?: 4,involved.maxOrNull() ?: 4)
                    match.add(1-player,penalty); points=0; match.snookerColor=false
                    match.lastRedColor=false; match.decision=ShotDecision.SNOOKER_FOUL
                } else {
                    keep=points>0
                    match.snookerColor=keep && !wasColor && redsAtStart
                    match.lastRedColor=!redsRemain && redsAtStart && !wasColor && keep
                    if(wasColor) { match.snookerColor=false; match.lastRedColor=false }
                }
                if(scratch || e.any { it.kind==EventKind.OFF_TABLE && it.ball==cueId }) { respot(cueId,V3(table.headLine-table.dRadius/2,table.width/2)); match.ballInHand=true; match.handRegion=HandRegion.D }
                val finalBlackAtStart=openingBalls.filter { it.id!=cueId && it.motion!=Motion.POCKETED }.map { it.id }==listOf(21)
                if(world.balls.none { it.id!=cueId && it.motion!=Motion.POCKETED } || finalBlackAtStart && foul!=Foul.NONE) {
                    val s0=match.score0+if(player==0) points else 0; val s1=match.score1+if(player==1) points else 0
                    match.decision=ShotDecision.NONE
                    if(s0==s1) {
                        respot(21); respot(cueId,V3(table.headLine-table.dRadius/2,table.width/2))
                        match.ballInHand=true; match.handRegion=HandRegion.D; match.respottedBlack=true; keep=false
                    } else match.winner=if(s0>s1) 0 else 1
                }
            }
            Discipline.PYRAMID_FREE, Discipline.PYRAMID_DYNAMIC -> {
                val dynamicHand=discipline==Discipline.PYRAMID_DYNAMIC && before?.match?.ballInHand==true && !wasBreak
                if(dynamicHand && scratch) foul=Foul.SCRATCH
                if(foul==Foul.NONE && potted.isEmpty() && !legalPyramidShot(e,firstTime,wasBreak)) foul=Foul.NO_RAIL
                if(foul==Foul.NONE) { points=potted.size; keep=points>0 }
                else {
                    potted.forEach { respot(it.ball) }
                    e.filter { it.kind==EventKind.OFF_TABLE }.forEach { respot(it.ball) }
                    world.balls.firstOrNull { it.id!=cueId && it.motion!=Motion.POCKETED }?.let { it.motion=Motion.POCKETED; it.stop(); match.add(1-player,1) }
                }
                if(discipline==Discipline.PYRAMID_DYNAMIC && (scratch || off || foul!=Foul.NONE)) {
                    respot(0,V3(table.length*.15,table.width/2)); match.ballInHand=true; match.handRegion=HandRegion.ANYWHERE
                    if(foul==Foul.NONE && scratch) world.balls.firstOrNull { it.id!=0 && it.motion!=Motion.POCKETED }?.let { it.motion=Motion.POCKETED; it.stop() }
                }
                if(discipline==Discipline.PYRAMID_FREE && cue==null) cueId=world.balls.firstOrNull { it.motion!=Motion.POCKETED }?.id ?: 0
            }
            else -> {
                if(scratch) foul=Foul.SCRATCH
                val afterFirst=hits.firstOrNull()?.time ?: Double.POSITIVE_INFINITY
                if(!wasBreak && !wasPush && foul==Foul.NONE && potted.isEmpty() && e.none { it.kind==EventKind.CUSHION && it.time>=afterFirst } &&
                    !(discipline==Discipline.BLACKBALL && blackballSnookered(targetIds))) foul=Foul.NO_RAIL
                val calledOnBreak=!safety && potted.any { it.ball==nominated && it.other==calledPocket }
                if(match.breaking && (foul==Foul.NONE || discipline==Discipline.STRAIGHT) && (potted.isEmpty() || discipline==Discipline.STRAIGHT && !calledOnBreak)) {
                    val railBalls=e.filter { it.kind==EventKind.CUSHION && it.ball!=cueId }.map { it.ball }.distinct().size
                    val legalBreak=when(discipline) {
                        Discipline.EIGHT,Discipline.NINE,Discipline.TEN,Discipline.HEYBALL,Discipline.BANK -> railBalls>=4
                        Discipline.BLACKBALL -> e.filter { it.kind==EventKind.CROSS_LINE && it.ball!=cueId && it.other<0 }.map { it.ball }.distinct().size>=2
                        Discipline.STRAIGHT -> railBalls>=2 && e.any { it.kind==EventKind.CUSHION && it.ball==cueId }
                        else -> e.any { it.kind==EventKind.CUSHION && it.time>=afterFirst }
                    }
                    if(!legalBreak) {
                        foul=if(discipline==Discipline.ONE_POCKET) Foul.NO_RAIL else Foul.ILLEGAL_BREAK
                        if(discipline in listOf(Discipline.EIGHT,Discipline.STRAIGHT,Discipline.HEYBALL,Discipline.BANK)) match.decision=ShotDecision.RERACK
                    }
                }
                if(wasBreak && discipline==Discipline.NINE && foul==Foul.NONE && potted.isEmpty() &&
                    e.filter { it.kind==EventKind.HEAD_CROSS && it.ball!=cueId && it.other<0 }.map { it.ball }.distinct().size<3)
                    match.decision=ShotDecision.ILLEGAL_NINE_BREAK
                val legalPots=potted.filter { it.ball!=cueId }
                when(discipline) {
                    Discipline.EIGHT, Discipline.BLACKBALL, Discipline.HEYBALL -> {
                        val eight=legalPots.firstOrNull { it.ball==8 }
                        val eightOff=e.any { it.kind==EventKind.OFF_TABLE && it.ball==8 }
                        if(eight!=null) {
                            if(match.breaking && discipline==Discipline.BLACKBALL) {
                                world=BilliardWorld(table,table.rack(discipline),trackVisualRotation=trackVisualRotation); newBreak=true; foul=Foul.NONE; keep=true
                                match.ballInHand=true; match.handRegion=HandRegion.HEAD
                            }
                            else if(match.breaking) { respot(8); match.decision=ShotDecision.RERACK }
                            else {
                                val ownGroup=groupFor(player)
                                val cleared=ownGroup!=0 && (if(discipline==Discipline.BLACKBALL) world.balls else openingBalls).none { group(it.id)==ownGroup && it.motion!=Motion.POCKETED }
                                val callOk=discipline!=Discipline.EIGHT || nominated==8 && calledPocket==eight.other
                                match.winner=if(cleared && foul==Foul.NONE && callOk) player else 1-player
                            }
                        }
                        if(eightOff && !newBreak) { if(wasBreak || discipline==Discipline.BLACKBALL) respot(8) else match.winner=1-player }
                        if(discipline==Discipline.BLACKBALL && !wasFree && match.group0!=0 && legalPots.any { group(it.ball)==3-groupFor(player) } && legalPots.none { group(it.ball)==groupFor(player) || it.ball==8 }) foul=Foul.WRONG_POT
                        if(foul==Foul.NONE && !newBreak) {
                            val called=legalPots.filter { !safety && (discipline!=Discipline.EIGHT || match.breaking || it.ball==nominated && it.other==calledPocket) }
                            val groups=called.map { group(it.ball) }.filter { it!=0 }.distinct()
                            if(match.group0==0 && !match.breaking && !wasFree && (discipline!=Discipline.BLACKBALL || groups.size==1))
                                called.firstOrNull { group(it.ball)!=0 && (discipline!=Discipline.HEYBALL || group(it.ball)==group(first ?: -1)) }?.let { match.group0=if(player==0) group(it.ball) else 3-group(it.ball) }
                            val own=groupFor(player)
                            points=called.count { it.ball!=8 && (wasFree || own==0 || group(it.ball)==own) }
                            keep=points>0 || wasBreak && eight!=null
                        }
                        if(discipline==Discipline.BLACKBALL && foul!=Foul.NONE) match.freeShots=1
                    }
                    Discipline.NINE, Discipline.TEN -> {
                        val winId=if(discipline==Discipline.NINE) 9 else 10
                        val win=legalPots.firstOrNull { it.ball==winId }
                        val callOk=discipline==Discipline.NINE || (nominated==winId && calledPocket==win?.other)
                        if(win!=null) {
                            val finalTen=discipline==Discipline.NINE || !match.breaking && openingBalls.count { it.id!=cueId && it.motion!=Motion.POCKETED }==1
                            if(foul==Foul.NONE && callOk && finalTen && !wasPush) match.winner=player
                            else respot(winId)
                        }
                        points=if(foul==Foul.NONE) legalPots.count { discipline==Discipline.NINE || match.breaking || (it.ball==nominated && it.other==calledPocket) } else 0
                        keep=points>0
                        if(wasPush && foul==Foul.NONE) { points=0; keep=false; match.decision=ShotDecision.PUSH_OUT }
                        else if(discipline==Discipline.TEN && !wasBreak && foul==Foul.NONE && points==0 && legalPots.isNotEmpty()) match.decision=ShotDecision.TEN_OPTION
                    }
                    Discipline.ONE_POCKET -> {
                        legalPots.forEach { p ->
                            when(p.other) { pocketFor(player) -> if(foul==Foul.NONE) { if(match.score(player)+points<0) queueSpot(p.ball) else match.capture(player,p.ball); points++ } else queueSpot(p.ball)
                                pocketFor(1-player) -> if(scratch) queueSpot(p.ball) else { if(match.score(1-player)<0) queueSpot(p.ball) else match.capture(1-player,p.ball); match.add(1-player,1) }
                                else -> queueSpot(p.ball) }
                        }
                        if(foul!=Foul.NONE) { returnScoredBall(player,potted.map { it.ball }); match.add(player,-1) }
                        keep=points>0
                    }
                    Discipline.BANK -> {
                        legalPots.forEach { p ->
                            val banks=e.filter { it.kind==EventKind.CUSHION && it.ball==p.ball && it.time<p.time && it.other<6 }.map { it.other }
                            val clean=first==p.ball && e.none { it.time<p.time && (it.kind==EventKind.BALL && it.time>firstTime && (it.ball==p.ball || it.other==p.ball) || it.kind==EventKind.CUSHION && it.ball==cueId && it.time<firstTime) }
                            if(!wasBreak && foul==Foul.NONE && clean && banks.isNotEmpty() && banks==bankRails && nominated==p.ball && calledPocket==p.other) {
                                if(match.score(player)+points<0) queueSpot(p.ball) else match.capture(player,p.ball); points++
                            } else queueSpot(p.ball)
                        }; keep=points>0 || wasBreak && foul==Foul.NONE && legalPots.isNotEmpty()
                        if(foul!=Foul.NONE && foul!=Foul.ILLEGAL_BREAK) { returnScoredBall(player,potted.map { it.ball }); points=-1 }
                    }
                    Discipline.STRAIGHT -> {
                        val madeCall=!safety && legalPots.any { it.ball==nominated && it.other==calledPocket }
                        points=if(foul==Foul.NONE) { if(madeCall) legalPots.size else 0 } else if(foul==Foul.ILLEGAL_BREAK) -2 else -1
                        if(foul!=Foul.NONE || !madeCall) legalPots.forEach { respot(it.ball) }
                        keep=points>0
                    }
                    else -> Unit
                }
                val cueOff=e.any { it.kind==EventKind.OFF_TABLE && it.ball==cueId }
                val kitchenGame=discipline in listOf(Discipline.STRAIGHT,Discipline.ONE_POCKET,Discipline.BANK)
                if(!newBreak) {
                    match.ballInHand=match.ballInHand || if(kitchenGame) scratch || cueOff else foul!=Foul.NONE
                    if(!match.ballInHand || match.handRegion!=HandRegion.HEAD || discipline!=Discipline.STRAIGHT)
                        match.handRegion=if(kitchenGame || discipline==Discipline.BLACKBALL || wasBreak && discipline in listOf(Discipline.EIGHT,Discipline.HEYBALL)) HandRegion.HEAD else HandRegion.ANYWHERE
                    if(scratch || cueOff) respot(cueId,V3(table.headLine-table.radius*3,table.width/2))
                }
                if(discipline==Discipline.STRAIGHT && points>0 && world.balls.count { it.id!=cueId && it.motion!=Motion.POCKETED }<=1) rerackStraight()
            }
        }
        world.balls.filter { it.motion==Motion.POCKETED && carom }.forEach { respot(it.id) }
        if(off && !newBreak) e.filter { it.kind==EventKind.OFF_TABLE }.forEach {
            val spot=when(discipline) {
                Discipline.EIGHT,Discipline.HEYBALL -> it.ball==cueId || it.ball==8 && wasBreak
                Discipline.NINE -> it.ball==cueId || it.ball==9
                Discipline.TEN -> it.ball==cueId || it.ball==10
                Discipline.SNOOKER,Discipline.SIX_RED -> false // Colours and cue already handled; reds stay off.
                Discipline.ENGLISH,Discipline.PYRAMID_FREE,Discipline.PYRAMID_DYNAMIC -> false // Handled by the discipline, including penalty-ball removal.
                else -> true
            }
            if(spot && world.balls.first { b -> b.id==it.ball }.motion==Motion.POCKETED) respot(it.ball)
        }
        if(discipline in listOf(Discipline.NINE,Discipline.TEN,Discipline.STRAIGHT,Discipline.ONE_POCKET,Discipline.BANK)) {
            if(foul!=Foul.ILLEGAL_BREAK) match.recordFoul(player,foul!=Foul.NONE)
            if(match.fouls(player)>=3) {
                if(discipline==Discipline.STRAIGHT) {
                    points-=15; match.recordFoul(player,false); world=BilliardWorld(table,table.rack(discipline),trackVisualRotation=trackVisualRotation)
                    newBreak=true; keep=true; match.ballInHand=true; match.handRegion=HandRegion.HEAD
                } else match.winner=1-player
                foul=Foul.THREE_FOULS
            }
        }
        match.add(player,points); match.points=points; match.foul=foul; match.shots++; match.breaking=newBreak
        if(foul==Foul.NONE && points>0) {
            match.run+=points
            if(player==0) match.best0=max(match.best0,match.run) else match.best1=max(match.best1,match.run)
        }
        if(wasBreak && foul==Foul.NONE && match.decision==ShotDecision.NONE && discipline in listOf(Discipline.NINE,Discipline.TEN)) match.pushOutAvailable=true
        val target=when(discipline) { Discipline.FIVE_PINS -> 60; Discipline.NINE_PINS -> 100; Discipline.STRAIGHT -> 50; Discipline.ENGLISH -> 100;
            Discipline.PYRAMID_FREE,Discipline.PYRAMID_DYNAMIC,Discipline.ONE_POCKET,Discipline.BANK -> 8; else -> 10 }
        if(carom || discipline==Discipline.ENGLISH || discipline==Discipline.STRAIGHT || discipline==Discipline.ONE_POCKET || discipline==Discipline.BANK || discipline.family==TableFamily.PYRAMID) {
            if(match.score0>=target || match.score1>=target) match.winner=if(match.score0>=target) 0 else 1
        }
        if(wasFree && foul==Foul.NONE) match.freeShots=0
        if(!newBreak && (!keep || foul!=Foul.NONE || pins) && !(wasFree && foul==Foul.NONE)) match.player=1-player
        if(match.player!=player || foul!=Foul.NONE) { match.run=0; match.cadreZone=-1; match.cadreCount=0; match.anchorZone=-1; match.anchorCount=0 }
        if(match.player!=player || foul!=Foul.NONE) { match.englishCannons=0; match.englishHazards=0; match.englishRedPots=0 }
        if(match.pendingSpots.isNotEmpty() && (match.player!=player || world.balls.none { it.id!=cueId && it.motion!=Motion.POCKETED })) {
            match.pendingSpots.sorted().forEach { respot(it) }; match.pendingSpots=emptyList()
        }
        if(discipline==Discipline.ENGLISH || carom && discipline!=Discipline.ARTISTIC) cueId=match.player
        if(discipline==Discipline.ENGLISH && cue==null) {
            respot(cueId,V3(table.headLine-table.dRadius/2,table.width/2)); match.ballInHand=true; match.handRegion=HandRegion.D
        }
        if(match.winner>=0) match.decision=ShotDecision.NONE
        ensureHandPlacement()
        if(discipline==Discipline.ARTISTIC && points>0 && match.winner<0) setupDrill(drill+1,resetScore=false)
        nominated=-1; calledPocket=-1; pushOut=false; safety=false; bankRails=emptyList()
    }

    private fun queueSpot(id: Int) { if(id !in match.pendingSpots) match.pendingSpots=match.pendingSpots+id }

    /** Keep a usable default placement after a change of cue ball or restricted hand. */
    private fun ensureHandPlacement() {
        if(!match.ballInHand || cuePlacementValid || match.winner>=0) return
        val ball=cue ?: return
        val options=mutableListOf(V3(table.headLine-table.dRadius/2,table.width/2),
            V3(table.length/4,table.width/2-table.startSpotOffset),V3(table.length/4,table.width/2+table.startSpotOffset))
        for(x in 1..40) for(y in 1..20) options+=V3(table.length*x/41,table.width*y/21)
        options.firstOrNull { p -> inHandRegion(p) && table.canPlace(p,world.balls,cueId) &&
            world.pins.none { !it.down && (it.p-p).planar().length()<table.radius+.005 } }?.let {
            ball.p=it.copy(z=table.radius); ball.motion=Motion.STILL; ball.stop()
        }
    }

    /** A blackball snooker has no direct visible cut on any eligible object ball. */
    private fun blackballSnookered(targets: Set<Int>): Boolean {
        val white=openingBalls.firstOrNull { it.id==cueId } ?: return false
        val balls=openingBalls.filter { it.id!=cueId && it.motion!=Motion.POCKETED }
        for(target in balls.filter { it.id in targets }) {
            val base=atan2(target.p.y-white.p.y,target.p.x-white.p.x)
            val span=asin(((target.radius+white.radius)/(target.p-white.p).planar().length()).coerceIn(0.0,1.0))
            val cuts=mutableListOf(-span,span)
            for(ball in balls) {
                val rel=(ball.p-white.p).planar(); val angle=atan2(sin(atan2(rel.y,rel.x)-base),cos(atan2(rel.y,rel.x)-base))
                val half=asin(((ball.radius+white.radius)/rel.length()).coerceIn(0.0,1.0))
                for(edge in listOf(angle-half,angle+half)) if(edge>-span && edge<span) cuts+=edge
            }
            val sorted=cuts.sorted()
            for(i in 0 until sorted.lastIndex) {
                val angle=base+(sorted[i]+sorted[i+1])/2; val dir=V3(cos(angle),sin(angle))
                fun distance(ball: Ball): Double {
                    val rel=(ball.p-white.p).planar(); val along=rel.dot(dir)
                    val disc=(ball.radius+white.radius).pow(2)-(rel.dot(rel)-along*along)
                    return if(along<=0 || disc<0) Double.POSITIVE_INFINITY else max(0.0,along-sqrt(disc))
                }
                if(balls.minByOrNull(::distance)?.id==target.id) return false
            }
        }
        return true
    }

    private fun legalTargetIdsAtStart(): Set<Int> {
        // Evaluate before removing potted balls (especially the final group ball).
        val current=world
        world=BilliardWorld(table,openingBalls.map { it.copyDeep() }.toMutableList(),trackVisualRotation=trackVisualRotation)
        val ids=legalTargets().map { it.id }.toSet(); world=current
        return if(nominated>=0 && match.snookerColor) setOf(nominated) else ids
    }
    private fun validCaromRegions(): Boolean {
        val objects=world.balls.filter { it.id!=cueId }
        val start=openingBalls.filter { it.id!=cueId }
        if(objects.size!=2 || start.size!=2) return true
        var valid=true
        for(anchor in listOf(false,true)) {
            val zones=table.regions.filter { (it.id>=100)==anchor }
            val old=zones.firstOrNull { zone -> start.all { zone.contains(it.p) } }
            val next=zones.firstOrNull { zone -> objects.all { zone.contains(it.p) } }
            val left=old!=null && world.events.any { it.kind==EventKind.REGION_EXIT && it.ball!=cueId && it.other==old.id }
            val previous=if(anchor) match.anchorZone else match.cadreZone
            val count=if(anchor) match.anchorCount else match.cadreCount
            val stayed=old!=null && !left && old.id==next?.id
            val limit=if(discipline==Discipline.CADRE_47_1) 1 else 2
            val total=if(stayed) (if(previous==old?.id) count else 0)+1 else 0
            if(stayed && total>=limit) valid=false
            if(anchor) { match.anchorZone=next?.id ?: -1; match.anchorCount=total }
            else { match.cadreZone=next?.id ?: -1; match.cadreCount=total }
        }
        return valid
    }
    private fun returnScoredBall(player: Int, excluded: List<Int>) {
        if(match.score(player)<=0) return
        val id=match.captured(player).lastOrNull { it !in excluded } ?: return
        respot(id); match.release(player,id)
    }
    private fun legalPyramidShot(events: List<BilliardEvent>, first: Double, breaking: Boolean): Boolean {
        val after=events.filter { it.time>=first }
        if(breaking) return after.filter { it.kind==EventKind.CUSHION }.map { it.ball }.distinct().size>=3 ||
            after.any { it.kind==EventKind.CROSS_LINE } && after.filter { it.kind==EventKind.CUSHION }.map { it.ball }.distinct().size>=2
        val rails=mutableMapOf<Int,Set<Int>>(); val crossed=mutableSetOf<Int>()
        for(e in after) when(e.kind) {
            EventKind.CUSHION -> {
                if(e.ball in crossed || rails[e.ball].orEmpty().any { it!=e.other }) return true
                rails[e.ball]=rails[e.ball].orEmpty()+e.other
            }
            EventKind.CROSS_LINE -> { if(rails[e.ball].orEmpty().isNotEmpty()) return true; crossed+=e.ball }
            EventKind.BALL -> {
                val union=rails[e.ball].orEmpty()+rails[e.other].orEmpty()
                rails[e.ball]=union; rails[e.other]=union
                if(e.ball in crossed || e.other in crossed) { crossed+=e.ball; crossed+=e.other }
            }
            else -> Unit
        }
        return false
    }
    private fun respot(id: Int, preferred: V3? = null) {
        val b=world.balls.firstOrNull { it.id==id } ?: return
        val snooker=discipline in listOf(Discipline.SNOOKER,Discipline.SIX_RED) && id>=16
        val point=preferred ?: if(snooker) table.colorSpot(id) else if(table.family==TableFamily.CAROM) spots[id] ?: table.footSpot else table.footSpot
        var chosen: V3?=null
        if(table.canPlace(point,world.balls,id)) chosen=point
        if(chosen==null && snooker) chosen=(21 downTo 16).map(table::colorSpot).firstOrNull { table.canPlace(it,world.balls,id) }
        // Spot on the long string, towards the foot cushion first. Resolve the
        // forbidden intervals exactly so balls are touching, without a coarse grid.
        for(direction in listOf(1,-1)) {
            if(chosen!=null) break
            var x=point.x
            repeat(world.balls.size+1) {
                val candidate=V3(x,point.y,table.radius)
                if(table.canPlace(candidate,world.balls,id)) { if(chosen==null) chosen=candidate }
                if(chosen==null) for(other in world.balls) if(other.id!=id && other.motion!=Motion.POCKETED) {
                    val distance=table.radius+other.radius+.000002
                    val dy=other.p.y-point.y
                    if(abs(dy)<distance) {
                        val reach=sqrt(distance*distance-dy*dy)
                        if(x>other.p.x-reach-.000001 && x<other.p.x+reach+.000001) x=other.p.x+direction*(reach+.000002)
                    }
                }
            }
        }
        if(chosen==null) loop@ for(x in 1..30) for(y in 1..15) {
            val p=V3(table.length*x/31,table.width*y/16,table.radius)
            if(table.canPlace(p,world.balls,id)) { chosen=p; break@loop }
        }
        chosen?.let { b.p=it; b.motion=Motion.STILL; b.stop() }
    }
    private fun rerackStraight() {
        var survivor=world.balls.firstOrNull { it.id!=cueId && it.motion!=Motion.POCKETED }
        val cue=cue ?: return
        val positions=table.rack(Discipline.STRAIGHT).filter { it.id!=0 }.map { it.p }
        fun obstructs(b: Ball) = positions.any { (it-b.p).planar().length()<table.radius*2+.003 }
        val cueIn=obstructs(cue); val objectIn=survivor?.let(::obstructs)==true
        if(cueIn && objectIn) { survivor?.motion=Motion.POCKETED; survivor=null }
        if(objectIn && survivor!=null) {
            val head=table.headSpot
            respot(survivor.id,if((cue.p-head).planar().length()>table.radius*2) head else V3(table.length/2,table.width/2))
        }
        if(cueIn) {
            if(survivor==null || survivor.p.x>=table.headLine) {
                match.ballInHand=true; match.handRegion=HandRegion.HEAD
                respot(cueId,V3(table.headLine-table.radius*3,table.width/2))
            } else respot(cueId,if((survivor.p-table.headSpot).planar().length()>table.radius*2) table.headSpot else V3(table.length/2,table.width/2))
        }
        val rackBalls=world.balls.filter { it.id!=cueId && it.id!=survivor?.id }.sortedBy { it.id }
        rackBalls.forEach { it.motion=Motion.POCKETED }
        val slots=if(survivor==null) positions else positions.drop(1)
        rackBalls.zip(slots).forEach { (ball,position) ->
            ball.p=position; ball.motion=Motion.STILL; ball.stop()
        }
    }
    fun setupDrill(index: Int, resetScore: Boolean = true) {
        if(world.moving) return
        drill=((index%8)+8)%8
        val l=table.length; val w=table.width
        val arrangements=listOf(
            listOf(V3(l*.22,w*.5),V3(l*.58,w*.5),V3(l*.75,w*.64)),
            listOf(V3(l*.25,w*.35),V3(l*.60,w*.55),V3(l*.36,w*.78)),
            listOf(V3(l*.24,w*.20),V3(l*.72,w*.70),V3(l*.40,w*.35)),
            listOf(V3(l*.30,w*.30),V3(l*.68,w*.65),V3(l*.53,w*.42)),
            listOf(V3(l*.25,w*.50),V3(l*.50,w*.50),V3(l*.19,w*.55)),
            listOf(V3(l*.30,w*.50),V3(l*.48,w*.50),V3(l*.70,w*.52)),
            listOf(V3(l*.40,w*.35),V3(l*.58,w*.48),V3(l*.42,w*.70)),
            listOf(V3(l*.32,w*.55),V3(l*.55,w*.52),V3(l*.47,w*.60)))
        world=BilliardWorld(table,arrangements[drill].mapIndexed { id,p -> table.ball(id,p.x,p.y) }.toMutableList(),trackVisualRotation=trackVisualRotation)
        cueId=0; if(resetScore) { match=MatchState(breaking=false); before=null; lastShot=null }
    }
}
