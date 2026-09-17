package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.*

/** Résout une entrée de journal : les arguments Labeled/Equipment sont d'abord traduits en texte. */
private fun LogEntry.resolve(context: Context): String {
    val resolvedArgs = args.map { a ->
        when (a) {
            is Equipment -> LootSystem.displayName(context, a)
            is Labeled   -> context.getString(a.labelRes)
            else         -> a
        }
    }
    return context.getString(keyRes, *resolvedArgs.toTypedArray())
}

/**
 * La carte : exploration au swipe, monstres visibles, repos, marchand sur l'escalier,
 * inventaire et popups de butin. Le combat, lui, a son propre écran ([CombatView]).
 */
class RoguelikeView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var game: RoguelikeGame? = null
    var onMove:           ((Int, Int) -> Unit)? = null
    var onRest:           (() -> Unit)? = null
    var onOpenMerchant:   (() -> Unit)? = null
    var onBuyPotion:      (() -> Unit)? = null
    var onDescend:        (() -> Unit)? = null
    var onCloseMerchant:  (() -> Unit)? = null
    var onEquipItem:      (() -> Unit)? = null
    var onIgnoreDrop:     (() -> Unit)? = null
    var onDismissDeath:   (() -> Unit)? = null

    private var tileSize = 40f
    private val hpBarW get() = context.resources.displayMetrics.density * 6f

    private var camX = 0f
    private var camY = 0f

    // Sprite sheet 64x64.png pour les équipements et objets
    private var equipSheet: Bitmap? = null

    // ── Paints ──────────────────────────────────────────────────────────────────
    private val pWallFallback  = Paint().apply { color = 0xFF1A1A1A.toInt(); isAntiAlias = false }
    private val pFloorFallback = Paint().apply { color = 0xFF3A2E24.toInt(); isAntiAlias = false }

    private val pSprite    = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pSpriteDim = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 70 }
    private val pFog       = Paint().apply { color = 0xCC000000.toInt(); isAntiAlias = false }
    private val pStairs    = Paint().apply { color = 0x664A3800 }

    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val pHpBar    = Paint().apply { isAntiAlias = false }
    private val pHpBg     = Paint().apply { color = 0xFF111111.toInt(); isAntiAlias = false }
    private val pHpSep    = Paint().apply { color = 0xFF555555.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val pShopBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0101820.toInt() }
    private val pShopCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2A38.toInt() }
    private val pShopBuy  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1565C0.toInt() }
    private val pShopSold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() }
    private val pShopDescend = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt() }
    private val pOverlay  = Paint().apply { color = 0xCC000000.toInt() }
    private val pIconBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA000000.toInt() }
    private val pIconOn   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC2E7D32.toInt() }
    private val pChase    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
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
        stopHold()
        SpriteLoader.clear()
        equipSheet?.recycle(); equipSheet = null
    }

    private val sd get() = context.resources.displayMetrics.density * context.resources.configuration.fontScale

    // ── Sprite sheet ─────────────────────────────────────────────────────────────

    private fun equipSheet(): Bitmap? {
        if (equipSheet != null) return equipSheet
        return try {
            context.assets.open("64x64.png").use { BitmapFactory.decodeStream(it) }.also { equipSheet = it }
        } catch (_: Exception) { null }
    }

    private fun drawSheetCell(canvas: Canvas, row: Int, col: Int, rect: RectF): Boolean {
        val sheet = equipSheet() ?: return false
        canvas.drawBitmap(sheet, Rect(col * 64, row * 64, (col + 1) * 64, (row + 1) * 64), rect, pSprite)
        return true
    }

    private fun drawEquipIcon(canvas: Canvas, equip: Equipment, rect: RectF) {
        if (!drawSheetCell(canvas, equip.spriteRow, equip.spriteCol, rect)) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = equip.rarity.colorArgb }
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2.5f, p)
        }
    }

    // ── Draw ────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        updateCamera(g)
        drawMap(canvas, g)
        drawItems(canvas, g)
        drawPacks(canvas, g)
        drawPlayer(canvas, g)
        drawHpBar(canvas, g)
        drawHud(canvas, g)
        drawHudIcons(canvas, g)
        val overlay = g.merchantOpen || g.pendingEquipDrop != null || g.deathReport != null
        if (g.merchantOpen)             drawShop(canvas, g)
        if (g.pendingEquipDrop != null) drawLootPopup(canvas, g)
        if (inventoryOpen && !overlay)  drawInventory(canvas, g)
        if (g.deathReport != null)      drawDeathPanel(canvas, g)
        if (!overlay && !inventoryOpen) drawSwipeZone(canvas)
    }

    // ── Caméra ──────────────────────────────────────────────────────────────────

    private fun updateCamera(g: RoguelikeGame) {
        val barW   = hpBarW
        val availW = width - barW
        val px = g.playerPos.x * tileSize; val py = g.playerPos.y * tileSize
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
                        canvas.drawRect(rect, pStairs)
                        pText.color = if (vis) 0xFFFFD600.toInt() else 0xFF665500.toInt()
                        pText.textSize = tileSize * 0.7f
                        canvas.drawText(">", l + tileSize / 2f, t + tileSize * 0.75f, pText)
                    }
                }
            }
            if (!vis) { pFog.alpha = 100; canvas.drawRect(rect, pFog) }
        }
    }

    // ── Objets au sol ────────────────────────────────────────────────────────────

    private fun drawItems(canvas: Canvas, g: RoguelikeGame) {
        for (item in g.level.items) {
            val tx = item.pos.x; val ty = item.pos.y
            if (!isOnScreen(tx, ty) || !g.level.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val pad = tileSize * 0.15f
            drawSheetCell(canvas, item.type.spriteRow, item.type.spriteCol, RectF(l + pad, t + pad, l + tileSize - pad, t + tileSize - pad))
        }
    }

    // ── Monstres ────────────────────────────────────────────────────────────────

    private fun drawPacks(canvas: Canvas, g: RoguelikeGame) {
        val lv = g.level
        for (pack in lv.packs) {
            if (!pack.alive) continue
            val tx = pack.pos.x; val ty = pack.pos.y
            if (!isOnScreen(tx, ty) || !lv.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val rect = RectF(l, t, l + tileSize, t + tileSize)
            SpriteLoader.load(context.assets, SpriteLoader.monsterPath(pack.types.first()))
                ?.let { canvas.drawBitmap(it, null, rect, pSprite) }

            // Groupe : nombre d'ennemis dans le coin
            if (pack.types.size > 1) {
                pText.textAlign = Paint.Align.RIGHT; pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 11f
                canvas.drawText(context.getString(R.string.roguelike_pack_size, pack.types.size), rect.right - 2f, rect.bottom - 3f, pText)
                pText.textAlign = Paint.Align.CENTER
            }
            // Poursuite : pastille rouge au-dessus
            if (pack.state == PackState.CHASING) {
                val r = tileSize * 0.16f
                canvas.drawCircle(rect.centerX(), rect.top + r * 0.2f, r, pChase)
                pText.color = 0xFFFFFFFF.toInt(); pText.textSize = r * 1.6f
                canvas.drawText("!", rect.centerX(), rect.top + r * 0.75f, pText)
            }
        }
    }

    // ── Joueur ──────────────────────────────────────────────────────────────────

    private fun drawPlayer(canvas: Canvas, g: RoguelikeGame) {
        val l = tileLeft(g.playerPos.x); val t = tileTop(g.playerPos.y)
        SpriteLoader.load(context.assets, g.heroSpritePath)
            ?.let { canvas.drawBitmap(it, null, RectF(l, t, l + tileSize, t + tileSize), pSprite) }
    }

    // ── Barre HP ────────────────────────────────────────────────────────────────

    private fun drawHpBar(canvas: Canvas, g: RoguelikeGame) {
        val p = g.hero; val barW = hpBarW; val barH = height.toFloat()
        canvas.drawRect(RectF(0f, 0f, barW, barH), pHpBg)
        val fillH = barH * p.hp.toFloat() / p.maxHp
        pHpBar.color = hpColor(p.hp, p.maxHp)
        canvas.drawRect(RectF(0f, barH - fillH, barW, barH), pHpBar)
        canvas.drawLine(barW, 0f, barW, barH, pHpSep)
    }

    // ── HUD ─────────────────────────────────────────────────────────────────────

    private fun drawHud(canvas: Canvas, g: RoguelikeGame) {
        val logSize  = sd * 17f; val hintSize = sd * 15f
        val lineH    = logSize * 1.4f
        val lines    = g.log.takeLast(3)
        val hudH     = lines.size * lineH + 16f
        val hudY     = height - hudH

        canvas.drawRect(RectF(hpBarW, hudY, width.toFloat(), height.toFloat()), pOverlay)
        pText.textSize = logSize; pText.textAlign = Paint.Align.LEFT; pText.color = 0xFFDDDDDD.toInt()
        for ((i, entry) in lines.withIndex())
            canvas.drawText(entry.resolve(context), hpBarW + 10f, hudY + 10f + (i + 1) * lineH - 4f, pText)
        pText.textAlign = Paint.Align.CENTER

        val hint = when {
            !g.isExploring   -> null
            g.isChased       -> R.string.roguelike_hint_chased
            g.onStairsTile() -> R.string.roguelike_descend_hint
            else             -> null
        }
        if (hint != null) {
            pText.color = if (hint == R.string.roguelike_hint_chased) 0xFFEF5350.toInt() else 0xFFFFD600.toInt()
            pText.textSize = hintSize
            canvas.drawText(context.getString(hint), (hpBarW + width) / 2f, hudY - 8f, pText)
        }
    }

    private fun hpColor(hp: Int, max: Int): Int {
        val r = hp.toFloat() / max
        return when { r > 0.5f -> 0xFF2E7D32.toInt(); r > 0.25f -> 0xFFE65100.toInt(); else -> 0xFFC62828.toInt() }
    }

    // ── Mort ────────────────────────────────────────────────────────────────────

    private fun drawDeathPanel(canvas: Canvas, g: RoguelikeGame) {
        val report = g.deathReport ?: return
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        val cx = width / 2f; val cy = height / 2f
        pText.textAlign = Paint.Align.CENTER
        pText.color = 0xFFEF5350.toInt(); pText.textSize = sd * 28f
        canvas.drawText(context.getString(R.string.roguelike_death_title), cx, cy - 60f * sd, pText)
        pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 15f
        canvas.drawText(context.getString(R.string.roguelike_death_summary, report.floor, report.goldLost), cx, cy - 20f * sd, pText)
        canvas.drawText(context.getString(R.string.roguelike_death_checkpoint, RoguelikeGame.CHECKPOINT), cx, cy + 8f * sd, pText)
        pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 14f
        canvas.drawText(context.getString(R.string.roguelike_combat_tap_continue), cx, cy + 60f * sd, pText)
    }

    // ── Icônes HUD ───────────────────────────────────────────────────────────────

    private val iconSizePx   get() = 44f * context.resources.displayMetrics.density
    private val iconMarginPx get() = 8f  * context.resources.displayMetrics.density

    private fun stairsIconRect(): RectF {
        val m = iconMarginPx; val s = iconSizePx
        return RectF(width - m - s, m, width - m, m + s)
    }

    private fun inventoryBtnRect(): RectF {
        val m = iconMarginPx; val s = iconSizePx
        // En bas à gauche, au-dessus du log
        return RectF(hpBarW + m, height - m * 2 - s * 2.3f, hpBarW + m + s, height - m * 2 - s * 1.3f)
    }

    private fun restBtnRect(): RectF {
        val inv = inventoryBtnRect()
        val m = iconMarginPx
        return RectF(inv.left, inv.top - m - inv.height(), inv.right, inv.top - m)
    }

    private fun drawHudIcons(canvas: Canvas, g: RoguelikeGame) {
        if (!g.isExploring) return
        pText.textAlign = Paint.Align.CENTER

        if (g.onStairsTile()) {
            val r = stairsIconRect()
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, pIconBg)
            pText.color = 0xFFFFD600.toInt(); pText.textSize = r.height() * 0.6f
            canvas.drawText("↓", r.centerX(), r.centerY() + r.height() * 0.22f, pText)
        }

        // Repos : grisé si poursuivi ou déjà en pleine forme
        val rest = restBtnRect()
        val canRest = g.canRest()
        canvas.drawCircle(rest.centerX(), rest.centerY(), rest.width() / 2f, if (canRest) pIconOn else pIconBg)
        pText.color = if (canRest) 0xFFFFFFFF.toInt() else 0xFF555555.toInt(); pText.textSize = rest.height() * 0.5f
        canvas.drawText("☾", rest.centerX(), rest.centerY() + rest.height() * 0.18f, pText)

        // Bouton inventaire (sac ⚔)
        val r = inventoryBtnRect()
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, if (inventoryOpen) pShopBuy else pIconBg)
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
        val labelFrac = 0.25f
        val gap       = paperdoll.width() * 0.03f
        val cellByW   = (paperdoll.width() - gap * 4f) / 3f
        val cellByH   = (paperdoll.height() - gap * 6f) / (4f * (1f + labelFrac))
        val cell      = minOf(cellByW, cellByH)
        val rowStep   = cell * (1f + labelFrac) + gap

        val cx    = paperdoll.centerX()
        val top   = paperdoll.top + gap * 2f
        val leftX = paperdoll.left  + gap
        val rightX= paperdoll.right - gap - cell

        fun slot(x: Float, row: Float): RectF {
            val y = top + row * rowStep
            return RectF(x, y, x + cell, y + cell)
        }
        fun center(row: Float): RectF {
            val x = cx - cell / 2f
            val y = top + row * rowStep
            return RectF(x, y, x + cell, y + cell)
        }

        val heroX1 = leftX + cell + gap
        val heroX2 = rightX - gap
        val heroH  = rowStep * 2f + cell * 0.1f
        val heroRect = RectF(heroX1, top + rowStep * 0.9f, heroX2, top + rowStep * 0.9f + heroH)

        val slots = mapOf(
            EquipSlot.HELMET  to center(0f),
            EquipSlot.WEAPON  to slot(leftX,  1f),
            EquipSlot.CHEST   to slot(rightX, 1f),
            EquipSlot.OFFHAND to slot(leftX,  2f),
            EquipSlot.AMULET  to slot(rightX, 2f),
            EquipSlot.BOOTS   to slot(leftX,  3f),
            EquipSlot.RING    to slot(rightX, 3f),
        )
        return heroRect to slots
    }

    private fun drawInventory(canvas: Canvas, g: RoguelikeGame) {
        val cr    = 12f * context.resources.displayMetrics.density
        val panel = invPanelRect()
        val gap   = panel.height() * 0.015f

        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        canvas.drawRoundRect(panel, cr, cr, pInvBg)
        canvas.drawRoundRect(panel, cr, cr, pInvBorder)

        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 14f
        canvas.drawText(context.getString(R.string.roguelike_equipment_title), panel.centerX(), panel.top + gap + sd * 14f, pText)

        val closeR = invCloseBtnRect(panel)
        canvas.drawRoundRect(closeR, cr * 0.4f, cr * 0.4f, pIgnoreBtn)
        pText.color = 0xFFAAAAAA.toInt(); pText.textSize = closeR.height() * 0.6f
        canvas.drawText("✕", closeR.centerX(), closeR.centerY() + closeR.height() * 0.22f, pText)

        val titleBottom = panel.top + gap * 2 + sd * 14f

        // Haut = paperdoll, milieu = caractéristiques, bas = détail de l'objet
        val paperdollH = panel.height() * 0.46f
        val statsH     = panel.height() * 0.24f
        val detailH    = panel.height() - paperdollH - statsH - gap * 4 - (titleBottom - panel.top)

        val paperdollRect = RectF(panel.left + gap, titleBottom + gap,
                                  panel.right - gap, titleBottom + gap + paperdollH)
        val statsRect     = RectF(panel.left + gap, paperdollRect.bottom + gap,
                                  panel.right - gap, paperdollRect.bottom + gap + statsH)
        val detailRect    = RectF(panel.left + gap, statsRect.bottom + gap,
                                  panel.right - gap, statsRect.bottom + gap + detailH)

        drawPaperdoll(canvas, g, paperdollRect, cr)
        drawStatsPanel(canvas, g, statsRect, cr, gap)
        drawDetailPanel(canvas, g, detailRect, cr, gap)
    }

    private fun drawPaperdoll(canvas: Canvas, g: RoguelikeGame, rect: RectF, cr: Float) {
        canvas.drawRoundRect(rect, cr, cr, pInvCard)
        val (heroR, slots) = paperdollSlotRects(rect)
        cachedSlotRects = slots

        val heroBmp = SpriteLoader.load(context.assets, g.heroSpritePath)
        if (heroBmp != null) {
            val bRatio = heroBmp.width.toFloat() / heroBmp.height
            val rRatio = heroR.width() / heroR.height()
            val (dw, dh) = if (bRatio > rRatio) heroR.width() to heroR.width() / bRatio
                           else heroR.height() * bRatio to heroR.height()
            val dx = heroR.left + (heroR.width() - dw) / 2f
            val dy = heroR.top  + (heroR.height() - dh) / 2f
            canvas.drawBitmap(heroBmp, null, RectF(dx, dy, dx + dw, dy + dh), pSprite)
        }

        for ((slot, slotR) in slots) {
            val isSelected = selectedEquipSlot == slot
            val equip = g.hero.equipped[slot]

            canvas.drawRoundRect(slotR, cr * 0.5f, cr * 0.5f, if (isSelected) pInvSlotSel else pInvSlotBg)
            canvas.drawRoundRect(slotR, cr * 0.5f, cr * 0.5f, if (isSelected) pInvBorderSel else pInvBorder)

            if (equip != null) {
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = equip.rarity.colorArgb and 0x33FFFFFF
                }
                canvas.drawRoundRect(slotR, cr * 0.5f, cr * 0.5f, haloPaint)
                drawEquipIcon(canvas, equip, slotR.inset(slotR.width() * 0.08f))
            } else {
                pText.textAlign = Paint.Align.CENTER
                pText.color = 0xFF2A3E52.toInt(); pText.textSize = slotR.height() * 0.4f
                val slotIcon = when (slot) {
                    EquipSlot.WEAPON  -> "⚔"; EquipSlot.CHEST   -> "◻"; EquipSlot.HELMET  -> "◯"
                    EquipSlot.BOOTS   -> "∪"; EquipSlot.OFFHAND -> "⊡"; EquipSlot.AMULET  -> "◇"
                    EquipSlot.RING    -> "○"
                }
                canvas.drawText(slotIcon, slotR.centerX(), slotR.centerY() + slotR.height() * 0.15f, pText)
            }

            pText.textAlign = Paint.Align.CENTER
            pText.color = if (isSelected) 0xFF42A5F5.toInt() else 0xFF445566.toInt()
            pText.textSize = slotR.height() * 0.20f
            canvas.drawText(context.getString(slot.labelRes), slotR.centerX(), slotR.bottom + slotR.height() * 0.22f, pText)
        }
    }

    private fun drawStatsPanel(canvas: Canvas, g: RoguelikeGame, rect: RectF, cr: Float, gap: Float) {
        canvas.drawRoundRect(rect, cr, cr, pInvCard)
        val p = g.hero

        pText.textAlign = Paint.Align.LEFT
        pText.color = 0xFF888888.toInt(); pText.textSize = sd * 11f
        canvas.drawText(context.getString(R.string.roguelike_stats_title), rect.left + gap * 2, rect.top + gap + sd * 11f, pText)

        val colW  = (rect.width() - gap * 4) / 3f
        val lineH = (rect.height() - gap * 3 - sd * 11f) / 4f
        val y0    = rect.top + gap * 2 + sd * 11f

        fun stat(label: String, value: String, col: Int, row: Int) {
            val x = rect.left + gap * 2 + col * colW
            val y = y0 + row * lineH + lineH * 0.7f
            pText.color = 0xFF556677.toInt(); pText.textSize = sd * 11f
            canvas.drawText(label, x, y, pText)
            pText.color = 0xFFDDEEFF.toInt(); pText.textSize = sd * 13f
            canvas.drawText(value, x + colW * 0.45f, y, pText)
        }

        // Les six caractéristiques D&D
        val attrs = listOf(StatType.STR, StatType.DEX, StatType.CON, StatType.INT, StatType.WIS, StatType.CHA)
        attrs.forEachIndexed { i, a -> stat(context.getString(a.labelRes), "${p.attribute(a)}", i % 3, i / 3) }

        stat(context.getString(R.string.roguelike_stat_hp), context.getString(R.string.roguelike_combat_hp, p.hp, p.maxHp), 0, 2)
        stat(context.getString(StatType.ARMOR.labelRes), "${p.armor}", 1, 2)
        stat(context.getString(R.string.roguelike_stat_crit), context.getString(R.string.roguelike_percent, (p.critChance * 100).roundToInt()), 2, 2)
        stat(context.getString(R.string.roguelike_stat_sword), context.getString(R.string.roguelike_range, p.swordMin, p.swordMax), 0, 3)
        stat(context.getString(R.string.roguelike_combat_potion), "${p.potions}", 1, 3)
        stat(context.getString(R.string.roguelike_gold), "${p.gold}", 2, 3)
    }

    private fun drawDetailPanel(canvas: Canvas, g: RoguelikeGame, rect: RectF, cr: Float, gap: Float) {
        canvas.drawRoundRect(rect, cr, cr, pInvCard)
        val slot  = selectedEquipSlot
        val equip = if (slot != null) g.hero.equipped[slot] else null

        pText.textAlign = Paint.Align.CENTER
        if (equip == null) {
            pText.color = if (slot == null) 0xFF334455.toInt() else 0xFF556677.toInt()
            pText.textSize = sd * 12f
            canvas.drawText(
                if (slot == null) context.getString(R.string.roguelike_select_slot_hint) else context.getString(R.string.roguelike_empty_slot),
                rect.centerX(), rect.centerY() + sd * 6f, pText
            )
            return
        }

        val iconSize = rect.height() * 0.55f
        val iconR = RectF(rect.left + gap * 2, rect.centerY() - iconSize / 2,
                          rect.left + gap * 2 + iconSize, rect.centerY() + iconSize / 2)
        canvas.drawRoundRect(iconR, cr * 0.4f, cr * 0.4f, pInvSlotBg)
        drawEquipIcon(canvas, equip, iconR)

        val textX = iconR.right + gap * 2

        pText.textAlign = Paint.Align.LEFT
        pText.color = equip.rarity.colorArgb; pText.textSize = sd * 13f
        canvas.drawText(LootSystem.displayName(context, equip), textX, rect.top + gap + sd * 13f, pText)

        pText.color = 0xFF556677.toInt(); pText.textSize = sd * 11f
        canvas.drawText(context.getString(R.string.roguelike_item_subtitle, context.getString(equip.material.labelRes), context.getString(equip.slot.labelRes)),
            textX, rect.top + gap * 2 + sd * 24f, pText)

        var statY = rect.top + gap * 3 + sd * 35f
        val statLineH = sd * 13f + gap * 0.4f
        for (stat in equip.stats) {
            pText.color = 0xFF88FFAA.toInt(); pText.textSize = sd * 13f
            canvas.drawText(stat.display(context), textX, statY, pText)
            statY += statLineH
        }
    }

    private fun RectF.inset(amount: Float) = RectF(left + amount, top + amount, right - amount, bottom - amount)

    // ── Marchand (sur l'escalier) ────────────────────────────────────────────────

    private fun shopPanelRect() = RectF(width * 0.06f, height * 0.18f, width * 0.94f, height * 0.82f)

    private fun shopPotionRect(panel: RectF): RectF {
        val gap = panel.height() * 0.04f
        val top = panel.top + panel.height() * 0.24f
        return RectF(panel.left + gap, top, panel.right - gap, top + panel.height() * 0.24f)
    }

    private fun shopDescendRect(panel: RectF): RectF {
        val btnH = panel.height() * 0.14f; val gap = panel.height() * 0.04f
        return RectF(panel.left + gap, panel.bottom - btnH - gap, panel.centerX() - gap / 2, panel.bottom - gap)
    }

    private fun shopStayRect(panel: RectF): RectF {
        val btnH = panel.height() * 0.14f; val gap = panel.height() * 0.04f
        return RectF(panel.centerX() + gap / 2, panel.bottom - btnH - gap, panel.right - gap, panel.bottom - gap)
    }

    private fun drawShop(canvas: Canvas, g: RoguelikeGame) {
        val panel = shopPanelRect(); val cr = 16f * context.resources.displayMetrics.density
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        canvas.drawRoundRect(panel, cr, cr, pShopBg)
        pText.textAlign = Paint.Align.CENTER
        pText.color = 0xFFFFD600.toInt(); pText.textSize = sd * 20f
        canvas.drawText(context.getString(R.string.roguelike_shop_title), panel.centerX(), panel.top + panel.height() * 0.10f, pText)
        pText.color = 0xFFAAAAAA.toInt(); pText.textSize = sd * 13f
        canvas.drawText(context.getString(R.string.roguelike_shop_gold_available, g.hero.gold), panel.centerX(), panel.top + panel.height() * 0.18f, pText)

        val r = shopPotionRect(panel)
        val price = RoguelikeGame.POTION_PRICE
        val canBuy = g.hero.gold >= price && g.hero.potions < Hero.MAX_POTIONS
        canvas.drawRoundRect(r, cr * 0.6f, cr * 0.6f, pShopCard)
        val icon = RectF(r.left + r.height() * 0.15f, r.top + r.height() * 0.15f, r.left + r.height() * 0.85f, r.bottom - r.height() * 0.15f)
        drawSheetCell(canvas, ItemType.POTION.spriteRow, ItemType.POTION.spriteCol, icon)
        pText.textAlign = Paint.Align.LEFT
        pText.color = 0xFFEEEEEE.toInt(); pText.textSize = sd * 15f
        canvas.drawText(context.getString(R.string.roguelike_combat_potion), icon.right + r.height() * 0.2f, r.centerY() - sd * 4f, pText)
        pText.color = 0xFF888888.toInt(); pText.textSize = sd * 12f
        canvas.drawText(context.getString(R.string.roguelike_shop_potion_owned, g.hero.potions, Hero.MAX_POTIONS), icon.right + r.height() * 0.2f, r.centerY() + sd * 12f, pText)
        val badgeW = r.height() * 1.1f
        val badgeR = RectF(r.right - badgeW - r.height() * 0.1f, r.top + r.height() * 0.2f, r.right - r.height() * 0.1f, r.bottom - r.height() * 0.2f)
        canvas.drawRoundRect(badgeR, cr * 0.4f, cr * 0.4f, if (canBuy) pShopBuy else pShopSold)
        pText.textAlign = Paint.Align.CENTER
        pText.color = if (canBuy) 0xFFFFFFFF.toInt() else 0xFF777777.toInt(); pText.textSize = sd * 13f
        canvas.drawText(context.getString(R.string.roguelike_shop_price, price), badgeR.centerX(), badgeR.centerY() + sd * 5f, pText)

        val dRect = shopDescendRect(panel)
        canvas.drawRoundRect(dRect, cr * 0.6f, cr * 0.6f, pShopDescend)
        pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 16f
        canvas.drawText(context.getString(R.string.roguelike_shop_descend), dRect.centerX(), dRect.centerY() + sd * 6f, pText)
        val sRect = shopStayRect(panel)
        canvas.drawRoundRect(sRect, cr * 0.6f, cr * 0.6f, pShopSold)
        canvas.drawText(context.getString(R.string.roguelike_shop_stay), sRect.centerX(), sRect.centerY() + sd * 6f, pText)
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
        val current = g.hero.equipped[equip.slot]
        val cr      = 14f * context.resources.displayMetrics.density
        val panel   = lootPanelRect()
        val gap     = panel.height() * 0.025f

        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        canvas.drawRoundRect(panel, cr, cr, pLootBg)
        pLootBorder.color = equip.rarity.colorArgb
        canvas.drawRoundRect(panel, cr, cr, pLootBorder)

        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 13f
        canvas.drawText(context.getString(R.string.roguelike_loot_found_title, context.getString(equip.slot.labelRes)), panel.centerX(), panel.top + gap + sd * 13f, pText)

        val titleBottom = panel.top + gap * 2 + sd * 13f

        val sepX    = panel.centerX()
        val btnTop  = lootEquipBtnRect(panel).top - gap
        val dividerPaint = Paint().apply { color = 0xFF333344.toInt(); strokeWidth = 1f }
        canvas.drawLine(sepX, titleBottom, sepX, btnTop, dividerPaint)

        // Colonnes : NOUVEAU (gauche) | ÉQUIPÉ (droite)
        val colL = RectF(panel.left,  titleBottom, sepX - gap * 0.5f, btnTop)
        val colR = RectF(sepX + gap * 0.5f, titleBottom, panel.right, btnTop)

        drawItemColumn(canvas, equip, colL, isNew = true, cr, gap)
        if (current != null)
            drawItemColumn(canvas, current, colR, isNew = false, cr, gap)
        else {
            pText.textAlign = Paint.Align.CENTER; pText.color = 0xFF555555.toInt(); pText.textSize = sd * 13f
            canvas.drawText(context.getString(R.string.roguelike_loot_nothing_equipped), colR.centerX(), colR.centerY(), pText)
        }

        if (current != null) drawStatDeltas(canvas, equip, current, colL, colR, gap)

        val equipR  = lootEquipBtnRect(panel)
        val ignoreR = lootIgnoreBtnRect(panel)
        canvas.drawRoundRect(equipR,  cr * 0.6f, cr * 0.6f, pEquipBtn)
        canvas.drawRoundRect(ignoreR, cr * 0.6f, cr * 0.6f, pIgnoreBtn)
        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 15f
        canvas.drawText(context.getString(R.string.roguelike_loot_equip_btn),  equipR.centerX(),  equipR.centerY()  + sd * 6f, pText)
        canvas.drawText(context.getString(R.string.roguelike_loot_ignore_btn), ignoreR.centerX(), ignoreR.centerY() + sd * 6f, pText)
    }

    /** Dessine une colonne item (icône + nom rareté + stats). */
    private fun drawItemColumn(canvas: Canvas, equip: Equipment, col: RectF, isNew: Boolean, cr: Float, gap: Float) {
        val iconSize = minOf(col.width() * 0.45f, col.height() * 0.25f)
        val iconRect = RectF(col.centerX() - iconSize / 2, col.top + gap,
                             col.centerX() + iconSize / 2, col.top + gap + iconSize)

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = equip.rarity.colorArgb and 0x44FFFFFF }
        canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconSize * 0.6f, haloPaint)
        canvas.drawRoundRect(iconRect, cr * 0.4f, cr * 0.4f, pLootCardBg)
        drawEquipIcon(canvas, equip, iconRect)

        var y = iconRect.bottom + gap

        pText.textAlign = Paint.Align.CENTER
        pText.color = if (isNew) 0xFFFFD600.toInt() else 0xFF888888.toInt()
        pText.textSize = sd * 11f
        canvas.drawText(if (isNew) context.getString(R.string.roguelike_loot_new_badge) else context.getString(R.string.roguelike_loot_equipped_badge), col.centerX(), y + sd * 11f, pText)
        y += sd * 11f + gap * 0.5f

        pText.color = equip.rarity.colorArgb; pText.textSize = sd * 13f
        canvas.drawText(LootSystem.displayName(context, equip), col.centerX(), y + sd * 13f, pText)
        y += sd * 13f + gap * 0.6f

        for (stat in equip.stats) {
            pText.color = if (isNew) 0xFFDDFFDD.toInt() else 0xFFAAAAAA.toInt()
            pText.textSize = sd * 12f
            canvas.drawText(stat.display(context), col.centerX(), y + sd * 12f, pText)
            y += sd * 12f + gap * 0.4f
        }
    }

    /** Sur chaque stat du nouvel objet, affiche l'écart (▲/▼) avec l'objet porté. */
    private fun drawStatDeltas(canvas: Canvas, newItem: Equipment, current: Equipment, colL: RectF, colR: RectF, gap: Float) {
        fun sumStat(eq: Equipment, type: StatType) =
            eq.stats.filter { it.type == type }.sumOf { it.value.toDouble() }.toFloat()

        val sepX  = (colL.right + colR.left) / 2f
        val iconSize = minOf(colL.width() * 0.45f, colL.height() * 0.25f)
        var y = colL.top + gap + iconSize + gap + sd * 11f + gap * 0.5f + sd * 13f + gap * 0.6f

        val deltaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }

        for (stat in newItem.stats) {
            val delta = sumStat(newItem, stat.type) - sumStat(current, stat.type)
            if (delta != 0f) {
                val sign  = if (delta > 0f) "▲" else "▼"
                val value = if (stat.type.isPercent)
                    context.getString(R.string.roguelike_percent, (abs(delta) * 100).roundToInt())
                else
                    "${abs(delta).roundToInt()}"
                deltaPaint.color = if (delta > 0f) 0xFF66FF66.toInt() else 0xFFFF6666.toInt()
                deltaPaint.textSize = sd * 11f
                canvas.drawText("$sign$value", sepX, y + sd * 12f, deltaPaint)
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
            // La flèche montre la direction réellement jouée (8 secteurs), pas le doigt exact
            val (sx, sy) = sectorDir(dx, dy)
            val angle = atan2(sy.toFloat(), sx.toFloat())
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
        val g = game ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y
                touchCurrX = event.x; touchCurrY = event.y
                touching = true; invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchCurrX = event.x; touchCurrY = event.y
                val dx = touchCurrX - touchDownX; val dy = touchCurrY - touchDownY
                if (sqrt(dx * dx + dy * dy) >= swipeMinPx && canWalk(g)) {
                    val dir = sectorDir(dx, dy)
                    if (dir != holdDir) {
                        // Nouvelle direction : un pas tout de suite, puis la répétition
                        holdDir = dir
                        holdSuspended = false
                        removeCallbacks(holdRepeat)
                        stepHeld()
                        postDelayed(holdRepeat, FIRST_REPEAT_MS)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - touchDownX; val dy = event.y - touchDownY
                val dist = sqrt(dx * dx + dy * dy)
                val tap = dist < swipeMinPx && !movedThisTouch
                val walked = movedThisTouch
                stopHold()
                movedThisTouch = false

                when {
                    g.deathReport != null -> if (tap) onDismissDeath?.invoke()

                    g.pendingEquipDrop != null -> if (tap) {
                        val panel = lootPanelRect()
                        when {
                            lootEquipBtnRect(panel).contains(touchDownX, touchDownY)  -> onEquipItem?.invoke()
                            lootIgnoreBtnRect(panel).contains(touchDownX, touchDownY) -> onIgnoreDrop?.invoke()
                        }
                    }

                    g.merchantOpen -> if (tap) {
                        val panel = shopPanelRect()
                        when {
                            shopPotionRect(panel).contains(touchDownX, touchDownY)  -> onBuyPotion?.invoke()
                            shopDescendRect(panel).contains(touchDownX, touchDownY) -> onDescend?.invoke()
                            shopStayRect(panel).contains(touchDownX, touchDownY)    -> onCloseMerchant?.invoke()
                        }
                    }

                    inventoryOpen -> if (tap) {
                        val panel = invPanelRect()
                        if (invCloseBtnRect(panel).contains(touchDownX, touchDownY)) {
                            inventoryOpen = false; selectedEquipSlot = null
                        } else {
                            val tapped = cachedSlotRects.entries.firstOrNull { (_, r) -> r.contains(touchDownX, touchDownY) }
                            selectedEquipSlot = if (tapped?.key == selectedEquipSlot) null else tapped?.key
                        }
                    }

                    !g.isExploring -> {}

                    tap -> when {
                        inventoryBtnRect().contains(touchDownX, touchDownY) -> {
                            inventoryOpen = true; selectedEquipSlot = null
                        }
                        restBtnRect().contains(touchDownX, touchDownY) -> onRest?.invoke()
                        stairsIconRect().contains(touchDownX, touchDownY) && g.onStairsTile() -> onOpenMerchant?.invoke()
                    }

                    // Swipe trop rapide pour que ACTION_MOVE ait déclenché un pas
                    !walked && dist >= swipeMinPx ->
                        slide(g, sectorDir(dx, dy), dx, dy)?.let { (mx, my) -> onMove?.invoke(mx, my) }
                }
                touching = false; invalidate()
            }
        }
        return true
    }

    // ── Déplacement maintenu ─────────────────────────────────────────────────────

    private companion object {
        const val FIRST_REPEAT_MS = 220L
        const val REPEAT_MS       = 140L
        /** Les 8 directions, dans l'ordre des secteurs de 45° (0 = droite, y vers le bas). */
        val DIRS8 = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    }

    private var holdDir: Pair<Int, Int>? = null
    private var movedThisTouch = false
    /** Mis en pause quand un nouveau monstre nous repère : on ne fonce pas dedans sans le vouloir. */
    private var holdSuspended = false

    private val holdRepeat = object : Runnable {
        override fun run() {
            if (holdDir == null) return
            stepHeld()
            postDelayed(this, REPEAT_MS)
        }
    }

    private fun canWalk(g: RoguelikeGame) =
        g.isExploring && !inventoryOpen

    private fun sectorDir(dx: Float, dy: Float): Pair<Int, Int> {
        val sector = ((atan2(dy, dx) / (PI / 4)).roundToInt() + 8) % 8
        return DIRS8[sector]
    }

    /**
     * Pas voulu, ou glissement le long du mur : une diagonale bloquée devient le pas droit
     * possible (celui que le doigt indique le plus), pratique dans les virages de couloir.
     */
    private fun slide(g: RoguelikeGame, dir: Pair<Int, Int>, dx: Float, dy: Float): Pair<Int, Int>? {
        val lv = g.level; val p = g.playerPos
        val (mx, my) = dir
        if (lv.canStep(p, mx, my)) return dir
        if (mx == 0 || my == 0) return null
        val axes = if (abs(dx) >= abs(dy)) listOf(mx to 0, 0 to my) else listOf(0 to my, mx to 0)
        return axes.firstOrNull { (ax, ay) -> lv.canStep(p, ax, ay) }
    }

    private fun stepHeld() {
        val g = game ?: return
        val dir = holdDir ?: return
        if (!canWalk(g) || holdSuspended) return
        val step = slide(g, dir, touchCurrX - touchDownX, touchCurrY - touchDownY) ?: return
        val chasingBefore = g.level.packs.count { it.alive && it.state == PackState.CHASING }
        onMove?.invoke(step.first, step.second)
        movedThisTouch = true
        val chasingAfter = g.level.packs.count { it.alive && it.state == PackState.CHASING }
        if (chasingAfter > chasingBefore || !g.isExploring) holdSuspended = true
    }

    private fun stopHold() {
        holdDir = null
        holdSuspended = false
        removeCallbacks(holdRepeat)
    }
}
