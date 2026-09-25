package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.FarmItems
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.CaveStackInventory
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import kotlin.math.*
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.render.FrontierModels

/** Persistent local inventories. All game mutations happen on the GL thread. */
internal class FrontierWorkshops(private val world: World, private val seed: Long) {
    data class Pos(val x: Int, val y: Int, val z: Int) {
        fun move(dx: Int, dy: Int, dz: Int) = Pos(x+dx,y+dy,z+dz)
    }
    data class View(val pos: Pos, val block: Short, val items: Map<Short,Int>, val powered: Boolean,val selection: Int=-1,val progress: Int=0,val active: Int=-1,val stacks: List<CaveStackInventory.Stack> = emptyList())
    private data class Store(val items: MutableMap<Short,Int> = linkedMapOf(), var progress: Int = 0,var active: String="",var selection: Int=-1,val stacks: CaveStackInventory = CaveStackInventory())
    private val stores = linkedMapOf<Pos,Store>()
    val machinery=ArrayList<Enemy>()
    private val knownMachines=linkedMapOf<Pos,Enemy>()
    private var visualTimer=0f
    private var feedTimer=0f
    private val running=hashSetOf<Pos>()
    private val rotorDef=FrontierModels.definition("workshop_rotor","machine",.48f)
    data class Recipe(val machine: Short,val input: Map<Short,Int>,val output: Map<Short,Int>,val seconds: Int,val power: Boolean=false) {
        val key: String = "$machine/" + input.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" } +
            "/" + output.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }
    }
    val recipes=listOf(
        Recipe(F.COOKER,mapOf(F.FLOUR to 3),mapOf(F.BREAD to 4),10),
        Recipe(F.COOKER,mapOf(9705.toShort() to 3),mapOf(F.BAKED_POTATO to 4),10),
        Recipe(F.MILL,mapOf(9700.toShort() to 1),mapOf(F.FLOUR to 3),6,true),
        Recipe(F.CRUSHER,mapOf(3101.toShort() to 1),mapOf(F.IRON_DUST to 2),8,true),
        Recipe(F.CRUSHER,mapOf(3104.toShort() to 1),mapOf(F.COPPER_DUST to 2),8,true),
        Recipe(F.KILN,mapOf(F.IRON_DUST to 1,3100.toShort() to 1),mapOf(3114.toShort() to 1),12),
        Recipe(F.KILN,mapOf(F.COPPER_DUST to 1,3100.toShort() to 1),mapOf(3115.toShort() to 1),12),
        Recipe(F.KILN,mapOf(3114.toShort() to 2,3100.toShort() to 2),mapOf(F.STEEL to 1),20),
        Recipe(F.PRESS,mapOf(3115.toShort() to 1),mapOf(F.PLATE to 2),6,true),
        Recipe(F.PRESS,mapOf(F.STEEL to 1),mapOf(F.GEAR to 3),10,true),
        Recipe(F.LOOM,mapOf(F.WOOL to 2),mapOf(F.CLOTH to 3),10,true),
        Recipe(F.VAT,mapOf(F.MILK to 1),mapOf(F.CHEESE to 3,BUCKET_EMPTY to 1),30),
        Recipe(F.COOKER,mapOf(F.EGG to 2,9706.toShort() to 1),mapOf(F.OMELETTE to 2),10),
        Recipe(F.COOKER,mapOf(F.MILK to 1,F.FLOUR to 2,F.EGG to 1),mapOf(F.PANCAKE to 4,BUCKET_EMPTY to 1),12),
        Recipe(F.COOKER,mapOf(F.MILK to 1,F.TRUFFLE to 1,9705.toShort() to 2),mapOf(F.CREAM_SOUP to 3,BUCKET_EMPTY to 1),15)
    ) + listOf(
        Recipe(E.FORGE,mapOf(3114.toShort() to 1,3100.toShort() to 1),mapOf(E.BLANK to 1),8),
        Recipe(E.FORGE,mapOf(F.GEODE to 1,3100.toShort() to 1),mapOf(E.POWDER to 8),12),
        Recipe(F.PRESS,mapOf(F.STEEL to 1),mapOf(E.STEEL_PLATE to 2),8,true),
        Recipe(F.PRESS,mapOf(3114.toShort() to 1),mapOf(E.RIVETS to 8),6,true),
        Recipe(F.PRESS,mapOf(F.PLATE to 1),mapOf(E.CASE to 8),6,true),
        Recipe(F.COOKER,mapOf(E.RIVER_FISH to 1),mapOf(E.GRILLED_FISH to 1),8),
        Recipe(F.COOKER,mapOf(E.RIVER_FISH to 1,9705.toShort() to 2,9706.toShort() to 1),mapOf(E.FISH_STEW to 2),12),
        Recipe(F.PRESS,mapOf(E.RIVER_FISH to 2),mapOf(E.FISH_OIL to 1),8,true),
        Recipe(F.COOKER,mapOf(E.CAVE_FISH to 1),mapOf(E.GRILLED_FISH to 1),8),
        Recipe(F.COOKER,mapOf(E.CAVE_FISH to 1,9705.toShort() to 2,9706.toShort() to 1),mapOf(E.FISH_STEW to 2),12),
        Recipe(F.PRESS,mapOf(E.CAVE_FISH to 2),mapOf(E.FISH_OIL to 1),8,true),
        Recipe(F.COOKER,mapOf(E.DEEP_FISH to 1),mapOf(E.GRILLED_FISH to 1),8),
        Recipe(F.COOKER,mapOf(E.DEEP_FISH to 1,9705.toShort() to 2,9706.toShort() to 1),mapOf(E.FISH_STEW to 2),12),
        Recipe(F.PRESS,mapOf(E.DEEP_FISH to 2),mapOf(E.FISH_OIL to 1),8,true)
    ) + FarmItems.crops.indices.map { Recipe(F.COMPOSTER,mapOf(FarmItems.produce(it) to 3),mapOf(F.COMPOST to 1),20) }
    @Synchronized fun select(p: Pos,index: Int): Boolean {
        if(!loaded(p) || !F.isContainer(block(p)) || index != -1 && recipes.getOrNull(index)?.machine!=block(p)) return false
        val s=store(p);s.selection=index;s.progress=0;s.active="";return true
    }
    @Synchronized fun discover(p: Pos,id: Short) {
        if(id==F.HOPPER) hoppers.add(p)
        if(F.isContainer(id) && id!=F.CHEST && id!=F.CACHE) store(p)
        if(id in setOf(F.MILL,F.WATERWHEEL,F.SHAFT,F.PRESS,F.CRUSHER,F.LOOM)) {
            knownMachines.getOrPut(p) { Enemy(knownMachines.size,rotorDef,p.x+.5,p.y+.02,p.z+1.025).apply { resting=true } }
        }
    }
    @Synchronized fun animate(dt: Float,x: Double,y: Double,z: Double) {
        visualTimer-=dt
        if(visualTimer<=0f) {
            visualTimer=1f;machinery.clear();running.clear()
            val iterator=knownMachines.iterator()
            while(iterator.hasNext()) {
                val (p,e)=iterator.next()
                if(!loaded(p) || block(p) !in setOf(F.MILL,F.WATERWHEEL,F.SHAFT,F.PRESS,F.CRUSHER,F.LOOM)) { iterator.remove();continue }
                if(abs(p.y-y)>24 || (p.x-x).pow(2)+(p.z-z).pow(2)>32.0.pow(2) || machinery.size>=48) continue
                machinery.add(e)
                if(powered(p)) running.add(p)
            }
        }
        for(p in running) knownMachines[p]?.let { it.animTime=(it.animTime+dt)%3141.59f }
    }
    @Synchronized fun feedAnimals(animals: com.Atom2Universe.app.games.caves.entity.PassiveAnimals,dt: Float) {
        feedTimer+=dt
        if(feedTimer<1f) return
        feedTimer-=1f
        for((p,s) in stores) {
            if(!loaded(p) || block(p)!=F.TROUGH) continue
            for(a in animals.visible) {
                if(a.mealRest>0f || (!a.young && a.productTime>=0f) || abs(a.y-p.y)>2 || hypot(a.x-p.x-.5,a.z-p.z-.5)>4) continue
                // A trough cannot feed through the wall of an adjacent pen.
                val distance=hypot(a.x-p.x-.5,a.z-p.z-.5);val steps=(distance*4).toInt().coerceAtLeast(1)
                if((1 until steps).any { i ->
                    val t=i.toDouble()/steps
                    val b=world.blockAt(floor(p.x+.5+(a.x-p.x-.5)*t).toInt(),floor(p.y+1.1+(a.y-p.y)*t).toInt(),floor(p.z+.5+(a.z-p.z-.5)*t).toInt())
                    b!=AIR && !isDecoration(b)
                }) continue
                animals.interact(a,animals.food(a),s.items)
            }
        }
    }
    private val hoppers = linkedSetOf<Pos>()
    private var accumulator = 0f
    private val directions = arrayOf(intArrayOf(1,0,0),intArrayOf(-1,0,0),intArrayOf(0,1,0),
        intArrayOf(0,-1,0),intArrayOf(0,0,1),intArrayOf(0,0,-1))

    private fun block(p: Pos) = world.blockAt(p.x,p.y,p.z)
    private fun loaded(p: Pos) = world.getChunk(Math.floorDiv(p.x,16),Math.floorDiv(p.y,16),Math.floorDiv(p.z,16))?.generated == true
    private fun store(p: Pos): Store = stores.getOrPut(p) {
        Store().also { s -> if(block(p) == F.CACHE) {
            val rng = Random(seed xor (p.x.toLong()*341873128712L) xor (p.z.toLong()*132897987541L) xor p.y.toLong())
            s.items[3110] = 4+rng.nextInt(8) // sticks
            s.items[3111] = 6+rng.nextInt(8) // fibres
            s.items[F.BREAD] = 2+rng.nextInt(3)
            repeat(3) { val id=FarmItems.seed(rng.nextInt(FarmItems.crops.size)); s.items[id]=(s.items[id] ?: 0)+3 }
            s.items[if(rng.nextBoolean()) F.GEAR else 3114] = 1+rng.nextInt(3)
        } }
    }
    @Synchronized fun placed(p: Pos, id: Short) {
        stores.remove(p); hoppers.remove(p); knownMachines.remove(p)
        if(F.isContainer(id)) stores[p]=Store() // player-placed caches never roll loot
        if(id == F.HOPPER) hoppers.add(p)
        discover(p,id)
    }
    @Synchronized fun view(p: Pos): View? {
        if(!loaded(p) || !F.isContainer(block(p))) return null
        val s=store(p)
        s.stacks.reconcile(s.items)
        return View(p,block(p),s.items.toMap(),powered(p),s.selection,s.progress,recipes.indexOfFirst { it.key==s.active },s.stacks.snapshot())
    }
    @Synchronized fun splitStack(p: Pos,key: Long,count: Int): Boolean {
        if(!loaded(p) || !F.isContainer(block(p))) return false
        val s=store(p);s.stacks.reconcile(s.items)
        return s.stacks.split(key,count)
    }
    @Synchronized fun moveStack(p: Pos,key: Long,target: Long?): Boolean {
        if(!loaded(p) || !F.isContainer(block(p))) return false
        val s=store(p);s.stacks.reconcile(s.items)
        return s.stacks.moveToBag(key,target)
    }
    @Synchronized fun transferStack(p: Pos,inventory: MutableMap<Short,Int>,player: CaveStackInventory,
        hotbar: Array<Short?>,key: Long,deposit: Boolean,target: Long?=null,slot: Int?=null): Int {
        if(!loaded(p) || !F.isContainer(block(p))) return 0
        val s=store(p);s.stacks.reconcile(s.items)
        val from=if(deposit) player else s.stacks;val to=if(deposit) s.stacks else player
        val source=from.get(key) ?: return 0
        val destination=if(deposit) s.items else inventory
        val count=minOf(source.count,Int.MAX_VALUE-(destination[source.id] ?: 0).coerceAtLeast(0))
        if(count<=0 || !transfer(p,inventory,source.id,count,deposit)) return 0
        from.take(key,count);to.receive(source.id,count,target,if(deposit) null else slot,bagOnly=slot==null)
        player.writeBar(hotbar)
        return count
    }
    /** Atomic exact transfer. Capacity and overflow are checked before either side changes. */
    @Synchronized fun transfer(p: Pos, inventory: MutableMap<Short,Int>, id: Short, requested: Int, deposit: Boolean): Boolean {
        if(requested <= 0 || !loaded(p) || !F.isContainer(block(p))) return false
        val box=store(p).items
        val from=if(deposit) inventory else box; val to=if(deposit) box else inventory
        val amount=minOf(requested,(from[id] ?: 0).coerceAtLeast(0),Int.MAX_VALUE-(to[id] ?: 0).coerceAtLeast(0))
        if(amount <= 0) return false
        to[id]=(to[id] ?: 0)+amount
        val left=(from[id] ?: 0)-amount
        if(left==0) from.remove(id) else from[id]=left
        return true
    }
    @Synchronized fun breakBlock(p: Pos): Map<Short,Int> {
        hoppers.remove(p); knownMachines.remove(p)
        if(F.isContainer(block(p))) store(p)
        return stores.remove(p)?.items?.toMap().orEmpty()
    }
    private fun wheel(p: Pos) = block(p)==F.WATERWHEEL && directions.any { d ->
        val q=p.move(d[0],d[1],d[2]); loaded(q) && block(q)==WATER_FLOW
    }
    private fun powered(origin: Pos): Boolean {
        if(wheel(origin)) return true
        val queue=ArrayDeque<Pair<Pos,Int>>(); val seen=hashSetOf(origin)
        queue.add(origin to 0)
        while(queue.isNotEmpty() && seen.size <= 96) {
            val (p,depth)=queue.removeFirst()
            for(d in directions) {
                val q=p.move(d[0],d[1],d[2])
                if(!seen.add(q) || !loaded(q)) continue
                val id=block(q)
                if(id==F.WATERWHEEL && directions.any { v ->
                    val water=q.move(v[0],v[1],v[2]); loaded(water) && block(water)==WATER_FLOW
                }) return true
                if(id==F.SHAFT && depth<12) queue.add(q to depth+1)
            }
        }
        return false
    }
    private fun canProcess(s: Store,input: Map<Short,Int>,output: Map<Short,Int>): Boolean =
        input.all { (id,n)->(s.items[id] ?: 0)>=n } && output.all { (id,n)->
            (s.items[id] ?: 0).toLong()-(input[id] ?: 0)+n<=Int.MAX_VALUE }
    private fun process(s: Store,input: Map<Short,Int>,output: Map<Short,Int>): Boolean {
        if(!canProcess(s,input,output)) return false
        for((id,n) in input) { val left=s.items.getValue(id)-n; if(left==0) s.items.remove(id) else s.items[id]=left }
        for((id,n) in output) s.items[id]=(s.items[id] ?: 0)+n
        return true
    }
    private fun process(s: Store,input: Map<Short,Int>,out: Short,count: Int) = process(s,input,mapOf(out to count))
    @Synchronized fun advance(dt: Float) {
        accumulator+=dt.coerceIn(0f,1f)
        if(accumulator<1f) return
        accumulator-=1f
        // Only nearby, loaded workshops run. No offline production or distant world reads.
        for((p,s) in stores) {
            if(!loaded(p)) continue
            val machine=block(p)
            val fuel=s.items.keys.sorted().firstOrNull { "fuel" in BlockRegistry.get(it)?.tags.orEmpty() && (s.items[it] ?: 0)>0 }
            val recipe=recipes.firstOrNull { r -> r.machine==machine && (s.selection<0 || recipes.getOrNull(s.selection)===r) && (!r.power || powered(p)) &&
                (machine!=F.COOKER || fuel!=null) && canProcess(s,
                    if(machine==F.COOKER) r.input + (fuel!! to 1) else r.input,r.output) }
            if(recipe!=null) {
                val recipeKey=recipe.key
                if(s.active!=recipeKey) { s.active=recipeKey;s.progress=0 }
                if(++s.progress>=recipe.seconds) {
                    s.progress=0
                    process(s,if(machine==F.COOKER) recipe.input+(fuel!! to 1) else recipe.input,recipe.output)
                }
                continue
            }
            if(s.active.isNotEmpty()) { s.active="";s.progress=0 }
            s.progress=0
        }

        for(p in hoppers) {
            if(!loaded(p) || block(p)!=F.HOPPER) continue
            val above=p.move(0,1,0); val below=p.move(0,-1,0)
            if(!loaded(above) || !loaded(below) || !F.isContainer(block(above)) || !F.isContainer(block(below))) continue
            val from=store(above).items; val to=store(below).items
            // Machine outlets export products only; raw material stays inside the machine.
            val id=from.keys.sorted().firstOrNull { id ->
                val machine=block(above)
                val selected=store(above).selection
                val outputs=recipes.filterIndexed { index,r -> r.machine==machine && (selected<0 || selected==index) }
                (to[id] ?: 0)<Int.MAX_VALUE && (outputs.isEmpty() || outputs.any { id in it.output })
            } ?: continue
            to[id]=(to[id] ?: 0)+1
            val left=from.getValue(id)-1; if(left==0) from.remove(id) else from[id]=left
        }
    }
    @Synchronized fun snapshot(): String {
        val rows=JSONArray()
        for((p,s) in stores) {
            s.stacks.reconcile(s.items)
            rows.put(JSONObject().put("x",p.x).put("y",p.y).put("z",p.z).put("stacks",s.stacks.json())
            .put("selectionKey",recipes.getOrNull(s.selection)?.key ?: "").put("active",s.active).put("progress",s.progress)
            .put("items",JSONObject().also { j -> s.items.forEach { (id,n)->j.put(id.toString(),n) } }))
        }
        return JSONObject().put("stores",rows).put("hoppers",JSONArray().also { a ->
            hoppers.forEach { a.put(JSONArray().put(it.x).put(it.y).put(it.z)) }
        }).toString()
    }
    @Synchronized fun restore(json: String) {
        stores.clear(); hoppers.clear()
        val root=runCatching { JSONObject(json) }.getOrNull() ?: return
        val rows=root.optJSONArray("stores") ?: JSONArray()
        for(i in 0 until rows.length()) runCatching {
            val j=rows.getJSONObject(i)
            val s=Store(progress=j.optInt("progress").coerceIn(0,29),active=j.optString("active",""),
                selection=recipes.indexOfFirst { it.key==j.optString("selectionKey","") },stacks=CaveStackInventory(j.optString("stacks","[]")))
            val items=j.optJSONObject("items") ?: JSONObject()
            items.keys().forEach { key ->
                val id=key.toIntOrNull(); val n=items.optInt(key)
                if(id!=null && id in 1..32767 && n>0) s.items[id.toShort()]=n
            }
            stores[Pos(j.getInt("x"),j.getInt("y"),j.getInt("z"))]=s
        }
        val hs=root.optJSONArray("hoppers") ?: return
        for(i in 0 until hs.length()) runCatching { val a=hs.getJSONArray(i); hoppers.add(Pos(a.getInt(0),a.getInt(1),a.getInt(2))) }
    }
}
