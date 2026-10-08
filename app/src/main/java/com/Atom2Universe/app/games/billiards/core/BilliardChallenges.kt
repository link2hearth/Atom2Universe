package com.Atom2Universe.app.games.billiards.core

import kotlin.math.*

enum class BilliardGoal { CLEAR, BANK, ORDER, DRAW, FOLLOW, CAROM }

data class BilliardExercise(val id: Int) {
    val goal get()=BilliardGoal.entries[id/4]
    val level get()=id%4
    val discipline get()=if(goal==BilliardGoal.CAROM) Discipline.FREE else Discipline.NINE
    val count get()=when(goal) { BilliardGoal.CLEAR -> level+1; BilliardGoal.ORDER -> 3; else -> 1 }
    val maxShots get()=when(goal) { BilliardGoal.CLEAR -> count+2; BilliardGoal.ORDER -> 5; else -> 3 }
    val requiredRails get()=level
    fun zone(table: BilliardTable): Pair<V3,Double>? = when(goal) {
        BilliardGoal.DRAW -> V3(table.length*(.35-level*.035),table.width*(.65+level*.035)) to (.23-level*.025)
        BilliardGoal.FOLLOW -> V3(table.length*(.78+level*.025),table.width*(.22-level*.025)) to (.20-level*.025)
        else -> null
    }
    fun install(s: BilliardSession) {
        val t=s.table; val l=t.length; val w=t.width
        fun ball(id: Int,x: Double,y: Double)=t.ball(id,l*x,w*y)
        val balls=when(goal) {
            BilliardGoal.CLEAR, BilliardGoal.ORDER -> buildList {
                add(ball(0,.27,.55))
                val positions=listOf(.50 to .22,.78 to .18,.78 to .80,.24 to .80)
                repeat(count) { i ->
                    val (x,y)=positions[(i+if(goal==BilliardGoal.ORDER) level else 0)%positions.size]
                    add(ball(i+1,x,y))
                }
            }
            BilliardGoal.BANK -> listOf(ball(0,.25+level*.03,.65),ball(1,.50,.30))
            BilliardGoal.DRAW, BilliardGoal.FOLLOW -> {
                // Both balls are aligned with the far top corner; draw/follow control the white afterwards.
                listOf(ball(0,.40,.60),ball(1,.70,.30))
            }
            BilliardGoal.CAROM -> listOf(ball(0,.25,.50),ball(1,.57,.48),ball(2,.78,.65-level*.06))
        }
        s.restore(SessionSnapshot(balls,emptyList(),MatchState(breaking=false,shots=s.match.shots),0,0))
    }
    /** Feedback codes: 1 scratch, 2 no contact, 3 order, 4 band, 5 zone, 6 carom, 7 miss. */
    fun assess(s: BilliardSession,run: BilliardRun): Pair<Int,Int> {
        val events=s.world.events
        if(events.any { it.kind==EventKind.OFF_TABLE || it.kind==EventKind.POCKET && it.ball==0 }) return 0 to 1
        val hits=events.filter { it.kind==EventKind.BALL && (it.ball==0 || it.other==0) }
        if(hits.isEmpty()) return 0 to 2
        val pots=events.filter { it.kind==EventKind.POCKET && it.ball!=0 }
        return when(goal) {
            BilliardGoal.CLEAR -> pots.size to if(pots.isEmpty()) 7 else 0
            BilliardGoal.ORDER -> {
                val first=hits.first().let { if(it.ball==0) it.other else it.ball }
                val expected=run.score+1
                if(first!=expected || pots.map { it.ball }!=(expected until expected+pots.size).toList()) 0 to 3
                else pots.size to if(pots.isEmpty()) 7 else 0
            }
            BilliardGoal.BANK -> {
                val pot=pots.firstOrNull()
                val bank=pot!=null && events.any { it.kind==EventKind.CUSHION && it.ball==pot.ball && it.time<pot.time }
                if(bank) 1 to 0 else 0 to 4
            }
            BilliardGoal.DRAW, BilliardGoal.FOLLOW -> {
                val zone=zone(s.table)!!
                val inZone=s.cue?.let { (it.p-zone.first).planar().length()<=zone.second }==true
                val spin=s.lastShot?.top ?: 0.0
                if(pots.isNotEmpty() && inZone && (if(goal==BilliardGoal.DRAW) spin<-.1 else spin>.1)) 1 to 0 else 0 to 5
            }
            BilliardGoal.CAROM -> {
                val ids=hits.map { if(it.ball==0) it.other else it.ball }.distinct()
                val second=hits.firstOrNull { ids.size>=2 && (if(it.ball==0) it.other else it.ball)==ids[1] }
                val rails=events.count { it.kind==EventKind.CUSHION && it.ball==0 && second!=null && it.time<second.time }
                if(ids.size>=2 && rails>=requiredRails) 1 to 0 else 0 to 6
            }
        }
    }
    fun medal(shots: Int)=when { shots<=count -> 3; shots<=count+1 -> 2; else -> 1 }
}

object BilliardChallenges {
    val all=List(24) { BilliardExercise(it) }
    val lessons=listOf(0,4,8,12,16,20)
    fun spread(s: BilliardSession, index: Int) {
        val t=s.table
        val balls=mutableListOf(t.ball(0,t.length*.25,t.width*.5))
        val positions=listOf(.50 to .22,.77 to .20,.78 to .78,.25 to .78,.50 to .76,.21 to .20)
        positions.forEachIndexed { i,(x,y) ->
            val flip=index%2==1
            balls+=t.ball(i+1,t.length*(if(flip) 1-x else x),t.width*y)
        }
        s.restore(SessionSnapshot(balls,emptyList(),MatchState(breaking=false,shots=s.match.shots),0,0))
    }
}
