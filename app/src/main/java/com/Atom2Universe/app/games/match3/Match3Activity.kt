package com.Atom2Universe.app.games.match3

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.applySystemBarsVisibility
import com.Atom2Universe.app.util.enableImmersiveMode

class Match3Activity : ThemedActivity() {

    private lateinit var scoreText: TextView
    private lateinit var match3View: Match3View
    private lateinit var timerBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match3)
        enableImmersiveMode()
        applySystemBarsVisibility(showStatusBar = false, showNavBar = false)

        scoreText = findViewById(R.id.match3_score)
        match3View = findViewById(R.id.match3_view)
        timerBar   = findViewById(R.id.match3_timer)
        val btnBack = findViewById<ImageButton>(R.id.match3_btn_back)
        val btnNew  = findViewById<TextView>(R.id.match3_btn_new)

        scoreText.text = getString(R.string.match3_score, 0)

        match3View.onScoreChanged = { score ->
            scoreText.text = getString(R.string.match3_score, score)
        }

        match3View.onTimerChanged = { value, max ->
            val ratio = (value / max).coerceIn(0f, 1f)
            timerBar.progress = (ratio * 1000).toInt()
            val hue = ratio * 120f
            timerBar.progressTintList = ColorStateList.valueOf(
                Color.HSVToColor(floatArrayOf(hue, 0.9f, 1f))
            )
        }

        btnBack.setOnClickListener { finish() }

        btnNew.setOnClickListener {
            match3View.resetGame()
            scoreText.text = getString(R.string.match3_score, 0)
            timerBar.progress = 1000
            timerBar.progressTintList = ColorStateList.valueOf(
                Color.HSVToColor(floatArrayOf(120f, 0.9f, 1f))
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsVisibility(showStatusBar = false, showNavBar = false)
    }
}
