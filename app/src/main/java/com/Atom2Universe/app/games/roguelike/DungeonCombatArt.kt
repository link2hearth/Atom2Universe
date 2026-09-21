package com.Atom2Universe.app.games.roguelike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.games.roguelike.demo.DemoGear
import com.Atom2Universe.app.games.roguelike.demo.DungeonDemoSprites

/**
 * Les sprites provisoires du Donjon étaient des PNG sans rapport avec le butin porté.
 * Cette petite couche reprend les pièces pixel-art de la démo, mais les compose à partir
 * du vrai [Hero]. Elle ne connaît aucune règle de combat : elle est uniquement dessinée
 * par [CombatView].
 */
internal class DungeonCombatArt {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private var heroKey = ""

    private var equipment = emptyMap<EquipSlot, DemoGear>()
    private var images = emptyMap<EquipSlot, Bitmap>()
    // Un tampon par acteur : le Canvas matériel peut consommer les bitmaps après onDraw.
    private val actorFrames = mutableMapOf<String, Pair<Bitmap, Canvas>>()
    private fun actorBuffer(key: String): Pair<Bitmap, Canvas> = actorFrames.getOrPut(key) {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap to Canvas(bitmap)
    }.also { it.first.eraseColor(android.graphics.Color.TRANSPARENT) }
    private var atmosphereFrame: Bitmap? = null
    private var effectsFrame: Bitmap? = null
    private val clockOrigin = android.os.SystemClock.uptimeMillis()
    private var clock = 0f

    private val sceneArt = com.Atom2Universe.app.games.roguelike.demo.DungeonSceneArt()
    private var background: Bitmap? = null
    private val backdropArt = DungeonBackdropArt()
    private var backdrop = DungeonBackdrop.DUNGEON
    private var cachedBackdrop: DungeonBackdrop? = null
    private var visualCombat: Combat? = null
    private var monsterStyles = emptyList<DungeonDemoSprites.MonsterStyle>()

    /** Apparences attribuées une fois, communes aux portraits et aux acteurs de la scène. */
    fun prepareCombat(combat: Combat) {
        if (visualCombat === combat) return
        visualCombat = combat
        // Stable pendant tout l'étage, y compris après reprise d'une sauvegarde.
        backdrop = DungeonBackdrop.entries[(combat.floor.coerceAtLeast(1) - 1) % DungeonBackdrop.entries.size]
        val used = mutableSetOf<DungeonDemoSprites.MonsterStyle>()
        monsterStyles = combat.enemies.map { enemy ->
            val variants = DungeonDemoSprites.MonsterStyle.entries.filter { style ->
                val sameFamily = when (enemy.type) {
                    MonsterType.RAT -> !style.isHumanoid && !style.isVampire
                    MonsterType.SKELETON -> style.isSkeleton
                    MonsterType.GOBLIN, MonsterType.ORC -> style.isZombie
                    MonsterType.DEMON -> style.isVampire
                }
                sameFamily && style.isBossAppearance == enemy.isBoss
            }
            // Évite les doublons visuels dans un même groupe de combattants.
            val available = variants.filterNot { it in used }.ifEmpty { variants }
            available.random().also { used += it }
        }
    }

    fun drawBackdrop(canvas: Canvas, bounds: RectF) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        paint.alpha = 255
        clock = (android.os.SystemClock.uptimeMillis() - clockOrigin).toFloat()
        val scale = bounds.width() / 240f
        val worldHeight = kotlin.math.ceil(bounds.height() / scale).toInt()
        val floorY = minOf(84f, worldHeight * .28f)
        if (background?.height != worldHeight || cachedBackdrop != backdrop) {
            background?.recycle()
            background = Bitmap.createBitmap(240, worldHeight, Bitmap.Config.ARGB_8888).also {
                backdropArt.drawBackground(Canvas(it), backdrop, floorY, worldHeight.toFloat())
            }
            cachedBackdrop = backdrop
        }
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.right, bounds.top)
        canvas.scale(-scale, scale)
        if (atmosphereFrame?.height != worldHeight) {
            atmosphereFrame?.recycle()
            atmosphereFrame = Bitmap.createBitmap(240, worldHeight, Bitmap.Config.ARGB_8888)
        }
        val pixels = Canvas(requireNotNull(atmosphereFrame))
        pixels.drawBitmap(requireNotNull(background), 0f, 0f, paint)
        backdropArt.drawAtmosphere(pixels, backdrop, floorY, this.clock)
        canvas.drawBitmap(requireNotNull(atmosphereFrame), 0f, 0f, paint)
        canvas.restore()
    }
    fun drawHero(canvas: Canvas, bounds: RectF, hero: Hero, windup: Boolean = false,
        swing: Boolean = false, casting: Boolean = false, blocking: Boolean = false,
        portrait: Boolean = false) {
        paint.alpha = 255
        updateEquipment(hero)
        val (actorFrame, actorCanvas) = actorBuffer(if (portrait) "heroPortrait" else "hero")
        actorCanvas.save()
        actorCanvas.translate(50f, 12f)
        actorCanvas.scale(-1f, 1f)
        val nodPhase = clock % 7000f
        val nod = when {
            nodPhase < 220f -> nodPhase / 220f
            nodPhase < 600f -> (600f - nodPhase) / 380f
            else -> 0f
        }
        sceneArt.drawPaperDoll(actorCanvas, equipment, images, windup, swing, casting, blocking,
            !portrait && !windup && !swing && !casting && !blocking, clock, nod)
        actorCanvas.restore()
        if (portrait) {
            canvas.drawBitmap(actorFrame, android.graphics.Rect(20, 12, 48, 40), bounds, paint)
        } else {
            val unit = bounds.width() / 36f
            canvas.drawBitmap(actorFrame, null, RectF(bounds.left - 14 * unit, bounds.top - 12 * unit,
                bounds.left + 50 * unit, bounds.top + 52 * unit), paint)
        }
    }

    fun drawShadow(canvas: Canvas, bounds: RectF, hero: Boolean = false) {
        val unit = bounds.width() / if (hero) 36f else 46f
        val right = bounds.right - (if (hero) 7f else 3f) * unit
        val y = bounds.top + (if (hero) 35f else 36f) * unit
        paint.color = 0x66313B49
        canvas.drawRect(right - (if (hero) 15 else 22) * unit, y - unit, right - 2 * unit, y + 2 * unit, paint)
        paint.color = 0xAA172538.toInt()
        canvas.drawRect(right - (if (hero) 17 else 24) * unit, y, right, y + 2 * unit, paint)
    }

    fun drawMonster(canvas: Canvas, bounds: RectF, type: MonsterType, alpha: Int = 255,
        icy: Boolean = false, hurt: Boolean = false, index: Int = 0, portrait: Boolean = false) {
        val (actorFrame, actorCanvas) = actorBuffer("monster:$index:$portrait")
        sceneArt.drawMonster(actorCanvas, 10f, 30f, icy, hurt, index,
            monsterStyles.getOrElse(index) { style(type) }, clock)
        if (icy) sceneArt.drawIce(actorCanvas, 22f, 39f)
        paint.alpha = alpha
        canvas.save()
        canvas.scale(-1f, 1f, bounds.centerX(), bounds.centerY())
        if (portrait) {
            canvas.drawBitmap(actorFrame, android.graphics.Rect(6, 10, 54, 54), bounds, paint)
        } else {
            val unit = bounds.width() / 46f
            canvas.drawBitmap(actorFrame, null, RectF(bounds.left - 10 * unit, bounds.top - 12 * unit,
                bounds.left + 54 * unit, bounds.top + 52 * unit), paint)
        }
        canvas.restore()
        paint.alpha = 255
    }

    fun drawImpact(canvas: Canvas, bounds: RectF, progress: Float, ice: Boolean, slash: Boolean, index: Int) {
        val (actorFrame, actorCanvas) = actorBuffer("impact:$index")
        if (slash && progress < .6f) sceneArt.drawSlash(actorCanvas, progress / .6f, 30f, 30f)
        sceneArt.fragments(actorCanvas, 30f, 30f, progress, ice)
        val unit = bounds.width() / 46f
        canvas.save()
        canvas.scale(-1f, 1f, bounds.centerX(), bounds.centerY())
        canvas.drawBitmap(actorFrame, null, RectF(bounds.left - 16 * unit, bounds.top - 3 * unit,
            bounds.left + 48 * unit, bounds.top + 61 * unit), paint)
        canvas.restore()
    }
    fun drawProjectile(canvas: Canvas, scene: RectF, hero: RectF, target: RectF, progress: Float, color: Int) {
        val unit = scene.width() / 240f
        val h = kotlin.math.ceil(scene.height() / unit).toInt()
        if (effectsFrame?.height != h) {
            effectsFrame?.recycle()
            effectsFrame = Bitmap.createBitmap(240, h, Bitmap.Config.ARGB_8888)
        }
        val frame = requireNotNull(effectsFrame)
        frame.eraseColor(android.graphics.Color.TRANSPARENT)
        sceneArt.drawProjectile(Canvas(frame), progress,
            (scene.right - hero.right) / unit, (hero.top - scene.top) / unit,
            (scene.right - target.right) / unit + 14f, (target.top - scene.top) / unit + 27f,
            color, color)
        canvas.save()
        canvas.translate(scene.right, scene.top)
        canvas.scale(-unit, unit)
        canvas.drawBitmap(frame, 0f, 0f, paint)
        canvas.restore()
    }

    private fun updateEquipment(hero: Hero) {
        val pieces = DungeonDemoSprites.slots.associateWith { slot -> hero.equipped[slot] }
        val key = hero.archetype.toString() + "|" + pieces.entries.joinToString("|") { (slot, item) ->
            "$slot:${item?.power}:${item?.weight}:${item?.isotopeZ}:${item?.rarity}"
        }
        if (key == heroKey) return
        heroKey = key
        val fallback = hero.archetype ?: Archetype.WARRIOR
        equipment = pieces.mapValues { (slot, item) ->
            val family = item?.weight?.let { weight ->
                Archetype.entries.firstOrNull { it.weight == weight }
            } ?: fallback
            DemoGear(slot, isotope = item?.isotopeZ != null,
                hue = com.Atom2Universe.app.games.roguelike.demo.DungeonClassSprites.defaultHue(family),
                family = family, equipped = item != null)
        }
        images.values.forEach { it.recycle() }
        images = equipment.mapValues { DungeonDemoSprites.equipment(it.value) }
    }

    private fun style(type: MonsterType): DungeonDemoSprites.MonsterStyle = when (type) {
        MonsterType.RAT -> DungeonDemoSprites.MonsterStyle.COMMON
        MonsterType.GOBLIN -> DungeonDemoSprites.MonsterStyle.ZOMBIE
        MonsterType.SKELETON -> DungeonDemoSprites.MonsterStyle.SKELETON
        MonsterType.ORC -> DungeonDemoSprites.MonsterStyle.ZOMBIE_SWAMP
        MonsterType.DEMON -> DungeonDemoSprites.MonsterStyle.VAMPIRE_BAT
    }
}
