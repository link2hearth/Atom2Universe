package com.Atom2Universe.app.games.caves

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CaveActivity : ThemedActivity() {

    companion object {
        const val EXTRA_WORLD_ID = "cave_world_id"

        fun blockColor(type: Byte): Int = when (type) {
            STONE   -> 0xFF808080.toInt()
            GRANITE -> 0xFF9B7B6A.toInt()
            QUARTZ  -> 0xFFE8E4D0.toInt()
            COAL    -> 0xFF2C2C2C.toInt()
            GOLD    -> 0xFFFFD700.toInt()
            CRYSTAL -> 0xFF88FFFF.toInt()
            DIRT    -> 0xFF8B5E3C.toInt()
            GRAVEL  -> 0xFF9E9E9E.toInt()
            IRON    -> 0xFFB87333.toInt()
            SILVER  -> 0xFFC0C0C0.toInt()
            RUBY    -> 0xFFCC1111.toInt()
            LAVA    -> 0xFFFF4500.toInt()
            FURNACE -> 0xFF555555.toInt()
            EMERALD -> 0xFF50C878.toInt()
            COPPER  -> 0xFFB87333.toInt()
            GRASS   -> 0xFF55AA33.toInt()
            WOOD    -> 0xFF8B6914.toInt()
            LEAVES  -> 0xFF2D6A1F.toInt()
            SAND    -> 0xFFD4C06A.toInt()
            REDSAND -> 0xFFCC6633.toInt()
            else    -> 0xFF444444.toInt()
        }
    }

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: CaveRenderer
    private val touch = TouchController()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var worldId: String? = null

    private var ptrUp    = -1; private var ptrDown  = -1
    private var ptrLaser = -1; private var ptrPlace = -1
    private var vBtnUp:    View? = null; private var vBtnDown:  View? = null
    private var vBtnLaser: View? = null; private var vBtnPlace: View? = null

    // Hotbar UI — 9 slots + 1 bouton sac
    private val slotViews   = arrayOfNulls<FrameLayout>(9)
    private val slotCounts  = arrayOfNulls<TextView>(9)
    private val slotColors  = arrayOfNulls<View>(9)
    private lateinit var invOverlay: View

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        worldId = intent.getStringExtra(EXTRA_WORLD_ID)
        val save = worldId?.let { CaveWorldSaveManager.loadWorld(this, it) }
        val savedState = if (save != null && save.playerY != 0f) {
            CaveRenderer.SavedState(
                x = save.playerX, y = save.playerY, z = save.playerZ,
                yaw = save.playerYaw, pitch = save.playerPitch,
                inventory = save.inventory,
                hotbar = save.hotbar
            )
        } else null

        renderer = CaveRenderer(
            context = this, touch = touch,
            worldSeed = save?.seed ?: System.currentTimeMillis(),
            worldId = worldId,
            savedState = savedState
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
        val btnUp         = hud.findViewById<Button>(R.id.cave_btn_up).also    { vBtnUp    = it }
        val btnDown       = hud.findViewById<Button>(R.id.cave_btn_down).also  { vBtnDown  = it }
        val btnLaser      = hud.findViewById<Button>(R.id.cave_btn_laser).also { vBtnLaser = it }
        val btnPlace      = hud.findViewById<Button>(R.id.cave_btn_place).also { vBtnPlace = it }
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

        renderer.modeCallback = { mode ->
            uiHandler.post { applyModeUi(mode, btnMode, btnUp as Button, btnDown, btnLaser, btnPlace) }
        }
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
        renderer.inventoryCallback = { _ ->
            uiHandler.post { saveWorldAsync() }
        }
        renderer.hotbarCallback = { slots, selected ->
            uiHandler.post { updateHotbarUI(slots, selected) }
        }

        // Overlay inventaire complet
        invOverlay = layoutInflater.inflate(R.layout.overlay_cave_inventory, root, false)
        root.addView(invOverlay)
        invOverlay.findViewById<View>(R.id.cave_inv_close).setOnClickListener {
            invOverlay.visibility = View.GONE
        }
        invOverlay.setOnClickListener { invOverlay.visibility = View.GONE }

        applyModeUi(PlayerMode.WALK, btnMode, btnUp as Button, btnDown, btnLaser, btnPlace)
    }

    // ── Hotbar UI ─────────────────────────────────────────────────────────────

    private fun buildHotbarUI(container: LinearLayout) {
        val dp = resources.displayMetrics.density
        val slotSizePx = (52 * dp).toInt()

        repeat(9) { i ->
            val slot = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, slotSizePx, 1f).also {
                    it.setMargins(2, 2, 2, 2)
                }
                background = slotDrawable(null, false)
                setOnClickListener { renderer.selectSlot(i) }
            }
            val colorDot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (28 * dp).toInt(), (28 * dp).toInt()
                ).also { it.gravity = android.view.Gravity.CENTER }
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            val countTv = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END }
                setTextColor(Color.WHITE)
                textSize = 9f
                setPadding(0, 0, (2 * dp).toInt(), (1 * dp).toInt())
            }
            slot.addView(colorDot)
            slot.addView(countTv)
            container.addView(slot)
            slotViews[i] = slot
            slotColors[i] = colorDot
            slotCounts[i] = countTv
        }

        // Bouton sac
        val bag = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), slotSizePx).also {
                it.setMargins(4, 2, 2, 2)
            }
            text = "🎒"
            textSize = 18f
            setBackgroundColor(0x55FFFFFF)
            setTextColor(Color.WHITE)
            setOnClickListener { openInventory() }
        }
        container.addView(bag)
    }

    private fun updateHotbarUI(slots: Array<Byte?>, selected: Int) {
        slots.forEachIndexed { i, type ->
            val count = if (type != null) renderer.inventory[type] ?: 0 else 0
            slotViews[i]?.background = slotDrawable(type, i == selected)
            (slotColors[i]?.background as? GradientDrawable)?.setColor(
                if (type != null) blockColor(type) else Color.TRANSPARENT
            )
            slotCounts[i]?.text = if (type != null && count > 0) count.toString() else ""
        }
    }

    private fun slotDrawable(type: Byte?, selected: Boolean): GradientDrawable {
        val dp = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (type != null) 0x33FFFFFF else 0x22FFFFFF)
            setStroke(
                if (selected) (2 * dp).toInt() else (1 * dp).toInt(),
                if (selected) Color.WHITE else 0x55FFFFFF
            )
            cornerRadius = 4 * dp
        }
    }

    // ── Overlay inventaire complet ────────────────────────────────────────────

    private fun openInventory() {
        val recycler = invOverlay.findViewById<RecyclerView>(R.id.cave_inv_recycler)
        val empty    = invOverlay.findViewById<View>(R.id.cave_inv_empty)

        val items = renderer.inventory.entries.filter { it.value > 0 }.toList()
        empty.visibility    = if (items.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (items.isEmpty()) View.GONE   else View.VISIBLE

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = InvAdapter(items)
        invOverlay.visibility = View.VISIBLE
    }

    private inner class InvAdapter(
        private val items: List<Map.Entry<Byte, Int>>
    ) : RecyclerView.Adapter<InvAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val color: View     = v.findViewById(R.id.cave_inv_color)
            val name: TextView  = v.findViewById(R.id.cave_inv_name)
            val count: TextView = v.findViewById(R.id.cave_inv_count)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_cave_inv_block, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (type, count) = items[position]
            holder.color.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(blockColor(type))
                cornerRadius = 4 * resources.displayMetrics.density
            }
            holder.name.text  = blockName(type)
            holder.count.text = "×$count"
            holder.itemView.setOnClickListener {
                renderer.hotbar[renderer.selectedSlot] = type
                renderer.hotbarCallback?.invoke(renderer.hotbar.copyOf(), renderer.selectedSlot)
                invOverlay.visibility = View.GONE
                saveWorldAsync()
            }
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveWorldAsync() {
        val id = worldId ?: return
        val snap = CaveWorldSave(
            id = id, name = "", seed = 0L, createdAt = 0L,
            lastPlayedAt = System.currentTimeMillis(),
            playerX = renderer.camera.x, playerY = renderer.camera.y, playerZ = renderer.camera.z,
            playerYaw = renderer.camera.yaw, playerPitch = renderer.camera.pitch,
            inventory = renderer.inventory.toMap(),
            hotbar = renderer.hotbar.map { it }
        )
        lifecycleScope.launch(Dispatchers.IO) { CaveWorldSaveManager.updateFields(this@CaveActivity, snap) }
    }

    private fun saveWorld() = saveWorldAsync()

    // ── Noms et mode ─────────────────────────────────────────────────────────

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

    private fun applyModeUi(mode: PlayerMode, btnMode: Button, btnUp: Button,
                             btnDown: View, btnLaser: View, btnPlace: View) {
        when (mode) {
            PlayerMode.SPECTATOR -> {
                btnMode.text = getString(R.string.cave_mode_spectator)
                btnUp.text   = "▲"
                btnDown.visibility  = View.VISIBLE
                btnLaser.visibility = View.GONE
                btnPlace.visibility = View.GONE
            }
            PlayerMode.WALK -> {
                btnMode.text = getString(R.string.cave_mode_walk)
                btnUp.text   = getString(R.string.cave_jump)
                btnDown.visibility  = View.GONE
                btnLaser.visibility = View.VISIBLE
                btnPlace.visibility = View.VISIBLE
            }
        }
    }

    // ── Touch multipoint ─────────────────────────────────────────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        val idx    = ev.actionIndex
        val pid    = ev.getPointerId(idx)

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

        touch.onTouch(ev, glView.width)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume()  { super.onResume();  glView.onResume() }
    override fun onPause()   { super.onPause();   glView.onPause(); saveWorld() }
    override fun onDestroy() { super.onDestroy(); renderer.destroy() }

    private fun View.isHitOnScreen(x: Float, y: Float): Boolean {
        val loc = IntArray(2); getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }
}
