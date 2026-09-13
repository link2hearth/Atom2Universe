package com.Atom2Universe.app.games.caves

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.caves.world.A2MapStorage
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaveWorldMenuActivity : ThemedActivity() {
    private var assault = false
    private var loading = true
    private var loadFailed = false
    private var launching = false
    private var refreshJob: Job? = null
    private var worlds = emptyList<CaveWorldSave>()
    private var maps = emptyList<A2MapStorage.Entry>()
    private lateinit var recycler: RecyclerView
    private lateinit var navigation: LinearLayout
    private val menuAdapter = MenuAdapter()
    private val ink = Color.rgb(239, 245, 242)
    private val muted = Color.rgb(176, 195, 201)
    private val green = Color.rgb(166, 221, 172)
    private val blue = Color.rgb(163, 200, 249)
    private val orange = Color.rgb(255, 190, 143)
    private val accent get() = if (assault) orange else green

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assault = savedInstanceState?.getBoolean("assault") ?: false
        setContentView(R.layout.activity_cave_menu)
        enableImmersiveMode()
        navigation = findViewById(R.id.cave_menu_navigation)
        findViewById<LinearLayout>(R.id.cave_menu_sidebar).apply {
            addView(button(R.string.cave_back) { finish() }, 0, LinearLayout.LayoutParams(-1, -2))
            addSpaced(button(R.string.cave_menu_controls) {
                startActivity(Intent(this@CaveWorldMenuActivity, CaveControlsEditorActivity::class.java))
            })
        }
        recycler = findViewById(R.id.cave_menu_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = menuAdapter
        recycler.itemAnimator = null
        buildNavigation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("assault", assault)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        launching = false
        refreshList()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun surface(color: Int, border: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(16).toFloat()
        border?.let { setStroke(dp(1), it) }
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun label(value: CharSequence, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun LinearLayout.addSpaced(view: View, space: Int = 0) {
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(space)
        })
    }

    private fun button(title: Int, primary: Boolean = false, action: () -> Unit) = MaterialButton(this).apply {
        setText(title)
        isAllCaps = false
        textSize = 14f
        minHeight = dp(48)
        minimumHeight = dp(48)
        insetTop = dp(2)
        insetBottom = dp(2)
        cornerRadius = dp(12)
        backgroundTintList = ColorStateList.valueOf(if (primary) accent else Color.rgb(35, 54, 66))
        setTextColor(if (primary) Color.rgb(21, 38, 43) else ink)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
    }

    private fun buildNavigation() {
        navigation.removeAllViews()
        navigation.addSpaced(label(getString(R.string.cave_title), 25f, ink, true), 12)
        navigation.addSpaced(label(getString(R.string.cave_menu_tagline), 12f, muted), 4)
        navigation.addSpaced(modeTile(false), 20)
        navigation.addSpaced(modeTile(true), 10)
    }

    private fun modeTile(isAssault: Boolean): View = column().apply {
        val selected = assault == isAssault
        val tint = if (isAssault) orange else green
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = RippleDrawable(ColorStateList.valueOf(0x24FFFFFF),
            surface(if (selected) Color.rgb(43, 65, 73) else Color.rgb(26, 43, 55), if (selected) tint else null), null)
        isSelected = selected
        isFocusable = true
        val title = getString(if (isAssault) R.string.cave_menu_assault else R.string.cave_menu_infinite)
        val subtitle = getString(if (isAssault) R.string.cave_menu_assault_short else R.string.cave_menu_worlds_short)
        contentDescription = getString(if (selected) R.string.cave_menu_tab_selected else R.string.cave_menu_tab, title, subtitle)
        addSpaced(label(title, 17f, tint, true))
        addSpaced(label(subtitle, 12f, muted), 5)
        for (i in 0 until childCount) getChildAt(i).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnClickListener {
            if (assault != isAssault) {
                assault = isAssault
                buildNavigation()
                menuAdapter.notifyDataSetChanged()
                recycler.scrollToPosition(0)
            }
        }
    }

    private fun refreshList() {
        refreshJob?.cancel()
        loading = true
        loadFailed = false
        menuAdapter.notifyDataSetChanged()
        refreshJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CaveWorldSaveManager.listWorlds(this@CaveWorldMenuActivity) to A2MapStorage.list(this@CaveWorldMenuActivity) }
            }
            result.onSuccess { (savedWorlds, availableMaps) ->
                worlds = savedWorlds
                maps = availableMaps
            }
            loadFailed = result.isFailure
            loading = false
            menuAdapter.notifyDataSetChanged()
        }
    }

    private fun header(): View = column().apply {
        val banner = FrameLayout(this@CaveWorldMenuActivity).apply {
            minimumHeight = dp(76)
            background = surface(Color.rgb(28, 53, 62))
            clipToOutline = true
        }
        banner.addView(CaveMenuArt(this@CaveWorldMenuActivity, if (assault) 2 else 0), FrameLayout.LayoutParams(-1, -1))
        banner.addView(label(getString(if (assault) R.string.cave_menu_assault else R.string.cave_menu_infinite), 25f, ink, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF172834.toInt(), 0xD9172834.toInt(), 0x40172834))
        }, FrameLayout.LayoutParams(-1, -2))
        addSpaced(banner)
        addSpaced(label(getString(if (assault) R.string.cave_menu_assault_description else R.string.cave_menu_worlds_description), 14f, muted), 10)
        val title = if (assault) R.string.cave_menu_maps_heading else R.string.cave_menu_worlds_heading
        val heading = label(getString(title), 16f, accent, true)
        if (assault) addSpaced(heading, 18) else {
            val actions = LinearLayout(this@CaveWorldMenuActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            // Keep the button beside the heading on phones, but allow large text to flow vertically.
            if (resources.configuration.fontScale > 1.2f || resources.configuration.screenWidthDp < 600) {
                actions.orientation = LinearLayout.VERTICAL
                actions.addSpaced(button(R.string.cave_menu_new_world, true) { showCreateDialog() })
                actions.addSpaced(heading, 12)
            } else {
                actions.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
                actions.addView(button(R.string.cave_menu_new_world, true) { showCreateDialog() },
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            }
            addSpaced(actions, 12)
        }
        setPadding(0, 0, 0, dp(4))
    }

    private fun emptyState(): View = card().apply {
        val title = when {
            loading -> R.string.cave_menu_loading
            loadFailed -> R.string.cave_menu_load_failed
            assault -> R.string.cave_assault_pick_map
            else -> R.string.cave_menu_empty_title
        }
        addSpaced(label(getString(title), 18f, ink, true))
        if (!loading) {
            addSpaced(label(getString(when {
                loadFailed -> R.string.cave_menu_retry_hint
                assault -> R.string.cave_assault_no_maps
                else -> R.string.cave_menu_empty_hint
            }), 14f, muted), 8)
            if (loadFailed) addSpaced(button(R.string.cave_menu_retry) { refreshList() }, 12)
        }
    }

    private fun card() = column().apply {
        background = surface(Color.rgb(27, 44, 56), Color.rgb(46, 65, 76))
        setPadding(dp(16), dp(14), dp(16), dp(12))
    }

    private fun worldCard(save: CaveWorldSave): View = card().apply {
        addSpaced(label(getString(if (save.isCreative) R.string.cave_menu_creative else R.string.cave_menu_survival), 12f,
            if (save.isCreative) blue else green, true))
        addSpaced(label(save.name, 20f, ink, true), 5)
        addSpaced(label(getString(R.string.cave_menu_last_played, save.formattedLastPlayed()), 12f, muted), 6)
        val actions = LinearLayout(this@CaveWorldMenuActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(button(R.string.cave_menu_play, true) { launchWorld(save) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button(R.string.cave_menu_manage) { showWorldOptions(save) }.apply {
            contentDescription = getString(R.string.cave_menu_manage_named, save.name)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        addSpaced(actions, 10)
    }

    private fun mapCard(entry: A2MapStorage.Entry): View = card().apply {
        addSpaced(label(getString(if (entry.path.startsWith("builtin:") || entry.path.startsWith("asset:"))
            R.string.cave_menu_bundled_map else R.string.cave_menu_personal_map), 12f, orange, true))
        addSpaced(label(entry.name, 20f, ink, true), 5)
        addSpaced(button(R.string.cave_menu_launch_assault, true) { launchAssault(entry) }, 10)
    }

    private fun showCreateDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_cave_new_world, null)
        val etName = view.findViewById<TextInputEditText>(R.id.cave_dialog_name)
        val etSeed = view.findViewById<TextInputEditText>(R.id.cave_dialog_seed)
        val modes = view.findViewById<RadioGroup>(R.id.cave_dialog_mode)
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.cave_menu_new_world)
            .setView(view)
            .setPositiveButton(R.string.cave_menu_create, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val seedText = etSeed.text.toString().trim()
                val seed = if (seedText.isEmpty()) System.currentTimeMillis() else seedText.toLongOrNull()
                if (seed == null) {
                    etSeed.error = getString(R.string.cave_menu_invalid_seed)
                    return@setOnClickListener
                }
                val name = etName.text.toString().trim().ifEmpty { getString(R.string.cave_menu_default_name) }
                val creative = modes.checkedRadioButtonId == R.id.cave_dialog_creative
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { CaveWorldSaveManager.createWorld(this@CaveWorldMenuActivity, name, seed, creative) }
                    }
                    result.onSuccess { dialog.dismiss(); launchWorld(it) }
                        .onFailure {
                            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            showSaveError()
                        }
                }
            }
        }
        dialog.show()
    }

    private fun showWorldOptions(save: CaveWorldSave) {
        val options = arrayOf(
            getString(if (save.isCreative) R.string.cave_menu_switch_survival else R.string.cave_menu_switch_creative),
            getString(R.string.cave_menu_delete_world)
        )
        MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(save.name)
            .setItems(options) { _, which ->
                if (which == 1) showDeleteDialog(save) else lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { CaveWorldSaveManager.updateWorld(this@CaveWorldMenuActivity, save.copy(isCreative = !save.isCreative)) }
                    }
                    if (result.isSuccess) refreshList() else showSaveError()
                }
            }
            .setNeutralButton(R.string.cave_menu_details) { _, _ ->
                MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
                    .setTitle(save.name)
                    .setMessage(getString(R.string.cave_menu_seed_label, save.seed))
                    .setPositiveButton(android.R.string.ok, null).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(save: CaveWorldSave) {
        MaterialAlertDialogBuilder(this, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.cave_menu_delete_confirm_title)
            .setMessage(getString(R.string.cave_menu_delete_confirm_msg, save.name))
            .setPositiveButton(R.string.cave_menu_delete_world) { _, _ ->
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { CaveWorldSaveManager.deleteWorld(this@CaveWorldMenuActivity, save.id) }
                    }
                    if (result.isSuccess) {
                        Toast.makeText(this@CaveWorldMenuActivity, R.string.cave_menu_deleted, Toast.LENGTH_SHORT).show()
                        refreshList()
                    } else showSaveError()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showSaveError() {
        Toast.makeText(this, R.string.cave_menu_save_failed, Toast.LENGTH_LONG).show()
    }

    private fun launchWorld(save: CaveWorldSave) {
        if (launching) return
        launching = true
        startActivity(Intent(this, CaveActivity::class.java).apply { putExtra(CaveActivity.EXTRA_WORLD_ID, save.id) })
    }

    private fun launchAssault(entry: A2MapStorage.Entry) {
        if (launching) return
        launching = true
        lifecycleScope.launch {
            val readable = withContext(Dispatchers.IO) {
                runCatching { A2MapStorage.load(this@CaveWorldMenuActivity, entry.path) }.isSuccess
            }
            if (!readable) {
                launching = false
                Toast.makeText(this@CaveWorldMenuActivity, R.string.cave_assault_map_load_failed, Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this@CaveWorldMenuActivity, CaveActivity::class.java).apply {
                    putExtra(CaveActivity.EXTRA_MAP_PATH, entry.path)
                })
            }
        }
    }

    // The header scrolls with the list, keeping every action reachable on short screens and at large font sizes.
    private inner class MenuAdapter : RecyclerView.Adapter<MenuAdapter.Holder>() {
        inner class Holder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
        override fun getItemCount(): Int = 1 + if (loading || loadFailed) 1 else
            (if (assault) maps.size else worlds.size).coerceAtLeast(1)
        override fun getItemViewType(position: Int) = if (position == 0) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(column().apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.container.removeAllViews()
            holder.container.addView(when {
                position == 0 -> header()
                loading || loadFailed -> emptyState()
                assault -> maps.getOrNull(position - 1)?.let { mapCard(it) } ?: emptyState()
                else -> worlds.getOrNull(position - 1)?.let { worldCard(it) } ?: emptyState()
            })
        }
    }
}
