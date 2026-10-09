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
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlin.math.*

/** How long the frame must stay unchanged before drawing at half rate: the camera glides settle well within it. */
private const val CALM_NANOS = 1_500_000_000L

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
    private var gridButton: GolfIcon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui=GolfUi(this); audio=GolfAudio(this); muted=prefs.getBoolean("muted",false);easy=prefs.getBoolean("easy",false)
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
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){ override fun handleOnBackPressed(){ if(game!=null||practiceMenu!=null)showMenu() else finish() } })
        showMenu()
        GolfMenus.enter(root.getChildAt(0))
    }

    override fun onResume(){ super.onResume(); resumed=true; if(!muted)audio.resume(); surface?.onResume(); startFrames() }
    override fun onPause(){ resumed=false; stopFrames(); save(); surface?.onPause(); audio.pause(); super.onPause() }
    override fun onDestroy(){ closeSurface(); dialog?.dismiss(); audio.close(); super.onDestroy() }
    private fun startFrames(){ lastFrame=0; Choreographer.getInstance().removeFrameCallback(this); if(resumed&&game!=null)Choreographer.getInstance().postFrameCallback(this) }
    private fun stopFrames(){ Choreographer.getInstance().removeFrameCallback(this); lastFrame=0 }
    private fun closeSurface(){
        stopFrames(); surface?.release(); surface?.onPause(); surface=null
    }

    private fun showMenu() {
        save(); closeSurface(); game=null; completed=false; practiceMenu=null; root.removeAllViews(); result=null
        val col=ui.column().apply{setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(20))}
        val head=ui.row()
        head.addView(icon(GolfIcon.Kind.BACK,R.string.golf_back){finish()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        head.addView(ui.text(getString(R.string.classic_title),26f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        head.addView(icon(GolfIcon.Kind.PERSON,R.string.classic_golfer_title){GolfMenus.wardrobe(this)},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        head.addView(icon(GolfIcon.Kind.HELP,R.string.golf_info){help()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        col.addView(head)
        col.addView(Switch(this).apply{
            text=getString(R.string.classic_easy_mode);textSize=16f;setTextColor(ui.palette.text);isChecked=easy
            setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6))
            setOnCheckedChangeListener{_,on->easy=on;prefs.edit().putBoolean("easy",on).apply()}
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=ui.dp(6)})
        val wide=resources.configuration.screenWidthDp>=600
        val cards=ui.column()
        ClassicCourses.all.chunked(if(wide)2 else 1).forEach { definitions ->
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

    private fun courseTile(definition:ClassicCourseDefinition):View {
        val tile=ui.column().apply{background=ui.shape(ui.palette.surface,22f);clipToOutline=true}
        val poster=GolfMenus.photoCard(this,definition.id,getString(definition.titleRes))
        val tools=ui.row().apply{gravity=Gravity.END;setPadding(ui.dp(8),ui.dp(8),ui.dp(8),0)}
        tools.addView(icon(GolfIcon.Kind.FLAG,R.string.classic_practice){showPractice(definition)},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        tools.addView(icon(GolfIcon.Kind.CARD,R.string.golf_scorecard){showCourseRecords(definition)},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply{leftMargin=ui.dp(6)})
        poster.addView(tools,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        tile.addView(poster,LinearLayout.LayoutParams(-1,ui.dp(180)))
        val buttons=ui.row().apply{setPadding(ui.dp(10),ui.dp(10),ui.dp(10),ui.dp(10))}
        ClassicRoundLength.entries.forEachIndexed { index,length ->
            val saved=getSharedPreferences(length.progressName(definition),Context.MODE_PRIVATE)
            val active=saved.getBoolean("active",false)
            val button=ui.primary(if(active)getString(R.string.golf_resume_section,getString(length.labelRes))else getString(length.labelRes)) {
                chooseRound(definition,length)
            }.apply{
                minWidth=0;setPadding(ui.dp(8),ui.dp(12),ui.dp(8),ui.dp(12))
                contentDescription=getString(R.string.golf_round_accessible,getString(definition.titleRes),text)
            }
            buttons.addView(button,LinearLayout.LayoutParams(0,-2,1f).apply{if(index>0)leftMargin=ui.dp(8)})
        }
        tile.addView(buttons)
        return tile
    }

    private fun selectCourse(definition:ClassicCourseDefinition,length:ClassicRoundLength){
        course=definition;roundLength=length
        prefs.edit().putString("selected_course",course.id).apply()
    }

    private fun chooseRound(definition:ClassicCourseDefinition,length:ClassicRoundLength){
        selectCourse(definition,length)
        if(!progress.getBoolean("active",false)){startRound(false);return}
        showDialog(AlertDialog.Builder(this).setTitle(getString(R.string.golf_section_title,getString(course.titleRes),getString(length.labelRes)))
            .setPositiveButton(R.string.golf_continue){_,_->startRound(true)}
            .setNeutralButton(R.string.golf_new_course){_,_->
                showDialog(AlertDialog.Builder(this).setMessage(R.string.classic_replace)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.golf_play){_,_->startRound(false)}.create())
            }.setNegativeButton(android.R.string.cancel,null).create())
    }

    private fun showCourseRecords(definition:ClassicCourseDefinition){
        val col=ui.column().apply{setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(16))}
        ClassicRoundLength.entries.forEach { length ->
            val saved=getSharedPreferences(length.progressName(definition),Context.MODE_PRIVATE)
            val best=saved.getInt("best",0)
            col.addView(ui.text(getString(length.labelRes),19f,bold=true).apply{setPadding(0,ui.dp(14),0,ui.dp(8))})
            col.addView(ui.text(if(best>0)getString(R.string.golf_course_best,best,relative(best-length.holes(definition).sumOf{it.par}))
                else getString(R.string.golf_no_record),15f,ui.palette.secondary))
            val stored=saved.getString("scores","").orEmpty().split(',').mapNotNull{it.toIntOrNull()}.take(length.count)
            if(stored.isNotEmpty())col.addView(ui.secondary(getString(R.string.golf_scorecard)){
                dialog?.dismiss();selectCourse(definition,length);round=true;scores.clear();scores+=stored;scorecard(false)
            },LinearLayout.LayoutParams(-1,-2).apply{topMargin=ui.dp(8)})
        }
        showDialog(AlertDialog.Builder(this).setTitle(getString(definition.titleRes))
            .setView(ScrollView(this).apply{addView(col)}).setPositiveButton(R.string.golf_done,null).create())
    }

    private fun showPractice(definition:ClassicCourseDefinition){
        practiceMenu=definition
        root.removeAllViews()
        val col=ui.column().apply{setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(20))}
        val head=ui.row()
        head.addView(icon(GolfIcon.Kind.BACK,R.string.golf_back){showMenu()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        head.addView(ui.text(getString(R.string.golf_practice),23f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        col.addView(head)
        col.addView(ui.text(getString(definition.titleRes),15f,ui.palette.secondary).apply{setPadding(ui.dp(8),ui.dp(8),0,ui.dp(10))})
        val names=resources.getStringArray(definition.holeNamesRes)
        val columns=(resources.configuration.screenWidthDp/155).coerceIn(2,6)
        definition.holes.chunked(columns).forEach { holes ->
            val row=ui.row().apply{gravity=Gravity.TOP}
            holes.forEach { h ->
                val tile=ui.column().apply{
                    background=ui.shape(ui.palette.surface,18f);clipToOutline=true
                    isClickable=true;isFocusable=true
                    contentDescription=getString(R.string.classic_hole_choice,h.number,names[h.number-1],h.par,h.displayLength.roundToInt())
                    foreground=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),null,ui.shape(Color.WHITE,18f,null))
                    setOnClickListener{selectCourse(definition,ClassicRoundLength.FULL);round=false;scores.clear();play(h.number-1)}
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
        if(game==null){val practice=practiceMenu;if(practice!=null)showPractice(practice)else showMenu()}
    }

    private fun startRound(resume:Boolean){
        round=true; scores.clear()
        if(resume)scores+=progress.getString("scores","").orEmpty().split(',').mapNotNull{it.toIntOrNull()}.take(roundLength.count)
        val index=if(resume)progress.getInt("hole",roundLength.startIndex).coerceIn(roundLength.startIndex,roundLength.lastIndex) else roundLength.startIndex
        if(!resume)progress.edit().remove("ball_x").remove("ball_z").remove("strokes").putString("scores","").putInt("hole",roundLength.startIndex).putBoolean("active",true).apply()
        play(index,resume)
    }

    private fun play(index:Int,restore:Boolean=false) {
        closeSurface(); root.removeAllViews(); result=null; completed=false; practiceMenu=null; overview=null; zoom=1f
        swing.cancel(); pendingStrike=null; swingTime=-1f; sceneReady=false; pullFraction=0f
        golfer=GolferAppearance(prefs.getBoolean("golfer_female",false),
            prefs.getInt("golfer_outfit",0).coerceIn(0,2),prefs.getInt("golfer_skin",0).coerceIn(0,2))
        // Easy mode: a cup half as wide again, and a gauge that forgives.
        val h=course.holes[index].let{if(easy)it.copy(cupRadius=it.cupRadius*EASY_CUP)else it}
        val mini=h.mini!=null
        swing.easy=easy
        val g=ClassicGame(h,easy); game=g
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
        lieView=null;spinPad=null;clubUp=null;clubDown=null;clubBadge=null;clubPicker=null;bottom=null
        if(!mini) {
            val dock=ui.row().apply {
                setPadding(ui.dp(12),ui.dp(18),ui.dp(12),ui.dp(10))
            }
            lieView=ClassicLieView(this).also{dock.addView(it,LinearLayout.LayoutParams(ui.dp(112),ui.dp(60)))}
            dock.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
            spinPad=ClassicSpinPad(this,getString(R.string.classic_spin_pad)){x,y->
                if(canSetShot()){g.setSpin(x,y);spinPad?.spinX=g.spinX;spinPad?.spinY=g.spinY;previewDirty=true;save()}
            }
            dock.addView(spinPad,LinearLayout.LayoutParams(ui.dp(64),ui.dp(64)))
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
            root.addView(dock,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        }
        root.post { layoutGame() }
        refreshHud();save();if(resumed)s.onResume();startFrames()
    }

    /** The scene fills the display; the spin pad shrinks and the club picker moves to the left in landscape. */
    private fun layoutGame() {
        if(game==null||root.width==0)return
        if(bottom==null)return
        val landscape=root.width>root.height
        val pad=ui.dp(if(landscape)104 else 128)
        spinPad?.let{if(it.layoutParams.height!=pad){it.layoutParams=it.layoutParams.apply{height=pad;width=pad}}}
        // The wind rose keeps the top-left corner: in landscape it sits under the back button and the picker moves beside it.
        windRose?.let{r->val lp=r.layoutParams as FrameLayout.LayoutParams;val top=ui.dp(if(landscape)62 else 112)
            if(lp.topMargin!=top){lp.topMargin=top;r.layoutParams=lp}}
        clubPicker?.let{p->val lp=p.layoutParams as FrameLayout.LayoutParams;val side=if(landscape)Gravity.START else Gravity.END;val top=ui.dp(if(landscape)64 else 256)
            val left=ui.dp(if(landscape)88 else 12)
            if(lp.topMargin!=top||lp.leftMargin!=left||lp.gravity!=(side or Gravity.TOP)){lp.topMargin=top;lp.leftMargin=left;lp.gravity=side or Gravity.TOP;p.layoutParams=lp}}
    }

    private fun canSetShot()=game?.state==GolfState.READY&&!swing.active&&pendingStrike==null&&swingTime<0f&&sceneReady&&!completed

    private val shotListener=object:ClassicShotOverlay.Listener {
        override fun canShoot()=canSetShot()
        override fun aim(radians:Float){val g=game?:return;if(canSetShot()){g.aimAngle+=radians;previewDirty=true}}
        override fun zoom(factor:Float){
            val aerial=overview
            if(aerial!=null)overview=aerial.copy(distance=(aerial.distance*factor).coerceIn(OverviewView.MIN_DISTANCE,OverviewView.MAX_DISTANCE))
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
            if(g.state!=GolfState.READY||completed){previewDirty=true;return}
            strikePower=GolfSwing.power(fraction,g.club)
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
            (span*1.3f+10f).coerceIn(OverviewView.MIN_DISTANCE*2f,OverviewView.MAX_DISTANCE),g.aimAngle)
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
        val g=game?:return
        val dt=if(lastFrame==0L)0f else ((time-lastFrame)/1e9f).coerceIn(0f,.05f);lastFrame=time
        if(dialog?.isShowing!=true&&!completed){
            if(swing.active)swing.update(dt,GolfSwing.period(g.club,GolfSwing.power(pullFraction,g.club),g.lie,easy))
            if(swingTime>=0f){
                swingTime+=dt
                if(swingTime>=GolferPose.STRIKE_SECONDS)pendingStrike?.let { (power,needle) ->
                    pendingStrike=null
                    if(g.hit(power,needle)){
                        lastState=g.state
                        audio.shot(power);root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        preview=ShotPreview.NONE;miniMap?.preview=emptyList();save()
                    }
                }
                if(swingTime>=GolferPose.RELEASE_SECONDS)swingTime=-1f
            }
            val moving=g.state==GolfState.FLYING||g.state==GolfState.ROLLING
            g.update(if(moving&&!g.celebrating&&overlay?.holding==true)dt*3f else dt)
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
        if(previewDirty&&g.state==GolfState.READY&&pendingStrike==null&&time-lastPreview>33_000_000L){
            lastPreview=time;previewDirty=false
            preview=g.preview(guidePower(g),simulatePutt=!gridShown)
            miniMap?.preview=preview.flight+preview.roll
            overlay?.readout=if(g.club==GolfClub.PUTTER)getString(R.string.classic_putt_readout,preview.carry)
                else getString(R.string.classic_shot_readout,(guidePower(g)*100).roundToInt(),preview.carry.roundToInt())
        }
        val pulling=swing.active
        val pose=when {
            swingTime>=0f -> GolferPose.releaseProgress(swingTime)
            pulling -> GolferPose.TOP*GolfSwing.power(pullFraction,g.club)
            else -> -1f
        }
        val power=if(swingTime>=0f)strikePower else if(pulling)GolfSwing.power(pullFraction,g.club) else 1f
        val shown=if(g.state==GolfState.READY&&pendingStrike==null&&swingTime<0f)preview else ShotPreview.NONE
        val frame=ClassicFrame(g.ball,g.aimAngle,g.state==GolfState.FLYING||g.state==GolfState.ROLLING,shown,overview,zoom,g.club,pose,power,pulling,golfer,gridShown,g.windX,g.windZ,
            if(g.hole.mini?.hasMovers==true)g.clock else 0f)
        // Still for a while (the camera has finished gliding): the scenery's own slow motion is drawn at half rate.
        if(frame!=lastSubmitted){lastSubmitted=frame;lastChange=time}
        surface?.submit(frame,time-lastChange>CALM_NANOS,time)
        if(pulling)overlay?.invalidate()
        if(time-lastHud>150_000_000L){lastHud=time;refreshHud();miniMap?.ball=g.ball}
        if(resumed)Choreographer.getInstance().postFrameCallback(this)
    }

    /** Sets a label only when its words change: every new text costs a layout pass and a redraw of the HUD. */
    private fun TextView.show(value:String,description:String?=null){
        if(text.toString()!=value)text=value
        if(description!=null&&contentDescription?.toString()!=description)contentDescription=description
    }

    private fun refreshHud(){
        val g=game?:return
        val h=g.hole
        val total=relative(scores.sum()-roundHoles.take(scores.size).sumOf{it.par})
        label?.show(getString(if(round&&roundLength.startIndex>0)R.string.golf_hud_section else R.string.classic_hud,h.number,h.par,g.strokes,total,if(round)roundLength.count else course.holes.size,if(round)h.number-roundLength.startIndex else h.number),
            getString(R.string.classic_hud_accessible,h.number,h.par,g.strokes,total,if(round)roundLength.count else course.holes.size,if(round)h.number-roundLength.startIndex else h.number))
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
        lieView?.let{it.visibility=if(g.state==GolfState.READY&&g.club!=GolfClub.PUTTER)View.VISIBLE else View.INVISIBLE;if(it.visibility==View.VISIBLE)it.set(g.lieSlopeAlong,g.lieSlopeSide)}
        clubBadge?.isEnabled=ready
        clubUp?.isEnabled=ready&&g.club.ordinal>0
        clubDown?.isEnabled=ready&&g.club.ordinal<GolfClub.entries.lastIndex
        spinPad?.let{it.isEnabled=ready&&g.club!=GolfClub.PUTTER;it.spinX=g.spinX;it.spinY=g.spinY}
    }

    private fun chooseClub(){
        val g=game?:return;if(!canSetShot())return
        val entries=GolfClub.entries.map{getString(R.string.classic_club_choice,clubNames[it.ordinal],it.carry.roundToInt(),it.loft.roundToInt())}.toTypedArray()
        showDialog(AlertDialog.Builder(this).setTitle(R.string.classic_choose_club).setSingleChoiceItems(entries,g.club.ordinal){d,n->g.club=GolfClub.entries[n];previewDirty=true;refreshHud();d.dismiss()}.setNegativeButton(android.R.string.cancel,null).create())
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
            val ed=progress.edit().putString("scores",scores.joinToString(",")).remove("ball_x").remove("ball_z").remove("strokes").putInt("hole",roundLength.nextIndex(scores.size)).putBoolean("active",!roundLength.finished(scores))
            if(roundLength.finished(scores)){val total=scores.sum();val best=progress.getInt("best",Int.MAX_VALUE);if(total<best)ed.putInt("best",total)}
            ed.apply()
        }
        val diff=g.strokes-g.hole.par
        val title=when{g.strokes==1->R.string.golf_result_hole_in_one;diff<=-3->R.string.golf_result_albatross;diff == -2->R.string.golf_result_eagle;diff == -1->R.string.golf_result_birdie;diff==0->R.string.golf_result_par;diff==1->R.string.golf_result_bogey;else->R.string.golf_result_double_bogey}
        val text=if(diff>2)getString(R.string.golf_result_over,diff) else getString(title)
        val buttons=if(round&&roundLength.finished(scores))listOf(ui.primary(getString(R.string.golf_scorecard)){closeResult();scorecard(true)})
            else listOf(ui.primary(getString(if(round)R.string.golf_next else R.string.golf_retry)){play(if(round)roundLength.nextIndex(scores.size) else g.hole.number-1)},
                ui.secondary(getString(R.string.golf_menu)){showMenu()})
        val view=ClassicResultView(this,ui,ClassicResultView.Kind.of(g.strokes,g.hole.par),text,buttons)
        result=view;root.addView(view,FrameLayout.LayoutParams(-1,-1))
    }

    private fun closeResult(){result?.let{root.removeView(it)};result=null}

    private fun scorecard(finished:Boolean){
        val col=ui.column().apply{setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(12))}
        col.addView(ui.text(if(round)getString(R.string.golf_section_title,getString(course.titleRes),getString(roundLength.labelRes))else getString(course.titleRes),20f,bold=true))
        val current=game?.takeIf{!completed}
        val displayScores=roundHoles.mapIndexed{i,h->
            if(round)scores.getOrNull(i)?:current?.takeIf{it.hole.number==h.number}?.strokes
            else game?.takeIf{it.hole.number==h.number}?.strokes
        }
        roundHoles.forEachIndexed{i,h->
            col.addView(ui.text(getString(R.string.classic_score_row,h.number,h.par,displayScores[i]?.toString()?:getString(R.string.classic_unplayed)),16f).apply{setPadding(0,ui.dp(5),0,ui.dp(5))})
        }
        col.addView(ui.text(getString(R.string.golf_total,displayScores.filterNotNull().sum(),roundHoles.filterIndexed{i,_->displayScores[i]!=null}.sumOf{it.par}),20f,bold=true).apply{setPadding(0,ui.dp(12),0,0)})
        val b=AlertDialog.Builder(this).setTitle(R.string.golf_scorecard).setView(ScrollView(this).apply{addView(col)})
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
            tips.addView(ui.text(getString(course.descriptionRes),15f,ui.palette.secondary))
            val names=resources.getStringArray(course.holeNamesRes)
            resources.getStringArray(course.tipsRes).forEachIndexed { index,tip ->
                tips.addView(ui.text(names[index],16f,bold=true).apply{setPadding(0,ui.dp(16),0,ui.dp(5))})
                tips.addView(ui.text(tip,15f,ui.palette.secondary))
            }
            tips
        })
        val b=AlertDialog.Builder(this).setTitle(R.string.golf_info)
            .setView(ScrollView(this).apply{addView(col)}).setPositiveButton(R.string.golf_done,null)
        b.setNeutralButton(if(muted)R.string.classic_sound_on else R.string.classic_sound_off){_,_->muted=!muted;prefs.edit().putBoolean("muted",muted).apply();if(muted)audio.pause()else if(resumed)audio.resume()}
        showDialog(b.create())
    }
    private fun showDialog(d:AlertDialog){dialog?.dismiss();dialog=d;d.setOnDismissListener{if(dialog===d){dialog=null;lastFrame=0}};d.show()}
    private fun icon(kind:GolfIcon.Kind,res:Int,action:()->Unit)=GolfIcon(this,kind,getString(res),action)
    private fun relative(n:Int)=when{n>0->getString(R.string.golf_relative_over,n);n<0->getString(R.string.golf_relative_under,-n);else->getString(R.string.classic_even)}
}
