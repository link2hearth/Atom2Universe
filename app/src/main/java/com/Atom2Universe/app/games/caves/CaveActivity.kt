package com.Atom2Universe.app.games.caves

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.util.enableImmersiveMode

class CaveActivity : ThemedActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: CaveRenderer
    private val touch = TouchController()
    private val uiHandler = Handler(Looper.getMainLooper())

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        setContentView(root)

        renderer = CaveRenderer(touch)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 0, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(glView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val hud = layoutInflater.inflate(R.layout.overlay_cave_hud, root, false)
        root.addView(hud)

        val tvCoords  = hud.findViewById<TextView>(R.id.cave_tv_coords)
        val btnBack   = hud.findViewById<View>(R.id.cave_btn_back)
        val btnMode   = hud.findViewById<Button>(R.id.cave_btn_mode)
        val btnUp     = hud.findViewById<Button>(R.id.cave_btn_up)
        val btnDown   = hud.findViewById<Button>(R.id.cave_btn_down)

        btnBack.setOnClickListener { finish() }

        // Toggle mode : SPECTATOR ↔ WALK
        btnMode.setOnClickListener {
            val next = if (renderer.playerMode == PlayerMode.WALK) PlayerMode.SPECTATOR else PlayerMode.WALK
            renderer.pendingMode = next
        }

        // Callback quand le renderer a appliqué le changement de mode
        renderer.modeCallback = { mode ->
            uiHandler.post { applyModeUi(mode, btnMode, btnUp, btnDown) }
        }

        // Bouton haut : fly up en spectateur, saut en marche
        @SuppressLint("ClickableViewAccessibility")
        fun flyOrJump(v: View) {
            v.setOnTouchListener { _, e ->
                touch.flyUp = e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL
                true
            }
        }
        flyOrJump(btnUp)

        btnDown.setOnTouchListener { _, e ->
            touch.flyDown = e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL
            true
        }

        glView.setOnTouchListener { _, event ->
            touch.onTouch(event, glView.width)
            true
        }

        renderer.posCallback = { pos -> uiHandler.post { tvCoords.text = pos } }

        // UI initiale (mode marche par défaut)
        applyModeUi(PlayerMode.WALK, btnMode, btnUp, btnDown)
    }

    private fun applyModeUi(mode: PlayerMode, btnMode: Button, btnUp: Button, btnDown: View) {
        when (mode) {
            PlayerMode.SPECTATOR -> {
                btnMode.text = getString(R.string.cave_mode_spectator)
                btnUp.text   = "▲"
                btnDown.visibility = View.VISIBLE
            }
            PlayerMode.WALK -> {
                btnMode.text = getString(R.string.cave_mode_walk)
                btnUp.text   = getString(R.string.cave_jump)
                btnDown.visibility = View.GONE
            }
        }
    }

    override fun onResume()  { super.onResume();  glView.onResume() }
    override fun onPause()   { super.onPause();   glView.onPause()  }
    override fun onDestroy() { super.onDestroy(); renderer.destroy() }
}
