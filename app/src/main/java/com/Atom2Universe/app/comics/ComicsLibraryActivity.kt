package com.Atom2Universe.app.comics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComicsLibraryActivity : ThemedActivity() {

    companion object {
        fun updateProgress(context: Context, id: String, page: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                ComicsDatabase.getInstance(context.applicationContext).comicsDao()
                    .updateProgress(id, page, System.currentTimeMillis())
            }
        }
    }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val items = mutableListOf<LibItem>()
    private lateinit var adapter: LibraryAdapter

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addComicFile(uri)
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) pendingFolderAction?.invoke(uri).also { pendingFolderAction = null }
    }
    private var pendingFolderAction: ((Uri) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comics_library)
        enableImmersiveMode()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.comics_library_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.comics_recycler)
        emptyText = findViewById(R.id.comics_empty_text)

        adapter = LibraryAdapter(
            items,
            onRootClick = { root -> openRoot(root) },
            onRootLongClick = { root -> showRootOptions(root) },
            onComicClick = { entry -> openComic(entry) },
            onComicLongClick = { entry -> confirmDelete(entry) }
        )

        val gridManager = GridLayoutManager(this, 2)
        gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = when (adapter.getItemViewType(position)) {
                LibraryAdapter.TYPE_ROOT -> 2
                else -> 1
            }
        }
        recycler.layoutManager = gridManager
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add_comic).setOnClickListener { showAddDialog() }

        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comics_library, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_comics_clear_all) {
            confirmClearAll()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun loadData() {
        scope.launch {
            val dao = ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao()
            val roots = withContext(Dispatchers.IO) { dao.getAllRoots() }
            val library = withContext(Dispatchers.IO) { dao.getAllComics() }
            items.clear()
            roots.forEach { root ->
                val count = library.count { it.rootId == root.id }
                items.add(LibItem.Root(root, count))
            }
            library.filter { it.rootId == null }.forEach { items.add(LibItem.Standalone(it)) }
            adapter.notifyDataSetChanged()
            emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddDialog() {
        val options = arrayOf(
            getString(R.string.comics_add_pdf),
            getString(R.string.comics_add_cbz),
            getString(R.string.comics_add_folder),
            getString(R.string.comics_add_library)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.comics_add_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFile.launch(arrayOf("application/pdf"))
                    1 -> pickFile.launch(arrayOf("application/zip", "application/x-cbz", "*/*"))
                    2 -> { pendingFolderAction = { uri -> addComicFolder(uri) }; pickFolder.launch(null) }
                    3 -> { pendingFolderAction = { uri -> addLibraryRoot(uri) }; pickFolder.launch(null) }
                }
            }
            .show()
    }

    // ── Single comic / folder ──────────────────────────────────────────────

    private fun addComicFile(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        scope.launch {
            val entry = withContext(Dispatchers.IO) { buildEntryFromFile(uri) } ?: return@launch
            withContext(Dispatchers.IO) {
                ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao().insertComic(entry)
            }
            loadData()
        }
    }

    private fun addComicFolder(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        scope.launch {
            val entry = withContext(Dispatchers.IO) { buildEntryFromFolder(uri) } ?: return@launch
            withContext(Dispatchers.IO) {
                ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao().insertComic(entry)
            }
            loadData()
        }
    }

    private fun buildEntryFromFile(uri: Uri): ComicEntry? {
        val doc = DocumentFile.fromSingleUri(this, uri) ?: return null
        val name = doc.name ?: uri.lastPathSegment ?: "Comic"
        val title = name.substringBeforeLast(".")
        val format = if (name.endsWith(".pdf", true)) "pdf" else "cbz"
        val pages = if (format == "pdf") countPdfPages(uri) else countCbzPages(uri)
        return ComicEntry(title = title, sourcePath = uri.toString(), format = format, totalPages = pages)
    }

    private fun buildEntryFromFolder(uri: Uri): ComicEntry? {
        val doc = DocumentFile.fromTreeUri(this, uri) ?: return null
        val title = doc.name ?: "Comics"
        val count = doc.listFiles().count { it.isFile && it.type?.startsWith("image/") == true }
        return ComicEntry(title = title, sourcePath = uri.toString(), format = "folder", totalPages = count)
    }

    // ── Root options (long press) ─────────────────────────────────────────

    private fun showRootOptions(root: ComicsRootLibrary) {
        val options = arrayOf(getString(R.string.comics_rescan), getString(R.string.comics_delete_confirm))
        AlertDialog.Builder(this)
            .setTitle(root.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> rescanRoot(root)
                    1 -> confirmDeleteRoot(root)
                }
            }
            .show()
    }

    private fun rescanRoot(root: ComicsRootLibrary) {
        val uri = Uri.parse(root.rootUri)
        val (countText, nameText, scanDialog) = buildScanDialog(R.string.comics_rescanning_title)
        scanDialog.show()
        scope.launch {
            val (newRoot, entries) = withContext(Dispatchers.IO) {
                scanLibraryRoot(uri, existingRootId = root.id) { count, lastName ->
                    runOnUiThread {
                        countText.text = getString(R.string.comics_scanning_found, count)
                        nameText.text = lastName
                    }
                }
            }
            scanDialog.dismiss()
            val dao = ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao()
            withContext(Dispatchers.IO) {
                dao.deleteComicsByRoot(root.id)
                dao.insertComics(entries)
                dao.updateRoot(newRoot.copy(comicCount = entries.size, lastOpenedAt = System.currentTimeMillis()))
            }
            loadData()
        }
    }

    // ── Library root (recursive scan) ─────────────────────────────────────

    private fun addLibraryRoot(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val (countText, nameText, scanDialog) = buildScanDialog(R.string.comics_scanning_title)
        scanDialog.show()
        scope.launch {
            val (root, entries) = withContext(Dispatchers.IO) {
                scanLibraryRoot(uri) { count, lastName ->
                    runOnUiThread {
                        countText.text = getString(R.string.comics_scanning_found, count)
                        nameText.text = lastName
                    }
                }
            }
            scanDialog.dismiss()
            val dao = ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao()
            withContext(Dispatchers.IO) {
                dao.insertRoot(root)
                dao.insertComics(entries)
            }
            loadData()
        }
    }

    private fun buildScanDialog(titleRes: Int): Triple<TextView, TextView, AlertDialog> {
        val countText = TextView(this).apply {
            text = getString(R.string.comics_scanning_found, 0)
            setPadding(64, 20, 64, 8); textSize = 14f
            gravity = android.view.Gravity.CENTER
        }
        val nameText = TextView(this).apply {
            text = ""; setPadding(64, 0, 64, 32); textSize = 11f; alpha = 0.55f
            gravity = android.view.Gravity.CENTER; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER; setPadding(48, 36, 48, 0)
            addView(android.widget.ProgressBar(this@ComicsLibraryActivity).apply { isIndeterminate = true })
            addView(countText); addView(nameText)
        }
        val dialog = AlertDialog.Builder(this).setTitle(titleRes).setView(container).setCancelable(false).create()
        return Triple(countText, nameText, dialog)
    }

    // ── Clear all ─────────────────────────────────────────────────────────

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.comics_clear_all)
            .setMessage(R.string.comics_clear_all_msg)
            .setPositiveButton(R.string.comics_delete_confirm) { _, _ ->
                scope.launch {
                    val dao = ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao()
                    withContext(Dispatchers.IO) { dao.deleteAllComics(); dao.deleteAllRoots() }
                    loadData()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Convertit un tree URI SAF en File (primary:path → /storage/emulated/0/path) ──

    private fun treeUriToFile(uri: Uri): java.io.File? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri) ?: return null
            val parts = docId.split(":")
            if (parts.size < 2) return null
            val base = when (parts[0].lowercase()) {
                "primary" -> "/storage/emulated/0"
                else -> "/storage/${parts[0]}"
            }
            java.io.File("$base/${parts[1]}")
        } catch (_: Exception) { null }
    }

    private fun scanLibraryRoot(uri: Uri, existingRootId: String? = null, onFound: (count: Int, lastName: String) -> Unit): Pair<ComicsRootLibrary, List<ComicEntry>> {
        val rootFile = treeUriToFile(uri)
        if (rootFile != null && rootFile.isDirectory) {
            return fastScan(rootFile, uri.toString(), existingRootId, onFound)
        }
        val rootDoc = DocumentFile.fromTreeUri(this, uri) ?: return Pair(
            ComicsRootLibrary(name = "Unknown", rootUri = uri.toString()), emptyList()
        )
        return safScan(rootDoc, uri.toString(), existingRootId, onFound)
    }

    // ── Scan rapide via File API (MANAGE_EXTERNAL_STORAGE) ───────────────────

    private fun fastScan(
        rootFile: java.io.File,
        rootUriStr: String,
        existingRootId: String? = null,
        onFound: (Int, String) -> Unit
    ): Pair<ComicsRootLibrary, List<ComicEntry>> {
        val root = if (existingRootId != null)
            ComicsRootLibrary(id = existingRootId, name = rootFile.name, rootUri = rootUriStr)
        else
            ComicsRootLibrary(name = rootFile.name, rootUri = rootUriStr)
        val entries = mutableListOf<ComicEntry>()
        val imageFolders = LinkedHashMap<java.io.File, MutableList<java.io.File>>()
        val archiveFiles = mutableListOf<java.io.File>()

        rootFile.walkTopDown().maxDepth(12).forEach { file ->
            if (!file.isFile || file.parentFile == rootFile) return@forEach
            when {
                isImageFile(file.name) -> imageFolders.getOrPut(file.parentFile!!) { mutableListOf() }.add(file)
                file.name.endsWith(".pdf", true) -> {
                    val rel = parentRelPath(file.parentFile!!, rootFile)
                    entries.add(ComicEntry(title = file.nameWithoutExtension, sourcePath = file.absolutePath, format = "pdf", totalPages = 0, rootId = root.id, relativePath = rel))
                    onFound(entries.size, file.nameWithoutExtension)
                }
                file.name.endsWith(".cbz", true) || file.name.endsWith(".zip", true) -> archiveFiles.add(file)
            }
        }

        for ((dir, images) in imageFolders) {
            images.sortWith(Comparator { a, b -> naturalCompare(a.name, b.name) })
            val rel = parentRelPath(dir, rootFile)
            entries.add(ComicEntry(title = dir.name, sourcePath = dir.absolutePath, format = "folder", totalPages = images.size, rootId = root.id, relativePath = rel, firstImagePath = images[0].absolutePath))
            onFound(entries.size, dir.name)
        }

        for (cbz in archiveFiles.sortedBy { it.absolutePath }) {
            val rel = parentRelPath(cbz.parentFile!!, rootFile)
            entries.add(ComicEntry(title = cbz.nameWithoutExtension, sourcePath = cbz.absolutePath, format = "cbz", totalPages = 0, rootId = root.id, relativePath = rel))
            onFound(entries.size, cbz.nameWithoutExtension)
        }

        return Pair(root.copy(comicCount = entries.size), entries)
    }

    private fun parentRelPath(dir: java.io.File, root: java.io.File): String = try {
        val parent = dir.parentFile ?: return ""
        if (parent == root) "" else parent.relativeTo(root).path.replace(java.io.File.separator, "/")
    } catch (_: Exception) { "" }

    private fun isImageFile(name: String): Boolean {
        val l = name.lowercase()
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") || l.endsWith(".gif")
    }

    // ── Fallback SAF (pour contenus non-accessibles via File) ──────────────

    private fun safScan(rootDoc: DocumentFile, rootUriStr: String, existingRootId: String? = null, onFound: (Int, String) -> Unit): Pair<ComicsRootLibrary, List<ComicEntry>> {
        val root = if (existingRootId != null)
            ComicsRootLibrary(id = existingRootId, name = rootDoc.name ?: "Comics", rootUri = rootUriStr)
        else
            ComicsRootLibrary(name = rootDoc.name ?: "Comics", rootUri = rootUriStr)
        val entries = mutableListOf<ComicEntry>()
        safScanDir(rootDoc, "", root.id, entries, 0, onFound)
        return Pair(root.copy(comicCount = entries.size), entries)
    }

    private fun safScanDir(dir: DocumentFile, parentRelPath: String, rootId: String, results: MutableList<ComicEntry>, depth: Int, onFound: (Int, String) -> Unit) {
        if (depth > 10) return
        val children = dir.listFiles()
        val images = children.filter { it.isFile && it.type?.startsWith("image/") == true }
        val pdfs = children.filter { it.isFile && (it.type == "application/pdf" || it.name?.endsWith(".pdf", true) == true) }
        val cbzs = children.filter { it.isFile && (it.name?.endsWith(".cbz", true) == true || it.name?.endsWith(".zip", true) == true) }
        val subDirs = children.filter { it.isDirectory }
        if (depth > 0 && images.isNotEmpty()) {
            val e = ComicEntry(title = dir.name ?: "Unknown", sourcePath = dir.uri.toString(), format = "folder", totalPages = images.size, rootId = rootId, relativePath = parentRelPath)
            results.add(e); onFound(results.size, e.title)
        }
        for (f in pdfs) {
            val e = ComicEntry(title = f.name?.substringBeforeLast(".") ?: "PDF", sourcePath = f.uri.toString(), format = "pdf", totalPages = 0, rootId = rootId, relativePath = parentRelPath)
            results.add(e); onFound(results.size, e.title)
        }
        for (f in cbzs) {
            val e = ComicEntry(title = f.name?.substringBeforeLast(".") ?: "CBZ", sourcePath = f.uri.toString(), format = "cbz", totalPages = 0, rootId = rootId, relativePath = parentRelPath)
            results.add(e); onFound(results.size, e.title)
        }
        val myRel = if (depth == 0) "" else if (parentRelPath.isEmpty()) (dir.name ?: "") else "$parentRelPath/${dir.name ?: ""}"
        for (sub in subDirs.sortedBy { it.name }) safScanDir(sub, myRel, rootId, results, depth + 1, onFound)
    }

    // ── Navigation ────────────────────────────────────────────────────────

    private fun openRoot(root: ComicsRootLibrary) {
        scope.launch {
            withContext(Dispatchers.IO) {
                ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao()
                    .updateRoot(root.copy(lastOpenedAt = System.currentTimeMillis()))
            }
            startActivity(Intent(this@ComicsLibraryActivity, ComicsTreeActivity::class.java).apply {
                putExtra(ComicsTreeActivity.EXTRA_ROOT_ID, root.id)
                putExtra(ComicsTreeActivity.EXTRA_ROOT_NAME, root.name)
            })
        }
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

    // ── Delete ────────────────────────────────────────────────────────────

    private fun confirmDeleteRoot(root: ComicsRootLibrary) {
        AlertDialog.Builder(this)
            .setTitle(R.string.comics_delete_title)
            .setMessage(getString(R.string.comics_delete_root_msg, root.name))
            .setPositiveButton(R.string.comics_delete_confirm) { _, _ ->
                scope.launch {
                    val dao = ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao()
                    withContext(Dispatchers.IO) { dao.deleteRoot(root.id); dao.deleteComicsByRoot(root.id) }
                    loadData()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(entry: ComicEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.comics_delete_title)
            .setMessage(getString(R.string.comics_delete_msg, entry.title))
            .setPositiveButton(R.string.comics_delete_confirm) { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ComicsDatabase.getInstance(this@ComicsLibraryActivity).comicsDao().deleteComic(entry.id)
                    }
                    loadData()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Page count helpers ────────────────────────────────────────────────

    private fun countPdfPages(uri: Uri): Int = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { it.pageCount }
        } ?: 0
    } catch (e: Exception) { 0 }

    private fun countCbzPages(uri: Uri): Int = try {
        contentResolver.openInputStream(uri)?.use { stream ->
            val zis = java.util.zip.ZipInputStream(stream)
            var count = 0
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isImageName(entry.name)) count++
                entry = zis.nextEntry
            }
            count
        } ?: 0
    } catch (e: Exception) { 0 }

    private fun isImageName(name: String): Boolean {
        val l = name.lowercase()
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") || l.endsWith(".gif")
    }
}

// ── Data model for main library list ─────────────────────────────────────────

sealed class LibItem {
    data class Root(val lib: ComicsRootLibrary, val count: Int) : LibItem()
    data class Standalone(val entry: ComicEntry) : LibItem()
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class LibraryAdapter(
    private val items: List<LibItem>,
    private val onRootClick: (ComicsRootLibrary) -> Unit,
    private val onRootLongClick: (ComicsRootLibrary) -> Unit,
    private val onComicClick: (ComicEntry) -> Unit,
    private val onComicLongClick: (ComicEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_ROOT = 0
        const val TYPE_STANDALONE = 1
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is LibItem.Root -> TYPE_ROOT
        is LibItem.Standalone -> TYPE_STANDALONE
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ROOT -> RootVH(inflater.inflate(R.layout.item_comics_root_card, parent, false))
            else -> StandaloneVH(inflater.inflate(R.layout.item_comic_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LibItem.Root -> (holder as RootVH).bind(item.lib, item.count, onRootClick, onRootLongClick)
            is LibItem.Standalone -> (holder as StandaloneVH).bind(item.entry, onComicClick, onComicLongClick)
        }
    }

    class RootVH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.root_name)
        val count: TextView = view.findViewById(R.id.root_count)
        fun bind(lib: ComicsRootLibrary, count: Int, onClick: (ComicsRootLibrary) -> Unit, onLong: (ComicsRootLibrary) -> Unit) {
            name.text = lib.name
            this.count.text = itemView.context.resources.getQuantityString(R.plurals.comics_root_count, count, count)
            itemView.setOnClickListener { onClick(lib) }
            itemView.setOnLongClickListener { onLong(lib); true }
        }
    }

    class StandaloneVH(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.comic_cover)
        val title: TextView = view.findViewById(R.id.comic_title)
        val progressText: TextView = view.findViewById(R.id.comic_progress_text)
        val progressBar: ProgressBar = view.findViewById(R.id.comic_progress_bar)
        fun bind(entry: ComicEntry, onClick: (ComicEntry) -> Unit, onLong: (ComicEntry) -> Unit) {
            title.text = entry.title
            progressText.text = "${entry.currentPage + 1} / ${entry.totalPages}"
            progressBar.progress = entry.progressPercent
            cover.setImageResource(R.drawable.ic_hub_comics)
            itemView.setOnClickListener { onClick(entry) }
            itemView.setOnLongClickListener { onLong(entry); true }
        }
    }
}
