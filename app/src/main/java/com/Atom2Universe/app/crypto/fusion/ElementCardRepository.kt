package com.Atom2Universe.app.crypto.fusion

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

data class ElementCard(val atomicNumber: Int, val file: String)

private data class FusionCardPool(val dropRate: Double, val cards: List<ElementCard>)

class ElementCardRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pools: Map<String, FusionCardPool> = loadPools(context)

    // Tente un drop de carte pour la fusion donnée.
    // Retourne la carte gagnée, ou null si pas de chance / déjà toutes obtenues.
    fun rollDrop(fusionId: String): ElementCard? {
        val pool = pools[fusionId] ?: return null
        if (Math.random() >= pool.dropRate) return null
        val candidates = pool.cards.filter { !hasCard(it.atomicNumber) }
        if (candidates.isEmpty()) return null
        return candidates.random()
    }

    fun markObtained(atomicNumber: Int) {
        val current = prefs.getStringSet(KEY_OBTAINED, emptySet())!!.toMutableSet()
        current.add(atomicNumber.toString())
        prefs.edit { putStringSet(KEY_OBTAINED, current) }
    }

    fun hasCard(atomicNumber: Int): Boolean =
        prefs.getStringSet(KEY_OBTAINED, emptySet())!!.contains(atomicNumber.toString())

    fun getObtainedCards(): Set<Int> =
        prefs.getStringSet(KEY_OBTAINED, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun getTotalCards(): Int = pools.values.sumOf { it.cards.size }

    fun reset() = prefs.edit { clear() }

    // Retourne la définition de la carte pour cet élément si elle est obtenue, null sinon.
    fun getCardFor(atomicNumber: Int): ElementCard? {
        if (!hasCard(atomicNumber)) return null
        return pools.values.flatMap { it.cards }.firstOrNull { it.atomicNumber == atomicNumber }
    }

    private fun loadPools(context: Context): Map<String, FusionCardPool> {
        val json = context.assets.open("element_cards.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val fusions = root.getJSONArray("fusions")
        val result = mutableMapOf<String, FusionCardPool>()
        for (i in 0 until fusions.length()) {
            val obj = fusions.getJSONObject(i)
            val fusionId = obj.getString("fusionId")
            val dropRate = obj.getDouble("cardDropRate")
            val cardsArr = obj.getJSONArray("cards")
            val cards = (0 until cardsArr.length()).map { j ->
                val c = cardsArr.getJSONObject(j)
                ElementCard(c.getInt("atomicNumber"), c.getString("file"))
            }
            result[fusionId] = FusionCardPool(dropRate, cards)
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "element_cards_prefs"
        private const val KEY_OBTAINED = "obtained_cards"
    }
}
