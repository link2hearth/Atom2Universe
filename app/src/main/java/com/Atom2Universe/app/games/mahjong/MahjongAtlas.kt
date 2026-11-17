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
    // ---- Fonds (paysages derrière le plateau) ------------------------------------------------
    const val TABLES = 12
    private val tableFiles = arrayOf("00_valley", "01_lotus", "02_bamboo", "03_tea", "04_palace", "05_koi",
        "06_monastery", "07_cherry", "08_harbor", "09_autumn", "10_starry", "11_snow")
    /** Voile en plus pour les paysages clairs, mesuré sur leur luminosité moyenne. */
    private val tableDim = floatArrayOf(0f, .23f, .26f, .10f, .38f, 0f, .49f, 0f, 0f, .28f, 0f, 0f)
    fun tableDim(id: Int) = tableDim[id.coerceIn(0, TABLES - 1)]
    private var tableId = -1
    private var table: Bitmap? = null
    private val thumbs = arrayOfNulls<Bitmap>(TABLES)

    /** Le paysage équipé, en pleine taille ; un seul gardé en mémoire à la fois. */
    @Synchronized fun table(context: Context, id: Int): Bitmap? {
        val safe = id.coerceIn(0, TABLES - 1)
        if (safe == tableId) return table
        return decode(context, safe, 1)?.also { table = it; tableId = safe }
    }

    private var bakedKey = -1L
    private var baked: Bitmap? = null

    /**
     * Le paysage équipé déjà recadré et voilé, à la résolution de l'image (pas de l'écran) ;
     * un seul gardé, refait quand le fond ou la taille de l'écran change. L'ancien n'est pas
     * recyclé : une liste d'affichage peut encore le tenir, le ramasse-miettes s'en charge.
     */
    @Synchronized fun bakedTable(key: Long, make: () -> Bitmap): Bitmap {
        baked?.takeIf { bakedKey == key }?.let { return it }
        return make().also { baked = it; bakedKey = key }
    }

    /** Petite version (1/4) pour les cartes de la collection. */
    @Synchronized fun tableThumb(context: Context, id: Int): Bitmap? {
        val safe = id.coerceIn(0, TABLES - 1)
        return thumbs[safe] ?: decode(context, safe, 4)?.also { thumbs[safe] = it }
    }

    private fun decode(context: Context, id: Int, sample: Int): Bitmap? = runCatching {
        context.assets.open("mahjong/tables/${tableFiles[id]}.webp").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false; inSampleSize = sample })
        }
    }.getOrNull()

    fun texture(canvas: Canvas, style: Int, target: RectF, paint: Paint, source: Rect) {
        val image=textures ?: return
        val id=style.coerceIn(0,23); val size=image.width/6
        source.set(id%6*size,id/6*size,id%6*size+size,id/6*size+size)
        canvas.drawBitmap(image,source,target,paint)
    }
}
