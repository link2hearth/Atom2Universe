package com.Atom2Universe.app.news

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewsActivity : com.Atom2Universe.app.ThemedActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var toolbar: MaterialToolbar
    private lateinit var searchInput: EditText
    private lateinit var searchButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var statusView: TextView
    private lateinit var refreshButton: MaterialButton
    private lateinit var restoreButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var bannedInput: EditText
    private lateinit var bannedSave: MaterialButton
    private lateinit var sourcesContainer: LinearLayout

    private lateinit var adapter: NewsAdapter
    private var highlightId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)
        enableImmersiveMode()

        highlightId = intent.getStringExtra(NewsWidgetView.EXTRA_HIGHLIGHT_ID)

        toolbar         = findViewById(R.id.news_toolbar)
        searchInput     = findViewById(R.id.news_search_input)
        searchButton    = findViewById(R.id.news_search_button)
        clearButton     = findViewById(R.id.news_clear_button)
        statusView      = findViewById(R.id.news_status)
        refreshButton   = findViewById(R.id.news_refresh_button)
        restoreButton   = findViewById(R.id.news_restore_button)
        recyclerView    = findViewById(R.id.news_recycler)
        emptyState      = findViewById(R.id.news_empty_state)
        bannedInput     = findViewById(R.id.news_banned_input)
        bannedSave      = findViewById(R.id.news_banned_save)
        sourcesContainer = findViewById(R.id.news_sources_container)

        toolbar.setNavigationOnClickListener { finish() }

        adapter = NewsAdapter(
            onOpen = { article ->
                if (article.link != null) {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(article.link)
                    )
                    startActivity(intent)
                }
            },
            onHide = { article ->
                NewsPreferences.addHiddenId(this, article.id)
                displayArticles()
            }
        )
        adapter.highlightedId = highlightId

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchInput.setText(NewsPreferences.getLastQuery(this))

        searchButton.setOnClickListener { performSearch() }
        clearButton.setOnClickListener { clearSearch() }
        refreshButton.setOnClickListener { loadArticles(force = true) }
        restoreButton.setOnClickListener {
            NewsPreferences.clearHiddenIds(this)
            displayArticles()
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        bannedInput.setText(NewsPreferences.getBannedWords(this).joinToString(", "))
        bannedSave.setOnClickListener { saveBannedWords() }

        buildSourceToggles()
        loadArticles(force = false)
    }

    // ── Sources toggles ───────────────────────────────────────────────────────

    private fun buildSourceToggles() {
        sourcesContainer.removeAllViews()
        val enabledIds = NewsPreferences.getEnabledSourceIds(this)
        for (source in NewsSource.DEFAULT_SOURCES) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (8 * resources.displayMetrics.density).toInt() }
            }
            val label = TextView(this).apply {
                text = source.name
                textSize = 13f
                setTextColor(0xFFF1F5F9.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val toggle = SwitchMaterial(this).apply {
                isChecked = source.id in enabledIds
                val ta = context.obtainStyledAttributes(intArrayOf(R.attr.a2uMidiAccent))
                val accent = ta.getColor(0, 0xFF4CAF50.toInt())
                ta.recycle()
                val accentList = android.content.res.ColorStateList.valueOf(accent)
                thumbTintList = accentList
                trackTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(android.graphics.Color.argb(128, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent)), 0xFF1E293B.toInt())
                )
                setOnCheckedChangeListener { _, isChecked ->
                    val current = NewsPreferences.getEnabledSourceIds(this@NewsActivity).toMutableSet()
                    if (isChecked) current.add(source.id) else current.remove(source.id)
                    NewsPreferences.setEnabledSourceIds(this@NewsActivity, current)
                }
            }
            row.addView(label)
            row.addView(toggle)
            sourcesContainer.addView(row)
        }
    }

    // ── Load / display ────────────────────────────────────────────────────────

    private fun loadArticles(force: Boolean) {
        statusView.text = getString(R.string.news_loading)
        scope.launch {
            val sources  = NewsSource.DEFAULT_SOURCES.filter {
                it.id in NewsPreferences.getEnabledSourceIds(this@NewsActivity)
            }
            val banned   = NewsPreferences.getBannedWords(this@NewsActivity)
            val query    = NewsPreferences.getLastQuery(this@NewsActivity)
            try {
                withContext(Dispatchers.IO) {
                    NewsRepository.refresh(sources, query = query, bannedWords = banned)
                }
                displayArticles()
            } catch (_: Exception) {
                statusView.text = getString(R.string.news_error)
                emptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }

    private fun displayArticles() {
        val hidden   = NewsPreferences.getHiddenIds(this).keys
        val banned   = NewsPreferences.getBannedWords(this)
        val articles = NewsRepository.filterVisible(hidden, banned)

        val count = articles.size
        statusView.text = if (count == 0) getString(R.string.news_empty)
                          else resources.getQuantityString(R.plurals.news_count, count, count)

        adapter.items = articles
        recyclerView.visibility  = if (articles.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility    = if (articles.isEmpty()) View.VISIBLE else View.GONE

        if (highlightId != null) {
            val idx = articles.indexOfFirst { it.id == highlightId }
            if (idx >= 0) recyclerView.scrollToPosition(idx)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun performSearch() {
        hideKeyboard()
        val query = searchInput.text.toString().trim()
        NewsPreferences.setLastQuery(this, query)
        loadArticles(force = true)
    }

    private fun clearSearch() {
        searchInput.setText("")
        NewsPreferences.setLastQuery(this, "")
        hideKeyboard()
        loadArticles(force = true)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    // ── Banned words ──────────────────────────────────────────────────────────

    private fun saveBannedWords() {
        val raw = bannedInput.text.toString()
        val words = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        NewsPreferences.setBannedWords(this, words)
        hideKeyboard()
        displayArticles()
    }
}
