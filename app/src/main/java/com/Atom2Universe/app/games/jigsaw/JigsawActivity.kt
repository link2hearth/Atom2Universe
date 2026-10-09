package com.Atom2Universe.app.games.jigsaw

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.graphics.Insets
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder
import com.Atom2Universe.app.util.SystemBarsManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Home (resume, surprise image or photo), then the options for the chosen image, then the board.
 * Nothing explains the rules on screen: they live behind the « i » button.
 */
class JigsawActivity : ThemedActivity() {
    private lateinit var store: JigsawStore
    private lateinit var repository: NeutrinoRepository
    private lateinit var body: FrameLayout
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var helpButton: ImageButton
    private lateinit var palette: KitPalette
    private var game: JigsawGame? = null
    private var board: JigsawView? = null
    private lateinit var guideButton: ImageButton
    private lateinit var guidePrice: TextView
    private lateinit var guideBox: View
    private var guideSheet: BottomSheetDialog? = null
    private var rotateButton: ImageButton? = null
    private var replayButton: View? = null
    private var loading: Job? = null
    private var loadGeneration = 0
    private var celebrationPlayed = false
    private var collection = JigsawImages.DEFAULT_COLLECTION
    private var size = JigsawSize.EASY
    private var pendingImage: Bitmap? = null
    private var pendingPath: String? = null
    private var rotationEnabled = false
    private var layoutChoice = JigsawLayout.TRAY
    private var active = false
    private val choosePhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        // Keeps the photo readable after a restart, whenever its provider allows it.
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        selectImage(uri.toString())
    }
    private var clockAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val saveLater = Runnable { save() }
    private val ticker = object : Runnable {
        override fun run() {
            checkpointClock(); updateStatus()
            if (active) handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        palette = KitPalette.from(this)
        store = JigsawStore(this); repository = NeutrinoRepository(this)
        game = store.load()
        game?.let { size = it.size; rotationEnabled = it.rotation; layoutChoice = it.layout }
        savedInstanceState?.let {
            collection = it.getInt("collection", collection)
            size = JigsawSize.entries[it.getInt("size", size.ordinal).coerceIn(JigsawSize.entries.indices)]
            rotationEnabled = it.getBoolean("rotation", rotationEnabled)
            layoutChoice = JigsawLayout.entries[it.getInt("layout", layoutChoice.ordinal).coerceIn(JigsawLayout.entries.indices)]
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(palette.background)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(2), dp(4), dp(2)) }
        header.addView(headerIcon(R.drawable.ic_app_back, R.string.jigsaw_back, palette.text) { onBackPressedDispatcher.onBackPressed() },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, 0, 0)
        }
        title = text(R.string.jigsaw_title, 20f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        status = text(R.string.jigsaw_title, 12f).apply {
            setTextColor(palette.secondary); visibility = View.GONE; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        heading.addView(title); heading.addView(status)
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        helpButton = headerIcon(R.drawable.ic_info, R.string.jigsaw_help, palette.accent) { showRules() }
        header.addView(helpButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        // In game, the transparent guide takes the place of the rules button, facing the back arrow.
        val guide = FrameLayout(this).apply { visibility = View.GONE }
        guideButton = headerIcon(R.drawable.ic_px_eye, R.string.jigsaw_ghost, palette.secondary) { requestGuide() }
        guide.addView(guideButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        guidePrice = text(R.string.jigsaw_title, 10f).apply {
            text = getString(R.string.jigsaw_count_short, NeutrinoRewards.JIGSAW_GUIDE_COST)
            gravity = Gravity.CENTER; setTextColor(palette.onAccent); background = rounded(palette.accent, 8f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        guide.addView(guidePrice, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.END).apply { topMargin = dp(4); rightMargin = dp(2) })
        guideBox = guide
        header.addView(guide, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            applyInsets(view, header, insets)
            insets
        }
        setContentView(root)
        root.doOnLayout { ViewCompat.requestApplyInsets(it) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (board != null || loading != null || pendingPath != null) showSetup() else finish()
            }
        })
        showSetup()
        val pending = savedInstanceState?.getString("pendingPath")
        if (pending != null) selectImage(pending)
        else game?.takeUnless { it.solved }?.let { openGame(it) }
    }

    private fun applyInsets(root: View, header: View, insets: WindowInsetsCompat) {
        // Transient bars must not push the puzzle down in immersive mode.
        val bars = if (SystemBarsManager.shouldShowSystemBars(this))
            insets.getInsets(WindowInsetsCompat.Type.systemBars()) else Insets.NONE
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        root.setPadding(max(bars.left, cutout.left), bars.top,
            max(bars.right, cutout.right), max(bars.bottom, cutout.bottom))

        // Avoid camera holes horizontally instead of reserving a blank strip above the header.
        val topCutouts = insets.displayCutout?.boundingRects.orEmpty().filter {
            it.top < bars.top + dp(52) && it.bottom > bars.top
        }
        val left = max(dp(4), (topCutouts.filter { it.centerX() < root.width / 3 }
            .maxOfOrNull { it.right - root.paddingLeft + dp(8) } ?: 0))
        val right = max(dp(4), (topCutouts.filter { it.centerX() > root.width * 2 / 3 }
            .maxOfOrNull { root.width - root.paddingRight - it.left + dp(8) } ?: 0))
        header.setPadding(left, dp(2), right, dp(2))
        val headingLeft = root.paddingLeft + left + dp(48)
        val textWidth = topCutouts.filter { it.left >= headingLeft }
            .minOfOrNull { (it.left - headingLeft - dp(8)).coerceAtLeast(0) } ?: Int.MAX_VALUE
        title.maxWidth = textWidth; status.maxWidth = textWidth
    }

    override fun onResume() {
        super.onResume(); active = true; startClock(); board?.resumeVictory()
        handler.removeCallbacks(ticker); handler.post(ticker)
        if (board != null && game?.solved == true) complete()
    }
    override fun onPause() {
        board?.cancelGesture(); board?.pauseVictory(); stopClock(); active = false
        guideSheet?.dismiss()
        handler.removeCallbacks(ticker); handler.removeCallbacks(saveLater)
        save(synchronous = true)
        super.onPause()
    }
    override fun onDestroy() {
        loading?.cancel(); guideSheet?.dismiss(); handler.removeCallbacksAndMessages(null)
        pendingImage?.recycle(); pendingImage = null
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("collection", collection)
        outState.putInt("size", size.ordinal)
        outState.putBoolean("rotation", rotationEnabled)
        outState.putInt("layout", layoutChoice.ordinal)
        outState.putString("pendingPath", pendingPath)
        super.onSaveInstanceState(outState)
    }
    private fun checkpointClock() {
        val now = SystemClock.elapsedRealtime()
        if (clockAt != 0L) game?.let { it.elapsedMs += now - clockAt }
        clockAt = if (clockAt != 0L) now else 0L
    }
    private fun startClock() {
        if (active && board != null && game?.solved == false && clockAt == 0L) clockAt = SystemClock.elapsedRealtime()
    }
    private fun stopClock() { checkpointClock(); clockAt = 0 }
    private fun save(synchronous: Boolean = false) { checkpointClock(); game?.let { store.save(it, synchronous) } }
    /** Encoding a large table takes a few milliseconds: grouped, and the pause still saves at once. */
    private fun scheduleSave() { handler.removeCallbacks(saveLater); handler.postDelayed(saveLater, 1000) }

    /** Home without a chosen image, options once an image is loaded. */
    private fun showSetup(clearImage: Boolean = true) {
        ++loadGeneration; loading?.cancel(); loading = null
        guideSheet?.dismiss(); board?.cancelGesture(); stopClock(); save()
        board = null; rotateButton = null; replayButton = null
        body.removeAllViews(); title.setText(R.string.jigsaw_title); status.visibility = View.GONE
        helpButton.visibility = View.VISIBLE; guideBox.visibility = View.GONE
        if (clearImage) { pendingImage?.recycle(); pendingImage = null; pendingPath = null }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        scroll.addView(page); body.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val image = pendingImage
        if (image == null) showHome(page) else showOptions(page, image)
    }

    private fun mystery() = ImageView(this).apply {
        setImageDrawable(JigsawMysteryDrawable(palette, getString(R.string.jigsaw_mystery_mark)))
        scaleType = ImageView.ScaleType.FIT_XY; contentDescription = getString(R.string.jigsaw_mystery_accessibility)
    }

    private fun showHome(page: LinearLayout) {
        page.addView(mystery(), LinearLayout.LayoutParams(-1, dp(132)))
        page.addView(text(R.string.jigsaw_mystery_title, 23f).apply {
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0)
        })
        game?.let { saved ->
            val resume = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(6), dp(6), dp(6))
                background = rounded(palette.raised, 18f, palette.outline)
                foreground = RippleDrawable(ColorStateList.valueOf(palette.withAlpha(palette.accent, .20f)), null, rounded(palette.text, 18f))
                setOnClickListener { openGame(saved) }
            }
            resume.addView(text(R.string.jigsaw_title, 15f).apply {
                text = if (saved.solved) getString(R.string.jigsaw_view_finished)
                    else getString(R.string.jigsaw_resume_summary, saved.placed, saved.grid.count)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            resume.addView(icon(R.drawable.ic_play, if (saved.solved) R.string.jigsaw_view_finished else R.string.jigsaw_resume) { openGame(saved) }.apply {
                imageTintList = ColorStateList.valueOf(palette.accent)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            page.addView(resume, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        }
        val available = JigsawImages.collections.indices.filter { JigsawImages.paths(this, it).isNotEmpty() }
        if (collection >= 0 && collection !in available) collection = available.firstOrNull() ?: -1
        if (available.isNotEmpty()) {
            val collectionButton = choice(collectionLabel()) {}
            collectionButton.setIconResource(R.drawable.ic_expand_more_24); collectionButton.iconGravity = MaterialButton.ICON_GRAVITY_END
            collectionButton.contentDescription = getString(R.string.jigsaw_style)
            collectionButton.setOnClickListener { chooseCollection { collectionButton.text = collectionLabel() } }
            page.addView(collectionButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(22) })
            page.addView(button(R.string.jigsaw_start_surprise, R.drawable.ic_shuffle, primary = true) { startSurprise() },
                LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) })
        }
        page.addView(button(R.string.jigsaw_choose_photo, R.drawable.ic_px_image, primary = available.isEmpty()) {
            choosePhoto.launch(arrayOf("image/*"))
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(if (available.isEmpty()) 22 else 10) })
    }

    private fun showOptions(page: LinearLayout, image: Bitmap) {
        page.addView(mystery(), LinearLayout.LayoutParams(-1, dp(96)))
        val grids = JigsawSize.entries.map { it to JigsawGrid.forImage(it, image.width, image.height) }
            .distinctBy { it.second.count }.sortedBy { it.second.count }
        if (grids.none { it.first == size }) size = grids.minBy { abs(it.first.ordinal - size.ordinal) }.first
        page.addView(fieldLabel(R.string.jigsaw_difficulty))
        val choices = mutableListOf<Pair<MaterialButton, JigsawSize>>()
        fun refreshSizes() { choices.forEach { (button, value) -> styleChoice(button, value == size) } }
        grids.chunked(3).forEach { sizes ->
            val row = LinearLayout(this)
            sizes.forEach { (option, grid) ->
                val chip = choice(getString(R.string.jigsaw_grid_choice, grid.count, grid.columns, grid.rows)) { size = option; refreshSizes() }
                chip.contentDescription = getString(R.string.jigsaw_grid_accessibility, grid.count, grid.columns, grid.rows)
                choices += chip to option
                row.addView(chip, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
            }
            page.addView(row)
        }
        refreshSizes()
        page.addView(fieldLabel(R.string.jigsaw_layout))
        val arrangement = LinearLayout(this)
        val modes = mutableListOf<Pair<MaterialButton, JigsawLayout>>()
        fun refreshModes() { modes.forEach { (button, value) -> styleChoice(button, value == layoutChoice) } }
        JigsawLayout.entries.forEach { value ->
            val chip = choice(getString(if (value == JigsawLayout.TRAY) R.string.jigsaw_layout_tray_short else R.string.jigsaw_layout_table_short)) {
                layoutChoice = value; refreshModes()
            }.apply { setIconResource(if (value == JigsawLayout.TRAY) R.drawable.ic_jigsaw_tray else R.drawable.ic_jigsaw_table); iconSize = dp(20) }
            modes += chip to value
            arrangement.addView(chip, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        page.addView(arrangement); refreshModes()
        val rotation = SwitchMaterial(this).apply {
            setText(R.string.jigsaw_rotation); setTextColor(palette.text); textSize = 15f; isChecked = rotationEnabled
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(palette.accent, palette.secondary))
            trackTintList = ColorStateList(states, intArrayOf(palette.withAlpha(palette.accent, .45f), palette.outline))
            setOnCheckedChangeListener { _, checked -> rotationEnabled = checked }
        }
        page.addView(rotation, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(3), dp(14), dp(3), 0) })
        page.addView(button(R.string.jigsaw_start, R.drawable.ic_play, primary = true) { startPreparedGame() },
            LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16) })
    }

    private fun collectionLabel() = if (collection < 0) getString(R.string.jigsaw_all_collections)
        else getString(JigsawImages.collections[collection].labelRes)
    private fun chooseCollection(onSelected: () -> Unit) {
        val sheet = BottomSheetDialog(this)
        val page = sheetPage(R.string.jigsaw_style)
        val options = RadioGroup(this)
        val available = listOf(-1) + JigsawImages.collections.indices.filter { JigsawImages.paths(this, it).isNotEmpty() }
        available.forEach { index ->
            val label = if (index < 0) getString(R.string.jigsaw_all_collections) else getString(JigsawImages.collections[index].labelRes)
            val radio = RadioButton(this).apply {
                id = View.generateViewId(); text = label; setTextColor(palette.text); buttonTintList = ColorStateList.valueOf(palette.accent)
                isChecked = collection == index
                setOnClickListener { collection = index; onSelected(); sheet.dismiss() }
            }
            options.addView(radio, RadioGroup.LayoutParams(-1, dp(48)))
        }
        page.addView(options); sheet.setContentView(page); sheet.show()
    }
    private fun startSurprise() {
        val paths = JigsawImages.paths(this, collection)
        val unseen = paths.filterNot { store.completed(it) }
        val path = (unseen.ifEmpty { paths }).randomOrNull() ?: return
        selectImage(path)
    }
    private fun selectImage(path: String) {
        showSetup()
        pendingPath = path
        val generation = ++loadGeneration
        body.removeAllViews()
        body.addView(spinner(), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        loading = lifecycleScope.launch {
            val decoded = decodeImage(path)
            if (generation != loadGeneration) { decoded?.recycle(); return@launch }
            loading = null
            if (decoded == null) showSetup()
            else {
                pendingImage = decoded
                showSetup(clearImage = false)
            }
        }
    }
    private fun startPreparedGame() {
        val path = pendingPath ?: return
        val image = pendingImage ?: return
        val start = {
            val grid = JigsawGrid.forImage(size, image.width, image.height)
            val next = JigsawGame(path, size, Random.nextInt(), rotationEnabled, layout = layoutChoice, grid = grid)
            pendingImage = null; pendingPath = null
            showLoadedGame(next, image)
        }
        if (game?.solved == false) {
            ImmersiveAlertDialogBuilder(this).setTitle(R.string.jigsaw_replace_title).setMessage(R.string.jigsaw_replace_message)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.jigsaw_start) { _, _ -> start() }.show()
        } else start()
    }
    private fun openGame(next: JigsawGame) {
        pendingImage?.recycle(); pendingImage = null; pendingPath = null
        board?.cancelGesture(); stopClock(); loading?.cancel()
        board = null; rotateButton = null; replayButton = null
        val generation = ++loadGeneration
        body.removeAllViews(); status.visibility = View.GONE; helpButton.visibility = View.GONE; guideBox.visibility = View.GONE
        body.addView(spinner(), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        loading = lifecycleScope.launch {
            val decoded = decodeImage(next.imagePath)
            if (generation != loadGeneration) { decoded?.recycle(); return@launch }
            loading = null
            // The image is gone (photo deleted or access lost): the home screen stays, the save too.
            if (decoded == null) showSetup() else showLoadedGame(next, decoded)
        }
    }
    private suspend fun decodeImage(path: String): Bitmap? {
        var decoded: Bitmap? = null
        try {
            withContext(Dispatchers.IO) { decoded = JigsawImages.decode(this@JigsawActivity, path) }
            return decoded
        } catch (cancelled: CancellationException) {
            decoded?.recycle()
            throw cancelled
        }
    }
    private fun showLoadedGame(next: JigsawGame, decoded: Bitmap) {
        helpButton.visibility = View.GONE
        if (!next.guideUnlocked && repository.hasJigsawGuide(next.sessionId) &&
            repository.purchaseJigsawGuide(next.sessionId) == NeutrinoRepository.GuidePurchase.UNLOCKED) {
            next.guideUnlocked = true; next.guideVisible = true
        }
        game = next; showBoard(next, decoded); save(); startClock()
        if (next.solved) complete()
    }

    private fun showBoard(current: JigsawGame, image: Bitmap) {
        body.removeAllViews(); celebrationPlayed = false
        title.text = getString(R.string.jigsaw_piece_count, current.grid.count); status.visibility = View.VISIBLE
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(page, FrameLayout.LayoutParams(-1, -1))
        board = JigsawView(this, current, image).apply {
            onChanged = { scheduleSave(); updateStatus(); updateTools(); if (current.solved) complete(celebrate = true) }
            onSelectionChanged = { updateTools() }
            onVictoryFinished = { updateStatus() }
        }
        page.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))
        val tools = LinearLayout(this).apply {
            gravity = Gravity.CENTER; background = rounded(palette.raised, 22f, palette.outline); setPadding(dp(6), 0, dp(6), 0)
        }
        rotateButton = icon(R.drawable.ic_jigsaw_rotate, R.string.jigsaw_rotate) { board?.rotateSelected(); updateTools() }
        tools.addView(rotateButton, LinearLayout.LayoutParams(dp(52), dp(48)))
        replayButton = icon(R.drawable.ic_restart, R.string.jigsaw_replay_animation) { board?.playVictory() }
        tools.addView(replayButton, LinearLayout.LayoutParams(dp(52), dp(48)))
        tools.addView(icon(R.drawable.ic_ae_zoom_fit, R.string.jigsaw_recenter) { board?.fitBoard() }, LinearLayout.LayoutParams(dp(52), dp(48)))
        val more = icon(R.drawable.ic_more_vert_24, R.string.jigsaw_more) {}
        more.setOnClickListener { showToolsMenu(more) }
        tools.addView(more, LinearLayout.LayoutParams(dp(52), dp(48)))
        page.addView(tools, LinearLayout.LayoutParams(-2, dp(52)).apply { gravity = Gravity.CENTER; setMargins(dp(8), dp(5), dp(8), dp(8)) })
        updateStatus(); updateTools()
    }
    private fun updateStatus() {
        val current = game ?: return
        if (board == null) return
        val seconds = current.elapsedMs / 1000
        val time = getString(R.string.jigsaw_time, seconds / 60, seconds % 60)
        val progress = getString(if (current.solved) R.string.jigsaw_solved_status else R.string.jigsaw_status, current.placed, current.grid.count, time)
        status.text = if (current.solved && current.rewardHandled) getString(R.string.jigsaw_completion_status, progress,
            getString(R.string.jigsaw_completion_reward, NeutrinoRewards.jigsaw(current.size.ordinal))) else progress
        status.setTextColor(if (current.solved) palette.success else palette.secondary)
    }
    private fun updateTools() {
        val current = game ?: return
        guideBox.visibility = if (board == null || current.solved) View.GONE else View.VISIBLE
        replayButton?.visibility = if (current.solved) View.VISIBLE else View.GONE
        rotateButton?.visibility = if (current.rotation && !current.solved) View.VISIBLE else View.GONE
        rotateButton?.isEnabled = board?.canRotateSelection == true
        rotateButton?.alpha = if (board?.canRotateSelection == true) 1f else .35f
        guidePrice.visibility = if (current.guideUnlocked) View.GONE else View.VISIBLE
        guideButton.apply {
            setImageResource(if (current.guideVisible) R.drawable.ic_px_eye_off else R.drawable.ic_px_eye)
            imageTintList = ColorStateList.valueOf(if (current.guideVisible) palette.accent else palette.secondary)
            contentDescription = if (!current.guideUnlocked) getString(R.string.jigsaw_guide_buy_accessibility, NeutrinoRewards.JIGSAW_GUIDE_COST)
                else getString(if (current.guideVisible) R.string.jigsaw_hide_ghost else R.string.jigsaw_ghost)
            tooltipText = contentDescription
        }
    }
    private fun showToolsMenu(anchor: View) {
        val current = game ?: return
        PopupMenu(this, anchor).apply {
            if (!current.solved) {
                menu.add(0, 1, 0, R.string.jigsaw_edges).apply { isCheckable = true; isChecked = board?.filteringEdges == true }
                menu.add(0, 2, 1, if (current.layout == JigsawLayout.TABLE) R.string.jigsaw_spread else R.string.jigsaw_tidy)
            }
            menu.add(0, 3, 2, R.string.jigsaw_new_puzzle)
            setOnMenuItemClickListener {
                when (it.itemId) { 1 -> board?.toggleEdges(); 2 -> board?.tidy(); 3 -> showSetup() }
                updateTools(); true
            }
            show()
        }
    }

    /** Bought once for the whole game after a confirmation; afterwards the eye shows or hides it for free. */
    private fun requestGuide() {
        val current = game ?: return
        if (current.solved) return
        board?.cancelGesture()
        if (current.guideUnlocked) { board?.setGuideVisible(!current.guideVisible); return }
        guideSheet?.dismiss()
        val sheet = BottomSheetDialog(this)
        guideSheet = sheet
        val page = sheetPage(R.string.jigsaw_ghost)
        page.addView(text(R.string.jigsaw_guide_description, 15f).apply { setTextColor(palette.secondary); setPadding(0, dp(8), 0, dp(18)) })
        val funds = text(R.string.jigsaw_title, 14f).apply { setTextColor(palette.secondary) }
        page.addView(funds)
        val buy = button(R.string.jigsaw_start, R.drawable.ic_hint, primary = true) {}.apply {
            text = getString(R.string.jigsaw_guide_unlock, NeutrinoRewards.JIGSAW_GUIDE_COST)
        }
        fun updateFunds() {
            buy.isEnabled = repository.getBalance() >= NeutrinoRewards.JIGSAW_GUIDE_COST || repository.hasJigsawGuide(current.sessionId)
            buy.alpha = if (buy.isEnabled) 1f else .4f
            funds.text = getString(if (buy.isEnabled) R.string.jigsaw_guide_balance else R.string.jigsaw_guide_insufficient, repository.getBalance())
        }
        updateFunds()
        buy.setOnClickListener {
            if (game !== current || board == null) { sheet.dismiss(); return@setOnClickListener }
            when (repository.purchaseJigsawGuide(current.sessionId)) {
                NeutrinoRepository.GuidePurchase.UNLOCKED -> {
                    current.guideUnlocked = true
                    guideButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    board?.setGuideVisible(true); save(synchronous = true)
                    updateTools(); sheet.dismiss()
                }
                NeutrinoRepository.GuidePurchase.NOT_ENOUGH -> updateFunds()
                NeutrinoRepository.GuidePurchase.WRITE_FAILED -> funds.setText(R.string.jigsaw_guide_save_error)
            }
        }
        page.addView(buy, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16) })
        sheet.setOnDismissListener { if (guideSheet === sheet) guideSheet = null }
        sheet.setContentView(page); sheet.show()
    }
    private fun complete(celebrate: Boolean = false) {
        val current = game ?: return
        if (!current.solved) return
        stopClock()
        if (!current.rewardHandled) {
            // The receipt makes the reward idempotent, even if the app stops between two writes.
            val receipt = "jigsaw:${current.sessionId}"
            if (repository.addBalanceOnce(receipt, NeutrinoRewards.jigsaw(current.size.ordinal)) || repository.hasRewardReceipt(receipt)) {
                current.rewardHandled = true
            }
            store.markCompleted(current.imagePath); save(synchronous = true)
        }
        updateStatus(); updateTools()
        if (celebrate && !celebrationPlayed) {
            celebrationPlayed = true; board?.playVictory()
            status.announceForAccessibility(getString(R.string.jigsaw_well_done))
        }
    }
    private fun showRules() {
        ImmersiveAlertDialogBuilder(this).setTitle(R.string.jigsaw_title).setMessage(R.string.jigsaw_rules)
            .setPositiveButton(R.string.close, null).show()
    }
    private fun sheetPage(titleRes: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(28)); setBackgroundColor(palette.surface)
        addView(text(titleRes, 21f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) })
    }
    private fun fieldLabel(label: Int) = text(label, 13f).apply {
        typeface = Typeface.DEFAULT_BOLD; setTextColor(palette.secondary); setPadding(dp(3), dp(22), 0, dp(8))
    }
    private fun spinner() = ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(palette.accent) }
    /** A choice chip (size, layout, collection): filled with the accent once chosen, as in the other puzzles. */
    private fun choice(label: String, action: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = label; textSize = 14f; isAllCaps = false; minWidth = 0
        setPadding(dp(8), 0, dp(8), 0); cornerRadius = corner(14f); insetTop = dp(2); insetBottom = dp(2)
        styleChoice(this, false); setOnClickListener { action() }
    }
    private fun styleChoice(button: MaterialButton, selected: Boolean) {
        val foreground = if (selected) palette.onAccent else palette.text
        button.isSelected = selected
        button.setTextColor(foreground)
        button.backgroundTintList = ColorStateList.valueOf(if (selected) palette.accent else palette.raised)
        button.strokeColor = ColorStateList.valueOf(if (selected) palette.accent else palette.outline)
        button.strokeWidth = dp(1); button.iconTint = ColorStateList.valueOf(if (selected) foreground else palette.secondary)
    }
    /** Filled for the main action, raised with an outline otherwise. */
    private fun button(label: Int, iconRes: Int, primary: Boolean, action: () -> Unit) = MaterialButton(this).apply {
        setText(label); textSize = 15f; isAllCaps = false; cornerRadius = corner(16f); setIconResource(iconRes)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; insetTop = 0; insetBottom = 0
        val foreground = if (primary) palette.onAccent else palette.text
        backgroundTintList = ColorStateList.valueOf(if (primary) palette.accent else palette.raised)
        strokeColor = ColorStateList.valueOf(if (primary) palette.accent else palette.outline)
        strokeWidth = if (primary) 0 else dp(1)
        setTextColor(foreground); iconTint = ColorStateList.valueOf(foreground)
        elevation = 0f; stateListAnimator = null
        setOnClickListener { action() }
    }
    private fun headerIcon(drawable: Int, label: Int, tint: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable); imageTintList = ColorStateList.valueOf(tint)
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        setBackgroundResource(ripple.resourceId)
        contentDescription = getString(label); tooltipText = contentDescription; setOnClickListener { action() }
    }
    private fun icon(drawable: Int, label: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable); imageTintList = ColorStateList.valueOf(palette.secondary)
        scaleType = ImageView.ScaleType.CENTER_INSIDE; setPadding(dp(12), dp(12), dp(12), dp(12))
        background = RippleDrawable(ColorStateList.valueOf(palette.withAlpha(palette.accent, .20f)), null, rounded(palette.text, 24f))
        contentDescription = getString(label); tooltipText = contentDescription; setOnClickListener { action() }
    }
    private fun corner(radius: Float) = AppearanceStyle.corner(this, radius).toInt()
    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = AppearanceStyle.corner(this@JigsawActivity, radius)
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun text(label: Int, size: Float) = TextView(this).apply { setText(label); textSize = size; setTextColor(palette.text) }
    private fun dp(value: Int) = dp(value.toFloat())
    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f).toInt()
}
