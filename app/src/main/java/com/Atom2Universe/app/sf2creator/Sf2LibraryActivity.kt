package com.Atom2Universe.app.sf2creator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.accentColor
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.primaryButton
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.pixelart.ui.secondaryButton
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectClipboard
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectCopier
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectOverview
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import com.Atom2Universe.app.sf2creator.ui.Sf2KeyboardThumbDrawable
import com.Atom2Universe.app.sf2creator.ui.Sf2ProjectActions
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.updateSystemBarsVisibility
import kotlinx.coroutines.launch

/**
 * The SF2 projects gallery, like the other creative modules: each project is a card showing
 * on a keyboard the keys that have a sound. New projects, SF2 import, and copying the
 * content of a project into another one start here.
 */
class Sf2LibraryActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val repository by lazy { Sf2ProjectRepository(this) }
    private lateinit var empty: View
    private lateinit var adapter: ProjectsAdapter

    private var exportProjectId: Long = -1

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) startActivity(Sf2CreatorActivity.importIntent(this, uri))
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val projectId = exportProjectId
        if (uri == null || projectId < 0) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = Sf2ProjectActions.exportToUri(this@Sf2LibraryActivity, projectId, uri)
            Toast.makeText(this@Sf2LibraryActivity, if (ok) R.string.sf2_save_as_success else R.string.sf2_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyAppStyle(this)
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_sf2_library)

        val list = findViewById<RecyclerView>(R.id.sf2_lib_list)
        empty = findViewById(R.id.sf2_lib_empty)
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        list.layoutManager = GridLayoutManager(this, (widthDp / 170f).toInt().coerceAtLeast(2))
        adapter = ProjectsAdapter(::openProject, ::showProjectMenu)
        list.adapter = adapter

        findViewById<View>(R.id.sf2_lib_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.sf2_lib_fab_new).setOnClickListener { newProject() }
        exportProjectId = savedInstanceState?.getLong(STATE_EXPORT, -1) ?: -1
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_EXPORT, exportProjectId)
    }

    /** A menu, sheet or dialog was closed: hide the system bars again. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemBarsVisibility()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val projects = repository.getAllProjects()
            adapter.submit(projects, repository.getProjectOverviews())
            empty.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openProject(id: Long) {
        startActivity(Sf2CreatorActivity.openIntent(this, id))
    }

    private fun newProject() {
        bottomSheet(getString(R.string.sf2_new_project)) { root, dialog ->
            val defaultName = getString(R.string.sf2_project_default_name, adapter.itemCount + 1)
            val name = EditText(this).apply {
                setText(defaultName)
                setSelectAllOnFocus(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine()
                setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
                hint = getString(R.string.sf2_project_name_hint)
            }
            root.addView(name)
            root.addView(primaryButton(getString(R.string.midi_create)) {
                dialog.dismiss()
                val n = name.text.toString().trim().ifEmpty { defaultName }
                lifecycleScope.launch { openProject(repository.createProject(n)) }
            })
            val row = LinearLayout(this)
            row.addView(secondaryButton(getString(R.string.sf2_import), R.drawable.ic_px_import) {
                dialog.dismiss()
                importLauncher.launch(arrayOf("*/*"))
            })
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }.show()
    }

    private fun showProjectMenu(project: Sf2ProjectEntity) {
        val clipboard = Sf2ProjectClipboard.get(this)
        val items = mutableListOf(
            SheetItem(R.drawable.ic_px_pencil, getString(R.string.px_open)) { openProject(project.id) },
            SheetItem(R.drawable.ic_px_rename, getString(R.string.px_rename)) {
                promptText(R.string.px_rename, project.name, R.string.px_ok) { name ->
                    if (name.isNotEmpty()) lifecycleScope.launch {
                        repository.updateProject(project.copy(name = name, modifiedAt = System.currentTimeMillis()))
                        reload()
                    }
                }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.px_duplicate)) {
                lifecycleScope.launch {
                    Sf2ProjectCopier(this@Sf2LibraryActivity).duplicateProject(project.id, getString(R.string.px_copy_of, project.name))
                    reload()
                }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.sf2_copy_all_programs)) {
                lifecycleScope.launch {
                    val programs = repository.getProgramsForProject(project.id)
                    if (programs.isNotEmpty()) {
                        Sf2ProjectActions.copyPrograms(this@Sf2LibraryActivity, project.id, programs.map { it.id }, programs.map { it.name })
                        Toast.makeText(this@Sf2LibraryActivity, R.string.sf2_copied, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        if (clipboard?.kind == Sf2ProjectClipboard.Kind.PROGRAMS) {
            items += SheetItem(R.drawable.ic_px_paste, getString(R.string.sf2_paste_into, clipboard.label)) {
                lifecycleScope.launch {
                    val outcome = Sf2ProjectActions.paste(this@Sf2LibraryActivity, project.id, null, null)
                    val message = Sf2ProjectActions.failureMessage(this@Sf2LibraryActivity, outcome)
                        ?: getString(R.string.sf2_pasted_into_project, project.name)
                    Toast.makeText(this@Sf2LibraryActivity, message, Toast.LENGTH_SHORT).show()
                    reload()
                }
            }
        }
        items += SheetItem(R.drawable.ic_px_export, getString(R.string.sf2_export_project)) {
            exportProjectId = project.id
            exportLauncher.launch(Sf2ProjectActions.fileName(project.name))
        }
        items += SheetItem(R.drawable.ic_px_delete, getString(R.string.px_delete), destructive = true) {
            confirm(R.string.sf2_delete_project, getString(R.string.sf2_delete_project_confirm, project.name), R.string.px_delete, true) {
                lifecycleScope.launch {
                    repository.deleteProject(project.id)
                    reload()
                }
            }
        }
        actionSheet(project.name, items).show()
    }

    private inner class ProjectsAdapter(
        private val onOpen: (Long) -> Unit,
        private val onMenu: (Sf2ProjectEntity) -> Unit
    ) : RecyclerView.Adapter<ProjectsAdapter.Holder>() {

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(R.id.project_thumb)
            val menu: View = v.findViewById(R.id.project_menu)
            val name: TextView = v.findViewById(R.id.project_name)
            val info: TextView = v.findViewById(R.id.project_info)
        }

        private var projects: List<Sf2ProjectEntity> = emptyList()
        private var overviews: Map<Long, Sf2ProjectOverview> = emptyMap()

        fun submit(list: List<Sf2ProjectEntity>, overview: Map<Long, Sf2ProjectOverview>) {
            projects = list
            overviews = overview
            notifyDataSetChanged()
        }

        override fun getItemCount() = projects.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_px_project, parent, false).also {
                it.findViewById<View>(R.id.project_link_badge).visibility = View.GONE
                it.findViewById<View>(R.id.project_cloud_badge).visibility = View.GONE
            }
        )

        override fun onBindViewHolder(h: Holder, position: Int) {
            val p = projects[position]
            val overview = overviews[p.id]
            val context = h.itemView.context
            h.name.text = p.name
            h.info.text = context.getString(
                R.string.sf2_project_card_info,
                context.getString(R.string.sf2_programs_count, overview?.programCount ?: 0),
                context.getString(R.string.sf2_samples_count, overview?.sampleCount ?: 0),
                DateUtils.getRelativeTimeSpanString(p.modifiedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            )
            h.thumb.setImageDrawable(Sf2KeyboardThumbDrawable(overview?.coveredKeys ?: BooleanArray(128), context.accentColor()))
            h.itemView.setOnClickListener { onOpen(p.id) }
            h.itemView.setOnLongClickListener { onMenu(p); true }
            h.menu.setOnClickListener { onMenu(p) }
        }
    }

    companion object {
        private const val STATE_EXPORT = "export_project"

        fun intent(context: Context) = Intent(context, Sf2LibraryActivity::class.java)
    }
}
