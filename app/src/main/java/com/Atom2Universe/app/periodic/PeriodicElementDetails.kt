package com.Atom2Universe.app.periodic

import android.content.Context

data class ElementDescription(
  val summary: String,
  val paragraphs: List<String>
)

class PeriodicElementDescriptionProvider(private val context: Context) {
  fun getDescription(element: PeriodicElement): ElementDescription {
    val cleanId = element.id.replace("-", "_")
    val summaryKey = "periodic_${cleanId}_summary"
    val paragraphPrefix = "periodic_${cleanId}_p"

    val summaryResId = context.resources.getIdentifier(summaryKey, "string", context.packageName)
    val summary = if (summaryResId != 0) {
      context.getString(summaryResId)
    } else {
      "${element.symbol} - ${element.name}"
    }

    val paragraphs = mutableListOf<String>()
    var i = 1
    while (true) {
      val paragraphKey = "${paragraphPrefix}$i"
      val paragraphResId = context.resources.getIdentifier(paragraphKey, "string", context.packageName)
      if (paragraphResId == 0) break
      paragraphs.add(context.getString(paragraphResId))
      i++
    }

    return ElementDescription(summary, paragraphs)
  }
}
