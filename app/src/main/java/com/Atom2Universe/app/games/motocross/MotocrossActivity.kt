package com.Atom2Universe.app.games.motocross

import android.os.Bundle
import android.view.WindowManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class MotocrossActivity : ThemedActivity() {

    private lateinit var gameView: MotocrossView
    private val controls = ArrayList<View>()
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_motocross)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.motocross_view)
        val tvDist  = findViewById<TextView>(R.id.motocross_hud_distance)
        val tvSpeed = findViewById<TextView>(R.id.motocross_hud_speed)
        val tvRun = findViewById<TextView>(R.id.motocross_hud_run)
        val tvTrack = findViewById<TextView>(R.id.motocross_hud_track)

        gameView.onStats = { stats ->
            tvDist.text = getString(R.string.motocross_progress, stats.distance, stats.length)
            tvSpeed.text = getString(R.string.motocross_hud_speed, stats.speed)
            tvRun.text = getString(R.string.motocross_run_summary, stats.seconds / 60, stats.seconds % 60, stats.faults)
            tvTrack.text = getString(R.string.motocross_track_info, stats.seed, stats.checkpoint, stats.checkpointCount)
        }

        val neutrinos = NeutrinoRepository(this)
        gameView.onReward = { reward ->
            if (reward > 0) neutrinos.addBalance(reward)
        }
        gameView.onControlsCleared = { controls.forEach { it.isPressed = false; it.isSelected = false } }

        findViewById<ImageButton>(R.id.motocross_btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.motocross_btn_restart).setOnClickListener { releaseControls(); gameView.resetGame() }
        findViewById<ImageButton>(R.id.motocross_btn_restart).setOnLongClickListener {
            releaseControls(); gameView.restartTrack(); true
        }
        findViewById<ImageButton>(R.id.motocross_btn_new).setOnClickListener { releaseControls(); gameView.newTrack() }
        bindControl(R.id.motocross_lean_back, 0)
        bindControl(R.id.motocross_lean_forward, 1)
        bindControl(R.id.motocross_brake, 2)
        bindControl(R.id.motocross_throttle, 3)
    }

    private fun bindControl(id: Int, control: Int) {
        val button = findViewById<Button>(id)
        controls.add(button)
        var pointerClick = false
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    view.isSelected = false
                    val inside = event.x >= 0f && event.x < view.width && event.y >= 0f && event.y < view.height
                    view.isPressed = inside
                    gameView.setControl(control, inside)
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false; gameView.setControl(control, false)
                    pointerClick = true
                    view.performClick()
                    pointerClick = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false; view.isSelected = false; gameView.setControl(control, false)
                }
            }
            true
        }
        // Avec un lecteur d'écran ou un clavier, clic = maintien / relâchement.
        button.setOnClickListener {
            if (!pointerClick) {
                button.isSelected = !button.isSelected
                gameView.setControl(control, button.isSelected)
            }
        }
    }

    private fun releaseControls() {
        gameView.clearControls()
        controls.forEach { it.isPressed = false; it.isSelected = false }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::gameView.isInitialized) {
            if (hasFocus && resumed) gameView.resume() else { releaseControls(); gameView.pause() }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        gameView.resume()
    }

    override fun onPause() {
        resumed = false
        releaseControls()
        super.onPause()
        gameView.pause()
    }
}
