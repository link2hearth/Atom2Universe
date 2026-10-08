package com.Atom2Universe.app.periodic

import android.content.Context
import com.Atom2Universe.app.LocaleHelper
import org.json.JSONObject

data class ElementDescription(
  val summary: String,
  val paragraphs: List<String>
)

data class RarityDescription(
  val process: String,
  val range: String,
  val body: String
)

class PeriodicElementDescriptionProvider(private val context: Context) {

  fun getName(element: PeriodicElement): String {
    val root = loadRoot(context, LocaleHelper.getLanguage(context))
      ?: loadRoot(context, "en")
      ?: return element.name
    return root.optJSONObject("elements")?.optJSONObject(element.id)?.optString("name", "")
      ?.takeIf { it.isNotEmpty() } ?: element.name
  }

  fun getDescription(element: PeriodicElement): ElementDescription {
    val root = loadRoot(context, LocaleHelper.getLanguage(context))
      ?: loadRoot(context, "en")
      ?: return fallback(element)
    val obj = root.optJSONObject("elements")?.optJSONObject(element.id) ?: return fallback(element)
    val summary = obj.optString("summary", "")
    val parasArr = obj.optJSONArray("paragraphs")
    val paragraphs = if (parasArr != null) (0 until parasArr.length()).map { parasArr.getString(it) } else emptyList()
    return ElementDescription(summary, paragraphs)
  }

  fun getRarityDescription(key: String): RarityDescription? {
    val root = loadRoot(context, LocaleHelper.getLanguage(context))
      ?: loadRoot(context, "en")
      ?: return null
    val obj = root.optJSONObject("rarities")?.optJSONObject(key) ?: return null
    return RarityDescription(
      process = obj.optString("process", ""),
      range   = obj.optString("range", ""),
      body    = obj.optString("body", "")
    )
  }

  /** Apparence localisée ; repli sur l'anglais, jamais sur le texte brut de periodic_table.json. */
  fun getAppearance(element: PeriodicElement): String? =
    localizedProperty("appearance", element.atomicNumber)

  /**
   * Découvreur : les lieux et époques (« Ancient Egypt », « 5000 BC »…) sont traduits dans
   * periodic_properties_*.json ; les noms propres de periodic_table.json restent tels quels.
   */
  fun getDiscoveredBy(element: PeriodicElement, raw: String?): String? =
    localizedProperty("discovered_by", element.atomicNumber) ?: raw

  private fun localizedProperty(section: String, atomicNumber: Int): String? {
    val key = atomicNumber.toString()
    return sequenceOf(LocaleHelper.getLanguage(context), "en")
      .mapNotNull { loadProperties(context, it)?.optJSONObject(section)?.optString(key, "") }
      .firstOrNull { it.isNotEmpty() }
  }

  private fun fallback(element: PeriodicElement) =
    ElementDescription("${element.symbol} – ${getName(element)}", emptyList())

  companion object {
    private val cache = mutableMapOf<String, JSONObject>()
    // Fichier absent ou illisible : on ne retente pas l'ouverture à chaque nom affiché.
    private val missing = mutableSetOf<String>()

    fun loadRoot(context: Context, lang: String): JSONObject? =
      loadAsset(context, "Elements/periodic_descriptions_$lang.json")

    private fun loadProperties(context: Context, lang: String): JSONObject? =
      loadAsset(context, "Elements/periodic_properties_$lang.json")

    @Synchronized
    private fun loadAsset(context: Context, path: String): JSONObject? {
      cache[path]?.let { return it }
      if (path in missing) return null
      val root = try {
        context.assets.open(path).bufferedReader().use { it.readText() }.let(::JSONObject)
      } catch (e: Exception) {
        missing += path
        return null
      }
      return root.also { cache[path] = it }
    }
  }
}
