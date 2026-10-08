package com.Atom2Universe.app.games.billiards

import android.content.Context
import android.util.AtomicFile
import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.render.BilliardCameraMode
import com.Atom2Universe.app.games.billiards.render.BilliardCameraState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal data class SavedTable(val config: BilliardConfig,val session: BilliardSession,val shot: Shot,val run: BilliardRun? = null,
    val camera: BilliardCameraState? = null,val cameraViews: Map<BilliardCameraMode,BilliardCameraState> = emptyMap())

/** Atomic write includes a moving shot's collision log and pre-shot state. */
internal class BilliardStore(context: Context,private val failed: (Exception)->Unit = {}) {
    private val file=AtomicFile(File(context.filesDir,"billiards-table-v1.json"))
    // The disk sync behind AtomicFile can block for tens of milliseconds: the table is
    // encoded where it changes, on the interface thread, and written on this one.
    private val writer=Executors.newSingleThreadExecutor()
    private val latest=AtomicReference<ByteArray?>()
    private val queued=AtomicInteger()
    val exists get() = queued.get()>0 || file.baseFile.exists()
    fun save(config: BilliardConfig,s: BilliardSession,shot: Shot,run: BilliardRun? = null,camera: BilliardCameraState? = null,
        cameraViews: Map<BilliardCameraMode,BilliardCameraState> = emptyMap()) {
        val bytes=encode(config,s,shot,run,camera,cameraViews)
        queued.incrementAndGet(); latest.set(bytes)
        writer.execute {
            // Saves that piled up while the disk was busy collapse into the newest one.
            try { latest.getAndSet(null)?.let(::write) }
            catch(e: Exception) { failed(e) }
            finally { queued.decrementAndGet() }
        }
    }
    /** Waits for the queued writes: before leaving the screen, and before reading back. */
    fun flush() { runCatching { writer.submit {}.get() } }
    fun close() { writer.shutdown() }
    private fun write(bytes: ByteArray) {
        val stream=file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch(e: Exception) { file.failWrite(stream); throw e }
    }
    private fun encode(config: BilliardConfig,s: BilliardSession,shot: Shot,run: BilliardRun?,camera: BilliardCameraState?,
        cameraViews: Map<BilliardCameraMode,BilliardCameraState>): ByteArray {
        val current=s.persistentSnapshot()
        val root=JSONObject().put("version",1).put("config",configJson(config)).put("current",snapshot(current))
            .put("shot",shotJson(shot)).put("active",s.isShotActive).put("nomination",current.nominated).put("pocket",current.pocket)
        run?.let { root.put("journey",it.toJson()) }
        camera?.let { root.put("camera",cameraJson(it)) }
        root.put("cameraViews",JSONArray().apply { cameraViews.values.forEach { put(cameraJson(it)) } })
        s.before?.let { root.put("before",snapshot(it)) }; s.lastShot?.let { root.put("lastShot",shotJson(it)) }
        root.put("events",JSONArray().apply { if(!s.replaying) s.world.events.forEach { e -> put(JSONObject()
            .put("kind",e.kind.name).put("ball",e.ball).put("other",e.other).put("strength",e.strength).put("time",e.time)
            .apply { e.position?.let { put("position",vec(it)) } }) } })
        return root.toString().toByteArray(Charsets.UTF_8)
    }
    fun load(): SavedTable {
        flush()
        val json=JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(json.getInt("version")==1)
        val c=readConfig(json.getJSONObject("config")); val s=BilliardSession(c.discipline,c.mode,c.cloth,c.tableSize)
        s.restore(readSnapshot(json.getJSONObject("current")))
        val before=json.optJSONObject("before")?.let(::readSnapshot)
        val last=json.optJSONObject("lastShot")?.let(::readShot)
        val events=json.getJSONArray("events").let { a -> (0 until a.length()).map { i ->
            val e=a.getJSONObject(i); BilliardEvent(enumValueOf(e.getString("kind")),e.getInt("ball"),e.getInt("other"),e.getDouble("strength"),e.getDouble("time"),e.optJSONArray("position")?.let(::readVec)) } }
        s.recoverHistory(before,last,events,json.getBoolean("active")); s.nominated=json.optInt("nomination",-1); s.calledPocket=json.optInt("pocket",-1)
        val camera=json.optJSONObject("camera")?.let { runCatching { readCamera(it) }.getOrNull() }
        val cameraViews=mutableMapOf<BilliardCameraMode,BilliardCameraState>()
        json.optJSONArray("cameraViews")?.let { views ->
            repeat(minOf(views.length(),BilliardCameraMode.entries.size)) { index ->
                runCatching { readCamera(views.getJSONObject(index)) }.getOrNull()?.let { cameraViews[it.mode]=it }
            }
        }
        // Old tables contain only the active camera; it becomes the first remembered slot.
        camera?.let { cameraViews[it.mode]=it }
        return SavedTable(c,s,readShot(json.getJSONObject("shot")),json.optJSONObject("journey")?.let(BilliardRun::fromJson),camera,cameraViews)
    }
    private fun cameraJson(camera: BilliardCameraState)=JSONObject().put("mode",camera.mode.name).put("yaw",camera.yaw)
        .put("pitch",camera.pitch).put("zoom",camera.zoom).put("x",camera.x).put("z",camera.z)
    private fun readCamera(j: JSONObject)=BilliardCameraState(enumValueOf<BilliardCameraMode>(j.getString("mode")),
        j.getDouble("yaw").toFloat(),j.getDouble("pitch").toFloat(),j.getDouble("zoom").toFloat(),j.getDouble("x").toFloat(),j.getDouble("z").toFloat()).also {
        require(listOf(it.yaw,it.pitch,it.zoom,it.x,it.z).all(Float::isFinite) && it.zoom in .45f..12f)
    }
    private fun vec(v: V3)=JSONArray(listOf(v.x,v.y,v.z))
    private fun readVec(a: JSONArray): V3 = V3(a.getDouble(0),a.getDouble(1),a.getDouble(2)).also {
        require(it.x.isFinite() && it.y.isFinite() && it.z.isFinite())
    }
    private fun shotJson(s: Shot)=JSONObject().put("angle",s.angle).put("speed",s.speed).put("side",s.side).put("top",s.top)
        .put("elevation",s.elevation).put("mass",s.cueMass).put("end",s.endMass)
    private fun readShot(j: JSONObject)=Shot(j.getDouble("angle"),j.getDouble("speed"),j.getDouble("side"),j.getDouble("top"),j.getDouble("elevation"),j.getDouble("mass"),j.getDouble("end"))
    private fun configJson(c: BilliardConfig)=JSONObject().put("discipline",c.discipline.name).put("room",c.room.name)
        .put("cue",c.cue.name).put("mode",c.mode.name).put("cloth",c.cloth).put("assistance",c.assistance).put("guideVersion",1).put("difficulty",c.difficulty).put("sound",c.sound)
        .put("tableSize",c.tableSize.name).put("rotationStripes",c.rotationStripes).put("controlMode",c.controlMode.name).put("style",c.style.name).put("finish",c.finish)
    private fun readConfig(j: JSONObject): BilliardConfig {
        val discipline=enumValueOf<Discipline>(j.getString("discipline"))
        // Old saves always used the family's original reference dimensions.
        val size=BilliardTableSize.restore(j.optString("tableSize"),discipline.family)
        return BilliardConfig(discipline,enumValueOf(j.getString("room")),enumValueOf(j.getString("cue")),
            enumValueOf(j.getString("mode")),j.getInt("cloth"),restoredBilliardAssistance(j.getInt("assistance"),j.optInt("guideVersion",0)),
            j.getInt("difficulty"),j.getBoolean("sound"),size,j.optBoolean("rotationStripes",true),BilliardControlMode.restore(j.optString("controlMode")),
            BilliardGameStyle.restore(j.optString("style")),j.optInt("finish").coerceIn(0,5))
    }
    private fun snapshot(s: SessionSnapshot)=JSONObject().put("cue",s.cueId).put("drill",s.drill).put("time",s.time)
        .put("nomination",s.nominated).put("pocket",s.pocket).put("pushOut",s.pushOut).put("safety",s.safety).put("bankRails",JSONArray(s.bankRails))
        .put("balls",JSONArray().apply { s.balls.forEach { b -> put(JSONObject().put("id",b.id).put("p",vec(b.p)).put("v",vec(b.v)).put("w",vec(b.w))
            .put("r",b.radius).put("m",b.mass).put("motion",b.motion.name).put("q",JSONArray(b.q.toList()))) } })
        .put("pins",JSONArray().apply { s.pins.forEach { put(JSONObject().put("id",it.id).put("p",vec(it.p)).put("down",it.down)) } })
        .put("match",JSONObject().apply {
            val m=s.match
            put("player",m.player); put("s0",m.score0); put("s1",m.score1); put("group",m.group0); put("winner",m.winner)
            put("hand",m.ballInHand); put("break",m.breaking); put("color",m.snookerColor); put("lastRed",m.lastRedColor)
            put("zone",m.cadreZone); put("count",m.cadreCount); put("foul",m.foul.name); put("points",m.points); put("shots",m.shots); put("free",m.freeShots)
            put("fouls0",m.fouls0); put("fouls1",m.fouls1); put("pushAvailable",m.pushOutAvailable); put("decision",m.decision.name)
            put("handRegion",m.handRegion.name); put("run",m.run); put("best0",m.best0); put("best1",m.best1)
            put("anchor",m.anchorZone); put("anchorCount",m.anchorCount); put("respottedBlack",m.respottedBlack)
            put("captured0",JSONArray(m.captured0)); put("captured1",JSONArray(m.captured1))
            put("pendingSpots",JSONArray(m.pendingSpots)); put("englishCannons",m.englishCannons)
            put("englishHazards",m.englishHazards); put("englishRedPots",m.englishRedPots)
        })
    private fun readSnapshot(j: JSONObject): SessionSnapshot {
        val balls=j.getJSONArray("balls").let { a -> require(a.length() in 1..22); (0 until a.length()).map { i ->
            val b=a.getJSONObject(i); Ball(b.getInt("id"),readVec(b.getJSONArray("p")),b.getDouble("r"),b.getDouble("m"),
                readVec(b.getJSONArray("v")),readVec(b.getJSONArray("w")),enumValueOf(b.getString("motion")),
                b.getJSONArray("q").let { q -> DoubleArray(4) { q.getDouble(it) } }) } }
        require(balls.map { it.id }.distinct().size==balls.size)
        require(balls.all { it.id in 0..21 && it.radius in .02..0.04 && it.mass in .1..0.4 && it.q.all(Double::isFinite) })
        val pins=j.getJSONArray("pins").let { a -> require(a.length()<=9); (0 until a.length()).map { i -> val p=a.getJSONObject(i); Pin(p.getInt("id"),readVec(p.getJSONArray("p")),p.getBoolean("down")) } }
        val m=j.getJSONObject("match")
        val state=MatchState(m.getInt("player"),m.getInt("s0"),m.getInt("s1"),m.getInt("group"),m.getInt("winner"),m.getBoolean("hand"),m.getBoolean("break"),
            m.getBoolean("color"),m.getBoolean("lastRed"),m.getInt("zone"),m.getInt("count"),enumValueOf(m.getString("foul")),m.getInt("points"),m.getInt("shots"),m.optInt("free"))
        state.fouls0=m.optInt("fouls0"); state.fouls1=m.optInt("fouls1"); state.pushOutAvailable=m.optBoolean("pushAvailable")
        state.decision=enumValueOf(m.optString("decision",ShotDecision.NONE.name)); state.handRegion=enumValueOf(m.optString("handRegion",HandRegion.ANYWHERE.name))
        state.run=m.optInt("run"); state.best0=m.optInt("best0"); state.best1=m.optInt("best1")
        state.anchorZone=m.optInt("anchor",-1); state.anchorCount=m.optInt("anchorCount"); state.respottedBlack=m.optBoolean("respottedBlack")
        state.captured0=readInts(m.optJSONArray("captured0")); state.captured1=readInts(m.optJSONArray("captured1"))
        state.pendingSpots=readInts(m.optJSONArray("pendingSpots")); state.englishCannons=m.optInt("englishCannons")
        state.englishHazards=m.optInt("englishHazards"); state.englishRedPots=m.optInt("englishRedPots")
        require(state.player in 0..1 && state.winner in -1..1)
        return SessionSnapshot(balls,pins,state,j.getInt("cue"),j.getInt("drill"),j.optDouble("time",0.0),
            j.optInt("nomination",-1),j.optInt("pocket",-1),j.optBoolean("pushOut"),j.optBoolean("safety"),readInts(j.optJSONArray("bankRails")))
    }
    private fun readInts(array: JSONArray?): List<Int> = if(array==null) emptyList() else (0 until array.length()).map(array::getInt)
}
