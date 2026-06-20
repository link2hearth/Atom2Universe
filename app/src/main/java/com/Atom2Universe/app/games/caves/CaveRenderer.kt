package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.Atom2Universe.app.games.caves.entity.EnemyManager
import com.Atom2Universe.app.games.caves.entity.ImpactParticle
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.ORIENT_AXIS
import com.Atom2Universe.app.games.caves.node.ORIENT_FACING
import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.ItemRegistry
import com.Atom2Universe.app.games.caves.node.LootNode
import com.Atom2Universe.app.games.caves.node.LootTableRegistry
import com.Atom2Universe.app.games.caves.node.MobRegistry
import com.Atom2Universe.app.games.caves.node.PhysicsNode
import com.Atom2Universe.app.games.caves.node.PlayerNode
import com.Atom2Universe.app.games.caves.entity.Projectile
import com.Atom2Universe.app.games.caves.entity.PlayerStats
import com.Atom2Universe.app.games.caves.entity.WeaponColor
import com.Atom2Universe.app.games.caves.entity.WeaponDef
import com.Atom2Universe.app.games.caves.entity.WeaponVariant
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.render.Camera
import com.Atom2Universe.app.games.caves.render.ChunkMesh
import com.Atom2Universe.app.games.caves.render.MobModels
import com.Atom2Universe.app.games.caves.render.EnemyRenderer
import com.Atom2Universe.app.games.caves.render.ProjectileRenderer
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
        val inventory: Map<Short, Int>,
        val hotbar: List<Short?>,
        val playerHp: Int = 20,
        val playerLevel: Int = 1,
        val playerXp: Int = 0,
        val playerDamage: Int = 2,
        val playerFireRate: Float = 1.5f,
        val playerMaxHp: Int = 20,
        val playerShield: Int = 0,
        val playerShieldCurrent: Int = 0,
        val playerWeapons: List<String> = listOf("WHITE_SQUARE"),
        val wardStonePositions: List<Pair<Double, Double>> = emptyList(),
        val skillAthleticsXp:  Int = 0,
        val skillSpeedXp:      Int = 0,
        val skillEnduranceXp:  Int = 0,
        val skillAcrobaticsXp: Int = 0
    )

    val camera = Camera(8.0, 8.0, 8.0)
    private val storage = worldId?.let {
        CaveWorldChunkStorage(java.io.File(context.filesDir, "cave_worlds/$it"))
    }
    internal val world = World(seed = worldSeed, storage = storage)
    private val meshes = ConcurrentHashMap<Long, ChunkMesh>()
    private val uploadQueue = ConcurrentLinkedQueue<Triple<Long, Int, FloatArray>>()

    private val lodMeshes      = ConcurrentHashMap<Long, ChunkMesh>()
    private val lodBuilding    = ConcurrentHashMap.newKeySet<Long>()
    private val lodUploadQueue = ConcurrentLinkedQueue<Pair<Long, FloatArray>>()
    private val lodUploadQueueSize = java.util.concurrent.atomic.AtomicInteger(0)
    private val LOD_RADIUS   = 90
    private val INITIAL_LOD_BUILD_LIMIT = 512
    private val MAX_LOD_TILES = 8000
    private val LOD_SUPER    = 8                           // 8×8 = 64 colonnes par super-tuile
    // Hauteur (blocs) de la bande de surface, pour les bounding-box de frustum LOD.
    private val SURFACE_BAND_H = ((SURFACE_CY_MAX + 1) * CHUNK_SIZE).toFloat()
    private val lodGrid      = HashMap<Long, ArrayList<Long>>() // super-tuile → liste de clés LOD
    private val lodCache = worldId?.let {
        LodCache(java.io.File(context.filesDir, "cave_worlds/$it/lod_cache.bin"))
    }

    private var worldShader: ShaderProgram? = null
    private var laserShader: ShaderProgram? = null
    private var starShader:  ShaderProgram? = null
    private var waterShader: ShaderProgram? = null
    private var lodShader:   ShaderProgram? = null
    private var wAPos = 0; private var wAUv = 0; private var wASky = 0; private var wUMvp = 0; private var wUTex = 0
    private var wUChunkOffset = 0; private var wUAmbient = 0; private var wUCaveFloor = 0
    private var wULights = 0; private var wULightCount = 0; private var wUTime = 0
    private var wUUnderwater = 0
    private var lAPos = 0; private var lAColor = 0; private var lUMvp = 0
    private var sAPos = 0; private var sABrightness = 0; private var sUMvp = 0
    private var wWAPos = 0; private var wWAUv = 0; private var wWASky = 0; private var wWUMvp = 0
    private var wWUChunkOffset = 0; private var wWUAmbient = 0; private var wWUCaveFloor = 0
    private var wWULights = 0; private var wWULightCount = 0; private var wWUTime = 0
    private var lodAPos = 0; private var lodARgb = 0; private var lodUMvp = 0
    private var lodUChunkOffset = 0; private var lodUAmbient = 0
    private var blockTexArray = 0

    private val waterMeshes         = ConcurrentHashMap<Long, ChunkMesh>()
    private val waterUploadQueue    = ConcurrentLinkedQueue<Triple<Long, Int, FloatArray>>()
    private val waterOnlyUploadQueue = ConcurrentLinkedQueue<Triple<Long, Int, FloatArray>>()

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
    // Lumière sur un seul thread de fond : le guard atomique garantit qu'un seul batch tourne à la
    // fois → aucune course sur chunk.light, et le BFS ne touche jamais le thread GL.
    private val lightDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val lightWorkerRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    private val building = ConcurrentHashMap.newKeySet<Long>()

    // Backstop d'éclairage : chunks maillés alors que la skylight n'était pas encore stabilisée.
    // Leurs faces ont pu échantillonner une lumière de voisin non-finale (repli analytique trop
    // clair au lancement). On les re-maille une fois que la cascade de lumière s'est drainée.
    private val fluxMeshedKeys = ConcurrentHashMap.newKeySet<Long>()
    private var lightPendingPrev = false

    private var lastCx = Int.MAX_VALUE; private var lastCy = Int.MAX_VALUE; private var lastCz = Int.MAX_VALUE
    private var streamNeedsMore = false
    private var streamTickAccum = 0f
    private var lastFrameNs = 0L
    private var fpsAccum = 0f; private var fpsFrames = 0   // moyenne FPS sur ~0.5 s

    private val frustum = Array(6) { FloatArray(4) }
    private var cleanupCounter = 0
    private val CLEANUP_INTERVAL = 60   // nettoyage plus fréquent (~2s)
    private val MAX_MESHES = 1200       // cap mémoire : éviction au-delà
    private val MAX_LIGHTS = 32
    // Pénombre : luminosité minimale partout (jamais 100 % noir). Une grotte sans torche reste
    // juste assez visible pour s'orienter ; une torche fait une vraie différence par-dessus.
    private val CAVE_FLOOR = 0.07f
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
    var isCreative = false
    private val physics = PhysicsNode { wx, wy, wz -> worldBlockAt(wx, wy, wz) }

    // ── Minage ────────────────────────────────────────────────────────────────

    private data class RayHit(val bx: Int, val by: Int, val bz: Int, val fnx: Int, val fny: Int, val fnz: Int)
    private var mineTarget: RayHit? = null
    private var mineDamage = 0f

    val inventory = mutableMapOf<Short, Int>()
    val hotbar    = arrayOfNulls<Short>(CaveActivity.ACTIVE_SIZE)
    var selectedSlot = 0

    private var transientVbo = 0
    private var playerBoxVbo = 0
    private var elapsed = 0f
    private var waterTickAccum   = 0f
    private var gravityTickAccum = 0f
    private var walkPhase  = 0f   // phase de balancement (rad), avance seulement quand le joueur marche
    private var walkLastX  = 0.0  // position précédente pour détecter le mouvement
    private var walkLastZ  = 0.0

    // ── Viewmodel 1re personne (bras + objet tenu) ────────────────────────────
    // Géométrie construite directement en repère vue (caméra à l'origine, regard −Z) :
    // projection dédiée vmProj, pas de matrice de vue. Buffer de profondeur vidé avant le
    // rendu → le bras ne s'enfonce jamais dans le décor.
    private var vmVbo = 0
    private val vmProj  = FloatArray(16)
    private val vmModel = FloatArray(16)
    private val vmMvp   = FloatArray(16)
    private val vmTmp   = FloatArray(16)
    private var swingActive = false
    private var swingTimer  = 0f
    private val SWING_DUR = 0.30f
    private val itemTexCache = HashMap<String, Int>()
    private val ARM_SKIN   = floatArrayOf(0.85f, 0.66f, 0.52f)   // teinte peau
    private val ARM_SLEEVE = floatArrayOf(0.30f, 0.55f, 0.85f)   // manche

    private class FallingBlock(val type: Short, val wx: Int, val wy: Int, val wz: Int) {
        var timer = 0f
        val done  get() = timer >= DURATION
        // ease-in (accélération naturelle) : commence lent, finit vite
        val visualY get() = wy.toFloat() - (timer / DURATION).let { it * it }
        companion object { const val DURATION = 0.2f }
    }
    private val fallingBlocks = mutableListOf<FallingBlock>()
    private var fallingVbo = 0


    // ── Ennemis ───────────────────────────────────────────────────────────────

    internal val eventBus           = EventBus()
    internal val playerNode         = PlayerNode()
    internal val lootNode           = LootNode(eventBus)
    internal val enemyManager      = EnemyManager(world, worldSeed)
    private val enemyRenderer      = EnemyRenderer()
    private val projRenderer       = ProjectileRenderer()

    // ── Progression joueur ────────────────────────────────────────────────────

    val skillBook   = com.Atom2Universe.app.games.caves.entity.SkillBook()
    // HP max du dernier mob tué — plafond pour les gains d'endurance
    private var lastKilledMobMaxHp = 20
    val playerStats = PlayerStats()
    val projectiles = ArrayList<Projectile>(64)
    private val impactParticles = ArrayList<ImpactParticle>(128)

    private val ROCK_IDS = setOf(2020.toShort(), 2021.toShort())
    private var rockChargeTime = 0f
    private val ROCK_CHARGE_MAX = 1.5f
    private val PLAYER_KNOCKBACK = 6.0   // vitesse initiale du recul quand le joueur est touché

    @Volatile var gamePaused = false

    // ── Outil de capture de structure (mode créatif) ──────────────────────────
    @Volatile var structCornerA: Triple<Int, Int, Int>? = null
    @Volatile var structCornerB: Triple<Int, Int, Int>? = null
    val currentLookAtBlock: Triple<Int, Int, Int>? get() = mineTarget?.let { Triple(it.bx, it.by, it.bz) }

    var posCallback:      ((String) -> Unit)?                    = null
    var fpsCallback:      ((Int) -> Unit)?                       = null
    var modeCallback:     ((PlayerMode) -> Unit)?                = null
    var miningCallback:   ((progress: Float, block: Short?) -> Unit)? = null
    var inventoryCallback: ((Map<Short, Int>) -> Unit)?           = null
    var hotbarCallback:   ((slots: Array<Short?>, selected: Int) -> Unit)? = null
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)?       = null
    var shieldCallback:   ((current: Int, max: Int) -> Unit)?    = null
    var swingCallback:    (() -> Unit)?                           = null
    var sprintCallback:       ((Boolean) -> Unit)?                = null
    var jumpChargeCallback:   ((Float) -> Unit)?                  = null
    var playerHitCallback:    (() -> Unit)?                       = null
    private var weaponAttackCooldown = 0f

    // ── Shaders ───────────────────────────────────────────────────────────────

    private val VERT_WORLD = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_uv;
        in float a_skyLight;
        uniform mat4 u_mvp;
        uniform vec3 u_chunk_offset;
        out vec2 v_uv;
        out float v_layer;
        out float v_faceDir;
        out vec3 v_worldPos;
        out float v_skyLight;
        void main() {
            vec3 worldPos = a_pos + u_chunk_offset;
            gl_Position = u_mvp * vec4(worldPos, 1.0);
            v_uv      = a_uv.xy;
            v_layer   = mod(a_uv.z, 4096.0);
            v_faceDir = floor(a_uv.z / 4096.0);
            v_worldPos = worldPos;
            v_skyLight = a_skyLight;
        }
    """.trimIndent()

    private val FRAG_WORLD = """
        #version 300 es
        precision mediump float;
        uniform sampler2DArray u_tex;
        uniform float u_ambient;
        uniform float u_caveFloor;
        uniform vec4 u_lights[32];
        uniform int u_lightCount;
        uniform float u_time;
        uniform float u_underwater;
        in vec2 v_uv;
        in float v_layer;
        in float v_faceDir;
        in vec3 v_worldPos;
        in float v_skyLight;
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
            // Lumière du ciel cuite (0..1) × ambiance jour/nuit ; plancher pénombre pour ne jamais
            // être 100 % noir ; les torches s'ajoutent par-dessus dans les zones non exposées.
            float sky = v_skyLight * u_ambient;
            vec3 lighting = max(max(vec3(sky), vec3(u_caveFloor)), torchContrib);
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
        uniform float u_caveFloor;
        uniform vec4 u_lights[32];
        uniform int u_lightCount;
        uniform float u_time;
        in vec2 v_uv;
        in float v_layer;
        in float v_faceDir;
        in vec3 v_worldPos;
        in float v_skyLight;
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
            float sky = v_skyLight * u_ambient;
            vec3 lighting = max(max(vec3(sky), vec3(u_caveFloor)), torchContrib);
            fragColor = vec4(baseColor * faceLight * lighting, 0.75);
        }
    """.trimIndent()

    // LOD lointain : couleur du bloc (BlockDef.color) au lieu de la texture array.
    // La lumière de face est déjà multipliée dans a_rgb au build ; le shader n'applique
    // que l'ambiance jour/nuit. Pas d'échantillonnage de texture → moins cher et plus net de loin.
    private val VERT_LOD = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_rgb;
        uniform mat4 u_mvp;
        uniform vec3 u_chunk_offset;
        out vec3 v_rgb;
        void main() {
            gl_Position = u_mvp * vec4(a_pos + u_chunk_offset, 1.0);
            v_rgb = a_rgb;
        }
    """.trimIndent()

    private val FRAG_LOD = """
        #version 300 es
        precision mediump float;
        uniform float u_ambient;
        in vec3 v_rgb;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(v_rgb * u_ambient, 1.0);
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
            wASky         = it.attrib("a_skyLight")
            wUMvp         = it.uniform("u_mvp")
            wUTex         = it.uniform("u_tex")
            wUChunkOffset = it.uniform("u_chunk_offset")
            wUAmbient     = it.uniform("u_ambient")
            wUCaveFloor   = it.uniform("u_caveFloor")
            wULights      = it.uniform("u_lights[0]")
            wULightCount  = it.uniform("u_lightCount")
            wUTime        = it.uniform("u_time")
            wUUnderwater  = it.uniform("u_underwater")
        }
        waterShader = ShaderProgram(VERT_WORLD, FRAG_WATER).also {
            it.use()
            wWAPos         = it.attrib("a_pos")
            wWAUv          = it.attrib("a_uv")
            wWASky         = it.attrib("a_skyLight")
            wWUMvp         = it.uniform("u_mvp")
            wWUChunkOffset = it.uniform("u_chunk_offset")
            wWUAmbient     = it.uniform("u_ambient")
            wWUCaveFloor   = it.uniform("u_caveFloor")
            wWULights      = it.uniform("u_lights[0]")
            wWULightCount  = it.uniform("u_lightCount")
            wWUTime        = it.uniform("u_time")
        }
        lodShader = ShaderProgram(VERT_LOD, FRAG_LOD).also {
            it.use()
            lodAPos         = it.attrib("a_pos")
            lodARgb         = it.attrib("a_rgb")
            lodUMvp         = it.uniform("u_mvp")
            lodUChunkOffset = it.uniform("u_chunk_offset")
            lodUAmbient     = it.uniform("u_ambient")
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

        MobRegistry.load(context.assets)
        ItemRegistry.load(context.assets)
        LootTableRegistry.load(context.assets)
        blockTexArray = loadBlockTextures()
        enemyRenderer.onSurfaceCreated(context.assets)
        projRenderer.onSurfaceCreated(context.assets)

        playerNode.onHpChanged     = { hp, max -> playerHpCallback?.invoke(hp, max) }
        playerNode.onShieldChanged = { cur, max -> shieldCallback?.invoke(cur, max) }
        playerNode.onEnduranceXp   = { xp ->
            skillBook.enduranceXp += xp
            val formula = skillBook.computedMaxHp
            val newMax  = formula.coerceAtMost(lastKilledMobMaxHp)
            if (newMax > playerNode.maxHp) {
                playerNode.setMaxHp(newMax)
                playerStats.maxHp = newMax
            }
        }

        physics.skillBook = skillBook
        physics.onJumped = { skillBook.athleticsXp += 1 }
        physics.onFallLanded = { fallBlocks ->
            val sb = skillBook
            val threshold = sb.fallSafeBlocks
            if (fallBlocks > threshold) {
                val damage = ((fallBlocks - threshold) * 2.5).toInt().coerceAtLeast(1)
                playerNode.applyDamage(damage)
                val xpGain = ((fallBlocks - threshold) * 10).toInt().coerceAtLeast(1)
                sb.acrobaticsXp += xpGain
            } else {
                // Chute sans dégâts : petit XP acrobatics quand même
                sb.acrobaticsXp += (fallBlocks * 2).toInt().coerceAtLeast(1)
            }
        }

        enemyManager.player        = playerNode
        enemyManager.eventBus      = eventBus
        enemyManager.thornsProvider = { equippedWeaponStat("thorns") }
        eventBus.subscribe { event ->
            if (event is GameEvent.PlayerHit) {
                // Recul du joueur (dans le sens attaquant → joueur) + flash rouge écran.
                physics.applyKnockback(event.dirX * PLAYER_KNOCKBACK, event.dirZ * PLAYER_KNOCKBACK)
                playerHitCallback?.invoke()
                return@subscribe
            }
            if (event !is GameEvent.MobDied) return@subscribe
            val xpGain = if (event.isBoss) 5 * event.level else event.level
            playerStats.addXp(xpGain)
            // Le dernier mob tué fixe le plafond HP pour les gains d'endurance
            lastKilledMobMaxHp = event.mobMaxHp.coerceAtLeast(20)
            if (event.isBoss) {
                inventory[WARD_STONE] = (inventory[WARD_STONE] ?: 0) + 1
                inventoryCallback?.invoke(inventory.toMap())
            }
        }

        lootNode.onItemsDropped = { items ->
            for (item in items) {
                if (com.Atom2Universe.app.games.caves.node.ItemRegistry.get(item.defId)?.type == "weapon") {
                    val id = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.allocate(item)
                    inventory[id] = 1
                    val freeHotbarSlot = hotbar.indexOfFirst { it == null }
                    if (freeHotbarSlot >= 0) hotbar[freeHotbarSlot] = id
                }
            }
            inventoryCallback?.invoke(inventory.toMap())
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
        }

        val ids = IntArray(3)
        GLES30.glGenBuffers(3, ids, 0)
        transientVbo = ids[0]
        playerBoxVbo = ids[1]
        vmVbo        = ids[2]

        if (savedState != null) {
            camera.playerX = savedState.x; camera.playerY = savedState.y; camera.playerZ = savedState.z
            camera.x = savedState.x; camera.y = savedState.y; camera.z = savedState.z
            camera.yaw = savedState.yaw; camera.pitch = savedState.pitch
            inventory.putAll(savedState.inventory)
            savedState.hotbar.take(hotbar.size).forEachIndexed { i, v -> hotbar[i] = v }
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            inventoryCallback?.invoke(inventory.toMap())
            // Restauration progression joueur
            playerStats.level    = savedState.playerLevel
            playerStats.xp       = savedState.playerXp
            playerStats.xpToNext = playerStats.xpRequired(savedState.playerLevel)
            playerStats.maxHp    = savedState.playerMaxHp
            playerStats.shield   = savedState.playerShield
            playerNode.maxHp    = savedState.playerMaxHp
            playerNode.hp       = savedState.playerHp.coerceAtMost(savedState.playerMaxHp)
            playerNode.maxShield = savedState.playerShield
            playerNode.shield   = savedState.playerShieldCurrent.coerceAtMost(savedState.playerShield)
            savedState.wardStonePositions.forEach { (x, z) -> enemyManager.wardStoneZones.add(Pair(x, z)) }
            skillBook.athleticsXp  = savedState.skillAthleticsXp
            skillBook.speedXp      = savedState.skillSpeedXp
            skillBook.enduranceXp  = savedState.skillEnduranceXp
            skillBook.acrobaticsXp = savedState.skillAcrobaticsXp
            val pcx = camera.chunkX(); val pcy = camera.chunkY(); val pcz = camera.chunkZ()
            for (dy in -1..1) for (dz in -1..1) for (dx in -1..1)
                world.pregenerateChunk(pcx + dx, pcy + dy, pcz + dz)
            val spawn = world.findSpawnPoint()
            enemyManager.worldSpawnX      = spawn[0].toDouble()
            enemyManager.worldSpawnY      = spawn[1].toDouble()
            enemyManager.worldSpawnZ      = spawn[2].toDouble()
        } else {
            // Nouvelle partie : démarrer à 10h du matin IG (portion jour, 100 000 ms/heure → 4h après 6h).
            gameTimeMs = NEW_GAME_START_MS
            val spawn = world.findSpawnPoint()
            camera.playerX = spawn[0].toDouble(); camera.playerY = spawn[1].toDouble(); camera.playerZ = spawn[2].toDouble()
            camera.x = camera.playerX; camera.y = camera.playerY; camera.z = camera.playerZ
            val pcx = camera.chunkX(); val pcy = camera.chunkY(); val pcz = camera.chunkZ()
            for (dy in -1..1) for (dz in -1..1) for (dx in -1..1)
                world.pregenerateChunk(pcx + dx, pcy + dy, pcz + dz)
            enemyManager.worldSpawnX = camera.x
            enemyManager.worldSpawnY = camera.y
            enemyManager.worldSpawnZ = camera.z
            // Donner le laser de minage au joueur dès le départ
            val laserInstance = com.Atom2Universe.app.games.caves.node.ItemRegistry.rollInstance(
                "mining_laser", kotlin.random.Random.Default
            )
            if (laserInstance != null) {
                val laserShortId = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.allocate(laserInstance)
                inventory[laserShortId] = 1
                hotbar[0] = laserShortId
            }
        }
        scheduleInitialLodBuilds()
    }

    private fun scheduleInitialLodBuilds() {
        val cache = lodCache ?: return
        val pcx = camera.chunkX(); val pcz = camera.chunkZ()
        val r = LOD_RADIUS
        scope.launch {
            cache.cachedColumns()
                .mapNotNull { (cx, cz) ->
                    val dx = cx - pcx; val dz = cz - pcz
                    val d2 = dx * dx + dz * dz
                    if (d2 <= r * r) Triple(d2, cx, cz) else null
                }
                .sortedBy { it.first }
                .take(INITIAL_LOD_BUILD_LIMIT)
                .forEach { (_, cx, cz) -> scheduleLodBuild(cx, cz) }
        }
    }

    private fun createWardStoneBitmap(size: Int): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val s = size.toFloat()
        // Base : pierre sombre bleutée
        paint.color = android.graphics.Color.rgb(35, 30, 70)
        canvas.drawRect(0f, 0f, s, s, paint)
        // Veines violettes
        paint.color = android.graphics.Color.rgb(90, 50, 160)
        paint.strokeWidth = s * 0.06f; paint.style = android.graphics.Paint.Style.STROKE
        canvas.drawLine(s * 0.1f, s * 0.3f, s * 0.5f, s * 0.7f, paint)
        canvas.drawLine(s * 0.6f, s * 0.1f, s * 0.9f, s * 0.6f, paint)
        canvas.drawLine(s * 0.2f, s * 0.8f, s * 0.7f, s * 0.4f, paint)
        // Cristal central lumineux
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.rgb(150, 90, 255)
        val cx = s * 0.5f; val cy = s * 0.45f
        val path = android.graphics.Path().apply {
            moveTo(cx, cy - s * 0.22f)
            lineTo(cx + s * 0.14f, cy)
            lineTo(cx, cy + s * 0.22f)
            lineTo(cx - s * 0.14f, cy)
            close()
        }
        canvas.drawPath(path, paint)
        paint.color = android.graphics.Color.rgb(210, 170, 255)
        val pathInner = android.graphics.Path().apply {
            moveTo(cx, cy - s * 0.12f)
            lineTo(cx + s * 0.07f, cy)
            lineTo(cx, cy + s * 0.12f)
            lineTo(cx - s * 0.07f, cy)
            close()
        }
        canvas.drawPath(pathInner, paint)
        return bmp
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
        BlockRegistry.registerGeneratedTexture("Items/torch.png") { size -> createTorchBitmap(size) }
        BlockRegistry.registerGeneratedTexture("ward_stone.png") { size -> createWardStoneBitmap(size) }

        val bitmaps = BlockRegistry.buildTextureAtlas(context.assets, 32)
        if (bitmaps.isEmpty()) return 0
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
        val hotbarPx = (60f * context.resources.displayMetrics.density).toInt()
        val gameH = (height - hotbarPx).coerceAtLeast(1)
        GLES30.glViewport(0, hotbarPx, width, gameH)
        camera.setProjection(70f, width.toFloat() / gameH)
        // Projection dédiée au viewmodel (FOV légèrement plus serré, near rapproché)
        android.opengl.Matrix.perspectiveM(vmProj, 0, 62f, width.toFloat() / gameH, 0.04f, 12f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val rawDt = (now - lastFrameNs) / 1_000_000_000f          // intervalle réel (FPS affiché)
        val dt = rawDt.coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        // FPS moyenné sur ~0.5 s (utilise l'intervalle réel, sleep inclus → vrai FPS écran)
        fpsAccum += rawDt; fpsFrames++
        if (fpsAccum >= 0.5f) {
            fpsCallback?.invoke((fpsFrames / fpsAccum).roundToInt())
            fpsAccum = 0f; fpsFrames = 0
        }

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
        adjustTpsCamera()

        elapsed += dt
        if (!gamePaused) gameTimeMs += (dt * 1_000f).toLong()

        waterTickAccum += dt
        if (waterTickAccum >= 0.25f) {
            waterTickAccum = 0f
            world.tickWater(playerX = camera.x.toInt(), playerZ = camera.z.toInt())
            world.tickDrain()
        }

        gravityTickAccum += dt
        if (gravityTickAccum >= 0.1f) {
            gravityTickAccum = 0f
            world.tickFalling(64)
        }

        if (!gamePaused) {
            if (weaponAttackCooldown > 0f) weaponAttackCooldown = (weaponAttackCooldown - dt).coerceAtLeast(0f)
            updateRockThrow(dt)
            updateMining(dt)
            updateProjectiles(dt)
            updateImpactParticles(dt)
        }

        val cx = camera.chunkX(); val cy = camera.chunkY(); val cz = camera.chunkZ()
        val movedChunk = cx != lastCx || cy != lastCy || cz != lastCz
        streamTickAccum += dt
        if (movedChunk || (streamNeedsMore && streamTickAccum >= 0.08f)) {
            val batchSize = if (movedChunk) 64 else 24
            lastCx = cx; lastCy = cy; lastCz = cz
            streamTickAccum = 0f
            streamNeedsMore = world.updateAroundPlayer(
                cx, cy, cz,
                camera.fwdX, camera.fwdZ,
                maxNewChunks = batchSize
            ) { chunk -> scheduleChunkBuild(chunk) }
        }

        // Propagation de lumière sur un thread de fond dédié (jamais sur le thread GL → pas de lag),
        // et mono-thread via un guard atomique → pas de course, la skylight converge proprement.
        // Les chunks dont la lumière s'est stabilisée sont mis en file de reconstruction du mesh.
        if (world.hasPendingLight() && lightWorkerRunning.compareAndSet(false, true)) {
            scope.launch(lightDispatcher) {
                try { processLightBatch() } finally { lightWorkerRunning.set(false) }
            }
        }

        // Backstop d'éclairage : quand la cascade de skylight vient de se drainer entièrement
        // (transition en-cours → drainé), la lumière de tous les chunks chargés est désormais
        // finale. On re-maille les chunks qui avaient été maillés pendant le flux : ils s'étaient
        // peut-être figés trop clairs (repli analytique d'un voisin pas encore chargé). Le read
        // volatile de lightWorkerRunning établit le happens-before avec les écritures de chunk.light.
        val lightPendingNow = world.hasPendingLight() || lightWorkerRunning.get()
        if (lightPendingPrev && !lightPendingNow && fluxMeshedKeys.isNotEmpty()) {
            val it = fluxMeshedKeys.iterator()
            while (it.hasNext()) {
                val key = it.next(); it.remove()
                val chunk = world.getChunkByKey(key)?.takeIf { c -> c.generated } ?: continue
                chunk.meshDirty = true
                world.rebuildQueue.add(key)
            }
        }
        lightPendingPrev = lightPendingNow

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
            chunk.waterMeshDirty = false
            val snapVersion  = chunk.version
            val snapWaterVer = chunk.waterVersion
            scope.launch(meshDispatcher) {
                try {
                    val verts      = MeshBuilder.build(chunk, world)
                    val waterVerts = MeshBuilder.buildWater(chunk, world)
                    if (chunk.version == snapVersion) {
                        uploadQueue.add(Triple(key, snapVersion, verts))
                        waterUploadQueue.add(Triple(key, snapVersion, waterVerts))
                    }
                    // Maillé pendant que la skylight converge encore → la lumière des voisins
                    // (et le repli analytique des voisins non chargés) peut être non-finale.
                    // On le re-maillera une fois la cascade drainée.
                    if (world.hasPendingLight() || lightWorkerRunning.get()) fluxMeshedKeys.add(key)
                    if (chunk.meshDirty || chunk.version != snapVersion) world.rebuildQueue.add(key)
                    if (chunk.waterMeshDirty || chunk.waterVersion != snapWaterVer) world.waterRebuildQueue.add(key)
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

        // Rebuilds eau seuls : ne reconstruit que buildWater(), évite build() solide inutile
        val pendingWaterSet = HashSet<Long>()
        while (true) { pendingWaterSet.add(world.waterRebuildQueue.poll() ?: break) }
        pendingWaterSet -= pendingSet  // les chunks déjà en rebuild solide reconstruisent l'eau aussi
        val waterBatch = pendingWaterSet.sortedBy { key ->
            val dx = world.keyToCx(key) - cx; val dy = world.keyToCy(key) - cy; val dz = world.keyToCz(key) - cz
            dx * dx + dz * dz + dy * dy * 8
        }
        var waterRebuilt = 0
        for (key in waterBatch) {
            if (waterRebuilt >= 8) { world.waterRebuildQueue.add(key); continue }
            val chunk = world.getChunkByKey(key) ?: continue
            if (!chunk.generated) { world.waterRebuildQueue.add(key); continue }
            if (!building.add(key)) { world.waterRebuildQueue.add(key); continue }
            chunk.waterMeshDirty = false
            val snapWaterVer = chunk.waterVersion
            scope.launch(meshDispatcher) {
                try {
                    val waterVerts = MeshBuilder.buildWater(chunk, world)
                    if (chunk.waterVersion == snapWaterVer) {
                        waterOnlyUploadQueue.add(Triple(key, snapWaterVer, waterVerts))
                    }
                    if (chunk.waterMeshDirty || chunk.waterVersion != snapWaterVer) world.waterRebuildQueue.add(key)
                } finally {
                    building.remove(key)
                }
            }
            waterRebuilt++
        }

        repeat(16) {
            val (key, ver, verts) = uploadQueue.poll() ?: return@repeat
            val chunk = world.getChunkByKey(key) ?: return@repeat
            if (chunk.version == ver) {
                meshes.getOrPut(key) { ChunkMesh(7) }.upload(verts)
                refreshChunkLightSources(chunk)
                if (chunk.cy in 0..SURFACE_CY_MAX) scheduleLodBuild(chunk.cx, chunk.cz)
            }
        }
        repeat(16) {
            val (key, verts) = lodUploadQueue.poll() ?: return@repeat
            lodUploadQueueSize.decrementAndGet()
            var isNew = false
            lodMeshes.getOrPut(key) { isNew = true; ChunkMesh(6) }.upload(verts)
            if (isNew) lodGrid.getOrPut(superKey(lodKeyToCx(key), lodKeyToCz(key))) { ArrayList() }.add(key)
        }
        repeat(16) {
            val (key, ver, verts) = waterUploadQueue.poll() ?: return@repeat
            val chunk = world.getChunkByKey(key) ?: return@repeat
            if (chunk.version == ver) {
                if (verts.isNotEmpty()) waterMeshes.getOrPut(key) { ChunkMesh(7) }.upload(verts)
                else waterMeshes.remove(key)?.destroy()
            }
        }
        repeat(8) {
            val (key, ver, verts) = waterOnlyUploadQueue.poll() ?: return@repeat
            val chunk = world.getChunkByKey(key) ?: return@repeat
            if (chunk.waterVersion == ver) {
                if (verts.isNotEmpty()) waterMeshes.getOrPut(key) { ChunkMesh(7) }.upload(verts)
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
                while (heap.isNotEmpty()) meshes.remove(heap.poll()!!)?.destroy()
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
                    val key = lodHeap.poll()!!
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
        GLES30.glUniform1f(wUCaveFloor, CAVE_FLOOR)
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
            mesh.draw(wAPos, wAUv, wASky)
        }

        // ── Rendu LOD (couleur des blocs, visible de loin) ────────────────────
        // Shader dédié : couleur plate (BlockDef.color) × ambiance, pas de texture.
        // Masquage : une colonne ayant un mesh réel (cy quelconque) n'affiche pas son LOD,
        // évitant le double-rendu près du joueur. Le LOD reste visible pendant le gap
        // génération→upload (pas de trou).
        columnsWithMesh.clear()
        meshes.keys.forEach { k -> columnsWithMesh.add(lodKey(world.keyToCx(k), world.keyToCz(k))) }
        lodShader?.use()
        GLES30.glUniformMatrix4fv(lodUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform1f(lodUAmbient, ambientFor(dayT))
        // Itère par super-tuiles (8×8 colonnes) : ~125 tests frustum au lieu de 8000.
        for ((sk, keys) in lodGrid) {
            val scx = superKeyToCx(sk) * LOD_SUPER
            val scz = superKeyToCz(sk) * LOD_SUPER
            if (!isLodSuperTileInFrustum(scx, scz)) continue
            for (key in keys) {
                if (columnsWithMesh.contains(key)) continue
                val mesh = lodMeshes[key] ?: continue
                val lcx = lodKeyToCx(key); val lcz = lodKeyToCz(key)
                GLES30.glUniform3f(lodUChunkOffset,
                    (lcx.toDouble() * CHUNK_SIZE - camera.x).toFloat(),
                    -camera.y.toFloat(),
                    (lcz.toDouble() * CHUNK_SIZE - camera.z).toFloat())
                mesh.draw(lodAPos, lodARgb)
            }
        }

        // ── Mise à jour + rendu ennemis ───────────────────────────────────────
        if (!gamePaused) enemyManager.update(dt, camera.playerX, camera.playerY, camera.playerZ)
        enemyRenderer.render(
            enemyManager.enemies,
            camera.x, camera.y, camera.z,
            camera.yaw, camera.vpMatrix
        )

        // ── Rendu projectiles + particules d'impact ───────────────────────────
        projRenderer.render(projectiles, camera.x, camera.y, camera.z, camera.yaw, camera.vpMatrix)
        projRenderer.renderParticles(impactParticles, camera.x, camera.y, camera.z, camera.yaw, camera.vpMatrix)

        // ── Boîte joueur (TPS) ────────────────────────────────────────────────
        drawPlayerBox(dt)

        // ── Rendu laser + highlight ───────────────────────────────────────────
        renderLaserAndHighlight()

        // ── Boîte de sélection structure (mode créatif) ───────────────────────
        drawStructureSelection()

        // ── Blocs en chute (rendu avec world shader encore actif) ────────────
        drawFallingBlocks(dt)

        // ── Passe eau (blending semi-transparent, après géométrie opaque) ─────
        val waterAmbient = if (headUnderwater) ambientFor(dayT) * 0.4f else ambientFor(dayT)
        waterShader?.use()
        GLES30.glUniformMatrix4fv(wWUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform1f(wWUAmbient, waterAmbient)
        GLES30.glUniform1f(wWUCaveFloor, CAVE_FLOOR)
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
            mesh.draw(wWAPos, wWAUv, wWASky)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        // ── Viewmodel 1re personne (bras + objet tenu) ────────────────────────
        drawViewmodel(dt)

        posCallback?.invoke(camera.posString())

        val elapsed2 = System.nanoTime() - now
        val sleepNs = TARGET_FRAME_NS - elapsed2
        if (sleepNs > 1_000_000L) Thread.sleep(sleepNs / 1_000_000L)
    }

private fun updateProjectiles(dt: Float) {
        val ROCK_GRAVITY = 12.0
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (p.isRock) {
                p.velY -= ROCK_GRAVITY * dt
                p.x += p.dirX * p.speed * dt
                p.y += p.velY * dt
                p.z += p.dirZ * p.speed * dt
                p.travelDist += p.speed.toDouble() * dt
            } else {
                val step = (p.speed * dt).toDouble()
                p.x += p.dirX * step; p.y += p.dirY * step; p.z += p.dirZ * step
                p.travelDist += step
            }
            if (p.travelDist > PROJ_MAX_DIST) { iter.remove(); continue }

            if (p.isRock) {
                val block = worldBlockAt(
                    Math.floor(p.x).toInt(),
                    Math.floor(p.y).toInt(),
                    Math.floor(p.z).toInt()
                )
                if (block != AIR && !BlockRegistry.isDecoration(block) && !BlockRegistry.isWater(block)) {
                    spawnImpact(p.x, p.y, p.z)
                    iter.remove(); continue
                }
            }

            val hit = enemyManager.enemies.find { e ->
                if (e.hp <= 0) return@find false
                // Cylindre de collision aligné sur le modèle voxel visible :
                // rayon XZ généreux, hauteur des pieds (e.y) jusqu'au sommet réel.
                val rXZ = e.def.radius.toDouble() + 0.5
                val dx = p.x - e.x; val dz = p.z - e.z
                if (dx * dx + dz * dz >= rXZ * rXZ) return@find false
                val top = MobModels.bodyHeightWorld(e.def.model, e.baseScale).toDouble()
                p.y >= e.y - 0.25 && p.y <= e.y + top + 0.25
            }
            if (hit != null) {
                if (p.isRock) spawnImpact(p.x, p.y, p.z)
                enemyManager.damageEnemy(hit, p.damage)
                iter.remove()
            }
        }
    }

    private fun isRockSelected(): Boolean {
        val id = hotbar[selectedSlot] ?: return false
        return id in ROCK_IDS
    }

    private fun isAimingAtRockBlock(): Boolean {
        val hit = raycastBlock() ?: return false
        return worldBlockAt(hit.bx, hit.by, hit.bz) in ROCK_IDS
    }

    private fun isMiningLaserSelected(): Boolean {
        val id = hotbar[selectedSlot] ?: return false
        if (!com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(id)) return false
        val weapon = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(id) ?: return false
        val def = com.Atom2Universe.app.games.caves.node.ItemRegistry.get(weapon.defId) ?: return false
        return def.weaponType == "mining_laser"
    }

    private fun updateRockThrow(dt: Float) {
        if (!isRockSelected()) {
            rockChargeTime = 0f
            // Attaque mêlée uniquement si l'arme équipée n'est pas le laser de minage
            if (touch.rtChargeRaw > 0.3f && !isMiningLaserSelected()) tryWeaponMeleeAttack()
            return
        }
        val rt = touch.rtChargeRaw
        if (rt > 0.3f) {
            if (isAimingAtRockBlock()) { rockChargeTime = 0f; return }
            rockChargeTime = (rockChargeTime + dt).coerceAtMost(ROCK_CHARGE_MAX)
        } else if (rockChargeTime > 0.3f) {
            val charge = rockChargeTime / ROCK_CHARGE_MAX
            val speed = 10f + (28f - 10f) * charge
            val damage = (2 + ((5 - 2) * charge)).toInt()
            // Départ depuis la main droite : décalé à droite + un peu en avant,
            // sous l'œil (l'œil caméra est à playerY) → la pierre part du bas-droite
            // de l'écran et non du haut.
            val yawRad = Math.toRadians(camera.yaw.toDouble())
            val rightX = cos(yawRad); val rightZ = -sin(yawRad)
            val fwdX = sin(yawRad);   val fwdZ = cos(yawRad)
            val spawnX = camera.playerX + rightX * 0.45 + fwdX * 0.35
            val spawnZ = camera.playerZ + rightZ * 0.45 + fwdZ * 0.35
            val spawnY = camera.playerY - 0.25
            val rockWeapon = WeaponDef(WeaponColor.BLUE, WeaponVariant.SWIRL)
            projectiles.add(Projectile(
                spawnX, spawnY, spawnZ,
                camera.aimX.toDouble(), camera.aimY.toDouble(), camera.aimZ.toDouble(),
                speed, damage, rockWeapon, isRock = true
            ))
            val blockId = hotbar[selectedSlot]!!
            val count = (inventory[blockId] ?: 0) - 1
            if (count <= 0) {
                inventory.remove(blockId)
                hotbar[selectedSlot] = null
            } else {
                inventory[blockId] = count
            }
            inventoryCallback?.invoke(inventory.toMap())
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            rockChargeTime = 0f
        } else {
            rockChargeTime = 0f
        }
    }

    // Attaque mêlée avec l'arme équipée (déclenché quand pas de caillou sélectionné)
    internal fun tryWeaponMeleeAttack(): Boolean {
        val heldId = hotbar[selectedSlot] ?: return false
        if (!com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(heldId)) return false
        val weapon = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(heldId) ?: return false
        if (weaponAttackCooldown > 0f) return false
        val def = com.Atom2Universe.app.games.caves.node.ItemRegistry.get(weapon.defId) ?: return false
        val stats = weapon.rolledStats
        val baseDamage = weapon.rolledDamage ?: 1
        val range = 2.5
        val rng = kotlin.random.Random.Default
        var hit = false

        for (enemy in enemyManager.enemies) {
            if (enemy.hp <= 0) continue
            val dx = abs(enemy.x - camera.playerX)
            val dy = abs(enemy.y - camera.playerY)
            val dz = abs(enemy.z - camera.playerZ)
            val inRange = dx <= range + 0.5 && dy <= range + 0.5 && dz <= range + 0.5
            if (!inRange) continue

            applyWeaponHit(enemy, baseDamage, stats, rng)
            spawnImpact(enemy.x, enemy.y + 1.0, enemy.z)
            hit = true

            // Éclaboussure AoE sur les ennemis adjacents
            val aoeSplash = stats["aoe_splash"] ?: 0
            if (aoeSplash > 0 && rng.nextInt(100) < aoeSplash) {
                for (adj in enemyManager.enemies) {
                    if (adj === enemy || adj.hp <= 0) continue
                    val ax = abs(adj.x - enemy.x); val az = abs(adj.z - enemy.z)
                    if (ax < 3.0 && az < 3.0) {
                        applyWeaponHit(adj, (baseDamage * 0.5f).toInt().coerceAtLeast(1), stats.filterKeys { it == "bleed_chance" || it == "poison_chance" }, rng)
                    }
                }
            }
        }

        val speedBonus = stats["attack_speed"] ?: 0
        val cooldownMs = def.attackSpeedMs.coerceAtLeast(300) * (1f - speedBonus / 100f)
        weaponAttackCooldown = (cooldownMs / 1000f).coerceAtLeast(0.2f)
        swingCallback?.invoke()
        startSwing()
        return hit
    }

    private fun applyWeaponHit(
        enemy: com.Atom2Universe.app.games.caves.entity.Enemy,
        baseDamage: Int,
        stats: Map<String, Int>,
        rng: kotlin.random.Random
    ) {
        var dmg = baseDamage

        // Coup critique — multiplie le coup ET les DoTs
        val critChance = stats["crit_chance"] ?: 0
        val isCrit = critChance > 0 && rng.nextInt(100) < critChance
        val critMult = if (isCrit) 2.0f + (stats["crit_dmg"] ?: 0) / 100f else 1.0f
        if (isCrit) dmg = (dmg * critMult).toInt()

        com.Atom2Universe.app.games.caves.node.CombatNode.damageEnemy(enemy, dmg)
        enemyManager.knockbackFromPlayer(enemy)

        // Vol de vie (sur les dégâts du coup, post-crit)
        val lifeSteal = stats["life_steal"] ?: 0
        if (lifeSteal > 0) {
            val heal = (dmg * lifeSteal / 100f).toInt().coerceAtLeast(1)
            enemyManager.player?.applyHeal(heal)
        }

        // Exécution (ennemi < 20% HP)
        val execute = stats["execute"] ?: 0
        if (execute > 0 && enemy.hp > 0) {
            val threshold = (enemy.maxHp * 0.20f).toInt()
            if (enemy.hp <= threshold && rng.nextInt(100) < execute) {
                enemy.hp = 0
                return
            }
        }

        // Saignement : 15% des dégâts de base (scalé par crit), tick 0.5s, durée 3s
        val bleedChance = stats["bleed_chance"] ?: 0
        if (bleedChance > 0 && rng.nextInt(100) < bleedChance) {
            enemy.bleedDamage = (baseDamage * 0.15f * critMult).toInt().coerceAtLeast(1)
            enemy.bleedTimer = 3f
            enemy.bleedTickTimer = 0.5f
        }

        // Poison : 10% des dégâts de base (scalé par crit), tick 0.8s, durée 4s
        val poisonChance = stats["poison_chance"] ?: 0
        if (poisonChance > 0 && rng.nextInt(100) < poisonChance) {
            enemy.poisonDamage = (baseDamage * 0.10f * critMult).toInt().coerceAtLeast(1)
            enemy.poisonTimer = 4f
            enemy.poisonTickTimer = 0.8f
        }

        // Feu : 20% des dégâts de base (scalé par crit), tick rapide 0.3s, durée 2s
        val fireChance = stats["fire_chance"] ?: 0
        if (fireChance > 0 && rng.nextInt(100) < fireChance) {
            enemy.fireDamage = (baseDamage * 0.20f * critMult).toInt().coerceAtLeast(1)
            enemy.fireTimer = 2f
            enemy.fireTickTimer = 0.3f
        }

        // Étourdissement
        val shockChance = stats["shock_chance"] ?: 0
        if (shockChance > 0 && rng.nextInt(100) < shockChance) {
            enemy.shockTimer = 1.5f
        }
    }

    internal fun equippedWeaponStat(key: String): Int {
        val id = hotbar[selectedSlot] ?: return 0
        if (!com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(id)) return 0
        return com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(id)?.rolledStats?.get(key) ?: 0
    }

    private fun spawnImpact(x: Double, y: Double, z: Double) {
        val rng = Random.Default
        repeat(8) {
            val angle = rng.nextDouble() * 2.0 * PI
            val spd = rng.nextFloat() * 2.5f + 0.8f
            impactParticles.add(ImpactParticle(
                x.toFloat(), y.toFloat(), z.toFloat(),
                cos(angle).toFloat() * spd,
                rng.nextFloat() * 2f + 0.5f,
                sin(angle).toFloat() * spd,
                0.45f, 0.45f
            ))
        }
    }

    private fun updateImpactParticles(dt: Float) {
        val iter = impactParticles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.lifeRem -= dt
            if (p.lifeRem <= 0f) { iter.remove(); continue }
            val age = 1f - p.lifeRem / p.lifeMax
            p.x += p.vx * dt
            p.y += (p.vy - 8f * age) * dt
            p.z += p.vz * dt
        }
    }


    // ── Minage ────────────────────────────────────────────────────────────────

    private val MINE_REACH = 6

    private fun updateMining(dt: Float) {
        if (touch.placeRequested) {
            touch.placeRequested = false
            placeBlock()
        }

        val laserAllowed = touch.laserActive && isMiningLaserSelected() && (!isRockSelected() || isAimingAtRockBlock())
        // Les blocs rock/rock_moss sont minables sans laser, en visant simplement dessus
        val rockMineActive = touch.laserActive && !isMiningLaserSelected() && isAimingAtRockBlock()
        // Le laser de minage n'affecte que les blocs : aucun dégât aux mobs.
        val target = if (laserAllowed || rockMineActive) raycastBlock() else null

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
        val hardness = BlockRegistry.getHardness(blockType)
        mineDamage += dt / hardness

        miningCallback?.invoke(mineDamage, blockType)

        if (mineDamage >= 1f) {
            world.setBlock(bx, by, bz, AIR)
            forceMeshRebuild(bx, by, bz)
            world.enqueueIfFalling(bx, by + 1, bz)
            when (blockType) {
                WATER      -> { world.onWaterSourceRemoved(bx, by, bz)
                                if (!isCreative) collectBlock(blockType) }
                WATER_FLOW -> { /* eau qui coule : pas de drop, pas de re-simulation */ }
                WARD_STONE -> { enemyManager.wardStoneZones.removeAll { (wx, wz) ->
                                    wx.toInt() == bx && wz.toInt() == bz }
                                if (!isCreative) collectBlock(blockType) }
                else       -> { if (!isCreative) collectBlock(blockType) }
            }
            if (blockType in ROCK_IDS) rockChargeTime = 0f
            mineTarget = null
            mineDamage = 0f
            miningCallback?.invoke(0f, null)
        }
    }

    private fun collectBlock(blockType: Short) {
        // Tous les cailloux (normal, moussu/poussiéreux…) donnent un seul et même
        // caillou → un seul stack dans l'inventaire.
        val dropType = if (blockType in ROCK_IDS) ROCK else blockType
        inventory[dropType] = (inventory[dropType] ?: 0) + 1
        if (hotbar.none { it == dropType }) {
            val emptySlot = hotbar.indexOfFirst { it == null }
            if (emptySlot != -1) {
                hotbar[emptySlot] = dropType
                hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            }
        }
        inventoryCallback?.invoke(inventory.toMap())
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

    // Offsets voisins par bit de face du masque renvoyé par LightEngine.computeSky.
    private val LIGHT_FACE_OFFSETS = arrayOf(
        intArrayOf(0, 1, 0), intArrayOf(0, -1, 0),   // +Y, −Y
        intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),   // +X, −X
        intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)    // +Z, −Z
    )
    private val LIGHT_BATCH = 192   // chunks traités par lancement de fond (BFS, sans mesh → bon marché)

    /**
     * Propagation de la skylight, **découplée du meshing** et exécutée sur un thread de fond dédié
     * mono-thread (jamais le thread GL) → pas de lag de rendu, pas de course de lecture/écriture de
     * `chunk.light`, convergence garantie. computeSky (BFS) est bon marché ; on ne reconstruit le
     * mesh d'un chunk qu'**une fois sa lumière stabilisée** (computeSky renvoie 0). La propagation
     * aux voisins se fait via la file de lumière, sans toucher au mesh tant que ça bouge encore.
     */
    private fun processLightBatch() {
        var processed = 0
        while (processed < LIGHT_BATCH) {
            val key = world.pollLightKey() ?: break
            processed++
            val chunk = world.getChunkByKey(key)?.takeIf { it.generated } ?: continue
            val r = LightEngine.computeSky(chunk, world)
            if (r != 0) {
                chunk.lightMeshDirty = true            // lumière changée → mesh périmé, on attend le calme
                chunk.lightBoundaryDirty = chunk.lightBoundaryDirty or (r and 0x3F)   // faces de bord changées
                for (f in 0 until 6) {
                    if (r and (1 shl f) == 0) continue
                    val o = LIGHT_FACE_OFFSETS[f]
                    val nb = world.getChunk(chunk.cx + o[0], chunk.cy + o[1], chunk.cz + o[2]) ?: continue
                    if (nb.generated) world.enqueueLight(nb.cx, nb.cy, nb.cz)   // propager la lumière
                }
                world.enqueueLight(key)                // re-vérifier ce chunk jusqu'à stabilisation
            } else if (chunk.lightMeshDirty || chunk.meshDirty) {
                chunk.lightMeshDirty = false           // stabilisé → un seul rebuild du mesh
                chunk.meshDirty = true
                world.rebuildQueue.add(key)
                // Notre lumière de bord est maintenant finale : les voisins dont le bord partagé a
                // changé doivent re-mesher (leurs faces de bord échantillonnent notre lumière, sinon
                // elles restent noires). On ne le fait qu'ici, une fois → pas de churn pendant la convergence.
                val bd = chunk.lightBoundaryDirty
                chunk.lightBoundaryDirty = 0
                for (f in 0 until 6) {
                    if (bd and (1 shl f) == 0) continue
                    val o = LIGHT_FACE_OFFSETS[f]
                    val nb = world.getChunk(chunk.cx + o[0], chunk.cy + o[1], chunk.cz + o[2]) ?: continue
                    if (nb.generated && !nb.lightMeshDirty) {
                        nb.meshDirty = true
                        world.rebuildQueue.add(world.chunkKey(nb.cx, nb.cy, nb.cz))
                    }
                }
            }
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
            meshes.getOrPut(key) { ChunkMesh(7) }.upload(verts)
            if (waterVerts.isNotEmpty()) waterMeshes.getOrPut(key) { ChunkMesh(7) }.upload(waterVerts)
            else waterMeshes.remove(key)?.destroy()
            refreshChunkLightSources(chunk)
            if (ncy in 0..SURFACE_CY_MAX) {
                lodCache?.invalidate(ncx, ncz)
                scheduleLodBuild(ncx, ncz)
            }
        }
    }

    private fun raycastBlock(): RayHit? {
        val dirX = camera.aimX.toDouble()
        val dirY = camera.aimY.toDouble()
        val dirZ = camera.aimZ.toDouble()

        // En TPS le ray part du point d'orbite (aligné avec la croix : caméra→orbite→bloc).
        val startX = if (camera.thirdPerson) camera.playerX else camera.x
        val startY = if (camera.thirdPerson) camera.playerY + Camera.TPP_ORBIT_DY else camera.y
        val startZ = if (camera.thirdPerson) camera.playerZ else camera.z

        var bx = floorInt(startX); var by = floorInt(startY); var bz = floorInt(startZ)

        val stepX = if (dirX > 0) 1 else -1
        val stepY = if (dirY > 0) 1 else -1
        val stepZ = if (dirZ > 0) 1 else -1

        val tDX = if (dirX != 0.0) 1.0 / Math.abs(dirX) else Double.MAX_VALUE
        val tDY = if (dirY != 0.0) 1.0 / Math.abs(dirY) else Double.MAX_VALUE
        val tDZ = if (dirZ != 0.0) 1.0 / Math.abs(dirZ) else Double.MAX_VALUE

        var tMaxX = if (dirX > 0) (bx + 1 - startX) * tDX else (startX - bx) * tDX
        var tMaxY = if (dirY > 0) (by + 1 - startY) * tDY else (startY - by) * tDY
        var tMaxZ = if (dirZ > 0) (bz + 1 - startZ) * tDZ else (startZ - bz) * tDZ

        var fnx = 0; var fny = 0; var fnz = -1
        val reach = MINE_REACH.toDouble()

        repeat(MINE_REACH * 4) {
            val b = worldBlockAt(bx, by, bz)
            if (b != AIR && !isWater(b)) return RayHit(bx, by, bz, fnx, fny, fnz)
            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> {
                    if (tMaxX > reach) return null
                    fnx = -stepX; fny = 0; fnz = 0; bx += stepX; tMaxX += tDX
                }
                tMaxY <= tMaxZ -> {
                    if (tMaxY > reach) return null
                    fnx = 0; fny = -stepY; fnz = 0; by += stepY; tMaxY += tDY
                }
                else -> {
                    if (tMaxZ > reach) return null
                    fnx = 0; fny = 0; fnz = -stepZ; bz += stepZ; tMaxZ += tDZ
                }
            }
        }
        return null
    }

    // ── Blocs en chute libre (animation 200ms) ───────────────────────────────

    private fun drawFallingBlocks(dt: Float) {
        // Récupérer les nouvelles animations lancées par tickFalling
        while (true) {
            val item = world.pendingFallingBlocks.poll() ?: break
            fallingBlocks.add(FallingBlock(item[3].toShort(), item[0], item[1], item[2]))
        }
        if (fallingBlocks.isEmpty()) return

        // Mettre à jour les timers ; poser les blocs qui ont atterri
        val iter = fallingBlocks.iterator()
        while (iter.hasNext()) {
            val fb = iter.next()
            fb.timer = minOf(fb.timer + dt, FallingBlock.DURATION)
            if (fb.done) {
                world.onFallingBlockLanded(fb.wx, fb.wy - 1, fb.wz, fb.type)
                world.enqueueIfFalling(fb.wx, fb.wy - 1, fb.wz)
                forceMeshRebuild(fb.wx, fb.wy - 1, fb.wz)
                iter.remove()
            }
        }
        if (fallingBlocks.isEmpty()) return

        // Construire la géométrie (5 faces par bloc : top + 4 côtés)
        val floatsPerBlock = 5 * 6 * 6   // 5 faces × 6 sommets × 6 floats
        val verts = FloatArray(fallingBlocks.size * floatsPerBlock)
        var vi = 0
        fun v(x: Float, y: Float, z: Float, u: Float, v: Float, p: Float) {
            verts[vi++] = x; verts[vi++] = y; verts[vi++] = z
            verts[vi++] = u; verts[vi++] = v; verts[vi++] = p
        }

        for (fb in fallingBlocks) {
            val bx = (fb.wx - camera.x).toFloat(); val bz = (fb.wz - camera.z).toFloat()
            val by = (fb.visualY.toDouble() - camera.y).toFloat()
            val L = (BlockRegistry.get(fb.type)?.layerTop ?: 0).toFloat()
            // Top (face 0)
            val p0 = L
            v(bx,   by+1f, bz,   0f,0f,p0); v(bx+1f,by+1f,bz,  1f,0f,p0); v(bx+1f,by+1f,bz+1f,1f,1f,p0)
            v(bx,   by+1f, bz,   0f,0f,p0); v(bx+1f,by+1f,bz+1f,1f,1f,p0); v(bx,  by+1f,bz+1f,0f,1f,p0)
            // Est +X (face 2, rotCW)
            val p2 = 2f*128f+L
            v(bx+1f,by,  bz+1f,0f,1f,p2); v(bx+1f,by+1f,bz+1f,0f,0f,p2); v(bx+1f,by+1f,bz,1f,0f,p2)
            v(bx+1f,by,  bz+1f,0f,1f,p2); v(bx+1f,by+1f,bz,   1f,0f,p2); v(bx+1f,by,  bz,1f,1f,p2)
            // Ouest -X (face 3, rotCW)
            val p3 = 3f*128f+L
            v(bx,  by,  bz,   0f,1f,p3); v(bx,  by+1f,bz,   0f,0f,p3); v(bx,  by+1f,bz+1f,1f,0f,p3)
            v(bx,  by,  bz,   0f,1f,p3); v(bx,  by+1f,bz+1f,1f,0f,p3); v(bx,  by,  bz+1f,1f,1f,p3)
            // Nord +Z (face 4, rotCW)
            val p4 = 4f*128f+L
            v(bx,  by,  bz+1f,0f,1f,p4); v(bx,  by+1f,bz+1f,0f,0f,p4); v(bx+1f,by+1f,bz+1f,1f,0f,p4)
            v(bx,  by,  bz+1f,0f,1f,p4); v(bx+1f,by+1f,bz+1f,1f,0f,p4); v(bx+1f,by,  bz+1f,1f,1f,p4)
            // Sud -Z (face 5, rotCW)
            val p5 = 5f*128f+L
            v(bx+1f,by,  bz,  0f,1f,p5); v(bx+1f,by+1f,bz,  0f,0f,p5); v(bx,  by+1f,bz,  1f,0f,p5)
            v(bx+1f,by,  bz,  0f,1f,p5); v(bx,  by+1f,bz,   1f,0f,p5); v(bx,  by,  bz,  1f,1f,p5)
        }

        val vertCount = vi / 6

        // Upload dans un VBO dédié et dessiner avec le world shader (déjà actif)
        if (fallingVbo == 0) { val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0); fallingVbo = ids[0] }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fallingVbo)
        val buf = java.nio.ByteBuffer.allocateDirect(vi * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts, 0, vi); buf.position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vi * 4, buf, GLES30.GL_DYNAMIC_DRAW)

        val stride = 24   // 6 floats × 4 bytes
        GLES30.glUniform3f(wUChunkOffset, 0f, 0f, 0f)
        GLES30.glEnableVertexAttribArray(wAPos)
        GLES30.glVertexAttribPointer(wAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(wAUv)
        GLES30.glVertexAttribPointer(wAUv,  3, GLES30.GL_FLOAT, false, stride, 12)

        // Polygon offset : pousse l'entité légèrement vers la caméra pour éviter le z-fighting
        // avec l'ancien chunk mesh pendant sa reconstruction (dure quelques frames).
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1f, -1f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertCount)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    // ── Boîte de sélection structure ─────────────────────────────────────────

    private fun drawStructureSelection() {
        val a = structCornerA ?: return
        val b = structCornerB ?: return
        val minX = minOf(a.first, b.first); val maxX = maxOf(a.first, b.first) + 1
        val minY = minOf(a.second, b.second); val maxY = maxOf(a.second, b.second) + 1
        val minZ = minOf(a.third, b.third); val maxZ = maxOf(a.third, b.third) + 1

        val ep = 0.03f
        val x0 = (minX - ep - camera.x).toFloat(); val x1 = (maxX + ep - camera.x).toFloat()
        val y0 = (minY - ep - camera.y).toFloat(); val y1 = (maxY + ep - camera.y).toFloat()
        val z0 = (minZ - ep - camera.z).toFloat(); val z1 = (maxZ + ep - camera.z).toFloat()

        // Arêtes d'une boîte = 12 segments, chaque segment = 2 triangles quad de width hw
        val hw = 0.028f
        val cr = 0.2f; val cg = 1.0f; val cb = 0.4f  // vert translucide
        val out = ArrayList<Float>(12 * 6 * 2 * 6)
        fun seg(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
            val ddx = bx - ax; val ddy = by - ay; val ddz = bz - az
            val len = sqrt(ddx*ddx + ddy*ddy + ddz*ddz).coerceAtLeast(0.001f)
            val fx = ddx/len; val fy = ddy/len; val fz = ddz/len
            val upX = if (abs(fy) < 0.9f) 0f else 1f; val upY = if (abs(fy) < 0.9f) 1f else 0f
            var ruX = fy*0f-fz*upY; var ruY = fz*upX-fx*0f; var ruZ = fx*upY-fy*upX
            val rl = sqrt(ruX*ruX+ruY*ruY+ruZ*ruZ).coerceAtLeast(0.001f); ruX/=rl; ruY/=rl; ruZ/=rl
            val rvX = ruY*fz-ruZ*fy; val rvY = ruZ*fx-ruX*fz; val rvZ = ruX*fy-ruY*fx
            fun v(px:Float,py:Float,pz:Float){out.add(px);out.add(py);out.add(pz);out.add(cr);out.add(cg);out.add(cb)}
            v(ax+ruX*hw,ay+ruY*hw,az+ruZ*hw); v(ax-ruX*hw,ay-ruY*hw,az-ruZ*hw); v(bx-ruX*hw,by-ruY*hw,bz-ruZ*hw)
            v(ax+ruX*hw,ay+ruY*hw,az+ruZ*hw); v(bx-ruX*hw,by-ruY*hw,bz-ruZ*hw); v(bx+ruX*hw,by+ruY*hw,bz+ruZ*hw)
            v(ax+rvX*hw,ay+rvY*hw,az+rvZ*hw); v(ax-rvX*hw,ay-rvY*hw,az-rvZ*hw); v(bx-rvX*hw,by-rvY*hw,bz-rvZ*hw)
            v(ax+rvX*hw,ay+rvY*hw,az+rvZ*hw); v(bx-rvX*hw,by-rvY*hw,bz-rvZ*hw); v(bx+rvX*hw,by+ruY*hw,bz+ruZ*hw)
        }
        // 12 arêtes de la boîte
        seg(x0,y0,z0, x1,y0,z0); seg(x0,y0,z1, x1,y0,z1)
        seg(x0,y1,z0, x1,y1,z0); seg(x0,y1,z1, x1,y1,z1)
        seg(x0,y0,z0, x0,y0,z1); seg(x1,y0,z0, x1,y0,z1)
        seg(x0,y1,z0, x0,y1,z1); seg(x1,y1,z0, x1,y1,z1)
        seg(x0,y0,z0, x0,y1,z0); seg(x1,y0,z0, x1,y1,z0)
        seg(x0,y0,z1, x0,y1,z1); seg(x1,y0,z1, x1,y1,z1)

        val verts = out.toFloatArray()
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts); buf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, transientVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES30.GL_DYNAMIC_DRAW)
        laserShader?.use()
        GLES30.glUniformMatrix4fv(lUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(lAPos); GLES30.glVertexAttribPointer(lAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(lAColor); GLES30.glVertexAttribPointer(lAColor, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, verts.size / 6)
        GLES30.glDisableVertexAttribArray(lAPos); GLES30.glDisableVertexAttribArray(lAColor)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glDepthMask(true); GLES30.glDisable(GLES30.GL_BLEND)
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
        val dx = target.bx.toDouble() + 0.5 - camera.x
        val dy = target.by.toDouble() + 0.5 - camera.y
        val dz = target.bz.toDouble() + 0.5 - camera.z
        val dist = (dx * camera.aimX + dy * camera.aimY + dz * camera.aimZ).toFloat()
        val ex = camera.aimX * dist
        val ey = camera.aimY * dist
        val ez = camera.aimZ * dist

        // En TPS : laser depuis le point d'orbite (aligné avec la croix).
        // En FPS : depuis la "main droite" du joueur comme avant.
        val sx: Float; val sy: Float; val sz: Float
        if (camera.thirdPerson) {
            val d = Camera.TPP_DIST.toFloat()
            sx = camera.aimX * d; sy = camera.aimY * d; sz = camera.aimZ * d
        } else {
            val yawRad = Math.toRadians(camera.yaw.toDouble()).toFloat()
            val rX = cos(yawRad); val rZ = -sin(yawRad)
            sx = rX * 0.35f; sy = -0.25f; sz = rZ * 0.35f
        }

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

    // ── Collision caméra TPS ─────────────────────────────────────────────────

    private fun adjustTpsCamera() {
        if (!camera.thirdPerson) return
        val headX = camera.playerX
        val headY = camera.playerY + Camera.TPP_ORBIT_DY
        val headZ = camera.playerZ

        val dx = camera.x - headX
        val dy = camera.y - headY
        val dz = camera.z - headZ
        val maxDist = sqrt(dx * dx + dy * dy + dz * dz)
        if (maxDist < 0.1) return
        val nx = dx / maxDist; val ny = dy / maxDist; val nz = dz / maxDist

        // Avance pas à pas de la tête vers l'œil idéal ; stoppe au premier bloc solide.
        var safeDist = maxDist
        val steps = 16
        for (i in 1..steps) {
            val t = i * maxDist / steps
            val b = worldBlockAt(floorInt(headX + nx * t), floorInt(headY + ny * t), floorInt(headZ + nz * t))
            if (b != AIR && !isWater(b) && !isDecoration(b)) {
                safeDist = ((i - 1) * maxDist / steps).coerceAtLeast(0.4)
                break
            }
        }

        if (safeDist < maxDist - 0.05) {
            camera.applyCollision(
                headX + nx * safeDist,
                headY + ny * safeDist,
                headZ + nz * safeDist
            )
        }
    }

    // ── Boîte joueur (vue 3ème personne) ────────────────────────────────────

    private fun drawPlayerBox(dt: Float) {
        if (!camera.thirdPerson) return

        // Position joueur (floating origin)
        val px = (camera.playerX - camera.x).toFloat()
        val py = (camera.playerY - camera.y).toFloat()
        val pz = (camera.playerZ - camera.z).toFloat()

        // Repère local orienté dans la direction de marche du joueur
        val fwdX = camera.fwdX; val fwdZ = camera.fwdZ
        val rgtX = -fwdZ;       val rgtZ =  fwdX

        fun wx(r: Float, f: Float) = px + r * rgtX + f * fwdX
        fun wz(r: Float, f: Float) = pz + r * rgtZ + f * fwdZ

        // Avance la phase de marche uniquement quand le joueur se déplace
        val pdx = camera.playerX - walkLastX; val pdz = camera.playerZ - walkLastZ
        if (pdx * pdx + pdz * pdz > 1e-6) walkPhase = (walkPhase + dt * 7.5f) % (2f * Math.PI.toFloat())
        walkLastX = camera.playerX; walkLastZ = camera.playerZ

        val sinW = kotlin.math.sin(walkPhase)
        val legSwing = 0.38f * sinW   // jambes : ~22° d'amplitude
        val armSwing = 0.28f * sinW   // bras   : ~16° d'amplitude

        val yFeet     = py - 1.62f
        val yWaist    = yFeet + 0.72f
        val yShoulder = yFeet + 1.35f
        val yTop      = yFeet + 1.80f

        // 6 parties × 6 faces × 6 sommets × 6 floats
        val verts = FloatArray(6 * 6 * 6 * 6)
        var vi = 0
        fun v(vx: Float, vy: Float, vz: Float, cr: Float, cg: Float, cb: Float) {
            verts[vi++] = vx; verts[vi++] = vy; verts[vi++] = vz
            verts[vi++] = cr; verts[vi++] = cg; verts[vi++] = cb
        }

        // ── Boîte statique ───────────────────────────────────────────────────
        fun box(rMin: Float, rMax: Float, yMin: Float, yMax: Float, fMin: Float, fMax: Float,
                cr: Float, cg: Float, cb: Float) {
            v(wx(rMin,fMin),yMax,wz(rMin,fMin), cr,cg,cb)
            v(wx(rMax,fMin),yMax,wz(rMax,fMin), cr,cg,cb)
            v(wx(rMax,fMax),yMax,wz(rMax,fMax), cr,cg,cb)
            v(wx(rMin,fMin),yMax,wz(rMin,fMin), cr,cg,cb)
            v(wx(rMax,fMax),yMax,wz(rMax,fMax), cr,cg,cb)
            v(wx(rMin,fMax),yMax,wz(rMin,fMax), cr,cg,cb)
            val b = 0.50f
            v(wx(rMin,fMax),yMin,wz(rMin,fMax), cr*b,cg*b,cb*b)
            v(wx(rMax,fMax),yMin,wz(rMax,fMax), cr*b,cg*b,cb*b)
            v(wx(rMax,fMin),yMin,wz(rMax,fMin), cr*b,cg*b,cb*b)
            v(wx(rMin,fMax),yMin,wz(rMin,fMax), cr*b,cg*b,cb*b)
            v(wx(rMax,fMin),yMin,wz(rMax,fMin), cr*b,cg*b,cb*b)
            v(wx(rMin,fMin),yMin,wz(rMin,fMin), cr*b,cg*b,cb*b)
            val rs = 0.78f
            v(wx(rMax,fMax),yMin,wz(rMax,fMax), cr*rs,cg*rs,cb*rs)
            v(wx(rMax,fMax),yMax,wz(rMax,fMax), cr*rs,cg*rs,cb*rs)
            v(wx(rMax,fMin),yMax,wz(rMax,fMin), cr*rs,cg*rs,cb*rs)
            v(wx(rMax,fMax),yMin,wz(rMax,fMax), cr*rs,cg*rs,cb*rs)
            v(wx(rMax,fMin),yMax,wz(rMax,fMin), cr*rs,cg*rs,cb*rs)
            v(wx(rMax,fMin),yMin,wz(rMax,fMin), cr*rs,cg*rs,cb*rs)
            v(wx(rMin,fMin),yMin,wz(rMin,fMin), cr*rs,cg*rs,cb*rs)
            v(wx(rMin,fMin),yMax,wz(rMin,fMin), cr*rs,cg*rs,cb*rs)
            v(wx(rMin,fMax),yMax,wz(rMin,fMax), cr*rs,cg*rs,cb*rs)
            v(wx(rMin,fMin),yMin,wz(rMin,fMin), cr*rs,cg*rs,cb*rs)
            v(wx(rMin,fMax),yMax,wz(rMin,fMax), cr*rs,cg*rs,cb*rs)
            v(wx(rMin,fMax),yMin,wz(rMin,fMax), cr*rs,cg*rs,cb*rs)
            val fs = 0.88f
            v(wx(rMin,fMax),yMin,wz(rMin,fMax), cr*fs,cg*fs,cb*fs)
            v(wx(rMin,fMax),yMax,wz(rMin,fMax), cr*fs,cg*fs,cb*fs)
            v(wx(rMax,fMax),yMax,wz(rMax,fMax), cr*fs,cg*fs,cb*fs)
            v(wx(rMin,fMax),yMin,wz(rMin,fMax), cr*fs,cg*fs,cb*fs)
            v(wx(rMax,fMax),yMax,wz(rMax,fMax), cr*fs,cg*fs,cb*fs)
            v(wx(rMax,fMax),yMin,wz(rMax,fMax), cr*fs,cg*fs,cb*fs)
            val bk = 0.62f
            v(wx(rMax,fMin),yMin,wz(rMax,fMin), cr*bk,cg*bk,cb*bk)
            v(wx(rMax,fMin),yMax,wz(rMax,fMin), cr*bk,cg*bk,cb*bk)
            v(wx(rMin,fMin),yMax,wz(rMin,fMin), cr*bk,cg*bk,cb*bk)
            v(wx(rMax,fMin),yMin,wz(rMax,fMin), cr*bk,cg*bk,cb*bk)
            v(wx(rMin,fMin),yMax,wz(rMin,fMin), cr*bk,cg*bk,cb*bk)
            v(wx(rMin,fMin),yMin,wz(rMin,fMin), cr*bk,cg*bk,cb*bk)
        }

        // ── Membre avec balancement (rotation autour du pivot supérieur) ──────
        // swing > 0 → membre part vers l'avant, swing < 0 → vers l'arrière
        fun swingLimb(rMin: Float, rMax: Float, yPivot: Float, yBot: Float,
                      fMin: Float, fMax: Float, swing: Float,
                      cr: Float, cg: Float, cb: Float) {
            val c = kotlin.math.cos(swing); val s = kotlin.math.sin(swing)
            // Rotation dans le plan (Y, fwd) autour de yPivot
            fun yW(y: Float, f: Float) = yPivot + (y - yPivot) * c + f * s
            fun fW(y: Float, f: Float) = -(y - yPivot) * s + f * c
            fun vt(r: Float, y: Float, f: Float, sh: Float) {
                val fw = fW(y, f); val yw = yW(y, f)
                v(wx(r, fw), yw, wz(r, fw), cr * sh, cg * sh, cb * sh)
            }
            // Top
            vt(rMin,yPivot,fMin,1.00f); vt(rMax,yPivot,fMin,1.00f); vt(rMax,yPivot,fMax,1.00f)
            vt(rMin,yPivot,fMin,1.00f); vt(rMax,yPivot,fMax,1.00f); vt(rMin,yPivot,fMax,1.00f)
            // Bottom
            vt(rMin,yBot,fMax,0.50f); vt(rMax,yBot,fMax,0.50f); vt(rMax,yBot,fMin,0.50f)
            vt(rMin,yBot,fMax,0.50f); vt(rMax,yBot,fMin,0.50f); vt(rMin,yBot,fMin,0.50f)
            // Right +r
            vt(rMax,yBot,fMax,0.78f); vt(rMax,yPivot,fMax,0.78f); vt(rMax,yPivot,fMin,0.78f)
            vt(rMax,yBot,fMax,0.78f); vt(rMax,yPivot,fMin,0.78f); vt(rMax,yBot,fMin,0.78f)
            // Left -r
            vt(rMin,yBot,fMin,0.78f); vt(rMin,yPivot,fMin,0.78f); vt(rMin,yPivot,fMax,0.78f)
            vt(rMin,yBot,fMin,0.78f); vt(rMin,yPivot,fMax,0.78f); vt(rMin,yBot,fMax,0.78f)
            // Front +fwd
            vt(rMin,yBot,fMax,0.88f); vt(rMin,yPivot,fMax,0.88f); vt(rMax,yPivot,fMax,0.88f)
            vt(rMin,yBot,fMax,0.88f); vt(rMax,yPivot,fMax,0.88f); vt(rMax,yBot,fMax,0.88f)
            // Back -fwd
            vt(rMax,yBot,fMin,0.62f); vt(rMax,yPivot,fMin,0.62f); vt(rMin,yPivot,fMin,0.62f)
            vt(rMax,yBot,fMin,0.62f); vt(rMin,yPivot,fMin,0.62f); vt(rMin,yBot,fMin,0.62f)
        }

        // Tête (statique)
        box(-0.225f,  0.225f, yShoulder, yTop,            -0.225f, 0.225f, 0.85f, 0.72f, 0.60f)
        // Torse (statique)
        box(-0.22f,   0.22f,  yWaist,   yShoulder,        -0.15f,  0.15f,  0.38f, 0.42f, 0.68f)
        // Bras gauche — swing opposé à la jambe gauche (naturel)
        swingLimb(-0.37f, -0.25f, yShoulder, yShoulder - 0.60f, -0.12f, 0.12f, -armSwing, 0.38f, 0.42f, 0.68f)
        // Bras droit — swing opposé à la jambe droite
        swingLimb( 0.25f,  0.37f, yShoulder, yShoulder - 0.60f, -0.12f, 0.12f,  armSwing, 0.38f, 0.42f, 0.68f)
        // Jambe gauche
        swingLimb(-0.22f, -0.02f, yWaist, yFeet, -0.15f, 0.15f,  legSwing, 0.22f, 0.26f, 0.48f)
        // Jambe droite — toujours opposé à la jambe gauche
        swingLimb( 0.02f,  0.22f, yWaist, yFeet, -0.15f, 0.15f, -legSwing, 0.22f, 0.26f, 0.48f)

        val floatCount = vi
        val buf = ByteBuffer.allocateDirect(floatCount * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts, 0, floatCount); buf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, playerBoxVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floatCount * 4, buf, GLES30.GL_DYNAMIC_DRAW)

        laserShader?.use()
        GLES30.glUniformMatrix4fv(lUMvp, 1, false, camera.vpMatrix, 0)

        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(lAPos)
        GLES30.glVertexAttribPointer(lAPos,   3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(lAColor)
        GLES30.glVertexAttribPointer(lAColor, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, floatCount / 6)
        GLES30.glDisableVertexAttribArray(lAPos)
        GLES30.glDisableVertexAttribArray(lAColor)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    // ── Viewmodel 1re personne (bras + objet tenu) ────────────────────────────

    /** Déclenche une animation de swing (un coup aller-retour). */
    internal fun startSwing() { swingActive = true; swingTimer = 0f }

    private fun drawViewmodel(dt: Float) {
        if (camera.thirdPerson) return

        // ── Animation ─────────────────────────────────────────────────────────
        // Minage en cours → swing en boucle ; sinon swing one-shot (attaque/pose).
        val mining = mineTarget != null && !gamePaused
        if (mining) {
            swingActive = true
            swingTimer += dt
            if (swingTimer > SWING_DUR) swingTimer = 0f
        } else if (swingActive) {
            swingTimer += dt
            if (swingTimer >= SWING_DUR) { swingActive = false; swingTimer = 0f }
        }
        val swing = if (swingActive) sin((swingTimer / SWING_DUR) * PI.toFloat()) else 0f

        // ── Matrice du bras (repère vue) ──────────────────────────────────────
        // Épaule en bas-droite (hors écran), avant-bras qui remonte vers le centre.
        android.opengl.Matrix.setIdentityM(vmModel, 0)
        android.opengl.Matrix.translateM(vmModel, 0, 0.50f, -0.70f - 0.03f * swing, -0.78f)
        android.opengl.Matrix.rotateM(vmModel, 0, 13f, 0f, 0f, 1f)             // penche vers le centre
        android.opengl.Matrix.rotateM(vmModel, 0, -8f - 48f * swing, 1f, 0f, 0f)  // avant-bras + coup
        android.opengl.Matrix.rotateM(vmModel, 0, -6f, 0f, 1f, 0f)

        // Profondeur fraîche : le viewmodel se dessine par-dessus le décor, sans s'y enfoncer.
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)

        val held = hotbar[selectedSlot]
        val isWeapon = held != null && com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(held)

        drawArm(isWeapon)

        if (held != null) {
            when {
                isWeapon                     -> drawHeldWeapon(held)
                BlockRegistry.isDecoration(held) -> drawHeldFlat(held)
                else                         -> drawHeldBlock(held)
            }
        }
    }

    /**
     * Bras voxel (manche + peau) dessiné avec le laser shader (couleur plate).
     * En mode arme/outil ([weapon] = true), on n'affiche qu'une main courte au niveau de la
     * poignée pour ne pas chevaucher le sprite de l'arme qui remonte au-dessus.
     */
    private fun drawArm(weapon: Boolean) {
        val w = 0.058f
        // Manche + peau : long avant-bras (blocs/main vide) ou main courte (arme).
        val sleeveTop = if (weapon) 0.20f else 0.28f
        val skinTop   = if (weapon) 0.40f else 0.56f
        val arr = FloatArray(2 * 36 * 6)
        var o = 0
        o = vmBox(arr, o, -w, w, 0.00f, sleeveTop, -w, w, ARM_SLEEVE[0], ARM_SLEEVE[1], ARM_SLEEVE[2])
        o = vmBox(arr, o, -w * 0.88f, w * 0.88f, sleeveTop, skinTop, -w * 0.88f, w * 0.88f, ARM_SKIN[0], ARM_SKIN[1], ARM_SKIN[2])

        android.opengl.Matrix.multiplyMM(vmMvp, 0, vmProj, 0, vmModel, 0)
        val buf = ByteBuffer.allocateDirect(o * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(arr, 0, o); buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vmVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, o * 4, buf, GLES30.GL_DYNAMIC_DRAW)
        laserShader?.use()
        GLES30.glUniformMatrix4fv(lUMvp, 1, false, vmMvp, 0)
        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(lAPos)
        GLES30.glVertexAttribPointer(lAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(lAColor)
        GLES30.glVertexAttribPointer(lAColor, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, o / 6)
        GLES30.glDisableVertexAttribArray(lAPos)
        GLES30.glDisableVertexAttribArray(lAColor)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Boîte colorée (pos3 + couleur3) avec ombrage de face cuit ; renvoie l'offset suivant. */
    private fun vmBox(a: FloatArray, o0: Int, x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float,
                      r: Float, g: Float, b: Float): Int {
        var o = o0
        fun q(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
              cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, sh: Float) {
            val cr = r * sh; val cg = g * sh; val cb = b * sh
            a[o++]=ax;a[o++]=ay;a[o++]=az;a[o++]=cr;a[o++]=cg;a[o++]=cb
            a[o++]=bx;a[o++]=by;a[o++]=bz;a[o++]=cr;a[o++]=cg;a[o++]=cb
            a[o++]=cx;a[o++]=cy;a[o++]=cz;a[o++]=cr;a[o++]=cg;a[o++]=cb
            a[o++]=ax;a[o++]=ay;a[o++]=az;a[o++]=cr;a[o++]=cg;a[o++]=cb
            a[o++]=cx;a[o++]=cy;a[o++]=cz;a[o++]=cr;a[o++]=cg;a[o++]=cb
            a[o++]=dx;a[o++]=dy;a[o++]=dz;a[o++]=cr;a[o++]=cg;a[o++]=cb
        }
        q(x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, 1.00f)   // +Y
        q(x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, 0.50f)   // −Y
        q(x1,y0,z1, x1,y1,z1, x1,y1,z0, x1,y0,z0, 0.78f)   // +X
        q(x0,y0,z0, x0,y1,z0, x0,y1,z1, x0,y0,z1, 0.78f)   // −X
        q(x0,y0,z1, x0,y1,z1, x1,y1,z1, x1,y0,z1, 0.92f)   // +Z (face caméra)
        q(x1,y0,z0, x1,y1,z0, x0,y1,z0, x0,y0,z0, 0.62f)   // −Z
        return o
    }

    /** Cube texturé du bloc tenu, présenté en biais dans le poing (world shader). */
    private fun drawHeldBlock(block: Short) {
        val s = 0.145f
        System.arraycopy(vmModel, 0, vmTmp, 0, 16)
        android.opengl.Matrix.translateM(vmTmp, 0, 0.0f, 0.60f, 0.06f)
        android.opengl.Matrix.rotateM(vmTmp, 0, -28f, 0f, 1f, 0f)
        android.opengl.Matrix.rotateM(vmTmp, 0, 20f, 1f, 0f, 0f)

        val arr = FloatArray(36 * 7)
        var o = 0
        fun lyr(face: Int) = BlockRegistry.getLayerForFace(block, face, AIR, 0).toFloat()
        o = vmQuad7(arr, o, -s, s,-s,0f,0f,  s, s,-s,1f,0f,  s, s, s,1f,1f, -s, s, s,0f,1f, 0*4096f+lyr(0))  // top
        o = vmQuad7(arr, o, -s,-s, s,0f,0f,  s,-s, s,1f,0f,  s,-s,-s,1f,1f, -s,-s,-s,0f,1f, 1*4096f+lyr(1))  // bottom
        o = vmQuad7(arr, o,  s,-s, s,0f,0f,  s, s, s,1f,0f,  s, s,-s,1f,1f,  s,-s,-s,0f,1f, 2*4096f+lyr(2))  // +X
        o = vmQuad7(arr, o, -s,-s,-s,0f,0f, -s, s,-s,1f,0f, -s, s, s,1f,1f, -s,-s, s,0f,1f, 3*4096f+lyr(3))  // −X
        o = vmQuad7(arr, o, -s,-s, s,0f,0f, -s, s, s,1f,0f,  s, s, s,1f,1f,  s,-s, s,0f,1f, 4*4096f+lyr(4))  // +Z
        o = vmQuad7(arr, o,  s,-s,-s,0f,0f,  s, s,-s,1f,0f, -s, s,-s,1f,1f, -s,-s,-s,0f,1f, 5*4096f+lyr(5))  // −Z
        drawWorldVm(arr, o, vmTmp)
    }

    /** Bloc-décoration (fleur, herbe…) tenu à plat dans le poing. */
    private fun drawHeldFlat(block: Short) {
        val s = 0.15f
        val layer = BlockRegistry.getLayerForDecoration(block).toFloat()
        System.arraycopy(vmModel, 0, vmTmp, 0, 16)
        android.opengl.Matrix.translateM(vmTmp, 0, 0.0f, 0.58f, 0.06f)
        android.opengl.Matrix.rotateM(vmTmp, 0, -14f, 0f, 1f, 0f)
        val arr = FloatArray(6 * 7)
        val o = vmQuad7(arr, 0, -s,-s,0f,0f,1f,  s,-s,0f,1f,1f,  s, s,0f,1f,0f, -s, s,0f,0f,0f, 0*4096f+layer)
        drawWorldVm(arr, o, vmTmp)
    }

    /** Quad (pos3,uv2,packed,sky) pour le world shader ; renvoie l'offset suivant. */
    private fun vmQuad7(a: FloatArray, o0: Int,
                        x0: Float, y0: Float, z0: Float, u0: Float, v0: Float,
                        x1: Float, y1: Float, z1: Float, u1: Float, v1: Float,
                        x2: Float, y2: Float, z2: Float, u2: Float, v2: Float,
                        x3: Float, y3: Float, z3: Float, u3: Float, v3: Float,
                        packed: Float): Int {
        var o = o0
        fun vert(x: Float, y: Float, z: Float, u: Float, v: Float) {
            a[o++]=x;a[o++]=y;a[o++]=z;a[o++]=u;a[o++]=v;a[o++]=packed;a[o++]=1f
        }
        vert(x0,y0,z0,u0,v0); vert(x1,y1,z1,u1,v1); vert(x2,y2,z2,u2,v2)
        vert(x0,y0,z0,u0,v0); vert(x2,y2,z2,u2,v2); vert(x3,y3,z3,u3,v3)
        return o
    }

    /** Dessine une géométrie world-shader (atlas de blocs) en pleine lumière fixe. */
    private fun drawWorldVm(arr: FloatArray, count: Int, model: FloatArray) {
        android.opengl.Matrix.multiplyMM(vmMvp, 0, vmProj, 0, model, 0)
        val buf = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(arr, 0, count); buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vmVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, count * 4, buf, GLES30.GL_DYNAMIC_DRAW)
        worldShader?.use()
        GLES30.glUniformMatrix4fv(wUMvp, 1, false, vmMvp, 0)
        GLES30.glUniform3f(wUChunkOffset, 0f, 0f, 0f)
        GLES30.glUniform1f(wUAmbient, 1.0f)
        GLES30.glUniform1f(wUCaveFloor, 0f)
        GLES30.glUniform1i(wULightCount, 0)
        GLES30.glUniform1f(wUTime, elapsed)
        GLES30.glUniform1f(wUUnderwater, 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, blockTexArray)
        GLES30.glUniform1i(wUTex, 0)
        val stride = 7 * 4
        GLES30.glEnableVertexAttribArray(wAPos)
        GLES30.glVertexAttribPointer(wAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(wAUv)
        GLES30.glVertexAttribPointer(wAUv, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(wASky)
        GLES30.glVertexAttribPointer(wASky, 1, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count / 7)
        GLES30.glDisableVertexAttribArray(wAPos)
        GLES30.glDisableVertexAttribArray(wAUv)
        GLES30.glDisableVertexAttribArray(wASky)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Arme/outil tenu : sprite 2D du PNG, présenté en diagonale (billboard shader). */
    private fun drawHeldWeapon(id: Short) {
        val weapon = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(id) ?: return
        val def = ItemRegistry.get(weapon.defId) ?: return
        val tex = itemTexture(def.sprite)
        if (tex == 0) return

        val s = 0.30f
        System.arraycopy(vmModel, 0, vmTmp, 0, 16)
        // Manche ancré dans le poing, tête/lame redressée vers le haut en diagonale
        // (roll négatif = redresse la diagonale de la texture vers la verticale),
        // légèrement inclinée vers l'avant et tournée pour un rendu 3D.
        android.opengl.Matrix.translateM(vmTmp, 0, 0.0f, 0.52f, 0.05f)
        android.opengl.Matrix.rotateM(vmTmp, 0, -16f, 1f, 0f, 0f)   // pointe vers l'avant
        android.opengl.Matrix.rotateM(vmTmp, 0, -12f, 0f, 1f, 0f)   // léger angle 3D
        android.opengl.Matrix.rotateM(vmTmp, 0, -42f, 0f, 0f, 1f)   // redresse la lame
        android.opengl.Matrix.multiplyMM(vmMvp, 0, vmProj, 0, vmTmp, 0)

        val v = floatArrayOf(
            -s,-s,0f, 0f,1f,   s,-s,0f, 1f,1f,   s, s,0f, 1f,0f,
            -s,-s,0f, 0f,1f,   s, s,0f, 1f,0f,  -s, s,0f, 0f,0f
        )
        val buf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(v); buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vmVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, v.size * 4, buf, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        billboardShader?.use()
        GLES30.glUniformMatrix4fv(bUMvp, 1, false, vmMvp, 0)
        GLES30.glUniform1f(bUAlpha, 1f)
        GLES30.glUniform1f(bUMask, 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glUniform1i(bUTex, 0)
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(bAPos)
        GLES30.glVertexAttribPointer(bAPos, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(bAUv)
        GLES30.glVertexAttribPointer(bAUv, 2, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glDisableVertexAttribArray(bAPos)
        GLES30.glDisableVertexAttribArray(bAUv)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /** Texture GL d'un sprite d'item (cache par nom ; 0 si introuvable). */
    private fun itemTexture(sprite: String): Int {
        itemTexCache[sprite]?.let { return it }
        val tex = runCatching {
            val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); val t = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
            val bmp = BitmapFactory.decodeStream(context.assets.open("Cave World/Items/$sprite.png"))
            val b = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
            bmp.copyPixelsToBuffer(b); b.position(0)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, bmp.width, bmp.height, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, b)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            bmp.recycle()
            t
        }.getOrDefault(0)
        itemTexCache[sprite] = tex
        return tex
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
        val y0 = -camera.y.toFloat();                                  val y1 = y0 + SURFACE_BAND_H
        val z0 = (scz.toDouble() * CHUNK_SIZE - camera.z).toFloat();  val z1 = z0 + w
        for (p in frustum) {
            val px = if (p[0] >= 0f) x1 else x0
            val py = if (p[1] >= 0f) y1 else y0
            val pz = if (p[2] >= 0f) z1 else z0
            if (p[0]*px + p[1]*py + p[2]*pz + p[3] < 0f) return false
        }
        return true
    }

    // Frustum test élargi en Y pour couvrir toute la bande de surface (jusqu'aux montagnes).
    private fun isLodColumnInFrustum(cx: Int, cz: Int): Boolean {
        val x0 = (cx.toDouble() * CHUNK_SIZE - camera.x).toFloat(); val x1 = x0 + CHUNK_SIZE
        val y0 = -camera.y.toFloat();                                val y1 = y0 + SURFACE_BAND_H
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

    // ── Cycle jour/nuit fictif (20 min jour + 10 min nuit = 30 min total) ────
    // gameTimeMs : ms de temps de jeu écoulées (ne progresse pas en pause).
    // t=0.25 = 6h (aube) · t=0.50 = 12h (midi) · t=0.75 = 18h (crépuscule) · t=0.0 = 0h (minuit)
    // Quarts : 6h=0 ms, 12h=600 000 ms, 18h=1 200 000 ms, 0h=1 500 000 ms
    private var gameTimeMs: Long = 0L
    // 10h du matin IG : 6h = 0 ms, et la portion jour avance à 100 000 ms par heure de jeu.
    private val NEW_GAME_START_MS = 400_000L

    fun cycleTimeOfDay() {
        val quarters = longArrayOf(0L, 600_000L, 1_200_000L, 1_500_000L)
        val cur = gameTimeMs % 1_800_000L
        val next = quarters.firstOrNull { it > cur } ?: 0L
        gameTimeMs = gameTimeMs - cur + next
    }

    val timeOfDayLabel: String get() {
        val cur = gameTimeMs % 1_800_000L
        return when {
            cur < 600_000L   -> "🌄"
            cur < 1_200_000L -> "☀"
            cur < 1_500_000L -> "🌆"
            else             -> "🌙"
        }
    }

    private fun dayFraction(): Float {
        val gf = (gameTimeMs % 1_800_000L) / 1_800_000f
        return if (gf < 0.6667f) {
            0.25f + gf * 0.75f
        } else {
            (0.75f + (gf - 0.6667f) * 1.5f) % 1f
        }
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

    private var speedXpAccum = 0f
    private var prevSprinting = false

    private fun updateWalk(dt: Float) {
        val yawRad = Math.toRadians(camera.yaw.toDouble())
        val fX = sin(yawRad).toFloat(); val fZ = cos(yawRad).toFloat()
        val rX = cos(yawRad).toFloat(); val rZ = -sin(yawRad).toFloat()
        val chargeMul = if (rockChargeTime > 0f) 0.55f else 1f

        physics.isSprinting = touch.sprintActive && physics.onGround
        val nowSprinting = physics.isSprinting
        if (nowSprinting != prevSprinting) {
            prevSprinting = nowSprinting
            sprintCallback?.invoke(nowSprinting)
        }

        val (newX, newY, newZ) = physics.updateWalk(
            dt,
            camera.playerX, camera.playerY, camera.playerZ,
            fX, fZ, rX, rZ,
            touch.moveForward * chargeMul, touch.moveRight * chargeMul, touch.flyUp
        )

        // XP Speed : distance parcourue au sol
        if (physics.onGround) {
            val dx = newX - camera.playerX; val dz = newZ - camera.playerZ
            val dist = sqrt(dx * dx + dz * dz).toFloat()
            val rate = if (touch.sprintActive) 0.3f else 0.1f
            speedXpAccum += dist * rate
            if (speedXpAccum >= 1f) {
                skillBook.speedXp += speedXpAccum.toInt()
                speedXpAccum -= speedXpAccum.toInt()
            }
        }

        camera.playerX = newX; camera.playerY = newY; camera.playerZ = newZ
    }

    // ── Collision (délégation vers PhysicsNode) ───────────────────────────────

    private fun isHeadInWater(): Boolean =
        physics.isHeadInWater(camera.playerX, camera.playerY, camera.playerZ)

    private fun collidesAt(px: Double, py: Double, pz: Double): Boolean =
        physics.collidesAt(px, py, pz)

    private fun worldBlockAt(wx: Int, wy: Int, wz: Int): Short {
        val cx = Math.floorDiv(wx, CHUNK_SIZE); val cy = Math.floorDiv(wy, CHUNK_SIZE); val cz = Math.floorDiv(wz, CHUNK_SIZE)
        val chunk = world.getChunk(cx, cy, cz) ?: return AIR
        if (!chunk.generated) return AIR
        return chunk.blockAt(wx-cx*CHUNK_SIZE, wy-cy*CHUNK_SIZE, wz-cz*CHUNK_SIZE)
    }

    private fun floorInt(d: Double) = Math.floor(d).toInt()

    // ── Mode switch ───────────────────────────────────────────────────────────

    private fun applyModeSwitch(newMode: PlayerMode) {
        if (newMode == PlayerMode.WALK) {
            val bcx = Math.floorDiv(floorInt(camera.playerX), CHUNK_SIZE)
            val bcy = Math.floorDiv(floorInt(camera.playerY), CHUNK_SIZE)
            val bcz = Math.floorDiv(floorInt(camera.playerZ), CHUNK_SIZE)
            val inLoadedSolid = world.getChunk(bcx, bcy, bcz)?.generated == true &&
                                collidesAt(camera.playerX, camera.playerY, camera.playerZ)
            if (inLoadedSolid) {
                var safeY = camera.playerY
                repeat(64) { if (collidesAt(camera.playerX, safeY, camera.playerZ)) safeY += 1.0 }
                camera.playerY = safeY
            }
            physics.reset()
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
        if (index !in 0..18) return
        selectedSlot = index
        hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
    }

    // Détermine le meta d'orientation au moment du placement.
    // AXIS  : axe dérivé de la face cliquée (X si paroi E/W, Z si paroi N/S, Y sinon).
    // FACING: direction vers le joueur depuis la face cliquée ; sur sol/plafond → yaw caméra.
    private fun computeOrientMeta(blockType: Short, fnx: Int, fny: Int, fnz: Int): Byte {
        if (!BlockRegistry.isOrientable(blockType)) return 0
        return when (BlockRegistry.getOrientMode(blockType)) {
            ORIENT_AXIS -> when {
                fnx != 0 -> 1  // paroi E/W → axe X
                fnz != 0 -> 2  // paroi N/S → axe Z
                else     -> 0  // sol/plafond → axe Y (défaut)
            }
            ORIENT_FACING -> when {
                fnx > 0  -> 2  // joueur à l'est   → front face Est
                fnx < 0  -> 3  // joueur à l'ouest → front face Ouest
                fnz > 0  -> 1  // joueur au sud     → front face Sud
                fnz < 0  -> 0  // joueur au nord    → front face Nord
                else -> {      // sol/plafond : orienter vers le joueur via yaw
                    val fx = camera.fwdX; val fz = camera.fwdZ
                    if (kotlin.math.abs(fx) > kotlin.math.abs(fz)) {
                        if (fx > 0) 3 else 2  // joueur regarde Est→front Ouest ; Ouest→Est
                    } else {
                        if (fz > 0) 0 else 1  // joueur regarde Sud→front Nord ; Nord→Sud
                    }
                }
            }
            else -> 0
        }
    }

    private fun placeBlock() {
        val blockType = hotbar[selectedSlot] ?: return
        startSwing()
        if ((inventory[blockType] ?: 0) <= 0) {
            hotbar[selectedSlot] = null
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            return
        }

        // Seau vide : raycast ignorant l'eau → l'eau est dans la position de face adjacente
        if (blockType == BUCKET_EMPTY) {
            val target = raycastBlock() ?: return
            // Le rayon traverse l'eau ; la source est dans la case côté joueur (face normale)
            val wx = target.bx + target.fnx
            val wy = target.by + target.fny
            val wz = target.bz + target.fnz
            if (world.blockAt(wx, wy, wz) != WATER) return
            world.setBlock(wx, wy, wz, AIR)
            world.onWaterSourceRemoved(wx, wy, wz)
            forceMeshRebuild(wx, wy, wz)
            swapBucketInInventory(BUCKET_EMPTY, BUCKET_FULL)
            return
        }

        // Seau plein : poser une source d'eau et récupérer un seau vide
        if (blockType == BUCKET_FULL) {
            val target = raycastBlock() ?: return
            val px = target.bx + target.fnx
            val py = target.by + target.fny
            val pz = target.bz + target.fnz
            if (isInsidePlayer(px, py, pz)) return
            if (world.blockAt(px, py, pz) != AIR) return
            world.setBlock(px, py, pz, WATER)
            world.onWaterSourcePlaced(px, py, pz)
            forceMeshRebuild(px, py, pz)
            swapBucketInInventory(BUCKET_FULL, BUCKET_EMPTY)
            return
        }

        val target = raycastBlock() ?: return
        val px = target.bx + target.fnx
        val py = target.by + target.fny
        val pz = target.bz + target.fnz
        if (isInsidePlayer(px, py, pz)) return
        world.setBlock(px, py, pz, blockType)
        val orientMeta = computeOrientMeta(blockType, target.fnx, target.fny, target.fnz)
        if (orientMeta != 0.toByte()) world.setMeta(px, py, pz, orientMeta)
        forceMeshRebuild(px, py, pz)
        if (blockType == WARD_STONE) enemyManager.wardStoneZones.add(Pair(px.toDouble(), pz.toDouble()))
        if (blockType == WATER) world.onWaterSourcePlaced(px, py, pz)
        if (isFalling(blockType)) world.enqueueIfFalling(px, py, pz)
        if (!isCreative) {
            inventory[blockType] = (inventory[blockType] ?: 1) - 1
            if ((inventory[blockType] ?: 0) <= 0) {
                inventory.remove(blockType)
                for (j in hotbar.indices) { if (hotbar[j] == blockType) hotbar[j] = null }
            }
        }
        inventoryCallback?.invoke(inventory.toMap())
        hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
    }

    private fun swapBucketInInventory(from: Short, to: Short) {
        if (isCreative) {
            inventory[to] = (inventory[to] ?: 0) + 1
            hotbar[selectedSlot] = to
            inventoryCallback?.invoke(inventory.toMap())
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            return
        }
        val newFromCount = (inventory[from] ?: 1) - 1
        if (newFromCount <= 0) inventory.remove(from) else inventory[from] = newFromCount
        inventory[to] = (inventory[to] ?: 0) + 1
        // Toujours remplacer dans le slot actif — le seau reste à la même position
        hotbar[selectedSlot] = to
        if (newFromCount <= 0) {
            for (j in hotbar.indices) {
                if (j != selectedSlot && hotbar[j] == from) hotbar[j] = null
            }
        }
        inventoryCallback?.invoke(inventory.toMap())
        hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
    }

    private fun isInsidePlayer(bx: Int, by: Int, bz: Int): Boolean {
        val x0 = floorInt(camera.playerX - 0.3); val x1 = floorInt(camera.playerX + 0.29)
        val y0 = floorInt(camera.playerY - 1.62); val y1 = floorInt(camera.playerY + 0.18)
        val z0 = floorInt(camera.playerZ - 0.3); val z1 = floorInt(camera.playerZ + 0.29)
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
        lodShader?.destroy()
        enemyRenderer.destroy()
        projRenderer.destroy()
        if (blockTexArray != 0) GLES30.glDeleteTextures(1, intArrayOf(blockTexArray), 0)
        if (transientVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(transientVbo), 0)
        if (playerBoxVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(playerBoxVbo), 0)
        if (vmVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vmVbo), 0)
        itemTexCache.values.filter { it != 0 }.forEach { GLES30.glDeleteTextures(1, intArrayOf(it), 0) }
        itemTexCache.clear()
    }

    companion object {
        private const val AUTO_SHOOT_RANGE = 30.0
        private const val PROJ_SPEED       = 15f
        private const val PROJ_MAX_DIST    = 40.0
    }
}
