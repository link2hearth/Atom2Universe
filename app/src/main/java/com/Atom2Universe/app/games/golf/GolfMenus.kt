package com.Atom2Universe.app.games.golf

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.golf.classic.GolfIcon
import com.Atom2Universe.app.games.golf.classic.render.GolferAppearance
import com.Atom2Universe.app.games.golf.classic.render.GolferPreview

/** Petits menus à la demande ; aucune prévisualisation GL ne reste cachée en arrière-plan. */
internal object GolfMenus {
    fun enter(view: View) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        view.alpha = 0f
        view.translationY = 28f * view.resources.displayMetrics.density
        view.scaleX = .97f; view.scaleY = .97f
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(420).setInterpolator(DecelerateInterpolator()).start()
    }

    fun photoCard(context: Context, imageKey: String, title: String, action: (() -> Unit)? = null): FrameLayout {
        val ui = GolfUi(context)
        return FrameLayout(context).apply {
            background = ui.shape(ui.palette.surface, 22f, null)
            clipToOutline = true
            addView(GolfSnapshots.image(context, imageKey), FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, 0xE6102C27.toInt()))
            }, FrameLayout.LayoutParams(-1, ui.dp(100), Gravity.BOTTOM))
            addView(ui.text(title, 23f, Color.WHITE, true).apply {
                setPadding(ui.dp(16), ui.dp(12), ui.dp(if (action == null) 16 else 60), ui.dp(16))
            }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            if (action != null) {
                contentDescription = title; isClickable = true; isFocusable = true
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x35FFFFFF), null, ui.shape(Color.WHITE, 22f, null))
                addView(GolfIcon(context, GolfIcon.Kind.RIGHT, title, action),
                    FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.END or Gravity.BOTTOM).apply {
                        setMargins(0, 0, ui.dp(10), ui.dp(8))
                    })
                setOnClickListener { action() }
            }
        }
    }

    fun disclosure(context: Context, title: String, build: () -> View): View {
        val ui = GolfUi(context)
        return ui.column().apply {
            var body: View? = null
            val row = ui.row()
            row.addView(ui.text(title, 16f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            val arrow = GolfIcon(context, GolfIcon.Kind.DOWN, title) { row.performClick() }
            row.addView(arrow, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            row.isClickable = true; row.isFocusable = true
            row.setOnClickListener {
                if (body == null) { body = build(); addView(body) }
                else body!!.visibility = if (body!!.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                val open = body!!.visibility == View.VISIBLE
                arrow.rotation = if (open) 180f else 0f
                row.contentDescription = context.getString(if (open) R.string.golf_collapse else R.string.golf_expand, title)
                if (open) enter(body!!)
            }
            row.contentDescription = context.getString(R.string.golf_expand, title)
            addView(row)
        }
    }

    fun wardrobe(activity: ComponentActivity) {
        val ui = GolfUi(activity)
        val prefs = activity.getSharedPreferences("classic_golf_v2", Context.MODE_PRIVATE)
        var look = GolferAppearance(prefs.getBoolean("golfer_female", false),
            prefs.getInt("golfer_outfit", 0).coerceIn(0, 2), prefs.getInt("golfer_skin", 0).coerceIn(0, 2))
        val model = GolferPreview(activity, look).apply { contentDescription = activity.getString(R.string.classic_golfer_preview) }
        val col = ui.column().apply { setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12)) }
        col.addView(model, LinearLayout.LayoutParams(-1, ui.dp(220)))
        fun choose(label: Int, choices: Int, selected: Int, change: (Int) -> Unit) {
            val caption = ui.text(activity.getString(label), 14f, bold = true).apply { setPadding(0, ui.dp(12), 0, 0) }
            val picker = com.Atom2Universe.app.util.ImmersiveSpinner(activity).apply {
                id = View.generateViewId(); contentDescription = activity.getString(label)
                adapter = ArrayAdapter(activity, com.Atom2Universe.app.R.layout.item_spinner_selected, activity.resources.getStringArray(choices)).apply {
                    setDropDownViewResource(com.Atom2Universe.app.R.layout.item_spinner_dropdown)
                }
                setSelection(selected)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        change(position); model.appearance = look
                        prefs.edit().putBoolean("golfer_female", look.female).putInt("golfer_outfit", look.outfit).putInt("golfer_skin", look.skin).apply()
                    }
                }
            }
            caption.labelFor = picker.id; col.addView(caption); col.addView(picker, LinearLayout.LayoutParams(-1, ui.dp(48)))
        }
        choose(R.string.classic_golfer_model, R.array.classic_golfer_models, if (look.female) 1 else 0) { look = look.copy(female = it == 1) }
        choose(R.string.classic_golfer_outfit, R.array.classic_golfer_outfits, look.outfit) { look = look.copy(outfit = it) }
        choose(R.string.classic_golfer_skin, R.array.classic_golfer_skins, look.skin) { look = look.copy(skin = it) }
        val dialog = com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(activity).setTitle(R.string.classic_golfer_title)
            .setView(ScrollView(activity).apply { addView(col) }).setPositiveButton(R.string.golf_done, null).create()
        var running = false
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { if (!running) { model.onResume(); running = true } }
            override fun onPause(owner: LifecycleOwner) { if (running) { model.onPause(); running = false } }
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        dialog.setOnDismissListener {
            model.release(); model.onPause(); running = false
            activity.lifecycle.removeObserver(observer)
        }
        dialog.show()
        activity.lifecycle.addObserver(observer)
    }

}
