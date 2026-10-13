package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

/** Un dessin prêt à coller : [ox], [oy] sont la position, dans le bitmap, du coin arrière de l'empreinte. */
class Sprite(val bmp: Bitmap, val ox: Float, val oy: Float) {
    private var mirror: Sprite? = null

    /** Première ligne du bitmap qui contient un pixel visible : les bulles se posent au-dessus. */
    val topRow: Int by lazy {
        val w = bmp.width
        val row = IntArray(w)
        var found = bmp.height / 2
        loop@ for (y in 0 until bmp.height) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (px in row) if ((px ushr 24) > 40) { found = y; break@loop }
        }
        found
    }

    /** Le même dessin vu dans l'autre sens : l'empreinte w × d devient d × w. */
    fun mirrored(): Sprite = mirror ?: run {
        val m = Matrix().apply { preScale(-1f, 1f); postTranslate(bmp.width.toFloat(), 0f) }
        val b = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(b).drawBitmap(bmp, m, null)
        Sprite(b, bmp.width - ox, oy).also { mirror = it }
    }
}

/**
 * Tous les dessins du village, fabriqués à la demande puis gardés : chaque objet est peint une
 * fois dans un bitmap, et la vue ne fait ensuite que les coller.
 */
class HamletArt {

    companion object {
        /** Échelle des sprites par rapport à la tuile de 64 × 32. */
        const val S = 1.25f
        private const val PAD = 20f
        const val CHUNK = 8
    }

    private val cache = HashMap<String, Sprite>()
    private val chunks = HashMap<Long, Bitmap>()
    private var chunkRev = -1

    /** Les bitmaps restent utilisables par un dessin en cours ; le GC libère les caches à la fermeture. */
    fun clear() { cache.clear(); chunks.clear(); chunkRev = -1 }

    // ------------------------------------------------------------------ objets

    private fun heightOf(def: Def): Float {
        if (HamletArtCoastal.handles(def)) return HamletArtCoastal.height(def)
        if (def.look.kind == "port_decor" || def.look.kind == "port_nature") return 2.8f
        val k = def.look.kind
        val w = def.w; val d = def.d
        return when (def.cat) {
            Cat.HALL -> 5.0f
            Cat.FIELD -> 1.6f
            Cat.RANCH -> 2.6f
            Cat.HOME -> when (k) {
                "h_citadel" -> 5.2f
                "h_chateau", "h_palace", "h_manor", "h_mansion", "h_guild" -> 4.4f
                "h_tower", "h_town", "h_watch" -> 4.0f
                "h_tree" -> 3.2f
                else -> 2.9f
            }
            Cat.SHOP -> when (k) {
                "asset" -> if (def.look.n == 1) 4.3f else 3.8f
                "s_tower" -> 4.8f
                "s_mill" -> 3.4f
                "s_circus", "s_theater", "s_lair", "s_chapel" -> 3.8f
                else -> 3.0f + 0.15f * maxOf(w, d)
            }
            Cat.DECOR -> when (k) {
                "d_hotair" -> 2.9f
                "d_maypole", "d_totem", "d_xmas", "d_portal", "d_trebuchet", "d_statue", "d_obelisk" -> 2.9f
                "d_guardtower", "d_stage", "d_pavilion", "d_gazebo" -> 3.0f
                else -> 2.0f
            }
            Cat.NATURE -> when {
                k.startsWith("t_") -> if (w > 1) 3.8f else 2.6f
                k == "n_bamboo" -> 2.2f
                else -> 1.5f
            }
            Cat.PATH -> 0.5f
        }
    }

    private fun build(w: Int, d: Int, ht: Float, paint: (Iso) -> Unit): Sprite {
        val widthPx = (((w + d) * Iso.HW + 2 * PAD) * S).toInt() + 1
        val heightPx = ((ht * Iso.ZH + PAD + (w + d) * Iso.HH + 10f) * S).toInt() + 1
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(S, S)
        val ox = d * Iso.HW + PAD
        val oy = ht * Iso.ZH + PAD
        paint(Iso(c, ox, oy))
        return Sprite(bmp, ox * S, oy * S)
    }

    /** Le dessin d'une définition dans son état « normal » (pour les menus et le fantôme de pose). */
    fun forDef(def: Def, flip: Boolean = false, cropId: String? = null, stage: Int = -1): Sprite {
        val base = baseSprite(def, 0, cropId, stage)
        return if (flip) base.mirrored() else base
    }

    fun forObj(o: Obj, game: HamletGame): Sprite {
        val def = o.def
        val base = when {
            game.buildLeft(o) > 0 -> constructionSprite(def.w, def.d)
            def.cat == Cat.FIELD -> baseSprite(def, 0, o.crop, if (o.crop == null) -1 else game.cropStage(o))
            def.cat == Cat.HALL -> baseSprite(def, game.hall, null, -1)
            def.cat == Cat.RANCH -> baseSprite(def, -3, null, -1)
            def.link -> baseSprite(def, fenceMask(o, game), null, -1)
            HamletArtCoastal.handles(def) -> baseSprite(def, -5, null, -1)
            HamletArtWildlife.handles(def) -> baseSprite(def, -6, null, -1)
            def.look.kind == "asset" -> baseSprite(def, -4, null, -1)
            def.look.kind == "s_mill" && def.look.n == 0 -> baseSprite(def, -2, null, -1)
            else -> baseSprite(def, 0, null, -1)
        }
        return if (o.flip) base.mirrored() else base
    }

    private fun fenceMask(o: Obj, game: HamletGame): Int {
        fun linked(x: Int, y: Int) = game.objAt(x, y)?.def?.link == true
        var m = 0
        if (linked(o.x + 1, o.y)) m = m or 1
        if (linked(o.x - 1, o.y)) m = m or 2
        if (linked(o.x, o.y + 1)) m = m or 4
        if (linked(o.x, o.y - 1)) m = m or 8
        return m
    }

    private fun constructionSprite(w: Int, d: Int): Sprite =
        cache.getOrPut("build|$w|$d") { build(w, d, 1.6f) { HamletArtHomes.construction(it, w.toFloat(), d.toFloat()) } }

    private fun baseSprite(def: Def, variant: Int, cropId: String?, stage: Int): Sprite {
        val key = "${def.id}|$variant|${cropId ?: ""}|$stage"
        return cache.getOrPut(key) {
            build(def.w, def.d, heightOf(def)) { p ->
                val w = def.w.toFloat(); val d = def.d.toFloat()
                if (HamletArtCoastal.handles(def)) {
                    HamletArtCoastal.draw(p, def, w, d, variant, movingParts = variant != -5)
                    return@build
                }
                if (HamletArtWildlife.handles(def)) {
                    if (variant != -6) HamletArtWildlife.draw(p, def, w, d)
                    return@build
                }
                if (def.look.kind == "port_decor" || def.look.kind == "port_nature") {
                    HamletArtPort.draw(p, def, w, d)
                    return@build
                }
                when (def.cat) {
                    Cat.HALL -> hall(p, variant)
                    Cat.FIELD -> HamletArtCrops.field(p, def, cropId?.let { Hamlet.cropById[it] }, stage)
                    Cat.HOME -> HamletArtHomes.home(p, def.look, w, d)
                    Cat.SHOP -> if (def.look.kind == "asset") {
                        HamletArtAdventure.draw(p, def.look.n)
                        if (variant != -4) HamletArtAdventure.animate(p, def.look.n, 0f, 1f, false)
                    } else HamletArtHomes.shop(p, def, w, d, staticParts = variant != -2)
                    Cat.RANCH -> HamletArtAnimals.ranch(p, def, animals = variant != -3)
                    Cat.DECOR -> HamletArtDecor.draw(p, def, w, d, variant)
                    Cat.NATURE -> HamletArtNature.draw(p, def, w, d)
                    Cat.PATH -> {}
                }
            }
        }
    }

    // ------------------------------------------------------------------ la halle

    private fun hall(p: Iso, level: Int) {
        val w = 4f; val d = 4f
        when (level) {
            1 -> {
                HamletArtHomes.gableHouse(p, 4f, 3.2f, 0xEDE0BC, 0xB8452E, 0x7A4A22, floors = 1, wallH = 1.1f, roofH = 0.95f, ov = 0.18f, chimney = true, lines = 4, door = true)
                // tour de la cloche au fond à gauche
                p.box(0.5f, 0.35f, 0.07f, 1.3f, 1.15f, 2.6f, 0xE2D4AE)
                p.windowY(1.15f, 0.9f, 1.8f, 0.2f, 0.34f, 0x2A3A4A, 0x7A4A22)
                p.windowX(1.3f, 0.75f, 1.8f, 0.2f, 0.34f, 0x2A3A4A, 0x7A4A22)
                p.box(0.42f, 0.27f, 2.6f, 1.38f, 1.23f, 2.72f, 0x7A4A22)
                p.pyramid(0.9f, 0.75f, 0.52f, 0.52f, 2.7f, 1.0f, 0xB8452E, 4)
                p.line(0.9f, 0.75f, 3.7f, 0.9f, 0.75f, 4.15f, 0x5A4A32, 1.8f)
                p.face(0xE8B82A, 0.9f, 0.75f, 4.15f, 1.2f, 0.75f, 4.04f, 0.9f, 0.75f, 3.93f)
            }
            2 -> {
                HamletArtHomes.gableHouse(p, 4f, 3.4f, 0xCFC9BA, 0x7A4A42, 0x5A4A3A, floors = 2, wallH = 0.95f, roofH = 0.9f, ov = 0.15f, chimney = true, lines = 4, door = true)
                for (tx in listOf(0.6f, 3.4f)) {
                    p.cyl(tx, 0.7f, 0.07f, 2.8f, 0.4f, 0xDAD4C4)
                    p.cone(tx, 0.7f, 2.8f, 1.0f, 0.5f, 0x7A4A42)
                }
                p.line(2f, 1.2f, 2.4f, 2f, 1.2f, 3.4f, 0x5A4A32, 1.8f)
                p.face(0xC8392E, 2f, 1.2f, 3.4f, 2.35f, 1.2f, 3.26f, 2f, 1.2f, 3.12f)
            }
            3 -> HamletArtHomes.fortress(p, w, d, 0xD8D2C2, 0x8A3A32, 0xC8392E, domes = false, towers = 4)
            4 -> HamletArtHomes.fortress(p, w, d, 0xE6E0D2, 0x2F5A8A, 0xE8B82A, domes = false, towers = 8)
            5 -> HamletArtHomes.fortress(p, w, d, 0xF4EEDC, 0xE8B82A, 0xE8B82A, domes = true, towers = 8)
            6 -> HamletArtHomes.fortress(p, w, d, 0xE7E5DA, 0x438978, 0xE5BC66, domes = false, towers = 8)
            7 -> HamletArtHomes.fortress(p, w, d, 0xF0E0D3, 0x8762A4, 0xECD189, domes = true, towers = 8)
            else -> {
                HamletArtHomes.fortress(p, w, d, 0xFFF2D3, 0x438694, 0xF3D17C, domes = true, towers = 8)
                p.cyl(2f, 1.1f, 2.8f, 3.4f, 0.3f, 0xD8B85E)
                p.cone(2f, 1.1f, 3.4f, 0.7f, 0.42f, 0xF0DDA3)
            }
        }
        // oriflammes devant la porte
        p.line(0.3f, 3.8f, 0f, 0.3f, 3.8f, 1.2f, 0x6A4A2E, 2.4f)
        p.face(0xC8392E, 0.3f, 3.8f, 1.2f, 0.6f, 3.8f, 1.12f, 0.6f, 3.8f, 0.7f, 0.3f, 3.8f, 0.8f)
        p.line(3.7f, 3.8f, 0f, 3.7f, 3.8f, 1.2f, 0x6A4A2E, 2.4f)
        p.face(0xC8392E, 3.7f, 3.8f, 1.2f, 4.0f, 3.8f, 1.12f, 4.0f, 3.8f, 0.7f, 3.7f, 3.8f, 0.8f)
    }

    // ------------------------------------------------------------------ sol

    /** Un morceau du sol, 8 × 8 tuiles, peint une fois. Rend le bitmap et son décalage d'écran (en px, échelle 1). */
    fun chunk(cx: Int, cy: Int, game: HamletGame): Bitmap {
        if (chunkRev != game.groundRev) { chunks.values.forEach { it.recycle() }; chunks.clear(); chunkRev = game.groundRev }
        val key = (cx.toLong() shl 32) or cy.toLong()
        return chunks.getOrPut(key) { HamletGround.paintChunk(cx, cy, game) }
    }

    /** Vide le sol en cache (la partie vient de changer entièrement). */
    fun dropGround() { chunks.values.forEach { it.recycle() }; chunks.clear(); chunkRev = -1 }
}
