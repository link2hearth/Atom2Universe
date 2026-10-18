package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.Atom2Universe.app.games.roguelike.Archetype
import com.Atom2Universe.app.games.roguelike.EquipSlot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/** Dessin commun à la démo validée et au combat réel, dans un monde de 240 pixels. */
internal class DungeonSceneArt {
    private val ink = 0xFF111729.toInt()
    private val gold = 0xFFDBB975.toInt()
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val path = Path()
    private fun box(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun line(c: Canvas, x: Float, y: Float, endX: Float, endY: Float, color: Int, thickness: Float = 1f) {
        paint.color = color
        paint.strokeWidth = thickness
        c.drawLine(x, y, endX, endY, paint)
    }
    private fun sprite(c: Canvas, bitmap: Bitmap, x: Float, y: Float, scale: Float = 2f) {
        c.save()
        c.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())
        c.scale(scale, scale)
        c.drawBitmap(bitmap, 0f, 0f, paint)
        c.restore()
    }
    fun drawBackground(c: Canvas, floorY: Float, worldHeight: Float) {
        c.drawColor(0xFF1A2439.toInt())
        // Mur du fond moins haut : une grande surface de sol pour les combattants.
        for (row in 0..(floorY / 12).toInt()) for (col in -1..8) {
            val x = col * 32f + if (row % 2 == 0) 0f else 16f
            val y = row * 12f
            box(c, x, y, 30f, 10f, if ((row + col) % 3 == 0) 0xFF293A50.toInt() else 0xFF243348.toInt())
            box(c, x + 1, y, 28f, 1f, 0xFF35465A.toInt())
        }
        for (center in intArrayOf(52, 185)) {
            val top = floorY - 53
            box(c, center - 16f, top + 10, 32f, 43f, 0xFF0E1A2A.toInt())
            box(c, center - 11f, top + 4, 22f, 9f, 0xFF0E1A2A.toInt())
            box(c, center - 6f, top, 12f, 5f, 0xFF0E1A2A.toInt())
            for (bar in -1..1) box(c, center + bar * 9f, top + 12, 2f, 41f, 0xFF283348.toInt())
            for (side in intArrayOf(-1, 1)) {
                val x = center + side * 22f - 4
                box(c, x, top, 8f, 53f, 0xFF37455A.toInt())
                box(c, x, top, 2f, 53f, 0xFF526071.toInt())
                for (joint in 0..4) box(c, x, top + joint * 12, 8f, 1f, 0xFF222E44.toInt())
                box(c, x - 2, floorY - 3, 12f, 5f, 0xFF435367.toInt())
            }
        }
        for (i in 0..17) {
            val x = ((i * 43 + 13) % 238).toFloat()
            val length = 5f + i * 7 % 20
            box(c, x, 0f, 2f, length, 0xFF285353.toInt())
            box(c, x, 0f, 1f, length * .65f, 0xFF478477.toInt())
        }
        box(c, 0f, floorY + 2, 240f, worldHeight, 0xFF293B4B.toInt())
        for (row in 0..((worldHeight - floorY) / 16).toInt()) {
            val y = floorY + 4 + row * 16
            for (col in -1..6) {
                val x = col * 45f + if (row % 2 == 0) 0 else 22
                box(c, x, y, 43f, 14f, if ((col + row) % 3 == 0) 0xFF354B59.toInt() else 0xFF304452.toInt())
                box(c, x + 2, y, 39f, 1f, 0xFF425764.toInt())
                if ((col * 3 + row) % 4 == 0) {
                    line(c, x + 9, y + 5, x + 14, y + 8, 0xFF233644.toInt())
                    line(c, x + 14, y + 8, x + 12, y + 12, 0xFF233644.toInt())
                }
            }
        }
        // Eau derrière les acteurs ; les emplacements de combat restent sur les dalles.
        box(c, 87f, floorY + 4, 43f, 12f, 0xFF21424F.toInt())
        box(c, 90f, floorY + 6, 35f, 1f, 0xFF3D7177.toInt())
        for (i in 0..22) {
            val x = ((i * 61 + 5) % 234).toFloat()
            val range = (worldHeight - floorY - 16).toInt().coerceAtLeast(1)
            val y = floorY + 12 + i * 29 % range
            box(c, x, y, 4f, 2f, 0xFF456C61.toInt())
            box(c, x + 2, y - 1, 3f, 1f, 0xFF709079.toInt())
        }
        for (x in intArrayOf(14, 219)) {
            box(c, x - 2f, floorY - 31, 7f, 13f, ink)
            box(c, x.toFloat(), floorY - 31, 2f, 11f, 0xFF8B6950.toInt())
        }
        for (x in intArrayOf(0, 232)) {
            box(c, x.toFloat(), worldHeight - 36, 8f, 36f, 0xFF172439.toInt())
            box(c, x - 2f, worldHeight - 39, 12f, 4f, 0xFF425366.toInt())
        }
    }
    fun drawAtmosphere(c: Canvas, floorY: Float, clock: Float) {
        val tick = (clock / 140).toInt()
        for (x in intArrayOf(14, 219)) {
            val y = floorY - 41
            box(c, x - 4f, y, 10f, 12f, 0x228DC56E)
            box(c, x - 2f, y + 2, 7f, 9f, 0xFFBD704C.toInt())
            box(c, x - 1f, y + tick % 3, 4f, 10f - tick % 3, 0xFFECB15E.toInt())
            box(c, x.toFloat(), y + 4, 2f, 6f, 0xFFFFE4A0.toInt())
            box(c, x + (tick % 3).toFloat(), y - 5 - tick % 4, 1f, 1f, gold)
        }
        for (i in 0..3) {
            val x = 92f + (i * 9 + tick) % 29
            box(c, x, floorY + 7 + i * 2, 4f, 1f, 0xFF4C8187.toInt())
        }
    }
    fun drawPaperDoll(c: Canvas, equipment: Map<EquipSlot, DemoGear>, images: Map<EquipSlot, Bitmap>,
        windup: Boolean = false, swing: Boolean = false, casting: Boolean = false,
        blocking: Boolean = false, animateIdle: Boolean = false, clock: Float = 0f, nod: Float = 0f, castAngle: Float = -25f) {
        fun gear(slot: EquipSlot) = equipment.getValue(slot)
        fun gearBitmap(slot: EquipSlot) = images.getValue(slot)
        fun wornPiece(c: Canvas, slot: EquipSlot, x: Float, y: Float) {
            val item = gear(slot)
            sprite(c, gearBitmap(slot), x + if (item.equipped) DungeonClassSprites.offsetX(item) else 0f,
                y + (if (item.equipped) DungeonClassSprites.offsetY(item) else 0f), 1f)
        }
        val tones = if (gear(EquipSlot.CHEST).equipped) DungeonDemoSprites.metal(gear(EquipSlot.CHEST).hue)
            else intArrayOf(0xFFAA7964.toInt(), 0xFFD5AA89.toInt(), 0xFFE8C5A3.toInt())
        val bowOnBack = gear(EquipSlot.OFFHAND).equipped && gear(EquipSlot.OFFHAND).family == Archetype.ROGUE
        if (bowOnBack) {
            // Corde en diagonale derrière l'épaule, courbure vers l'extérieur du dos.
            // L'icône du jeu est verticale (16 × 24), celle de la démo horizontale (25 × 9).
            val bow = gearBitmap(EquipSlot.OFFHAND)
            c.save()
            c.translate(11f, 16f)
            if (bow.width > bow.height) {
                c.rotate(45f)
                sprite(c, bow, -12f, -1f, 1f)
            } else {
                c.rotate(-45f)
                c.scale(-1f, 1f)
                sprite(c, bow, -3f, -12f, 1f)
            }
            c.restore()
        }
        // Bassin et jambes sous les pièces, pas de jambes redessinées par-dessus les bottes.
        box(c, 8f, 22f, 12f, 6f, ink)
        val legTone = if (gear(EquipSlot.BOOTS).equipped) tones[0] else 0xFF394454.toInt()
        box(c, 9f, 23f, 4f, 7f, legTone)
        box(c, 16f, 23f, 4f, 7f, legTone)
        wornPiece(c, EquipSlot.BOOTS, 6f, 26f)
        // Bras court : raccord arrondi au coude, poignée au point (8, 2).
        c.save()
        c.translate(19f, 15f)
        c.rotate(when { swing -> 70f; windup -> -55f; casting -> castAngle; else -> 8f })
        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(3f, 4f)
        path.lineTo(8f, 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = ink
        paint.strokeWidth = 5f
        c.drawPath(path, paint)
        paint.color = tones[1]
        paint.strokeWidth = 3f
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.strokeJoin = Paint.Join.MITER
        paint.strokeCap = Paint.Cap.BUTT
        c.save()
        if (animateIdle) c.rotate(sin(clock / 900f) * 4f, 8f, 2f)
        wornPiece(c, EquipSlot.WEAPON, 3f, -15f)
        c.restore()
        box(c, 6f, 1f, 4f, 3f, tones[1])
        box(c, 7f, 1f, 2f, 1f, tones[2])
        c.restore()
        wornPiece(c, EquipSlot.CHEST, 7f, 12f)
        c.save()
        if (animateIdle) {

            c.rotate(nod * 6f, 14f, 12f)
        }
        wornPiece(c, EquipSlot.HELMET, 6f, 1f)
        c.restore()
        // Bras proche visible entre l'épaule et le bouclier, celui-ci passe DEVANT le torse.
        val shieldX = if (blocking) 10f else 0f
        val shieldY = if (blocking) 11f else 14f
        if (bowOnBack || !gear(EquipSlot.OFFHAND).equipped) {
            // Bras libre au repos : aucune prise ni animation de bouclier pour l'arc rangé.
            line(c, 8f, 18f, 6f, 24f, ink, 5f)
            line(c, 8f, 18f, 6f, 24f, 0xFFD5AA89.toInt(), 3f)
            box(c, 5f, 23f, 3f, 3f, 0xFFD5AA89.toInt())
        } else {
            line(c, 8f, 18f, shieldX + 6, shieldY + 5, ink, 5f)
            line(c, 8f, 18f, shieldX + 6, shieldY + 5, tones[1], 3f)
            wornPiece(c, EquipSlot.OFFHAND, shieldX, shieldY)
        }
    }
    private val white = 0xFFE8EAD5.toInt()
    private val iceBlue = 0xFFA6E7EF.toInt()
    private class MonsterVisual(val poses: Array<Bitmap>, val seed: Int?) { val portrait get() = poses[0] }
    private val monsterVisuals = mutableMapOf<Int, Pair<DungeonDemoSprites.MonsterStyle, MonsterVisual>>()
    fun monsterPoses(style: DungeonDemoSprites.MonsterStyle, index: Int): Array<Bitmap> = visualFor(style, index).poses
    private fun visualFor(style: DungeonDemoSprites.MonsterStyle, index: Int, seed: Int? = null): MonsterVisual {
        monsterVisuals[index]?.takeIf { it.first == style && it.second.seed == seed }?.let { return it.second }
        val clothes = if (style == DungeonDemoSprites.MonsterStyle.ZOMBIE ||
            style.creature == com.Atom2Universe.app.games.roguelike.MonsterType.GOBLIN ||
            style.creature == com.Atom2Universe.app.games.roguelike.MonsterType.TROLL)
            DungeonZombieSprites.randomClothes(seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random) else null
        return MonsterVisual(if (style.creature != null) Array(8) { DungeonCreatureSprites.create(style, it, clothes) }
        else if (style.isSpider) Array(8) { DungeonSpiderSprites.create(style, it) }
        else if (style.isPirate) Array(8) { DungeonPirateSprites.create(style, it) }
        else if (style.isAlien) Array(8) { DungeonAlienSprites.create(style, it) }
        else if (style.isVampire) Array(8) { DungeonVampireSprites.create(style, it) }
        else if (style.isHumanoid) Array(24) {
            if (style.isZombie) DungeonZombieSprites.create(style, it % 8, it / 8, clothes)
            else DungeonSkeletonSprites.create(style, it % 8, it / 8)
        } else arrayOf(DungeonDemoSprites.rat(style)), seed).also { monsterVisuals[index] = style to it }
    }
    fun drawMonster(c: Canvas, x: Float, y: Float, icy: Boolean, hurt: Boolean,
        index: Int, style: DungeonDemoSprites.MonsterStyle, clock: Float, appearanceSeed: Int? = null) {

        val visual = visualFor(style, index, appearanceSeed)
        if (style.creature != null) {
            val pose = if (icy) 0 else ((clock + index * 317) / 150).toInt() % 8
            val bitmap = visual.poses[pose]
            sprite(c, bitmap, x - 6, y + 19 - bitmap.height, 1f)
            if (hurt) {
                line(c, x + 12, y - 6, x + 16, y - 2, white)
                line(c, x + 16, y - 6, x + 12, y - 2, white)
            }
            return
        }
        if (style.isSpider) {
            val pose = if (icy) 0 else ((clock + index * 317) / 120).toInt() % 8
            val bitmap = visual.poses[pose]
            sprite(c, bitmap, x - 5, y + 19 - bitmap.height, 1f)
            if (hurt) {
                line(c, x + 13, y + 2, x + 17, y + 6, white)
                line(c, x + 17, y + 2, x + 13, y + 6, white)
            }
            return
        }
        if (style.isPirate) {
            val pose = if (icy) 0 else ((clock + index * 317) / 180).toInt() % 8
            val bitmap = visual.poses[pose]
            sprite(c, bitmap, x - 4, y + 19 - bitmap.height, 1f)
            if (hurt) {
                line(c, x + 11, y - 10, x + 15, y - 6, white)
                line(c, x + 15, y - 10, x + 11, y - 6, white)
            }
            return
        }
        if (style.isAlien) {
            val pose = if (icy) 0 else ((clock + index * 317) / 160).toInt() % 8
            sprite(c, visual.poses[pose], x - 4, y - 17, 1f)
            if (hurt) {
                line(c, x + 12, y - 6, x + 16, y - 2, white)
                line(c, x + 16, y - 6, x + 12, y - 2, white)
            }
            return
        }
        if (style.isVampire) {
            val phase = clock + index * 317
            val pose = if (icy) 0 else (phase / if (style.isBat) 95 else 180).toInt() % 8
            sprite(c, visual.poses[pose], x - 4, y - 17, 1f)
            if (hurt) {
                val hitY = y + if (style.isBat) -2 else -11
                line(c, x + 11, hitY, x + 13, hitY + 2, white)
                line(c, x + 13, hitY, x + 11, hitY + 2, white)
            }
            return
        }
        if (style.isHumanoid) {
            val phase = clock + style.ordinal * 317 + x * 3
            val pose = if (icy) 0 else (phase / if (style.isZombie) 240 else 180).toInt() % 8
            val jawPhase = (phase.toInt() % 5200)
            val jaw = if (icy) 0 else when (jawPhase) {
                in 3600..3799, in 4400..4599 -> 1
                in 3800..4399 -> 2
                else -> 0
            }
            val bitmap = visual.poses[jaw * 8 + pose]
            val top = y + 19 - bitmap.height
            sprite(c, bitmap, x - 4, top, 1f)
            if (hurt) {
                line(c, x + 10, top + 5, x + 12, top + 7, white)
                line(c, x + 12, top + 5, x + 10, top + 7, white)
            }
            return
        }
        c.save()
        c.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())
        if (style == DungeonDemoSprites.MonsterStyle.BOSS) {
            // Conserve l'ancrage des pieds et la place de la barre de vie.
            c.translate(-4f, -5f)
            c.scale(1.25f, 1.25f)
        }
        val twitch = if (icy) 0f else ((clock / 260).toInt() % 3 - 1).toFloat()
        // Racine sous la croupe (26,11), puis courbe au ras du sol, sur la grille du corps.
        path.reset()
        path.moveTo(26f, 11f)
        path.lineTo(30f, 14f)
        path.lineTo(34f, 16f)
        when (style) {
            DungeonDemoSprites.MonsterStyle.GREY -> {
                path.lineTo(38f, 17f)
                path.lineTo(40f, 19f + twitch)
            }
            DungeonDemoSprites.MonsterStyle.BROWN -> {
                path.lineTo(38f, 15f)
                path.lineTo(39f, 11f + twitch)
                path.lineTo(37f, 9f + twitch)
            }
            DungeonDemoSprites.MonsterStyle.ALBINO -> {
                path.lineTo(39f, 17f)
                path.lineTo(43f, 16f + twitch)
            }
            DungeonDemoSprites.MonsterStyle.BOSS -> {
                path.lineTo(38f, 15f)
                path.lineTo(40f, 11f + twitch)
            }
            else -> {
                path.lineTo(38f, 16f)
                path.lineTo(41f, 14f + twitch)
            }
        }
        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        c.drawPath(path, paint)
        paint.color = style.skin.toInt()
        paint.strokeWidth = 1f
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        sprite(c, visual.portrait, 0f, 0f, 1f)
        // Traits courts sur la joue ; aucune pointe claire devant le nez rose.
        if (style != DungeonDemoSprites.MonsterStyle.BOSS) {
            line(c, 4f, 10f, 7f, 9f, 0xFF958C85.toInt())
            line(c, 5f, 12f, 8f, 12f, 0xFF958C85.toInt())
        }
        if (hurt) {
            line(c, 8f, 8f, 10f, 10f, white)
            line(c, 8f, 10f, 10f, 8f, white)
        } else if (style != DungeonDemoSprites.MonsterStyle.BOSS && !icy && (clock.toInt() % 3200) < 150) {
            box(c, 8f, 8f, 3f, 3f, style.fur.toInt())
            box(c, 8f, 9f, 3f, 1f, ink)
        }
        c.restore()
    }
    fun crystal(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        path.reset()
        path.moveTo(x, y - size)
        path.lineTo(x + size * .45f, y)
        path.lineTo(x, y + size * .6f)
        path.lineTo(x - size * .4f, y)
        path.close()
        paint.color = color
        c.drawPath(path, paint)
        line(c, x, y - size + 1, x, y + size * .4f, white)
    }
    fun drawIce(c: Canvas, centerX: Float, centerY: Float) {
        c.save()
        c.translate(centerX, centerY)
        c.scale(.5f, .5f)
        val x = 0f
        val y = 0f
        crystal(c, x - 19, y + 8, 18f, 0xAA57A9CD.toInt())
        crystal(c, x + 19, y + 9, 23f, 0xBB438CB5.toInt())
        crystal(c, x + 1, y + 15, 15f, 0xAA8DCFDA.toInt())
        line(c, x - 26, y + 19, x + 28, y + 19, iceBlue, 2f)
        line(c, x - 16, y - 13, x - 4, y - 18, iceBlue)
        c.restore()
    }
    fun drawProjectile(c: Canvas, progress: Float, heroX: Float, heroY: Float,
        targetX: Float, targetY: Float, color: Int = iceBlue, trail: Int = 0xFF468AA7.toInt()) {
        val x = heroX + 28 + progress * (targetX - heroX - 28)
        val y = heroY + 16 + progress * (targetY - heroY - 16) - sin(progress * PI).toFloat() * 12
        for (i in 1..5) box(c, x - i * 3, y + i, 2f, 1f, trail)
        crystal(c, x, y, 4.5f, color)
    }

    fun drawSlash(c: Canvas, p: Float, targetX: Float, targetY: Float) {
        // Arc en segments sur grille, bref et sans masquer toute la cible.
        for (i in 0..12) {
            val angle = -.9f + (p * 1.6f) - i * .085f
            val x = targetX - 14 + cos(angle) * 16
            val y = targetY + sin(angle) * 12
            box(c, x.roundToInt().toFloat(), y.roundToInt().toFloat(), if (i < 5) 3f else 2f, 3f,
                if (i < 5) white else gold)
        }
    }
    fun fragments(c: Canvas, x: Float, y: Float, progress: Float, ice: Boolean) {
        if (progress !in 0f..1f) return
        for (i in 0 until 12) {
            val angle = i * (PI * 2 / 12)
            val distance = progress * (10 + i % 4 * 5)
            val dx = x + (cos(angle) * distance).toFloat()
            val dy = y + (sin(angle) * distance).toFloat() + progress * progress * 12
            if (ice) crystal(c, dx.roundToInt().toFloat(), dy.roundToInt().toFloat(), (1 - progress) * 3 + 1, iceBlue)
            else box(c, dx.roundToInt().toFloat(), dy.roundToInt().toFloat(), 2f, 2f, if (i % 2 == 0) gold else white)
        }
    }
}
