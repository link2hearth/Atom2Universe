package com.Atom2Universe.app.games.toyboxracers.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.round

internal class ToyboxWorldEditorView(context: Context) : View(context) {
    interface Listener {
        fun onSelectionChanged(volume: ToyboxVolume?)
        fun onWorldChanged(world: ToyboxWorld)
    }

    var listener: Listener? = null
    var world = ToyboxWorld()
        private set
    var selectedId: Long? = world.volumes.firstOrNull()?.id
        private set
    var floorIndex = 0
        set(value) {
            field = value.coerceIn(0, 2)
            selectedId = null
            listener?.onSelectionChanged(null)
            invalidate()
        }
    var viewMode = ViewMode.ISOMETRIC
        private set
    private var zoom = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var scale = 1f
    private var dragOffsetX = 0f
    private var dragOffsetZ = 0f

    val selected: ToyboxVolume? get() = world.volumes.firstOrNull { it.id == selectedId }

    fun setWorld(value: ToyboxWorld) {
        world = value
        selectedId = value.volumes.firstOrNull()?.id
        listener?.onSelectionChanged(selected)
        invalidate()
    }

    fun addVolume(kind: ToyboxVolumeKind) {
        val y = floorIndex * FLOOR_HEIGHT + when (kind) {
            ToyboxVolumeKind.FLOOR -> -0.4f
            ToyboxVolumeKind.WALL, ToyboxVolumeKind.DOOR, ToyboxVolumeKind.WINDOW -> 5f
            ToyboxVolumeKind.RAIL -> 2.8f
            ToyboxVolumeKind.RAMP, ToyboxVolumeKind.STAIR -> 1.4f
            ToyboxVolumeKind.DUCT -> 4f
            ToyboxVolumeKind.FURNITURE, ToyboxVolumeKind.DECOR -> 3f
        }
        val volume = ToyboxVolume(
            id = System.nanoTime(),
            kind = kind,
            x = 0f,
            y = y,
            z = 0f,
            width = defaultWidth(kind),
            height = defaultHeight(kind),
            depth = defaultDepth(kind),
            solid = kind.solidByDefault
        )
        updateWorld(world.copy(volumes = world.volumes + volume), volume.id)
    }

    fun addCheckpoint() {
        val checkpoint = ToyboxCheckpoint(System.nanoTime(), 0f, floorIndex * FLOOR_HEIGHT, 0f)
        updateWorld(world.copy(checkpoints = world.checkpoints + checkpoint), selectedId)
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        updateWorld(world.copy(volumes = world.volumes.filterNot { it.id == id }), null)
    }

    fun updateSelected(transform: (ToyboxVolume) -> ToyboxVolume) {
        val id = selectedId ?: return
        updateWorld(world.copy(volumes = world.volumes.map { if (it.id == id) transform(it) else it }), id)
    }

    fun toggleViewMode() {
        viewMode = if (viewMode == ViewMode.ISOMETRIC) ViewMode.PLAN else ViewMode.ISOMETRIC
        invalidate()
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(0.55f, 2.2f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF263042.toInt())
        scale = minOf(width / WORLD_WIDTH, height / WORLD_DEPTH) * 0.90f * zoom
        drawGrid(canvas)
        drawCheckpoints(canvas)
        if (viewMode == ViewMode.ISOMETRIC) drawVolumesIsometric(canvas) else drawVolumesPlan(canvas)
        drawFrame(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wx = screenToWorldX(event.x, event.y)
        val wz = screenToWorldZ(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = visibleVolumes().asReversed().firstOrNull {
                    wx in it.left..it.right && wz in it.back..it.front
                }
                selectedId = hit?.id
                hit?.let {
                    dragOffsetX = it.x - wx
                    dragOffsetZ = it.z - wz
                }
                listener?.onSelectionChanged(hit)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val current = selected ?: return true
                val nx = snap(wx + dragOffsetX)
                val nz = snap(wz + dragOffsetZ)
                updateWorld(world.copy(volumes = world.volumes.map {
                    if (it.id == current.id) it.moveTo(nx, nz) else it
                }), current.id)
                return true
            }
        }
        return true
    }

    private fun updateWorld(next: ToyboxWorld, selection: Long?) {
        world = next
        selectedId = selection
        listener?.onWorldChanged(next)
        listener?.onSelectionChanged(selected)
        invalidate()
    }

    private fun drawGrid(canvas: Canvas) {
        val left = worldToScreenX(-WORLD_WIDTH * 0.5f, 0f, baseY())
        val right = worldToScreenX(WORLD_WIDTH * 0.5f, 0f, baseY())
        val top = worldToScreenZ(0f, -WORLD_DEPTH * 0.5f, baseY())
        val bottom = worldToScreenZ(0f, WORLD_DEPTH * 0.5f, baseY())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        for (x in -200..200 step 10) {
            paint.color = if (x == 0) 0x99FFFFFF.toInt() else 0x25FFFFFF
            canvas.drawLine(
                worldToScreenX(x.toFloat(), -WORLD_DEPTH * 0.5f, baseY()),
                worldToScreenZ(x.toFloat(), -WORLD_DEPTH * 0.5f, baseY()),
                worldToScreenX(x.toFloat(), WORLD_DEPTH * 0.5f, baseY()),
                worldToScreenZ(x.toFloat(), WORLD_DEPTH * 0.5f, baseY()),
                paint
            )
        }
        for (z in -120..120 step 10) {
            paint.color = if (z == 0) 0x99FFFFFF.toInt() else 0x25FFFFFF
            canvas.drawLine(
                worldToScreenX(-WORLD_WIDTH * 0.5f, z.toFloat(), baseY()),
                worldToScreenZ(-WORLD_WIDTH * 0.5f, z.toFloat(), baseY()),
                worldToScreenX(WORLD_WIDTH * 0.5f, z.toFloat(), baseY()),
                worldToScreenZ(WORLD_WIDTH * 0.5f, z.toFloat(), baseY()),
                paint
            )
        }
    }

    private fun drawVolumesPlan(canvas: Canvas) {
        for (volume in visibleVolumes()) {
            rect.set(
                worldToScreenX(volume.left, volume.back, volume.y),
                worldToScreenZ(volume.left, volume.back, volume.y),
                worldToScreenX(volume.right, volume.front, volume.y),
                worldToScreenZ(volume.right, volume.front, volume.y)
            )
            paint.style = Paint.Style.FILL
            paint.color = if (volume.solid) volume.kind.color else tintAlpha(volume.kind.color, 120)
            canvas.drawRoundRect(rect, 5f, 5f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (volume.id == selectedId) 4f else 1.5f
            paint.color = if (volume.id == selectedId) Color.WHITE else 0xAA000000.toInt()
            canvas.drawRoundRect(rect, 5f, 5f, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0xEEFFFFFF.toInt()
            paint.textSize = 18f * resources.displayMetrics.scaledDensity
            paint.typeface = PaintTypeface.BOLD
            canvas.drawText(volume.kind.label.uppercase(), rect.left + 8f, rect.top + 25f, paint)
        }
    }

    private fun drawVolumesIsometric(canvas: Canvas) {
        val volumes = visibleVolumes().sortedWith(compareBy<ToyboxVolume> { it.x + it.z }.thenBy { it.y })
        for (volume in volumes) {
            val left = volume.left
            val right = volume.right
            val back = volume.back
            val front = volume.front
            val bottom = volume.y - volume.height * 0.5f
            val top = volume.y + volume.height * 0.5f
            val a = iso(left, back, top)
            val b = iso(right, back, top)
            val c = iso(right, front, top)
            val d = iso(left, front, top)
            val e = iso(left, front, bottom)
            val f = iso(right, front, bottom)
            val g = iso(right, back, bottom)

            drawPoly(canvas, listOf(a, b, c, d), lighten(volume.kind.color, 1.08f), true)
            drawPoly(canvas, listOf(d, c, f, e), darken(volume.kind.color, 0.74f), true)
            drawPoly(canvas, listOf(b, g, f, c), darken(volume.kind.color, 0.62f), true)
            val selected = volume.id == selectedId
            drawPoly(canvas, listOf(a, b, c, d), if (selected) Color.WHITE else 0xAA000000.toInt(), false, if (selected) 4f else 1.5f)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 17f * resources.displayMetrics.scaledDensity
            paint.typeface = PaintTypeface.BOLD
            canvas.drawText(volume.kind.label.uppercase(), a.x + 8f, a.y + 24f, paint)
        }
    }

    private fun drawCheckpoints(canvas: Canvas) {
        val baseY = floorIndex * FLOOR_HEIGHT
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0xFFFFE7A8.toInt()
        for ((index, checkpoint) in world.checkpoints.withIndex()) {
            if (abs(checkpoint.y - baseY) > FLOOR_HEIGHT * 0.5f) continue
            canvas.drawCircle(worldToScreenX(checkpoint.x, checkpoint.z, checkpoint.y), worldToScreenZ(checkpoint.x, checkpoint.z, checkpoint.y), checkpoint.radius * scale, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 18f * resources.displayMetrics.scaledDensity
            paint.typeface = PaintTypeface.BOLD
            canvas.drawText("CP ${index + 1}", worldToScreenX(checkpoint.x, checkpoint.z, checkpoint.y) + 6f, worldToScreenZ(checkpoint.x, checkpoint.z, checkpoint.y) - 6f, paint)
            paint.style = Paint.Style.STROKE
        }
    }

    private fun drawFrame(canvas: Canvas) {
        if (viewMode == ViewMode.ISOMETRIC) {
            drawPoly(
                canvas,
                listOf(
                    iso(-WORLD_WIDTH * 0.5f, -WORLD_DEPTH * 0.5f, baseY()),
                    iso(WORLD_WIDTH * 0.5f, -WORLD_DEPTH * 0.5f, baseY()),
                    iso(WORLD_WIDTH * 0.5f, WORLD_DEPTH * 0.5f, baseY()),
                    iso(-WORLD_WIDTH * 0.5f, WORLD_DEPTH * 0.5f, baseY())
                ),
                0xCCFFFFFF.toInt(),
                fill = false,
                stroke = 3f
            )
            return
        }
        rect.set(
            worldToScreenX(-WORLD_WIDTH * 0.5f, 0f, 0f),
            worldToScreenZ(0f, -WORLD_DEPTH * 0.5f, 0f),
            worldToScreenX(WORLD_WIDTH * 0.5f, 0f, 0f),
            worldToScreenZ(0f, WORLD_DEPTH * 0.5f, 0f)
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawRect(rect, paint)
    }

    private fun visibleVolumes(): List<ToyboxVolume> {
        val baseY = floorIndex * FLOOR_HEIGHT
        return world.volumes.filter { abs(it.y - baseY) <= FLOOR_HEIGHT * 0.5f || it.kind == ToyboxVolumeKind.FLOOR }
    }

    private fun worldToScreenX(x: Float, z: Float, y: Float) =
        if (viewMode == ViewMode.ISOMETRIC) width * 0.5f + (x - z) * scale * ISO_X
        else width * 0.5f + x * scale

    private fun worldToScreenZ(x: Float, z: Float, y: Float) =
        if (viewMode == ViewMode.ISOMETRIC) height * 0.57f + (x + z) * scale * ISO_Z - y * scale * ISO_Y
        else height * 0.5f + z * scale

    private fun screenToWorldX(x: Float, y: Float): Float {
        if (viewMode == ViewMode.PLAN) return (x - width * 0.5f) / scale
        val sx = (x - width * 0.5f) / (scale * ISO_X)
        val sz = (y - height * 0.57f + floorIndex * FLOOR_HEIGHT * scale * ISO_Y) / (scale * ISO_Z)
        return (sx + sz) * 0.5f
    }

    private fun screenToWorldZ(x: Float, y: Float): Float {
        if (viewMode == ViewMode.PLAN) return (y - height * 0.5f) / scale
        val sx = (x - width * 0.5f) / (scale * ISO_X)
        val sz = (y - height * 0.57f + floorIndex * FLOOR_HEIGHT * scale * ISO_Y) / (scale * ISO_Z)
        return (sz - sx) * 0.5f
    }
    private fun snap(value: Float) = round(value / GRID) * GRID

    private fun iso(x: Float, z: Float, y: Float): ScreenPoint {
        val sx = width * 0.5f + (x - z) * scale * ISO_X
        val sy = height * 0.57f + (x + z) * scale * ISO_Z - y * scale * ISO_Y
        return ScreenPoint(sx, sy)
    }

    private fun drawPoly(canvas: Canvas, points: List<ScreenPoint>, color: Int, fill: Boolean, stroke: Float = 1f) {
        path.reset()
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        path.close()
        paint.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun lighten(color: Int, amount: Float): Int {
        val r = (((color shr 16) and 255) * amount).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 255) * amount).toInt().coerceIn(0, 255)
        val b = ((color and 255) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun darken(color: Int, amount: Float): Int {
        val r = (((color shr 16) and 255) * amount).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 255) * amount).toInt().coerceIn(0, 255)
        val b = ((color and 255) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun tintAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun defaultWidth(kind: ToyboxVolumeKind) = when (kind) {
        ToyboxVolumeKind.FLOOR -> 80f
        ToyboxVolumeKind.WALL -> 48f
        ToyboxVolumeKind.DOOR, ToyboxVolumeKind.WINDOW -> 16f
        ToyboxVolumeKind.RAIL -> 34f
        ToyboxVolumeKind.RAMP, ToyboxVolumeKind.DUCT -> 40f
        ToyboxVolumeKind.STAIR -> 22f
        ToyboxVolumeKind.FURNITURE -> 20f
        ToyboxVolumeKind.DECOR -> 10f
    }

    private fun defaultDepth(kind: ToyboxVolumeKind) = when (kind) {
        ToyboxVolumeKind.FLOOR -> 60f
        ToyboxVolumeKind.WALL -> 2f
        ToyboxVolumeKind.DOOR, ToyboxVolumeKind.WINDOW -> 1f
        ToyboxVolumeKind.RAIL -> 1.2f
        ToyboxVolumeKind.RAMP, ToyboxVolumeKind.DUCT -> 14f
        ToyboxVolumeKind.STAIR -> 8f
        ToyboxVolumeKind.FURNITURE -> 14f
        ToyboxVolumeKind.DECOR -> 10f
    }

    private fun defaultHeight(kind: ToyboxVolumeKind) = when (kind) {
        ToyboxVolumeKind.FLOOR -> 0.8f
        ToyboxVolumeKind.WALL -> 10f
        ToyboxVolumeKind.DOOR, ToyboxVolumeKind.WINDOW -> 9f
        ToyboxVolumeKind.RAIL -> 5f
        ToyboxVolumeKind.RAMP, ToyboxVolumeKind.STAIR -> 1f
        ToyboxVolumeKind.DUCT -> 8f
        ToyboxVolumeKind.FURNITURE -> 8f
        ToyboxVolumeKind.DECOR -> 4f
    }

    companion object {
        private const val FLOOR_HEIGHT = 26f
        private const val GRID = 2f
        private const val WORLD_WIDTH = 420f
        private const val WORLD_DEPTH = 240f
        private const val ISO_X = 0.72f
        private const val ISO_Z = 0.36f
        private const val ISO_Y = 0.88f
    }

    enum class ViewMode { ISOMETRIC, PLAN }

    private data class ScreenPoint(val x: Float, val y: Float)

    private fun baseY() = floorIndex * FLOOR_HEIGHT

    private object PaintTypeface {
        val BOLD = android.graphics.Typeface.DEFAULT_BOLD
    }
}
