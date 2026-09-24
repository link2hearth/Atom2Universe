package com.Atom2Universe.app.hub

import android.content.Context
import android.graphics.Color
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
        box(91f, 174f, 119f, 28f, 0xFF33445F.toInt(), 4f)
        for (i in 0..8) {
            box(94f + i * 13f, 177f, 11f, 22f, 0xFFD8E3EE.toInt(), 1f)
            if (i % 7 in intArrayOf(0, 1, 3, 4, 5)) box(103f + i * 13f, 177f, 6f, 13f, 0xFF101A2D.toInt(), 1f)
        }
    }
}

class GamesOverviewHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF789DF1.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        glow(148f, 117f, 112f, accent)
        canvas.save()
        canvas.rotate(-9f, 91f, 53f)
        box(59f, 16f, 55f, 67f, 0xFFD7E2EF.toInt(), 9f)
        for ((x, y) in arrayOf(70f to 29f, 96f to 29f, 83f to 46f, 70f to 63f, 96f to 63f))
            oval(x, y, 6f, 6f, 0xFF4C6385.toInt())
        canvas.restore()
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
