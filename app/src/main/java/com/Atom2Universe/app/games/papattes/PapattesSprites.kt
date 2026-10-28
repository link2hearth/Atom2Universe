package com.Atom2Universe.app.games.papattes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.lang.ref.WeakReference

/** Un sprite et ses points d'accroche, en pixels du bitmap. */
class Sprite(val bitmap: Bitmap, private val anchors: Map<String, FloatArray>) {
    fun anchor(name: String): FloatArray = anchors[name] ?: floatArrayOf(bitmap.width / 2f, bitmap.height / 2f)
}

/**
 * Le paquet d'images de Grandes papattes, partagé entre l'écran de jeu, le Labo et la tuile.
 * Les pièces des bestioles et les accessoires sont décodés une fois ; les grands fonds ne le sont
 * qu'à la demande, un biome à la fois. Rien n'est décodé dans la boucle de dessin.
 */
internal class PapattesSprites private constructor(context: Context) {
    private val assets = context.assets
    private val anchors: JSONObject = assets.open("papattes/sprites.json").use { JSONObject(it.reader().readText()) }

    private fun decode(name: String, alpha: Boolean = true): Bitmap = assets.open("papattes/$name.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = if (alpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        })) { "Invalid Papattes asset: $name" }
    }

    private fun sprite(name: String): Sprite {
        val map = HashMap<String, FloatArray>()
        anchors.optJSONObject(name)?.optJSONObject("anchors")?.let { a ->
            for (key in a.keys()) {
                val p = a.getJSONArray(key)
                map[key] = floatArrayOf(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
            }
        }
        return Sprite(decode(name), map)
    }

    /** Une famille de pièces décodées à la première demande : on n'a jamais toutes les têtes à l'écran à la fois. */
    inner class Family(private val names: List<String>) {
        private val cache = arrayOfNulls<Sprite>(names.size)
        @Synchronized operator fun get(i: Int): Sprite = cache[i] ?: sprite(names[i]).also { cache[i] = it }
    }

    val heads = Family(Head.entries.map { "head_${it.id}" })
    val bodies = Family(Body.entries.map { "body_${it.id}" })
    val thighs = Family(Legs.entries.map { "thigh_${it.id}" })
    val shins = Family(Legs.entries.map { "shin_${it.id}" })

    /** Une vignette réduite de [name] pour le carnet, décodée au quart de sa taille. */
    fun thumbnail(name: String): Bitmap = assets.open("papattes/$name.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })) {
            "Invalid Papattes asset: $name"
        }
    }
    val finishFlag = sprite("prop_finish_flag")
    val checkpointFlag = sprite("prop_checkpoint_flag")
    val egg = sprite("prop_egg")
    val seed = sprite("prop_seed")
    val flask = sprite("prop_flask")
    val decor = mapOf(
        DecorKind.ROCK to sprite("prop_rock"),
        DecorKind.BUSH to sprite("prop_bush"),
        DecorKind.MUSHROOM to sprite("prop_mushroom"),
        DecorKind.REEDS to sprite("prop_reeds"),
        DecorKind.LILY_PAD to sprite("prop_lily_pad"),
        DecorKind.SNOW_PILE to sprite("prop_snow_pile")
    )

    private var backdropBiome: Biome? = null
    private var backdropBitmap: Bitmap? = null

    /** Le fond lointain d'un biome ; l'ancien biome est libéré. */
    @Synchronized fun backdrop(biome: Biome): Bitmap {
        if (backdropBiome != biome) {
            val id = when (biome) { Biome.MEADOW -> "meadow"; Biome.MARSH -> "marsh"; Biome.ICE -> "ice" }
            backdropBitmap = decode("bg_$id", alpha = false)
            backdropBiome = biome
        }
        return backdropBitmap!!
    }

    companion object {
        private var cached = WeakReference<PapattesSprites>(null)
        @Synchronized fun get(context: Context): PapattesSprites = cached.get()
            ?: PapattesSprites(context.applicationContext).also { cached = WeakReference(it) }
    }
}
