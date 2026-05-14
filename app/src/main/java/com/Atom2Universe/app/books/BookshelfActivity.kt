package com.Atom2Universe.app.books

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
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
import java.io.File
import java.util.zip.ZipFile

class BookshelfActivity : ThemedActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val roots = mutableListOf<BookShelfRoot>()
    private lateinit var adapter: RootsAdapter
    private lateinit var emptyView: TextView

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) addLibraryRoot(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookshelf)
        enableImmersiveMode()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.bookshelf_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        emptyView = findViewById(R.id.bookshelf_empty)

        adapter = RootsAdapter(
            roots,
            onRootClick = { root -> openRoot(root) },
            onRootLongClick = { root -> showRootOptions(root) }
        )

        val recycler = findViewById<RecyclerView>(R.id.bookshelf_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.bookshelf_fab).setOnClickListener {
            pickFolder.launch(null)
        }

        loadRoots()
    }

    override fun onResume() {
        super.onResume()
        loadRoots()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun loadRoots() {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                BookDatabase.getInstance(this@BookshelfActivity).bookShelfDao().getAllRoots()
            }
            roots.clear()
            roots.addAll(data)
            adapter.notifyDataSetChanged()
            emptyView.visibility = if (roots.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openRoot(root: BookShelfRoot) {
        startActivity(Intent(this, BookshelfTreeActivity::class.java).apply {
            putExtra(BookshelfTreeActivity.EXTRA_ROOT_ID, root.id)
            putExtra(BookshelfTreeActivity.EXTRA_ROOT_NAME, root.name)
        })
    }

    // ── Root options ───────────���─────────────────���────────────────────────────

    private fun showRootOptions(root: BookShelfRoot) {
        val options = arrayOf(
            getString(R.string.book_shelf_options_rescan),
            getString(R.string.book_shelf_options_delete)
        )
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

    private fun confirmDeleteRoot(root: BookShelfRoot) {
        AlertDialog.Builder(this)
            .setTitle(root.name)
            .setMessage(R.string.book_shelf_delete_confirm)
            .setPositiveButton(R.string.book_shelf_options_delete) { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = BookDatabase.getInstance(this@BookshelfActivity).bookShelfDao()
                        // Delete cached covers
                        val entries = dao.getEntriesByRoot(root.id)
                        entries.forEach { entry -> entry.coverPath?.let { File(it).delete() } }
                        dao.deleteEntriesByRoot(root.id)
                        dao.deleteRoot(root.id)
                    }
                    loadRoots()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun rescanRoot(root: BookShelfRoot) {
        val uri = Uri.parse(root.rootUri)
        val (countText, nameText, scanDialog) = buildScanDialog(R.string.book_shelf_rescanning_title)
        scanDialog.show()
        scope.launch {
            val (newRoot, entries) = withContext(Dispatchers.IO) {
                scanLibraryRoot(uri, existingRootId = root.id) { count, lastName ->
                    runOnUiThread {
                        countText.text = getString(R.string.book_shelf_scanning_found, count)
                        nameText.text = lastName
                    }
                }
            }
            scanDialog.dismiss()
            val dao = BookDatabase.getInstance(this@BookshelfActivity).bookShelfDao()
            withContext(Dispatchers.IO) {
                val oldEntries = dao.getEntriesByRoot(root.id)
                oldEntries.forEach { e -> e.coverPath?.let { File(it).delete() } }
                dao.deleteEntriesByRoot(root.id)
                dao.insertEntries(entries)
                dao.updateRoot(newRoot.copy(bookCount = entries.size, lastScannedAt = System.currentTimeMillis()))
            }
            loadRoots()
        }
    }

    // ── Add library root ─────────────���───────────────────────────��────────────

    private fun addLibraryRoot(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}

        val (countText, nameText, scanDialog) = buildScanDialog(R.string.book_shelf_scanning_title)
        scanDialog.show()

        scope.launch {
            val (root, entries) = withContext(Dispatchers.IO) {
                scanLibraryRoot(uri) { count, lastName ->
                    runOnUiThread {
                        countText.text = getString(R.string.book_shelf_scanning_found, count)
                        nameText.text = lastName
                    }
                }
            }
            scanDialog.dismiss()
            if (entries.isEmpty()) {
                Toast.makeText(this@BookshelfActivity, getString(R.string.book_shelf_scanning_found, 0), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val dao = BookDatabase.getInstance(this@BookshelfActivity).bookShelfDao()
            withContext(Dispatchers.IO) {
                dao.insertRoot(root)
                dao.insertEntries(entries)
            }
            loadRoots()
        }
    }

    // ── Scan dialog ────────────────────────���────────────────────────────��─────

    private fun buildScanDialog(titleRes: Int): Triple<TextView, TextView, AlertDialog> {
        val countText = TextView(this).apply {
            text = getString(R.string.book_shelf_scanning_found, 0)
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
            addView(android.widget.ProgressBar(this@BookshelfActivity).apply { isIndeterminate = true })
            addView(countText)
            addView(nameText)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(container)
            .setCancelable(false)
            .create()
        return Triple(countText, nameText, dialog)
    }

    // ── Scanner ─────────────────────���─────────────────────────────────────────

    private fun treeUriToFile(uri: Uri): File? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
            val parts = docId.split(":")
            if (parts.size < 2) return null
            val base = when (parts[0].lowercase()) {
                "primary" -> "/storage/emulated/0"
                else -> "/storage/${parts[0]}"
            }
            File("$base/${parts[1]}")
        } catch (_: Exception) { null }
    }

    private fun scanLibraryRoot(
        uri: Uri,
        existingRootId: String? = null,
        onFound: (Int, String) -> Unit
    ): Pair<BookShelfRoot, List<BookShelfEntry>> {
        val rootFile = treeUriToFile(uri)
        if (rootFile != null && rootFile.isDirectory) {
            return fastScan(rootFile, uri.toString(), existingRootId, onFound)
        }
        val rootDoc = DocumentFile.fromTreeUri(this, uri)
            ?: return Pair(BookShelfRoot(name = "Unknown", rootUri = uri.toString()), emptyList())
        return safScan(rootDoc, uri.toString(), existingRootId, onFound)
    }

    private fun fastScan(
        rootFile: File,
        rootUriStr: String,
        existingRootId: String? = null,
        onFound: (Int, String) -> Unit
    ): Pair<BookShelfRoot, List<BookShelfEntry>> {
        val root = if (existingRootId != null)
            BookShelfRoot(id = existingRootId, name = rootFile.name, rootUri = rootUriStr)
        else
            BookShelfRoot(name = rootFile.name, rootUri = rootUriStr)
        val entries = mutableListOf<BookShelfEntry>()

        rootFile.walkTopDown().maxDepth(12).forEach { file ->
            if (!file.isFile) return@forEach
            val lower = file.name.lowercase()
            val format = when {
                lower.endsWith(".epub") -> "epub"
                lower.endsWith(".pdf") -> "pdf"
                lower.endsWith(".txt") -> "txt"
                else -> return@forEach
            }
            val rel = parentRelPath(file.parentFile!!, rootFile)
            val (title, author) = if (format == "epub") extractEpubTitleAuthor(file) else Pair(file.nameWithoutExtension, "")
            entries.add(BookShelfEntry(
                title = title.ifEmpty { file.nameWithoutExtension },
                author = author,
                sourcePath = file.absolutePath,
                format = format,
                fileSize = file.length(),
                rootId = root.id,
                relativePath = rel
            ))
            onFound(entries.size, title.ifEmpty { file.nameWithoutExtension })
        }
        return Pair(root.copy(bookCount = entries.size), entries)
    }

    private fun safScan(
        rootDoc: DocumentFile,
        rootUriStr: String,
        existingRootId: String? = null,
        onFound: (Int, String) -> Unit
    ): Pair<BookShelfRoot, List<BookShelfEntry>> {
        val root = if (existingRootId != null)
            BookShelfRoot(id = existingRootId, name = rootDoc.name ?: "Library", rootUri = rootUriStr)
        else
            BookShelfRoot(name = rootDoc.name ?: "Library", rootUri = rootUriStr)
        val entries = mutableListOf<BookShelfEntry>()
        safScanDir(rootDoc, "", root.id, entries, 0, onFound)
        return Pair(root.copy(bookCount = entries.size), entries)
    }

    private fun safScanDir(
        dir: DocumentFile,
        parentRel: String,
        rootId: String,
        results: MutableList<BookShelfEntry>,
        depth: Int,
        onFound: (Int, String) -> Unit
    ) {
        if (depth > 10) return
        val children = dir.listFiles()
        val myRel = if (depth == 0) "" else if (parentRel.isEmpty()) (dir.name ?: "") else "$parentRel/${dir.name ?: ""}"
        for (f in children) {
            if (f.isDirectory) {
                safScanDir(f, myRel, rootId, results, depth + 1, onFound)
                continue
            }
            val name = f.name ?: continue
            val lower = name.lowercase()
            val format = when {
                lower.endsWith(".epub") -> "epub"
                lower.endsWith(".pdf") -> "pdf"
                lower.endsWith(".txt") -> "txt"
                else -> continue
            }
            val title = name.substringBeforeLast(".")
            results.add(BookShelfEntry(
                title = title,
                sourcePath = f.uri.toString(),
                format = format,
                fileSize = f.length(),
                rootId = rootId,
                relativePath = myRel
            ))
            onFound(results.size, title)
        }
    }

    private fun parentRelPath(dir: File, root: File): String = try {
        val parent = dir.parentFile ?: return ""
        if (parent == root) "" else parent.relativeTo(root).path.replace(File.separator, "/")
    } catch (_: Exception) { "" }

    // ── EPUB metadata (title + author only, no cover during scan for speed) ──

    private fun extractEpubTitleAuthor(file: File): Pair<String, String> {
        var title = ""
        var author = ""
        try {
            ZipFile(file).use { zip ->
                val container = zip.getEntry("META-INF/container.xml") ?: return Pair(title, author)
                val containerText = zip.getInputStream(container).readBytes().decodeToString()
                val opfPath = Regex("""full-path="([^"]+\.opf)"""").find(containerText)?.groupValues?.get(1)
                    ?: return Pair(title, author)
                val opfEntry = zip.getEntry(opfPath) ?: return Pair(title, author)
                val opfText = zip.getInputStream(opfEntry).readBytes().decodeToString()
                Regex("""<dc:title[^>]*>([^<]+)</dc:title>""").find(opfText)?.let { title = it.groupValues[1].trim() }
                Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""").find(opfText)?.let { author = it.groupValues[1].trim() }
            }
        } catch (_: Exception) {}
        return Pair(title, author)
    }
}

// ── Roots adapter ─────────────────────��───────────────────────────────────────

private class RootsAdapter(
    private val roots: List<BookShelfRoot>,
    private val onRootClick: (BookShelfRoot) -> Unit,
    private val onRootLongClick: (BookShelfRoot) -> Unit
) : RecyclerView.Adapter<RootsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.root_name)
        val count: TextView = view.findViewById(R.id.root_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_book_shelf_root, parent, false))

    override fun getItemCount() = roots.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val root = roots[position]
        holder.name.text = root.name
        val ctx = holder.itemView.context
        val count = ctx.getString(R.string.book_shelf_books_count, root.bookCount)
        val scanned = DateUtils.getRelativeTimeSpanString(
            root.lastScannedAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS
        )
        holder.count.text = "$count  ·  $scanned"
        holder.itemView.setOnClickListener { onRootClick(root) }
        holder.itemView.setOnLongClickListener { onRootLongClick(root); true }
    }
}
