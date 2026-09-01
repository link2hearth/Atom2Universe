package com.Atom2Universe.app.games.trebuchet.gears

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Éditeur flottant d'une roue sélectionnée.
 *
 * La denture est la dimension maîtresse : le module reste constant, donc changer le
 * nombre de dents recalcule automatiquement rayons, masse, inertie et engrènements.
 * La couche et la matière vivent au même endroit afin que toute l'identité de la pièce
 * soit modifiable sans retourner dans le menu Pièces.
 */
class GearEditorBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gearView: GearMachineView? = null
    var onEdited: (() -> Unit)? = null

    private enum class Row { TEETH, LAYER, MATERIAL }
    private enum class Hit { NONE, HEADER, MINUS, PLUS, VALUE, COUPLE, DUPLICATE, DELETE }

    private val dp = resources.displayMetrics.density
    private val panel = RectF()
    private val path = Path()
    private val pPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 16, 25, 50) }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 209, 102)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val pHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 143, 166, 200) }
    private val pCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(65, 143, 166, 200) }
    private val pCellOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 209, 102) }
    private val pDanger = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(65, 230, 112, 82) }
    private val pAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 209, 102) }
    private val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        textAlign = Paint.Align.CENTER
        textSize = 12f * dp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(143, 166, 200)
        textSize = 12f * dp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        textAlign = Paint.Align.CENTER
        textSize = 20f * dp
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val pValueDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 255, 209, 102)
        textAlign = Paint.Align.CENTER
        textSize = 13f * dp
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val pButton = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 216, 224)
        textAlign = Paint.Align.CENTER
        textSize = 13f * dp
        typeface = Typeface.DEFAULT_BOLD
    }

    private companion object {
        const val WIDTH_DP = 306f
        const val HEADER_DP = 36f
        const val ROW_DP = 54f
        const val ACTION_DP = 52f
        const val PAD_DP = 10f
        const val LABEL_DP = 70f
        const val STEP_DP = 22f
        const val TAP_SLOP_DP = 8f
        const val REPEAT_DELAY_MS = 420L
        const val REPEAT_EVERY_MS = 90L
    }

    private var hit = Hit.NONE
    private var row = Row.TEETH
    private var direction = 0
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var travel = 0f
    private var wheelOffset = 0f
    private val restX = FloatArray(2) { Float.NaN }
    private val restY = FloatArray(2) { Float.NaN }
    private val slot: Int
        get() = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 1 else 0

    private val repeater = object : Runnable {
        override fun run() {
            if (hit != Hit.MINUS && hit != Hit.PLUS) return
            change(direction)
            postDelayed(this, REPEAT_EVERY_MS)
        }
    }

    /** Appelé avec chaque rafraîchissement de l'activité : la sélection pilote la bulle. */
    fun showForSelection() {
        val show = gearView?.selectedWheel() != null
        visibility = if (show) VISIBLE else GONE
        if (!show) release()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            (WIDTH_DP * dp).roundToInt(),
            ((HEADER_DP + ROW_DP * Row.entries.size + ACTION_DP + PAD_DP) * dp).roundToInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        val wheel = gearView?.selectedWheel() ?: return
        panel.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(panel, 12f * dp, 12f * dp, pPanel)
        canvas.drawRoundRect(panel, 12f * dp, 12f * dp, pEdge)
        drawHeader(canvas, wheel)
        drawRow(canvas, Row.TEETH, context.getString(R.string.trebuchet_gear_edit_teeth), wheel.teeth.toString())
        drawRow(canvas, Row.LAYER, context.getString(R.string.trebuchet_gear_edit_layer), signed(wheel.layer))
        drawRow(canvas, Row.MATERIAL, context.getString(R.string.trebuchet_gear_edit_material), materialName(wheel.material))
        drawActions(canvas)
    }

    private fun drawHeader(canvas: Canvas, wheel: GearWheelConfig) {
        val h = HEADER_DP * dp
        canvas.save()
        path.reset()
        path.addRoundRect(panel, 12f * dp, 12f * dp, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawRect(0f, 0f, width.toFloat(), h, pHeader)
        canvas.restore()
        val diameterCm = (wheel.outerRadius * 200f).roundToInt()
        val circumferenceCm = (2f * PI.toFloat() * wheel.outerRadius * 100f).roundToInt()
        canvas.drawText(
            context.getString(R.string.trebuchet_gear_size_header, diameterCm, circumferenceCm),
            width / 2f, h / 2f - (pTitle.ascent() + pTitle.descent()) / 2f, pTitle
        )
        // Deux prises discrètes disent que le bandeau entier se déplace.
        canvas.drawCircle(12f * dp, h / 2f, 2f * dp, pAccent)
        canvas.drawCircle(width - 12f * dp, h / 2f, 2f * dp, pAccent)
    }

    private fun drawRow(canvas: Canvas, target: Row, label: String, value: String) {
        val top = rowTop(target)
        val mid = top + ROW_DP * dp / 2f
        canvas.drawText(label, PAD_DP * dp, mid - (pLabel.ascent() + pLabel.descent()) / 2f, pLabel)
        drawStepButton(canvas, minusLeft(), top, false, hit == Hit.MINUS && row == target)
        drawStepButton(canvas, plusLeft(), top, true, hit == Hit.PLUS && row == target)

        val left = valueLeft()
        val right = plusLeft() - 4f * dp
        panel.set(left, top + 5f * dp, right, top + (ROW_DP - 5f) * dp)
        canvas.drawRoundRect(panel, 6f * dp, 6f * dp, if (hit == Hit.VALUE && row == target) pCellOn else pCell)
        val centerX = (left + right) / 2f
        val offset = if (hit == Hit.VALUE && row == target) wheelOffset else 0f
        canvas.save()
        canvas.clipRect(panel)
        canvas.drawText(value, centerX, mid - (pValue.ascent() + pValue.descent()) / 2f + offset, pValue)
        if (target != Row.MATERIAL) {
            canvas.drawText(neighborValue(target, -1), centerX, mid - 26f * dp + offset, pValueDim)
            canvas.drawText(neighborValue(target, +1), centerX, mid + 31f * dp + offset, pValueDim)
        }
        canvas.restore()
    }

    private fun drawStepButton(canvas: Canvas, left: Float, top: Float, plus: Boolean, pressed: Boolean) {
        val size = 44f * dp
        panel.set(left, top + 5f * dp, left + size, top + (ROW_DP - 5f) * dp)
        canvas.drawRoundRect(panel, 6f * dp, 6f * dp, if (pressed) pCellOn else pCell)
        val cx = left + size / 2f
        val cy = top + ROW_DP * dp / 2f
        canvas.drawRect(cx - 7f * dp, cy - 1.4f * dp, cx + 7f * dp, cy + 1.4f * dp, pAccent)
        if (plus) canvas.drawRect(cx - 1.4f * dp, cy - 7f * dp, cx + 1.4f * dp, cy + 7f * dp, pAccent)
    }

    private fun drawActions(canvas: Canvas) {
        val top = actionTop()
        val gap = 6f * dp
        val left = PAD_DP * dp
        val buttonWidth = (width - left * 2f - gap * 2f) / 3f
        panel.set(left, top, left + buttonWidth, top + 42f * dp)
        canvas.drawRoundRect(panel, 7f * dp, 7f * dp, if (hit == Hit.COUPLE) pCellOn else pCell)
        canvas.drawText(context.getString(R.string.trebuchet_gear_couple), panel.centerX(), panel.centerY() - (pButton.ascent() + pButton.descent()) / 2f, pButton)
        panel.set(left + buttonWidth + gap, top, left + buttonWidth * 2f + gap, top + 42f * dp)
        canvas.drawRoundRect(panel, 7f * dp, 7f * dp, if (hit == Hit.DUPLICATE) pCellOn else pCell)
        canvas.drawText(context.getString(R.string.trebuchet_gear_duplicate), panel.centerX(), panel.centerY() - (pButton.ascent() + pButton.descent()) / 2f, pButton)
        panel.set(left + buttonWidth * 2f + gap * 2f, top, width - left, top + 42f * dp)
        canvas.drawRoundRect(panel, 7f * dp, 7f * dp, if (hit == Hit.DELETE) pCellOn else pDanger)
        canvas.drawText(context.getString(R.string.trebuchet_gear_delete_short), panel.centerX(), panel.centerY() - (pButton.ascent() + pButton.descent()) / 2f, pButton)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                travel = 0f
                wheelOffset = 0f
                hitTest(event.x, event.y)
                if (hit == Hit.MINUS || hit == Hit.PLUS) {
                    change(direction)
                    postDelayed(repeater, REPEAT_DELAY_MS)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                travel += abs(dx) + abs(dy)
                when (hit) {
                    Hit.HEADER -> dragPanel(dx, dy)
                    Hit.VALUE -> {
                        wheelOffset += dy
                        val step = STEP_DP * dp
                        while (wheelOffset >= step) { change(-1); wheelOffset -= step }
                        while (wheelOffset <= -step) { change(+1); wheelOffset += step }
                    }
                    Hit.MINUS, Hit.PLUS -> if (travel > 2f * TAP_SLOP_DP * dp) release()
                    else -> Unit
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (travel < TAP_SLOP_DP * dp) when (hit) {
                    Hit.VALUE -> change(if (event.y < rowTop(row) + ROW_DP * dp / 2f) +1 else -1)
                    Hit.COUPLE -> gearView?.armLink(GearLinkKind.SHAFT_CLUTCH)
                    Hit.DUPLICATE -> gearView?.duplicateSelected()
                    Hit.DELETE -> gearView?.deleteSelected()
                    else -> Unit
                }
                onEdited?.invoke()
                release()
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    private fun hitTest(x: Float, y: Float) {
        hit = Hit.NONE
        if (y <= HEADER_DP * dp) { hit = Hit.HEADER; return }
        for (candidate in Row.entries) {
            val top = rowTop(candidate)
            if (y !in top..(top + ROW_DP * dp)) continue
            row = candidate
            hit = when {
                x in minusLeft()..(minusLeft() + 44f * dp) -> Hit.MINUS
                x in valueLeft()..(plusLeft() - 4f * dp) -> Hit.VALUE
                x in plusLeft()..(plusLeft() + 44f * dp) -> Hit.PLUS
                else -> Hit.NONE
            }
            direction = if (hit == Hit.MINUS) -1 else if (hit == Hit.PLUS) +1 else 0
            return
        }
        if (y >= actionTop()) {
            val third = width / 3f
            hit = when {
                x < third -> Hit.COUPLE
                x < third * 2f -> Hit.DUPLICATE
                else -> Hit.DELETE
            }
        }
    }

    private fun change(delta: Int) {
        val view = gearView ?: return
        val wheel = view.selectedWheel() ?: return
        when (row) {
            Row.TEETH -> view.setSelectedTeeth(wheel.teeth + delta)
            Row.LAYER -> view.changeSelectedLayer(delta)
            Row.MATERIAL -> view.changeSelectedMaterial(delta)
        }
        onEdited?.invoke()
        invalidate()
    }

    private fun neighborValue(target: Row, delta: Int): String {
        val wheel = gearView?.selectedWheel() ?: return ""
        return when (target) {
            Row.TEETH -> (wheel.teeth + delta).coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH).toString()
            Row.LAYER -> signed((wheel.layer + delta).coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER))
            Row.MATERIAL -> ""
        }
    }

    private fun materialName(material: GearWheelMaterial): String = context.getString(when (material) {
        GearWheelMaterial.WOOD -> R.string.trebuchet_gear_material_wood
        GearWheelMaterial.ALUMINUM -> R.string.trebuchet_gear_material_aluminum
        GearWheelMaterial.STEEL -> R.string.trebuchet_gear_material_steel
        GearWheelMaterial.TITANIUM -> R.string.trebuchet_gear_material_titanium
    })

    private fun signed(value: Int) = if (value > 0) "+$value" else value.toString()
    private fun rowTop(row: Row) = (HEADER_DP + row.ordinal * ROW_DP) * dp
    private fun actionTop() = (HEADER_DP + Row.entries.size * ROW_DP + 5f) * dp
    private fun minusLeft() = (PAD_DP + LABEL_DP) * dp
    private fun valueLeft() = minusLeft() + 48f * dp
    private fun plusLeft() = width - (PAD_DP + 44f) * dp

    private fun dragPanel(dx: Float, dy: Float) {
        translationX = clampX(translationX + dx)
        translationY = clampY(translationY + dy)
        restX[slot] = translationX
        restY[slot] = translationY
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        translationX = restX[slot].takeUnless { it.isNaN() }?.let(::clampX) ?: 0f
        translationY = restY[slot].takeUnless { it.isNaN() }?.let(::clampY) ?: 0f
    }

    private fun clampX(value: Float): Float {
        val host = parent as? View ?: return value
        val min = -left.toFloat()
        val max = (host.width - width - left).toFloat()
        return if (max <= min) min else value.coerceIn(min, max)
    }

    private fun clampY(value: Float): Float {
        val host = parent as? View ?: return value
        val min = -top.toFloat()
        val max = (host.height - height - top).toFloat()
        return if (max <= min) min else value.coerceIn(min, max)
    }

    private fun release() {
        removeCallbacks(repeater)
        hit = Hit.NONE
        direction = 0
        wheelOffset = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
