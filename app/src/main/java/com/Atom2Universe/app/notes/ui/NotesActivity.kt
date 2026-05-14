package com.Atom2Universe.app.notes.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NotesDatabase
import com.Atom2Universe.app.notes.repository.NotesRepository
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.notes.viewmodel.NotesViewModelFactory

class NotesActivity : AppCompatActivity() {

    lateinit var viewModel: NotesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)
        enableImmersiveMode()

        val db = NotesDatabase.getInstance(this)
        val repository = NotesRepository(db.noteDao(), db.noteGroupDao(), db.tagDao(), db.tagCategoryDao())
        viewModel = ViewModelProvider(this, NotesViewModelFactory(repository))[NotesViewModel::class.java]

        setupNavBar()

        if (savedInstanceState == null) {
            navigateToHome()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private fun setupNavBar() {
        findViewById<android.widget.ImageButton>(R.id.nav_back)?.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<TextView>(R.id.nav_home)?.setOnClickListener { navigateToHome() }
        findViewById<TextView>(R.id.nav_groups)?.setOnClickListener { navigateToGroups() }
        findViewById<TextView>(R.id.nav_favorites)?.setOnClickListener { navigateToFavorites() }
        findViewById<TextView>(R.id.nav_tags)?.setOnClickListener { navigateToTags() }
        findViewById<TextView>(R.id.nav_search)?.setOnClickListener { navigateToSearch() }
    }

    fun navigateToHome() = showFragment(NotesHomeFragment(), "home")

    fun navigateToGroups() = showFragment(GroupsFragment(), "groups")

    fun navigateToFavorites() = showFragment(FavoritesFragment(), "favorites")

    fun navigateToTags() = showFragment(TagsFragment(), "tags")

    fun navigateToSearch() = showFragment(SearchFragment(), "search")

    fun navigateToGroup(groupId: Long, groupName: String) {
        val fragment = GroupNotesFragment.newInstance(groupId, groupName)
        showFragment(fragment, "group_$groupId", addToBack = true)
    }

    fun navigateToNoteEditor(noteId: Long? = null, groupId: Long? = null) {
        val fragment = NoteEditorFragment.newInstance(noteId, groupId)
        showFragment(fragment, "editor", addToBack = true)
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment, tag: String, addToBack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.notes_fragment_container, fragment, tag)
        if (addToBack) transaction.addToBackStack(tag)
        transaction.commit()
    }

    private fun enableImmersiveMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }
}
