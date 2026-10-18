package com.Atom2Universe.app.games.billiards.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

enum class BilliardActivityMode { QUICK, CAREER, CUP, LEAGUE, CHALLENGE, LESSON, SERIES, TIMED }
enum class JourneyStage { PLAYING, RACK_RESULT, MATCH_RESULT, FINISHED }

/** The event survives table recreation. Results are applied once, before displaying them. */
data class BilliardRun(
    val id: String = UUID.randomUUID().toString(),
    val mode: BilliardActivityMode = BilliardActivityMode.QUICK,
    val raceTo: Int = 1,
    val careerStep: Int = -1,
    val exercise: Int = -1,
    var round: Int = 0,
    var racks0: Int = 0,
    var racks1: Int = 0,
    var rackNumber: Int = 0,
    var stage: JourneyStage = JourneyStage.PLAYING,
    var lastWinner: Int = -1,
    var shots: Int = 0,
    var handledShot: Int = 0,
    var score: Int = 0,
    var errors: Int = 0,
    var remainingMillis: Long = 120_000L,
    var medal: Int = 0,
    var feedback: Int = 0,
    var roster: List<Int> = (0..7).toList(),
    var points: List<Int> = List(8) { 0 },
    var history: List<String> = emptyList(),
    var lastOpponent: Int = 1
) {
    val solo get() = exercise >= 0 || mode in listOf(BilliardActivityMode.SERIES,BilliardActivityMode.TIMED)
    val competitive get() = mode != BilliardActivityMode.QUICK
    val opponent get() = when(mode) {
        BilliardActivityMode.CUP -> roster.getOrElse(roster.indexOf(0) xor 1) { 1 }
        BilliardActivityMode.LEAGUE -> leaguePairs(round).firstOrNull { 0 in it }?.firstOrNull { it!=0 } ?: 1
        BilliardActivityMode.CAREER -> 1+(careerStep/4*2+careerStep%4)%7
        else -> 1
    }
    val displayedOpponent get()=if(stage==JourneyStage.PLAYING) opponent else lastOpponent
    val leagueOrder get() = (0..7).sortedWith(compareByDescending<Int> { points[it] }.thenBy { it })
    val champion get() = when(mode) {
        BilliardActivityMode.CUP -> roster==listOf(0)
        BilliardActivityMode.LEAGUE -> points[0]==points.maxOrNull()
        else -> lastWinner==0 || medal>0
    }
    fun finishRack(winner: Int) {
        if(stage!=JourneyStage.PLAYING) return
        lastOpponent=opponent
        lastWinner=winner
        if(winner==0) racks0++ else racks1++
        stage=if(maxOf(racks0,racks1)<raceTo) JourneyStage.RACK_RESULT else JourneyStage.MATCH_RESULT
        if(stage==JourneyStage.MATCH_RESULT) {
            val playerWon=racks0>=raceTo
            val rng=Random(id.hashCode()+round*101)
            when(mode) {
                BilliardActivityMode.CUP -> {
                    val next=mutableListOf<Int>()
                    roster.chunked(2).forEach { pair ->
                        val won=if(0 in pair) if(playerWon) 0 else pair.first { it!=0 }
                            else if(rng.nextInt(100)<50+(pair[0]-pair[1])*3) pair[0] else pair[1]
                        next+=won; history=history+"${round},${pair[0]},${pair[1]},$won"
                    }
                    roster=next
                    if(!playerWon || roster.size==1) stage=JourneyStage.FINISHED
                }
                BilliardActivityMode.LEAGUE -> {
                    val next=points.toMutableList()
                    leaguePairs(round).forEach { pair ->
                        val won=if(0 in pair) if(playerWon) 0 else pair.first { it!=0 }
                            else if(rng.nextInt(100)<50+(pair[0]-pair[1])*3) pair[0] else pair[1]
                        next[won]+=3; history=history+"${round},${pair[0]},${pair[1]},$won"
                    }
                    points=next
                    if(round>=6) stage=JourneyStage.FINISHED
                }
                else -> stage=JourneyStage.FINISHED
            }
        }
    }
    fun nextRack() {
        require(stage==JourneyStage.RACK_RESULT || stage==JourneyStage.MATCH_RESULT)
        if(stage==JourneyStage.MATCH_RESULT) { round++; racks0=0; racks1=0 }
        rackNumber++; handledShot=0; stage=JourneyStage.PLAYING
    }
    fun toJson()=JSONObject().put("id",id).put("mode",mode.name).put("race",raceTo)
        .put("career",careerStep).put("exercise",exercise).put("round",round).put("r0",racks0).put("r1",racks1)
        .put("rack",rackNumber).put("stage",stage.name).put("winner",lastWinner).put("shots",shots)
        .put("handled",handledShot).put("score",score).put("errors",errors).put("time",remainingMillis)
        .put("medal",medal).put("feedback",feedback).put("roster",JSONArray(roster)).put("points",JSONArray(points)).put("history",JSONArray(history)).put("lastOpponent",lastOpponent)
    companion object {
        fun fromJson(j: JSONObject): BilliardRun {
            fun ints(key: String)=j.getJSONArray(key).let { a -> List(a.length()) { a.getInt(it) } }
            return BilliardRun(j.getString("id"),enumValueOf(j.getString("mode")),j.getInt("race").coerceIn(1,3),
                j.getInt("career"),j.getInt("exercise"),j.getInt("round"),j.getInt("r0"),j.getInt("r1"),j.getInt("rack"),
                enumValueOf(j.getString("stage")),j.getInt("winner"),j.getInt("shots"),j.getInt("handled"),j.getInt("score"),
                j.getInt("errors"),j.getLong("time").coerceAtLeast(0),j.getInt("medal"),j.optInt("feedback"),ints("roster"),ints("points"),
                j.optJSONArray("history")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),j.optInt("lastOpponent",1)).also {
                require(it.careerStep in -1..19 && it.exercise in -1..23 && it.round in 0..6)
                require(it.points.size==8 && it.points.all { n -> n>=0 })
                require(it.roster.size in 1..8 && it.roster.distinct()==it.roster && it.roster.all { n -> n in 0..7 })
                require(it.shots>=0 && it.handledShot>=0 && it.score>=0 && it.errors>=0)
            }
        }
        fun leaguePairs(round: Int): List<List<Int>> {
            val circle=(0..7).toMutableList()
            repeat(round.coerceIn(0,6)) { circle.add(1,circle.removeAt(7)) }
            return List(4) { listOf(circle[it],circle[7-it]) }
        }
    }
}

/** Monotonic decision time; pausing never transfers a paused interval to the next tick. */
class BilliardDecisionClock {
    private var previous: Long? = null
    fun step(now: Long, playable: Boolean): Long {
        val old=previous
        previous=if(playable) now else null
        return if(playable && old!=null) (now-old).coerceAtLeast(0) else 0
    }
    fun pause() { previous=null }
}
