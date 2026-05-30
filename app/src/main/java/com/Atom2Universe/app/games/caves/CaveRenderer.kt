package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyManager
import com.Atom2Universe.app.games.caves.entity.Projectile
import com.Atom2Universe.app.games.caves.entity.PlayerStats
import com.Atom2Universe.app.games.caves.entity.UpgradeOption
import com.Atom2Universe.app.games.caves.entity.UpgradeType
import com.Atom2Universe.app.games.caves.entity.WeaponColor
import com.Atom2Universe.app.games.caves.entity.WeaponDef
import com.Atom2Universe.app.games.caves.entity.WeaponVariant
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.render.Camera
import com.Atom2Universe.app.games.caves.render.ChunkMesh
import com.Atom2Universe.app.games.caves.entity.PassiveMobManager
import com.Atom2Universe.app.games.caves.render.EnemyRenderer
import com.Atom2Universe.app.games.caves.render.PassiveMobRenderer
import com.Atom2Universe.app.games.caves.render.ProjectileRenderer
import com.Atom2Universe.app.games.caves.render.ShaderProgram
import com.Atom2Universe.app.games.caves.world.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

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
        val hotbar: List<Byte?>,
        val playerHp: Int = 20,
        val playerLevel: Int = 1,
        val playerXp: Int = 0,
        val playerDamage: Int = 2,
        val playerFireRate: Float = 1.5f,
        val playerMaxHp: Int = 20,
        val playerShield: Int = 0,
        val playerShieldCurrent: Int = 0,
        val playerWeapons: List<String> = listOf("WHITE_SQUARE")
    )

    val camera = Camera(8.0, 8.0, 8.0)
    private val storage = worldId?.let {
        CaveWorldChunkStorage(java.io.File(context.filesDir, "cave_worlds/$it"))
    }
    private val world = World(seed = worldSeed, storage = storage)
    private val meshes = ConcurrentHashMap<Long, ChunkMesh>()
    private val uploadQueue = ConcurrentLinkedQueue<Triple<Long, Int, FloatArray>>()

    private val lodMeshes      = ConcurrentHashMap<Long, ChunkMesh>()
    private val lodBuilding    = ConcurrentHashMap.newKeySet<Long>()
    private val lodUploadQueue = ConcurrentLinkedQueue<Pair<Long, FloatArray>>()
    private val lodUploadQueueSize = java.util.concurrent.atomic.AtomicInteger(0)
    private val LOD_RADIUS   = 90
    private val MAX_LOD_TILES = 8000
    private val LOD_SUPER    = 8                           // 8×8 = 64 colonnes par super-tuile
    private val lodGrid      = HashMap<Long, ArrayList<Long>>() // super-tuile → liste de clés LOD
    private val lodCache = worldId?.let {
        LodCache(java.io.File(context.filesDir, "cave_worlds/$it/lod_cache.bin"))
    }

    private var worldShader: ShaderProgram? = null
    private var laserShader: ShaderProgram? = null
    private var starShader:  ShaderProgram? = null
    private var waterShader: ShaderProgram? = null
    private var wAPos = 0; private var wAUv = 0; private var wUMvp = 0; private var wUTex = 0
    private var wUChunkOffset = 0; private var wUAmbient = 0
    private var wULights = 0; private var wULightCount = 0; private var wUTime = 0
    private var wUUnderwater = 0
    private var lAPos = 0; private var lAColor = 0; private var lUMvp = 0
    private var sAPos = 0; private var sABrightness = 0; private var sUMvp = 0
    private var wWAPos = 0; private var wWAUv = 0; private var wWUMvp = 0
    private var wWUChunkOffset = 0; private var wWUAmbient = 0
    private var wWULights = 0; private var wWULightCount = 0; private var wWUTime = 0
    private var blockTexArray = 0

    private val waterMeshes      = ConcurrentHashMap<Long, ChunkMesh>()
    private val waterUploadQueue = ConcurrentLinkedQueue<Triple<Long, Int, FloatArray>>()

    // ── Étoiles ───────────────────────────────────────────────────────────────
    private val STAR_COUNT  = 250
    private val STAR_RADIUS = 120f
    private val starDirs    = FloatArray(STAR_COUNT * 3)
    private val starVerts   = FloatArray(STAR_COUNT * 4)
    private var starVbo     = 0
    private var starVertsBuf: java.nio.FloatBuffer? = null

    // ── Soleil / Lune ─────────────────────────────────────────────────────────
    private var billboardShader: ShaderProgram? = null
    private var bAPos = 0; private var bAUv = 0; private var bUMvp = 0
    private var bUTex = 0; private var bUAlpha = 0; private var bUMask = 0
    private var sunTex  = 0
    private var moonTex = 0
    private var billboardVbo = 0
    private val billboardBuf = ByteBuffer.allocateDirect(6 * 5 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val genDispatcher  = Dispatchers.Default.limitedParallelism(2)
    private val meshDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val building = ConcurrentHashMap.newKeySet<Long>()

    private var lastCx = Int.MAX_VALUE; private var lastCy = Int.MAX_VALUE; private var lastCz = Int.MAX_VALUE
    private var lastFrameNs = 0L

    private val frustum = Array(6) { FloatArray(4) }
    private var cleanupCounter = 0
    private val CLEANUP_INTERVAL = 60   // nettoyage plus fréquent (~2s)
    private val MAX_MESHES = 1200       // cap mémoire : éviction au-delà
    private val MAX_LIGHTS = 32
    private val lightSources = HashMap<Triple<Int,Int,Int>, Float>()
    private val lightData = FloatArray(MAX_LIGHTS * 4)       // réutilisé chaque frame
    private var cachedLightCount = 0
    private var lightRefreshCounter = 0
    private var lightsDirty = true
    private var lastLightCamX = 0.0; private var lastLightCamY = 0.0; private var lastLightCamZ = 0.0
    private val columnsWithMesh = HashSet<Long>(2048)        // réutilisé chaque frame
    private val TARGET_FRAME_NS = 33_333_333L

    // Physique
    var playerMode = PlayerMode.WALK
    @Volatile var pendingMode: PlayerMode? = null
    private var velocityY = 0f
    private var onGround = false
    private var prevFlyUp = false
    private var stepUpRemaining = 0.0

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
        ICE           to 0.5f,
        SNOW          to 0.2f,
        BRICK_RED     to 4.5f,
        ROCK          to 0.1f,
        ROCK_MOSS     to 0.1f,
        MUSHROOM_RED  to 0.1f,
        MUSHROOM_BROWN to 0.1f,
        MUSHROOM_TAN  to 0.1f,
        TORCH         to 0.2f,
        PLANK         to 1.5f,
        BRICK_GREY    to 3.0f,
        CACTUS        to 0.4f,
        GLASS         to 0.3f,
        GRAVEL_DIRT   to 0.5f,
        TABLE         to 1.5f,
        GRASS_WILD1   to 0.05f, GRASS_WILD2 to 0.05f, GRASS_WILD3 to 0.05f, GRASS_WILD4 to 0.05f,
        GRASS_BROWN   to 0.05f, GRASS_TAN   to 0.05f,
        WHEAT1        to 0.05f, WHEAT2      to 0.05f,  WHEAT3      to 0.05f, WHEAT4      to 0.05f,
        REDSTONE      to 4.0f,
        LEAVES_ORANGE to 0.2f,
        WOOD_WHITE    to 2.0f,
        LEAVES_FALL   to 0.2f,
    ) + (COTTON_AMBER.toInt()..COTTON_YELLOW.toInt()).associate { it.toByte() to 0.8f }

    // ── Ennemis ───────────────────────────────────────────────────────────────

    internal val enemyManager      = EnemyManager(world, worldSeed)
    private val enemyRenderer      = EnemyRenderer()
    private val projRenderer       = ProjectileRenderer()
    internal val passiveMobManager = PassiveMobManager(world, worldSeed)
    private val passiveMobRenderer = PassiveMobRenderer()
    private var laserEnemyDmgTimer = 0f

    // ── Progression joueur ────────────────────────────────────────────────────

    val playerStats = PlayerStats()
    private val upgradeRng = Random(System.currentTimeMillis())
    val projectiles = ArrayList<Projectile>(64)

    @Volatile var gamePaused = false

    var posCallback:      ((String) -> Unit)?                    = null
    var modeCallback:     ((PlayerMode) -> Unit)?                = null
    var miningCallback:   ((progress: Float, block: Byte?) -> Unit)? = null
    var inventoryCallback: ((Map<Byte, Int>) -> Unit)?           = null
    var hotbarCallback:   ((slots: Array<Byte?>, selected: Int) -> Unit)? = null
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)?       = null
    var shieldCallback:   ((current: Int, max: Int) -> Unit)?    = null
    var xpCallback:       ((xp: Int, xpMax: Int, level: Int) -> Unit)? = null
    var levelUpCallback:  ((List<UpgradeOption>) -> Unit)?       = null

    // ── Shaders ───────────────────────────────────────────────────────────────

    private val VERT_WORLD = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_uv;
        uniform mat4 u_mvp;
        uniform vec3 u_chunk_offset;
        out vec2 v_uv;
        out float v_layer;
        out float v_faceDir;
        out vec3 v_worldPos;
        void main() {
            vec3 worldPos = a_pos + u_chunk_offset;
            gl_Position = u_mvp * vec4(worldPos, 1.0);
            v_uv      = a_uv.xy;
            v_layer   = mod(a_uv.z, 128.0);
            v_faceDir = floor(a_uv.z / 128.0);
            v_worldPos = worldPos;
        }
    """.trimIndent()

    private val FRAG_WORLD = """
        #version 300 es
        precision mediump float;
        uniform sampler2DArray u_tex;
        uniform float u_ambient;
        uniform vec4 u_lights[32];
        uniform int u_lightCount;
        uniform float u_time;
        uniform float u_underwater;
        in vec2 v_uv;
        in float v_layer;
        in float v_faceDir;
        in vec3 v_worldPos;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(u_tex, vec3(v_uv, v_layer));
            if (col.a < 0.5) discard;
            float fd = floor(v_faceDir + 0.5);
            float faceLight = fd < 0.5 ? 1.0 : fd < 1.5 ? 0.45 : fd < 3.5 ? 0.72 : 0.62;
            vec3 torchColor = vec3(1.0, 0.72, 0.25);
            vec3 torchContrib = vec3(0.0);
            for (int i = 0; i < u_lightCount; i++) {
                float flicker = 1.0 + 0.015 * sin(u_time * 3.1 + float(i) * 2.1);
                float radius = 24.0 * u_lights[i].w * flicker;
                float d = length(v_worldPos - u_lights[i].xyz);
                float atten = clamp(1.0 - d / radius, 0.0, 1.0);
                atten = atten * atten;
                torchContrib = max(torchContrib, atten * u_lights[i].w * torchColor);
            }
            vec3 lighting = max(vec3(u_ambient), torchContrib);
            fragColor = vec4(col.rgb * faceLight * lighting, 1.0);
            if (u_underwater > 0.5) {
                fragColor = vec4(fragColor.rgb * vec3(0.18, 0.48, 0.88) * 0.55, 1.0);
            }
        }
    """.trimIndent()

    private val FRAG_WATER = """
        #version 300 es
        precision mediump float;
        uniform float u_ambient;
        uniform vec4 u_lights[32];
        uniform int u_lightCount;
        uniform float u_time;
        in vec2 v_uv;
        in float v_layer;
        in float v_faceDir;
        in vec3 v_worldPos;
        out vec4 fragColor;
        void main() {
            float wave = 0.5 + 0.5 * sin(v_worldPos.x * 1.1 + u_time * 1.7)
                                   * sin(v_worldPos.z * 0.85 + u_time * 1.3);
            vec3 deepColor    = vec3(0.05, 0.28, 0.72);
            vec3 shallowColor = vec3(0.16, 0.50, 0.90);
            vec3 baseColor    = mix(deepColor, shallowColor, wave * 0.5 + 0.2);
            float fd = floor(v_faceDir + 0.5);
            float faceLight = fd < 0.5 ? 1.0 : fd < 1.5 ? 0.45 : 0.72;
            vec3 torchColor = vec3(1.0, 0.72, 0.25);
            vec3 torchContrib = vec3(0.0);
            for (int i = 0; i < u_lightCount; i++) {
                float flicker = 1.0 + 0.015 * sin(u_time * 3.1 + float(i) * 2.1);
                float radius = 24.0 * u_lights[i].w * flicker;
                float d = length(v_worldPos - u_lights[i].xyz);
                float atten = clamp(1.0 - d / radius, 0.0, 1.0);
                atten = atten * atten;
                torchContrib = max(torchContrib, atten * u_lights[i].w * torchColor);
            }
            vec3 lighting = max(vec3(u_ambient), torchContrib);
            fragColor = vec4(baseColor * faceLight * lighting, 0.75);
        }
    """.trimIndent()

    private val VERT_LASER = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_color;
        uniform mat4 u_mvp;
        out vec3 v_color;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            v_color = a_color;
        }
    """.trimIndent()

    private val FRAG_LASER = """
        #version 300 es
        precision mediump float;
        in vec3 v_color;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(v_color, 1.0);
        }
    """.trimIndent()

    private val VERT_STAR = """
        #version 300 es
        in vec3 a_pos;
        in float a_brightness;
        uniform mat4 u_mvp;
        out float v_brightness;
        void main() {
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            gl_PointSize = 2.5;
            v_brightness = a_brightness;
        }
    """.trimIndent()

    private val FRAG_STAR = """
        #version 300 es
        precision mediump float;
        in float v_brightness;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(v_brightness, v_brightness, v_brightness, 1.0);
        }
    """.trimIndent()

    private val VERT_BILLBOARD = """
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

    private val FRAG_BILLBOARD = """
        #version 300 es
        precision mediump float;
        uniform sampler2D u_tex;
        uniform float u_alpha;
        uniform float u_mask;
        in vec2 v_uv;
        out vec4 fragColor;
        void main() {
            if (u_mask > 0.5 && length(v_uv - vec2(0.5)) > 0.5) discard;
            vec4 col = texture(u_tex, v_uv);
            if (col.a < 0.05) discard;
            fragColor = vec4(col.rgb, col.a * u_alpha);
        }
    """.trimIndent()

    // ── Lifecycle GL ──────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.682f, 0.910f, 0.973f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        lastFrameNs = System.nanoTime()

        worldShader = ShaderProgram(VERT_WORLD, FRAG_WORLD).also {
            it.use()
            wAPos         = it.attrib("a_pos")
            wAUv          = it.attrib("a_uv")
            wUMvp         = it.uniform("u_mvp")
            wUTex         = it.uniform("u_tex")
            wUChunkOffset = it.uniform("u_chunk_offset")
            wUAmbient     = it.uniform("u_ambient")
            wULights      = it.uniform("u_lights[0]")
            wULightCount  = it.uniform("u_lightCount")
            wUTime        = it.uniform("u_time")
            wUUnderwater  = it.uniform("u_underwater")
        }
        waterShader = ShaderProgram(VERT_WORLD, FRAG_WATER).also {
            it.use()
            wWAPos         = it.attrib("a_pos")
            wWAUv          = it.attrib("a_uv")
            wWUMvp         = it.uniform("u_mvp")
            wWUChunkOffset = it.uniform("u_chunk_offset")
            wWUAmbient     = it.uniform("u_ambient")
            wWULights      = it.uniform("u_lights[0]")
            wWULightCount  = it.uniform("u_lightCount")
            wWUTime        = it.uniform("u_time")
        }
        laserShader = ShaderProgram(VERT_LASER, FRAG_LASER).also {
            it.use()
            lAPos   = it.attrib("a_pos")
            lAColor = it.attrib("a_color")
            lUMvp   = it.uniform("u_mvp")
        }
        starShader = ShaderProgram(VERT_STAR, FRAG_STAR).also {
            it.use()
            sAPos        = it.attrib("a_pos")
            sABrightness = it.attrib("a_brightness")
            sUMvp        = it.uniform("u_mvp")
        }
        initStars()
        initSkyBodies()

        blockTexArray = loadBlockTextures()
        enemyRenderer.onSurfaceCreated(context.assets, enemyManager.familyPool)
        projRenderer.onSurfaceCreated(context.assets)
        passiveMobRenderer.onSurfaceCreated(context.assets)

        enemyManager.playerHpCallback = { hp, max -> playerHpCallback?.invoke(hp, max) }
        enemyManager.shieldCallback   = { cur, max -> shieldCallback?.invoke(cur, max) }

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
            // Restauration progression joueur
            playerStats.level    = savedState.playerLevel
            playerStats.xp       = savedState.playerXp
            playerStats.xpToNext = playerStats.xpRequired(savedState.playerLevel)
            playerStats.damage   = savedState.playerDamage
            playerStats.fireRate = savedState.playerFireRate
            playerStats.maxHp    = savedState.playerMaxHp
            playerStats.shield   = savedState.playerShield
            playerStats.weapons.clear(); playerStats.shootTimers.clear()
            for (key in savedState.playerWeapons) {
                val parts = key.split("_")
                if (parts.size == 2) {
                    val color   = runCatching { WeaponColor.valueOf(parts[0])   }.getOrNull() ?: continue
                    val variant = runCatching { WeaponVariant.valueOf(parts[1]) }.getOrNull() ?: continue
                    playerStats.weapons.add(WeaponDef(color, variant))
                    playerStats.shootTimers.add(0f)
                }
            }
            if (playerStats.weapons.isEmpty()) {
                playerStats.weapons.add(WeaponDef(WeaponColor.WHITE, WeaponVariant.SQUARE))
                playerStats.shootTimers.add(0f)
            }
            enemyManager.playerMaxHp         = savedState.playerMaxHp
            enemyManager.playerHp            = savedState.playerHp.coerceAtMost(savedState.playerMaxHp)
            enemyManager.playerShieldMax     = savedState.playerShield
            enemyManager.playerShieldCurrent = savedState.playerShieldCurrent.coerceAtMost(savedState.playerShield)
            val pcx = camera.chunkX(); val pcy = camera.chunkY(); val pcz = camera.chunkZ()
            for (dy in -1..1) for (dz in -1..1) for (dx in -1..1)
                world.pregenerateChunk(pcx + dx, pcy + dy, pcz + dz)
            val spawn = world.findSpawnPoint()
            enemyManager.worldSpawnX      = spawn[0].toDouble()
            enemyManager.worldSpawnZ      = spawn[2].toDouble()
            passiveMobManager.worldSpawnX = spawn[0].toDouble()
            passiveMobManager.worldSpawnZ = spawn[2].toDouble()
        } else {
            val spawn = world.findSpawnPoint()
            camera.x = spawn[0].toDouble(); camera.y = spawn[1].toDouble(); camera.z = spawn[2].toDouble()
            val pcx = camera.chunkX(); val pcy = camera.chunkY(); val pcz = camera.chunkZ()
            for (dy in -1..1) for (dz in -1..1) for (dx in -1..1)
                world.pregenerateChunk(pcx + dx, pcy + dy, pcz + dz)
            enemyManager.worldSpawnX      = camera.x
            enemyManager.worldSpawnZ      = camera.z
            passiveMobManager.worldSpawnX = camera.x
            passiveMobManager.worldSpawnZ = camera.z
        }
        scheduleInitialLodBuilds()
    }

    private fun scheduleInitialLodBuilds() {
        val cache = lodCache ?: return
        val pcx = camera.chunkX(); val pcz = camera.chunkZ()
        val r = LOD_RADIUS
        scope.launch {
            for ((cx, cz) in cache.cachedColumns()) {
                val dx = cx - pcx; val dz = cz - pcz
                if (dx * dx + dz * dz <= r * r) scheduleLodBuild(cx, cz)
            }
        }
    }

    private fun createTorchBitmap(size: Int): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val s = size.toFloat()
        paint.color = android.graphics.Color.rgb(110, 65, 22)
        canvas.drawRect(s * 0.38f, s * 0.38f, s * 0.62f, s * 1.0f, paint)
        paint.color = android.graphics.Color.rgb(220, 95, 15)
        canvas.drawOval(android.graphics.RectF(s * 0.20f, s * 0.02f, s * 0.80f, s * 0.50f), paint)
        paint.color = android.graphics.Color.rgb(255, 195, 30)
        canvas.drawOval(android.graphics.RectF(s * 0.30f, s * 0.08f, s * 0.70f, s * 0.42f), paint)
        paint.color = android.graphics.Color.rgb(255, 250, 180)
        canvas.drawOval(android.graphics.RectF(s * 0.40f, s * 0.14f, s * 0.60f, s * 0.32f), paint)
        return bmp
    }

    private fun loadBlockTextures(): Int {
        val files = listOf(
            "stone.png", "greystone.png", "greysand.png", "stone_coal.png",
            "stone_gold.png", "stone_diamond.png", "dirt.png", "gravel_stone.png",
            "stone_iron.png", "stone_silver.png", "greystone_ruby.png", "lava.png", "oven.png",
            "redstone_emerald.png", "stone_browniron.png",
            "dirt_grass.png", "grass_top.png",
            "trunk_side.png", "trunk_top.png", "leaves.png",
            "sand.png", "stone_grass.png", "redsand.png",
            "ice.png", "snow.png", "dirt_snow.png", "brick_red.png",
            "rock.png", "rock_moss.png", "mushroom_red.png", "mushroom_brown.png", "mushroom_tan.png",
            "stone_sand_side.png", "stone_snow_side.png"
        )
        val am = context.assets
        val bitmaps = files.map { BitmapFactory.decodeStream(am.open("Cave World/Tiles/$it")) }.toMutableList()
        val w = bitmaps[0].width; val h = bitmaps[0].height
        bitmaps.add(createTorchBitmap(w))                                                       // 34
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/wood_plank.png")))     // 35
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/brick_grey.png")))     // 36
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/cactus_side.png")))    // 37
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/cactus_top.png")))     // 38
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/cactus_inside.png")))  // 39
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/glass.png")))          // 40
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/glass_frame.png")))    // 41
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/gravel_dirt.png")))    // 42
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/table.png")))          // 43
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/grass1.png")))         // 44
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/grass2.png")))         // 45
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/grass3.png")))         // 46
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/grass4.png")))         // 47
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/grass_brown.png")))    // 48
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/grass_tan.png")))      // 49
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/wheat_stage1.png")))   // 50
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/wheat_stage2.png")))   // 51
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/wheat_stage3.png")))   // 52
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/wheat_stage4.png")))   // 53
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/redstone.png")))        // 54
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/redstone_sand.png")))         // 55
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/leaves_orange_transparent.png"))) // 56
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/trunk_white_side.png")))          // 57
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/trunk_white_top.png")))           // 58
        bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/leaves_orange.png")))             // 59
        // Cotton — 34 couleurs, layers 60–93, ordre alphabétique = ordre des IDs
        listOf("amber","black","blue","brown","coral","crimson","cyan","dark_green",
               "gold","green","hot_pink","indigo","lavender","light_blue","lime","magenta",
               "mint","navy","olive","orange","peach","pink","purple","red","rose","salmon",
               "silver","sky","tan","teal","turquoise","violet","white","yellow")
            .forEach { name -> bitmaps.add(BitmapFactory.decodeStream(am.open("Cave World/Tiles/cotton/cotton_$name.png"))) }

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

        if (!gamePaused) {
            when (playerMode) {
                PlayerMode.SPECTATOR -> updateSpectator(dt)
                PlayerMode.WALK      -> updateWalk(dt)
            }
        }
        camera.update()

        elapsed += dt

        if (!gamePaused) {
            updateMining(dt)
            updateAutoShoot(dt)
            updateProjectiles(dt)
        }

        val cx = camera.chunkX(); val cy = camera.chunkY(); val cz = camera.chunkZ()
        if (cx != lastCx || cy != lastCy || cz != lastCz) {
            lastCx = cx; lastCy = cy; lastCz = cz
            world.updateAroundPlayer(cx, cy, cz, camera.fwdX, camera.fwdZ) { chunk -> scheduleChunkBuild(chunk) }
        }

        // Drain complet + déduplication : évite que les chunks générés en premier (lointains)
        // bloquent les proches dans le FIFO. Le tri porte sur TOUS les éléments en attente.
        val pendingSet = HashSet<Long>()
        while (true) { pendingSet.add(world.rebuildQueue.poll() ?: break) }
        val rebuildBatch = pendingSet.sortedBy { key ->
            val dx = world.keyToCx(key) - cx; val dy = world.keyToCy(key) - cy; val dz = world.keyToCz(key) - cz
            dx * dx + dz * dz + dy * dy * 8
        }
        var rebuilt = 0
        for (key in rebuildBatch) {
            if (rebuilt >= 16) { world.rebuildQueue.add(key); continue }
            val chunk = world.getChunkByKey(key) ?: continue
            if (!chunk.generated) { world.rebuildQueue.add(key); continue }
            if (!building.add(key)) { world.rebuildQueue.add(key); continue }
            chunk.meshDirty = false
            val snapVersion = chunk.version
            scope.launch(meshDispatcher) {
                try {
                    val verts      = MeshBuilder.build(chunk, world)
                    val waterVerts = MeshBuilder.buildWater(chunk, world)
                    if (chunk.version == snapVersion) {
                        uploadQueue.add(Triple(key, snapVersion, verts))
                        waterUploadQueue.add(Triple(key, snapVersion, waterVerts))
                    }
                    if (chunk.meshDirty || chunk.version != snapVersion) world.rebuildQueue.add(key)
                } catch (_: OutOfMemoryError) {
                    // Heap plein : re-queue pour réessayer quand la pression mémoire baisse
                    chunk.meshDirty = true
                    world.rebuildQueue.add(key)
                } finally {
                    building.remove(key)
                }
            }
            rebuilt++
        }

        repeat(16) {
            val (key, ver, verts) = uploadQueue.poll() ?: return@repeat
            val chunk = world.getChunkByKey(key) ?: return@repeat
            if (chunk.version == ver) {
                meshes.getOrPut(key) { ChunkMesh() }.upload(verts)
                refreshChunkLightSources(chunk)
                if (chunk.cy in 0..9) scheduleLodBuild(chunk.cx, chunk.cz)
            }
        }
        repeat(16) {
            val (key, verts) = lodUploadQueue.poll() ?: return@repeat
            lodUploadQueueSize.decrementAndGet()
            var isNew = false
            lodMeshes.getOrPut(key) { isNew = true; ChunkMesh() }.upload(verts)
            if (isNew) lodGrid.getOrPut(superKey(lodKeyToCx(key), lodKeyToCz(key))) { ArrayList() }.add(key)
        }
        repeat(16) {
            val (key, ver, verts) = waterUploadQueue.poll() ?: return@repeat
            val chunk = world.getChunkByKey(key) ?: return@repeat
            if (chunk.version == ver) {
                if (verts.isNotEmpty()) waterMeshes.getOrPut(key) { ChunkMesh() }.upload(verts)
                else waterMeshes.remove(key)?.destroy()
            }
        }
        meshes.values.forEach { it.flushPending() }
        lodMeshes.values.forEach { it.flushPending() }
        waterMeshes.values.forEach { it.flushPending() }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0
            lodCache?.saveIfDirty(scope)
            val pcx = camera.chunkX(); val pcy = camera.chunkY(); val pcz = camera.chunkZ()
            val allChunks = world.allChunks()
            val loadedKeys = allChunks.mapTo(HashSet()) { world.chunkKey(it.cx, it.cy, it.cz) }
            // Supprimer les meshes de chunks déchargés
            meshes.keys.filter { it !in loadedKeys }.forEach { key -> meshes.remove(key)?.destroy() }
            waterMeshes.keys.filter { it !in loadedKeys }.forEach { key -> waterMeshes.remove(key)?.destroy() }
            // Nettoyer les sources de lumière des chunks déchargés
            if (lightSources.isNotEmpty()) {
                lightSources.keys.removeAll { (x, y, z) ->
                    val kcx = Math.floorDiv(x, CHUNK_SIZE)
                    val kcy = Math.floorDiv(y, CHUNK_SIZE)
                    val kcz = Math.floorDiv(z, CHUNK_SIZE)
                    world.chunkKey(kcx, kcy, kcz) !in loadedKeys
                }
            }
            // Cap mémoire : éviction des meshes distants uniquement (jamais de re-queue immédiate
            // pour éviter une boucle éviction→rebuild→éviction qui sature le heap).
            if (meshes.size > MAX_MESHES) {
                val toEvict = meshes.size - MAX_MESHES
                val nearR = world.renderRadiusXZ
                // Min-heap de taille toEvict : conserve les toEvict clés les plus éloignées
                // sans trier toute la liste → O(n log toEvict) au lieu de O(n log n).
                val heap = java.util.PriorityQueue<Long>(toEvict + 1,
                    compareBy { key ->
                        val dx = world.keyToCx(key) - pcx
                        val dy = world.keyToCy(key) - pcy
                        val dz = world.keyToCz(key) - pcz
                        dx * dx + dy * dy * 4 + dz * dz
                    })
                for (key in meshes.keys) {
                    if (abs(world.keyToCx(key) - pcx) <= nearR && abs(world.keyToCz(key) - pcz) <= nearR) continue
                    heap.offer(key)
                    if (heap.size > toEvict) heap.poll()
                }
                while (heap.isNotEmpty()) meshes.remove(heap.poll())?.destroy()
            }
            // Lazy scan : reconstruire les chunks proches visibles qui n'ont plus de mesh
            // (suite à une éviction ou à un OOM lors d'un précédent build).
            val nearR = world.renderRadiusXZ
            allChunks
                .filter { c ->
                    c.generated && !c.meshDirty &&
                    abs(c.cx - pcx) <= nearR && abs(c.cz - pcz) <= nearR &&
                    !building.contains(world.chunkKey(c.cx, c.cy, c.cz)) &&
                    !meshes.containsKey(world.chunkKey(c.cx, c.cy, c.cz))
                }
                .forEach { c ->
                    c.meshDirty = true
                    world.rebuildQueue.add(world.chunkKey(c.cx, c.cy, c.cz))
                }
            // Nettoyage et cap LOD
            lodMeshes.keys
                .filter { key -> abs(lodKeyToCx(key) - pcx) > LOD_RADIUS + 4 || abs(lodKeyToCz(key) - pcz) > LOD_RADIUS + 4 }
                .forEach { key -> lodMeshes.remove(key)?.also { it.destroy(); lodGridRemove(key) } }
            if (lodMeshes.size > MAX_LOD_TILES) {
                val toEvictLod = lodMeshes.size - MAX_LOD_TILES
                val lodHeap = java.util.PriorityQueue<Long>(toEvictLod + 1,
                    compareBy { key -> val dx = lodKeyToCx(key) - pcx; val dz = lodKeyToCz(key) - pcz; dx*dx + dz*dz })
                for (key in lodMeshes.keys) { lodHeap.offer(key); if (lodHeap.size > toEvictLod) lodHeap.poll() }
                while (lodHeap.isNotEmpty()) {
                    val key = lodHeap.poll()
                    lodMeshes.remove(key)?.also { it.destroy(); lodGridRemove(key) }
                }
            }
        }

        // ── Cycle jour/nuit ───────────────────────────────────────────────────
        val dayT = dayFraction()
        val (skyR, skyG, skyB) = skyColorFor(dayT)
        GLES30.glClearColor(skyR, skyG, skyB, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // ── Corps célestes (avant le terrain — depth mask off pour laisser le terrain gagner) ──
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        renderSkyBody(sunTex,  sunAngleFor(dayT),  12f, sunAlphaFor(dayT),  false)
        renderSkyBody(moonTex, moonAngleFor(dayT),  8f, moonAlphaFor(dayT), true)
        renderStars(starsAlphaFor(dayT))
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)

        // ── Rendu chunks ─────────────────────────────────────────────────────
        val headUnderwater = isHeadInWater()
        worldShader?.use()
        GLES30.glUniformMatrix4fv(wUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform1f(wUAmbient, if (headUnderwater) ambientFor(dayT) * 0.4f else ambientFor(dayT))
        GLES30.glUniform1f(wUTime, elapsed)
        GLES30.glUniform1f(wUUnderwater, if (headUnderwater) 1f else 0f)

        val camX = camera.x; val camY = camera.y; val camZ = camera.z
        if (++lightRefreshCounter >= 3 || lightsDirty) {
            lightRefreshCounter = 0
            lightsDirty = false
            cachedLightCount = 0
            lightData.fill(0f)
            lightSources.entries
                .filter { (pos, _) ->
                    val dx = pos.first + 0.5 - camX; val dy = pos.second + 0.5 - camY; val dz = pos.third + 0.5 - camZ
                    dx*dx + dy*dy + dz*dz < 160.0 * 160.0
                }
                .sortedBy { (pos, _) ->
                    val dx = pos.first + 0.5 - camX; val dy = pos.second + 0.5 - camY; val dz = pos.third + 0.5 - camZ
                    dx*dx + dy*dy + dz*dz - (dx * camera.fwdX + dz * camera.fwdZ) * 80.0
                }
                .take(MAX_LIGHTS)
                .forEachIndexed { i, (pos, intensity) ->
                    lightData[i*4+0] = (pos.first  + 0.5 - camX).toFloat()
                    lightData[i*4+1] = (pos.second + 0.5 - camY).toFloat()
                    lightData[i*4+2] = (pos.third  + 0.5 - camZ).toFloat()
                    lightData[i*4+3] = intensity
                    cachedLightCount = i + 1
                }
        } else {
            // Recaler les offsets caméra sans recalculer le tri
            for (i in 0 until cachedLightCount) {
                lightData[i*4+0] -= (camX - lastLightCamX).toFloat()
                lightData[i*4+1] -= (camY - lastLightCamY).toFloat()
                lightData[i*4+2] -= (camZ - lastLightCamZ).toFloat()
            }
        }
        lastLightCamX = camX; lastLightCamY = camY; lastLightCamZ = camZ
        GLES30.glUniform4fv(wULights, cachedLightCount.coerceAtLeast(1), lightData, 0)
        GLES30.glUniform1i(wULightCount, cachedLightCount)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, blockTexArray)
        GLES30.glUniform1i(wUTex, 0)

        extractFrustumPlanes(camera.vpMatrix)
        for ((key, mesh) in meshes) {
            val kcx = world.keyToCx(key); val kcy = world.keyToCy(key); val kcz = world.keyToCz(key)
            if (!isChunkInFrustum(kcx, kcy, kcz)) continue
            val offX = (kcx.toDouble() * CHUNK_SIZE - camera.x).toFloat()
            val offY = (kcy.toDouble() * CHUNK_SIZE - camera.y).toFloat()
            val offZ = (kcz.toDouble() * CHUNK_SIZE - camera.z).toFloat()
            GLES30.glUniform3f(wUChunkOffset, offX, offY, offZ)
            mesh.draw(wAPos, wAUv)
        }

        // ── Rendu LOD (zones visitées visibles de loin) ───────────────────────
        // Critère de masquage : mesh VBO présent pour un chunk surface (cy 0..9).
        // Les meshes de la zone proche ne sont plus évincés par MAX_MESHES → critère stable,
        // pas de boucle. Le LOD reste visible pendant le gap génération→upload (pas de trou).
        columnsWithMesh.clear()
        meshes.keys.forEach { k -> columnsWithMesh.add(lodKey(world.keyToCx(k), world.keyToCz(k))) }
        // Itère par super-tuiles (8×8 colonnes) : ~125 tests frustum au lieu de 8000.
        for ((sk, keys) in lodGrid) {
            val scx = superKeyToCx(sk) * LOD_SUPER
            val scz = superKeyToCz(sk) * LOD_SUPER
            if (!isLodSuperTileInFrustum(scx, scz)) continue
            for (key in keys) {
                if (columnsWithMesh.contains(key)) continue
                val mesh = lodMeshes[key] ?: continue
                val lcx = lodKeyToCx(key); val lcz = lodKeyToCz(key)
                GLES30.glUniform3f(wUChunkOffset,
                    (lcx.toDouble() * CHUNK_SIZE - camera.x).toFloat(),
                    -camera.y.toFloat(),
                    (lcz.toDouble() * CHUNK_SIZE - camera.z).toFloat())
                mesh.draw(wAPos, wAUv)
            }
        }

        // ── Mobs passifs ──────────────────────────────────────────────────────
        if (!gamePaused) passiveMobManager.update(dt, camera.x, camera.y, camera.z)
        passiveMobRenderer.render(
            passiveMobManager.mobs,
            camera.x, camera.y, camera.z,
            camera.vpMatrix, ambientFor(dayT)
        )

        // ── Mise à jour + rendu ennemis ───────────────────────────────────────
        if (!gamePaused) enemyManager.update(dt, camera.x, camera.y, camera.z)
        enemyRenderer.render(
            enemyManager.enemies,
            camera.x, camera.y, camera.z,
            camera.yaw, camera.vpMatrix
        )

        // ── Rendu projectiles ─────────────────────────────────────────────────
        projRenderer.render(projectiles, camera.x, camera.y, camera.z, camera.yaw, camera.vpMatrix)

        // ── Rendu laser + highlight ───────────────────────────────────────────
        renderLaserAndHighlight()

        // ── Passe eau (blending semi-transparent, après géométrie opaque) ─────
        val waterAmbient = if (headUnderwater) ambientFor(dayT) * 0.4f else ambientFor(dayT)
        waterShader?.use()
        GLES30.glUniformMatrix4fv(wWUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform1f(wWUAmbient, waterAmbient)
        GLES30.glUniform1f(wWUTime, elapsed)
        GLES30.glUniform4fv(wWULights, cachedLightCount.coerceAtLeast(1), lightData, 0)
        GLES30.glUniform1i(wWULightCount, cachedLightCount)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        for ((key, mesh) in waterMeshes) {
            val kcx = world.keyToCx(key); val kcy = world.keyToCy(key); val kcz = world.keyToCz(key)
            if (!isChunkInFrustum(kcx, kcy, kcz)) continue
            val offX = (kcx.toDouble() * CHUNK_SIZE - camera.x).toFloat()
            val offY = (kcy.toDouble() * CHUNK_SIZE - camera.y).toFloat()
            val offZ = (kcz.toDouble() * CHUNK_SIZE - camera.z).toFloat()
            GLES30.glUniform3f(wWUChunkOffset, offX, offY, offZ)
            mesh.draw(wWAPos, wWAUv)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        posCallback?.invoke(camera.posString())

        val elapsed2 = System.nanoTime() - now
        val sleepNs = TARGET_FRAME_NS - elapsed2
        if (sleepNs > 1_000_000L) Thread.sleep(sleepNs / 1_000_000L)
    }

    // ── Tir automatique ───────────────────────────────────────────────────────

    private fun updateAutoShoot(dt: Float) {
        val liveEnemies = enemyManager.enemies.filter { it.hp > 0 }
        if (liveEnemies.isEmpty()) return

        val closest = liveEnemies.minByOrNull { e ->
            val dx = e.x - camera.x; val dz = e.z - camera.z
            dx * dx + dz * dz
        } ?: return

        val dxC = closest.x - camera.x; val dzC = closest.z - camera.z
        if (dxC * dxC + dzC * dzC > AUTO_SHOOT_RANGE * AUTO_SHOOT_RANGE) return

        for (i in playerStats.weapons.indices) {
            playerStats.shootTimers[i] -= dt
            if (playerStats.shootTimers[i] <= 0f) {
                playerStats.shootTimers[i] = playerStats.fireRate
                val tx = closest.x; val ty = closest.y + closest.baseScale * 0.5; val tz = closest.z
                val spawnY = camera.y + 0.8
                val dx = tx - camera.x; val dy = ty - spawnY; val dz = tz - camera.z
                val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001)
                projectiles.add(Projectile(
                    camera.x, spawnY, camera.z,
                    dx / d, dy / d, dz / d,
                    PROJ_SPEED, playerStats.damage, playerStats.weapons[i]
                ))
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            val step = (p.speed * dt).toDouble()
            p.x += p.dirX * step; p.y += p.dirY * step; p.z += p.dirZ * step
            p.travelDist += step
            if (p.travelDist > PROJ_MAX_DIST) { iter.remove(); continue }

            val hit = enemyManager.enemies.find { e ->
                if (e.hp <= 0) return@find false
                val r = e.type.radius.toDouble() + 0.3
                val dx = p.x - e.x; val dy = p.y - e.y; val dz = p.z - e.z
                dx * dx + dy * dy * 0.5 + dz * dz < r * r
            }
            if (hit != null) {
                val wasAlive = hit.hp > 0
                enemyManager.damageEnemy(hit, p.damage)
                if (wasAlive && hit.hp <= 0) handleKill(hit)
                iter.remove()
            }
        }
    }

    private fun handleKill(e: Enemy) {
        val xpGain = if (e.isBoss) 5 * e.level else e.level
        val leveledUp = playerStats.addXp(xpGain)
        xpCallback?.invoke(playerStats.xp, playerStats.xpToNext, playerStats.level)
        if (leveledUp) {
            gamePaused = true
            val options = playerStats.pickThreeUpgrades(upgradeRng)
            levelUpCallback?.invoke(options)
        }
    }

    fun applyUpgrade(option: UpgradeOption) {
        playerStats.applyUpgrade(option.type)
        when (option.type) {
            UpgradeType.MAX_HP -> {
                enemyManager.playerMaxHp = playerStats.maxHp
                enemyManager.playerHp = (enemyManager.playerHp + 4).coerceAtMost(playerStats.maxHp)
                enemyManager.playerHpCallback?.invoke(enemyManager.playerHp, enemyManager.playerMaxHp)
            }
            UpgradeType.SHIELD -> {
                enemyManager.playerShieldMax = playerStats.shield
                enemyManager.playerShieldCurrent = (enemyManager.playerShieldCurrent + 5)
                    .coerceAtMost(playerStats.shield)
                shieldCallback?.invoke(enemyManager.playerShieldCurrent, enemyManager.playerShieldMax)
            }
            else -> {}
        }
        gamePaused = false
    }

    // ── Minage ────────────────────────────────────────────────────────────────

    private val MINE_REACH = 6

    private fun updateMining(dt: Float) {
        if (touch.placeRequested) {
            touch.placeRequested = false
            placeBlock()
        }

        if (touch.laserActive) {
            val hitEnemy = enemyManager.hitByLaser(
                camera.x, camera.y, camera.z,
                camera.lookX, camera.lookY, camera.lookZ,
                MINE_REACH.toFloat()
            )
            if (hitEnemy != null) {
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
                    val wasAlive = hitEnemy.hp > 0
                    enemyManager.damageEnemy(hitEnemy, 1)
                    if (wasAlive && hitEnemy.hp <= 0) handleKill(hitEnemy)
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
            inventoryCallback?.invoke(inventory.toMap())
            mineTarget = null
            mineDamage = 0f
            miningCallback?.invoke(0f, null)
        }
    }

    private fun refreshChunkLightSources(chunk: Chunk) {
        lightsDirty = true
        val wx0 = chunk.worldX; val wy0 = chunk.worldY; val wz0 = chunk.worldZ
        val wxEnd = wx0 + CHUNK_SIZE; val wyEnd = wy0 + CHUNK_SIZE; val wzEnd = wz0 + CHUNK_SIZE
        lightSources.keys.removeAll { (x, y, z) ->
            x in wx0 until wxEnd && y in wy0 until wyEnd && z in wz0 until wzEnd
        }
        for (ly in 0 until CHUNK_SIZE)
        for (lz in 0 until CHUNK_SIZE)
        for (lx in 0 until CHUNK_SIZE) {
            val intensity = when (chunk.blockAt(lx, ly, lz)) {
                TORCH -> 1.0f
                LAVA  -> 0.85f
                else  -> 0f
            }
            if (intensity > 0f) lightSources[Triple(wx0 + lx, wy0 + ly, wz0 + lz)] = intensity
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
            val verts      = MeshBuilder.build(chunk, world)
            val waterVerts = MeshBuilder.buildWater(chunk, world)
            meshes.getOrPut(key) { ChunkMesh() }.upload(verts)
            if (waterVerts.isNotEmpty()) waterMeshes.getOrPut(key) { ChunkMesh() }.upload(waterVerts)
            else waterMeshes.remove(key)?.destroy()
            refreshChunkLightSources(chunk)
            if (ncy in 0..9) {
                lodCache?.invalidate(ncx, ncz)
                scheduleLodBuild(ncx, ncz)
            }
        }
    }

    private fun raycastBlock(): RayHit? {
        val dirX = camera.lookX; val dirY = camera.lookY; val dirZ = camera.lookZ

        var bx = floorInt(camera.x); var by = floorInt(camera.y); var bz = floorInt(camera.z)

        val stepX = if (dirX > 0) 1 else -1
        val stepY = if (dirY > 0) 1 else -1
        val stepZ = if (dirZ > 0) 1 else -1

        val tDX = if (dirX != 0f) abs(1f / dirX) else Float.MAX_VALUE
        val tDY = if (dirY != 0f) abs(1f / dirY) else Float.MAX_VALUE
        val tDZ = if (dirZ != 0f) abs(1f / dirZ) else Float.MAX_VALUE

        var tMaxX = if (dirX > 0) ((bx + 1).toDouble() - camera.x).toFloat() * tDX else (camera.x - bx.toDouble()).toFloat() * tDX
        var tMaxY = if (dirY > 0) ((by + 1).toDouble() - camera.y).toFloat() * tDY else (camera.y - by.toDouble()).toFloat() * tDY
        var tMaxZ = if (dirZ > 0) ((bz + 1).toDouble() - camera.z).toFloat() * tDZ else (camera.z - bz.toDouble()).toFloat() * tDZ

        var fnx = 0; var fny = 0; var fnz = -1

        repeat(MINE_REACH * 4) {
            val b = worldBlockAt(bx, by, bz)
            if (b != AIR && !isWater(b)) return RayHit(bx, by, bz, fnx, fny, fnz)
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

    private fun buildLaserVerts(target: RayHit): FloatArray {
        val ex = (target.bx.toDouble() + 0.5 + target.fnx * 0.5 - camera.x).toFloat()
        val ey = (target.by.toDouble() + 0.5 + target.fny * 0.5 - camera.y).toFloat()
        val ez = (target.bz.toDouble() + 0.5 + target.fnz * 0.5 - camera.z).toFloat()

        val yawRad = Math.toRadians(camera.yaw.toDouble()).toFloat()
        val rX = cos(yawRad); val rZ = -sin(yawRad)
        val sx = rX * 0.35f; val sy = -0.25f; val sz = rZ * 0.35f

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
            emit(x0+rvX*hw,y0+rvY*hw,z0+rvZ*hw,r,g,b); emit(x1-rvX*hw,y1-rvY*hw,z1-rvZ*hw,r,g,b); emit(x1+rvX*hw,y1+ruY*hw,z1+ruZ*hw,r,g,b)
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
            val cx2 = fny*0f-fnz*1f; val cy2 = fnz*0f-fnx*0f; val cz2 = fnx*1f-fny*0f
            val cl = sqrt(cx2*cx2+cy2*cy2+cz2*cz2).coerceAtLeast(0.001f)
            t1x=cx2/cl; t1y=cy2/cl; t1z=cz2/cl
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

    // ── LOD helpers ──────────────────────────────────────────────────────────

    private fun lodKey(cx: Int, cz: Int): Long =
        (cx.toLong() and 0xFFFFF) or ((cz.toLong() and 0xFFFFF) shl 20)

    private fun lodKeyToCx(key: Long): Int {
        val v = (key and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v
    }
    private fun lodKeyToCz(key: Long): Int {
        val v = ((key shr 20) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v
    }

    private fun scheduleLodBuild(cx: Int, cz: Int) {
        val key = lodKey(cx, cz)
        if (!lodBuilding.add(key)) return
        scope.launch {
            val verts = LodBuilder.buildColumn(cx, cz, world, lodCache)
            if (verts.isNotEmpty() && lodUploadQueueSize.get() < 64) {
                lodUploadQueueSize.incrementAndGet()
                lodUploadQueue.add(Pair(key, verts))
            } else if (verts.isNotEmpty()) {
                // Queue saturée : on relibère le verrou pour replanifier plus tard
                lodBuilding.remove(key)
                return@launch
            }
            lodBuilding.remove(key)
        }
    }

    // ── Super-tuiles LOD ──────────────────────────────────────────────────────

    private fun superKey(lcx: Int, lcz: Int): Long {
        val scx = Math.floorDiv(lcx, LOD_SUPER); val scz = Math.floorDiv(lcz, LOD_SUPER)
        return (scx.toLong() and 0xFFFFF) or ((scz.toLong() and 0xFFFFF) shl 20)
    }
    private fun superKeyToCx(sk: Long): Int { val v = (sk and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }
    private fun superKeyToCz(sk: Long): Int { val v = ((sk shr 20) and 0xFFFFF).toInt(); return if (v >= 0x80000) v - 0x100000 else v }

    private fun lodGridRemove(key: Long) {
        val sk = superKey(lodKeyToCx(key), lodKeyToCz(key))
        val list = lodGrid[sk] ?: return
        list.remove(key)
        if (list.isEmpty()) lodGrid.remove(sk)
    }

    // Frustum test sur une boîte 8×8 chunks (couvre toute la bande de surface Y 0..160).
    private fun isLodSuperTileInFrustum(scx: Int, scz: Int): Boolean {
        val w = (LOD_SUPER * CHUNK_SIZE).toFloat()
        val x0 = (scx.toDouble() * CHUNK_SIZE - camera.x).toFloat(); val x1 = x0 + w
        val y0 = -camera.y.toFloat();                                  val y1 = y0 + 160f
        val z0 = (scz.toDouble() * CHUNK_SIZE - camera.z).toFloat();  val z1 = z0 + w
        for (p in frustum) {
            val px = if (p[0] >= 0f) x1 else x0
            val py = if (p[1] >= 0f) y1 else y0
            val pz = if (p[2] >= 0f) z1 else z0
            if (p[0]*px + p[1]*py + p[2]*pz + p[3] < 0f) return false
        }
        return true
    }

    // Frustum test élargi en Y pour couvrir toute la bande de surface (Y 0..160).
    private fun isLodColumnInFrustum(cx: Int, cz: Int): Boolean {
        val x0 = (cx.toDouble() * CHUNK_SIZE - camera.x).toFloat(); val x1 = x0 + CHUNK_SIZE
        val y0 = -camera.y.toFloat();                                val y1 = y0 + 160f
        val z0 = (cz.toDouble() * CHUNK_SIZE - camera.z).toFloat(); val z1 = z0 + CHUNK_SIZE
        for (p in frustum) {
            val px = if (p[0] >= 0f) x1 else x0
            val py = if (p[1] >= 0f) y1 else y0
            val pz = if (p[2] >= 0f) z1 else z0
            if (p[0]*px + p[1]*py + p[2]*pz + p[3] < 0f) return false
        }
        return true
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

    // ── Cycle jour/nuit ───────────────────────────────────────────────────────

    var dayNightInverted = false

    fun toggleDayNight() { dayNightInverted = !dayNightInverted }

    private fun dayFraction(): Float {
        val cal = Calendar.getInstance()
        val sec = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
        val base = sec / 86400f
        return if (dayNightInverted) (base + 0.5f) % 1f else base
    }

    private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    private fun skyColorFor(t: Float): Triple<Float, Float, Float> {
        // couleurs clés : nuit / aube / jour / crépuscule / nuit
        val night  = Triple(0.010f, 0.015f, 0.060f)
        val dawn1  = Triple(0.350f, 0.130f, 0.050f)
        val dawn2  = Triple(0.980f, 0.520f, 0.180f)
        val day    = Triple(0.682f, 0.910f, 0.973f)
        val dusk2  = Triple(0.980f, 0.480f, 0.150f)
        val dusk1  = Triple(0.300f, 0.090f, 0.060f)

        fun lerp3(a: Triple<Float,Float,Float>, b: Triple<Float,Float,Float>, f: Float) =
            Triple(lerpF(a.first,b.first,f), lerpF(a.second,b.second,f), lerpF(a.third,b.third,f))

        return when {
            t < 0.208f -> night                                         // 00h–05h
            t < 0.250f -> lerp3(night, dawn1, (t-0.208f)/0.042f)       // 05h–06h
            t < 0.281f -> lerp3(dawn1, dawn2, (t-0.250f)/0.031f)       // 06h–06h45
            t < 0.313f -> lerp3(dawn2, day,   (t-0.281f)/0.032f)       // 06h45–07h30
            t < 0.708f -> day                                           // 07h30–17h
            t < 0.740f -> lerp3(day,   dusk2, (t-0.708f)/0.032f)       // 17h–17h45
            t < 0.771f -> lerp3(dusk2, dusk1, (t-0.740f)/0.031f)       // 17h45–18h30
            t < 0.833f -> lerp3(dusk1, night, (t-0.771f)/0.062f)       // 18h30–20h
            else       -> night                                         // 20h–00h
        }
    }

    private fun ambientFor(t: Float): Float = when {
        t < 0.208f -> 0.08f
        t < 0.313f -> lerpF(0.08f, 1.00f, (t-0.208f)/0.105f)
        t < 0.708f -> 1.00f
        t < 0.833f -> lerpF(1.00f, 0.08f, (t-0.708f)/0.125f)
        else       -> 0.08f
    }

    private fun starsAlphaFor(t: Float): Float = when {
        t < 0.208f -> 1.00f
        t < 0.271f -> lerpF(1.00f, 0.00f, (t-0.208f)/0.063f)
        t < 0.740f -> 0.00f
        t < 0.792f -> lerpF(0.00f, 1.00f, (t-0.740f)/0.052f)
        else       -> 1.00f
    }

    private fun sunAngleFor(t: Float): Float  = ((t - 0.25f) / 0.5f * PI.toFloat()).coerceIn(0f, PI.toFloat())
    private fun moonAngleFor(t: Float): Float = sunAngleFor((t + 0.5f) % 1.0f)

    private fun sunAlphaFor(t: Float): Float = when {
        t < 0.21f || t > 0.79f -> 0f
        t < 0.27f -> (t - 0.21f) / 0.06f
        t < 0.73f -> 1f
        else      -> (0.79f - t) / 0.06f
    }

    private fun moonAlphaFor(t: Float): Float {
        val mt = (t + 0.5f) % 1.0f
        return when {
            mt < 0.21f || mt > 0.79f -> 0f
            mt < 0.27f -> (mt - 0.21f) / 0.06f
            mt < 0.73f -> 1f
            else       -> (0.79f - mt) / 0.06f
        }
    }

    private fun loadSkyTexture(assetPath: String): Int {
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); val texId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        val bmp = BitmapFactory.decodeStream(context.assets.open(assetPath))
        val buf = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buf); buf.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, bmp.width, bmp.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        bmp.recycle()
        return texId
    }

    private fun initSkyBodies() {
        billboardShader = ShaderProgram(VERT_BILLBOARD, FRAG_BILLBOARD).also {
            it.use()
            bAPos   = it.attrib("a_pos")
            bAUv    = it.attrib("a_uv")
            bUMvp   = it.uniform("u_mvp")
            bUTex   = it.uniform("u_tex")
            bUAlpha = it.uniform("u_alpha")
            bUMask  = it.uniform("u_mask")
        }
        sunTex  = loadSkyTexture("Assets/Image/Sun.png")
        moonTex = loadSkyTexture("Assets/Image/FullMoon2010.png")
        val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0); billboardVbo = ids[0]
    }

    private fun renderSkyBody(texId: Int, angle: Float, halfSize: Float, alpha: Float, maskCircle: Boolean) {
        if (alpha <= 0f) return
        val sh = billboardShader ?: return

        // Floating origin : la caméra est à (0,0,0) en repère OpenGL.
        // Les positions sont donc directement relatives à l'origine.
        val cx = cos(angle).toFloat() * STAR_RADIUS
        val cy = sin(angle).toFloat() * STAR_RADIUS
        val cz = 0f

        val yawRad   = Math.toRadians(camera.yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(camera.pitch.toDouble()).toFloat()

        // Vecteur right caméra (horizontal, indépendant du pitch)
        val rX =  cos(yawRad) * halfSize
        val rZ = -sin(yawRad) * halfSize

        // Vecteur up caméra tenant compte du pitch (pitch > 0 = regard vers le bas)
        // Dérivé de setLookAtM(0,0,0, lookX,lookY,lookZ, 0,1,0)
        val uX = sin(pitchRad) * sin(yawRad) * halfSize
        val uY = cos(pitchRad) * halfSize
        val uZ = sin(pitchRad) * cos(yawRad) * halfSize

        val v = floatArrayOf(
            cx-rX-uX, cy-uY, cz-rZ-uZ,  0f, 1f,  // BL
            cx+rX-uX, cy-uY, cz+rZ-uZ,  1f, 1f,  // BR
            cx+rX+uX, cy+uY, cz+rZ+uZ,  1f, 0f,  // TR
            cx-rX-uX, cy-uY, cz-rZ-uZ,  0f, 1f,  // BL
            cx+rX+uX, cy+uY, cz+rZ+uZ,  1f, 0f,  // TR
            cx-rX+uX, cy+uY, cz-rZ+uZ,  0f, 0f,  // TL
        )
        billboardBuf.position(0); billboardBuf.put(v); billboardBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, billboardVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, v.size * 4, billboardBuf, GLES30.GL_DYNAMIC_DRAW)

        sh.use()
        GLES30.glUniformMatrix4fv(bUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform1f(bUAlpha, alpha)
        GLES30.glUniform1f(bUMask, if (maskCircle) 1f else 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(bUTex, 0)

        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(bAPos)
        GLES30.glVertexAttribPointer(bAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(bAUv)
        GLES30.glVertexAttribPointer(bAUv, 2, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glDisableVertexAttribArray(bAPos)
        GLES30.glDisableVertexAttribArray(bAUv)
    }

    private fun initStars() {
        val rng = Random(0xBEEFCAFEL)
        var i = 0
        repeat(STAR_COUNT) {
            val cosTheta = rng.nextFloat()
            val sinTheta = sqrt(1f - cosTheta * cosTheta)
            val phi = rng.nextFloat() * 2f * PI.toFloat()
            starDirs[i++] = sinTheta * cos(phi) * STAR_RADIUS
            starDirs[i++] = cosTheta * STAR_RADIUS
            starDirs[i++] = sinTheta * sin(phi) * STAR_RADIUS
        }
        val buf = ByteBuffer.allocateDirect(STAR_COUNT * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        starVertsBuf = buf
        val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0); starVbo = ids[0]
    }

    private fun renderStars(alpha: Float) {
        if (alpha <= 0f) return
        val sh = starShader ?: return
        // Floating origin : positions relatives à l'origine (= caméra en repère OpenGL)
        for (i in 0 until STAR_COUNT) {
            starVerts[i*4+0] = starDirs[i*3+0]
            starVerts[i*4+1] = starDirs[i*3+1]
            starVerts[i*4+2] = starDirs[i*3+2]
            starVerts[i*4+3] = alpha
        }
        val buf = starVertsBuf ?: return
        buf.position(0); buf.put(starVerts); buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, starVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, STAR_COUNT * 4 * 4, buf, GLES30.GL_DYNAMIC_DRAW)
        sh.use()
        GLES30.glUniformMatrix4fv(sUMvp, 1, false, camera.vpMatrix, 0)
        val stride = 4 * 4
        GLES30.glEnableVertexAttribArray(sAPos)
        GLES30.glVertexAttribPointer(sAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(sABrightness)
        GLES30.glVertexAttribPointer(sABrightness, 1, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, STAR_COUNT)
        GLES30.glDisableVertexAttribArray(sAPos)
        GLES30.glDisableVertexAttribArray(sABrightness)
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
        val inWater = isBodyInWater()
        val hSpeed = when {
            inWater  -> 3f * dt
            onGround -> 7f * dt
            else     -> 4f * dt
        }
        val dx = (fX * touch.moveForward * hSpeed - rX * touch.moveRight * hSpeed).toDouble()
        val dz = (fZ * touch.moveForward * hSpeed - rZ * touch.moveRight * hSpeed).toDouble()
        val jumpPressed = touch.flyUp && !prevFlyUp
        prevFlyUp = touch.flyUp

        if (!collidesAt(camera.x + dx, camera.y, camera.z)) {
            camera.x += dx
        } else if ((onGround || inWater) && dx != 0.0 && !collidesAt(camera.x + dx, camera.y + 1.0, camera.z)) {
            if (stepUpRemaining == 0.0) stepUpRemaining = 1.0
        }
        if (!collidesAt(camera.x, camera.y, camera.z + dz)) {
            camera.z += dz
        } else if ((onGround || inWater) && dz != 0.0 && !collidesAt(camera.x, camera.y + 1.0, camera.z + dz)) {
            if (stepUpRemaining == 0.0) stepUpRemaining = 1.0
        }
        if (stepUpRemaining > 0.0) {
            val rise = minOf(stepUpRemaining, 12.0 * dt)
            if (!collidesAt(camera.x, camera.y + rise, camera.z)) {
                camera.y += rise
                stepUpRemaining -= rise
            } else {
                stepUpRemaining = 0.0
            }
            velocityY = 0f
            onGround = (stepUpRemaining == 0.0)
        } else if (inWater) {
            // Physique eau : gravité réduite, nager vers le haut en appuyant saut
            if (touch.flyUp) velocityY = (velocityY + 5f * dt).coerceAtMost(3f)
            velocityY -= 3f * dt
            velocityY = velocityY.coerceAtLeast(-2f)
            val dy = (velocityY * dt).toDouble()
            if (!collidesAt(camera.x, camera.y + dy, camera.z)) {
                camera.y += dy
            } else {
                if (velocityY < 0f) {
                    var lo = camera.y + dy; var hi = camera.y
                    repeat(8) { val mid = (lo + hi) * 0.5; if (collidesAt(camera.x, mid, camera.z)) lo = mid else hi = mid }
                    camera.y = hi
                }
                velocityY = 0f
            }
            onGround = false
        } else {
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
    }

    // ── Collision ─────────────────────────────────────────────────────────────

    private fun isBodyInWater(): Boolean {
        val bx = floorInt(camera.x); val by = floorInt(camera.y - 0.9); val bz = floorInt(camera.z)
        return isWater(worldBlockAt(bx, by, bz))
    }

    private fun isHeadInWater(): Boolean {
        val bx = floorInt(camera.x); val by = floorInt(camera.y - 0.1); val bz = floorInt(camera.z)
        return isWater(worldBlockAt(bx, by, bz))
    }

    private fun collidesAt(px: Double, py: Double, pz: Double): Boolean {
        val x0=floorInt(px-0.3); val x1=floorInt(px+0.29)
        val y0=floorInt(py-1.62); val y1=floorInt(py+0.18)
        val z0=floorInt(pz-0.3); val z1=floorInt(pz+0.29)
        for (bz in z0..z1) for (by in y0..y1) for (bx in x0..x1) {
            val b = worldBlockAt(bx, by, bz)
            if (b != AIR && !isDecoration(b) && !isWater(b)) return true
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
            try {
                world.generate(chunk)
                world.markGenerated(chunk)
            } catch (_: Throwable) {
                // Libère le chunk (OOM inclus) pour qu'il puisse être regénéré
                world.abandonChunk(chunk)
            } finally {
                building.remove(key)
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
        lodCache?.shutdown()
        scope.cancel()
        storage?.shutdown()
        meshes.values.forEach { it.destroy() }
        waterMeshes.values.forEach { it.destroy() }
        worldShader?.destroy()
        laserShader?.destroy()
        waterShader?.destroy()
        enemyRenderer.destroy()
        projRenderer.destroy()
        passiveMobRenderer.destroy()
        if (blockTexArray != 0) GLES30.glDeleteTextures(1, intArrayOf(blockTexArray), 0)
        if (transientVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(transientVbo), 0)
    }

    companion object {
        private const val AUTO_SHOOT_RANGE = 30.0
        private const val PROJ_SPEED       = 15f
        private const val PROJ_MAX_DIST    = 40.0
    }
}
