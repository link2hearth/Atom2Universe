package com.Atom2Universe.app.science.bestiary

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.FitFrame
import com.Atom2Universe.app.science.ScienceFiche
import com.Atom2Universe.app.science.ScienceNavigation
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.science.parentes.LifeTree
import com.Atom2Universe.app.science.parentes.ParentesActivity
import com.Atom2Universe.app.science.parentes.ParentesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class BestiaryActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private var repository: ParentesRepository? = null
    private var index: BestiaryLifeIndex? = null
    private var entries: List<BestiaryEntry>? = null
    private var selected: String? = null
    private var pendingNode: String? = null
    private var query = ""
    private var group = ""
    private var listPosition = 0
    private var listTop = 0
    private var keepScroll = true
    private var opening: Job? = null
    // Classification block of the open profile, filled when the tree finishes loading.
    private var pendingClassification: Pair<String, LinearLayout>? = null
    private lateinit var list: ListView
    private lateinit var count: TextView
    private lateinit var treeStatus: LinearLayout
    private lateinit var groups: LinearLayout
    private val adapter = EntryAdapter()
    private val searchHandler = Handler(Looper.getMainLooper())
    private val applySearch = Runnable { renderList() }
    private val fiche by lazy {
        ScienceFiche(this, palette, R.string.be_back, R.string.be_close) { selected = null; pendingClassification = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.Atom2Universe.app.science.ScienceAssets.isAvailable(this, "bestiary")) {
            finish()
            return
        }
        query = savedInstanceState?.getString("query").orEmpty()
        group = savedInstanceState?.getString("group").orEmpty()
        selected = if (savedInstanceState != null) savedInstanceState.getString("species") else intent.getStringExtra(EXTRA_SPECIES_ID)
        pendingNode = if (savedInstanceState != null) savedInstanceState.getString("node") else intent.getStringExtra(EXTRA_LIFE_NODE)
        listPosition = savedInstanceState?.getInt("listPosition") ?: 0
        listTop = savedInstanceState?.getInt("listTop") ?: 0
        buildUi()
        loadEntries()
        loadTree()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fiche.isShowing) fiche.dismiss() else finish() }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNode = intent.getStringExtra(EXTRA_LIFE_NODE)
        intent.getStringExtra(EXTRA_SPECIES_ID)?.let { id ->
            if (entries == null) selected = id else AnimalCatalog.byId[id]?.let(::open)
        }
        applyLink()
    }

    private fun buildUi() {
        val root = column().apply { setBackgroundColor(palette.background); setPadding(dp(16), dp(8), dp(16), 0) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back_24); setColorFilter(palette.text); background = null
            contentDescription = getString(R.string.be_back); setOnClickListener { finish() }
        }
        ScienceNavigation.bindHomeAction(back) {
            fiche.dismiss(); query = ""; group = ""; pendingNode = null; keepScroll = false
            intent.removeExtra(EXTRA_SPECIES_ID); intent.removeExtra(EXTRA_LIFE_NODE)
            recreate()
        }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(label(getString(R.string.be_title), 26f, true))
        root.addView(header)
        root.addView(label(getString(R.string.be_intro), 14f))
        root.addView(EditText(this).apply {
            setTextColor(palette.text); setHintTextColor(palette.secondary)
            hint = getString(R.string.be_search); setSingleLine(); setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s.toString()
                    searchHandler.removeCallbacks(applySearch); searchHandler.postDelayed(applySearch, SEARCH_DELAY_MS)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }, full())
        groups = LinearLayout(this)
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(groups) })
        count = label("", 13f)
        root.addView(count, full())
        treeStatus = column()
        root.addView(treeStatus)
        list = ListView(this).apply { divider = null; setBackgroundColor(palette.background) }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> open(adapter.items[position].animal) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(dp(16) + safe.left, dp(8) + safe.top, dp(16) + safe.right, safe.bottom)
            insets
        }
        setContentView(root)
        renderGroups()
    }

    private fun renderGroups() {
        groups.removeAllViews()
        (listOf("" to R.string.be_all) + GROUPS.map { it to groupTitle(it) }).forEach { (id, title) ->
            groups.addView(button(getString(title)) { group = id; renderGroups(); renderList() }.apply {
                background = palette.control(group == id); setTextColor(if (group == id) palette.onAccent else palette.text)
                isSelected = group == id
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
    }

    private fun renderList(position: Int = 0, top: Int = 0) {
        searchHandler.removeCallbacks(applySearch)
        val all = entries ?: return
        val terms = LifeTree.normalize(query).split(SPACES).filter { it.isNotBlank() }
        val shown = all.filter { entry ->
            (group.isEmpty() || group == entry.animal.group) && terms.all { it in entry.searchKey }
        }
        count.text = getString(if (shown.isEmpty()) R.string.be_empty else R.string.be_count, shown.size, all.size)
        adapter.items = shown
        adapter.notifyDataSetChanged()
        list.setSelectionFromTop(position, top)
    }

    private inner class EntryAdapter : BaseAdapter() {
        var items: List<BestiaryEntry> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val entry = items[position]
            return (convertView as? LinearLayout ?: LinearLayout(this@BestiaryActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(16), dp(8)); minimumHeight = dp(96)
                background = palette.shape(palette.surface)
                addView(AnimalPlateView(this@BestiaryActivity, thumbnail = true).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                    LinearLayout.LayoutParams(dp(104), dp(80)))
                addView(column().apply { addView(label("", 18f, true)); addView(label("", 14f)) },
                    LinearLayout.LayoutParams(0, -2, 1f))
            }).apply {
                (getChildAt(0) as AnimalPlateView).variant = entry.animal.id
                (getChildAt(1) as LinearLayout).apply {
                    (getChildAt(0) as TextView).text = entry.name
                    (getChildAt(1) as TextView).text = entry.animal.scientific
                }
            }
        }
    }

    private fun loadEntries() {
        val locale = resources.configuration.locales[0]
        lifecycleScope.launch {
            entries = withContext(Dispatchers.Default) { AnimalCatalog.entries(locale) }
            renderList(listPosition, listTop)
            selected?.let { AnimalCatalog.byId[it]?.let(::open) }
            applyLink()
        }
    }

    private fun loadTree() {
        treeStatus.removeAllViews()
        treeStatus.addView(label(getString(R.string.be_tree_loading), 12f))
        lifecycleScope.launch {
            val loaded = try {
                withContext(Dispatchers.Default) {
                    val repo = ParentesRepository.load(applicationContext)
                    repo to BestiaryLifeIndex.of(repo.tree)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            treeStatus.removeAllViews()
            if (loaded == null) {
                treeStatus.addView(button(getString(R.string.be_tree_retry)) { loadTree() })
                return@launch
            }
            repository = loaded.first
            index = loaded.second
            pendingClassification?.let { (id, block) -> pendingClassification = null; addClassification(block, id) }
            applyLink()
        }
    }

    private fun applyLink() {
        val node = pendingNode ?: return
        val loaded = index ?: return
        if (entries == null) return
        pendingNode = null
        val animal = loaded.forNode(node)?.let { AnimalCatalog.byId[it] }
        if (animal == null) Toast.makeText(this, R.string.be_unavailable, Toast.LENGTH_LONG).show()
        else if (animal.id != selected) open(animal)
    }

    private fun open(animal: AnimalSpecies) {
        selected = animal.id
        opening?.cancel()
        val language = language()
        opening = lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                try {
                    AnimalEncyclopediaRepository.load(applicationContext, animal.id, language)
                } catch (_: IOException) {
                    null
                } catch (_: JSONException) {
                    null
                }
            }
            show(animal, profile)
        }
    }

    private fun show(animal: AnimalSpecies, profile: EncyclopediaProfile?) {
        val name = AnimalCatalog.name(animal, language())
        val profileSections = profile?.sections
        val top = column().apply {
            addView(label(animal.scientific, 18f).apply { setTypeface(typeface, Typeface.ITALIC) })
            addView(label(getString(groupTitle(animal.group)), 13f))
        }
        val media = column()
        val plate = AnimalPlateView(this).apply {
            variant = animal.id
            contentDescription = profileSections?.get("identification")?.let {
                getString(R.string.be_plate_accessibility, name, it)
            } ?: getString(R.string.be_plate_accessibility_name, name)
        }
        media.addView(FitFrame(this, .78f, dp(640)).apply { addView(plate, android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)) },
            if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else full())
        media.addView(label(getString(R.string.be_plate_note), 12f), full())
        val body = column()
        if (profileSections == null) {
            body.addView(label(getString(R.string.be_encyclopedia_coming), 15f), full())
        } else {
            listOf(
                R.string.be_overview to profileSections["overview"],
                R.string.be_identification to profileSections["identification"],
                R.string.be_habitat to profileSections["habitat"],
                R.string.be_diet to profileSections["diet"],
                R.string.be_behavior to profileSections["behavior"],
                R.string.be_reproduction to profileSections["reproduction"],
                R.string.be_ecology to profileSections["ecology"],
                R.string.be_conservation to profileSections["conservation"]
            ).forEach { (title, text) ->
                if (text == null) return@forEach
                body.addView(label(getString(title), 18f, true).apply { ViewCompat.setAccessibilityHeading(this, true) }, full(18))
                body.addView(label(text).apply { setTextIsSelectable(true) }, full(4))
            }
        }
        val classification = column()
        body.addView(classification, LinearLayout.LayoutParams(-1, -2))
        pendingClassification = null
        if (index != null) addClassification(classification, animal.id) else pendingClassification = animal.id to classification
        val references = profile?.references.orEmpty()
        if (references.isNotEmpty()) {
            body.addView(label(getString(R.string.be_sources), 18f, true), full(18))
            body.addView(label(getString(R.string.be_source_note), 12f), full(4))
            references.forEach { reference ->
                body.addView(button(reference.title) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, reference.url.toUri())) }
                    catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.be_no_browser, Toast.LENGTH_SHORT).show() }
                }, full())
                body.addView(label(reference.url, 12f).apply { setTextIsSelectable(true) }, full(4))
            }
        }
        fiche.show(name, top, media, body)
    }

    private fun addClassification(block: LinearLayout, animalId: String) {
        val node = index?.forSpecies(animalId) ?: return
        val repo = repository ?: return
        block.addView(label(getString(R.string.be_classification), 18f, true), full(18))
        block.addView(label(repo.tree.ancestors(node).filter { it in repo.groups }.reversed()
            .joinToString(getString(R.string.pt_separator)) { repo.name(this, it) }), full(4))
        block.addView(button(getString(R.string.be_open_tree)) {
            if (ScienceNavigation.isModuleLink(intent) && intent.getStringExtra(EXTRA_LIFE_NODE) == node) finish()
            else startActivity(Intent(this, ParentesActivity::class.java).putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                .putExtra(ParentesActivity.EXTRA_NODE_ID, node))
        }, full())
    }

    private fun language() = resources.configuration.locales[0].language

    private fun groupTitle(id: String) = when (id) {
        "mammals" -> R.string.be_mammals
        "birds" -> R.string.be_birds
        "reptiles" -> R.string.be_reptiles
        "amphibians" -> R.string.be_amphibians
        "fish" -> R.string.be_fish
        else -> R.string.be_invertebrates
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(palette.text); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; minHeight = dp(48); background = palette.control()
        setTextColor(palette.text); setOnClickListener { action() }
    }
    private fun full(top: Int = 8) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("query", query); outState.putString("group", group)
        outState.putString("species", selected); outState.putString("node", pendingNode)
        if (!keepScroll) return
        if (entries != null && ::list.isInitialized) {
            listPosition = list.firstVisiblePosition
            listTop = list.getChildAt(0)?.top ?: 0
        }
        outState.putInt("listPosition", listPosition); outState.putInt("listTop", listTop)
    }
    override fun onDestroy() { searchHandler.removeCallbacks(applySearch); fiche.dismiss(); super.onDestroy() }
    companion object {
        const val EXTRA_LIFE_NODE = "bestiary_life_node"
        const val EXTRA_SPECIES_ID = "bestiary_species_id"
        private const val SEARCH_DELAY_MS = 150L
        private val SPACES = Regex("\\s+")
        // Order of the group filters as first shown in the catalogue.
        private val GROUPS = listOf("birds", "fish", "invertebrates", "mammals", "amphibians", "reptiles")
    }
}
