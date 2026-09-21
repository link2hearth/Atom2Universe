package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.content.res.ColorStateList
import android.widget.TextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.games.roguelike.EquipSlot
import com.Atom2Universe.app.games.roguelike.Archetype

/** Échantillon jetable : aucune instance du jeu, aucun accès aux sauvegardes. */
class DungeonArtDemoActivity : ThemedActivity() {
    private lateinit var scene: DungeonArtDemoView
    private val controls = mutableListOf<Button>()
    private var capturedGesture = false

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (::scene.isInitialized && (scene.inputLocked || capturedGesture)) {
            capturedGesture = event.actionMasked != android.view.MotionEvent.ACTION_UP &&
                event.actionMasked != android.view.MotionEvent.ACTION_CANCEL
            scene.gestureTouch(event)
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 21, 35))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        scene = DungeonArtDemoView(this)
        val arena = FrameLayout(this)
        arena.addView(scene, FrameLayout.LayoutParams(-1, -1))
        arena.addView(button(R.string.dungeon_demo_back) { finish() },
            FrameLayout.LayoutParams(dp(80), dp(48), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(6), dp(6), dp(6), 0)
            })
        root.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))

        // Une seule bannière pour les PV, les sorts et les réglages de l'échantillon.
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(0xFF151F32.toInt())
                setStroke(dp(1).coerceAtLeast(1), 0xFF8D7954.toInt())
            }
        }
        root.addView(banner, LinearLayout.LayoutParams(-1, -2))
        val healthRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val familyButton = button(Archetype.WARRIOR.labelRes) { chooseFamily() }.apply {
            textSize = 13f
            contentDescription = getString(R.string.dungeon_demo_choose_family)
        }
        healthRow.addView(familyButton, LinearLayout.LayoutParams(dp(124), dp(48)).apply {
            setMargins(0, 0, dp(10), 0)
        })
        val health = FrameLayout(this)
        val heroProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 86
            progressTintList = ColorStateList.valueOf(0xFF509886.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(0xFF0D1524.toInt())
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        health.addView(heroProgress, FrameLayout.LayoutParams(-1, dp(18), Gravity.CENTER))
        val heroHealthText = TextView(this).apply {
            text = getString(R.string.dungeon_demo_health, 86, 100)
            setTextColor(Color.BLACK)
            textSize = 12f
            gravity = Gravity.CENTER
            setShadowLayer(2f, 0f, 1f, Color.WHITE)
        }
        health.addView(heroHealthText, FrameLayout.LayoutParams(-1, -1))
        scene.onHeroHealthChanged = { hp ->
            heroProgress.progress = hp
            heroHealthText.text = getString(R.string.dungeon_demo_health, hp, 100)
        }
        healthRow.addView(health, LinearLayout.LayoutParams(0, dp(28), 1f))
        banner.addView(healthRow)

        val equipmentRow = LinearLayout(this)
        val equipmentButtons = DungeonDemoSprites.slots.associateWith { slot ->
            button(slot.labelRes) { editEquipment(slot) }.apply {
                textSize = 11f
                equipmentRow.addView(this, LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                })
            }
        }
        fun refreshEquipment() {
            familyButton.setText(if (scene.gear(EquipSlot.CHEST).equipped) scene.gear(EquipSlot.CHEST).family.labelRes else R.string.dungeon_demo_civilian)
            equipmentButtons.forEach { (slot, button) ->
                val bitmap = scene.gearBitmap(slot)
                val icon = BitmapDrawable(resources, bitmap).apply {
                    isFilterBitmap = false
                    val scale = dp(26).toFloat() / maxOf(bitmap.width, bitmap.height)
                    setBounds(0, 0, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
                }
                button.setCompoundDrawables(null, icon, null, null)
                button.contentDescription = getString(R.string.dungeon_demo_piece_description,
                    getString(slot.labelRes), getString(if (scene.gear(slot).equipped) scene.gear(slot).family.labelRes else R.string.dungeon_demo_unequipped),
                    getString(if (scene.gear(slot).isotope) R.string.dungeon_demo_isotope else R.string.dungeon_demo_standard))
            }
        }
        scene.onEquipmentChanged = { refreshEquipment() }
        refreshEquipment()

        val actions = LinearLayout(this)
        val attack = button(R.string.dungeon_demo_attack) { scene.play(DungeonArtDemoView.Action.ATTACK) }
        val ice = button(R.string.dungeon_demo_ice) { scene.play(DungeonArtDemoView.Action.ICE) }
        val shatter = button(R.string.dungeon_demo_shatter) { scene.play(DungeonArtDemoView.Action.SHATTER) }
        listOf(attack, ice, shatter).forEach { actions.addView(it, cell()) }
        banner.addView(actions)
        banner.addView(equipmentRow)
        val options = LinearLayout(this)
        val armor = button(R.string.dungeon_demo_toggle_set) { scene.toggleSet() }
        val mobs = button(R.string.dungeon_demo_three_rats) { }
        mobs.setOnClickListener {
            scene.toggleMobCount()
            mobs.setText(if (scene.mobCount == 1) R.string.dungeon_demo_three_rats else R.string.dungeon_demo_one_rat)
        }
        val pause = button(R.string.dungeon_demo_pause) { }
        pause.setOnClickListener {
            scene.paused = !scene.paused
            pause.setText(if (scene.paused) R.string.dungeon_demo_resume else R.string.dungeon_demo_pause)
        }
        options.addView(armor, cell())
        options.addView(mobs, cell())
        options.addView(pause, cell())
        banner.addView(options)
        val gestures = LinearLayout(this)
        gestures.addView(button(R.string.dungeon_gesture_button) {
            val kinds = DungeonGesture.Kind.entries
            val names = intArrayOf(R.string.dungeon_gesture_right, R.string.dungeon_gesture_return,
                R.string.dungeon_gesture_circle, R.string.dungeon_gesture_triangle,
                R.string.dungeon_gesture_tap, R.string.dungeon_gesture_double)
            AlertDialog.Builder(this, R.style.Theme_A2U_Dialog)
                .setTitle(R.string.dungeon_gesture_select)
                .setSingleChoiceItems(names.map { getString(it) }.toTypedArray(), kinds.indexOf(scene.spellGesture)) { dialog, index ->
                    scene.spellGesture = kinds[index]
                    dialog.dismiss()
                }.show()
        }, cell())
        val parryMode = button(R.string.dungeon_gesture_parry) {}
        parryMode.setOnClickListener {
            scene.doubleParry = !scene.doubleParry
            parryMode.setText(if (scene.doubleParry) R.string.dungeon_gesture_double_parry else R.string.dungeon_gesture_parry)
        }
        gestures.addView(parryMode, cell())
        banner.addView(gestures)
        scene.onBusyChanged = { busy ->
            controls.forEach { it.isEnabled = !busy; it.alpha = if (busy) .45f else 1f }
            pause.setText(if (scene.paused) R.string.dungeon_demo_resume else R.string.dungeon_demo_pause)
        }
        setContentView(root)
    }

    private fun chooseFamily() {
        val families = Archetype.entries
        AlertDialog.Builder(this, R.style.Theme_A2U_Dialog)
            .setTitle(R.string.dungeon_demo_choose_family)
            .setItems((listOf(getString(R.string.dungeon_demo_civilian)) + families.map { getString(it.labelRes) }).toTypedArray()) { _, which ->
                if (which == 0) scene.unequipAll() else scene.equipFamily(families[which - 1])
            }
            .show()
    }

    private fun editEquipment(slot: EquipSlot) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        panel.addView(TextView(this).apply {
            setText(R.string.dungeon_demo_equipment_hint)
            textSize = 14f
        })
        panel.addView(TextView(this).apply { setText(R.string.dungeon_demo_family); textSize = 14f })
        panel.addView(Spinner(this).apply {
            val families = Archetype.entries
            adapter = ArrayAdapter(this@DungeonArtDemoActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.dungeon_demo_unequipped)) + families.map { getString(it.labelRes) })
            contentDescription = getString(R.string.dungeon_demo_family)
            setSelection(if (scene.gear(slot).equipped) families.indexOf(scene.gear(slot).family) + 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    scene.equip(if (position == 0) scene.gear(slot).copy(equipped = false)
                        else scene.gear(slot).copy(family = families[position - 1], equipped = true))
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        val choice = RadioGroup(this)
        val normal = RadioButton(this).apply { id = android.view.View.generateViewId(); setText(R.string.dungeon_demo_standard) }
        val isotope = RadioButton(this).apply { id = android.view.View.generateViewId(); setText(R.string.dungeon_demo_isotope) }
        choice.addView(normal)
        choice.addView(isotope)
        choice.check(if (scene.gear(slot).isotope) isotope.id else normal.id)
        choice.setOnCheckedChangeListener { _, id -> scene.equip(scene.gear(slot).copy(isotope = id == isotope.id)) }
        panel.addView(choice)
        panel.addView(TextView(this).apply { setText(R.string.dungeon_demo_tint); textSize = 14f })
        panel.addView(SeekBar(this).apply {
            max = 359
            progress = scene.gear(slot).hue
            contentDescription = getString(R.string.dungeon_demo_tint)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) scene.equip(scene.gear(slot).copy(hue = value))
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
        AlertDialog.Builder(this, R.style.Theme_A2U_Dialog)
            .setTitle(slot.labelRes)
            .setView(panel)
            .setPositiveButton(R.string.dungeon_demo_done, null)
            .show()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun cell() = LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    private fun button(label: Int, action: () -> Unit) = Button(this).apply {
        controls.add(this)
        setText(label)
        isAllCaps = false
        textSize = 14f
        setTextColor(0xFFF5E9CF.toInt())
        minimumWidth = 0
        minimumHeight = 0
        setPadding(dp(3), 0, dp(3), 0)
        background = GradientDrawable().apply {
            setColor(0xFF24334A.toInt())
            setStroke(dp(1).coerceAtLeast(1), 0xFF8D7954.toInt())
            cornerRadius = dp(3).toFloat()
        }
        setOnClickListener { action() }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        if (::scene.isInitialized) scene.setRunning(true)
    }

    override fun onPause() {
        if (::scene.isInitialized) scene.setRunning(false)
        super.onPause()
    }
}
