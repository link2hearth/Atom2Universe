package com.Atom2Universe.app.gallery

import androidx.lifecycle.ViewModel

internal enum class TempMark { CHECK, CROSS }

/**
 * Marks chosen in the sorting gallery opened from the clicker widget. Nothing is applied before "Validate":
 * the check copies the image, the cross removes it from the rotation. A check already green when the gallery
 * opens belongs to an image that is already copied: it cannot be changed.
 */
internal class GalleryTemp : ViewModel() {
    var ready = false
    var locked = emptySet<String>()
    var checked = emptySet<String>()
    var crossed = emptySet<String>()
    var multi: TempMark? = null

    fun start(saved: Set<String>) {
        if (ready) return
        ready = true
        locked = saved
        checked = saved
    }

    fun toggle(id: String, mark: TempMark) {
        when (mark) {
            TempMark.CHECK -> if (id !in locked) checked = if (id in checked) checked - id else checked + id
            TempMark.CROSS -> crossed = if (id in crossed) crossed - id else crossed + id
        }
    }

    /** « Tout » : pose la marque sur toutes les images ; si elles l'ont déjà toutes, la retire de toutes. */
    fun markAll(ids: Collection<String>, mark: TempMark) {
        when (mark) {
            TempMark.CHECK -> {
                val free = ids.filter { it !in locked }
                checked = if (free.all { it in checked }) checked - free.toSet() else checked + free
            }
            TempMark.CROSS -> crossed = if (ids.all { it in crossed }) crossed - ids.toSet() else crossed + ids
        }
    }

    val toSave: Set<String> get() = checked - locked
    val changed: Boolean get() = toSave.isNotEmpty() || crossed.isNotEmpty()
}
