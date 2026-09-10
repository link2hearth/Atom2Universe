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
    fun controlIcon(button: Button, kind: String, label: String) {
        button.text = ""; button.contentDescription = label; button.backgroundTintList = null
        val inset = android.graphics.drawable.InsetDrawable(CaveActionDrawable(kind), CaveUiStyle.dp(activity, 9))
        button.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x447DAD8B),
            android.graphics.drawable.LayerDrawable(arrayOf(CaveUiStyle.panel(activity, 0x66293F33, 0x6686A38C), inset)), null)
    }

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
    private var sprintIndicator: TextView? = null
    private var vitals: CaveVitalsView? = null
    private var quickbarWidth = 0


    // ── Hotbar ────────────────────────────────────────────────────────────────

    fun buildHotbarUI(container: LinearLayout) {
        val sz = (((res.displayMetrics.widthPixels / dp - 88) / CaveActivity.ACTIVE_SIZE).coerceIn(40f, 52f) * dp).toInt()
        container.gravity = Gravity.CENTER
        container.background = CaveUiStyle.panel(activity, 0x7822382D, 0x6686A38C)
        container.setPadding((5*dp).toInt(), (3*dp).toInt(), (5*dp).toInt(), (3*dp).toInt())
        quickbarWidth = CaveActivity.ACTIVE_SIZE * (sz + (4*dp).toInt()) + (58*dp).toInt()
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            vitals?.layoutParams = vitals?.layoutParams?.also { it.width = (container.width - 22*dp).toInt().coerceAtLeast(1) }
        }
        repeat(CaveActivity.ACTIVE_SIZE) { i ->
            val slot = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(sz, sz).also { it.setMargins((2*dp).toInt(), 0, (2*dp).toInt(), 0) }
                background = slotDrawable(null, false)
                setOnClickListener {
                    if (activity.invOverlay.visibility == View.VISIBLE)
                        activity.invManager.onOverlayActiveSlotClick(i)
                    else
                        activity.renderer.selectSlot(i)
                }
            }
            val colorDot = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).also { it.gravity = Gravity.CENTER }
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            val countTv = TextView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.gravity = Gravity.BOTTOM or Gravity.END }
                setTextColor(Color.WHITE); textSize = 9f
                setPadding(0, 0, (2 * dp).toInt(), (1 * dp).toInt())
            }
            val number = TextView(activity).apply {
                text = (i + 1).toString(); textSize = 8f; setTextColor(0xA9DBE8D6.toInt())
                setPadding((4*dp).toInt(), (2*dp).toInt(), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            slot.isFocusable = true
            countTv.setShadowLayer(2*dp,0f,dp,0xFF203329.toInt())
            slot.addView(colorDot); slot.addView(number); slot.addView(countTv); container.addView(slot)
            slotViews[i] = slot; slotColors[i] = colorDot; slotCounts[i] = countTv
        }
        container.addView(android.widget.ImageButton(activity).apply {
            layoutParams = LinearLayout.LayoutParams((48*dp).toInt(), (48*dp).toInt()).also { it.marginStart = (6*dp).toInt() }
            background = CaveUiStyle.panel(activity, 0x99718D70.toInt(), CaveUiStyle.ACCENT)
            setPadding((10*dp).toInt(),(10*dp).toInt(),(10*dp).toInt(),(10*dp).toInt())
            setImageDrawable(CaveActionDrawable("bag")); contentDescription = activity.getString(R.string.cave_ui_bag)
            setOnClickListener { activity.invManager.openInventory() }
        })
    }

    fun updateHotbarUI(slots: Array<Short?>, selected: Int) {
        slots.forEachIndexed { i, type ->
            val count    = if (type != null) activity.renderer.inventory[type] ?: 0 else 0
            val eff      = if (count > 0) type else null
            val isWeapon = eff != null && com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(eff)

            val instance = eff?.let { com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(it) }
            slotViews[i]?.background = CaveUiStyle.panel(activity,
                if (i == selected) 0xAE688365.toInt() else 0x55405748,
                if (i == selected) CaveUiStyle.ACCENT else instance?.let { rarityColor(it.rarity) } ?: 0x7786A38C,
                i == selected)
            slotViews[i]?.contentDescription = activity.getString(R.string.cave_ui_shortcut_description, i + 1,
                eff?.let { activity.blockName(it) } ?: activity.getString(R.string.cave_ui_empty_slot))
            slotColors[i]?.background = if (eff != null) activity.blockDrawable(eff, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }
            // Les armes n'empilent pas → pas de compteur
            slotCounts[i]?.text = if (eff != null && !isWeapon) count.toString() else ""
        }

        // Arme en main : affiché uniquement si le slot sélectionné contient une arme
        val selType = slots.getOrNull(selected)
        val selInstance = selType?.let { com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.get(it) }
        if (selType != null && com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry.isWeapon(selType)) {
            if (selInstance != null) showWeaponInHand(selInstance) else hideWeaponInHand()
        } else {
            hideWeaponInHand()
        }
    }

    private fun hideWeaponInHand() {
        weaponTooltipView?.visibility = View.GONE
    }

    fun slotDrawable(type: Short?, selected: Boolean): GradientDrawable =
        CaveUiStyle.panel(activity, if (selected) 0xAE688365.toInt() else 0x55405748,
            if (selected) CaveUiStyle.ACCENT else 0x7786A38C, selected)

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
                layoutParams = FrameLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt())
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
        CaveUiStyle.panel(activity, if (selected) CaveUiStyle.SELECTED else 0x55405748,
            if (selected) CaveUiStyle.ACCENT else if (cursor) 0xFFB0D5D3.toInt() else CaveUiStyle.BORDER, selected || cursor)

    // ── Barre HP / Bouclier ───────────────────────────────────────────────────

    fun buildHealthBar(root: FrameLayout) {
        vitals = CaveVitalsView(activity).also { view ->
            view.layoutParams = FrameLayout.LayoutParams(quickbarWidth.coerceAtLeast((420*dp).toInt()), (30*dp).toInt()).also {
                it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; it.bottomMargin = (73*dp).toInt()
            }
            root.addView(view)
        }
    }
    fun updateHealthBar(hp: Int, maxHp: Int) { vitals?.health(hp, maxHp) }
    fun updateSprintIndicator(active: Boolean) { vitals?.sprint(active) }
    fun updateShieldBar(current: Int, max: Int) { vitals?.shield(current, max) }

    // ── Arme en main (style Minecraft, bas-droite) ────────────────────────────

    private var weaponTooltipView: LinearLayout? = null
    private var weaponTooltipName: android.widget.TextView? = null
    private var weaponTooltipStats: android.widget.TextView? = null

    // ── Flash rouge de dégât ────────────────────────────────────────────────────

    private var damageFlash: View? = null

    /** Vignette radiale rouge plein écran, transparente au centre, animée en alpha. */
    fun buildDamageFlash(root: FrameLayout) {
        val dm = res.displayMetrics
        val v = View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = maxOf(dm.widthPixels, dm.heightPixels) * 0.62f
                // Centre transparent → bords rouges (vignette)
                colors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(190, 185, 12, 12))
            }
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        root.addView(v)
        damageFlash = v
    }

    /** Déclenche un flash : pleine intensité puis fondu vers 0. */
    fun flashDamage() {
        val v = damageFlash ?: return
        v.animate().cancel()
        v.alpha = 0.7f
        v.animate().alpha(0f).setDuration(420).start()
    }

    private var magazineText: android.widget.TextView? = null
    fun updateWeaponStatus(text: String) { magazineText?.text=text }

    fun buildWeaponInHand(root: FrameLayout) {
        // Nom de l'arme affiché dans la barre hotbar (collé à droite)
        val tooltip = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                it.setMargins(0, 0, 0, (108 * dp).toInt())
            }
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply { setColor(0x7622382D); cornerRadius = 12 * dp }
            visibility = View.GONE
        }
        val nameTv = android.widget.TextView(activity).apply {
            textSize = 11f; setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
        }
        val statsTv = android.widget.TextView(activity).apply {
            textSize = 9f; setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.END
        }
        tooltip.addView(nameTv); tooltip.addView(statsTv)
        magazineText=android.widget.TextView(activity).apply { textSize=10f;setTextColor(0xFFE5C987.toInt());gravity=Gravity.END }
        tooltip.addView(magazineText)
        root.addView(tooltip)
        weaponTooltipView = tooltip
        weaponTooltipName = nameTv
        weaponTooltipStats = statsTv
    }


    private fun showWeaponInHand(weapon: com.Atom2Universe.app.games.caves.node.ItemInstance) {
        val def = com.Atom2Universe.app.games.caves.node.ItemRegistry.get(weapon.defId) ?: return
        val rarityColor = rarityColor(weapon.rarity)

        // Tooltip
        val rarityLabel = weapon.rarity.name.lowercase().replaceFirstChar { it.uppercase() }
        val baseName = activity.weaponName(def.id)
        weaponTooltipName?.setTextColor(rarityColor)
        weaponTooltipName?.text = "[$rarityLabel] $baseName"
        val dmg = weapon.rolledDamage ?: 0
        val extra = weapon.rolledStats.entries.firstOrNull()
        weaponTooltipStats?.text = if (extra != null) "⚔ $dmg   ${extra.key.replace('_',' ')}: ${extra.value}%" else "⚔ $dmg"
        weaponTooltipView?.visibility = View.VISIBLE
    }

    private fun rarityColor(rarity: com.Atom2Universe.app.games.caves.node.ItemRarity) = when (rarity) {
        com.Atom2Universe.app.games.caves.node.ItemRarity.COMMON    -> 0xFFAAAAAA.toInt()
        com.Atom2Universe.app.games.caves.node.ItemRarity.MAGIC     -> 0xFF4488FF.toInt()
        com.Atom2Universe.app.games.caves.node.ItemRarity.RARE      -> 0xFFFFDD00.toInt()
        com.Atom2Universe.app.games.caves.node.ItemRarity.EPIC      -> 0xFFCC44FF.toInt()
        com.Atom2Universe.app.games.caves.node.ItemRarity.LEGENDARY -> 0xFFFF8800.toInt()
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
