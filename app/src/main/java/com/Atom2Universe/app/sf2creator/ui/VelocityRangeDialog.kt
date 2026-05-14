package com.Atom2Universe.app.sf2creator.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.Atom2Universe.app.R

/**
 * Dialog for selecting velocity range (0-127).
 * Used for assigning samples to specific velocity layers.
 */
class VelocityRangeDialog(
    context: Context,
    private val currentStart: Int,
    private val currentEnd: Int,
    private val onRangeSelected: (start: Int, end: Int) -> Unit
) : Dialog(context) {

    private lateinit var titleText: TextView
    private lateinit var startSeekbar: SeekBar
    private lateinit var startValueText: TextView
    private lateinit var endSeekbar: SeekBar
    private lateinit var endValueText: TextView
    private lateinit var previewText: TextView
    private lateinit var cancelButton: Button
    private lateinit var okButton: Button

    private var velStart: Int = currentStart
    private var velEnd: Int = currentEnd

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_velocity_range)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        findViews()
        setupSliders()
        setupButtons()
        loadValues()
    }

    private fun findViews() {
        titleText = findViewById(R.id.dialog_title)
        startSeekbar = findViewById(R.id.vel_start_seekbar)
        startValueText = findViewById(R.id.vel_start_value)
        endSeekbar = findViewById(R.id.vel_end_seekbar)
        endValueText = findViewById(R.id.vel_end_value)
        previewText = findViewById(R.id.vel_preview_text)
        cancelButton = findViewById(R.id.cancel_button)
        okButton = findViewById(R.id.ok_button)
    }

    private fun setupSliders() {
        startSeekbar.max = 127
        endSeekbar.max = 127

        startSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                velStart = progress
                if (velStart > velEnd) {
                    velEnd = velStart
                    endSeekbar.progress = velEnd
                }
                updateDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        endSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                velEnd = progress
                if (velEnd < velStart) {
                    velStart = velEnd
                    startSeekbar.progress = velStart
                }
                updateDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        cancelButton.setOnClickListener { dismiss() }
        okButton.setOnClickListener {
            onRangeSelected(velStart, velEnd)
            dismiss()
        }
    }

    private fun loadValues() {
        startSeekbar.progress = velStart
        endSeekbar.progress = velEnd
        updateDisplay()
    }

    private fun updateDisplay() {
        startValueText.text = velStart.toString()
        endValueText.text = velEnd.toString()

        val rangeDescription = when {
            velStart == 0 && velEnd == 127 -> context.getString(R.string.sf2_vel_full_range)
            velStart == velEnd -> context.getString(R.string.sf2_vel_single, velStart)
            velEnd - velStart < 32 -> context.getString(R.string.sf2_vel_narrow_range, velStart, velEnd)
            else -> context.getString(R.string.sf2_vel_range_desc, velStart, velEnd)
        }
        previewText.text = rangeDescription
    }
}
