package com.Atom2Universe.app.midi.practice.scoring

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R

/**
 * Custom view that displays scoring metrics during practice.
 *
 * Features:
 * - Modular: shows only metrics enabled in ScoringConfig
 * - Animated: score/streak changes animate smoothly
 * - Themed: uses a2uMidiAccent for accent color
 * - Responsive: adapts to compact mode
 */
class ScoreDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), ScoringListener {

    companion object {
        private const val TAG = "ScoreDisplayView"
    }

    // Config
    private var config = ScoringConfig()

    // Text views for each metric
    private var scoreLabel: TextView? = null
    private var scoreValue: TextView? = null
    private var accuracyLabel: TextView? = null
    private var accuracyValue: TextView? = null
    private var streakLabel: TextView? = null
    private var streakValue: TextView? = null
    private var bestStreakLabel: TextView? = null
    private var bestStreakValue: TextView? = null
    private var goodNotesLabel: TextView? = null
    private var goodNotesValue: TextView? = null
    private var perfectNotesLabel: TextView? = null
    private var perfectNotesValue: TextView? = null
    private var missedNotesLabel: TextView? = null
    private var missedNotesValue: TextView? = null
    private var comboLabel: TextView? = null
    private var comboValue: TextView? = null
    private var gradeValue: TextView? = null

    // Popup for streak milestones
    private var streakPopup: TextView? = null

    // Animation values
    private var displayedScore: Long = 0
    private var scoreAnimator: ValueAnimator? = null

    // Colors
    private var accentColor: Int = Color.CYAN
    private var textPrimaryColor: Int = Color.WHITE
    private var textSecondaryColor: Int = Color.GRAY
    private var successColor: Int = Color.GREEN
    private var errorColor: Int = Color.RED

    init {
        orientation = VERTICAL
        gravity = Gravity.END
        setPadding(16, 8, 16, 8)

        loadColors()
        buildViews()
    }

    private fun loadColors() {
        // Get theme accent color
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.a2uMidiAccent, typedValue, true)
        accentColor = typedValue.data

        // Get other colors
        textPrimaryColor = ContextCompat.getColor(context, R.color.midi_text_primary)
        textSecondaryColor = ContextCompat.getColor(context, R.color.midi_text_secondary)
        successColor = Color.parseColor("#4CAF50") // Green
        errorColor = Color.parseColor("#F44336") // Red
    }

    /**
     * Update configuration and rebuild views
     */
    fun setConfig(newConfig: ScoringConfig) {
        config = newConfig
        buildViews()
    }

    /**
     * Rebuild all views based on current config
     */
    private fun buildViews() {
        removeAllViews()

        if (!config.scoringEnabled || !config.hasVisibleMetrics()) {
            visibility = View.GONE
            return
        }

        visibility = View.VISIBLE

        val textSize = if (config.compactMode) 12f else 14f
        val valueSizeLarge = if (config.compactMode) 16f else 20f
        val spacing = if (config.compactMode) 2 else 4

        // Score
        if (config.showScore) {
            addMetricRow("Score", "0", textSize, valueSizeLarge, true).also {
                scoreLabel = it.first
                scoreValue = it.second
            }
        }

        // Combo (show prominently when active)
        if (config.showCombo) {
            addMetricRow("Combo", "1x", textSize, valueSizeLarge, true).also {
                comboLabel = it.first
                comboValue = it.second
            }
        }

        // Accuracy
        if (config.showAccuracy) {
            addMetricRow("Accuracy", "0%", textSize, textSize, false).also {
                accuracyLabel = it.first
                accuracyValue = it.second
            }
        }

        // Current Streak
        if (config.showCurrentStreak) {
            addMetricRow("Streak", "0", textSize, textSize, false).also {
                streakLabel = it.first
                streakValue = it.second
            }
        }

        // Best Streak
        if (config.showBestStreak) {
            addMetricRow("Best", "0", textSize, textSize, false).also {
                bestStreakLabel = it.first
                bestStreakValue = it.second
            }
        }

        // Good Notes
        if (config.showGoodNotes) {
            addMetricRow("Good", "0", textSize, textSize, false).also {
                goodNotesLabel = it.first
                goodNotesValue = it.second
            }
        }

        // Perfect Notes
        if (config.showPerfectNotes) {
            addMetricRow("Perfect", "0", textSize, textSize, false).also {
                perfectNotesLabel = it.first
                perfectNotesValue = it.second
            }
        }

        // Missed Notes
        if (config.showMissedNotes) {
            addMetricRow("Missed", "0", textSize, textSize, false).also {
                missedNotesLabel = it.first
                missedNotesValue = it.second
            }
        }

        // Grade
        if (config.showGrade) {
            addGradeView(valueSizeLarge)
        }

        // Streak popup (for milestones)
        if (config.showStreakPopups) {
            createStreakPopup()
        }
    }

    private fun addMetricRow(
        label: String,
        initialValue: String,
        labelSize: Float,
        valueSize: Float,
        isLarge: Boolean
    ): Pair<TextView, TextView> {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = if (config.compactMode) 2 else 4
            }
        }

        val labelView = TextView(context).apply {
            text = "$label: "
            setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSize)
            setTextColor(textSecondaryColor)
        }

        val valueView = TextView(context).apply {
            text = initialValue
            setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSize)
            setTextColor(if (isLarge) accentColor else textPrimaryColor)
            if (isLarge) {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        row.addView(labelView)
        row.addView(valueView)
        addView(row)

        return Pair(labelView, valueView)
    }

    private fun addGradeView(textSize: Float) {
        gradeValue = TextView(context).apply {
            text = "F"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize * 1.5f)
            setTextColor(textSecondaryColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
        }
        addView(gradeValue)
    }

    private fun createStreakPopup() {
        streakPopup = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(accentColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            alpha = 0f
        }
        // Popup will be shown overlaid, not in this layout
    }

    // ============ ScoringListener Implementation ============

    override fun onMetricsUpdated(metrics: ScoringMetrics, event: NoteEvent?) {
        post {
            updateDisplay(metrics, event)
        }
    }

    override fun onStreakMilestone(streak: Int) {
        if (config.showStreakPopups) {
            post {
                showStreakPopup(streak)
            }
        }
    }

    override fun onComboChanged(multiplier: Int) {
        post {
            updateComboDisplay(multiplier)
        }
    }

    override fun onSessionComplete(finalMetrics: ScoringMetrics) {
        post {
            updateDisplay(finalMetrics, null)
            // Could show a final results animation here
        }
    }

    // ============ Display Updates ============

    private fun updateDisplay(metrics: ScoringMetrics, event: NoteEvent?) {
        // Score with animation
        if (config.showScore && metrics.score != displayedScore) {
            animateScoreChange(displayedScore, metrics.score)
        }

        // Accuracy
        accuracyValue?.text = String.format("%.1f%%", metrics.accuracy)

        // Streaks
        streakValue?.text = metrics.currentStreak.toString()
        bestStreakValue?.text = metrics.bestStreak.toString()

        // Note counts
        goodNotesValue?.text = metrics.goodNotes.toString()
        perfectNotesValue?.text = metrics.perfectNotes.toString()
        missedNotesValue?.text = metrics.missedNotes.toString()

        // Missed notes in red
        missedNotesValue?.setTextColor(if (metrics.missedNotes > 0) errorColor else textPrimaryColor)

        // Grade with color
        gradeValue?.let {
            it.text = metrics.grade
            it.setTextColor(getGradeColor(metrics.grade))
        }

        // Highlight streak if active
        streakValue?.setTextColor(
            if (metrics.currentStreak >= 5) successColor else textPrimaryColor
        )

        // Flash effect on hit
        if (event is NoteEvent.NoteHit && config.showAnimations) {
            flashView(if (event.isPerfectTiming) perfectNotesValue else goodNotesValue)
        }
    }

    private fun animateScoreChange(from: Long, to: Long) {
        if (!config.showAnimations) {
            scoreValue?.text = to.toString()
            displayedScore = to
            return
        }

        scoreAnimator?.cancel()
        scoreAnimator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat()).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = (animation.animatedValue as Float).toLong()
                scoreValue?.text = value.toString()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    displayedScore = to
                }
            })
            start()
        }
    }

    private fun updateComboDisplay(multiplier: Int) {
        comboValue?.text = "${multiplier}x"
        comboValue?.setTextColor(
            when (multiplier) {
                4 -> Color.parseColor("#FFD700") // Gold
                3 -> Color.parseColor("#FF9800") // Orange
                2 -> successColor
                else -> textPrimaryColor
            }
        )

        // Scale animation for combo change
        if (config.showAnimations && multiplier > 1) {
            comboValue?.let { view ->
                view.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(100)
                    .withEndAction {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
            }
        }
    }

    private fun showStreakPopup(streak: Int) {
        // This would show an overlay popup like "10 STREAK!"
        // For now, just flash the streak value
        streakValue?.let { view ->
            val originalColor = view.currentTextColor
            view.setTextColor(accentColor)
            view.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator())
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .withEndAction {
                            view.setTextColor(if (streak > 0) successColor else textPrimaryColor)
                        }
                        .start()
                }
                .start()
        }
    }

    private fun flashView(view: TextView?) {
        view ?: return
        if (!config.showAnimations) return

        view.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun getGradeColor(grade: String): Int {
        return when (grade) {
            "S" -> Color.parseColor("#FFD700") // Gold
            "A" -> successColor
            "B" -> Color.parseColor("#8BC34A") // Light green
            "C" -> Color.parseColor("#FFC107") // Amber
            "D" -> Color.parseColor("#FF9800") // Orange
            else -> errorColor
        }
    }

    /**
     * Reset display for new session
     */
    fun reset() {
        displayedScore = 0
        scoreAnimator?.cancel()

        scoreValue?.text = "0"
        accuracyValue?.text = "0%"
        streakValue?.text = "0"
        bestStreakValue?.text = "0"
        goodNotesValue?.text = "0"
        perfectNotesValue?.text = "0"
        missedNotesValue?.text = "0"
        comboValue?.text = "1x"
        gradeValue?.text = "F"

        streakValue?.setTextColor(textPrimaryColor)
        missedNotesValue?.setTextColor(textPrimaryColor)
        comboValue?.setTextColor(textPrimaryColor)
        gradeValue?.setTextColor(textSecondaryColor)
    }

    /**
     * Refresh colors after theme change
     */
    fun refreshColors() {
        loadColors()
        buildViews()
    }
}
