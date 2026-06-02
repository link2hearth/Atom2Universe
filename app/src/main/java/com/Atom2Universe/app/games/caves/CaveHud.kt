package com.Atom2Universe.app.games.caves

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CaveHud(private val activity: CaveActivity) {

    private val res get() = activity.resources
    private val dp  get() = res.displayMetrics.density

    // ── Slots hotbar ──────────────────────────────────────────────────────────
    val slotViews  = arrayOfNulls<FrameLayout>(CaveActivity.ACTIVE_SIZE)
    val slotColors = arrayOfNulls<View>(CaveActivity.ACTIVE_SIZE)
    val slotCounts = arrayOfNulls<TextView>(CaveActivity.ACTIVE_SIZE)

    // ── Overlay active bar ────────────────────────────────────────────────────
    val overlayActiveFrames = arrayOfNulls<FrameLayout>(CaveActivity.ACTIVE_SIZE)
    val overlayActiveColors = arrayOfNulls<View>(CaveActivity.ACTIVE_SIZE)
    val overlayActiveCounts = arrayOfNulls<TextView>(CaveActivity.ACTIVE_SIZE)

    // ── HP / Bouclier ─────────────────────────────────────────────────────────
    var hpBarFg: View? = null
    var hpText: TextView? = null
    var hpBarMaxWidth = 0
    var shieldBarFg: View? = null
    var shieldContainer: View? = null


    // ── Hotbar ────────────────────────────────────────────────────────────────

    fun buildHotbarUI(container: LinearLayout) {
        val sz = (52 * dp).toInt()
        container.gravity = Gravity.CENTER
        repeat(CaveActivity.ACTIVE_SIZE) { i ->
            val slot = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(sz, sz).also { it.setMargins(2, 2, 2, 2) }
                background = slotDrawable(null, false)
                setOnClickListener {
                    if (activity.invOverlay.visibility == View.VISIBLE)
                        activity.invManager.onOverlayActiveSlotClick(i)
                    else
                        activity.renderer.selectSlot(i)
                }
            }
            val colorDot = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).also { it.gravity = Gravity.CENTER }
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            val countTv = TextView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.gravity = Gravity.BOTTOM or Gravity.END }
                setTextColor(Color.WHITE); textSize = 9f
                setPadding(0, 0, (2 * dp).toInt(), (1 * dp).toInt())
            }
            slot.addView(colorDot); slot.addView(countTv); container.addView(slot)
            slotViews[i] = slot; slotColors[i] = colorDot; slotCounts[i] = countTv
        }
        container.addView(Button(activity).apply {
            layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), (52 * dp).toInt())
                .also { it.setMargins(4, 2, 2, 2) }
            text = "🎒"; textSize = 18f; setBackgroundColor(0x55FFFFFF); setTextColor(Color.WHITE)
            setOnClickListener { activity.invManager.openInventory() }
        })
    }

    fun updateHotbarUI(slots: Array<Short?>, selected: Int) {
        slots.forEachIndexed { i, type ->
            val count = if (type != null) activity.renderer.inventory[type] ?: 0 else 0
            val eff   = if (count > 0) type else null
            slotViews[i]?.background = slotDrawable(eff, i == selected)
            slotColors[i]?.background = if (eff != null) activity.blockDrawable(eff, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }
            slotCounts[i]?.text = if (eff != null) count.toString() else ""
        }
    }

    fun slotDrawable(type: Short?, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (type != null) 0x33FFFFFF else 0x22FFFFFF)
            setStroke(if (selected) (2 * dp).toInt() else (1 * dp).toInt(),
                if (selected) Color.WHITE else 0x55FFFFFF)
            cornerRadius = 4 * dp
        }

    // ── Hotbar highlighting pendant l'inventaire ──────────────────────────────

    fun updateHotbarForInventory() {
        val inv = activity.invManager
        val base = inv.hotbarBase()
        for (i in 0 until CaveActivity.ACTIVE_SIZE) {
            val invIdx = base + i
            val type  = inv.invSlots.getOrNull(invIdx)
            val count = if (type != null) activity.renderer.inventory[type] ?: 0 else 0
            val eff   = if (count > 0) type else null
            val isSel    = invIdx == inv.selectedSlotIdx
            val isCursor = inv.invGpZone == InvGpZone.HOTBAR && invIdx == inv.invGpCursor
            slotViews[i]?.background  = when {
                isSel    -> overlaySlotDrawable(selected = true)
                isCursor -> overlaySlotDrawable(selected = false, cursor = true)
                else     -> slotDrawable(eff, false)
            }
            slotColors[i]?.background = if (eff != null) activity.blockDrawable(eff, 3f)
                else GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT); cornerRadius = 3 * dp }
            slotCounts[i]?.text = if (eff != null) count.toString() else ""
        }
    }

    // ── Overlay active bar (conservé pour compatibilité, non utilisé) ─────────

    fun buildOverlayActiveBar(container: LinearLayout) {
        val inv = activity.invManager
        repeat(CaveActivity.ACTIVE_SIZE) { i ->
            val frame = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .also { it.setMargins(2, 2, 2, 2) }
                background = overlaySlotDrawable(false)
            }
            val colorDot = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
                    .also { it.gravity = Gravity.CENTER }
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            val countTv = TextView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.gravity = Gravity.BOTTOM or Gravity.END }
                setTextColor(Color.WHITE); textSize = 11f
                setPadding(0, 0, (3 * dp).toInt(), (2 * dp).toInt())
            }
            frame.addView(colorDot); frame.addView(countTv)
            container.addView(frame)
            overlayActiveFrames[i] = frame
            overlayActiveColors[i] = colorDot
            overlayActiveCounts[i] = countTv
            frame.setOnClickListener { inv.onOverlayActiveSlotClick(i) }
            frame.setOnLongClickListener { inv.startSlotDrag(it, inv.hotbarBase() + i); true }
            frame.setOnDragListener(inv.makeSlotDragListener { inv.hotbarBase() + i })
        }
    }

    fun updateActiveBarOverlay() {
        val inv = activity.invManager
        val base = inv.hotbarBase()
        for (i in 0 until CaveActivity.ACTIVE_SIZE) {
            val invIdx = base + i
            val type  = inv.invSlots.getOrNull(invIdx)
            val count = if (type != null) activity.renderer.inventory[type] ?: 0 else 0
            val eff   = if (count > 0) type else null
            val isSel    = invIdx == inv.selectedSlotIdx
            val isCursor = inv.invGpZone == InvGpZone.HOTBAR && invIdx == inv.invGpCursor
            overlayActiveFrames[i]?.background = overlaySlotDrawable(isSel, isCursor)
            overlayActiveColors[i]?.background = if (eff != null) activity.blockDrawable(eff, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }
            overlayActiveCounts[i]?.text = if (eff != null) count.toString() else ""
        }
    }

    fun updateActiveSlotHighlights() {
        val inv = activity.invManager
        for (i in 0 until CaveActivity.ACTIVE_SIZE) {
            val isSel = (inv.hotbarBase() + i) == inv.selectedSlotIdx
            overlayActiveFrames[i]?.background = overlaySlotDrawable(isSel)
        }
    }

    fun overlaySlotDrawable(selected: Boolean, cursor: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(when { selected -> 0x33FFDD00.toInt(); cursor -> 0x3300DDFF.toInt(); else -> 0x66FFFFFF.toInt() })
            setStroke(if (selected || cursor) (2 * dp).toInt() else (1 * dp).toInt(),
                when { selected -> 0xFFFFDD00.toInt(); cursor -> 0xFF00DDFF.toInt(); else -> 0xAAFFFFFF.toInt() })
            cornerRadius = 4 * dp
        }

    // ── Barre HP / Bouclier ───────────────────────────────────────────────────

    fun buildHealthBar(root: FrameLayout) {
        val bW = (80 * dp).toInt(); val bH = (10 * dp).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                .also { it.gravity = Gravity.TOP or Gravity.START; it.setMargins((12 * dp).toInt(), (18 * dp).toInt(), 0, 0) }
            setPadding((6 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(0x99000000.toInt()); cornerRadius = 6 * dp }
        }
        val hpRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val heartTv = TextView(activity).apply { text = "❤"; textSize = 12f; setTextColor(0xFFFF4444.toInt()); setPadding(0, 0, (4 * dp).toInt(), 0) }
        val barFrame = FrameLayout(activity).apply { layoutParams = LinearLayout.LayoutParams(bW, bH).also { it.gravity = Gravity.CENTER_VERTICAL } }
        val barBg = View(activity).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0x88440000.toInt()); cornerRadius = 3 * dp } }
        val barFg = View(activity).apply { layoutParams = FrameLayout.LayoutParams(bW, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0xFF22CC44.toInt()); cornerRadius = 3 * dp } }
        barFrame.addView(barBg); barFrame.addView(barFg)
        val tv = TextView(activity).apply { text = "20/20"; textSize = 9f; setTextColor(Color.WHITE); setPadding((4 * dp).toInt(), 0, 0, 0) }
        hpRow.addView(heartTv); hpRow.addView(barFrame); hpRow.addView(tv)
        container.addView(hpRow)
        hpBarFg = barFg; hpText = tv; hpBarMaxWidth = bW

        val shRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (2 * dp).toInt() }
        }
        val shIconTv = TextView(activity).apply { text = "🛡"; textSize = 10f; setPadding(0, 0, (3 * dp).toInt(), 0) }
        val shFrame = FrameLayout(activity).apply { layoutParams = LinearLayout.LayoutParams(bW, bH).also { it.gravity = Gravity.CENTER_VERTICAL } }
        val shBg = View(activity).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0x88002244.toInt()); cornerRadius = 3 * dp } }
        val shFg = View(activity).apply { layoutParams = FrameLayout.LayoutParams(bW, FrameLayout.LayoutParams.MATCH_PARENT); background = GradientDrawable().apply { setColor(0xFF0099FF.toInt()); cornerRadius = 3 * dp } }
        shFrame.addView(shBg); shFrame.addView(shFg)
        shRow.addView(shIconTv); shRow.addView(shFrame)
        container.addView(shRow)
        shieldBarFg = shFg; shieldContainer = shRow
        root.addView(container)
    }

    fun updateHealthBar(hp: Int, maxHp: Int) {
        val frac = hp.toFloat() / maxHp.coerceAtLeast(1)
        hpBarFg?.layoutParams = (hpBarFg?.layoutParams as? FrameLayout.LayoutParams)
            ?.also { it.width = (hpBarMaxWidth * frac).toInt().coerceAtLeast(0) }
        hpBarFg?.requestLayout()
        hpText?.text = "$hp/$maxHp"
        (hpBarFg?.background as? GradientDrawable)?.setColor(
            when { frac > 0.6f -> 0xFF22CC44.toInt(); frac > 0.3f -> 0xFFDDAA00.toInt(); else -> 0xFFCC2222.toInt() }
        )
    }

    fun updateShieldBar(current: Int, max: Int) {
        if (max <= 0) { shieldContainer?.visibility = View.GONE; return }
        shieldContainer?.visibility = View.VISIBLE
        val frac = current.toFloat() / max.coerceAtLeast(1)
        shieldBarFg?.layoutParams = (shieldBarFg?.layoutParams as? FrameLayout.LayoutParams)
            ?.also { it.width = (hpBarMaxWidth * frac).toInt().coerceAtLeast(0) }
        shieldBarFg?.requestLayout()
    }


    // ── Panneau capture de structure (mode créatif uniquement) ────────────────

    private var btnStructA: Button? = null
    private var btnStructB: Button? = null
    private var tvStructDims: android.widget.TextView? = null
    private var btnStructSave: Button? = null
    private var structPanel: LinearLayout? = null
    private var structPanelExpanded = false

    fun buildStructurePanel(root: FrameLayout) {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.TOP or Gravity.END; it.setMargins(0, (56 * dp).toInt(), (8 * dp).toInt(), 0) }
            setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xCC001122.toInt()); cornerRadius = 8 * dp
                setStroke((1 * dp).toInt(), 0x6600FF88.toInt())
            }
            visibility = View.GONE
        }
        structPanel = panel

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(android.widget.TextView(activity).apply {
            text = "📐 Structure"; textSize = 11f; setTextColor(0xFF00FF88.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        panel.addView(header)

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (4 * dp).toInt() }
        }
        fun mkBtn(label: String) = Button(activity).apply {
            text = label; textSize = 10f; setTextColor(Color.WHITE)
            setBackgroundColor(0x8800FF88.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (32 * dp).toInt()
            ).also { it.setMargins(0, 0, (4 * dp).toInt(), 0) }
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }

        val bA = mkBtn("A ?"); btnStructA = bA
        val bB = mkBtn("B ?"); btnStructB = bB
        row.addView(bA); row.addView(bB)

        val tvDims = android.widget.TextView(activity).apply {
            text = ""; textSize = 9f; setTextColor(0xAAFFFFFF.toInt())
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, 0, (6 * dp).toInt(), 0) }
        }
        tvStructDims = tvDims
        row.addView(tvDims)

        val bSave = mkBtn("💾").also {
            it.setBackgroundColor(0x880044FF.toInt()); it.visibility = View.GONE
        }
        btnStructSave = bSave
        row.addView(bSave)
        panel.addView(row)
        root.addView(panel)

        bA.setOnClickListener { onCornerAPressed() }
        bB.setOnClickListener { onCornerBPressed() }
        bSave.setOnClickListener { onSaveStructurePressed() }
    }

    fun showStructurePanel(show: Boolean) {
        structPanel?.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            activity.renderer.structCornerA = null
            activity.renderer.structCornerB = null
            refreshStructureButtons()
        }
    }

    private fun onCornerAPressed() {
        val b = activity.renderer.currentLookAtBlock ?: return
        activity.renderer.structCornerA = b
        refreshStructureButtons()
    }

    private fun onCornerBPressed() {
        val b = activity.renderer.currentLookAtBlock ?: return
        activity.renderer.structCornerB = b
        refreshStructureButtons()
    }

    private fun refreshStructureButtons() {
        val a = activity.renderer.structCornerA
        val b = activity.renderer.structCornerB
        btnStructA?.text = if (a != null) "A ✓" else "A ?"
        btnStructB?.text = if (b != null) "B ✓" else "B ?"
        if (a != null && b != null) {
            val sx = kotlin.math.abs(a.first  - b.first)  + 1
            val sy = kotlin.math.abs(a.second - b.second) + 1
            val sz = kotlin.math.abs(a.third  - b.third)  + 1
            tvStructDims?.text = "${sx}×${sy}×${sz}"
            tvStructDims?.visibility = View.VISIBLE
            btnStructSave?.visibility = View.VISIBLE
        } else {
            tvStructDims?.visibility = View.GONE
            btnStructSave?.visibility = View.GONE
        }
    }

    private fun onSaveStructurePressed() {
        val a = activity.renderer.structCornerA ?: return
        val b = activity.renderer.structCornerB ?: return

        if (!com.Atom2Universe.app.games.caves.world.StructureCapture.hasStorageAccess()) {
            android.app.AlertDialog.Builder(activity)
                .setTitle("Accès stockage requis")
                .setMessage("Autorisez l'accès à tous les fichiers pour écrire dans Documents/cave_world/.")
                .setPositiveButton("Ouvrir Paramètres") { _, _ ->
                    com.Atom2Universe.app.games.caves.world.StructureCapture.openStorageSettings(activity)
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }

        val input = android.widget.EditText(activity).apply {
            hint = "nom_de_structure"; setSingleLine(true)
            setText("structure_${System.currentTimeMillis() / 1000}")
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle("💾 Sauvegarder la structure")
            .setMessage("→ Documents/cave_world/structures/")
            .setView(input)
            .setPositiveButton("Sauvegarder") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "structure" }
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val def = com.Atom2Universe.app.games.caves.world.StructureCapture.capture(
                        activity.renderer.world, name, a, b)
                    val file = com.Atom2Universe.app.games.caves.world.StructureCapture.save(def)
                    com.Atom2Universe.app.games.caves.world.StructureRegistry.addUserStructure(def)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(activity,
                            "✓ ${file.name}\nDocuments/cave_world/structures/",
                            android.widget.Toast.LENGTH_LONG).show()
                        activity.renderer.structCornerA = null
                        activity.renderer.structCornerB = null
                        refreshStructureButtons()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
