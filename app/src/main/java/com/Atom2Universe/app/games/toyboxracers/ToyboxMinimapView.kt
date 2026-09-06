package com.Atom2Universe.app.games.toyboxracers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.track.HouseGeometry
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.sin

/** Carte fixe : le triangle blanc montre le joueur et son cap, les points les IA. */
internal class ToyboxMinimapView(context: Context) : View(context) {
    private var track = PrototypeTrack()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val route = Path()
    private val levelRoutes = Array(3) { Path() }
    private val arrow = Path()
    private val density = resources.displayMetrics.density
    private var scale = 1f
    private var state: ToyboxRacersRenderer.HudState? = null
    private val colors = intArrayOf(0xFF6BD9B3.toInt(), 0xFFAD94EB.toInt(),
        0xFF85C7FA.toInt(), 0xFFFFC75C.toInt(), 0xFFED6BA3.toInt())

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
    }

    fun update(state: ToyboxRacersRenderer.HudState) {
        if (state.scene != track.scene) {
            track = PrototypeTrack(scene = state.scene)
            rebuildRoute(width, height)
        }
        this.state = state
        invalidate()
    }

    private fun mapX(x: Float) = width * 0.5f + x * scale
    private fun mapZ(z: Float) = height * 0.5f + z * scale

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildRoute(w, h)
    }

    private fun rebuildRoute(w: Int, h: Int) {
        // La pièce entière reste visible, y compris lorsque le joueur explore.
        // En mode Maison, la carte couvre les quatre pièces et le couloir.
        scale = if (track.scene.circuit.usesHouseLayout) {
            minOf(
                (w - 16f * density).coerceAtLeast(1f) / (2f * HouseGeometry.HALF_WIDTH),
                (h - 16f * density).coerceAtLeast(1f) / (2f * HouseGeometry.HALF_DEPTH)
            )
        } else {
            minOf(
                (w - 16f * density).coerceAtLeast(1f) / (2f * PrototypeTrack.ROOM_HALF_WIDTH),
                (h - 16f * density).coerceAtLeast(1f) / (2f * PrototypeTrack.ROOM_HALF_DEPTH)
            )
        }
        route.reset()
        levelRoutes.forEach { it.reset() }
        var connected = false
        var previousLevel = -1
        repeat(481) { index ->
            val distance = track.length * index / 480f
            val sample = track.sampleAt(distance)
            if (track.scene.circuit.usesHouseLayout) {
                val level = ((sample.position.y + HouseGeometry.LEVEL_HEIGHT * .5f) /
                    HouseGeometry.LEVEL_HEIGHT).toInt().coerceIn(0, 2)
                if (level != previousLevel) {
                    levelRoutes[level].moveTo(mapX(sample.position.x), mapZ(sample.position.z))
                } else {
                    levelRoutes[level].lineTo(mapX(sample.position.x), mapZ(sample.position.z))
                }
                previousLevel = level
            }
            if (track.isJumpGap(distance)) {
                connected = false
            } else {
                if (connected) route.lineTo(mapX(sample.position.x), mapZ(sample.position.z))
                else route.moveTo(mapX(sample.position.x), mapZ(sample.position.z))
                connected = true
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        paint.color = 0xB83B4055.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 14f * density, 14f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * density
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = 0xBBD7DCF0.toInt()
        canvas.drawPath(route, paint)
        if (track.scene.circuit.usesHouseLayout) {
            val level = (((state?.playerY ?: 0f) + HouseGeometry.LEVEL_HEIGHT * .5f) /
                HouseGeometry.LEVEL_HEIGHT).toInt().coerceIn(0, 2)
            paint.color = 0xFFFFE7A8.toInt()
            paint.strokeWidth = 3.5f * density
            canvas.drawPath(levelRoutes[level], paint)
        }
        paint.style = Paint.Style.FILL
        val start = track.sampleAt(track.length * RaceSession.START_FRACTION).position
        paint.color = 0xFFFFE7A8.toInt()
        val r = 2.5f * density
        canvas.drawRect(mapX(start.x) - r, mapZ(start.z) - r, mapX(start.x) + r, mapZ(start.z) + r, paint)
        val snapshot = state ?: return
        snapshot.rivalPositions.forEachIndexed { index, position ->
            paint.color = colors[index % colors.size]
            if (track.scene.circuit.usesHouseLayout && abs(position.y - snapshot.playerY) > 13f)
                paint.alpha = 65
            canvas.drawCircle(mapX(position.x), mapZ(position.z), 2.8f * density, paint)
        }
        val x = mapX(snapshot.playerX)
        val z = mapZ(snapshot.playerZ)
        val forwardX = sin(snapshot.playerYaw)
        val forwardZ = cos(snapshot.playerYaw)
        val size = 5.5f * density
        arrow.rewind()
        arrow.moveTo(x + forwardX * size, z + forwardZ * size)
        arrow.lineTo(x - forwardX * size * 0.6f + forwardZ * size * 0.65f,
            z - forwardZ * size * 0.6f - forwardX * size * 0.65f)
        arrow.lineTo(x - forwardX * size * 0.6f - forwardZ * size * 0.65f,
            z - forwardZ * size * 0.6f + forwardX * size * 0.65f)
        arrow.close()
        paint.color = 0xFF30354A.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        canvas.drawPath(arrow, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        canvas.drawPath(arrow, paint)
    }
}
