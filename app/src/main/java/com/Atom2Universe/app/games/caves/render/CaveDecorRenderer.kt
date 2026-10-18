package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import android.opengl.Matrix
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.toyboxracers.render.ColoredMesh
import com.Atom2Universe.app.games.toyboxracers.render.DecorMeshFactory

/** One static batch for the small map prop collection, with Cave World's floating origin. */
internal class CaveDecorRenderer(private val source: MapSource) {
    private var mesh: ColoredMesh? = null
    private var shader: ShaderProgram? = null
    private val model = FloatArray(16)
    private var mvpLocation = 0
    private var modelLocation = 0
    private var ambientLocation = 0
    private var fogLocation = 0

    fun onSurfaceCreated() {
        // The previous context (and its handles) is gone. Build fresh GPU resources on resume.
        if (source.decor.placements.isEmpty()) return
        shader = ShaderProgram(VERTEX, FRAGMENT).also {
            mvpLocation = it.uniform("uMvp")
            modelLocation = it.uniform("uModel")
            ambientLocation = it.uniform("uAmbient")
            fogLocation = it.uniform("uCaveFog")
        }
        mesh = DecorMeshFactory.build(source.decor.placements).also { it.upload() }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(camera: Camera, ambient: Float, caveBlend: Float, fogEnd: Float) {
        val s = shader ?: return
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, (source.originX-camera.x).toFloat(),
            (source.originY-camera.y).toFloat(), (source.originZ-camera.z).toFloat())
        s.use()
        GLES30.glUniform1f(ambientLocation, ambient)
        GLES30.glUniform3f(fogLocation, caveBlend, fogEnd * .55f, fogEnd)
        mesh?.draw(camera.vpMatrix, model, mvpLocation, modelLocation)
        // Cave's other renderers use the default VAO: never let them modify the prop VAO.
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun destroy() {
        mesh?.destroy(); mesh = null
        shader?.destroy(); shader = null
    }

    companion object {
        private const val VERTEX = """#version 300 es
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec3 aNormal;
            layout(location=2) in vec4 aColor;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            out vec3 vNormal;
            out vec4 vColor;
            out float vDistance;
            void main() {
                gl_Position = uMvp * vec4(aPosition,1.0);
                vNormal = aNormal;
                vColor = aColor;
                vDistance = length((uModel * vec4(aPosition,1.0)).xyz);
            }
        """
        private const val FRAGMENT = """#version 300 es
            precision mediump float;
            in vec3 vNormal;
            in vec4 vColor;
            in float vDistance;
            uniform float uAmbient;
            uniform vec3 uCaveFog;
            out vec4 fragColor;
            void main() {
                float diffuse = max(dot(normalize(vNormal),normalize(vec3(-0.45,0.85,0.35))),0.0);
                vec3 rgb = vColor.rgb * (0.5 + diffuse * 0.5) * uAmbient;
                float fog = smoothstep(uCaveFog.y,uCaveFog.z,vDistance) * uCaveFog.x;
                fragColor = vec4(mix(rgb,vec3(0.0),fog),1.0);
            }
        """
    }
}
