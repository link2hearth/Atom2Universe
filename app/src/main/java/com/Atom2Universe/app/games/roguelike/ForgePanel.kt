package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import java.text.NumberFormat

/**
 * La fenêtre de la forge ([DungeonForge]) : l'équipement porté puis le sac, filtrables par
 * emplacement. Toucher un objet l'ouvre ; « Reforger » le remplace sur place par le nouveau tirage,
 * qui reste ouvert pour pouvoir recommencer.
 */
class ForgePanel(
    private val root: View,
    private val lexicon: LexiconPanel,
    private val onChanged: () -> Unit,
    private val onLeave: () -> Unit,
) {
    private val ctx: Context = root.context
    private val density = ctx.resources.displayMetrics.density
    private val ink = 0xFFE5EAF2.toInt()
    private val muted = 0xFFADB9CD.toInt()
    private val green = 0xFF80D6A0.toInt()
    private val red = 0xFFFF9393.toInt()
    private val accent = 0xFFE8BF78.toInt()
    private val number = NumberFormat.getNumberInstance(ctx.resources.configuration.locales[0])
    private var game: RoguelikeGame? = null
    private var slot: EquipSlot? = null
    private var items = mutableListOf<Equipment>()
    private var expanded: Equipment? = null
    /** Le score de l'objet avant son dernier passage à la forge, pour comparer. */
    private val previousScore = HashMap<Equipment, Int>()

    private val title = text("", 20f, accent, true)
    private val gold = text("", 14f, ink, true)
    private val price = text("", 14f, muted)
    private val filters = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private val empty = text(ctx.getString(R.string.roguelike_forge_empty), 15f, muted).apply {
        setPadding(dp(12), dp(24), dp(12), dp(24))
    }
    private val list = RecyclerView(ctx)
    val isOpen get() = root.visibility == View.VISIBLE

    private class Holder(v: View) : RecyclerView.ViewHolder(v)
    private val adapter = object : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(column().apply { layoutParams = RecyclerView.LayoutParams(-1, -2) })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val box = holder.itemView as LinearLayout
            box.removeAllViews()
            box.addView(card(items[position]), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    init {
        val container = root as LinearLayout
        container.removeAllViews()
        container.setBackgroundColor(0xFF0B101B.toInt())
        container.setPadding(dp(12), dp(10), dp(12), dp(8))
        val head = row()
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(button(ctx.getString(R.string.lex_button)) { lexicon.open("forge") })
        container.addView(head)
        val money = row()
        money.addView(gold, LinearLayout.LayoutParams(0, -2, 1f))
        money.addView(price)
        container.addView(money)
        container.addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) })
        container.addView(empty)
        list.layoutManager = LinearLayoutManager(ctx)
        list.adapter = adapter
        list.itemAnimator = null
        container.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        container.addView(button(ctx.getString(R.string.roguelike_forge_leave), true) { onLeave() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    fun show(g: RoguelikeGame) {
        game = g
        slot = null
        expanded = null
        previousScore.clear()
        root.visibility = View.VISIBLE
        rebuild()
        list.scrollToPosition(0)
    }

    fun hide() {
        root.visibility = View.GONE
        game = null
    }

    /** Retour : referme d'abord l'objet ouvert, puis quitte la forge. */
    fun back() {
        val open = expanded
        if (open != null) {
            expanded = null
            items.indexOf(open).takeIf { it >= 0 }?.let(adapter::notifyItemChanged)
        } else onLeave()
    }

    /** L'équipement porté d'abord, puis le sac, du meilleur score au moins bon. */
    private fun rebuild() {
        val hero = game?.hero ?: return
        val archetype = hero.archetype
        val worn = EquipSlot.entries.mapNotNull { hero.equipped[it] }
        val bag = hero.bag.sortedByDescending { LootSystem.rating(it, archetype) }
        items = (worn + bag).filter { slot == null || it.slot == slot }.toMutableList()
        buildFilters()
        updateMoney()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun updateMoney() {
        val g = game ?: return
        title.text = ctx.getString(R.string.roguelike_forge_title, g.floor)
        gold.text = ctx.getString(R.string.roguelike_forge_gold, DungeonNumbers.format(ctx, g.hero.gold))
        price.text = ctx.getString(R.string.roguelike_forge_price, DungeonNumbers.format(ctx, g.forgePrice))
    }

    private fun buildFilters() {
        filters.removeAllViews()
        val choices = listOf<EquipSlot?>(null) + EquipSlot.entries
        for (choice in choices) {
            val label = choice?.let { ctx.getString(it.labelRes) } ?: ctx.getString(R.string.inv_all)
            filters.addView(button(label, choice == slot) {
                if (slot != choice) { slot = choice; expanded = null; rebuild(); list.scrollToPosition(0) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
        }
    }

    private fun card(item: Equipment): View {
        val g = requireNotNull(game)
        val hero = g.hero
        val isOpen = expanded === item
        val tile = column().apply { background = frame(if (isOpen) accent else item.inventoryColor) }
        val line = row().apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            minimumHeight = dp(76)
        }
        line.addView(ImageView(ctx).apply {
            setImageDrawable(PixelArtIcon(EquipmentArt.icon(item)))
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(44), dp(52)))
        val words = column()
        words.addView(text(LootSystem.displayName(ctx, item), 15f, item.inventoryColor, true))
        val tags = listOfNotNull(
            ctx.getString(item.slot.labelRes),
            item.weight?.let { ctx.getString(it.labelRes) },
            ctx.getString(if (item.isotopeZ != null) R.string.inv_legendary else item.rarity.labelRes),
            ctx.getString(R.string.roguelike_forge_worn).takeIf { hero.equipped[item.slot] === item },
        )
        words.addView(text(tags.joinToString(ctx.getString(R.string.inv_separator)), 12f, muted))
        val score = LootSystem.rating(item, hero.archetype)
        val before = previousScore[item]
        words.addView(text(ctx.getString(R.string.inv_rating, number.format(score)), 12f,
            if (before == null) muted else if (score > before) green else if (score < before) red else muted, before != null))
        if (before != null) words.addView(text(ctx.getString(R.string.roguelike_forge_previous, number.format(before)), 12f, muted))
        line.addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        line.setOnClickListener {
            val previous = expanded
            expanded = if (isOpen) null else item
            previous?.let { p -> items.indexOf(p).takeIf { it >= 0 }?.let(adapter::notifyItemChanged) }
            items.indexOf(item).takeIf { it >= 0 }?.let(adapter::notifyItemChanged)
        }
        tile.addView(line)
        if (isOpen) {
            val detail = column().apply { setPadding(dp(10), 0, dp(10), dp(10)) }
            detail.addView(text(LootSystem.describe(ctx, item, linked = false, archetype = hero.archetype).joinToString("\n"), 14f, ink))
            detail.addView(button(ctx.getString(R.string.roguelike_forge_reforge, DungeonNumbers.format(ctx, g.forgePrice)), true) {
                reforge(item)
            }.apply { isEnabled = hero.gold >= g.forgePrice; alpha = if (isEnabled) 1f else .45f },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            tile.addView(detail)
        }
        return tile
    }

    /** Le nouvel objet prend la place de l'ancien dans la liste et reste ouvert. */
    private fun reforge(item: Equipment) {
        val g = game ?: return
        val index = items.indexOf(item)
        val before = previousScore[item] ?: LootSystem.rating(item, g.hero.archetype)
        val forged = g.reforge(item) ?: return
        previousScore.remove(item)
        previousScore[forged] = before
        if (index >= 0) items[index] = forged
        expanded = forged
        updateMoney()
        if (index >= 0) adapter.notifyItemChanged(index)
        onChanged()
    }

    private fun dp(n: Int) = (n * density).toInt()
    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun button(label: String, active: Boolean = false, action: () -> Unit) = text(label, 13f, if (active) ink else muted, active).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = frame(if (active) accent else 0xFF303C52.toInt())
        setOnClickListener { action() }
    }
    private fun frame(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat(); setColor(0xFF151F30.toInt()); setStroke(dp(1).coerceAtLeast(1), color)
    }
}
