package com.Atom2Universe.app.games.billiards

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.render.*
import com.Atom2Universe.app.games.billiards.ui.*
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.followImmersiveMode
import java.util.concurrent.Executors
import kotlin.math.*

/** All match mutations happen on the main thread. Workers receive private copies. */
class BilliardActivity : ThemedActivity() {
    private lateinit var store: BilliardStore
    private lateinit var audio: BilliardAudio
    private lateinit var progress: BilliardProgress
    private var run: BilliardRun?=null
    private var lounge: BilliardLounge?=null
    private var menuPreview: BilliardMenuPreview?=null
    private var menuHost: FrameLayout?=null
    private var menuChrome: View?=null
    private var setupVisible=false
    private var setupMenu: BilliardMenuLayout?=null
    private var quickRace=1
    private var journeyLabel: TextView?=null
    private var resultPresented=false
    private val decisionClock=BilliardDecisionClock()
    private var clockArmed=false
    private var cameraRecovery=false
    private var recoveryFrame=0L
    private var returnCamera: BilliardCameraState?=null
    private var lastClockSave=0L
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val arcadeWorker=BilliardArcadePreparation {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
    }
    private var arcadeRevision=0L
    private var arcadePlan: BilliardArcadePlanner.Plan?=null
    private var arcadeRequested=false
    private val arcadePreparation=Runnable { requestArcadePreparation() }
    private val arcade get()=config.style==BilliardGameStyle.ARCADE
    private val showPowerGauge get()=arcade || config.controlMode.gauge
    @Volatile private var generation=0
    private var resumed=false
    private var modalCount=0
    private var ticking=false
    private var lastFrame=0L
    private var session: BilliardSession?=null
    private var config=BilliardConfig()
    private var shot=Shot(0.0)
    private var scene: BilliardRenderer?=null
    private var surface: BilliardSurface?=null
    private var status: TextView?=null
    private var scoreboard: BilliardScoreboard?=null
    private var targetLabel: TextView?=null
    private var callButton: TextView?=null
    private var callBackButton: TextView?=null
    private data class CallDraft(val camera: BilliardCameraState,val placing: Boolean,
        var step: BilliardCallStep=BilliardCallStep.BALL,var ball: Int=-1,var pocket: Int=-1,var rails: List<Int> = emptyList())
    private var callDraft: CallDraft?=null
    private var pushButton: TextView?=null
    private var safetyButton: TextView?=null
    private var decisions: LinearLayout?=null
    private var decisionAlternative: TextView?=null
    private var decisionRebreak: TextView?=null
    private var pocketLabels: BilliardPocketLabels?=null
    private var invalidPlacement=false
    private var hint: TextView?=null
    private var shootButton: TextView?=null
    private var powerGauge: BilliardPowerView?=null
    private var precisionGauge: BilliardArcadeTimingView?=null
    private var fineAimRow: View?=null
    private var precisionControls: View?=null
    private var disciplineLabel: TextView?=null
    private var strokeView: BilliardStrokeView?=null
    private var powerArmed=false
    private var powerOriginal: Shot?=null
    private var cancelShotButton: TextView?=null
    private var cameraButton: TextView?=null
    private val cameraViews=mutableMapOf<BilliardCameraMode,BilliardCameraState>()
    private var placementButton: BilliardIconView?=null
    private var menuInitialized=false
    private var powerLabel: TextView?=null
    private var angleLabel: TextView?=null
    private var elevationLabel: TextView?=null
    private var angleBar: SeekBar?=null
    private var angleDial: BilliardAngleDial?=null
    private var powerBar: SeekBar?=null
    private var elevationBar: SeekBar?=null
    private var spin: BilliardSpinView?=null
    private var spinLabel: TextView?=null
    private var impactPanel: View?=null
    private var impactButton: BilliardIconView?=null
    private val adjustable=mutableListOf<View>()
    private var settingControls=false
    private var placing=false
    private var dragging=-1
    private var slow=false
    private var computerAim: BilliardComputerAim?=null
    private val thinking get()=computerAim!=null
    private var winnerShown=-1
    private var predictionBusy=false
    private var predictionDirty=false
    private var predictionStartedAt=0L
    private var publishedTrace: Trace?=null
    private var publishedHud: List<Float>?=null
    private val prediction=Runnable { requestPrediction() }
    private val idleTick=Runnable { startTick() }
    private val tick=object: Choreographer.FrameCallback {
        override fun doFrame(time: Long) {
            ticking=false
            if(!resumed || surface==null) return
            val delta=if(lastFrame==0L) 0.0 else min(.05,(time-lastFrame)/1e9)
            lastFrame=time
            val s=session ?: return
            if(modalCount==0) {
                val wasMoving=s.world.moving
                val wasActive=s.isShotActive
                val wasReplay=s.replaying
                s.update(delta*if(slow) .25 else 1.0).forEach { audio.event(it,s.world); scene?.effect(it) }
                if(wasActive && !s.isShotActive && !wasReplay) handleJourneyShot(s)
                if((wasMoving || wasReplay) && !s.world.moving && !s.replaying) {
                    placing=s.match.ballInHand; recoverShotCamera(); schedulePrediction(); save()
                }
                if(run?.stage?.let { it==JourneyStage.PLAYING }!=false) {
                    if(s.mode==PlayMode.COMPUTER && s.match.player==1 && s.match.decision!=ShotDecision.NONE && !s.world.moving) s.decide(true)
                    if(s.mode==PlayMode.COMPUTER && s.match.player==1 && s.ready && !thinking) requestComputer()
                }
                if(thinking) advanceComputer(delta)
            }
            updateJourneyClock(s)
            publish()
            refreshStatus()
            if(run!=null && s.match.winner>=0 && run?.stage==JourneyStage.PLAYING) {
                run?.finishRack(s.match.winner); save()
            }
            if(run?.stage?.let { it!=JourneyStage.PLAYING }==true && !resultPresented && modalCount==0) showRunResult()
            else if(run==null && s.match.winner>=0 && winnerShown!=s.match.winner && modalCount==0) {
                winnerShown=s.match.winner
                showDialog(AlertDialog.Builder(this@BilliardActivity).setTitle(getString(R.string.billiard_winner,s.match.winner+1))
                    .setMessage(getString(R.string.billiard_score,s.match.winner+1,s.match.score0,s.match.score1))
                    .setPositiveButton(R.string.billiard_close,null).create())
            }
            if(s.world.moving || thinking || cameraRecovery || run?.let { it.mode==BilliardActivityMode.TIMED && it.stage==JourneyStage.PLAYING && clockArmed && modalCount==0 && impactPanel?.visibility!=View.VISIBLE }==true) startTick() else handler.postDelayed(idleTick,200)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store=BilliardStore(this) { handler.post { Toast.makeText(this,R.string.billiard_save_error,Toast.LENGTH_SHORT).show() } }; audio=BilliardAudio(this); progress=BilliardProgress(this); progress.deliverRewards()
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(impactPanel?.visibility==View.VISIBLE) closeImpactPanel()
                else if(powerArmed) cancelPowerShot()
                else if(callDraft!=null) finishCall(false)
                else if(session!=null) { save(); showLounge(run) }
                else if(setupVisible && setupMenu?.panelVisible==false) setupMenu?.setPanelVisible(true)
                else if(setupVisible) showLounge() else lounge?.back() ?: finish()
            }
        })
        if(savedInstanceState?.getBoolean("table")==true && store.exists) resumeTable() else showLounge()
    }
    private fun dp(n: Int)=(n*resources.displayMetrics.density).roundToInt()
    private fun plate(color: Int=0x900C2524.toInt(),radius: Int=18,border: Boolean=true)=GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
        if(border) setStroke(dp(1),0x507AAE9B)
    }
    private fun text(res: Int,size: Float=15f)=TextView(this).apply {
        setText(res); textSize=size; setTextColor(0xFFF1EBDD.toInt()); typeface=Typeface.create("sans-serif",0)
        setPadding(dp(8),dp(6),dp(8),dp(4)); setShadowLayer(dp(2).toFloat(),0f,dp(1).toFloat(),0xB0000000.toInt())
    }
    private fun button(res: Int,action: ()->Unit)=text(res,14f).apply {
        gravity=Gravity.CENTER; typeface=Typeface.create("sans-serif-medium",0)
        background=plate(); minHeight=dp(48); setPadding(dp(16),dp(10),dp(16),dp(10))
        isClickable=true; isFocusable=true; foreground=RippleDrawable(ColorStateList.valueOf(0x304FB3A5),null,plate(Color.WHITE)); setOnClickListener { action() }
    }
    private fun accent(view: TextView) { view.background=plate(0xB55B5030.toInt()); view.setTextColor(0xFFFFDEA2.toInt()) }
    private fun precisionToggle(label: Int,checked: Boolean,changed: (Boolean)->Unit)=BilliardTapToggle(this).apply {
        setText(label); textSize=12f; isAllCaps=false; minWidth=dp(104); minHeight=dp(48)
        setPadding(dp(12),dp(6),dp(12),dp(6)); isChecked=checked
        backgroundTintList=null; selectedControl(this,checked)
        setOnClickListener { selectedControl(this,isChecked); changed(isChecked) }
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; isMotionEventSplittingEnabled=false }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; isMotionEventSplittingEnabled=false }
    private fun overlayRoot()=FrameLayout(this).apply {
        setBackgroundColor(0xFF101820.toInt())
        // Keep all fingers with the initial control; a second finger must not toggle its neighbour.
        isMotionEventSplittingEnabled=false
        setOnApplyWindowInsetsListener { v,insets ->
            val cutout=insets.displayCutout
            v.setPadding(cutout?.safeInsetLeft ?: 0,cutout?.safeInsetTop ?: 0,cutout?.safeInsetRight ?: 0,cutout?.safeInsetBottom ?: 0)
            insets
        }
    }
    private fun icon(kind: BilliardIconView.Icon,label: Int,action: ()->Unit)=BilliardIconView(this,kind).apply {
        contentDescription=getString(label); isFocusable=true; isClickable=true; setOnClickListener { action() }
    }
    private fun choose(label: Int,items: List<String>,selected: Int,changed: (Int)->Unit) {
        val list=column().apply { setPadding(dp(16),dp(4),dp(16),dp(12)) }
        lateinit var dialog: AlertDialog
        items.forEachIndexed { i,name ->
            list.addView(button(label) { dialog.dismiss(); changed(i) }.apply {
                text=name; if(i==selected) accent(this)
            },LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(6) })
        }
        dialog=AlertDialog.Builder(this).setTitle(label).setView(ScrollView(this).apply { addView(list) }).create()
        showDialog(dialog)
    }
    private fun chooseDiscipline() {
        val content=column().apply { setPadding(dp(16),dp(8),dp(16),dp(16)) }
        val tabs=row(); val choices=column(); var family=config.discipline.family
        lateinit var dialog: AlertDialog
        val familyNames=listOf(R.string.billiard_family_carom,R.string.billiard_family_pool,R.string.billiard_family_blackball,
            R.string.billiard_family_snooker,R.string.billiard_family_pyramid,R.string.billiard_family_heyball)
        fun display() {
            choices.removeAllViews(); tabs.removeAllViews()
            TableFamily.entries.forEach { f ->
                tabs.addView(button(familyNames[f.ordinal]) { family=f; display() }.apply { if(f==family) accent(this) },
                    LinearLayout.LayoutParams(-2,dp(44)).apply { rightMargin=dp(6) })
            }
            Discipline.entries.filter { it.family==family }.forEach { d ->
                val card=row().apply {
                    background=plate(if(d==config.discipline) 0xA8444930.toInt() else 0x55142C2C,14)
                    setPadding(dp(6),dp(6),dp(12),dp(6)); isClickable=true; isFocusable=true
                    contentDescription=getString(d.titleRes()); setOnClickListener {
                        dialog.dismiss()
                        val size=if(d.family==config.discipline.family) config.tableSize else rememberedTableSize(d.family)
                        config=config.copy(discipline=d,tableSize=size); showMenu()
                    }
                }
                card.addView(BilliardTableBadge(this,family),LinearLayout.LayoutParams(dp(84),dp(64)))
                val description=column()
                description.addView(text(d.titleRes(),16f).apply { typeface=Typeface.create("sans-serif-medium",0) })
                description.addView(text(d.rulesRes(),11f).apply { maxLines=2; setTextColor(0xFF9EB5B7.toInt()) })
                card.addView(description,LinearLayout.LayoutParams(0,-2,1f))
                choices.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
            }
        }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tabs) },LinearLayout.LayoutParams(-1,dp(54)))
        content.addView(ScrollView(this).apply { isVerticalScrollBarEnabled=false; addView(choices) },LinearLayout.LayoutParams(-1,dp(280)))
        display()
        dialog=AlertDialog.Builder(this).setTitle(R.string.billiard_discipline).setView(content).setNegativeButton(R.string.billiard_close,null).create()
        showDialog(dialog)
    }
    private fun rememberedTableSize(family: TableFamily)=BilliardTableSize.restore(
        getSharedPreferences("billiards",MODE_PRIVATE).getString("tableSize_${family.name}",null),family)
    private fun tableSizeName(size: BilliardTableSize): String = when(size) {
        BilliardTableSize.CAROM_210 -> getString(R.string.billiard_size_carom_210)
        BilliardTableSize.CAROM_230 -> getString(R.string.billiard_size_carom_230)
        BilliardTableSize.CAROM_252 -> getString(R.string.billiard_size_carom_252)
        BilliardTableSize.CAROM_284 -> getString(R.string.billiard_size_carom_284)
        BilliardTableSize.POOL_8_PRO -> getString(R.string.billiard_size_8_pro)
        else -> getString(R.string.billiard_size_feet,size.feet)
    }
    private fun adaptedTable(size: BilliardTableSize,d: Discipline)=size.adaptedMarkings ||
        size==BilliardTableSize.CAROM_210 || (!size.isReference && d in listOf(Discipline.CADRE_71_2,Discipline.FIVE_PINS,Discipline.NINE_PINS,Discipline.ARTISTIC))
    private fun tableSizeCategory(size: BilliardTableSize,d: Discipline)=getString(when {
        size.isReference -> R.string.billiard_size_reference
        adaptedTable(size,d) -> R.string.billiard_size_leisure
        else -> R.string.billiard_size_club
    })
    private fun chooseTableSize() {
        val content=column().apply { setPadding(dp(16),dp(8),dp(16),dp(16)) }
        lateinit var dialog: AlertDialog
        BilliardTableSize.forFamily(config.discipline.family).forEach { size ->
            val title=tableSizeName(size)
            val dimensions=getString(R.string.billiard_size_dimensions,size.length,size.width)
            val category=tableSizeCategory(size,config.discipline)
            val card=column().apply {
                isSelected=size==config.tableSize; isClickable=true; isFocusable=true
                background=plate(if(isSelected) 0xA8444930.toInt() else 0x55142C2C,14)
                setPadding(dp(8),dp(5),dp(8),dp(8))
                contentDescription=getString(R.string.billiard_selection,title,getString(R.string.billiard_selection,dimensions,category))
                setOnClickListener {
                    dialog.dismiss(); config=config.copy(tableSize=size)
                    getSharedPreferences("billiards",MODE_PRIVATE).edit().putString("tableSize_${size.family.name}",size.name).apply()
                    showMenu()
                }
            }
            card.addView(text(R.string.billiard_table_size,17f).apply { text=title; if(card.isSelected) setTextColor(0xFFE4C488.toInt()) })
            card.addView(text(R.string.billiard_size_dimensions,13f).apply { text=dimensions })
            card.addView(text(R.string.billiard_size_reference,11f).apply { text=category; setTextColor(0xFFB2C5C4.toInt()) })
            content.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        dialog=AlertDialog.Builder(this).setTitle(R.string.billiard_table_size)
            .setView(ScrollView(this).apply { addView(content) }).setNegativeButton(R.string.billiard_close,null).create()
        showDialog(dialog)
    }
    private val difficultyLevels=listOf(R.string.billiard_easy,R.string.billiard_medium,R.string.billiard_hard)
    private fun setDifficulty(level: Int) {
        if(config.difficulty==level) return
        config=config.copy(difficulty=level)
        getSharedPreferences("billiards",MODE_PRIVATE).edit().putInt("difficulty",level).apply()
        if(session==null) { showMenu(); return }
        // A pending choice belongs to the old level. Cancel it before the next tick replans.
        if(thinking) { generation++; computerAim=null; syncControls(); updateControlText() }
        updateInteraction(); publish(); refreshStatus(); save(); startTick()
    }
    private fun chooseDifficulty() = choose(R.string.billiard_difficulty,difficultyLevels.map(::getString),config.difficulty,::setDifficulty)
    private fun setRotationStripes(enabled: Boolean) {
        config=config.copy(rotationStripes=enabled)
        getSharedPreferences("billiards",MODE_PRIVATE).edit().putBoolean("rotationStripes",enabled).apply()
        scene?.rotationStripes=enabled
        menuPreview?.appearance(config)
        surface?.requestRender()
        save()
    }
    private fun setLook(look: BilliardLook) {
        config=config.copy(look=look)
        getSharedPreferences("billiards",MODE_PRIVATE).edit().putString("look",look.name).apply()
        scene?.look=look
        menuPreview?.appearance(config)
        surface?.requestRender()
        save()
    }
    private fun chooseLook() = choose(R.string.billiard_look,BilliardLook.entries.map { getString(it.title) },config.look.ordinal) {
        setLook(BilliardLook.entries[it])
    }
    private fun chooseRotationStripes() = choose(R.string.billiard_rotation_stripes,
        listOf(R.string.billiard_rotation_stripes_off,R.string.billiard_rotation_stripes_on).map(::getString),
        if(config.rotationStripes) 1 else 0) { setRotationStripes(it==1) }
    private fun chooseControlMode() = choose(R.string.billiard_input_mode,
        BilliardControlMode.entries.map { getString(it.title) },config.controlMode.ordinal) { index ->
        cancelPowerShot()
        config=config.copy(controlMode=BilliardControlMode.entries[index])
        getSharedPreferences("billiards",MODE_PRIVATE).edit().putString("controlMode",config.controlMode.name).apply()
        if(session==null) showMenu() else { refreshStatus(); save(); surface?.wakeCamera() }
    }
    /** Chosen on the achievements card of the lounge, where the clubs that award them are counted. */
    private fun setFinish(value: Int) {
        config=config.copy(finish=value); menuPreview?.appearance(config)
        getSharedPreferences("billiards",MODE_PRIVATE).edit().putInt("finish",value).apply()
    }
    private fun chooseGameStyle() = choose(R.string.billiard_game_style,
        BilliardGameStyle.entries.map { getString(it.title) },config.style.ordinal) { index ->
        cancelPowerShot()
        config=config.copy(style=BilliardGameStyle.entries[index])
        getSharedPreferences("billiards",MODE_PRIVATE).edit().putString("style",config.style.name).apply()
        if(session==null) showMenu() else {
            disciplineLabel?.text=tableTitle(); refreshStatus(); schedulePrediction(); save()
        }
    }
    private fun tableTitle()=if(arcade) getString(R.string.billiard_selection,
        getString(config.discipline.titleRes()),getString(config.style.title)) else getString(config.discipline.titleRes())
    private fun readMenuConfig() {
        if(menuInitialized) return
        menuInitialized=true
        val p=getSharedPreferences("billiards",MODE_PRIVATE)
        fun i(key: String,max: Int,default: Int=0)=p.getInt(key,default).coerceIn(0,max-1)
        val assistance=restoredBilliardAssistance(i("aid",3,1),p.getInt("guideVersion",0))
        p.edit().putInt("aid",assistance).putInt("guideVersion",1).apply()
        val discipline=Discipline.entries[i("discipline",Discipline.entries.size)]
        config=BilliardConfig(discipline,BilliardRoom.entries[i("room",5)],
            BilliardCue.entries[i("cue",4)],PlayMode.entries[i("mode",3)],i("cloth",3,1),assistance,i("difficulty",3,1),
            p.getBoolean("sound",true),rememberedTableSize(discipline.family),p.getBoolean("rotationStripes",true),
            BilliardControlMode.restore(p.getString("controlMode",null)),BilliardGameStyle.restore(p.getString("style",null)),p.getInt("finish",0).coerceIn(0,progress.clubCount),
            BilliardLook.restore(p.getString("look",null)))
    }
    private fun showMenu() {
        setupVisible=true; lounge=null
        if(menuPreview==null) releaseTable()
        run=null
        readMenuConfig()
        showSetupContent()
    }
    private fun releaseTable() {
        rememberCameraView()
        menuPreview?.close(); menuPreview=null; menuHost=null; menuChrome=null; setupMenu=null
        cancelPowerShot()
        decisionClock.pause(); returnCamera=null; cameraRecovery=false
        invalidateArcadePreparation()
        if(callDraft!=null) finishCall(false)
        generation++; computerAim=null; handler.removeCallbacks(prediction); handler.removeCallbacks(idleTick)
        Choreographer.getInstance().removeFrameCallback(tick); ticking=false
        surface?.let { it.preserveEGLContextOnPause=false; it.onPause() }; surface=null; scene=null; session=null; lastFrame=0
        status=null; hint=null; shootButton=null; powerGauge=null; precisionGauge=null; fineAimRow=null; precisionControls=null; disciplineLabel=null; powerLabel=null; angleLabel=null; elevationLabel=null
        scoreboard=null; targetLabel=null; callButton=null; callBackButton=null; pushButton=null; safetyButton=null; decisions=null; pocketLabels=null
        powerBar=null; angleBar=null; angleDial=null; elevationBar=null; spin=null; spinLabel=null; impactPanel=null; impactButton=null; strokeView=null; cancelShotButton=null; adjustable.clear()
        cameraButton=null
    }
    private fun menuBackdrop(): BilliardMenuPreview {
        menuPreview?.let { it.preferences=config; return it }
        val backdrop=BilliardMenuPreview(this,config)
        menuPreview=backdrop
        menuHost=overlayRoot().apply { addView(backdrop.root,FrameLayout.LayoutParams(-1,-1)) }
        setContentView(menuHost)
        if(resumed) backdrop.resume()
        return backdrop
    }
    private fun menuContent(content: View) {
        val host=menuHost ?: return
        menuChrome?.let(host::removeView)
        menuChrome=content; host.addView(content,FrameLayout.LayoutParams(-1,-1))
    }
    private fun showSetupContent() {
        val preview=BilliardSession(config.discipline,PlayMode.PRACTICE,config.cloth,config.tableSize)
        val backdrop=menuBackdrop(); backdrop.showTable(config)
        val menu=BilliardMenuLayout(this,backdrop::viewport)
        val heading=row().apply { isClickable=true }
        heading.addView(icon(BilliardIconView.Icon.BACK,R.string.billiard_back) { showLounge() },LinearLayout.LayoutParams(dp(48),dp(48)))
        heading.addView(text(R.string.billiard_quick,22f),LinearLayout.LayoutParams(0,-2,1f))
        val peek=button(R.string.billiard_menu_view_table) { menu.toggle() }.apply { textSize=12f; setPadding(dp(8),dp(4),dp(8),dp(4)) }
        menu.visibilityChanged={ peek.setText(if(it) R.string.billiard_menu_view_table else R.string.billiard_menu_show) }
        heading.addView(peek,LinearLayout.LayoutParams(dp(82),-2))
        val form=column().apply { setPadding(dp(12),dp(12),dp(12),dp(12)) }
        form.addView(text(R.string.billiard_your_table,21f))
        form.addView(text(R.string.billiard_table_specs,12f).apply { text=tableSpecs(preview.table); setTextColor(0xFFB2C5C4.toInt()) })
        val modes=listOf(R.string.billiard_mode_solo,R.string.billiard_mode_duo,R.string.billiard_mode_ai)
        val modeRow=row()
        modes.forEachIndexed { i,id -> modeRow.addView(button(id) { config=config.copy(mode=PlayMode.entries[i]); showMenu() }.apply {
            textSize=12f; setPadding(dp(6),dp(6),dp(6),dp(6)); isSelected=i==config.mode.ordinal; if(isSelected) accent(this)
        },LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),dp(8),dp(2),dp(4)) }) }
        form.addView(modeRow)
        if(config.mode!=PlayMode.PRACTICE) form.addView(button(R.string.billiard_race_to) {
            quickRace=quickRace%3+1; showMenu()
        }.apply { text=getString(R.string.billiard_race_to,quickRace) })
        if(config.mode==PlayMode.COMPUTER) {
            form.addView(text(R.string.billiard_difficulty,12f).apply { setTextColor(0xFFE4C488.toInt()) })
            val levels=row()
            difficultyLevels.forEachIndexed { i,id -> levels.addView(button(id) { setDifficulty(i) }.apply {
                textSize=12f; setPadding(dp(4),dp(4),dp(4),dp(4)); isSelected=i==config.difficulty; if(isSelected) accent(this)
            },LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),0,dp(2),dp(4)) }) }
            form.addView(levels)
        }
        fun selection(label: Int,value: String,action: ()->Unit) {
            val choice=column().apply {
                background=plate(0x55182E29,12); setPadding(dp(8),dp(3),dp(8),dp(5)); isClickable=true; isFocusable=true
                setOnClickListener { action() }; contentDescription=getString(R.string.billiard_selection,getString(label),value)
            }
            choice.addView(text(label,10f).apply { setTextColor(0xFF8FA8AA.toInt()); letterSpacing=.08f })
            choice.addView(text(label,16f).apply { text=value; typeface=Typeface.create("sans-serif-medium",0); setPadding(dp(8),0,dp(8),dp(3)) })
            form.addView(choice,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(7) })
        }
        selection(R.string.billiard_discipline,getString(config.discipline.titleRes())) {
            chooseDiscipline()
        }
        selection(R.string.billiard_table_size,getString(R.string.billiard_selection,tableSizeName(config.tableSize),
            tableSizeCategory(config.tableSize,config.discipline))) { chooseTableSize() }
        selection(R.string.billiard_environment,getString(config.room.title)) {
            choose(R.string.billiard_environment,BilliardRoom.entries.map { getString(it.title) },config.room.ordinal) { config=config.copy(room=BilliardRoom.entries[it]); showMenu() }
        }
        selection(R.string.billiard_cue,getString(config.cue.title)) {
            choose(R.string.billiard_cue,BilliardCue.entries.map { getString(it.title) },config.cue.ordinal) { config=config.copy(cue=BilliardCue.entries[it]); showMenu() }
        }
        selection(R.string.billiard_game_style,getString(config.style.title)) { chooseGameStyle() }
        if(!arcade) selection(R.string.billiard_input_mode,getString(config.controlMode.title)) { chooseControlMode() }
        form.addView(button(R.string.billiard_preferences) { menuOptions() },LinearLayout.LayoutParams(-1,dp(44)))
        form.addView(button(R.string.billiard_play) {
            getSharedPreferences("billiards",MODE_PRIVATE).edit().putInt("discipline",config.discipline.ordinal).putInt("room",config.room.ordinal)
                .putInt("cue",config.cue.ordinal).putInt("mode",config.mode.ordinal).putInt("cloth",config.cloth).putInt("aid",config.assistance)
                .putInt("difficulty",config.difficulty).putBoolean("sound",config.sound)
                .putBoolean("rotationStripes",config.rotationStripes)
                .putString("controlMode",config.controlMode.name)
                .putString("style",config.style.name)
                .putString("tableSize_${config.discipline.family.name}",config.tableSize.name).apply()
            run=if(config.mode==PlayMode.PRACTICE) null else BilliardRun(raceTo=quickRace)
            openTable(config,BilliardSession(config.discipline,config.mode,config.cloth,config.tableSize),Shot(0.0,cueMass=config.cue.mass,endMass=config.cue.endMass)); save()
        }.apply { accent(this) },LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(12) })
        if(store.exists) form.addView(button(R.string.billiard_resume) { resumeTable() },LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(8) })
        form.addView(button(R.string.billiard_rules) { showRules(config.discipline) },LinearLayout.LayoutParams(-1,dp(44)).apply { topMargin=dp(4) })
        val body=column().apply { background=plate(0x80091C1B.toInt(),24); isClickable=true; setPadding(dp(6),dp(6),dp(6),dp(6)) }
        body.addView(ScrollView(this).apply { isVerticalScrollBarEnabled=true; addView(form) },LinearLayout.LayoutParams(-1,0,1f))
        setupMenu=menu; menu.content(heading,body); menuContent(menu)
    }

    /** One line of a settings dialog: the setting and its current value. A tap opens its choices. */
    private fun settingLine(form: LinearLayout,label: Int,value: String,open: ()->Unit) {
        form.addView(button(label) { open() }.apply { text=getString(R.string.billiard_selection,getString(label),value) },
            LinearLayout.LayoutParams(-1,dp(56)).apply { bottomMargin=dp(8) })
    }
    private fun settingSwitch(form: LinearLayout,label: Int,checked: Boolean,changed: (Boolean)->Unit) {
        form.addView(SwitchCompat(this).apply {
            setText(label); setTextColor(0xFFF1EBDD.toInt()); isChecked=checked; minHeight=dp(48)
            setOnCheckedChangeListener { _,v -> changed(v) }
        })
    }
    private fun menuOptions() {
        lateinit var dialog: AlertDialog
        val form=column().apply { setPadding(dp(16),dp(8),dp(16),dp(16)) }
        fun option(label: Int,items: List<Int>,value: Int,changed: (Int)->Unit)=
            settingLine(form,label,getString(items[value])) { dialog.dismiss(); choose(label,items.map(::getString),value,changed) }
        option(R.string.billiard_cloth,listOf(R.string.billiard_slow,R.string.billiard_normal,R.string.billiard_fast),config.cloth) { config=config.copy(cloth=it); showMenu() }
        option(R.string.billiard_assistance,listOf(R.string.billiard_aid_none,R.string.billiard_aid_contact,R.string.billiard_aid_full),config.assistance) { config=config.copy(assistance=it); showMenu() }
        form.addView(button(R.string.billiard_game_style) { dialog.dismiss(); chooseGameStyle() })
        if(!arcade) form.addView(button(R.string.billiard_input_mode) { dialog.dismiss(); chooseControlMode() })
        if(config.mode==PlayMode.COMPUTER) option(R.string.billiard_difficulty,difficultyLevels,config.difficulty,::setDifficulty)
        option(R.string.billiard_look,BilliardLook.entries.map { it.title },config.look.ordinal) { setLook(BilliardLook.entries[it]) }
        settingSwitch(form,R.string.billiard_rotation_stripes,config.rotationStripes,::setRotationStripes)
        settingSwitch(form,R.string.billiard_sound,config.sound) { config=config.copy(sound=it) }
        dialog=AlertDialog.Builder(this).setTitle(R.string.billiard_preferences)
            .setView(ScrollView(this).apply { addView(form) }).setPositiveButton(R.string.billiard_close,null).create()
        showDialog(dialog)
    }
    private fun resumeTable() {
        runCatching { store.load() }.onSuccess {
            run=it.run
            // A display preference changed in the lounge also applies to a resumed match.
            val stripes=getSharedPreferences("billiards",MODE_PRIVATE).getBoolean("rotationStripes",it.config.rotationStripes)
            val controls=BilliardControlMode.restore(getSharedPreferences("billiards",MODE_PRIVATE).getString("controlMode",it.config.controlMode.name))
            val style=BilliardGameStyle.restore(getSharedPreferences("billiards",MODE_PRIVATE).getString("style",it.config.style.name))
            val look=BilliardLook.restore(getSharedPreferences("billiards",MODE_PRIVATE).getString("look",it.config.look.name))
            val finish=getSharedPreferences("billiards",MODE_PRIVATE).getInt("finish",it.config.finish).coerceIn(0,progress.clubCount)
            cameraViews.clear(); cameraViews.putAll(it.cameraViews)
            it.camera?.let { camera -> cameraViews[camera.mode]=camera }
            openTable(it.config.copy(rotationStripes=stripes,controlMode=controls,style=if(it.run?.competitive==true) it.config.style else style,look=look,finish=finish),
                it.session,it.shot,it.camera)
            it.camera?.let { camera ->
                if(it.session.world.moving) returnCamera=camera else scene?.camera?.restore(camera,resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE)
                awaitCamera()
            }
        }
            .onFailure { Toast.makeText(this,R.string.billiard_load_error,Toast.LENGTH_LONG).show(); showLounge() }
    }
    private fun openTable(c: BilliardConfig,s: BilliardSession,initial: Shot,initialCamera: BilliardCameraState?=cameraViewState()) {
        rememberCameraView(); returnCamera=null
        menuPreview?.close(); menuPreview=null; menuHost=null; menuChrome=null
        setupVisible=false; setupMenu=null; lounge=null; resultPresented=false; clockArmed=false; decisionClock.pause()
        cancelPowerShot()
        invalidateArcadePreparation()
        handler.removeCallbacks(idleTick)
        generation++; computerAim=null; surface?.let { it.preserveEGLContextOnPause=false; it.onPause() }
        config=c; session=s; shot=initial; placing=s.match.ballInHand; slow=false; winnerShown=-1; invalidPlacement=false; callDraft=null; powerOriginal=null; powerArmed=false
        audio.enabled=c.sound
        scene=BilliardRenderer(c,s.table) { getString(R.string.billiard_number,it) }
        run?.exercise?.takeIf { it>=0 }?.let { scene?.targetZone=BilliardChallenges.all[it].zone(s.table) }
        surface=BilliardSurface(this,scene!!)
        adjustable.clear(); cameraButton=null; angleBar=null; powerBar=null; powerLabel=null
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        val sideWidth=min(300,resources.configuration.screenWidthDp*2/5)
        val root=overlayRoot()
        val renderer=scene!!; val gl=surface!!
        (initialCamera ?: cameraViews[BilliardCameraMode.TOP])?.let { renderer.camera.restore(it,!landscape) }
            ?: renderer.camera.top(!landscape)
        renderer.hudTop=dp(100).toFloat(); renderer.hudBottom=dp(180).toFloat()
        root.addView(gl,FrameLayout.LayoutParams(-1,-1))
        val labels=BilliardPocketLabels(this,renderer,s.table)
        pocketLabels=labels; labels.session=s
        labels.ballSelected={ selectCallBall(it) }
        labels.pocketSelected={ selectCallPocket(it) }
        labels.railSelected={ selectCallRail(it) }
        root.addView(labels,FrameLayout.LayoutParams(-1,-1))
        gl.touch={ p,down -> tableTouch(p,down) }
        gl.targetTapped={ x,y -> labels.selectAt(x,y) }
        gl.finished={ save() }
        gl.cameraChanging={ rememberCameraView() }
        gl.cameraAdjusted={ refreshCameraControls(); refreshStatus() }
        val header=row().apply { setPadding(dp(10),dp(8),dp(10),dp(8)) }
        header.addView(icon(BilliardIconView.Icon.MENU,R.string.billiard_menu) { gameMenu() },LinearLayout.LayoutParams(dp(48),dp(48)))
        val score=column()
        disciplineLabel=text(c.discipline.titleRes(),11f).apply { text=tableTitle(); setTextColor(0xFFDCC28A.toInt()) }
        score.addView(disciplineLabel)
        journeyLabel=if(run!=null) text(R.string.billiard_objective,12f).also {
            it.setTextColor(0xFFE4C488.toInt()); it.maxLines=2; it.minHeight=dp(48); score.addView(it)
            it.isClickable=true; it.setOnClickListener { showJourneyInfo() }
        } else null
        scoreboard=BilliardScoreboard(this,::ballName).also { score.addView(it); it.visibility=if(run?.solo==true) View.GONE else View.VISIBLE }
        header.addView(score,LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(8); rightMargin=dp(8) })
        cameraButton=button(R.string.billiard_control_camera,::cycleCamera).apply {
            textSize=12f; maxLines=2; setPadding(dp(8),dp(4),dp(8),dp(4))
        }.also { header.addView(it,LinearLayout.LayoutParams(dp(96),-2)) }
        val headerPanel=column().apply { addView(header) }
        root.addView(headerPanel,FrameLayout.LayoutParams(if(landscape) dp(resources.configuration.screenWidthDp-sideWidth-16) else -1,-2,Gravity.TOP))
        val tools=if(landscape) row() else column()
        fun tool(kind: BilliardIconView.Icon,label: Int,action: ()->Unit): BilliardIconView {
            val v=icon(kind,label,action); tools.addView(v,LinearLayout.LayoutParams(dp(48),dp(48)).apply { bottomMargin=dp(7); rightMargin=dp(5) }); return v
        }
        val effects=column().apply {
            setPadding(dp(14),dp(10),dp(14),dp(14)); background=plate()
            // Empty space belongs to this panel, not to the table behind it.
            isClickable=true; isFocusable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val effectsPanel=FrameLayout(this).apply {
            visibility=View.GONE; setBackgroundColor(0x66000000)
            isMotionEventSplittingEnabled=false
            isClickable=true; setOnClickListener { closeImpactPanel() }
        }
        effectsPanel.addView(ScrollView(this).apply { addView(effects); isVerticalScrollBarEnabled=true; isClickable=true; isMotionEventSplittingEnabled=false },
            FrameLayout.LayoutParams(dp(min(320,resources.configuration.screenWidthDp-24)),
                dp(min(620,resources.configuration.screenHeightDp-24)),Gravity.CENTER))
        impactPanel=effectsPanel
        effects.addView(text(R.string.billiard_spin,15f))
        spin=BilliardSpinView(this).apply { set(shot.side,shot.top); changed={ a,b -> changeShot(shot.copy(side=a,top=b)) } }
        effects.addView(spin,LinearLayout.LayoutParams(-1,dp(260))); adjustable+=spin!!
        spinLabel=text(R.string.billiard_spin_value,12f).also { effects.addView(it) }
        val precisionPrefs=getSharedPreferences("billiards",MODE_PRIVATE)
        val spinPrecision=precisionPrefs.getBoolean("spinPrecision",false)
        spin?.precision=spinPrecision
        effects.addView(precisionToggle(R.string.billiard_spin_precision,spinPrecision) { checked ->
            spin?.precision=checked; precisionPrefs.edit().putBoolean("spinPrecision",checked).apply()
        }.also { adjustable+=it },LinearLayout.LayoutParams(-2,-2).apply { topMargin=dp(10); bottomMargin=dp(8) })
        val fineSpin=row()
        for((label,delta) in listOf(R.string.billiard_spin_left to (-.01 to 0.0),R.string.billiard_spin_down to (0.0 to -.01),
            R.string.billiard_spin_up to (0.0 to .01),R.string.billiard_spin_right to (.01 to 0.0))) {
            fineSpin.addView(button(label) { changeShot(shot.copy(side=shot.side+delta.first,top=shot.top+delta.second)) }.apply {
                textSize=12f; setPadding(dp(4),dp(8),dp(4),dp(8)); maxLines=1
            }.also { adjustable+=it },
                LinearLayout.LayoutParams(0,dp(48),1f).apply { marginStart=dp(2); marginEnd=dp(2) })
        }
        effects.addView(fineSpin)
        effects.addView(button(R.string.billiard_spin_reset) { changeShot(shot.copy(side=0.0,top=0.0)) }.also { adjustable+=it },LinearLayout.LayoutParams(-1,dp(44)))
        elevationLabel=text(R.string.billiard_elevation,12f).also { effects.addView(it) }
        elevationBar=seek(effects,80,shot.elevation.toInt()) { p -> changeShot(shot.copy(elevation=p.toDouble())) }
        effects.addView(button(R.string.billiard_close) { closeImpactPanel() })
        val spinIcon=tool(BilliardIconView.Icon.SPIN,R.string.billiard_spin) { effectsPanel.visibility=if(effectsPanel.visibility==View.GONE) View.VISIBLE else View.GONE }
        impactButton=spinIcon
        spinIcon.setOnClickListener { cancelPowerShot(); effectsPanel.visibility=if(effectsPanel.visibility==View.GONE) View.VISIBLE else View.GONE; spinIcon.active=effectsPanel.visibility==View.VISIBLE }
        placementButton=tool(BilliardIconView.Icon.PLACE,R.string.billiard_place) {
            if(callDraft==null && s.ready && !thinking && !(s.mode==PlayMode.COMPUTER && s.match.player==1) && (config.mode==PlayMode.PRACTICE || s.match.ballInHand)) {
                cancelPowerShot(); placing=!placing
                if(placing) showCamera(BilliardCameraMode.TOP)
                updateInteraction()
            }
        }
        root.addView(tools,FrameLayout.LayoutParams(-2,-2,(if(landscape) Gravity.TOP or Gravity.END else Gravity.CENTER_VERTICAL or Gravity.START)).apply {
            if(landscape) { rightMargin=dp(8); topMargin=dp(6) } else leftMargin=dp(8)
        })
        powerGauge=BilliardPowerView(this).apply {
            setPower(powerProgress(shot.speed)/1000f); changed={ v -> this@BilliardActivity.setPower(v) }
            timingStopped={ time ->
                if(powerArmed && !arcade && config.controlMode==BilliardControlMode.TIMING) finishPowerShot(stopTiming(time))
            }
        }
        adjustable+=powerGauge!!
        val gaugeHeight=if(landscape) 220 else 280
        root.addView(powerGauge,FrameLayout.LayoutParams(dp(100),dp(gaugeHeight),Gravity.TOP or Gravity.END).apply {
            rightMargin=dp(if(landscape) sideWidth+16 else 8)
        })
        val dock=column().apply {
            background=plate(0xED101B22.toInt(),22); setPadding(dp(10),dp(4),dp(10),dp(8))
            // Children keep their controls; padding, labels and gaps consume stray touches.
            isClickable=true; isFocusable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        targetLabel=text(R.string.billiard_target,12f).also { it.setTextColor(0xFFE4C488.toInt()); it.maxLines=2; dock.addView(it) }
        val actions=row()
        callButton=button(R.string.billiard_nominate_short) { nominate() }.also { actions.addView(it) }
        callBackButton=button(R.string.billiard_change_ball) { previousCallStep() }.also { actions.addView(it) }
        pushButton=button(R.string.billiard_push_out) { s.pushOut=!s.pushOut; s.safety=false; schedulePrediction(); refreshStatus(); save() }.also { actions.addView(it) }
        safetyButton=button(R.string.billiard_safety) { s.safety=!s.safety; s.pushOut=false; schedulePrediction(); refreshStatus(); save() }.also { actions.addView(it) }
        dock.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(actions) })
        decisions=column().also { choices ->
            val primary=row()
            primary.addView(button(R.string.billiard_play_here) { decideShot(true) },LinearLayout.LayoutParams(0,-2,1f))
            decisionAlternative=button(R.string.billiard_pass_back) { decideShot(false) }.also { primary.addView(it,LinearLayout.LayoutParams(0,-2,1f)) }
            choices.addView(primary)
            decisionRebreak=button(R.string.billiard_opponent_rebreak) { decideShot(false,true) }.also { choices.addView(it) }
            dock.addView(choices)
        }
        hint=text(R.string.billiard_aim_hint,11f).also { it.text=""; it.visibility=View.GONE; it.maxLines=2; it.setTextColor(0xFFB2C5C4.toInt()); dock.addView(it) }
        val aimingRow=row().apply { gravity=Gravity.CENTER_VERTICAL }
        fineAimRow=aimingRow
        val dialPrefs=getSharedPreferences("billiards",MODE_PRIVATE)
        val dial=BilliardAngleDial(this).apply {
            precision=dialPrefs.getBoolean("dialPrecision",false)
            setAngle(shot.angle); adjusted={ delta -> changeShot(shot.copy(angle=shot.angle+delta)) }
        }
        angleDial=dial; adjustable+=dial
        fun stepButton(forward: Boolean)=button(if(forward) R.string.billiard_dial_plus else R.string.billiard_dial_minus) { dial.nudge(forward) }.apply {
            textSize=24f; setPadding(0,0,0,0); adjustable+=this
        }
        val minus=stepButton(false); val plus=stepButton(true)
        aimingRow.addView(minus,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(4) })
        aimingRow.addView(dial,LinearLayout.LayoutParams(0,dp(48),1f))
        aimingRow.addView(plus,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginStart=dp(4) })
        val aimActions=row().apply { gravity=Gravity.CENTER_VERTICAL }
        val precisionColumn=column().apply { setPadding(0,dp(10),0,0) }.also { precisionControls=it }
        fun precisionState() {
            minus.contentDescription=getString(if(dial.precision) R.string.billiard_fine_minus_precise else R.string.billiard_fine_minus)
            plus.contentDescription=getString(if(dial.precision) R.string.billiard_fine_plus_precise else R.string.billiard_fine_plus)
        }
        val precise=precisionToggle(R.string.billiard_dial_precision,dial.precision) { checked ->
            dial.precision=checked; dialPrefs.edit().putBoolean("dialPrecision",checked).apply(); precisionState()
        }.also { adjustable+=it }
        precisionState()
        precisionColumn.addView(precise,LinearLayout.LayoutParams(-2,-2))
        angleLabel=text(R.string.billiard_fine_label,11f).also { it.setPadding(dp(6),0,0,dp(2)); precisionColumn.addView(it) }
        aimActions.addView(precisionColumn,LinearLayout.LayoutParams(0,-2,1f))
        shootButton=button(R.string.billiard_shoot) {
            if(run?.stage?.let { it!=JourneyStage.PLAYING }==true) showRunResult()
            else if(run?.mode==BilliardActivityMode.TIMED && !clockArmed) { clockArmed=true; decisionClock.pause(); refreshStatus(); startTick() }
            else if(callDraft!=null) { if(callDraft?.step==BilliardCallStep.RAILS && callDraft?.rails?.isNotEmpty()==true) finishCall(true) }
            else if(placing) { if(s.cuePlacementValid) { placing=false; invalidPlacement=false; updateInteraction(); save() }
                else { invalidPlacement=true; refreshStatus() } } else requestPlayerShot()
        }.apply { accent(this); textSize=14f; maxLines=2; setPadding(dp(6),dp(6),dp(6),dp(6)) }
        var shotButtonPress=false
        shootButton?.setOnTouchListener { view,event ->
            val startsPreparation=(run?.mode!=BilliardActivityMode.TIMED || clockArmed) && run?.stage?.let { it==JourneyStage.PLAYING }!=false && !powerArmed && callDraft==null && !placing && !incompleteCall(s) &&
                (arcade || config.controlMode!=BilliardControlMode.GAUGE)
            val stopsTiming=powerArmed && (arcade || config.controlMode==BilliardControlMode.TIMING)
            // Ready responds to the first contact. Keep this entire gesture even if
            // opening the meter relays out the dock; UP must not perform a second action.
            if(event.actionMasked==MotionEvent.ACTION_DOWN && event.pointerCount==1 && view.isEnabled &&
                (startsPreparation || stopsTiming)) {
                shotButtonPress=true; view.isPressed=true
                view.parent.requestDisallowInterceptTouchEvent(true)
                if(stopsTiming) {
                    if(arcade) finishArcadeShot(event.eventTime) else finishPowerShot(powerGauge?.stopTiming(event.eventTime) ?: 0f)
                } else view.performClick()
                true
            } else if(shotButtonPress) {
                if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) {
                    shotButtonPress=false; view.isPressed=false
                    view.parent.requestDisallowInterceptTouchEvent(false)
                }
                true
            } else false
        }
        if(!landscape) aimActions.addView(shootButton,LinearLayout.LayoutParams(dp(100),dp(54)).apply { leftMargin=dp(6) })
        dock.addView(aimingRow)
        dock.addView(aimActions)
        precisionGauge=BilliardArcadeTimingView(this).apply {
            visibility=View.GONE; stopped={ time -> finishArcadeShot(time) }
        }
        dock.addView(precisionGauge,LinearLayout.LayoutParams(-1,dp(64)))
        if(landscape) dock.addView(shootButton,LinearLayout.LayoutParams(-1,dp(54)).apply { topMargin=dp(8) })
        cancelShotButton=button(R.string.billiard_cancel_shot) { cancelPowerShot() }.also {
            it.visibility=View.GONE; dock.addView(it,LinearLayout.LayoutParams(-1,dp(44)))
        }
        if(landscape) {
            root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled=false; addView(dock) },FrameLayout.LayoutParams(dp(sideWidth),-1,Gravity.END).apply { setMargins(0,dp(68),dp(8),dp(8)) })
            renderer.hudLeft=dp(14).toFloat(); renderer.hudBottom=dp(14).toFloat()
        } else {
            root.addView(dock,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { setMargins(dp(10),0,dp(10),dp(10)) })
            dock.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
                val bottom=(dock.height+dp(20)).toFloat()
                if(renderer.hudBottom!=bottom) { renderer.hudBottom=bottom; gl.wakeCamera() }
            }
        }
        headerPanel.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
            val top=headerPanel.height.toFloat()
            if(renderer.hudTop!=top) { renderer.hudTop=top; gl.wakeCamera() }
        }
        strokeView=BilliardStrokeView(this).apply {
            visibility=View.GONE; fired={ value -> finishPowerShot(value) }
        }
        root.addView(strokeView,FrameLayout.LayoutParams(dp(150),dp(240),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            if(landscape) rightMargin=dp(sideWidth+110)
        })
        fun layoutGauge() {
            val top=headerPanel.height+dp(12)
            val bottom=if(landscape) dp(12) else dock.height+dp(22)
            val available=(root.height-root.paddingTop-root.paddingBottom-top-bottom).coerceAtLeast(0)
            // Assigning layoutParams always requests another layout. Called from the layout
            // listeners below, an unconditional assignment looped at every frame and kept the
            // 3D table redrawing at 60 images/s while nothing moved: only real changes apply.
            fun place(view: View?,height: Int) {
                val params=view?.layoutParams as? FrameLayout.LayoutParams ?: return
                val nextTop=top+(available-height)/2
                if(params.height!=height || params.topMargin!=nextTop) { params.height=height; params.topMargin=nextTop; view.layoutParams=params }
            }
            place(powerGauge,min(dp(gaugeHeight),available))
            place(strokeView,min(dp(240),available))
            val right=dp(if(landscape) sideWidth+if(showPowerGauge) 120 else 24 else if(showPowerGauge) 112 else 14).toFloat()
            val left=if(landscape) renderer.hudLeft else dp(66).toFloat()
            if(renderer.hudRight!=right || renderer.hudLeft!=left) { renderer.hudRight=right; renderer.hudLeft=left; gl.wakeCamera() }
        }
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> layoutGauge() }
        headerPanel.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> layoutGauge() }
        dock.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> layoutGauge() }
        root.addView(effectsPanel,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        gl.setOnTouchListener { _,_ -> labels.invalidate(); false }
        if(resumed) gl.onResume()
        updateInteraction(); gl.wakeCamera()
        updateControlText(); publish(); awaitCamera(); refreshStatus(); startTick(); schedulePrediction()
    }
    private fun showLounge(returnTo: BilliardRun?=null) {
        val previousConfig=config
        if(menuPreview==null) releaseTable()
        run=null; setupVisible=false; setupMenu=null; menuInitialized=false; readMenuConfig()
        val backdrop=menuBackdrop()
        lounge=BilliardLounge(this,progress,store.exists,::resumeTable,::showMenu,::launchJourney,
            backdrop::show,backdrop::viewport,::loungeSettings,{ config },::setFinish,::finish).also {
            it.open(returnTo,previousConfig); menuContent(it.root)
        }
    }
    /**
     * Settings shared by every mode of the lounge, saved as soon as they change. After a choice
     * the list opens again, showing the new value.
     */
    private fun loungeSettings() {
        val prefs=getSharedPreferences("billiards",MODE_PRIVATE)
        lateinit var dialog: AlertDialog
        val form=column().apply { setPadding(dp(16),dp(8),dp(16),dp(16)) }
        fun option(label: Int,items: List<String>,value: Int,changed: (Int)->Unit)=
            settingLine(form,label,items[value]) { dialog.dismiss(); choose(label,items,value) { changed(it); loungeSettings() } }
        option(R.string.billiard_game_style,BilliardGameStyle.entries.map { getString(it.title) },config.style.ordinal) {
            config=config.copy(style=BilliardGameStyle.entries[it]); prefs.edit().putString("style",config.style.name).apply()
        }
        if(!arcade) option(R.string.billiard_input_mode,BilliardControlMode.entries.map { getString(it.title) },config.controlMode.ordinal) {
            config=config.copy(controlMode=BilliardControlMode.entries[it]); prefs.edit().putString("controlMode",config.controlMode.name).apply()
        }
        option(R.string.billiard_assistance,listOf(R.string.billiard_aid_none,R.string.billiard_aid_contact,R.string.billiard_aid_full).map(::getString),config.assistance) {
            config=config.copy(assistance=it); prefs.edit().putInt("aid",it).apply()
        }
        option(R.string.billiard_look,BilliardLook.entries.map { getString(it.title) },config.look.ordinal) { setLook(BilliardLook.entries[it]) }
        settingSwitch(form,R.string.billiard_rotation_stripes,config.rotationStripes,::setRotationStripes)
        settingSwitch(form,R.string.billiard_sound,config.sound) {
            config=config.copy(sound=it); prefs.edit().putBoolean("sound",it).apply()
        }
        dialog=AlertDialog.Builder(this).setTitle(R.string.billiard_preferences)
            .setView(ScrollView(this).apply { addView(form) }).setPositiveButton(R.string.billiard_close,null).create()
        showDialog(dialog)
    }
    private fun launchJourney(event: BilliardRun,discipline: Discipline,room: BilliardRoom,difficulty: Int) {
        run=event
        val c=config.copy(discipline=discipline,room=room,mode=if(event.solo) PlayMode.PRACTICE else PlayMode.COMPUTER,
            difficulty=difficulty,tableSize=BilliardTableSize.defaultFor(discipline.family),cloth=1,
            finish=config.finish.coerceAtMost(progress.clubCount))
        val s=BilliardSession(c.discipline,c.mode,c.cloth,c.tableSize)
        if(event.exercise>=0) BilliardChallenges.all[event.exercise].install(s)
        else if(event.solo && discipline.family!=TableFamily.CAROM) BilliardChallenges.spread(s,0)
        openTable(c,s,Shot(initialAngle(s),cueMass=c.cue.mass,endMass=c.cue.endMass)); save()
    }
    private fun initialAngle(s: BilliardSession): Double {
        val cue=s.cue ?: return 0.0
        val target=s.legalTargets().firstOrNull() ?: return 0.0
        return atan2(target.p.y-cue.p.y,target.p.x-cue.p.x)
    }
    private fun exerciseDescription(e: BilliardExercise)=getString(when(e.goal) {
        BilliardGoal.CLEAR -> R.string.billiard_objective_clear
        BilliardGoal.BANK -> R.string.billiard_objective_bank
        BilliardGoal.ORDER -> R.string.billiard_objective_order
        BilliardGoal.DRAW -> R.string.billiard_objective_draw
        BilliardGoal.FOLLOW -> R.string.billiard_objective_follow
        BilliardGoal.CAROM -> R.string.billiard_objective_carom
    },if(e.goal==BilliardGoal.CAROM) e.requiredRails else e.count,e.maxShots)
    private fun playerName(player: Int)=if(config.mode==PlayMode.LOCAL && run?.mode==BilliardActivityMode.QUICK) getString(R.string.billiard_player_name,player+1)
        else if(player==0) getString(R.string.billiard_you) else resources.getStringArray(R.array.billiard_rivals)[(player-1).coerceIn(0,6)]
    private fun standings(event: BilliardRun): String {
        val lines=mutableListOf<String>()
        if(event.mode==BilliardActivityMode.LEAGUE) {
            lines+=getString(R.string.billiard_league_round,event.round+1)
            lines+=event.leagueOrder.map { player ->
                val rank=1+event.points.count { it>event.points[player] }
                getString(R.string.billiard_standing_row,rank,playerName(player),event.points[player])
            }
        } else if(event.mode==BilliardActivityMode.CUP) {
            lines+=getString(R.string.billiard_cup_round,event.round+1)
            lines+=event.roster.chunked(2).map { pair -> if(pair.size==2) getString(R.string.billiard_duel_pair,playerName(pair[0]),playerName(pair[1])) else playerName(pair[0]) }
        }
        if(event.history.isNotEmpty()) {
            lines+=""
            event.history.takeLast(8).forEach { entry ->
                val row=entry.split(',').mapNotNull(String::toIntOrNull)
                if(row.size==4) lines+=getString(R.string.billiard_match_history,playerName(row[1]),playerName(row[2]),playerName(row[3]))
            }
        }
        return lines.joinToString("\n")
    }
    private fun showJourneyInfo() {
        val event=run ?: return
        val detail=when {
            event.exercise>=0 -> {
                val e=BilliardChallenges.all[event.exercise]
                exerciseDescription(e)+"\n\n"+getString(BilliardLounge.lessonText(e.goal))
            }
            event.mode==BilliardActivityMode.SERIES -> getString(if(config.discipline.family==TableFamily.CAROM) R.string.billiard_carom_series_hint else R.string.billiard_series_hint)
            event.mode==BilliardActivityMode.TIMED -> getString(R.string.billiard_timed_hint)
            else -> getString(R.string.billiard_match_settings,getString(config.discipline.titleRes()),event.raceTo,getString(difficultyLevels[config.difficulty]))+
                "\n\n"+getString(R.string.billiard_opponent_detail,playerName(event.opponent),getString(difficultyLevels[config.difficulty]))+
                "\n\n"+standings(event)
        }
        val content=column().apply { setPadding(dp(20),dp(12),dp(20),dp(12)) }
        if(!event.solo) {
            val avatar=text(R.string.billiard_opponent,30f).apply {
                text=playerName(event.opponent).take(1); gravity=Gravity.CENTER; background=plate(0xFF385B5C.toInt(),36)
            }
            content.addView(avatar,LinearLayout.LayoutParams(dp(64),dp(64)).apply { gravity=Gravity.CENTER_HORIZONTAL })
        }
        content.addView(text(R.string.billiard_objective,15f).apply { text=detail })
        showDialog(AlertDialog.Builder(this).setTitle(BilliardLounge.modeName(event.mode)).setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.billiard_close,null).create())
    }
    private fun handleJourneyShot(s: BilliardSession) {
        val event=run ?: return
        if(event.stage!=JourneyStage.PLAYING || s.replaying || s.match.shots<=event.handledShot) return
        event.handledShot=s.match.shots
        if(!event.solo) {
            if(s.before?.match?.player==0) {
                event.shots++
                if(s.match.foul==Foul.NONE) event.score+=maxOf(s.match.points,s.world.events.count { it.kind==EventKind.POCKET && it.ball!=s.cueId })
            }
            return
        }
        event.shots++
        if(event.exercise>=0) {
            val e=BilliardChallenges.all[event.exercise]
            val (scored,feedback)=e.assess(s,event)
            event.score+=scored; event.feedback=feedback
            if(event.score>=e.count) { event.medal=e.medal(event.shots); event.lastWinner=0; event.stage=JourneyStage.FINISHED }
            else if(event.shots>=e.maxShots || feedback in listOf(1,3)) { event.lastWinner=1; event.stage=JourneyStage.FINISHED }
            else if(e.goal !in listOf(BilliardGoal.CLEAR,BilliardGoal.ORDER)) {
                e.install(s)
            }
        } else {
            val foul=s.world.events.any { it.kind==EventKind.OFF_TABLE || it.kind==EventKind.POCKET && it.ball==s.cueId }
            val contact=s.world.events.any { it.kind==EventKind.BALL && (it.ball==s.cueId || it.other==s.cueId) }
            val scored=if(foul || !contact) 0 else if(s.discipline.family==TableFamily.CAROM) BilliardExercise(20).assess(s,event).first
                else s.world.events.count { it.kind==EventKind.POCKET && it.ball!=s.cueId }
            event.score+=scored
            if(scored==0) event.errors++
            event.feedback=if(foul) 1 else if(scored==0) 7 else 0
            if(foul && s.discipline.family==TableFamily.CAROM) {
                s.before?.let { before -> s.restore(before.copy(match=s.match.copy())) }
            }
            if(event.mode==BilliardActivityMode.TIMED) event.remainingMillis=(event.remainingMillis+scored*3000L-if(foul) 5000L else 0L).coerceAtLeast(0)
            if(event.mode==BilliardActivityMode.SERIES && event.errors>=3 || event.mode==BilliardActivityMode.TIMED && event.remainingMillis==0L) event.stage=JourneyStage.FINISHED
            else if(s.discipline.family!=TableFamily.CAROM && s.world.balls.none { it.id!=s.cueId && it.motion!=Motion.POCKETED }) {
                BilliardChallenges.spread(s,event.score/6)
            }
        }
    }
    private fun awaitCamera() {
        val renderer=scene ?: return
        cameraRecovery=true; recoveryFrame=renderer.renderedFrames; decisionClock.pause()
        surface?.wakeCamera(); startTick()
    }
    private fun recoverShotCamera() {
        returnCamera?.let { scene?.camera?.restore(it,resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE) }
        returnCamera=null; awaitCamera()
    }
    private fun updateJourneyClock(s: BilliardSession) {
        val renderer=scene
        if(cameraRecovery && renderer!=null && renderer.renderedFrames>recoveryFrame+1 && !renderer.camera.settling && !renderer.visualAnimating) cameraRecovery=false
        val event=run ?: return
        if(event.mode!=BilliardActivityMode.TIMED) return
        val playable=resumed && clockArmed && modalCount==0 && impactPanel?.visibility!=View.VISIBLE && !cameraRecovery &&
            event.stage==JourneyStage.PLAYING && s.ready && !s.isShotActive && !s.replaying && renderer?.visualAnimating!=true
        val now=android.os.SystemClock.elapsedRealtime()
        val elapsed=decisionClock.step(now,playable)
        if(elapsed>0) {
            event.remainingMillis=(event.remainingMillis-elapsed).coerceAtLeast(0)
            if(event.remainingMillis==0L) {
                event.stage=JourneyStage.FINISHED; cancelPowerShot(); save()
            } else if(now-lastClockSave>=3000) { lastClockSave=now; save() }
        }
    }
    private fun feedbackText(value: Int)=getString(when(value) {
        1 -> R.string.billiard_feedback_scratch
        2 -> R.string.billiard_feedback_contact
        3 -> R.string.billiard_feedback_order
        4 -> R.string.billiard_feedback_bank
        5 -> R.string.billiard_feedback_zone
        6 -> R.string.billiard_feedback_carom
        else -> R.string.billiard_feedback_miss
    })
    private fun refreshJourneyStatus() {
        val event=run ?: return
        journeyLabel?.show(when {
            event.exercise>=0 -> BilliardChallenges.all[event.exercise].let { e ->
                getString(R.string.billiard_journey_progress_tries,getString(BilliardLounge.goalName(e.goal)),event.score,e.count,
                    resources.getQuantityString(R.plurals.billiard_tries_left,(e.maxShots-event.shots).coerceAtLeast(0),(e.maxShots-event.shots).coerceAtLeast(0)))
            }
            event.mode==BilliardActivityMode.TIMED -> {
                val seconds=(event.remainingMillis+999)/1000
                getString(R.string.billiard_journey_time,seconds/60,seconds%60,event.score)
            }
            event.mode==BilliardActivityMode.SERIES -> getString(R.string.billiard_journey_score,event.score,(3-event.errors).coerceAtLeast(0))
            else -> getString(R.string.billiard_racks_score,playerName(0),event.racks0,event.racks1,playerName(event.displayedOpponent))
        })
        if(event.solo) {
            targetLabel?.show(if(event.exercise>=0) exerciseDescription(BilliardChallenges.all[event.exercise]) else getString(BilliardLounge.modeName(event.mode)))
            targetLabel?.visibility=if(event.exercise>=0) View.VISIBLE else View.GONE
            showHint(if(event.mode==BilliardActivityMode.TIMED && clockArmed && session?.world?.moving==true) getString(R.string.billiard_clock_paused) else "")
        }
    }
    private fun showRunResult() {
        val event=run ?: return
        if(event.stage==JourneyStage.PLAYING) return
        resultPresented=true; decisionClock.pause()
        var reward=0
        if(event.stage==JourneyStage.FINISHED) runCatching { reward=progress.finish(event,config) }
            .onFailure { resultPresented=false; Toast.makeText(this,R.string.billiard_save_error,Toast.LENGTH_LONG).show() }
        val title=when {
            event.stage==JourneyStage.RACK_RESULT -> R.string.billiard_result_rack
            event.stage==JourneyStage.MATCH_RESULT -> if(event.lastWinner==0) R.string.billiard_result_win else R.string.billiard_result_loss
            event.mode==BilliardActivityMode.LEAGUE -> if(event.champion) R.string.billiard_champion else R.string.billiard_season_complete
            event.solo && event.exercise<0 -> R.string.billiard_result_done
            event.champion -> R.string.billiard_result_win
            else -> R.string.billiard_result_loss
        }
        val detail=if(event.solo) {
            (if(event.exercise>=0) getString(R.string.billiard_result_medal,event.medal,event.shots) else getString(R.string.billiard_result_points,event.score,event.shots))+
                if(event.feedback>0) "\n\n"+feedbackText(event.feedback) else ""
        } else getString(R.string.billiard_racks_score,playerName(0),event.racks0,event.racks1,playerName(event.displayedOpponent))+"\n\n"+standings(event)
        val message=detail+(if(reward>0) "\n\n"+getString(R.string.billiard_result_reward,reward) else "")+
            if(event.mode==BilliardActivityMode.CAREER && event.champion && event.careerStep%4==3) "\n\n"+getString(R.string.billiard_badge_unlock,getString(config.room.title)) else ""
        val dialog=AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton(if(event.mode==BilliardActivityMode.CHALLENGE) R.string.billiard_challenges else R.string.billiard_back) { _,_ -> save(); showLounge(event) }
        if(event.stage!=JourneyStage.FINISHED) {
            dialog.setPositiveButton(if(event.stage==JourneyStage.RACK_RESULT) R.string.billiard_next_rack else R.string.billiard_next_match) { _,_ ->
                event.nextRack()
                val s=BilliardSession(config.discipline,config.mode,config.cloth,config.tableSize).apply { startWithPlayer(event.rackNumber%2) }
                openTable(config,s,Shot(initialAngle(s),cueMass=config.cue.mass,endMass=config.cue.endMass)); save()
            }
        } else {
            val nextExercise=when(event.mode) {
                BilliardActivityMode.CHALLENGE -> BilliardChallenges.all.getOrNull(event.exercise+1)?.id
                BilliardActivityMode.LESSON -> BilliardChallenges.lessons.firstOrNull { it>event.exercise }
                else -> null
            }
            when {
                nextExercise!=null -> {
                    dialog.setPositiveButton(if(event.mode==BilliardActivityMode.LESSON) R.string.billiard_next_lesson else R.string.billiard_next_challenge) { _,_ ->
                        val exercise=BilliardChallenges.all[nextExercise]
                        launchJourney(BilliardRun(mode=event.mode,exercise=exercise.id),exercise.discipline,config.room,config.difficulty)
                    }
                    dialog.setNeutralButton(R.string.billiard_retry) { _,_ -> restartTable() }
                }
                event.mode==BilliardActivityMode.CAREER && event.champion && progress.careerStep<20 -> {
                    dialog.setPositiveButton(R.string.billiard_next_event) { _,_ -> launchCareerStep(progress.careerStep) }
                    dialog.setNeutralButton(R.string.billiard_retry) { _,_ -> restartTable() }
                }
                else -> dialog.setPositiveButton(R.string.billiard_retry) { _,_ -> restartTable() }
            }
        }
        showDialog(dialog.create())
    }
    private fun restartTable() {
        val event=run
        if(event!=null && event.mode!=BilliardActivityMode.QUICK) {
            launchJourney(BilliardRun(mode=event.mode,raceTo=event.raceTo,careerStep=event.careerStep,exercise=event.exercise),config.discipline,config.room,config.difficulty)
        } else {
            run=event?.let { BilliardRun(raceTo=it.raceTo) }
            val s=BilliardSession(config.discipline,config.mode,config.cloth,config.tableSize)
            openTable(config,s,Shot(initialAngle(s),cueMass=config.cue.mass,endMass=config.cue.endMass)); save()
        }
    }
    private fun launchCareerStep(step: Int) {
        val club=step/4; val event=step%4
        val exercise=when(event) { 0 -> listOf(0,1,4,12,16)[club]; 2 -> listOf(1,8,5,13,17)[club]; else -> -1 }
        val d=if(exercise>=0) BilliardChallenges.all[exercise].discipline else listOf(Discipline.EIGHT,Discipline.NINE,Discipline.BLACKBALL,Discipline.NINE,Discipline.EIGHT)[club]
        val room=listOf(BilliardRoom.HOME,BilliardRoom.CLUB,BilliardRoom.LOFT,BilliardRoom.GARDEN,BilliardRoom.NEON)[club]
        launchJourney(BilliardRun(mode=BilliardActivityMode.CAREER,raceTo=if(event==3) 2 else 1,careerStep=step,exercise=exercise),d,room,minOf(2,club/2))
    }
    private fun updateInteraction() {
        surface?.placing=placing
        placementButton?.active=placing; refreshStatus(); surface?.wakeCamera()
        if(placing) invalidateArcadePreparation() else if(!arcadeRequested) scheduleArcadePreparation()
    }
    private fun closeImpactPanel() {
        impactPanel?.visibility=View.GONE; impactButton?.active=false
    }
    private fun cameraLabel(mode: BilliardCameraMode)=when(mode) {
        BilliardCameraMode.CLOSE -> R.string.billiard_camera_close
        BilliardCameraMode.WIDE -> R.string.billiard_camera_wide
        BilliardCameraMode.TOP -> R.string.billiard_camera_top_2d
        BilliardCameraMode.FREE -> R.string.billiard_camera_free
    }
    private fun selectedControl(view: TextView?,selected: Boolean) {
        if(view==null || view.tag==selected) return
        view.tag=selected; view.isSelected=selected
        if(selected) accent(view) else { view.background=plate(); view.setTextColor(0xFFF1EBDD.toInt()) }
    }
    private fun refreshCameraControls() {
        val mode=cameraViewState()?.mode ?: return
        cameraButton?.let { button ->
            if(button.tag!=mode) {
                val next=nextCameraMode(mode)
                button.tag=mode; button.text=getString(R.string.billiard_camera_cycle_label,getString(cameraLabel(mode)))
                button.contentDescription=getString(R.string.billiard_camera_cycle_description,getString(cameraLabel(mode)),getString(cameraLabel(next)))
            }
        }
    }
    // Tracking shots and ball/pocket announcements must not replace the player's view slots.
    private fun cameraViewState()=callDraft?.camera ?: returnCamera ?: scene?.camera?.state()
    private fun rememberCameraView() { cameraViewState()?.let { cameraViews[it.mode]=it } }
    private fun nextCameraMode(mode: BilliardCameraMode)=BilliardCameraMode.entries[(mode.ordinal+1)%BilliardCameraMode.entries.size]
    private fun cycleCamera() { cameraViewState()?.let { showCamera(nextCameraMode(it.mode)) } }
    private fun showCamera(mode: BilliardCameraMode,reset: Boolean=false) {
        val camera=scene?.camera ?: return
        rememberCameraView()
        val portrait=resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE
        val remembered=if(reset) null else cameraViews[mode]
        if(remembered!=null) camera.restore(remembered,portrait) else camera.select(mode,portrait)
        val selected=camera.state()
        cameraViews[mode]=selected
        if(returnCamera!=null) returnCamera=selected
        callDraft=callDraft?.copy(camera=selected)
        awaitCamera()
        refreshCameraControls(); refreshStatus(); save()
    }
    private fun watchShot(angle: Double=shot.angle) {
        scene?.camera?.watchShot(angle)
        refreshCameraControls(); surface?.wakeCamera()
    }
    private fun seek(parent: LinearLayout,max: Int,progress: Int,changed: (Int)->Unit): SeekBar {
        return SeekBar(this).apply {
            this.max=max; this.progress=progress.coerceIn(0,max)
            progressTintList=ColorStateList.valueOf(0xFFE4C488.toInt()); thumbTintList=progressTintList
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?,p: Int,user: Boolean) { if(user && !settingControls) changed(p) }
                override fun onStartTrackingTouch(b: SeekBar?) { b?.parent?.requestDisallowInterceptTouchEvent(true) }
                override fun onStopTrackingTouch(b: SeekBar?) { b?.parent?.requestDisallowInterceptTouchEvent(false) }
            })
            adjustable+=this; parent.addView(this,LinearLayout.LayoutParams(-1,dp(42)))
        }
    }
    private fun powerProgress(speed: Double)=(((speed-.05)/7.95).coerceIn(0.0,1.0).pow(1/1.7)*1000).roundToInt()
    private fun setPower(value: Float) = changeShot(shot.copy(speed=.05+7.95*value.coerceIn(0f,1f).toDouble().pow(1.7)))
    private fun requestPlayerShot() {
        val s=session ?: return
        if(!resumed || modalCount>0 || !s.ready || thinking || s.mode==PlayMode.COMPUTER && s.match.player==1) return
        if(!s.cuePlacementValid || incompleteCall(s)) { cancelPowerShot(); shoot(); return }
        if(arcade) {
            if(powerArmed) finishArcadeShot() else armPowerShot()
            return
        }
        when(config.controlMode) {
            BilliardControlMode.GAUGE -> shoot()
            BilliardControlMode.GESTURE -> if(powerArmed) cancelPowerShot() else armPowerShot()
            BilliardControlMode.TIMING -> if(powerArmed) finishPowerShot(powerGauge?.stopTiming() ?: 0f) else armPowerShot()
        }
    }
    private fun armPowerShot() {
        powerOriginal=shot; powerArmed=true
        impactPanel?.visibility=View.GONE; impactButton?.active=false
        generation++; handler.removeCallbacks(prediction); scene?.trace=null
        if(arcade) {
            handler.removeCallbacks(arcadePreparation)
            if(!arcadeRequested) requestArcadePreparation()
            precisionGauge?.start()
        } else if(config.controlMode==BilliardControlMode.TIMING) powerGauge?.startTiming()
        strokeView?.reset()
        refreshStatus(); surface?.requestRender()
    }
    private fun finishArcadeShot(atMillis: Long = android.os.SystemClock.uptimeMillis()) {
        val s=session ?: return
        if(!arcade || !powerArmed || !resumed || modalCount>0 || !s.ready || thinking || s.mode==PlayMode.COMPUTER && s.match.player==1) {
            cancelPowerShot(); return
        }
        val quality=precisionGauge?.stop(atMillis) ?: 0.0
        val original=powerOriginal ?: shot
        // No simulation, scoring, waiting, or interpolation on the main thread.
        shot=arcadePlan?.takeIf { it.original==original }?.select(quality) ?: original
        powerArmed=false; powerOriginal=null
        syncControls(); updateControlText(); refreshStatus(); shoot()
    }
    private fun finishPowerShot(value: Float) {
        val s=session ?: return
        if(!powerArmed || !resumed || modalCount>0 || !s.ready || thinking || s.mode==PlayMode.COMPUTER && s.match.player==1) {
            cancelPowerShot(); return
        }
        powerArmed=false; powerOriginal=null
        powerGauge?.stopTiming(); strokeView?.reset(); strokeView?.visibility=View.GONE
        setPower(value); refreshStatus(); shoot()
    }
    private fun cancelPowerShot() {
        if(!powerArmed) return
        powerArmed=false
        precisionGauge?.stop()
        powerGauge?.stopTiming(); strokeView?.reset(); strokeView?.visibility=View.GONE
        powerOriginal?.let { shot=it }; powerOriginal=null
        syncControls(); updateControlText(); refreshStatus(); publish(); schedulePrediction()
    }
    private fun changeShot(value: Shot) {
        val s=session ?: return
        if(powerArmed || callDraft!=null || run?.stage?.let { it!=JourneyStage.PLAYING }==true || !s.ready || thinking || s.mode==PlayMode.COMPUTER && s.match.player==1) return
        val spinLength=hypot(value.side,value.top)
        val spinScale=if(spinLength>.8) .8/spinLength else 1.0
        val next=value.copy(angle=((value.angle%(2*PI))+2*PI)%(2*PI),side=value.side*spinScale,top=value.top*spinScale,
            cueMass=config.cue.mass,endMass=config.cue.endMass)
        if(next==shot) return
        shot=next
        syncControls()
        updateControlText(); publish(); if(scene?.camera?.behindCue==true) surface?.wakeCamera(); schedulePrediction(liveEdit=true)
    }
    private fun syncControls(value: Shot=shot) {
        settingControls=true
        angleBar?.progress=(value.angle*180/PI*200).roundToInt()
        angleDial?.setAngle(value.angle)
        if(powerGauge?.timing!=true) powerGauge?.setPower(powerProgress(value.speed)/1000f)
        powerBar?.progress=powerProgress(value.speed)
        spin?.set(value.side,value.top); settingControls=false
    }
    /** The bar starts at the lowest elevation the cue can take from here. */
    private fun showElevation(floor: Int,elevation: Double) {
        val degrees=elevation.roundToInt()
        elevationBar?.let { bar ->
            settingControls=true
            if(bar.min!=floor) bar.min=floor
            if(bar.progress!=degrees) bar.progress=degrees
            settingControls=false
        }
        val text=getString(R.string.billiard_elevation,degrees)
        if(elevationLabel?.text?.toString()!=text) elevationLabel?.text=text
    }
    private fun updateControlText(value: Shot=shot) {
        fun update(label: TextView?,text: String) { if(label?.text?.toString()!=text) label?.text=text }
        update(powerLabel,getString(R.string.billiard_power,(powerProgress(value.speed)/10.0).roundToInt()))
        update(angleLabel,getString(R.string.billiard_angle,value.angle*180/PI))
        update(spinLabel,getString(R.string.billiard_spin_value,value.side,value.top))
    }
    private fun tableTouch(p: V3,down: Boolean) {
        val s=session ?: return
        if(powerArmed || callDraft!=null || run?.stage?.let { it!=JourneyStage.PLAYING }==true || !s.ready || thinking || s.mode==PlayMode.COMPUTER && s.match.player==1) return
        if(placing && (run?.solo!=true || s.match.ballInHand)) {
            if(down) dragging=s.world.balls.filter { it.motion!=Motion.POCKETED && (config.mode==PlayMode.PRACTICE || it.id==s.cueId) }
                .minByOrNull { (it.p-p).length() }?.takeIf { (it.p-p).length()<s.table.radius*3 }?.id ?: if(config.mode!=PlayMode.PRACTICE) s.cueId else -1
            if(dragging>=0) { invalidPlacement=!s.moveBall(dragging,p); refreshStatus(); if(!invalidPlacement) { publish(); schedulePrediction() } }
        }
    }
    private fun incompleteCall(s: BilliardSession)=s.needsCall && (s.nominated<0 ||
        s.discipline.family!=TableFamily.SNOOKER && s.calledPocket !in s.table.pockets.indices ||
        s.discipline==Discipline.BANK && s.bankRails.isEmpty())
    private fun shoot() {
        val s=session ?: return
        updateJourneyClock(s)
        if(run?.stage?.let { it!=JourneyStage.PLAYING }==true || cameraRecovery || run?.mode==BilliardActivityMode.TIMED && !clockArmed) return
        if(!resumed || modalCount>0 || powerArmed || callDraft!=null || !s.ready || thinking || config.mode==PlayMode.COMPUTER && s.match.player==1) return
        if(!s.cuePlacementValid) { placing=true; invalidPlacement=true; updateInteraction(); return }
        if(incompleteCall(s)) { nominate(); return }
        generation++; handler.removeCallbacks(prediction); scene?.trace=null
        invalidateArcadePreparation()
        val ball=s.cue?.copyDeep(); val rackBreak=s.match.breaking && s.world.balls.size>6
        if(s.shoot(shot)) { returnCamera=scene?.camera?.state(); decisionClock.pause(); if(ball!=null) scene?.animateStrike(ball,s.lastShot ?: shot); placing=false; watchShot(); audio.strike(shot.speed,rackBreak); lastFrame=0; startTick(); publish(); refreshStatus(); save() }
    }
    private fun undoPracticeShot() {
        val s=session ?: return
        if(s.mode!=PlayMode.PRACTICE || thinking || callDraft!=null) return
        val previousShot=s.lastShot ?: return
        if(!s.undo()) return
        // The snapshot restores the table; the aim controls may have changed since the shot.
        shot=previousShot
        winnerShown=-1; placing=s.match.ballInHand; invalidPlacement=false; dragging=-1
        syncControls(); updateControlText(); schedulePrediction()
        updateInteraction(); publish(); save()
    }
    private fun publish() {
        val s=session ?: return
        val aim=computerAim
        val chosen=aim?.selected
        scoreboard?.playerNames=run?.takeUnless { it.solo }?.let { listOf(playerName(0),playerName(it.displayedOpponent)) }
        scoreboard?.update(s,thinking,chosen?.ball)
        // The cue cannot lie in the rail: draw and show the elevation it will really take.
        val aimed=aim?.visualShot ?: shot
        val cueBall=s.cue?.takeIf { !s.world.moving }
        val floor=cueBall?.let { CueReach.minimum(s.world,it,aimed) } ?: 0
        val cueShot=if(aimed.elevation>=floor) aimed else aimed.copy(elevation=floor.toDouble())
        if(cueBall!=null) showElevation(floor,cueShot.elevation)
        val next=BilliardFrame(s.world.balls.map { it.copyDeep() },s.world.pins.map { it.copy() },s.cueId,
            cueShot,s.world.moving,chosen?.ball ?: s.nominated,chosen?.pocket ?: s.calledPocket,aim?.pullback ?: 0.0)
        val renderer=scene
        val hud=renderer?.let { listOf(it.hudTop,it.hudBottom,it.hudLeft,it.hudRight) }
        // The idle tick publishes five times a second: an identical picture is not drawn again.
        val unchanged=renderer!=null && renderer.frame?.looksLike(next)==true && renderer.trace===publishedTrace &&
            hud==publishedHud && !renderer.visualAnimating && !renderer.animatedScenery
        renderer?.frame=next; publishedTrace=renderer?.trace; publishedHud=hud
        if(!unchanged) surface?.requestRender()
        if(renderer?.camera?.settling==true) surface?.wakeCamera()
    }
    // Setting a text, even the same one, makes Android lay the whole screen out again. The
    // status runs at every frame while the balls roll: only real changes are applied.
    private fun TextView.show(value: CharSequence) { if(text.toString()!=value.toString()) text=value }
    private fun TextView.show(res: Int) = show(getString(res))
    private fun showHint(value: String) {
        hint?.let { it.show(value); val shown=if(value.isEmpty()) View.GONE else View.VISIBLE; if(it.visibility!=shown) it.visibility=shown }
    }
    private fun refreshStatus() {
        val s=session ?: return
        status?.show(if(config.mode==PlayMode.PRACTICE) getString(R.string.billiard_practice_score,s.match.shots)
            else getString(R.string.billiard_score,s.match.player+1,s.match.score0,s.match.score1))
        val editable=s.ready && !thinking && run?.stage?.let { it==JourneyStage.PLAYING }!=false && !(config.mode==PlayMode.COMPUTER && s.match.player==1)
        val draft=callDraft
        val selecting=draft!=null
        surface?.shotEditable=editable && !powerArmed
        surface?.placing=placing && editable
        surface?.selectingTarget=selecting && editable
        refreshCameraControls()
        adjustable.forEach { it.isEnabled=editable && !selecting && !powerArmed; it.alpha=if(editable && !selecting && !powerArmed) 1f else .4f }
        powerGauge?.visibility=if(showPowerGauge) View.VISIBLE else View.GONE
        powerGauge?.isEnabled=editable && !selecting && !powerArmed && (arcade || config.controlMode==BilliardControlMode.GAUGE)
        powerGauge?.alpha=if(editable && !selecting) 1f else .4f
        strokeView?.visibility=if(powerArmed && !arcade && config.controlMode==BilliardControlMode.GESTURE) View.VISIBLE else View.GONE
        precisionGauge?.visibility=if(powerArmed && arcade) View.VISIBLE else View.GONE
        precisionGauge?.isEnabled=editable && !selecting && powerArmed
        fineAimRow?.visibility=if(powerArmed && arcade) View.GONE else View.VISIBLE
        precisionControls?.visibility=if(powerArmed && arcade) View.GONE else View.VISIBLE
        cancelShotButton?.visibility=if(powerArmed) View.VISIBLE else View.GONE
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        val sideWidth=min(300,resources.configuration.screenWidthDp*2/5)
        scene?.hudRight=dp(if(landscape) sideWidth+(if(showPowerGauge) 120 else 24) else if(showPowerGauge) 112 else 14).toFloat()
        val canConfirm=run?.stage?.let { it!=JourneyStage.PLAYING }==true || editable && !cameraRecovery && (draft==null || draft.step==BilliardCallStep.RAILS && draft.rails.isNotEmpty())
        shootButton?.isEnabled=canConfirm; shootButton?.alpha=if(canConfirm) 1f else .4f
        shootButton?.show(when {
            run?.stage?.let { it!=JourneyStage.PLAYING }==true -> R.string.billiard_result_done
            run?.mode==BilliardActivityMode.TIMED && !clockArmed -> R.string.billiard_clock_start
            draft?.step==BilliardCallStep.BALL -> R.string.billiard_select_ball_short
            draft?.step==BilliardCallStep.POCKET -> R.string.billiard_select_pocket_short
            draft?.step==BilliardCallStep.RAILS || placing -> R.string.billiard_confirm_placement
            incompleteCall(s) -> R.string.billiard_select_call_short
            powerArmed && !arcade && config.controlMode==BilliardControlMode.GESTURE -> R.string.billiard_cancel_shot
            !powerArmed && (arcade || config.controlMode!=BilliardControlMode.GAUGE) -> R.string.billiard_ready_to_shoot
            else -> R.string.billiard_shoot
        })
        callButton?.visibility=if(s.needsCall || selecting) View.VISIBLE else View.GONE; callButton?.isEnabled=editable && !powerArmed
        callButton?.show(when {
            selecting -> R.string.billiard_cancel
            s.nominated>=0 -> R.string.billiard_change_call
            s.discipline.family==TableFamily.SNOOKER -> R.string.billiard_choose_colour
            else -> R.string.billiard_nominate_short
        })
        callBackButton?.visibility=if(selecting && draft?.step!=BilliardCallStep.BALL) View.VISIBLE else View.GONE
        callBackButton?.show(if(draft?.step==BilliardCallStep.RAILS) {
            if(draft.rails.isNotEmpty()) R.string.billiard_undo_rail else R.string.billiard_change_pocket
        } else R.string.billiard_change_ball)
        pushButton?.visibility=if(s.canPushOut) View.VISIBLE else View.GONE; pushButton?.isEnabled=editable && !selecting && !powerArmed
        safetyButton?.visibility=if(s.canSafety) View.VISIBLE else View.GONE; safetyButton?.isEnabled=editable && !selecting && !powerArmed
        for((v,selected) in listOf(pushButton to s.pushOut,safetyButton to s.safety)) v?.let {
            // A new background drawable at every frame would redraw the button for nothing.
            if(it.tag==selected) return@let
            it.tag=selected
            if(selected) accent(it) else { it.background=plate(); it.setTextColor(0xFFF1EBDD.toInt()) }
        }
        decisions?.visibility=if(s.match.decision!=ShotDecision.NONE && !s.replaying && !(s.mode==PlayMode.COMPUTER && s.match.player==1)) View.VISIBLE else View.GONE
        decisionAlternative?.show(when(s.match.decision) {
            ShotDecision.RERACK -> R.string.billiard_rerack_choice
            ShotDecision.ENGLISH_RESET -> R.string.billiard_reset_spots
            else -> R.string.billiard_pass_back
        })
        decisionRebreak?.visibility=if(s.match.decision==ShotDecision.RERACK && s.match.foul==Foul.ILLEGAL_BREAK &&
            s.discipline in listOf(Discipline.EIGHT,Discipline.HEYBALL)) View.VISIBLE else View.GONE
        val targets=s.legalTargets().map { ballName(it.id) }
        targetLabel?.show(when {
            draft!=null && draft.ball>=0 -> getString(R.string.billiard_call_selected_ball,ballName(draft.ball))
            s.discipline==Discipline.ENGLISH && (s.match.englishHazards>=10 || s.match.englishCannons>=70) -> getString(R.string.billiard_english_sequence,s.match.englishHazards,s.match.englishCannons)
            s.match.fouls(s.match.player)==2 -> getString(R.string.billiard_two_fouls)
            s.match.freeShots>0 -> getString(R.string.billiard_free_shot)
            s.pushOut -> getString(R.string.billiard_push_hint)
            s.safety -> getString(R.string.billiard_safety_hint)
            s.mode==PlayMode.PRACTICE -> getString(R.string.billiard_practice_score,s.match.shots)
            else -> getString(R.string.billiard_target,if(targets.size>3) getString(R.string.billiard_targets_count,targets.size) else targets.joinToString(getString(R.string.billiard_list_separator)))
        })
        placementButton?.active=placing
        placementButton?.isEnabled=editable && !selecting && !powerArmed
        placementButton?.visibility=if(config.mode==PlayMode.PRACTICE && run?.solo!=true || s.match.ballInHand) View.VISIBLE else View.GONE
        // Only states (an announcement, a foul, the winner): the controls speak for themselves.
        showHint(when {
            s.match.winner>=0 -> getString(R.string.billiard_winner,s.match.winner+1)
            draft?.step==BilliardCallStep.RAILS && draft.rails.isNotEmpty() -> getString(R.string.billiard_bank_route,
                draft.rails.joinToString(getString(R.string.billiard_list_separator)) { getString(R.string.billiard_rail_number,it+1) })
            draft!=null || s.world.moving || s.replaying -> ""
            s.nominated>=0 && s.discipline.family==TableFamily.SNOOKER -> getString(R.string.billiard_called_color,ballName(s.nominated))
            s.nominated>=0 && s.discipline==Discipline.BANK -> getString(R.string.billiard_called_bank,s.nominated,s.calledPocket+1,
                s.bankRails.joinToString(getString(R.string.billiard_list_separator)) { getString(R.string.billiard_number,it+1) })
            s.nominated>=0 -> getString(R.string.billiard_called,s.nominated,s.calledPocket+1)
            s.match.foul!=Foul.NONE -> getString(s.match.foul.titleRes())
            config.discipline==Discipline.ARTISTIC -> getString(R.string.billiard_drill,s.drill+1,s.drill%4)
            else -> ""
        })
        refreshJourneyStatus()
    }
    private fun schedulePrediction(liveEdit: Boolean=false) {
        // Only a different table/context invalidates an in-flight result. While aiming,
        // keep the last completed trace visible and coalesce edits into the next sample.
        if(!liveEdit) { generation++; scene?.trace=null; surface?.requestRender() }
        predictionDirty=true
        scheduleArcadePreparation()
        queuePrediction()
    }
    private fun canPredict(): Boolean {
        val s=session ?: return false
        return resumed && scene!=null && !worker.isShutdown && modalCount==0 && config.assistance>0 &&
            callDraft==null && !powerArmed && s.ready && !thinking &&
            run?.stage?.let { it==JourneyStage.PLAYING }!=false &&
            !(config.mode==PlayMode.COMPUTER && s.match.player==1)
    }
    private fun queuePrediction() {
        handler.removeCallbacks(prediction)
        if(predictionBusy || !predictionDirty || !canPredict()) return
        // A fixed deadline, not a delay restarted by every MOVE: at most 12.5 samples/s.
        val wait=(predictionStartedAt+80L-android.os.SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(prediction,wait)
    }
    private fun invalidateArcadePreparation() {
        handler.removeCallbacks(arcadePreparation)
        arcadeRevision=arcadeWorker.invalidate(); arcadePlan=null; arcadeRequested=false
    }
    private fun canPrepareArcade(s: BilliardSession)=arcade && resumed && modalCount==0 && s.ready &&
        !thinking && !placing && callDraft==null && s.cuePlacementValid && !incompleteCall(s) &&
        !(s.mode==PlayMode.COMPUTER && s.match.player==1)
    private fun scheduleArcadePreparation() {
        invalidateArcadePreparation()
        val s=session ?: return
        if(canPrepareArcade(s)) handler.postDelayed(arcadePreparation,90)
    }
    private fun requestArcadePreparation() {
        val s=session ?: return
        if(arcadeRequested || !canPrepareArcade(s)) return
        val ticket=arcadeRevision; val intended=shot
        val request=BilliardArcadePlanner.Request(s.discipline,s.mode,s.clothSpeed,s.table.size,s.snapshot(),intended)
        arcadeRequested=true
        arcadeWorker.submit(ticket,request) { plan ->
            handler.post {
                if(arcadeRevision==ticket && session===s && canPrepareArcade(s) && shot==intended) arcadePlan=plan
            }
        }
    }
    private fun requestPrediction() {
        val s=session ?: return
        val renderer=scene ?: return
        if(predictionBusy || !predictionDirty || !canPredict()) return
        // The guide only needs positions: a headless copy skips the drawn rotation and resting balls.
        val ticket=generation; val world=s.world.copyHeadless(); val cue=s.cueId; val aimed=shot
        val assistance=config.assistance
        predictionDirty=false; predictionBusy=true; predictionStartedAt=android.os.SystemClock.uptimeMillis()
        worker.execute {
            val cancelled={ generation!=ticket || Thread.currentThread().isInterrupted }
            val trace=runCatching {
                if(cancelled()) null
                else if(assistance==1) BilliardPlanner.preview(world,cue,aimed,cancelled)
                else BilliardPlanner.trace(world,cue,aimed,12.0,cancelled=cancelled)
            }.getOrNull()
            // Always release the worker slot, including cancellation and failed calculations.
            handler.post {
                predictionBusy=false
                if(trace!=null && generation==ticket && session===s && scene===renderer && canPredict()) {
                    renderer.trace=trace; surface?.requestRender()
                }
                // If inputs changed during this calculation, sample their latest values now.
                // No queue of obsolete strokes, and no need to lift the finger to finish one.
                queuePrediction()
            }
        }
    }
    private fun requestComputer() {
        val s=session ?: return
        invalidateArcadePreparation()
        val target=s.legalTargets().firstOrNull()
        val firstAngle=target?.let { b -> s.cue?.let { atan2(b.p.y-it.p.y,b.p.x-it.p.x) } } ?: shot.angle
        computerAim=BilliardComputerAim(shot,shot.copy(angle=firstAngle,side=0.0,top=0.0,elevation=0.0))
        generation++; val ticket=generation; scene?.trace=null
        val difficulty=config.difficulty; val cue=config.cue
        val copy=BilliardSession(s.discipline,s.mode,s.clothSpeed,s.table.size).apply { restore(s.snapshot()) }
        worker.execute {
            // The player waits on this search: it leaves the slow background cores while it runs.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT)
            val choice=try {
                BilliardPlanner.choose(copy,difficulty,cue.mass,cue.endMass,onCandidate={ candidate ->
                    handler.post {
                        if(generation==ticket && session===s && resumed) computerAim?.consider(candidate)
                    }
                }) { generation!=ticket || Thread.currentThread().isInterrupted }
            } finally { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
            handler.post {
                if(generation!=ticket || session!==s || !resumed) return@post
                val fallback=s.legalTargets().firstOrNull()
                val selected=choice ?: BilliardPlanner.PlannedShot(Shot(fallback?.let { b -> s.cue?.let { atan2(b.p.y-it.p.y,b.p.x-it.p.x) } } ?: 0.0,1.0),fallback?.id ?: -1,0,rails=if(s.discipline==Discipline.BANK) listOf(0) else emptyList())
                // Chosen exactly, struck like a player of its level.
                val stroke=BilliardSkill.of(difficulty).stroke(selected.shot.copy(cueMass=cue.mass,endMass=cue.endMass))
                computerAim?.prepare(selected.copy(shot=stroke))
                startTick()
            }
        }
    }
    private fun advanceComputer(delta: Double) {
        val s=session ?: return
        val aim=computerAim ?: return
        if(!s.ready || s.match.player!=1) { computerAim=null; return }
        val selected=aim.selected
        if(selected!=null && !aim.placementApplied) {
            selected.position?.let { s.moveBall(s.cueId,it) }
            aim.placementApplied=true
        }
        val readyToStrike=aim.advance(delta)
        syncControls(aim.visualShot); updateControlText(aim.visualShot)
        if(!readyToStrike || selected==null) return
        // Only the settled, final choice enters the match. Pauses and dialogs gate this method.
        shot=selected.shot
        s.nominated=selected.ball; s.calledPocket=selected.pocket; s.bankRails=selected.rails
        val ball=s.cue?.copyDeep(); val rackBreak=s.match.breaking && s.world.balls.size>6
        computerAim=null
        if(s.shoot(shot)) {
            returnCamera=scene?.camera?.state()
            placing=false
            if(ball!=null) scene?.animateStrike(ball,s.lastShot ?: shot)
            watchShot(); audio.strike(shot.speed,rackBreak); save()
        }
        syncControls(); updateControlText()
    }
    private fun nominate() {
        invalidateArcadePreparation()
        cancelPowerShot()
        val s=session ?: return; if(!s.ready || thinking || s.mode==PlayMode.COMPUTER && s.match.player==1) return
        if(callDraft!=null) { finishCall(false); return }
        if(!s.needsCall || callBalls(s).isEmpty()) return
        val camera=scene?.camera?.state() ?: return
        callDraft=CallDraft(camera,placing)
        placing=false; invalidPlacement=false
        generation++; handler.removeCallbacks(prediction); scene?.trace=null
        scene?.camera?.top(resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE)
        awaitCamera(); refreshCameraControls()
        syncCallSelection(); accessibleCallChoice()
    }
    private fun callBalls(s: BilliardSession): List<Ball> =
        // A ten-ball combination may call a different ball from the first contact.
        if(s.match.snookerColor || s.discipline==Discipline.EIGHT) s.legalTargets()
        else s.world.balls.filter { it.id!=s.cueId && it.motion!=Motion.POCKETED }

    private fun selectCallBall(id: Int) {
        val s=session ?: return; val draft=callDraft ?: return
        if(!s.ready || draft.step!=BilliardCallStep.BALL || callBalls(s).none { it.id==id }) return
        draft.ball=id; draft.pocket=-1; draft.rails=emptyList()
        if(s.discipline.family==TableFamily.SNOOKER) finishCall(true)
        else { draft.step=BilliardCallStep.POCKET; syncCallSelection(); accessibleCallChoice() }
    }
    private fun selectCallPocket(id: Int) {
        val s=session ?: return; val draft=callDraft ?: return
        if(!s.ready || draft.step!=BilliardCallStep.POCKET || id !in s.table.pockets.indices) return
        draft.pocket=id; draft.rails=emptyList()
        if(s.discipline==Discipline.BANK) { draft.step=BilliardCallStep.RAILS; syncCallSelection(); accessibleCallChoice() }
        else finishCall(true)
    }
    private fun selectCallRail(id: Int) {
        val draft=callDraft ?: return
        if(session?.ready!=true || draft.step!=BilliardCallStep.RAILS || id !in 0..5 || draft.rails.size>=8) return
        draft.rails=draft.rails+id
        syncCallSelection(); accessibleCallChoice()
    }
    private fun previousCallStep() {
        val draft=callDraft ?: return
        if(draft.step==BilliardCallStep.RAILS) {
            if(draft.rails.isNotEmpty()) draft.rails=draft.rails.dropLast(1)
            else { draft.step=BilliardCallStep.POCKET; draft.pocket=-1 }
        } else { draft.step=BilliardCallStep.BALL; draft.ball=-1; draft.pocket=-1 }
        syncCallSelection()
    }
    private fun syncCallSelection() {
        val s=session ?: return
        val draft=callDraft
        pocketLabels?.apply {
            step=draft?.step ?: BilliardCallStep.NONE
            candidates=if(draft!=null) callBalls(s).map { it.id }.toSet() else emptySet()
            draftBall=draft?.ball ?: -1; draftPocket=draft?.pocket ?: -1; draftRails=draft?.rails ?: emptyList()
            invalidate()
        }
        publish(); refreshStatus()
        hint?.let { it.announceForAccessibility(it.text) }
    }
    private fun finishCall(commit: Boolean) {
        val s=session ?: return; val draft=callDraft ?: return
        if(commit) {
            if(!s.ready || callBalls(s).none { it.id==draft.ball }) return
            if(s.discipline.family!=TableFamily.SNOOKER && draft.pocket !in s.table.pockets.indices) return
            if(s.discipline==Discipline.BANK && draft.rails.isEmpty()) return
            s.nominated=draft.ball
            s.calledPocket=if(s.discipline.family==TableFamily.SNOOKER) -1 else draft.pocket
            s.bankRails=draft.rails.toList()
        }
        callDraft=null; placing=draft.placing
        scene?.camera?.restore(draft.camera,resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE)
        awaitCamera()
        syncCallSelection(); surface?.wakeCamera(); schedulePrediction(); save()
    }
    private fun accessibleCallChoice() {
        // Spoken choices remain available to TalkBack; ordinary touch uses the table.
        if(!(getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled) return
        val s=session ?: return; val draft=callDraft ?: return
        val ids: List<Int>
        val names: List<String>
        val title: Int
        when(draft.step) {
            BilliardCallStep.BALL -> { ids=callBalls(s).map { it.id }; names=ids.map(::ballName); title=R.string.billiard_choose_ball }
            BilliardCallStep.POCKET -> { ids=s.table.pockets.map { it.id }; names=ids.map { getString(R.string.billiard_pocket,it+1) }; title=R.string.billiard_choose_pocket }
            BilliardCallStep.RAILS -> {
                ids=(0..5).toList()+if(draft.rails.isNotEmpty()) listOf(-1) else emptyList()
                names=ids.map { if(it<0) getString(R.string.billiard_confirm_bank) else getString(R.string.billiard_rail_number,it+1) }
                title=R.string.billiard_choose_rails
            }
            else -> return
        }
        val dialog=AlertDialog.Builder(this).setTitle(title).setItems(names.toTypedArray()) { _,i ->
            when(draft.step) {
                BilliardCallStep.BALL -> selectCallBall(ids[i])
                BilliardCallStep.POCKET -> selectCallPocket(ids[i])
                BilliardCallStep.RAILS -> if(ids[i]<0) finishCall(true) else selectCallRail(ids[i])
                else -> Unit
            }
        }.setNegativeButton(R.string.billiard_cancel) { _,_ -> finishCall(false) }.create()
        dialog.setOnCancelListener { finishCall(false) }
        showDialog(dialog)
    }
    private fun chooseAssistance() {
        choose(R.string.billiard_assistance,listOf(R.string.billiard_aid_none,R.string.billiard_aid_contact,R.string.billiard_aid_full).map { getString(it) },config.assistance) {
            config=config.copy(assistance=it)
            getSharedPreferences("billiards",MODE_PRIVATE).edit().putInt("aid",it).putInt("guideVersion",1).apply()
            schedulePrediction(); surface?.requestRender(); save()
        }
    }
    private fun decideShot(playHere: Boolean, originalBreaker: Boolean = false) {
        val s=session ?: return
        if(s.decide(playHere,originalBreaker)) { placing=s.match.ballInHand; updateInteraction(); publish(); schedulePrediction(); save(); startTick() }
    }
    private fun chooseCue() {
        val s=session ?: return; val balls=s.world.balls.filter { it.motion!=Motion.POCKETED }
        showDialog(AlertDialog.Builder(this).setTitle(R.string.billiard_choose_cue_ball)
            .setItems(balls.map { ballName(it.id) }.toTypedArray()) { _,i -> if(s.chooseCue(balls[i].id)) { publish(); schedulePrediction() } }.create())
    }
    private fun ballName(id: Int): String {
        if(config.discipline==Discipline.ENGLISH || config.discipline.family==TableFamily.CAROM)
            return getString(when(id) { 0 -> R.string.billiard_white; 1 -> R.string.billiard_plain_yellow; else -> R.string.billiard_plain_red })
        if(config.discipline.family==TableFamily.SNOOKER) {
            val name=when(id) { 0->R.string.billiard_white; 16->R.string.billiard_yellow; 17->R.string.billiard_green;
                18->R.string.billiard_brown; 19->R.string.billiard_blue; 20->R.string.billiard_pink; 21->R.string.billiard_black; else->0 }
            if(name!=0) return getString(name)
            if(config.discipline!=Discipline.ENGLISH) return getString(R.string.billiard_red_number,id)
        }
        return getString(R.string.billiard_ball,id)
    }
    private fun settingsMenu() {
        val s=session ?: return
        val options=mutableListOf<Pair<Int,()->Unit>>()
        if(s.mode==PlayMode.COMPUTER && run?.competitive!=true) options+=R.string.billiard_difficulty to { chooseDifficulty() }
        if(!thinking && run?.competitive!=true) options+=R.string.billiard_assistance to { chooseAssistance() }
        if(run?.competitive!=true) options+=R.string.billiard_game_style to { chooseGameStyle() }
        if(!arcade) options+=R.string.billiard_input_mode to { chooseControlMode() }
        options+=R.string.billiard_look to { chooseLook() }
        options+=R.string.billiard_rotation_stripes to { chooseRotationStripes() }
        options+=(if(slow) R.string.billiard_normal_motion else R.string.billiard_slow_motion) to { slow=!slow }
        options+=(if(audio.enabled) R.string.billiard_mute else R.string.billiard_unmute) to { audio.enabled=!audio.enabled; config=config.copy(sound=audio.enabled); save() }
        showDialog(AlertDialog.Builder(this).setTitle(R.string.billiard_menu_settings).setItems(options.map {
            when(it.first) {
                R.string.billiard_difficulty -> getString(R.string.billiard_selection,getString(it.first),getString(difficultyLevels[config.difficulty]))
                R.string.billiard_rotation_stripes -> getString(R.string.billiard_selection,getString(it.first),
                    getString(if(config.rotationStripes) R.string.billiard_rotation_stripes_on else R.string.billiard_rotation_stripes_off))
                R.string.billiard_input_mode -> getString(R.string.billiard_selection,getString(it.first),getString(config.controlMode.title))
                R.string.billiard_game_style -> getString(R.string.billiard_selection,getString(it.first),getString(config.style.title))
                R.string.billiard_look -> getString(R.string.billiard_selection,getString(it.first),getString(config.look.title))
                else -> getString(it.first)
            }
        }.toTypedArray()) { _,i -> options[i].second() }.create())
    }
    private fun gameMenu() {
        val s=session ?: return
        val options=mutableListOf<Pair<Int,()->Unit>>()
        val canReview=!s.world.moving && !s.replaying && !thinking && callDraft==null && s.before!=null && s.lastShot!=null
        if(canReview) {
            if(s.mode==PlayMode.PRACTICE && run?.solo!=true) options+=R.string.billiard_undo to { undoPracticeShot() }
            options+=R.string.billiard_replay to { if(s.replay()) { returnCamera=scene?.camera?.state(); invalidateArcadePreparation(); generation++; scene?.trace=null; watchShot(s.lastShot?.angle ?: shot.angle); publish() } }
        }
        options+=R.string.billiard_zoom_reset to { showCamera(cameraViewState()?.mode ?: BilliardCameraMode.TOP,reset=true) }
        if(!s.world.moving && !thinking && callDraft==null) {
            // Placing and calling already have their own buttons on the table.
            if(config.mode==PlayMode.PRACTICE && run?.solo!=true || config.discipline==Discipline.PYRAMID_FREE) options+=R.string.billiard_choose_cue_ball to { chooseCue() }
            if(config.discipline==Discipline.ARTISTIC && run?.solo!=true) options+=R.string.billiard_next_drill to { s.setupDrill(s.drill+1); publish(); schedulePrediction() }
        }
        options+=(if(run!=null) R.string.billiard_retry else R.string.billiard_reset) to ::restartTable
        if(run!=null) options+=R.string.billiard_objective to { showJourneyInfo() }
        options+=R.string.billiard_rules to { showRules(s.discipline) }
        options+=R.string.billiard_menu_settings to { settingsMenu() }
        options+=R.string.billiard_change_mode to { save(); showLounge() }
        showDialog(AlertDialog.Builder(this).setTitle(config.discipline.titleRes())
            .setItems(options.map { getString(it.first) }.toTypedArray()) { _,i -> options[i].second() }.create())
    }
    private fun showRules(d: Discipline) {
        val table=session?.takeIf { it.discipline==d }?.table ?: BilliardTable(d.family,config.cloth,d,
            if(d.family==config.discipline.family) config.tableSize else rememberedTableSize(d.family))
        val sizeInfo=getString(R.string.billiard_selection,tableSizeName(table.size),tableSizeCategory(table.size,d))
        val adaptation=if(adaptedTable(table.size,d)) "\n"+getString(R.string.billiard_size_adapted_help) else ""
        val cadre=if(d in listOf(Discipline.CADRE_47_1,Discipline.CADRE_47_2,Discipline.CADRE_71_2))
            "\n"+getString(R.string.billiard_size_cadre,table.cadreInset*100) else ""
        val content=text(d.rulesRes()).apply {
            text=sizeInfo+"\n"+tableSpecs(table)+adaptation+cadre+"\n\n"+getString(d.rulesRes())+"\n\n"+getString(R.string.billiard_controls)+"\n\n"+getString(R.string.billiard_arcade_help)+"\n\n"+getString(R.string.billiard_guide_help)+"\n\n"+getString(R.string.billiard_club_rules)+"\n\n"+getString(R.string.billiard_rule_sources)
            autoLinkMask=android.text.util.Linkify.WEB_URLS; setTextIsSelectable(true); setPadding(dp(20),dp(10),dp(20),dp(10))
        }
        showDialog(AlertDialog.Builder(this).setTitle(d.titleRes()).setView(ScrollView(this).apply { addView(content) }).setPositiveButton(R.string.billiard_close,null).create())
    }
    private fun tableSpecs(t: BilliardTable)=getString(R.string.billiard_table_specs,t.length,t.width,t.radius*2000)
    private fun showDialog(dialog: AlertDialog) {
        cancelPowerShot()
        invalidateArcadePreparation()
        modalCount++; decisionClock.pause(); audio.pause()
        dialog.setOnDismissListener {
            modalCount=(modalCount-1).coerceAtLeast(0); lastFrame=0
            if(modalCount==0 && resumed) { audio.resume(); schedulePrediction(liveEdit=true) }
        }
        dialog.followImmersiveMode(); dialog.show()
        // All billiards dialogs share the same glass treatment, including rules and results.
        dialog.window?.let { window ->
            window.setBackgroundDrawable(plate(0xBC0A2421.toInt(),24))
            window.setDimAmount(.18f)
            // Keep outside dismissal modal: the gesture must never reach the cue or camera.
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }
        listOf(androidx.appcompat.R.id.parentPanel,androidx.appcompat.R.id.topPanel,androidx.appcompat.R.id.contentPanel,
            androidx.appcompat.R.id.buttonPanel,androidx.appcompat.R.id.customPanel).forEach { id -> dialog.findViewById<View>(id)?.setBackgroundColor(Color.TRANSPARENT) }
        fun styleDialogText(view: View) {
            if(view is TextView) { view.setTextColor(0xFFF3EBDD.toInt()); view.setShadowLayer(dp(2).toFloat(),0f,dp(1).toFloat(),0xB0000000.toInt()) }
        }
        dialog.findViewById<TextView>(android.R.id.message)?.let(::styleDialogText)
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply { setTextColor(0xFFFFDEA2.toInt()) }
        dialog.listView?.let { list ->
            list.setBackgroundColor(Color.TRANSPARENT); list.cacheColorHint=Color.TRANSPARENT
            val original=list.adapter
            if(original!=null) list.adapter=object: BaseAdapter() {
                override fun getCount()=original.count
                override fun getItem(position: Int)=original.getItem(position)
                override fun getItemId(position: Int)=original.getItemId(position)
                override fun getView(position: Int,convertView: View?,parent: android.view.ViewGroup): View =
                    original.getView(position,convertView,parent).also { view ->
                        styleDialogText(view); view.setBackgroundColor(Color.TRANSPARENT)
                        if(view is TextView) { view.minHeight=dp(52); view.setPadding(dp(20),dp(12),dp(20),dp(12)) }
                    }
            }
        }
        for(which in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL)) {
            dialog.getButton(which)?.apply { setTextColor(0xFFFFDEA2.toInt()); background=plate(0x55445532,14) }
        }
    }
    private fun save() {
        val s=session ?: return
        rememberCameraView()
        runCatching { store.save(config,s,shot,run,cameraViewState(),cameraViews) }.onFailure { Toast.makeText(this,R.string.billiard_save_error,Toast.LENGTH_SHORT).show() }
    }
    private fun startTick() { if(resumed && surface!=null && !ticking) { ticking=true; Choreographer.getInstance().postFrameCallback(tick) } }
    override fun onResume() { super.onResume(); resumed=true; audio.resume(); menuPreview?.resume(); surface?.onResume(); surface?.wakeCamera(); lastFrame=0; decisionClock.pause(); clockArmed=false; awaitCamera(); startTick(); schedulePrediction() }
    override fun onPause() {
        decisionClock.pause(); clockArmed=false
        cancelPowerShot()
        invalidateArcadePreparation()
        resumed=false; generation++; computerAim=null; handler.removeCallbacks(prediction); handler.removeCallbacks(idleTick)
        Choreographer.getInstance().removeFrameCallback(tick); ticking=false; save(); store.flush(); menuPreview?.pause(); surface?.onPause(); audio.pause(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) { save(); outState.putBoolean("table",session!=null); super.onSaveInstanceState(outState) }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cancelPowerShot()
        val s=session
        if(s!=null) {
            val old=scene?.camera?.state()
            val shotReturn=returnCamera
            val wasPlacing=placing
            val draft=callDraft
            openTable(config,s,shot,old)
            returnCamera=shotReturn
            placing=wasPlacing; updateInteraction()
            callDraft=draft; syncCallSelection()
            surface?.wakeCamera()
        } else if(setupVisible) showMenu() else if(lounge!=null && menuPreview!=null) lounge?.root?.requestLayout() else showLounge()
    }
    override fun onDestroy() { menuPreview?.close(); generation++; arcadeWorker.close(); worker.shutdownNow(); store.close(); handler.removeCallbacksAndMessages(null); audio.close(); super.onDestroy() }
}
