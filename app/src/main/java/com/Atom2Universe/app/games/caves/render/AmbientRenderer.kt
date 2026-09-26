package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.AmbientWildlife
import com.Atom2Universe.app.games.caves.entity.AmbientWildlife.Kind
import com.Atom2Universe.app.games.caves.world.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Closed voxel animals and weather, one reusable GPU batch per pass. */
internal class AmbientRenderer(private val world: World, private val grayscale: Boolean) {
    private val buffer = ByteBuffer.allocateDirect(maxOf(6000, AmbientWildlife.MAX_CREATURES * AmbientAnimalModels.maxParts * 36) * 7 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var shader: ShaderProgram? = null
    private var vbo = 0
    private var vao = 0
    private var mvp = 0
    private var originX = 0f; private var originY = 0f; private var originZ = 0f
    private var turnSin = 0f; private var turnCos = 1f
    private var light = 1f; private var alpha = 1f
    private val roofX = IntArray(196) { Int.MIN_VALUE }
    private val roofZ = IntArray(196)
    private val roofs = IntArray(196)
    private var roofCursor = 0

    fun onSurfaceCreated() {
        shader = ShaderProgram("""#version 300 es
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec4 aColor;
            uniform mat4 uMvp;
            out vec4 color;
            void main() { gl_Position=uMvp*vec4(aPosition,1.0); color=aColor; }
        """, """#version 300 es
            precision mediump float;
            in vec4 color;
            out vec4 fragColor;
            void main() { fragColor=color; }
        """).also { mvp = it.uniform("uMvp") }
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
        GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, buffer.capacity() * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 28, 0)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 28, 12)
        GLES30.glBindVertexArray(0); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun vertex(x: Float, y: Float, z: Float, color: Int) {
        var r = (color shr 16 and 255) / 255f
        var g = (color shr 8 and 255) / 255f
        var b = (color and 255) / 255f
        if (grayscale) { val gray = r * .2126f + g * .7152f + b * .0722f; r = gray; g = gray; b = gray }
        buffer.put(originX + x * turnCos + z * turnSin).put(originY + y)
            .put(originZ - x * turnSin + z * turnCos)
            .put(r * light * faceShade).put(g * light * faceShade).put(b * light * faceShade).put(alpha)
    }

    private fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
                    cx: Float, cy: Float, cz: Float, color: Int) {
        vertex(ax, ay, az, color); vertex(bx, by, bz, color); vertex(cx, cy, cz, color)
    }

    private fun body(w: Float, h: Float, d: Float, color: Int) {
        tri(0f,h,0f, -w,0f,0f, 0f,0f,d,color)
        tri(0f,h,0f, 0f,0f,d, w,0f,0f,color)
        tri(0f,h,0f, w,0f,0f, 0f,0f,-d,color)
        tri(0f,h,0f, 0f,0f,-d, -w,0f,0f,color)
        tri(0f,-h,0f, 0f,0f,d, -w,0f,0f,color)
        tri(0f,-h,0f, w,0f,0f, 0f,0f,d,color)
        tri(0f,-h,0f, 0f,0f,-d, w,0f,0f,color)
        tri(0f,-h,0f, -w,0f,0f, 0f,0f,-d,color)
    }

    private val corners = FloatArray(24)
    private val faces = arrayOf(intArrayOf(0,3,2,1), intArrayOf(4,5,6,7),
        intArrayOf(0,4,7,3), intArrayOf(1,2,6,5), intArrayOf(3,7,6,2), intArrayOf(0,1,5,4))
    private val triangleOrder = intArrayOf(0,1,2,0,2,3)
    private var faceShade = 1f

    /** Closed six-face part. Reuse eight corners and bake directional shading after animation. */
    private fun partBox(p: MobPart, roll: Float, tail: Float) {
        val cr=cos(roll); val sr=sin(roll); val ct=cos(tail); val st=sin(tail)
        for (i in 0..7) {
            val x=p.cx + p.w*.5f*(if(i==1 || i==2 || i==5 || i==6) 1f else -1f)
            val y=p.cy + p.h*.5f*(if(i==2 || i==3 || i==6 || i==7) 1f else -1f)
            val z=p.cz + p.d*.5f*(if(i>=4) 1f else -1f)
            val dx=x-p.pivotX; val dy=y-p.pivotY; val dz=z-p.pivotZ
            val rx=dx*cr-dy*sr
            corners[i*3]=p.pivotX+rx*ct+dz*st
            corners[i*3+1]=p.pivotY+dx*sr+dy*cr
            corners[i*3+2]=p.pivotZ-rx*st+dz*ct
        }
        for (face in faces) {
            val a=face[0]*3; val b=face[1]*3; val c=face[2]*3
            val ux=corners[b]-corners[a]; val uy=corners[b+1]-corners[a+1]; val uz=corners[b+2]-corners[a+2]
            val vx=corners[c]-corners[a]; val vy=corners[c+1]-corners[a+1]; val vz=corners[c+2]-corners[a+2]
            val nx=uy*vz-uz*vy; val ny=uz*vx-ux*vz; val nz=ux*vy-uy*vx
            val length=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.0000001f)
            val worldNx=nx*turnCos+nz*turnSin; val worldNz=-nx*turnSin+nz*turnCos
            faceShade=if(p.emissive) 1f else .58f+.42f*max(0f,(-.36f*worldNx+.84f*ny+.40f*worldNz)/length)
            for (index in triangleOrder) {
                val v=face[index]*3
                vertex(corners[v],corners[v+1],corners[v+2],p.color)
            }
        }
        faceShade=1f
    }

    fun wildlife(camera: Camera, wildlife: AmbientWildlife, ambient: Float) {
        buffer.clear()
        val blend = wildlife.renderFraction
        for (a in wildlife.creatures) {
            val opacity = a.previousAlpha + (a.alpha-a.previousAlpha)*blend
            if (opacity <= .01f) continue
            // Interpolate in world-space doubles before subtracting the moving camera origin.
            originX = (a.previousX+(a.x-a.previousX)*blend-camera.x).toFloat()
            originY = (a.previousY+(a.y-a.previousY)*blend-camera.y).toFloat()
            originZ = (a.previousZ+(a.z-a.previousZ)*blend-camera.z).toFloat()
            val turn = atan2(sin(a.yaw-a.previousYaw), cos(a.yaw-a.previousYaw))
            val yaw = a.previousYaw + turn*blend
            turnSin = sin(yaw); turnCos = cos(yaw)
            val distance = sqrt(originX*originX + originY*originY + originZ*originZ)
            alpha = opacity * ((AmbientWildlife.VISIBLE_RADIUS-distance)/10f).coerceIn(0f,1f)
            if (alpha <= .01f) continue
            // Animation follows render time, independently of the 20 Hz collision simulation.
            val t = wildlife.animationTime + a.phase
            val flightFlap = when(a.kind) {
                Kind.BIRD -> if(sin(t*.9f)<-.25f) .12f else .10f+sin(t*7f)*.48f
                Kind.BUTTERFLY -> .45f+sin(t*15f)*.85f
                Kind.BEE -> .3f+sin(t*32f)*.55f
                Kind.FISH -> sin(t*6f)*.22f
                Kind.DRAGONFLY -> .12f+sin(t*34f)*.28f
                Kind.FIREFLY -> .2f+sin(t*30f)*.4f
            }
            val feeding = a.previousActivityBlend+(a.activityBlend-a.previousActivityBlend)*blend
            // Butterflies fold their wings above the bloom; bees keep hovering while collecting nectar.
            val flap = if(a.kind == Kind.BUTTERFLY) flightFlap*(1f-feeding)+(1.1f+sin(t*2f)*.08f)*feeding
                else flightFlap
            for (part in AmbientAnimalModels.get(a.kind,a.coat)) {
                light = if(part.emissive) .65f+.35f*sin(t*2.8f) else ambient.coerceAtLeast(.14f)
                val roll=if(part.limb==Limb.WING) part.side*flap else 0f
                val tail=if(part.limb==Limb.TAIL) sin(t*8f)*(if(a.kind==Kind.FISH) .4f else .04f) else 0f
                partBox(part,roll,tail)
            }
        }
        flush(camera, true)
    }
    fun clouds(camera: Camera, weather: AmbientWeather, seconds: Double, daylight: Float, cave: Float) {
        if (weather.cloud < .01f || cave > .99f) return
        buffer.clear(); light = daylight.coerceAtLeast(.12f); turnSin = 0f; turnCos = 1f
        val drift = seconds * 1.4
        val tileX = floor((camera.x-drift)/70).toInt(); val tileZ = floor(camera.z/70).toInt()
        for (z in tileZ-4..tileZ+4) for (x in tileX-4..tileX+4) {
            val hash = (x*73428767 xor z*912931) and 255
            if (hash / 255f > .22f + weather.cloud*.75f) continue
            originX = (x*70.0+drift-camera.x).toFloat(); originZ = (z*70.0-camera.z).toFloat()
            originY = 95f + hash%23
            alpha = (.25f+weather.cloud*.48f)*(1f-cave)
            // Low polygon banks with smaller lobes soften the square skyline.
            for (lobe in 0..2) {
                val w=24f + (hash+lobe*13)%19; val d=17f + (hash+lobe*7)%13
                val ox=lobe*17f-17f; val oz=(lobe%2)*13f
                tri(ox-w,0f,oz-d,ox+w,0f,oz-d,ox+w,0f,oz+d,0xBAC5CF)
                tri(ox-w,0f,oz-d,ox+w,0f,oz+d,ox-w,0f,oz+d,0xBAC5CF)
            }
        }
        flush(camera, false)
    }

    fun precipitation(camera: Camera, weather: AmbientWeather, seconds: Double, ambient: Float, underwater: Boolean) {
        if (weather.precipitation < .015f || underwater) return
        buffer.clear(); light = ambient.coerceAtLeast(.16f); turnSin = 0f; turnCos = 1f
        val cx=floor(camera.x/2).toInt(); val cz=floor(camera.z/2).toInt()
        val snow=weather.snow
        // Recheck a bounded number of columns each frame, including roofs built by the player.
        roofCursor=(roofCursor+12)%196
        var roofBudget = 24
        for (i in 0 until 196) {
            val gx=cx+i%14-7; val gz=cz+i/14-7
            val x=gx*2; val z=gz*2
            // World-aligned ring slots survive movement: only newly entered columns need work.
            val slot=Math.floorMod(gx,14)+Math.floorMod(gz,14)*14
            if (roofBudget > 0 && (roofX[slot]!=x || roofZ[slot]!=z || (slot-roofCursor+196)%196<12)) {
                roofX[slot]=x; roofZ[slot]=z; roofs[slot]=weatherRoof(world,x,z,camera.y)
                roofBudget--
            }
            if (roofX[slot]!=x || roofZ[slot]!=z) continue
            val hash=(x*73428767 xor z*912931) and 1023
            val density = weather.precipitation * (.52f + .48f*snow)
            val presence = ((density-hash/1023f)*18f).coerceIn(0f,1f)
            if (presence <= 0f) continue
            val speed=(9.0+hash%7*.25)*(1-snow)+1.7*snow
            val fall=seconds*speed+hash*.13
            val y=camera.y+12.0-((fall%19.0+19.0)%19.0)
            if (y < roofs[slot]+.15) continue
            originX=(x+.28+(hash%17)/40.0-camera.x).toFloat(); originY=(y-camera.y).toFloat()
            originZ=(z+.28+(hash%23)/55.0-camera.z).toFloat()
            // Sway stays within the sampled column, so particles cannot drift through its roof.
            originX += (sin(seconds*1.3+hash)*.22*snow).toFloat()
            val edge=(1f-max(abs(originX),abs(originZ))/15f).coerceIn(0f,1f)
            val nearby = ((hypot(originX,originZ)-1f)/2f).coerceIn(0f,1f)
            alpha=(.14f+snow*.5f)*edge*presence*nearby
            if (snow>.5f) {
                body(.045f,.055f,.045f,0xF2F7FF)
            } else {
                val yaw=Math.toRadians(camera.yaw.toDouble())
                turnSin=sin(yaw).toFloat(); turnCos=cos(yaw).toFloat()
                val lean=weather.wind*.045f
                val length=.20f+(hash%11)*.009f
                tri(-.007f,0f,0f,.007f,0f,0f,lean+.007f,length,0f,0xAEC0C5)
                tri(-.007f,0f,0f,lean+.007f,length,0f,lean-.007f,length,0f,0xAEC0C5)
            }
        }
        flush(camera, false)
    }

    private fun flush(camera: Camera, depthWrite: Boolean) {
        if (buffer.position()==0) return
        val s=shader ?: return
        val floats=buffer.position(); buffer.flip()
        s.use(); GLES30.glUniformMatrix4fv(mvp,1,false,camera.vpMatrix,0)
        GLES30.glBindVertexArray(vao); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER,0,floats*4,buffer)
        val culling=GLES30.glIsEnabled(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(depthWrite)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,floats/7)
        GLES30.glDepthMask(true); GLES30.glDisable(GLES30.GL_BLEND)
        if (culling) GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glBindVertexArray(0); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0)
    }

    fun destroy() {
        shader?.destroy(); shader=null
        GLES30.glDeleteBuffers(1,intArrayOf(vbo),0); vbo=0
        GLES30.glDeleteVertexArrays(1,intArrayOf(vao),0); vao=0
    }
}
