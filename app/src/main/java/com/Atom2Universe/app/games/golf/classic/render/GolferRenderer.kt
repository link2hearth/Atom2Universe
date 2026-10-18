package com.Atom2Universe.app.games.golf.classic.render

import android.opengl.GLES20 as GL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * One cached VBO and one draw call for the whole golfer, animated by 17 rigid bone matrices.
 * A body takes about a tenth of a second to build, so the three stances (putter, iron, wood) are
 * built ahead on a background thread and kept: changing club never stalls a frame.
 */
internal class GolferRenderer {
    private class Mesh(val buffer: Int,val count: Int)
    val pose=GolferPose()
    private var program=0
    private var bonesLoc=0
    private var modelLoc=0
    private var projectionLoc=0
    private var eyeLoc=0
    private val meshes=HashMap<Triple<GolferAppearance,Boolean,Boolean>,Mesh>()
    private val requested=HashSet<Triple<GolferAppearance,Boolean,Boolean>>()
    private val built=ConcurrentLinkedQueue<Pair<Triple<GolferAppearance,Boolean,Boolean>,FloatArray>>()
    private var outfit: GolferAppearance?=null

    fun create() {
        // A surface recreation has a fresh GL context: all previous names are invalid.
        meshes.clear();requested.clear();outfit=null
        val vertex=shader(GL.GL_VERTEX_SHADER,VERTEX)
        val fragment=shader(GL.GL_FRAGMENT_SHADER,FRAGMENT)
        program=GL.glCreateProgram()
        GL.glAttachShader(program,vertex);GL.glAttachShader(program,fragment)
        arrayOf("aPosition","aNormal","aColour","aBone","aMaterial").forEachIndexed { i,name ->
            GL.glBindAttribLocation(program,i,name)
        }
        GL.glLinkProgram(program)
        GL.glDeleteShader(vertex);GL.glDeleteShader(fragment)
        val ok=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) { GL.glGetProgramInfoLog(program) }
        bonesLoc=GL.glGetUniformLocation(program,"uBones[0]")
        modelLoc=GL.glGetUniformLocation(program,"uModel")
        projectionLoc=GL.glGetUniformLocation(program,"uViewProjection")
        eyeLoc=GL.glGetUniformLocation(program,"uEye")
    }

    fun draw(look: GolferAppearance,putting: Boolean,wood: Boolean,model: FloatArray,
             viewProjection: FloatArray,eyeX: Float,eyeY: Float,eyeZ: Float) {
        while(true) {
            val (key,vertices)=built.poll()?:break
            if(key.first==look&&key !in meshes) meshes[key]=upload(vertices)
        }
        if(look!=outfit) {
            // A new outfit replaces the old bodies.
            meshes.values.forEach { GL.glDeleteBuffers(1,intArrayOf(it.buffer),0) }
            meshes.clear();requested.clear();outfit=look
        }
        if(meshes.size<3) for(stance in listOf(Triple(look,true,false),Triple(look,false,false),Triple(look,false,true)))
            if(stance !in meshes&&requested.add(stance))
                BackgroundBuilder.run { built+=stance to GolferGeometry.build(stance.first,stance.second,stance.third) }
        val next=Triple(look,putting,wood)
        // Only the very first frame of a hole may still have to build the stance it shows.
        val mesh=meshes.getOrPut(next) { upload(GolferGeometry.build(look,putting,wood)) }
        GL.glUseProgram(program)
        GL.glUniformMatrix4fv(bonesLoc,GolferPose.COUNT,false,pose.matrices,0)
        GL.glUniformMatrix4fv(modelLoc,1,false,model,0)
        GL.glUniformMatrix4fv(projectionLoc,1,false,viewProjection,0)
        GL.glUniform3f(eyeLoc,eyeX,eyeY,eyeZ)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,mesh.buffer)
        for(i in 0..4) GL.glEnableVertexAttribArray(i)
        val stride=GolferGeometry.STRIDE*4
        GL.glVertexAttribPointer(0,3,GL.GL_FLOAT,false,stride,0)
        GL.glVertexAttribPointer(1,3,GL.GL_FLOAT,false,stride,12)
        GL.glVertexAttribPointer(2,3,GL.GL_FLOAT,false,stride,24)
        GL.glVertexAttribPointer(3,1,GL.GL_FLOAT,false,stride,36)
        GL.glVertexAttribPointer(4,1,GL.GL_FLOAT,false,stride,40)
        GL.glDrawArrays(GL.GL_TRIANGLES,0,mesh.count)
        for(i in 2..4) GL.glDisableVertexAttribArray(i)
    }

    private fun upload(vertices: FloatArray): Mesh {
        val ids=IntArray(1);GL.glGenBuffers(1,ids,0)
        val bytes=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        bytes.put(vertices).position(0)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,ids[0])
        GL.glBufferData(GL.GL_ARRAY_BUFFER,vertices.size*4,bytes,GL.GL_STATIC_DRAW)
        return Mesh(ids[0],vertices.size/GolferGeometry.STRIDE)
    }

    fun release() {
        meshes.values.forEach { GL.glDeleteBuffers(1,intArrayOf(it.buffer),0) }
        if(program!=0) GL.glDeleteProgram(program)
        meshes.clear();requested.clear();outfit=null;program=0
    }

    private fun shader(type: Int,source: String): Int {
        val id=GL.glCreateShader(type)
        GL.glShaderSource(id,source);GL.glCompileShader(id)
        val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0)
        check(ok[0]!=0) { GL.glGetShaderInfoLog(id) }
        return id
    }

    companion object {
        // 17 matrices + 2 transforms fit the ES 2.0 minimum of 128 vertex uniform vectors.
        val VERTEX="""
            uniform mat4 uBones[17];
            uniform mat4 uModel;
            uniform mat4 uViewProjection;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColour;
            attribute float aBone;
            attribute float aMaterial;
            varying vec3 vNormal;
            varying vec3 vColour;
            varying vec3 vWorld;
            varying float vMaterial;
            void main() {
                mat4 bone=uBones[int(aBone)];
                vec4 position=bone*vec4(aPosition,1.0);
                vec3 normal=mat3(bone)*aNormal;
                // The tucked hem follows the pelvis; the upper shirt follows the ribcage.
                // Blend the waist so twisting cannot pull a rigid shirt through the belt.
                if(aBone>.5 && aBone<1.5 && aPosition.y<.12) {
                    float blend=smoothstep(-.06,.12,aPosition.y);
                    vec4 hipPosition=uBones[0]*vec4(aPosition+vec3(0.0,.095,0.0),1.0);
                    position=mix(hipPosition,position,blend);
                    normal=mix(mat3(uBones[0])*aNormal,normal,blend);
                }
                vec4 world=uModel*position;
                vWorld=world.xyz;
                vNormal=mat3(uModel)*normal;
                vColour=aColour;
                vMaterial=aMaterial;
                gl_Position=uViewProjection*world;
            }
        """.trimIndent()
        val FRAGMENT="""
            precision mediump float;
            uniform vec3 uEye;
            varying vec3 vNormal;
            varying vec3 vColour;
            varying vec3 vWorld;
            varying float vMaterial;
            void main() {
                vec3 n=normalize(vNormal);
                vec3 l=normalize(vec3(-.45,.85,.55));
                vec3 eye=normalize(uEye-vWorld);
                float sun=max(dot(n,l),0.0);
                float hemi=n.y*.5+.5;
                vec3 ambient=mix(vec3(.33,.38,.36),vec3(.66,.73,.78),hemi);
                vec3 colour=vColour*(ambient+vec3(.48,.43,.35)*sun);
                float spec=pow(max(dot(n,normalize(l+eye)),0.0),mix(24.0,64.0,vMaterial));
                colour+=vec3(1.0,.97,.88)*spec*(.025+vMaterial*.5);
                float rim=pow(1.0-max(dot(n,eye),0.0),3.0)*.10;
                colour+=vColour*rim;
                float fog=smoothstep(180.0,850.0,length((vWorld-uEye)*.01)*100.0)*.62;
                gl_FragColor=vec4(mix(colour,vec3(.70,.85,.86),fog),1.0);
            }
        """.trimIndent()
    }
}
