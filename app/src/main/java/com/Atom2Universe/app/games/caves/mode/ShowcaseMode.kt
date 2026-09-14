package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.ShowcaseMap
import kotlin.math.floor
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyState
import com.Atom2Universe.app.games.caves.entity.ExhibitPose
import com.Atom2Universe.app.games.caves.entity.PassiveAnimals
import com.Atom2Universe.app.games.caves.node.MobRegistry

/** Peaceful inspection of the finite map, entered through the Assault map picker. */
internal class ShowcaseMode(private val r: CaveRenderer, private val source: MapSource) : GameMode {
    override val allowsWorldEdits = false
    override val fixedTimeOfDayMs = 600_000L
    @Volatile var onCaption: ((Int, Short?) -> Unit)? = null
    @Volatile var onMobCaption: ((Enemy, ExhibitPose) -> Unit)? = null
    @Volatile var onTreeCaption: ((Int) -> Unit)? = null
    @Volatile var onColdCaption: ((Int) -> Unit)? = null
    @Volatile var onVillageCaption: ((Int) -> Unit)? = null
    @Volatile var onGardenCaption: ((Int, Int) -> Unit)? = null
    val mannequins = mutableListOf<Enemy>()
    private val exhibits = ShowcaseMap.galleryBlocks()
    private var captionTimer = 0f

    override fun spawnPoint() = source.spawnPoint()
    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        r.playerNode.setMaxHp(100, 100)
        mannequins.clear()
        val definitions = (MobRegistry.all().sortedBy { it.id } + AssaultMode.SOLDIER)
            .map { PassiveAnimals.Display(it,false,0) } + PassiveAnimals.displays
        definitions.forEachIndexed { index, display ->
            val def=display.def
            ExhibitPose.entries.forEachIndexed { column, pose ->
                mannequins += Enemy(index * 3 + column, def,
                    source.originX + 12.0 + index % 3 * 24 + (1 - column) * 5,
                    source.originY + ShowcaseMap.FLOOR + 1.0,
                    source.originZ + ShowcaseMap.mobGalleryZ() + 5.0 + index / 3 * 10).apply {
                    exhibitPose = pose
                    young = display.young
                    coat = display.coat
                    resting = pose != ExhibitPose.ACTION
                    state = if (def.model == "spider" || def.behavior == "passive") EnemyState.CHASE else EnemyState.ATTACK
                    yaw = 180f
                    level = 2
                    hp = maxHp
                }
            }
        }
    }
    override fun onPlayerPlaced(x: Double, y: Double, z: Double) {
        r.camera.yaw = 0f
        r.camera.pitch = 0f
    }
    override fun update(dt: Float) {
        for (mob in mannequins) {
            if (mob.exhibitPose != ExhibitPose.REFERENCE) mob.animTime += dt
            if (mob.def.behavior != "passive" && mob.def.model != "soldier") {
                // Présentoir action : deux secondes de marche, puis une frappe et sa récupération.
                val phase=mob.animTime%4f
                val action=mob.exhibitPose == ExhibitPose.ACTION
                mob.resting=!action
                mob.state=if(action && phase<2f) EnemyState.CHASE else EnemyState.ATTACK
                mob.motionBlend=if(action && phase<2f) 1f else 0f
                if(mob.motionBlend>0f) mob.walkPhase += dt*com.Atom2Universe.app.games.caves.render.MobModels.get(mob.def.model).gait
                mob.strikeTime=if(action && phase>=2.2f) (.55f-(phase-2.2f)).coerceAtLeast(0f) else 0f
            }
            if (mob.def.model == "soldier" && mob.exhibitPose == ExhibitPose.ACTION) {
                // Three shots then a pause; visual only, never produces damaging projectiles.
                val phase = mob.animTime % 2.4f
                mob.shotRecoil = if (phase < .6f) (.16f - phase % .2f).coerceAtLeast(0f) else 0f
            }
        }
        if (r.camera.playerY < source.originY - 8 || r.playerNode.hp <= 0) {
            val spawn = source.spawnPoint()
            r.camera.playerX = spawn[0].toDouble()
            r.camera.playerY = spawn[1].toDouble()
            r.camera.playerZ = spawn[2].toDouble()
            r.physics.reset()
            r.playerNode.setMaxHp(100, 100)
        }
        captionTimer += dt
        if (captionTimer < .25f) return
        captionTimer = 0f
        val x = floor(r.camera.playerX - source.originX).toInt()
        val z = floor(r.camera.playerZ - source.originZ).toInt()
        ShowcaseMap.gardenSample(x, z)?.let { (crop, stage) ->
            onGardenCaption?.invoke(crop, stage)
            return
        }
        ShowcaseMap.villageAt(x, z)?.let {
            onVillageCaption?.invoke(it)
            return
        }
        if (z >= ShowcaseMap.mobGalleryZ() - 2) {
            val nearest = mannequins.minByOrNull {
                val dx = it.x - r.camera.playerX; val dz = it.z - r.camera.playerZ
                dx * dx + dz * dz
            }
            if (nearest != null) onMobCaption?.invoke(nearest, nearest.exhibitPose!!)
            return
        }
        ShowcaseMap.treeAt(x, z)?.let {
            onTreeCaption?.invoke(it)
            return
        }
        ShowcaseMap.coldAt(x, z)?.let {
            onColdCaption?.invoke(it)
            return
        }
        val zone = ShowcaseMap.zoneAt(x, z)
        val col = Math.floorDiv(x - 2, 4)
        val row = Math.floorDiv(z - ShowcaseMap.GALLERY_Z + 2, 4)
        val block = if (zone == 6 && col in 0 until ShowcaseMap.COLUMNS && row >= 0)
            exhibits.getOrNull(row * ShowcaseMap.COLUMNS + col) else null
        onCaption?.invoke(zone, block)
    }
}
