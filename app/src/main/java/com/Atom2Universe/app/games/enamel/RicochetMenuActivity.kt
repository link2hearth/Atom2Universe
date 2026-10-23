package com.Atom2Universe.app.games.enamel

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.enableImmersiveMode

/** Deux façons de jouer à Ricochet, accessibles depuis sa propre tuile. */
class RicochetMenuActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        val dp = resources.displayMetrics.density
        val colors = SciencePalette(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = AppearanceStyle.screen(this@RicochetMenuActivity)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back); setColorFilter(colors.text); background = null
            contentDescription = getString(R.string.back); setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((52 * dp).toInt(), (52 * dp).toInt()))
        header.addView(TextView(this).apply {
            setText(R.string.enamel_title); textSize = 24f; setTextColor(colors.text); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        root.addView(RicochetMenuView(this,
            onClassic = { startActivity(Intent(this, EnamelActivity::class.java)) },
            onPuzzle = { startActivity(Intent(this, RicochetPuzzleActivity::class.java)) }
        ), LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
