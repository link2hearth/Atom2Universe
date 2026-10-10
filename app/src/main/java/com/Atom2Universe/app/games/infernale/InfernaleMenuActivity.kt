package com.Atom2Universe.app.games.infernale

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import java.io.File

/** Le dossier des tableaux de la machine infernale, propre a l'application. */
internal fun Context.sauvegardesInfernale() = Sauvegardes(File(filesDir, "infernale"))

/**
 * L'ecran de depart de la machine infernale : les tableaux sauvegardes, et de quoi en
 * ouvrir un, en creer un, le renommer ou le supprimer.
 *
 * Rien d'autre : tout le jeu vit dans [InfernaleActivity].
 */
class InfernaleMenuActivity : ThemedActivity() {

    private lateinit var liste: LinearLayout
    private val sauvegardes by lazy { sauvegardesInfernale() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infernale_menu)
        enableImmersiveMode()

        liste = findViewById(R.id.infernale_menu_list)
        findViewById<ImageButton>(R.id.infernale_menu_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.infernale_menu_new).setOnClickListener { nouveau() }
    }

    override fun onResume() {
        super.onResume()
        rafraichir()
    }

    private fun rafraichir() {
        liste.removeAllViews()
        for (t in sauvegardes.lister()) liste.addView(ligne(t))
    }

    private fun ligne(t: TableauSauve): android.view.View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_infernale_tableau, liste, false)
        v.findViewById<TextView>(R.id.infernale_item_name).text = t.nom
        v.findViewById<TextView>(R.id.infernale_item_count).text =
            resources.getQuantityString(R.plurals.infernale_piece_count, t.poses.size, t.poses.size)
        v.findViewById<android.view.View>(R.id.infernale_item_open).setOnClickListener { ouvrir(t.id) }
        v.findViewById<TextView>(R.id.infernale_item_rename).setOnClickListener { renommer(t) }
        v.findViewById<TextView>(R.id.infernale_item_delete).setOnClickListener { supprimer(t) }
        return v
    }

    private fun ouvrir(id: String) {
        startActivity(Intent(this, InfernaleActivity::class.java).putExtra(InfernaleActivity.EXTRA_ID, id))
    }

    private fun nouveau() {
        val t = sauvegardes.creer(getString(R.string.infernale_default_name, sauvegardes.lister().size + 1))
        ouvrir(t.id)
    }

    private fun renommer(t: TableauSauve) {
        val champ = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            setText(t.nom)
            setSelection(t.nom.length)
        }
        val cadre = FrameLayout(this).apply {
            val marge = (20 * resources.displayMetrics.density).toInt()
            setPadding(marge, marge / 2, marge, 0)
            addView(
                champ,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setTitle(R.string.infernale_rename)
            .setView(cadre)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val nom = champ.text.toString().trim()
                if (nom.isNotEmpty()) {
                    sauvegardes.renommer(t.id, nom)
                    rafraichir()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun supprimer(t: TableauSauve) {
        com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder(this)
            .setMessage(getString(R.string.infernale_confirm_delete, t.nom))
            .setPositiveButton(R.string.infernale_delete) { _, _ ->
                sauvegardes.supprimer(t.id)
                rafraichir()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
