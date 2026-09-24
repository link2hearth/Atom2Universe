package com.Atom2Universe.app.music.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.*

/** Bounded Canvas scenes: shared scratch geometry, no per-frame particle allocations. */
internal class MusicVisualScenes {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val wedge = Path()
    private val rect = RectF()
    private val hsv = floatArrayOf(0f, 0.8f, 1f)
    private var time = 0f
    private var travel = 0f
    private var aspect = 1f
    private var pulse = 0f
    private var bass = 0f
    private var mids = 0f
    private lateinit var spectrum: FloatArray
    private lateinit var wave: FloatArray
    private var plasma: Bitmap? = null
    private var pixels = IntArray(0)
    private var plasmaAge = 1f
    private val spiroX = FloatArray(721)
    private val spiroY = FloatArray(721)
    private val spiroHistoryX = Array(6) { FloatArray(721) }
    private val spiroHistoryY = Array(6) { FloatArray(721) }
    private val spiroHistoryTime = FloatArray(6)
    private var spiroHistoryHead = 0
    private var spiroHistoryCount = 0
    private var spiroHistoryAge = 0f
    private var spiroBass = 0f
    private var spiroMids = 0f
    private var spiroTreble = 0f
    private val liquidOutline = Path()
    private val liquidRadius = FloatArray(96) { 0.29f }
    private val liquidVelocity = FloatArray(96)
    private val liquidTarget = FloatArray(96)
    private val liquidNextVelocity = FloatArray(96)
    private val liquidX = FloatArray(96)
    private val liquidY = FloatArray(96)
    private val liquidAngles = FloatArray(96) { it * 2f * PI.toFloat() / 96f }
    private val liquidCos = FloatArray(96) { cos(liquidAngles[it]) }
    private val liquidSin = FloatArray(96) { sin(liquidAngles[it]) }
    private val liquidDyes = Array(5) { index ->
        val tint = color(165f + index * 55f, 225, 0.85f)
        RadialGradient(0f, 0f, 1f, tint, tint and 0x00ffffff, Shader.TileMode.CLAMP)
    }
    private val liquidHighlight = RadialGradient(0f, 0f, 1f,
        intArrayOf(0xb3eeffff.toInt(), 0x36b5f6ff, 0x00b5f6ff),
        floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)

    fun reset() {
        pulse = 0f
        spiroBass = 0f
        spiroMids = 0f
        spiroTreble = 0f
        plasmaAge = 1f
        spiroHistoryCount = 0
        spiroHistoryHead = 0
        spiroHistoryAge = 0f
        liquidRadius.fill(0.29f)
        liquidVelocity.fill(0f)
    }

    fun release() {
        plasma?.recycle()
        plasma = null
        pixels = IntArray(0)
    }

    fun draw(canvas: Canvas, mode: AudioVisualizerView.VisualizationMode, width: Int,
             height: Int, dt: Float, fft: FloatArray, waveform: FloatArray, beat: Boolean) {
        if (width <= 0 || height <= 0) return
        spectrum = fft
        wave = waveform
        val seconds = dt / 60f
        time += seconds
        plasmaAge += seconds
        bass = band(0, 8)
        mids = band(8, 40)
        travel += seconds * (1f + bass * 0.65f)
        aspect = width.toFloat() / height
        pulse = if (beat) 1f else (pulse - seconds * 2.4f).coerceAtLeast(0f)
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        val save = canvas.save()
        // Normalized coordinates preserve compositions across strip, widget and fullscreen.
        canvas.scale(width.toFloat(), height.toFloat())
        when (mode) {
            AudioVisualizerView.VisualizationMode.PLASMA -> plasma(canvas, width, height)
            AudioVisualizerView.VisualizationMode.SYNTHWAVE -> landscape(canvas, false)
            AudioVisualizerView.VisualizationMode.TERRAIN -> landscape(canvas, true)
            AudioVisualizerView.VisualizationMode.AURORA -> aurora(canvas)
            else -> {
                canvas.translate(0.5f, 0.5f)
                val side = min(width, height).toFloat()
                canvas.scale(side / width, side / height)
                when (mode) {
                    AudioVisualizerView.VisualizationMode.NEON_TUNNEL -> tunnel(canvas, max(width, height) / side)
                    AudioVisualizerView.VisualizationMode.KALEIDOSCOPE -> kaleidoscope(canvas, hypot(width.toFloat(), height.toFloat()) / side)
                    AudioVisualizerView.VisualizationMode.LIQUID_CHROME -> liquid(canvas, seconds)
                    AudioVisualizerView.VisualizationMode.PHOSPHOR -> phosphor(canvas)
                    AudioVisualizerView.VisualizationMode.SPIROGRAPH -> spirograph(canvas, seconds)
                    else -> Unit
                }
            }
        }
        canvas.restoreToCount(save)
    }

    private fun band(start: Int, end: Int): Float {
        var sum = 0f
        for (i in start until min(end, spectrum.size)) sum += spectrum[i]
        return (sum / (end - start) * 2.2f).coerceIn(0f, 1f)
    }

    private fun color(hue: Float, alpha: Int = 255, saturation: Float = 0.8f, value: Float = 1f): Int {
        hsv[0] = ((hue % 360f) + 360f) % 360f
        hsv[1] = saturation
        hsv[2] = value
        return Color.HSVToColor(alpha.coerceIn(0, 255), hsv)
    }

    private fun stroke(canvas: Canvas, hue: Float, thickness: Float = 0.003f, alpha: Int = 220) {
        paint.style = Paint.Style.STROKE
        paint.color = color(hue, alpha / 7)
        paint.strokeWidth = thickness * 5f
        canvas.drawPath(path, paint)
        paint.color = color(hue, alpha, 0.55f)
        paint.strokeWidth = thickness
        canvas.drawPath(path, paint)
    }

    private fun tunnel(canvas: Canvas, extent: Float) {
        // Nested twisted octagons, with continuous travel through the near plane.
        for (ring in 0 until 30) {
            val depth = (ring / 30f + travel * 0.12f) % 1f
            val radius = 0.025f + depth.pow(2.5f) * extent
            val twist = time * 0.25f + (1f - depth) * (1.7f + mids * 0.7f)
            val cx = sin(time * 0.43f + depth * 2f) * 0.07f * depth
            val cy = cos(time * 0.37f + depth * 2f) * 0.07f * depth
            path.rewind()
            for (i in 0..8) {
                val a = i * PI.toFloat() / 4 + twist
                val r = radius * (1f + spectrum[i * 4] * 0.15f)
                val x = cx + cos(a) * r
                val y = cy + sin(a) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            stroke(canvas, 185f + ring * 5f + time * 8f, 0.001f + depth * 0.003f,
                (sin(depth * PI.toFloat()) * 220).toInt())
        }
    }

    private fun kaleidoscope(canvas: Canvas, extent: Float) {
        val save = canvas.save()
        canvas.rotate(time * 5f)
        // Twelve identical 30-degree wedges, alternately reflected along their boundary.
        wedge.rewind()
        wedge.moveTo(0f, 0f)
        wedge.lineTo(extent, 0f)
        wedge.lineTo(extent * cos(PI.toFloat() / 6f), extent * 0.5f)
        wedge.close()
        for (sector in 0 until 12) {
            val segment = canvas.save()
            canvas.rotate(sector * 30f)
            if (sector % 2 == 1) { canvas.rotate(30f); canvas.scale(1f, -1f) }
            canvas.clipPath(wedge)
            for (j in 0 until 22) {
                val angle = j * 1.7f + time * 0.23f
                val r = (0.08f + j * 0.057f + sin(time * 0.4f + j) * 0.035f) * extent
                val positionAngle = PI.toFloat() / 12f + sin(time * 0.3f + j) * 0.28f
                val x = r * cos(positionAngle)
                val y = r * sin(positionAngle)
                val size = extent * (0.055f + spectrum[j * 3] * 0.10f + bass * 0.025f + pulse * 0.012f)
                paint.style = Paint.Style.FILL
                paint.color = color(j * 27f + time * 13f, 145, 0.75f)
                path.rewind()
                for (k in 0 until 4) {
                    val a = angle + k * PI.toFloat() / 2f
                    val px = x + cos(a) * size
                    val py = y + sin(a) * size * 1.7f
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
                stroke(canvas, j * 27f + time * 13f, 0.002f, 180)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.003f
                paint.color = color(j * 27f + 70f, 130)
                canvas.drawCircle(x, y, size * 1.8f, paint)
            }
            canvas.restoreToCount(segment)
        }
        canvas.restoreToCount(save)
    }

    private fun liquid(canvas: Canvas, seconds: Float) {
        val treble = band(40, 110)
        val agitation = (bass * 0.6f + mids * 0.3f + treble * 0.1f).coerceIn(0f, 1f)
        // Low lobes move the puddle; finer modes wrinkle its edge like a driven membrane.
        for (i in liquidTarget.indices) {
            val a = liquidAngles[i]
            liquidTarget[i] = 0.29f + bass * 0.025f +
                (0.025f + bass * 0.025f) * sin(a * 3f + time * 1.1f) +
                0.023f * sin(a * 2f - time * 0.67f) +
                (0.01f + mids * 0.023f) * sin(a * 5f - time * 2.3f) +
                mids * 0.018f * sin(a * 9f + time * 5f) +
                treble * 0.012f * sin(a * 17f - time * 12f)
        }
        // Coupled damped springs give the edge inertia and surface tension. Increased
        // stiffness under excitation suggests shear thickening, without claiming fluid simulation.
        val steps = ceil(seconds / (1f / 120f)).toInt().coerceIn(1, 6)
        val step = seconds / steps
        val stiffness = 110f + agitation * 220f
        repeat(steps) {
            for (i in liquidRadius.indices) {
                val previous = (i + liquidRadius.size - 1) % liquidRadius.size
                val next = (i + 1) % liquidRadius.size
                val tension = (liquidRadius[previous] + liquidRadius[next] - 2f * liquidRadius[i]) * 45f
                val force = (liquidTarget[i] - liquidRadius[i]) * stiffness + tension
                liquidNextVelocity[i] = (liquidVelocity[i] + force * step) * exp(-11f * step)
            }
            for (i in liquidRadius.indices) {
                liquidVelocity[i] = liquidNextVelocity[i]
                liquidRadius[i] += liquidVelocity[i] * step
            }
        }
        for (i in liquidRadius.indices) {
            liquidX[i] = liquidCos[i] * liquidRadius[i] * (1f + sin(time * 0.53f) * 0.07f)
            liquidY[i] = liquidSin[i] * liquidRadius[i] * (0.94f - sin(time * 0.53f) * 0.05f)
        }
        liquidOutline.rewind()
        liquidOutline.moveTo(liquidX[0], liquidY[0])
        for (i in liquidRadius.indices) {
            val p = (i + 95) % 96
            val n = (i + 1) % 96
            val nn = (i + 2) % 96
            liquidOutline.cubicTo(
                liquidX[i] + (liquidX[n] - liquidX[p]) / 6f,
                liquidY[i] + (liquidY[n] - liquidY[p]) / 6f,
                liquidX[n] - (liquidX[nn] - liquidX[i]) / 6f,
                liquidY[n] - (liquidY[nn] - liquidY[i]) / 6f,
                liquidX[n], liquidY[n])
        }
        liquidOutline.close()

        // One continuous colored mass, with diffuse pigments flowing under its surface.
        paint.style = Paint.Style.FILL
        paint.color = color(205f + sin(time * 0.24f) * 100f, 255, 0.78f, 0.65f)
        canvas.drawPath(liquidOutline, paint)
        val clipped = canvas.save()
        canvas.clipPath(liquidOutline)
        for (i in liquidDyes.indices) {
            val a = time * 0.24f + i * 2.4f
            val x = cos(a) * 0.19f + sin(time * 0.73f + i) * 0.045f
            val y = sin(a * 0.83f + i) * 0.18f
            liquidSpot(canvas, x, y, 0.29f + bass * 0.03f, 0.22f, liquidDyes[i], 255)
        }
        // Interfering waves form moving ridges and hollows across the puddle, rather
        // than concentric outlines. Tiny bright crests vibrate faster on the upper bands.
        for (ridge in 0 until 12) {
            path.rewind()
            val centerY = (ridge - 5.5f) * 0.041f
            val span = 0.16f + 0.045f * sin(ridge * 1.7f + time * 0.5f)
            val centerX = sin(ridge * 2.1f + time * 0.4f) * 0.07f
            for (j in 0..40) {
                val u = j / 40f
                val x = centerX + (u - 0.5f) * span * 2f
                val envelope = sin(u * PI.toFloat())
                val y = centerY + envelope * (
                    (0.009f + bass * 0.024f) * sin(x * 24f + time * 5f + ridge * 0.9f) +
                    mids * 0.012f * sin(x * 43f - time * 8f) +
                    treble * 0.005f * sin(x * 80f + time * 15f + ridge))
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.008f + agitation * 0.006f
            paint.color = Color.argb((18 + agitation * 25).toInt(), 5, 15, 50)
            canvas.drawPath(path, paint)
            paint.strokeWidth = 0.0015f + agitation * 0.002f
            paint.color = Color.argb((28 + agitation * 65).toInt(), 215, 255, 255)
            canvas.drawPath(path, paint)
        }
        liquidSpot(canvas, -0.10f + sin(time * 0.7f) * 0.03f, -0.12f,
            0.13f, 0.045f + mids * 0.018f, liquidHighlight, 175)
        liquidSpot(canvas, 0.13f, 0.10f + sin(time * 1.1f) * 0.03f,
            0.05f + bass * 0.035f, 0.025f, liquidHighlight, 90)
        canvas.restoreToCount(clipped)

        // A small meniscus keeps the silhouette readable without metallic contour rings.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.004f
        paint.color = color(190f + sin(time * 0.3f) * 80f, 105, 0.25f)
        canvas.drawPath(liquidOutline, paint)
    }

    private fun liquidSpot(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float,
                           shader: Shader, alpha: Int) {
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.scale(rx, ry)
        paint.style = Paint.Style.FILL
        paint.shader = shader
        paint.alpha = alpha
        canvas.drawCircle(0f, 0f, 1f, paint)
        paint.shader = null
        paint.alpha = 255
        canvas.restoreToCount(saved)
    }

    private fun phosphor(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.001f
        paint.color = 0x3042ff96
        for (i in -4..4) {
            val p = i * 0.1f
            canvas.drawLine(-0.45f, p, 0.45f, p, paint)
            canvas.drawLine(p, -0.45f, p, 0.45f, paint)
        }
        // Delayed waveform against itself: a real audio XY scope, with a quiet idle trace.
        for (trail in 5 downTo 0) {
            path.rewind()
            for (i in 0 until 192) {
                val a = i / 191f * 2f * PI.toFloat()
                val x = (wave[i] - 0.5f) * 0.7f + cos(a + time * 0.35f - trail * 0.025f) * 0.13f
                val y = (wave[(i + 32) % wave.size] - 0.5f) * 0.7f + sin(a * 2f + time * 0.27f) * 0.13f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(canvas, 140f, 0.002f, if (trail == 0) 245 else 35 - trail * 4)
        }
    }

    private fun spirograph(canvas: Canvas, seconds: Float) {
        // Raw bands jump on every kick; the figure only follows their smoothed envelope.
        val follow = 1f - exp(-seconds / 0.15f)
        spiroBass += (bass - spiroBass) * follow
        spiroMids += (mids - spiroMids) * follow
        spiroTreble += (band(40, 110) - spiroTreble) * follow

        val orbit = 0.21f + spiroBass * 0.025f
        val pen = 0.085f + spiroBass * 0.02f
        val morph = 0.5f + sin(time * 0.31f) * 0.5f
        // Twist follows the distance from the centre, so every petal bends alike (galaxy arms).
        val twist = sin(time * 0.27f) * (0.9f + spiroMids * 0.6f)
        val ripple = 0.004f + spiroMids * 0.012f
        val vibration = spiroTreble * 0.006f
        val relief = 0.05f + spiroMids * 0.05f
        // The whole figure tumbles like a coin: rigid, so the drawing itself stays intact.
        val pitch = sin(time * 0.23f) * 0.75f
        val yaw = sin(time * 0.17f + 1.3f) * 0.6f
        val pitchSin = sin(pitch)
        val pitchCos = cos(pitch)
        val yawSin = sin(yaw)
        val yawCos = cos(yaw)
        val rotation = time * 0.12f

        // Deform one closed curve, then reuse actual past shapes for the afterimage.
        // All angular harmonics are multiples of 1/4 and close over 8 PI: no seam.
        for (i in 0 until spiroX.lastIndex) {
            val a = i * PI.toFloat() / 90f
            val petal = 3.75f * a - time * 0.3f
            val otherPetal = 2.75f * a + time * 0.23f
            // Waves travel along the curve instead of across the screen.
            val radialWave = ripple * sin(a * 7.5f - time * 2.2f) +
                vibration * sin(a * 15f + time * 9f)
            val x = (orbit + radialWave) * cos(a) + pen *
                (cos(petal) * (1f - morph) + cos(otherPetal) * morph)
            val y = (orbit + radialWave) * sin(a) - pen *
                (sin(petal) * (1f - morph) + sin(otherPetal) * morph)

            val spiral = twist * (hypot(x, y) - orbit) / (orbit + pen) * 2.2f + rotation
            val spiralSin = sin(spiral)
            val spiralCos = cos(spiral)
            val flatX = x * spiralCos - y * spiralSin
            val flatY = x * spiralSin + y * spiralCos
            // Relief belongs to the petals: alternate ones rise out of the plane.
            val flatZ = relief * (cos(petal) * (1f - morph) + cos(otherPetal) * morph)

            val tiltedY = flatY * pitchCos - flatZ * pitchSin
            val tiltedZ = flatY * pitchSin + flatZ * pitchCos
            val turnedX = flatX * yawCos + tiltedZ * yawSin
            val turnedZ = -flatX * yawSin + tiltedZ * yawCos
            val perspective = 0.92f / (1f - turnedZ * 0.9f)
            spiroX[i] = turnedX * perspective
            spiroY[i] = tiltedY * perspective
        }
        spiroX[spiroX.lastIndex] = spiroX[0]
        spiroY[spiroY.lastIndex] = spiroY[0]

        val hue = 32f + sin(time * 0.1f) * 20f
        for (trail in spiroHistoryCount downTo 1) {
            val slot = (spiroHistoryHead - trail + spiroHistoryX.size) % spiroHistoryX.size
            val fade = (1f - (time - spiroHistoryTime[slot]) / 0.25f).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            spiroPath(spiroHistoryX[slot], spiroHistoryY[slot])
            stroke(canvas, hue + trail * 6f, 0.0012f, (fade * 65f).toInt())
        }
        spiroPath(spiroX, spiroY)
        stroke(canvas, hue, 0.0014f + spiroTreble * 0.0005f, 235)

        // Fixed-time history avoids changing the trail length at 60 / 90 / 120 Hz.
        spiroHistoryAge += seconds
        if (spiroHistoryAge >= 1f / 30f) {
            spiroHistoryAge %= 1f / 30f
            spiroX.copyInto(spiroHistoryX[spiroHistoryHead])
            spiroY.copyInto(spiroHistoryY[spiroHistoryHead])
            spiroHistoryTime[spiroHistoryHead] = time
            spiroHistoryHead = (spiroHistoryHead + 1) % spiroHistoryX.size
            spiroHistoryCount = min(spiroHistoryCount + 1, spiroHistoryX.size)
        }
    }

    private fun spiroPath(x: FloatArray, y: FloatArray) {
        path.rewind()
        path.moveTo(x[0], y[0])
        for (i in 1 until x.size) path.lineTo(x[i], y[i])
        path.close()
    }

    private fun landscape(canvas: Canvas, terrain: Boolean) {
        val horizon = if (terrain) 0.25f else 0.43f
        if (!terrain) {
            // Striped setting sun, rendered as clipped lines rather than a texture.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.007f
            for (i in -20..20) {
                val y = i / 20f
                val half = sqrt((1f - y * y).coerceAtLeast(0f)) * 0.16f / max(1f, aspect)
                paint.color = color(35f - (y + 1f) * 25f, 225)
                val sunY = 0.24f + y * 0.16f * min(1f, aspect)
                canvas.drawLine(0.5f - half, sunY, 0.5f + half, sunY, paint)
            }
        }
        val distance = travel * 2f
        val passedRows = floor(distance)
        val fraction = distance - passedRows
        for (row in 0 until 32) {
            val z = (row + fraction) / 32f
            // A row retains its world identity as it advances to the next screen slot.
            // Resetting the fraction alone used to snap every line backwards twice a second.
            val worldRow = row - passedRows
            val perspective = z * z
            path.rewind()
            for (col in 0..64) {
                val x = col / 64f
                val mountain = if (terrain) {
                    (sin(x * 19f + worldRow * 0.3f + time) * cos(x * 11f - worldRow * 0.22f) * 0.06f +
                        spectrum[min(col, spectrum.lastIndex)] * 0.14f) * (0.2f + z)
                } else abs(x - 0.5f).pow(2) * sin(x * 32f + worldRow * 0.4f + time) * (0.2f + bass * 0.3f)
                val y = horizon + perspective * (1f - horizon) - mountain
                if (col == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // Recycle only invisible rows at the horizon / near plane.
            val fade = smoothStep(z / 0.08f) * smoothStep((1f - z) / 0.10f)
            stroke(canvas, if (terrain) 160f + z * 75f else 285f + z * 40f,
                0.0008f + z * 0.0015f, ((40 + z * 170) * fade).toInt())
        }
        if (!terrain) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.0012f
            paint.color = color(185f, 125)
            for (i in -12..12) canvas.drawLine(0.5f + i * 0.013f, horizon, 0.5f + i * 0.14f, 1f, paint)
        }
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun aurora(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (i in 0 until 80) {
            val x = ((i * 0.618034f) % 1f)
            val y = ((i * 0.381966f) % 0.72f)
            paint.color = color(190f, (65 + 45 * sin(time + i)).toInt(), 0.1f)
            canvas.drawCircle(x, y, 0.0015f, paint)
        }
        for (curtain in 0 until 3) {
            for (strand in 0 until 100) {
                val x = strand / 99f
                val y = 0.3f + curtain * 0.13f + sin(x * 6f + time * 0.3f + curtain) * 0.12f +
                    sin(x * 17f - time * 0.5f) * 0.025f
                val length = 0.12f + mids * 0.15f + spectrum[strand] * 0.12f
                path.rewind()
                path.moveTo(x, y - length)
                path.cubicTo(x - 0.035f, y - length * 0.5f, x + 0.03f, y - 0.04f, x, y)
                stroke(canvas, 140f + curtain * 40f + x * 45f, 0.003f,
                    (35 + 45 * (sin(x * 8f + time) + 1f)).toInt())
            }
        }
    }

    private fun plasma(canvas: Canvas, width: Int, height: Int) {
        // Small software field at 30 Hz, aspect-correct and bilinearly enlarged.
        val w = (96f * width / max(width, height)).toInt().coerceAtLeast(16)
        val h = (96f * height / max(width, height)).toInt().coerceAtLeast(16)
        if (plasma?.width != w || plasma?.height != h) {
            plasma?.recycle()
            plasma = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixels = IntArray(w * h)
            plasmaAge = 1f
        }
        if (plasmaAge >= 1f / 30f) {
            plasmaAge %= 1f / 30f // Preserve the remainder instead of drifting to 20 Hz.
            for (y in 0 until h) for (x in 0 until w) {
                val px = (x - w * 0.5f) / max(w, h)
                val py = (y - h * 0.5f) / max(w, h)
                val v = sin(px * 18f + time) + sin(py * 21f - time * 0.8f) +
                    sin((px + py) * 14f + time * 0.7f) +
                    sin(hypot(px + sin(time * 0.4f) * 0.2f, py) * (25f + bass * 9f) - time * 1.5f)
                pixels[y * w + x] = color(220f + v * 55f + time * 9f, 255, 0.85f, 0.55f + (v + 4f) / 18f)
            }
            plasma!!.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        paint.style = Paint.Style.FILL
        paint.isFilterBitmap = true
        rect.set(0f, 0f, 1f, 1f)
        canvas.drawBitmap(plasma!!, null, rect, paint)
    }

}
