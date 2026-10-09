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

/** An isolated viewer: it has no reference to the active game or its scores. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class GolfReplayView(context: Context, val replay: GolfReplay, private val hole: ClassicHole,
                              onClose: () -> Unit, onSave: () -> Unit) : FrameLayout(context), Choreographer.FrameCallback {
    private val ui=GolfUi(context)
    private val scene=ClassicSurface(context,hole)
    private var ready=false
    private var active=false
    private var lastFrame=0L
    private var position=0f
    private var playing=true
    private var speed=1f
    private var mode=ShotCameraMode.AUTO
    private var yaw=replay.aim+.8f
    private var pitch=.4f
    private var distance=if(replay.club==GolfClub.PUTTER)8f else 30f
    private var freeEye=replay.samples.first().ball
    private var currentPose: GolfCameraPose?=null
    private val play=ui.secondary(context.getString(R.string.golf_replay_pause)) { togglePlayback() }
    private val timeLabel=ui.text("",13f)
    private val seek=SeekBar(context).apply { max=10000;contentDescription=context.getString(R.string.golf_replay_timeline) }
    private val hint=ui.pill().apply { textSize=12f }
    private val manual=ui.row()
    private var dragCount=0
    private var dragX=0f
    private var dragY=0f
    private var dragSpan=0f
    private var updatingFollow=false

    init {
        setBackgroundColor(ui.palette.surface)
        addView(scene,LayoutParams(-1,-1))
        val gestures=View(context).apply {
            contentDescription=context.getString(R.string.golf_replay_gestures)
            setOnTouchListener { _,event -> touch(event) }
        }
        addView(gestures,LayoutParams(-1,-1))
        val loading=ProgressBar(context)
        addView(loading,LayoutParams(ui.dp(48),ui.dp(48),Gravity.CENTER))
        scene.onReady={ready=true;loading.visibility=GONE}
        val top=ui.row().apply { setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6));background=ui.shape(ui.palette.surface) }
        top.addView(button(R.string.golf_back,onClose))
        val cameras=Spinner(context)
        cameras.adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.golf_replay_cameras))
        cameras.contentDescription=context.getString(R.string.golf_replay_camera)
        cameras.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?) {}
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,index:Int,id:Long) { selectCamera(ShotCameraMode.entries[index]) }
        }
        top.addView(cameras,LinearLayout.LayoutParams(0,ui.dp(48),1f))
        top.addView(button(R.string.golf_replay_save,onSave))
        addView(top,LayoutParams(-1,-2,Gravity.TOP))
        val dock=ui.column().apply { setPadding(ui.dp(8),ui.dp(4),ui.dp(8),ui.dp(6));background=ui.shape(ui.palette.surface) }
        hint.text=context.getString(R.string.golf_replay_gestures)
        dock.addView(hint,LinearLayout.LayoutParams(-1,-2))
        manual.addView(button(R.string.golf_replay_recenter) { resetCamera() })
        val follow=Switch(context).apply {
            text=context.getString(R.string.golf_replay_follow);setTextColor(ui.palette.text);isChecked=true
            setOnCheckedChangeListener { _,checked -> if(!updatingFollow)cameras.setSelection(if(checked)ShotCameraMode.ORBIT.ordinal else ShotCameraMode.FREE.ordinal) }
        }
        manual.addView(follow)
        dock.addView(manual)
        dock.addView(timeLabel)
        dock.addView(seek,LinearLayout.LayoutParams(-1,ui.dp(36)))
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) { if(fromUser){position=replay.duration*value/10000f;updateLabels()} }
            override fun onStartTrackingTouch(bar:SeekBar?) { playing=false;updateLabels() }
            override fun onStopTrackingTouch(bar:SeekBar?) {}
        })
        val controls=ui.row()
        controls.addView(button(R.string.golf_replay_back) { position=(position-2f).coerceAtLeast(0f);updateLabels() })
        controls.addView(play.apply { minWidth=0;setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6)) },LinearLayout.LayoutParams(0,ui.dp(48),1f))
        controls.addView(button(R.string.golf_replay_forward) { position=(position+2f).coerceAtMost(replay.duration);updateLabels() })
        val speeds=Spinner(context)
        speeds.adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.golf_replay_speeds))
        speeds.contentDescription=context.getString(R.string.golf_replay_speed)
        speeds.setSelection(6)
        speeds.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?) {}
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,index:Int,id:Long) {
                speed=floatArrayOf(-4f,-2f,-1f,-.5f,.25f,.5f,1f,2f,4f)[index]
            }
        }
        controls.addView(speeds,LinearLayout.LayoutParams(ui.dp(100),ui.dp(48)))
        dock.addView(controls)
        addView(dock,LayoutParams(-1,-2,Gravity.BOTTOM))
        selectCamera(mode);updateLabels()
    }

    private fun button(res:Int,action:()->Unit)=ui.secondary(context.getString(res),action).apply {
        minWidth=0;textSize=13f;setPadding(ui.dp(10),ui.dp(6),ui.dp(10),ui.dp(6));minHeight=ui.dp(48)
    }

    private fun togglePlayback() {
        if(!playing) {
            if(speed>0f && position>=replay.duration)position=0f
            if(speed<0f && position<=0f)position=replay.duration
        }
        playing=!playing;updateLabels()
    }

    private fun selectCamera(next:ShotCameraMode) {
        if(next==ShotCameraMode.FREE && mode!=next) {
            val pose=currentPose ?: ShotCamera.pose(hole,replay.sample(position).ball,replay.samples.first().ball,replay.aim,replay.club,position,replay.stroke,ShotCameraMode.SIDE)
            freeEye=pose.eye
            val dx=pose.target.x-freeEye.x;val dy=pose.target.y-freeEye.y;val dz=pose.target.z-freeEye.z
            yaw=atan2(dx,dz);pitch=atan2(-dy,hypot(dx,dz))
        }
        mode=next
        val custom=mode==ShotCameraMode.ORBIT || mode==ShotCameraMode.FREE
        hint.visibility=if(custom && resources.configuration.orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE)VISIBLE else GONE
        manual.visibility=if(custom)VISIBLE else GONE
        val follow=manual.getChildAt(1) as Switch
        val shouldFollow=mode!=ShotCameraMode.FREE
        updatingFollow=true
        if(follow.isChecked!=shouldFollow)follow.isChecked=shouldFollow
        updatingFollow=false
    }

    private fun resetCamera() {
        yaw=replay.aim+.8f;pitch=.4f;distance=if(replay.club==GolfClub.PUTTER)8f else 30f
        val ball=replay.sample(position).ball
        freeEye=GolfPoint(ball.x-sin(yaw)*distance*cos(pitch),ball.y+distance*sin(pitch),ball.z-cos(yaw)*distance*cos(pitch))
    }

    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val custom=mode==ShotCameraMode.ORBIT || mode==ShotCameraMode.FREE
        hint.visibility=if(custom && newConfig.orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE)VISIBLE else GONE
    }

    private fun touch(event:MotionEvent):Boolean {
        if(mode!=ShotCameraMode.ORBIT && mode!=ShotCameraMode.FREE)return true
        val count=event.pointerCount
        val x=if(count>1)(event.getX(0)+event.getX(1))*.5f else event.x
        val y=if(count>1)(event.getY(0)+event.getY(1))*.5f else event.y
        val span=if(count>1)hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1)) else 0f
        if(event.actionMasked==MotionEvent.ACTION_MOVE && dragCount==count) {
            val dx=x-dragX;val dy=y-dragY
            if(count==1) {
                yaw-=dx/width.coerceAtLeast(1)*5f
                pitch=(pitch+dy/height.coerceAtLeast(1)*3f).coerceIn(-1.35f,1.45f)
            } else if(dragSpan>0f && span>0f) {
                if(mode==ShotCameraMode.ORBIT)distance=(distance*dragSpan/span).coerceIn(.8f,600f)
                else {
                    val scale=distance/height.coerceAtLeast(1)
                    val forward=(span-dragSpan)*scale*2f
                    freeEye=GolfPoint(freeEye.x-cos(yaw)*dx*scale+sin(yaw)*cos(pitch)*forward,
                        freeEye.y+dy*scale-sin(pitch)*forward,
                        freeEye.z+sin(yaw)*dx*scale+cos(yaw)*cos(pitch)*forward)
                }
            }
        }
        dragCount=if(event.actionMasked==MotionEvent.ACTION_POINTER_UP || event.actionMasked==MotionEvent.ACTION_UP)0 else count
        dragX=x;dragY=y;dragSpan=span
        return true
    }

    fun resume() { if(active)return;active=true;lastFrame=0L;scene.onResume();Choreographer.getInstance().postFrameCallback(this) }
    fun pause() { active=false;lastFrame=0L;scene.onPause();Choreographer.getInstance().removeFrameCallback(this) }
    fun release() { scene.release();pause() }

    override fun doFrame(frameTimeNanos:Long) {
        if(!active)return
        val dt=if(lastFrame==0L)0f else ((frameTimeNanos-lastFrame)*1e-9f).coerceIn(0f,.05f)
        lastFrame=frameTimeNanos
        if(playing && ready) {
            position=(position+dt*speed).coerceIn(0f,replay.duration)
            if((speed>0f && position>=replay.duration)||(speed<0f && position<=0f))playing=false
        }
        val sample=replay.sample(position)
        val pose=when(mode) {
            ShotCameraMode.ORBIT -> {
                val b=sample.ball
                val x=b.x-sin(yaw)*distance*cos(pitch);val z=b.z-cos(yaw)*distance*cos(pitch)
                GolfCameraPose(GolfPoint(x,max(b.y+distance*sin(pitch),hole.heightAt(x,z)+.3f),z),b,mode.ordinal)
            }
            ShotCameraMode.FREE -> {
                freeEye=freeEye.copy(x=freeEye.x.coerceIn(-1200f,1200f),z=freeEye.z.coerceIn(-1200f,hole.length+1200f),y=freeEye.y.coerceIn(-200f,1200f))
                freeEye=freeEye.copy(y=max(freeEye.y,hole.heightAt(freeEye.x,freeEye.z)+.3f))
                GolfCameraPose(freeEye,GolfPoint(freeEye.x+sin(yaw)*cos(pitch),freeEye.y-sin(pitch),freeEye.z+cos(yaw)*cos(pitch)),mode.ordinal)
            }
            else -> ShotCamera.pose(hole,sample.ball,replay.samples.first().ball,replay.aim,replay.club,position,replay.stroke,mode)
        }
        currentPose=pose
        val trail=List(45) { i -> replay.sample((position-(44-i)*.025f).coerceAtLeast(0f)).ball }
        scene.submit(ClassicFrame(sample.ball,replay.aim,true,ShotPreview.NONE,club=replay.club,grid=false,
            clock=sample.clock,shotCamera=pose,replayTime=position,replayTrail=trail),false,frameTimeNanos)
        updateLabels()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun updateLabels() {
        val label=context.getString(R.string.golf_replay_time,position,replay.duration)
        if(timeLabel.text.toString()!=label)timeLabel.text=label
        if(!seek.isPressed)seek.progress=(position/replay.duration*10000).roundToInt()
        val action=context.getString(if(playing)R.string.golf_replay_pause else R.string.golf_replay_play)
        if(play.text.toString()!=action)play.text=action
    }
}
