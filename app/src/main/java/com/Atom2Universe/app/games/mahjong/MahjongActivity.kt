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
import com.Atom2Universe.app.util.FitRow
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
    private var collectionTab = 0
    private var chimes: MahjongChimes? = null
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val workHandler = Handler(Looper.getMainLooper())
    // Each save rewrites the whole preferences file: moves are saved at most every 1.5 s,
    // and any exit (menu, other board, onPause) saves at once.
    private val saveHandler = Handler(Looper.getMainLooper())
    private var savePending = false
    private val delayedSave = Runnable { savePending = false; run?.let(store::save) }
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
                            val continuation: List<MahjongSolutionStep>, val lost: Boolean)
    private var cachedHint: Hint? = null
    private lateinit var undoButton: View
    private lateinit var hintButton: View
    private lateinit var hintBadge: TextView
    private lateinit var progress: ProgressBar
    private var boardFrame: FrameLayout? = null
    private var stuckPanel: View? = null
    private val ink get() = AppearanceStyle.color(this,R.attr.a2uTextColor)
    private val muted get() = AppearanceStyle.color(this,R.attr.a2uSecondaryTextColor)
    private val surface get() = AppearanceStyle.color(this,R.attr.a2uSurfaceColor)
    private val accent get() = AppearanceStyle.color(this,R.attr.a2uMusicAccent)
    private val effects get() = prefs.getBoolean("effects",true)
    private val modeTitles = intArrayOf(R.string.mj_classic,R.string.mj_triple,R.string.mj_connect,
        R.string.mj_cascade,R.string.mj_slide,R.string.mj_ten)
    private val descriptions = intArrayOf(R.string.mj_classic_desc,R.string.mj_triple_desc,R.string.mj_connect_desc,
        R.string.mj_cascade_desc,R.string.mj_slide_desc,R.string.mj_ten_desc)
    private val styleNames get() = resources.getStringArray(R.array.mj_porcelain_names)
    private val textureNames get() = resources.getStringArray(R.array.mj_texture_names)
    private val tableNames get() = resources.getStringArray(R.array.mj_table_names)

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
        collectionTab=(savedInstanceState?.getInt("collection_tab",0) ?: 0).coerceIn(0,2)
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
    /** Une bande du haut trop chargée pour un téléphone étroit est réduite d'un bloc plutôt que coupée à droite. */
    private fun fitted(row:LinearLayout):View=FitRow(this).apply { addView(row,FrameLayout.LayoutParams(-1,-2)) }
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
    private fun header(title:String,back:()->Unit,extra:View?=null,titleColor:Int=ink,help:()->Unit)=LinearLayout(this).apply {
        gravity=Gravity.CENTER_VERTICAL
        addView(icon(MahjongIcon.BACK,R.string.mj_back,action=back),LinearLayout.LayoutParams(dp(48),dp(48)))
        addView(label(title,21f,titleColor).apply { setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER; maxLines=2 },
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
    private fun collectionProgress(tab:Int=0):View = column().apply {
        val owned=owned(tab)
        background=panel(); setPadding(dp(12),dp(8),dp(12),dp(8))
        if(owned.size==itemCount(tab)) {
            addView(label(getString(R.string.mj_collection_complete),14f,accent))
        } else {
            val price=price(tab)
            val next=intArrayOf(R.string.mj_next_texture_price,R.string.mj_next_price,R.string.mj_next_table_price)[tab]
            addView(label(getString(next,price),14f).apply { setTypeface(typeface,Typeface.BOLD) })
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
            getString(descriptions[mode.ordinal]),getString(if(mode==MahjongMode.SLIDE) R.string.mj_help_tools_untimed else R.string.mj_help_tools))) {
            if(stuckPanel==null) board?.resume()
        }
    }
    private fun gardenHelp()=showGameRules(R.string.mj_title,R.string.mj_garden_help)

    private fun showMenu() {
        save(); releaseBoard(); run=null; screen="menu"; dialog?.dismiss(); dialog=null
        val root=root(); setContentView(root)
        root.full(fitted(menuBanner()),0)
        val scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false }
        val body=column().apply { setPadding(0,dp(4),0,dp(8)) }
        scroll.addView(body); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        // Les étoiles du prochain coffre et le bonus du jour, sur une seule ligne.
        val today=LocalDate.now().toEpochDay()
        val news=LinearLayout(this).apply { gravity=Gravity.CENTER }
        news.addView(MahjongGiftMeter(this,store.wins),LinearLayout.LayoutParams(dp(150),dp(30)))
        news.addView(label(if(MahjongCollection.isWeekend(today)) getString(R.string.mj_weekend_bonus)
            else getString(R.string.mj_featured_bonus,getString(modeTitles[MahjongCollection.featuredMode(today).ordinal])),14f,accent).apply {
            setTypeface(typeface,Typeface.BOLD); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
        },LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(10) })
        body.full(news,2)
        val cards=MahjongMode.entries.map { mode -> modeCard(mode.ordinal,mode.name,modeTitles[mode.ordinal]) }
            .toMutableList<View>()
        cards+=modeCard(6,"journey",R.string.mj_journey_short)
        cards+=modeCard(7,"daily",R.string.mj_daily_short)
        addGrid(body,cards,if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) { if(resources.configuration.screenWidthDp>=700) 4 else 3 } else 2)
    }

    /**
     * Bannière du jardin : retour, titre, puis la collection (trois tuiles, perles et une fine
     * jauge vers le prochain visuel), la difficulté en barres, réglages et règles.
     */
    private fun menuBanner()=LinearLayout(this).apply {
        gravity=Gravity.CENTER_VERTICAL
        addView(icon(MahjongIcon.BACK,R.string.mj_back) { finish() },LinearLayout.LayoutParams(dp(48),dp(48)))
        addView(label(getString(R.string.mj_title),21f).apply {
            setTypeface(typeface,Typeface.BOLD); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(dp(10),0,dp(6),0)
        },LinearLayout.LayoutParams(0,-2,1f))
        addView(collectionChip(),LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(8) })
        addView(difficultyChoice(),LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(8) })
        addView(icon(MahjongIcon.SETTINGS,R.string.mj_settings) { settings() },LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(5) })
        addView(icon(MahjongIcon.INFO,R.string.kit_rules) { gardenHelp() },LinearLayout.LayoutParams(dp(48),dp(48)))
    }

    private fun collectionChip():View {
        val owned=store.unlockedTextures
        val price=store.nextTexturePrice
        val chip=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; background=panel()
            setPadding(dp(8),dp(2),dp(12),dp(4)); isClickable=true; isFocusable=true
            contentDescription=getString(R.string.mj_collection)
            setOnClickListener { openCollection() }
        }
        val line=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        line.addView(object:View(this) {
            private val art=MahjongArt(this@MahjongActivity)
            override fun onDraw(c:Canvas) {
                val w=height*.5f; val h=w*1.24f
                for(i in 0..2) {
                    val x=width*.5f+(i-1)*w*.48f-w/2
                    c.save(); c.rotate((i-1)*14f,x+w/2,height*.5f)
                    art.tile(c,x,height*.5f-h/2,w,h,i*12,accent,style=store.style,texture=(store.texture+i)%MahjongStyles.COUNT); c.restore()
                }
            }
        },LinearLayout.LayoutParams(dp(46),dp(34)))
        line.addView(label(getString(R.string.mj_pearl_count,store.pearls),17f).apply {
            setTypeface(typeface,Typeface.BOLD); contentDescription=getString(R.string.mj_profile,store.wins,store.pearls)
        })
        chip.addView(line)
        if(owned.size<MahjongStyles.COUNT) chip.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=price; progress=store.pearls.coerceAtMost(price)
            progressTintList=android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList=android.content.res.ColorStateList.valueOf(MahjongArt.mix(surface,accent,.15f))
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(-1,dp(3)))
        return chip
    }

    /** Facile ou Difficile : une touche bascule ; le choix vaut pour les prochains plateaux. */
    private fun difficultyChoice()=label(getString(if(store.difficulty==1) R.string.mj_difficulty_hard else R.string.mj_difficulty_normal),16f).apply {
        setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER; setPadding(dp(16),0,dp(16),0)
        background=panel(); tag="difficulty"; isClickable=true; isFocusable=true
        setOnClickListener { store.setDifficulty(1-store.difficulty); showMenu() }
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
            override fun onDraw(c:Canvas) { art.modeScene(c,width.toFloat(),height.toFloat(),index,store.style,store.texture) }
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
        val root=root().apply { background=MahjongTableDrawable(this@MahjongActivity,store.table) }; setContentView(root)
        root.full(header(getString(R.string.mj_title),{ showMenu() },titleColor=Color.WHITE) { gardenHelp() },0)
        root.addView(column().apply {
            gravity=Gravity.CENTER
            addView(ProgressBar(this@MahjongActivity),LinearLayout.LayoutParams(dp(40),dp(40)))
            full(label(getString(R.string.mj_preparing),16f,0xddffffff.toInt()).apply { gravity=Gravity.CENTER },16)
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
        hintsUsed=game.hintsUsed; shufflesUsed=game.shufflesUsed
        if(history) this.history.addAll(game.history)
    }

    private fun requestHint(show:Boolean) {
        val current=run ?: return
        if(boardBusy || current.game.complete) return
        if(show && current.game.hintsLeft<=0) { pulse(hintButton); return }
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
                if(suggestion.ids.isNotEmpty()) {
                    // Un indice n'est compté que s'il montre vraiment un coup.
                    current.game.hintsUsed++; target.showHint(suggestion.ids,suggestion.slide); updateHud(); saveSoon()
                } else pulse(undoButton)
            }
            // Glissières et Pile dix : le solveur repère un plateau devenu sans issue.
            if(suggestion.lost && (current.game.mode==MahjongMode.SLIDE || current.game.mode==MahjongMode.TEN)) showStuck(noSolution=true)
        }
        cachedHint?.takeIf { cachedHintState==state }?.let { deliver(it); return }
        val copy=copyGame(current.game)
        hintTask=compute({ Hint(copy.hint(),copy.slideHint(),copy.hintSearchLimited,copy.solutionSteps.toList(),copy.hintProvedLost) },::deliver,{})
    }

    private fun shuffleBoard() {
        val current=run ?: return
        if(boardBusy || current.game.shufflesLeft<=0) return
        cancelHint()
        val target=board ?: return
        val revision=++boardRevision
        val copy=copyGame(current.game,history=true)
        setBoardBusy(true)
        boardTask=compute({ copy.shuffle() to copy },{ (done,shuffled) ->
            if(revision==boardRevision && run===current && board===target) {
                setBoardBusy(false)
                if(done) {
                    current.game.restore(shuffled.snapshot())
                    current.game.history.clear(); current.game.history.addAll(shuffled.history)
                    current.game.solution.clear(); current.game.solution.addAll(shuffled.solution)
                    current.game.solutionSteps.clear(); current.game.solutionSteps.addAll(shuffled.solutionSteps)
                    current.game.shufflesUsed=shuffled.shufflesUsed
                    target.refresh(shuffled=true); target.resume(); updateHud(); save()
                    if(current.game.stuck) showStuck()
                } else showStuck(lost=true)
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
        val root=root().apply { background=MahjongTableDrawable(this@MahjongActivity,store.table) }; setContentView(root)
        root.full(fitted(boardBanner(current)),0)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=current.game.tiles.size; progressTintList=android.content.res.ColorStateList.valueOf(accent)
        }
        root.full(progress,2,dp(4))
        board=MahjongBoardView(this,current.game,accent,store.style,effects,prefs.getBoolean("haptics",true),store.texture).apply {
            onMove={ result -> if(run===current) this@MahjongActivity.onMove(result) }
        }
        val frame=FrameLayout(this).apply { clipChildren=false }
        frame.addView(board,FrameLayout.LayoutParams(-1,-1))
        boardFrame=frame
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=dp(3) })
        updateHud(); save()
        if(current.game.stuck) showStuck()
    }

    /** Bannière du plateau : retour, nom du mode, puis annuler, indice (avec son compteur), Nouveau, visuels et règles. */
    private fun boardBanner(current:MahjongSave.Saved)=LinearLayout(this).apply {
        gravity=Gravity.CENTER_VERTICAL
        addView(icon(MahjongIcon.BACK,R.string.mj_back) { showMenu() },LinearLayout.LayoutParams(dp(48),dp(48)))
        val title=when {
            current.journey -> getString(R.string.mj_stage,current.game.stage)
            current.daily -> getString(R.string.mj_daily_short)
            else -> getString(modeTitles[current.game.mode.ordinal])
        }
        addView(label(title,19f,Color.WHITE).apply {
            setTypeface(typeface,Typeface.BOLD); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(dp(10),0,dp(4),0)
        },LinearLayout.LayoutParams(0,-2,1f))
        if(current.bonusMultiplier==2) addView(label(getString(R.string.mj_bonus_badge),15f,accent).apply {
            setTypeface(typeface,Typeface.BOLD); setPadding(dp(6),0,dp(8),0)
        })
        val controls=LinearLayout(this@MahjongActivity).apply { gravity=Gravity.CENTER_VERTICAL }
        boardControls=controls
        undoButton=icon(MahjongIcon.UNDO,R.string.mj_undo) { undoMove() }
        controls.addView(undoButton,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(5) })
        hintBadge=label("",11f,androidx.core.content.ContextCompat.getColor(this@MahjongActivity,R.color.audio_on_accent)).apply {
            setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER; setPadding(0,0,0,0)
            background=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(accent) }
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        hintButton=FrameLayout(this@MahjongActivity).apply {
            addView(icon(MahjongIcon.HINT,R.string.mj_hint) { requestHint(show=true) },FrameLayout.LayoutParams(dp(48),dp(48)))
            addView(hintBadge,FrameLayout.LayoutParams(dp(19),dp(19),Gravity.TOP or Gravity.END))
            clipChildren=false
        }
        controls.addView(hintButton,LinearLayout.LayoutParams(dp(50),dp(50)).apply { marginEnd=dp(5) })
        controls.addView(label(getString(R.string.mj_new_short),16f,androidx.core.content.ContextCompat.getColor(this@MahjongActivity,R.color.audio_on_accent)).apply {
            setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER; setPadding(dp(16),0,dp(16),0)
            background=GradientDrawable().apply { setColor(accent); cornerRadius=AppearanceStyle.corner(this@MahjongActivity,20f) }
            contentDescription=getString(R.string.mj_new_short)
            isClickable=true; isFocusable=true
            setOnClickListener { replaceBoard() }
        },LinearLayout.LayoutParams(-2,dp(48)).apply { marginStart=dp(4); marginEnd=dp(10) })
        addView(controls)
        addView(icon(MahjongIcon.COLLECTION,R.string.mj_collection) { openCollection() },
            LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(5) })
        addView(icon(MahjongIcon.INFO,R.string.kit_rules) { modeHelp(current.game.mode) },LinearLayout.LayoutParams(dp(48),dp(48)))
    }

    private fun undoMove() {
        val current=run ?: return
        cancelHint()
        if(!boardBusy && current.game.undo()) { hideStuck(); board?.refresh(); board?.resume(); updateHud(); saveSoon() }
    }

    /**
     * Plus aucun coup : Mélanger (s'il en reste) ou Annuler. Sans mélange restant, ou quand
     * aucune redistribution n'existe, le plateau est perdu : Recommencer ou Annuler.
     */
    private fun showStuck(noSolution:Boolean=false,lost:Boolean=false) {
        val current=run ?: return
        val game=current.game
        val frame=boardFrame ?: return
        if(game.complete || screen!="board") return
        hideStuck()
        board?.pause()
        val canShuffle=!lost && game.shufflesLeft>0
        val title=when {
            !canShuffle -> R.string.mj_board_lost
            game.mode==MahjongMode.TRIPLE && game.tray.size>=MahjongGame.TRAY -> R.string.mj_tray_full
            noSolution -> R.string.mj_no_solution
            else -> R.string.mj_no_moves
        }
        val panel=column().apply {
            gravity=Gravity.CENTER_HORIZONTAL; background=panel(surface,accent); elevation=dp(10).toFloat()
            setPadding(dp(20),dp(14),dp(20),dp(16)); isClickable=true
        }
        panel.addView(label(getString(title),21f).apply { setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER })
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER }
        if(canShuffle) row.addView(panelButton(MahjongIcon.SHUFFLE,getString(R.string.mj_shuffle_count,game.shufflesLeft),true) {
            hideStuck(); shuffleBoard()
        }) else row.addView(panelButton(MahjongIcon.PLAY,getString(R.string.mj_restart),true) { openSlot(current.slot,fresh=true) })
        if(game.history.isNotEmpty()) row.addView(panelButton(MahjongIcon.UNDO,getString(R.string.mj_undo),false) { undoMove() },
            LinearLayout.LayoutParams(-2,dp(52)).apply { marginStart=dp(10) })
        panel.addView(row,LinearLayout.LayoutParams(-2,-2).apply { topMargin=dp(12) })
        frame.addView(panel,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
        stuckPanel=panel
        if(effects && ValueAnimator.areAnimatorsEnabled()) {
            panel.scaleX=.85f; panel.scaleY=.85f; panel.alpha=0f
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
        }
    }

    private fun hideStuck() {
        stuckPanel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        stuckPanel=null
    }

    private fun panelButton(symbol:MahjongIcon,text:String,primary:Boolean,action:()->Unit)=LinearLayout(this).apply {
        gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),0,dp(18),0); minimumHeight=dp(52)
        val tint=if(primary) androidx.core.content.ContextCompat.getColor(this@MahjongActivity,R.color.audio_on_accent) else ink
        background=if(primary) GradientDrawable().apply { setColor(accent); cornerRadius=AppearanceStyle.corner(this@MahjongActivity,20f) } else panel()
        isClickable=true; isFocusable=true; contentDescription=text
        setOnClickListener { action() }
        addView(ImageView(this@MahjongActivity).apply {
            setImageDrawable(MahjongIconDrawable(symbol,tint)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(22),dp(22)).apply { marginEnd=dp(8) })
        addView(label(text,16f,tint).apply { setTypeface(typeface,Typeface.BOLD); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO })
    }

    private fun onMove(result:MahjongGame.Result) {
        val current=run ?: return
        cancelHint()
        val matched=result==MahjongGame.Result.MATCHED
        if(prefs.getBoolean("sound",true)) {
            if(matched) chimes?.play(current.game.combo)
            else if(result==MahjongGame.Result.SELECTED || result==MahjongGame.Result.MOVED || result==MahjongGame.Result.TRAY_FULL) chimes?.click()
        }
        updateHud()
        if(current.game.complete) {
            store.finish(current)
            // finish() has just written the completed run: a pending move save is obsolete.
            saveHandler.removeCallbacks(delayedSave); savePending=false
            handler.removeCallbacksAndMessages(null)
            if(effects) handler.postDelayed({ if(run===current && screen=="board" && !isFinishing && !isDestroyed) showReward() },900)
            else showReward()
        } else {
            if(result==MahjongGame.Result.TRAY_FULL || (matched || result==MahjongGame.Result.SELECTED &&
                current.game.mode==MahjongMode.TRIPLE) && current.game.stuck) {
                // Le panneau attend que la dernière tuile se soit posée.
                handler.postDelayed({ if(run===current && screen=="board" && current.game.stuck) showStuck() },if(effects) 450L else 0L)
            }
            else if((matched || result==MahjongGame.Result.MOVED) &&
                (current.game.mode==MahjongMode.SLIDE || current.game.mode==MahjongMode.TEN)) requestHint(show=false)
            // Ice focus and its rollback origin are durable, including a blocked swipe
            // that selected a different tile. Other pair selections remain transient.
            if(result==MahjongGame.Result.MATCHED || result==MahjongGame.Result.MOVED ||
                result==MahjongGame.Result.MISMATCH || result==MahjongGame.Result.TRAY_FULL ||
                (result==MahjongGame.Result.SELECTED && current.game.mode==MahjongMode.TRIPLE) ||
                (current.game.mode==MahjongMode.SLIDE && result!=MahjongGame.Result.IGNORED)) saveSoon()
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
                popup.setOnDismissListener { if(screen=="board" && stuckPanel==null) board?.resume() }
                popup.show()
            }
    }

    private fun pulse(view:View) {
        if(!effects || !ValueAnimator.areAnimatorsEnabled()) return
        view.animate().cancel(); view.scaleX=1.12f; view.scaleY=1.12f
        view.animate().scaleX(1f).scaleY(1f).setDuration(350).start()
    }
    private fun updateHud() {
        val current=run?.game ?: return
        if(screen!="board") return
        progress.progress=current.tiles.size-current.remaining
        undoButton.isEnabled=!boardBusy && current.history.isNotEmpty() && !current.complete
        undoButton.alpha=if(undoButton.isEnabled) 1f else .4f
        hintBadge.text=getString(R.string.mj_number,current.hintsLeft)
        hintButton.alpha=if(current.hintsLeft>0) 1f else .4f
        hintButton.contentDescription=getString(R.string.mj_hints_left,current.hintsLeft)
    }

    private fun showReward() {
        val current=run ?: return
        val reward=current.reward ?: store.finish(current) ?: return
        releaseBoard(); screen="reward"
        val root=root().apply { background=MahjongTableDrawable(this@MahjongActivity,store.table) }; setContentView(root)
        val title=when { reward.gift -> R.string.mj_big_gift; reward.styles.isEmpty() -> R.string.mj_victory_short; else -> R.string.mj_new_porcelain }
        root.full(header(getString(title),{ showMenu() },titleColor=Color.WHITE) { gardenHelp() },0)
        val amount=label(getString(R.string.mj_pearl_gain,reward.pearls),32f,accent).apply {
            gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD)
        }
        root.full(amount,6)
        root.full(label(getString(R.string.mj_result_short,current.game.score,current.game.peakCombo),15f,0xddffffff.toInt()).apply { gravity=Gravity.CENTER },0)
        root.full(label(getString(R.string.mj_reward_breakdown,reward.stars,current.bonusMultiplier,
            if(reward.gift) 100 else 0),13f,0xccffffff.toInt()).apply { gravity=Gravity.CENTER },0)
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
        collectionReturn=screen; collectionTab=0; save(); releaseBoard(); showCollection()
    }
    private fun leaveCollection() {
        when(collectionReturn) {
            "board" -> if(run!=null) showBoard() else showMenu()
            "reward" -> if(run!=null) showReward() else showMenu()
            else -> showMenu()
        }
    }

    // Onglets de la collection : 0 textures, 1 porcelaines, 2 fonds.
    private fun itemCount(tab:Int)=if(tab==2) MahjongAtlas.TABLES else MahjongStyles.COUNT
    private fun names(tab:Int):Array<String> = when(tab) { 0 -> textureNames; 1 -> styleNames; else -> tableNames }
    private fun owned(tab:Int)=when(tab) { 0 -> store.unlockedTextures; 1 -> store.unlocked; else -> store.unlockedTables }
    private fun equipped(tab:Int)=when(tab) { 0 -> store.texture; 1 -> store.style; else -> store.table }
    private fun price(tab:Int)=when(tab) { 0 -> store.nextTexturePrice; 1 -> store.nextPrice; else -> store.nextTablePrice }
    private fun equip(tab:Int,id:Int)=when(tab) { 0 -> store.equipTexture(id); 1 -> store.equip(id); else -> store.equipTable(id) }
    private fun purchase(tab:Int,id:Int)=when(tab) { 0 -> store.purchaseTexture(id); 1 -> store.purchase(id); else -> store.purchaseTable(id) }

    private fun showCollection() {
        screen="collection"
        val tab=collectionTab
        val names=names(tab); val owned=owned(tab); val equipped=equipped(tab); val price=price(tab)
        val root=root(); setContentView(root)
        root.full(header(getString(R.string.mj_collection_short),{ leaveCollection() }) {
            showGameRules(R.string.mj_collection,R.string.mj_collection_help)
        },0)
        val tabs=LinearLayout(this)
        val tabTitles=intArrayOf(R.string.mj_textures,R.string.mj_porcelains,R.string.mj_tables)
        val tabTags=arrayOf("tab:textures","tab:porcelains","tab:tables")
        for(index in 0..2) {
            tabs.addView(label(getString(tabTitles[index]),16f).apply {
                gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD)
                background=panel(if(tab==index) MahjongArt.mix(surface,accent,.24f) else surface)
                tag=tabTags[index]
                isSelected=tab==index; isClickable=true; isFocusable=true
                setOnClickListener { collectionTab=index; showCollection() }
            },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginStart=dp(3); marginEnd=dp(3) })
        }
        root.full(tabs,6)
        val selection=intArrayOf(R.string.mj_texture_selection,R.string.mj_porcelain_selection,R.string.mj_table_selection)[tab]
        root.full(label(getString(selection,names[equipped]),13f,muted),2)
        root.full(label(getString(R.string.mj_collection_count,owned.size,itemCount(tab),store.pearls),16f).apply { gravity=Gravity.CENTER },0)
        root.full(collectionProgress(tab),4)
        val scroll=ScrollView(this); val body=column(); scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val cards=(0 until itemCount(tab)).map { id ->
            val card=object:FrameLayout(this) {
                override fun onMeasure(w:Int,h:Int) {
                    super.onMeasure(w,MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(w)*1.55f).toInt(),MeasureSpec.EXACTLY))
                }
            }.apply {
                background=panel(if(id==equipped) MahjongArt.mix(surface,accent,.18f) else surface)
                clipToOutline=true; isClickable=true; isFocusable=true
                tag="${tabTags[tab].removePrefix("tab:")}:$id"
                contentDescription=getString(if(id in owned) R.string.mj_style_available else R.string.mj_style_waiting,names[id])
                setOnClickListener {
                    if(id in owned) {
                        equip(tab,id); showCollection()
                        Toast.makeText(this@MahjongActivity,getString(R.string.mj_visual_applied,names[id]),Toast.LENGTH_SHORT).show()
                    } else showStylePreview(id,tab)
                }
            }
            card.addView(object:View(this) {
                private val art=MahjongArt(this@MahjongActivity)
                override fun onDraw(c:Canvas) {
                    if(tab==2) {
                        // Le paysage en vignette, avec deux tuiles posées dessus pour juger le contraste.
                        art.table(c,width.toFloat(),height.toFloat(),id,thumbnail=true)
                        val w=width*.3f; val h=w*1.33f
                        art.tile(c,width*.5f-w*1.02f+w*.13f,height*.38f-h/2,w,h,31,accent,style=store.style,texture=store.texture)
                        art.tile(c,width*.5f+w*.02f+w*.13f,height*.38f-h/2,w,h,34,accent,style=store.style,texture=store.texture)
                        return
                    }
                    val textures=tab==0
                    val w=width*.56f; val h=w*1.33f
                    val material=if(textures) id else store.texture
                    val icon=if(textures) store.style else id
                    art.tile(c,width*.2f,height*.07f,w,h,if(textures) 4 else 34,accent,style=icon,texture=material)
                    val small=width*.30f
                    c.save(); c.rotate(10f,width*.70f,height*.39f)
                    art.tile(c,width*.6f,height*.29f,small,small*1.33f,if(textures) 32 else 34,accent,style=icon,texture=material)
                    c.restore()
                }
            },FrameLayout.LayoutParams(-1,-1))
            val onImage=tab==2
            val caption=column().apply {
                if(onImage) background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x00000000,0xcc000000.toInt()))
                setPadding(0,dp(14),0,dp(5))
            }
            caption.addView(label(names[id],12f,if(onImage) Color.WHITE else ink).apply { gravity=Gravity.CENTER; maxLines=2; setTypeface(typeface,Typeface.BOLD) })
            caption.addView(label(if(id in owned) getString(if(id==equipped) R.string.mj_equipped else R.string.mj_apply_visual)
                else getString(R.string.mj_price,price),11f,if(id in owned) accent else if(onImage) 0xddffffff.toInt() else muted).apply { gravity=Gravity.CENTER })
            card.addView(caption,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
            val mark=ImageView(this).apply {
                setImageDrawable(MahjongIconDrawable(if(id==equipped) MahjongIcon.CHECK else MahjongIcon.LOCK,
                    if(id==equipped) accent else if(onImage) Color.WHITE else muted))
                setPadding(dp(5),dp(5),dp(5),dp(5))
                visibility=if(id==equipped || id !in owned) View.VISIBLE else View.GONE
            }
            card.addView(mark,FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP or Gravity.END))
            card
        }
        addGrid(body,cards,if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) { if(resources.configuration.screenWidthDp>=700) 6 else 5 } else if(resources.configuration.screenWidthDp>=600) 4 else 3)
    }
    private fun showStylePreview(id:Int,tab:Int) {
        val names=names(tab); val price=price(tab)
        val body=column().apply { setPadding(dp(18),dp(12),dp(18),dp(12)) }
        body.addView(object:View(this) {
            private val art=MahjongArt(this@MahjongActivity)
            override fun onDraw(c:Canvas) {
                if(tab==2) art.table(c,width.toFloat(),height.toFloat(),id,thumbnail=true)
                val w=minOf(width/4.5f,height*.55f); val h=w*1.33f
                repeat(3) { i -> art.tile(c,width/2f+(i-1)*w*1.12f-w/2+w*.06f,(height-h)/2,w,h,
                    when(tab) { 0 -> intArrayOf(4,32,22)[i]; 1 -> intArrayOf(34,31,34)[i]; else -> intArrayOf(0,34,27)[i] },accent,
                    style=if(tab==1) id else store.style,texture=if(tab==0) id else store.texture) }
            }
        },LinearLayout.LayoutParams(-1,dp(if(tab==2) 220 else 160)))
        val help=intArrayOf(R.string.mj_texture_preview_help,R.string.mj_porcelain_preview_help,R.string.mj_table_preview_help)[tab]
        body.full(label(getString(help),15f).apply { gravity=Gravity.CENTER })
        body.full(label(getString(R.string.mj_purchase_balance,store.pearls,price),15f).apply { gravity=Gravity.CENTER })
        val scroll=ScrollView(this).apply { addView(body) }
        dialog=AlertDialog.Builder(this).setTitle(names[id]).setView(scroll)
            .setNegativeButton(R.string.close,null)
            .setPositiveButton(getString(R.string.mj_buy,price)) { _,_ ->
                if(purchase(tab,id)) {
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
    private fun save() {
        if(savePending) { saveHandler.removeCallbacks(delayedSave); savePending=false }
        run?.let(store::save)
    }
    private fun saveSoon() {
        if(savePending) return
        savePending=true; saveHandler.postDelayed(delayedSave,1500)
    }
    private fun releaseBoard() {
        boardRevision++; boardTask?.cancel(true); boardTask=null; cancelHint()
        workHandler.removeCallbacksAndMessages(null)
        cachedHintState=null; cachedHint=null; boardBusy=false; boardControls=null; loading=null
        handler.removeCallbacksAndMessages(null)
        stuckPanel=null; boardFrame=null
        board?.pause(); board=null
    }
    override fun onPause() {
        cancelHint(); boardRevision++; boardTask?.cancel(true); boardTask=null; worker.purge()
        if(boardBusy) setBoardBusy(false)
        handler.removeCallbacksAndMessages(null); board?.pause(); save(); super.onPause()
    }
    override fun onResume() {
        super.onResume()
        val pending=loading
        if(screen=="loading" && boardTask==null && pending!=null) openSlot(pending.slot,pending.fresh,pending.restoring)
        else if(screen=="board" && run?.game?.complete==true) showReward()
        else { if(screen=="board") updateHud(); if(stuckPanel==null) board?.resume() }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        save(); outState.putString("screen",screen); outState.putString("active_slot",run?.slot)
        loading?.let { outState.putString("loading_slot",it.slot); outState.putBoolean("loading_fresh",it.fresh); outState.putBoolean("loading_restoring",it.restoring) }
        outState.putInt("collection_tab",collectionTab); outState.putString("collection_return",collectionReturn)
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
