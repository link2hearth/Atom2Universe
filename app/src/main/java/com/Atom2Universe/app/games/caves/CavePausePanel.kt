package com.Atom2Universe.app.games.caves

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.Atom2Universe.app.R

/** Pause uses the same transparent overlay and persistent hotbar as the other panels. */
internal class CavePausePanel(private val activity: CaveActivity) {
    private fun dp(value: Int) = CaveUiStyle.dp(activity, value)

    fun show(quit: () -> Unit) {
        if (activity.invOverlay.visibility == View.VISIBLE) return
        var distances = CaveViewDistances.load(activity)
        var applied = distances
        var updating = false
        fun applyDistances() {
            if (distances == applied) return
            applied = distances
            distances.save(activity)
            val value = distances
            activity.glView.queueEvent { activity.renderer.setViewDistances(value) }
        }
        val root = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(minOf(View.MeasureSpec.getSize(widthMeasureSpec), dp(620)), View.MeasureSpec.EXACTLY),
                    heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = CaveUiStyle.bubble(activity)
            elevation = dp(8).toFloat()
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        root.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                setText(R.string.cave_pause_title); textSize = 18f; setTextColor(CaveUiStyle.TEXT)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(activity).apply {
                CaveUiStyle.icon(this, "close", activity.getString(R.string.cave_pause_resume))
                setOnClickListener { activity.invManager.closeInventory() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        })
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(activity).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        fun label() = TextView(activity).apply {
            textSize = 14f; setTextColor(CaveUiStyle.TEXT)
            setPadding(0, dp(6), 0, 0)
            body.addView(this)
        }
        fun slider(minimum: Int, maximum: Int, value: Int) = SeekBar(activity).apply {
            min = minimum; max = maximum; progress = value
            progressTintList = ColorStateList.valueOf(CaveUiStyle.ACCENT)
            thumbTintList = ColorStateList.valueOf(CaveUiStyle.ACCENT)
            body.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
        }
        val simulationLabel = label()
        val simulation = slider(CaveViewDistances.MIN_SIMULATION, CaveViewDistances.MAX_SIMULATION, distances.simulation)
        val viewLabel = label()
        val view = slider(distances.simulation, CaveViewDistances.MAX_VIEW, distances.view)
        fun refreshLabels() {
            simulationLabel.text = activity.getString(R.string.cave_pause_simulation_distance, distances.simulation)
            viewLabel.text = activity.getString(R.string.cave_pause_view_distance, distances.view)
            simulation.contentDescription = simulationLabel.text
            view.contentDescription = viewLabel.text
        }
        fun listener(isSimulation: Boolean) = object : SeekBar.OnSeekBarChangeListener {
            private var dragging = false
            override fun onStartTrackingTouch(seekBar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) { dragging = false; applyDistances() }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (updating || !fromUser) return
                distances = (if (isSimulation) distances.copy(simulation = progress)
                    else distances.copy(view = progress)).normalized()
                updating = true
                view.min = distances.simulation
                view.progress = distances.view
                updating = false
                refreshLabels()
                // Keyboard/accessibility changes have no touch-release event.
                if (!dragging) applyDistances()
            }
        }
        simulation.setOnSeekBarChangeListener(listener(true))
        view.setOnSeekBarChangeListener(listener(false))
        refreshLabels()
        root.addView(Button(activity).apply {
            setText(R.string.cave_quit_confirm); CaveUiStyle.button(this)
            setTextColor(CaveUiStyle.WARNING)
            setOnClickListener {
                isEnabled = false
                activity.invManager.closeInventory()
                quit()
            }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        activity.invManager.showStoragePage(root) { applyDistances() }
    }
}
