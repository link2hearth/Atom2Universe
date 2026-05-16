package com.Atom2Universe.app.hub

import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
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
 * - Toggle liste/grille
 * - Drag & drop pour réordonner
 * - Mode édition pour personnaliser les couleurs (via SimpleColorPickerDialog)
 * - Long-press sur une tuile pour l'ajouter en raccourci dans le hub parent
 */
abstract class BaseHubActivity : AppCompatActivity() {

    companion object {
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_TILE_ORDER = "tile_order"
        private const val KEY_TILE_COLORS = "tile_colors"
        private const val KEY_QUICK_ACCESS = "quick_access"
        private const val VIEW_MODE_GRID = "grid"
        private const val VIEW_MODE_LIST = "list"
        private const val MAX_SHORTCUTS_PER_TILE = 3
    }

    protected lateinit var hubPrefs: SharedPreferences
    protected lateinit var tilesAdapter: HubTilesAdapter

    private lateinit var recyclerView: RecyclerView
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
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(getLayoutResId())

        hubPrefs = getSharedPreferences(getPrefsName(), Context.MODE_PRIVATE)

        initViews()
        setupBackButton()
        setupViewToggle()
        setupEditModeButton()
        setupRecyclerView()

        isGridMode = hubPrefs.getString(KEY_VIEW_MODE, VIEW_MODE_GRID) == VIEW_MODE_GRID
        updateViewMode()
    }

    abstract fun getLayoutResId(): Int
    abstract fun getPrefsName(): String
    abstract fun getHubTitle(): Int
    abstract fun getHubSubtitle(): Int?
    abstract fun getDefaultTiles(): List<HubTile>
    abstract fun onTileClicked(tile: HubTile)

    open fun onQuickAccessClicked(tile: HubTile, item: QuickAccessItem) {}
    open fun supportsQuickAccess(): Boolean = false

    // À override pour activer les raccourcis vers le hub parent
    open fun getParentHubPrefsName(): String? = null
    open fun getParentTileId(): String? = null

    private fun initViews() {
        recyclerView = findViewById(R.id.hub_recycler_view)
        listViewContainer = findViewById(R.id.hub_list_container)
        viewToggleButton = findViewById(R.id.hub_view_toggle)
        editModeButton = findViewById(R.id.hub_edit_mode)
        titleView = findViewById(R.id.hub_title)
        subtitleView = findViewById(R.id.hub_subtitle)

        titleView.setText(getHubTitle())
        getHubSubtitle()?.let { subtitleView?.setText(it) } ?: run { subtitleView?.visibility = View.GONE }
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.hub_back_button).setOnClickListener {
            finish()
        }
    }

    private fun setupViewToggle() {
        viewToggleButton.setOnClickListener {
            isGridMode = !isGridMode
            hubPrefs.edit { putString(KEY_VIEW_MODE, if (isGridMode) VIEW_MODE_GRID else VIEW_MODE_LIST) }
            updateViewMode()
        }
    }

    private fun setupEditModeButton() {
        editModeButton.setOnClickListener {
            isEditMode = !isEditMode
            tilesAdapter.setEditMode(isEditMode)
            updateEditModeUI()
        }
    }

    private fun updateViewMode() {
        if (isGridMode) {
            recyclerView.layoutManager = GridLayoutManager(this, 2)
            viewToggleButton.setImageResource(R.drawable.ic_view_list)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
            viewToggleButton.setImageResource(R.drawable.ic_view_grid)
        }
        tilesAdapter.setGridMode(isGridMode)

        recyclerView.post {
            val height = recyclerView.height
            if (height > 0) tilesAdapter.setRecyclerViewHeight(height)
        }
    }

    private fun updateEditModeUI() {
        if (isEditMode) {
            editModeButton.setImageResource(R.drawable.ic_check)
            editModeButton.setColorFilter(Color.parseColor("#4CAF50"))
        } else {
            editModeButton.setImageResource(R.drawable.ic_edit)
            editModeButton.clearColorFilter()
        }
    }

    private fun setupRecyclerView() {
        tilesAdapter = HubTilesAdapter(
            context = this,
            onTileClick = { tile -> onTileClicked(tile) },
            onOrderChanged = { order -> saveTileOrder(order) },
            onEditTile = { tile -> showColorPicker(tile) },
            onQuickAccessClick = { tile, item -> onQuickAccessClicked(tile, item) },
            onLongPressTile = { tile -> handleTileLongPress(tile) }
        )

        tilesAdapter.setShowQuickAccessButtons(supportsQuickAccess())

        recyclerView.adapter = tilesAdapter

        val touchCallback = HubTileTouchCallback(tilesAdapter)
        val itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
        tilesAdapter.attachItemTouchHelper(itemTouchHelper)

        loadTiles()

        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val height = recyclerView.height
                if (height > 0) {
                    tilesAdapter.setRecyclerViewHeight(height)
                    recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
    }

    private fun loadTiles() {
        val defaultTiles = getDefaultTiles()
        val savedOrder = loadTileOrder()
        val savedColors = loadTileColors()

        val tilesWithCustomization = defaultTiles.map { tile ->
            val colorData = savedColors[tile.id]
            tile.copy(
                customColorHex = colorData?.first,
                textColorMode = colorData?.second ?: "auto",
                quickAccessItems = emptyList()
            )
        }

        val orderedTiles = if (savedOrder.isNotEmpty()) {
            val ordered = savedOrder.mapNotNull { id -> tilesWithCustomization.find { it.id == id } }
            val newTiles = tilesWithCustomization.filter { tile -> tile.id !in savedOrder }
            ordered + newTiles
        } else {
            tilesWithCustomization
        }

        tilesAdapter.setTiles(orderedTiles)
    }

    private fun loadTileOrder(): List<String> {
        val saved = hubPrefs.getString(KEY_TILE_ORDER, null)
        return saved?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    private fun saveTileOrder(order: List<String>) {
        hubPrefs.edit { putString(KEY_TILE_ORDER, order.joinToString(",")) }
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
            AlertDialog.Builder(this)
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
            AlertDialog.Builder(this)
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
        val parentPrefs = getSharedPreferences(parentPrefsName, Context.MODE_PRIVATE)
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
        val parentPrefs = getSharedPreferences(parentPrefsName, Context.MODE_PRIVATE)
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
        val parentPrefs = getSharedPreferences(parentPrefsName, Context.MODE_PRIVATE)
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
