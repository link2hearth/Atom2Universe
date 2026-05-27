package com.Atom2Universe.app.books

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import androidx.core.content.edit

data class BookEntry(
    val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val totalItems: Int,
    val lastReadItem: Int,
    val format: String,
    val addedAt: Long,
    val lastOpenedAt: Long,
    val fileSize: Long = 0L
) {
    val progressPercent: Int
        get() = if (totalItems > 0) ((lastReadItem.toFloat() / totalItems) * 100).toInt().coerceIn(0, 100) else 0
}

data class AuthorFolder(
    val displayName: String,
    val pathPrefix: String,
    val bookCount: Int
)

class BookLibraryActivity : ThemedActivity() {

    companion object {
        private const val PREFS_NAME = "books_prefs"
        private const val KEY_LIBRARY = "book_library"
        private const val KEY_DISPLAY_MODE = "book_display_mode"
        private const val KEY_GRID_COLS = "book_grid_cols"
        private const val MODE_LIST = 0
        private const val MODE_GRID = 1
        private const val DEFAULT_GRID_COLS = 3

        fun loadLibrary(prefs: SharedPreferences): MutableList<BookEntry> {
            val json = prefs.getString(KEY_LIBRARY, "[]") ?: "[]"
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    BookEntry(
                        uri = o.getString("uri"),
                        title = o.optString("title", ""),
                        author = o.optString("author", ""),
                        coverPath = o.optString("coverPath", "").ifEmpty { null },
                        totalItems = o.optInt("totalItems", 0),
                        lastReadItem = o.optInt("lastReadItem", 0),
                        format = o.optString("format", "TXT"),
                        addedAt = o.optLong("addedAt", 0L),
                        lastOpenedAt = o.optLong("lastOpenedAt", 0L),
                        fileSize = o.optLong("fileSize", 0L)
                    )
                }.sortedByDescending { it.lastOpenedAt }.toMutableList()
            } catch (_: Exception) { mutableListOf() }
        }

        fun updateOrAddBook(prefs: SharedPreferences, entry: BookEntry) {
            val books = loadLibrary(prefs)
            val idx = books.indexOfFirst { it.uri == entry.uri }
            if (idx >= 0) {
                val existing = books[idx]
                books[idx] = entry.copy(
                    addedAt = existing.addedAt,
                    lastReadItem = maxOf(existing.lastReadItem, entry.lastReadItem),
                    totalItems = if (entry.totalItems > 0) entry.totalItems else existing.totalItems,
                    fileSize = if (entry.fileSize > 0) entry.fileSize else existing.fileSize
                )
            } else {
                books.add(0, entry)
            }
            if (books.size > 50) books.subList(50, books.size).clear()
            saveLibrary(prefs, books)
        }

        fun updateProgress(prefs: SharedPreferences, uri: String, lastReadItem: Int, totalItems: Int) {
            val books = loadLibrary(prefs)
            val idx = books.indexOfFirst { it.uri == uri }
            if (idx >= 0) {
                val b = books[idx]
                books[idx] = b.copy(
                    lastReadItem = lastReadItem,
                    totalItems = if (totalItems > 0) totalItems else b.totalItems
                )
                saveLibrary(prefs, books)
            }
        }

        fun removeBook(prefs: SharedPreferences, uri: String) {
            val books = loadLibrary(prefs).filter { it.uri != uri }.toMutableList()
            saveLibrary(prefs, books)
        }

        fun saveLibrary(prefs: SharedPreferences, books: List<BookEntry>) {
            val arr = JSONArray()
            books.forEach { b ->
                arr.put(JSONObject().apply {
                    put("uri", b.uri)
                    put("title", b.title)
                    put("author", b.author)
                    put("coverPath", b.coverPath ?: "")
                    put("totalItems", b.totalItems)
                    put("lastReadItem", b.lastReadItem)
                    put("format", b.format)
                    put("addedAt", b.addedAt)
                    put("lastOpenedAt", b.lastOpenedAt)
                    put("fileSize", b.fileSize)
                })
            }
            prefs.edit { putString(KEY_LIBRARY, arr.toString()) }
        }
    }

    // ── Récents ───────────────────────────────────────────────────────────────

    private lateinit var prefs: SharedPreferences
    private val books = mutableListOf<BookEntry>()
    private lateinit var adapter: BookAdapter
    private lateinit var emptyState: View
    private lateinit var recyclerView: RecyclerView

    // ── Bibliothèques ─────────────────────────────────────────────────────────

    private val shelfRoots = mutableListOf<BookShelfRoot>()
    private lateinit var shelfAdapter: ShelfRootsAdapter
    private lateinit var shelfRecycler: RecyclerView
    private lateinit var shelfEmptyText: TextView
    private lateinit var tabRecentsContainer: View
    private lateinit var tabShelfContainer: View
    private lateinit var shelfRootsContainer: View
    private lateinit var shelfAuthorsContainer: View
    private lateinit var shelfAuthorsRecycler: RecyclerView
    private lateinit var shelfAuthorsEmpty: TextView
    private lateinit var shelfTreeContainer: View
    private lateinit var shelfTreeRecycler: RecyclerView
    private lateinit var shelfTreeEmpty: TextView
    private val shelfScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var treeCoverScope: CoroutineScope? = null
    private var currentTab = 0
    private var currentRoot: BookShelfRoot? = null
    private var currentAuthorPath: String? = null
    private val currentEntries = mutableListOf<BookShelfEntry>()
    private var displayMode = MODE_LIST
    private var gridColumns = DEFAULT_GRID_COLS
    private var toggleMenuItem: MenuItem? = null
    private lateinit var fab: FloatingActionButton
    private lateinit var toolbar: MaterialToolbar

    // ── Launchers ─────────────────────────────────────────────────────────────

    private val openBookLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            launchReader(it)
        }
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) addLibraryRoot(uri)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_book_library)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        displayMode = prefs.getInt(KEY_DISPLAY_MODE, MODE_LIST)
        gridColumns = prefs.getInt(KEY_GRID_COLS, DEFAULT_GRID_COLS)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { handleBack() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })

        tabRecentsContainer = findViewById(R.id.tab_recents_container)
        tabShelfContainer = findViewById(R.id.tab_shelf_container)
        shelfRootsContainer = findViewById(R.id.shelf_roots_container)
        shelfAuthorsContainer = findViewById(R.id.shelf_authors_container)
        shelfAuthorsRecycler = findViewById(R.id.shelf_authors_recycler)
        shelfAuthorsEmpty = findViewById(R.id.shelf_authors_empty)
        shelfTreeContainer = findViewById(R.id.shelf_tree_container)
        shelfTreeRecycler = findViewById(R.id.shelf_tree_recycler)
        shelfTreeEmpty = findViewById(R.id.shelf_tree_empty)

        val tabs = findViewById<TabLayout>(R.id.book_tabs)
        tabs.addTab(tabs.newTab().setText(R.string.book_tab_recents))
        tabs.addTab(tabs.newTab().setText(R.string.book_tab_shelf))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = switchTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        emptyState = findViewById(R.id.empty_state)
        recyclerView = findViewById(R.id.books_recycler)
        adapter = BookAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        shelfEmptyText = findViewById(R.id.shelf_empty_text)
        shelfRecycler = findViewById(R.id.shelf_recycler)
        shelfAdapter = ShelfRootsAdapter(
            shelfRoots,
            onRootClick = { root -> openRoot(root) },
            onRootLongClick = { root -> showRootOptions(root) }
        )
        shelfRecycler.layoutManager = LinearLayoutManager(this)
        shelfRecycler.adapter = shelfAdapter

        fab = findViewById(R.id.fab_add)
        fab.setOnClickListener { onFabClick() }

        switchTab(0)
        refreshBooks()
        installFreeBooksIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshBooks()
        if (currentTab == 1) {
            val root = currentRoot
            val authorPath = currentAuthorPath
            when {
                root != null && authorPath != null -> {
                    shelfScope.launch {
                        val entries = withContext(Dispatchers.IO) {
                            BookDatabase.getInstance(this@BookLibraryActivity).bookShelfDao().getEntriesByRoot(root.id)
                        }
                        currentEntries.clear()
                        currentEntries.addAll(entries)
                        openAuthorPath(authorPath)
                    }
                }
                root != null -> openRoot(root)
                else -> loadRoots()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shelfScope.cancel()
        treeCoverScope?.cancel()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_book_library, menu)
        toggleMenuItem = menu.findItem(R.id.action_book_toggle_view)
        tintMenuIcons(menu)
        updateToggleIcon()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        toggleMenuItem?.isVisible = currentAuthorPath != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_book_toggle_view) {
            displayMode = if (displayMode == MODE_LIST) MODE_GRID else MODE_LIST
            prefs.edit { putInt(KEY_DISPLAY_MODE, displayMode) }
            updateToggleIcon()
            currentAuthorPath?.let { applyBooksDisplayMode(it) }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun tintMenuIcons(menu: Menu) {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(R.attr.a2uMidiAccent, tv, true)
        val tint = android.content.res.ColorStateList.valueOf(tv.data)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.let { icon ->
                icon.mutate()
                androidx.core.graphics.drawable.DrawableCompat.setTintList(icon, tint)
            }
        }
    }

    private fun updateToggleIcon() {
        toggleMenuItem?.setIcon(
            if (displayMode == MODE_LIST) R.drawable.ic_view_grid else R.drawable.ic_view_list
        )
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun handleBack() {
        when {
            currentAuthorPath != null -> {
                currentAuthorPath = null
                treeCoverScope?.cancel()
                treeCoverScope = null
                shelfTreeRecycler.adapter = null
                shelfTreeContainer.visibility = View.GONE
                shelfAuthorsContainer.visibility = View.VISIBLE
                supportActionBar?.title = currentRoot?.name
                invalidateOptionsMenu()
            }
            currentRoot != null -> showRootsView()
            else -> finish()
        }
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        tabRecentsContainer.visibility = if (tab == 0) View.VISIBLE else View.GONE
        tabShelfContainer.visibility = if (tab == 1) View.VISIBLE else View.GONE
        if (tab == 0) {
            fab.setImageResource(R.drawable.ic_bookmark_add)
            fab.visibility = View.VISIBLE
        } else {
            if (currentRoot != null) {
                fab.visibility = View.GONE
            } else {
                fab.setImageResource(R.drawable.ic_add)
                fab.visibility = View.VISIBLE
                loadRoots()
            }
        }
    }

    private fun onFabClick() {
        if (currentTab == 0) {
            openBookLauncher.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "*/*"))
        } else {
            pickFolder.launch(null)
        }
    }

    // ── Récents ───────────────────────────────────────────────────────────────

    private fun refreshBooks() {
        books.clear()
        books.addAll(loadLibrary(prefs))
        adapter.notifyDataSetChanged()
        val empty = books.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun launchReader(uri: Uri) {
        startActivity(Intent(this, BookReaderActivity::class.java).apply {
            putExtra(BookReaderActivity.EXTRA_BOOK_URI, uri.toString())
        })
    }

    // ── Bibliothèques : liste des racines ─────────────────────────────────────

    private fun loadRoots() {
        shelfScope.launch {
            val data = withContext(Dispatchers.IO) {
                BookDatabase.getInstance(this@BookLibraryActivity).bookShelfDao().getAllRoots()
            }
            shelfRoots.clear()
            shelfRoots.addAll(data)
            shelfAdapter.notifyDataSetChanged()
            shelfEmptyText.visibility = if (shelfRoots.isEmpty()) View.VISIBLE else View.GONE
            shelfRecycler.visibility = if (shelfRoots.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // ── Bibliothèques : navigation niveau auteurs ─────────────────────────────

    private fun openRoot(root: BookShelfRoot) {
        currentRoot = root
        currentAuthorPath = null
        supportActionBar?.title = root.name
        shelfRootsContainer.visibility = View.GONE
        shelfAuthorsContainer.visibility = View.VISIBLE
        shelfTreeContainer.visibility = View.GONE
        fab.visibility = View.GONE

        shelfScope.launch {
            val entries = withContext(Dispatchers.IO) {
                BookDatabase.getInstance(this@BookLibraryActivity).bookShelfDao().getEntriesByRoot(root.id)
            }
            currentEntries.clear()
            currentEntries.addAll(entries)
            showAuthorsForEntries(entries)
        }
    }

    private fun showAuthorsForEntries(entries: List<BookShelfEntry>) {
        if (entries.isEmpty()) {
            shelfAuthorsEmpty.visibility = View.VISIBLE
            shelfAuthorsRecycler.visibility = View.GONE
            return
        }

        val authorMap = mutableMapOf<String, Int>()
        var rootBookCount = 0
        for (entry in entries) {
            if (entry.relativePath.isEmpty()) { rootBookCount++; continue }
            val first = entry.relativePath.split("/").first()
            authorMap[first] = (authorMap[first] ?: 0) + 1
        }

        // Si aucun sous-dossier, aller directement à la vue livres (bibliothèque plate)
        if (authorMap.isEmpty()) {
            openAuthorPath("")
            return
        }

        val folders = mutableListOf<AuthorFolder>()
        if (rootBookCount > 0) {
            folders.add(AuthorFolder(
                displayName = getString(R.string.book_shelf_section_root),
                pathPrefix = "",
                bookCount = rootBookCount
            ))
        }
        authorMap.entries.sortedBy { it.key }.forEach { (name, count) ->
            folders.add(AuthorFolder(name, name, count))
        }

        shelfAuthorsEmpty.visibility = View.GONE
        shelfAuthorsRecycler.visibility = View.VISIBLE
        shelfAuthorsRecycler.layoutManager = LinearLayoutManager(this)
        shelfAuthorsRecycler.adapter = AuthorFoldersAdapter(folders) { folder ->
            openAuthorPath(folder.pathPrefix)
        }
    }

    private fun openAuthorPath(authorPath: String) {
        currentAuthorPath = authorPath
        shelfRootsContainer.visibility = View.GONE
        shelfAuthorsContainer.visibility = View.GONE
        shelfTreeContainer.visibility = View.VISIBLE
        fab.visibility = View.GONE
        invalidateOptionsMenu()

        supportActionBar?.title = when {
            authorPath.isEmpty() -> getString(R.string.book_shelf_section_root)
            else -> authorPath.split("/").last()
        }

        val entries = if (authorPath.isEmpty())
            currentEntries.filter { it.relativePath.isEmpty() }
        else
            currentEntries.filter { it.relativePath == authorPath || it.relativePath.startsWith("$authorPath/") }

        if (entries.isEmpty()) {
            shelfTreeEmpty.visibility = View.VISIBLE
            shelfTreeRecycler.visibility = View.GONE
            return
        }

        shelfTreeEmpty.visibility = View.GONE
        shelfTreeRecycler.visibility = View.VISIBLE

        applyBooksDisplayMode(authorPath, entries)
    }

    private fun applyBooksDisplayMode(authorPath: String, entries: List<BookShelfEntry>? = null) {
        val bookEntries = entries ?: run {
            if (authorPath.isEmpty()) currentEntries.filter { it.relativePath.isEmpty() }
            else currentEntries.filter { it.relativePath == authorPath || it.relativePath.startsWith("$authorPath/") }
        }

        treeCoverScope?.cancel()
        treeCoverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val coverScope = treeCoverScope!!

        if (displayMode == MODE_GRID) {
            shelfTreeRecycler.layoutManager = GridLayoutManager(this, gridColumns)
            shelfTreeRecycler.adapter = BookCoverGridAdapter(
                entries = bookEntries,
                scope = coverScope,
                onBookClick = { entry -> openShelfBook(entry) }
            )
        } else {
            shelfTreeRecycler.layoutManager = LinearLayoutManager(this)
            shelfTreeRecycler.adapter = AuthorBooksAdapter(
                context = this,
                scope = coverScope,
                entries = bookEntries,
                authorPath = authorPath,
                onBookClick = { entry -> openShelfBook(entry) }
            )
        }
    }

    private fun showRootsView() {
        currentRoot = null
        currentAuthorPath = null
        treeCoverScope?.cancel()
        treeCoverScope = null
        shelfTreeRecycler.adapter = null
        shelfAuthorsRecycler.adapter = null
        currentEntries.clear()

        supportActionBar?.title = getString(R.string.hub_books_title)
        shelfTreeContainer.visibility = View.GONE
        shelfAuthorsContainer.visibility = View.GONE
        shelfRootsContainer.visibility = View.VISIBLE
        fab.setImageResource(R.drawable.ic_add)
        fab.visibility = View.VISIBLE
    }

    private fun openShelfBook(entry: BookShelfEntry) {
        val uri = if (entry.sourcePath.startsWith("/")) Uri.fromFile(File(entry.sourcePath))
                  else Uri.parse(entry.sourcePath)
        startActivity(Intent(this, BookReaderActivity::class.java).apply {
            putExtra(BookReaderActivity.EXTRA_BOOK_URI, uri.toString())
        })
    }

    // ── Options sur une bibliothèque (long press) ─────────────────────────────

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
                shelfScope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = BookDatabase.getInstance(this@BookLibraryActivity).bookShelfDao()
                        dao.getEntriesByRoot(root.id).forEach { e -> e.coverPath?.let { File(it).delete() } }
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
        val (countText, nameText, dialog) = buildScanDialog(R.string.book_shelf_rescanning_title)
        dialog.show()
        shelfScope.launch {
            val (newRoot, entries) = withContext(Dispatchers.IO) {
                scanLibraryRoot(uri, existingRootId = root.id) { count, lastName ->
                    runOnUiThread {
                        countText.text = getString(R.string.book_shelf_scanning_found, count)
                        nameText.text = lastName
                    }
                }
            }
            dialog.dismiss()
            val dao = BookDatabase.getInstance(this@BookLibraryActivity).bookShelfDao()
            withContext(Dispatchers.IO) {
                dao.getEntriesByRoot(root.id).forEach { e -> e.coverPath?.let { File(it).delete() } }
                dao.deleteEntriesByRoot(root.id)
                dao.insertEntries(entries)
                dao.updateRoot(newRoot.copy(bookCount = entries.size, lastScannedAt = System.currentTimeMillis()))
            }
            loadRoots()
        }
    }

    // ── Ajout d'une bibliothèque (scan) ───────────────────────────────────────

    private fun addLibraryRoot(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val (countText, nameText, dialog) = buildScanDialog(R.string.book_shelf_scanning_title)
        dialog.show()
        shelfScope.launch {
            val (root, entries) = withContext(Dispatchers.IO) {
                scanLibraryRoot(uri) { count, lastName ->
                    runOnUiThread {
                        countText.text = getString(R.string.book_shelf_scanning_found, count)
                        nameText.text = lastName
                    }
                }
            }
            dialog.dismiss()
            if (entries.isEmpty()) {
                Toast.makeText(this@BookLibraryActivity, getString(R.string.book_shelf_scanning_found, 0), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val dao = BookDatabase.getInstance(this@BookLibraryActivity).bookShelfDao()
            withContext(Dispatchers.IO) {
                dao.insertRoot(root)
                dao.insertEntries(entries)
            }
            loadRoots()
        }
    }

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
            addView(ProgressBar(this@BookLibraryActivity).apply { isIndeterminate = true })
            addView(countText); addView(nameText)
        }
        val dialog = AlertDialog.Builder(this).setTitle(titleRes).setView(container).setCancelable(false).create()
        return Triple(countText, nameText, dialog)
    }

    // ── Scanner ───────────────────────────────────────────────────────────────

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

    private fun scanLibraryRoot(uri: Uri, existingRootId: String? = null, onFound: (Int, String) -> Unit): Pair<BookShelfRoot, List<BookShelfEntry>> {
        val rootFile = treeUriToFile(uri)
        if (rootFile != null && rootFile.isDirectory && rootFile.canRead() && rootFile.listFiles() != null) {
            val result = fastScan(rootFile, uri.toString(), existingRootId, onFound)
            if (result.second.isNotEmpty()) return result
        }
        val rootDoc = DocumentFile.fromTreeUri(this, uri)
            ?: return Pair(BookShelfRoot(name = "Unknown", rootUri = uri.toString()), emptyList())
        return safScan(rootDoc, uri.toString(), existingRootId, onFound)
    }

    private fun fastScan(rootFile: File, rootUriStr: String, existingRootId: String? = null, onFound: (Int, String) -> Unit): Pair<BookShelfRoot, List<BookShelfEntry>> {
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
                lower.endsWith(".fb2") -> "fb2"
                lower.endsWith(".mobi") -> "mobi"
                lower.endsWith(".azw") || lower.endsWith(".azw3") -> "mobi"
                lower.endsWith(".cbz") -> "cbz"
                lower.endsWith(".cbr") -> "cbr"
                else -> return@forEach
            }
            val rel = fileRelPath(file.parentFile!!, rootFile)
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

    private fun safScan(rootDoc: DocumentFile, rootUriStr: String, existingRootId: String? = null, onFound: (Int, String) -> Unit): Pair<BookShelfRoot, List<BookShelfEntry>> {
        val root = if (existingRootId != null)
            BookShelfRoot(id = existingRootId, name = rootDoc.name ?: "Library", rootUri = rootUriStr)
        else
            BookShelfRoot(name = rootDoc.name ?: "Library", rootUri = rootUriStr)
        val entries = mutableListOf<BookShelfEntry>()
        safScanDir(rootDoc, "", root.id, entries, 0, onFound)
        return Pair(root.copy(bookCount = entries.size), entries)
    }

    private fun safScanDir(dir: DocumentFile, parentRel: String, rootId: String, results: MutableList<BookShelfEntry>, depth: Int, onFound: (Int, String) -> Unit) {
        if (depth > 10) return
        val myRel = if (depth == 0) "" else if (parentRel.isEmpty()) (dir.name ?: "") else "$parentRel/${dir.name ?: ""}"
        for (f in dir.listFiles()) {
            if (f.isDirectory) { safScanDir(f, myRel, rootId, results, depth + 1, onFound); continue }
            val name = f.name ?: continue
            val lower = name.lowercase()
            val format = when {
                lower.endsWith(".epub") -> "epub"
                lower.endsWith(".pdf") -> "pdf"
                lower.endsWith(".txt") -> "txt"
                lower.endsWith(".fb2") -> "fb2"
                lower.endsWith(".mobi") -> "mobi"
                lower.endsWith(".azw") || lower.endsWith(".azw3") -> "mobi"
                lower.endsWith(".cbz") -> "cbz"
                lower.endsWith(".cbr") -> "cbr"
                else -> continue
            }
            val title = name.substringBeforeLast(".")
            results.add(BookShelfEntry(title = title, sourcePath = f.uri.toString(), format = format, fileSize = f.length(), rootId = rootId, relativePath = myRel))
            onFound(results.size, title)
        }
    }

    private fun fileRelPath(dir: File, root: File): String = try {
        if (dir == root) "" else dir.relativeTo(root).path.replace(File.separator, "/")
    } catch (_: Exception) { "" }

    private fun extractEpubTitleAuthor(file: File): Pair<String, String> {
        var title = ""; var author = ""
        try {
            ZipFile(file).use { zip ->
                val container = zip.getEntry("META-INF/container.xml") ?: return Pair(title, author)
                val containerText = zip.getInputStream(container).readBytes().decodeToString()
                val opfPath = Regex("""full-path="([^"]+\.opf)"""").find(containerText)?.groupValues?.get(1) ?: return Pair(title, author)
                val opfEntry = zip.getEntry(opfPath) ?: return Pair(title, author)
                val opfText = zip.getInputStream(opfEntry).readBytes().decodeToString()
                Regex("""<dc:title[^>]*>([^<]+)</dc:title>""").find(opfText)?.let { title = it.groupValues[1].trim() }
                Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""").find(opfText)?.let { author = it.groupValues[1].trim() }
            }
        } catch (_: Exception) {}
        return Pair(title, author)
    }

    // ── Bibliothèque "Free" intégrée ──────────────────────────────────────────

    private fun installFreeBooksIfNeeded() {
        if (prefs.getBoolean("free_books_installed", false)) return
        shelfScope.launch {
            withContext(Dispatchers.IO) {
                val destDir = File(filesDir, "free_books").also { it.mkdirs() }
                data class AssetBook(val asset: String, val title: String, val author: String)
                val books = listOf(
                    AssetBook("books/Alice_in_Wonderland_EN.epub",             "Alice in Wonderland",           "Lewis Carroll"),
                    AssetBook("books/Alice_au_pays_des_merveilles_FR.epub",    "Alice au pays des merveilles",  "Lewis Carroll"),
                    AssetBook("books/The_Time_Machine_EN.epub",                "The Time Machine",              "H.G. Wells"),
                    AssetBook("books/La_Machine_a_explorer_le_temps_FR.epub",  "La Machine à explorer le temps","H.G. Wells")
                )
                val dao = BookDatabase.getInstance(this@BookLibraryActivity).bookShelfDao()
                val rootId = "free_library_root"
                val root = BookShelfRoot(
                    id = rootId,
                    name = "Free",
                    rootUri = destDir.absolutePath,
                    bookCount = books.size
                )
                dao.insertRoot(root)
                val entries = books.mapNotNull { b ->
                    val destFile = File(destDir, b.asset.substringAfterLast("/"))
                    try {
                        assets.open(b.asset).use { ins ->
                            FileOutputStream(destFile).use { out -> ins.copyTo(out) }
                        }
                    } catch (_: Exception) { return@mapNotNull null }
                    BookShelfEntry(
                        title = b.title,
                        author = b.author,
                        sourcePath = destFile.absolutePath,
                        format = "epub",
                        fileSize = destFile.length(),
                        rootId = rootId
                    )
                }
                dao.insertEntries(entries)
                prefs.edit { putBoolean("free_books_installed", true) }
            }
        }
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun formatFileSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024f * 1024f))
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return DateUtils.getRelativeTimeSpanString(
            timestamp, System.currentTimeMillis(),
            DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    // ── Adapter : livres récents ──────────────────────────────────────────────

    inner class BookAdapter : RecyclerView.Adapter<BookAdapter.BookVH>() {

        inner class BookVH(val root: View) : RecyclerView.ViewHolder(root) {
            val cover: ImageView = root.findViewById(R.id.book_cover)
            val title: TextView = root.findViewById(R.id.book_title)
            val author: TextView = root.findViewById(R.id.book_author)
            val size: TextView = root.findViewById(R.id.book_size)
            val progress: ProgressBar = root.findViewById(R.id.book_progress)
            val progressText: TextView = root.findViewById(R.id.book_progress_text)
            val date: TextView = root.findViewById(R.id.book_date)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookVH =
            BookVH(layoutInflater.inflate(R.layout.item_book_card, parent, false))

        override fun onBindViewHolder(holder: BookVH, position: Int) {
            val book = books[position]
            holder.title.text = book.title
            holder.author.text = book.author.ifEmpty { getString(R.string.book_library_author_unknown) }
            holder.size.text = formatFileSize(book.fileSize)
            holder.progress.progress = book.progressPercent
            holder.progressText.text = if (book.progressPercent > 0) "${book.progressPercent}%" else ""
            holder.date.text = formatDate(book.lastOpenedAt)

            val coverFile = book.coverPath?.let { File(it) }
            if (coverFile != null && coverFile.exists()) {
                val bmp = BitmapFactory.decodeFile(coverFile.absolutePath)
                if (bmp != null) {
                    holder.cover.scaleType = ImageView.ScaleType.CENTER_CROP
                    holder.cover.setPadding(0, 0, 0, 0)
                    holder.cover.setImageBitmap(bmp)
                    holder.cover.clearColorFilter()
                } else { setPlaceholder(holder.cover) }
            } else { setPlaceholder(holder.cover) }

            holder.root.setOnClickListener { launchReader(Uri.parse(book.uri)) }
            holder.root.setOnLongClickListener {
                AlertDialog.Builder(this@BookLibraryActivity)
                    .setTitle(book.title)
                    .setMessage(R.string.book_library_delete_confirm)
                    .setPositiveButton(R.string.book_library_delete) { _, _ ->
                        book.coverPath?.let { File(it).delete() }
                        removeBook(prefs, book.uri)
                        val pos = books.indexOf(book)
                        if (pos >= 0) { books.removeAt(pos); notifyItemRemoved(pos) }
                        if (books.isEmpty()) { emptyState.visibility = View.VISIBLE; recyclerView.visibility = View.GONE }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }

        private fun setPlaceholder(iv: ImageView) {
            iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
            iv.setPadding(dp(20), dp(20), dp(20), dp(20))
            iv.setImageResource(R.drawable.ic_hub_books)
            iv.setColorFilter(Color.argb(100, 255, 255, 255))
        }

        override fun getItemCount() = books.size
    }

    // ── Adapter : liste des bibliothèques ─────────────────────────────────────

    inner class ShelfRootsAdapter(
        private val roots: List<BookShelfRoot>,
        private val onRootClick: (BookShelfRoot) -> Unit,
        private val onRootLongClick: (BookShelfRoot) -> Unit
    ) : RecyclerView.Adapter<ShelfRootsAdapter.VH>() {

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
            val count = getString(R.string.book_shelf_books_count, root.bookCount)
            val scanned = DateUtils.getRelativeTimeSpanString(root.lastScannedAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS)
            holder.count.text = "$count  ·  $scanned"
            holder.itemView.setOnClickListener { onRootClick(root) }
            holder.itemView.setOnLongClickListener { onRootLongClick(root); true }
        }
    }

    // ── Adapter : liste des dossiers auteurs ──────────────────────────────────

    inner class AuthorFoldersAdapter(
        private val folders: List<AuthorFolder>,
        private val onFolderClick: (AuthorFolder) -> Unit
    ) : RecyclerView.Adapter<AuthorFoldersAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.root_name)
            val count: TextView = view.findViewById(R.id.root_count)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_book_shelf_root, parent, false))

        override fun getItemCount() = folders.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val folder = folders[position]
            holder.name.text = folder.displayName
            holder.count.text = getString(R.string.book_shelf_books_count, folder.bookCount)
            holder.itemView.setOnClickListener { onFolderClick(folder) }
        }
    }
}

// ── Author books rows ─────────────────────────────────────────────────────────

private sealed class AuthorBooksRow {
    data class SectionHeader(val name: String, val bookCount: Int) : AuthorBooksRow()
    data class BookItem(val entry: BookShelfEntry) : AuthorBooksRow()
}

private fun buildAuthorBooksRows(entries: List<BookShelfEntry>, authorPath: String): List<AuthorBooksRow> {
    val rows = mutableListOf<AuthorBooksRow>()

    val directBooks = entries.filter { it.relativePath == authorPath }.sortedBy { it.title }
    directBooks.forEach { rows.add(AuthorBooksRow.BookItem(it)) }

    val subEntries = entries.filter { it.relativePath != authorPath }
    if (subEntries.isNotEmpty()) {
        val subGroups = subEntries.groupBy { entry ->
            val remainder = if (authorPath.isEmpty()) entry.relativePath
                            else entry.relativePath.removePrefix("$authorPath/")
            remainder.split("/").first()
        }
        subGroups.keys.sorted().forEach { sectionName ->
            val sectionBooks = subGroups[sectionName]!!.sortedBy { it.title }
            rows.add(AuthorBooksRow.SectionHeader(sectionName, sectionBooks.size))
            sectionBooks.forEach { rows.add(AuthorBooksRow.BookItem(it)) }
        }
    }

    return rows
}

// ── Author books adapter ──────────────────────────────────────────────────────

private class AuthorBooksAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    entries: List<BookShelfEntry>,
    authorPath: String,
    private val onBookClick: (BookShelfEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = buildAuthorBooksRows(entries, authorPath)

    companion object { private const val TYPE_HEADER = 0; private const val TYPE_BOOK = 1 }

    override fun getItemViewType(p: Int) = if (rows[p] is AuthorBooksRow.SectionHeader) TYPE_HEADER else TYPE_BOOK
    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            SectionHeaderVH(inf.inflate(R.layout.item_book_folder_header, parent, false))
        else
            ShelfBookVH(inf.inflate(R.layout.item_book_card, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is AuthorBooksRow.SectionHeader -> (holder as SectionHeaderVH).bind(row)
            is AuthorBooksRow.BookItem -> {
                (holder as ShelfBookVH).bind(row.entry, 0, context.resources.displayMetrics.density, scope, context)
                holder.itemView.setOnClickListener { onBookClick(row.entry) }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? ShelfBookVH)?.cancelLoad()
    }
}

// ── Section header view holder ────────────────────────────────────────────────

private class SectionHeaderVH(view: View) : RecyclerView.ViewHolder(view) {
    val indent: View = view.findViewById(R.id.folder_indent)
    val name: TextView = view.findViewById(R.id.folder_name)
    val count: TextView = view.findViewById(R.id.folder_count)
    val chevron: ImageView = view.findViewById(R.id.folder_chevron)

    fun bind(row: AuthorBooksRow.SectionHeader) {
        indent.layoutParams = indent.layoutParams.also { it.width = 0 }
        name.text = row.name
        count.text = "${row.bookCount}"
        chevron.visibility = View.GONE
        itemView.isClickable = false
    }
}

// ── Book view holder (shared) ─────────────────────────────────────────────────

private class ShelfBookVH(view: View) : RecyclerView.ViewHolder(view) {
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
        size.text = formatShelfSize(entry.fileSize)
        progress.progress = entry.progressPercent
        progressText.text = if (entry.progressPercent > 0) "${entry.progressPercent}%" else ""
        date.text = if (entry.lastOpenedAt > 0)
            DateUtils.getRelativeTimeSpanString(entry.lastOpenedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        else ""

        setPlaceholder(cover)
        loadJob?.cancel()
        loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadShelfCover(context, entry) }
            if (bmp != null) {
                cover.scaleType = ImageView.ScaleType.CENTER_CROP
                cover.setPadding(0, 0, 0, 0)
                cover.clearColorFilter()
                cover.setImageBitmap(bmp)
            }
        }
    }

    fun cancelLoad() { loadJob?.cancel(); loadJob = null }

    private fun setPlaceholder(iv: ImageView) {
        val dp = iv.resources.displayMetrics.density
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iv.setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        iv.setImageResource(R.drawable.ic_hub_books)
        iv.setColorFilter(Color.argb(100, 255, 255, 255))
    }
}

private fun formatShelfSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}

// ── Cover grid adapter ────────────────────────────────────────────────────────

private class BookCoverGridAdapter(
    private val entries: List<BookShelfEntry>,
    private val scope: CoroutineScope,
    private val onBookClick: (BookShelfEntry) -> Unit
) : RecyclerView.Adapter<BookCoverGridAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.book_grid_cover)
        val title: TextView = view.findViewById(R.id.book_grid_title)
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_book_cover_grid, parent, false))

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        val dp = holder.cover.resources.displayMetrics.density

        holder.cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
        holder.cover.setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        holder.cover.setImageResource(R.drawable.ic_hub_books)
        holder.cover.setColorFilter(Color.argb(100, 255, 255, 255))
        holder.title.text = entry.title
        holder.title.visibility = View.VISIBLE

        holder.loadJob?.cancel()
        holder.loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadShelfCover(holder.cover.context, entry) }
            if (bmp != null) {
                holder.cover.clearColorFilter()
                holder.cover.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.cover.setPadding(0, 0, 0, 0)
                holder.cover.setImageBitmap(bmp)
                holder.title.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener { onBookClick(entry) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
        holder.loadJob = null
    }
}

// ── Cover loader ──────────────────────────────────────────────────────────────

private fun loadShelfCover(context: Context, entry: BookShelfEntry): Bitmap? {
    val cacheFile = File(context.filesDir, "book_covers/${entry.id}.jpg")
    if (cacheFile.exists()) {
        return BitmapFactory.decodeFile(cacheFile.path, BitmapFactory.Options().apply { inSampleSize = 2 })
    }
    entry.coverPath?.let { path ->
        val f = File(path)
        if (f.exists()) return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })
    }
    val uri = if (entry.sourcePath.startsWith("/")) Uri.fromFile(File(entry.sourcePath)) else Uri.parse(entry.sourcePath)
    val bmp = when (entry.format) {
        "epub" -> loadShelfEpubCover(entry)
        "pdf" -> loadShelfPdfCover(context, uri)
        else -> null
    } ?: return null
    cacheFile.parentFile?.mkdirs()
    try { FileOutputStream(cacheFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } } catch (_: Exception) {}
    return bmp
}

private fun loadShelfEpubCover(entry: BookShelfEntry): Bitmap? {
    if (!entry.sourcePath.startsWith("/")) return null
    return try {
        ZipFile(File(entry.sourcePath)).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return null
            val containerText = zip.getInputStream(container).readBytes().decodeToString()
            val opfPath = Regex("""full-path="([^"]+\.opf)"""").find(containerText)?.groupValues?.get(1) ?: return null
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

private fun loadShelfPdfCover(context: Context, uri: Uri): Bitmap? = try {
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
