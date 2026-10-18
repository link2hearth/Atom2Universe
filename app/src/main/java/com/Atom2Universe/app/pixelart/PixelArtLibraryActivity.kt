package com.Atom2Universe.app.pixelart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.Atom2Universe.app.ThemedActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.io.DeviceImages
import com.Atom2Universe.app.pixelart.io.ImageExport
import com.Atom2Universe.app.pixelart.io.ImageFormat
import com.Atom2Universe.app.pixelart.io.OpenImageForEdit
import com.Atom2Universe.app.pixelart.io.ProjectSummary
import com.Atom2Universe.app.pixelart.io.SourceLink
import com.Atom2Universe.app.pixelart.ui.PixelArtStorage
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.checkerDrawable
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.pixelDrawable
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.pixelart.ui.showNewProjectSheet
import com.Atom2Universe.app.cloud.projects.CloudModule
import com.Atom2Universe.app.cloud.projects.CloudProjectSync
import com.Atom2Universe.app.cloud.projects.CloudProjectsUi
import com.Atom2Universe.app.pixelart.io.PixelArtCloudAdapter
import com.Atom2Universe.app.pixelart.ui.showSheetImportSheet
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La galerie des dessins : chaque projet est une carte. C'est l'entrée du module — on y crée un
 * dessin, on ouvre un projet, ou on ouvre une image de l'appareil pour la retoucher puis
 * l'enregistrer par-dessus.
 */
class PixelArtLibraryActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val store by lazy { PixelArtStorage.store(this) }
    private val cloud by lazy { PixelArtCloudAdapter(store) }
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var adapter: ProjectsAdapter
    private var pendingExportId: String? = null

    /** Sélecteur de fichiers : donne le droit de réécrire l'image, donc « Enregistrer » peut l'écraser. */
    private val openImage = registerForActivityResult(OpenImageForEdit()) { uri -> uri?.let { openImageAsProject(it) } }
    /** Sélecteur de galerie : plus pratique pour trouver une image, mais en lecture seule (pas d'écrasement). */
    private val openImageFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { openImageAsProject(it) } }
    private val importProject = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importProjectFile(it) } }
    private val importSheet = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { importSpriteSheet(it) } }
    private val exportProject = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = pendingExportId
        if (uri != null && id != null) exportProjectTo(id, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_pixel_art_library)

        list = findViewById(R.id.lib_list)
        empty = findViewById(R.id.lib_empty)
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        list.layoutManager = GridLayoutManager(this, (widthDp / 170f).toInt().coerceAtLeast(2))
        adapter = ProjectsAdapter(this, ::openProject, ::showProjectMenu)
        list.adapter = adapter

        findViewById<View>(R.id.lib_btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.lib_btn_more).setOnClickListener { showMainMenu() }
        findViewById<View>(R.id.lib_fab_new).setOnClickListener { newProject() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            // L'éditeur qu'on vient de quitter écrit peut-être encore : on attend qu'il ait fini.
            PixelArtStorage.writesInFlight.first { it == 0 }
            val items = withContext(Dispatchers.IO) { store.list() }
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openProject(id: String) {
        startActivity(PixelArtEditorActivity.intent(this, id))
    }

    private fun newProject() {
        val default = getString(R.string.px_untitled_n, adapter.itemCount + 1)
        showNewProjectSheet(default, onImportCloud = { CloudProjectsUi.showImportSheet(this, cloud, ::reload) }) { name, w, h ->
            lifecycleScope.launch {
                val p = withContext(Dispatchers.IO) { store.create(name, w, h, getString(R.string.px_layer_name, 1)) }
                openProject(p.meta.id)
            }
        }
    }

    private fun showMainMenu() {
        actionSheet(null, listOf(
            SheetItem(R.drawable.ic_px_folder_open, getString(R.string.px_open_image)) { showOpenImageChoice() },
            SheetItem(R.drawable.ic_px_grid, getString(R.string.px_import_sheet)) { importSheet.launch("image/*") },
            SheetItem(R.drawable.ic_px_import, getString(R.string.px_import_project)) { importProject.launch("*/*") },
        )).show()
    }

    private fun showOpenImageChoice() {
        actionSheet(getString(R.string.px_open_image), listOf(
            SheetItem(R.drawable.ic_px_image, getString(R.string.px_open_from_gallery)) { openImageFromGallery.launch("image/*") },
            SheetItem(R.drawable.ic_px_folder_open, getString(R.string.px_open_from_files)) { openImage.launch(arrayOf("image/*")) },
        )).show()
    }

    private fun showProjectMenu(item: ProjectSummary) {
        actionSheet(item.name, listOf(
            SheetItem(R.drawable.ic_px_pencil, getString(R.string.px_open)) { openProject(item.id) },
            SheetItem(R.drawable.ic_px_rename, getString(R.string.px_rename)) {
                promptText(R.string.px_rename, item.name, R.string.px_ok) { name ->
                    if (name.isNotEmpty()) lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.rename(item.id, name) }
                        reload()
                    }
                }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.px_duplicate)) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.duplicate(item.id, getString(R.string.px_copy_of, item.name)) }
                    reload()
                }
            },
            SheetItem(R.drawable.ic_cloud, getString(R.string.cloud_proj_sync)) {
                CloudProjectsUi.syncProject(this, cloud, item.id, item.name, ::reload)
            },
            SheetItem(R.drawable.ic_px_export, getString(R.string.px_export_project)) {
                pendingExportId = item.id
                exportProject.launch("${item.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.a2upix.zip")
            },
            SheetItem(R.drawable.ic_cloud_off, getString(R.string.cloud_proj_remove)) {
                CloudProjectsUi.removeFromCloud(this, CloudModule.PIXEL_ART, item.id, item.name, ::reload)
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.px_delete), destructive = true) {
                confirm(R.string.px_delete_project_title, getString(R.string.px_delete_project_message, item.name), R.string.px_delete, true) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { store.delete(item.id) }
                        reload()
                    }
                }
            },
        )).show()
    }

    // ---- Importer ------------------------------------------------------------------------------------

    /** Ouvre une image de l'appareil : le projet garde le lien, « Enregistrer » l'écrasera. */
    private fun openImageAsProject(uri: Uri) {
        val writable = DeviceImages.keepAccess(this, uri)
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) { DeviceImages.decode(this@PixelArtLibraryActivity, uri) }
            if (d == null) { Toast.makeText(this@PixelArtLibraryActivity, R.string.px_open_failed, Toast.LENGTH_SHORT).show(); return@launch }
            val fileName = DeviceImages.displayName(this@PixelArtLibraryActivity, uri) ?: getString(R.string.px_untitled)
            // Un GIF n'est lu qu'à sa première image : l'écraser détruirait l'animation, on ne le relie donc pas.
            val format = d.format ?: ImageFormat.PNG
            // Pas de lien si on ne peut pas réécrire le fichier (galerie) : « Enregistrer » proposera « Enregistrer sous ».
            val link = if (format == ImageFormat.GIF || !writable) null else SourceLink(uri.toString(), fileName, format, 1, true)
            val p = withContext(Dispatchers.IO) {
                store.createFromPixels(fileName.substringBeforeLast('.'), d.width, d.height, d.pixels, link, getString(R.string.px_layer_name, 1))
            }
            openProject(p.meta.id)
        }
    }

    private fun importSpriteSheet(uri: Uri) {
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) { DeviceImages.decode(this@PixelArtLibraryActivity, uri) }
            if (d == null) { Toast.makeText(this@PixelArtLibraryActivity, R.string.px_open_failed, Toast.LENGTH_SHORT).show(); return@launch }
            showSheetImportSheet(d.width, d.height) { cols, rows ->
                lifecycleScope.launch {
                    val cells = ImageExport.splitSheet(d.pixels, d.width, d.height, cols, rows)
                    if (cells.isEmpty()) return@launch
                    val name = (DeviceImages.displayName(this@PixelArtLibraryActivity, uri) ?: getString(R.string.px_untitled)).substringBeforeLast('.')
                    val p = withContext(Dispatchers.IO) { store.createFromFrames(name, d.width / cols, d.height / rows, cells, getString(R.string.px_layer_name, 1)) }
                    openProject(p.meta.id)
                }
            }
        }
    }

    private fun importProjectFile(uri: Uri) {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { store.importZip(it, getString(R.string.px_untitled)) }
                } catch (e: Exception) { null }
            }
            if (id == null) Toast.makeText(this@PixelArtLibraryActivity, R.string.px_open_failed, Toast.LENGTH_SHORT).show()
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
            if (!ok) Toast.makeText(this@PixelArtLibraryActivity, R.string.px_export_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

private class ProjectsAdapter(
    private val context: Context,
    private val onOpen: (String) -> Unit,
    private val onMenu: (ProjectSummary) -> Unit,
) : RecyclerView.Adapter<ProjectsAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.project_thumb)
        val badge: ImageView = v.findViewById(R.id.project_link_badge)
        val cloudBadge: ImageView = v.findViewById(R.id.project_cloud_badge)
        val menu: View = v.findViewById(R.id.project_menu)
        val name: TextView = v.findViewById(R.id.project_name)
        val info: TextView = v.findViewById(R.id.project_info)
    }

    private var items: List<ProjectSummary> = emptyList()
    private val thumbs = object : LruCache<String, Bitmap>(48) {}
    private val store = PixelArtStorage.store(context)

    fun submit(list: List<ProjectSummary>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_px_project, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val p = items[position]
        h.name.text = p.name
        val sb = StringBuilder(context.getString(R.string.px_subtitle_size, p.width, p.height))
        if (p.frames > 1) sb.append(" · ").append(context.resources.getQuantityString(R.plurals.px_frames_count, p.frames, p.frames))
        sb.append(" · ").append(DateUtils.getRelativeTimeSpanString(p.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
        h.info.text = sb.toString()
        h.badge.visibility = if (p.linkName != null) View.VISIBLE else View.GONE
        when (CloudProjectSync.badge(context, CloudModule.PIXEL_ART, p.id, p.modified)) {
            CloudProjectSync.Badge.NONE -> h.cloudBadge.visibility = View.GONE
            CloudProjectSync.Badge.SYNCED -> { h.cloudBadge.setImageResource(R.drawable.ic_cloud_done); h.cloudBadge.visibility = View.VISIBLE }
            CloudProjectSync.Badge.PENDING -> { h.cloudBadge.setImageResource(R.drawable.ic_cloud_upload); h.cloudBadge.visibility = View.VISIBLE }
        }
        h.thumb.background = context.checkerDrawable(context.dp(6))
        val key = p.id + p.modified
        val cached = thumbs.get(key)
        if (cached != null) h.thumb.setImageDrawable(context.pixelDrawable(cached)) else {
            h.thumb.setImageDrawable(null)
            val f = store.thumbFile(p.id)
            (context as? AppCompatActivity)?.lifecycleScope?.launch {
                val bmp = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inScaled = false })
                }
                if (bmp != null) {
                    thumbs.put(key, bmp)
                    if (h.bindingAdapterPosition == position) h.thumb.setImageDrawable(context.pixelDrawable(bmp))
                }
            }
        }
        h.itemView.setOnClickListener { onOpen(p.id) }
        h.itemView.setOnLongClickListener { onMenu(p); true }
        h.menu.setOnClickListener { onMenu(p) }
    }
}
