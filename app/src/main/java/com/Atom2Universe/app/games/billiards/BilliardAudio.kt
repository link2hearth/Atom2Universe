package com.Atom2Universe.app.games.billiards

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.Atom2Universe.app.games.billiards.core.*
import kotlin.math.*
import kotlin.random.Random

/** Recorded samples from assets/billiards/sounds (file name prefix = sound family: queue, choc, bande, poche, casse). */
internal class BilliardAudio(context: Context) {
    private val pool=SoundPool.Builder().setMaxStreams(10).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val samples=mutableMapOf<String,Int>()
    @Volatile private var closed=false
    var enabled=true
        set(value) { field=value; if(!closed) { if(value && active) pool.autoResume() else pool.autoPause() } }
    private var active=false
    private var lastImpact=0L
    private var quietBallsUntil=0L
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0 && !closed) loaded+=id }
        context.assets.list("billiards/sounds")?.sorted()?.forEach { name ->
            runCatching { context.assets.openFd("billiards/sounds/$name").use { pool.load(it,1) } }.onSuccess { samples[name.substringBeforeLast('.')]=it }
        }
    }
    fun resume() { active=true; if(!closed && enabled) pool.autoResume() }
    fun pause() { active=false; if(!closed) pool.autoPause() }
    fun strike(speed: Double,rackBreak: Boolean=false) {
        if(rackBreak && sample("casse",.9f,.5)) { quietBallsUntil=android.os.SystemClock.uptimeMillis()+300; return }
        sample(if(speed<3.0) "queue_1" else "queue_3",.35f+.65f*(speed/8).coerceIn(0.0,1.0).toFloat(),.5)
    }
    fun event(e: BilliardEvent,world: BilliardWorld) {
        if(e.kind in listOf(EventKind.CROSS_LINE,EventKind.HEAD_CROSS,EventKind.REGION_EXIT)) return
        if(e.kind==EventKind.BALL && e.strength<.035) return
        val now=android.os.SystemClock.uptimeMillis()
        if(e.kind==EventKind.BALL && now-lastImpact<8) return
        if(e.kind==EventKind.BALL) lastImpact=now
        if(e.kind==EventKind.BALL && now<quietBallsUntil) return
        val x=world.balls.firstOrNull { it.id==e.ball }?.p?.x ?: world.table.length/2
        val family=when(e.kind) { EventKind.BALL,EventKind.PIN->"choc"; EventKind.POCKET->"poche"; else->"bande" }
        sample(family,(.2+.8*sqrt(e.strength.coerceIn(0.0,8.0)/8)).toFloat(),x/world.table.length)
    }
    /** Plays a random loaded sample whose file name starts with [prefix]; false if nothing was played. */
    private fun sample(prefix: String,volume: Float,pan: Double): Boolean {
        if(closed || !active || !enabled) return false
        val id=samples.filterKeys { it.startsWith(prefix) }.values.filter { it in loaded }.takeIf { it.isNotEmpty() }?.random() ?: return false
        val p=pan.coerceIn(0.0,1.0).toFloat()
        pool.play(id,volume*sqrt(1-p*.35f),volume*sqrt(.65f+p*.35f),1,0,Random.nextDouble(.97,1.03).toFloat())
        return true
    }
    fun close() { closed=true; pool.release() }
}
