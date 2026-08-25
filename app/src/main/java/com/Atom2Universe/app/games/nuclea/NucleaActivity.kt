package com.Atom2Universe.app.games.nuclea

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import kotlin.math.abs
import androidx.activity.addCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

class NucleaActivity : ThemedActivity() {

    private lateinit var gameView: NucleaView
    private lateinit var tvTitle: TextView
    private val sfx = NucleaSoundEngine()
    private val prefs by lazy { getSharedPreferences("nuclea_save", MODE_PRIVATE) }

    private val handler = Handler(Looper.getMainLooper())
    private val titleUpdater = object : Runnable {
        override fun run() {
            val g = gameView.game
            tvTitle.text = when (g.phase) {
                NucleaPhase.PLAYING, NucleaPhase.PAUSED ->
                    getString(R.string.nuclea_wave_label, g.wave) + "   ✦ ${g.runDust}"
                else -> getString(R.string.nuclea_title)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nuclea)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = findViewById(R.id.nuclea_view)
        tvTitle = findViewById(R.id.nuclea_tv_title)

        val game = gameView.game
        game.meta.load(prefs)
        game.loadRun(prefs)          // partie laissée en plan lors de la dernière session
        game.sound = sfx
        game.onMetaChanged = { game.meta.save(prefs) }
        game.onRunEnded = { game.clearSavedRun(prefs) }
        game.onGameOver = {
            // 2 neutrinos par vague terminée
            val reward = NeutrinoRewards.nuclea(game.wavesCleared)
            if (reward > 0) runOnUiThread { NeutrinoRepository(this).addBalance(reward) }
        }

        onBackPressedDispatcher.addCallback(this) {
            when (game.phase) {
                NucleaPhase.MENU -> finish()
                NucleaPhase.PLAYING -> gameView.requestPause()
                else -> gameView.requestMenu()
            }
        }

        findViewById<ImageButton>(R.id.nuclea_btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<ImageButton>(R.id.nuclea_btn_pause).setOnClickListener {
            gameView.requestPause()
        }
    }

    // ─── Manette (type Xbox) ─────────────────────────────────────────────────

    /** Zone morte : ignore les micro-dérives des sticks. */
    private fun deadZone(v: Float) = if (abs(v) < 0.18f) 0f else v

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            val mx = deadZone(event.getAxisValue(MotionEvent.AXIS_X))
            val my = deadZone(event.getAxisValue(MotionEvent.AXIS_Y))
            // Stick droit : Z/RZ sur les manettes Xbox, RX/RY sur certaines autres
            var ax = deadZone(event.getAxisValue(MotionEvent.AXIS_Z))
            var ay = deadZone(event.getAxisValue(MotionEvent.AXIS_RZ))
            if (ax == 0f && ay == 0f) {
                ax = deadZone(event.getAxisValue(MotionEvent.AXIS_RX))
                ay = deadZone(event.getAxisValue(MotionEvent.AXIS_RY))
            }
            gameView.onPadSticks(mx, my, ax, ay)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isGamepad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        if (isGamepad && event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> { gameView.onPadConfirm(isStart = false); return true }
                KeyEvent.KEYCODE_BUTTON_START -> { gameView.onPadConfirm(isStart = true); return true }
                KeyEvent.KEYCODE_BUTTON_Y -> { gameView.onPadY(); return true }
                KeyEvent.KEYCODE_BUTTON_B -> { onBackPressedDispatcher.onBackPressed(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        sfx.start()
        gameView.resume()
        handler.post(titleUpdater)
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        sfx.stop()
        handler.removeCallbacks(titleUpdater)
        // Une partie en cours est mémorisée (avec le record atteint) pour être reprise
        val game = gameView.game
        if (game.runInProgress) game.saveRun(prefs) else game.clearSavedRun(prefs)
        game.meta.save(prefs)
    }
}
