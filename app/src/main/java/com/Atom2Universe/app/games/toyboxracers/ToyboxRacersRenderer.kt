package com.Atom2Universe.app.games.toyboxracers

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import com.Atom2Universe.app.games.toyboxracers.ai.RivalCar
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.render.ColoredMesh
import com.Atom2Universe.app.games.toyboxracers.render.PrototypeMeshFactory
import com.Atom2Universe.app.games.toyboxracers.render.ToyboxShader
import com.Atom2Universe.app.games.toyboxracers.render.TurboEffects
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

internal class ToyboxRacersRenderer(
    initialDifficulty: RaceDifficulty,
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
        val finishSerial: Int
    )

    private val track = PrototypeTrack()
    private val car = ArcadeCar(track)
    private val turboEffects = TurboEffects()
    private val raceSession = RaceSession(track)
    private val rivals = List(5) { RivalCar(track, it) }
    private var difficulty = initialDifficulty

    @Volatile private var steeringInput = 0f
    @Volatile private var acceleratorInput = false
    @Volatile private var brakeInput = false
    @Volatile private var resetRequested = false
    @Volatile private var requestedDifficulty = initialDifficulty

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
        resetRace()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 58f, aspect, 0.1f, 420f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val frameSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.10f)
        lastFrameNanos = now

        if (resetRequested) {
            difficulty = requestedDifficulty
            resetRace()
            resetRequested = false
        }

        accumulator = (accumulator + frameSeconds).coerceAtMost(0.20f)
        while (accumulator >= FIXED_STEP) {
            when (raceSession.phase) {
                RacePhase.COUNTDOWN -> raceSession.updateCountdown(FIXED_STEP, car.distance)
                RacePhase.RACING -> {
                    val previousRelease = car.turboReleaseSerial
                    car.update(FIXED_STEP, ArcadeCar.Input(steeringInput, acceleratorInput, brakeInput))
                    rivals.forEach { it.update(FIXED_STEP, difficulty) }
                    raceSession.updateRace(
                        FIXED_STEP,
                        car.distance,
                        car.groundedOnRoad && !car.offRoad,
                        rivals
                    )
                    turboEffects.update(FIXED_STEP, car)
                    if (car.turboReleaseSerial != previousRelease) cameraKick = 1f
                }
                RacePhase.FINISHED -> Unit
            }
            accumulator -= FIXED_STEP
            hudAccumulator += FIXED_STEP
        }

        updateCamera(frameSeconds)
        renderScene()

        if (hudAccumulator >= 0.10f) {
            hudAccumulator = 0f
            hudListener(
                HudState(
                    speedKmh = (car.speed * 11.5f).toInt(),
                    lap = raceSession.playerLap,
                    elapsedSeconds = raceSession.raceSeconds,
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
                    finishSerial = raceSession.finishSerial
                )
            )
        }
    }

    private fun updateCamera(frameSeconds: Float) {
        // La hauteur de la caméra suit la voiture, mais pas la pente instantanée.
        // Elle reste ainsi stable lors de la cassure entre le tremplin et le vide.
        // Son cap vient de la voiture et jamais de la piste : sortir de la route
        // ne peut donc provoquer aucune rotation automatique de la vue.
        val horizontalForward = Vec3(
            kotlin.math.sin(car.yawRadians),
            0f,
            kotlin.math.cos(car.yawRadians)
        )
        val right = Vec3(horizontalForward.z, 0f, -horizontalForward.x)
        val wantedDriftLean = if (car.drifting) car.headingOffset.coerceIn(-0.45f, 0.45f) else 0f
        visualDriftLean += (wantedDriftLean - visualDriftLean) *
            (frameSeconds * 7f).coerceIn(0f, 1f)
        cameraKick = (cameraKick - frameSeconds * 2.4f).coerceAtLeast(0f)
        cameraKickVisual += (cameraKick - cameraKickVisual) * (frameSeconds * 5f).coerceIn(0f, 1f)
        val target = car.worldPosition + horizontalForward * 2.1f +
            right * (visualDriftLean * 0.9f) + Vec3(0f, 0.60f, 0f)
        val wanted = car.worldPosition - horizontalForward * (5.2f + cameraKickVisual * 0.55f) +
            right * (visualDriftLean * 1.15f) + Vec3(0f, 3.0f + cameraKickVisual * 0.12f, 0f)
        if (!cameraReady) {
            cameraPosition = wanted
            cameraReady = true
        } else {
            val smoothing = (frameSeconds * 6.5f).coerceIn(0f, 1f)
            cameraPosition = lerp(cameraPosition, wanted, smoothing)
        }
        Matrix.setLookAtM(
            view, 0,
            cameraPosition.x, cameraPosition.y, cameraPosition.z,
            target.x, target.y, target.z,
            -right.x * visualDriftLean * 0.10f, 1f, -right.z * visualDriftLean * 0.10f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    private fun renderScene() {
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

        val road = track.sampleAt(car.distance)
        val groundY = track.groundHeightAt(car.worldPosition.x, car.worldPosition.z)
        val shadowY = if (!car.groundedOnRoad || car.offRoad || track.isJumpGap(car.distance)) {
            groundY + 0.025f
        } else {
            road.position.y + 0.075f
        }
        Matrix.setIdentityM(shadowModel, 0)
        Matrix.translateM(
            shadowModel, 0,
            car.worldPosition.x,
            shadowY,
            car.worldPosition.z
        )
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        shadowMesh.draw(shader, viewProjection, shadowModel)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        val heading = car.yawRadians
        val horizontal = sqrt(road.tangent.x * road.tangent.x + road.tangent.z * road.tangent.z)
        val targetPitch = when {
            car.airborne -> 0f
            !car.groundedOnRoad || car.offRoad -> 0f
            else -> atan2(road.tangent.y, horizontal).coerceIn(-0.35f, 0.35f)
        }
        // Évite la cassure visuelle d'une image au moment où les roues quittent
        // le tremplin ou touchent la réception, perçue comme un petit lag.
        visualPitch += (targetPitch - visualPitch) * 0.14f
        Matrix.setIdentityM(carModel, 0)
        Matrix.translateM(carModel, 0, car.worldPosition.x, car.worldPosition.y, car.worldPosition.z)
        Matrix.rotateM(carModel, 0, heading * 180f / PI.toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(carModel, 0, -visualPitch * 180f / PI.toFloat(), 1f, 0f, 0f)
        carMesh.draw(shader, viewProjection, carModel)
        renderRivals()
    }

    private fun renderRivals() {
        rivals.forEachIndexed { index, rival ->
            val model = rivalModels[index]
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, rival.worldPosition.x, rival.worldPosition.y, rival.worldPosition.z)
            Matrix.rotateM(model, 0, rival.yawRadians * 180f / PI.toFloat(), 0f, 1f, 0f)
            rivalMeshes[index].draw(shader, viewProjection, model)
        }
    }

    private fun resetRace() {
        car.reset()
        rivals.forEach { it.reset(difficulty) }
        raceSession.reset(car.distance)
        cameraReady = false
        visualPitch = 0f
        cameraKick = 0f
        cameraKickVisual = 0f
        visualDriftLean = 0f
        turboEffects.reset(car.turboReleaseSerial)
    }

    private fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

    companion object {
        private const val FIXED_STEP = 1f / 60f
    }
}
