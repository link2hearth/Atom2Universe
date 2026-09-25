package com.Atom2Universe.app.games.caves

import android.content.ClipData
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops

/** Two inventories remain visible throughout a transfer; all mutations stay on the GL thread. */
internal class CaveStorageBrowser(private val a: CaveActivity) {
    private var dialog: AlertDialog?=null
    private var view: FrontierWorkshops.View?=null
    private var inventory: Map<Short,Int> = emptyMap()
    private var busy=false
    private var amount=1
    private var query=""
    private var generation=0
    private lateinit var bag: RecyclerView
    private lateinit var box: RecyclerView
    private lateinit var bagTitle: TextView
    private lateinit var boxTitle: TextView
    private lateinit var status: TextView
    private lateinit var production: Button
    private lateinit var matching: Button
    private lateinit var progress: ProgressBar
    private var compact=false
    private val accents=Regex("\\p{M}+")
    private fun folded(text: String)=java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replace(accents,"")
    private data class TransferDrag(val id: Short,val deposit: Boolean,val generation: Int)
    private fun dp(n: Int)=CaveUiStyle.dp(a,n)
    private fun label()=TextView(a).apply { textSize=12f;setTextColor(CaveUiStyle.MUTED);setPadding(dp(4),dp(4),dp(4),dp(4)) }
    fun show(snapshot: FrontierWorkshops.View,items: Map<Short,Int>) {
        if(a.isFinishing || a.isDestroyed || dialog?.isShowing==true) return
        generation++;val session=generation
        view=snapshot;inventory=items;busy=false;amount=1;query=""
        a.renderer.gamePaused=true
        val root=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(8));background=CaveUiStyle.panel(a,CaveUiStyle.SURFACE)
        }
        compact=a.resources.displayMetrics.widthPixels>a.resources.displayMetrics.heightPixels
        val header=LinearLayout(a).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(View(a).apply { background=a.blockDrawable(snapshot.block,5f) },LinearLayout.LayoutParams(dp(32),dp(32)))
            addView(label().apply { text=a.blockName(snapshot.block);textSize=17f;setTextColor(CaveUiStyle.TEXT) },LinearLayout.LayoutParams(0,-2,1f))
            addView(Button(a).apply { CaveUiStyle.icon(this,"info",a.getString(R.string.cave_ui_details));setOnClickListener {
                AlertDialog.Builder(a).setTitle(R.string.cave_ui_details).setMessage(machineDetails()).setPositiveButton(android.R.string.ok,null).show()
            } },LinearLayout.LayoutParams(dp(44),dp(44)))
        }
        root.addView(header)
        val search=EditText(a).apply {
            setSingleLine();setHint(R.string.cave_ui_search);textSize=14f;setTextColor(CaveUiStyle.TEXT);setHintTextColor(CaveUiStyle.MUTED)
            addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int)=Unit
                override fun afterTextChanged(s: Editable?)=Unit
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { query=s?.toString().orEmpty().trim();if(::bag.isInitialized) refresh() }
            })
        }
        if(compact) header.addView(search,2,LinearLayout.LayoutParams(0,dp(44),1.3f))
        else root.addView(search,LinearLayout.LayoutParams(-1,dp(44)))
        val quantity=LinearLayout(a)
        val buttons=mutableListOf<Pair<Button,Int>>()
        for(n in listOf(1,16,64,Int.MAX_VALUE)) {
            val button=Button(a).apply {
                text=if(n==Int.MAX_VALUE) a.getString(R.string.cave_storage_all) else a.getString(R.string.cave_storage_quantity,n)
                CaveUiStyle.button(this,n==amount)
                setOnClickListener { amount=n;buttons.forEach { (b,value)->CaveUiStyle.button(b,value==amount) } }
            }
            buttons+=button to n;quantity.addView(button,LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(2),0,dp(2),0) })
        }
        root.addView(quantity)
        val work=LinearLayout(a).apply { gravity=Gravity.CENTER_VERTICAL }
        production=Button(a).apply { CaveUiStyle.button(this);setOnClickListener { chooseProduction() } }
        matching=Button(a).apply { CaveUiStyle.icon(this,"pin",a.getString(R.string.cave_storage_matching));setOnClickListener { depositMatching() } }
        if(compact) {
            quantity.addView(production,LinearLayout.LayoutParams(dp(44),dp(44)))
            quantity.addView(matching,LinearLayout.LayoutParams(dp(44),dp(44)))
        } else {
            work.addView(production,LinearLayout.LayoutParams(0,dp(42),1f));work.addView(matching,LinearLayout.LayoutParams(dp(44),dp(44)))
            root.addView(work)
        }
        progress=ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=100;progressTintList=android.content.res.ColorStateList.valueOf(CaveUiStyle.ACCENT)
        }
        root.addView(progress,LinearLayout.LayoutParams(-1,dp(4)))
        val columns=LinearLayout(a)
        val span=((a.resources.displayMetrics.widthPixels*.43f)/dp(76)).toInt().coerceIn(2,7)
        fun side(deposit: Boolean): RecyclerView {
            val column=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL;background=CaveUiStyle.panel(a,0xFF20372F.toInt()) }
            val title=label();if(deposit) bagTitle=title else boxTitle=title;column.addView(title)
            val grid=RecyclerView(a).apply {
                layoutManager=GridLayoutManager(a,span);clipToPadding=false;setPadding(dp(3),dp(3),dp(3),dp(3))
                setOnDragListener { v,event ->
                    val token=event.localState as? TransferDrag
                    val accepts=token!=null && token.generation==generation && token.deposit!=deposit && !busy
                    when(event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> accepts
                        DragEvent.ACTION_DRAG_ENTERED -> { if(accepts) v.alpha=.65f;accepts }
                        DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED -> { v.alpha=1f;true }
                        DragEvent.ACTION_DROP -> { v.alpha=1f;if(accepts) transfer(token!!.id,token.deposit);accepts }
                        DragEvent.ACTION_DRAG_LOCATION -> { if(accepts) { if(event.y<dp(40)) scrollBy(0,-dp(10));if(event.y>height-dp(40)) scrollBy(0,dp(10)) };accepts }
                        else -> accepts
                    }
                }
            }
            column.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
            columns.addView(column,LinearLayout.LayoutParams(0,-1,1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            return grid
        }
        bag=side(true);box=side(false)
        root.addView(columns,LinearLayout.LayoutParams(-1,0,1f))
        status=label().apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setText(R.string.cave_storage_drag_hint);accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        root.addView(status)
        dialog=AlertDialog.Builder(a).setView(root).setPositiveButton(R.string.cave_storage_close,null).create().also { d ->
            d.setOnDismissListener { if(session==generation) { dialog=null;a.renderer.gamePaused=false;a.saveWorldAsync() } }
            d.show()
            d.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val metrics=a.resources.displayMetrics
            d.window?.setLayout((metrics.widthPixels*.94f).toInt(),(metrics.heightPixels*.93f).toInt())
            root.layoutParams?.let { params -> params.height=(metrics.heightPixels*.93f-dp(60)).toInt().coerceAtLeast(dp(220));root.layoutParams=params }
        }
        refresh()
    }
    private fun refresh() {
        val v=view ?: return
        val needle=folded(query)
        fun filtered(items: Map<Short,Int>)=items.keys.filter { folded(a.blockName(it)).contains(needle) }.sortedBy { folded(a.blockName(it)) }
        val bagIds=filtered(inventory);val boxIds=filtered(v.items)
        bagTitle.text=a.getString(R.string.cave_storage_bag_title,bagIds.size)
        boxTitle.text=a.getString(R.string.cave_storage_box_title,boxIds.size)
        val bagScroll=bag.layoutManager?.onSaveInstanceState();val boxScroll=box.layoutManager?.onSaveInstanceState()
        bag.adapter=Items(bagIds,true);box.adapter=Items(boxIds,false)
        bag.layoutManager?.onRestoreInstanceState(bagScroll);box.layoutManager?.onRestoreInstanceState(boxScroll)
        bag.alpha=if(busy) .5f else 1f;box.alpha=bag.alpha;matching.isEnabled=!busy
        val recipes=a.renderer.workshops.recipes
        production.visibility=if(recipes.none { it.machine==v.block }) View.GONE else View.VISIBLE
        production.isEnabled=!busy
        val recipe=recipes.getOrNull(v.selection)
        production.text=recipe?.let { a.getString(R.string.cave_machine_selected,it.output.keys.joinToString { id -> a.blockName(id) }) } ?: a.getString(R.string.cave_machine_auto)
        val selected=production.text.toString()
        if(compact) CaveUiStyle.icon(production,"craft",selected)
        production.contentDescription=machineDetails();production.tooltipText=selected
        production.maxLines=1;production.ellipsize=android.text.TextUtils.TruncateAt.END
        val active=recipes.getOrNull(v.active)
        progress.visibility=if(active!=null) View.VISIBLE else View.GONE
        progress.progress=if(active!=null) (v.progress*100/active.seconds.coerceAtLeast(1)).coerceIn(0,100) else 0
        progress.contentDescription=active?.let { a.getString(R.string.cave_machine_progress,v.progress,it.seconds) }
    }
    private fun machineDetails(): String {
        val v=view ?: return a.getString(R.string.cave_storage_drag_hint)
        val recipes=a.renderer.workshops.recipes
        return buildList {
            add(a.getString(R.string.cave_storage_drag_hint))
            if(recipes.any { it.machine==v.block && it.power }) {
                add(a.getString(if(v.powered) R.string.cave_machine_powered else R.string.cave_machine_unpowered))
                add(a.getString(R.string.cave_machine_power))
            }
            recipes.getOrNull(v.active)?.let { add(a.getString(R.string.cave_machine_progress,v.progress,it.seconds)) }
        }.joinToString("\n\n")
    }
    private inner class Items(private val ids: List<Short>,private val deposit: Boolean): RecyclerView.Adapter<Items.Holder>() {
        inner class Holder(val tile: CaveItemTile): RecyclerView.ViewHolder(tile)
        override fun getItemCount()=ids.size
        override fun onCreateViewHolder(parent: ViewGroup,viewType: Int)=Holder(CaveItemTile(a).apply {
            layoutParams=RecyclerView.LayoutParams(-1,dp(78)).apply { setMargins(3,3,3,3) }
        })
        override fun onBindViewHolder(holder: Holder,position: Int) {
            val id=ids[position];val count=(if(deposit) inventory else view?.items.orEmpty())[id] ?: 0
            holder.tile.bind(a.blockDrawable(id,4f),a.blockName(id),count,favorite=a.invManager.isFavorite(id))
            holder.tile.setOnClickListener { if(!busy) transfer(id,deposit) }
            holder.tile.setOnLongClickListener { v ->
                if(busy) false else v.startDragAndDrop(ClipData.newPlainText("cave-transfer",id.toString()),View.DragShadowBuilder(v),TransferDrag(id,deposit,generation),0)
            }
        }
    }
    private fun transfer(id: Short,deposit: Boolean) {
        val v=view ?: return;if(busy) return
        val n=amount;val before=inventory[id] ?: 0
        runMutation({ a.renderer.transferStorage(v.pos,id,n,deposit) }) { next ->
            val transferred=kotlin.math.abs((next[id] ?: 0)-before)
            status.text=a.getString(R.string.cave_storage_done,a.blockName(id),transferred)
        }
    }
    private fun depositMatching() {
        val v=view ?: return;if(busy) return
        val bars=a.renderer.hotbar.toSet()
        val ids=inventory.keys.filter { it in v.items && it !in bars && !a.invManager.isFavorite(it) }
        runMutation({ a.renderer.transferStorageBatch(v.pos,ids) }) { }
    }
    private fun runMutation(action: ()->Unit, after: (Map<Short,Int>)->Unit) {
        val v=view ?: return;val session=generation
        busy=true;refresh()
        a.glView.queueEvent {
            action()
            val next=a.renderer.workshops.view(v.pos);val inv=a.renderer.inventory.toMap()
            a.runOnUiThread {
                if(session!=generation || dialog==null) return@runOnUiThread
                busy=false
                if(next==null) dialog?.dismiss() else { view=next;inventory=inv;refresh();after(inv) }
            }
        }
    }
    private fun chooseProduction() {
        val v=view ?: return;if(busy) return
        val options=a.renderer.workshops.recipes.withIndex().filter { it.value.machine==v.block }
        val labels=listOf(a.getString(R.string.cave_machine_auto))+options.map { (_,recipe) ->
            fun names(items: Map<Short,Int>)=items.entries.joinToString { (id,n)->a.getString(R.string.cave_storage_row,a.blockName(id),n) }
            a.getString(R.string.cave_machine_recipe,names(recipe.input),names(recipe.output),recipe.seconds)
        }
        AlertDialog.Builder(a).setTitle(R.string.cave_machine_choose).setItems(labels.toTypedArray()) { _,index ->
            runMutation({ a.renderer.selectWorkshopRecipe(v.pos,if(index==0) -1 else options[index-1].index) }) { }
        }.show()
    }
}
