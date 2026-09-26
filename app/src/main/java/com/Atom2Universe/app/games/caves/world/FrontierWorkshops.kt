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
    data class View(val pos: Pos, val block: Short, val items: Map<Short,Int>, val powered: Boolean,val selection: Int=-1,val progress: Int=0,val active: Int=-1,val stacks: List<CaveStackInventory.Stack> = emptyList(),val overloaded: Boolean=false,val conflict: Boolean=false,
        /** Forge heat: 0 out, 1 embers, 2 red, 3 white. */
        val heat: Int=0,
        /** Ovens: ingredients are [stacks], fuel and finished products have their own compartments. */
        val zoned: Boolean=false,val fuelStacks: List<CaveStackInventory.Stack> = emptyList(),
        val outputStacks: List<CaveStackInventory.Stack> = emptyList())
    private data class Store(val items: MutableMap<Short,Int> = linkedMapOf(), var progress: Int = 0,var active: String="",var selection: Int=-1,val stacks: CaveStackInventory = CaveStackInventory()) {
        /** Fraction of a second of work carried over: a machine turning at half speed works every other second. */
        var partial=0f
        /** Forge: seconds of fire left from the last fuel, burnt faster the hotter it blows. */
        var burn=0f
        /** Ovens: the fuel and the finished products, kept apart from the ingredients in [items]. */
        val fuel: MutableMap<Short,Int> = linkedMapOf()
        var fuelStacks=CaveStackInventory()
        val output: MutableMap<Short,Int> = linkedMapOf()
        var outputStacks=CaveStackInventory()
        fun items(zone: Int)=when(zone) { ZONE_FUEL -> fuel; ZONE_OUTPUT -> output; else -> items }
        fun stacks(zone: Int)=when(zone) { ZONE_FUEL -> fuelStacks; ZONE_OUTPUT -> outputStacks; else -> stacks }
        fun reconcile() { stacks.reconcile(items); fuelStacks.reconcile(fuel); outputStacks.reconcile(output) }
    }
    /** The two ovens (cooking stove, forge) sort ingredients, fuel and products into three compartments. */
    private fun zoned(id: Short)=id==F.COOKER || id==E.FORGE
    private fun isFuel(id: Short)="fuel" in BlockRegistry.get(id)?.tags.orEmpty()
    /** Stack keys shown to the panel carry their compartment in their top bits. */
    private fun zoneOf(key: Long)=(key ushr ZONE_SHIFT).toInt()
    private fun rawKey(key: Long)=key and ((1L shl ZONE_SHIFT)-1)
    private fun tagged(stacks: List<CaveStackInventory.Stack>,zone: Int)=stacks.map { it.copy(key=it.key or (zone.toLong() shl ZONE_SHIFT)) }
    private val stores = linkedMapOf<Pos,Store>()
    private var visualTimer=0f
    private var feedTimer=0f
    /** A turning part drawn by KineticRenderer: axis 0 = X, 1 = Y, 2 = Z; speed as in KineticNetwork;
     * angle in radians; light 0..1. */
    class Kinetic(val pos: Pos,val block: Short) { var axis=1; var angle=0f; var speed=0f; var light=1f
        /** Crank fixed to a part on the + side of its axis: its mesh is turned round. */
        var flipped=false
        /** Crucible: how far it leans (radians, up to [MAX_TILT]) and whether it is pouring (it leans over
         * its mould, [mold] when that mould is drawn). */
        var tilt=0f; var pouring=false; var mold: Kinetic?=null
        /** Crucible and mould: how full they really are (0..1) and how full they look. The look follows the
         * pour: the crucible empties as it leans past its lip, its mould fills by as much. */
        var fill=0f; var level=0f; var feeding=false }
    private val knownKinetics=linkedMapOf<Pos,Kinetic>()
    /** Turning parts near the player, refreshed once per second; angles advance every frame. */
    val kinetics=ArrayList<Kinetic>()
    private val cranks=hashMapOf<Pos,Float>()
    /** Simulation distance from the pause menu, in blocks: parts and workshops beyond it neither turn nor work. */
    var simulationRadius=64.0
    private var playerX=Double.NaN;private var playerZ=Double.NaN
    private fun simulated(p: Pos)=playerX.isNaN() || (p.x+.5-playerX).pow(2)+(p.z+.5-playerZ).pow(2)<=simulationRadius.pow(2)
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
    /** [heat]: the forge heat a recipe needs (0: no fire, 1 embers, 2 red, 3 white). */
    data class Recipe(val machine: Short,val input: Map<Short,Int>,val output: Map<Short,Int>,val seconds: Int,val power: Boolean=false,val heat: Int=0) {
        val key: String = "$machine/" + input.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" } +
            "/" + output.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }
    }
    val recipes=listOf(
        Recipe(F.COOKER,mapOf(F.FLOUR to 3),mapOf(F.BREAD to 4),10),
        Recipe(F.COOKER,mapOf(9705.toShort() to 3),mapOf(F.BAKED_POTATO to 4),10),
        Recipe(F.MILL,mapOf(9700.toShort() to 1),mapOf(F.FLOUR to 3),6,true),
        Recipe(F.CRUSHER,mapOf(3101.toShort() to 1),mapOf(F.IRON_DUST to 2),8,true),
        Recipe(F.CRUSHER,mapOf(3104.toShort() to 1),mapOf(F.COPPER_DUST to 2),8,true),
        // Cooking stove: every dish that needs a fire (1 fuel per batch).
        Recipe(F.COOKER,mapOf(9705.toShort() to 2,9704.toShort() to 1,9706.toShort() to 1),mapOf(F.STEW to 3),12),
        Recipe(F.COOKER,mapOf(9702.toShort() to 2,9709.toShort() to 1,9710.toShort() to 1,9706.toShort() to 1),mapOf(F.RATATOUILLE to 3),12),
        Recipe(F.COOKER,mapOf(F.FLOUR to 2,9707.toShort() to 3),mapOf(F.BERRY_TART to 3),10),
        Recipe(F.COOKER,mapOf(F.FLOUR to 2,9716.toShort() to 3),mapOf(F.BERRY_TART to 3),10),
        Recipe(F.COOKER,mapOf(F.FLOUR to 2,9717.toShort() to 3),mapOf(F.BERRY_TART to 3),10),
        // Forge furnace: all the metal, glass and fired clay. Its heat comes from fuel and bellows.
        Recipe(E.FORGE,mapOf(LOG to 1),mapOf(CHARCOAL to 1),10,heat=0),
        Recipe(E.FORGE,mapOf(3104.toShort() to 1),mapOf(3115.toShort() to 1),6,heat=1),
        Recipe(E.FORGE,mapOf(F.COPPER_DUST to 1),mapOf(3115.toShort() to 1),4,heat=1),
        Recipe(E.FORGE,mapOf(SAND to 1),mapOf(8003.toShort() to 1),4,heat=1),
        Recipe(E.FORGE,mapOf(2300.toShort() to 1),mapOf(2000.toShort() to 1),4,heat=1),
        Recipe(E.FORGE,mapOf(2304.toShort() to 1),mapOf(2308.toShort() to 1),5,heat=1),
        Recipe(E.FORGE,mapOf(3118.toShort() to 4),mapOf(3119.toShort() to 4),8,heat=1),
        Recipe(E.FORGE,mapOf(3101.toShort() to 1),mapOf(3114.toShort() to 1),8,heat=2),
        Recipe(E.FORGE,mapOf(F.IRON_DUST to 1),mapOf(3114.toShort() to 1),5,heat=2),
        Recipe(E.FORGE,mapOf(3103.toShort() to 1),mapOf(3116.toShort() to 1),6,heat=2),
        Recipe(E.FORGE,mapOf(3102.toShort() to 1),mapOf(3117.toShort() to 1),6,heat=2),
        Recipe(E.FORGE,mapOf(3114.toShort() to 2,CHARCOAL to 1),mapOf(F.STEEL to 1),15,heat=3),
        // Crucible on a forge: every form of a metal melts into measures of it, at that metal's heat.
        Recipe(F.CRUCIBLE,mapOf(3115.toShort() to 1),mapOf(F.MOLTEN_COPPER to 1),3,heat=1),
        Recipe(F.CRUCIBLE,mapOf(F.COPPER_DUST to 1),mapOf(F.MOLTEN_COPPER to 1),3,heat=1),
        Recipe(F.CRUCIBLE,mapOf(3104.toShort() to 1),mapOf(F.MOLTEN_COPPER to 1),4,heat=1),
        Recipe(F.CRUCIBLE,mapOf(3114.toShort() to 1),mapOf(F.MOLTEN_IRON to 1),3,heat=2),
        Recipe(F.CRUCIBLE,mapOf(F.IRON_DUST to 1),mapOf(F.MOLTEN_IRON to 1),3,heat=2),
        Recipe(F.CRUCIBLE,mapOf(3101.toShort() to 1),mapOf(F.MOLTEN_IRON to 1),4,heat=2),
        Recipe(F.CRUCIBLE,mapOf(3116.toShort() to 1),mapOf(F.MOLTEN_GOLD to 1),3,heat=2),
        Recipe(F.CRUCIBLE,mapOf(3103.toShort() to 1),mapOf(F.MOLTEN_GOLD to 1),4,heat=2),
        Recipe(F.CRUCIBLE,mapOf(3117.toShort() to 1),mapOf(F.MOLTEN_SILVER to 1),3,heat=2),
        Recipe(F.CRUCIBLE,mapOf(3102.toShort() to 1),mapOf(F.MOLTEN_SILVER to 1),4,heat=2),
        Recipe(F.CRUCIBLE,mapOf(F.STEEL to 1),mapOf(F.MOLTEN_STEEL to 1),4,heat=3),
        // Casting mould: ingots first (the automatic choice), then plates and the blank. The metal sets in
        // the mould (its time is the cooling), so what comes out is already cold.
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_COPPER to 1),mapOf(3115.toShort() to 1),6),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_IRON to 1),mapOf(3114.toShort() to 1),6),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_STEEL to 1),mapOf(F.STEEL to 1),6),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_GOLD to 1),mapOf(3116.toShort() to 1),6),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_SILVER to 1),mapOf(3117.toShort() to 1),6),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_COPPER to 1),mapOf(F.PLATE to 1),8),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_IRON to 1),mapOf(F.IRON_PLATE to 1),8),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_STEEL to 1),mapOf(E.STEEL_PLATE to 1),8),
        Recipe(F.CAST_MOLD,mapOf(F.MOLTEN_IRON to 2),mapOf(E.BLANK to 1),10),
        // Press: it shapes cast plates, never ingots.
        Recipe(F.PRESS,mapOf(E.STEEL_PLATE to 1),mapOf(F.GEAR to 3),10,true),
        Recipe(F.LOOM,mapOf(F.WOOL to 2),mapOf(F.CLOTH to 3),10,true),
        Recipe(F.VAT,mapOf(F.MILK to 1),mapOf(F.CHEESE to 3,BUCKET_EMPTY to 1),30),
        Recipe(F.COOKER,mapOf(F.EGG to 2,9706.toShort() to 1),mapOf(F.OMELETTE to 2),10),
        Recipe(F.COOKER,mapOf(F.MILK to 1,F.FLOUR to 2,F.EGG to 1),mapOf(F.PANCAKE to 4,BUCKET_EMPTY to 1),12),
        Recipe(F.COOKER,mapOf(F.MILK to 1,F.TRUFFLE to 1,9705.toShort() to 2),mapOf(F.CREAM_SOUP to 3,BUCKET_EMPTY to 1),15)
    ) + listOf(
        Recipe(E.FORGE,mapOf(F.GEODE to 1),mapOf(E.POWDER to 8),12,heat=1),
        Recipe(F.PRESS,mapOf(F.IRON_PLATE to 1),mapOf(E.RIVETS to 8),6,true),
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
        if(id in TURNING || id==F.HOPPER || id==F.CRUCIBLE || id==F.CAST_MOLD) knownKinetics.getOrPut(p) { Kinetic(p,id) }
        if(id==F.MOLD_WET) drying.getOrPut(p) { 0f }
    }
    @Synchronized fun animate(dt: Float,x: Double,y: Double,z: Double,light: (Int,Int,Int)->Float) {
        visualTimer-=dt
        playerX=x;playerZ=z
        if(visualTimer<=0f) {
            visualTimer=1f;kinetics.clear();visibleWindmills.clear()
            // advance() does not run on the exhibition map: solve the rotation here instead.
            if(exhibition) refreshRotation()
            val parts=knownKinetics.iterator()
            while(parts.hasNext()) {
                val k=parts.next().value;val p=k.pos
                if(!loaded(p) || block(p)!=k.block) { parts.remove();continue }
                if(abs(p.y-y)>32 || !simulated(p) || kinetics.size>=MAX_KINETICS) continue
                var turn=1f
                if(k.block in MACHINES) turn=gearboxSide(k)
                else if(k.block==F.HOPPER) {
                    // The spout points where the hopper pours: an axis and which way along it.
                    val d=HOPPER_DIRS[(world.metaAt(p.x,p.y,p.z).toInt() and 7).coerceIn(0,4)]
                    k.axis=if(d[0]!=0) 0 else if(d[1]!=0) 1 else 2;k.flipped=d[0]+d[1]+d[2]<0
                } else if(k.block==F.CRUCIBLE) {
                    // It leans towards its mould: the axis of that side, flipped when the mould is on the - side.
                    val d=moldSide(p)
                    k.axis=if(d==null) -1 else if(d[0]!=0) 0 else 2;k.flipped=d!=null && d[0]+d[2]<0
                    val s=store(p);val mold=spout(p)
                    val molten=F.MOLTEN.sumOf { s.items[it] ?: 0 }
                    k.fill=if(exhibition) 1f else (molten.toFloat()/(mold?.let { moldNeeds(store(it)) } ?: 1)).coerceAtMost(1f)
                    k.pouring=p in tipped
                    k.mold=mold?.let { knownKinetics[it] }
                } else if(k.block==F.CAST_MOLD) {
                    // Flat, not turning: molten metal fills it, then the piece cast in it stays until taken.
                    val s=store(p)
                    k.axis=2;k.flipped=false
                    k.fill=if(s.items.keys.any { it !in F.MOLTEN }) 1f
                        else (F.MOLTEN.sumOf { s.items[it] ?: 0 }.toFloat()/moldNeeds(s)).coerceAtMost(1f)
                } else {
                    val meta=world.metaAt(p.x,p.y,p.z)
                    k.axis=PartialBlockModel.shaftAxis(meta);k.flipped=meta.toInt() and PartialBlockModel.CRANK_ON_PLUS!=0
                }
                // The network only keeps a machine's pace; its way round comes from what drives it.
                k.speed=if(k.block in MACHINES) abs(speedAt(p))*turn else speedAt(p);k.light=light(p.x,p.y,p.z)
                kinetics.add(k)
            }
            for(mill in windmills.values) {
                val p=mill.pos
                if(!loaded(p) || block(p)!=F.WINDMILL) continue
                if(abs(p.y-y)>64 || !simulated(p)) continue
                // The head is a solid block, always dark inside: take the light around it.
                mill.speed=speedAt(p)
                mill.light=maxOf(light(p.x+1,p.y,p.z),light(p.x-1,p.y,p.z),light(p.x,p.y+1,p.z),
                    maxOf(light(p.x,p.y-1,p.z),light(p.x,p.y,p.z+1),light(p.x,p.y,p.z-1)))
                visibleWindmills.add(mill)
            }
        }
        // Angle advances with time, never by a fixed step per frame: smooth at 60 and 120 Hz.
        for(k in kinetics) if(k.speed!=0f) k.angle=(k.angle+k.speed*RADIANS_PER_SPEED*dt)%(2f*PI.toFloat())
        // A crucible leans over its mould while it pours, and rights itself after. On the exhibition map
        // nothing melts: it leans and rights itself in turn, to show how it moves.
        showClock=(showClock+dt)%(2*SHOW_POUR)
        // Its metal runs out once it leans past its lip, into its mould; it keeps leaning until it looks empty.
        for(k in kinetics) k.feeding=false
        for(k in kinetics) if(k.block==F.CRUCIBLE) {
            val pouring=if(exhibition) showClock<SHOW_POUR else k.pouring || k.tilt>0f && k.level>0.01f
            val target=if(pouring && k.axis>=0) MAX_TILT else 0f
            k.tilt=if(k.tilt<target) minOf(target,k.tilt+TILT_SPEED*dt) else maxOf(target,k.tilt-TILT_SPEED*dt)
            val before=k.level
            k.level=when {
                k.tilt>LIP_TILT -> maxOf(0f,k.level-FLOW*dt)
                k.tilt<=0f -> toward(k.level,k.fill,FLOW*dt)
                else -> k.level
            }
            k.mold?.let { m -> if(k.tilt>0f) m.feeding=true; if(k.level<before) m.level=minOf(1f,m.level+before-k.level) }
        }
        // A mould nobody pours into shows what it really holds: empty again once its piece is taken.
        for(k in kinetics) if(k.block==F.CAST_MOLD && !k.feeding) k.level=toward(k.level,k.fill,FLOW*dt)
        for(m in visibleWindmills) if(m.speed!=0f) m.angle=(m.angle+m.speed*RADIANS_PER_SPEED*dt)%(2f*PI.toFloat())
    }
    private fun toward(from: Float,to: Float,step: Float)=if(from<to) minOf(to,from+step) else maxOf(to,from-step)
    /** A machine shows an axle end on the face where a gearbox drives it: axis of that face, flipped when
     * the gearbox is on the - side. Axis -1: no gearbox, nothing drawn. Returns which way that face turns
     * about its +axis (±1), so the machine's parts follow the axle that drives them. */
    private fun gearboxSide(k: Kinetic): Float {
        k.axis=-1
        for(axis in 0..2) for(sign in intArrayOf(1,-1)) {
            val q=when(axis) { 0 -> k.pos.move(sign,0,0); 1 -> k.pos.move(0,sign,0); else -> k.pos.move(0,0,sign) }
            val id=block(q);val qAxis=PartialBlockModel.shaftAxis(world.metaAt(q.x,q.y,q.z))
            val gearbox=id==F.GEARBOX && qAxis!=axis
            // The mill's spindle: a vertical shaft (or any turning axle) coming down onto it.
            val spindle=k.block==F.MILL && axis==1 && sign>0 && id in TURNING && id !in MACHINES && qAxis==1
            if(!gearbox && !spindle) continue
            k.axis=axis;k.flipped=sign<0
            val driver=speedAt(q)
            // A gearbox face at -sign from the gearbox turns at -sign·f·v, f = +1 for the first axis after its own.
            val face=if(spindle) driver else -sign*(if(axis==(qAxis+1)%3) 1 else -1)*driver
            return if(face<0f) -1f else 1f
        }
        return 1f
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
        stores.remove(p); hoppers.remove(p); knownKinetics.remove(p); cranks.remove(p); drying.remove(p)
        if(F.isContainer(id)) stores[p]=Store() // player-placed caches never roll loot
        if(id == F.HOPPER) hoppers.add(p)
        discover(p,id)
    }
    @Synchronized fun view(p: Pos): View? {
        if(!loaded(p) || !F.isContainer(block(p))) return null
        val s=store(p)
        s.reconcile()
        val net=rotation.network[p]
        val zoned=zoned(block(p))
        return View(p,block(p),if(zoned) s.items+s.fuel else s.items.toMap(),speedAt(p)!=0f,s.selection,s.progress,recipes.indexOfFirst { it.key==s.active },
            tagged(s.stacks.snapshot(),ZONE_INPUT),net?.overloaded==true,net?.conflict==true,if(block(p)==E.FORGE && s.burn>0f) 1+bellows(p) else 0,
            zoned,tagged(s.fuelStacks.snapshot(),ZONE_FUEL),tagged(s.outputStacks.snapshot(),ZONE_OUTPUT))
    }
    /** How much the bellows around a forge raise its fire: +1 from speed 16 in all, +2 from 32. */
    private fun bellows(p: Pos): Int {
        var blow=0f
        for(d in NEIGHBOURS) { val q=p.move(d[0],d[1],d[2]); if(block(q)==F.BELLOWS) blow+=abs(speedAt(q)) }
        return when { blow>=2*KineticNetwork.BASE -> 2; blow>=KineticNetwork.BASE -> 1; else -> 0 }
    }
    /** Brick moulds drying: seconds of sun each has had. */
    private val drying=linkedMapOf<Pos,Float>()
    /** Daytime, set by the renderer: moulds only dry under the sun. */
    @Volatile var sunUp=false
    /** Cells whose block changed on their own (a mould that dried): the renderer remeshes them. */
    private val changed=ArrayList<Pos>()
    @Synchronized fun takeChanged(): List<Pos> = changed.toList().also { changed.clear() }
    private fun underSky(p: Pos): Boolean {
        for(y in p.y+1..p.y+SKY_CHECK) {
            val b=world.blockAt(p.x,y,p.z)
            if(b!=AIR && !isTransparent(b) && !isDecoration(b) && !isLeaf(b)) return false
        }
        return true
    }
    private fun dryMolds() {
        val iterator=drying.entries.iterator()
        while(iterator.hasNext()) {
            val entry=iterator.next();val p=entry.key
            if(!loaded(p)) continue
            if(block(p)!=F.MOLD_WET) { iterator.remove();continue }
            if(!sunUp || !simulated(p) || !underSky(p)) continue
            entry.setValue(entry.value+1f)
            if(entry.value>=DRY_SECONDS) {
                world.setBlock(p.x,p.y,p.z,F.MOLD_DRY);changed+=p;iterator.remove()
            }
        }
    }
    @Synchronized fun splitStack(p: Pos,key: Long,count: Int): Boolean {
        if(!loaded(p) || !F.isContainer(block(p))) return false
        val s=store(p);s.reconcile()
        return s.stacks(zoneOf(key)).split(rawKey(key),count)
    }
    /** Reorders a stack inside its compartment, or moves it to [zone] in an oven: ingredients and fuel
     * trade places (fuel only if it burns); nothing goes back into the products. */
    @Synchronized fun moveStack(p: Pos,key: Long,target: Long?,zone: Int=zoneOf(key)): Boolean {
        if(!loaded(p) || !F.isContainer(block(p))) return false
        val s=store(p);s.reconcile()
        val from=zoneOf(key);val into=target?.let { zoneOf(it) } ?: zone
        if(into==from) return s.stacks(from).moveToBag(rawKey(key),target?.let { rawKey(it) })
        val stack=s.stacks(from).get(rawKey(key)) ?: return false
        if(!zoned(block(p)) || into==ZONE_OUTPUT || into==ZONE_FUEL && !isFuel(stack.id)) return false
        val to=s.items(into);val count=minOf(stack.count,Int.MAX_VALUE-(to[stack.id] ?: 0))
        if(count<=0) return false
        val fromItems=s.items(from);val left=fromItems.getValue(stack.id)-count
        if(left==0) fromItems.remove(stack.id) else fromItems[stack.id]=left
        to[stack.id]=(to[stack.id] ?: 0)+count
        s.stacks(from).take(rawKey(key),count);s.stacks(into).receive(stack.id,count,target?.let { rawKey(it) })
        return true
    }
    @Synchronized fun transferStack(p: Pos,inventory: MutableMap<Short,Int>,player: CaveStackInventory,
        hotbar: Array<Short?>,key: Long,deposit: Boolean,target: Long?=null,slot: Int?=null,zone: Int=ZONE_INPUT): Int {
        if(!loaded(p) || !F.isContainer(block(p))) return 0
        val s=store(p);s.reconcile()
        // Depositing: into the compartment dropped on (ovens only); withdrawing: from the stack's own.
        val z=if(!zoned(block(p))) ZONE_INPUT else if(deposit) target?.let { zoneOf(it) } ?: zone else zoneOf(key)
        val box=s.stacks(z);val boxItems=s.items(z)
        val from=if(deposit) player else box;val to=if(deposit) box else player
        val source=from.get(if(deposit) key else rawKey(key)) ?: return 0
        if(deposit && (z==ZONE_OUTPUT || z==ZONE_FUEL && !isFuel(source.id)) || source.id in F.MOLTEN) return 0
        val destination=if(deposit) boxItems else inventory
        val count=minOf(source.count,Int.MAX_VALUE-(destination[source.id] ?: 0).coerceAtLeast(0),if(deposit) room(p,s,source.id) else Int.MAX_VALUE)
        if(count<=0 || !move(boxItems,inventory,source.id,count,deposit)) return 0
        from.take(source.key,count);to.receive(source.id,count,if(deposit) target?.let { rawKey(it) } else target,if(deposit) null else slot,bagOnly=slot==null)
        player.writeBar(hotbar)
        return count
    }
    /** Atomic exact transfer by item. Ovens take fuel into their fuel compartment and give their products first. */
    @Synchronized fun transfer(p: Pos, inventory: MutableMap<Short,Int>, id: Short, requested: Int, deposit: Boolean): Boolean {
        if(requested <= 0 || !loaded(p) || !F.isContainer(block(p)) || id in F.MOLTEN) return false
        val s=store(p)
        val box=if(!zoned(block(p))) s.items
            else if(deposit) (if(isFuel(id)) s.fuel else s.items)
            else listOf(s.output,s.items,s.fuel).firstOrNull { (it[id] ?: 0)>0 } ?: return false
        return move(box,inventory,id,if(deposit) minOf(requested,room(p,s,id)) else requested,deposit)
    }
    /** Capacity and overflow are checked before either side changes. */
    private fun move(box: MutableMap<Short,Int>,inventory: MutableMap<Short,Int>,id: Short,requested: Int,deposit: Boolean): Boolean {
        if(requested <= 0) return false
        val from=if(deposit) inventory else box; val to=if(deposit) box else inventory
        val amount=minOf(requested,(from[id] ?: 0).coerceAtLeast(0),Int.MAX_VALUE-(to[id] ?: 0).coerceAtLeast(0))
        if(amount <= 0) return false
        to[id]=(to[id] ?: 0)+amount
        val left=(from[id] ?: 0)-amount
        if(left==0) from.remove(id) else from[id]=left
        return true
    }
    @Synchronized fun breakBlock(p: Pos): Map<Short,Int> {
        hoppers.remove(p); knownKinetics.remove(p); cranks.remove(p); drying.remove(p)
        if(F.isContainer(block(p))) store(p)
        val contents=mutableMapOf<Short,Int>()
        stores.remove(p)?.let { s -> for(m in listOf(s.items,s.fuel,s.output)) for((id,n) in m) contents[id]=(contents[id] ?: 0)+n }
        // Dry bricks come out of their mould (the bricks are the block's drop): the mould goes back to the bag.
        if(block(p)==F.MOLD_DRY) contents[F.BRICK_MOLD]=(contents[F.BRICK_MOLD] ?: 0)+1
        // Metal still molten sets as ingots when its crucible or mould is broken.
        for(m in F.MOLTEN) contents.remove(m)?.let { n -> val solid=F.SOLID.getValue(m); contents[solid]=(contents[solid] ?: 0)+n }
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
    /** A recipe asking for a stand-in ([LOG], [SAND], [CHARCOAL]) takes any block of that family. */
    private val families: Map<Short,List<Short>> by lazy {
        fun tagged(tag: String)=BlockRegistry.all().filter { tag in it.tags }.map { it.id }.sorted()
        mapOf(LOG to tagged("logs"),SAND to BlockRegistry.all().filter { it.name in SANDS }.map { it.id }.sorted(),
            CHARCOAL to listOf(CHARCOAL,3100.toShort()))
    }
    private fun family(id: Short)=families[id] ?: listOf(id)
    private fun count(s: Store,id: Short)=family(id).sumOf { (s.items[it] ?: 0).toLong() }
    private fun canProcess(s: Store,input: Map<Short,Int>,output: Map<Short,Int>,out: Map<Short,Int> = s.items): Boolean =
        input.all { (id,n)->count(s,id)>=n } && output.all { (id,n)->
            (out[id] ?: 0).toLong()-(if(out===s.items) input[id] ?: 0 else 0)+n<=Int.MAX_VALUE }
    private fun process(s: Store,input: Map<Short,Int>,output: Map<Short,Int>,out: MutableMap<Short,Int> = s.items): Boolean {
        if(!canProcess(s,input,output,out)) return false
        for((wanted,n) in input) {
            var left=n
            for(id in family(wanted)) {
                val take=minOf(left,s.items[id] ?: 0); if(take==0) continue
                val rest=s.items.getValue(id)-take; if(rest==0) s.items.remove(id) else s.items[id]=rest
                left-=take
            }
        }
        for((id,n) in output) out[id]=(out[id] ?: 0)+n
        return true
    }
    private fun process(s: Store,input: Map<Short,Int>,out: Short,count: Int) = process(s,input,mapOf(out to count))
    @Synchronized fun advance(dt: Float) {
        accumulator+=dt.coerceIn(0f,1f)
        if(accumulator<1f) return
        accumulator-=1f
        for(entry in cranks.entries) entry.setValue(entry.value-1f)
        refreshRotation()
        dryMolds()
        pour()
        // Only nearby, loaded workshops run. No offline production or distant world reads.
        for((p,s) in stores) {
            if(!loaded(p) || !simulated(p)) continue
            val machine=block(p)
            // Ovens burn from their fuel compartment and put their products apart, so a product is never re-used.
            val fuelBox=if(zoned(machine)) s.fuel else s.items
            val out=if(zoned(machine)) s.output else s.items
            val fuel=fuelBox.keys.sorted().firstOrNull { isFuel(it) && (fuelBox[it] ?: 0)>0 }
            fun burnOne() { val left=fuelBox.getValue(fuel!!)-1; if(left==0) fuelBox.remove(fuel) else fuelBox[fuel]=left }
            // The forge's heat: its fire (lit, or fuel ready to light it) raised by the bellows beside it.
            // A crucible takes the heat of the forge it sits on, and keeps that forge's fire going.
            val hearth=if(machine==E.FORGE) p else if(machine==F.CRUCIBLE && block(p.move(0,-1,0))==E.FORGE) p.move(0,-1,0) else null
            val fire=hearth?.let { store(it) }
            val fireFuel=fire?.fuel?.keys?.sorted()?.firstOrNull { isFuel(it) && (fire.fuel[it] ?: 0)>0 }
            val heat=if(fire==null || fire.burn<=0f && fireFuel==null) 0 else 1+bellows(hearth!!)
            val recipe=recipes.firstOrNull { r -> r.machine==machine && (s.selection<0 || recipes.getOrNull(s.selection)===r) && (!r.power || speedAt(p)!=0f) &&
                (machine!=F.COOKER || fuel!=null) && heat>=r.heat && canProcess(s,r.input,r.output,out) &&
                (machine!=F.CRUCIBLE || crucibleTakes(s,r.output.keys.first())) }
            if(recipe!=null) {
                if(recipe.heat>0 && fire!=null) {
                    // Keep the fire going: a fuel lasts FORGE_FUEL_SECONDS on embers, half as long red, a third white.
                    if(fire.burn<=0f) {
                        val left=fire.fuel.getValue(fireFuel!!)-1; if(left==0) fire.fuel.remove(fireFuel) else fire.fuel[fireFuel]=left
                        fire.burn=FORGE_FUEL_SECONDS
                    }
                    fire.burn-=heat
                }
                val recipeKey=recipe.key
                if(s.active!=recipeKey) { s.active=recipeKey;s.progress=0;s.partial=0f }
                if(recipe.power) {
                    s.partial+=1f/KineticNetwork.durationFactor(speedAt(p))
                    val seconds=s.partial.toInt();s.partial-=seconds;s.progress+=seconds
                } else s.progress++
                if(s.progress>=recipe.seconds) {
                    s.progress=0
                    if(process(s,recipe.input,recipe.output,out) && machine==F.COOKER) burnOne()
                }
                continue
            }
            if(s.active.isNotEmpty()) { s.active="";s.progress=0 }
            s.progress=0
        }

        // Hoppers: each second, take one item from the container above into their own small stock, and pour
        // one item into the container they point at. A row of hoppers carries items along.
        for(p in hoppers) {
            if(!loaded(p) || block(p)!=F.HOPPER || !simulated(p)) continue
            val box=store(p).items
            val above=p.move(0,1,0)
            // Another hopper above pours by itself: never pull from it, or items would move twice.
            if(loaded(above) && F.isContainer(block(above)) && block(above)!=F.HOPPER) {
                val machine=block(above);val source=store(above)
                // A machine gives only its products, an oven only its output; raw material stays inside.
                val from=if(zoned(machine)) source.output else source.items
                val outputs=recipes.filterIndexed { index,r -> r.machine==machine && (source.selection<0 || source.selection==index) }
                val id=from.keys.sorted().firstOrNull { id ->
                    hopperTakes(box,id) && (zoned(machine) || outputs.isEmpty() || outputs.any { id in it.output }) }
                if(id!=null) moveOne(from,box,id)
            }
            val d=HOPPER_DIRS[(world.metaAt(p.x,p.y,p.z).toInt() and 7).coerceIn(0,4)]
            val target=p.move(d[0],d[1],d[2])
            if(!loaded(target) || !F.isContainer(block(target))) continue
            val into=store(target)
            fun destination(id: Short)=if(zoned(block(target)) && isFuel(id)) into.fuel else into.items
            val id=box.keys.sorted().firstOrNull { id ->
                if(block(target)==F.HOPPER) hopperTakes(into.items,id) else (destination(id)[id] ?: 0)<Int.MAX_VALUE && room(target,into,id)>0 } ?: continue
            moveOne(box,destination(id),id)
        }
    }
    /** One metal at a time in a crucible (a melting recipe only gives one kind of metal). */
    private fun crucibleTakes(s: Store,molten: Short)=s.items.keys.none { it in F.MOLTEN && it!=molten }
    /** Which molten metal an item melts into, if any. */
    private fun meltsInto(id: Short)=recipes.firstOrNull { it.machine==F.CRUCIBLE && id in it.input }?.output?.keys?.first()
    /** Where a crucible pours: a mould beside it at its own height (a hopper can then sit under the mould),
     * or on the ground beside the forge it sits on. */
    private fun spout(p: Pos): Pos? {
        for(dy in intArrayOf(0,-1)) for(d in HOPPER_DIRS.drop(1)) {
            val q=p.move(d[0],dy,d[2]); if(block(q)==F.CAST_MOLD) return q
        }
        return null
    }
    /** The side it leans to: the direction of its mould. */
    private fun moldSide(p: Pos): IntArray? = spout(p)?.let { q -> intArrayOf(q.x-p.x,0,q.z-p.z) }
    /** How many more of [id] a container takes. A crucible holds one metal, and just what its mould needs
     * for one piece (one measure without a mould). */
    private fun room(p: Pos,s: Store,id: Short): Int {
        if(block(p)!=F.CRUCIBLE) return Int.MAX_VALUE
        val metal=meltsInto(id) ?: return 0
        val present=s.items.keys.mapNotNull { if(it in F.MOLTEN) it else meltsInto(it) }.toSet()
        if(present.any { it!=metal }) return 0
        val batch=spout(p)?.let { moldNeeds(store(it)) } ?: 1
        return (batch-s.items.values.sum()).coerceAtLeast(0)
    }
    /** Crucibles leaning over their mould right now. */
    private val tipped=HashSet<Pos>()
    private var showClock=0f
    /** Measures a mould waits for: its chosen shape, or one (an ingot) when left automatic. */
    private fun moldNeeds(s: Store)=recipes.getOrNull(s.selection)?.takeIf { it.machine==F.CAST_MOLD }?.input?.values?.sum() ?: 1
    /** Once all its metal has melted, a crucible leans over its mould and pours one measure a second,
     * one piece at a time; then it rights itself for the next batch. */
    private fun pour() {
        tipped.clear()
        for((p,s) in stores) {
            if(!loaded(p) || block(p)!=F.CRUCIBLE || !simulated(p)) continue
            if(s.items.keys.any { it !in F.MOLTEN }) continue
            val metal=s.items.keys.firstOrNull { it in F.MOLTEN } ?: continue
            val target=spout(p) ?: continue
            if(!loaded(target)) continue
            val mold=store(target)
            if(mold.items.keys.any { it !in F.MOLTEN || it!=metal }) continue
            if((mold.items[metal] ?: 0)>=moldNeeds(mold)) continue
            moveOne(s.items,mold.items,metal)
            tipped+=p
        }
    }
    /** A hopper holds up to [HOPPER_KINDS] kinds of item, [HOPPER_STACK] of each. */
    private fun hopperTakes(box: Map<Short,Int>,id: Short)=id !in F.MOLTEN && (box[id] ?: 0)<HOPPER_STACK && (id in box || box.size<HOPPER_KINDS)
    private fun moveOne(from: MutableMap<Short,Int>,to: MutableMap<Short,Int>,id: Short) {
        val left=from.getValue(id)-1; if(left==0) from.remove(id) else from[id]=left
        to[id]=(to[id] ?: 0)+1
    }
    @Synchronized fun snapshot(): String {
        val rows=JSONArray()
        for((p,s) in stores) {
            s.reconcile()
            rows.put(JSONObject().put("x",p.x).put("y",p.y).put("z",p.z).put("stacks",s.stacks.json())
            .put("fuelStacks",s.fuelStacks.json()).put("outputStacks",s.outputStacks.json())
            .put("fuel",JSONObject().also { j -> s.fuel.forEach { (id,n)->j.put(id.toString(),n) } })
            .put("output",JSONObject().also { j -> s.output.forEach { (id,n)->j.put(id.toString(),n) } })
            .put("selectionKey",recipes.getOrNull(s.selection)?.key ?: "").put("active",s.active).put("progress",s.progress).put("burn",s.burn.toDouble())
            .put("items",JSONObject().also { j -> s.items.forEach { (id,n)->j.put(id.toString(),n) } }))
        }
        return JSONObject().put("stores",rows).put("hoppers",JSONArray().also { a ->
            hoppers.forEach { a.put(JSONArray().put(it.x).put(it.y).put(it.z)) }
        }).put("drying",JSONArray().also { a ->
            drying.forEach { (p,t) -> a.put(JSONArray().put(p.x).put(p.y).put(p.z).put(t.toDouble())) }
        }).put("windmills",JSONArray().also { a ->
            // The sails are not in the world while they turn: the save is their only copy.
            for(m in windmills.values) a.put(JSONObject().put("x",m.pos.x).put("y",m.pos.y).put("z",m.pos.z).put("axis",m.axis)
                .put("sails",JSONArray().also { s -> m.sails.forEach { v -> s.put(JSONArray().put(v[0]).put(v[1]).put(v[2]).put(v[3])) } }))
        }).toString()
    }
    @Synchronized fun restore(json: String) {
        stores.clear(); hoppers.clear(); windmills.clear(); drying.clear()
        val root=runCatching { JSONObject(json) }.getOrNull() ?: return
        val mills=root.optJSONArray("windmills") ?: JSONArray()
        for(i in 0 until mills.length()) runCatching {
            val j=mills.getJSONObject(i);val s=j.getJSONArray("sails")
            val p=Pos(j.getInt("x"),j.getInt("y"),j.getInt("z"))
            windmills[p]=Windmill(p,j.getInt("axis"),(0 until s.length()).map { k ->
                val v=s.getJSONArray(k);intArrayOf(v.getInt(0),v.getInt(1),v.getInt(2),v.getInt(3)) })
        }
        val dry=root.optJSONArray("drying") ?: JSONArray()
        for(i in 0 until dry.length()) runCatching {
            val a=dry.getJSONArray(i); drying[Pos(a.getInt(0),a.getInt(1),a.getInt(2))]=a.getDouble(3).toFloat()
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
            s.burn=j.optDouble("burn",0.0).toFloat()
            for((name,into) in listOf("fuel" to s.fuel,"output" to s.output)) {
                val o=j.optJSONObject(name) ?: continue
                o.keys().forEach { key -> val id=key.toIntOrNull(); val n=o.optInt(key); if(id!=null && id in 1..32767 && n>0) into[id.toShort()]=n }
            }
            s.fuelStacks=CaveStackInventory(j.optString("fuelStacks","[]"));s.outputStacks=CaveStackInventory(j.optString("outputStacks","[]"))
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
        val sources=knownKinetics.keys.filter { loaded(it) && simulated(it) && block(it) in SOURCES } +
            windmills.keys.filter { loaded(it) && simulated(it) && block(it)==F.WINDMILL }
        rotation=network.solve(sources)
    }
    companion object {
        const val MAX_KINETICS=512
        /** Machines driven by the network: drawn whole by KineticRenderer, with a part that shows their work. */
        val MACHINES=setOf(F.MILL,F.PRESS,F.CRUSHER,F.LOOM,F.BELLOWS)
        /** Exhibition map: the crucible leans and rights itself every this many seconds. */
        const val SHOW_POUR=3f
        /** A crucible leans this far to pour, at this pace (radians, radians a second). */
        const val MAX_TILT=1.9f
        const val TILT_SPEED=1.4f
        /** The crucible's metal starts running out past this lean (radians), this much of it a second. */
        const val LIP_TILT=.6f
        const val FLOW=.8f
        /** Stand-ins in recipes for a family of blocks: any log, any sand, charcoal or coal. */
        val LOG: Short=1000
        val SAND: Short=4000
        val CHARCOAL: Short=3113
        private val SANDS=setOf("sand","redsand","greysand")
        /** Where a hopper pours, by its meta: down, +X, -X, +Z, -Z. */
        val HOPPER_DIRS=arrayOf(intArrayOf(0,-1,0),intArrayOf(1,0,0),intArrayOf(-1,0,0),intArrayOf(0,0,1),intArrayOf(0,0,-1))
        /** Meta of a hopper pouring towards (dx, dy, dz); anything not sideways pours down. */
        fun hopperMeta(dx: Int,dy: Int,dz: Int): Byte=when {
            dy!=0 -> 0; dx>0 -> 1; dx<0 -> 2; dz>0 -> 3; dz<0 -> 4; else -> 0
        }.toByte()
        const val HOPPER_KINDS=5
        const val HOPPER_STACK=64
        const val ZONE_INPUT=0
        const val ZONE_FUEL=1
        const val ZONE_OUTPUT=2
        private const val ZONE_SHIFT=48
        private val NEIGHBOURS=arrayOf(intArrayOf(1,0,0),intArrayOf(-1,0,0),intArrayOf(0,1,0),intArrayOf(0,-1,0),intArrayOf(0,0,1),intArrayOf(0,0,-1))
        /** Seconds of embers per fuel in the forge. */
        const val FORGE_FUEL_SECONDS=24f
        /** A day of sun (the daytime of a 30 min day) dries a brick mould. */
        const val DRY_SECONDS=1200f
        private const val SKY_CHECK=96
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
