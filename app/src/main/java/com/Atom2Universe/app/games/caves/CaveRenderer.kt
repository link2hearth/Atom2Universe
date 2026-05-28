package com.Atom2Universe.app.games.caves

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.render.Camera
import com.Atom2Universe.app.games.caves.render.ChunkMesh
import com.Atom2Universe.app.games.caves.render.ShaderProgram
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.CHUNK_SIZE
import com.Atom2Universe.app.games.caves.world.MeshBuilder
import com.Atom2Universe.app.games.caves.world.STONE
import com.Atom2Universe.app.games.caves.world.World
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

enum class PlayerMode { WALK, SPECTATOR }

internal class CaveRenderer(private val touch: TouchController) : GLSurfaceView.Renderer {

    val camera = Camera(8f, 8f, 8f)
    private val world = World(seed = System.currentTimeMillis())
    private val meshes = ConcurrentHashMap<Long, ChunkMesh>()
    private val uploadQueue = ConcurrentLinkedQueue<Pair<Long, FloatArray>>()

    private var shader: ShaderProgram? = null
    private var aPos = 0; private var aColor = 0; private var uMvp = 0

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val building = ConcurrentHashMap.newKeySet<Long>()

    private var lastCx = Int.MAX_VALUE; private var lastCy = Int.MAX_VALUE; private var lastCz = Int.MAX_VALUE
    private var lastFrameNs = 0L

    // Frustum culling : 6 plans × 4 coefficients (a, b, c, d)
    private val frustum = Array(6) { FloatArray(4) }

    // Nettoyage des meshes orphelins toutes les N frames (pas chaque frame)
    private var cleanupCounter = 0
    private val CLEANUP_INTERVAL = 120

    // Plafond à ~30 fps pour limiter la chauffe
    private val TARGET_FRAME_NS = 33_333_333L

    // Physique (mode marche)
    var playerMode = PlayerMode.WALK
    @Volatile var pendingMode: PlayerMode? = null
    private var velocityY = 0f
    private var onGround = false

    var posCallback: ((String) -> Unit)? = null
    var modeCallback: ((PlayerMode) -> Unit)? = null

    private val VERT = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_color;
        uniform mat4 u_mvp;
        out vec3 v_color;
        out float v_fog;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_color = a_color;
            v_fog = clamp((gl_Position.w - 28.0) / 32.0, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG = """
        #version 300 es
        precision mediump float;
        in vec3 v_color;
        in float v_fog;
        out vec4 fragColor;
        void main() {
            vec3 fog = vec3(0.008, 0.006, 0.015);
            fragColor = vec4(mix(v_color, fog, v_fog), 1.0);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.008f, 0.006f, 0.015f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        lastFrameNs = System.nanoTime()

        shader = ShaderProgram(VERT, FRAG).also {
            it.use()
            aPos   = it.attrib("a_pos")
            aColor = it.attrib("a_color")
            uMvp   = it.uniform("u_mvp")
        }

        // Pré-génère le bloc 3×3×3 autour du spawn de façon synchrone.
        // Garantit que le joueur a du sol solide et des murs réels dès la première frame,
        // sans attendre la génération asynchrone des voisins.
        for (dy in -1..1) for (dz in -1..1) for (dx in -1..1)
            world.pregenerateChunk(dx, dy, dz)

        val spawn = world.findSpawnPoint()
        camera.x = spawn[0]; camera.y = spawn[1]; camera.z = spawn[2]
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.setProjection(70f, width.toFloat() / height.coerceAtLeast(1))
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        pendingMode?.let { newMode ->
            pendingMode = null
            applyModeSwitch(newMode)
        }

        val (dy, dp) = touch.consumeDeltas()
        camera.yaw   -= dy
        camera.pitch += dp

        when (playerMode) {
            PlayerMode.SPECTATOR -> updateSpectator(dt)
            PlayerMode.WALK      -> updateWalk(dt)
        }
        camera.update()

        // ── Mise à jour du monde ─────────────────────────────────────────────
        val cx = camera.chunkX(); val cy = camera.chunkY(); val cz = camera.chunkZ()
        if (cx != lastCx || cy != lastCy || cz != lastCz) {
            lastCx = cx; lastCy = cy; lastCz = cz
            world.updateAroundPlayer(cx, cy, cz) { chunk -> scheduleChunkBuild(chunk) }
        }

        // File de rebuild
        repeat(4) {  // max 4 rebuilds lancés par frame
            val key = world.rebuildQueue.poll() ?: return@repeat
            val chunk = world.getChunkByKey(key) ?: return@repeat
            if (!chunk.generated) return@repeat
            if (!building.add(key)) {
                world.rebuildQueue.add(key)  // déjà en cours : retry frame suivante
                return@repeat
            }
            chunk.meshDirty = false
            scope.launch {
                val verts = MeshBuilder.build(chunk, world)
                uploadQueue.add(Pair(key, verts))
                building.remove(key)
                // Un voisin a peut-être marqué ce chunk dirty pendant le build
                if (chunk.meshDirty) world.rebuildQueue.add(key)
            }
        }

        // Upload meshes sur le thread GL (max 4 par frame)
        repeat(4) {
            val (key, verts) = uploadQueue.poll() ?: return@repeat
            meshes.getOrPut(key) { ChunkMesh() }.upload(verts)
        }
        meshes.values.forEach { it.flushPending() }

        // Nettoyage des meshes orphelins (toutes les CLEANUP_INTERVAL frames)
        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0
            val loadedKeys = world.allChunks().mapTo(HashSet()) { world.chunkKey(it.cx, it.cy, it.cz) }
            val orphans = meshes.keys.filter { it !in loadedKeys }
            orphans.forEach { key -> meshes.remove(key)?.destroy() }
        }

        // ── Rendu ───────────────────────────────────────────────────────────
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        shader?.use()
        GLES30.glUniformMatrix4fv(uMvp, 1, false, camera.vpMatrix, 0)

        // Frustum culling : ne rend que les chunks dans le champ de vision
        extractFrustumPlanes(camera.vpMatrix)
        for ((key, mesh) in meshes) {
            val mcx = world.keyToCx(key)
            val mcy = world.keyToCy(key)
            val mcz = world.keyToCz(key)
            if (isChunkInFrustum(mcx, mcy, mcz)) mesh.draw(aPos, aColor)
        }

        posCallback?.invoke(camera.posString())

        // ── Plafond 30 fps ──────────────────────────────────────────────────
        val elapsed = System.nanoTime() - now
        val sleepNs = TARGET_FRAME_NS - elapsed
        if (sleepNs > 1_000_000L) Thread.sleep(sleepNs / 1_000_000L)
    }

    // ── Frustum culling ───────────────────────────────────────────────────────

    // Méthode Gribb-Hartmann : extraction des 6 plans depuis la matrice VP (column-major Android)
    private fun extractFrustumPlanes(m: FloatArray) {
        // Left   : row3 + row0
        frustum[0][0]=m[3]+m[0]; frustum[0][1]=m[7]+m[4];  frustum[0][2]=m[11]+m[8];  frustum[0][3]=m[15]+m[12]
        // Right  : row3 - row0
        frustum[1][0]=m[3]-m[0]; frustum[1][1]=m[7]-m[4];  frustum[1][2]=m[11]-m[8];  frustum[1][3]=m[15]-m[12]
        // Bottom : row3 + row1
        frustum[2][0]=m[3]+m[1]; frustum[2][1]=m[7]+m[5];  frustum[2][2]=m[11]+m[9];  frustum[2][3]=m[15]+m[13]
        // Top    : row3 - row1
        frustum[3][0]=m[3]-m[1]; frustum[3][1]=m[7]-m[5];  frustum[3][2]=m[11]-m[9];  frustum[3][3]=m[15]-m[13]
        // Near   : row3 + row2
        frustum[4][0]=m[3]+m[2]; frustum[4][1]=m[7]+m[6];  frustum[4][2]=m[11]+m[10]; frustum[4][3]=m[15]+m[14]
        // Far    : row3 - row2
        frustum[5][0]=m[3]-m[2]; frustum[5][1]=m[7]-m[6];  frustum[5][2]=m[11]-m[10]; frustum[5][3]=m[15]-m[14]
    }

    private fun isChunkInFrustum(cx: Int, cy: Int, cz: Int): Boolean {
        val minX = (cx * CHUNK_SIZE).toFloat(); val maxX = minX + CHUNK_SIZE
        val minY = (cy * CHUNK_SIZE).toFloat(); val maxY = minY + CHUNK_SIZE
        val minZ = (cz * CHUNK_SIZE).toFloat(); val maxZ = minZ + CHUNK_SIZE
        for (p in frustum) {
            val px = if (p[0] >= 0f) maxX else minX
            val py = if (p[1] >= 0f) maxY else minY
            val pz = if (p[2] >= 0f) maxZ else minZ
            if (p[0]*px + p[1]*py + p[2]*pz + p[3] < 0f) return false
        }
        return true
    }

    // ── Mouvement ─────────────────────────────────────────────────────────────

    private fun updateSpectator(dt: Float) {
        val speed = 10f * dt
        camera.moveHorizontal(touch.moveForward * speed, -touch.moveRight * speed)
        if (touch.flyUp)   camera.moveVertical( speed)
        if (touch.flyDown) camera.moveVertical(-speed)
    }

    private fun updateWalk(dt: Float) {
        val yawRad = Math.toRadians(camera.yaw.toDouble())
        val fX = sin(yawRad).toFloat(); val fZ = cos(yawRad).toFloat()
        val rX = cos(yawRad).toFloat(); val rZ = -sin(yawRad).toFloat()

        val hSpeed = if (onGround) 7f * dt else 4f * dt
        val dx = fX * touch.moveForward * hSpeed - rX * touch.moveRight * hSpeed
        val dz = fZ * touch.moveForward * hSpeed - rZ * touch.moveRight * hSpeed

        if (!collidesAt(camera.x + dx, camera.y, camera.z)) camera.x += dx
        if (!collidesAt(camera.x, camera.y, camera.z + dz)) camera.z += dz

        velocityY -= 22f * dt
        velocityY = velocityY.coerceAtLeast(-30f)
        val dy = velocityY * dt

        if (!collidesAt(camera.x, camera.y + dy, camera.z)) {
            camera.y += dy
            if (dy < 0f) onGround = false
        } else {
            if (velocityY < 0f) onGround = true
            velocityY = 0f
        }

        // v = sqrt(2 * g * h) = sqrt(2 * 22 * 2.5) ≈ 10.5 → 2.5 blocs de hauteur
        if (touch.flyUp && onGround) { velocityY = 10.5f; onGround = false }
    }

    // ── Collision AABB ────────────────────────────────────────────────────────

    private fun collidesAt(px: Float, py: Float, pz: Float): Boolean {
        val x0 = floorInt(px - 0.3f);  val x1 = floorInt(px + 0.29f)
        val y0 = floorInt(py - 1.62f); val y1 = floorInt(py + 0.18f)
        val z0 = floorInt(pz - 0.3f);  val z1 = floorInt(pz + 0.29f)
        for (bz in z0..z1) for (by in y0..y1) for (bx in x0..x1)
            if (worldBlockAt(bx, by, bz) != AIR) return true
        return false
    }

    private fun worldBlockAt(wx: Int, wy: Int, wz: Int): Byte {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = world.getChunk(cx, cy, cz) ?: return AIR   // non chargé → passant
        if (!chunk.generated) return AIR                        // en cours de génération → passant
        return chunk.blockAt(wx - cx * CHUNK_SIZE, wy - cy * CHUNK_SIZE, wz - cz * CHUNK_SIZE)
    }

    private fun floorInt(f: Float) = floor(f.toDouble()).toInt()

    // ── Changement de mode ────────────────────────────────────────────────────

    private fun applyModeSwitch(newMode: PlayerMode) {
        if (newMode == PlayerMode.WALK) {
            // Scan de sécurité uniquement si le chunk courant est chargé ET le joueur est dans du solide
            val bcx = Math.floorDiv(floorInt(camera.x), CHUNK_SIZE)
            val bcy = Math.floorDiv(floorInt(camera.y), CHUNK_SIZE)
            val bcz = Math.floorDiv(floorInt(camera.z), CHUNK_SIZE)
            val inLoadedSolid = world.getChunk(bcx, bcy, bcz)?.generated == true
                    && collidesAt(camera.x, camera.y, camera.z)
            if (inLoadedSolid) {
                var safeY = camera.y
                repeat(64) { if (collidesAt(camera.x, safeY, camera.z)) safeY += 1f }
                camera.y = safeY
            }
            velocityY = 0f
            onGround = false
        }
        playerMode = newMode
        modeCallback?.invoke(newMode)
    }

    // ── Génération ────────────────────────────────────────────────────────────

    private fun scheduleChunkBuild(chunk: com.Atom2Universe.app.games.caves.world.Chunk) {
        val key = world.chunkKey(chunk.cx, chunk.cy, chunk.cz)
        if (!building.add(key)) return
        scope.launch {
            world.generate(chunk)
            world.markGenerated(chunk)
            val verts = MeshBuilder.build(chunk, world)
            uploadQueue.add(Pair(key, verts))
            building.remove(key)
            if (chunk.meshDirty) world.rebuildQueue.add(key)
        }
    }

    fun destroy() {
        scope.cancel()
        meshes.values.forEach { it.destroy() }
        shader?.destroy()
    }
}
