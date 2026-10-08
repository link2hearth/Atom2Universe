package com.Atom2Universe.app.games.toyboxracers

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.Atom2Universe.app.games.toyboxracers.game.PlayMode
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sin

/** Original toy-motor and race cues. PCM preparation never blocks the UI or GL thread. */
internal class ToyboxAudio(context: Context) {
    private enum class Cue { MOTOR, COUNT, GO, BOOST, LAND, FINISH }
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor { task -> Thread(task,"Toybox audio").apply { isDaemon=true } }
    private val cache=context.cacheDir
    private val sounds=HashMap<Cue,Int>()
    private val loaded=HashSet<Int>()
    private val pendingFiles=HashMap<Int,File>()
    private val effects=ArrayDeque<Int>()
    private val pool=runCatching {
        SoundPool.Builder().setMaxStreams(5).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    }.getOrNull()
    @Volatile private var released=false
    private var active=false
    private var motor=0
    private var last: ToyboxRacersRenderer.HudState?=null
    var enabled=true
        set(value) { field=value; if(!value) silence() }

    init {
        pool?.setOnLoadCompleteListener { _,id,status ->
            pendingFiles.remove(id)?.delete()
            if(!released && status==0) loaded+=id
        }
        if(pool!=null) worker.execute {
            for(cue in Cue.entries) {
                if(released || Thread.currentThread().isInterrupted) break
                val file=runCatching {
                    File.createTempFile("toybox_audio_",".wav",cache).also { it.writeBytes(synthesize(cue)) }
                }.getOrNull() ?: continue
                handler.post {
                    if(released) file.delete()
                    else {
                        val id=runCatching { pool?.load(file.absolutePath,1) ?: 0 }.getOrDefault(0)
                        if(id!=0) { sounds[cue]=id; pendingFiles[id]=file } else file.delete()
                    }
                }
            }
        }
    }

    fun resume() { if(!released) { active=true; last=null } }
    fun pause() { active=false; last=null; silence() }

    private fun silence() {
        // Stop instead of suspending: old countdowns must never consume a stream in the next race.
        effects.forEach { pool?.stop(it) }
        effects.clear()
        if(motor!=0) pool?.stop(motor)
        motor=0
    }

    fun update(state: ToyboxRacersRenderer.HudState) {
        if(!active || !enabled || released || pool==null) return
        val previous=last
        last=state
        val driving=(state.mode==PlayMode.EXPLORATION || state.racePhase==RacePhase.RACING) && !state.rescuing
        if(driving) {
            val id=sounds[Cue.MOTOR]
            if(motor==0 && id!=null && id in loaded) motor=pool.play(id,.04f,.04f,2,-1,.7f)
            if(motor!=0) {
                val speed=(state.speedKmh/420f).coerceIn(0f,1f)
                val volume=(.028f+speed*.095f)*(if(state.airborne) .55f else 1f)
                pool.setRate(motor,(.68f+speed*1.15f+if(state.turboBoosting) .12f else 0f).coerceAtMost(2f))
                pool.setVolume(motor,volume,volume)
            }
        } else if(motor!=0) { pool.stop(motor); motor=0 }
        if(previous==null || previous.scene!=state.scene || previous.mode!=state.mode) return
        if(state.mode==PlayMode.RACE) {
            if(state.racePhase==RacePhase.COUNTDOWN && previous.racePhase==RacePhase.COUNTDOWN &&
                ceil(previous.countdownSeconds)!=ceil(state.countdownSeconds)) play(Cue.COUNT,.24f)
            if(previous.racePhase==RacePhase.COUNTDOWN && state.racePhase==RacePhase.RACING) play(Cue.GO,.3f)
            if(previous.finishSerial!=state.finishSerial && state.racePhase==RacePhase.FINISHED) play(Cue.FINISH,.28f)
        }
        if(driving && previous.turboReleaseSerial!=state.turboReleaseSerial) play(Cue.BOOST,.22f)
        if(driving && previous.airborne && !state.airborne && !previous.rescuing) play(Cue.LAND,.16f)
    }

    private fun play(cue: Cue,volume: Float) {
        val id=sounds[cue] ?: return
        if(id in loaded) {
            if(effects.size>=4) pool?.stop(effects.removeFirst())
            val stream=pool?.play(id,volume,volume,1,0,1f) ?: 0
            if(stream!=0) effects.addLast(stream)
        }
    }

    fun release() {
        if(released) return
        released=true
        pause()
        worker.shutdownNow()
        pool?.setOnLoadCompleteListener(null)
        pool?.release()
        pendingFiles.values.forEach { it.delete() }
        pendingFiles.clear()
        sounds.clear()
        loaded.clear()
    }

    private fun synthesize(cue: Cue): ByteArray {
        val rate=22050
        val seconds=when(cue) { Cue.MOTOR -> .5; Cue.COUNT -> .12; Cue.GO -> .32
            Cue.BOOST -> .42; Cue.LAND -> .14; Cue.FINISH -> .92 }
        val count=(rate*seconds).toInt()
        val data=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36+count*2)
        data.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
        data.putInt(rate).putInt(rate*2).putShort(2).putShort(16)
        data.put("data".toByteArray(Charsets.US_ASCII)).putInt(count*2)
        val finishNotes=doubleArrayOf(523.25,659.25,783.99,1046.50)
        for(i in 0 until count) {
            val t=i.toDouble()/rate
            val envelope=if(cue==Cue.MOTOR) 1.0 else minOf(1.0,t/.012,(seconds-t)/.025).coerceAtLeast(0.0)
            val value=when(cue) {
                Cue.MOTOR -> .4*sin(2*PI*88*t)+.16*sin(2*PI*176*t)+.06*sin(2*PI*352*t)
                Cue.COUNT -> .55*sin(2*PI*660*t)
                Cue.GO -> .42*sin(2*PI*990*t)+.18*sin(2*PI*1320*t)
                Cue.BOOST -> (.4*sin(2*PI*(180*t+750*t*t))+.12*sin(2*PI*(350*t+1250*t*t)))*(1-t/seconds)
                Cue.LAND -> .7*sin(2*PI*(95*t-110*t*t))*exp(-t*25)
                Cue.FINISH -> {
                    val step=(t/.23).toInt().coerceAtMost(3)
                    val local=t-step*.23
                    (.45*sin(2*PI*finishNotes[step]*local)+.12*sin(4*PI*finishNotes[step]*local))*
                        minOf(1.0,local/.008,(.23-local)/.025).coerceAtLeast(0.0)
                }
            }
            data.putShort((value*envelope*28000).toInt().coerceIn(-32768,32767).toShort())
        }
        return data.array()
    }
}
