package com.Atom2Universe.app.periodic

import android.content.Context
import androidx.core.content.edit

class PeriodicCollectionStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getCopyCount(atomicNumber: Int): Int {
    return prefs.getInt(copyKey(atomicNumber), 0)
  }

  fun addCopy(atomicNumber: Int): Int {
    val newCount = getCopyCount(atomicNumber) + 1
    val newMax = maxOf(newCount, getMaxCopyCount(atomicNumber))
    val newTotal = getTotalEverCount(atomicNumber) + 1
    prefs.edit()
      .putInt(copyKey(atomicNumber), newCount)
      .putInt(maxCopyKey(atomicNumber), newMax)
      .putInt(totalEverKey(atomicNumber), newTotal)
      .putBoolean(everKey(atomicNumber), true)
      .apply()
    return newCount
  }

  /** Nombre maximum de copies jamais possédées simultanément (ne diminue jamais). */
  fun getMaxCopyCount(atomicNumber: Int): Int =
    prefs.getInt(maxCopyKey(atomicNumber), getCopyCount(atomicNumber))

  /** Nombre total cumulatif de copies jamais obtenues (ne diminue jamais). Utilisé pour les bonus permanents. */
  fun getTotalEverCount(atomicNumber: Int): Int =
    prefs.getInt(totalEverKey(atomicNumber), getMaxCopyCount(atomicNumber))

  /** Nombre de copies obtenues spécifiquement via une fusion réussie (ne diminue jamais). */
  fun getFusionCount(atomicNumber: Int): Int =
    prefs.getInt(fusionCountKey(atomicNumber), 0)

  /** Ajoute une copie obtenue via fusion : incrémente les compteurs réguliers ET le compteur fusion. */
  fun addCopyFromFusion(atomicNumber: Int): Int {
    val regular = addCopy(atomicNumber)
    val newFusion = getFusionCount(atomicNumber) + 1
    prefs.edit { putInt(fusionCountKey(atomicNumber), newFusion) }
    return regular
  }

  fun hasElement(atomicNumber: Int): Boolean = getCopyCount(atomicNumber) > 0

  /** Retourne true si l'élément a déjà été obtenu au moins une fois (ne se réinitialise jamais). */
  fun hasEverObtained(atomicNumber: Int): Boolean =
    prefs.getBoolean(everKey(atomicNumber), false) || getCopyCount(atomicNumber) > 0

  /**
   * Consomme une copie de l'élément (min 0). Retourne true si une copie était disponible.
   * Ne touche pas au flag [hasEverObtained].
   */
  fun consumeCopy(atomicNumber: Int): Boolean {
    val current = getCopyCount(atomicNumber)
    if (current <= 0) return false
    prefs.edit { putInt(copyKey(atomicNumber), current - 1) }
    return true
  }

  /** Nombre total de copies disponibles sur tous les éléments (1–118). */
  fun getTotalCopies(): Int = (1..118).sumOf { getCopyCount(it) }

  private fun copyKey(atomicNumber: Int): String = "element_${atomicNumber}_copies"
  private fun maxCopyKey(atomicNumber: Int): String = "element_${atomicNumber}_max"
  private fun totalEverKey(atomicNumber: Int): String = "element_${atomicNumber}_total"
  private fun fusionCountKey(atomicNumber: Int): String = "element_${atomicNumber}_fusion"
  private fun everKey(atomicNumber: Int): String = "element_${atomicNumber}_ever"

  fun setCopyCount(atomicNumber: Int, count: Int) {
    prefs.edit { putInt(copyKey(atomicNumber), count) }
  }

  fun reset() {
    prefs.edit { clear() }
  }

  companion object {
    private const val PREFS_NAME = "periodic_collection"
  }
}
