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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.util.enableImmersiveMode

class CaveActivity : ThemedActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: CaveRenderer
    private val touch = TouchController()
    private val uiHandler = Handler(Looper.getMainLooper())

    // Suivi des pointers pour les boutons hold — multitouch-safe
    private var ptrUp    = -1
    private var ptrDown  = -1
    private var ptrLaser = -1
    private var vBtnUp:    View? = null
    private var vBtnDown:  View? = null
    private var vBtnLaser: View? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        setContentView(root)

        renderer = CaveRenderer(this, touch)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 0, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(glView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val hud = layoutInflater.inflate(R.layout.overlay_cave_hud, root, false)
        root.addView(hud)

        val tvCoords      = hud.findViewById<TextView>(R.id.cave_tv_coords)
        val btnBack       = hud.findViewById<View>(R.id.cave_btn_back)
        val btnMode       = hud.findViewById<Button>(R.id.cave_btn_mode)
        val btnUp         = hud.findViewById<Button>(R.id.cave_btn_up).also    { vBtnUp    = it }
        val btnDown       = hud.findViewById<Button>(R.id.cave_btn_down).also  { vBtnDown  = it }
        val btnLaser      = hud.findViewById<Button>(R.id.cave_btn_laser).also { vBtnLaser = it }
        val miningPanel   = hud.findViewById<LinearLayout>(R.id.cave_mining_panel)
        val tvMiningBlock = hud.findViewById<TextView>(R.id.cave_tv_mining_block)
        val miningBar     = hud.findViewById<ProgressBar>(R.id.cave_mining_progress)
        val tvInventory   = hud.findViewById<TextView>(R.id.cave_tv_inventory)

        btnBack.setOnClickListener { finish() }

        btnMode.setOnClickListener {
            val next = if (renderer.playerMode == PlayerMode.WALK) PlayerMode.SPECTATOR else PlayerMode.WALK
            renderer.pendingMode = next
        }

        renderer.modeCallback = { mode ->
            uiHandler.post { applyModeUi(mode, btnMode, btnUp, btnDown, btnLaser) }
        }

        // Les boutons hold sont gérés dans dispatchTouchEvent (bounds-checking multitouch).

        renderer.posCallback = { pos ->
            uiHandler.post { tvCoords.text = pos }
        }

        renderer.miningCallback = { progress, blockType ->
            uiHandler.post {
                if (blockType != null && progress > 0f) {
                    miningPanel.visibility = View.VISIBLE
                    tvMiningBlock.text = blockName(blockType)
                    miningBar.progress = (progress * 100).toInt()
                } else {
                    miningPanel.visibility = View.GONE
                    miningBar.progress = 0
                }
            }
        }

        renderer.inventoryCallback = { inv ->
            uiHandler.post {
                tvInventory.text = inv.entries
                    .filter { it.value > 0 }
                    .joinToString("  ") { (type, count) -> "${blockName(type)} ×$count" }
            }
        }

        applyModeUi(PlayerMode.WALK, btnMode, btnUp, btnDown, btnLaser)
    }

    private fun blockName(type: Byte): String = when (type) {
        STONE   -> getString(R.string.cave_block_stone)
        GRANITE -> getString(R.string.cave_block_granite)
        QUARTZ  -> getString(R.string.cave_block_quartz)
        COAL    -> getString(R.string.cave_block_coal)
        GOLD    -> getString(R.string.cave_block_gold)
        CRYSTAL -> getString(R.string.cave_block_crystal)
        DIRT    -> getString(R.string.cave_block_dirt)
        GRAVEL  -> getString(R.string.cave_block_gravel)
        IRON    -> getString(R.string.cave_block_iron)
        SILVER  -> getString(R.string.cave_block_silver)
        RUBY    -> getString(R.string.cave_block_ruby)
        LAVA    -> getString(R.string.cave_block_lava)
        FURNACE -> getString(R.string.cave_block_furnace)
        EMERALD -> getString(R.string.cave_block_emerald)
        COPPER  -> getString(R.string.cave_block_copper)
        else    -> "?"
    }

    private fun applyModeUi(mode: PlayerMode, btnMode: Button, btnUp: Button, btnDown: View, btnLaser: View) {
        when (mode) {
            PlayerMode.SPECTATOR -> {
                btnMode.text = getString(R.string.cave_mode_spectator)
                btnUp.text   = "▲"
                btnDown.visibility  = View.VISIBLE
                btnLaser.visibility = View.GONE
            }
            PlayerMode.WALK -> {
                btnMode.text = getString(R.string.cave_mode_walk)
                btnUp.text   = getString(R.string.cave_jump)
                btnDown.visibility  = View.GONE
                btnLaser.visibility = View.VISIBLE
            }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        val action = ev.actionMasked
        val idx    = ev.actionIndex
        val pid    = ev.getPointerId(idx)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = ev.getX(idx); val y = ev.getY(idx)
                fun hit(v: View?) = v != null && v.visibility == android.view.View.VISIBLE &&
                    v.isHitOnScreen(x, y)
                if (ptrUp    == -1 && hit(vBtnUp))    { ptrUp    = pid; touch.flyUp       = true }
                if (ptrDown  == -1 && hit(vBtnDown))  { ptrDown  = pid; touch.flyDown     = true }
                if (ptrLaser == -1 && hit(vBtnLaser)) { ptrLaser = pid; touch.laserActive = true }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pid == ptrUp    || action == MotionEvent.ACTION_CANCEL) { ptrUp    = -1; touch.flyUp       = false }
                if (pid == ptrDown  || action == MotionEvent.ACTION_CANCEL) { ptrDown  = -1; touch.flyDown     = false }
                if (pid == ptrLaser || action == MotionEvent.ACTION_CANCEL) { ptrLaser = -1; touch.laserActive = false }
            }
        }

        touch.onTouch(ev, glView.width)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume()  { super.onResume();  glView.onResume() }
    override fun onPause()   { super.onPause();   glView.onPause()  }
    override fun onDestroy() { super.onDestroy(); renderer.destroy() }

    private fun View.isHitOnScreen(x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }
}
