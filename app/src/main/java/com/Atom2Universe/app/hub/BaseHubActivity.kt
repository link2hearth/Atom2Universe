package com.Atom2Universe.app.hub

import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.ThemedActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.SimpleColorPickerDialog
import com.Atom2Universe.app.util.enableImmersiveMode
import androidx.core.content.edit

/**
 * Activité de base pour tous les hubs.
 * Fournit les fonctionnalités communes:
 * - Toggle liste/grille (sauf pour un hub en tuiles carrées seulement)
 * - Drag & drop pour réordonner
 * - Mode édition pour personnaliser les couleurs (via SimpleColorPickerDialog), sauf pour un hub
 *   dont chaque tuile porte son illustration
 * - Long-press sur une tuile pour l'ajouter en raccourci dans le hub parent
 */
abstract class BaseHubActivity : ThemedActivity() {

    companion object {
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_TILE_ORDER = "tile_order"
        private const val KEY_TILE_PANES = "tile_panes"
        private const val KEY_LAST_PANE = "last_pane"
        private const val KEY_TILE_COLORS = "tile_colors"
        private const val KEY_QUICK_ACCESS = "quick_access"
        private const val VIEW_MODE_GRID = "grid"
        private const val VIEW_MODE_LIST = "list"
        private const val MAX_SHORTCUTS_PER_TILE = 3
    }

    protected lateinit var hubPrefs: SharedPreferences
    protected lateinit var tilesAdapter: HubTilesAdapter

    private lateinit var recyclerView: RecyclerView
    /** Un hub à plusieurs files (voir [getPaneCount]) : une grille et un adaptateur par file, la première étant [recyclerView] / [tilesAdapter]. */
    private val paneRecyclers = mutableListOf<RecyclerView>()
    protected val paneAdapters = mutableListOf<HubTilesAdapter>()
    private var carousel: HubCarouselLayout? = null
    private var listViewContainer: ScrollView? = null
    private lateinit var viewToggleButton: ImageButton
    private lateinit var editModeButton: ImageButton
    private lateinit var titleView: TextView
    private var subtitleView: TextView? = null

    protected var isGridMode = true
    protected var isEditMode = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.ensureLocale(this)
        super.onCreate(savedInstanceState)
        LocaleHelper.ensureLocale(this)
        enableImmersiveMode()
        setContentView(getLayoutResId())

        hubPrefs = getSharedPreferences(getPrefsName(), MODE_PRIVATE)

        initViews()
        setupBackButton()
        setupViewToggle()
        setupEditModeButton()
        setupRecyclerView()

        isGridMode = !supportsListMode() ||
            hubPrefs.getString(KEY_VIEW_MODE, VIEW_MODE_GRID) == VIEW_MODE_GRID
        updateViewMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.ensureLocale(this)
        // Le hub gère lui-même la rotation (configChanges) : l'activité n'est pas recréée.
        // Sans ce recalcul, la grille garderait les colonnes et les hauteurs du portrait,
        // et les tuiles deviendraient des rectangles allongés en paysage.
        updateViewMode()
    }

    abstract fun getLayoutResId(): Int
    abstract fun getPrefsName(): String
    abstract fun getHubTitle(): Int
    abstract fun getHubSubtitle(): Int?
    abstract fun getDefaultTiles(): List<HubTile>
    open fun normalizeTileOrder(tiles: List<HubTile>): List<HubTile> = tiles
    abstract fun onTileClicked(tile: HubTile)

    /**
     * Faux pour un hub dont chaque tuile porte son illustration : une couleur choisie n'y
     * changerait rien, le dessin la recouvre. Le mode édition ne sert plus qu'à ranger.
     */
    open fun supportsTileColors(): Boolean = true

    /**
     * Faux pour un hub qui ne s'affiche qu'en grille de tuiles carrées : le bouton liste/grille
     * disparaît. Les illustrations sont dessinées pour un carré, une bande longue les rognerait.
     */
    open fun supportsListMode(): Boolean = true

    /** Tuiles carrées en grille. Par défaut, seul un hub sans mode liste les veut carrées. */
    open fun usesSquareTiles(): Boolean = !supportsListMode()

    /**
     * Nombre de files de tuiles. Plus d'une : le hub les met côte à côte dans un carrousel (balayage
     * horizontal, sans fin) et il faut fournir `R.id.hub_carousel` avec une grille par file dans le
     * layout (`hub_recycler_view`, `hub_recycler_view_2`). Une tuile peut passer d'une file à l'autre.
     */
    open fun getPaneCount(): Int = 1

    /** La file où une tuile commence, tant que le joueur ne l'a pas changée. */
    open fun defaultPaneOf(tile: HubTile): Int = 0

    /** Le nom d'une file, écrit sous le titre du hub. */
    open fun getPaneTitle(pane: Int): Int? = null

    /** Appelé avant de lire l'ordre et les files enregistrés (pour une migration unique, par exemple). */
    open fun onBeforeLoadTiles() {}

    open fun onQuickAccessClicked(tile: HubTile, item: QuickAccessItem) {}
    open fun supportsQuickAccess(): Boolean = false

    // À override pour activer les raccourcis vers le hub parent
    open fun getParentHubPrefsName(): String? = null
    open fun getParentTileId(): String? = null

    private fun initViews() {
        recyclerView = findViewById(R.id.hub_recycler_view)
        paneRecyclers.add(recyclerView)
        if (getPaneCount() > 1) {
            paneRecyclers.add(findViewById(R.id.hub_recycler_view_2))
            carousel = findViewById(R.id.hub_carousel)
        }
        listViewContainer = findViewById(R.id.hub_list_container)
        viewToggleButton = findViewById(R.id.hub_view_toggle)
        editModeButton = findViewById(R.id.hub_edit_mode)
        titleView = findViewById(R.id.hub_title)
        subtitleView = findViewById(R.id.hub_subtitle)

        titleView.setText(getHubTitle())
        if (getPaneCount() > 1) {
            subtitleView?.visibility = View.VISIBLE
        } else {
            getHubSubtitle()?.let { subtitleView?.setText(it) } ?: run { subtitleView?.visibility = View.GONE }
        }
    }

    /** Le nom de la file affichée, avec un point par file dont la courante est pleine. */
    private fun updatePaneSubtitle(pane: Int) {
        val title = getPaneTitle(pane) ?: return
        val dots = (0 until getPaneCount()).joinToString(" ") { if (it == pane) "●" else "○" }
        subtitleView?.text = "$dots  ${getString(title)}"
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.hub_back_button).setOnClickListener {
            finish()
        }
    }

    private fun setupViewToggle() {
        if (!supportsListMode()) {
            viewToggleButton.visibility = View.GONE
            return
        }
        viewToggleButton.setOnClickListener {
            isGridMode = !isGridMode
            hubPrefs.edit { putString(KEY_VIEW_MODE, if (isGridMode) VIEW_MODE_GRID else VIEW_MODE_LIST) }
            updateViewMode()
        }
    }

    private fun setupEditModeButton() {
        editModeButton.setOnClickListener {
            isEditMode = !isEditMode
            paneAdapters.forEach { it.setEditMode(isEditMode) }
            updateEditModeUI()
        }
    }

    /** Nombre de colonnes de la grille selon la largeur d'écran (téléphone/tablette). */
    private fun calculateSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return when {
            widthDp >= 840 -> 4
            widthDp >= 600 -> 3
            else -> 2
        }
    }

    private fun updateViewMode() {
        paneRecyclers.forEachIndexed { i, rv ->
            val adapter = paneAdapters[i]
            if (isGridMode) {
                val spanCount = calculateSpanCount()
                // On garde le gestionnaire existant quand c'en est déjà un en grille :
                // changer seulement le nombre de colonnes conserve la position de défilement.
                val grid = rv.layoutManager as? GridLayoutManager
                if (grid != null) grid.spanCount = spanCount
                else rv.layoutManager = GridLayoutManager(this, spanCount)
                adapter.setSpanCount(spanCount)
            } else {
                rv.layoutManager = LinearLayoutManager(this)
            }
            adapter.setGridMode(isGridMode)
        }
        viewToggleButton.setImageResource(if (isGridMode) R.drawable.ic_view_list else R.drawable.ic_view_grid)

        awaitCorrectTileHeightBeforeDraw()
    }

    private fun updateEditModeUI() {
        if (isEditMode) {
            editModeButton.setImageResource(R.drawable.ic_check)
            editModeButton.setColorFilter(com.Atom2Universe.app.audio.AudioStyle.accent(this))
        } else {
            editModeButton.setImageResource(R.drawable.ic_edit)
            editModeButton.clearColorFilter()
        }
    }

    private fun setupRecyclerView() {
        paneRecyclers.forEachIndexed { pane, rv ->
            val adapter = HubTilesAdapter(
                context = this,
                onTileClick = { tile -> onTileClicked(tile) },
                onOrderChanged = { order -> savePaneOrder(pane, order) },
                onEditTile = if (supportsTileColors()) { tile -> showColorPicker(tile) } else null,
                onQuickAccessClick = { tile, item -> onQuickAccessClicked(tile, item) },
                onLongPressTile = { tile -> handleTileLongPress(tile) }
            )
            paneAdapters.add(adapter)

            adapter.setShowQuickAccessButtons(supportsQuickAccess())
            adapter.setIllustratedListMode(true)
            adapter.setSquareTiles(usesSquareTiles())

            rv.adapter = adapter
            // Une grille de tuiles lourdes : garder les vues juste sorties de l'écran liées évite de les
            // reconstruire au moindre aller-retour de défilement.
            rv.setItemViewCacheSize(12)

            val edgeListener = if (getPaneCount() > 1) object : HubTileTouchCallback.EdgeDragListener {
                override fun onDragState(dragging: Boolean, hot: Boolean) {
                    carousel?.setEdgeDrag(dragging, hot)
                }

                override fun onDroppedOnEdge(tileId: String) {
                    // Hors de la boucle de dessin du glisser-déposer qui vient de finir.
                    rv.post { moveTileToNextPane(pane, tileId) }
                }
            } else null
            val itemTouchHelper = ItemTouchHelper(HubTileTouchCallback(adapter, edgeListener))
            itemTouchHelper.attachToRecyclerView(rv)
            adapter.attachItemTouchHelper(itemTouchHelper)
        }
        tilesAdapter = paneAdapters[0]

        loadTiles()

        carousel?.let { c ->
            c.onPaneChanged = { pane ->
                hubPrefs.edit { putInt(KEY_LAST_PANE, pane) }
                updatePaneSubtitle(pane)
            }
            val last = hubPrefs.getInt(KEY_LAST_PANE, 0).coerceIn(0, getPaneCount() - 1)
            c.showPane(last, animated = false)
            updatePaneSubtitle(last)
        }

        awaitCorrectTileHeightBeforeDraw()
    }

    /** Dépose une tuile en tête de l'autre file, puis y glisse pour la montrer. */
    private fun moveTileToNextPane(fromPane: Int, tileId: String) {
        val toPane = (fromPane + 1) % getPaneCount()
        val tile = paneAdapters[fromPane].removeTile(tileId) ?: return
        paneAdapters[toPane].insertTile(0, tile)
        paneRecyclers[toPane].scrollToPosition(0)
        savePaneOrder(fromPane, paneAdapters[fromPane].getTileIds())
        savePaneOrder(toPane, paneAdapters[toPane].getTileIds())
        savePaneAssignments()
        carousel?.showPane(toPane, animated = true)
    }

    /**
     * La hauteur des tuiles dépend de la hauteur mesurée du RecyclerView, connue seulement
     * après un premier passage de layout. Corriger la hauteur après coup (post{} ou
     * OnGlobalLayoutListener) planifie un nouveau layout pour la frame suivante : la frame
     * actuelle s'affiche donc une fraction de seconde avec la hauteur par défaut (tuiles
     * étirées) avant la correction. OnPreDrawListener s'exécute juste avant l'affichage et
     * peut annuler la frame en cours (retour false) tant que la hauteur n'est pas stable,
     * ce qui évite complètement ce flash.
     */
    private fun awaitCorrectTileHeightBeforeDraw() {
        paneRecyclers.forEachIndexed { i, rv ->
            val adapter = paneAdapters[i]
            var appliedWidth = -1
            var appliedHeight = -1
            rv.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val width = rv.width
                    val height = rv.height
                    if (width <= 0 || height <= 0) return false
                    // La largeur compte aussi : c'est elle qui donne le côté des tuiles carrées.
                    if (width != appliedWidth || height != appliedHeight) {
                        appliedWidth = width
                        appliedHeight = height
                        adapter.setRecyclerViewSize(width, height)
                        return false
                    }
                    rv.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            })
        }
    }

    private fun loadTiles() {
        onBeforeLoadTiles()
        val defaultTiles = getDefaultTiles()
        val savedColors = loadTileColors()
        val assignments = if (getPaneCount() > 1) loadPaneAssignments() else emptyMap()

        val tilesWithCustomization = defaultTiles.map { tile ->
            // Une couleur enregistrée avant qu'un hub ne les retire resterait sinon pour toujours.
            val colorData = if (supportsTileColors()) savedColors[tile.id] else null
            tile.copy(
                // L'illustration se lit dans le registre commun : une seule déclaration par jeu.
                artworkClass = tile.artworkClass
                    ?: tile.activityClass?.let { HubTileArtworks.forActivity(it.name) },
                customColorHex = colorData?.first,
                textColorMode = colorData?.second ?: "auto",
                quickAccessItems = emptyList()
            )
        }

        for (pane in 0 until getPaneCount()) {
            val paneTiles = tilesWithCustomization.filter { tile ->
                val wanted = if (getPaneCount() > 1) assignments[tile.id] ?: defaultPaneOf(tile) else 0
                wanted.coerceIn(0, getPaneCount() - 1) == pane
            }
            val savedOrder = loadPaneOrder(pane)
            val orderedTiles = if (savedOrder.isNotEmpty()) {
                val ordered = savedOrder.mapNotNull { id -> paneTiles.find { it.id == id } }
                val newTiles = paneTiles.filter { tile -> tile.id !in savedOrder }
                ordered + newTiles
            } else {
                paneTiles
            }
            paneAdapters[pane].setTiles(normalizeTileOrder(orderedTiles))
        }
    }

    protected fun loadTileOrder(): List<String> = loadPaneOrder(0)

    protected fun saveTileOrder(order: List<String>) = savePaneOrder(0, order)

    /** La première file garde la clé historique ; les suivantes ont la leur. */
    private fun paneOrderKey(pane: Int) = if (pane == 0) KEY_TILE_ORDER else "${KEY_TILE_ORDER}_$pane"

    protected fun loadPaneOrder(pane: Int): List<String> {
        val saved = hubPrefs.getString(paneOrderKey(pane), null)
        return saved?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    protected fun savePaneOrder(pane: Int, order: List<String>) {
        hubPrefs.edit { putString(paneOrderKey(pane), order.joinToString(",")) }
    }

    /** Quelle file porte chaque tuile que le joueur a déplacée ("id:file;id:file"). */
    private fun loadPaneAssignments(): Map<String, Int> {
        val saved = hubPrefs.getString(KEY_TILE_PANES, null) ?: return emptyMap()
        return saved.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            val pane = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && pane != null) parts[0] to pane else null
        }.toMap()
    }

    private fun savePaneAssignments() {
        val encoded = paneAdapters.withIndex().joinToString(";") { (pane, adapter) ->
            adapter.getTileIds().joinToString(";") { "$it:$pane" }
        }
        hubPrefs.edit { putString(KEY_TILE_PANES, encoded) }
    }

    private fun loadTileColors(): Map<String, Pair<String, String>> {
        val saved = hubPrefs.getString(KEY_TILE_COLORS, null) ?: return emptyMap()
        return saved.split(";")
            .filter { it.contains(":") }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size >= 3) {
                    parts[0] to Pair(parts[1], parts[2])
                } else if (parts.size == 2) {
                    parts[0] to Pair(parts[1], "auto")
                } else null
            }
            .toMap()
    }

    private fun saveTileColors(colors: Map<String, Pair<String, String>>) {
        val encoded = colors.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" }
        hubPrefs.edit { putString(KEY_TILE_COLORS, encoded) }
    }

    private fun showColorPicker(tile: HubTile) {
        SimpleColorPickerDialog(
            context = this,
            currentColorHex = tile.customColorHex,
            currentTextColorMode = tile.textColorMode
        ) { colorHex, textColorMode ->
            tilesAdapter.updateTileColor(tile.id, colorHex, textColorMode)
            val currentColors = loadTileColors().toMutableMap()
            currentColors[tile.id] = Pair(colorHex, textColorMode)
            saveTileColors(currentColors)
        }.show()
    }

    // --- Raccourcis vers le hub parent ---

    private fun handleTileLongPress(tile: HubTile) {
        val parentPrefsName = getParentHubPrefsName() ?: return
        val parentTileId = getParentTileId() ?: return
        if (tile.activityClass == null) return

        val colorInt = if (tile.customColorHex != null) {
            Color.parseColor(tile.customColorHex)
        } else {
            ContextCompat.getColor(this, tile.defaultColorRes)
        }
        val colorHex = String.format("#%06X", 0xFFFFFF and colorInt)

        val tileName = getString(tile.titleRes)
        val alreadyShortcut = isAlreadyShortcut(parentPrefsName, parentTileId, tile.activityClass.name)

        if (alreadyShortcut) {
            com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
                .setTitle(R.string.hub_shortcut_add_title)
                .setMessage(getString(R.string.hub_shortcut_exists_message, tileName))
                .setPositiveButton(R.string.confirm_return_menu_yes) { _, _ ->
                    saveShortcutToParentHub(parentPrefsName, parentTileId, tileName, tile.activityClass.name, colorHex)
                }
                .setNegativeButton(R.string.confirm_return_menu_no) { _, _ ->
                    removeShortcutFromParentHub(parentPrefsName, parentTileId, tile.activityClass.name)
                }
                .show()
        } else {
            com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
                .setTitle(R.string.hub_shortcut_add_title)
                .setMessage(getString(R.string.hub_shortcut_add_message, tileName))
                .setPositiveButton(R.string.confirm_return_menu_yes) { _, _ ->
                    saveShortcutToParentHub(parentPrefsName, parentTileId, tileName, tile.activityClass.name, colorHex)
                    Toast.makeText(this, R.string.hub_shortcut_added, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.confirm_return_menu_no, null)
                .show()
        }
    }

    private fun isAlreadyShortcut(parentPrefsName: String, parentTileId: String, activityClassName: String): Boolean {
        val parentPrefs = getSharedPreferences(parentPrefsName, MODE_PRIVATE)
        val current = parentPrefs.getString(KEY_QUICK_ACCESS, null) ?: return false
        return current.split(";").any { entry ->
            val parts = entry.split(":", limit = 4)
            parts.size >= 3 && parts[0] == parentTileId && parts[2] == activityClassName
        }
    }

    private fun saveShortcutToParentHub(
        parentPrefsName: String,
        parentTileId: String,
        label: String,
        activityClassName: String,
        colorHex: String
    ) {
        val parentPrefs = getSharedPreferences(parentPrefsName, MODE_PRIVATE)
        val current = parentPrefs.getString(KEY_QUICK_ACCESS, null)
        val allEntries = current?.split(";")?.filter { it.isNotEmpty() } ?: emptyList()

        val thisEntry = "$parentTileId:$label:$activityClassName:$colorHex"

        // Entrées des autres tuiles parentes (inchangées)
        val otherParentEntries = allEntries.filter { !it.startsWith("$parentTileId:") }

        // Entrées de cette tuile parente, sans cet item (pour le déplacer en tête)
        val sameParentOthers = allEntries.filter { entry ->
            val parts = entry.split(":", limit = 4)
            parts.size >= 3 && parts[0] == parentTileId && parts[2] != activityClassName
        }

        // Nouvel ordre : cet item en tête, puis les autres, max 3
        val newSameParent = (listOf(thisEntry) + sameParentOthers).take(MAX_SHORTCUTS_PER_TILE)

        parentPrefs.edit()
            .putString(KEY_QUICK_ACCESS, (newSameParent + otherParentEntries).joinToString(";"))
            .apply()
    }

    private fun removeShortcutFromParentHub(
        parentPrefsName: String,
        parentTileId: String,
        activityClassName: String
    ) {
        val parentPrefs = getSharedPreferences(parentPrefsName, MODE_PRIVATE)
        val current = parentPrefs.getString(KEY_QUICK_ACCESS, null) ?: return

        val newEntries = current.split(";").filter { entry ->
            val parts = entry.split(":", limit = 4)
            !(parts.size >= 3 && parts[0] == parentTileId && parts[2] == activityClassName)
        }

        parentPrefs.edit { putString(KEY_QUICK_ACCESS, newEntries.joinToString(";")) }
    }

    protected fun startActivityForTile(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
    }
}
