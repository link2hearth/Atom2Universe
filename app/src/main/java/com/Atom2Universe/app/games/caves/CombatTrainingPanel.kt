package com.Atom2Universe.app.games.caves

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.mode.CombatTrainingMode

/** Compact encounter picker: no need to walk through twelve sites just to try an enemy. */
internal class CombatTrainingPanel(
    activity: Activity, root: FrameLayout, mode: CombatTrainingMode, enter: (Int) -> Unit,
) {
    init {
        val names = intArrayOf(R.string.cave_site_abandoned_mine,R.string.cave_site_fungal_mine,
            R.string.cave_site_ember_foundry,R.string.cave_site_zombie_temple,R.string.cave_site_goblin_laboratory,
            R.string.cave_site_crystal_sanctum,R.string.cave_site_dwarven_mine,R.string.cave_site_ogre_den,
            R.string.cave_site_mummy_tomb,R.string.cave_site_troll_grove,R.string.cave_site_wraith_archive,
            R.string.cave_site_slime_cistern).map(activity::getString).toTypedArray()
        var selected=0
        val panel=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setBackgroundColor(0xAA202A32.toInt())
        }
        val buttons=LinearLayout(activity)
        val choose=Button(activity).apply {
            text=activity.getString(R.string.cave_training_choose,names[0])
            textSize=12f
            setOnClickListener {
                com.Atom2Universe.app.util.ImmersivePlatformAlertDialogBuilder(activity).setTitle(R.string.cave_training_map)
                    .setSingleChoiceItems(names,selected) { dialog,index -> enter(index); dialog.dismiss() }
                    .setNegativeButton(android.R.string.cancel,null).show()
            }
        }
        buttons.addView(choose)
        buttons.addView(Button(activity).apply {
            setText(R.string.cave_training_reset); textSize=12f
            setOnClickListener { enter(selected) }
        })
        val status=TextView(activity).apply {
            setTextColor(Color.WHITE); textSize=12f; gravity=Gravity.CENTER
            setPadding(12,0,12,6)
            text=activity.getString(R.string.cave_training_status,0,0)
        }
        panel.addView(buttons); panel.addView(status)
        root.addView(panel,FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin=(84*activity.resources.displayMetrics.density).toInt()
        })
        mode.onStatus={ index,damage,defeats -> activity.runOnUiThread {
            selected=index
            choose.text=activity.getString(R.string.cave_training_choose,names[index])
            status.text=activity.getString(R.string.cave_training_status,damage,defeats)
        } }
    }
}
