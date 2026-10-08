package com.Atom2Universe.app.games.golf.classic.render

import android.opengl.GLES20 as GL
import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlin.random.Random

/**
 * Geometry that takes several milliseconds to build (grass tiles, the golfer's bodies) is made
 * here, off the drawing thread, which then only uploads it. One low-priority thread shared by
 * every hole, which stops after a few idle seconds.
 */
internal object BackgroundBuilder {
    private val executor=ThreadPoolExecutor(0,1,3L,TimeUnit.SECONDS,LinkedBlockingQueue()) { task ->
        Thread(task,"classic-builder").apply { isDaemon=true; priority=Thread.MIN_PRIORITY }
    }
    fun run(task: () -> Unit) = executor.execute(task)
}

/** Deterministic, bounded tiles. Only grass near the eye becomes geometry; the rest is shading. */
internal object GrassGeometry {
    const val TILE=8f
    const val STRIDE=10
    const val MAX_TRIANGLES=648
    const val MAX_VISIBLE_TILES=49
    const val RANGE=25f

    fun build(hole:ClassicHole,tx:Int,tz:Int):FloatArray {
        val random=Random(hole.number*9187 xor (tx*73856093) xor (tz*19349663))
        val data=FloatArray(MAX_TRIANGLES*3*STRIDE)
        var size=0
        fun vertex(x:Float,y:Float,z:Float,c:C,root:P,flex:Float) {
            data[size++]=x;data[size++]=y;data[size++]=z
            data[size++]=c.r;data[size++]=c.g;data[size++]=c.b
            data[size++]=root.x;data[size++]=root.y;data[size++]=root.z;data[size++]=flex
        }
        fun blade(root:P,height:Float,width:Float,angle:Float,tint:Float) {
            val dx=cos(angle)*width;val dz=sin(angle)*width
            val bendX=sin(angle)*height*.27f;val bendZ=-cos(angle)*height*.27f
            val base=C(.22f,.35f,.12f).shade(tint);val tip=C(.49f,.59f,.25f).shade(tint)
            fun p(i:Int) {
                when(i) {
                    0->vertex(root.x,root.y,root.z,base,root,0f)
                    1->vertex(root.x+bendX*.15f+dx,root.y+height*.36f,root.z+bendZ*.15f+dz,base.mix(tip,.42f),root,.36f)
                    2->vertex(root.x+bendX*.15f-dx,root.y+height*.36f,root.z+bendZ*.15f-dz,base.mix(tip,.42f),root,.36f)
                    else->vertex(root.x+bendX,root.y+height,root.z+bendZ,tip,root,1f)
                }
            }
            // Two triangles per leaf leave room for fuller clumps at the same tile budget.
            p(0);p(1);p(2);p(2);p(1);p(3)
        }
        val cx=(tx+.5f)*TILE;val cz=(tz+.5f)*TILE
        val trees=hole.trees.filter{hypot(it.x-cx,it.z-cz)<8f}
        // A permutation spreads the budget across the whole tile, even if all of it is rough.
        for(index in 0..255) {
            val cell=(index*73+31)and 255
            val x=(tx+(cell%16+.15f+random.nextFloat()*.7f)/16f)*TILE
            val z=(tz+(cell/16+.15f+random.nextFloat()*.7f)/16f)*TILE
            val lie=hole.lieAt(x,z)
            if(lie!=GolfLie.ROUGH&&lie!=GolfLie.SEMI_ROUGH&&lie!=GolfLie.FAIRWAY&&lie!=GolfLie.TEE)continue
            if(hole.greenSignedDistance(x,z)<.85f || abs(x-hole.pathX(z))<1.65f)continue
            if(hole.hazards.any{it.signedDistance(x,z)<.40f})continue
            if(trees.any{hypot(x-it.x,z-it.z)<.50f})continue
            val rough=lie==GolfLie.ROUGH
            val semi=lie==GolfLie.SEMI_ROUGH
            val patches=.50f+.25f*sin(x*.67f+sin(z*.31f))+.18f*cos(z*.83f-x*.21f)
            if(random.nextFloat()>(if(rough) patches.coerceIn(.14f,.88f) else .32f))continue
            val blades=if(rough) { if(index%3==0)5 else 4 } else if(semi) 2 else 1
            if(size+blades*6*STRIDE>data.size)continue
            val height=if(rough) .16f+random.nextFloat()*.25f+patches*.10f else if(semi) .065f+random.nextFloat()*.045f else .028f+random.nextFloat()*.028f
            val y=hole.heightAt(x,z)+if(rough)-.025f else .008f
            val angle=random.nextFloat()*PI.toFloat()*2f
            val tint=.80f+random.nextFloat()*.22f
            repeat(blades) { i ->
                val a=angle+i*2.4f
                blade(P(x+cos(a)*.05f,y,z+sin(a)*.05f),height*(.7f+random.nextFloat()*.3f),
                    if(rough) .022f+random.nextFloat()*.016f else .008f,a,tint)
            }
        }
        return data.copyOf(size)
    }
}

/**
 * GLES 2 streaming cache: 64 resident tiles, 31,752 triangles. Tiles are built on a background
 * thread (one costs several milliseconds, enough to miss a frame when the camera reaches a new
 * spot) and only uploaded here, a few per frame.
 */
internal class ClassicGrass(private val hole:ClassicHole) {
    private data class Tile(val key:Long,val x:Int,val z:Int,val count:Int,val buffer:Int,val born:Float)
    private class Built(val key:Long,val x:Int,val z:Int,val vertices:FloatArray)
    private val tiles=LinkedHashMap<Long,Tile>(64,.75f,true)
    private val built=ConcurrentLinkedQueue<Built>()
    /** Tiles handed to the builder and not uploaded yet (drawing thread only). */
    private val building=HashSet<Long>()
    /** Ground height at each tile's centre: the visibility test runs on ~49 tiles every frame. */
    private val centreHeights=HashMap<Long,Float>()
    private var program=0
    private var vpLoc=0;private var eyeLoc=0;private var ballLoc=0;private var timeLoc=0;private var growLoc=0
    private fun key(x:Int,z:Int)=(x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)

    fun create() {
        tiles.clear() // context loss invalidates the old GPU names
        fun shader(type:Int,source:String):Int {
            val id=GL.glCreateShader(type);GL.glShaderSource(id,source);GL.glCompileShader(id)
            val status=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,status,0)
            check(status[0]!=0){GL.glGetShaderInfoLog(id)};return id
        }
        val vertex=shader(GL.GL_VERTEX_SHADER,VERTEX);val fragment=shader(GL.GL_FRAGMENT_SHADER,FRAGMENT)
        program=GL.glCreateProgram();GL.glAttachShader(program,vertex);GL.glAttachShader(program,fragment)
        arrayOf("aPosition","aColour","aRoot","aFlex").forEachIndexed{i,s->GL.glBindAttribLocation(program,i,s)}
        GL.glLinkProgram(program);GL.glDeleteShader(vertex);GL.glDeleteShader(fragment)
        val status=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,status,0)
        check(status[0]!=0){GL.glGetProgramInfoLog(program)}
        vpLoc=GL.glGetUniformLocation(program,"uVp");eyeLoc=GL.glGetUniformLocation(program,"uEye")
        ballLoc=GL.glGetUniformLocation(program,"uBall");timeLoc=GL.glGetUniformLocation(program,"uTime")
        growLoc=GL.glGetUniformLocation(program,"uGrow")
    }

    fun draw(vp:FloatArray,eyeX:Float,eyeY:Float,eyeZ:Float,ballX:Float,ballZ:Float,time:Float) {
        if(eyeY-hole.heightAt(eyeX,eyeZ)>24f)return
        val tx=floor(eyeX/GrassGeometry.TILE).toInt();val tz=floor(eyeZ/GrassGeometry.TILE).toInt()
        val wanted=ArrayList<Pair<Int,Int>>(49)
        for(z in tz-3..tz+3)for(x in tx-3..tx+3) {
            val cx=(x+.5f)*GrassGeometry.TILE;val cz=(z+.5f)*GrassGeometry.TILE
            if(hypot(cx-eyeX,cz-eyeZ)>GrassGeometry.RANGE+5.7f)continue
            // Very cheap homogeneous clip test of a conservative tile sphere, before generation.
            val cy=centreHeights.getOrPut(key(x,z)){hole.heightAt(cx,cz)}
            var visible=true
            for(axis in 0..2)for(side in 0..1) {
                val s=if(side==0)-1f else 1f
                val a=vp[3]+s*vp[axis];val b=vp[7]+s*vp[axis+4];val c=vp[11]+s*vp[axis+8]
                if(a*cx+b*cy+c*cz+vp[15]+s*vp[axis+12]<-9f*sqrt(a*a+b*b+c*c))visible=false
            }
            if(visible)wanted+=x to z
        }
        wanted.sortBy{(it.first+.5f-tx).pow(2)+(it.second+.5f-tz).pow(2)}
        var uploads=0
        while(uploads<4) {
            val ready=built.poll()?:break
            building.remove(ready.key)
            if(tiles.containsKey(ready.key))continue
            val ids=IntArray(1)
            if(ready.vertices.isNotEmpty()) {
                GL.glGenBuffers(1,ids,0);GL.glBindBuffer(GL.GL_ARRAY_BUFFER,ids[0])
                val bytes=ByteBuffer.allocateDirect(ready.vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                bytes.put(ready.vertices).position(0)
                GL.glBufferData(GL.GL_ARRAY_BUFFER,ready.vertices.size*4,bytes,GL.GL_STATIC_DRAW)
            }
            tiles[ready.key]=Tile(ready.key,ready.x,ready.z,ready.vertices.size/GrassGeometry.STRIDE,ids[0],time)
            uploads++
        }
        GL.glUseProgram(program)
        GL.glUniformMatrix4fv(vpLoc,1,false,vp,0);GL.glUniform3f(eyeLoc,eyeX,eyeY,eyeZ)
        GL.glUniform2f(ballLoc,ballX,ballZ);GL.glUniform1f(timeLoc,time)
        for((x,z)in wanted) {
            val key=key(x,z)
            val tile=tiles[key]
            if(tile==null) {
                // Nearest first, a few at a time, so that a fast pan does not queue a whole field.
                if(key !in building&&building.size<4) {
                    building+=key
                    BackgroundBuilder.run { built+=Built(key,x,z,GrassGeometry.build(hole,x,z)) }
                }
                continue
            }
            if(tile.count==0)continue
            GL.glUniform1f(growLoc,((time-tile.born)/.35f).coerceIn(0f,1f))
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER,tile.buffer)
            for(i in 0..3)GL.glEnableVertexAttribArray(i)
            GL.glVertexAttribPointer(0,3,GL.GL_FLOAT,false,40,0)
            GL.glVertexAttribPointer(1,3,GL.GL_FLOAT,false,40,12)
            GL.glVertexAttribPointer(2,3,GL.GL_FLOAT,false,40,24)
            GL.glVertexAttribPointer(3,1,GL.GL_FLOAT,false,40,36)
            GL.glDrawArrays(GL.GL_TRIANGLES,0,tile.count)
        }
        while(tiles.size>64) {
            val oldest=tiles.entries.iterator().next()
            if(oldest.value.buffer!=0)GL.glDeleteBuffers(1,intArrayOf(oldest.value.buffer),0)
            tiles.remove(oldest.key)
        }
        GL.glDisableVertexAttribArray(2);GL.glDisableVertexAttribArray(3)
    }

    fun release() {
        tiles.values.forEach{if(it.buffer!=0)GL.glDeleteBuffers(1,intArrayOf(it.buffer),0)}
        tiles.clear();if(program!=0)GL.glDeleteProgram(program);program=0
    }

    companion object {
        const val VERTEX="""
uniform mat4 uVp;
uniform vec3 uEye;
uniform vec2 uBall;
uniform float uTime;
uniform float uGrow;
attribute vec3 aPosition;
attribute vec3 aColour;
attribute vec3 aRoot;
attribute float aFlex;
varying vec3 vColour;
void main() {
    float distanceFade=1.0-smoothstep(16.0,25.0,length(aRoot.xz-uEye.xz));
    float ballClear=smoothstep(.19,.48,length(aRoot.xz-uBall));
    float scale=distanceFade*ballClear*uGrow;
    vec3 p=aRoot+(aPosition-aRoot)*scale;
    float wind=sin(uTime*1.65+aRoot.x*.45+aRoot.z*.28)+.35*sin(uTime*2.8+aRoot.z*.8);
    float height=max(0.0,aPosition.y-aRoot.y);
    p.xz+=vec2(.8,.4)*wind*height*.18*aFlex*aFlex*scale;
    vColour=aColour*(.94+.06*sin(aRoot.x*.7+aRoot.z*.5));
    gl_Position=uVp*vec4(p,1.0);
}
"""
        const val FRAGMENT="""
precision mediump float;
varying vec3 vColour;
void main() { gl_FragColor=vec4(vColour,1.0); }
"""
    }
}
