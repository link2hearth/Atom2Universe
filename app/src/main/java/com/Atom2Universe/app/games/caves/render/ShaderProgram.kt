package com.Atom2Universe.app.games.caves.render

import android.opengl.GLES30
import android.util.Log

internal class ShaderProgram(vertSrc: String, fragSrc: String) {
    companion object {
        // Each GLSurfaceView owns its GL thread, so simultaneous previews cannot
        // change another renderer's shader palette.
        val grayscale = ThreadLocal.withInitial { false }
    }
    val id: Int

    init {
        val vert = compile(GLES30.GL_VERTEX_SHADER, vertSrc)
        val source = if (grayscale.get() == true) {
            // Convert the final shaded colour, preserving alpha, discard and early returns.
            fragSrc.replace(Regex("void\\s+main\\s*\\(\\s*\\)"), "void caveColorMain()") + """

                void main() {
                    caveColorMain();
                    float gray = dot(fragColor.rgb, vec3(0.2126, 0.7152, 0.0722));
                    fragColor.rgb = vec3(gray);
                }
            """.trimIndent()
        } else fragSrc
        val frag = compile(GLES30.GL_FRAGMENT_SHADER, source)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vert)
        GLES30.glAttachShader(id, frag)
        GLES30.glLinkProgram(id)
        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) Log.e("CaveShader", GLES30.glGetProgramInfoLog(id))
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) Log.e("CaveShader", GLES30.glGetShaderInfoLog(shader))
        return shader
    }

    fun use() = GLES30.glUseProgram(id)
    fun attrib(name: String) = GLES30.glGetAttribLocation(id, name)
    fun uniform(name: String) = GLES30.glGetUniformLocation(id, name)
    fun destroy() = GLES30.glDeleteProgram(id)
}
