package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.MobDef
import com.Atom2Universe.app.games.caves.node.KitchenItems as K
import com.Atom2Universe.app.games.caves.render.AnimalModels
import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

/** Faune indépendante des vagues, niveaux et récompenses des monstres. Thread GL uniquement. */
internal class PassiveAnimals(private val world: World, private val seed: Long) {
    val visible = ArrayList<Enemy>()
    private val residents = linkedMapOf<String, MutableList<Enemy>>()
    private val random = Random(seed xor 71623L)
    private var timer = 0f
    @Volatile var snapshot: String = "[]"
        private set

    fun restore(json: String) {
        residents.clear(); visible.clear(); timer=0f
        runCatching { JSONArray(json) }.getOrNull()?.let { cells ->
            for (i in 0 until cells.length()) {
                val cell = cells.optJSONObject(i) ?: continue
                val key = cell.optString("cell")
                if (!key.matches(Regex("-?\\d+:-?\\d+"))) continue
                val animals = mutableListOf<Enemy>()
                val list = cell.optJSONArray("animals") ?: continue
                for (j in 0 until list.length()) {
                    val a = list.optJSONObject(j) ?: continue
                    val def = definitions.firstOrNull { it.id == a.optString("species") } ?: continue
                    val xyz = listOf("x", "y", "z").map { a.optDouble(it) }
                    if (xyz.any { !it.isFinite() }) continue
                    if (a.optInt("hp",def.hpBase)<=0) continue
                    animals += Enemy(j, def, xyz[0], xyz[1], xyz[2]).apply {
                        hp=a.optInt("hp",def.hpBase).coerceIn(1,def.hpBase)
                        young = a.optBoolean("young", false)
                        domestic=a.optBoolean("domestic")
                        fun time(key: String, default: Float, min: Float, max: Float) =
                            a.optDouble(key,default.toDouble()).toFloat().takeIf { it.isFinite() }?.coerceIn(min,max) ?: default
                        growth=time("growth",600f,0f,600f)
                        affection=time("affection",0f,0f,60f)
                        breedRest=time("breedRest",0f,0f,300f)
                        mealRest=time("mealRest",0f,0f,30f)
                        productTime=time("productTime",-1f,-1f,120f)
                        coat = a.optInt("coat", if(def.id == "cow") 1 else 0)
                            .coerceIn(0, AnimalModels.coatCount(def.id)-1)
                        yaw = a.optDouble("yaw", 0.0).toFloat().takeIf { it.isFinite() } ?: 0f
                        resting = a.optBoolean("resting", true)
                        wanderTimer = a.optDouble("timer", 2.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 8f) ?: 2f
                        wanderDirX = sin(yaw * PI.toFloat() / 180)
                        wanderDirZ = cos(yaw * PI.toFloat() / 180)
                    }
                }
                residents[key] = animals
            }
        }
        snapshot = json
    }

    /** Simulation distance from the pause menu, in blocks: animals are placed and move within it. */
    var simulationRadius = 64.0

    fun update(dt: Float, px: Double, py: Double, pz: Double, held: Short?) {
        timer -= dt
        val refresh=timer<=0f
        if (refresh) {
            timer = 2f
            val cx = floor(px / 32).toInt(); val cz = floor(pz / 32).toInt()
            val cells = kotlin.math.ceil(simulationRadius / 32).toInt()
            for (z in cz - cells..cz + cells) for (x in cx - cells..cx + cells) populate(x, z, py)
            visible.clear()
            // Prioritise the nearby herd over older, distant wild spawn cells.
            visible += residents.values.asSequence().flatten().filter { a ->
                a.hp>0 && abs(a.y-py)<24 && (a.x-px).pow(2)+(a.z-pz).pow(2)<simulationRadius.pow(2) && loaded(a.x,a.y,a.z)
            }.sortedWith(compareByDescending<Enemy> { it.domestic }.thenBy { (it.x-px).pow(2)+(it.z-pz).pow(2) }).take(64)
        }
        for (a in visible) {
            if (!loaded(a.x, a.y, a.z)) continue
            a.animTime += dt
            a.hitFlash=(a.hitFlash-dt).coerceAtLeast(0f)
            a.staggerTimer=(a.staggerTimer-dt).coerceAtLeast(0f)
            a.affection=(a.affection-dt).coerceAtLeast(0f)
            a.breedRest=(a.breedRest-dt).coerceAtLeast(0f)
            a.mealRest=(a.mealRest-dt).coerceAtLeast(0f)
            if(a.productTime>0f) a.productTime=(a.productTime-dt).coerceAtLeast(0f)
            if(a.young) { a.growth=(a.growth-dt).coerceAtLeast(0f); if(a.growth==0f) a.young=false }
            a.wanderTimer -= dt
            if (a.wanderTimer <= 0) {
                a.resting = random.nextInt(3) == 0
                a.yaw = random.nextFloat() * 360
                a.wanderDirX = sin(a.yaw * PI.toFloat()/180)
                a.wanderDirZ = cos(a.yaw * PI.toFloat()/180)
                a.wanderTimer = 2f + random.nextFloat() * 4
            }
            // Le joueur très proche fait s'écarter l'animal, sans riposte.
            val dx = a.x-px; val dz = a.z-pz; val distance = hypot(dx, dz)
            val frightened=a.staggerTimer>0f
            val lured=!frightened && held==food(a) && distance<12 && abs(a.y-py)<4
            val shy = (frightened || !a.domestic && !lured) && distance < (if(frightened) 12.0 else 2.5) && abs(a.y-py) < 3
            if (shy && distance > .01) {
                a.resting = false
                a.wanderDirX = (dx/distance).toFloat(); a.wanderDirZ = (dz/distance).toFloat()
                a.yaw = atan2(a.wanderDirX, a.wanderDirZ)*180/PI.toFloat()
            }
            if(lured && distance>2.0) {
                a.resting=false
                a.wanderDirX=(-dx/distance).toFloat();a.wanderDirZ=(-dz/distance).toFloat()
                a.yaw=atan2(a.wanderDirX,a.wanderDirZ)*180/PI.toFloat()
            } else if(lured) a.resting=true
            // Fed parents approach one another; wandering alone rarely brought a pair together in time.
            if(!a.young && a.affection>0f && a.breedRest==0f && !shy && !lured) {
                val mate=visible.asSequence().filter { it!==a && !it.young && it.def.id==a.def.id &&
                    it.affection>0f && it.breedRest==0f && abs(it.y-a.y)<1.1 && hypot(it.x-a.x,it.z-a.z)<10 && clearBetween(a,it) }
                    .minByOrNull { hypot(it.x-a.x,it.z-a.z) }
                if(mate!=null) {
                    val mx=mate.x-a.x;val mz=mate.z-a.z;val d=hypot(mx,mz)
                    a.resting=d<=radius(a)+radius(mate)+.3
                    if(!a.resting) {
                        a.wanderDirX=(mx/d).toFloat();a.wanderDirZ=(mz/d).toFloat()
                        a.yaw=atan2(a.wanderDirX,a.wanderDirZ)*180/PI.toFloat()
                    }
                }
            }
            // Les petits rejoignent un adulte de leur espèce au lieu de se disperser.
            if (a.young && !shy && !lured) {
                var adult: Enemy? = null
                var nearest = Double.MAX_VALUE
                for (other in visible) {
                    if (other.young || other.def.id != a.def.id || abs(other.y-a.y)>=3) continue
                    val d2=(other.x-a.x).pow(2)+(other.z-a.z).pow(2)
                    if (d2<nearest) { nearest=d2; adult=other }
                }
                if (adult != null) {
                    val ax=adult.x-a.x; val az=adult.z-a.z; val dist=hypot(ax,az)
                    if (dist > 3.5) {
                        a.resting=false
                        a.wanderDirX=(ax/dist).toFloat(); a.wanderDirZ=(az/dist).toFloat()
                        a.yaw=atan2(a.wanderDirX,a.wanderDirZ)*180/PI.toFloat()
                    }
                }
            }
            if (applyWaterMotion(a, dt)) continue
            if (!a.resting) {
                val speed = a.def.speed * dt.coerceAtMost(.05f) * if (shy) 1.8 else 1.0
                val nx = a.x+a.wanderDirX*speed; val nz = a.z+a.wanderDirZ*speed
                val ground = floorAt(nx, nz, a.y, radius(a))
                val crowded = visible.any { it !== a && abs(it.y-a.y)<2 && hypot(it.x-nx, it.z-nz) < radius(a)+radius(it) }
                if (ground != null && !crowded) { a.x=nx; a.z=nz; a.y=ground }
                else { a.wanderTimer=0f; a.resting=true }
            }
        }
        if(refresh) breed()
    }

    fun snapshotNow(): String {
        snapshot=JSONArray().also { cells ->
            residents.forEach { (key, group) -> cells.put(JSONObject().put("cell", key).put("animals", JSONArray().also { list ->
                group.forEach { a -> list.put(JSONObject().put("species", a.def.id).put("x", a.x).put("y", a.y).put("z", a.z)
                    .put("young", a.young).put("coat", a.coat).put("domestic",a.domestic).put("hp",a.hp)
                    .put("growth",a.growth.toDouble()).put("affection",a.affection.toDouble())
                    .put("breedRest",a.breedRest.toDouble()).put("mealRest",a.mealRest.toDouble()).put("productTime",a.productTime.toDouble())
                    .put("yaw", a.yaw.toDouble()).put("resting", a.resting).put("timer", a.wanderTimer.toDouble())) }
            })) }
        }.toString()
        return snapshot
    }

    fun food(a: Enemy): Short = when(a.def.id) { "pig"->9704; "chicken"->9600;else->9700 }
    fun height(a: Enemy): Double = AnimalModels.get(a.def.model,a.young,a.coat).heightVox *
        (a.baseScale * 2.0 / com.Atom2Universe.app.games.caves.render.MobModels.REF_VOX)

    /** Do not waste a meal while all its benefits are already active. */
    fun needsFood(a: Enemy) = a.hp>0 && a.mealRest==0f &&
        (a.young || !a.domestic || a.productTime<0f || a.breedRest==0f && a.affection==0f)

    /** Animals never enter the monster death/XP/reward pipeline. Empty cells stay saved after hunting. */
    fun damage(a: Enemy, amount: Int): List<Pair<Short,Int>> {
        if(a !in visible || a.hp<=0 || amount<=0) return emptyList()
        a.hp=(a.hp.toLong()-amount).coerceAtLeast(0L).toInt()
        a.hitFlash=.25f;a.staggerTimer=4f;a.affection=0f;a.resting=false
        if(a.hp>0) return emptyList()
        visible.remove(a)
        residents.values.forEach { it.remove(a) }
        if(a.young) return emptyList()
        return when(a.def.id) {
            "cow" -> listOf(K.BEEF to 3)
            "pig" -> listOf(K.PORK to 3)
            "chicken" -> listOf(K.CHICKEN to 2)
            "sheep" -> listOf(K.MUTTON to 2)
            else -> emptyList()
        }
    }

    /** Returns 0 for no action, 1 fed, 2 collected, 3 animal status, 4 inventory full. */
    fun interact(a: Enemy, held: Short?, inventory: MutableMap<Short,Int>): Int {
        if(a !in visible || a.hp<=0) return 0
        if(held==food(a)) {
            if((inventory[held] ?: 0)<=0 || !needsFood(a)) return 3
            val left=inventory.getValue(held)-1
            if(left==0) inventory.remove(held) else inventory[held]=left
            a.domestic=true;a.mealRest=30f
            if(a.young) {
                a.growth=(a.growth-120f).coerceAtLeast(0f)
                if(a.growth==0f) { a.young=false;a.productTime=120f }
            }
            else {
                if(a.breedRest==0f) a.affection=60f
                if(a.productTime<0f) a.productTime=120f
            }
            return 1
        }
        val product: Short = when {
            a.def.id=="cow" && held==BUCKET_EMPTY -> F.MILK
            a.def.id=="sheep" && held==F.SHEARS -> F.WOOL
            a.def.id=="chicken" && held==null -> F.EGG
            a.def.id=="pig" && held==null -> F.TRUFFLE
            else -> return if(held==null) 3 else 0
        }
        val count=if(product==F.WOOL) 3 else 1
        if(a.young || !a.domestic || a.productTime!=0f || (held!=null && (inventory[held] ?: 0)<1)) return 3
        if((inventory[product] ?: 0)>Int.MAX_VALUE-count) return 4
        if(held==BUCKET_EMPTY) {
            val left=inventory.getValue(held)-1
            if(left==0) inventory.remove(held) else inventory[held]=left
        }
        inventory[product]=(inventory[product] ?: 0)+count;a.productTime=-1f
        return 2
    }

    private fun breed() {
        val newborns=mutableListOf<Enemy>()
        val domesticCount=visible.count { it.domestic }
        for(a in visible) {
            // Distant wildlife must not use up all the birth slots of a small nearby herd.
            if(domesticCount+newborns.size>=64) break
            if(a.young || a.affection<=0 || a.breedRest>0) continue
            if((visible+newborns).count { abs(it.y-a.y)<3 && hypot(it.x-a.x,it.z-a.z)<12 }>=24) continue
            val mate=visible.firstOrNull { it!==a && it.def.id==a.def.id && !it.young && it.affection>0 &&
                it.breedRest==0f && abs(it.y-a.y)<1.1 && hypot(it.x-a.x,it.z-a.z)<4 && clearBetween(a,it) } ?: continue
            val group=residents.values.firstOrNull { a in it } ?: continue
            if(group.size>=24) continue
            val baby=Enemy(group.size,a.def,a.x,a.y,a.z).apply {
                young=true;domestic=true;coat=if(random.nextBoolean()) a.coat else mate.coat;resting=true;wanderTimer=1f
            }
            // Birth is only possible in a free patch beside both parents.
            val spot=listOf(2.0 to 0.0,-2.0 to 0.0,0.0 to 2.0,0.0 to -2.0).firstNotNullOfOrNull { (dx,dz) ->
                val x=a.x+dx;val z=a.z+dz
                val y=floorAt(x,z,a.y,radius(baby))
                baby.x=x;baby.z=z;baby.y=y ?: a.y
                if(y!=null && clearBetween(a,baby) && (visible+newborns).none { abs(it.y-y)<2 && hypot(it.x-x,it.z-z)<radius(it)+radius(baby) }) Triple(x,y,z) else null
            } ?: continue
            baby.x=spot.first;baby.y=spot.second;baby.z=spot.third;group.add(baby)
            newborns.add(baby)
            a.affection=0f;mate.affection=0f;a.breedRest=300f;mate.breedRest=300f
        }
        visible.addAll(newborns)
        while(visible.size>64) {
            val wild=visible.indexOfLast { !it.domestic }
            if(wild<0) break
            visible.removeAt(wild) // Remains in residents and reappears on a later visit.
        }
    }

    /** Parents and births must share the same pen, rather than reach through a wall. */
    private fun clearBetween(a: Enemy,b: Enemy): Boolean {
        val steps=(hypot(b.x-a.x,b.z-a.z)*4).toInt().coerceAtLeast(1)
        for(i in 1 until steps) {
            val t=i.toDouble()/steps
            val x=a.x+(b.x-a.x)*t;val y=a.y+(b.y-a.y)*t+.5;val z=a.z+(b.z-a.z)*t
            if(!loaded(x,y,z)) return false
            val block=world.blockAt(floor(x).toInt(),floor(y).toInt(),floor(z).toInt())
            if(block!=AIR && !isDecoration(block)) return false
        }
        return true
    }

    private val current = DoubleArray(4)

    /** La faune reste entraînée au repos et continue de tomber après une cascade. */
    private fun applyWaterMotion(a: Enemy, dt: Float): Boolean {
        WaterCurrent.sample(world, a.x, a.y + 0.25, a.z, current)
        val wet = current[3] > 0.0
        if (!wet && a.velY == 0.0) {
            a.waterDriftX = 0.0; a.waterDriftZ = 0.0
            return false
        }
        val step = dt.coerceIn(0f, 0.05f).toDouble()
        val response = 1.0 - exp(-4.0 * step)
        if (wet) {
            a.waterDriftX += (current[0] - a.waterDriftX) * response
            a.waterDriftZ += (current[2] - a.waterDriftZ) * response
        } else { a.waterDriftX = 0.0; a.waterDriftZ = 0.0 }
        val swim = if (wet && !a.resting) a.def.speed * 0.45 else 0.0
        val dx = (a.waterDriftX + a.wanderDirX * swim) * step
        val dz = (a.waterDriftZ + a.wanderDirZ * swim) * step
        if (waterSpaceFree(a, a.x + dx, a.y, a.z)) a.x += dx
        if (waterSpaceFree(a, a.x, a.y, a.z + dz)) a.z += dz
        if (wet) a.velY += (current[1] - a.velY) * response
        a.velY = (a.velY - (if (wet) 4.0 else 20.0) * step).coerceAtLeast(if (wet) -3.0 else -12.0)
        val dy = a.velY * step
        if (waterSpaceFree(a, a.x, a.y + dy, a.z)) a.y += dy
        else {
            // Approcher le sol sans l'enfoncer ni figer l'animal au-dessus.
            var free = 0.0; var blocked = 1.0
            repeat(8) {
                val fraction = (free + blocked) * 0.5
                if (waterSpaceFree(a, a.x, a.y + dy * fraction, a.z)) free = fraction
                else blocked = fraction
            }
            a.y += dy * free
            a.velY = 0.0
        }
        return true
    }

    private fun waterSpaceFree(a: Enemy, x: Double, y: Double, z: Double): Boolean {
        val r = radius(a)
        val height = maxOf(0.6, a.def.eyeHeight.toDouble() * if (a.young) 0.72 else 1.0)
        for (bx in floor(x-r).toInt()..floor(x+r).toInt())
            for (bz in floor(z-r).toInt()..floor(z+r).toInt())
                for (by in floor(y+0.002).toInt()..floor(y+height).toInt()) {
                    if (!loaded(bx.toDouble(), by.toDouble(), bz.toDouble())) return false
                    val block = world.blockAt(bx, by, bz)
                    if (block != AIR && !isWater(block) && !isDecoration(block)) return false
                }
        return true
    }

    private fun loaded(x: Double, y: Double, z: Double) = world.getChunk(floor(x/16).toInt(), floor(y/16).toInt(), floor(z/16).toInt())?.generated == true

    /** Volume entier libre et appui sec sous toute l'empreinte ; aucune marche dans le vide. */
    private fun radius(a: Enemy): Double = a.def.radius.toDouble() * if(a.young) .72 else 1.0

    private fun floorAt(x: Double, z: Double, y: Double, radius: Double): Double? {
        for (floor in floor(y).toInt() downTo floor(y).toInt()-2) {
            var safe = true
            for (bx in floor(x-radius).toInt()..floor(x+radius).toInt())
                for (bz in floor(z-radius).toInt()..floor(z+radius).toInt()) {
                for (by in floor..floor+2) {
                    if (!loaded(bx.toDouble(), by.toDouble(), bz.toDouble())) { safe=false; break }
                    val b = world.blockAt(bx, by, bz)
                    if (by == floor) {
                        if (b == AIR || isWater(b) || isDecoration(b)) safe=false
                    } else if (b != AIR && !isDecoration(b)) safe=false
                }
            }
            if (safe && abs(floor+1-y) <= 1.01) return floor+1.0
        }
        return null
    }

    private fun populate(cx: Int, cz: Int, py: Double) {
        val key = "$cx:$cz"
        if (key in residents) return
        val rng = Random(seed xor (cx.toLong()*73428767) xor (cz.toLong()*912931))
        val x = cx*32 + 8.5 + rng.nextInt(12); val z = cz*32 + 8.5 + rng.nextInt(12)
        val y = floor(world.surfaceHeight(x,z))+1
        if (abs(py-y)>24 || !loaded(x,y,z)) return
        val biome = world.naturalSurfaceBiomeAt(x,y,z) ?: BiomeMap.surfaceBiomeAt(x,z,seed).id
        val habitat = com.Atom2Universe.app.games.caves.world.RegionalBiomes.parent(biome)
        val allowed = definitions.filter { habitat in it.biomes }
        val group = mutableListOf<Enemy>()
        if (allowed.isNotEmpty() && rng.nextInt(3) != 0) {
            val def=allowed[rng.nextInt(allowed.size)]
            for (i in 0..2) {
                val nx=x+i*2.5
                val animal=Enemy(i,def,nx,y,z).apply {
                    young=i == 2 && group.isNotEmpty()
                    coat=rng.nextInt(AnimalModels.coatCount(def.id))
                    resting=true; wanderTimer=1f+i
                }
                val ground=floorAt(nx,z,y,radius(animal)) ?: return // Réessayer une fois tous les chunks disponibles.
                val b=world.blockAt(floor(nx).toInt(),ground.toInt()-1,floor(z).toInt())
                if (b != GRASS && b != FOREST_FLOOR && b != MOSS) continue
                animal.y=ground
                group += animal
            }
        }
        residents[key]=group
    }

    data class Display(val def: MobDef, val young: Boolean, val coat: Int)

    companion object {
        val definitions = listOf(definition("sheep", .9f, listOf("plains", "forest", "birch_forest", "taiga")),
            definition("cow", 1.05f, listOf("plains", "forest", "birch_forest")),
            definition("chicken", .8f, listOf("plains", "forest", "birch_forest", "jungle_edge"), .65f, .85f),
            definition("pig", .95f, listOf("plains", "forest", "birch_forest", "dark_forest"), 1.1f, .6f))
        val displays: List<Display> get() = definitions.flatMap { def ->
            listOf(false,true).flatMap { young -> (0 until AnimalModels.coatCount(def.id)).map { Display(def,young,it) } }
        }
        private fun definition(id: String, scale: Float, biomes: List<String>, radius: Float=1.15f, speed: Float=.65f) = MobDef(
            id=id, hpBase=20, damageBase=0, speed=speed, attackRange=0.0, detectRange=0.0,
            eyeHeight=1f, radius=radius, spriteScale=scale, hpScalePerLevel=1.0, hpScaleCap=1.0,
            damageScalePer3Lvl=0, speedScalePerLevel=0f, biomes=biomes, model=id, spawnZoneMin=0,
            spawnWeight=0f, behavior="passive", bossEligible=false, xpBase=0)
    }
}
