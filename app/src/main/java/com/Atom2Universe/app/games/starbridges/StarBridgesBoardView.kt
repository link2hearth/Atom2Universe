package com.Atom2Universe.app.games.starbridges

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class StarBridgesBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onBoardChanged()
        fun onSolved()
    }

    var game: StarBridgesGame? = null
    var listener: Listener? = null

    // ── Paints ────────────────────────────────────────────────────────────────────
    private val paintBg = Paint().apply { color = Color.parseColor("#0A0F1E") }
    private val paintBridge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#818CF8")
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val paintNodeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintNodeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val COLOR_NODE_NORMAL   = Color.parseColor("#1E1B4B")
    private val COLOR_NODE_SELECTED = Color.parseColor("#F59E0B")
    private val COLOR_NODE_DONE     = Color.parseColor("#065F46")
    private val COLOR_NODE_OVER     = Color.parseColor("#7F1D1D")
    private val COLOR_STROKE_NORMAL = Color.parseColor("#4F46E5")
    private val COLOR_STROKE_SEL    = Color.parseColor("#FCD34D")
    private val COLOR_STROKE_DONE   = Color.parseColor("#10B981")
    private val COLOR_STROKE_OVER   = Color.parseColor("#EF4444")

    // ── Layout cache ──────────────────────────────────────────────────────────────
    private var boardPx = 0f
    private var cellPx = 0f
    private var startX = 0f
    private var startY = 0f
    private var nodeRadius = 0f
    private val nodeRect = RectF()

    private fun recompute() {
        val g = game ?: return
        val s = g.size.toFloat()
        val pad = 8f * resources.displayMetrics.density
        boardPx = min(width.toFloat() - pad * 2, height.toFloat() - pad * 2)
        cellPx = boardPx / s
        startX = (width - boardPx) / 2f
        startY = (height - boardPx) / 2f
        nodeRadius = cellPx * 0.28f
        paintText.textSize = cellPx * 0.34f
        paintBridge.strokeWidth = cellPx * 0.09f
        paintNodeStroke.strokeWidth = cellPx * 0.06f
    }

    private fun nodeCx(n: SbNode) = startX + (n.x + 0.5f) * cellPx
    private fun nodeCy(n: SbNode) = startY + (n.y + 0.5f) * cellPx

    // ── Draw ──────────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        val g = game ?: return
        recompute()

        // Bridges
        for ((key, count) in g.bridges) {
            if (count <= 0) continue
            val edge = g.edgesByKey[key] ?: continue
            val x1 = startX + edge.x1 * cellPx
            val y1 = startY + edge.y1 * cellPx
            val x2 = startX + edge.x2 * cellPx
            val y2 = startY + edge.y2 * cellPx
            // Trim line ends to stop at node border
            val dx = x2 - x1; val dy = y2 - y1
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1f) continue
            val trim = nodeRadius + cellPx * 0.04f
            val ux = dx / len * trim; val uy = dy / len * trim
            canvas.drawLine(x1 + ux, y1 + uy, x2 - ux, y2 - uy, paintBridge)
        }

        // Nodes
        for (node in g.nodes) {
            val cx = nodeCx(node)
            val cy = nodeCy(node)
            val bridgeCount = g.bridgeCountFor(node.id)
            val isSelected = g.selectedNodeId == node.id

            val (fillColor, strokeColor) = when {
                isSelected -> COLOR_NODE_SELECTED to COLOR_STROKE_SEL
                bridgeCount > node.required -> COLOR_NODE_OVER to COLOR_STROKE_OVER
                bridgeCount == node.required -> COLOR_NODE_DONE to COLOR_STROKE_DONE
                else -> COLOR_NODE_NORMAL to COLOR_STROKE_NORMAL
            }

            nodeRect.set(cx - nodeRadius, cy - nodeRadius, cx + nodeRadius, cy + nodeRadius)
            paintNodeFill.color = fillColor
            canvas.drawOval(nodeRect, paintNodeFill)
            paintNodeStroke.color = strokeColor
            canvas.drawOval(nodeRect, paintNodeStroke)

            // Number
            val textY = cy - (paintText.descent() + paintText.ascent()) / 2f
            canvas.drawText(node.required.toString(), cx, textY, paintText)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────────
    private fun nodeAtTouch(tx: Float, ty: Float): SbNode? {
        val g = game ?: return null
        val hitR = nodeRadius * 1.5f
        return g.nodes.firstOrNull { n ->
            val dx = tx - nodeCx(n); val dy = ty - nodeCy(n)
            dx * dx + dy * dy <= hitR * hitR
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val g = game ?: return true
        if (g.solved) return true

        recompute()
        val tapped = nodeAtTouch(event.x, event.y) ?: run {
            // Tap on empty space deselects
            if (g.selectedNodeId != null) { g.selectedNodeId = null; invalidate(); listener?.onBoardChanged() }
            return true
        }

        val prev = g.selectedNodeId
        when {
            prev == null -> {
                g.selectedNodeId = tapped.id
                invalidate()
                listener?.onBoardChanged()
            }
            prev == tapped.id -> {
                g.selectedNodeId = null
                invalidate()
                listener?.onBoardChanged()
            }
            else -> {
                val key = if (prev < tapped.id) "$prev-${tapped.id}" else "${tapped.id}-$prev"
                if (g.edgesByKey.containsKey(key)) {
                    g.selectedNodeId = null
                    g.toggleBridge(prev, tapped.id)
                    invalidate()
                    if (g.solved) listener?.onSolved() else listener?.onBoardChanged()
                } else {
                    // No direct edge — move selection to the tapped node
                    g.selectedNodeId = tapped.id
                    invalidate()
                    listener?.onBoardChanged()
                }
            }
        }
        return true
    }

    fun refresh() { recompute(); invalidate() }
}
