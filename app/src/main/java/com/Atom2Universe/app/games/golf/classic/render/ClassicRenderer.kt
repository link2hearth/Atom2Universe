package com.Atom2Universe.app.games.golf.classic.render

import android.opengl.GLES20 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfPoint
import com.Atom2Universe.app.games.golf.classic.core.GolfClub
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import com.Atom2Universe.app.games.golf.classic.core.ShotPreview
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

internal class ClassicRenderer(private val hole: ClassicHole) : GLSurfaceView.Renderer {
    var onReady: (() -> Unit)? = null
    @Volatile var frame = ClassicFrame(hole.tee, 0f, false, ShotPreview.NONE)
    private val landscape=ClassicLandscape(hole)
    private val terrain by lazy { landscape.terrain() }
    private val groundShader=GroundShader(hole.highlands,hole.snowy)
    private val grass=ClassicGrass(hole)
    private val lightShader=BoardLightShader()
    private var boardLights=FloatArray(0)
    private var lightsKey:List<Any>?=null
    private val scenery by lazy { landscape.scenery() }
    private val festive by lazy { ClassicFestiveDecor(hole) }
    private val blades by lazy { landscape.blades() }
    private val ball by lazy { MeshBuilder().apply { sphere(0f,0f,0f,1f,
        if(hole.snowy)C(1f,.36f,.06f) else C(1f,.99f,.92f),14,8) }.build() }
    private val shadow by lazy { MeshBuilder().apply { disc(0f,0f,0f,1f,1f,C(.22f,.39f,.18f)) }.build() }
    private val ring by lazy { MeshBuilder().apply { ring(0f,0f,0f,1f,.055f,C(1f,.92f,.55f),48) }.build() }
    private val bird by lazy { MeshBuilder().apply {
        tri(P(-1f,0f,0f),P(0f,0f,.2f),P(-.25f,.18f,.3f),C(.97f,.97f,.88f))
        tri(P(1f,0f,0f),P(0f,0f,.2f),P(.25f,.18f,.3f),C(.97f,.97f,.88f))
    }.build() }
    private val rabbit by lazy { GolfRabbit(hole.snowy) }
    private val sky by lazy { MeshBuilder().apply {
        quad(P(-1f,-1f,0f),P(1f,-1f,0f),P(1f,1f,0f),P(-1f,1f,0f),C(1f,1f,1f),false)
    }.build() }
    private val golfer = GolferRenderer()
    /** Pin 2.4 m tall; drawn larger from afar so that it stays a landmark on long holes. */
    private val flag by lazy { MeshBuilder().apply {
        // The flagstick stands on the bottom of the cup.
        cone(0f,-ClassicHole.CUP_DEPTH,0f,ClassicHole.PIN_RADIUS,2.4f+ClassicHole.CUP_DEPTH,C(.98f,.97f,.90f),6,ClassicHole.PIN_RADIUS)
        tri(P(0f,2.4f,0f),P(.95f,2.12f,0f),P(0f,1.82f,0f),C(1f,.34f,.22f),false)
        tri(P(0f,2.4f,0f),P(0f,1.82f,0f),P(.95f,2.12f,0f),C(.9f,.28f,.18f),false)
    }.build() }
    /** Landing pin of the guide; like the flag it grows with distance so a long carry stays readable. */
    private fun landingPin(colour:C) = MeshBuilder().apply {
        cone(0f,0f,0f,.03f,.45f,colour.shade(.8f),6,.03f)
        cone(0f,.45f,0f,.02f,.85f,colour,10,.34f)
    }.build()
    private val pin by lazy { landingPin(C(1f,.86f,.35f)) }
    private val hazardPin by lazy { landingPin(C(1f,.38f,.32f)) }
    /** Floating marker over the hole while the flagstick is out for putting. */
    private val cupMarker by lazy { MeshBuilder().apply {
        cone(0f,0f,0f,.014f,.5f,C(.98f,.97f,.92f),12,.21f)
        cone(0f,.5f,0f,.21f,.08f,C(1f,.34f,.22f),12,.21f)
    }.build() }
    /** Dead leaves in three autumn tones: flat kites, seen from both sides. */
    private val leaves by lazy { listOf(C(.74f,.44f,.16f),C(.62f,.5f,.17f),C(.55f,.27f,.12f)).map { colour ->
        MeshBuilder().apply {
            val a=P(0f,0f,-.09f); val b=P(.045f,0f,0f); val c=P(0f,0f,.09f); val d=P(-.045f,0f,0f)
            quad(a,b,c,d,colour); quad(d,c,b,a,colour)
        }.build()
    } }
    private val meshes get() = scenery+festive.scenery+festive.animatedMeshes+
        listOf(blades,ball,shadow,ring,bird,sky,flag,pin,hazardPin,cupMarker)+rabbit.meshes+leaves
    // A few leaves carried by the wind a couple of metres above the turf; each waits a while before the next one.
    private val leafX=FloatArray(LEAVES); private val leafY=FloatArray(LEAVES); private val leafZ=FloatArray(LEAVES)
    private val leafWait=FloatArray(LEAVES) { 1f+it*2.5f }
    private val leafAge=FloatArray(LEAVES); private val leafSeed=FloatArray(LEAVES) { it*1.9f+.4f }
    private val leafRandom=java.util.Random(11)
    private var program=0
    private var matrixLoc=0; private var modelLoc=0; private var eyeLoc=0
    private var detailLoc=0; private var skyLoc=0; private var timeLoc=0; private var overviewLoc=0
    private var skyRayLoc=0
    private var highlandsLoc=0
    private var snowLoc=0
    private var screenHeight=1
    private val skyRays=FloatArray(9)
    private var dynamicBuffer=0
    private val dynamic=ByteBuffer.allocateDirect(2048*6*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val trail=FloatArray(180*3)
    private var trailSize=0; private var trailCursor=0
    private var golferAddress=hole.tee
    private var golferAim=0f
    private var golferClub=GolfClub.DRIVER
    private var previousFlying=false
    private var previousBall=hole.tee
    private val projection=FloatArray(16)
    private val view=FloatArray(16)
    private val viewProjection=FloatArray(16)
    private val model=FloatArray(16)
    private val mvp=FloatArray(16)
    private var aspect=1f
    private var eyeX=0f; private var eyeY=0f; private var eyeZ=0f
    private var lookX=0f; private var lookY=0f; private var lookZ=0f
    private var initializedCamera=false
    private var wasTargetView=false; private var wasSliding=false
    private var previousTime=0L
    private var time=0f
    private var released=false
    private companion object { const val LEAVES=3 }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        released=false
        program=createProgram()
        matrixLoc=GL.glGetUniformLocation(program,"uMvp")
        modelLoc=GL.glGetUniformLocation(program,"uModel")
        eyeLoc=GL.glGetUniformLocation(program,"uEye")
        detailLoc=GL.glGetUniformLocation(program,"uDetail")
        skyLoc=GL.glGetUniformLocation(program,"uSky")
        timeLoc=GL.glGetUniformLocation(program,"uTime")
        overviewLoc=GL.glGetUniformLocation(program,"uOverview")
        skyRayLoc=GL.glGetUniformLocation(program,"uSkyRays")
        highlandsLoc=GL.glGetUniformLocation(program,"uHighlands")
        snowLoc=GL.glGetUniformLocation(program,"uSnow")
        meshes.forEach { it.upload() }
        terrain.upload(); groundShader.create(); lightShader.create()
        grass.create()
        golfer.create()
        val ids=IntArray(1)
        GL.glGenBuffers(1,ids,0); dynamicBuffer=ids[0]
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,dynamicBuffer)
        GL.glBufferData(GL.GL_ARRAY_BUFFER,2048*6*4,null,GL.GL_DYNAMIC_DRAW)
        GL.glEnable(GL.GL_DEPTH_TEST)
        GL.glDisable(GL.GL_DITHER)
        GL.glClearColor(.64f,.85f,.93f,1f)
        previousTime=0L
        onReady?.invoke()
    }

    override fun onSurfaceChanged(gl: GL10?, width:Int,height:Int) {
        GL.glViewport(0,0,width,height)
        aspect=width.toFloat()/height.coerceAtLeast(1)
        screenHeight=height.coerceAtLeast(1)
        Matrix.perspectiveM(projection,0,50f,aspect,.25f,1800f)
        projection[9]=-.12f // Raise the action above the translucent touch dock.
    }

    override fun onDrawFrame(gl: GL10?) {
        if(released) return
        val now=System.nanoTime()
        val dt=if(previousTime==0L) 1f/60 else ((now-previousTime)*1e-9f).coerceIn(.001f,.05f)
        previousTime=now; time+=dt
        val f=frame
        if(!f.flying && f.swingProgress<GolferPose.IMPACT) {
            golferAddress=f.ball;golferAim=f.aimAngle;golferClub=f.club
        }
        // The caddie may already prepare the next shot during a very short follow-through.
        val playerFrame=if(f.swingProgress>=GolferPose.IMPACT)
            f.copy(ball=golferAddress,aimAngle=golferAim,club=golferClub) else f
        // Let the finish read before the flight camera takes over.
        val showFinish=f.swingProgress in GolferPose.IMPACT.. .72f
        camera(if(showFinish) playerFrame.copy(flying=false) else f,dt)
        GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        GL.glUseProgram(program)
        GL.glUniform3f(eyeLoc,eyeX,eyeY,eyeZ)
        GL.glUniform1f(timeLoc,time)
        GL.glUniform1f(overviewLoc,if(f.overview!=null) 1f else 0f)
        GL.glUniform1f(skyLoc,0f)
        GL.glUniform1f(highlandsLoc,if(hole.highlands)1f else 0f)
        GL.glUniform1f(snowLoc,if(hole.snowy)1f else 0f)
        // Slope board, only while a shot is being prepared (gone during the stroke and the ball's run):
        // from above, around what the camera looks at, for any shot; otherwise from the ball to the hole for a putt.
        val putting=f.club==GolfClub.PUTTER
        val shownBoard=if(f.flying||f.preview==ShotPreview.NONE||!f.grid) null else when {
            f.overview!=null -> SlopeBoard.around(hole,lookX,lookZ,sqrt((lookX-eyeX).pow(2)+(lookY-eyeY).pow(2)+(lookZ-eyeZ).pow(2)),
                if(putting) hole.heightAt(f.ball.x,f.ball.z) else null)
            putting -> SlopeBoard.forPutt(hole,f.ball,f.preview.landing)
            else -> null
        }
        // Its squares follow the zoom: sized from the distance to the middle of the window.
        val (cell,fineWeight)=if(shownBoard!=null) {
            val mx=shownBoard.middleX; val mz=shownBoard.middleZ
            SlopeBoard.cell(sqrt((mx-eyeX).pow(2)+(hole.heightAt(mx,mz)-eyeY).pow(2)+(mz-eyeZ).pow(2)),shownBoard)
        } else 1f to 1f
        groundShader.draw(terrain,viewProjection,eyeX,eyeY,eyeZ,time,f.overview!=null,shownBoard,cell,fineWeight,
            hole.cup.x,hole.cup.z,2f*tan(Math.toRadians(25.0).toFloat())/screenHeight,screenHeight/900f)
        GL.glUseProgram(program)
        identity(); scenery.forEach { if(it.visible(viewProjection)) draw(it,.12f) }
        festive.scenery.forEach { if(it.visible(viewProjection)) draw(it,.04f) }
        festive.animate(time,P(eyeX,eyeY,eyeZ)) { mesh,position,yaw,scale ->
            identity(); Matrix.translateM(model,0,position.x,position.y,position.z)
            Matrix.rotateM(model,0,yaw*180f/PI.toFloat(),0f,1f,0f)
            Matrix.scaleM(model,0,scale,scale,scale)
            draw(mesh,0f)
        }
        identity()
        if(!hole.highlands && hole.islands.isEmpty()) {
            Matrix.translateM(model,0,landscape.millX,landscape.millY,landscape.millZ-2.2f)
            Matrix.rotateM(model,0,time*12f,0f,0f,1f)
            draw(blades,.05f)
        }
        wildlife()
        if(f.overview==null && hole.highlands && !hole.snowy) drawLeaves(f,dt)
        grass.draw(viewProjection,eyeX,eyeY,eyeZ,f.ball.x,f.ball.z,time)
        GL.glUseProgram(program)
        val ground=hole.heightAt(f.ball.x,f.ball.z)
        val altitude=(f.ball.y-ClassicHole.BALL_RADIUS-ground).coerceAtLeast(0f)
        // Below the turf the ball is inside the cup: true size, no shadow on the green above it.
        val sunk=f.ball.y<ground+ClassicHole.BALL_RADIUS-.002f
        // A real 43 mm ball, only enlarged with distance so that it never shrinks below a few pixels.
        val cameraDistance=sqrt((f.ball.x-eyeX).pow(2)+(f.ball.y-eyeY).pow(2)+(f.ball.z-eyeZ).pow(2))
        val radius=if(sunk) ClassicHole.BALL_RADIUS else max(ClassicHole.BALL_RADIUS,cameraDistance*.0026f)
        // The green is meshed finely enough for the ball to sit on it; coarser turf needs a little lift.
        val lift=if(sunk) 0f else if(hole.lieAt(f.ball.x,f.ball.z)==GolfLie.GREEN) .002f else .012f
        if(!sunk) {
            identity(); Matrix.translateM(model,0,f.ball.x,ground+.006f,f.ball.z)
            val shadeSize=radius*(1.1f+(altitude*.03f).coerceAtMost(2f))
            Matrix.scaleM(model,0,shadeSize,1f,shadeSize)
            draw(shadow,0f)
        }
        // As on a real green, the flagstick is taken out for putts (the physics ignores it too).
        if(f.club!=GolfClub.PUTTER) {
            val flagDistance=sqrt((hole.cup.x-eyeX).pow(2)+(hole.cup.z-eyeZ).pow(2))
            val flagScale=max(1f,flagDistance*.012f)
            identity(); Matrix.translateM(model,0,hole.cup.x,hole.heightAt(hole.cup.x,hole.cup.z),hole.cup.z)
            Matrix.rotateM(model,0,f.aimAngle*180f/PI.toFloat(),0f,1f,0f)
            Matrix.scaleM(model,0,flagScale,flagScale,flagScale)
            draw(flag,0f)
        } else if(!sunk) {
            // Flag out: a marker bobs over the hole so it can be found from anywhere on the green;
            // smaller when the view passes close by, so that it never hides the line.
            val markerDistance=sqrt((hole.cup.x-eyeX).pow(2)+(hole.cup.z-eyeZ).pow(2))
            val markerScale=max(markerDistance*.03f,min(1f,markerDistance*.15f))
            identity(); Matrix.translateM(model,0,hole.cup.x,hole.cup.y+(.45f+.04f*sin(time*2.4f))*markerScale,hole.cup.z)
            Matrix.scaleM(model,0,markerScale,markerScale,markerScale)
            draw(cupMarker,0f)
        }
        if(shownBoard!=null) drawBoardLights(shownBoard,if(fineWeight>=.5f) cell else cell*2f)
        if(!f.flying || f.swingProgress>=0f) drawGolfer(playerFrame)
        updateTrail(f)
        if(f.flying) drawTrail() else if(f.preview.landing!=null) drawPreview(f)
        identity(); Matrix.translateM(model,0,f.ball.x,f.ball.y+radius-ClassicHole.BALL_RADIUS+lift,f.ball.z)
        Matrix.scaleM(model,0,radius,radius,radius)
        draw(ball,0f)
        drawSky()
    }

    /**
     * Last, on the far plane, so that only the pixels nothing else covered are shaded: from above
     * or on the green the sky would otherwise be computed under the whole turf, then hidden.
     */
    private fun drawSky() {
        val tangent=tan(Math.toRadians(25.0)).toFloat()
        for(i in 0..2) {
            skyRays[i]=view[i*4]*tangent*aspect
            skyRays[i+3]=view[i*4+1]*tangent
            skyRays[i+6]=-view[i*4+2]-view[i*4+1]*.12f*tangent
        }
        GL.glUseProgram(program)
        GL.glUniformMatrix3fv(skyRayLoc,1,false,skyRays,0)
        GL.glUniform1f(skyLoc,1f)
        GL.glDepthFunc(GL.GL_LEQUAL)
        identity(); draw(sky,0f)
        GL.glDepthFunc(GL.GL_LESS)
        GL.glUniform1f(skyLoc,0f)
    }

    private fun camera(f:ClassicFrame,dt:Float) {
        val zoom=f.zoom.coerceIn(.6f,1.8f)
        val sx=sin(f.aimAngle); val sz=cos(f.aimAngle)
        val putting=f.club==GolfClub.PUTTER
        val landing=f.preview.landing
        val reach=landing?.let { hypot(it.x-f.ball.x,it.z-f.ball.z) } ?: 0f
        // Drawing a putt back, the view slides down the line to where the ball would stop, rising a
        // little: the strength reads on the green, as the landing view does for long shots.
        val slide=if(putting&&f.aiming&&f.overview==null&&!f.flying) max(0f,reach-2.5f) else 0f
        // Address view from just behind the player, looking down the line; a chase view in flight.
        val behind=if(putting) 3.4f+slide*.15f else if(f.flying) 15f else 7.5f
        val high=if(putting) 1.45f+slide*.2f else if(f.flying) 5.5f else 3.6f
        val ahead=if(putting) 2.5f+slide else if(f.flying) 8f else (f.club.carry*.3f).coerceIn(6f,60f)
        var ex=f.ball.x+sx*(slide-behind*zoom)
        var ez=f.ball.z+sz*(slide-behind*zoom)
        var ey=max(f.ball.y+high*zoom,hole.heightAt(ex,ez)+1f)
        var lx=f.ball.x+sx*ahead; var lz=f.ball.z+sz*ahead
        var ly=if(f.flying&&!putting) f.ball.y+.5f else hole.heightAt(lx,lz)+if(putting) .1f else 0f
        // Close to the hole the view comes earlier: a club that flies far must be pulled gently, so the cursor next to the cup has to be seen.
        val cupFromLanding=landing?.let { hypot(hole.cup.x-it.x,hole.cup.z-it.z) } ?: 0f
        val cupFromBall=hypot(hole.cup.x-f.ball.x,hole.cup.z-f.ball.z)
        val nearCup=landing!=null&&reach>6f&&cupFromBall>12f&&cupFromLanding<35f
        val targetView=f.aiming&&f.overview==null&&!putting&&!f.flying&&landing!=null&&(reach>45f||nearCup)
        if(targetView&&landing!=null) {
            // While the shot is drawn back, look down on the landing area: the ring moves with the pull.
            val back=(reach*.26f).coerceIn(20f,60f)*zoom
            val up=(reach*.1f).coerceIn(10f,24f)*zoom
            ex=landing.x-sx*back; ez=landing.z-sz*back
            ey=max(landing.y+up,hole.heightAt(ex,ez)+4f)
            lx=landing.x+sx*reach*.08f; lz=landing.z+sz*reach*.08f
            if(nearCup) { lx=(landing.x+hole.cup.x)*.5f; lz=(landing.z+hole.cup.z)*.5f }
            ly=hole.heightAt(lx,lz)
        }
        // Leaving the landing view, or playing a putt from far down its line, is a cut back to the player, as on television.
        if((wasTargetView&&!targetView)||(wasSliding&&!f.aiming)) initializedCamera=false
        wasTargetView=targetView; wasSliding=slide>1f
        f.overview?.let { aerial ->
            val eye=aerial.eye(hole); val look=aerial.look(hole)
            ex=eye.x; ey=eye.y; ez=eye.z; lx=look.x; ly=look.y; lz=look.z
        }
        // The free aerial camera follows the fingers closely; the others glide.
        val rate=if(f.overview!=null) 14f else if(f.flying) 6f else 4.7f
        val blend=if(initializedCamera) 1f-exp(-dt*rate) else 1f
        eyeX+=(ex-eyeX)*blend; eyeY+=(ey-eyeY)*blend; eyeZ+=(ez-eyeZ)*blend
        lookX+=(lx-lookX)*blend; lookY+=(ly-lookY)*blend; lookZ+=(lz-lookZ)*blend
        initializedCamera=true
        Matrix.setLookAtM(view,0,eyeX,eyeY,eyeZ,lookX,lookY,lookZ,0f,1f,0f)
        Matrix.multiplyMM(viewProjection,0,projection,0,view,0)
    }

    private fun wildlife() {
        repeat(4) { i ->
            val phase=time*.13f+i*1.7f
            val x=sin(phase)*28f+hole.width*.28f
            val z=hole.length*.46f+cos(phase)*38f
            identity(); Matrix.translateM(model,0,x,hole.heightAt(x,z)+18f+sin(time*.8f+i)*1.6f,z)
            Matrix.rotateM(model,0,-phase*57.3f,0f,1f,0f)
            Matrix.scaleM(model,0,1.2f,.7f+abs(sin(time*3.4f+i)),1f)
            draw(bird,0f)
        }
        if(hole.islands.isNotEmpty()) return
        val z=hole.tee.z+13f
        val x=hole.fairwayCenter(z)-hole.fairwayWidth(z)*.5f-4f
        if(hole.lieAt(x,z)!=GolfLie.ROUGH || hole.hazards.any { it.signedDistance(x,z)<1f }) return
        val cycle=(time%13f)
        val hop=if(cycle<1.6f) abs(sin(cycle*PI.toFloat()*2.5f))*.32f else 0f
        identity(); Matrix.translateM(model,0,x,hole.heightAt(x,z)+hop,z)
        Matrix.rotateM(model,0,35f,0f,1f,0f)
        Matrix.scaleM(model,0,1f,1f+.018f*sin(time*2.5f),1f)
        draw(rabbit.body,0f)
        for(s in intArrayOf(-1,1)) {
            identity(); Matrix.translateM(model,0,x,hole.heightAt(x,z)+hop,z)
            Matrix.rotateM(model,0,35f,0f,1f,0f)
            Matrix.translateM(model,0,s*.085f,.69f,-.25f)
            Matrix.rotateM(model,0,s*12f+sin(time*1.7f+s)*6f,0f,0f,1f)
            draw(rabbit.ear,0f)
        }
    }

    /** Rare dead leaves drifting with the wind: they come in upwind of the view, cross it and are gone. */
    private fun drawLeaves(f:ClassicFrame,dt:Float) {
        val wind=hypot(f.windX,f.windZ)
        if(wind<.05f) return
        val ux=f.windX/wind; val uz=f.windZ/wind
        var fx=lookX-eyeX; var fz=lookZ-eyeZ
        val fl=hypot(fx,fz).coerceAtLeast(1e-3f); fx/=fl; fz/=fl
        for(i in 0 until LEAVES) {
            if(leafWait[i]>0f) {
                leafWait[i]-=dt
                if(leafWait[i]>0f) continue
                // Appears ahead of the camera and upwind of the middle of the view.
                val depth=6f+leafRandom.nextFloat()*18f; val side=(leafRandom.nextFloat()-.5f)*20f; val upwind=12f+leafRandom.nextFloat()*8f
                leafX[i]=eyeX+fx*depth-fz*side-ux*upwind; leafZ[i]=eyeZ+fz*depth+fx*side-uz*upwind
                leafY[i]=hole.heightAt(leafX[i],leafZ[i])+1.6f+leafRandom.nextFloat()*2.4f
                leafAge[i]=0f
            }
            leafAge[i]+=dt
            val seed=leafSeed[i]
            leafX[i]+=(f.windX*.8f+sin(time*1.3f+seed)*.5f)*dt
            leafZ[i]+=(f.windZ*.8f+cos(time*1.1f+seed)*.5f)*dt
            leafY[i]+=sin(time*1.7f+seed*2f)*.35f*dt-.05f*dt
            val ground=hole.heightAt(leafX[i],leafZ[i])
            leafY[i]=max(leafY[i],ground+.8f)
            val away=hypot(leafX[i]-eyeX,leafZ[i]-eyeZ)
            if(away>46f||leafAge[i]>26f) { leafWait[i]=3f+leafRandom.nextFloat()*9f; continue }
            val scale=max(1f,away*.07f)
            identity(); Matrix.translateM(model,0,leafX[i],leafY[i],leafZ[i])
            Matrix.rotateM(model,0,time*70f+seed*57f,0f,1f,0f)
            Matrix.rotateM(model,0,sin(time*3.1f+seed)*55f,1f,0f,0f)
            Matrix.scaleM(model,0,scale,scale,scale)
            draw(leaves[i%leaves.size],0f)
        }
    }

    /** The same model/rig as the wardrobe, in a proper right-handed local frame. */
    private fun drawGolfer(f:ClassicFrame) {
        val putting=f.club==GolfClub.PUTTER
        val sx=sin(f.aimAngle); val sz=cos(f.aimAngle)
        val gx=f.ball.x-sz*.983f; val gz=f.ball.z+sx*.983f
        val stance=if(putting) .17f else .225f
        val lead=hole.heightAt(gx+sx*stance,gz+sz*stance)
        val trail=hole.heightAt(gx-sx*stance,gz-sz*stance)
        val gy=(lead+trail)*.5f
        // Small contact shadows anchor each shoe on the sampled terrain.
        for(side in intArrayOf(-1,1)) {
            val x=gx+sx*stance*side;val z=gz+sz*stance*side
            identity();Matrix.translateM(model,0,x,hole.heightAt(x,z)+.014f,z)
            Matrix.rotateM(model,0,(f.aimAngle+PI.toFloat()/2)*180f/PI.toFloat(),0f,1f,0f)
            Matrix.scaleM(model,0,.11f,1f,.19f);draw(shadow,0f)
        }
        golfer.pose.update(f.swingProgress,f.swingPower,putting,time,f.golfer.female,
            leadGround=lead-gy,trailGround=trail-gy,ballGround=hole.heightAt(f.ball.x,f.ball.z)-gy)
        identity();Matrix.translateM(model,0,gx,gy,gz)
        Matrix.rotateM(model,0,(f.aimAngle+PI.toFloat()/2)*180f/PI.toFloat(),0f,1f,0f)
        golfer.draw(f.golfer,putting,f.club.ordinal<=2,model,viewProjection,eyeX,eyeY,eyeZ)
        GL.glUseProgram(program)
    }

    /** Lights gliding down the board's lines, rebuilt only when the squares change or the window moves by a quarter of one. */
    private fun drawBoardLights(shown:SlopeBoard,cell:Float) {
        fun step(v:Float)=(v*4f/cell).roundToInt()
        val key=listOf(cell,step(shown.fromX),step(shown.fromZ),step(shown.toX),step(shown.toZ),step(shown.radius))
        if(key!=lightsKey) { boardLights=shown.lights(hole,cell); lightsKey=key }
        val lights=boardLights
        if(lights.isEmpty()) return
        dynamic.clear()
        val lift=.012f+cell*.006f
        var i=0
        while(i<lights.size) {
            val t=(time*lights[i+6]+lights[i+7])%1f
            putPoint(lights[i]+(lights[i+3]-lights[i])*t,lights[i+1]+(lights[i+4]-lights[i+1])*t+lift,
                lights[i+2]+(lights[i+5]-lights[i+2])*t,1f,1f,.9f)
            i+=9
        }
        dynamic.flip()
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,dynamicBuffer)
        GL.glBufferSubData(GL.GL_ARRAY_BUFFER,0,lights.size/9*24,dynamic)
        // About 4 % of a square across, so they shrink with distance like the lines.
        lightShader.draw(dynamicBuffer,lights.size/9,viewProjection,.04f*cell*screenHeight/(2f*tan(Math.toRadians(25.0).toFloat())))
        GL.glUseProgram(program)
    }

    private fun updateTrail(f:ClassicFrame) {
        if(f.flying && !previousFlying) { trailSize=0; trailCursor=0 }
        if(f.flying && (hypot(f.ball.x-previousBall.x,f.ball.z-previousBall.z)>.6f || abs(f.ball.y-previousBall.y)>.6f)) {
            trail[trailCursor*3]=f.ball.x; trail[trailCursor*3+1]=f.ball.y; trail[trailCursor*3+2]=f.ball.z
            trailCursor=(trailCursor+1)%180; trailSize=min(180,trailSize+1)
            previousBall=f.ball
        }
        previousFlying=f.flying
    }

    private fun putPoint(x:Float,y:Float,z:Float,r:Float,g:Float,b:Float) {
        dynamic.put(x).put(y).put(z).put(r).put(g).put(b)
    }
    /** Dashed polyline; [lift] raises points that lie on the turf above the terrain mesh. */
    private fun dashes(points:List<GolfPoint>,dash:Float,lift:Float,r:Float,g:Float,b:Float):Int {
        var travelled=0f
        var count=0
        for(i in 1 until points.size) {
            val p=points[i-1]; val q=points[i]
            val length=sqrt((q.x-p.x).pow(2)+(q.y-p.y).pow(2)+(q.z-p.z).pow(2))
            if(length<1e-4f) continue
            var along=0f
            while(along<length && count<2046) {
                val boundary=(floor(travelled/dash)+1f)*dash
                val part=min(length-along,(boundary-travelled).coerceAtLeast(.001f))
                if((floor((travelled+.0001f)/dash).toInt() and 1)==0) {
                    val a=along/length; val c=(along+part)/length
                    putPoint(p.x+(q.x-p.x)*a,p.y+(q.y-p.y)*a+lift,p.z+(q.z-p.z)*a,r,g,b)
                    putPoint(p.x+(q.x-p.x)*c,p.y+(q.y-p.y)*c+lift,p.z+(q.z-p.z)*c,r,g,b)
                    count+=2
                }
                travelled+=part; along+=part
            }
        }
        return count
    }
    private fun drawPreview(f:ClassicFrame) {
        val shot=f.preview
        val end=shot.landing ?: return
        val putting=f.club==GolfClub.PUTTER
        val bright=if(f.aiming) 1f else .82f
        dynamic.clear()
        drawDynamic(dashes(shot.flight,if(putting) .25f else if(shot.carry<40f) .9f else 2.5f,.03f,bright,.95f*bright,.67f*bright),GL.GL_LINES)
        // Ground track of the arc: the line the shot follows on the turf, readable from any view.
        val track=shot.flight.map { GolfPoint(it.x,hole.heightAt(it.x,it.z),it.z) }
        dynamic.clear()
        drawDynamic(dashes(track,if(putting) .25f else 1.2f,.05f,1f*bright,1f*bright,1f*bright),GL.GL_LINES)
        if(shot.roll.size>1) {
            dynamic.clear()
            drawDynamic(dashes(shot.roll,.45f,.04f,.80f,.92f,.98f),GL.GL_LINES)
        }
        val r=if(putting) .22f else (shot.carry*.02f).coerceIn(.8f,4.5f)
        dynamic.clear()
        val red=shot.hazard
        for(i in 0..48) {
            val a=i*2f*PI.toFloat()/48f
            val x=end.x+cos(a)*r; val z=end.z+sin(a)*r
            putPoint(x,hole.heightAt(x,z)+.05f,z,1f,if(red) .42f else .93f,if(red) .36f else .55f)
        }
        drawDynamic(49,GL.GL_LINE_STRIP)
        if(!putting) {
            val scale=max(1f,sqrt((end.x-eyeX).pow(2)+(end.y-eyeY).pow(2)+(end.z-eyeZ).pow(2))*.016f)
            identity(); Matrix.translateM(model,0,end.x,hole.heightAt(end.x,end.z)+.04f,end.z)
            Matrix.scaleM(model,0,scale,scale,scale)
            draw(if(red) hazardPin else pin,0f)
        }
    }
    private fun drawTrail() {
        if(trailSize<2) return
        dynamic.clear()
        val start=(trailCursor-trailSize+180)%180
        for(i in 0 until trailSize) {
            val index=(start+i)%180
            val t=i.toFloat()/trailSize
            putPoint(trail[index*3],trail[index*3+1],trail[index*3+2],.67f+t*.33f,.88f+t*.12f,.89f+t*.11f)
        }
        drawDynamic(trailSize,GL.GL_LINE_STRIP)
    }
    private fun drawDynamic(count:Int,primitive:Int) {
        dynamic.flip()
        identity(); uniforms(0f)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,dynamicBuffer)
        GL.glBufferSubData(GL.GL_ARRAY_BUFFER,0,count*24,dynamic)
        GL.glEnableVertexAttribArray(0); GL.glEnableVertexAttribArray(1)
        GL.glVertexAttribPointer(0,3,GL.GL_FLOAT,false,24,0)
        GL.glVertexAttribPointer(1,3,GL.GL_FLOAT,false,24,12)
        GL.glLineWidth(2f)
        GL.glDrawArrays(primitive,0,count)
    }
    private fun identity() = Matrix.setIdentityM(model,0)
    private fun uniforms(detail:Float) {
        Matrix.multiplyMM(mvp,0,viewProjection,0,model,0)
        GL.glUniformMatrix4fv(matrixLoc,1,false,mvp,0)
        GL.glUniformMatrix4fv(modelLoc,1,false,model,0)
        GL.glUniform1f(detailLoc,detail)
    }
    private fun draw(mesh:ClassicMesh,detail:Float) { uniforms(detail); mesh.draw() }

    fun release() {
        if(released) return
        released=true
        meshes.forEach { it.delete() }
        terrain.delete(); groundShader.release(); lightShader.release()
        grass.release()
        golfer.release()
        GL.glDeleteBuffers(1,intArrayOf(dynamicBuffer),0)
        GL.glDeleteProgram(program)
    }

    private fun createProgram():Int {
        fun shader(type:Int,source:String):Int {
            val id=GL.glCreateShader(type)
            GL.glShaderSource(id,source); GL.glCompileShader(id)
            val result=IntArray(1); GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,result,0)
            check(result[0]!=0) { GL.glGetShaderInfoLog(id) }
            return id
        }
        val vertex=shader(GL.GL_VERTEX_SHADER,"""
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform float uSky;
            attribute vec3 aPosition;
            attribute vec3 aColour;
            varying vec3 vColour;
            varying vec3 vWorld;
            void main() {
                vColour=aColour;
                vWorld=(uModel*vec4(aPosition,1.0)).xyz;
                gl_Position=uSky>0.5?vec4(aPosition.xy,1.0,1.0):uMvp*vec4(aPosition,1.0);
                gl_PointSize=5.0;
            }
        """.trimIndent())
        val fragment=shader(GL.GL_FRAGMENT_SHADER,"""
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform vec3 uEye;
            uniform float uDetail;
            uniform float uTime;
            uniform float uOverview;
            uniform float uSky;
            varying vec3 vColour;
            varying vec3 vWorld;
            uniform mat3 uSkyRays;
            uniform float uHighlands;
            uniform float uSnow;
            float skyHash(vec2 p) {
                vec3 q=fract(vec3(p.xyx)*.1031);
                q+=dot(q,q.yzx+33.33);
                return fract((q.x+q.y)*q.z);
            }
            float skyNoise(vec2 p) {
                vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
                return mix(mix(skyHash(i),skyHash(i+vec2(1.0,0.0)),f.x),
                    mix(skyHash(i+vec2(0.0,1.0)),skyHash(i+vec2(1.0,1.0)),f.x),f.y);
            }
            void main() {
                if(uSky>0.5) {
                    vec3 ray=normalize(uSkyRays*vec3(vWorld.xy,1.0));
                    float up=max(ray.y,0.0);
                    vec3 colour=mix(mix(vec3(.79,.84,.79),vec3(.75,.72,.67),uHighlands),
                        mix(vec3(.22,.47,.68),vec3(.32,.42,.55),uHighlands),pow(up,.48));
                    colour=mix(colour,mix(vec3(.83,.89,.95),vec3(.27,.46,.66),pow(up,.48)),uSnow);
                    vec3 sun=normalize(vec3(-.35,.86,-.36));
                    float glow=max(0.0,dot(ray,sun));
                    colour+=vec3(.26,.20,.10)*pow(glow,24.0);
                    colour=mix(colour,vec3(1.0,.96,.79),smoothstep(.99955,.99988,glow));
                    if(ray.y>0.015) {
                        vec2 p=ray.xz/(ray.y+.24)*2.2+vec2(uTime*.008,0.0);
                        float n=skyNoise(p)*.57+skyNoise(p*2.1)*.28+skyNoise(p*4.3)*.15;
                        float clouds=smoothstep(mix(.49,.40,uHighlands),.72,n)*smoothstep(.015,.18,ray.y);
                        vec3 cloud=mix(vec3(.69,.76,.77),vec3(.99,.97,.88),smoothstep(.48,.76,n));
                        colour=mix(colour,cloud,clouds*.91);
                    }
                    gl_FragColor=vec4(colour,1.0);
                    return;
                }
                vec3 colour=vColour;
                float grain=sin(vWorld.x*7.1+vWorld.z*4.2)*sin(vWorld.z*8.3-vWorld.x*3.5);
                float distanceToEye=length((vWorld-uEye)*.01)*100.0;
                float grainFade=1.0-smoothstep(18.0,65.0,distanceToEye);
                colour*=1.0+grain*.028*uDetail*grainFade;
                if(uDetail>.5 && colour.b>colour.r*1.6 && colour.g>.43) {
                    float ripple=pow(max(0.0,sin(vWorld.z*3.1+sin(vWorld.x*.47+uTime*.6)+uTime*.8)),18.0);
                    colour=mix(colour,vec3(.68,.88,.85),ripple*.16*grainFade);
                }
                float fog=smoothstep(100.0,760.0,distanceToEye)*mix(.56,.13,uOverview);
                colour=mix(colour,mix(vec3(.70,.79,.78),vec3(.68,.72,.76),uHighlands),fog);
                gl_FragColor=vec4(colour,1.0);
            }
        """.trimIndent())
        val id=GL.glCreateProgram()
        GL.glAttachShader(id,vertex); GL.glAttachShader(id,fragment)
        GL.glBindAttribLocation(id,0,"aPosition"); GL.glBindAttribLocation(id,1,"aColour")
        GL.glLinkProgram(id)
        GL.glDeleteShader(vertex); GL.glDeleteShader(fragment)
        val result=IntArray(1); GL.glGetProgramiv(id,GL.GL_LINK_STATUS,result,0)
        check(result[0]!=0) { GL.glGetProgramInfoLog(id) }
        return id
    }
}
