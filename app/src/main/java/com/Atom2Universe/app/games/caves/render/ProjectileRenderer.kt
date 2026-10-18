package com.Atom2Universe.app.games.caves.render

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES30
import com.Atom2Universe.app.games.caves.entity.ImpactParticle
import com.Atom2Universe.app.games.caves.entity.ProjectileKind
import com.Atom2Universe.app.games.caves.entity.Projectile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

internal class ProjectileRenderer {

    private var physicalShader: ShaderProgram? = null
    private val physicalMesh = HeldEquipmentMesh()
    private var physicalPos=0; private var physicalColor=0; private var physicalMvp=0
    private var shader: ShaderProgram? = null
    private var aPos = 0; private var aUv = 0
    private var uMvp = 0; private var uTex = 0

    private var partShader: ShaderProgram? = null
    private var pAPos = 0; private var pAUv = 0; private var pAAlpha = 0
    private var pUMvp = 0; private var pUTex = 0

    private val textures = IntArray(8)
    private var vbo = 0

    private val MAX_PROJ = 256
    private val vBuf = FloatArray(MAX_PROJ * 6 * 5)
    // Buffer natif réutilisé pour chaque upload GPU (par groupe de texture et par particule) —
    // un allocateDirect() par frame (voire par particule) saturait le GC pendant le tir en rafale.
    private val nativeBuf: FloatBuffer =
        ByteBuffer.allocateDirect(vBuf.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private val VERT = """
        #version 300 es
        in vec3 a_pos;
        in vec2 a_uv;
        uniform mat4 u_mvp;
        out vec2 v_uv;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_uv = a_uv;
        }
    """.trimIndent()

    private val FRAG = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        in vec2 v_uv;
        out vec4 fragColor;
        void main() {
            vec4 c = texture(u_tex, v_uv);
            if (c.a < 0.05) discard;
            fragColor = c;
        }
    """.trimIndent()

    // Alpha par sommet plutôt qu'un uniform : un uniform différent par particule interdisait
    // de les regrouper en un seul appel de dessin (jusqu'à 128 draw calls/frame en combat
    // dense — voir renderParticles). Porté par le flux de sommets, tout le lot part ensemble.
    private val VERT_PART = """
        #version 300 es
        in vec3 a_pos;
        in vec2 a_uv;
        in float a_alpha;
        uniform mat4 u_mvp;
        out vec2 v_uv;
        out float v_alpha;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_uv = a_uv;
            v_alpha = a_alpha;
        }
    """.trimIndent()

    private val FRAG_PART = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        in vec2 v_uv;
        in float v_alpha;
        out vec4 fragColor;
        void main() {
            vec4 c = texture(u_tex, v_uv);
            if (c.a < 0.05) discard;
            fragColor = vec4(c.rgb, c.a * v_alpha);
        }
    """.trimIndent()

    fun onSurfaceCreated(assets: AssetManager) {
        physicalShader=ShaderProgram("""
            #version 300 es
            in vec3 a_pos;
            in vec3 a_color;
            uniform mat4 u_mvp;
            out vec3 color;
            void main() { color=a_color; gl_Position=u_mvp*vec4(a_pos,1.0); }
        """.trimIndent(),"""
            #version 300 es
            precision mediump float;
            in vec3 color;
            out vec4 fragColor;
            void main() { fragColor=vec4(color,1.0); }
        """.trimIndent()).also {
            it.use(); physicalPos=it.attrib("a_pos");physicalColor=it.attrib("a_color");physicalMvp=it.uniform("u_mvp")
        }
        shader = ShaderProgram(VERT, FRAG).also {
            it.use()
            aPos = it.attrib("a_pos"); aUv  = it.attrib("a_uv")
            uMvp = it.uniform("u_mvp"); uTex = it.uniform("u_tex")
        }
        // Ordre : WHITE_SQUARE, WHITE_SWIRL, BLUE_SQUARE, BLUE_SWIRL, ORANGE_SQUARE, ORANGE_SWIRL, RED_SQUARE, RED_SWIRL
        val files = listOf(
            "square_white.png", "swirl_white.png",
            "square_blue.png",  "swirl_blue.png",
            "square_orange.png","swirl_orange.png",
            "square_red.png",   "swirl_red.png"
        )
        val ids = IntArray(8); GLES30.glGenTextures(8, ids, 0)
        files.forEachIndexed { i, name ->
            textures[i] = ids[i]
            val bmp = BitmapFactory.decodeStream(assets.open("caves/particles/$name"))
            val buf = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
            bmp.copyPixelsToBuffer(buf); buf.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[i])
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                bmp.width, bmp.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            bmp.recycle()
        }
        val vboIds = IntArray(1); GLES30.glGenBuffers(1, vboIds, 0); vbo = vboIds[0]

        partShader = ShaderProgram(VERT_PART, FRAG_PART).also {
            it.use()
            pAPos  = it.attrib("a_pos"); pAUv  = it.attrib("a_uv"); pAAlpha = it.attrib("a_alpha")
            pUMvp  = it.uniform("u_mvp"); pUTex = it.uniform("u_tex")
        }
    }

    fun render(
        projectiles: List<Projectile>,
        camX: Double, camY: Double, camZ: Double,
        cameraYaw: Float, vpMatrix: FloatArray
    ) {
        if (projectiles.isEmpty()) return
        shader?.use() ?: return

        val yawRad = Math.toRadians(cameraYaw.toDouble())
        val rX = (-cos(yawRad)).toFloat()
        val rZ = sin(yawRad).toFloat()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, vpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(uTex, 0)

        // Regroupement par texture sans allouer de List/HashMap par frame (filter+groupBy
        // tournait 60x/s même hors tir) : on parcourt les projectiles une fois par texture.
        for (texIdx in 0 until 8) {
            var si = 0
            for (p in projectiles) {
                if (p.kind != ProjectileKind.LEGACY) continue
                if (p.weapon.texIndex.coerceIn(0, 7) != texIdx) continue
                if (si + 6 * 5 > vBuf.size) break
                val px = (p.x - camX).toFloat()
                val py = (p.y - camY).toFloat()
                val pz = (p.z - camZ).toFloat()
                // Un caillou a la même échelle que celui dans la main, pas un halo de 40 cm.
                val hw = if (p.isRock) 0.055f else 0.20f
                // Le départ physique reste près du viseur pour conserver collisions/précision.
                // Son sprite n'apparaît qu'une fois sorti de l'espace occupé par le visage.
                if (p.isRock && px*px + py*py + pz*pz < 0.35f*0.35f) continue
                if (!ProjectileVisibility.inFrontOfNearPlane(px, py, pz, rX, rZ, hw, vpMatrix)) continue
                fun sv(rx: Float, ry: Float, u: Float, v: Float) {
                    vBuf[si++] = px + rX * rx; vBuf[si++] = py + ry; vBuf[si++] = pz + rZ * rx
                    vBuf[si++] = u;             vBuf[si++] = v
                }
                sv(-hw,  hw, 0f, 0f); sv(-hw, -hw, 0f, 1f); sv( hw, -hw, 1f, 1f)
                sv(-hw,  hw, 0f, 0f); sv( hw, -hw, 1f, 1f); sv( hw,  hw, 1f, 0f)
            }
            val count = si / 5
            if (count == 0) continue
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[texIdx])
            nativeBuf.clear(); nativeBuf.put(vBuf, 0, si); nativeBuf.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, si * 4, nativeBuf, GLES30.GL_DYNAMIC_DRAW)
            val stride = 5 * 4
            GLES30.glEnableVertexAttribArray(aPos)
            GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(aUv)
            GLES30.glVertexAttribPointer(aUv,  2, GLES30.GL_FLOAT, false, stride, 12)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
            GLES30.glDisableVertexAttribArray(aPos)
            GLES30.glDisableVertexAttribArray(aUv)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        renderPhysical(projectiles,camX,camY,camZ,vpMatrix)
    }

    private fun renderPhysical(projectiles: List<Projectile>,camX: Double,camY: Double,camZ: Double,vp: FloatArray) {
        physicalShader?.use() ?: return
        GLES30.glUniformMatrix4fv(physicalMvp,1,false,vp,0)
        val m=physicalMesh;m.clear()
        fun flush() {
            if(m.count==0) return
            m.buffer.clear();m.buffer.put(m.vertices,0,m.count);m.buffer.flip()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,m.count*4,m.buffer,GLES30.GL_DYNAMIC_DRAW)
            GLES30.glEnableVertexAttribArray(physicalPos);GLES30.glVertexAttribPointer(physicalPos,3,GLES30.GL_FLOAT,false,24,0)
            GLES30.glEnableVertexAttribArray(physicalColor);GLES30.glVertexAttribPointer(physicalColor,3,GLES30.GL_FLOAT,false,24,12)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,m.count/6)
            GLES30.glDisableVertexAttribArray(physicalPos);GLES30.glDisableVertexAttribArray(physicalColor)
            m.clear()
        }
        for(p in projectiles) {
            if(p.kind==ProjectileKind.LEGACY) continue
            val x=(p.x-camX).toFloat();val y=(p.y-camY).toFloat();val z=(p.z-camZ).toFloat()
            if(x*x+y*y+z*z<.35f*.35f) continue
            // Tout le volume doit se trouver devant le plan proche, queue comprise.
            val extent=if(p.kind==ProjectileKind.ARROW || p.kind==ProjectileKind.BOLT) .6f else .22f
            val near=(vp[2]+vp[3])*x+(vp[6]+vp[7])*y+(vp[10]+vp[11])*z+vp[14]+vp[15]
            val nx=vp[2]+vp[3];val ny=vp[6]+vp[7];val nz=vp[10]+vp[11]
            if(near<=extent*kotlin.math.sqrt(nx*nx+ny*ny+nz*nz)) continue
            if(m.count>70000) flush()
            m.projectile(p.kind,x,y,z,(p.dirX*p.speed).toFloat(),p.velY.toFloat(),(p.dirZ*p.speed).toFloat())
        }
        flush();GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0)
    }

    fun renderParticles(
        particles: List<ImpactParticle>,
        camX: Double, camY: Double, camZ: Double,
        cameraYaw: Float, vpMatrix: FloatArray
    ) {
        if (particles.isEmpty()) return
        partShader?.use() ?: return

        val yawRad = Math.toRadians(cameraYaw.toDouble())
        val rX = (-cos(yawRad)).toFloat()
        val rZ = sin(yawRad).toFloat()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glUniformMatrix4fv(pUMvp, 1, false, vpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(pUTex, 0)
        // square_blue = index 2
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[2])

        var si = 0
        for (p in particles) {
            if (si + 6 * 6 > vBuf.size) break
            val alpha = (p.lifeRem / p.lifeMax).coerceIn(0f, 1f)
            val px = (p.x - camX).toFloat()
            val py = (p.y - camY).toFloat()
            val pz = (p.z - camZ).toFloat()
            val hw = 0.10f
            if (!ProjectileVisibility.inFrontOfNearPlane(px, py, pz, rX, rZ, hw, vpMatrix)) continue
            fun sv(rx: Float, ry: Float, u: Float, v: Float) {
                vBuf[si++] = px + rX * rx; vBuf[si++] = py + ry; vBuf[si++] = pz + rZ * rx
                vBuf[si++] = u;             vBuf[si++] = v;      vBuf[si++] = alpha
            }
            sv(-hw,  hw, 0f, 0f); sv(-hw, -hw, 0f, 1f); sv( hw, -hw, 1f, 1f)
            sv(-hw,  hw, 0f, 0f); sv( hw, -hw, 1f, 1f); sv( hw,  hw, 1f, 0f)
        }
        val count = si / 6
        if (count > 0) {
            nativeBuf.clear(); nativeBuf.put(vBuf, 0, si); nativeBuf.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, si * 4, nativeBuf, GLES30.GL_DYNAMIC_DRAW)
            val stride = 6 * 4
            GLES30.glEnableVertexAttribArray(pAPos)
            GLES30.glVertexAttribPointer(pAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(pAUv)
            GLES30.glVertexAttribPointer(pAUv,  2, GLES30.GL_FLOAT, false, stride, 12)
            GLES30.glEnableVertexAttribArray(pAAlpha)
            GLES30.glVertexAttribPointer(pAAlpha, 1, GLES30.GL_FLOAT, false, stride, 20)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
            GLES30.glDisableVertexAttribArray(pAPos)
            GLES30.glDisableVertexAttribArray(pAUv)
            GLES30.glDisableVertexAttribArray(pAAlpha)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun destroy() {
        physicalShader?.destroy()
        shader?.destroy()
        partShader?.destroy()
        if (textures.any { it != 0 }) GLES30.glDeleteTextures(8, textures, 0)
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
    }
}
