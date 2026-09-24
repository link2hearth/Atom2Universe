package com.Atom2Universe.app.hub

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.Atom2Universe.app.audio.AudioHubTileDrawable
import com.Atom2Universe.app.audio.AudioTileScene

// Même éclairage, proportions et cache que les décors audio. Aucun libellé dans les dessins.
private const val PAPER = 0xFFE6DCC7.toInt()
private const val INK = 0xFF65758D.toInt()

private fun AudioTileScene.polygon(color: Int, vararg points: Float) {
    val path = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        close()
    }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}

private fun AudioTileScene.book() {
    polygon(0xFF775949.toInt(), 36f, 69f, 146f, 82f, 264f, 69f, 264f, 176f, 150f, 192f, 36f, 175f)
    polygon(PAPER, 42f, 61f, 149f, 78f, 149f, 179f, 42f, 165f)
    polygon(0xFFF6EDD9.toInt(), 151f, 78f, 258f, 61f, 258f, 165f, 151f, 179f)
    for (i in 0..6) {
        val y = 85f + i * 11f
        line(56f, y, 133f - (i % 3) * 8f, y + 11f, 0xFFAAA591.toInt(), 2f)
        line(167f, y + 11f, 245f - (i % 3) * 9f, y, 0xFFBAB09B.toInt(), 2f)
    }
    line(150f, 80f, 150f, 180f, 0xFFAA8F71.toInt(), 3f)
    polygon(0xFFCA6F73.toInt(), 207f, 69f, 223f, 67f, 223f, 119f, 215f, 111f, 207f, 122f)
}

class BooksHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFE8BC7C.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        glow(150f, 90f, 115f, accent)
        // Rayonnage discret derrière le livre ouvert.
        for (i in 0..8) {
            val h = 35f + (i * 13 % 29)
            box(42f + i * 25f, 70f - h, 17f, h, if (i % 2 == 0) 0xFF39485D.toInt() else 0xFF655248.toInt(), 2f)
            line(46f + i * 25f, 62f, 54f + i * 25f, 62f, 0xFFAA9070.toInt(), 1f)
        }
        book()
    }
}

class ComicsHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFB68CFF.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        canvas.save()
        canvas.rotate(-7f, 150f, 110f)
        box(57f, 24f, 190f, 180f, 0xFF554575.toInt(), 7f)
        box(49f, 17f, 190f, 180f, PAPER, 5f)
        box(57f, 25f, 174f, 71f, 0xFF324065.toInt(), 1f)
        oval(177f, 34f, 34f, 34f, 0xFFF0C97D.toInt())
        for (i in 0..9) {
            val h = 13f + (i * 17 % 31)
            box(60f + i * 17f, 94f - h, 14f, h, 0xFF17283C.toInt(), 0f)
        }
        box(57f, 103f, 79f, 85f, 0xFF5592A2.toInt(), 1f)
        box(143f, 103f, 88f, 85f, 0xFFB56880.toInt(), 1f)
        // Une bulle sans texte et une étoile d'action : langage visuel de la BD.
        oval(66f, 111f, 61f, 33f, 0xFFF7EEDB.toInt())
        polygon(0xFFF7EEDB.toInt(), 86f, 139f, 76f, 155f, 106f, 140f)
        polygon(0xFFFFDA8E.toInt(), 187f, 110f, 194f, 132f, 219f, 126f, 208f, 145f,
            223f, 163f, 198f, 161f, 187f, 182f, 180f, 160f, 152f, 168f, 169f, 146f, 155f, 124f, 180f, 132f)
        canvas.restore()
    }
}

class NotesHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF78CDBD.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        canvas.save()
        canvas.rotate(-8f, 144f, 110f)
        box(68f, 25f, 147f, 179f, 0xFF38685F.toInt(), 9f)
        box(76f, 20f, 140f, 175f, PAPER, 7f)
        for (i in 0..7) {
            oval(83f, 32f + i * 20f, 5f, 5f, INK)
            line(69f, 34f + i * 20f, 85f, 34f + i * 20f, 0xFFADBEBD.toInt(), 3f)
        }
        box(102f, 43f, 76f, 7f, 0xFF617C79.toInt(), 2f)
        for (i in 0..5) {
            val y = 69f + i * 18f
            line(104f, y, 198f - (i % 3) * 12f, y, 0xFFAAA996.toInt(), 2f)
        }
        box(176f, 19f, 17f, 39f, 0xFFD77E85.toInt(), 1f)
        canvas.restore()
        canvas.save()
        canvas.rotate(31f, 221f, 120f)
        box(215f, 57f, 12f, 103f, 0xFFE4B573.toInt(), 2f)
        box(215f, 49f, 12f, 15f, 0xFFCE8698.toInt(), 3f)
        polygon(PAPER, 215f, 160f, 227f, 160f, 221f, 181f)
        polygon(INK, 218f, 172f, 224f, 172f, 221f, 181f)
        canvas.restore()
    }
}

class PixelArtHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFB28CFF.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        box(47f, 17f, 206f, 171f, 0xFF33445F.toInt(), 9f)
        box(55f, 25f, 190f, 155f, 0xFF14263B.toInt(), 4f)
        val sprite = arrayOf("000011110000", "001122221100", "012222222210", "122322232221",
            "122222222221", "122233222221", "012222222210", "001222222100", "001100001100")
        val palette = intArrayOf(0, 0xFF5D4D94.toInt(), 0xFFB4A0EB.toInt(), 0xFFF0D18F.toInt())
        for (row in sprite.indices) for (col in sprite[row].indices) {
            val value = sprite[row][col] - '0'
            box(65f + col * 14f, 35f + row * 14f, 12f, 12f,
                if (value == 0) 0xFF23374D.toInt() else palette[value], 0f)
        }
        for (i in 0..6) box(70f + i * 24f, 195f, 17f, 9f,
            intArrayOf(accent, 0xFF67CDBF.toInt(), 0xFFF0D18F.toInt(), 0xFFE98DAB.toInt())[i % 4], 2f)
    }
}

class CanvasHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF6ACAC5.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        line(110f, 36f, 78f, 209f, 0xFFA97B54.toInt(), 8f)
        line(185f, 36f, 219f, 209f, 0xFFA97B54.toInt(), 8f)
        box(56f, 31f, 190f, 143f, PAPER, 5f)
        box(65f, 40f, 172f, 125f, 0xFF3A657B.toInt(), 1f)
        oval(177f, 54f, 33f, 33f, 0xFFF4D394.toInt())
        polygon(0xFF7397A2.toInt(), 65f, 140f, 116f, 65f, 166f, 128f, 190f, 101f, 237f, 149f, 237f, 165f, 65f, 165f)
        polygon(0xFFB9D7D1.toInt(), 98f, 92f, 116f, 65f, 138f, 93f, 117f, 86f, 108f, 97f)
        polygon(0xFF315B60.toInt(), 65f, 150f, 128f, 119f, 198f, 153f, 237f, 129f, 237f, 165f, 65f, 165f)
        box(49f, 174f, 204f, 8f, 0xFFC99A68.toInt(), 2f)
        line(246f, 190f, 263f, 122f, 0xFFB8CDD5.toInt(), 5f)
        polygon(0xFFCC8B9D.toInt(), 259f, 130f, 253f, 110f, 268f, 99f, 268f, 125f)
    }
}

private fun AudioTileScene.chart(accent: Int) {
    box(41f, 77f, 219f, 117f, 0xFF20334A.toInt(), 8f)
    for (i in 0..3) line(56f, 96f + i * 25f, 247f, 96f + i * 25f, 0xFF37485E.toInt(), 1f)
    val heights = floatArrayOf(28f, 43f, 34f, 59f, 73f, 90f)
    for (i in heights.indices) {
        box(62f + i * 30f, 182f - heights[i], 16f, heights[i], accent, 3f)
        box(65f + i * 30f, 185f - heights[i], 3f, heights[i] - 6f, 0x66FFFFFF, 1f)
    }
}

class AudioStatsHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFB391EF.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        chart(accent)
        arc(110f, 8f, 80f, 79f, 180f, 180f, 0xFFD4C7EF.toInt(), 7f)
        box(104f, 44f, 16f, 31f, accent, 6f)
        box(180f, 44f, 16f, 31f, accent, 6f)
        note(150f, 57f, 0xFFF1DDA7.toInt())
    }
}

class GameStatsHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFFE4B771.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        chart(accent)
        // Trophée au-dessus du tableau de progression.
        arc(100f, 10f, 36f, 40f, 75f, 260f, accent, 4f)
        arc(164f, 10f, 36f, 40f, -155f, 260f, accent, 4f)
        polygon(0xFFF5D497.toInt(), 119f, 8f, 181f, 8f, 172f, 43f, 150f, 57f, 128f, 43f)
        line(150f, 54f, 150f, 69f, accent, 6f)
        box(130f, 68f, 40f, 6f, accent, 2f)
    }
}

class ReadingStatsHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : AudioHubTileDrawable(0xFF77CAB7.toInt()) {
    override fun drawScene(scene: AudioTileScene, accent: Int) = with(scene) {
        chart(accent)
        canvas.save()
        canvas.translate(59f, -27f)
        canvas.scale(.61f, .51f)
        book()
        canvas.restore()
        oval(223f, 35f, 34f, 34f, 0xFF2D5C5D.toInt())
        line(240f, 41f, 240f, 52f, Color.WHITE, 2f)
        line(240f, 52f, 248f, 56f, Color.WHITE, 2f)
    }
}
