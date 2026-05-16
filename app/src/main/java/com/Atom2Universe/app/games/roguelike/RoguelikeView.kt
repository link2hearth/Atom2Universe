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
    var onMove: ((Int, Int) -> Unit)? = null
    var onUseItem: (() -> Unit)? = null
    var onDescend: (() -> Unit)? = null
    var onBuyShopItem: ((ShopItem) -> Unit)? = null
    var onConfirmDescend: (() -> Unit)? = null

    private var tileSize = 40f
    private val hpBarW get() = context.resources.displayMetrics.density * 6f

    private var camX = 0f
    private var camY = 0f

    // ── Paints ──────────────────────────────────────────────────────────────────
    private val pWallFallback  = Paint().apply { color = 0xFF1A1A1A.toInt(); isAntiAlias = false }
    private val pFloorFallback = Paint().apply { color = 0xFF3A2E24.toInt(); isAntiAlias = false }
    private val pStairs        = Paint().apply { color = 0xFF4A3800.toInt(); isAntiAlias = false }

    private val pSprite    = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pSpriteDim = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 70 }
    private val pFog       = Paint().apply { color = 0xCC000000.toInt(); isAntiAlias = false }

    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val pHpBar      = Paint().apply { isAntiAlias = false }
    private val pBarrier    = Paint().apply { color = 0xFF1E88E5.toInt(); isAntiAlias = false }
    private val pHpBg       = Paint().apply { color = 0xFF111111.toInt(); isAntiAlias = false }
    private val pHpSep      = Paint().apply { color = 0xFF555555.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val pShopBg     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0101820.toInt() }
    private val pShopCard   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2A38.toInt() }
    private val pShopBuy    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1565C0.toInt() }
    private val pShopSold   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() }
    private val pShopDescend = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt() }
    private val pOverlay = Paint().apply { color = 0xCC000000.toInt() }
    private val pMobHpBg = Paint().apply { color = 0xFF2E2E2E.toInt() }
    private val pMobHp   = Paint().apply { color = 0xFF4CAF50.toInt() }

    // ── Swipe ───────────────────────────────────────────────────────────────────
    private val swipeMinPx get() = 16f * context.resources.displayMetrics.density

    private val pIconBg    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA000000.toInt() }
    private val pSwipeDot  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
    private val pSwipeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    // ── Touch state ─────────────────────────────────────────────────────────────
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchCurrX = 0f
    private var touchCurrY = 0f
    private var touching = false

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        tileSize = w / 11f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        SpriteLoader.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        updateCamera(g)
        drawMap(canvas, g)
        drawItems(canvas, g)
        drawMonsters(canvas, g)
        drawPlayer(canvas, g)
        drawHpBar(canvas, g)
        drawBarrierBar(canvas, g)
        drawHud(canvas, g)
        drawHudIcons(canvas, g)
        if (g.shopOpen) drawShop(canvas, g)
        if (g.phase != GamePhase.PLAYING) drawEndScreen(canvas, g)
        if (!g.shopOpen) drawSwipeZone(canvas)
    }

    // ── Camera ──────────────────────────────────────────────────────────────────

    private fun updateCamera(g: RoguelikeGame) {
        val barW = hpBarW
        val availW = width - barW
        val px = g.player.pos.x * tileSize
        val py = g.player.pos.y * tileSize
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

    // ── Sprite helpers ──────────────────────────────────────────────────────────

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

    // ── Map tiles ───────────────────────────────────────────────────────────────

    private fun drawMap(canvas: Canvas, g: RoguelikeGame) {
        val lv = g.level
        val theme = lv.theme
        val floorSprites = SpriteLoader.listDir(context.assets, theme.floorDir)
        val wallSprites  = SpriteLoader.listDir(context.assets, theme.wallDir)

        for (ty in 0 until lv.h) {
            for (tx in 0 until lv.w) {
                if (!isOnScreen(tx, ty)) continue
                val vis = lv.visible[ty][tx]
                val exp = lv.explored[ty][tx]
                if (!exp) continue

                val l = tileLeft(tx); val t = tileTop(ty)
                val rect = RectF(l, t, l + tileSize, t + tileSize)
                val tile = lv.tiles[ty][tx]

                when (tile) {
                    TileType.WALL -> {
                        if (wallSprites.isNotEmpty()) {
                            val path = wallSprites[variantIndex(tx, ty, wallSprites.size)]
                            drawSprite(canvas, path, rect, !vis)
                        } else {
                            canvas.drawRect(rect, pWallFallback)
                        }
                    }
                    TileType.FLOOR, TileType.STAIRS_DOWN -> {
                        if (floorSprites.isNotEmpty()) {
                            val path = floorSprites[variantIndex(tx, ty, floorSprites.size)]
                            drawSprite(canvas, path, rect, !vis)
                        } else {
                            canvas.drawRect(rect, if (vis) pFloorFallback else pWallFallback)
                        }
                        if (tile == TileType.STAIRS_DOWN) {
                            // Escaliers : superposition d'une tuile de sol colorée + symbole
                            canvas.drawRect(rect, Paint().apply {
                                color = 0x664A3800.toInt()
                            })
                            pText.color = if (vis) 0xFFFFD600.toInt() else 0xFF665500.toInt()
                            pText.textSize = tileSize * 0.7f
                            canvas.drawText(">", l + tileSize / 2f, t + tileSize * 0.75f, pText)
                        }
                    }
                }

                if (!vis) {
                    pFog.alpha = 100
                    canvas.drawRect(rect, pFog)
                }
            }
        }
    }

    // ── Items ───────────────────────────────────────────────────────────────────

    private fun drawItems(canvas: Canvas, g: RoguelikeGame) {
        val lv = g.level
        for (item in lv.items) {
            val tx = item.pos.x; val ty = item.pos.y
            if (!isOnScreen(tx, ty) || !lv.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            pText.color = item.type.colorArgb
            pText.textSize = tileSize * 0.65f
            canvas.drawText(item.type.symbol.toString(), l + tileSize / 2f, t + tileSize * 0.75f, pText)
        }
    }

    // ── Monsters ────────────────────────────────────────────────────────────────

    private fun drawMonsters(canvas: Canvas, g: RoguelikeGame) {
        val lv = g.level
        val barH = tileSize * 0.15f
        for (m in lv.monsters) {
            if (!m.isAlive) continue
            val tx = m.pos.x; val ty = m.pos.y
            if (!isOnScreen(tx, ty) || !lv.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val rect = RectF(l, t, l + tileSize, t + tileSize)

            val spritePath = monsterSpritePath(m.type)
            val bmp = SpriteLoader.load(context.assets, spritePath)
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, rect, pSprite)
            } else {
                pText.color = m.type.colorArgb
                pText.textSize = tileSize * 0.7f
                canvas.drawText(m.type.symbol.toString(), l + tileSize / 2f, t + tileSize * 0.82f, pText)
            }

            val barW = tileSize - 2f
            canvas.drawRect(RectF(l + 1f, t, l + 1f + barW, t + barH), pMobHpBg)
            canvas.drawRect(RectF(l + 1f, t, l + 1f + barW * m.hp / m.maxHp, t + barH), pMobHp)
        }
    }

    // ── Player ──────────────────────────────────────────────────────────────────

    private fun drawPlayer(canvas: Canvas, g: RoguelikeGame) {
        val tx = g.player.pos.x; val ty = g.player.pos.y
        val l = tileLeft(tx); val t = tileTop(ty)
        val rect = RectF(l, t, l + tileSize, t + tileSize)

        val bmp = SpriteLoader.load(context.assets, g.heroSpritePath)
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rect, pSprite)
        } else {
            pText.color = 0xFF00E5FF.toInt()
            pText.textSize = tileSize * 0.8f
            canvas.drawText("@", l + tileSize / 2f, t + tileSize * 0.82f, pText)
        }
    }

    // ── Fine bande HP verticale (bord gauche) ────────────────────────────────────

    private fun drawHpBar(canvas: Canvas, g: RoguelikeGame) {
        val p = g.player
        val barW = hpBarW
        val barH = height.toFloat()

        canvas.drawRect(RectF(0f, 0f, barW, barH), pHpBg)

        val fillH = barH * p.hp.toFloat() / p.maxHp
        pHpBar.color = hpColor(p.hp, p.maxHp)
        canvas.drawRect(RectF(0f, barH - fillH, barW, barH), pHpBar)

        canvas.drawLine(barW, 0f, barW, barH, pHpSep)
    }

    // ── Barre de barrière (bord droit) ──────────────────────────────────────────

    private fun drawBarrierBar(canvas: Canvas, g: RoguelikeGame) {
        val p = g.player
        if (!p.barrierUnlocked) return
        val barW = hpBarW
        val barH = height.toFloat()
        val x0 = width - barW

        canvas.drawRect(RectF(x0, 0f, width.toFloat(), barH), pHpBg)

        if (p.maxBarrier > 0 && p.barrier > 0) {
            val fillH = barH * p.barrier.toFloat() / p.maxBarrier
            canvas.drawRect(RectF(x0, barH - fillH, width.toFloat(), barH), pBarrier)
        }

        canvas.drawLine(x0, 0f, x0, barH, pHpSep)
    }

    // ── HUD overlay (log + hint escaliers) ──────────────────────────────────────

    private fun drawHud(canvas: Canvas, g: RoguelikeGame) {
        val sd = context.resources.displayMetrics.scaledDensity
        val logTextSize  = sd * 17f
        val hintTextSize = sd * 15f

        val lineH = logTextSize * 1.4f
        val lines = g.log.takeLast(3)
        val hudH = lines.size * lineH + 16f
        val hudY = height - hudH

        canvas.drawRect(RectF(hpBarW, hudY, width.toFloat(), height.toFloat()), pOverlay)

        pText.textSize = logTextSize
        pText.textAlign = Paint.Align.LEFT
        pText.color = 0xFFDDDDDD.toInt()
        for ((i, line) in lines.withIndex()) {
            canvas.drawText(line, hpBarW + 10f, hudY + 10f + (i + 1) * lineH - 4f, pText)
        }
        pText.textAlign = Paint.Align.CENTER

        if (g.onStairsTile()) {
            pText.color = 0xFFFFD600.toInt()
            pText.textSize = hintTextSize
            canvas.drawText("[ > ] Tap Descend", (hpBarW + width) / 2f, hudY - 8f, pText)
        }
    }

    private fun hpColor(hp: Int, max: Int): Int {
        val r = hp.toFloat() / max
        return when {
            r > 0.5f  -> 0xFF2E7D32.toInt()
            r > 0.25f -> 0xFFE65100.toInt()
            else      -> 0xFFC62828.toInt()
        }
    }

    // ── End screen ──────────────────────────────────────────────────────────────

    private fun drawEndScreen(canvas: Canvas, g: RoguelikeGame) {
        val sd = context.resources.displayMetrics.scaledDensity
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        pText.textAlign = Paint.Align.CENTER
        val cx = width / 2f; val cy = height / 2f
        if (g.phase == GamePhase.VICTORY) {
            pText.color = 0xFFFFD600.toInt(); pText.textSize = sd * 28f
            canvas.drawText("VICTORY!", cx, cy - 50f, pText)
            pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 16f
            canvas.drawText("Gold collected: ${g.player.gold}", cx, cy, pText)
        } else {
            pText.color = 0xFFEF5350.toInt(); pText.textSize = sd * 28f
            canvas.drawText("YOU DIED", cx, cy - 50f, pText)
            pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 16f
            canvas.drawText("Floor ${g.player.floor}  —  ${g.player.gold} gold", cx, cy, pText)
        }
        pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 14f
        canvas.drawText("Tap to restart", cx, cy + 50f, pText)
    }

    // ── HUD icons (heart / stairs) ───────────────────────────────────────────────

    private val iconSizePx  get() = 44f * context.resources.displayMetrics.density
    private val iconMarginPx get() = 8f * context.resources.displayMetrics.density

    private fun heartRect(): RectF {
        val m = iconMarginPx; val s = iconSizePx
        return RectF(hpBarW + m, m, hpBarW + m + s, m + s)
    }

    private fun stairsIconRect(): RectF {
        val m = iconMarginPx; val s = iconSizePx
        return RectF(width - m - s, m, width - m, m + s)
    }

    private fun drawHudIcons(canvas: Canvas, g: RoguelikeGame) {
        if (g.phase != GamePhase.PLAYING) return
        val sd = context.resources.displayMetrics.scaledDensity
        pText.textAlign = Paint.Align.CENTER

        // ── Cœur (utiliser objet) ──
        val inv = g.player.inventory
        if (inv.isNotEmpty()) {
            val r = heartRect()
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, pIconBg)
            pText.color = 0xFFEF5350.toInt()
            pText.textSize = r.height() * 0.55f
            canvas.drawText("♥", r.centerX(), r.centerY() + r.height() * 0.2f, pText)
            // Badge : nombre d'objets
            pText.color = 0xFFFFFFFF.toInt()
            pText.textSize = sd * 11f
            pText.textAlign = Paint.Align.RIGHT
            canvas.drawText("${inv.size}", r.right, r.top + sd * 13f, pText)
            pText.textAlign = Paint.Align.CENTER
        }

        // ── Flèche escaliers ──
        if (g.onStairsTile()) {
            val r = stairsIconRect()
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, pIconBg)
            pText.color = 0xFFFFD600.toInt()
            pText.textSize = r.height() * 0.6f
            canvas.drawText("↓", r.centerX(), r.centerY() + r.height() * 0.22f, pText)
        }
    }

    // ── Shop overlay ────────────────────────────────────────────────────────────

    private fun shopPanelRect() = RectF(
        width * 0.06f, height * 0.08f, width * 0.94f, height * 0.92f
    )

    private fun shopItemRect(index: Int, panel: RectF): RectF {
        val topPad = panel.height() * 0.12f   // espace titre
        val btnH   = panel.height() * 0.10f   // hauteur bouton Descend
        val gap    = panel.height() * 0.02f
        val cardH  = (panel.height() - topPad - btnH - gap * 4) / 3f
        val top    = panel.top + topPad + index * (cardH + gap)
        return RectF(panel.left + gap, top, panel.right - gap, top + cardH)
    }

    private fun shopDescendRect(panel: RectF): RectF {
        val btnH = panel.height() * 0.10f
        val gap  = panel.height() * 0.02f
        return RectF(panel.left + gap * 4, panel.bottom - btnH - gap, panel.right - gap * 4, panel.bottom - gap)
    }

    private fun drawShop(canvas: Canvas, g: RoguelikeGame) {
        val sd    = context.resources.displayMetrics.scaledDensity
        val panel = shopPanelRect()
        val cr    = 16f * context.resources.displayMetrics.density

        // Fond panneau
        canvas.drawRoundRect(panel, cr, cr, pShopBg)

        // Titre
        pText.textAlign = Paint.Align.CENTER
        pText.color = 0xFFFFD600.toInt()
        pText.textSize = sd * 20f
        canvas.drawText("SHOP", panel.centerX(), panel.top + panel.height() * 0.08f, pText)

        // Or disponible
        pText.color = 0xFFAAAAAA.toInt()
        pText.textSize = sd * 13f
        canvas.drawText("${g.player.gold} g disponibles", panel.centerX(), panel.top + panel.height() * 0.13f, pText)

        // 3 items
        val items = listOf(
            Triple(ShopItem.POTION,  "♥  Vie max +10",      "Max HP +10 + soins complets"),
            Triple(ShopItem.ATK_UP, "⚔  Cristal de force", "ATK +1 permanent"),
            Triple(ShopItem.BARRIER,"◈  Bouclier",         "Recharge la barrière")
        )
        items.forEachIndexed { i, (item, name, desc) ->
            val r     = shopItemRect(i, panel)
            val bought = item in g.shopBought
            val canBuy = !bought && g.player.gold >= item.cost

            canvas.drawRoundRect(r, cr * 0.6f, cr * 0.6f, if (bought) pShopSold else pShopCard)

            pText.textAlign = Paint.Align.LEFT
            pText.color = if (bought) 0xFF666666.toInt() else 0xFFEEEEEE.toInt()
            pText.textSize = sd * 15f
            canvas.drawText(name, r.left + r.height() * 0.2f, r.centerY() - sd * 4f, pText)

            pText.color = if (bought) 0xFF444444.toInt() else 0xFF888888.toInt()
            pText.textSize = sd * 12f
            canvas.drawText(desc, r.left + r.height() * 0.2f, r.centerY() + sd * 12f, pText)

            // Badge prix / VENDU
            val badgeW = r.height() * 1.1f
            val badgeR = RectF(r.right - badgeW - r.height() * 0.1f, r.top + r.height() * 0.2f,
                r.right - r.height() * 0.1f, r.bottom - r.height() * 0.2f)
            canvas.drawRoundRect(badgeR, cr * 0.4f, cr * 0.4f,
                when { bought -> pShopSold; canBuy -> pShopBuy; else -> pShopSold })
            pText.textAlign = Paint.Align.CENTER
            pText.color = if (bought) 0xFF555555.toInt() else 0xFFFFFFFF.toInt()
            pText.textSize = sd * 13f
            canvas.drawText(if (bought) "✓" else "${item.cost}g",
                badgeR.centerX(), badgeR.centerY() + sd * 5f, pText)
        }

        // Bouton Descend
        val dRect = shopDescendRect(panel)
        canvas.drawRoundRect(dRect, cr * 0.6f, cr * 0.6f, pShopDescend)
        pText.textAlign = Paint.Align.CENTER
        pText.color = 0xFFFFFFFF.toInt()
        pText.textSize = sd * 16f
        canvas.drawText("↓  Descendre", dRect.centerX(), dRect.centerY() + sd * 6f, pText)
    }

    // ── Swipe visual ────────────────────────────────────────────────────────────

    private fun drawSwipeZone(canvas: Canvas) {
        if (!touching) return
        val dx = touchCurrX - touchDownX
        val dy = touchCurrY - touchDownY
        val dist = sqrt(dx * dx + dy * dy)

        canvas.drawCircle(touchDownX, touchDownY, 18f, pSwipeDot)

        if (dist >= swipeMinPx * 0.5f) {
            canvas.drawLine(touchDownX, touchDownY, touchCurrX, touchCurrY, pSwipeLine)
            val angle = atan2(dy, dx)
            val tipX = touchDownX + cos(angle) * (dist + 16f)
            val tipY = touchDownY + sin(angle) * (dist + 16f)
            val leftX = tipX + cos(angle + 2.4f) * 20f
            val leftY = tipY + sin(angle + 2.4f) * 20f
            val rightX = tipX + cos(angle - 2.4f) * 20f
            val rightY = tipY + sin(angle - 2.4f) * 20f
            val path = Path().apply {
                moveTo(tipX, tipY); lineTo(leftX, leftY); lineTo(rightX, rightY); close()
            }
            canvas.drawPath(path, pSwipeDot)
        }
    }

    // ── Touch ───────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        game ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y
                touchCurrX = event.x; touchCurrY = event.y
                touching = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchCurrX = event.x; touchCurrY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                val dist = sqrt(dx * dx + dy * dy)
                val g = game
                if (g != null && g.shopOpen) {
                    // Shop ouvert : seulement les taps comptent
                    if (dist < swipeMinPx) {
                        val panel = shopPanelRect()
                        val shopItems = listOf(ShopItem.POTION, ShopItem.ATK_UP, ShopItem.BARRIER)
                        shopItems.forEachIndexed { i, item ->
                            if (shopItemRect(i, panel).contains(touchDownX, touchDownY))
                                onBuyShopItem?.invoke(item)
                        }
                        if (shopDescendRect(panel).contains(touchDownX, touchDownY))
                            onConfirmDescend?.invoke()
                    }
                } else if (dist < swipeMinPx) {
                    // Tap normal : icônes HUD
                    if (g != null && g.phase == GamePhase.PLAYING) {
                        when {
                            heartRect().contains(touchDownX, touchDownY)
                                    && g.player.inventory.isNotEmpty() -> onUseItem?.invoke()
                            stairsIconRect().contains(touchDownX, touchDownY)
                                    && g.onStairsTile() -> onDescend?.invoke()
                        }
                    }
                } else if (g?.phase == GamePhase.PLAYING) {
                    // Glissé : déplacement
                    val mx = if (abs(dx) > dist * 0.38f) dx.sign.toInt() else 0
                    val my = if (abs(dy) > dist * 0.38f) dy.sign.toInt() else 0
                    if (mx != 0 || my != 0) onMove?.invoke(mx, my)
                }
                touching = false
                invalidate()
            }
        }
        return true
    }
}
