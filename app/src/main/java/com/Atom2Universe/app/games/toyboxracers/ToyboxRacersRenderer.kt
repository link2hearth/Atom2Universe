package com.Atom2Universe.app.games.toyboxracers

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import com.Atom2Universe.app.games.toyboxracers.ai.RivalCar
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
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.exp

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

    private lateinit var shader: ToyboxShader
    private lateinit var trackMesh: ColoredMesh
    private lateinit var environmentMesh: ColoredMesh
    private lateinit var carMesh: ColoredMesh
    private lateinit var shadowMesh: ColoredMesh
    private lateinit var rivalMeshes: List<ColoredMesh>

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val identity = FloatArray(16)
    private val carModel = FloatArray(16)
    private val shadowModel = FloatArray(16)
    private val rivalModels = Array(5) { FloatArray(16) }

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

        if (resetRequested) {
            val scene = requestedScene
            if (scene != track.scene) {
                track = PrototypeTrack(scene = scene)
                car = ArcadeCar(track)
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
            resetRace()
            resetRequested = false
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

    private fun renderScene(frameSeconds: Float) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(shader.program)
        environmentMesh.draw(shader, viewProjection, identity)
        trackMesh.draw(shader, viewProjection, identity)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        turboEffects.draw(shader, viewProjection, identity)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        val shouldDrawShadow = car.airborne || !car.groundedOnRoad || car.offRoad || track.isJumpGap(car.distance)
        if (shouldDrawShadow) {
            val groundY = maxOf(track.groundHeightAt(renderCarPosition.x, renderCarPosition.z),
                track.furnitureHeightAt(renderCarPosition.x, renderCarPosition.z,
                    renderCarPosition.y - PrototypeTrack.CAR_CLEARANCE + 0.02f))
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

    companion object {
        private const val FIXED_STEP = 1f / 60f
        /** Équivaut à l'ancien 0,14 par image, mais mesuré à 60 images par seconde. */
        private const val PITCH_SMOOTHING_RATE = 9.05f
        private const val MAX_VISUAL_PITCH = 0.76f
        private const val CAR_VISUAL_SUSPENSION_OFFSET = -0.21f
    }
}
