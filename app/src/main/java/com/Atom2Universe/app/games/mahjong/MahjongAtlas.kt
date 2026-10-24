package com.Atom2Universe.app.games.mahjong

import android.content.Context
import android.graphics.*

/** Shared compact atlases, under 4.3 MiB decoded together; no allocation while drawing. */
internal object MahjongAtlas {
    @Volatile private var bitmap: Bitmap? = null
    private var treasures: Bitmap? = null
    private var textures: Bitmap? = null
    @Synchronized fun load(context: Context) {
        if (bitmap != null) return
        bitmap = context.assets.open("mahjong/porcelain_atlas.webp").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        }
        treasures = context.assets.open("mahjong/treasure_atlas.webp").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        }
        textures = context.assets.open("mahjong/texture_atlas.webp").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        }
    }
    fun draw(canvas: Canvas, style: Int, target: RectF, paint: Paint, source: Rect): Boolean {
        val image = bitmap ?: return false
        val id = style.coerceIn(0, 23)
        val size=image.width/6
        source.set(id % 6 * size, id / 6 * size, id % 6 * size + size, id / 6 * size + size)
        canvas.drawBitmap(image, source, target, paint)
        return true
    }
    fun treasure(canvas: Canvas, id: Int, target: RectF, paint: Paint, source: Rect): Boolean {
        val image=treasures ?: return false
        val size=image.width/2
        source.set(id%2*size,id/2*size,id%2*size+size,id/2*size+size)
        canvas.drawBitmap(image,source,target,paint)
        return true
    }
    fun texture(canvas: Canvas, style: Int, target: RectF, paint: Paint, source: Rect) {
        val image=textures ?: return
        val id=style.coerceIn(0,23); val size=image.width/6
        source.set(id%6*size,id/6*size,id%6*size+size,id/6*size+size)
        canvas.drawBitmap(image,source,target,paint)
    }
}
