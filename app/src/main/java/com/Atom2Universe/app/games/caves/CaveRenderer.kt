package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyManager
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.render.Camera
import com.Atom2Universe.app.games.caves.render.ChunkMesh
import com.Atom2Universe.app.games.caves.render.EnemyRenderer
import com.Atom2Universe.app.games.caves.render.ShaderProgram
import com.Atom2Universe.app.games.caves.world.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

enum class PlayerMode { WALK, SPECTATOR }

internal class CaveRenderer(
    private val context: Context,
    private val touch: TouchController,
    private val worldSeed: Long = System.currentTimeMillis(),
    private val worldId: String? = null,
    private val savedState: SavedState? = null
) : GLSurfaceView.Renderer {

    data class SavedState(
        val x: Double, val y: Double, val z: Double,
        val yaw: Float, val pitch: Float,
        val inventory: Map<Byte, Int>,
        val hotbar: List<Byte?>
    )

    val camera = Camera(8.0, 8.0, 8.0)
    private val storage = worldId?.let {
        CaveWorldChunkStorage(java.io.File(context.filesDir, "cave_worlds/$it"))
    }
    private val world = World(seed = worldSeed, storage = storage)
    private val meshes = ConcurrentHashMap<Long, ChunkMesh>()
    private val uploadQueue = ConcurrentLinkedQueue<Triple<Long, Int, FloatArray>>()

    private var worldShader: ShaderProgram? = null
    private var laserShader: ShaderProgram? = null
    private var wAPos = 0; private var wAUv = 0; private var wUMvp = 0; private var wUTex = 0
    private var wUChunkOffset = 0
    private var lAPos = 0; private var lAColor = 0; private var lUMvp = 0
    private var blockTexArray = 0

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val genDispatcher = Dispatchers.Default.limitedParallelism(3)
    private val building = ConcurrentHashMap.newKeySet<Long>()

    private var lastCx = Int.MAX_VALUE; private var lastCy = Int.MAX_VALUE; private var lastCz = Int.MAX_VALUE
    private var lastFrameNs = 0L

    private val frustum = Array(6) { FloatArray(4) }
    private var cleanupCounter = 0
    private val CLEANUP_INTERVAL = 120
    private val TARGET_FRAME_NS = 33_333_333L

    // Physique
    var playerMode = PlayerMode.WALK
    @Volatile var pendingMode: PlayerMode? = null
    private var velocityY = 0f
    private var onGround = false
    private var prevFlyUp = false

    // ── Minage ────────────────────────────────────────────────────────────────

    private data class RayHit(val bx: Int, val by: Int, val bz: Int, val fnx: Int, val fny: Int, val fnz: Int)
    private var mineTarget: RayHit? = null
    private var mineDamage = 0f

    val inventory = mutableMapOf<Byte, Int>()
    val hotbar    = arrayOfNulls<Byte>(9)
    var selectedSlot = 0

    private var transientVbo = 0
    private var elapsed = 0f

    private val HARDNESS = mapOf(
        GRASS         to 0.6f,
        LEAVES        to 0.2f,
        WOOD          to 2.0f,
        DIRT          to 0.5f,
        GRAVEL        to 0.8f,
        COAL          to 1.5f,
        QUARTZ        to 2.5f,
        STONE         to 3.0f,
        COPPER        to 2.0f,
        FURNACE       to 3.5f,
        GRANITE       to 4.0f,
        EMERALD       to 5.0f,
        IRON          to 4.0f,
        SILVER        to 4.5f,
        GOLD          to 5.0f,
        RUBY          to 5.5f,
        CRYSTAL       to 6.0f,
        LAVA          to 8.0f,
        // Biome blocks
        ICE           to 0.5f,
        SNOW          to 0.2f,
        BRICK_RED     to 4.5f,
        // Décors : minage instantané
        ROCK          to 0.1f,
        ROCK_MOSS     to 0.1f,
        MUSHROOM_RED  to 0.1f,
        MUSHROOM_BROWN to 0.1f,
        MUSHROOM_TAN  to 0.1f,
    )

    // ── Ennemis ───────────────────────────────────────────────────────────────

    private val enemyManager  = EnemyManager(world)
    private val enemyRenderer = EnemyRenderer()
    private var laserEnemyDmgTimer = 0f

    var posCallback: ((String) -> Unit)? = null
    var modeCallback: ((PlayerMode) -> Unit)? = null
    var miningCallback: ((progress: Float, block: Byte?) -> Unit)? = null
    var inventoryCallback: ((Map<Byte, Int>) -> Unit)? = null
    var hotbarCallback: ((slots: Array<Byte?>, selected: Int) -> Unit)? = null
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)? = null

    // ── Shaders ───────────────────────────────────────────────────────────────

    // Vertices are in chunk-local space (0..CHUNK_SIZE).
    // u_chunk_offset translates them to camera-relative world space (Floating Origin).
    private val VERT_WORLD = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_uv;
        uniform mat4 u_mvp;
        uniform vec3 u_chunk_offset;
        out vec2 v_uv;
        out float v_layer;
        out float v_faceDir;
        out float v_fog;
        void main() {
            vec3 worldPos = a_pos + u_chunk_offset;
            gl_Position = u_mvp * vec4(worldPos, 1.0);
            v_uv      = a_uv.xy;
            v_layer   = mod(a_uv.z, 32.0);
            v_faceDir = floor(a_uv.z / 32.0);
            v_fog     = clamp((gl_Position.w - 28.0) / 32.0, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG_WORLD = """
        #version 300 es
        precision mediump float;
        uniform sampler2DArray u_tex;
        in vec2 v_uv;
        in float v_layer;
        in float v_faceDir;
        in float v_fog;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(u_tex, vec3(v_uv, v_layer));
            if (col.a < 0.5) discard;
            float fd = floor(v_faceDir + 0.5);
            float light = fd < 0.5 ? 1.0 : fd < 1.5 ? 0.45 : fd < 3.5 ? 0.72 : 0.62;
            vec3 fog = vec3(0.008, 0.006, 0.015);
            fragColor = vec4(mix(col.rgb * light, fog, v_fog), 1.0);
        }
    """.trimIndent()

    // Laser vertices are already in camera-relative world space.
    private val VERT_LASER = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_color;
        uniform mat4 u_mvp;
        out vec3 v_color;
        out float v_fog;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_color = a_color;
            v_fog   = clamp((gl_Position.w - 28.0) / 32.0, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG_LASER = """
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

    // ── Lifecycle GL ──────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.008f, 0.006f, 0.015f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        lastFrameNs = System.nanoTime()

        worldShader = ShaderProgram(VERT_WORLD, FRAG_WORLD).also {
            it.use()
            wAPos         = it.attrib("a_pos")
            wAUv          = it.attrib("a_uv")
            wUMvp         = it.uniform("u_mvp")
            wUTex         = it.uniform("u_tex")
            wUChunkOffset = it.uniform("u_chunk_offset")
        }
        laserShader = ShaderProgram(VERT_LASER, FRAG_LASER).also {
            it.use()
            lAPos   = it.attrib("a_pos")
            lAColor = it.attrib("a_color")
            lUMvp   = it.uniform("u_mvp")
        }

        blockTexArray = loadBlockTextures()
        enemyRenderer.onSurfaceCreated()
        enemyManager.playerHpCallback = { hp, max -> playerHpCallback?.invoke(hp, max) }

        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        transientVbo = ids[0]

        if (savedState != null) {
            camera.x = savedState.x; camera.y = savedState.y; camera.z = savedState.z
            camera.yaw = savedState.yaw; camera.pitch = savedState.pitch
            inventory.putAll(savedState.inventory)
            savedState.hotbar.forEachIndexed { i, v -> hotbar[i] = v }
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            inventoryCallback?.invoke(inventory.toMap())
            val pcx = camera.chunkX(); val pcy = camera.chunkY(); val pcz = camera.chunkZ()
            for (dy in -1..1) for (dz in -1..1) for (dx in -1..1)
                world.pregenerateChunk(pcx + dx, pcy + dy, pcz + dz)
            // Calculer le spawn d'origine pour le système de niveaux
            val spawn = world.findSpawnPoint()
            enemyManager.worldSpawnX = spawn[0].toDouble()
            enemyManager.worldSpawnZ = spawn[2].toDouble()
        } else {
            for (dy in -1..1) for (dz in -1..1) for (dx in -1..1)
                world.pregenerateChunk(dx, dy, dz)
            val spawn = world.findSpawnPoint()
            camera.x = spawn[0].toDouble(); camera.y = spawn[1].toDouble(); camera.z = spawn[2].toDouble()
            enemyManager.worldSpawnX = camera.x
            enemyManager.worldSpawnZ = camera.z
        }
    }

    private fun loadBlockTextures(): Int {
        val files = listOf(
            // Solides de base (layers 0-22, identiques à avant)
            "stone.png", "greystone.png", "greysand.png", "stone_coal.png",
            "stone_gold.png", "stone_diamond.png", "dirt.png", "gravel_stone.png",
            "stone_iron.png", "stone_silver.png", "greystone_ruby.png", "lava.png", "oven.png",
            "redstone_emerald.png", "stone_browniron.png",
            "dirt_grass.png", "grass_top.png",
            "trunk_side.png", "trunk_top.png", "leaves.png",
            "sand.png", "stone_grass.png", "redsand.png",
            // Nouveaux blocs biome (layers 23-26)
            "ice.png", "snow.png", "dirt_snow.png", "brick_red.png",
            // Décors cross-sprite (layers 27-31)
            "rock.png", "rock_moss.png", "mushroom_red.png", "mushroom_brown.png", "mushroom_tan.png",
            // Transitions sol biome pour faces latérales STONE (layers 32-33)
            "stone_sand_side.png", "stone_snow_side.png"
        )
        val am = context.assets
        val bitmaps = files.map { BitmapFactory.decodeStream(am.open("Cave World/Tiles/$it")) }
        val w = bitmaps[0].width; val h = bitmaps[0].height

        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, texId)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_RGBA8, w, h, bitmaps.size, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)

        for ((i, bmp) in bitmaps.withIndex()) {
            val scaled = if (bmp.width == w && bmp.height == h) bmp
                         else android.graphics.Bitmap.createScaledBitmap(bmp, w, h, false)
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            scaled.copyPixelsToBuffer(buf); buf.position(0)
            GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, w, h, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            if (scaled !== bmp) scaled.recycle()
            bmp.recycle()
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        return texId
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.setProjection(70f, width.toFloat() / height.coerceAtLeast(1))
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        pendingMode?.let { newMode -> pendingMode = null; applyModeSwitch(newMode) }

        val (dy, dp) = touch.consumeDeltas()
        camera.yaw   -= dy
        camera.pitch += dp

        when (playerMode) {
            PlayerMode.SPECTATOR -> updateSpectator(dt)
            PlayerMode.WALK      -> updateWalk(dt)
        }
        camera.update()

        elapsed += dt

        updateMining(dt)

        val cx = camera.chunkX(); val cy = camera.chunkY(); val cz = camera.chunkZ()
        if (cx != lastCx || cy != lastCy || cz != lastCz) {
            lastCx = cx; lastCy = cy; lastCz = cz
            world.updateAroundPlayer(cx, cy, cz) { chunk -> scheduleChunkBuild(chunk) }
        }

        val rebuildBatch = ArrayList<Long>(32)
        repeat(64) { world.rebuildQueue.poll()?.let { rebuildBatch.add(it) } }
        rebuildBatch.sortBy { key ->
            val dx = world.keyToCx(key) - cx; val dy = world.keyToCy(key) - cy; val dz = world.keyToCz(key) - cz
            dx * dx + dy * dy + dz * dz
        }
        var rebuilt = 0
        for (key in rebuildBatch) {
            if (rebuilt >= 16) { world.rebuildQueue.add(key); continue }
            val chunk = world.getChunkByKey(key) ?: continue
            if (!chunk.generated) { world.rebuildQueue.add(key); continue }
            if (!building.add(key)) { world.rebuildQueue.add(key); continue }
            chunk.meshDirty = false
            val snapVersion = chunk.version
            scope.launch {
                val verts = MeshBuilder.build(chunk, world)
                if (chunk.version == snapVersion) uploadQueue.add(Triple(key, snapVersion, verts))
                building.remove(key)
                if (chunk.meshDirty || chunk.version != snapVersion) world.rebuildQueue.add(key)
            }
            rebuilt++
        }

        repeat(8) {
            val (key, ver, verts) = uploadQueue.poll() ?: return@repeat
            val chunk = world.getChunkByKey(key) ?: return@repeat
            if (chunk.version == ver) meshes.getOrPut(key) { ChunkMesh() }.upload(verts)
        }
        meshes.values.forEach { it.flushPending() }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0
            val loadedKeys = world.allChunks().mapTo(HashSet()) { world.chunkKey(it.cx, it.cy, it.cz) }
            meshes.keys.filter { it !in loadedKeys }.forEach { key -> meshes.remove(key)?.destroy() }
        }

        // ── Rendu chunks ─────────────────────────────────────────────────────
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        worldShader?.use()
        GLES30.glUniformMatrix4fv(wUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, blockTexArray)
        GLES30.glUniform1i(wUTex, 0)

        extractFrustumPlanes(camera.vpMatrix)
        for ((key, mesh) in meshes) {
            val kcx = world.keyToCx(key); val kcy = world.keyToCy(key); val kcz = world.keyToCz(key)
            if (!isChunkInFrustum(kcx, kcy, kcz)) continue
            // Offset in Double, converted to Float. Since the chunk is within renderRadius
            // of the camera, this value is always small (<= ~80 blocks) — exact in Float.
            val offX = (kcx.toDouble() * CHUNK_SIZE - camera.x).toFloat()
            val offY = (kcy.toDouble() * CHUNK_SIZE - camera.y).toFloat()
            val offZ = (kcz.toDouble() * CHUNK_SIZE - camera.z).toFloat()
            GLES30.glUniform3f(wUChunkOffset, offX, offY, offZ)
            mesh.draw(wAPos, wAUv)
        }

        // ── Mise à jour + rendu ennemis ───────────────────────────────────────
        enemyManager.update(dt, camera.x, camera.y, camera.z)
        enemyRenderer.render(
            enemyManager.enemies,
            camera.x, camera.y, camera.z,
            camera.yaw, camera.vpMatrix
        )

        // ── Rendu laser + highlight (blending additif) ────────────────────────
        renderLaserAndHighlight()

        posCallback?.invoke(camera.posString())

        val elapsed = System.nanoTime() - now
        val sleepNs = TARGET_FRAME_NS - elapsed
        if (sleepNs > 1_000_000L) Thread.sleep(sleepNs / 1_000_000L)
    }

    // ── Minage ────────────────────────────────────────────────────────────────

    private val MINE_REACH = 6

    private fun updateMining(dt: Float) {
        if (touch.placeRequested) {
            touch.placeRequested = false
            placeBlock()
        }

        // Vérifier si le laser touche un ennemi en priorité
        if (touch.laserActive) {
            val hitEnemy = enemyManager.hitByLaser(
                camera.x, camera.y, camera.z,
                camera.lookX, camera.lookY, camera.lookZ,
                MINE_REACH.toFloat()
            )
            if (hitEnemy != null) {
                // Pointer le laser vers le centre de l'ennemi (pour la visu)
                mineTarget = RayHit(
                    Math.floor(hitEnemy.x).toInt(),
                    Math.floor(hitEnemy.y - hitEnemy.type.eyeHeight * 0.5).toInt(),
                    Math.floor(hitEnemy.z).toInt(),
                    0, 0, 0
                )
                mineDamage = 0f
                miningCallback?.invoke(0f, null)
                laserEnemyDmgTimer -= dt
                if (laserEnemyDmgTimer <= 0f) {
                    laserEnemyDmgTimer = 0.22f
                    enemyManager.damageEnemy(hitEnemy, 1)
                }
                return
            }
        }
        laserEnemyDmgTimer = 0f

        val target = if (touch.laserActive) raycastBlock() else null

        if (target == null) {
            mineTarget = null
            mineDamage = 0f
            miningCallback?.invoke(0f, null)
            return
        }

        val bx = target.bx; val by = target.by; val bz = target.bz
        if (mineTarget?.bx != bx || mineTarget?.by != by || mineTarget?.bz != bz) mineDamage = 0f
        mineTarget = target

        val blockType = worldBlockAt(bx, by, bz)
        val hardness = HARDNESS[blockType] ?: 3f
        mineDamage += dt / hardness

        miningCallback?.invoke(mineDamage, blockType)

        if (mineDamage >= 1f) {
            world.setBlock(bx, by, bz, AIR)
            forceMeshRebuild(bx, by, bz)
            inventory[blockType] = (inventory[blockType] ?: 0) + 1
            if (hotbar.none { it == blockType }) {
                val empty = hotbar.indexOfFirst { it == null }
                if (empty >= 0) { hotbar[empty] = blockType; hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot) }
            }
            inventoryCallback?.invoke(inventory.toMap())
            mineTarget = null
            mineDamage = 0f
            miningCallback?.invoke(0f, null)
        }
    }

    private fun forceMeshRebuild(wx: Int, wy: Int, wz: Int) {
        val cx = Math.floorDiv(wx, CHUNK_SIZE)
        val cy = Math.floorDiv(wy, CHUNK_SIZE)
        val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val affectedChunks = mutableListOf(Triple(cx, cy, cz))
        val lx = wx - cx * CHUNK_SIZE; val ly = wy - cy * CHUNK_SIZE; val lz = wz - cz * CHUNK_SIZE
        if (lx == 0)              affectedChunks += Triple(cx - 1, cy, cz)
        if (lx == CHUNK_SIZE - 1) affectedChunks += Triple(cx + 1, cy, cz)
        if (ly == 0)              affectedChunks += Triple(cx, cy - 1, cz)
        if (ly == CHUNK_SIZE - 1) affectedChunks += Triple(cx, cy + 1, cz)
        if (lz == 0)              affectedChunks += Triple(cx, cy, cz - 1)
        if (lz == CHUNK_SIZE - 1) affectedChunks += Triple(cx, cy, cz + 1)

        for ((ncx, ncy, ncz) in affectedChunks) {
            val key = world.chunkKey(ncx, ncy, ncz)
            val chunk = world.getChunk(ncx, ncy, ncz)?.takeIf { it.generated } ?: continue
            val verts = MeshBuilder.build(chunk, world)
            meshes.getOrPut(key) { ChunkMesh() }.upload(verts)
        }
    }

    // DDA 3D : retourne le premier bloc solide avec la normale de la face touchée.
    // camera.x/y/z sont Double; les calculs initiaux se font en Double pour que
    // la position fractionnaire dans le bloc de départ soit correcte à grande distance.
    private fun raycastBlock(): RayHit? {
        val dirX = camera.lookX; val dirY = camera.lookY; val dirZ = camera.lookZ

        var bx = floorInt(camera.x); var by = floorInt(camera.y); var bz = floorInt(camera.z)

        val stepX = if (dirX > 0) 1 else -1
        val stepY = if (dirY > 0) 1 else -1
        val stepZ = if (dirZ > 0) 1 else -1

        val tDX = if (dirX != 0f) abs(1f / dirX) else Float.MAX_VALUE
        val tDY = if (dirY != 0f) abs(1f / dirY) else Float.MAX_VALUE
        val tDZ = if (dirZ != 0f) abs(1f / dirZ) else Float.MAX_VALUE

        // Fraction initiale calculée en Double pour éviter l'erreur de troncature à grande distance
        var tMaxX = if (dirX > 0) ((bx + 1).toDouble() - camera.x).toFloat() * tDX else (camera.x - bx.toDouble()).toFloat() * tDX
        var tMaxY = if (dirY > 0) ((by + 1).toDouble() - camera.y).toFloat() * tDY else (camera.y - by.toDouble()).toFloat() * tDY
        var tMaxZ = if (dirZ > 0) ((bz + 1).toDouble() - camera.z).toFloat() * tDZ else (camera.z - bz.toDouble()).toFloat() * tDZ

        var fnx = 0; var fny = 0; var fnz = -1

        repeat(MINE_REACH * 4) {
            val b = worldBlockAt(bx, by, bz)
            if (b != AIR) return RayHit(bx, by, bz, fnx, fny, fnz)
            when {
                tMaxX < tMaxY && tMaxX < tMaxZ -> { fnx = -stepX; fny = 0; fnz = 0; bx += stepX; tMaxX += tDX }
                tMaxY < tMaxZ                  -> { fnx = 0; fny = -stepY; fnz = 0; by += stepY; tMaxY += tDY }
                else                           -> { fnx = 0; fny = 0; fnz = -stepZ; bz += stepZ; tMaxZ += tDZ }
            }
            val t = minOf(tMaxX - tDX, tMaxY - tDY, tMaxZ - tDZ)
            if (t > MINE_REACH) return null
        }
        return null
    }

    // ── Rendu laser + highlight ───────────────────────────────────────────────

    private fun renderLaserAndHighlight() {
        val target = mineTarget ?: return
        if (!touch.laserActive) return

        val verts = buildLaserVerts(target) + buildHighlightVerts(target, mineDamage)
        if (verts.isEmpty()) return

        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts); buf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, transientVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES30.GL_DYNAMIC_DRAW)

        laserShader?.use()
        GLES30.glUniformMatrix4fv(lUMvp, 1, false, camera.vpMatrix, 0)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)

        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(lAPos)
        GLES30.glVertexAttribPointer(lAPos,   3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(lAColor)
        GLES30.glVertexAttribPointer(lAColor, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, verts.size / 6)
        GLES30.glDisableVertexAttribArray(lAPos)
        GLES30.glDisableVertexAttribArray(lAColor)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    // Laser et highlight en espace caméra-relatif : camera = origine, tout est soustrait.
    private fun buildLaserVerts(target: RayHit): FloatArray {
        // Centre de la face touchée, en espace caméra-relatif
        val ex = (target.bx.toDouble() + 0.5 + target.fnx * 0.5 - camera.x).toFloat()
        val ey = (target.by.toDouble() + 0.5 + target.fny * 0.5 - camera.y).toFloat()
        val ez = (target.bz.toDouble() + 0.5 + target.fnz * 0.5 - camera.z).toFloat()

        // Source du laser : offset latéral depuis la caméra (camera = origin)
        val yawRad = Math.toRadians(camera.yaw.toDouble()).toFloat()
        val rX = cos(yawRad); val rZ = -sin(yawRad)
        val sx = rX * 0.35f
        val sy = -0.25f
        val sz = rZ * 0.35f

        val ddx = ex - sx; val ddy = ey - sy; val ddz = ez - sz
        val blen = sqrt(ddx*ddx + ddy*ddy + ddz*ddz).coerceAtLeast(0.001f)
        val bfX = ddx/blen; val bfY = ddy/blen; val bfZ = ddz/blen

        val upX = if (abs(bfY) < 0.9f) 0f else 1f
        val upY = if (abs(bfY) < 0.9f) 1f else 0f
        val upZ = 0f
        var ruX = bfY*upZ-bfZ*upY; var ruY = bfZ*upX-bfX*upZ; var ruZ = bfX*upY-bfY*upX
        val rl = sqrt(ruX*ruX+ruY*ruY+ruZ*ruZ).coerceAtLeast(0.001f); ruX/=rl; ruY/=rl; ruZ/=rl
        val rvX = ruY*bfZ-ruZ*bfY; val rvY = ruZ*bfX-ruX*bfZ; val rvZ = ruX*bfY-ruY*bfX

        val out = ArrayList<Float>(3200)
        fun emit(px:Float,py:Float,pz:Float, r:Float,g:Float,b:Float) {
            out.add(px);out.add(py);out.add(pz);out.add(r);out.add(g);out.add(b)
        }
        fun seg(x0:Float,y0:Float,z0:Float, x1:Float,y1:Float,z1:Float, hw:Float, r:Float,g:Float,b:Float) {
            emit(x0+ruX*hw,y0+ruY*hw,z0+ruZ*hw,r,g,b); emit(x0-ruX*hw,y0-ruY*hw,z0-ruZ*hw,r,g,b); emit(x1-ruX*hw,y1-ruY*hw,z1-ruZ*hw,r,g,b)
            emit(x0+ruX*hw,y0+ruY*hw,z0+ruZ*hw,r,g,b); emit(x1-ruX*hw,y1-ruY*hw,z1-ruZ*hw,r,g,b); emit(x1+ruX*hw,y1+ruY*hw,z1+ruZ*hw,r,g,b)
            emit(x0+rvX*hw,y0+rvY*hw,z0+rvZ*hw,r,g,b); emit(x0-rvX*hw,y0-rvY*hw,z0-rvZ*hw,r,g,b); emit(x1-rvX*hw,y1-rvY*hw,z1-rvZ*hw,r,g,b)
            emit(x0+rvX*hw,y0+rvY*hw,z0+rvZ*hw,r,g,b); emit(x1-rvX*hw,y1-rvY*hw,z1-rvZ*hw,r,g,b); emit(x1+rvX*hw,y1+rvY*hw,z1+rvZ*hw,r,g,b)
        }

        val N = 8
        val px = FloatArray(N+1); val py = FloatArray(N+1); val pz = FloatArray(N+1)
        px[0]=sx; py[0]=sy; pz[0]=sz; px[N]=ex; py[N]=ey; pz[N]=ez
        for (i in 1 until N) {
            val t = i.toFloat() / N
            val env = sin(t * PI.toFloat()) * 0.10f
            val wu = sin(elapsed * 34.7f + i * 2.13f) * env
            val wv = cos(elapsed * 28.9f + i * 1.87f) * env
            px[i] = sx+(ex-sx)*t + ruX*wu + rvX*wv
            py[i] = sy+(ey-sy)*t + ruY*wu + rvY*wv
            pz[i] = sz+(ez-sz)*t + ruZ*wu + rvZ*wv
        }
        for (i in 0 until N) seg(px[i],py[i],pz[i], px[i+1],py[i+1],pz[i+1], 0.022f, 0.04f,0.15f,0.55f)
        for (i in 0 until N) seg(px[i],py[i],pz[i], px[i+1],py[i+1],pz[i+1], 0.006f, 0.75f,1.0f,1.0f)

        for (br in 0..1) {
            val si = 2 + br * 3
            val qx = FloatArray(6); val qy = FloatArray(6); val qz = FloatArray(6)
            qx[0]=px[si]; qy[0]=py[si]; qz[0]=pz[si]
            val sign = if (br == 0) 1f else -1f
            for (j in 1..5) {
                val t2 = j / 5f
                val env2 = sin(t2 * PI.toFloat()) * 0.08f
                val wu2 = sin(elapsed * 43.1f + j*3.3f + br*6.1f) * env2 * sign
                val wv2 = cos(elapsed * 38.7f + j*2.7f + br*5.3f) * env2
                qx[j] = px[si]+(ex-px[si])*t2*0.85f + ruX*wu2 + rvX*wv2
                qy[j] = py[si]+(ey-py[si])*t2*0.85f + ruY*wu2 + rvY*wv2
                qz[j] = pz[si]+(ez-pz[si])*t2*0.85f + ruZ*wu2 + rvZ*wv2
            }
            for (j in 0..4) seg(qx[j],qy[j],qz[j], qx[j+1],qy[j+1],qz[j+1], 0.005f, 0.2f,0.55f,1.0f)
        }

        val fnx = target.fnx.toFloat(); val fny = target.fny.toFloat(); val fnz = target.fnz.toFloat()
        val t1x: Float; val t1y: Float; val t1z: Float
        val t2x: Float; val t2y: Float; val t2z: Float
        if (abs(fny) > 0.9f) { t1x=1f; t1y=0f; t1z=0f; t2x=0f; t2y=0f; t2z=1f }
        else {
            val cx = fny*0f-fnz*1f; val cy = fnz*0f-fnx*0f; val cz = fnx*1f-fny*0f
            val cl = sqrt(cx*cx+cy*cy+cz*cz).coerceAtLeast(0.001f)
            t1x=cx/cl; t1y=cy/cl; t1z=cz/cl
            t2x=fny*t1z-fnz*t1y; t2y=fnz*t1x-fnx*t1z; t2z=fnx*t1y-fny*t1x
        }
        val rot = elapsed * 2.5f
        val pulse = 1f + 0.35f * sin(elapsed * 22f)
        for (k in 0..3) {
            val angle = rot + k * PI.toFloat() / 2
            val sLen = 0.18f * pulse * (0.7f + 0.3f * sin(elapsed * 17f + k * 1.4f))
            val spX = ex + t1x*cos(angle)*sLen + t2x*sin(angle)*sLen
            val spY = ey + t1y*cos(angle)*sLen + t2y*sin(angle)*sLen
            val spZ = ez + t1z*cos(angle)*sLen + t2z*sin(angle)*sLen
            seg(ex,ey,ez, spX,spY,spZ, 0.018f, 0.04f,0.18f,0.55f)
            seg(ex,ey,ez, spX,spY,spZ, 0.007f, 0.85f,1.0f,1.0f)
        }

        return out.toFloatArray()
    }

    // Faces du bloc ciblé en espace caméra-relatif
    private fun buildHighlightVerts(target: RayHit, progress: Float): FloatArray {
        val x = (target.bx.toDouble() - camera.x).toFloat()
        val y = (target.by.toDouble() - camera.y).toFloat()
        val z = (target.bz.toDouble() - camera.z).toFloat()
        val ep = 0.005f
        val x0=x-ep; val y0=y-ep; val z0=z-ep
        val x1=x+1+ep; val y1=y+1+ep; val z1=z+1+ep

        val t = progress.coerceIn(0f, 1f)
        val cr = t * 0.9f; val cg = (1f - t) * 0.4f; val cb = (1f - t) * 0.5f

        val out = FloatArray(36 * 6)
        var i = 0
        fun v(px: Float, py: Float, pz: Float) {
            out[i++]=px; out[i++]=py; out[i++]=pz; out[i++]=cr; out[i++]=cg; out[i++]=cb
        }
        v(x0,y1,z0); v(x1,y1,z0); v(x1,y1,z1); v(x0,y1,z0); v(x1,y1,z1); v(x0,y1,z1)
        v(x0,y0,z1); v(x1,y0,z1); v(x1,y0,z0); v(x0,y0,z1); v(x1,y0,z0); v(x0,y0,z0)
        v(x1,y0,z1); v(x1,y1,z1); v(x1,y1,z0); v(x1,y0,z1); v(x1,y1,z0); v(x1,y0,z0)
        v(x0,y0,z0); v(x0,y1,z0); v(x0,y1,z1); v(x0,y0,z0); v(x0,y1,z1); v(x0,y0,z1)
        v(x0,y0,z1); v(x0,y1,z1); v(x1,y1,z1); v(x0,y0,z1); v(x1,y1,z1); v(x1,y0,z1)
        v(x1,y0,z0); v(x1,y1,z0); v(x0,y1,z0); v(x1,y0,z0); v(x0,y1,z0); v(x0,y0,z0)
        return out
    }

    // ── Frustum culling ───────────────────────────────────────────────────────

    private fun extractFrustumPlanes(m: FloatArray) {
        frustum[0][0]=m[3]+m[0]; frustum[0][1]=m[7]+m[4];  frustum[0][2]=m[11]+m[8];  frustum[0][3]=m[15]+m[12]
        frustum[1][0]=m[3]-m[0]; frustum[1][1]=m[7]-m[4];  frustum[1][2]=m[11]-m[8];  frustum[1][3]=m[15]-m[12]
        frustum[2][0]=m[3]+m[1]; frustum[2][1]=m[7]+m[5];  frustum[2][2]=m[11]+m[9];  frustum[2][3]=m[15]+m[13]
        frustum[3][0]=m[3]-m[1]; frustum[3][1]=m[7]-m[5];  frustum[3][2]=m[11]-m[9];  frustum[3][3]=m[15]-m[13]
        frustum[4][0]=m[3]+m[2]; frustum[4][1]=m[7]+m[6];  frustum[4][2]=m[11]+m[10]; frustum[4][3]=m[15]+m[14]
        frustum[5][0]=m[3]-m[2]; frustum[5][1]=m[7]-m[6];  frustum[5][2]=m[11]-m[10]; frustum[5][3]=m[15]-m[14]
    }

    // AABB du chunk en espace caméra-relatif (camera = origine)
    private fun isChunkInFrustum(cx: Int, cy: Int, cz: Int): Boolean {
        val x0 = (cx.toDouble() * CHUNK_SIZE - camera.x).toFloat(); val x1 = x0 + CHUNK_SIZE
        val y0 = (cy.toDouble() * CHUNK_SIZE - camera.y).toFloat(); val y1 = y0 + CHUNK_SIZE
        val z0 = (cz.toDouble() * CHUNK_SIZE - camera.z).toFloat(); val z1 = z0 + CHUNK_SIZE
        for (p in frustum) {
            val px = if (p[0]>=0f) x1 else x0; val py = if (p[1]>=0f) y1 else y0; val pz = if (p[2]>=0f) z1 else z0
            if (p[0]*px+p[1]*py+p[2]*pz+p[3] < 0f) return false
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
        val dx = (fX * touch.moveForward * hSpeed - rX * touch.moveRight * hSpeed).toDouble()
        val dz = (fZ * touch.moveForward * hSpeed - rZ * touch.moveRight * hSpeed).toDouble()

        if (!collidesAt(camera.x + dx, camera.y, camera.z)) camera.x += dx
        if (!collidesAt(camera.x, camera.y, camera.z + dz)) camera.z += dz

        val jumpPressed = touch.flyUp && !prevFlyUp
        prevFlyUp = touch.flyUp
        if (jumpPressed && onGround) { velocityY = 10.5f; onGround = false }

        velocityY -= 22f * dt
        velocityY = velocityY.coerceAtLeast(-30f)
        val dy = (velocityY * dt).toDouble()
        if (!collidesAt(camera.x, camera.y + dy, camera.z)) {
            camera.y += dy
        } else {
            if (velocityY < 0f) {
                var lo = camera.y + dy
                var hi = camera.y
                repeat(8) {
                    val mid = (lo + hi) * 0.5
                    if (collidesAt(camera.x, mid, camera.z)) lo = mid else hi = mid
                }
                camera.y = hi
            }
            velocityY = 0f
        }

        onGround = velocityY <= 0.1f && collidesAt(camera.x, camera.y - 0.1, camera.z)
    }

    // ── Collision ─────────────────────────────────────────────────────────────

    private fun collidesAt(px: Double, py: Double, pz: Double): Boolean {
        val x0=floorInt(px-0.3); val x1=floorInt(px+0.29)
        val y0=floorInt(py-1.62); val y1=floorInt(py+0.18)
        val z0=floorInt(pz-0.3); val z1=floorInt(pz+0.29)
        for (bz in z0..z1) for (by in y0..y1) for (bx in x0..x1) {
            val b = worldBlockAt(bx, by, bz)
            if (b != AIR && !isDecoration(b)) return true
        }
        return false
    }

    private fun worldBlockAt(wx: Int, wy: Int, wz: Int): Byte {
        val cx = Math.floorDiv(wx, CHUNK_SIZE); val cy = Math.floorDiv(wy, CHUNK_SIZE); val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = world.getChunk(cx, cy, cz) ?: return AIR
        if (!chunk.generated) return AIR
        return chunk.blockAt(wx-cx*CHUNK_SIZE, wy-cy*CHUNK_SIZE, wz-cz*CHUNK_SIZE)
    }

    private fun floorInt(d: Double) = Math.floor(d).toInt()

    // ── Mode switch ───────────────────────────────────────────────────────────

    private fun applyModeSwitch(newMode: PlayerMode) {
        if (newMode == PlayerMode.WALK) {
            val bcx = Math.floorDiv(floorInt(camera.x), CHUNK_SIZE)
            val bcy = Math.floorDiv(floorInt(camera.y), CHUNK_SIZE)
            val bcz = Math.floorDiv(floorInt(camera.z), CHUNK_SIZE)
            val inLoadedSolid = world.getChunk(bcx, bcy, bcz)?.generated == true && collidesAt(camera.x, camera.y, camera.z)
            if (inLoadedSolid) {
                var safeY = camera.y
                repeat(64) { if (collidesAt(camera.x, safeY, camera.z)) safeY += 1.0 }
                camera.y = safeY
            }
            velocityY = 0f; onGround = false
        }
        playerMode = newMode
        modeCallback?.invoke(newMode)
    }

    // ── Génération ────────────────────────────────────────────────────────────

    private fun scheduleChunkBuild(chunk: com.Atom2Universe.app.games.caves.world.Chunk) {
        val key = world.chunkKey(chunk.cx, chunk.cy, chunk.cz)
        if (!building.add(key)) return
        scope.launch(genDispatcher) {
            world.generate(chunk)
            world.markGenerated(chunk)

            val snapVersion = chunk.version
            val verts = MeshBuilder.build(chunk, world)
            if (chunk.version == snapVersion) uploadQueue.add(Triple(key, snapVersion, verts))
            building.remove(key)
            if (chunk.meshDirty || chunk.version != snapVersion) world.rebuildQueue.add(key)

            val offsets = arrayOf(
                intArrayOf(1,0,0), intArrayOf(-1,0,0),
                intArrayOf(0,1,0), intArrayOf(0,-1,0),
                intArrayOf(0,0,1), intArrayOf(0,0,-1)
            )
            for ((dx, dy, dz) in offsets) {
                val nb = world.getChunk(chunk.cx + dx, chunk.cy + dy, chunk.cz + dz) ?: continue
                if (!nb.generated) continue
                val nKey = world.chunkKey(nb.cx, nb.cy, nb.cz)
                if (!building.add(nKey)) continue
                nb.meshDirty = false
                val nSnap = nb.version
                val nVerts = MeshBuilder.build(nb, world)
                if (nb.version == nSnap) uploadQueue.add(Triple(nKey, nSnap, nVerts))
                building.remove(nKey)
                if (nb.meshDirty || nb.version != nSnap) world.rebuildQueue.add(nKey)
            }
        }
    }

    fun selectSlot(index: Int) {
        if (index !in 0..8) return
        selectedSlot = index
        hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
    }

    private fun placeBlock() {
        val blockType = hotbar[selectedSlot] ?: return
        if ((inventory[blockType] ?: 0) <= 0) {
            hotbar[selectedSlot] = null
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            return
        }
        val target = raycastBlock() ?: return
        val px = target.bx + target.fnx
        val py = target.by + target.fny
        val pz = target.bz + target.fnz
        if (isInsidePlayer(px, py, pz)) return
        world.setBlock(px, py, pz, blockType)
        forceMeshRebuild(px, py, pz)
        inventory[blockType] = (inventory[blockType] ?: 1) - 1
        if ((inventory[blockType] ?: 0) <= 0) {
            inventory.remove(blockType)
            for (j in hotbar.indices) { if (hotbar[j] == blockType) hotbar[j] = null }
        }
        inventoryCallback?.invoke(inventory.toMap())
        hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
    }

    private fun isInsidePlayer(bx: Int, by: Int, bz: Int): Boolean {
        val x0 = floorInt(camera.x - 0.3); val x1 = floorInt(camera.x + 0.29)
        val y0 = floorInt(camera.y - 1.62); val y1 = floorInt(camera.y + 0.18)
        val z0 = floorInt(camera.z - 0.3); val z1 = floorInt(camera.z + 0.29)
        return bx in x0..x1 && by in y0..y1 && bz in z0..z1
    }

    fun destroy() {
        scope.cancel()
        storage?.shutdown()
        meshes.values.forEach { it.destroy() }
        worldShader?.destroy()
        laserShader?.destroy()
        enemyRenderer.destroy()
        if (blockTexArray != 0) GLES30.glDeleteTextures(1, intArrayOf(blockTexArray), 0)
        if (transientVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(transientVbo), 0)
    }
}
