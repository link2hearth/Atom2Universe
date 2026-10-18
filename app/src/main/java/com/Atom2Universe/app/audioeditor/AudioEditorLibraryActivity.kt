package com.Atom2Universe.app.audioeditor

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.Atom2Universe.app.ThemedActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.io.AudioImporter
import com.Atom2Universe.app.audioeditor.io.LegacyMigration
import com.Atom2Universe.app.audioeditor.io.ProjectSummary
import com.Atom2Universe.app.audioeditor.ui.ProgressHandle
import com.Atom2Universe.app.audioeditor.ui.progressDialog
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.primaryButton
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.pixelart.ui.scrollRow
import com.Atom2Universe.app.pixelart.ui.secondaryButton
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * La galerie des projets audio : chaque projet est une carte (vignette = forme d'onde du mixage). C'est
 * l'entrée du module : on y crée un projet, on importe des fichiers audio ou un projet partagé, et on
 * ouvre un projet pour l'éditer.
 *
 * Pas de recréation à la rotation (voir le manifeste) : un import ou une migration en cours garde sa
 * boîte de progression.
 */
class AudioEditorLibraryActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val store by lazy { AudioEditorStorage.store(this) }
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var adapter: ProjectsAdapter

    /** Ce que la feuille « Nouveau » a demandé, en attendant le choix des fichiers. */
    private var pendingName: String? = null
    private var pendingRate = 44100
    private var pendingExportId: String? = null
    private var progress: ProgressHandle? = null

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importAudioAsProject(uris, pendingName, pendingRate)
    }
    private val importProject = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importProjectFile(it) } }
    private val exportProject = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = pendingExportId
        if (uri != null && id != null) exportProjectTo(id, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_audio_editor_library)

        list = findViewById(R.id.lib_list)
        empty = findViewById(R.id.lib_empty)
        list.layoutManager = GridLayoutManager(this, spanCount())
        adapter = ProjectsAdapter(this, ::openProject, ::showProjectMenu)
        list.adapter = adapter

        findViewById<View>(R.id.lib_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.lib_btn_more).setOnClickListener { showMainMenu() }
        findViewById<View>(R.id.lib_fab_new).setOnClickListener { newProject() }

        migrateLegacyProject()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        progress?.dismiss()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (list.layoutManager as? GridLayoutManager)?.spanCount = spanCount()
    }

    private fun spanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (widthDp / 170f).toInt().coerceAtLeast(2)
    }

    private fun reload() {
        lifecycleScope.launch {
            // L'éditeur qu'on vient de quitter écrit peut-être encore : on attend qu'il ait fini.
            AudioEditorStorage.writesInFlight.first { it == 0 }
            val items = withContext(Dispatchers.IO) { store.list() }
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openProject(id: String, record: Boolean = false) {
        startActivity(AudioEditorActivity.intent(this, id, record))
    }

    // ---- Nouveau projet ------------------------------------------------------------------------------

    private fun newProject() {
        val default = getString(R.string.ae_untitled_n, adapter.itemCount + 1)
        var rate = 44100
        bottomSheet(getString(R.string.ae_new_title)) { root, dlg ->
            val nameField = EditText(this).apply {
                setText(default)
                setSingleLine()
                hint = getString(R.string.ae_name_hint)
                setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
                setSelectAllOnFocus(true)
            }
            root.addView(nameField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            root.addView(label(getString(R.string.ae_sample_rate), 12f).apply { setPadding(dp(2), dp(16), 0, dp(6)) })
            val chips = ArrayList<Pair<Int, TextView>>()
            fun select(r: Int) {
                rate = r
                chips.forEach { (v, c) -> c.isSelected = v == r }
            }
            for ((value, text) in listOf(44100 to R.string.ae_rate_44100, 48000 to R.string.ae_rate_48000)) {
                chips.add(value to chip(getString(text)) { select(value) })
            }
            select(44100)
            root.addView(scrollRow(*chips.map { it.second }.toTypedArray()))

            fun chosenName() = nameField.text.toString().trim().ifEmpty { default }

            root.addView(primaryButton(getString(R.string.ae_create_empty)) {
                val name = chosenName()
                val r = rate
                dlg.dismiss()
                lifecycleScope.launch {
                    val p = withContext(Dispatchers.IO) { store.create(name, r) }
                    openProject(p.id)
                    reload()
                }
            })
            val recordRow = LinearLayout(this)
            recordRow.addView(secondaryButton(getString(R.string.ae_start_recording), R.drawable.ic_ae_record) {
                val name = chosenName()
                val r = rate
                dlg.dismiss()
                lifecycleScope.launch {
                    val p = withContext(Dispatchers.IO) { store.create(name, r) }
                    openProject(p.id, record = true)
                    reload()
                }
            })
            root.addView(recordRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            val importRow = LinearLayout(this)
            importRow.addView(secondaryButton(getString(R.string.ae_import_files), R.drawable.ic_px_import) {
                pendingName = chosenName()
                pendingRate = rate
                dlg.dismiss()
                pickAudio.launch(AUDIO_MIME_TYPES)
            })
            root.addView(importRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }.show()
    }

    private fun showMainMenu() {
        actionSheet(null, listOf(
            SheetItem(R.drawable.ic_px_folder_open, getString(R.string.ae_menu_open_audio)) {
                // Le projet prendra le nom du premier fichier.
                pendingName = null
                pendingRate = 44100
                pickAudio.launch(AUDIO_MIME_TYPES)
            },
            SheetItem(R.drawable.ic_px_import, getString(R.string.ae_menu_import_project)) { importProject.launch("*/*") },
        )).show()
    }

    private fun showProjectMenu(item: ProjectSummary) {
        actionSheet(item.name, listOf(
            SheetItem(R.drawable.ic_px_pencil, getString(R.string.ae_open)) { openProject(item.id) },
            SheetItem(R.drawable.ic_px_rename, getString(R.string.ae_rename)) {
                promptText(R.string.ae_rename, item.name, R.string.px_ok) { name ->
                    if (name.isNotEmpty()) lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.rename(item.id, name) }
                        reload()
                    }
                }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.ae_duplicate)) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.duplicate(item.id, getString(R.string.ae_copy_of, item.name)) }
                    reload()
                }
            },
            SheetItem(R.drawable.ic_px_export, getString(R.string.ae_export_project)) {
                pendingExportId = item.id
                exportProject.launch("${item.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.a2uaudio.zip")
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.ae_delete), destructive = true) {
                confirm(R.string.ae_delete_project_title, getString(R.string.ae_delete_project_message, item.name), R.string.ae_delete, true) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.delete(item.id) }
                        reload()
                    }
                }
            },
        )).show()
    }

    // ---- Importer ------------------------------------------------------------------------------------

    /**
     * Crée un projet et y importe [uris], un fichier par piste, posés à 0. [name] `null` : le projet prend
     * le nom du premier fichier. Annuler efface le projet à moitié importé. Un fichier illisible est
     * sauté ; si aucun ne passe, il n'y a pas de projet.
     */
    private fun importAudioAsProject(uris: List<Uri>, name: String?, rate: Int) {
        val cancel = AtomicBoolean(false)
        val dialog = progressDialog(R.string.ae_import_files) { cancel.set(true) }
        progress = dialog
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) { runImport(uris, name, rate, cancel, dialog) }
            dialog.dismiss()
            progress = null
            if (id == null && !cancel.get()) Toast.makeText(this@AudioEditorLibraryActivity, R.string.ae_import_failed, Toast.LENGTH_SHORT).show()
            if (id != null) openProject(id)
            reload()
        }
    }

    private fun runImport(uris: List<Uri>, name: String?, rate: Int, cancel: AtomicBoolean, dialog: ProgressHandle): String? {
        val created = store.create(name.orEmpty(), rate)
        val dir = store.dir(created.id)
        val importer = AudioImporter(this)
        var project = created.project
        var firstName: String? = null
        try {
            for ((i, uri) in uris.withIndex()) {
                if (cancel.get()) throw CancellationException()
                val label = importer.displayName(uri)
                val head = getString(R.string.ae_import_progress, i + 1, uris.size, label)
                runOnUiThread { dialog.update(head, i.toFloat() / uris.size) }
                val ctx = RunContext(
                    rate,
                    progress = { f -> runOnUiThread { dialog.update(head, (i + f) / uris.size) } },
                    isCancelled = { cancel.get() },
                )
                val r = try {
                    importer.import(uri, dir, rate, ctx)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    continue
                }
                val title = r.displayName.ifBlank { getString(R.string.ae_track_n, project.tracks.size + 1) }
                val (p1, tid) = project.addSource(r.source).addTrack(title)
                project = p1.addClip(tid, r.source.id, 0, 0, r.source.frames, title).first
                if (firstName == null && r.displayName.isNotBlank()) firstName = r.displayName
            }
        } catch (e: Throwable) {
            store.delete(created.id)
            if (e is CancellationException) return null
            throw e
        }
        if (project.tracks.isEmpty()) {
            store.delete(created.id)
            return null
        }
        val finalName = name?.takeIf { it.isNotBlank() } ?: firstName ?: getString(R.string.ae_untitled_n, store.list().size)
        store.save(created.id, project, created.meta.copy(name = finalName))
        store.updateThumb(created.id, project)
        return created.id
    }

    private fun importProjectFile(uri: Uri) {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { store.importZip(it, getString(R.string.ae_untitled_n, store.list().size + 1)) }
                } catch (e: Exception) { null }
            }
            if (id == null) Toast.makeText(this@AudioEditorLibraryActivity, R.string.ae_open_failed, Toast.LENGTH_SHORT).show()
            reload()
        }
    }

    private fun exportProjectTo(id: String, uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { store.exportZip(id, it) } != null
                } catch (e: Exception) { false }
            }
            if (!ok) Toast.makeText(this@AudioEditorLibraryActivity, R.string.ae_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Ancien projet -------------------------------------------------------------------------------

    /**
     * Si l'ancien éditeur a laissé un projet, il devient un projet de la galerie : une seule tentative,
     * pour ne pas relancer une conversion qui échouerait à chaque ouverture. [migrating] évite deux
     * conversions simultanées si l'écran est rouvert pendant l'opération.
     */
    private fun migrateLegacyProject() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_DONE, false)) return
        if (LegacyMigration.pending(filesDir).isEmpty()) {
            prefs.edit().putBoolean(KEY_LEGACY_DONE, true).apply()
            return
        }
        if (migrating) return
        migrating = true
        val cancel = AtomicBoolean(false)
        val dialog = progressDialog(R.string.ae_migrating) { cancel.set(true) }
        progress = dialog
        val importer = AudioImporter(this)
        val name = getString(R.string.ae_legacy_project_name)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val ctx = RunContext(
                        44100,
                        progress = { f -> runOnUiThread { dialog.update("", f) } },
                        isCancelled = { cancel.get() },
                    )
                    LegacyMigration.migrate(filesDir, store, name, 44100, ctx) { file, dir, c -> importer.importFile(file, dir, 44100, c) }
                } catch (e: CancellationException) {
                    // Annulée : l'ancien projet reste intact, on ne retentera pas à chaque ouverture.
                } catch (e: Exception) {
                    // Échec : même chose, rien n'a été effacé.
                } finally {
                    migrating = false
                }
            }
            prefs.edit().putBoolean(KEY_LEGACY_DONE, true).apply()
            dialog.dismiss()
            progress = null
            reload()
        }
    }

    private companion object {
        val AUDIO_MIME_TYPES = arrayOf("audio/*", "video/*", "application/ogg")
        const val PREFS = "audio_editor_prefs"
        const val KEY_LEGACY_DONE = "legacy_migration_done"

        @Volatile var migrating = false
    }
}

private class ProjectsAdapter(
    private val context: Context,
    private val onOpen: (String) -> Unit,
    private val onMenu: (ProjectSummary) -> Unit,
) : RecyclerView.Adapter<ProjectsAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.project_thumb)
        val menu: View = v.findViewById(R.id.project_menu)
        val name: TextView = v.findViewById(R.id.project_name)
        val info: TextView = v.findViewById(R.id.project_info)
    }

    private var items: List<ProjectSummary> = emptyList()
    private val thumbs = object : LruCache<String, Bitmap>(48) {}
    private val store = AudioEditorStorage.store(context)

    fun submit(list: List<ProjectSummary>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_audio_project, parent, false))

    private fun duration(seconds: Double): String {
        val s = seconds.toLong()
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        return if (h > 0) context.getString(R.string.ae_time_hmmss, h, m, sec) else context.getString(R.string.ae_time_mss, m, sec)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val p = items[position]
        h.name.text = p.name
        h.info.text = context.getString(
            R.string.ae_project_info,
            context.resources.getQuantityString(R.plurals.ae_tracks_count, p.trackCount, p.trackCount),
            duration(p.seconds),
            DateUtils.getRelativeTimeSpanString(p.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        )
        val key = p.id + p.modified
        val cached = thumbs.get(key)
        if (cached != null) h.thumb.setImageBitmap(cached) else {
            h.thumb.setImageDrawable(null)
            val f = store.thumbFile(p.id)
            (context as? AppCompatActivity)?.lifecycleScope?.launch {
                val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path) }
                if (bmp != null) {
                    thumbs.put(key, bmp)
                    if (h.bindingAdapterPosition == position) h.thumb.setImageBitmap(bmp)
                }
            }
        }
        h.itemView.setOnClickListener { onOpen(p.id) }
        h.itemView.setOnLongClickListener { onMenu(p); true }
        h.menu.setOnClickListener { onMenu(p) }
    }
}
