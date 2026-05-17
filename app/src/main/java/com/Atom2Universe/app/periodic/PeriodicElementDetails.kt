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
      ?: loadRoot(context, "fr")
      ?: return element.name
    return root.optJSONObject("elements")?.optJSONObject(element.id)?.optString("name", "")
      ?.takeIf { it.isNotEmpty() } ?: element.name
  }

  fun getDescription(element: PeriodicElement): ElementDescription {
    val root = loadRoot(context, LocaleHelper.getLanguage(context))
      ?: loadRoot(context, "fr")
      ?: return fallback(element)
    val obj = root.optJSONObject("elements")?.optJSONObject(element.id) ?: return fallback(element)
    val summary = obj.optString("summary", "")
    val parasArr = obj.optJSONArray("paragraphs")
    val paragraphs = if (parasArr != null) (0 until parasArr.length()).map { parasArr.getString(it) } else emptyList()
    return ElementDescription(summary, paragraphs)
  }

  fun getRarityDescription(key: String): RarityDescription? {
    val root = loadRoot(context, LocaleHelper.getLanguage(context))
      ?: loadRoot(context, "fr")
      ?: return null
    val obj = root.optJSONObject("rarities")?.optJSONObject(key) ?: return null
    return RarityDescription(
      process = obj.optString("process", ""),
      range   = obj.optString("range", ""),
      body    = obj.optString("body", "")
    )
  }

  private fun fallback(element: PeriodicElement) =
    ElementDescription("${element.symbol} – ${element.name}", emptyList())

  companion object {
    private val cache = mutableMapOf<String, JSONObject>()

    fun loadRoot(context: Context, lang: String): JSONObject? {
      cache[lang]?.let { return it }
      val json = try {
        context.assets.open("periodic_descriptions_$lang.json").bufferedReader().readText()
      } catch (e: Exception) {
        return null
      }
      return JSONObject(json).also { cache[lang] = it }
    }
  }
}
