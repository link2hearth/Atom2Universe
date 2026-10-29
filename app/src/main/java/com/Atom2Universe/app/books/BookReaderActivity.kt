package com.Atom2Universe.app.books

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.Atom2Universe.app.util.ImmersivePopupWindow as PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.readingprogress.data.ReadingProgressRepository
import com.Atom2Universe.app.stats.StatsTracker
import com.Atom2Universe.app.stats.data.StatsRepository
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import android.graphics.BitmapFactory
import android.util.LruCache
import com.Atom2Universe.app.reading.decodeSampledStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import androidx.core.content.edit
import kotlin.math.roundToInt

// Regex précompilées: l'extraction EPUB les utilise pour chaque paragraphe (des milliers de fois)
private val HTML_TAG_RE = Regex("<[^>]+>")
private val WHITESPACE_RE = Regex("\\s+")
private val HTML_BR_RE = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val HTML_COMMENT_RE = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val HTML_ANY_TAG_RE = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9:\-]*)(?=[\s/>])[^>]*>""")
private val HTML_IMG_RE = Regex("""<img\b[^>]*?>""", RegexOption.IGNORE_CASE)
private val HTML_SRC_RE = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val HTML_ENTITY_RE = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);")
private val LINE_BREAK_RE = Regex("\r?\n")

/** Paragraphe et titres : un bloc de texte, fermé par sa balise. */
private val HTML_LEAF_CLOSE_RE: Map<String, Regex> = (listOf("p") + (1..6).map { "h$it" })
    .associateWith { Regex("</$it\\s*>", RegexOption.IGNORE_CASE) }

/** Contenu jamais affiché. */
private val HTML_SKIP_CLOSE_RE: Map<String, Regex> = listOf("script", "style", "head")
    .associateWith { Regex("</$it\\s*>", RegexOption.IGNORE_CASE) }

/** Conteneurs : ils ne font que séparer le texte libre, on descend dans leurs paragraphes. */
private val HTML_BLOCK_TAGS = setOf(
    "body", "div", "section", "article", "aside", "main", "header", "footer", "nav",
    "figure", "figcaption", "blockquote", "pre", "hr",
    "ul", "ol", "li", "dl", "dt", "dd", "table", "caption", "tr", "td", "th"
)

/** Plus grand côté d'une image décodée (plafond commun aux lecteurs). */
private const val MAX_DECODED_SIDE = 4096

/** Côté le plus court de la couverture enregistrée pour les récents. */
private const val READER_COVER_PX = 600

class BookReaderActivity : ThemedActivity() {

    companion object {
        private const val PREFS_NAME = "books_prefs"
        private const val KEY_CURRENT_URI = "current_book_uri"
        private const val KEY_CURRENT_TITLE = "current_book_title"
        private const val KEY_THEME = "reading_theme"
        private const val KEY_DISTINCTION = "para_distinction"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_FONT_NAME = "reading_font_name"
        private const val KEY_FONT_SIZE = "reading_font_size"
        const val EXTRA_BOOK_URI = "extra_book_uri"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
        private const val MENU_THEME = 2
        private const val MENU_FONT = 3
        private const val MENU_BOOKMARKS = 4
        private const val DEFAULT_FONT_SIZE = 16f
        private val AVAILABLE_FONTS = listOf(
            "Atkinson Hyperlegible.ttf" to "Atkinson Hyperlegible",
            "Space Grotesk.ttf" to "Space Grotesk",
            "JetBrains Mono.ttf" to "JetBrains Mono",
            "Caveat.ttf" to "Caveat",
            "Designer-Notes.ttf" to "Designer Notes",
            "Bungee.ttf" to "Bungee",
            "Chomsky.otf" to "Chomsky",
            "Orbitron-Regular.ttf" to "Orbitron",
            "Roboto-Condensed.ttf" to "Roboto Condensed",
            "ScienceGothic_Expanded-Regular.ttf" to "Science Gothic"
        )
    }

    // A checkout may contain no local font assets at all.
    private val availableFonts get(): List<Pair<String, String>> {
        val files = runCatching { assets.list("fonts").orEmpty().toSet() }.getOrDefault(emptySet())
        return AVAILABLE_FONTS.filter { it.first in files } +
            com.Atom2Universe.app.fonts.PersonalFonts.list(this).map { it.id to it.label }
    }

    private data class OpfData(val spineIds: List<String>, val manifest: Map<String, String>, val author: String, val coverItemId: String?)
    sealed class EpubItem {
        data class Paragraph(val text: String) : EpubItem()
        data class Heading(val text: String, val level: Int) : EpubItem()
        /** Image : seulement son chemin dans l'archive et ses dimensions ; décodée à l'affichage. */
        data class Image(val path: String, val width: Int, val height: Int) : EpubItem()
        object ChapterBreak : EpubItem()
    }
    private data class EpubContent(val items: List<EpubItem>, val author: String, val coverPath: String?, val zip: ZipFile? = null) {
        val paragraphs: List<String> get() = items.mapNotNull { when (it) {
            is EpubItem.Paragraph -> it.text
            is EpubItem.Heading  -> it.text
            else -> null
        } }
    }

    // ── Thèmes ────────────────────────────────────────────────────────────────

    enum class ReadingTheme(
        val bg1: Int, val bg2: Int,
        val readingBg: Int, val wordBg: Int,
        val textColor: Int,
        val toolbarBg: Int, val toolbarText: Int, val bottomBg: Int,
        val paletteColors: IntArray = intArrayOf(),
        val paletteWordColors: IntArray = intArrayOf()
    ) {
        DARK(
            bg1 = 0xFF0D0D0D.toInt(), bg2 = 0xFF282828.toInt(),
            readingBg = 0xFF0A2010.toInt(), wordBg = 0xFF194D28.toInt(),
            textColor = 0xFFDDDDDD.toInt(),
            toolbarBg = 0xFF111111.toInt(), toolbarText = 0xFFFFFFFF.toInt(),
            bottomBg = 0xFF111111.toInt()
        ),
        LIGHT(
            bg1 = 0xFFFFFFFF.toInt(), bg2 = 0xFFE4E4E4.toInt(),
            readingBg = 0xFFE8F5E9.toInt(), wordBg = 0xFF81C784.toInt(),
            textColor = 0xFF1A1A1A.toInt(),
            toolbarBg = 0xFFF8F8F8.toInt(), toolbarText = 0xFF111111.toInt(),
            bottomBg = 0xFFF8F8F8.toInt()
        ),
        SEPIA(
            bg1 = 0xFFF4E8C1.toInt(), bg2 = 0xFFEBD9A4.toInt(),
            readingBg = 0xFFD8E8C8.toInt(), wordBg = 0xFF8CB870.toInt(),
            textColor = 0xFF3B2A14.toInt(),
            toolbarBg = 0xFFE8D5A0.toInt(), toolbarText = 0xFF3B2A14.toInt(),
            bottomBg = 0xFFE8D5A0.toInt()
        ),
        SKY(
            bg1 = 0xFFE3F2FD.toInt(), bg2 = 0xFFBBDEFB.toInt(),
            readingBg = 0xFFC8E6C9.toInt(), wordBg = 0xFF66BB6A.toInt(),
            textColor = 0xFF0D1B2A.toInt(),
            toolbarBg = 0xFF90CAF9.toInt(), toolbarText = 0xFF0D1B2A.toInt(),
            bottomBg = 0xFF90CAF9.toInt()
        ),
        MINT(
            bg1 = 0xFFF1F8F1.toInt(), bg2 = 0xFFC8E6C9.toInt(),
            readingBg = 0xFFE8EAF6.toInt(), wordBg = 0xFF9FA8DA.toInt(),
            textColor = 0xFF1B3A1B.toInt(),
            toolbarBg = 0xFFA5D6A7.toInt(), toolbarText = 0xFF1B3A1B.toInt(),
            bottomBg = 0xFFA5D6A7.toInt()
        ),
        FOREST(
            bg1 = 0xFF1B4332.toInt(), bg2 = 0xFF2D6A4F.toInt(),
            readingBg = 0xFF1A3A5C.toInt(), wordBg = 0xFF4D7CC7.toInt(),
            textColor = 0xFFD8F3DC.toInt(),
            toolbarBg = 0xFF0D2A1C.toInt(), toolbarText = 0xFFD8F3DC.toInt(),
            bottomBg = 0xFF0D2A1C.toInt()
        ),
        PASTEL(
            bg1 = 0xFFFFE4E1.toInt(), bg2 = 0xFFF0FFF0.toInt(),
            readingBg = 0xFFFFECB3.toInt(), wordBg = 0xFFFFCA28.toInt(),
            textColor = 0xFF1A1A1A.toInt(),
            toolbarBg = 0xFFF3E5F5.toInt(), toolbarText = 0xFF1A1A1A.toInt(),
            bottomBg = 0xFFF3E5F5.toInt(),
            paletteColors = intArrayOf(
                0xFFFFE4E1.toInt(), // rose givrée
                0xFFF0FFF0.toInt(), // menthe douce
                0xFFE6E6FA.toInt(), // lavande
                0xFFFFFFCC.toInt(), // citron pâle
                0xFFE0F8FF.toInt(), // ciel d'eau
                0xFFFFE8D6.toInt(), // pêche
                0xFFF0E8FF.toInt(), // lilas
                0xFFE8FFE8.toInt(), // vert d'eau
                0xFFFFE0F0.toInt(), // rose bonbon
                0xFFEAF4FF.toInt()  // bleuet pâle
            ),
            paletteWordColors = intArrayOf(
                0xFFF06292.toInt(), // rose framboise
                0xFF66BB6A.toInt(), // vert herbe
                0xFF9575CD.toInt(), // violet lavande
                0xFFFDD835.toInt(), // jaune doré
                0xFF29B6F6.toInt(), // bleu océan
                0xFFFFB74D.toInt(), // orange pêche
                0xFFBA68C8.toInt(), // mauve
                0xFF4DB6AC.toInt(), // vert émeraude
                0xFFEC407A.toInt(), // rose soutenu
                0xFF64B5F6.toInt()  // bleu bleuet
            )
        );

        fun paragraphBg(paraIdx: Int): Int =
            if (paletteColors.isNotEmpty()) paletteColors[paraIdx % paletteColors.size]
            else if (paraIdx % 2 != 0) bg2 else bg1

        fun activeParagraphBg(paraIdx: Int, useDistinction: Boolean): Int =
            if (paletteColors.isNotEmpty()) {
                if (useDistinction) paragraphBg(paraIdx) else bg1
            } else readingBg

        fun wordHighlightBg(paraIdx: Int): Int =
            if (paletteWordColors.isNotEmpty()) {
                // Une touche de couleur plus vive dans le pastel d'origine : le mot reste
                // distinct, tout en gardant un excellent contraste avec le texte noir.
                ColorUtils.blendARGB(
                    paragraphBg(paraIdx),
                    paletteWordColors[paraIdx % paletteWordColors.size],
                    0.35f
                )
            } else wordBg
    }

    // ── TTS ───────────────────────────────────────────────────────────────────

    private enum class TtsState { IDLE, PLAYING, PAUSED }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsState = TtsState.IDLE
    private var ttsCurrentParagraph = 0
    private var ttsSpeed = 1.0f
    private var ttsPitch = 1.0f
    private var selectedVoiceName: String? = null
    private var paragraphs: List<String> = emptyList()
    private var pausedCharOffset = 0
    private var activeTtsSegment: TtsTextSegment? = null


    // ── Vues ──────────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // Écritures de progression : jamais annulées avec l'activité, pour que la dernière aille au bout
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Images EPUB décodées à la demande, deux à la fois au plus
    private val epubImageDispatcher = Dispatchers.IO.limitedParallelism(2)
    // Archive du livre ouvert, gardée ouverte pour lire les images au fil du défilement
    private var epubZip: ZipFile? = null
    private var autoSaveJob: Job? = null
    // Dernière position écrite : l'auto-sauvegarde n'écrit que si elle a changé
    private var lastSavedIdx = -1
    private var lastSavedOff = 0
    // Une position a été écrite depuis la dernière synchro (la synchro part en onStop)
    private var progressSyncPending = false
    // Faux tant que la position de reprise n'est pas appliquée : on n'écrase pas la sauvegarde avec 0
    private var positionRestored = false
    private lateinit var prefs: SharedPreferences
    private var currentTheme = ReadingTheme.DARK
    private var paragraphDistinction = true

    private lateinit var toolbar: MaterialToolbar
    private lateinit var contentContainer: FrameLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var txtRecycler: RecyclerView
    private lateinit var bottomBar: View
    private lateinit var bottomDivider: View
    private lateinit var pageIndicator: TextView
    private lateinit var playPauseBtn: TextView
    private lateinit var speedBtn: TextView
    private lateinit var pitchBtn: TextView
    private lateinit var voiceBtn: TextView

    private var currentUri: Uri? = null
    private var currentViewType = ViewType.EMPTY
    private var paragraphAdapter: ParagraphAdapter? = null
    private var currentFileSize = 0L

    private var currentFontName = "default"
    private var currentFontSize = DEFAULT_FONT_SIZE
    private var currentTypeface: Typeface = Typeface.DEFAULT
    private val typefaceCache = mutableMapOf<String, Typeface>()
    private var fontRevision = com.Atom2Universe.app.fonts.PersonalFonts.revision

    // ── Bookmarks ─────────────────────────────────────────────────────────────
    private val bookmarkData = mutableMapOf<Int, String>() // itemIndex → text snippet
    // Titre fourni par la bibliothèque (plus fiable que le nom de fichier), prioritaire quand présent
    private var pendingBookTitle: String? = null
    // Titre du livre affiché, pour les stats (la toolbar porte le nom de l'appli tant qu'aucun livre n'est ouvert)
    private var statsTitle: String? = null
    private val readingProgressRepository by lazy { ReadingProgressRepository(this) }

    // ── Auto-hide des barres ──────────────────────────────────────────────────
    private var barsActive = false   // true dès qu'un livre est ouvert
    private var barsVisible = true
    private val hideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideRunnable = Runnable { hideBarsAnimated() }

    private val openBookLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { openBook(it) } }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_book_reader)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentTheme = ReadingTheme.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ReadingTheme.DARK }
        paragraphDistinction = prefs.getBoolean(KEY_DISTINCTION, true)
        ttsSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        ttsPitch = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        selectedVoiceName = prefs.getString(KEY_TTS_VOICE, null)
        currentFontName = prefs.getString(KEY_FONT_NAME, "default") ?: "default"
        if (currentFontName != "default" && availableFonts.none { it.first == currentFontName }) {
            currentFontName = "default"
            prefs.edit().putString(KEY_FONT_NAME, currentFontName).apply()
        }
        currentFontSize = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        currentTypeface = loadTypeface(currentFontName)

        toolbar = findViewById(R.id.toolbar)
        contentContainer = findViewById(R.id.content_container)
        emptyState = findViewById(R.id.empty_state)
        txtRecycler = findViewById(R.id.txt_recycler)
        bottomBar = findViewById(R.id.bottom_bar)
        bottomDivider = findViewById(R.id.bottom_divider)
        pageIndicator = findViewById(R.id.page_indicator)
        playPauseBtn = findViewById(R.id.tts_play_pause)
        speedBtn = findViewById(R.id.tts_speed_btn)
        pitchBtn = findViewById(R.id.tts_pitch_btn)
        voiceBtn = findViewById(R.id.tts_voice_btn)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.open_book_button).setOnClickListener { pickBook() }

        applyThemeToUi()
        initTts()
        setupTtsControls()
        attachBarRevealListener(txtRecycler)
        // Toucher la toolbar ou la barre basse remet le compteur à zéro
        toolbar.setOnTouchListener { _, _ -> showBarsTemporarily(); false }
        bottomBar.setOnTouchListener { _, _ -> showBarsTemporarily(); false }

        pendingBookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE)?.takeIf { it.isNotBlank() }

        val intentUri = intent.getStringExtra(EXTRA_BOOK_URI)?.let { Uri.parse(it) }
            ?: intent.data
            ?: prefs.getString(KEY_CURRENT_URI, null)?.let { Uri.parse(it) }
        if (intentUri != null) {
            try { openBook(intentUri) } catch (_: Exception) { showEmpty() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (fontRevision != com.Atom2Universe.app.fonts.PersonalFonts.revision) {
            fontRevision = com.Atom2Universe.app.fonts.PersonalFonts.revision
            typefaceCache.clear()
            if (currentFontName != "default" && availableFonts.none { it.first == currentFontName }) {
                currentFontName = "default"
                prefs.edit().putString(KEY_FONT_NAME, currentFontName).apply()
            }
            currentTypeface = loadTypeface(currentFontName)
            paragraphAdapter?.updateFont(currentTypeface, currentFontSize)
        }
        if (barsActive) showBarsTemporarily()
        // Reprend le suivi du temps de lecture après un retour en avant-plan (le livre est déjà ouvert)
        startReadingStats()
    }

    /** Une séance seulement quand un livre est affiché : pas d'écran vide, pas de fichier refusé. */
    private fun startReadingStats() {
        if (currentViewType == ViewType.EMPTY) return
        statsTitle?.let { StatsTracker.startReadingSession(StatsRepository.MODULE_BOOK, it, owner = this) }
    }

    override fun onStart() {
        super.onStart()
        startAutoSave()
    }

    override fun onStop() {
        super.onStop()
        hideHandler.removeCallbacks(hideRunnable)
        autoSaveJob?.cancel()
        autoSaveJob = null
        savePosition(finalSave = true)
        // Ne ferme que notre séance : le lecteur de BD ouvert pour un PDF a déjà la sienne
        StatsTracker.endReadingSession(owner = this)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
        closeEpubZip()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_BOOKMARKS, 0, R.string.book_reader_bookmarks)
            .setIcon(R.drawable.ic_star_filled_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, MENU_THEME, 1, R.string.book_reader_theme)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_FONT, 2, R.string.book_reader_font_picker_title)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_BOOKMARKS)?.icon?.setTint(currentTheme.toolbarText)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_BOOKMARKS -> { showBookmarksList(); true }
        MENU_THEME -> { savePosition(); showThemePicker(); true }
        MENU_FONT -> { showFontPicker(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    private fun bookmarkKey(uri: Uri) = "bm_${uri.toString().hashCode()}"

    private fun loadBookmarks(uri: Uri) {
        bookmarkData.clear()
        val set = prefs.getStringSet(bookmarkKey(uri), emptySet()) ?: emptySet()
        for (entry in set) {
            val tabIdx = entry.indexOf('\t')
            if (tabIdx < 1) continue
            val idx = entry.substring(0, tabIdx).toIntOrNull() ?: continue
            bookmarkData[idx] = entry.substring(tabIdx + 1)
        }
    }

    private fun saveBookmarks(uri: Uri) {
        val set = bookmarkData.entries.mapTo(mutableSetOf()) { "${it.key}\t${it.value}" }
        prefs.edit { putStringSet(bookmarkKey(uri), set) }
    }

    fun toggleBookmark(itemIndex: Int, text: String) {
        val uri = currentUri ?: return
        if (bookmarkData.containsKey(itemIndex)) {
            bookmarkData.remove(itemIndex)
            Toast.makeText(this, R.string.book_reader_bookmark_removed, Toast.LENGTH_SHORT).show()
        } else {
            bookmarkData[itemIndex] = text.take(150).replace('\t', ' ')
            Toast.makeText(this, R.string.book_reader_bookmark_added, Toast.LENGTH_SHORT).show()
        }
        saveBookmarks(uri)
        paragraphAdapter?.setBookmarks(bookmarkData.keys.toSet())
    }

    private fun showBookmarksList() {
        if (currentViewType != ViewType.EPUB && currentViewType != ViewType.TXT) return
        if (bookmarkData.isEmpty()) {
            Toast.makeText(this, R.string.book_reader_no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }
        val sorted = bookmarkData.entries.sortedBy { it.key }
        val labels = sorted.map { getString(R.string.book_reader_bookmark_item, it.value) }.toTypedArray()
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.book_reader_bookmarks)
            .setItems(labels) { _, which ->
                val itemIdx = sorted[which].key
                txtRecycler.post {
                    (txtRecycler.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(itemIdx, dp(32))
                }
            }
            .setNeutralButton(R.string.book_reader_bookmarks_clear) { _, _ ->
                bookmarkData.clear()
                currentUri?.let { saveBookmarks(it) }
                paragraphAdapter?.setBookmarks(emptySet())
                Toast.makeText(this, R.string.book_reader_bookmarks_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Sélecteur de thème ────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun showThemePicker() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }

        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val switchLabel = TextView(this).apply {
            text = getString(R.string.book_reader_para_distinction)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val switchView = Switch(this).apply { isChecked = paragraphDistinction }
        switchRow.addView(switchLabel)
        switchRow.addView(switchView)
        root.addView(switchRow)

        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(0x22888888)
        })

        val themeLabels = listOf(
            getString(R.string.book_reader_theme_dark),
            getString(R.string.book_reader_theme_light),
            getString(R.string.book_reader_theme_sepia),
            getString(R.string.book_reader_theme_sky),
            getString(R.string.book_reader_theme_mint),
            getString(R.string.book_reader_theme_forest),
            getString(R.string.book_reader_theme_pastel)
        )
        val rg = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        ReadingTheme.entries.forEachIndexed { i, _ ->
            rg.addView(RadioButton(this).apply {
                text = themeLabels[i]; id = i
                isChecked = currentTheme.ordinal == i
                textSize = 15f; setPadding(dp(8), dp(10), dp(8), dp(10))
            })
        }
        root.addView(rg)

        val dialog = com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this).setTitle(R.string.book_reader_theme).setView(root).create()

        switchView.setOnCheckedChangeListener { _, checked ->
            paragraphDistinction = checked
            prefs.edit { putBoolean(KEY_DISTINCTION, checked) }
            paragraphAdapter?.setDistinction(checked)
        }
        rg.setOnCheckedChangeListener { _, checkedId ->
            val chosen = ReadingTheme.entries[checkedId]
            if (chosen != currentTheme) {
                currentTheme = chosen
                prefs.edit { putInt(KEY_THEME, checkedId) }
                applyThemeToUi()
                // Le recoloriage suffit (texte, fonds, séparateurs) : pas de rechargement du livre
                paragraphAdapter?.updateTheme(currentTheme)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun applyThemeToUi() {
        val t = currentTheme
        toolbar.setBackgroundColor(t.toolbarBg)
        toolbar.setTitleTextColor(t.toolbarText)
        toolbar.navigationIcon?.setTint(t.toolbarText)
        toolbar.overflowIcon?.setTint(t.toolbarText)
        invalidateOptionsMenu()
        contentContainer.setBackgroundColor(t.bg1)
        bottomBar.setBackgroundColor(t.bottomBg)
        bottomDivider.setBackgroundColor(
            if (android.graphics.Color.luminance(t.bg1) < 0.15f) 0x33FFFFFF.toInt() else 0x33000000.toInt()
        )
        for (v in listOf(pageIndicator, playPauseBtn, speedBtn, pitchBtn, voiceBtn)) {
            (v as TextView).setTextColor(t.textColor)
        }
    }

    // ── Police ───────────────────────────────────────────────────────────────

    private fun loadTypeface(name: String): Typeface {
        if (name == "default") return Typeface.DEFAULT
        if (com.Atom2Universe.app.fonts.PersonalFonts.isPersonal(name)) {
            return com.Atom2Universe.app.fonts.PersonalFonts.loadTypeface(this, name) ?: Typeface.DEFAULT
        }
        return typefaceCache.getOrPut(name) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val font = android.graphics.fonts.Font.Builder(assets, "fonts/$name").build()
                    val family = android.graphics.fonts.FontFamily.Builder(font).build()
                    Typeface.CustomFallbackBuilder(family)
                        .setSystemFallback("sans-serif")
                        .build()
                } else {
                    Typeface.createFromAsset(assets, "fonts/$name")
                }
            }.getOrNull() ?: Typeface.DEFAULT
        }
    }

    private fun showFontPicker() {
        val availableFonts = availableFonts
        var tempFontName = currentFontName
        var tempSize = currentFontSize
        var tempTypeface = currentTypeface

        val scrollRoot = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(16))
        }
        scrollRoot.addView(root)

        val previewTv = TextView(this).apply {
            text = getString(R.string.book_reader_font_preview_text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tempSize)
            typeface = tempTypeface
            setTextColor(currentTheme.textColor)
            background = GradientDrawable().apply {
                setColor(currentTheme.bg1)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), if (currentTheme == ReadingTheme.DARK) 0x44FFFFFF.toInt() else 0x33000000.toInt())
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setLineSpacing(0f, 1.4f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(16)) }
        }
        root.addView(previewTv)

        val sizeLabel = TextView(this).apply {
            text = getString(R.string.book_reader_font_size, tempSize.toInt())
            textSize = 14f
            setPadding(dp(20), 0, dp(16), 0)
        }
        root.addView(sizeLabel)

        val sizeBar = SeekBar(this).apply {
            max = 20  // 10sp..30sp
            progress = (tempSize - 10f).toInt().coerceIn(0, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), dp(4), dp(8), dp(12)) }
        }
        root.addView(sizeBar)

        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                tempSize = (10f + progress).coerceIn(10f, 30f)
                sizeLabel.text = getString(R.string.book_reader_font_size, tempSize.toInt())
                previewTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, tempSize)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                currentFontSize = tempSize
                prefs.edit { putFloat(KEY_FONT_SIZE, tempSize) }
                paragraphAdapter?.updateFont(tempTypeface, tempSize)
            }
        })

        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(8)) }
            setBackgroundColor(0x22888888)
        })

        val rg = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        rg.addView(RadioButton(this).apply {
            text = getString(R.string.book_reader_font_default)
            id = 0
            isChecked = tempFontName == "default"
            textSize = 15f
            setPadding(dp(8), dp(10), dp(8), dp(10))
        })
        availableFonts.forEachIndexed { i, (file, label) ->
            rg.addView(RadioButton(this).apply {
                text = label
                id = i + 1
                typeface = loadTypeface(file)
                isChecked = tempFontName == file
                textSize = 17f
                setPadding(dp(8), dp(10), dp(8), dp(10))
            })
        }
        rg.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == 0) {
                tempFontName = "default"
                tempTypeface = Typeface.DEFAULT
            } else {
                val (file, _) = availableFonts.getOrNull(checkedId - 1) ?: return@setOnCheckedChangeListener
                tempFontName = file
                tempTypeface = loadTypeface(file)
            }
            previewTv.typeface = tempTypeface
            currentFontName = tempFontName
            currentTypeface = tempTypeface
            prefs.edit { putString(KEY_FONT_NAME, tempFontName) }
            paragraphAdapter?.updateFont(tempTypeface, currentFontSize)
        }
        root.addView(rg)

        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.book_reader_font_picker_title)
            .setView(scrollRoot)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ── TTS init & contrôles ─────────────────────────────────────────────────

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(ttsSpeed)
                tts?.setPitch(ttsPitch)
                selectedVoiceName?.let { name ->
                    tts?.voices?.find { it.name == name }?.let { tts?.voice = it }
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        val (idx, segment) = parseTtsUtteranceId(utteranceId) ?: return
                        runOnUiThread {
                            if (activeTtsSegment != segment || idx != ttsCurrentParagraph) return@runOnUiThread
                            paragraphAdapter?.setReadingParagraph(idx)
                            autoScrollToReadingParagraph(idx)
                        }
                    }
                    override fun onDone(utteranceId: String) {
                        val (idx, segment) = parseTtsUtteranceId(utteranceId) ?: return
                        runOnUiThread {
                            if (ttsState != TtsState.PLAYING || activeTtsSegment != segment || idx != ttsCurrentParagraph) return@runOnUiThread
                            val paragraph = paragraphs.getOrNull(idx)
                            if (paragraph != null && segment.end < paragraph.length) speakParagraphFrom(idx, segment.end)
                            else speakParagraph(idx + 1)
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        val parsed = parseTtsUtteranceId(utteranceId) ?: return
                        runOnUiThread {
                            if (ttsState == TtsState.PLAYING && parsed.second == activeTtsSegment) stopReading()
                        }
                    }
                    override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                        val (idx, segment) = parseTtsUtteranceId(utteranceId) ?: return
                        runOnUiThread {
                            if (activeTtsSegment != segment || idx != ttsCurrentParagraph) return@runOnUiThread
                            val absoluteStart = start + segment.start
                            val absoluteEnd = end + segment.start
                            paragraphAdapter?.highlightWord(idx, absoluteStart, absoluteEnd)
                            paragraphAdapter?.focusHighlight(idx, absoluteStart)
                        }
                    }
                })
            }
        }
    }

    private fun setupTtsControls() {
        speedBtn.text = getString(R.string.book_reader_speed_value, ttsSpeed)
        pitchBtn.text = getString(R.string.book_reader_pitch_value, ttsPitch)
        updatePlayPauseBtn()

        playPauseBtn.setOnClickListener {
            when (ttsState) {
                TtsState.IDLE -> {
                    if ((currentViewType == ViewType.TXT || currentViewType == ViewType.EPUB) && paragraphs.isNotEmpty()) {
                        startReading(ttsCurrentParagraph.coerceIn(0, paragraphs.lastIndex))
                    } else {
                        Toast.makeText(this, R.string.book_reader_tts_not_ready, Toast.LENGTH_SHORT).show()
                    }
                }
                TtsState.PLAYING -> pauseReading()
                TtsState.PAUSED -> resumeReading()
            }
        }

        speedBtn.setOnClickListener {
            showSliderPopup(speedBtn, ttsSpeed, 0.5f, 2.0f, 0.1f, R.string.book_reader_speed_value) { v ->
                ttsSpeed = v
                speedBtn.text = getString(R.string.book_reader_speed_value, v)
                prefs.edit { putFloat(KEY_TTS_SPEED, v) }
                tts?.setSpeechRate(v)
                if (ttsState == TtsState.PLAYING) restartCurrentParagraph()
            }
        }

        pitchBtn.setOnClickListener {
            showSliderPopup(pitchBtn, ttsPitch, 0.5f, 2.0f, 0.1f, R.string.book_reader_pitch_value) { v ->
                ttsPitch = v
                pitchBtn.text = getString(R.string.book_reader_pitch_value, v)
                prefs.edit { putFloat(KEY_TTS_PITCH, v) }
                tts?.setPitch(v)
                if (ttsState == TtsState.PLAYING) restartCurrentParagraph()
            }
        }

        voiceBtn.setOnClickListener { showVoicePicker() }
    }

    private fun showVoicePicker() {
        val engine = tts ?: run {
            Toast.makeText(this, R.string.book_reader_tts_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        // Toutes les voix disponibles, locales en premier, puis réseau, triées par qualité
        val voices = (engine.voices?.toList() ?: emptyList())
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))

        if (voices.isEmpty()) {
            Toast.makeText(this, R.string.book_reader_tts_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = voices.mapIndexed { i, v ->
            val parts = v.name.split("-")
            val xIdx = parts.indexOf("x")
            val code = if (xIdx >= 0 && xIdx + 1 < parts.size) parts[xIdx + 1].uppercase()
                       else v.name.take(6)
            val lang = v.locale.displayLanguage.replaceFirstChar { it.uppercase() }
            val network = if (v.isNetworkConnectionRequired) getString(R.string.book_reader_voice_network) else ""
            getString(R.string.book_reader_voice_item, i + 1, lang, code, network)
        }

        val currentIdx = voices.indexOfFirst { it.name == selectedVoiceName }

        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.book_reader_voice)
            .setSingleChoiceItems(labels.toTypedArray(), currentIdx) { dialog, idx ->
                val voice = voices[idx]
                selectedVoiceName = voice.name
                engine.voice = voice
                prefs.edit { putString(KEY_TTS_VOICE, voice.name) }
                if (ttsState == TtsState.PLAYING) restartCurrentParagraph()
                dialog.dismiss()
            }
            .setNeutralButton(R.string.book_reader_voice_default) { _, _ ->
                selectedVoiceName = null
                prefs.edit { remove(KEY_TTS_VOICE) }
                engine.language = Locale.getDefault()
                if (ttsState == TtsState.PLAYING) restartCurrentParagraph()
            }
            .show()
    }

    private fun restartCurrentParagraph() {
        tts?.stop()
        speakParagraphFrom(ttsCurrentParagraph, 0)
    }

    private fun showSliderPopup(
        anchor: View, current: Float, min: Float, max: Float, step: Float, valueFormat: Int,
        onRelease: (Float) -> Unit
    ) {
        val steps = Math.round((max - min) / step)
        val popupWidth = dp(220)

        val valueLabel = TextView(this).apply {
            text = getString(valueFormat, current)
            textSize = 20f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(currentTheme.textColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val seekBar = SeekBar(this).apply {
            this.max = steps
            progress = Math.round((current - min) / step)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(currentTheme.bottomBg)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (currentTheme == ReadingTheme.DARK) 0x55FFFFFF.toInt() else 0x33000000.toInt())
            }
            elevation = dp(6).toFloat()
            addView(valueLabel); addView(seekBar)
        }

        val popup = PopupWindow(container, popupWidth, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener { enableImmersiveMode() }
        }

        container.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )
        val xOff = -(popupWidth - anchor.width) / 2
        val yOff = -(container.measuredHeight + anchor.height + dp(6))
        popup.showAsDropDown(anchor, xOff, yOff)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                valueLabel.text = getString(valueFormat, (min + progress * step).coerceIn(min, max))
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                onRelease((min + sb.progress * step).coerceIn(min, max))
                popup.dismiss()
            }
        })
    }

    // ── Lecture TTS ───────────────────────────────────────────────────────────

    private fun startReading(fromParagraph: Int) {
        ttsCurrentParagraph = fromParagraph
        ttsState = TtsState.PLAYING
        updatePlayPauseBtn()
        speakParagraph(fromParagraph)
    }

    private fun speakParagraph(idx: Int) {
        if (idx >= paragraphs.size) { stopReading(); return }
        if (ttsState != TtsState.PLAYING) return
        speakParagraphFrom(idx, 0)
    }

    private fun speakParagraphFrom(idx: Int, fromOffset: Int) {
        if (ttsState != TtsState.PLAYING) return
        val paragraph = paragraphs.getOrNull(idx) ?: run { stopReading(); return }
        val maxChars = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtMost(TtsTextSegmenter.DEFAULT_MAX_CHARS)
        val segment = TtsTextSegmenter.next(paragraph, fromOffset, maxChars = maxChars)
            ?: run { speakParagraph(idx + 1); return }
        ttsCurrentParagraph = idx
        activeTtsSegment = segment
        tts?.speak(segment.text, TextToSpeech.QUEUE_FLUSH, null, ttsUtteranceId(idx, segment))
    }

    private fun pauseReading() {
        pausedCharOffset = (paragraphAdapter?.getCurrentHighlightStart() ?: 0).coerceAtLeast(0)
        ttsState = TtsState.PAUSED
        tts?.stop()
        updatePlayPauseBtn()
    }

    private fun resumeReading() {
        ttsState = TtsState.PLAYING
        updatePlayPauseBtn()
        speakParagraphFrom(ttsCurrentParagraph, pausedCharOffset)
    }

    private fun stopReading() {
        tts?.stop()
        ttsState = TtsState.IDLE
        pausedCharOffset = 0
        activeTtsSegment = null
        updatePlayPauseBtn()
        paragraphAdapter?.clearHighlight()
    }

    private fun updatePlayPauseBtn() {
        playPauseBtn.setText(when (ttsState) {
            TtsState.PLAYING -> R.string.book_reader_tts_pause
            else -> R.string.book_reader_tts_play
        })
    }

    private fun autoScrollToReadingParagraph(idx: Int) {
        val lm = txtRecycler.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        val itemIdx = paragraphAdapter?.itemIndexForParagraph(idx) ?: idx
        if (itemIdx < first || itemIdx > last) txtRecycler.smoothScrollToPosition(itemIdx)
    }

    private fun ttsUtteranceId(idx: Int, segment: TtsTextSegment) = "$idx:${segment.start}:${segment.end}"

    private fun parseTtsUtteranceId(id: String): Pair<Int, TtsTextSegment>? {
        val parts = id.split(':')
        if (parts.size != 3) return null
        val idx = parts[0].toIntOrNull() ?: return null
        val start = parts[1].toIntOrNull() ?: return null
        val end = parts[2].toIntOrNull() ?: return null
        val paragraph = paragraphs.getOrNull(idx) ?: return null
        if (start !in 0..paragraph.length || end !in (start + 1)..paragraph.length) return null
        return idx to TtsTextSegment(start, end, paragraph.substring(start, end))
    }

    // ── Ouverture de livre ────────────────────────────────────────────────────

    private fun pickBook() {
        stopReading()
        openBookLauncher.launch(arrayOf(
            "application/epub+zip", "application/pdf", "text/plain", "*/*"
        ))
    }

    private fun openBook(uri: Uri) {
        stopReading()
        val mimeType = contentResolver.getType(uri)
        val fileName = getFileName(uri)
        // Le lecteur n'ouvre que l'EPUB, le PDF (passé au lecteur de BD) et le texte brut
        if (!isEpub(mimeType, fileName) && !isPdf(mimeType, fileName) && !isTxt(mimeType, fileName)) {
            pendingBookTitle = null
            showEmpty()
            Toast.makeText(this, R.string.book_reader_error, Toast.LENGTH_SHORT).show()
            return
        }
        // Le titre fourni par la bibliothèque (EPUB metadata, plus fiable) prime sur celui dérivé du nom de fichier,
        // pour que le titre affiché reste identique à celui utilisé par les stats de lecture et la sync de progression.
        val title = pendingBookTitle?.also { pendingBookTitle = null }
            ?: fileName?.substringBeforeLast('.')
            ?: getString(R.string.book_reader_title)
        toolbar.title = title
        currentUri = uri
        currentFileSize = getFileSize(uri)
        loadBookmarks(uri)
        prefs.edit {
            putString(KEY_CURRENT_URI, uri.toString())
            putString(KEY_CURRENT_TITLE, title)
        }
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: Exception) {}
        when {
            isEpub(mimeType, fileName) -> loadEpub(uri)
            isPdf(mimeType, fileName) -> {
                // Les PDF ouverts depuis les livres restent dans les récents des livres (pas dans les BD)
                val now = System.currentTimeMillis()
                BookLibraryActivity.updateOrAddBook(prefs, BookEntry(
                    uri = uri.toString(), title = title, author = "", coverPath = null,
                    totalItems = 0, lastReadItem = 0, format = "PDF",
                    addedAt = now, lastOpenedAt = now, fileSize = currentFileSize
                ))
                startActivity(Intent(this, com.Atom2Universe.app.comics.ComicsReaderActivity::class.java).apply {
                    data = uri
                    putExtra(com.Atom2Universe.app.comics.ComicsReaderActivity.EXTRA_FROM_BOOKS, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                finish()
            }
            else -> loadTxt(uri)
        }
        // Le PDF part au lecteur de BD, qui compte lui-même son temps de lecture
        if (!isPdf(mimeType, fileName)) {
            statsTitle = title
            startReadingStats()
        }
    }

    private fun isEpub(mime: String?, name: String?) =
        mime?.contains("epub") == true || name?.endsWith(".epub", true) == true

    private fun isPdf(mime: String?, name: String?) =
        mime?.contains("pdf") == true || name?.endsWith(".pdf", true) == true

    private fun isTxt(mime: String?, name: String?) =
        mime?.startsWith("text/plain") == true || name?.endsWith(".txt", true) == true

    /** Oublie le livre affiché avant d'en charger un autre (la position ne sera écrite qu'après la reprise). */
    private fun resetForNewBook() {
        paragraphAdapter = null
        positionRestored = false
        lastSavedIdx = -1
        lastSavedOff = 0
        closeEpubZip()
    }

    private fun closeEpubZip() {
        val zip = epubZip ?: return
        epubZip = null
        runCatching { zip.close() }
    }

    // ── TXT ──────────────────────────────────────────────────────────────────

    private fun loadTxt(uri: Uri) {
        resetForNewBook()
        showView(ViewType.TXT)
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { readTextWithCharsetDetection(uri) }.getOrElse { "" }
            }
            if (currentUri != uri) return@launch
            if (text.isEmpty()) {
                showEmpty()
                Toast.makeText(this@BookReaderActivity, R.string.book_reader_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            paragraphs = splitParagraphs(text)
            BookLibraryActivity.updateOrAddBook(prefs, BookEntry(
                uri = uri.toString(),
                title = toolbar.title?.toString() ?: getString(R.string.book_reader_title),
                author = "", coverPath = null, totalItems = paragraphs.size, lastReadItem = 0,
                format = "TXT", addedAt = System.currentTimeMillis(), lastOpenedAt = System.currentTimeMillis(),
                fileSize = currentFileSize
            ))
            showParagraphs(paragraphs.map { EpubItem.Paragraph(it) }, uri)
        }
    }

    private fun splitParagraphs(text: String): List<String> =
        text.split(LINE_BREAK_RE)
            .map { it.trim() }.filter { it.length > 3 }

    /** Installe l'adaptateur, le compteur de paragraphes et la reprise de lecture (TXT et EPUB). */
    private fun showParagraphs(items: List<EpubItem>, uri: Uri) {
        val adapter = ParagraphAdapter(items, currentTheme, paragraphDistinction, currentTypeface, currentFontSize)
        paragraphAdapter = adapter
        adapter.setBookmarks(bookmarkData.keys.toSet())
        val lm = LinearLayoutManager(this)
        txtRecycler.layoutManager = lm
        txtRecycler.clearOnScrollListeners()
        txtRecycler.adapter = adapter
        pageIndicator.text = getString(R.string.book_reader_page_of, 1, paragraphs.size)
        var shownParagraph = 1
        txtRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstItem = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
                val paragraphNumber = (adapter.paragraphIndexAtOrAfter(firstItem) + 1)
                    .coerceIn(1, paragraphs.size)
                // Le défilement appelle ceci à chaque image : on ne réécrit le compteur que s'il change
                if (paragraphNumber == shownParagraph) return
                shownParagraph = paragraphNumber
                pageIndicator.text = getString(R.string.book_reader_page_of, paragraphNumber, paragraphs.size)
            }
        })
        restoreTxtPosition(uri)
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private fun loadEpub(uri: Uri) {
        resetForNewBook()
        showView(ViewType.EPUB)
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                extractEpubContent(uri).also { if (!isActive) it.zip?.let { z -> runCatching { z.close() } } }
            }
            // Un autre livre a été ouvert pendant l'analyse : celle-ci ne sert plus
            if (currentUri != uri) {
                content.zip?.let { runCatching { it.close() } }
                return@launch
            }
            epubZip = content.zip
            if (content.paragraphs.isEmpty()) {
                closeEpubZip()
                showEmpty()
                Toast.makeText(this@BookReaderActivity, R.string.book_reader_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            paragraphs = content.paragraphs
            BookLibraryActivity.updateOrAddBook(prefs, BookEntry(
                uri = uri.toString(),
                title = toolbar.title?.toString() ?: getString(R.string.book_reader_title),
                author = content.author, coverPath = content.coverPath,
                totalItems = paragraphs.size, lastReadItem = 0,
                format = "EPUB", addedAt = System.currentTimeMillis(), lastOpenedAt = System.currentTimeMillis(),
                fileSize = currentFileSize
            ))
            showParagraphs(content.items, uri)
        }
    }

    /**
     * Ouvre l'EPUB en accès direct (ZipFile) : seuls le sommaire et les chapitres sont lus,
     * jamais tout le livre en mémoire. Un document `content://` est d'abord copié en cache.
     */
    private fun openEpubZip(uri: Uri): ZipFile? = runCatching {
        if (uri.scheme == "file") return@runCatching ZipFile(File(uri.path ?: return@runCatching null))
        val dir = File(cacheDir, "epub_reader").apply { mkdirs() }
        val target = File(dir, "${uri.toString().hashCode()}.epub")
        val upToDate = target.exists() && currentFileSize > 0 && target.length() == currentFileSize
        if (!upToDate) {
            dir.listFiles()?.forEach { it.delete() }
            val part = File(dir, "${target.name}.part")
            val copied = contentResolver.openInputStream(uri)?.use { input ->
                part.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                true
            } ?: false
            if (!copied || !part.renameTo(target)) {
                part.delete()
                return@runCatching null
            }
        }
        ZipFile(target)
    }.getOrNull()

    private fun extractEpubContent(uri: Uri): EpubContent {
        val zip = openEpubZip(uri) ?: return EpubContent(emptyList(), "", null)
        return try {
            extractEpubContent(zip, uri)
        } catch (_: Exception) {
            runCatching { zip.close() }
            EpubContent(emptyList(), "", null)
        }
    }

    private fun extractEpubContent(zip: ZipFile, uri: Uri): EpubContent {
        val opfPath = zip.getEntry("META-INF/container.xml")?.let { parseContainerXml(readEntry(zip, it)) }
        val opfEntry = opfPath?.let { zip.getEntry(it) }
        if (opfPath == null || opfEntry == null) return EpubContent(fallbackEpubItems(zip), "", null, zip)
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = parseOpf(readEntry(zip, opfEntry))
        val result = mutableListOf<EpubItem>()
        var firstChapter = true
        for (id in opf.spineIds) {
            val href = opf.manifest[id] ?: continue
            // Résolution robuste (gère "./" et "../" dans les hrefs du manifest) : un simple
            // opfDir+href sans normalisation ratait des chapitres et sous-comptait le total.
            val entry = resolveEpubEntry(href, opfDir, zip) ?: continue
            val htmlDir = entry.name.substringBeforeLast('/', "")
            val html = runCatching { readEntry(zip, entry).toString(Charsets.UTF_8) }.getOrNull() ?: continue
            val sizeBefore = result.size
            extractContentFromHtml(html, result, zip, htmlDir)
            if (result.size > sizeBefore) {
                if (!firstChapter) result.add(sizeBefore, EpubItem.ChapterBreak)
                firstChapter = false
            }
        }
        val items = if (result.none { it is EpubItem.Paragraph || it is EpubItem.Heading })
            fallbackEpubItems(zip)
        else result
        val coverPath = opf.coverItemId?.let { coverId ->
            val href = opf.manifest[coverId] ?: return@let null
            val entry = resolveEpubEntry(href, opfDir, zip) ?: return@let null
            saveCover(zip, entry, uri)
        }
        return EpubContent(items, opf.author, coverPath, zip)
    }

    private fun readEntry(zip: ZipFile, entry: ZipEntry): ByteArray =
        zip.getInputStream(entry).use { it.readBytes() }

    /** Sans sommaire exploitable : le premier fichier HTML de l'archive. */
    private fun fallbackEpubItems(zip: ZipFile): List<EpubItem> {
        val entry = zip.entries().asSequence().firstOrNull {
            !it.isDirectory && (it.name.endsWith(".html", true) || it.name.endsWith(".xhtml", true))
        } ?: return emptyList()
        val html = runCatching { readEntry(zip, entry).toString(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        return mutableListOf<EpubItem>().also {
            extractContentFromHtml(html, it, zip, entry.name.substringBeforeLast('/', ""))
        }
    }

    /**
     * Parcourt le corps d'un chapitre balise par balise. p et h1-h6 donnent un paragraphe
     * ou un titre ; les conteneurs (div, section, figure…) ne font que couper le texte libre
     * qu'ils contiennent : on descend dans leurs paragraphes au lieu de tout fusionner.
     */
    private fun extractContentFromHtml(
        html: String,
        out: MutableList<EpubItem>,
        zip: ZipFile,
        htmlDir: String
    ) {
        val bodyStart = html.indexOf("<body", ignoreCase = true).takeIf { it >= 0 } ?: return
        val bodyEnd = html.lastIndexOf("</body>", ignoreCase = true).takeIf { it > bodyStart } ?: return
        var body = html.substring(bodyStart, bodyEnd)
        if (body.contains("<!--")) body = body.replace(HTML_COMMENT_RE, "")

        // Texte hors paragraphe (directement dans un div, une cellule…) : <br> = saut de ligne
        val loose = StringBuilder()
        fun flushLoose() {
            if (loose.isEmpty()) return
            decodeHtmlEntities(loose.toString()).split('\n')
                .map { it.replace(WHITESPACE_RE, " ").trim() }
                .filter { it.length > 3 }
                .forEach { out.add(EpubItem.Paragraph(it)) }
            loose.setLength(0)
        }
        fun appendLoose(raw: String) {
            // Même un simple espace compte : il sépare deux mots mis en forme (<i>un</i> <b>deux</b>)
            if (raw.isNotEmpty()) loose.append(raw.replace(WHITESPACE_RE, " "))
        }

        var i = 0
        while (i < body.length) {
            val m = HTML_ANY_TAG_RE.find(body, i)
            if (m == null) {
                appendLoose(body.substring(i))
                break
            }
            if (m.range.first > i) appendLoose(body.substring(i, m.range.first))
            i = m.range.last + 1
            val closing = m.groupValues[1].isNotEmpty()
            val name = m.groupValues[2].substringAfter(':').lowercase()
            when {
                name == "br" -> loose.append('\n')
                name == "img" -> {
                    flushLoose()
                    epubImage(m.value, zip, htmlDir)?.let { out.add(it) }
                }
                closing -> if (name in HTML_BLOCK_TAGS || name in HTML_LEAF_CLOSE_RE) flushLoose()
                name in HTML_SKIP_CLOSE_RE -> {
                    if (!m.value.endsWith("/>")) {
                        HTML_SKIP_CLOSE_RE.getValue(name).find(body, i)?.let { i = it.range.last + 1 }
                    }
                }
                name in HTML_LEAF_CLOSE_RE -> {
                    flushLoose()
                    val close = if (m.value.endsWith("/>")) null else HTML_LEAF_CLOSE_RE.getValue(name).find(body, i)
                    if (close != null) {
                        addLeafBlock(name, body.substring(i, close.range.first), out, zip, htmlDir)
                        i = close.range.last + 1
                    }
                }
                name in HTML_BLOCK_TAGS -> flushLoose()
            }
        }
        flushLoose()
    }

    /** Un <p> ou un <hN> : ses images d'abord, puis son texte (<br> = simple espace). */
    private fun addLeafBlock(tagName: String, inner: String, out: MutableList<EpubItem>, zip: ZipFile, htmlDir: String) {
        for (imgM in HTML_IMG_RE.findAll(inner)) {
            epubImage(imgM.value, zip, htmlDir)?.let { out.add(it) }
        }
        val text = decodeHtmlEntities(
            inner.replace(HTML_BR_RE, " ")
                 .replace(HTML_TAG_RE, "")
        ).replace(WHITESPACE_RE, " ").trim()
        if (tagName == "p") {
            if (text.length > 3) out.add(EpubItem.Paragraph(text))
        } else if (text.isNotEmpty()) {
            out.add(EpubItem.Heading(text, tagName[1].digitToInt()))
        }
    }

    /** Image : on ne lit que ses dimensions ici (en-tête du fichier), le décodage se fait à l'affichage. */
    private fun epubImage(imgTag: String, zip: ZipFile, htmlDir: String): EpubItem.Image? {
        val src = HTML_SRC_RE.find(imgTag)?.groupValues?.get(1)?.trim() ?: return null
        val entry = resolveEpubEntry(src, htmlDir, zip) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return EpubItem.Image(entry.name, bounds.outWidth, bounds.outHeight)
    }

    /** Retrouve un fichier de l'archive d'après un href (relatif au dossier du document, encodé en %xx). */
    private fun resolveEpubEntry(src: String, baseDir: String, zip: ZipFile): ZipEntry? {
        val clean = decodeHref(src.substringBefore('#')).trimStart('/').removePrefix("./")
        if (clean.isEmpty()) return null
        val candidates = buildList {
            if (baseDir.isNotEmpty()) {
                add(normalizePath("$baseDir/$clean"))
                add("$baseDir/$clean")
            }
            add(clean)
            add(normalizePath(clean))
        }
        return candidates.firstNotNullOfOrNull { name -> zip.getEntry(name)?.takeIf { !it.isDirectory } }
    }

    private fun normalizePath(path: String): String {
        val result = mutableListOf<String>()
        for (part in path.split("/")) {
            when (part) { ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex); ".", "" -> {}; else -> result.add(part) }
        }
        return result.joinToString("/")
    }

    /** Décode une image du livre à la largeur de la liste (plafond [MAX_DECODED_SIDE]). */
    private fun decodeEpubImage(zip: ZipFile, item: EpubItem.Image, targetWidth: Int): Bitmap? = try {
        var sample = 1
        while (item.width / (sample * 2) >= targetWidth ||
            maxOf(item.width, item.height) / sample > MAX_DECODED_SIDE) sample *= 2
        val entry = zip.getEntry(item.path)
        if (entry == null) null
        else zip.getInputStream(entry).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun newXmlBuilder() = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()

    private fun parseContainerXml(bytes: ByteArray): String? = runCatching {
        val doc = newXmlBuilder().parse(bytes.inputStream())
        (doc.getElementsByTagNameNS("*", "rootfile").item(0) as? Element)
            ?.getAttribute("full-path")?.takeIf { it.isNotBlank() }
            ?.let { decodeHref(it) }
    }.getOrNull()

    private fun parseOpf(bytes: ByteArray): OpfData =
        runCatching {
            // Espaces de noms pris en compte : <opf:item>, <dc:creator>… sont trouvés quel que soit leur préfixe
            val doc = newXmlBuilder().parse(bytes.inputStream())
            val itemrefs = doc.getElementsByTagNameNS("*", "itemref")
            val spineIds = (0 until itemrefs.length).mapNotNull { (itemrefs.item(it) as? Element)?.getAttribute("idref") }
            val items = doc.getElementsByTagNameNS("*", "item")
            val itemElements = (0 until items.length).mapNotNull { items.item(it) as? Element }
            val manifest = itemElements.associate { el -> el.getAttribute("id") to el.getAttribute("href") }
            val author = doc.getElementsByTagNameNS("*", "creator").let {
                if (it.length > 0) it.item(0).textContent.trim() else ""
            }
            var coverId: String? = null
            val metas = doc.getElementsByTagNameNS("*", "meta")
            for (i in 0 until metas.length) {
                val el = metas.item(i) as? Element ?: continue
                if (el.getAttribute("name") == "cover") {
                    coverId = el.getAttribute("content").ifEmpty { null }; break
                }
            }
            if (coverId == null) {
                // EPUB3 : properties="cover-image"
                coverId = itemElements.firstOrNull { el ->
                    el.getAttribute("properties").split(' ').any { it == "cover-image" }
                }?.getAttribute("id")
            }
            if (coverId == null) {
                for (el in itemElements) {
                    val id = el.getAttribute("id").lowercase()
                    val mime = el.getAttribute("media-type")
                    if ((id == "cover-image" || id == "cover") && mime.startsWith("image/")) {
                        coverId = el.getAttribute("id"); break
                    }
                }
            }
            if (coverId == null) {
                for (el in itemElements) {
                    val href = el.getAttribute("href").lowercase()
                    val mime = el.getAttribute("media-type")
                    if (href.contains("cover") && mime.startsWith("image/")) {
                        coverId = el.getAttribute("id"); break
                    }
                }
            }
            OpfData(spineIds, manifest, author, coverId)
        }.getOrElse { OpfData(emptyList(), emptyMap(), "", null) }

    // ── Position save/restore ─────────────────────────────────────────────────

    private fun posKey(uri: Uri) = "pos_${uri.toString().hashCode()}"

    /**
     * Écrit la position seulement si elle a changé. [finalSave] (onStop) déclenche en plus
     * la synchro entre appareils si une position a été écrite depuis la dernière.
     */
    private fun savePosition(finalSave: Boolean = false) {
        val uri = currentUri ?: return
        val k = posKey(uri)
        val adapter = paragraphAdapter
        val lm = txtRecycler.layoutManager as? LinearLayoutManager
        var percent: Float? = null
        val title = toolbar.title?.toString()
        if (adapter != null && lm != null && positionRestored &&
            (currentViewType == ViewType.TXT || currentViewType == ViewType.EPUB)) {
            val pos = lm.findFirstVisibleItemPosition()
            val off = txtRecycler.getChildAt(0)?.top ?: 0
            if (pos >= 0 && (pos != lastSavedIdx || off != lastSavedOff)) {
                lastSavedIdx = pos
                lastSavedOff = off
                prefs.edit {
                    putInt("${k}_idx", pos)
                    putInt("${k}_off", off)
                    putLong("${k}_time", System.currentTimeMillis())
                }
                val paragraphNumber = adapter.paragraphIndexAtOrAfter(pos) + 1
                BookLibraryActivity.updateProgress(
                    prefs,
                    uri.toString(),
                    paragraphNumber.coerceIn(0, paragraphs.size),
                    paragraphs.size
                )
                // Progression synchronisable entre appareils : ratio position/nombre total d'items,
                // indépendant de l'écran (déterminé uniquement par le contenu du fichier).
                val itemCount = adapter.itemCount
                if (itemCount > 0 && !title.isNullOrBlank()) {
                    percent = pos.toFloat() / itemCount
                    progressSyncPending = true
                }
            }
        }
        val sync = finalSave && progressSyncPending
        if (sync) progressSyncPending = false
        val toWrite = percent
        if (toWrite == null && !sync) return
        saveScope.launch {
            if (toWrite != null && title != null) {
                readingProgressRepository.updateProgress(StatsRepository.MODULE_BOOK, title, toWrite, triggerSync = sync)
            } else {
                ReadingProgressRepository.requestSync()
            }
        }
    }

    private fun restoreTxtPosition(uri: Uri) {
        val k = posKey(uri)
        val localPos = prefs.getInt("${k}_idx", 0)
        val localOff = prefs.getInt("${k}_off", 0)
        val localTime = prefs.getLong("${k}_time", 0L)
        val title = toolbar.title?.toString()
        val itemCount = txtRecycler.adapter?.itemCount ?: 0

        scope.launch {
            val remote = title?.let { readingProgressRepository.getProgress(StatsRepository.MODULE_BOOK, it) }
            if (currentUri != uri) return@launch
            // Seule une progression venue d'un autre appareil (sourceDeviceId non nul) peut remplacer
            // la position locale : la nôtre, réécrite juste après la locale, garde son décalage précis.
            val remotePos = if (remote != null && remote.sourceDeviceId != null &&
                remote.lastReadTimestamp > localTime && itemCount > 0) {
                (remote.progressPercent * itemCount).roundToInt().coerceIn(0, itemCount - 1)
            } else null
            val pos = remotePos ?: localPos.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
            val off = if (remotePos != null) 0 else localOff
            // Reprise venue d'ailleurs : elle sera réécrite ici à la prochaine sauvegarde
            lastSavedIdx = if (remotePos != null) -1 else pos
            lastSavedOff = off
            if (pos > 0) {
                txtRecycler.post {
                    if (currentUri != uri) return@post
                    (txtRecycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, off)
                    ttsCurrentParagraph = paragraphAdapter?.paragraphIndexAtOrAfter(pos) ?: 0
                    positionRestored = true
                }
            } else {
                positionRestored = true
            }
        }
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch { while (true) { delay(15_000); savePosition() } }
    }

    // ── Décodage entités HTML ────────────────────────────────────────────────

    private fun decodeHtmlEntities(text: String): String {
        if (text.indexOf('&') < 0) return text
        return text.replace(HTML_ENTITY_RE) { m ->
            val entity = m.groupValues[1]
            when {
                entity.startsWith("#x") || entity.startsWith("#X") -> codePointText(entity.substring(2).toIntOrNull(16)) ?: m.value
                entity.startsWith("#") -> codePointText(entity.substring(1).toIntOrNull()) ?: m.value
                else -> HTML_NAMED_ENTITIES[entity] ?: m.value
            }
        }
    }

    /** Entité numérique → texte, y compris au-delà de U+FFFF (émojis, idéogrammes rares). */
    private fun codePointText(codePoint: Int?): String? =
        codePoint?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }

    // ── Détection d'encodage pour les fichiers TXT ────────────────────────────

    private fun readTextWithCharsetDetection(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.toString(Charsets.UTF_16BE)
            isValidUtf8(bytes) -> bytes.toString(Charsets.UTF_8)
            else -> bytes.toString(Charsets.ISO_8859_1)
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val extra = when {
                b and 0x80 == 0 -> 0
                b and 0xE0 == 0xC0 -> 1
                b and 0xF0 == 0xE0 -> 2
                b and 0xF8 == 0xF0 -> 3
                else -> return false
            }
            i++
            repeat(extra) {
                if (i >= bytes.size || bytes[i].toInt() and 0xC0 != 0x80) return false
                i++
            }
        }
        return true
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) return c.getString(idx) }
        }
        return uri.lastPathSegment
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun getFileSize(uri: Uri): Long = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) c.getLong(idx) else 0L
            } else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }

    /** Couverture pour les récents : écrite une seule fois, réduite (~600 px), jamais en pleine taille. */
    private fun saveCover(zip: ZipFile, entry: ZipEntry, uri: Uri): String? = runCatching {
        val dir = File(filesDir, "book_covers").apply { mkdirs() }
        val file = File(dir, "${uri.toString().hashCode()}.jpg")
        if (file.exists()) return@runCatching file.absolutePath
        val bmp = decodeSampledStream(READER_COVER_PX) { zip.getInputStream(entry) } ?: return@runCatching null
        file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        file.absolutePath
    }.getOrNull()

    // ── Gestion auto-hide des barres ─────────────────────────────────────────

    private fun showBarsTemporarily() {
        if (!barsActive) return
        hideHandler.removeCallbacks(hideRunnable)
        if (!barsVisible) {
            barsVisible = true
            for (v in listOf(toolbar, bottomDivider, bottomBar)) {
                v.visibility = View.VISIBLE
                v.alpha = 0f
                v.animate().alpha(1f).setDuration(200).start()
            }
        }
        hideHandler.postDelayed(hideRunnable, 10_000L)
    }

    private fun hideBarsAnimated() {
        if (!barsActive || !barsVisible) return
        barsVisible = false
        val dur = 350L
        toolbar.animate().alpha(0f).setDuration(dur).withEndAction {
            if (!barsVisible) toolbar.visibility = View.GONE
        }.start()
        bottomDivider.animate().alpha(0f).setDuration(dur).withEndAction {
            if (!barsVisible) bottomDivider.visibility = View.GONE
        }.start()
        bottomBar.animate().alpha(0f).setDuration(dur).withEndAction {
            if (!barsVisible) bottomBar.visibility = View.GONE
        }.start()
    }

    private fun attachBarRevealListener(recycler: RecyclerView) {
        val tapZonePx = dp(80)
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (e.y < tapZonePx || e.y > recycler.height - tapZonePx) {
                    showBarsTemporarily()
                }
                return false
            }
        })
        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gd.onTouchEvent(e)
                return false
            }
        })
    }

    private fun showEmpty() = showView(ViewType.EMPTY)
    private enum class ViewType { EMPTY, TXT, EPUB }

    private fun showView(type: ViewType) {
        currentViewType = type
        if (type == ViewType.EMPTY) {
            statsTitle = null
            StatsTracker.endReadingSession(owner = this)
        }
        emptyState.visibility = if (type == ViewType.EMPTY) View.VISIBLE else View.GONE
        txtRecycler.visibility = if (type == ViewType.TXT || type == ViewType.EPUB) View.VISIBLE else View.GONE
        val hasBook = type != ViewType.EMPTY
        barsActive = hasBook
        if (hasBook) {
            // Assure que la toolbar est visible avant l'animation (GONE → VISIBLE)
            toolbar.visibility = View.VISIBLE; toolbar.alpha = 1f
            bottomBar.visibility = View.VISIBLE; bottomBar.alpha = 1f
            bottomDivider.visibility = View.VISIBLE; bottomDivider.alpha = 1f
            barsVisible = true
            showBarsTemporarily()
        } else {
            hideHandler.removeCallbacks(hideRunnable)
            barsActive = false; barsVisible = true
            toolbar.visibility = View.VISIBLE; toolbar.alpha = 1f
            bottomBar.visibility = View.GONE
            bottomDivider.visibility = View.GONE
        }
        val ttsVisible = if (type == ViewType.TXT || type == ViewType.EPUB) View.VISIBLE else View.INVISIBLE
        playPauseBtn.visibility = ttsVisible
        speedBtn.visibility = ttsVisible
        pitchBtn.visibility = ttsVisible
        voiceBtn.visibility = ttsVisible
    }

    // ── Adaptateur paragraphes + images ──────────────────────────────────────

    inner class ParagraphAdapter(
        private val items: List<EpubItem>,
        private var theme: ReadingTheme,
        private var useDistinction: Boolean,
        private var typeface: Typeface = Typeface.DEFAULT,
        private var textSizeSp: Float = DEFAULT_FONT_SIZE
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val textItemIndices: List<Int> = items.mapIndexedNotNull { i, it ->
            if (it is EpubItem.Paragraph || it is EpubItem.Heading) i else null
        }
        private val itemToParagraphIdx: Map<Int, Int> = buildMap {
            textItemIndices.forEachIndexed { paraIdx, itemIdx -> put(itemIdx, paraIdx) }
        }
        val paragraphs: List<String> = items.mapNotNull { when (it) {
            is EpubItem.Paragraph -> it.text
            is EpubItem.Heading  -> it.text
            else -> null
        } }

        private var readingParagraph = -1  // paragraph index
        private var cursorParagraph = -1   // paragraph index
        private var highlightStart = -1
        private var highlightEnd = -1
        private var focusedParagraph = -1
        private var focusedLine = -1
        private var bookmarkedItems = setOf<Int>() // item indices

        private val TYPE_TEXT = 0; private val TYPE_IMAGE = 1; private val TYPE_CHAPTER = 2

        // Images déjà décodées : un petit cache, le reste du livre reste sur disque
        private val imageCache = object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt()
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

        inner class TextHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)
        inner class ImageHolder(val iv: ImageView) : RecyclerView.ViewHolder(iv) {
            var path: String? = null
            var job: Job? = null
        }
        inner class ChapterHolder(root: FrameLayout, val line: View) : RecyclerView.ViewHolder(root)

        override fun getItemViewType(position: Int) = when (items[position]) {
            is EpubItem.Image -> TYPE_IMAGE
            is EpubItem.ChapterBreak -> TYPE_CHAPTER
            else -> TYPE_TEXT
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_IMAGE) {
                val iv = ImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                return ImageHolder(iv)
            }
            if (viewType == TYPE_CHAPTER) {
                val container = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88))
                }
                val line = View(parent.context).apply {
                    val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1), Gravity.CENTER_VERTICAL)
                    lp.marginStart = dp(48); lp.marginEnd = dp(48)
                    layoutParams = lp
                }
                container.addView(line)
                return ChapterHolder(container, line)
            }
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setLineSpacing(0f, 1.6f)
                setTextIsSelectable(true)
            }
            val holder = TextHolder(tv)
            val gd = GestureDetector(parent.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val itemPos = holder.bindingAdapterPosition
                    if (itemPos == RecyclerView.NO_POSITION) return false
                    val paraIdx = itemToParagraphIdx[itemPos] ?: return false
                    ttsCurrentParagraph = paraIdx
                    pausedCharOffset = 0
                    val firstWordStart = prepareParagraphForTts(paraIdx)
                    showBarsTemporarily()
                    txtRecycler.post { focusHighlight(paraIdx, firstWordStart) }
                    if (ttsState == TtsState.PLAYING) speakParagraph(paraIdx)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    val itemPos = holder.bindingAdapterPosition
                    if (itemPos == RecyclerView.NO_POSITION) return
                    val text = when (val item = items[itemPos]) {
                        is EpubItem.Paragraph -> item.text
                        is EpubItem.Heading -> item.text
                        else -> return
                    }
                    toggleBookmark(itemPos, text)
                }
            })
            tv.setOnTouchListener { _, event -> gd.onTouchEvent(event); false }
            return holder
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is EpubItem.ChapterBreak -> {
                    val h = holder as ChapterHolder
                    val lineColor = if (android.graphics.Color.luminance(theme.bg1) < 0.15f) 0x44FFFFFF.toInt() else 0x44000000.toInt()
                    h.itemView.setBackgroundColor(theme.bg1)
                    h.line.setBackgroundColor(lineColor)
                }
                is EpubItem.Image -> bindImage(holder as ImageHolder, item)
                is EpubItem.Heading -> {
                    val h = holder as TextHolder
                    val paraIdx = itemToParagraphIdx[position] ?: 0
                    val isBookmarked = bookmarkedItems.contains(position)
                    val scale = when (item.level) { 1 -> 1.75f; 2 -> 1.45f; 3 -> 1.25f; 4 -> 1.12f; else -> 1.05f }
                    val topPad = when (item.level) { 1 -> dp(20); 2 -> dp(16); else -> dp(12) }
                    h.tv.setPadding(dp(16), topPad, dp(16), dp(6))
                    h.tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp * scale)
                    h.tv.typeface = Typeface.create(typeface, Typeface.BOLD)
                    h.tv.setTextColor(theme.textColor)
                    val readingItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
                    val cursorItemIdx = textItemIndices.getOrElse(cursorParagraph) { -1 }
                    when {
                        position == readingItemIdx -> {
                            val activeBg = if (theme.paletteColors.isNotEmpty()) theme.bg1 else theme.readingBg
                            h.tv.setBackgroundColor(activeBg)
                            applyWordHighlight(h.tv, item.text, highlightStart, highlightEnd, paraIdx)
                        }
                        position == cursorItemIdx -> {
                            applyBookmarkPrefix(h.tv, item.text, isBookmarked)
                            val cursorBg = if (theme.paletteColors.isNotEmpty()) theme.bg1
                                else ColorUtils.blendARGB(theme.bg1, theme.readingBg, 0.35f)
                            h.tv.setBackgroundColor(cursorBg)
                        }
                        else -> {
                            applyBookmarkPrefix(h.tv, item.text, isBookmarked)
                            h.tv.setBackgroundColor(theme.bg1)
                        }
                    }
                }
                is EpubItem.Paragraph -> {
                    val h = holder as TextHolder
                    val paraIdx = itemToParagraphIdx[position] ?: 0
                    val isBookmarked = bookmarkedItems.contains(position)
                    h.tv.setPadding(dp(16), dp(12), dp(16), dp(12))
                    h.tv.typeface = typeface
                    h.tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                    h.tv.setTextColor(theme.textColor)
                    val readingItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
                    val cursorItemIdx = textItemIndices.getOrElse(cursorParagraph) { -1 }
                    when {
                        position == readingItemIdx -> {
                            h.tv.setBackgroundColor(theme.activeParagraphBg(paraIdx, useDistinction))
                            applyWordHighlight(h.tv, item.text, highlightStart, highlightEnd, paraIdx)
                        }
                        position == cursorItemIdx -> {
                            applyBookmarkPrefix(h.tv, item.text, isBookmarked)
                            val base = if (useDistinction) theme.paragraphBg(paraIdx) else theme.bg1
                            val cursorBg = if (theme.paletteColors.isNotEmpty()) base
                                else ColorUtils.blendARGB(base, theme.readingBg, 0.35f)
                            h.tv.setBackgroundColor(cursorBg)
                        }
                        else -> {
                            applyBookmarkPrefix(h.tv, item.text, isBookmarked)
                            h.tv.setBackgroundColor(if (useDistinction) theme.paragraphBg(paraIdx) else theme.bg1)
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            (holder as? ImageHolder)?.let { it.job?.cancel(); it.job = null }
        }

        /** Image décodée à la demande, sur un fil de fond, à la largeur de la liste. */
        private fun bindImage(holder: ImageHolder, item: EpubItem.Image) {
            val iv = holder.iv
            holder.job?.cancel()
            holder.path = item.path
            imageCache.get(item.path)?.let {
                iv.minimumHeight = 0
                iv.setImageBitmap(it)
                return
            }
            iv.setImageDrawable(null)
            val listWidth = txtRecycler.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val targetWidth = (listWidth - iv.paddingLeft - iv.paddingRight).coerceAtLeast(1)
            // Hauteur réservée d'après les dimensions lues à l'ouverture : le texte ne saute pas
            // quand l'image arrive
            val shownWidth = minOf(item.width, targetWidth)
            iv.minimumHeight = shownWidth * item.height / item.width + iv.paddingTop + iv.paddingBottom
            val zip = epubZip ?: return
            holder.job = scope.launch {
                val bmp = withContext(epubImageDispatcher) { decodeEpubImage(zip, item, targetWidth) } ?: return@launch
                imageCache.put(item.path, bmp)
                if (holder.path == item.path) {
                    iv.minimumHeight = 0
                    iv.setImageBitmap(bmp)
                }
            }
        }

        private fun applyWordHighlight(tv: TextView, text: String, start: Int, end: Int, paraIdx: Int) {
            if (start >= 0 && end > start && end <= text.length) {
                val current = tv.text
                val s = if (current is Spannable && current.toString() == text) {
                    current
                } else {
                    SpannableString(text)
                }
                s.getSpans(0, s.length, BackgroundColorSpan::class.java)
                    .forEach(s::removeSpan)
                s.setSpan(BackgroundColorSpan(theme.wordHighlightBg(paraIdx)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (tv.text !== s) tv.setText(s, TextView.BufferType.SPANNABLE)
                else tv.invalidate()
            } else { tv.text = text }
        }

        fun itemIndexForParagraph(paraIdx: Int): Int = textItemIndices.getOrElse(paraIdx) { paraIdx }

        // Recherche dichotomique : appelée à chaque image de défilement sur des milliers de paragraphes
        fun paragraphIndexAtOrAfter(itemPos: Int): Int {
            val found = textItemIndices.binarySearch(itemPos)
            val idx = if (found >= 0) found else -(found + 1)
            return idx.coerceAtMost(textItemIndices.lastIndex).coerceAtLeast(0)
        }

        fun setCursorParagraph(idx: Int) {
            val prevItemIdx = textItemIndices.getOrElse(cursorParagraph) { -1 }
            cursorParagraph = idx
            val itemIdx = textItemIndices.getOrElse(idx) { -1 }
            val readingItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
            if (prevItemIdx >= 0 && prevItemIdx != readingItemIdx) notifyItemChanged(prevItemIdx)
            if (itemIdx >= 0 && itemIdx != readingItemIdx) notifyItemChanged(itemIdx)
        }

        fun prepareParagraphForTts(idx: Int): Int {
            val text = paragraphs.getOrNull(idx) ?: return 0
            val firstWord = FIRST_WORD_REGEX.find(text)
            val start = firstWord?.range?.first ?: 0
            val end = firstWord?.let { it.range.last + 1 } ?: text.length.coerceAtMost(1)
            val prevItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
            val itemIdx = textItemIndices.getOrElse(idx) { -1 }

            readingParagraph = idx
            cursorParagraph = idx
            highlightStart = start
            highlightEnd = end
            focusedParagraph = -1
            focusedLine = -1

            if (prevItemIdx >= 0 && prevItemIdx != itemIdx) notifyItemChanged(prevItemIdx)
            if (itemIdx >= 0) notifyItemChanged(itemIdx)
            return start
        }

        fun setReadingParagraph(idx: Int) {
            val prevItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
            readingParagraph = idx; cursorParagraph = idx; highlightStart = -1; highlightEnd = -1
            if (prevItemIdx >= 0) notifyItemChanged(prevItemIdx)
            val itemIdx = textItemIndices.getOrElse(idx) { -1 }
            if (itemIdx >= 0) notifyItemChanged(itemIdx)
        }

        fun highlightWord(paragraphIndex: Int, start: Int, end: Int) {
            if (paragraphIndex != readingParagraph) return
            highlightStart = start; highlightEnd = end
            val itemIdx = textItemIndices.getOrElse(paragraphIndex) { -1 }
            if (itemIdx < 0) return
            val vh = this@BookReaderActivity.txtRecycler
                .findViewHolderForAdapterPosition(itemIdx) as? TextHolder
            if (vh != null) {
                val activeBg = if (items[itemIdx] is EpubItem.Heading && theme.paletteColors.isNotEmpty()) {
                    theme.bg1
                } else {
                    theme.activeParagraphBg(paragraphIndex, useDistinction)
                }
                vh.tv.setBackgroundColor(activeBg)
                applyWordHighlight(vh.tv, paragraphs[paragraphIndex], start, end, paragraphIndex)
            }
        }

        fun focusHighlight(paragraphIndex: Int, charOffset: Int) {
            val itemIdx = textItemIndices.getOrElse(paragraphIndex) { -1 }
            if (itemIdx < 0) return
            val recycler = this@BookReaderActivity.txtRecycler
            val holder = recycler.findViewHolderForAdapterPosition(itemIdx) as? TextHolder
            if (holder == null) {
                // Le défilement de paragraphe lancé par onStart est déjà animé. Ne pas
                // le remplacer par un repositionnement instantané qui créerait un à-coup.
                recycler.smoothScrollToPosition(itemIdx)
                return
            }
            val layout = holder.tv.layout ?: return
            val safeOffset = charOffset.coerceIn(0, (holder.tv.text.length - 1).coerceAtLeast(0))
            val line = layout.getLineForOffset(safeOffset)
            if (focusedParagraph == paragraphIndex && focusedLine == line) return
            focusedParagraph = paragraphIndex
            focusedLine = line

            val lineCenter = holder.tv.top + holder.tv.totalPaddingTop +
                (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
            val viewportTop = recycler.paddingTop
            val viewportHeight = recycler.height - recycler.paddingTop - recycler.paddingBottom
            val comfortTop = viewportTop + viewportHeight * 28 / 100
            val comfortBottom = viewportTop + viewportHeight * 72 / 100

            // Tant que la ligne lue reste dans la zone centrale, l'écran ne bouge pas.
            // Quand elle en sort, un petit rattrapage animé lui redonne de l'avance.
            val targetY = when {
                lineCenter > comfortBottom -> viewportTop + viewportHeight * 58 / 100
                lineCenter < comfortTop -> viewportTop + viewportHeight * 42 / 100
                else -> return
            }
            recycler.smoothScrollBy(0, lineCenter - targetY)
        }

        fun clearHighlight() {
            val prevItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
            readingParagraph = -1; highlightStart = -1; highlightEnd = -1
            focusedParagraph = -1; focusedLine = -1
            if (prevItemIdx >= 0) notifyItemChanged(prevItemIdx)
        }

        fun updateTheme(newTheme: ReadingTheme) {
            theme = newTheme; notifyItemRangeChanged(0, items.size)
        }

        fun getCurrentHighlightStart(): Int = highlightStart.coerceAtLeast(0)

        fun setDistinction(enabled: Boolean) {
            useDistinction = enabled; notifyItemRangeChanged(0, items.size)
        }

        fun updateFont(tf: Typeface, sizeSp: Float) {
            typeface = tf; textSizeSp = sizeSp; notifyItemRangeChanged(0, items.size)
        }

        fun setBookmarks(indices: Set<Int>) {
            bookmarkedItems = indices.toSet()
            notifyItemRangeChanged(0, items.size)
        }

        private fun applyBookmarkPrefix(tv: TextView, text: String, isBookmarked: Boolean) {
            if (!isBookmarked) { tv.text = text; return }
            val full = getString(R.string.book_reader_bookmark_item, text)
            val s = SpannableString(full)
            s.setSpan(
                android.text.style.ForegroundColorSpan(0xFFFFC107.toInt()),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            tv.text = s
        }
    }

}

private val FIRST_WORD_REGEX = Regex("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}'’\\-]*")

private val HTML_NAMED_ENTITIES: Map<String, String> = mapOf(
    // Base
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    // Typographie courante
    "rsquo" to "’", "lsquo" to "‘", "sbquo" to "‚",
    "rdquo" to "”", "ldquo" to "“", "bdquo" to "„",
    "mdash" to "—", "ndash" to "–", "shy" to "­",
    "hellip" to "…", "middot" to "·", "bull" to "•",
    "dagger" to "†", "Dagger" to "‡", "permil" to "‰",
    "prime" to "′", "Prime" to "″",
    "laquo" to "«", "raquo" to "»",
    "lsaquo" to "‹", "rsaquo" to "›",
    "thinsp" to " ", "ensp" to " ", "emsp" to " ", "zwj" to "‍",
    // Symboles
    "trade" to "™", "copy" to "©", "reg" to "®",
    "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢",
    "deg" to "°", "plusmn" to "±", "times" to "×", "divide" to "÷",
    "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
    "sup1" to "¹", "sup2" to "²", "sup3" to "³",
    "micro" to "µ", "para" to "¶", "sect" to "§",
    "iexcl" to "¡", "iquest" to "¿", "ordf" to "ª", "ordm" to "º",
    "macr" to "¯", "acute" to "´", "cedil" to "¸", "uml" to "¨",
    "not" to "¬", "brvbar" to "¦",
    // Lettres accentuées minuscules
    "agrave" to "à", "aacute" to "á", "acirc" to "â", "atilde" to "ã",
    "auml" to "ä", "aring" to "å", "aelig" to "æ", "ccedil" to "ç",
    "egrave" to "è", "eacute" to "é", "ecirc" to "ê", "euml" to "ë",
    "igrave" to "ì", "iacute" to "í", "icirc" to "î", "iuml" to "ï",
    "eth" to "ð", "ntilde" to "ñ",
    "ograve" to "ò", "oacute" to "ó", "ocirc" to "ô", "otilde" to "õ",
    "ouml" to "ö", "oslash" to "ø",
    "ugrave" to "ù", "uacute" to "ú", "ucirc" to "û", "uuml" to "ü",
    "yacute" to "ý", "thorn" to "þ", "yuml" to "ÿ",
    // Lettres accentuées majuscules
    "Agrave" to "À", "Aacute" to "Á", "Acirc" to "Â", "Atilde" to "Ã",
    "Auml" to "Ä", "Aring" to "Å", "AElig" to "Æ", "Ccedil" to "Ç",
    "Egrave" to "È", "Eacute" to "É", "Ecirc" to "Ê", "Euml" to "Ë",
    "Igrave" to "Ì", "Iacute" to "Í", "Icirc" to "Î", "Iuml" to "Ï",
    "ETH" to "Ð", "Ntilde" to "Ñ",
    "Ograve" to "Ò", "Oacute" to "Ó", "Ocirc" to "Ô", "Otilde" to "Õ",
    "Ouml" to "Ö", "Oslash" to "Ø",
    "Ugrave" to "Ù", "Uacute" to "Ú", "Ucirc" to "Û", "Uuml" to "Ü",
    "Yacute" to "Ý", "THORN" to "Þ",
    // Latin étendu (alphabet West European + ligatures)
    "OElig" to "Œ", "oelig" to "œ", "Scaron" to "Š", "scaron" to "š",
    "Yuml" to "Ÿ", "fnof" to "ƒ", "circ" to "ˆ", "tilde" to "˜",
    // Flèches
    "larr" to "←", "uarr" to "↑", "rarr" to "→", "darr" to "↓", "harr" to "↔",
    // Maths courants
    "minus" to "−", "lowast" to "∗", "radic" to "√",
    "infin" to "∞", "ne" to "≠", "le" to "≤", "ge" to "≥",
    "sim" to "∼", "asymp" to "≈", "equiv" to "≡",
    "sum" to "∑", "prod" to "∏", "int" to "∫",
    "forall" to "∀", "exist" to "∃", "empty" to "∅",
    "and" to "∧", "or" to "∨", "cap" to "∩", "cup" to "∪",
    "isin" to "∈", "notin" to "∉", "sub" to "⊂", "sup" to "⊃",
    "sube" to "⊆", "supe" to "⊇",
    "oplus" to "⊕", "otimes" to "⊗", "perp" to "⊥",
    // Divers
    "spades" to "♠", "clubs" to "♣", "hearts" to "♥", "diams" to "♦"
)
