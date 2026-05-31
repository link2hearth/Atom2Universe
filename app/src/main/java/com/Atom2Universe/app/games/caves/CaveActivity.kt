package com.Atom2Universe.app.games.caves

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity

import com.Atom2Universe.app.games.caves.entity.UpgradeOption
import com.Atom2Universe.app.games.caves.entity.UpgradeType
import com.Atom2Universe.app.games.caves.input.GamepadController
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class CraftingRecipe(
    val ingredients: List<Pair<Byte, Int>>,
    val output: Byte,
    val outputCount: Int
) {
    fun canCraft(inv: Map<Byte, Int>) = ingredients.all { (t, n) -> (inv[t] ?: 0) >= n }
}

class CaveActivity : ThemedActivity() {

    companion object {
        const val EXTRA_WORLD_ID = "cave_world_id"
        private const val ACTIVE_SIZE  = 9
        private const val GRID_COLS    = 6
        private const val EMPTY_BUFFER = 36

        fun blockColor(type: Byte): Int = when (type) {
            STONE          -> 0xFF808080.toInt()
            GRANITE        -> 0xFF9B7B6A.toInt()
            QUARTZ         -> 0xFFE8E4D0.toInt()
            COAL           -> 0xFF2C2C2C.toInt()
            GOLD           -> 0xFFFFD700.toInt()
            CRYSTAL        -> 0xFF88FFFF.toInt()
            DIRT           -> 0xFF8B5E3C.toInt()
            GRAVEL         -> 0xFF9E9E9E.toInt()
            IRON           -> 0xFFB87333.toInt()
            SILVER         -> 0xFFC0C0C0.toInt()
            RUBY           -> 0xFFCC1111.toInt()
            LAVA           -> 0xFFFF4500.toInt()
            FURNACE        -> 0xFF555555.toInt()
            EMERALD        -> 0xFF50C878.toInt()
            COPPER         -> 0xFFB87333.toInt()
            GRASS          -> 0xFF55AA33.toInt()
            WOOD           -> 0xFF8B6914.toInt()
            LEAVES         -> 0xFF2D6A1F.toInt()
            SAND           -> 0xFFD4C06A.toInt()
            REDSAND        -> 0xFFCC6633.toInt()
            ICE            -> 0xFFAADDFF.toInt()
            SNOW           -> 0xFFEEEEFF.toInt()
            BRICK_RED      -> 0xFFAA3322.toInt()
            ROCK           -> 0xFF888888.toInt()
            ROCK_MOSS      -> 0xFF667744.toInt()
            MUSHROOM_RED   -> 0xFFCC3322.toInt()
            MUSHROOM_BROWN -> 0xFF886644.toInt()
            MUSHROOM_TAN   -> 0xFFAA8855.toInt()
            TORCH          -> 0xFFFF9020.toInt()
            PLANK            -> 0xFFC88237.toInt()
            WOOD_PLANK_WHITE -> 0xFFD4BFA0.toInt()
            WARD_STONE       -> 0xFF8844FF.toInt()
            BUCKET_EMPTY     -> 0xFFB87832.toInt()
            BUCKET_FULL      -> 0xFF3A7BCC.toInt()
            else             -> 0xFF444444.toInt()
        }

        fun blockTextureName(type: Byte): String? = when (type) {
            STONE          -> "stone.png"
            GRANITE        -> "greystone.png"
            QUARTZ         -> "greysand.png"
            COAL           -> "stone_coal.png"
            GOLD           -> "stone_gold.png"
            CRYSTAL        -> "stone_diamond.png"
            DIRT           -> "dirt.png"
            GRAVEL         -> "gravel_stone.png"
            IRON           -> "stone_iron.png"
            SILVER         -> "stone_silver.png"
            RUBY           -> "greystone_ruby.png"
            LAVA           -> "lava.png"
            FURNACE        -> "oven.png"
            EMERALD        -> "redstone_emerald.png"
            COPPER         -> "stone_browniron.png"
            GRASS          -> "grass_top.png"
            WOOD           -> "trunk_top.png"
            LEAVES         -> "leaves.png"
            SAND           -> "sand.png"
            REDSAND        -> "redsand.png"
            ICE            -> "ice.png"
            SNOW           -> "snow.png"
            BRICK_RED      -> "brick_red.png"
            ROCK           -> "rock.png"
            ROCK_MOSS      -> "rock_moss.png"
            MUSHROOM_RED   -> "mushroom_red.png"
            MUSHROOM_BROWN -> "mushroom_brown.png"
            MUSHROOM_TAN     -> "mushroom_tan.png"
            WOOD_PLANK_WHITE -> "wood_plank_white.png"
            BUCKET_EMPTY     -> "Items/bucket.png"
            BUCKET_FULL      -> "Items/bucket_full.png"
            else             -> null  // TORCH et PLANK : texture générée en code, pas de fichier asset
        }

        private val RECIPES = listOf(
            CraftingRecipe(listOf(STONE   to 4),            BRICK_RED, 4),
            CraftingRecipe(listOf(SAND    to 2),            QUARTZ,    1),
            CraftingRecipe(listOf(GRAVEL  to 4),            STONE,     2),
            CraftingRecipe(listOf(SNOW    to 4),            ICE,       1),
            CraftingRecipe(listOf(DIRT    to 4),            GRASS,     1),
            CraftingRecipe(listOf(GRANITE to 4),            ROCK,      4),
            CraftingRecipe(listOf(WOOD    to 3),            FURNACE,   1),
            CraftingRecipe(listOf(IRON    to 3),            FURNACE,   1),
            CraftingRecipe(listOf(COAL to 4, STONE to 4),  FURNACE,   2),
            CraftingRecipe(listOf(QUARTZ to 2, STONE to 4),BRICK_RED, 8),
            CraftingRecipe(listOf(WOOD       to 1),          PLANK,           4),
            CraftingRecipe(listOf(WOOD_WHITE to 1),          WOOD_PLANK_WHITE, 4),
            CraftingRecipe(listOf(PLANK to 1, COAL to 1),   TORCH,            4),
            CraftingRecipe(listOf(PLANK to 3),               BUCKET_EMPTY,     1),
        )
    }

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: CaveRenderer
    private val touch   = TouchController()
    private val gamepad = GamepadController(touch)
    private val uiHandler = Handler(Looper.getMainLooper())
    private var worldId: String? = null
    private var ambientMusic: CaveAmbientMusic? = null

    private var ptrUp    = -1; private var ptrDown  = -1
    private var ptrLaser = -1; private var ptrPlace = -1
    private var vBtnUp:    View? = null; private var vBtnDown:  View? = null
    private var vBtnLaser: View? = null; private var vBtnPlace: View? = null

    // ── HP / XP / Shield ──────────────────────────────────────────────────────
    private var hpBarFg: View? = null
    private var hpText: TextView? = null
    private var hpBarMaxWidth = 0
    private var shieldBarFg: View? = null
    private var shieldContainer: View? = null

    private var xpBarFg: View? = null
    private var xpBarMaxWidth = 0
    private var xpLevelTv: TextView? = null

    private var levelUpOverlay: FrameLayout? = null

    private val slotViews   = arrayOfNulls<FrameLayout>(ACTIVE_SIZE)
    private val slotCounts  = arrayOfNulls<TextView>(ACTIVE_SIZE)
    private val slotColors  = arrayOfNulls<View>(ACTIVE_SIZE)
    private lateinit var invOverlay: View

    private val blockBitmapCache = HashMap<Byte, Bitmap?>()

    private val invSlots = ArrayList<Byte?>()
    private var invSlotsReady = false
    private fun hotbarBase() = (invSlots.size - ACTIVE_SIZE).coerceAtLeast(0)

    private var selectedSlotIdx = -1
    private fun selectedType(): Byte? =
        selectedSlotIdx.takeIf { it in invSlots.indices }?.let { invSlots[it] }

    private var invGridAdapter:    InvGridAdapter?  = null
    private var craftingAdapter:   CraftingAdapter? = null

    private var infoSpriteView:        View?        = null
    private var infoNameTv:            TextView?    = null
    private var infoCountTv:           TextView?    = null
    private var infoDivider:           View?        = null
    private var infoIngredientsTv:     TextView?    = null
    private var craftingEmptyTv:       View?        = null
    private var craftingRecyclerView:  RecyclerView? = null

    private var selectedRecipe: CraftingRecipe? = null

    private val overlayActiveFrames = arrayOfNulls<FrameLayout>(ACTIVE_SIZE)
    private val overlayActiveColors = arrayOfNulls<View>(ACTIVE_SIZE)
    private val overlayActiveCounts = arrayOfNulls<TextView>(ACTIVE_SIZE)

    // ── Textures ──────────────────────────────────────────────────────────────

    private fun blockBitmap(type: Byte): Bitmap? {
        if (blockBitmapCache.containsKey(type)) return blockBitmapCache[type]
        val name = blockTextureName(type) ?: return null.also { blockBitmapCache[type] = null }
        val assetPath = if (name.startsWith("Items/")) "Cave World/$name" else "Cave World/Tiles/$name"
        return try {
            assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null }.also { blockBitmapCache[type] = it }
    }

    private fun blockDrawable(type: Byte, cornerDp: Float = 4f): Drawable {
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
                setColor(blockColor(type))
                cornerRadius = cornerDp * dp
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        worldId = intent.getStringExtra(EXTRA_WORLD_ID)
        val save = worldId?.let { CaveWorldSaveManager.loadWorld(this, it) }
        val savedState = if (save != null && save.playerY != 0.0) {
            CaveRenderer.SavedState(
                x = save.playerX, y = save.playerY, z = save.playerZ,
                yaw = save.playerYaw, pitch = save.playerPitch,
                inventory = save.inventory, hotbar = save.hotbar,
                playerHp            = save.playerHp,
                playerLevel         = save.playerLevel,
                playerXp            = save.playerXp,
                playerDamage        = save.playerDamage,
                playerFireRate      = save.playerFireRate,
                playerMaxHp         = save.playerMaxHp,
                playerShield           = save.playerShield,
                playerShieldCurrent    = save.playerShieldCurrent,
                playerWeapons          = save.playerWeapons,
                wardStonePositions     = save.wardStonePositions
            )
        } else null

        renderer = CaveRenderer(
            context = this, touch = touch,
            worldSeed = save?.seed ?: System.currentTimeMillis(),
            worldId = worldId, savedState = savedState
        )

        val root = FrameLayout(this)
        setContentView(root)

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
        val btnDayNight   = hud.findViewById<Button>(R.id.cave_btn_day_night)
        val btnCamera     = hud.findViewById<Button>(R.id.cave_btn_camera)

        val btnUp         = hud.findViewById<Button>(R.id.cave_btn_up).also    { vBtnUp    = it }
        val btnDown       = hud.findViewById<Button>(R.id.cave_btn_down).also  { vBtnDown  = it }
        val btnLaser      = hud.findViewById<Button>(R.id.cave_btn_laser).also { vBtnLaser = it }
        val btnPlace      = hud.findViewById<Button>(R.id.cave_btn_place).also { vBtnPlace = it }

        makeCircular(btnUp,    0x55FFFFFF.toInt())
        makeCircular(btnDown,  0x55FFFFFF.toInt())
        makeCircular(btnLaser, 0x66003366.toInt())
        makeCircular(btnPlace, 0x66336600.toInt())
        applyButtonPositions(hud.findViewById(R.id.cave_game_area))

        val miningPanel   = hud.findViewById<LinearLayout>(R.id.cave_mining_panel)
        val tvMiningBlock = hud.findViewById<TextView>(R.id.cave_tv_mining_block)
        val miningBar     = hud.findViewById<ProgressBar>(R.id.cave_mining_progress)
        val hotbarLayout  = hud.findViewById<LinearLayout>(R.id.cave_hotbar)

        buildHotbarUI(hotbarLayout)
        btnBack.setOnClickListener { finish() }
        btnMode.setOnClickListener {
            renderer.pendingMode =
                if (renderer.playerMode == PlayerMode.WALK) PlayerMode.SPECTATOR else PlayerMode.WALK
        }
        btnDayNight.setOnClickListener {
            renderer.toggleDayNight()
            btnDayNight.text = if (renderer.dayNightInverted) "☀" else "🌙"
        }
        btnCamera.setOnClickListener {
            glView.queueEvent {
                renderer.camera.thirdPerson = !renderer.camera.thirdPerson
            }
            btnCamera.alpha = if (renderer.camera.thirdPerson) 1.0f else 0.5f
        }
        btnCamera.alpha = 0.5f
        renderer.modeCallback = { mode ->
            uiHandler.post { applyModeUi(mode, btnMode, btnUp as Button, btnDown, btnLaser, btnPlace) }
        }
        renderer.posCallback    = { pos -> uiHandler.post { tvCoords.text = pos } }
        renderer.miningCallback = { progress, blockType ->
            uiHandler.post {
                if (blockType != null && progress > 0f) {
                    miningPanel.visibility = View.VISIBLE
                    tvMiningBlock.text = blockName(blockType)
                    miningBar.progress = (progress * 100).toInt()
                } else { miningPanel.visibility = View.GONE; miningBar.progress = 0 }
            }
        }
        renderer.inventoryCallback = { inv -> uiHandler.post { onInventoryChanged(inv); saveWorldAsync() } }
        renderer.hotbarCallback    = { slots, selected -> uiHandler.post { updateHotbarUI(slots, selected) } }

        buildHealthBar(root)
        buildXpBar(root)

        renderer.playerHpCallback = { hp, maxHp -> uiHandler.post { updateHealthBar(hp, maxHp) } }
        renderer.shieldCallback   = { cur, max  -> uiHandler.post { updateShieldBar(cur, max) } }
        renderer.xpCallback       = { xp, xpMax, level -> uiHandler.post { updateXpBar(xp, xpMax, level) } }
        renderer.levelUpCallback  = { options -> uiHandler.post { showLevelUpDialog(options, root) } }

        invOverlay = layoutInflater.inflate(R.layout.overlay_cave_inventory, root, false)
        root.addView(invOverlay)
        invOverlay.setOnClickListener { closeInventory() }
        invOverlay.findViewById<View>(R.id.cave_inv_panel).setOnClickListener { /* consomme */ }

        infoSpriteView       = invOverlay.findViewById(R.id.cave_inv_info_sprite)
        infoNameTv           = invOverlay.findViewById(R.id.cave_inv_info_name)
        infoCountTv          = invOverlay.findViewById(R.id.cave_inv_info_count)
        infoDivider          = invOverlay.findViewById(R.id.cave_inv_info_divider)
        infoIngredientsTv    = invOverlay.findViewById(R.id.cave_inv_info_ingredients)
        craftingEmptyTv      = invOverlay.findViewById(R.id.cave_inv_crafting_empty)
        craftingRecyclerView = invOverlay.findViewById(R.id.cave_inv_crafting_recycler)

        val ca = CraftingAdapter(emptyList()).also { craftingAdapter = it }
        craftingRecyclerView?.apply { layoutManager = LinearLayoutManager(this@CaveActivity); adapter = ca }

        invOverlay.findViewById<RecyclerView>(R.id.cave_inv_recycler)
            .layoutManager = GridLayoutManager(this, GRID_COLS)

        buildOverlayActiveBar(invOverlay.findViewById(R.id.cave_inv_active_row))

        applyModeUi(PlayerMode.WALK, btnMode, btnUp as Button, btnDown, btnLaser, btnPlace)

        ambientMusic = CaveAmbientMusic(this, lifecycleScope)
    }

    // ── Barre XP (en haut, pleine largeur) ───────────────────────────────────

    private fun buildXpBar(root: FrameLayout) {
        val dp = resources.displayMetrics.density
        val barH = (6 * dp).toInt()

        val barFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, barH
            ).also { it.gravity = Gravity.TOP }
        }
        val barBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply { setColor(0x88000022.toInt()) }
        }
        val barFg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            // Gradient cyan → bleu vif
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF00FFEE.toInt(), 0xFF0055FF.toInt())
            )
        }
        barFrame.addView(barBg); barFrame.addView(barFg)
        root.addView(barFrame)
        xpBarFg = barFg

        // Texte niveau, centré sur la barre
        val lvTv = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, barH
            ).also { it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
            setTextColor(Color.WHITE)
            textSize = 7f
            gravity = Gravity.CENTER
            text = getString(R.string.cave_player_level, 1)
        }
        root.addView(lvTv)
        xpLevelTv = lvTv

        barFrame.post { xpBarMaxWidth = barFrame.width }
    }

    private fun updateXpBar(xp: Int, xpMax: Int, level: Int) {
        if (xpBarMaxWidth == 0) xpBarFg?.parent?.let { (it as? View)?.post { xpBarMaxWidth = (it as View).width } }
        val frac = xp.toFloat() / xpMax.coerceAtLeast(1)
        xpBarFg?.layoutParams = (xpBarFg?.layoutParams as? FrameLayout.LayoutParams)?.also {
            it.width = (xpBarMaxWidth * frac).toInt().coerceAtLeast(0)
        }
        xpBarFg?.requestLayout()
        xpLevelTv?.text = getString(R.string.cave_player_level, level)
    }

    // ── Dialog Level Up ───────────────────────────────────────────────────────

    private fun showLevelUpDialog(options: List<UpgradeOption>, root: FrameLayout) {
        levelUpOverlay?.let { root.removeView(it) }
        val dp = resources.displayMetrics.density

        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xBB000011.toInt())
        }
        levelUpOverlay = overlay

        // Panel pleine largeur pour que les 3 cartes se répartissent équitablement
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER }
            setPadding((16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt())
        }

        panel.addView(TextView(this).apply {
            text = getString(R.string.cave_levelup_title)
            textSize = 22f; setTextColor(0xFFFFDD44.toInt()); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        panel.addView(TextView(this).apply {
            text = getString(R.string.cave_levelup_subtitle)
            textSize = 13f; setTextColor(0xAAFFFFFF.toInt()); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (16 * dp).toInt() }
        })

        // Cartes en ligne, chacune avec weight=1 pour occuper 1/3 de la largeur
        val cardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        for (opt in options) {
            cardsRow.addView(buildUpgradeCard(opt, dp) {
                renderer.applyUpgrade(opt)
                root.removeView(overlay)
                levelUpOverlay = null
            })
        }
        panel.addView(cardsRow)
        overlay.addView(panel)
        root.addView(overlay)
    }

    private fun buildUpgradeCard(opt: UpgradeOption, dp: Float, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // weight=1 + width=0 : chaque carte prend exactement 1/3 de la ligne
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.setMargins((6 * dp).toInt(), 0, (6 * dp).toInt(), 0) }
            setPadding((10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (opt.isRare) 0xCC1A0033.toInt() else 0xCC001122.toInt())
                cornerRadius = 10 * dp
                setStroke(
                    (2 * dp).toInt(),
                    if (opt.isRare) 0xFFCC44FF.toInt() else 0xFF0088FF.toInt()
                )
            }
            setOnClickListener { onClick() }
        }

        // Icône / emoji
        val icon = upgradeIcon(opt.type)
        card.addView(TextView(this).apply {
            text = icon; textSize = 28f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * dp).toInt() }
        })

        // Badge RARE
        if (opt.isRare) {
            card.addView(TextView(this).apply {
                text = getString(R.string.cave_levelup_rare)
                textSize = 9f; setTextColor(0xFFCC44FF.toInt()); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (4 * dp).toInt() }
            })
        }

        // Nom
        card.addView(TextView(this).apply {
            text = upgradeName(opt.type); textSize = 12f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (4 * dp).toInt() }
        })

        // Description
        card.addView(TextView(this).apply {
            text = upgradeDesc(opt.type); textSize = 10f
            setTextColor(0xAAFFFFFF.toInt()); gravity = Gravity.CENTER
        })

        return card
    }

    private fun upgradeIcon(type: UpgradeType): String = when (type) {
        UpgradeType.DAMAGE         -> "⚔"
        UpgradeType.FIRE_RATE      -> "⚡"
        UpgradeType.MAX_HP         -> "❤"
        UpgradeType.SHIELD         -> "🛡"
        UpgradeType.WEAPON_WHITE_SWIRL   -> "○"
        UpgradeType.WEAPON_BLUE_SQUARE   -> "■"
        UpgradeType.WEAPON_BLUE_SWIRL    -> "◎"
        UpgradeType.WEAPON_ORANGE_SQUARE -> "◆"
        UpgradeType.WEAPON_ORANGE_SWIRL  -> "✦"
        UpgradeType.WEAPON_RED_SQUARE    -> "▲"
        UpgradeType.WEAPON_RED_SWIRL     -> "✿"
    }

    private fun upgradeName(type: UpgradeType): String = getString(when (type) {
        UpgradeType.DAMAGE                               -> R.string.cave_upgrade_damage_name
        UpgradeType.FIRE_RATE                            -> R.string.cave_upgrade_fire_rate_name
        UpgradeType.MAX_HP                               -> R.string.cave_upgrade_max_hp_name
        UpgradeType.SHIELD                               -> R.string.cave_upgrade_shield_name
        UpgradeType.WEAPON_WHITE_SWIRL,
        UpgradeType.WEAPON_BLUE_SQUARE, UpgradeType.WEAPON_BLUE_SWIRL,
        UpgradeType.WEAPON_ORANGE_SQUARE, UpgradeType.WEAPON_ORANGE_SWIRL,
        UpgradeType.WEAPON_RED_SQUARE, UpgradeType.WEAPON_RED_SWIRL -> R.string.cave_upgrade_weapon_name
    })

    private fun upgradeDesc(type: UpgradeType): String = getString(when (type) {
        UpgradeType.DAMAGE                               -> R.string.cave_upgrade_damage_desc
        UpgradeType.FIRE_RATE                            -> R.string.cave_upgrade_fire_rate_desc
        UpgradeType.MAX_HP                               -> R.string.cave_upgrade_max_hp_desc
        UpgradeType.SHIELD                               -> R.string.cave_upgrade_shield_desc
        UpgradeType.WEAPON_WHITE_SWIRL,
        UpgradeType.WEAPON_BLUE_SQUARE, UpgradeType.WEAPON_BLUE_SWIRL,
        UpgradeType.WEAPON_ORANGE_SQUARE, UpgradeType.WEAPON_ORANGE_SWIRL,
        UpgradeType.WEAPON_RED_SQUARE, UpgradeType.WEAPON_RED_SWIRL -> R.string.cave_upgrade_weapon_desc
    })

    // ── HP / Bouclier ─────────────────────────────────────────────────────────

    private fun buildHealthBar(root: FrameLayout) {
        val dp = resources.displayMetrics.density
        val bW = (80 * dp).toInt(); val bH = (10 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                .also { it.gravity = Gravity.TOP or Gravity.START; it.setMargins((12 * dp).toInt(), (18 * dp).toInt(), 0, 0) }
            setPadding((6 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(0x99000000.toInt()); cornerRadius = 6 * dp }
        }

        // Ligne HP
        val hpRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val heartTv = TextView(this).apply { text = "❤"; textSize = 12f; setTextColor(0xFFFF4444.toInt()); setPadding(0, 0, (4 * dp).toInt(), 0) }
        val barFrame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(bW, bH).also { it.gravity = Gravity.CENTER_VERTICAL } }
        val barBg = View(this).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0x88440000.toInt()); cornerRadius = 3 * dp } }
        val barFg = View(this).apply { layoutParams = FrameLayout.LayoutParams(bW, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0xFF22CC44.toInt()); cornerRadius = 3 * dp } }
        barFrame.addView(barBg); barFrame.addView(barFg)
        val tv = TextView(this).apply { text = "20/20"; textSize = 9f; setTextColor(Color.WHITE); setPadding((4 * dp).toInt(), 0, 0, 0) }
        hpRow.addView(heartTv); hpRow.addView(barFrame); hpRow.addView(tv)
        container.addView(hpRow)
        hpBarFg = barFg; hpText = tv; hpBarMaxWidth = bW

        // Ligne Bouclier (cachée par défaut)
        val shRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (2 * dp).toInt() }
        }
        val shIconTv = TextView(this).apply { text = "🛡"; textSize = 10f; setPadding(0, 0, (3 * dp).toInt(), 0) }
        val shFrame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(bW, bH).also { it.gravity = Gravity.CENTER_VERTICAL } }
        val shBg = View(this).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0x88002244.toInt()); cornerRadius = 3 * dp } }
        val shFg = View(this).apply { layoutParams = FrameLayout.LayoutParams(bW, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0xFF0099FF.toInt()); cornerRadius = 3 * dp } }
        shFrame.addView(shBg); shFrame.addView(shFg)
        shRow.addView(shIconTv); shRow.addView(shFrame)
        container.addView(shRow)
        shieldBarFg = shFg; shieldContainer = shRow

        root.addView(container)
    }

    private fun updateHealthBar(hp: Int, maxHp: Int) {
        val frac = hp.toFloat() / maxHp.coerceAtLeast(1)
        hpBarFg?.layoutParams = (hpBarFg?.layoutParams as? FrameLayout.LayoutParams)?.also { it.width = (hpBarMaxWidth * frac).toInt().coerceAtLeast(0) }
        hpBarFg?.requestLayout(); hpText?.text = "$hp/$maxHp"
        (hpBarFg?.background as? GradientDrawable)?.setColor(when { frac > 0.6f -> 0xFF22CC44.toInt(); frac > 0.3f -> 0xFFDDAA00.toInt(); else -> 0xFFCC2222.toInt() })
    }

    private fun updateShieldBar(current: Int, max: Int) {
        if (max <= 0) { shieldContainer?.visibility = View.GONE; return }
        shieldContainer?.visibility = View.VISIBLE
        val frac = current.toFloat() / max.coerceAtLeast(1)
        shieldBarFg?.layoutParams = (shieldBarFg?.layoutParams as? FrameLayout.LayoutParams)?.also { it.width = (hpBarMaxWidth * frac).toInt().coerceAtLeast(0) }
        shieldBarFg?.requestLayout()
    }

    // ── Gestion des slots ─────────────────────────────────────────────────────

    private fun initInvSlots() {
        invSlots.clear()
        val hotbarTypes = (0 until ACTIVE_SIZE).mapNotNull { i ->
            renderer.hotbar[i]?.takeIf { (renderer.inventory[it] ?: 0) > 0 }
        }.toSet()
        val gridTypes = renderer.inventory
            .filter { (t, c) -> c > 0 && t !in hotbarTypes }
            .keys.sortedBy { it }
        for (t in gridTypes) invSlots.add(t)
        repeat(EMPTY_BUFFER) { invSlots.add(null) }
        for (i in 0 until ACTIVE_SIZE) {
            val t = renderer.hotbar[i]
            invSlots.add(if (t != null && (renderer.inventory[t] ?: 0) > 0) t else null)
        }
        invSlotsReady = true
        syncHotbar()
    }

    private fun addNewType(type: Byte) {
        val base = hotbarBase()
        val emptyIdx = (0 until base).firstOrNull { invSlots[it] == null }
        if (emptyIdx != null) invSlots[emptyIdx] = type else invSlots.add(base, type)
        invSlots.add(base, null)
    }

    private fun syncHotbar() {
        val base = hotbarBase()
        for (i in 0 until ACTIVE_SIZE) renderer.hotbar[i] = invSlots.getOrNull(base + i)
        renderer.hotbarCallback?.invoke(renderer.hotbar.copyOf(), renderer.selectedSlot)
    }

    private fun swapSlots(a: Int, b: Int) {
        if (a == b || a !in invSlots.indices || b !in invSlots.indices) return
        val tmp = invSlots[a]; invSlots[a] = invSlots[b]; invSlots[b] = tmp
        syncHotbar(); saveWorldAsync()
    }

    // ── Inventaire changed ────────────────────────────────────────────────────

    private fun onInventoryChanged(inv: Map<Byte, Int>) {
        for (i in 0 until ACTIVE_SIZE) {
            val t = renderer.hotbar[i] ?: continue
            if ((inv[t] ?: 0) <= 0) renderer.hotbar[i] = null
        }
        if (invSlotsReady) {
            for (i in invSlots.indices) {
                val t = invSlots[i] ?: continue
                if ((inv[t] ?: 0) <= 0) invSlots[i] = null
            }
            val existing = invSlots.filterNotNull().toSet()
            for ((type, count) in inv) if (count > 0 && type !in existing) addNewType(type)
            if (selectedSlotIdx >= invSlots.size) selectedSlotIdx = -1
            syncHotbar()
            if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) {
                refreshGridAdapter(); updateActiveBarOverlay(); updateInfoPanel(); updateCraftingList()
            }
        } else {
            renderer.hotbarCallback?.invoke(renderer.hotbar.copyOf(), renderer.selectedSlot)
        }
    }

    // ── Inventaire open/close ─────────────────────────────────────────────────

    private fun openInventory() {
        if (!invSlotsReady) initInvSlots()

        val w = (resources.displayMetrics.widthPixels  * 0.82f).toInt()
        val h = (resources.displayMetrics.heightPixels * 0.84f).toInt()
        invOverlay.findViewById<LinearLayout>(R.id.cave_inv_panel).layoutParams =
            FrameLayout.LayoutParams(w, h).also { it.gravity = Gravity.CENTER }

        invOverlay.visibility = View.VISIBLE

        invOverlay.post {
            if (invOverlay.visibility == View.VISIBLE) {
                refreshGridAdapter()
                updateActiveBarOverlay()
                updateInfoPanel()
                updateCraftingList()
            }
        }
    }

    private fun closeInventory() {
        selectedSlotIdx = -1
        selectedRecipe  = null
        invOverlay.visibility = View.GONE
    }

    private fun refreshGridAdapter() {
        val base = hotbarBase()
        val gridList: List<Byte?> = if (base > 0) invSlots.subList(0, base).toList() else emptyList()
        val recycler = invOverlay.findViewById<RecyclerView>(R.id.cave_inv_recycler)
        if (invGridAdapter == null) {
            val adapter = InvGridAdapter(gridList)
            invGridAdapter = adapter
            recycler.adapter = adapter
        } else {
            invGridAdapter!!.items = gridList
            invGridAdapter!!.notifyDataSetChanged()
        }
    }

    // ── Panneau info ──────────────────────────────────────────────────────────

    private fun updateInfoPanel() {
        val recipe = selectedRecipe
        val dp = resources.displayMetrics.density

        if (recipe != null) {
            infoSpriteView?.background = blockDrawable(recipe.output, 6f)
            infoNameTv?.text  = blockName(recipe.output)
            infoCountTv?.text = "×${recipe.outputCount}"
            val ingredientsText = recipe.ingredients.joinToString("\n") { (t, n) -> "${blockName(t)} ×$n" }
            infoIngredientsTv?.text = ingredientsText
            infoDivider?.visibility       = View.VISIBLE
            infoIngredientsTv?.visibility = View.VISIBLE
        } else {
            val type = selectedType()
            infoDivider?.visibility       = View.GONE
            infoIngredientsTv?.visibility = View.GONE
            if (type == null) {
                infoSpriteView?.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; setColor(0x33FFFFFF); cornerRadius = 6 * dp
                }
                infoNameTv?.text  = getString(R.string.cave_inv_info_empty)
                infoCountTv?.text = ""
            } else {
                infoSpriteView?.background = blockDrawable(type, 6f)
                infoNameTv?.text  = blockName(type)
                infoCountTv?.text = "×${renderer.inventory[type] ?: 0}"
            }
        }
    }

    // ── Panneau crafting ──────────────────────────────────────────────────────

    private fun updateCraftingList() {
        val type = selectedType()
        val recipes = if (type == null) emptyList()
                      else RECIPES.filter { r -> r.ingredients.any { it.first == type } }
        craftingAdapter?.recipes = recipes
        craftingAdapter?.notifyDataSetChanged()
        craftingEmptyTv?.visibility      = if (recipes.isEmpty()) View.VISIBLE else View.GONE
        craftingRecyclerView?.visibility = if (recipes.isEmpty()) View.GONE    else View.VISIBLE
    }

    private fun doCraft(recipe: CraftingRecipe) {
        if (!recipe.canCraft(renderer.inventory)) return
        for ((type, need) in recipe.ingredients) {
            val after = (renderer.inventory[type] ?: 0) - need
            if (after <= 0) {
                renderer.inventory.remove(type)
                for (i in invSlots.indices) { if (invSlots[i] == type) { invSlots[i] = null; break } }
            } else renderer.inventory[type] = after
        }
        val outType = recipe.output
        val outCurrent = renderer.inventory[outType] ?: 0
        renderer.inventory[outType] = outCurrent + recipe.outputCount
        if (outCurrent == 0 && outType !in invSlots.filterNotNull()) addNewType(outType)
        if (selectedSlotIdx in invSlots.indices && invSlots[selectedSlotIdx] == null) selectedSlotIdx = -1
        syncHotbar(); refreshGridAdapter(); updateActiveBarOverlay(); updateInfoPanel(); updateCraftingList()
        saveWorldAsync()
    }

    // ── Barre active dans l'overlay ───────────────────────────────────────────

    private fun buildOverlayActiveBar(container: LinearLayout) {
        val dp = resources.displayMetrics.density
        repeat(ACTIVE_SIZE) { i ->
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .also { it.setMargins(2, 2, 2, 2) }
                background = overlaySlotDrawable(false)
            }
            val colorDot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
                    .also { it.gravity = Gravity.CENTER }
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            val countTv = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.gravity = Gravity.BOTTOM or Gravity.END }
                setTextColor(Color.WHITE); textSize = 11f
                setPadding(0, 0, (3 * dp).toInt(), (2 * dp).toInt())
            }
            frame.addView(colorDot); frame.addView(countTv)
            container.addView(frame)
            overlayActiveFrames[i] = frame; overlayActiveColors[i] = colorDot; overlayActiveCounts[i] = countTv
            frame.setOnClickListener { onOverlayActiveSlotClick(i) }
        }
    }

    private fun onOverlayActiveSlotClick(i: Int) {
        val invIdx = hotbarBase() + i
        when {
            selectedSlotIdx == invIdx -> {
                selectedSlotIdx = -1; updateActiveSlotHighlights(); updateInfoPanel(); updateCraftingList()
            }
            selectedSlotIdx >= 0 -> {
                swapSlots(selectedSlotIdx, invIdx); selectedSlotIdx = -1
                refreshGridAdapter(); updateActiveBarOverlay(); updateInfoPanel(); updateCraftingList()
            }
            invSlots.getOrNull(invIdx) != null -> {
                selectedSlotIdx = invIdx; selectedRecipe = null
                updateActiveSlotHighlights(); updateInfoPanel(); updateCraftingList()
            }
        }
    }

    private fun updateActiveBarOverlay() {
        val base = hotbarBase()
        val dp = resources.displayMetrics.density
        for (i in 0 until ACTIVE_SIZE) {
            val invIdx = base + i
            val type  = invSlots.getOrNull(invIdx)
            val count = if (type != null) renderer.inventory[type] ?: 0 else 0
            val eff   = if (count > 0) type else null
            val isSel = invIdx == selectedSlotIdx
            overlayActiveFrames[i]?.background = overlaySlotDrawable(isSel)
            overlayActiveColors[i]?.background = if (eff != null) blockDrawable(eff, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }
            overlayActiveCounts[i]?.text = if (eff != null) count.toString() else ""
        }
    }

    private fun updateActiveSlotHighlights() {
        for (i in 0 until ACTIVE_SIZE) {
            val isSel = (hotbarBase() + i) == selectedSlotIdx
            overlayActiveFrames[i]?.background = overlaySlotDrawable(isSel)
        }
    }

    private fun overlaySlotDrawable(selected: Boolean): GradientDrawable {
        val dp = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (selected) 0x33FFDD00.toInt() else 0x66FFFFFF.toInt())
            setStroke(if (selected) (2 * dp).toInt() else (1 * dp).toInt(),
                if (selected) 0xFFFFDD00.toInt() else 0xAAFFFFFF.toInt())
            cornerRadius = 4 * dp
        }
    }

    // ── Adapter grille inventaire ─────────────────────────────────────────────

    private inner class InvGridAdapter(var items: List<Byte?>) : RecyclerView.Adapter<InvGridAdapter.ItemVH>() {

        inner class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
            val colorView: View    = v.findViewById(R.id.cave_inv_color)
            val countTv:  TextView = v.findViewById(R.id.cave_inv_count)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
            val dp       = resources.displayMetrics.density
            val cellSize = if (parent.width > 0) parent.width / GRID_COLS else (52 * dp).toInt()
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cave_inv_block, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(cellSize, cellSize)
            view.setPadding(2, 2, 2, 2)
            return ItemVH(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ItemVH, position: Int) {
            val type  = items.getOrNull(position)
            val count = if (type != null) renderer.inventory[type] ?: 0 else 0
            val isSel = position == selectedSlotIdx
            val dp    = resources.displayMetrics.density

            holder.itemView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(when { isSel -> 0x33FFDD00.toInt(); type != null -> 0x55FFFFFF.toInt(); else -> 0x44FFFFFF.toInt() })
                setStroke(if (isSel) (2 * dp).toInt() else (1 * dp).toInt(),
                    if (isSel) 0xFFFFDD00.toInt() else 0x88FFFFFF.toInt())
                cornerRadius = 4 * dp
            }

            holder.colorView.background = if (type != null) blockDrawable(type, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }

            holder.countTv.text     = if (type != null) count.toString() else ""
            holder.countTv.textSize = 11f

            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                when {
                    selectedSlotIdx == pos -> {
                        selectedSlotIdx = -1; notifyDataSetChanged(); updateInfoPanel(); updateCraftingList()
                    }
                    selectedSlotIdx >= 0 -> {
                        swapSlots(selectedSlotIdx, pos); selectedSlotIdx = -1
                        refreshGridAdapter(); updateActiveBarOverlay(); updateInfoPanel(); updateCraftingList()
                    }
                    type != null -> {
                        selectedSlotIdx = pos; selectedRecipe = null
                        notifyDataSetChanged(); updateInfoPanel(); updateCraftingList()
                    }
                }
            }
        }
    }

    // ── Adapter crafting ──────────────────────────────────────────────────────

    private inner class CraftingAdapter(var recipes: List<CraftingRecipe>)
        : RecyclerView.Adapter<CraftingAdapter.VH>() {

        inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = resources.displayMetrics.density
            val ll = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                val m = (6 * dp).toInt(); setPadding(m, m, m, m)
            }
            return VH(ll)
        }

        override fun getItemCount() = recipes.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val recipe  = recipes[position]
            val dp      = resources.displayMetrics.density
            val isSelRec = recipe == selectedRecipe
            holder.root.removeAllViews()
            holder.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (isSelRec) 0x33FFDD00.toInt() else 0x22FFFFFF)
                cornerRadius = 6 * dp
                setStroke(if (isSelRec) (2 * dp).toInt() else 1, if (isSelRec) 0xFFFFDD00.toInt() else 0x33FFFFFF)
            }

            val row = LinearLayout(holder.root.context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            recipe.ingredients.forEachIndexed { idx, (type, need) ->
                if (idx > 0) row.addView(TextView(holder.root.context).apply {
                    text = "+"; textSize = 12f; setTextColor(0x88FFFFFF.toInt())
                    setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                })
                row.addView(blockLabel(holder.root.context, type, need, dp))
            }
            row.addView(TextView(holder.root.context).apply {
                text = "→"; textSize = 14f; setTextColor(0xAAFFFFFF.toInt())
                setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            })
            row.addView(blockLabel(holder.root.context, recipe.output, recipe.outputCount, dp))
            holder.root.addView(row)

            val canCraft = recipe.canCraft(renderer.inventory)
            holder.root.addView(Button(holder.root.context).apply {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (32 * dp).toInt())
                lp.topMargin = (6 * dp).toInt(); layoutParams = lp
                text = getString(R.string.cave_inv_craft_btn); textSize = 11f
                isEnabled = canCraft; alpha = if (canCraft) 1f else 0.4f
                setBackgroundColor(if (canCraft) 0xFF336633.toInt() else 0x44FFFFFF.toInt())
                setTextColor(Color.WHITE); setPadding(0, 0, 0, 0)
                setOnClickListener { doCraft(recipe) }
            })

            holder.root.setOnClickListener {
                selectedRecipe = if (selectedRecipe == recipe) null else recipe
                notifyDataSetChanged()
                updateInfoPanel()
            }
        }

        private fun blockLabel(ctx: Context, type: Byte, count: Int, dp: Float): LinearLayout =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                val sq = (44 * dp).toInt()
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(sq, sq)
                    background = blockDrawable(type, 4f)
                })
                addView(TextView(ctx).apply {
                    text = "×$count"; textSize = 12f; setTextColor(0xCCFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(sq, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
            }
    }

    // ── Hotbar HUD ────────────────────────────────────────────────────────────

    private fun buildHotbarUI(container: LinearLayout) {
        val dp = resources.displayMetrics.density; val sz = (52 * dp).toInt()
        container.gravity = Gravity.CENTER
        repeat(ACTIVE_SIZE) { i ->
            val slot = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(sz, sz).also { it.setMargins(2, 2, 2, 2) }
                background = slotDrawable(null, false); setOnClickListener { renderer.selectSlot(i) }
            }
            val colorDot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).also { it.gravity = Gravity.CENTER }
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            val countTv = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.BOTTOM or Gravity.END }
                setTextColor(Color.WHITE); textSize = 9f; setPadding(0, 0, (2 * dp).toInt(), (1 * dp).toInt())
            }
            slot.addView(colorDot); slot.addView(countTv); container.addView(slot)
            slotViews[i] = slot; slotColors[i] = colorDot; slotCounts[i] = countTv
        }
        container.addView(Button(this).apply {
            layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), sz).also { it.setMargins(4, 2, 2, 2) }
            text = "🎒"; textSize = 18f; setBackgroundColor(0x55FFFFFF); setTextColor(Color.WHITE)
            setOnClickListener { openInventory() }
        })
    }

    private fun updateHotbarUI(slots: Array<Byte?>, selected: Int) {
        val dp = resources.displayMetrics.density
        slots.forEachIndexed { i, type ->
            val count = if (type != null) renderer.inventory[type] ?: 0 else 0
            val eff   = if (count > 0) type else null
            slotViews[i]?.background = slotDrawable(eff, i == selected)
            slotColors[i]?.background = if (eff != null) blockDrawable(eff, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }
            slotCounts[i]?.text = if (eff != null) count.toString() else ""
        }
    }

    private fun slotDrawable(type: Byte?, selected: Boolean): GradientDrawable {
        val dp = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (type != null) 0x33FFFFFF else 0x22FFFFFF)
            setStroke(if (selected) (2 * dp).toInt() else (1 * dp).toInt(), if (selected) Color.WHITE else 0x55FFFFFF)
            cornerRadius = 4 * dp
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveWorldAsync() {
        val id = worldId ?: return
        val stats = renderer.playerStats
        val em    = renderer.enemyManager
        val snap = CaveWorldSave(
            id = id, name = "", seed = 0L, createdAt = 0L,
            lastPlayedAt = System.currentTimeMillis(),
            playerX = renderer.camera.playerX, playerY = renderer.camera.playerY, playerZ = renderer.camera.playerZ,
            playerYaw = renderer.camera.yaw, playerPitch = renderer.camera.pitch,
            inventory = renderer.inventory.toMap(), hotbar = renderer.hotbar.map { it },
            playerHp            = em.playerHp,
            playerLevel         = stats.level,
            playerXp            = stats.xp,
            playerDamage        = stats.damage,
            playerFireRate      = stats.fireRate,
            playerMaxHp         = stats.maxHp,
            playerShield        = stats.shield,
            playerShieldCurrent = em.playerShieldCurrent,
            playerWeapons       = stats.weapons.map { "${it.color.name}_${it.variant.name}" },
            wardStonePositions  = renderer.enemyManager.wardStoneZones.toList()
        )
        lifecycleScope.launch(Dispatchers.IO) { CaveWorldSaveManager.updateFields(this@CaveActivity, snap) }
    }

    private fun saveWorld() = saveWorldAsync()

    // ── Noms de blocs ─────────────────────────────────────────────────────────

    private fun blockName(type: Byte): String = when (type) {
        STONE -> getString(R.string.cave_block_stone); GRANITE -> getString(R.string.cave_block_granite)
        QUARTZ -> getString(R.string.cave_block_quartz); COAL -> getString(R.string.cave_block_coal)
        GOLD -> getString(R.string.cave_block_gold); CRYSTAL -> getString(R.string.cave_block_crystal)
        DIRT -> getString(R.string.cave_block_dirt); GRAVEL -> getString(R.string.cave_block_gravel)
        IRON -> getString(R.string.cave_block_iron); SILVER -> getString(R.string.cave_block_silver)
        RUBY -> getString(R.string.cave_block_ruby); LAVA -> getString(R.string.cave_block_lava)
        FURNACE -> getString(R.string.cave_block_furnace); EMERALD -> getString(R.string.cave_block_emerald)
        COPPER -> getString(R.string.cave_block_copper); GRASS -> getString(R.string.cave_block_grass)
        WOOD -> getString(R.string.cave_block_wood); LEAVES -> getString(R.string.cave_block_leaves)
        SAND -> getString(R.string.cave_block_sand); REDSAND -> getString(R.string.cave_block_redsand)
        ICE -> getString(R.string.cave_block_ice); SNOW -> getString(R.string.cave_block_snow)
        BRICK_RED -> getString(R.string.cave_block_brick_red); ROCK -> getString(R.string.cave_block_rock)
        ROCK_MOSS -> getString(R.string.cave_block_rock_moss)
        MUSHROOM_RED -> getString(R.string.cave_block_mushroom_red)
        MUSHROOM_BROWN -> getString(R.string.cave_block_mushroom_brown)
        MUSHROOM_TAN -> getString(R.string.cave_block_mushroom_tan)
        TORCH -> getString(R.string.cave_block_torch)
        PLANK -> getString(R.string.cave_block_plank)
        WOOD_PLANK_WHITE -> getString(R.string.cave_block_wood_plank_white)
        WARD_STONE   -> getString(R.string.cave_block_ward_stone)
        BUCKET_EMPTY -> getString(R.string.cave_item_bucket_empty)
        BUCKET_FULL  -> getString(R.string.cave_item_bucket_full)
        else -> "?"
    }

    // ── Boutons action : forme et position ───────────────────────────────────

    private fun makeCircular(btn: Button, bgColor: Int) {
        btn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }
    }

    private fun applyButtonPositions(gameArea: FrameLayout) {
        gameArea.doOnLayout {
            val w = gameArea.width.toFloat()
            val h = gameArea.height.toFloat()
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
        when (mode) {
            PlayerMode.SPECTATOR -> { btnMode.text = getString(R.string.cave_mode_spectator); btnUp.text = "▲"; btnDown.visibility = View.VISIBLE; btnLaser.visibility = View.GONE; btnPlace.visibility = View.GONE }
            PlayerMode.WALK      -> { btnMode.text = getString(R.string.cave_mode_walk); btnUp.text = getString(R.string.cave_jump); btnDown.visibility = View.GONE; btnLaser.visibility = View.VISIBLE; btnPlace.visibility = View.VISIBLE }
        }
    }

    // ── Manette ───────────────────────────────────────────────────────────────

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean =
        gamepad.onGenericMotion(event) || super.onGenericMotionEvent(event)

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val consumed = when (event.action) {
            android.view.KeyEvent.ACTION_DOWN -> gamepad.onKeyDown(event.keyCode)
            android.view.KeyEvent.ACTION_UP   -> gamepad.onKeyUp(event.keyCode)
            else -> false
        }
        return consumed || super.dispatchKeyEvent(event)
    }

    // ── Touch multipoint ─────────────────────────────────────────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) return super.dispatchTouchEvent(ev)
        if (levelUpOverlay != null) return super.dispatchTouchEvent(ev)
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

    override fun onResume()  { super.onResume();  glView.onResume(); ambientMusic?.resume() }
    override fun onPause()   { super.onPause();   glView.onPause();  ambientMusic?.pause(); saveWorld() }
    override fun onDestroy() {
        super.onDestroy(); renderer.destroy(); ambientMusic?.destroy()
        blockBitmapCache.values.forEach { it?.recycle() }
        blockBitmapCache.clear()
    }

    private fun View.isHitOnScreen(x: Float, y: Float): Boolean {
        val loc = IntArray(2); getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }
}
