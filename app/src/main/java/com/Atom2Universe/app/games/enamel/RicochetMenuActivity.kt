package com.Atom2Universe.app.games.enamel

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.enableImmersiveMode

/** Accueil de Ricochet : une planche jouée par un robot, avec les deux façons de jouer posées dessus. */
class RicochetMenuActivity : ThemedActivity() {
    private lateinit var demo: EnamelView
    private lateinit var classic: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        val dp = resources.displayMetrics.density
        fun px(n: Int) = (n * dp).toInt()
        val colors = SciencePalette(this)
        val wide = resources.configuration.smallestScreenWidthDp >= 600
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(colors.background) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = px(56) }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back); setColorFilter(colors.text); background = null
            contentDescription = getString(R.string.back); setPadding(px(12), px(12), px(12), px(12))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(px(48), px(48)))
        header.addView(TextView(this).apply {
            setText(R.string.enamel_title); textSize = if (wide) 26f else 23f; setTextColor(colors.text); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val stage = FrameLayout(this)
        demo = EnamelView(this).apply { this.demo = true; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        stage.addView(demo, FrameLayout.LayoutParams(-1, -1))
        fun choice(label: Int, open: () -> Unit) = Button(this).apply {
            setText(label); isAllCaps = false; textSize = if (wide) 22f else 18f; typeface = Typeface.DEFAULT_BOLD
            backgroundTintList = ColorStateList.valueOf(colors.accent); setTextColor(colors.onAccent)
            minimumWidth = 0; minimumHeight = px(if (wide) 64 else 52)
            setPadding(px(26), px(10), px(26), px(10)); elevation = 6 * dp
            setOnClickListener { open() }
        }
        val choices = LinearLayout(this).apply { gravity = Gravity.CENTER }
        classic = choice(R.string.ricochet_new_game) { startActivity(Intent(this, EnamelActivity::class.java)) }
        choices.addView(classic, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(16) })
        choices.addView(choice(R.string.ricochet_challenge) { startActivity(Intent(this, RicochetPuzzleActivity::class.java)) },
            LinearLayout.LayoutParams(-2, -2))
        // Au milieu de la planche : la base, où le robot se place et tire, reste visible en bas.
        stage.addView(choices, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Une partie classique en cours reprend là où on l'a laissée.
        val saved = getSharedPreferences(EnamelActivity.PREFS, MODE_PRIVATE).getString("run_state", null) != null
        classic.setText(if (saved) R.string.ricochet_continue else R.string.ricochet_new_game)
        demo.resume()
    }

    override fun onPause() { demo.pause(); super.onPause() }
}
