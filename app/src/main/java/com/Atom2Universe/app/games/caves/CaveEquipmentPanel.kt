package com.Atom2Universe.app.games.caves

import android.content.ClipData
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.node.ForgedEquipment as G
import com.Atom2Universe.app.games.caves.world.MineralProgression
import kotlin.math.roundToInt

/** Local drag tokens never travel through the inventory's stack/shortcut drag handlers. */
internal class CaveEquipmentPanel(private val activity: CaveActivity) : LinearLayout(activity) {
    private enum class Slot(val piece: G.Slot?, val label: Int, val icon: String, val x: Float, val y: Float) {
        HEAD(G.Slot.HELMET, R.string.cave_gear_slot_helmet, "helmet", .5f, .12f),
        CHEST(G.Slot.CHEST, R.string.cave_gear_slot_chest, "armor", .17f, .37f),
        LEGS(G.Slot.LEGS, R.string.cave_gear_slot_legs, "legs", .17f, .68f),
        FEET(G.Slot.BOOTS, R.string.cave_gear_slot_boots, "boots", .5f, .88f),
        SHIELD(null, R.string.cave_equipment_shield, "shield", .83f, .37f),
        SUIT(null, R.string.cave_equipment_suit, "suit", .83f, .68f)
    }
    private data class Transfer(val owner: CaveEquipmentPanel, val id: Short, val source: Slot? = null)
    private fun dp(n: Int) = CaveUiStyle.dp(activity, n)
    private fun column() = LinearLayout(activity).apply { orientation = VERTICAL }
    private fun text(res: Int, size: Float = 12f) = TextView(activity).apply {
        setText(res); textSize = size; setTextColor(CaveUiStyle.TEXT)
        setPadding(dp(8), dp(5), dp(8), dp(5))
    }
    private val inventoryTitle = text(R.string.cave_equipment_available, 15f)
    private val empty = text(R.string.cave_equipment_empty_list)
    private val hint = text(R.string.cave_equipment_drag_hint, 11f)
    private val totals = text(R.string.cave_gear_equipped, 11f)
    private val grid = GridLayoutManager(activity, 2)
    private val list = RecyclerView(activity).apply {
        layoutManager = grid; clipToPadding = false; setPadding(dp(3), dp(3), dp(3), dp(3))
    }
    private val bag = column()
    private val board = EquipmentBoard()
    private var ids = emptyList<Short>()
    private var chosen: Transfer? = null
    private var dragging: Transfer? = null
    private var busy = false
    private var describe: (Short) -> String = { "" }
    private var bubble: PopupWindow? = null
    private var equip: (Short) -> Unit = {}
    private var remove: (G.Slot?, Boolean) -> Unit = { _, _ -> }
    private val adapter = ArmorAdapter()
    private val slots = Slot.entries.associateWith { CaveItemTile(activity) }

    init {
        orientation = HORIZONTAL
        setPadding(dp(3), dp(5), dp(3), dp(3))
        bag.background = CaveUiStyle.panel(activity, CaveUiStyle.SURFACE)
        bag.addView(inventoryTitle); bag.addView(empty)
        bag.addView(list, LayoutParams(-1, 0, 1f))
        bag.addView(text(R.string.cave_equipment_return_hint, 11f).apply {
            setTextColor(CaveUiStyle.MUTED); setOnClickListener { returnChosen() }; isFocusable = true
            setOnDragListener { _, event -> handleBagDrag(event) }
        })
        addView(bag, LayoutParams(0, -1, 1f).apply { marginEnd = dp(8) })
        addView(column().apply {
            background = CaveUiStyle.panel(activity, 0xFF1B2B26.toInt())
            addView(text(R.string.cave_gear_equipped, 15f))
            addView(board, LayoutParams(-1, 0, 1f))
            addView(totals.apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(hint)
        }, LayoutParams(0, -1, 1.25f))
        list.adapter = adapter
        list.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val columns = ((r - l) / dp(86)).coerceIn(1, 4)
            if (grid.spanCount != columns) grid.spanCount = columns
        }
        inventoryTitle.setOnClickListener { returnChosen() }
        for ((slot, tile) in slots) {
            board.addView(tile)
            tile.setOnClickListener {
                val transfer = chosen
                if (transfer != null && transfer.source == null) place(slot, transfer)
                else {
                    chosen = equipped(slot)?.let { Transfer(this, it, slot) }
                    updateHighlights()
                    equipped(slot)?.let { showInfo(tile, it) }
                }
            }
            directDrag(tile, false) { equipped(slot)?.let { Transfer(this, it, slot) } }
            tile.setOnDragListener { _, event -> handleSlotDrag(slot, event) }
            tile.setOnFocusChangeListener { _, _ -> updateHighlights() }
        }
        for (view in listOf(bag, list, inventoryTitle, empty)) view.setOnDragListener { _, e -> handleBagDrag(e) }
    }

    fun refresh(describe: (Short) -> String, equip: (Short) -> Unit, remove: (G.Slot?, Boolean) -> Unit) {
        this.describe = describe
        bubble?.dismiss()
        this.equip = equip; this.remove = remove; busy = false; chosen = null; dragging = null
        val renderer = activity.renderer
        val owned = renderer.inventory.filterValues { it > 0 }.keys
        ids = (if (activity.isCreative) (BlockRegistry.creativeList() + owned).distinct() else owned)
            .filter { InventoryCategory.ARMOR.matches(it) }
            .sortedWith(compareBy<Short> { G.template(it)?.slot?.ordinal ?: G.Slot.entries.size }.thenBy { activity.blockName(it) })
        empty.visibility = if (ids.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
        val combat = renderer.expeditionCombat
        val reduction = (combat.reduction(MineralProgression.stage(renderer.camera.playerY)) * 100).roundToInt()
        totals.text = activity.getString(R.string.cave_equipment_compact_totals, renderer.playerNode.maxHp, reduction)
        totals.tooltipText = activity.getString(R.string.cave_gear_totals, renderer.playerNode.maxHp, reduction)
        updateHighlights(); board.invalidate()
    }
    private fun equipped(slot: Slot): Short? = activity.renderer.expeditionCombat.let { combat ->
        when (slot) { Slot.SUIT -> combat.armor; Slot.SHIELD -> if (combat.shield) E.SHIELD else null
            else -> combat.equipped(requireNotNull(slot.piece)) }
    }
    private fun target(id: Short): Slot? = if (id == E.SHIELD) Slot.SHIELD
        else G.template(id)?.slot?.let { piece -> Slot.entries.first { it.piece == piece } }
            ?: Slot.SUIT.takeIf { E.armor(id) > 0f }
    private fun startDrag(view: View, transfer: Transfer): Boolean {
        if (busy) return false
        bubble?.dismiss()
        chosen = transfer
        val started = view.startDragAndDrop(ClipData.newPlainText("", activity.blockName(transfer.id)),
            View.DragShadowBuilder(view), transfer, 0)
        if (!started) chosen = null
        updateHighlights(); return started
    }
    /** Horizontal motion from the bag picks up the item; vertical motion stays with the list. */
    private fun directDrag(view: View, scrollable: Boolean, transfer: () -> Transfer?) {
        val slop = ViewConfiguration.get(activity).scaledTouchSlop
        var startX = 0f; var startY = 0f; var started = false; var scrolling = false
        view.setOnLongClickListener(null)
        view.isLongClickable = false
        view.tooltipText = null
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY; started = false; scrolling = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(event.rawX - startX)
                    val dy = kotlin.math.abs(event.rawY - startY)
                    if (!started && !scrolling && maxOf(dx, dy) > slop) {
                        if (scrollable && dy > dx) scrolling = true
                        else transfer()?.let { token ->
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            started = startDrag(view, token)
                            if (started) { view.isPressed = false; view.cancelLongPress() }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (started) { view.isPressed = false; return@setOnTouchListener true }
                }
            }
            started
        }
    }
    private fun showInfo(anchor: View, id: Short) {
        bubble?.dismiss()
        val content = column().apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(ImageView(activity).apply { setImageDrawable(activity.blockDrawable(id, 0f)) }, LayoutParams(-1, dp(60)))
            addView(text(R.string.cave_ui_details, 16f).apply { text = activity.blockName(id); setTypeface(typeface, 1) })
            addView(text(R.string.cave_ui_details).apply { text = describe(id) })
        }
        val metrics = activity.resources.displayMetrics
        val width = minOf(dp(300), (metrics.widthPixels * .85f).toInt())
        val limit = (metrics.heightPixels * .65f).toInt()
        val scroll = ScrollView(activity).apply { addView(content) }
        scroll.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
        val height = scroll.measuredHeight.coerceAtMost(limit)
        val location = IntArray(2); anchor.getLocationOnScreen(location)
        val x = (location[0] + anchor.width / 2 - width / 2).coerceIn(dp(8), (metrics.widthPixels - width - dp(8)).coerceAtLeast(dp(8)))
        val y = (location[1] - height - dp(4)).coerceIn(dp(8), (metrics.heightPixels - height - dp(8)).coerceAtLeast(dp(8)))
        bubble = PopupWindow(scroll, width, height, true).apply {
            setBackgroundDrawable(CaveUiStyle.bubble(activity)); elevation = dp(10).toFloat()
            isOutsideTouchable = true; inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            showAtLocation(activity.invOverlay, Gravity.TOP or Gravity.LEFT, x, y)
        }
        CaveUiStyle.openBubble(scroll)
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) bubble?.dismiss()
    }
    override fun onDetachedFromWindow() { bubble?.dismiss(); super.onDetachedFromWindow() }
    private fun token(event: DragEvent) = (event.localState as? Transfer)?.takeIf { it.owner === this }
    private fun place(slot: Slot, transfer: Transfer): Boolean {
        if (busy || transfer.source != null || target(transfer.id) != slot || transfer.id !in ids) return false
        busy = true; chosen = null; equip(transfer.id); return true
    }
    private fun returnChosen(): Boolean {
        val transfer = chosen ?: return false
        val source = transfer.source ?: return false
        if (busy || equipped(source) != transfer.id) return false
        busy = true; chosen = null; remove(source.piece, source == Slot.SHIELD); return true
    }
    private fun handleSlotDrag(slot: Slot, event: DragEvent): Boolean {
        val transfer = token(event) ?: return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                dragging = transfer; updateHighlights()
                return !busy && transfer.source == null && target(transfer.id) == slot
            }
            DragEvent.ACTION_DRAG_ENTERED -> updateHighlights(slot)
            DragEvent.ACTION_DRAG_EXITED -> updateHighlights()
            DragEvent.ACTION_DROP -> return place(slot, transfer)
            DragEvent.ACTION_DRAG_ENDED -> { dragging = null; chosen = null; updateHighlights() }
        }
        return true
    }
    private fun handleBagDrag(event: DragEvent): Boolean {
        val transfer = token(event) ?: return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return !busy && transfer.source != null
            DragEvent.ACTION_DRAG_ENTERED -> bag.background = CaveUiStyle.panel(activity, CaveUiStyle.SELECTED, CaveUiStyle.ACCENT, true)
            DragEvent.ACTION_DRAG_EXITED -> bag.background = CaveUiStyle.panel(activity, CaveUiStyle.SURFACE)
            DragEvent.ACTION_DROP -> { chosen = transfer; return returnChosen() }
            DragEvent.ACTION_DRAG_ENDED -> {
                dragging = null; chosen = null
                bag.background = CaveUiStyle.panel(activity, CaveUiStyle.SURFACE); updateHighlights()
            }
        }
        return true
    }
    private fun updateHighlights(hover: Slot? = null) {
        val transfer = dragging ?: chosen
        for ((slot, tile) in slots) {
            val id = equipped(slot)
            val compatible = transfer != null && transfer.source == null && target(transfer.id) == slot
            val active = slot == hover || compatible || transfer?.source == slot || tile.hasFocus()
            val title = activity.getString(slot.label)
            tile.bind(id?.let { activity.blockDrawable(it, 0f) } ?: CaveActionDrawable(slot.icon), title, 0, selected = active)
            tile.alpha = if (transfer != null && transfer.source == null && !compatible) .45f else 1f
            tile.contentDescription = if (id == null) activity.getString(R.string.cave_gear_empty, title)
                else activity.getString(R.string.cave_equipment_slot_item, title, activity.blockName(id))
            tile.tooltipText = null
            ViewCompat.replaceAccessibilityAction(tile, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS,
                activity.getString(R.string.cave_equipment_remove_piece)) { _, _ ->
                if (id == null) false else { chosen = Transfer(this, id, slot); returnChosen() }
            }
        }
        hint.setText(if (transfer?.source != null) R.string.cave_equipment_return_hint else R.string.cave_equipment_drag_hint)
        board.invalidate()
    }
    fun moveFocus(direction: Int) {
        val focus = findFocus()
        if (focus != null) focus.focusSearch(direction)?.requestFocus()
        else list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            ?: slots.values.firstOrNull()?.requestFocus()
    }
    private inner class ArmorAdapter : RecyclerView.Adapter<ArmorHolder>() {
        override fun getItemCount() = ids.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ArmorHolder(CaveItemTile(activity).apply {
            layoutParams = RecyclerView.LayoutParams(-1, dp(84)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        })
        override fun onBindViewHolder(holder: ArmorHolder, position: Int) {
            val id = ids[position]
            holder.tile.bind(activity.blockDrawable(id, 0f), activity.blockName(id),
                if (activity.isCreative) 0 else activity.renderer.inventory[id] ?: 0, selected = chosen?.id == id)
            holder.tile.setOnClickListener {
                if (!busy) { chosen = Transfer(this@CaveEquipmentPanel, id); updateHighlights(); showInfo(holder.tile, id) }
            }
            directDrag(holder.tile, true) { Transfer(this@CaveEquipmentPanel, id) }
            holder.tile.setOnDragListener { _, event -> handleBagDrag(event) }
        }
    }
    private class ArmorHolder(val tile: CaveItemTile) : RecyclerView.ViewHolder(tile)

    /** Slots stay square and anchored to the body, even on a narrow screen. */
    private inner class EquipmentBoard : FrameLayout(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { setWillNotDraw(false); minimumHeight = dp(180) }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val edge = minOf(dp(76), (measuredWidth * .29f).toInt(), (measuredHeight * .29f).toInt()).coerceAtLeast(1)
            for (i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(edge, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(edge, MeasureSpec.EXACTLY))
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            for ((slot, tile) in slots) {
                val x = (width * slot.x - tile.measuredWidth / 2).roundToInt()
                val y = (height * slot.y - tile.measuredHeight / 2).roundToInt().coerceIn(0, (height - tile.measuredHeight).coerceAtLeast(0))
                tile.layout(x, y, x + tile.measuredWidth, y + tile.measuredHeight)
            }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width == 0 || height == 0) return
            paint.shader = RadialGradient(width / 2f, height / 2f, maxOf(width, height) * .6f,
                intArrayOf(0xFF385547.toInt(), 0x001B2B26), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
            canvas.save(); canvas.translate(width * .5f, height * .49f)
            val unit = minOf(width * .0058f, height * .0032f)
            canvas.scale(unit, unit)
            val armor = activity.renderer.expeditionCombat.armorColor
            paint.color = if (armor != null) (armor and 0xFFFFFF) or 0x99000000.toInt() else 0xFF627B69.toInt()
            canvas.drawRoundRect(-19f, -74f, 19f, -35f, 8f, 8f, paint)
            canvas.drawRoundRect(-26f, -29f, 26f, 42f, 9f, 9f, paint)
            canvas.drawRoundRect(-45f, -27f, -30f, 43f, 7f, 7f, paint)
            canvas.drawRoundRect(30f, -27f, 45f, 43f, 7f, 7f, paint)
            canvas.drawRoundRect(-25f, 48f, -4f, 115f, 7f, 7f, paint)
            canvas.drawRoundRect(4f, 48f, 25f, 115f, 7f, 7f, paint)
            canvas.restore()
        }
    }
}
