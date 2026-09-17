package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R

/**
 * L'inventaire : ce qu'on porte, ses caractéristiques, et le sac (infini) trié du
 * meilleur au moins bon ou du plus récent au plus ancien. Toucher un objet affiche son
 * détail, avec Équiper et Vendre. Rien n'oblige à gérer quoi que ce soit.
 */
class InventoryPanel(private val root: View, private val onChanged: () -> Unit) {

    private enum class Sort { BEST, RECENT }

    private val ctx: Context = root.context
    private val density = ctx.resources.displayMetrics.density

    private val tvGold      = root.findViewById<TextView>(R.id.inv_gold)
    private val equippedRow = root.findViewById<LinearLayout>(R.id.inv_equipped)
    private val tvStats     = root.findViewById<TextView>(R.id.inv_stats)
    private val tvBagCount  = root.findViewById<TextView>(R.id.inv_bag_count)
    private val btnBest     = root.findViewById<TextView>(R.id.inv_sort_best)
    private val btnRecent   = root.findViewById<TextView>(R.id.inv_sort_recent)
    private val list        = root.findViewById<RecyclerView>(R.id.inv_list)
    private val tvEmpty     = root.findViewById<TextView>(R.id.inv_empty)
    private val detail      = root.findViewById<View>(R.id.inv_detail)
    private val detailIcon  = root.findViewById<ImageView>(R.id.inv_detail_icon)
    private val detailName  = root.findViewById<TextView>(R.id.inv_detail_name)
    private val detailSub   = root.findViewById<TextView>(R.id.inv_detail_subtitle)
    private val detailStats = root.findViewById<TextView>(R.id.inv_detail_stats)
    private val detailCmp   = root.findViewById<TextView>(R.id.inv_detail_compare)
    private val detailActs  = root.findViewById<View>(R.id.inv_detail_actions)
    private val btnEquip    = root.findViewById<TextView>(R.id.inv_detail_equip)
    private val btnSell     = root.findViewById<TextView>(R.id.inv_detail_sell)

    private var game: RoguelikeGame? = null
    private var sort = Sort.BEST
    private var selected: Equipment? = null
    private var selectedIsEquipped = false
    private var sorted: List<Equipment> = emptyList()
    private val slotViews = mutableMapOf<EquipSlot, ImageView>()

    val isOpen get() = root.visibility == View.VISIBLE

    // ── Liste du sac ────────────────────────────────────────────────────────────

    private class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.inv_item_icon)
        val name: TextView = v.findViewById(R.id.inv_item_name)
        val subtitle: TextView = v.findViewById(R.id.inv_item_subtitle)
        val rating: TextView = v.findViewById(R.id.inv_item_rating)
        val delta: TextView = v.findViewById(R.id.inv_item_delta)
    }

    private val adapter = object : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = sorted.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.roguelike_inventory_item, parent, false))

        override fun onBindViewHolder(h: Holder, position: Int) {
            val item = sorted[position]
            val hero = game?.hero ?: return
            h.icon.background = frame(item.rarity.colorArgb, !selectedIsEquipped && item == selected)
            h.icon.setImageBitmap(SpriteLoader.sheetCell(ctx.assets, item.spriteRow, item.spriteCol))
            h.name.text = LootSystem.displayName(ctx, item)
            h.name.setTextColor(item.rarity.colorArgb)
            h.subtitle.text = ctx.getString(R.string.roguelike_item_subtitle, ctx.getString(item.slot.labelRes), ctx.getString(item.rarity.labelRes))

            val rating = LootSystem.rating(item)
            h.rating.text = rating.toString()
            val diff = rating - (hero.equipped[item.slot]?.let { LootSystem.rating(it) } ?: 0)
            h.delta.text = when {
                diff > 0 -> ctx.getString(R.string.roguelike_delta_up, diff)
                diff < 0 -> ctx.getString(R.string.roguelike_delta_down, -diff)
                else     -> ctx.getString(R.string.roguelike_delta_equal)
            }
            h.delta.setTextColor(when { diff > 0 -> 0xFF66BB6A.toInt(); diff < 0 -> 0xFFEF5350.toInt(); else -> 0xFF90A4AE.toInt() })
            h.itemView.setOnClickListener { select(item, equipped = false) }
        }
    }

    init {
        root.findViewById<View>(R.id.inv_close).setOnClickListener { hide() }
        btnBest.setOnClickListener { sort = Sort.BEST; refresh() }
        btnRecent.setOnClickListener { sort = Sort.RECENT; refresh() }
        list.layoutManager = LinearLayoutManager(ctx)
        list.adapter = adapter

        // Les emplacements portés : une case par slot, dans l'ordre de l'enum
        for (slot in EquipSlot.entries) {
            val iv = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, (46 * density).toInt(), 1f).apply {
                    marginEnd = (4 * density).toInt()
                }
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                contentDescription = ctx.getString(slot.labelRes)
                setOnClickListener { select(game?.hero?.equipped?.get(slot), equipped = true) }
            }
            slotViews[slot] = iv
            equippedRow.addView(iv)
        }

        btnEquip.setOnClickListener {
            val item = selected ?: return@setOnClickListener
            game?.equipFromBag(item)
            select(item, equipped = true)
            onChanged()
        }
        btnSell.setOnClickListener {
            val item = selected ?: return@setOnClickListener
            game?.sell(item)
            select(null, equipped = false)
            onChanged()
        }
    }

    fun show(g: RoguelikeGame) {
        game = g
        selected = null
        root.visibility = View.VISIBLE
        refresh()
        list.scrollToPosition(0)
    }

    fun hide() {
        root.visibility = View.GONE
    }

    private fun select(item: Equipment?, equipped: Boolean) {
        selected = item
        selectedIsEquipped = equipped
        refresh()
    }

    fun refresh() {
        val g = game ?: return
        val hero = g.hero
        tvGold.text = ctx.getString(R.string.roguelike_hud_gold, hero.gold)

        for ((slot, iv) in slotViews) {
            val e = hero.equipped[slot]
            iv.background = frame(e?.rarity?.colorArgb ?: 0xFF2A3E52.toInt(), selectedIsEquipped && selected != null && selected == e)
            iv.setImageBitmap(e?.let { SpriteLoader.sheetCell(ctx.assets, it.spriteRow, it.spriteCol) })
        }

        tvStats.text = statsText(hero)

        sorted = when (sort) {
            Sort.BEST   -> hero.bag.sortedByDescending { LootSystem.rating(it) }
            Sort.RECENT -> hero.bag.sortedByDescending { it.lootId }
        }
        tvBagCount.text = ctx.resources.getQuantityString(R.plurals.roguelike_inventory_count, sorted.size, sorted.size)
        styleSortButton(btnBest, sort == Sort.BEST)
        styleSortButton(btnRecent, sort == Sort.RECENT)
        tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
        adapter.notifyDataSetChanged()

        bindDetail(hero)
    }

    private fun statsText(hero: Hero): String {
        val attrs = StatType.ATTRIBUTES.joinToString("   ") {
            ctx.getString(R.string.roguelike_inventory_attr, ctx.getString(it.labelRes), hero.attribute(it))
        }
        return listOf(
            attrs,
            ctx.getString(R.string.roguelike_inventory_stats_line,
                hero.hp, hero.maxHp, hero.armor, hero.weaponMin, hero.weaponMax, Math.round(hero.critChance * 100)),
        ).joinToString("\n")
    }

    private fun bindDetail(hero: Hero) {
        val item = selected
        if (item == null) { detail.visibility = View.GONE; return }
        detail.visibility = View.VISIBLE
        detailIcon.background = frame(item.rarity.colorArgb, false)
        detailIcon.setImageBitmap(SpriteLoader.sheetCell(ctx.assets, item.spriteRow, item.spriteCol))
        detailName.text = LootSystem.displayName(ctx, item)
        detailName.setTextColor(item.rarity.colorArgb)
        detailSub.text = ctx.getString(R.string.roguelike_inventory_subtitle,
            ctx.getString(item.slot.labelRes), ctx.getString(item.rarity.labelRes), LootSystem.rating(item))
        detailStats.text = LootSystem.describe(ctx, item).joinToString("\n")

        if (selectedIsEquipped) {
            detailCmp.text = ctx.getString(R.string.roguelike_loot_equipped_badge)
            detailActs.visibility = View.GONE
        } else {
            val worn = hero.equipped[item.slot]
            detailCmp.text = if (worn == null) ctx.getString(R.string.roguelike_loot_nothing_equipped)
                else ctx.getString(R.string.roguelike_inventory_worn, LootSystem.displayName(ctx, worn), LootSystem.rating(worn))
            detailActs.visibility = View.VISIBLE
            btnSell.text = ctx.getString(R.string.roguelike_inventory_sell, LootSystem.sellPrice(item))
        }
    }

    private fun styleSortButton(tv: TextView, active: Boolean) {
        tv.background = GradientDrawable().apply {
            cornerRadius = 18 * density
            setColor(if (active) 0xFF1565C0.toInt() else 0xFF1C2A38.toInt())
        }
        tv.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFF90A4AE.toInt())
    }

    /** Cadre à la couleur de la rareté, plus épais pour l'objet sélectionné. */
    private fun frame(color: Int, highlighted: Boolean) = GradientDrawable().apply {
        cornerRadius = 8 * density
        setColor(0xFF0D1520.toInt())
        setStroke(((if (highlighted) 3f else 1.5f) * density).toInt(), color)
    }

}
