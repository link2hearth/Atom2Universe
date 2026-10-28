package com.Atom2Universe.app.comics

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.readingprogress.data.ReadingProgressRepository
import com.Atom2Universe.app.stats.StatsTracker
import com.Atom2Universe.app.stats.data.StatsRepository
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/** Plus grand côté d'une page décodée ou rendue. */
private const val MAX_PAGE_SIDE = 4096

class ComicsReaderActivity : ThemedActivity() {

    companion object {
        const val EXTRA_COMIC_ID = "comic_id"
        const val EXTRA_COMIC_SOURCE = "comic_source"
        const val EXTRA_COMIC_FORMAT = "comic_format"
        const val EXTRA_COMIC_TITLE = "comic_title"
        const val EXTRA_COMIC_PAGE = "comic_page"
        /** PDF ouvert depuis le module Livres: suivi dans les récents des livres, pas dans la base BD. */
        const val EXTRA_FROM_BOOKS = "from_books"
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
    // Un seul fil pour tout ce qui touche au PdfRenderer et à l'archive : ouverture, rendus,
    // fermeture. La fermeture passe après le dernier rendu, jamais pendant.
    private val renderDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var currentPage = 0
    private var totalPages = 0
    private var barsVisible = true
    private var currentLayout = ZoomableImageView.ViewLayout.FULL

    private var comicId: String? = null
    // Titre de la BD : clé de progression et de stats (le titre affiché ne change jamais en cours de lecture)
    private var comicTitle = ""
    private var fromBooks = false
    private var comicFormat = "pdf"
    private var sourceUri: Uri? = null

    @Volatile private var pdfRenderer: PdfRenderer? = null
    @Volatile private var pdfFd: ParcelFileDescriptor? = null
    private var cbzNames: List<String>? = null
    @Volatile private var cbzZipFile: ZipFile? = null   // accès aléatoire O(1) par entrée
    @Volatile private var cbzPfd: ParcelFileDescriptor? = null  // maintenu ouvert pour /proc/self/fd
    private var folderImages: List<Uri>? = null

    private var isLoading = false
    private val readingProgressRepository by lazy { ReadingProgressRepository(this) }

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

        val sourcePath = intent.getStringExtra(EXTRA_COMIC_SOURCE)
        if (sourcePath == null) {
            // Ouverture externe (ex. appli Fichiers) — intent.data contient l'URI du PDF
            val externalUri = intent.data ?: run { finish(); return }
            fromBooks = intent.getBooleanExtra(EXTRA_FROM_BOOKS, false)
            if (fromBooks) handleBookPdfIntent(externalUri) else handleExternalIntent(externalUri)
            return
        }

        comicId = intent.getStringExtra(EXTRA_COMIC_ID)
        comicFormat = intent.getStringExtra(EXTRA_COMIC_FORMAT) ?: "pdf"
        // Les entrées scannées via File API utilisent des chemins absolus, pas des URIs
        sourceUri = if (sourcePath.startsWith("/")) Uri.fromFile(java.io.File(sourcePath)) else Uri.parse(sourcePath)
        // Page de départ depuis l'intent (sera écrasée par la valeur DB si disponible)
        currentPage = intent.getIntExtra(EXTRA_COMIC_PAGE, 0)
        setComicTitle(intent.getStringExtra(EXTRA_COMIC_TITLE) ?: "")

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

    private fun setComicTitle(title: String) {
        comicTitle = title
        titleText.text = title
    }

    private fun bookPrefs() = getSharedPreferences(
        com.Atom2Universe.app.books.BookLibraryActivity.PREFS_NAME, MODE_PRIVATE
    )

    /** PDF venant du module Livres: page de reprise lue dans les récents des livres, rien dans la base BD. */
    private fun handleBookPdfIntent(uri: Uri) {
        comicFormat = "pdf"
        sourceUri = uri
        val saved = com.Atom2Universe.app.books.BookLibraryActivity.loadLibrary(bookPrefs())
            .firstOrNull { it.uri == uri.toString() }
        currentPage = ((saved?.lastReadItem ?: 1) - 1).coerceAtLeast(0)
        setComicTitle(saved?.title ?: (uri.lastPathSegment?.substringBeforeLast('.') ?: ""))
        setupSource()
    }

    private fun saveBookPdfProgress() {
        val uri = sourceUri ?: return
        if (totalPages <= 0) return
        com.Atom2Universe.app.books.BookLibraryActivity.updateProgress(
            bookPrefs(), uri.toString(), currentPage + 1, totalPages
        )
    }

    private fun handleExternalIntent(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        comicFormat = "pdf"
        sourceUri = uri
        val fileName = contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment ?: getString(R.string.comics_default_document)
        val title = fileName.substringBeforeLast('.')
        setComicTitle(title)
        val uriStr = uri.toString()
        scope.launch {
            val dao = ComicsDatabase.getInstance(this@ComicsReaderActivity).comicsDao()
            val existing = withContext(Dispatchers.IO) { dao.getComicBySource(uriStr) }
            if (existing != null) {
                comicId = existing.id
                currentPage = existing.currentPage
            } else {
                val entry = ComicEntry(title = title, sourcePath = uriStr, format = "pdf", totalPages = 0, rootId = null)
                comicId = entry.id
                withContext(Dispatchers.IO) { dao.insertComic(entry) }
            }
            setupSource()
        }
    }

    override fun onResume() {
        super.onResume()
        comicTitle.takeIf { it.isNotEmpty() }?.let {
            StatsTracker.startReadingSession(StatsRepository.MODULE_COMIC, it)
        }
    }

    override fun onPause() {
        super.onPause()
        StatsTracker.endReadingSession()
        // Hors du fil de l'écran : saveScope n'est jamais annulé, l'écriture va au bout même si
        // l'activité est détruite. La page est déjà écrite à chaque changement (voir
        // initNavigation), donc la bibliothèque qui se rouvre lit la bonne page même si cette
        // écriture-ci, qui rafraîchit surtout la date de lecture, finit après elle.
        // Fin de séance : c'est ici seulement que la synchro entre appareils est déclenchée.
        saveProgress(sync = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        // Sur le fil des rendus : passe après un rendu en cours au lieu de fermer sous ses pieds
        saveScope.launch(renderDispatcher) {
            runCatching { pdfRenderer?.close() }
            pdfRenderer = null
            runCatching { pdfFd?.close() }
            pdfFd = null
            runCatching { cbzZipFile?.close() }
            cbzZipFile = null
            runCatching { cbzPfd?.close() }
            cbzPfd = null
        }
    }

    private fun saveProgress(sync: Boolean = false) {
        if (fromBooks) { saveBookPdfProgress(); return }
        val id = comicId ?: return
        val page = currentPage
        val title = comicTitle
        val total = totalPages
        saveScope.launch {
            ComicsDatabase.getInstance(applicationContext).comicsDao()
                .updateProgress(id, page, System.currentTimeMillis())
            if (total > 0 && title.isNotBlank()) {
                readingProgressRepository.updateProgress(
                    StatsRepository.MODULE_COMIC, title, page.toFloat() / total, triggerSync = sync
                )
            }
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
        val ok = withContext(renderDispatcher) {
            try {
                val fd = when (uri.scheme) {
                    "file" -> ParcelFileDescriptor.open(java.io.File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                    else -> contentResolver.openFileDescriptor(uri, "r")
                }
                pdfFd = fd
                val renderer = PdfRenderer(fd!!)
                pdfRenderer = renderer
                totalPages = renderer.pageCount
                true
            } catch (e: Exception) { false }
        }
        if (!ok) { showError(); return }
        initNavigation()
        loadCurrentPage()
    }

    private suspend fun setupCbz(uri: Uri) {
        val names = mutableListOf<String>()
        val ok = withContext(renderDispatcher) {
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

    private suspend fun initNavigation() {
        // Reprise synchronisée : si un autre appareil a lu ce titre plus récemment, on saute à sa page
        val startPage = currentPage
        val id = comicId
        val title = comicTitle
        if (id != null && totalPages > 0 && title.isNotBlank()) {
            val localEntry = withContext(Dispatchers.IO) {
                ComicsDatabase.getInstance(this@ComicsReaderActivity).comicsDao().getComicById(id)
            }
            val remote = readingProgressRepository.getProgress(StatsRepository.MODULE_COMIC, title)
            // sourceDeviceId nul = progression écrite ici : jamais prise pour une reprise d'ailleurs
            if (remote != null && remote.sourceDeviceId != null &&
                remote.lastReadTimestamp > (localEntry?.lastOpenedAt ?: 0L)) {
                currentPage = (remote.progressPercent * totalPages).roundToInt().coerceIn(0, totalPages - 1)
            }
        }

        if (currentPage >= totalPages) currentPage = 0
        if (currentPage != startPage) saveProgress()
        seekBar.max = maxOf(0, totalPages - 1)
        seekBar.progress = currentPage
        updatePageText()
        // Le scan de bibliothèque laisse totalPages à 0 pour les CBZ/dossiers (compte coûteux
        // à faire pendant le scan) : on le connaît seulement ici, il faut donc le réécrire en base
        // sinon la barre de progression reste bloquée à 0% pour toujours.
        if (id != null && totalPages > 0) {
            withContext(Dispatchers.IO) {
                ComicsDatabase.getInstance(this@ComicsReaderActivity).comicsDao().updateTotalPages(id, totalPages)
            }
        }
    }

    private fun navigatePage(delta: Int) {
        if (totalPages <= 0) return
        val newPage = (currentPage + delta).coerceIn(0, totalPages - 1)
        if (newPage != currentPage) {
            currentPage = newPage
            saveProgress()
            loadCurrentPage()
        }
    }

    private fun loadCurrentPage() {
        updatePageText()
        seekBar.progress = currentPage
        // Un chargement est déjà en cours : à sa fin, il relance la dernière page demandée
        if (isLoading) return
        isLoading = true
        val page = currentPage
        val (targetW, targetH) = renderTarget()
        scope.launch {
            val bmp = try {
                withContext(renderDispatcher) { renderPage(page, targetW, targetH) }
            } finally {
                isLoading = false
            }
            imageView.setBitmap(bmp, currentLayout)
            if (page != currentPage) loadCurrentPage()
        }
    }

    /** Taille visée pour une page : deux fois la vue (marge pour le zoom), plafonnée. */
    private fun renderTarget(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val w = imageView.width.takeIf { it > 0 } ?: dm.widthPixels
        val h = imageView.height.takeIf { it > 0 } ?: dm.heightPixels
        return (w * 2).coerceIn(1, MAX_PAGE_SIDE) to (h * 2).coerceIn(1, MAX_PAGE_SIDE)
    }

    private fun renderPage(page: Int, targetW: Int, targetH: Int): Bitmap? = when (comicFormat) {
        "pdf" -> renderPdfPage(page, targetW, targetH)
        "cbz" -> renderCbzPage(page, targetW, targetH)
        "folder" -> renderFolderPage(page, targetW, targetH)
        else -> null
    }

    private fun renderPdfPage(page: Int, targetW: Int, targetH: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (page >= renderer.pageCount) return null
        return try {
            renderer.openPage(page).use { pdfPage ->
                val pw = pdfPage.width.coerceAtLeast(1)
                val ph = pdfPage.height.coerceAtLeast(1)
                // Échelle d'après la vue (au lieu d'un ×2 fixe), plafonnée sur le plus grand côté
                val scale = minOf(
                    minOf(targetW.toFloat() / pw, targetH.toFloat() / ph),
                    MAX_PAGE_SIDE.toFloat() / maxOf(pw, ph)
                )
                val bmp = Bitmap.createBitmap(
                    (pw * scale).toInt().coerceAtLeast(1),
                    (ph * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(Color.WHITE)
                val matrix = android.graphics.Matrix()
                matrix.setScale(scale, scale)
                pdfPage.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
    }

    private fun renderCbzPage(page: Int, targetW: Int, targetH: Int): Bitmap? {
        val names = cbzNames ?: return null
        if (page >= names.size) return null
        val target = names[page]
        return try {
            val zf = cbzZipFile
            if (zf != null) {
                val entry = zf.getEntry(target) ?: return null
                decodePage(targetW, targetH) { zf.getInputStream(entry) }
            } else {
                // Fallback streaming si ZipFile n'a pas pu être ouvert : on garde les octets de
                // cette seule page pour lire ses dimensions puis la décoder
                val bytes = openCbzStream(sourceUri ?: return null)?.use { s ->
                    val zis = ZipInputStream(java.io.BufferedInputStream(s, 65536))
                    var entry = zis.nextEntry
                    var result: ByteArray? = null
                    while (entry != null && result == null) {
                        if (entry.name == target) result = zis.readBytes()
                        else zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    result
                } ?: return null
                decodePage(targetW, targetH) { bytes.inputStream() }
            }
        } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
    }

    private fun renderFolderPage(page: Int, targetW: Int, targetH: Int): Bitmap? {
        val uris = folderImages ?: return null
        if (page >= uris.size) return null
        return try {
            val u = uris[page]
            if (u.scheme == "file") decodePage(targetW, targetH) { java.io.FileInputStream(u.path!!) }
            else decodePage(targetW, targetH) { contentResolver.openInputStream(u) }
        } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
    }

    /** Dimensions d'abord, puis décodage sous-échantillonné ; [open] rouvre un flux neuf à chaque appel. */
    private fun decodePage(targetW: Int, targetH: Int, open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val first = open() ?: return null
        first.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = pageSampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
        }
        return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * Plus grande puissance de 2 qui garde la page au moins à la taille visée une fois ajustée
     * à l'écran, et sous le plafond [MAX_PAGE_SIDE].
     */
    private fun pageSampleSize(width: Int, height: Int, targetW: Int, targetH: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val fit = minOf(targetW.toFloat() / width, targetH.toFloat() / height)
        var sample = 1
        while (fit * sample * 2 <= 1f) sample *= 2
        while (maxOf(width, height) / sample > MAX_PAGE_SIDE) sample *= 2
        return sample
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
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
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
