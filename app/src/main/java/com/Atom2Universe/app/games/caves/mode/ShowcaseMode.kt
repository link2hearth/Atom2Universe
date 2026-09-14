package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.ShowcaseMap
import kotlin.math.floor

/** Peaceful inspection of the finite map, entered through the Assault map picker. */
internal class ShowcaseMode(private val r: CaveRenderer, private val source: MapSource) : GameMode {
    override val allowsWorldEdits = false
    override val fixedTimeOfDayMs = 600_000L
    @Volatile var onCaption: ((Int, Short?) -> Unit)? = null
    private val exhibits = ShowcaseMap.galleryBlocks()
    private var captionTimer = 0f

    override fun spawnPoint() = source.spawnPoint()
    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        r.playerNode.setMaxHp(100, 100)
    }
    override fun onPlayerPlaced(x: Double, y: Double, z: Double) {
        r.camera.yaw = 0f
        r.camera.pitch = 0f
    }
    override fun update(dt: Float) {
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
        val zone = ShowcaseMap.zoneAt(x, z)
        val col = Math.floorDiv(x - 2, 4)
        val row = Math.floorDiv(z - ShowcaseMap.GALLERY_Z + 2, 4)
        val block = if (zone == 6 && col in 0 until ShowcaseMap.COLUMNS && row >= 0)
            exhibits.getOrNull(row * ShowcaseMap.COLUMNS + col) else null
        onCaption?.invoke(zone, block)
    }
}
