package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.AttackShape
import com.Atom2Universe.app.games.caves.entity.Enemy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Depth-tested world warnings, attack trails and contact flashes, batched in one draw call. */
internal class CombatEffectsRenderer {
    private var shader: ShaderProgram? = null
    private var vbo=0; private var position=0; private var color=0; private var matrix=0
    private val vertices=FloatArray(240_000)
    private val buffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var count=0
    private var red=1f; private var green=.55f; private var blue=.12f; private var alpha=.4f
    private var cx=0.0; private var cy=0.0; private var cz=0.0
    private class Impact(val x: Double,val y: Double,val z: Double,val heavy: Boolean) { var life=.24f }
    private val impacts=ArrayList<Impact>(24)

    fun impact(x: Double,y: Double,z: Double,heavy: Boolean) {
        if(impacts.size>=24) impacts.removeAt(0)
        impacts.add(Impact(x,y,z,heavy))
    }
    fun update(dt: Float) {
        val it=impacts.iterator()
        while(it.hasNext()) { val p=it.next();p.life-=dt;if(p.life<=0f) it.remove() }
    }
    fun onSurfaceCreated() {
        shader=ShaderProgram("""
            #version 300 es
            in vec3 a_pos;
            in vec4 a_color;
            uniform mat4 u_mvp;
            out vec4 color;
            void main() { color=a_color;gl_Position=u_mvp*vec4(a_pos,1.0); }
        """.trimIndent(),"""
            #version 300 es
            precision mediump float;
            in vec4 color;
            out vec4 fragColor;
            void main() { fragColor=color; }
        """.trimIndent()).also {
            position=it.attrib("a_pos");color=it.attrib("a_color");matrix=it.uniform("u_mvp")
        }
        val ids=IntArray(1);GLES30.glGenBuffers(1,ids,0);vbo=ids[0]
    }
    fun render(enemies: List<Enemy>,camera: Camera) {
        val effectShader=shader ?: return
        count=0;cx=camera.x;cy=camera.y;cz=camera.z
        for(e in enemies) {
            if(count+7000>vertices.size) break
            val a=e.attack ?: continue
            if(e.hp<=0 || e.occluded || e.freezeTimer>0 || e.staggerTimer>0 || e.confusionTimer>0) continue
            if(e.attackWindup<=0f && a.flash<=0f) continue
            if((a.x-cx).pow(2)+(a.z-cz).pow(2)>48*48) continue
            val firing=e.attackWindup<=0f
            val progress=if(firing) 1.0 else (1-e.attackWindup/a.windup).coerceIn(0f,1f).toDouble()
            red=1f;green=if(firing) .85f else .52f;blue=if(firing) .4f else .08f
            alpha=if(firing) a.flash/.3f*.65f else .24f+progress.toFloat()*.25f
            val y=a.y+.055
            when(a.shape) {
                AttackShape.BEAM,AttackShape.ARROW,AttackShape.VENOM -> {
                    val beam=a.shape==AttackShape.BEAM
                    if(beam) { red=.65f;green=.35f;blue=1f }
                    if(a.shape==AttackShape.VENOM) { red=.45f;green=1f;blue=.12f }
                    val width=if(firing && beam) a.width else .018+progress*.025
                    line(a.x,a.y,a.z,a.targetX,a.targetY,a.targetZ,width)
                    // Energy gathers at the source; a shrinking ring announces the release.
                    ring(a.x,a.y,a.z,.16+(1-progress)*.35,camera.yaw,.025)
                    if(firing && beam) {
                        red=1f;green=.95f;blue=1f
                        line(a.x,a.y,a.z,a.targetX,a.targetY,a.targetZ,.055)
                    }
                }
                AttackShape.LUNGE -> {
                    val fx=sin(a.yaw);val fz=cos(a.yaw);val rx=cos(a.yaw)*a.width;val rz=-sin(a.yaw)*a.width
                    val ex=a.x+fx*a.range;val ez=a.z+fz*a.range
                    line(a.x+rx,y,a.z+rz,ex+rx,y,ez+rz,.025)
                    line(a.x-rx,y,a.z-rz,ex-rx,y,ez-rz,.025)
                    line(ex-rx,y,ez-rz,ex+rx,y,ez+rz,.025)
                    val marker=a.range*progress
                    line(a.x+fx*marker-rx,y,a.z+fz*marker-rz,a.x+fx*marker+rx,y,a.z+fz*marker+rz,.045)
                }
                AttackShape.SLAM,AttackShape.SWEEP -> {
                    val angle=if(a.shape==AttackShape.SLAM) PI else a.halfAngle
                    for(i in 0 until 32) {
                        val t0=a.yaw-angle+2*angle*i/32
                        val t1=a.yaw-angle+2*angle*(i+1)/32
                        val x0=a.x+sin(t0)*a.range;val z0=a.z+cos(t0)*a.range
                        val x1=a.x+sin(t1)*a.range;val z1=a.z+cos(t1)*a.range
                        line(x0,y,z0,x1,y,z1,.026)
                        val radius=a.range*(if(firing) 1-a.flash/.3 else progress)
                        line(a.x+sin(t0)*radius,y+.025,a.z+cos(t0)*radius,
                            a.x+sin(t1)*radius,y+.025,a.z+cos(t1)*radius,.04)
                        val oldAlpha=alpha;alpha*=.22f
                        vertex(a.x,y,a.z);vertex(x0,y,z0);vertex(x1,y,z1)
                        alpha=oldAlpha
                    }
                    if(a.shape==AttackShape.SWEEP) {
                        for(side in intArrayOf(-1,1)) line(a.x,y,a.z,a.x+sin(a.yaw+angle*side)*a.range,y,
                            a.z+cos(a.yaw+angle*side)*a.range,.025)
                    }
                }
            }
        }
        for(p in impacts) {
            red=1f;green=if(p.heavy) .7f else .96f;blue=if(p.heavy) .18f else .8f;alpha=p.life/.24f
            val radius=(1-p.life/.24f)*(if(p.heavy) .85 else .45)+.08
            ring(p.x,p.y,p.z,radius,camera.yaw,.025)
            val yaw=Math.toRadians(camera.yaw.toDouble());val rx=cos(yaw);val rz=-sin(yaw)
            line(p.x-rx*radius,p.y-radius,p.z-rz*radius,p.x+rx*radius,p.y+radius,p.z+rz*radius,.04)
            line(p.x-rx*radius,p.y+radius,p.z-rz*radius,p.x+rx*radius,p.y-radius,p.z+rz*radius,.025)
        }
        if(count==0) return
        effectShader.use()
        buffer.clear();buffer.put(vertices,0,count);buffer.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,count*4,buffer,GLES30.GL_DYNAMIC_DRAW)
        GLES30.glUniformMatrix4fv(matrix,1,false,camera.vpMatrix,0)
        GLES30.glEnable(GLES30.GL_BLEND);GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA)
        // The world normally renders both sides. Enabling culling unconditionally here made
        // block faces disappear from the frame following the first visible attack effect.
        val wasCulling=GLES30.glIsEnabled(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glDepthMask(false)
        GLES30.glEnableVertexAttribArray(position);GLES30.glVertexAttribPointer(position,3,GLES30.GL_FLOAT,false,28,0)
        GLES30.glEnableVertexAttribArray(color);GLES30.glVertexAttribPointer(color,4,GLES30.GL_FLOAT,false,28,12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,count/7)
        GLES30.glDisableVertexAttribArray(position);GLES30.glDisableVertexAttribArray(color)
        GLES30.glDepthMask(true)
        if(wasCulling) GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0)
    }
    private fun vertex(x: Double,y: Double,z: Double) {
        vertices[count++]=(x-cx).toFloat();vertices[count++]=(y-cy).toFloat();vertices[count++]=(z-cz).toFloat()
        vertices[count++]=red;vertices[count++]=green;vertices[count++]=blue;vertices[count++]=alpha
    }
    private fun line(ax: Double,ay: Double,az: Double,bx: Double,by: Double,bz: Double,width: Double) {
        if(count+84>vertices.size) return
        val dx=bx-ax;val dz=bz-az;val length=hypot(dx,dz)
        val rx=if(length>.001) -dz/length*width else width
        val rz=if(length>.001) dx/length*width else 0.0
        vertex(ax-rx,ay,az-rz);vertex(ax+rx,ay,az+rz);vertex(bx+rx,by,bz+rz)
        vertex(ax-rx,ay,az-rz);vertex(bx+rx,by,bz+rz);vertex(bx-rx,by,bz-rz)
        vertex(ax,ay-width,az);vertex(ax,ay+width,az);vertex(bx,by+width,bz)
        vertex(ax,ay-width,az);vertex(bx,by+width,bz);vertex(bx,by-width,bz)
    }
    private fun ring(x: Double,y: Double,z: Double,radius: Double,yaw: Float,width: Double) {
        val rx=cos(Math.toRadians(yaw.toDouble()));val rz=-sin(Math.toRadians(yaw.toDouble()))
        for(i in 0 until 20) {
            val a=i*PI/10;val b=(i+1)*PI/10
            line(x+rx*cos(a)*radius,y+sin(a)*radius,z+rz*cos(a)*radius,
                x+rx*cos(b)*radius,y+sin(b)*radius,z+rz*cos(b)*radius,width)
        }
    }
    fun destroy() { shader?.destroy();GLES30.glDeleteBuffers(1,intArrayOf(vbo),0) }
}
