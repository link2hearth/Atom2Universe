package com.Atom2Universe.app.games.hotpotato

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class HotPotatoActivity : ThemedActivity() {

    private lateinit var gameView: HotPotatoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hot_potato)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.hot_potato_view)
        findViewById<ImageButton>(R.id.hot_potato_btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.hot_potato_btn_pause).setOnClickListener { gameView.togglePause() }
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
