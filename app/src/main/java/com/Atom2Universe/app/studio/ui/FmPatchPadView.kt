package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.view.KeyEvent
import androidx.core.view.ViewCompat
import com.Atom2Universe.app.R
import kotlin.math.roundToInt

/** Le pad du timbre personnel garde sa position. Les quatre directions sont aussi disponibles au clavier et avec TalkBack. */
internal class FmPatchPadView(context: Context) : XyPadView(
    context, context.getString(R.string.studio_fm_axis_brightness), context.getString(R.string.studio_fm_axis_attack),
) {
    init {
        isFocusable = true
        contentDescription = context.getString(R.string.studio_fm_pad)
        fun addStep(label: Int, dx: Float, dy: Float) {
            ViewCompat.addAccessibilityAction(this, context.getString(label)) { _, _ -> adjust(dx, dy) }
        }
        addStep(R.string.studio_fm_brighter, STEP, 0f)
        addStep(R.string.studio_fm_darker, -STEP, 0f)
        addStep(R.string.studio_fm_slower, 0f, STEP)
        addStep(R.string.studio_fm_faster, 0f, -STEP)
    }

    fun describePosition() {
        ViewCompat.setStateDescription(this, context.getString(R.string.studio_fm_pad_values,
            (padX * 100).roundToInt(), (padY * 100).roundToInt()))
    }

    private fun adjust(dx: Float, dy: Float): Boolean {
        if (isTouching) return false
        val x = (padX + dx).coerceIn(0f, 1f)
        val y = (padY + dy).coerceIn(0f, 1f)
        if (x == padX && y == padY) return false
        onPress?.invoke()
        showAt(x, y)
        onMove?.moved(x, y)
        onRelease?.invoke(true)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val moved = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> adjust(STEP, 0f)
            KeyEvent.KEYCODE_DPAD_LEFT -> adjust(-STEP, 0f)
            KeyEvent.KEYCODE_DPAD_UP -> adjust(0f, STEP)
            KeyEvent.KEYCODE_DPAD_DOWN -> adjust(0f, -STEP)
            else -> false
        }
        return moved || super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        finishTouch(false)
        super.onDetachedFromWindow()
    }

    private companion object { const val STEP = 0.1f }
}
