package com.Atom2Universe.app.hub

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audio.AudioHubTileDrawable
import com.Atom2Universe.app.audio.AudioTileScene
import kotlin.math.cos
import kotlin.math.sin

/** Les six portes d'entrée : même lumière de studio que les illustrations des modules. */
class AudioOverviewHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFA68BFA.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        waveform(36f, 111f, 228f, 30f, 0xFF486A87.toInt())
        glow(150f, 91f, 100f, accent)
        arc(80f, 24f, 140f, 160f, 180f, 180f, 0xFFC5CBE2.toInt(), 12f)
        arc(88f, 32f, 124f, 144f, 185f, 170f, accent, 3f)
        for (x in floatArrayOf(73f, 195f)) {
            box(x, 102f, 32f, 62f, 0xFF455271.toInt(), 12f)
            box(x + 6f, 109f, 20f, 47f, 0xFF121F34.toInt(), 8f)
            line(x + 4f, 118f, x + 4f, 145f, accent, 3f)
        }
        note(147f, 104f, 0xFFF0D8A5.toInt())
    }
}

class GamesOverviewHubTileDrawable(context: Context) : AudioHubTileDrawable(0xFF789DF1.toInt()) {
    private val aceRank = context.getString(R.string.hub_artwork_ace_rank)

    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        glow(148f, 117f, 112f, accent)
        // As de cœur : une vraie enseigne et deux index opposés, lisibles en miniature.
        canvas.save()
        canvas.rotate(-12f, 84f, 53f)
        box(55f, 14f, 58f, 78f, 0xFF8B9BB4.toInt(), 6f)
        box(56f, 14f, 55f, 75f, 0xFFF4EBDD.toInt(), 5f)
        heart(this, 70f, 39f, 27f)
        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFBC4D64.toInt()
            textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        repeat(2) {
            canvas.save()
            if (it == 1) canvas.rotate(180f, 83.5f, 51.5f)
            canvas.drawText(aceRank, 61f, 29f, rankPaint)
            heart(this, 61f, 32f, 8f)
            canvas.restore()
        }
        canvas.restore()

        // Silhouettes d'échecs au-dessus de la manette : roi ivoire et pion bleu.
        chessPiece(this, 217f, 21f, king = true)
        chessPiece(this, 171f, 39f, king = false)

        box(54f, 89f, 192f, 80f, 0xFF7488AE.toInt(), 37f)
        oval(49f, 109f, 67f, 82f, 0xFF7488AE.toInt())
        oval(184f, 109f, 67f, 82f, 0xFF7488AE.toInt())
        box(64f, 99f, 172f, 57f, 0xFF293B57.toInt(), 25f)
        box(83f, 109f, 12f, 38f, 0xFFCBD8EB.toInt(), 3f)
        box(70f, 122f, 38f, 12f, 0xFFCBD8EB.toInt(), 3f)
        for (i in 0..3) {
            val a = i * Math.PI / 2
            oval(209f + cos(a).toFloat() * 13f - 5f, 126f + sin(a).toFloat() * 13f - 5f,
                10f, 10f, intArrayOf(0xFFE69AAF.toInt(), 0xFF77CFBD.toInt(), 0xFFF1C884.toInt(), accent)[i])
        }
        oval(119f, 144f, 22f, 22f, 0xFF17273D.toInt())
        oval(159f, 144f, 22f, 22f, 0xFF17273D.toInt())
        line(143f, 120f, 154f, 120f, accent, 3f)

        // Dé au premier plan, face cinq ; les points restent sur le dé uniquement.
        canvas.save()
        canvas.rotate(-12f, 58f, 169f)
        box(32f, 145f, 49f, 49f, 0xFF91A6BC.toInt(), 8f)
        box(32f, 142f, 46f, 46f, 0xFFF0E7D7.toInt(), 7f)
        for ((x, y) in arrayOf(43f to 153f, 67f to 153f, 55f to 165f, 43f to 177f, 67f to 177f)) {
            oval(x - 3.5f, y - 3.5f, 7f, 7f, 0xFF293B57.toInt())
        }
        canvas.restore()

        // Pions de dames, avec épaisseur et anneau gravé.
        for ((x, y, color) in arrayOf(
            Triple(222f, 163f, 0xFFCF9F70.toInt()),
            Triple(238f, 179f, 0xFFDCB98B.toInt())
        )) {
            oval(x, y + 6f, 35f, 18f, 0xFF7C5947.toInt())
            box(x, y + 9f, 35f, 6f, 0xFF7C5947.toInt(), 0f)
            oval(x, y, 35f, 18f, color)
            arc(x + 5f, y + 3f, 25f, 11f, 0f, 360f, 0xFF956A4C.toInt(), 1.5f)
        }
    }

    private fun heart(scene: AudioTileScene, x: Float, y: Float, size: Float) {
        val path = Path().apply {
            moveTo(x + size * .5f, y + size)
            cubicTo(x - size * .45f, y + size * .35f, x + size * .05f, y - size * .3f,
                x + size * .5f, y + size * .2f)
            cubicTo(x + size * .95f, y - size * .3f, x + size * 1.45f, y + size * .35f,
                x + size * .5f, y + size)
            close()
        }
        scene.canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBC4D64.toInt() })
    }

    private fun chessPiece(scene: AudioTileScene, x: Float, y: Float, king: Boolean) = with(scene) {
        val body = if (king) 0xFFE9D6B4.toInt() else 0xFF92B5CB.toInt()
        val shade = if (king) 0xFFB69876.toInt() else 0xFF587F9D.toInt()
        canvas.save()
        canvas.translate(x, y)
        if (king) {
            line(0f, 0f, 0f, 14f, body, 5f)
            line(-6f, 5f, 6f, 5f, body, 5f)
            box(-11f, 16f, 22f, 10f, body, 3f)
        } else {
            oval(-10f, 4f, 20f, 20f, body)
        }
        val stem = Path().apply {
            moveTo(-7f, 27f)
            cubicTo(-5f, 37f, -9f, 45f, -15f, 50f)
            lineTo(15f, 50f)
            cubicTo(9f, 45f, 5f, 37f, 7f, 27f)
            close()
        }
        canvas.drawPath(stem, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = body })
        box(-11f, 25f, 22f, 5f, shade, 2f)
        box(-17f, 49f, 34f, 7f, body, 3f)
        box(-19f, 55f, 38f, 6f, shade, 2f)
        canvas.restore()
    }
}

class CreativeOverviewHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF79CCB8.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        canvas.save()
        canvas.rotate(-8f, 129f, 113f)
        box(52f, 24f, 157f, 170f, 0xFF466E6F.toInt(), 9f)
        box(63f, 20f, 153f, 168f, 0xFFE4DDCA.toInt(), 7f)
        for (i in 0..6) line(55f, 37f + i * 22f, 70f, 37f + i * 22f, 0xFFBFCBDC.toInt(), 3f)
        for (i in 0..4) line(85f, 46f + i * 13f, 157f - i * 7f, 46f + i * 13f, 0xFFACAF9B.toInt(), 2f)
        for (row in 0..4) for (col in 0..4) {
            if (row + col in 2..6) box(111f + col * 13f, 109f + row * 13f, 11f, 11f,
                if ((row + col) % 2 == 0) 0xFF9676C5.toInt() else 0xFF73B3B0.toInt(), 0f)
        }
        canvas.restore()
        line(209f, 182f, 241f, 61f, 0xFFCC9B66.toInt(), 9f)
        line(240f, 66f, 246f, 45f, 0xFFBDD1DC.toInt(), 10f)
        oval(241f, 25f, 15f, 26f, 0xFFE79EAD.toInt())
        for (i in 0..2) oval(224f + i * 12f, 184f - i * 5f, 8f, 8f,
            intArrayOf(accent, 0xFFE79EAD.toInt(), 0xFFF0CC8A.toInt())[i])
    }
}

class ReadingOverviewHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFE1B886.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        val colors = intArrayOf(0xFF957EAD.toInt(), 0xFF5E959A.toInt(), 0xFFBD8F64.toInt())
        for (i in 0..2) {
            val y = 143f + i * 20f
            box(52f + i * 5f, y, 192f - i * 9f, 17f, colors[i], 4f)
            box(67f + i * 5f, y + 4f, 169f - i * 9f, 9f, 0xFFE0D8C6.toInt(), 2f)
        }
        canvas.save()
        canvas.rotate(-9f, 150f, 93f)
        box(88f, 20f, 125f, 122f, 0xFF527B83.toInt(), 7f)
        box(97f, 25f, 111f, 108f, 0xFF243F53.toInt(), 3f)
        oval(155f, 42f, 27f, 27f, accent)
        for (i in 0..3) line(116f, 86f + i * 10f, 187f - (i % 2) * 16f, 86f + i * 10f, 0xFF819BA4.toInt(), 2f)
        box(113f, 20f, 12f, 40f, 0xFFD8969C.toInt(), 1f)
        canvas.restore()
    }
}

class ScienceOverviewHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF74C7E6.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        glow(150f, 109f, 108f, accent)
        for (angle in intArrayOf(0, 60, 120)) {
            canvas.save()
            canvas.rotate(angle.toFloat(), 150f, 109f)
            arc(43f, 70f, 214f, 78f, 0f, 360f, 0xFF5C8FA9.toInt(), 2f)
            oval(49f, 88f, 10f, 10f, 0xFFF2CF91.toInt())
            canvas.restore()
        }
        oval(131f, 91f, 38f, 38f, 0xFF8CD3D6.toInt())
        oval(136f, 95f, 12f, 12f, 0xFFD2ECE8.toInt())
        for (i in 0..11) oval(35f + (i * 47 % 231), 19f + (i * 29 % 179), 2f, 2f, 0xFF8CA6C1.toInt())
    }
}

class StatsOverviewHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF8BCDBF.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        box(37f, 29f, 225f, 166f, 0xFF30455D.toInt(), 10f)
        box(44f, 36f, 211f, 152f, 0xFF13243A.toInt(), 6f)
        arc(65f, 51f, 68f, 68f, -90f, 220f, accent, 11f)
        arc(65f, 51f, 68f, 68f, 140f, 80f, 0xFFE6B980.toInt(), 11f)
        arc(65f, 51f, 68f, 68f, 230f, 30f, 0xFFA78ADD.toInt(), 11f)
        for (i in 0..3) {
            val h = 18f + i * 13f
            box(156f + i * 22f, 120f - h, 13f, h, if (i % 2 == 0) accent else 0xFF879ADD.toInt(), 3f)
        }
        val ys = floatArrayOf(169f, 157f, 164f, 145f, 153f, 132f)
        for (i in 0..4) line(61f + i * 34f, ys[i], 95f + i * 34f, ys[i + 1], 0xFFB8A4EC.toInt(), 3f)
        for (i in ys.indices) oval(58f + i * 34f, ys[i] - 3f, 6f, 6f, Color.WHITE)
    }
}
