package com.Atom2Universe.app.games.toyboxracers

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import com.Atom2Universe.app.games.toyboxracers.ai.RivalCar
import com.Atom2Universe.app.games.toyboxracers.editor.ActiveWorldKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxDecor
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxTrackSection
import com.Atom2Universe.app.games.toyboxracers.editor.TrackStyle
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxRotationAxis
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolume
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolumeKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.game.PlayMode
import com.Atom2Universe.app.games.toyboxracers.render.ColoredMesh
import com.Atom2Universe.app.games.toyboxracers.render.PrototypeMeshFactory
import com.Atom2Universe.app.games.toyboxracers.render.ToyboxShader
import com.Atom2Universe.app.games.toyboxracers.render.TurboEffects
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal class ToyboxRacersRenderer(
    initialDifficulty: RaceDifficulty,
    initialScene: SceneChoice = SceneChoice(),
    private val hudListener: (HudState) -> Unit
) : GLSurfaceView.Renderer {

    data class HudState(
        val speedKmh: Int,
        val lap: Int,
        val elapsedSeconds: Float,
        val airborne: Boolean,
        val offRoad: Boolean,
        val drifting: Boolean,
        val turboCharge: Float,
        val turboLevel: Int,
        val turboBoosting: Boolean,
        val turboReleaseSerial: Int,
        val reversing: Boolean,
        val racePhase: RacePhase,
        val countdownSeconds: Float,
        val position: Int,
        val wrongWay: Boolean,
        val difficulty: RaceDifficulty,
        val finishPosition: Int,
        val finishSerial: Int,
        val mode: PlayMode,
        val playerX: Float,
        val playerY: Float,
        val playerZ: Float,
        val playerYaw: Float,
        val rivalPositions: List<Vec3>,
        val scene: SceneChoice
    )

    private var track = PrototypeTrack(scene = initialScene)
    private var car = ArcadeCar(track)
    private val turboEffects = TurboEffects()
    private var raceSession = RaceSession(track)
    private var rivals = List(5) { RivalCar(track, it) }
    private var difficulty = initialDifficulty
    private var mode = PlayMode.EXPLORATION
    private var simulationInitialized = false
    private var explorationSeconds = 0f

    @Volatile private var steeringInput = 0f
    @Volatile private var acceleratorInput = false
    @Volatile private var brakeInput = false
    @Volatile private var resetRequested = false
    @Volatile private var requestedDifficulty = initialDifficulty
    @Volatile private var requestedMode = PlayMode.EXPLORATION
    @Volatile private var paused = false
    @Volatile private var discardFrameTime = false
    @Volatile private var requestedScene = initialScene
    @Volatile private var requestedWorldKind = ActiveWorldKind.CUSTOM
    private var activeWorldKind = ActiveWorldKind.CUSTOM
    @Volatile private var editorActive = false
    @Volatile private var editorForwardInput = 0f
    @Volatile private var editorStrafeInput = 0f
    @Volatile private var editorYawInput = 0f
    @Volatile private var editorPitchInput = 0f
    @Volatile private var requestedWorld = ToyboxWorld()
    @Volatile private var worldDirty = true
    @Volatile private var previewKind = ToyboxVolumeKind.FLOOR
    @Volatile private var previewWidth = 20f
    @Volatile private var previewHeight = 0.6f
    @Volatile private var previewDepth = 20f
    @Volatile private var previewGrid = 1f
    @Volatile private var previewFloorY = 0f
    @Volatile private var previewSolid = true
    @Volatile private var previewColor = ToyboxVolumeKind.FLOOR.color
    @Volatile private var previewQuarterTurns = 0
    @Volatile private var previewYawDegrees = 0f
    @Volatile private var previewPitchDegrees = 0f
    @Volatile private var previewRollDegrees = 0f
    @Volatile private var previewRotationAxis = ToyboxRotationAxis.YAW
    @Volatile private var selectedPreview: ToyboxVolume? = null
    @Volatile private var previewVisible = false
    @Volatile private var trackPreview: ToyboxTrackSection? = null
    @Volatile private var decorPreview: ToyboxDecor? = null

    private lateinit var shader: ToyboxShader
    private lateinit var trackMesh: ColoredMesh
    private lateinit var environmentMesh: ColoredMesh
    private lateinit var carMesh: ColoredMesh
    private lateinit var shadowMesh: ColoredMesh
    private lateinit var rivalMeshes: List<ColoredMesh>
    private var worldMesh: ColoredMesh? = null
    private var previewMesh: ColoredMesh? = null
    private var trackPreviewMesh: ColoredMesh? = null
    private var decorPreviewMesh: ColoredMesh? = null
    private var currentWorld = ToyboxWorld()
    private var lastPreviewVolume: ToyboxVolume? = null
    private var lastPreviewTrack: ToyboxTrackSection? = null
    private var lastPreviewDecor: ToyboxDecor? = null
    @Volatile private var previewMeshDirty = true
    @Volatile private var trackPreviewMeshDirty = true
    @Volatile private var decorPreviewMeshDirty = true

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val identity = FloatArray(16)
    private val carModel = FloatArray(16)
    private val shadowModel = FloatArray(16)
    private val rivalModels = Array(5) { FloatArray(16) }
    private val inverseViewProjection = FloatArray(16)

    private var lastFrameNanos = 0L
    private var accumulator = 0f
    private var hudAccumulator = 0f
    private var cameraPosition = Vec3(0f, 5f, 10f)
    private var cameraReady = false
    private var visualPitch = 0f
    private var cameraKick = 0f
    private var cameraKickVisual = 0f
    private var visualDriftLean = 0f
    private var visualRoll = 0f
    private var editorCameraPosition = Vec3(0f, 16f, -38f)
    private var editorCameraYaw = 0f
    private var editorCameraPitch = -0.22f
    private var previewAnchorReady = false
    private var previewAnchorX = 0f
    private var previewAnchorZ = 0f
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    // État de rendu interpolé entre deux pas de simulation. L'écran affiche
    // 120 images par seconde alors que la simulation en calcule 60 : sans ces
    // deux photos, une image sur deux montrerait exactement la même chose que
    // la précédente, et l'autre un saut double. C'est ce battement à 60 Hz que
    // l'œil perçoit comme une vibration de l'image.
    private var previousCarPosition = Vec3(0f, 0f, 0f)
    private var previousCarYaw = 0f
    private var renderCarPosition = Vec3(0f, 0f, 0f)
    private var renderCarYaw = 0f
    private val previousRivalPositions = Array(5) { Vec3(0f, 0f, 0f) }
    private val previousRivalYaws = FloatArray(5)
    private val renderRivalPositions = Array(5) { Vec3(0f, 0f, 0f) }
    private val renderRivalYaws = FloatArray(5)

    fun setSteering(value: Float) {
        steeringInput = value.coerceIn(-1f, 1f)
    }

    fun setBraking(braking: Boolean) {
        brakeInput = braking
    }

    fun setAccelerating(accelerating: Boolean) {
        acceleratorInput = accelerating
    }

    fun requestReset() {
        resetRequested = true
    }

    fun setScene(scene: SceneChoice) {
        requestedScene = scene
        resetRequested = true
    }

    fun setMode(value: PlayMode) {
        requestedMode = value
        resetRequested = true
    }

    /** Un seul monde est jamais dessiné/simulé à la fois : bascule entre le
     * circuit classique (ruban procédural) et le monde bâti dans l'éditeur. */
    fun setActiveWorldKind(kind: ActiveWorldKind) {
        requestedWorldKind = kind
    }

    fun setPaused(value: Boolean) {
        if (value) {
            steeringInput = 0f
            acceleratorInput = false
            brakeInput = false
        }
        discardFrameTime = true
        paused = value
    }

    fun setDifficulty(value: RaceDifficulty) {
        requestedDifficulty = value
        resetRequested = true
    }

    fun setEditorActive(value: Boolean) {
        editorActive = value
        discardFrameTime = true
        if (value) {
            steeringInput = 0f
            acceleratorInput = false
            brakeInput = false
            editorCameraPosition = Vec3(renderCarPosition.x, maxOf(7f, renderCarPosition.y + 8f), renderCarPosition.z - 28f)
            editorCameraYaw = renderCarYaw
            editorCameraPitch = -0.22f
            resetEditorPreviewAnchor()
            cameraReady = false
        } else {
            editorForwardInput = 0f
            editorStrafeInput = 0f
            editorYawInput = 0f
            editorPitchInput = 0f
            cameraReady = false
        }
    }

    fun setEditorInput(strafe: Float, forward: Float, yaw: Float, pitch: Float) {
        editorStrafeInput = strafe.coerceIn(-1f, 1f)
        editorForwardInput = forward.coerceIn(-1f, 1f)
        editorYawInput = yaw.coerceIn(-1f, 1f)
        editorPitchInput = pitch.coerceIn(-1f, 1f)
    }

    fun setEditorWorld(world: ToyboxWorld) {
        requestedWorld = world
    }

    fun setEditorSelection(volume: ToyboxVolume?) {
        selectedPreview = volume
        previewMeshDirty = true
    }

    fun setEditorPreviewVisible(value: Boolean) {
        previewVisible = value
        previewMeshDirty = true
    }

    fun setEditorDecorPreview(decor: ToyboxDecor?) {
        decorPreview = decor
        decorPreviewMeshDirty = true
    }

    fun setEditorTrackPreview(section: ToyboxTrackSection?) {
        trackPreview = section
        trackPreviewMeshDirty = true
    }

    fun resetEditorPreviewAnchor() {
        val forward = horizontalEditorForward()
        previewAnchorX = snap(editorCameraPosition.x + forward.x * 18f, previewGrid.coerceAtLeast(0.01f))
        previewAnchorZ = snap(editorCameraPosition.z + forward.z * 18f, previewGrid.coerceAtLeast(0.01f))
        previewAnchorReady = true
        previewMeshDirty = true
    }

    fun setEditorPreview(
        kind: ToyboxVolumeKind,
        width: Float,
        height: Float,
        depth: Float,
        grid: Float,
        floorY: Float,
        solid: Boolean,
        color: Int,
        quarterTurns: Int = 0,
        yawDegrees: Float = quarterTurns * 90f,
        pitchDegrees: Float = 0f,
        rollDegrees: Float = 0f,
        rotationAxis: ToyboxRotationAxis = ToyboxRotationAxis.YAW
    ) {
        previewKind = kind
        previewWidth = width.coerceAtLeast(0.05f)
        previewHeight = height.coerceAtLeast(0.05f)
        previewDepth = depth.coerceAtLeast(0.05f)
        previewGrid = grid.coerceAtLeast(0.01f)
        previewFloorY = floorY
        previewSolid = solid
        previewColor = color
        previewQuarterTurns = quarterTurns
        previewYawDegrees = yawDegrees
        previewPitchDegrees = pitchDegrees
        previewRollDegrees = rollDegrees
        previewRotationAxis = rotationAxis
        previewMeshDirty = true
    }

    fun moveEditorPreview(dx: Float, dz: Float) {
        ensurePreviewAnchor()
        val grid = previewGrid.coerceAtLeast(0.01f)
        previewAnchorX = snap(previewAnchorX + dx, grid)
        previewAnchorZ = snap(previewAnchorZ + dz, grid)
        previewMeshDirty = true
    }

    fun editorNudgeDelta(strafe: Float, forward: Float, grid: Float): Vec3 {
        val horizontalForward = horizontalEditorForward()
        val right = Vec3(horizontalForward.z, 0f, -horizontalForward.x)
        return right * (strafe * grid) + horizontalForward * (forward * grid)
    }

    fun moveEditorCameraHeight(delta: Float) {
        editorCameraPosition = Vec3(
            editorCameraPosition.x,
            (editorCameraPosition.y + delta).coerceIn(2.2f, 65f),
            editorCameraPosition.z
        )
    }

    fun makePreviewVolume(id: Long): ToyboxVolume {
        val grid = previewGrid.coerceAtLeast(0.01f)
        ensurePreviewAnchor()
        val rawX = previewAnchorX
        val rawZ = previewAnchorZ
        val width = snap(previewWidth, grid).coerceAtLeast(grid)
        val height = snap(previewHeight, grid).coerceAtLeast(grid)
        val depth = snap(previewDepth, grid).coerceAtLeast(grid)
        val floorY = snap(previewFloorY, grid)
        val centerY = if (previewKind == ToyboxVolumeKind.FLOOR) floorY - height * 0.5f else floorY + height * 0.5f
        return ToyboxVolume(
            id = id,
            kind = previewKind,
            x = snap(rawX, grid),
            y = centerY,
            z = snap(rawZ, grid),
            width = width,
            height = height,
            depth = depth,
            solid = previewSolid,
            color = previewColor,
            quarterTurns = previewQuarterTurns,
            yawDegrees = previewYawDegrees,
            pitchDegrees = previewPitchDegrees,
            rollDegrees = previewRollDegrees
        )
    }

    private fun ensurePreviewAnchor() {
        if (!previewAnchorReady) resetEditorPreviewAnchor()
    }

    fun pickVolume(screenX: Float, screenY: Float, volumes: List<ToyboxVolume>): Long? {
        if (!editorActive || !Matrix.invertM(inverseViewProjection, 0, viewProjection, 0)) return null
        val near = unproject(screenX, screenY, -1f) ?: return null
        val far = unproject(screenX, screenY, 1f) ?: return null
        val direction = (far - near).normalized()
        return volumes
            .mapNotNull { volume -> rayBoxDistance(near, direction, volume)?.let { distance -> volume.id to distance } }
            .minByOrNull { it.second }
            ?.first
    }

    enum class EditorPickKind { VOLUME, TRACK, DECOR }
    data class EditorPick(val kind: EditorPickKind, val id: Long, val distance: Float)

    /** Pick visible parts independently of their collision flag, in camera depth order. */
    fun pickEditorObject(screenX: Float, screenY: Float, world: ToyboxWorld): EditorPick? {
        if (!editorActive || !Matrix.invertM(inverseViewProjection,0,viewProjection,0)) return null
        val near = unproject(screenX,screenY,-1f) ?: return null
        val far = unproject(screenX,screenY,1f) ?: return null
        val direction = (far-near).normalized()
        val hits = buildList {
            world.decorations.forEach { decor ->
                decor.placement()?.let { placement ->
                    placement.model.parts.mapNotNull { part ->
                        rayBoxDistance(near,direction,ToyboxVolume(decor.id,ToyboxVolumeKind.DECOR,
                            placement.x+placement.rotatedX(part.x,part.z)*placement.scale,
                            placement.y+part.y*placement.scale,
                            placement.z+placement.rotatedZ(part.x,part.z)*placement.scale,
                            part.width*placement.scale,part.height*placement.scale,part.depth*placement.scale,
                            yawDegrees=placement.yawDegrees))
                    }.minOrNull()?.let { add(EditorPick(EditorPickKind.DECOR,decor.id,it)) }
                }
            }
            world.trackSections.forEach { section -> section.meshSections.mapNotNull {
                rayTrackSectionDistance(near,direction,it)
            }.minOrNull()?.let { add(EditorPick(EditorPickKind.TRACK,section.id,it)) } }
            world.volumes.forEach { volume -> rayBoxDistance(near,direction,volume)?.let {
                add(EditorPick(EditorPickKind.VOLUME,volume.id,it))
            } }
        }
        return hits.minByOrNull { it.distance }
    }

    fun pickDecor(screenX: Float, screenY: Float, decorations: List<ToyboxDecor>): Long? {
        if (!editorActive || !Matrix.invertM(inverseViewProjection, 0, viewProjection, 0)) return null
        val near = unproject(screenX, screenY, -1f) ?: return null
        val far = unproject(screenX, screenY, 1f) ?: return null
        val direction = (far - near).normalized()
        return decorations
            .mapNotNull { decor ->
                val distance = decor.placement()
                    ?.solids
                    ?.mapNotNull { box -> rayBoxDistance(near, direction, box) }
                    ?.minOrNull()
                distance?.let { decor.id to it }
            }
            .minByOrNull { it.second }
            ?.first
    }

    fun pickTrackSection(screenX: Float, screenY: Float, sections: List<ToyboxTrackSection>): Long? {
        if (!editorActive || !Matrix.invertM(inverseViewProjection, 0, viewProjection, 0)) return null
        val near = unproject(screenX, screenY, -1f) ?: return null
        val far = unproject(screenX, screenY, 1f) ?: return null
        val direction = (far - near).normalized()
        return sections
            .mapNotNull { section -> section.meshSections.mapNotNull { rayTrackSectionDistance(near, direction, it) }.minOrNull()?.let { distance -> section.id to distance } }
            .minByOrNull { it.second }
            ?.first
    }

    fun projectEditorPoint(x: Float, y: Float, z: Float): Pair<Float, Float>? {
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, viewProjection, 0, floatArrayOf(x, y, z, 1f), 0)
        if (output[3] <= 0f) return null
        return (output[0] / output[3] + 1f) * surfaceWidth * 0.5f to
            (1f - output[1] / output[3]) * surfaceHeight * 0.5f
    }

    fun editorPointOnPlane(screenX: Float, screenY: Float, height: Float): Vec3? {
        if (!Matrix.invertM(inverseViewProjection, 0, viewProjection, 0)) return null
        val near = unproject(screenX, screenY, -1f) ?: return null
        val far = unproject(screenX, screenY, 1f) ?: return null
        val direction = far - near
        if (kotlin.math.abs(direction.y) < 0.0001f) return null
        val t = (height - near.y) / direction.y
        if (t !in 0f..1f) return null
        return near + direction * t
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.72f, 0.86f, 0.94f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        shader = ToyboxShader()
        trackMesh = PrototypeMeshFactory.track(track).also { it.upload() }
        environmentMesh = PrototypeMeshFactory.environment(track).also { it.upload() }
        carMesh = PrototypeMeshFactory.car().also { it.upload() }
        val rivalColors = arrayOf(
            floatArrayOf(0.42f, 0.85f, 0.70f, 1f),
            floatArrayOf(0.68f, 0.58f, 0.92f, 1f),
            floatArrayOf(0.52f, 0.78f, 0.98f, 1f),
            floatArrayOf(1.00f, 0.78f, 0.36f, 1f),
            floatArrayOf(0.93f, 0.42f, 0.64f, 1f)
        )
        rivalMeshes = rivalColors.map { color -> PrototypeMeshFactory.car(color).also { it.upload() } }
        shadowMesh = PrototypeMeshFactory.shadow().also { it.upload() }
        currentWorld = requestedWorld
        car.setEditorWorld(currentWorld)
        car.sandboxMode = requestedWorldKind == ActiveWorldKind.CUSTOM
        worldMesh?.destroy()
        worldMesh = PrototypeMeshFactory.world(currentWorld).also { it.upload() }
        turboEffects.upload()
        turboEffects.reset(car.turboReleaseSerial)
        Matrix.setIdentityM(identity, 0)
        lastFrameNanos = 0L
        accumulator = 0f
        cameraReady = false
        // Recréer les ressources GPU ne doit pas effacer une partie suspendue.
        if (!simulationInitialized) {
            resetRace()
            simulationInitialized = true
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 58f, aspect, 0.1f, 420f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val skipTime = paused || discardFrameTime
        discardFrameTime = false
        val frameSeconds = if (skipTime) 0f else
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.10f)
        lastFrameNanos = now
        // Physics state belongs exclusively to the GL/simulation thread.
        val frameWorld = requestedWorld
        if (!editorActive || resetRequested) car.setEditorWorld(frameWorld)
        car.sandboxMode = requestedWorldKind == ActiveWorldKind.CUSTOM

        if (resetRequested) {
            val scene = requestedScene
            if (scene != track.scene) {
                track = PrototypeTrack(scene = scene)
                car = ArcadeCar(track)
                car.setEditorWorld(frameWorld)
                raceSession = RaceSession(track)
                rivals = List(5) { RivalCar(track, it) }
                trackMesh.destroy()
                environmentMesh.destroy()
                trackMesh = PrototypeMeshFactory.track(track).also { it.upload() }
                environmentMesh = PrototypeMeshFactory.environment(track).also { it.upload() }
                discardFrameTime = true
            }
            difficulty = requestedDifficulty
            mode = requestedMode
            car.sandboxMode = requestedWorldKind == ActiveWorldKind.CUSTOM
            resetRace()
            resetRequested = false
        }

        activeWorldKind = requestedWorldKind
        // `car` peut avoir été recréé juste au-dessus (changement de scène) : on
        // resynchronise systématiquement, jamais seulement quand le genre change.
        car.sandboxMode = activeWorldKind == ActiveWorldKind.CUSTOM
        // Un monde bâti dans l'éditeur n'a ni tour ni adversaires : la course
        // n'a de sens que sur un circuit classique.
        if (activeWorldKind == ActiveWorldKind.CUSTOM) mode = PlayMode.EXPLORATION

        rebuildWorldMeshesIfNeeded(frameWorld)

        if (editorActive) {
            updateEditorCamera(frameSeconds)
            renderScene(frameSeconds)
            return
        }

        accumulator = if (skipTime) 0f else (accumulator + frameSeconds).coerceAtMost(0.20f)
        // Vider l'accumulateur remet l'interpolation au début du dernier pas :
        // sans recaler la borne de départ, la pause ferait reculer l'image d'un pas.
        if (skipTime) captureSimulationState()
        while (accumulator >= FIXED_STEP) {
            captureSimulationState()
            if (mode == PlayMode.EXPLORATION) {
                updatePlayer()
                explorationSeconds += FIXED_STEP
            } else when (raceSession.phase) {
                RacePhase.COUNTDOWN -> raceSession.updateCountdown(FIXED_STEP, car.distance)
                RacePhase.RACING -> {
                    updatePlayer()
                    rivals.forEach { it.update(FIXED_STEP, difficulty) }
                    raceSession.updateRace(
                        FIXED_STEP,
                        car.distance,
                        car.groundedOnRoad && !car.offRoad,
                        rivals
                    )
                }
                RacePhase.FINISHED -> Unit
            }
            accumulator -= FIXED_STEP
            hudAccumulator += FIXED_STEP
        }
        interpolateSimulationState()

        updateCamera(frameSeconds)
        renderScene(frameSeconds)

        if (hudAccumulator >= 0.10f) {
            hudAccumulator = 0f
            hudListener(
                HudState(
                    speedKmh = (car.speed * 11.5f).toInt(),
                    lap = raceSession.playerLap,
                    elapsedSeconds = if (mode == PlayMode.RACE) raceSession.raceSeconds else explorationSeconds,
                    airborne = car.airborne,
                    offRoad = car.offRoad,
                    drifting = car.drifting,
                    turboCharge = car.turboCharge,
                    turboLevel = car.turboLevel,
                    turboBoosting = car.turboBoostSeconds > 0f,
                    turboReleaseSerial = car.turboReleaseSerial,
                    reversing = car.reversing,
                    racePhase = raceSession.phase,
                    countdownSeconds = raceSession.countdownSeconds,
                    position = raceSession.playerPosition,
                    wrongWay = raceSession.wrongWay,
                    difficulty = difficulty,
                    finishPosition = raceSession.finishPosition,
                    finishSerial = raceSession.finishSerial,
                    mode = mode,
                    playerX = car.worldPosition.x,
                    playerY = car.worldPosition.y,
                    playerZ = car.worldPosition.z,
                    playerYaw = car.yawRadians,
                    rivalPositions = if (mode == PlayMode.RACE) rivals.map { it.worldPosition } else emptyList(),
                    scene = track.scene
                )
            )
        }
    }

    /** Photo de l'état avant le pas : l'autre borne de l'interpolation. */
    private fun captureSimulationState() {
        previousCarPosition = car.worldPosition
        previousCarYaw = car.yawRadians
        rivals.forEachIndexed { index, rival ->
            previousRivalPositions[index] = rival.worldPosition
            previousRivalYaws[index] = rival.yawRadians
        }
    }

    private fun interpolateSimulationState() {
        // Ce qui reste dans l'accumulateur dit où l'on se trouve entre les deux
        // pas : à 120 Hz il vaut une demi-image sur deux, et c'est exactement la
        // moitié de mouvement qui manquait.
        val alpha = (accumulator / FIXED_STEP).coerceIn(0f, 1f)
        renderCarPosition = lerp(previousCarPosition, car.worldPosition, alpha)
        // Le cap du joueur s'accumule sans jamais être ramené dans un tour :
        // deux pas voisins ne peuvent pas être séparés par un saut de 2π.
        renderCarYaw = previousCarYaw + (car.yawRadians - previousCarYaw) * alpha
        rivals.forEachIndexed { index, rival ->
            renderRivalPositions[index] = lerp(previousRivalPositions[index], rival.worldPosition, alpha)
            // Celui des rivaux sort d'un atan2 : il saute de +π à -π au passage,
            // donc on interpole l'écart le plus court et non la valeur brute.
            renderRivalYaws[index] = previousRivalYaws[index] +
                angleDifference(rival.yawRadians, previousRivalYaws[index]) * alpha
        }
    }

    private fun angleDifference(target: Float, current: Float): Float {
        var value = target - current
        while (value > PI) value -= (2.0 * PI).toFloat()
        while (value < -PI) value += (2.0 * PI).toFloat()
        return value
    }

    private fun updatePlayer() {
        val previousRelease = car.turboReleaseSerial
        car.update(FIXED_STEP, ArcadeCar.Input(steeringInput, acceleratorInput, brakeInput))
        turboEffects.update(FIXED_STEP, car)
        if (car.turboReleaseSerial != previousRelease) cameraKick = 1f
    }

    private fun updateCamera(frameSeconds: Float) {
        // La hauteur de la caméra suit la voiture, mais pas la pente instantanée.
        // Elle reste ainsi stable lors de la cassure entre le tremplin et le vide.
        // Son cap vient de la voiture et jamais de la piste : sortir de la route
        // ne peut donc provoquer aucune rotation automatique de la vue. Il est
        // lu sur l'état interpolé, car la direction du regard n'est pas amortie :
        // un cap qui avance par à-coups fait sauter toute l'image d'un bloc.
        val horizontalForward = Vec3(
            kotlin.math.sin(renderCarYaw),
            0f,
            kotlin.math.cos(renderCarYaw)
        )
        val right = Vec3(horizontalForward.z, 0f, -horizontalForward.x)
        val wantedDriftLean = if (car.drifting) car.headingOffset.coerceIn(-0.45f, 0.45f) else 0f
        visualDriftLean += (wantedDriftLean - visualDriftLean) * smoothing(7f, frameSeconds)
        cameraKick = (cameraKick - frameSeconds * 2.4f).coerceAtLeast(0f)
        cameraKickVisual += (cameraKick - cameraKickVisual) * smoothing(5f, frameSeconds)
        val target = renderCarPosition + horizontalForward * 2.1f +
            right * (visualDriftLean * 0.9f) + Vec3(0f, 0.60f, 0f)
        val wanted = renderCarPosition - horizontalForward * (5.2f + cameraKickVisual * 0.55f) +
            right * (visualDriftLean * 1.15f) + Vec3(0f, 3.0f + cameraKickVisual * 0.12f, 0f)
        if (!cameraReady) {
            cameraPosition = wanted
            cameraReady = true
        } else {
            cameraPosition = lerp(cameraPosition, wanted, smoothing(6.5f, frameSeconds))
        }
        Matrix.setLookAtM(
            view, 0,
            cameraPosition.x, cameraPosition.y, cameraPosition.z,
            target.x, target.y, target.z,
            -right.x * visualDriftLean * 0.10f, 1f, -right.z * visualDriftLean * 0.10f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    private fun updateEditorCamera(frameSeconds: Float) {
        val seconds = frameSeconds.coerceAtMost(0.05f)
        editorCameraYaw += editorYawInput * seconds * 2.4f
        editorCameraPitch = (editorCameraPitch + editorPitchInput * seconds * 1.45f).coerceIn(-1.05f, 0.55f)
        val horizontalForward = horizontalEditorForward()
        val forward = editorForward()
        val right = Vec3(horizontalForward.z, 0f, -horizontalForward.x)
        val speed = 35f
        editorCameraPosition = editorCameraPosition +
            horizontalForward * (editorForwardInput * speed * seconds) +
            right * (editorStrafeInput * speed * seconds) +
            Vec3(0f, 0f, 0f)
        editorCameraPosition = Vec3(
            editorCameraPosition.x.coerceIn(-180f, 180f),
            editorCameraPosition.y.coerceIn(2.2f, 65f),
            editorCameraPosition.z.coerceIn(-150f, 150f)
        )
        val target = editorCameraPosition + forward * 18f
        Matrix.setLookAtM(
            view, 0,
            editorCameraPosition.x, editorCameraPosition.y, editorCameraPosition.z,
            target.x, target.y, target.z,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        val preview = when {
            selectedPreview != null -> selectedPreview
            previewVisible -> makePreviewVolume(PREVIEW_ID)
            else -> null
        }
        if (previewMeshDirty || preview != lastPreviewVolume) {
            previewMesh?.destroy()
            previewMesh = preview?.let {
                PrototypeMeshFactory.world(
                    ToyboxWorld(volumes = emptyList(), trackSections = emptyList()),
                    it,
                    previewRotationAxis
                ).also { mesh -> mesh.upload() }
            }
            lastPreviewVolume = preview
            previewMeshDirty = false
        }
        val trackSection = trackPreview
        if (trackPreviewMeshDirty || trackSection != lastPreviewTrack) {
            trackPreviewMesh?.destroy()
            trackPreviewMesh = trackSection?.let {
                PrototypeMeshFactory.world(ToyboxWorld(volumes = emptyList(), trackSections = listOf(it))).also { mesh -> mesh.upload() }
            }
            lastPreviewTrack = trackSection
            trackPreviewMeshDirty = false
        }
        val decor = decorPreview
        if (decorPreviewMeshDirty || decor != lastPreviewDecor) {
            decorPreviewMesh?.destroy()
            decorPreviewMesh = decor?.let {
                PrototypeMeshFactory.world(
                    ToyboxWorld(volumes = emptyList(), trackSections = emptyList(), decorations = listOf(it)),
                    surfacePriorityStart = currentWorld.decorations.count { decor ->
                        decor.placement()?.model?.surfacePriority?.let { priority -> priority > 0 } == true
                    } + 1
                ).also { mesh -> mesh.upload() }
            }
            lastPreviewDecor = decor
            decorPreviewMeshDirty = false
        }
    }

    private fun renderScene(frameSeconds: Float) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(shader.program)
        if (editorActive) {
            worldMesh?.draw(shader, viewProjection, identity)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            previewMesh?.draw(shader, viewProjection, identity)
            trackPreviewMesh?.draw(shader, viewProjection, identity)
            decorPreviewMesh?.draw(shader, viewProjection, identity)
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
            return
        }
        if (activeWorldKind == ActiveWorldKind.LEGACY) {
            environmentMesh.draw(shader, viewProjection, identity)
            trackMesh.draw(shader, viewProjection, identity)
        } else {
            worldMesh?.draw(shader, viewProjection, identity)
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        turboEffects.draw(shader, viewProjection, identity)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        val shouldDrawShadow = car.airborne || !car.groundedOnRoad || car.offRoad ||
            (activeWorldKind == ActiveWorldKind.LEGACY && track.isJumpGap(car.distance))
        if (shouldDrawShadow) {
            val maximumShadowY = renderCarPosition.y - PrototypeTrack.CAR_CLEARANCE + 0.02f
            val groundY = if (activeWorldKind == ActiveWorldKind.CUSTOM) {
                currentWorld.volumes
                    .filter { it.solid }
                    .mapNotNull { it.topSurfaceYForShadow(renderCarPosition.x, renderCarPosition.z) }
                    .filter { it <= maximumShadowY }
                    .maxOrNull() ?: 0f
            } else {
                maxOf(
                    track.groundHeightAt(renderCarPosition.x, renderCarPosition.z),
                    track.furnitureHeightAt(renderCarPosition.x, renderCarPosition.z, maximumShadowY)
                )
            }
            Matrix.setIdentityM(shadowModel, 0)
            Matrix.translateM(
                shadowModel, 0,
                renderCarPosition.x,
                groundY + 0.025f,
                renderCarPosition.z
            )
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            shadowMesh.draw(shader, viewProjection, shadowModel)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        val heading = renderCarYaw
        val targetPitch = if (car.airborne) 0f else car.pitchRadians.coerceIn(-MAX_VISUAL_PITCH, MAX_VISUAL_PITCH)
        val targetRoll = if (car.airborne) 0f else car.rollRadians.coerceIn(-0.34f, 0.34f)
        // Évite la cassure visuelle d'une image au moment où les roues quittent
        // le tremplin ou touchent la réception, perçue comme un petit lag.
        // Le taux est par seconde : écrit par image, il amortissait deux fois
        // plus vite sur un écran 120 Hz que sur un 60 Hz.
        visualPitch += (targetPitch - visualPitch) * smoothing(PITCH_SMOOTHING_RATE, frameSeconds)
        visualRoll += (targetRoll - visualRoll) * smoothing(PITCH_SMOOTHING_RATE, frameSeconds)
        Matrix.setIdentityM(carModel, 0)
        Matrix.translateM(carModel, 0, renderCarPosition.x, renderCarPosition.y, renderCarPosition.z)
        Matrix.rotateM(carModel, 0, heading * 180f / PI.toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(carModel, 0, -visualPitch * 180f / PI.toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(carModel, 0, visualRoll * 180f / PI.toFloat(), 0f, 0f, 1f)
        Matrix.translateM(carModel, 0, 0f, CAR_VISUAL_SUSPENSION_OFFSET, 0f)
        carMesh.draw(shader, viewProjection, carModel)
        renderRivals()
    }

    private fun renderRivals() {
        if (mode != PlayMode.RACE) return
        rivals.forEachIndexed { index, rival ->
            val model = rivalModels[index]
            Matrix.setIdentityM(model, 0)
            val position = renderRivalPositions[index]
            Matrix.translateM(model, 0, position.x, position.y, position.z)
            Matrix.rotateM(model, 0, renderRivalYaws[index] * 180f / PI.toFloat(), 0f, 1f, 0f)
            rivalMeshes[index].draw(shader, viewProjection, model)
        }
    }

    private fun resetRace() {
        car.reset()
        rivals.forEach { it.reset(difficulty) }
        raceSession.reset(car.distance)
        explorationSeconds = 0f
        accumulator = 0f
        hudAccumulator = 0.10f
        cameraReady = false
        visualPitch = 0f
        cameraKick = 0f
        cameraKickVisual = 0f
        visualDriftLean = 0f
        visualRoll = 0f
        captureSimulationState()
        interpolateSimulationState()
        turboEffects.reset(car.turboReleaseSerial)
    }

    private fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

    /**
     * Amortissement exprimé par seconde et non par image : la même constante
     * donne le même ressenti à 60 comme à 120 images par seconde.
     */
    private fun smoothing(rate: Float, seconds: Float) = 1f - exp(-rate * seconds)

    private fun rebuildWorldMeshesIfNeeded(world: ToyboxWorld) {
        if (!worldDirty && currentWorld === world) return
        discardFrameTime = true
        currentWorld = world
        worldMesh?.destroy()
        worldMesh = PrototypeMeshFactory.world(currentWorld).also { it.upload() }
        previewMesh?.destroy()
        previewMesh = null
        lastPreviewVolume = null
        previewMeshDirty = true
        trackPreviewMesh?.destroy()
        trackPreviewMesh = null
        lastPreviewTrack = null
        trackPreviewMeshDirty = true
        decorPreviewMesh?.destroy()
        decorPreviewMesh = null
        lastPreviewDecor = null
        decorPreviewMeshDirty = true
        worldDirty = false
    }

    private fun horizontalEditorForward() = Vec3(sin(editorCameraYaw), 0f, cos(editorCameraYaw)).normalized()

    private fun editorForward(): Vec3 {
        val flat = cos(editorCameraPitch)
        return Vec3(
            sin(editorCameraYaw) * flat,
            sin(editorCameraPitch),
            cos(editorCameraYaw) * flat
        ).normalized()
    }

    private fun snap(value: Float, grid: Float): Float =
        kotlin.math.round(value / grid) * grid

    private fun unproject(screenX: Float, screenY: Float, ndcZ: Float): Vec3? {
        val ndcX = screenX / surfaceWidth.toFloat() * 2f - 1f
        val ndcY = 1f - screenY / surfaceHeight.toFloat() * 2f
        val input = floatArrayOf(ndcX, ndcY, ndcZ, 1f)
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, inverseViewProjection, 0, input, 0)
        val w = output[3]
        if (kotlin.math.abs(w) < 0.0001f) return null
        return Vec3(output[0] / w, output[1] / w, output[2] / w)
    }

    private fun rayBoxDistance(origin: Vec3, direction: Vec3, box: ToyboxVolume): Float? {
        val local = box.localPoint(origin.x, origin.y, origin.z)
        val directionLocal = box.localPoint(origin.x + direction.x, origin.y + direction.y, origin.z + direction.z)
        val localOrigin = Vec3(local.x, local.y, local.z)
        val localDirection = Vec3(directionLocal.x - local.x, directionLocal.y - local.y, directionLocal.z - local.z)
        return rayBoxDistance(
            localOrigin,
            localDirection,
            -box.width * 0.5f,
            box.width * 0.5f,
            -box.height * 0.5f,
            box.height * 0.5f,
            -box.depth * 0.5f,
            box.depth * 0.5f
        )
    }

    private fun ToyboxVolume.topSurfaceYForShadow(x: Float, z: Float): Float? {
        if (x < left || x > right || z < back || z > front) return null
        val top = height * 0.5f
        val a = worldPoint(-width * 0.5f, top, -depth * 0.5f)
        val b = worldPoint(width * 0.5f, top, -depth * 0.5f)
        val c = worldPoint(width * 0.5f, top, depth * 0.5f)
        val d = worldPoint(-width * 0.5f, top, depth * 0.5f)
        return triangleSurfaceY(x, z, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z)
            ?: triangleSurfaceY(x, z, a.x, a.y, a.z, c.x, c.y, c.z, d.x, d.y, d.z)
    }

    private fun triangleSurfaceY(
        x: Float,
        z: Float,
        ax: Float,
        ay: Float,
        az: Float,
        bx: Float,
        by: Float,
        bz: Float,
        cx: Float,
        cy: Float,
        cz: Float
    ): Float? {
        val denominator = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
        if (kotlin.math.abs(denominator) < 0.000001f) return null
        val u = ((bz - cz) * (x - cx) + (cx - bx) * (z - cz)) / denominator
        val v = ((cz - az) * (x - cx) + (ax - cx) * (z - cz)) / denominator
        val w = 1f - u - v
        if (u < -0.035f || v < -0.035f || w < -0.035f) return null
        return ay * u + by * v + cy * w
    }

    private fun rayBoxDistance(origin: Vec3, direction: Vec3, box: RoomBox): Float? {
        val halfWidth = box.width * 0.5f
        val halfHeight = box.height * 0.5f
        val halfDepth = box.depth * 0.5f
        return rayBoxDistance(
            origin,
            direction,
            box.x - halfWidth,
            box.x + halfWidth,
            box.y - halfHeight,
            box.y + halfHeight,
            box.z - halfDepth,
            box.z + halfDepth
        )
    }

    private fun rayTrackSectionDistance(origin: Vec3, direction: Vec3, section: ToyboxTrackSection): Float? {
        fun cross(a: Vec3, b: Vec3) = Vec3(a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x)
        fun dot(a: Vec3, b: Vec3) = a.x*b.x + a.y*b.y + a.z*b.z
        fun triangle(a: Vec3, b: Vec3, c: Vec3): Float? {
            val edge1 = b - a
            val edge2 = c - a
            val h = cross(direction, edge2)
            val determinant = dot(edge1, h)
            if (kotlin.math.abs(determinant) < 0.000001f) return null
            val s = origin - a
            val u = dot(s, h) / determinant
            if (u !in 0f..1f) return null
            val q = cross(s, edge1)
            val v = dot(direction, q) / determinant
            if (v < 0f || u + v > 1f) return null
            return (dot(edge2, q) / determinant).takeIf { it >= 0f }
        }
        fun point(along: Float, side: Float) = section.corner(along, side).let {
            Vec3(it.x, it.y + PrototypeTrack.ROAD_SURFACE_LIFT, it.z)
        }
        val a = point(-1f, -1f)
        val b = point(1f, -1f)
        val c = point(1f, 1f)
        val d = point(-1f, 1f)
        val hits = mutableListOf<Float>()
        fun face(p: Vec3, q: Vec3, r: Vec3, s: Vec3) {
            triangle(p,q,r)?.let(hits::add)
            triangle(p,r,s)?.let(hits::add)
        }
        face(a,b,c,d)
        val up = Vec3(0f, TrackStyle.BARRIER_HEIGHT, 0f)
        if (section.barriers and 1 != 0) face(a,b,b+up,a+up)
        if (section.barriers and 2 != 0) face(d,c,c+up,d+up)
        return hits.minOrNull()
    }
    private fun rayBoxDistance(
        origin: Vec3,
        direction: Vec3,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        minZ: Float,
        maxZ: Float
    ): Float? {
        var tMin = 0f
        var tMax = 500f

        fun slab(originValue: Float, directionValue: Float, minValue: Float, maxValue: Float): Boolean {
            if (kotlin.math.abs(directionValue) < 0.0001f) return originValue in minValue..maxValue
            var t1 = (minValue - originValue) / directionValue
            var t2 = (maxValue - originValue) / directionValue
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
            }
            tMin = maxOf(tMin, t1)
            tMax = minOf(tMax, t2)
            return tMin <= tMax
        }

        if (!slab(origin.x, direction.x, minX, maxX)) return null
        if (!slab(origin.y, direction.y, minY, maxY)) return null
        if (!slab(origin.z, direction.z, minZ, maxZ)) return null
        return tMin
    }

    companion object {
        private const val FIXED_STEP = 1f / 60f
        /** Équivaut à l'ancien 0,14 par image, mais mesuré à 60 images par seconde. */
        private const val PITCH_SMOOTHING_RATE = 9.05f
        private const val MAX_VISUAL_PITCH = 0.76f
        private const val CAR_VISUAL_SUSPENSION_OFFSET = -0.21f
        private const val PREVIEW_ID = -1L
    }
}
