package com.Atom2Universe.app.gallery

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.SystemBarsManager
import com.Atom2Universe.app.util.SystemBarsPreferenceOwner
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.followImmersiveMode
import com.Atom2Universe.app.util.updateSystemBarsVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryActivity : ThemedActivity(), SystemBarsPreferenceOwner {
    private val library by lazy { GalleryLibrary.get(this) }
    private val prefs by lazy { getSharedPreferences("gallery_view", MODE_PRIVATE) }
    private val batch by lazy { ViewModelProvider(this)[GalleryBatch::class.java] }
    private val path = mutableListOf<Folder>()
    private var shelf = Shelf.HOME
    private var viewer: GalleryViewer? = null
    private lateinit var root: FrameLayout
    private lateinit var browser: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var grid: GalleryGrid
    private lateinit var tiles: GalleryTiles
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private var loading: Job? = null
    private var operation: Job? = null
    private var pendingOpen: String? = null
    private var pendingImport: Uri? = null
    private var gridPosition = 0
    private var gridOffset = 0
    private var resumeRefresh = false
    private var showControls = true
    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val id = data?.getStringExtra(PhotoEditorActivity.RESULT_ID)
        if (result.resultCode == RESULT_OK && id != null) {
            closeViewer()
            if (shelf != Shelf.FOLDER || path.lastOrNull()?.uri?.toString() != data.getStringExtra(PhotoEditorActivity.PARENT)) {
                shelf = Shelf.RECENT
                path.clear()
            }
            pendingOpen = id
            showControls = true
            resumeRefresh = false
            load()
        }
    }
    private fun editPhoto(photo: Photo) {
        editor.launch(Intent(this, PhotoEditorActivity::class.java).setData(photo.uri)
            .putExtra(PhotoEditorActivity.NAME, photo.name).putExtra(PhotoEditorActivity.PARENT, photo.parent?.toString()))
    }
    override val showStatusBar get() = viewer == null && SystemBarsManager.shouldShowSystemBars(this)
    override val showNavigationBar get() = showStatusBar

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val tree = data?.data
        if (result.resultCode == RESULT_OK && tree != null) change({
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(tree, flags)
            library.addFolder(library.rootFolder(tree))
        }) { navigate(Shelf.HOME) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            shelf = Shelf.valueOf(state.getString("shelf") ?: Shelf.HOME.name)
            val names = state.getStringArrayList("names").orEmpty()
            state.getStringArrayList("uris").orEmpty().forEachIndexed { i, uri -> path += Folder(Uri.parse(uri), names[i]) }
            pendingOpen = state.getString("photo")
            pendingImport = state.getString("import")?.let(Uri::parse)
            gridPosition = state.getInt("position")
            gridOffset = state.getInt("offset")
            showControls = state.getBoolean("controls", true)
        }
        buildBrowser()
        setContentView(root)
        batch.result.observe(this) { result ->
            if (result == null) return@observe
            batch.result.value = null
            rememberScroll()
            load()
            if (result.failures.isNotEmpty()) {
                val names = result.failures.take(3).joinToString("\n") { it.name }
                AlertDialog.Builder(this).setTitle(R.string.gallery_operation_failed)
                    .setMessage(getString(R.string.gallery_batch_failed, result.failures.size, result.total, names))
                    .setPositiveButton(R.string.ok, null).create().apply { followImmersiveMode(); show() }
            }
        }
        enableImmersiveMode()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        if (pendingImport != null || (savedInstanceState == null && intent.action == Intent.ACTION_VIEW)) {
            acceptImage(intent)
        } else load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) acceptImage(intent)
    }

    override fun onStop() {
        super.onStop()
        resumeRefresh = true
    }
    override fun onResume() {
        super.onResume()
        if (resumeRefresh && viewer == null && operation?.isActive != true && loading?.isActive != true) {
            rememberScroll()
            load()
        }
        resumeRefresh = false
    }

    private fun buildBrowser() {
        root = FrameLayout(this)
        browser = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppearanceStyle.screen(context)
        }
        root.addView(browser, FrameLayout.LayoutParams(-1, -1))
        toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = AppearanceStyle.drawable(context, R.attr.a2uPanelBackground)
        }
        browser.addView(toolbar, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(8), dp(8), dp(8), dp(4))
        })
        val body = FrameLayout(this)
        browser.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        grid = GalleryGrid(this, photoColumns()) { prefs.edit().putInt("columns", it).apply() }
        grid.setPadding(dp(6), dp(4), dp(6), dp(8))
        tiles = GalleryTiles(lifecycleScope, ::select, ::toggleFavorite, ::selectPhoto, ::editPhoto, { items ->
            ordering().saveManual(items)
        }) { folder ->
            if (shelf == Shelf.HOME) {
                confirm(folder.name, R.string.gallery_unlink_message, R.string.gallery_unlink) {
                    change({ library.unlink(folder) }) { load() }
                }
                true
            } else false
        }
        grid.adapter = tiles
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        empty = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = if (tablet()) 19f else 16f
            setTextColor(galleryColor(R.attr.a2uSecondaryTextColor))
        }
        body.addView(empty, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        progress = ProgressBar(this)
        body.addView(progress, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
    }

    private fun photoColumns() = prefs.getInt("columns", if (tablet()) 5 else 3).coerceIn(1, 8)
    private fun title() = when (shelf) {
        Shelf.HOME -> getString(R.string.gallery_title)
        Shelf.PICK -> path.lastOrNull()?.name ?: getString(R.string.gallery_add)
        Shelf.FOLDER -> path.last().name
        Shelf.RECENT -> getString(R.string.gallery_recent)
        Shelf.FAVORITES -> getString(R.string.gallery_favorites)
        Shelf.TRASH -> getString(R.string.gallery_bin)
    }
    private fun selectionAction(icon: Int, label: Int, click: () -> Unit): View {
        if (resources.configuration.screenWidthDp < 600) return toolbar.action(icon, label, click)
        return TextView(this).apply {
            val accent = galleryColor(R.attr.a2uMidiAccent)
            val image = requireNotNull(getDrawable(icon)).mutate().apply {
                setTint(accent)
                setBounds(0, 0, dp(20), dp(20))
            }
            setCompoundDrawablesRelative(image, null, null, null)
            compoundDrawablePadding = dp(6)
            setTextColor(accent)
            setText(label)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            background = AppearanceStyle.drawable(context, androidx.appcompat.R.attr.selectableItemBackground)
            isFocusable = true
            setOnClickListener { click() }
            toolbar.addView(this, -2, dp(48))
        }
    }
    private fun header() {
        toolbar.removeAllViews()
        if (batch.selected.isNotEmpty()) {
            toolbar.action(R.drawable.ic_close, R.string.cancel) { clearSelection() }
            toolbar.heading().text = getString(R.string.gallery_selected, batch.selected.size)
            if (shelf == Shelf.TRASH) {
                selectionAction(R.drawable.ic_nt_restore, R.string.gallery_restore) { startBatch(BatchAction.RESTORE) }
                selectionAction(R.drawable.ic_delete, R.string.gallery_discard) { confirmBatchDelete() }
            } else {
                selectionAction(R.drawable.ic_px_move, R.string.gallery_move) { destination(BatchAction.MOVE) }
                    .apply {
                        isEnabled = selectedPhotos().all(library::canRelocate)
                        alpha = if (isEnabled) 1f else 0.4f
                    }
                selectionAction(R.drawable.ic_px_copy, R.string.gallery_copy) { destination(BatchAction.COPY) }
                selectionAction(R.drawable.ic_delete, R.string.gallery_discard) { confirmBatchDelete() }
            }
            for (i in 0 until toolbar.childCount) if (batch.busy) toolbar.getChildAt(i).isEnabled = false
            return
        }
        toolbar.action(R.drawable.ic_arrow_back_24, R.string.back, ::back)
        toolbar.heading().text = title()
        if (shelf == Shelf.HOME) {
            toolbar.action(R.drawable.ic_sleep_timer, R.string.gallery_recent) { navigate(Shelf.RECENT) }
            toolbar.action(R.drawable.ic_star_outline_24, R.string.gallery_favorites) { navigate(Shelf.FAVORITES) }
            toolbar.action(R.drawable.ic_delete, R.string.gallery_bin) { navigate(Shelf.TRASH) }
        } else if (shelf == Shelf.PICK) {
            toolbar.action(R.drawable.ic_folder_open, R.string.gallery_browse_device) { pickOnDevice() }
            path.lastOrNull()?.let { folder ->
                toolbar.action(R.drawable.ic_check, R.string.gallery_choose) {
                    change({ library.addFolder(folder) }) { navigate(Shelf.HOME) }
                }
            }
        } else {
            toolbar.action(R.drawable.ic_sort_24, R.string.gallery_sort) {
                sortMenu(toolbar.getChildAt(toolbar.childCount - 1))
            }
        }
    }

    private fun ordering() = GalleryOrdering(prefs, path.lastOrNull()?.uri?.toString() ?: shelf.name, shelf)

    private fun sortMenu(anchor: View) {
        val order = ordering()
        PopupMenu(this, anchor).apply {
            for (mode in GalleryOrder.entries) {
                if (mode == GalleryOrder.MANUAL && shelf == Shelf.TRASH) continue
                menu.add(1, mode.ordinal + 1, mode.ordinal, mode.label).apply {
                    isCheckable = true
                    isChecked = mode == order.mode
                    setOnMenuItemClickListener {
                        if (mode == GalleryOrder.MANUAL && !prefs.contains("manual/${order.key}")) order.saveManual(tiles.tiles)
                        else order.choose(mode)
                        gridPosition = 0
                        gridOffset = 0
                        load()
                        true
                    }
                }
            }
            show()
        }
    }

    private fun selectPhoto(photo: Photo) {
        if (batch.busy) return
        batch.selected = if (photo.id in batch.selected) batch.selected - photo.id else batch.selected + photo.id
        tiles.selected = batch.selected
        header()
    }

    private fun clearSelection() {
        if (batch.busy) return
        batch.selected = emptySet()
        tiles.selected = emptySet()
        header()
    }

    private fun selectedPhotos() = tiles.photos(batch.selected)

    private fun destination(action: BatchAction) {
        if (!batch.busy) chooseDestination(action) { startBatch(action, it) }
    }

    private fun confirmBatchDelete() {
        if (batch.busy) return
        val trash = shelf == Shelf.TRASH
        val photos = selectedPhotos()
        val prompt = when {
            trash -> R.string.gallery_batch_discard
            photos.all { it.movable && it.parent != null } -> R.string.gallery_batch_trash
            else -> R.string.gallery_batch_hide
        }
        confirm(getString(R.string.gallery_selected, photos.size), prompt,
            if (trash) R.string.gallery_discard else R.string.gallery_trash) {
            startBatch(if (trash) BatchAction.DISCARD else BatchAction.TRASH)
        }
    }

    private fun startBatch(action: BatchAction, target: Folder? = null) {
        batch.run(action, selectedPhotos(), target)
        if (batch.busy) {
            header()
            progress.visibility = View.VISIBLE
        }
    }

    private fun select(item: GalleryTile) {
        if (batch.busy) return
        if (batch.selected.isNotEmpty()) {
            if (item is Photo) selectPhoto(item)
            return
        }
        when (item) {
            AddFolder -> {
                if (contentResolver.persistedUriPermissions.any { it.isReadPermission && android.provider.DocumentsContract.isTreeUri(it.uri) }) {
                    navigate(Shelf.PICK)
                } else pickOnDevice()
            }
            is Folder -> { path.add(item); navigate(if (shelf == Shelf.PICK) Shelf.PICK else Shelf.FOLDER) }
            is Photo -> {
                if (shelf == Shelf.FOLDER || shelf == Shelf.FAVORITES) {
                    lifecycleScope.launch(Dispatchers.IO) { runCatching { library.opened(item) } }
                }
                openViewer(item.id)
            }
        }
    }
    private fun pickOnDevice() {
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            path.lastOrNull()?.let { putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it.uri) }
        })
    }
    private fun navigate(to: Shelf) {
        closeViewer()
        shelf = to
        if (to != Shelf.FOLDER && to != Shelf.PICK) path.clear()
        pendingOpen = null
        gridPosition = 0
        gridOffset = 0
        load()
    }
    private fun load() {
        loading?.cancel()
        val section = shelf
        val folder = path.lastOrNull()
        val order = ordering()
        header()
        grid.zoomEnabled = section != Shelf.HOME && section != Shelf.PICK
        tiles.showCovers = section == Shelf.HOME
        tiles.inTrash = section == Shelf.TRASH
        grid.columns.spanCount = if (grid.zoomEnabled) photoColumns() else if (tablet()) 4 else 2
        grid.visibility = View.INVISIBLE
        empty.visibility = View.GONE
        progress.visibility = View.VISIBLE
        loading = lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val entries = when (section) {
                        Shelf.HOME -> library.folders()
                        Shelf.PICK -> if (folder == null) library.pickerRoots() else library.contents(folder, directoriesOnly = true)
                        Shelf.FOLDER -> library.contents(requireNotNull(folder))
                        else -> library.collection(section)
                    }
                    if (section == Shelf.HOME || section == Shelf.PICK) entries else order.arrange(entries)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            val items = result.orEmpty()
            tiles.display(items)
            if (!batch.busy) batch.selected = batch.selected.intersect(items.filterIsInstance<Photo>().map { it.id }.toSet())
            tiles.selected = batch.selected
            header()
            grid.columns.scrollToPositionWithOffset(gridPosition.coerceIn(0, (items.size - 1).coerceAtLeast(0)), gridOffset)
            grid.visibility = View.VISIBLE
            progress.visibility = if (batch.busy) View.VISIBLE else View.GONE
            empty.setText(if (result == null) R.string.gallery_folder_unavailable else when (section) {
                Shelf.FAVORITES -> R.string.gallery_no_favorites
                Shelf.RECENT -> R.string.gallery_no_recent
                Shelf.TRASH -> R.string.gallery_no_trash
                Shelf.PICK -> R.string.gallery_no_folders
                else -> R.string.gallery_no_images
            })
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            pendingOpen?.let(::openViewer)
            pendingOpen = null
        }
    }

    private fun acceptImage(intent: Intent) {
        val source = intent.data ?: return navigate(Shelf.RECENT)
        operation?.cancel()
        loading?.cancel()
        closeViewer()
        batch.selected = emptySet()
        tiles.selected = emptySet()
        path.clear()
        shelf = Shelf.RECENT
        header()
        grid.visibility = View.INVISIBLE
        empty.visibility = View.GONE
        progress.visibility = View.VISIBLE
        gridPosition = 0
        gridOffset = 0
        pendingImport = source
        operation = lifecycleScope.launch {
            try {
                pendingOpen = withContext(Dispatchers.IO) { library.openExternal(source, intent.flags) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message(R.string.gallery_unreadable) }
            pendingImport = null
            load()
        }
    }

    private fun openViewer(id: String) {
        val photos = tiles.tiles.filterIsInstance<Photo>()
        val index = photos.indexOfFirst { it.id == id }
        if (index < 0) return
        closeViewer()
        val screen = GalleryViewer(this, lifecycleScope, photos, index, shelf == Shelf.TRASH,
            ::back, ::toggleFavorite,
            ::share, { photo -> removeFromView(photo) { library.restore(photo) } }, ::remove, ::editPhoto)
        viewer = screen
        screen.controlsVisible = showControls
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
        browser.visibility = View.INVISIBLE
        updateSystemBarsVisibility()
    }
    private fun toggleFavorite(photo: Photo) {
        val updated = photo.copy(favorite = !photo.favorite)
        if (shelf == Shelf.FAVORITES && !updated.favorite) {
            if (viewer != null) removeFromView(photo) { library.setFavorite(photo, false) }
            else change({ library.setFavorite(photo, false) }) { rememberScroll(); load() }
        } else change({ library.setFavorite(photo, updated.favorite) }) {
            tiles.updateFavorite(updated)
            viewer?.updateFavorite(updated)
        }
    }
    private fun closeViewer() {
        val screen = viewer ?: return
        screen.release()
        root.removeView(screen)
        viewer = null
        browser.visibility = View.VISIBLE
        updateSystemBarsVisibility()
    }
    private fun rememberScroll() {
        if (grid.visibility != View.VISIBLE) return
        val first = grid.columns.findFirstVisibleItemPositions(null).filter { it != RecyclerView.NO_POSITION }.minOrNull()
            ?: return
        if (first != RecyclerView.NO_POSITION) {
            gridPosition = first
            gridOffset = (grid.columns.findViewByPosition(first)?.top ?: grid.paddingTop) - grid.paddingTop
        }
    }
    private fun back() {
        if (batch.busy) return
        if (batch.selected.isNotEmpty()) clearSelection()
        else if (viewer != null) {
            val id = viewer?.current?.id
            closeViewer()
            val position = tiles.tiles.indexOfFirst { it is Photo && it.id == id }
            if (position >= 0) grid.scrollToPosition(position)
            showControls = true
        } else if ((shelf == Shelf.FOLDER && path.size > 1) || (shelf == Shelf.PICK && path.isNotEmpty())) {
            path.removeAt(path.lastIndex)
            navigate(shelf)
        } else if (shelf != Shelf.HOME) {
            if (pendingImport != null) {
                operation?.cancel()
                pendingImport = null
            }
            navigate(Shelf.HOME)
        } else finish()
    }

    private fun removeFromView(photo: Photo, write: () -> Unit) {
        val screen = viewer ?: return
        val collection = tiles.tiles.filterIsInstance<Photo>()
        val index = collection.indexOf(photo)
        val next = collection.getOrNull(index + 1) ?: collection.getOrNull(index - 1)
        change(write) {
            if (viewer === screen) {
                pendingOpen = if (screen.current == photo) next?.id else screen.current.id
                showControls = screen.controlsVisible
                closeViewer()
            }
            rememberScroll()
            load()
        }
    }
    private fun remove(photo: Photo) {
        val inTrash = shelf == Shelf.TRASH
        val prompt = when {
            inTrash && photo.bin != null -> R.string.gallery_delete_file_message
            inTrash -> R.string.gallery_discard_message
            photo.parent != null && photo.movable -> R.string.gallery_move_message
            else -> R.string.gallery_hide_message
        }
        confirm(photo.name, prompt, if (inTrash) R.string.gallery_discard else R.string.gallery_trash) {
            removeFromView(photo) { if (inTrash) library.discard(photo) else library.trash(photo) }
        }
    }
    private fun share(photo: Photo) {
        try {
            val uri = if (photo.uri.scheme == "file") FileProvider.getUriForFile(this, "$packageName.provider",
                File(requireNotNull(photo.uri.path)), photo.name) else photo.uri
            val type = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?:
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(photo.name.substringAfterLast('.').lowercase()) ?: "image/*"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(photo.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getString(R.string.gallery_share)))
        } catch (_: Exception) { message(R.string.gallery_unreadable) }
    }
    private fun change(write: () -> Unit, done: () -> Unit) {
        if (operation?.isActive == true) return
        operation = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { write() }
                done()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message(R.string.gallery_operation_failed) }
        }
    }
    private fun confirm(name: String, prompt: Int, accept: Int, run: () -> Unit) {
        AlertDialog.Builder(this).setTitle(name).setMessage(prompt).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(accept) { _, _ -> run() }.create().apply { followImmersiveMode(); show() }
    }
    private fun message(text: Int) {
        AlertDialog.Builder(this).setMessage(text).setPositiveButton(R.string.ok, null)
            .create().apply { followImmersiveMode(); show() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        rememberScroll()
        outState.putString("shelf", shelf.name)
        outState.putStringArrayList("names", ArrayList(path.map { it.name }))
        outState.putStringArrayList("uris", ArrayList(path.map { it.uri.toString() }))
        outState.putString("photo", viewer?.current?.id ?: pendingOpen)
        outState.putString("import", pendingImport?.toString())
        outState.putBoolean("controls", viewer?.controlsVisible ?: showControls)
        outState.putInt("position", gridPosition)
        outState.putInt("offset", gridOffset)
    }
}
