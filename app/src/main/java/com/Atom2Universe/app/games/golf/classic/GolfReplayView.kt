package com.Atom2Universe.app.games.golf.classic

import android.annotation.SuppressLint
import android.content.Context
import android.view.*
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.golf.GolfUi
import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import kotlin.math.*

/** Playback controls over the live surface; library shots can also own a standalone surface. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class GolfReplayView(context: Context, val replay: GolfReplay, private val hole: ClassicHole,
                              onClose: () -> Unit, onSave: () -> Unit,
                              private val camera: LiveShotCamera,
                              sharedScene: ClassicSurface? = null,
                              private val liveFrame: ClassicFrame? = null) : FrameLayout(context), Choreographer.FrameCallback {
    val usesLiveScene=sharedScene!=null
    private val ui=GolfUi(context)
    private val scene=sharedScene?:ClassicSurface(context,hole)
    private var ready=usesLiveScene
    private var active=false
    private var lastFrame=0L
    private var position=0f
    private var playing=true
    private var speed=1f
    private val cameraModes=listOf(ShotCameraMode.ORBIT,ShotCameraMode.SIDE,ShotCameraMode.AERIAL,
        ShotCameraMode.ARRIVAL,ShotCameraMode.CHASE,ShotCameraMode.AUTO)
    private val speeds=floatArrayOf(-4f,-2f,-1f,-.5f,.25f,.5f,1f,2f,4f)
    private val cameraButton=icon(GolfIcon.Kind.CAMERA,R.string.golf_live_camera) { cycleCamera() }
    private val play=icon(GolfIcon.Kind.PAUSE,R.string.golf_replay_pause) { togglePlayback() }
    private val speedButton=ui.pill().apply {
        textSize=12f;minWidth=0;setPadding(ui.dp(5),0,ui.dp(5),0)
        isFocusable=true;setOnClickListener{chooseSpeed()}
        contentDescription=context.getString(R.string.golf_replay_speed)
    }
    private val timeLabel=ui.text("",12f,android.graphics.Color.WHITE).apply{gravity=Gravity.CENTER}
    private val seek=SeekBar(context).apply { max=10000;contentDescription=context.getString(R.string.golf_replay_timeline) }
    private val timeline=ui.column().apply {
        background=ui.shape(GolfUi.SCENE_PLATE,18f,null)
        setPadding(ui.dp(4),ui.dp(3),ui.dp(4),0)
    }
    private val miniMap=ClassicMap(context,hole)
    private var cameraToast:Toast?=null
    private var dragCount=0
    private var dragX=0f
    private var dragY=0f
    private var dragSpan=0f

    init {
        if(!usesLiveScene) {
            setBackgroundColor(ui.palette.surface)
            addView(scene,LayoutParams(-1,-1))
            val loading=ProgressBar(context)
            addView(loading,LayoutParams(ui.dp(48),ui.dp(48),Gravity.CENTER))
            scene.onReady={ready=true;loading.visibility=GONE}
        }
        addView(View(context).apply {
            contentDescription=context.getString(R.string.golf_replay_gestures)
            setOnTouchListener { _,event -> touch(event) }
        },LayoutParams(-1,-1))

        // Match the live HUD anchors exactly: back, status, map, camera.
        val top=ui.row().apply{setPadding(ui.dp(10),ui.dp(10),ui.dp(10),0)}
        top.addView(icon(GolfIcon.Kind.BACK,R.string.golf_back,onClose),LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        top.addView(ui.pill().apply {
            text=context.getString(R.string.golf_replay_hud,replay.hole,replay.stroke)
            textSize=14f;setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8))
        },LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(ui.dp(6),0,ui.dp(6),0)})
        top.addView(icon(GolfIcon.Kind.SAVE,R.string.golf_replay_save,onSave),LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        addView(top,LayoutParams(-1,-2,Gravity.TOP))
        miniMap.contentDescription=context.getString(R.string.classic_overview)
        addView(miniMap,LayoutParams(ui.dp(88),ui.dp(138),Gravity.END or Gravity.TOP).apply{topMargin=ui.dp(112);rightMargin=ui.dp(12)})

        timeline.addView(timeLabel,LinearLayout.LayoutParams(-1,ui.dp(18)))
        val scrub=ui.row()
        scrub.addView(smallButton(R.string.golf_replay_back){position=(position-2f).coerceAtLeast(0f);updateLabels()},
            LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        scrub.addView(seek,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        scrub.addView(smallButton(R.string.golf_replay_forward){position=(position+2f).coerceAtMost(replay.duration);updateLabels()},
            LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        timeline.addView(scrub)
        addView(timeline,LayoutParams(-1,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=ui.dp(76)})
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
                if(fromUser){position=replay.duration*value/10000f;updateLabels()}
            }
            override fun onStartTrackingTouch(bar:SeekBar?) { playing=false;updateLabels() }
            override fun onStopTrackingTouch(bar:SeekBar?) {}
        })
        bottom(icon(GolfIcon.Kind.REPLAY,R.string.golf_replay_restart){
            position=0f;if(speed<0f)speed=abs(speed);playing=true;updateLabels()
        },-56)
        bottom(cameraButton,0)
        bottom(play,56)
        bottom(speedButton,112)
        cameraButton.setOnLongClickListener{cameraMenu();true}
        updateCameraLabel();updateLabels()
    }

    private fun icon(kind:GolfIcon.Kind,res:Int,action:()->Unit)=GolfIcon(context,kind,context.getString(res),action).apply{alpha=.72f}
    private fun smallButton(res:Int,action:()->Unit)=ui.text(context.getString(res),12f,android.graphics.Color.WHITE).apply {
        gravity=Gravity.CENTER;isClickable=true;isFocusable=true;setOnClickListener{action()}
    }
    private fun bottom(view:View,offset:Int) {
        view.translationX=ui.dp(offset).toFloat()
        addView(view,LayoutParams(ui.dp(48),ui.dp(48),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=ui.dp(16)})
    }

    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        timeline.layoutParams=timeline.layoutParams.apply{width=min(ui.dp(480),w-ui.dp(if(w>h)224 else 32)).coerceAtLeast(ui.dp(200))}
    }

    private fun togglePlayback() {
        if(!playing) {
            if(speed>0f && position>=replay.duration)position=0f
            if(speed<0f && position<=0f)position=replay.duration
        }
        playing=!playing;updateLabels()
    }

    private fun cycleCamera() {
        camera.select(cameraModes[(cameraModes.indexOf(camera.mode)+1)%cameraModes.size])
        updateCameraLabel()
        cameraToast?.cancel()
        cameraToast=Toast.makeText(context,cameraButton.contentDescription,Toast.LENGTH_SHORT).also{it.show()}
    }

    private fun updateCameraLabel() {
        val name=resources.getStringArray(R.array.golf_replay_cameras)[camera.mode.ordinal]
        cameraButton.contentDescription=context.getString(R.string.golf_live_camera_current,name)
        cameraButton.tooltipText=context.getString(R.string.golf_replay_camera_options)
    }

    private fun cameraMenu() {
        PopupMenu(context,cameraButton).apply {
            val names=resources.getStringArray(R.array.golf_replay_cameras)
            cameraModes.forEachIndexed{index,mode->menu.add(0,index,0,names[mode.ordinal])}
            menu.add(0,cameraModes.size,0,R.string.golf_replay_recenter)
            setOnMenuItemClickListener{
                if(it.itemId==cameraModes.size){camera.begin(replay.aim,replay.club);camera.select(ShotCameraMode.ORBIT)}
                else camera.select(cameraModes[it.itemId])
                updateCameraLabel();true
            }
            show()
        }
    }

    private fun chooseSpeed() {
        PopupMenu(context,speedButton).apply {
            resources.getStringArray(R.array.golf_replay_speeds).forEachIndexed{index,label->menu.add(0,index,0,label)}
            setOnMenuItemClickListener{speed=speeds[it.itemId];updateLabels();true}
            show()
        }
    }

    private fun touch(event:MotionEvent):Boolean {
        if(!camera.manual)return true
        val count=event.pointerCount
        val x=if(count>1)(event.getX(0)+event.getX(1))*.5f else event.x
        val y=if(count>1)(event.getY(0)+event.getY(1))*.5f else event.y
        val span=if(count>1)hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1)) else 0f
        if(event.actionMasked==MotionEvent.ACTION_MOVE && dragCount==count) {
            if(count==1)camera.rotate(x-dragX,y-dragY,width,height)
            else if(dragSpan>0f && span>0f)camera.zoom(dragSpan/span)
        }
        dragCount=if(event.actionMasked in intArrayOf(MotionEvent.ACTION_POINTER_UP,MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL))0 else count
        dragX=x;dragY=y;dragSpan=span
        return true
    }

    fun resume() {
        if(active)return
        active=true;lastFrame=0L
        if(!usesLiveScene)scene.onResume()
        Choreographer.getInstance().postFrameCallback(this)
    }
    fun pause() {
        active=false;lastFrame=0L
        if(!usesLiveScene)scene.onPause()
        Choreographer.getInstance().removeFrameCallback(this)
    }
    fun release() {
        cameraToast?.cancel()
        if(!usesLiveScene)scene.release()
        pause()
    }

    override fun doFrame(frameTimeNanos:Long) {
        if(!active)return
        val dt=if(lastFrame==0L)0f else ((frameTimeNanos-lastFrame)*1e-9f).coerceIn(0f,.05f)
        lastFrame=frameTimeNanos
        if(playing && ready) {
            position=(position+dt*speed).coerceIn(0f,replay.duration)
            if((speed>0f && position>=replay.duration)||(speed<0f && position<=0f))playing=false
        }
        val sample=replay.sample(position)
        val pose=camera.pose(hole,sample.ball,replay.samples.first().ball,replay.aim,replay.club,position,replay.stroke)
        val trail=List(45) { i -> replay.sample((position-(44-i)*.025f).coerceAtLeast(0f)).ball }
        val frame=(liveFrame?:ClassicFrame(sample.ball,replay.aim,true,ShotPreview.NONE,club=replay.club)).copy(
            ball=sample.ball,aimAngle=replay.aim,flying=true,preview=ShotPreview.NONE,
            overview=null,swingProgress=-1f,aiming=false,clock=sample.clock,shotCamera=pose,replayTime=position,replayTrail=trail)
        scene.submit(frame,false,frameTimeNanos)
        miniMap.ball=sample.ball
        updateLabels()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun updateLabels() {
        val label=context.getString(R.string.golf_replay_time,position,replay.duration)
        if(timeLabel.text.toString()!=label)timeLabel.text=label
        if(!seek.isPressed)seek.progress=(position/replay.duration*10000).roundToInt()
        play.kind=if(playing)GolfIcon.Kind.PAUSE else GolfIcon.Kind.PLAY
        play.contentDescription=context.getString(if(playing)R.string.golf_replay_pause else R.string.golf_replay_play)
        val speedLabel=resources.getStringArray(R.array.golf_replay_speeds)[speeds.indexOfFirst{it==speed}.coerceAtLeast(0)]
        if(speedButton.text.toString()!=speedLabel)speedButton.text=speedLabel
    }
}
