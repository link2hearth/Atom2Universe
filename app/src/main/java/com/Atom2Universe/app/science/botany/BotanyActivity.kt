package com.Atom2Universe.app.science.botany

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.science.parentes.ParentesActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.FitFrame
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.NumberFormat
import java.util.Locale

/** Catalogue pages share one full-screen fiche, including its navigation history. */
class BotanyActivity : ThemedActivity() {
    private enum class Tab(val title:Int) {
        EXPLORE(R.string.bot_explore), COMPARE(R.string.bot_compare), FAMILIES(R.string.bot_families), LEARN(R.string.bot_learn), LIFE(R.string.bot_life_catalogue)
    }
    private data class Facet(val key:String,val title:Int,val options:List<Pair<String,Int>>,val present:(PlantSpecies)->Set<String>)
    private val palette by lazy { SciencePalette(this) }
    private val prefs by lazy {getSharedPreferences("botany",MODE_PRIVATE)}
    private val selected=linkedMapOf<String,MutableSet<String>>()
    private val expanded=mutableSetOf<String>()
    private var tab=Tab.EXPLORE
    private var search=""
    private var sort=PlantSort.NAME
    private var mode=PlantView.HABIT
    private var marks=true
    private var filtersOpen=false
    private lateinit var content:LinearLayout
    private lateinit var scroll:ScrollView
    private lateinit var tabs:LinearLayout
    private var current:String?=null
    private val history=mutableListOf<String>()
    private val fiche by lazy { ScienceFiche(this,palette,R.string.bot_back,R.string.bot_close){current=null;history.clear()} }
    private var life:BotanyLifeCatalogue?=null
    private var lifeLoading=false
    private var lifeFailed=false
    private var lifeSearch=""
    private var lifeFamily=""
    private var lifeBranch=""
    private var lifeLatinSort=false
    private var lifeLimit=40
    private val catalog=BotanyCatalog
    private val monthIds=listOf(R.string.bot_month_1,R.string.bot_month_2,R.string.bot_month_3,R.string.bot_month_4,
        R.string.bot_month_5,R.string.bot_month_6,R.string.bot_month_7,R.string.bot_month_8,R.string.bot_month_9,R.string.bot_month_10,R.string.bot_month_11,R.string.bot_month_12)
    private val monthShort=listOf(R.string.bot_month_short_1,R.string.bot_month_short_2,R.string.bot_month_short_3,R.string.bot_month_short_4,
        R.string.bot_month_short_5,R.string.bot_month_short_6,R.string.bot_month_short_7,R.string.bot_month_short_8,R.string.bot_month_short_9,R.string.bot_month_short_10,R.string.bot_month_short_11,R.string.bot_month_short_12)
    private val facets by lazy {
        listOf(
            Facet("collection",R.string.bot_collections,catalog.collections.map{it.id to it.title}){it.collectionIds},
            Facet("section",R.string.bot_explore,catalog.sections.map{it.id to it.title}){it.sectionIds},
            Facet("family",R.string.bot_family,catalog.families.map{it.id to it.name}){setOf(it.familyId)},
            Facet("leaf",R.string.bot_leaf_shape,LeafShape.entries.map{it.name to it.label}){p->p.leafShapes.map{it.name}.toSet()},
            Facet("margin",R.string.bot_leaf_margin,LeafMargin.entries.map{it.name to it.label}){p->p.leafMargins.map{it.name}.toSet()},
            Facet("arrangement",R.string.bot_leaf_arrangement,LeafArrangement.entries.map{it.name to it.label}){p->p.leafArrangements.map{it.name}.toSet()},
            Facet("color",R.string.bot_flower_color,FlowerColor.entries.map{it.name to it.label}){p->p.flowerColors.map{it.name}.toSet()},
            Facet("flower",R.string.bot_flower_shape,FlowerShape.entries.map{it.name to it.label}){setOf(it.look.flower.name)},
            Facet("fruit",R.string.bot_fruit_shape,FruitShape.entries.map{it.name to it.label}){setOf(it.look.fruit.name)},
            Facet("habitat",R.string.bot_habitat,Habitat.entries.map{it.name to it.label}){p->p.habitats.map{it.name}.toSet()},
            Facet("month",R.string.bot_month,monthIds.mapIndexed{i,r->(i+1).toString() to r}){p->p.flowering.map{it.toString()}.toSet()},
            Facet("foliage",R.string.bot_foliage,listOf("true" to R.string.bot_evergreen,"false" to R.string.bot_deciduous)){if(it.variablePersistence)setOf("true","false") else setOf(it.evergreen.toString())}
        )
    }
    private val english by lazy {createConfigurationContext(Configuration(resources.configuration).apply{setLocale(Locale.ENGLISH)}).resources}
    private val french by lazy {createConfigurationContext(Configuration(resources.configuration).apply{setLocale(Locale.FRENCH)}).resources}
    private val searchIndex by lazy {catalog.species.associate{p->
        val family=catalog.family(p.familyId)
        p.id to listOf(english,french).flatMap{r->listOf(r.getString(p.name),r.getString(p.aliases),r.getString(p.latin),r.getString(family.name),
            if(family.aliases==0) "" else r.getString(family.aliases)) +
            catalog.collections.filter{it.id in p.collectionIds}.map{r.getString(it.title)}}.joinToString(" ")
    }}

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        tab=Tab.entries.firstOrNull{it.name==(savedInstanceState?.getString("tab") ?: prefs.getString("tab",null))} ?: Tab.EXPLORE
        search=savedInstanceState?.getString("search") ?: prefs.getString("search","").orEmpty()
        sort=PlantSort.entries.firstOrNull{it.name==(savedInstanceState?.getString("sort") ?: prefs.getString("sort",null))} ?: PlantSort.NAME
        mode=PlantView.entries.firstOrNull{it.name==(savedInstanceState?.getString("mode") ?: prefs.getString("mode",null))} ?: PlantView.HABIT
        marks=savedInstanceState?.getBoolean("marks") ?: prefs.getBoolean("marks",true)
        filtersOpen=savedInstanceState?.getBoolean("filters") ?: prefs.getBoolean("filters",false)
        for(f in facets) {
            val ids=savedInstanceState?.getStringArrayList("facet_${f.key}")?.toSet() ?: prefs.getStringSet("facet_${f.key}",emptySet()).orEmpty()
            selected[f.key]=ids.filterTo(mutableSetOf()){id->f.options.any{it.first==id} && catalog.species.any{id in f.present(it)}}
        }
        expanded.addAll(savedInstanceState?.getStringArrayList("expanded") ?: prefs.getStringSet("expanded",emptySet()).orEmpty())
        lifeSearch=savedInstanceState?.getString("life_search").orEmpty()
        lifeFamily=savedInstanceState?.getString("life_family").orEmpty()
        lifeBranch=savedInstanceState?.getString("life_branch").orEmpty()
        lifeLatinSort=savedInstanceState?.getBoolean("life_latin_sort") ?: false
        lifeLimit=savedInstanceState?.getInt("life_limit") ?: 40
        buildUi();render()
        loadLife()
        savedInstanceState?.getStringArrayList("history")?.let{history.addAll(it)}
        val sheet=if(savedInstanceState!=null)savedInstanceState.getString("sheet")
            else intent.getStringExtra(EXTRA_LIFE_NODE)?.let{"life:$it"} ?: intent.getStringExtra(EXTRA_SPECIES_ID)?.let{"species:$it"}
        sheet?.let { key->content.post{open(key,false)} }
        val y=savedInstanceState?.getInt("scroll") ?: 0
        scroll.post{scroll.scrollTo(0,y)}
    }

    private fun buildUi() {
        val root=column().apply{setBackgroundColor(palette.background)}
        val toolbar=row()
        toolbar.addView(icon(R.drawable.ic_arrow_back_24,R.string.bot_back){finish()}.apply{
            ScienceNavigation.bindHomeAction(this){fiche.dismiss();tab=Tab.EXPLORE;render();scroll.scrollTo(0,0)}
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        toolbar.addView(label(getString(R.string.bot_title),20f,true),LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(icon(R.drawable.ic_more_vert_24,R.string.bot_about){open("about")},LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(toolbar)
        tabs=row().apply{setPadding(dp(12),0,dp(12),0)}
        root.addView(horizontal(tabs))
        content=column().apply{setPadding(dp(16),dp(10),dp(16),dp(24))}
        scroll=ScrollView(this).apply{isFillViewport=true;addView(content,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL))}
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root){view,insets->
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom)
            scroll.post {
                val target=minOf(scroll.width,dp(840))
                if(target>0 && content.layoutParams.width!=target)
                    content.layoutParams=(content.layoutParams as FrameLayout.LayoutParams).apply{width=target;gravity=Gravity.CENTER_HORIZONTAL}
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun render() {
        tabs.removeAllViews();content.removeAllViews()
        Tab.entries.forEach { t->tabs.addView(button(getString(t.title),tab==t){tab=t;render();scroll.scrollTo(0,0)},wrap()) }
        when(tab) {
            Tab.EXPLORE->explore()
            Tab.LIFE->lifeBrowser()
            Tab.COMPARE-> {
                content.addView(label(getString(R.string.bot_compare_note),14f),full())
                catalog.groups.forEach {g->content.addView(groupCard(g),full())}
            }
            Tab.FAMILIES-> {
                content.addView(label(getString(R.string.bot_port_note),14f),full())
                catalog.families.sortedBy{getString(it.name)}.forEach {f->
                    val members=catalog.species.filter{it.familyId==f.id}
                    if(members.isNotEmpty()) content.addView(card(getString(f.name),getString(f.description),members.first(),PlantView.LEAF){open("family:${f.id}")},full())
                }
            }
            Tab.LEARN-> {
                content.addView(label(getString(R.string.bot_learn_intro),14f),full())
                BotanyLessons.all.forEach {lesson->content.addView(card(getString(lesson.title),null,catalog.get(lesson.specimen),lesson.view){open("lesson:${lesson.id}")},full())}
            }
        }
    }

    private fun explore() {
        val results=column()
        val controls=mutableListOf<Pair<Chip,Pair<String,String>>>()
        val toggle=button(getString(R.string.bot_filters),filtersOpen){}
        val filterPanel=column().apply{visibility=if(filtersOpen) View.VISIBLE else View.GONE}
        val searchBox=EditText(this).apply {
            isSingleLine=true;hint=getString(R.string.bot_search);textSize=15f;setTextColor(palette.text);setHintTextColor(palette.secondary)
            background=palette.shape(palette.raised,12f);setPadding(dp(12),dp(10),dp(12),dp(10));setText(search)
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        var pendingRefresh:Runnable?=null
        fun refresh() {
            pendingRefresh?.let{results.removeCallbacks(it)};pendingRefresh=null
            controls.forEach{(chip,key)->chip.isChecked=key.second in selected[key.first].orEmpty()}
            val count=selected.values.sumOf{it.size}
            toggle.text=if(count==0)getString(R.string.bot_filters) else getString(R.string.bot_filters_count,count)
            results.removeAllViews()
            val q=query()
            val found=PlantQuery.sorted(catalog.species.filter{q.matches(it,searchIndex[it.id].orEmpty())},sort,resources.configuration.locales[0],
                {getString(it.name)},{getString(it.latin)},{getString(catalog.family(it.familyId).name)})
            results.addView(label(resources.getQuantityString(R.plurals.bot_species_count,found.size,found.size),13f).apply{accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE},full())
            catalog.collections.filter{it.id in q.collections}.forEach{
                results.addView(label(getString(it.description),13f).apply{setTextColor(palette.secondary)},full(4))
            }
            if(q.months.isNotEmpty())results.addView(label(getString(R.string.bot_month_scope),12f).apply{setTextColor(palette.secondary)},full(4))
            if(found.isEmpty())results.addView(label(getString(R.string.bot_empty),15f),full())
            found.forEach{results.addView(speciesCard(it),full())}
        }
        content.addView(searchBox,full())
        content.addView(label(getString(R.string.bot_collections),13f,true),full(4))
        val collections=ChipGroup(this)
        for(collection in catalog.collections) {
            val ch=chip(getString(collection.title),collection.id in selected["collection"].orEmpty()){
                flip("collection",collection.id);refresh()
            }
            controls+=ch to ("collection" to collection.id);collections.addView(ch)
        }
        content.addView(collections,full(0))
        content.addView(label(getString(R.string.bot_collection_help),12f).apply{setTextColor(palette.secondary)},full(4))
        val sectionGroup=ChipGroup(this)
        for(section in catalog.sections) {
            val chip=chip(getString(section.title),section.id in selected["section"].orEmpty()){
                flip("section",section.id);refresh()
            }
            controls+=chip to ("section" to section.id);sectionGroup.addView(chip)
        }
        content.addView(sectionGroup,full(0))
        content.addView(label(getString(R.string.bot_port_note),12.5f).apply{setTextColor(palette.secondary)},full(4))
        val options=row()
        options.addView(toggle,LinearLayout.LayoutParams(0,-2,1f))
        options.addView(button(getString(R.string.bot_reset)){
            selected.values.forEach{it.clear()};search="";searchBox.setText("");refresh()
        },wrap())
        content.addView(options,full())
        var filtersBuilt=false
        fun buildFilters() {
            if(filtersBuilt)return
            filtersBuilt=true
            filterPanel.addView(label(getString(R.string.bot_filter_help),13f),full())
            for(f in facets.filter{it.key!="section" && it.key!="collection"}) {
                val offered=f.options.filter{(id,_)->catalog.species.any{id in f.present(it)}}
                if(offered.isEmpty())continue
                val flow=ChipGroup(this).apply{visibility=if(f.key in expanded)View.VISIBLE else View.GONE}
                fun populate() {
                    if(flow.childCount>0)return
                    for((id,res) in offered) {
                        val ch=chip(getString(res),id in selected[f.key].orEmpty()){flip(f.key,id);refresh()}
                        controls+=ch to (f.key to id);flow.addView(ch)
                    }
                }
                filterPanel.addView(button(getString(f.title)){
                    if(!expanded.remove(f.key))expanded.add(f.key)
                    if(f.key in expanded)populate()
                    flow.visibility=if(f.key in expanded)View.VISIBLE else View.GONE
                },full(4))
                if(f.key in expanded)populate()
                filterPanel.addView(flow,full(0))
            }
        }
        toggle.setOnClickListener {
            filtersOpen=!filtersOpen
            if(filtersOpen)buildFilters()
            filterPanel.visibility=if(filtersOpen)View.VISIBLE else View.GONE;style(toggle,filtersOpen)
        }
        if(filtersOpen)buildFilters()
        content.addView(filterPanel,full(0))
        val sortRow=row()
        sortRow.addView(label(getString(R.string.bot_sort),13f),wrap())
        val sortTitles=listOf(R.string.bot_sort_name,R.string.bot_sort_latin,R.string.bot_sort_family,R.string.bot_sort_height,R.string.bot_sort_flowering)
        sortRow.addView(Spinner(this).apply{
            adapter=object:ArrayAdapter<String>(this@BotanyActivity,android.R.layout.simple_spinner_item,sortTitles.map{getString(it)}) {
                override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View =
                    (super.getView(position,convertView,parent) as TextView).apply{setTextColor(palette.text);textSize=14f}
                override fun getDropDownView(position:Int,convertView:View?,parent:android.view.ViewGroup):View =
                    (super.getDropDownView(position,convertView,parent) as TextView).apply{setTextColor(palette.text);setBackgroundColor(palette.raised);setPadding(dp(12),dp(14),dp(12),dp(14))}
            }.apply{setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)}
            contentDescription=getString(R.string.bot_sort);setSelection(sort.ordinal)
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){val next=PlantSort.entries[position];if(next!=sort){sort=next;refresh()}}
                override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            }
        },LinearLayout.LayoutParams(0,dp(48),1f))
        content.addView(sortRow,full(4));content.addView(results,full(0))
        searchBox.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                search=s.toString();pendingRefresh?.let{results.removeCallbacks(it)}
                pendingRefresh=Runnable { if(results.isAttachedToWindow)refresh() }.also{results.postDelayed(it,140)}
            }
            override fun afterTextChanged(s:Editable?)=Unit
        })
        refresh()
    }

    private fun flip(key:String,id:String){val set=selected.getOrPut(key){mutableSetOf()};if(!set.remove(id))set.add(id)}
    private inline fun <reified E:Enum<E>> enums(key:String)=enumValues<E>().filterTo(mutableSetOf()){it.name in selected[key].orEmpty()}
    private fun query()=PlantQuery(search,selected["section"].orEmpty(),selected["family"].orEmpty(),
        enums("leaf"),enums("margin"),enums("arrangement"),enums("color"),enums("flower"),enums("fruit"),enums("habitat"),
        selected["month"].orEmpty().mapNotNull{it.toIntOrNull()}.toSet(),selected["foliage"]?.singleOrNull()?.toBooleanStrictOrNull(),
        collections=selected["collection"].orEmpty())

    private fun speciesCard(p:PlantSpecies)=card(getString(p.name),getString(p.latin),p,PlantView.HABIT){open("species:${p.id}")}
    private fun card(title:String,subtitle:String?,p:PlantSpecies?,view:PlantView,action:()->Unit):View {
        val r=row().apply{background=palette.shape(palette.raised);setPadding(dp(10),dp(10),dp(12),dp(10));isClickable=true;isFocusable=true;setOnClickListener{action()}}
        p?.let{r.addView(PlantPlateView(this,it,view,true).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(76),dp(88)))}
        val text=column().apply{setPadding(dp(12),0,0,0)}
        text.addView(label(title,16f,true));subtitle?.let{text.addView(label(it,13f).apply{setTextColor(palette.secondary)},full(4))}
        r.addView(text,LinearLayout.LayoutParams(0,-2,1f));return r
    }

    private fun groupCard(g:PlantGroup):View {
        val box=column().apply{background=palette.shape(palette.raised);setPadding(dp(12),dp(10),dp(12),dp(10));isClickable=true;isFocusable=true;setOnClickListener{open("group:${g.id}")}}
        box.addView(label(getString(g.title),17f,true))
        val plates=row()
        g.members.take(4).forEach{id->catalog.get(id)?.let{p->
            val col=column();col.addView(PlantPlateView(this,p,PlantView.LEAF,true).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(-1,dp(112)))
            col.addView(label(getString(p.name),12f).apply{gravity=Gravity.CENTER},full(3))
            plates.addView(col,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(4)})
        }}
        box.addView(plates,full());return box
    }

    private fun open(key:String,push:Boolean=true) {
        val previous=current
        if(push && previous!=null && previous!=key)history.add(previous)
        current=key
        val back:(()->Unit)?=if(history.isEmpty())null else ({open(history.removeAt(history.lastIndex),false)})
        when {
            key=="about"->showAbout(back)
            key.startsWith("life:")->showLife(key.substringAfter(':'),back)
            key.startsWith("species:")->catalog.get(key.substringAfter(':'))?.let{showSpecies(it,back)}
            key.startsWith("group:")->catalog.groups.firstOrNull{it.id==key.substringAfter(':')}?.let{showGroup(it,back)}
            key.startsWith("family:")->catalog.families.firstOrNull{it.id==key.substringAfter(':')}?.let{showFamily(it,back)}
            key.startsWith("lesson:")->BotanyLessons.all.firstOrNull{it.id==key.substringAfter(':')}?.let{lesson->
                val p=catalog.get(lesson.specimen) ?: return
                val body=column();body.addView(label(getString(lesson.body),16f),full())
                body.addView(button(getString(p.name)){open("species:${p.id}")},full())
                fiche.show(getString(lesson.title),null,media(p,lesson.view,allowModes=false),body,back)
            }
        }
    }

    private fun persistence(p:PlantSpecies)=if(p.variablePersistence)R.string.bot_persistence_variable
        else if(p.evergreen)R.string.bot_evergreen else R.string.bot_deciduous

    private fun showSpecies(p:PlantSpecies,back:(()->Unit)?) {
        val top=column()
        top.addView(label(getString(p.latin),17f).apply{setTypeface(typeface,Typeface.ITALIC)})
        top.addView(label(getString(p.aliases),13f).apply{setTextColor(palette.secondary)},full(4))
        top.addView(button(getString(catalog.family(p.familyId).name)){open("family:${p.familyId}")},full(4))
        val body=column()
        life?.index?.forIllustrated(p.id)?.let { entry ->
            body.addView(button(getString(R.string.bot_life_open_tree)){openTree(entry.nodeId)},full())
        }
        heading(body,R.string.bot_traits)
        body.addView(label(getString(p.description),16f),full(4))
        body.addView(label(getString(R.string.bot_height),13f,true),full())
        body.addView(label(height(p),16f),full(2))
        body.addView(label(getString(persistence(p)),14f),full(4))
        heading(body,R.string.bot_ecology)
        body.addView(label(p.habitats.joinToString(" · "){getString(it.label)},15f),full(4))
        body.addView(label(getString(p.distribution),14f),full(4))
        body.addView(label(catalog.collections.filter{it.id in p.collectionIds}.joinToString(" · "){getString(it.title)},12f).apply{setTextColor(palette.secondary)},full(4))
        heading(body,R.string.bot_calendar)
        if(p.flowering.isNotEmpty() || p.fruiting.isNotEmpty())body.addView(calendar(p),full(4))
        body.addView(label(getString(p.phenologyNote.takeIf{it!=0} ?: R.string.bot_calendar_note),13f).apply{setTextColor(palette.secondary)},full(4))
        val groups=catalog.groups.filter{p.id in it.members}
        if(groups.isNotEmpty()){heading(body,R.string.bot_related);groups.forEach{g->body.addView(button(getString(g.title)){open("group:${g.id}")},full(4))}}
        fiche.show(getString(p.name),top,media(p,mode),body,back)
    }

    private fun media(p:PlantSpecies,initial:PlantView,allowModes:Boolean=true):View {
        val box=column()
        val plate=PlantPlateView(this,p,initial).apply{marks=this@BotanyActivity.marks}
        val legend=column()
        fun updateLegend() {
            legend.removeAllViews()
            if(!plate.marks)return
            val labels=when(plate.mode) {
                PlantView.HABIT->listOf(p.captions.getValue(PlantView.HABIT),persistence(p),R.string.bot_height)
                PlantView.LEAF->listOf(p.look.leaf.label,p.look.arrangement.label,p.look.margin.label)
                PlantView.FLOWER->listOf(p.look.flower.label,p.look.color.label)
                PlantView.FRUIT->listOf(p.look.fruit.label)
            }
            labels.forEachIndexed{i,res->legend.addView(label(getString(R.string.bot_legend_format,i+1,getString(res)),12f),full(2))}
        }
        if(allowModes)box.addView(viewBar(initial){v->mode=v;plate.mode=v;updateLegend()},full(0))
        box.addView(CheckBox(this).apply{text=getString(R.string.bot_marks);setTextColor(palette.text);isChecked=marks
            setOnCheckedChangeListener{_,checked->marks=checked;plate.marks=checked;updateLegend()}},full(0))
        val frame=FitFrame(this,340f/300f,dp(460)).apply{addView(plate,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))}
        box.addView(frame,if(fiche.wide)LinearLayout.LayoutParams(-1,0,1f) else full(4))
        box.addView(legend,full(2));updateLegend()
        box.addView(label(getString(R.string.bot_plate_note),11.5f).apply{setTextColor(palette.secondary)},full(4))
        return box
    }

    private fun viewBar(initial:PlantView,action:(PlantView)->Unit):View {
        val r=row();val buttons=mutableMapOf<PlantView,Button>()
        PlantView.entries.forEach{v->
            val b=button(getString(v.label),v==initial){buttons.forEach{(k,b)->style(b,k==v)};action(v)}
            buttons[v]=b;r.addView(b,wrap())
        }
        return horizontal(r)
    }

    private fun showGroup(g:PlantGroup,back:(()->Unit)?) {
        val body=column()
        body.addView(label(getString(R.string.bot_compare_note),13f),full())
        val plates=mutableListOf<PlantPlateView>()
        body.addView(viewBar(mode){v->mode=v;plates.forEach{it.mode=v}},full())
        val r=row().apply{gravity=Gravity.TOP}
        val cellWidth=dp(225)
        g.members.mapNotNull{catalog.get(it)}.forEach{p->
            val col=column()
            val plate=PlantPlateView(this,p,mode).apply{marks=false};plates+=plate
            col.addView(plate,LinearLayout.LayoutParams(cellWidth,dp(255)))
            col.addView(button(getString(p.name)){open("species:${p.id}")},full(4))
            col.addView(label(getString(p.latin),12f).apply{setTypeface(typeface,Typeface.ITALIC)},full(2))
            r.addView(col,LinearLayout.LayoutParams(cellWidth,-2).apply{marginEnd=dp(10)})
        }
        body.addView(horizontal(r),full())
        body.addView(label(getString(g.differences),16f),full())
        body.addView(label(getString(R.string.bot_plate_note),12f),full())
        fiche.show(getString(g.title),null,null,body,back)
    }

    private fun showFamily(f:PlantFamily,back:(()->Unit)?) {
        val body=column();body.addView(label(getString(f.description),16f),full())
        val members=catalog.species.filter{it.familyId==f.id}
        body.addView(label(resources.getQuantityString(R.plurals.bot_species_count,members.size,members.size),13f),full())
        members.groupBy{getString(it.latin).substringBefore(' ')}.toSortedMap().forEach{(genus,plants)->
            body.addView(label(getString(R.string.bot_genus,genus),16f,true),full(14))
            plants.forEach{body.addView(speciesCard(it),full())}
        }
        fiche.show(getString(f.name),null,null,body,back)
    }

    private fun calendar(p:PlantSpecies):View {
        val box=column()
        for((title,months) in listOf(R.string.bot_bloom to p.flowering,R.string.bot_fruiting to p.fruiting)) {
            box.addView(label(getString(title),13f,true),full(6))
            // Two rows of six, so month names remain legible on a phone with large fonts.
            repeat(2){half->val r=row();repeat(6){j->val m=half*6+j;val active=m+1 in months
                r.addView(label(getString(monthShort[m]),12f,active).apply{
                    gravity=Gravity.CENTER;setPadding(dp(2),dp(8),dp(2),dp(8));background=palette.control(active)
                    setTextColor(if(active)palette.onAccent else palette.secondary)
                    contentDescription=getString(R.string.bot_calendar_cell,getString(title),getString(monthIds[m]),getString(if(active)R.string.bot_present else R.string.bot_absent))
                },LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(3)})
            };box.addView(r,full(3))}
        }
        return box
    }

    private fun showAbout(back:(()->Unit)?) {
        val body=column()
        body.addView(label(getString(R.string.bot_about_text),15f),full())
        body.addView(label(getString(R.string.bot_about_scope),14f),full())
        heading(body,R.string.bot_references)
        catalog.sources.forEach{s->body.addView(button(getString(s.title)){link(s.url)},full(4))}
        heading(body,R.string.bot_sources_species)
        catalog.species.forEach{p->
            val latin=getString(p.latin)
            body.addView(label(getString(p.name),15f,true),full(12))
            body.addView(button(getString(R.string.bot_lookup_powo,latin)){link("https://powo.science.kew.org/results?q="+android.net.Uri.encode(latin))},full(2))
            BotanyReferences.taxonPages[p.id]?.let{url->body.addView(button(getString(R.string.bot_taxon_reference,latin)){link(url)},full(2))}
        }
        fiche.show(getString(R.string.bot_about),null,null,body,back)
    }

    private fun link(url:String){try{startActivity(Intent(Intent.ACTION_VIEW,url.toUri()))}catch(_:ActivityNotFoundException){Toast.makeText(this,R.string.bot_no_browser,Toast.LENGTH_SHORT).show()}}

    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        val key=intent.getStringExtra(EXTRA_LIFE_NODE)?.let{"life:$it"}
            ?: intent.getStringExtra(EXTRA_SPECIES_ID)?.let{"species:$it"}
        key?.let{open(it)}
    }

    private fun loadLife() {
        if(lifeLoading || life!=null)return
        lifeLoading=true;lifeFailed=false
        lifecycleScope.launch {
            val loaded=withContext(Dispatchers.Default){runCatching{BotanyLifeCatalogue.load(applicationContext)}}
            lifeLoading=false
            loaded.onSuccess{life=it}.onFailure{
                lifeFailed=true;android.util.Log.e("BotanyLife", "Plant reference catalogue load failed",it)
            }
            if(tab==Tab.LIFE)render()
            current?.takeIf{it.startsWith("life:") || it.startsWith("species:")}?.let{open(it,false)}
        }
    }

    private fun lifeBrowser() {
        content.addView(label(getString(R.string.bot_life_intro),14f),full())
        val data=life
        if(data==null){
            content.addView(label(getString(if(lifeFailed)R.string.bot_life_error else R.string.bot_life_loading)),full())
            if(lifeFailed)content.addView(button(getString(R.string.bot_life_retry)){loadLife();render()},full())
            return
        }
        val repo=data.repository
        val results=column()
        fun refresh() {
            results.removeAllViews()
            val words=PlantQuery.normalize(lifeSearch).split(Regex("\\s+")).filter{it.isNotEmpty()}
            val collator=java.text.Collator.getInstance(resources.configuration.locales[0])
            val found=data.index.entries.filter{entry->
                (lifeFamily.isEmpty() || entry.family==lifeFamily) && (lifeBranch.isEmpty() || lifeBranch in entry.lineage) &&
                    words.all{it in data.searchTerms.getValue(entry.nodeId)}
            }.sortedWith { a,b ->
                val first=if(lifeLatinSort)a.scientific else repo.name(this,a.nodeId)
                val second=if(lifeLatinSort)b.scientific else repo.name(this,b.nodeId)
                collator.compare(first,second).takeIf{it!=0} ?: a.nodeId.compareTo(b.nodeId)
            }
            results.addView(label(getString(R.string.bot_life_count,found.size),13f),full())
            for(entry in found.take(lifeLimit)) {
                val name=repo.name(this,entry.nodeId)
                val status=getString(if(entry.illustratedId!=null)R.string.bot_life_illustrated else R.string.bot_life_reference)
                results.addView(button(getString(R.string.bot_life_row,name,entry.scientific,status)){open("life:${entry.nodeId}")}.apply{gravity=Gravity.START},full(4))
            }
            if(found.size>lifeLimit)results.addView(button(getString(R.string.bot_life_more)){lifeLimit+=40;refresh()},full())
        }
        content.addView(EditText(this).apply{
            isSingleLine=true;hint=getString(R.string.bot_life_search);setTextColor(palette.text);setHintTextColor(palette.secondary);setText(lifeSearch)
            addTextChangedListener(object:TextWatcher{
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){lifeSearch=s.toString();lifeLimit=40;refresh()}
                override fun afterTextChanged(s:Editable?)=Unit
            })
        },full())
        fun selector(title:Int,items:List<Pair<String,String>>,selected:String,change:(String)->Unit) {
            content.addView(label(getString(title),13f,true),full())
            content.addView(Spinner(this).apply{
                adapter=ArrayAdapter(this@BotanyActivity,android.R.layout.simple_spinner_dropdown_item,items.map{it.second})
                setSelection(items.indexOfFirst{it.first==selected}.coerceAtLeast(0))
                onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
                    override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){change(items[position].first);refresh()}
                    override fun onNothingSelected(parent:AdapterView<*>?)=Unit
                }
            },full(2))
        }
        val all="" to getString(R.string.bot_life_all)
        selector(R.string.bot_family,listOf(all)+data.index.entries.mapNotNull{it.family}.distinct().sorted().map{it to it},lifeFamily){if(it!=lifeFamily)lifeLimit=40;lifeFamily=it}
        val branches=repo.groups.keys.filter{it!=BotanyLifeIndex.GREEN_ROOT && data.index.isPlant(it)}
            .map{it to repo.name(this,it)}.sortedBy{it.second}
        selector(R.string.bot_life_branch,listOf(all)+branches,lifeBranch){if(it!=lifeBranch)lifeLimit=40;lifeBranch=it}
        content.addView(CheckBox(this).apply{text=getString(R.string.bot_life_sort_latin);setTextColor(palette.text);isChecked=lifeLatinSort
            setOnCheckedChangeListener{_,checked->lifeLatinSort=checked;refresh()}},full())
        content.addView(results,full());refresh()
    }

    private fun showLife(id:String,back:(()->Unit)?) {
        val data=life
        if(data==null){
            val body=column();body.addView(label(getString(if(lifeFailed)R.string.bot_life_error else R.string.bot_life_loading)),full())
            if(lifeFailed)body.addView(button(getString(R.string.bot_life_retry)){loadLife();showLife(id,back)},full())
            fiche.show(getString(R.string.bot_life_reference),null,null,body,back);return
        }
        val entry=data.index.forNode(id)
        if(entry==null){Toast.makeText(this,R.string.bot_life_missing,Toast.LENGTH_LONG).show();fiche.dismiss();return}
        entry.illustratedId?.let{catalog.get(it)}?.let{showSpecies(it,back);return}
        val repo=data.repository
        val top=column();top.addView(label(entry.scientific,17f).apply{setTypeface(typeface,Typeface.ITALIC)})
        val body=column()
        body.addView(label(getString(R.string.bot_life_reference_note),14f),full())
        repo.notes[id]?.let{note->heading(body,R.string.bot_life_notice);body.addView(label(repo.text(this,note.key),16f),full())}
        heading(body,R.string.bot_life_classification)
        entry.lineage.reversed().filter{repo.tree.nodes.getValue(it).scientific.isNotBlank() || it in repo.groups}.forEach { ancestor ->
            body.addView(button(repo.name(this,ancestor)){openTree(ancestor)},full(3))
        }
        entry.family?.let{family->body.addView(label(getString(R.string.bot_life_family_value,family),14f),full())}
        entry.lineage.firstOrNull{it in repo.groups}?.let{group->
            heading(body,R.string.bot_life_group_traits)
            body.addView(label(repo.text(this,repo.groups.getValue(group).key+"_body"),15f),full())
        }
        body.addView(button(getString(R.string.bot_life_open_tree)){openTree(id)},full())
        heading(body,R.string.bot_sources_species)
        body.addView(label(getString(R.string.bot_life_provenance,repo.version,repo.taxonomy,repo.retrieved),12f),full())
        body.addView(button(getString(R.string.bot_life_source_tree)){link("https://tree.opentreeoflife.org/opentree/${repo.version}@$id")},full())
        val qid=repo.notes[id]?.qid ?: repo.labels[id]?.qid
        if(qid?.matches(Regex("Q[0-9]+"))==true)body.addView(button(getString(R.string.bot_life_source_notice)){link("https://www.wikidata.org/wiki/$qid")},full())
        body.addView(button(getString(R.string.bot_life_source_kew)){link("https://powo.science.kew.org/results?q=${android.net.Uri.encode(entry.scientific)}")},full())
        fiche.show(repo.name(this,id),top,null,body,back)
    }

    private fun openTree(id:String) {
        startActivity(Intent(this,ParentesActivity::class.java).putExtra(ScienceNavigation.EXTRA_FROM_MODULE,true)
            .putExtra(ParentesActivity.EXTRA_NODE_ID,id))
    }

    companion object {
        const val EXTRA_LIFE_NODE="botany_life_node"
        const val EXTRA_SPECIES_ID="botany_species_id"
    }

    private fun height(p:PlantSpecies):String {
        if(p.heightMaxCm<200)return getString(R.string.bot_height_cm,p.heightMinCm,p.heightMaxCm)
        val n=NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply{maximumFractionDigits=1}
        return getString(R.string.bot_height_m,n.format(p.heightMinCm/100f),n.format(p.heightMaxCm/100f))
    }
    private fun heading(parent:LinearLayout,title:Int){parent.addView(label(getString(title),17f,true).apply{ViewCompat.setAccessibilityHeading(this,true)},full(18))}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun label(value:String,size:Float=15f,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size;setTextColor(palette.text);if(bold)setTypeface(typeface,Typeface.BOLD)}
    private fun horizontal(view:View)=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(view)}
    private fun button(text:String,active:Boolean=false,action:()->Unit)=Button(this).apply{
        this.text=text;isAllCaps=false;textSize=13f;minimumHeight=dp(44);minHeight=dp(44);setPadding(dp(12),dp(4),dp(12),dp(4));style(this,active);setOnClickListener{action()}
    }
    private fun style(b:Button,active:Boolean){b.background=palette.control(active);b.setTextColor(if(active)palette.onAccent else palette.text);b.isSelected=active}
    private fun chip(title:String,active:Boolean,action:()->Unit)=Chip(this).apply{
        text=title;isCheckable=true;isChecked=active;isClickable=true;setEnsureMinTouchTargetSize(true)
        setTextColor(palette.text);chipBackgroundColor=android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(palette.categorySurface(palette.accent),palette.raised))
        setOnClickListener{action()}
    }
    private fun icon(drawable:Int,title:Int,action:()->Unit)=ImageButton(this).apply{setImageResource(drawable);setColorFilter(palette.text);background=null;contentDescription=getString(title);setOnClickListener{action()}}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun full(top:Int=8)=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)}
    private fun wrap()=LinearLayout.LayoutParams(-2,-2).apply{marginEnd=dp(6)}

    override fun onSaveInstanceState(out:Bundle) {
        super.onSaveInstanceState(out)
        out.putString("life_search",lifeSearch);out.putString("life_family",lifeFamily);out.putString("life_branch",lifeBranch)
        out.putBoolean("life_latin_sort",lifeLatinSort);out.putInt("life_limit",lifeLimit)
        out.putString("tab",tab.name);out.putString("search",search);out.putString("sort",sort.name);out.putString("mode",mode.name)
        out.putBoolean("marks",marks);out.putBoolean("filters",filtersOpen);out.putString("sheet",current)
        out.putStringArrayList("history",ArrayList(history));out.putStringArrayList("expanded",ArrayList(expanded));out.putInt("scroll",scroll.scrollY)
        selected.forEach{(key,value)->out.putStringArrayList("facet_$key",ArrayList(value))}
    }
    override fun onPause() {
        super.onPause()
        prefs.edit().apply{
            putString("tab",tab.name);putString("search",search);putString("sort",sort.name);putString("mode",mode.name)
            putBoolean("marks",marks);putBoolean("filters",filtersOpen);putStringSet("expanded",expanded.toSet())
            selected.forEach{(key,value)->putStringSet("facet_$key",value.toSet())}
        }.apply()
    }
    override fun onDestroy(){fiche.dismiss();super.onDestroy()}
}
