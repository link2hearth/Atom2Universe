package com.Atom2Universe.app.games.golf.classic

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.golf.GolfAudio
import com.Atom2Universe.app.games.golf.GolfUi
import com.Atom2Universe.app.games.golf.GolfMenus
import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.ClassicFrame
import com.Atom2Universe.app.games.golf.classic.render.OverviewView
import com.Atom2Universe.app.games.golf.classic.render.ClassicSurface
import com.Atom2Universe.app.games.golf.classic.render.GolferAppearance
import com.Atom2Universe.app.games.golf.classic.render.GolferPose
import com.Atom2Universe.app.games.golf.classic.render.LiveShotCamera
import com.Atom2Universe.app.games.golf.classic.render.ShotCameraMode
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlin.math.*

/** How long the frame must stay unchanged before drawing at half rate: the camera glides settle well within it. */
private const val CALM_NANOS = 1_500_000_000L

/** Calm and untouched for this long: the scenery is drawn about twelve times a second. */
private const val IDLE_NANOS = 10_000_000_000L

/** Easy mode: the cup is this much wider. */
private const val EASY_CUP = 1.5f

/** Stroke play with an immutable bag. Simulation and UI share the main thread; GL gets snapshots. */
class ClassicGolfActivity : ThemedActivity(), Choreographer.FrameCallback {
    private lateinit var root: FrameLayout
    private lateinit var ui: GolfUi
    private lateinit var audio: GolfAudio
    private val prefs by lazy { getSharedPreferences("classic_golf_v2", Context.MODE_PRIVATE) }
    private var course = ClassicCourses.all.first()
    private var roundLength = ClassicRoundLength.FULL
    private val roundHoles get() = if (round) roundLength.holes(course) else course.holes
    /** Number and par of each hole of the round; a generated course reads them from its seed without building its holes. */
    private fun roundPars():List<Pair<Int,Int>> = course.random?.let { spec ->
        val first=if(round)roundLength.startIndex else 0
        List(roundHoles.size){ i -> (first+i+1) to RandomGolfGenerator.parOf(spec,first+i+1) }
    } ?: roundHoles.map{ it.number to it.par }
    private val progress get() = getSharedPreferences(roundLength.progressName(course), Context.MODE_PRIVATE)
    private var practiceMenu: ClassicCourseDefinition? = null
    private var surface: ClassicSurface? = null
    private var golfer = GolferAppearance()
    private var game: ClassicGame? = null
    private var resumed = false
    private var dialog: AlertDialog? = null
    private var lastFrame = 0L
    private var lastHud = 0L
    private var lastKnock = 0L
    private var lastState = GolfState.READY
    private var previousPenalty = 0
    private var round = true
    private var completed = false
    private val scores = ArrayList<Int>()
    private var overview: OverviewView? = null
    private var zoom = 1f
    private var muted = false
    private var preview = ShotPreview.NONE
    private var previewDirty = true
    private var label: TextView? = null
    private var lieLabel: TextView? = null
    private var clubBadge: ClassicClubBadge? = null
    private var clubUp: GolfIcon? = null
    private var clubDown: GolfIcon? = null
    private var clubPicker: View? = null
    private val swing = GolfSwing()
    private var overlay: ClassicShotOverlay? = null
    private var spinPad: ClassicSpinPad? = null
    private var feedback: TextView? = null
    private var pendingStrike: Pair<Float, Float>? = null
    private var strikePower = 1f
    private var pullFraction = 0f
    private var swingTime = -1f
    private var feedbackTime = 0f
    private var lastSubmitted: ClassicFrame? = null
    private var lastChange = 0L
    private var sceneReady = false
    private var sceneSpinner: ProgressBar? = null
    private var miniMap: ClassicMap? = null
    private var bottom: View? = null
    private var result: View? = null
    private var lastPreview = 0L
    private val clubNames by lazy { resources.getStringArray(R.array.classic_clubs) }
    private val lieNames by lazy { resources.getStringArray(R.array.classic_lies) }
    private var windRose: ClassicWindRose? = null
    private var gridShown = true
    private var easy = false
    private var lieView: ClassicLieView? = null
    private var lieAlong: ClassicLieView? = null
    private var spinCentre: GolfIcon? = null
    private var gridButton: GolfIcon? = null
    private var recording: GolfReplayRecorder? = null
    private var lastReplay: GolfReplay? = null
    private var shotChoices: View? = null
    private var replayButton: GolfIcon? = null
    private var replayRequested=false
    private var replayView: GolfReplayView? = null
    private var hiddenGameViews = emptyList<Pair<View,Int>>()
    private val replayStore by lazy { GolfReplayStore(java.io.File(filesDir,"golf_replays")) }
    private val replayIo = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var screenGeneration=0
    private val liveCamera=LiveShotCamera()
    private var cameraButton:GolfIcon?=null
    private var cameraToast:Toast?=null
    private var liveFastForward=false
    private var speedBadge:TextView?=null
    private val liveCameraModes=listOf(ShotCameraMode.ORBIT,ShotCameraMode.SIDE,ShotCameraMode.AERIAL,
        ShotCameraMode.ARRIVAL,ShotCameraMode.CHASE,ShotCameraMode.AUTO)
    private var lastTouch=0L
    /** The finished hole's last image has gone to the scene, which then stays still under the result. */
    private var finalFrameSent=false
    /** The guide is simulated on this copy of the game, on its own thread (thousands of steps per guide). */
    private var previewGame:ClassicGame?=null
    @Volatile private var previewAsked:PreviewAsk?=null
    /** Number of the last guide asked, and of the last one shown (or thrown away when the shot changed for good). */
    private var previewSeq=0L
    private var previewShown=0L
    private val previewWorker=java.util.concurrent.Executors.newSingleThreadExecutor{ task -> Thread(task,"golf-preview").apply{isDaemon=true} }
    /** Builds the first hole of each generated course for the menu's posters. */
    private val posterWorker=java.util.concurrent.Executors.newSingleThreadExecutor{ task -> Thread(task,"golf-posters").apply{isDaemon=true} }

    /** What the guide is asked for: the shot as set up when it was asked. */
    private class PreviewAsk(val seq:Long,val ball:GolfPoint,val club:GolfClub,val aim:Float,val spinX:Float,val spinY:Float,
        val power:Float,val simulatePutt:Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        // The clubs' calibration tables take a moment: made before the first guide or shot needs them.
        previewWorker.execute{GolfCalibration.warmUp()}
        ui=GolfUi(this); audio=GolfAudio(this); muted=prefs.getBoolean("muted",false);easy=prefs.getBoolean("easy",false)
        liveCamera.select(ShotCameraMode.entries.firstOrNull{it.name==prefs.getString("live_camera",null)}?:ShotCameraMode.ORBIT)
        prefs.edit().putString("live_camera",liveCamera.mode.name).apply()
        course=ClassicCourses.find(prefs.getString("selected_course",null))
        golfer=GolferAppearance(prefs.getBoolean("golfer_female",false),
            prefs.getInt("golfer_outfit",0).coerceIn(0,2),prefs.getInt("golfer_skin",0).coerceIn(0,2))
        root=FrameLayout(this).apply {
            background=com.Atom2Universe.app.AppearanceStyle.screen(this@ClassicGolfActivity)
            setOnApplyWindowInsetsListener { v,i ->
                val cut=i.displayCutout
                v.setPadding(cut?.safeInsetLeft?:0,cut?.safeInsetTop?:0,cut?.safeInsetRight?:0,cut?.safeInsetBottom?:0); i
            }
        }
        setContentView(root)
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> layoutGame() }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){ override fun handleOnBackPressed(){ if(replayView!=null)closeReplay() else if(game!=null||practiceMenu!=null)showMenu() else finish() } })
        showMenu()
        GolfMenus.enter(root.getChildAt(0))
    }

    override fun onResume(){ super.onResume(); resumed=true; lastTouch=System.nanoTime(); finalFrameSent=false; if(!muted)audio.resume(); surface?.onResume();if(replayView!=null){if(dialog?.isShowing!=true)replayView?.resume()}else{startFrames()} }
    override fun onPause(){ resumed=false;liveFastForward=false;overlay?.resetCameraTaps();stopFrames(); save(); replayView?.pause();surface?.onPause(); audio.pause(); super.onPause() }
    override fun onDestroy(){ replayView?.release();replayView=null;closeSurface(); dialog?.dismiss(); replayIo.shutdown();previewWorker.shutdown();posterWorker.shutdown();audio.close(); super.onDestroy() }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{ lastTouch=System.nanoTime(); return super.dispatchTouchEvent(event) }
    /** The screen stays on while a hole or a replay is shown, not in the menus. */
    private fun keepScreenOn(on:Boolean){
        if(on)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun startFrames(){ lastFrame=0; Choreographer.getInstance().removeFrameCallback(this); if(resumed&&game!=null)Choreographer.getInstance().postFrameCallback(this) }
    private fun stopFrames(){ Choreographer.getInstance().removeFrameCallback(this); lastFrame=0 }
    private fun closeSurface(){
        stopFrames(); surface?.release(); surface?.onPause(); surface=null
    }

    private fun showMenu() {
        screenGeneration++;recording=null;lastReplay=null;shotChoices=null;replayRequested=false
        save(); closeSurface(); game=null; previewGame=null; previewAsked=null; previewShown=previewSeq; completed=false; practiceMenu=null; root.removeAllViews(); result=null
        keepScreenOn(false)
        val col=ui.column().apply{setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(20))}
        val head=ui.row()
        head.addView(icon(GolfIcon.Kind.BACK,R.string.golf_back){finish()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        head.addView(ui.text(getString(R.string.classic_title),26f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        head.addView(icon(GolfIcon.Kind.PERSON,R.string.classic_golfer_title){GolfMenus.wardrobe(this)},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        head.addView(icon(GolfIcon.Kind.HELP,R.string.golf_info){help()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        col.addView(head)
        col.addView(ui.secondary(getString(R.string.golf_replay_library)){showReplayLibrary()},LinearLayout.LayoutParams(-1,-2))
        col.addView(Switch(this).apply{
            text=getString(R.string.classic_easy_mode);textSize=16f;setTextColor(ui.palette.text);isChecked=easy
            setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6))
            setOnCheckedChangeListener{_,on->easy=on;prefs.edit().putBoolean("easy",on).apply()}
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=ui.dp(6)})
        val wide=resources.configuration.screenWidthDp>=600
        val tab=prefs.getInt("menu_tab",0).coerceIn(0,2)
        val tabs=ui.row().apply{setPadding(0,ui.dp(14),0,ui.dp(2))}
        listOf(R.string.golf_tab_courses,R.string.golf_tab_minigolf,R.string.golf_tab_seeds).forEachIndexed { i,label ->
            val button=(if(i==tab)ui.primary(getString(label)){} else ui.secondary(getString(label)){prefs.edit().putInt("menu_tab",i).apply();showMenu()}).apply{
                minWidth=0;minHeight=ui.dp(44);setPadding(ui.dp(8),ui.dp(10),ui.dp(8),ui.dp(10))
            }
            tabs.addView(button,LinearLayout.LayoutParams(0,-2,1f).apply{if(i>0)leftMargin=ui.dp(8)})
        }
        col.addView(tabs)
        if(tab==2)col.addView(ui.primary(getString(R.string.golf_random_new)){newRandomCourse()},LinearLayout.LayoutParams(-1,-2).apply{topMargin=ui.dp(10)})
        val cards=ui.column()
        val shown=when(tab){0->ClassicCourses.all.filter{!it.mini};1->ClassicCourses.all.filter{it.mini};else->randomStore.all().map{ClassicCourses.random(it)}}
        shown.chunked(if(wide)2 else 1).forEach { definitions ->
            val row=if(wide)ui.row().apply{gravity=Gravity.TOP}else cards
            definitions.forEach { definition ->
                row.addView(courseTile(definition),LinearLayout.LayoutParams(if(wide)0 else -1,-2,if(wide)1f else 0f).apply{
                    setMargins(ui.dp(4),ui.dp(12),ui.dp(4),0)
                })
            }
            if(wide) {
                if(definitions.size==1)row.addView(View(this),LinearLayout.LayoutParams(0,0,1f))
                cards.addView(row)
            }
        }
        col.addView(cards)
        root.addView(ScrollView(this).apply{addView(col)},FrameLayout.LayoutParams(-1,-1))
    }

    private val randomStore by lazy{RandomCourseStore(this)}
    private fun lengthsOf(d:ClassicCourseDefinition)=if(d.random!=null)listOf(ClassicRoundLength.WHOLE)else ClassicRoundLength.STANDARD
    private fun titleOf(d:ClassicCourseDefinition)=d.random?.let{randomName(it.seed)}?:getString(d.titleRes)
    private fun roundTitle(d:ClassicCourseDefinition,length:ClassicRoundLength)=
        if(d.random!=null)titleOf(d) else getString(R.string.golf_section_title,titleOf(d),getString(length.labelRes))
    private fun holeNamesOf(d:ClassicCourseDefinition):List<String> =
        if(d.random!=null)List(d.holes.size){getString(R.string.golf_random_hole,it+1)} else resources.getStringArray(d.holeNamesRes).toList()
    /** A noun and an adjective drawn from the seed ("Golden Constellation"); the French adjective agrees with the noun. */
    private fun randomName(seed:Long):String{
        val nouns=resources.getStringArray(R.array.golf_random_nouns);val adjectives=resources.getStringArray(R.array.golf_random_adjectives)
        val (n,a)=RandomGolfNames.pick(seed,nouns.size,adjectives.size)
        val feminine=nouns[n].substringAfter('|',"")=="f"
        val adjective=adjectives[a].let{if(feminine)it.substringAfter('|',it) else it.substringBefore('|')}
        return getString(R.string.golf_random_name,nouns[n].substringBefore('|'),adjective)
    }

    private fun randomInfo(d:ClassicCourseDefinition):String{
        val spec=d.random?:return ""
        return getString(R.string.golf_random_info,resources.getStringArray(R.array.golf_random_counts)[RandomGolfSpec.HOLE_COUNTS.indexOf(spec.holeCount)],
            d.par,resources.getStringArray(R.array.golf_random_themes)[spec.theme?.let{it.ordinal+1}?:0],spec.seed)
    }

    /** The poster of a generated course: the plan of its first hole under its name. The hole is built off the UI thread. */
    private fun randomPoster(d:ClassicCourseDefinition):FrameLayout=FrameLayout(this).apply{
        background=ui.shape(ui.palette.surface,22f,null);clipToOutline=true
        val poster=this;val generation=screenGeneration
        posterWorker.execute {
            val first=d.holes.first()
            runOnUiThread{ if(!isDestroyed&&generation==screenGeneration)poster.addView(ClassicMap(this@ClassicGolfActivity,first),0,FrameLayout.LayoutParams(-1,-1)) }
        }
        addView(View(this@ClassicGolfActivity).apply{
            background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,0xE6102C27.toInt()))
        },FrameLayout.LayoutParams(-1,ui.dp(110),Gravity.BOTTOM))
        addView(ui.column().apply{
            setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(14))
            addView(ui.text(titleOf(d),23f,Color.WHITE,true))
            addView(ui.text(randomInfo(d),14f,Color.WHITE).apply{setPadding(0,ui.dp(4),0,0)})
        },FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
    }

    private fun newRandomCourse(){
        val start=randomStore.last
        fun newSeed()=kotlin.random.Random.nextLong(100_000L,1_000_000L)
        val col=ui.column().apply{setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(12))}
        col.addView(ui.text(getString(R.string.golf_random_seed),14f,bold=true).apply{setPadding(0,ui.dp(8),0,ui.dp(4))})
        val seedField=EditText(this).apply{
            inputType=android.text.InputType.TYPE_CLASS_NUMBER;filters=arrayOf(android.text.InputFilter.LengthFilter(9));setSingleLine()
            textSize=18f;setTextColor(ui.palette.text);setText((start?.seed?:newSeed()).toString())
            contentDescription=getString(R.string.golf_random_seed)
        }
        val seedRow=ui.row()
        seedRow.addView(seedField,LinearLayout.LayoutParams(0,-2,1f))
        seedRow.addView(ui.secondary(getString(R.string.golf_random_roll)){seedField.setText(newSeed().toString())},LinearLayout.LayoutParams(-2,-2).apply{leftMargin=ui.dp(8)})
        col.addView(seedRow)
        fun picker(label:Int,items:Int,selected:Int):Spinner{
            col.addView(ui.text(getString(label),14f,bold=true).apply{setPadding(0,ui.dp(14),0,ui.dp(2))})
            val spinner=com.Atom2Universe.app.util.ImmersiveSpinner(this).apply{
                contentDescription=getString(label)
                adapter=ArrayAdapter(this@ClassicGolfActivity,R.layout.item_spinner_selected,resources.getStringArray(items)).apply{setDropDownViewResource(R.layout.item_spinner_dropdown)}
                setSelection(selected)
            }
            col.addView(spinner,LinearLayout.LayoutParams(-1,ui.dp(48)))
            return spinner
        }
        val counts=picker(R.string.golf_random_holes,R.array.golf_random_counts,RandomGolfSpec.HOLE_COUNTS.indexOf(start?.holeCount ?: 9).coerceAtLeast(0))
        val themes=picker(R.string.golf_random_theme,R.array.golf_random_themes,start?.theme?.let{it.ordinal+1}?:0)
        showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(R.string.golf_random_new)
            .setView(ScrollView(this).apply{addView(col)}).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.golf_random_create){_,_->
                val seed=seedField.text.toString().toLongOrNull()?.coerceIn(0L,RandomGolfSpec.MAX_SEED)?:newSeed()
                val spec=RandomGolfSpec(seed,RandomGolfSpec.HOLE_COUNTS[counts.selectedItemPosition],
                    themes.selectedItemPosition.let{if(it==0)null else RandomGolfTheme.entries[it-1]})
                randomStore.add(spec);randomStore.last=spec
                chooseRound(ClassicCourses.random(spec),ClassicRoundLength.WHOLE)
            }.create())
    }

    private fun courseTile(definition:ClassicCourseDefinition):View {
        val tile=ui.column().apply{background=ui.shape(ui.palette.surface,22f);clipToOutline=true}
        val poster=if(definition.random!=null)randomPoster(definition) else GolfMenus.photoCard(this,definition.id,titleOf(definition))
        val tools=ui.row().apply{gravity=Gravity.END;setPadding(ui.dp(8),ui.dp(8),ui.dp(8),0)}
        definition.random?.let{spec->
            tools.addView(icon(GolfIcon.Kind.STAR,R.string.golf_random_favourite){randomStore.toggleFavourite(spec);showMenu()}.apply{highlighted=randomStore.isFavourite(spec)},
                LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply{rightMargin=ui.dp(6)})
        }
        tools.addView(icon(GolfIcon.Kind.FLAG,R.string.classic_practice){showPractice(definition)},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        tools.addView(icon(GolfIcon.Kind.CARD,R.string.golf_scorecard){showCourseRecords(definition)},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply{leftMargin=ui.dp(6)})
        poster.addView(tools,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        tile.addView(poster,LinearLayout.LayoutParams(-1,ui.dp(180)))
        val buttons=ui.row().apply{setPadding(ui.dp(10),ui.dp(10),ui.dp(10),ui.dp(10))}
        lengthsOf(definition).forEachIndexed { index,length ->
            val saved=getSharedPreferences(length.progressName(definition),Context.MODE_PRIVATE)
            val active=saved.getBoolean("active",false)
            val button=ui.primary(if(active)getString(R.string.golf_resume_section,getString(length.labelRes))else getString(length.labelRes)) {
                chooseRound(definition,length)
            }.apply{
                minWidth=0;setPadding(ui.dp(8),ui.dp(12),ui.dp(8),ui.dp(12))
                contentDescription=getString(R.string.golf_round_accessible,titleOf(definition),text)
            }
            buttons.addView(button,LinearLayout.LayoutParams(0,-2,1f).apply{if(index>0)leftMargin=ui.dp(8)})
        }
        tile.addView(buttons)
        return tile
    }

    private fun selectCourse(definition:ClassicCourseDefinition,length:ClassicRoundLength){
        course=definition;roundLength=length
        prefs.edit().putInt("menu_tab",if(definition.random!=null)2 else if(definition.mini)1 else 0).apply()
        prefs.edit().putString("selected_course",course.id).apply()
    }

    private fun chooseRound(definition:ClassicCourseDefinition,length:ClassicRoundLength){
        selectCourse(definition,length)
        if(!progress.getBoolean("active",false)){startRound(false);return}
        showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(roundTitle(course,length))
            .setPositiveButton(R.string.golf_continue){_,_->startRound(true)}
            .setNeutralButton(R.string.golf_new_course){_,_->
                showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setMessage(R.string.classic_replace)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.golf_play){_,_->startRound(false)}.create())
            }.setNegativeButton(android.R.string.cancel,null).create())
    }

    private fun showCourseRecords(definition:ClassicCourseDefinition){
        val col=ui.column().apply{setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(16))}
        lengthsOf(definition).forEach { length ->
            val saved=getSharedPreferences(length.progressName(definition),Context.MODE_PRIVATE)
            val best=saved.getInt("best",0)
            col.addView(ui.text(getString(length.labelRes),19f,bold=true).apply{setPadding(0,ui.dp(14),0,ui.dp(8))})
            col.addView(ui.text(if(best>0)getString(R.string.golf_course_best,best,relative(best-length.par(definition)))
                else getString(R.string.golf_no_record),15f,ui.palette.secondary))
            val stored=saved.getString("scores","").orEmpty().split(',').mapNotNull{it.toIntOrNull()}.take(length.count(definition))
            if(stored.isNotEmpty())col.addView(ui.secondary(getString(R.string.golf_scorecard)){
                dialog?.dismiss();selectCourse(definition,length);round=true;scores.clear();scores+=stored;scorecard(false)
            },LinearLayout.LayoutParams(-1,-2).apply{topMargin=ui.dp(8)})
        }
        val builder=com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(titleOf(definition))
            .setView(ScrollView(this).apply{addView(col)}).setPositiveButton(R.string.golf_done,null)
        definition.random?.let{spec->builder.setNeutralButton(R.string.golf_random_delete){_,_->
            showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setMessage(R.string.golf_random_delete_confirm)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.golf_random_delete){_,_->randomStore.remove(spec);showMenu()}.create())
        }}
        showDialog(builder.create())
    }

    private fun showPractice(definition:ClassicCourseDefinition){
        practiceMenu=definition
        root.removeAllViews()
        keepScreenOn(false)
        val col=ui.column().apply{setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(20))}
        val head=ui.row()
        head.addView(icon(GolfIcon.Kind.BACK,R.string.golf_back){showMenu()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        head.addView(ui.text(getString(R.string.golf_practice),23f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        col.addView(head)
        col.addView(ui.text(titleOf(definition),15f,ui.palette.secondary).apply{setPadding(ui.dp(8),ui.dp(8),0,ui.dp(10))})
        val names=holeNamesOf(definition)
        val columns=(resources.configuration.screenWidthDp/155).coerceIn(2,6)
        definition.holes.chunked(columns).forEach { holes ->
            val row=ui.row().apply{gravity=Gravity.TOP}
            holes.forEach { h ->
                val tile=ui.column().apply{
                    background=ui.shape(ui.palette.surface,18f);clipToOutline=true
                    isClickable=true;isFocusable=true
                    contentDescription=getString(R.string.classic_hole_choice,h.number,names[h.number-1],h.par,h.displayLength.roundToInt())
                    foreground=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),null,ui.shape(Color.WHITE,18f,null))
                    setOnClickListener{selectCourse(definition,lengthsOf(definition).last());round=false;scores.clear();play(h.number-1)}
                }
                val map=FrameLayout(this)
                map.addView(ClassicMap(this,h),FrameLayout.LayoutParams(-1,-1))
                map.addView(ui.text(getString(R.string.golf_hole_number,h.number),16f,Color.WHITE,true).apply{
                    background=ui.shape(GolfUi.SCENE_PLATE,12f,null);setPadding(ui.dp(9),ui.dp(5),ui.dp(9),ui.dp(5))
                },FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply{setMargins(ui.dp(6),ui.dp(6),0,0)})
                tile.addView(map,LinearLayout.LayoutParams(-1,ui.dp(155)))
                tile.addView(ui.text(names[h.number-1],14f,bold=true).apply{
                    maxLines=2;minLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(ui.dp(10),ui.dp(8),ui.dp(10),0)
                })
                tile.addView(ui.text(getString(R.string.golf_hole_detail,h.par,h.displayLength.roundToInt()),12f,ui.palette.secondary).apply{setPadding(ui.dp(10),ui.dp(4),ui.dp(10),ui.dp(10))})
                row.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4))})
            }
            repeat(columns-holes.size){row.addView(View(this),LinearLayout.LayoutParams(0,0,1f).apply{setMargins(ui.dp(4),0,ui.dp(4),0)})}
            col.addView(row)
        }
        root.addView(ScrollView(this).apply{addView(col)},FrameLayout.LayoutParams(-1,-1))
        GolfMenus.enter(col)
    }

    override fun onConfigurationChanged(newConfig:android.content.res.Configuration){
        super.onConfigurationChanged(newConfig)
        if(replayView!=null)return
        if(game==null){val practice=practiceMenu;if(practice!=null)showPractice(practice)else showMenu()}
    }

    private fun startRound(resume:Boolean){
        round=true; scores.clear()
        if(resume)scores+=progress.getString("scores","").orEmpty().split(',').mapNotNull{it.toIntOrNull()}.take(roundLength.count(course))
        val index=if(resume)progress.getInt("hole",roundLength.startIndex).coerceIn(roundLength.startIndex,roundLength.lastIndex(course)) else roundLength.startIndex
        if(!resume)progress.edit().remove("ball_x").remove("ball_z").remove("strokes").putString("scores","").putInt("hole",roundLength.startIndex).putBoolean("active",true).apply()
        play(index,resume)
    }

    private fun play(index:Int,restore:Boolean=false) {
        screenGeneration++;recording=null;lastReplay=null;shotChoices=null;replayRequested=false
        closeSurface(); root.removeAllViews(); result=null; completed=false; practiceMenu=null; overview=null; zoom=1f
        swing.cancel(); pendingStrike=null; swingTime=-1f; sceneReady=false; pullFraction=0f
        golfer=GolferAppearance(prefs.getBoolean("golfer_female",false),
            prefs.getInt("golfer_outfit",0).coerceIn(0,2),prefs.getInt("golfer_skin",0).coerceIn(0,2))
        // Easy mode: a cup half as wide again, and a gauge that forgives.
        val h=course.holes[index].let{if(easy)it.copy(cupRadius=it.cupRadius*EASY_CUP)else it}
        val mini=h.mini!=null
        swing.easy=easy
        val g=ClassicGame(h,easy); game=g
        previewGame=ClassicGame(h,easy); previewAsked=null; previewShown=previewSeq; preview=ShotPreview.NONE; finalFrameSent=false
        keepScreenOn(true)
        if(restore&&progress.contains("ball_x")) {
            val x=progress.getFloat("ball_x",h.tee.x);val z=progress.getFloat("ball_z",h.tee.z)
            if(x.isFinite()&&z.isFinite())g.restore(GolfPoint(x,h.heightAt(x,z)+ClassicHole.BALL_RADIUS,z),progress.getInt("strokes",0).coerceAtLeast(0))
            g.club=GolfClub.entries.getOrElse(progress.getInt("club",g.club.ordinal)){g.club}
            g.aimAngle=progress.getFloat("aim",g.aimAngle)
            g.setSpin(progress.getFloat("spin_x",0f),progress.getFloat("spin_y",0f))
        }
        lastState=g.state;previousPenalty=g.lastPenalty;previewDirty=true
        val s=ClassicSurface(this,h)
        s.onReady={if(surface===s){sceneReady=true;sceneSpinner?.visibility=View.GONE;refreshHud()}}
        s.onLoading={if(surface===s){sceneReady=false;sceneSpinner?.visibility=View.VISIBLE;refreshHud()}}
        surface=s;root.addView(s,FrameLayout.LayoutParams(-1,-1))
        val gesture=ClassicShotOverlay(this,swing,shotListener).apply{contentDescription=getString(R.string.classic_scene_accessible)}
        overlay=gesture;root.addView(gesture,FrameLayout.LayoutParams(-1,-1))
        val top=ui.column().apply{setPadding(ui.dp(10),ui.dp(10),ui.dp(10),0)}
        val row=ui.row()
        row.addView(icon(GolfIcon.Kind.BACK,R.string.golf_menu){showMenu()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        label=ui.pill().apply{textSize=14f;setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8))};row.addView(label,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(ui.dp(6),0,ui.dp(6),0)})
        // Mini-golf is flat enough to show the real path of the putt, bounces included: the slope grid starts hidden.
        gridShown=prefs.getBoolean(if(mini)"grid_mini" else "grid",!mini)
        gridButton=icon(GolfIcon.Kind.GRID,R.string.classic_grid_toggle){toggleGrid()}.apply{highlighted=!gridShown;alpha=.75f}
        row.addView(gridButton,LinearLayout.LayoutParams(ui.dp(36),ui.dp(36)).apply{rightMargin=ui.dp(6)})
        row.addView(icon(GolfIcon.Kind.CARD,R.string.golf_scorecard){scorecard(false)},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        top.addView(row)
        lieLabel=ui.pill().apply{textSize=12f;setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6));minHeight=ui.dp(32)}
        top.addView(lieLabel,LinearLayout.LayoutParams(-2,-2).apply{gravity=Gravity.CENTER;topMargin=ui.dp(6)})
        root.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        miniMap=ClassicMap(this,h).also { it.ball=g.ball;it.contentDescription=getString(R.string.classic_overview);it.setOnClickListener{toggleOverview()} }
        root.addView(miniMap,FrameLayout.LayoutParams(ui.dp(88),ui.dp(138),Gravity.END or Gravity.TOP).apply{topMargin=ui.dp(112);rightMargin=ui.dp(12)})
        windRose=if(mini)null else ClassicWindRose(this).also{root.addView(it,FrameLayout.LayoutParams(ui.dp(64),ui.dp(84),Gravity.START or Gravity.TOP).apply{leftMargin=ui.dp(12);topMargin=ui.dp(112)})}
        feedback=ui.pill().apply {textSize=22f;visibility=View.GONE;setTextColor(0xFFFFD778.toInt())}
        root.addView(feedback,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply{topMargin=ui.dp(100)})
        sceneSpinner=ProgressBar(this).also{root.addView(it,FrameLayout.LayoutParams(ui.dp(48),ui.dp(48),Gravity.CENTER))}
        // One putter, no wind, no lie to read: mini-golf shows none of the club, spin and slope controls.
        lieView=null;lieAlong=null;spinCentre=null;spinPad=null;clubUp=null;clubDown=null;clubBadge=null;clubPicker=null;bottom=null
        if(!mini) {
            // Bottom left: the point of contact above the view from behind the ball; the slope towards the target stands upright beside the contact point.
            val dock=ui.row().apply {
                gravity=Gravity.TOP
                setPadding(ui.dp(10),0,ui.dp(10),ui.dp(10))
            }
            val stack=ui.column().apply{gravity=Gravity.CENTER_HORIZONTAL}
            dock.addView(stack)
            spinPad=ClassicSpinPad(this,getString(R.string.classic_spin_pad)){x,y->
                if(canSetShot()){g.setSpin(x,y);spinPad?.spinX=g.spinX;spinPad?.spinY=g.spinY;previewDirty=true;save()}
            }
            val centre=icon(GolfIcon.Kind.CENTER,R.string.classic_spin_centre){
                if(canSetShot()){g.setSpin(0f,0f);spinPad?.spinX=g.spinX;spinPad?.spinY=g.spinY;previewDirty=true;save();refreshHud()}
            }.apply{alpha=.8f;visibility=View.INVISIBLE}
            spinCentre=centre
            val padBox=FrameLayout(this)
            padBox.addView(spinPad,FrameLayout.LayoutParams(ui.dp(80),ui.dp(80)))
            padBox.addView(centre,FrameLayout.LayoutParams(ui.dp(26),ui.dp(26),Gravity.TOP or Gravity.END))
            stack.addView(padBox,LinearLayout.LayoutParams(-2,-2).apply{bottomMargin=ui.dp(8)})
            lieView=ClassicLieView(this,false).also{stack.addView(it,LinearLayout.LayoutParams(ui.dp(68),ui.dp(56)))}
            lieAlong=ClassicLieView(this,true).also{dock.addView(it,LinearLayout.LayoutParams(ui.dp(56),ui.dp(88)).apply{leftMargin=ui.dp(8)})}
            val up=icon(GolfIcon.Kind.UP,R.string.classic_club_longer){stepClub(-1)}
            val down=icon(GolfIcon.Kind.DOWN,R.string.classic_club_shorter){stepClub(1)}
            val badge=ClassicClubBadge(this){chooseClub()}
            clubUp=up;clubDown=down;clubBadge=badge
            val picker=ui.column().apply{gravity=Gravity.CENTER_HORIZONTAL}
            picker.addView(up,LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
            picker.addView(badge,LinearLayout.LayoutParams(ui.dp(66),ui.dp(66)).apply{topMargin=ui.dp(4);bottomMargin=ui.dp(4)})
            picker.addView(down,LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
            clubPicker=picker
            root.addView(picker,FrameLayout.LayoutParams(-2,-2,Gravity.END or Gravity.TOP).apply{leftMargin=ui.dp(12);rightMargin=ui.dp(12);topMargin=ui.dp(256)})
            bottom=dock
            root.addView(dock,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.START))
        }
        cameraButton=icon(GolfIcon.Kind.CAMERA,R.string.golf_live_camera){cycleLiveCamera()}.apply{alpha=.72f}
        root.addView(cameraButton,FrameLayout.LayoutParams(ui.dp(48),ui.dp(48),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=ui.dp(16)})
        replayButton=icon(GolfIcon.Kind.REPLAY,R.string.golf_replay_title){requestShotReplay()}.apply{alpha=.72f;translationX=-ui.dp(56).toFloat()}
        root.addView(replayButton,FrameLayout.LayoutParams(ui.dp(48),ui.dp(48),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=ui.dp(16)})
        speedBadge=ui.pill().apply {
            text=getString(R.string.golf_live_speed_badge);textSize=13f;visibility=View.GONE
            minHeight=0;setPadding(ui.dp(8),ui.dp(4),ui.dp(8),ui.dp(4))
        }
        root.addView(speedBadge,FrameLayout.LayoutParams(-2,ui.dp(32),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=ui.dp(72)})
        updateCameraButton()
        root.post { layoutGame() }
        refreshHud();save();if(resumed)s.onResume();startFrames()
    }

    /** The scene fills the display; the spin pad shrinks and the club picker moves to the left in landscape. */
    private fun layoutGame() {
        if(game==null||root.width==0)return
        if(bottom==null)return
        val landscape=root.width>root.height
        val pad=ui.dp(if(landscape)72 else 80)
        spinPad?.let{if(it.layoutParams.height!=pad){it.layoutParams=it.layoutParams.apply{height=pad;width=pad}}}
        // The wind rose keeps the top-left corner: in landscape it sits under the back button and the picker moves beside it.
        windRose?.let{r->val lp=r.layoutParams as FrameLayout.LayoutParams;val top=ui.dp(if(landscape)62 else 112)
            if(lp.topMargin!=top){lp.topMargin=top;r.layoutParams=lp}}
        clubPicker?.let{p->val lp=p.layoutParams as FrameLayout.LayoutParams;val side=if(landscape)Gravity.START else Gravity.END;val top=ui.dp(if(landscape)64 else 256)
            val left=ui.dp(if(landscape)88 else 12)
            if(lp.topMargin!=top||lp.leftMargin!=left||lp.gravity!=(side or Gravity.TOP)){lp.topMargin=top;lp.leftMargin=left;lp.gravity=side or Gravity.TOP;p.layoutParams=lp}}
    }

    private fun canSetShot()=replayView==null&&lastReplay==null&&game?.state==GolfState.READY&&!swing.active&&pendingStrike==null&&swingTime<0f&&sceneReady&&!completed

    private val shotListener=object:ClassicShotOverlay.Listener {
        override fun manualFlightCamera()=replayView==null&&liveCamera.manual&&!completed&&
            (pendingStrike!=null||swingTime>=0f||game?.state==GolfState.FLYING||game?.state==GolfState.ROLLING)
        override fun cameraRotate(dx:Float,dy:Float) {
            if(manualFlightCamera())liveCamera.rotate(dx,dy,overlay?.width?:root.width,overlay?.height?:root.height)
        }
        override fun cameraMove(dx:Float,dy:Float,zoom:Float) {
            if(manualFlightCamera())liveCamera.zoom(zoom)
        }
        override fun cameraFastForward(held:Boolean) {
            val accelerate=held&&manualFlightCamera()
            if(liveFastForward==accelerate)return
            liveFastForward=accelerate
            refreshHud()
            cameraToast?.cancel()
            if(accelerate)cameraToast=Toast.makeText(this@ClassicGolfActivity,
                R.string.golf_live_fast_forward,Toast.LENGTH_SHORT).also{it.show()}
        }
        override fun canShoot()=canSetShot()
        override fun aim(radians:Float){val g=game?:return;if(canSetShot()){g.aimAngle+=radians;previewDirty=true}}
        override fun zoom(factor:Float){
            val aerial=overview
            if(aerial!=null) {
                val hole=game?.hole?:return
                overview=aerial.copy(distance=(aerial.distance*factor).coerceIn(OverviewView.MIN_DISTANCE,OverviewView.maxDistance(hole)))
            }
            else zoom=(zoom*factor).coerceIn(.6f,1.8f)
        }
        override fun overview()=overview!=null
        override fun pan(dx:Float,dy:Float){
            val aerial=overview?:return
            val h=game?.hole?:return
            // The ground under the finger follows it: metres per pixel at the looked-at point.
            val metres=2f*aerial.distance*tan(Math.toRadians(25.0).toFloat())/(overlay?.height?:root.height).coerceAtLeast(1)
            val forward=dy*metres/sin(aerial.pitch)
            val right=-dx*metres
            val x=aerial.x+sin(aerial.yaw)*forward-cos(aerial.yaw)*right
            val z=aerial.z+cos(aerial.yaw)*forward+sin(aerial.yaw)*right
            overview=aerial.copy(x=x.coerceIn(-h.width*.75f,h.width*.75f),z=z.coerceIn(-120f,h.length+150f))
        }
        // Fingers turning clockwise turn the course clockwise, as on a map.
        override fun rotate(radians:Float){overview=overview?.let{it.copy(yaw=it.yaw+radians)}}
        override fun aimOnMap(dx:Float,dy:Float){
            val aerial=overview?:return
            val g=game?:return
            if(!canSetShot())return
            val scene=overlay?:return
            // Ground covered by the finger at this zoom, then turn so that the landing point follows it:
            // the closer the camera, the finer the aim.
            val metres=2f*aerial.distance*tan(Math.toRadians(25.0).toFloat())/scene.height.coerceAtLeast(1)
            val forward=-dy*metres/sin(aerial.pitch)
            val gx=(-cos(aerial.yaw)*dx*metres+sin(aerial.yaw)*forward)
            val gz=(sin(aerial.yaw)*dx*metres+cos(aerial.yaw)*forward)
            val sideways=gx*-cos(g.aimAngle)+gz*sin(g.aimAngle)
            val reach=max(3f,preview.carry)
            g.aimAngle-=sideways/reach;previewDirty=true
        }
        override fun pull(fraction:Float){if(fraction!=pullFraction){pullFraction=fraction;previewDirty=true}}
        override fun release(fraction:Float,needle:Float){
            val g=game?:return
            pullFraction=0f
            if(g.state!=GolfState.READY||completed||lastReplay!=null){previewDirty=true;return}
            strikePower=GolfSwing.power(fraction,g.club)
            liveFastForward=false;overlay?.resetCameraTaps()
            liveCamera.begin(g.aimAngle,g.club)
            pendingStrike=strikePower to needle;swingTime=0f
            val off=abs(needle)
            val message=getString(when {
                off<=swing.perfect->R.string.classic_swing_perfect
                off<=swing.good->R.string.classic_swing_good
                off>=swing.miss->R.string.classic_swing_miss
                needle<0f->R.string.classic_swing_left
                else->R.string.classic_swing_right
            })
            feedback?.text=message;feedback?.visibility=View.VISIBLE;feedbackTime=1.8f
            root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            refreshHud()
        }
        override fun cancel(){pullFraction=0f;previewDirty=true;refreshHud()}
        override fun fieldOfView():Float{
            val scene=overlay?:root
            val aspect=scene.width.toFloat()/scene.height.coerceAtLeast(1)
            return 2f*atan(tan(Math.toRadians(25.0).toFloat())*aspect)
        }
    }

    /** Opens the free aerial camera over the ball and the guided shot, looking down the line. */
    private fun toggleOverview(){
        val g=game?:return
        if(!canSetShot())return
        if(overview!=null){overview=null;return}
        val end=preview.landing?:g.ball
        val span=hypot(end.x-g.ball.x,end.z-g.ball.z)
        overview=OverviewView((g.ball.x+end.x)*.5f,(g.ball.z+end.z)*.5f,
            (span*1.3f+10f).coerceIn(OverviewView.MIN_DISTANCE*2f,OverviewView.maxDistance(g.hole)),g.aimAngle)
        previewDirty=true
    }

    /** Grid shown: the putt guide is a plain line and the player reads the slopes. Hidden: the guide rolls like the ball. */
    private fun toggleGrid(){
        gridShown=!gridShown;prefs.edit().putBoolean(if(game?.hole?.mini!=null)"grid_mini" else "grid",gridShown).apply()
        gridButton?.highlighted=!gridShown;previewDirty=true
    }

    private fun stepClub(delta:Int){
        val g=game?:return;if(!canSetShot())return
        g.club=GolfClub.entries[(g.club.ordinal+delta).coerceIn(0,GolfClub.entries.lastIndex)]
        previewDirty=true;refreshHud();save()
    }

    /** Power shown by the guide: the live pull, or a full swing at rest (the cup distance for putts). */
    private fun guidePower(g:ClassicGame):Float = when {
        swing.active -> GolfSwing.power(pullFraction,g.club)
        g.club==GolfClub.PUTTER -> (g.distanceToCup/g.hole.puttRange).coerceIn(.04f,1f)
        else -> 1f
    }

    override fun doFrame(time:Long){
        if(replayView!=null)return
        val g=game?:return
        val dt=if(lastFrame==0L)0f else ((time-lastFrame)/1e9f).coerceIn(0f,.05f);lastFrame=time
        if(dialog?.isShowing!=true&&!completed&&lastReplay==null){
            if(swing.active)swing.update(dt,GolfSwing.period(g.club,GolfSwing.power(pullFraction,g.club),g.lie,easy))
            if(swingTime>=0f){
                swingTime+=if(liveFastForward)dt*3f else dt
                if(swingTime>=GolferPose.STRIKE_SECONDS)pendingStrike?.let { (power,needle) ->
                    pendingStrike=null
                    if(g.hit(power,needle)){
                        recording=GolfReplayRecorder(GolfReplay(course=course.id,hole=g.hole.number,club=g.club,
                            aim=g.aimAngle,stroke=g.strokes,easy=easy,samples=listOf(GolfReplaySample(0f,g.ball,g.clock))))
                        lastState=g.state
                        audio.shot(power);root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        previewAsked=null;previewShown=previewSeq;preview=ShotPreview.NONE;miniMap?.preview=emptyList();save()
                    }
                }
                if(swingTime>=GolferPose.RELEASE_SECONDS)swingTime=-1f
            }
            val moving=g.state==GolfState.FLYING||g.state==GolfState.ROLLING
            val accelerate=if(liveCamera.manual)liveFastForward else overlay?.holding==true
            val simulationDt=if(moving&&accelerate)dt*3f else dt
            g.update(simulationDt)
            recording?.let { capture ->
                capture.append(simulationDt,g.ball,g.clock)
                if(g.state==GolfState.READY||g.state==GolfState.HOLED) {
                    lastReplay=capture.finish(g.state==GolfState.HOLED);recording=null;liveFastForward=false;overlay?.resetCameraTaps()
                    if(g.state==GolfState.READY&&lastReplay!=null)showShotChoices()
                }
            }
            if(g.hole.mini!=null) {
                // The knock of the ball on the rails, at most ten times a second.
                val knock=g.takeBounce()
                if(knock>.4f&&time-lastKnock>100_000_000L){lastKnock=time;audio.impact(knock*.6f)}
            }
            if(feedbackTime>0f){feedbackTime-=dt;if(feedbackTime<=0f)feedback?.visibility=View.GONE}
        }
        if(g.state!=lastState){
            if(g.state==GolfState.READY){previewDirty=true;save()}
            if(g.state==GolfState.ROLLING&&lastState==GolfState.FLYING)audio.impact(2f)
            lastState=g.state;refreshHud()
        }
        if(g.lastPenalty!=previousPenalty){previousPenalty=g.lastPenalty;audio.outOfBounds();Toast.makeText(this,R.string.classic_penalty,Toast.LENGTH_SHORT).show();save()}
        if(g.state==GolfState.HOLED&&!completed){completed=true;audio.holed();root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);finishHole()}
        if(previewDirty&&lastReplay==null&&g.state==GolfState.READY&&pendingStrike==null&&time-lastPreview>33_000_000L){
            lastPreview=time;previewDirty=false
            askPreview(g)
        }
        val pulling=swing.active
        val pose=when {
            swingTime>=0f -> GolferPose.releaseProgress(swingTime)
            pulling -> GolferPose.TOP*GolfSwing.power(pullFraction,g.club)
            else -> -1f
        }
        val power=if(swingTime>=0f)strikePower else if(pulling)GolfSwing.power(pullFraction,g.club) else 1f
        val shown=if(lastReplay==null&&g.state==GolfState.READY&&pendingStrike==null&&swingTime<0f)preview else ShotPreview.NONE
        val shotCamera=recording?.let { capture ->
            val shot=capture.template
            liveCamera.pose(g.hole,g.ball,shot.samples.first().ball,shot.aim,shot.club,capture.seconds,shot.stroke)
        }?:lastReplay?.let { shot ->
            liveCamera.pose(g.hole,g.ball,shot.samples.first().ball,shot.aim,shot.club,shot.duration,shot.stroke)
        }
        val frame=ClassicFrame(g.ball,g.aimAngle,g.state==GolfState.FLYING||g.state==GolfState.ROLLING,shown,overview,zoom,g.club,pose,power,pulling,golfer,gridShown,g.windX,g.windZ,
            if(g.hole.mini?.hasMovers==true)g.clock else 0f,shotCamera=shotCamera)
        // Still for a while (the camera has finished gliding): the scenery's own slow motion is drawn at half rate,
        // and slower still when nobody has touched the screen for a while. Nothing is drawn under a dialog,
        // nor under the result of a finished hole once its last image is in.
        if(frame!=lastSubmitted){lastSubmitted=frame;lastChange=time}
        val calm=time-lastChange>CALM_NANOS
        if(dialog?.isShowing!=true&&!(completed&&finalFrameSent)) {
            surface?.submit(frame,calm,time,calm&&time-lastTouch>IDLE_NANOS)
            if(completed)finalFrameSent=true
        }
        if(pulling)overlay?.invalidate()
        if(time-lastHud>150_000_000L){lastHud=time;refreshHud();miniMap?.ball=g.ball}
        if(replayRequested) {
            val shot=lastReplay?:recording?.finish(false)
            if(shot!=null){replayRequested=false;openReplay(shot);return}
        }
        if(resumed&&!(completed&&finalFrameSent))Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * Hands the guide's simulation to [previewWorker]; queued requests that a newer one replaced are skipped.
     * A finished guide is shown even if the finger has moved on since: while aiming, a long guide takes longer
     * than the gap between requests, and waiting for the very latest would leave the guide frozen.
     */
    private fun askPreview(g:ClassicGame){
        val copy=previewGame?:return
        val ask=PreviewAsk(++previewSeq,g.ball,g.club,g.aimAngle,g.spinX,g.spinY,guidePower(g),!gridShown)
        previewAsked=ask
        previewWorker.execute {
            if(previewAsked!==ask)return@execute
            val shot=copy.previewFor(ask.ball,ask.club,ask.aim,ask.spinX,ask.spinY,ask.power,ask.simulatePutt)
            runOnUiThread{
                if(previewAsked!=null&&copy===previewGame&&ask.seq>previewShown&&!isDestroyed){previewShown=ask.seq;showPreview(ask,shot)}
            }
        }
    }

    private fun showPreview(ask:PreviewAsk,shot:ShotPreview){
        preview=shot
        miniMap?.preview=shot.flight+shot.roll
        overlay?.readout=if(ask.club==GolfClub.PUTTER)getString(R.string.classic_putt_readout,shot.carry)
            else getString(R.string.classic_shot_readout,(ask.power*100).roundToInt(),shot.carry.roundToInt())
    }

    /** Sets a label only when its words change: every new text costs a layout pass and a redraw of the HUD. */
    private fun TextView.show(value:String,description:String?=null){
        if(text.toString()!=value)text=value
        if(description!=null&&contentDescription?.toString()!=description)contentDescription=description
    }

    private fun refreshHud(){
        val g=game?:return
        speedBadge?.visibility=if(liveFastForward&&!completed)View.VISIBLE else View.GONE
        val choosing=lastReplay!=null
        bottom?.visibility=if(choosing)View.GONE else View.VISIBLE
        clubPicker?.visibility=if(choosing)View.GONE else View.VISIBLE
        cameraButton?.visibility=if(choosing)View.GONE else View.VISIBLE
        replayButton?.visibility=if(!completed&&(choosing||pendingStrike!=null||recording!=null||swingTime>=0f))View.VISIBLE else View.GONE
        val h=g.hole
        val total=relative(scores.sum()-roundPars().take(scores.size).sumOf{it.second})
        label?.show(getString(if(round&&roundLength.startIndex>0)R.string.golf_hud_section else R.string.classic_hud,h.number,h.par,g.strokes,total,if(round)roundLength.count(course) else course.holes.size,if(round)h.number-roundLength.startIndex else h.number),
            getString(R.string.classic_hud_accessible,h.number,h.par,g.strokes,total,if(round)roundLength.count(course) else course.holes.size,if(round)h.number-roundLength.startIndex else h.number))
        windRose?.set(g.aimAngle,g.windX,g.windZ)
        if(h.mini!=null) {
            // Only what matters on a carpet: how far the cup is, and the sand when the ball lies in it.
            lieLabel?.show(if(g.lie==GolfLie.GREEN)getString(R.string.minigolf_distance,g.distanceToCup.roundToInt())
                else getString(R.string.minigolf_lie_distance,lieNames[g.lie.ordinal],g.distanceToCup.roundToInt()))
        } else {
            val elevation=(h.cup.y-g.ball.y).roundToInt()
            val grip=(g.lieGrip*100).roundToInt()
            val slope=getString(if(elevation>=0)R.string.classic_uphill else R.string.classic_downhill,abs(elevation))
            lieLabel?.show(if(grip<100)getString(R.string.classic_lie_distance_loss,lieNames[g.lie.ordinal],g.distanceToCup.roundToInt(),slope,grip)
                else getString(R.string.classic_lie_distance,lieNames[g.lie.ordinal],g.distanceToCup.roundToInt(),slope))
        }
        clubBadge?.set(g.club,clubNames[g.club.ordinal],
            getString(R.string.golf_result_line,getString(R.string.classic_choose_club),getString(R.string.classic_club_choice,clubNames[g.club.ordinal],g.club.carry.roundToInt(),g.club.loft.roundToInt())))
        val ready=canSetShot()
        val lieShown=g.state==GolfState.READY&&g.club!=GolfClub.PUTTER
        for(view in listOf(lieView,lieAlong))view?.let{it.visibility=if(lieShown)View.VISIBLE else View.INVISIBLE;if(lieShown)it.set(g.lieSlopeAlong,g.lieSlopeSide)}
        clubBadge?.isEnabled=ready
        clubUp?.isEnabled=ready&&g.club.ordinal>0
        clubDown?.isEnabled=ready&&g.club.ordinal<GolfClub.entries.lastIndex
        spinPad?.let{it.visibility=if(g.club==GolfClub.PUTTER)View.INVISIBLE else View.VISIBLE;it.isEnabled=ready&&g.club!=GolfClub.PUTTER;it.spinX=g.spinX;it.spinY=g.spinY}
        spinCentre?.visibility=if(ready&&g.club!=GolfClub.PUTTER&&(g.spinX!=0f||g.spinY!=0f))View.VISIBLE else View.INVISIBLE
    }

    private fun chooseClub(){
        val g=game?:return;if(!canSetShot())return
        val entries=GolfClub.entries.map{getString(R.string.classic_club_choice,clubNames[it.ordinal],it.carry.roundToInt(),it.loft.roundToInt())}.toTypedArray()
        showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(R.string.classic_choose_club).setSingleChoiceItems(entries,g.club.ordinal){d,n->g.club=GolfClub.entries[n];previewDirty=true;refreshHud();d.dismiss()}.setNegativeButton(android.R.string.cancel,null).create())
    }

    private fun save(){
        val g=game?:return;if(!round||completed)return
        progress.edit().putBoolean("active",true).putInt("hole",g.hole.number-1).putString("scores",scores.joinToString(","))
            .putFloat("ball_x",g.saveBall.x).putFloat("ball_z",g.saveBall.z).putInt("strokes",g.saveStrokes)
            .putInt("club",g.club.ordinal).putFloat("aim",g.aimAngle).putFloat("spin_x",g.spinX).putFloat("spin_y",g.spinY).apply()
    }

    private fun finishHole(){
        val g=game?:return
        if(round){
            scores+=g.strokes
            val ed=progress.edit().putString("scores",scores.joinToString(",")).remove("ball_x").remove("ball_z").remove("strokes").putInt("hole",roundLength.nextIndex(scores.size)).putBoolean("active",!roundLength.finished(scores,course))
            if(roundLength.finished(scores,course)){val total=scores.sum();val best=progress.getInt("best",Int.MAX_VALUE);if(total<best)ed.putInt("best",total)}
            ed.apply()
        }
        val diff=g.strokes-g.hole.par
        val title=when{g.strokes==1->R.string.golf_result_hole_in_one;diff<=-3->R.string.golf_result_albatross;diff == -2->R.string.golf_result_eagle;diff == -1->R.string.golf_result_birdie;diff==0->R.string.golf_result_par;diff==1->R.string.golf_result_bogey;else->R.string.golf_result_double_bogey}
        val text=if(diff>2)getString(R.string.golf_result_over,diff) else getString(title)
        val buttons=(if(round&&roundLength.finished(scores,course))listOf<View>(ui.primary(getString(R.string.golf_scorecard)){lastReplay=null;closeResult();scorecard(true)})
            else listOf(ui.primary(getString(if(round)R.string.golf_next else R.string.golf_retry)){play(if(round)roundLength.nextIndex(scores.size) else g.hole.number-1)},
                ui.secondary(getString(R.string.golf_menu)){showMenu()})).toMutableList()
        if(lastReplay!=null)buttons.add(1,icon(GolfIcon.Kind.REPLAY,R.string.golf_replay_title){requestShotReplay()}.apply{minimumHeight=ui.dp(48)})
        val view=ClassicResultView(this,ui,ClassicResultView.Kind.of(g.strokes,g.hole.par),text,buttons)
        result=view;root.addView(view,FrameLayout.LayoutParams(-1,-1))
    }

    private fun closeResult(){result?.let{root.removeView(it)};result=null}

    private fun scorecard(finished:Boolean){
        val col=ui.column().apply{setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(12))}
        col.addView(ui.text(if(round)roundTitle(course,roundLength)else titleOf(course),20f,bold=true))
        val current=game?.takeIf{!completed}
        val pars=roundPars()
        val displayScores=pars.mapIndexed{i,(number,_)->
            if(round)scores.getOrNull(i)?:current?.takeIf{it.hole.number==number}?.strokes
            else game?.takeIf{it.hole.number==number}?.strokes
        }
        pars.forEachIndexed{i,(number,par)->
            col.addView(ui.text(getString(R.string.classic_score_row,number,par,displayScores[i]?.toString()?:getString(R.string.classic_unplayed)),16f).apply{setPadding(0,ui.dp(5),0,ui.dp(5))})
        }
        col.addView(ui.text(getString(R.string.golf_total,displayScores.filterNotNull().sum(),pars.filterIndexed{i,_->displayScores[i]!=null}.sumOf{it.second}),20f,bold=true).apply{setPadding(0,ui.dp(12),0,0)})
        val b=com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(R.string.golf_scorecard).setView(ScrollView(this).apply{addView(col)})
        if(finished)b.setPositiveButton(R.string.golf_menu){_,_->showMenu()}.setCancelable(false) else b.setPositiveButton(android.R.string.ok,null)
        showDialog(b.create())
    }

    private fun help(){
        val col=ui.column().apply{setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(12))}
        col.addView(GolfMenus.disclosure(this,getString(R.string.classic_help)) {
            ui.text(getString(if(course.mini)R.string.minigolf_help_body else R.string.classic_help_body),15f,ui.palette.secondary)
        })
        col.addView(GolfMenus.disclosure(this,getString(R.string.golf_tips)) {
            val tips=ui.column()
            tips.addView(ui.text(getString(if(course.random!=null)R.string.golf_random_description else course.descriptionRes),15f,ui.palette.secondary))
            val names=holeNamesOf(course)
            if(course.random==null)resources.getStringArray(course.tipsRes).forEachIndexed { index,tip ->
                tips.addView(ui.text(names[index],16f,bold=true).apply{setPadding(0,ui.dp(16),0,ui.dp(5))})
                tips.addView(ui.text(tip,15f,ui.palette.secondary))
            }
            tips
        })
        val b=com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(R.string.golf_info)
            .setView(ScrollView(this).apply{addView(col)}).setPositiveButton(R.string.golf_done,null)
        b.setNeutralButton(if(muted)R.string.classic_sound_on else R.string.classic_sound_off){_,_->muted=!muted;prefs.edit().putBoolean("muted",muted).apply();if(muted)audio.pause()else if(resumed)audio.resume()}
        showDialog(b.create())
    }
    private fun updateCameraButton() {
        val name=resources.getStringArray(R.array.golf_replay_cameras)[liveCamera.mode.ordinal]
        val description=getString(R.string.golf_live_camera_current,name)
        cameraButton?.contentDescription=description
        cameraButton?.tooltipText=description
    }

    private fun cycleLiveCamera() {
        liveFastForward=false;overlay?.resetCameraTaps()
        liveCamera.select(liveCameraModes[(liveCameraModes.indexOf(liveCamera.mode)+1)%liveCameraModes.size])
        prefs.edit().putString("live_camera",liveCamera.mode.name).apply()
        updateCameraButton()
        cameraToast?.cancel()
        val name=resources.getStringArray(R.array.golf_replay_cameras)[liveCamera.mode.ordinal]
        cameraToast=Toast.makeText(this,getString(R.string.golf_live_camera_current,name),Toast.LENGTH_SHORT).also{it.show()}
    }

    private fun showShotChoices() {
        shotChoices?.let{root.removeView(it)}
        feedback?.visibility=View.GONE
        feedbackTime=0f
        val choices=FrameLayout(this).apply {
            isClickable=true;isFocusable=true
            contentDescription=getString(R.string.golf_shot_continue)
            setOnClickListener{nextShot()}
        }
        shotChoices=choices
        root.addView(choices,FrameLayout.LayoutParams(-1,-1))
        replayButton?.bringToFront()
        refreshHud()
    }

    private fun nextShot() {
        lastReplay=null;replayRequested=false
        shotChoices?.let{root.removeView(it)};shotChoices=null
        overlay?.resetCameraTaps()
        previewDirty=true
        refreshHud()
    }

    private fun requestShotReplay() {
        val shot=lastReplay?:recording?.finish(false)
        if(shot!=null)openReplay(shot)
        else if(pendingStrike!=null||swingTime>=0f)replayRequested=true
    }

    private fun openReplay(shot:GolfReplay) {
        val definition=ClassicCourses.byId(shot.course)
        // By its index first: a generated course then builds that hole only.
        val base=definition?.holes?.let{holes->holes.getOrNull(shot.hole-1)?.takeIf{it.number==shot.hole}?:holes.firstOrNull{it.number==shot.hole}}
        if(base==null){replayError();return}
        if(replayView!=null)return
        stopFrames();liveFastForward=false;overlay?.resetCameraTaps();refreshHud()
        keepScreenOn(true)
        val current=shot===lastReplay||recording?.template?.id==shot.id
        val sharedScene=surface?.takeIf{current}
        if(sharedScene==null)surface?.onPause()
        hiddenGameViews=(0 until root.childCount).map{root.getChildAt(it)}.filter{it!==sharedScene}.map{it to it.visibility}
        hiddenGameViews.forEach{(v,_)->v.visibility=View.GONE}
        val hole=if(shot.easy)base.copy(cupRadius=base.cupRadius*EASY_CUP)else base
        val camera=if(current)liveCamera.copy() else LiveShotCamera().apply{
            begin(shot.aim,shot.club);select(liveCamera.mode)
        }
        // A saved excerpt must not reserve the library ID of the still-recording full shot.
        val savableShot=if(current&&recording!=null)shot.copy(id=java.util.UUID.randomUUID().toString())else shot
        val viewer=GolfReplayView(this,shot,hole,{closeReplay()},{saveReplay(savableShot)},camera,sharedScene,
            lastSubmitted.takeIf{current})
        replayView=viewer;root.addView(viewer,FrameLayout.LayoutParams(-1,-1))
        if(resumed)viewer.resume()
    }

    private fun closeReplay() {
        val viewer=replayView?:return
        viewer.release();root.removeView(viewer);replayView=null
        hiddenGameViews.forEach{(v,visibility)->v.visibility=visibility};hiddenGameViews=emptyList()
        lastFrame=0L;finalFrameSent=false;lastTouch=System.nanoTime()
        keepScreenOn(game!=null)
        if(resumed){if(!viewer.usesLiveScene)surface?.onResume();startFrames()}
    }

    private fun saveReplay(shot:GolfReplay) {
        val definition=ClassicCourses.byId(shot.course)?:return
        val name=EditText(this).apply {
            setSingleLine();filters=arrayOf(android.text.InputFilter.LengthFilter(80))
            setText(shot.name.ifBlank{getString(R.string.golf_replay_default_name,titleOf(definition),shot.hole,shot.stroke)})
            selectAll()
        }
        showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(R.string.golf_replay_name).setView(name)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.golf_replay_save){_,_->
                val named=shot.copy(name=name.text.toString().trim().ifBlank{getString(R.string.golf_replay_default_name,titleOf(definition),shot.hole,shot.stroke)})
                replayIo.execute {
                    val saved=runCatching{replayStore.save(named)}
                    runOnUiThread{if(!isDestroyed){if(saved.isSuccess)Toast.makeText(this,R.string.golf_replay_saved,Toast.LENGTH_SHORT).show()else replayError()}}
                }
            }.create())
    }

    private fun showReplayLibrary() {
        val generation=screenGeneration
        replayIo.execute {
            val result=runCatching{replayStore.entries()}
            runOnUiThread {
                if(isDestroyed||generation!=screenGeneration)return@runOnUiThread
                val entries=result.getOrElse{replayError();return@runOnUiThread}
                val builder=com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(R.string.golf_replay_library)
                    .setNegativeButton(R.string.golf_done,null)
                if(entries.isEmpty())builder.setMessage(R.string.golf_replay_empty)
                else builder.setItems(entries.map{entry->
                    val definition=ClassicCourses.byId(entry.course)
                    getString(R.string.golf_replay_entry,entry.name,definition?.let{titleOf(it)}?:entry.course,entry.hole,
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(entry.created)))
                }.toTypedArray()){_,index->chooseSavedReplay(entries[index])}
                showDialog(builder.create())
            }
        }
    }

    private fun chooseSavedReplay(entry:GolfReplayStore.Entry) {
        showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setTitle(entry.name)
            .setPositiveButton(R.string.golf_replay_play){_,_->
                val generation=screenGeneration
                replayIo.execute {
                    val loaded=runCatching{replayStore.load(entry.id)}
                    runOnUiThread{if(!isDestroyed&&generation==screenGeneration)loaded.fold({openReplay(it)},{replayError()})}
                }
            }.setNeutralButton(R.string.golf_replay_delete){_,_->
                showDialog(com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(this).setMessage(getString(R.string.golf_replay_delete_confirm,entry.name))
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.golf_replay_delete){_,_->
                        replayIo.execute {
                            val deleted=runCatching{replayStore.delete(entry.id)}
                            runOnUiThread{if(!isDestroyed){if(deleted.isSuccess)showReplayLibrary()else replayError()}}
                        }
                    }.create())
            }.setNegativeButton(android.R.string.cancel,null).create())
    }

    private fun replayError(){Toast.makeText(this,R.string.golf_replay_error,Toast.LENGTH_LONG).show()}
    private fun showDialog(d:AlertDialog){dialog?.dismiss();dialog=d;replayView?.pause();d.setOnDismissListener{if(dialog===d){dialog=null;lastFrame=0;lastTouch=System.nanoTime();if(resumed)replayView?.resume()}};d.show()}
    private fun icon(kind:GolfIcon.Kind,res:Int,action:()->Unit)=GolfIcon(this,kind,getString(res),action)
    private fun relative(n:Int)=when{n>0->getString(R.string.golf_relative_over,n);n<0->getString(R.string.golf_relative_under,-n);else->getString(R.string.classic_even)}
}
