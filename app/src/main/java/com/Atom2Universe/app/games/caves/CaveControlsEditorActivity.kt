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
import com.Atom2Universe.app.games.caves.input.GamepadBindings
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
    private lateinit var bubble: ScrollView
    private lateinit var crouchChoices: RadioGroup
    private lateinit var holdChoice: RadioButton
    private lateinit var toggleChoice: RadioButton
    private var crouchToggle = false
    private var runToggle = false
    private var placeAtCrosshair = false
    private lateinit var placementChoices: RadioGroup
    private lateinit var touchPlacementChoice: RadioButton
    private lateinit var crosshairPlacementChoice: RadioButton
    private var updatingChoices = false
    private lateinit var gamepadEditor: CaveGamepadEditor
    private var gamepadTab = false

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
        crouchToggle = CaveControlsPrefs.crouchToggle(this)
        runToggle = CaveControlsPrefs.runToggle(this)
        placeAtCrosshair = CaveControlsPrefs.placeAtCrosshair(this)
        createBubble()
        gamepadEditor = CaveGamepadEditor(this, findViewById(R.id.cave_editor_gamepad),
            CaveControlsPrefs.gamepadBindings(this).toMutableMap())
        gamepadEditor.refresh()
        findViewById<RadioGroup>(R.id.cave_editor_tabs).setOnCheckedChangeListener { _, id ->
            gamepadTab = id == R.id.cave_editor_tab_gamepad
            closeBubble()
            canvas.visibility = if (gamepadTab) View.GONE else View.VISIBLE
            findViewById<View>(R.id.cave_editor_touch_hint).visibility = if (gamepadTab) View.GONE else View.VISIBLE
            findViewById<View>(R.id.cave_editor_gamepad).visibility = if (gamepadTab) View.VISIBLE else View.GONE
        }
        canvas.setOnClickListener { closeBubble() }
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
                if (bubble.visibility == View.VISIBLE) positionBubble()
            }
        }
    }

    private fun createButtons(defaults: Boolean = false) {
        if (canvas.width <= 0 || canvas.height <= 0) return
        states.clear()
        canvas.removeAllViews()
        selected = null

        val dp = resources.displayMetrics.density
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        for (cfg in CaveControlsPrefs.Btn.entries) {
            val xf = if (defaults) cfg.defaultXf else CaveControlsPrefs.xf(this, cfg).coerceIn(0f, 1f)
            val yf = if (defaults) cfg.defaultYf else CaveControlsPrefs.yf(this, cfg).coerceIn(0f, 1f)
            val sizeDp = if (defaults) cfg.defaultSizeDp else CaveControlsPrefs.sizeDp(this, cfg)
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

        closeBubble()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createBubble() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        selectedNameTv = TextView(this).apply { setTextColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uTextColor)); textSize = 15f }
        header.addView(selectedNameTv, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            setText(R.string.cave_controls_close)
            isAllCaps = false
            setOnClickListener { closeBubble() }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        content.addView(header)
        val sizeRow = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        sizeRow.addView(TextView(this).apply {
            setText(R.string.cave_controls_size); setTextColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uSecondaryTextColor))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        sizeValueTv = TextView(this).apply { setTextColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uTextColor)) }
        sizeRow.addView(sizeValueTv)
        content.addView(sizeRow)
        seekBar = SeekBar(this)
        content.addView(seekBar, LinearLayout.LayoutParams(-1, dp(48)))
        crouchChoices = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        holdChoice = RadioButton(this).apply {
            id = View.generateViewId(); setText(R.string.cave_controls_hold); setTextColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uTextColor))
        }
        toggleChoice = RadioButton(this).apply {
            id = View.generateViewId(); setText(R.string.cave_controls_toggle); setTextColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uTextColor))
        }
        crouchChoices.addView(holdChoice, RadioGroup.LayoutParams(-1, dp(48)))
        crouchChoices.addView(toggleChoice, RadioGroup.LayoutParams(-1, dp(48)))
        crouchChoices.setOnCheckedChangeListener { _, id ->
            if (!updatingChoices) {
                when (selected?.cfg) {
                    CaveControlsPrefs.Btn.DOWN -> crouchToggle = id == toggleChoice.id
                    CaveControlsPrefs.Btn.RUN -> runToggle = id == toggleChoice.id
                    else -> Unit
                }
            }
        }
        content.addView(crouchChoices)
        placementChoices = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        touchPlacementChoice = RadioButton(this).apply {
            id = View.generateViewId(); setText(R.string.cave_controls_place_touch); setTextColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uTextColor))
            minHeight = dp(64)
        }
        crosshairPlacementChoice = RadioButton(this).apply {
            id = View.generateViewId(); setText(R.string.cave_controls_place_crosshair); setTextColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uTextColor))
            minHeight = dp(64)
        }
        placementChoices.addView(touchPlacementChoice, RadioGroup.LayoutParams(-1, -2))
        placementChoices.addView(crosshairPlacementChoice, RadioGroup.LayoutParams(-1, -2))
        placementChoices.setOnCheckedChangeListener { _, id ->
            if (!updatingChoices) placeAtCrosshair = id == crosshairPlacementChoice.id
        }
        content.addView(placementChoices)
        bubble = ScrollView(this).apply {
            visibility = View.GONE
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                setColor(com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uSurfaceColor)); cornerRadius = com.Atom2Universe.app.AppearanceStyle.corner(this@CaveControlsEditorActivity, 20f)
                setStroke(dp(1), com.Atom2Universe.app.AppearanceStyle.color(this@CaveControlsEditorActivity, R.attr.a2uOutlineColor))
            }
            addView(content)
        }
        (canvas.parent as FrameLayout).addView(bubble, FrameLayout.LayoutParams(dp(320), -2))
    }

    private fun closeBubble() {
        bubble.visibility = View.GONE
        selected = null
        states.forEach { it.view.alpha = 1f }
    }

    private fun positionBubble() {
        val state = selected ?: return
        val gap = dp(10)
        val top = findViewById<View>(R.id.cave_editor_tabs).bottom + gap
        val width = dp(320).coerceAtMost((canvas.width - gap * 2).coerceAtLeast(1))
        val availableHeight = (canvas.height - top - gap).coerceAtLeast(1)
        bubble.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST))
        bubble.layoutParams = FrameLayout.LayoutParams(width, bubble.measuredHeight)
        val button = state.view
        val right = button.x + button.layoutParams.width + gap
        val left = button.x - width - gap
        bubble.x = (if (right + width <= canvas.width - gap) right else left)
            .coerceIn(gap.toFloat(), (canvas.width - width - gap).coerceAtLeast(gap).toFloat())
        bubble.y = (button.y + button.layoutParams.height / 2f - bubble.measuredHeight / 2f)
            .coerceIn(top.toFloat(), (canvas.height - bubble.measuredHeight - gap).coerceAtLeast(top).toFloat())
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
                    selected = state
                    bubble.visibility = View.GONE
                    findViewById<View>(R.id.cave_editor_topbar).visibility = View.INVISIBLE
                    findViewById<View>(R.id.cave_editor_tabs).visibility = View.INVISIBLE
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
                    findViewById<View>(R.id.cave_editor_topbar).visibility = View.VISIBLE
                    findViewById<View>(R.id.cave_editor_tabs).visibility = View.VISIBLE
                    selectState(state)
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
        val isRun = state.cfg == CaveControlsPrefs.Btn.RUN
        crouchChoices.visibility = if (state.cfg == CaveControlsPrefs.Btn.DOWN || isRun) View.VISIBLE else View.GONE
        holdChoice.setText(if (isRun) R.string.cave_controls_run_hold else R.string.cave_controls_hold)
        toggleChoice.setText(if (isRun) R.string.cave_controls_run_toggle else R.string.cave_controls_toggle)
        updatingChoices = true
        crouchChoices.check(if (if (isRun) runToggle else crouchToggle) toggleChoice.id else holdChoice.id)
        placementChoices.visibility = if (state.cfg == CaveControlsPrefs.Btn.PLACE) View.VISIBLE else View.GONE
        placementChoices.check(if (placeAtCrosshair) crosshairPlacementChoice.id else touchPlacementChoice.id)
        updatingChoices = false
        bubble.visibility = View.VISIBLE
        positionBubble()
    }

    private fun saveAll() {
        if (states.size != CaveControlsPrefs.Btn.entries.size) return
        val saved = CaveControlsPrefs.saveAll(this, states.associate { s ->
            s.cfg to CaveControlsPrefs.Layout(s.xf, s.yf, s.sizeDp)
        }, crouchToggle, runToggle, placeAtCrosshair, gamepadEditor.bindings)
        if (!saved) {
            Toast.makeText(this, R.string.cave_controls_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.cave_controls_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmReset() {
        com.Atom2Universe.app.util.ImmersiveMaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.cave_controls_reset)
            .setMessage(if (gamepadTab) R.string.cave_pad_reset_confirm else R.string.cave_controls_reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (gamepadTab) {
                    gamepadEditor.bindings.clear()
                    gamepadEditor.bindings.putAll(GamepadBindings.defaults())
                    gamepadEditor.refresh()
                } else {
                    crouchToggle = false
                    runToggle = false
                    placeAtCrosshair = false
                    createButtons(defaults = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun btnLabel(cfg: CaveControlsPrefs.Btn) = when (cfg) {
        CaveControlsPrefs.Btn.UP    -> getString(R.string.cave_jump)
        CaveControlsPrefs.Btn.DOWN  -> getString(R.string.cave_controls_crouch)
        CaveControlsPrefs.Btn.LASER -> getString(R.string.cave_controls_shoot)
        CaveControlsPrefs.Btn.PLACE -> getString(R.string.cave_place)
        CaveControlsPrefs.Btn.RUN -> getString(R.string.cave_controls_run)
        CaveControlsPrefs.Btn.RELOAD -> getString(R.string.cave_controls_reload)
    }

    private fun btnColor(cfg: CaveControlsPrefs.Btn) = when (cfg) {
        CaveControlsPrefs.Btn.UP, CaveControlsPrefs.Btn.DOWN, CaveControlsPrefs.Btn.RUN, CaveControlsPrefs.Btn.RELOAD -> 0x55FFFFFF.toInt()
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
