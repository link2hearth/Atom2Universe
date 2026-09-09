package com.Atom2Universe.app.games.toyboxracers.render

import android.opengl.GLES30
import android.opengl.Matrix
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolume
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolumeKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxRotationAxis
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxTrackSection
import com.Atom2Universe.app.games.toyboxracers.editor.TrackStyle
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import com.Atom2Universe.app.games.toyboxracers.track.ToyKind
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.RoomThemes
import com.Atom2Universe.app.games.toyboxracers.track.RoomTheme
import com.Atom2Universe.app.games.toyboxracers.track.FloorKind
import com.Atom2Universe.app.games.toyboxracers.track.HouseGeometry
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class ToyboxShader {
    val program: Int
    val mvpLocation: Int
    val modelLocation: Int

    init {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertex)
            GLES30.glAttachShader(it, fragment)
            GLES30.glLinkProgram(it)
        }
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { "Toybox shader link: ${GLES30.glGetProgramInfoLog(program)}" }
        mvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
        modelLocation = GLES30.glGetUniformLocation(program, "uModel")
    }

    fun destroy() = GLES30.glDeleteProgram(program)

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] != 0) { "Toybox shader compile: ${GLES30.glGetShaderInfoLog(shader)}" }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec4 aColor;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            out vec3 vNormal;
            out vec4 vColor;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = normalize(mat3(uModel) * aNormal);
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec3 vNormal;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                vec3 lightDirection = normalize(vec3(-0.45, 0.85, 0.35));
                float diffuse = max(dot(normalize(vNormal), lightDirection), 0.0);
                float light = 0.58 + diffuse * 0.42;
                fragColor = vec4(vColor.rgb * light, vColor.a);
            }
        """
    }
}

internal data class MeshLayer(val firstVertex: Int, var vertexCount: Int, val priority: Int)

internal class ColoredMesh private constructor(
    private val vertexBuffer: java.nio.FloatBuffer,
    private val vertexCount: Int,
    private val layers: List<MeshLayer>
) {
    private var vao = 0
    private var vbo = 0
    private val mvp = FloatArray(16)

    fun upload() {
        val handles = IntArray(1)
        GLES30.glGenVertexArrays(1, handles, 0)
        vao = handles[0]
        GLES30.glGenBuffers(1, handles, 0)
        vbo = handles[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexBuffer.capacity() * Float.SIZE_BYTES,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )
        val stride = FLOATS_PER_VERTEX * Float.SIZE_BYTES
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * Float.SIZE_BYTES)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 6 * Float.SIZE_BYTES)
        GLES30.glBindVertexArray(0)
    }

    fun draw(shader: ToyboxShader, viewProjection: FloatArray, model: FloatArray) {
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES30.glUniformMatrix4fv(shader.mvpLocation, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(shader.modelLocation, 1, false, model, 0)
        GLES30.glBindVertexArray(vao)
        try {
            for (layer in layers) {
                if (layer.priority > 0) {
                    GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
                    GLES30.glPolygonOffset(-1f, -4f * layer.priority)
                } else {
                    GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
                }
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, layer.firstVertex, layer.vertexCount)
            }
        } finally {
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(0f, 0f)
        }
    }

    fun destroy() {
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        vbo = 0
        vao = 0
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 10

        fun from(values: FloatArray, layers: List<MeshLayer> = listOf(MeshLayer(0, values.size / FLOATS_PER_VERTEX, 0))): ColoredMesh {
            val buffer = ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(values); position(0) }
            return ColoredMesh(buffer, values.size / FLOATS_PER_VERTEX, layers)
        }
    }
}

internal class MeshBuilder(surfacePriorityStart: Int = 1) {
    private val values = ArrayList<Float>()
    private var placement: DecorPlacement? = null
    private var priority = 0
    private var nextSurfacePriority = surfacePriorityStart.coerceAtLeast(1)
    private val layerRuns = ArrayList<MeshLayer>()

    /** Each overlay gets a stable layer in construction order (also stable after loading). */
    fun surface(build: () -> Unit) {
        val previous = priority
        priority = nextSurfacePriority++
        try { build() } finally { priority = previous }
    }

    fun placed(value: DecorPlacement, build: () -> Unit) {
        check(placement == null) { "Nested decor transforms are not supported" }
        placement = value
        try { build() } finally { placement = null }
    }

    fun triangle(a: Vec3, b: Vec3, c: Vec3, color: FloatArray) {
        val previous = layerRuns.lastOrNull()
        if (previous != null && previous.priority == priority) {
            previous.vertexCount += 3
        } else {
            layerRuns += MeshLayer(values.size / 10, 3, priority)
        }
        val normal = cross(b - a, c - a).normalized()
        vertex(a, normal, color)
        vertex(b, normal, color)
        vertex(c, normal, color)
    }

    fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, color: FloatArray) {
        triangle(a, b, c, color)
        triangle(a, c, d, color)
    }

    fun box(centerX: Float, centerY: Float, centerZ: Float, sizeX: Float, sizeY: Float, sizeZ: Float, color: FloatArray) {
        val left = centerX - sizeX * 0.5f
        val right = centerX + sizeX * 0.5f
        val bottom = centerY - sizeY * 0.5f
        val top = centerY + sizeY * 0.5f
        val back = centerZ - sizeZ * 0.5f
        val front = centerZ + sizeZ * 0.5f
        val lbb = Vec3(left, bottom, back)
        val rbb = Vec3(right, bottom, back)
        val ltb = Vec3(left, top, back)
        val rtb = Vec3(right, top, back)
        val lbf = Vec3(left, bottom, front)
        val rbf = Vec3(right, bottom, front)
        val ltf = Vec3(left, top, front)
        val rtf = Vec3(right, top, front)
        quad(lbf, rbf, rtf, ltf, color)
        quad(rbb, lbb, ltb, rtb, color)
        quad(rbf, rbb, rtb, rtf, color)
        quad(lbb, lbf, ltf, ltb, color)
        quad(ltf, rtf, rtb, ltb, color)
        quad(lbb, rbb, rbf, lbf, color)
    }

    /** Cylindre facetté dont l'axe suit X, pratique pour les roues. */
    fun cylinderX(centerX: Float, centerY: Float, centerZ: Float, length: Float, radius: Float, sides: Int, color: FloatArray) {
        val x0 = centerX - length * 0.5f
        val x1 = centerX + length * 0.5f
        repeat(sides) { side ->
            val angle0 = side.toFloat() / sides * 2f * PI.toFloat()
            val angle1 = (side + 1).toFloat() / sides * 2f * PI.toFloat()
            val y0 = centerY + cos(angle0) * radius
            val z0 = centerZ + sin(angle0) * radius
            val y1 = centerY + cos(angle1) * radius
            val z1 = centerZ + sin(angle1) * radius
            quad(Vec3(x0, y0, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y0, z0), color)
            triangle(Vec3(x0, centerY, centerZ), Vec3(x0, y1, z1), Vec3(x0, y0, z0), color)
            triangle(Vec3(x1, centerY, centerZ), Vec3(x1, y0, z0), Vec3(x1, y1, z1), color)
        }
    }

    /** Cylindre facetté vertical pour les cheminées, poignées et petits piliers. */
    fun cylinderY(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        height: Float,
        radius: Float,
        sides: Int,
        color: FloatArray
    ) {
        val bottom = centerY - height * 0.5f
        val top = centerY + height * 0.5f
        repeat(sides) { side ->
            val angle0 = side.toFloat() / sides * 2f * PI.toFloat()
            val angle1 = (side + 1).toFloat() / sides * 2f * PI.toFloat()
            val x0 = centerX + cos(angle0) * radius
            val z0 = centerZ + sin(angle0) * radius
            val x1 = centerX + cos(angle1) * radius
            val z1 = centerZ + sin(angle1) * radius
            quad(Vec3(x0, bottom, z0), Vec3(x0, top, z0), Vec3(x1, top, z1), Vec3(x1, bottom, z1), color)
            triangle(Vec3(centerX, top, centerZ), Vec3(x1, top, z1), Vec3(x0, top, z0), color)
            triangle(Vec3(centerX, bottom, centerZ), Vec3(x0, bottom, z0), Vec3(x1, bottom, z1), color)
        }
    }

    fun coneY(
        centerX: Float,
        bottomY: Float,
        centerZ: Float,
        height: Float,
        radius: Float,
        sides: Int,
        color: FloatArray
    ) {
        val apex = Vec3(centerX, bottomY + height, centerZ)
        repeat(sides) { side ->
            val angle0 = side.toFloat() / sides * 2f * PI.toFloat()
            val angle1 = (side + 1).toFloat() / sides * 2f * PI.toFloat()
            val base0 = Vec3(centerX + cos(angle0) * radius, bottomY, centerZ + sin(angle0) * radius)
            val base1 = Vec3(centerX + cos(angle1) * radius, bottomY, centerZ + sin(angle1) * radius)
            triangle(apex, base1, base0, color)
            triangle(Vec3(centerX, bottomY, centerZ), base0, base1, color)
        }
    }

    /** Ellipsoïde volontairement peu facetté : rond et mignon, mais très léger. */
    fun lowPolyEllipsoid(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radiusX: Float,
        radiusY: Float,
        radiusZ: Float,
        rings: Int,
        sides: Int,
        color: FloatArray
    ) {
        fun point(ring: Int, side: Int): Vec3 {
            val latitude = ring.toFloat() / rings * PI.toFloat()
            val longitude = side.toFloat() / sides * 2f * PI.toFloat()
            return Vec3(
                centerX + sin(latitude) * cos(longitude) * radiusX,
                centerY + cos(latitude) * radiusY,
                centerZ + sin(latitude) * sin(longitude) * radiusZ
            )
        }
        repeat(rings) { ring ->
            repeat(sides) { side ->
                quad(
                    point(ring, side),
                    point(ring, side + 1),
                    point(ring + 1, side + 1),
                    point(ring + 1, side),
                    color
                )
            }
        }
    }

    fun build(): ColoredMesh {
        // Batch by layer without changing the vertex format used by every existing mesh.
        val sorted = FloatArray(values.size)
        val layers = ArrayList<MeshLayer>()
        var offset = 0
        for ((priority, runs) in layerRuns.groupBy { it.priority }.toSortedMap()) {
            val start = offset
            for (run in runs) {
                for (i in run.firstVertex * 10 until (run.firstVertex + run.vertexCount) * 10)
                    sorted[offset++] = values[i]
            }
            layers += MeshLayer(start / 10, (offset - start) / 10, priority)
        }
        return ColoredMesh.from(sorted, layers)
    }

    private fun vertex(position: Vec3, normal: Vec3, color: FloatArray) {
        val p = placement
        if (p == null) {
            values += position.x; values += position.y; values += position.z
            values += normal.x; values += normal.y; values += normal.z
        } else {
            values += p.x + p.rotatedX(position.x, position.z) * p.scale
            values += p.y + position.y * p.scale
            values += p.z + p.rotatedZ(position.x, position.z) * p.scale
            values += p.rotatedX(normal.x, normal.z)
            values += normal.y
            values += p.rotatedZ(normal.x, normal.z)
        }
        values += color[0]; values += color[1]; values += color[2]; values += color.getOrElse(3) { 1f }
    }

    private fun cross(a: Vec3, b: Vec3) = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )
}

internal object PrototypeMeshFactory {
    private val ROAD = color(0.43f, 0.48f, 0.57f)
    private val ROAD_LIGHT = color(0.48f, 0.53f, 0.62f)
    private val ROAD_SIDE = color(0.33f, 0.38f, 0.47f)
    private val ROAD_UNDERSIDE = color(0.25f, 0.29f, 0.37f)
    private val HOUSE_DUCT = color(0.53f, 0.64f, 0.69f)
    private val HOUSE_DUCT_LIGHT = color(0.66f, 0.76f, 0.80f)
    private val HOUSE_PLANK = color(0.72f, 0.49f, 0.29f)
    private val HOUSE_PLANK_LIGHT = color(0.84f, 0.63f, 0.39f)
    private val CREAM = color(1.00f, 0.89f, 0.63f)
    private val PINK = color(0.98f, 0.48f, 0.58f)
    private val MINT = color(0.43f, 0.85f, 0.70f)
    private val LAVENDER = color(0.68f, 0.58f, 0.92f)
    private val SKY = color(0.52f, 0.78f, 0.98f)
    private val FLOOR = color(0.84f, 0.72f, 0.59f)
    private val DARK = color(0.16f, 0.18f, 0.24f)
    private val WINDOW = color(0.52f, 0.80f, 0.91f)
    private val WALL_PINK = color(0.96f, 0.82f, 0.84f)
    private val WALL_CREAM = color(1.00f, 0.93f, 0.78f)
    private val TEDDY = color(0.72f, 0.50f, 0.34f)
    private val TEDDY_LIGHT = color(0.93f, 0.73f, 0.51f)

    fun world(
        world: ToyboxWorld,
        preview: ToyboxVolume? = null,
        rotationAxis: ToyboxRotationAxis = ToyboxRotationAxis.YAW,
        surfacePriorityStart: Int = 1
    ): ColoredMesh {
        val builder = MeshBuilder(surfacePriorityStart)
        world.volumes.forEach { addWorldVolume(builder, it, alpha = 1f) }
        world.trackSections.flatMap { it.meshSections }.forEach { addWorldTrackSection(builder, it, alpha = 1f) }
        world.decorations.mapNotNull { it.placement() }.forEach { DecorMeshFactory.add(builder, it) }
        if (preview != null) {
            addWorldVolume(builder, preview, alpha = 0.54f)
            addRotationGizmo(builder, preview, rotationAxis)
        }
        return builder.build()
    }

    fun track(track: PrototypeTrack): ColoredMesh {
        val builder = MeshBuilder()
        val samples = track.allSamples()
        fun segmentMiddleDistance(index: Int): Float {
            val next = (index + 1) % samples.size
            return if (next == 0) track.length else {
                (samples[index].distance + samples[next].distance) * 0.5f
            }
        }
        samples.indices.forEach { index ->
            val nextIndex = (index + 1) % samples.size
            val a = samples[index]
            val b = samples[nextIndex]
            val middleDistance = segmentMiddleDistance(index)
            val middle = track.sampleAt(middleDistance)
            if (!track.hasDeck(middle)) {
                // Sur parquet et mobilier : des chevrons espacés guident la
                // course, sans masquer les vraies surfaces sous un ruban.
                if (track.scene.circuit.usesFurnitureLayout && index % 12 == 0) {
                    val p = middle.position + Vec3(0f, PrototypeTrack.ROAD_SURFACE_LIFT + .025f, 0f)
                    val forward = Vec3(middle.tangent.x, 0f, middle.tangent.z).normalized()
                    val right = middle.right
                    for (side in intArrayOf(-1, 1)) {
                        val outer = p - forward * .7f + right * (side * 1.25f)
                        val tip = p + forward * .6f
                        builder.quad(outer, tip, tip - forward * .3f, outer - forward * .3f, DARK)
                        // Les deux sens d'enroulement garantissent la visibilité des deux branches.
                        builder.quad(outer - forward * .3f, tip - forward * .3f, tip, outer, DARK)
                    }
                }
                return@forEach
            }

            val aHalf = a.roadWidth * 0.5f
            val bHalf = b.roadWidth * 0.5f
            val lift = Vec3(0f, PrototypeTrack.ROAD_SURFACE_LIFT, 0f)
            val aLeft = a.position - a.right * aHalf + lift
            val aRight = a.position + a.right * aHalf + lift
            val bLeft = b.position - b.right * bHalf + lift
            val bRight = b.position + b.right * bHalf + lift
            val roadColor = if (track.scene.circuit.usesHouseLayout) {
                houseCourseColor(middle.fraction, index)
            } else if (track.scene.circuit.usesFurnitureLayout)
                rgb(if ((index / 12) % 2 == 0) 0xDAB68B else 0xCBA47C)
                else if ((index / 12) % 2 == 0) ROAD else ROAD_LIGHT
            builder.quad(aLeft, bLeft, bRight, aRight, roadColor)

            val curbWidth = if (track.scene.circuit.usesHouseLayout) PrototypeTrack.CURB_WIDTH * 2.6f
                else PrototypeTrack.CURB_WIDTH
            val curbColor = if (track.scene.circuit.usesHouseLayout) {
                if (middle.fraction in 0.10f..0.21f || middle.fraction in 0.68f..0.79f)
                    HOUSE_DUCT_LIGHT else CREAM
            } else if ((index / 8) % 2 == 0) CREAM else PINK
            val aLeftOutside = a.position - a.right * (aHalf + curbWidth) + lift
            val bLeftOutside = b.position - b.right * (bHalf + curbWidth) + lift
            builder.quad(aLeftOutside, bLeftOutside, bLeft, aLeft, curbColor)
            val aRightOutside = a.position + a.right * (aHalf + curbWidth) + lift
            val bRightOutside = b.position + b.right * (bHalf + curbWidth) + lift
            builder.quad(aRight, bRight, bRightOutside, aRightOutside, curbColor)

            // La route est une dalle et non une simple feuille : dessous sombre,
            // flancs visibles et bouchons aux deux bords du vide du tremplin.
            val thickness = PrototypeTrack.ROAD_THICKNESS
            val down = Vec3(0f, -thickness, 0f)
            val aLeftBottom = aLeftOutside + down
            val aRightBottom = aRightOutside + down
            val bLeftBottom = bLeftOutside + down
            val bRightBottom = bRightOutside + down
            val underside = if (track.scene.circuit.usesHouseLayout) DARK else ROAD_UNDERSIDE
            val sideColor = if (track.scene.circuit.usesHouseLayout &&
                (middle.fraction in 0.10f..0.21f || middle.fraction in 0.68f..0.79f)) HOUSE_DUCT
                else ROAD_SIDE
            builder.quad(aRightBottom, bRightBottom, bLeftBottom, aLeftBottom, underside)
            builder.quad(aLeftOutside, aLeftBottom, bLeftBottom, bLeftOutside, sideColor)
            builder.quad(aRightOutside, bRightOutside, bRightBottom, aRightBottom, sideColor)

            val previousIndex = (index - 1 + samples.size) % samples.size
            if (!track.hasDeck(track.sampleAt(segmentMiddleDistance(previousIndex)))) {
                builder.quad(aLeftOutside, aRightOutside, aRightBottom, aLeftBottom, ROAD_SIDE)
            }
            if (!track.hasDeck(track.sampleAt(segmentMiddleDistance(nextIndex)))) {
                builder.quad(bRightOutside, bLeftOutside, bLeftBottom, bRightBottom, ROAD_SIDE)
            }
        }
        return builder.build()
    }

    private fun houseCourseColor(fraction: Float, index: Int): FloatArray = when {
        fraction in 0.10f..0.21f || fraction in 0.34f..0.45f ||
            fraction in 0.68f..0.79f || fraction >= 0.88f ->
            if ((index / 10) % 2 == 0) HOUSE_DUCT else HOUSE_DUCT_LIGHT
        fraction in 0.22f..0.31f || fraction in 0.55f..0.64f ->
            if ((index / 8) % 2 == 0) HOUSE_PLANK else HOUSE_PLANK_LIGHT
        else -> if ((index / 14) % 2 == 0) FLOOR else CREAM
    }

    fun environment(track: PrototypeTrack): ColoredMesh {
        val builder = MeshBuilder()
        if (track.scene.circuit.usesHouseLayout) {
            addHouseFloors(builder)
        } else {
            val theme = RoomThemes.theme(track.scene.room)
            addTerrain(builder, theme)
            if (track.scene.room == RoomKind.BEDROOM) builder.surface { addPatchworkRug(builder, track) }
            if (theme.floor == FloorKind.TILES) addTiledFloor(builder, theme)
            if (track.scene.room == RoomKind.LIVING_ROOM || track.scene.room == RoomKind.OFFICE) {
                builder.box(0f, 0.012f, 0f, 90f, 0.02f, 52f, rgb(theme.accent))
                builder.box(0f, 0.023f, 0f, 84f, 0.002f, 46f, rgb(theme.wall))
            }
            if (track.scene.room == RoomKind.GARAGE) {
                for (x in floatArrayOf(-40f, 40f)) builder.box(x, .012f, 0f, .5f, .02f, 90f, CREAM)
                for (z in floatArrayOf(-45f, 45f)) builder.box(0f, .012f, z, 80f, .02f, .5f, CREAM)
            }
            addRoomWalls(builder, theme)
        }
        addFurniture(builder, track)
        addToyModels(builder, track)
        track.decorations.forEach { DecorMeshFactory.add(builder, it) }
        return builder.build()
    }

    /** Les mêmes planchers finis alimentent le rendu et les contacts. */
    private fun addHouseFloors(builder: MeshBuilder) {
        for (box in HouseGeometry.floorBoxes()) {
            builder.box(box.x, box.y, box.z, box.width, box.height, box.depth, rgb(box.color))
        }
    }

    private fun addWorldTrackSection(builder: MeshBuilder, section: ToyboxTrackSection, alpha: Float) {
        val road = rgba(section.color, alpha)
        val curbA = rgba(0xFFFFE7A8.toInt(), alpha)
        val curbB = rgba(0xFFFF7B8A.toInt(), alpha)
        val side = rgba(0xFF394456.toInt(), alpha)
        val underside = rgba(0xFF252C38.toInt(), alpha)
        val thickness = PrototypeTrack.ROAD_THICKNESS
        val lift = Vec3(0f, PrototypeTrack.ROAD_SURFACE_LIFT, 0f)
        val leftBack = section.corner(-1f, -1f).toVec3() + lift
        val rightBack = section.corner(-1f, 1f).toVec3() + lift
        val leftFront = section.corner(1f, -1f).toVec3() + lift
        val rightFront = section.corner(1f, 1f).toVec3() + lift
        builder.quad(leftBack, leftFront, rightFront, rightBack, road)

        fun surface(t: Float, s: Float): Vec3 =
            (leftBack + (leftFront-leftBack)*t) * (1f-s) +
                (rightBack + (rightFront-rightBack)*t) * s + Vec3(0f, 0.009f, 0f)
        fun mark(a: Float, b: Float, l: Float, r: Float, color: Int) {
            builder.quad(surface(a,l), surface(b,l), surface(b,r), surface(a,r), rgba(color, alpha))
        }
        val accent = section.style.accent
        val tiles = kotlin.math.ceil(section.length / 1.5f).toInt().coerceIn(1, 128)
        repeat(tiles) { index ->
            val a = index.toFloat()/tiles
            val b = (index+1f)/tiles
            val span = b-a
            when (section.style) {
                TrackStyle.CLASSIC -> mark(a+span*.15f, b-span*.15f, .49f, .51f, 0xFFFFE7A8.toInt())
                TrackStyle.WOOD -> {
                    mark(a, a+span*.06f, 0f, 1f, accent)
                    mark(a+span*.12f, b-span*.08f, .08f, .085f, accent)
                    mark(a+span*.12f, b-span*.08f, .915f, .92f, accent)
                }
                TrackStyle.NEON -> {
                    mark(a, b, .03f, .055f, accent)
                    mark(a, b, .945f, .97f, 0xFFFF5ADA.toInt())
                    mark(a+span*.3f, b-span*.3f, .48f, .52f, accent)
                }
                TrackStyle.ICE -> {
                    builder.quad(surface(a,.14f), surface(b,.7f), surface(b,.715f), surface(a,.155f), rgba(accent, alpha))
                    mark(a+span*.4f, b, .82f, .83f, accent)
                }
                TrackStyle.SAND -> for (lane in 0..3) {
                    val s = .1f + lane*.23f
                    builder.quad(surface(a,s), surface(b,s+.07f), surface(b,s+.09f), surface(a,s+.02f), rgba(accent, alpha))
                }
                TrackStyle.DIRT, TrackStyle.GRASS -> {
                    val seed = kotlin.math.abs((section.id xor (index * 7919L)).toInt() % 97)
                    for (lane in 0..5) {
                        val s = .04f + lane*.16f
                        val t = a + span * ((seed + lane*17) % 70) / 100f
                        mark(t, (t+span*.18f).coerceAtMost(b), s, s+.018f, accent)
                    }
                    if (section.style == TrackStyle.DIRT) {
                        mark(a,b,.22f,.27f,0xFF795033.toInt())
                        mark(a,b,.73f,.78f,0xFF795033.toInt())
                    }
                }
            }
        }

        val curbWidth = PrototypeTrack.CURB_WIDTH * 1.6f
        fun edge(sideSign: Float, innerBack: Vec3, innerFront: Vec3, color: FloatArray) {
            val outerBack = innerBack + (rightBack - leftBack).normalized() * (sideSign * curbWidth)
            val outerFront = innerFront + (rightFront - leftFront).normalized() * (sideSign * curbWidth)
            if (sideSign < 0f) builder.quad(outerBack, outerFront, innerFront, innerBack, color)
            else builder.quad(innerBack, innerFront, outerFront, outerBack, color)
            val down = Vec3(0f, -thickness, 0f)
            builder.quad(outerFront, outerBack, outerBack + down, outerFront + down, side)
        }
        edge(-1f, leftBack, leftFront, if (section.style == TrackStyle.CLASSIC) curbA else rgba(accent,alpha))
        edge(1f, rightBack, rightFront, if (section.style == TrackStyle.CLASSIC) curbB else rgba(accent,alpha))

        fun barrier(back: Vec3, front: Vec3, sign: Float) {
            val outsideBack = back + (rightBack-leftBack).normalized() * (sign*TrackStyle.BARRIER_THICKNESS)
            val outsideFront = front + (rightFront-leftFront).normalized() * (sign*TrackStyle.BARRIER_THICKNESS)
            val up = Vec3(0f, TrackStyle.BARRIER_HEIGHT, 0f)
            val wall = rgba(accent,alpha)
            if (sign < 0f) {
                builder.quad(back,back+up,front+up,front,wall)
                builder.quad(outsideFront,outsideFront+up,outsideBack+up,outsideBack,side)
                builder.quad(back+up,outsideBack+up,outsideFront+up,front+up,wall)
            } else {
                builder.quad(front,front+up,back+up,back,wall)
                builder.quad(outsideBack,outsideBack+up,outsideFront+up,outsideFront,side)
                builder.quad(front+up,outsideFront+up,outsideBack+up,back+up,wall)
            }
            builder.quad(back,outsideBack,outsideBack+up,back+up,wall)
            builder.quad(front,front+up,outsideFront+up,outsideFront,wall)
        }
        if (section.barriers and 1 != 0) barrier(leftBack,leftFront,-1f)
        if (section.barriers and 2 != 0) barrier(rightBack,rightFront,1f)

        val down = Vec3(0f, -thickness, 0f)
        builder.quad(rightFront + down, leftFront + down, leftBack + down, rightBack + down, underside)
        builder.quad(leftBack, rightBack, rightBack + down, leftBack + down, side)
        builder.quad(rightFront, leftFront, leftFront + down, rightFront + down, side)
    }

    private fun com.Atom2Universe.app.games.toyboxracers.editor.VolumePoint.toVec3() = Vec3(x, y, z)

    private fun addWorldVolume(builder: MeshBuilder, volume: ToyboxVolume, alpha: Float) {
        val color = rgba(volume.color, alpha)
        when (volume.kind) {
            ToyboxVolumeKind.RAMP -> addWorldRamp(builder, volume, color)
            ToyboxVolumeKind.STAIR -> addWorldStairs(builder, volume, color)
            ToyboxVolumeKind.WINDOW -> {
                addOrientedBox(builder, volume, 0f, 0f, 0f, volume.width, volume.height, volume.depth, color)
                addOrientedBox(builder, volume, 0f, 0f, 0f, volume.width * 0.92f, volume.height * 0.08f, volume.depth + 0.08f, CREAM)
                addOrientedBox(builder, volume, 0f, 0f, 0f, volume.width * 0.08f, volume.height * 0.92f, volume.depth + 0.08f, CREAM)
            }
            ToyboxVolumeKind.DOOR -> {
                addOrientedBox(builder, volume, 0f, 0f, 0f, volume.width, volume.height, volume.depth, color)
                builder.cylinderY(
                    volume.worldX(volume.width * 0.34f, volume.depth * 0.52f),
                    volume.y,
                    volume.worldZ(volume.width * 0.34f, volume.depth * 0.52f),
                    0.42f,
                    0.18f,
                    8,
                    CREAM
                )
            }
            ToyboxVolumeKind.RAIL -> {
                addOrientedBox(builder, volume, 0f, volume.height * 0.38f, 0f, volume.width, volume.height * 0.18f, volume.depth, color)
                addOrientedBox(builder, volume, 0f, -volume.height * 0.38f, 0f, volume.width, volume.height * 0.14f, volume.depth, color)
                val posts = maxOf(2, (volume.width / 6f).toInt() + 1)
                repeat(posts) { index ->
                    val t = if (posts == 1) 0.5f else index.toFloat() / (posts - 1)
                    val localX = -volume.width * 0.5f + volume.width * t
                    addOrientedBox(builder, volume, localX, 0f, 0f, 0.45f, volume.height, volume.depth, color)
                }
            }
            else -> addOrientedBox(builder, volume, 0f, 0f, 0f, volume.width, volume.height, volume.depth, color)
        }
    }

    private fun addRotationGizmo(builder: MeshBuilder, volume: ToyboxVolume, axis: ToyboxRotationAxis) {
        val radius = maxOf(volume.width, volume.height, volume.depth).coerceAtLeast(3.2f) * 0.52f
        val thickness = maxOf(0.035f, radius * 0.015f)
        addRotationRing(builder, volume, ToyboxRotationAxis.YAW, axis, radius, thickness)
        addRotationRing(builder, volume, ToyboxRotationAxis.PITCH, axis, radius, thickness)
        addRotationRing(builder, volume, ToyboxRotationAxis.ROLL, axis, radius, thickness)
    }

    private fun addRotationRing(
        builder: MeshBuilder,
        volume: ToyboxVolume,
        ringAxis: ToyboxRotationAxis,
        activeAxis: ToyboxRotationAxis,
        radius: Float,
        thickness: Float
    ) {
        val color = when (ringAxis) {
            ToyboxRotationAxis.YAW -> ringColor(0.20f, 1f, 0.35f, ringAxis == activeAxis)
            ToyboxRotationAxis.PITCH -> ringColor(1f, 0.24f, 0.15f, ringAxis == activeAxis)
            ToyboxRotationAxis.ROLL -> ringColor(0.20f, 0.52f, 1f, ringAxis == activeAxis)
        }
        val segments = 72
        for (index in 0 until segments) {
            val start = (index.toFloat() / segments) * (PI.toFloat() * 2f)
            val end = ((index + 1f) / segments) * (PI.toFloat() * 2f)
            val innerStart = ringPoint(volume, ringAxis, start, radius - thickness)
            val innerEnd = ringPoint(volume, ringAxis, end, radius - thickness)
            val outerEnd = ringPoint(volume, ringAxis, end, radius + thickness)
            val outerStart = ringPoint(volume, ringAxis, start, radius + thickness)
            builder.quad(innerStart, innerEnd, outerEnd, outerStart, color)
        }
    }

    private fun ringPoint(volume: ToyboxVolume, axis: ToyboxRotationAxis, angle: Float, radius: Float): Vec3 {
        val c = cos(angle) * radius
        val s = sin(angle) * radius
        val point = when (axis) {
            ToyboxRotationAxis.YAW -> volume.worldPoint(c, 0f, s)
            ToyboxRotationAxis.PITCH -> volume.worldPoint(0f, c, s)
            ToyboxRotationAxis.ROLL -> volume.worldPoint(c, s, 0f)
        }
        return Vec3(point.x, point.y, point.z)
    }

    private fun ringColor(r: Float, g: Float, b: Float, active: Boolean): FloatArray {
        val strength = if (active) 1f else 0.72f
        val alpha = if (active) 0.96f else 0.34f
        return floatArrayOf(r * strength, g * strength, b * strength, alpha)
    }

    private fun addOrientedBox(
        builder: MeshBuilder,
        volume: ToyboxVolume,
        localCenterX: Float,
        localCenterY: Float,
        localCenterZ: Float,
        sizeX: Float,
        sizeY: Float,
        sizeZ: Float,
        color: FloatArray
    ) {
        val left = localCenterX - sizeX * 0.5f
        val right = localCenterX + sizeX * 0.5f
        val bottom = volume.y + localCenterY - sizeY * 0.5f
        val top = volume.y + localCenterY + sizeY * 0.5f
        val back = localCenterZ - sizeZ * 0.5f
        val front = localCenterZ + sizeZ * 0.5f
        fun p(localX: Float, y: Float, localZ: Float): Vec3 {
            val point = volume.worldPoint(localX, y - volume.y, localZ)
            return Vec3(point.x, point.y, point.z)
        }
        val lbb = p(left, bottom, back)
        val rbb = p(right, bottom, back)
        val ltb = p(left, top, back)
        val rtb = p(right, top, back)
        val lbf = p(left, bottom, front)
        val rbf = p(right, bottom, front)
        val ltf = p(left, top, front)
        val rtf = p(right, top, front)
        builder.quad(lbf, rbf, rtf, ltf, color)
        builder.quad(rbb, lbb, ltb, rtb, color)
        builder.quad(rbf, rbb, rtb, rtf, color)
        builder.quad(lbb, lbf, ltf, ltb, color)
        builder.quad(ltf, rtf, rtb, ltb, color)
        builder.quad(lbb, rbb, rbf, lbf, color)
    }

    private fun addWorldRamp(builder: MeshBuilder, volume: ToyboxVolume, color: FloatArray) {
        val left = -volume.width * 0.5f
        val right = volume.width * 0.5f
        val back = -volume.depth * 0.5f
        val front = volume.depth * 0.5f
        val bottom = volume.y - volume.height * 0.5f
        val top = volume.y + volume.height * 0.5f
        fun p(localX: Float, y: Float, localZ: Float): Vec3 {
            val point = volume.worldPoint(localX, y - volume.y, localZ)
            return Vec3(point.x, point.y, point.z)
        }
        val lbb = p(left, bottom, back)
        val rbb = p(right, bottom, back)
        val lbf = p(left, bottom, front)
        val rbf = p(right, bottom, front)
        val ltf = p(left, top, front)
        val rtf = p(right, top, front)
        builder.quad(lbf, rbf, rtf, ltf, color)
        builder.triangle(lbb, lbf, ltf, color)
        builder.triangle(rbb, rtf, rbf, color)
        builder.quad(lbb, rbb, rbf, lbf, color)
        builder.quad(lbb, ltf, rtf, rbb, color)
    }

    private fun addWorldStairs(builder: MeshBuilder, volume: ToyboxVolume, color: FloatArray) {
        val steps = maxOf(2, (volume.depth / 3f).toInt())
        val stepDepth = volume.depth / steps
        val bottom = volume.y - volume.height * 0.5f
        repeat(steps) { index ->
            val h = volume.height * (index + 1) / steps
            val localZ = -volume.depth * 0.5f + stepDepth * (index + 0.5f)
            addOrientedBox(
                builder,
                volume,
                0f,
                bottom + h * 0.5f - volume.y,
                localZ,
                volume.width,
                h,
                stepDepth,
                color
            )
        }
    }

    private fun rgb(value: Int) = color(((value shr 16) and 255) / 255f,
        ((value shr 8) and 255) / 255f, (value and 255) / 255f)

    private fun rgba(value: Int, alpha: Float) = floatArrayOf(
        ((value shr 16) and 255) / 255f,
        ((value shr 8) and 255) / 255f,
        (value and 255) / 255f,
        alpha
    )

    private fun addTerrain(builder: MeshBuilder, theme: RoomTheme) {
        builder.box(0f, -0.40f, 0f, 236f, 0.8f, 150f, rgb(theme.floorColor))
        if (theme.floor != FloorKind.PARQUET) return
        // Joints de parquet en géométrie, sans texture ni chargement externe.
        val seam = color(0.73f, 0.60f, 0.47f)
        for (row in -7..7) {
            builder.box(0f, 0.003f, row * 10f, 236f, 0.005f, 0.065f, seam)
            for (column in -3..3) {
                val x = column * 32f + if (row % 2 == 0) 0f else 16f
                builder.box(x, 0.003f, row * 10f + 5f, 0.065f, 0.005f, 10f, seam)
            }
        }
    }

    private fun addTiledFloor(builder: MeshBuilder, theme: RoomTheme) {
        for (column in 0 until 20) for (row in 0 until 12) {
            builder.box(-118f + (column + 0.5f) * 11.8f, 0.006f, -75f + (row + 0.5f) * 12.5f,
                11.65f, 0.008f, 12.35f,
                rgb(if ((column + row) % 2 == 0) theme.floorColor else theme.accent))
        }
    }

    private fun addFurniture(builder: MeshBuilder, track: PrototypeTrack) {
        for (part in track.roomBoxes) {
            val c = part.color
            builder.box(part.x, part.y, part.z, part.width, part.height, part.depth,
                color(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f))
        }
        if (track.scene.room != RoomKind.BEDROOM || track.scene.circuit.usesHouseLayout) return
        // Ciel illustré derrière les croisillons : nuages en relief très aplati.
        for (x in floatArrayOf(-21f, 25f)) {
            builder.lowPolyEllipsoid(x + 6f, 24.8f, -74.35f, 1.6f, 1.6f, 0.12f, 6, 12, CREAM)
            for (i in 0..2) {
                builder.lowPolyEllipsoid(x - 7f + i * 2f, 23.4f + (i % 2) * 0.6f, -74.35f,
                    1.8f, 0.9f, 0.12f, 5, 10, color(0.97f, 0.98f, 1f))
            }
        }
        // Lampe champignon et pot à crayons sur le bureau.
        builder.cylinderY(-7.5f, 11.3f, -68f, 0.5f, 2f, 14, LAVENDER)
        builder.cylinderY(-7.5f, 13.6f, -68f, 4.2f, 0.3f, 10, CREAM)
        builder.lowPolyEllipsoid(-7.5f, 16f, -68f, 2.8f, 1.5f, 2.8f, 6, 14, PINK)
        builder.cylinderY(-7.5f, 15.5f, -68f, 0.18f, 2.45f, 14, CREAM)
        builder.cylinderY(-24f, 12.1f, -69f, 2.1f, 1.2f, 10, MINT)
        for (i in 0..4) {
            val a = i * 2f * PI.toFloat() / 5f
            val x = -24f + cos(a) * 0.7f
            val z = -69f + sin(a) * 0.7f
            val h = 2.4f + (i % 3) * 0.4f
            val c = arrayOf(PINK, SKY, CREAM, LAVENDER, MINT)[i]
            builder.cylinderY(x, 12.5f + h * 0.5f, z, h, 0.13f, 6, c)
            builder.coneY(x, 12.5f + h, z, 0.5f, 0.13f, 6, FLOOR)
        }
    }

    private fun addPatchworkRug(builder: MeshBuilder, track: PrototypeTrack) {
        val left = -54f
        val right = 54f
        val back = -36f
        val front = 36f
        val columns = 18
        val rows = 12
        val colors = arrayOf(PINK, MINT, LAVENDER, SKY, CREAM)
        repeat(columns) { column ->
            val x0 = left + (right - left) * column / columns
            val x1 = left + (right - left) * (column + 1) / columns
            repeat(rows) { row ->
                val z0 = back + (front - back) * row / rows
                val z1 = back + (front - back) * (row + 1) / rows
                val border = column == 0 || row == 0 || column == columns - 1 || row == rows - 1
                val rugColor = if (border) CREAM else colors[(column * 2 + row * 3) % colors.size]
                fun rugPoint(x: Float, z: Float) = Vec3(x, track.groundHeightAt(x, z) + 0.018f, z)
                builder.quad(rugPoint(x0, z0), rugPoint(x0, z1), rugPoint(x1, z1), rugPoint(x1, z0), rugColor)
            }
        }
    }

    private fun addRoomWalls(builder: MeshBuilder, theme: RoomTheme) {
        val halfWidth = PrototypeTrack.ROOM_HALF_WIDTH
        val halfDepth = PrototypeTrack.ROOM_HALF_DEPTH
        val height = PrototypeTrack.ROOM_WALL_HEIGHT
        val thickness = 1.5f
        // Les volumes commencent exactement au bord jouable : le visuel et la
        // collision correspondent, sans mur invisible placé avant la plinthe.
        builder.box(0f, height * 0.5f, -halfDepth - thickness * 0.5f, halfWidth * 2f + thickness * 2f, height, thickness, rgb(theme.accent))
        builder.box(0f, height * 0.5f, halfDepth + thickness * 0.5f, halfWidth * 2f + thickness * 2f, height, thickness, rgb(theme.wall))
        builder.box(-halfWidth - thickness * 0.5f, height * 0.5f, 0f, thickness, height, halfDepth * 2f, rgb(theme.wall))
        builder.box(halfWidth + thickness * 0.5f, height * 0.5f, 0f, thickness, height, halfDepth * 2f, rgb(theme.accent))
        builder.box(0f, 0.65f, -halfDepth + 0.22f, halfWidth * 2f, 1.3f, 0.44f, CREAM)
        builder.box(0f, 0.65f, halfDepth - 0.22f, halfWidth * 2f, 1.3f, 0.44f, CREAM)
        builder.box(-halfWidth + 0.22f, 0.65f, 0f, 0.44f, 1.3f, halfDepth * 2f, CREAM)
        builder.box(halfWidth - 0.22f, 0.65f, 0f, 0.44f, 1.3f, halfDepth * 2f, CREAM)
    }

    private fun addToyModels(builder: MeshBuilder, track: PrototypeTrack) {
        for (toy in track.toyObstacles) {
            val ground = track.groundHeightAt(toy.x, toy.z)
            when (toy.kind) {
                ToyKind.BLOCKS -> addBlocks(builder, toy.x, ground, toy.z)
                ToyKind.TEDDY -> addTeddy(builder, toy.x, ground, toy.z)
                ToyKind.TRAIN -> addTrain(builder, toy.x, ground, toy.z)
                ToyKind.SPINNING_TOP -> addSpinningTop(builder, toy.x, ground, toy.z)
            }
        }
    }

    private fun addBlocks(builder: MeshBuilder, x: Float, ground: Float, z: Float) {
        builder.box(x - 2.2f, ground + 1.6f, z, 3.2f, 3.2f, 3.2f, PINK)
        builder.box(x + 1.6f, ground + 1.9f, z + 0.8f, 3.8f, 3.8f, 3.8f, SKY)
        builder.box(x - 0.3f, ground + 4.9f, z + 0.2f, 3.4f, 3.0f, 3.4f, MINT)
        builder.box(x + 3.5f, ground + 1.3f, z - 2.8f, 2.6f, 2.6f, 2.6f, CREAM)
    }

    private fun addTeddy(builder: MeshBuilder, x: Float, ground: Float, z: Float) {
        fun oval(dx: Float, y: Float, dz: Float, rx: Float, ry: Float, rz: Float, c: FloatArray) =
            builder.lowPolyEllipsoid(x + dx, ground + y, z + dz, rx, ry, rz, 7, 12, c)
        // Bassin posé au sol, ventre rond et pattes avancées : silhouette assise.
        oval(0f, 2.6f, -0.3f, 2.8f, 2.6f, 2.2f, TEDDY)
        oval(0f, 4f, 0f, 2.45f, 2.7f, 2.1f, TEDDY)
        oval(0f, 3.6f, 1.8f, 1.65f, 1.85f, 0.5f, TEDDY_LIGHT)
        for (side in intArrayOf(-1, 1)) {
            oval(side * 2.1f, 1.3f, 2f, 1.55f, 1.3f, 2.05f, TEDDY)
            oval(side * 2.1f, 1.35f, 3.95f, 1.08f, 0.9f, 0.32f, TEDDY_LIGHT)
            oval(side * 2.7f, 3.9f, 0.8f, 1.05f, 1.9f, 1.05f, TEDDY)
            oval(side * 2.65f, 2.65f, 1.4f, 0.7f, 0.65f, 0.6f, TEDDY_LIGHT)
            oval(side * 1.9f, 8.75f, 0.1f, 0.95f, 1f, 0.65f, TEDDY)
            oval(side * 1.9f, 8.8f, 0.64f, 0.6f, 0.65f, 0.16f, TEDDY_LIGHT)
        }
        oval(0f, 7.3f, 0.35f, 2.45f, 2.25f, 2.1f, TEDDY)
        oval(0f, 6.8f, 2.15f, 1.25f, 0.85f, 0.65f, TEDDY_LIGHT)
        for (side in intArrayOf(-1, 1)) {
            oval(side * 0.86f, 7.7f, 2.25f, 0.22f, 0.28f, 0.15f, DARK)
            oval(side * 0.86f - 0.05f, 7.8f, 2.37f, 0.065f, 0.075f, 0.045f, CREAM)
        }
        oval(0f, 7.02f, 2.77f, 0.36f, 0.25f, 0.16f, DARK)
        builder.box(x, ground + 6.66f, z + 2.79f, 0.075f, 0.35f, 0.06f, DARK)
        // Nœud lavande et petites coutures du ventre.
        oval(-0.65f, 5.65f, 2.1f, 0.7f, 0.42f, 0.3f, LAVENDER)
        oval(0.65f, 5.65f, 2.1f, 0.7f, 0.42f, 0.3f, LAVENDER)
        oval(0f, 5.65f, 2.3f, 0.3f, 0.32f, 0.25f, PINK)
        for (i in 0..3) builder.box(x, ground + 2.8f + i * 0.4f, z + 2.31f, 0.16f, 0.055f, 0.055f, TEDDY)
    }

    private fun addTrain(builder: MeshBuilder, x: Float, ground: Float, z: Float) {
        builder.box(x, ground + 1.55f, z, 4.8f, 2.2f, 10.2f, SKY)
        builder.box(x, ground + 3.2f, z - 2.4f, 4.4f, 3.2f, 4.0f, PINK)
        builder.box(x, ground + 3.0f, z + 2.2f, 3.7f, 2.7f, 4.8f, CREAM)
        builder.cylinderY(x, ground + 5.0f, z + 3.1f, 2.4f, 0.72f, 9, LAVENDER)
        for (wheelZ in floatArrayOf(z - 3.2f, z + 3.0f)) {
            builder.cylinderX(x, ground + 0.75f, wheelZ, 5.5f, 1.05f, 10, DARK)
            builder.cylinderX(x, ground + 0.75f, wheelZ, 5.62f, 0.48f, 10, MINT)
        }
    }

    private fun addSpinningTop(builder: MeshBuilder, x: Float, ground: Float, z: Float) {
        builder.coneY(x, ground + 0.2f, z, 6.0f, 4.0f, 12, LAVENDER)
        builder.coneY(x, ground + 3.0f, z, 3.2f, 3.0f, 12, PINK)
        builder.cylinderY(x, ground + 7.0f, z, 2.0f, 0.55f, 10, CREAM)
        builder.lowPolyEllipsoid(x, ground + 7.9f, z, 1.1f, 0.65f, 1.1f, 4, 10, MINT)
    }

    fun car(bodyColor: FloatArray = PINK): ColoredMesh {
        val builder = MeshBuilder()
        builder.box(0f, 0.25f, 0f, 0.82f, 0.30f, 1.28f, bodyColor)
        builder.box(0f, 0.48f, -0.05f, 0.62f, 0.30f, 0.60f, CREAM)
        builder.box(0f, 0.51f, 0.19f, 0.53f, 0.15f, 0.06f, WINDOW)
        builder.box(-0.42f, 0.31f, 0.40f, 0.06f, 0.12f, 0.22f, CREAM)
        builder.box(0.42f, 0.31f, 0.40f, 0.06f, 0.12f, 0.22f, CREAM)
        for (z in floatArrayOf(0.46f, -0.46f)) {
            builder.box(0f, 0.19f, z, 0.86f, 0.07f, 0.08f, DARK)
            for (x in floatArrayOf(-0.49f, 0.49f)) {
                builder.cylinderX(x, 0.18f, z, 0.16f, 0.21f, 10, DARK)
                builder.cylinderX(x, 0.18f, z, 0.17f, 0.11f, 10, CREAM)
            }
        }
        return builder.build()
    }

    fun shadow(): ColoredMesh {
        val builder = MeshBuilder()
        val color = floatArrayOf(0.12f, 0.12f, 0.18f, 0.28f)
        val segments = 20
        repeat(segments) { index ->
            val a0 = index.toFloat() / segments * 2f * PI.toFloat()
            val a1 = (index + 1).toFloat() / segments * 2f * PI.toFloat()
            builder.triangle(
                Vec3(0f, 0f, 0f),
                Vec3(cos(a1) * 0.58f, 0f, sin(a1) * 0.72f),
                Vec3(cos(a0) * 0.58f, 0f, sin(a0) * 0.72f),
                color
            )
        }
        return builder.build()
    }

    private fun color(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b, 1f)
}
