package com.Atom2Universe.app.games.caves

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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

        // Inventaire unifié : 27 slots grille + séparateur (1) + 9 slots actifs
        private const val GRID_SIZE   = 27
        private const val ACTIVE_SIZE = 9
        private const val SEP_POS     = GRID_SIZE      // position du séparateur dans l'adapter
        private const val TOTAL_ADAPTER = GRID_SIZE + 1 + ACTIVE_SIZE  // 37

        private const val VT_ITEM = 0
        private const val VT_SEP  = 1

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

        // Convertit position adapter ↔ index dans invSlots[0..35]
        fun adapterToSlot(pos: Int): Int = if (pos < SEP_POS) pos else pos - 1
        fun slotToAdapter(idx: Int): Int = if (idx < GRID_SIZE) idx else idx + 1
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

    // Hotbar HUD (9 slots + bouton sac)
    private val slotViews   = arrayOfNulls<FrameLayout>(ACTIVE_SIZE)
    private val slotCounts  = arrayOfNulls<TextView>(ACTIVE_SIZE)
    private val slotColors  = arrayOfNulls<View>(ACTIVE_SIZE)
    private lateinit var invOverlay: View

    // Inventaire unifié : 0-26 = grille, 27-35 = actifs
    private val invSlots = arrayOfNulls<Byte>(GRID_SIZE + ACTIVE_SIZE)
    private var invSlotsReady = false

    // Sélection (tap-pour-déplacer)
    private var selectedAdapterPos = -1
    private var invGridAdapter: InvGridAdapter? = null

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
        renderer.inventoryCallback = { inv ->
            uiHandler.post {
                if (invSlotsReady) onInventoryChanged(inv)
                saveWorldAsync()
            }
        }
        renderer.hotbarCallback = { slots, selected ->
            uiHandler.post { updateHotbarUI(slots, selected) }
        }

        invOverlay = layoutInflater.inflate(R.layout.overlay_cave_inventory, root, false)
        root.addView(invOverlay)
        invOverlay.setOnClickListener { closeInventory() }
        invOverlay.findViewById<View>(R.id.cave_inv_panel).setOnClickListener { /* consomme */ }

        val recycler = invOverlay.findViewById<RecyclerView>(R.id.cave_inv_recycler)
        val glm = GridLayoutManager(this, ACTIVE_SIZE)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (position == SEP_POS) ACTIVE_SIZE else 1
        }
        recycler.layoutManager = glm

        applyModeUi(PlayerMode.WALK, btnMode, btnUp as Button, btnDown, btnLaser, btnPlace)
    }

    // ── Inventaire unifié ─────────────────────────────────────────────────────

    /** Initialise invSlots depuis le renderer (appelé une seule fois à la première ouverture). */
    private fun initInvSlots() {
        invSlots.fill(null)
        // Rangée active depuis le hotbar renderer
        for (i in 0 until ACTIVE_SIZE) {
            val t = renderer.hotbar[i]
            invSlots[GRID_SIZE + i] = if (t != null && (renderer.inventory[t] ?: 0) > 0) t else null
        }
        // Grille : types de l'inventaire pas encore dans la rangée active
        val activeSet = (GRID_SIZE until GRID_SIZE + ACTIVE_SIZE).mapNotNull { invSlots[it] }.toSet()
        var gridPos = 0
        for ((type, count) in renderer.inventory) {
            if (count <= 0 || type in activeSet) continue
            while (gridPos < GRID_SIZE && invSlots[gridPos] != null) gridPos++
            if (gridPos < GRID_SIZE) invSlots[gridPos++] = type
        }
        invSlotsReady = true
    }

    /** Synchronise invSlots quand l'inventaire change (minage / pose). */
    private fun onInventoryChanged(inv: Map<Byte, Int>) {
        // Retirer les types épuisés
        for (i in invSlots.indices) {
            val t = invSlots[i] ?: continue
            if ((inv[t] ?: 0) <= 0) invSlots[i] = null
        }
        // Ajouter les nouveaux types dans le premier slot grille vide
        val present = invSlots.filterNotNull().toSet()
        for ((type, count) in inv) {
            if (count <= 0 || type in present) continue
            val empty = (0 until GRID_SIZE).firstOrNull { invSlots[it] == null }
            if (empty != null) invSlots[empty] = type
        }
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE)
            invGridAdapter?.notifyDataSetChanged()
    }

    /** Pousse les slots actifs (27-35) vers renderer.hotbar. */
    private fun syncHotbarFromActiveSlots() {
        for (i in 0 until ACTIVE_SIZE) renderer.hotbar[i] = invSlots[GRID_SIZE + i]
        renderer.hotbarCallback?.invoke(renderer.hotbar.copyOf(), renderer.selectedSlot)
    }

    private fun openInventory() {
        if (!invSlotsReady) initInvSlots()

        val dp = resources.displayMetrics.density
        val cellSize = (40 * dp).toInt()
        val gridWidth = cellSize * ACTIVE_SIZE

        val recycler = invOverlay.findViewById<RecyclerView>(R.id.cave_inv_recycler)
        recycler.layoutParams = LinearLayout.LayoutParams(gridWidth, LinearLayout.LayoutParams.WRAP_CONTENT)

        if (invGridAdapter == null) {
            val adapter = InvGridAdapter()
            invGridAdapter = adapter
            recycler.adapter = adapter
        } else {
            invGridAdapter!!.notifyDataSetChanged()
        }

        invOverlay.visibility = View.VISIBLE
    }

    private fun closeInventory() {
        if (selectedAdapterPos >= 0) {
            val prev = selectedAdapterPos
            selectedAdapterPos = -1
            invGridAdapter?.notifyItemChanged(prev)
        }
        invOverlay.visibility = View.GONE
    }

    private fun setSelectedAdapterPos(pos: Int) {
        val prev = selectedAdapterPos
        selectedAdapterPos = pos
        if (prev >= 0) invGridAdapter?.notifyItemChanged(prev)
        if (pos >= 0)  invGridAdapter?.notifyItemChanged(pos)
    }

    private fun swapAdapterPositions(a: Int, b: Int) {
        if (a == b || a == SEP_POS || b == SEP_POS) return
        val sa = adapterToSlot(a); val sb = adapterToSlot(b)
        val tmp = invSlots[sa]; invSlots[sa] = invSlots[sb]; invSlots[sb] = tmp
        invGridAdapter?.notifyItemChanged(a)
        invGridAdapter?.notifyItemChanged(b)
        syncHotbarFromActiveSlots()
        saveWorldAsync()
    }


    // ── Adapter unifié ────────────────────────────────────────────────────────

    private inner class InvGridAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
            val colorView: View    = v.findViewById(R.id.cave_inv_color)
            val countTv:  TextView = v.findViewById(R.id.cave_inv_count)
        }

        inner class SepVH(v: View) : RecyclerView.ViewHolder(v)

        override fun getItemViewType(position: Int) = if (position == SEP_POS) VT_SEP else VT_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VT_SEP) {
                val sep = TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, (28 * resources.displayMetrics.density).toInt()
                    )
                    gravity = Gravity.CENTER_VERTICAL
                    text = getString(R.string.cave_inventory_title)   // sera "Actifs" idéalement
                    setTextColor(0x88FFFFFF.toInt())
                    textSize = 9f
                    setPadding((4 * resources.displayMetrics.density).toInt(), 0, 0, 0)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(0x00000000)
                        setStroke(1, 0x33FFFFFF)
                    }
                }
                SepVH(sep)
            } else {
                val cellSize = if (parent.width > 0) parent.width / ACTIVE_SIZE
                               else (40 * resources.displayMetrics.density).toInt()
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_cave_inv_block, parent, false)
                view.layoutParams = RecyclerView.LayoutParams(cellSize, cellSize)
                view.setPadding(1, 1, 1, 1)
                ItemVH(view)
            }
        }

        override fun getItemCount() = TOTAL_ADAPTER

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is SepVH) return   // séparateur statique

            holder as ItemVH
            val slotIdx  = adapterToSlot(position)
            val isActive = slotIdx >= GRID_SIZE
            val type  = invSlots[slotIdx]
            val count = if (type != null) renderer.inventory[type] ?: 0 else 0
            val eff   = if (count > 0) type else null
            val isSel = position == selectedAdapterPos
            val dp    = resources.displayMetrics.density

            // Fond
            holder.itemView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (isActive) 0x44FFFFFF else 0x22FFFFFF)
                setStroke(
                    if (isSel) (2 * dp).toInt() else (1 * dp).toInt(),
                    when {
                        isSel    -> 0xFFFFDD00.toInt()
                        isActive -> 0x88FFFFFF.toInt()
                        else     -> 0x44FFFFFF.toInt()
                    }
                )
                cornerRadius = 3 * dp
            }

            // Couleur du bloc
            val colorBg = holder.colorView.background as? GradientDrawable
                ?: GradientDrawable().also { holder.colorView.background = it }
            colorBg.apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (eff != null) blockColor(eff) else Color.TRANSPARENT)
                cornerRadius = 2 * dp
            }

            // Quantité
            holder.countTv.text = if (eff != null) count.toString() else ""

            // Tap : sélectionner → puis tap ailleurs pour déplacer
            holder.itemView.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_ID.toInt() || pos == SEP_POS) return@setOnClickListener
                val sel = selectedAdapterPos
                when {
                    // Rien de sélectionné et case non vide → sélectionner
                    sel < 0 && eff != null -> setSelectedAdapterPos(pos)
                    // Même case → désélectionner
                    sel == pos -> setSelectedAdapterPos(-1)
                    // Item sélectionné → swap
                    sel >= 0 -> {
                        setSelectedAdapterPos(-1)
                        swapAdapterPositions(sel, pos)
                    }
                }
            }
        }
    }

    // ── Hotbar HUD ────────────────────────────────────────────────────────────

    private fun buildHotbarUI(container: LinearLayout) {
        val dp = resources.displayMetrics.density
        val slotSizePx = (52 * dp).toInt()

        repeat(ACTIVE_SIZE) { i ->
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
                ).also { it.gravity = Gravity.CENTER }
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            val countTv = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.gravity = Gravity.BOTTOM or Gravity.END }
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
            val eff = if (count > 0) type else null
            slotViews[i]?.background = slotDrawable(eff, i == selected)
            (slotColors[i]?.background as? GradientDrawable)?.setColor(
                if (eff != null) blockColor(eff) else Color.TRANSPARENT
            )
            slotCounts[i]?.text = if (eff != null) count.toString() else ""
        }
        // Synchroniser la rangée active dans invSlots (sans déclencher de rebuild)
        if (invSlotsReady) {
            for (i in 0 until ACTIVE_SIZE) {
                val t = slots[i]
                invSlots[GRID_SIZE + i] = if (t != null && (renderer.inventory[t] ?: 0) > 0) t else null
            }
            if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE)
                invGridAdapter?.notifyDataSetChanged()
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
        // Bloquer les commandes de jeu quand l'inventaire est ouvert
        if (::invOverlay.isInitialized && invOverlay.visibility == View.VISIBLE) {
            return super.dispatchTouchEvent(ev)
        }

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
