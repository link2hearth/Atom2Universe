package com.Atom2Universe.app.games.caves

import android.content.ClipData
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops

/** Every drag carries a persistent stack identity, never a global transfer quantity. */
internal class CaveStorageBrowser(private val a: CaveActivity) {
    private var visible=false
    private var view: FrontierWorkshops.View?=null
    private var inventory: Map<Short,Int> = emptyMap()
    private var playerStacks: List<CaveStackInventory.Stack> = emptyList()
    private var busy=false
    private var opening=false
    private var query=""
    private var generation=0
    private val bubbles=CaveStackPopup(a)
    private lateinit var bag: RecyclerView
    private lateinit var box: RecyclerView
    private lateinit var bagTitle: TextView
    private lateinit var boxTitle: TextView
    /** Ovens: the fuel and product compartments beside the ingredients ([box]). */
    private var zoned=false
    private lateinit var fuelBox: RecyclerView
    private lateinit var outBox: RecyclerView
    private lateinit var fuelTitle: TextView
    private lateinit var outTitle: TextView
    private lateinit var status: TextView
    private lateinit var production: Button
    private lateinit var matching: Button
    private lateinit var takeAll: Button
    private lateinit var progress: ProgressBar
    private val barTiles=ArrayList<CaveItemTile>()
    private val accents=Regex("\\p{M}+")
    private fun folded(text: String)=java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replace(accents,"")
    private data class TransferDrag(val key: Long,val player: Boolean,val generation: Int)
    private fun dp(n: Int)=CaveUiStyle.dp(a,n)
    private fun label()=TextView(a).apply { textSize=12f;setTextColor(CaveUiStyle.MUTED);setPadding(dp(4),dp(2),dp(4),dp(2)) }
    fun show(snapshot: FrontierWorkshops.View,items: Map<Short,Int>) {
        if(a.isFinishing || a.isDestroyed || opening || visible) return
        opening=true;a.renderer.gamePaused=true;a.releaseGameInputs()
        a.glView.queueEvent {
            a.renderer.syncInventoryStacks()
            val fresh=a.renderer.workshops.view(snapshot.pos)
            val counts=a.renderer.inventory.toMap();val stacks=a.renderer.inventoryStacks.snapshot()
            a.runOnUiThread {
                opening=false
                if(a.isFinishing || a.isDestroyed) return@runOnUiThread
                if(fresh==null) { a.renderer.gamePaused=false;return@runOnUiThread }
                playerStacks=stacks;showReady(fresh,counts)
            }
        }
    }
    private fun showReady(snapshot: FrontierWorkshops.View,items: Map<Short,Int>) {
        generation++;visible=true
        view=snapshot;inventory=items;busy=false;query="";barTiles.clear();zoned=snapshot.zoned
        val root=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL
            isFocusableInTouchMode=true
        }
        val header=LinearLayout(a).apply {
            gravity=Gravity.CENTER_VERTICAL
            background=CaveUiStyle.bubble(a)
            setPadding(dp(10),dp(4),dp(6),dp(4))
        }
        root.addView(header)
        header.addView(label().apply { text=if(snapshot.block==com.Atom2Universe.app.games.caves.node.ExpeditionItems.FORGE)
            com.Atom2Universe.app.games.caves.node.MineralItems.forgeName(a,snapshot.forgeTier) else a.blockName(snapshot.block)
            textSize=16f;setTextColor(CaveUiStyle.TEXT);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,dp(44),1f))
        header.addView(EditText(a).apply {
            setSingleLine();setHint(R.string.cave_ui_search);textSize=14f;setTextColor(CaveUiStyle.TEXT);setHintTextColor(CaveUiStyle.MUTED)
            addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int)=Unit
                override fun afterTextChanged(s: Editable?)=Unit
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                    query=s?.toString().orEmpty().trim();if(::bag.isInitialized && visible) refresh()
                }
            })
        },LinearLayout.LayoutParams(0,dp(44),1.2f))
        fun action(kind: String,res: Int,run: (View)->Unit)=Button(a).apply {
            CaveUiStyle.icon(this,kind,a.getString(res));setOnClickListener { run(it) }
            header.addView(this,LinearLayout.LayoutParams(dp(44),dp(44)))
        }
        production=action("craft",R.string.cave_machine_choose) { chooseProduction(it) }
        takeAll=action("takeall",R.string.cave_storage_take_all) { takeEverything() }
        matching=action("pin",R.string.cave_storage_matching) { depositMatching() }
        action("info",R.string.cave_ui_details) { bubbles.message(it,a.getString(R.string.cave_ui_details),machineDetails()) }
        action("close",R.string.cave_storage_close) { a.invManager.closeInventory() }
        progress=ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal).apply { max=100;progressTintList=android.content.res.ColorStateList.valueOf(CaveUiStyle.ACCENT) }
        root.addView(progress,LinearLayout.LayoutParams(-1,dp(4)))
        val columns=LinearLayout(a)
        fun side(player: Boolean): RecyclerView {
            val column=LinearLayout(a).apply {
                orientation=LinearLayout.VERTICAL;background=CaveUiStyle.bubble(a)
                setPadding(dp(6),dp(4),dp(6),dp(6))
                elevation=dp(6).toFloat()
            }
            val top=LinearLayout(a).apply { gravity=Gravity.CENTER_VERTICAL }
            val title=label();if(player) bagTitle=title else boxTitle=title
            top.addView(title,LinearLayout.LayoutParams(0,dp(36),1f));column.addView(top)
            val grid=RecyclerView(a).apply {
                layoutManager=CaveItemGridLayout(a);clipToPadding=false;setPadding(dp(2),dp(2),dp(2),dp(2))
                itemAnimator=null
                setOnDragListener(dropTarget(player))
            }
            for(direction in listOf(-1,1)) top.addView(Button(a).apply {
                CaveUiStyle.button(this);setText(if(direction<0) R.string.cave_stack_up else R.string.cave_stack_down)
                contentDescription=a.getString(if(direction<0) R.string.cave_ui_previous else R.string.cave_ui_next)
                setOnClickListener { grid.smoothScrollBy(0,direction*grid.height) }
            },LinearLayout.LayoutParams(dp(36),dp(36)))
            column.setOnDragListener(dropTarget(player))
            column.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
            columns.addView(column,LinearLayout.LayoutParams(0,-1,1f).apply {
                if(player) marginEnd=dp(5) else marginStart=dp(5)
                topMargin=dp(8);bottomMargin=dp(6)
            })
            return grid
        }
        /** An oven's column: ingredients, fuel and products, each its own drop zone. */
        fun ovenSide() {
            val column=LinearLayout(a).apply {
                orientation=LinearLayout.VERTICAL;background=CaveUiStyle.bubble(a)
                setPadding(dp(6),dp(4),dp(6),dp(6));elevation=dp(6).toFloat()
            }
            fun section(zone: Int,weight: Float): Pair<TextView,RecyclerView> {
                val title=label();column.addView(title,LinearLayout.LayoutParams(-1,dp(26)))
                val grid=RecyclerView(a).apply {
                    layoutManager=CaveItemGridLayout(a);clipToPadding=false;setPadding(dp(2),dp(2),dp(2),dp(2))
                    itemAnimator=null;background=CaveUiStyle.bubble(a)
                    setOnDragListener(dropTarget(false,zone=zone))
                }
                column.addView(grid,LinearLayout.LayoutParams(-1,0,weight).apply { bottomMargin=dp(4) })
                return title to grid
            }
            section(FrontierWorkshops.ZONE_INPUT,1.4f).let { boxTitle=it.first;box=it.second }
            section(FrontierWorkshops.ZONE_FUEL,1f).let { fuelTitle=it.first;fuelBox=it.second }
            section(FrontierWorkshops.ZONE_OUTPUT,1f).let { outTitle=it.first;outBox=it.second }
            columns.addView(column,LinearLayout.LayoutParams(0,-1,1f).apply { marginStart=dp(5);topMargin=dp(8);bottomMargin=dp(6) })
        }
        bag=side(true);if(zoned) ovenSide() else box=side(false)
        root.addView(columns,LinearLayout.LayoutParams(-1,0,1f))
        status=label().apply {
            maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setText(R.string.cave_storage_drag_hint)
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            background=CaveUiStyle.bubble(a);setPadding(dp(12),dp(3),dp(12),dp(3))
        }
        root.addView(status)
        barTiles+=a.invManager.showStoragePage(root) {
            bubbles.cancel();visible=false;generation++;view=null
        }
        barTiles.forEachIndexed { slot,tile -> tile.setOnDragListener(dropTarget(true,slot=slot)) }
        root.requestFocus()
        refresh()
    }
    private fun dropTarget(player: Boolean,target: Long?=null,slot: Int?=null,zone: Int=FrontierWorkshops.ZONE_INPUT)=View.OnDragListener { targetView,event ->
        val token=event.localState as? TransferDrag
        val accepts=visible && token!=null && token.generation==generation && !busy
        when(event.action) {
            DragEvent.ACTION_DRAG_STARTED -> accepts
            DragEvent.ACTION_DRAG_ENTERED -> { if(accepts) targetView.alpha=.65f;accepts }
            DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED -> { targetView.alpha=1f;true }
            DragEvent.ACTION_DROP -> {
                targetView.alpha=1f
                if(accepts) {
                    val v=view!!;val move=token!!;var moved=0
                    runMutation({ moved=a.renderer.mutateStorageStack(v.pos,move.key,move.player,target=target,barSlot=slot,transfer=move.player!=player,zone=zone) }) {
                        if(moved>0) status.setText(R.string.cave_stack_moved)
                    }
                }
                accepts
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                if(accepts && targetView is RecyclerView) {
                    if(event.y<dp(32)) targetView.scrollBy(0,-dp(8))
                    if(event.y>targetView.height-dp(32)) targetView.scrollBy(0,dp(8))
                };accepts
            }
            else -> accepts
        }
    }
    private fun bindGestures(tile: CaveItemTile,stack: CaveStackInventory.Stack,player: Boolean) {
        CaveInventoryGestures.bind(tile,active={ !busy && visible },
            tap={ bubbles.info(tile,stack.id,stack.count) },
            favorite={ bubbles.split(tile,stack.id,stack.count,
                favorite={
                    a.invManager.toggleFavorite(stack.id);refresh()
                    status.setText(if(a.invManager.isFavorite(stack.id)) R.string.cave_catalog_favorite_added else R.string.cave_catalog_favorite_removed)
                },apply={ n ->
                    val v=view ?: return@split
                    runMutation({ a.renderer.mutateStorageStack(v.pos,stack.key,player,split=n) }) { status.setText(R.string.cave_stack_split_done) }
                }) },
            drag={ bubbles.cancel();tile.startDragAndDrop(ClipData.newPlainText("cave-stack",stack.key.toString()),View.DragShadowBuilder(tile),TransferDrag(stack.key,player,generation),0) })
    }
    private fun refresh() {
        val v=view ?: return;val needle=folded(query)
        fun filtered(rows: List<CaveStackInventory.Stack>)=rows.filter { folded(a.blockName(it.id)).contains(needle) }.sortedBy { folded(a.blockName(it.id)) }
        val bagRows=filtered(playerStacks.filter { it.slot<0 });val boxRows=filtered(v.stacks)
        bagTitle.text=a.getString(R.string.cave_storage_bag_title,bagRows.size)
        boxTitle.text=a.getString(if(zoned) R.string.cave_oven_input else R.string.cave_storage_box_title,boxRows.size)
        if(zoned) {
            val fuelRows=filtered(v.fuelStacks);val outRows=filtered(v.outputStacks)
            fuelTitle.text=a.getString(R.string.cave_oven_fuel,fuelRows.size);outTitle.text=a.getString(R.string.cave_oven_output,outRows.size)
            fuelBox.adapter=Items(fuelRows,false,FrontierWorkshops.ZONE_FUEL);outBox.adapter=Items(outRows,false,FrontierWorkshops.ZONE_OUTPUT)
            fuelBox.alpha=if(busy) .5f else 1f;outBox.alpha=fuelBox.alpha
        }
        val bagScroll=bag.layoutManager?.onSaveInstanceState();val boxScroll=box.layoutManager?.onSaveInstanceState()
        bag.adapter=Items(bagRows,true);box.adapter=Items(boxRows,false)
        bag.layoutManager?.onRestoreInstanceState(bagScroll);box.layoutManager?.onRestoreInstanceState(boxScroll)
        bag.alpha=if(busy) .5f else 1f;box.alpha=bag.alpha;matching.isEnabled=!busy
        takeAll.isEnabled=!busy && (if(zoned) v.outputStacks else v.stacks).isNotEmpty()
        barTiles.forEachIndexed { slot,tile ->
            val stack=playerStacks.firstOrNull { it.slot==slot }
            tile.bind(stack?.let { a.blockDrawable(it.id,4f) },stack?.let { a.blockName(it.id) } ?: a.getString(R.string.cave_ui_empty_slot),stack?.count ?: 0,favorite=stack?.let { a.invManager.isFavorite(it.id) }==true)
            tile.contentDescription=a.getString(R.string.cave_ui_shortcut_description,slot+1,stack?.let { a.blockName(it.id) } ?: a.getString(R.string.cave_ui_empty_slot))
            if(stack!=null) bindGestures(tile,stack,true) else { CaveInventoryGestures.clear(tile);tile.setOnClickListener(null);tile.setOnLongClickListener(null) }
        }
        val recipes=a.renderer.workshops.recipes
        production.visibility=if(recipes.none { it.machine==v.block }) View.GONE else View.VISIBLE;production.isEnabled=!busy
        production.tooltipText=recipes.getOrNull(v.selection)?.let { a.getString(R.string.cave_machine_selected,it.output.keys.joinToString { id -> a.blockName(id) }) } ?: a.getString(R.string.cave_machine_auto)
        val active=recipes.getOrNull(v.active)
        progress.visibility=if(active!=null) View.VISIBLE else View.GONE
        progress.progress=if(active!=null) (v.progress*100/active.seconds.coerceAtLeast(1)).coerceIn(0,100) else 0
        progress.contentDescription=active?.let { a.getString(R.string.cave_machine_progress,v.progress,it.seconds) }
    }
    private fun machineDetails(): String {
        val v=view ?: return a.getString(R.string.cave_storage_drag_hint);val recipes=a.renderer.workshops.recipes
        return buildList {
            add(a.getString(R.string.cave_storage_drag_hint))
            if(v.block in setOf(com.Atom2Universe.app.games.caves.node.FrontierItems.COOKER,
                    com.Atom2Universe.app.games.caves.node.FrontierItems.VAT)) add(a.getString(R.string.cave_kitchen_recipe_help))
            if(recipes.any { it.machine==v.block && it.power }) {
                add(a.getString(when {
                    v.powered -> R.string.cave_machine_powered
                    v.overloaded -> R.string.cave_machine_overloaded
                    v.conflict -> R.string.cave_machine_conflict
                    else -> R.string.cave_machine_unpowered
                }));add(a.getString(R.string.cave_machine_power))
            }
            if(v.block==com.Atom2Universe.app.games.caves.node.ExpeditionItems.FORGE) {
                add(com.Atom2Universe.app.games.caves.node.MineralItems.forgeName(a,v.forgeTier))
                add(a.getString(R.string.cave_forge_upgrade_hint))
                add(a.getString(R.string.cave_forge_heat,a.resources.getStringArray(R.array.cave_forge_heat_levels)[v.heat]))
                add(a.getString(R.string.cave_forge_hint))
            }
            recipes.getOrNull(v.active)?.let { add(a.getString(R.string.cave_machine_progress,v.progress,it.seconds)) }
        }.joinToString("\n\n")
    }
    private inner class Items(private val rows: List<CaveStackInventory.Stack>,private val player: Boolean,
                              private val zone: Int=FrontierWorkshops.ZONE_INPUT): RecyclerView.Adapter<Items.Holder>() {
        inner class Holder(val tile: CaveItemTile): RecyclerView.ViewHolder(tile)
        override fun getItemCount()=rows.size
        override fun onCreateViewHolder(parent: ViewGroup,viewType: Int)=Holder(CaveItemTile(a).apply {
            layoutParams=RecyclerView.LayoutParams(CaveItemTile.edge(a),CaveItemTile.edge(a)).apply { val gap=dp(2);setMargins(gap,gap,gap,gap) }
        })
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val stack=rows[position]
            holder.tile.bind(a.blockDrawable(stack.id,4f),a.blockName(stack.id),stack.count,favorite=a.invManager.isFavorite(stack.id))
            bindGestures(holder.tile,stack,player);holder.tile.setOnDragListener(dropTarget(player,target=stack.key,zone=zone))
        }
    }
    private fun depositMatching() {
        val v=view ?: return;if(busy) return
        val keys=playerStacks.filter { it.slot<0 && it.id in v.items && !a.invManager.isFavorite(it.id) }
        // In an oven, what already sits in the fuel compartment joins it there.
        val fuel=v.fuelStacks.map { it.id }.toSet()
        runMutation({
            for(stack in keys) a.renderer.mutateStorageStack(v.pos,stack.key,true,transfer=true,notify=false,
                zone=if(stack.id in fuel) FrontierWorkshops.ZONE_FUEL else FrontierWorkshops.ZONE_INPUT)
            a.renderer.changedFrontierInventory()
        }) { }
    }
    /** Chest: the whole content; oven: its finished products only (inputs and fuel stay). */
    private fun takeEverything() {
        val v=view ?: return;if(busy) return
        val zone=if(zoned) FrontierWorkshops.ZONE_OUTPUT else FrontierWorkshops.ZONE_INPUT
        val keys=(if(zoned) v.outputStacks else v.stacks).map { it.key }
        if(keys.isEmpty()) return
        runMutation({
            for(key in keys) a.renderer.mutateStorageStack(v.pos,key,false,transfer=true,notify=false,zone=zone)
            a.renderer.changedFrontierInventory()
        }) { status.setText(R.string.cave_stack_moved) }
    }
    private fun runMutation(action: ()->Unit,after: ()->Unit) {
        val v=view ?: return;val session=generation
        if(busy || !visible) return
        bubbles.cancel();busy=true;refresh()
        a.glView.queueEvent {
            action();a.renderer.syncInventoryStacks()
            val next=a.renderer.workshops.view(v.pos);val inv=a.renderer.inventory.toMap();val stacks=a.renderer.inventoryStacks.snapshot()
            a.runOnUiThread {
                if(session!=generation || !visible) return@runOnUiThread
                busy=false
                if(next==null) a.invManager.closeInventory() else { view=next;inventory=inv;playerStacks=stacks;refresh();after();a.saveWorldAsync() }
            }
        }
    }
    private fun chooseProduction(anchor: View) {
        val v=view ?: return;if(busy) return
        val options=a.renderer.workshops.recipes.withIndex().filter { it.value.machine==v.block }
        val labels=listOf(a.getString(R.string.cave_machine_auto))+options.map { (_,recipe) ->
            fun names(items: Map<Short,Int>)=items.entries.joinToString { (id,n)->a.getString(R.string.cave_storage_row,a.blockName(id),n) }
            val line=a.getString(R.string.cave_machine_recipe,names(recipe.input),names(recipe.output),recipe.seconds)
            val heated=if(recipe.heat==0) line else a.getString(R.string.cave_forge_recipe_heat,line,a.resources.getStringArray(R.array.cave_forge_heat_levels)[recipe.heat])
            if(recipe.forgeTier==0) heated else a.getString(R.string.cave_mineral_recipe_forge,heated,
                com.Atom2Universe.app.games.caves.node.MineralItems.forgeName(a,recipe.forgeTier))
        }
        bubbles.choices(anchor,a.getString(R.string.cave_machine_choose),labels) { index ->
            runMutation({ a.renderer.selectWorkshopRecipe(v.pos,if(index==0) -1 else options[index-1].index) }) { }
        }
    }
}
