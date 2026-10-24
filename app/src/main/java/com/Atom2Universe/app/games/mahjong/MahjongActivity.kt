package com.Atom2Universe.app.games.mahjong

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import java.time.LocalDate
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MahjongActivity : ThemedActivity() {
    private val store by lazy { MahjongStore(this) }
    private val prefs get() = store.prefs
    private var run: MahjongSave.Saved? = null
    private var board: MahjongBoardView? = null
    private var screen = "menu"
    private var collectionReturn = "menu"
    private var collectionTextures = true
    private var chimes: MahjongChimes? = null
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val workHandler = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,LinkedBlockingQueue<Runnable>()) { task ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); task.run() },"Mahjong puzzles")
    }
    private var boardTask: Future<*>? = null
    private var hintTask: Future<*>? = null
    private var boardRevision = 0
    private var hintRevision = 0
    private data class Loading(val slot: String, val fresh: Boolean, val restoring: Boolean)
    private var loading: Loading? = null
    private var boardBusy = false
    private var boardControls: ViewGroup? = null
    private var cachedHintState: MahjongSnapshot? = null
    private data class Hint(val ids: List<Int>, val slide: MahjongSlide?, val limited: Boolean,
                            val continuation: List<MahjongSolutionStep>)
    private var cachedHint: Hint? = null
    private var scoreAnimator: ValueAnimator? = null
    private var displayedScore = 0
    private lateinit var scoreLabel: TextView
    private lateinit var remainingLabel: TextView
    private lateinit var comboLabel: TextView
    private lateinit var ruleLabel: TextView
    private lateinit var undoButton: View
    private lateinit var shuffleButton: View
    private lateinit var progress: ProgressBar
    private val ink get() = AppearanceStyle.color(this,R.attr.a2uTextColor)
    private val muted get() = AppearanceStyle.color(this,R.attr.a2uSecondaryTextColor)
    private val surface get() = AppearanceStyle.color(this,R.attr.a2uSurfaceColor)
    private val accent get() = AppearanceStyle.color(this,R.attr.a2uMusicAccent)
    private val effects get() = prefs.getBoolean("effects",true)
    private val modeTitles = intArrayOf(R.string.mj_classic,R.string.mj_triple,R.string.mj_connect,
        R.string.mj_cascade,R.string.mj_slide,R.string.mj_ten)
    private val descriptions = intArrayOf(R.string.mj_classic_desc,R.string.mj_triple_desc,R.string.mj_connect_desc,
        R.string.mj_cascade_desc,R.string.mj_slide_desc,R.string.mj_ten_desc)
    private val ruleSummaries = intArrayOf(R.string.mj_classic_rule,R.string.mj_triple_rule,R.string.mj_connect_rule,
        R.string.mj_cascade_rule,R.string.mj_slide_rule,R.string.mj_ten_rule)
    private val styleNames get() = resources.getStringArray(R.array.mj_porcelain_names)
    private val textureNames get() = resources.getStringArray(R.array.mj_texture_names)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableImmersiveMode()
        if(prefs.getBoolean("sound",true)) chimes=MahjongChimes(this)
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when(screen) {
                    "menu" -> finish()
                    "collection" -> leaveCollection()
                    else -> showMenu()
                }
            }
        })
        collectionTextures=savedInstanceState?.getBoolean("collection_textures",true) ?: true
        collectionReturn=savedInstanceState?.getString("collection_return") ?: "menu"
        val slot=savedInstanceState?.getString("active_slot")
        val pendingSlot=savedInstanceState?.getString("loading_slot")
        if(pendingSlot!=null) openSlot(pendingSlot,savedInstanceState?.getBoolean("loading_fresh")==true,savedInstanceState?.getBoolean("loading_restoring")==true)
        else if(slot!=null && savedInstanceState?.getString("screen") in listOf("board","reward")) openSlot(slot,restoring=true)
        else if(savedInstanceState?.getString("screen")=="collection") {
            run=slot?.let(store::load); showCollection()
        } else showMenu()
    }

    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun panel(color:Int=surface,stroke:Int=AppearanceStyle.color(this,R.attr.a2uOutlineColor))=GradientDrawable().apply {
        setColor(color); cornerRadius=AppearanceStyle.corner(this@MahjongActivity,20f); setStroke(dp(1),stroke)
    }
    private fun label(value:String,size:Float=16f,color:Int=ink)=TextView(this).apply {
        text=value; textSize=size; setTextColor(color); gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(4),dp(3),dp(4),dp(3))
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun LinearLayout.full(view:View,gap:Int=8,height:Int=ViewGroup.LayoutParams.WRAP_CONTENT) {
        addView(view,LinearLayout.LayoutParams(-1,height).apply { topMargin=dp(gap) })
    }
    private fun root()=column().apply {
        setPadding(dp(12),dp(8),dp(12),dp(10))
        background=AppearanceStyle.screen(this@MahjongActivity)
    }
    private fun icon(symbol:MahjongIcon,title:Int,color:Int=ink,bg:Int=surface,action:()->Unit)=ImageButton(this).apply {
        setImageDrawable(MahjongIconDrawable(symbol,color))
        scaleType=ImageView.ScaleType.FIT_CENTER
        contentDescription=getString(title); background=panel(bg)
        setPadding(dp(13),dp(13),dp(13),dp(13)); minimumHeight=dp(48); minimumWidth=dp(48)
        setOnClickListener { action() }
        setOnLongClickListener { Toast.makeText(this@MahjongActivity,title,Toast.LENGTH_SHORT).show(); true }
    }
    private fun header(title:String,back:()->Unit,extra:View?=null,help:()->Unit)=LinearLayout(this).apply {
        gravity=Gravity.CENTER_VERTICAL
        addView(icon(MahjongIcon.BACK,R.string.mj_back,action=back),LinearLayout.LayoutParams(dp(48),dp(48)))
        addView(label(title,21f).apply { setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER; maxLines=2 },
            LinearLayout.LayoutParams(0,-2,1f))
        if(extra!=null) addView(extra,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(5) })
        addView(icon(MahjongIcon.INFO,R.string.kit_rules,action=help),LinearLayout.LayoutParams(dp(48),dp(48)))
    }
    private fun toolButton(symbol:MahjongIcon,title:Int,action:()->Unit)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; background=panel()
        contentDescription=getString(title); isClickable=true; isFocusable=true
        setOnClickListener { action() }
        addView(ImageView(this@MahjongActivity).apply {
            setImageDrawable(MahjongIconDrawable(symbol,ink)); scaleType=ImageView.ScaleType.FIT_CENTER
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(23),dp(23)))
        addView(label(getString(title),11f).apply {
            setPadding(0,dp(3),0,dp(3))
            gravity=Gravity.CENTER; maxLines=1; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
    }
    private fun collectionProgress(textures:Boolean=true):View = column().apply {
        val owned=if(textures) store.unlockedTextures else store.unlocked
        background=panel(); setPadding(dp(12),dp(8),dp(12),dp(8))
        if(owned.size==MahjongStyles.COUNT) {
            addView(label(getString(R.string.mj_collection_complete),14f,accent))
        } else {
            val price=if(textures) store.nextTexturePrice else store.nextPrice
            addView(label(getString(if(textures) R.string.mj_next_texture_price else R.string.mj_next_price,price),14f).apply { setTypeface(typeface,Typeface.BOLD) })
            addView(label(getString(if(store.pearls>=price) R.string.mj_can_buy else R.string.mj_save_pearls,
                (price-store.pearls).coerceAtLeast(0)),12f,muted))
            addView(ProgressBar(this@MahjongActivity,null,android.R.attr.progressBarStyleHorizontal).apply {
                max=price; progress=store.pearls.coerceAtMost(price)
                progressTintList=android.content.res.ColorStateList.valueOf(accent)
                progressBackgroundTintList=android.content.res.ColorStateList.valueOf(MahjongArt.mix(surface,accent,.15f))
            },LinearLayout.LayoutParams(-1,dp(4)).apply { topMargin=dp(4) })
        }
    }
    private fun modeHelp(mode:MahjongMode) {
        board?.pause()
        showGameRules(modeTitles[mode.ordinal],getString(R.string.mj_mode_help,
            getString(descriptions[mode.ordinal]),getString(R.string.mj_help_tools))) { board?.resume() }
    }
    private fun gardenHelp()=showGameRules(R.string.mj_title,R.string.mj_garden_help)

    private fun showMenu() {
        save(); releaseBoard(); run=null; screen="menu"; dialog?.dismiss(); dialog=null
        val root=root(); setContentView(root)
        root.full(header(getString(R.string.mj_title),{ finish() },
            icon(MahjongIcon.SETTINGS,R.string.mj_settings) { settings() }) { gardenHelp() },0)
        val scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false }
        val body=column().apply { setPadding(0,dp(4),0,dp(8)) }
        scroll.addView(body); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val collection=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL; background=panel(); setPadding(dp(8),dp(3),dp(8),dp(3))
            contentDescription=getString(R.string.mj_collection); isClickable=true; isFocusable=true
            setOnClickListener { openCollection() }
        }
        collection.addView(object:View(this) {
            private val art=MahjongArt(this@MahjongActivity)
            override fun onDraw(c:Canvas) {
                val w=height*.43f; val h=w*1.24f
                for(i in 0..2) {
                    val x=width*.5f+(i-1)*w*.48f-w/2
                    c.save(); c.rotate((i-1)*14f,x+w/2,height*.47f)
                    art.tile(c,x,height*.11f,w,h,i*12,accent,style=store.style,texture=(store.texture+i)%MahjongStyles.COUNT); c.restore()
                }
            }
        },LinearLayout.LayoutParams(dp(94),dp(76)))
        val counter=column()
        counter.addView(label(getString(R.string.mj_collection_short),16f).apply { setTypeface(typeface,Typeface.BOLD) })
        counter.addView(label(getString(R.string.mj_customize_action),13f,accent))
        collection.addView(counter,LinearLayout.LayoutParams(0,-2,1f))
        collection.addView(label(getString(R.string.mj_pearl_count,store.pearls),19f,ink).apply {
            contentDescription=getString(R.string.mj_profile,store.wins,store.pearls)
        })
        body.full(collection,4)
        body.full(collectionProgress(),6)
        body.full(MahjongGiftMeter(this,store.wins),2,dp(32))
        val today=LocalDate.now().toEpochDay()
        body.full(label(if(MahjongCollection.isWeekend(today)) getString(R.string.mj_weekend_bonus)
            else getString(R.string.mj_featured_bonus,getString(modeTitles[MahjongCollection.featuredMode(today).ordinal])),14f,accent).apply {
            gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD)
        },4)
        body.full(label(getString(R.string.mj_difficulty),14f).apply { setTypeface(typeface,Typeface.BOLD) },8)
        val difficultyChoices=LinearLayout(this)
        listOf(R.string.mj_difficulty_normal,R.string.mj_difficulty_hard).forEachIndexed { level,title ->
            difficultyChoices.addView(label(getString(title),14f).apply {
                gravity=Gravity.CENTER; tag="difficulty:$level"
                isSelected=store.difficulty==level; isClickable=true; isFocusable=true
                background=panel(if(isSelected) MahjongArt.mix(surface,accent,.25f) else surface)
                setOnClickListener { store.setDifficulty(level); showMenu() }
            },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginStart=dp(3); marginEnd=dp(3) })
        }
        body.full(difficultyChoices,3)
        body.full(label(getString(R.string.mj_difficulty_note),12f,muted),0)
        val cards=MahjongMode.entries.map { mode -> modeCard(mode.ordinal,mode.name,modeTitles[mode.ordinal]) }
            .toMutableList<View>()
        cards+=modeCard(6,"journey",R.string.mj_journey_short)
        cards+=modeCard(7,"daily",R.string.mj_daily_short)
        addGrid(body,cards,if(resources.configuration.screenWidthDp>=700) 4 else if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) 3 else 2)
    }

    private fun modeCard(index:Int,slot:String,title:Int):View {
        val ongoing=store.summary(slot)?.takeIf { it.remaining>0 }
        val card=object:FrameLayout(this) {
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                val side=MeasureSpec.getSize(widthMeasureSpec)
                super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY))
            }
        }.apply {
            background=panel(MahjongArt.modeColors[index]); clipToOutline=true; elevation=dp(2).toFloat()
            tag="mode:$slot"; isClickable=true; isFocusable=true
            contentDescription=if(ongoing!=null) getString(R.string.mj_continue,getString(title)) else getString(title)
            setOnClickListener { openSlot(slot) }
        }
        card.addView(object:View(this) {
            private val art=MahjongArt(this@MahjongActivity)
            override fun onDraw(c:Canvas) { art.modeScene(c,width.toFloat(),height.toFloat(),index,(index*2+store.style)%MahjongStyles.COUNT) }
        },FrameLayout.LayoutParams(-1,-1))
        card.addView(label(getString(title),19f,Color.WHITE).apply {
            setTypeface(typeface,Typeface.BOLD); setPadding(dp(12),0,dp(40),dp(12)); maxLines=2
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM))
        val play=ImageView(this).apply {
            setImageDrawable(MahjongIconDrawable(MahjongIcon.PLAY,Color.WHITE)); setPadding(dp(8),dp(8),dp(8),dp(8))
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(play,FrameLayout.LayoutParams(dp(36),dp(36),Gravity.BOTTOM or Gravity.END).apply { bottomMargin=dp(9); marginEnd=dp(5) })
        card.addView(icon(MahjongIcon.INFO,R.string.kit_rules,Color.WHITE,0x20334363) {
            if(index<6) modeHelp(MahjongMode.entries[index])
            else showGameRules(title,if(index==6) R.string.mj_journey_help else R.string.mj_daily_help)
        },FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END))
        if(ongoing!=null) {
            val badge=label(getString(R.string.mj_fraction,ongoing.total-ongoing.remaining,ongoing.total),12f,Color.WHITE)
            badge.background=panel(0x60334363); badge.gravity=Gravity.CENTER
            card.addView(badge,FrameLayout.LayoutParams(dp(60),dp(28),Gravity.TOP or Gravity.START).apply { topMargin=dp(10); marginStart=dp(8) })
            card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max=ongoing.total; progress=max-ongoing.remaining
                progressTintList=android.content.res.ColorStateList.valueOf(0xffffe193.toInt())
            },FrameLayout.LayoutParams(-1,dp(4),Gravity.BOTTOM))
        }
        return card
    }

    private fun addGrid(parent:LinearLayout,views:List<View>,columns:Int) {
        for(row in views.chunked(columns)) {
            val line=LinearLayout(this)
            repeat(columns) { i ->
                line.addView(row.getOrNull(i) ?: Space(this),LinearLayout.LayoutParams(0,-2,1f).apply {
                    if(i>0) marginStart=dp(8)
                })
            }
            parent.full(line,8)
        }
    }

    private fun openSlot(slot:String,fresh:Boolean=false,restoring:Boolean=false) {
        save(); releaseBoard()
        loading=Loading(slot,fresh,restoring); screen="loading"
        showLoading()
        val revision=boardRevision
        val today=LocalDate.now().toEpochDay()
        boardTask=compute({ store.prepareRun(slot,today,fresh,restoring) },{ prepared ->
            if(revision==boardRevision && screen=="loading") {
                // A cancelled or superseded job can never overwrite another saved board.
                store.activate(prepared); run=prepared; loading=null
                if(prepared.game.complete) showReward() else showBoard()
            }
        },{
            if(revision==boardRevision && screen=="loading") {
                loading=null
                if(run!=null) { if(run!!.game.complete) showReward() else showBoard() } else showMenu()
                Toast.makeText(this,R.string.mj_prepare_failed,Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showLoading() {
        val root=root(); setContentView(root)
        root.full(header(getString(R.string.mj_title),{ showMenu() }) { gardenHelp() },0)
        root.addView(column().apply {
            gravity=Gravity.CENTER
            addView(ProgressBar(this@MahjongActivity),LinearLayout.LayoutParams(dp(40),dp(40)))
            full(label(getString(R.string.mj_preparing),16f,muted).apply { gravity=Gravity.CENTER },16)
        },LinearLayout.LayoutParams(-1,0,1f))
    }

    private fun <T> compute(work:()->T,ready:(T)->Unit,failed:()->Unit):Future<*> = worker.submit {
        try {
            val result=work()
            if(!Thread.currentThread().isInterrupted) workHandler.post { if(!isFinishing && !isDestroyed) ready(result) }
        } catch(_:CancellationException) {
            // Navigation and a newer move invalidate obsolete work.
        } catch(error:Exception) {
            android.util.Log.e("Mahjong","Puzzle calculation failed",error)
            workHandler.post { if(!isFinishing && !isDestroyed) failed() }
        }
    }

    private fun cancelHint() {
        hintRevision++; hintTask?.cancel(true); hintTask=null; worker.purge()
    }

    private fun copyGame(game:MahjongGame,history:Boolean=false) = MahjongGame(game.mode,game.seed,game.stage,initializeBoard=false).apply {
        restore(game.snapshot())
        solutionSteps.addAll(game.solutionSteps)
        if(history) this.history.addAll(game.history)
    }

    private fun requestHint(show:Boolean) {
        val current=run ?: return
        if(boardBusy || current.game.complete) return
        cancelHint()
        val revision=hintRevision
        val target=board ?: return
        val state=current.game.snapshot()
        fun deliver(suggestion:Hint) {
            if(revision!=hintRevision || run!==current || board!==target || current.game.snapshot()!=state) return
            cachedHintState=state; cachedHint=suggestion
            if(!suggestion.limited && suggestion.ids.isNotEmpty() && current.game.mode!=MahjongMode.SLIDE) {
                current.game.solutionSteps.clear(); current.game.solutionSteps.addAll(suggestion.continuation)
            }
            if(show) {
                target.showHint(suggestion.ids,suggestion.slide)
                ruleLabel.text=getString(if(suggestion.limited) R.string.mj_hint_limit
                    else if(suggestion.ids.isEmpty()) R.string.mj_stuck
                    else if(current.game.mode==MahjongMode.SLIDE && suggestion.ids.size==1)
                        if(suggestion.slide!=null) R.string.mj_slide_hint else R.string.mj_slide_focus_hint
                    else R.string.mj_hint_done)
            }
            if(suggestion.ids.isEmpty() && !suggestion.limited) pulse(shuffleButton)
        }
        cachedHint?.takeIf { cachedHintState==state }?.let { deliver(it); return }
        if(show) ruleLabel.setText(R.string.mj_thinking)
        val copy=copyGame(current.game)
        hintTask=compute({ Hint(copy.hint(),copy.slideHint(),copy.hintSearchLimited,copy.solutionSteps.toList()) },::deliver,{
            if(revision==hintRevision && board===target) ruleLabel.text=ruleText(current.game)
        })
    }

    private fun shuffleBoard() {
        val current=run ?: return
        if(boardBusy) return
        cancelHint()
        val target=board ?: return
        val revision=++boardRevision
        val copy=copyGame(current.game,history=true)
        setBoardBusy(true)
        ruleLabel.setText(R.string.mj_preparing)
        boardTask=compute({ copy.shuffle(); copy },{ shuffled ->
            if(revision==boardRevision && run===current && board===target) {
                current.game.restore(shuffled.snapshot())
                current.game.history.clear(); current.game.history.addAll(shuffled.history)
                current.game.solution.clear(); current.game.solution.addAll(shuffled.solution)
                current.game.solutionSteps.clear(); current.game.solutionSteps.addAll(shuffled.solutionSteps)
                setBoardBusy(false); target.refresh(); updateHud(); save()
            }
        },{
            if(revision==boardRevision && board===target) {
                setBoardBusy(false); updateHud()
                Toast.makeText(this,R.string.mj_prepare_failed,Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setBoardBusy(busy:Boolean) {
        boardBusy=busy; board?.isEnabled=!busy
        boardControls?.let { controls -> for(i in 0 until controls.childCount) controls.getChildAt(i).isEnabled=!busy }
    }

    private fun showBoard() {
        val current=run ?: return
        releaseBoard(); screen="board"
        val root=root(); setContentView(root)
        val modeTitle=getString(modeTitles[current.game.mode.ordinal])
        root.full(header(if(current.bonusMultiplier==2) getString(R.string.mj_mode_bonus_title,modeTitle) else modeTitle,{ showMenu() },
            toolButton(MahjongIcon.COLLECTION,R.string.mj_textures) { openCollection() }.apply { contentDescription=getString(R.string.mj_collection) }) { modeHelp(current.game.mode) },0)
        val hud=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(6),dp(4),dp(6),dp(2)) }
        scoreLabel=label("",21f).apply { setTypeface(typeface,Typeface.BOLD) }
        comboLabel=label("",23f,0xffc27417.toInt()).apply { gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD) }
        remainingLabel=label("",14f,muted).apply { gravity=Gravity.END }
        hud.addView(scoreLabel,LinearLayout.LayoutParams(0,-2,1f))
        hud.addView(comboLabel,LinearLayout.LayoutParams(0,-2,1f))
        hud.addView(remainingLabel,LinearLayout.LayoutParams(0,-2,1f))
        root.full(hud,3)
        ruleLabel=label(getString(ruleSummaries[current.game.mode.ordinal]),12f,muted).apply {
            gravity=Gravity.CENTER; maxLines=2
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) {
            hud.addView(ruleLabel,LinearLayout.LayoutParams(0,-2,3f))
        } else root.full(ruleLabel,0)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=current.game.tiles.size; progressTintList=android.content.res.ColorStateList.valueOf(accent)
        }
        root.full(progress,0,dp(4))
        board=MahjongBoardView(this,current.game,accent,store.style,effects,prefs.getBoolean("haptics",true),store.texture).apply {
            onMove={ result -> if(run===current) this@MahjongActivity.onMove(result) }
        }
        root.addView(board,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=dp(3) })
        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER }
        boardControls=controls
        undoButton=toolButton(MahjongIcon.UNDO,R.string.mj_undo) {
            cancelHint()
            if(!boardBusy && current.game.undo()) { board?.refresh(); updateHud(); save() }
        }
        val hint=toolButton(MahjongIcon.HINT,R.string.mj_hint) { requestHint(show=true) }
        shuffleButton=toolButton(MahjongIcon.SHUFFLE,R.string.mj_shuffle) { shuffleBoard() }
        val newBoard=toolButton(MahjongIcon.PLAY,R.string.mj_new_short) { replaceBoard() }
        for(control in listOf(undoButton,hint,shuffleButton,newBoard)) controls.addView(control,
            LinearLayout.LayoutParams(0,dp(58),1f).apply { marginStart=dp(4); marginEnd=dp(4) })
        root.full(controls,5)
        if(current.iceMigration) {
            current.iceMigration=false
            Toast.makeText(this,R.string.mj_ice_migration,Toast.LENGTH_LONG).show()
        }
        displayedScore=current.game.score; updateHud(); save()
    }

    private fun onMove(result:MahjongGame.Result) {
        val current=run ?: return
        cancelHint()
        val matched=result==MahjongGame.Result.MATCHED
        if(matched && prefs.getBoolean("sound",true)) chimes?.play(current.game.combo)
        updateHud(matched)
        ruleLabel.text=when(result) {
            MahjongGame.Result.TRAY_FULL -> getString(R.string.mj_tray_full)
            MahjongGame.Result.BLOCKED -> getString(if(current.game.mode==MahjongMode.SLIDE) R.string.mj_slide_blocked else R.string.mj_blocked)
            MahjongGame.Result.MISMATCH -> getString(when(current.game.mode) {
                MahjongMode.CONNECT, MahjongMode.CASCADE -> R.string.mj_path_miss
                MahjongMode.TEN -> R.string.mj_ten_miss
                MahjongMode.SLIDE -> R.string.mj_slide_miss
                else -> R.string.mj_pair_miss
            })
            else -> ruleText(current.game)
        }
        if(current.game.complete) {
            store.finish(current)
            handler.removeCallbacksAndMessages(null)
            if(effects) handler.postDelayed({ if(run===current && screen=="board" && !isFinishing && !isDestroyed) showReward() },650)
            else showReward()
        } else {
            if(result==MahjongGame.Result.TRAY_FULL) pulse(shuffleButton)
            else if(matched && current.game.mode!=MahjongMode.TRIPLE) requestHint(show=false)
            if(result==MahjongGame.Result.TRAY_FULL) pulse(undoButton)
            // Ice focus and its rollback origin are durable, including a blocked swipe
            // that selected a different tile. Other pair selections remain transient.
            if(result==MahjongGame.Result.MATCHED || result==MahjongGame.Result.MOVED ||
                result==MahjongGame.Result.MISMATCH || result==MahjongGame.Result.TRAY_FULL ||
                (result==MahjongGame.Result.SELECTED && current.game.mode==MahjongMode.TRIPLE) ||
                (current.game.mode==MahjongMode.SLIDE && result!=MahjongGame.Result.IGNORED)) save()
        }
    }

    private fun replaceBoard() {
        val current=run ?: return
        board?.pause()
        dialog=AlertDialog.Builder(this).setTitle(R.string.mj_replace_title).setMessage(R.string.mj_replace_desc)
            .setNegativeButton(R.string.mj_cancel,null)
            .setPositiveButton(R.string.mj_new_board) { _,_ ->
                openSlot(current.slot,fresh=true)
            }.create().also { popup ->
                popup.setOnDismissListener { if(screen=="board") board?.resume() }
                popup.show()
            }
    }

    private fun pulse(view:View) {
        if(!effects || !ValueAnimator.areAnimatorsEnabled()) return
        view.animate().cancel(); view.scaleX=1.12f; view.scaleY=1.12f
        view.animate().scaleX(1f).scaleY(1f).setDuration(350).start()
    }
    private fun updateHud(animate:Boolean=false) {
        val current=run?.game ?: return
        scoreAnimator?.cancel()
        if(animate && effects && ValueAnimator.areAnimatorsEnabled()) {
            scoreAnimator=ValueAnimator.ofInt(displayedScore,current.score).apply {
                duration=260
                addUpdateListener { displayedScore=it.animatedValue as Int; scoreLabel.text=getString(R.string.mj_number,displayedScore) }
                start()
            }
        } else { displayedScore=current.score; scoreLabel.text=getString(R.string.mj_number,current.score) }
        scoreLabel.contentDescription=getString(R.string.mj_score,current.score)
        comboLabel.text=getString(R.string.mj_multiplier,current.combo.coerceAtLeast(1))
        if(animate) pulse(comboLabel)
        remainingLabel.text=getString(R.string.mj_fraction,current.remaining,current.tiles.size)
        remainingLabel.contentDescription=getString(R.string.mj_remaining,current.remaining,current.tiles.size)
        progress.progress=current.tiles.size-current.remaining
        undoButton.isEnabled=!boardBusy && current.history.isNotEmpty() && !current.complete
        undoButton.alpha=if(undoButton.isEnabled) 1f else .4f
        ruleLabel.text=ruleText(current)
    }

    private fun ruleText(game:MahjongGame):String=when(game.mode) {
        MahjongMode.TRIPLE -> if(game.tray.size>=7) getString(R.string.mj_tray_full)
            else getString(R.string.mj_tray_status,game.tray.size)
        MahjongMode.CASCADE -> getString(R.string.mj_cascade_status,resources.getStringArray(R.array.mj_gravity_directions)[game.gravityDirection])
        MahjongMode.SLIDE -> getString(if(game.selected==null) R.string.mj_slide_rule else R.string.mj_slide_focus_rule)
        else -> getString(ruleSummaries[game.mode.ordinal])
    }

    private fun showReward() {
        val current=run ?: return
        val reward=current.reward ?: store.finish(current) ?: return
        releaseBoard(); screen="reward"
        val root=root(); setContentView(root)
        val title=when { reward.gift -> R.string.mj_big_gift; reward.styles.isEmpty() -> R.string.mj_victory_short; else -> R.string.mj_new_porcelain }
        root.full(header(getString(title),{ showMenu() }) { gardenHelp() },0)
        val amount=label(getString(R.string.mj_pearl_gain,reward.pearls),32f,accent).apply {
            gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD)
        }
        root.full(amount,6)
        root.full(label(getString(R.string.mj_result_short,current.game.score,current.game.peakCombo),15f,muted).apply { gravity=Gravity.CENTER },0)
        root.full(label(getString(R.string.mj_reward_breakdown,reward.comboBonus,current.bonusMultiplier,
            if(reward.gift) 100 else 0),13f,muted).apply { gravity=Gravity.CENTER },0)
        val chosenName=label(if(reward.styles.isNotEmpty()) styleNames[reward.styles.first()] else "",20f).apply { gravity=Gravity.CENTER }
        root.addView(MahjongRewardView(this,reward,store.style,effects) { id -> store.equip(id); chosenName.text=styleNames[id] },
            LinearLayout.LayoutParams(-1,0,1f))
        if(reward.styles.isNotEmpty()) root.full(chosenName,0)
        root.full(collectionProgress(),6)
        val actions=LinearLayout(this).apply { gravity=Gravity.CENTER }
        actions.addView(icon(MahjongIcon.HOME,R.string.mj_garden) { showMenu() },
            LinearLayout.LayoutParams(dp(58),dp(58)).apply { marginEnd=dp(10) })
        actions.addView(toolButton(MahjongIcon.COLLECTION,R.string.mj_textures) { openCollection() }.apply { contentDescription=getString(R.string.mj_collection) },
            LinearLayout.LayoutParams(dp(58),dp(58)).apply { marginEnd=dp(10) })
        actions.addView(toolButton(MahjongIcon.PLAY,R.string.mj_next) {
            openSlot(current.slot,fresh=true)
        },LinearLayout.LayoutParams(0,dp(58),1f))
        root.full(actions,6)
    }

    private fun openCollection() {
        collectionReturn=screen; collectionTextures=true; save(); releaseBoard(); showCollection()
    }
    private fun leaveCollection() {
        when(collectionReturn) {
            "board" -> if(run!=null) showBoard() else showMenu()
            "reward" -> if(run!=null) showReward() else showMenu()
            else -> showMenu()
        }
    }
    private fun showCollection() {
        screen="collection"
        val textures=collectionTextures
        val names=if(textures) textureNames else styleNames
        val owned=if(textures) store.unlockedTextures else store.unlocked
        val equipped=if(textures) store.texture else store.style
        val price=if(textures) store.nextTexturePrice else store.nextPrice
        val root=root(); setContentView(root)
        root.full(header(getString(R.string.mj_collection_short),{ leaveCollection() }) {
            showGameRules(R.string.mj_collection,R.string.mj_collection_help)
        },0)
        val tabs=LinearLayout(this)
        for(isTexture in listOf(true,false)) {
            tabs.addView(label(getString(if(isTexture) R.string.mj_textures else R.string.mj_porcelains),16f).apply {
                gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD)
                background=panel(if(textures==isTexture) MahjongArt.mix(surface,accent,.24f) else surface)
                tag=if(isTexture) "tab:textures" else "tab:porcelains"
                isSelected=textures==isTexture; isClickable=true; isFocusable=true
                setOnClickListener { collectionTextures=isTexture; showCollection() }
            },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginStart=dp(3); marginEnd=dp(3) })
        }
        root.full(tabs,6)
        root.full(label(getString(if(textures) R.string.mj_texture_selection else R.string.mj_porcelain_selection,
            names[equipped]),13f,muted),2)
        root.full(label(getString(R.string.mj_collection_count,owned.size,MahjongStyles.COUNT,store.pearls),16f).apply { gravity=Gravity.CENTER },0)
        root.full(collectionProgress(textures),4)
        val scroll=ScrollView(this); val body=column(); scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val cards=(0 until MahjongStyles.COUNT).map { id ->
            val card=object:FrameLayout(this) {
                override fun onMeasure(w:Int,h:Int) {
                    super.onMeasure(w,MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(w)*1.55f).toInt(),MeasureSpec.EXACTLY))
                }
            }.apply {
                background=panel(if(id==equipped) MahjongArt.mix(surface,accent,.18f) else surface)
                clipToOutline=true; isClickable=true; isFocusable=true
                tag=if(textures) "texture:$id" else "porcelain:$id"
                contentDescription=getString(if(id in owned) R.string.mj_style_available else R.string.mj_style_waiting,names[id])
                setOnClickListener {
                    if(id in owned) {
                        if(textures) store.equipTexture(id) else store.equip(id)
                        showCollection()
                        Toast.makeText(this@MahjongActivity,getString(R.string.mj_visual_applied,names[id]),Toast.LENGTH_SHORT).show()
                    } else showStylePreview(id,textures)
                }
            }
            card.addView(object:View(this) {
                private val art=MahjongArt(this@MahjongActivity)
                override fun onDraw(c:Canvas) {
                    val w=width*.60f; val h=w*1.24f
                    val material=if(textures) id else store.texture
                    val icon=if(textures) store.style else id
                    art.tile(c,width*.15f,height*.07f,w,h,if(textures) 4 else 0,accent,style=icon,texture=material)
                    val small=width*.30f
                    c.save(); c.rotate(10f,width*.70f,height*.39f)
                    art.tile(c,width*.57f,height*.29f,small,small*1.24f,if(textures) 8 else 10,accent,style=icon,texture=material)
                    c.restore()
                }
            },FrameLayout.LayoutParams(-1,-1))
            val caption=column()
            caption.addView(label(names[id],12f).apply { gravity=Gravity.CENTER; maxLines=2; setTypeface(typeface,Typeface.BOLD) })
            caption.addView(label(if(id in owned) getString(if(id==equipped) R.string.mj_equipped else R.string.mj_apply_visual)
                else getString(R.string.mj_price,price),11f,if(id in owned) accent else muted).apply { gravity=Gravity.CENTER })
            card.addView(caption,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { bottomMargin=dp(5) })
            val mark=ImageView(this).apply {
                setImageDrawable(MahjongIconDrawable(if(id==equipped) MahjongIcon.CHECK else MahjongIcon.LOCK,
                    if(id==equipped) accent else muted))
                setPadding(dp(5),dp(5),dp(5),dp(5))
                visibility=if(id==equipped || id !in owned) View.VISIBLE else View.GONE
            }
            card.addView(mark,FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP or Gravity.END))
            card
        }
        addGrid(body,cards,if(resources.configuration.screenWidthDp>=700) 6 else if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) 5 else 3)
    }
    private fun showStylePreview(id:Int,textures:Boolean) {
        val names=if(textures) textureNames else styleNames
        val price=if(textures) store.nextTexturePrice else store.nextPrice
        val body=column().apply { setPadding(dp(18),dp(12),dp(18),dp(12)) }
        body.addView(object:View(this) {
            private val art=MahjongArt(this@MahjongActivity)
            override fun onDraw(c:Canvas) {
                val w=minOf(width/4.5f,height*.58f); val h=w*1.24f
                repeat(3) { i -> art.tile(c,width/2f+(i-1)*w*1.12f-w/2,(height-h)/2,w,h,
                    if(textures) intArrayOf(4,14,26)[i] else intArrayOf(0,10,18)[i],accent,
                    style=if(textures) store.style else id,texture=if(textures) id else store.texture) }
            }
        },LinearLayout.LayoutParams(-1,dp(160)))
        body.full(label(getString(if(textures) R.string.mj_texture_preview_help else R.string.mj_porcelain_preview_help),15f).apply { gravity=Gravity.CENTER })
        body.full(label(getString(R.string.mj_purchase_balance,store.pearls,price),15f).apply { gravity=Gravity.CENTER })
        val scroll=ScrollView(this).apply { addView(body) }
        dialog=AlertDialog.Builder(this).setTitle(names[id]).setView(scroll)
            .setNegativeButton(R.string.close,null)
            .setPositiveButton(getString(R.string.mj_buy,price)) { _,_ ->
                if(if(textures) store.purchaseTexture(id) else store.purchase(id)) {
                    chimes?.play(8); showCollection()
                    Toast.makeText(this,getString(R.string.mj_visual_applied,names[id]),Toast.LENGTH_SHORT).show()
                }
            }.create()
        dialog?.show()
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=store.pearls>=price
    }
    private fun settings() {
        val body=column().apply { setPadding(dp(24),dp(8),dp(24),dp(8)) }
        for((key,title) in listOf("sound" to R.string.mj_sound,"effects" to R.string.mj_effects,"haptics" to R.string.mj_haptics)) {
            body.full(SwitchCompat(this).apply {
                text=getString(title); isChecked=prefs.getBoolean(key,true); minHeight=dp(52)
                setOnCheckedChangeListener { _,checked ->
                    prefs.edit().putBoolean(key,checked).apply()
                    if(key=="sound") {
                        if(checked && chimes==null) chimes=MahjongChimes(this@MahjongActivity)
                        if(!checked) { chimes?.close(); chimes=null }
                    }
                }
            })
        }
        dialog=AlertDialog.Builder(this).setTitle(R.string.mj_settings).setView(body).setPositiveButton(R.string.close,null).show()
    }
    private fun save() { run?.let(store::save) }
    private fun releaseBoard() {
        boardRevision++; boardTask?.cancel(true); boardTask=null; cancelHint()
        workHandler.removeCallbacksAndMessages(null)
        cachedHintState=null; cachedHint=null; boardBusy=false; boardControls=null; loading=null
        handler.removeCallbacksAndMessages(null); scoreAnimator?.cancel(); scoreAnimator=null
        board?.pause(); board=null
    }
    override fun onPause() {
        cancelHint(); boardRevision++; boardTask?.cancel(true); boardTask=null; worker.purge()
        if(boardBusy) setBoardBusy(false)
        handler.removeCallbacksAndMessages(null); scoreAnimator?.cancel(); board?.pause(); save(); super.onPause()
    }
    override fun onResume() {
        super.onResume()
        val pending=loading
        if(screen=="loading" && boardTask==null && pending!=null) openSlot(pending.slot,pending.fresh,pending.restoring)
        else if(screen=="board" && run?.game?.complete==true) showReward()
        else { if(screen=="board") updateHud(); board?.resume() }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        save(); outState.putString("screen",screen); outState.putString("active_slot",run?.slot)
        loading?.let { outState.putString("loading_slot",it.slot); outState.putBoolean("loading_fresh",it.fresh); outState.putBoolean("loading_restoring",it.restoring) }
        outState.putBoolean("collection_textures",collectionTextures); outState.putString("collection_return",collectionReturn)
        super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig:Configuration) {
        super.onConfigurationChanged(newConfig); dialog?.dismiss(); dialog=null
        when(screen) {
            "loading" -> showLoading()
            "board" -> if(run?.game?.complete==true) showReward() else showBoard()
            "reward" -> showReward()
            "collection" -> showCollection()
            else -> showMenu()
        }
    }
    override fun onDestroy() { releaseBoard(); worker.shutdownNow(); chimes?.close(); dialog?.dismiss(); super.onDestroy() }
}
