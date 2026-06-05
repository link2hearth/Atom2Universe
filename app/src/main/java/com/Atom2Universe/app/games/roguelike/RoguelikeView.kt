package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class RoguelikeView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var game: RoguelikeGame? = null
    var onMove:           ((Int, Int) -> Unit)? = null
    var onUseItem:        (() -> Unit)? = null
    var onDescend:        (() -> Unit)? = null
    var onBuyShopItem:    ((ShopItem) -> Unit)? = null
    var onConfirmDescend: (() -> Unit)? = null
    var onEquipItem:      (() -> Unit)? = null
    var onIgnoreDrop:     (() -> Unit)? = null

    private var tileSize = 40f
    private val hpBarW get() = context.resources.displayMetrics.density * 6f

    private var camX = 0f
    private var camY = 0f

    // Sprite sheet 64x64.png pour les équipements
    private var equipSheet: Bitmap? = null

    // ── Paints ──────────────────────────────────────────────────────────────────
    private val pWallFallback  = Paint().apply { color = 0xFF1A1A1A.toInt(); isAntiAlias = false }
    private val pFloorFallback = Paint().apply { color = 0xFF3A2E24.toInt(); isAntiAlias = false }

    private val pSprite    = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pSpriteDim = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 70 }
    private val pFog       = Paint().apply { color = 0xCC000000.toInt(); isAntiAlias = false }

    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val pHpBar    = Paint().apply { isAntiAlias = false }
    private val pBarrier  = Paint().apply { color = 0xFF1E88E5.toInt(); isAntiAlias = false }
    private val pHpBg     = Paint().apply { color = 0xFF111111.toInt(); isAntiAlias = false }
    private val pHpSep    = Paint().apply { color = 0xFF555555.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val pShopBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0101820.toInt() }
    private val pShopCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2A38.toInt() }
    private val pShopBuy  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1565C0.toInt() }
    private val pShopSold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() }
    private val pShopDescend = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt() }
    private val pOverlay  = Paint().apply { color = 0xCC000000.toInt() }
    private val pMobHpBg  = Paint().apply { color = 0xFF2E2E2E.toInt() }
    private val pMobHp    = Paint().apply { color = 0xFF4CAF50.toInt() }
    private val pIconBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA000000.toInt() }
    private val pSwipeDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
    private val pSwipeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }

    // Popup loot
    private val pLootBg      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2101820.toInt() }
    private val pLootBorder  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val pLootCardBg  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2A38.toInt() }
    private val pEquipBtn    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1565C0.toInt() }
    private val pIgnoreBtn   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF424242.toInt() }

    // Inventaire
    private val pInvBg       = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2080E18.toInt() }
    private val pInvCard     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141E2A.toInt() }
    private val pInvSlotBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0D1520.toInt() }
    private val pInvSlotSel  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A3A5C.toInt() }
    private val pInvBorder   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0xFF2A3E52.toInt() }
    private val pInvBorderSel= Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f;   color = 0xFF42A5F5.toInt() }
    private val pStatGood    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF66FF88.toInt() }
    private val pStatNeutral = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCCCCCC.toInt() }

    // ── Swipe ───────────────────────────────────────────────────────────────────
    private val swipeMinPx get() = 16f * context.resources.displayMetrics.density
    private var touchDownX = 0f; private var touchDownY = 0f
    private var touchCurrX = 0f; private var touchCurrY = 0f
    private var touching   = false

    // ── État inventaire ──────────────────────────────────────────────────────────
    private var inventoryOpen       = false
    private var selectedEquipSlot:    EquipSlot? = null
    private var cachedSlotRects:      Map<EquipSlot, RectF> = emptyMap()

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        tileSize = w / 11f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        SpriteLoader.clear()
        equipSheet?.recycle(); equipSheet = null
    }

    // ── Sprite sheet équipements ─────────────────────────────────────────────────

    private fun equipSheet(): Bitmap? {
        if (equipSheet != null) return equipSheet
        return try {
            context.assets.open("64x64.png").use { BitmapFactory.decodeStream(it) }.also { equipSheet = it }
        } catch (_: Exception) { null }
    }

    private fun drawEquipIcon(canvas: Canvas, equip: Equipment, rect: RectF) {
        val sheet = equipSheet() ?: run {
            // fallback : cercle coloré rareté
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = equip.rarity.colorArgb }
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2.5f, p)
            return
        }
        val src = Rect(equip.spriteCol * 64, equip.spriteRow * 64,
                       (equip.spriteCol + 1) * 64, (equip.spriteRow + 1) * 64)
        canvas.drawBitmap(sheet, src, rect, pSprite)
    }

    // ── Draw ────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        updateCamera(g)
        drawMap(canvas, g)
        drawItems(canvas, g)
        drawEquipDrops(canvas, g)
        drawMonsters(canvas, g)
        drawPlayer(canvas, g)
        drawHpBar(canvas, g)
        drawBarrierBar(canvas, g)
        drawHud(canvas, g)
        drawHudIcons(canvas, g)
        if (g.shopOpen)                 drawShop(canvas, g)
        if (g.pendingEquipDrop != null) drawLootPopup(canvas, g)
        if (inventoryOpen && !g.shopOpen && g.pendingEquipDrop == null) drawInventory(canvas, g)
        if (g.phase != GamePhase.PLAYING && !g.shopOpen && g.pendingEquipDrop == null && !inventoryOpen)
            drawEndScreen(canvas, g)
        if (!g.shopOpen && g.pendingEquipDrop == null && !inventoryOpen) drawSwipeZone(canvas)
    }

    // ── Caméra ──────────────────────────────────────────────────────────────────

    private fun updateCamera(g: RoguelikeGame) {
        val barW   = hpBarW
        val availW = width - barW
        val px = g.player.pos.x * tileSize; val py = g.player.pos.y * tileSize
        val targetCx = px - (barW + availW / 2f) + tileSize / 2f
        val targetCy = py - height / 2f + tileSize / 2f
        camX = targetCx.coerceIn(0f, maxOf(0f, g.level.w * tileSize - width.toFloat()))
        camY = targetCy.coerceIn(0f, maxOf(0f, g.level.h * tileSize - height.toFloat()))
    }

    private fun tileLeft(tx: Int) = tx * tileSize - camX
    private fun tileTop(ty: Int)  = ty * tileSize - camY

    private fun isOnScreen(tx: Int, ty: Int): Boolean {
        val l = tileLeft(tx); val t = tileTop(ty)
        return l + tileSize > hpBarW && l < width && t + tileSize > 0 && t < height
    }

    private fun variantIndex(tx: Int, ty: Int, size: Int): Int {
        val h = tx * 73856093 xor ty * 19349663
        return (h and Int.MAX_VALUE) % size
    }

    private fun drawSprite(canvas: Canvas, path: String, rect: RectF, dim: Boolean) {
        val bmp = SpriteLoader.load(context.assets, path) ?: return
        canvas.drawBitmap(bmp, null, rect, if (dim) pSpriteDim else pSprite)
    }

    private fun monsterSpritePath(type: MonsterType): String = when (type) {
        MonsterType.RAT      -> "Assets/sprites/Dungeon/Monsters/misc/fire_bat.png"
        MonsterType.GOBLIN   -> "Assets/sprites/Dungeon/Monsters/misc/quasit.png"
        MonsterType.SKELETON -> "Assets/sprites/Dungeon/Monsters/skeleton/skeleton_humanoid.png"
        MonsterType.ORC      -> "Assets/sprites/Dungeon/Monsters/deepdwarf/deepdwarf_berzerker.png"
        MonsterType.DEMON    -> "Assets/sprites/Dungeon/Monsters/pandemon/examples/monsters_pandemon_examples_a.png"
    }

    // ── Map ─────────────────────────────────────────────────────────────────────

    private fun drawMap(canvas: Canvas, g: RoguelikeGame) {
        val lv = g.level; val theme = lv.theme
        val floorSprites = SpriteLoader.listDir(context.assets, theme.floorDir)
        val wallSprites  = SpriteLoader.listDir(context.assets, theme.wallDir)

        for (ty in 0 until lv.h) for (tx in 0 until lv.w) {
            if (!isOnScreen(tx, ty)) continue
            val vis = lv.visible[ty][tx]; val exp = lv.explored[ty][tx]
            if (!exp) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val rect = RectF(l, t, l + tileSize, t + tileSize)

            when (val tile = lv.tiles[ty][tx]) {
                TileType.WALL -> {
                    if (wallSprites.isNotEmpty()) drawSprite(canvas, wallSprites[variantIndex(tx, ty, wallSprites.size)], rect, !vis)
                    else canvas.drawRect(rect, pWallFallback)
                }
                TileType.FLOOR, TileType.STAIRS_DOWN -> {
                    if (floorSprites.isNotEmpty()) drawSprite(canvas, floorSprites[variantIndex(tx, ty, floorSprites.size)], rect, !vis)
                    else canvas.drawRect(rect, if (vis) pFloorFallback else pWallFallback)
                    if (tile == TileType.STAIRS_DOWN) {
                        canvas.drawRect(rect, Paint().apply { color = 0x664A3800.toInt() })
                        pText.color = if (vis) 0xFFFFD600.toInt() else 0xFF665500.toInt()
                        pText.textSize = tileSize * 0.7f
                        canvas.drawText(">", l + tileSize / 2f, t + tileSize * 0.75f, pText)
                    }
                }
            }
            if (!vis) { pFog.alpha = 100; canvas.drawRect(rect, pFog) }
        }
    }

    // ── Items consommables ───────────────────────────────────────────────────────

    private fun drawItems(canvas: Canvas, g: RoguelikeGame) {
        for (item in g.level.items) {
            val tx = item.pos.x; val ty = item.pos.y
            if (!isOnScreen(tx, ty) || !g.level.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            pText.color = item.type.colorArgb; pText.textSize = tileSize * 0.65f
            canvas.drawText(item.type.symbol.toString(), l + tileSize / 2f, t + tileSize * 0.75f, pText)
        }
    }

    // ── Drops d'équipement sur la carte ─────────────────────────────────────────

    private fun drawEquipDrops(canvas: Canvas, g: RoguelikeGame) {
        for ((equip, pos) in g.level.equipDrops) {
            val tx = pos.x; val ty = pos.y
            if (!isOnScreen(tx, ty) || !g.level.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val pad = tileSize * 0.1f
            val rect = RectF(l + pad, t + pad, l + tileSize - pad, t + tileSize - pad)
            // Halo couleur rareté
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = equip.rarity.colorArgb and 0x66FFFFFF.toInt()
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f + 2f, glowPaint)
            drawEquipIcon(canvas, equip, rect)
        }
    }

    // ── Monstres ────────────────────────────────────────────────────────────────

    private fun drawMonsters(canvas: Canvas, g: RoguelikeGame) {
        val lv = g.level; val barH = tileSize * 0.15f
        for (m in lv.monsters) {
            if (!m.isAlive) continue
            val tx = m.pos.x; val ty = m.pos.y
            if (!isOnScreen(tx, ty) || !lv.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val rect = RectF(l, t, l + tileSize, t + tileSize)
            val bmp  = SpriteLoader.load(context.assets, monsterSpritePath(m.type))
            if (bmp != null) canvas.drawBitmap(bmp, null, rect, pSprite)
            else {
                pText.color = m.type.colorArgb; pText.textSize = tileSize * 0.7f
                canvas.drawText(m.type.symbol.toString(), l + tileSize / 2f, t + tileSize * 0.82f, pText)
            }
            val barW = tileSize - 2f
            canvas.drawRect(RectF(l + 1f, t, l + 1f + barW, t + barH), pMobHpBg)
            canvas.drawRect(RectF(l + 1f, t, l + 1f + barW * m.hp / m.maxHp, t + barH), pMobHp)
        }
    }

    // ── Joueur ──────────────────────────────────────────────────────────────────

    private fun drawPlayer(canvas: Canvas, g: RoguelikeGame) {
        val tx = g.player.pos.x; val ty = g.player.pos.y
        val l = tileLeft(tx); val t = tileTop(ty)
        val rect = RectF(l, t, l + tileSize, t + tileSize)
        val bmp  = SpriteLoader.load(context.assets, g.heroSpritePath)
        if (bmp != null) canvas.drawBitmap(bmp, null, rect, pSprite)
        else {
            pText.color = 0xFF00E5FF.toInt(); pText.textSize = tileSize * 0.8f
            canvas.drawText("@", l + tileSize / 2f, t + tileSize * 0.82f, pText)
        }
    }

    // ── Barre HP ────────────────────────────────────────────────────────────────

    private fun drawHpBar(canvas: Canvas, g: RoguelikeGame) {
        val p = g.player; val barW = hpBarW; val barH = height.toFloat()
        canvas.drawRect(RectF(0f, 0f, barW, barH), pHpBg)
        val fillH = barH * p.hp.toFloat() / p.totalMaxHp
        pHpBar.color = hpColor(p.hp, p.totalMaxHp)
        canvas.drawRect(RectF(0f, barH - fillH, barW, barH), pHpBar)
        canvas.drawLine(barW, 0f, barW, barH, pHpSep)
    }

    // ── Barre barrière ───────────────────────────────────────────────────────────

    private fun drawBarrierBar(canvas: Canvas, g: RoguelikeGame) {
        val p = g.player; if (!p.barrierUnlocked) return
        val barW = hpBarW; val barH = height.toFloat(); val x0 = width - barW
        canvas.drawRect(RectF(x0, 0f, width.toFloat(), barH), pHpBg)
        if (p.maxBarrier > 0 && p.barrier > 0) {
            val fillH = barH * p.barrier.toFloat() / p.maxBarrier
            canvas.drawRect(RectF(x0, barH - fillH, width.toFloat(), barH), pBarrier)
        }
        canvas.drawLine(x0, 0f, x0, barH, pHpSep)
    }

    // ── HUD ─────────────────────────────────────────────────────────────────────

    private fun drawHud(canvas: Canvas, g: RoguelikeGame) {
        val sd       = context.resources.displayMetrics.density * context.resources.configuration.fontScale
        val logSize  = sd * 17f; val hintSize = sd * 15f
        val lineH    = logSize * 1.4f
        val lines    = g.log.takeLast(3)
        val hudH     = lines.size * lineH + 16f
        val hudY     = height - hudH

        canvas.drawRect(RectF(hpBarW, hudY, width.toFloat(), height.toFloat()), pOverlay)
        pText.textSize = logSize; pText.textAlign = Paint.Align.LEFT; pText.color = 0xFFDDDDDD.toInt()
        for ((i, line) in lines.withIndex())
            canvas.drawText(line, hpBarW + 10f, hudY + 10f + (i + 1) * lineH - 4f, pText)
        pText.textAlign = Paint.Align.CENTER

        if (g.onStairsTile()) {
            pText.color = 0xFFFFD600.toInt(); pText.textSize = hintSize
            canvas.drawText("[ > ] Tap Descend", (hpBarW + width) / 2f, hudY - 8f, pText)
        }
    }

    private fun hpColor(hp: Int, max: Int): Int {
        val r = hp.toFloat() / max
        return when { r > 0.5f -> 0xFF2E7D32.toInt(); r > 0.25f -> 0xFFE65100.toInt(); else -> 0xFFC62828.toInt() }
    }

    // ── Fin de partie ────────────────────────────────────────────────────────────

    private fun drawEndScreen(canvas: Canvas, g: RoguelikeGame) {
        val sd = context.resources.displayMetrics.density * context.resources.configuration.fontScale
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        val cx = width / 2f; val cy = height / 2f
        pText.textAlign = Paint.Align.CENTER
        pText.color = 0xFFEF5350.toInt(); pText.textSize = sd * 28f
        canvas.drawText("TU ES MORT", cx, cy - 50f, pText)
        pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 16f
        canvas.drawText("Étage ${g.player.floor}  —  ${g.player.gold} or", cx, cy, pText)
        pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 14f
        canvas.drawText("Tap pour recommencer", cx, cy + 50f, pText)
    }

    // ── Icônes HUD ───────────────────────────────────────────────────────────────

    private val iconSizePx   get() = 44f * context.resources.displayMetrics.density
    private val iconMarginPx get() = 8f  * context.resources.displayMetrics.density

    private fun heartRect(): RectF {
        val m = iconMarginPx; val s = iconSizePx
        return RectF(hpBarW + m, m, hpBarW + m + s, m + s)
    }

    private fun stairsIconRect(): RectF {
        val m = iconMarginPx; val s = iconSizePx
        return RectF(width - m - s, m, width - m, m + s)
    }

    private fun inventoryBtnRect(): RectF {
        val m = iconMarginPx; val s = iconSizePx
        // En bas à gauche, au-dessus du log
        return RectF(hpBarW + m, height - m * 2 - s * 2.3f, hpBarW + m + s, height - m * 2 - s * 1.3f)
    }

    private fun drawHudIcons(canvas: Canvas, g: RoguelikeGame) {
        if (g.phase != GamePhase.PLAYING) return
        val sd = context.resources.displayMetrics.density * context.resources.configuration.fontScale
        pText.textAlign = Paint.Align.CENTER

        if (g.player.inventory.isNotEmpty()) {
            val r = heartRect()
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, pIconBg)
            pText.color = 0xFFEF5350.toInt(); pText.textSize = r.height() * 0.55f
            canvas.drawText("♥", r.centerX(), r.centerY() + r.height() * 0.2f, pText)
            pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 11f; pText.textAlign = Paint.Align.RIGHT
            canvas.drawText("${g.player.inventory.size}", r.right, r.top + sd * 13f, pText)
            pText.textAlign = Paint.Align.CENTER
        }

        if (g.onStairsTile()) {
            val r = stairsIconRect()
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, pIconBg)
            pText.color = 0xFFFFD600.toInt(); pText.textSize = r.height() * 0.6f
            canvas.drawText("↓", r.centerX(), r.centerY() + r.height() * 0.22f, pText)
        }

        // Bouton inventaire (sac ⚔)
        val r = inventoryBtnRect()
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (inventoryOpen) 0xCC1565C0.toInt() else 0xAA000000.toInt()
        }
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, bgPaint)
        pText.color = 0xFFCCCCCC.toInt(); pText.textSize = r.height() * 0.52f
        canvas.drawText("⚔", r.centerX(), r.centerY() + r.height() * 0.19f, pText)
    }

    // ── Inventaire / Paperdoll ───────────────────────────────────────────────────

    private fun invPanelRect()  = RectF(width * 0.03f, height * 0.05f, width * 0.97f, height * 0.95f)
    private fun invCloseBtnRect(panel: RectF): RectF {
        val s = panel.height() * 0.06f
        return RectF(panel.right - s - 8f, panel.top + 8f, panel.right - 8f, panel.top + 8f + s)
    }

    /**
     * Retourne les RectF de chaque slot d'équipement + du héros dans la zone paperdoll.
     * Layout :
     *          [HELMET]
     * [WEAPON] [ HERO ] [CHEST ]
     * [OFFHND] [ HERO ] [AMULET]
     *   [BOOTS]  gap  [RING]
     */
    private fun paperdollSlotRects(paperdoll: RectF): Pair<RectF, Map<EquipSlot, RectF>> {
        val cell  = minOf(paperdoll.width() / 3.6f, paperdoll.height() / 4.4f)
        val gap   = cell * 0.15f
        val cx    = paperdoll.centerX()
        val top   = paperdoll.top + gap

        fun r(col: Float, row: Float) = RectF(
            cx + (col - 1f) * (cell + gap),
            top + row * (cell + gap),
            cx + (col - 1f) * (cell + gap) + cell,
            top + row * (cell + gap) + cell
        )

        val heroRect = RectF(
            cx - cell / 2f,
            top + (cell + gap),
            cx + cell / 2f,
            top + (cell + gap) + cell * 2f + gap
        )

        val slots = mapOf(
            EquipSlot.HELMET  to r(1f, 0f),
            EquipSlot.WEAPON  to r(0f, 1f),
            EquipSlot.CHEST   to r(2f, 1f),
            EquipSlot.OFFHAND to r(0f, 2f),
            EquipSlot.AMULET  to r(2f, 2f),
            EquipSlot.BOOTS   to RectF(cx - cell - gap / 2f, top + 3f * (cell + gap), cx - gap / 2f, top + 3f * (cell + gap) + cell),
            EquipSlot.RING    to RectF(cx + gap / 2f,        top + 3f * (cell + gap), cx + cell + gap / 2f, top + 3f * (cell + gap) + cell),
        )
        return heroRect to slots
    }

    private fun drawInventory(canvas: Canvas, g: RoguelikeGame) {
        val sd    = context.resources.displayMetrics.density * context.resources.configuration.fontScale
        val cr    = 12f * context.resources.displayMetrics.density
        val panel = invPanelRect()
        val gap   = panel.height() * 0.015f

        // Fond
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        canvas.drawRoundRect(panel, cr, cr, pInvBg)
        canvas.drawRoundRect(panel, cr, cr, pInvBorder)

        // Titre
        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 14f
        canvas.drawText("ÉQUIPEMENT", panel.centerX(), panel.top + gap + sd * 14f, pText)

        // Bouton fermer ✕
        val closeR = invCloseBtnRect(panel)
        canvas.drawRoundRect(closeR, cr * 0.4f, cr * 0.4f, pIgnoreBtn)
        pText.color = 0xFFAAAAAA.toInt(); pText.textSize = closeR.height() * 0.6f
        canvas.drawText("✕", closeR.centerX(), closeR.centerY() + closeR.height() * 0.22f, pText)

        val titleBottom = panel.top + gap * 2 + sd * 14f

        // Divise le panel : haut=paperdoll, milieu=stats globales, bas=détail item
        val paperdollH = panel.height() * 0.48f
        val statsH     = panel.height() * 0.22f
        val detailH    = panel.height() - paperdollH - statsH - gap * 4 - (titleBottom - panel.top)

        val paperdollRect = RectF(panel.left + gap, titleBottom + gap,
                                  panel.right - gap, titleBottom + gap + paperdollH)
        val statsRect     = RectF(panel.left + gap, paperdollRect.bottom + gap,
                                  panel.right - gap, paperdollRect.bottom + gap + statsH)
        val detailRect    = RectF(panel.left + gap, statsRect.bottom + gap,
                                  panel.right - gap, statsRect.bottom + gap + detailH)

        drawPaperdoll(canvas, g, paperdollRect, sd, cr)
        drawStatsPanel(canvas, g, statsRect, sd, cr, gap)
        drawDetailPanel(canvas, g, detailRect, sd, cr, gap)
    }

    private fun drawPaperdoll(canvas: Canvas, g: RoguelikeGame, rect: RectF, sd: Float, cr: Float) {
        canvas.drawRoundRect(rect, cr, cr, pInvCard)
        val (heroR, slots) = paperdollSlotRects(rect)
        cachedSlotRects = slots  // mis en cache pour le touch handler

        // Sprite héros
        val heroBmp = SpriteLoader.load(context.assets, g.heroSpritePath)
        if (heroBmp != null) canvas.drawBitmap(heroBmp, null, heroR, pSprite)
        else {
            pText.color = 0xFF00E5FF.toInt(); pText.textSize = heroR.height() * 0.6f
            canvas.drawText("@", heroR.centerX(), heroR.centerY() + heroR.height() * 0.22f, pText)
        }

        // Slots d'équipement
        for ((slot, slotR) in slots) {
            val isSelected = selectedEquipSlot == slot
            val equip = g.player.equipped[slot]

            // Fond slot
            canvas.drawRoundRect(slotR, cr * 0.5f, cr * 0.5f, if (isSelected) pInvSlotSel else pInvSlotBg)
            canvas.drawRoundRect(slotR, cr * 0.5f, cr * 0.5f, if (isSelected) pInvBorderSel else pInvBorder)

            if (equip != null) {
                // Halo rareté subtil
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = equip.rarity.colorArgb and 0x33FFFFFF.toInt()
                }
                canvas.drawRoundRect(slotR, cr * 0.5f, cr * 0.5f, haloPaint)
                drawEquipIcon(canvas, equip, slotR.inset(slotR.width() * 0.08f))
            } else {
                // Slot vide : icône de slot
                pText.textAlign = Paint.Align.CENTER
                pText.color = 0xFF2A3E52.toInt(); pText.textSize = slotR.height() * 0.4f
                val slotIcon = when (slot) {
                    EquipSlot.WEAPON  -> "⚔"; EquipSlot.CHEST   -> "◻"; EquipSlot.HELMET  -> "◯"
                    EquipSlot.BOOTS   -> "∪"; EquipSlot.OFFHAND -> "⊡"; EquipSlot.AMULET  -> "◇"
                    EquipSlot.RING    -> "○"
                }
                canvas.drawText(slotIcon, slotR.centerX(), slotR.centerY() + slotR.height() * 0.15f, pText)
            }

            // Label slot (en dessous)
            pText.textAlign = Paint.Align.CENTER
            pText.color = if (isSelected) 0xFF42A5F5.toInt() else 0xFF445566.toInt()
            pText.textSize = slotR.height() * 0.22f
            canvas.drawText(slot.label, slotR.centerX(), slotR.bottom + slotR.height() * 0.28f, pText)
        }
    }

    private fun drawStatsPanel(canvas: Canvas, g: RoguelikeGame, rect: RectF, sd: Float, cr: Float, gap: Float) {
        canvas.drawRoundRect(rect, cr, cr, pInvCard)
        val p = g.player

        pText.textAlign = Paint.Align.LEFT
        pText.color = 0xFF888888.toInt(); pText.textSize = sd * 11f
        canvas.drawText("STATISTIQUES", rect.left + gap * 2, rect.top + gap + sd * 11f, pText)

        val col1 = rect.left + gap * 2
        val col2 = rect.left + rect.width() * 0.5f
        val lineH = (rect.height() - gap * 3 - sd * 11f) / 4f
        val y0 = rect.top + gap * 2 + sd * 11f

        fun stat(label: String, value: String, col: Float, row: Int) {
            val y = y0 + row * lineH + lineH * 0.7f
            pText.color = 0xFF556677.toInt(); pText.textSize = sd * 11f
            canvas.drawText(label, col, y, pText)
            pText.color = 0xFFDDEEFF.toInt(); pText.textSize = sd * 13f
            canvas.drawText(value, col + rect.width() * 0.2f, y, pText)
        }

        stat("HP",      "${p.hp} / ${p.totalMaxHp}",                      col1, 0)
        stat("ATK",     "${p.atk}",                                        col2, 0)
        stat("DEF",     "${p.def}",                                        col1, 1)
        stat("Crit",    "${(p.critChance * 100).toInt()}%",                col2, 1)
        stat("Crit×",   "×${"%.1f".format(p.critDmgMult)}",               col1, 2)
        stat("Esquive", "${(p.evasionChance * 100).toInt()}%",             col2, 2)
        stat("Blocage", "${(p.blockChance * 100).toInt()}%",               col1, 3)
        stat("Or",      "${p.gold}",                                       col2, 3)
    }

    private fun drawDetailPanel(canvas: Canvas, g: RoguelikeGame, rect: RectF, sd: Float, cr: Float, gap: Float) {
        canvas.drawRoundRect(rect, cr, cr, pInvCard)
        val slot  = selectedEquipSlot
        val equip = if (slot != null) g.player.equipped[slot] else null

        pText.textAlign = Paint.Align.CENTER
        if (equip == null) {
            pText.color = if (slot == null) 0xFF334455.toInt() else 0xFF556677.toInt()
            pText.textSize = sd * 12f
            canvas.drawText(
                if (slot == null) "← Sélectionne un emplacement" else "— Emplacement vide —",
                rect.centerX(), rect.centerY() + sd * 6f, pText
            )
            return
        }

        // Icône item (petit, à gauche)
        val iconSize = rect.height() * 0.55f
        val iconR = RectF(rect.left + gap * 2, rect.centerY() - iconSize / 2,
                          rect.left + gap * 2 + iconSize, rect.centerY() + iconSize / 2)
        canvas.drawRoundRect(iconR, cr * 0.4f, cr * 0.4f, pInvSlotBg)
        drawEquipIcon(canvas, equip, iconR)

        val textX = iconR.right + gap * 2

        // Nom + rareté
        pText.textAlign = Paint.Align.LEFT
        pText.color = equip.rarity.colorArgb; pText.textSize = sd * 13f
        canvas.drawText(equip.label, textX, rect.top + gap + sd * 13f, pText)

        // Matière
        pText.color = 0xFF556677.toInt(); pText.textSize = sd * 11f
        canvas.drawText("${equip.material.label} · ${equip.slot.label}", textX, rect.top + gap * 2 + sd * 24f, pText)

        // Stats
        var statY = rect.top + gap * 3 + sd * 35f
        val statLineH = sd * 13f + gap * 0.4f
        for (stat in equip.stats) {
            pText.color = 0xFF88FFAA.toInt(); pText.textSize = sd * 13f
            canvas.drawText(stat.display(), textX, statY, pText)
            statY += statLineH
        }
    }

    private fun RectF.inset(amount: Float) = RectF(left + amount, top + amount, right - amount, bottom - amount)

    // ── Shop ────────────────────────────────────────────────────────────────────

    private fun shopPanelRect() = RectF(width * 0.06f, height * 0.08f, width * 0.94f, height * 0.92f)

    private fun shopItemRect(index: Int, panel: RectF): RectF {
        val topPad = panel.height() * 0.12f; val btnH = panel.height() * 0.10f
        val gap    = panel.height() * 0.02f; val cardH = (panel.height() - topPad - btnH - gap * 4) / 3f
        val top    = panel.top + topPad + index * (cardH + gap)
        return RectF(panel.left + gap, top, panel.right - gap, top + cardH)
    }

    private fun shopDescendRect(panel: RectF): RectF {
        val btnH = panel.height() * 0.10f; val gap = panel.height() * 0.02f
        return RectF(panel.left + gap * 4, panel.bottom - btnH - gap, panel.right - gap * 4, panel.bottom - gap)
    }

    private fun drawShop(canvas: Canvas, g: RoguelikeGame) {
        val sd = context.resources.displayMetrics.density * context.resources.configuration.fontScale
        val panel = shopPanelRect(); val cr = 16f * context.resources.displayMetrics.density
        canvas.drawRoundRect(panel, cr, cr, pShopBg)
        pText.textAlign = Paint.Align.CENTER
        pText.color = 0xFFFFD600.toInt(); pText.textSize = sd * 20f
        canvas.drawText("BOUTIQUE", panel.centerX(), panel.top + panel.height() * 0.08f, pText)
        pText.color = 0xFFAAAAAA.toInt(); pText.textSize = sd * 13f
        canvas.drawText("${g.player.gold} or disponibles", panel.centerX(), panel.top + panel.height() * 0.13f, pText)

        val items = listOf(
            Triple(ShopItem.POTION,  "♥  Vie max +10",       "HP max +10 + soins complets"),
            Triple(ShopItem.ATK_UP, "⚔  Cristal de force",  "ATK +1 permanent"),
            Triple(ShopItem.BARRIER,"◈  Barrière",           "Débloque la régénération de barrière")
        )
        items.forEachIndexed { i, (item, name, desc) ->
            val r      = shopItemRect(i, panel)
            val bought = item in g.shopBought; val canBuy = !bought && g.player.gold >= item.cost
            canvas.drawRoundRect(r, cr * 0.6f, cr * 0.6f, if (bought) pShopSold else pShopCard)
            pText.textAlign = Paint.Align.LEFT
            pText.color = if (bought) 0xFF666666.toInt() else 0xFFEEEEEE.toInt(); pText.textSize = sd * 15f
            canvas.drawText(name, r.left + r.height() * 0.2f, r.centerY() - sd * 4f, pText)
            pText.color = if (bought) 0xFF444444.toInt() else 0xFF888888.toInt(); pText.textSize = sd * 12f
            canvas.drawText(desc, r.left + r.height() * 0.2f, r.centerY() + sd * 12f, pText)
            val badgeW = r.height() * 1.1f
            val badgeR = RectF(r.right - badgeW - r.height() * 0.1f, r.top + r.height() * 0.2f, r.right - r.height() * 0.1f, r.bottom - r.height() * 0.2f)
            canvas.drawRoundRect(badgeR, cr * 0.4f, cr * 0.4f, when { bought -> pShopSold; canBuy -> pShopBuy; else -> pShopSold })
            pText.textAlign = Paint.Align.CENTER
            pText.color = if (bought) 0xFF555555.toInt() else 0xFFFFFFFF.toInt(); pText.textSize = sd * 13f
            canvas.drawText(if (bought) "✓" else "${item.cost}or", badgeR.centerX(), badgeR.centerY() + sd * 5f, pText)
        }

        val dRect = shopDescendRect(panel)
        canvas.drawRoundRect(dRect, cr * 0.6f, cr * 0.6f, pShopDescend)
        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 16f
        canvas.drawText("↓  Descendre", dRect.centerX(), dRect.centerY() + sd * 6f, pText)
    }

    // ── Popup de loot ────────────────────────────────────────────────────────────

    private fun lootPanelRect() = RectF(width * 0.04f, height * 0.10f, width * 0.96f, height * 0.90f)

    private fun lootEquipBtnRect(panel: RectF): RectF {
        val gap = panel.height() * 0.025f; val btnH = panel.height() * 0.11f
        return RectF(panel.left + gap * 2, panel.bottom - btnH - gap,
                     panel.centerX() - gap, panel.bottom - gap)
    }

    private fun lootIgnoreBtnRect(panel: RectF): RectF {
        val gap = panel.height() * 0.025f; val btnH = panel.height() * 0.11f
        return RectF(panel.centerX() + gap, panel.bottom - btnH - gap,
                     panel.right - gap * 2, panel.bottom - gap)
    }

    private fun drawLootPopup(canvas: Canvas, g: RoguelikeGame) {
        val equip   = g.pendingEquipDrop ?: return
        val current = g.player.equipped[equip.slot]
        val sd      = context.resources.displayMetrics.density * context.resources.configuration.fontScale
        val cr      = 14f * context.resources.displayMetrics.density
        val panel   = lootPanelRect()
        val gap     = panel.height() * 0.025f

        // Assombrir le fond
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)

        // Panneau principal
        canvas.drawRoundRect(panel, cr, cr, pLootBg)
        pLootBorder.color = equip.rarity.colorArgb
        canvas.drawRoundRect(panel, cr, cr, pLootBorder)

        // ── Titre ──────────────────────────────────────────────────────────────
        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 13f
        canvas.drawText("— ${equip.slot.label} trouvé —", panel.centerX(), panel.top + gap + sd * 13f, pText)

        val titleBottom = panel.top + gap * 2 + sd * 13f

        // ── Séparateur vertical central ────────────────────────────────────────
        val sepX    = panel.centerX()
        val btnTop  = lootEquipBtnRect(panel).top - gap
        val dividerPaint = Paint().apply { color = 0xFF333344.toInt(); strokeWidth = 1f }
        canvas.drawLine(sepX, titleBottom, sepX, btnTop, dividerPaint)

        // ── Colonnes : NOUVEAU (gauche) | ÉQUIPÉ (current, droite) ─────────────
        val colL = RectF(panel.left,  titleBottom, sepX - gap * 0.5f, btnTop)
        val colR = RectF(sepX + gap * 0.5f, titleBottom, panel.right, btnTop)

        drawItemColumn(canvas, equip,   colL, isNew = true,  sd, cr, gap)
        if (current != null)
            drawItemColumn(canvas, current, colR, isNew = false, sd, cr, gap)
        else {
            pText.textAlign = Paint.Align.CENTER; pText.color = 0xFF555555.toInt(); pText.textSize = sd * 13f
            canvas.drawText("(rien d'équipé)", colR.centerX(), colR.centerY(), pText)
        }

        // ── Indicateurs de delta par stat (sur la colonne gauche) ──────────────
        if (current != null) drawStatDeltas(canvas, equip, current, colL, colR, sd, gap)

        // ── Boutons ────────────────────────────────────────────────────────────
        val equipR  = lootEquipBtnRect(panel)
        val ignoreR = lootIgnoreBtnRect(panel)
        canvas.drawRoundRect(equipR,  cr * 0.6f, cr * 0.6f, pEquipBtn)
        canvas.drawRoundRect(ignoreR, cr * 0.6f, cr * 0.6f, pIgnoreBtn)
        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 15f
        canvas.drawText("Équiper",  equipR.centerX(),  equipR.centerY()  + sd * 6f, pText)
        canvas.drawText("Garder",   ignoreR.centerX(), ignoreR.centerY() + sd * 6f, pText)
    }

    /** Dessine une colonne item (icône + nom rareté + stats). */
    private fun drawItemColumn(
        canvas: Canvas, equip: Equipment, col: RectF,
        isNew: Boolean, sd: Float, cr: Float, gap: Float
    ) {
        val iconSize = minOf(col.width() * 0.45f, col.height() * 0.25f)
        val iconRect = RectF(col.centerX() - iconSize / 2, col.top + gap,
                             col.centerX() + iconSize / 2, col.top + gap + iconSize)

        // Fond icône + halo rareté
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = equip.rarity.colorArgb and 0x44FFFFFF.toInt() }
        canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconSize * 0.6f, haloPaint)
        canvas.drawRoundRect(iconRect, cr * 0.4f, cr * 0.4f, pLootCardBg)
        drawEquipIcon(canvas, equip, iconRect)

        var y = iconRect.bottom + gap

        // Étiquette NOUVEAU / ÉQUIPÉ
        pText.textAlign = Paint.Align.CENTER
        pText.color = if (isNew) 0xFFFFD600.toInt() else 0xFF888888.toInt()
        pText.textSize = sd * 11f
        canvas.drawText(if (isNew) "NOUVEAU" else "ÉQUIPÉ", col.centerX(), y + sd * 11f, pText)
        y += sd * 11f + gap * 0.5f

        // Nom avec couleur rareté
        pText.color = equip.rarity.colorArgb; pText.textSize = sd * 13f
        canvas.drawText(equip.label, col.centerX(), y + sd * 13f, pText)
        y += sd * 13f + gap * 0.6f

        // Stats
        for (stat in equip.stats) {
            pText.color = if (isNew) 0xFFDDFFDD.toInt() else 0xFFAAAAAA.toInt()
            pText.textSize = sd * 12f
            canvas.drawText(stat.display(), col.centerX(), y + sd * 12f, pText)
            y += sd * 12f + gap * 0.4f
        }
    }

    /**
     * Sur chaque stat du nouvel item, affiche un delta (+X ou -X) centré
     * sur le séparateur vertical entre les deux colonnes.
     */
    private fun drawStatDeltas(
        canvas: Canvas, newItem: Equipment, current: Equipment,
        colL: RectF, colR: RectF, sd: Float, gap: Float
    ) {
        // Calcule la somme par StatType pour chaque item
        fun sumStat(eq: Equipment, type: StatType) =
            eq.stats.filter { it.type == type }.sumOf { it.value.toDouble() }.toFloat()

        val sepX  = (colL.right + colR.left) / 2f
        // Position Y de départ des stats dans la colonne gauche
        // (approximatif : on aligne avec la zone stats du nouveau)
        val iconSize = minOf(colL.width() * 0.45f, colL.height() * 0.25f)
        var y = colL.top + gap + iconSize + gap + sd * 11f + gap * 0.5f + sd * 13f + gap * 0.6f

        val deltaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }

        for (stat in newItem.stats) {
            val newVal  = sumStat(newItem, stat.type)
            val curVal  = sumStat(current, stat.type)
            val delta   = newVal - curVal
            if (delta != 0f) {
                val sign   = if (delta > 0f) "▲" else "▼"
                val color  = if (delta > 0f) 0xFF66FF66.toInt() else 0xFFFF6666.toInt()
                val dispVal = if (stat.type.isPercent)
                    "${(kotlin.math.abs(delta) * 100).toInt()}%"
                else
                    "${kotlin.math.abs(delta).toInt()}"
                deltaPaint.color = color; deltaPaint.textSize = sd * 11f
                canvas.drawText("$sign$dispVal", sepX, y + sd * 12f, deltaPaint)
            }
            y += sd * 12f + gap * 0.4f
        }
    }

    // ── Swipe visuel ─────────────────────────────────────────────────────────────

    private fun drawSwipeZone(canvas: Canvas) {
        if (!touching) return
        val dx = touchCurrX - touchDownX; val dy = touchCurrY - touchDownY
        val dist = sqrt(dx * dx + dy * dy)
        canvas.drawCircle(touchDownX, touchDownY, 18f, pSwipeDot)
        if (dist >= swipeMinPx * 0.5f) {
            canvas.drawLine(touchDownX, touchDownY, touchCurrX, touchCurrY, pSwipeLine)
            val angle = atan2(dy, dx)
            val tipX = touchDownX + cos(angle) * (dist + 16f); val tipY = touchDownY + sin(angle) * (dist + 16f)
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(tipX + cos(angle + 2.4f) * 20f, tipY + sin(angle + 2.4f) * 20f)
                lineTo(tipX + cos(angle - 2.4f) * 20f, tipY + sin(angle - 2.4f) * 20f)
                close()
            }
            canvas.drawPath(path, pSwipeDot)
        }
    }

    // ── Touch ────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        game ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y
                touchCurrX = event.x; touchCurrY = event.y
                touching = true; invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchCurrX = event.x; touchCurrY = event.y; invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - touchDownX; val dy = event.y - touchDownY
                val dist = sqrt(dx * dx + dy * dy)
                val g = game

                if (g != null && g.pendingEquipDrop != null && dist < swipeMinPx) {
                    // ── Popup loot ──────────────────────────────────────────────
                    val panel = lootPanelRect()
                    when {
                        lootEquipBtnRect(panel).contains(touchDownX, touchDownY)  -> onEquipItem?.invoke()
                        lootIgnoreBtnRect(panel).contains(touchDownX, touchDownY) -> onIgnoreDrop?.invoke()
                    }
                } else if (g != null && g.shopOpen && dist < swipeMinPx) {
                    // ── Shop ────────────────────────────────────────────────────
                    val panel = shopPanelRect()
                    listOf(ShopItem.POTION, ShopItem.ATK_UP, ShopItem.BARRIER).forEachIndexed { i, item ->
                        if (shopItemRect(i, panel).contains(touchDownX, touchDownY)) onBuyShopItem?.invoke(item)
                    }
                    if (shopDescendRect(panel).contains(touchDownX, touchDownY)) onConfirmDescend?.invoke()
                } else if (inventoryOpen && dist < swipeMinPx) {
                    // ── Inventaire ──────────────────────────────────────────────
                    val panel = invPanelRect()
                    when {
                        invCloseBtnRect(panel).contains(touchDownX, touchDownY) -> {
                            inventoryOpen = false; selectedEquipSlot = null
                        }
                        else -> {
                            // Tap sur un slot ? (utilise les rects calculés au dernier draw)
                            val tapped = cachedSlotRects.entries
                                .firstOrNull { (_, r) -> r.contains(touchDownX, touchDownY) }
                            selectedEquipSlot = if (tapped?.key == selectedEquipSlot) null else tapped?.key
                        }
                    }
                } else if (dist < swipeMinPx) {
                    // ── Taps HUD normaux ────────────────────────────────────────
                    if (g != null && g.phase == GamePhase.PLAYING) {
                        when {
                            inventoryBtnRect().contains(touchDownX, touchDownY) -> {
                                inventoryOpen = !inventoryOpen
                                if (!inventoryOpen) selectedEquipSlot = null
                            }
                            heartRect().contains(touchDownX, touchDownY)
                                    && g.player.inventory.isNotEmpty() -> onUseItem?.invoke()
                            stairsIconRect().contains(touchDownX, touchDownY)
                                    && g.onStairsTile() -> onDescend?.invoke()
                        }
                    }
                } else if (g?.phase == GamePhase.PLAYING && g.pendingEquipDrop == null && !inventoryOpen) {
                    // ── Swipe déplacement ───────────────────────────────────────
                    val mx = if (abs(dx) > dist * 0.38f) dx.sign.toInt() else 0
                    val my = if (abs(dy) > dist * 0.38f) dy.sign.toInt() else 0
                    if (mx != 0 || my != 0) onMove?.invoke(mx, my)
                }
                touching = false; invalidate()
            }
        }
        return true
    }
}
