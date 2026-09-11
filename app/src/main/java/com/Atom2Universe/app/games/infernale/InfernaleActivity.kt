package com.Atom2Universe.app.games.infernale

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * La machine infernale : poser des pieces pour qu'une bille finisse par atteindre le
 * bouton.
 *
 * L'activite ne fait que trois choses — tirer un tableau, montrer la reserve, et
 * transmettre les boutons. Tout le jeu est dans [Partie], qui se teste sans elle.
 */
class InfernaleActivity : ThemedActivity(), InfernaleView.Listener {

    private companion object {
        const val PREFS = "infernale"
        const val CLE_TABLEAU = "tableau"
        const val PENTE_MIN = 5
    }

    private lateinit var vue: InfernaleView
    private lateinit var etat: TextView
    private lateinit var titre: TextView
    private lateinit var reserve: LinearLayout
    private lateinit var lignePente: View
    private lateinit var libellePente: TextView
    private lateinit var reglagePente: SeekBar
    private lateinit var prefs: SharedPreferences

    private var numero = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infernale)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        numero = prefs.getInt(CLE_TABLEAU, 1)

        vue = findViewById(R.id.infernale_view)
        etat = findViewById(R.id.infernale_status)
        titre = findViewById(R.id.infernale_board)
        reserve = findViewById(R.id.infernale_stock)
        lignePente = findViewById(R.id.infernale_slope_row)
        libellePente = findViewById(R.id.infernale_slope_label)
        reglagePente = findViewById(R.id.infernale_slope)
        vue.listener = this

        findViewById<ImageButton>(R.id.infernale_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.infernale_btn_new).setOnClickListener { nouveauTableau() }
        findViewById<TextView>(R.id.infernale_btn_clear).setOnClickListener {
            vue.surPartie { it.tableauRase() }
            vue.typeChoisi = null
            rafraichir()
        }
        findViewById<TextView>(R.id.infernale_btn_replay).setOnClickListener {
            vue.surPartie { it.rejouer() }
            rafraichir()
        }
        findViewById<TextView>(R.id.infernale_btn_launch).setOnClickListener {
            vue.surPartie { it.lancer() }
            vue.typeChoisi = null
            rafraichir()
        }

        reglagePente.progress = (vue.penteRampe - PENTE_MIN).toInt()
        reglagePente.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, valeur: Int, deLUsager: Boolean) {
                vue.penteRampe = (valeur + PENTE_MIN).toFloat()
                majPente()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        charger(numero)
    }

    override fun onResume() {
        super.onResume()
        vue.reprendre()
    }

    override fun onPause() {
        vue.suspendre()
        super.onPause()
    }

    private fun nouveauTableau() {
        numero++
        prefs.edit { putInt(CLE_TABLEAU, numero) }
        charger(numero)
    }

    private fun charger(n: Int) {
        // Le tirage fait tourner des machines completes jusqu'a en trouver une qui
        // gagne : c'est rapide, mais ce n'est pas instantane, donc pas sur le fil
        // principal.
        etat.text = getString(R.string.infernale_hint)
        titre.text = getString(R.string.infernale_board, n)
        Thread {
            val tableau = Tableaux.genererSurement(n.toLong())
            runOnUiThread {
                vue.jouer(Partie(tableau))
                vue.typeChoisi = null
                rafraichir()
            }
        }.start()
    }

    override fun surVictoire() {
        etat.text = getString(R.string.infernale_won)
        etat.setTextColor(0xFF55E08A.toInt())
    }

    override fun surChangement() = rafraichir()

    private fun rafraichir() {
        val partie = vue.partieCourante() ?: return
        if (!partie.gagne) {
            etat.setTextColor(0xFF94A3B8.toInt())
            etat.text = getString(R.string.infernale_hint)
        }
        majPente()
        construireReserve(partie)
    }

    private fun majPente() {
        val visible = vue.typeChoisi == TypePiece.RAMPE
        lignePente.visibility = if (visible) View.VISIBLE else View.GONE
        libellePente.text = getString(R.string.infernale_slope, vue.penteRampe.toInt())
    }

    /**
     * Reconstruit la reserve : une case par type qu'il reste a poser.
     *
     * Elle se refait entierement a chaque changement plutot que de se mettre a jour
     * case par case. C'est quelques vues recreees par pose, ce qui n'est rien a
     * l'echelle d'un geste de doigt, et ca supprime toute une classe de bugs ou
     * l'affichage et le stock finissent par ne plus dire la meme chose.
     */
    private fun construireReserve(partie: Partie) {
        reserve.removeAllViews()
        val stock = partie.stock()
        if (stock.isEmpty()) return
        for (type in TypePiece.entries) {
            val reste = stock[type] ?: continue
            reserve.addView(caseReserve(type, reste, choisi = vue.typeChoisi == type))
        }
    }

    private fun caseReserve(type: TypePiece, reste: Int, choisi: Boolean): View {
        val case = TextView(this).apply {
            text = "${nom(type)}  ×$reste"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(26, 20, 26, 20)
            setTextColor(if (choisi) Color.BLACK else 0xFFCBD5E1.toInt())
            setBackgroundColor(if (choisi) 0xFF9BC2FF.toInt() else 0xFF1B2540.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 10 }
            setOnClickListener {
                vue.typeChoisi = if (vue.typeChoisi == type) null else type
                rafraichir()
            }
        }
        return case
    }

    private fun nom(type: TypePiece): String = getString(
        when (type) {
            TypePiece.RAMPE -> R.string.infernale_piece_ramp
            TypePiece.PLOT -> R.string.infernale_piece_plot
            TypePiece.BLOC -> R.string.infernale_piece_block
            TypePiece.DOMINO -> R.string.infernale_piece_domino
            TypePiece.BASCULE -> R.string.infernale_piece_seesaw
            TypePiece.TREMPLIN -> R.string.infernale_piece_trampoline
        }
    )
}
