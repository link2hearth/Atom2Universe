package com.Atom2Universe.app.games.caves.render

import android.graphics.*
import com.Atom2Universe.app.games.caves.node.BlockDef
import com.Atom2Universe.app.games.caves.world.PartialBlockModel
import com.Atom2Universe.app.games.caves.world.TorchModel

/** UI thumbnails baked once with the atlas, never while scrolling the inventory. */
internal object BlockItemIcon {
    private val cubeFaces = run {
        val corners = Array(8) { i -> floatArrayOf(
            (i and 1).toFloat(), ((i shr 1) and 1).toFloat(), ((i shr 2) and 1).toFloat()) }
        TorchModel.faces.mapIndexed { direction, indices ->
            PartialBlockModel.Face(direction, indices.map { corners[it] }.toTypedArray())
        }
    }

    fun create(def: BlockDef, textures: List<Bitmap>): Bitmap {
        val result = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { isFilterBitmap = false }
        // Camera looks at the front (-Z) and right (+X), so steps and facades stay visible.
        fun project(v: FloatArray) = floatArrayOf(48f + (v[0]+v[2]-1f)*39f,
            48f + (v[0]-v[2])*19.5f - (v[1]-.5f)*43f)
        val faces = if (def.partial) PartialBlockModel.faces(def, 0)
            else cubeFaces
        val visible = faces.filter { it.direction == 0 || it.direction == 2 || it.direction == 5 }
            .sortedBy { face -> face.vertices.sumOf { (it[0]-it[2]+it[1]*39f/43f).toDouble() } / 4 }
        for (face in visible) {
            val layer = when (face.texture) {
                0 -> def.layerTop
                1 -> def.layerBottom
                2 -> def.layerSide
                3 -> def.layerFront
                else -> when (face.direction) { 0 -> def.layerTop; 5 -> def.layerFront; else -> def.layerSide }
            }
            val bitmap = textures[if (layer >= 0) layer else def.layerSide]
            val points = face.vertices.map(::project)
            val path = Path().apply {
                moveTo(points[0][0], points[0][1])
                for (p in points.drop(1)) lineTo(p[0], p[1])
                close()
            }
            val uv = face.uv ?: face.vertices.map { v ->
                when (face.direction) {
                    0 -> floatArrayOf(v[0], v[2])
                    2 -> floatArrayOf(1f-v[2], 1f-v[1])
                    else -> floatArrayOf(v[0], 1f-v[1])
                }
            }.toTypedArray()
            val source = FloatArray(6) { i -> uv[i/2][i%2] * if (i%2 == 0) bitmap.width else bitmap.height }
            val target = FloatArray(6) { i -> points[i/2][i%2] }
            val matrix = Matrix().apply { setPolyToPoly(source, 0, target, 0, 3) }
            paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
            val light = when(face.direction) { 0 -> 1f; 5 -> .86f; else -> .67f }
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                light,0f,0f,0f,0f, 0f,light,0f,0f,0f, 0f,0f,light,0f,0f, 0f,0f,0f,1f,0f)))
            canvas.drawPath(path, paint)
        }
        return result
    }
}
