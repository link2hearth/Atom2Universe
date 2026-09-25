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
    private lateinit var status: TextView
    private lateinit var production: Button
    private lateinit var matching: Button
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
        view=snapshot;inventory=items;busy=false;query="";barTiles.clear()
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
        header.addView(label().apply { text=a.blockName(snapshot.block);textSize=16f;setTextColor(CaveUiStyle.TEXT);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,dp(44),1f))
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
        bag=side(true);box=side(false)
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
    private fun dropTarget(player: Boolean,target: Long?=null,slot: Int?=null)=View.OnDragListener { targetView,event ->
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
                    runMutation({ moved=a.renderer.mutateStorageStack(v.pos,move.key,move.player,target=target,barSlot=slot,transfer=move.player!=player) }) {
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
        bagTitle.text=a.getString(R.string.cave_storage_bag_title,bagRows.size);boxTitle.text=a.getString(R.string.cave_storage_box_title,boxRows.size)
        val bagScroll=bag.layoutManager?.onSaveInstanceState();val boxScroll=box.layoutManager?.onSaveInstanceState()
        bag.adapter=Items(bagRows,true);box.adapter=Items(boxRows,false)
        bag.layoutManager?.onRestoreInstanceState(bagScroll);box.layoutManager?.onRestoreInstanceState(boxScroll)
        bag.alpha=if(busy) .5f else 1f;box.alpha=bag.alpha;matching.isEnabled=!busy
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
            if(recipes.any { it.machine==v.block && it.power }) {
                add(a.getString(if(v.powered) R.string.cave_machine_powered else R.string.cave_machine_unpowered));add(a.getString(R.string.cave_machine_power))
            }
            recipes.getOrNull(v.active)?.let { add(a.getString(R.string.cave_machine_progress,v.progress,it.seconds)) }
        }.joinToString("\n\n")
    }
    private inner class Items(private val rows: List<CaveStackInventory.Stack>,private val player: Boolean): RecyclerView.Adapter<Items.Holder>() {
        inner class Holder(val tile: CaveItemTile): RecyclerView.ViewHolder(tile)
        override fun getItemCount()=rows.size
        override fun onCreateViewHolder(parent: ViewGroup,viewType: Int)=Holder(CaveItemTile(a).apply {
            layoutParams=RecyclerView.LayoutParams(CaveItemTile.edge(a),CaveItemTile.edge(a)).apply { val gap=dp(2);setMargins(gap,gap,gap,gap) }
        })
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val stack=rows[position]
            holder.tile.bind(a.blockDrawable(stack.id,4f),a.blockName(stack.id),stack.count,favorite=a.invManager.isFavorite(stack.id))
            bindGestures(holder.tile,stack,player);holder.tile.setOnDragListener(dropTarget(player,target=stack.key))
        }
    }
    private fun depositMatching() {
        val v=view ?: return;if(busy) return
        val keys=playerStacks.filter { it.slot<0 && it.id in v.items && !a.invManager.isFavorite(it.id) }.map { it.key }
        runMutation({ for(key in keys) a.renderer.mutateStorageStack(v.pos,key,true,transfer=true,notify=false);a.renderer.changedFrontierInventory() }) { }
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
            a.getString(R.string.cave_machine_recipe,names(recipe.input),names(recipe.output),recipe.seconds)
        }
        bubbles.choices(anchor,a.getString(R.string.cave_machine_choose),labels) { index ->
            runMutation({ a.renderer.selectWorkshopRecipe(v.pos,if(index==0) -1 else options[index-1].index) }) { }
        }
    }
}
