package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.render.FrontierModels
import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

/** Persistent residents, independent of hostile spawning and combat rewards. */
internal class FrontierResidents(private val world: World,json: String) {
    data class Resident(val key: String,val role: Int,val home: FrontierLife.Place,val bell: Boolean,val actor: Enemy)
    val visible=ArrayList<Enemy>()
    private val people=linkedMapOf<String,Resident>()
    private val bells=linkedSetOf<FrontierLife.Place>()
    private val definitions=(0..2).map { FrontierModels.definition("settler_$it","settler") }
    private val random=Random(7937)
    private var timer=0f
    init {
        val rows=runCatching { JSONArray(json) }.getOrElse { JSONArray() }
        for(i in 0 until rows.length()) runCatching {
            val j=rows.getJSONObject(i);val role=j.getInt("role").coerceIn(0,2)
            val home=FrontierLife.Place(j.getInt("hx"),j.getInt("hy"),j.getInt("hz"))
            val key=j.getString("key")
            val x=j.getDouble("x");val y=j.getDouble("y");val z=j.getDouble("z")
            require(x.isFinite() && y.isFinite() && z.isFinite())
            val actor=Enemy(i,definitions[role],x,y,z).apply { resting=true;wanderTimer=1f }
            people[key]=Resident(key,role,home,j.optBoolean("bell"),actor)
            if(j.optBoolean("bell")) bells.add(home)
        }
    }
    @Synchronized fun discoverBell(x: Int,y: Int,z: Int) {
        bells.add(FrontierLife.Place(x,y,z))
    }
    private fun loaded(x: Int,y: Int,z: Int) = world.getChunk(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16))?.generated==true
    private fun floorAt(x: Double,y: Double,z: Double): Double? {
        for(f in floor(y).toInt() downTo floor(y).toInt()-2) {
            var safe=true
            for(bx in floor(x-.3).toInt()..floor(x+.3).toInt()) for(bz in floor(z-.3).toInt()..floor(z+.3).toInt()) {
                for(by in f..f+2) {
                    if(!loaded(bx,by,bz)) { safe=false;break }
                    val b=world.blockAt(bx,by,bz)
                    if(by==f) { if(b==AIR || isWater(b) || b==LAVA || isDecoration(b) || isTransparent(b)) safe=false }
                    else if(b!=AIR) safe=false
                }
            }
            if(safe && abs(f+1-y)<1.1) return f+1.0
        }
        return null
    }
    private fun add(key: String,role: Int,home: FrontierLife.Place,bell: Boolean) {
        if(key in people) return
        for(r in 1..4) for(dx in -r..r) for(dz in -r..r) {
            if(abs(dx)!=r && abs(dz)!=r) continue
            val x=home.x+dx+.5;val z=home.z+dz+.5
            val y=floorAt(x,home.y.toDouble(),z) ?: continue
            if(people.values.any { hypot(it.actor.x-x,it.actor.z-z)<1.2 && abs(it.actor.y-y)<2 }) continue
            people[key]=Resident(key,role,home,bell,Enemy(people.size,definitions[role],x,y,z).apply { resting=true })
            return
        }
    }
    fun resident(actor: Enemy) = people.values.firstOrNull { it.actor===actor }
    fun nearby(key: String,x: Double,y: Double,z: Double): Resident? = people[key]?.takeIf {
        it.actor in visible && hypot(it.actor.x-x,it.actor.z-z)<5 && abs(it.actor.y-y)<4 &&
            (!it.bell || world.blockAt(it.home.x,it.home.y,it.home.z)==F.MARKET_BELL)
    }
    @Synchronized fun update(dt: Float,x: Double,y: Double,z: Double,night: Boolean) {
        timer-=dt
        if(timer<=0f) {
            timer=2f
            val bellIterator=bells.iterator()
            while(bellIterator.hasNext()) {
                val p=bellIterator.next()
                if(!loaded(p.x,p.y,p.z)) continue
                if(world.blockAt(p.x,p.y,p.z)!=F.MARKET_BELL) { bellIterator.remove();continue }
                if(hypot(p.x-x,p.z-z)<56 && abs(p.y-y)<24)
                    for(role in 0..2) add("bell:${p.x}:${p.y}:${p.z}:$role",role,p,true)
            }
            for(home in world.frontierHomes(x,z)) for(role in 0..2) {
                val p=FrontierLife.Place(home.x+(if(role==0) 4 else 34),home.y+1,home.z+12+role*5)
                if(hypot(p.x-x,p.z-z)<64 && abs(p.y-y)<24) add("hamlet:${home.x}:${home.z}:$role",role,p,false)
            }
            visible.clear()
            val iterator=people.iterator()
            while(iterator.hasNext()) {
                val r=iterator.next().value;val p=r.home;val a=r.actor
                if(r.bell && loaded(p.x,p.y,p.z) && world.blockAt(p.x,p.y,p.z)!=F.MARKET_BELL) { iterator.remove();continue }
                if(visible.size<24 && hypot(a.x-x,a.z-z)<56 && abs(a.y-y)<24 && loaded(floor(a.x).toInt(),floor(a.y).toInt(),floor(a.z).toInt())) visible.add(a)
            }
        }
        for(a in visible) {
            val r=resident(a) ?: continue
            a.animTime+=dt;a.wanderTimer-=dt
            val homeDistance=hypot(r.home.x+.5-a.x,r.home.z+.5-a.z)
            val talking=hypot(x-a.x,z-a.z)<3.5
            if(talking) { a.resting=true;a.yaw=(atan2(x-a.x,z-a.z)*180/PI).toFloat();continue }
            if(night && homeDistance<3) { a.resting=true;continue }
            if(a.wanderTimer<=0) {
                a.wanderTimer=2+random.nextFloat()*4
                a.resting=random.nextBoolean() && homeDistance<8 && !night
                val angle=if(homeDistance>8 || night) atan2(r.home.x+.5-a.x,r.home.z+.5-a.z).toFloat() else random.nextFloat()*2*PI.toFloat()
                a.wanderDirX=sin(angle);a.wanderDirZ=cos(angle);a.yaw=angle*180/PI.toFloat()
            }
            if(a.resting) continue
            val nx=a.x+a.wanderDirX*dt*.7;val nz=a.z+a.wanderDirZ*dt*.7
            val ground=floorAt(nx,a.y,nz)
            if(ground!=null && visible.none { it!==a && hypot(it.x-nx,it.z-nz)<.7 && abs(it.y-a.y)<2 }) {
                a.x=nx;a.z=nz;a.y=ground
            } else { a.wanderTimer=0f;a.resting=true }
        }
    }
    @Synchronized fun snapshot(): String = JSONArray().also { rows -> people.values.forEach { r ->
        rows.put(JSONObject().put("key",r.key).put("role",r.role).put("bell",r.bell)
            .put("hx",r.home.x).put("hy",r.home.y).put("hz",r.home.z)
            .put("x",r.actor.x).put("y",r.actor.y).put("z",r.actor.z))
    } }.toString()
}
