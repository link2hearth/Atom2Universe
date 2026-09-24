package com.Atom2Universe.app.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.midi.visualizer.GeneralMidiInstruments
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Une même lumière de studio pour les six modules, sans texte intégré au décor. */
abstract class AudioHubTileDrawable(private val accent: Int) : CachedHubArtworkDrawable() {
    final override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xFF090F1D.toInt())
        paint.shader = RadialGradient(w * .5f, h * .32f, maxOf(w, h) * .7f,
            intArrayOf((accent and 0xFFFFFF) or 0x60000000, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        // Conserver les proportions des instruments, y compris dans les raccourcis rectangulaires.
        val scale = min(w / 300f, h / 280f)
        canvas.save()
        canvas.translate(w / 2f - 150f * scale, h * .39f - 108f * scale)
        canvas.scale(scale, scale)
        drawScene(AudioTileScene(canvas), accent)
        canvas.restore()
        paint.shader = LinearGradient(0f, h * .61f, 0f, h * .91f,
            intArrayOf(Color.TRANSPARENT, 0xF2090F1D.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .61f, w, h, paint)
    }

    protected abstract fun drawScene(scene: AudioTileScene, accent: Int)
}

/** Primitives en coordonnées logiques, partagées uniquement par les illustrations audio. */
class AudioTileScene(val canvas: Canvas) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun box(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 5f) {
        paint.color = color
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, paint)
    }

    fun line(x: Float, y: Float, x2: Float, y2: Float, color: Int, width: Float = 2f) {
        paint.color = color
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x, y, x2, y2, paint)
    }

    fun oval(x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        canvas.drawOval(x, y, x + w, y + h, paint)
    }

    fun arc(x: Float, y: Float, w: Float, h: Float, start: Float, sweep: Float, color: Int, width: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(x, y, x + w, y + h), start, sweep, false, paint)
        paint.style = Paint.Style.FILL
    }

    fun glow(x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(x, y, radius,
            intArrayOf((color and 0xFFFFFF) or 0x65000000, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    fun note(x: Float, y: Float, color: Int) {
        oval(x - 8f, y - 4f, 13f, 9f, color)
        line(x + 4f, y, x + 4f, y - 27f, color, 2.5f)
        line(x + 4f, y - 27f, x + 14f, y - 21f, color, 3f)
    }

    fun waveform(x: Float, y: Float, width: Float, height: Float, color: Int, phase: Float = 0f) {
        for (i in 0..48) {
            val t = i / 48f
            val envelope = .12f + .88f * abs(sin(t * 8f + phase))
            val amplitude = height * envelope * (.25f + .75f * abs(sin(i * 1.7f + phase)))
            line(x + t * width, y - amplitude, x + t * width, y + amplitude, color, 2.2f)
        }
    }

    fun microphone(x: Float, y: Float, scale: Float, accent: Int) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        glow(0f, 42f, 65f, accent)
        box(-22f, 0f, 44f, 78f, 0xFFB2C4D6.toInt(), 22f)
        box(-17f, 4f, 34f, 64f, 0xFF33495E.toInt(), 17f)
        for (row in 0..8) {
            val inset = if (row == 0 || row == 8) 9f else 0f
            line(-13f + inset, 13f + row * 6f, 13f - inset, 13f + row * 6f, 0xFF8FAABD.toInt(), 1.4f)
        }
        box(-20f, 66f, 40f, 5f, accent, 2f)
        arc(-31f, 42f, 62f, 52f, 0f, 180f, 0xFF9EB4C9.toInt(), 4f)
        line(-31f, 44f, -31f, 67f, 0xFF9EB4C9.toInt(), 4f)
        line(31f, 44f, 31f, 67f, 0xFF9EB4C9.toInt(), 4f)
        line(0f, 94f, 0f, 124f, 0xFF9EB4C9.toInt(), 5f)
        oval(-33f, 119f, 66f, 12f, 0xFF263C52.toInt())
        line(-23f, 122f, 23f, 122f, 0xFF9EB4C9.toInt(), 2f)
        canvas.restore()
    }
}

/** Casque hi-fi posé devant une partition suspendue. */
class MusicHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFA98AFF.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        canvas.save()
        canvas.rotate(-9f, 150f, 105f)
        box(38f, 53f, 226f, 101f, 0xFF26334C.toInt(), 8f)
        for (i in 0..4) line(50f, 73f + i * 13f, 251f, 73f + i * 13f, 0xFF77859F.toInt(), 1f)
        note(65f, 100f, 0xFFE0D6FF.toInt())
        note(224f, 111f, 0xFFE0D6FF.toInt())
        note(248f, 85f, accent)
        canvas.restore()
        glow(150f, 95f, 100f, accent)
        arc(81f, 29f, 138f, 154f, 180f, 180f, 0xFF111B2E.toInt(), 22f)
        arc(81f, 29f, 138f, 154f, 185f, 170f, 0xFFBEC7E0.toInt(), 10f)
        arc(89f, 37f, 122f, 138f, 188f, 164f, accent, 3f)
        for (x in floatArrayOf(73f, 193f)) {
            box(x, 104f, 36f, 70f, 0xFF080E1A.toInt(), 14f)
            box(x + 4f, 108f, 27f, 60f, 0xFF4E5578.toInt(), 11f)
            box(x + 10f, 116f, 15f, 44f, 0xFF1D2940.toInt(), 7f)
            line(x + 5f, 123f, x + 5f, 152f, accent, 3f)
        }
        arc(117f, 146f, 87f, 49f, 0f, 170f, 0xFF9F8EC7.toInt(), 2f)
        note(147f, 129f, 0xFFF0E9FF.toInt())
    }
}

/** Notes alignées sur les touches, comme dans le piano roll du lecteur. */
class MidiHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF44B9FF.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        val left = 31f
        val keyWidth = 17f
        val colors = intArrayOf(GeneralMidiInstruments.getChannelColor(0),
            GeneralMidiInstruments.getChannelColor(2), GeneralMidiInstruments.getChannelColor(5))
        for (i in 0..13) {
            val x = left + i * keyWidth
            val active = i == 2 || i == 6 || i == 10
            box(x, 150f, 16f, 48f, if (active) 0xFFA8E4F5.toInt() else 0xFFE2E9F3.toInt(), 2f)
            box(x + 1f, 190f, 14f, 6f, 0xFF99AFC5.toInt(), 1f)
        }
        for (i in 0..12) {
            if (i % 7 in intArrayOf(0, 1, 3, 4, 5)) {
                box(left + (i + 1) * keyWidth - 5f, 150f, 10f, 29f, 0xFF060C18.toInt(), 2f)
                box(left + (i + 1) * keyWidth - 3f, 152f, 6f, 21f, 0xFF27384F.toInt(), 1f)
            }
        }
        // Deux phrases en escalier : chaque hauteur arrive à un instant différent.
        // Les durées varient, sans les rangées artificielles de l'ancien motif modulo.
        val keys = intArrayOf(2, 3, 4, 6, 7, 8, 10, 12, 11, 9, 8, 6, 4)
        val starts = floatArrayOf(116f, 99f, 77f, 56f, 34f, 11f, 122f, 99f, 72f, 49f, 24f, 5f, 8f)
        val lengths = floatArrayOf(31f, 22f, 29f, 35f, 24f, 31f, 25f, 19f, 27f, 22f, 17f, 20f, 26f)
        for (i in keys.indices) {
            val x = left + keys[i] * keyWidth + 3f
            val y = starts[i]
            val length = lengths[i]
            val color = colors[if (i < 6) 0 else if (i < 12) 2 else 1]
            box(x - 2f, y - 2f, 15f, length + 4f, (color and 0xFFFFFF) or 0x22000000, 4f)
            box(x, y, 11f, length, color, 3f)
            line(x + 2f, y + 3f, x + 2f, y + length - 3f, 0x99FFFFFF.toInt(), 1f)
        }
        line(31f, 148f, 269f, 148f, accent, 2f)
        for (key in intArrayOf(2, 6, 10)) {
            val x = left + key * keyWidth + 8f
            glow(x, 147f, 26f, accent)
            box(x - 5f, 128f, 10f, 20f, colors[key % 3], 2f)
            oval(x - 3f, 145f, 6f, 4f, Color.WHITE)
        }
    }
}

class RadioHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFFFBD70.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        line(218f, 77f, 247f, 15f, 0xFFA9BCCB.toInt(), 4f)
        oval(243f, 11f, 7f, 7f, 0xFFDFEAF0.toInt())
        arc(117f, 45f, 69f, 36f, 180f, 180f, 0xFFB58D67.toInt(), 7f)
        box(34f, 72f, 232f, 119f, 0xFF664638.toInt(), 15f)
        box(40f, 77f, 220f, 106f, 0xFFBB8960.toInt(), 12f)
        box(49f, 85f, 204f, 90f, 0xFF252D39.toInt(), 8f)
        oval(57f, 95f, 74f, 74f, 0xFF101A28.toInt())
        for (row in 0..10) for (col in 0..10) {
            val dx = (col - 5) * 5.5f
            val dy = (row - 5) * 5.5f
            if (dx * dx + dy * dy < 31f * 31f) oval(93f + dx, 131f + dy, 2f, 2f, 0xFF72828C.toInt())
        }
        box(144f, 96f, 96f, 32f, 0xFF101D27.toInt(), 3f)
        glow(192f, 112f, 49f, accent)
        for (i in 0..15) line(149f + i * 5.5f, 102f, 149f + i * 5.5f, if (i % 5 == 0) 118f else 110f, accent, 1f)
        line(198f, 99f, 198f, 123f, 0xFFFFEDD0.toInt(), 2f)
        for (x in floatArrayOf(161f, 218f)) {
            oval(x - 12f, 141f, 25f, 25f, 0xFFD6BA92.toInt())
            oval(x - 8f, 145f, 17f, 17f, 0xFF6C5546.toInt())
            line(x, 147f, x + 3f, 152f, 0xFFF9E9C9.toInt(), 2f)
        }
        for (i in 0..2) arc(56f - i * 12f, 24f - i * 12f, 46f + i * 24f, 46f + i * 24f,
            215f, 65f, (accent and 0xFFFFFF) or ((150 - i * 35) shl 24), 2f)
    }
}

class AudioEditorHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF62E0CB.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        box(25f, 39f, 250f, 155f, 0xFF324B60.toInt(), 10f)
        box(29f, 43f, 242f, 147f, 0xFF101F31.toInt(), 8f)
        for (i in 0..16) line(41f + i * 13.5f, 52f, 41f + i * 13.5f, if (i % 4 == 0) 62f else 57f, 0xFF748BA3.toInt(), 1f)
        for (row in 0..1) {
            val y = 72f + row * 55f
            box(39f, y, 222f, 47f, 0xFF192F43.toInt(), 3f)
            waveform(45f, y + 23f, 210f, 17f, if (row == 0) accent else 0xFF72AFFF.toInt(), row * 1.8f)
        }
        box(118f, 67f, 65f, 110f, 0x224FE4CA, 0f)
        line(118f, 67f, 118f, 177f, accent, 1f)
        line(183f, 67f, 183f, 177f, accent, 1f)
        line(154f, 61f, 154f, 182f, Color.WHITE, 1.6f)
        box(149f, 59f, 10f, 6f, Color.WHITE, 2f)
        // Les deux poignées encadrent la portion sélectionnée.
        box(113f, 99f, 10f, 24f, accent, 3f)
        box(178f, 99f, 10f, 24f, accent, 3f)
        for (x in floatArrayOf(118f, 183f)) line(x, 106f, x, 116f, 0xFF173D40.toInt(), 2f)
    }
}

class Sf2CreatorHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFF4B86B.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        waveform(41f, 101f, 219f, 22f, 0x445EDBCD, .7f)
        canvas.save()
        canvas.rotate(23f, 111f, 137f)
        glow(110f, 119f, 88f, accent)
        val body = Path().apply {
            moveTo(99f, 88f)
            cubicTo(77f, 79f, 58f, 103f, 77f, 124f)
            cubicTo(81f, 136f, 54f, 144f, 64f, 172f)
            cubicTo(74f, 204f, 145f, 204f, 157f, 173f)
            cubicTo(169f, 144f, 139f, 136f, 144f, 122f)
            cubicTo(161f, 96f, 136f, 79f, 120f, 88f)
            close()
        }
        canvas.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(65f, 100f, 156f, 181f, 0xFFF1C37E.toInt(), 0xFFAE5F34.toInt(), Shader.TileMode.CLAMP)
        })
        box(101f, 37f, 17f, 89f, 0xFF684532.toInt(), 2f)
        box(99f, 14f, 21f, 30f, 0xFFCC985D.toInt(), 5f)
        for (i in 0..2) {
            line(95f, 21f + i * 8f, 123f, 21f + i * 8f, 0xFFBDC7D0.toInt(), 3f)
        }
        oval(94f, 119f, 33f, 33f, 0xFFF4CF8B.toInt())
        oval(98f, 123f, 25f, 25f, 0xFF302721.toInt())
        box(92f, 165f, 36f, 9f, 0xFF543528.toInt(), 2f)
        for (i in 0..5) line(104f + i * 2.2f, 30f, 104f + i * 2.2f, 169f, 0xAACEE0E8.toInt(), .65f)
        for (i in 0..8) line(102f, 50f + i * 8f, 117f, 50f + i * 8f, 0xFFCAAA80.toInt(), .8f)
        canvas.restore()
        microphone(218f, 71f, .77f, 0xFF6CDACF.toInt())
    }
}

class DictaphoneHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFFF7896.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        waveform(32f, 113f, 68f, 23f, 0xFF88516F.toInt(), .3f)
        waveform(200f, 113f, 68f, 23f, 0xFF88516F.toInt(), 1.3f)
        microphone(150f, 30f, 1.12f, accent)
        box(202f, 39f, 48f, 24f, 0xFF49283F.toInt(), 12f)
        oval(211f, 46f, 10f, 10f, 0xFFFF607B.toInt())
        for (i in 0..2) line(229f + i * 5f, 47f, 229f + i * 5f, 55f, 0xFFFFAABD.toInt(), 2f)
    }
}
