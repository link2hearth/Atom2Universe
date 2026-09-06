package com.Atom2Universe.app.games.toyboxracers.menu

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.editor.ActiveWorldKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorldStore
import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.HousePlan
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Ce que le menu unifié a besoin de lire et de déclencher sur l'activité de
 * jeu, sans connaître ses champs privés. L'activité implémente cette
 * interface par de simples délégations vers son propre état. */
internal interface ToyboxWorldMenuHost {
    val worldStore: ToyboxWorldStore
    fun dialogBuilder(): AlertDialog.Builder
    fun activeWorldKind(): ActiveWorldKind
    fun isEditorActive(): Boolean
    fun currentScene(): SceneChoice
    fun housePlan(): HousePlan
    fun currentCreationFile(): File?
    fun currentEditorWorldName(): String
    fun roomLabel(kind: RoomKind): String
    fun circuitLabel(kind: CircuitKind): String
    fun sceneForRoom(kind: RoomKind): SceneChoice

    fun resumeGame()
    fun restartRace()
    fun quitGame()
    fun setEditing(editing: Boolean)
    fun loadCustomWorld(world: ToyboxWorld, sourceFile: File?)
    fun loadLegacyScene(scene: SceneChoice)
    fun editLegacySceneAsNewCreation()
    fun setHousePlan(plan: HousePlan)
    fun saveCurrentCreation()
    fun saveCurrentCreationAsNew(name: String)
}

/**
 * Menu pause unique de Toybox Racers : fusionne l'ancien menu de course et
 * l'ancien menu d'édition. Un seul monde est actif à la fois
 * ([ActiveWorldKind]) — ce menu bascule Éditer/Tester, charge un monde
 * (intégré, circuit classique, ou création du joueur) et gère la sauvegarde
 * en copie (jamais d'écrasement silencieux d'une création ouverte pour
 * édition).
 */
internal class ToyboxWorldMenu(private val activity: Activity, private val host: ToyboxWorldMenuHost) {

    /** Une seule boîte de dialogue de ce menu est jamais affichée à la fois :
     * un nouvel écran remplace toujours le précédent dans ce même suivi. */
    private var activeDialog: AlertDialog? = null

    private fun str(resId: Int) = activity.getString(resId)
    private fun str(resId: Int, vararg args: Any) = activity.getString(resId, *args)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun rowBackground() = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(0xAA4B617A.toInt())
    }

    /** Vrai tant qu'une des boîtes de ce menu est affichée — sert de garde
     * contre une double ouverture (bouton pause pressé deux fois, etc.). */
    fun isShowing(): Boolean = activeDialog?.isShowing == true

    /** Ferme la boîte affichée par ce menu, s'il y en a une (appelé quand
     * l'activité se met en pause ou se détruit). */
    fun dismiss() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun AlertDialog.trackAndShow(): AlertDialog {
        activeDialog = this
        show()
        return this
    }

    fun showPause() {
        val kind = host.activeWorldKind()
        val rows = buildList {
            add(str(R.string.toybox_resume) to { host.resumeGame() })
            add(str(R.string.toybox_restart) to { host.restartRace() })
            val label = if (host.isEditorActive()) str(R.string.toybox_menu_test_mode) else str(R.string.toybox_menu_edit_mode)
            add(label to {
                if (kind == ActiveWorldKind.LEGACY && !host.isEditorActive()) host.editLegacySceneAsNewCreation()
                else host.setEditing(!host.isEditorActive())
            })
            add(str(R.string.toybox_menu_load) to { showLoad() })
            if (kind == ActiveWorldKind.CUSTOM) {
                add(str(R.string.toybox_menu_save) to { showSaveOptions() })
            }
            add(str(R.string.toybox_quit) to { host.quitGame() })
        }
        host.dialogBuilder()
            .setTitle(R.string.toybox_pause)
            .setItems(rows.map { it.first }.toTypedArray()) { _, which -> rows[which].second() }
            .setOnCancelListener { host.resumeGame() }
            .create()
            .trackAndShow()
    }

    private fun showSaveOptions() {
        host.dialogBuilder()
            .setTitle(R.string.toybox_menu_save)
            .setItems(arrayOf(str(R.string.toybox_menu_save), str(R.string.toybox_menu_save_as_new))) { _, which ->
                when (which) {
                    0 -> if (host.currentCreationFile() != null) {
                        host.saveCurrentCreation()
                        host.resumeGame()
                    } else {
                        showNamePrompt()
                    }
                    1 -> showNamePrompt()
                }
            }
            .setNegativeButton(str(R.string.toybox_menu_back)) { _, _ -> showPause() }
            .setOnCancelListener { host.resumeGame() }
            .create()
            .trackAndShow()
    }

    private fun showNamePrompt() {
        val builder = host.dialogBuilder()
        val input = EditText(builder.context).apply {
            setText(host.currentEditorWorldName())
            selectAll()
        }
        val dialog = builder
            .setTitle(R.string.toybox_menu_name_prompt)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> host.resumeGame() }
            .setOnCancelListener { host.resumeGame() }
            .create()
        activeDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim().ifBlank { str(R.string.toybox_menu_default_name) }
                host.saveCurrentCreationAsNew(name)
                Toast.makeText(activity, str(R.string.toybox_menu_creation_saved, name), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                host.resumeGame()
            }
        }
        dialog.show()
    }

    private fun showLoad() {
        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
        }
        scroll.addView(list)
        val dialog = host.dialogBuilder()
            .setTitle(R.string.toybox_menu_load)
            .setView(scroll)
            .setNegativeButton(str(R.string.toybox_menu_back)) { _, _ -> showPause() }
            .setOnCancelListener { host.resumeGame() }
            .create()
        activeDialog = dialog

        list.addView(sectionHeader(str(R.string.toybox_menu_worlds_section)))
        ToyboxWorld.builtInWorlds().forEach { world ->
            list.addView(loadRow(world.name, str(R.string.toybox_menu_blocks_count, world.volumes.size)) {
                dialog.dismiss()
                host.loadCustomWorld(world, null)
            })
        }
        list.addView(loadRow(str(R.string.toybox_house_mode), str(R.string.toybox_menu_classic_circuits)) {
            dialog.dismiss()
            host.loadLegacyScene(SceneChoice(RoomKind.BEDROOM, CircuitKind.HOUSE_GROUND_FLOOR))
        })
        list.addView(loadRow(str(R.string.toybox_menu_classic_circuits) + " ▸", null) {
            dialog.dismiss()
            showLegacyRoomPicker()
        })

        list.addView(sectionHeader(str(R.string.toybox_menu_my_creations_section)))
        val creations = host.worldStore.listCreations()
        if (creations.isEmpty()) {
            list.addView(TextView(activity).apply {
                text = str(R.string.toybox_menu_no_creations)
                setTextColor(Color.WHITE)
                alpha = 0.7f
                textSize = 12f
                setPadding(dp(4), dp(4), dp(4), dp(8))
            })
        } else {
            val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            creations.forEach { file ->
                list.addView(loadRow(file.nameWithoutExtension, stamp.format(file.lastModified())) {
                    dialog.dismiss()
                    val world = host.worldStore.loadCreation(file)
                    if (world == null) {
                        Toast.makeText(activity, str(R.string.toybox_menu_no_creations), Toast.LENGTH_SHORT).show()
                        host.resumeGame()
                    } else {
                        host.loadCustomWorld(world, file)
                    }
                })
            }
        }
        dialog.show()
    }

    private fun sectionHeader(text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        textSize = 13f
        setPadding(dp(4), dp(10), dp(4), dp(6))
    }

    private fun loadRow(label: String, sub: String?, onClick: () -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rowBackground()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(activity).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        if (sub != null) {
            row.addView(TextView(activity).apply {
                text = sub
                setTextColor(0xFFFFE7A8.toInt())
                textSize = 11f
            })
        }
        return LinearLayout(activity).apply {
            addView(row, LinearLayout.LayoutParams(-1, -2))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
        }
    }

    private fun showLegacyRoomPicker() {
        val plan = host.housePlan()
        val rooms = plan.rooms.map { it.kind }
        val changeCircuitLabel = str(R.string.toybox_change_circuit) + " · " + host.roomLabel(host.currentScene().room) + " ▸"
        val labels = rooms.map { host.roomLabel(it) + " · " + host.circuitLabel(host.sceneForRoom(it).circuit) } + changeCircuitLabel
        host.dialogBuilder()
            .setTitle(str(R.string.toybox_change_room) + " · " + plan.seed)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < rooms.size) host.loadLegacyScene(host.sceneForRoom(rooms[which]))
                else showLegacyCircuitPicker()
            }
            .setNeutralButton(R.string.toybox_house_seed) { _, _ ->
                Handler(Looper.getMainLooper()).post { showLegacySeedPicker() }
            }
            .setNegativeButton(str(R.string.toybox_menu_back)) { _, _ -> showLoad() }
            .setOnCancelListener { host.resumeGame() }
            .create()
            .trackAndShow()
    }

    private fun showLegacySeedPicker() {
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
            .setNegativeButton(android.R.string.cancel) { _, _ -> host.resumeGame() }
            .setOnCancelListener { host.resumeGame() }
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
                host.resumeGame()
            }
        }
        dialog.show()
    }

    private fun showLegacyCircuitPicker() {
        val scene = host.currentScene()
        val pickable = CircuitKind.entries.filterNot { it.usesHouseLayout }
        host.dialogBuilder()
            .setTitle(str(R.string.toybox_change_circuit) + " · " + host.roomLabel(scene.room))
            .setSingleChoiceItems(
                pickable.map { host.circuitLabel(it) }.toTypedArray(),
                pickable.indexOf(scene.circuit)
            ) { _, which ->
                host.loadLegacyScene(scene.copy(circuit = pickable[which]))
            }
            .setNegativeButton(str(R.string.toybox_menu_back)) { _, _ -> showLegacyRoomPicker() }
            .setOnCancelListener { host.resumeGame() }
            .create()
            .trackAndShow()
    }
}
