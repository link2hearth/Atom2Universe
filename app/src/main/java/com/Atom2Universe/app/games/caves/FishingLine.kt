package com.Atom2Universe.app.games.caves

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.world.*
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

/** A cast is transient; bait debits, catches and pool rest periods share the world checkpoint. */
internal class FishingLine(private val r: CaveRenderer, private val context: Context, json: String) {
    data class FloatPosition(val x: Double,val y: Double,val z: Double)
    var bobber: FloatPosition? = null
        private set
    var biting=false
        private set
    private var timer=0f
    private var species=E.RIVER_FISH
    private var pool=""
    data class Gauge(val active: Boolean, val fish: Float=0f, val zone: Float=.5f,
        val radius: Float=.17f, val progress: Float=0f, val tracking: Boolean=false)
    private var fighting=false
    private var fish=.5f
    private var zone=.5f
    private var velocity=0f
    private var progress=.3f
    private var fightTime=0f
    private var target=.5f
    private var dartTimer=0f
    private var uiTimer=0f
    private var baited=false
    private val pools=linkedMapOf<String,Long>()
    init {
        runCatching { val j=JSONObject(json);j.keys().forEach { pools[it]=j.optLong(it) } }
    }
    fun snapshot()=JSONObject().apply { pools.forEach { (key,until)->put(key,until) } }.toString()
    fun cancel() { bobber=null;biting=false;fighting=false;timer=0f;r.fishingGaugeCallback?.invoke(Gauge(false)) }
    fun tick(dt: Float, held: Short?, pulling: Boolean) {
        val b=bobber ?: return
        val c=r.camera
        if(held!=E.ROD || r.playerNode.hp<=0 || hypot(b.x-c.playerX,b.z-c.playerZ)>16 || abs(b.y-c.eyeY)>12 ||
            !isWater(r.world.blockAt(floor(b.x).toInt(),floor(b.y-.1).toInt(),floor(b.z).toInt())) ||
            !r.clearCombatLine(c.playerX,c.eyeY,c.playerZ,b.x,b.y+.08,b.z)) { cancel();return }
        if(fighting) { updateFight(dt,pulling);return }
        timer-=dt
        if(timer<=0f) {
            if(biting) { cancel();message(R.string.cave_fishing_missed) }
            else { biting=true;timer=2f;message(R.string.cave_fishing_bite) }
        }
    }
    fun status(): String=context.getString(if(fighting) R.string.cave_fishing_control else if(bobber==null) R.string.cave_fishing_aim else if(biting) R.string.cave_fishing_bite else R.string.cave_fishing_wait)
    fun action() {
        if(fighting) return
        if(bobber!=null) {
            if(biting) {
                fighting=true;biting=false;fish=.5f;zone=.5f;velocity=0f;progress=.3f
                fightTime=0f;dartTimer=.7f;target=.5f;uiTimer=0f
                publishGauge();r.startSwing()
            } else { message(R.string.cave_fishing_missed);cancel() }
            return
        }
        val c=r.camera
        var bx=0;var by=0;var bz=0;var found=false
        for(step in 2..140) {
            val d=step*.1
            bx=floor(c.playerX+c.aimX*d).toInt();by=floor(c.eyeY+c.aimY*d).toInt();bz=floor(c.playerZ+c.aimZ*d).toInt()
            val block=r.world.blockAt(bx,by,bz)
            if(block==WATER) { found=true;break }
            if(block!=AIR && !isWater(block) && !isDecoration(block)) break
        }
        if(!found) { message(R.string.cave_fishing_aim);return }
        val waterCount=(-1..1).sumOf { dx -> (-1..1).count { dz -> isWater(r.world.blockAt(bx+dx,by,bz+dz)) && isWater(r.world.blockAt(bx+dx,by-1,bz+dz)) } }
        if(waterCount<6 || !isWater(r.world.blockAt(bx,by-1,bz)) || r.world.blockAt(bx,by+1,bz)!=AIR) { message(R.string.cave_fishing_pool);return }
        pool="${Math.floorDiv(bx,16)}:${Math.floorDiv(by,16)}:${Math.floorDiv(bz,16)}"
        pools.entries.removeAll { it.value<=r.frontierLife.elapsedMs }
        if((pools[pool] ?: 0L)>r.frontierLife.elapsedMs) { message(R.string.cave_fishing_rest);return }
        val bait=(r.inventory[E.BAIT] ?: 0)>0
        baited=bait
        if(bait) { val n=r.inventory.getValue(E.BAIT)-1; if(n==0) r.inventory.remove(E.BAIT) else r.inventory[E.BAIT]=n;r.changedFrontierInventory() }
        species=when {
            by<r.world.surfaceHeight(bx.toDouble(),bz.toDouble())-8 -> E.CAVE_FISH
            isWater(r.world.blockAt(bx,by-4,bz)) && Random.nextFloat()<.55f -> E.DEEP_FISH
            else -> E.RIVER_FISH
        }
        bobber=FloatPosition(bx+.5,by+.95,bz+.5);biting=false
        timer=if(bait) Random.nextFloat()*3.5f+3f else Random.nextFloat()*6f+6f
        r.startSwing()
    }
    private val radius get() = (if(species==E.DEEP_FISH) .13f else if(species==E.CAVE_FISH) .15f else .18f) + if(baited) .025f else 0f
    private fun updateFight(dt: Float,pulling: Boolean) {
        fightTime+=dt;dartTimer-=dt
        if(dartTimer<=0f) {
            target=Random.nextFloat()*.86f+.07f
            dartTimer=when(species) { E.DEEP_FISH -> .55f;E.CAVE_FISH -> .85f;else -> 1.25f }+Random.nextFloat()*.65f
        }
        val speed=when(species) { E.DEEP_FISH -> .68f;E.CAVE_FISH -> .49f;else -> .34f }
        fish+=(target-fish).coerceIn(-speed*dt,speed*dt)
        velocity=(velocity+(if(pulling) 2.6f else -2.1f)*dt)*kotlin.math.exp(-3.8f*dt)
        zone+=velocity*dt
        if(zone<radius) { zone=radius;velocity=0f }
        if(zone>1f-radius) { zone=1f-radius;velocity=0f }
        val tracking=kotlin.math.abs(fish-zone)<radius
        // A short opening grace lets the player release the hooking press and find the rhythm.
        progress=(progress+dt*if(tracking) .14f else if(fightTime<1.1f) 0f else -.105f).coerceIn(0f,1f)
        if(progress>=1f) { landCatch();return }
        if(progress<=0f || fightTime>=40f) { message(R.string.cave_fishing_missed);cancel();return }
        uiTimer-=dt
        if(uiTimer<=0f) { uiTimer=1f/30f;publishGauge() }
    }
    private fun publishGauge() { r.fishingGaugeCallback?.invoke(Gauge(true,fish,zone,radius,progress,kotlin.math.abs(fish-zone)<radius)) }
    private fun landCatch() {
        if((r.inventory[species] ?: 0)==Int.MAX_VALUE) { message(R.string.cave_storage_overflow);cancel();return }
        r.inventory[species]=(r.inventory[species] ?: 0)+1
        pools[pool]=r.frontierLife.elapsedMs+75_000L
        r.changedFrontierInventory()
        val name=BlockRegistry.get(species)?.name
        val resource=name?.let { context.resources.getIdentifier("cave_block_$it","string",context.packageName) } ?: 0
        if(resource!=0) r.farmMessageCallback?.invoke(context.getString(R.string.cave_fishing_caught,context.getString(resource)))
        r.startSwing();cancel()
    }
    private fun message(id: Int) { r.farmMessageCallback?.invoke(context.getString(id)) }
}
