package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.CraftDef
import com.Atom2Universe.app.games.caves.node.CraftRegistry
import com.Atom2Universe.app.games.caves.node.ItemRarity
import com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry
import com.Atom2Universe.app.games.caves.node.FrontierItems as F

internal enum class InvGpZone { GRID, HOTBAR, CRAFTING }

internal class InventoryManager(private val activity: CaveActivity) {

    private val renderer get() = activity.renderer
    private val hud      get() = activity.hud
    private lateinit var ui: CaveInventoryPanel
    private var storageClose: (() -> Unit)? = null
    private val menuBubbles = CaveStackPopup(activity)
    internal val storagePageOpen get()=storageClose!=null

    private fun showOverlay() {
        activity.findViewById<View>(R.id.cave_game_area).visibility = View.INVISIBLE
        hud.setInventoryOpen(true)
        activity.invOverlay.visibility = View.VISIBLE
        ui.animateOpen()
    }

    internal fun showStoragePage(content: View,onClose: () -> Unit): List<CaveItemTile> {
        ui.dismissDetails()
        storageClose=onClose
        renderer.gamePaused=true
        activity.releaseGameInputs()
        ui.showStorage(content)
        showOverlay()
        return hud.overlayActiveFrames.mapNotNull { it as? CaveItemTile }
    }
    private var browsingCraft = false
    private var assigningShortcut = false
    private var query = ""
    private var categoryIndex = 0
    private var relatedType: Short? = null
    private var gridIndices = emptyList<Int>()
    private var gridColumns = CaveActivity.GRID_COLS
    private val categoryKeys = listOf("", "terrain", "wood", "stone", "nature", "functional", "cotton", "ores", "resources")

    // ── Slots ─────────────────────────────────────────────────────────────────
    // The count map covers bag + bar; each stack is shown in exactly one location.
    val invSlots = ArrayList<Short?>()
    private val stackKeys=ArrayList<Long?>()
    private fun stackAt(index: Int)=stackKeys.getOrNull(index)?.let(renderer.inventoryStacks::get)
    private fun countAt(index: Int)=stackAt(index)?.count ?: 0
    private val preferences by lazy { activity.getSharedPreferences("cave_catalog_"+activity.intent.getStringExtra(CaveActivity.EXTRA_WORLD_ID),Context.MODE_PRIVATE) }
    private val favorites by lazy { preferences.getStringSet("favorites",emptySet()).orEmpty().toMutableSet() }
    private val recent by lazy { preferences.getString("recent","").orEmpty().split(',').mapNotNull { it.toShortOrNull() }.toMutableList() }
    private var previousCounts: Map<Short,Int> = emptyMap()
    private var bankFilter: HotbarMode? = null
    private var onlyFavorites=false
    private var onlyRecent=false
    private var sortOrder=0
    private val recipeColumns get()=(craftingRecyclerView?.layoutManager as? GridLayoutManager)?.spanCount ?: 1
    private val favoriteRecipes by lazy { preferences.getStringSet("recipeFavorites",emptySet()).orEmpty().toMutableSet() }
    private val recipeHistory=java.util.ArrayDeque<CraftDef>()
    private fun recipeKey(r: CraftDef)="${r.resultItemId ?: r.result}:${r.ingredients}:${r.groups.map { it.tag to it.count }}:${r.station}"

    private val names=hashMapOf<Short,String>()
    private val accents=Regex("\\p{M}+")
    private fun folded(text: String)=java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replace(accents,"")
    private fun name(id: Short)=names.getOrPut(id) { folded(activity.blockName(id)) }
    private fun favoriteKey(id: Short)=WeaponInstanceRegistry.get(id)?.let { "$id:${it.hashCode()}" } ?: id.toString()
    internal fun isFavorite(id: Short)=favoriteKey(id) in favorites

    private fun rebuildCatalog() {
        val selected=stackKeys.getOrNull(selectedSlotIdx)
        val wasShortcut=selectedSlotIdx>=hotbarBase() && selectedSlotIdx>=0
        val shortcut=selectedSlotIdx-hotbarBase()
        val stacks=renderer.inventoryStacks.snapshot()
        val bag=stacks.filter { it.slot<0 }
        invSlots.clear();stackKeys.clear()
        for(stack in bag) { invSlots+=stack.id;stackKeys+=stack.key }
        for(i in renderer.hotbar.indices) {
            val stack=stacks.firstOrNull { it.slot==i };invSlots+=stack?.id;stackKeys+=stack?.key
        }
        selectedSlotIdx=if(wasShortcut && shortcut in renderer.hotbar.indices) hotbarBase()+shortcut else selected?.let(stackKeys::indexOf) ?: -1
    }
    var invSlotsReady = false
    var selectedSlotIdx = -1
    var dragSourceIdx = -1

    fun hotbarBase() = (invSlots.size - CaveActivity.ACTIVE_SIZE).coerceAtLeast(0)
    fun selectedType(): Short? = selectedSlotIdx.takeIf { it in invSlots.indices }?.let { invSlots[it] }

    // ── Crafting ──────────────────────────────────────────────────────────────
    var selectedRecipe: CraftDef? = null
    var craftingAdapter: CraftingAdapter? = null

    // ── Refs views de l'overlay ───────────────────────────────────────────────
    var infoSpriteView: View? = null
    var infoNameTv: TextView? = null
    var infoCountTv: TextView? = null
    var infoDivider: View? = null
    var infoIngredientsTv: TextView? = null
    var craftingEmptyTv: View? = null
    var craftingRecyclerView: RecyclerView? = null
    var rightHeaderTv: TextView? = null
    var sellPanel: View? = null
    var sellPriceTv: TextView? = null
    var sellButton: Button? = null

    // ── Pager inventaire ──────────────────────────────────────────────────────
    var invPager: ViewPager2? = null
    var pageIndicatorTv: TextView? = null
    var pagedAdapter: PagedInvPagerAdapter? = null
    var pageSize = CaveActivity.GRID_COLS * 5
    var currentPage = 0

    // ── Gamepad inventaire ────────────────────────────────────────────────────
    var invGpZone = InvGpZone.HOTBAR
    var invGpCursor = 0
    private var invGpLastMoveMs = 0L
    private var invGpRightLastMs = 0L
    private val INV_GP_REPEAT_MS = 170L

    // ── Init overlay ──────────────────────────────────────────────────────────

    fun setupOverlay(invOverlay: View) {
        ui = CaveInventoryPanel(activity).also { it.populate(invOverlay as FrameLayout) }
        invOverlay.findViewById<View>(R.id.cave_inv_dim_area).setOnClickListener { closeInventory() }
        invOverlay.findViewById<View>(R.id.cave_inv_panel).setOnClickListener { /* consomme */ }
        ui.detailScroll.findViewById<View>(R.id.cave_inv_info_column).setOnClickListener { /* details never dismiss the inventory */ }

        infoSpriteView       = ui.detailScroll.findViewById(R.id.cave_inv_info_sprite)
        infoNameTv           = ui.detailScroll.findViewById(R.id.cave_inv_info_name)
        infoCountTv          = ui.detailScroll.findViewById(R.id.cave_inv_info_count)
        infoDivider          = ui.detailScroll.findViewById(R.id.cave_inv_info_divider)
        infoIngredientsTv    = ui.detailScroll.findViewById(R.id.cave_inv_info_ingredients)
        craftingEmptyTv      = invOverlay.findViewById(R.id.cave_inv_crafting_empty)
        craftingRecyclerView = invOverlay.findViewById(R.id.cave_inv_crafting_recycler)
        rightHeaderTv        = ui.detailScroll.findViewById(R.id.cave_inv_right_header)
        sellPanel            = ui.detailScroll.findViewById(R.id.cave_inv_sell_panel)
        sellPriceTv          = ui.detailScroll.findViewById(R.id.cave_inv_sell_price)
        sellButton           = ui.detailScroll.findViewById<Button>(R.id.cave_inv_sell_btn)
            ?.also { btn -> btn.setOnClickListener { selectedType()?.let { confirmSell(it) } } }
        invPager             = invOverlay.findViewById(R.id.cave_inv_pager)
        pageIndicatorTv      = invOverlay.findViewById(R.id.cave_inv_page_indicator)
        setupNavigation()
        invPager?.addOnLayoutChangeListener { _,l,t,r,b,ol,ot,or,ob ->
            if(activity.invOverlay.visibility==View.VISIBLE && (r-l!=or-ol || b-t!=ob-ot)) layoutCatalogue()
        }


        val ca = CraftingAdapter(emptyList()).also { craftingAdapter = it }
        craftingRecyclerView?.apply {
            layoutManager = CaveItemGridLayout(activity)
            adapter = ca
        }

        invPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                if (invGpZone == InvGpZone.GRID)
                    invGpCursor = gridIndices.getOrNull(position * pageSize) ?: hotbarBase()
                updatePageIndicator()
                invPager?.post { pagedAdapter?.notifyDataSetChanged() }
                hud.updateHotbarForInventory()
            }
        })

        hud.buildOverlayActiveBar()

    }

    // ── Gestion slots ─────────────────────────────────────────────────────────

    private fun setupNavigation() {
        sortOrder=preferences.getInt("sort",0).coerceIn(0,2)
        ui.close.setOnClickListener { closeInventory() }
        ui.inventoryTab.setOnClickListener { showLibrary(false) }
        ui.craftTab.setOnClickListener { showLibrary(true) }
        val categories = intArrayOf(R.string.cave_ui_all, R.string.cave_ui_terrain, R.string.cave_ui_wood,
            R.string.cave_ui_stone, R.string.cave_ui_nature, R.string.cave_ui_functional, R.string.cave_ui_cotton,
            R.string.cave_ui_ores, R.string.cave_ui_resources)
        ui.category.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            categories.map { activity.getString(it) })
        ui.category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(CaveUiStyle.TEXT)
                categoryIndex = position; currentPage = 0; refreshPagedAdapter();updateCraftingList()
            }
        }
        ui.search.setOnEditorActionListener { view,_,_ ->
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken,0)
            view.clearFocus();true
        }
        ui.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ui.dismissDetails()
                query = s?.toString()?.trim().orEmpty(); currentPage = 0;selectedSlotIdx=-1;selectedRecipe=null
                relatedType = null; refreshPagedAdapter(); updateCraftingList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        ui.craftable.setOnCheckedChangeListener { _, _ -> updateCraftingList() }
        ui.previous.setOnClickListener { changePage(-1) }; ui.next.setOnClickListener { changePage(1) }
        dragPaging(ui.previous,-1);dragPaging(ui.next,1)
        ui.pager.setOnDragListener(makeBagDragListener())
        ui.bagDropArea.setOnDragListener(makeBagDragListener())
        ui.empty.setOnDragListener(makeBagDragListener())
        ui.sort.setOnClickListener {
            val options=intArrayOf(R.string.cave_catalog_sort_name,R.string.cave_catalog_sort_count,R.string.cave_catalog_sort_recent)
            ui.dismissDetails()
            menuBubbles.choices(ui.sort,activity.getString(R.string.cave_ui_sort),options.map { activity.getString(it) },sortOrder) { index ->
                sortOrder=index;preferences.edit().putInt("sort",index).apply();currentPage=0;refreshPagedAdapter()
            }
        }
        ui.clearSearch.setOnClickListener { ui.search.setText("");bankFilter=null;onlyFavorites=false;onlyRecent=false;categoryIndex=0;ui.category.setSelection(0);ui.craftable.isChecked=false;relatedType=null;refreshPagedAdapter();updateCraftingList() }
        ui.favoritesOnly.setOnClickListener { onlyFavorites=!onlyFavorites;currentPage=0;refreshPagedAdapter();updateCraftingList() }
        ui.recentOnly.setOnClickListener { onlyRecent=!onlyRecent;currentPage=0;refreshPagedAdapter();updateCraftingList() }
        for((button,filter) in listOf(ui.filterAll to null,ui.filterGear to HotbarMode.COMBAT,ui.filterBuild to HotbarMode.BUILD,ui.filterGarden to HotbarMode.GARDEN)) {
            button.setOnClickListener { bankFilter=filter;categoryIndex=0;ui.category.setSelection(0);currentPage=0;refreshPagedAdapter();updateCraftingList() }
        }
        ui.craftMax.setOnClickListener { selectedRecipe?.let { doCraft(it,minOf(64,it.maxCraftable(renderer.inventory,renderer.nearbyStations))) } }
        pageIndicatorTv?.setOnClickListener {
            val field=android.widget.EditText(activity).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText((currentPage+1).toString());selectAll() }
            AlertDialog.Builder(activity).setTitle(R.string.cave_catalog_jump).setView(field).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(android.R.string.ok) { _,_ -> val page=field.text.toString().toIntOrNull() ?: 1;changePage(page.coerceIn(1,pageCount())-1-currentPage) }.show()
        }
        ui.related.setOnClickListener {
            if(recipeHistory.isNotEmpty()) {
                selectedRecipe=recipeHistory.removeLast();updateInfoPanel();craftingAdapter?.notifyDataSetChanged()
            }
        }
        ui.craftOne.setOnClickListener { selectedRecipe?.let { doCraft(it) } }
        ui.craftFive.setOnClickListener { selectedRecipe?.let { doCraft(it, 5) } }
    }

    private fun showLibrary(craft: Boolean) {
        ui.dismissDetails()
        browsingCraft = craft; assigningShortcut = false; selectedRecipe = null; relatedType = null;recipeHistory.clear()
        invGpZone = if (craft) InvGpZone.CRAFTING else InvGpZone.HOTBAR
        invGpCursor = if (craft) 0 else hotbarBase()
        refreshPagedAdapter(); updateCraftingList(); updateInfoPanel()
    }

    private fun updateActions() {
        if (!::ui.isInitialized) return
        val recipe = selectedRecipe
        ui.related.visibility = if (browsingCraft && recipeHistory.isNotEmpty()) View.VISIBLE else View.GONE
        CaveUiStyle.icon(ui.related,"previous",activity.getString(R.string.cave_ui_previous))
        ui.craftOne.visibility = if (browsingCraft && recipe != null) View.VISIBLE else View.GONE
        ui.craftFive.visibility = ui.craftOne.visibility;ui.craftMax.visibility=ui.craftOne.visibility
        ui.craftMax.isEnabled=(recipe?.maxCraftable(renderer.inventory,renderer.nearbyStations) ?: 0)>0
        ui.craftOne.text=activity.getString(R.string.cave_catalog_craft_quantity,1)
        ui.craftFive.text=activity.getString(R.string.cave_catalog_craft_quantity,5)
        ui.craftMax.text=activity.getString(R.string.cave_catalog_craft_quantity,minOf(64,recipe?.maxCraftable(renderer.inventory,renderer.nearbyStations) ?: 0))
        for(b in listOf(ui.craftOne,ui.craftFive,ui.craftMax)) {
            b.contentDescription=activity.getString(R.string.cave_catalog_craft_action,b.text);b.tooltipText=b.contentDescription
        }
        ui.craftOne.isEnabled = recipe?.canCraft(renderer.inventory, renderer.nearbyStations) == true
        ui.craftFive.isEnabled = (recipe?.maxCraftable(renderer.inventory, renderer.nearbyStations) ?: 0) >= 5
        ui.craftOne.alpha = if (ui.craftOne.isEnabled) 1f else .45f
        ui.craftFive.alpha = if (ui.craftFive.isEnabled) 1f else .45f
        ui.status.setText(if (assigningShortcut) R.string.cave_ui_choose_shortcut else if (browsingCraft) R.string.cave_catalog_recipe_hint else R.string.cave_catalog_drag_hint)
        CaveUiStyle.button(ui.inventoryTab, !browsingCraft); CaveUiStyle.button(ui.craftTab, browsingCraft)
        for((b,key,label,active) in listOf(
            IconState(ui.filterAll,"all",R.string.cave_ui_all,bankFilter==null),IconState(ui.filterGear,"combat",R.string.cave_ui_equipment,bankFilter==HotbarMode.COMBAT),
            IconState(ui.filterBuild,"place",R.string.cave_ui_materials,bankFilter==HotbarMode.BUILD),IconState(ui.filterGarden,"garden",R.string.cave_ui_garden,bankFilter==HotbarMode.GARDEN),
            IconState(ui.favoritesOnly,"star",R.string.cave_catalog_favorites,onlyFavorites),IconState(ui.recentOnly,"clock",R.string.cave_catalog_recent,onlyRecent))) CaveUiStyle.icon(b,key,activity.getString(label),active)

    }

    private data class IconState(val button: Button,val key: String,val label: Int,val active: Boolean)

    private fun selectInventorySlot(index: Int) {
        if (index !in invSlots.indices) return
        if (assigningShortcut && index >= hotbarBase() && selectedType() != null) {
            swapSlots(selectedSlotIdx, index); assigningShortcut = false
        } else {
            assigningShortcut = false
            selectedSlotIdx = if (invSlots[index] != null) index else -1
        }
        selectedRecipe = null
        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
    }

    internal fun toggleFavorite(id: Short) {
        ui.dismissDetails()
        val key=favoriteKey(id)
        val added=favorites.add(key);if(!added) favorites.remove(key)
        preferences.edit().putStringSet("favorites",favorites.toSet()).apply()
        if(!storagePageOpen) refreshPagedAdapter()
        ui.status.setText(if(added) R.string.cave_catalog_favorite_added else R.string.cave_catalog_favorite_removed)
    }

    private fun toggleRecipeFavorite(recipe: CraftDef) {
        ui.dismissDetails()
        val key=recipeKey(recipe)
        val added=favoriteRecipes.add(key);if(!added) favoriteRecipes.remove(key)
        preferences.edit().putStringSet("recipeFavorites",favoriteRecipes.toSet()).apply()
        updateCraftingList()
        ui.status.setText(if(added) R.string.cave_catalog_favorite_added else R.string.cave_catalog_favorite_removed)
    }

    private fun confirmSell(id: Short) {
        val offered = WeaponInstanceRegistry.get(id) ?: return
        AlertDialog.Builder(activity).setTitle(R.string.cave_inv_sell_btn)
            .setMessage(activity.getString(R.string.cave_ui_sell_confirm, activity.blockName(id), weaponSellPrice(id)))
            .setNegativeButton(R.string.cave_ui_cancel, null)
            .setPositiveButton(R.string.cave_inv_sell_btn) { _, _ -> if (WeaponInstanceRegistry.get(id) === offered) doSell(id) }.show()
    }

    fun initInvSlots() { renderer.syncInventoryStacks();invSlotsReady=true;previousCounts=renderer.inventory.toMap();rebuildCatalog();syncHotbar() }
    fun addNewType(type: Short) { if(type !in invSlots) rebuildCatalog() }
    private fun addNewTypeByCategory(type: Short) { addNewType(type) }
    fun syncHotbar() {
        renderer.syncInventoryStacks();rebuildCatalog();renderer.notifyHotbar()
    }
    private fun assignToBar(key: Long,index: Int) {
        if(!renderer.inventoryStacks.moveToBar(key,index)) return
        renderer.inventoryStacks.writeBar(renderer.hotbar)
        syncHotbar();selectedSlotIdx=hotbarBase()+index;activity.saveWorldAsync()
    }
    fun swapSlots(a: Int,b: Int) {
        val stack=stackAt(a) ?: return
        if(a==b || b !in invSlots.indices) return
        if(b>=hotbarBase()) assignToBar(stack.key,b-hotbarBase())
        else returnToBag(InventoryDrag(stack.key,stack.id,stack.slot.takeIf { it>=0 }),stackKeys.getOrNull(b))
    }
    private data class InventoryDrag(val key: Long,val id: Short,val slot: Int?)

    internal fun bindBarGestures(view: View,index: Int) {
        CaveInventoryGestures.bind(view,
            active={ activity.invOverlay.visibility == View.VISIBLE && !storagePageOpen },
            inactiveTap={ renderer.selectSlot(index) },
            tap={ onOverlayActiveSlotClick(index) },
            favorite={ renderer.hotbar.getOrNull(index)?.let { toggleFavorite(it) } },
            drag={ startSlotDrag(view,hotbarBase()+index) })
    }

    private fun returnToBag(token: InventoryDrag,target: Long?=null) {
        if(!renderer.inventoryStacks.moveToBag(token.key,target)) return
        renderer.inventoryStacks.writeBar(renderer.hotbar)
        syncHotbar();selectedSlotIdx=stackKeys.indexOf(token.key)
        refreshPagedAdapter();hud.updateHotbarForInventory();updateInfoPanel()
        ui.status.text=activity.getString(R.string.cave_inventory_moved_bag,activity.blockName(token.id))
        activity.saveWorldAsync()
    }

    private fun makeBagDragListener()=View.OnDragListener { view,event ->
        val token=event.localState as? InventoryDrag
        when(event.action) {
            DragEvent.ACTION_DRAG_ENTERED -> { if(token?.slot!=null) view.alpha=.8f;token!=null }
            DragEvent.ACTION_DRAG_EXITED -> { view.alpha=1f;true }
            DragEvent.ACTION_DROP -> { view.alpha=1f;if(token!=null) returnToBag(token);token!=null }
            DragEvent.ACTION_DRAG_ENDED -> { view.alpha=1f;dragSourceIdx=-1;true }
            else -> token!=null
        }
    }

    private fun dragPaging(view: View,delta: Int) {
        var hovering=false
        val advance=object: Runnable { override fun run() {
            if(hovering && activity.invOverlay.visibility==View.VISIBLE) { changePage(delta);view.postDelayed(this,700) }
        } }
        view.setOnDragListener { _,event ->
            when(event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.localState is InventoryDrag
                DragEvent.ACTION_DRAG_ENTERED -> { hovering=true;view.postDelayed(advance,500);true }
                DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED -> { hovering=false;view.removeCallbacks(advance);true }
                else -> true
            }
        }
    }
    fun startSlotDrag(view: View,idx: Int) {
        ui.dismissDetails()
        val stack=stackAt(idx) ?: return
        dragSourceIdx=idx
        val token=InventoryDrag(stack.key,stack.id,stack.slot.takeIf { it>=0 })
        if(!view.startDragAndDrop(ClipData.newPlainText("cave-item",stack.id.toString()),View.DragShadowBuilder(view),token,0)) dragSourceIdx=-1
    }
    fun makeSlotDragListener(idxProvider: () -> Int): View.OnDragListener = View.OnDragListener { v,event ->
        val token=event.localState as? InventoryDrag
        when(event.action) {
            DragEvent.ACTION_DRAG_STARTED -> token!=null
            DragEvent.ACTION_DRAG_ENTERED -> { if(token!=null) v.alpha=.55f;token!=null }
            DragEvent.ACTION_DRAG_EXITED -> { v.alpha=1f;true }
            DragEvent.ACTION_DROP -> {
                v.alpha=1f
                val index=idxProvider()
                if(token!=null && index in invSlots.indices && (renderer.inventory[token.id] ?: 0)>0) {
                    val target=if(index>=hotbarBase()) index-hotbarBase() else null
                    if(target!=null) assignToBar(token.key,target) else returnToBag(token,stackKeys.getOrNull(index))
                    refreshPagedAdapter();hud.updateHotbarForInventory();updateInfoPanel()
                    ui.status.text=if(target!=null) activity.getString(R.string.cave_inventory_moved_bar,activity.blockName(token.id),target+1)
                        else activity.getString(R.string.cave_inventory_moved_bag,activity.blockName(token.id))
                }
                token!=null
            }
            DragEvent.ACTION_DRAG_ENDED -> { v.alpha=1f;dragSourceIdx=-1;true }
            else -> token!=null
        }
    }
    fun onInventoryChanged(inv: Map<Short,Int>) {
        var changed=false
        for((id,count) in inv) if(count>(previousCounts[id] ?: 0)) {
            recent.remove(id);recent.add(0,id);changed=true
        }
        if(recent.size>64) recent.subList(64,recent.size).clear()
        if(changed) preferences.edit().putString("recent",recent.joinToString(",")).apply()
        previousCounts=inv.toMap()
        if(storagePageOpen) return
        if(!invSlotsReady) return
        if(activity.invOverlay.visibility==View.VISIBLE) {
            syncHotbar()
            refreshPagedAdapter();hud.updateHotbarForInventory();updateInfoPanel();updateCraftingList()
        } else rebuildCatalog()
    }

    // ── Inventaire open/close ─────────────────────────────────────────────────

    private var openingInventory=false
    fun openInventory() {
        if (renderer.mode.singleWeapon || openingInventory || storagePageOpen || !renderer.spawnReady || activity.invOverlay.visibility == View.VISIBLE) return
        openingInventory=true
        renderer.gamePaused = true
        activity.releaseGameInputs()
        // Let the current simulation frame finish before crafting may edit the inventory
        // on the UI thread. Snapshots taken from this paused panel then share a stable world.
        activity.glView.queueEvent { renderer.refreshCraftStations();renderer.syncInventoryStacks(); activity.runOnUiThread {
            if(!openingInventory || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            openingInventory=false
            previousCounts=renderer.inventory.toMap()
            rebuildCatalog()
            showPausedInventory()
        } }
    }

    private fun showPausedInventory() {
        assigningShortcut = false; selectedSlotIdx = -1; selectedRecipe = null
        activity.releaseGameInputs()
        if (!invSlotsReady) initInvSlots()
        showOverlay()
        invGpZone   = InvGpZone.HOTBAR
        invGpCursor = hotbarBase()
        activity.invOverlay.post {
            if (activity.invOverlay.visibility != View.VISIBLE) return@post
            layoutCatalogue()
            refreshPagedAdapter()
            hud.updateHotbarForInventory()
            updateInfoPanel()
            updateCraftingList()
        }
    }

    private fun layoutCatalogue() {
        val pager=invPager ?: return
        if(pager.width<=0 || pager.height<=0) return
        val size=CaveItemTile.edge(activity)
        val columns=(pager.width/CaveItemTile.pitch(activity)).coerceAtLeast(1)
        val rows=(pager.height/CaveItemTile.pitch(activity)).coerceAtLeast(1)
        val anchor=currentPage*pageSize
        if(pagedAdapter!=null && columns==gridColumns && rows*columns==pageSize && pagedAdapter?.cellSize==size) return
        gridColumns=columns;pageSize=rows*columns
        pagedAdapter=PagedInvPagerAdapter(size).also { pager.adapter=it }
        currentPage=anchor/pageSize
        refreshPagedAdapter()
    }

    fun closeInventory() {
        ui.dismissDetails()
        menuBubbles.cancel()
        openingInventory=false
        if(activity.invOverlay.visibility==View.VISIBLE) activity.saveWorldAsync()
        storageClose?.let { close ->
            storageClose=null
            close()
            ui.hideStorage()
            hud.buildOverlayActiveBar()
        }
        assigningShortcut = false; dragSourceIdx = -1
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.search.windowToken, 0)
        ui.search.clearFocus()
        selectedSlotIdx = -1
        selectedRecipe  = null
        invGpZone       = InvGpZone.HOTBAR
        invGpCursor     = 0
        activity.invOverlay.visibility = View.GONE
        activity.findViewById<View>(R.id.cave_game_area).visibility = View.VISIBLE
        hud.setInventoryOpen(false)
        hud.updateHotbarUI(renderer.hotbar, renderer.selectedSlot)
        renderer.gamePaused = false
    }

    fun refreshPagedAdapter() {
        val needle=folded(query)
        gridIndices = (0 until hotbarBase()).filter { index ->
            val type=invSlots[index] ?: return@filter false
            (renderer.inventory[type] ?: 0)>0 && name(type).contains(needle) &&
                (bankFilter==null || renderer.itemMode(type)==bankFilter) &&
                (!onlyFavorites || isFavorite(type)) && (!onlyRecent || type in recent.take(24)) &&
                (categoryIndex==0 || BlockRegistry.get(type)?.creativeTab==categoryKeys[categoryIndex])
        }.sortedWith(compareByDescending<Int> { isFavorite(invSlots[it]!!) }.thenComparator { a,b ->
            val x=invSlots[a]!!;val y=invSlots[b]!!
            when(sortOrder) { 1 -> countAt(b).compareTo(countAt(a))
                2 -> (recent.indexOf(x).takeIf { it>=0 } ?: Int.MAX_VALUE).compareTo(recent.indexOf(y).takeIf { it>=0 } ?: Int.MAX_VALUE)
                else -> name(x).compareTo(name(y)) }.takeIf { it!=0 } ?: name(x).compareTo(name(y))
        })
        currentPage = currentPage.coerceIn(0, pageCount() - 1)
        pagedAdapter?.notifyDataSetChanged()
        invPager?.setCurrentItem(currentPage, false)
        if (::ui.isInitialized) {
            ui.pager.visibility = if (browsingCraft) View.GONE else View.VISIBLE
            ui.footer.visibility = if (browsingCraft) View.GONE else View.VISIBLE
            ui.category.visibility = if (!browsingCraft) View.VISIBLE else View.GONE
            ui.summary.text=activity.getString(R.string.cave_catalog_summary,gridIndices.size,hotbarBase())
            ui.craftable.visibility = if (browsingCraft) View.VISIBLE else View.GONE
            if (!browsingCraft) { ui.empty.setText(if(hotbarBase()==0) R.string.cave_inventory_empty_bag else R.string.cave_ui_empty_search);ui.empty.visibility = if (gridIndices.isEmpty()) View.VISIBLE else View.GONE }
            updateActions()
        }
        updatePageIndicator()
    }

    private fun pageCount() = ((gridIndices.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    private fun updatePageIndicator() {
        pageIndicatorTv?.text = activity.getString(R.string.cave_catalog_page, currentPage + 1, pageCount(),gridIndices.size)
        if (::ui.isInitialized) { ui.previous.isEnabled = currentPage > 0; ui.next.isEnabled = currentPage + 1 < pageCount() }
    }

    // ── Panneau info ──────────────────────────────────────────────────────────

    fun updateInfoPanel() {
        val recipe = selectedRecipe
        val dp = activity.resources.displayMetrics.density
        updateActions()
        ui.ingredients.removeAllViews()
        if (recipe != null) {
            val weaponDefId = recipe.resultItemId
            if (weaponDefId != null) {
                infoSpriteView?.background = weaponSpriteDrawable(weaponDefId, 6f)
                infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                infoNameTv?.text  = activity.weaponName(weaponDefId)
                infoCountTv?.text = activity.getString(R.string.cave_catalog_craft_quantity,1)
            } else {
                infoSpriteView?.background = activity.blockDrawable(recipe.result, 6f)
                infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                infoNameTv?.text  = activity.blockName(recipe.result)
                infoCountTv?.text = activity.getString(R.string.cave_catalog_craft_quantity,recipe.resultCount)
            }
            infoIngredientsTv?.text = activity.getString(R.string.cave_ui_available_batches, recipe.maxCraftable(renderer.inventory, renderer.nearbyStations))
            infoDivider?.visibility       = View.VISIBLE
            infoIngredientsTv?.visibility = View.VISIBLE
            ingredientTiles(recipe)
        } else {
            val type = if (browsingCraft) null else selectedType()
            val isWeapon = type != null && WeaponInstanceRegistry.isWeapon(type)
            if (isWeapon) {
                val instance = WeaponInstanceRegistry.get(type!!)
                val def = instance?.let { com.Atom2Universe.app.games.caves.node.ItemRegistry.get(it.defId) }
                infoSpriteView?.background = activity.blockDrawable(type, 6f)
                val rarityColor = weaponRarityColor(instance?.rarity ?: ItemRarity.COMMON)
                val rarityLabel = instance?.rarity?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "?"
                val baseName = def?.id?.let { activity.weaponName(it) } ?: "?"
                infoNameTv?.setTextColor(rarityColor)
                infoNameTv?.text  = "$baseName · $rarityLabel"
                infoCountTv?.text = ""
                infoDivider?.visibility = View.VISIBLE
                val dmg   = instance?.rolledDamage ?: 0
                val speed = def?.attackSpeedMs ?: 0
                val extra = instance?.rolledStats?.entries?.joinToString("\n") { (k, v) ->
                    val label = affixLabel(k)
                    val suffix = affixSuffix(k)
                    "$label: $v$suffix"
                } ?: ""
                infoIngredientsTv?.text = buildString {
                    append(activity.getString(R.string.cave_ui_damage, dmg, speed))
                    if (extra.isNotEmpty()) { append("\n"); append(extra) }
                    val description=activity.resources.getIdentifier("cave_weapon_style_${def?.id}","string",activity.packageName)
                    if(description!=0) { append("\n");append(activity.getString(description)) }
                }
                infoIngredientsTv?.visibility = View.VISIBLE
            } else {
                infoDivider?.visibility       = View.GONE
                infoIngredientsTv?.visibility = View.GONE
                if (type == null) {
                    infoSpriteView?.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; setColor(0x33FFFFFF)
                        cornerRadius = 6 * dp
                    }
                    infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                    infoNameTv?.text  = activity.getString(if (browsingCraft) R.string.cave_ui_select_recipe else R.string.cave_ui_select_item)
                    infoCountTv?.text = ""
                } else {
                    infoSpriteView?.background = activity.blockDrawable(type, 6f)
                    infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                    infoNameTv?.text  = activity.blockName(type)
                    infoCountTv?.text = activity.getString(R.string.cave_ui_owned, countAt(selectedSlotIdx))
                    infoIngredientsTv?.text = itemDescription(type)
                    infoDivider?.visibility = View.VISIBLE
                    infoIngredientsTv?.visibility = View.VISIBLE
                }
            }
        }
        ui.resizeDetails()
    }

    internal fun itemDescription(type: Short): String {
        WeaponInstanceRegistry.get(type)?.let { instance ->
            val definition=com.Atom2Universe.app.games.caves.node.ItemRegistry.get(instance.defId)
            return activity.getString(R.string.cave_ui_damage,instance.rolledDamage ?: 0,definition?.attackSpeedMs ?: 0)
        }
        val def=BlockRegistry.get(type)
        val drop=BlockRegistry.harvestDrop(type)
        return when {
                        type in E.melee -> E.melee.getValue(type).let { p -> activity.getString(R.string.cave_melee_info,p.damage,p.reach,p.recovery,p.targets) }
                        E.armor(type)>0f -> activity.getString(R.string.cave_armor_description,(E.armor(type)*100).toInt())
                        type==E.SHIELD -> activity.getString(R.string.cave_shield_description,82,50)
                        type==E.ROD || type==E.BAIT -> activity.getString(R.string.cave_fishing_aim)
                        type in E.RIVER_FISH..E.DEEP_FISH || type==E.FISH_OIL -> activity.getString(R.string.cave_fish_description)
                        type==E.FORGE || type==E.ANVIL || type in E.BARREL..E.BLANK -> activity.getString(R.string.cave_forge_description)
                        type==F.CHARM || type==F.HEARTH -> activity.getString(R.string.cave_travel_hint)
                        type==F.MARKET_BELL || type==F.TOKEN -> activity.getString(R.string.cave_trade_hint)
                        type==F.SHEARS || type in F.WOOL..F.TRUFFLE -> activity.getString(R.string.cave_husbandry_hint)
                        type in F.PRESS..F.LOOM -> activity.getString(R.string.cave_machine_power)
                        type==F.KILN -> activity.getString(R.string.cave_machine_kiln)
                        type==F.TROUGH -> activity.getString(R.string.cave_trough_hint)
                        type==F.VAT -> activity.getString(R.string.cave_machine_vat)
                        F.healing(type)>0 -> activity.getString(R.string.cave_frontier_food_hint,F.healing(type))
                        F.toolIndex(type)>=0 -> activity.getString(R.string.cave_frontier_tool_hint)
                        type==F.CHEST || type==F.CACHE -> activity.getString(R.string.cave_storage_hint)
                        type==F.MILL || type==F.WATERWHEEL || type==F.SHAFT -> activity.getString(R.string.cave_mill_hint)
                        type==F.COOKER -> activity.getString(R.string.cave_cooker_hint)
                        type==F.COMPOSTER || type==F.COMPOST -> activity.getString(R.string.cave_composter_hint)
                        type==F.HOPPER -> activity.getString(R.string.cave_frontier_hopper_hint)
                        type==F.MORTAR -> activity.getString(R.string.cave_frontier_mortar_hint)
                        com.Atom2Universe.app.games.caves.node.FarmItems.seedCrop(type) != null -> {
                            val crop = com.Atom2Universe.app.games.caves.node.FarmItems.seedCrop(type)!!
                            activity.getString(R.string.cave_farm_seed_hint,
                                com.Atom2Universe.app.games.caves.node.FarmItems.durationMs(crop)/60_000L)
                        }
                        com.Atom2Universe.app.games.caves.node.FarmItems.produceCrop(type) != null -> activity.getString(R.string.cave_farm_produce_hint)
                        type == com.Atom2Universe.app.games.caves.node.FarmSoil.HOE -> activity.getString(R.string.cave_block_desc_hoe)
                        type == com.Atom2Universe.app.games.caves.node.FarmSoil.FARMLAND -> activity.getString(R.string.cave_block_desc_farmland)
                        type == 8000.toShort() -> activity.getString(R.string.cave_craft_furnace_hint)
                        type == 8001.toShort() -> activity.getString(R.string.cave_craft_table_hint)
                        type == 9990.toShort() || type == 9991.toShort() -> activity.getString(R.string.cave_craft_bucket_hint)
                        def?.placeable == false -> activity.getString(R.string.cave_ui_raw_resource_hint)
                        drop == null -> activity.getString(R.string.cave_ui_harvest_none)
                        else -> activity.getString(R.string.cave_ui_harvest_result, drop.second, activity.blockName(drop.first))
                    }
    }

    /** Icône d'un résultat de recette d'arme (pas encore d'instance rollée, juste l'aperçu). */
    private fun weaponSpriteDrawable(defId: String, cornerDp: Float): android.graphics.drawable.Drawable {
        val def = com.Atom2Universe.app.games.caves.node.ItemRegistry.get(defId)
        return com.Atom2Universe.app.games.caves.render.WeaponIconDrawable(activity.assets,
            def?.weaponType ?: def?.sprite ?: defId,ItemRarity.COMMON)
    }

    private fun ingredientTiles(recipe: CraftDef) {
        val groups=recipe.groups.associateBy { group -> group.ids.maxBy { renderer.inventory[it] ?: 0 } }
        val entries=recipe.ingredients.map { Triple(it.first,it.second,(renderer.inventory[it.first] ?: 0)>=it.second) } +
            recipe.groups.map { group -> Triple(group.ids.maxBy { renderer.inventory[it] ?: 0 },group.count,group.available(renderer.inventory)>=group.count) } +
            recipe.tools.map { Triple(it,1,(renderer.inventory[it] ?: 0)>0) } +
            listOfNotNull(recipe.station?.let { Triple(it,1,it in renderer.nearbyStations) })
        for(batch in entries.chunked(3)) {
            val row=LinearLayout(activity)
            for((id,n,ready) in batch) {
                val group=groups[id]
                val title=group?.let { craftGroupName(it.tag) } ?: activity.blockName(id)
                val tile=CaveItemTile(activity).apply {
                    bind(activity.blockDrawable(id,4f),title,n,pinned=id in recipe.tools || id==recipe.station,available=ready)
                    val state=activity.getString(if(ready) R.string.cave_ui_ready else R.string.cave_ui_missing)
                    contentDescription=if(id==recipe.station) activity.getString(R.string.cave_catalog_station,title,state)
                        else activity.getString(R.string.cave_catalog_ingredient,title,group?.available(renderer.inventory) ?: (renderer.inventory[id] ?: 0).toLong(),n,state)
                    tooltipText=contentDescription
                    setOnClickListener {
                        val recipes=CraftRegistry.all().filter { it.result in (group?.ids ?: listOf(id)) }
                        if(recipes.isEmpty()) {
                            AlertDialog.Builder(activity).setTitle(activity.blockName(id)).setMessage(contentDescription)
                                .setPositiveButton(android.R.string.ok,null).show()
                        } else AlertDialog.Builder(activity).setTitle(R.string.cave_catalog_make_ingredient)
                            .setItems(recipes.map { recipeName(it) }.toTypedArray()) { _,index ->
                                selectedRecipe?.let { recipeHistory.addLast(it) };selectedRecipe=recipes[index];updateInfoPanel();craftingAdapter?.notifyDataSetChanged()
                            }.setNegativeButton(android.R.string.cancel,null).show()
                    }
                }
                row.addView(tile,LinearLayout.LayoutParams(CaveItemTile.edge(activity),CaveItemTile.edge(activity)).apply { setMargins(2,3,2,3) })
            }
            ui.ingredients.addView(row)
        }
        recipe.station?.let { station -> ui.ingredients.addView(TextView(activity).apply {
            text=activity.getString(R.string.cave_catalog_station,activity.blockName(station),activity.getString(if(station in renderer.nearbyStations) R.string.cave_ui_ready else R.string.cave_ui_missing))
            textSize=11f;setTextColor(if(station in renderer.nearbyStations) CaveUiStyle.ACCENT else CaveUiStyle.WARNING)
        }) }
    }

    private fun affixLabel(key: String): String = when (key) {
        "crit_chance"   -> activity.getString(R.string.cave_affix_crit_chance)
        "crit_dmg"      -> activity.getString(R.string.cave_affix_crit_dmg)
        "attack_speed"  -> activity.getString(R.string.cave_affix_attack_speed)
        "life_steal"    -> activity.getString(R.string.cave_affix_life_steal)
        "bleed_chance"    -> activity.getString(R.string.cave_affix_bleed_chance)
        "electric_chance" -> activity.getString(R.string.cave_affix_electric_chance)
        "freeze_chance"   -> activity.getString(R.string.cave_affix_freeze_chance)
        "execute"       -> activity.getString(R.string.cave_affix_execute)
        "aoe_splash"    -> activity.getString(R.string.cave_affix_aoe_splash)
        "thorns"        -> activity.getString(R.string.cave_affix_thorns)
        "poison_chance" -> activity.getString(R.string.cave_affix_poison_chance)
        "fire_chance"   -> activity.getString(R.string.cave_affix_fire_chance)
        else            -> key.replace('_', ' ').split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun affixSuffix(key: String): String = when (key) {
        "crit_dmg" -> "%"
        "thorns"   -> "%"
        else       -> "%"
    }

    private fun weaponRarityColor(rarity: ItemRarity) = when (rarity) {
        ItemRarity.COMMON    -> 0xFFAAAAAA.toInt()
        ItemRarity.MAGIC     -> 0xFF4488FF.toInt()
        ItemRarity.RARE      -> CaveUiStyle.ACCENT
        ItemRarity.EPIC      -> 0xFFCC44FF.toInt()
        ItemRarity.LEGENDARY -> 0xFFFF8800.toInt()
    }

    private fun weaponSellPrice(id: Short): Int {
        val inst = WeaponInstanceRegistry.get(id) ?: return 0
        val tierMult = inst.tier.coerceAtLeast(1) * 5
        val rarityMult = when (inst.rarity) {
            ItemRarity.COMMON    -> 1
            ItemRarity.MAGIC     -> 2
            ItemRarity.RARE      -> 4
            ItemRarity.EPIC      -> 8
            ItemRarity.LEGENDARY -> 15
        }
        return tierMult * rarityMult
    }

    // ── Crafting ──────────────────────────────────────────────────────────────

    fun updateCraftingList() {
        val type = selectedType()
        sellPanel?.visibility = if (!browsingCraft && type != null && WeaponInstanceRegistry.get(type) != null) View.VISIBLE else View.GONE
        if (type != null) sellPriceTv?.text = activity.getString(R.string.cave_inv_sell_price_label) + ": " + weaponSellPrice(type) + " " + activity.getString(R.string.cave_inv_sell_ward_stones)
        rightHeaderTv?.setText(if (browsingCraft) R.string.cave_ui_workshop else R.string.cave_ui_details)
        if(!browsingCraft) { craftingRecyclerView?.visibility=View.GONE;updateActions();return }
        val needle=folded(query)
        val recipes = CraftRegistry.all().filter { r ->
            (if(ui.craftable.isChecked) r.canCraft(renderer.inventory, renderer.nearbyStations)
                else r.ingredients.any { (id,_) -> (renderer.inventory[id] ?: 0)>0 } || r.groups.any { it.available(renderer.inventory)>0 }) &&
                (bankFilter==null || (if(r.resultItemId!=null) HotbarMode.COMBAT else renderer.itemMode(r.result))==bankFilter) &&
                (!onlyFavorites || recipeKey(r) in favoriteRecipes) &&
                (!onlyRecent || r.result in recent.take(24)) &&
                (relatedType == null || relatedType in r.inputIds) &&
                (folded(recipeName(r)).contains(needle) || r.inputIds.any { name(it).contains(needle) } || r.groups.any { folded(craftGroupName(it.tag)).contains(needle) })
        }.sortedWith(compareByDescending<CraftDef> { recipeKey(it) in favoriteRecipes }.thenByDescending { it.canCraft(renderer.inventory, renderer.nearbyStations) }.thenBy { recipeName(it) })
        ui.empty.setText(if(ui.craftable.isChecked) R.string.cave_ui_empty_search else R.string.cave_catalog_no_known_recipe)
        if(browsingCraft) ui.summary.text=activity.getString(R.string.cave_catalog_recipes,recipes.size)
        craftingAdapter?.recipes = recipes
        craftingAdapter?.notifyDataSetChanged()
        craftingRecyclerView?.visibility = if (browsingCraft && recipes.isNotEmpty()) View.VISIBLE else View.GONE
        craftingEmptyTv?.visibility = if (browsingCraft && recipes.isEmpty() || !browsingCraft && gridIndices.isEmpty()) View.VISIBLE else View.GONE
        updateActions()
    }

    private fun recipeName(recipe: CraftDef) = recipe.resultItemId?.let { activity.weaponName(it) } ?: activity.blockName(recipe.result)

    private fun craftGroupName(tag: String): String = activity.getString(when (tag) {
        "logs" -> R.string.cave_craft_logs
        "planks" -> R.string.cave_craft_planks
        "fuel" -> R.string.cave_craft_fuel
        "sand" -> R.string.cave_craft_sand
        else -> R.string.cave_craft_stone
    })

    fun doSell(id: Short) {
        if (WeaponInstanceRegistry.get(id) == null || (renderer.inventory[id] ?: 0) <= 0) return
        val price = weaponSellPrice(id)
        // Rémunération en ward stones
        if (price > 0) {
            val WARD_STONE = com.Atom2Universe.app.games.caves.world.WARD_STONE
            renderer.inventory[WARD_STONE] = (renderer.inventory[WARD_STONE] ?: 0) + price
            val existing = invSlots.filterNotNull().toSet()
            addNewTypeByCategory(WARD_STONE)
        }
        // Retirer l'arme
        renderer.inventory.remove(id)
        for (i in renderer.hotbar.indices) { if (renderer.hotbar[i] == id) renderer.hotbar[i] = null }
        WeaponInstanceRegistry.free(id)
        names.remove(id)
        selectedSlotIdx = -1
        previousCounts=renderer.inventory.toMap()
        syncHotbar()
        refreshPagedAdapter()
        hud.updateHotbarForInventory()
        updateInfoPanel()
        updateCraftingList()
        activity.saveWorldAsync()
    }

    fun doCraft(recipe: CraftDef, batches: Int = 1) {
        if (batches !in 1..64 || recipe.maxCraftable(renderer.inventory, renderer.nearbyStations) < batches) {
            ui.status.setText(R.string.cave_ui_missing); return
        }
        val allocated = mutableListOf<Short>()
        val consumption = recipe.consumption(renderer.inventory, batches, renderer.nearbyStations) ?: return
        val weaponDefId = recipe.resultItemId
        if (weaponDefId != null) {
            val prepared = runCatching {
                repeat(batches) {
                    val instance = (if(recipe.station!=null) com.Atom2Universe.app.games.caves.node.ItemRegistry.forgedInstance(weaponDefId) else com.Atom2Universe.app.games.caves.node.ItemRegistry.rollInstance(weaponDefId, kotlin.random.Random.Default))
                        ?: error("Unknown craft output")
                    allocated += WeaponInstanceRegistry.allocate(instance)
                }
            }
            if (prepared.isFailure) {
                allocated.forEach { WeaponInstanceRegistry.free(it) }
                ui.status.setText(R.string.cave_ui_craft_failed); return
            }
        } else if ((renderer.inventory[recipe.result] ?: 0).toLong() + recipe.resultCount.toLong() * batches > Int.MAX_VALUE) {
            ui.status.setText(R.string.cave_ui_craft_failed); return
        }
        for ((type, need) in consumption) {
            val after = (renderer.inventory[type] ?: 0) - need
            if (after <= 0) {
                renderer.inventory.remove(type)
                for(i in renderer.hotbar.indices) if(renderer.hotbar[i]==type) renderer.hotbar[i]=null
            } else renderer.inventory[type] = after
        }
        if (weaponDefId != null) for (id in allocated) {
            renderer.inventory[id] = 1; addNewTypeByCategory(id)
        } else {
            val out = recipe.result
            renderer.inventory[out] = (renderer.inventory[out] ?: 0) + recipe.resultCount * batches
            addNewTypeByCategory(out)
        }
        selectedSlotIdx = -1
        onInventoryChanged(renderer.inventory.toMap()); refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
        ui.status.text = activity.getString(R.string.cave_ui_crafted, recipeName(recipe), if (weaponDefId != null) batches else recipe.resultCount * batches)
        activity.saveWorldAsync()
    }

    // ── Slot click hotbar réelle (pendant inventaire) ─────────────────────────

    fun onOverlayActiveSlotClick(i: Int) {
        selectInventorySlot(hotbarBase() + i)
        if(selectedType()!=null) hud.overlayActiveFrames[i]?.let { ui.showDetails(it) }
    }

    // ── Navigation manette inventaire ─────────────────────────────────────────

    fun moveInvCursor(dx: Int, dy: Int) {
        val base = hotbarBase()
        when (invGpZone) {
            InvGpZone.GRID -> {
                val pos = gridIndices.indexOf(invGpCursor).coerceAtLeast(0)
                val next = pos + dx + dy * gridColumns
                if (next < 0 || next >= gridIndices.size) {
                    invGpZone = InvGpZone.HOTBAR; invGpCursor = base
                } else {
                    invGpCursor = gridIndices[next]; currentPage = next / pageSize
                    invPager?.setCurrentItem(currentPage, false)
                }
            }
            InvGpZone.HOTBAR -> {
                if (dy < 0 && gridIndices.isNotEmpty() && !browsingCraft) {
                    invGpZone = InvGpZone.GRID
                    invGpCursor = gridIndices[minOf((currentPage + 1) * pageSize, gridIndices.size) - 1]
                } else invGpCursor = base + Math.floorMod(invGpCursor - base + dx, CaveActivity.ACTIVE_SIZE)
            }
            InvGpZone.CRAFTING -> {
                val count = craftingAdapter?.itemCount ?: 0
                if (count > 0) { invGpCursor = Math.floorMod(invGpCursor + dx + dy*recipeColumns, count); craftingRecyclerView?.scrollToPosition(invGpCursor) }
            }
        }
        updatePageIndicator(); refreshInvGpCursorUi()
    }

    fun changePage(delta: Int) {
        currentPage = (currentPage + delta).coerceIn(0, pageCount() - 1)
        invPager?.setCurrentItem(currentPage, true)
        if (invGpZone == InvGpZone.GRID) invGpCursor = gridIndices.getOrNull(currentPage * pageSize) ?: hotbarBase()
        updatePageIndicator(); refreshInvGpCursorUi()
    }

    fun exitCraftingZone() { showLibrary(false) }

    fun handleInvGamepadMotion(event: MotionEvent): Boolean {
        if(storagePageOpen) return true
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val now = System.currentTimeMillis()

        // Stick droit : changement de page
        val rsx = event.getAxisValue(MotionEvent.AXIS_Z)
        val rdx = when { rsx < -0.5f -> -1; rsx > 0.5f -> 1; else -> 0 }
        if (rdx != 0 && now - invGpRightLastMs >= INV_GP_REPEAT_MS) {
            invGpRightLastMs = now
            changePage(rdx)
            return true
        }

        // Stick gauche / croix : déplacement curseur
        if (now - invGpLastMoveMs < INV_GP_REPEAT_MS) return true
        val sx = event.getAxisValue(MotionEvent.AXIS_X); val sy = event.getAxisValue(MotionEvent.AXIS_Y)
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X); val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val rawX = if (sx != 0f) sx else hx; val rawY = if (sy != 0f) sy else hy
        val dx = when { rawX < -0.5f -> -1; rawX > 0.5f -> 1; else -> 0 }
        val dy = when { rawY < -0.5f -> -1; rawY > 0.5f -> 1; else -> 0 }
        if (dx == 0 && dy == 0) return true
        invGpLastMoveMs = now
        moveInvCursor(dx, dy)
        return true
    }

    fun handleInvGamepadKey(keyCode: Int): Boolean {
        if(storagePageOpen) {
            if(keyCode==KeyEvent.KEYCODE_BUTTON_B || keyCode==KeyEvent.KEYCODE_BACK) { closeInventory();return true }
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_Y -> { showLibrary(!browsingCraft); true }
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (invGpZone == InvGpZone.CRAFTING) {
                    selectedRecipe = craftingAdapter?.recipes?.getOrNull(invGpCursor)
                    craftingAdapter?.notifyDataSetChanged(); updateInfoPanel();if(selectedRecipe!=null) craftingRecyclerView?.let { ui.showDetails(it) }
                } else { selectInventorySlot(invGpCursor);if(!assigningShortcut && selectedType()!=null) invPager?.let { ui.showDetails(it) } }
                true
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                if (browsingCraft) selectedRecipe?.let { doCraft(it) }
                else if (selectedType() != null) { assigningShortcut = true; invGpZone = InvGpZone.HOTBAR; invGpCursor = hotbarBase(); updateActions() }
                true
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                when { assigningShortcut -> { assigningShortcut = false; updateActions() }
                    browsingCraft -> showLibrary(false)
                    else -> closeInventory() }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { moveInvCursor(-1, 0); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveInvCursor(1, 0); true }
            KeyEvent.KEYCODE_DPAD_UP -> { moveInvCursor(0, -1); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveInvCursor(0, 1); true }
            else -> false
        }
    }

    fun refreshInvGpCursorUi() {
        pagedAdapter?.notifyDataSetChanged()
        hud.updateHotbarForInventory()
        craftingAdapter?.notifyDataSetChanged()
    }

    // ── Adapter pager (une page = RecyclerView grille) ────────────────────────

    inner class PagedInvPagerAdapter(var cellSize: Int) : RecyclerView.Adapter<PagedInvPagerAdapter.PageVH>() {

        inner class PageVH(val rv: RecyclerView) : RecyclerView.ViewHolder(rv)

        fun pageCount() = ((gridIndices.size + pageSize - 1) / pageSize).coerceAtLeast(1)

        override fun getItemCount() = pageCount()

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): PageVH {
            val rv = RecyclerView(parent.context).apply {
                layoutManager = GridLayoutManager(parent.context, gridColumns)
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return PageVH(rv)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val start = position * pageSize
            val indices = gridIndices.drop(start).take(pageSize)
            holder.rv.adapter = PageSlotAdapter(indices.map { invSlots.getOrNull(it) }, indices, cellSize)
        }
    }

    // ── Adapter d'une page de grille ──────────────────────────────────────────

    inner class PageSlotAdapter(
        val items: List<Short?>,
        val indices: List<Int>,
        val cellSize: Int
    ) : RecyclerView.Adapter<PageSlotAdapter.VH>() {

        inner class VH(val tile: CaveItemTile): RecyclerView.ViewHolder(tile)
        override fun getItemCount()=items.size
        override fun onCreateViewHolder(parent: ViewGroup,vt: Int): VH = VH(CaveItemTile(parent.context).apply {
            layoutParams=RecyclerView.LayoutParams(cellSize,cellSize).apply { val gap=CaveUiStyle.dp(activity,2);setMargins(gap,gap,gap,gap) }
        })
        override fun onBindViewHolder(holder: VH,position: Int) {
            val index=indices[position];val id=items[position] ?: return
            holder.tile.bind(activity.blockDrawable(id,3f),activity.blockName(id),countAt(index),
                index==selectedSlotIdx || invGpZone==InvGpZone.GRID && index==invGpCursor,isFavorite(id),
                accent=WeaponInstanceRegistry.get(id)?.let { weaponRarityColor(it.rarity) })
            CaveInventoryGestures.bind(holder.tile,
                tap={ holder.bindingAdapterPosition.takeIf { it!=RecyclerView.NO_POSITION }?.let { selectInventorySlot(indices[it]);ui.showDetails(holder.tile) } },
                favorite={ toggleFavorite(id) },
                drag={ holder.bindingAdapterPosition.takeIf { it!=RecyclerView.NO_POSITION }?.let { startSlotDrag(holder.tile,indices[it]) } })
            holder.tile.setOnDragListener(makeSlotDragListener { holder.bindingAdapterPosition.takeIf { it!=RecyclerView.NO_POSITION }?.let { indices.getOrNull(it) } ?: -1 })
        }
    }

    // ── Adapter crafting ──────────────────────────────────────────────────────

    inner class CraftingAdapter(var recipes: List<CraftDef>): RecyclerView.Adapter<CraftingAdapter.VH>() {
        inner class VH(val tile: CaveItemTile): RecyclerView.ViewHolder(tile)
        override fun getItemCount()=recipes.size
        override fun onCreateViewHolder(parent: ViewGroup,viewType: Int)=VH(CaveItemTile(parent.context).apply {
            layoutParams=RecyclerView.LayoutParams(CaveItemTile.edge(activity),CaveItemTile.edge(activity)).apply { val gap=CaveUiStyle.dp(activity,2);setMargins(gap,gap,gap,gap) }
        })
        override fun onBindViewHolder(holder: VH,position: Int) {
            val recipe=recipes[position];val ready=recipe.canCraft(renderer.inventory,renderer.nearbyStations)
            holder.tile.bind(recipe.resultItemId?.let { weaponSpriteDrawable(it,4f) } ?: activity.blockDrawable(recipe.result,4f),
                recipeName(recipe),recipe.resultCount,recipe==selectedRecipe || invGpZone==InvGpZone.CRAFTING && position==invGpCursor,favorite=recipeKey(recipe) in favoriteRecipes,available=ready)
            holder.tile.contentDescription=activity.getString(R.string.cave_catalog_recipe_state,recipeName(recipe),activity.getString(if(ready) R.string.cave_ui_ready else R.string.cave_ui_missing))
            holder.tile.setOnClickListener { recipeHistory.clear();selectedRecipe=recipe;notifyDataSetChanged();updateInfoPanel();ui.showDetails(holder.tile) }
            holder.tile.setOnLongClickListener { toggleRecipeFavorite(recipe);true }
        }
    }
}
