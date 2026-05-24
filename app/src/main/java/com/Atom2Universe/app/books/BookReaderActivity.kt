package com.Atom2Universe.app.books

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
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
import android.widget.PopupWindow
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
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import android.graphics.BitmapFactory
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import androidx.core.content.edit

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
        private const val MENU_THEME = 2
        private const val MENU_FONT = 3
        private const val MENU_BOOKMARKS = 4
        private const val DEFAULT_FONT_SIZE = 16f
        private val AVAILABLE_FONTS = listOf(
            "alamain1.ttf" to "Alamain",
            "Chomsky.otf" to "Chomsky",
            "Coffee Spark.otf" to "Coffee Spark",
            "Creativo Regular.otf" to "Creativo",
            "Designer-Notes.ttf" to "Designer Notes",
            "Little days.ttf" to "Little Days",
            "Orbitron-Regular.ttf" to "Orbitron",
            "Roboto-Condensed.ttf" to "Roboto Condensed",
            "ScienceGothic_Expanded-Regular.ttf" to "Science Gothic",
            "Slow Play.otf" to "Slow Play",
            "To Japan.otf" to "To Japan"
        )
    }

    private data class OpfData(val spineIds: List<String>, val manifest: Map<String, String>, val author: String, val coverItemId: String?)
    sealed class EpubItem {
        data class Paragraph(val text: String) : EpubItem()
        data class Heading(val text: String, val level: Int) : EpubItem()
        class Image(val bitmap: Bitmap) : EpubItem()
        object ChapterBreak : EpubItem()
    }
    private data class EpubContent(val items: List<EpubItem>, val author: String, val coverPath: String?) {
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
        val paletteColors: IntArray = intArrayOf()
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
            )
        );

        fun paragraphBg(paraIdx: Int): Int =
            if (paletteColors.isNotEmpty()) paletteColors[paraIdx % paletteColors.size]
            else if (paraIdx % 2 != 0) bg2 else bg1
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
    private var speakOffset = 0
    private var pausedCharOffset = 0


    // ── Vues ──────────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: SharedPreferences
    private var currentTheme = ReadingTheme.DARK
    private var paragraphDistinction = true

    private lateinit var toolbar: MaterialToolbar
    private lateinit var contentContainer: FrameLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var pdfRecycler: RecyclerView
    private lateinit var txtRecycler: RecyclerView
    private lateinit var bottomBar: View
    private lateinit var bottomDivider: View
    private lateinit var pageIndicator: TextView
    private lateinit var playPauseBtn: TextView
    private lateinit var speedBtn: TextView
    private lateinit var pitchBtn: TextView
    private lateinit var voiceBtn: TextView

    private var pdfRenderer: PdfRenderer? = null
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var currentUri: Uri? = null
    private var currentViewType = ViewType.EMPTY
    private var paragraphAdapter: ParagraphAdapter? = null
    private var currentFileSize = 0L

    private var currentFontName = "default"
    private var currentFontSize = DEFAULT_FONT_SIZE
    private var currentTypeface: Typeface = Typeface.DEFAULT
    private val typefaceCache = mutableMapOf<String, Typeface>()

    // ── Bookmarks ─────────────────────────────────────────────────────────────
    private val bookmarkData = mutableMapOf<Int, String>() // itemIndex → text snippet

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

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentTheme = ReadingTheme.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ReadingTheme.DARK }
        paragraphDistinction = prefs.getBoolean(KEY_DISTINCTION, true)
        ttsSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        ttsPitch = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        selectedVoiceName = prefs.getString(KEY_TTS_VOICE, null)
        currentFontName = prefs.getString(KEY_FONT_NAME, "default") ?: "default"
        currentFontSize = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        currentTypeface = loadTypeface(currentFontName)

        toolbar = findViewById(R.id.toolbar)
        contentContainer = findViewById(R.id.content_container)
        emptyState = findViewById(R.id.empty_state)
        pdfRecycler = findViewById(R.id.pdf_recycler)
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
        startAutoSave()
        attachBarRevealListener(txtRecycler)
        attachBarRevealListener(pdfRecycler)
        // Toucher la toolbar ou la barre basse remet le compteur à zéro
        toolbar.setOnTouchListener { _, _ -> showBarsTemporarily(); false }
        bottomBar.setOnTouchListener { _, _ -> showBarsTemporarily(); false }

        val intentUri = intent.getStringExtra(EXTRA_BOOK_URI)?.let { Uri.parse(it) }
            ?: intent.data
            ?: prefs.getString(KEY_CURRENT_URI, null)?.let { Uri.parse(it) }
        if (intentUri != null) {
            try { openBook(intentUri) } catch (_: Exception) { showEmpty() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (barsActive) showBarsTemporarily()
    }

    override fun onStop() {
        super.onStop()
        hideHandler.removeCallbacks(hideRunnable)
        savePosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdf()
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
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
        val labels = sorted.map { "★  ${it.value}" }.toTypedArray()
        AlertDialog.Builder(this)
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

        val dialog = AlertDialog.Builder(this).setTitle(R.string.book_reader_theme).setView(root).create()

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
                paragraphAdapter?.updateTheme(currentTheme)
                if (currentViewType == ViewType.EPUB) currentUri?.let { loadEpub(it) }
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
        AVAILABLE_FONTS.forEachIndexed { i, (file, label) ->
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
                val (file, _) = AVAILABLE_FONTS[checkedId - 1]
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

        AlertDialog.Builder(this)
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
                        val idx = utteranceId.toIntOrNull() ?: return
                        runOnUiThread {
                            paragraphAdapter?.setReadingParagraph(idx)
                            autoScrollToReadingParagraph(idx)
                        }
                    }
                    override fun onDone(utteranceId: String) {
                        val idx = utteranceId.toIntOrNull() ?: return
                        if (ttsState == TtsState.PLAYING) {
                            runOnUiThread { speakParagraph(idx + 1) }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) { runOnUiThread { stopReading() } }
                    override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                        val idx = utteranceId.toIntOrNull() ?: return
                        runOnUiThread { paragraphAdapter?.highlightWord(idx, start + speakOffset, end + speakOffset) }
                    }
                })
            }
        }
    }

    private fun setupTtsControls() {
        speedBtn.text = "%.1f×".format(ttsSpeed)
        pitchBtn.text = "%.1f♪".format(ttsPitch)

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
            showSliderPopup(speedBtn, ttsSpeed, 0.5f, 2.0f, 0.1f, "×") { v ->
                ttsSpeed = v
                speedBtn.text = "%.1f×".format(v)
                prefs.edit { putFloat(KEY_TTS_SPEED, v) }
                tts?.setSpeechRate(v)
                if (ttsState == TtsState.PLAYING) restartCurrentParagraph()
            }
        }

        pitchBtn.setOnClickListener {
            showSliderPopup(pitchBtn, ttsPitch, 0.5f, 2.0f, 0.1f, "♪") { v ->
                ttsPitch = v
                pitchBtn.text = "%.1f♪".format(v)
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
            val network = if (v.isNetworkConnectionRequired) "  ☁" else ""
            "${i + 1}.  $lang · $code$network"
        }

        val currentIdx = voices.indexOfFirst { it.name == selectedVoiceName }

        AlertDialog.Builder(this)
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
        speakOffset = 0
        tts?.stop()
        tts?.speak(paragraphs.getOrNull(ttsCurrentParagraph) ?: return,
            TextToSpeech.QUEUE_FLUSH, null, ttsCurrentParagraph.toString())
    }

    private fun showSliderPopup(
        anchor: View, current: Float, min: Float, max: Float, step: Float, suffix: String,
        onRelease: (Float) -> Unit
    ) {
        val steps = Math.round((max - min) / step)
        val popupWidth = dp(220)

        val valueLabel = TextView(this).apply {
            text = "%.1f$suffix".format(current)
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
                valueLabel.text = "%.1f$suffix".format((min + progress * step).coerceIn(min, max))
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
        ttsCurrentParagraph = idx
        speakOffset = 0
        tts?.speak(paragraphs[idx], TextToSpeech.QUEUE_FLUSH, null, idx.toString())
    }

    private fun pauseReading() {
        tts?.stop()
        pausedCharOffset = (paragraphAdapter?.getCurrentHighlightStart() ?: 0).coerceAtLeast(0)
        ttsState = TtsState.PAUSED
        updatePlayPauseBtn()
    }

    private fun resumeReading() {
        ttsState = TtsState.PLAYING
        updatePlayPauseBtn()
        speakOffset = pausedCharOffset
        val fullText = paragraphs.getOrNull(ttsCurrentParagraph) ?: run { stopReading(); return }
        val text = if (pausedCharOffset > 0 && pausedCharOffset < fullText.length)
            fullText.substring(pausedCharOffset) else fullText
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, ttsCurrentParagraph.toString())
    }

    private fun stopReading() {
        tts?.stop()
        ttsState = TtsState.IDLE
        speakOffset = 0
        pausedCharOffset = 0
        updatePlayPauseBtn()
        paragraphAdapter?.clearHighlight()
    }

    private fun updatePlayPauseBtn() {
        playPauseBtn.text = when (ttsState) {
            TtsState.PLAYING -> "⏸"
            else -> "▶"
        }
    }

    private fun autoScrollToReadingParagraph(idx: Int) {
        val lm = txtRecycler.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        val itemIdx = paragraphAdapter?.itemIndexForParagraph(idx) ?: idx
        if (itemIdx < first || itemIdx > last) txtRecycler.smoothScrollToPosition(itemIdx)
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
        val title = fileName?.substringBeforeLast('.') ?: getString(R.string.book_reader_title)
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
            isPdf(mimeType, fileName) -> loadPdf(uri)
            else -> loadTxt(uri)
        }
    }

    private fun isEpub(mime: String?, name: String?) =
        mime?.contains("epub") == true || name?.endsWith(".epub", true) == true

    private fun isPdf(mime: String?, name: String?) =
        mime?.contains("pdf") == true || name?.endsWith(".pdf", true) == true

    // ── TXT ──────────────────────────────────────────────────────────────────

    private fun loadTxt(uri: Uri) {
        showView(ViewType.TXT)
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { readTextWithCharsetDetection(uri) }.getOrElse { "" }
            }
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
            val items = paragraphs.map { EpubItem.Paragraph(it) }
            val adapter = ParagraphAdapter(items, currentTheme, paragraphDistinction, currentTypeface, currentFontSize)
            paragraphAdapter = adapter
            adapter.setBookmarks(bookmarkData.keys.toSet())
            val lm = LinearLayoutManager(this@BookReaderActivity)
            txtRecycler.layoutManager = lm
            txtRecycler.clearOnScrollListeners()
            txtRecycler.adapter = adapter
            pageIndicator.text = getString(R.string.book_reader_page_of, 1, paragraphs.size)
            txtRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val first = lm.findFirstVisibleItemPosition().coerceAtLeast(0) + 1
                    pageIndicator.text = getString(R.string.book_reader_page_of, first, paragraphs.size)
                }
            })
            restoreTxtPosition(uri)
        }
    }

    private fun splitParagraphs(text: String): List<String> =
        text.split(Regex("\r?\n"))
            .map { it.trim() }.filter { it.length > 3 }

    // ── PDF ──────────────────────────────────────────────────────────────────

    private fun loadPdf(uri: Uri) {
        closePdf()
        runCatching {
            pdfDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return showEmpty()
            pdfRenderer = PdfRenderer(pdfDescriptor!!)
        }.onFailure {
            showEmpty()
            Toast.makeText(this, R.string.book_reader_error, Toast.LENGTH_SHORT).show()
            return
        }
        val count = pdfRenderer!!.pageCount
        BookLibraryActivity.updateOrAddBook(prefs, BookEntry(
            uri = uri.toString(),
            title = toolbar.title?.toString() ?: getString(R.string.book_reader_title),
            author = "", coverPath = null, totalItems = count, lastReadItem = 0,
            format = "PDF", addedAt = System.currentTimeMillis(), lastOpenedAt = System.currentTimeMillis(),
            fileSize = currentFileSize
        ))
        showView(ViewType.PDF)
        val lm = LinearLayoutManager(this)
        pdfRecycler.layoutManager = lm
        pdfRecycler.adapter = PdfPagesAdapter(pdfRenderer!!)
        pageIndicator.text = getString(R.string.book_reader_page_of, 1, count)
        pdfRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val page = lm.findFirstVisibleItemPosition() + 1
                pageIndicator.text = getString(R.string.book_reader_page_of, page, count)
            }
        })
        restorePdfPosition(uri)
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private fun loadEpub(uri: Uri) {
        showView(ViewType.EPUB)
        scope.launch {
            val content = withContext(Dispatchers.IO) { extractEpubContent(uri) }
            if (content.paragraphs.isEmpty()) {
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
            val adapter = ParagraphAdapter(content.items, currentTheme, paragraphDistinction, currentTypeface, currentFontSize)
            paragraphAdapter = adapter
            adapter.setBookmarks(bookmarkData.keys.toSet())
            val lm = LinearLayoutManager(this@BookReaderActivity)
            txtRecycler.layoutManager = lm
            txtRecycler.clearOnScrollListeners()
            txtRecycler.adapter = adapter
            pageIndicator.text = getString(R.string.book_reader_page_of, 1, paragraphs.size)
            txtRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val first = lm.findFirstVisibleItemPosition().coerceAtLeast(0) + 1
                    pageIndicator.text = getString(R.string.book_reader_page_of, first, paragraphs.size)
                }
            })
            restoreTxtPosition(uri)
        }
    }

    private fun extractEpubContent(uri: Uri): EpubContent {
        val files = mutableMapOf<String, ByteArray>()
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) files[entry.name] = zip.readBytes()
                        entry = zip.nextEntry
                    }
                }
            }
        }
        val containerBytes = files["META-INF/container.xml"]
            ?: return EpubContent(fallbackEpubParagraphs(files).map { EpubItem.Paragraph(it) }, "", null)
        val opfPath = parseContainerXml(containerBytes)
            ?: return EpubContent(fallbackEpubParagraphs(files).map { EpubItem.Paragraph(it) }, "", null)
        val opfBytes = files[opfPath]
            ?: return EpubContent(fallbackEpubParagraphs(files).map { EpubItem.Paragraph(it) }, "", null)
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = parseOpf(opfBytes)
        val result = mutableListOf<EpubItem>()
        var firstChapter = true
        for (id in opf.spineIds) {
            val href = opf.manifest[id] ?: continue
            val path = if (opfDir.isEmpty()) href else "$opfDir/$href"
            val bytes = files[path] ?: files[href] ?: continue
            val htmlDir = path.substringBeforeLast('/', "")
            val sizeBefore = result.size
            extractContentFromHtml(bytes.toString(Charsets.UTF_8), result, files, htmlDir)
            if (result.size > sizeBefore) {
                if (!firstChapter) result.add(sizeBefore, EpubItem.ChapterBreak)
                firstChapter = false
            }
        }
        val items = if (result.isEmpty())
            fallbackEpubParagraphs(files).map { EpubItem.Paragraph(it) }
        else result
        val coverPath = opf.coverItemId?.let { coverId ->
            val href = opf.manifest[coverId] ?: return@let null
            val path = if (opfDir.isEmpty()) href else "$opfDir/$href"
            val bytes = files[path] ?: files[href] ?: return@let null
            saveCoverBytes(bytes, uri)
        }
        return EpubContent(items, opf.author, coverPath)
    }

    private fun fallbackEpubParagraphs(files: Map<String, ByteArray>): List<String> {
        val html = files.entries
            .firstOrNull { it.key.endsWith(".html", true) || it.key.endsWith(".xhtml", true) }
            ?.value?.toString(Charsets.UTF_8) ?: return emptyList()
        return mutableListOf<String>().also { extractParagraphsFromHtml(html, it) }
    }

    private fun extractParagraphsFromHtml(html: String, out: MutableList<String>) {
        val body = run {
            val s = html.indexOf("<body", ignoreCase = true).takeIf { it >= 0 } ?: return
            val e = html.lastIndexOf("</body>", ignoreCase = true).takeIf { it > s } ?: return
            html.substring(s, e + 7)
        }
        val tagRe = Regex("<(p|h[1-6]|div)[^>]*>(.*?)</(p|h[1-6]|div)>", RegexOption.DOT_MATCHES_ALL)
        var found = false
        for (m in tagRe.findAll(body)) {
            val text = decodeHtmlEntities(m.groupValues[2].replace(Regex("<[^>]+>"), ""))
                .replace(Regex("\\s+"), " ").trim()
            if (text.length > 5) { out.add(text); found = true }
        }
        if (!found) {
            val stripped = body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), "\n\n").trim()
            out.addAll(splitParagraphs(stripped))
        }
    }

    private fun extractContentFromHtml(
        html: String,
        out: MutableList<EpubItem>,
        files: Map<String, ByteArray>,
        htmlDir: String
    ) {
        val bodyStart = html.indexOf("<body", ignoreCase = true).takeIf { it >= 0 } ?: return
        val bodyEnd = html.lastIndexOf("</body>", ignoreCase = true).takeIf { it > bodyStart } ?: return
        val body = html.substring(bodyStart, bodyEnd + 7)

        // Regex matches: block elements (p/h1-6/figure/div) OR standalone img tags
        val elemRe = Regex(
            """<(p|h[1-6]|figure|div)\b[^>]*>(.*?)</\1>|<img\b[^>]*?>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val imgRe = Regex("""<img\b[^>]*?>""", RegexOption.IGNORE_CASE)
        var found = false
        for (m in elemRe.findAll(body)) {
            val full = m.value
            if (full.startsWith("<img", ignoreCase = true)) {
                resolveAndDecodeImg(full, files, htmlDir)?.let { out.add(EpubItem.Image(it)); found = true }
            } else {
                val tagName = m.groupValues[1].lowercase()
                val inner = m.groupValues[2]
                // Nested images inside block elements
                for (imgM in imgRe.findAll(inner)) {
                    resolveAndDecodeImg(imgM.value, files, htmlDir)?.let { out.add(EpubItem.Image(it)); found = true }
                }
                // Heading tags → single Heading item (bold, sized by level)
                if (tagName.length == 2 && tagName[0] == 'h' && tagName[1].isDigit()) {
                    val level = tagName[1].digitToInt()
                    val text = decodeHtmlEntities(inner.replace(Regex("<[^>]+>"), ""))
                        .replace(Regex("\\s+"), " ").trim()
                    if (text.isNotEmpty()) { out.add(EpubItem.Heading(text, level)); found = true }
                } else if (tagName == "p") {
                    // <p> : <br> = saut typographique, garder en un seul paragraphe
                    val text = decodeHtmlEntities(
                        inner.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
                             .replace(Regex("<[^>]+>"), "")
                    ).replace(Regex("\\s+"), " ").trim()
                    if (text.length > 3) { out.add(EpubItem.Paragraph(text)); found = true }
                } else {
                    // div/figure : <br> = séparateur de lignes
                    val withBreaks = inner.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    val raw = decodeHtmlEntities(withBreaks.replace(Regex("<[^>]+>"), ""))
                    val lines = raw.split("\n")
                        .map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length > 3 }
                    if (lines.isNotEmpty()) { lines.forEach { out.add(EpubItem.Paragraph(it)) }; found = true }
                }
            }
        }
        if (!found) {
            val stripped = body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), "\n\n").trim()
            splitParagraphs(stripped).forEach { out.add(EpubItem.Paragraph(it)) }
        }
    }

    private fun resolveAndDecodeImg(imgTag: String, files: Map<String, ByteArray>, htmlDir: String): Bitmap? {
        val srcRe = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val src = srcRe.find(imgTag)?.groupValues?.get(1) ?: return null
        val bytes = resolveEpubPath(src.trim(), htmlDir, files) ?: return null
        return decodeScaledBitmap(bytes)
    }

    private fun resolveEpubPath(src: String, htmlDir: String, files: Map<String, ByteArray>): ByteArray? {
        val clean = src.trimStart('/').removePrefix("./")
        val candidates = buildList {
            if (htmlDir.isNotEmpty()) {
                add(normalizePath("$htmlDir/$clean"))
                add("$htmlDir/$clean")
            }
            add(clean)
            add(normalizePath(clean))
        }
        return candidates.firstNotNullOfOrNull { files[it] }
    }

    private fun normalizePath(path: String): String {
        val result = mutableListOf<String>()
        for (part in path.split("/")) {
            when (part) { ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex); ".", "" -> {}; else -> result.add(part) }
        }
        return result.joinToString("/")
    }

    private fun decodeScaledBitmap(bytes: ByteArray): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1800) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun parseContainerXml(bytes: ByteArray): String? = runCatching {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bytes.inputStream())
        (doc.getElementsByTagName("rootfile").item(0) as? Element)?.getAttribute("full-path")
    }.getOrNull()

    private fun parseOpf(bytes: ByteArray): OpfData =
        runCatching {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bytes.inputStream())
            val itemrefs = doc.getElementsByTagName("itemref")
            val spineIds = (0 until itemrefs.length).map { (itemrefs.item(it) as Element).getAttribute("idref") }
            val items = doc.getElementsByTagName("item")
            val manifest = (0 until items.length).associate { i ->
                val el = items.item(i) as Element; el.getAttribute("id") to el.getAttribute("href")
            }
            val author = doc.getElementsByTagName("dc:creator").let {
                if (it.length > 0) it.item(0).textContent.trim() else ""
            }
            var coverId: String? = null
            val metas = doc.getElementsByTagName("meta")
            for (i in 0 until metas.length) {
                val el = metas.item(i) as? Element ?: continue
                if (el.getAttribute("name") == "cover") {
                    coverId = el.getAttribute("content").ifEmpty { null }; break
                }
            }
            if (coverId == null) {
                for (i in 0 until items.length) {
                    val el = items.item(i) as Element
                    val id = el.getAttribute("id").lowercase()
                    val mime = el.getAttribute("media-type")
                    if ((id == "cover-image" || id == "cover") && mime.startsWith("image/")) {
                        coverId = el.getAttribute("id"); break
                    }
                }
            }
            if (coverId == null) {
                for (i in 0 until items.length) {
                    val el = items.item(i) as Element
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

    private fun savePosition() {
        val uri = currentUri ?: return; val k = posKey(uri)
        when (currentViewType) {
            ViewType.TXT, ViewType.EPUB -> {
                val lm = txtRecycler.layoutManager as? LinearLayoutManager ?: return
                val pos = lm.findFirstVisibleItemPosition()
                prefs.edit {
                    putInt("${k}_idx", pos)
                    putInt("${k}_off", txtRecycler.getChildAt(0)?.top ?: 0)
                }
                BookLibraryActivity.updateProgress(prefs, uri.toString(), pos, paragraphs.size)
            }
            ViewType.PDF -> {
                val lm = pdfRecycler.layoutManager as? LinearLayoutManager ?: return
                val pos = lm.findFirstVisibleItemPosition()
                prefs.edit { putInt("${k}_idx", pos) }
                BookLibraryActivity.updateProgress(prefs, uri.toString(), pos, pdfRenderer?.pageCount ?: 0)
            }
            ViewType.EMPTY -> {}
        }
    }

    private fun restoreTxtPosition(uri: Uri) {
        val k = posKey(uri)
        val pos = prefs.getInt("${k}_idx", 0); val off = prefs.getInt("${k}_off", 0)
        if (pos > 0) txtRecycler.post {
            (txtRecycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, off)
            ttsCurrentParagraph = paragraphAdapter?.paragraphIndexAtOrAfter(pos) ?: 0
        }
    }

    private fun restorePdfPosition(uri: Uri) {
        val pos = prefs.getInt("${posKey(uri)}_idx", 0)
        if (pos > 0) pdfRecycler.post {
            (pdfRecycler.layoutManager as? LinearLayoutManager)?.scrollToPosition(pos)
        }
    }

    private fun startAutoSave() {
        scope.launch { while (true) { delay(15_000); if (currentViewType != ViewType.EMPTY) savePosition() } }
    }

    // ── Décodage entités HTML ────────────────────────────────────────────────

    private fun decodeHtmlEntities(text: String): String {
        return text.replace(Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);")) { m ->
            val entity = m.groupValues[1]
            when {
                entity.startsWith("#x") -> entity.substring(2).toIntOrNull(16)?.toChar()?.toString() ?: m.value
                entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.toChar()?.toString() ?: m.value
                else -> HTML_NAMED_ENTITIES[entity] ?: m.value
            }
        }
    }

    // ── Détection d'encodage pour les fichiers TXT ────────────────────────────

    private fun readTextWithCharsetDetection(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return ""
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
                val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0) c.getLong(idx) else 0L
            } else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }

    private fun saveCoverBytes(bytes: ByteArray, uri: Uri): String? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val dir = File(filesDir, "book_covers").apply { mkdirs() }
        val file = File(dir, "${uri.toString().hashCode()}.jpg")
        file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        file.absolutePath
    }.getOrNull()

    private fun closePdf() {
        pdfRenderer?.close(); pdfDescriptor?.close(); pdfRenderer = null; pdfDescriptor = null
    }

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
    private enum class ViewType { EMPTY, PDF, TXT, EPUB }

    private fun showView(type: ViewType) {
        currentViewType = type
        emptyState.visibility = if (type == ViewType.EMPTY) View.VISIBLE else View.GONE
        pdfRecycler.visibility = if (type == ViewType.PDF) View.VISIBLE else View.GONE
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
        private var bookmarkedItems = setOf<Int>() // item indices

        private val TYPE_TEXT = 0; private val TYPE_IMAGE = 1; private val TYPE_CHAPTER = 2

        inner class TextHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)
        inner class ImageHolder(val iv: ImageView) : RecyclerView.ViewHolder(iv)
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
                    speakOffset = 0; pausedCharOffset = 0
                    ttsCurrentParagraph = paraIdx
                    setCursorParagraph(paraIdx)
                    autoScrollToReadingParagraph(paraIdx)
                    if (ttsState == TtsState.PLAYING) speakParagraph(paraIdx)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    val itemPos = holder.bindingAdapterPosition
                    if (itemPos == RecyclerView.NO_POSITION) return
                    val item = items[itemPos]
                    val text = when (item) {
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
                is EpubItem.Image -> (holder as ImageHolder).iv.setImageBitmap(item.bitmap)
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
                            h.tv.setBackgroundColor(theme.readingBg)
                            applyWordHighlight(h.tv, item.text, highlightStart, highlightEnd)
                        }
                        position == cursorItemIdx -> {
                            applyBookmarkPrefix(h.tv, item.text, isBookmarked)
                            h.tv.setBackgroundColor(ColorUtils.blendARGB(theme.bg1, theme.readingBg, 0.35f))
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
                            h.tv.setBackgroundColor(theme.readingBg)
                            applyWordHighlight(h.tv, item.text, highlightStart, highlightEnd)
                        }
                        position == cursorItemIdx -> {
                            applyBookmarkPrefix(h.tv, item.text, isBookmarked)
                            val base = if (useDistinction) theme.paragraphBg(paraIdx) else theme.bg1
                            h.tv.setBackgroundColor(ColorUtils.blendARGB(base, theme.readingBg, 0.35f))
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

        private fun applyWordHighlight(tv: TextView, text: String, start: Int, end: Int) {
            if (start >= 0 && end > start && end <= text.length) {
                val s = SpannableString(text)
                s.setSpan(BackgroundColorSpan(theme.wordBg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                tv.text = s
            } else { tv.text = text }
        }

        fun itemIndexForParagraph(paraIdx: Int): Int = textItemIndices.getOrElse(paraIdx) { paraIdx }

        fun paragraphIndexAtOrAfter(itemPos: Int): Int =
            textItemIndices.indexOfFirst { it >= itemPos }.takeIf { it >= 0 }
                ?: textItemIndices.lastIndex.coerceAtLeast(0)

        fun setCursorParagraph(idx: Int) {
            val prevItemIdx = textItemIndices.getOrElse(cursorParagraph) { -1 }
            cursorParagraph = idx
            val itemIdx = textItemIndices.getOrElse(idx) { -1 }
            val readingItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
            if (prevItemIdx >= 0 && prevItemIdx != readingItemIdx) notifyItemChanged(prevItemIdx)
            if (itemIdx >= 0 && itemIdx != readingItemIdx) notifyItemChanged(itemIdx)
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
                vh.tv.setBackgroundColor(theme.readingBg)
                applyWordHighlight(vh.tv, paragraphs[paragraphIndex], start, end)
            }
        }

        fun clearHighlight() {
            val prevItemIdx = textItemIndices.getOrElse(readingParagraph) { -1 }
            readingParagraph = -1; highlightStart = -1; highlightEnd = -1
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
            val full = "★  $text"
            val s = android.text.SpannableString(full)
            s.setSpan(
                android.text.style.ForegroundColorSpan(0xFFFFC107.toInt()),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            tv.text = s
        }
    }

    // ── Adaptateur PDF ────────────────────────────────────────────────────────

    private inner class PdfPagesAdapter(private val renderer: PdfRenderer) :
        RecyclerView.Adapter<PdfPagesAdapter.PageHolder>() {

        private val screenWidth = resources.displayMetrics.widthPixels

        inner class PageHolder(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageHolder(
            ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                adjustViewBounds = true
            }
        )

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val page = renderer.openPage(position)
            val h = (screenWidth * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(screenWidth, h, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close(); holder.image.setImageBitmap(bmp)
        }

        override fun getItemCount() = renderer.pageCount
    }
}

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
