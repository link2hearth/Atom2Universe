package com.Atom2Universe.app.games.golf.classic.render

import android.opengl.GLES20 as GL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** A single interleaved position / lit colour VBO. No image assets or per-object draw calls. */
internal class ClassicMesh(private val vertices: FloatArray) {
    private var buffer = 0
    val count get() = vertices.size / 6
    private val bounds by lazy {
        val b=floatArrayOf(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE)
        for(i in vertices.indices step 6) for(axis in 0..2) {
            b[axis]=min(b[axis],vertices[i+axis]);b[axis+3]=max(b[axis+3],vertices[i+axis])
        }
        b
    }
    /** Conservative world-space AABB/frustum test for immutable scenery batches. */
    fun visible(vp:FloatArray):Boolean {
        if(count==0)return false
        val b=bounds
        for(axis in 0..2) for(side in 0..1) {
            val sign=if(side==0)-1f else 1f
            val x=vp[3]+sign*vp[axis];val y=vp[7]+sign*vp[axis+4]
            val z=vp[11]+sign*vp[axis+8];val w=vp[15]+sign*vp[axis+12]
            if(x*b[if(x>=0f)3 else 0]+y*b[if(y>=0f)4 else 1]+z*b[if(z>=0f)5 else 2]+w<0f)return false
        }
        return true
    }
    fun upload() {
        val ids = IntArray(1)
        GL.glGenBuffers(1, ids, 0)
        buffer = ids[0]
        val bytes = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        bytes.put(vertices).position(0)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer)
        GL.glBufferData(GL.GL_ARRAY_BUFFER, vertices.size * 4, bytes, GL.GL_STATIC_DRAW)
    }
    fun draw() {
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer)
        GL.glEnableVertexAttribArray(0)
        GL.glEnableVertexAttribArray(1)
        GL.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0)
        GL.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12)
        GL.glDrawArrays(GL.GL_TRIANGLES, 0, count)
    }
    fun delete() {
        if (buffer != 0) GL.glDeleteBuffers(1, intArrayOf(buffer), 0)
        buffer = 0
    }
}

internal data class P(val x: Float, val y: Float, val z: Float)
internal data class C(val r: Float, val g: Float, val b: Float) {
    fun shade(v: Float) = C(r*v, g*v, b*v)
    fun mix(other:C,t:Float) = C(r+(other.r-r)*t,g+(other.g-g)*t,b+(other.b-b)*t)
}

internal class MeshBuilder {
    private var data = FloatArray(8192)
    private var size = 0
    val vertexCount get()=size/6
    /** Bake a local model into a scenery batch; no extra GPU object per ornament. */
    fun append(other:MeshBuilder, origin:P, yaw:Float=0f, scale:Float=1f, mirrorX:Float=1f,
        groundHeight:((Float,Float)->Float)?=null) {
        val cs=cos(yaw); val sn=sin(yaw)
        // Triangle lists repeat corners and stack many vertices at identical x/z.
        // Preserve exact terrain heights but only evaluate each position once per model.
        val heights = if (groundHeight != null) HashMap<Long, Float>() else null
        for(i in 0 until other.size step 6) {
            val x=other.data[i]*scale*mirrorX; val z=other.data[i+2]*scale
            val wx=origin.x+x*cs+z*sn; val wz=origin.z-x*sn+z*cs
            val ground = if (heights != null) {
                val key = (wx.toRawBits().toLong() shl 32) or (wz.toRawBits().toLong() and 0xffffffffL)
                heights.getOrPut(key) { groundHeight!!(wx, wz) }
            } else origin.y
            vertex(P(wx,ground+other.data[i+1]*scale,wz),
                C(other.data[i+3],other.data[i+4],other.data[i+5]))
        }
    }
    fun ellipsoid(x:Float,y:Float,z:Float,rx:Float,ry:Float,rz:Float,c:C) =
        organic(x,y,z,rx,ry,rz,c,0f,if(maxOf(rx,ry,rz)<.14f)12 else 20,
            if(maxOf(rx,ry,rz)<.14f)8 else 12,0f)
    private fun vertex(p: P, c: C) {
        if (size + 6 > data.size) data = data.copyOf(data.size * 2)
        data[size++] = p.x; data[size++] = p.y; data[size++] = p.z
        data[size++] = c.r; data[size++] = c.g; data[size++] = c.b
    }
    fun tri(a: P, b: P, c: P, colour: C, lit: Boolean = true) {
        val ux = b.x-a.x; val uy = b.y-a.y; val uz = b.z-a.z
        val vx = c.x-a.x; val vy = c.y-a.y; val vz = c.z-a.z
        val nx = uy*vz-uz*vy; val ny = uz*vx-ux*vz; val nz = ux*vy-uy*vx
        val length = sqrt(nx*nx + ny*ny + nz*nz).coerceAtLeast(.001f)
        val light = if (lit) .70f + .30f * ((nx * -.35f + abs(ny) * .86f + nz * -.36f) / length).coerceIn(0f,1f) else 1f
        val shade = colour.shade(light)
        vertex(a, shade); vertex(b, shade); vertex(c, shade)
    }
    fun quad(a: P, b: P, c: P, d: P, colour: C, lit: Boolean = true) {
        tri(a,b,c,colour,lit); tri(a,c,d,colour,lit)
    }
    /** Smooth vertex lighting is baked once for landscape, avoiding faceted checkerboards. */
    fun colouredQuad(a:P,b:P,c:P,d:P,ca:C,cb:C,cc:C,cd:C) {
        vertex(a,ca); vertex(b,cb); vertex(c,cc)
        vertex(a,ca); vertex(c,cc); vertex(d,cd)
    }
    fun box(x: Float,y: Float,z: Float,w: Float,h: Float,d: Float,c: C) {
        val a=P(x-w/2,y,z-d/2); val b=P(x+w/2,y,z-d/2)
        val e=P(x-w/2,y+h,z-d/2); val f=P(x+w/2,y+h,z-d/2)
        val k=P(x-w/2,y,z+d/2); val l=P(x+w/2,y,z+d/2)
        val m=P(x-w/2,y+h,z+d/2); val n=P(x+w/2,y+h,z+d/2)
        quad(a,b,f,e,c); quad(l,k,m,n,c); quad(k,a,e,m,c)
        quad(b,l,n,f,c); quad(e,f,n,m,c)
    }
    fun cone(x: Float,y: Float,z: Float,r: Float,h: Float,c: C,sides: Int=8, top: Float=0f) {
        for (i in 0 until sides) {
            val a = i * (2f * PI.toFloat() / sides); val b = (i+1)*(2f*PI.toFloat()/sides)
            val p=P(x+cos(a)*r,y,z+sin(a)*r); val q=P(x+cos(b)*r,y,z+sin(b)*r)
            val u=P(x+cos(a)*top,y+h,z+sin(a)*top); val v=P(x+cos(b)*top,y+h,z+sin(b)*top)
            quad(p,q,v,u,c)
            if (top > 0f) tri(P(x,y+h,z),u,v,c)
        }
    }
    fun sphere(x: Float,y: Float,z: Float,r: Float,c: C, segments: Int=10,rings: Int=6, sy: Float=1f) {
        fun point(a: Float,b: Float) = P(x + cos(a)*cos(b)*r, y+sin(b)*r*sy, z+sin(a)*cos(b)*r)
        for (j in 0 until rings) for (i in 0 until segments) {
            val a = i*2f*PI.toFloat()/segments; val b = (i+1)*2f*PI.toFloat()/segments
            val u = -PI.toFloat()/2+j*PI.toFloat()/rings; val v = -PI.toFloat()/2+(j+1)*PI.toFloat()/rings
            quad(point(a,u),point(b,u),point(b,v),point(a,v),c)
        }
    }
    fun disc(x:Float,y:Float,z:Float,rx:Float,rz:Float,c:C, segments:Int=24) {
        for(i in 0 until segments) {
            val a=i*2f*PI.toFloat()/segments; val b=(i+1)*2f*PI.toFloat()/segments
            tri(P(x,y,z),P(x+cos(a)*rx,y,z+sin(a)*rz),P(x+cos(b)*rx,y,z+sin(b)*rz),c,false)
        }
    }
    fun ring(x: Float,y: Float,z:Float,r:Float,width:Float,c:C,segments:Int=36) {
        for(i in 0 until segments) {
            val a=i*2f*PI.toFloat()/segments; val b=(i+1)*2f*PI.toFloat()/segments
            quad(P(x+cos(a)*r,y,z+sin(a)*r),P(x+cos(b)*r,y,z+sin(b)*r),
                P(x+cos(b)*(r+width),y,z+sin(b)*(r+width)),P(x+cos(a)*(r+width),y,z+sin(a)*(r+width)),c,false)
        }
    }
    /** Tapered branch/rail between arbitrary points, without a separate draw call. */
    fun beam(a:P,b:P,r:Float,c:C,sides:Int=6,top:Float=r) {
        val dx=b.x-a.x;val dy=b.y-a.y;val dz=b.z-a.z
        val length=sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(.0001f)
        val axis=P(dx/length,dy/length,dz/length)
        val horizontal=hypot(dx,dz)
        val u=if(horizontal>.0001f) P(dz/horizontal,0f,-dx/horizontal) else P(1f,0f,0f)
        val v=P(axis.y*u.z-axis.z*u.y,axis.z*u.x-axis.x*u.z,axis.x*u.y-axis.y*u.x)
        fun p(origin:P,angle:Float,radius:Float)=P(origin.x+(u.x*cos(angle)+v.x*sin(angle))*radius,
            origin.y+(u.y*cos(angle)+v.y*sin(angle))*radius,origin.z+(u.z*cos(angle)+v.z*sin(angle))*radius)
        for(i in 0 until sides) {
            val t=i*2f*PI.toFloat()/sides;val s=(i+1)*2f*PI.toFloat()/sides
            quad(p(a,t,r),p(a,s,r),p(b,s,top),p(b,t,top),c)
        }
    }
    /** Irregular low-poly crown/stone with smooth baked lighting, not a spherical silhouette. */
    fun organic(x:Float,y:Float,z:Float,rx:Float,ry:Float,rz:Float,c:C,seed:Float,
                segments:Int=8,rings:Int=4,irregularity:Float=.12f) {
        fun point(i:Int,j:Int):Pair<P,C> {
            val a=i*2f*PI.toFloat()/segments;val t=-PI.toFloat()/2+j*PI.toFloat()/rings
            val nx=cos(a)*cos(t);val ny=sin(t);val nz=sin(a)*cos(t)
            val wobble=1f+irregularity*(sin(a*3f+seed)*cos(t*2f)+.4f*cos(a*5f-seed+t))
            val light=.67f+.33f*max(0f,-nx*.35f+ny*.86f-nz*.36f)
            val tint=1f+.055f*sin(a*4f+t*3f+seed)
            return P(x+nx*rx*wobble,y+ny*ry*wobble,z+nz*rz*wobble) to c.shade(light*tint)
        }
        for(j in 0 until rings) for(i in 0 until segments) {
            val a=point(i,j);val b=point(i+1,j);val d=point(i,j+1);val e=point(i+1,j+1)
            colouredQuad(a.first,b.first,e.first,d.first,a.second,b.second,e.second,d.second)
        }
    }
    fun build() = ClassicMesh(data.copyOf(size))
}
