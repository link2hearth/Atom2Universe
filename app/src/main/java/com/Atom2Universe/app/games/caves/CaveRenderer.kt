package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
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
import com.Atom2Universe.app.games.caves.entity.RangedProfile
import com.Atom2Universe.app.games.caves.entity.MagazineState
import com.Atom2Universe.app.games.caves.entity.ProjectileKind
import com.Atom2Universe.app.games.caves.entity.Projectile
import com.Atom2Universe.app.games.caves.entity.PlayerStats
import com.Atom2Universe.app.games.caves.entity.WeaponColor
import com.Atom2Universe.app.games.caves.entity.WeaponDef
import com.Atom2Universe.app.games.caves.entity.WeaponVariant
import com.Atom2Universe.app.games.caves.input.TouchController
import com.Atom2Universe.app.games.caves.mode.GameMode
import com.Atom2Universe.app.games.caves.mode.SurvivalMode
import com.Atom2Universe.app.games.caves.render.HeldEquipmentMesh
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
enum class HotbarMode { COMBAT, BUILD, GARDEN }

internal class CaveRenderer(
    private val context: Context,
    private val touch: TouchController,
    private val worldSeed: Long = System.currentTimeMillis(),
    private val worldId: String? = null,
    private val savedState: SavedState? = null,
    private val terrainVersion: Int = 5,
    /** Blocs préparés à l'avance (carte Assaut) ; null = génération procédurale. */
    private val worldSource: WorldSource? = null,
    /** Règles de la partie : la survie par défaut. */
    modeFactory: (CaveRenderer) -> GameMode = ::SurvivalMode,
) : GLSurfaceView.Renderer {
    private val vividStyle = CaveVisualStyle.isVivid(context)
    private val grayscaleStyle = CaveVisualStyle.current(context) == CaveVisualStyle.Theme.GRAYSCALE
    private var wATint = -1

    data class SavedState(
        val x: Double, val y: Double, val z: Double,
        val yaw: Float, val pitch: Float,
        val inventory: Map<Short, Int>,
        val hotbar: List<Short?>,
        val farming: String = "{}",
        val workshops: String = "{}",
        val worldTimeMs: Long = 400_000L,
        val frontierLife: String = "{}",
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
        val recoverableAmmo: List<StuckAmmo> = emptyList(),
        val passiveAnimals: String = "[]",
        val skillAthleticsXp:  Int = 0,
        val skillSpeedXp:      Int = 0,
        val skillEnduranceXp:  Int = 0,
        val skillAcrobaticsXp: Int = 0
    )

    val camera = Camera(8.0, 8.0, 8.0)
    private val storage = worldId?.let {
        CaveWorldChunkStorage(java.io.File(context.filesDir, "cave_worlds/$it"))
    }
    internal val world = World(seed = worldSeed, storage = storage, terrainVersion = terrainVersion,
                               source = worldSource)
    private val meshes = ConcurrentHashMap<Long, ChunkMesh>()
    private val decorSource = worldSource as? MapSource
    private val decorRenderer = decorSource?.let { com.Atom2Universe.app.games.caves.render.CaveDecorRenderer(it) }
    private val uploadQueue = ConcurrentLinkedQueue<LitMeshUpload>()

    private val lodMeshes      = ConcurrentHashMap<Long, ChunkMesh>()
    private val lodBuilding    = ConcurrentHashMap.newKeySet<Long>()
    private val lodRebuildRequested = ConcurrentHashMap.newKeySet<Long>()
    private val lodUploadQueue = ConcurrentLinkedQueue<Pair<Long, FloatArray>>()
    private val lodUploadQueueSize = java.util.concurrent.atomic.AtomicInteger(0)
    private val LOD_RADIUS   = 32
    private val INITIAL_LOD_BUILD_LIMIT = 512
    private val MAX_LOD_TILES = 8000
    private val LOD_SUPER    = 8                           // 8×8 = 64 colonnes par super-tuile
    // Hauteur (blocs) de la bande de surface, pour les bounding-box de frustum LOD.
    private val SURFACE_BAND_H = ((world.surfaceChunkMax + 1) * CHUNK_SIZE).toFloat()
    private val lodGrid      = HashMap<Long, ArrayList<Long>>() // super-tuile → liste de clés LOD
    private val lodCache = worldId?.let {
        LodCache(java.io.File(context.filesDir, "cave_worlds/$it/lod_cache.bin"))
    }

    private var worldShader: ShaderProgram? = null
    private var laserShader: ShaderProgram? = null
    private var starShader:  ShaderProgram? = null
    private var waterShader: ShaderProgram? = null
    private var waterPhaseUniform = 0
    private var waterTintUniform = 0
    private var waterSkyUniform = 0
    private val waterTint = floatArrayOf(.08f,.43f,.50f)
    private var waterBiomeKey = Long.MIN_VALUE
    private var targetWaterTint = floatArrayOf(.08f,.43f,.50f)
    private val visibleWaterKeys = ArrayList<Long>()
    private var lodShader:   ShaderProgram? = null
    private var wAPos = 0; private var wAUv = 0; private var wASky = 0; private var wUMvp = 0; private var wUTex = 0
    private var wUChunkOffset = 0; private var wUAmbient = 0; private var wUCaveFloor = 0
    private var wULightColors = 0; private var wULights = 0; private var wULightCount = 0; private var wUTime = 0
    private var wUUnderwater = 0
    private var lAPos = 0; private var lAColor = 0; private var lUMvp = 0; private var lUAlpha = 0
    private var sAPos = 0; private var sABrightness = 0; private var sUMvp = 0
    private var wWAPos = 0; private var wWAUv = 0; private var wWASky = 0; private var wWUMvp = 0
    private var wWUChunkOffset = 0; private var wWUAmbient = 0; private var wWUCaveFloor = 0
    private var wWULightColors = 0; private var wWULights = 0; private var wWULightCount = 0; private var wWUTime = 0
    private var lodAPos = 0; private var lodARgb = 0; private var lodUMvp = 0
    private var lodUChunkOffset = 0; private var lodUAmbient = 0
    private var blockTexArray = 0

    private val waterMeshes         = ConcurrentHashMap<Long, ChunkMesh>()
    private val waterOnlyUploadQueue = ConcurrentLinkedQueue<LitMeshUpload>()

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
    private val generationJobs = java.util.concurrent.atomic.AtomicInteger()
    private val meshJobs = java.util.concurrent.atomic.AtomicInteger()
    private val pendingMeshBytes = java.util.concurrent.atomic.AtomicLong()
    private val MAX_PENDING_MESH_BYTES = 8L * 1024 * 1024
    private var waitingMeshChunks = 0
    private var waitingFirstMeshes = 0
    // GL-thread backlog: retain waiting requests instead of reallocating a queue node
    // and sorting the entire backlog every frame.
    private val pendingMeshKeys = LinkedHashSet<Long>()
    private var meshDispatchSerial = 0L
    private var refreshMeshUploads = 0
    private var firstMeshUploads = 0
    private var provisionalUploads = 0
    private var perfLogNs = 0L
    private var perfFrames = 0

    private data class LitMeshUpload(
        val key: Long, val version: Int, val vertices: FloatArray,
        val lighting: MeshLightingSnapshot
    )
    private fun enqueueMesh(queue: ConcurrentLinkedQueue<LitMeshUpload>, upload: LitMeshUpload) {
        pendingMeshBytes.addAndGet(upload.vertices.size.toLong() * 4)
        queue.add(upload)
    }
    private fun pollMesh(queue: ConcurrentLinkedQueue<LitMeshUpload>): LitMeshUpload? {
        val upload = queue.poll() ?: return null
        pendingMeshBytes.addAndGet(-upload.vertices.size.toLong() * 4)
        return upload
    }
    // Le LOD ignore la skylight : mémoriser la géométrie déjà soumise.
    private val lodGeometryVersions = HashMap<Long, Pair<Chunk, Int>>()
    private val lodDispatcher = Dispatchers.Default.limitedParallelism(1)

    private var lastCx = Int.MAX_VALUE; private var lastCy = Int.MAX_VALUE; private var lastCz = Int.MAX_VALUE
    private var streamNeedsMore = false
    private var streamTickAccum = 0f
    private var lastFrameNs = 0L
    private var farmSessionReady = false
    private var fpsAccum = 0f; private var fpsFrames = 0   // moyenne FPS sur ~0.5 s
    private var posAccum = 0f                               // throttle affichage coordonnées ~10 Hz

    private val frustum = Array(6) { FloatArray(4) }
    private var cleanupCounter = 0
    private val CLEANUP_INTERVAL = 60   // nettoyage plus fréquent (~2s)
    private val MAX_MESHES = 1200       // cap mémoire : éviction au-delà
    // Budget de temps (pas de compte fixe) pour l'upload GPU des meshes de chunk par image —
    // voir le commentaire au point d'appel : évite qu'une rafale de chunks prêts en même
    // temps (typiquement au premier chargement) ne fige une seule image pendant ~1s.
    private val UPLOAD_BUDGET_NS = 6_000_000L   // 6 ms
    private val MAX_LIGHTS = 32
    // Pénombre : luminosité minimale partout (jamais 100 % noir). Une grotte sans torche reste
    // juste assez visible pour s'orienter ; une torche fait une vraie différence par-dessus.
    private val CAVE_FLOOR = 0.07f
    private val lightSources = HashMap<Triple<Int,Int,Int>, Float>()
    private data class ChunkLightState(val chunk: Chunk, val version: Int, val positions: List<Triple<Int, Int, Int>>)
    private val chunkLightStates = HashMap<Long, ChunkLightState>()
    private val lightColors = FloatArray(MAX_LIGHTS * 4)
    private val lightData = FloatArray(MAX_LIGHTS * 4)       // réutilisé chaque frame
    private val chunkLightData = FloatArray(MAX_LIGHTS * 4)
    // Sélection spatiale stable par chunk : les torches d'une salle éloignée éclairent
    // cette salle même si les 32 sources proches du joueur sont dans un autre étage.
    private class LocalLights(val data: FloatArray, val colors: FloatArray)
    private val localLights = HashMap<Long, LocalLights>()
    // Sélection des MAX_LIGHTS sources les plus proches sans allouer de liste : un
    // filter{}.sortedBy{}.take{} sur TOUTES les sources du monde chargé (potentiellement
    // des centaines de torches) tournait 20x/s, indépendamment du combat — un vrai foyer
    // de GC qui empirait avec l'exploration. Ces tableaux réutilisables font le même tri
    // top-K par insertion bornée, sans jamais allouer.
    private val lightSelectKey   = arrayOfNulls<Triple<Int,Int,Int>>(MAX_LIGHTS)
    private val lightSelectScore = DoubleArray(MAX_LIGHTS)
    private val lightSelectValue = FloatArray(MAX_LIGHTS)
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
    internal val physics = PhysicsNode { wx, wy, wz -> worldBlockAt(wx, wy, wz) }.apply {
        decorCollision = { x, feet, z, height -> decorSource?.decorCollides(x, feet, z, height, .30) == true }
        metaAt = { x, y, z -> world.metaAt(x, y, z) }
        waterContainsPoint = { x, y, z -> MeshBuilder.isPointInWater(world, x, y, z) }
        sampleWaterCurrent = { x, y, z, out -> WaterCurrent.sample(world, x, y, z, out) }
    }

    // ── Minage ────────────────────────────────────────────────────────────────

    private data class RayHit(val bx: Int, val by: Int, val bz: Int, val fnx: Int, val fny: Int, val fnz: Int) { var hitY: Double = 0.0; var distance: Double = 0.0 }
    private var mineTarget: RayHit? = null
    private var mineDamage = 0f

    val inventory = mutableMapOf<Short, Int>()

    // WeaponDef n'a aucun état mutable (couleur/variante fixes) : partagés plutôt que
    // réalloués à chaque tir/jet, qui pouvait dépasser 10 fois/s en tir automatique.
    private val ammoWeaponDef = WeaponDef(WeaponColor.WHITE, WeaponVariant.SQUARE)
    private val rockWeaponDef = WeaponDef(WeaponColor.BLUE, WeaponVariant.SWIRL)

    // One mixed shortcut bar. Item categories affect actions, never the destination slot.
    val hotbar = arrayOfNulls<Short>(CaveActivity.ACTIVE_SIZE)
    internal val frontierLife = FrontierLife(savedState?.frontierLife ?: "{}")
    internal val residents by lazy { com.Atom2Universe.app.games.caves.entity.FrontierResidents(world,frontierLife.residents) }
    var travelCallback: (() -> Unit)? = null
    var tradeCallback: ((TradeView) -> Unit)? = null
    var checkpointCallback: (() -> Unit)? = null
    private var checkpointTimer=0f
    internal val expeditionCombat by lazy { ExpeditionCombat(this,context,frontierLife.equipment) }
    private val fishing by lazy { FishingLine(this,context,frontierLife.fisheries) }
    @Volatile internal var nearbyStations: Set<Short> = emptySet()
        private set
    internal fun refreshCraftStations() {
        val found=mutableSetOf<Short>()
        val px=floor(camera.playerX).toInt();val py=floor(camera.playerY-1).toInt();val pz=floor(camera.playerZ).toInt()
        for(dx in -4..4) for(dy in -3..3) for(dz in -4..4) {
            if(dx*dx+dy*dy+dz*dz>16) continue
            val id=world.blockAt(px+dx,py+dy,pz+dz)
            if(id==E.ANVIL && clearCombatLine(camera.playerX,camera.eyeY,camera.playerZ,px+dx+.5,py+dy+1.05,pz+dz+.5)) found+=id
        }
        nearbyStations=found
    }
    internal fun clearCombatLine(ax: Double,ay: Double,az: Double,bx: Double,by: Double,bz: Double): Boolean {
        val dx=bx-ax;val dy=by-ay;val dz=bz-az
        val steps=ceil(sqrt(dx*dx+dy*dy+dz*dz)*10).toInt().coerceAtLeast(1)
        for(i in 1 until steps) {
            val t=i.toDouble()/steps
            if(projectileSolid(ax+dx*t,ay+dy*t,az+dz*t)) return false
        }
        return true
    }
    internal fun fireExplorationArrow(e: com.Atom2Universe.app.games.caves.entity.Enemy,tx: Double,ty: Double,tz: Double) {
        val sx=e.x;val sy=e.y+e.def.eyeHeight;val sz=e.z
        val distance=hypot(tx-sx,tz-sz)
        val dx=tx-sx;val dy=ty-sy+ProjectileKind.ARROW.gravity*.5*(distance/19.0).pow(2);val dz=tz-sz
        val len=sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(.01)
        projectiles.add(Projectile(sx,sy,sz,dx/len,dy/len,dz/len,19f,e.scaledDamage+1,ammoWeaponDef,
            fromEnemy=true,kind=ProjectileKind.ARROW,maxRange=24f))
    }
    internal fun meleeVisual(type: String) { equipmentRelease=0f;releasedEquipment=type }
    private fun magazine(id: Short, profile: RangedProfile): MagazineState = magazines.getOrPut(id) {
        MagazineState(profile.magazine,profile.reload).also { m ->
            if(mode.allowsWorldEdits) runCatching { org.json.JSONObject(frontierLife.magazines).optJSONObject(id.toString())?.let { m.restore(it) } }
        }
    }
    internal fun frontierSnapshot(): String {
        frontierLife.residents=residents.snapshot()
        frontierLife.equipment=expeditionCombat.snapshot();frontierLife.fisheries=fishing.snapshot()
        val saved=runCatching { org.json.JSONObject(frontierLife.magazines) }.getOrElse { org.json.JSONObject() }
        magazines.forEach { (id,m) -> saved.put(id.toString(),m.snapshot()) }
        saved.keys().asSequence().toList().forEach { key ->
            if(com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(key.toShortOrNull() ?: 0)==null) saved.remove(key)
        }
        frontierLife.magazines=saved.toString()
        return frontierLife.snapshot()
    }
    internal val workshops by lazy { FrontierWorkshops(world, worldSeed).also { it.restore(savedState?.workshops ?: "{}") } }
    var craftStationCallback: (() -> Unit)? = null
    var storageCallback: ((FrontierWorkshops.View, Map<Short, Int>) -> Unit)? = null
    val worldTimeSnapshot: Long get() = gameTimeMs
    internal fun checkpointCommitted(changes: Map<String,CaveCheckpoint.Edit>) { storage?.acknowledge(changes) }
    internal fun chunkSnapshot() = storage?.snapshot().orEmpty()
    internal val farming by lazy { com.Atom2Universe.app.games.caves.world.Farming(world, ::forceMeshRebuild, ::ecologicalLight) }
    internal fun itemMode(id: Short) = if (com.Atom2Universe.app.games.caves.node.FarmItems.isItem(id)) HotbarMode.GARDEN
        else if (isCombatItem(id)) HotbarMode.COMBAT else HotbarMode.BUILD
    // Context follows the selected item; there are no separate shortcut banks.
    val heldItemMode: HotbarMode get() = hotbar.getOrNull(selectedSlot)?.let(::itemMode) ?: HotbarMode.BUILD
    var selectedSlot = 0
        private set

    /** Combat = armes équipées, munitions (cailloux/flèches/carreaux/balles) et pierres de garde. */
    internal fun isCombatItem(id: Short): Boolean = E.isEquipment(id) ||
        com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(id) ||
        id in ROCK_IDS || id == ARROW_ID || id == BOLT_ID || id == BULLET_ID ||
        id == com.Atom2Universe.app.games.caves.world.WARD_STONE

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
    internal val passiveAnimals = com.Atom2Universe.app.games.caves.entity.PassiveAnimals(world, worldSeed).apply { restore(savedState?.passiveAnimals ?: "[]") }
    private val projRenderer       = ProjectileRenderer()

    // ── Règles de la partie ───────────────────────────────────────────────────
    // Ce qui est propre au mode (monstres, XP, butin…) vit dans le mode, pas ici.
    // Les modes ne lisent le renderer qu'une fois la surface GL créée.
    internal val mode: GameMode = modeFactory(this)

    // ── Progression joueur ────────────────────────────────────────────────────

    val skillBook   = com.Atom2Universe.app.games.caves.entity.SkillBook()
    val playerStats = PlayerStats()
    val projectiles = ArrayList<Projectile>(64)
    @Volatile var recoverableAmmoSnapshot: List<StuckAmmo> = emptyList()
        private set
    private val impactParticles = ArrayList<ImpactParticle>(128)

    private val ROCK_IDS = setOf(2020.toShort(), 2021.toShort())
    private var rockChargeTime = 0f
    private val ROCK_CHARGE_MAX = 1.5f
    private val RANGED_WEAPON_TYPES = RangedProfile.all.keys
    private val magazines = mutableMapOf<Short, MagazineState>()
    private var fireWasDown = false
    private var lastFireWeapon: Short? = null
    private var lastWeaponStatus = ""
    var weaponStatusCallback: ((String) -> Unit)? = null
    private var weaponChargeTime = 0f   // temps de visée/tension avant de relâcher pour tirer
    private val WEAPON_CHARGE_VISUAL_MAX = 0.6f   // durée pour atteindre la tension visuelle max
    private val ARROW_ID: Short = 8010
    private val BOLT_ID: Short = 8011
    private val BULLET_ID: Short = 8012
    private val RANGED_PROJECTILE_SPEED = 24f
    private val PLAYER_KNOCKBACK = 6.0   // vitesse initiale du recul quand le joueur est touché

    @Volatile private var requestedPause = false
    @Volatile var spawnReady = false
        private set
    @Volatile var loadingCallback: ((Boolean) -> Unit)? = null
    private val spawnChunks = HashSet<Long>()
    private var caveBlend = 0f
    private var caveFogUniform = -1
    private var waterCaveFogUniform = -1
    var gamePaused: Boolean
        get() = requestedPause || !spawnReady
        set(value) { requestedPause = value }

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
    var fishingGaugeCallback: ((FishingLine.Gauge)->Unit)? = null
    var farmMessageCallback: ((String) -> Unit)? = null
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)?       = null
    var shieldCallback:   ((current: Int, max: Int) -> Unit)?    = null
    var swingCallback:    (() -> Unit)?                           = null
    var sprintCallback:       ((Boolean) -> Unit)?                = null
    var crouchCallback:       ((Boolean) -> Unit)?                = null
    var jumpChargeCallback:   ((Float) -> Unit)?                  = null
    var playerHitCallback:    (() -> Unit)?                       = null
    /** L'inventaire et les barres ont été remplacés d'un bloc (kit d'armes) : l'UI doit tout relire. */
    var loadoutChangedCallback: (() -> Unit)?                     = null
    private var weaponAttackCooldown = 0f

    // ── Shaders ───────────────────────────────────────────────────────────────

    private val VERT_WORLD = """
        #version 300 es
        in vec3 a_pos;
        in vec3 a_uv;
        in float a_skyLight;
        in vec4 a_tint;
        uniform mat4 u_mvp;
        uniform vec3 u_chunk_offset;
        out vec2 v_uv;
        out float v_layer;
        out float v_faceDir;
        out vec3 v_worldPos;
        out float v_skyLight;
        out vec4 v_tint;
        void main() {
            vec3 worldPos = a_pos + u_chunk_offset;
            gl_Position = u_mvp * vec4(worldPos, 1.0);
            v_uv      = a_uv.xy;
            v_layer   = mod(a_uv.z, 4096.0);
            v_faceDir = floor(a_uv.z / 4096.0);
            v_worldPos = worldPos;
            v_skyLight = a_skyLight;
            v_tint = a_tint;
        }
    """.trimIndent()

    private val FRAG_WORLD = """
        #version 300 es
        precision highp float;
        uniform sampler2DArray u_tex;
        uniform float u_ambient;
        uniform float u_caveFloor;
        uniform vec4 u_lights[32];
        uniform vec4 u_lightColors[32];
        uniform int u_lightCount;
        uniform float u_time;
        uniform float u_underwater;
        uniform vec3 u_caveFog;
        in vec2 v_uv;
        in float v_layer;
        in float v_faceDir;
        in vec3 v_worldPos;
        in float v_skyLight;
        in vec4 v_tint;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(u_tex, vec3(v_uv, v_layer));
            if (col.a < 0.5) discard;
            float mask = v_tint.w > 0.5 ? 1.0 : 0.0;
            if (v_tint.w > 1.5) {
                // Same integer fringe as MeadowTextures.capDepth; soil is never recolored.
                vec2 pixel = clamp(floor(v_uv * 32.0), vec2(0.0), vec2(31.0));
                int i = int(min(pixel.x, 31.0 - pixel.x)) / 2;
                float depth = i >= 6 ? 10.0 : (i == 2 || i == 3 || i == 5) ? 8.0 : 6.0;
                mask = pixel.y <= depth ? 1.0 : 0.0;
            }
            col.rgb = clamp(col.rgb * (vec3(1.0) + v_tint.rgb * mask), 0.0, 1.0);
            float fd = floor(v_faceDir + 0.5);
            // Voxel face axes are known: screen derivatives become tiny on nearby faces,
            // causing unstable normals (and dark patches) at mobile mediump precision.
            vec3 normal = fd < 1.5 ? vec3(0.0, 1.0, 0.0)
                : fd < 3.5 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0);
            float faceLight = fd < 0.5 ? 1.0 : fd < 1.5 ? 0.45 : fd < 3.5 ? 0.72 : 0.62;
            vec3 torchContrib = vec3(0.0);
            for (int i = 0; i < u_lightCount; i++) {
                float flicker = u_lightColors[i].w;
                float radius = 16.0 * u_lights[i].w;
                vec3 delta = v_worldPos - u_lights[i].xyz;
                float distanceSquared = dot(delta, delta);
                if (distanceSquared >= radius * radius) continue;
                float d = sqrt(distanceSquared);
                float atten = clamp(1.0 - d / radius, 0.0, 1.0);
                atten = atten * atten;
                vec3 toLight = (u_lights[i].xyz - v_worldPos) / max(d, 0.001);
                float diffuse = 0.35 + 0.65 * abs(dot(normal, toLight));
                torchContrib += atten * diffuse * u_lights[i].w * flicker * u_lightColors[i].rgb;
            }
            // Lumière du ciel cuite (0..1) × ambiance jour/nuit ; plancher pénombre pour ne jamais
            // être 100 % noir ; les torches s'ajoutent par-dessus dans les zones non exposées.
            float sky = v_skyLight * u_ambient;
            vec3 baseLight = vec3(max(sky, u_caveFloor));
            vec3 lighting = baseLight + (vec3(1.0) - baseLight) * (vec3(1.0) - exp(-torchContrib * 1.8));
            float glow = fd > 6.5 ? 1.0 : 0.94 + 0.06 * sin(u_time * 9.0);
            // Directional face shading belongs to skylight; a torch can illuminate a ceiling.
            vec3 lit = baseLight * faceLight + (lighting - baseLight);
            fragColor = vec4(fd > 5.5 ? col.rgb * glow : col.rgb * lit, 1.0);
            if (u_underwater > 0.5) {
                fragColor = vec4(fragColor.rgb * vec3(0.18, 0.48, 0.88) * 0.55, 1.0);
            }
            fragColor.rgb *= 1.0 - u_caveFog.x * smoothstep(u_caveFog.y, u_caveFog.z, length(v_worldPos));
        }
    """.trimIndent()

    private val FRAG_WATER = """
        #version 300 es
        precision highp float;
        uniform vec3 u_wavePhase;
        uniform vec3 u_waterTint;
        uniform vec3 u_skyColor;
        uniform vec3 u_caveFog;
        uniform float u_ambient;
        uniform float u_caveFloor;
        uniform vec4 u_lights[32];
        uniform vec4 u_lightColors[32];
        uniform int u_lightCount;
        uniform float u_time;
        in vec2 v_uv;
        in float v_layer;
        in float v_faceDir;
        in vec3 v_worldPos;
        in float v_skyLight;
        out vec4 fragColor;
        void main() {
            // Phases ancrées au monde, calculées en double côté CPU, sans grandes
            // coordonnées dans le shader. Deux rides, aucune texture supplémentaire.
            float a = v_worldPos.x * 1.1 + u_wavePhase.x;
            float b = v_worldPos.z * 0.85 + u_wavePhase.y;
            vec3 normal = normalize(cross(dFdx(v_worldPos), dFdy(v_worldPos)));
            float top = smoothstep(0.55, 0.95, abs(normal.y));
            float ripple = sin(a) * 0.55 + sin(b) * 0.30 + sin(a+b) * 0.15;
            float fall = sin(v_worldPos.y * 5.0 + u_wavePhase.z + sin(a));
            float wave = 0.5 + 0.5 * mix(fall, ripple, top);
            vec3 viewDir = normalize(-v_worldPos);
            if (dot(normal, viewDir) < 0.0) normal = -normal;
            float fade = 1.0 - smoothstep(24.0, 80.0, length(v_worldPos));
            normal = normalize(normal + vec3(cos(a) * 0.55 + cos(a+b) * 0.15, 0.0,
                cos(b) * 0.30 + cos(a+b) * 0.15) * (0.12 * top * fade));
            float grazing = 1.0 - clamp(dot(normal, viewDir), 0.0, 1.0);
            float fresnel = 0.04 + 0.96 * grazing * grazing * grazing * grazing * grazing;
            vec3 deepColor = u_waterTint * vec3(0.35, 0.62, 0.75);
            vec3 shallowColor = u_waterTint + vec3(0.07, 0.16, 0.12);
            vec3 baseColor = mix(deepColor, shallowColor, wave * 0.22 + 0.28);
            float faceLight = mix(0.78, 1.0, top);
            vec3 torchContrib = vec3(0.0);
            for (int i = 0; i < u_lightCount; i++) {
                float flicker = u_lightColors[i].w;
                float radius = 16.0 * u_lights[i].w;
                vec3 delta = v_worldPos - u_lights[i].xyz;
                float distanceSquared = dot(delta, delta);
                if (distanceSquared >= radius * radius) continue;
                float d = sqrt(distanceSquared);
                float atten = clamp(1.0 - d / radius, 0.0, 1.0);
                atten = atten * atten;
                torchContrib += atten * u_lights[i].w * flicker * u_lightColors[i].rgb;
            }
            float sky = v_skyLight * u_ambient;
            vec3 baseLight = vec3(max(sky, u_caveFloor));
            vec3 lighting = baseLight + (vec3(1.0) - baseLight) * (vec3(1.0) - exp(-torchContrib * 1.8));
            vec3 reflection = mix(u_skyColor, vec3(0.72,0.80,0.81) * u_ambient, 0.22) * v_skyLight;
            vec3 color = mix(baseColor * faceLight * lighting, reflection, fresnel * 0.78);
            float glint = pow(max(dot(normal, normalize(viewDir + vec3(0.35, 0.85, 0.4))), 0.0), 48.0);
            color += vec3(0.90, 0.90, 0.76) * glint * sky * top * fade * 0.24;
            // Thin descending streaks give waterfalls a readable motion without opaque blue walls.
            float streak = pow(max(0.0, fall), 12.0) * (1.0-top) * fade;
            color += vec3(0.30,0.36,0.33) * streak * lighting * 0.22;
            fragColor = vec4(color, mix(0.30 + 0.10 * (1.0-top), 0.82, fresnel));
            fragColor.rgb *= 1.0 - u_caveFog.x * smoothstep(u_caveFog.y, u_caveFog.z, length(v_worldPos));
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
        uniform float u_alpha;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(v_color, u_alpha);
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
        ShaderProgram.grayscale.set(grayscaleStyle)
        val startupNs = System.nanoTime()
        if (com.Atom2Universe.app.BuildConfig.DEBUG) android.util.Log.i("CavePerf", "startupBegin")
        val liveFarmInventory = if (farmSessionReady) inventory.filterKeys {
            com.Atom2Universe.app.games.caves.node.FarmItems.isItem(it)
        } else null
        GLES30.glClearColor(0.682f, 0.910f, 0.973f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        lastFrameNs = System.nanoTime()

        worldShader = ShaderProgram(VERT_WORLD, FRAG_WORLD).also {
            it.use()
            caveFogUniform = it.uniform("u_caveFog")
            wAPos         = it.attrib("a_pos")
            wAUv          = it.attrib("a_uv")
            wASky         = it.attrib("a_skyLight")
            wATint        = it.attrib("a_tint")
            wUMvp         = it.uniform("u_mvp")
            wUTex         = it.uniform("u_tex")
            wUChunkOffset = it.uniform("u_chunk_offset")
            wUAmbient     = it.uniform("u_ambient")
            wUCaveFloor   = it.uniform("u_caveFloor")
            wULightColors = it.uniform("u_lightColors[0]")
            wULights      = it.uniform("u_lights[0]")
            wULightCount  = it.uniform("u_lightCount")
            wUTime        = it.uniform("u_time")
            wUUnderwater  = it.uniform("u_underwater")
        }
        waterShader = ShaderProgram(VERT_WORLD, FRAG_WATER).also {
            it.use()
            waterPhaseUniform = it.uniform("u_wavePhase")
            waterTintUniform = it.uniform("u_waterTint")
            waterSkyUniform = it.uniform("u_skyColor")
            waterCaveFogUniform = it.uniform("u_caveFog")
            wWAPos         = it.attrib("a_pos")
            wWAUv          = it.attrib("a_uv")
            wWASky         = it.attrib("a_skyLight")
            wWUMvp         = it.uniform("u_mvp")
            wWUChunkOffset = it.uniform("u_chunk_offset")
            wWUAmbient     = it.uniform("u_ambient")
            wWUCaveFloor   = it.uniform("u_caveFloor")
            wWULightColors = it.uniform("u_lightColors[0]")
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
            lUAlpha = it.uniform("u_alpha")
            GLES30.glUniform1f(lUAlpha, 1f)
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
        decorRenderer?.onSurfaceCreated()
        projRenderer.onSurfaceCreated(context.assets)

        playerNode.onHpChanged     = { hp, max -> playerHpCallback?.invoke(hp, max) }
        playerNode.onShieldChanged = { cur, max -> shieldCallback?.invoke(cur, max) }
        eventBus.subscribe { event ->
            if (event is GameEvent.PlayerHit) {
                // Recul du joueur (dans le sens attaquant → joueur) + flash rouge écran.
                physics.applyKnockback(event.dirX * PLAYER_KNOCKBACK, event.dirZ * PLAYER_KNOCKBACK)
                playerHitCallback?.invoke()
            }
        }

        // Règles propres au mode (survie : XP, butin, monstres…) et restauration de sa progression.
        mode.onSurfaceCreated(savedState)

        val ids = IntArray(3)
        GLES30.glGenBuffers(3, ids, 0)
        transientVbo = ids[0]
        playerBoxVbo = ids[1]
        vmVbo        = ids[2]

        if (farmSessionReady && mode.allowsWorldEdits) {
            warmSpawnNeighborhood()
        } else if (savedState != null) {
            camera.playerX = savedState.x; camera.playerY = savedState.y; camera.playerZ = savedState.z
            camera.x = savedState.x; camera.y = savedState.y; camera.z = savedState.z
            camera.yaw = savedState.yaw; camera.pitch = savedState.pitch
            inventory.putAll(savedState.inventory)
            hotbar.fill(null)
            savedState.hotbar.take(hotbar.size).forEachIndexed { i,id ->
                hotbar[i]=id?.takeIf { (inventory[it] ?: 0)>0 }
            }
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            inventoryCallback?.invoke(inventory.toMap())
            recoverableAmmoSnapshot=savedState.recoverableAmmo
            for(a in savedState.recoverableAmmo) {
                projectiles.add(Projectile(a.x,a.y,a.z,a.vx,a.vy,a.vz,1f,0,
                    WeaponDef(WeaponColor.WHITE,WeaponVariant.SQUARE),kind=if(a.ammoId==ARROW_ID) ProjectileKind.ARROW else ProjectileKind.BOLT,
                    ammoId=a.ammoId).also { it.stuck=true;it.age=1f })
            }
            warmSpawnNeighborhood()
            val spawn = world.findSpawnPoint()
            mode.onPlayerPlaced(spawn[0].toDouble(), spawn[1].toDouble(), spawn[2].toDouble())
        } else {
            // Nouvelle partie : démarrer à 10h du matin IG (portion jour, 100 000 ms/heure → 4h après 6h).
            gameTimeMs = mode.fixedTimeOfDayMs ?: NEW_GAME_START_MS
            // Une carte préparée connaît ses points d'apparition ; sinon le monde en cherche un
            // (et peut construire une île : à ne jamais faire sur une carte).
            val spawn = mode.spawnPoint() ?: world.findSpawnPoint()
            camera.playerX = spawn[0].toDouble(); camera.playerY = spawn[1].toDouble(); camera.playerZ = spawn[2].toDouble()
            camera.x = camera.playerX; camera.y = camera.playerY; camera.z = camera.playerZ
            warmSpawnNeighborhood()
            mode.onPlayerPlaced(camera.x, camera.y, camera.z)
        }
        if (!farmSessionReady) {
            farming.restore(savedState?.farming ?: "{}")
            if (savedState != null && mode.fixedTimeOfDayMs == null) gameTimeMs = savedState.worldTimeMs
        }
        liveFarmInventory?.let { live ->
            inventory.keys.removeAll { com.Atom2Universe.app.games.caves.node.FarmItems.isItem(it) }
            inventory.putAll(live)
        }
        if (mode.allowsWorldEdits) {
            val hoe = com.Atom2Universe.app.games.caves.node.FarmSoil.HOE
            inventory[hoe] = 1
            if (farming.initialize()) {
                // Starter shortcuts are offered once; cleared slots stay cleared on reload.
                if(hoe !in hotbar) hotbar.indexOfFirst { it==null }.takeIf { it>=0 }?.let { hotbar[it]=hoe }
                for (crop in listOf(0,4,5)) {
                    val seed=com.Atom2Universe.app.games.caves.node.FarmItems.seed(crop)
                    inventory[seed]=(inventory[seed] ?: 0)+3
                }
            }
            inventoryCallback?.invoke(inventory.toMap())
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
        }
        farmSessionReady = true
        lastFrameNs = System.nanoTime()
        if (com.Atom2Universe.app.BuildConfig.DEBUG)
            android.util.Log.i("CavePerf", "startupReady ms=${(lastFrameNs - startupNs) / 1_000_000}")
        scheduleInitialLodBuilds()
    }

    private fun warmSpawnNeighborhood() {
        val pcx = camera.chunkX(); val pcz = camera.chunkZ()
        spawnReady = false
        loadingCallback?.invoke(true)
        spawnChunks.clear()
        val bounds = worldSource?.chunkBounds()
        for (dy in -1..1) for (dz in -1..1) for (dx in -1..1) {
            val cx = pcx + dx
            val cy = camera.chunkY() + dy
            val cz = pcz + dz
            // Finite maps never stream chunks outside these bounds. Waiting for them
            // would leave spawns near an edge or the map's top permanently loading.
            if (bounds != null && (cx !in bounds.minCx..bounds.maxCx ||
                    cy !in bounds.minCy..bounds.maxCy || cz !in bounds.minCz..bounds.maxCz)) continue
            if (worldSource == null && world.terrainVersion < 3 && cy > SURFACE_CY_MAX && cy < 625) continue
            spawnChunks.add(world.chunkKey(cx, cy, cz))
        }
        // Keep a full horizontal safety ring, but only the vertical chunks supporting
        // the player's feet/body. The other layers stream asynchronously near-first.
        val bottom = Math.floorDiv(floorInt(camera.playerY - 2.75), CHUNK_SIZE)
        val top = Math.floorDiv(floorInt(camera.playerY + .25), CHUNK_SIZE)
        for (cy in bottom..top) for (dz in -1..1) for (dx in -1..1)
            world.pregenerateChunk(pcx + dx, cy, pcz + dz)
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

    private fun loadBlockTextures(): Int {
        com.Atom2Universe.app.games.caves.node.MeadowTextures.itemTextureNames.forEach { name ->
            BlockRegistry.registerGeneratedTexture(name) { size ->
                com.Atom2Universe.app.games.caves.node.MeadowTextures.texture(name, size, vivid = vividStyle)
            }
        }


        // Preserve the native crop silhouettes in every playable world.
        val tileSize = 96
        val bitmaps = BlockRegistry.buildTextureAtlas(context.assets, tileSize, vivid = vividStyle)
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
        val gameH = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, width, gameH)
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
        if (spawnReady) {
            camera.yaw -= dy
            camera.pitch += dp
        }

        if (!gamePaused) {
            when (playerMode) {
                PlayerMode.SPECTATOR -> updateSpectator(dt)
                PlayerMode.WALK      -> updateWalk(dt)
            }
        }
        if (gamePaused || playerMode != PlayerMode.WALK) eventBus.publish(GameEvent.Footstep())
        camera.eyeDrop = if (playerMode == PlayerMode.WALK) physics.eyeDrop else 0.0
        camera.update()
        adjustTpsCamera()

        elapsed += dt
        // Heure figée par le mode (Assaut : midi) ; sinon le jour et la nuit tournent.
        if (!gamePaused && mode.allowsWorldEdits && rawDt < 1f) {
            frontierLife.advance((rawDt*1000f).toLong())
            farming.advance((rawDt * 1000f).toLong())
            workshops.advance(rawDt)
            workshops.feedAnimals(passiveAnimals,rawDt)
            checkpointTimer+=rawDt
            if(checkpointTimer>=10f) { checkpointTimer=0f;checkpointCallback?.invoke() }
        }
        if (!gamePaused && mode.fixedTimeOfDayMs == null) gameTimeMs += (dt * 1_000f).toLong()

        if(!gamePaused) waterTickAccum += dt
        if (!gamePaused && waterTickAccum >= 0.25f) {
            waterTickAccum = 0f
            world.tickWater()
            if (!gamePaused && mode.allowsWorldEdits) world.tickLeaves { if (!isCreative) collectBlock(it) }
        }

        if(!gamePaused) gravityTickAccum += dt
        if (!gamePaused && gravityTickAccum >= 0.1f) {
            gravityTickAccum = 0f
            world.tickFalling(64)
        }

        if (!gamePaused) {
            if (weaponAttackCooldown > 0f) weaponAttackCooldown = (weaponAttackCooldown - dt).coerceAtLeast(0f)
            updateRockThrow(dt)
            updateMining(dt)
            updateProjectiles(dt)
            publishWeaponStatus()
            updateImpactParticles(dt)
        }

        val cx = camera.chunkX(); val cy = camera.chunkY(); val cz = camera.chunkZ()
        val movedChunk = cx != lastCx || cy != lastCy || cz != lastCz
        streamTickAccum += dt
        if (movedChunk || (streamNeedsMore && streamTickAccum >= 0.08f)) {
            // Workers must not accumulate hundreds of jobs/vertex arrays behind the renderer.
            val batchSize = if (waitingFirstMeshes >= 64 || pendingMeshBytes.get() >= MAX_PENDING_MESH_BYTES) 0
                else (8 - generationJobs.get()).coerceAtLeast(0)
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

        // Drain and deduplicate new requests; waiting work remains in the GL-owned set.
        // Only select work when a worker and upload budget are actually available.
        val pendingSet = pendingMeshKeys
        while (true) { pendingSet.add(world.rebuildQueue.poll() ?: break) }
        pendingSet.removeAll { world.getChunkByKey(it) == null }
        waitingMeshChunks = pendingSet.size
        waitingFirstMeshes = pendingSet.count { key ->
            !meshes.containsKey(key) && world.getChunkByKey(key)?.generated == true
        }
        var rebuilt = 0
        val dispatchedSolids = HashSet<Long>(2)
        while (rebuilt < 2 && meshJobs.get() < 2 && pendingMeshBytes.get() < MAX_PENDING_MESH_BYTES) {
            // Alternate first geometry and refreshes even when only one worker is free.
            // A continuous stream of new chunks must not starve existing dark meshes.
            val preferFirst = meshDispatchSerial % 2L == 0L
            var bestKey: Long? = null
            var bestClass = Int.MAX_VALUE
            var bestDistance = Int.MAX_VALUE
            for (candidate in pendingSet) {
                if (candidate in building || world.getChunkByKey(candidate)?.generated != true) continue
                val priority = if ((!meshes.containsKey(candidate)) == preferFirst) 0 else 1
                val dx = world.keyToCx(candidate) - cx
                val dy = world.keyToCy(candidate) - cy
                val dz = world.keyToCz(candidate) - cz
                val distance = dx * dx + dy * dy + dz * dz
                if (priority < bestClass || (priority == bestClass && distance < bestDistance)) {
                    bestKey = candidate
                    bestClass = priority
                    bestDistance = distance
                }
            }
            val key = bestKey ?: break
            val chunk = world.getChunkByKey(key) ?: continue
            if (!building.add(key)) continue
            pendingSet.remove(key)
            dispatchedSolids.add(key)
            meshDispatchSerial++
            chunk.meshDirty = false
            chunk.waterMeshDirty = false
            val snapVersion  = chunk.version
            val snapWaterVer = chunk.waterVersion
            meshJobs.incrementAndGet()
            scope.launch(meshDispatcher) {
                try {
                    val lighting = MeshLightingSnapshot(chunk, world)
                    val verts      = MeshBuilder.build(chunk, world)
                    val waterVerts = MeshBuilder.buildWater(chunk, world)
                    if (chunk.version == snapVersion) {
                        enqueueMesh(uploadQueue, LitMeshUpload(key, snapVersion, verts, lighting))
                        if (chunk.waterVersion == snapWaterVer)
                            enqueueMesh(waterOnlyUploadQueue, LitMeshUpload(key, snapWaterVer, waterVerts, lighting))
                    }
                    if (chunk.meshDirty || chunk.version != snapVersion) world.rebuildQueue.add(key)
                    if (chunk.waterMeshDirty || chunk.waterVersion != snapWaterVer) world.waterRebuildQueue.add(key)
                } catch (_: OutOfMemoryError) {
                    // Heap plein : re-queue pour réessayer quand la pression mémoire baisse
                    chunk.meshDirty = true
                    world.rebuildQueue.add(key)
                } finally {
                    meshJobs.decrementAndGet()
                    building.remove(key)
                }
            }
            rebuilt++
        }

        // Rebuilds eau seuls : ne reconstruit que buildWater(), évite build() solide inutile
        val pendingWaterSet = HashSet<Long>()
        while (true) { pendingWaterSet.add(world.waterRebuildQueue.poll() ?: break) }
        pendingWaterSet -= pendingSet  // les chunks déjà en rebuild solide reconstruisent l'eau aussi
        pendingWaterSet -= dispatchedSolids
        val waterBatch = pendingWaterSet.sortedBy { key ->
            val dx = world.keyToCx(key) - cx; val dy = world.keyToCy(key) - cy; val dz = world.keyToCz(key) - cz
            dx * dx + dz * dz + dy * dy * 8
        }
        var waterRebuilt = 0
        for (key in waterBatch) {
            if (waterRebuilt >= 2 || meshJobs.get() >= 2 || pendingMeshBytes.get() >= MAX_PENDING_MESH_BYTES) {
                world.waterRebuildQueue.add(key); continue
            }
            val chunk = world.getChunkByKey(key) ?: continue
            if (!chunk.generated) { world.waterRebuildQueue.add(key); continue }
            if (!building.add(key)) { world.waterRebuildQueue.add(key); continue }
            chunk.waterMeshDirty = false
            val snapWaterVer = chunk.waterVersion
            meshJobs.incrementAndGet()
            scope.launch(meshDispatcher) {
                try {
                    val lighting = MeshLightingSnapshot(chunk, world)
                    val waterVerts = MeshBuilder.buildWater(chunk, world)
                    if (chunk.waterVersion == snapWaterVer) {
                        enqueueMesh(waterOnlyUploadQueue, LitMeshUpload(key, snapWaterVer, waterVerts, lighting))
                    }
                    if (chunk.waterMeshDirty || chunk.waterVersion != snapWaterVer) world.waterRebuildQueue.add(key)
                } finally {
                    meshJobs.decrementAndGet()
                    building.remove(key)
                }
            }
            waterRebuilt++
        }

        // Budget de temps partagé plutôt qu'un compte fixe par catégorie (16/16/16/8) :
        // au premier chargement, des dizaines de chunks finissent leur maillage en même
        // temps et un compte fixe pouvait forcer 56 glBufferData() dans une seule image
        // (jusqu'à ~1s de gel mesuré). Le flush GPU se fait ici, item par item, pour que
        // le temps écoulé reflète le vrai coût — le surplus attend simplement l'image
        // suivante, rien n'est perdu, juste étalé.
        val uploadDeadline = System.nanoTime() + UPLOAD_BUDGET_NS
        while (System.nanoTime() < uploadDeadline) {
            val (key, ver, verts, lighting) = pollMesh(uploadQueue) ?: break
            val chunk = world.getChunkByKey(key) ?: continue
            if (lighting.belongsTo(chunk) && chunk.version == ver) {
                val needsLightRefresh = !lighting.isCurrent()
                if (!meshes.containsKey(key)) firstMeshUploads++ else refreshMeshUploads++
                val mesh = meshes.getOrPut(key) { ChunkMesh(11) }
                mesh.upload(verts); mesh.flushPending()
                // Show valid geometry now. A changing neighbour light must never leave a hole.
                if (needsLightRefresh) {
                    provisionalUploads++
                    world.rebuildQueue.add(key)
                }
                refreshChunkLightSources(chunk)
                if (chunk.cy in 0..world.surfaceChunkMax) {
                    val previous = lodGeometryVersions[key]
                    if (previous?.first !== chunk || previous.second != ver) {
                        lodGeometryVersions[key] = chunk to ver
                        scheduleLodBuild(chunk.cx, chunk.cz)
                    }
                }
            }
        }
        while (System.nanoTime() < uploadDeadline) {
            val (key, verts) = lodUploadQueue.poll() ?: break
            lodUploadQueueSize.decrementAndGet()
            var isNew = false
            val mesh = lodMeshes.getOrPut(key) { isNew = true; ChunkMesh(6) }
            mesh.upload(verts); mesh.flushPending()
            if (isNew) lodGrid.getOrPut(superKey(lodKeyToCx(key), lodKeyToCz(key))) { ArrayList() }.add(key)
        }
        while (System.nanoTime() < uploadDeadline) {
            val (key, ver, verts, lighting) = pollMesh(waterOnlyUploadQueue) ?: break
            val chunk = world.getChunkByKey(key) ?: continue
            if (lighting.belongsTo(chunk) && chunk.waterVersion == ver) {
                if (verts.isNotEmpty()) { val mesh = waterMeshes.getOrPut(key) { ChunkMesh(7) }; mesh.upload(verts); mesh.flushPending() }
                else waterMeshes.remove(key)?.destroy()
                if (!lighting.isCurrent()) world.waterRebuildQueue.add(key)
            }
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0
            lodCache?.saveIfDirty(scope)
            val pcx = camera.chunkX(); val pcy = camera.chunkY(); val pcz = camera.chunkZ()
            val allChunks = world.allChunks()
            val loadedKeys = allChunks.mapTo(HashSet()) { world.chunkKey(it.cx, it.cy, it.cz) }
            // Supprimer les meshes de chunks déchargés
            lodGeometryVersions.keys.retainAll(loadedKeys)
            meshes.keys.filter { it !in loadedKeys }.forEach { key -> meshes.remove(key)?.destroy() }
            waterMeshes.keys.filter { it !in loadedKeys }.forEach { key -> waterMeshes.remove(key)?.destroy() }
            // Nettoyer les sources de lumière des chunks déchargés
            if (chunkLightStates.isNotEmpty()) {
                val iterator = chunkLightStates.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key !in loadedKeys) {
                        for (position in entry.value.positions) lightSources.remove(position)
                        localLights.clear()
                        iterator.remove()
                        lightsDirty = true
                    }
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

        if (!spawnReady && spawnChunks.all { meshes.containsKey(it) }) {
            spawnReady = true
            loadingCallback?.invoke(false)
        }

        // Reserve the dark background/fog for depths below sea level. A roof alone
        // must not darken an outdoor view from a cave in a mountainside.
        // Fade over one chunk in altitude, in addition to the local roof-depth test.
        val roofDepth = world.surfaceTopY(floorInt(camera.x), floorInt(camera.z)) - camera.y
        val altitudeBlend = ((NaturalTerrain.SEA_LEVEL - camera.y) / CHUNK_SIZE)
            .toFloat().coerceIn(0f, 1f)
        val targetCave = ((roofDepth - 3.0) / 12.0).toFloat().coerceIn(0f, 1f) * altitudeBlend
        caveBlend += (targetCave - caveBlend) * (dt * 4f).coerceAtMost(1f)
        caveBlend = caveBlend.coerceAtMost(altitudeBlend)
        // ── Cycle jour/nuit ───────────────────────────────────────────────────
        val dayT = dayFraction()
        val (skyR, skyG, skyB) = skyColorFor(dayT)
        val skyGray = skyR * .2126f + skyG * .7152f + skyB * .0722f
        GLES30.glClearColor(
            (if (grayscaleStyle) skyGray else skyR) * (1f - caveBlend),
            (if (grayscaleStyle) skyGray else skyG) * (1f - caveBlend),
            (if (grayscaleStyle) skyGray else skyB) * (1f - caveBlend), 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // ── Corps célestes (avant le terrain — depth mask off pour laisser le terrain gagner) ──
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        renderSkyBody(sunTex,  sunAngleFor(dayT),  12f, sunAlphaFor(dayT) * (1f - caveBlend),  false)
        renderSkyBody(moonTex, moonAngleFor(dayT),  8f, moonAlphaFor(dayT) * (1f - caveBlend), true)
        renderStars(starsAlphaFor(dayT) * (1f - caveBlend))
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)

        // ── Rendu chunks ─────────────────────────────────────────────────────
        val headUnderwater = isHeadInWater()
        worldShader?.use()
        val caveFogEnd = (minOf(world.renderRadiusCave, world.renderRadiusYSurface) - 1) * CHUNK_SIZE.toFloat()
        GLES30.glUniform3f(caveFogUniform, caveBlend, caveFogEnd * 0.55f, caveFogEnd)
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
            // Top-MAX_LIGHTS par score (le plus petit = le plus prioritaire) par insertion
            // bornée : O(sources × MAX_LIGHTS) au pire, mais zéro allocation, contre trois
            // listes intermédiaires par cycle avec filter{}.sortedBy{}.take{}.
            var filled = 0
            for ((pos, intensity) in lightSources) {
                val dx = pos.first + 0.5 - camX; val dy = pos.second + 0.5 - camY; val dz = pos.third + 0.5 - camZ
                val distSq = dx*dx + dy*dy + dz*dz
                if (distSq >= 160.0 * 160.0) continue
                // Prioritize lights that can reach the nearby scene; numerous small cave
                // lights farther ahead must not displace the player's nearby torches.
                val score = distSq / (intensity * intensity).coerceAtLeast(.09f) -
                    (dx * camera.fwdX + dz * camera.fwdZ) * 4.0
                if (filled < MAX_LIGHTS) {
                    var i = filled
                    while (i > 0 && lightSelectScore[i - 1] > score) {
                        lightSelectScore[i] = lightSelectScore[i - 1]
                        lightSelectKey[i]   = lightSelectKey[i - 1]
                        lightSelectValue[i] = lightSelectValue[i - 1]
                        i--
                    }
                    lightSelectScore[i] = score; lightSelectKey[i] = pos; lightSelectValue[i] = intensity
                    filled++
                } else if (score < lightSelectScore[filled - 1]) {
                    var i = filled - 1
                    while (i > 0 && lightSelectScore[i - 1] > score) {
                        lightSelectScore[i] = lightSelectScore[i - 1]
                        lightSelectKey[i]   = lightSelectKey[i - 1]
                        lightSelectValue[i] = lightSelectValue[i - 1]
                        i--
                    }
                    lightSelectScore[i] = score; lightSelectKey[i] = pos; lightSelectValue[i] = intensity
                }
            }
            for (i in 0 until filled) {
                val pos = lightSelectKey[i]!!
                val lightBlock = world.blockAt(pos.first, pos.second, pos.third)
                val naturalLight = lightBlock != TORCH && lightBlock != LAVA
                val color = if (naturalLight) BlockRegistry.get(lightBlock)?.color ?: 0xFFFFFFFF.toInt() else 0xFFFFAD4D.toInt()
                lightColors[i*4+0] = ((color ushr 16) and 255) / 255f
                lightColors[i*4+1] = ((color ushr 8) and 255) / 255f
                lightColors[i*4+2] = (color and 255) / 255f
                // Flicker is constant across pixels: evaluate once on the CPU, not in every fragment.
                lightColors[i*4+3] = if (naturalLight) 1f else
                    1f + .025f * sin(elapsed * 7.3f + i * 2.1f) + .012f * sin(elapsed * 13.7f + i * 4.3f)
                val flame = if (lightBlock == TORCH)
                    TorchModel.flame(world.metaAt(pos.first, pos.second, pos.third)) else null
                lightData[i*4+0] = (pos.first.toDouble()  + (flame?.x ?: .5f) - camX).toFloat()
                lightData[i*4+1] = (pos.second.toDouble() + (flame?.y ?: .5f) - camY).toFloat()
                lightData[i*4+2] = (pos.third.toDouble()  + (flame?.z ?: .5f) - camZ).toFloat()
                lightData[i*4+3] = lightSelectValue[i]
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
        GLES30.glUniform4fv(wULightColors, cachedLightCount.coerceAtLeast(1), lightColors, 0)
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
            uploadChunkLights(key, offX, offY, offZ, wULights, wULightColors, wULightCount)
            mesh.draw(wAPos, wAUv, wASky, wATint)
        }
        // Keep the complete selection available to non-chunk world draws.
        GLES30.glUniform4fv(wULights, cachedLightCount.coerceAtLeast(1), lightData, 0)
        GLES30.glUniform4fv(wULightColors, cachedLightCount.coerceAtLeast(1), lightColors, 0)
        GLES30.glUniform1i(wULightCount, cachedLightCount)

        // ── Rendu LOD (couleur des blocs, visible de loin) ────────────────────
        // Shader dédié : couleur plate (BlockDef.color) × ambiance, pas de texture.
        // Masquage : une colonne ayant un mesh réel (cy quelconque) n'affiche pas son LOD,
        // évitant le double-rendu près du joueur. Le LOD reste visible pendant le gap
        // génération→upload (pas de trou).
        columnsWithMesh.clear()
        meshes.keys.forEach { k -> columnsWithMesh.add(lodKey(world.keyToCx(k), world.keyToCz(k))) }
        lodShader?.use()
        GLES30.glUniformMatrix4fv(lodUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform1f(lodUAmbient, ambientFor(dayT) * (1f - caveBlend))
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

        decorRenderer?.draw(camera, if (headUnderwater) ambientFor(dayT) * .4f else ambientFor(dayT), caveBlend, caveFogEnd)

        // ── Mise à jour + rendu ennemis ───────────────────────────────────────
        if (!gamePaused) mode.update(dt)
        if (worldSource == null) {
            if (!gamePaused) {
                passiveAnimals.update(dt, camera.playerX, camera.playerY, camera.playerZ,hotbar[selectedSlot]?.takeIf { (inventory[it] ?: 0)>0 })
                residents.update(dt,camera.playerX,camera.playerY,camera.playerZ,ambientFor(dayT)<.4f)
                workshops.animate(dt,camera.playerX,camera.playerY,camera.playerZ)
                updateAnimalAudio(dt)
            }
            enemyRenderer.render(passiveAnimals.visible, camera.x, camera.y, camera.z, camera.yaw, camera.vpMatrix)
            enemyRenderer.render(residents.visible,camera.x,camera.y,camera.z,camera.yaw,camera.vpMatrix)
            enemyRenderer.render(workshops.machinery,camera.x,camera.y,camera.z,camera.yaw,camera.vpMatrix)
        }
        enemyRenderer.render(
            enemyManager.enemies,
            camera.x, camera.y, camera.z,
            camera.yaw, camera.vpMatrix
        )
        (mode as? com.Atom2Universe.app.games.caves.mode.AssaultMode)?.let { assault ->
            enemyRenderer.renderShieldPickups(assault.shieldPickups,
                camera.x, camera.y, camera.z, camera.vpMatrix, elapsed)
        }
        (mode as? com.Atom2Universe.app.games.caves.mode.ShowcaseMode)?.let { showcase ->
            // Separate display bodies from combat targets; respect the renderer batch capacity.
            showcase.mannequins.filter {
                val dx = it.x - camera.x; val dz = it.z - camera.z
                dx * dx + dz * dz < 32.0 * 32.0
            }.chunked(32).forEach { batch ->
                enemyRenderer.render(batch, camera.x, camera.y, camera.z, camera.yaw, camera.vpMatrix)
            }
        }
        // ── Rendu projectiles + particules d'impact ───────────────────────────
        projRenderer.render(projectiles, camera.x, camera.y, camera.z, camera.yaw, camera.vpMatrix)
        projRenderer.renderParticles(impactParticles, camera.x, camera.y, camera.z, camera.yaw, camera.vpMatrix)

        drawFishingLine()

        // ── Boîte joueur (TPS) ────────────────────────────────────────────────
        updateEquipmentAnimation(if (gamePaused) 0f else dt)
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
        val waterKey=world.chunkKey(camera.chunkX(),Math.floorDiv(floor(camera.playerY).toInt(),16),camera.chunkZ())
        if(waterKey!=waterBiomeKey) {
            waterBiomeKey=waterKey
            val biome = world.naturalSurfaceBiomeAt(camera.playerX, camera.playerY, camera.playerZ)
            targetWaterTint = when {
                biome == "wetlands" -> floatArrayOf(.20f,.38f,.27f)
                biome == "tundra" || biome == "taiga" || biome == "iceberg" -> floatArrayOf(.16f,.37f,.49f)
                biome?.startsWith("magic_forest") == true -> floatArrayOf(.22f,.35f,.57f)
                biome == "jungle" || biome == "desert" -> floatArrayOf(.05f,.50f,.46f)
                else -> floatArrayOf(.08f,.43f,.50f)
            }
        }
        for (i in 0..2) waterTint[i] += (targetWaterTint[i]-waterTint[i]) * (dt*.8f).coerceIn(0f,1f)
        GLES30.glUniform3f(waterTintUniform, waterTint[0],waterTint[1],waterTint[2])
        GLES30.glUniform3f(waterSkyUniform, skyR,skyG,skyB)
        GLES30.glUniform3f(waterCaveFogUniform, caveBlend, caveFogEnd * 0.55f, caveFogEnd)
        GLES30.glUniformMatrix4fv(wWUMvp, 1, false, camera.vpMatrix, 0)
        GLES30.glUniform1f(wWUAmbient, waterAmbient)
        GLES30.glUniform1f(wWUCaveFloor, CAVE_FLOOR)
        GLES30.glUniform1f(wWUTime, elapsed)
        GLES30.glUniform3f(waterPhaseUniform,
            ((camera.x * 1.1 + elapsed.toDouble() * .7) % (2.0 * Math.PI)).toFloat(),
            ((camera.z * 0.85 + elapsed.toDouble() * .5) % (2.0 * Math.PI)).toFloat(),
            ((camera.y * 5.0 + elapsed.toDouble() * 4.0) % (2.0 * Math.PI)).toFloat())
        GLES30.glUniform4fv(wWULights, cachedLightCount.coerceAtLeast(1), lightData, 0)
        GLES30.glUniform4fv(wWULightColors, cachedLightCount.coerceAtLeast(1), lightColors, 0)
        GLES30.glUniform1i(wWULightCount, cachedLightCount)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        // Tri des chunks visibles seulement : transparence stable, tampon réutilisé.
        visibleWaterKeys.clear()
        for (key in waterMeshes.keys) {
            if (isChunkInFrustum(world.keyToCx(key), world.keyToCy(key), world.keyToCz(key)))
                visibleWaterKeys.add(key)
        }
        fun waterDistance(key: Long): Double {
            val x = world.keyToCx(key).toDouble() * CHUNK_SIZE + CHUNK_SIZE * 0.5 - camera.x
            val y = world.keyToCy(key).toDouble() * CHUNK_SIZE + CHUNK_SIZE * 0.5 - camera.y
            val z = world.keyToCz(key).toDouble() * CHUNK_SIZE + CHUNK_SIZE * 0.5 - camera.z
            return x * x + y * y + z * z
        }
        visibleWaterKeys.sortWith(Comparator { a, b -> waterDistance(b).compareTo(waterDistance(a)) })
        for (key in visibleWaterKeys) {
            val mesh = waterMeshes[key] ?: continue
            val kcx = world.keyToCx(key); val kcy = world.keyToCy(key); val kcz = world.keyToCz(key)
            val offX = (kcx.toDouble() * CHUNK_SIZE - camera.x).toFloat()
            val offY = (kcy.toDouble() * CHUNK_SIZE - camera.y).toFloat()
            val offZ = (kcz.toDouble() * CHUNK_SIZE - camera.z).toFloat()
            GLES30.glUniform3f(wWUChunkOffset, offX, offY, offZ)
            uploadChunkLights(key, offX, offY, offZ, wWULights, wWULightColors, wWULightCount)
            mesh.draw(wWAPos, wWAUv, wWASky)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        // ── Viewmodel 1re personne (bras + objet tenu) ────────────────────────
        drawViewmodel(dt)

        posAccum += dt
        if (posAccum >= 0.1f) {
            posAccum = 0f
            posCallback?.invoke(camera.posString())
        }

        if (com.Atom2Universe.app.BuildConfig.DEBUG) {
            perfFrames++
            if (perfLogNs == 0L) perfLogNs = now
            val period = now - perfLogNs
            if (period >= 5_000_000_000L) {
                val runtime = Runtime.getRuntime()
                val heapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val fps = perfFrames * 1_000_000_000L / period
                android.util.Log.i("CavePerf", "fps=$fps heapMB=$heapMb genJobs=${generationJobs.get()} " +
                    "meshJobs=${meshJobs.get()} waitingMeshes=$waitingMeshChunks " +
                    "uploadKB=${pendingMeshBytes.get() / 1024} lights=${lightSources.size} " +
                    "waitingFirst=$waitingFirstMeshes firstUploads=$firstMeshUploads provisional=$provisionalUploads " +
                    "refreshUploads=$refreshMeshUploads lightWorker=${lightWorkerRunning.get()} " +
                    "lightPending=${world.hasPendingLight()} lodJobs=${lodBuilding.size} " +
                    "meshes=${meshes.size} position=$cx,$cy,$cz")
                firstMeshUploads = 0
                refreshMeshUploads = 0
                provisionalUploads = 0
                perfFrames = 0
                perfLogNs = now
            }
        }
        val elapsed2 = System.nanoTime() - now
        val sleepNs = TARGET_FRAME_NS - elapsed2
        if (sleepNs > 1_000_000L) Thread.sleep(sleepNs / 1_000_000L)
    }

    /** Les sources sont choisies par volume éclairé, puis mises en cache hors du shader. */
    private fun selectChunkLights(key: Long): LocalLights {
        val wx = world.keyToCx(key).toDouble() * CHUNK_SIZE
        val wy = world.keyToCy(key).toDouble() * CHUNK_SIZE
        val wz = world.keyToCz(key).toDouble() * CHUNK_SIZE
        val candidates = lightSources.entries.mapNotNull { entry ->
            val p = entry.key
            val dx = maxOf(wx - p.first - .5, 0.0, p.first + .5 - wx - CHUNK_SIZE)
            val dy = maxOf(wy - p.second - .5, 0.0, p.second + .5 - wy - CHUNK_SIZE)
            val dz = maxOf(wz - p.third - .5, 0.0, p.third + .5 - wz - CHUNK_SIZE)
            val radius = 16.0 * entry.value + 1.0 // marge pour la flamme murale
            if (dx * dx + dy * dy + dz * dz >= radius * radius) null else entry
        }.sortedBy { entry ->
            val dx = entry.key.first + .5 - wx - CHUNK_SIZE * .5
            val dy = entry.key.second + .5 - wy - CHUNK_SIZE * .5
            val dz = entry.key.third + .5 - wz - CHUNK_SIZE * .5
            (dx * dx + dy * dy + dz * dz) / (entry.value * entry.value).coerceAtLeast(.01f)
        }.take(MAX_LIGHTS)
        val data = FloatArray(candidates.size * 4)
        val colors = FloatArray(data.size)
        candidates.forEachIndexed { i, entry ->
            val p = entry.key
            val block = world.blockAt(p.first, p.second, p.third)
            val natural = block != TORCH && block != LAVA
            val color = if (natural) BlockRegistry.get(block)?.color ?: -1 else 0xFFFFAD4D.toInt()
            val flame = if (block == TORCH) TorchModel.flame(world.metaAt(p.first, p.second, p.third)) else null
            data[i * 4] = (p.first - wx + (flame?.x ?: .5f)).toFloat()
            data[i * 4 + 1] = (p.second - wy + (flame?.y ?: .5f)).toFloat()
            data[i * 4 + 2] = (p.third - wz + (flame?.z ?: .5f)).toFloat()
            data[i * 4 + 3] = entry.value
            colors[i * 4] = ((color ushr 16) and 255) / 255f
            colors[i * 4 + 1] = ((color ushr 8) and 255) / 255f
            colors[i * 4 + 2] = (color and 255) / 255f
            colors[i * 4 + 3] = 1f
        }
        return LocalLights(data, colors)
    }

    private fun uploadChunkLights(key: Long, x: Float, y: Float, z: Float,
                                  lights: Int, colors: Int, countUniform: Int) {
        val selected = localLights.getOrPut(key) { selectChunkLights(key) }
        val count = selected.data.size / 4
        for (i in 0 until count) {
            val src = i * 4
            chunkLightData[src] = selected.data[src] + x
            chunkLightData[src + 1] = selected.data[src + 1] + y
            chunkLightData[src + 2] = selected.data[src + 2] + z
            chunkLightData[src + 3] = selected.data[src + 3]
        }
        if (count > 0) {
            GLES30.glUniform4fv(lights, count, chunkLightData, 0)
            GLES30.glUniform4fv(colors, count, selected.colors, 0)
        }
        GLES30.glUniform1i(countUniform, count)
    }

    private fun stairMaskAt(x: Int, y: Int, z: Int) =
        StairConnections.maskAt(x, y, z, ::worldBlockAt, world::metaAt)

    private fun projectileSolid(x: Double, y: Double, z: Double): Boolean {
        if (decorSource?.decorHitsSegment(x, y, z, x, y, z) == true) return true
        val bx = floor(x).toInt(); val by = floor(y).toInt(); val bz = floor(z).toInt()
        val block = worldBlockAt(bx, by, bz)
        if (block == AIR || BlockRegistry.isDecoration(block) || BlockRegistry.isWater(block)) return false
        val def = BlockRegistry.get(block) ?: return true
        if (!def.stairs && !def.slab && def.blockHeight >= 1f) return true
        return PartialBlockModel.boxes(world.metaAt(bx, by, bz), def.slab, def.blockHeight, stairMaskAt(bx, by, bz)).any {
            x - bx >= it.x && x - bx <= it.x + it.width &&
                y - by >= it.y && y - by <= it.y + it.height &&
                z - bz >= it.z && z - bz <= it.z + it.depth
        }
    }
    private val PLAYER_HIT_RADIUS = 0.4

    /** Une balle traverse-t-elle le corps du joueur ? Cylindre qui va des pieds au sommet du crâne. */
    private fun projectileHitsPlayer(p: Projectile): Boolean {
        if (playerMode != PlayerMode.WALK) return false
        val dx = p.x - camera.playerX; val dz = p.z - camera.playerZ
        if (dx * dx + dz * dz > PLAYER_HIT_RADIUS * PLAYER_HIT_RADIUS) return false
        return p.y >= camera.playerY - 1.62 && p.y <= camera.playerY + physics.heightAbove
    }

    private fun canRecover(p: Projectile): Boolean {
        val dx=p.x-camera.playerX; val dy=p.y-(camera.playerY-.5); val dz=p.z-camera.playerZ
        if (dx*dx+dy*dy+dz*dz > 2.2*2.2) return false
        val steps=ceil(sqrt(dx*dx+dy*dy+dz*dz)/.15).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val t=i.toDouble()/steps
            if(projectileSolid(camera.playerX+dx*t,camera.playerY-.5+dy*t,camera.playerZ+dz*t)) return false
        }
        return true
    }

    private fun updateProjectiles(dt: Float) {
        var recovered = false
        val iter=projectiles.iterator()
        while(iter.hasNext()) {
            val p=iter.next()
            p.age+=dt
            if(p.stuck) {
                if(p.age>.4f && p.ammoId != null && canRecover(p)) {
                    inventory[p.ammoId]=(inventory[p.ammoId] ?: 0)+1
                    recovered=true; iter.remove()
                }
                continue
            }
            // Sous-pas de 15 cm : même une balle rapide ne saute pas une paroi voxel.
            val steps=p.substeps(dt)
            val step=dt/steps
                for(i in 0 until steps) {
                val ox=p.x; val oy=p.y; val oz=p.z
                p.advance(step)
                if(p.kind != ProjectileKind.LEGACY && (projectileSolid(p.x,p.y,p.z) ||
                    decorSource?.decorHitsSegment(ox,oy,oz,p.x,p.y,p.z) == true)) {
                    spawnImpact(p.x,p.y,p.z)
                    if (p.kind == ProjectileKind.BULLET || p.kind == ProjectileKind.PELLET) announceImpact(p.x,p.y,p.z)
                    if(p.ammoId != null && (p.kind==ProjectileKind.ARROW || p.kind==ProjectileKind.BOLT)) {
                        p.x=ox;p.y=oy;p.z=oz;p.stuck=true;recovered=true
                    } else { iter.remove() }
                    break
                }
                if (p.fromEnemy) {
                    // Balle de soldat : elle ne touche que le joueur (pas de tir ami entre soldats).
                    if (projectileHitsPlayer(p)) {
                        spawnImpact(p.x,p.y,p.z)
                        mode.onPlayerShot(p.damage, p.dirX, p.dirZ)
                        iter.remove();break
                    }
                    if(p.travelDist>p.maxRange) { iter.remove();break }
                    continue
                }
                val hit=enemyManager.enemies.find { e ->
                    val radius=e.def.radius.toDouble()+.5
                    e.hp>0 && (p.x-e.x).pow(2)+(p.z-e.z).pow(2)<radius*radius &&
                        p.y>=e.y-.25 && p.y<=e.y+MobModels.bodyHeightWorld(e.def.model,e.baseScale)+.25
                }
                if(hit!=null) {
                    if(p.kind!=ProjectileKind.LEGACY) spawnImpact(p.x,p.y,p.z)
                    // Tête : le haut du corps, au-dessus de MobModels.HEAD_START. Le mode décide ce
                    // qu'elle vaut (la survie ne change rien, l'Assaut double les dégâts).
                    val headshot = p.y >= hit.y + MobModels.bodyHeightWorld(hit.def.model, hit.baseScale) * MobModels.HEAD_START
                    val damage = if (headshot) (p.damage * mode.headshotMultiplier).roundToInt() else p.damage
                    if(p.isPlayerWeapon) applyWeaponHit(hit,damage,p.stats,Random.Default)
                    else enemyManager.damageEnemy(hit,damage)
                    mode.onEnemyHit(hit, headshot)
                    iter.remove();break
                }
                if(p.travelDist>p.maxRange) { iter.remove();break }
            }
        }
        // Limite mémoire pour les munitions plantées dans une session très longue.
        var excess=projectiles.count { it.stuck }-256
        if(excess>0) projectiles.removeAll { it.stuck && excess-- > 0 }
        if(recovered) {
            recoverableAmmoSnapshot=projectiles.filter { it.stuck && it.ammoId!=null }.map {
                StuckAmmo(it.x,it.y,it.z,it.dirX*it.speed,it.velY,it.dirZ*it.speed,it.ammoId!!)
            }
            inventoryCallback?.invoke(inventory.toMap())
        }
    }

    private fun isAimingAtRockBlock(): Boolean {
        val hit = raycastBlock() ?: return false
        return worldBlockAt(hit.bx, hit.by, hit.bz) in ROCK_IDS
    }

    // Munitions possibles pour chaque famille d'arme à distance, dans l'ordre de préférence
    // de consommation (le lance-pierre accepte les deux variantes de caillou ramassées au sol).
    /** Kit de test, appelé sur le thread GL. Réutilise les armes déjà possédées. */
    fun giveWeaponTestKit() {
        if (mode.singleWeapon) return
        val registry = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry
        val types = RangedProfile.all.keys.toList()
        magazines.clear()
        for ((slot, type) in types.withIndex()) {
            val existing = inventory.keys.firstOrNull { id ->
                (inventory[id] ?: 0) > 0 && registry.get(id)?.defId == type
            }
            val id = existing ?: ItemRegistry.rollInstance(type,Random.Default)?.let { registry.allocate(it) } ?: continue
            inventory[id] = 1
            // Les objets déplacés de la barre restent dans l'inventaire.
            for (i in hotbar.indices) if (hotbar[i] == id) hotbar[i] = null
            hotbar[slot] = id
            for (ammo in ammoCandidatesFor(type)) inventory[ammo] = maxOf(inventory[ammo] ?: 0,250)
        }
        selectedSlot = 0
        weaponChargeTime = 0f; rockChargeTime = 0f
        equipmentRelease = -1f; releasedEquipment = null
        // L’activité reconstruit les banques UI avant de publier les changements.
    }

    private val assaultWeaponIds = HashMap<String, Short>()

    /** Un seul prêt par famille, sans affixes aléatoires qui modifieraient la cadence. Thread GL. */
    fun equipAssaultWeapon(type: String): Boolean {
        if (!mode.singleWeapon) return false
        val def = ItemRegistry.get(type) ?: return false
        val profile = RangedProfile.all[type] ?: return false
        if (profile.magazine <= 0) return false
        val id = assaultWeaponIds.getOrPut(type) {
            val damage = def.damageBase?.let { (it.min + it.max) / 2 } ?: 1
            com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.allocate(
                com.Atom2Universe.app.games.caves.node.ItemInstance(type,
                    com.Atom2Universe.app.games.caves.node.ItemRarity.COMMON, damage, emptyMap(), tier = def.tier))
        }
        inventory.clear()
        inventory[id] = 1
        hotbar.fill(null)
        hotbar[0] = id
        selectedSlot = 0
        magazines.clear()
        magazines[id] = MagazineState(profile.magazine, profile.reload)
        weaponAttackCooldown = 0f
        weaponChargeTime = 0f; rockChargeTime = 0f
        equipmentRelease = -1f; releasedEquipment = null
        fireWasDown = false
        hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
        return true
    }

    private fun ammoCandidatesFor(weaponType: String?): List<Short> = when (weaponType) {
        "sling"    -> ROCK_IDS.toList()
        "bow"      -> listOf(ARROW_ID)
        "crossbow" -> listOf(BOLT_ID)
        "gun", "shotgun", "smg", "dual_pistols", "lever_rifle" -> listOf(BULLET_ID)
        else       -> emptyList()
    }

    /** Première munition compatible réellement présente dans l'inventaire (ou null si aucune). */
    private fun ammoBlockIdFor(weaponType: String?): Short? =
        ammoCandidatesFor(weaponType).firstOrNull { (inventory[it] ?: 0) > 0 }
            ?: ammoCandidatesFor(weaponType).firstOrNull()

    /** Le slot sélectionné contient-il une arme à distance (sling/bow/crossbow/gun) ? */
    private fun isSelectedRangedWeapon(): Boolean {
        val id = hotbar[selectedSlot] ?: return false
        if (!com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(id)) return false
        val weapon = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(id) ?: return false
        val def = com.Atom2Universe.app.games.caves.node.ItemRegistry.get(weapon.defId) ?: return false
        return def.weaponType in RANGED_WEAPON_TYPES
    }

    // Attaque à distance avec l'arme équipée (arc, arbalète…) : consomme 1 munition
    // dans l'inventaire et applique les mêmes affixes qu'un coup de mêlée (crit, statuts…).
    private fun tryWeaponRangedAttack(): Boolean {
        val heldId = hotbar[selectedSlot] ?: return false
        val weapon = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(heldId) ?: return false
        val def = com.Atom2Universe.app.games.caves.node.ItemRegistry.get(weapon.defId) ?: return false
        val ammoId = ammoBlockIdFor(def.weaponType) ?: return false
        if (weaponAttackCooldown > 0f) return false
        // Munitions illimitées (Assaut) : la réserve n'est ni vérifiée ni entamée, seul le chargeur compte.
        val infiniteAmmo = mode.infiniteAmmo
        val ammoCount = inventory[ammoId] ?: 0
        if (!infiniteAmmo && ammoCount <= 0) return false

        val profile = RangedProfile.all[def.weaponType] ?: return false
        val magazine = if(profile.magazine>0) magazine(heldId,profile) else null
        if(magazine != null && !magazine.shoot()) {
            reloadWithSound(magazine)
            return false
        }
        val stats = weapon.rolledStats
        val drawPower=if(mode.allowsWorldEdits && profile.magazine==0) (.45f+.55f*(weaponChargeTime/.9f).coerceIn(0f,1f)) else 1f
        val baseDamage = ((weapon.rolledDamage ?: 1)*drawPower).roundToInt()
        val yawRad = Math.toRadians(camera.yaw.toDouble())
        val rightX = -cos(yawRad); val rightZ = sin(yawRad)
        val fwdX = sin(yawRad);   val fwdZ = cos(yawRad)
        // Départ quasi depuis la tête (précision) avec un léger décalage droite/avant
        // pour l'impression que c'est le bras qui tire — voir le même choix sur le jet à main nue.
        val spawnX = camera.playerX + rightX * 0.10 + fwdX * 0.12
        val spawnZ = camera.playerZ + rightZ * 0.10 + fwdZ * 0.12
        val spawnY = camera.eyeY - 0.05
        val ammoWeapon = ammoWeaponDef
        val heat = if(def.weaponType=="smg") 1f+(magazine?.shots?.rem(profile.magazine) ?: 0)*.055f else 1f
        repeat(profile.pellets) {
            val spread=profile.spread*heat*if(mode.allowsWorldEdits && physics.isCrouching) .65f else 1f
            var dx=camera.aimX.toDouble()+Random.nextDouble(-spread.toDouble(),spread.toDouble())
            var dy=camera.aimY.toDouble()+Random.nextDouble(-spread.toDouble(),spread.toDouble())
            var dz=camera.aimZ.toDouble()+Random.nextDouble(-spread.toDouble(),spread.toDouble())
            val len=sqrt(dx*dx+dy*dy+dz*dz);dx/=len;dy/=len;dz/=len
            projectiles.add(Projectile(spawnX,spawnY,spawnZ,dx,dy,dz,
                profile.speed*sqrt(drawPower),(baseDamage/profile.pellets).coerceAtLeast(1),ammoWeapon,
                isRock=profile.kind==ProjectileKind.ROCK,stats=stats,isPlayerWeapon=true,
                kind=profile.kind,ammoId=if(profile.kind==ProjectileKind.ARROW || profile.kind==ProjectileKind.BOLT) ammoId else null,
                maxRange=profile.range))
        }

        val newCount = if (infiniteAmmo) ammoCount else ammoCount - 1
        if (!infiniteAmmo) {
            if (newCount <= 0) inventory.remove(ammoId) else inventory[ammoId] = newCount
            inventoryCallback?.invoke(inventory.toMap())
        }

        val speedBonus = stats["attack_speed"] ?: 0
        weaponAttackCooldown=(profile.interval*(1f-speedBonus/100f)).coerceAtLeast(profile.interval*.45f)
        eventBus.publish(GameEvent.WeaponFired(def.weaponType ?: "gun"))
        if(magazine?.remaining==0 && (infiniteAmmo || newCount>0)) reloadWithSound(magazine)
        // Un coup de feu s'entend : le mode prévient les ennemis à portée d'oreille.
        mode.onPlayerFired()
        swingCallback?.invoke()
        startSwing()
        equipmentRelease = 0f
        releasedEquipment = def.weaponType
        return true
    }

    // Bouton action en mode combat : tire l'arme sélectionnée (le joueur choisit, on ne
    // bascule plus jamais tout seul sur une autre arme) ; repli main nue si rien d'utilisable
    // n'est sélectionné. Sélection mémorisée séparément par mode (voir [selectedSlot]).
    private fun updateRockThrow(dt: Float) {
        if (!mode.allowsCombat) {
            fireWasDown = false
            rockChargeTime = 0f; weaponChargeTime = 0f
            return
        }
        val down=touch.rtChargeRaw>.3f
        val pressed=down && !fireWasDown
        fireWasDown=down
        val held=hotbar[selectedSlot]
        if(mode.allowsWorldEdits) {
            expeditionCombat.tick(dt,held)
            fishing.tick(dt,held,down)
        }
        if(held!=lastFireWeapon) {
            rockChargeTime=0f;weaponChargeTime=0f;lastFireWeapon=held
        }
        magazines[held]?.let { magazine ->
            val wasReloading = magazine.reloadRemaining > 0f
            magazine.update(dt)
            if (wasReloading && magazine.reloadRemaining == 0f) {
                eventBus.publish(GameEvent.WeaponReload(complete = true))
            }
        }
        if(mode.allowsWorldEdits && held==E.ROD) {
            rockChargeTime=0f;weaponChargeTime=0f
            if(pressed && (inventory[held] ?: 0)>0) fishing.action()
            return
        }
        if(mode.allowsWorldEdits && expeditionCombat.guard>0f) {
            rockChargeTime=0f;weaponChargeTime=0f;return
        }
        if (heldItemMode != HotbarMode.COMBAT) { rockChargeTime = 0f; weaponChargeTime = 0f; return }
        if(mode.allowsWorldEdits && held!=null && E.isEquipment(held)) {
            rockChargeTime=0f;weaponChargeTime=0f
            if((inventory[held] ?: 0)<=0) return
            if(held in E.melee) { if(down) expeditionCombat.hold(dt); expeditionCombat.input(held,down) }
            else if(pressed) expeditionCombat.equip(held)
            return
        }

        // Viser un caillou au sol le ramasse plutôt que de tirer dans le vide dessus
        // (voir [updateMining]) — on n'engage donc pas le tir dans ce cas.
        if (mode.allowsWorldEdits && isAimingAtRockBlock()) { weaponChargeTime = 0f; rockChargeTime = 0f; return }

        if (isSelectedRangedWeapon()) {
            val profile=RangedProfile.all[selectedEquipmentType()]
            if(profile != null && profile.magazine>0) {
                rockChargeTime=0f;weaponChargeTime=0f
                if(down && (profile.automatic || pressed)) tryWeaponRangedAttack()
                return
            }
            rockChargeTime = 0f
            // Vise en tenant le bouton (tension de l'arme), tire seulement au relâchement —
            // pas de tir instantané à l'appui, pour pouvoir viser d'abord. Si l'arme
            // sélectionnée n'a plus de munitions, tryWeaponRangedAttack ne fait simplement rien.
            if (touch.rtChargeRaw > 0.3f) {
                weaponChargeTime += dt
            } else if (weaponChargeTime > 0.3f) {
                tryWeaponRangedAttack()
                weaponChargeTime = 0f
            } else {
                weaponChargeTime = 0f
            }
            return
        }
        weaponChargeTime = 0f

        // Repli : jet de caillou à main nue, tant qu'il en reste dans l'inventaire.
        val rockId = ROCK_IDS.firstOrNull { (inventory[it] ?: 0) > 0 }
        if (rockId == null) { rockChargeTime = 0f; return }
        val rt = touch.rtChargeRaw
        if (rt > 0.3f) {
            rockChargeTime = (rockChargeTime + dt).coerceAtMost(ROCK_CHARGE_MAX)
        } else if (rockChargeTime > 0.3f) {
            val charge = rockChargeTime / ROCK_CHARGE_MAX
            val speed = 10f + (28f - 10f) * charge
            val damage = (2 + ((5 - 2) * charge)).toInt()
            // Départ quasi depuis la tête (vise juste), avec un tout petit décalage vers
            // la droite/l'avant pour donner l'impression que c'est le bras qui lance —
            // un décalage trop grand (ancien 0.45/0.35) désalignait le tir du réticule.
            val yawRad = Math.toRadians(camera.yaw.toDouble())
            val rightX = -cos(yawRad); val rightZ = sin(yawRad)
            val fwdX = sin(yawRad);   val fwdZ = cos(yawRad)
            val spawnX = camera.playerX + rightX * 0.10 + fwdX * 0.12
            val spawnZ = camera.playerZ + rightZ * 0.10 + fwdZ * 0.12
            val spawnY = camera.eyeY - 0.05
            val rockWeapon = rockWeaponDef
            projectiles.add(Projectile(
                spawnX, spawnY, spawnZ,
                camera.aimX.toDouble(), camera.aimY.toDouble(), camera.aimZ.toDouble(),
                speed, damage, rockWeapon, isRock = true
            ))
            startSwing()
            equipmentRelease = 0f
            releasedEquipment = "rock"
            val count = (inventory[rockId] ?: 0) - 1
            if (count <= 0) inventory.remove(rockId) else inventory[rockId] = count
            inventoryCallback?.invoke(inventory.toMap())
            rockChargeTime = 0f
        } else {
            rockChargeTime = 0f
        }
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

        // Résistances élémentaires du mob : multiplient à la fois la chance de proc
        // et l'ampleur de l'effet (dégâts ou durée). 0 = immunisé, >1 = vulnérable.
        val res = enemy.def.resistances

        // Saignement : chaque proc ajoute à une jauge (façon Dark Souls) ; pleine (100),
        // elle explose en un gros pourcentage des PV max puis retombe à zéro. Elle
        // redescend seule si le mob n'est pas retouché (voir EnemyManager).
        val bleedRes = res["bleed"] ?: 1f
        val bleedChance = ((stats["bleed_chance"] ?: 0) * bleedRes).toInt()
        if (bleedRes > 0f && bleedChance > 0 && rng.nextInt(100) < bleedChance) {
            enemy.bleedBuildup = (enemy.bleedBuildup + 25f * bleedRes).coerceAtMost(100f)
            enemy.bleedDecayGrace = 2.5f
        }

        // Poison : dégâts sur la durée, chance et ampleur réduites si le mob y résiste
        val poisonRes = res["poison"] ?: 1f
        val poisonChance = ((stats["poison_chance"] ?: 0) * poisonRes).toInt()
        if (poisonRes > 0f && poisonChance > 0 && rng.nextInt(100) < poisonChance) {
            enemy.poisonDamage = (baseDamage * 0.10f * critMult * poisonRes).toInt().coerceAtLeast(1)
            enemy.poisonTimer = 4f
            enemy.poisonTickTimer = 0.8f
        }

        // Feu : dégâts rapides sur la durée, inefficace contre les mobs résistants au feu
        val fireRes = res["fire"] ?: 1f
        val fireChance = ((stats["fire_chance"] ?: 0) * fireRes).toInt()
        if (fireRes > 0f && fireChance > 0 && rng.nextInt(100) < fireChance) {
            enemy.fireDamage = (baseDamage * 0.20f * critMult * fireRes).toInt().coerceAtLeast(1)
            enemy.fireTimer = 2f
            enemy.fireTickTimer = 0.3f
        }

        // Gel : immobilisation totale, durée modulée par la résistance/vulnérabilité au froid
        val freezeRes = res["ice"] ?: 1f
        val freezeChance = ((stats["freeze_chance"] ?: 0) * freezeRes).toInt()
        if (freezeRes > 0f && freezeChance > 0 && rng.nextInt(100) < freezeChance) {
            enemy.freezeTimer = 1.5f * freezeRes
        }

        // Électrique : le mob "bugue" et attaque ses propres alliés un instant
        val electricRes = res["electric"] ?: 1f
        val electricChance = ((stats["electric_chance"] ?: 0) * electricRes).toInt()
        if (electricRes > 0f && electricChance > 0 && rng.nextInt(100) < electricChance) {
            enemy.confusionTimer = 1.5f * electricRes
        }
    }

    internal fun equippedWeaponStat(key: String): Int {
        val id = hotbar[selectedSlot] ?: return 0
        if (!com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(id)) return 0
        return com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(id)?.rolledStats?.get(key) ?: 0
    }

    /**
     * Le bruit d'une balle qui frappe un mur, en mode Assaut seulement : béton, bois ou métal selon
     * le bloc touché, plus faible avec la distance et placé à gauche ou à droite comme les animaux.
     * Les balles des soldats aussi : entendre claquer le mur juste à côté, c'est savoir qu'on est visé.
     */
    private fun announceImpact(x: Double, y: Double, z: Double) {
        if (mode.allowsWorldEdits) return
        val dx = x - camera.playerX
        val dz = z - camera.playerZ
        val distance = kotlin.math.sqrt(dx * dx + dz * dz + (y - camera.eyeY) * (y - camera.eyeY))
        if (distance > IMPACT_HEARING) return
        val block = worldBlockAt(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
        val material = when {
            block == AIR || block in 1000..1019 -> "wood"   // meubles (décor) et planches
            block == BRICK_OBSIDIAN || block == FURNACE || block == IRON -> "metal"
            else -> "concrete"
        }
        val yaw = Math.toRadians(camera.yaw.toDouble())
        val pan = ((-cos(yaw) * dx + sin(yaw) * dz) / distance.coerceAtLeast(1.0)).toFloat() * .65f
        eventBus.publish(GameEvent.BulletImpact(material, (1f - distance.toFloat() / IMPACT_HEARING.toFloat()), pan))
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
        // Mode sans construction ni destruction (Assaut) : la carte ne se touche pas.
        if (!mode.allowsWorldEdits) {
            touch.placeRequested = false
            return
        }
        if (touch.placeRequested) {
            touch.placeRequested = false
            placeBlock()
        }

        // Minage libre et gratuit en mode construction, peu importe ce qui est en main.
        // En mode combat, viser un caillou permet quand même de le ramasser (munitions),
        // sans avoir à repasser en construction juste pour ça.
        if (touch.laserActive && heldItemMode == HotbarMode.BUILD && tryToolStrike()) {
            mineTarget=null; mineDamage=0f; miningCallback?.invoke(0f,null)
            return
        }
        val canMine = touch.laserActive &&
            (heldItemMode == HotbarMode.BUILD || (heldItemMode == HotbarMode.COMBAT && hotbar[selectedSlot] !in E.melee && isAimingAtRockBlock()))
        val target = if (canMine) raycastBlock() else null

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
        mineDamage += dt * com.Atom2Universe.app.games.caves.node.FrontierItems.miningSpeed(hotbar[selectedSlot], BlockRegistry.get(blockType)) / hardness

        miningCallback?.invoke(mineDamage, blockType)

        if (mineDamage >= 1f) {
            val box = workshops.view(FrontierWorkshops.Pos(bx,by,bz))
            if (box?.items?.any { (id,count) -> (inventory[id] ?: 0).toLong()+count > Int.MAX_VALUE } == true) {
                mineDamage = 0f
                farmMessageCallback?.invoke(context.getString(com.Atom2Universe.app.R.string.cave_storage_overflow))
                return
            }
            val farmDrops = farming.harvest(bx, by, bz, uproot = true)
            if (!isCreative && farmDrops != null) grantFarmItems(farmDrops)
            if (!isCreative && BlockRegistry.get(blockType)?.harvestCategory == "plant" && Random.nextFloat() < .12f) {
                val biome=world.naturalSurfaceBiomeAt(bx.toDouble(),by.toDouble(),bz.toDouble())
                val crops=when(biome) {
                    "desert", "red_desert", "savanna" -> intArrayOf(1,9,14,18)
                    "taiga", "tundra" -> intArrayOf(4,5,15,17)
                    "jungle", "jungle_edge", "wetlands" -> intArrayOf(2,10,13,18)
                    "forest", "birch_forest", "dark_forest", "redwood_forest" -> intArrayOf(7,8,11,16,17)
                    else -> intArrayOf(0,2,3,6,8,12)
                }
                grantFarmItems(listOf(com.Atom2Universe.app.games.caves.node.FarmItems.seed(crops[Random.nextInt(crops.size)]) to 1))
            }
            val contents = workshops.breakBlock(FrontierWorkshops.Pos(bx, by, bz))
            for ((id, count) in contents) inventory[id] = ((inventory[id] ?: 0).toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (contents.isNotEmpty()) inventoryCallback?.invoke(inventory.toMap())
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
            clearUnsupportedAround(bx, by, bz)
            mineTarget = null
            mineDamage = 0f
            miningCallback?.invoke(0f, null)
        }
    }

    private fun tryToolStrike(): Boolean {
        if (!mode.allowsCombat || isCreative) return false
        val held=hotbar[selectedSlot]
        if (held != null && com.Atom2Universe.app.games.caves.node.FrontierItems.toolIndex(held)<0) return false
        val sx=camera.playerX; val sy=camera.eyeY; val sz=camera.playerZ
        // March the short aim ray, stopping at opaque terrain before considering a target.
        for(step in 1..18) {
            val distance=step*.18
            val x=sx+camera.aimX*distance; val y=sy+camera.aimY*distance; val z=sz+camera.aimZ*distance
            if (projectileSolid(x,y,z)) return false
            val enemy=enemyManager.enemies.firstOrNull { e ->
                e.hp>0 && (x-e.x).pow(2)+(z-e.z).pow(2)<(e.def.radius+.25).pow(2) &&
                    y>=e.y && y<=e.y+MobModels.bodyHeightWorld(e.def.model,e.baseScale)
            } ?: continue
            if (weaponAttackCooldown <= 0f) {
                val n=com.Atom2Universe.app.games.caves.node.FrontierItems.toolIndex(held)
                val damage=if(n<0) 2 else 3+(n/3)*2+if(n%3==1) 2 else 0
                enemyManager.damageEnemy(enemy,damage)
                weaponAttackCooldown=.65f; startSwing()
            }
            return true
        }
        return false
    }

    private fun collectBlock(blockType: Short) {
        if (blockType.toInt() in 7020..7023) {
            grantFarmItems(listOf(com.Atom2Universe.app.games.caves.node.FarmItems.seed(0) to 1))
            return
        }
        // Resolve once: breaking stone yields cobble, never recursively breaks the result.
        val (dropType, count) = BlockRegistry.harvestDrop(blockType) ?: return
        val newStack=(inventory[dropType] ?: 0)==0
        inventory[dropType] = (inventory[dropType] ?: 0) + count
        // New pickups can fill a free shortcut, without replacing the player's assignments.
        val targetBar = hotbar
        if (newStack && targetBar.none { it == dropType }) {
            val emptySlot = targetBar.indexOfFirst { it == null }
            if (emptySlot != -1) {
                targetBar[emptySlot] = dropType
                hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            }
        }
        inventoryCallback?.invoke(inventory.toMap())
    }

    private fun clearUnsupportedAround(x: Int, y: Int, z: Int) {
        val pending = java.util.ArrayDeque<Triple<Int, Int, Int>>()
        pending.add(Triple(x, y, z))
        var budget = 256
        while (pending.isNotEmpty() && budget-- > 0) {
            val (cx, cy, cz) = pending.removeFirst()
            for ((nx, ny, nz) in listOf(Triple(cx, cy + 1, cz), Triple(cx - 1, cy, cz),
                Triple(cx + 1, cy, cz), Triple(cx, cy, cz - 1), Triple(cx, cy, cz + 1))) {
                val id = world.blockAt(nx, ny, nz)
                val def = BlockRegistry.get(id) ?: continue
                if (def.placementRule == "any") continue
                if (com.Atom2Universe.app.games.caves.world.BlockPlacement.supported(id, nx, ny, nz, world.metaAt(nx, ny, nz)) { a, b, c -> world.blockAt(a, b, c) }) continue
                val farmDrops = farming.harvest(nx, ny, nz, uproot = true)
                if (!isCreative && farmDrops != null) grantFarmItems(farmDrops)
                world.setBlock(nx, ny, nz, AIR)
                forceMeshRebuild(nx, ny, nz)
                if (!isCreative) collectBlock(id)
                pending.add(Triple(nx, ny, nz))
            }
        }
    }

    private fun refreshChunkLightSources(chunk: Chunk) {
        val key = world.chunkKey(chunk.cx, chunk.cy, chunk.cz)
        val previous = chunkLightStates[key]
        if (previous?.chunk === chunk && previous.version == chunk.version) return
        lightsDirty = true
        val wx0 = chunk.worldX; val wy0 = chunk.worldY; val wz0 = chunk.worldZ
        previous?.positions?.forEach { lightSources.remove(it) }
        val positions = ArrayList<Triple<Int, Int, Int>>()
        for (ly in 0 until CHUNK_SIZE)
            for (lz in 0 until CHUNK_SIZE)
                for (lx in 0 until CHUNK_SIZE) {
            val block=chunk.blockAt(lx,ly,lz)
            if(worldSource==null && block.toInt() in 9800..9891) {
                workshops.discover(FrontierWorkshops.Pos(wx0+lx,wy0+ly,wz0+lz),block)
                if(block==com.Atom2Universe.app.games.caves.node.FrontierItems.MARKET_BELL) residents.discoverBell(wx0+lx,wy0+ly,wz0+lz)
            }
            val intensity = when (block) {
                TORCH -> 1.0f
                LAVA  -> 0.85f
                else  -> BlockRegistry.lightEmission(block) / 15f
            }
            if (intensity > 0f) {
                val position = Triple(wx0 + lx, wy0 + ly, wz0 + lz)
                positions.add(position)
                lightSources[position] = intensity
            }
        }
        chunkLightStates[key] = ChunkLightState(chunk, chunk.version, positions)
        if (positions.isNotEmpty() || previous?.positions?.isNotEmpty() == true) localLights.clear()
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
            val version=chunk.version
            val r = LightEngine.computeSky(chunk, world) or LightEngine.computeBlock(chunk, world)
            if(r==0 && version==chunk.version) chunk.ecologyLightReady=true
            else if(version!=chunk.version) world.enqueueLight(key)
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
            } else if (chunk.lightMeshDirty) {
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
                    if (nb.generated) {
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
            meshes.getOrPut(key) { ChunkMesh(11) }.also { it.upload(verts); it.flushPending() }
            if (waterVerts.isNotEmpty()) waterMeshes.getOrPut(key) { ChunkMesh(7) }.also { it.upload(waterVerts); it.flushPending() }
            else waterMeshes.remove(key)?.destroy()
            refreshChunkLightSources(chunk)
            if (ncy in 0..world.surfaceChunkMax) {
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
        val startY = if (camera.thirdPerson) camera.orbitY else camera.y
        val startZ = if (camera.thirdPerson) camera.playerZ else camera.z

        return raycastBlock(startX, startY, startZ, dirX, dirY, dirZ, MINE_REACH.toDouble())
    }

    /** Called on the GL thread; unproject the touched pixel using the actual rendered camera. */
    fun placeBlockAtScreen(xf: Float, yf: Float) {
        if (!mode.allowsWorldEdits || heldItemMode == HotbarMode.COMBAT) return
        if (!xf.isFinite() || !yf.isFinite() || xf !in 0f..1f || yf !in 0f..1f) return
        val inverse = FloatArray(16)
        if (!android.opengl.Matrix.invertM(inverse, 0, camera.vpMatrix, 0)) return
        val point = FloatArray(4)
        android.opengl.Matrix.multiplyMV(point, 0, inverse, 0,
            floatArrayOf(2f * xf - 1f, 1f - 2f * yf, -1f, 1f), 0)
        if (point[3] == 0f) return
        // Floating origin: the unprojected point is relative to the rendering eye.
        val dx = (point[0] / point[3]).toDouble()
        val dy = (point[1] / point[3]).toDouble()
        val dz = (point[2] / point[3]).toDouble()
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (!length.isFinite() || length <= 0.0) return
        val orbitDistance = sqrt(
            (camera.x - camera.playerX).let { it * it } +
            (camera.y - camera.playerY).let { it * it } +
            (camera.z - camera.playerZ).let { it * it })
        if(interactActor(camera.x,camera.y,camera.z,dx/length,dy/length,dz/length)) return
        if(hotbar[selectedSlot]==com.Atom2Universe.app.games.caves.node.FrontierItems.CHARM) { placeBlock(null,true);return }
        val target = raycastBlock(camera.x, camera.y, camera.z,
            dx / length, dy / length, dz / length, MINE_REACH + orbitDistance) ?: return
        // A third-person camera must not extend the player's building reach.
        val px = camera.playerX.coerceIn(target.bx.toDouble(), target.bx + 1.0)
        val py = camera.playerY.coerceIn(target.by.toDouble(), target.by + 1.0)
        val pz = camera.playerZ.coerceIn(target.bz.toDouble(), target.bz + 1.0)
        val distanceSquared = (px - camera.playerX).let { it * it } +
            (py - camera.playerY).let { it * it } + (pz - camera.playerZ).let { it * it }
        if (distanceSquared > MINE_REACH * MINE_REACH) return
        placeBlock(target,true)
    }

    /** Expanded crop sprites can extend outside their base voxel; stop them at solid occluders. */
    private fun raycastBlock(startX: Double, startY: Double, startZ: Double,
        dirX: Double, dirY: Double, dirZ: Double, reach: Double,
        includeWater: Boolean = hotbar[selectedSlot] == BUCKET_EMPTY): RayHit? {
        var best = raycastVoxel(startX,startY,startZ,dirX,dirY,dirZ,reach,includeWater)
        var distance = best?.distance ?: reach
        if (!mode.allowsWorldEdits) return best
        for (plant in farming.nearby(startX,startY,startZ,reach)) {
            val p=plant.position; val id=worldBlockAt(p.x,p.y,p.z)
            if (com.Atom2Universe.app.games.caves.node.FarmShowcasePlants.sample(id)?.first != plant.crop) continue
            val hit = BlockRegistry.decorationMask(id)?.hitDistance(startX-p.x,
                startY-p.y-plantSoilOffset(p.x,p.y,p.z),startZ-p.z,dirX,dirY,dirZ,
                BlockRegistry.getSpriteMargin(id).toDouble(),BlockRegistry.getSpriteHeight(id).toDouble(),
                0.0,distance) ?: continue
            distance=hit
            best=RayHit(p.x,p.y,p.z,0,1,0).apply { this.distance=hit; hitY=startY+dirY*hit-p.y }
        }
        return best
    }
    private fun raycastVoxel(
        startX: Double, startY: Double, startZ: Double,
        dirX: Double, dirY: Double, dirZ: Double, reach: Double,
        includeWater: Boolean = hotbar[selectedSlot] == BUCKET_EMPTY,
    ): RayHit? {
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
        var entryDistance = 0.0

        repeat(ceil(reach * 3).toInt() + 3) {
            val b = worldBlockAt(bx, by, bz)
            if (b != AIR && (!isWater(b) || includeWater && b == WATER)) {
                if ((BlockRegistry.get(b)?.stairs == true || BlockRegistry.get(b)?.slab == true || (BlockRegistry.get(b)?.blockHeight ?: 1f) < 1f)) {
                    val stairHit = PartialBlockModel.intersect(world.metaAt(bx, by, bz),
                        startX-bx, startY-by, startZ-bz, dirX, dirY, dirZ, reach, BlockRegistry.get(b)?.slab == true, BlockRegistry.get(b)?.blockHeight ?: 1f, stairMaskAt(bx, by, bz))
                    if (stairHit != null) return RayHit(bx, by, bz, stairHit.nx, stairHit.ny, stairHit.nz).apply {
                        distance = stairHit.distance; hitY = startY + dirY * stairHit.distance - by
                    }
                }
                val hit = if ((BlockRegistry.get(b)?.stairs == true || BlockRegistry.get(b)?.slab == true || (BlockRegistry.get(b)?.blockHeight ?: 1f) < 1f)) false else if (b == TORCH) TorchModel.intersects(world.metaAt(bx, by, bz),
                    startX - bx, startY - by, startZ - bz, dirX, dirY, dirZ,
                    entryDistance, minOf(reach, tMaxX, tMaxY, tMaxZ))
                else !isDecoration(b) || BlockRegistry.decorationMask(b)?.intersects(
                    startX - bx, startY - by - plantSoilOffset(bx, by, bz), startZ - bz, dirX, dirY, dirZ,
                    BlockRegistry.getSpriteMargin(b).toDouble(), BlockRegistry.getSpriteHeight(b).toDouble(),
                    entryDistance, minOf(reach, tMaxX, tMaxY, tMaxZ),
                ) == true
                if (hit) return RayHit(bx, by, bz, fnx, fny, fnz).apply { distance = entryDistance; hitY = startY + dirY * entryDistance - by }
            }
            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> {
                    if (tMaxX > reach) return null
                    entryDistance = tMaxX
                    fnx = -stepX; fny = 0; fnz = 0; bx += stepX; tMaxX += tDX
                }
                tMaxY <= tMaxZ -> {
                    if (tMaxY > reach) return null
                    entryDistance = tMaxY
                    fnx = 0; fny = -stepY; fnz = 0; by += stepY; tMaxY += tDY
                }
                else -> {
                    if (tMaxZ > reach) return null
                    entryDistance = tMaxZ
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
                clearUnsupportedAround(fb.wx, fb.wy, fb.wz)
                clearUnsupportedAround(fb.wx, fb.wy - 1, fb.wz)
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

        val block = worldBlockAt(target.bx, target.by, target.bz)
        if ((BlockRegistry.get(block)?.stairs == true || BlockRegistry.get(block)?.slab == true || (BlockRegistry.get(block)?.blockHeight ?: 1f) < 1f)) {
            val vertices = ArrayList<Float>()
            val normals = arrayOf(floatArrayOf(0f,ep,0f), floatArrayOf(0f,-ep,0f),
                floatArrayOf(ep,0f,0f), floatArrayOf(-ep,0f,0f), floatArrayOf(0f,0f,ep), floatArrayOf(0f,0f,-ep))
            for (face in PartialBlockModel.faces(world.metaAt(target.bx, target.by, target.bz), BlockRegistry.get(block)?.slab == true, BlockRegistry.get(block)?.blockHeight ?: 1f, stairMaskAt(target.bx, target.by, target.bz))) {
                val n = normals[face.direction]
                for (i in intArrayOf(0,1,2,0,2,3)) {
                    val v = face.vertices[i]
                    vertices.addAll(listOf(x+v[0]+n[0], y+v[1]+n[1], z+v[2]+n[2], cr,cg,cb))
                }
            }
            return vertices.toFloatArray()
        }
        if (block == TORCH) return TorchModel.highlight(world.metaAt(target.bx, target.by, target.bz),
            x, y, z, cr, cg, cb)
        if (isDecoration(block)) {
            return BlockRegistry.decorationMask(block)?.highlight(x, y + plantSoilOffset(target.bx, target.by, target.bz), z,
                BlockRegistry.getSpriteMargin(block), BlockRegistry.getSpriteHeight(block), cr, cg, cb)
                ?: floatArrayOf()
        }

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
        val headY = camera.orbitY
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
        val moving = hypot(camera.playerX-walkLastX, camera.playerZ-walkLastZ) > .001
        if (moving && !gamePaused) walkPhase += dt*7.5f
        walkLastX = camera.playerX; walkLastZ = camera.playerZ
        val crouchDrop = if (physics.isCrouching && playerMode == PlayerMode.WALK) physics.eyeDrop.toFloat() else 0f
        val step = if (moving) sin(walkPhase) * (if (crouchDrop > 0f) .07f else .24f) else 0f
        val m = equipmentMesh
        m.clear()
        // Explorateur : veste, ceinture, bottes, sac et visage.
        val armorColor=when(expeditionCombat.armor) { E.PADDED_ARMOR -> 0xAC835C; E.IRON_ARMOR -> 0xA3AFB6; E.STEEL_ARMOR -> 0x73AFBF; else -> 0x435B78 }
        m.box(0f,-.59f,0f,.22f,.29f,.13f,armorColor)
        m.box(0f,-.86f,0f,.225f,.035f,.14f,0x49382B)
        m.box(0f,-.85f,-.15f,.035f,.028f,.012f,0xC6AA67)
        m.box(0f,-.18f,0f,.18f,.20f,.17f,0xD5A17C)
        m.box(0f,.015f,0f,.19f,.045f,.18f,0x49392C)
        m.box(0f,-.14f,.15f,.185f,.15f,.035f,0x49392C)
        for (side in listOf(-1f,1f)) {
            m.box(side*.073f,-.15f,-.174f,.035f,.028f,.008f,0xEEE9DE)
            m.box(side*.073f,-.15f,-.184f,.015f,.019f,.006f,0x263B43)
            m.box(side*.15f,-.58f,-.145f,.026f,.25f,.017f,0x896C44)
            val kneeY = -1.25f + crouchDrop * .88f
            val kneeZ = side * step - crouchDrop * .47f
            m.rod(side*.115f,-.9f,0f,side*.115f,kneeY,kneeZ,.103f,0x293C53,.086f)
            m.rod(side*.115f,kneeY,kneeZ,side*.115f,-1.53f+crouchDrop,side*step*.8f,.085f,0x293C53,.072f)
            m.box(side*.115f,-1.565f+crouchDrop,side*step*.8f-.035f,.09f,.055f,.145f,0x332F2C)
        }
        m.box(0f,-.57f,.205f,.18f,.235f,.075f,0x786042)
        m.box(0f,-.66f,.29f,.12f,.09f,.024f,0x9C8055)
        val type = selectedEquipmentType()
        val throwing = rockChargeTime > 0f || (releasedEquipment == "rock" && equipmentRelease >= 0f)
        val active = type != null || throwing || (heldItemMode == HotbarMode.COMBAT && hotbar[selectedSlot] in ROCK_IDS)
        if (!active) for (side in listOf(-1f,1f)) {
            m.rod(side*.27f,-.38f,0f,side*.31f,-.67f,-side*step,.080f,0x435B78,.061f)
            m.rod(side*.31f,-.67f,-side*step,side*.32f,-.94f,-side*step,.059f,0xD5A17C,.045f)
            m.hand(side*.32f,-.97f,-side*step)
        }
        android.opengl.Matrix.setIdentityM(equipmentModel,0)
        // Droite caméra, haut, arrière : les armes pointent vers -Z local.
        equipmentModel[0] = -camera.fwdZ; equipmentModel[2] = camera.fwdX
        equipmentModel[8] = -camera.fwdX; equipmentModel[10] = -camera.fwdZ
        equipmentModel[12] = (camera.playerX-camera.x).toFloat()
        equipmentModel[13] = (camera.playerY-camera.y).toFloat()
        equipmentModel[14] = (camera.playerZ-camera.z).toFloat()
        // Lower the intact torso; bent knees and raised local feet compensate this translation.
        android.opengl.Matrix.translateM(equipmentModel, 0, 0f, -crouchDrop, 0f)
        drawEquipmentMesh(equipmentModel,camera.vpMatrix)
        if (active) {
            android.opengl.Matrix.translateM(equipmentModel,0,0f,-.38f,0f)
            android.opengl.Matrix.rotateM(equipmentModel,0,-camera.pitch,1f,0f,0f)
            android.opengl.Matrix.translateM(equipmentModel,0,if(type == "dual_pistols") 0f else .27f,.03f,-.43f)
            drawEquipment(type,throwing || type == null,false)
        }
    }

    // ── Viewmodel 1re personne (bras + objet tenu) ────────────────────────────

    /** Déclenche une animation de swing (un coup aller-retour). */
    internal fun startSwing() { swingActive = true; swingTimer = 0f }

    private fun drawViewmodel(dt: Float) {
        if (camera.thirdPerson) return

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


        val type = selectedEquipmentType()
        val throwing = rockChargeTime > 0f || (releasedEquipment == "rock" && equipmentRelease >= 0f)
        if (type != null || throwing || (heldItemMode == HotbarMode.COMBAT && (held == null || held in ROCK_IDS))) {
            android.opengl.Matrix.setIdentityM(equipmentModel,0)
            val charge = (weaponChargeTime / WEAPON_CHARGE_VISUAL_MAX).coerceIn(0f,1f)
            val recoil = if (equipmentRelease >= 0f) exp(-equipmentRelease*15f)*.065f else 0f
            val offsetX = when(type) {
                "dual_pistols" -> 0f // Repère central pour une arme de chaque côté.
                "crossbow" -> .24f
                else -> .34f
            }
            val offsetY = when(type) {
                "bow" -> -.16f
                "sling" -> -.38f
                "crossbow" -> -.34f
                else -> -.28f
            }
            android.opengl.Matrix.translateM(equipmentModel,0,offsetX-charge*.015f,
                offsetY,(if(type == "bow") -.85f else -.72f)+recoil)
            android.opengl.Matrix.rotateM(equipmentModel,0,recoil*95f,1f,0f,0f)
            drawEquipment(type,throwing || type == null,true)
            return
        }
        drawArm(isWeapon)

        if (held != null) {
            when {
                isWeapon                     -> drawHeldWeapon(held)
                BlockRegistry.isDecoration(held) || com.Atom2Universe.app.games.caves.node.FarmItems.isItem(held) -> drawHeldFlat(held)
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

    private val equipmentMesh = HeldEquipmentMesh()
    private val equipmentModel = FloatArray(16)
    private var equipmentRelease = -1f
    private var releasedEquipment: String? = null

    /** Called on the GL thread, like firing and magazine updates. */
    fun reloadAssaultWeapon() {
        if (!mode.allowsCombat || gamePaused) return
        if(mode.allowsWorldEdits) {
            val held=hotbar[selectedSlot]
            if(held==E.ROD) { fishing.cancel();return }
            if(heldItemMode==HotbarMode.COMBAT && (held in E.melee || expeditionCombat.shield && (RangedProfile.all[selectedEquipmentType()]?.magazine ?: 0)==0)) {
                expeditionCombat.raiseGuard();return
            }
        }
        val profile = RangedProfile.all[selectedEquipmentType()] ?: return
        val id = hotbar[selectedSlot] ?: return
        if (profile.magazine <= 0) return
        reloadWithSound(magazine(id,profile))
        publishWeaponStatus()
    }

    private fun reloadWithSound(magazine: MagazineState) {
        val wasReloading = magazine.reloadRemaining > 0f
        magazine.reload()
        if (!wasReloading && magazine.reloadRemaining > 0f) {
            eventBus.publish(GameEvent.WeaponReload(complete = false, weaponType = selectedEquipmentType() ?: ""))
        }
    }

    private fun publishWeaponStatus() {
        val type=selectedEquipmentType()
        val profile=RangedProfile.all[type]
        val id=hotbar[selectedSlot]
        val reserve=ammoBlockIdFor(type)?.let { inventory[it] ?: 0 } ?: 0
        val mag=if(id!=null && profile!=null && profile.magazine>0) magazine(id,profile) else null
        val status=if(mode.allowsWorldEdits && id==E.ROD) fishing.status()
            else if(mode.allowsWorldEdits && heldItemMode==HotbarMode.COMBAT && expeditionCombat.guard>0f) context.getString(com.Atom2Universe.app.R.string.cave_guard_status,expeditionCombat.stamina.toInt())
            else if(mode.allowsWorldEdits && heldItemMode==HotbarMode.COMBAT && id in E.melee) context.getString(com.Atom2Universe.app.R.string.cave_melee_status,expeditionCombat.stamina.toInt())
            else if(mode.allowsWorldEdits && id!=null && E.isEquipment(id)) context.getString(com.Atom2Universe.app.R.string.cave_equipment_use)
            else if(heldItemMode==HotbarMode.COMBAT && profile!=null && profile.magazine==0) context.getString(com.Atom2Universe.app.R.string.cave_ranged_charge, (weaponChargeTime/.9f*100).toInt().coerceIn(0,100),reserve)
            else if(heldItemMode!=HotbarMode.COMBAT || mag==null) ""
            else if(mag.reloadRemaining>0f) context.getString(com.Atom2Universe.app.R.string.cave_weapon_reloading)
            else if(mode.infiniteAmmo) context.getString(com.Atom2Universe.app.R.string.cave_assault_magazine,
                mag.remaining, profile?.magazine ?: 0)
            else context.getString(com.Atom2Universe.app.R.string.cave_weapon_magazine,minOf(mag.remaining,reserve),reserve)
        if(status!=lastWeaponStatus) { lastWeaponStatus=status;weaponStatusCallback?.invoke(status) }
    }

    private fun selectedEquipmentType(): String? {
        val id = hotbar[selectedSlot] ?: return if(mode.allowsWorldEdits && expeditionCombat.shield && expeditionCombat.guard>0f) "buckler" else null
        E.melee[id]?.let { return it.type }
        if(id==E.ROD) return "fishing_rod"
        val instance = com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(id) ?: return null
        return ItemRegistry.get(instance.defId)?.weaponType
    }

    private fun updateEquipmentAnimation(dt: Float) {
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
        if (equipmentRelease >= 0f) {
            equipmentRelease += dt
            if (equipmentRelease > .55f) { equipmentRelease = -1f; releasedEquipment = null }
        }
    }

    private fun drawEquipment(type: String?, rock: Boolean, fps: Boolean) {
        val m = equipmentMesh
        m.clear()
        val release = if (releasedEquipment == (if(rock) "rock" else type)) equipmentRelease else -1f
        val charge = (weaponChargeTime / WEAPON_CHARGE_VISUAL_MAX).coerceIn(0f,1f)
        val rockCharge = (rockChargeTime / ROCK_CHARGE_MAX).coerceIn(0f,1f)
        val ammo = ammoBlockIdFor(type)
        val loaded = if (rock) ROCK_IDS.any { (inventory[it] ?: 0) > 0 }
            else ammo != null && (inventory[ammo] ?: 0) > 0 && release < 0f
        val instance = hotbar[selectedSlot]?.let { com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(it) }
        val accent = when(instance?.rarity) {
            com.Atom2Universe.app.games.caves.node.ItemRarity.MAGIC -> 0x60B6E3
            com.Atom2Universe.app.games.caves.node.ItemRarity.RARE -> 0xE5C36A
            com.Atom2Universe.app.games.caves.node.ItemRarity.EPIC -> 0xB889E8
            com.Atom2Universe.app.games.caves.node.ItemRarity.LEGENDARY -> 0xF5A04A
            else -> 0xBFA779
        }
        val mag=hotbar[selectedSlot]?.let { magazines[it] }
        val reload=mag?.progress ?: 0f
        val dip=sin(reload*PI.toFloat())
        android.opengl.Matrix.rotateM(equipmentModel,0,dip*28f,0f,0f,1f)
        android.opengl.Matrix.translateM(equipmentModel,0,0f,-dip*.12f,0f)
        if(type in setOf("sword","spear","hammer")) {
            val wind=expeditionCombat.charge/.9f
            val swing=if(release>=0f) sin((release/.5f).coerceIn(0f,1f)*PI.toFloat()) else 0f
            android.opengl.Matrix.rotateM(equipmentModel,0,wind*35f-swing*75f,1f,0f,0f)
            android.opengl.Matrix.rotateM(equipmentModel,0,if(type=="sword") swing*55f else 0f,0f,0f,1f)
            if(expeditionCombat.guard>0f) android.opengl.Matrix.rotateM(equipmentModel,0,-65f,0f,0f,1f)
        }
        val bladeColor=when(hotbar[selectedSlot]) {
            E.WOOD_SWORD -> 0xB28A50; E.STONE_SPEAR -> 0x888F91
            E.STEEL_SWORD, E.STEEL_HAMMER -> 0x83CBD1; else -> null
        }
        m.pose(type,rock,fps,charge,rockCharge,release,loaded,accent,reload,mag?.shots ?: 0,bladeColor)
        if(mode.allowsWorldEdits && expeditionCombat.shield && expeditionCombat.guard>0f) {
            m.box(-.28f,.12f,-.02f,.17f,.22f,.035f,0x946E4D)
            m.box(-.28f,.12f,-.06f,.022f,.22f,.014f,0xBBC9C6)
        }
        drawEquipmentMesh(equipmentModel,if(fps) vmProj else camera.vpMatrix)
        if (fps && type == "sling" && !rock) {
            m.slingDrawHand(charge,release)
            drawEquipmentMesh(equipmentModel,vmProj,0.32f)
        }
    }

    private fun drawFishingLine() {
        val b=fishing.bobber ?: return
        val m=equipmentMesh;m.clear()
        val bx=(b.x-camera.x).toFloat();val by=(b.y-camera.y).toFloat();val bz=(b.z-camera.z).toFloat()
        val bob=if(fishing.biting) -.12f+sin(elapsed*24)*.06f else sin(elapsed*3)*.025f
        m.rod(bx,by+bob-.07f,bz,bx,by+bob+.07f,bz,.042f,0xF2E4BC)
        m.box(bx,by+bob+.065f,bz,.035f,.035f,.035f,0xE76543)
        m.rod((camera.playerX-camera.x).toFloat(),(camera.eyeY-camera.y-.2).toFloat(),(camera.playerZ-camera.z).toFloat(),bx,by+bob+.08f,bz,.003f,0xD7DBCB)
        android.opengl.Matrix.setIdentityM(equipmentModel,0)
        drawEquipmentMesh(equipmentModel,camera.vpMatrix)
    }

    private fun drawEquipmentMesh(model: FloatArray, projection: FloatArray, alpha: Float = 1f) {
        val m = equipmentMesh
        m.buffer.clear(); m.buffer.put(m.vertices,0,m.count); m.buffer.flip()
        android.opengl.Matrix.multiplyMM(vmMvp,0,projection,0,model,0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vmVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,m.count*4,m.buffer,GLES30.GL_DYNAMIC_DRAW)
        laserShader?.use()
        GLES30.glUniformMatrix4fv(lUMvp,1,false,vmMvp,0)
        GLES30.glUniform1f(lUAlpha,alpha)
        GLES30.glEnableVertexAttribArray(lAPos)
        GLES30.glVertexAttribPointer(lAPos,3,GLES30.GL_FLOAT,false,24,0)
        GLES30.glEnableVertexAttribArray(lAColor)
        GLES30.glVertexAttribPointer(lAColor,3,GLES30.GL_FLOAT,false,24,12)
        if (alpha < 1f) {
            // Pré-passe profondeur : seule la face visible de la main contribue à l'alpha.
            GLES30.glColorMask(false,false,false,false)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,m.count/6)
            GLES30.glColorMask(true,true,true,true)
            GLES30.glDepthFunc(GLES30.GL_EQUAL)
            GLES30.glDepthMask(false)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA)
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,m.count/6)
        if (alpha < 1f) {
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDepthMask(true)
            GLES30.glDepthFunc(GLES30.GL_LESS)
        }
        GLES30.glUniform1f(lUAlpha,1f)
        GLES30.glDisableVertexAttribArray(lAPos); GLES30.glDisableVertexAttribArray(lAColor)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0)
    }

    /** Texture GL d'un sprite d'item (cache par nom ; 0 si introuvable). */
    private fun itemTexture(sprite: String): Int {
        itemTexCache[sprite]?.let { return it }
        val tex = runCatching {
            val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); val t = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
            val bmp = runCatching {
                context.assets.open("caves/items/$sprite.png").use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: context.assets.open("caves/weapon_icons/$sprite.png").use { BitmapFactory.decodeStream(it) }
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
        // Une carte préparée tient entière dans la distance de vue : le LOD lointain ne sert à rien.
        if (worldSource != null) return
        val key = lodKey(cx, cz)
        lodRebuildRequested.add(key)
        if (!lodBuilding.add(key)) return
        scope.launch(lodDispatcher) {
            try {
                do {
                    lodRebuildRequested.remove(key)
                    val verts = LodBuilder.buildColumn(cx, cz, world, lodCache)
                    if (verts.isNotEmpty() && lodUploadQueueSize.get() < 64) {
                        lodUploadQueueSize.incrementAndGet()
                        lodUploadQueue.add(Pair(key, verts))
                    }
                    // Conserver une édition/génération survenue pendant le calcul.
                } while (isActive && lodRebuildRequested.contains(key))
            } finally {
                lodBuilding.remove(key)
                if (scope.isActive && lodRebuildRequested.contains(key)) scheduleLodBuild(cx, cz)
            }
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
        val day    = if (vividStyle) Triple(0.39f, 0.76f, 0.97f) else Triple(0.682f, 0.910f, 0.973f)
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

    private fun loadSkyTexture(assetPath: String): Int =
        uploadSkyTexture(context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) })

    private fun uploadSkyTexture(bmp: android.graphics.Bitmap): Int {
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); val texId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
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
        sunTex  = uploadSkyTexture(com.Atom2Universe.app.graphics.SunArtwork.createBitmap())
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
    private var prevCrouching = false
    private var footstepQuietTime = 0.0
    private var footstepsMoving = false
    private var animalSoundTimer = 6f
    private val audioRandom = Random(worldSeed xor 9152026L)

    private fun updateAnimalAudio(dt: Float) {
        animalSoundTimer -= dt
        if (animalSoundTimer > 0f) return
        animalSoundTimer = 7f + audioRandom.nextFloat() * 7f
        val nearby = passiveAnimals.visible.filter { animal ->
            val dx = animal.x - camera.playerX
            val dz = animal.z - camera.playerZ
            abs(animal.y - (camera.playerY - 1.62)) < 4 && dx * dx + dz * dz < 18 * 18
        }
        if (nearby.isEmpty()) return
        val animal = nearby[audioRandom.nextInt(nearby.size)]
        val dx = animal.x - camera.playerX
        val dz = animal.z - camera.playerZ
        val distance = hypot(dx, dz)
        // Muffle a call behind terrain; no loud farm voices through a cave ceiling.
        val raySteps = (distance * 2).toInt().coerceAtLeast(1)
        val blocked = (1 until raySteps).any { step ->
            val fraction = step.toDouble() / raySteps
            val block = worldBlockAt(floorInt(camera.playerX + dx * fraction),
                floorInt(camera.playerY + (animal.y + .7 - camera.playerY) * fraction),
                floorInt(camera.playerZ + dz * fraction))
            block != AIR && !isDecoration(block) && !isWater(block)
        }
        val yaw = Math.toRadians(camera.yaw.toDouble())
        val pan = ((-cos(yaw) * dx + sin(yaw) * dz) / distance.coerceAtLeast(1.0)).toFloat() * .65f
        eventBus.publish(GameEvent.AnimalCall(animal.def.id,
            (.45f * (1f - distance.toFloat() / 18f) * if (blocked) .2f else 1f).coerceIn(0f, .45f), pan))
    }

    private fun updateWalk(dt: Float) {
        val yawRad = Math.toRadians(camera.yaw.toDouble())
        val fX = sin(yawRad).toFloat(); val fZ = cos(yawRad).toFloat()
        val rX = cos(yawRad).toFloat(); val rZ = -sin(yawRad).toFloat()
        val chargeMul = if (rockChargeTime > 0f) 0.55f else 1f

        physics.updateCrouch(touch.crouchRequested, camera.playerX, camera.playerY, camera.playerZ)
        if (physics.isCrouching) touch.cancelSprint()
        if (physics.isCrouching != prevCrouching) {
            prevCrouching = physics.isCrouching
            crouchCallback?.invoke(prevCrouching)
        }
        physics.isSprinting = touch.sprintRequested && physics.onGround && !physics.isCrouching
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

        // Send movement state, never individual beats. SoundPool loops on its audio clock.
        val travelled = hypot(newX - camera.playerX, newZ - camera.playerZ)
        val input = hypot(touch.moveForward, touch.moveRight).coerceAtMost(1f)
        val feetBlock = worldBlockAt(floorInt(newX), floorInt(newY - 1.62 + .10), floorInt(newZ))
        val eligible = !physics.isCrouching && input > .03f && !isWater(feetBlock)
        val movingOnGround = physics.onGround && travelled > .0001 && travelled < 2
        footstepQuietTime = if (movingOnGround) 0.0 else footstepQuietTime + dt
        if (eligible && (movingOnGround || (footstepsMoving && footstepQuietTime < .12))) {
            footstepsMoving = true
            // Stable intended speed avoids collision/frame jitter changing the tempo.
            // Actual travel still gates playback, so pushing a wall never sustains steps.
            val speed = (if (nowSprinting) skillBook.sprintSpeed else
                com.Atom2Universe.app.games.caves.entity.SkillBook.BASE_WALK_SPEED) * input * chargeMul
            val stride = 1.6 + (speed - 3.5).coerceAtLeast(0.0) * .10
            val interval = (stride / speed.coerceAtLeast(.1)).coerceIn(.23, .92).toFloat()
            val ground = worldBlockAt(floorInt(newX), floorInt(newY - 1.62 - .08), floorInt(newZ))
            val surface = when (ground) {
                GRASS, DIRT, DIRT_SAND, DIRT_SNOW, SAND, SNOW, FOREST_FLOOR, MOSS -> "earth"
                in WOOD..PLANK_SAPIN -> "wood"
                else -> "stone"
            }
            // During a short ground-contact gap keep the current loop/rate untouched.
            if (movingOnGround) eventBus.publish(GameEvent.Footstep(surface, nowSprinting, true, interval))
        } else {
            footstepsMoving = false
            eventBus.publish(GameEvent.Footstep())
        }

        // XP Speed : distance parcourue au sol
        if (physics.onGround) {
            val dx = newX - camera.playerX; val dz = newZ - camera.playerZ
            val dist = sqrt(dx * dx + dz * dz).toFloat()
            val rate = if (nowSprinting) 0.3f else 0.1f
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
        footstepsMoving = false
        eventBus.publish(GameEvent.Footstep())
        footstepQuietTime = 0.0
        touch.crouchLatched = false
        touch.cancelSprint()
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
        generationJobs.incrementAndGet()
        scope.launch(genDispatcher) {
            try {
                world.generate(chunk)
                world.markGenerated(chunk)
            } catch (_: Throwable) {
                // Libère le chunk (OOM inclus) pour qu'il puisse être regénéré
                world.abandonChunk(chunk)
            } finally {
                generationJobs.decrementAndGet()
                building.remove(key)
            }
        }
    }

    fun selectSlot(index: Int) {
        if (mode.singleWeapon && index != 0) return
        if (index !in hotbar.indices) return
        selectedSlot = index
        hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
    }

    // Détermine le meta d'orientation au moment du placement.
    // AXIS  : axe dérivé de la face cliquée (X si paroi E/W, Z si paroi N/S, Y sinon).
    // FACING: direction vers le joueur depuis la face cliquée ; sur sol/plafond → yaw caméra.
    private fun computeOrientMeta(blockType: Short, fnx: Int, fny: Int, fnz: Int): Byte {
        if (blockType == TORCH) return TorchModel.orientation(fnx, fny, fnz)
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

    private fun plantSoilOffset(x: Int, y: Int, z: Int): Float =
        if (worldBlockAt(x, y - 1, z) == com.Atom2Universe.app.games.caves.node.FarmSoil.FARMLAND) -1f / 16f else 0f

    private fun grantFarmItems(items: List<Pair<Short, Int>>) {
        for ((id,count) in items) {
            val newStack=(inventory[id] ?: 0)==0
            inventory[id] = ((inventory[id] ?: 0).toLong()+count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (newStack && id !in hotbar) {
                val empty = hotbar.indices.firstOrNull { hotbar[it] == null }
                if (empty != null) hotbar[empty] = id
            }
        }
        inventoryCallback?.invoke(inventory.toMap())
        hotbarCallback?.invoke(hotbar.copyOf(),selectedSlot)
    }
    private fun consumeFarmItem(id: Short) {
        if (isCreative) return
        val left=(inventory[id] ?: 0)-1
        if (left > 0) inventory[id]=left else {
            inventory.remove(id)
            for (i in hotbar.indices) if (hotbar[i] == id) hotbar[i]=null
        }
        inventoryCallback?.invoke(inventory.toMap())
        hotbarCallback?.invoke(hotbar.copyOf(),selectedSlot)
    }

    internal data class TradeView(val key: String,val role: Int,val offers: List<FrontierLife.Offer>,val remaining: List<Int>,val items: Map<Short,Int>)
    internal fun tradeView(key: String): TradeView? {
        val r=residents.nearby(key,camera.playerX,camera.playerY,camera.playerZ) ?: return null
        val offers=frontierLife.offers(r.role)
        return TradeView(key,r.role,offers,offers.indices.map { frontierLife.remaining(key,it,frontierLife.elapsedMs) },inventory.toMap())
    }
    internal fun trade(key: String,index: Int): Boolean {
        if(worldSource!=null || !mode.allowsWorldEdits || !playerNode.isAlive) return false
        val r=residents.nearby(key,camera.playerX,camera.playerY,camera.playerZ) ?: return false
        if(!frontierLife.trade(key,r.role,index,inventory,frontierLife.elapsedMs)) return false
        changedFrontierInventory();return true
    }
    internal fun changedFrontierInventory() {
        for(i in hotbar.indices) {
            val id=hotbar[i] ?: continue
            if((inventory[id] ?: 0)<=0) hotbar[i]=null
        }
        inventoryCallback?.invoke(inventory.toMap());hotbarCallback?.invoke(hotbar.copyOf(),selectedSlot)
    }
    private fun interactActor(sx: Double,sy: Double,sz: Double,dx: Double,dy: Double,dz: Double): Boolean {
        if(worldSource!=null || !mode.allowsWorldEdits || !playerNode.isAlive) return false
        val solid=raycastBlock(sx,sy,sz,dx,dy,dz,12.0)?.distance ?: 12.0
        val actors=passiveAnimals.visible+residents.visible
        var selected: com.Atom2Universe.app.games.caves.entity.Enemy?=null
        var nearest=solid
        for(a in actors) {
            if(hypot(a.x-camera.playerX,a.z-camera.playerZ)>4.5 || abs(a.y-camera.playerY)>4) continue
            val height=if(a.def.behavior=="settler") 1.8 else if(a.young) .8 else 1.25
            val radius=if(a.def.behavior=="settler") .4 else (a.def.radius*(if(a.young) .65 else .85)).coerceAtLeast(.3)
            var enter=0.0;var leave=nearest
            val origin=doubleArrayOf(sx,sy,sz);val direction=doubleArrayOf(dx,dy,dz)
            val low=doubleArrayOf(a.x-radius,a.y,a.z-radius);val high=doubleArrayOf(a.x+radius,a.y+height,a.z+radius)
            for(axis in 0..2) {
                if(abs(direction[axis])<1e-8) { if(origin[axis]<low[axis] || origin[axis]>high[axis]) leave=-1.0 }
                else { val u=(low[axis]-origin[axis])/direction[axis];val v=(high[axis]-origin[axis])/direction[axis]
                    enter=maxOf(enter,minOf(u,v));leave=minOf(leave,maxOf(u,v)) }
            }
            if(enter<=leave && enter<nearest) { nearest=enter;selected=a }
        }
        val actor=selected ?: return false
        residents.resident(actor)?.let { resident ->
            val view=tradeView(resident.key) ?: return false
            touch.reset();gamePaused=true;tradeCallback?.invoke(view);return true
        }
        val result=passiveAnimals.interact(actor,hotbar[selectedSlot],inventory)
        if(result==0) return false
        val message=when(result) {
            1 -> com.Atom2Universe.app.R.string.cave_animal_fed
            2 -> com.Atom2Universe.app.R.string.cave_animal_ready
            else -> com.Atom2Universe.app.R.string.cave_animal_wait
        }
        if(result!=3) { changedFrontierInventory();startSwing() }
        farmMessageCallback?.invoke(context.getString(message));return true
    }
    /** Validate loaded terrain before moving the player. No destination writes or remote mining. */
    internal fun travel(home: Boolean): Int {
        val missing=com.Atom2Universe.app.R.string.cave_travel_missing
        if(worldSource!=null || !mode.allowsWorldEdits || !playerNode.isAlive ||
            (inventory[com.Atom2Universe.app.games.caves.node.FrontierItems.CHARM] ?: 0)<1) return missing
        if(frontierLife.elapsedMs-frontierLife.lastTravel<20_000L) return com.Atom2Universe.app.R.string.cave_travel_wait
        val dest=(if(home) frontierLife.home else frontierLife.expedition) ?: return missing
        for(cx in Math.floorDiv(dest.x-5,16)..Math.floorDiv(dest.x+5,16))
            for(cy in Math.floorDiv(dest.y-5,16)..Math.floorDiv(dest.y+6,16))
                for(cz in Math.floorDiv(dest.z-5,16)..Math.floorDiv(dest.z+5,16)) world.pregenerateChunk(cx,cy,cz)
        if(home && world.blockAt(dest.x,dest.y,dest.z)!=com.Atom2Universe.app.games.caves.node.FrontierItems.HEARTH) return missing
        var landing: FrontierLife.Place?=null
        search@ for(r in 0..4) for(dx in -r..r) for(dz in -r..r) {
            if(abs(dx)!=r && abs(dz)!=r) continue
            for(dy in listOf(0,1,-1,2,-2,3,-3)) {
                val x=dest.x+dx;val y=dest.y+dy;val z=dest.z+dz
                val floor=world.blockAt(x,y-1,z);val def=BlockRegistry.get(floor)
                if(floor==AIR || isWater(floor) || floor==LAVA || isDecoration(floor) || isTransparent(floor) ||
                    def==null || def.slab || def.stairs || def.blockHeight<1f) continue
                if(world.blockAt(x,y,z)!=AIR || world.blockAt(x,y+1,z)!=AIR) continue
                landing=FrontierLife.Place(x,y,z);break@search
            }
        }
        val p=landing ?: return com.Atom2Universe.app.R.string.cave_travel_blocked
        val from=FrontierLife.Place(floorInt(camera.playerX),floorInt(camera.playerY-1.62),floorInt(camera.playerZ))
        frontierLife.arrived(from,home,frontierLife.elapsedMs)
        fishing.cancel()
        camera.playerX=p.x+.5;camera.playerY=p.y+1.62;camera.playerZ=p.z+.5
        physics.reset();touch.reset();enemyManager.enemies.clear()
        enemyManager.spawnManager.resetAfterTravel()
        mineTarget=null;mineDamage=0f;rockChargeTime=0f;weaponChargeTime=0f
        projectiles.removeAll { it.fromEnemy }
        camera.eyeDrop=0.0;camera.update()
        warmSpawnNeighborhood();checkpointCallback?.invoke()
        return com.Atom2Universe.app.R.string.cave_travel_done
    }

    private fun placeBlock(target: RayHit? = raycastBlock(), actorChecked: Boolean=false) {
        if(worldSource==null && mode.allowsWorldEdits && playerNode.isAlive) {
            if(!actorChecked && interactActor(
                if(camera.thirdPerson) camera.playerX else camera.x,
                if(camera.thirdPerson) camera.orbitY else camera.y,
                if(camera.thirdPerson) camera.playerZ else camera.z,
                camera.aimX.toDouble(),camera.aimY.toDouble(),camera.aimZ.toDouble())) return
            val held=hotbar[selectedSlot]
            if(held==com.Atom2Universe.app.games.caves.node.FrontierItems.CHARM && (inventory[held] ?: 0)>0) {
                gamePaused=true;touch.reset();travelCallback?.invoke();return
            }
            if(target!=null && !physics.isCrouching && world.blockAt(target.bx,target.by,target.bz)==com.Atom2Universe.app.games.caves.node.FrontierItems.HEARTH) {
                frontierLife.bindHome(FrontierLife.Place(target.bx,target.by,target.bz))
                farmMessageCallback?.invoke(context.getString(com.Atom2Universe.app.R.string.cave_travel_bound))
                checkpointCallback?.invoke();return
            }
        }
        if (target != null && !physics.isCrouching && mode.allowsWorldEdits) {
            if(world.blockAt(target.bx,target.by,target.bz)==E.ANVIL) {
                touch.reset();craftStationCallback?.invoke();return
            }
            workshops.view(FrontierWorkshops.Pos(target.bx,target.by,target.bz))?.let { box ->
                touch.laserActive = false
                gamePaused=true
                storageCallback?.invoke(box, inventory.toMap())
                return
            }
        }
        val held = hotbar[selectedSlot]
        if (held != null && (inventory[held] ?: 0) > 0) {
            val heal = com.Atom2Universe.app.games.caves.node.FrontierItems.healing(held)
            if (heal > 0) {
                if (playerNode.isAlive && playerNode.hp < playerNode.maxHp) {
                    playerNode.applyHeal(heal); consumeFarmItem(held); startSwing()
                }
                return
            }
            if (held == com.Atom2Universe.app.games.caves.node.FrontierItems.COMPOST) {
                if (target != null && farming.fertilize(target.bx,target.by,target.bz)) {
                    consumeFarmItem(held); startSwing()
                } else farmMessageCallback?.invoke(context.getString(com.Atom2Universe.app.R.string.cave_frontier_compost_hint))
                return
            }
        }
        if (heldItemMode == HotbarMode.GARDEN && target != null) {
            val drops = farming.harvest(target.bx,target.by,target.bz,uproot=false)
            if (drops != null) {
                if (drops.isEmpty()) farmMessageCallback?.invoke(context.getString(com.Atom2Universe.app.R.string.cave_farm_not_ready))
                else { grantFarmItems(drops); startSwing() }
                return
            }
        }
        val blockType = hotbar[selectedSlot] ?: return
        com.Atom2Universe.app.games.caves.node.FarmItems.seedCrop(blockType)?.let { crop ->
            if ((inventory[blockType] ?: 0) <= 0 || target == null) return
            if (target.fny == 1 && farming.plant(target.bx,target.by+1,target.bz,crop)) {
                consumeFarmItem(blockType); startSwing()
            } else farmMessageCallback?.invoke(context.getString(com.Atom2Universe.app.R.string.cave_farm_plant_hint))
            return
        }
        com.Atom2Universe.app.games.caves.node.FarmItems.produceCrop(blockType)?.let {
            val player=enemyManager.player ?: return
            if ((inventory[blockType] ?: 0) <= 0 || !player.isAlive || player.hp >= player.maxHp) return
            player.applyHeal(3); consumeFarmItem(blockType); startSwing()
            return
        }
        if (blockType == com.Atom2Universe.app.games.caves.node.FarmSoil.HOE) {
            if (!mode.allowsWorldEdits || (inventory[blockType] ?: 0) <= 0 || target == null || target.fny != 1) return
            val soil = world.blockAt(target.bx, target.by, target.bz)
            if (soil == com.Atom2Universe.app.games.caves.node.FarmSoil.FARMLAND ||
                "soil" !in BlockRegistry.get(soil)?.tags.orEmpty() ||
                world.blockAt(target.bx, target.by + 1, target.bz) != AIR) return
            world.setBlock(target.bx, target.by, target.bz, com.Atom2Universe.app.games.caves.node.FarmSoil.FARMLAND)
            world.setMeta(target.bx, target.by, target.bz, 0)
            forceMeshRebuild(target.bx, target.by, target.bz)
            startSwing()
            return
        }
        if (blockType.toInt() in 3130..3132) {
            val player = enemyManager.player ?: return
            val count = inventory[blockType] ?: 0
            if (count <= 0 || !player.isAlive || player.hp >= player.maxHp) return
            player.applyHeal(if (blockType.toInt() == 3132) 3 else 5)
            startSwing()
            if (!isCreative) {
                if (count == 1) {
                    inventory.remove(blockType)
                    for (i in hotbar.indices) if (hotbar[i] == blockType) hotbar[i] = null
                } else inventory[blockType] = count - 1
            }
            inventoryCallback?.invoke(inventory.toMap())
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            return
        }
        if (BlockRegistry.get(blockType)?.placeable == false && blockType != BUCKET_EMPTY && blockType != BUCKET_FULL) return
        startSwing()
        if ((inventory[blockType] ?: 0) <= 0) {
            hotbar[selectedSlot] = null
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            return
        }
        target ?: return

        // Seau vide : raycast ignorant l'eau → l'eau est dans la position de face adjacente
        if (blockType == BUCKET_EMPTY) {
            // Le rayon traverse l'eau ; la source est dans la case côté joueur (face normale)
            val directSource = world.blockAt(target.bx, target.by, target.bz) == WATER
            val wx = target.bx + if (directSource) 0 else target.fnx
            val wy = target.by + if (directSource) 0 else target.fny
            val wz = target.bz + if (directSource) 0 else target.fnz
            if (world.blockAt(wx, wy, wz) != WATER) return
            world.setBlock(wx, wy, wz, AIR)
            world.onWaterSourceRemoved(wx, wy, wz)
            forceMeshRebuild(wx, wy, wz)
            clearUnsupportedAround(wx, wy, wz)
            swapBucketInInventory(BUCKET_EMPTY, BUCKET_FULL)
            return
        }

        // Seau plein : poser une source d'eau et récupérer un seau vide
        if (blockType == BUCKET_FULL) {
            val px = target.bx + target.fnx
            val py = target.by + target.fny
            val pz = target.bz + target.fnz
            if (isInsidePlayer(px, py, pz)) return
            val destination = world.blockAt(px, py, pz)
            if (destination != AIR && destination != WATER_FLOW) return
            world.setBlock(px, py, pz, WATER)
            world.onWaterSourcePlaced(px, py, pz)
            forceMeshRebuild(px, py, pz)
            clearUnsupportedAround(px, py, pz)
            swapBucketInInventory(BUCKET_FULL, BUCKET_EMPTY)
            return
        }

        val px = target.bx + target.fnx
        val py = target.by + target.fny
        val pz = target.bz + target.fnz
        if (isInsidePlayer(px, py, pz)) return
        val existing = world.blockAt(px, py, pz)
        if (existing != AIR && !isWater(existing) && BlockRegistry.get(existing)?.replaceable != true) return
        val orientMeta = if (BlockRegistry.get(blockType)?.slab == true) {
            (if (target.fny < 0 || target.fny == 0 && target.hitY > .5) 4 else 0).toByte()
        } else if (BlockRegistry.get(blockType)?.stairs == true) {
            val facing = if (abs(camera.fwdX) > abs(camera.fwdZ)) {
                if (camera.fwdX > 0) 1 else 3
            } else if (camera.fwdZ > 0) 0 else 2
            (facing or if (target.fny < 0 || target.fny == 0 && target.hitY > .5) 4 else 0).toByte()
        } else if (isLeaf(blockType)) com.Atom2Universe.app.games.caves.world.LeafSupport.PERSISTENT else computeOrientMeta(blockType, target.fnx, target.fny, target.fnz)
        if (!com.Atom2Universe.app.games.caves.world.BlockPlacement.supported(blockType, px, py, pz, orientMeta) { a, b, c -> world.blockAt(a, b, c) }) return
        world.setBlock(px, py, pz, blockType)
        workshops.placed(FrontierWorkshops.Pos(px, py, pz), blockType)
        world.setMeta(px, py, pz, orientMeta)
        forceMeshRebuild(px, py, pz)
        if (blockType == WARD_STONE) enemyManager.wardStoneZones.add(Pair(px.toDouble(), pz.toDouble()))
        if (blockType == WATER) world.onWaterSourcePlaced(px, py, pz)
        if (isFalling(blockType)) world.enqueueIfFalling(px, py, pz)
        clearUnsupportedAround(px, py, pz)
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

    internal fun selectWorkshopRecipe(p: FrontierWorkshops.Pos,index: Int) {
        if(!mode.allowsWorldEdits || !playerNode.isAlive ||
            (p.x+.5-camera.playerX).pow(2)+(p.y+.5-camera.playerY).pow(2)+(p.z+.5-camera.playerZ).pow(2)>36) return
        if(workshops.select(p,index)) checkpointCallback?.invoke()
    }
    internal fun transferStorageBatch(p: FrontierWorkshops.Pos,ids: List<Short>) {
        for(id in ids) transferStorage(p,id,Int.MAX_VALUE,true,false)
        inventoryCallback?.invoke(inventory.toMap());hotbarCallback?.invoke(hotbar.copyOf(),selectedSlot)
    }
    internal fun transferStorage(p: FrontierWorkshops.Pos, id: Short, count: Int, deposit: Boolean, notify: Boolean=true) {
        val distance = (camera.playerX-p.x-.5).let { it*it } + (camera.playerY-p.y).let { it*it } +
            (camera.playerZ-p.z-.5).let { it*it }
        if (!mode.allowsWorldEdits || distance > 81.0) return
        if (!workshops.transfer(p, inventory, id, count, deposit)) return
        for (i in hotbar.indices)
            if (hotbar[i]?.let { (inventory[it] ?: 0) <= 0 } == true) hotbar[i] = null
        if(notify) {
            inventoryCallback?.invoke(inventory.toMap())
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
        }
    }

    /** Wait for occluded light to settle before allowing hostile population checks. */
    internal fun spawnLight(x: Int,y: Int,z: Int): Int {
        val c=world.getChunk(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16))
        return if(c?.ecologyLightReady==true) ecologicalLight(x,y,z) else 15
    }
    internal fun ecologicalLight(x: Int, y: Int, z: Int): Int {
        val chunk = world.getChunk(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16)) ?: return 15
        if (!meshes.containsKey(world.chunkKey(chunk.cx,chunk.cy,chunk.cz))) return 15
        val sky = chunk.skyAt(Math.floorMod(x,16),Math.floorMod(y,16),Math.floorMod(z,16))
        val index=Math.floorMod(x,16)+Math.floorMod(y,16)*16+Math.floorMod(z,16)*256
        val blockLight=(chunk.light[index].toInt() ushr 4) and 15
        return maxOf((sky*ambientFor(dayFraction())).toInt(),blockLight).coerceIn(0,15)
    }
    private fun swapBucketInInventory(from: Short, to: Short) {
        if (isCreative) {
            inventory[to] = (inventory[to] ?: 0) + 1
            for(i in hotbar.indices) if(i!=selectedSlot && hotbar[i]==to) hotbar[i]=null
            hotbar[selectedSlot] = to
            inventoryCallback?.invoke(inventory.toMap())
            hotbarCallback?.invoke(hotbar.copyOf(), selectedSlot)
            return
        }
        val newFromCount = (inventory[from] ?: 1) - 1
        if (newFromCount <= 0) inventory.remove(from) else inventory[from] = newFromCount
        inventory[to] = (inventory[to] ?: 0) + 1
        // Keep the resulting stack in hand; it must not occupy a second slot as well.
        for(i in hotbar.indices) if(i!=selectedSlot && hotbar[i]==to) hotbar[i]=null
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
        val y0 = floorInt(camera.playerY - 1.62); val y1 = floorInt(camera.playerY + physics.heightAbove)
        val z0 = floorInt(camera.playerZ - 0.3); val z1 = floorInt(camera.playerZ + 0.29)
        return bx in x0..x1 && by in y0..y1 && bz in z0..z1
    }

    /** Utilisé par la sauvegarde de l'activité pour rendre persistantes les modifications de chunks. */
    fun flushStorage() {
        storage?.flush()
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
        decorRenderer?.destroy()
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
        /** Au-delà, une balle qui frappe un mur ne s'entend plus. */
        private const val IMPACT_HEARING   = 40.0
    }
}
