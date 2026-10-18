package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.node.MobRegistry
import com.Atom2Universe.app.games.caves.node.MineralItems as M
import com.Atom2Universe.app.games.caves.world.CombatTrainingMap
import com.Atom2Universe.app.games.caves.world.MapSource
import kotlin.math.floor

/** Repeatable survival encounters, selected from the Assault map menu. No campaign or rewards. */
internal class CombatTrainingMode(private val r: CaveRenderer, private val source: MapSource) : GameMode {
    override val allowsWorldEdits = false
    override val usesSurvivalCombat = true
    override val infiniteAmmo = true
    override val fixedTimeOfDayMs = 600_000L
    override val headshotMultiplier = 1.4f
    val health = TrainingHealth()
    var selected = 0
        private set
    @Volatile var onStatus: ((Int, Int, Int) -> Unit)? = null
    private val pending = mutableListOf<Enemy>()
    private var lastStatus: Triple<Int,Int,Int>? = null
    private var lastHp = -1

    override fun spawnPoint() = source.spawnPoint(selected)

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        r.enemyManager.apply {
            targets=r.sim.players; eventBus=r.eventBus
            spawnManager.enabled=false
            explorationCombat=true
            clearSight=r::clearCombatLine
            meleeImpact={ enemy,_,dx,dz -> r.expeditionCombat.receive(enemy.scaledDamage,dx,dz,enemy) }
            rangedImpact=r::fireExplorationProjectile
        }
        r.playerNode.setMaxHp(health.maxHp,health.maxHp)
        r.playerNode.setMaxShield(0)
        r.playerNode.damagePreview=health::damage
        prepareEncounter()
    }

    override fun onPlayerPlaced(x: Double, y: Double, z: Double) {
        r.camera.yaw=0f; r.camera.pitch=0f
        r.enemyManager.worldSpawnX=x; r.enemyManager.worldSpawnY=y; r.enemyManager.worldSpawnZ=z
    }

    /** GL thread: both selecting a structure and restarting clear every previous attack. */
    fun enter(index: Int) {
        require(index in CombatTrainingMap.bays.indices)
        selected=index
        prepareEncounter()
        val spawn=spawnPoint()
        r.restartTrainingAt(spawn)
        onPlayerPlaced(spawn[0].toDouble(),spawn[1].toDouble(),spawn[2].toDouble())
    }

    private fun prepareEncounter() {
        r.enemyManager.enemies.clear(); pending.clear(); r.projectiles.clear()
        health.reset(); lastHp=-1; lastStatus=null
        r.inventory.clear(); r.hotbar.fill(null)
        weapons.forEachIndexed { i,id -> r.inventory[id]=1; r.hotbar[i]=id }
        r.inventory[8010]=1; r.inventory[8011]=1
        r.selectSlot(0)
        r.notifyHotbar(); r.inventoryCallback?.invoke(r.inventory.toMap()); r.loadoutChangedCallback?.invoke()
        val bay=CombatTrainingMap.bays[selected]
        val def=MobRegistry.get(bay.kind.mob)
        val pads=bay.plan.spawnPoints.take(bay.kind.population)
        (pads + bay.plan.boss).forEachIndexed { i,p ->
            pending += Enemy(10_000+selected*100+i,def,
                source.originX+bay.x+p.x+.5, source.originY+CombatTrainingMap.FLOOR+p.y.toDouble(),
                source.originZ+bay.z+p.z+.5).apply {
                exploration=true; level=1
                isBoss=i==pads.size
                undergroundSiteId="training_$selected"
                hp=maxHp
            }
        }
        publishStatus()
    }

    override fun onPlayerShot(damage: Int, dirX: Double, dirZ: Double) {
        r.expeditionCombat.receive(damage,dirX.toFloat(),dirZ.toFloat())
    }

    override fun update(dt: Float) {
        if (r.camera.playerY < source.originY-4) { enter(selected); return }
        // Do not create a mob over unloaded terrain: it would fall through the floor.
        val ready=pending.filter { e ->
            val radius=e.collisionRadius
            val minX=Math.floorDiv(floor(e.x-radius).toInt(),16)
            val maxX=Math.floorDiv(floor(e.x+radius).toInt(),16)
            val minZ=Math.floorDiv(floor(e.z-radius).toInt(),16)
            val maxZ=Math.floorDiv(floor(e.z+radius).toInt(),16)
            val bottom=Math.floorDiv(floor(e.y-1).toInt(),16)
            val top=Math.floorDiv(floor(e.y+9).toInt(),16)
            (minX..maxX).all { x -> (minZ..maxZ).all { z -> (bottom..top).all { y ->
                r.world.getChunk(x,y,z)?.generated == true
            } } }
        }
        r.enemyManager.enemies.addAll(ready); pending.removeAll(ready.toSet())
        r.enemyManager.update(dt)
        health.tick(dt)
        publishStatus()
    }

    private fun publishStatus() {
        if (health.hp!=lastHp) { lastHp=health.hp; r.playerHpCallback?.invoke(health.hp,health.maxHp) }
        val status=Triple(selected,health.lastDamage,health.defeats)
        if (status!=lastStatus) { lastStatus=status; onStatus?.invoke(status.first,status.second,status.third) }
    }

    companion object {
        val weapons: List<Short> = listOf(M.id(0,M.Form.SWORD),M.id(0,M.Form.HAMMER),M.id(0,M.Form.SPEAR),9907,9908)
    }
}
