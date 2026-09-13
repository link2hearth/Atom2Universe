package com.Atom2Universe.app.games.caves

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.view.doOnLayout
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal class CaveControlsEditorActivity : ThemedActivity() {
    private companion object {
        private const val SIZE_MIN_DP = 40
        private const val SIZE_MAX_DP = 140
    }

    private lateinit var canvas: FrameLayout
    private lateinit var selectedNameTv: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var sizeValueTv: TextView

    private data class BtnState(
        val cfg: CaveControlsPrefs.Btn,
        var xf: Float, var yf: Float, var sizeDp: Int,
        val view: Button
    )

    private val states = mutableListOf<BtnState>()
    private var selected: BtnState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_cave_controls_editor)

        canvas         = findViewById(R.id.cave_editor_canvas)
        selectedNameTv = findViewById(R.id.cave_editor_selected_name)
        seekBar        = findViewById(R.id.cave_editor_seekbar)
        sizeValueTv    = findViewById(R.id.cave_editor_size_value)
        seekBar.max = SIZE_MAX_DP - SIZE_MIN_DP

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val sizeDp = progress + SIZE_MIN_DP
                sizeValueTv.text = getString(R.string.cave_controls_size_value, sizeDp)
                if (!fromUser) return
                selected?.let { s ->
                    s.sizeDp = sizeDp
                    applyStateSize(s)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        findViewById<View>(R.id.cave_editor_btn_save).setOnClickListener { saveAll() }
        findViewById<View>(R.id.cave_editor_btn_reset).setOnClickListener { confirmReset() }

        canvas.doOnLayout { createButtons() }
        canvas.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                states.forEach(::applyStateSize)
            }
        }
    }

    private fun createButtons() {
        if (canvas.width <= 0 || canvas.height <= 0) return
        states.clear()
        canvas.removeAllViews()
        selected = null

        val dp = resources.displayMetrics.density
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        for (cfg in CaveControlsPrefs.Btn.entries) {
            val xf     = CaveControlsPrefs.xf(this, cfg).coerceIn(0f, 1f)
            val yf     = CaveControlsPrefs.yf(this, cfg).coerceIn(0f, 1f)
            val sizeDp = CaveControlsPrefs.sizeDp(this, cfg)
            val normalizedSize = normalizeStateSize(sizeDp)
            val buttonSizePx = (normalizedSize * dp).toInt()
            val normalizedXf = normalizeNormalizedCoord(xf, w, buttonSizePx)
            val normalizedYf = normalizeNormalizedCoord(yf, h, buttonSizePx)

            val label   = btnLabel(cfg)
            val bgColor = btnColor(cfg)
            val textColor = if (cfg == CaveControlsPrefs.Btn.LASER) 0xFF00D4FF.toInt()
                            else if (cfg == CaveControlsPrefs.Btn.PLACE) 0xFF88FF44.toInt()
                            else Color.WHITE

            val btn = Button(this).apply {
                text = label
                textSize = 10f
                setTextColor(textColor)
                isAllCaps = false
                includeFontPadding = false
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bgColor) }
                layoutParams = FrameLayout.LayoutParams(buttonSizePx, buttonSizePx)
            }

            val state = BtnState(cfg, normalizedXf, normalizedYf, normalizedSize, btn)
            states.add(state)
            canvas.addView(btn)
            btn.x = normalizedXf * w - buttonSizePx / 2f
            btn.y = normalizedYf * h - buttonSizePx / 2f
            attachDrag(btn, state)
        }

        states.firstOrNull()?.let { selectState(it) }
    }

    private fun normalizeStateSize(sizeDp: Int): Int = sizeDp.coerceIn(SIZE_MIN_DP, SIZE_MAX_DP)

    private fun normalizeNormalizedCoord(normalized: Float, parentSize: Float, buttonSizePx: Int): Float {
        if (!normalized.isFinite()) return 0.5f
        val half = (buttonSizePx / (parentSize * 2f)).coerceAtMost(0.5f)
        if (half <= 0f) return normalized.coerceIn(0f, 1f)
        return normalized.coerceIn(half, 1f - half)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(btn: Button, state: BtnState) {
        var originTx = 0f
        var originTy = 0f

        val parentLoc = IntArray(2)

        btn.setOnTouchListener { v, ev ->
            canvas.getLocationOnScreen(parentLoc)
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val localX = ev.x
            val localY = ev.y
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    originTx = localX
                    originTy = localY
                    selectState(state)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (w - v.width).coerceAtLeast(0f)
                    val maxY = (h - v.height).coerceAtLeast(0f)
                    val newX = (ev.rawX - parentLoc[0] - originTx).coerceIn(0f, maxX)
                    val newY = (ev.rawY - parentLoc[1] - originTy).coerceIn(0f, maxY)
                    v.x = newX; v.y = newY
                    state.xf = (newX + v.width / 2f) / w
                    state.yf = (newY + v.height / 2f) / h
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    true
                }
                else -> false
            }
        }
    }

    private fun selectState(state: BtnState) {
        selected = state
        selectedNameTv.text = getString(R.string.cave_controls_selected, btnLabel(state.cfg))
        seekBar.progress = (state.sizeDp - SIZE_MIN_DP).coerceIn(0, seekBar.max)
        sizeValueTv.text = getString(R.string.cave_controls_size_value, state.sizeDp)
        states.forEach { s -> s.view.alpha = if (s == state) 1f else 0.5f }
    }

    private fun saveAll() {
        if (states.size != CaveControlsPrefs.Btn.entries.size) return
        val saved = CaveControlsPrefs.saveAll(this, states.associate { s ->
            s.cfg to CaveControlsPrefs.Layout(s.xf, s.yf, s.sizeDp)
        })
        if (!saved) {
            Toast.makeText(this, R.string.cave_controls_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.cave_controls_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.cave_controls_reset)
            .setMessage(R.string.cave_controls_reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CaveControlsPrefs.reset(this)
                createButtons()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun btnLabel(cfg: CaveControlsPrefs.Btn) = when (cfg) {
        CaveControlsPrefs.Btn.UP    -> getString(R.string.cave_jump)
        CaveControlsPrefs.Btn.DOWN  -> getString(R.string.cave_controls_crouch)
        CaveControlsPrefs.Btn.LASER -> getString(R.string.cave_controls_shoot)
        CaveControlsPrefs.Btn.PLACE -> getString(R.string.cave_place)
    }

    private fun btnColor(cfg: CaveControlsPrefs.Btn) = when (cfg) {
        CaveControlsPrefs.Btn.UP, CaveControlsPrefs.Btn.DOWN -> 0x55FFFFFF.toInt()
        CaveControlsPrefs.Btn.LASER                          -> 0x66003366.toInt()
        CaveControlsPrefs.Btn.PLACE                          -> 0x66336600.toInt()
    }

    private fun applyStateSize(state: BtnState) {
        val dp = resources.displayMetrics.density
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val sizePx = (state.sizeDp * dp).toInt()
        state.view.layoutParams = state.view.layoutParams.also {
            it.width = sizePx
            it.height = sizePx
        }

        val clampedXf = normalizeNormalizedCoord(state.xf, w, sizePx)
        val clampedYf = normalizeNormalizedCoord(state.yf, h, sizePx)
        state.xf = clampedXf
        state.yf = clampedYf
        state.view.x = clampedXf * w - sizePx / 2f
        state.view.y = clampedYf * h - sizePx / 2f
    }

}
