package com.Atom2Universe.app.gallery

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.followImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browse only the albums on the gallery home and their descendants. */
internal fun GalleryActivity.chooseDestination(action: BatchAction, chosen: (Folder) -> Unit) {
    val library = GalleryLibrary.get(this)
    val path = mutableListOf<Folder>()
    val title = if (action == BatchAction.MOVE) R.string.gallery_move_to else R.string.gallery_copy_to
    fun browse() {
        lifecycleScope.launch {
            val current = path.lastOrNull()
            val folders = withContext(Dispatchers.IO) {
                runCatching {
                    (if (current == null) library.folders() else library.contents(current, directoriesOnly = true))
                        .filterIsInstance<Folder>()
                }.getOrNull()
            }
            if (isFinishing || isDestroyed) return@launch
            val dialog = AlertDialog.Builder(this@chooseDestination)
                .setTitle(current?.name ?: getString(R.string.gallery_destination))
                .setNegativeButton(R.string.cancel, null)
            if (folders == null) dialog.setMessage(R.string.gallery_folder_unavailable)
            else {
                if (folders.isNotEmpty()) dialog.setItems(folders.map { it.name }.toTypedArray()) { _, i ->
                    path.add(folders[i])
                    browse()
                } else if (current == null) dialog.setMessage(R.string.gallery_no_destinations)
                if (current != null) dialog.setPositiveButton(title) { _, _ -> chosen(current) }
            }
            if (current != null) dialog.setNeutralButton(R.string.back) { _, _ ->
                path.removeAt(path.lastIndex)
                browse()
            }
            dialog.create().apply { followImmersiveMode(); show() }
        }
    }
    browse()
}
