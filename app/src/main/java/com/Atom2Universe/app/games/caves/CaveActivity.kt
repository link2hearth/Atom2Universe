package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.mode.AssaultMode
import com.Atom2Universe.app.games.caves.mode.SurvivalMode
import com.Atom2Universe.app.games.caves.world.A2MapStorage
import com.Atom2Universe.app.games.caves.world.MapSource

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.widget.Toast
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
import kotlin.math.hypot
import kotlin.math.max

class CaveActivity : ThemedActivity() {

    companion object {
        const val EXTRA_WORLD_ID = "cave_world_id"
        /** Chemin d'une carte Assaut (voir A2MapStorage) : lance le mode Assaut au lieu d'un monde. */
        const val EXTRA_MAP_PATH = "cave_map_path"
        const val ACTIVE_SIZE    = 9
        const val GRID_COLS      = 6
        const val EMPTY_BUFFER   = 36
    }

    internal lateinit var glView: GLSurfaceView
    internal lateinit var renderer: CaveRenderer
    private  val touch   = TouchController()
    private var vQuickbar: View? = null
    private var assaultWeaponDialog: AlertDialog? = null
    private var assaultWeaponPending = false
    private val uiTouchIds = mutableSetOf<Int>()
    internal fun releaseGameInputs() {
        touch.reset(); ptrUp = -1; ptrDown = -1; ptrLaser = -1; ptrPlace = -1; uiTouchIds.clear()
        tapCandidates.clear()
    }
    private  val gamepad = GamepadController(touch)
    private  val uiHandler = Handler(Looper.getMainLooper())
    private  var worldId: String? = null
    private var soundEngine: CaveSoundEngine? = null
    private val music by lazy { CaveProceduralMusic(lifecycleScope) }

    internal var isCreative = false
    /** Partie du mode Assaut : carte préparée, ni construction, ni destruction, ni sauvegarde. */
    internal var isAssault = false
    private  var survivalInventory: Map<Short, Int> = emptyMap()
    private  var survivalHotbar: List<Short?> = List(ACTIVE_SIZE) { null }

    private var ptrUp    = -1; private var ptrDown  = -1
    private var ptrLaser = -1; private var ptrPlace = -1
    private val tapCandidates = HashMap<Int, TapCandidate>()
    private var vBtnBack: View? = null
    private var vBtnCamera: Button? = null
    private var vHudControls: View? = null
    private var hudTouchButtonsVisible = true
    private var vBtnUp:    View? = null; private var vBtnDown:  View? = null
    private var vBtnRun: View? = null
    private var crouchingUi = false
    private var walkingUi = true
    private var vBtnLaser: View? = null; private var vBtnPlace: View? = null
    private var vGameArea: FrameLayout? = null

    internal lateinit var invOverlay: View

    internal val hud = CaveHud(this)
    internal val invManager = InventoryManager(this)

    private val minimapRenderer = MinimapRenderer()
    private var minimapView: ImageView? = null
    private var minimapJob: Job? = null

    private data class TapCandidate(val x: Float, val y: Float, val downMs: Long)

    // ── Textures ──────────────────────────────────────────────────────────────

    internal fun blockBitmap(type: Short): Bitmap? = BlockRegistry.getBitmap(type)

    internal fun blockDrawable(type: Short, cornerDp: Float = 4f): Drawable {
        val dp = resources.displayMetrics.density
        if (com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(type)) {
            val instance = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(type)
            val def = instance?.let { com.Atom2Universe.app.games.caves.node.ItemRegistry.get(it.defId) }
            return com.Atom2Universe.app.games.caves.render.WeaponIconDrawable(assets,
                def?.weaponType ?: def?.sprite ?: "gun",
                instance?.rarity ?: com.Atom2Universe.app.games.caves.node.ItemRarity.COMMON)
        }
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

    internal fun weaponName(id: String): String {
        val res=resources.getIdentifier("cave_weapon_$id","string",packageName)
        return if(res!=0) getString(res) else id.replace('_',' ')
    }

    internal fun blockName(type: Short): String {
        com.Atom2Universe.app.games.caves.node.FarmItems.seedCrop(type)?.let { crop ->
            return getString(R.string.cave_farm_seed_name, getString(com.Atom2Universe.app.games.caves.node.FarmItems.crops[crop].label))
        }
        com.Atom2Universe.app.games.caves.node.FarmItems.produceCrop(type)?.let { crop ->
            return getString(com.Atom2Universe.app.games.caves.node.FarmItems.crops[crop].label)
        }
        com.Atom2Universe.app.games.caves.node.FarmShowcasePlants.sample(type)?.let { (crop, stage) ->
            return getString(R.string.cave_showcase_garden_sample,
                getString(com.Atom2Universe.app.games.caves.node.FarmShowcasePlants.crops[crop].label),
                resources.getStringArray(R.array.cave_showcase_garden_stages)[stage])
        }
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
        enableImmersiveMode()
        forceImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this)
        val loadingCover = createLoadingCover()
        root.addView(loadingCover, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        val mapPath = intent.getStringExtra(EXTRA_MAP_PATH)
        lifecycleScope.launch {
            val mapSource = withContext(Dispatchers.IO) {
                BlockRegistry.load(assets)
                BiomeRegistry.load(assets)
                CraftRegistry.load(assets)
                mapPath?.let { path ->
                    runCatching {
                        MapSource(A2MapStorage.load(this@CaveActivity, path),
                            isShowcase = path == A2MapStorage.SHOWCASE_PATH)
                    }.getOrNull()
                }
            }
            if (isFinishing || isDestroyed) return@launch
            if (mapPath != null && mapSource == null) {
                android.widget.Toast.makeText(this@CaveActivity, R.string.cave_assault_map_load_failed,
                    android.widget.Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            initializeGame(mapSource, root, loadingCover)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                resumeGame()
            } else {
                glView.onPause()
                music.pause()
                soundEngine?.pause()
            }
        }
    }

    private fun createLoadingCover() = android.widget.TextView(this).apply {
        setText(R.string.cave_loading_terrain)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(12, 15, 20))
        textSize = 20f
        isClickable = true
        isFocusable = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeGame(mapSource: MapSource?, root: FrameLayout, loadingCover: View) {
        isAssault = mapSource != null
        worldId = if (isAssault) null else intent.getStringExtra(EXTRA_WORLD_ID)
        val save = worldId?.let { CaveWorldSaveManager.loadWorld(this, it) }
        isCreative = save?.isCreative ?: false
        if (isCreative) {
            survivalInventory = save?.inventory ?: emptyMap()
            survivalHotbar    = save?.hotbar    ?: List(ACTIVE_SIZE) { null }
        }
        // Restaurer les instances d'armes AVANT de créer le renderer
        com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.clear()
        save?.weaponInstances?.forEach { (id, inst) ->
            com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.restore(id, inst)
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
                wardStonePositions  = save.wardStonePositions,
                recoverableAmmo = save.recoverableAmmo,
                passiveAnimals = save.passiveAnimals,
                gardenHotbar = save.gardenHotbar, farming = save.farming,
                skillAthleticsXp    = save.skillAthleticsXp,
                skillSpeedXp        = save.skillSpeedXp,
                skillEnduranceXp    = save.skillEnduranceXp,
                skillAcrobaticsXp   = save.skillAcrobaticsXp
            )
            save != null && save.playerY != 0.0 -> CaveRenderer.SavedState(
                x = save.playerX, y = save.playerY, z = save.playerZ,
                yaw = save.playerYaw, pitch = save.playerPitch,
                inventory = save.inventory, hotbar = save.hotbar, buildHotbar = save.buildHotbar,
                playerHp            = save.playerHp,
                playerLevel         = save.playerLevel,
                playerXp            = save.playerXp,
                playerDamage        = save.playerDamage,
                playerFireRate      = save.playerFireRate,
                playerMaxHp         = save.playerMaxHp,
                playerShield        = save.playerShield,
                playerShieldCurrent = save.playerShieldCurrent,
                playerWeapons       = save.playerWeapons,
                wardStonePositions  = save.wardStonePositions,
                recoverableAmmo = save.recoverableAmmo,
                passiveAnimals = save.passiveAnimals,
                gardenHotbar = save.gardenHotbar, farming = save.farming,
                skillAthleticsXp    = save.skillAthleticsXp,
                skillSpeedXp        = save.skillSpeedXp,
                skillEnduranceXp    = save.skillEnduranceXp,
                skillAcrobaticsXp   = save.skillAcrobaticsXp
            )
            else -> null
        }

        renderer = CaveRenderer(
            context = this, touch = touch,
            worldSeed = save?.seed ?: System.currentTimeMillis(),
            worldId = worldId, savedState = savedState,
            terrainVersion = save?.terrainVersion ?: 4,
            worldSource = mapSource,
            modeFactory = if (mapSource != null) { r ->
                if (mapSource.isShowcase) com.Atom2Universe.app.games.caves.mode.ShowcaseMode(r, mapSource)
                else AssaultMode(r, mapSource)
            } else ::SurvivalMode
        )
        renderer.isCreative = isCreative
        renderer.enemyManager.isCreative = isCreative
        if (isCreative) renderer.pendingMode = PlayerMode.SPECTATOR

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
        val btnCombatMode = hudView.findViewById<Button>(R.id.cave_btn_combat_mode)
        minimapView       = hudView.findViewById(R.id.cave_minimap)
        vHudControls      = hudView.findViewById(R.id.cave_hud_controls)
        val btnUp         = hudView.findViewById<Button>(R.id.cave_btn_up).also    { vBtnUp    = it }
        val btnDown       = hudView.findViewById<Button>(R.id.cave_btn_down).also  { vBtnDown  = it }
        val btnRun = hudView.findViewById<Button>(R.id.cave_btn_run).also { vBtnRun = it }
        hud.controlIcon(btnRun, "run", getString(R.string.cave_controls_run))
        val btnLaser      = hudView.findViewById<Button>(R.id.cave_btn_laser).also { vBtnLaser = it }
        val btnPlace      = hudView.findViewById<Button>(R.id.cave_btn_place).also { vBtnPlace = it }

        makeCircular(btnUp,    0x55FFFFFF.toInt())
        makeCircular(btnDown,  0x55FFFFFF.toInt())
        makeCircular(btnLaser, 0x66003366.toInt())
        makeCircular(btnPlace, 0x66336600.toInt())
        btnPlace.visibility = View.GONE
        vGameArea = hudView.findViewById(R.id.cave_game_area)
        vGameArea?.let { applyButtonPositions(it) }
        vGameArea?.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyButtonPositions(view as FrameLayout)
            }
        }

        val miningPanel   = hudView.findViewById<LinearLayout>(R.id.cave_mining_panel)
        val tvMiningBlock = hudView.findViewById<android.widget.TextView>(R.id.cave_tv_mining_block)
        val miningBar     = hudView.findViewById<android.widget.ProgressBar>(R.id.cave_mining_progress)
        val hotbarLayout  = hudView.findViewById<LinearLayout>(R.id.cave_hotbar)

        val hotbarModeButton = if (!isAssault) btnCombatMode.also {
            (it.parent as android.view.ViewGroup).removeView(it)
        } else null
        hud.buildHotbarUI(hotbarLayout, hotbarModeButton)
        if (renderer.mode.singleWeapon) hotbarLayout.visibility = View.GONE
        vQuickbar = hotbarLayout
        btnBack.background = CaveUiStyle.panel(this, 0x66293F33, 0x6686A38C)
        CaveUiStyle.button(btnMode)
        hud.controlIcon(btnCamera, "camera", getString(R.string.cave_ui_camera))
        hud.controlIcon(btnDayNight, "sun", renderer.timeOfDayLabel)
        btnBack.setOnClickListener { showQuitConfirmation() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) invManager.closeInventory()
                else showQuitConfirmation()
            }
        })
        btnMode.setOnClickListener {
            renderer.pendingMode = if (renderer.playerMode == PlayerMode.WALK) PlayerMode.SPECTATOR else PlayerMode.WALK
        }
        btnDayNight.setOnClickListener {
            renderer.cycleTimeOfDay()
            hud.controlIcon(btnDayNight, "sun", renderer.timeOfDayLabel)
        }
        btnCamera.setOnClickListener {
            glView.queueEvent { renderer.camera.thirdPerson = !renderer.camera.thirdPerson }
            btnCamera.alpha = if (renderer.camera.thirdPerson) 1.0f else 0.5f
        }
        btnCamera.alpha = 0.5f

        fun applyCombatModeUi(mode: HotbarMode) {
            hud.controlIcon(btnCombatMode, when(mode) { HotbarMode.COMBAT -> "combat"; HotbarMode.BUILD -> "place"; HotbarMode.GARDEN -> "garden" },
                getString(when(mode) { HotbarMode.COMBAT -> R.string.cave_ui_equipment; HotbarMode.BUILD -> R.string.cave_ui_materials; HotbarMode.GARDEN -> R.string.cave_ui_garden }))
        }
        applyCombatModeUi(renderer.hotbarMode)
        applyBuildModeUi(renderer.hotbarMode, btnPlace)
        btnCombatMode.setOnClickListener { renderer.toggleHotbarMode() }
        renderer.farmMessageCallback = { message -> uiHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() } }
        renderer.hotbarModeCallback = { mode ->
            uiHandler.post {
                applyCombatModeUi(mode)
                applyBuildModeUi(mode, btnPlace)
                invManager.onHotbarModeChanged()
            }
        }

        val btnMap = hudView.findViewById<Button>(R.id.cave_btn_map)
        hud.controlIcon(btnMap, "map", getString(R.string.cave_ui_map))
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
        renderer.modeCallback    = { mode -> uiHandler.post { applyModeUi(mode, btnMode, btnUp as Button, btnDown, btnLaser) } }
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

        hud.buildHealthBar(root, renderer.mode is AssaultMode)
        hud.buildWeaponInHand(root)
        renderer.weaponStatusCallback = { text -> uiHandler.post { hud.updateWeaponStatus(text) } }
        hud.buildDamageFlash(root)

        renderer.playerHpCallback = { hp, maxHp -> uiHandler.post { hud.updateHealthBar(hp, maxHp) } }
        renderer.shieldCallback   = { cur, max  -> uiHandler.post { hud.updateShieldBar(cur, max) } }
        renderer.sprintCallback   = { active -> uiHandler.post { hud.updateSprintIndicator(active) } }
        renderer.crouchCallback = { crouching -> uiHandler.post {
            crouchingUi = crouching
            vBtnRun?.visibility = if (walkingUi && !crouching) View.VISIBLE else View.GONE
        } }
        renderer.playerHitCallback = { uiHandler.post { hud.flashDamage() } }

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

        applyModeUi(if (isCreative) PlayerMode.SPECTATOR else PlayerMode.WALK, btnMode, btnUp as Button, btnDown, btnLaser)

        // Le mode Assaut distribue son kit d'armes au démarrage : l'UI relit tout.
        renderer.loadoutChangedCallback = { uiHandler.post { refreshInventoryUi() } }
        if (isAssault) {
            // Shooter : ni construction ni destruction, donc pas de bascule vers la barre des matériaux.
            btnCombatMode.visibility = View.GONE
            // L'heure est figée à midi : le bouton jour/nuit n'a plus de sens.
            btnDayNight.visibility = View.GONE
            if (mapSource?.isShowcase != true) hud.buildMatchPanel(root)
            (renderer.mode as? com.Atom2Universe.app.games.caves.mode.ShowcaseMode)?.let { mode ->
                val caption = android.widget.TextView(this).apply {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(0x99404B43.toInt())
                    textSize = 13f
                    setPadding(16, 8, 16, 8)
                    setText(R.string.cave_showcase_hint)
                }
                root.addView(caption, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (56 * resources.displayMetrics.density).toInt() })
                val names = intArrayOf(R.string.cave_showcase_mountain, R.string.cave_showcase_desert,
                    R.string.cave_showcase_beach, R.string.cave_showcase_forest, R.string.cave_showcase_marsh,
                    R.string.cave_showcase_volcano, R.string.cave_showcase_gallery, R.string.cave_showcase_walk)
                mode.onCaption = { zone, block -> uiHandler.post {
                    caption.text = if (block != null) getString(R.string.cave_showcase_sample, blockName(block))
                        else getString(names[zone])
                } }
                val coldNames = resources.getStringArray(R.array.cave_showcase_cold_names)
                val caveNames = resources.getStringArray(R.array.cave_showcase_underground_names)
                mode.onCaveCaption = { index -> uiHandler.post { caption.text = caveNames[index] } }
                val gardenStages = resources.getStringArray(R.array.cave_showcase_garden_stages)
                mode.onGardenCaption = { crop, stage -> uiHandler.post {
                    caption.text = getString(R.string.cave_showcase_garden_caption,
                        getString(com.Atom2Universe.app.games.caves.node.FarmShowcasePlants.crops[crop].label),
                        gardenStages[stage])
                } }
                val villageNames = resources.getStringArray(R.array.cave_showcase_village_names)
                mode.onVillageCaption = { index -> uiHandler.post {
                    caption.text = villageNames[index]
                } }
                mode.onColdCaption = { index -> uiHandler.post {
                    caption.text = coldNames[index]
                } }
                val treeNames = resources.getStringArray(R.array.cave_showcase_tree_names)
                mode.onTreeCaption = { index -> uiHandler.post {
                    caption.text = getString(R.string.cave_showcase_tree_caption, treeNames[index])
                } }
                val modelIds = listOf("dwarf", "goblin", "golem", "imp", "mummy", "ogre",
                    "skeleton", "slime", "spider", "troll", "wraith", "zombie", "soldier")
                val mobNames = resources.getStringArray(R.array.cave_showcase_mob_names)
                mode.onMobCaption = { animal, pose -> uiHandler.post {
                    val model = animal.def.model
                    val passive = animal.def.behavior == "passive"
                    val animatedIdle = !passive
                    val poseName = when (pose) {
                        com.Atom2Universe.app.games.caves.entity.ExhibitPose.REFERENCE -> R.string.cave_showcase_reference
                        com.Atom2Universe.app.games.caves.entity.ExhibitPose.IDLE ->
                            when {
                                model == "chicken" -> R.string.cave_animal_pecking
                                model == "pig" -> R.string.cave_animal_rooting
                                passive -> R.string.cave_animal_grazing
                                animatedIdle -> R.string.cave_showcase_idle
                                else -> R.string.cave_showcase_still
                            }
                        com.Atom2Universe.app.games.caves.entity.ExhibitPose.ACTION -> when (model) {
                            "soldier" -> R.string.cave_showcase_firing
                            "sheep", "cow", "chicken", "pig" -> R.string.cave_showcase_walking
                            else -> R.string.cave_showcase_walk_attack
                        }
                    }
                    val name = if (passive) {
                        val speciesName = when (model) {
                            "sheep" -> if (animal.young) R.string.cave_animal_lamb else R.string.cave_animal_sheep
                            "cow" -> if (animal.young) R.string.cave_animal_calf else R.string.cave_animal_cow
                            "chicken" -> if (animal.young) R.string.cave_animal_chick else R.string.cave_animal_chicken
                            else -> if (animal.young) R.string.cave_animal_piglet else R.string.cave_animal_pig
                        }
                        val palette = when (model) {
                            "sheep" -> R.array.cave_sheep_coats
                            "cow" -> R.array.cave_cow_coats
                            "chicken" -> if (animal.young) R.array.cave_chick_coats else R.array.cave_chicken_coats
                            else -> R.array.cave_pig_coats
                        }
                        val coats=resources.getStringArray(palette)
                        getString(R.string.cave_animal_variant, getString(speciesName), coats[animal.coat.coerceIn(0,coats.lastIndex)])
                    } else mobNames[modelIds.indexOf(model).coerceAtLeast(0)]
                    caption.text = getString(R.string.cave_showcase_mob_caption, name, getString(poseName))
                } }
            }
            (renderer.mode as? AssaultMode)?.let { mode ->
                mode.onStatus = { status -> uiHandler.post {
                    hud.updateMatchPanel(status)
                    if (status.phase == com.Atom2Universe.app.games.caves.mode.AssaultMatch.Phase.CHOOSING_WEAPON) {
                        showAssaultWeaponChoice(mode, status.round + 1)
                    } else {
                        assaultWeaponPending = false
                        assaultWeaponDialog?.dismiss()
                    }
                } }
                mode.onHeadshotKill = { uiHandler.post { hud.flashHeadshot() } }
                mode.onShieldCollected = { amount -> uiHandler.post { hud.flashShieldPickup(amount) } }

                // Bouton 🧭 : trace au sol le chemin du mannequin coureur (test de la navigation).
                val density = resources.displayMetrics.density
                root.addView(Button(this).apply {
                    text = "🧭"; textSize = 14f; setBackgroundColor(0x55FFFFFF); setTextColor(Color.WHITE)
                    contentDescription = getString(R.string.cave_assault_show_path)
                    layoutParams = FrameLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).also {
                        it.gravity = Gravity.TOP or Gravity.END
                        it.setMargins(0, (8 * density).toInt(), (8 * density).toInt(), 0)
                    }
                    alpha = 0.6f
                    setOnClickListener {
                        mode.showPath = !mode.showPath
                        alpha = if (mode.showPath) 1.0f else 0.6f
                    }
                })
            }
        }

        loadingCover.bringToFront()
        renderer.loadingCallback = { loading ->
            uiHandler.post { loadingCover.visibility = if (loading) View.VISIBLE else View.GONE }
        }
        loadingCover.visibility = if (renderer.spawnReady) View.GONE else View.VISIBLE

        soundEngine = CaveSoundEngine(lifecycleScope).also {
            it.start()
            it.subscribe(renderer.eventBus)
        }
    }

    private fun showAssaultWeaponChoice(mode: AssaultMode, round: Int) {
        if (isFinishing || isDestroyed || assaultWeaponDialog != null || assaultWeaponPending) return
        releaseGameInputs()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun surface(color: Int, stroke: Int) = android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), stroke)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            background = surface(0xFF111B27.toInt(), 0xFF35465B.toInt())
        }
        panel.addView(android.widget.TextView(this).apply {
            text = getString(R.string.cave_assault_choose_weapon, round)
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        panel.addView(android.widget.TextView(this).apply {
            setText(R.string.cave_assault_weapon_rules)
            setTextColor(0xFFB8C8DA.toInt())
            textSize = 13f
            setPadding(0, dp(6), 0, dp(16))
        })
        val widthDp = resources.displayMetrics.widthPixels / density
        val columns = if (widthDp >= 680) 3 else 2
        val grid = android.widget.GridLayout(this).apply { columnCount = columns }
        panel.addView(grid, LinearLayout.LayoutParams(-1, -2))
        panel.addView(android.widget.TextView(this).apply {
            setText(R.string.cave_quit_confirm)
            setTextColor(0xFFB8C8DA.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            addView(panel)
        }
        val dialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .create()
        dialog.setView(scroll, 0, 0, 0, 0)
        for ((index, type) in mode.weaponChoices.withIndex()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), surface(0xFF304B63.toInt(), 0xFF74CFFF.toInt()))
                    addState(intArrayOf(android.R.attr.state_focused), surface(0xFF304B63.toInt(), 0xFF74CFFF.toInt()))
                    addState(intArrayOf(), surface(0xFF1C2B3C.toInt(), 0xFF3B5068.toInt()))
                }
                isFocusable = true
                contentDescription = weaponName(type)
                addView(android.widget.ImageView(this@CaveActivity).apply {
                    setImageDrawable(com.Atom2Universe.app.games.caves.render.WeaponIconDrawable(
                        assets, type, com.Atom2Universe.app.games.caves.node.ItemRarity.COMMON))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(72), dp(72)))
                addView(android.widget.TextView(this@CaveActivity).apply {
                    text = weaponName(type)
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(8), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, -2))
                setOnClickListener {
                    if (assaultWeaponPending) return@setOnClickListener
                    assaultWeaponPending = true
                    releaseGameInputs()
                    dialog.dismiss()
                    glView.queueEvent {
                        val accepted = mode.chooseWeapon(type)
                        if (!accepted) uiHandler.post {
                            assaultWeaponPending = false
                            showAssaultWeaponChoice(mode, round)
                        }
                    }
                }
            }
            grid.addView(card, android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(index / columns),
                android.widget.GridLayout.spec(index % columns, 1f)).apply {
                width = 0
                height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        assaultWeaponDialog = dialog
        dialog.setOnDismissListener {
            if (assaultWeaponDialog === dialog) assaultWeaponDialog = null
            releaseGameInputs()
        }
        dialog.show()
        dialog.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            val maxHeight = resources.displayMetrics.heightPixels - dp(32)
            val dialogWidth = minOf(dp(760), resources.displayMetrics.widthPixels - dp(32))
            scroll.measure(View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
            w.setLayout(dialogWidth, minOf(scroll.measuredHeight, maxHeight))
            w.setDimAmount(0.65f)
            WindowInsetsControllerCompat(w, w.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
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

    override fun onResume()  {
        super.onResume()
        if (::glView.isInitialized) resumeGame()
    }

    private fun resumeGame() {
        glView.onResume()
        touch.crouchToggleEnabled = CaveControlsPrefs.crouchToggle(this)
        touch.runToggleEnabled = CaveControlsPrefs.runToggle(this)
        soundEngine?.resume()
        music.resume()
        forceImmersiveMode()
        vGameArea?.let { applyButtonPositions(it) }
    }
    override fun onPause()   { super.onPause(); if (!::glView.isInitialized) return; glView.onPause();  music.pause(); soundEngine?.pause(); saveWorld(); minimapJob?.cancel() }
    override fun onDestroy() {
        assaultWeaponDialog?.dismiss()
        assaultWeaponDialog = null
        super.onDestroy()
        // Carte Assaut illisible : l'activité se ferme dans onCreate, avant d'avoir créé le renderer.
        if (!::renderer.isInitialized) return
        renderer.destroy()
        music.stop()
        soundEngine?.destroy()
        com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.clear()
    }

    /** À appeler sur le thread UI quand le renderer a remplacé l'inventaire d'un bloc (kit d'armes). */
    private fun refreshInventoryUi() {
        // Les anciennes banques UI ne doivent pas réécrire les slots du kit.
        invManager.initInvSlots()
        renderer.hotbarModeCallback?.invoke(renderer.hotbarMode)
        renderer.inventoryCallback?.invoke(renderer.inventory.toMap())
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun buildSaveSnap(): CaveWorldSave? {
        val id    = worldId ?: return null
        val stats = renderer.playerStats
        val sb    = renderer.skillBook
        return CaveWorldSave(
            id = id, name = "", seed = 0L, createdAt = 0L,
            terrainVersion = renderer.world.terrainVersion,
            lastPlayedAt = System.currentTimeMillis(),
            playerX = renderer.camera.playerX, playerY = renderer.camera.playerY, playerZ = renderer.camera.playerZ,
            playerYaw = renderer.camera.yaw, playerPitch = renderer.camera.pitch,
            inventory = if (isCreative) survivalInventory + renderer.inventory.filterKeys {
                com.Atom2Universe.app.games.caves.node.FarmItems.isItem(it)
            } else renderer.inventory.toMap(),
            hotbar    = if (isCreative) survivalHotbar    else renderer.combatHotbar.map { it },
            buildHotbar = if (isCreative) emptyList()     else renderer.buildHotbar.map { it },
            gardenHotbar = renderer.gardenHotbar.toList(), farming = renderer.farming.snapshot(),
            isCreative          = isCreative,
            playerHp            = renderer.playerNode.hp,
            playerLevel         = stats.level,
            playerXp            = stats.xp,
            playerMaxHp         = stats.maxHp,
            playerShield        = stats.shield,
            playerShieldCurrent = renderer.playerNode.shield,
            wardStonePositions  = renderer.enemyManager.wardStoneZones.toList(),
            skillAthleticsXp    = sb.athleticsXp,
            skillSpeedXp        = sb.speedXp,
            skillEnduranceXp    = sb.enduranceXp,
            skillAcrobaticsXp   = sb.acrobaticsXp,
            weaponInstances     = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.snapshot(),
            passiveAnimals = renderer.passiveAnimals.snapshot,
            recoverableAmmo = renderer.recoverableAmmoSnapshot
        )
    }

    fun saveWorldAsync(flushChunks: Boolean = false) {
        val snap = buildSaveSnap() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            CaveWorldSaveManager.updateFields(this@CaveActivity, snap)
            if (flushChunks) renderer.flushStorage()
        }
    }

    private suspend fun saveWorldNow() {
        val snap = buildSaveSnap() ?: return
        withContext(Dispatchers.IO) {
            CaveWorldSaveManager.updateFields(this@CaveActivity, snap)
            renderer.flushStorage()
        }
    }

    private fun saveWorld() = saveWorldAsync(flushChunks = true)

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
        if (!renderer.mode.singleWeapon) dialogRoot.addView(Button(this).apply {
            setText(R.string.cave_cheat_weapon_kit)
            setTextColor(0xFF9FD5FF.toInt())
            setOnClickListener {
                isEnabled = false
                glView.queueEvent {
                    renderer.giveWeaponTestKit()
                    uiHandler.post {
                        refreshInventoryUi()
                        android.widget.Toast.makeText(this@CaveActivity,
                            R.string.cave_cheat_weapon_kit_done,android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
        },dialogRoot.indexOfChild(btnRow))
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
        val label = btn.text
        val kind = when (btn.id) {
            R.id.cave_btn_laser -> "aim"
            R.id.cave_btn_place -> "place"
            R.id.cave_btn_down -> "down"
            else -> "jump"
        }
        btn.contentDescription = label; btn.text = ""
        btn.backgroundTintList = null; btn.setPadding(0, 0, 0, 0)
        val icon = android.graphics.drawable.InsetDrawable(CaveActionDrawable(kind), CaveUiStyle.dp(this, 18))
        btn.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x557DAD8B),
            android.graphics.drawable.LayerDrawable(arrayOf(CaveUiStyle.panel(this, 0x66293F33, 0x889EBAA3.toInt()), icon)), null)
    }

    private fun applyButtonPositions(gameArea: FrameLayout) {
        gameArea.doOnLayout {
            val w = gameArea.width.toFloat(); val h = gameArea.height.toFloat()
            applyBtnLayout(vBtnUp,    CaveControlsPrefs.Btn.UP,    w, h)
            applyBtnLayout(vBtnDown,  CaveControlsPrefs.Btn.DOWN,  w, h)
            applyBtnLayout(vBtnLaser, CaveControlsPrefs.Btn.LASER, w, h)
            applyBtnLayout(vBtnPlace, CaveControlsPrefs.Btn.PLACE, w, h)
            applyBtnLayout(vBtnRun, CaveControlsPrefs.Btn.RUN, w, h)
        }
    }

    private fun applyBtnLayout(view: View?, btn: CaveControlsPrefs.Btn, parentW: Float, parentH: Float) {
        view ?: return
        if (parentW <= 0f || parentH <= 0f) return
        val dp = resources.displayMetrics.density
        val sizePx = max(1, (CaveControlsPrefs.sizeDp(this, btn) * dp).toInt())
        val halfSizeX = (sizePx / (parentW * 2f)).coerceAtMost(0.5f)
        val halfSizeY = (sizePx / (parentH * 2f)).coerceAtMost(0.5f)
        val safeXf = CaveControlsPrefs.xf(this, btn).coerceIn(halfSizeX, 1f - halfSizeX)
        val safeYf = CaveControlsPrefs.yf(this, btn).coerceIn(halfSizeY, 1f - halfSizeY)
        val maxX = max(0f, parentW - sizePx)
        val maxY = max(0f, parentH - sizePx)
        // Store the position in the layout itself, including for currently GONE buttons.
        // Translations based on stale bounds can shift when a hidden button is laid out.
        val x = (safeXf * parentW - sizePx / 2f).coerceIn(0f, maxX).toInt()
        val y = (safeYf * parentH - sizePx / 2f).coerceIn(0f, maxY).toInt()
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.width != sizePx || params.height != sizePx ||
            params.leftMargin != x || params.topMargin != y || params.gravity != (Gravity.TOP or Gravity.LEFT)) {
            params.width = sizePx
            params.height = sizePx
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.setMargins(x, y, 0, 0)
            view.layoutParams = params
        }
        view.translationX = 0f
        view.translationY = 0f
    }

    // ── Mode UI ───────────────────────────────────────────────────────────────

    private fun applyModeUi(mode: PlayerMode, btnMode: Button, btnUp: Button, btnDown: View, btnLaser: View) {
        walkingUi = mode == PlayerMode.WALK
        vBtnRun?.visibility = if (walkingUi && !crouchingUi) View.VISIBLE else View.GONE
        btnMode.visibility = if (isCreative) View.VISIBLE else View.GONE
        btnDown.visibility = View.VISIBLE
        hud.controlIcon(btnDown as Button,
            if (mode == PlayerMode.WALK) "crouch" else "down",
            getString(if (mode == PlayerMode.WALK) R.string.cave_controls_crouch else R.string.cave_ui_descend))
        when (mode) {
            PlayerMode.SPECTATOR -> { btnMode.text = getString(R.string.cave_mode_spectator); btnUp.contentDescription = getString(R.string.cave_ui_ascend); btnDown.visibility = View.VISIBLE; btnLaser.visibility = View.GONE }
            PlayerMode.WALK      -> { btnMode.text = getString(R.string.cave_mode_walk); btnUp.contentDescription = getString(R.string.cave_jump); btnLaser.visibility = View.VISIBLE }
        }
    }

    private fun applyBuildModeUi(mode: HotbarMode, btnPlace: View) {
        btnPlace.visibility = if (mode != HotbarMode.COMBAT) View.VISIBLE else View.GONE
        if (btnPlace is Button) hud.controlIcon(btnPlace, if (mode == HotbarMode.GARDEN) "garden" else "place",
            getString(if (mode == HotbarMode.GARDEN) R.string.cave_farm_controls else R.string.cave_ui_materials))
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
        listOf(vBtnBack, vBtnUp, vBtnDown, vBtnLaser, vBtnPlace, vBtnRun).forEach { v ->
            v?.alpha = a; v?.isEnabled = visible
        }
        vHudControls?.alpha = a
        (vHudControls as? ViewGroup)?.let { g ->
            for (i in 0 until g.childCount) { g.getChildAt(i).alpha = a; g.getChildAt(i).isEnabled = visible }
        }
    }

    // ── Input manette ─────────────────────────────────────────────────────────

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (!::glView.isInitialized) return super.onGenericMotionEvent(event)
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE)
            return invManager.handleInvGamepadMotion(event)
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
            setHudButtonsVisible(false)
        return gamepad.onGenericMotion(event) || super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (!::glView.isInitialized) return super.dispatchKeyEvent(event)
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) {
            if (event.action == KeyEvent.ACTION_DOWN && invManager.handleInvGamepadKey(event.keyCode)) return true
            if (event.action == KeyEvent.ACTION_UP) return true
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (KeyEvent.isGamepadButton(event.keyCode)) setHudButtonsVisible(false)
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_Y  -> { invManager.openInventory(); return true }
                KeyEvent.KEYCODE_BUTTON_L1 -> { glView.queueEvent { renderer.selectSlot((renderer.selectedSlot - 1 + ACTIVE_SIZE) % ACTIVE_SIZE) }; return true }
                KeyEvent.KEYCODE_BUTTON_R1 -> { glView.queueEvent { renderer.selectSlot((renderer.selectedSlot + 1) % ACTIVE_SIZE) }; return true }
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
        if (!::glView.isInitialized) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) setHudButtonsVisible(true)
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) return super.dispatchTouchEvent(ev)
        val action = ev.actionMasked; val idx = ev.actionIndex; val pid = ev.getPointerId(idx)
        var hitsRun = false
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = ev.getX(idx); val y = ev.getY(idx)
                fun hit(v: View?) = v != null && v.visibility == View.VISIBLE && v.isHitOnScreen(x, y)
                val hitsQuickbar = hit(vQuickbar)
                val hitsHudOnly = listOf(vHudControls, vBtnBack).any { hit(it) }
                hitsRun = hit(vBtnRun)
                if (hitsQuickbar) {
                    uiTouchIds.add(pid)
                    handleQuickbarPointerDown(x, y)
                }
                if (hitsHudOnly) uiTouchIds.add(pid)
                if (ptrUp    == -1 && hit(vBtnUp))    { ptrUp    = pid; touch.flyUp       = true }
                if (ptrDown  == -1 && hit(vBtnDown))  { ptrDown  = pid; touch.pressDown() }
                if (ptrLaser == -1 && hit(vBtnLaser)) { ptrLaser = pid; touch.laserActive = true; touch.rtChargeRaw = 1f }
                if (ptrPlace == -1 && hit(vBtnPlace)) { ptrPlace = pid; touch.placeRequested = true; uiTouchIds.add(pid) }
                val hitsAction = hitsRun || listOf(vBtnUp, vBtnDown, vBtnLaser, vBtnPlace).any { hit(it) }
                if (!hitsQuickbar && !hitsHudOnly && !hitsAction) {
                    tapCandidates[pid] = TapCandidate(x, y, ev.eventTime)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // A drag that returns to its starting point is still not a tap.
                for (i in 0 until ev.pointerCount) {
                    val pointer = ev.getPointerId(i)
                    val candidate = tapCandidates[pointer] ?: continue
                    val moved = (0 until ev.historySize).any { history ->
                        hypot(ev.getHistoricalX(i, history) - candidate.x,
                            ev.getHistoricalY(i, history) - candidate.y) > 18f
                    } || hypot(ev.getX(i) - candidate.x, ev.getY(i) - candidate.y) > 18f
                    if (moved) tapCandidates.remove(pointer)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val cancel = action == MotionEvent.ACTION_CANCEL
                if (!cancel) handleWorldTapCandidate(ev, idx, pid)
                if (pid == ptrUp    || cancel) { ptrUp    = -1; touch.flyUp       = false }
                if (pid == ptrDown  || cancel) { ptrDown  = -1; touch.flyDown     = false }
                if (pid == ptrLaser || cancel) { ptrLaser = -1; touch.laserActive = false; touch.rtChargeRaw = 0f }
                if (pid == ptrPlace || cancel) { ptrPlace = -1 }
            }
        }
        touch.onTouch(ev, glView.width, uiTouchIds,
            actionCameraPointer = pid == ptrUp || pid == ptrDown || pid == ptrLaser,
            runButtonPointer = hitsRun)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) uiTouchIds.remove(pid)
        if (action == MotionEvent.ACTION_CANCEL) { uiTouchIds.clear(); tapCandidates.clear(); touch.reset() }
        return super.dispatchTouchEvent(ev)
    }

    private fun handleQuickbarPointerDown(x: Float, y: Float): Boolean {
        hud.slotViews.forEachIndexed { index, slot ->
            if (slot != null && slot.visibility == View.VISIBLE && slot.isHitOnScreen(x, y)) {
                if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) {
                    invManager.onOverlayActiveSlotClick(index)
                } else {
                    glView.queueEvent { renderer.selectSlot(index) }
                }
                return true
            }
        }
        return false
    }

    private fun handleWorldTapCandidate(ev: MotionEvent, idx: Int, pid: Int) {
        val candidate = tapCandidates.remove(pid) ?: return
        if (renderer.hotbarMode == HotbarMode.COMBAT) return
        val x = ev.getX(idx); val y = ev.getY(idx)
        val moved = hypot(x - candidate.x, y - candidate.y)
        val elapsed = ev.eventTime - candidate.downMs
        val wasCameraDrag = touch.isCameraPointer(pid) && touch.didCameraPointerMove()
        if (moved <= 18f && elapsed <= 260L && !wasCameraDrag && !isAssault) {
            val location = IntArray(2)
            glView.getLocationInWindow(location)
            if (glView.width <= 0 || glView.height <= 0) return
            val xf = (x - location[0]) / glView.width
            val yf = (y - location[1]) / glView.height
            if (xf in 0f..1f && yf in 0f..1f) {
                glView.queueEvent { renderer.placeBlockAtScreen(xf, yf) }
            }
        }
    }

    private fun View.isHitOnScreen(x: Float, y: Float): Boolean {
        val loc = IntArray(2); getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }
}
