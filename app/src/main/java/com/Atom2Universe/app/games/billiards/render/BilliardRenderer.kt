package com.Atom2Universe.app.games.billiards.render

import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.Atom2Universe.app.games.billiards.*
import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import com.Atom2Universe.app.games.toyboxracers.models.DecorShape
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette
import com.Atom2Universe.app.games.toyboxracers.render.ColoredMesh
import com.Atom2Universe.app.games.toyboxracers.render.DecorMeshFactory
import com.Atom2Universe.app.games.toyboxracers.render.MeshBuilder
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import java.util.concurrent.ConcurrentLinkedQueue

data class BilliardFrame(val balls: List<Ball>,val pins: List<Pin>,val cueId: Int,val shot: Shot,
    val moving: Boolean,val nominated: Int = -1,val pocket: Int = -1,val cuePullback: Double = 0.0) {
    /** Same picture: positions, orientations and states. Velocities are not drawn. */
    fun looksLike(o: BilliardFrame)=cueId==o.cueId && shot==o.shot && moving==o.moving && nominated==o.nominated &&
        pocket==o.pocket && cuePullback==o.cuePullback && pins==o.pins && balls.size==o.balls.size &&
        balls.indices.all { i -> val a=balls[i]; val b=o.balls[i]; a.id==b.id && a.p==b.p && a.motion==b.motion && a.q.contentEquals(b.q) }
}

/** The room and all furniture are procedural Toybox meshes. No downloaded art. */
class BilliardRenderer(private val config: BilliardConfig,private val table: BilliardTable,
                       private val numberLabel: (Int)->String) : GLSurfaceView.Renderer {
    @Volatile var frame: BilliardFrame?=null
    @Volatile var trace: Trace?=null
    @Volatile var rotationStripes=config.rotationStripes
    @Volatile var look=config.look
    @Volatile var cueTint=config.cueTint
    @Volatile var showCue=true
    @Volatile var renderedFrames=0L; private set
    @Volatile var targetZone: Pair<V3,Double>?=null
    private var zoneMesh: ColoredMesh?=null
    private var builtZone: Pair<V3,Double>?=null
    val visualAnimating get()=drops.isNotEmpty() || stroke?.let { System.nanoTime()-it.start<180_000_000L }==true
    /** The garden water ripples on its own: that room keeps its slow redraws at rest. */
    val animatedScenery get()=config.room==BilliardRoom.GARDEN
    val camera=BilliardCamera()
    @Volatile var hudTop=0f
    @Volatile var hudBottom=0f
    @Volatile var hudLeft=0f
    @Volatile var hudRight=0f
    @Volatile private var inverse=FloatArray(16)
    @Volatile private var projected=FloatArray(16)
    @Volatile private var screenW=1
    @Volatile private var screenH=1
    private lateinit var shader: BilliardShader
    private val meshes=mutableListOf<ColoredMesh>()
    private lateinit var floor: ColoredMesh
    private lateinit var room: ColoredMesh
    private lateinit var walls: ColoredMesh
    private lateinit var furniture: ColoredMesh
    private lateinit var base: ColoredMesh
    private lateinit var felt: ColoredMesh
    private lateinit var cushions: ColoredMesh
    private lateinit var pocketLinings: ColoredMesh
    private lateinit var sphere: ColoredMesh
    private lateinit var shadow: ColoredMesh
    private lateinit var cueMesh: ColoredMesh
    private lateinit var pinMesh: ColoredMesh
    private lateinit var metalwork: ColoredMesh
    private lateinit var luminaires: ColoredMesh
    private lateinit var hanging: ColoredMesh
    private lateinit var rug: ColoredMesh
    private lateinit var markings: ColoredMesh
    private var garden: ColoredMesh?=null
    private var lawn: ColoredMesh?=null
    private var water: ColoredMesh?=null
    private var sky: ColoredMesh?=null
    private var guides: ColoredMesh?=null
    private var lastTrace: Trace?=null
    private val vp=FloatArray(16); private val projection=FloatArray(16)
    private val inkEye=FloatArray(3)
    private var inkSpan: Float?=null
    private val view=FloatArray(16); private val model=FloatArray(16)
    private val l=table.length.toFloat(); private val w=table.width.toFloat()
    private val height=.78f
    private data class Stroke(val ball: Ball,val shot: Shot,val start: Long)
    private data class Drop(val id: Int,val p: V3,val target: V3,val start: Long)
    @Volatile private var stroke: Stroke?=null
    private val drops=ConcurrentLinkedQueue<Drop>()
    fun animateStrike(ball: Ball,shot: Shot) { stroke=Stroke(ball.copyDeep(),shot,System.nanoTime()) }
    fun effect(e: BilliardEvent) {
        if(e.kind==EventKind.POCKET && e.position!=null) {
            val target=table.pockets.firstOrNull { it.id==e.other }?.center ?: e.position
            drops.add(Drop(e.ball,e.position,target,System.nanoTime()))
        }
    }
    private fun color(c: Int,a: Float=1f)=floatArrayOf(((c shr 16) and 255)/255f,((c shr 8) and 255)/255f,(c and 255)/255f,a)
    private fun mesh(build: MeshBuilder.()->Unit)=MeshBuilder().apply(build).build().also { it.upload(); meshes+=it }
    private fun pos(v: V3,y: Float=height+.002f)=Vec3(v.x.toFloat()-l/2,y,v.y.toFloat()-w/2)

    override fun onSurfaceCreated(gl: GL10?, egl: EGLConfig?) {
        // Old handles belong to the previous EGL context and must never be deleted here.
        meshes.clear(); guides=null; lastTrace=null; zoneMesh=null; builtZone=null
        shader=BilliardShader(numberLabel,table)
        GL.glEnable(GL.GL_DEPTH_TEST); GL.glDisable(GL.GL_CULL_FACE)
        GL.glEnable(GL.GL_BLEND); GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA)
        if(config.room==BilliardRoom.GARDEN) GL.glClearColor(.46f,.64f,.68f,1f) else GL.glClearColor(.045f,.06f,.075f,1f)
        floor=mesh { box(0f,-.035f,0f,9f,.07f,8f,color(config.room.floor)) }
        garden=null; lawn=null; water=null; sky=null
        if(config.room==BilliardRoom.GARDEN) {
            lawn=mesh { BilliardEnvironmentMesh.lawn(this) }
            garden=mesh { BilliardEnvironmentMesh.garden(this) }
            water=mesh { BilliardEnvironmentMesh.water(this) }
            sky=mesh { BilliardEnvironmentMesh.sky(this) }
        }
        walls=mesh {
            if(config.room!=BilliardRoom.GARDEN) {
                box(0f,1.25f,-3.4f,9f,2.5f,.12f,color(config.room.wall))
                box(-4.4f,1.25f,0f,.12f,2.5f,7f,color(config.room.wall))
                box(4.4f,1.25f,0f,.12f,2.5f,7f,color(config.room.wall))
                box(0f,1.25f,3.4f,9f,2.5f,.12f,color(config.room.wall))
            }
        }
        room=mesh {
            if(config.room!=BilliardRoom.GARDEN) {

                // Framed abstract artwork opposite the break position.
                box(4.30f,1.65f,-.25f,.07f,1.05f,1.9f,color(0xB39861))
                box(4.25f,1.65f,-.25f,.02f,.94f,1.78f,color(0x17282E))
                for(i in 0..5) box(4.23f,1.4f+i*.07f,-.7f+i*.19f,.01f,.1f,.36f,color(if(i%2==0) 0x7A8A72 else 0xBB9867))
                for(z in floatArrayOf(-2.9f,2.9f)) {
                    box(4.24f,1.7f,z,.03f,1.1f,.45f,color(0x9D845B))
                    box(4.20f,1.7f,z,.015f,.98f,.35f,color(0xBFCCC4))
                }
                for(z in floatArrayOf(-3.25f,3.25f)) {
                    box(0f,.12f,z,8.6f,.24f,.09f,color(config.room.wood))
                    box(0f,.88f,z,8.6f,.055f,.08f,color(config.room.wood))
                }
                for(x in floatArrayOf(-4.24f,4.24f)) {
                    box(x,.12f,0f,.09f,.24f,6.4f,color(config.room.wood))
                    box(x,.88f,0f,.08f,.055f,6.4f,color(config.room.wood))
                    for(i in 0..10) box(x,.46f,-2.8f+i*.56f,.06f,.8f,.032f,color(config.room.wood))
                }
                repeat(3) { i ->
                    box(-2.6f+i*2.6f,1.65f,-3.32f,1.3f,.85f,.03f,color(if(config.room==BilliardRoom.NEON) 0x894DBD else 0x9FADAC))
                    box(-2.6f+i*2.6f,1.65f,-3.29f,1.15f,.7f,.03f,color(if(config.room==BilliardRoom.NEON) 0x26344B else 0xBCDDD8))
                }
            } else {
                for(x in floatArrayOf(-3.6f,3.6f)) for(z in floatArrayOf(-2.8f,2.8f)) box(x,1.3f,z,.13f,2.6f,.13f,color(config.room.wood))
                for(i in 0..10) box(-3.6f+i*.72f,2.58f,0f,.10f,.12f,5.8f,color(config.room.wood))
                for(z in floatArrayOf(-3.3f,3.3f)) {
                    // Leave an entrance onto the garden path.
                    for(x in floatArrayOf(-2.45f,2.45f)) box(x,.75f,z,3.5f,.09f,.10f,color(config.room.wood))
                    for(i in 0..28) {
                        val x=-4.2f+i*.3f
                        if(abs(x)>.70f) box(x,.38f,z,.035f,.7f,.035f,color(config.room.wood))
                    }
                }
            }
            if(config.room!=BilliardRoom.GARDEN) {
            // Wall panelling / window mullions give the room a human scale.
            for(i in 0..14) {
                val x=-4.1f+i*.57f
                box(x,.46f,-3.26f,.035f,.9f,.04f,color(config.room.wood))
                box(x,.92f,-3.26f,.5f,.035f,.045f,color(config.room.wood))
            }
            box(0f,.06f,-3.24f,8.6f,.12f,.07f,color(config.room.wood))
            for(i in 0..2) {
                val x=-2.6f+i*2.6f
                box(x,1.65f,-3.25f,.026f,.7f,.02f,color(config.room.wood))
                box(x,1.65f,-3.25f,1.15f,.026f,.02f,color(config.room.wood))
            }
            // Wall-mounted cue rack.
            for(i in 0..4) {
                cylinderY(2.5f+i*.08f,1.1f,-3.18f,1.25f,.009f,12,color(0xD4B587))
                cylinderY(2.5f+i*.08f,.59f,-3.18f,.23f,.010f,12,color(0x46352B))
            }
            box(2.66f,.53f,-3.16f,.52f,.045f,.09f,color(config.room.wood))
            box(2.66f,1.51f,-3.16f,.52f,.035f,.08f,color(config.room.wood))
            BilliardEnvironmentMesh.interior(this,config.room)
            }
        }
        hanging=mesh {
            for(x in floatArrayOf(-l*.23f,l*.23f)) {
                box(x,2.22f,0f,.56f,.13f,.28f,color(0x37342C))
                cylinderY(x,2.62f,0f,.67f,.008f,12,color(0x272B2D))
                box(x,2.20f,0f,.59f,.016f,.30f,color(0x86704A))
            }
        }
        val placements=buildList {
            fun place(id: String,x: Float,z: Float,scale: Float=.09f,turn: Int=0) {
                val source=DecorCatalog[id]
                val fabric=when(config.room) { BilliardRoom.CLUB->0x3B4A3B; BilliardRoom.HOME->0xA18F72; BilliardRoom.LOFT->0x444E59; BilliardRoom.NEON->0x322B4C; BilliardRoom.GARDEN->0x7B8A69 }
                val palette=source.copy(parts=source.parts.map { part ->
                    val c=when(part.color) {
                        DecorPalette.WOOD->config.room.wood
                        DecorPalette.CREAM->0xCEBE9C
                        DecorPalette.GOLD->0xAD8B4C
                        DecorPalette.LEAF,DecorPalette.MINT->if(id=="living.plant") 0x355A3E else fabric
                        DecorPalette.ROSE,DecorPalette.LILAC,DecorPalette.BLUE->if(id=="living.plant") 0x675043 else fabric
                        else->part.color
                    }
                    part.copy(color=c)
                })
                add(DecorPlacement(palette,x,0f,z,turn,scale))
            }
            place("living.sofa",-1.65f,-2.82f,.095f)
            place("living.armchair",1.6f,-2.7f,.09f)
            // Sofa front: -2.3925 m. Table back: -2.2275 m, leaving 16.5 cm.
            place("living.coffee_table",-1.65f,-1.89f,.075f)
            place("living.floor_lamp",-3.05f,-2.7f,.09f)
            place("living.plant",2.8f,-2.65f,.11f)
            place("living.plant",-3.5f,2.4f,.10f)
            if(config.room==BilliardRoom.LOFT) place("living.tv_cabinet",3.5f,0f,.09f,3)
            if(config.room==BilliardRoom.HOME) place("living.dining_chair",2.8f,1.8f,.09f,3)
            if(config.room==BilliardRoom.GARDEN) {
                place("living.plant",3.1f,2.2f,.15f); place("living.plant",-3.3f,-1.7f,.15f)
                place("living.armchair",-1.65f,2.75f,.09f,2)
                place("living.armchair",1.65f,2.75f,.09f,2)
            }
        }
        furniture=mesh {
            placements.forEach { placement ->
                val rounded=placement.model.parts.filter { it.shape==DecorShape.OVAL }
                val solid=placement.model.parts.filter { it.shape!=DecorShape.OVAL }
                if(solid.isNotEmpty()) DecorMeshFactory.add(this,placement.copy(model=placement.model.copy(parts=solid)))
                placed(placement) {
                    rounded.forEach { p -> lowPolyEllipsoid(p.x,p.y,p.z,p.width/2,p.height/2,p.depth/2,20,32,color(p.color)) }
                }
            }
        }
        val tableMesh=BilliardTableMesh(table,height)
        base=mesh { tableMesh.wood(this,color(config.room.wood)) }
        felt=mesh { tableMesh.cloth(this,color(config.room.felt)) }
        cushions=mesh { tableMesh.cushions(this,color(config.room.felt)) }
        pocketLinings=mesh { tableMesh.pockets(this) }
        metalwork=mesh { tableMesh.trim(this,color(0xB79A5E)) }
        luminaires=mesh {
            for(x in floatArrayOf(-l*.23f,l*.23f)) box(x,2.16f,0f,.46f,.012f,.20f,color(0xFFEDC1))
            if(config.room==BilliardRoom.NEON) {
                box(0f,.94f,-3.20f,8.6f,.012f,.016f,color(0xB652E7))
                box(-4.25f,.94f,0f,.016f,.012f,6.4f,color(0x44CFE5))
            }
        }
        rug=mesh {
            if(config.room==BilliardRoom.CLUB || config.room==BilliardRoom.HOME) {
                box(0f,.004f,0f,l+1.05f,.008f,w+.95f,color(if(config.room==BilliardRoom.CLUB) 0x492E2B else 0x807964))
                for(z in floatArrayOf(-w/2-.45f,w/2+.45f)) box(0f,.009f,z,l+.95f,.002f,.03f,color(0xA58B62))
                for(x in floatArrayOf(-l/2-.48f,l/2+.48f)) box(x,.009f,0f,.03f,.002f,w+.90f,color(0xA58B62))
            }
        }
        sphere=mesh { lowPolyEllipsoid(0f,0f,0f,1f,1f,1f,24,40,color(0xFFFFFF)) }
        shadow=mesh { cylinderY(0f,0f,0f,.0001f,1f,32,color(0xFFFFFF)) }
        cueMesh=mesh {
            // Built along local +Y; pivot at the tip.
            for(i in 0 until 24) {
                val a=i*2*PI/24; val b=(i+1)*2*PI/24
                val tip=CueReach.TIP.toFloat(); val butt=CueReach.BUTT.toFloat(); val length=CueReach.LENGTH.toFloat()
                quad(Vec3(cos(a).toFloat()*tip,0f,sin(a).toFloat()*tip),
                    Vec3(cos(b).toFloat()*tip,0f,sin(b).toFloat()*tip),
                    Vec3(cos(b).toFloat()*butt,length,sin(b).toFloat()*butt),
                    Vec3(cos(a).toFloat()*butt,length,sin(a).toFloat()*butt),color(0xFFFFFF))
            }
            cylinderY(0f,.003f,0f,.006f,.0057f,16,color(0x77B9C3))
            cylinderY(0f,.30f,0f,.009f,.009f,16,color(0x695447))
        }
        pinMesh=mesh {
            cylinderY(0f,.006f,0f,.012f,.005f,12,color(0xFFFFFF))
            lowPolyEllipsoid(0f,.014f,0f,.0045f,.006f,.0045f,8,12,color(0xFFFFFF))
            cylinderY(0f,.022f,0f,.006f,.0035f,12,color(0xFFFFFF))
        }
        markings=mesh {
            fun line(a: V3,b: V3,c: Int=0xC9D5B9) = ribbon(this,pos(a,height+.0018f),pos(b,height+.0018f),.0015f,color(c,.55f))
            for(region in table.regions) {
                if(region.corner) line(V3(region.x0,region.y1),V3(region.x1,region.y0))
                else {
                    line(V3(region.x0,region.y0),V3(region.x1,region.y0))
                    line(V3(region.x1,region.y0),V3(region.x1,region.y1))
                    line(V3(region.x1,region.y1),V3(region.x0,region.y1))
                    line(V3(region.x0,region.y1),V3(region.x0,region.y0))
                }
            }
            if(table.family==TableFamily.SNOOKER) {
                line(V3(table.headLine),V3(table.headLine,table.width))
                for(i in 0 until 64) {
                    fun point(k: Int): V3 { val a=PI/2+k*PI/64; return V3(table.headLine+cos(a)*table.dRadius,table.width/2+sin(a)*table.dRadius) }
                    line(point(i),point(i+1))
                }
                for(id in 16..21) { val p=table.colorSpot(id); cylinderY((p.x-l/2).toFloat(),height+.002f,(p.y-w/2).toFloat(),.001f,.003f,12,color(0xEEE5CC)) }
            } else if(table.pockets.isNotEmpty()) {
                line(V3(table.headLine),V3(table.headLine,table.width))
                val p=table.footSpot
                cylinderY((p.x-l/2).toFloat(),height+.002f,0f,.001f,.004f,12,color(0xEEE5CC))
            }
            fun diamond(x: Float,z: Float,alongLength: Boolean) {
                val dx=if(alongLength) .009f else .004f
                val dz=if(alongLength) .004f else .009f
                val y=height+tableMesh.railTop+.0005f
                quad(Vec3(x-dx,y,z),Vec3(x,y,z+dz),Vec3(x+dx,y,z),Vec3(x,y,z-dz),color(0xEDE1C3))
            }
            for(i in 1..7) if(i!=4 || table.pockets.isEmpty()) for(z in floatArrayOf(-w/2-.135f,w/2+.135f))
                diamond(-l/2+i*l/8,z,false)
            for(i in 1..3) for(x in floatArrayOf(-l/2-.135f,l/2+.135f))
                diamond(x,-w/2+i*w/4,true)
        }
    }
    /** Touches, overlays and HUD margins stay in the view's pixels; only the GL buffer is smaller. */
    fun viewSize(width: Int,height: Int) { screenW=width; screenH=height }
    override fun onSurfaceChanged(gl: GL10?,width: Int,height: Int) {
        if(screenW<=1) { screenW=width; screenH=height }
        GL.glViewport(0,0,width,height)
    }
    override fun onDrawFrame(gl: GL10?) {
        GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        val aspect=screenW.toFloat()/screenH.coerceAtLeast(1)
        val available=(1f-(hudTop+hudBottom)/screenH.coerceAtLeast(1)).coerceIn(.20f,1f)
        val horizontal=(1f-(hudLeft+hudRight)/screenW.coerceAtLeast(1)).coerceIn(.25f,1f)
        val pose=camera.pose(frame,l,w,aspect*horizontal/available,available)
        val eye=pose.eye; val target=pose.target
        val eyeX=eye.x.toFloat(); val eyeY=eye.y.toFloat(); val eyeZ=eye.z.toFloat()
        val halfHeight=pose.orthographicHalfHeight
        val outdoor=config.room==BilliardRoom.GARDEN
        val far=if(outdoor) 160f else 40f
        if(halfHeight!=null) {
            Matrix.orthoM(projection,0,-halfHeight*aspect,halfHeight*aspect,-halfHeight,halfHeight,.012f,far)
            projection[12]=(hudLeft-hudRight)/screenW.coerceAtLeast(1)
            projection[13]=(hudBottom-hudTop)/screenH.coerceAtLeast(1)
        } else {
            Matrix.perspectiveM(projection,0,46f,aspect,.012f,far)
            projection[9]=(hudTop-hudBottom)/screenH.coerceAtLeast(1)
            projection[8]=(hudRight-hudLeft)/screenW.coerceAtLeast(1)
        }
        Matrix.setLookAtM(view,0,eyeX,eyeY,eyeZ,target.x.toFloat(),target.y.toFloat(),target.z.toFloat(),pose.up.x.toFloat(),pose.up.y.toFloat(),pose.up.z.toFloat())
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        projected=vp.copyOf()
        val inv=FloatArray(16); if(Matrix.invertM(inv,0,vp,0)) inverse=inv
        // Read once: the interface thread may publish a newer frame while this one is drawn.
        val f=frame
        val cartoon=look==BilliardLook.CARTOON
        inkEye[0]=eyeX; inkEye[1]=eyeY; inkEye[2]=eyeZ; inkSpan=halfHeight?.let { it*2 }
        shader.begin(eyeX,eyeY,eyeZ,config.room==BilliardRoom.NEON,f,outdoor,rotationStripes,cartoon)
        Matrix.setIdentityM(model,0)
        sky?.let {
            Matrix.translateM(model,0,eyeX,eyeY,eyeZ)
            GL.glDepthMask(false); draw(it,14); GL.glDepthMask(true)
            Matrix.setIdentityM(model,0)
        }
        // The table and its balls first: the floor, rug and furniture hidden beneath them then
        // fail the depth test before their pixels are shaded.
        draw(base,2); draw(felt,1); draw(cushions,1); draw(pocketLinings,11); draw(metalwork,7); draw(markings,5)
        if(f!=null) {
            for(b in f.balls) if(b.motion!=Motion.POCKETED) {
                Matrix.setIdentityM(model,0)
                Matrix.translateM(model,0,(b.p.x-l/2).toFloat(),height+.0015f,(b.p.y-w/2).toFloat())
                val r=b.radius.toFloat(); val shadowR=r*(1.05f+((b.p.z-b.radius)*.8).toFloat())
                Matrix.scaleM(model,0,shadowR,1f,shadowR)
                GL.glDepthMask(false); draw(shadow,6); GL.glDepthMask(true)
                quaternion(model,b)
                draw(sphere,3,ballColor(b.id),number(b.id))
                if(cartoon) outline(b)
            }
        }
        Matrix.setIdentityM(model,0)
        lawn?.let { draw(it,13) }; garden?.let { draw(it,0) }; water?.let { draw(it,12) }
        draw(floor,4); draw(rug,9)
        draw(walls,if(config.room==BilliardRoom.LOFT) 10 else 0,cutaway=true)
        draw(room,0,cutaway=true); draw(furniture,0,cutaway=true)
        if(targetZone!=builtZone) {
            zoneMesh?.let { it.destroy(); meshes.remove(it) }; zoneMesh=null; builtZone=targetZone
            targetZone?.let { (center,radius) -> zoneMesh=mesh {
                for(i in 0 until 64) {
                    fun point(k: Int)=center+V3(cos(k*2*PI/64),sin(k*2*PI/64))*radius
                    ribbon(this,pos(point(i),height+.007f),pos(point(i+1),height+.007f),.007f,color(0xE4C488,.8f))
                }
            } }
        }
        zoneMesh?.let { draw(it,5) }
        if(eyeY<2.10f) draw(hanging,7)
        draw(luminaires,8,cutaway=true)
        if(f==null) return
        for(p in f.pins) {
            Matrix.setIdentityM(model,0); Matrix.translateM(model,0,(p.p.x-l/2).toFloat(),height+.001f,(p.p.y-w/2).toFloat())
            if(p.down) Matrix.rotateM(model,0,85f,0f,0f,1f)
            draw(pinMesh,0,if(p.id==0) 0xB84939 else 0xF5EACD)
        }
        val now=System.nanoTime()
        for(d in drops) {
            val t=((now-d.start)/1e9/.38).toFloat()
            if(t>=1f) { drops.remove(d); continue }
            // Keep the body's orientation as it drops: markings must not snap to identity.
            val ball=f.balls.firstOrNull { it.id==d.id }?.copyDeep() ?: table.ball(d.id,d.p.x,d.p.y)
            val progress=(1.0-(1.0-t).pow(3)).coerceIn(0.0,1.0)
            ball.p=(d.p+(d.target-d.p)*progress).copy(z=table.radius-.20*t*t)
            quaternion(model,ball)
            draw(sphere,3,ballColor(d.id),number(d.id),1f-((t-.65f)/.35f).coerceIn(0f,1f))
            if(cartoon) outline(ball,1f-((t-.65f)/.35f).coerceIn(0f,1f))
        }
        val t=trace
        if(t !== lastTrace) {
            guides?.let { it.destroy(); meshes.remove(it) }; guides=null; lastTrace=t
            if(t!=null && !f.moving) guides=mesh {
                for((id,path) in t.paths) {
                    val lengths=path.zipWithNext { a,b -> (b-a).length() }
                    val total=lengths.sum(); var travelled=0.0
                    for(i in 1 until path.size) {
                        travelled+=lengths[i-1]
                        val fade=((total-travelled)/.20).coerceIn(.15,1.0).toFloat()
                        ribbon(this,pos(path[i-1],height+.004f),pos(path[i],height+.004f),.002f,
                            color(if(id==f.cueId) 0xF5E8B7 else 0x89D2D5,.70f*fade))
                    }
                }
                fun ring(center: V3,radius: Double,tint: Int=0xF5E8B7) {
                    for(i in 0 until 48) {
                        fun point(k: Int)=center+V3(cos(k*2*PI/48),sin(k*2*PI/48))*radius
                        ribbon(this,pos(point(i),height+.005f),pos(point(i+1),height+.005f),.0013f,color(tint,.85f))
                    }
                }
                val firstBall=t.events.firstOrNull { it.kind==EventKind.BALL && (it.ball==f.cueId || it.other==f.cueId) }
                firstBall?.position?.let { hit ->
                    val impact=if(firstBall.ball==f.cueId) hit else t.paths[f.cueId]?.minByOrNull {
                        abs((it-hit).length()-table.radius*2)
                    }
                    if(impact!=null) ring(impact,table.radius)
                }
                t.events.filter { it.kind==EventKind.CUSHION }.distinctBy { it.ball }.take(2).forEach { event ->
                    event.position?.let { ring(it,.009,if(event.ball==f.cueId) 0xF5E8B7 else 0x89D2D5) }
                }
            }
        }
        Matrix.setIdentityM(model,0)
        if(!f.moving) { GL.glDepthMask(false); guides?.let { draw(it,5) }; GL.glDepthMask(true) }
        val strokeNow=stroke
        val strokeTime=if(strokeNow==null) 1f else ((now-strokeNow.start)/1e9/.18).toFloat()
        val cueBall=if(!showCue) null else if(strokeNow!=null && strokeTime<1) strokeNow.ball else if(!f.moving) f.balls.firstOrNull { it.id==f.cueId && it.motion!=Motion.POCKETED } else null
        val cueShot=if(strokeNow!=null && strokeTime<1) strokeNow.shot else f.shot
        cueBall?.let { b ->
            Matrix.setIdentityM(model,0)
            val gap=if(strokeTime<1) -.045*min(strokeTime*2,1f) else .035+cueShot.speed*.011+f.cuePullback
            // The tip slides along the cue's own axis: once raised, it still meets the struck point.
            val tip=CueReach.tip(b,cueShot,gap)
            Matrix.translateM(model,0,(tip.x-l/2).toFloat(),height+tip.z.toFloat(),(tip.y-w/2).toFloat())
            val dir=CueReach.direction(cueShot)
            val d=floatArrayOf(dir.x.toFloat(),dir.z.toFloat(),dir.y.toFloat())
            Matrix.rotateM(model,0,(acos(d[1].toDouble())*180/PI).toFloat(),d[2],0f,-d[0])
            draw(cueMesh,2,cueTint,opacity=if(strokeTime<1) 1-strokeTime else 1f)
        }
        renderedFrames++
    }
    private fun quaternion(out: FloatArray,b: Ball) {
        val x=-b.q[0].toFloat(); val y=-b.q[2].toFloat(); val z=-b.q[1].toFloat(); val a=b.q[3].toFloat(); val r=b.radius.toFloat()
        Matrix.setIdentityM(out,0)
        out[0]=(1-2*y*y-2*z*z)*r; out[1]=(2*x*y+2*a*z)*r; out[2]=(2*x*z-2*a*y)*r
        out[4]=(2*x*y-2*a*z)*r; out[5]=(1-2*x*x-2*z*z)*r; out[6]=(2*y*z+2*a*x)*r
        out[8]=(2*x*z+2*a*y)*r; out[9]=(2*y*z-2*a*x)*r; out[10]=(1-2*x*x-2*y*y)*r
        out[12]=(b.p.x-l/2).toFloat(); out[13]=height+b.p.z.toFloat(); out[14]=(b.p.y-w/2).toFloat()
    }
    /**
     * Cartoon ink: the ball drawn again a little larger, inside out and dark. The ball hides
     * its middle, so only a rim around the silhouette is left. Its width follows the distance,
     * so the line keeps about the same thickness on screen whatever the zoom.
     */
    private fun outline(b: Ball,opacity: Float=1f) {
        val r=b.radius.toFloat()
        val span=inkSpan ?: run {
            val dx=(b.p.x-l/2).toFloat()-inkEye[0]; val dy=height+b.p.z.toFloat()-inkEye[1]; val dz=(b.p.y-w/2).toFloat()-inkEye[2]
            // Height seen by the 46° lens at that distance.
            .849f*sqrt(dx*dx+dy*dy+dz*dz)
        }
        val grow=1f+(span*INK/r).coerceIn(.04f,.25f)
        Matrix.scaleM(model,0,grow,grow,grow)
        // Only the far half of the larger ball: its near half would cover the ball itself.
        GL.glEnable(GL.GL_CULL_FACE); GL.glCullFace(GL.GL_FRONT)
        draw(sphere,5,INK_COLOR,opacity=opacity)
        GL.glDisable(GL.GL_CULL_FACE)
    }
    private fun number(id: Int): Int = if(BilliardBallStyle.numbered(config.discipline)) id else -1
    private fun ballColor(id: Int): Int = BilliardBallStyle.color(config.discipline,id)
    private fun draw(mesh: ColoredMesh,type: Int,tint: Int=0xFFFFFF,id: Int=-1,opacity: Float=1f,cutaway: Boolean=false) {
        shader.material(type,tint,id,opacity,cutaway); mesh.draw(vp,model,shader.mvp,shader.model)
    }
    fun tablePoint(x: Float,y: Float): V3? {
        val inv=inverse; val near=FloatArray(4); val far=FloatArray(4)
        val sx=2*x/screenW-1; val sy=1-2*y/screenH
        Matrix.multiplyMV(near,0,inv,0,floatArrayOf(sx,sy,-1f,1f),0)
        Matrix.multiplyMV(far,0,inv,0,floatArrayOf(sx,sy,1f,1f),0)
        if(abs(near[3])<1e-9 || abs(far[3])<1e-9) return null
        for(i in 0..2) { near[i]/=near[3]; far[i]/=far[3] }
        val dy=far[1]-near[1]; if(abs(dy)<1e-8) return null
        val t=(height-near[1])/dy
        if(t<0 || !t.isFinite()) return null
        return V3((near[0]+(far[0]-near[0])*t+l/2).toDouble(),(near[2]+(far[2]-near[2])*t+w/2).toDouble(),table.radius)
    }
    fun screenPoint(p: V3,aboveCloth: Double=.045): Pair<Float,Float>? {
        val point=FloatArray(4)
        Matrix.multiplyMV(point,0,projected,0,floatArrayOf((p.x-l/2).toFloat(),height+aboveCloth.toFloat(),(p.y-w/2).toFloat(),1f),0)
        if(point[3]<=1e-6) return null
        return (point[0]/point[3]+1)*screenW/2 to (1-point[1]/point[3])*screenH/2
    }
    fun release() { meshes.forEach { it.destroy() }; meshes.clear(); shader.destroy() }
    companion object {
        /** Width of the cartoon outline, as a share of the screen height. */
        private const val INK=.0025f
        private const val INK_COLOR=0x141218
        private fun ribbon(b: MeshBuilder,a: Vec3,c: Vec3,width: Float,color: FloatArray) {
            val dx=c.x-a.x; val dz=c.z-a.z; val len=hypot(dx,dz); if(len<1e-8) return
            val nx=-dz/len*width; val nz=dx/len*width
            b.quad(Vec3(a.x+nx,a.y,a.z+nz),Vec3(a.x-nx,a.y,a.z-nz),Vec3(c.x-nx,c.y,c.z-nz),Vec3(c.x+nx,c.y,c.z+nz),color)
        }
    }
}
