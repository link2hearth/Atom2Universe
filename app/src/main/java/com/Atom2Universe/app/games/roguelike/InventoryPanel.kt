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
import com.Atom2Universe.app.games.roguelike.LexiconText.setLexiconText

/**
 * L'inventaire : ce qu'on porte, ses caractéristiques, les reliques trouvées (toucher
 * pour porter / ranger), et le sac (infini) trié du
 * meilleur au moins bon ou du plus récent au plus ancien. Toucher un objet affiche son
 * détail, avec Équiper et Vendre. Rien n'oblige à gérer quoi que ce soit.
 *
 * Rien ne s'y explique en phrases : des noms et des chiffres, et des mots qui sont des liens vers
 * le lexique ([LexiconPanel]), dont un bouton ouvre la liste.
 */
class InventoryPanel(private val root: View, private val lexicon: LexiconPanel, private val onChanged: () -> Unit) {

    private enum class Sort { BEST, RECENT }

    private val ctx: Context = root.context
    private val density = ctx.resources.displayMetrics.density

    private val tvGold      = root.findViewById<TextView>(R.id.inv_gold)
    private val equippedRow = root.findViewById<LinearLayout>(R.id.inv_equipped)
    private val tvStats     = root.findViewById<TextView>(R.id.inv_stats)
    private val attrsRow    = root.findViewById<LinearLayout>(R.id.inv_attrs)
    private val tvRelics    = root.findViewById<TextView>(R.id.inv_relics_title)
    private val relicsRow   = root.findViewById<LinearLayout>(R.id.inv_relics)
    private val tvRelicDesc = root.findViewById<TextView>(R.id.inv_relic_desc)
    private val tvResonance = root.findViewById<TextView>(R.id.inv_resonance)
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
    private var selectedRelic: Relic? = null
    private var relicRefused = false

    /** Un texte dont les mots marqués (voir [LexiconText]) ouvrent le lexique. */
    private fun TextView.setLex(markup: String) = setLexiconText(markup) { lexicon.open(it) }

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
            h.rating.text = num(rating)
            val diff = rating - (hero.equipped[item.slot]?.let { LootSystem.rating(it) } ?: 0)
            h.delta.text = when {
                diff > 0 -> ctx.getString(R.string.roguelike_delta_up, num(diff))
                diff < 0 -> ctx.getString(R.string.roguelike_delta_down, num(-diff))
                else     -> ctx.getString(R.string.roguelike_delta_equal)
            }
            h.delta.setTextColor(when { diff > 0 -> 0xFF66BB6A.toInt(); diff < 0 -> 0xFFEF5350.toInt(); else -> 0xFF90A4AE.toInt() })
            h.itemView.setOnClickListener { select(item, equipped = false) }
        }
    }

    init {
        root.findViewById<View>(R.id.inv_close).setOnClickListener { hide() }
        root.findViewById<View>(R.id.inv_lexicon).setOnClickListener { lexicon.open() }
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
        selectedRelic = null
        relicRefused = false
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
        tvGold.text = ctx.getString(R.string.roguelike_hud_gold, num(hero.gold))

        for ((slot, iv) in slotViews) {
            val e = hero.equipped[slot]
            iv.background = frame(e?.rarity?.colorArgb ?: 0xFF2A3E52.toInt(), selectedIsEquipped && selected != null && selected == e)
            iv.setImageBitmap(e?.let { SpriteLoader.sheetCell(ctx.assets, it.spriteRow, it.spriteCol) })
        }

        bindAttributes(hero)
        tvStats.setLex(statsText(hero))
        bindRelics(hero)

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

    // ── Reliques ────────────────────────────────────────────────────────────────

    private fun bindRelics(hero: Hero) {
        val worn = hero.relicSlots.count { it != null }
        tvRelics.setLex(if (hero.relics.isEmpty()) ctx.getString(R.string.roguelike_inventory_relics_none)
            else ctx.getString(R.string.roguelike_inventory_relics_title, worn, hero.unlockedRelicSlots))

        relicsRow.removeAllViews()
        for (relic in hero.relics) {
            val on = relic in hero.relicSlots
            val chip = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (6 * density).toInt()
                setPadding(pad, pad / 2, pad * 2, pad / 2)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (40 * density).toInt()).apply {
                    marginEnd = (6 * density).toInt()
                }
                background = GradientDrawable().apply {
                    cornerRadius = 20 * density
                    setColor(if (on) relic.color else 0xFF1C2A38.toInt())
                    setStroke(((if (relic == selectedRelic) 2.5f else 1f) * density).toInt(),
                        if (relic == selectedRelic) 0xFFFFFFFF.toInt() else 0xFF455A64.toInt())
                }
                setOnClickListener { onRelicTapped(relic) }
            }
            chip.addView(ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt())
                setImageBitmap(SpriteLoader.sheetCell(ctx.assets, relic.iconRow, relic.iconCol))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            chip.addView(TextView(ctx).apply {
                text = ctx.getString(relic.labelRes)
                setTextColor(if (on) 0xFFFFFFFF.toInt() else 0xFF90A4AE.toInt())
                textSize = 13f
                setPadding((4 * density).toInt(), 0, 0, 0)
            })
            relicsRow.addView(chip)
        }

        bindResonances(hero)

        val relic = selectedRelic
        if (relic == null) { tvRelicDesc.visibility = View.GONE; return }
        tvRelicDesc.visibility = View.VISIBLE
        tvRelicDesc.setLex(relicLine(hero, relic) + if (relicRefused) "\n" + ctx.getString(R.string.roguelike_inventory_relics_full) else "")
    }

    /**
     * Une relique en une ligne : son nom, sa caractéristique, son élément, ses dégâts, sa recharge.
     * Chacun est un lien ; la description complète est dans sa fiche du lexique.
     */
    private fun relicLine(hero: Hero, relic: Relic): String {
        val name = LexiconText.link(Lexicon.idOf(relic), ctx.getString(relic.labelRes))
        val attr = LexiconText.link(Lexicon.idOf(relic.attribute), ctx.getString(relic.attribute.labelRes))
        val element = LexiconText.link(Lexicon.idOf(relic.element), ctx.getString(Lexicon.elementRes(relic.element)))
        val cooldown = hero.castCooldown(relic)
        if (!relic.hits) return ctx.getString(R.string.roguelike_inventory_relic_line, name, attr, element, cooldown)
        val (lo, hi) = hero.relicDamage(relic)
        return ctx.getString(R.string.roguelike_inventory_relic_line_hit, name, attr, element, num(lo), num(hi), cooldown)
    }

    /**
     * La résonance portée (son bonus et son effet), puis le carnet : les paires déjà
     * découvertes. Celles qu'on n'a jamais portées restent cachées, on n'en montre que le compte.
     */
    private fun bindResonances(hero: Hero) {
        val lines = hero.resonances.map { r ->
            ctx.getString(R.string.roguelike_inventory_resonance_active, LexiconText.link(Lexicon.idOf(r), ctx.getString(r.labelRes)),
                Resonance.BONUS, LexiconText.link(Lexicon.idOf(r.attribute), ctx.getString(r.attribute.labelRes)))
        }.toMutableList()
        if (hero.knownResonances.isNotEmpty())
            lines += ctx.getString(R.string.roguelike_inventory_resonance_book, hero.knownResonances.size, Resonance.entries.size)
        tvResonance.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        tvResonance.setLex(lines.joinToString("\n"))
    }

    /** Toucher une relique la décrit, et la porte ou la range. */
    private fun onRelicTapped(relic: Relic) {
        val result = game?.toggleRelic(relic) ?: return
        selectedRelic = relic
        relicRefused = result == Hero.RelicToggle.SLOTS_FULL
        refresh()
        onChanged()
    }

    // ── Caractéristiques ────────────────────────────────────────────────────────

    /**
     * Les six sigles (FOR, DEX…) gardent les lignes courtes. Toucher l'un d'eux ouvre sa fiche du
     * lexique : son nom complet, son rôle, et les vrais chiffres.
     */
    private fun bindAttributes(hero: Hero) {
        attrsRow.removeAllViews()
        for (attr in StatType.ATTRIBUTES) {
            attrsRow.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, (34 * density).toInt(), 1f).apply {
                    marginEnd = (4 * density).toInt()
                }
                gravity = android.view.Gravity.CENTER
                text = ctx.getString(R.string.roguelike_inventory_attr, ctx.getString(attr.labelRes), hero.attribute(attr))
                textSize = 12f
                setTextColor(LexiconText.LINK_COLOR)
                background = GradientDrawable().apply {
                    cornerRadius = 6 * density
                    setColor(0xFF141E2A.toInt())
                }
                setOnClickListener { lexicon.open(Lexicon.idOf(attr)) }
            })
        }
    }

    /** Grands nombres (PV, or, note, dégâts) : abrégés au-delà de 100 000. */
    private fun num(v: Int) = DungeonNumbers.format(ctx, v)

    private fun statsText(hero: Hero): String {
        val archetype = hero.archetype?.let {
            ctx.getString(R.string.roguelike_inventory_archetype, LexiconText.link(Lexicon.idOf(it), ctx.getString(it.labelRes)))
        } ?: ctx.getString(R.string.roguelike_inventory_archetype_none)
        return listOf(
            archetype,
            ctx.getString(R.string.roguelike_inventory_stats_line,
                num(hero.hp), num(hero.maxHp), num(hero.armor), num(hero.weaponMin), num(hero.weaponMax), Math.round(hero.critChance(game?.floor ?: 1) * 100),
                Math.round(hero.dodgeChance(game?.floor ?: 1) * 100), Math.round(hero.speed * 100)),
        ).joinToString("\n")
    }

    private fun bindDetail(hero: Hero) {
        val item = selected
        if (item == null) { detail.visibility = View.GONE; return }
        detail.visibility = View.VISIBLE
        detailIcon.background = frame(item.rarity.colorArgb, false)
        detailIcon.setImageBitmap(SpriteLoader.sheetCell(ctx.assets, item.spriteRow, item.spriteCol))
        detailName.setTextColor(item.rarity.colorArgb)
        detailName.setLex(LootSystem.displayName(ctx, item, linked = true))
        detailSub.setLex(ctx.getString(R.string.roguelike_inventory_subtitle, ctx.getString(item.slot.labelRes),
            LexiconText.link(Lexicon.idOf(item.rarity), ctx.getString(item.rarity.labelRes)), num(LootSystem.rating(item))))
        detailStats.setLex(LootSystem.describe(ctx, item, linked = true).joinToString("\n"))

        if (selectedIsEquipped) {
            detailCmp.text = ctx.getString(R.string.roguelike_loot_equipped_badge)
            detailActs.visibility = View.GONE
        } else {
            val worn = hero.equipped[item.slot]
            detailCmp.text = if (worn == null) ctx.getString(R.string.roguelike_loot_nothing_equipped)
                else ctx.getString(R.string.roguelike_inventory_worn, LootSystem.displayName(ctx, worn), num(LootSystem.rating(worn)))
            detailActs.visibility = View.VISIBLE
            btnSell.text = ctx.getString(R.string.roguelike_inventory_sell, num(LootSystem.sellPrice(item)))
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
