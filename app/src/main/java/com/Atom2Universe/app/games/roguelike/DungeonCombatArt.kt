package com.Atom2Universe.app.games.roguelike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.SparseArray
import com.Atom2Universe.app.games.roguelike.demo.DemoGear
import com.Atom2Universe.app.games.roguelike.demo.DungeonDemoSprites

/**
 * Les sprites provisoires du Donjon étaient des PNG sans rapport avec le butin porté.
 * Cette petite couche reprend les pièces pixel-art de la démo, mais les compose à partir
 * du vrai [Hero]. Elle ne connaît aucune règle de combat : elle est uniquement dessinée
 * par [CombatView].
 */
internal class DungeonCombatArt {
    companion object {
        private const val HERO = 0
        private const val HERO_PORTRAIT = 1
        private const val MIRROR_BASE = 16
        private const val MONSTER_BASE = 1 shl 20
        private const val IMPACT_BASE = 1 shl 24
        /** Le tampon d'un double du mage (Image miroir). */
        fun mirrorKey(index: Int) = MIRROR_BASE + index
        /** L'attente du héros bouge sans cesse (arme qui se balance, hochement) : une image par pas. */
        private const val IDLE_STEP_MS = 33f
        /** Même chose pour l'ambiance du décor, qui mêle des pas de 120 à 220 ms et des mouvements continus. */
        private const val ATMOSPHERE_STEP_MS = 33f
    }

    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val dst = RectF()
    private val heroPortraitSrc = Rect(20, 12, 48, 40)
    private val monsterPortraitSrc = Rect(6, 10, 54, 54)

    // L'équipement dessiné : refait seulement quand une pièce portée ou l'archétype change.
    private var heroReady = false
    private var heroArchetype: Archetype? = null
    private val heroPieces = arrayOfNulls<Equipment>(DungeonDemoSprites.slots.size)
    private var equipmentVersion = 0
    private var equipment = emptyMap<EquipSlot, DemoGear>()
    private var images = emptyMap<EquipSlot, Bitmap>()

    /**
     * Un tampon par acteur : le Canvas matériel peut consommer les bitmaps après onDraw.
     * Il retient ce qu'il montre ([a] à [d]) pour n'être redessiné, et renvoyé à la carte
     * graphique, que quand la pose ou l'état change.
     */
    private class ActorFrame {
        val bitmap: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var valid = false
        var a = 0; var b = 0; var c = 0; var d = 0
        fun shows(a: Int, b: Int, c: Int, d: Int) = valid && this.a == a && this.b == b && this.c == c && this.d == d
        /** Vide le tampon avant un nouveau dessin, qui montrera ([a], [b], [c], [d]). */
        fun redraw(a: Int, b: Int, c: Int, d: Int) {
            valid = true; this.a = a; this.b = b; this.c = c; this.d = d
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        }
    }
    private val actorFrames = SparseArray<ActorFrame>()
    private fun actorFrame(key: Int): ActorFrame = actorFrames.get(key) ?: ActorFrame().also { actorFrames.put(key, it) }

    private var atmosphereFrame: Bitmap? = null
    private var atmosphereCanvas: Canvas? = null
    /** Le pas d'horloge de l'ambiance déjà dessinée ; MIN_VALUE : à refaire. */
    private var atmosphereStep = Int.MIN_VALUE
    private var effectsFrame: Bitmap? = null
    private var effectsCanvas: Canvas? = null
    private val clockOrigin = android.os.SystemClock.uptimeMillis()
    private var clock = 0f

    private val sceneArt = com.Atom2Universe.app.games.roguelike.demo.DungeonSceneArt()
    private var background: Bitmap? = null
    private val backdropArt = DungeonBackdropArt()
    private var backdrop = DungeonBackdrop.DUNGEON
    private var cachedBackdrop: DungeonBackdrop? = null
    private var visualCombat: Combat? = null
    private var appearanceSeed = 0
    private var monsterStyles = emptyList<DungeonDemoSprites.MonsterStyle>()

    /** Apparences attribuées une fois, communes aux portraits et aux acteurs de la scène. */
    fun prepareCombat(combat: Combat) {
        if (visualCombat === combat) return
        visualCombat = combat
        // Stable pendant tout l'étage, y compris après reprise d'une sauvegarde.
        backdrop = combat.backdrop
        appearanceSeed = combat.visualSeed
        monsterStyles = styles(combat.enemies.map { it.type }, combat.visualSeed)
    }

    private fun styles(types: List<MonsterType>, seed: Int): List<DungeonDemoSprites.MonsterStyle> {
        val rng = kotlin.random.Random(seed)
        val used = mutableSetOf<DungeonDemoSprites.MonsterStyle>()
        return types.mapIndexed { index, type ->
            val variants = DungeonDemoSprites.MonsterStyle.entries.filter { style ->
                val sameFamily = when (type) {
                    MonsterType.RAT -> style.creature == null && !style.isHumanoid && !style.isVampire && !style.isAlien && !style.isPirate && !style.isSpider
                    MonsterType.SPIDER -> style.isSpider
                    MonsterType.SKELETON -> style.isSkeleton
                    MonsterType.ZOMBIE -> style.isZombie
                    MonsterType.GOBLIN, MonsterType.DEMON, MonsterType.SCORPION,
                    MonsterType.CARNIVOROUS_PLANT, MonsterType.FELINE, MonsterType.WOLF,
                    MonsterType.BEAR, MonsterType.TROLL, MonsterType.SNAKE -> style.creature == type
                    MonsterType.VAMPIRE -> style.isVampire && !style.isBat
                    MonsterType.VAMPIRE_BAT -> style.isBat
                    MonsterType.PIRATE -> style == DungeonDemoSprites.MonsterStyle.PIRATE
                    MonsterType.PIRATE_BRUTE -> style == DungeonDemoSprites.MonsterStyle.PIRATE_BRUTE
                    MonsterType.PIRATE_CAPTAIN -> style == DungeonDemoSprites.MonsterStyle.PIRATE_CAPTAIN
                    MonsterType.ALIEN_SCOUT -> style == DungeonDemoSprites.MonsterStyle.ALIEN_SCOUT
                    MonsterType.ALIEN_CRAWLER -> style == DungeonDemoSprites.MonsterStyle.ALIEN_CRAWLER
                    MonsterType.ALIEN_FLOATER -> style == DungeonDemoSprites.MonsterStyle.ALIEN_FLOATER
                }
                sameFamily && (style.isAlien || style.isPirate || type == MonsterType.VAMPIRE ||
                    type == MonsterType.VAMPIRE_BAT || style.isBossAppearance == Encounters.isBoss(types, index))
            }
            // Évite les doublons visuels dans un même groupe de combattants.
            val available = variants.filterNot { it in used }.ifEmpty { variants }
            available.random(rng).also { used += it }
        }
    }

    /** Par graine de groupe : ses types et les apparences tirées pour eux. */
    private val mapStyles = SparseArray<Pair<List<MonsterType>, List<DungeonDemoSprites.MonsterStyle>>>()

    fun prepareMapPack(pack: MonsterPack) {
        val seed = pack.home.x * 73856093 xor pack.home.y * 19349663
        appearanceSeed = seed
        val known = mapStyles.get(seed)
        monsterStyles = if (known != null && known.first == pack.types) known.second
            else styles(pack.types, seed).also { mapStyles.put(seed, pack.types to it) }
    }

    fun clearMapPacks() { mapStyles.clear() }

    fun tick() {
        clock = (android.os.SystemClock.uptimeMillis() - clockOrigin).toFloat()
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
            atmosphereStep = Int.MIN_VALUE
        }
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.right, bounds.top)
        canvas.scale(-scale, scale)
        if (atmosphereFrame?.height != worldHeight) {
            atmosphereFrame?.recycle()
            atmosphereFrame = Bitmap.createBitmap(240, worldHeight, Bitmap.Config.ARGB_8888).also {
                atmosphereCanvas = Canvas(it)
            }
            atmosphereStep = Int.MIN_VALUE
        }
        // L'ambiance n'est refaite (et renvoyée à la carte graphique) qu'à chaque pas d'horloge
        val step = (clock / ATMOSPHERE_STEP_MS).toInt()
        if (step != atmosphereStep) {
            atmosphereStep = step
            val pixels = requireNotNull(atmosphereCanvas)
            pixels.drawBitmap(requireNotNull(background), 0f, 0f, paint)
            backdropArt.drawAtmosphere(pixels, backdrop, floorY, this.clock)
        }
        canvas.drawBitmap(requireNotNull(atmosphereFrame), 0f, 0f, paint)
        canvas.restore()
    }

    /** [alpha] : l'opacité du héros entier (les doubles du mage sont translucides). */
    fun drawHero(canvas: Canvas, bounds: RectF, hero: Hero, windup: Boolean = false,
        swing: Boolean = false, casting: Boolean = false, blocking: Boolean = false,
        portrait: Boolean = false, invocation: Boolean = false, castProgress: Float = 0f,
        actorKey: Int? = null, alpha: Int = 255) {
        updateEquipment(hero)
        val frame = actorFrame(actorKey ?: if (portrait) HERO_PORTRAIT else HERO)
        val idle = !portrait && !windup && !swing && !casting && !blocking
        val pose = (if (windup) 1 else 0) or (if (swing) 2 else 0) or (if (casting) 4 else 0) or
            (if (blocking) 8 else 0) or (if (portrait) 16 else 0)
        val step = if (idle) (clock / IDLE_STEP_MS).toInt() else 0
        // L'invocation (Météore) lève le bras en continu : toujours redessinée
        if (invocation || !frame.shows(equipmentVersion, pose, step, 0)) {
            frame.redraw(equipmentVersion, pose, step, 0)
            if (invocation) frame.valid = false
            val actorCanvas = frame.canvas
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
                idle, clock, nod,
                if (invocation) -25f - 70f * castProgress.coerceIn(0f, 1f) else -25f)
            actorCanvas.restore()
        }
        paint.alpha = alpha
        if (portrait) {
            canvas.drawBitmap(frame.bitmap, heroPortraitSrc, bounds, paint)
        } else {
            val unit = bounds.width() / 36f
            dst.set(bounds.left - 14 * unit, bounds.top - 12 * unit, bounds.left + 50 * unit, bounds.top + 52 * unit)
            canvas.drawBitmap(frame.bitmap, null, dst, paint)
        }
        paint.alpha = 255
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
        icy: Boolean = false, hurt: Boolean = false, index: Int = 0, portrait: Boolean = false, actorIndex: Int = index) {
        val frame = actorFrame(MONSTER_BASE + actorIndex * 2 + if (portrait) 1 else 0)
        val style = monsterStyles.getOrElse(index) { style(type) }
        val seed = appearanceSeed + index
        val pose = sceneArt.monsterFrame(10f, icy, actorIndex, style, clock)
        val state = style.ordinal or (if (icy) 1 shl 16 else 0) or (if (hurt) 1 shl 17 else 0)
        if (!frame.shows(state, seed, pose, actorIndex)) {
            frame.redraw(state, seed, pose, actorIndex)
            sceneArt.drawMonster(frame.canvas, 10f, 30f, icy, hurt, actorIndex, style, clock, seed)
            if (icy) sceneArt.drawIce(frame.canvas, 22f, 39f)
        }
        paint.alpha = alpha
        canvas.save()
        canvas.scale(-1f, 1f, bounds.centerX(), bounds.centerY())
        if (portrait) {
            canvas.drawBitmap(frame.bitmap, monsterPortraitSrc, bounds, paint)
        } else {
            val unit = bounds.width() / 46f
            dst.set(bounds.left - 10 * unit, bounds.top - 12 * unit, bounds.left + 54 * unit, bounds.top + 52 * unit)
            canvas.drawBitmap(frame.bitmap, null, dst, paint)
        }
        canvas.restore()
        paint.alpha = 255
    }

    fun drawImpact(canvas: Canvas, bounds: RectF, progress: Float, ice: Boolean, slash: Boolean, index: Int) {
        // Les éclats bougent à chaque image : pas de tampon gardé
        val frame = actorFrame(IMPACT_BASE + index)
        frame.redraw(0, 0, 0, 0)
        frame.valid = false
        if (slash && progress < .6f) sceneArt.drawSlash(frame.canvas, progress / .6f, 30f, 30f)
        sceneArt.fragments(frame.canvas, 30f, 30f, progress, ice)
        val unit = bounds.width() / 46f
        canvas.save()
        canvas.scale(-1f, 1f, bounds.centerX(), bounds.centerY())
        dst.set(bounds.left - 16 * unit, bounds.top - 3 * unit, bounds.left + 48 * unit, bounds.top + 61 * unit)
        canvas.drawBitmap(frame.bitmap, null, dst, paint)
        canvas.restore()
    }
    fun drawProjectile(canvas: Canvas, scene: RectF, hero: RectF, target: RectF, progress: Float, color: Int) {
        val unit = scene.width() / 240f
        val h = kotlin.math.ceil(scene.height() / unit).toInt()
        if (effectsFrame?.height != h) {
            effectsFrame?.recycle()
            effectsFrame = Bitmap.createBitmap(240, h, Bitmap.Config.ARGB_8888).also { effectsCanvas = Canvas(it) }
        }
        val frame = requireNotNull(effectsFrame)
        frame.eraseColor(android.graphics.Color.TRANSPARENT)
        sceneArt.drawProjectile(requireNotNull(effectsCanvas), progress,
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
        val slots = DungeonDemoSprites.slots
        if (heroReady && hero.archetype == heroArchetype && wearsSamePieces(hero)) return
        heroReady = true
        heroArchetype = hero.archetype
        for (i in slots.indices) heroPieces[i] = hero.equipped[slots[i]]
        equipmentVersion++
        val pieces = slots.associateWith { slot -> hero.equipped[slot] }
        val fallback = hero.archetype ?: Archetype.WARRIOR
        equipment = pieces.mapValues { (slot, item) ->
            EquipmentArt.gear(slot, item, fallback)
        }
        images.values.forEach { it.recycle() }
        images = equipment.mapValues { (slot, gear) ->
            pieces[slot]?.let { EquipmentArt.icon(it).copy(Bitmap.Config.ARGB_8888, false) }
                ?: DungeonDemoSprites.equipment(gear)
        }
    }

    private fun wearsSamePieces(hero: Hero): Boolean {
        val slots = DungeonDemoSprites.slots
        for (i in slots.indices) if (hero.equipped[slots[i]] !== heroPieces[i]) return false
        return true
    }

    private fun style(type: MonsterType): DungeonDemoSprites.MonsterStyle = when (type) {
        MonsterType.SPIDER -> DungeonDemoSprites.MonsterStyle.SPIDER
        MonsterType.RAT -> DungeonDemoSprites.MonsterStyle.COMMON
        MonsterType.GOBLIN -> DungeonDemoSprites.MonsterStyle.GOBLIN_GREEN
        MonsterType.SKELETON -> DungeonDemoSprites.MonsterStyle.SKELETON
        MonsterType.DEMON -> DungeonDemoSprites.MonsterStyle.DEMON_EMBER
        MonsterType.SCORPION -> DungeonDemoSprites.MonsterStyle.SCORPION_SAND
        MonsterType.CARNIVOROUS_PLANT -> DungeonDemoSprites.MonsterStyle.PLANT_JADE
        MonsterType.FELINE -> DungeonDemoSprites.MonsterStyle.FELINE_TAWNY
        MonsterType.WOLF -> DungeonDemoSprites.MonsterStyle.WOLF_GREY
        MonsterType.BEAR -> DungeonDemoSprites.MonsterStyle.BEAR_BROWN
        MonsterType.TROLL -> DungeonDemoSprites.MonsterStyle.TROLL_MOSS
        MonsterType.SNAKE -> DungeonDemoSprites.MonsterStyle.SNAKE_EMERALD
        MonsterType.ZOMBIE -> DungeonDemoSprites.MonsterStyle.ZOMBIE
        MonsterType.VAMPIRE -> DungeonDemoSprites.MonsterStyle.VAMPIRE
        MonsterType.VAMPIRE_BAT -> DungeonDemoSprites.MonsterStyle.VAMPIRE_BAT
        MonsterType.PIRATE -> DungeonDemoSprites.MonsterStyle.PIRATE
        MonsterType.PIRATE_BRUTE -> DungeonDemoSprites.MonsterStyle.PIRATE_BRUTE
        MonsterType.PIRATE_CAPTAIN -> DungeonDemoSprites.MonsterStyle.PIRATE_CAPTAIN
        MonsterType.ALIEN_SCOUT -> DungeonDemoSprites.MonsterStyle.ALIEN_SCOUT
        MonsterType.ALIEN_CRAWLER -> DungeonDemoSprites.MonsterStyle.ALIEN_CRAWLER
        MonsterType.ALIEN_FLOATER -> DungeonDemoSprites.MonsterStyle.ALIEN_FLOATER
    }
}
