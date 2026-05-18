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

class ComicsTreeActivity : ThemedActivity() {

    companion object {
        const val EXTRA_ROOT_ID = "root_id"
        const val EXTRA_ROOT_NAME = "root_name"
        private const val PREFS_NAME = "comics_tree_prefs"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val MODE_TREE = 0
        private const val MODE_GRID = 1
        private const val MIN_COLS = 1
        private const val MAX_COLS = 6
        private const val DEFAULT_COLS = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: SharedPreferences
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private var allEntries: List<ComicEntry> = emptyList()
    private var rootId: String = ""
    private var displayMode = MODE_TREE
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
        val rootName = intent.getStringExtra(EXTRA_ROOT_NAME) ?: ""

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        displayMode = prefs.getInt(KEY_DISPLAY_MODE, MODE_TREE)
        gridColumns = prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_COLS).coerceIn(MIN_COLS, MAX_COLS)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.comics_tree_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = rootName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.comics_tree_recycler)
        emptyView = findViewById(R.id.comics_tree_empty)

        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                scaleGestureDetector.onTouchEvent(e)
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        scope.launch {
            val scrollState = recycler.layoutManager?.onSaveInstanceState()
            allEntries = withContext(Dispatchers.IO) {
                ComicsDatabase.getInstance(this@ComicsTreeActivity).comicsDao().getComicsByRoot(rootId)
            }
            emptyView.visibility = if (allEntries.isEmpty()) View.VISIBLE else View.GONE
            if (allEntries.isNotEmpty()) {
                applyDisplayMode()
                if (scrollState != null) recycler.layoutManager?.onRestoreInstanceState(scrollState)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comics_tree, menu)
        toggleMenuItem = menu.findItem(R.id.action_toggle_view)
        updateToggleIcon()
        tintMenuIcons(menu)
        return true
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
            displayMode = if (displayMode == MODE_TREE) MODE_GRID else MODE_TREE
            prefs.edit { putInt(KEY_DISPLAY_MODE, displayMode) }
            applyDisplayMode()
            updateToggleIcon()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun applyDisplayMode() {
        if (displayMode == MODE_GRID) {
            recycler.layoutManager = GridLayoutManager(this, gridColumns)
            recycler.adapter = CoverGridAdapter(allEntries, scope) { entry -> openComic(entry) }
        } else {
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = ComicsTreeAdapter(
                context = this,
                scope = scope,
                entries = allEntries,
                onComicClick = { entry -> openComic(entry) }
            )
        }
    }

    private fun updateToggleIcon() {
        toggleMenuItem?.setIcon(
            if (displayMode == MODE_TREE) R.drawable.ic_view_grid else R.drawable.ic_view_list
        )
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

// ── Tree model ──────────────────────────────────────────────────────────────

private data class FolderNode(
    val name: String,
    val path: String,
    val depth: Int,
    val subFolders: MutableList<FolderNode> = mutableListOf(),
    val comics: MutableList<ComicEntry> = mutableListOf()
)

private sealed class TreeRow {
    data class Folder(
        val name: String,
        val path: String,
        val depth: Int,
        val childCount: Int,
        var expanded: Boolean = true
    ) : TreeRow()

    data class Comic(val entry: ComicEntry, val depth: Int) : TreeRow()
}

private fun buildTree(entries: List<ComicEntry>): FolderNode {
    val root = FolderNode("", "", -1)
    for (entry in entries.sortedWith(compareBy({ it.relativePath ?: "" }, { it.title }))) {
        val rp = entry.relativePath ?: ""
        val parts = if (rp.isEmpty()) emptyList() else rp.split("/")
        var cur = root
        for ((i, part) in parts.withIndex()) {
            val path = parts.take(i + 1).joinToString("/")
            var child = cur.subFolders.find { it.path == path }
            if (child == null) {
                child = FolderNode(part, path, i)
                cur.subFolders.add(child)
            }
            cur = child
        }
        cur.comics.add(entry)
    }
    return root
}

private fun flatten(node: FolderNode, expandedPaths: Set<String>, result: MutableList<TreeRow>) {
    if (node.depth >= 0) {
        val childCount = node.subFolders.size + node.comics.size
        result.add(TreeRow.Folder(
            name = node.name,
            path = node.path,
            depth = node.depth,
            childCount = childCount,
            expanded = node.path in expandedPaths
        ))
        if (node.path !in expandedPaths) return
    }
    for (sub in node.subFolders.sortedBy { it.name }) flatten(sub, expandedPaths, result)
    val comicDepth = if (node.depth < 0) 0 else node.depth + 1
    for (comic in node.comics.sortedBy { it.title }) result.add(TreeRow.Comic(comic, comicDepth))
}

// ── Tree adapter ──────────────────────────────────────────────────────────────

private class ComicsTreeAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    entries: List<ComicEntry>,
    private val onComicClick: (ComicEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val tree = buildTree(entries)
    private val expandedPaths = mutableSetOf<String>().also { set ->
        fun collectPaths(node: FolderNode) {
            if (node.depth >= 0) set.add(node.path)
            node.subFolders.forEach { collectPaths(it) }
        }
        collectPaths(tree)
    }
    private val rows = mutableListOf<TreeRow>()

    init { rebuildRows() }

    private fun rebuildRows() { rows.clear(); flatten(tree, expandedPaths, rows) }

    companion object {
        private const val TYPE_FOLDER = 0
        private const val TYPE_COMIC = 1
    }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is TreeRow.Folder -> TYPE_FOLDER
        is TreeRow.Comic -> TYPE_COMIC
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderVH(inflater.inflate(R.layout.item_comics_folder_header, parent, false))
            else -> ComicVH(inflater.inflate(R.layout.item_comic_tile, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val density = context.resources.displayMetrics.density
        when (val row = rows[position]) {
            is TreeRow.Folder -> (holder as FolderVH).bind(row, density) { path ->
                if (path in expandedPaths) expandedPaths.remove(path) else expandedPaths.add(path)
                rebuildRows()
                notifyDataSetChanged()
            }
            is TreeRow.Comic -> {
                (holder as ComicVH).bind(row.entry, row.depth, density, scope, context)
                holder.itemView.setOnClickListener { onComicClick(row.entry) }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? ComicVH)?.cancelLoad()
    }
}

private class FolderVH(view: View) : RecyclerView.ViewHolder(view) {
    val indent: View = view.findViewById(R.id.folder_indent)
    val name: TextView = view.findViewById(R.id.folder_name)
    val count: TextView = view.findViewById(R.id.folder_count)
    val chevron: ImageView = view.findViewById(R.id.folder_chevron)

    fun bind(row: TreeRow.Folder, density: Float, onToggle: (String) -> Unit) {
        indent.layoutParams = indent.layoutParams.also { it.width = ((row.depth * 20) * density).toInt() }
        name.text = row.name
        count.text = "${row.childCount}"
        chevron.setImageResource(if (row.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        itemView.setOnClickListener { onToggle(row.path) }
    }
}

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

// ── Cover thumbnail helper ───────────────────────────────────────────────────

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
                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
} catch (_: Exception) { null }

private fun folderFirstImage(context: Context, uri: Uri): Bitmap? = try {
    if (uri.scheme == "file") {
        val dir = File(uri.path ?: return null)
        val first = dir.listFiles()
            ?.filter { it.isFile && isComicImage(it.name) }
            ?.minWithOrNull(Comparator { a, b -> naturalCompare(a.name, b.name) }) ?: return null
        BitmapFactory.decodeFile(first.path, BitmapFactory.Options().apply { inSampleSize = 2 })
    } else {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return null
        val first = doc.listFiles()
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .minWithOrNull(Comparator { a, b -> naturalCompare(a.name ?: "", b.name ?: "") }) ?: return null
        context.contentResolver.openInputStream(first.uri)?.use { BitmapFactory.decodeStream(it) }
    }
} catch (_: Exception) { null }

private fun isComicImage(name: String): Boolean {
    val l = name.lowercase()
    return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")
}

private fun cbzFirstImage(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val zis = ZipInputStream(stream)
        var entry = zis.nextEntry
        var result: Bitmap? = null
        val names = mutableListOf<Pair<String, ByteArray>>()
        while (entry != null && names.size < 20) {
            val n = entry.name.lowercase()
            if (!entry.isDirectory && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) {
                names.add(entry.name to zis.readBytes())
            }
            entry = zis.nextEntry
        }
        names.minWithOrNull(Comparator { a, b -> naturalCompare(a.first, b.first) })?.second?.let { bytes ->
            result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        result
    }
} catch (_: Exception) { null }
