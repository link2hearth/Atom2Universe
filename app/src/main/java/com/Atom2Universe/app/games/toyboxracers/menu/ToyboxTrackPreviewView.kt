package com.Atom2Universe.app.games.toyboxracers.menu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolumeKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Tracé d'un circuit vu de dessus, en coordonnées du monde : un contour par
 * morceau de ruban, de la largeur réelle de la route, et les sols posés autour.
 * Calculé hors du fil d'affichage, puis dessiné à n'importe quelle taille.
 */
internal class TrackOutline(
    /** Chaque ruban : x0, z0, x1, z1, … */
    val ribbons: List<FloatArray>,
    val widths: FloatArray,
    /** Chaque sol : gauche, arrière, droite, avant. */
    val floors: List<FloatArray>,
    val closed: Boolean,
    val startPoint: Pair<Float, Float>? = null
) {
    val minX: Float
    val maxX: Float
    val minZ: Float
    val maxZ: Float

    init {
        var loX = Float.MAX_VALUE; var hiX = -Float.MAX_VALUE
        var loZ = Float.MAX_VALUE; var hiZ = -Float.MAX_VALUE
        for (ribbon in ribbons) {
            for (i in ribbon.indices step 2) {
                loX = min(loX, ribbon[i]); hiX = max(hiX, ribbon[i])
                loZ = min(loZ, ribbon[i + 1]); hiZ = max(hiZ, ribbon[i + 1])
            }
        }
        if (loX > hiX) { loX = -1f; hiX = 1f; loZ = -1f; hiZ = 1f }
        minX = loX; maxX = hiX; minZ = loZ; maxZ = hiZ
    }

    companion object {
        private val classicCache = ConcurrentHashMap<SceneChoice, TrackOutline>()

        /** Les circuits classiques ne changent jamais : un seul calcul par scène. */
        fun classic(scene: SceneChoice): TrackOutline = classicCache.getOrPut(scene) {
            val track = PrototypeTrack(sampleCount = 160, scene = scene)
            val samples = track.allSamples()
            val ribbons = ArrayList<FloatArray>()
            var line = ArrayList<Float>()
            var hasGaps = false
            repeat(321) { index ->
                val distance = track.length * index / 320f
                if (track.isJumpGap(distance)) {
                    hasGaps = true
                    if (line.size >= 4) ribbons += line.toFloatArray()
                    line = ArrayList()
                } else {
                    val position = track.sampleAt(distance).position
                    line.add(position.x)
                    line.add(position.z)
                }
            }
            if (line.size >= 4) ribbons += line.toFloatArray()
            val width = if (samples.isEmpty()) 8f else samples.map { it.roadWidth }.average().toFloat()
            val start = track.sampleAt(track.length * RaceSession.START_FRACTION).position
            TrackOutline(ribbons, FloatArray(ribbons.size) { width }, emptyList(), closed = !hasGaps,
                startPoint = start.x to start.z)
        }

        fun of(world: ToyboxWorld): TrackOutline {
            val ribbons = ArrayList<FloatArray>()
            val widths = ArrayList<Float>()
            for (section in world.trackSections) {
                val steps = if (section.bends.isEmpty() && section.startTangentDegrees == null &&
                    section.endTangentDegrees == null) 1 else 14
                val line = FloatArray((steps + 1) * 2)
                for (i in 0..steps) {
                    val p = section.centerAt(i / steps.toFloat())
                    line[i * 2] = p.x
                    line[i * 2 + 1] = p.z
                }
                ribbons += line
                widths += (section.width + section.endWidth) * 0.5f
            }
            val floors = world.volumes
                .filter { it.kind == ToyboxVolumeKind.FLOOR }
                .map { floatArrayOf(it.left, it.back, it.right, it.front) }
            return TrackOutline(ribbons, widths.toFloatArray(), floors, closed = false)
        }
    }
}

/** Vignette d'un circuit : fond de couleur, sols pâles, ruban gris et départ. */
internal class ToyboxTrackPreviewView(context: Context) : View(context) {
    private val road = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val floorRect = RectF()

    var background = 0xFF4B617A.toInt()
        set(value) { field = value; invalidate() }
    var roadColor = 0xFFF2F4F8.toInt()
        set(value) { field = value; invalidate() }
    var outline: TrackOutline? = null
        set(value) { field = value; invalidate() }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = dp(14f)
        rect.set(0f, 0f, w, h)
        fill.color = background
        canvas.drawRoundRect(rect, radius, radius, fill)

        val data = outline ?: return
        val pad = dp(14f)
        val spanX = max(data.maxX - data.minX, 1f)
        val spanZ = max(data.maxZ - data.minZ, 1f)
        val scale = min((w - pad * 2) / spanX, (h - pad * 2) / spanZ)
        val offsetX = (w - spanX * scale) * 0.5f - data.minX * scale
        val offsetY = (h - spanZ * scale) * 0.5f - data.minZ * scale

        val save = canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        // Un sol plus grand que le tracé déborde de la vignette : il est rogné par le clip.
        fill.color = 0x66FFFFFF
        for (floor in data.floors) {
            floorRect.set(
                floor[0] * scale + offsetX, floor[1] * scale + offsetY,
                floor[2] * scale + offsetX, floor[3] * scale + offsetY
            )
            canvas.drawRect(floorRect, fill)
        }
        canvas.restoreToCount(save)

        data.ribbons.forEachIndexed { index, line ->
            if (line.size < 4) return@forEachIndexed
            path.rewind()
            path.moveTo(line[0] * scale + offsetX, line[1] * scale + offsetY)
            for (i in 2 until line.size step 2) path.lineTo(line[i] * scale + offsetX, line[i + 1] * scale + offsetY)
            if (data.closed) path.close()
            val strokeWidth = max(dp(3.2f), data.widths[index] * scale)
            road.strokeWidth = strokeWidth + dp(2.4f)
            road.color = 0x66000000
            canvas.drawPath(path, road)
            road.strokeWidth = strokeWidth
            road.color = roadColor
            canvas.drawPath(path, road)
        }

        val first = data.ribbons.firstOrNull()
        if (first != null && first.size >= 2) {
            start.color = 0xFFE26F82.toInt()
            val startX = data.startPoint?.first ?: first[0]
            val startZ = data.startPoint?.second ?: first[1]
            canvas.drawCircle(startX * scale + offsetX, startZ * scale + offsetY, dp(4.5f), start)
        }
    }
}
