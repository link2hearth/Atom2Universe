package com.Atom2Universe.app.comics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import androidx.core.content.edit

data class ComicSeriesFolder(
    val displayName: String,
    val pathPrefix: String,
    val comicCount: Int
)

class ComicsTreeActivity : ThemedActivity() {

    companion object {
        const val EXTRA_ROOT_ID = "root_id"
        const val EXTRA_ROOT_NAME = "root_name"
        private const val PREFS_NAME = "comics_tree_prefs"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val MODE_LIST = 0
        private const val MODE_GRID = 1
        private const val MIN_COLS = 1
        private const val MAX_COLS = 6
        private const val DEFAULT_COLS = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: SharedPreferences
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var foldersContainer: View
    private lateinit var foldersRecycler: RecyclerView
    private lateinit var foldersEmpty: TextView
    private lateinit var contentContainer: View
    private var allEntries: List<ComicEntry> = emptyList()
    private var currentFolderPath: String? = null
    private var currentFilteredEntries: List<ComicEntry> = emptyList()
    private var hasFolderLevel = false
    private var rootId: String = ""
    private var rootName: String = ""
    private var displayMode = MODE_LIST
    private var gridColumns = DEFAULT_COLS
    private var toggleMenuItem: MenuItem? = null
    private var scaleFactor = 1f

    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaleFactor = 1f
                return displayMode == MODE_GRID
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (displayMode != MODE_GRID) return false
                scaleFactor *= detector.scaleFactor
                when {
                    scaleFactor < 0.8f -> {
                        setGridColumns((gridColumns + 1).coerceIn(MIN_COLS, MAX_COLS))
                        scaleFactor = 1f
                    }
                    scaleFactor > 1.25f -> {
                        setGridColumns((gridColumns - 1).coerceIn(MIN_COLS, MAX_COLS))
                        scaleFactor = 1f
                    }
                }
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comics_tree)
        enableImmersiveMode()

        rootId = intent.getStringExtra(EXTRA_ROOT_ID) ?: run { finish(); return }
        rootName = intent.getStringExtra(EXTRA_ROOT_NAME) ?: ""

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        displayMode = prefs.getInt(KEY_DISPLAY_MODE, MODE_LIST)
        gridColumns = prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_COLS).coerceIn(MIN_COLS, MAX_COLS)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.comics_tree_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = rootName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { handleBack() }

        foldersContainer = findViewById(R.id.comics_folders_container)
        foldersRecycler = findViewById(R.id.comics_folders_recycler)
        foldersEmpty = findViewById(R.id.comics_folders_empty)
        contentContainer = findViewById(R.id.comics_content_container)
        recycler = findViewById(R.id.comics_tree_recycler)
        emptyView = findViewById(R.id.comics_tree_empty)

        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                scaleGestureDetector.onTouchEvent(e)
                return false
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun handleBack() {
        if (hasFolderLevel && currentFolderPath != null) {
            currentFolderPath = null
            currentFilteredEntries = emptyList()
            contentContainer.visibility = View.GONE
            foldersContainer.visibility = View.VISIBLE
            supportActionBar?.title = rootName
            invalidateOptionsMenu()
        } else {
            finish()
        }
    }

    // ── Chargement des données ────────────────────────────────────────────────

    private fun loadData() {
        scope.launch {
            allEntries = withContext(Dispatchers.IO) {
                ComicsDatabase.getInstance(this@ComicsTreeActivity).comicsDao().getComicsByRoot(rootId)
            }
            if (allEntries.isEmpty()) {
                showFoldersContainer()
                foldersEmpty.visibility = View.VISIBLE
                foldersRecycler.visibility = View.GONE
                return@launch
            }
            // Si on revient dans un dossier déjà ouvert, le ré-ouvrir
            val fp = currentFolderPath
            if (fp != null) {
                openFolderPath(fp)
            } else {
                showFolderLevel(allEntries)
            }
        }
    }

    // ── Niveau 1 : liste des dossiers ─────────────────────────────────────────

    private fun showFolderLevel(entries: List<ComicEntry>) {
        val folderMap = mutableMapOf<String, Int>()
        var rootCount = 0
        for (e in entries) {
            val rp = e.relativePath ?: ""
            if (rp.isEmpty()) { rootCount++; continue }
            val first = rp.split("/").first()
            folderMap[first] = (folderMap[first] ?: 0) + 1
        }

        // Pas de sous-dossiers → affichage direct de toutes les BD
        if (folderMap.isEmpty()) {
            hasFolderLevel = false
            currentFolderPath = ""
            currentFilteredEntries = entries
            showContentContainer()
            applyDisplayMode()
            return
        }

        hasFolderLevel = true
        val folders = mutableListOf<ComicSeriesFolder>()
        if (rootCount > 0) {
            folders.add(ComicSeriesFolder(getString(R.string.comics_section_root), "", rootCount))
        }
        folderMap.entries.sortedBy { it.key }.forEach { (name, count) ->
            folders.add(ComicSeriesFolder(name, name, count))
        }

        showFoldersContainer()
        foldersEmpty.visibility = View.GONE
        foldersRecycler.visibility = View.VISIBLE
        foldersRecycler.layoutManager = LinearLayoutManager(this)
        foldersRecycler.adapter = ComicSeriesFoldersAdapter(folders, getString(R.string.comics_series_count)) { folder ->
            openFolderPath(folder.pathPrefix)
        }
    }

    // ── Niveau 2 : BD d'un dossier ───────────────────────────────────────────

    private fun openFolderPath(path: String) {
        currentFolderPath = path
        currentFilteredEntries = if (path.isEmpty()) {
            allEntries.filter { (it.relativePath ?: "").isEmpty() }
        } else {
            allEntries.filter {
                val rp = it.relativePath ?: ""
                rp == path || rp.startsWith("$path/")
            }
        }
        supportActionBar?.title = if (path.isEmpty()) getString(R.string.comics_section_root)
                                   else path.split("/").last()
        showContentContainer()
        invalidateOptionsMenu()
        applyDisplayMode()
    }

    private fun showFoldersContainer() {
        foldersContainer.visibility = View.VISIBLE
        contentContainer.visibility = View.GONE
    }

    private fun showContentContainer() {
        foldersContainer.visibility = View.GONE
        contentContainer.visibility = View.VISIBLE
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comics_tree, menu)
        toggleMenuItem = menu.findItem(R.id.action_toggle_view)
        updateToggleIcon()
        tintMenuIcons(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        toggleMenuItem?.isVisible = currentFolderPath != null
        return super.onPrepareOptionsMenu(menu)
    }

    private fun tintMenuIcons(menu: Menu) {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(R.attr.a2uMidiAccent, typedValue, true)
        val tint = android.content.res.ColorStateList.valueOf(typedValue.data)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.let { icon ->
                icon.mutate()
                androidx.core.graphics.drawable.DrawableCompat.setTintList(icon, tint)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_toggle_view) {
            displayMode = if (displayMode == MODE_LIST) MODE_GRID else MODE_LIST
            prefs.edit { putInt(KEY_DISPLAY_MODE, displayMode) }
            applyDisplayMode()
            updateToggleIcon()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateToggleIcon() {
        toggleMenuItem?.setIcon(
            if (displayMode == MODE_LIST) R.drawable.ic_view_grid else R.drawable.ic_view_list
        )
    }

    // ── Affichage des BD ──────────────────────────────────────────────────────

    private fun applyDisplayMode() {
        val entries = currentFilteredEntries
        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            return
        }
        emptyView.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        val folderPath = currentFolderPath ?: ""
        if (displayMode == MODE_GRID) {
            recycler.layoutManager = GridLayoutManager(this, gridColumns)
            recycler.adapter = CoverGridAdapter(entries, scope) { entry -> openComic(entry) }
        } else {
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = ComicBrowseAdapter(
                context = this,
                scope = scope,
                entries = entries,
                folderPath = folderPath,
                onComicClick = { entry -> openComic(entry) }
            )
        }
    }

    private fun setGridColumns(cols: Int) {
        if (cols == gridColumns) return
        gridColumns = cols
        prefs.edit { putInt(KEY_GRID_COLUMNS, cols) }
        (recycler.layoutManager as? GridLayoutManager)?.spanCount = cols
        recycler.post { recycler.invalidateItemDecorations(); recycler.requestLayout() }
    }

    private fun openComic(entry: ComicEntry) {
        startActivity(Intent(this, ComicsReaderActivity::class.java).apply {
            putExtra(ComicsReaderActivity.EXTRA_COMIC_ID, entry.id)
            putExtra(ComicsReaderActivity.EXTRA_COMIC_SOURCE, entry.sourcePath)
            putExtra(ComicsReaderActivity.EXTRA_COMIC_FORMAT, entry.format)
            putExtra(ComicsReaderActivity.EXTRA_COMIC_TITLE, entry.title)
            putExtra(ComicsReaderActivity.EXTRA_COMIC_PAGE, entry.currentPage)
        })
    }
}

// ── Adapter : liste des dossiers/séries ──────────────────────────────────────

private class ComicSeriesFoldersAdapter(
    private val folders: List<ComicSeriesFolder>,
    private val countFormat: String,
    private val onFolderClick: (ComicSeriesFolder) -> Unit
) : RecyclerView.Adapter<ComicSeriesFoldersAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.series_folder_name)
        val count: TextView = view.findViewById(R.id.series_folder_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_comics_series_folder, parent, false))

    override fun getItemCount() = folders.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val folder = folders[position]
        holder.name.text = folder.displayName
        holder.count.text = countFormat.format(folder.comicCount)
        holder.itemView.setOnClickListener { onFolderClick(folder) }
    }
}

// ── Browse rows (BD + sections non-collapsibles) ──────────────────────────────

private sealed class ComicBrowseRow {
    data class SectionHeader(val name: String, val count: Int) : ComicBrowseRow()
    data class ComicItem(val entry: ComicEntry) : ComicBrowseRow()
}

private fun buildBrowseRows(entries: List<ComicEntry>, folderPath: String): List<ComicBrowseRow> {
    val rows = mutableListOf<ComicBrowseRow>()

    val direct = entries.filter { (it.relativePath ?: "") == folderPath }.sortedBy { it.title }
    direct.forEach { rows.add(ComicBrowseRow.ComicItem(it)) }

    val sub = entries.filter { (it.relativePath ?: "") != folderPath }
    if (sub.isNotEmpty()) {
        val groups = sub.groupBy { entry ->
            val rp = entry.relativePath ?: ""
            val remainder = if (folderPath.isEmpty()) rp else rp.removePrefix("$folderPath/")
            remainder.split("/").first()
        }
        groups.keys.sorted().forEach { sectionName ->
            val sectionComics = groups[sectionName]!!.sortedBy { it.title }
            rows.add(ComicBrowseRow.SectionHeader(sectionName, sectionComics.size))
            sectionComics.forEach { rows.add(ComicBrowseRow.ComicItem(it)) }
        }
    }

    return rows
}

// ── Adapter : BD d'un dossier avec sections ───────────────────────────────────

private class ComicBrowseAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    entries: List<ComicEntry>,
    folderPath: String,
    private val onComicClick: (ComicEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = buildBrowseRows(entries, folderPath)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_COMIC = 1
    }

    override fun getItemViewType(p: Int) = if (rows[p] is ComicBrowseRow.SectionHeader) TYPE_HEADER else TYPE_COMIC
    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            BrowseSectionVH(inf.inflate(R.layout.item_comics_folder_header, parent, false))
        else
            ComicVH(inf.inflate(R.layout.item_comic_tile, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val density = context.resources.displayMetrics.density
        when (val row = rows[position]) {
            is ComicBrowseRow.SectionHeader -> (holder as BrowseSectionVH).bind(row)
            is ComicBrowseRow.ComicItem -> {
                (holder as ComicVH).bind(row.entry, 0, density, scope, context)
                holder.itemView.setOnClickListener { onComicClick(row.entry) }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? ComicVH)?.cancelLoad()
    }
}

private class BrowseSectionVH(view: View) : RecyclerView.ViewHolder(view) {
    val indent: View = view.findViewById(R.id.folder_indent)
    val name: TextView = view.findViewById(R.id.folder_name)
    val count: TextView = view.findViewById(R.id.folder_count)
    val chevron: ImageView = view.findViewById(R.id.folder_chevron)

    fun bind(row: ComicBrowseRow.SectionHeader) {
        indent.layoutParams = indent.layoutParams.also { it.width = 0 }
        name.text = row.name
        count.text = "${row.count}"
        chevron.visibility = View.GONE
        itemView.isClickable = false
    }
}

// ── Cover grid adapter ────────────────────────────────────────────────────────

private class CoverGridAdapter(
    private val entries: List<ComicEntry>,
    private val scope: CoroutineScope,
    private val onComicClick: (ComicEntry) -> Unit
) : RecyclerView.Adapter<CoverGridAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.comic_grid_cover)
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_comic_cover, parent, false))

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        holder.cover.scaleType = ImageView.ScaleType.CENTER_CROP
        holder.cover.setImageResource(R.drawable.ic_hub_comics)
        holder.loadJob?.cancel()
        holder.loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadCover(holder.cover.context, entry) }
            if (bmp != null) holder.cover.setImageBitmap(bmp)
        }
        holder.itemView.setOnClickListener { onComicClick(entry) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
        holder.loadJob = null
    }
}

// ── ComicVH ───────────────────────────────────────────────────────────────────

private class ComicVH(view: View) : RecyclerView.ViewHolder(view) {
    val cover: ImageView = view.findViewById(R.id.comic_tile_cover)
    val title: TextView = view.findViewById(R.id.comic_tile_title)
    val format: TextView = view.findViewById(R.id.comic_tile_format)
    val pages: TextView = view.findViewById(R.id.comic_tile_pages)
    val progress: ProgressBar = view.findViewById(R.id.comic_tile_progress)
    val progressText: TextView = view.findViewById(R.id.comic_tile_progress_text)
    val date: TextView = view.findViewById(R.id.comic_tile_date)
    var loadJob: Job? = null

    fun bind(entry: ComicEntry, depth: Int, density: Float, scope: CoroutineScope, context: Context) {
        (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.marginStart = ((depth * 20) * density).toInt() + (10 * density).toInt()
            itemView.layoutParams = lp
        }
        title.text = entry.title
        format.text = entry.format.uppercase()
        val ctx = itemView.context
        pages.text = ctx.getString(R.string.comics_page_counter, entry.currentPage + 1, entry.totalPages)
        progress.progress = entry.progressPercent
        progressText.text = "${entry.progressPercent}%"
        date.text = if (entry.lastOpenedAt > 0)
            DateUtils.getRelativeTimeSpanString(entry.lastOpenedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        else ""

        cover.setImageResource(R.drawable.ic_hub_comics)
        loadJob?.cancel()
        loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadCover(context, entry) }
            if (bmp != null) cover.setImageBitmap(bmp)
        }
    }

    fun cancelLoad() { loadJob?.cancel(); loadJob = null }
}

// ── Cover thumbnail helper ────────────────────────────────────────────────────

private fun loadCover(context: Context, entry: ComicEntry): Bitmap? {
    val cacheFile = File(context.filesDir, "comics_covers/${entry.id}.jpg")
    if (cacheFile.exists()) {
        return BitmapFactory.decodeFile(cacheFile.path, BitmapFactory.Options().apply { inSampleSize = 2 })
    }
    val bmp = entry.firstImagePath?.let { path ->
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })
    } ?: when (entry.format) {
        "pdf" -> {
            val uri = if (entry.sourcePath.startsWith("/")) Uri.fromFile(File(entry.sourcePath)) else Uri.parse(entry.sourcePath)
            pdfFirstPage(context, uri)
        }
        "folder" -> {
            val uri = if (entry.sourcePath.startsWith("/")) Uri.fromFile(File(entry.sourcePath)) else Uri.parse(entry.sourcePath)
            folderFirstImage(context, uri)
        }
        "cbz" -> {
            val uri = if (entry.sourcePath.startsWith("/")) Uri.fromFile(File(entry.sourcePath)) else Uri.parse(entry.sourcePath)
            cbzFirstImage(context, uri)
        }
        else -> null
    } ?: return null
    cacheFile.parentFile?.mkdirs()
    try { FileOutputStream(cacheFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } } catch (_: Exception) {}
    return bmp
}

private fun pdfFirstPage(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
        PdfRenderer(fd).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val scale = 600f / maxOf(page.width, page.height).coerceAtLeast(1)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
} catch (_: Exception) { null }

private fun folderFirstImage(context: Context, uri: Uri): Bitmap? = try {
    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
    if (uri.scheme == "file") {
        val dir = File(uri.path ?: return null)
        val first = dir.listFiles()
            ?.filter { it.isFile && isComicImage(it.name) }
            ?.minWithOrNull(Comparator { a, b -> naturalCompare(a.name, b.name) }) ?: return null
        BitmapFactory.decodeFile(first.path, opts)
    } else {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return null
        val first = doc.listFiles()
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .minWithOrNull(Comparator { a, b -> naturalCompare(a.name ?: "", b.name ?: "") }) ?: return null
        context.contentResolver.openInputStream(first.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
} catch (_: Exception) { null }

private fun isComicImage(name: String): Boolean {
    val l = name.lowercase()
    return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")
}

private fun cbzFirstImage(context: Context, uri: Uri): Bitmap? {
    return try {
        // Passe 1 : collecter les noms des entrées images sans lire leurs bytes
        val imageNames = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val zis = ZipInputStream(stream)
            var entry = zis.nextEntry
            while (entry != null) {
                val n = entry.name.lowercase()
                if (!entry.isDirectory && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) {
                    imageNames.add(entry.name)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val target = imageNames.minWithOrNull(Comparator { a, b -> naturalCompare(a, b) })
            ?: return null
        // Passe 2 : décoder uniquement l'entrée cible avec subsample
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val zis = ZipInputStream(stream)
            var entry = zis.nextEntry
            var result: Bitmap? = null
            while (entry != null && result == null) {
                if (entry.name == target) {
                    result = BitmapFactory.decodeStream(zis, null, opts)
                } else {
                    zis.closeEntry()
                }
                entry = zis.nextEntry
            }
            result
        }
    } catch (_: Exception) { null }
}
