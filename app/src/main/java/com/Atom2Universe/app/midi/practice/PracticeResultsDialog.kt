package com.Atom2Universe.app.midi.practice

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.practice.scoring.ScoringMetrics
import java.text.NumberFormat
import java.util.Locale

/**
 * Discrete centered dialog showing practice session results.
 * Displayed at the end of a track or when user presses STOP.
 * Semi-transparent, easy to dismiss (tap outside or X button).
 */
class PracticeResultsDialog(
    context: Context,
    private val metrics: ScoringMetrics,
    private val trackTitle: String,
    private val onDismiss: (() -> Unit)? = null
) : Dialog(context, R.style.Theme_PracticeResultsDialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_practice_results)

        // Semi-transparent background, centered, dismissable on touch outside
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            // Dim the background slightly
            setDimAmount(0.5f)
        }

        // Allow dismiss by tapping outside
        setCanceledOnTouchOutside(true)

        setupViews()
    }

    private fun setupViews() {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

        // Track title
        findViewById<TextView>(R.id.txt_track_title).text = trackTitle

        // Grade (large)
        val txtGrade = findViewById<TextView>(R.id.txt_grade)
        txtGrade.text = metrics.grade
        txtGrade.setTextColor(getGradeColor(metrics.grade))

        // Score
        findViewById<TextView>(R.id.txt_score).text = numberFormat.format(metrics.score)

        // Accuracy
        findViewById<TextView>(R.id.txt_accuracy).text =
            String.format(Locale.getDefault(), "%.1f%% %s",
                metrics.accuracy,
                context.getString(R.string.practice_results_accuracy).lowercase()
            )

        // Perfect notes
        findViewById<TextView>(R.id.txt_perfect_notes).text =
            numberFormat.format(metrics.perfectNotes)

        // Good notes (total - includes perfect)
        findViewById<TextView>(R.id.txt_good_notes).text =
            numberFormat.format(metrics.goodNotes)

        // Missed notes
        findViewById<TextView>(R.id.txt_missed_notes).text =
            numberFormat.format(metrics.missedNotes)

        // Wrong notes
        findViewById<TextView>(R.id.txt_wrong_notes).text =
            numberFormat.format(metrics.wrongNotes)

        // Best streak
        findViewById<TextView>(R.id.txt_best_streak).text =
            numberFormat.format(metrics.bestStreak)

        // Close button (X)
        findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            dismiss()
        }
    }

    override fun dismiss() {
        super.dismiss()
        onDismiss?.invoke()
    }

    private fun getGradeColor(grade: String): Int {
        return when (grade) {
            "S" -> 0xFFFFD700.toInt()  // Gold
            "A" -> 0xFF4CAF50.toInt()  // Green
            "B" -> 0xFF8BC34A.toInt()  // Light Green
            "C" -> 0xFFFFC107.toInt()  // Amber
            "D" -> 0xFFFF9800.toInt()  // Orange
            else -> 0xFFF44336.toInt()  // Red (F)
        }
    }
}
