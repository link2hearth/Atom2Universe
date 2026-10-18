package com.Atom2Universe.app.games.golf.classic

import com.Atom2Universe.app.games.golf.classic.core.*
import java.io.*
import java.util.UUID

data class GolfReplaySample(val time: Float, val ball: GolfPoint, val clock: Float)

data class GolfReplay(
    val id: String = UUID.randomUUID().toString(),
    val created: Long = System.currentTimeMillis(),
    val name: String = "",
    val course: String,
    val hole: Int,
    val club: GolfClub,
    val aim: Float,
    val stroke: Int,
    val easy: Boolean,
    val holed: Boolean = false,
    val samples: List<GolfReplaySample>,
) {
    val duration get() = samples.last().time

    /** Binary search keeps scrubbing equally cheap at either end of a long shot. */
    fun sample(seconds: Float): GolfReplaySample {
        val t = seconds.coerceIn(0f, duration)
        val found = samples.binarySearchBy(t) { it.time }
        if (found >= 0) return samples[found]
        val upper = (-found-1).coerceIn(1,samples.lastIndex)
        val a = samples[upper-1]; val b = samples[upper]
        val blend = (t-a.time)/(b.time-a.time)
        fun mix(x: Float,y: Float) = x+(y-x)*blend
        return GolfReplaySample(t,GolfPoint(mix(a.ball.x,b.ball.x),mix(a.ball.y,b.ball.y),mix(a.ball.z,b.ball.z)),mix(a.clock,b.clock))
    }
}

/** One bounded recording in memory; no physics is run when it is replayed. */
class GolfReplayRecorder(val template: GolfReplay) {
    private val samples = ArrayList<GolfReplaySample>().apply { addAll(template.samples) }
    var seconds = 0f
        private set
    private var overflow = false
    fun append(dt: Float, ball: GolfPoint, clock: Float) {
        if (dt <= 0f || overflow) return
        seconds += dt
        if (samples.size >= MAX_SAMPLES) { overflow = true; return }
        samples += GolfReplaySample(seconds,ball,clock)
    }
    fun finish(holed: Boolean): GolfReplay? = if (overflow || samples.size < 2) null
        else template.copy(holed=holed,samples=samples.toList())

    companion object { const val MAX_SAMPLES = 24000 }
}

/** Each favourite is independently replaceable; an interrupted write leaves existing shots intact. */
class GolfReplayStore(private val directory: File) {
    data class Entry(val id: String, val name: String, val course: String, val hole: Int, val created: Long)
    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(directory,"$id.golf")
    }
    fun entries(): List<Entry> = directory.listFiles { f -> f.extension == "golf" }.orEmpty().mapNotNull { f ->
        runCatching { DataInputStream(BufferedInputStream(FileInputStream(f))).use { input ->
            check(input.readInt()==VERSION)
            val id=input.readUTF();file(id)
            check(f.name=="$id.golf")
            val created=input.readLong();val name=input.readUTF();val course=input.readUTF();val hole=input.readInt()
            Entry(id,name,course,hole,created)
        } }.getOrNull()
    }.sortedByDescending { it.created }

    fun save(replay: GolfReplay) {
        require(replay.samples.size in 2..GolfReplayRecorder.MAX_SAMPLES)
        check(directory.isDirectory || directory.mkdirs())
        val destination=file(replay.id)
        if(destination.exists()) return // Saving the same shot twice is idempotent.
        val temporary=File(directory,"${replay.id}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                val out=DataOutputStream(BufferedOutputStream(stream))
                out.writeInt(VERSION);out.writeUTF(replay.id);out.writeLong(replay.created)
                out.writeUTF(replay.name.take(80));out.writeUTF(replay.course);out.writeInt(replay.hole)
                out.writeUTF(replay.club.name);out.writeFloat(replay.aim);out.writeInt(replay.stroke)
                out.writeBoolean(replay.easy);out.writeBoolean(replay.holed);out.writeInt(replay.samples.size)
                replay.samples.forEach { s ->
                    out.writeFloat(s.time);out.writeFloat(s.ball.x);out.writeFloat(s.ball.y);out.writeFloat(s.ball.z);out.writeFloat(s.clock)
                }
                out.flush();stream.fd.sync()
            }
            check(temporary.renameTo(destination))
        } finally { temporary.delete() }
    }

    fun load(id: String): GolfReplay = DataInputStream(BufferedInputStream(FileInputStream(file(id)))).use { input ->
        check(input.readInt()==VERSION);check(input.readUTF()==id)
        val created=input.readLong();val name=input.readUTF();val course=input.readUTF();val hole=input.readInt()
        val club=GolfClub.valueOf(input.readUTF());val aim=input.readFloat();val stroke=input.readInt()
        val easy=input.readBoolean();val holed=input.readBoolean();val count=input.readInt()
        require(count in 2..GolfReplayRecorder.MAX_SAMPLES && aim.isFinite())
        var previous=-1f
        val samples=List(count) {
            val t=input.readFloat();val x=input.readFloat();val y=input.readFloat();val z=input.readFloat();val clock=input.readFloat()
            require(listOf(t,x,y,z,clock).all { it.isFinite() } && t>previous && t>=0f)
            previous=t
            GolfReplaySample(t,GolfPoint(x,y,z),clock)
        }
        require(samples.first().time==0f)
        GolfReplay(id,created,name,course,hole,club,aim,stroke,easy,holed,samples)
    }
    fun delete(id: String) { check(file(id).delete()) }
    companion object { private const val VERSION=1 }
}
