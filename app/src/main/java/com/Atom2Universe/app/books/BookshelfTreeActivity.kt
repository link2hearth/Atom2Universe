package com.Atom2Universe.app.books

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
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
import java.util.zip.ZipFile

class BookshelfTreeActivity : ThemedActivity() {

    companion object {
        const val EXTRA_ROOT_ID = "root_id"
        const val EXTRA_ROOT_NAME = "root_name"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookshelf_tree)
        enableImmersiveMode()

        val rootId = intent.getStringExtra(EXTRA_ROOT_ID) ?: run { finish(); return }
        val rootName = intent.getStringExtra(EXTRA_ROOT_NAME) ?: ""

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.bookshelf_tree_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = rootName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.bookshelf_tree_recycler)
        val emptyView = findViewById<TextView>(R.id.bookshelf_tree_empty)

        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                BookDatabase.getInstance(this@BookshelfTreeActivity).bookShelfDao().getEntriesByRoot(rootId)
            }
            if (entries.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                return@launch
            }
            val adapter = BookTreeAdapter(
                context = this@BookshelfTreeActivity,
                scope = scope,
                entries = entries,
                onBookClick = { entry -> openBook(entry) }
            )
            recycler.layoutManager = LinearLayoutManager(this@BookshelfTreeActivity)
            recycler.adapter = adapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun openBook(entry: BookShelfEntry) {
        val uri = if (entry.sourcePath.startsWith("/")) Uri.fromFile(File(entry.sourcePath))
                  else Uri.parse(entry.sourcePath)
        startActivity(Intent(this, BookReaderActivity::class.java).apply {
            putExtra(BookReaderActivity.EXTRA_BOOK_URI, uri.toString())
        })
    }
}

// ── Tree model ────────────────────────────────────────────────────────────────

private data class BookFolderNode(
    val name: String,
    val path: String,
    val depth: Int,
    val subFolders: MutableList<BookFolderNode> = mutableListOf(),
    val books: MutableList<BookShelfEntry> = mutableListOf()
)

private sealed class BookTreeRow {
    data class Folder(
        val name: String,
        val path: String,
        val depth: Int,
        val childCount: Int,
        var expanded: Boolean = true
    ) : BookTreeRow()

    data class Book(val entry: BookShelfEntry, val depth: Int) : BookTreeRow()
}

private fun buildBookTree(entries: List<BookShelfEntry>): BookFolderNode {
    val root = BookFolderNode("", "", -1)
    for (entry in entries.sortedWith(compareBy({ it.relativePath }, { it.title }))) {
        val rp = entry.relativePath
        val parts = if (rp.isEmpty()) emptyList() else rp.split("/")
        var cur = root
        for ((i, part) in parts.withIndex()) {
            val path = parts.take(i + 1).joinToString("/")
            var child = cur.subFolders.find { it.path == path }
            if (child == null) {
                child = BookFolderNode(part, path, i)
                cur.subFolders.add(child)
            }
            cur = child
        }
        cur.books.add(entry)
    }
    return root
}

private fun flattenBookTree(
    node: BookFolderNode,
    expandedPaths: Set<String>,
    result: MutableList<BookTreeRow>
) {
    if (node.depth >= 0) {
        val childCount = node.subFolders.size + node.books.size
        result.add(BookTreeRow.Folder(
            name = node.name,
            path = node.path,
            depth = node.depth,
            childCount = childCount,
            expanded = node.path in expandedPaths
        ))
        if (node.path !in expandedPaths) return
    }
    for (sub in node.subFolders.sortedBy { it.name }) {
        flattenBookTree(sub, expandedPaths, result)
    }
    val bookDepth = if (node.depth < 0) 0 else node.depth + 1
    for (book in node.books.sortedBy { it.title }) {
        result.add(BookTreeRow.Book(book, bookDepth))
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

private class BookTreeAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    entries: List<BookShelfEntry>,
    private val onBookClick: (BookShelfEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val tree = buildBookTree(entries)
    private val expandedPaths = mutableSetOf<String>().also { set ->
        fun collectPaths(node: BookFolderNode) {
            if (node.depth >= 0) set.add(node.path)
            node.subFolders.forEach { collectPaths(it) }
        }
        collectPaths(tree)
    }
    private val rows = mutableListOf<BookTreeRow>()

    init { rebuildRows() }

    private fun rebuildRows() {
        rows.clear()
        flattenBookTree(tree, expandedPaths, rows)
    }

    companion object {
        private const val TYPE_FOLDER = 0
        private const val TYPE_BOOK = 1
    }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is BookTreeRow.Folder -> TYPE_FOLDER
        is BookTreeRow.Book -> TYPE_BOOK
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderVH(inflater.inflate(R.layout.item_book_folder_header, parent, false))
            else -> BookVH(inflater.inflate(R.layout.item_book_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val density = context.resources.displayMetrics.density
        when (val row = rows[position]) {
            is BookTreeRow.Folder -> (holder as FolderVH).bind(row, density) { path ->
                if (path in expandedPaths) expandedPaths.remove(path) else expandedPaths.add(path)
                rebuildRows()
                notifyDataSetChanged()
            }
            is BookTreeRow.Book -> {
                (holder as BookVH).bind(row.entry, row.depth, density, scope, context)
                holder.itemView.setOnClickListener { onBookClick(row.entry) }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? BookVH)?.cancelLoad()
    }
}

private class FolderVH(view: View) : RecyclerView.ViewHolder(view) {
    val indent: View = view.findViewById(R.id.folder_indent)
    val name: TextView = view.findViewById(R.id.folder_name)
    val count: TextView = view.findViewById(R.id.folder_count)
    val chevron: ImageView = view.findViewById(R.id.folder_chevron)

    fun bind(row: BookTreeRow.Folder, density: Float, onToggle: (String) -> Unit) {
        indent.layoutParams = indent.layoutParams.also { it.width = ((row.depth * 20) * density).toInt() }
        name.text = row.name
        count.text = "${row.childCount}"
        chevron.setImageResource(if (row.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        itemView.setOnClickListener { onToggle(row.path) }
    }
}

private class BookVH(view: View) : RecyclerView.ViewHolder(view) {
    val cover: ImageView = view.findViewById(R.id.book_cover)
    val title: TextView = view.findViewById(R.id.book_title)
    val author: TextView = view.findViewById(R.id.book_author)
    val size: TextView = view.findViewById(R.id.book_size)
    val progress: ProgressBar = view.findViewById(R.id.book_progress)
    val progressText: TextView = view.findViewById(R.id.book_progress_text)
    val date: TextView = view.findViewById(R.id.book_date)
    var loadJob: Job? = null

    fun bind(entry: BookShelfEntry, depth: Int, density: Float, scope: CoroutineScope, context: Context) {
        (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.marginStart = ((depth * 20) * density).toInt() + (10 * density).toInt()
            itemView.layoutParams = lp
        }
        title.text = entry.title
        author.text = entry.author.ifEmpty { entry.format.uppercase() }
        size.text = formatSize(entry.fileSize)
        progress.progress = entry.progressPercent
        progressText.text = if (entry.progressPercent > 0) "${entry.progressPercent}%" else ""
        date.text = if (entry.lastOpenedAt > 0)
            DateUtils.getRelativeTimeSpanString(entry.lastOpenedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        else ""

        setPlaceholder(cover)
        loadJob?.cancel()
        loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadCover(context, entry) }
            if (bmp != null) {
                cover.scaleType = ImageView.ScaleType.CENTER_CROP
                cover.setPadding(0, 0, 0, 0)
                cover.clearColorFilter()
                cover.setImageBitmap(bmp)
            }
        }
    }

    fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun setPlaceholder(iv: ImageView) {
        val dp = iv.resources.displayMetrics.density
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iv.setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        iv.setImageResource(R.drawable.ic_hub_books)
        iv.setColorFilter(Color.argb(100, 255, 255, 255))
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024f * 1024f))
    }
}

// ── Cover loader ─────────────────────────────────────────────────────────────

private fun loadCover(context: Context, entry: BookShelfEntry): Bitmap? {
    // Cached cover saved during a previous load
    val cacheFile = File(context.filesDir, "book_covers/${entry.id}.jpg")
    if (cacheFile.exists()) {
        return BitmapFactory.decodeFile(cacheFile.path, BitmapFactory.Options().apply { inSampleSize = 2 })
    }
    // Pre-extracted cover from scan (EPUB)
    entry.coverPath?.let { path ->
        val f = File(path)
        if (f.exists()) return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })
    }

    val bmp = when (entry.format) {
        "epub" -> loadEpubCover(entry)
        "pdf" -> {
            val uri = entryUri(entry)
            loadPdfCover(context, uri)
        }
        else -> null
    } ?: return null

    // Cache for next time
    cacheFile.parentFile?.mkdirs()
    try { FileOutputStream(cacheFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } } catch (_: Exception) {}
    return bmp
}

private fun entryUri(entry: BookShelfEntry): Uri =
    if (entry.sourcePath.startsWith("/")) Uri.fromFile(File(entry.sourcePath))
    else Uri.parse(entry.sourcePath)

private fun loadEpubCover(entry: BookShelfEntry): Bitmap? {
    if (!entry.sourcePath.startsWith("/")) return null
    return try {
        ZipFile(File(entry.sourcePath)).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return null
            val containerText = zip.getInputStream(container).readBytes().decodeToString()
            val opfPath = Regex("""full-path="([^"]+\.opf)"""").find(containerText)?.groupValues?.get(1)
                ?: return null
            val opfEntry = zip.getEntry(opfPath) ?: return null
            val opfText = zip.getInputStream(opfEntry).readBytes().decodeToString()

            val coverId = Regex("""<meta[^>]+name="cover"[^>]+content="([^"]+)"""").find(opfText)?.groupValues?.get(1)
                ?: Regex("""<meta[^>]+content="([^"]+)"[^>]+name="cover"""").find(opfText)?.groupValues?.get(1)
                ?: return null
            val coverHref = Regex("""<item[^>]+id="$coverId"[^>]+href="([^"]+)"""").find(opfText)?.groupValues?.get(1)
                ?: Regex("""<item[^>]+href="([^"]+)"[^>]+id="$coverId"""").find(opfText)?.groupValues?.get(1)
                ?: return null

            val opfDir = opfPath.substringBeforeLast("/", "")
            val coverKey = if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"
            val imgEntry = zip.getEntry(coverKey)
                ?: zip.entries().asSequence().find { it.name.endsWith(coverKey.substringAfterLast("/")) }
                ?: return null
            val bytes = zip.getInputStream(imgEntry).readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
        }
    } catch (_: Exception) { null }
}

private fun loadPdfCover(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
        PdfRenderer(fd).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val scale = minOf(400f / page.width, 600f / page.height)
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
