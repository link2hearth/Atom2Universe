package com.Atom2Universe.app.comics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class ComicsReaderActivity : ThemedActivity() {

    companion object {
        const val EXTRA_COMIC_ID = "comic_id"
        const val EXTRA_COMIC_SOURCE = "comic_source"
        const val EXTRA_COMIC_FORMAT = "comic_format"
        const val EXTRA_COMIC_TITLE = "comic_title"
        const val EXTRA_COMIC_PAGE = "comic_page"
    }

    private lateinit var imageView: ZoomableImageView
    private lateinit var toolbar: View
    private lateinit var bottomBar: View
    private lateinit var titleText: TextView
    private lateinit var pageText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // Scope IO dédié à la sauvegarde — non annulé avec l'activité
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentPage = 0
    private var totalPages = 0
    private var barsVisible = true
    private var currentLayout = ZoomableImageView.ViewLayout.FULL

    private var comicId: String? = null
    private var comicFormat = "pdf"
    private var sourceUri: Uri? = null

    private var pdfRenderer: PdfRenderer? = null
    private var pdfFd: ParcelFileDescriptor? = null
    private var cbzNames: List<String>? = null
    private var cbzZipFile: ZipFile? = null   // accès aléatoire O(1) par entrée
    private var cbzPfd: ParcelFileDescriptor? = null  // maintenu ouvert pour /proc/self/fd
    private var folderImages: List<Uri>? = null

    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comics_reader)
        enableImmersiveMode()

        imageView = findViewById(R.id.comics_image_view)
        toolbar = findViewById(R.id.comics_toolbar)
        bottomBar = findViewById(R.id.comics_bottom_bar)
        titleText = findViewById(R.id.comics_title)
        pageText = findViewById(R.id.comics_page_text)
        seekBar = findViewById(R.id.comics_page_seekbar)
        btnPrev = findViewById(R.id.btn_prev_page)
        btnNext = findViewById(R.id.btn_next_page)

        comicId = intent.getStringExtra(EXTRA_COMIC_ID)
        comicFormat = intent.getStringExtra(EXTRA_COMIC_FORMAT) ?: "pdf"
        val sourcePath = intent.getStringExtra(EXTRA_COMIC_SOURCE) ?: run { finish(); return }
        // Les entrées scannées via File API utilisent des chemins absolus, pas des URIs
        sourceUri = if (sourcePath.startsWith("/")) Uri.fromFile(java.io.File(sourcePath)) else Uri.parse(sourcePath)
        // Page de départ depuis l'intent (sera écrasée par la valeur DB si disponible)
        currentPage = intent.getIntExtra(EXTRA_COMIC_PAGE, 0)

        titleText.text = intent.getStringExtra(EXTRA_COMIC_TITLE) ?: ""

        findViewById<ImageButton>(R.id.btn_comics_back).setOnClickListener { finish() }
        btnPrev.setOnClickListener { navigatePage(-1) }
        btnNext.setOnClickListener { navigatePage(1) }
        findViewById<ImageButton>(R.id.btn_layout).setOnClickListener { showLayoutPicker() }

        imageView.onSwipeLeft = { navigatePage(1) }
        imageView.onSwipeRight = { navigatePage(-1) }
        imageView.onTap = { toggleBars() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentPage = progress
                    loadCurrentPage()
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) { saveProgress() }
        })

        // Lire la page réelle depuis la DB pour ne pas dépendre de l'appelant
        scope.launch {
            val id = comicId
            if (id != null) {
                val saved = withContext(Dispatchers.IO) {
                    ComicsDatabase.getInstance(this@ComicsReaderActivity).comicsDao().getCurrentPage(id)
                }
                if (saved != null) currentPage = saved
            }
            setupSource()
        }
    }

    override fun onPause() {
        super.onPause()
        // Synchrone : garantit l'écriture avant que onResume de la bibliothèque ne lise la DB
        val id = comicId ?: return
        val page = currentPage
        runBlocking(Dispatchers.IO) {
            ComicsDatabase.getInstance(applicationContext).comicsDao()
                .updateProgress(id, page, System.currentTimeMillis())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        pdfRenderer?.close()
        pdfFd?.close()
        cbzZipFile?.close()
        cbzPfd?.close()
    }

    private fun saveProgress() {
        val id = comicId ?: return
        val page = currentPage
        saveScope.launch {
            ComicsDatabase.getInstance(applicationContext).comicsDao()
                .updateProgress(id, page, System.currentTimeMillis())
        }
    }

    private fun setupSource() {
        val uri = sourceUri ?: return
        scope.launch {
            when (comicFormat) {
                "pdf" -> setupPdf(uri)
                "cbz" -> setupCbz(uri)
                "folder" -> setupFolder(uri)
            }
        }
    }

    private suspend fun setupPdf(uri: Uri) {
        val ok = withContext(Dispatchers.IO) {
            try {
                pdfFd = when (uri.scheme) {
                    "file" -> ParcelFileDescriptor.open(java.io.File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                    else -> contentResolver.openFileDescriptor(uri, "r")
                }
                pdfRenderer = PdfRenderer(pdfFd!!)
                totalPages = pdfRenderer!!.pageCount
                true
            } catch (e: Exception) { false }
        }
        if (!ok) { showError(); return }
        initNavigation()
        loadCurrentPage()
    }

    private suspend fun setupCbz(uri: Uri) {
        val names = mutableListOf<String>()
        val ok = withContext(Dispatchers.IO) {
            try {
                val zf = openZipFileRandom(uri)
                if (zf != null) {
                    cbzZipFile = zf
                    zf.entries().toList()
                        .filter { !it.isDirectory && isImageName(it.name) }
                        .sortedWith(Comparator { a, b -> naturalCompare(a.name, b.name) })
                        .mapTo(names) { it.name }
                } else {
                    openCbzStream(uri)?.use { s ->
                        val zis = ZipInputStream(java.io.BufferedInputStream(s, 65536))
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && isImageName(entry.name)) names.add(entry.name)
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                        names.sortWith(Comparator { a, b -> naturalCompare(a, b) })
                    }
                }
                names.isNotEmpty()
            } catch (e: Exception) { false }
        }
        if (!ok) { showError(); return }
        cbzNames = names
        totalPages = names.size
        initNavigation()
        loadCurrentPage()
    }

    // ZipFile donne un accès aléatoire O(1) via le répertoire central du ZIP.
    // Pour les content:// URIs, /proc/self/fd/<n> convertit le FileDescriptor en chemin utilisable.
    private fun openZipFileRandom(uri: Uri): ZipFile? = try {
        when (uri.scheme) {
            "file" -> ZipFile(java.io.File(uri.path!!))
            else -> {
                val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return null
                cbzPfd = pfd
                ZipFile("/proc/self/fd/${pfd.fd}")
            }
        }
    } catch (_: Exception) { null }

    private fun openCbzStream(uri: Uri): java.io.InputStream? = when (uri.scheme) {
        "file" -> java.io.FileInputStream(java.io.File(uri.path!!))
        else -> contentResolver.openInputStream(uri)
    }

    private suspend fun setupFolder(uri: Uri) {
        val images = mutableListOf<Uri>()
        withContext(Dispatchers.IO) {
            if (uri.scheme == "file") {
                // Chemin absolu : File.listFiles() est quasi-instantané
                java.io.File(uri.path!!)
                    .listFiles()
                    ?.filter { it.isFile && isImageName(it.name) }
                    ?.sortedWith(Comparator { a, b -> naturalCompare(a.name, b.name) })
                    ?.forEach { images.add(Uri.fromFile(it)) }
            } else {
                // Fallback SAF
                DocumentFile.fromTreeUri(this@ComicsReaderActivity, uri)
                    ?.listFiles()
                    ?.filter { it.isFile && it.type?.startsWith("image/") == true }
                    ?.sortedWith(Comparator { a, b -> naturalCompare(a.name ?: "", b.name ?: "") })
                    ?.forEach { images.add(it.uri) }
            }
        }
        folderImages = images
        totalPages = images.size
        if (totalPages == 0) { showError(); return }
        initNavigation()
        loadCurrentPage()
    }

    private fun initNavigation() {
        if (currentPage >= totalPages) currentPage = 0
        seekBar.max = maxOf(0, totalPages - 1)
        seekBar.progress = currentPage
        updatePageText()
    }

    private fun navigatePage(delta: Int) {
        val newPage = (currentPage + delta).coerceIn(0, totalPages - 1)
        if (newPage != currentPage) {
            currentPage = newPage
            saveProgress()
            loadCurrentPage()
        }
    }

    private fun loadCurrentPage() {
        if (isLoading) return
        updatePageText()
        updatePageTitle()
        seekBar.progress = currentPage
        isLoading = true
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { renderPage(currentPage) }
            imageView.setBitmap(bmp, currentLayout)
            isLoading = false
        }
    }

    private fun updatePageTitle() {
        val name: String? = when (comicFormat) {
            "folder" -> folderImages?.getOrNull(currentPage)?.let { uri ->
                if (uri.scheme == "file") java.io.File(uri.path!!).name
                else uri.lastPathSegment
            }
            "cbz" -> cbzNames?.getOrNull(currentPage)?.let { java.io.File(it).name }
            else -> null
        }
        if (!name.isNullOrBlank()) titleText.text = name
    }

    private fun renderPage(page: Int): Bitmap? = when (comicFormat) {
        "pdf" -> renderPdfPage(page)
        "cbz" -> renderCbzPage(page)
        "folder" -> renderFolderPage(page)
        else -> null
    }

    private fun renderPdfPage(page: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (page >= renderer.pageCount) return null
        return try {
            renderer.openPage(page).use { pdfPage ->
                val scale = 2f
                val bmp = Bitmap.createBitmap(
                    (pdfPage.width * scale).toInt(),
                    (pdfPage.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(Color.WHITE)
                val matrix = android.graphics.Matrix()
                matrix.setScale(scale, scale)
                pdfPage.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (e: Exception) { null }
    }

    private fun renderCbzPage(page: Int): Bitmap? {
        val names = cbzNames ?: return null
        if (page >= names.size) return null
        val target = names[page]
        return try {
            val zf = cbzZipFile
            if (zf != null) {
                val entry = zf.getEntry(target) ?: return null
                zf.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
            } else {
                // Fallback streaming si ZipFile n'a pas pu être ouvert
                openCbzStream(sourceUri ?: return null)?.use { s ->
                    val zis = ZipInputStream(java.io.BufferedInputStream(s, 65536))
                    var entry = zis.nextEntry
                    var result: Bitmap? = null
                    while (entry != null && result == null) {
                        if (entry.name == target) result = BitmapFactory.decodeStream(zis)
                        else zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    result
                }
            }
        } catch (e: Exception) { null }
    }

    private fun renderFolderPage(page: Int): Bitmap? {
        val uris = folderImages ?: return null
        if (page >= uris.size) return null
        return try {
            val u = uris[page]
            if (u.scheme == "file") BitmapFactory.decodeFile(u.path)
            else contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null }
    }

    private fun updatePageText() {
        pageText.text = getString(R.string.comics_page_counter, currentPage + 1, totalPages)
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val vis = if (barsVisible) View.VISIBLE else View.GONE
        toolbar.visibility = vis
        bottomBar.visibility = vis
    }

    private fun showLayoutPicker() {
        val layouts = arrayOf(
            getString(R.string.comics_layout_full),
            getString(R.string.comics_layout_half_top),
            getString(R.string.comics_layout_half_left),
            getString(R.string.comics_layout_half_right)
        )
        val current = currentLayout.ordinal
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.comics_layout_title)
            .setSingleChoiceItems(layouts, current) { dialog, which ->
                currentLayout = ZoomableImageView.ViewLayout.values()[which]
                imageView.setLayout(currentLayout)
                dialog.dismiss()
            }
            .show()
    }

    private fun showError() {
        Toast.makeText(this, R.string.comics_error_open, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun isImageName(name: String): Boolean {
        val l = name.lowercase()
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") ||
               l.endsWith(".webp") || l.endsWith(".gif")
    }
}
