package com.Atom2Universe.app.games.bigger

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class BiggerActivity : ThemedActivity() {

    private lateinit var gameView: BiggerView
    private lateinit var tvGoal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bigger)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.bigger_view)
        tvGoal   = findViewById(R.id.bigger_tv_goal)
        tvGoal.text = getString(R.string.bigger_goal, MAX_VALUE)

        findViewById<ImageButton>(R.id.bigger_btn_back).setOnClickListener {
            @Suppress("DEPRECATION") onBackPressed()
        }
        findViewById<ImageButton>(R.id.bigger_btn_restart).setOnClickListener {
            synchronized(gameView.game) { gameView.game.reset() }
        }
    }

    override fun onResume() { super.onResume(); gameView.resume() }
    override fun onPause()  { super.onPause();  gameView.pause()  }
}
