package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.ShowcaseMap
import kotlin.math.floor
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyState
import com.Atom2Universe.app.games.caves.entity.ExhibitPose
import com.Atom2Universe.app.games.caves.node.MobRegistry

/** Peaceful inspection of the finite map, entered through the Assault map picker. */
internal class ShowcaseMode(private val r: CaveRenderer, private val source: MapSource) : GameMode {
    override val allowsWorldEdits = false
    override val fixedTimeOfDayMs = 600_000L
    @Volatile var onCaption: ((Int, Short?) -> Unit)? = null
    @Volatile var onMobCaption: ((String, ExhibitPose) -> Unit)? = null
    val mannequins = mutableListOf<Enemy>()
    private val exhibits = ShowcaseMap.galleryBlocks()
    private var captionTimer = 0f

    override fun spawnPoint() = source.spawnPoint()
    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        r.playerNode.setMaxHp(100, 100)
        mannequins.clear()
        val definitions = MobRegistry.all().sortedBy { it.id } + AssaultMode.SOLDIER
        definitions.forEachIndexed { index, def ->
            ExhibitPose.entries.forEachIndexed { column, pose ->
                mannequins += Enemy(index * 3 + column, def,
                    source.originX + 12.0 + index % 3 * 24 + (1 - column) * 5,
                    source.originY + ShowcaseMap.FLOOR + 1.0,
                    source.originZ + ShowcaseMap.mobGalleryZ() + 5.0 + index / 3 * 10).apply {
                    exhibitPose = pose
                    resting = pose != ExhibitPose.ACTION
                    state = if (def.model == "spider") EnemyState.CHASE else EnemyState.ATTACK
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
        if (z >= ShowcaseMap.mobGalleryZ() - 2) {
            val nearest = mannequins.minByOrNull {
                val dx = it.x - r.camera.playerX; val dz = it.z - r.camera.playerZ
                dx * dx + dz * dz
            }
            if (nearest != null) onMobCaption?.invoke(nearest.def.model, nearest.exhibitPose!!)
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
