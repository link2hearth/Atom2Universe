package com.Atom2Universe.app.games.golf.classic.render

import android.opengl.GLES20 as GL
import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** What grows at a terrain vertex; rough is whatever the four weights leave. */
internal data class Turf(val fairway: Float = 0f, val green: Float = 0f, val sand: Float = 0f, val water: Float = 0f) {
    companion object { val ROUGH = Turf() }
}

/** Lit hazard colour, turf weights and signed mowing contours with their local lighting. */
internal class GroundSample(val colour: C, val turf: Turf,
    val fairwayDistance: Float = 1000f, val greenDistance: Float = 1000f,
    val light: Float = 1f, val cuts: Float = 0f)

/** Position, lit colour, turf weights, then fairway / green distances, lighting and cut mask. */
internal class GroundBuilder(capacity: Int = 1 shl 16) {
    private var data = FloatArray(capacity)
    private var size = 0

    fun vertex(p: P, s: GroundSample) {
        if (size + STRIDE > data.size) data = data.copyOf(data.size * 2)
        data[size++] = p.x; data[size++] = p.y; data[size++] = p.z
        data[size++] = s.colour.r; data[size++] = s.colour.g; data[size++] = s.colour.b
        data[size++] = s.turf.fairway; data[size++] = s.turf.green; data[size++] = s.turf.sand; data[size++] = s.turf.water
        data[size++] = s.fairwayDistance; data[size++] = s.greenDistance
        data[size++] = s.light; data[size++] = s.cuts
    }

    fun triangle(a: P, b: P, c: P, sa: GroundSample, sb: GroundSample, sc: GroundSample) {
        vertex(a, sa); vertex(b, sb); vertex(c, sc)
    }

    /** Flat-shaded quad with the same sun as [MeshBuilder] (aprons, paths, glints). */
    fun quad(a: P, b: P, c: P, d: P, colour: C, turf: Turf, lit: Boolean = true) {
        val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
        val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
        val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.001f)
        val light = if (lit) .70f + .30f * ((nx * -.35f + abs(ny) * .86f + nz * -.36f) / length).coerceIn(0f, 1f) else 1f
        val sample = GroundSample(colour.shade(light), turf)
        triangle(a, b, c, sample, sample, sample)
        triangle(a, c, d, sample, sample, sample)
    }

    /** Adds everything [other] holds, after what this one holds. */
    fun append(other: GroundBuilder) {
        if (size + other.size > data.size) data = data.copyOf(max(data.size * 2, size + other.size))
        other.data.copyInto(data, size, 0, other.size)
        size += other.size
    }

    fun build() = GroundMesh(data.copyOf(size))

    companion object { const val STRIDE = 14 }
}

internal class GroundMesh(private val vertices: FloatArray) {
    private var buffer = 0
    val count get() = vertices.size / GroundBuilder.STRIDE

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
        val stride = GroundBuilder.STRIDE * 4
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer)
        GL.glEnableVertexAttribArray(0); GL.glEnableVertexAttribArray(1); GL.glEnableVertexAttribArray(2)
        GL.glEnableVertexAttribArray(3)
        GL.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride, 0)
        GL.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride, 12)
        GL.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, stride, 24)
        GL.glVertexAttribPointer(3, 4, GL.GL_FLOAT, false, stride, 40)
        GL.glDrawArrays(GL.GL_TRIANGLES, 0, count)
        // The scene's other program only reads two attributes.
        GL.glDisableVertexAttribArray(2)
        GL.glDisableVertexAttribArray(3)
    }

    fun delete() {
        if (buffer != 0) GL.glDeleteBuffers(1, intArrayOf(buffer), 0)
        buffer = 0
    }
}

/**
 * The turf is textured per pixel: tufts in the rough, long mown bands on the fairway, a fine
 * soft single-direction cut on the green, grains and rake marks in the sand. Nothing is sampled at the
 * terrain vertices any more, so the 2.5 m mesh no longer shows as light and dark squares.
 * The slope board ([SlopeBoard]) is drawn here too: continuous, anti-aliased lines of constant
 * screen width that follow the turf exactly, tinted progressively by height.
 */
internal class GroundShader(private val highlands:Boolean=false, private val snowy:Boolean=false) {
    private var program = 0
    private var mvpLoc = 0; private var eyeLoc = 0; private var timeLoc = 0; private var overviewLoc = 0
    private var boardLoc = 0; private var boardSizeLoc = 0; private var boardCellLoc = 0
    private var holeLoc = 0; private var pixelLoc = 0; private var lineScaleLoc = 0
    private var cutWidthsLoc = 0
    private var highlandsLoc = 0
    private var snowLoc = 0

    fun create() {
        program = link(VERTEX, FRAGMENT)
        mvpLoc = GL.glGetUniformLocation(program, "uMvp")
        eyeLoc = GL.glGetUniformLocation(program, "uEye")
        timeLoc = GL.glGetUniformLocation(program, "uTime")
        overviewLoc = GL.glGetUniformLocation(program, "uOverview")
        boardLoc = GL.glGetUniformLocation(program, "uBoard")
        boardSizeLoc = GL.glGetUniformLocation(program, "uBoardSize")
        boardCellLoc = GL.glGetUniformLocation(program, "uBoardCell")
        holeLoc = GL.glGetUniformLocation(program, "uHole")
        pixelLoc = GL.glGetUniformLocation(program, "uPixel")
        lineScaleLoc = GL.glGetUniformLocation(program, "uLineScale")
        cutWidthsLoc = GL.glGetUniformLocation(program, "uCutWidths")
        highlandsLoc = GL.glGetUniformLocation(program, "uHighlands")
        snowLoc = GL.glGetUniformLocation(program, "uSnow")
    }

    /**
     * Draws [mesh] in world coordinates, with the slope [board] (squares of [cell] metres, the
     * half-size lines weighted by [fineWeight]) if any. [pixel] is the size of a screen pixel one
     * metre from the eye; line widths are multiplied by [lineScale]. The caller restores its own
     * program afterwards.
     */
    fun draw(mesh: GroundMesh, viewProjection: FloatArray, eyeX: Float, eyeY: Float, eyeZ: Float, time: Float, overview: Boolean,
             board: SlopeBoard?, cell: Float, fineWeight: Float, holeX: Float, holeZ: Float, pixel: Float, lineScale: Float) {
        GL.glUseProgram(program)
        GL.glUniform1f(highlandsLoc,if(highlands)1f else 0f)
        GL.glUniform1f(snowLoc,if(snowy)1f else 0f)
        GL.glUniformMatrix4fv(mvpLoc, 1, false, viewProjection, 0)
        GL.glUniform3f(eyeLoc, eyeX, eyeY, eyeZ)
        GL.glUniform1f(timeLoc, time)
        GL.glUniform1f(overviewLoc, if (overview) 1f else 0f)
        if (board != null) {
            GL.glUniform4f(boardLoc, board.fromX, board.fromZ, board.toX, board.toZ)
            GL.glUniform2f(boardSizeLoc, board.radius, 1f)
            GL.glUniform4f(boardCellLoc, cell, fineWeight, board.baseY, board.relief)
        } else GL.glUniform2f(boardSizeLoc, 0f, 0f)
        GL.glUniform2f(holeLoc, holeX, holeZ)
        GL.glUniform1f(pixelLoc, pixel)
        GL.glUniform1f(lineScaleLoc, lineScale)
        GL.glUniform2f(cutWidthsLoc, ClassicHole.SEMI_ROUGH_WIDTH, ClassicHole.FRINGE_WIDTH)
        mesh.draw()
    }

    fun release() {
        if (program != 0) GL.glDeleteProgram(program)
        program = 0
    }

    private fun link(vertexSource: String, fragmentSource: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = GL.glCreateShader(type)
            GL.glShaderSource(id, source); GL.glCompileShader(id)
            val result = IntArray(1); GL.glGetShaderiv(id, GL.GL_COMPILE_STATUS, result, 0)
            check(result[0] != 0) { GL.glGetShaderInfoLog(id) }
            return id
        }
        val vertex = shader(GL.GL_VERTEX_SHADER, vertexSource)
        val fragment = shader(GL.GL_FRAGMENT_SHADER, fragmentSource)
        val id = GL.glCreateProgram()
        GL.glAttachShader(id, vertex); GL.glAttachShader(id, fragment)
        GL.glBindAttribLocation(id, 0, "aPosition"); GL.glBindAttribLocation(id, 1, "aColour"); GL.glBindAttribLocation(id, 2, "aTurf")
        GL.glBindAttribLocation(id, 3, "aCuts")
        GL.glLinkProgram(id)
        GL.glDeleteShader(vertex); GL.glDeleteShader(fragment)
        val result = IntArray(1); GL.glGetProgramiv(id, GL.GL_LINK_STATUS, result, 0)
        check(result[0] != 0) { GL.glGetProgramInfoLog(id) }
        return id
    }

    companion object {
        const val VERTEX = """
uniform mat4 uMvp;
attribute vec3 aPosition;
attribute vec3 aColour;
attribute vec4 aTurf;
attribute vec4 aCuts;
varying vec3 vColour;
varying vec3 vWorld;
varying vec4 vTurf;
varying vec4 vCuts;
void main() {
    vColour = aColour;
    vWorld = aPosition;
    vTurf = aTurf;
    vCuts = aCuts;
    gl_Position = uMvp * vec4(aPosition, 1.0);
}
"""
        const val FRAGMENT = """#extension GL_OES_standard_derivatives : enable
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec3 uEye;
uniform float uTime;
uniform float uOverview;
uniform float uHighlands;
uniform float uSnow;
uniform vec2 uCutWidths;
// Slope board: squares fixed on the ground, lines along the world axes through the hole (uHole).
// Window: the squares whose centre lies within radius of the segment uBoard (from xz, to xz);
// window radius, on; square size, weight of the half-size lines, reference height, rise shown in full colour.
uniform vec4 uBoard;
uniform vec2 uBoardSize;
uniform vec4 uBoardCell;
uniform vec2 uHole;
uniform float uPixel;
// Line widths follow the screen size, so a tablet and a phone see the same proportions.
uniform float uLineScale;
varying vec3 vColour;
varying vec3 vWorld;
varying vec4 vTurf;
varying vec4 vCuts;
// Lines keep a constant width on screen: a light core inside a soft dark border that lifts them off the turf.
vec2 lineCore(vec2 pixels) { return 1.0 - smoothstep(.55, 1.25, pixels); }
vec2 lineHalo(vec2 pixels) { return 1.0 - smoothstep(1.0, 2.6, pixels); }
float segmentDistance(vec2 c) {
    vec2 ab=uBoard.zw-uBoard.xy;
    float t=clamp(dot(c-uBoard.xy,ab)/max(dot(ab,ab),1e-6),0.0,1.0);
    return length(c-uBoard.xy-ab*t);
}
// How much of the square centred on g (in board squares) shows, as SlopeBoard.weight.
float boardWindow(vec2 g, float fade) {
    return 1.0-smoothstep(uBoardSize.x-fade,uBoardSize.x,segmentDistance(uHole+g*uBoardCell.x));
}
// The window cut along squares k board squares wide: x for the lines of constant x, y for the
// others. A line shows as much as the more visible of the two squares it separates.
vec2 boardLines(vec2 g, float k) {
    float fade=min(2.5*k*uBoardCell.x,uBoardSize.x*.5);
    vec2 c=(floor(g/k)+.5)*k;
    vec2 n=floor(g/k+.5)*k;
    float h=.5*k;
    return vec2(max(boardWindow(vec2(n.x-h,c.y),fade),boardWindow(vec2(n.x+h,c.y),fade)),
                max(boardWindow(vec2(c.x,n.y-h),fade),boardWindow(vec2(c.x,n.y+h),fade)));
}
float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * .1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
void main() {
    vec2 p=vWorld.xz;
    vec3 toEye=uEye-vWorld;
    float dist=length(toEye);
    float near=1.0-smoothstep(9.0,55.0,dist);
    float mid=1.0-smoothstep(100.0,430.0,dist);
    float grazing=clamp(abs(toEye.y)/max(dist,.01),.12,1.0);
    float fine=1.0-smoothstep(4.0*grazing,24.0*grazing,dist);
    float fairway=vTurf.x, green=vTurf.y, sand=vTurf.z, water=vTurf.w;
    float semi=0.0, fringe=0.0;
    vec3 base=vColour;
    if(vCuts.w>.5) {
        // Threshold interpolated contour distances per pixel: narrow, crisp mowing edges.
        float edge=.035;
#ifdef GL_OES_standard_derivatives
        edge=max(edge,min(.18,max(fwidth(vCuts.x),fwidth(vCuts.y))*.65));
#endif
        green=1.0-smoothstep(-edge,edge,vCuts.y);
        fringe=(1.0-green)*(1.0-smoothstep(uCutWidths.y-edge,uCutWidths.y+edge,vCuts.y));
        float remaining=1.0-green-fringe;
        float tee=1.0-smoothstep(-edge,edge,max(abs(p.x)-4.5,abs(p.y)-5.5));
        fairway=remaining*max(tee,1.0-smoothstep(-edge,edge,vCuts.x));
        semi=max(0.0,remaining-fairway)*(1.0-smoothstep(uCutWidths.x-edge,uCutWidths.x+edge,vCuts.x));
        vec3 grass=mix(vec3(.30,.46,.21),vec3(.48,.43,.26),uHighlands)*(1.0-green-fringe-fairway-semi)
            +mix(vec3(.39,.58,.25),vec3(.40,.51,.32),uHighlands)*fairway
            +mix(vec3(.40,.50,.23),vec3(.46,.46,.29),uHighlands)*semi
            +mix(vec3(.32,.52,.24),vec3(.35,.47,.30),uHighlands)*fringe
            +mix(vec3(.46,.65,.32),vec3(.49,.61,.40),uHighlands)*green;
        vec3 winter=vec3(.86,.90,.94)*(1.0-green-fringe-fairway-semi)
            +vec3(.37,.51,.48)*fairway+vec3(.69,.77,.79)*semi
            +vec3(.52,.65,.61)*fringe+vec3(.40,.58,.49)*green;
        grass=mix(grass,winter,uSnow);
        float dry=1.0-clamp(sand+water,0.0,1.0);
        base=mix(vColour,grass*vCuts.z,dry);
        green*=dry; fringe*=dry; fairway*=dry; semi*=dry;
    }
    float rough=clamp(1.0-fairway-green-semi-fringe-sand-water,0.0,1.0);
    float meadow=noise(p*.065)*.65+noise(p*.19)*.35;
    float cloudShade=smoothstep(.48,.76,noise(p*.009+vec2(uTime*.008,0.0)));
    float shade=1.0+(meadow-.5)*.17*mid-cloudShade*.075;
    vec3 tint=vec3(1.0);
    if(rough>.01) {
        float tufts=noise(p*2.3)*.60+noise(p*7.9)*.40;
        shade+=rough*(tufts-.48)*.27*near;
        float straw=smoothstep(.58,.79,meadow);
        tint=mix(tint,vec3(1.13,1.03,.86),rough*straw*.48*(1.0-uSnow));
    }
    if(fairway>.01) {
        float soft=clamp(dist*.004,.08,1.0);
        float stripes=smoothstep(-soft,soft,sin((p.x*.93+p.y*.37)*.70));
        shade+=fairway*((stripes-.5)*.115*mid+(noise(p*5.0)-.5)*.055*near);
    }
    if(green>.01) {
        float soft=clamp(dist*.012,.10,1.0);
        // Soft mowing bands in one direction only: a chequer fought with the putting grid.
        float bands=smoothstep(-soft,soft,sin((p.x*.42+p.y*.91)*1.35));
        shade+=green*((bands-.5)*.032*mid+(noise(p*13.0)-.5)*.035*near);
    }
    shade+=semi*(noise(p*4.0)-.5)*.12*near;
    shade+=fringe*(noise(p*10.0)-.5)*.045*near;
    // Fine cut fibres are filtered out at distance AND at grazing angles (no crawling moire).
    if(fine>.001 && rough+fairway>.01) {
        vec2 cell=p*vec2(48.0,19.0);
        vec2 q=fract(cell);
        float line=1.0-smoothstep(.035,.14,abs(q.x-.5-(q.y-.5)*.28));
        line*=smoothstep(.05,.30,q.y)*(1.0-smoothstep(.60,.98,q.y));
        shade+=(rough+fairway)*(line-.16)*.16*fine;
    }
    if(sand>.01) {
        float rake=sin(p.x*8.0+p.y*1.7+noise(p*.6)*2.4);
        shade+=sand*((noise(p*24.0)-.5)*.14*fine+rake*.035*near);
        tint=mix(tint,vec3(1.04,1.015,.94),sand);
    }
    vec3 colour=base*shade*tint;
    if(water>.01) {
        float phase=p.x*.65+p.y*.93+uTime*.85;
        vec3 normal=normalize(vec3(.045*cos(phase)+.018*sin(p.y*2.2-uTime),1.0,
            .05*sin(phase*.83)+.015*cos(p.x*2.7+uTime*1.3)));
        vec3 view=normalize(toEye);
        float fresnel=.12+.68*pow(1.0-max(0.0,dot(normal,view)),4.0);
        vec3 reflection=mix(vec3(.43,.61,.67),vec3(.79,.84,.78),fresnel);
        vec3 lake=mix(vColour*shade,reflection,fresnel);
        vec3 sun=normalize(vec3(-.35,.86,-.36));
        float sparkle=pow(max(0.0,dot(normal,normalize(view+sun))),96.0);
        lake+=vec3(1.0,.88,.61)*sparkle*.65;
        float edge=(1.0-smoothstep(.35,.98,water))*water;
        lake+=vec3(.28,.30,.18)*edge*(.7+.3*sin(phase*3.0));
        colour=mix(colour,lake,water);
    }
    // Slope board, over the turf only.
    if(uBoardSize.y>0.5) {
        float mask=step(segmentDistance(p),uBoardSize.x+2.0*uBoardCell.x)
            *smoothstep(.10,.16,length(p-uHole))*(1.0-water);
        if(mask>.002) {
            vec2 g=(p-uHole)/uBoardCell.x;
#ifdef GL_OES_standard_derivatives
            vec2 w=max(fwidth(g),vec2(1e-4));
#else
            vec2 w=vec2(dist*uPixel/(grazing*uBoardCell.x));
#endif
            // Distances in reference pixels (1/900 of the screen height), whatever the screen.
            w*=uLineScale;
            vec2 fine=abs(fract(g-.5)-.5)/w;
            vec2 coarse=abs(fract(g*.5-.5)-.5)/(w*.5);
            // Squares smaller than a few pixels fade out instead of shimmering.
            float lodFine=(1.0-smoothstep(.07,.16,max(w.x,w.y)))*uBoardCell.y;
            float lodCoarse=1.0-smoothstep(.07,.16,max(w.x,w.y)*.5);
            // Whole squares show or fade, cut at the half-size squares' scale as those lines fade in.
            vec2 lines=mix(boardLines(g,2.0),boardLines(g,1.0),uBoardCell.y);
            // The half-size lines are lighter versions: their border fades faster than their core.
            vec2 cores=max(lineCore(coarse)*lodCoarse,lineCore(fine)*lodFine*.8)*lines;
            vec2 halos=max(lineHalo(coarse)*lodCoarse,lineHalo(fine)*lodFine*lodFine*.4)*lines;
            float core=max(cores.x,cores.y);
            float halo=max(halos.x,halos.y);
            // Height above the reference, progressively: white when level, pink then red higher up, green lower down.
            float rise=clamp((vWorld.y-uBoardCell.z)/uBoardCell.w,-1.0,1.0);
            vec3 level=vec3(.94,.95,.90);
            vec3 tone=rise>0.0?mix(level,vec3(.96,.36,.28),rise):mix(level,vec3(.30,.86,.50),-rise);
            colour=mix(colour,colour*.55,halo*mask*.6);
            colour=mix(colour,tone,core*mask*.85);
        }
    }
    float fog=smoothstep(100.0,760.0,dist)*mix(.56,.13,uOverview);
    gl_FragColor=vec4(mix(colour,mix(vec3(.70,.79,.78),vec3(.68,.72,.76),uHighlands),fog),1.0);
}
"""
    }
}
