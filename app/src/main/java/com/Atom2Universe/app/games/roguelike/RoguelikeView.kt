package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.*

/**
 * Les lignes du journal dont certains arguments sont des montants (PV, or) : ceux-là sont abrégés
 * au-delà de 100 000 et leur chaîne attend `%s`. Les autres nombres (étage…) restent tels quels.
 */
private val LOG_AMOUNT_ARGS: Map<Int, Set<Int>> = mapOf(
    R.string.roguelike_log_rest to setOf(0, 1),
    R.string.roguelike_log_camp_rest to setOf(0, 1),
    R.string.roguelike_log_victory to setOf(0),
    R.string.roguelike_log_gold_pickup to setOf(0),
    R.string.roguelike_log_sold to setOf(1),
)

/** Résout une entrée de journal : les arguments Labeled/Equipment sont d'abord traduits en texte. */
private fun LogEntry.resolve(context: Context): String {
    val amounts = LOG_AMOUNT_ARGS[keyRes].orEmpty()
    val resolvedArgs = args.mapIndexed { i, a ->
        when {
            a is Equipment -> LootSystem.displayName(context, a)
            a is Labeled   -> context.getString(a.labelRes)
            i in amounts && a is Int -> DungeonNumbers.format(context, a)
            else           -> a
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
    var onOpenStairs:     (() -> Unit)? = null
    var onDescend:        (() -> Unit)? = null
    var onCloseStairs:    (() -> Unit)? = null
    var onEquipItem:      (() -> Unit)? = null
    var onStashDrop:      (() -> Unit)? = null
    var onOpenInventory:  (() -> Unit)? = null
    var onRestartAfterDeath: ((atCheckpoint: Boolean) -> Unit)? = null

    private val mapArt = DungeonMapArt()
    private val actors = DungeonCombatArt()
    private var drawnLevel: DungeonLevel? = null
    private var lastPlayerPos: Pos? = null
    private var facingLeft = false
    private var tileSize = 40f
    private val hpBarW get() = context.resources.displayMetrics.density * 6f

    private var camX = 0f
    private var camY = 0f


    // ── Paints ──────────────────────────────────────────────────────────────────

    private val pSprite    = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val pFog       = Paint().apply { color = 0xCC000000.toInt(); isAntiAlias = false }

    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val pHpBar    = Paint().apply { isAntiAlias = false }
    private val pHpBg     = Paint().apply { color = 0xFF111111.toInt(); isAntiAlias = false }
    private val pHpSep    = Paint().apply { color = 0xFF555555.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val pShopBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0101820.toInt() }
    private val pShopSold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() }
    private val pShopDescend = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt() }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
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

    // ── Swipe ───────────────────────────────────────────────────────────────────
    private val swipeMinPx get() = 16f * context.resources.displayMetrics.density
    private var touchDownX = 0f; private var touchDownY = 0f
    private var touchCurrX = 0f; private var touchCurrY = 0f
    private var touching   = false

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        tileSize = w / 11f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopHold()
        actors.clearMapPacks()
        mapArt.clearTiles()
    }

    private val sd get() = context.resources.displayMetrics.density * context.resources.configuration.fontScale

    // ── Sprite sheet ─────────────────────────────────────────────────────────────

    // ── Draw ────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        if (drawnLevel !== g.level) {
            drawnLevel = g.level
            actors.clearMapPacks()
            mapArt.clearTiles()
            lastPlayerPos = null
        }
        lastPlayerPos?.let { if (it.x != g.playerPos.x) facingLeft = g.playerPos.x < it.x }
        lastPlayerPos = g.playerPos
        actors.tick()
        updateCamera(g)
        drawMap(canvas, g)
        drawItems(canvas, g)
        drawPacks(canvas, g)
        drawPlayer(canvas, g)
        drawHpBar(canvas, g)
        drawHud(canvas, g)
        drawHudIcons(canvas, g)
        val overlay = g.stairsOpen || g.pendingEquipDrop != null || g.deathReport != null
        if (g.stairsOpen)               drawStairs(canvas, g)
        if (g.pendingEquipDrop != null) drawLootPopup(canvas, g)
        if (g.deathReport != null)      drawDeathPanel(canvas, g)
        if (!overlay) drawSwipeZone(canvas)
        if (g.isExploring && isShown) postInvalidateDelayed(80L)
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

    private fun drawMap(canvas: Canvas, g: RoguelikeGame) {
        canvas.drawColor(0xFF101820.toInt())
        val lv = g.level
        val minX = maxOf(0, (camX / tileSize).toInt())
        val minY = maxOf(0, (camY / tileSize).toInt())
        val maxX = minOf(lv.w - 1, ((camX + width) / tileSize).toInt())
        val maxY = minOf(lv.h - 1, ((camY + height) / tileSize).toInt())
        for (ty in minY..maxY) for (tx in minX..maxX) {
            if (!lv.explored[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val rect = RectF(l, t, l + tileSize, t + tileSize)
            val theme = lv.themeAt(tx, ty)
            val passage = lv.passages[Pos(tx, ty)]
            val waterway = lv.waterways[Pos(tx, ty)]
            val scenery = lv.scenery[Pos(tx, ty)]
            var neighbours = 0
            if (theme == DungeonTheme.CEMETERY || theme == DungeonTheme.DUNGEON || theme == DungeonTheme.FOREST || theme == DungeonTheme.SPACESHIP || theme == DungeonTheme.MINE || theme == DungeonTheme.MINE_DEPOT || theme == DungeonTheme.CRYPT) {
                if (!lv.walkable(tx, ty - 1)) neighbours = neighbours or 1
                if (!lv.walkable(tx + 1, ty)) neighbours = neighbours or 2
                if (!lv.walkable(tx, ty + 1)) neighbours = neighbours or 4
                if (!lv.walkable(tx - 1, ty)) neighbours = neighbours or 8
            }
            if (theme == DungeonTheme.PIRATE_CABIN) {
                if (!lv.walkable(tx, ty - 1) || lv.themeAt(tx, ty - 1) != theme) neighbours = neighbours or 1
                if (!lv.walkable(tx + 1, ty) || lv.themeAt(tx + 1, ty) != theme) neighbours = neighbours or 2
                if (!lv.walkable(tx, ty + 1) || lv.themeAt(tx, ty + 1) != theme) neighbours = neighbours or 4
                if (!lv.walkable(tx - 1, ty) || lv.themeAt(tx - 1, ty) != theme) neighbours = neighbours or 8
            }
            if (theme == DungeonTheme.PIRATE) {
                if (ty == 0) neighbours = neighbours or 16
                if (tx == lv.w - 1) neighbours = neighbours or 32
                if (ty == lv.h - 1) neighbours = neighbours or 64
                if (tx == 0) neighbours = neighbours or 128
            }
            val monument = lv.mausoleums.firstOrNull {
                tx in it.x until it.x + CemeteryMonuments.SIZE && ty in it.y until it.y + CemeteryMonuments.SIZE
            }
            if (monument != null) mapArt.mausoleumCell(canvas, rect, tx - monument.x, ty - monument.y)
            else mapArt.tile(canvas, rect, theme, if (scenery != null || waterway != null || passage?.kind == PassageKind.FENCE) TileType.FLOOR else lv.tiles[ty][tx], tx, ty, neighbours)
            if (passage != null) mapArt.passage(canvas, rect, passage)
            if (waterway != null) mapArt.waterway(canvas, rect, waterway)
            if (scenery != null) mapArt.scenery(canvas, rect, scenery)
            if (!lv.visible[ty][tx]) {
                pFog.alpha = 170
                canvas.drawRect(rect, pFog)
            }
        }
    }
    // ── Objets au sol ────────────────────────────────────────────────────────────

    private fun drawItems(canvas: Canvas, g: RoguelikeGame) {
        for (item in g.level.items) {
            val tx = item.pos.x; val ty = item.pos.y
            if (!isOnScreen(tx, ty) || !g.level.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val pad = tileSize * 0.15f
            mapArt.pickup(canvas, RectF(l + pad, t + pad, l + tileSize - pad, t + tileSize - pad), item.type == ItemType.GOLD, item.relic?.ordinal ?: 0)
        }
    }

    // ── Monstres ────────────────────────────────────────────────────────────────

    private fun drawPacks(canvas: Canvas, g: RoguelikeGame) {
        val lv = g.level
        for ((packIndex, pack) in lv.packs.withIndex()) {
            if (!pack.alive) continue
            val tx = pack.pos.x; val ty = pack.pos.y
            if (!isOnScreen(tx, ty) || !lv.visible[ty][tx]) continue
            val l = tileLeft(tx); val t = tileTop(ty)
            val rect = RectF(l, t, l + tileSize, t + tileSize)
            actors.prepareMapPack(pack)
            actors.drawShadow(canvas, rect)
            actors.drawMonster(canvas, rect, pack.types.first(), actorIndex = packIndex)

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
        val rect = RectF(l, t, l + tileSize, t + tileSize)
        pFill.color = 0x775ECAC7
        canvas.drawOval(l + tileSize * .2f, t + tileSize * .76f, l + tileSize * .8f, t + tileSize * .94f, pFill)
        canvas.save()
        if (facingLeft) canvas.scale(-1f, 1f, rect.centerX(), rect.centerY())
        actors.drawShadow(canvas, rect, hero = true)
        actors.drawHero(canvas, rect, g.hero)
        canvas.restore()
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
        pText.textAlign = Paint.Align.RIGHT
        pText.textSize = sd * 12f
        val region = context.getString(R.string.roguelike_region_floor, g.floor, context.getString(g.level.themeAt(g.playerPos.x, g.playerPos.y).label))
        val badge = RectF(width - pText.measureText(region) - 24f, 8f, width - 8f, 16f + sd * 18f)
        canvas.drawRoundRect(badge, 6f, 6f, pIconBg)
        pText.color = 0xFFE3D7B3.toInt()
        canvas.drawText(region, badge.right - 8f, badge.bottom - 8f, pText)
        pText.textSize = sd * 10f
        canvas.drawText(context.getString(R.string.roguelike_map_format, context.getString(g.level.format.label), g.level.w, g.level.h), badge.right - 8f, badge.bottom + sd * 14f, pText)
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
            g.onCampTile()   -> R.string.roguelike_camp_hint
            g.hero.hp < g.hero.maxHp -> R.string.roguelike_camp_return_hint
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
        canvas.drawText(context.getString(R.string.roguelike_death_title), cx, cy - 100f * sd, pText)
        pText.color = 0xFFCCCCCC.toInt(); pText.textSize = sd * 15f
        canvas.drawText(context.getString(R.string.roguelike_death_summary, report.floor, DungeonNumbers.format(context, report.goldLost)), cx, cy - 60f * sd, pText)
        for (atCheckpoint in listOf(false, true)) {
            val bounds = deathChoiceRect(atCheckpoint)
            pFill.color = if (atCheckpoint) 0xFF35465D.toInt() else 0xFF246241.toInt()
            canvas.drawRoundRect(bounds, 8f * sd, 8f * sd, pFill)
            val label = context.getString(if (atCheckpoint) R.string.roguelike_death_restart_checkpoint else R.string.roguelike_death_restart_floor,
                if (atCheckpoint) report.checkpointFloor else report.floor)
            pText.color = Color.WHITE; pText.textSize = sd * 16f
            val availableWidth = bounds.width() - 20f * sd
            if (pText.measureText(label) > availableWidth) pText.textSize *= availableWidth / pText.measureText(label)
            canvas.drawText(label, bounds.centerX(), bounds.centerY() - (pText.ascent() + pText.descent()) / 2f, pText)
        }
    }

    private fun deathChoiceRect(atCheckpoint: Boolean): RectF {
        val halfWidth = minOf(width / 2f - 16f * sd, 220f * sd)
        val top = height / 2f + (if (atCheckpoint) 42f else -22f) * sd
        return RectF(width / 2f - halfWidth, top, width / 2f + halfWidth, top + 52f * sd)
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

        if (g.onCampTile()) {
            val rest = restBtnRect()
            canvas.drawCircle(rest.centerX(), rest.centerY(), rest.width() / 2f, if (g.canRest()) pIconOn else pIconBg)
            mapArt.scenery(canvas, rest, MapScenery(SceneryKind.BONFIRE))
        }
        drawRelicStatus(canvas, g)

        // Bouton inventaire (sac ⚔)
        val r = inventoryBtnRect()
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, pIconBg)
        pText.color = 0xFFCCCCCC.toInt(); pText.textSize = r.height() * 0.52f
        canvas.drawText("⚔", r.centerX(), r.centerY() + r.height() * 0.19f, pText)
    }

    /**
     * Les reliques portées, en haut à gauche : on voit avant d'engager un combat si ses
     * sorts sont prêts. Assombrie avec le nombre de tours restants si elle se recharge.
     */
    private fun drawRelicStatus(canvas: Canvas, g: RoguelikeGame) {
        val s = iconSizePx * 0.8f; val m = iconMarginPx
        var x = hpBarW + m
        for (relic in g.hero.relicSlots) {
            if (relic == null) continue
            val r = RectF(x, m, x + s, m + s)
            canvas.drawRoundRect(r, s * 0.2f, s * 0.2f, pIconBg)
            mapArt.pickup(canvas, RectF(r.left + s * 0.1f, r.top + s * 0.1f, r.right - s * 0.1f, r.bottom - s * 0.1f), false, relic.ordinal)
            val cd = g.hero.relicCooldown(relic)
            if (cd > 0) {
                canvas.drawRoundRect(r, s * 0.2f, s * 0.2f, pOverlay)
                pText.color = 0xFFFFFFFF.toInt(); pText.textSize = s * 0.5f
                canvas.drawText(context.getString(R.string.roguelike_hud_relic_cooldown, cd), r.centerX(), r.centerY() + s * 0.18f, pText)
            }
            x += s + m
        }
    }

    // ── L'escalier : descendre ou rester ─────────────────────────────────────────

    private fun stairsPanelRect() = RectF(width * 0.06f, height * 0.32f, width * 0.94f, height * 0.68f)

    private fun stairsDescendRect(panel: RectF): RectF {
        val btnH = panel.height() * 0.28f; val gap = panel.height() * 0.08f
        return RectF(panel.left + gap, panel.bottom - btnH - gap, panel.centerX() - gap / 2, panel.bottom - gap)
    }

    private fun stairsStayRect(panel: RectF): RectF {
        val btnH = panel.height() * 0.28f; val gap = panel.height() * 0.08f
        return RectF(panel.centerX() + gap / 2, panel.bottom - btnH - gap, panel.right - gap, panel.bottom - gap)
    }

    private fun drawStairs(canvas: Canvas, g: RoguelikeGame) {
        val panel = stairsPanelRect(); val cr = 16f * context.resources.displayMetrics.density
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pOverlay)
        canvas.drawRoundRect(panel, cr, cr, pShopBg)
        pText.textAlign = Paint.Align.CENTER
        pText.color = 0xFFFFD600.toInt(); pText.textSize = sd * 20f
        canvas.drawText(context.getString(R.string.roguelike_stairs_title), panel.centerX(), panel.top + panel.height() * 0.22f, pText)
        pText.color = 0xFFAAAAAA.toInt(); pText.textSize = sd * 13f
        canvas.drawText(context.getString(R.string.roguelike_stairs_next, g.floor + 1), panel.centerX(), panel.top + panel.height() * 0.38f, pText)

        val dRect = stairsDescendRect(panel)
        canvas.drawRoundRect(dRect, cr * 0.6f, cr * 0.6f, pShopDescend)
        pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 16f
        canvas.drawText(context.getString(R.string.roguelike_stairs_descend), dRect.centerX(), dRect.centerY() + sd * 6f, pText)
        val sRect = stairsStayRect(panel)
        canvas.drawRoundRect(sRect, cr * 0.6f, cr * 0.6f, pShopSold)
        canvas.drawText(context.getString(R.string.roguelike_stairs_stay), sRect.centerX(), sRect.centerY() + sd * 6f, pText)
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

        val archetype = g.hero.archetype
        val delta = LootSystem.rating(equip, archetype) - (current?.let { LootSystem.rating(it, archetype) } ?: 0)
        drawItemColumn(canvas, equip, colL, isNew = true, cr, gap, delta)
        if (current != null)
            drawItemColumn(canvas, current, colR, isNew = false, cr, gap, null)
        else {
            pText.textAlign = Paint.Align.CENTER; pText.color = 0xFF555555.toInt(); pText.textSize = sd * 13f
            canvas.drawText(context.getString(R.string.roguelike_loot_nothing_equipped), colR.centerX(), colR.centerY(), pText)
        }


        val equipR  = lootEquipBtnRect(panel)
        val ignoreR = lootIgnoreBtnRect(panel)
        canvas.drawRoundRect(equipR,  cr * 0.6f, cr * 0.6f, pEquipBtn)
        canvas.drawRoundRect(ignoreR, cr * 0.6f, cr * 0.6f, pIgnoreBtn)
        pText.textAlign = Paint.Align.CENTER; pText.color = 0xFFFFFFFF.toInt(); pText.textSize = sd * 15f
        canvas.drawText(context.getString(R.string.roguelike_loot_equip_btn),  equipR.centerX(),  equipR.centerY()  + sd * 6f, pText)
        canvas.drawText(context.getString(R.string.roguelike_loot_stash_btn), ignoreR.centerX(), ignoreR.centerY() + sd * 6f, pText)
    }

    /** Une colonne du popup : icône, badge, nom (couleur de rareté), note, puis les stats. */
    private fun drawItemColumn(canvas: Canvas, equip: Equipment, col: RectF, isNew: Boolean, cr: Float, gap: Float, delta: Int?) {
        val iconSize = minOf(col.width() * 0.40f, col.height() * 0.22f)
        val iconRect = RectF(col.centerX() - iconSize / 2, col.top + gap,
                             col.centerX() + iconSize / 2, col.top + gap + iconSize)

        pLootBorder.color = equip.rarity.colorArgb
        canvas.drawRoundRect(iconRect, cr * 0.4f, cr * 0.4f, pLootCardBg)
        canvas.drawRoundRect(iconRect, cr * 0.4f, cr * 0.4f, pLootBorder)
        PixelArtIcon.draw(canvas, EquipmentArt.icon(equip), RectF(iconRect.left + 4f, iconRect.top + 4f, iconRect.right - 4f, iconRect.bottom - 4f), pSprite)

        var y = iconRect.bottom + gap
        pText.textAlign = Paint.Align.CENTER
        pText.color = if (isNew) 0xFFFFD600.toInt() else 0xFF888888.toInt()
        pText.textSize = sd * 11f
        canvas.drawText(context.getString(if (isNew) R.string.roguelike_loot_new_badge else R.string.roguelike_loot_equipped_badge), col.centerX(), y + sd * 11f, pText)
        y += sd * 11f + gap * 0.5f

        pText.color = equip.rarity.colorArgb; pText.textSize = sd * 13f
        canvas.drawText(LootSystem.displayName(context, equip), col.centerX(), y + sd * 13f, pText)
        y += sd * 13f + gap * 0.4f

        pText.color = 0xFF78909C.toInt(); pText.textSize = sd * 11f
        canvas.drawText(context.getString(R.string.roguelike_item_subtitle, context.getString(equip.rarity.labelRes),
            context.getString(R.string.roguelike_rating, DungeonNumbers.format(context, LootSystem.rating(equip, game?.hero?.archetype)))), col.centerX(), y + sd * 11f, pText)
        y += sd * 11f + gap * 0.3f

        if (delta != null) {
            pText.textSize = sd * 13f
            pText.color = when { delta > 0 -> 0xFF66BB6A.toInt(); delta < 0 -> 0xFFEF5350.toInt(); else -> 0xFF90A4AE.toInt() }
            val text = when {
                delta > 0 -> context.getString(R.string.roguelike_delta_up, DungeonNumbers.format(context, delta))
                delta < 0 -> context.getString(R.string.roguelike_delta_down, DungeonNumbers.format(context, -delta))
                else      -> context.getString(R.string.roguelike_delta_equal)
            }
            canvas.drawText(text, col.centerX(), y + sd * 13f, pText)
            y += sd * 13f
        }
        y += gap * 0.6f

        for (line in LootSystem.describe(context, equip, archetype = game?.hero?.archetype)) {
            pText.color = if (isNew) 0xFFDDFFDD.toInt() else 0xFFAAAAAA.toInt()
            pText.textSize = sd * 12f
            canvas.drawText(line, col.centerX(), y + sd * 12f, pText)
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
                    g.deathReport != null -> if (tap && event.action == MotionEvent.ACTION_UP) {
                        for (atCheckpoint in listOf(false, true)) {
                            val bounds = deathChoiceRect(atCheckpoint)
                            if (bounds.contains(touchDownX, touchDownY) && bounds.contains(event.x, event.y)) {
                                onRestartAfterDeath?.invoke(atCheckpoint)
                                break
                            }
                        }
                    }

                    g.pendingEquipDrop != null -> if (tap) {
                        val panel = lootPanelRect()
                        when {
                            lootEquipBtnRect(panel).contains(touchDownX, touchDownY)  -> onEquipItem?.invoke()
                            lootIgnoreBtnRect(panel).contains(touchDownX, touchDownY) -> onStashDrop?.invoke()
                        }
                    }

                    g.stairsOpen -> if (tap) {
                        val panel = stairsPanelRect()
                        when {
                            stairsDescendRect(panel).contains(touchDownX, touchDownY) -> onDescend?.invoke()
                            stairsStayRect(panel).contains(touchDownX, touchDownY)    -> onCloseStairs?.invoke()
                        }
                    }

                    !g.isExploring -> {}

                    tap -> when {
                        inventoryBtnRect().contains(touchDownX, touchDownY) -> onOpenInventory?.invoke()
                        g.onCampTile() && (restBtnRect().contains(touchDownX, touchDownY) ||
                            RectF(tileLeft(g.playerPos.x),tileTop(g.playerPos.y),tileLeft(g.playerPos.x)+tileSize,tileTop(g.playerPos.y)+tileSize).contains(touchDownX,touchDownY)) -> onRest?.invoke()
                        stairsIconRect().contains(touchDownX, touchDownY) && g.onStairsTile() -> onOpenStairs?.invoke()
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
        g.isExploring

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
