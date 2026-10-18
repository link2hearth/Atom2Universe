package com.Atom2Universe.app.games.billiards

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.ui.BilliardMenuLayout

/** Game modes come first; a saved table is always an optional, separate action. */
internal class BilliardLounge(
    private val context: Context, private val progress: BilliardProgress,
    private val canResume: Boolean, private val resume: ()->Unit, private val quick: ()->Unit,
    private val start: (BilliardRun,Discipline,BilliardRoom,Int)->Unit,
    private val preview: (BilliardPreviewSpec)->Unit, viewport: (Rect)->Unit,
    private val preferences: ()->Unit, private val appearance: ()->BilliardConfig,
    private val selectFinish: (Int)->Unit, private val exit: ()->Unit
) {
    private val cream=0xFFF3EBDD.toInt()
    private val muted=0xFFAABFB8.toInt()
    private val gold=0xFFE4C488.toInt()
    val root=BilliardMenuLayout(context,viewport)
    private lateinit var panel: LinearLayout
    private lateinit var launchBar: LinearLayout
    private var selectedCard: View?=null
    private var page=0
    private var discipline=Discipline.EIGHT
    private var race=2
    private var difficulty=1
    private fun dp(n: Int)=(n*context.resources.displayMetrics.density+.5f).toInt()
    private fun string(id: Int,vararg args: Any)=context.getString(id,*args)
    private fun label(value: String,size: Float,color: Int=cream)=TextView(context).apply {
        text=value; textSize=size; setTextColor(color); includeFontPadding=false
        setPadding(0,dp(4),0,dp(4)); setShadowLayer(dp(2).toFloat(),0f,dp(1).toFloat(),0xB0000000.toInt())
    }
    private fun column()=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    private fun background(color: Int,radius: Int=20)=GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat(); setStroke(dp(1),0x507AAE9B)
    }
    private fun interactive(view: View,title: String,detail: String="",action: ()->Unit) {
        view.isClickable=true; view.isFocusable=true
        view.contentDescription=if(detail.isBlank()) title else string(R.string.billiard_lounge_accessibility,title,detail)
        view.foreground=RippleDrawable(ColorStateList.valueOf(0x30E4C488),null,background(0xFFFFFFFF.toInt()))
        view.setOnClickListener { action() }
        view.accessibilityDelegate=object: View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View,info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info); info.className=Button::class.java.name
            }
        }
    }
    private fun decorative(view: View) { view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    private fun card(parent: LinearLayout,title: String,detail: String="",enabled: Boolean=true,action: ()->Unit): LinearLayout {
        val card=LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(16),dp(13),dp(14),dp(13)); background=background(0x64142C2C)
            minimumHeight=dp(72)
        }
        val copy=column().apply {
            addView(label(title,17f).apply { typeface=Typeface.create("sans-serif-medium",0) })
            if(detail.isNotBlank()) addView(label(detail,13f,muted).apply { setLineSpacing(dp(2).toFloat(),1f) })
        }
        decorative(copy); card.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        card.addView(glyph(if(enabled) Glyph.ARROW else Glyph.LOCK),LinearLayout.LayoutParams(dp(32),dp(32)).apply { marginStart=dp(8) })
        interactive(card,title,detail,action); card.isEnabled=enabled; card.alpha=if(enabled) 1f else .48f
        parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        return card
    }
    private fun page(title: Int,description: String=""): LinearLayout {
        selectedCard=null
        val heading=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; isClickable=true }
        heading.addView(iconButton(Glyph.BACK,R.string.billiard_back) { back() },LinearLayout.LayoutParams(dp(48),dp(48)))
        heading.addView(label(string(title),22f).apply { typeface=Typeface.create("sans-serif-medium",0) },
            LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(10); marginEnd=dp(6) })
        if(page==0) heading.addView(iconButton(Glyph.SETTINGS,R.string.billiard_preferences,preferences),LinearLayout.LayoutParams(dp(48),dp(48)))
        val peek=label(string(R.string.billiard_menu_view_table),12f,gold).apply {
            gravity=Gravity.CENTER; minHeight=dp(48); setPadding(dp(8),dp(4),dp(8),dp(4)); background=background(0x70172D2B,16)
        }
        interactive(peek,string(R.string.billiard_menu_view_table)) { root.toggle() }
        heading.addView(peek,LinearLayout.LayoutParams(dp(82),-2).apply { marginStart=dp(6) })
        root.visibilityChanged={ visible ->
            peek.text=string(if(visible) R.string.billiard_menu_view_table else R.string.billiard_menu_show)
            peek.contentDescription=peek.text
        }
        panel=column().apply {
            background=background(0x80091C1B.toInt(),24); setPadding(dp(8),dp(8),dp(8),dp(8)); isClickable=true
        }
        val list=column().apply { setPadding(dp(6),dp(6),dp(6),dp(12)) }
        panel.addView(ScrollView(context).apply {
            isFillViewport=true; isVerticalScrollBarEnabled=true; isScrollbarFadingEnabled=false; addView(list)
        },LinearLayout.LayoutParams(-1,0,1f))
        launchBar=column().apply { visibility=View.GONE; setPadding(dp(6),dp(4),dp(6),0) }
        panel.addView(launchBar,LinearLayout.LayoutParams(-1,-2))
        root.content(heading,panel)
        if(description.isNotBlank()) list.addView(label(description,13f,muted).apply { setPadding(0,0,0,dp(12)); setLineSpacing(dp(2).toFloat(),1f) })
        return list
    }
    private fun selectEvent(title: String,event: BilliardRun,d: Discipline,room: BilliardRoom,difficulty: Int) {
        preview(BilliardPreviewSpec(d,room,event.exercise,event.solo && event.exercise<0))
        launchBar.removeAllViews(); launchBar.visibility=View.VISIBLE
        val play=label(string(R.string.billiard_menu_play_selection,title),15f,gold).apply {
            background=background(0xC0444930.toInt(),16); gravity=Gravity.CENTER; minHeight=dp(48); setPadding(dp(12),dp(10),dp(12),dp(10))
        }
        interactive(play,play.text.toString()) { start(event,d,room,difficulty) }
        launchBar.addView(play,LinearLayout.LayoutParams(-1,-2))
    }
    private fun eventCard(parent: LinearLayout,title: String,detail: String,event: BilliardRun,d: Discipline,
        room: BilliardRoom,difficulty: Int,enabled: Boolean=true) {
        lateinit var entry: View
        entry=card(parent,title,detail,enabled) {
            selectedCard?.background=background(0x64142C2C)
            entry.background=background(0xA8444930.toInt()); selectedCard=entry
            selectEvent(title,event,d,room,difficulty)
        }
    }
    fun back() { if(!root.panelVisible) root.setPanelVisible(true) else if(page==0) exit() else home() }
    fun open(event: BilliardRun?,config: BilliardConfig) {
        when(event?.mode) {
            BilliardActivityMode.CHALLENGE,BilliardActivityMode.LESSON -> {
                val exercise=event.exercise.coerceIn(BilliardChallenges.all.indices)
                challenges(event.mode==BilliardActivityMode.LESSON,exercise/4,exercise%4)
            }
            BilliardActivityMode.CAREER -> career()
            BilliardActivityMode.CUP,BilliardActivityMode.LEAGUE -> {
                discipline=config.discipline; race=event.raceTo; difficulty=config.difficulty
                competitions()
            }
            BilliardActivityMode.SERIES,BilliardActivityMode.TIMED -> solo()
            else -> home()
        }
    }
    fun home() {
        page=0
        val list=page(R.string.billiard_title)
        preview(BilliardPreviewSpec())
        // Choices start immediately, including on small screens and with enlarged text.
        val grid=ModeGrid(context)
        fun mode(title: Int,detail: Int,action: ()->Unit) { grid.addView(modeTile(title,detail,action)) }
        mode(R.string.billiard_career,R.string.billiard_lounge_career) { career() }
        mode(R.string.billiard_quick,R.string.billiard_lounge_quick,quick)
        mode(R.string.billiard_competitions,R.string.billiard_lounge_competitions) { competitions() }
        mode(R.string.billiard_challenges,R.string.billiard_lounge_challenges) { challenges(false) }
        mode(R.string.billiard_solo_modes,R.string.billiard_lounge_solo) { solo() }
        mode(R.string.billiard_school,R.string.billiard_lounge_school) { challenges(true) }
        list.addView(grid,LinearLayout.LayoutParams(-1,-2))
        val progressCard=column().apply {
            setPadding(dp(16),dp(12),dp(12),dp(8)); background=background(0x70142925,16)
        }
        val cabinet=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        val summary=column().apply {
            addView(label(string(R.string.billiard_lounge_your_club),12f,gold))
            addView(label(string(R.string.billiard_home_summary,progress.clubCount,progress.medalCount),14f))
        }
        decorative(summary); cabinet.addView(summary,LinearLayout.LayoutParams(0,-2,1f))
        cabinet.addView(glyph(Glyph.ARROW),LinearLayout.LayoutParams(dp(32),dp(32)).apply { marginStart=dp(12) })
        interactive(cabinet,string(R.string.billiard_records),string(R.string.billiard_home_summary,progress.clubCount,progress.medalCount)) { records() }
        progressCard.addView(cabinet,LinearLayout.LayoutParams(-1,-2))
        progressCard.addView(finishes(),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
        list.addView(progressCard,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
        if(canResume) {
            // This row does not take the place of the mode grid, even for endless free practice.
            val saved=LinearLayout(context).apply {
                gravity=Gravity.CENTER_VERTICAL; background=background(0x70172D2B,16)
                minimumHeight=dp(56); setPadding(dp(16),dp(8),dp(12),dp(8))
            }
            val caption=label(string(R.string.billiard_resume),14f,muted)
            decorative(caption); saved.addView(caption,LinearLayout.LayoutParams(0,-2,1f))
            saved.addView(glyph(Glyph.ARROW),LinearLayout.LayoutParams(dp(32),dp(32)))
            interactive(saved,string(R.string.billiard_resume),string(R.string.billiard_resume_hint),resume)
            panel.addView(saved,LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(6),dp(6),dp(6),0) })
        }
    }
    private fun modeTile(title: Int,detail: Int,action: ()->Unit): View {
        val tile=column().apply { background=background(0x70172E2A); clipToOutline=true }
        val copy=column().apply { setPadding(dp(12),dp(10),dp(12),dp(12)) }
        copy.addView(label(string(title),16f).apply { typeface=Typeface.create("sans-serif-medium",0) })
        copy.addView(label(string(detail),12f,muted).apply { setLineSpacing(dp(2).toFloat(),1f) })
        decorative(copy); tile.addView(copy)
        interactive(tile,string(title),string(detail),action)
        return tile
    }
    /**
     * The cue finishes, one per club won, chosen right on the achievements card. They replace
     * the old club track: a finish still to win is faded and does nothing.
     */
    private fun finishes(): View {
        val box=column()
        box.addView(label(string(R.string.billiard_cue_finish),12f,muted).apply { decorative(this) })
        val row=LinearLayout(context)
        val current=appearance()
        val swatches=mutableListOf<View>()
        for(finish in 0..rooms.size) {
            val name=if(finish==0) string(R.string.billiard_finish_natural) else string(R.string.billiard_finish_club,finish)
            val view=swatch(current.copy(finish=finish).cueTint).apply { isSelected=finish==current.finish }
            if(finish<=progress.clubCount) interactive(view,string(R.string.billiard_selection,string(R.string.billiard_cue_finish),name)) {
                selectFinish(finish); swatches.forEachIndexed { i,other -> other.isSelected=i==finish }
            } else view.apply { alpha=.3f; isEnabled=false; contentDescription=name }
            swatches+=view; row.addView(view,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        box.addView(row,LinearLayout.LayoutParams(-1,-2))
        return box
    }
    private fun swatch(tint: Int)=object: View(context) {
        private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF000000.toInt() or tint }
        private val ring=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=gold; style=Paint.Style.STROKE; strokeWidth=dp(2).toFloat() }
        override fun onDraw(c: Canvas) {
            val r=dp(15).toFloat()
            c.drawCircle(width/2f,height/2f,r-dp(4),fill)
            if(isSelected) c.drawCircle(width/2f,height/2f,r,ring)
        }
    }
    private enum class Glyph { BACK, SETTINGS, ARROW, LOCK }
    private fun glyph(kind: Glyph)=object: View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=gold; style=Paint.Style.STROKE; strokeWidth=1.6f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
        init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(c: Canvas) {
            c.save(); c.translate(width/2f,height/2f); val scale=dp(24)/24f; c.scale(scale,scale)
            when(kind) {
                Glyph.BACK -> { c.drawLine(4f,-7f,-3f,0f,paint); c.drawLine(-3f,0f,4f,7f,paint) }
                Glyph.ARROW -> { c.drawLine(-3f,-5f,2f,0f,paint); c.drawLine(2f,0f,-3f,5f,paint) }
                Glyph.SETTINGS -> {
                    for((x,y) in listOf(-6f to -3f,0f to 4f,6f to -5f)) {
                        c.drawLine(x,-9f,x,y-2,paint); c.drawLine(x,y+2,x,9f,paint); c.drawCircle(x,y,2f,paint)
                    }
                }
                Glyph.LOCK -> { c.drawRoundRect(-6f,-1f,6f,8f,2f,2f,paint); c.drawArc(-4f,-9f,4f,4f,180f,180f,false,paint) }
            }
            c.restore()
        }
    }
    private fun iconButton(kind: Glyph,title: Int,action: ()->Unit)=FrameLayout(context).apply {
        background=background(0x70172D2B,16); addView(glyph(kind),FrameLayout.LayoutParams(-1,-1))
        interactive(this,string(title),action=action)
    }
    /** Measure against actual available width, including cutouts and enlarged type. */
    private class ModeGrid(context: Context): ViewGroup(context) {
        private val gap=(12*resources.displayMetrics.density).toInt()
        private var columns=1
        private var cellWidth=0
        private var rows=IntArray(0)
        override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
            val width=MeasureSpec.getSize(widthMeasureSpec)
            val minimum=132*resources.displayMetrics.density*resources.configuration.fontScale.coerceAtLeast(1f)
            columns=((width+gap)/(minimum+gap)).toInt().coerceIn(1,3)
            cellWidth=((width-gap*(columns-1))/columns).coerceAtLeast(0)
            rows=IntArray((childCount+columns-1)/columns)
            for(i in 0 until childCount) {
                val child=getChildAt(i)
                child.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
                rows[i/columns]=maxOf(rows[i/columns],child.measuredHeight)
            }
            for(i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rows[i/columns],MeasureSpec.EXACTLY))
            setMeasuredDimension(width,resolveSize(rows.sum()+gap*(rows.size-1).coerceAtLeast(0),heightMeasureSpec))
        }
        override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
            var y=0
            for(i in 0 until childCount) {
                val logical=i%columns
                val col=if(layoutDirection==LAYOUT_DIRECTION_RTL) columns-1-logical else logical
                val x=col*(cellWidth+gap)
                getChildAt(i).layout(x,y,x+cellWidth,y+rows[i/columns])
                if(logical==columns-1) y+=rows[i/columns]+gap
            }
        }
        override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
    }
    private val rooms=listOf(BilliardRoom.HOME,BilliardRoom.CLUB,BilliardRoom.LOFT,BilliardRoom.GARDEN,BilliardRoom.NEON)
    private fun career() {
        page=1
        val list=page(R.string.billiard_career,string(R.string.billiard_career_progress,progress.careerStep,20))
        preview(BilliardPreviewSpec(room=rooms[(progress.careerStep/4).coerceAtMost(4)]))
        rooms.forEachIndexed { club,room ->
            val steps=column().apply { visibility=if(progress.careerStep/4==club) View.VISIBLE else View.GONE }
            card(list,string(R.string.billiard_club_name,club+1,string(room.title)),
                string(if(progress.careerStep>=(club+1)*4) R.string.billiard_club_complete else R.string.billiard_club_detail)) {
                steps.visibility=if(steps.visibility==View.VISIBLE) View.GONE else View.VISIBLE
                preview(BilliardPreviewSpec(room=room)); launchBar.visibility=View.GONE
                selectedCard?.background=background(0x64142C2C); selectedCard=null
            }
            list.addView(steps)
            repeat(4) { event ->
                val step=club*4+event
                val exercise=when(event) { 0 -> listOf(0,1,4,12,16)[club]; 2 -> listOf(1,8,5,13,17)[club]; else -> -1 }
                val d=listOf(Discipline.EIGHT,Discipline.NINE,Discipline.BLACKBALL,Discipline.NINE,Discipline.EIGHT)[club]
                val title=string(when(event) { 0 -> R.string.billiard_career_warmup; 1 -> R.string.billiard_career_duel; 2 -> R.string.billiard_career_skill; else -> R.string.billiard_career_final })
                val detail=if(exercise>=0) exerciseDescription(BilliardChallenges.all[exercise]) else string(R.string.billiard_match_settings,string(d.titleRes()),if(event==3) 2 else 1,string(difficultyName(minOf(2,club/2))))
                eventCard(steps,string(R.string.billiard_event_title,step+1,title),detail,
                    BilliardRun(mode=BilliardActivityMode.CAREER,raceTo=if(event==3) 2 else 1,careerStep=step,exercise=exercise),
                    if(exercise>=0) BilliardChallenges.all[exercise].discipline else d,room,minOf(2,club/2),step<=progress.careerStep)
            }
        }
    }
    private fun difficultyName(value: Int)=listOf(R.string.billiard_easy,R.string.billiard_medium,R.string.billiard_hard)[value]
    private fun competitions(expanded: Boolean=false) {
        page=2
        val list=page(R.string.billiard_competitions)
        preview(BilliardPreviewSpec(discipline,BilliardRoom.CLUB))
        val options=column().apply { visibility=if(expanded) View.VISIBLE else View.GONE }
        card(list,string(R.string.billiard_match_settings,string(discipline.titleRes()),race,string(difficultyName(difficulty))),string(R.string.billiard_expand)) {
            options.visibility=if(options.visibility==View.VISIBLE) View.GONE else View.VISIBLE
        }
        list.addView(options)
        listOf(Discipline.EIGHT,Discipline.NINE,Discipline.BLACKBALL,Discipline.SIX_RED,Discipline.THREE_CUSHION).forEach { d ->
            card(options,string(d.titleRes())) { discipline=d; competitions(true) }
        }
        card(options,string(R.string.billiard_race_to,race)) { race=race%3+1; competitions(true) }
        card(options,string(difficultyName(difficulty))) { difficulty=(difficulty+1)%3; competitions(true) }
        eventCard(list,string(R.string.billiard_cup),string(R.string.billiard_cup_hint),
            BilliardRun(mode=BilliardActivityMode.CUP,raceTo=race),discipline,BilliardRoom.CLUB,difficulty)
        eventCard(list,string(R.string.billiard_league),string(R.string.billiard_league_hint),
            BilliardRun(mode=BilliardActivityMode.LEAGUE,raceTo=race),discipline,BilliardRoom.LOFT,difficulty)
        val rules=label(string(R.string.billiard_competition_rules),13f,muted).apply { visibility=View.GONE }
        card(list,string(R.string.billiard_rules)) { rules.visibility=if(rules.visibility==View.VISIBLE) View.GONE else View.VISIBLE }
        list.addView(rules)
    }
    private fun challenges(school: Boolean,group: Int=0,level: Int=0) {
        page=if(school) 4 else 3
        val list=page(if(school) R.string.billiard_school else R.string.billiard_challenges)
        val goals=LinearLayout(context)
        var active: View?=null
        BilliardGoal.entries.forEachIndexed { index,goal ->
            val chip=label(string(goalName(goal)),13f,if(index==group) gold else cream).apply {
                background=background(if(index==group) 0xA8444930.toInt() else 0x64142C2C,14)
                minHeight=dp(48); gravity=Gravity.CENTER; setPadding(dp(12),dp(8),dp(12),dp(8))
            }
            interactive(chip,chip.text.toString()) { challenges(school,index,0) }
            chip.isSelected=index==group
            if(index==group) active=chip
            goals.addView(chip,LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(6) })
        }
        list.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled=true; addView(goals)
            post { active?.let { scrollTo(it.left,0) } }
        })
        val e=BilliardChallenges.all[group*4+if(school) 0 else level]
        if(!school) {
            val levels=LinearLayout(context)
            repeat(4) { index ->
                val chip=label(string(R.string.billiard_menu_level,index+1),15f,if(index==level) gold else cream).apply {
                    gravity=Gravity.CENTER; minHeight=dp(48); background=background(if(index==level) 0xA8444930.toInt() else 0x64142C2C,14)
                }
                interactive(chip,string(R.string.billiard_exercise_number,group*4+index+1)) { challenges(false,group,index) }
                chip.isSelected=index==level
                levels.addView(chip,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(10),dp(3),dp(8)) })
            }
            list.addView(levels)
        }
        list.addView(label(exerciseDescription(e),16f))
        list.addView(label(if(school) string(if(progress.lesson(e.id)) R.string.billiard_lesson_done else R.string.billiard_lesson_try)
            else string(R.string.billiard_medal_count,progress.medal(e.id)),13f,gold))
        val advice=label(string(if(school) lessonText(e.goal) else R.string.billiard_medals_help),13f,muted).apply { visibility=if(school) View.VISIBLE else View.GONE }
        if(!school) card(list,string(R.string.billiard_rules)) { advice.visibility=if(advice.visibility==View.VISIBLE) View.GONE else View.VISIBLE }
        list.addView(advice)
        selectEvent(string(R.string.billiard_exercise_number,e.id+1),
            BilliardRun(mode=if(school) BilliardActivityMode.LESSON else BilliardActivityMode.CHALLENGE,exercise=e.id),e.discipline,BilliardRoom.HOME,0)
    }
    private fun solo() {
        page=5
        val list=page(R.string.billiard_solo_modes,string(R.string.billiard_solo_modes_hint))
        preview(BilliardPreviewSpec(Discipline.NINE,BilliardRoom.GARDEN,spread=true))
        eventCard(list,string(R.string.billiard_series),string(R.string.billiard_series_hint),
            BilliardRun(mode=BilliardActivityMode.SERIES),Discipline.NINE,BilliardRoom.GARDEN,0)
        eventCard(list,string(R.string.billiard_timed),string(R.string.billiard_timed_hint),
            BilliardRun(mode=BilliardActivityMode.TIMED),Discipline.NINE,BilliardRoom.NEON,0)
        eventCard(list,string(R.string.billiard_carom_series),string(R.string.billiard_carom_series_hint),
            BilliardRun(mode=BilliardActivityMode.SERIES),Discipline.FREE,BilliardRoom.CLUB,0)
    }
    private fun records() {
        page=6
        val list=page(R.string.billiard_records,string(R.string.billiard_home_summary,progress.clubCount,progress.medalCount))
        preview(BilliardPreviewSpec())
        rooms.forEachIndexed { i,room -> if(progress.trophy("club_$i")) list.addView(label(string(R.string.billiard_club_trophy,string(room.title)),17f,0xFFE4C488.toInt())) }
        Discipline.entries.forEach { d ->
            val stats=progress.stats(d)
            if(stats.optInt("played")>0) {
                val details=column().apply { visibility=View.GONE }
                card(list,string(d.titleRes()),string(R.string.billiard_stats_summary,stats.optInt("played"),stats.optInt("won"))) {
                    details.visibility=if(details.visibility==View.VISIBLE) View.GONE else View.VISIBLE
                    preview(BilliardPreviewSpec(d))
                }
                list.addView(details)
                details.addView(label(string(R.string.billiard_stats_detail,stats.optInt("shots"),stats.optInt("points")),14f))
                for(m in listOf(BilliardActivityMode.CUP,BilliardActivityMode.LEAGUE)) if(progress.trophy("${m.name}_${d.name}"))
                    details.addView(label(string(R.string.billiard_trophy,string(modeName(m))),15f,0xFFE4C488.toInt()))
                for(m in listOf(BilliardActivityMode.SERIES,BilliardActivityMode.TIMED)) for(style in BilliardGameStyle.entries) for(aid in 0..2) {
                    val best=progress.record("${m.name}_${d.name}_${style.name}_$aid")
                    if(best>0) details.addView(label(string(R.string.billiard_record_detail,string(modeName(m)),string(style.title),
                        string(listOf(R.string.billiard_aid_none,R.string.billiard_aid_contact,R.string.billiard_aid_full)[aid]),best),14f))
                }
            }
        }
        if(Discipline.entries.none { progress.stats(it).optInt("played")>0 }) list.addView(label(string(R.string.billiard_records_empty),15f))
    }
    companion object {
        fun modeName(mode: BilliardActivityMode)=when(mode) {
            BilliardActivityMode.QUICK -> R.string.billiard_quick
            BilliardActivityMode.CAREER -> R.string.billiard_career
            BilliardActivityMode.CUP -> R.string.billiard_cup
            BilliardActivityMode.LEAGUE -> R.string.billiard_league
            BilliardActivityMode.CHALLENGE -> R.string.billiard_challenges
            BilliardActivityMode.LESSON -> R.string.billiard_school
            BilliardActivityMode.SERIES -> R.string.billiard_series
            BilliardActivityMode.TIMED -> R.string.billiard_timed
        }
        fun goalName(goal: BilliardGoal)=listOf(R.string.billiard_goal_clear,R.string.billiard_goal_bank,R.string.billiard_goal_order,
            R.string.billiard_goal_draw,R.string.billiard_goal_follow,R.string.billiard_goal_carom)[goal.ordinal]
        fun lessonText(goal: BilliardGoal)=listOf(R.string.billiard_lesson_clear,R.string.billiard_lesson_bank,R.string.billiard_lesson_order,
            R.string.billiard_lesson_draw,R.string.billiard_lesson_follow,R.string.billiard_lesson_carom)[goal.ordinal]
    }
    private fun exerciseDescription(e: BilliardExercise)=context.getString(when(e.goal) {
        BilliardGoal.CLEAR -> R.string.billiard_objective_clear
        BilliardGoal.BANK -> R.string.billiard_objective_bank
        BilliardGoal.ORDER -> R.string.billiard_objective_order
        BilliardGoal.DRAW -> R.string.billiard_objective_draw
        BilliardGoal.FOLLOW -> R.string.billiard_objective_follow
        BilliardGoal.CAROM -> R.string.billiard_objective_carom
    },if(e.goal==BilliardGoal.CAROM) e.requiredRails else e.count,e.maxShots)
}
