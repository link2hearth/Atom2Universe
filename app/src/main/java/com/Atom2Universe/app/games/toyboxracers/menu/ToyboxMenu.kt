package com.Atom2Universe.app.games.toyboxracers.menu

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.editor.ActiveWorldKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorldStore
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.HousePlan
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.RoomThemes
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import java.io.File
import java.text.DateFormat
import java.util.concurrent.Executors

/** Ce que le menu a besoin de lire et de déclencher sur l'activité de jeu,
 * sans connaître ses champs privés. L'activité implémente cette interface
 * par de simples délégations vers son propre état. */
internal interface ToyboxMenuHost {
    val worldStore: ToyboxWorldStore
    fun dialogBuilder(): AlertDialog.Builder

    // État
    fun activeWorldKind(): ActiveWorldKind
    fun isEditorActive(): Boolean
    fun currentScene(): SceneChoice
    fun housePlan(): HousePlan
    fun currentCreationFile(): File?
    fun currentEditorWorldName(): String
    /** Un niveau est chargé et peut être repris tel quel. */
    fun levelInProgress(): Boolean
    /** Un niveau a déjà été joué lors d'une session précédente. */
    fun hasLastPlayed(): Boolean
    /** Nom du niveau en cours, ou du dernier niveau joué. */
    fun levelLabel(): String?
    fun difficulty(): RaceDifficulty
    /** Meilleur temps en secondes, 0 s'il n'y en a pas. */
    fun bestTime(scene: SceneChoice, difficulty: RaceDifficulty): Float
    fun raceResults(scene: SceneChoice, difficulty: RaceDifficulty): ToyboxRaceProgress.Results
    fun totalResults(): ToyboxRaceProgress.Results
    fun drivingOptions(): BooleanArray
    fun setDrivingOption(index: Int, enabled: Boolean)
    fun steeringResponse(): Int
    fun setSteeringResponse(index: Int)
    fun hasUnsavedChanges(): Boolean
    fun roomLabel(kind: RoomKind): String
    fun circuitLabel(kind: CircuitKind): String

    // Actions
    /** Le décor 3D derrière le menu ne tourne pas : inutile de le dessiner à 120 images/s. */
    fun setSceneLive(live: Boolean)
    fun resumeGame()
    fun restartRace()
    fun rescueCar()
    fun quitGame()
    fun setDifficulty(difficulty: RaceDifficulty)
    fun playClassic(scene: SceneChoice, race: Boolean)
    fun openCreation(world: ToyboxWorld, file: File?, edit: Boolean)
    fun continueLast()
    fun editClassicCopy(scene: SceneChoice)
    fun toggleEditing()
    fun saveCurrentCreation(): Boolean
    fun saveCurrentCreationAsNew(name: String): Boolean
    /** Le fichier de la création ouverte vient d'être renommé : l'activité doit suivre le nouveau. */
    fun creationRenamed(old: File, renamed: File, name: String)
    /** Le fichier de la création ouverte vient d'être supprimé : ce qui est en mémoire n'a plus de fichier. */
    fun creationDeleted(file: File)
    fun setHousePlan(plan: HousePlan)
}

/**
 * Menu de Toybox Racers : accueil, choix du circuit, atelier de création et
 * pause, tous dans le même habillage plein écran posé au-dessus du jeu.
 * Rien ici ne touche à la conduite : le menu ne fait que choisir un niveau
 * puis demande à l'activité de le lancer.
 */
internal class ToyboxMenu(private val activity: Activity, private val host: ToyboxMenuHost) {

    enum class Page { HOME, PLAY, WORKSHOP, PAUSE }

    private enum class Tab { CLASSIC, CREATIONS }

    /** Une case du catalogue : circuit classique ou création du joueur. */
    private sealed class Entry(val key: String) {
        class Classic(val room: RoomKind?, val scene: SceneChoice) : Entry(if (room == null) KEY_HOUSE else "circuit:${scene.circuit.name}") {
            val outlineKey = "${scene.room.name}:${scene.circuit.name}"
        }
        class Creation(val file: File, val world: ToyboxWorld, val outline: TrackOutline) : Entry("file:${file.name}")
    }

    val view = FrameLayout(activity).apply {
        visibility = View.GONE
        isClickable = true
        isFocusable = true
    }

    private val density = activity.resources.displayMetrics.density
    var page = Page.HOME
        private set
    private var tab = Tab.CLASSIC
    private var selectedKey: String? = null
    private var activeDialog: AlertDialog? = null

    /** Chaque reconstruction de page invalide les chargements en cours. */
    private var generation = 0
    private var creations: List<Entry.Creation>? = null
    private val outlines = HashMap<String, TrackOutline>()
    private val previewViews = HashMap<String, ToyboxTrackPreviewView>()
    private var detailPreview: ToyboxTrackPreviewView? = null
    private var detailPreviewKey: String? = null
    private var gridHost: LinearLayout? = null
    private var detailHost: LinearLayout? = null

    private fun str(resId: Int) = activity.getString(resId)
    private fun str(resId: Int, vararg args: Any) = activity.getString(resId, *args)
    private fun dp(value: Int) = (value * density).toInt()
    /** Un monde sans nom est un circuit libre : son nom se traduit à l'affichage. */
    private fun worldName(name: String) = name.ifBlank { str(R.string.toybox_menu_free_world_name) }

    fun isShowing(): Boolean = view.visibility == View.VISIBLE

    /** Ferme le menu et ses boîtes de dialogue (reprise du jeu, mise en pause ou destruction). */
    fun hide() {
        generation++
        dismissDialog()
        view.visibility = View.GONE
        view.removeAllViews()
        gridHost = null
        detailHost = null
        previewViews.clear()
    }

    fun dismissDialog() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    fun show(target: Page) {
        page = target
        generation++
        dismissDialog()
        host.setSceneLive(false)
        view.removeAllViews()
        previewViews.clear()
        gridHost = null
        detailHost = null
        view.visibility = View.VISIBLE
        when (target) {
            Page.HOME -> buildHome()
            Page.PLAY, Page.WORKSHOP -> buildBrowser(target)
            Page.PAUSE -> buildPause()
        }
    }

    /** Retour système : remonte d'un niveau, reprend la partie ou quitte. */
    fun handleBack(): Boolean {
        if (!isShowing()) return false
        when (page) {
            Page.PAUSE -> host.resumeGame()
            Page.PLAY, Page.WORKSHOP -> show(Page.HOME)
            Page.HOME -> if (host.levelInProgress()) show(Page.PAUSE) else quit()
        }
        return true
    }

    private fun quit() = leaveEditing { host.quitGame() }

    // ───────────────────────────── Accueil ─────────────────────────────

    private fun buildHome() {
        view.addView(MenuBackdrop(activity), match())
        val left = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        left.addView(label(str(R.string.toybox_racers_title), 44f, Color.WHITE, bold = true).apply {
            setShadowLayer(dp(6).toFloat(), 0f, dp(3).toFloat(), 0xAA3B4055.toInt())
        })
        left.addView(label(str(R.string.toybox_home_tagline), 17f, 0xFFFFE7A8.toInt()).apply {
            setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), 0xAA3B4055.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        val total = host.totalResults()
        if (total.races > 0) left.addView(label(str(R.string.toybox_progress_summary,
            total.races, total.podiums, total.wins), 13f, Color.WHITE).apply {
            setPadding(0, dp(18), dp(16), 0)
        })

        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun add(button: View) = buttons.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (host.levelInProgress()) {
            add(bigButton(str(R.string.toybox_home_resume), host.levelLabel(), COLOR_PINK) { host.resumeGame() })
        } else if (host.hasLastPlayed()) {
            add(bigButton(str(R.string.toybox_home_continue), host.levelLabel(), COLOR_PINK) { host.continueLast() })
        }
        add(bigButton(str(R.string.toybox_home_play), str(R.string.toybox_home_play_sub), COLOR_GREEN) { show(Page.PLAY) })
        add(bigButton(str(R.string.toybox_home_workshop), str(R.string.toybox_home_workshop_sub), COLOR_LAVENDER) { show(Page.WORKSHOP) })
        add(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(smallButton(str(R.string.toybox_driving_guide), COLOR_BLUE) { showDrivingGuide() },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
            addView(smallButton(str(R.string.toybox_settings), COLOR_BLUE) { showSettings() },
                LinearLayout.LayoutParams(0, dp(44), 1f))
        })
        add(bigButton(str(R.string.toybox_quit), null, COLOR_BLUE, compact = true) { quit() })

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(44), dp(20), dp(44), dp(20))
        }
        content.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(buttons, LinearLayout.LayoutParams(dp(300), -2))
        view.addView(ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content, FrameLayout.LayoutParams(-1, -1))
        }, match())
    }

    // ───────────────────────────── Pause ─────────────────────────────

    private fun buildPause() {
        view.addView(View(activity).apply { setBackgroundColor(0xB0161A28.toInt()) }, match())
        val custom = host.activeWorldKind() == ActiveWorldKind.CUSTOM
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(0xF03B4055.toInt(), 22f)
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        card.addView(label(str(R.string.toybox_pause), 22f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER })
        host.levelLabel()?.let {
            card.addView(label(it, 12f, 0xFFFFE7A8.toInt()).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(12))
            })
        }
        fun add(button: View) = card.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        fun row(vararg buttons: View) = add(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEachIndexed { index, button ->
                addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (index < buttons.lastIndex) rightMargin = dp(8) })
            }
        })
        add(bigButton(str(R.string.toybox_resume), null, COLOR_GREEN, compact = true) { host.resumeGame() })
        add(bigButton(str(R.string.toybox_restart), null, COLOR_BLUE, compact = true) { host.restartRace() })
        if (!host.isEditorActive()) add(bigButton(str(R.string.toybox_rescue_action),
            str(R.string.toybox_rescue_hint), COLOR_BLUE, compact = true) { host.rescueCar() })
        if (custom) {
            val toggle = if (host.isEditorActive()) R.string.toybox_menu_test_mode else R.string.toybox_menu_edit_mode
            add(bigButton(str(toggle), null, COLOR_LAVENDER, compact = true) { host.toggleEditing() })
            row(
                smallButton(str(R.string.toybox_menu_save), COLOR_BLUE) {
                    if (host.currentCreationFile() != null) {
                        if (host.saveCurrentCreation()) host.resumeGame()
                    } else promptName(host.currentEditorWorldName(), R.string.toybox_menu_name_prompt) { name ->
                        if (host.saveCurrentCreationAsNew(name)) {
                            Toast.makeText(activity, str(R.string.toybox_menu_creation_saved, name), Toast.LENGTH_SHORT).show()
                            host.resumeGame()
                        }
                    }
                },
                smallButton(str(R.string.toybox_menu_save_as_new), COLOR_BLUE) {
                    promptName(worldName(host.currentEditorWorldName()), R.string.toybox_menu_name_prompt) { name ->
                        if (host.saveCurrentCreationAsNew(name)) {
                            Toast.makeText(activity, str(R.string.toybox_menu_creation_saved, name), Toast.LENGTH_SHORT).show()
                            host.resumeGame()
                        }
                    }
                }
            )
        } else {
            add(bigButton(str(R.string.toybox_pause_edit_copy), null, COLOR_LAVENDER, compact = true) {
                host.editClassicCopy(host.currentScene())
            })
        }
        row(
            smallButton(str(R.string.toybox_pause_circuits), COLOR_BLUE) { show(Page.PLAY) },
            smallButton(str(R.string.toybox_pause_main_menu), COLOR_BLUE) { show(Page.HOME) }
        )
        row(
            smallButton(str(R.string.toybox_driving_guide), COLOR_BLUE) { showDrivingGuide() },
            smallButton(str(R.string.toybox_settings), COLOR_BLUE) { showSettings() }
        )
        view.addView(ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(card, FrameLayout.LayoutParams(dp(330), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(12); bottomMargin = dp(12)
            })
        }, match())
    }

    // ───────────────────── Choix du circuit et atelier ─────────────────────

    private fun buildBrowser(mode: Page) {
        val workshop = mode == Page.WORKSHOP
        view.addView(MenuBackdrop(activity), match())
        val screenDp = activity.resources.displayMetrics.widthPixels / density
        val detailWidth = dp((screenDp * 0.36f).coerceIn(250f, 340f).toInt())

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
        }
        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(smallButton("‹", COLOR_BLUE, textSize = 24f) { show(Page.HOME) }.apply {
            contentDescription = str(R.string.toybox_menu_back)
        }, LinearLayout.LayoutParams(dp(48), dp(44)).apply { rightMargin = dp(12) })
        topBar.addView(
            label(str(if (workshop) R.string.toybox_workshop_title else R.string.toybox_levels_title), 22f, Color.WHITE, bold = true)
                .apply { setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), 0xAA3B4055.toInt()) },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        if (!workshop) {
            topBar.addView(tabButton(Tab.CLASSIC, str(R.string.toybox_menu_classic_circuits)), LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(6) })
            topBar.addView(tabButton(Tab.CREATIONS, str(R.string.toybox_menu_my_creations_section)), LinearLayout.LayoutParams(-2, dp(38)))
        }
        column.addView(topBar, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(8) })

        val body = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val gridScroll = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(grid, FrameLayout.LayoutParams(-1, -2))
        }
        val detail = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(0xD83B4055.toInt(), 20f)
            setPadding(dp(14), dp(14), dp(14), dp(12))
        }
        val detailScroll = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(detail, FrameLayout.LayoutParams(-1, -2))
        }
        body.addView(gridScroll, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(14) })
        body.addView(detailScroll, LinearLayout.LayoutParams(detailWidth, -1))
        column.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        view.addView(column, match())
        gridHost = grid
        detailHost = detail

        // Colonnes : de quoi garder des cartes lisibles dans la place laissée au panneau.
        columns = ((activity.resources.displayMetrics.widthPixels - detailWidth - dp(18 + 18 + 14)) / dp(150)).coerceIn(1, 4)

        if (workshop || tab == Tab.CREATIONS) loadCreations()
        selectedKey = defaultSelection(workshop)
        refreshBrowser()
    }

    private var columns = 3

    private fun tabButton(target: Tab, text: String): Button {
        val active = tab == target
        return smallButton(text, if (active) COLOR_GREEN else COLOR_BLUE) {
            if (tab != target) {
                tab = target
                show(Page.PLAY)
            }
        }.apply {
            alpha = if (active) 1f else 0.7f
            setPadding(dp(14), 0, dp(14), 0)
        }
    }

    private fun defaultSelection(workshop: Boolean): String? {
        if (workshop) {
            val current = host.currentCreationFile()?.name
            return if (current != null) "file:$current" else null
        }
        if (tab == Tab.CREATIONS) {
            val current = host.currentCreationFile()?.name
            return if (current != null && host.activeWorldKind() == ActiveWorldKind.CUSTOM) "file:$current" else null
        }
        if (host.activeWorldKind() == ActiveWorldKind.LEGACY) {
            val scene = host.currentScene()
            return if (scene.circuit.usesHouseLayout) KEY_HOUSE else "circuit:${scene.circuit.name}"
        }
        return KEY_HOUSE
    }

    private fun classicEntries(): List<Entry.Classic> = buildList {
        val plan = host.housePlan()
        val destinationRooms = RoomKind.entries.filter { it.isDestination }.associateBy { it.defaultCircuit }
        // Une carte par tracé, y compris ceux absents des huit pièces du plan.
        // Les nouvelles destinations gardent leur décor et restent en tête de liste.
        val circuits = CircuitKind.entries.sortedBy {
            when {
                it.isDestinationCircuit -> 0
                it.usesHouseLayout -> 1
                else -> 2
            }
        }
        circuits.forEach { circuit ->
            val room = destinationRooms[circuit]
                ?: plan.rooms.firstOrNull { it.circuit == circuit }?.kind
                ?: when (circuit) {
                    CircuitKind.FURNITURE_TRAIL -> RoomKind.LIVING_ROOM
                    CircuitKind.WORKSHOP_EXPEDITION -> RoomKind.GARAGE
                    CircuitKind.HOUSE_GROUND_FLOOR -> RoomKind.BEDROOM
                    else -> plan.rooms[circuit.ordinal % plan.rooms.size].kind
                }
            add(Entry.Classic(room.takeUnless { circuit.usesHouseLayout }, SceneChoice(room, circuit)))
        }
    }

    private fun loadCreations() {
        val gen = generation
        creations = null
        worker.execute {
            val store = host.worldStore
            val loaded = runCatching {
                store.listCreations().mapNotNull { file ->
                    store.loadCreation(file)?.let { Entry.Creation(file, it, TrackOutline.of(it)) }
                }
            }.getOrDefault(emptyList())
            activity.runOnUiThread {
                if (gen != generation || !isShowing()) return@runOnUiThread
                creations = loaded
                if (loaded.none { it.key == selectedKey }) selectedKey = loaded.firstOrNull()?.key
                refreshBrowser()
            }
        }
    }

    private fun refreshBrowser() {
        val grid = gridHost ?: return
        val detail = detailHost ?: return
        grid.removeAllViews()
        previewViews.clear()
        val workshop = page == Page.WORKSHOP
        val cards = ArrayList<View>()
        var entries: List<Entry> = emptyList()

        if (workshop) {
            cards += actionCard("+", str(R.string.toybox_workshop_new), str(R.string.toybox_workshop_new_sub), COLOR_GREEN) { showNewCreation() }
            cards += actionCard("⧉", str(R.string.toybox_workshop_copy_classic), str(R.string.toybox_workshop_copy_classic_sub), COLOR_LAVENDER) { showCopyClassic() }
            entries = creations.orEmpty()
        } else if (tab == Tab.CLASSIC) {
            entries = classicEntries()
        } else {
            entries = creations.orEmpty()
        }
        entries.forEach { cards += entryCard(it) }

        val loadingCreations = (workshop || tab == Tab.CREATIONS) && creations == null
        val rows = cards.chunked(columns)
        rows.forEach { chunk ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { card ->
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            repeat(columns - chunk.size) {
                row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        if (!loadingCreations && entries.isEmpty()) {
            grid.addView(label(str(if (workshop) R.string.toybox_workshop_empty else R.string.toybox_menu_no_creations), 14f, Color.WHITE).apply {
                alpha = 0.85f
                setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xAA3B4055.toInt())
                setPadding(dp(8), dp(14), dp(8), dp(8))
            })
        }

        val selected = entries.firstOrNull { it.key == selectedKey }
        buildDetail(detail, selected)
        // Les contours classiques se calculent en arrière-plan, une fois par scène.
        entries.filterIsInstance<Entry.Classic>().forEach { entry -> requestClassicOutline(entry) }
    }

    private fun requestClassicOutline(entry: Entry.Classic) {
        val key = entry.outlineKey
        outlines[key]?.let { previewViews[entry.key]?.outline = it; return }
        val gen = generation
        worker.execute {
            val outline = runCatching { TrackOutline.classic(entry.scene) }.getOrNull() ?: return@execute
            activity.runOnUiThread {
                outlines[key] = outline
                if (gen != generation) return@runOnUiThread
                previewViews[entry.key]?.outline = outline
                if (detailPreviewKey == entry.key) detailPreview?.outline = outline
            }
        }
    }

    private fun preview(entry: Entry, heightDp: Int): ToyboxTrackPreviewView {
        val preview = ToyboxTrackPreviewView(activity)
        when (entry) {
            is Entry.Classic -> {
                preview.background = classicTint(entry)
                preview.roadColor = 0xFF5A6480.toInt()
                outlines[entry.outlineKey]?.let { preview.outline = it }
            }
            is Entry.Creation -> {
                preview.background = 0xFFB9D4E8.toInt()
                preview.roadColor = 0xFF6F7B91.toInt()
                preview.outline = entry.outline
            }
        }
        preview.layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp))
        return preview
    }

    private fun classicTint(entry: Entry.Classic): Int {
        val room = entry.room ?: return 0xFFF2D9A4.toInt()
        return 0xFF000000.toInt() or RoomThemes.theme(room).accent
    }

    private fun entryCard(entry: Entry): View {
        val selected = entry.key == selectedKey
        val title: String
        val subtitle: String
        when (entry) {
            is Entry.Classic -> {
                title = if (entry.room == null) str(R.string.toybox_house_mode) else host.circuitLabel(entry.scene.circuit)
                subtitle = if (entry.room == null) str(R.string.toybox_level_house_sub) else host.roomLabel(entry.room)
            }
            is Entry.Creation -> {
                title = worldName(entry.world.name)
                subtitle = str(R.string.toybox_menu_creation_details, DateFormat.getDateInstance(DateFormat.SHORT).format(entry.file.lastModified()), entry.world.trackSections.size)
            }
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(if (selected) 0xF05A6A8C.toInt() else 0xC83B4055.toInt(), 16f,
                stroke = if (selected) 0xFFFFE7A8.toInt() else 0x44FFFFFF, strokeDp = if (selected) 3 else 1)
            setPadding(dp(6), dp(6), dp(6), dp(8))
            ripple(16f)
            setOnClickListener {
                selectedKey = entry.key
                refreshBrowser()
            }
            isSelected = selected
            contentDescription = str(R.string.toybox_level_accessibility, title, subtitle)
        }
        val preview = preview(entry, 82)
        previewViews[entry.key] = preview
        card.addView(preview)
        card.addView(label(title, 13f, Color.WHITE, bold = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), dp(6), dp(4), 0)
        })
        card.addView(label(subtitle, 11f, 0xFFFFE7A8.toInt()).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(4), 0)
        })
        if (entry is Entry.Classic) {
            val results = host.raceResults(entry.scene, host.difficulty())
            if (results.bestPosition > 0) card.addView(label(str(R.string.toybox_progress_best_place, results.bestPosition),
                11f, 0xFFD3F2DF.toInt()).apply { setPadding(dp(4), dp(3), dp(4), 0) })
        }
        return card
    }

    private fun actionCard(glyph: String, title: String, subtitle: String, color: Int, onClick: () -> Unit): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(0xC83B4055.toInt(), 16f, stroke = 0x88FFFFFF.toInt(), strokeDp = 2)
            setPadding(dp(6), dp(6), dp(6), dp(8))
            ripple(16f)
            setOnClickListener { onClick() }
        }
        card.addView(label(glyph, 38f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            background = roundedBackground(color, 14f, stroke = 0x00000000, strokeDp = 0)
        }, LinearLayout.LayoutParams(-1, dp(82)))
        card.addView(label(title, 13f, Color.WHITE, bold = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), dp(6), dp(4), 0)
        })
        card.addView(label(subtitle, 11f, 0xFFFFE7A8.toInt()).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(4), 0)
        })
        return card
    }

    // ───────────────────────────── Panneau de détail ─────────────────────────────

    private fun buildDetail(detail: LinearLayout, entry: Entry?) {
        detail.removeAllViews()
        if (entry == null) {
            detail.gravity = Gravity.CENTER
            detail.addView(label(str(R.string.toybox_level_pick), 14f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                alpha = 0.8f
                setPadding(0, dp(40), 0, dp(40))
            })
            return
        }
        detail.gravity = Gravity.TOP
        detailPreview = preview(entry, 96)
        detailPreviewKey = entry.key
        detail.addView(detailPreview, LinearLayout.LayoutParams(-1, dp(96)).apply { bottomMargin = dp(10) })
        fun add(view: View, heightDp: Int = -2, bottomDp: Int = 8) =
            detail.addView(view, LinearLayout.LayoutParams(-1, if (heightDp > 0) dp(heightDp) else -2).apply { bottomMargin = dp(bottomDp) })

        when (entry) {
            is Entry.Classic -> {
                val house = entry.room == null
                detail.addView(label(if (house) str(R.string.toybox_house_mode) else host.circuitLabel(entry.scene.circuit), 18f, Color.WHITE, bold = true))
                detail.addView(label(if (house) str(R.string.toybox_level_house_sub) else host.roomLabel(entry.room!!), 12f, 0xFFFFE7A8.toInt()).apply {
                    setPadding(0, 0, 0, dp(10))
                })
                add(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(bigButton(str(R.string.toybox_level_race), null, COLOR_PINK, compact = true) {
                        leaveEditing { host.playClassic(entry.scene, race = true) }
                    }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
                    addView(bigButton(str(R.string.toybox_free), null, COLOR_BLUE, compact = true) {
                        leaveEditing { host.playClassic(entry.scene, race = false) }
                    }, LinearLayout.LayoutParams(0, dp(46), 1f))
                }, 46, 10)
                // La difficulté se règle ici : elle décide des adversaires et du record affiché.
                val chips = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                RaceDifficulty.entries.forEachIndexed { index, difficulty ->
                    val active = difficulty == host.difficulty()
                    chips.addView(smallButton(str(difficulty.label), if (active) COLOR_GREEN else COLOR_BLUE, textSize = 12f) {
                        host.setDifficulty(difficulty)
                        buildDetail(detail, entry)
                    }.apply {
                        alpha = if (active) 1f else 0.65f
                        setPadding(dp(2), 0, dp(2), 0)
                    }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { if (index < RaceDifficulty.entries.lastIndex) rightMargin = dp(6) })
                }
                add(chips, 38, 8)
                add(label(str(when (host.difficulty()) {
                    RaceDifficulty.RELAX -> R.string.toybox_difficulty_relax_description
                    RaceDifficulty.ARCADE -> R.string.toybox_difficulty_arcade_description
                    RaceDifficulty.CHAMPION -> R.string.toybox_difficulty_champion_description
                }), 11f, 0xFFD7E2F0.toInt()), bottomDp = 8)
                val best = host.bestTime(entry.scene, host.difficulty())
                add(label(if (best > 0f) str(R.string.toybox_result_best, formatTime(best)) else str(R.string.toybox_level_no_record), 13f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    alpha = 0.9f
                }, bottomDp = 10)
                val results = host.raceResults(entry.scene, host.difficulty())
                if (results.races > 0) add(label(str(R.string.toybox_progress_summary,
                    results.races, results.podiums, results.wins), 11f, 0xFFD3F2DF.toInt()), bottomDp = 10)
                add(label(str(R.string.toybox_race_rules, com.Atom2Universe.app.games.toyboxracers.game.RaceSession.TOTAL_LAPS),
                    11f, 0xFFE3E9F5.toInt()), bottomDp = 10)
                if (house) {
                    add(smallButton(str(R.string.toybox_level_seed_chip, host.housePlan().seed), COLOR_LAVENDER, textSize = 13f) { showSeedPicker() }, 40, 0)
                }
                entry.room?.takeIf { it.isDestination }?.let { room ->
                    add(label(str(when (room) {
                        RoomKind.MINE -> R.string.toybox_world_mine_description
                        RoomKind.LABORATORY -> R.string.toybox_world_laboratory_description
                        RoomKind.RESTAURANT -> R.string.toybox_world_restaurant_description
                        else -> R.string.toybox_world_neon_description
                    }), 12f, 0xFFE3E9F5.toInt()).apply { setPadding(0, dp(12), 0, 0) }, bottomDp = 0)
                }
            }
            is Entry.Creation -> {
                detail.addView(label(worldName(entry.world.name), 18f, Color.WHITE, bold = true).apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(entry.file.lastModified())
                detail.addView(label(str(R.string.toybox_menu_creation_details, stamp, entry.world.trackSections.size), 12f, 0xFFFFE7A8.toInt()).apply {
                    setPadding(0, 0, 0, dp(12))
                })
                if (page == Page.WORKSHOP) {
                    add(bigButton(str(R.string.toybox_menu_edit_mode), null, COLOR_LAVENDER, compact = true) {
                        leaveEditing(entry.file) { host.openCreation(entry.world, entry.file, edit = true) }
                    }, 50, 8)
                    add(bigButton(str(R.string.toybox_menu_test_mode), null, COLOR_GREEN, compact = true) {
                        leaveEditing(entry.file) { host.openCreation(entry.world, entry.file, edit = false) }
                    }, 46, 8)
                    add(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        val actions = listOf(
                            str(R.string.toybox_menu_duplicate) to { duplicate(entry) },
                            str(R.string.toybox_menu_rename) to { rename(entry) },
                            str(R.string.toybox_menu_delete) to { confirmDelete(entry) }
                        )
                        actions.forEachIndexed { index, (text, action) ->
                            addView(smallButton(text, if (index == 2) COLOR_RED else COLOR_BLUE, textSize = 11f) { action() }.apply {
                                setPadding(dp(2), 0, dp(2), 0)
                            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { if (index < actions.lastIndex) rightMargin = dp(6) })
                        }
                    }, 38, 0)
                } else {
                    add(bigButton(str(R.string.toybox_level_drive), null, COLOR_GREEN, compact = true) {
                        leaveEditing(entry.file) { host.openCreation(entry.world, entry.file, edit = false) }
                    }, 50, 8)
                    add(bigButton(str(R.string.toybox_menu_edit_mode), null, COLOR_LAVENDER, compact = true) {
                        leaveEditing(entry.file) { host.openCreation(entry.world, entry.file, edit = true) }
                    }, 46, 0)
                }
            }
        }
    }

    private fun formatTime(seconds: Float): String = formatToyboxTime(activity, seconds)

    // ───────────────────────────── Dialogues ─────────────────────────────

    private fun AlertDialog.trackAndShow(): AlertDialog {
        activeDialog = this
        show()
        return this
    }

    private fun showDrivingGuide() {
        host.dialogBuilder()
            .setTitle(R.string.toybox_driving_guide)
            .setMessage(R.string.toybox_driving_guide_body)
            .setPositiveButton(android.R.string.ok, null)
            .create().trackAndShow()
    }

    private fun showSettings() {
        val labels = arrayOf(str(R.string.toybox_setting_haptics), str(R.string.toybox_setting_minimap),
            str(R.string.toybox_setting_wide_camera), str(R.string.toybox_setting_reduced_motion),
            str(R.string.toybox_setting_sound))
        host.dialogBuilder()
            .setTitle(R.string.toybox_settings)
            .setMultiChoiceItems(labels, host.drivingOptions()) { _, index, enabled -> host.setDrivingOption(index, enabled) }
            .setNeutralButton(R.string.toybox_setting_steering) { _, _ -> showSteeringSettings() }
            .setPositiveButton(android.R.string.ok, null)
            .create().trackAndShow()
    }

    private fun showSteeringSettings() {
        val labels = arrayOf(str(R.string.toybox_steering_smooth), str(R.string.toybox_steering_balanced),
            str(R.string.toybox_steering_reactive))
        host.dialogBuilder()
            .setTitle(R.string.toybox_setting_steering)
            .setSingleChoiceItems(labels, host.steeringResponse()) { _, index -> host.setSteeringResponse(index) }
            .setPositiveButton(android.R.string.ok, null)
            .create().trackAndShow()
    }

    /** Quitter un circuit en cours d'édition ne perd jamais de travail sans le dire. */
    private fun leaveEditing(target: File? = null, proceed: () -> Unit) {
        val sameFile = target != null && target.name == host.currentCreationFile()?.name
        if (!host.hasUnsavedChanges() || sameFile) {
            proceed()
            return
        }
        host.dialogBuilder()
            .setTitle(R.string.toybox_unsaved_title)
            .setMessage(str(R.string.toybox_unsaved_message, worldName(host.currentEditorWorldName())))
            .setPositiveButton(R.string.toybox_menu_save) { _, _ ->
                if (host.currentCreationFile() != null) {
                    if (host.saveCurrentCreation()) proceed()
                } else promptName(worldName(host.currentEditorWorldName()), R.string.toybox_menu_name_prompt) { name ->
                    if (host.saveCurrentCreationAsNew(name)) proceed()
                }
            }
            .setNegativeButton(R.string.toybox_unsaved_discard) { _, _ -> proceed() }
            .setNeutralButton(android.R.string.cancel, null)
            .create()
            .trackAndShow()
    }

    private fun promptName(initial: String, title: Int, accept: (String) -> Unit) {
        val builder = host.dialogBuilder()
        val input = EditText(builder.context).apply {
            setText(initial)
            selectAll()
            setSingleLine()
        }
        val dialog = builder
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        activeDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                accept(input.text.toString().trim().ifBlank { str(R.string.toybox_menu_default_name) })
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showNewCreation() {
        val labels = arrayOf(str(R.string.toybox_workshop_template_starter), str(R.string.toybox_workshop_template_empty))
        host.dialogBuilder()
            .setTitle(R.string.toybox_workshop_template_title)
            .setItems(labels) { _, which ->
                promptName(str(R.string.toybox_menu_default_name), R.string.toybox_menu_name_prompt) { name ->
                    val base = ToyboxWorld(name = name)
                    val world = if (which == 1) base.copy(trackSections = emptyList()) else base
                    // Résoudre le brouillon actuel AVANT que saveCreation n'écrive le nouvel autosave.
                    leaveEditing {
                        val file = runCatching { host.worldStore.saveCreation(world, name) }.getOrNull()
                        if (file != null) host.openCreation(world, file, edit = true)
                        else Toast.makeText(activity, R.string.toybox_save_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .trackAndShow()
    }

    private fun showCopyClassic() {
        val entries = classicEntries()
        val labels = entries.map {
            if (it.room == null) str(R.string.toybox_house_mode)
            else host.roomLabel(it.room) + " · " + host.circuitLabel(it.scene.circuit)
        }
        host.dialogBuilder()
            .setTitle(R.string.toybox_workshop_copy_classic)
            .setItems(labels.toTypedArray()) { _, which ->
                leaveEditing { host.editClassicCopy(entries[which].scene) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .trackAndShow()
    }

    private fun duplicate(entry: Entry.Creation) {
        host.worldStore.duplicateCreation(entry.file) { str(R.string.toybox_menu_copy_name, worldName(it)) }
        Toast.makeText(activity, str(R.string.toybox_menu_duplicated), Toast.LENGTH_SHORT).show()
        reloadCreations()
    }

    private fun rename(entry: Entry.Creation) {
        promptName(worldName(entry.world.name), R.string.toybox_menu_rename) { name ->
            host.worldStore.renameCreation(entry.file, name)?.let {
                selectedKey = "file:${it.name}"
                host.creationRenamed(entry.file, it, name)
            }
            Toast.makeText(activity, str(R.string.toybox_menu_renamed), Toast.LENGTH_SHORT).show()
            reloadCreations()
        }
    }

    private fun confirmDelete(entry: Entry.Creation) {
        val name = worldName(entry.world.name)
        host.dialogBuilder()
            .setTitle(R.string.toybox_menu_delete)
            .setMessage(str(R.string.toybox_menu_delete_confirm, name))
            .setPositiveButton(R.string.toybox_menu_delete) { _, _ ->
                host.worldStore.deleteCreation(entry.file)
                host.creationDeleted(entry.file)
                Toast.makeText(activity, str(R.string.toybox_menu_deleted), Toast.LENGTH_SHORT).show()
                selectedKey = null
                reloadCreations()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .trackAndShow()
    }

    private fun reloadCreations() {
        generation++
        loadCreations()
        refreshBrowser()
    }

    private fun showSeedPicker() {
        val builder = host.dialogBuilder()
        val input = EditText(builder.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(host.housePlan().seed.toString())
            selectAll()
            contentDescription = str(R.string.toybox_house_seed)
        }
        val dialog = builder
            .setTitle(R.string.toybox_house_seed)
            .setMessage(R.string.toybox_house_seed_help)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        activeDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val seed = input.text.toString().trim().toLongOrNull()
                if (seed == null) {
                    input.error = str(R.string.toybox_house_seed_invalid)
                    return@setOnClickListener
                }
                host.setHousePlan(HousePlan.generate(seed))
                dialog.dismiss()
                generation++
                refreshBrowser()
            }
        }
        dialog.show()
    }

    // ───────────────────────────── Fabrique de vues ─────────────────────────────

    private fun match() = FrameLayout.LayoutParams(-1, -1)

    private fun roundedBackground(color: Int, radiusDp: Float, stroke: Int = 0x66FFFFFF, strokeDp: Int = 2) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
            if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
        }

    /** Retour visuel au toucher : un voile clair qui suit les coins arrondis. */
    private fun View.ripple(radiusDp: Float) {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusDp * density
        }
        foreground = RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), null, mask)
        isClickable = true
        isFocusable = true
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(activity).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    private fun smallButton(text: String, color: Int, textSize: Float = 14f, onClick: () -> Unit) = Button(activity).apply {
        this.text = text
        this.textSize = textSize
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        includeFontPadding = false
        maxLines = 1
        stateListAnimator = null
        minWidth = 0
        minHeight = 0
        background = roundedBackground(color, 14f)
        setPadding(dp(10), 0, dp(10), 0)
        ripple(14f)
        setOnClickListener { onClick() }
    }

    /** Gros bouton d'accueil : un titre et, en dessous, ce qu'il ouvre. */
    private fun bigButton(title: String, sub: String?, color: Int, compact: Boolean = false, onClick: () -> Unit): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(color, 18f)
            setPadding(dp(14), if (compact) dp(6) else dp(12), dp(14), if (compact) dp(6) else dp(12))
            minimumHeight = dp(if (compact) 44 else 64)
            ripple(18f)
            setOnClickListener { onClick() }
        }
        box.addView(label(title, if (compact) 15f else 20f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        })
        if (sub != null) {
            box.addView(label(sub, 12f, 0xFFFFF3D0.toInt()).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            })
        }
        return box
    }

    companion object {
        private const val KEY_HOUSE = "house"
        private const val COLOR_PINK = 0xF0E26F82.toInt()
        private const val COLOR_GREEN = 0xF04B8F6E.toInt()
        private const val COLOR_LAVENDER = 0xF0735D91.toInt()
        private const val COLOR_BLUE = 0xE04B617A.toInt()
        private const val COLOR_RED = 0xE09A4B4B.toInt()
        /** Un seul fil pour les calculs de contours : ils s'enchaînent sans se disputer le processeur. */
        private val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "toybox-menu").apply { isDaemon = true }
        }
    }
}

/** Fond commun des écrans : ciel pastel, collines douces et un ruban de route en travers. */
private class MenuBackdrop(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF7FB9E0.toInt(), 0xFFF3DDBB.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        paint.style = Paint.Style.FILL
        paint.color = 0x33FFFFFF
        canvas.drawCircle(w * 0.12f, h * 0.2f, h * 0.22f, paint)
        canvas.drawCircle(w * 0.2f, h * 0.27f, h * 0.15f, paint)
        canvas.drawCircle(w * 0.82f, h * 0.12f, h * 0.17f, paint)

        // Ruban de route qui traverse l'écran en diagonale, avec son pointillé central.
        path.rewind()
        path.moveTo(-w * 0.1f, h * 0.92f)
        path.cubicTo(w * 0.3f, h * 0.55f, w * 0.55f, h * 1.05f, w * 1.1f, h * 0.55f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = h * 0.2f
        paint.color = 0x553B4055
        canvas.drawPath(path, paint)
        paint.strokeWidth = h * 0.012f
        paint.color = 0x88FFFFFF.toInt()
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(h * 0.05f, h * 0.045f), 0f)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
    }
}
