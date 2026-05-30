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

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val sizeDp = progress + 40
                sizeValueTv.text = "${sizeDp}dp"
                selected?.let { s ->
                    s.sizeDp = sizeDp
                    val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
                    s.view.layoutParams = s.view.layoutParams.also { it.width = sizePx; it.height = sizePx }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        findViewById<View>(R.id.cave_editor_btn_save).setOnClickListener { saveAll() }
        findViewById<View>(R.id.cave_editor_btn_reset).setOnClickListener { confirmReset() }

        canvas.doOnLayout { createButtons() }
    }

    private fun createButtons() {
        val dp = resources.displayMetrics.density
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        for (cfg in CaveControlsPrefs.Btn.entries) {
            val xf     = CaveControlsPrefs.xf(this, cfg)
            val yf     = CaveControlsPrefs.yf(this, cfg)
            val sizeDp = CaveControlsPrefs.sizeDp(this, cfg)
            val sizePx = (sizeDp * dp).toInt()

            val label   = btnLabel(cfg)
            val bgColor = btnColor(cfg)
            val textColor = if (cfg == CaveControlsPrefs.Btn.LASER) 0xFF00D4FF.toInt()
                            else if (cfg == CaveControlsPrefs.Btn.PLACE) 0xFF88FF44.toInt()
                            else Color.WHITE

            val btn = Button(this).apply {
                text = label
                textSize = 9f
                setTextColor(textColor)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bgColor) }
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            }

            val state = BtnState(cfg, xf, yf, sizeDp, btn)
            states.add(state)
            canvas.addView(btn)
            btn.x = xf * w - sizePx / 2f
            btn.y = yf * h - sizePx / 2f
            attachDrag(btn, state)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(btn: Button, state: BtnState) {
        var originTx = 0f; var originTy = 0f

        btn.setOnTouchListener { v, ev ->
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    originTx = ev.rawX - v.x
                    originTy = ev.rawY - v.y
                    selectState(state)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (ev.rawX - originTx).coerceIn(0f, w - v.width)
                    val newY = (ev.rawY - originTy).coerceIn(0f, h - v.height)
                    v.x = newX; v.y = newY
                    state.xf = (newX + v.width / 2f) / w
                    state.yf = (newY + v.height / 2f) / h
                    true
                }
                else -> false
            }
        }
    }

    private fun selectState(state: BtnState) {
        selected = state
        selectedNameTv.text = getString(R.string.cave_controls_selected, btnLabel(state.cfg))
        seekBar.progress = state.sizeDp - 40
        sizeValueTv.text = "${state.sizeDp}dp"
        states.forEach { s -> s.view.alpha = if (s == state) 1f else 0.5f }
    }

    private fun saveAll() {
        states.forEach { s -> CaveControlsPrefs.save(this, s.cfg, s.xf, s.yf, s.sizeDp) }
        Toast.makeText(this, R.string.cave_controls_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.cave_controls_reset)
            .setMessage(R.string.cave_controls_reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CaveControlsPrefs.reset(this)
                val dp = resources.displayMetrics.density
                val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
                states.forEach { s ->
                    s.xf = s.cfg.defaultXf; s.yf = s.cfg.defaultYf; s.sizeDp = s.cfg.defaultSizeDp
                    val sizePx = (s.sizeDp * dp).toInt()
                    s.view.layoutParams = s.view.layoutParams.also { it.width = sizePx; it.height = sizePx }
                    s.view.x = s.xf * w - sizePx / 2f
                    s.view.y = s.yf * h - sizePx / 2f
                }
                selected?.let { selectState(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun btnLabel(cfg: CaveControlsPrefs.Btn) = when (cfg) {
        CaveControlsPrefs.Btn.UP    -> "▲"
        CaveControlsPrefs.Btn.DOWN  -> "▼"
        CaveControlsPrefs.Btn.LASER -> getString(R.string.cave_laser)
        CaveControlsPrefs.Btn.PLACE -> getString(R.string.cave_place)
    }

    private fun btnColor(cfg: CaveControlsPrefs.Btn) = when (cfg) {
        CaveControlsPrefs.Btn.UP, CaveControlsPrefs.Btn.DOWN -> 0x55FFFFFF.toInt()
        CaveControlsPrefs.Btn.LASER                          -> 0x66003366.toInt()
        CaveControlsPrefs.Btn.PLACE                          -> 0x66336600.toInt()
    }
}
