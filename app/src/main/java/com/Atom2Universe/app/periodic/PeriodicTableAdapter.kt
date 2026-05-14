package com.Atom2Universe.app.periodic

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf

class PeriodicTableAdapter(
  private val context: Context,
  private val elements: List<PeriodicElement>,
  private val collectionStore: com.Atom2Universe.app.periodic.PeriodicCollectionStore,
  private val onElementClick: (PeriodicElement) -> Unit
) : RecyclerView.Adapter<PeriodicTableAdapter.ElementViewHolder>() {

  inner class ElementViewHolder(itemView: FrameLayout) : RecyclerView.ViewHolder(itemView) {
    private val contentView: LinearLayout = itemView.findViewById(R.id.element_content)
    private val rarityCorner: View = itemView.findViewById(R.id.element_rarity_corner)
    private val symbolView: TextView = itemView.findViewById(R.id.element_symbol)
    private val numberView: TextView = itemView.findViewById(R.id.element_number)
    private val nameView: TextView = itemView.findViewById(R.id.element_name)
    private val massView: TextView = itemView.findViewById(R.id.element_mass)

    fun bind(element: PeriodicElement) {
      symbolView.text = element.symbol
      numberView.text = element.atomicNumber.toString()
      nameView.text = element.localizedName(context)
      massView.text = "%.3f".format(element.atomicMass)

      val categoryColor = getCategoryColor(element.category)
      contentView.background.setTint(categoryColor)

      val owned = collectionStore.hasEverObtained(element.atomicNumber)
      val rarity = rarityOf(element.atomicNumber)
      rarityCorner.setBackgroundColor(getRarityColor(rarity))
      rarityCorner.visibility = if (owned) View.VISIBLE else View.INVISIBLE

      itemView.setOnClickListener { onElementClick(element) }
    }

    private fun getCategoryColor(category: String): Int = when (category) {
      "alkali-metal" -> context.getColor(R.color.category_alkali_metal)
      "alkaline-earth-metal" -> context.getColor(R.color.category_alkaline_earth_metal)
      "transition-metal" -> context.getColor(R.color.category_transition_metal)
      "post-transition-metal" -> context.getColor(R.color.category_post_transition_metal)
      "metalloid" -> context.getColor(R.color.category_metalloid)
      "nonmetal" -> context.getColor(R.color.category_nonmetal)
      "halogen" -> context.getColor(R.color.category_halogen)
      "noble-gas" -> context.getColor(R.color.category_noble_gas)
      "lanthanide" -> context.getColor(R.color.category_lanthanide)
      "actinide" -> context.getColor(R.color.category_actinide)
      else -> context.getColor(R.color.category_default)
    }

    private fun getRarityColor(rarity: GachaRarity): Int = when (rarity) {
      GachaRarity.COMMUN -> context.getColor(R.color.rarity_commun)
      GachaRarity.ESSENTIEL -> context.getColor(R.color.rarity_essentiel)
      GachaRarity.STELLAIRE -> context.getColor(R.color.rarity_stellaire)
      GachaRarity.MYTHIQUE -> context.getColor(R.color.rarity_mythique)
      GachaRarity.SINGULIER -> context.getColor(R.color.rarity_singulier)
      GachaRarity.IRREEL -> context.getColor(R.color.rarity_irreel)
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ElementViewHolder {
    val itemView = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_periodic_element, parent, false) as FrameLayout
    return ElementViewHolder(itemView)
  }

  override fun onBindViewHolder(holder: ElementViewHolder, position: Int) {
    holder.bind(elements[position])
  }

  override fun getItemCount(): Int = elements.size
}
