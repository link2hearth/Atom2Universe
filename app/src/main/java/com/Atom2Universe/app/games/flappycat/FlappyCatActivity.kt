package com.Atom2Universe.app.games.flappycat

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules

class FlappyCatActivity : ThemedActivity() {

    private lateinit var gameView: FlappyCatView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_flappy_cat)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.flappy_cat_view)
        findViewById<ImageButton>(R.id.flappy_cat_btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.flappy_cat_btn_pause).setOnClickListener { gameView.togglePause() }
        findViewById<ImageButton>(R.id.flappy_cat_btn_info).setOnClickListener {
            gameView.pauseForRules()
            showGameRules(R.string.flappy_cat_title, R.string.flappy_cat_rules)
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }
}
