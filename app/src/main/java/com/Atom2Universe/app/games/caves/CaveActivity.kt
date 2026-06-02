package com.Atom2Universe.app.games.caves

import android.annotation.SuppressLint
import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.caves.input.GamepadController
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.CraftRegistry
import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.util.enableImmersiveMode
import android.widget.ImageView
import com.Atom2Universe.app.games.caves.render.MinimapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaveActivity : ThemedActivity() {

    companion object {
        const val EXTRA_WORLD_ID = "cave_world_id"
        const val ACTIVE_SIZE    = 9
        const val GRID_COLS      = 6
        const val EMPTY_BUFFER   = 36
    }

    internal lateinit var glView: GLSurfaceView
    internal lateinit var renderer: CaveRenderer
    private  val touch   = TouchController()
    private  val gamepad = GamepadController(touch)
    private  val uiHandler = Handler(Looper.getMainLooper())
    private  var worldId: String? = null
    private  var ambientMusic: CaveAmbientMusic? = null

    internal var isCreative = false
    private  var survivalInventory: Map<Short, Int> = emptyMap()
    private  var survivalHotbar: List<Short?> = List(ACTIVE_SIZE) { null }

    private var ptrUp    = -1; private var ptrDown  = -1
    private var ptrLaser = -1; private var ptrPlace = -1
    private var vBtnBack: View? = null
    private var vBtnCamera: Button? = null
    private var vHudControls: View? = null
    private var hudTouchButtonsVisible = true
    private var vBtnUp:    View? = null; private var vBtnDown:  View? = null
    private var vBtnLaser: View? = null; private var vBtnPlace: View? = null

    internal lateinit var invOverlay: View

    internal val hud = CaveHud(this)
    internal val invManager = InventoryManager(this)

    private val minimapRenderer = MinimapRenderer()
    private var minimapView: ImageView? = null
    private var minimapJob: Job? = null

    // ── Textures ──────────────────────────────────────────────────────────────

    internal fun blockBitmap(type: Short): Bitmap? = BlockRegistry.getBitmap(type)

    internal fun blockDrawable(type: Short, cornerDp: Float = 4f): Drawable {
        val dp  = resources.displayMetrics.density
        val bmp = blockBitmap(type)
        return if (bmp != null) {
            RoundedBitmapDrawableFactory.create(resources, bmp).apply {
                cornerRadius = cornerDp * dp
                isFilterBitmap = false
            }
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(BlockRegistry.getColor(type))
                cornerRadius = cornerDp * dp
            }
        }
    }

    internal fun blockName(type: Short): String {
        val name = BlockRegistry.get(type)?.name ?: return "?"
        val resId = resources.getIdentifier("cave_block_$name", "string", packageName).takeIf { it != 0 }
            ?: resources.getIdentifier("cave_item_$name", "string", packageName).takeIf { it != 0 }
            ?: return name
        return getString(resId)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlockRegistry.load(assets)
        BiomeRegistry.load(assets)
        CraftRegistry.load(assets)
        enableImmersiveMode()
        forceImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        worldId = intent.getStringExtra(EXTRA_WORLD_ID)
        val save = worldId?.let { CaveWorldSaveManager.loadWorld(this, it) }
        isCreative = save?.isCreative ?: false
        if (isCreative) {
            survivalInventory = save?.inventory ?: emptyMap()
            survivalHotbar    = save?.hotbar    ?: List(ACTIVE_SIZE) { null }
        }
        val savedState = when {
            save != null && save.isCreative -> CaveRenderer.SavedState(
                x = save.playerX, y = save.playerY, z = save.playerZ,
                yaw = save.playerYaw, pitch = save.playerPitch,
                inventory = BlockRegistry.creativeList().associateWith { 1 },
                hotbar = BlockRegistry.creativeList().let { keys -> List(ACTIVE_SIZE) { i -> keys.getOrNull(i) } },
                playerHp            = save.playerHp,
                playerLevel         = save.playerLevel,
                playerXp            = save.playerXp,
                playerDamage        = save.playerDamage,
                playerFireRate      = save.playerFireRate,
                playerMaxHp         = save.playerMaxHp,
                playerShield        = save.playerShield,
                playerShieldCurrent = save.playerShieldCurrent,
                playerWeapons       = save.playerWeapons,
                wardStonePositions  = save.wardStonePositions
            )
            save != null && save.playerY != 0.0 -> CaveRenderer.SavedState(
                x = save.playerX, y = save.playerY, z = save.playerZ,
                yaw = save.playerYaw, pitch = save.playerPitch,
                inventory = save.inventory, hotbar = save.hotbar,
                playerHp            = save.playerHp,
                playerLevel         = save.playerLevel,
                playerXp            = save.playerXp,
                playerDamage        = save.playerDamage,
                playerFireRate      = save.playerFireRate,
                playerMaxHp         = save.playerMaxHp,
                playerShield        = save.playerShield,
                playerShieldCurrent = save.playerShieldCurrent,
                playerWeapons       = save.playerWeapons,
                wardStonePositions  = save.wardStonePositions
            )
            else -> null
        }

        renderer = CaveRenderer(
            context = this, touch = touch,
            worldSeed = save?.seed ?: System.currentTimeMillis(),
            worldId = worldId, savedState = savedState
        )
        renderer.isCreative = isCreative
        renderer.enemyManager.isCreative = isCreative
        if (isCreative) renderer.pendingMode = PlayerMode.SPECTATOR

        val root = FrameLayout(this)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            view.setPadding(0, 0, 0, 0); WindowInsetsCompat.CONSUMED
        }

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 0, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(glView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val hudView = layoutInflater.inflate(R.layout.overlay_cave_hud, root, false)
        root.addView(hudView)

        val tvCoords      = hudView.findViewById<android.widget.TextView>(R.id.cave_tv_coords)
        val tvFps         = hudView.findViewById<android.widget.TextView>(R.id.cave_tv_fps)
        val btnBack       = hudView.findViewById<View>(R.id.cave_btn_back).also { vBtnBack = it }
        val btnMode       = hudView.findViewById<Button>(R.id.cave_btn_mode)
        val btnDayNight   = hudView.findViewById<Button>(R.id.cave_btn_day_night)
        val btnCamera     = hudView.findViewById<Button>(R.id.cave_btn_camera).also { vBtnCamera = it }
        minimapView       = hudView.findViewById(R.id.cave_minimap)
        vHudControls      = hudView.findViewById(R.id.cave_hud_controls)
        val btnUp         = hudView.findViewById<Button>(R.id.cave_btn_up).also    { vBtnUp    = it }
        val btnDown       = hudView.findViewById<Button>(R.id.cave_btn_down).also  { vBtnDown  = it }
        val btnLaser      = hudView.findViewById<Button>(R.id.cave_btn_laser).also { vBtnLaser = it }
        val btnPlace      = hudView.findViewById<Button>(R.id.cave_btn_place).also { vBtnPlace = it }

        makeCircular(btnUp,    0x55FFFFFF.toInt())
        makeCircular(btnDown,  0x55FFFFFF.toInt())
        makeCircular(btnLaser, 0x66003366.toInt())
        makeCircular(btnPlace, 0x66336600.toInt())
        applyButtonPositions(hudView.findViewById(R.id.cave_game_area))

        val miningPanel   = hudView.findViewById<LinearLayout>(R.id.cave_mining_panel)
        val tvMiningBlock = hudView.findViewById<android.widget.TextView>(R.id.cave_tv_mining_block)
        val miningBar     = hudView.findViewById<android.widget.ProgressBar>(R.id.cave_mining_progress)
        val hotbarLayout  = hudView.findViewById<LinearLayout>(R.id.cave_hotbar)

        hud.buildHotbarUI(hotbarLayout)
        btnBack.setOnClickListener { showQuitConfirmation() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { showQuitConfirmation() }
        })
        btnMode.setOnClickListener {
            renderer.pendingMode = if (renderer.playerMode == PlayerMode.WALK) PlayerMode.SPECTATOR else PlayerMode.WALK
        }
        btnDayNight.setOnClickListener {
            renderer.toggleDayNight()
            btnDayNight.text = if (renderer.dayNightInverted) "☀" else "🌙"
        }
        btnCamera.setOnClickListener {
            glView.queueEvent { renderer.camera.thirdPerson = !renderer.camera.thirdPerson }
            btnCamera.alpha = if (renderer.camera.thirdPerson) 1.0f else 0.5f
        }
        btnCamera.alpha = 0.5f

        val btnMap = hudView.findViewById<Button>(R.id.cave_btn_map)
        btnMap.alpha = 0.5f
        btnMap.setOnClickListener {
            val mapView = minimapView ?: return@setOnClickListener
            if (mapView.visibility == View.GONE) {
                mapView.visibility = View.VISIBLE
                btnMap.alpha = 1.0f
                startMinimapLoop()
            } else {
                mapView.visibility = View.GONE
                btnMap.alpha = 0.5f
                minimapJob?.cancel()
                minimapJob = null
            }
        }
        renderer.modeCallback    = { mode -> uiHandler.post { applyModeUi(mode, btnMode, btnUp as Button, btnDown, btnLaser, btnPlace) } }
        renderer.posCallback     = { pos  -> uiHandler.post { tvCoords.text = pos } }
        renderer.fpsCallback     = { fps  -> uiHandler.post { tvFps.text = "$fps FPS" } }
        renderer.miningCallback  = { progress, blockType ->
            uiHandler.post {
                if (blockType != null && progress > 0f) {
                    miningPanel.visibility = View.VISIBLE
                    tvMiningBlock.text = blockName(blockType)
                    miningBar.progress = (progress * 100).toInt()
                } else { miningPanel.visibility = View.GONE; miningBar.progress = 0 }
            }
        }
        renderer.inventoryCallback = { inv -> uiHandler.post { invManager.onInventoryChanged(inv); saveWorldAsync() } }
        renderer.hotbarCallback    = { slots, selected -> uiHandler.post { hud.updateHotbarUI(slots, selected) } }

        hud.buildHealthBar(root)
        hud.buildXpBar(root)

        renderer.playerHpCallback = { hp, maxHp    -> uiHandler.post { hud.updateHealthBar(hp, maxHp) } }
        renderer.shieldCallback   = { cur, max     -> uiHandler.post { hud.updateShieldBar(cur, max) } }
        renderer.xpCallback       = { xp, xpMax, l -> uiHandler.post { hud.updateXpBar(xp, xpMax, l) } }
        renderer.levelUpCallback  = { options       -> uiHandler.post { hud.showLevelUpDialog(options, root) } }

        invOverlay = layoutInflater.inflate(R.layout.overlay_cave_inventory, root, false)
        root.addView(invOverlay)
        invManager.setupOverlay(invOverlay)

        // ── Outil de capture de structure (mode créatif) ───────────────────────
        if (isCreative) {
            hud.buildStructurePanel(root)
            // Bouton 📐 dans la barre de contrôles
            val btnStruct = Button(this).apply {
                text = "📐"; textSize = 14f; setBackgroundColor(0x55FFFFFF.toInt()); setTextColor(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams((40 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()).also {
                    it.gravity = Gravity.TOP or Gravity.END
                    it.setMargins(0, (8 * resources.displayMetrics.density).toInt(),
                        (8 * resources.displayMetrics.density).toInt(), 0)
                }
                alpha = 0.6f
                var panelShown = false
                setOnClickListener {
                    panelShown = !panelShown
                    alpha = if (panelShown) 1.0f else 0.6f
                    hud.showStructurePanel(panelShown)
                }
            }
            root.addView(btnStruct)
            lifecycleScope.launch(Dispatchers.IO) {
                StructureRegistry.loadUserStructures()
            }
        }

        applyModeUi(if (isCreative) PlayerMode.SPECTATOR else PlayerMode.WALK, btnMode, btnUp as Button, btnDown, btnLaser, btnPlace)
        ambientMusic = CaveAmbientMusic(this, lifecycleScope)
    }

    private fun startMinimapLoop() {
        minimapJob?.cancel()
        minimapJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                val world  = renderer.world
                val camera = renderer.camera
                val bmp = minimapRenderer.render(world, camera.playerX, camera.playerY, camera.playerZ, camera.yaw)
                val label = if (minimapRenderer.isCaveMode) getString(R.string.cave_map_cave)
                            else getString(R.string.cave_map_surface)
                withContext(Dispatchers.Main) {
                    minimapView?.setImageBitmap(bmp)
                    minimapView?.contentDescription = label
                }
                delay(500L)
            }
        }
    }

    override fun onResume()  { super.onResume();  glView.onResume(); ambientMusic?.resume(); forceImmersiveMode() }
    override fun onPause()   { super.onPause();   glView.onPause();  ambientMusic?.pause(); saveWorld(); minimapJob?.cancel() }
    override fun onDestroy() { super.onDestroy(); renderer.destroy(); ambientMusic?.destroy() }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun buildSaveSnap(): CaveWorldSave? {
        val id    = worldId ?: return null
        val stats = renderer.playerStats
        return CaveWorldSave(
            id = id, name = "", seed = 0L, createdAt = 0L,
            lastPlayedAt = System.currentTimeMillis(),
            playerX = renderer.camera.playerX, playerY = renderer.camera.playerY, playerZ = renderer.camera.playerZ,
            playerYaw = renderer.camera.yaw, playerPitch = renderer.camera.pitch,
            inventory = if (isCreative) survivalInventory else renderer.inventory.toMap(),
            hotbar    = if (isCreative) survivalHotbar    else renderer.hotbar.map { it },
            isCreative          = isCreative,
            playerHp            = renderer.playerNode.hp,
            playerLevel         = stats.level,
            playerXp            = stats.xp,
            playerDamage        = stats.damage,
            playerFireRate      = stats.fireRate,
            playerMaxHp         = stats.maxHp,
            playerShield        = stats.shield,
            playerShieldCurrent = renderer.playerNode.shield,
            playerWeapons       = stats.weapons.map { "${it.color.name}_${it.variant.name}" },
            wardStonePositions  = renderer.enemyManager.wardStoneZones.toList()
        )
    }

    fun saveWorldAsync() {
        val snap = buildSaveSnap() ?: return
        lifecycleScope.launch(Dispatchers.IO) { CaveWorldSaveManager.updateFields(this@CaveActivity, snap) }
    }

    private suspend fun saveWorldNow() {
        val snap = buildSaveSnap() ?: return
        withContext(Dispatchers.IO) { CaveWorldSaveManager.updateFields(this@CaveActivity, snap) }
    }

    private fun saveWorld() = saveWorldAsync()

    // ── Confirmation quitter ──────────────────────────────────────────────────

    private fun showQuitConfirmation() {
        val dp = resources.displayMetrics.density
        val dialogRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; setColor(0xFF111122.toInt())
                cornerRadius = 12 * dp; setStroke((1 * dp).toInt(), 0x55FFFFFF.toInt())
            }
            val p = (20 * dp).toInt()
            setPadding(p, p, p, (10 * dp).toInt())
        }
        dialogRoot.addView(android.widget.TextView(this).apply {
            text = getString(R.string.cave_quit_title); setTextColor(Color.WHITE)
            textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = (8 * dp).toInt() }
        })
        dialogRoot.addView(android.widget.TextView(this).apply {
            text = getString(R.string.cave_quit_message); setTextColor(0xCCFFFFFF.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = (18 * dp).toInt() }
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogRoot.addView(btnRow)
        val dialog = AlertDialog.Builder(this).setView(dialogRoot).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnRow.addView(Button(this).apply {
            text = getString(R.string.cave_quit_cancel); setTextColor(0xAAFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT); setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(Button(this).apply {
            text = getString(R.string.cave_quit_confirm); setTextColor(0xFFFF5555.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dialog.dismiss(); lifecycleScope.launch { saveWorldNow(); finish() } }
        })
        dialog.show()
    }

    // ── Boutons action ────────────────────────────────────────────────────────

    private fun makeCircular(btn: Button, bgColor: Int) {
        btn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bgColor) }
    }

    private fun applyButtonPositions(gameArea: FrameLayout) {
        gameArea.doOnLayout {
            val w = gameArea.width.toFloat(); val h = gameArea.height.toFloat()
            applyBtnLayout(vBtnUp,    CaveControlsPrefs.Btn.UP,    w, h)
            applyBtnLayout(vBtnDown,  CaveControlsPrefs.Btn.DOWN,  w, h)
            applyBtnLayout(vBtnLaser, CaveControlsPrefs.Btn.LASER, w, h)
            applyBtnLayout(vBtnPlace, CaveControlsPrefs.Btn.PLACE, w, h)
        }
    }

    private fun applyBtnLayout(view: View?, btn: CaveControlsPrefs.Btn, parentW: Float, parentH: Float) {
        view ?: return
        val dp = resources.displayMetrics.density
        val sizePx = (CaveControlsPrefs.sizeDp(this, btn) * dp).toInt()
        view.layoutParams = view.layoutParams.also { it.width = sizePx; it.height = sizePx }
        view.x = CaveControlsPrefs.xf(this, btn) * parentW - sizePx / 2f
        view.y = CaveControlsPrefs.yf(this, btn) * parentH - sizePx / 2f
    }

    // ── Mode UI ───────────────────────────────────────────────────────────────

    private fun applyModeUi(mode: PlayerMode, btnMode: Button, btnUp: Button, btnDown: View, btnLaser: View, btnPlace: View) {
        btnMode.visibility = if (isCreative) View.VISIBLE else View.GONE
        when (mode) {
            PlayerMode.SPECTATOR -> { btnMode.text = getString(R.string.cave_mode_spectator); btnUp.text = "▲"; btnDown.visibility = View.VISIBLE; btnLaser.visibility = View.GONE; btnPlace.visibility = View.GONE }
            PlayerMode.WALK      -> { btnMode.text = getString(R.string.cave_mode_walk); btnUp.text = getString(R.string.cave_jump); btnDown.visibility = View.GONE; btnLaser.visibility = View.VISIBLE; btnPlace.visibility = View.VISIBLE }
        }
    }

    // ── Mode immersif ─────────────────────────────────────────────────────────

    private fun forceImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) forceImmersiveMode()
    }

    // ── Visibilité boutons HUD ────────────────────────────────────────────────

    private fun setHudButtonsVisible(visible: Boolean) {
        if (hudTouchButtonsVisible == visible) return
        hudTouchButtonsVisible = visible
        val a = if (visible) 1f else 0f
        listOf(vBtnBack, vBtnUp, vBtnDown, vBtnLaser, vBtnPlace).forEach { v ->
            v?.alpha = a; v?.isEnabled = visible
        }
        vHudControls?.alpha = a
        (vHudControls as? ViewGroup)?.let { g ->
            for (i in 0 until g.childCount) { g.getChildAt(i).alpha = a; g.getChildAt(i).isEnabled = visible }
        }
    }

    // ── Input manette ─────────────────────────────────────────────────────────

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE)
            return invManager.handleInvGamepadMotion(event)
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
            setHudButtonsVisible(false)
        return gamepad.onGenericMotion(event) || super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) {
            if (event.action == KeyEvent.ACTION_DOWN && invManager.handleInvGamepadKey(event.keyCode)) return true
            if (event.action == KeyEvent.ACTION_UP) return true
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (KeyEvent.isGamepadButton(event.keyCode)) setHudButtonsVisible(false)
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_Y  -> { invManager.openInventory(); return true }
                KeyEvent.KEYCODE_BUTTON_L1 -> { glView.queueEvent { renderer.selectSlot((renderer.selectedSlot - 1 + 19) % 19) }; return true }
                KeyEvent.KEYCODE_BUTTON_R1 -> { glView.queueEvent { renderer.selectSlot((renderer.selectedSlot + 1) % 19) }; return true }
                KeyEvent.KEYCODE_BUTTON_X  -> {
                    val newTps = !renderer.camera.thirdPerson
                    glView.queueEvent { renderer.camera.thirdPerson = newTps }
                    vBtnCamera?.alpha = if (newTps) 1.0f else 0.5f
                    return true
                }
            }
        }
        val consumed = when (event.action) {
            KeyEvent.ACTION_DOWN -> gamepad.onKeyDown(event.keyCode)
            KeyEvent.ACTION_UP   -> gamepad.onKeyUp(event.keyCode)
            else -> false
        }
        return consumed || super.dispatchKeyEvent(event)
    }

    // ── Touch multipoint ─────────────────────────────────────────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) setHudButtonsVisible(true)
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) return super.dispatchTouchEvent(ev)
        if (hud.levelUpOverlay != null) return super.dispatchTouchEvent(ev)
        val action = ev.actionMasked; val idx = ev.actionIndex; val pid = ev.getPointerId(idx)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = ev.getX(idx); val y = ev.getY(idx)
                fun hit(v: View?) = v != null && v.visibility == View.VISIBLE && v.isHitOnScreen(x, y)
                if (ptrUp    == -1 && hit(vBtnUp))    { ptrUp    = pid; touch.flyUp       = true }
                if (ptrDown  == -1 && hit(vBtnDown))  { ptrDown  = pid; touch.flyDown     = true }
                if (ptrLaser == -1 && hit(vBtnLaser)) { ptrLaser = pid; touch.laserActive = true }
                if (ptrPlace == -1 && hit(vBtnPlace)) { ptrPlace = pid; touch.placeRequested = true }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val cancel = action == MotionEvent.ACTION_CANCEL
                if (pid == ptrUp    || cancel) { ptrUp    = -1; touch.flyUp       = false }
                if (pid == ptrDown  || cancel) { ptrDown  = -1; touch.flyDown     = false }
                if (pid == ptrLaser || cancel) { ptrLaser = -1; touch.laserActive = false }
                if (pid == ptrPlace || cancel) { ptrPlace = -1 }
            }
        }
        touch.onTouch(ev, glView.width); return super.dispatchTouchEvent(ev)
    }

    private fun View.isHitOnScreen(x: Float, y: Float): Boolean {
        val loc = IntArray(2); getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }
}
