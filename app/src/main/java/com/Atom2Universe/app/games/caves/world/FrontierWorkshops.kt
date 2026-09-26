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

/** Persistent local inventories. All game mutations happen on the GL thread. */
internal class FrontierWorkshops(private val world: World, private val seed: Long) {
    data class Pos(val x: Int, val y: Int, val z: Int) {
        fun move(dx: Int, dy: Int, dz: Int) = Pos(x+dx,y+dy,z+dz)
    }
    data class View(val pos: Pos, val block: Short, val items: Map<Short,Int>, val powered: Boolean,val selection: Int=-1,val progress: Int=0,val active: Int=-1,val stacks: List<CaveStackInventory.Stack> = emptyList(),val overloaded: Boolean=false,val conflict: Boolean=false)
    private data class Store(val items: MutableMap<Short,Int> = linkedMapOf(), var progress: Int = 0,var active: String="",var selection: Int=-1,val stacks: CaveStackInventory = CaveStackInventory()) {
        /** Fraction of a second of work carried over: a machine turning at half speed works every other second. */
        var partial=0f
    }
    private val stores = linkedMapOf<Pos,Store>()
    private var visualTimer=0f
    private var feedTimer=0f
    /** A turning part drawn by KineticRenderer: axis 0 = X, 1 = Y, 2 = Z; speed as in KineticNetwork;
     * angle in radians; light 0..1. */
    class Kinetic(val pos: Pos,val block: Short) { var axis=1; var angle=0f; var speed=0f; var light=1f
        /** Crank fixed to a part on the + side of its axis: its mesh is turned round. */
        var flipped=false }
    private val knownKinetics=linkedMapOf<Pos,Kinetic>()
    /** Turning parts near the player, refreshed once per second; angles advance every frame. */
    val kinetics=ArrayList<Kinetic>()
    private val cranks=hashMapOf<Pos,Float>()
    /** Exhibition map: every source turns (no flowing water, nobody at the cranks), nothing is produced. */
    var exhibition=false
    private val network=KineticNetwork({ x,y,z -> world.blockAt(x,y,z) },{ x,y,z -> world.metaAt(x,y,z) },::drive) { p ->
        windmills[p]?.let { minOf(it.sails.size,MAX_SAIL_FORCE).toFloat() } ?: 0f
    }
    /** An assembled windmill: its sails left the world and turn as one piece around the head's axis.
     * Each sail is (dx, dy, dz, block) from the head. */
    class Windmill(val pos: Pos,val axis: Int,val sails: List<IntArray>) { var angle=0f; var speed=0f; var light=1f
        /** Which way the wind turns it, from the shape of its sails (KineticNetwork.DRIVE_*). A sail beside an arm
         * catches the wind on that side, so the arm moves away from it: seen from the front of a pinwheel whose
         * blades sit counter-clockwise of their arms, it turns counter-clockwise. A symmetric mill follows its network. */
        val drive: Int = run {
            val u=(axis+1)%3;val v=(axis+2)%3;var lean=0
            for(s in sails) {
                val a=s[u];val b=s[v]
                if(abs(a)>abs(b) && b!=0) lean+=a.sign*b.sign
                else if(abs(b)>abs(a) && a!=0) lean-=a.sign*b.sign
            }
            when { lean>0 -> KineticNetwork.DRIVE_NEGATIVE; lean<0 -> KineticNetwork.DRIVE_POSITIVE; else -> KineticNetwork.DRIVE_EITHER }
        } }
    private val windmills=linkedMapOf<Pos,Windmill>()
    /** Windmills near the player, refreshed with the other turning parts. */
    val visibleWindmills=ArrayList<Windmill>()
    private val current=DoubleArray(4)
    /** Which way a source turns right now (KineticNetwork.DRIVE_*). */
    private fun drive(p: Pos,id: Short): Int {
        if(id==F.WINDMILL) return windmills[p]?.drive ?: KineticNetwork.DRIVE_NONE
        if(exhibition) return KineticNetwork.DRIVE_EITHER
        if(id==F.CRANK) return if((cranks[p] ?: 0f)>0f) KineticNetwork.DRIVE_EITHER else KineticNetwork.DRIVE_NONE
        val meta=world.metaAt(p.x,p.y,p.z).toInt()
        // The current pushing on each paddle cell turns the wheel one way or the other; they add up.
        val axis=PartialBlockModel.shaftAxis(meta.toByte());val u=(axis+1)%3;val v=(axis+2)%3
        val reach=if(id==F.LARGE_WATERWHEEL) 2 else 1
        val offset=IntArray(3);var torque=0.0
        for(a in -reach..reach) for(b in -reach..reach) {
            if(a==0 && b==0 || reach==2 && abs(a)==2 && abs(b)==2) continue
            offset.fill(0);offset[u]=a;offset[v]=b
            WaterCurrent.sample(world,p.x+offset[0]+.5,p.y+offset[1]+.5,p.z+offset[2]+.5,current)
            if(current[3]==0.0) continue
            torque+=a*current[v]-b*current[u]
        }
        return when { torque>1e-6 -> KineticNetwork.DRIVE_POSITIVE; torque< -1e-6 -> KineticNetwork.DRIVE_NEGATIVE; else -> KineticNetwork.DRIVE_NONE }
    }
    private var rotation=KineticNetwork.Result(emptyMap(),emptyMap())
    private fun speedAt(p: Pos)=rotation.speedAt(p)
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
        if(id in TURNING) knownKinetics.getOrPut(p) { Kinetic(p,id) }
    }
    @Synchronized fun animate(dt: Float,x: Double,y: Double,z: Double,light: (Int,Int,Int)->Float) {
        visualTimer-=dt
        if(visualTimer<=0f) {
            visualTimer=1f;kinetics.clear();visibleWindmills.clear()
            // advance() does not run on the exhibition map: solve the rotation here instead.
            if(exhibition) refreshRotation()
            val parts=knownKinetics.iterator()
            while(parts.hasNext()) {
                val k=parts.next().value;val p=k.pos
                if(!loaded(p) || block(p)!=k.block) { parts.remove();continue }
                if(abs(p.y-y)>32 || (p.x-x).pow(2)+(p.z-z).pow(2)>48.0.pow(2) || kinetics.size>=MAX_KINETICS) continue
                val meta=world.metaAt(p.x,p.y,p.z)
                k.axis=PartialBlockModel.shaftAxis(meta);k.flipped=meta.toInt() and PartialBlockModel.CRANK_ON_PLUS!=0
                k.speed=speedAt(p);k.light=light(p.x,p.y,p.z)
                kinetics.add(k)
            }
            for(mill in windmills.values) {
                val p=mill.pos
                if(!loaded(p) || block(p)!=F.WINDMILL) continue
                if(abs(p.y-y)>64 || (p.x-x).pow(2)+(p.z-z).pow(2)>64.0.pow(2)) continue
                // The head is a solid block, always dark inside: take the light around it.
                mill.speed=speedAt(p)
                mill.light=maxOf(light(p.x+1,p.y,p.z),light(p.x-1,p.y,p.z),light(p.x,p.y+1,p.z),
                    maxOf(light(p.x,p.y-1,p.z),light(p.x,p.y,p.z+1),light(p.x,p.y,p.z-1)))
                visibleWindmills.add(mill)
            }
        }
        // Angle advances with time, never by a fixed step per frame: smooth at 60 and 120 Hz.
        for(k in kinetics) if(k.speed!=0f) k.angle=(k.angle+k.speed*RADIANS_PER_SPEED*dt)%(2f*PI.toFloat())
        for(m in visibleWindmills) if(m.speed!=0f) m.angle=(m.angle+m.speed*RADIANS_PER_SPEED*dt)%(2f*PI.toFloat())
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
        stores.remove(p); hoppers.remove(p); knownKinetics.remove(p); cranks.remove(p)
        if(F.isContainer(id)) stores[p]=Store() // player-placed caches never roll loot
        if(id == F.HOPPER) hoppers.add(p)
        discover(p,id)
    }
    @Synchronized fun view(p: Pos): View? {
        if(!loaded(p) || !F.isContainer(block(p))) return null
        val s=store(p)
        s.stacks.reconcile(s.items)
        val net=rotation.network[p]
        return View(p,block(p),s.items.toMap(),speedAt(p)!=0f,s.selection,s.progress,recipes.indexOfFirst { it.key==s.active },s.stacks.snapshot(),
            net?.overloaded==true,net?.conflict==true)
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
        hoppers.remove(p); knownKinetics.remove(p); cranks.remove(p)
        if(F.isContainer(block(p))) store(p)
        val contents=stores.remove(p)?.items?.toMutableMap() ?: mutableMapOf()
        // Breaking a turning windmill head gives its sails back.
        windmills.remove(p)?.let { mill ->
            for(s in mill.sails) contents[s[3].toShort()]=(contents[s[3].toShort()] ?: 0)+1
            refreshRotation()
        }
        return contents
    }
    /** What happened when the player used a windmill head. */
    class WindmillChange(val changed: List<Pos>,val returned: Map<Short,Int>,val tooFewSails: Boolean=false)
    /** Starts a windmill (its joined sails leave the world and turn) or stops it (they go back in place;
     * a sail whose cell is now taken returns to the bag). */
    @Synchronized fun toggleWindmill(p: Pos): WindmillChange {
        if(!loaded(p) || block(p)!=F.WINDMILL) return WindmillChange(emptyList(),emptyMap())
        windmills.remove(p)?.let { mill ->
            val changed=ArrayList<Pos>();val returned=HashMap<Short,Int>()
            for(s in mill.sails) {
                val q=p.move(s[0],s[1],s[2]);val id=s[3].toShort()
                if(loaded(q) && block(q)==AIR) { world.setBlock(q.x,q.y,q.z,id);changed+=q }
                else returned[id]=(returned[id] ?: 0)+1
            }
            refreshRotation()
            return WindmillChange(changed,returned)
        }
        val axis=PartialBlockModel.shaftAxis(world.metaAt(p.x,p.y,p.z))
        val step=when(axis) { 0 -> intArrayOf(1,0,0); 1 -> intArrayOf(0,1,0); else -> intArrayOf(0,0,1) }
        val found=LinkedHashSet<Pos>();val queue=ArrayDeque<Pos>()
        for(sign in intArrayOf(1,-1)) {
            val q=p.move(step[0]*sign,step[1]*sign,step[2]*sign)
            if(loaded(q) && block(q)==F.SAIL && found.add(q)) queue.add(q)
        }
        while(queue.isNotEmpty() && found.size<MAX_SAILS) {
            val q=queue.removeFirst()
            for(d in arrayOf(intArrayOf(1,0,0),intArrayOf(-1,0,0),intArrayOf(0,1,0),intArrayOf(0,-1,0),intArrayOf(0,0,1),intArrayOf(0,0,-1))) {
                val r=q.move(d[0],d[1],d[2])
                if(r!=p && loaded(r) && block(r)==F.SAIL && found.size<MAX_SAILS && found.add(r)) queue.add(r)
            }
        }
        if(found.size<MIN_SAILS) return WindmillChange(emptyList(),emptyMap(),tooFewSails=true)
        val sails=found.map { intArrayOf(it.x-p.x,it.y-p.y,it.z-p.z,block(it).toInt()) }
        for(q in found) world.setBlock(q.x,q.y,q.z,AIR)
        windmills[p]=Windmill(p,axis,sails)
        refreshRotation()
        return WindmillChange(found.toList(),emptyMap())
    }
    /** Exhibition map: a windmill that turns from the start, its sails never placed as blocks. */
    @Synchronized fun addWindmill(p: Pos,axis: Int,sails: List<IntArray>) { windmills[p]=Windmill(p,axis,sails) }
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
        for(entry in cranks.entries) entry.setValue(entry.value-1f)
        refreshRotation()
        // Only nearby, loaded workshops run. No offline production or distant world reads.
        for((p,s) in stores) {
            if(!loaded(p)) continue
            val machine=block(p)
            val fuel=s.items.keys.sorted().firstOrNull { "fuel" in BlockRegistry.get(it)?.tags.orEmpty() && (s.items[it] ?: 0)>0 }
            val recipe=recipes.firstOrNull { r -> r.machine==machine && (s.selection<0 || recipes.getOrNull(s.selection)===r) && (!r.power || speedAt(p)!=0f) &&
                (machine!=F.COOKER || fuel!=null) && canProcess(s,
                    if(machine==F.COOKER) r.input + (fuel!! to 1) else r.input,r.output) }
            if(recipe!=null) {
                val recipeKey=recipe.key
                if(s.active!=recipeKey) { s.active=recipeKey;s.progress=0;s.partial=0f }
                if(recipe.power) {
                    s.partial+=1f/KineticNetwork.durationFactor(speedAt(p))
                    val seconds=s.partial.toInt();s.partial-=seconds;s.progress+=seconds
                } else s.progress++
                if(s.progress>=recipe.seconds) {
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
        }).put("windmills",JSONArray().also { a ->
            // The sails are not in the world while they turn: the save is their only copy.
            for(m in windmills.values) a.put(JSONObject().put("x",m.pos.x).put("y",m.pos.y).put("z",m.pos.z).put("axis",m.axis)
                .put("sails",JSONArray().also { s -> m.sails.forEach { v -> s.put(JSONArray().put(v[0]).put(v[1]).put(v[2]).put(v[3])) } }))
        }).toString()
    }
    @Synchronized fun restore(json: String) {
        stores.clear(); hoppers.clear(); windmills.clear()
        val root=runCatching { JSONObject(json) }.getOrNull() ?: return
        val mills=root.optJSONArray("windmills") ?: JSONArray()
        for(i in 0 until mills.length()) runCatching {
            val j=mills.getJSONObject(i);val s=j.getJSONArray("sails")
            val p=Pos(j.getInt("x"),j.getInt("y"),j.getInt("z"))
            windmills[p]=Windmill(p,j.getInt("axis"),(0 until s.length()).map { k ->
                val v=s.getJSONArray(k);intArrayOf(v.getInt(0),v.getInt(1),v.getInt(2),v.getInt(3)) })
        }
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
    /** One turn of the handle: the crank keeps going a moment, so it turns as long as the player keeps at it. */
    @Synchronized fun crank(p: Pos) {
        if(!loaded(p) || block(p)!=F.CRANK) return
        val idle=(cranks[p] ?: 0f)<=0f
        cranks[p]=minOf((cranks[p] ?: 0f)+CRANK_PULSE,CRANK_MAX)
        if(idle) refreshRotation()
    }
    private fun refreshRotation() {
        cranks.entries.removeAll { (p,t) -> !loaded(p) || block(p)!=F.CRANK || t<=0f }
        // Every known wheel and crank is a candidate; the driving rule says which ones turn right now.
        val sources=knownKinetics.keys.filter { loaded(it) && block(it) in SOURCES } +
            windmills.keys.filter { loaded(it) && block(it)==F.WINDMILL }
        rotation=network.solve(sources)
    }
    companion object {
        const val MAX_KINETICS=512
        /** Machines driven by the network: drawn whole by KineticRenderer, with a part that shows their work. */
        val MACHINES=setOf(F.MILL,F.PRESS,F.CRUSHER,F.LOOM)
        val TURNING=setOf(F.SHAFT,F.COGWHEEL,F.LARGE_COGWHEEL,F.CRANK,F.WATERWHEEL,F.LARGE_WATERWHEEL)+MACHINES
        val SOURCES=setOf(F.WATERWHEEL,F.LARGE_WATERWHEEL,F.CRANK)
        /** A water wheel (speed 16) turns half a turn per second. */
        const val RADIANS_PER_SPEED=(PI/16).toFloat()
        /** Seconds of turning per use of a crank, and the most it can store ahead. */
        const val CRANK_PULSE=2f
        const val CRANK_MAX=4f
        const val MIN_SAILS=4
        const val MAX_SAILS=256
        /** One force per sail, up to this. */
        const val MAX_SAIL_FORCE=32
    }
}
