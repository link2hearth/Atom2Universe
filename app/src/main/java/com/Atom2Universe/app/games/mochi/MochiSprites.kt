package com.Atom2Universe.app.games.mochi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.lang.ref.WeakReference

/** A shared, density-independent WebP pack; no decoding in the drawing loop. */
internal class MochiSprites private constructor(context: Context) {
    private val assets = context.assets
    private fun read(name: String): Bitmap = assets.open("mochi/$name.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })) { "Invalid Mochi asset: $name" }
    }
    val gardens = arrayOf(read("garden_dawn"), read("garden_sunset"), read("garden_moon"))
    val flowers = arrayOf(read("flower_gold"), read("flower_spring"), read("flower_drift"),
        read("flower_fragile"), read("flower_rest"))
    val faces = arrayOf(read("mochi_happy"), read("mochi_blink"), read("mochi_fly"), read("mochi_hurt"))
    val hero = read("mochi_hero")
    val leaves = read("leaves")
    val bee = read("bee")
    val thorn = read("thorn")
    val peach = read("peach")
    val starfruit = read("starfruit")
    companion object {
        private var cached = WeakReference<MochiSprites>(null)
        @Synchronized fun get(context: Context): MochiSprites = cached.get()
            ?: MochiSprites(context.applicationContext).also { cached = WeakReference(it) }
    }
}
