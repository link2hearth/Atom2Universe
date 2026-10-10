package com.Atom2Universe.app.science.geology

import android.animation.ValueAnimator
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
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
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.parentes.ParentesActivity
import com.Atom2Universe.app.science.timeline.*
import com.Atom2Universe.app.util.followImmersiveMode
import com.Atom2Universe.app.util.paintSheetFrame
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
    private val savedTopics by lazy { prefs.getStringSet("saved_topics", emptySet()).orEmpty().toMutableSet() }
    private var evolving=false
    private var evolutionPhase=0f
    private var stageSlider:SeekBar?=null
    private var stageCaption:TextView?=null
    private var evolutionButton:Button?=null
    private var playbackButton:ImageButton?=null
    private val chapterScenes=mutableMapOf<EarthChapter,EarthScene>()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        scene=EarthScene.entries.firstOrNull { it.name==(savedInstanceState?.getString("scene") ?: prefs.getString("scene",null)) } ?: EarthScene.GLOBE
        stage=(savedInstanceState?.getFloat("stage") ?: prefs.getFloat("stage",.65f)).coerceIn(0f,1f)
        labels=savedInstanceState?.getBoolean("labels") ?: prefs.getBoolean("labels",true)
        realScale=savedInstanceState?.getBoolean("scale") ?: prefs.getBoolean("scale",true)
        playing=savedInstanceState?.getBoolean("playing") ?: prefs.getBoolean("playing",true)
        evolving=savedInstanceState?.getBoolean("evolving") ?: false
        evolutionPhase=savedInstanceState?.getFloat("evolution_phase") ?: 0f
        phase=savedInstanceState?.getFloat("phase") ?: 0f
        timeRank=GeologicalRank.entries.firstOrNull { it.name==(savedInstanceState?.getString("rank") ?: prefs.getString("rank",null)) } ?: GeologicalRank.EON
        buildUi();render()
        savedInstanceState?.getString("topic")?.let { content.post { showTopic(it) } }
        val y=savedInstanceState?.getInt("scroll") ?: 0
        scroll.post { scroll.scrollTo(0,y) }
    }
    private fun buildUi() {
        val root=column().apply { setBackgroundColor(palette.background) }
        val toolbar=row()
        toolbar.addView(icon(R.drawable.ic_arrow_back_24,R.string.geo_back) { finish() }.apply {
            ScienceNavigation.bindHomeAction(this) {
                openSheet?.dismiss()
                navigate(EarthScene.GLOBE)
                announceForAccessibility(getString(R.string.science_return_to_module_start))
            }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
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
        animator?.cancel();section=null;stageSlider=null;stageCaption=null;evolutionButton=null;playbackButton=null
        content.animate().cancel();content.alpha=1f
        content.removeAllViews();tabs.removeAllViews()
        if(resumed && ValueAnimator.areAnimatorsEnabled()) {
            content.alpha=0f;content.animate().alpha(1f).setDuration(180).start()
        }
        EarthChapter.entries.forEach { chapter ->
            val tab=button(chapter.title,scene.chapter==chapter) {
                if(scene.chapter!=chapter) navigate(chapterScenes[chapter] ?: EarthScene.entries.first { it.chapter==chapter })
            }
            tabs.addView(tab,LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(6) })
            if(scene.chapter==chapter) tab.post { (tabs.parent as? HorizontalScrollView)?.smoothScrollTo((tab.left-dp(20)).coerceAtLeast(0),0) }
        }
        val choices=EarthScene.entries.filter { it.chapter==scene.chapter }
        val heading=row().apply { setPadding(dp(4),dp(4),0,dp(4)) }
        val headingText=column()
        headingText.addView(label(getString(R.string.geo_scene_position,choices.indexOf(scene)+1,choices.size,getString(scene.chapter.title)),12f).apply {
            setTextColor(palette.ink(scene.chapter.color,palette.background))
        })
        headingText.addView(label(getString(scene.title),25f,true))
        heading.addView(headingText,LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(icon(R.drawable.ic_expand_more_24,R.string.geo_choose_scene) { sceneAtlas() },LinearLayout.LayoutParams(dp(48),dp(48)))
        heading.contentDescription=getString(R.string.geo_scene_menu_hint)
        heading.setOnClickListener { sceneAtlas() }
        content.addView(heading,full())
        if(scene==EarthScene.AGES) {
            content.addView(button(R.string.geo_time_help) { showTimeHelp() },full())
            renderTime();return
        }
        section=EarthSectionView(this,scene,::showTopic).also {
            it.stage=stage;it.phase=phase;it.labels=labels;it.trueScale=realScale
            content.addView(it,full())
        }
        val controls=row()
        val pause=icon(if(playing) R.drawable.ic_pause else R.drawable.ic_play,
            if(playing) R.string.geo_pause else R.string.geo_play) {}
        playbackButton=pause
        pause.setOnClickListener {
            playing=!playing
            pause.setImageResource(if(playing) R.drawable.ic_pause else R.drawable.ic_play)
            pause.contentDescription=getString(if(playing) R.string.geo_pause else R.string.geo_play)
            syncAnimation()
        }
        controls.addView(pause,LinearLayout.LayoutParams(dp(48),dp(48)))
        val diagramNote=label(getString(R.string.geo_diagram_note),12f).apply { setTextColor(palette.secondary) }
        controls.addView(diagramNote,LinearLayout.LayoutParams(0,-2,1f))
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
                        true
                    }
                }
                if(scene.lesson.steps!=0) menu.add(R.string.geo_restart).setOnMenuItemClickListener {
                    stopEvolution();stage=0f;section?.stage=stage;stageSlider?.progress=0;updateStageCaption();true
                }
            }.show()
        }
        controls.addView(settings,LinearLayout.LayoutParams(dp(48),dp(48)))
        content.addView(controls,full())
        if(scene.lesson.steps!=0) {
            val progressRow=row()
            stageCaption=label("",12f).apply { setTextColor(palette.secondary) }
            progressRow.addView(stageCaption,LinearLayout.LayoutParams(0,-2,1f))
            evolutionButton=button(R.string.geo_evolution,evolving) {
                evolving=!evolving
                evolutionPhase=(kotlin.math.acos(1.0-2.0*stage)/(2*Math.PI)).toFloat()
                if(evolving) playing=true
                updateEvolutionButton()
                syncAnimation()
            }.apply { textSize=12f }
            progressRow.addView(evolutionButton,LinearLayout.LayoutParams(-2,-2))
            content.addView(progressRow,full())
            stageSlider=SeekBar(this).apply {
                max=100;progress=(stage*100).toInt();contentDescription=getString(R.string.geo_step_label)
                progressTintList=ColorStateList.valueOf(palette.ink(scene.chapter.color))
                thumbTintList=progressTintList
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar:SeekBar?) { stopEvolution() }
                    override fun onStopTrackingTouch(seekBar:SeekBar?)=Unit
                    override fun onProgressChanged(seekBar:SeekBar?,progress:Int,fromUser:Boolean) {
                        if(fromUser) { stopEvolution();stage=progress/100f;section?.stage=stage;updateStageCaption() }
                    }
                })
            }
            content.addView(stageSlider,full())
            updateStageCaption()
        }
        val navigation=row()
        val scenes=EarthScene.entries
        navigation.addView(icon(R.drawable.ic_chevron_left,R.string.geo_previous) {
            navigate(scenes[scene.ordinal-1])
        }.apply { isEnabled=scene.ordinal>0;alpha=if(isEnabled) 1f else .25f },LinearLayout.LayoutParams(dp(48),dp(48)))
        navigation.addView(button(R.string.geo_understand) { showLesson() },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(4);marginEnd=dp(4) })
        navigation.addView(icon(R.drawable.ic_chevron_right,R.string.geo_next) {
            navigate(scenes[scene.ordinal+1])
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        content.addView(navigation,full())
        syncAnimation()
    }
    private fun navigate(target:EarthScene) {
        chapterScenes[scene.chapter]=scene
        evolving=false;scene=target;stage=.65f;render();scroll.scrollTo(0,0)
    }
    private fun stopEvolution() {
        if(!evolving) return
        evolving=false;updateEvolutionButton()
    }
    private fun updateEvolutionButton() {
        evolutionButton?.apply {
            isSelected=evolving;background=controlBackground(evolving)
            setTextColor(if(evolving) palette.onAccent else palette.text)
        }
    }
    private fun updateStageCaption() {
        if(scene.lesson.steps==0) return
        val steps=resources.getStringArray(scene.lesson.steps)
        stageCaption?.text=getString(R.string.geo_stage_caption,steps[(stage*3).toInt().coerceAtMost(2)],(stage*100).toInt())
    }
    private fun sceneAtlas() {
        val body=column()
        body.addView(label(getString(R.string.geo_atlas),24f,true),full())
        EarthChapter.entries.forEach { chapter ->
            body.addView(label(getString(chapter.title),14f,true).apply { setTextColor(palette.ink(chapter.color)) },full())
            EarthScene.entries.filter { it.chapter==chapter }.forEach { target ->
                val card=row().apply {
                    background=palette.shape(if(target==scene) ColorUtils.blendARGB(palette.raised,chapter.color,.18f) else palette.raised)
                    setPadding(dp(8),dp(8),dp(12),dp(8));minimumHeight=dp(80)
                    setOnClickListener { openSheet?.dismiss();navigate(target) }
                    isFocusable=true;contentDescription=getString(target.title);isSelected=target==scene
                }
                card.addView(EarthPreviewView(this,target),LinearLayout.LayoutParams(dp(94),dp(68)))
                card.addView(label(getString(target.title),16f,true),LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(14) })
                body.addView(card,full())
            }
        }
        showSheet(body)
    }
    private fun showLesson() {
        val lesson=scene.lesson
        val body=column()
        body.addView(label(getString(scene.title),24f,true),full())
        body.addView(label(getString(lesson.summary),16f),full())
        body.addView(infoCard(R.string.geo_observe,lesson.observation),full())
        val question=infoCard(R.string.geo_wonder,lesson.question)
        val answer=label(getString(lesson.answer),16f).apply { visibility=View.GONE }
        val reveal=button(R.string.geo_reveal) {}
        reveal.setOnClickListener {
            val show=answer.visibility!=View.VISIBLE
            answer.visibility=if(show) View.VISIBLE else View.GONE
            reveal.setText(if(show) R.string.geo_hide_answer else R.string.geo_reveal)
        }
        question.addView(reveal,full());question.addView(answer,full())
        body.addView(question,full())
        body.addView(label(getString(R.string.geo_topics),16f,true),full())
        scene.ids.forEach { id -> GeologyCatalog.get(id)?.let { topic ->
            body.addView(button(topic.title) { showTopic(id) },full())
        } }
        body.addView(label(getString(when {
            scene!=EarthScene.GLOBE -> R.string.geo_scale_local_note
            realScale -> R.string.geo_scale_real_note
            else -> R.string.geo_scale_expanded_note
        }),12f).apply { setTextColor(palette.secondary) },full())
        showSheet(body)
    }
    private fun showTimeHelp() {
        val body=column()
        body.addView(label(getString(R.string.geo_time_help),24f,true),full())
        body.addView(label(getString(R.string.geo_time_intro),16f),full())
        body.addView(infoCard(R.string.geo_observe,R.string.geo_date_key),full())
        body.addView(label(getString(R.string.geo_period_note),16f),full())
        showSheet(body)
    }
    private fun infoCard(title:Int,body:Int)=column().apply {
        background=palette.shape(ColorUtils.blendARGB(palette.surface,scene.chapter.color,.07f))
        setPadding(dp(16),dp(12),dp(16),dp(16))
        addView(label(getString(title),13f,true).apply { setTextColor(palette.ink(scene.chapter.color)) })
        addView(label(getString(body),16f),full())
    }
    private fun renderTime() {
        val ranks=row()
        GeologicalRank.entries.forEach { rank -> ranks.addView(button(rank.label,rank==timeRank) { timeRank=rank;render() },LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(4) }) }
        content.addView(ranks,full())
        content.addView(label(getString(R.string.geo_young),12f,true),full())
        // Equal-height bands for browsing, explicitly not proportional to duration or rock thickness.
        EarthPeriods.all.filter { it.geology?.rank==timeRank }.sortedBy { it.geology!!.olderMa }.forEach { period ->
            val entry=row().apply { background=palette.shape(palette.raised);setPadding(dp(8),dp(4),dp(8),dp(4)) }
            entry.addView(View(this).apply { setBackgroundColor(period.color) },LinearLayout.LayoutParams(dp(5),dp(52)))
            val description=column().apply { setPadding(dp(12),dp(8),dp(8),dp(8)) }
            description.addView(label(getString(period.title),17f,true))
            description.addView(label(period.dateLabel(this),13f).apply { setTextColor(palette.secondary) })
            entry.addView(description,LinearLayout.LayoutParams(0,-2,1f))
            entry.isFocusable=true
            entry.contentDescription=getString(R.string.geo_time_range,getString(period.title),period.dateLabel(this))
            entry.setOnClickListener { showTopic(period.id) }
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
        val save=button(if(id in savedTopics) R.string.geo_saved else R.string.geo_save,id in savedTopics) {}
        save.setOnClickListener {
            if(!savedTopics.add(id)) savedTopics.remove(id)
            val selected=id in savedTopics
            save.setText(if(selected) R.string.geo_saved else R.string.geo_save)
            save.background=controlBackground(selected);save.setTextColor(if(selected) palette.onAccent else palette.text)
            save.isSelected=selected
            prefs.edit().putStringSet("saved_topics",savedTopics.toSet()).apply()
        }
        body.addView(save,full())
        if(topic!=null) {
            body.addView(infoCard(R.string.geo_observe,topic.facts),full())
            body.addView(label(getString(R.string.geo_origin),15f,true),full())
            body.addView(label(getString(topic.origin),15f),full())
            body.addView(label(getString(R.string.geo_detail),15f,true),full())
            body.addView(label(getString(topic.detail),15f),full())
            GeologyCatalog.scene(id)?.let { target ->
                body.addView(infoCard(R.string.geo_understand,target.lesson.summary),full())
            }
            topic.periodId?.let { addTimeline(body,it) }
            topic.lifeId?.let { addLife(body,it) }
            GeologyCatalog.scene(id)?.takeIf { it!=scene }?.let { target ->
                body.addView(button(R.string.geo_related) { openSheet?.dismiss();navigate(target) },full())
            }
            body.addView(label(getString(R.string.geo_sources),14f,true),full())
            addSource(body,topic.source)
        } else if(period!=null) {
            body.addView(label(period.dateLabel(this),15f,true),full())
            body.addView(label(getString(period.description),15f),full())
            body.addView(label(getString(R.string.geo_period_note),14f),full())
            body.addView(label(getString(R.string.geo_period_precision),14f,true),full())
            body.addView(label(period.geology!!.precision(this),14f),full())
            addTimeline(body,id)
            GeologyCatalog.lifeByPeriod[id]?.let { addLife(body,it) }
            period.sources.forEach { source -> body.addView(button(source.label) { openUrl(source.url) },full()) }
        }
        showSheet(body,id)
    }
    private fun addTimeline(body:LinearLayout,id:String) {
        val chapter=TimelineChapters.get(id) ?: return
        body.addView(button(R.string.geo_timeline_link) {
            startActivity(Intent(this,CosmicTimelineActivity::class.java)
                .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                .putExtra(CosmicTimelineActivity.EXTRA_CHAPTER_ID,id))
        }.apply { text=getString(R.string.geo_timeline_link,getString(chapter.title)) },full())
    }
    private fun addLife(body:LinearLayout,id:String) {
        val date=LifeTimeline.get(id) ?: return
        body.addView(button(R.string.geo_tree_link) {
            startActivity(Intent(this,ParentesActivity::class.java)
                .putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
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
        var savedOnly=false
        val filters=row()
        val all=button(R.string.geo_all_entries,true) {}
        val saved=button(R.string.geo_saved_entries) {}
        filters.addView(all,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8) })
        filters.addView(saved,LinearLayout.LayoutParams(0,-2,1f))
        body.addView(filters,full())
        val count=label("",12f).apply { setTextColor(palette.secondary) };body.addView(count,full())
        val results=column();body.addView(results,full())
        fun filter(query:String) {
            val needle=normalize(query);results.removeAllViews()
            GeologyCatalog.topics.filter { topic ->
                (!savedOnly || topic.id in savedTopics) && listOf(topic.title,topic.facts,topic.origin,topic.detail).any { normalize(getString(it)).contains(needle) }
            }.forEach { topic -> results.addView(button(topic.title) { showTopic(topic.id) }.apply { gravity=Gravity.START or Gravity.CENTER_VERTICAL },full()) }
            EarthPeriods.all.filter { (!savedOnly || it.id in savedTopics) && normalize(getString(it.title)).contains(needle) }.forEach { period ->
                results.addView(button(period.title) { showTopic(period.id) },full())
            }
            count.text=getString(R.string.geo_results_count,results.childCount)
            if(results.childCount==0) results.addView(label(getString(if(savedOnly && needle.isEmpty()) R.string.geo_saved_empty else R.string.geo_no_results),14f))
        }
        fun selectFilter(value:Boolean) {
            savedOnly=value
            listOf(all to !value,saved to value).forEach { (view,selected) ->
                view.background=controlBackground(selected);view.setTextColor(if(selected) palette.onAccent else palette.text);view.isSelected=selected
            }
            filter(search.text.toString())
        }
        all.setOnClickListener { selectFilter(false) };saved.setOnClickListener { selectFilter(true) }
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
        body.addView(label(getString(R.string.science_navigation_help),15f),full())
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
        val container=column().apply { setBackgroundColor(palette.surface) }
        val close=row().apply { gravity=Gravity.END;setPadding(dp(12),0,dp(12),0) }
        close.addView(button(R.string.geo_close) { dialog.dismiss() })
        container.addView(close)
        container.addView(wrapper,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(container)
        dialog.paintSheetFrame(palette.surface)
        openSheet=dialog;sheetTopic=topic
        dialog.setOnDismissListener {
            if(openSheet===dialog) { openSheet=null;sheetTopic=null;syncAnimation() }
        }
        dialog.show();dialog.followImmersiveMode()
        dialog.behavior.maxHeight=(resources.displayMetrics.heightPixels*.90f).toInt()
        container.layoutParams=container.layoutParams.apply { height=dialog.behavior.maxHeight }
        dialog.behavior.state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        animator?.cancel()
    }
    private fun syncAnimation() {
        animator?.cancel()
        playbackButton?.setImageResource(if(playing) R.drawable.ic_pause else R.drawable.ic_play)
        playbackButton?.contentDescription=getString(if(playing) R.string.geo_pause else R.string.geo_play)
        val target=section ?: return
        if(!resumed || !playing || openSheet!=null || !ValueAnimator.areAnimatorsEnabled()) return
        var previous=0f
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=18_000;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator()
            addUpdateListener {
                val value=it.animatedValue as Float
                val delta=if(value>=previous) value-previous else 1f-previous+value
                previous=value
                if(playing) { phase=(phase+delta)%1f;target.phase=phase }
                if(evolving) {
                    evolutionPhase=(evolutionPhase+delta)%1f
                    stage=((1-kotlin.math.cos(evolutionPhase*2*Math.PI))/2).toFloat()
                    target.stage=stage;stageSlider?.progress=(stage*100).toInt();updateStageCaption()
                }
            }
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
        outState.putBoolean("evolving",evolving);outState.putFloat("evolution_phase",evolutionPhase);outState.putFloat("phase",phase)
        super.onSaveInstanceState(outState)
    }
    private fun label(text:String,size:Float,bold:Boolean=false)=TextView(this).apply {
        this.text=text;textSize=size;setTextColor(palette.text)
        if(bold) typeface=Typeface.create("sans-serif",Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun button(title:Int,selected:Boolean=false,action:()->Unit)=Button(this).apply {
        setText(title);isAllCaps=false;textSize=14f;minHeight=dp(48)
        setTextColor(if(selected) palette.onAccent else palette.text);background=controlBackground(selected)
        setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener { action() }
        isSelected=selected
    }
    private fun icon(drawable:Int,title:Int,action:()->Unit)=ImageButton(this).apply {
        setImageResource(drawable);setColorFilter(palette.text)
        background=RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent,55)),null,palette.shape(android.graphics.Color.WHITE,24f))
        contentDescription=getString(title);setOnClickListener { action() }
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun controlBackground(selected:Boolean)=RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent,55)),palette.control(selected),null)
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun full()=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun normalize(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
}
