package com.Atom2Universe.app.science.bestiary

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class BestiaryActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private var repository: ParentesRepository? = null
    private var index: BestiaryLifeIndex? = null
    private var selected: String? = null
    private var pendingNode: String? = null
    private var query = ""
    private var group = ""
    private lateinit var list: ListView
    private lateinit var count: TextView
    private lateinit var treeStatus: LinearLayout
    private lateinit var groups: LinearLayout
    private val fiche by lazy {
        ScienceFiche(this, palette, R.string.be_back, R.string.be_close) { selected = null }
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
        buildUi()
        selected?.let { AnimalCatalog.byId[it]?.let(::open) }
        loadTree()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fiche.isShowing) fiche.dismiss() else finish() }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNode = intent.getStringExtra(EXTRA_LIFE_NODE)
        intent.getStringExtra(EXTRA_SPECIES_ID)?.let { AnimalCatalog.byId[it]?.let(::open) }
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
            fiche.dismiss(); query = ""; group = ""; pendingNode = null
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
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); renderList() }
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
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(dp(16) + safe.left, dp(8) + safe.top, dp(16) + safe.right, safe.bottom)
            insets
        }
        setContentView(root)
        renderGroups(); renderList()
    }

    private fun renderGroups() {
        groups.removeAllViews()
        (listOf("" to R.string.be_all) + AnimalCatalog.species.map { it.group }.distinct().map { it to groupTitle(it) }).forEach { (id, title) ->
            groups.addView(button(getString(title)) { group = id; renderGroups(); renderList() }.apply {
                background = palette.control(group == id); setTextColor(if (group == id) palette.onAccent else palette.text)
                isSelected = group == id
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
    }

    private fun renderList() {
        if (!::list.isInitialized) return
        val terms = LifeTree.normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        val collator = Collator.getInstance(resources.configuration.locales[0])
        val entries = AnimalCatalog.species.filter { animal ->
            (group.isEmpty() || group == animal.group) && terms.all {
                it in LifeTree.normalize(animalName(animal) + " " + animal.scientific)
            }
        }.sortedWith { a, b -> collator.compare(animalName(a), animalName(b)) }
        count.text = getString(if (entries.isEmpty()) R.string.be_empty else R.string.be_count, entries.size, AnimalCatalog.species.size)
        list.adapter = object : ArrayAdapter<AnimalSpecies>(this, android.R.layout.simple_list_item_1, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val animal = getItem(position)!!
                return (convertView as? LinearLayout ?: LinearLayout(this@BestiaryActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(8), dp(16), dp(8)); minimumHeight = dp(96)
                    addView(AnimalPlateView(this@BestiaryActivity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                        LinearLayout.LayoutParams(dp(104), dp(80)))
                    addView(column().apply { addView(label("", 18f, true)); addView(label("", 14f)) },
                        LinearLayout.LayoutParams(0, -2, 1f))
                }).apply {
                    background = palette.shape(palette.surface)
                    (getChildAt(0) as AnimalPlateView).variant = animal.id
                    (getChildAt(1) as LinearLayout).apply {
                        (getChildAt(0) as TextView).text = animalName(animal)
                        (getChildAt(1) as TextView).text = animalLatin(animal)
                    }
                }
            }
        }
        list.setOnItemClickListener { _, _, position, _ -> open(entries[position]) }
    }

    private fun loadTree() {
        treeStatus.removeAllViews()
        treeStatus.addView(label(getString(R.string.be_tree_loading), 12f))
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.Default) { ParentesRepository.load(applicationContext) } }
                .onSuccess {
                    repository = it
                    index = BestiaryLifeIndex(it.tree, AnimalCatalog.species.associate { animal -> animal.id to animal.scientific })
                    treeStatus.removeAllViews()
                    val linked = pendingNode != null
                    applyLink()
                    if (!linked) selected?.let { id -> AnimalCatalog.byId[id]?.let(::open) }
                }.onFailure {
                    treeStatus.removeAllViews()
                    treeStatus.addView(button(getString(R.string.be_tree_retry)) { loadTree() })
                }
        }
    }

    private fun applyLink() {
        val node = pendingNode ?: return
        val loaded = index ?: return
        pendingNode = null
        val animal = loaded.forNode(node)?.let { AnimalCatalog.byId[it] }
        if (animal != null) open(animal) else Toast.makeText(this, R.string.be_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun open(animal: AnimalSpecies) {
        selected = animal.id
        val encyclopediaProfile = animal.encyclopediaId?.let { id ->
            runCatching {
                AnimalEncyclopediaRepository.load(this, id, resources.configuration.locales[0].language)
            }.getOrNull()
        }
        val profileSections = encyclopediaProfile?.sections
        val top = column().apply {
            addView(label(animalLatin(animal), 18f).apply { setTypeface(typeface, Typeface.ITALIC) })
            addView(label(getString(groupTitle(animal.group)), 13f))
        }
        val media = column()
        val plate = AnimalPlateView(this).apply {
            variant = animal.id
            val identification = profileSections?.get("identification")
                ?: animal.identification?.let(::getString)
            contentDescription = identification?.let {
                getString(R.string.be_plate_accessibility, animalName(animal), it)
            } ?: getString(R.string.be_plate_accessibility_name, animalName(animal))
        }
        media.addView(FitFrame(this, .78f, dp(640)).apply { addView(plate, android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)) },
            if (fiche.wide) LinearLayout.LayoutParams(-1, 0, 1f) else full())
        media.addView(label(getString(R.string.be_plate_note), 12f), full())
        val body = column()
        if (encyclopediaProfile == null && animal.description == null) {
            body.addView(label(getString(R.string.be_encyclopedia_coming), 15f), full())
        }
        val sections = if (profileSections != null) {
            listOf(
                R.string.be_overview to profileSections["overview"],
                R.string.be_identification to profileSections["identification"],
                R.string.be_habitat to profileSections["habitat"],
                R.string.be_diet to profileSections["diet"],
                R.string.be_behavior to profileSections["behavior"],
                R.string.be_reproduction to profileSections["reproduction"],
                R.string.be_ecology to profileSections["ecology"],
                R.string.be_conservation to profileSections["conservation"]
            )
        } else {
            listOf(R.string.be_overview to animal.description?.let(::getString),
                R.string.be_identification to animal.identification?.let(::getString),
                R.string.be_habitat to animal.habitat?.let(::getString),
                R.string.be_diet to animal.diet?.let(::getString),
                R.string.be_behavior to animal.behavior?.let(::getString),
                R.string.be_reproduction to animal.reproduction?.let(::getString),
                R.string.be_ecology to animal.ecology?.let(::getString),
                R.string.be_conservation to animal.conservation?.let(::getString))
        }
        sections.forEach { (title, text) ->
            if (text == null) return@forEach
            body.addView(label(getString(title), 18f, true).apply { ViewCompat.setAccessibilityHeading(this, true) }, full(18))
            body.addView(label(text).apply { setTextIsSelectable(true) }, full(4))
        }
        index?.forSpecies(animal.id)?.let { node ->
            val repo = repository!!
            body.addView(label(getString(R.string.be_classification), 18f, true), full(18))
            body.addView(label(repo.tree.ancestors(node).filter { it in repo.groups }.reversed()
                .joinToString(getString(R.string.pt_separator)) { repo.name(this, it) }), full(4))
            body.addView(button(getString(R.string.be_open_tree)) {
                if (ScienceNavigation.isModuleLink(intent) && intent.getStringExtra(EXTRA_LIFE_NODE) == node) finish()
                else startActivity(Intent(this, ParentesActivity::class.java).putExtra(ScienceNavigation.EXTRA_FROM_MODULE, true)
                    .putExtra(ParentesActivity.EXTRA_NODE_ID, node))
            }, full())
        }
        val references = encyclopediaProfile?.references?.map { it.url to it.title }
            ?: animal.references.map { it.url to getString(it.title) }
        if (references.isNotEmpty()) {
            body.addView(label(getString(R.string.be_sources), 18f, true), full(18))
            body.addView(label(getString(R.string.be_source_note), 12f), full(4))
            references.forEach { (url, title) ->
                body.addView(button(title) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.be_no_browser, Toast.LENGTH_SHORT).show() }
                }, full())
                body.addView(label(url, 12f).apply { setTextIsSelectable(true) }, full(4))
            }
        }
        fiche.show(animalName(animal), top, media, body)
    }

    private fun animalName(animal: AnimalSpecies): String = animal.encyclopediaId?.let {
        BestiaryEncyclopediaCatalog.name(it, resources.configuration.locales[0].language)
    } ?: getString(animal.name)

    private fun animalLatin(animal: AnimalSpecies): String =
        if (animal.encyclopediaId != null) animal.scientific else getString(animal.latin)

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
    }
    override fun onDestroy() { fiche.dismiss(); super.onDestroy() }
    companion object {
        const val EXTRA_LIFE_NODE = "bestiary_life_node"
        const val EXTRA_SPECIES_ID = "bestiary_species_id"
    }
}
