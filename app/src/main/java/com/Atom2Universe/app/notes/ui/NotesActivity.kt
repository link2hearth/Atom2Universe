package com.Atom2Universe.app.notes.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NotesDatabase
import com.Atom2Universe.app.notes.repository.NotesRepository
import com.Atom2Universe.app.notes.sync.NotesSyncManager
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel
import com.Atom2Universe.app.notes.viewmodel.NotesViewModelFactory
import com.Atom2Universe.app.util.SystemBarsManager
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.updateSystemBarsVisibility
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Les Notes : une seule activité. La bibliothèque est la racine ; l'éditeur, les tags et la
 * corbeille s'empilent par-dessus (le retour arrière y revient).
 */
class NotesActivity : AppCompatActivity() {

    lateinit var viewModel: NotesViewModel

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyAppStyle(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)
        enableImmersiveMode()
        followKeyboard()

        val repository = NotesRepository(NotesDatabase.getInstance(this))
        viewModel = ViewModelProvider(this, NotesViewModelFactory(repository))[NotesViewModel::class.java]

        // Toute écriture dans les notes programme une sync (si elle est permise) ; et on en fait une à l'ouverture.
        NotesSyncManager.watch(this)
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) { NotesSyncManager.syncOnOpen(applicationContext) }
            viewModel.purgeOldTrash()
            supportFragmentManager.beginTransaction()
                .replace(R.id.notes_fragment_container, NotesLibraryFragment(), TAG_LIBRARY)
                .commit()
        }
    }

    /**
     * L'écran est en plein écran : le clavier ne le redimensionne pas de lui-même. On réserve donc
     * sa hauteur en bas, pour que la barre de mise en forme reste juste au-dessus de lui.
     */
    private fun followKeyboard() {
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                if (SystemBarsManager.shouldShowSystemBars(this)) WindowInsetsCompat.Type.systemBars()
                else WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** Une feuille ou un dialogue vient de se fermer : on recache les barres système. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemBarsVisibility()
    }

    fun openNote(noteId: Long?, groupId: Long? = null, tagId: Long? = null, favorite: Boolean = false) =
        push(NoteEditorFragment.newInstance(noteId, groupId, tagId, favorite), TAG_EDITOR)

    fun openTags() = push(NotesTagsFragment(), TAG_TAGS)

    fun openTrash() = push(NotesTrashFragment(), TAG_TRASH)

    /** Ferme l'écran du dessus (éditeur, tags…) et revient à celui d'en dessous. */
    fun closeEditor() = supportFragmentManager.popBackStack()

    /** Retour direct à la bibliothèque (après avoir choisi un tag à filtrer). */
    fun backToLibrary() = supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

    fun snack(@StringRes text: Int, @StringRes action: Int, onAction: () -> Unit) {
        Snackbar.make(findViewById(R.id.notes_fragment_container), text, Snackbar.LENGTH_LONG)
            .setAction(action) { onAction() }
            .show()
    }

    private fun push(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.notes_fragment_container, fragment, tag)
            .addToBackStack(tag)
            .commit()
    }

    companion object {
        private const val TAG_LIBRARY = "library"
        private const val TAG_EDITOR = "editor"
        private const val TAG_TAGS = "tags"
        private const val TAG_TRASH = "trash"
    }
}
