package com.Atom2Universe.app.crypto.fusion

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

data class ElementCard(val atomicNumber: Int, val file: String)

private data class FusionCardPool(val dropRate: Double, val cards: List<ElementCard>)

class ElementCardRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pools: Map<String, FusionCardPool> = loadPools(context)

    // Nombre d'exemplaires gagnés par numéro atomique (0 / absent = jamais obtenue).
    // Pas encore affiché dans l'UI, mais persisté pour usage futur.
    private val counts: MutableMap<Int, Int> = loadCounts()

    // Tente un drop de carte pour la fusion donnée.
    // Retourne la carte gagnée, ou null si pas de chance.
    // Tant que le lot n'est pas complet, on tire en priorité une carte manquante.
    // Une fois toutes les cartes du lot obtenues, le drop "boucle" : on peut re-gagner
    // n'importe quelle carte du lot (doublons autorisés, comptés en exemplaires).
    fun rollDrop(fusionId: String): ElementCard? {
        val pool = pools[fusionId] ?: return null
        if (Math.random() >= pool.dropRate) return null
        if (pool.cards.isEmpty()) return null
        val missing = pool.cards.filter { !hasCard(it.atomicNumber) }
        return if (missing.isNotEmpty()) missing.random() else pool.cards.random()
    }

    /** Enregistre l'obtention d'une carte : +1 exemplaire. */
    fun markObtained(atomicNumber: Int) {
        counts[atomicNumber] = (counts[atomicNumber] ?: 0) + 1
        persistCounts()
    }

    fun hasCard(atomicNumber: Int): Boolean = (counts[atomicNumber] ?: 0) > 0

    /** Nombre d'exemplaires de cette carte gagnés au total (doublons compris). */
    fun getCardCount(atomicNumber: Int): Int = counts[atomicNumber] ?: 0

    fun getObtainedCards(): Set<Int> =
        counts.filterValues { it > 0 }.keys.toSet()

    fun getTotalCards(): Int = pools.values.sumOf { it.cards.size }

    /** Nombre total d'exemplaires gagnés, toutes cartes confondues (doublons compris). */
    fun getTotalCopies(): Int = counts.values.sum()

    fun reset() {
        counts.clear()
        prefs.edit { clear() }
    }

    // Retourne la définition de la carte pour cet élément si elle est obtenue, null sinon.
    fun getCardFor(atomicNumber: Int): ElementCard? {
        if (!hasCard(atomicNumber)) return null
        return pools.values.flatMap { it.cards }.firstOrNull { it.atomicNumber == atomicNumber }
    }

    private fun loadCounts(): MutableMap<Int, Int> {
        // Nouveau format : objet JSON { numéroAtomique: nbExemplaires }.
        prefs.getString(KEY_COUNTS, null)?.let { json ->
            val obj = JSONObject(json)
            val map = mutableMapOf<Int, Int>()
            obj.keys().forEach { key -> key.toIntOrNull()?.let { map[it] = obj.getInt(key) } }
            return map
        }
        // Migration depuis l'ancien format (Set "obtained_cards" = 1 exemplaire chacun).
        val legacy = prefs.getStringSet(KEY_OBTAINED, emptySet()) ?: emptySet()
        val migrated = legacy.mapNotNull { it.toIntOrNull() }.associateWith { 1 }.toMutableMap()
        if (migrated.isNotEmpty()) {
            prefs.edit { putString(KEY_COUNTS, mapToJson(migrated)) }
        }
        return migrated
    }

    private fun persistCounts() {
        prefs.edit { putString(KEY_COUNTS, mapToJson(counts)) }
    }

    private fun mapToJson(map: Map<Int, Int>): String {
        val obj = JSONObject()
        map.forEach { (atomicNumber, count) -> if (count > 0) obj.put(atomicNumber.toString(), count) }
        return obj.toString()
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
        private const val KEY_OBTAINED = "obtained_cards"   // ancien format (migration uniquement)
        private const val KEY_COUNTS = "card_counts"
    }
}
