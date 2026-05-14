package com.Atom2Universe.app

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import java.util.Locale

/**
 * Dialog for setting the global sleep timer.
 * Shows hour/minute pickers for configuring the timer duration.
 */
class SleepTimerDialog(
    context: Context,
    private val onTimerChanged: (() -> Unit)? = null
) : Dialog(context, R.style.Theme_A2U_Dialog), SleepTimerManager.Listener {

    private lateinit var remainingTimeText: TextView
    private lateinit var hoursPicker: NumberPicker
    private lateinit var minutesPicker: NumberPicker
    private lateinit var btnStop: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnStart: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_sleep_timer)

        // Set dialog width
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        initViews()
        setupPickers()
        setupButtons()
        updateUI()

        SleepTimerManager.addListener(this)
    }

    override fun dismiss() {
        SleepTimerManager.removeListener(this)
        super.dismiss()
    }

    private fun initViews() {
        remainingTimeText = findViewById(R.id.remaining_time_text)
        hoursPicker = findViewById(R.id.hours_picker)
        minutesPicker = findViewById(R.id.minutes_picker)
        btnStop = findViewById(R.id.btn_stop)
        btnCancel = findViewById(R.id.btn_cancel)
        btnStart = findViewById(R.id.btn_start)
    }

    private fun setupPickers() {
        // Hours picker: 0-23
        hoursPicker.minValue = 0
        hoursPicker.maxValue = 23
        hoursPicker.wrapSelectorWheel = true
        hoursPicker.setFormatter { String.format(Locale.ROOT, "%02d", it) }

        // Minutes picker: 0-59
        minutesPicker.minValue = 0
        minutesPicker.maxValue = 59
        minutesPicker.wrapSelectorWheel = true
        minutesPicker.setFormatter { String.format(Locale.ROOT, "%02d", it) }

        // Set initial values
        if (SleepTimerManager.isTimerRunning) {
            val (hours, minutes) = SleepTimerManager.formatRemainingTime()
            hoursPicker.value = hours
            minutesPicker.value = minutes
        } else {
            // Default: 30 minutes
            hoursPicker.value = 0
            minutesPicker.value = 30
        }

        // Fix NumberPicker text color on dark background
        setNumberPickerTextColor(hoursPicker, android.graphics.Color.WHITE)
        setNumberPickerTextColor(minutesPicker, android.graphics.Color.WHITE)
    }

    private fun setNumberPickerTextColor(numberPicker: NumberPicker, color: Int) {
        try {
            val count = numberPicker.childCount
            for (i in 0 until count) {
                val child = numberPicker.getChildAt(i)
                if (child is android.widget.EditText) {
                    child.setTextColor(color)
                    child.setHintTextColor(color)
                }
            }
            // Use reflection to set the text color for the wheel
            val fields = NumberPicker::class.java.declaredFields
            for (field in fields) {
                if (field.name == "mSelectorWheelPaint" ||
                    field.name == "mInputText") {
                    field.isAccessible = true
                    val paint = field.get(numberPicker)
                    if (paint is android.graphics.Paint) {
                        paint.color = color
                    } else if (paint is android.widget.EditText) {
                        paint.setTextColor(color)
                    }
                }
            }
            numberPicker.invalidate()
        } catch (e: Exception) {
            // Ignore reflection errors
        }
    }

    private fun setupButtons() {
        btnStop.setOnClickListener {
            SleepTimerManager.cancelTimer()
            updateUI()
            onTimerChanged?.invoke()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnStart.setOnClickListener {
            val hours = hoursPicker.value
            val minutes = minutesPicker.value

            if (hours == 0 && minutes == 0) {
                // Don't start timer with 0 duration
                dismiss()
                return@setOnClickListener
            }

            val durationMillis = (hours * 60 + minutes) * 60 * 1000L
            SleepTimerManager.startTimer(durationMillis)
            onTimerChanged?.invoke()
            dismiss()
        }
    }

    private fun updateUI() {
        val isRunning = SleepTimerManager.isTimerRunning

        if (isRunning) {
            val (hours, minutes) = SleepTimerManager.formatRemainingTime()
            remainingTimeText.text = context.getString(R.string.sleep_timer_remaining, hours, minutes)
            remainingTimeText.visibility = View.VISIBLE
            btnStop.visibility = View.VISIBLE
        } else {
            remainingTimeText.text = context.getString(R.string.sleep_timer_not_set)
            remainingTimeText.visibility = View.VISIBLE
            btnStop.visibility = View.GONE
        }
    }

    // SleepTimerManager.Listener implementation
    override fun onTimerTick(remainingMillis: Long) {
        updateUI()
    }

    override fun onTimerFinished() {
        updateUI()
    }

    override fun onTimerCancelled() {
        updateUI()
    }
}
