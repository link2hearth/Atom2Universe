package com.Atom2Universe.app.science.geology

import android.animation.ValueAnimator
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.net.toUri
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.parentes.ParentesActivity
import com.Atom2Universe.app.science.timeline.*
import com.Atom2Universe.app.util.followImmersiveMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.Normalizer
import java.util.Locale

class GeologyActivity:ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private val prefs by lazy { getSharedPreferences("living_earth",MODE_PRIVATE) }
    private lateinit var content:LinearLayout
    private lateinit var scroll:ScrollView
    private lateinit var tabs:LinearLayout
    private var scene=EarthScene.GLOBE
    private var section:EarthSectionView?=null
    private var stage=.65f
    private var phase=0f
    private var labels=true
    private var realScale=true
    private var playing=true
    private var resumed=false
    private var animator:ValueAnimator?=null
    private var openSheet:BottomSheetDialog?=null
    private var sheetTopic:String?=null
    private var timeRank=GeologicalRank.EON

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        scene=EarthScene.entries.firstOrNull { it.name==(savedInstanceState?.getString("scene") ?: prefs.getString("scene",null)) } ?: EarthScene.GLOBE
        stage=(savedInstanceState?.getFloat("stage") ?: prefs.getFloat("stage",.65f)).coerceIn(0f,1f)
        labels=savedInstanceState?.getBoolean("labels") ?: prefs.getBoolean("labels",true)
        realScale=savedInstanceState?.getBoolean("scale") ?: prefs.getBoolean("scale",true)
        playing=savedInstanceState?.getBoolean("playing") ?: prefs.getBoolean("playing",true)
        timeRank=GeologicalRank.entries.firstOrNull { it.name==(savedInstanceState?.getString("rank") ?: prefs.getString("rank",null)) } ?: GeologicalRank.EON
        buildUi();render()
        savedInstanceState?.getString("topic")?.let { content.post { showTopic(it) } }
        val y=savedInstanceState?.getInt("scroll") ?: 0
        scroll.post { scroll.scrollTo(0,y) }
    }
    private fun buildUi() {
        val root=column().apply { setBackgroundColor(palette.background) }
        val toolbar=row()
        toolbar.addView(icon(R.drawable.ic_arrow_back_24,R.string.geo_back) { finish() },LinearLayout.LayoutParams(dp(48),dp(48)))
        toolbar.addView(label(getString(R.string.geo_title),20f,true),LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(icon(R.drawable.ic_search,R.string.geo_catalog) { catalog() },LinearLayout.LayoutParams(dp(48),dp(48)))
        toolbar.addView(icon(R.drawable.ic_more_vert_24,R.string.geo_about) { about() },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(toolbar)
        tabs=row().apply { setPadding(dp(8),0,dp(8),0) }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(tabs) })
        content=column().apply { setPadding(dp(16),dp(12),dp(16),dp(24)) }
        scroll=ScrollView(this).apply { isFillViewport=true;addView(content) }
        val centered=FrameLayout(this).apply { addView(scroll,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER_HORIZONTAL)) }
        root.addView(centered,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom)
            centered.post { scroll.layoutParams=(scroll.layoutParams as FrameLayout.LayoutParams).apply { width=minOf(centered.width,dp(840)) } }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun render() {
        animator?.cancel();section=null;content.removeAllViews();tabs.removeAllViews()
        EarthChapter.entries.forEach { chapter ->
            tabs.addView(button(chapter.title,scene.chapter==chapter) {
                if(scene.chapter!=chapter) navigate(EarthScene.entries.first { it.chapter==chapter })
            },LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(6) })
        }
        if(scene==EarthScene.AGES) { renderTime();return }
        val choices=EarthScene.entries.filter { it.chapter==scene.chapter }
        val selector=Spinner(this)
        selector.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,choices.map { getString(it.title) })
        selector.contentDescription=getString(scene.chapter.title)
        selector.setSelection(choices.indexOf(scene))
        selector.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                (view as? TextView)?.setTextColor(palette.text)
                if(choices[position]!=scene) navigate(choices[position])
            }
        }
        content.addView(selector,full())
        section=EarthSectionView(this,scene,::showTopic).also {
            it.stage=stage;it.phase=phase;it.labels=labels;it.trueScale=realScale
            content.addView(it,full())
        }
        val controls=row()
        val pause=icon(if(playing) R.drawable.ic_pause else R.drawable.ic_play,
            if(playing) R.string.geo_pause else R.string.geo_play) {}
        pause.setOnClickListener {
            playing=!playing
            pause.setImageResource(if(playing) R.drawable.ic_pause else R.drawable.ic_play)
            pause.contentDescription=getString(if(playing) R.string.geo_pause else R.string.geo_play)
            syncAnimation()
        }
        controls.addView(pause,LinearLayout.LayoutParams(dp(48),dp(48)))
        val scaleNote=label(getString(if(scene==EarthScene.GLOBE && realScale) R.string.geo_scale_real_note else R.string.geo_scale_local_note),12f).apply { setTextColor(palette.secondary) }
        controls.addView(scaleNote,LinearLayout.LayoutParams(0,-2,1f))
        val settings=icon(R.drawable.ic_px_tune,R.string.geo_display_options) {}
        settings.setOnClickListener {
            PopupMenu(this,settings).apply {
                menu.add(R.string.geo_labels).apply {
                    isCheckable=true;isChecked=labels
                    setOnMenuItemClickListener { labels=!labels;section?.labels=labels;true }
                }
                if(scene==EarthScene.GLOBE) menu.add(R.string.geo_real_scale).apply {
                    isCheckable=true;isChecked=realScale
                    setOnMenuItemClickListener {
                        realScale=!realScale;section?.trueScale=realScale
                        scaleNote.setText(if(realScale) R.string.geo_scale_real_note else R.string.geo_scale_local_note)
                        true
                    }
                }
            }.show()
        }
        controls.addView(settings,LinearLayout.LayoutParams(dp(48),dp(48)))
        content.addView(controls,full())
        if(scene in listOf(EarthScene.COLLISION,EarthScene.FOLDS,EarthScene.FAULTS,EarthScene.LANDSCAPE,EarthScene.STRATA,EarthScene.TRANSFORM)) {
            content.addView(SeekBar(this).apply {
                max=100;progress=(stage*100).toInt();contentDescription=getString(R.string.geo_step_label)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar:SeekBar?)=Unit
                    override fun onStopTrackingTouch(seekBar:SeekBar?)=Unit
                    override fun onProgressChanged(seekBar:SeekBar?,progress:Int,fromUser:Boolean) {
                        stage=progress/100f;section?.stage=stage
                    }
                })
            },full())
        }
        content.addView(label(getString(R.string.geo_info_hint),12f).apply { setTextColor(palette.secondary) },full())
        syncAnimation()
    }
    private fun navigate(target:EarthScene) {
        scene=target;stage=.65f;render();scroll.scrollTo(0,0)
    }
    private fun renderTime() {
        val ranks=row()
        GeologicalRank.entries.forEach { rank -> ranks.addView(button(rank.label,rank==timeRank) { timeRank=rank;render() },LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(4) }) }
        content.addView(ranks,full())
        content.addView(label(getString(R.string.geo_date_key),12f).apply { setTextColor(palette.secondary) },full())
        content.addView(label(getString(R.string.geo_young),12f,true),full())
        // Equal-height bands for browsing, explicitly not proportional to duration or rock thickness.
        EarthPeriods.all.filter { it.geology?.rank==timeRank }.sortedBy { it.geology!!.olderMa }.forEach { period ->
            val entry=row().apply { background=palette.shape(palette.raised);setPadding(dp(8),dp(4),dp(8),dp(4)) }
            entry.addView(View(this).apply { setBackgroundColor(period.color) },LinearLayout.LayoutParams(dp(5),dp(52)))
            entry.addView(button(period.title) { showTopic(period.id) }.apply {
                text=getString(R.string.geo_time_range,getString(period.title),period.dateLabel(this@GeologyActivity))
                gravity=Gravity.START or Gravity.CENTER_VERTICAL
            },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(8) })
            content.addView(entry,full())
        }
        content.addView(label(getString(R.string.geo_old),12f,true),full())
    }
    private fun showTopic(id:String) {
        val topic=GeologyCatalog.get(id)
        val period=EarthPeriods.all.firstOrNull { it.id==id }
        if(topic==null && period==null) return
        val body=column()
        body.addView(label(getString(topic?.title ?: period!!.title),22f,true),full())
        if(topic!=null) {
            body.addView(label(getString(topic.facts),15f,true),full())
            body.addView(label(getString(R.string.geo_origin),15f,true),full())
            body.addView(label(getString(topic.origin),15f),full())
            body.addView(label(getString(R.string.geo_detail),15f,true),full())
            body.addView(label(getString(topic.detail),15f),full())
            topic.periodId?.let { addTimeline(body,it) }
            topic.lifeId?.let { addLife(body,it) }
            GeologyCatalog.scene(id)?.takeIf { it!=scene }?.let { target ->
                body.addView(button(R.string.geo_related) { openSheet?.dismiss();navigate(target) },full())
            }
        } else if(period!=null) {
            body.addView(label(period.dateLabel(this),15f,true),full())
            body.addView(label(getString(period.description),15f),full())
            body.addView(label(getString(R.string.geo_period_note),14f),full())
            body.addView(label(getString(R.string.geo_period_precision),14f,true),full())
            body.addView(label(period.geology!!.precision(this),14f),full())
            addTimeline(body,id)
            GeologyCatalog.lifeByPeriod[id]?.let { addLife(body,it) }
        }
        showSheet(body,id)
    }
    private fun addTimeline(body:LinearLayout,id:String) {
        val chapter=TimelineChapters.get(id) ?: return
        body.addView(button(R.string.geo_timeline_link) {
            startActivity(Intent(this,CosmicTimelineActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(CosmicTimelineActivity.EXTRA_CHAPTER_ID,id))
        }.apply { text=getString(R.string.geo_timeline_link,getString(chapter.title)) },full())
    }
    private fun addLife(body:LinearLayout,id:String) {
        val date=LifeTimeline.get(id) ?: return
        body.addView(button(R.string.geo_tree_link) {
            startActivity(Intent(this,ParentesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(ParentesActivity.EXTRA_NODE_ID,date.nodeId))
        }.apply { text=getString(R.string.geo_tree_link,getString(date.title)) },full())
    }
    private fun addSource(body:LinearLayout,source:EarthSource) {
        body.addView(button(source.title) { openUrl(source.url) },full())
    }
    private fun openUrl(url:String) {
        try { startActivity(Intent(Intent.ACTION_VIEW,url.toUri())) }
        catch(_:ActivityNotFoundException) { Toast.makeText(this,R.string.geo_no_browser,Toast.LENGTH_SHORT).show() }
    }
    private fun catalog() {
        val body=column()
        body.addView(label(getString(R.string.geo_catalog),20f,true),full())
        val search=EditText(this).apply { setHint(R.string.geo_search_hint);setTextColor(palette.text);setHintTextColor(palette.secondary);isSingleLine=true }
        body.addView(search,full())
        val results=column();body.addView(results,full())
        fun filter(query:String) {
            val needle=normalize(query);results.removeAllViews()
            GeologyCatalog.topics.filter { topic ->
                listOf(topic.title,topic.facts,topic.origin,topic.detail).any { normalize(getString(it)).contains(needle) }
            }.forEach { topic -> results.addView(button(topic.title) { showTopic(topic.id) },full()) }
            EarthPeriods.all.filter { normalize(getString(it.title)).contains(needle) }.forEach { period ->
                results.addView(button(period.title) { showTopic(period.id) },full())
            }
            if(results.childCount==0) results.addView(label(getString(R.string.geo_no_results),14f))
        }
        filter("")
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s:Editable?)=Unit
        })
        showSheet(body)
    }
    private fun about() {
        val body=column()
        body.addView(label(getString(R.string.geo_about),22f,true),full())
        body.addView(label(getString(R.string.geo_about_body),15f),full())
        body.addView(label(getString(R.string.geo_sources),16f,true),full())
        EarthSource.entries.forEach { addSource(body,it) }
        EarthPeriods.all.flatMap { it.sources }.distinctBy { it.url }
            .filter { source -> EarthSource.entries.none { it.url==source.url } }
            .forEach { source -> body.addView(button(source.label) { openUrl(source.url) },full()) }
        showSheet(body)
    }
    private fun showSheet(body:LinearLayout,topic:String?=null) {
        openSheet?.dismiss()
        val dialog=BottomSheetDialog(this)
        body.setPadding(dp(20),dp(12),dp(20),dp(24))
        dialog.setCanceledOnTouchOutside(true)
        val wrapper=ScrollView(this).apply { setBackgroundColor(palette.surface);addView(body) }
        dialog.setContentView(wrapper)
        openSheet=dialog;sheetTopic=topic
        dialog.setOnDismissListener {
            if(openSheet===dialog) { openSheet=null;sheetTopic=null;syncAnimation() }
        }
        dialog.show();dialog.followImmersiveMode()
        dialog.behavior.maxHeight=(resources.displayMetrics.heightPixels*.86f).toInt()
        animator?.cancel()
    }
    private fun syncAnimation() {
        animator?.cancel()
        val target=section ?: return
        if(!resumed || !playing || openSheet!=null || !ValueAnimator.areAnimatorsEnabled()) return
        animator=ValueAnimator.ofFloat(phase,phase+1f).apply {
            duration=12_000;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator()
            addUpdateListener { phase=(it.animatedValue as Float)%1f;target.phase=phase }
            start()
        }
    }
    override fun onResume() { super.onResume();resumed=true;syncAnimation() }
    override fun onPause() {
        resumed=false;animator?.cancel()
        prefs.edit().putString("scene",scene.name).putFloat("stage",stage).putBoolean("labels",labels)
            .putBoolean("scale",realScale).putBoolean("playing",playing).putString("rank",timeRank.name).apply()
        super.onPause()
    }
    override fun onDestroy() { animator?.cancel();openSheet?.dismiss();super.onDestroy() }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("scene",scene.name);outState.putFloat("stage",stage);outState.putBoolean("labels",labels)
        outState.putBoolean("scale",realScale);outState.putBoolean("playing",playing);outState.putString("rank",timeRank.name)
        outState.putString("topic",sheetTopic);outState.putInt("scroll",scroll.scrollY)
        super.onSaveInstanceState(outState)
    }
    private fun label(text:String,size:Float,bold:Boolean=false)=TextView(this).apply {
        this.text=text;textSize=size;setTextColor(palette.text)
        if(bold) typeface=Typeface.create("sans-serif",Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun button(title:Int,selected:Boolean=false,action:()->Unit)=Button(this).apply {
        setText(title);isAllCaps=false;textSize=14f;minHeight=dp(48)
        setTextColor(if(selected) palette.onAccent else palette.text);background=palette.control(selected)
        setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener { action() }
        isSelected=selected
    }
    private fun icon(drawable:Int,title:Int,action:()->Unit)=ImageButton(this).apply {
        setImageResource(drawable);setColorFilter(palette.text);setBackgroundColor(android.graphics.Color.TRANSPARENT)
        contentDescription=getString(title);setOnClickListener { action() }
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun full()=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun normalize(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
}
