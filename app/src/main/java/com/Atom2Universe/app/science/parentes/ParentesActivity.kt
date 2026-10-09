package com.Atom2Universe.app.science.parentes

import android.graphics.Typeface
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Job
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.timeline.CosmicTimelineActivity
import com.Atom2Universe.app.science.timeline.LifeDate
import com.Atom2Universe.app.science.timeline.LifeTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentesActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private var repository: ParentesRepository? = null
    private val repo get() = requireNotNull(repository)
    private var focus = ""
    private var selected = ""
    private var first = ""
    private var second = ""
    private var comparing = false
    private var pendingNode: String? = null
    private var pendingFirst: String? = null
    private var pendingSecond: String? = null
    private var pendingDate: String? = null
    private var scene: TreeScene? = null
    private lateinit var root: LinearLayout
    private lateinit var back: ImageButton
    private lateinit var controls: LinearLayout
    private lateinit var breadcrumb: LinearLayout
    private lateinit var selectors: LinearLayout
    private lateinit var chart: ParentesTreeView
    private lateinit var card: LinearLayout
    private lateinit var cardScroll: ScrollView
    private lateinit var status: TextView
    private lateinit var loading: LinearLayout
    private val storyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val a = result.data?.getStringExtra("first")
            val b = result.data?.getStringExtra("second")
            if (a != null && b != null && (repository == null ||
                (repo.tree.nodes[a]?.species == true && repo.tree.nodes[b]?.species == true))) {
                first = a; second = b; comparing = true
                if (repository != null) render()
            }
        }
    }
    private var activeDialog: AlertDialog? = null
    private val wide get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focus = savedInstanceState?.getString("focus").orEmpty()
        selected = savedInstanceState?.getString("selected").orEmpty()
        first = savedInstanceState?.getString("first").orEmpty()
        second = savedInstanceState?.getString("second").orEmpty()
        comparing = savedInstanceState?.getBoolean("comparing") ?: false
        if (savedInstanceState != null) {
            pendingNode = savedInstanceState.getString("pending_node")
            pendingFirst = savedInstanceState.getString("pending_first")
            pendingSecond = savedInstanceState.getString("pending_second")
            pendingDate = savedInstanceState.getString("pending_date")
        } else receiveLink(intent)
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        loadAtlas()
    }

    private fun loadAtlas() {
        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.Default) { runCatching { ParentesRepository.load(applicationContext) } }
            loaded.onSuccess { result ->
                loading.visibility = View.GONE
                repository = result
                focus = focus.takeIf { it in repo.tree.nodes } ?: repo.tree.root
                selected = selected.takeIf { it in repo.tree.nodes } ?: focus
                fun defaultSpecies(name: String) = repo.tree.species.first { it.scientific == name }.id
                first = first.takeIf { repo.tree.nodes[it]?.species == true } ?: defaultSpecies("Apis mellifera")
                second = second.takeIf { repo.tree.nodes[it]?.species == true } ?: defaultSpecies("Bombus terrestris")
                val restoredSelection = selected
                render()
                if (restoredSelection in requireNotNull(scene).parents) select(restoredSelection)
                applyPendingLink()
            }.onFailure { error ->
                android.util.Log.e("ParentesLoad", "Atlas load failed", error)
                status.text = getString(R.string.pt_error)
                loading.removeAllViews()
                loading.addView(button(getString(R.string.pt_story_retry)) {
                    loading.removeAllViews(); loading.addView(ProgressBar(this@ParentesActivity)); loadAtlas()
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        updateBackDescription()
        receiveLink(intent)
        if (repository != null) applyPendingLink()
    }

    private fun receiveLink(intent: Intent) {
        pendingNode = intent.getStringExtra(EXTRA_NODE_ID)
        pendingFirst = intent.getStringExtra(EXTRA_FIRST_ID)
        pendingSecond = intent.getStringExtra(EXTRA_SECOND_ID)
        pendingDate = intent.getStringExtra(CosmicTimelineActivity.EXTRA_LIFE_ID)
    }

    private fun applyPendingLink() {
        val id = pendingNode ?: return
        val dating = LifeTimeline.get(pendingDate)
        val a = pendingFirst ?: dating?.comparison?.first
        val b = pendingSecond ?: dating?.comparison?.second
        pendingNode = null; pendingFirst = null; pendingSecond = null; pendingDate = null
        if (id !in repo.tree.nodes || (dating != null && (dating.nodeId != id || !LifeTimeline.matches(repo.tree, dating)))) {
            Toast.makeText(this, R.string.ct_life_link_unavailable, Toast.LENGTH_LONG).show(); return
        }
        activeDialog?.dismiss()
        if (a != null && b != null && a != b && repo.tree.nodes[a]?.species == true &&
            repo.tree.nodes[b]?.species == true && repo.tree.lca(a, b) == id) {
            first = a; second = b; comparing = true; render()
        } else reveal(id)
    }

    private fun buildUi() {
        root = column().apply { setBackgroundColor(palette.background) }
        val heading = row()
        back = icon(R.drawable.ic_arrow_back_24, R.string.pt_back) { navigateBack() }
        ScienceNavigation.bindHomeAction(back, ::returnToStart)
        updateBackDescription()
        heading.addView(back)
        val titles = column().apply { setPadding(dp(4),0,0,0) }
        titles.addView(label(getString(R.string.pt_short_title),24f,true))
        titles.addView(label(getString(R.string.pt_tree_subtitle),12f).apply { setTextColor(palette.secondary) })
        heading.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(icon(R.drawable.ic_pt_trail, R.string.pt_read_title) { showReading() })
        heading.addView(icon(R.drawable.ic_view_list,R.string.pt_list) { if(repository!=null) accessibleList() })
        root.addView(heading)
        val mapColumn = if(wide) column().apply { setPadding(0,0,dp(8),0) } else root
        val toolsColumn = if(wide) column().apply { setPadding(dp(8),0,0,0) } else root
        if(wide) {
            root.addView(row().apply {
                gravity=Gravity.TOP
                addView(mapColumn,LinearLayout.LayoutParams(0,-1,1f))
                addView(toolsColumn,LinearLayout.LayoutParams(dp(260),-1))
            },LinearLayout.LayoutParams(-1,0,1f))
        }
        toolsColumn.addView(button(getString(R.string.pt_search_tree)) {
            if(repository!=null) picker(false) { reveal(it) }
        }.apply {
            gravity=Gravity.START or Gravity.CENTER_VERTICAL
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search,0,0,0)
            compoundDrawableTintList=android.content.res.ColorStateList.valueOf(palette.secondary)
            compoundDrawablePadding=dp(12)
            setTextColor(palette.secondary)
        },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(4) })
        breadcrumb = row()
        mapColumn.addView(breadcrumb)
        selectors = column()
        toolsColumn.addView(selectors)
        val mapArea = FrameLayout(this)
        chart = ParentesTreeView(this).apply { onSelect = { id ->
            if (!comparing && repo.tree.isBrowsable(id) && id!=focus) explore(id)
            else select(id)
        } }
        mapArea.addView(chart,FrameLayout.LayoutParams(-1,-1))
        loading = column().apply {
            gravity = Gravity.CENTER
            addView(ProgressBar(this@ParentesActivity))
            addView(label(getString(R.string.pt_story_loading_map), 15f))
        }
        mapArea.addView(loading,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
        val camera=column().apply { setPadding(0,0,0,0); background=palette.shape(palette.surface,20f) }
        camera.addView(icon(R.drawable.ic_ae_zoom_in,R.string.pt_zoom_in) { chart.zoomBy(1.6f) })
        camera.addView(icon(R.drawable.ic_ae_zoom_out,R.string.pt_zoom_out) { chart.zoomBy(1f/1.6f) })
        camera.addView(icon(R.drawable.ic_ae_zoom_fit,R.string.pt_fit) { chart.fit() })
        mapArea.addView(camera,FrameLayout.LayoutParams(dp(48),-2,Gravity.BOTTOM or Gravity.END).apply { bottomMargin=dp(8) })
        mapColumn.addView(mapArea,LinearLayout.LayoutParams(-1,0,1f))
        card=column().apply { background=palette.shape(palette.surface) }
        cardScroll=ScrollView(this).apply { addView(card); visibility=View.GONE }
        toolsColumn.addView(cardScroll,if(wide) LinearLayout.LayoutParams(-1,0,1f) else LinearLayout.LayoutParams(-1,-2))
        if(wide) toolsColumn.addView(View(this),LinearLayout.LayoutParams(-1,0,0.001f))
        status=label(getString(R.string.pt_loading),12f).apply { gravity=Gravity.CENTER; setTextColor(palette.secondary) }
        toolsColumn.addView(status)
        controls=row().apply { background=palette.shape(palette.surface,20f) }
        toolsColumn.addView(controls,LinearLayout.LayoutParams(-1,-2))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left+dp(12),bars.top+dp(4),bars.right+dp(12),bars.bottom+dp(6))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun buildControls() {
        controls.removeAllViews()
        fun tab(title:Int,drawable:Int,active:Boolean,action:()->Unit) {
            controls.addView(button(getString(title),action).apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0,drawable,0,0)
                compoundDrawableTintList=android.content.res.ColorStateList.valueOf(if(active) palette.ink(palette.accent) else palette.secondary)
                compoundDrawablePadding=dp(3)
                setTextColor(if(active) palette.ink(palette.accent) else palette.secondary)
                background=if(active) palette.shape(palette.raised,14f) else null
                isSelected=active
                ViewCompat.setStateDescription(this,if(active) getString(R.string.pt_active_mode) else null)
            },LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) })
        }
        tab(R.string.pt_explore_tab,R.drawable.ic_pt_tree,!comparing) { explore(repo.tree.root) }
        tab(R.string.pt_compare,R.drawable.ic_pt_compare,comparing) { comparing=true; render() }
        tab(R.string.pt_trails_tab,R.drawable.ic_pt_trail,false) {
            storyLauncher.launch(Intent(this, ParentesStoryActivity::class.java))
        }
    }

    private fun render() {
        scene=if(comparing) repo.tree.compare(first,second) else if(focus==repo.tree.root) repo.overview else repo.tree.atlas(focus)
        val current=requireNotNull(scene)
        selected=if(comparing) current.common!! else focus
        chart.selected=selected
        chart.show(current,repo.tree,repo.groups.keys,if(current===repo.overview) repo.overviewPositions else null) { id ->
            when {
                comparing && id==current.common && repo.tree.nodes.getValue(id).scientific.isEmpty() -> getString(R.string.pt_common_node)
                repo.tree.nodes.getValue(id).species || repo.tree.isBrowsable(id) || comparing -> repo.name(this,id)
                else -> ""
            }
        }
        refreshNavigation()
        buildControls()
        showSummary()
    }

    private fun refreshNavigation() {
        breadcrumb.removeAllViews()
        selectors.removeAllViews()
        if(comparing) {
            for((id,isFirst) in listOf(first to true,second to false)) {
                selectors.addView(button(getString(if(isFirst) R.string.pt_a else R.string.pt_b,repo.name(this,id))) {
                    picker(true) { if(isFirst) first=it else second=it; render() }
                }.apply { gravity=Gravity.START or Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(-1,-2))
            }
        } else {
            if(focus!=repo.tree.root) breadcrumb.addView(icon(R.drawable.ic_chevron_left,R.string.pt_parent) {
                explore(repo.tree.displayParent(focus) ?: repo.tree.root)
            })
            breadcrumb.addView(label(repo.name(this,focus),15f,true).apply {
                maxLines=2; setOnClickListener { detailsDialog(focus) }
                contentDescription=getString(R.string.pt_node_action,repo.name(this@ParentesActivity,focus))
                isFocusable=true
            },LinearLayout.LayoutParams(0,dp(48),1f))
            breadcrumb.addView(label(getString(R.string.pt_tip_count,repo.tree.descendants(focus).size),12f).apply { setTextColor(palette.secondary) })
            LifeTimeline.forNode(repo.tree, focus)?.let { date ->
                breadcrumb.addView(button(getString(R.string.ct_view_timeline_short)) { openTimeline(date) })
            }
        }
        status.text=getString(if(comparing) R.string.pt_comparison_hint else R.string.pt_map_hint)
    }

    private fun explore(id:String) {
        comparing=false; focus=id; selected=id
        render()
    }
    private fun reveal(id:String) {
        if(repo.tree.nodes.getValue(id).species) {
            explore(repo.tree.displayParent(id) ?: repo.tree.root)
            select(id)
            chart.post { chart.reveal(id) }
        } else explore(id)
    }
    private fun select(id:String) { selected=id; chart.selected=id; showSummary() }
    private fun navigateBack() {
        when {
            ScienceNavigation.isModuleLink(intent) -> finish()
            repository==null -> finish()
            comparing -> explore(repo.tree.root)
            selected!=focus -> select(focus)
            focus!=repo.tree.root -> explore(repo.tree.displayParent(focus) ?: repo.tree.root)
            else -> finish()
        }
    }

    private fun updateBackDescription() {
        back.contentDescription = getString(if (ScienceNavigation.isModuleLink(intent))
            R.string.science_back_to_previous_module else R.string.pt_back)
    }

    private fun returnToStart() {
        activeDialog?.dismiss()
        pendingNode = null; pendingFirst = null; pendingSecond = null; pendingDate = null
        comparing = false
        // Also cancel a pending deep link if the atlas is still loading.
        if (repository == null) {
            focus = ""; selected = ""
        } else {
            explore(repo.tree.root)
            back.announceForAccessibility(getString(R.string.science_return_to_module_start))
        }
    }

    private fun showSummary() {
        card.removeAllViews()
        val show=comparing || selected!=focus
        cardScroll.visibility=if(show) View.VISIBLE else View.GONE
        if(!show) return
        // A bounded, scrollable preview leaves the drawing in charge, including in landscape.
        if(!wide) cardScroll.layoutParams.height=dp(132)
        if(comparing && selected==repo.tree.lca(first,second)) {
            card.addView(label(getString(when {
                first==second -> R.string.pt_same
                else -> R.string.pt_common_summary
            }),14f,true))
            card.addView(button(getString(R.string.pt_details)) { comparisonDetails() })
            if (first != second) LifeTimeline.forNode(repo.tree, selected)?.let { date ->
                card.addView(button(getString(R.string.ct_view_timeline)) { openTimeline(date) })
            }
        } else {
            val heading=row()
            heading.addView(label(repo.name(this,selected),18f,true),LinearLayout.LayoutParams(0,-2,1f))
            heading.addView(button(getString(R.string.pt_details)) { detailsDialog(selected) })
            card.addView(heading)
            card.addView(label(if(repo.tree.nodes.getValue(selected).species) repo.tree.nodes.getValue(selected).scientific else article(selected),14f))
        }
        cardScroll.post { cardScroll.scrollTo(0,0) }
    }

    private fun showReading() {
        val body = column().apply { setPadding(dp(16), dp(8), dp(16), dp(16)) }
        body.addView(label(getString(R.string.science_navigation_help), 15f))
        ParentesReading.sections.forEach { section ->
            body.addView(label(getString(section.title), 18f, true))
            body.addView(label(getString(section.body), 15f).apply { setTextIsSelectable(true) })
        }
        activeDialog?.dismiss()
        activeDialog = AlertDialog.Builder(this).setTitle(R.string.pt_read_title)
            .setView(ScrollView(this).apply { addView(body) }).setPositiveButton(R.string.pt_close, null).show()
    }

    private fun accessibleList() {
        val body=column()
        body.addView(button(getString(R.string.pt_catalogue,repo.tree.species.size)) { picker(true) { reveal(it) } })
        body.addView(label(getString(R.string.pt_list_intro)))
        val entries=scene?.parents.orEmpty().keys.filter {
            repo.tree.nodes.getValue(it).species || repo.tree.isBrowsable(it) || comparing
        }
        val list=ListView(this).apply {
            adapter=object:ArrayAdapter<String>(this@ParentesActivity,android.R.layout.simple_list_item_1,entries) {
                override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                    val id=getItem(position)!!
                    val ancestor=repo.tree.displayParent(id)
                    return (convertView as? TextView ?: label("",16f)).apply {
                        text=if(ancestor==null) repo.name(this@ParentesActivity,id) else getString(R.string.pt_branch_format,repo.name(this@ParentesActivity,ancestor),repo.name(this@ParentesActivity,id))
                        minHeight=dp(56); gravity=Gravity.CENTER_VERTICAL
                    }
                }
            }
            setOnItemClickListener { _,_,position,_ -> activeDialog?.dismiss(); select(entries[position]); detailsDialog(entries[position]) }
        }
        body.addView(list,LinearLayout.LayoutParams(-1,dp(minOf(360,resources.configuration.screenHeightDp/2))))
        activeDialog?.dismiss()
        activeDialog=AlertDialog.Builder(this).setTitle(R.string.pt_list).setView(body).setPositiveButton(R.string.pt_close,null).show()
    }

    private fun article(id: String): String {
        repo.groups[id]?.let { return repo.text(this,it.key+"_body") }
        repo.notes[id]?.let { return repo.text(this,it.key) }
        val n = repo.tree.nodes.getValue(id)
        if (!n.species) return getString(R.string.pt_node_body)
        val group = repo.tree.ancestors(id).firstOrNull { it in repo.groups } ?: repo.tree.root
        return getString(R.string.pt_species_body,repo.name(this,group))
    }

    private fun detailsDialog(id: String) {
        val body = column()
        val node = repo.tree.nodes.getValue(id)
        if (node.scientific.isNotEmpty()) body.addView(label(node.scientific,16f,true))
        body.addView(label(article(id)))
        val lineage = repo.tree.ancestors(id).drop(1).filter { it in repo.groups }.reversed()
        body.addView(label(getString(R.string.pt_position,lineage.joinToString(getString(R.string.pt_separator)) { repo.name(this,it) })))
        repo.tree.displayParent(id)?.let { parent ->
            body.addView(button(getString(R.string.pt_parent)) { activeDialog?.dismiss(); explore(parent) })
        }
        if (!node.species) {
            body.addView(button(getString(R.string.pt_open)) { activeDialog?.dismiss(); explore(id) })
            val members = repo.tree.descendants(id)
            body.addView(label(getString(R.string.pt_count,members.size)))
            body.addView(button(getString(R.string.pt_members)) {
                picker(true,members.map { it.id }.toSet()) { detailsDialog(it) }
            })
        } else {
            val actions = row()
            actions.addView(button(getString(R.string.pt_choose_a)) { activeDialog?.dismiss(); first=id; comparing=true; render() })
            actions.addView(button(getString(R.string.pt_choose_b)) { activeDialog?.dismiss(); second=id; comparing=true; render() })
            body.addView(horizontal(actions))
            val group = repo.tree.ancestors(id).firstOrNull { it in repo.groups }
            group?.let { body.addView(label(repo.text(this,repo.groups.getValue(it).key+"_body"))) }
        }
        body.addView(label(getString(if (node.support.any { '@' in it }) R.string.pt_support_study else R.string.pt_support_taxonomy)))
        if (node.conflicts.isNotEmpty()) body.addView(label(getString(R.string.pt_conflicts)))
        if (repo.tree.children[id].orEmpty().size > 2) body.addView(label(getString(R.string.pt_polytomy)))
        addDatingLink(body, id)
        dialog(repo.name(this,id),body)
    }

    private fun comparisonDetails() {
        val common = repo.tree.lca(first,second)
        val body = column()
        body.addView(label(getString(R.string.pt_comparison_limits)))
        body.addView(button(repo.name(this,common)) { detailsDialog(common) })
        if (first != second) addDatingLink(body, common)
        for ((id,title) in listOf(first to R.string.pt_path_a,second to R.string.pt_path_b)) {
            body.addView(button(getString(title)) {
                val path = column()
                repo.tree.pathTo(id,common).forEach { n ->
                    path.addView(button(getString(R.string.pt_path_item,repo.name(this,n),n)) { detailsDialog(n) })
                }
                dialog(getString(title),path)
            })
        }
        dialog(getString(R.string.pt_compare),body)
    }

    private fun addDatingLink(body: LinearLayout, id: String) {
        val exact = LifeTimeline.forNode(repo.tree, id)
        if (exact != null) {
            body.addView(label(exact.dateLabel(this), 14f, true))
            body.addView(button(getString(R.string.ct_view_timeline)) { openTimeline(exact) })
        } else {
            LifeTimeline.nearestAncestor(repo.tree, id)?.let { ancestor ->
                body.addView(label(getString(R.string.ct_life_ancestor_context, getString(ancestor.title)), 13f))
                body.addView(button(getString(R.string.ct_life_open_ancestor, getString(ancestor.title))) { openTimeline(ancestor) })
            }
        }
    }

    private fun openTimeline(date: LifeDate) {
        val target = Intent(this, CosmicTimelineActivity::class.java)
            .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
            .putExtra(CosmicTimelineActivity.EXTRA_LIFE_ID, date.id)
        if (comparing && first != second && repo.tree.lca(first, second) == date.nodeId) {
            target.putExtra(EXTRA_FIRST_ID, first).putExtra(EXTRA_SECOND_ID, second)
        }
        startActivity(target)
    }

    private fun picker(speciesOnly: Boolean, within: Set<String>? = null, chosen: (String) -> Unit) {
        val body = column()
        val search = EditText(this).apply {
            hint=getString(R.string.pt_search_hint); setTextColor(palette.text); setHintTextColor(palette.secondary)
            isSingleLine=true; minHeight=dp(48)
            inputType=android.text.InputType.TYPE_CLASS_TEXT
        }
        body.addView(search)
        val empty=label(getString(R.string.pt_none))
        body.addView(empty)
        val list=ListView(this)
        body.addView(list,LinearLayout.LayoutParams(-1,dp(minOf(320,resources.configuration.screenHeightDp/2))))
        var results=emptyList<LifeNode>()
        var searchJob: Job? = null
        fun update(query:String) {
            searchJob?.cancel()
            list.isEnabled = false
            empty.text = getString(R.string.pt_story_loading_search)
            empty.visibility = View.VISIBLE
            searchJob = lifecycleScope.launch {
                results = withContext(Dispatchers.Default) {
                    val collator = java.text.Collator.getInstance(resources.configuration.locales[0])
                    repo.search(query,speciesOnly).filter { within==null || it.id in within }
                        .map { it to repo.name(this@ParentesActivity,it.id) }
                        .sortedWith { a,b -> collator.compare(a.second,b.second) }.map { it.first }
                }
                list.isEnabled = true
                empty.text = getString(R.string.pt_none)
                list.adapter=object:ArrayAdapter<LifeNode>(this@ParentesActivity,android.R.layout.simple_list_item_1,results) {
                    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                        val n=getItem(position)!!
                        return (convertView as? TextView ?: label("",16f)).apply {
                            text=getString(R.string.pt_path_item,repo.name(this@ParentesActivity,n.id),n.scientific)
                            minHeight=dp(64); gravity=Gravity.CENTER_VERTICAL
                        }
                    }
                }
                empty.visibility=if(results.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { update(s.toString()) }
            override fun afterTextChanged(s:Editable?) {}
        })
        list.setOnItemClickListener { _,_,position,_ -> activeDialog?.dismiss(); chosen(results[position].id) }
        update("")
        activeDialog?.dismiss()
        activeDialog=AlertDialog.Builder(this).setTitle(if(speciesOnly) R.string.pt_choose else R.string.pt_search)
            .setView(body).setNegativeButton(R.string.pt_close,null).create().also {
                it.setOnDismissListener { searchJob?.cancel() }; it.show()
            }
    }

    private fun dialog(title:String,body:LinearLayout) {
        activeDialog?.dismiss()
        val scroll=ScrollView(this).apply { addView(body) }
        activeDialog=AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton(R.string.pt_close,null).create().also { it.show() }
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(6),dp(8),dp(6)) }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun horizontal(row:LinearLayout)=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(row) }
    private fun label(value:String,size:Float=15f,bold:Boolean=false)=TextView(this).apply {
        text=value; textSize=size; setTextColor(palette.text); setPadding(dp(4),dp(3),dp(4),dp(3))
        if(bold) setTypeface(typeface,Typeface.BOLD)
    }
    private fun button(value:String,action:()->Unit)=androidx.appcompat.widget.AppCompatButton(this).apply {
        text=value; textSize=14f; isAllCaps=false; minHeight=dp(48); minimumHeight=dp(48)
        setPadding(dp(12),dp(8),dp(12),dp(8)); setTextColor(palette.text)
        background=palette.shape(palette.raised,12f); setOnClickListener { action() }
        layoutParams=LinearLayout.LayoutParams(-2,-2).apply { setMargins(dp(2),dp(3),dp(2),dp(3)) }
    }
    private fun icon(drawable:Int,description:Int,action:()->Unit)=androidx.appcompat.widget.AppCompatImageButton(this).apply {
        setImageResource(drawable); imageTintList=android.content.res.ColorStateList.valueOf(palette.text)
        contentDescription=getString(description); setPadding(dp(12),dp(12),dp(12),dp(12))
        val selectable=android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,selectable,true)
        setBackgroundResource(selectable.resourceId)
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48))
        setOnClickListener { action() }
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    override fun onSaveInstanceState(outState:Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("focus",focus); outState.putString("selected",selected)
        outState.putString("first",first); outState.putString("second",second)
        outState.putBoolean("comparing",comparing)
        outState.putString("pending_node",pendingNode); outState.putString("pending_first",pendingFirst)
        outState.putString("pending_second",pendingSecond); outState.putString("pending_date",pendingDate)
    }
    override fun onDestroy() { activeDialog?.dismiss(); super.onDestroy() }

    companion object {
        const val EXTRA_NODE_ID = "life_node_id"
        const val EXTRA_FIRST_ID = "life_comparison_first"
        const val EXTRA_SECOND_ID = "life_comparison_second"
    }
}
