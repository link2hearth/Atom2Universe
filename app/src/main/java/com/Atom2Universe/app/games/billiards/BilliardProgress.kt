package com.Atom2Universe.app.games.billiards

import android.content.Context
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.billiards.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Profile, result receipts and pending rewards are committed together. Table saves are independent. */
internal class BilliardProgress(context: Context) {
    private val prefs=context.getSharedPreferences("billiards-progress-v1",Context.MODE_PRIVATE)
    private val neutrinos=NeutrinoRepository(context)
    private var data=runCatching { JSONObject(prefs.getString("profile","{}") ?: "{}") }.getOrElse { JSONObject() }
    val careerStep get()=data.optInt("career").coerceIn(0,20)
    fun medal(id: Int)=data.optInt("medal_$id")
    fun lesson(id: Int)=data.optBoolean("lesson_$id")
    fun trophy(key: String)=data.optBoolean("trophy_$key")
    fun record(key: String)=data.optInt("record_$key")
    val clubCount get()=(careerStep/4).coerceIn(0,5)
    val medalCount get()=(0..23).sumOf(::medal)
    fun stats(d: Discipline)=data.optJSONObject("stats_${d.name}") ?: JSONObject()
    fun finish(run: BilliardRun,config: BilliardConfig): Int {
        if(run.stage!=JourneyStage.FINISHED) return 0
        val processed=JSONArray((data.optJSONArray("processed") ?: JSONArray()).toString())
        if((0 until processed.length()).any { processed.getString(it)==run.id }) { deliverRewards(); return 0 }
        // Keep the in-memory profile unchanged if a write fails.
        val next=JSONObject(data.toString())
        val awards=next.optJSONObject("awards") ?: JSONObject()
        var total=0
        fun award(key: String,amount: Int) { if(!awards.has(key)) { awards.put(key,amount); total+=amount } }
        if(run.mode==BilliardActivityMode.CHALLENGE && run.medal>medal(run.exercise)) {
            for(level in medal(run.exercise)+1..run.medal) award("medal:${run.exercise}:$level",NeutrinoRewards.BILLIARD_MEDAL)
            next.put("medal_${run.exercise}",run.medal)
        }
        if(run.mode==BilliardActivityMode.LESSON && run.medal>0) {
            next.put("lesson_${run.exercise}",true); award("lesson:${run.exercise}",NeutrinoRewards.BILLIARD_LESSON)
        }
        if(run.mode==BilliardActivityMode.CAREER && run.champion && run.careerStep==careerStep) {
            next.put("career",careerStep+1)
            if((careerStep+1)%4==0) {
                val club=careerStep/4; next.put("trophy_club_$club",true)
                award("club:$club",NeutrinoRewards.BILLIARD_CLUB)
            }
        }
        if(run.mode in listOf(BilliardActivityMode.CUP,BilliardActivityMode.LEAGUE) && run.champion) {
            val key="${run.mode.name}_${config.discipline.name}"
            next.put("trophy_$key",true)
            award(key,if(run.mode==BilliardActivityMode.CUP) NeutrinoRewards.BILLIARD_CUP else NeutrinoRewards.BILLIARD_LEAGUE)
        }
        val recordKey="${run.mode.name}_${config.discipline.name}_${config.style.name}_${config.assistance}"
        next.put("record_$recordKey",maxOf(record(recordKey),run.score))
        val stats=JSONObject(stats(config.discipline).toString())
        stats.put("played",stats.optInt("played")+1).put("won",stats.optInt("won")+if(run.champion) 1 else 0)
            .put("shots",stats.optInt("shots")+run.shots).put("points",stats.optInt("points")+run.score)
        next.put("stats_${config.discipline.name}",stats)
        processed.put(run.id); next.put("processed",processed).put("awards",awards)
        check(prefs.edit().putString("profile",next.toString()).commit())
        data=next; deliverRewards(); return total
    }
    fun deliverRewards() {
        val awards=data.optJSONObject("awards") ?: return
        awards.keys().forEach { key -> neutrinos.addBalanceOnce("billiards:$key",awards.getInt(key)) }
    }
}
