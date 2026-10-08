package com.Atom2Universe.app.games.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.Atom2Universe.app.games.kit.Celebration
import com.Atom2Universe.app.games.kit.KitPalette
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Ce qu'on voit d'un joueur : son nom, sa main, et de quoi savoir si c'est à lui de jouer. */
class SeatInfo(
    val name: String,
    val handSize: Int,
    /** Les cartes visibles (la main du joueur), déjà rangées ; null pour un adversaire dont les cartes restent cachées. */
    val hand: List<PlayingCard>? = null,
    /** Les cartes soulevées (choisies, en attente d'être jouées ou passées). */
    val lifted: Set<PlayingCard> = emptySet(),
    /** Les cartes jouables : les autres sont grisées. null : aucune n'est grisée. */
    val playable: Set<PlayingCard>? = null,
    /** Les cartes entourées de la couleur d'accent (celles qu'on vient de recevoir). */
    val marked: Set<PlayingCard> = emptySet(),
    /** Une ligne sous le nom : points, plis… */
    val caption: String? = null,
    /** C'est à ce joueur de jouer. */
    val active: Boolean = false,
)

/** La pioche et la défausse, pour les jeux qui en ont. */
class PileInfo(
    val stock: Int,
    val top: PlayingCard?,
    /** La couleur à suivre quand un 8 l'a changée. */
    val suit: CardSuit? = null,
    /** La pioche attend un toucher du joueur. */
    val stockActive: Boolean = false,
    /** La défausse attend un toucher du joueur (prendre sa carte du dessus). */
    val discardActive: Boolean = false,
)

/** Une table : ce que l'écran dessine pour l'état présent de la partie. */
class TableScene(
    val seats: List<SeatInfo>,
    /** Le pli déjà posé au centre (à la reprise d'une partie) : qui a posé quoi, dans l'ordre. */
    val trick: List<Pair<Int, PlayingCard>> = emptyList(),
    val pile: PileInfo? = null,
    /** La rangée déjà posée au centre (à la reprise d'une partie) : qui a posé quoi, dans l'ordre. */
    val row: List<Pair<Int, PlayingCard>> = emptyList(),
    /** Un nombre posé à côté de la rangée (le total du décompte au cribbage). */
    val rowBadge: String? = null,
    /** Combien de cartes la rangée peut recevoir : sa place est réservée, elle ne bouge pas quand elle grandit. */
    val rowCapacity: Int = 8,
    /** La table réserve une place à la rangée sous la pioche (même quand elle est vide). */
    val hasRow: Boolean = false,
)

/**
 * La table de jeu : ta main en éventail en bas, les adversaires autour avec leurs cartes cachées, le
 * pli (ou la pioche et la défausse) au centre. Les cartes voyagent quand on les joue.
 *
 * La vue ne connaît aucune règle. L'écran lui donne une [TableScene] (l'état) et, à chaque coup, la
 * liste des [CardEvent] qui l'expliquent : ce sont eux qui déclenchent les trajets. Pendant un
 * trajet la scène est déjà à jour (la carte a quitté la main) ; le pli, lui, n'apparaît qu'à
 * l'arrivée de la carte.
 *
 * Un toucher sur une carte de ta main, ou sur la pioche, est transmis à l'écran ([onCardTap],
 * [onStockTap]) ; c'est lui qui décide (soulever, jouer, ne rien faire). Pendant les trajets, rien
 * n'est transmis.
 */
class CardTableView(context: Context, labels: Array<String>? = null) : View(context) {

    var palette: KitPalette = KitPalette.artwork(0xFF4FC3F7.toInt())
        set(value) { field = value; painter.palette = value; invalidate() }

    val painter = CardPainter(context, labels = labels)

    var onCardTap: ((PlayingCard) -> Unit)? = null

    /** Une carte de ta main a été glissée puis lâchée au-dessus de la main, vers la table. */
    var onCardDrop: ((PlayingCard) -> Unit)? = null
    var onStockTap: (() -> Unit)? = null
    var onDiscardTap: (() -> Unit)? = null

    /** Appelé une fois, quand la dernière carte en route est arrivée. */
    var onIdle: (() -> Unit)? = null

    private var scene: TableScene? = null
    private val density = resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    // ── Ce qui est posé sur la table (change par les événements, pas par la scène) ───────────
    private val shownTrick = ArrayList<Pair<Int, PlayingCard>>()
    private var shownTop: PlayingCard? = null
    private var trickFlying = false
    private val hiddenInHand = HashSet<PlayingCard>()
    private val shownRow = ArrayList<Pair<Int, PlayingCard>>()

    // ── Trajets ─────────────────────────────────────────────────────────────────────────────
    private class Item(val card: PlayingCard?, val from: RectF, val to: RectF, val angle: Float)
    private class Step(val duration: Long, val begin: (() -> List<Item>)? = null, val end: (() -> Unit)? = null)

    private val queue = ArrayDeque<Step>()
    private var step: Step? = null
    private var stepStart = 0L
    private var items: List<Item> = emptyList()

    val busy: Boolean get() = step != null || queue.isNotEmpty()

    private val celebration = Celebration(this)

    // ── Géométrie, recalculée quand la scène ou la taille changent ───────────────────────────
    private val handRects = LinkedHashMap<PlayingCard, RectF>()
    private class Fan(val first: RectF, val dx: Float, val dy: Float, val count: Int) {
        fun rect(i: Int) = RectF(first).apply { offset(dx * i, dy * i) }
        fun bounds() = RectF(first).apply { if (count > 0) union(rect(count - 1)) }
    }
    private val fans = HashMap<Int, Fan>()
    private val pills = HashMap<Int, RectF>()
    private val stockRect = RectF()
    private val discardRect = RectF()
    private val rowRect = RectF()
    private var rowStep = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var trickW = 0f
    private var trickH = 0f
    private var handW = 0f
    private var handH = 0f
    private var lift = 0f
    private var handBandTop = 0f

    // ── Glisser une carte de la main vers la table ───────────────────────────────────────────
    private var dragCard: PlayingCard? = null
    private var dragging = false
    private var overZone = false
    private val dragRect = RectF()
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    /** Où la carte a été lâchée : le trajet qui suit part de là, pas de la main. */
    private var dropFrom: Pair<PlayingCard, RectF>? = null

    private var laidOutFor: TableScene? = null
    private var laidOutSize = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()

    // ── API ─────────────────────────────────────────────────────────────────────────────────

    /** Pose la scène telle quelle, sans trajet (début de partie, reprise, nouvelle manche). */
    fun show(scene: TableScene) {
        skipAnimations()
        this.scene = scene
        shownTrick.clear()
        shownTrick.addAll(scene.trick)
        shownTop = scene.pile?.top
        shownRow.clear()
        shownRow.addAll(scene.row)
        hiddenInHand.clear()
        invalidate()
    }

    /** Change la scène sans trajet ni remise à zéro du pli (une carte soulevée, un tour qui commence). */
    fun update(scene: TableScene) {
        this.scene = scene
        laidOutFor = null
        invalidate()
    }

    /** Met la scène à jour et joue les trajets des [events]. */
    fun present(scene: TableScene, events: List<CardEvent>) {
        // Là où étaient les cartes de ta main avant le coup : c'est de là qu'elles partent.
        val before = HashMap<PlayingCard, RectF>()
        for ((c, r) in handRects) before[c] = RectF(r)
        this.scene = scene
        laidOutFor = null
        ensureLayout()
        // Le dessus de la défausse suit la scène, sauf quand une carte y arrive ou en part : le trajet s'en charge.
        if (events.none { (it is CardEvent.Play && it.toPile) || (it is CardEvent.Draw && it.fromDiscard) }) {
            shownTop = scene.pile?.top
        }
        for (e in events) enqueue(e, before)
        invalidate()
    }

    /** Les trajets se terminent d'un coup (écran qui se met en pause, partie qui recommence). */
    fun skipAnimations() {
        step?.end?.invoke()
        step = null
        items = emptyList()
        while (queue.isNotEmpty()) queue.removeFirst().end?.invoke()
        trickFlying = false
        hiddenInHand.clear()
    }

    fun celebrate() = celebration.start(palette)

    override fun onDetachedFromWindow() {
        celebration.cancel()
        super.onDetachedFromWindow()
    }

    // ── Événements → trajets ────────────────────────────────────────────────────────────────

    private fun enqueue(event: CardEvent, before: Map<PlayingCard, RectF>) {
        when (event) {
            is CardEvent.Play -> {
                val n = scene?.seats?.size ?: 4
                queue.add(Step(PLAY_MS, begin = {
                    ensureLayout()
                    val dropped = dropFrom?.takeIf { it.first == event.card }?.second
                    val from = if (event.seat == 0) dropped ?: before[event.card] ?: seatAnchor(0) else seatAnchor(event.seat)
                    val to = when {
                        event.toPile -> RectF(discardRect)
                        event.toRow -> rowSlot(shownRow.size)
                        else -> trickSlot(event.seat, n)
                    }
                    listOf(Item(event.card, RectF(from), to, if (event.toPile || event.toRow) 0f else tilt(event.card)))
                }, end = {
                    when {
                        event.toPile -> shownTop = event.card
                        event.toRow -> shownRow.add(event.seat to event.card)
                        else -> shownTrick.add(event.seat to event.card)
                    }
                }))
            }
            is CardEvent.Draw -> {
                queue.add(Step(0, end = { event.card?.let { hiddenInHand.add(it) } }))
                queue.add(Step(DRAW_MS, begin = {
                    ensureLayout()
                    val to = event.card?.let { handRects[it] } ?: seatAnchor(event.seat)
                    if (event.fromDiscard) shownTop = scene?.pile?.top
                    listOf(Item(event.card, RectF(if (event.fromDiscard) discardRect else stockRect), RectF(to), 0f))
                }, end = { hiddenInHand.clear() }))
            }
            is CardEvent.Take -> {
                queue.add(Step(TAKE_PAUSE_MS))
                queue.add(Step(TAKE_MS, begin = {
                    trickFlying = true
                    val to = seatAnchor(event.seat)
                    fun target() = RectF(to).also { r -> r.inset(r.width() * 0.2f, r.height() * 0.2f) }
                    val flying = ArrayList<Item>()
                    val n = scene?.seats?.size ?: 4
                    for ((seat, card) in shownTrick) flying.add(Item(card, trickSlot(seat, n), target(), tilt(card)))
                    for ((i, pair) in shownRow.withIndex()) flying.add(Item(pair.second, rowSlot(i), target(), 0f))
                    flying
                }, end = {
                    shownTrick.clear()
                    shownRow.clear()
                    trickFlying = false
                }))
            }
            is CardEvent.Give -> {
                queue.add(Step(0, end = { if (event.to == 0) hiddenInHand.addAll(event.cards) }))
                queue.add(Step(DRAW_MS * 2, begin = {
                    ensureLayout()
                    event.cards.map { card ->
                        val from = if (event.from == 0) before[card] ?: seatAnchor(0) else seatAnchor(event.from)
                        val to = if (event.to == 0) handRects[card] ?: seatAnchor(0) else seatAnchor(event.to)
                        Item(card, RectF(from), RectF(to), 0f)
                    }
                }, end = { hiddenInHand.removeAll(event.cards.toSet()) }))
            }
            is CardEvent.Lay -> queue.add(Step(LAY_PAUSE_MS, end = {
                shownRow.clear()
                shownRow.addAll(event.cards)
            }))
        }
    }

    /** Une petite inclinaison, toujours la même pour une carte donnée : le pli a l'air jeté, pas aligné. */
    private fun tilt(card: PlayingCard): Float = ((card.id * 37) % 13 - 6) * 1.1f

    private fun advance(now: Long) {
        while (true) {
            if (step == null) {
                val next = queue.removeFirstOrNull()
                if (next == null) return
                step = next
                stepStart = now
                items = next.begin?.invoke() ?: emptyList()
            }
            val s = step ?: return
            if (now - stepStart < s.duration) return
            s.end?.invoke()
            step = null
            items = emptyList()
            if (queue.isEmpty()) post { onIdle?.invoke() }
        }
    }

    // ── Géométrie ───────────────────────────────────────────────────────────────────────────

    private fun positionOf(seat: Int, n: Int): Int = when {
        seat == 0 -> BOTTOM
        n == 2 -> TOP
        n == 3 -> if (seat == 1) LEFT else RIGHT
        else -> when (seat) { 1 -> LEFT; 2 -> TOP; else -> RIGHT }
    }

    /** Le rectangle d'où part (ou où arrive) une carte du joueur [seat] quand on ne sait pas laquelle. */
    private fun seatAnchor(seat: Int): RectF {
        ensureLayout()
        if (seat == 0) {
            val bottom = height - paddingBottom - 6 * density
            return RectF(centerX - handW / 2f, bottom - handH, centerX + handW / 2f, bottom)
        }
        fans[seat]?.let { if (it.count > 0) return it.rect(it.count / 2) }
        val pill = pills[seat] ?: return RectF(centerX - handW / 2f, 0f, centerX + handW / 2f, handH)
        return RectF(pill.centerX() - handW / 2f, pill.bottom, pill.centerX() + handW / 2f, pill.bottom + handH)
    }

    /** La place de la carte numéro [index] de la rangée du centre. */
    private fun rowSlot(index: Int): RectF {
        ensureLayout()
        return RectF(rowRect.left + rowStep * index, rowRect.top, rowRect.left + rowStep * index + trickW, rowRect.bottom)
    }

    private fun trickSlot(seat: Int, n: Int): RectF {
        val cx = centerX
        val cy = centerY
        val (dx, dy) = when (positionOf(seat, n)) {
            BOTTOM -> 0f to trickH * 0.34f
            TOP -> 0f to -trickH * 0.34f
            LEFT -> -trickW * 0.78f to 0f
            else -> trickW * 0.78f to 0f
        }
        return RectF(cx + dx - trickW / 2, cy + dy - trickH / 2, cx + dx + trickW / 2, cy + dy + trickH / 2)
    }

    private fun ensureLayout() {
        val s = scene ?: return
        val size = width * 31 + height
        if (laidOutFor === s && laidOutSize == size) return
        laidOutFor = s
        laidOutSize = size
        val padL = paddingLeft.toFloat()
        val padR = paddingRight.toFloat()
        val padT = paddingTop.toFloat()
        val padB = paddingBottom.toFloat()
        val w = width - padL - padR
        val h = height - padT - padB
        if (w <= 0f || h <= 0f) return
        val n = s.seats.size

        handH = min(h * 0.20f, w * 0.24f / CardPainter.ASPECT)
        handW = handH * CardPainter.ASPECT
        lift = handH * 0.26f
        val oppH = handH * 0.60f
        val oppW = oppH * CardPainter.ASPECT
        trickH = handH * 0.95f
        trickW = trickH * CardPainter.ASPECT
        val pillH = sp(12f) * 1.9f
        val gap = 5 * density

        // ── Ma main, en bas.
        handRects.clear()
        val hand = s.seats[0].hand ?: emptyList()
        val rows = if (hand.size > 13) 2 else 1
        val handTop = padT + h - handH * (1f + 0.42f * (rows - 1)) - 6 * density
        val perRow = (hand.size + rows - 1) / rows
        for ((i, card) in hand.withIndex()) {
            val row = if (rows == 1) 0 else i / perRow
            val inRow = if (rows == 1) hand.size else min(perRow, hand.size - row * perRow)
            val col = i - row * perRow
            val avail = w - 12 * density
            val step = if (inRow <= 1) 0f else min(handW * 0.62f, (avail - handW) / (inRow - 1))
            val total = handW + step * (inRow - 1)
            val left = padL + w / 2f - total / 2f + step * col
            val top = handTop + row * handH * 0.42f
            handRects[card] = RectF(left, top, left + handW, top + handH)
        }
        handBandTop = handTop - lift

        // ── Mon étiquette, au-dessus de la main, à gauche.
        pills.clear()
        pills[0] = pillRect(s.seats[0], padL + 6 * density, handBandTop - pillH - gap, pillH, leftAligned = true)

        // ── Les adversaires.
        fans.clear()
        var topBottom = padT
        for (seat in 1 until n) {
            val info = s.seats[seat]
            val count = info.handSize
            when (positionOf(seat, n)) {
                TOP -> {
                    val pill = pillRect(info, padL + w / 2f, padT + gap, pillH, centered = true)
                    pills[seat] = pill
                    val avail = w - 24 * density
                    val step = if (count <= 1) 0f else min(oppW * 0.34f, (avail - oppW) / (count - 1))
                    val total = oppW + step * max(count - 1, 0)
                    val left = padL + w / 2f - total / 2f
                    fans[seat] = Fan(RectF(left, pill.bottom + gap, left + oppW, pill.bottom + gap + oppH), step, 0f, count)
                    topBottom = max(topBottom, pill.bottom + gap + oppH)
                }
                else -> {}
            }
        }
        val zoneTop = max(topBottom + gap, padT)
        val zoneBottom = pills[0]!!.top - gap
        centerY = (zoneTop + zoneBottom) / 2f
        centerX = padL + w / 2f
        for (seat in 1 until n) {
            val pos = positionOf(seat, n)
            if (pos != LEFT && pos != RIGHT) continue
            val info = s.seats[seat]
            val count = info.handSize
            val avail = (zoneBottom - zoneTop) - pillH - 2 * gap
            val step = if (count <= 1) 0f else min(oppH * 0.2f, (avail - oppH) / (count - 1)).coerceAtLeast(2 * density)
            val total = oppH + step * max(count - 1, 0)
            val blockH = pillH + gap + total
            val top = centerY - blockH / 2f
            val x = if (pos == LEFT) padL + 6 * density else padL + w - 6 * density - oppW
            val pill = pillRect(info, x + oppW / 2f, top, pillH, centered = true)
            pills[seat] = pill
            // La pastille peut être plus large que les cartes : on la cale dans l'écran.
            val shift = if (pill.left < padL + 2 * density) padL + 2 * density - pill.left
                else if (pill.right > padL + w - 2 * density) padL + w - 2 * density - pill.right else 0f
            pill.offset(shift, 0f)
            fans[seat] = Fan(RectF(x, pill.bottom + gap, x + oppW, pill.bottom + gap + oppH), 0f, step, count)
        }

        // ── La pioche et la défausse, au centre.
        val pw = trickW
        val ph = trickH
        // Avec une rangée, la pioche monte pour lui laisser la place en dessous.
        val shiftY = if (s.pile != null && s.rowCapacity > 0 && s.hasRow) -ph * 0.58f else 0f
        stockRect.set(centerX - pw * 1.12f, centerY + shiftY - ph / 2f, centerX - pw * 0.12f, centerY + shiftY + ph / 2f)
        discardRect.set(centerX + pw * 0.12f, centerY + shiftY - ph / 2f, centerX + pw * 1.12f, centerY + shiftY + ph / 2f)

        // ── La rangée : sa largeur est celle de sa capacité, pour qu'elle ne bouge pas en grandissant.
        val cap = max(2, s.rowCapacity)
        rowStep = min(trickW * 0.62f, (w - 24 * density - trickW) / (cap - 1))
        val rowTotal = trickW + rowStep * (cap - 1)
        val rowY = if (s.pile != null && s.hasRow) centerY + ph * 0.58f else centerY
        rowRect.set(centerX - rowTotal / 2f, rowY - ph / 2f, centerX - rowTotal / 2f + trickW, rowY + ph / 2f)
    }

    private fun pillRect(info: SeatInfo, anchorX: Float, top: Float, height: Float,
                         centered: Boolean = false, leftAligned: Boolean = false): RectF {
        text.textSize = sp(12f)
        val label = pillText(info)
        val width = text.measureText(label) + 20 * density
        val left = if (leftAligned) anchorX else anchorX - width / 2f
        return RectF(left, top, left + width, top + height)
    }

    private fun pillText(info: SeatInfo): String = info.caption?.let { "${info.name} · $it" } ?: info.name

    // ── Dessin ──────────────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val s = scene ?: return
        advance(SystemClock.uptimeMillis())
        ensureLayout()
        val n = s.seats.size

        // Les adversaires : pastille, cartes cachées, nombre de cartes.
        for (seat in 1 until n) {
            val fan = fans[seat] ?: continue
            for (i in 0 until fan.count) painter.drawBack(canvas, fan.rect(i))
            if (fan.count > 0) drawCount(canvas, fan, fan.count)
            pills[seat]?.let { drawPill(canvas, s.seats[seat], it) }
        }

        // La pioche et la défausse.
        s.pile?.let { drawPile(canvas, it) }

        drawDropZone(canvas)

        // Le pli.
        if (!trickFlying) for ((seat, card) in shownTrick) {
            val r = trickSlot(seat, n)
            canvas.save()
            canvas.rotate(tilt(card), r.centerX(), r.centerY())
            painter.drawFace(canvas, card, r)
            canvas.restore()
        }

        // La rangée du centre (le décompte), et son total.
        if (s.hasRow || shownRow.isNotEmpty()) drawRow(canvas, s)

        // Ma main : les cartes soulevées montent, les cartes injouables sont grisées.
        val me = s.seats[0]
        for ((card, r) in handRects) {
            if (card in hiddenInHand || (dragging && card == dragCard)) continue
            val up = card in me.lifted
            rect.set(r)
            if (up) rect.offset(0f, -lift)
            painter.drawFace(canvas, card, rect,
                dimmed = me.playable != null && card !in me.playable,
                outline = if (card in me.marked || up) palette.accent else null)
        }
        pills[0]?.let { drawPill(canvas, me, it) }

        drawFlights(canvas)
        drawDragged(canvas)

        if (celebration.running) {
            celebration.drawOver(canvas, RectF(paddingLeft.toFloat(), paddingTop.toFloat(),
                (width - paddingRight).toFloat(), (height - paddingBottom).toFloat()), palette)
        }
        if (busy || celebration.running) postInvalidateOnAnimation()
    }

    private fun drawRow(canvas: Canvas, s: TableScene) {
        if (shownRow.isEmpty()) painter.drawSlot(canvas, rowSlot(0))
        if (!trickFlying) for ((i, pair) in shownRow.withIndex()) painter.drawFace(canvas, pair.second, rowSlot(i))
        // Le total se pose à droite de la défausse (la retournée), là où il y a toujours de la place ; sans pile, au-dessus de la rangée.
        s.rowBadge?.let {
            if (s.pile != null) drawBadge(canvas, it, discardRect.right + 24 * density, discardRect.centerY())
            else drawBadge(canvas, it, rowRect.left + trickW / 2f, rowRect.top - 14 * density)
        }
    }

    /** La table s'éclaire derrière les cartes quand une carte glissée peut y être lâchée. */
    private fun drawDropZone(canvas: Canvas) {
        if (!dragging || !overZone) return
        val zone = RectF(centerX - trickW * 1.35f, centerY - trickH * 0.85f, centerX + trickW * 1.35f, centerY + trickH * 0.85f)
        fill.color = palette.withAlpha(palette.accent, 0.14f)
        canvas.drawRoundRect(zone, 18 * density, 18 * density, fill)
        line.color = palette.withAlpha(palette.accent, 0.7f)
        line.strokeWidth = 2 * density
        canvas.drawRoundRect(zone, 18 * density, 18 * density, line)
    }

    /** La carte qu'on glisse : elle suit le doigt, un peu plus grande. */
    private fun drawDragged(canvas: Canvas) {
        val card = dragCard ?: return
        if (!dragging) return
        rect.set(dragRect)
        rect.inset(-rect.width() * 0.04f, -rect.height() * 0.04f)
        painter.drawFace(canvas, card, rect, outline = if (overZone) palette.accent else null)
    }

    private fun drawFlights(canvas: Canvas) {
        val s = step ?: return
        if (items.isEmpty()) return
        val t = if (s.duration <= 0) 1f else ((SystemClock.uptimeMillis() - stepStart).toFloat() / s.duration).coerceIn(0f, 1f)
        val e = 1f - (1f - t) * (1f - t)
        for (item in items) {
            rect.set(lerp(item.from.left, item.to.left, e), lerp(item.from.top, item.to.top, e),
                lerp(item.from.right, item.to.right, e), lerp(item.from.bottom, item.to.bottom, e))
            canvas.save()
            canvas.rotate(item.angle * e, rect.centerX(), rect.centerY())
            if (item.card != null) painter.drawFace(canvas, item.card, rect) else painter.drawBack(canvas, rect)
            canvas.restore()
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun drawPile(canvas: Canvas, pile: PileInfo) {
        // La pioche : une petite pile de dos, ou un contour vide.
        if (pile.stock > 0) {
            val layers = min(3, (pile.stock + 9) / 10)
            for (i in layers - 1 downTo 0) {
                rect.set(stockRect)
                rect.offset(-i * 1.6f * density, -i * 1.6f * density)
                painter.drawBack(canvas, rect)
            }
            if (pile.stockActive) {
                line.color = palette.accent
                line.strokeWidth = 2.5f * density
                canvas.drawRoundRect(stockRect, stockRect.width() * 0.09f, stockRect.width() * 0.09f, line)
            }
            drawBadge(canvas, pile.stock.toString(), stockRect.centerX(), stockRect.bottom)
        } else painter.drawSlot(canvas, stockRect)

        // La défausse : la carte du dessus, et la couleur à suivre.
        val top = shownTop
        if (top != null) painter.drawFace(canvas, top, discardRect) else painter.drawSlot(canvas, discardRect)
        if (pile.discardActive) {
            line.color = palette.accent
            line.strokeWidth = 2.5f * density
            canvas.drawRoundRect(discardRect, discardRect.width() * 0.09f, discardRect.width() * 0.09f, line)
        }
        pile.suit?.let { suit ->
            val badgeText = suit.symbol
            drawBadge(canvas, badgeText, discardRect.centerX(), discardRect.bottom, painter.inkOf(suit), paper = true)
        }
    }

    /** Une petite pastille ronde sur le bord d'une pile : un nombre, ou la couleur à suivre. */
    private fun drawBadge(canvas: Canvas, label: String, cx: Float, cy: Float, ink: Int = palette.text, paper: Boolean = false) {
        text.textSize = sp(12f)
        val rad = max(11 * density, text.measureText(label) / 2f + 6 * density)
        fill.color = if (paper) paperColor() else palette.raised
        canvas.drawCircle(cx, cy, rad, fill)
        line.color = palette.outline
        line.strokeWidth = density
        canvas.drawCircle(cx, cy, rad, line)
        text.color = ink
        canvas.drawText(label, cx, cy - (text.ascent() + text.descent()) / 2f, text)
    }

    private fun drawCount(canvas: Canvas, fan: Fan, count: Int) {
        val b = fan.bounds()
        drawBadge(canvas, count.toString(), b.centerX(), b.centerY())
    }

    private fun drawPill(canvas: Canvas, info: SeatInfo, r: RectF) {
        val rad = r.height() / 2f
        fill.color = if (info.active) palette.accent else palette.raised
        canvas.drawRoundRect(r, rad, rad, fill)
        line.color = if (info.active) palette.accent else palette.outline
        line.strokeWidth = density.coerceAtLeast(1f)
        canvas.drawRoundRect(r, rad, rad, line)
        text.textSize = sp(12f)
        text.color = if (info.active) palette.onAccent else palette.text
        canvas.drawText(pillText(info), r.centerX(), r.centerY() - (text.ascent() + text.descent()) / 2f, text)
    }

    /** Le papier d'une carte, pour les pastilles qui montrent une couleur posée dessus. */
    private fun paperColor(): Int = palette.blend(0xFFFFFFFF.toInt(), palette.accent, 0.05f)

    // ── Toucher ─────────────────────────────────────────────────────────────────────────────

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scene == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                dragCard = if (busy) null else cardAt(event.x, event.y)?.takeIf { playableOrFree(it) }
                dragCard?.let { c ->
                    val r = handRects[c] ?: return@let
                    val liftBy = if (scene?.seats?.get(0)?.lifted?.contains(c) == true) lift else 0f
                    dragOffsetX = event.x - r.left
                    dragOffsetY = event.y - (r.top - liftBy)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val card = dragCard
                if (card != null && !dragging && hypot(event.x - downX, event.y - downY) > touchSlop * 1.5f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    dragRect.set(event.x - dragOffsetX, event.y - dragOffsetY,
                        event.x - dragOffsetX + handW, event.y - dragOffsetY + handH)
                    overZone = event.y < handBandTop
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val card = dragCard
                if (dragging && card != null) {
                    dragging = false
                    // Lâchée au-dessus de la main : la carte part de là où elle est vers la table.
                    if (event.y < handBandTop) {
                        dropFrom = card to RectF(dragRect)
                        onCardDrop?.invoke(card)
                        dropFrom = null
                    }
                    dragCard = null
                    overZone = false
                    invalidate()
                } else if (hypot(event.x - downX, event.y - downY) <= touchSlop * 2) {
                    performClick()
                    if (!busy) tapAt(event.x, event.y)
                }
                dragCard = null
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                dragCard = null
                overZone = false
                invalidate()
            }
        }
        return true
    }

    /** Une carte de la main n'est glissable que si le jeu la permet (ou s'il ne grise rien). */
    private fun playableOrFree(card: PlayingCard): Boolean {
        val playable = scene?.seats?.get(0)?.playable ?: return true
        return card in playable
    }

    /** La carte de ma main sous le doigt : celle du dessus d'abord (la dernière dessinée). */
    private fun cardAt(x: Float, y: Float): PlayingCard? {
        val me = scene?.seats?.get(0) ?: return null
        for ((card, r) in handRects.entries.reversed()) {
            rect.set(r)
            if (card in me.lifted) rect.offset(0f, -lift)
            if (rect.contains(x, y)) return card
        }
        return null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun tapAt(x: Float, y: Float) {
        val s = scene ?: return
        cardAt(x, y)?.let { onCardTap?.invoke(it); return }
        if (s.pile != null && stockRect.contains(x, y)) onStockTap?.invoke()
        else if (s.pile != null && discardRect.contains(x, y)) onDiscardTap?.invoke()
    }

    private companion object {
        const val BOTTOM = 0
        const val LEFT = 1
        const val TOP = 2
        const val RIGHT = 3
        const val PLAY_MS = 260L
        const val DRAW_MS = 240L
        const val TAKE_PAUSE_MS = 650L
        const val TAKE_MS = 330L
        const val LAY_PAUSE_MS = 120L
    }
}
